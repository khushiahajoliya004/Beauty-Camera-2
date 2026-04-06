package com.beautycamera.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Stability AI REST API — official API from the creators of Stable Diffusion.
 * Docs: https://platform.stability.ai/docs/api-reference
 *
 * Endpoints used:
 *  - /v2beta/stable-image/generate/core       → SDXL Core img2img
 *  - /v2beta/stable-image/generate/sd3        → Stable Diffusion 3 img2img
 *  - /v2beta/stable-image/control/structure   → ControlNet structure
 *  - /v2beta/stable-image/control/style       → ControlNet style transfer
 *  - /v2beta/stable-image/edit/inpaint        → Inpainting with mask
 *  - /v2beta/stable-image/edit/remove-background → BG removal
 *  - /v2beta/stable-image/upscale/conservative → Conservative upscaler
 *  - /v2beta/stable-image/upscale/creative    → Creative upscaler (adds detail)
 *
 * All endpoints accept multipart/form-data.
 * All return image/jpeg bytes directly (not JSON) when output_format=jpeg.
 */
interface StabilityAiApiService {

    // ── Core SDXL img2img ──────────────────────────────────────────────────────
    // Best for: portrait stylization, beauty retouching
    // Supports: image-to-image with strength, seed, style presets
    @Multipart
    @POST("v2beta/stable-image/generate/core")
    suspend fun generateCore(
        @Part("prompt")            prompt: RequestBody,
        @Part("negative_prompt")   negativePrompt: RequestBody,
        @Part                      image: MultipartBody.Part,     // source image
        @Part("strength")          strength: RequestBody,         // 0.0–1.0 (denoising)
        @Part("seed")              seed: RequestBody,             // 0 = random
        @Part("output_format")     outputFormat: RequestBody,     // jpeg | png | webp
        @Part("aspect_ratio")      aspectRatio: RequestBody,      // 1:1 | 16:9 | 9:16 etc.
        @Part("style_preset")      stylePreset: RequestBody,      // see StylePreset enum
    ): Response<ResponseBody>

    // ── Stable Diffusion 3 img2img ─────────────────────────────────────────────
    // Best for: highest quality, most prompt-accurate, most expensive
    // Models: sd3-large, sd3-large-turbo, sd3-medium
    @Multipart
    @POST("v2beta/stable-image/generate/sd3")
    suspend fun generateSD3(
        @Part("prompt")            prompt: RequestBody,
        @Part("negative_prompt")   negativePrompt: RequestBody,
        @Part                      image: MultipartBody.Part,
        @Part("strength")          strength: RequestBody,
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
        @Part("model")             model: RequestBody,            // sd3-large | sd3-large-turbo | sd3-medium
        @Part("mode")              mode: RequestBody,             // image-to-image | text-to-image
    ): Response<ResponseBody>

    // ── ControlNet — Structure ─────────────────────────────────────────────────
    // Preserves structural layout (edges, depth) while applying new style.
    // Best for: face-preserving stylization (anime, painting, sketch)
    @Multipart
    @POST("v2beta/stable-image/control/structure")
    suspend fun controlStructure(
        @Part("prompt")            prompt: RequestBody,
        @Part("negative_prompt")   negativePrompt: RequestBody,
        @Part                      image: MultipartBody.Part,     // source image (auto generates control map)
        @Part("control_strength")  controlStrength: RequestBody,  // 0.0–1.0
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
    ): Response<ResponseBody>

    // ── ControlNet — Style Transfer ────────────────────────────────────────────
    // Transfers the style from a reference image to the generated image.
    @Multipart
    @POST("v2beta/stable-image/control/style")
    suspend fun controlStyle(
        @Part("prompt")            prompt: RequestBody,
        @Part("negative_prompt")   negativePrompt: RequestBody,
        @Part                      image: MultipartBody.Part,     // style reference image
        @Part("fidelity")          fidelity: RequestBody,         // 0.0–1.0 how closely to follow style
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
    ): Response<ResponseBody>

    // ── Inpainting ─────────────────────────────────────────────────────────────
    // Replace masked region with AI-generated content.
    // White mask = regenerate, Black mask = preserve.
    @Multipart
    @POST("v2beta/stable-image/edit/inpaint")
    suspend fun inpaint(
        @Part("prompt")            prompt: RequestBody,
        @Part("negative_prompt")   negativePrompt: RequestBody,
        @Part                      image: MultipartBody.Part,
        @Part                      mask: MultipartBody.Part,      // mask bitmap
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
        @Part("grow_mask")         growMask: RequestBody,         // expand mask by N pixels (feathering)
    ): Response<ResponseBody>

    // ── Remove Background ──────────────────────────────────────────────────────
    @Multipart
    @POST("v2beta/stable-image/edit/remove-background")
    suspend fun removeBackground(
        @Part                      image: MultipartBody.Part,
        @Part("output_format")     outputFormat: RequestBody,     // png (preserves transparency)
    ): Response<ResponseBody>

    // ── Conservative Upscaler ──────────────────────────────────────────────────
    // Upscales without hallucinating new detail. Good for portraits.
    @Multipart
    @POST("v2beta/stable-image/upscale/conservative")
    suspend fun upscaleConservative(
        @Part("prompt")            prompt: RequestBody,           // describe image content
        @Part                      image: MultipartBody.Part,
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
        @Part("creativity")        creativity: RequestBody,       // 0.0–0.35 (conservative: 0.1–0.2)
    ): Response<ResponseBody>

    // ── Creative Upscaler ─────────────────────────────────────────────────────
    // Adds fine detail while upscaling. Better for non-portrait images.
    @Multipart
    @POST("v2beta/stable-image/upscale/creative")
    suspend fun upscaleCreative(
        @Part("prompt")            prompt: RequestBody,
        @Part                      image: MultipartBody.Part,
        @Part("seed")              seed: RequestBody,
        @Part("output_format")     outputFormat: RequestBody,
        @Part("creativity")        creativity: RequestBody,       // 0.0–0.35
    ): Response<ResponseBody>
}

// ── Style presets supported by SDXL Core ─────────────────────────────────────
// These are server-side LoRA-like style modifiers built into the Core endpoint.
object StabilityStylePreset {
    const val NONE              = "none"
    const val PHOTOGRAPHIC      = "photographic"
    const val DIGITAL_ART       = "digital-art"
    const val ANIME             = "anime"
    const val CINEMATIC         = "cinematic"
    const val FANTASY_ART       = "fantasy-art"
    const val COMIC_BOOK        = "comic-book"
    const val NEON_PUNK         = "neon-punk"
    const val ISOMETRIC         = "isometric"
    const val LINE_ART          = "line-art"
    const val MODELING_COMPOUND = "modeling-compound"
    const val ORIGAMI           = "origami"
    const val THREE_D_MODEL     = "3d-model"
    const val TILE_TEXTURE      = "tile-texture"
    const val PIXEL_ART         = "pixel-art"
    const val LOW_POLY          = "low-poly"
    const val ANALOG_FILM       = "analog-film"
    const val ENHANCE           = "enhance"
}

// ── SD3 model variants ─────────────────────────────────────────────────────────
object SD3Model {
    const val LARGE        = "sd3-large"         // 8B params, best quality, ~6.5 credits/image
    const val LARGE_TURBO  = "sd3-large-turbo"   // 8B params, faster, ~4 credits/image
    const val MEDIUM       = "sd3-medium"         // 2B params, faster + cheaper, ~3.5 credits/image
}
