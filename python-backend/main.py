"""
Beauty Camera AI Backend — FastAPI entry point.
Run: python main.py  (starts on http://0.0.0.0:8000)
"""
# ── Compatibility shim (must be FIRST) ────────────────────────────────────────
# torchvision >= 0.17 removed functional_tensor; basicsr/gfpgan/realesrgan need it
import sys
import types as _types
try:
    import torchvision.transforms.functional as _tvF
    _ft_mod = _types.ModuleType("torchvision.transforms.functional_tensor")
    # copy every public attr that basicsr references
    for _attr in ("rgb_to_grayscale", "to_tensor", "normalize",
                  "adjust_brightness", "adjust_contrast", "adjust_saturation",
                  "adjust_hue", "pad", "center_crop", "resize", "rotate",
                  "hflip", "vflip", "crop", "perspective", "gaussian_blur"):
        if hasattr(_tvF, _attr):
            setattr(_ft_mod, _attr, getattr(_tvF, _attr))
    sys.modules.setdefault("torchvision.transforms.functional_tensor", _ft_mod)
except Exception:
    pass  # best-effort; import errors surface naturally below
# ─────────────────────────────────────────────────────────────────────────────

import logging
import time

import torch
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from models.loader import ModelLoader
from pipeline.face_analyzer import FaceAnalyzer
from pipeline.pose_extractor import PoseExtractor
from pipeline.generator import ImageGenerator
from pipeline.face_enhancer import FaceEnhancer
from pipeline.upscaler import Upscaler
from styles.prompts import STYLES, UNIVERSAL_NEGATIVE
from utils.image_utils import base64_to_pil, pil_to_base64
from utils.validator import validate_image

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)

# ── App setup ──────────────────────────────────────────────────────────────────

app = FastAPI(title="Beauty Camera AI Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Model loading (once at startup) ───────────────────────────────────────────

loader = ModelLoader()
face_analyzer = FaceAnalyzer(loader)
pose_extractor = PoseExtractor(loader)
generator = ImageGenerator(loader)
face_enhancer = FaceEnhancer(loader)
upscaler = Upscaler(loader)


@app.on_event("startup")
async def startup_event():
    import asyncio
    import traceback
    logger.info("Loading all AI models — this may take 2-3 minutes on first run...")
    try:
        # Run blocking model loading in a thread so it doesn't block the event loop
        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, loader.load_all)
        logger.info("Server ready.")
    except Exception as e:
        logger.error(f"Model loading failed:\n{traceback.format_exc()}")
        raise  # still exit — can't serve without models


# ── Request / Response models ──────────────────────────────────────────────────

class GenerateRequest(BaseModel):
    image: str            # base64-encoded JPEG
    style: str            # style key from STYLES dict
    quality: str = "high" # "high" (30 steps) or "fast" (20 steps)
    width: int = 1024
    height: int = 1024


# ── Endpoints ──────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    """Android health-check before starting generation."""
    return {
        "status": "ready" if loader.all_loaded else "loading",
        "models_loaded": loader.all_loaded,
        "device": loader.device,
        "vram_free_gb": loader.get_vram_free_gb(),
    }


@app.get("/styles")
def get_styles():
    """Return all available style IDs and display names for Android UI."""
    return {
        "styles": [
            {"id": k, "name": v["name"], "preview_color": v.get("preview_color", "#888888")}
            for k, v in STYLES.items()
        ]
    }


@app.post("/generate")
def generate(req: GenerateRequest):
    """
    Main generation endpoint.
    Receives a user photo + style ID, returns a professional AI portrait
    with the user's face identity preserved.
    """
    if not loader.all_loaded:
        raise HTTPException(status_code=503, detail="Models are still loading. Please retry in a moment.")

    start = time.time()

    # ── Step 1: Decode and validate input ─────────────────────────────────────
    try:
        user_image = base64_to_pil(req.image)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image data: {e}")

    ok, err = validate_image(user_image)
    if not ok:
        raise HTTPException(status_code=400, detail=err)

    # ── Step 2: Face analysis ─────────────────────────────────────────────────
    face_result = face_analyzer.analyze(user_image)
    if not face_result.success:
        raise HTTPException(
            status_code=422,
            detail={"error": face_result.error, "code": "FACE_NOT_FOUND"},
        )

    # ── Step 3: Pose and depth maps ───────────────────────────────────────────
    pose_map = pose_extractor.get_pose(user_image, req.width, req.height)
    depth_map = pose_extractor.get_depth(user_image, req.width, req.height)

    # ── Step 4: Prompt building ───────────────────────────────────────────────
    style_data = STYLES.get(req.style)
    if not style_data:
        raise HTTPException(status_code=400, detail=f"Unknown style: '{req.style}'. See GET /styles.")

    positive_prompt = (
        style_data["positive"]
        + ", same person, same face, photorealistic, ultra detailed skin, 8k portrait"
    )
    negative_prompt = style_data.get("negative", "") + ", " + UNIVERSAL_NEGATIVE

    # ── Step 5: IP-Adapter FaceID + SDXL generation ───────────────────────────
    steps = 30 if req.quality == "high" else 20
    generated = generator.generate(
        face_embedding=face_result.embedding,
        face_keypoints=face_result.keypoints,
        pose_map=pose_map,
        depth_map=depth_map,
        positive_prompt=positive_prompt,
        negative_prompt=negative_prompt,
        width=req.width,
        height=req.height,
        steps=steps,
        face_crop=face_result.face_crop,
    )

    # ── Step 6: GFPGAN face restoration ──────────────────────────────────────
    enhanced = face_enhancer.enhance(generated, fidelity=0.5)

    # ── Step 7: Real-ESRGAN upscale (overscale trick) ─────────────────────────
    if loader.has_enough_memory(min_gb=6):
        final = upscaler.upscale_then_downscale(enhanced, target_size=(req.width, req.height))
    else:
        logger.info("Skipping upscale — not enough free memory (< 6 GB)")
        final = enhanced

    # ── Step 8: Encode and respond ────────────────────────────────────────────
    elapsed = round(time.time() - start, 1)
    logger.info(f"Generation complete in {elapsed}s | style={req.style} | device={loader.device}")

    return {
        "success": True,
        "image": pil_to_base64(final),
        "generation_time_seconds": elapsed,
        "model_used": "sdxl_ip_adapter_faceid_v2",
    }


# ── Dev runner ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")
