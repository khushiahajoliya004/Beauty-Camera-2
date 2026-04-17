package com.beautycamera.ui.filter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.FilterRepository
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.FilterModel
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.util.BitmapUtils
import com.beautycamera.util.GPUImageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FilterUiState(
    val filters: List<FilterModel> = emptyList(),
    val selectedIndex: Int = 0,
    val intensity: Float = 1.0f,
    val originalBitmap: Bitmap? = null,       // full-res, used only for saving
    val previewBitmap: Bitmap? = null,         // small 400px, used for live preview
    val processedBitmap: Bitmap? = null,       // what is shown on screen
    val filterPreviews: List<Bitmap?> = emptyList(),     // 400px — shown in main preview area
    val filterThumbnails: List<Bitmap?> = emptyList(),  // 80px  — shown in filter strip
    val isProcessing: Boolean = false,         // only true when saving
    val isPreviewing: Boolean = false,         // true while generating previews
    val isSaved: Boolean = false
)

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val filterRepository: FilterRepository,
    private val gpuImageHelper: GPUImageHelper,
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()
    private var filterJob: Job? = null

    init { _uiState.value = _uiState.value.copy(filters = filterRepository.getAllFilters()) }

    fun loadImage(context: Context, imagePath: String) {
        viewModelScope.launch {
            val bitmap = try {
                bitmapUtils.uriToBitmap(context, Uri.parse(Uri.decode(imagePath)))
            } catch (e: Exception) { null } ?: return@launch

            val fullRes = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            // 400px for main preview display
            val preview = bitmapUtils.scaleBitmap(bitmap, 400, 400)
            // 80px for strip thumbnails — tiny, fast to process and render
            val thumb = bitmapUtils.scaleBitmap(bitmap, 80, 80)

            _uiState.value = _uiState.value.copy(
                originalBitmap = fullRes,
                previewBitmap = preview,
                processedBitmap = preview.copy(Bitmap.Config.ARGB_8888, true),
                isPreviewing = true
            )

            generateAllPreviews(preview, thumb)
        }
    }

    private fun generateAllPreviews(previewBitmap: Bitmap, thumbBitmap: Bitmap) {
        viewModelScope.launch {
            val filters = _uiState.value.filters
            val previews = MutableList<Bitmap?>(filters.size) { null }   // 400px — for main view
            val thumbs  = MutableList<Bitmap?>(filters.size) { null }   // 80px  — for strip

            // Generate all on background thread — no state updates mid-loop (no recomposition spam)
            withContext(Dispatchers.Default) {
                filters.forEachIndexed { i, filter ->
                    previews[i] = gpuImageHelper.applyFilter(previewBitmap, filter.filter, 1.0f)
                    thumbs[i]   = gpuImageHelper.applyFilter(thumbBitmap,  filter.filter, 1.0f)
                }
            }

            // Single state update when everything is ready — one recomposition total
            _uiState.value = _uiState.value.copy(
                processedBitmap  = previews[0] ?: previewBitmap,
                filterPreviews   = previews.toList(),
                filterThumbnails = thumbs.toList(),
                isPreviewing     = false
            )
        }
    }

    fun selectFilter(index: Int) {
        if (_uiState.value.selectedIndex == index) return
        _uiState.value = _uiState.value.copy(selectedIndex = index)

        // If preview already generated → show instantly, no processing needed
        val cached = _uiState.value.filterPreviews.getOrNull(index)
        if (cached != null) {
            val withIntensity = applyIntensityBlend(cached, index)
            _uiState.value = _uiState.value.copy(processedBitmap = withIntensity)
        } else {
            // Preview not ready yet (still generating) → fallback to small bitmap
            applyFilterOnPreview()
        }
    }

    fun setIntensity(v: Float) {
        _uiState.value = _uiState.value.copy(intensity = v)
        // Re-apply intensity blend on cached preview (fast, no GPU needed at full res)
        val cached = _uiState.value.filterPreviews.getOrNull(_uiState.value.selectedIndex)
        if (cached != null) {
            val withIntensity = applyIntensityBlend(cached, _uiState.value.selectedIndex)
            _uiState.value = _uiState.value.copy(processedBitmap = withIntensity)
        } else {
            applyFilterOnPreview()
        }
    }

    // Blends cached filtered preview with original preview based on intensity
    private fun applyIntensityBlend(filtered: Bitmap, index: Int): Bitmap {
        val intensity = _uiState.value.intensity
        if (intensity >= 0.99f) return filtered
        val original = _uiState.value.previewBitmap ?: return filtered
        val result = original.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            alpha = (intensity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(filtered, 0f, 0f, paint)
        return result
    }

    // Fallback: apply filter on small preview bitmap (much faster than full-res)
    private fun applyFilterOnPreview() {
        val preview = _uiState.value.previewBitmap ?: return
        val filter = _uiState.value.filters.getOrNull(_uiState.value.selectedIndex) ?: return
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            if (_uiState.value.intensity != 1.0f && _uiState.value.intensity != 0.0f) delay(16)
            val result = gpuImageHelper.applyFilter(preview, filter.filter, _uiState.value.intensity)
            _uiState.value = _uiState.value.copy(processedBitmap = result)
        }
    }

    fun saveResult(context: Context) {
        // Apply filter on full-res original only when saving
        val original = _uiState.value.originalBitmap ?: return
        val filter = _uiState.value.filters.getOrNull(_uiState.value.selectedIndex) ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val fullResResult = withContext(Dispatchers.Default) {
                gpuImageHelper.applyFilter(original, filter.filter, _uiState.value.intensity)
            }
            photoRepository.savePhoto(fullResResult, context)
            _uiState.value = _uiState.value.copy(isSaved = true, isProcessing = false)
        }
    }
}

@Composable
fun FilterScreen(
    imagePath: String,
    onNavigateBack: () -> Unit,
    viewModel: FilterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(imagePath) { viewModel.loadImage(context, imagePath) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Filters", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = { viewModel.saveResult(context) }) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = PinkAccent)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            uiState.processedBitmap?.let {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                )
            } ?: CircularProgressIndicator(color = PinkAccent)
            // Show spinner only when saving (full-res processing), not during filter switching
            if (uiState.isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PinkAccent)
                        Spacer(Modifier.height(8.dp))
                        Text("Saving...", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Intensity slider
        Column(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Intensity", color = Color.White, fontSize = 12.sp)
                Text("${(uiState.intensity * 100).toInt()}", color = PinkAccent, fontSize = 12.sp)
            }
            Slider(value = uiState.intensity, onValueChange = { viewModel.setIntensity(it) },
                colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent))
        }

        // Filter strip — thumbnails are 80px bitmaps, rendered at 60dp
        // key(index) ensures only the tapped item recomposes on selection change
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(uiState.filters, key = { index, _ -> index }) { index, filter ->
                val isSelected = uiState.selectedIndex == index
                val thumbnail = uiState.filterThumbnails.getOrNull(index)
                FilterStripItem(
                    name = filter.name,
                    thumbnail = thumbnail,
                    isSelected = isSelected,
                    onClick = { viewModel.selectFilter(index) }
                )
            }
        }
    }
}

@Composable
private fun FilterStripItem(
    name: String,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) PinkAccent else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PinkAccent,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = if (isSelected) PinkAccent else Color.Gray, fontSize = 10.sp)
    }
}
