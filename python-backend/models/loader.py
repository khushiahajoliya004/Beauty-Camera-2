"""
ModelLoader — loads and caches all models at startup.
Call loader.load_all() once; every pipeline module receives this loader.
"""
import logging
import os
import torch
import psutil

logger = logging.getLogger(__name__)


class ModelLoader:
    def __init__(self):
        self.device = self._detect_device()
        if self.device in ("cuda", "mps"):
            self.dtype = torch.float16   # GPU: float16 is fastest (MPS doesn't support bfloat16)
        else:
            # CPU: bfloat16 halves RAM (13 GB → ~7 GB) AND is fully supported.
            # float16 on CPU fails for LayerNorm/GroupNorm ops; bfloat16 works.
            self.dtype = torch.bfloat16
        self.all_loaded = False

        # Model references (set by load_all)
        self.sdxl_pipe = None       # StableDiffusionXLPipeline or IPAdapterFaceIDXL wrapper
        self.sd15_pipe = None       # SD 1.5 fallback
        self.face_app = None        # InsightFace app
        self.openpose = None        # OpenPose detector
        self.depth_estimator = None # MiDaS depth estimator
        self.gfpganer = None        # GFPGANer
        self.upsampler = None       # RealESRGANer

    # ── Device detection ──────────────────────────────────────────────────────

    def _detect_device(self) -> str:
        if torch.cuda.is_available():
            return "cuda"
        if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            return "mps"
        return "cpu"

    def has_enough_memory(self, min_gb: float = 6.0) -> bool:
        """True if device has at least min_gb of free memory."""
        try:
            if self.device == "cuda":
                free, _ = torch.cuda.mem_get_info()
                return (free / 1e9) >= min_gb
            # CPU: check RAM
            return (psutil.virtual_memory().available / 1e9) >= min_gb
        except Exception:
            return False

    def get_vram_free_gb(self) -> float:
        try:
            if self.device == "cuda":
                free, _ = torch.cuda.mem_get_info()
                return round(free / 1e9, 2)
            return round(psutil.virtual_memory().available / 1e9, 2)
        except Exception:
            return 0.0

    # ── Model loading ──────────────────────────────────────────────────────────

    def load_all(self):
        """Load all models in sequence. Called once at startup."""
        logger.info(f"Loading models on device={self.device} dtype={self.dtype}")

        # Load SDXL first while RAM is freshest (it needs the most headroom).
        # InsightFace + OpenPose each consume ~1 GB so load them after.
        self._load_sdxl()
        self._load_insightface()
        self._load_pose_depth()
        self._load_gfpgan()
        self._load_realesrgan()

        self.all_loaded = True
        logger.info("All models loaded successfully")

    def _load_insightface(self):
        from insightface.app import FaceAnalysis
        from models.paths import INSIGHTFACE_MODEL
        logger.info("Loading InsightFace buffalo_l...")
        self.face_app = FaceAnalysis(
            name=INSIGHTFACE_MODEL,
            providers=["CUDAExecutionProvider", "CPUExecutionProvider"]
            if self.device == "cuda"
            else ["CPUExecutionProvider"],
        )
        self.face_app.prepare(ctx_id=0 if self.device == "cuda" else -1, det_size=(640, 640))
        logger.info("InsightFace loaded")

    def _load_pose_depth(self):
        from controlnet_aux import OpenposeDetector, MidasDetector
        logger.info("Loading OpenPose and MiDaS...")
        self.openpose = OpenposeDetector.from_pretrained("lllyasviel/Annotators")
        self.depth_estimator = MidasDetector.from_pretrained("lllyasviel/Annotators")
        logger.info("Pose/depth detectors loaded")

    def _load_sdxl(self):
        # Use SD 1.5 instead of SDXL — needs only ~2 GB RAM (vs SDXL's 14 GB).
        # SDXL cannot reliably load on a 15-16 GB RAM machine without a GPU.
        from diffusers import StableDiffusionPipeline
        from models.paths import SD15_MODEL_ID, IP_ADAPTER_FACEID_SD15

        logger.info("Loading SD 1.5 pipeline (RAM-friendly alternative to SDXL)...")
        pipe = StableDiffusionPipeline.from_pretrained(
            SD15_MODEL_ID,
            torch_dtype=self.dtype,
            variant=None,
            low_cpu_mem_usage=True,
            safety_checker=None,          # skip safety checker — saves 800 MB RAM
            requires_safety_checker=False,
        )
        pipe = pipe.to(self.device)
        pipe.enable_attention_slicing()
        pipe.enable_vae_slicing()
        if self.device == "cuda":
            pipe.enable_xformers_memory_efficient_attention()

        # Load IP-Adapter FaceID Portrait SD15 (only 65 MB — already downloaded)
        if os.path.exists(IP_ADAPTER_FACEID_SD15):
            try:
                from ip_adapter.ip_adapter_faceid import IPAdapterFaceIDPortrait
                self.sdxl_pipe = IPAdapterFaceIDPortrait(
                    pipe,
                    ip_ckpt=IP_ADAPTER_FACEID_SD15,
                    device=self.device,
                    lora_rank=128,
                    num_tokens=16,
                )
                logger.info("SD 1.5 + IP-Adapter FaceID Portrait loaded")
            except Exception as e:
                logger.warning(f"IP-Adapter FaceID Portrait load failed ({e}), using plain SD 1.5")
                self.sdxl_pipe = pipe
        else:
            logger.warning("IP-Adapter SD15 weights not found. Using plain SD 1.5.")
            self.sdxl_pipe = pipe

    def _load_gfpgan(self):
        from gfpgan import GFPGANer
        from models.paths import GFPGAN_MODEL
        if not os.path.exists(GFPGAN_MODEL):
            logger.warning(f"GFPGAN weights not found at {GFPGAN_MODEL}. Face restoration disabled.")
            return
        logger.info("Loading GFPGAN...")
        self.gfpganer = GFPGANer(
            model_path=GFPGAN_MODEL,
            upscale=1,
            arch="clean",
            channel_multiplier=2,
            bg_upsampler=None,
        )
        logger.info("GFPGAN loaded")

    def _load_realesrgan(self):
        from realesrgan import RealESRGANer
        from basicsr.archs.rrdbnet_arch import RRDBNet
        from models.paths import REALESRGAN_MODEL
        if not os.path.exists(REALESRGAN_MODEL):
            logger.warning(f"RealESRGAN weights not found at {REALESRGAN_MODEL}. Upscaler disabled.")
            return
        logger.info("Loading Real-ESRGAN x4plus...")
        model = RRDBNet(
            num_in_ch=3, num_out_ch=3,
            num_feat=64, num_block=23, num_grow_ch=32, scale=4
        )
        self.upsampler = RealESRGANer(
            scale=4,
            model_path=REALESRGAN_MODEL,
            model=model,
            half=(self.dtype == torch.float16),
        )
        logger.info("Real-ESRGAN loaded")
