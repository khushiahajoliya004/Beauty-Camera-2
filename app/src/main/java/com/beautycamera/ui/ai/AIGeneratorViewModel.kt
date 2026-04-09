package com.beautycamera.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.GeminiRepository
import com.beautycamera.data.repository.PhotoRepository
// import com.beautycamera.data.repository.SDRepository  // commented out — using Gemini instead
import com.beautycamera.domain.model.AIStyleTemplate
import com.beautycamera.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AIGeneratorUiState(
    val selectedBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val templates: List<AIStyleTemplate> = emptyList(),
    val selectedTemplate: AIStyleTemplate? = null,
    val isGenerating: Boolean = false,
    val statusMessage: String = "",
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AIGeneratorViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    // private val sdRepository: SDRepository,  // commented out — using Gemini instead
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIGeneratorUiState())
    val uiState: StateFlow<AIGeneratorUiState> = _uiState.asStateFlow()

    init { loadTemplates() }

    private fun loadTemplates() {
        val templates = listOf(
            AIStyleTemplate(id = "golden_hour_rooftop",    name = "Golden Hour Rooftop",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person on a modern city rooftop during golden hour. Warm sunlight creates soft highlights and natural shadows on the face. Add a beige linen shirt with rolled sleeves. Background shows a softly blurred skyline with shallow depth of field. Cinematic lighting, 85mm lens, ultra realistic.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "rainy_street_cinematic", name = "Rainy Street Cinematic",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person under a transparent umbrella on a rainy neon-lit street at night. Add a dark navy trench coat with slight rain droplets. Reflections of blue and magenta lights in puddles. Cinematic mood, soft bokeh, high detail.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "minimal_studio",         name = "Minimal Studio Portrait",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person in a clean studio with matte gray background. Add a crisp white shirt. Use softbox lighting with even illumination and soft shadows. Ultra sharp focus on eyes, professional portrait style.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "outdoor_forest",         name = "Outdoor Forest Light",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person in a lush green forest with sunlight filtering through leaves. Add an olive green jacket over a t-shirt. Natural light patterns on face, soft background blur.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "coffee_shop",            name = "Coffee Shop Lifestyle",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person in a cozy coffee shop near a window. Add a brown textured sweater and a coffee mug in hand. Warm lighting, soft background blur with plants and shelves.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "formal_editorial",       name = "Formal Editorial Look",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Dress the person in a charcoal suit with black shirt. Place against a dark gradient background with dramatic side lighting. High-end fashion editorial look, strong contrast.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "beach_sunset",           name = "Beach Sunset Portrait",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person on a beach at sunset with golden-orange lighting. Add a light blue open shirt. Slight wind effect in hair, ocean background softly blurred.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "urban_daylight",         name = "Urban Daylight Street Style",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person walking on a modern city street. Add a black leather jacket and sunglasses slightly lowered. Motion blur background, sharp subject focus.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "low_key_portrait",       name = "Artistic Low-Key Portrait",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Create a low-key portrait with one side of the face lit and the other in shadow. Black background. Dramatic cinematic lighting, high contrast.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0),
            AIStyleTemplate(id = "home_interior",          name = "Home Interior Natural Light",
                prompt = "Use the provided image of the person. Preserve the exact same face, identity, facial features, and expression. Do not change the person's appearance, gender, or identity. Only modify the environment, lighting, clothing, and style as described. Place the person indoors near a window with soft daylight. Add a simple white t-shirt. Minimal background with plants and neutral tones. Warm and natural mood.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing, different person, changed face, changed identity, changed gender", thumbnailResId = 0)
        )
        _uiState.value = _uiState.value.copy(templates = templates)
    }

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                bitmapUtils.uriToBitmap(context, uri)
            }
            bitmap?.let {
                _uiState.value = _uiState.value.copy(
                    selectedBitmap = it,
                    resultBitmap = null,
                    isSaved = false
                )
            }
        }
    }

    fun selectTemplate(template: AIStyleTemplate) {
        _uiState.value = _uiState.value.copy(selectedTemplate = template)
    }

    fun generate() {
        val bitmap   = _uiState.value.selectedBitmap   ?: return
        val template = _uiState.value.selectedTemplate ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                progress = 0f,
                statusMessage = "Preparing...",
                errorMessage = null,
                resultBitmap = null,
                isSaved = false
            )

            val progressJob = launch {
                var p = 0f
                while (p < 0.95f) {
                    kotlinx.coroutines.delay(1000)
                    p += 0.05f
                    _uiState.value = _uiState.value.copy(progress = p.coerceAtMost(0.95f))
                }
            }

            // ── Old pipeline (Together AI / HuggingFace / Stability AI) — commented out ──
            // val result = sdRepository.generateFromTemplate(
            //     sourceBitmap = bitmap,
            //     template = template,
            //     onStatusUpdate = { msg ->
            //         _uiState.value = _uiState.value.copy(statusMessage = msg)
            //     }
            // )

            // ── Gemini only ───────────────────────────────────────────────────────────────
            val result = geminiRepository.generateImage(
                sourceBitmap = bitmap,
                template = template,
                onStatusUpdate = { msg ->
                    _uiState.value = _uiState.value.copy(statusMessage = msg)
                }
            )

            progressJob.cancel()

            result.fold(
                onSuccess = { resultBitmap ->
                    _uiState.value = _uiState.value.copy(
                        resultBitmap = resultBitmap,
                        isGenerating = false,
                        statusMessage = "Success!",
                        progress = 1f
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = "",
                        progress = 0f,
                        errorMessage = error.message ?: "Generation failed"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun saveResult(context: Context) {
        val bitmap = _uiState.value.resultBitmap ?: return
        viewModelScope.launch {
            photoRepository.savePhoto(bitmap, context, format = Bitmap.CompressFormat.PNG)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
