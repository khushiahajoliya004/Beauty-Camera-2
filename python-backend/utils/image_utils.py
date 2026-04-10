"""
Image conversion helpers — base64 ↔ PIL, resizing, EXIF correction.
"""
import base64
import io
import logging
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

MAX_ANALYSIS_SIZE = 640  # resize to this before face detection (speed)


def base64_to_pil(b64_string: str) -> Image.Image:
    """Decode a base64-encoded JPEG/PNG string → PIL Image in RGB."""
    if "," in b64_string:
        # Strip data-URI prefix (data:image/jpeg;base64,...)
        b64_string = b64_string.split(",", 1)[1]
    data = base64.b64decode(b64_string)
    img = Image.open(io.BytesIO(data))
    img = ImageOps.exif_transpose(img)  # fix EXIF rotation
    return img.convert("RGB")


def pil_to_base64(img: Image.Image, quality: int = 95) -> str:
    """Encode a PIL Image → base64 JPEG string."""
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality, optimize=True)
    return base64.b64encode(buf.getvalue()).decode("utf-8")


def resize_for_analysis(img: Image.Image, max_side: int = MAX_ANALYSIS_SIZE) -> Image.Image:
    """Resize image so the longest side is max_side px (preserving aspect ratio)."""
    w, h = img.size
    if max(w, h) <= max_side:
        return img
    scale = max_side / max(w, h)
    return img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)


def resize_to_target(img: Image.Image, width: int, height: int) -> Image.Image:
    """Resize and centre-crop image to exact width×height."""
    img = ImageOps.fit(img, (width, height), method=Image.LANCZOS)
    return img


def pil_to_numpy(img: Image.Image):
    """PIL RGB → numpy uint8 HWC array (for OpenCV/InsightFace)."""
    import numpy as np
    return np.array(img)


def numpy_to_pil(arr) -> Image.Image:
    """numpy uint8 HWC (RGB or BGR) → PIL Image RGB."""
    import numpy as np
    arr = np.uint8(arr)
    if arr.shape[2] == 3:
        return Image.fromarray(arr, mode="RGB")
    return Image.fromarray(arr)
