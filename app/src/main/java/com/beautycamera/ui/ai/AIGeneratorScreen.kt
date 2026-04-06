package com.beautycamera.ui.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
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

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadImage(context, it) }
    }

    Column(
        modifier = modifier.fillMaxSize().background(BackgroundDark)
    ) {
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
            Text("AI Portrait", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photo selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceVariant)
                    .border(2.dp, if (uiState.selectedBitmap != null) PinkAccent else Color.Gray, RoundedCornerShape(16.dp))
                    .clickable { pickImageLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                uiState.selectedBitmap?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = PinkAccent, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to select photo", color = Color.Gray, fontSize = 14.sp)
                }
            }

            // Style templates
            Text("Choose Style", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(810.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.templates) { template ->
                    val isSelected = uiState.selectedTemplate?.id == template.id
                    Box(
                        modifier = Modifier
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariant)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) PinkAccent else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectTemplate(template) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = when (template.id) {
                                    "golden_hour_rooftop"    -> "🌅"
                                    "rainy_street_cinematic" -> "🌧️"
                                    "minimal_studio"         -> "📷"
                                    "outdoor_forest"         -> "🌿"
                                    "coffee_shop"            -> "☕"
                                    "formal_editorial"       -> "👔"
                                    "beach_sunset"           -> "🌊"
                                    "urban_daylight"         -> "🏙️"
                                    "low_key_portrait"       -> "🎭"
                                    "home_interior"          -> "🏠"
                                    else                     -> "🖼️"
                                },
                                fontSize = 36.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                template.name,
                                color = if (isSelected) PinkAccent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PinkAccent,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                            )
                        }
                    }
                }
            }

            // Generate button
            Button(
                onClick = { viewModel.generate() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = uiState.selectedBitmap != null && uiState.selectedTemplate != null && !uiState.isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Text(
                            text = if (uiState.statusMessage.isNotBlank())
                                "${uiState.statusMessage} ${(uiState.progress * 100).toInt()}%"
                            else
                                "Generating... ${(uiState.progress * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate AI Portrait", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Progress bar
            if (uiState.isGenerating) {
                LinearProgressIndicator(
                    progress = uiState.progress,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = PinkAccent,
                    trackColor = SurfaceVariant
                )
            }

            // Result
            uiState.resultBitmap?.let { bitmap ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Result", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                            .clip(RoundedCornerShape(16.dp)).background(SurfaceVariant)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Generated",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out my AI portrait!")
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                            },
                            modifier = Modifier.weight(1f),
                            border = ButtonDefaults.outlinedButtonBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PinkAccent)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = { viewModel.saveResult(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PinkAccent)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(if (uiState.isSaved) "Saved!" else "Save", color = Color.White)
                        }
                    }
                }
            }

            // Error
            uiState.errorMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D0000)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
