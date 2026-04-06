package com.beautycamera.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.beautycamera.ui.ai.AIGeneratorScreen
import com.beautycamera.ui.camera.CameraScreen
import com.beautycamera.ui.gallery.GalleryScreen
import com.beautycamera.ui.gallery.GalleryViewModel
import com.beautycamera.ui.navigation.Screen
import com.beautycamera.ui.theme.BackgroundDark
import com.beautycamera.ui.theme.PinkAccent
import com.beautycamera.ui.theme.SurfaceDark

@Composable
fun HomeScreen(navController: NavHostController) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Triple("Camera", Icons.Default.CameraAlt, 0),
        Triple("Edit", Icons.Default.Edit, 1),
        Triple("AI", Icons.Default.AutoFixHigh, 2),
        Triple("Gallery", Icons.Default.PhotoLibrary, 3)
    )

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PinkAccent,
                            selectedTextColor = PinkAccent,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = PinkAccent.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> CameraScreen(
                modifier = Modifier.padding(paddingValues),
                onNavigateToFace = { imagePath ->
                    navController.navigate(Screen.FaceEditor.createRoute(imagePath))
                },
                onNavigateToBody = { imagePath ->
                    navController.navigate(Screen.BodyEditor.createRoute(imagePath))
                },
                onNavigateBack = {}
            )
            1 -> GalleryScreen(
                modifier = Modifier.padding(paddingValues),
                onNavigateBack = {},
                showAllPhotos = false,
                viewModel = hiltViewModel(key = "edit_gallery"),
                onEditPhoto = { imagePath ->
                    navController.navigate(Screen.FaceEditor.createRoute(imagePath))
                },
                onBodyEdit = { imagePath ->
                    navController.navigate(Screen.BodyEditor.createRoute(imagePath))
                },
                onStickerEdit = { imagePath ->
                    navController.navigate(Screen.Sticker.createRoute(imagePath))
                }
            )
            2 -> AIGeneratorScreen(
                modifier = Modifier.padding(paddingValues),
                onNavigateBack = {}
            )
            3 -> GalleryScreen(
                modifier = Modifier.padding(paddingValues),
                onNavigateBack = {},
                showAllPhotos = true,
                viewModel = hiltViewModel(key = "device_gallery"),
                onEditPhoto = { imagePath ->
                    navController.navigate(Screen.FaceEditor.createRoute(imagePath))
                },
                onBodyEdit = { imagePath ->
                    navController.navigate(Screen.BodyEditor.createRoute(imagePath))
                },
                onStickerEdit = { imagePath ->
                    navController.navigate(Screen.Sticker.createRoute(imagePath))
                }
            )
        }
    }
}
