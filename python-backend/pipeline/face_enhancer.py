"""
Face restoration using GFPGAN v1.4.
Fixes blurry/distorted faces in the generated image.
"""
import logging
import numpy as np
import cv2
from PIL import Image

logger = logging.getLogger(__name__)


class FaceEnhancer:
    def __init__(self, loader):
        self._loader = loader  # access .gfpganer at call time (not None at init)

    def enhance(self, img: Image.Image, fidelity: float = 0.5) -> Image.Image:
        """
        Apply GFPGAN face restoration.
        fidelity: 0.0 = maximum enhancement, 1.0 = maximum preserve original.

        Returns the enhanced image. If GFPGAN is unavailable, returns img unchanged.
        """
        if self._loader.gfpganer is None:
            logger.warning("GFPGAN not loaded — skipping face restoration")
            return img

        try:
            # GFPGAN works on BGR uint8
            arr_rgb = np.array(img)
            arr_bgr = cv2.cvtColor(arr_rgb, cv2.COLOR_RGB2BGR)

            _, _, restored_bgr = self._loader.gfpganer.enhance(
                arr_bgr,
                has_aligned=False,
                only_center_face=False,
                paste_back=True,
                weight=fidelity,
            )

            if restored_bgr is None:
                logger.warning("GFPGAN returned None — skipping enhancement")
                return img

            restored_rgb = cv2.cvtColor(restored_bgr, cv2.COLOR_BGR2RGB)
            return Image.fromarray(restored_rgb)

        except Exception as e:
            logger.error(f"GFPGAN enhancement failed: {e}", exc_info=True)
            return img
