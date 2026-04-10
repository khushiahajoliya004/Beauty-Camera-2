"""
4x super-resolution upscaling using Real-ESRGAN.
Uses the "overscale trick": upscale 1024→4096, then downscale back to 1024.
Net effect: extreme sharpness of hair, skin pores, and fabric texture.
"""
import logging
import numpy as np
import cv2
from PIL import Image

logger = logging.getLogger(__name__)


class Upscaler:
    def __init__(self, loader):
        self._loader = loader  # access .upsampler at call time (not None at init)

    def upscale_then_downscale(
        self,
        img: Image.Image,
        target_size: tuple[int, int] = (1024, 1024),
    ) -> Image.Image:
        """
        Upscale 4x then downscale back to target_size for sharpness boost.
        Returns img unchanged if Real-ESRGAN is unavailable.
        """
        if self._loader.upsampler is None:
            logger.warning("Real-ESRGAN not loaded — skipping upscale")
            return img

        try:
            arr_rgb = np.array(img)
            arr_bgr = cv2.cvtColor(arr_rgb, cv2.COLOR_RGB2BGR)

            upscaled_bgr, _ = self._loader.upsampler.enhance(arr_bgr, outscale=4)

            # Downscale back to target — this is the "overscale trick"
            upscaled_rgb = cv2.cvtColor(upscaled_bgr, cv2.COLOR_BGR2RGB)
            big = Image.fromarray(upscaled_rgb)
            final = big.resize(target_size, Image.LANCZOS)

            logger.debug(
                f"Upscaled {img.size} → {big.size} → downscaled to {final.size}"
            )
            return final

        except Exception as e:
            logger.error(f"Real-ESRGAN upscaling failed: {e}", exc_info=True)
            return img
