package com.beautycamera.ui.face

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.BeautySettings
import com.beautycamera.domain.model.FaceLandmarks
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
import javax.inject.Inject

data class FaceEditorUiState(
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val faceLandmarks: FaceLandmarks? = null,
    val beautySettings: BeautySettings = BeautySettings(),
    val selectedTool: BeautyTool = BeautyTool.ADJUST,
    val selectedMakeupTool: MakeupSubTool = MakeupSubTool.SKIN,
    // Blur: 0 = no blur, 1 = max blur
    val blurIntensity: Float = 0f,
    // Brightness/Contrast/Saturation stored as slider 0..1 where 0.5 = neutral (no change)
    val brightness: Float = 0.5f,
    val contrast: Float = 0.5f,
    val saturation: Float = 0.5f,
    val sharpness: Float = 0f,
    val isBeforeMode: Boolean = false,
    val isProcessing: Boolean = false,
    val isDetectingFace: Boolean = false,
    val isSaved: Boolean = false
)

enum class BeautyTool {
    ADJUST, BLUR, BRIGHTNESS, CROP, MAKEUP
}

enum class MakeupSubTool {
    SKIN, LIPS, BLUSH, EYES, FOUNDATION
}

@HiltViewModel
class FaceEditorViewModel @Inject constructor(
    private val gpuImageHelper: GPUImageHelper,
    private val mediaPipeHelper: MediaPipeHelper,
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FaceEditorUiState())
    val uiState: StateFlow<FaceEditorUiState> = _uiState.asStateFlow()

    /** Called when the user takes a photo directly inside the editor (TakePicturePreview). */
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
            // Direct bitmap from camera capture — no IO needed
            imagePath == "from_camera" -> {
                val bmp = CapturedBitmapHolder.consume() ?: return
                loadBitmap(bmp)
                return
            }
            // Picker sentinel — show pick-photo UI
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

    // ── Crop helpers ─────────────────────────────────────────────────────────

    /** Saves the current processed bitmap to the cache directory and returns its [Uri]. */
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

    /** Called after uCrop returns successfully — reloads the cropped image. */
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

                // 1. Gaussian blur
                if (st.blurIntensity > 0f)
                    advance(gpuImageHelper.applyGaussianBlur(current, st.blurIntensity))

                // 2. Brightness / contrast / saturation
                // Slider 0..1 where 0.5 = neutral; map to GPU ranges
                val gpuBrightness = (st.brightness - 0.5f) * 1.0f          // -0.5 .. +0.5
                val gpuContrast   = 0.5f + st.contrast * 1.5f              //  0.5 .. 2.0
                val gpuSaturation = st.saturation * 2.0f                   //  0.0 .. 2.0
                if (st.brightness != 0.5f || st.contrast != 0.5f || st.saturation != 0.5f)
                    advance(gpuImageHelper.applyAdjustments(current, gpuBrightness, gpuContrast, gpuSaturation))

                // 3. Sharpness
                if (st.sharpness > 0f)
                    advance(gpuImageHelper.applySharpness(current, st.sharpness))

                // 4. Makeup — skin & foundation (GPU)
                if (settings.skinSmoothing > 0f)
                    advance(gpuImageHelper.applySkinSmoothing(current, settings.skinSmoothing))
                if (settings.foundationIntensity > 0f)
                    advance(gpuImageHelper.applyFoundation(current, settings.foundationIntensity))

                // 5. Makeup — landmark-based canvas effects
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

    fun saveResult(context: Context) {
        val bitmap = _uiState.value.processedBitmap ?: return
        viewModelScope.launch {
            photoRepository.savePhoto(bitmap, context)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPipeHelper.close()
    }
}
