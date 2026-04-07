
package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.beautycamera.BuildConfig
import com.beautycamera.data.remote.GeminiApiService
import com.beautycamera.data.remote.GeminiContent
import com.beautycamera.data.remote.GeminiGenerationConfig
import com.beautycamera.data.remote.GeminiPart
import com.beautycamera.data.remote.GeminiRequest
import com.beautycamera.data.remote.ImagenInstance
import com.beautycamera.data.remote.ImagenParameters
import com.beautycamera.data.remote.ImagenRequest
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val TAG = "GeminiRepository"
private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/"

/**
 * Imagen model IDs tried in order.
 * All require a paid Google AI plan — they will be skipped on 400/404/429
 * and the pipeline falls through to free alternatives.
 */
private val IMAGEN_MODEL_CHAIN = listOf(
    "imagen-4.0-fast-generate-001",
    "imagen-4.0-generate-001",
    "imagen-4.0-generate-002",
    "imagen-4.0-ultra-generate-001",
    "imagen-3.0-generate-002",
)

/**
 * Gemini native image models — tried after Imagen fails.
 * These show 0/0 limits on free tier but worth attempting.
 */
private val GEMINI_IMAGE_MODEL_CHAIN = listOf(
    "gemini-2.0-flash-exp",
    "gemini-2.0-flash-preview-image-generation",
)

class GeminiRepository(
    private val geminiApi: GeminiApiService
) {

    /**
     * Pipeline:
     * 1. Refine style prompt via Gemini text model.
     * 2. Try Imagen 4 → Imagen 3 → Gemini image models.
     * 3. If all paid/unavailable → Pollinations.ai (free, no API key).
     */
    suspend fun generateImage(
        sourceBitmap: Bitmap,
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("Refining prompt...")
            val finalPrompt = refinePrompt(template)
            Log.d(TAG, "Final prompt: $finalPrompt")

            onStatusUpdate("Generating portrait...")
            val bitmap = generateWithFallback(finalPrompt, onStatusUpdate)
                ?: return@withContext Result.failure(
                    Exception("All image generation models failed. Please try again later.")
                )

            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Portrait pipeline failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Step 1: Prompt refinement ─────────────────────────────────────────────

    private suspend fun refinePrompt(template: AIStyleTemplate): String {
        val default = "${template.name} style portrait, high quality, photorealistic, professional photography"
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "You are an AI prompt engineer. Optimize the following portrait prompt " +
                                "for image generation. Preserve all visual details, lighting, clothing, and mood. " +
                                "Keep it under 120 words. Output ONLY the final prompt text.\n\n${template.prompt}"
                        )
                    )
                )
            )
        )

        // Try gemini-flash-latest, then explicit gemini-2.5-flash
        for (call in listOf(
            runCatching { geminiApi.generatePrompt(BuildConfig.GEMINI_API_KEY, request) },
            runCatching { geminiApi.generatePromptFallback(BuildConfig.GEMINI_API_KEY, request) }
        )) {
            val text = call.getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?.takeIf { it.isNotBlank() }
            if (text != null) return text
        }

        Log.w(TAG, "Prompt refinement failed, using default")
        return default
    }

    // ── Step 2: Image generation fallback chain ───────────────────────────────

    private suspend fun generateWithFallback(
        prompt: String,
        onStatusUpdate: (String) -> Unit
    ): Bitmap? {

        // 2a. Try Imagen models (paid — will be skipped on 400/404/429)
        val imagenRequest = ImagenRequest(
            instances = listOf(ImagenInstance(prompt = prompt)),
            parameters = ImagenParameters(sampleCount = 1)
        )
        for (modelId in IMAGEN_MODEL_CHAIN) {
            onStatusUpdate("Trying Imagen: $modelId...")
            Log.d(TAG, "Trying Imagen: $modelId")
            val url = "${GEMINI_BASE}v1beta/models/$modelId:predict"

            val response = runCatching {
                geminiApi.predictImagen(url, BuildConfig.GEMINI_API_KEY, imagenRequest)
            }.getOrElse {
                Log.w(TAG, "$modelId — network error: ${it.message}")
                null
            } ?: continue

            if (response.isSuccessful) {
                val base64 = response.body()?.predictions?.firstOrNull()?.bytesBase64Encoded
                if (base64 != null) {
                    Log.d(TAG, "Success: $modelId")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                continue
            }

            val code = response.code()
            val body = response.errorBody()?.string() ?: ""
            Log.w(TAG, "$modelId → $code: $body")

            // Skip: wrong model ID, rate limited, unavailable, or paid-plan-only
            if (code in listOf(400, 404, 429, 503)) continue

            // Any other error is unrecoverable for this model — still try next
            continue
        }

        // 2b. Try Gemini native image models
        val geminiImageRequest = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(responseModalities = listOf("TEXT", "IMAGE"))
        )
        for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
            onStatusUpdate("Trying Gemini image: $modelId...")
            Log.d(TAG, "Trying Gemini image: $modelId")
            val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"

            val response = runCatching {
                geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, geminiImageRequest)
            }.getOrElse {
                Log.w(TAG, "$modelId — network error: ${it.message}")
                null
            } ?: continue

            if (response.isSuccessful) {
                val base64 = response.body()
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData?.data
                if (base64 != null) {
                    Log.d(TAG, "Success: $modelId")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                continue
            }

            val code = response.code()
            val body = response.errorBody()?.string() ?: ""
            Log.w(TAG, "$modelId → $code: $body")
            continue
        }

        // 2c. Last resort: Pollinations.ai — completely free, no API key needed
        onStatusUpdate("Using free fallback (Pollinations.ai)...")
        Log.d(TAG, "Falling back to Pollinations.ai")
        return tryPollinations(prompt)
    }

    /**
     * Pollinations.ai — free image generation, returns JPEG/PNG bytes directly.
     * URL format: https://image.pollinations.ai/prompt/{encoded}?model=flux&width=512&height=512
     */
    private fun tryPollinations(prompt: String): Bitmap? {
        return try {
            val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
            val url = "https://image.pollinations.ai/prompt/$encoded" +
                "?model=flux&width=768&height=768&nologo=true&enhance=true"
            Log.d(TAG, "Pollinations URL: $url")
            val bytes = URL(url).readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Pollinations.ai failed: ${e.message}")
            null
        }
    }
}
