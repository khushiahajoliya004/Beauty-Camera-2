"""
Input validation helpers.
"""
import cv2
import numpy as np
from PIL import Image


def compute_blur_score(img: Image.Image) -> float:
    """
    Return the Laplacian variance of the image.
    Higher = sharper. Values < 50 indicate a very blurry image.
    """
    gray = np.array(img.convert("L"))
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def validate_image(img: Image.Image) -> tuple[bool, str]:
    """
    Validate the decoded user image before processing.
    Returns (ok, error_message). If ok is True, error_message is empty.
    """
    w, h = img.size

    if w < 64 or h < 64:
        return False, "Image is too small (minimum 64×64 px)"

    if w > 8000 or h > 8000:
        return False, "Image is too large (maximum 8000×8000 px)"

    blur = compute_blur_score(img)
    if blur < 20:
        return False, f"Image is too blurry (score={blur:.1f}). Please use a clearer photo."

    return True, ""
