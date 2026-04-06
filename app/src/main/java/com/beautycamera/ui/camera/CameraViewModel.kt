package com.beautycamera.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.FilterRepository
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.FilterModel
import com.beautycamera.util.CapturedBitmapHolder
import com.beautycamera.util.GPUImageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val filters: List<FilterModel> = emptyList(),
    val selectedFilterIndex: Int = 0,
    val filterIntensity: Float = 1f,
    val isFrontCamera: Boolean = true,
    val capturedImagePath: String? = null,
    val isCapturing: Boolean = false,
    val showFilterStrip: Boolean = true,
    val activeMode: CameraMode = CameraMode.BEAUTY
)

enum class CameraMode { BEAUTY, BODY, NORMAL }

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val filterRepository: FilterRepository,
    private val photoRepository: PhotoRepository,
    private val gpuImageHelper: GPUImageHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        loadFilters()
    }

    private fun loadFilters() {
        val filters = filterRepository.getAllFilters()
        _uiState.value = _uiState.value.copy(filters = filters)
    }

    fun selectFilter(index: Int) {
        _uiState.value = _uiState.value.copy(selectedFilterIndex = index)
    }

    fun setFilterIntensity(intensity: Float) {
        _uiState.value = _uiState.value.copy(filterIntensity = intensity)
    }

    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(
            isFrontCamera = !_uiState.value.isFrontCamera
        )
    }

    fun setMode(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(activeMode = mode)
    }

    fun capturePhoto(bitmap: Bitmap, context: Context, mode: CameraMode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCapturing = true)

            // Apply the selected filter using a fresh instance (NOT the live preview one —
            // re-using the same GPUImageFilter instance across GL contexts corrupts the shader).
            val index = _uiState.value.selectedFilterIndex
            val intensity = _uiState.value.filterIntensity
            val freshFilter = filterRepository.getAllFilters().getOrNull(index)
            val filtered = if (freshFilter != null)
                gpuImageHelper.applyFilter(bitmap, freshFilter.filter, intensity)
            else bitmap

            when (mode) {
                CameraMode.BEAUTY, CameraMode.BODY -> {
                    // Hand bitmap directly to the editor — no MediaStore save needed yet.
                    // Editor calls saveResult() after editing is done.
                    CapturedBitmapHolder.set(filtered)
                    _uiState.value = _uiState.value.copy(
                        capturedImagePath = "from_camera",
                        isCapturing = false
                    )
                }
                CameraMode.NORMAL -> {
                    // Save to gallery immediately, then show post-capture choice card.
                    val path = photoRepository.savePhoto(filtered, context)
                    _uiState.value = _uiState.value.copy(
                        capturedImagePath = path.ifEmpty { null },
                        isCapturing = false
                    )
                }
            }
        }
    }

    fun clearCapturedImage() {
        _uiState.value = _uiState.value.copy(capturedImagePath = null)
    }

    fun getSelectedFilter(): FilterModel? {
        val state = _uiState.value
        return state.filters.getOrNull(state.selectedFilterIndex)
    }
}
