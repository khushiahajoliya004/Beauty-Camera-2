package com.beautycamera.ui.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.data.repository.StickerRepository
import com.beautycamera.domain.model.StickerModel
import com.beautycamera.domain.model.TextLayerModel
import com.beautycamera.ui.face.ColorPicker
import com.beautycamera.ui.face.IntensitySlider
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.ui.theme.SurfaceVariant
import com.beautycamera.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class StickerUiState(
    val originalBitmap: Bitmap? = null,
    val availableStickers: List<StickerModel> = emptyList(),
    val placedStickers: List<StickerModel> = emptyList(),
    val textLayers: List<TextLayerModel> = emptyList(),
    val selectedStickerId: String? = null,
    val selectedTextId: String? = null,
    val showStickerSheet: Boolean = false,
    val showTextDialog: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class StickerViewModel @Inject constructor(
    private val stickerRepository: StickerRepository,
    private val bitmapUtils: BitmapUtils,
    private val photoRepository: PhotoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StickerUiState())
    val uiState: StateFlow<StickerUiState> = _uiState.asStateFlow()

    init { _uiState.value = _uiState.value.copy(availableStickers = stickerRepository.getAllStickers()) }

    fun loadImage(context: Context, imagePath: String) {
        viewModelScope.launch {
            val bitmap = try {
                bitmapUtils.uriToBitmap(context, Uri.parse(Uri.decode(imagePath)))
            } catch (e: Exception) { null } ?: return@launch
            _uiState.value = _uiState.value.copy(
                originalBitmap = bitmapUtils.scaleBitmap(bitmap, 1080, 1920)
            )
        }
    }

    fun addSticker(sticker: StickerModel) {
        val newSticker = sticker.copy(id = UUID.randomUUID().toString(), x = 200f, y = 400f, scale = 1f)
        _uiState.value = _uiState.value.copy(
            placedStickers = _uiState.value.placedStickers + newSticker,
            showStickerSheet = false,
            selectedStickerId = newSticker.id
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

    fun updateTextSize(id: String, fontSize: Float) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.map {
                if (it.id == id) it.copy(fontSize = fontSize.coerceIn(12f, 120f)) else it
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

    fun selectText(id: String?) {
        _uiState.value = _uiState.value.copy(selectedTextId = id, selectedStickerId = null)
    }

    fun addTextLayer(text: String, color: Int, fontSize: Float) {
        val layer = TextLayerModel(
            id = UUID.randomUUID().toString(), text = text,
            x = 200f, y = 300f, color = color, fontSize = fontSize
        )
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers + layer,
            showTextDialog = false,
            selectedTextId = layer.id
        )
    }

    fun updateTextPosition(id: String, dx: Float, dy: Float) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.map {
                if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
            }
        )
    }

    fun deleteText(id: String) {
        _uiState.value = _uiState.value.copy(
            textLayers = _uiState.value.textLayers.filter { it.id != id },
            selectedTextId = null
        )
    }

    fun toggleStickerSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showStickerSheet = show)
    }

    fun toggleTextDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTextDialog = show)
    }

    fun saveResult(context: Context, canvasWidthPx: Float, canvasHeightPx: Float, density: Float) {
        val bitmap = _uiState.value.originalBitmap ?: return
        val stickers = _uiState.value.placedStickers
        val texts = _uiState.value.textLayers
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                flattenCanvas(bitmap, stickers, texts, canvasWidthPx, canvasHeightPx, density)
            }
            photoRepository.savePhoto(result, context)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    private fun flattenCanvas(
        bitmap: Bitmap,
        stickers: List<StickerModel>,
        texts: List<TextLayerModel>,
        canvasW: Float,
        canvasH: Float,
        density: Float
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)

        // ContentScale.Fit: image scaled to fit inside canvasW x canvasH
        val fitScale = minOf(canvasW / bitmap.width, canvasH / bitmap.height)
        val dispW = bitmap.width * fitScale
        val dispH = bitmap.height * fitScale
        val offX = (canvasW - dispW) / 2f
        val offY = (canvasH - dispH) / 2f

        // Map screen px -> bitmap px
        fun mapX(sx: Float) = (sx - offX) / fitScale
        fun mapY(sy: Float) = (sy - offY) / fitScale

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw stickers
        stickers.forEach { sticker ->
            val bx = mapX(sticker.x)
            val by = mapY(sticker.y)
            // 80dp sticker box converted to bitmap pixels
            val boxPx = (80f * density) / fitScale * sticker.scale
            paint.textSize = boxPx * 0.75f
            canvas.save()
            canvas.translate(bx + boxPx * 0.5f, by + boxPx * 0.5f)
            canvas.rotate(sticker.rotation)
            canvas.drawText(
                sticker.emoji.ifEmpty { "🎨" },
                -paint.textSize * 0.4f, paint.textSize * 0.35f, paint
            )
            canvas.restore()
        }

        // Draw text layers
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerScreen(
    imagePath: String,
    onNavigateBack: () -> Unit,
    viewModel: StickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current.density

    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var canvasHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imagePath) { viewModel.loadImage(context, imagePath) }

    var textInput by remember { mutableStateOf("") }
    var textColor by remember { mutableStateOf(Color.White) }
    var textSize by remember { mutableStateOf(40f) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Sticker & Text", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = {
                viewModel.saveResult(context, canvasWidthPx, canvasHeightPx, density)
            }) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = PinkAccent)
            }
        }

        // Canvas area
        val localDensity = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .onGloballyPositioned { coords ->
                    canvasWidthPx = coords.size.width.toFloat()
                    canvasHeightPx = coords.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { viewModel.selectSticker(null) }
                }
        ) {
            uiState.originalBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                )
            }

            // Placed stickers
            uiState.placedStickers.forEach { sticker ->
                val isSelected = uiState.selectedStickerId == sticker.id
                // Use actual dynamic size so touch target matches visual size
                val stickerSizeDp = (80 * sticker.scale).dp

                // Sticker body — gesture handling only, no delete button inside
                Box(
                    modifier = Modifier
                        .offset { IntOffset(sticker.x.roundToInt(), sticker.y.roundToInt()) }
                        .size(stickerSizeDp)
                        .graphicsLayer { rotationZ = sticker.rotation }
                        .border(
                            if (isSelected) 2.dp else 0.dp,
                            if (isSelected) PinkAccent else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        // Single gesture handler: handles pan (drag), zoom, rotation
                        .pointerInput(sticker.id) {
                            detectTransformGestures { _, pan, zoom, rot ->
                                viewModel.selectSticker(sticker.id)
                                viewModel.updateStickerPosition(sticker.id, pan.x, pan.y)
                                viewModel.updateStickerTransform(sticker.id, zoom, rot)
                            }
                        }
                        // Tap-only handler for selection without drag
                        .pointerInput("tap_${sticker.id}") {
                            detectTapGestures { viewModel.selectSticker(sticker.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = sticker.emoji.ifEmpty { "🎨" }, fontSize = (40 * sticker.scale).sp)
                }

                // Delete button — sibling composable, completely outside gesture scope
                if (isSelected) {
                    val stickerSizePx = with(localDensity) { stickerSizeDp.roundToPx() }
                    val btnSizePx = with(localDensity) { 24.dp.roundToPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    sticker.x.roundToInt() + stickerSizePx - btnSizePx / 2,
                                    sticker.y.roundToInt() - btnSizePx / 2
                                )
                            }
                            .size(24.dp)
                            .background(Color.Red, CircleShape)
                            .clickable { viewModel.deleteSticker(sticker.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close, contentDescription = "Delete",
                            tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Text layers
            uiState.textLayers.forEach { layer ->
                val isSelected = uiState.selectedTextId == layer.id

                Box(
                    modifier = Modifier
                        .offset { IntOffset(layer.x.roundToInt(), layer.y.roundToInt()) }
                        .border(
                            if (isSelected) 1.dp else 0.dp,
                            if (isSelected) PinkAccent else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                        .pointerInput(layer.id) {
                            detectTransformGestures { _, pan, _, _ ->
                                viewModel.selectText(layer.id)
                                viewModel.updateTextPosition(layer.id, pan.x, pan.y)
                            }
                        }
                        .pointerInput("tap_${layer.id}") {
                            detectTapGestures { viewModel.selectText(layer.id) }
                        }
                ) {
                    Text(
                        layer.text, color = Color(layer.color),
                        fontSize = layer.fontSize.sp, fontWeight = FontWeight.Bold
                    )
                }

                // Delete button for text — sibling, outside gesture scope
                if (isSelected) {
                    val approxTextWidthPx = with(localDensity) {
                        (layer.fontSize * layer.text.length * 0.6f).dp.roundToPx()
                    }
                    val btnSizePx = with(localDensity) { 24.dp.roundToPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    layer.x.roundToInt() + approxTextWidthPx,
                                    layer.y.roundToInt() - btnSizePx / 2
                                )
                            }
                            .size(24.dp)
                            .background(Color.Red, CircleShape)
                            .clickable { viewModel.deleteText(layer.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close, contentDescription = "Delete",
                            tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Bottom toolbar — shows size controls when something is selected
        val selectedSticker = uiState.placedStickers.find { it.id == uiState.selectedStickerId }
        val selectedText = uiState.textLayers.find { it.id == uiState.selectedTextId }

        if (selectedSticker != null) {
            // Sticker selected: show size slider + delete
            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Size", color = Color.White, fontSize = 13.sp)
                    Text("${(selectedSticker.scale * 100).toInt()}%", color = PinkAccent, fontSize = 13.sp)
                    IconButton(
                        onClick = { viewModel.deleteSticker(selectedSticker.id) },
                        modifier = Modifier.size(36.dp).background(Color.Red.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = selectedSticker.scale,
                    onValueChange = { viewModel.updateStickerScale(selectedSticker.id, it) },
                    valueRange = 0.3f..4f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
                )
            }
        } else if (selectedText != null) {
            // Text selected: show font size slider + delete
            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Font Size", color = Color.White, fontSize = 13.sp)
                    Text("${selectedText.fontSize.toInt()}sp", color = PinkAccent, fontSize = 13.sp)
                    IconButton(
                        onClick = { viewModel.deleteText(selectedText.id) },
                        modifier = Modifier.size(36.dp).background(Color.Red.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = selectedText.fontSize,
                    onValueChange = { viewModel.updateTextSize(selectedText.id, it) },
                    valueRange = 12f..120f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
                )
            }
        } else {
            // Nothing selected: show add tools
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.toggleStickerSheet(true) }
                ) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = PinkAccent, modifier = Modifier.size(28.dp))
                    Text("Stickers", color = Color.Gray, fontSize = 11.sp)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.toggleTextDialog(true) }
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = PinkAccent, modifier = Modifier.size(28.dp))
                    Text("Text", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }

    // Sticker picker sheet
    if (uiState.showStickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleStickerSheet(false) },
            containerColor = SurfaceDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Stickers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(220.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.availableStickers) { sticker ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariant)
                                .clickable { viewModel.addSticker(sticker) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sticker.emoji.ifEmpty { "🎨" },
                                fontSize = 28.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Text dialog
    if (uiState.showTextDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleTextDialog(false) },
            containerColor = SurfaceDark,
            title = { Text("Add Text", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Enter text", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = PinkAccent, unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Color", color = Color.White, fontSize = 13.sp)
                    ColorPicker(selectedColor = textColor, onColorSelected = { textColor = it })
                    IntensitySlider("Font Size", textSize / 100f) { textSize = it * 100f }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.addTextLayer(textInput, textColor.toArgb(), textSize)
                        textInput = ""
                    }
                }) { Text("Add", color = PinkAccent) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleTextDialog(false) }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
