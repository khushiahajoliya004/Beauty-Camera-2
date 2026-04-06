package com.beautycamera.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class HFRequest(
    val inputs: String,
    val parameters: Map<String, Any> = emptyMap()
)

interface HuggingFaceApiService {
    @POST("models/black-forest-labs/FLUX.1-schnell")
    suspend fun generateFlux(
        @Body request: HFRequest
    ): Response<ResponseBody>
}
