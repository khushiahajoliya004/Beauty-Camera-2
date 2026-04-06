package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.beautycamera.data.remote.HFRequest
import com.beautycamera.data.remote.HuggingFaceApiService
import com.beautycamera.data.remote.SDStyleConfig
import com.beautycamera.data.remote.StabilityAiApiService
import com.beautycamera.data.remote.TogetherAiApiService
import com.beautycamera.data.remote.TogetherAiRequest
import com.beautycamera.domain.model.AIStyleTemplate
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.CannyPreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.ByteArrayOutputStream

class SDRepository(
    private val stabilityApi: StabilityAiApiService,
    private val togetherApi: TogetherAiApiService,
    private val hfApi: HuggingFaceApiService,
    private val bitmapUtils: BitmapUtils,
    private val cannyPreprocessor: CannyPreprocessor,
) {

    suspend fun generateFromTemplate(
        sourceBitmap: Bitmap,
        template: AIStyleTemplate,
        seed: Int = 0,
        onStatusUpdate: (String) -> Unit = {},
    ): Result<Bitmap> {
        val style = SDStyleConfig.findById(template.id)
            ?: SDStyleConfig.findById("portrait_enhance")!!.copy(
                styleId = template.id,
                displayName = template.name,
                prompt = template.prompt,
                negativePrompt = template.negativePrompt,
            )
        return runPipeline(sourceBitmap, style, seed, onStatusUpdate)
    }

    private suspend fun runPipeline(
        sourceBitmap: Bitmap,
        style: SDStyleConfig,
        seed: Int,
        onStatusUpdate: (String) -> Unit,
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        // 1. Try FLUX.1 [schnell] via Together AI
        onStatusUpdate("Generating with FLUX.1 [schnell]...")
        try {
            val fluxResult = runFluxTogether(sourceBitmap, style.prompt)
            Log.d("SDRepository", "FLUX generation successful via Together AI")
            return@withContext Result.success(fluxResult)
        } catch (e: Exception) {
            Log.e("SDRepository", "FLUX Together AI failed: ${e.message}")
        }

        // 2. Try FLUX.1 [schnell] via HuggingFace
        onStatusUpdate("FLUX Together failed, trying HuggingFace...")
        try {
            val fluxResult = runFluxHuggingFace(sourceBitmap, style.prompt)
            Log.d("SDRepository", "FLUX generation successful via HuggingFace")
            return@withContext Result.success(fluxResult)
        } catch (e: Exception) {
            Log.e("SDRepository", "FLUX HuggingFace failed: ${e.message}")
        }

        // 3. Fallback to Stable Diffusion (Stability AI)
        onStatusUpdate("FLUX failed, falling back to Stable Diffusion...")
        try {
            val scaledBitmap = bitmapUtils.scaleBitmap(sourceBitmap, 1024, 1024)
            val imageBytes = toJpegBytes(scaledBitmap)

            // Optimized: Use SDXL Core or ControlNet instead of SD3 to save credits
            val generatedBytes: ByteArray = when {
                style.useControlNet -> runControlNetStructure(
                    imageBytes = imageBytes,
                    prompt = style.prompt,
                    negativePrompt = style.negativePrompt,
                    controlStrength = style.controlnetScale,
                    seed = seed,
                )
                else -> runSDXLCore(
                    imageBytes = imageBytes,
                    prompt = style.prompt,
                    negativePrompt = style.negativePrompt,
                    strength = style.denoisingStrength,
                    seed = seed,
                    stylePreset = style.sdxlStylePreset,
                )
            }

            val resultBitmap = BitmapFactory.decodeByteArray(generatedBytes, 0, generatedBytes.size)
                ?: throw Exception("Failed to decode SD result")
            
            Log.d("SDRepository", "Stable Diffusion fallback successful")
            return@withContext Result.success(resultBitmap)
        } catch (e: Exception) {
            Log.e("SDRepository", "Stable Diffusion fallback failed: ${e.message}")
            Result.failure(Exception("All models failed: ${e.message}"))
        }
    }

    private suspend fun runFluxTogether(sourceBitmap: Bitmap, prompt: String): Bitmap {
        // Correct Image-to-Image for Together AI
        val scaled = bitmapUtils.scaleBitmap(sourceBitmap, 1024, 1024)
        val imageBase64 = toBase64(scaled)

        val request = TogetherAiRequest(
            prompt = prompt,
            image_base64 = imageBase64,
            strength = 0.85f, // Higher strength for better transformation
            width = 1024,
            height = 1024,
            steps = 4
        )
        val response = togetherApi.generateImage(request)
        if (response.isSuccessful) {
            val b64 = response.body()?.data?.firstOrNull()?.b64_json ?: throw Exception("Empty response from Together AI")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Failed to decode FLUX image")
        } else {
            throw Exception("Together AI error: ${response.errorBody()?.string()}")
        }
    }

    private suspend fun runFluxHuggingFace(sourceBitmap: Bitmap, prompt: String): Bitmap {
        val request = HFRequest(
            inputs = prompt,
            parameters = mapOf(
                "num_inference_steps" to 4,
                "guidance_scale" to 0.0,
                "width" to 1024,
                "height" to 1024
            )
        )
        val response = hfApi.generateFlux(request)
        if (response.isSuccessful) {
            val bytes = response.body()?.bytes() ?: throw Exception("Empty response from HuggingFace")
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("Failed to decode HF image")
        } else {
            throw Exception("HuggingFace error: ${response.errorBody()?.string()}")
        }
    }

    private suspend fun runControlNetStructure(
        imageBytes: ByteArray,
        prompt: String,
        negativePrompt: String,
        controlStrength: Float,
        seed: Int,
    ): ByteArray = stabilityApi.controlStructure(
        prompt = prompt.toTextBody(),
        negativePrompt = negativePrompt.toTextBody(),
        image = imageBytes.toImagePart("image"),
        controlStrength = controlStrength.toString().toTextBody(),
        seed = seed.toString().toTextBody(),
        outputFormat = "jpeg".toTextBody(),
    ).bodyOrThrow("ControlNet structure")

    private suspend fun runSDXLCore(
        imageBytes: ByteArray,
        prompt: String,
        negativePrompt: String,
        strength: Float,
        seed: Int,
        stylePreset: String,
    ): ByteArray = stabilityApi.generateCore(
        prompt = prompt.toTextBody(),
        negativePrompt = negativePrompt.toTextBody(),
        image = imageBytes.toImagePart("image"),
        strength = strength.toString().toTextBody(),
        seed = seed.toString().toTextBody(),
        outputFormat = "jpeg".toTextBody(),
        aspectRatio = "1:1".toTextBody(),
        stylePreset = stylePreset.toTextBody(),
    ).bodyOrThrow("SDXL Core")

    private fun toJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    private fun toBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun String.toTextBody(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())

    private fun ByteArray.toImagePart(name: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            name, "image.jpg",
            toRequestBody("image/jpeg".toMediaTypeOrNull())
        )

    private fun Response<ResponseBody>.bodyOrThrow(step: String): ByteArray {
        if (!isSuccessful) {
            val err = errorBody()?.string() ?: "no error body"
            throw Exception("$step failed ${code()}: $err")
        }
        return body()?.bytes() ?: throw Exception("$step returned empty body")
    }
}
