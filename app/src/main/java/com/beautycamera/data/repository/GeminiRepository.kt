
package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
import android.util.Log
import com.beautycamera.BuildConfig
import com.beautycamera.data.remote.GeminiApiService
import com.beautycamera.data.remote.GeminiContent
import com.beautycamera.data.remote.GeminiGenerationConfig
import com.beautycamera.data.remote.GeminiInlineData
import com.beautycamera.data.remote.GeminiPart
import com.beautycamera.data.remote.GeminiRequest
import com.beautycamera.data.remote.ImagenInstance
import com.beautycamera.data.remote.ImagenParameters
import com.beautycamera.data.remote.ImagenRequest
import com.beautycamera.data.repository.PythonBackendRepository
import com.beautycamera.domain.model.AIStyleTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "GeminiRepository"
private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/"

// Confirmed from ListModels API — require billing (paid tier)
private val IMAGEN_MODEL_CHAIN = listOf(
    "imagen-4.0-ultra-generate-001",
    "imagen-4.0-generate-001",
    "imagen-4.0-fast-generate-001",
)

// Confirmed from ListModels API — require billing (limit=0 on free tier)
private val GEMINI_IMAGE_MODEL_CHAIN = listOf(
    "gemini-3.1-flash-image-preview",
    "gemini-3-pro-image-preview",
    "gemini-2.5-flash-image",
)

// Pollinations models tried in order on failure
private val POLLINATIONS_MODELS = listOf("flux", "turbo", "flux-realism")

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

class GeminiRepository(
    private val geminiApi: GeminiApiService,
    private val pythonBackend: PythonBackendRepository? = null,
) {

    suspend fun generateImage(
        sourceBitmap: Bitmap,
        template: AIStyleTemplate,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // ── Step 0: Try local Python backend first (best quality, free) ───
            if (pythonBackend != null) {
                Log.d(TAG, "Checking Python backend availability...")
                if (pythonBackend.isAvailable()) {
                    Log.d(TAG, "Python backend available — using it as primary generator")
                    val result = pythonBackend.generateImage(sourceBitmap, template, onStatusUpdate)
                    if (result.isSuccess) return@withContext result
                    Log.w(TAG, "Python backend failed (${result.exceptionOrNull()?.message}), falling through to cloud APIs")
                } else {
                    Log.d(TAG, "Python backend not available — using cloud APIs")
                }
            }

            val base64Image = run {
                val stream = java.io.ByteArrayOutputStream()
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }

            onStatusUpdate("Generating portrait...")
            val bitmap = generateWithFallback(sourceBitmap, template.prompt, base64Image, onStatusUpdate)
                ?: return@withContext Result.failure(Exception("Generation failed. Please check your internet connection and try again."))

            Result.success(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Main fallback chain ───────────────────────────────────────────────────

    private suspend fun generateWithFallback(
        sourceBitmap: Bitmap,
        prompt: String,
        base64Image: String,
        onStatusUpdate: (String) -> Unit
    ): Bitmap? {

        // 1. Imagen (paid — skipped when billing not enabled)
        val imagenRequest = ImagenRequest(
            instances = listOf(ImagenInstance(prompt = prompt)),
            parameters = ImagenParameters(sampleCount = 1)
        )
        for (modelId in IMAGEN_MODEL_CHAIN) {
            onStatusUpdate("Trying $modelId...")
            val url = "${GEMINI_BASE}v1beta/models/$modelId:predict"
            val response = runCatching {
                geminiApi.predictImagen(url, BuildConfig.GEMINI_API_KEY, imagenRequest)
            }.getOrElse { Log.w(TAG, "$modelId error: ${it.message}"); null } ?: continue

            if (response.isSuccessful) {
                val base64 = response.body()?.predictions?.firstOrNull()?.bytesBase64Encoded
                if (base64 != null) {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            Log.w(TAG, "$modelId → ${response.code()}: ${response.errorBody()?.string()}")
        }

        // 2. Gemini native image models (paid — skipped when billing not enabled)
        val geminiRequest = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(
                        GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image)),
                        GeminiPart(text = buildEditPrompt(prompt))
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(responseModalities = listOf("IMAGE"))
        )
        for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
            onStatusUpdate("Generating AI portrait...")
            val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"
            val response = runCatching {
                geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, geminiRequest)
            }.getOrElse { Log.e(TAG, "$modelId error: ${it.message}"); null } ?: continue

            if (response.isSuccessful) {
                val base64 = response.body()
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData?.data
                if (base64 != null) {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                Log.w(TAG, "$modelId — no image in response")
                continue
            }
            Log.e(TAG, "$modelId FAILED ${response.code()}: ${response.errorBody()?.string()}")
        }

        // 3. Pollinations.ai (completely free, no API key needed)
        Log.d(TAG, "Paid models unavailable — using Pollinations.ai free fallback")
        return generateWithPollinations(sourceBitmap, base64Image, prompt, onStatusUpdate)
    }

    // ── Pollinations.ai free generation + face transfer ───────────────────────

    private suspend fun generateWithPollinations(
        sourceBitmap: Bitmap,
        base64Image: String,
        prompt: String,
        onStatusUpdate: (String) -> Unit
    ): Bitmap? {

        // Step 1: Describe person using free Gemini text model
        onStatusUpdate("Analyzing your photo...")
        val personDescription = runCatching {
            val req = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image)),
                            GeminiPart(
                                text = "Describe the person in this photo for a portrait prompt. " +
                                    "Include gender, age range, hair color and style, eye color, skin tone, " +
                                    "facial structure. 2 sentences max. Start with 'A [age] [gender] with...'"
                            )
                        )
                    )
                )
            )
            geminiApi.generatePrompt(BuildConfig.GEMINI_API_KEY, req)
        }.getOrNull()?.body()
            ?.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull { it.text != null }
            ?.text?.trim() ?: "a person"

        Log.d(TAG, "Person description: $personDescription")

        // Step 2: Build a short, focused Pollinations prompt (shorter = more reliable)
        // Extract only the scene/style keywords from the template prompt
        val sceneKeywords = extractSceneKeywords(prompt)
        val pollinationsPrompt = "$personDescription, $sceneKeywords, " +
            "close-up portrait head and shoulders, face centered in frame, " +
            "photorealistic DSLR photo, 85mm lens, professional photography"

        // Step 3: Generate styled scene with Pollinations (try each model until one works)
        onStatusUpdate("Generating AI portrait...")
        val generatedBitmap = fetchPollinationsImage(pollinationsPrompt) ?: return null

        // Step 4: Paste the original face over the generated image to preserve identity
        onStatusUpdate("Applying your face...")
        val result = transferFace(sourceBitmap, generatedBitmap)
        // Only recycle if transferFace returned a NEW bitmap (not the same reference on exception)
        if (result !== generatedBitmap) generatedBitmap.recycle()
        return result
    }

    private fun fetchPollinationsImage(prompt: String): Bitmap? {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        for (model in POLLINATIONS_MODELS) {
            val url = "https://image.pollinations.ai/prompt/$encoded" +
                "?width=1024&height=1024&nologo=true&model=$model"
            Log.d(TAG, "Pollinations [$model] requesting...")
            val bmp = tryFetchBitmap(url, model)
            if (bmp != null) return bmp
        }
        return null
    }

    private fun tryFetchBitmap(url: String, model: String): Bitmap? {
        return try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Pollinations [$model] HTTP ${response.code}")
                response.body?.close()
                return null
            }
            val bytes = response.body?.bytes()
            if (bytes == null) {
                Log.w(TAG, "Pollinations [$model] empty body")
                return null
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                Log.d(TAG, "Pollinations [$model] SUCCESS ${bytes.size}B")
            } else {
                Log.w(TAG, "Pollinations [$model] decode failed")
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Pollinations [$model] exception: ${e.message}")
            null
        }
    }

    // ── Face transfer ─────────────────────────────────────────────────────────

    private data class FaceInfo(val cx: Float, val cy: Float, val eyeDist: Float)

    /**
     * Detect face in the SOURCE (user's real photo) using Android FaceDetector.
     * Falls back to upper-center heuristic if detection fails.
     */
    @Suppress("DEPRECATION")
    private fun detectSourceFace(bitmap: Bitmap): FaceInfo {
        return try {
            val bmp565 = bitmap.copy(Bitmap.Config.RGB_565, false)
            val detector = android.media.FaceDetector(bmp565.width, bmp565.height, 1)
            val faces = arrayOfNulls<android.media.FaceDetector.Face>(1)
            val found = detector.findFaces(bmp565, faces)
            bmp565.recycle()
            if (found > 0 && faces[0] != null) {
                val mid = PointF()
                faces[0]!!.getMidPoint(mid)
                Log.d(TAG, "Source face detected: cx=${mid.x} cy=${mid.y} eyeDist=${faces[0]!!.eyesDistance()}")
                FaceInfo(mid.x, mid.y, faces[0]!!.eyesDistance())
            } else {
                Log.d(TAG, "Source face not detected — using heuristic")
                FaceInfo(bitmap.width * 0.50f, bitmap.height * 0.37f, bitmap.width * 0.22f)
            }
        } catch (e: Exception) {
            FaceInfo(bitmap.width * 0.50f, bitmap.height * 0.37f, bitmap.width * 0.22f)
        }
    }

    /**
     * Transfers the face from [source] onto [generated].
     *
     * For the generated image we use a FIXED portrait heuristic instead of
     * trying to detect the face — Pollinations portrait prompts reliably place
     * the face centered at ~34% height, so detection is not needed and would
     * be unreliable on AI images anyway.
     */
    private fun transferFace(source: Bitmap, generated: Bitmap): Bitmap {
        return try {
            val srcFace = detectSourceFace(source)

            // Fixed heuristic for Pollinations portrait output
            val genFace = FaceInfo(
                cx = generated.width * 0.50f,
                cy = generated.height * 0.34f,
                eyeDist = generated.width * 0.26f
            )

            val scale = genFace.eyeDist / srcFace.eyeDist

            // Extract face patch from source with generous padding (forehead + chin + cheeks)
            val pad = srcFace.eyeDist * 2.2f
            val left   = (srcFace.cx - pad).toInt().coerceAtLeast(0)
            val top    = (srcFace.cy - pad * 1.4f).toInt().coerceAtLeast(0)
            val right  = (srcFace.cx + pad).toInt().coerceAtMost(source.width)
            val bottom = (srcFace.cy + pad * 1.6f).toInt().coerceAtMost(source.height)

            val patchW = (right - left).coerceAtLeast(1)
            val patchH = (bottom - top).coerceAtLeast(1)
            val patch  = Bitmap.createBitmap(source, left, top, patchW, patchH)

            // Scale patch to match generated face size
            val scaledW = (patchW * scale).toInt().coerceAtLeast(1)
            val scaledH = (patchH * scale).toInt().coerceAtLeast(1)
            val scaled  = Bitmap.createScaledBitmap(patch, scaledW, scaledH, true)
            patch.recycle()

            // Eye centre position inside the scaled patch
            val eyeInPatchX = (srcFace.cx - left) * scale
            val eyeInPatchY = (srcFace.cy - top)  * scale

            // Paste position — align eye centres between source and generated
            val pasteX = genFace.cx - eyeInPatchX
            val pasteY = genFace.cy - eyeInPatchY

            // Apply feathered ellipse mask so edges blend into generated background
            val masked = applyFeatheredEllipseMask(scaled, genFace.eyeDist * scale)
            scaled.recycle()

            // Composite masked face onto generated image
            val result = generated.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(result).drawBitmap(masked, pasteX, pasteY, null)
            masked.recycle()

            Log.d(TAG, "Face transfer done — paste=(${pasteX.toInt()},${pasteY.toInt()}) scale=%.2f".format(scale))
            result
        } catch (e: Exception) {
            Log.e(TAG, "transferFace error: ${e.message}", e)
            generated
        }
    }

    /**
     * Returns [face] with alpha that fades to 0 outside a face-shaped ellipse.
     * Centre is placed at the eye line (43% down), giving natural room for
     * forehead above and chin below.
     */
    private fun applyFeatheredEllipseMask(face: Bitmap, eyeDistInPatch: Float): Bitmap {
        val w  = face.width
        val h  = face.height
        val cx = w * 0.50f
        val cy = h * 0.43f                                           // eye line ~43% down in patch
        val rx = (eyeDistInPatch * 1.75f).coerceAtMost(w * 0.49f)
        val ry = (eyeDistInPatch * 2.30f).coerceAtMost(h * 0.49f)

        // Mask: radial gradient opaque → transparent, clipped to ellipse
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc   = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, maxOf(rx, ry),
                intArrayOf(Color.WHITE, Color.WHITE, 0x00FFFFFF),
                floatArrayOf(0f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        mc.clipPath(Path().apply {
            addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW)
        })
        mc.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // DST_IN: keep face pixels where mask is opaque, transparent where mask fades
        val result = face.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(result).drawBitmap(mask, 0f, 0f, Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        mask.recycle()
        return result
    }

    // ── Prompt helpers ────────────────────────────────────────────────────────

    /**
     * Extracts the core scene/style keywords from the full template prompt
     * to keep the Pollinations URL short and focused.
     */
    private fun extractSceneKeywords(templatePrompt: String): String {
        // Remove the identity-preservation header boilerplate, keep the scene description
        val cleaned = templatePrompt
            .replace(Regex("Use the provided image.*?described\\.\\s*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Preserve the exact.*?\\.\\s*"), "")
            .replace(Regex("Do not change.*?\\.\\s*"), "")
            .trim()
        // Limit to ~200 chars so URL stays reasonable
        return if (cleaned.length > 200) cleaned.substring(0, 200) else cleaned
    }

    private fun buildEditPrompt(templatePrompt: String): String =
        """Edit this portrait photo. Keep the person's face, eyes, nose, mouth, expression, skin tone and hair EXACTLY the same. Only change the background, environment, clothing and lighting as described below.

$templatePrompt

Output must look like a real DSLR photograph. Photorealistic, not illustration, not painting."""
}
