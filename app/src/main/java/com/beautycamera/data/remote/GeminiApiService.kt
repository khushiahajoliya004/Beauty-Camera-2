
package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

// ── Request models ────────────────────────────────────────────────────────────

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

// ── Response models ───────────────────────────────────────────────────────────

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

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GeminiApiService {

    // Text prompt refinement — primary (gemini-flash-latest = Gemini 2.5 Flash)
    @POST("v1beta/models/gemini-flash-latest:generateContent")
    suspend fun generatePrompt(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    // Text prompt refinement — fallback (explicit model ID)
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generatePromptFallback(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    // Dynamic Gemini image generation — model ID supplied at call site for fallback chain
    @POST
    suspend fun generateGeminiImage(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
