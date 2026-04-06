package com.beautycamera.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class StableHordeRequest(
    val prompt: String,
    val params: StableHordeParams,
    val nsfw: Boolean = false,
    @SerializedName("trusted_workers") val trustedWorkers: Boolean = false,
    @SerializedName("slow_workers") val slowWorkers: Boolean = true,
    @SerializedName("source_image") val sourceImage: String,
    @SerializedName("source_processing") val sourceProcessing: String = "img2img",
    val r2: Boolean = false,
    val models: List<String> = emptyList()
)

data class StableHordeParams(
    @SerializedName("sampler_name") val samplerName: String = "k_euler_a",
    @SerializedName("cfg_scale") val cfgScale: Double = 9.0,
    @SerializedName("denoising_strength") val denoisingStrength: Double = 0.45,
    val height: Int = 512,
    val width: Int = 512,
    val steps: Int = 30,
    val n: Int = 1
)

data class StableHordeSubmitResponse(
    val id: String?,
    val kudos: Double?,
    val message: String?,
    val errors: Map<String, String>?
)

data class StableHordeCheckResponse(
    val done: Boolean,
    val faulted: Boolean,
    val waiting: Int,
    val processing: Int,
    @SerializedName("queue_position") val queuePosition: Int?,
    val finished: Int
)

data class StableHordeStatusResponse(
    val done: Boolean,
    val faulted: Boolean,
    val generations: List<StableHordeGeneration>?
)

data class StableHordeGeneration(
    val img: String,
    val id: String?,
    val censored: Boolean?
)

interface StableHordeApiService {
    @POST("api/v2/generate/async")
    suspend fun submitJob(
        @Body request: StableHordeRequest
    ): Response<StableHordeSubmitResponse>

    @GET("api/v2/generate/check/{id}")
    suspend fun checkJob(
        @Path("id") id: String
    ): Response<StableHordeCheckResponse>

    @GET("api/v2/generate/status/{id}")
    suspend fun getResult(
        @Path("id") id: String
    ): Response<StableHordeStatusResponse>
}
