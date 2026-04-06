package com.beautycamera.ui.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.beautycamera.data.repository.PhotoRepository
import com.beautycamera.domain.model.PhotoModel
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val photos: List<PhotoModel> = emptyList(),
    val selectedPhoto: PhotoModel? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadPhotos(context: Context, showAllPhotos: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val source = if (showAllPhotos) photoRepository.getDevicePhotos(context)
                         else photoRepository.getPhotos(context)
            source.collect { photos ->
                _uiState.value = _uiState.value.copy(photos = photos, isLoading = false)
            }
        }
    }

    fun selectPhoto(photo: PhotoModel?) {
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
    }

    fun deletePhoto(photoId: Long, context: Context) {
        viewModelScope.launch {
            photoRepository.deletePhoto(photoId, context)
            _uiState.value = _uiState.value.copy(
                photos = _uiState.value.photos.filter { it.id != photoId },
                selectedPhoto = null
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onEditPhoto: (String) -> Unit,
    onBodyEdit: (String) -> Unit = {},
    onStickerEdit: (String) -> Unit = {},
    showAllPhotos: Boolean = false,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val readPermission = rememberPermissionState(
        if (android.os.Build.VERSION.SDK_INT >= 33)
            android.Manifest.permission.READ_MEDIA_IMAGES
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    LaunchedEffect(readPermission.status.isGranted) {
        if (readPermission.status.isGranted) viewModel.loadPhotos(context, showAllPhotos)
        else readPermission.launchPermissionRequest()
    }

    Column(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (showAllPhotos) "Device Gallery" else "Edited Photos",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
            Text("${uiState.photos.size} photos", color = Color.Gray, fontSize = 13.sp)
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinkAccent)
            }
        } else if (uiState.photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No photos yet", color = Color.Gray, fontSize = 16.sp)
                    Text(
                        if (showAllPhotos) "No photos found on device" else "Edit a photo to get started!",
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.photos) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { viewModel.selectPhoto(photo) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }

    // Full screen view
    uiState.selectedPhoto?.let { photo ->
        Dialog(onDismissRequest = { viewModel.selectPhoto(null) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { viewModel.selectPhoto(null) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = {
                        onEditPhoto(Uri.encode(photo.uri))
                        viewModel.selectPhoto(null)
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = PinkAccent)
                            Text("Edit", color = PinkAccent, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        onBodyEdit(Uri.encode(photo.uri))
                        viewModel.selectPhoto(null)
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = "Body", tint = PinkAccent)
                            Text("Body", color = PinkAccent, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        onStickerEdit(Uri.encode(photo.uri))
                        viewModel.selectPhoto(null)
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEmotions, contentDescription = "Sticker", tint = PinkAccent)
                            Text("Sticker", color = PinkAccent, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(photo.uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            Text("Share", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = { viewModel.deletePhoto(photo.id, context) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            Text("Delete", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
