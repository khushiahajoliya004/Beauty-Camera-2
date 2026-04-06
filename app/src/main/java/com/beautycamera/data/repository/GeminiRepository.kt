
package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.beautycamera.BuildConfig
import com.beautycamera.data.remote.GeminiApiService
import com.beautycamera.data.remote.GeminiContent
import com.beautycamera.data.remote.GeminiPart
import com.beautycamera.data.remote.GeminiRequest
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeminiRepository(
    private val geminiApi: GeminiApiService
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // Flux image gen can take 60-90s
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Pipeline:
     * 1. Gemini (v1beta gemini-2.0-flash) -> Generates/Refines a high-quality prompt.
     * 2. Pollinations.ai (Free, Flux model) -> Generates the actual portrait bitmap.
     */
    suspend fun generateImage(
        sourceBitmap: Bitmap, // Note: For this free pipeline, we use the prompt for transformation
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("Refining prompt with Gemini...")

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = "You are an AI prompt engineer. Create a highly detailed image generation prompt for a portrait. " +
                                    "Style: ${template.name}. Details: ${template.prompt}. " +
                                    "Ensure the prompt describes a high-quality, photorealistic portrait. " +
                                    "Keep it under 60 words. Output ONLY the refined prompt text.")
                        )
                    )
                )
            )

            val response = geminiApi.generatePrompt(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )

            val defaultPrompt = "${template.name} style portrait, high quality, photorealistic, professional photography"
            val finalPrompt = if (response.isSuccessful) {
                response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: defaultPrompt
            } else {
                Log.e("GeminiRepository", "Prompt refinement failed (${response.code()}), using default")
                defaultPrompt
            }

            onStatusUpdate("Generating portrait image...")

            // Pollinations.ai is a free, high-quality image generation tool (using Flux/SDXL)
            val encodedPrompt = URLEncoder.encode(finalPrompt, "UTF-8")
            val url = "https://pollinations.ai/p/$encodedPrompt?width=1024&height=1024&seed=${System.currentTimeMillis()}&model=flux&nologo=true"

            val imageRequest = Request.Builder().url(url).build()
            val imageResponse = httpClient.newCall(imageRequest).execute()

            if (!imageResponse.isSuccessful) {
                throw Exception("Image generator error: ${imageResponse.code}")
            }

            val bytes = imageResponse.body?.bytes() ?: throw Exception("Empty image received")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw Exception("Failed to decode portrait")

            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e("GeminiRepository", "AI Portrait pipeline failed: ${e.message}")
            Result.failure(e)
        }
    }
}
