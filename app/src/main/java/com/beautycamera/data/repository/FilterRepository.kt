package com.beautycamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.beautycamera.domain.model.FilterModel
import com.beautycamera.util.LutGenerator
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All 25 filters are now LUT-based (GPUImageLookupFilter + pre-baked 512×512 colour table).
 *
 * Benefits over the old GPUImage parameter chains:
 *  - Single GPU pass for any colour transform, regardless of complexity
 *  - LUT can express curves, cross-processing, channel mixing — not possible with simple params
 *  - Consistent quality: the same maths that defines the filter IS the preview thumbnail grade
 *
 * Filters that also need a spatial effect (vignette / blur) use GPUImageFilterGroup:
 *   LUT (colour) → Vignette / Blur (spatial)
 *
 * Threading: LUT bitmaps are lazily generated on first access via `by lazy`.
 *   Generation takes ~50–150 ms per LUT — always call getAllFilters() off the main thread
 *   (e.g. inside withContext(Dispatchers.Default) in FilterViewModel).
 */
@Singleton
class FilterRepository @Inject constructor(private val context: Context) {

    // ── LUT bitmap cache — generated once, reused for every filter application ──

    // Original 15 filters
    private val naturalLut   by lazy { LutGenerator.natural()   }
    private val glamLut      by lazy { LutGenerator.glam()      }
    private val vintageLut   by lazy { LutGenerator.vintage()   }
    private val warmLut      by lazy { LutGenerator.warm()      }
    private val coolLut      by lazy { LutGenerator.cool()      }
    private val softLut      by lazy { LutGenerator.soft()      }
    private val boldLut      by lazy { LutGenerator.bold()      }
    private val matteLut     by lazy { LutGenerator.matte()     }
    private val vividLut     by lazy { LutGenerator.vivid()     }
    private val fadeLut      by lazy { LutGenerator.fade()      }
    private val dramaLut     by lazy { LutGenerator.drama()     }
    private val noirLut      by lazy { LutGenerator.noir()      }
    private val peachLut     by lazy { LutGenerator.peach()     }
    private val roseLut      by lazy { LutGenerator.rose()      }
    private val neonLut      by lazy { LutGenerator.neon()      }

    // 10 new MagicCamera-style filters
    private val lomoLut      by lazy { LutGenerator.lomo()      }
    private val amaroLut     by lazy { LutGenerator.amaro()     }
    private val nashvilleLut by lazy { LutGenerator.nashville() }
    private val hudsonLut    by lazy { LutGenerator.hudson()    }
    private val waldenLut    by lazy { LutGenerator.walden()    }
    private val earlybirdLut by lazy { LutGenerator.earlybird() }
    private val valenciaLut  by lazy { LutGenerator.valencia()  }
    private val toasterLut   by lazy { LutGenerator.toaster()   }
    private val sakuraLut    by lazy { LutGenerator.sakura()    }
    private val sunsetLut    by lazy { LutGenerator.sunset()    }

    // ── Filter list ────────────────────────────────────────────────────────────

    fun getAllFilters(): List<FilterModel> = listOf(

        // ── Original 15 ──────────────────────────────────────────────────────

        FilterModel("natural",   "Natural",   lut(naturalLut)),
        FilterModel("glam",      "Glam",      lut(glamLut)),

        // Vintage: sepia colour grade + lens-falloff vignette
        FilterModel("vintage",   "Vintage",   lutVignette(vintageLut)),

        FilterModel("warm",      "Warm",      lut(warmLut)),
        FilterModel("cool",      "Cool",      lut(coolLut)),

        // Soft: gaussian blur first (spatial), then colour grade LUT
        FilterModel("soft",      "Soft",      lutBlur(softLut, blurSize = 0.5f)),

        FilterModel("bold",      "Bold",      lut(boldLut)),
        FilterModel("matte",     "Matte",     lut(matteLut)),
        FilterModel("vivid",     "Vivid",     lut(vividLut)),
        FilterModel("fade",      "Fade",      lut(fadeLut)),

        // Drama & Noir: colour grade + cinematic vignette
        FilterModel("drama",     "Drama",     lutVignette(dramaLut)),
        FilterModel("noir",      "Noir",      lutVignette(noirLut)),

        FilterModel("peach",     "Peach",     lut(peachLut)),
        FilterModel("rose",      "Rose",      lut(roseLut)),
        FilterModel("neon",      "Neon",      lut(neonLut)),

        // ── 10 new MagicCamera-style ──────────────────────────────────────────

        // Lomo: punchy S-curve, vivid colours, classic Lomo vignette
        FilterModel("lomo",      "Lomo",      lutVignette(lomoLut)),

        // Amaro: lifted shadows + warm midtones (Instagram classic)
        FilterModel("amaro",     "Amaro",     lut(amaroLut)),

        // Nashville: warm dusty-pink vintage tint
        FilterModel("nashville", "Nashville", lut(nashvilleLut)),

        // Hudson: cool blue editorial + vignette
        FilterModel("hudson",    "Hudson",    lutVignette(hudsonLut)),

        // Walden: faded warm Polaroid
        FilterModel("walden",    "Walden",    lut(waldenLut)),

        // Earlybird: warm sepia-tinted vintage + vignette
        FilterModel("earlybird", "Earlybird", lutVignette(earlybirdLut)),

        // Valencia: warm orange tones, slight lifted shadows
        FilterModel("valencia",  "Valencia",  lut(valenciaLut)),

        // Toaster: high-contrast warm vintage + vignette
        FilterModel("toaster",   "Toaster",   lutVignette(toasterLut)),

        // Sakura: soft dreamy pink bloom
        FilterModel("sakura",    "Sakura",    lut(sakuraLut)),

        // Sunset: strong golden-hour warm + vivid
        FilterModel("sunset",    "Sunset",    lut(sunsetLut))
    )

    // ── Factory helpers ────────────────────────────────────────────────────────

    /** Pure LUT colour filter — single GPU pass */
    private fun lut(bitmap: Bitmap): GPUImageFilter =
        GPUImageLookupFilter().apply { setBitmap(bitmap) }

    /**
     * LUT colour grade → vignette (lens-falloff edge darkening).
     * Vignette parameters are tuned to darken only the outer 15–35% ring,
     * preventing the "black image" bug from overly aggressive defaults.
     */
    private fun lutVignette(bitmap: Bitmap): GPUImageFilter =
        GPUImageFilterGroup(listOf(
            GPUImageLookupFilter().apply { setBitmap(bitmap) },
            GPUImageVignetteFilter(
                PointF(0.5f, 0.5f),       // centre
                floatArrayOf(0f, 0f, 0f), // vignette colour: black
                0.50f,                    // start: 50% from centre
                0.85f                     // end: 85% from centre (corners only fully dark)
            )
        ))

    /**
     * Gaussian blur (spatial softening) → LUT colour grade.
     * Blur runs first so the colour table grades the already-softened image.
     * [blurSize] is a normalised texture-coordinate radius (GPUImage convention).
     */
    private fun lutBlur(bitmap: Bitmap, blurSize: Float): GPUImageFilter =
        GPUImageFilterGroup(listOf(
            GPUImageGaussianBlurFilter(blurSize),
            GPUImageLookupFilter().apply { setBitmap(bitmap) }
        ))
}
