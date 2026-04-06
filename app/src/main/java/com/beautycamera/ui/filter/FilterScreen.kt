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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterUiState(
    val filters: List<FilterModel> = emptyList(),
    val selectedIndex: Int = 0,
    val intensity: Float = 1.0f,
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
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
            val scaled = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            _uiState.value = _uiState.value.copy(originalBitmap = scaled, processedBitmap = scaled.copy(Bitmap.Config.ARGB_8888, true))
            applyFilter() // Apply default filter (Natural)
        }
    }

    fun selectFilter(index: Int) {
        if (_uiState.value.selectedIndex == index) return
        _uiState.value = _uiState.value.copy(selectedIndex = index)
        applyFilter()
    }

    fun setIntensity(v: Float) {
        _uiState.value = _uiState.value.copy(intensity = v)
        applyFilter()
    }

    private fun applyFilter() {
        val original = _uiState.value.originalBitmap ?: return
        val filter = _uiState.value.filters.getOrNull(_uiState.value.selectedIndex) ?: return
        val intensity = _uiState.value.intensity

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            // Add a small delay for intensity slider to avoid too many intermediate frames
            if (intensity != 1.0f && intensity != 0.0f) {
                delay(16) 
            }
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val result = gpuImageHelper.applyFilter(original, filter.filter, intensity)
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
            if (uiState.isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinkAccent)
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

        // Filter strip
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(uiState.filters) { index, filter ->
                val isSelected = uiState.selectedIndex == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.selectFilter(index) }.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PinkAccent.copy(alpha = 0.25f) else Color(0xFF2A2A2A))
                            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) PinkAccent else Color.Transparent, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(filter.name.first().toString(), color = if (isSelected) PinkAccent else Color.Gray,
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(filter.name, color = if (isSelected) PinkAccent else Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}
