package com.beautycamera.data.remote

import com.beautycamera.data.remote.SD3Model
import com.beautycamera.data.remote.StabilityStylePreset

/**
 * Style configuration for Stability AI generation.
 *
 * Each preset maps a user-facing style name to:
 *  - Which SD endpoint to use (ControlNet structure / SD3 / SDXL Core)
 *  - The exact prompt + negative prompt
 *  - All tuning parameters
 *
 * Choosing the right endpoint per style:
 *  ┌─────────────────┬──────────────────────────────────────────────────┐
 *  │ useControlNet   │ Best for portrait — face structure locked by SD   │
 *  │ useSD3          │ Best quality, most creative, most expensive       │
 *  │ else (Core)     │ Fast, good quality, free built-in style presets   │
 *  └─────────────────┴──────────────────────────────────────────────────┘
 *
 * Denoising strength guide:
 *  0.25–0.40 → Subtle: clean portrait, beauty, skin retouching
 *  0.40–0.55 → Moderate: glamour, vintage, cinematic
 *  0.55–0.70 → Heavy: anime, painting, sketch, full style transfer
 *
 * controlnetScale guide (ControlNet Structure):
 *  0.8–0.9 → Very rigid structure (exact face shape locked)
 *  0.6–0.7 → Balanced (good for stylization with face preservation)
 *  0.4–0.5 → Loose (more artistic freedom)
 */
data class SDStyleConfig(
    val styleId: String,
    val displayName: String,
    val prompt: String,
    val negativePrompt: String,
    val denoisingStrength: Float,

    // Which SD endpoint to use
    val useControlNet: Boolean = false,   // /control/structure — face structure preserved
    val controlnetScale: Float = 0.7f,    // how strongly structure is locked (0.0–1.0)
    val useSD3: Boolean = false,          // /generate/sd3 — highest quality
    val sd3Model: String = SD3Model.LARGE_TURBO,

    // SDXL Core style preset (used when useControlNet=false and useSD3=false)
    val sdxlStylePreset: String = StabilityStylePreset.PHOTOGRAPHIC,

    // Post-processing
    val upscaleAfter: Boolean = true,     // conservative upscaler (4× output resolution)
) {
    companion object {

        val ALL = listOf(

            // ── Portrait & Beauty ──────────────────────────────────────────────
            // All styles use SD3 Medium (cheapest img2img, avoids 402 on free tier)
            // Upscaling disabled to save credits

            SDStyleConfig(
                styleId = "portrait_enhance",
                displayName = "Portrait Enhance",
                prompt = "(photorealistic portrait:1.3), (preserve facial identity:1.5), " +
                         "(same person:1.4), flawless skin, soft studio lighting, " +
                         "sharp eyes, professional photography, 8k uhd",
                negativePrompt = "different person, changed face, deformed, blurry, " +
                                 "bad anatomy, extra limbs, low quality, watermark",
                denoisingStrength = 0.30f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "glamour",
                displayName = "Glamour",
                prompt = "(glamour photography:1.3), (preserve facial identity:1.5), " +
                         "magazine editorial, soft bokeh, dramatic lighting, " +
                         "flawless skin, vivid colors, professional makeup, 8k",
                negativePrompt = "different person, changed face, amateur, dark, blurry",
                denoisingStrength = 0.38f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "cinematic",
                displayName = "Cinematic",
                prompt = "(cinematic portrait:1.3), (preserve facial identity:1.4), " +
                         "dramatic film lighting, shallow depth of field, " +
                         "movie still, Kodak Vision3 color grade, 35mm film",
                negativePrompt = "different person, anime, cartoon, painting, blurry",
                denoisingStrength = 0.45f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            // ── Artistic styles ────────────────────────────────────────────────

            SDStyleConfig(
                styleId = "anime",
                displayName = "Anime",
                prompt = "(anime style:1.4), (preserve facial identity:1.3), " +
                         "cel shading, vibrant colors, studio ghibli quality, " +
                         "clean lineart, detailed eyes, soft gradients",
                negativePrompt = "photorealistic, 3d render, different person, " +
                                 "deformed, western cartoon, nsfw",
                denoisingStrength = 0.62f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "oil_painting",
                displayName = "Oil Painting",
                prompt = "(oil painting portrait:1.4), (preserve facial identity:1.3), " +
                         "impasto texture, classical art style, warm color palette, " +
                         "visible brushstrokes, old masters technique, museum quality",
                negativePrompt = "photorealistic, anime, cartoon, different person, blurry",
                denoisingStrength = 0.60f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "watercolor",
                displayName = "Watercolor",
                prompt = "(watercolor painting:1.4), (preserve facial identity:1.3), " +
                         "soft washes, paper texture, translucent colors, " +
                         "loose brushwork, artistic illustration",
                negativePrompt = "photorealistic, different person, dark, harsh edges",
                denoisingStrength = 0.58f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "pencil_sketch",
                displayName = "Pencil Sketch",
                prompt = "(detailed pencil sketch:1.4), (preserve facial identity:1.3), " +
                         "crosshatching, fine linework, graphite texture, " +
                         "monochrome, fine art drawing, professional sketch",
                negativePrompt = "color, photorealistic, different person, blurry",
                denoisingStrength = 0.65f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "fantasy",
                displayName = "Fantasy",
                prompt = "(fantasy portrait:1.3), (preserve facial identity:1.4), " +
                         "ethereal lighting, magical atmosphere, epic fantasy costume, " +
                         "concept art quality, artstation trending, detailed background",
                negativePrompt = "different person, deformed, low quality, modern clothing",
                denoisingStrength = 0.52f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "vintage_film",
                displayName = "Vintage Film",
                prompt = "(vintage film photography:1.3), (preserve facial identity:1.4), " +
                         "35mm film grain, muted tones, warm golden shadows, " +
                         "1970s aesthetics, analog photography, faded colors",
                negativePrompt = "different person, digital art, oversaturated, blurry",
                denoisingStrength = 0.45f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "neon_cyberpunk",
                displayName = "Neon Cyberpunk",
                prompt = "(cyberpunk portrait:1.3), (preserve facial identity:1.4), " +
                         "neon lights, futuristic city background, rain reflections, " +
                         "dramatic purple and blue lighting, sci-fi aesthetic",
                negativePrompt = "different person, bland lighting, no neon, blurry",
                denoisingStrength = 0.55f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "comic_book",
                displayName = "Comic Book",
                prompt = "(comic book style:1.4), (preserve facial identity:1.3), " +
                         "bold outlines, flat colors, halftone dots, " +
                         "Marvel comic art style, action pose",
                negativePrompt = "photorealistic, different person, blurry, watercolor",
                denoisingStrength = 0.60f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),

            SDStyleConfig(
                styleId = "pixel_art",
                displayName = "Pixel Art",
                prompt = "(pixel art portrait:1.4), (preserve facial identity:1.2), " +
                         "16-bit style, retro game character, clean pixels, " +
                         "limited color palette, RPG character portrait",
                negativePrompt = "photorealistic, blurry, anti-aliased, different person",
                denoisingStrength = 0.70f,
                useSD3 = true,
                sd3Model = SD3Model.LARGE_TURBO,
                upscaleAfter = false,
            ),
        )

        fun findById(id: String) = ALL.find { it.styleId == id }
    }
}
