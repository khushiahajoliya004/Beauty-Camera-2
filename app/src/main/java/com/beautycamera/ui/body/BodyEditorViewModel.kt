package com.beautycamera.ui.body

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.BodySettings
import com.beautycamera.domain.model.PoseLandmarks
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.CapturedBitmapHolder
import com.beautycamera.util.MediaPipeHelper
import com.beautycamera.util.OpenCVHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BodyEditorUiState(
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val poseLandmarks: PoseLandmarks? = null,
    val bodySettings: BodySettings = BodySettings(),
    val isAutoMode: Boolean = false,
    val selectedZone: BodyZone = BodyZone.WAIST,
    val isProcessing: Boolean = false,
    val isSaved: Boolean = false,
    val isBeforeMode: Boolean = false
)

enum class BodyZone(val label: String) {
    BODY("Body"), WAIST("Waist"), LEGS("Legs"),
    BUST("Bust"), HIPS("Hips"), SHOULDERS("Shoulders")
}

@HiltViewModel
class BodyEditorViewModel @Inject constructor(
    private val openCVHelper: OpenCVHelper,
    private val mediaPipeHelper: MediaPipeHelper,
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyEditorUiState())
    val uiState: StateFlow<BodyEditorUiState> = _uiState.asStateFlow()
    private var effectJob: Job? = null

    /** Called when user takes a photo directly inside the editor. */
    fun loadBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val scaled = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            _uiState.value = _uiState.value.copy(
                originalBitmap = scaled,
                processedBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
            )
            detectPose(scaled)
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
            val bitmap = try {
                val uri = Uri.parse(Uri.decode(imagePath))
                bitmapUtils.uriToBitmap(context, uri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } ?: return@launch

            val scaled = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            _uiState.value = _uiState.value.copy(
                originalBitmap = scaled,
                processedBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
            )
            detectPose(scaled)
        }
    }

    private fun detectPose(bitmap: Bitmap) {
        viewModelScope.launch {
            val landmarks = mediaPipeHelper.detectPoseLandmarks(bitmap)
            _uiState.value = _uiState.value.copy(poseLandmarks = landmarks)
        }
    }

    fun selectZone(zone: BodyZone) {
        _uiState.value = _uiState.value.copy(selectedZone = zone)
    }

    fun setBeforeMode(before: Boolean) {
        _uiState.value = _uiState.value.copy(isBeforeMode = before)
    }

    fun toggleAutoMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoMode = enabled)
        if (enabled) applyAutoMode()
    }

    private fun applyAutoMode() {
        val autoSettings = BodySettings(
            slimBody = 0.3f, slimWaist = 0.4f, legLengthening = 0.2f,
            bustEnhance = 0.2f, hipEnhance = 0.1f, shoulderReshape = 0.1f
        )
        _uiState.value = _uiState.value.copy(bodySettings = autoSettings)
        applyEffects()
    }

    fun updateSlimBody(v: Float) = updateSettings(_uiState.value.bodySettings.copy(slimBody = v))
    fun updateSlimWaist(v: Float) = updateSettings(_uiState.value.bodySettings.copy(slimWaist = v))
    fun updateLegLengthening(v: Float) = updateSettings(_uiState.value.bodySettings.copy(legLengthening = v))
    fun updateBustEnhance(v: Float) = updateSettings(_uiState.value.bodySettings.copy(bustEnhance = v))
    fun updateHipEnhance(v: Float) = updateSettings(_uiState.value.bodySettings.copy(hipEnhance = v))
    fun updateShoulderReshape(v: Float) = updateSettings(_uiState.value.bodySettings.copy(shoulderReshape = v))

    private fun updateSettings(settings: BodySettings) {
        _uiState.value = _uiState.value.copy(bodySettings = settings)
        applyEffects()
    }

    private fun applyEffects() {
        val original = _uiState.value.originalBitmap ?: return
        val pose = _uiState.value.poseLandmarks
        val settings = _uiState.value.bodySettings

        // Cancel previous in-flight job so slider changes don't queue up
        effectJob?.cancel()
        effectJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val result = if (pose != null) {
                openCVHelper.applyBodyReshapeWithPose(original, settings, pose)
            } else {
                openCVHelper.applyBodyReshape(original, settings)
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
}
