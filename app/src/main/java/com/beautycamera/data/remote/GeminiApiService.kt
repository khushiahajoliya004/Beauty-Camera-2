
package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

// ── Gemini Request / Response models ─────────────────────────────────────────

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiGenerationConfig(
    @SerializedName("responseModalities")
    val responseModalities: List<String>
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inlineData")
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @SerializedName("mimeType")
    val mimeType: String,
    val data: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

// ── Imagen Request / Response models ─────────────────────────────────────────

data class ImagenRequest(
    val instances: List<ImagenInstance>,
    val parameters: ImagenParameters = ImagenParameters()
)

data class ImagenInstance(
    val prompt: String
)

data class ImagenParameters(
    val sampleCount: Int = 1
)

data class ImagenResponse(
    val predictions: List<ImagenPrediction>? = null,
    val error: GeminiError? = null
)

data class ImagenPrediction(
    @SerializedName("bytesBase64Encoded")
    val bytesBase64Encoded: String? = null,
    val mimeType: String? = null
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GeminiApiService {

    @POST("v1beta/models/gemini-flash-latest:generateContent")
    suspend fun generatePrompt(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @POST
    suspend fun predictImagen(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: ImagenRequest
    ): Response<ImagenResponse>

    @POST
    suspend fun generateGeminiImage(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
