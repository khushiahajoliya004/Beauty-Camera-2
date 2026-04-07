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
                prompt = "A photorealistic portrait of the same man standing on a modern city rooftop during golden hour. Warm sunlight casts soft highlights across his face, creating natural shadows that emphasize his jawline. He wears a fitted beige linen shirt with sleeves slightly rolled up. The skyline behind him is softly blurred with a shallow depth of field. His confident smile and direct eye contact create a relaxed yet charismatic presence. Shot on a 85mm lens, f/1.8, ultra-detailed skin texture, cinematic lighting.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "rainy_street_cinematic", name = "Rainy Street",
                prompt = "A hyper-realistic portrait of the same man standing under a transparent umbrella on a rainy urban street at night. Neon reflections shimmer in puddles behind him. He wears a dark navy trench coat, slightly damp from rain. Raindrops are visible on the umbrella and subtly on his hair. Soft blue and magenta lighting reflects on his face, while his confident smile contrasts the moody environment. Shot with cinematic bokeh, 50mm lens, high dynamic range.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "minimal_studio",         name = "Studio Portrait",
                prompt = "A studio portrait of the same man against a clean, matte gray background. He wears a crisp white shirt with top button undone. Softbox lighting creates even illumination with subtle shadows for depth. The focus is razor-sharp on his eyes, capturing their hazel detail. His confident smile feels approachable and natural. Ultra-high resolution, editorial style, 85mm portrait lens.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "outdoor_forest",         name = "Forest Light",
                prompt = "A photorealistic portrait of the same man in a lush green forest with dappled sunlight filtering through leaves. He wears a casual olive green jacket over a plain t-shirt. Light patterns fall naturally across his face. The background is softly blurred with rich green tones. His smile is relaxed and grounded, with an engaging gaze that feels connected to nature. Shot with natural light, shallow depth of field.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "coffee_shop",            name = "Coffee Shop",
                prompt = "A candid-style portrait of the same man sitting in a cozy café near a window. Warm ambient lighting and soft sunlight illuminate his face. He holds a ceramic coffee mug mid-conversation, smiling confidently. He wears a textured brown sweater. Background includes blurred shelves, plants, and warm tones. Shot in lifestyle photography style, natural tones, 35mm lens.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "formal_editorial",       name = "Formal Editorial",
                prompt = "A high-end editorial portrait of the same man wearing a tailored charcoal suit and black shirt. He stands against a dark gradient background with dramatic side lighting creating contrast and depth. His confident smile softens the otherwise bold, powerful look. Skin texture is ultra-realistic, with precise lighting on cheekbones and eyes. Shot in fashion magazine style, high contrast, 85mm lens.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "beach_sunset",           name = "Beach Sunset",
                prompt = "A photorealistic portrait of the same man standing on a beach at sunset. Golden-orange light reflects off the ocean behind him. He wears a light blue open-collar shirt slightly moving in the breeze. His hair is gently tousled by wind. The warm glow enhances his skin tone while his confident smile feels relaxed and natural. Shot with soft flare, cinematic color grading.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "urban_daylight",         name = "Urban Street",
                prompt = "A sharp, photorealistic portrait of the same man walking on a modern city street during daytime. He wears a stylish black leather jacket and sunglasses slightly lowered to reveal his eyes. Buildings blur behind him with motion. His confident smile and direct gaze give a bold, contemporary vibe. Shot with motion blur background, 35mm lens, high detail.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "low_key_portrait",       name = "Low-Key Drama",
                prompt = "A dramatic low-key portrait of the same man in near-darkness with a single soft light illuminating half his face. The other half fades into shadow. His confident smile emerges subtly from the darkness, creating intrigue. Background is completely black. Skin detail and eye reflection are highly emphasized. Shot with studio lighting, cinematic mood.",
                negativePrompt = "ugly, deformed, blurry, low quality, cartoon, drawing", thumbnailResId = 0),
            AIStyleTemplate(id = "home_interior",          name = "Home Natural",
                prompt = "A warm, intimate portrait of the same man standing near a window inside a modern home. Soft daylight streams through sheer curtains, casting gentle highlights on his face. He wears a relaxed white t-shirt. The background includes minimal decor with plants and neutral tones. His genuine smile and engaging gaze create a welcoming, authentic feel. Shot with natural light, shallow depth of field.",
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
