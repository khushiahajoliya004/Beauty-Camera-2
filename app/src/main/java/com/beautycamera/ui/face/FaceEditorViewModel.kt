package com.beautycamera.ui.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.data.repository.StickerRepository
import com.beautycamera.domain.model.BeautySettings
import com.beautycamera.domain.model.FaceLandmarks
import com.beautycamera.domain.model.StickerModel
import com.beautycamera.domain.model.TextLayerModel
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.CapturedBitmapHolder
import com.beautycamera.util.GPUImageHelper
import com.beautycamera.util.MediaPipeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class FaceEditorUiState(
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val faceLandmarks: FaceLandmarks? = null,
    val beautySettings: BeautySettings = BeautySettings(),
    val selectedTool: BeautyTool = BeautyTool.ADJUST,
    val selectedMakeupTool: MakeupSubTool = MakeupSubTool.SKIN,
    val blurIntensity: Float = 0f,
    val brightness: Float = 0.5f,
    val contrast: Float = 0.5f,
    val saturation: Float = 0.5f,
    val sharpness: Float = 0f,
    val isBeforeMode: Boolean = false,
    val isProcessing: Boolean = false,
    val isDetectingFace: Boolean = false,
    val isSaved: Boolean = false,
    // ── Sticker & Text ────────────────────────────────────────────────────────
    val availableStickers: List<StickerModel> = emptyList(),
    val placedStickers: List<StickerModel> = emptyList(),
    val textLayers: List<TextLayerModel> = emptyList(),
    val selectedStickerId: String? = null,
    val selectedTextId: String? = null,
    val showStickerSheet: Boolean = false,
    val showTextDialog: Boolean = false
)

enum class BeautyTool {
    ADJUST, BLUR, BRIGHTNESS, CROP, MAKEUP, STICKER
}

enum class MakeupSubTool {
    SKIN, LIPS, BLUSH, EYES, FOUNDATION
}

@HiltViewModel
class FaceEditorViewModel @Inject constructor(
    private val gpuImageHelper: GPUImageHelper,
    private val mediaPipeHelper: MediaPipeHelper,
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository,
    private val stickerRepository: StickerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceEditorUiState())
    val uiState: StateFlow<FaceEditorUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(availableStickers = stickerRepository.getAllStickers())
    }

    fun loadBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val scaled = withContext(Dispatchers.Default) {
                bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            }
            _uiState.value = _uiState.value.copy(
                originalBitmap = scaled,
                processedBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
            )
            detectFace(scaled)
        }
    }

    fun loadImage(context: Context, imagePath: String) {
        when {
            imagePath == "from_camera" -> {
                val bmp = CapturedBitmapHolder.consume() ?: return
                loadBitmap(bmp)
                return
            }
            imagePath == "picker" || imagePath.isBlank() -> return
        }

        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(Uri.decode(imagePath))
                    bitmapUtils.uriToBitmap(context, uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } ?: return@launch

            val scaledBitmap = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            _uiState.value = _uiState.value.copy(
                originalBitmap = scaledBitmap,
                processedBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
            )
            detectFace(scaledBitmap)
        }
    }

    private fun detectFace(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetectingFace = true)
            val landmarks = mediaPipeHelper.detectFaceLandmarks(bitmap)
            _uiState.value = _uiState.value.copy(
                faceLandmarks = landmarks,
                isDetectingFace = false
            )
        }
    }

    fun selectTool(tool: BeautyTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectMakeupSubTool(tool: MakeupSubTool) {
        _uiState.value = _uiState.value.copy(selectedMakeupTool = tool)
    }

    fun setBeforeMode(before: Boolean) {
        _uiState.value = _uiState.value.copy(isBeforeMode = before)
    }

    // ── Adjustment controls ──────────────────────────────────────────────────

    fun updateBlurIntensity(value: Float) {
        _uiState.value = _uiState.value.copy(blurIntensity = value)
        applyAllEffects()
    }

    fun updateBrightness(value: Float) {
        _uiState.value = _uiState.value.copy(brightness = value)
        applyAllEffects()
    }

    fun updateContrast(value: Float) {
        _uiState.value = _uiState.value.copy(contrast = value)
        applyAllEffects()
    }

    fun updateSaturation(value: Float) {
        _uiState.value = _uiState.value.copy(saturation = value)
        applyAllEffects()
    }

    fun updateSharpness(value: Float) {
        _uiState.value = _uiState.value.copy(sharpness = value)
        applyAllEffects()
    }

    // ── Makeup controls ──────────────────────────────────────────────────────

    fun setLipColor(color: Int) {
        val settings = _uiState.value.beautySettings.copy(lipColor = color)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun setEyeColor(color: Int) {
        val settings = _uiState.value.beautySettings.copy(eyeColor = color)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun updateSkinSmoothing(value: Float) {
        val settings = _uiState.value.beautySettings.copy(skinSmoothing = value)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun updateLipOpacity(value: Float) {
        val settings = _uiState.value.beautySettings.copy(lipOpacity = value)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun updateBlushIntensity(value: Float) {
        val settings = _uiState.value.beautySettings.copy(blushIntensity = value)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun updateEyeColorOpacity(value: Float) {
        val settings = _uiState.value.beautySettings.copy(eyeColorOpacity = value)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    fun updateFoundationIntensity(value: Float) {
        val settings = _uiState.value.beautySettings.copy(foundationIntensity = value)
        _uiState.value = _uiState.value.copy(beautySettings = settings)
        applyAllEffects()
    }

    // ── Sticker controls ─────────────────────────────────────────────────────

    fun toggleStickerSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showStickerSheet = show)
    }

    fun toggleTextDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTextDialog = show)
    }

    fun addSticker(sticker: StickerModel) {
        val new = sticker.copy(id = UUID.randomUUID().toString(), x = 200f, y = 400f, scale = 1f)
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + new,
            showStickerSheet = false,
            selectedStickerId = new.id,
            selectedTextId = null
        )
    }

    fun updateStickerPosition(id: String, dx: Float, dy: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map {
                if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
            }
        )
    }

    fun updateStickerTransform(id: String, scaleChange: Float, rotationChange: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map {
                if (it.id == id) it.copy(
                    scale = (it.scale * scaleChange).coerceIn(0.2f, 5f),
                    rotation = it.rotation + rotationChange
                ) else it
            }
        )
    }

    fun updateStickerScale(id: String, scale: Float) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.map {
                if (it.id == id) it.copy(scale = scale.coerceIn(0.3f, 4f)) else it
            }
        )
    }

    fun deleteSticker(id: String) {
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers.filter { it.id != id },
            selectedStickerId = null
        )
    }

    fun selectSticker(id: String?) {
        _uiState.value = _uiState.value.copy(selectedStickerId = id, selectedTextId = null)
    }

    fun addTextLayer(text: String, color: Int, fontSize: Float) {
        val layer = TextLayerModel(
            id = UUID.randomUUID().toString(), text = text,
            x = 200f, y = 300f, color = color, fontSize = fontSize
        )
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers + layer,
            showTextDialog = false,
            selectedTextId = layer.id,
            selectedStickerId = null
        )
    }

    fun updateTextPosition(id: String, dx: Float, dy: Float) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.map {
                if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
            }
        )
    }

    fun updateTextSize(id: String, fontSize: Float) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.map {
                if (it.id == id) it.copy(fontSize = fontSize.coerceIn(12f, 120f)) else it
            }
        )
    }

    fun deleteText(id: String) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.filter { it.id != id },
            selectedTextId = null
        )
    }

    fun selectText(id: String?) {
        _uiState.value = _uiState.value.copy(selectedTextId = id, selectedStickerId = null)
    }

    // ── Crop helpers ─────────────────────────────────────────────────────────

    fun saveBitmapToCache(context: Context): Uri? {
        val bitmap = _uiState.value.processedBitmap ?: return null
        return try {
            val file = File(context.cacheDir, "crop_src_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateAfterCrop(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                bitmapUtils.uriToBitmap(context, uri)
            } ?: return@launch
            _uiState.value = _uiState.value.copy(
                originalBitmap = bitmap,
                processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true),
                blurIntensity = 0f,
                brightness = 0.5f,
                contrast = 0.5f,
                saturation = 0.5f,
                sharpness = 0f,
                beautySettings = BeautySettings()
            )
            detectFace(bitmap)
        }
    }

    // ── Effect pipeline ──────────────────────────────────────────────────────

    private fun applyAllEffects() {
        val original = _uiState.value.originalBitmap ?: return
        val landmarks = _uiState.value.faceLandmarks
        val settings = _uiState.value.beautySettings
        val st = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            val result = withContext(Dispatchers.Default) {
                var current = original.copy(Bitmap.Config.ARGB_8888, true)

                fun advance(next: Bitmap) {
                    if (next !== current) { current.recycle(); current = next }
                }

                if (st.blurIntensity > 0f)
                    advance(gpuImageHelper.applyGaussianBlur(current, st.blurIntensity))

                val gpuBrightness = (st.brightness - 0.5f) * 1.0f
                val gpuContrast   = 0.5f + st.contrast * 1.5f
                val gpuSaturation = st.saturation * 2.0f
                if (st.brightness != 0.5f || st.contrast != 0.5f || st.saturation != 0.5f)
                    advance(gpuImageHelper.applyAdjustments(current, gpuBrightness, gpuContrast, gpuSaturation))

                if (st.sharpness > 0f)
                    advance(gpuImageHelper.applySharpness(current, st.sharpness))

                if (settings.skinSmoothing > 0f)
                    advance(gpuImageHelper.applySkinSmoothing(current, settings.skinSmoothing))
                if (settings.foundationIntensity > 0f)
                    advance(gpuImageHelper.applyFoundation(current, settings.foundationIntensity))

                if (landmarks != null) {
                    if (settings.lipOpacity > 0f) {
                        val pts = mediaPipeHelper.getLandmarkPixelCoords(landmarks, mediaPipeHelper.lipIndices)
                        advance(gpuImageHelper.applyLipColor(current, pts, settings.lipColor, settings.lipOpacity))
                    }
                    if (settings.blushIntensity > 0f) {
                        val lc = mediaPipeHelper.getLandmarkPixelCoords(landmarks, mediaPipeHelper.leftCheekIndices)
                        val rc = mediaPipeHelper.getLandmarkPixelCoords(landmarks, mediaPipeHelper.rightCheekIndices)
                        advance(gpuImageHelper.applyBlush(current, lc, rc, settings.blushIntensity))
                    }
                    if (settings.eyeColorOpacity > 0f) {
                        val li = mediaPipeHelper.getLandmarkPixelCoords(landmarks, mediaPipeHelper.leftIrisIndices)
                        val ri = mediaPipeHelper.getLandmarkPixelCoords(landmarks, mediaPipeHelper.rightIrisIndices)
                        advance(gpuImageHelper.applyEyeColor(current, li, ri, settings.eyeColor, settings.eyeColorOpacity))
                    }
                }
                current
            }

            _uiState.value = _uiState.value.copy(processedBitmap = result, isProcessing = false)
        }
    }

    fun saveResult(context: Context, canvasWidthPx: Float, canvasHeightPx: Float, density: Float) {
        val bitmap = _uiState.value.processedBitmap ?: return
        val stickers = _uiState.value.placedStickers
        val texts = _uiState.value.textLayers
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                if (stickers.isEmpty() && texts.isEmpty()) {
                    bitmap
                } else {
                    flattenStickersAndText(bitmap, stickers, texts, canvasWidthPx, canvasHeightPx, density)
                }
            }
            photoRepository.savePhoto(result, context)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    private fun flattenStickersAndText(
        bitmap: Bitmap,
        stickers: List<StickerModel>,
        texts: List<TextLayerModel>,
        canvasW: Float,
        canvasH: Float,
        density: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val fitScale = minOf(canvasW / bitmap.width, canvasH / bitmap.height)
        val dispW = bitmap.width * fitScale
        val dispH = bitmap.height * fitScale
        val offX = (canvasW - dispW) / 2f
        val offY = (canvasH - dispH) / 2f

        fun mapX(sx: Float) = (sx - offX) / fitScale
        fun mapY(sy: Float) = (sy - offY) / fitScale

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        stickers.forEach { sticker ->
            val bx = mapX(sticker.x)
            val by = mapY(sticker.y)
            val boxPx = (80f * density) / fitScale * sticker.scale
            paint.textSize = boxPx * 0.75f
            canvas.save()
            canvas.translate(bx + boxPx * 0.5f, by + boxPx * 0.5f)
            canvas.rotate(sticker.rotation)
            canvas.drawText(sticker.emoji.ifEmpty { "🎨" }, -paint.textSize * 0.4f, paint.textSize * 0.35f, paint)
            canvas.restore()
        }

        texts.forEach { layer ->
            val bx = mapX(layer.x)
            val by = mapY(layer.y)
            paint.color = layer.color
            paint.textSize = (layer.fontSize * density) / fitScale
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.save()
            canvas.translate(bx, by + paint.textSize)
            canvas.rotate(layer.rotation)
            canvas.drawText(layer.text, 0f, 0f, paint)
            canvas.restore()
        }

        return result
    }

    override fun onCleared() {
        super.onCleared()
        mediaPipeHelper.close()
    }
}
