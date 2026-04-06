package com.beautycamera.ui.camera

import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
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

    val selectedFilter = remember(uiState.selectedFilterIndex, uiState.filters) {
        uiState.filters.getOrNull(uiState.selectedFilterIndex)?.filter
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
 * - PreviewView (bottom): raw hardware camera feed — always visible, guarantees reliable capture.
 * - GPUImageView (top):   ImageAnalysis frames with the selected GPU filter applied.
 *                         alpha = filterIntensity so the slider blends raw ↔ filtered.
 *
 * Camera binding: tries Preview + ImageAnalysis + ImageCapture (3 use cases).
 * Falls back to Preview + ImageCapture (2 use cases) if the device rejects 3.
 * Capture always works because Preview + ImageCapture is the primary binding.
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
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    // Holds the GPUImageView so filter changes can be applied without rebinding the camera.
    val gpuViewRef = remember { mutableStateOf<GPUImageView?>(null) }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    // Update filter on the overlay view without touching the camera.
    LaunchedEffect(selectedFilter) {
        gpuViewRef.value?.filter = selectedFilter ?: GPUImageFilter()
    }

    // key() forces full teardown + recreation only when the camera selector changes.
    key(isFrontCamera) {
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        Box(modifier = modifier) {

            // ── Bottom layer: raw PreviewView (hardware path, reliable) ──────────
            // Camera is set up only in factory. key(isFrontCamera) recreates this
            // composable when the camera selector changes, so update never rebinds.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    bindCamera(
                        ctx, lifecycleOwner, isFrontCamera,
                        cameraProviderFuture, previewView,
                        analyzerExecutor, gpuViewRef,
                        onImageCaptureReady
                    )
                    previewView
                }
            )

            // ── Top layer: filtered GPUImageView — alpha = filterIntensity ───────
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = filterIntensity),
                factory = { ctx ->
                    GPUImageView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        setScaleType(GPUImage.ScaleType.CENTER_CROP)
                        filter = selectedFilter ?: GPUImageFilter()
                    }.also { gpuViewRef.value = it }
                },
                update = { view ->
                    view.filter = selectedFilter ?: GPUImageFilter()
                }
            )
        }
    }
}

/** Binds Preview + ImageCapture (always) and optionally ImageAnalysis for the filter overlay. */
private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isFrontCamera: Boolean,
    cameraProviderFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
    previewView: PreviewView,
    analyzerExecutor: java.util.concurrent.ExecutorService,
    gpuViewRef: MutableState<GPUImageView?>,
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

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(480, 640))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { analysis ->
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val src = imageProxy.toBitmap()
                    imageProxy.close()

                    val rotated = if (rotationDegrees != 0) {
                        val m = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
                            .also { src.recycle() }
                    } else src

                    val frame = if (isFrontCamera) {
                        val m = android.graphics.Matrix().apply {
                            postScale(-1f, 1f, rotated.width / 2f, rotated.height / 2f)
                        }
                        Bitmap.createBitmap(rotated, 0, 0, rotated.width, rotated.height, m, false)
                            .also { rotated.recycle() }
                    } else rotated

                    gpuViewRef.value?.setImage(frame)
                }
            }

        try {
            cameraProvider.unbindAll()
            // Try with filter overlay (3 use cases).
            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture
                )
            } catch (e: Exception) {
                // Device doesn't support 3 simultaneous use cases — fall back to 2.
                // Capture still works; filter overlay just won't update live.
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            }
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}
