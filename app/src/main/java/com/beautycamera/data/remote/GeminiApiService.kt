
package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request models ────────────────────────────────────────────────────────────

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null
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
    // gemini-2.0-flash is the current stable model (April 2026)
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generatePrompt(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
