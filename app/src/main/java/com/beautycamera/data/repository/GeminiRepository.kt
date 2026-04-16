
package com.beautycamera.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

// Gemini image generation/editing models — support BOTH text→image AND image+text→image
// Same models used by the Gemini app and website
private val GEMINI_IMAGE_MODEL_CHAIN = listOf(
    "gemini-2.0-flash-preview-image-generation",
    "gemini-2.0-flash-exp-image-generation",
)

// Pollinations models tried in order on failure
private val POLLINATIONS_MODELS = listOf("flux", "turbo", "flux-realism")

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private const val DEFAULT_NEGATIVE =
    "ugly, deformed, blurry, low quality, bad anatomy, extra limbs, mutated, watermark, text"

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

class GeminiRepository(
    private val geminiApi: GeminiApiService,
    private val pythonBackend: PythonBackendRepository? = null,
) {

    // Holds the analyzed gender + full description of the uploaded person
    private data class PersonInfo(val gender: String, val description: String)

    /**
     * AI portrait style transfer — like uploading a photo in the Gemini app.
     *
     * PIPELINE:
     * 1. Analyze the photo first → get EXACT gender + appearance (prevents gender swap)
     * 2. PRIMARY: Gemini image-to-image with gender-locked prompt (photo as input)
     * 3. FALLBACK: Gemini text-to-image using the person description + style
     * 4. LAST: Pollinations.ai (free, always works)
     */
    suspend fun generateWithPhotoAndPrompt(
        sourceBitmap: Bitmap,
        stylePrompt: String,
        styleId: String = "",
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            // ── Step 0: Python GPU backend ────────────────────────────────────
            if (pythonBackend != null && pythonBackend.isAvailable()) {
                val template = AIStyleTemplate(
                    id = styleId.ifBlank { "ai_enhanced" }, name = "Custom",
                    prompt = stylePrompt, negativePrompt = DEFAULT_NEGATIVE, thumbnailResId = 0
                )
                val result = pythonBackend.generateImage(sourceBitmap, template, onStatusUpdate)
                if (result.isSuccess) return@withContext result
                Log.w(TAG, "Python backend failed — falling back to cloud")
            }

            val base64Image = bitmapToBase64(sourceBitmap)

            // ── Step 1: Analyze person — gender + appearance (CRITICAL) ───────
            // This runs first so ALL subsequent prompts know the exact gender.
            onStatusUpdate("Reading your photo...")
            val person = analyzePersonDetailed(base64Image)
            Log.d(TAG, "Person → gender=${person.gender}, desc=${person.description}")

            // ── Step 2: Gemini image-to-image (sends actual photo) ────────────
            // Prompt explicitly states the detected gender so Gemini CANNOT swap it.
            onStatusUpdate("Generating AI portrait...")
            val imageEditPrompt = buildGenderLockedEditPrompt(person, stylePrompt)

            for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
                val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(
                                GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image)),
                                GeminiPart(text = imageEditPrompt)
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseModalities = listOf("IMAGE", "TEXT")
                    )
                )
                val response = runCatching {
                    geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, request)
                }.getOrElse { Log.w(TAG, "$modelId error: ${it.message}"); null } ?: continue

                if (response.isSuccessful) {
                    val base64 = response.body()
                        ?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull { it.inlineData != null }
                        ?.inlineData?.data
                    if (base64 != null) {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            Log.d(TAG, "Image-to-image success ($modelId)")
                            return@withContext Result.success(bmp)
                        }
                    }
                    Log.w(TAG, "$modelId — no image in response")
                } else {
                    Log.w(TAG, "$modelId → ${response.code()}: ${response.errorBody()?.string()}")
                }
            }

            // ── Step 3: Text-to-image fallback (person desc + style) ──────────
            // Person description already has correct gender — no gender swap possible.
            onStatusUpdate("Generating AI art...")
            val textPrompt = buildTextPrompt(person, stylePrompt)
            Log.d(TAG, "Text prompt: $textPrompt")

            for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
                val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = textPrompt))
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseModalities = listOf("IMAGE", "TEXT")
                    )
                )
                val response = runCatching {
                    geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, request)
                }.getOrElse { null } ?: continue

                if (response.isSuccessful) {
                    val base64 = response.body()
                        ?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull { it.inlineData != null }
                        ?.inlineData?.data
                    if (base64 != null) {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) return@withContext Result.success(bmp)
                    }
                }
            }

            // ── Step 4: Pollinations free (always works) ──────────────────────
            // Generate the COMPLETE full-body image from text only.
            // The face, body, clothes, and background are all generated together so
            // they naturally match in size, lighting, style, and proportion.
            // (Pasting a face photo over an AI body always looks editor-made because
            //  the two images have different lighting, scale, and style — Gemini
            //  image-to-image above is the right path for face-matching.)
            onStatusUpdate("Generating full body AI image...")
            val seed = sourceBitmap.hashCode().toLong().let {
                if (it == 0L) System.currentTimeMillis() else Math.abs(it) + System.currentTimeMillis() % 10000
            }
            val generatedBmp = fetchPollinationsImage(textPrompt, seed)
                ?: return@withContext Result.failure(
                    Exception("Generation failed. Please check your internet connection.")
                )
            Result.success(generatedBmp)

        } catch (e: Exception) {
            Log.e(TAG, "generateWithPhotoAndPrompt failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Analyzes the uploaded photo to extract EXACT gender + appearance.
     * This runs BEFORE any generation so gender is never lost.
     */
    private suspend fun analyzePersonDetailed(base64Image: String): PersonInfo {
        val raw = runCatching {
            val req = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image)),
                            GeminiPart(
                                text = """Look at this photo carefully and answer in EXACTLY this format:
GENDER: [write only the word: male  OR  female]
DESC: [2-3 sentences starting with "A male" or "A female" describing: estimated age range, exact hair color and style (length, curly/straight/wavy), exact skin tone, eye color if visible, face shape (oval/round/square/heart), distinctive facial features (strong jaw, high cheekbones, sharp nose, etc.), any facial hair for males, expression, and overall look]

Example for a man:
GENDER: male
DESC: A male in his early 30s with short neat black hair, medium-warm brown skin, dark brown almond-shaped eyes, a strong square jaw, and a light beard stubble. He has a broad nose, prominent cheekbones, and a serious focused expression.

Example for a woman:
GENDER: female
DESC: A female in her mid-20s with long wavy golden-blonde hair, fair porcelain skin, bright blue eyes, high defined cheekbones, and a delicate pointed chin. She has full lips, a straight refined nose, and a warm natural smile.

Be very specific — your description will be used to generate an AI portrait that must look like this exact person.

Now analyze the photo:"""
                            )
                        )
                    )
                )
            )
            geminiApi.generatePrompt(BuildConfig.GEMINI_API_KEY, req)
        }.getOrNull()?.body()
            ?.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull { it.text != null }
            ?.text?.trim() ?: ""

        Log.d(TAG, "Raw analysis: $raw")

        val genderMatch = Regex("GENDER:\\s*(male|female)", RegexOption.IGNORE_CASE).find(raw)
        val descMatch   = Regex("DESC:\\s*(.+)", RegexOption.IGNORE_CASE).find(raw)

        val gender = genderMatch?.groupValues?.getOrNull(1)?.lowercase()?.trim() ?: "person"
        val desc   = descMatch?.groupValues?.getOrNull(1)?.trim()?.replace("\n", " ") ?: "A ${gender} person"

        return PersonInfo(gender = gender, description = desc)
    }

    /** Strips the leading style:xxx | tag from a card prompt before sending to Gemini. */
    private fun cleanStyleTag(prompt: String) =
        prompt.replace(Regex("^style:\\w+\\s*\\|\\s*"), "").trim()

    /**
     * Builds the image-to-image prompt for Gemini.
     * Asks Gemini to generate a FULL BODY image in the chosen art style,
     * using the uploaded photo's face as identity reference.
     */
    private fun buildGenderLockedEditPrompt(person: PersonInfo, stylePrompt: String): String {
        val genderWord = if (person.gender == "female") "woman" else "man"
        val opposite   = if (person.gender == "female") "male" else "female"
        val cleanPrompt = cleanStyleTag(stylePrompt)
        return """Look at the face in the uploaded photo. Use that person's face as the identity for a brand new full body AI-generated image described below.

PERSON: ${person.gender}, ${person.description}

GENERATE THIS:
$cleanPrompt

CRITICAL REQUIREMENTS:
1. FULL BODY: Generate the COMPLETE figure from head to feet. Show the entire body, outfit, and background. NOT a headshot.
2. FACE IDENTITY: The generated character's face must match the uploaded person's face — same face shape, bone structure, eye shape and placement, nose shape, lip shape. The face is rendered entirely in the art style (painted, drawn, cinematic, etc.) — NOT copied as a photo.
3. NATURAL INTEGRATION: The face, body, hair, clothes, and background must all be generated together as one cohesive image. The face must naturally fit the character's head and body with correct proportions, perspective, and lighting.
4. GENDER: Always ${person.gender} ($genderWord). Never $opposite.
5. STYLE: Invent the clothing, body pose, hair styling, and environment from scratch to match the requested style — do NOT reuse anything from the uploaded photo except the face identity."""
    }

    /**
     * Builds the Gemini text-to-image fallback prompt (no input image).
     * Requests a full body image using the person's description + style.
     */
    private fun buildTextPrompt(person: PersonInfo, stylePrompt: String): String {
        val genderWord = if (person.gender == "female") "woman" else if (person.gender == "male") "man" else "person"
        val cleanPrompt = cleanStyleTag(stylePrompt)
        return "Full body AI generated portrait, complete figure from head to feet, standing. " +
            "Subject: ${person.description}. " +
            "$cleanPrompt. " +
            "The face, body, clothing, hair, and background are all part of one cohesive AI-generated image. " +
            "Face naturally fits the character's head — correct size, perspective, and lighting. " +
            "Entire body visible including feet. ${person.gender} $genderWord. High quality detailed art."
    }

    /** Extracts scene/style keywords from a card prompt, stripping the style tag. */
    private fun extractStyleKeywords(stylePrompt: String): String {
        val cleaned = cleanStyleTag(stylePrompt)
        return if (cleaned.length > 400) cleaned.substring(0, 400) else cleaned
    }

    /**
     * Pure text-to-image generation — no user photo required.
     * Tries Gemini image generation models first (same quality as Gemini app),
     * then falls back to Pollinations.ai (free, no API key needed).
     */
    suspend fun generateTextToImage(
        prompt: String,
        onStatusUpdate: (String) -> Unit = {}
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("Generating AI art...")

            // 1. Try Gemini text-to-image models (free tier available)
            val geminiRequest = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(responseModalities = listOf("IMAGE", "TEXT"))
            )
            for (modelId in GEMINI_IMAGE_MODEL_CHAIN) {
                onStatusUpdate("Generating AI image...")
                val url = "${GEMINI_BASE}v1beta/models/$modelId:generateContent"
                val response = runCatching {
                    geminiApi.generateGeminiImage(url, BuildConfig.GEMINI_API_KEY, geminiRequest)
                }.getOrElse { Log.w(TAG, "T2I $modelId error: ${it.message}"); null } ?: continue

                if (response.isSuccessful) {
                    val base64 = response.body()
                        ?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull { it.inlineData != null }
                        ?.inlineData?.data
                    if (base64 != null) {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            Log.d(TAG, "T2I success with $modelId")
                            return@withContext Result.success(bmp)
                        }
                    }
                    Log.w(TAG, "T2I $modelId — no image in response")
                } else {
                    Log.w(TAG, "T2I $modelId → ${response.code()}: ${response.errorBody()?.string()}")
                }
            }

            // 2. Try Imagen text-to-image (paid tier)
            val imagenRequest = ImagenRequest(
                instances = listOf(ImagenInstance(prompt = prompt)),
                parameters = ImagenParameters(sampleCount = 1)
            )
            for (modelId in IMAGEN_MODEL_CHAIN) {
                onStatusUpdate("Trying Imagen model...")
                val url = "${GEMINI_BASE}v1beta/models/$modelId:predict"
                val response = runCatching {
                    geminiApi.predictImagen(url, BuildConfig.GEMINI_API_KEY, imagenRequest)
                }.getOrElse { Log.w(TAG, "$modelId error: ${it.message}"); null } ?: continue

                if (response.isSuccessful) {
                    val base64 = response.body()?.predictions?.firstOrNull()?.bytesBase64Encoded
                    if (base64 != null) {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) return@withContext Result.success(bmp)
                    }
                }
            }

            // 3. Pollinations.ai free fallback (no API key, always works)
            onStatusUpdate("Rendering with AI...")
            val bmp = fetchPollinationsImage(prompt)
                ?: return@withContext Result.failure(Exception("All generation methods failed. Check your internet connection."))
            Result.success(bmp)

        } catch (e: Exception) {
            Log.e(TAG, "generateTextToImage failed: ${e.message}")
            Result.failure(e)
        }
    }

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

        // Step 1: Get a very detailed face+appearance description to guide generation
        onStatusUpdate("Analyzing your photo...")
        val personDescription = runCatching {
            val req = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image)),
                            GeminiPart(
                                text = "Describe the person in this photo in precise detail for AI image generation. " +
                                    "Include: gender, estimated age, exact hair color and style (length/texture/waves), " +
                                    "skin tone, eye color, face shape (oval/round/square/heart), " +
                                    "distinctive facial features (strong jaw, high cheekbones, sharp nose, full lips, etc.). " +
                                    "3 sentences max. Start with 'A [gender] in their [age range] with...'"
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

        // Step 2: Build full body prompt — person description drives who appears,
        // scene keywords drive the style, body, clothes, and background.
        val sceneKeywords = extractSceneKeywords(prompt)
        val pollinationsPrompt = "Full body character portrait from head to feet, complete standing figure. " +
            "Subject: $personDescription. $sceneKeywords. " +
            "Entire body visible, face centered at top quarter of image. " +
            "Face, body, clothes, and background are all part of the same cohesive AI generated artwork. " +
            "High quality, detailed art."

        // Step 3: Generate the complete full-body image from text.
        // Face, body, clothes, and background are all generated together — they naturally
        // match in proportion, lighting, and style. No paste needed.
        val seed = sourceBitmap.hashCode().toLong().let {
            Math.abs(if (it == 0L) System.currentTimeMillis() else it + System.currentTimeMillis() % 10000)
        }
        onStatusUpdate("Generating full body AI image...")
        return fetchPollinationsImage(pollinationsPrompt, seed)
    }

    private fun fetchPollinationsImage(
        prompt: String,
        seed: Long = System.currentTimeMillis(),
        width: Int = 768,
        height: Int = 1024
    ): Bitmap? {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        for (model in POLLINATIONS_MODELS) {
            val url = "https://image.pollinations.ai/prompt/$encoded" +
                "?width=$width&height=$height&nologo=true&model=$model&seed=$seed"
            Log.d(TAG, "Pollinations [$model] ${width}x${height} seed=$seed")
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

    // ── (face transfer removed — Gemini image-to-image handles face matching natively) ───────


    // ── Prompt helpers ────────────────────────────────────────────────────────

    /**
     * Extracts the core scene/style keywords from the full template prompt
     * to keep the Pollinations URL short and focused.
     * Strips the leading style:xxx tag and any identity-preservation boilerplate.
     */
    private fun extractSceneKeywords(templatePrompt: String): String {
        val cleaned = cleanStyleTag(templatePrompt)
            .replace(Regex("Only the face must match.*?\\.\\s*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Use the provided image.*?described\\.\\s*", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("Preserve the exact.*?\\.\\s*"), "")
            .replace(Regex("Do not change.*?\\.\\s*"), "")
            .trim()
        return if (cleaned.length > 220) cleaned.substring(0, 220) else cleaned
    }

    private fun buildEditPrompt(templatePrompt: String): String =
        """Completely transform this portrait photo into the following art style. Apply the style to the ENTIRE image — including the face, hair, skin texture, and expression — so the whole image is rendered in the art style.

$templatePrompt

IMPORTANT: While transforming the full image into the art style, keep the person's identity recognizable — preserve the same face shape, bone structure, eye placement, nose shape, and distinctive features. The result should look like a fully styled artwork of this specific person — not a photo with a background swap."""
}
