package com.beautycamera.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates ControlNet conditioning maps from a source image.
 * Uses the OpenCV library already present in the project.
 *
 * The canny edge map is sent alongside the source image to the
 * ControlNet model on Replicate. It tells the model "keep this structure"
 * while allowing the style to change freely.
 */
@Singleton
class CannyPreprocessor @Inject constructor() {

    /**
     * Generate a Canny edge map from [bitmap].
     *
     * @param lowThreshold  Lower hysteresis threshold (100 is a good default)
     * @param highThreshold Upper hysteresis threshold (200 is a good default)
     *
     * Tuning guide:
     *  - Portrait (soft skin, gentle edges): low=80,  high=160
     *  - Clothing / detailed scene:          low=100, high=200
     *  - Architectural / sharp edges:        low=150, high=300
     *
     * Returns a 3-channel RGB bitmap where white = detected edge, black = no edge.
     * The 3-channel format is required by Replicate's ControlNet model.
     */
    suspend fun generateCanny(
        bitmap: Bitmap,
        lowThreshold: Double = 100.0,
        highThreshold: Double = 200.0,
    ): Bitmap = withContext(Dispatchers.Default) {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Convert to grayscale for edge detection
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        // Optional: apply Gaussian blur to reduce noise before Canny
        // This prevents hair and skin texture from creating too many edges
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)

        // Run Canny edge detection
        val edges = Mat()
        Imgproc.Canny(blurred, edges, lowThreshold, highThreshold)

        // Convert single-channel edge map to 3-channel RGB (required by ControlNet)
        val edgesRgb = Mat()
        Imgproc.cvtColor(edges, edgesRgb, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(edgesRgb, result)

        // Release native memory
        src.release(); gray.release(); blurred.release(); edges.release(); edgesRgb.release()

        result
    }

    /**
     * Generate a face-region mask for inpainting.
     * Returns a bitmap where the face area is WHITE (regenerate) and
     * everything else is BLACK (preserve).
     *
     * Used when the user wants AI to only transform the face,
     * keeping the background and clothing untouched.
     *
     * @param faceBoundingBox Normalized rect: left, top, right, bottom (0..1)
     * @param featherRadius   Gaussian blur radius for soft mask edges
     */
    suspend fun generateFaceMask(
        bitmap: Bitmap,
        faceBoundingBox: FaceBox,
        featherRadius: Double = 15.0,
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height

        // Build a black bitmap with a white ellipse at the face region
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(mask)
        canvas.drawColor(Color.BLACK)

        val paint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        // Add padding around the detected face box (20% each side)
        val padX = (faceBoundingBox.right - faceBoundingBox.left) * 0.20f
        val padY = (faceBoundingBox.bottom - faceBoundingBox.top) * 0.20f

        val left   = ((faceBoundingBox.left   - padX).coerceAtLeast(0f) * w)
        val top    = ((faceBoundingBox.top    - padY).coerceAtLeast(0f) * h)
        val right  = ((faceBoundingBox.right  + padX).coerceAtMost(1f) * w)
        val bottom = ((faceBoundingBox.bottom + padY).coerceAtMost(1f) * h)

        canvas.drawOval(android.graphics.RectF(left, top, right, bottom), paint)

        // Feather the mask edges using Gaussian blur (soft mask)
        val maskMat = Mat()
        Utils.bitmapToMat(mask, maskMat)
        val blurred = Mat()
        Imgproc.GaussianBlur(maskMat, blurred, org.opencv.core.Size(0.0, 0.0), featherRadius)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(blurred, result)

        maskMat.release(); blurred.release()
        result
    }
}

data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
