package com.beautycamera.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.beautycamera.ui.ai.AIGeneratorScreen
import com.beautycamera.ui.body.BodyEditorScreen
import com.beautycamera.ui.camera.CameraScreen
import com.beautycamera.ui.face.FaceEditorScreen
import com.beautycamera.ui.filter.FilterScreen
import com.beautycamera.ui.gallery.GalleryScreen
import com.beautycamera.ui.home.HomeScreen
import com.beautycamera.ui.splash.SplashScreen
import com.beautycamera.ui.sticker.StickerScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Camera : Screen("camera")
    object FaceEditor : Screen("face_editor/{imagePath}") {
        fun createRoute(imagePath: String) = "face_editor/$imagePath"
    }
    object BodyEditor : Screen("body_editor/{imagePath}") {
        fun createRoute(imagePath: String) = "body_editor/$imagePath"
    }
    object Filter : Screen("filter/{imagePath}") {
        fun createRoute(imagePath: String) = "filter/$imagePath"
    }
    object Sticker : Screen("sticker/{imagePath}") {
        fun createRoute(imagePath: String) = "sticker/$imagePath"
    }
    object AIGenerator : Screen("ai_generator")
    object Gallery : Screen("gallery")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                onNavigateToFace = { imagePath ->
                    navController.navigate(Screen.FaceEditor.createRoute(imagePath))
                },
                onNavigateToBody = { imagePath ->
                    navController.navigate(Screen.BodyEditor.createRoute(imagePath))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.FaceEditor.route) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            FaceEditorScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBody = { path ->
                    navController.navigate(Screen.BodyEditor.createRoute(Uri.encode(path)))
                },
                onNavigateToSticker = { path ->
                    navController.navigate(Screen.Sticker.createRoute(Uri.encode(path)))
                }
            )
        }
        composable(Screen.BodyEditor.route) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            BodyEditorScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Filter.route) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            FilterScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Sticker.route) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            StickerScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AIGenerator.route) {
            AIGeneratorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Gallery.route) {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() },
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
