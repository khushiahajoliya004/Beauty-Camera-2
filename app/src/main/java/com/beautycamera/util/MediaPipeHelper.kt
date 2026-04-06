package com.beautycamera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.beautycamera.domain.model.FaceLandmarks
import com.beautycamera.domain.model.PoseLandmarks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeHelper @Inject constructor(private val context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null

    // Lip landmark indices for upper and lower lip
    val lipIndices = listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291,
        375, 321, 405, 314, 17, 84, 181, 91, 146)

    // Left cheek — upper "apple" area (cheekbone, below outer eye corner)
    val leftCheekIndices = listOf(50, 36, 31, 101, 119, 118, 117, 123, 187, 192)

    // Right cheek — upper "apple" area (cheekbone, below outer eye corner)
    val rightCheekIndices = listOf(280, 266, 261, 330, 348, 347, 346, 352, 411, 416)

    // Left iris indices
    val leftIrisIndices = listOf(474, 475, 476, 477)

    // Right iris indices
    val rightIrisIndices = listOf(469, 470, 471, 472)

    // Left eyebrow indices
    val leftEyebrowIndices = listOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)

    // Right eyebrow indices
    val rightEyebrowIndices = listOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)

    fun initFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun detectFaceLandmarks(bitmap: Bitmap): FaceLandmarks? =
        withContext(Dispatchers.Default) {
            // Try MediaPipe first (requires face_landmarker.task in assets)
            val mpResult = try {
                if (faceLandmarker == null) initFaceLandmarker()
                val mpImage = BitmapImageBuilder(bitmap).build()
                faceLandmarker?.detect(mpImage)?.faceLandmarks()?.firstOrNull()?.let { landmarks ->
                    FaceLandmarks(
                        points = landmarks.map { lm -> Pair(lm.x(), lm.y()) },
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height
                    )
                }
            } catch (e: Exception) {
                null
            }
            // Fall back to Android's built-in FaceDetector (no model file needed)
            mpResult ?: detectFaceWithAndroidApi(bitmap)
        }

    @Suppress("DEPRECATION")
    private fun detectFaceWithAndroidApi(bitmap: Bitmap): FaceLandmarks? {
        return try {
            val bmp565 = bitmap.copy(Bitmap.Config.RGB_565, false)
            val detector = android.media.FaceDetector(bmp565.width, bmp565.height, 1)
            val faces = arrayOfNulls<android.media.FaceDetector.Face>(1)
            val count = detector.findFaces(bmp565, faces)
            bmp565.recycle()
            if (count == 0 || faces[0] == null) return null

            val face = faces[0]!!
            val midPoint = PointF()
            face.getMidPoint(midPoint)
            val eyeDist = face.eyesDistance()

            buildSyntheticLandmarks(
                midPoint.x / bitmap.width,
                midPoint.y / bitmap.height,
                eyeDist / bitmap.width,
                bitmap.width,
                bitmap.height
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Builds a synthetic FaceLandmarks from eye midpoint + eye distance so that
     * all canvas-based beauty effects work even without the MediaPipe model file.
     * Positions are approximate but well-placed for typical frontal portrait photos.
     */
    private fun buildSyntheticLandmarks(
        eyeMidNX: Float, eyeMidNY: Float,
        dN: Float,           // normalized eye distance (relative to image width)
        imageWidth: Int, imageHeight: Int
    ): FaceLandmarks {
        val points = MutableList(478) { Pair(eyeMidNX, eyeMidNY) }

        fun setCircle(indices: List<Int>, cx: Float, cy: Float, rx: Float, ry: Float) {
            indices.forEachIndexed { i, idx ->
                val angle = (i.toFloat() / indices.size) * 2f * Math.PI.toFloat()
                points[idx] = Pair(
                    (cx + rx * cos(angle)).coerceIn(0f, 1f),
                    (cy + ry * sin(angle)).coerceIn(0f, 1f)
                )
            }
        }

        fun setLine(indices: List<Int>, cx: Float, cy: Float, halfW: Float, curveH: Float) {
            indices.forEachIndexed { i, idx ->
                val t = i.toFloat() / (indices.size - 1).coerceAtLeast(1) - 0.5f
                points[idx] = Pair(
                    (cx + t * halfW * 2f).coerceIn(0f, 1f),
                    (cy + t * t * curveH).coerceIn(0f, 1f)
                )
            }
        }

        // Lips — centered below eyes
        val mouthX = eyeMidNX
        val mouthY = eyeMidNY + dN * 1.35f
        setCircle(lipIndices, mouthX, mouthY, dN * 0.38f, dN * 0.09f)

        // Left cheek — placed at upper cheek apple (below outer eye, above mouth)
        setCircle(leftCheekIndices, eyeMidNX - dN * 0.62f, eyeMidNY + dN * 0.20f, dN * 0.12f, dN * 0.10f)

        // Right cheek — mirror of left
        setCircle(rightCheekIndices, eyeMidNX + dN * 0.62f, eyeMidNY + dN * 0.20f, dN * 0.12f, dN * 0.10f)

        // Left iris
        setCircle(leftIrisIndices, eyeMidNX - dN * 0.5f, eyeMidNY, dN * 0.06f, dN * 0.06f)

        // Right iris
        setCircle(rightIrisIndices, eyeMidNX + dN * 0.5f, eyeMidNY, dN * 0.06f, dN * 0.06f)

        // Left eyebrow
        setLine(leftEyebrowIndices, eyeMidNX - dN * 0.5f, eyeMidNY - dN * 0.3f, dN * 0.22f, dN * 0.04f)

        // Right eyebrow
        setLine(rightEyebrowIndices, eyeMidNX + dN * 0.5f, eyeMidNY - dN * 0.3f, dN * 0.22f, dN * 0.04f)

        return FaceLandmarks(points, imageWidth, imageHeight)
    }

    suspend fun detectPoseLandmarks(bitmap: Bitmap): PoseLandmarks? =
        withContext(Dispatchers.Default) {
            try {
                if (poseLandmarker == null) initPoseLandmarker()
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = poseLandmarker?.detect(mpImage)
                result?.landmarks()?.firstOrNull()?.let { landmarks ->
                    PoseLandmarks(
                        points = landmarks.map { lm -> Triple(lm.x(), lm.y(), lm.z()) },
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    fun getLandmarkPixelCoords(
        landmarks: FaceLandmarks,
        indices: List<Int>
    ): List<Pair<Float, Float>> {
        return indices.mapNotNull { idx ->
            if (idx < landmarks.points.size) {
                val (nx, ny) = landmarks.points[idx]
                Pair(nx * landmarks.imageWidth, ny * landmarks.imageHeight)
            } else null
        }
    }

    fun getPoseLandmarkPixelCoords(
        landmarks: PoseLandmarks,
        indices: List<Int>
    ): List<Pair<Float, Float>> {
        return indices.mapNotNull { idx ->
            if (idx < landmarks.points.size) {
                val (nx, ny, _) = landmarks.points[idx]
                Pair(nx * landmarks.imageWidth, ny * landmarks.imageHeight)
            } else null
        }
    }

    fun close() {
        faceLandmarker?.close()
        poseLandmarker?.close()
        faceLandmarker = null
        poseLandmarker = null
    }
}
