"""
Main image generation pipeline.
Supports two modes:
  1. IP-Adapter FaceID + SDXL  — best quality, requires ~10GB VRAM
  2. Plain SDXL text-to-image  — fallback when IP-Adapter not loaded
"""
import logging
from typing import Optional

import numpy as np
import torch
from PIL import Image

logger = logging.getLogger(__name__)

# ── Generation hyper-parameters (tune here) ───────────────────────────────────
IP_ADAPTER_SCALE = 0.8        # 0.6 = more creative  |  0.9 = face-locked
GUIDANCE_SCALE = 7.5
NUM_STEPS_HIGH = 30
NUM_STEPS_FAST = 20
# SD 1.5 native resolution — requests for 1024 are capped here; Real-ESRGAN upscales later
SD15_MAX_SIZE = 512


class ImageGenerator:
    def __init__(self, loader):
        self._loader = loader  # access .sdxl_pipe at call time (not None at init)

    def generate(
        self,
        face_embedding: np.ndarray,
        face_keypoints: Optional[np.ndarray],
        pose_map: Image.Image,
        depth_map: Image.Image,
        positive_prompt: str,
        negative_prompt: str,
        width: int = 1024,
        height: int = 1024,
        steps: int = NUM_STEPS_HIGH,
        face_crop: Optional[Image.Image] = None,
    ) -> Image.Image:
        """
        Generate a portrait image.
        Returns a PIL Image at the requested width × height.
        """
        # Cap to SD 1.5 native resolution (512). Real-ESRGAN upscales afterwards.
        gen_w = min(width, SD15_MAX_SIZE)
        gen_h = min(height, SD15_MAX_SIZE)

        try:
            return self._generate_with_ip_adapter(
                face_embedding=face_embedding,
                face_keypoints=face_keypoints,
                positive_prompt=positive_prompt,
                negative_prompt=negative_prompt,
                width=gen_w,
                height=gen_h,
                steps=steps,
                face_crop=face_crop,
            )
        except Exception as e:
            logger.error(f"IP-Adapter generation failed: {e}", exc_info=True)
            logger.info("Falling back to plain SD 1.5 text-to-image generation")
            return self._generate_plain_sdxl(
                positive_prompt=positive_prompt,
                negative_prompt=negative_prompt,
                width=gen_w,
                height=gen_h,
                steps=steps,
            )

    # ── IP-Adapter FaceID path ────────────────────────────────────────────────

    def _generate_with_ip_adapter(
        self,
        face_embedding: np.ndarray,
        face_keypoints,
        positive_prompt: str,
        negative_prompt: str,
        width: int,
        height: int,
        steps: int,
        face_crop: Optional[Image.Image],
    ) -> Image.Image:
        pipe = self._loader.sdxl_pipe  # read from loader at call time

        # ip_adapter.ip_adapter_faceid IP-Adapter wrappers all expose .generate()
        if hasattr(pipe, "generate"):
            # face embedding shape: (1, 1, 512)
            faceid_embeds = torch.from_numpy(face_embedding).unsqueeze(0).unsqueeze(0)
            faceid_embeds = faceid_embeds.to(dtype=self._loader.dtype)

            kwargs = dict(
                prompt=positive_prompt,
                negative_prompt=negative_prompt,
                faceid_embeds=faceid_embeds,
                scale=IP_ADAPTER_SCALE,
                num_samples=1,
                width=width,
                height=height,
                num_inference_steps=steps,
                guidance_scale=GUIDANCE_SCALE,
            )
            # FaceID Plus variants also accept a face_image; Portrait does not
            pipe_cls = type(pipe).__name__
            if face_crop is not None and "Plus" in pipe_cls:
                kwargs["face_image"] = face_crop

            images = pipe.generate(**kwargs)
            return images[0] if isinstance(images, list) else images

        # Diffusers built-in IP-Adapter path (fallback for plain pipe with loaded adapter)
        if hasattr(pipe, "set_ip_adapter_scale"):
            pipe.set_ip_adapter_scale(IP_ADAPTER_SCALE)
            faceid_embeds = torch.from_numpy(face_embedding).unsqueeze(0)
            if self._loader.dtype == torch.float16:
                faceid_embeds = faceid_embeds.half()
            result = pipe(
                prompt=positive_prompt,
                negative_prompt=negative_prompt,
                ip_adapter_image_embeds=[faceid_embeds],
                width=width,
                height=height,
                num_inference_steps=steps,
                guidance_scale=GUIDANCE_SCALE,
            )
            return result.images[0]

        # No IP-Adapter — fall through to plain generation
        raise RuntimeError("IP-Adapter not available on loaded pipeline")

    def generate_text2img(
        self,
        prompt: str,
        negative_prompt: str,
        width: int = 1024,
        height: int = 1024,
        steps: int = NUM_STEPS_HIGH,
    ) -> Image.Image:
        """Pure text-to-image generation — no face input required."""
        gen_w = min(width, SD15_MAX_SIZE)
        gen_h = min(height, SD15_MAX_SIZE)
        return self._generate_plain_sdxl(
            positive_prompt=prompt,
            negative_prompt=negative_prompt,
            width=gen_w,
            height=gen_h,
            steps=steps,
        )

    # ── Plain SDXL text-to-image path ─────────────────────────────────────────

    def _generate_plain_sdxl(
        self,
        positive_prompt: str,
        negative_prompt: str,
        width: int,
        height: int,
        steps: int,
    ) -> Image.Image:
        pipe = self._loader.sdxl_pipe  # read from loader at call time

        # If pipe is an IPAdapter wrapper, access the underlying pipe
        underlying = getattr(pipe, "pipe", pipe)

        result = underlying(
            prompt=positive_prompt,
            negative_prompt=negative_prompt,
            width=width,
            height=height,
            num_inference_steps=steps,
            guidance_scale=GUIDANCE_SCALE,
        )
        return result.images[0]
