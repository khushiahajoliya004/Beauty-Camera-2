package com.beautycamera.ui.face

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.ui.theme.SurfaceVariant
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.filled.CameraAlt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceEditorScreen(
    imagePath: String,
    onNavigateBack: () -> Unit,
    onNavigateToBody: (String) -> Unit = {},
    onNavigateToSticker: (String) -> Unit = {},
    viewModel: FaceEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val localDensity = LocalDensity.current

    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var canvasHeightPx by remember { mutableFloatStateOf(0f) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadImage(context, android.net.Uri.encode(it.toString())) }
    }

    val inEditorCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.loadBitmap(it) }
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { croppedUri ->
                    viewModel.updateAfterCrop(context, croppedUri)
                }
            }
        }
    }

    // Text input state (kept in composable, only committed to VM on confirm)
    var textInput by remember { mutableStateOf("") }
    var textColor by remember { mutableStateOf(Color.White) }
    var textSize by remember { mutableStateOf(40f) }

    LaunchedEffect(imagePath) {
        viewModel.loadImage(context, imagePath)
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {

        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Face Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                }
                IconButton(onClick = { onNavigateToBody(imagePath) }) {
                    Icon(Icons.Default.AccessibilityNew, contentDescription = "Body Editor", tint = Color.White)
                }
                TextButton(onClick = { viewModel.setBeforeMode(!uiState.isBeforeMode) }) {
                    Text(if (uiState.isBeforeMode) "After" else "Before", color = PinkAccent)
                }
                IconButton(onClick = {
                    viewModel.saveResult(context, canvasWidthPx, canvasHeightPx, density)
                }) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = PinkAccent)
                }
            }
        }

        // ── Image canvas + sticker/text overlay ──────────────────────────────
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
                    detectTapGestures {
                        viewModel.selectSticker(null)
                        viewModel.selectText(null)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = if (uiState.isBeforeMode) uiState.originalBitmap
                                else uiState.processedBitmap

            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (!uiState.isProcessing) {
                PickPhotoPrompt(
                    onCamera = { inEditorCameraLauncher.launch(null) },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
            }

            // ── Placed stickers ───────────────────────────────────────────────
            uiState.placedStickers.forEach { sticker ->
                val isSelected = uiState.selectedStickerId == sticker.id
                val stickerSizeDp = (80 * sticker.scale).dp

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
                        .pointerInput(sticker.id) {
                            detectTransformGestures { _, pan, zoom, rot ->
                                viewModel.selectSticker(sticker.id)
                                viewModel.updateStickerPosition(sticker.id, pan.x, pan.y)
                                viewModel.updateStickerTransform(sticker.id, zoom, rot)
                            }
                        }
                        .pointerInput("tap_${sticker.id}") {
                            detectTapGestures { viewModel.selectSticker(sticker.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = sticker.emoji.ifEmpty { "🎨" }, fontSize = (40 * sticker.scale).sp)
                }

                // Delete button (outside gesture scope)
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
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // ── Text layers ───────────────────────────────────────────────────
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
                    Text(layer.text, color = Color(layer.color), fontSize = layer.fontSize.sp, fontWeight = FontWeight.Bold)
                }

                // Delete button for text
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
                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Processing indicator
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = PinkAccent) }
            }

            if (uiState.isDetectingFace) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PinkAccent, strokeWidth = 2.dp)
                    Text("Detecting face…", color = Color.White, fontSize = 12.sp)
                }
            } else if (uiState.originalBitmap != null && uiState.faceLandmarks == null
                && uiState.selectedTool == BeautyTool.MAKEUP
                && uiState.selectedMakeupTool in listOf(MakeupSubTool.LIPS, MakeupSubTool.BLUSH, MakeupSubTool.EYES)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("No face detected — effect won't apply", color = Color(0xFFFFAA00), fontSize = 12.sp)
                }
            }

            if (uiState.isSaved) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) { Text("Photo saved!") }
            }
        }

        // ── Main tool row ────────────────────────────────────────────────────
        LazyRow(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(BeautyTool.values().toList()) { tool ->
                ToolItem(
                    icon = toolIcon(tool),
                    label = toolLabel(tool),
                    isSelected = uiState.selectedTool == tool,
                    onClick = { viewModel.selectTool(tool) }
                )
            }
        }

        // ── MakeUp sub-tool row ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.selectedTool == BeautyTool.MAKEUP,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(MakeupSubTool.values().toList()) { sub ->
                    ToolItem(
                        icon = makeupSubIcon(sub),
                        label = subToolLabel(sub),
                        isSelected = uiState.selectedMakeupTool == sub,
                        onClick = { viewModel.selectMakeupSubTool(sub) },
                        iconSize = 18.dp,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // ── Control panel ────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(16.dp)
        ) {
            when (uiState.selectedTool) {
                BeautyTool.ADJUST -> AdjustPanel(uiState, viewModel)
                BeautyTool.BLUR -> IntensitySlider(
                    label = "Blur",
                    value = uiState.blurIntensity,
                    onValueChange = { viewModel.updateBlurIntensity(it) }
                )
                BeautyTool.BRIGHTNESS -> BrightnessPanel(uiState, viewModel)
                BeautyTool.CROP -> CropPanel(
                    onCrop = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val srcUri = viewModel.saveBitmapToCache(context) ?: return@launch
                            val destUri = Uri.fromFile(
                                File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                            )
                            val intent = UCrop.of(srcUri, destUri)
                                .withOptions(UCrop.Options().apply {
                                    setToolbarColor(android.graphics.Color.parseColor("#0D0D0D"))
                                    setStatusBarColor(android.graphics.Color.parseColor("#0D0D0D"))
                                    setToolbarWidgetColor(android.graphics.Color.WHITE)
                                    setActiveControlsWidgetColor(android.graphics.Color.parseColor("#FF6B9D"))
                                })
                                .getIntent(context)
                            withContext(Dispatchers.Main) { cropLauncher.launch(intent) }
                        }
                    }
                )
                BeautyTool.MAKEUP -> MakeupPanel(uiState, viewModel)
                BeautyTool.STICKER -> StickerToolPanel(uiState, viewModel)
            }
        }
    }

    // ── Sticker picker bottom sheet ──────────────────────────────────────────
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
                            Text(text = sticker.emoji.ifEmpty { "🎨" }, fontSize = 28.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Text input dialog ────────────────────────────────────────────────────
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

// ── Sticker tool panel (shown in control area when STICKER tool is active) ────

@Composable
private fun StickerToolPanel(state: FaceEditorUiState, vm: FaceEditorViewModel) {
    val selectedSticker = state.placedStickers.find { it.id == state.selectedStickerId }
    val selectedText = state.textLayers.find { it.id == state.selectedTextId }

    when {
        selectedSticker != null -> {
            // Size slider for selected sticker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Size", color = Color.White, fontSize = 13.sp)
                    Text("${(selectedSticker.scale * 100).toInt()}%", color = PinkAccent, fontSize = 13.sp)
                    IconButton(
                        onClick = { vm.deleteSticker(selectedSticker.id) },
                        modifier = Modifier.size(36.dp).background(Color.Red.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = selectedSticker.scale,
                    onValueChange = { vm.updateStickerScale(selectedSticker.id, it) },
                    valueRange = 0.3f..4f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
                )
            }
        }
        selectedText != null -> {
            // Font size slider for selected text
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Font Size", color = Color.White, fontSize = 13.sp)
                    Text("${selectedText.fontSize.toInt()}sp", color = PinkAccent, fontSize = 13.sp)
                    IconButton(
                        onClick = { vm.deleteText(selectedText.id) },
                        modifier = Modifier.size(36.dp).background(Color.Red.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Slider(
                    value = selectedText.fontSize,
                    onValueChange = { vm.updateTextSize(selectedText.id, it) },
                    valueRange = 12f..120f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
                )
            }
        }
        else -> {
            // Default: Add sticker / Add text buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { vm.toggleStickerSheet(true) }
                ) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = null, tint = PinkAccent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Stickers", color = Color.Gray, fontSize = 11.sp)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { vm.toggleTextDialog(true) }
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = PinkAccent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Text", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Pick-photo prompt ─────────────────────────────────────────────────────────

@Composable
fun PickPhotoPrompt(onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Choose a Photo to Edit", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Take a new photo or pick one from your gallery", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp).clip(CircleShape)
                        .background(PinkAccent.copy(alpha = 0.15f))
                        .border(2.dp, PinkAccent, CircleShape)
                        .clickable { onCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo", tint = PinkAccent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Camera", color = Color.Gray, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp).clip(CircleShape)
                        .background(PinkAccent.copy(alpha = 0.15f))
                        .border(2.dp, PinkAccent, CircleShape)
                        .clickable { onGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick from Gallery", tint = PinkAccent, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Gallery", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// ── Reusable tool chip ────────────────────────────────────────────────────────

@Composable
fun ToolItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PinkAccent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null,
            tint = if (isSelected) PinkAccent else Color.Gray, modifier = Modifier.size(iconSize))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (isSelected) PinkAccent else Color.Gray, fontSize = fontSize)
    }
}

// ── Icon / label helpers ──────────────────────────────────────────────────────

private fun toolIcon(tool: BeautyTool) = when (tool) {
    BeautyTool.ADJUST     -> Icons.Default.Tune
    BeautyTool.BLUR       -> Icons.Default.BlurOn
    BeautyTool.BRIGHTNESS -> Icons.Default.BrightnessHigh
    BeautyTool.CROP       -> Icons.Default.Crop
    BeautyTool.MAKEUP     -> Icons.Default.FaceRetouchingNatural
    BeautyTool.STICKER    -> Icons.Default.EmojiEmotions
}

private fun toolLabel(tool: BeautyTool) = when (tool) {
    BeautyTool.ADJUST     -> "Adjust"
    BeautyTool.BLUR       -> "Blur"
    BeautyTool.BRIGHTNESS -> "Brightness"
    BeautyTool.CROP       -> "Crop"
    BeautyTool.MAKEUP     -> "MakeUp"
    BeautyTool.STICKER    -> "Sticker"
}

private fun makeupSubIcon(sub: MakeupSubTool) = when (sub) {
    MakeupSubTool.SKIN       -> Icons.Default.Face
    MakeupSubTool.LIPS       -> Icons.Default.Favorite
    MakeupSubTool.BLUSH      -> Icons.Default.FaceRetouchingNatural
    MakeupSubTool.EYES       -> Icons.Default.RemoveRedEye
    MakeupSubTool.FOUNDATION -> Icons.Default.AutoFixHigh
}

private fun subToolLabel(sub: MakeupSubTool) = when (sub) {
    MakeupSubTool.SKIN       -> "Skin"
    MakeupSubTool.LIPS       -> "Lips"
    MakeupSubTool.BLUSH      -> "Blush"
    MakeupSubTool.EYES       -> "Eyes"
    MakeupSubTool.FOUNDATION -> "Foundation"
}

// ── Control panels ────────────────────────────────────────────────────────────

@Composable
private fun AdjustPanel(state: FaceEditorUiState, vm: FaceEditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        IntensitySlider("Contrast", state.contrast,
            displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" }) { vm.updateContrast(it) }
        IntensitySlider("Saturation", state.saturation,
            displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" }) { vm.updateSaturation(it) }
        IntensitySlider("Sharpness", state.sharpness) { vm.updateSharpness(it) }
    }
}

@Composable
private fun BrightnessPanel(state: FaceEditorUiState, vm: FaceEditorViewModel) {
    IntensitySlider("Brightness", state.brightness,
        displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" }) { vm.updateBrightness(it) }
}

@Composable
private fun CropPanel(onCrop: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = onCrop,
            colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Crop Image")
        }
    }
}

@Composable
private fun MakeupPanel(state: FaceEditorUiState, vm: FaceEditorViewModel) {
    when (state.selectedMakeupTool) {
        MakeupSubTool.SKIN -> IntensitySlider("Skin Smoothing", state.beautySettings.skinSmoothing) { vm.updateSkinSmoothing(it) }
        MakeupSubTool.LIPS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntensitySlider("Lip Opacity", state.beautySettings.lipOpacity) { vm.updateLipOpacity(it) }
            ColorPicker(selectedColor = Color(state.beautySettings.lipColor), onColorSelected = { vm.setLipColor(it.toArgb()) })
        }
        MakeupSubTool.BLUSH -> IntensitySlider("Blush Intensity", state.beautySettings.blushIntensity) { vm.updateBlushIntensity(it) }
        MakeupSubTool.EYES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntensitySlider("Eye Color Opacity", state.beautySettings.eyeColorOpacity) { vm.updateEyeColorOpacity(it) }
            ColorPicker(selectedColor = Color(state.beautySettings.eyeColor), onColorSelected = { vm.setEyeColor(it.toArgb()) })
        }
        MakeupSubTool.FOUNDATION -> IntensitySlider("Foundation", state.beautySettings.foundationIntensity) { vm.updateFoundationIntensity(it) }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun IntensitySlider(
    label: String,
    value: Float,
    displayValue: ((Float) -> String)? = null,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(displayValue?.invoke(value) ?: "${(value * 100).toInt()}", color = PinkAccent, fontSize = 13.sp)
        }
        Slider(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
        )
    }
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.Red, Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF009688),
        Color(0xFF4CAF50), Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFF9800),
        Color(0xFFFF5722), Color(0xFF795548), Color.Gray, Color.White
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(32.dp).clip(CircleShape).background(color)
                    .border(if (color == selectedColor) 2.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
