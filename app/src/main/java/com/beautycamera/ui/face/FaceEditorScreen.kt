package com.beautycamera.ui.face

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadImage(context, android.net.Uri.encode(it.toString())) }
    }

    // In-editor camera (TakePicturePreview — no FileProvider needed)
    val inEditorCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.loadBitmap(it) }
    }

    // uCrop result handler
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

    LaunchedEffect(imagePath) {
        viewModel.loadImage(context, imagePath)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundDark)
    ) {
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
                IconButton(onClick = { onNavigateToSticker(imagePath) }) {
                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Sticker & Text", tint = Color.White)
                }
                TextButton(onClick = { viewModel.setBeforeMode(!uiState.isBeforeMode) }) {
                    Text(if (uiState.isBeforeMode) "After" else "Before", color = PinkAccent)
                }
                IconButton(onClick = { viewModel.saveResult(context) }) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = PinkAccent)
                }
            }
        }

        // ── Image preview ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = if (uiState.isBeforeMode) uiState.originalBitmap
                                else uiState.processedBitmap

            if (displayBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (!uiState.isProcessing) {
                // No image yet — prompt the user to pick one
                PickPhotoPrompt(
                    onCamera = { inEditorCameraLauncher.launch(null) },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
            }

            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PinkAccent)
                }
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) { Text("Photo saved!") }
            }
        }

        // ── Main tool row ────────────────────────────────────────────────────
        // All 5 tools displayed in one horizontal line
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(BeautyTool.values().toList()) { tool ->
                val isSelected = uiState.selectedTool == tool
                ToolItem(
                    icon = toolIcon(tool),
                    label = toolLabel(tool),
                    isSelected = isSelected,
                    onClick = { viewModel.selectTool(tool) }
                )
            }
        }

        // ── MakeUp sub-tool row (shown only when MAKEUP is active) ───────────
        AnimatedVisibility(
            visible = uiState.selectedTool == BeautyTool.MAKEUP,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariant)
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(MakeupSubTool.values().toList()) { sub ->
                    val isSelected = uiState.selectedMakeupTool == sub
                    ToolItem(
                        icon = makeupSubIcon(sub),
                        label = subToolLabel(sub),
                        isSelected = isSelected,
                        onClick = { viewModel.selectMakeupSubTool(sub) },
                        iconSize = 18.dp,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // ── Control panel ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant)
                .padding(16.dp)
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
                            withContext(Dispatchers.Main) {
                                cropLauncher.launch(intent)
                            }
                        }
                    }
                )
                BeautyTool.MAKEUP -> MakeupPanel(uiState, viewModel)
            }
        }
    }
}

// ── Pick-photo prompt (shown when no image is loaded yet) ─────────────────────

@Composable
fun PickPhotoPrompt(onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            "Choose a Photo to Edit",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Take a new photo or pick one from your gallery",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            // Camera option
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(PinkAccent.copy(alpha = 0.15f))
                        .border(2.dp, PinkAccent, CircleShape)
                        .clickable { onCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Take Photo",
                        tint = PinkAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Camera", color = Color.Gray, fontSize = 12.sp)
            }
            // Gallery option
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(PinkAccent.copy(alpha = 0.15f))
                        .border(2.dp, PinkAccent, CircleShape)
                        .clickable { onGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Pick from Gallery",
                        tint = PinkAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Gallery", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// ── Reusable tool chip ────────────────────────────────────────────────────────

@Composable
private fun ToolItem(
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) PinkAccent else Color.Gray,
            modifier = Modifier.size(iconSize)
        )
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
}

private fun toolLabel(tool: BeautyTool) = when (tool) {
    BeautyTool.ADJUST     -> "Adjust"
    BeautyTool.BLUR       -> "Blur"
    BeautyTool.BRIGHTNESS -> "Brightness"
    BeautyTool.CROP       -> "Crop"
    BeautyTool.MAKEUP     -> "MakeUp"
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
        IntensitySlider(
            label = "Contrast",
            value = state.contrast,
            displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" },
            onValueChange = { vm.updateContrast(it) }
        )
        IntensitySlider(
            label = "Saturation",
            value = state.saturation,
            displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" },
            onValueChange = { vm.updateSaturation(it) }
        )
        IntensitySlider(
            label = "Sharpness",
            value = state.sharpness,
            onValueChange = { vm.updateSharpness(it) }
        )
    }
}

@Composable
private fun BrightnessPanel(state: FaceEditorUiState, vm: FaceEditorViewModel) {
    IntensitySlider(
        label = "Brightness",
        value = state.brightness,
        displayValue = { v -> "${((v - 0.5f) * 200).toInt()}" },
        onValueChange = { vm.updateBrightness(it) }
    )
}

@Composable
private fun CropPanel(onCrop: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
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
        MakeupSubTool.SKIN -> IntensitySlider(
            label = "Skin Smoothing",
            value = state.beautySettings.skinSmoothing,
            onValueChange = { vm.updateSkinSmoothing(it) }
        )
        MakeupSubTool.LIPS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntensitySlider(
                label = "Lip Opacity",
                value = state.beautySettings.lipOpacity,
                onValueChange = { vm.updateLipOpacity(it) }
            )
            ColorPicker(
                selectedColor = Color(state.beautySettings.lipColor),
                onColorSelected = { vm.setLipColor(it.toArgb()) }
            )
        }
        MakeupSubTool.BLUSH -> IntensitySlider(
            label = "Blush Intensity",
            value = state.beautySettings.blushIntensity,
            onValueChange = { vm.updateBlushIntensity(it) }
        )
        MakeupSubTool.EYES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntensitySlider(
                label = "Eye Color Opacity",
                value = state.beautySettings.eyeColorOpacity,
                onValueChange = { vm.updateEyeColorOpacity(it) }
            )
            ColorPicker(
                selectedColor = Color(state.beautySettings.eyeColor),
                onColorSelected = { vm.setEyeColor(it.toArgb()) }
            )
        }
        MakeupSubTool.FOUNDATION -> IntensitySlider(
            label = "Foundation",
            value = state.beautySettings.foundationIntensity,
            onValueChange = { vm.updateFoundationIntensity(it) }
        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(
                displayValue?.invoke(value) ?: "${(value * 100).toInt()}",
                color = PinkAccent,
                fontSize = 13.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
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
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selectedColor) 2.dp else 0.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
