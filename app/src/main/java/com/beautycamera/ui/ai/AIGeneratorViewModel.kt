package com.beautycamera.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.GeminiRepository
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.AIArtCard
import com.beautycamera.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AIArtUiState(
    val cards: List<AIArtCard> = emptyList(),
    val selectedPhoto: Bitmap? = null,        // user's uploaded/captured photo
    val selectedCard: AIArtCard? = null,      // chosen style
    val resultBitmap: Bitmap? = null,         // AI-generated result
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val shuffleCount: Int = 0
)

@HiltViewModel
class AIGeneratorViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val photoRepository: PhotoRepository,
    private val bitmapUtils: BitmapUtils
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIArtUiState())
    val uiState: StateFlow<AIArtUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init { loadCards() }

    private fun loadCards() {
        val cards = listOf(
            AIArtCard(
                id = "ai_enhanced",
                name = "AI Enhanced",
                emoji = "✨",
                description = "Full body AI portrait",
                cardColor = 0xFFAD1457,
                prompt = "style:realistic | Generate a full body AI-enhanced portrait from head to toe of this person. Show the complete figure standing: polished skin, refined hair, elegant smart-casual or formal clothing. Soft professional studio lighting. Clean neutral gradient background. Entire body visible. Only the face must match the uploaded image exactly — same face shape, bone structure, eyes, nose, lips. Photorealistic, high-end beauty photography quality."
            ),
            AIArtCard(
                id = "cinematic",
                name = "Cinematic",
                emoji = "🎬",
                description = "Full body cinematic scene",
                cardColor = 0xFF1A237E,
                prompt = "style:cinematic | Generate a full body cinematic movie-still of this person, head to toe. The complete figure stands in a dramatic urban or moody indoor scene. Stylish protagonist clothing — leather jacket, trench coat, or dramatic outfit. Orange-teal Hollywood film color grade. Dramatic side rim lighting. Shallow depth of field background. Only the face must match the uploaded image: same face shape, eyes, nose, lips, bone structure. Film photography quality."
            ),
            AIArtCard(
                id = "sci_fi",
                name = "Sci-Fi",
                emoji = "🔮",
                description = "Full body sci-fi character",
                cardColor = 0xFF0D47A1,
                prompt = "style:scifi | Generate a full body futuristic sci-fi character portrait, head to toe. The complete figure wears a sleek glowing battle suit or technological armor with neon accents. Dark sci-fi environment background — space station corridor, neon-lit futuristic city. Cool blue and cyan holographic lighting across the entire body and face. Only the face must match the uploaded image: same face shape, eyes, nose, lips, bone structure. High-end sci-fi concept art quality."
            ),
            AIArtCard(
                id = "oil_painting",
                name = "Oil Painting",
                emoji = "🎨",
                description = "Full body oil painting",
                cardColor = 0xFF4E342E,
                prompt = "style:oilpainting | Create a full body classical oil painting in Rembrandt style, showing the complete figure from head to toe. The person wears rich Renaissance or Baroque aristocratic clothing — robes, doublet, or period gown. Grand interior hall or landscape background painted with visible oil brushwork. Warm chiaroscuro lighting. The ENTIRE painting — body, clothing, background, AND face — rendered in oil paint with fine brushwork. Only the face structure must match the uploaded image: same face shape, eyes, nose, lips."
            ),
            AIArtCard(
                id = "van_gogh",
                name = "Van Gogh",
                emoji = "🌻",
                description = "Full body Van Gogh painting",
                cardColor = 0xFF1565C0,
                prompt = "style:vangogh | Paint a full body portrait in Vincent van Gogh's style, the complete figure head to toe. Simple period or peasant clothing with bold swirling Van Gogh brushstrokes across the clothing and body. Vivid swirling countryside or starry night background with thick impasto paint. The ENTIRE image — body, clothes, background, AND face — painted with Van Gogh's cobalt blue, golden yellow, deep green palette and visible swirling brushstrokes. Only the face structure must match the uploaded image: same face shape, eyes, nose, lips."
            ),
            AIArtCard(
                id = "watercolor",
                name = "Watercolor",
                emoji = "💧",
                description = "Full body watercolor art",
                cardColor = 0xFF00838F,
                prompt = "style:watercolor | Create a full body watercolor illustration, the complete figure from head to toe. The person wears light, elegant or casual clothing — flowy dress, linen shirt — rendered in delicate watercolor washes. Soft nature or abstract watercolor background with visible white paper texture and color bleeding at edges. The ENTIRE figure including face painted as watercolor with transparent washes and soft edges. Only the face structure must match the uploaded image: same face shape, eyes, nose, lips."
            ),
            AIArtCard(
                id = "pencil_sketch",
                name = "Pencil Sketch",
                emoji = "✏️",
                description = "Full body pencil drawing",
                cardColor = 0xFF37474F,
                prompt = "style:pencilsketch | Draw a full body graphite pencil portrait on white paper, the complete figure from head to toe. The entire person — face, hair, clothing, body — drawn with detailed pencil shading, fine lines, and cross-hatching for shadow areas. Casual or simple clothing rendered in pencil strokes. White paper background with light sketch lines. The ENTIRE figure is pencil drawing — no color. Only the face structure must match the uploaded image: same face shape, eyes, nose, lips. Artist-quality graphite."
            ),
            AIArtCard(
                id = "anime",
                name = "Anime Style",
                emoji = "🌸",
                description = "Full body anime character",
                cardColor = 0xFF6A0DAD,
                prompt = "style:anime | Generate a full body anime character illustration, the complete figure from head to toe. The person is an anime character wearing a detailed anime outfit — stylish school uniform, fantasy armor, traditional Japanese clothing, or modern anime fashion. Vibrant anime background: sky with clouds, anime cityscape, cherry blossom park, or fantasy landscape. The ENTIRE body, clothing, hair, and background are in high-quality anime art style. The face is drawn in anime style with large expressive eyes, smooth cel-shaded skin, clean linework — but the face SHAPE, eye positions, nose position, and mouth position must match the uploaded image so the person is recognizable as the same individual as an anime character."
            ),
            AIArtCard(
                id = "golden_hour",
                name = "Golden Hour",
                emoji = "🌅",
                description = "Full body golden hour photo",
                cardColor = 0xFFE65100,
                prompt = "style:goldenhour | Generate a full body golden hour outdoor photography portrait from head to toe. The complete figure stands in a beautiful outdoor location — golden wheat field, beach, forest path, or meadow — wearing casual summer or outdoor clothing. Warm golden orange sunlight from behind creating a glowing rim light in the hair and shoulders. Soft bokeh of trees, grass, or nature blurred in background. Entire body visible with warm golden light. Only the face must match the uploaded image: same face shape, eyes, nose, lips, bone structure. Natural DSLR photography quality."
            ),
            AIArtCard(
                id = "studio_pro",
                name = "Studio Pro",
                emoji = "📸",
                description = "Full body professional photo",
                cardColor = 0xFF212121,
                prompt = "style:studio | Generate a full body professional studio portrait from head to toe. The complete figure stands wearing formal business attire — suit and tie, blazer, professional dress. Clean seamless neutral grey or white background. Professional three-point studio lighting: softbox key light, fill light, rim light. Full body visible from head to feet. Only the face must match the uploaded image: same face shape, eyes, nose, lips, bone structure. Premium corporate/LinkedIn photography quality."
            )
        )
        _uiState.value = _uiState.value.copy(cards = cards)
    }

    fun loadImageFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                bitmapUtils.uriToBitmap(context, uri)
            }
            if (bitmap != null) {
                _uiState.value = _uiState.value.copy(
                    selectedPhoto = bitmap,
                    resultBitmap = null,
                    isSaved = false,
                    errorMessage = null,
                    shuffleCount = 0
                )
            }
        }
    }

    fun loadImageFromCamera(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            selectedPhoto = bitmap,
            resultBitmap = null,
            isSaved = false,
            errorMessage = null,
            shuffleCount = 0
        )
    }

    fun selectCard(card: AIArtCard) {
        _uiState.value = _uiState.value.copy(
            selectedCard = card,
            resultBitmap = null,
            isSaved = false,
            errorMessage = null,
            shuffleCount = 0
        )
    }

    fun generate() {
        val photo = _uiState.value.selectedPhoto ?: return
        val card  = _uiState.value.selectedCard  ?: return
        if (_uiState.value.isGenerating) return
        startGeneration(photo, card)
    }

    fun shuffle() {
        val photo = _uiState.value.selectedPhoto ?: return
        val card  = _uiState.value.selectedCard  ?: return
        if (_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(
            resultBitmap = null,
            isSaved = false,
            errorMessage = null,
            shuffleCount = _uiState.value.shuffleCount + 1
        )
        startGeneration(photo, card)
    }

    private fun startGeneration(photo: Bitmap, card: AIArtCard) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                progress = 0f,
                statusMessage = "Preparing...",
                errorMessage = null
            )

            // Animated progress
            val progressJob = launch {
                var p = 0f
                while (p < 0.97f) {
                    val delayMs = if (p < 0.55f) 700L else 2000L
                    val step    = if (p < 0.55f) 0.04f else 0.01f
                    delay(delayMs)
                    p = (p + step).coerceAtMost(0.97f)
                    _uiState.value = _uiState.value.copy(progress = p)
                }
            }

            // Add slight variation on shuffle so the model gives a different output
            val shuffleSuffix = if (_uiState.value.shuffleCount > 0)
                "\n\nGenerate a fresh unique variation with different pose, lighting angle, or composition details."
            else ""

            val result = geminiRepository.generateWithPhotoAndPrompt(
                sourceBitmap = photo,
                stylePrompt = card.prompt + shuffleSuffix,
                styleId = card.id,
                onStatusUpdate = { msg -> _uiState.value = _uiState.value.copy(statusMessage = msg) }
            )

            progressJob.cancel()

            result.fold(
                onSuccess = { bitmap ->
                    _uiState.value = _uiState.value.copy(
                        resultBitmap = bitmap,
                        isGenerating = false,
                        statusMessage = "Done!",
                        progress = 1f
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = "",
                        progress = 0f,
                        errorMessage = error.message ?: "Generation failed. Please try again."
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
