"""
Centralized model file paths.
All paths are relative to the python-backend/ directory.
"""
import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEIGHTS_DIR = os.path.join(BASE_DIR, "weights")

# ── Stable Diffusion XL base model (downloaded via HuggingFace hub) ──────────
SDXL_MODEL_ID = "stabilityai/stable-diffusion-xl-base-1.0"

# ── IP-Adapter FaceID weights ─────────────────────────────────────────────────
IP_ADAPTER_DIR = os.path.join(WEIGHTS_DIR, "ip_adapter")
IP_ADAPTER_FACEID_SDXL = os.path.join(IP_ADAPTER_DIR, "ip-adapter-faceid-plusv2_sdxl.bin")
IP_ADAPTER_FACEID_SD15 = os.path.join(IP_ADAPTER_DIR, "ip-adapter-faceid-portrait-v11_sd15.bin")

# SD1.5 base (fallback when SDXL runs out of VRAM)
SD15_MODEL_ID = "runwayml/stable-diffusion-v1-5"

# ── ControlNet weights (SD1.5) ────────────────────────────────────────────────
CONTROLNET_DIR = os.path.join(WEIGHTS_DIR, "controlnet")
CONTROLNET_OPENPOSE = os.path.join(CONTROLNET_DIR, "control_v11p_sd15_openpose.pth")
CONTROLNET_DEPTH = os.path.join(CONTROLNET_DIR, "control_v11f1p_sd15_depth.pth")

# ── GFPGAN face restoration ───────────────────────────────────────────────────
GFPGAN_DIR = os.path.join(WEIGHTS_DIR, "gfpgan")
GFPGAN_MODEL = os.path.join(GFPGAN_DIR, "GFPGANv1.4.pth")

# ── Real-ESRGAN upscaler ──────────────────────────────────────────────────────
REALESRGAN_DIR = os.path.join(WEIGHTS_DIR, "realesrgan")
REALESRGAN_MODEL = os.path.join(REALESRGAN_DIR, "RealESRGAN_x4plus.pth")

# ── InsightFace buffalo_l (auto-downloaded to ~/.insightface) ─────────────────
INSIGHTFACE_MODEL = "buffalo_l"
