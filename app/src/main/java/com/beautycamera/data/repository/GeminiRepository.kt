
package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.beautycamera.BuildConfig
import com.beautycamera.data.remote.GeminiApiService
import com.beautycamera.data.remote.GeminiContent
import com.beautycamera.data.remote.GeminiGenerationConfig
import com.beautycamera.data.remote.GeminiInlineData
import com.beautycamera.data.remote.GeminiPart
import com.beautycamera.data.remote.GeminiRequest
import com.beautycamera.data.remote.ImagenInstance
import com.beautycamera.data.remote.ImagenParameters
import com.beautycamera.data.remote.ImagenRequest
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * gemini-2.5-flash-image is the model confirmed working in AI Studio.
 */
// Confirmed from AI Studio → Get code → REST
private val GEMINI_IMAGE_MODEL_CHAIN = listOf(
    "gemini-2.5-flash-image",                     // confirmed exact model ID from AI Studio REST export
    "gemini-2.0-flash-preview-image-generation",  // fallback
    "gemini-2.0-flash-exp",                       // last fallback
)

class GeminiRepository(
    private val geminiApi: GeminiApiService
) {

    /**
     * Pipeline:
     * 1. Try Imagen models (paid — skipped on 400/404/429).
     * 2. Try Gemini image models with uploaded photo as inlineData.
     * 3. If all fail → returns failure with error message.
     */
    suspend fun generateImage(
        sourceBitmap: Bitmap,
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // Use the template prompt directly — do NOT rewrite it.
            // The prompts already contain identity-preservation instructions that must not be altered.
            val prompt = template.prompt
            Log.d(TAG, "Using prompt: $prompt")

            // Convert uploaded photo to base64 JPEG for inline image input
            val base64Image = run {
                val stream = java.io.ByteArrayOutputStream()
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }

            onStatusUpdate("Generating portrait...")
            val bitmap = generateWithFallback(prompt, base64Image, onStatusUpdate)
                ?: return@withContext Result.failure(
                    Exception("All image generation models failed. Please try again later.")
                )

            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Portrait pipeline failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Image generation fallback chain ──────────────────────────────────────

    private suspend fun generateWithFallback(
        prompt: String,
        base64Image: String,
        onStatusUpdate: (String) -> Unit
    ): Bitmap? {

        // 2a. Try Imagen models (paid — will be skipped on 400/404/429)
        // Imagen predict endpoint is text-only; pass the prompt as-is (already contains
        // identity-preservation instructions from the template).
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
            if (code in listOf(400, 404, 429, 503)) continue
            continue
        }

        // 2b. Try Gemini native image models — send the uploaded photo as inline image input.
        // Rules that match what AI Studio sends via REST:
        //   • role = "user" on the content object
        //   • inlineData part BEFORE the text part
        //   • responseModalities = ["IMAGE"] only (not ["TEXT","IMAGE"])
        val geminiImageRequest = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        // Image FIRST — model uses this as the editing source
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = base64Image
                            )
                        ),
                        // Text SECOND — style/scene instructions
                        GeminiPart(text = "This is an image editing task. Modify the provided image while preserving the same face, identity, and facial features. Do not change the person. Apply the following style: $prompt")
                    )
                )
            ),
            // ["IMAGE", "TEXT"] — confirmed from AI Studio → Get code → REST
            generationConfig = GeminiGenerationConfig(responseModalities = listOf("IMAGE", "TEXT"))
        )
        for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
            onStatusUpdate("Trying Gemini image: $modelId...")
            Log.d(TAG, "Trying Gemini image: $modelId")

            // generateContent returns a single JSON object — Retrofit can parse it correctly.
            // streamGenerateContent returns a JSON array of chunks which breaks Retrofit parsing.
            val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"

            val response = runCatching {
                geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, geminiImageRequest)
            }.getOrElse {
                Log.e(TAG, "$modelId — network error: ${it.message}")
                null
            } ?: continue

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "$modelId — raw response: $body")
                val base64 = body
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData?.data
                if (base64 != null) {
                    Log.d(TAG, "$modelId — image generation SUCCESS")
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                Log.w(TAG, "$modelId — response OK but no image part. Parts: ${body?.candidates?.firstOrNull()?.content?.parts}")
                continue
            }

            val code = response.code()
            val errorBody = response.errorBody()?.string() ?: "no error body"
            Log.e(TAG, "$modelId — FAILED $code: $errorBody")
            continue
        }

        // All models failed — return null so the caller shows a proper error to the user.
        Log.e(TAG, "All Gemini image models failed.")
        return null
    }
}
