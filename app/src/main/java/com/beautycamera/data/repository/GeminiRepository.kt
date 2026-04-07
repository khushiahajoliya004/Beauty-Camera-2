
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
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GeminiRepository"
private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/"

/**
 * Gemini image generation models tried in order.
 * Each is tried with responseModalities: ["TEXT","IMAGE"].
 * Skipped on 404 (not found) or 429 (rate limited).
 */
private val GEMINI_IMAGE_MODEL_CHAIN = listOf(
    "gemini-2.0-flash-exp",
    "gemini-2.0-flash-preview-image-generation",
    "gemini-2.5-flash",
    "gemini-flash-latest",
)

class GeminiRepository(
    private val geminiApi: GeminiApiService
) {

    /**
     * Pipeline:
     * 1. Refine style prompt via Gemini text model.
     * 2. Generate image — tries each Gemini image model in order,
     *    skipping on 404 / 429 / 503.
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
                    Exception(
                        "Gemini image generation is not available on the free tier for this account. " +
                        "Enable billing or use a key with image generation access."
                    )
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
                                "for Gemini image generation. Preserve all visual details, lighting, " +
                                "clothing, and mood. Keep it under 120 words. Output ONLY the final prompt.\n\n${template.prompt}"
                        )
                    )
                )
            )
        )

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

    // ── Step 2: Gemini image generation fallback chain ────────────────────────

    private suspend fun generateWithFallback(
        prompt: String,
        onStatusUpdate: (String) -> Unit
    ): Bitmap? {
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(responseModalities = listOf("TEXT", "IMAGE"))
        )

        for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
            onStatusUpdate("Trying $modelId...")
            Log.d(TAG, "Trying Gemini image model: $modelId")

            val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"
            val response = runCatching {
                geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, request)
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
                    Log.d(TAG, "Success with model: $modelId")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                Log.w(TAG, "$modelId — response OK but no image data in parts")
                continue
            }

            val code = response.code()
            val body = response.errorBody()?.string() ?: ""
            Log.w(TAG, "$modelId → $code: $body")

            // Skip to next model on these codes
            if (code in listOf(404, 429, 503)) continue

            // 400 = bad request (could be model doesn't support image output) — skip
            if (code == 400) continue
        }

        return null
    }
}
