package com.beautycamera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.beautycamera.domain.model.BeautySettings
import com.beautycamera.domain.model.FaceLandmarks
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GPUImageHelper @Inject constructor(private val context: Context) {

    suspend fun applyFilter(bitmap: Bitmap, filter: GPUImageFilter, intensity: Float = 1.0f): Bitmap =
        withContext(Dispatchers.Default) {
            try {
                val gpu = GPUImage(context)
                gpu.setImage(bitmap)
                gpu.setFilter(filter)
                val filteredBitmap = gpu.bitmapWithFilterApplied ?: return@withContext bitmap

                if (intensity >= 0.99f) {
                    filteredBitmap
                } else {
                    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(result)
                    val paint = Paint().apply {
                        alpha = (intensity * 255).toInt().coerceIn(0, 255)
                    }
                    canvas.drawBitmap(filteredBitmap, 0f, 0f, paint)
                    filteredBitmap.recycle()
                    result
                }
            } catch (e: Exception) {
                e.printStackTrace()
                bitmap
            }
        }

    suspend fun applyBeautyEffects(bitmap: Bitmap, settings: BeautySettings): Bitmap =
        withContext(Dispatchers.Default) {
            var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            if (settings.skinSmoothing > 0f) {
                val filters = GPUImageFilterGroup(listOf(
                    GPUImageBilateralBlurFilter(settings.skinSmoothing * 10f),
                    GPUImageBrightnessFilter(settings.skinSmoothing * 0.04f)
                ))
                result = applyFilter(result, filters)
            }

            if (settings.foundationIntensity > 0f) {
                val foundationFilter = GPUImageFilterGroup(listOf(
                    GPUImageBilateralBlurFilter(settings.foundationIntensity * 6f),
                    GPUImageBrightnessFilter(settings.foundationIntensity * 0.06f),
                    GPUImageContrastFilter(1f + settings.foundationIntensity * 0.08f)
                ))
                result = applyFilter(result, foundationFilter)
            }

            result
        }

    suspend fun applySkinSmoothing(bitmap: Bitmap, intensity: Float): Bitmap {
        // Stage 1 — Bilateral blur: edge-preserving smoothing.
        // Unlike Gaussian, bilateral keeps sharp edges (eyes, lips, hair) while
        // smoothing out flat skin areas (pores, blemishes, uneven tone).
        // Radius kept low (max 2.5) to avoid the plastic/over-smoothed look.
        val bilateralRadius = intensity * 2.5f

        // Stage 2 — Brightness: subtle healthy glow on the skin
        val brightness = intensity * 0.04f

        // Stage 3 — Saturation: slight warmth (1.0 → 1.08) makes skin look alive
        val saturation = 1f + intensity * 0.08f

        val smoothFilter = GPUImageFilterGroup(listOf(
            GPUImageBilateralBlurFilter(bilateralRadius),
            GPUImageBrightnessFilter(brightness),
            GPUImageSaturationFilter(saturation)
        ))
        val smoothed = applyFilter(bitmap, smoothFilter)
        if (smoothed === bitmap) return applySkinSmoothingCanvas(bitmap, intensity)

        // Stage 4 — Light sharpening to recover fine detail at edges
        // (hair strands, eye definition) that bilateral softens slightly.
        val sharpened = applyFilter(smoothed, GPUImageSharpenFilter(intensity * 0.3f))
        return if (sharpened !== smoothed) sharpened else smoothed
    }

    private fun applySkinSmoothingCanvas(bitmap: Bitmap, intensity: Float): Bitmap {
        // Canvas fallback: warm-tone color matrix for skin glow (no blur)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val warmth    = intensity * 10f
        val brightness = intensity * 12f
        val matrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness + warmth,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness - warmth * 0.4f,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return result
    }

    suspend fun applyFoundation(bitmap: Bitmap, intensity: Float): Bitmap {
        // Reduced bilateral radius (was 10f) — prevents plastic/over-smoothed skin
        val filter = GPUImageFilterGroup(listOf(
            GPUImageBilateralBlurFilter(intensity * 6f),
            GPUImageBrightnessFilter(intensity * 0.06f),
            GPUImageContrastFilter(1f + intensity * 0.08f)
        ))
        val gpuResult = applyFilter(bitmap, filter)
        return if (gpuResult !== bitmap) gpuResult else applyFoundationCanvas(bitmap, intensity)
    }

    private fun applyFoundationCanvas(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, intensity * 15f,
            0f, 1f, 0f, 0f, intensity * 8f,
            0f, 0f, 1f, 0f, intensity * 2f,
            0f, 0f, 0f, 1f, 0f
        ))
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return result
    }

    fun applyLipColor(
        bitmap: Bitmap,
        lipPoints: List<Pair<Float, Float>>,
        color: Int,
        opacity: Float
    ): Bitmap {
        if (lipPoints.size < 3) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val smoothPath = buildSmoothPath(lipPoints)

        val lipLayer = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(lipLayer)

        // Pass 1 — outer bloom: large blur, very low alpha → creates the soft diffuse
        // halo around the lips that makes makeup look naturally applied
        val bloomAlpha = (opacity * 55).toInt().coerceIn(0, 70)
        val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(bloomAlpha, Color.red(color), Color.green(color), Color.blue(color))
            maskFilter = BlurMaskFilter(bitmap.width * 0.028f, BlurMaskFilter.Blur.NORMAL)
            style = Paint.Style.FILL
        }
        layerCanvas.drawPath(smoothPath, bloomPaint)

        // Pass 2 — core shape: moderate blur, soft alpha → the main lip tint
        val coreAlpha = (opacity * 120).toInt().coerceIn(0, 150)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(coreAlpha, Color.red(color), Color.green(color), Color.blue(color))
            maskFilter = BlurMaskFilter(bitmap.width * 0.012f, BlurMaskFilter.Blur.NORMAL)
            style = Paint.Style.FILL
        }
        layerCanvas.drawPath(smoothPath, corePaint)

        // SOFT_LIGHT is gentler than OVERLAY — tints without blowing out highlights,
        // making the lip color sit on the skin like real product
        val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
        canvas.drawBitmap(lipLayer, 0f, 0f, compositePaint)
        lipLayer.recycle()
        return result
    }

    fun applyBlush(
        bitmap: Bitmap,
        leftCheekPoints: List<Pair<Float, Float>>,
        rightCheekPoints: List<Pair<Float, Float>>,
        intensity: Float,
        color: Int = 0xFFE8A0A8.toInt()  // default: soft peach-rose
    ): Bitmap {
        if (leftCheekPoints.isEmpty() || rightCheekPoints.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val leftCX  = leftCheekPoints.map  { it.first  }.average().toFloat()
        val leftCY  = leftCheekPoints.map  { it.second }.average().toFloat()
        val rightCX = rightCheekPoints.map { it.first  }.average().toFloat()
        val rightCY = rightCheekPoints.map { it.second }.average().toFloat()

        val faceWidth = Math.abs(rightCX - leftCX).coerceAtLeast(bitmap.width * 0.1f)
        val radius = faceWidth * 0.28f

        fun drawBlushOnCheek(cx: Float, cy: Float) {
            val peakAlpha = (intensity * 85).toInt().coerceIn(0, 100)
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            val gradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.argb(peakAlpha,          r, g, b),
                    Color.argb(peakAlpha * 3 / 4,  r, g, b),
                    Color.argb(peakAlpha / 3,       r, g, b),
                    Color.argb(0,                   r, g, b)
                ),
                floatArrayOf(0f, 0.3f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
            // OVERLAY blend: warms light skin, deepens shadows → looks like real blush
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
                xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }

        drawBlushOnCheek(leftCX, leftCY)
        drawBlushOnCheek(rightCX, rightCY)
        return result
    }

    fun applyEyeColor(
        bitmap: Bitmap,
        leftIrisPoints: List<Pair<Float, Float>>,
        rightIrisPoints: List<Pair<Float, Float>>,
        color: Int,
        opacity: Float
    ): Bitmap {
        if (leftIrisPoints.isEmpty() && rightIrisPoints.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        fun drawIrisColor(points: List<Pair<Float, Float>>) {
            if (points.isEmpty()) return
            val centerX = points.map { it.first }.average().toFloat()
            val centerY = points.map { it.second }.average().toFloat()
            // Slightly larger radius for better iris coverage (was 0.018f)
            val radius = bitmap.width * 0.024f

            // Soft gradient: peak alpha low → reads as a translucent tint not paint
            val maxAlpha = (opacity * 85).toInt().coerceIn(0, 110)
            // Four stops: solid core fades gradually — no hard edge at any point
            val gradient = RadialGradient(
                centerX, centerY, radius,
                intArrayOf(
                    Color.argb(maxAlpha,      Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(maxAlpha * 3/4, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(maxAlpha / 3,  Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(0,             Color.red(color), Color.green(color), Color.blue(color))
                ),
                floatArrayOf(0f, 0.3f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
            // SOFT_LIGHT tints without saturating — keeps the iris texture visible
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
                xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            }
            canvas.drawCircle(centerX, centerY, radius, paint)
        }

        drawIrisColor(leftIrisPoints)
        drawIrisColor(rightIrisPoints)
        return result
    }

    // Smooth closed curve through landmark points using midpoint quadratic Bézier.
    // Works for both lip outlines and eyebrow shapes.
    private fun buildSmoothPath(points: List<Pair<Float, Float>>): Path {
        val path = Path()
        if (points.size < 2) return path
        val n = points.size
        // Start at midpoint between last and first point so the join is seamless
        path.moveTo(
            (points[n - 1].first + points[0].first) / 2f,
            (points[n - 1].second + points[0].second) / 2f
        )
        for (i in 0 until n) {
            val ctrl = points[i]
            val next = points[(i + 1) % n]
            path.quadTo(ctrl.first, ctrl.second, (ctrl.first + next.first) / 2f, (ctrl.second + next.second) / 2f)
        }
        path.close()
        return path
    }

    fun buildFilterChain(
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f
    ): GPUImageFilterGroup = GPUImageFilterGroup(listOf(
        GPUImageBrightnessFilter(brightness),
        GPUImageContrastFilter(contrast),
        GPUImageSaturationFilter(saturation)
    ))

    /** Gaussian blur — intensity 0..1 maps to blur radius 0..5. */
    suspend fun applyGaussianBlur(bitmap: Bitmap, intensity: Float): Bitmap =
        applyFilter(bitmap, GPUImageGaussianBlurFilter(intensity * 5f))

    /** Brightness / contrast / saturation in one GPU pass. */
    suspend fun applyAdjustments(
        bitmap: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): Bitmap = applyFilter(bitmap, buildFilterChain(brightness, contrast, saturation))

    /** Sharpness — intensity 0..1 maps to sharpen strength 0..2. */
    suspend fun applySharpness(bitmap: Bitmap, intensity: Float): Bitmap =
        applyFilter(bitmap, GPUImageSharpenFilter(intensity * 2f))
}
