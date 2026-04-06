package com.beautycamera.util

import android.graphics.Bitmap
import com.beautycamera.domain.model.BodySettings
import com.beautycamera.domain.model.PoseLandmarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.roundToInt

@Singleton
class OpenCVHelper @Inject constructor() {

    companion object {
        init {
            try {
                System.loadLibrary("opencv_java4")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    suspend fun applyBodyReshape(bitmap: Bitmap, settings: BodySettings): Bitmap =
        withContext(Dispatchers.Default) {
            var result = bitmap
            if (settings.slimBody > 0f || settings.slimWaist > 0f)
                result = applyBodySlim(result, settings.slimBody, settings.slimWaist)
            if (settings.legLengthening > 0f)
                result = applyLegLengthening(result, settings.legLengthening)
            if (settings.bustEnhance > 0f)
                result = applyBustEnhanceNoPose(result, settings.bustEnhance)
            if (settings.hipEnhance > 0f)
                result = applyHipEnhanceNoPose(result, settings.hipEnhance)
            if (settings.shoulderReshape > 0f)
                result = applyShoulderReshapeNoPose(result, settings.shoulderReshape)
            result
        }

    suspend fun applyBodyReshapeWithPose(
        bitmap: Bitmap,
        settings: BodySettings,
        poseLandmarks: PoseLandmarks
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            var result = bitmap

            if (settings.slimWaist > 0f)
                result = applyWaistSlim(result, poseLandmarks, settings.slimWaist)
            if (settings.slimBody > 0f)
                result = applyBodySlim(result, settings.slimBody, 0f)
            if (settings.legLengthening > 0f)
                result = applyLegLengtheningWithPose(result, poseLandmarks, settings.legLengthening)
            if (settings.bustEnhance > 0f)
                result = applyBustEnhance(result, poseLandmarks, settings.bustEnhance)
            if (settings.hipEnhance > 0f)
                result = applyHipEnhance(result, poseLandmarks, settings.hipEnhance)
            if (settings.shoulderReshape > 0f)
                result = applyShoulderReshape(result, poseLandmarks, settings.shoulderReshape)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyWaistSlim(
        bitmap: Bitmap,
        pose: PoseLandmarks,
        intensity: Float
    ): Bitmap {
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()

            // Landmarks 11=left shoulder, 12=right shoulder, 23=left hip, 24=right hip
            if (pose.points.size > 24) {
                val leftShoulder = pose.points[11]
                val rightShoulder = pose.points[12]
                val leftHip = pose.points[23]
                val rightHip = pose.points[24]

                val waistY = ((leftHip.second + rightHip.second) / 2 * h).toInt()
                val squeezeAmount = (w * 0.05f * intensity).toInt()

                // Build remap matrices
                val mapX = Mat(src.rows(), src.cols(), CvType.CV_32FC1)
                val mapY = Mat(src.rows(), src.cols(), CvType.CV_32FC1)

                for (y in 0 until src.rows()) {
                    for (x in 0 until src.cols()) {
                        val centerX = src.cols() / 2f
                        val distFromCenter = x - centerX
                        val distFromWaist = abs(y - waistY).toFloat()
                        val influence = max(0f, 1f - distFromWaist / (h * 0.15f))

                        val newX = if (distFromCenter > 0) {
                            x - (squeezeAmount * influence * (distFromCenter / (w / 2))).toInt()
                        } else {
                            x + (squeezeAmount * influence * (-distFromCenter / (w / 2))).toInt()
                        }
                        mapX.put(y, x, newX.toDouble())
                        mapY.put(y, x, y.toDouble())
                    }
                }

                Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
                mapX.release()
                mapY.release()
            }

            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release()
            dst.release()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyBodySlim(
        bitmap: Bitmap,
        bodyIntensity: Float,
        waistIntensity: Float
    ): Bitmap {
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols()
            val h = src.rows()
            val mapX = Mat(h, w, CvType.CV_32FC1)
            val mapY = Mat(h, w, CvType.CV_32FC1)
            val squeeze = bodyIntensity * 0.08f

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val nx = (x - w / 2f) / (w / 2f)
                    val mappedX = nx - nx * abs(nx) * squeeze
                    mapX.put(y, x, (mappedX * (w / 2f) + w / 2f).toDouble())
                    mapY.put(y, x, y.toDouble())
                }
            }

            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyLegLengthening(bitmap: Bitmap, intensity: Float): Bitmap {
        return try {
            val legStartY = (bitmap.height * 0.55f).toInt()
            val legRegion = Bitmap.createBitmap(
                bitmap, 0, legStartY, bitmap.width, bitmap.height - legStartY
            )
            val stretchFactor = 1f + intensity * 0.08f
            val newLegHeight = (legRegion.height * stretchFactor).toInt()
            val stretchedLegs = Bitmap.createScaledBitmap(
                legRegion, bitmap.width, newLegHeight, true
            )
            val result = Bitmap.createBitmap(
                bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.drawBitmap(stretchedLegs, 0f, legStartY.toFloat(), null)
            stretchedLegs.recycle()
            legRegion.recycle()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyLegLengtheningWithPose(
        bitmap: Bitmap,
        pose: PoseLandmarks,
        intensity: Float
    ): Bitmap {
        if (pose.points.size <= 28) return applyLegLengthening(bitmap, intensity)
        val hipY = ((pose.points[23].second + pose.points[24].second) / 2 * bitmap.height).toInt()
        return try {
            val legRegion = Bitmap.createBitmap(
                bitmap, 0, hipY, bitmap.width, bitmap.height - hipY
            )
            val newHeight = (legRegion.height * (1f + intensity * 0.08f)).toInt()
            val stretched = Bitmap.createScaledBitmap(legRegion, bitmap.width, newHeight, true)
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.drawBitmap(stretched, 0f, hipY.toFloat(), null)
            stretched.recycle(); legRegion.recycle()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyBustEnhance(
        bitmap: Bitmap,
        pose: PoseLandmarks,
        intensity: Float
    ): Bitmap {
        if (pose.points.size < 12) return bitmap
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val chestY = ((pose.points[11].second + pose.points[12].second) / 2f * h.toFloat())
            val chestX = (w / 2f)
            val radius = w * 0.15f
            val expand = intensity * 8f

            val mapX = Mat(h, w, CvType.CV_32FC1)
            val mapY = Mat(h, w, CvType.CV_32FC1)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - chestX; val dy = y - chestY
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist < radius && dist > 0) {
                        val factor = (1f - dist / radius) * expand
                        mapX.put(y, x, (x - dx / dist * factor).toDouble())
                        mapY.put(y, x, (y - dy / dist * factor).toDouble())
                    } else {
                        mapX.put(y, x, x.toDouble())
                        mapY.put(y, x, y.toDouble())
                    }
                }
            }

            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun applyHipEnhance(bitmap: Bitmap, pose: PoseLandmarks, intensity: Float): Bitmap {
        if (pose.points.size < 25) return applyHipEnhanceNoPose(bitmap, intensity)
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val leftHipX  = pose.points[23].first  * w
            val rightHipX = pose.points[24].first  * w
            val hipY      = ((pose.points[23].second + pose.points[24].second) / 2f * h)
            val radius    = abs(rightHipX - leftHipX) * 0.65f
            val expand    = intensity * 14f
            val mapX = Mat(h, w, CvType.CV_32FC1)
            val mapY = Mat(h, w, CvType.CV_32FC1)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var srcX = x.toDouble(); var srcY = y.toDouble()
                    if (x < w / 2) {
                        val dx = x - leftHipX; val dy = y - hipY
                        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (dist in 1f..radius) {
                            val factor = (1f - dist / radius) * expand
                            srcX = (x - dx / dist * factor).toDouble()
                            srcY = (y - dy / dist * factor).toDouble()
                        }
                    } else {
                        val dx = x - rightHipX; val dy = y - hipY
                        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (dist in 1f..radius) {
                            val factor = (1f - dist / radius) * expand
                            srcX = (x - dx / dist * factor).toDouble()
                            srcY = (y - dy / dist * factor).toDouble()
                        }
                    }
                    mapX.put(y, x, srcX); mapY.put(y, x, srcY)
                }
            }
            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    private fun applyHipEnhanceNoPose(bitmap: Bitmap, intensity: Float): Bitmap {
        // Approximate hip area at ~58% down the image
        val hipY = bitmap.height * 0.58f
        val cx = bitmap.width / 2f
        val radius = bitmap.width * 0.38f
        val expand = intensity * 14f
        return try {
            val src = Mat(); Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val mapX = Mat(h, w, CvType.CV_32FC1); val mapY = Mat(h, w, CvType.CV_32FC1)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val side = if (x < w / 2) x.toFloat() else (w - x).toFloat()
                    val dy = abs(y - hipY)
                    val influence = maxOf(0f, 1f - dy / (bitmap.height * 0.12f))
                    val push = side / (w / 2f) * expand * influence
                    val srcX = if (x < w / 2) (x + push).toDouble() else (x - push).toDouble()
                    mapX.put(y, x, srcX); mapY.put(y, x, y.toDouble())
                }
            }
            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    private fun applyShoulderReshape(bitmap: Bitmap, pose: PoseLandmarks, intensity: Float): Bitmap {
        if (pose.points.size < 13) return applyShoulderReshapeNoPose(bitmap, intensity)
        return try {
            val src = Mat(); Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val leftShoulderX  = pose.points[11].first  * w
            val rightShoulderX = pose.points[12].first  * w
            val shoulderY      = ((pose.points[11].second + pose.points[12].second) / 2f * h)
            val radius  = abs(rightShoulderX - leftShoulderX) * 0.45f
            val squeeze = intensity * 12f
            val mapX = Mat(h, w, CvType.CV_32FC1); val mapY = Mat(h, w, CvType.CV_32FC1)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var srcX = x.toDouble(); var srcY = y.toDouble()
                    if (x < w / 2) {
                        val dx = x - leftShoulderX; val dy = y - shoulderY
                        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (dist in 1f..radius) {
                            val factor = (1f - dist / radius) * squeeze
                            srcX = (x + dx / dist * factor).toDouble()
                            srcY = (y + dy / dist * factor).toDouble()
                        }
                    } else {
                        val dx = x - rightShoulderX; val dy = y - shoulderY
                        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        if (dist in 1f..radius) {
                            val factor = (1f - dist / radius) * squeeze
                            srcX = (x + dx / dist * factor).toDouble()
                            srcY = (y + dy / dist * factor).toDouble()
                        }
                    }
                    mapX.put(y, x, srcX); mapY.put(y, x, srcY)
                }
            }
            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    private fun applyShoulderReshapeNoPose(bitmap: Bitmap, intensity: Float): Bitmap {
        // Approximate shoulder area at ~22% down the image
        val shoulderY = bitmap.height * 0.22f
        val squeeze = intensity * 12f
        return try {
            val src = Mat(); Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val mapX = Mat(h, w, CvType.CV_32FC1); val mapY = Mat(h, w, CvType.CV_32FC1)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val side = if (x < w / 2) x.toFloat() else (w - x).toFloat()
                    val dy = abs(y - shoulderY)
                    val influence = maxOf(0f, 1f - dy / (bitmap.height * 0.10f))
                    val push = side / (w / 2f) * squeeze * influence
                    val srcX = if (x < w / 2) (x - push).toDouble() else (x + push).toDouble()
                    mapX.put(y, x, srcX); mapY.put(y, x, y.toDouble())
                }
            }
            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    private fun applyBustEnhanceNoPose(bitmap: Bitmap, intensity: Float): Bitmap {
        // Approximate chest area at ~35% down the image
        val chestY = bitmap.height * 0.35f
        val chestX = bitmap.width / 2f
        val radius = bitmap.width * 0.18f
        val expand = intensity * 10f
        return try {
            val src = Mat(); Utils.bitmapToMat(bitmap, src)
            val dst = src.clone()
            val w = src.cols(); val h = src.rows()
            val mapX = Mat(h, w, CvType.CV_32FC1); val mapY = Mat(h, w, CvType.CV_32FC1)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - chestX; val dy = y - chestY
                    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist in 1f..radius) {
                        val factor = (1f - dist / radius) * expand
                        mapX.put(y, x, (x - dx / dist * factor).toDouble())
                        mapY.put(y, x, (y - dy / dist * factor).toDouble())
                    } else {
                        mapX.put(y, x, x.toDouble()); mapY.put(y, x, y.toDouble())
                    }
                }
            }
            Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR)
            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release(); mapX.release(); mapY.release()
            result
        } catch (e: Exception) { e.printStackTrace(); bitmap }
    }

    fun gaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        return try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)
            val dst = Mat()
            val kernelSize = ((radius * 2).toInt() or 1).coerceAtLeast(1)
            Imgproc.GaussianBlur(src, dst, Size(kernelSize.toDouble(), kernelSize.toDouble()), 0.0)
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, result)
            src.release(); dst.release()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
}
