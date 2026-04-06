package com.beautycamera.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TogetherAiApiService {
    @POST("v1/images/generations")
    suspend fun generateImage(
        @Body request: TogetherAiRequest
    ): Response<TogetherAiResponse>
}

data class TogetherAiRequest(
    val model: String = "black-forest-labs/FLUX.1-schnell-Free",
    val prompt: String,
    val image_base64: String? = null,
    val strength: Float? = 0.75f,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 4,
    val n: Int = 1,
    val response_format: String = "b64_json"
)

data class TogetherAiResponse(
    val data: List<ImageData>
)

data class ImageData(
    val b64_json: String
)
