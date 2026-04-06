package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

// ── Response models ───────────────────────────────────────────────────────────

data class PredictionRequest(
    val version: String,
    val input: Map<String, Any>
)

data class PredictionResponse(
    val id: String,
    val status: String,          // "starting" | "processing" | "succeeded" | "failed" | "canceled"
    val output: Any?,            // String URL or List<String> URLs depending on model
    val error: String?,
    @SerializedName("urls") val urls: PredictionUrls?,
    val metrics: PredictionMetrics?,
)

data class PredictionUrls(
    val get: String,
    val cancel: String
)

data class PredictionMetrics(
    @SerializedName("predict_time") val predictTime: Float?,
)

// ── API Interface ─────────────────────────────────────────────────────────────

interface ReplicateApiService {

    @POST("v1/predictions")
    suspend fun createPrediction(
        @Body request: PredictionRequest
    ): Response<PredictionResponse>

    @GET
    suspend fun getPrediction(
        @Url url: String
    ): Response<PredictionResponse>
}
