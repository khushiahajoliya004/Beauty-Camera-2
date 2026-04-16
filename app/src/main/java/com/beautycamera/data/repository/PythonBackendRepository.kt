package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.beautycamera.data.remote.PythonBackendApiService
import com.beautycamera.data.remote.PythonGenerateRequest
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "PythonBackendRepo"

/**
 * Repository that calls the local Python AI backend for high-quality portrait generation.
 *
 * The Python server runs on the same machine as the Android emulator/device:
 *   - Android Emulator : http://10.0.2.2:8000
 *   - Real device (LAN): http://<HOST_LAN_IP>:8000
 *
 * Slotted as Step 1 in GeminiRepository's fallback chain — best quality, fully local, free.
 */
class PythonBackendRepository(
    private val apiService: PythonBackendApiService,
) {

    // Maps AIStyleTemplate IDs (used by the ViewModel) → Python backend style IDs
    private val styleMap = mapOf(
        // AI Generator card IDs
        "ai_enhanced"            to "ai_enhanced",
        "cinematic"              to "cinematic",
        "sci_fi"                 to "sci_fi",
        "oil_painting"           to "oil_painting",
        "van_gogh"               to "van_gogh",
        "watercolor"             to "watercolor",
        "pencil_sketch"          to "pencil_sketch",
        "anime"                  to "anime",
        "golden_hour"            to "golden_hour",
        "studio_pro"             to "studio_pro",
        // Legacy style IDs
        "golden_hour_rooftop"    to "golden_hour_rooftop",
        "rainy_street_cinematic" to "rainy_street_cinematic",
        "minimal_studio"         to "minimal_studio_portrait",
        "outdoor_forest"         to "outdoor_forest_light",
        "coffee_shop"            to "coffee_shop_lifestyle",
        "formal_editorial"       to "formal_editorial",
        "beach_sunset"           to "beach_sunset_portrait",
        "urban_daylight"         to "urban_street_style",
        "low_key_portrait"       to "artistic_lowkey",
        "home_interior"          to "home_interior_natural",
    )

    /**
     * Returns true if the Python backend server is reachable and fully loaded.
     * Use this to skip the backend early if it's not running.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.health()
            val body = response.body()
            val available = response.isSuccessful && body?.modelsLoaded == true
            Log.d(TAG, "Health check: available=$available status=${body?.status} device=${body?.device}")
            available
        } catch (e: Exception) {
            Log.d(TAG, "Health check failed (server not running?): ${e.message}")
            false
        }
    }

    /**
     * Generate a portrait image using the local Python AI backend.
     *
     * @param sourceBitmap  The user's photo (any resolution; server handles resize)
     * @param template      The selected AI style template
     * @return Result wrapping the generated Bitmap, or failure with a descriptive message
     */
    suspend fun generateImage(
        sourceBitmap: Bitmap,
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {},
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("Connecting to local AI server...")

            // Map template ID to Python backend style key
            val styleKey = styleMap[template.id] ?: run {
                Log.w(TAG, "No style mapping for '${template.id}', using id as-is")
                template.id
            }

            // Encode bitmap to base64 JPEG
            val base64Image = bitmapToBase64(sourceBitmap)

            onStatusUpdate("Analyzing your face...")
            val request = PythonGenerateRequest(
                image = base64Image,
                style = styleKey,
                quality = "high",
                width = 1024,
                height = 1024,
            )

            onStatusUpdate("Generating AI portrait (local)...")
            val response = apiService.generate(request)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown server error"
                Log.w(TAG, "Server returned ${response.code()}: $errorBody")
                return@withContext Result.failure(Exception("Python backend error ${response.code()}: $errorBody"))
            }

            val body = response.body()
            if (body == null || !body.success) {
                val msg = body?.error ?: "Empty response from Python backend"
                Log.w(TAG, "Generation failed: $msg")
                return@withContext Result.failure(Exception(msg))
            }

            val imageBase64 = body.image ?: return@withContext Result.failure(
                Exception("Python backend returned success=true but no image data")
            )

            onStatusUpdate("Applying face enhancement...")
            val bitmap = base64ToBitmap(imageBase64) ?: return@withContext Result.failure(
                Exception("Failed to decode generated image from Python backend")
            )

            Log.i(TAG, "Python backend generation OK in ${body.generationTimeSeconds}s using ${body.modelUsed}")
            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Python backend generation exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "base64ToBitmap failed: ${e.message}")
            null
        }
    }
}
