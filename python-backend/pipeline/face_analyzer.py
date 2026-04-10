"""
Face analysis using InsightFace buffalo_l.
Detects faces, extracts 512-dim identity embedding and landmarks.
"""
import logging
from dataclasses import dataclass, field
from typing import Optional

import numpy as np
from PIL import Image

from utils.image_utils import pil_to_numpy, resize_for_analysis

logger = logging.getLogger(__name__)

MIN_FACE_SIZE_PX = 80
MIN_CONFIDENCE = 0.70


@dataclass
class FaceResult:
    success: bool
    embedding: Optional[np.ndarray] = None   # shape (512,)
    keypoints: Optional[np.ndarray] = None   # shape (5, 2) — five facial landmarks
    bbox: Optional[list] = None              # [x1, y1, x2, y2]
    face_crop: Optional[Image.Image] = None  # aligned face crop (for IP-Adapter Plus)
    score: float = 0.0
    error: str = ""


class FaceAnalyzer:
    def __init__(self, loader):
        self._loader = loader  # access .face_app at call time (not None at init)

    def analyze(self, img: Image.Image) -> FaceResult:
        """
        Detect the largest/most-centered face and extract its identity embedding.
        """
        try:
            arr = pil_to_numpy(img)  # RGB uint8
            # InsightFace expects BGR
            import cv2
            bgr = cv2.cvtColor(arr, cv2.COLOR_RGB2BGR)

            faces = self._loader.face_app.get(bgr)
            if not faces:
                return FaceResult(success=False, error="No face detected in input image",)

            # Pick the largest face (highest det_score or largest bbox area)
            face = max(faces, key=lambda f: _face_area(f))

            # Quality checks
            bbox = face.bbox.astype(int)
            face_w = bbox[2] - bbox[0]
            face_h = bbox[3] - bbox[1]
            if face_w < MIN_FACE_SIZE_PX or face_h < MIN_FACE_SIZE_PX:
                return FaceResult(
                    success=False,
                    error=f"Face too small ({face_w}×{face_h} px). Please use a closer photo.",
                )

            score = float(face.det_score) if hasattr(face, "det_score") else 1.0
            if score < MIN_CONFIDENCE:
                return FaceResult(
                    success=False,
                    error=f"Face detection confidence too low ({score:.2f}). Please use a clearer photo.",
                )

            embedding = face.normed_embedding  # shape (512,)
            keypoints = face.kps               # shape (5, 2)

            # Extract aligned face crop for IP-Adapter Plus variant
            face_crop = _extract_face_crop(img, bbox, padding_factor=0.4)

            logger.debug(
                f"Face detected: bbox={bbox.tolist()} size={face_w}x{face_h} score={score:.3f}"
            )

            return FaceResult(
                success=True,
                embedding=embedding,
                keypoints=keypoints,
                bbox=bbox.tolist(),
                face_crop=face_crop,
                score=score,
            )

        except Exception as e:
            logger.error(f"Face analysis error: {e}", exc_info=True)
            return FaceResult(success=False, error=f"Face analysis failed: {e}")


# ── Helpers ───────────────────────────────────────────────────────────────────

def _face_area(face) -> float:
    b = face.bbox
    return (b[2] - b[0]) * (b[3] - b[1])


def _extract_face_crop(img: Image.Image, bbox: np.ndarray, padding_factor: float = 0.4) -> Image.Image:
    """
    Crop the face region with extra padding for IP-Adapter Plus.
    Returns a square 224×224 crop.
    """
    x1, y1, x2, y2 = int(bbox[0]), int(bbox[1]), int(bbox[2]), int(bbox[3])
    w, h = x2 - x1, y2 - y1
    pad_x = int(w * padding_factor)
    pad_y = int(h * padding_factor)
    iw, ih = img.size
    x1 = max(0, x1 - pad_x)
    y1 = max(0, y1 - pad_y)
    x2 = min(iw, x2 + pad_x)
    y2 = min(ih, y2 + pad_y)
    crop = img.crop((x1, y1, x2, y2))
    return crop.resize((224, 224), Image.LANCZOS)
