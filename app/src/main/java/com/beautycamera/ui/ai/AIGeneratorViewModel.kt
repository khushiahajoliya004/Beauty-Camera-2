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
            AIStyleTemplate(id = "golden_hour_rooftop",    name = "Golden Hour",
                prompt = "golden hour rooftop portrait, warm orange sunlight, cinematic lens flare, bokeh background, film grain, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "rainy_street_cinematic", name = "Rainy Street",
                prompt = "rainy night street portrait, neon reflections on wet pavement, dramatic cinematic lighting, shallow depth of field, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "minimal_studio",         name = "Studio Portrait",
                prompt = "clean minimal studio portrait, soft box lighting, white background, high-end fashion photography, sharp focus, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "outdoor_forest",         name = "Forest Light",
                prompt = "outdoor forest portrait, dappled sunlight through trees, emerald green bokeh, natural beauty, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "coffee_shop",            name = "Coffee Shop",
                prompt = "cozy coffee shop portrait, warm ambient lighting, bokeh of cafe background, lifestyle photography, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "formal_editorial",       name = "Formal Editorial",
                prompt = "high-fashion editorial portrait, dramatic studio lighting, vogue magazine style, sharp details, luxury aesthetic, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "beach_sunset",           name = "Beach Sunset",
                prompt = "beach sunset portrait, warm golden light, ocean waves bokeh, sun-kissed skin, lifestyle travel photography, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "urban_daylight",         name = "Urban Street",
                prompt = "urban street portrait, natural daylight, city architecture background, candid style, sharp focus, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "low_key_portrait",       name = "Low-Key Drama",
                prompt = "low-key dramatic portrait, single directional light, deep shadows, high contrast, theatrical mood, professional studio, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "home_interior",          name = "Home Natural",
                prompt = "natural home interior portrait, soft window light, cozy warm tones, lifestyle photography, shallow depth of field, photorealistic",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0)
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
