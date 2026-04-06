package com.beautycamera.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.pow

/**
 * Generates 512×512 LUT (Look-Up Table) bitmaps compatible with GPUImageLookupFilter.
 *
 * LUT format (standard GPUImage layout):
 *   - 8×8 grid of 64×64 tiles on a 512×512 bitmap
 *   - Tile at (col, row) -> blue index = row*8 + col  (0..63)
 *   - Within tile: pixel x -> red input (0..63), pixel y -> green input (0..63)
 *   - Pixel color = OUTPUT color for that (r, g, b) input triple
 *
 * THREADING: generate() is CPU-intensive (~50-150 ms per LUT on modern hardware).
 * Always call getAllFilters() / access lazy LUT properties on Dispatchers.Default.
 */
object LutGenerator {

    private const val TILE = 64          // pixels per tile side
    private const val GRID = 8           // tiles per row / col
    private const val SIZE = TILE * GRID // 512 - total bitmap dimension

    // ──────────────────────────────────────────────────────────────────────────
    // Existing filters - LUT-upgraded versions
    // ──────────────────────────────────────────────────────────────────────────

    fun natural() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = contrast(c, 1.02f)
        c = brightness(c, 0.03f)
        saturation(c, 1.05f)
    }

    fun glam() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = brightness(c, 0.10f)
        c = contrast(c, 1.20f)
        saturation(c, 1.30f)
    }

    /** Vintage color grade - sepia + slight dark; vignette added in FilterRepository */
    fun vintage() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = sepia(c, 0.60f)
        brightness(c, -0.05f)
    }

    fun warm() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.80f)
        c = brightness(c, 0.05f)
        saturation(c, 1.10f)
    }

    fun cool() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = cool(c, 0.70f)
        c = brightness(c, 0.02f)
        saturation(c, 1.05f)
    }

    /** Soft color grade only - blur added in FilterRepository */
    fun soft() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = brightness(c, 0.08f)
        saturation(c, 0.90f)
    }

    fun bold() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = contrast(c, 1.50f)
        saturation(c, 1.40f)
    }

    /** Matte: lifted shadow floor + reduced contrast = flat film look */
    fun matte() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = liftShadows(c, 0.06f)  // raise the black point - the key "matte" move
        c = contrast(c, 0.85f)
        c = brightness(c, 0.05f)
        saturation(c, 0.80f)
    }

    fun vivid() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = contrast(c, 1.30f)
        saturation(c, 1.60f)
    }

    /** Fade: overexposed + washed-out film look */
    fun fade() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = brightness(c, 0.15f)
        c = contrast(c, 0.85f)
        saturation(c, 0.70f)
    }

    /** Drama color grade - vignette added in FilterRepository */
    fun drama() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = brightness(c, 0.05f)
        c = contrast(c, 1.30f)
        saturation(c, 0.90f)
    }

    /** Noir: bake grayscale + boosted contrast into LUT - vignette added separately */
    fun noir() = generate { r, g, b ->
        val lum = luminance(r, g, b)
        // Boost contrast on the luminance channel
        val out = ((lum - 0.5f) * 1.40f + 0.5f).coerceIn(0f, 1f)
        fa(out, out, out)
    }

    /** Peach: warm skin-flattering tint */
    fun peach() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.60f)
        c[0] = (c[0] + 0.05f).coerceIn(0f, 1f)  // push toward pink-red
        c = brightness(c, 0.10f)
        saturation(c, 1.10f)
    }

    /** Rose: magenta-pink cast */
    fun rose() = generate { r, g, b ->
        var c = fa(r, g, b)
        c[0] = (c[0] + 0.08f).coerceIn(0f, 1f)  // red boost
        c[2] = (c[2] + 0.04f).coerceIn(0f, 1f)  // blue boost -> magenta
        c = brightness(c, 0.05f)
        saturation(c, 1.20f)
    }

    /** Neon: max saturation + exposure for cyberpunk look */
    fun neon() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = saturation(c, 2.00f)
        c = contrast(c, 1.40f)
        exposure(c, 0.20f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // New MagicCamera-style filters
    // ──────────────────────────────────────────────────────────────────────────

    /** Lomo: punchy S-curve + vivid colors + slight warmth. Vignette added separately. */
    fun lomo() = generate { r, g, b ->
        // S-curve per channel adds the characteristic "Lomo" contrast punch
        var c = fa(sCurve(r, 1.5f), sCurve(g, 1.5f), sCurve(b, 1.5f))
        c = saturation(c, 1.30f)
        warm(c, 0.30f)
    }

    /** Amaro: lifted shadows + warm midtones (classic Instagram look) */
    fun amaro() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = liftShadows(c, 0.04f)
        c = warm(c, 0.50f)
        c = brightness(c, 0.07f)
        saturation(c, 1.15f)
    }

    /** Nashville: warm + dusty pink vintage tint */
    fun nashville() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.40f)
        c[0] = (c[0] + 0.06f).coerceIn(0f, 1f)  // pink channel boost
        c[2] = (c[2] + 0.03f).coerceIn(0f, 1f)  // slight blue lift
        c = liftShadows(c, 0.03f)
        c = brightness(c, 0.05f)
        saturation(c, 0.95f)
    }

    /** Hudson: cool blue editorial tone. Vignette added separately. */
    fun hudson() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = cool(c, 0.60f)
        c = brightness(c, 0.04f)
        contrast(c, 1.25f)
    }

    /** Walden: faded warm Polaroid */
    fun walden() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = liftShadows(c, 0.05f)
        c = warm(c, 0.50f)
        c = brightness(c, 0.08f)
        saturation(c, 0.85f)
    }

    /** Earlybird: warm sepia-tinted vintage. Vignette added separately. */
    fun earlybird() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = sepia(c, 0.20f)
        c = warm(c, 0.40f)
        c = contrast(c, 1.10f)
        saturation(c, 1.10f)
    }

    /** Valencia: warm orange tones + slight lifted shadows */
    fun valencia() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.70f)
        c[0] = (c[0] + 0.05f).coerceIn(0f, 1f)  // orange push
        c[1] = (c[1] + 0.02f).coerceIn(0f, 1f)
        c = liftShadows(c, 0.03f)
        saturation(c, 1.10f)
    }

    /** Toaster: warm high-contrast vintage. Vignette added separately. */
    fun toaster() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.50f)
        c = contrast(c, 1.30f)
        sepia(c, 0.15f)
    }

    /** Sakura: soft dreamy pink bloom */
    fun sakura() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = brightness(c, 0.08f)
        c = saturation(c, 0.85f)
        c[0] = (c[0] + 0.08f).coerceIn(0f, 1f)  // pink bloom
        c[2] = (c[2] + 0.04f).coerceIn(0f, 1f)
        liftShadows(c, 0.04f)
    }

    /** Sunset: strong golden-hour warm + vivid colors */
    fun sunset() = generate { r, g, b ->
        var c = fa(r, g, b)
        c = warm(c, 0.90f)
        c[0] = (c[0] + 0.08f).coerceIn(0f, 1f)  // extra orange
        c[1] = (c[1] + 0.02f).coerceIn(0f, 1f)
        c[2] = (c[2] - 0.05f).coerceIn(0f, 1f)  // reduce blue
        c = contrast(c, 1.10f)
        saturation(c, 1.20f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core generator
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a 512×512 ARGB_8888 LUT bitmap.
     * [transform] receives normalized (0..1) r, g, b and must return a FloatArray(3)
     * with normalized (0..1) output r, g, b.
     */
    fun generate(transform: (r: Float, g: Float, b: Float) -> FloatArray): Bitmap {
        val pixels = IntArray(SIZE * SIZE)

        for (tileRow in 0 until GRID) {
            for (tileCol in 0 until GRID) {
                val blueIdx = tileRow * GRID + tileCol
                val inputB  = blueIdx / (TILE - 1f)

                for (py in 0 until TILE) {
                    val inputG = py / (TILE - 1f)
                    for (px in 0 until TILE) {
                        val inputR = px / (TILE - 1f)
                        val out    = transform(inputR, inputG, inputB)

                        val pixelX = tileCol * TILE + px
                        val pixelY = tileRow * TILE + py
                        pixels[pixelY * SIZE + pixelX] = Color.rgb(
                            (out[0] * 255f).toInt().coerceIn(0, 255),
                            (out[1] * 255f).toInt().coerceIn(0, 255),
                            (out[2] * 255f).toInt().coerceIn(0, 255)
                        )
                    }
                }
            }
        }

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return bmp
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Primitive color transforms  (all work on FloatArray [r, g, b] in 0..1)
    // ──────────────────────────────────────────────────────────────────────────

    /** Convenience: create a FloatArray(3) from r, g, b */
    private fun fa(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    private fun brightness(c: FloatArray, amount: Float) = floatArrayOf(
        (c[0] + amount).coerceIn(0f, 1f),
        (c[1] + amount).coerceIn(0f, 1f),
        (c[2] + amount).coerceIn(0f, 1f)
    )

    private fun contrast(c: FloatArray, factor: Float) = floatArrayOf(
        ((c[0] - 0.5f) * factor + 0.5f).coerceIn(0f, 1f),
        ((c[1] - 0.5f) * factor + 0.5f).coerceIn(0f, 1f),
        ((c[2] - 0.5f) * factor + 0.5f).coerceIn(0f, 1f)
    )

    private fun saturation(c: FloatArray, factor: Float): FloatArray {
        val lum = luminance(c[0], c[1], c[2])
        return floatArrayOf(
            (lum + (c[0] - lum) * factor).coerceIn(0f, 1f),
            (lum + (c[1] - lum) * factor).coerceIn(0f, 1f),
            (lum + (c[2] - lum) * factor).coerceIn(0f, 1f)
        )
    }

    private fun sepia(c: FloatArray, intensity: Float): FloatArray {
        val r = c[0]; val g = c[1]; val b = c[2]
        val sr = (r * 0.393f + g * 0.769f + b * 0.189f).coerceIn(0f, 1f)
        val sg = (r * 0.349f + g * 0.686f + b * 0.168f).coerceIn(0f, 1f)
        val sb = (r * 0.272f + g * 0.534f + b * 0.131f).coerceIn(0f, 1f)
        return floatArrayOf(
            (r + (sr - r) * intensity).coerceIn(0f, 1f),
            (g + (sg - g) * intensity).coerceIn(0f, 1f),
            (b + (sb - b) * intensity).coerceIn(0f, 1f)
        )
    }

    /** Warm: shifts toward orange/yellow (red +, green +slight, blue −) */
    private fun warm(c: FloatArray, strength: Float) = floatArrayOf(
        (c[0] + strength * 0.15f).coerceIn(0f, 1f),
        (c[1] + strength * 0.05f).coerceIn(0f, 1f),
        (c[2] - strength * 0.10f).coerceIn(0f, 1f)
    )

    /** Cool: shifts toward blue (red −, blue +) */
    private fun cool(c: FloatArray, strength: Float) = floatArrayOf(
        (c[0] - strength * 0.10f).coerceIn(0f, 1f),
        (c[1] + strength * 0.02f).coerceIn(0f, 1f),
        (c[2] + strength * 0.12f).coerceIn(0f, 1f)
    )

    /**
     * Raises the shadow floor: maps input 0..1 to output lift..1.
     * This is the mathematical basis of the "matte" / "faded" look -
     * pure blacks become gray, giving that film-print quality.
     */
    private fun liftShadows(c: FloatArray, lift: Float) = floatArrayOf(
        c[0] * (1f - lift) + lift,
        c[1] * (1f - lift) + lift,
        c[2] * (1f - lift) + lift
    )

    /** Exposure: multiplicative luminance scale (2^ev), more natural than additive brightness */
    private fun exposure(c: FloatArray, ev: Float): FloatArray {
        val factor = 2.0.pow(ev.toDouble()).toFloat()
        return floatArrayOf(
            (c[0] * factor).coerceIn(0f, 1f),
            (c[1] * factor).coerceIn(0f, 1f),
            (c[2] * factor).coerceIn(0f, 1f)
        )
    }

    /**
     * Cubic S-curve on a single channel.
     * At v=0 and v=1: no change (endpoints preserved).
     * Darks are pulled darker; lights are pulled lighter - adds "punch".
     * strength=1.5 gives the characteristic Lomo contrast boost.
     */
    private fun sCurve(v: Float, strength: Float): Float =
        (v + strength * 0.25f * v * (1f - v) * (2f * v - 1f)).coerceIn(0f, 1f)

    /** ITU-R BT.601 luminance weights */
    private fun luminance(r: Float, g: Float, b: Float) = 0.299f * r + 0.587f * g + 0.114f * b
}
