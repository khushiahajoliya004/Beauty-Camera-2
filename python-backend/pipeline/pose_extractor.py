"""
Pose and depth map extraction using ControlNet auxiliary detectors.
Used to preserve body structure during generation.
"""
import logging
from PIL import Image

from utils.image_utils import resize_to_target

logger = logging.getLogger(__name__)


class PoseExtractor:
    def __init__(self, loader):
        self._loader = loader  # access .openpose / .depth_estimator at call time

    def get_pose(self, img: Image.Image, width: int, height: int) -> Image.Image:
        """
        Run OpenPose on the image and return the pose skeleton map
        resized to (width, height).
        """
        try:
            pose_map = self._loader.openpose(img, hand_and_face=False)
            return pose_map.resize((width, height), Image.LANCZOS)
        except Exception as e:
            logger.warning(f"Pose extraction failed ({e}), using blank pose map")
            return Image.new("RGB", (width, height), color=(0, 0, 0))

    def get_depth(self, img: Image.Image, width: int, height: int) -> Image.Image:
        """
        Run MiDaS depth estimator and return the depth map
        resized to (width, height).
        """
        try:
            depth_map = self._loader.depth_estimator(img)
            return depth_map.resize((width, height), Image.LANCZOS)
        except Exception as e:
            logger.warning(f"Depth extraction failed ({e}), using blank depth map")
            return Image.new("RGB", (width, height), color=(128, 128, 128))
