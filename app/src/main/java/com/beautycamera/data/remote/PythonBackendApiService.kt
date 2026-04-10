package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ── Request ───────────────────────────────────────────────────────────────────

data class PythonGenerateRequest(
    @SerializedName("image")   val image: String,
    @SerializedName("style")   val style: String,
    @SerializedName("quality") val quality: String = "high",
    @SerializedName("width")   val width: Int = 1024,
    @SerializedName("height")  val height: Int = 1024,
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class PythonGenerateResponse(
    @SerializedName("success")                   val success: Boolean,
    @SerializedName("image")                     val image: String?,
    @SerializedName("generation_time_seconds")   val generationTimeSeconds: Float?,
    @SerializedName("model_used")                val modelUsed: String?,
    @SerializedName("error")                     val error: String?,
    @SerializedName("code")                      val code: String?,
)

data class PythonHealthResponse(
    @SerializedName("status")        val status: String,
    @SerializedName("models_loaded") val modelsLoaded: Boolean,
    @SerializedName("device")        val device: String,
    @SerializedName("vram_free_gb")  val vramFreeGb: Float?,
)

data class PythonStyle(
    @SerializedName("id")            val id: String,
    @SerializedName("name")          val name: String,
    @SerializedName("preview_color") val previewColor: String,
)

data class PythonStylesResponse(
    @SerializedName("styles") val styles: List<PythonStyle>,
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface PythonBackendApiService {

    @GET("health")
    suspend fun health(): Response<PythonHealthResponse>

    @GET("styles")
    suspend fun styles(): Response<PythonStylesResponse>

    @POST("generate")
    suspend fun generate(@Body request: PythonGenerateRequest): Response<PythonGenerateResponse>
}
