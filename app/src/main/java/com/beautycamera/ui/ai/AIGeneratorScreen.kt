package com.beautycamera.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautycamera.domain.model.AIArtCard
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.ui.theme.SurfaceVariant

@Composable
fun AIGeneratorScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    viewModel: AIGeneratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadImageFromGallery(context, it) }
    }

    // Camera capture (returns a preview Bitmap directly)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.loadImageFromCamera(it) }
    }

    Column(modifier = modifier.fillMaxSize().background(BackgroundDark)) {

        // ── Top bar ───────────────────────────────────────────────────────────
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "AI Art Generator",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text("Upload photo → Pick style → Generate", color = Color.Gray, fontSize = 10.sp)
            }
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Photo Upload Section ──────────────────────────────────────────
            Text(
                "1. Upload Your Photo",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (uiState.selectedPhoto == null) {
                // Empty state — show two upload options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Camera button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, PinkAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { cameraLauncher.launch(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = PinkAccent,
                                modifier = Modifier.size(38.dp)
                            )
                            Text("Take Photo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Use camera", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    // Gallery button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, PinkAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = PinkAccent,
                                modifier = Modifier.size(38.dp)
                            )
                            Text("Gallery", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Pick from photos", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                // Photo selected — show preview with change options
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = uiState.selectedPhoto!!.asImageBitmap(),
                        contentDescription = "Selected photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Overlay gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                )
                            )
                    )
                    // Change buttons at bottom
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallActionButton(
                            icon = Icons.Default.CameraAlt,
                            label = "Retake",
                            onClick = { cameraLauncher.launch(null) }
                        )
                        SmallActionButton(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Change",
                            onClick = { galleryLauncher.launch("image/*") }
                        )
                    }
                    // Checkmark badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PinkAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Style Cards Section ───────────────────────────────────────────
            Text(
                "2. Choose AI Style",
                color = if (uiState.selectedPhoto != null) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(820.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(uiState.cards) { card ->
                    ArtStyleCard(
                        card = card,
                        isSelected = uiState.selectedCard?.id == card.id,
                        isGenerating = uiState.isGenerating && uiState.selectedCard?.id == card.id,
                        enabled = uiState.selectedPhoto != null,
                        onClick = {
                            if (uiState.selectedPhoto != null) viewModel.selectCard(card)
                        }
                    )
                }
            }

            // ── Generate Button ───────────────────────────────────────────────
            val canGenerate = uiState.selectedPhoto != null && uiState.selectedCard != null && !uiState.isGenerating

            Button(
                onClick = { viewModel.generate() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = canGenerate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinkAccent,
                    disabledContainerColor = SurfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${uiState.statusMessage.ifBlank { "Generating..." }} ${(uiState.progress * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            uiState.selectedPhoto == null -> "Upload a photo first"
                            uiState.selectedCard == null  -> "Choose a style"
                            else -> "Generate AI Art"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Progress bar
            if (uiState.isGenerating) {
                LinearProgressIndicator(
                    progress = uiState.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = PinkAccent,
                    trackColor = SurfaceVariant
                )
            }

            // ── Result Section — Before / After ──────────────────────────────
            AnimatedVisibility(
                visible = uiState.resultBitmap != null,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit  = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                uiState.resultBitmap?.let { resultBitmap ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // Title row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(uiState.selectedCard?.emoji ?: "🎨", fontSize = 20.sp)
                                Text(
                                    uiState.selectedCard?.name ?: "AI Result",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            if (uiState.shuffleCount > 0) {
                                Surface(shape = RoundedCornerShape(20.dp), color = PinkAccent.copy(alpha = 0.2f)) {
                                    Text(
                                        "v${uiState.shuffleCount + 1}",
                                        color = PinkAccent, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // ── Before / After side-by-side ───────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // BEFORE
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                uiState.selectedPhoto?.let { photo ->
                                    Image(
                                        bitmap = photo.asImageBitmap(),
                                        contentDescription = "Before",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                // "Before" label
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Before", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // AFTER
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, PinkAccent, RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    bitmap = resultBitmap.asImageBitmap(),
                                    contentDescription = "After — AI Generated",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // "After" label
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PinkAccent.copy(alpha = 0.9f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("AI Art", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ── Full AI result image ──────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, PinkAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        ) {
                            Image(
                                bitmap = resultBitmap.asImageBitmap(),
                                contentDescription = "Full AI result",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Style badge
                            uiState.selectedCard?.let { card ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(card.emoji, fontSize = 13.sp)
                                        Text(card.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // ── Action buttons ────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.shuffle() },
                                modifier = Modifier.weight(1f).height(50.dp),
                                enabled = !uiState.isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Variation", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.saveResult(context) },
                                modifier = Modifier.height(50.dp),
                                border = ButtonDefaults.outlinedButtonBorder,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PinkAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    if (uiState.isSaved) Icons.Default.CheckCircle else Icons.Default.Download,
                                    contentDescription = null, modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (uiState.isSaved) "Saved" else "Save")
                            }

                            IconButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Check out my AI portrait!")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                },
                                modifier = Modifier
                                    .height(50.dp).width(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceVariant)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // ── Error card ────────────────────────────────────────────────────
            uiState.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D0000)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ArtStyleCard(
    card: AIArtCard,
    isSelected: Boolean,
    isGenerating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )
    val accentColor = Color(card.cardColor)
    val dimmed = !enabled && !isSelected

    Box(
        modifier = Modifier
            .scale(scale)
            .height(155.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (dimmed) 0.12f else 0.35f),
                        accentColor.copy(alpha = if (dimmed) 0.06f else 0.15f)
                    )
                )
            )
            .then(
                if (isSelected)
                    Modifier.border(2.dp, PinkAccent, RoundedCornerShape(16.dp))
                else
                    Modifier.border(1.dp, accentColor.copy(alpha = if (dimmed) 0.15f else 0.3f), RoundedCornerShape(16.dp))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(card.emoji, fontSize = 36.sp, color = if (dimmed) Color.Gray else Color.Unspecified)
            Text(
                card.name,
                color = when {
                    isSelected -> PinkAccent
                    dimmed     -> Color.Gray
                    else       -> Color.White
                },
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                card.description,
                color = if (dimmed) Color(0xFF555555) else Color.Gray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }

        // Selected badge
        if (isSelected && !isGenerating) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(PinkAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        // Generating spinner on card
        if (isGenerating) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(PinkAccent),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }

        // Lock icon when photo not uploaded yet
        if (dimmed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }
        }
    }
}
