package com.beautycamera.ui.camera

import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.ui.theme.SurfaceVariant
import com.beautycamera.util.GPUImageHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    onNavigateToFace: (String) -> Unit,
    onNavigateToBody: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    // Auto-navigate for Beauty/Body; Normal shows the choice card
    LaunchedEffect(uiState.capturedImagePath) {
        val path = uiState.capturedImagePath ?: return@LaunchedEffect
        when {
            path == "from_camera" && uiState.activeMode == CameraMode.BEAUTY -> {
                viewModel.clearCapturedImage()
                onNavigateToFace("from_camera")
            }
            path == "from_camera" && uiState.activeMode == CameraMode.BODY -> {
                viewModel.clearCapturedImage()
                onNavigateToBody("from_camera")
            }
            // NORMAL mode — post-capture card stays visible, user picks action
        }
    }

    if (!cameraPermission.status.isGranted) {
        Box(
            modifier = modifier.fillMaxSize().background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { cameraPermission.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkAccent)
                ) { Text("Grant Permission") }
            }
        }
        return
    }

    // Return null for index 0 (Natural) so no overlay is drawn — raw preview is shown as-is.
    val selectedFilter = remember(uiState.selectedFilterIndex, uiState.filters) {
        if (uiState.selectedFilterIndex == 0) null
        else uiState.filters.getOrNull(uiState.selectedFilterIndex)?.filter
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        CameraPreview(
            isFrontCamera = uiState.isFrontCamera,
            selectedFilter = selectedFilter,
            filterIntensity = uiState.filterIntensity,
            modifier = Modifier.fillMaxSize(),
            onImageCaptureReady = { imageCapture = it }
        )

        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark.copy(alpha = 0.8f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CameraMode.values().forEach { mode ->
                        val isSelected = uiState.activeMode == mode
                        Text(
                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PinkAccent else Color.Transparent)
                                .clickable { viewModel.setMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                // Hint text below mode selector
                Text(
                    text = when (uiState.activeMode) {
                        CameraMode.BEAUTY -> "📸 → Face Editor"
                        CameraMode.BODY   -> "📸 → Body Editor"
                        CameraMode.NORMAL -> "📸 → Save only"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            IconButton(
                onClick = { viewModel.toggleCamera() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark.copy(alpha = 0.8f))
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Flip Camera", tint = Color.White)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = uiState.selectedFilterIndex != 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        "Intensity: ${(uiState.filterIntensity * 100).toInt()}",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = uiState.filterIntensity,
                        onValueChange = { viewModel.setFilterIntensity(it) },
                        colors = SliderDefaults.colors(thumbColor = PinkAccent, activeTrackColor = PinkAccent)
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark.copy(alpha = 0.9f))
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(uiState.filters) { index, filter ->
                    val isSelected = uiState.selectedFilterIndex == index
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { viewModel.selectFilter(index) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PinkAccent.copy(alpha = 0.3f) else SurfaceDark)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) PinkAccent else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.name.first().toString(),
                                color = if (isSelected) PinkAccent else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = filter.name,
                            color = if (isSelected) PinkAccent else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .padding(vertical = 20.dp, horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left — go straight to Face Editor (no photo needed)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onNavigateToFace("picker") },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PinkAccent.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.FaceRetouchingNatural, contentDescription = "Face Editor", tint = PinkAccent)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Face Edit", color = PinkAccent, fontSize = 10.sp)
                }

                // Centre — capture button
                CaptureButton(
                    isCapturing = uiState.isCapturing,
                    imageCapture = imageCapture,
                    onCapture = { bitmap -> viewModel.capturePhoto(bitmap, context, uiState.activeMode) }
                )

                // Right — go straight to Body Editor (no photo needed)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onNavigateToBody("picker") },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PinkAccent.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Body Editor", tint = PinkAccent)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Body Edit", color = PinkAccent, fontSize = 10.sp)
                }
            }
        }

        if (uiState.isCapturing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PinkAccent)
            }
        }

        // ── Post-capture action card — slides up after photo is saved ─────────
        AnimatedVisibility(
            visible = uiState.capturedImagePath != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PostCaptureCard(
                onEditFace = {
                    uiState.capturedImagePath?.let { path ->
                        viewModel.clearCapturedImage()
                        onNavigateToFace(android.net.Uri.encode(path))
                    }
                },
                onEditBody = {
                    uiState.capturedImagePath?.let { path ->
                        viewModel.clearCapturedImage()
                        onNavigateToBody(android.net.Uri.encode(path))
                    }
                },
                onSaveOnly = { viewModel.clearCapturedImage() }
            )
        }
    }
}

@Composable
private fun PostCaptureCard(
    onEditFace: () -> Unit,
    onEditBody: () -> Unit,
    onSaveOnly: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            // Success indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PinkAccent,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    "Photo saved! What would you like to do?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // Edit Face button
            Button(
                onClick = onEditFace,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FaceRetouchingNatural, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Edit Face", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Edit Body button
            OutlinedButton(
                onClick = onEditBody,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PinkAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = PinkAccent)
                Spacer(Modifier.width(10.dp))
                Text("Edit Body", color = PinkAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Save only
            TextButton(onClick = onSaveOnly) {
                Text("Save only, continue camera", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CaptureButton(
    isCapturing: Boolean,
    imageCapture: ImageCapture?,
    onCapture: (Bitmap) -> Unit
) {
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, PinkAccent, CircleShape)
            .clickable(enabled = !isCapturing && imageCapture != null) {
                imageCapture?.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val bitmap = image.toBitmap()
                            image.close()
                            // Apply capture rotation so the saved image is upright
                            val final = if (rotationDegrees != 0) {
                                val m = android.graphics.Matrix().apply {
                                    postRotate(rotationDegrees.toFloat())
                                }
                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                                    .also { bitmap.recycle() }
                            } else bitmap
                            onCapture(final)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = PinkAccent, strokeWidth = 3.dp)
        } else {
            Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(PinkAccent.copy(alpha = 0.15f)))
        }
    }
}

/**
 * Camera preview with live filter overlay.
 *
 * Architecture:
 * - PreviewView (bottom): raw hardware camera feed via Preview + ImageCapture (2 use cases,
 *   works on ALL devices — no more silent 3-use-case fallback).
 * - Compose Image (top): filtered bitmap grabbed from PreviewView.bitmap every ~100ms,
 *   processed on a background thread, shown as a Compose overlay.
 *
 * This approach is fully reliable: no ImageAnalysis dependency, no GPUImageView GL threading
 * issues. The filter is visible immediately when tapped.
 */
@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    selectedFilter: GPUImageFilter?,
    filterIntensity: Float,
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gpuImageHelper = remember { GPUImageHelper(context) }
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }
    var filteredOverlay by remember { mutableStateOf<Bitmap?>(null) }
    // Prevents queueing multiple filter jobs when GPU is still busy with previous frame
    val isProcessingFrame = remember { mutableStateOf(false) }

    // Live filter preview loop.
    // - Waits 300ms before starting (debounce) — no processing while user is scrolling through filters
    // - Bitmap scaled to 320px max before GPU processing
    // - Skips frame if previous still processing
    // - Recycles old overlay bitmaps immediately
    LaunchedEffect(selectedFilter) {
        filteredOverlay?.recycle()
        filteredOverlay = null
        isProcessingFrame.value = false

        if (selectedFilter == null) return@LaunchedEffect

        // Debounce: wait for user to settle on a filter before starting GPU work
        delay(300)

        while (true) {
            delay(150)
            if (isProcessingFrame.value) continue

            val rawBmp = previewViewRef.value?.bitmap ?: continue
            isProcessingFrame.value = true

            val result = withContext(Dispatchers.Default) {
                val scale = 320f / maxOf(rawBmp.width, rawBmp.height)
                val small = if (scale < 1f)
                    Bitmap.createScaledBitmap(rawBmp, (rawBmp.width * scale).toInt(),
                        (rawBmp.height * scale).toInt(), false)
                else rawBmp
                val filtered = gpuImageHelper.applyFilterPreview(small, selectedFilter)
                if (small !== rawBmp) small.recycle()
                filtered
            }

            filteredOverlay?.recycle()
            filteredOverlay = result
            isProcessingFrame.value = false
        }
    }

    // key() forces full teardown + recreation only when the camera selector changes.
    key(isFrontCamera) {
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        Box(modifier = modifier) {

            // ── Bottom layer: raw PreviewView (Preview + ImageCapture, always 2 use cases) ──
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    previewViewRef.value = previewView
                    bindCamera(ctx, lifecycleOwner, isFrontCamera, cameraProviderFuture,
                        previewView, onImageCaptureReady)
                    previewView
                }
            )

            // ── Top layer: filtered overlay — shown only when a filter is active ──
            filteredOverlay?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = filterIntensity),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** Binds Preview + ImageCapture (2 use cases — works on all devices). */
private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isFrontCamera: Boolean,
    cameraProviderFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
    previewView: PreviewView,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val cameraSelector = if (isFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder().build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}
