package com.beautycamera.ui.body

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beautycamera.ui.face.IntensitySlider
import com.beautycamera.ui.face.PickPhotoPrompt
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.beautycamera.ui.theme.SurfaceVariant

@Composable
fun BodyEditorScreen(
    imagePath: String,
    onNavigateBack: () -> Unit,
    viewModel: BodyEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadImage(context, android.net.Uri.encode(it.toString())) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.loadBitmap(it) }
    }

    LaunchedEffect(imagePath) { viewModel.loadImage(context, imagePath) }

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
            Text("Body Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto", color = if (uiState.isAutoMode) PinkAccent else Color.Gray, fontSize = 13.sp)
                Switch(
                    checked = uiState.isAutoMode,
                    onCheckedChange = { viewModel.toggleAutoMode(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = PinkAccent, checkedTrackColor = PinkAccent.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { viewModel.setBeforeMode(!uiState.isBeforeMode) }) {
                    Text(
                        if (uiState.isBeforeMode) "After" else "Before",
                        color = PinkAccent, fontSize = 12.sp
                    )
                }
                IconButton(onClick = { viewModel.saveResult(context) }) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = PinkAccent)
                }
            }
        }

        // Image
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
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
                PickPhotoPrompt(
                    onCamera = { cameraLauncher.launch(null) },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
            }

            if (uiState.isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinkAccent)
                }
            }

            // Before/After label
            if (uiState.originalBitmap != null) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = if (uiState.isBeforeMode) "BEFORE" else "AFTER",
                        color = PinkAccent,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Zone selector
        Row(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BodyZone.values().forEach { zone ->
                val isSelected = uiState.selectedZone == zone
                Text(
                    text = zone.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) PinkAccent else SurfaceVariant)
                        .clickable { viewModel.selectZone(zone) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        // Sliders
        Column(
            modifier = Modifier.fillMaxWidth().background(SurfaceVariant)
                .padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (uiState.selectedZone) {
                BodyZone.BODY -> IntensitySlider("Slim Body", uiState.bodySettings.slimBody) { viewModel.updateSlimBody(it) }
                BodyZone.WAIST -> IntensitySlider("Slim Waist", uiState.bodySettings.slimWaist) { viewModel.updateSlimWaist(it) }
                BodyZone.LEGS -> IntensitySlider("Leg Lengthening", uiState.bodySettings.legLengthening) { viewModel.updateLegLengthening(it) }
                BodyZone.BUST -> IntensitySlider("Bust Enhance", uiState.bodySettings.bustEnhance) { viewModel.updateBustEnhance(it) }
                BodyZone.HIPS -> IntensitySlider("Hips Enhance", uiState.bodySettings.hipEnhance) { viewModel.updateHipEnhance(it) }
                BodyZone.SHOULDERS -> IntensitySlider("Shoulder Reshape", uiState.bodySettings.shoulderReshape) { viewModel.updateShoulderReshape(it) }
            }
        }
    }
}
