package com.yuu18id.mangatranslator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuu18id.mangatranslator.ui.home.HomeScreen
import com.yuu18id.mangatranslator.ui.translate.TranslateScreen
import com.yuu18id.mangatranslator.ui.reader.ReaderScreen
import com.yuu18id.mangatranslator.ui.gallery.GalleryScreen
import com.yuu18id.mangatranslator.ui.settings.SettingsScreen
import com.yuu18id.mangatranslator.ui.batch.BatchScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Translate : Screen("translate?imageUri={imageUri}") {
        fun createRoute(imageUri: String?) = "translate?imageUri=${imageUri ?: ""}"
    }
    object Batch : Screen("batch")
    object Reader : Screen("reader/{translationId}") {
        fun createRoute(translationId: String) = "reader/$translationId"
    }
    object Gallery : Screen("gallery")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(initialImageUri: android.net.Uri? = null) {
    val navController = rememberNavController()
    val startDestination = if (initialImageUri != null) {
        Screen.Translate.createRoute(initialImageUri.toString())
    } else {
        Screen.Home.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToTranslate = { uri -> navController.navigate(Screen.Translate.createRoute(uri)) },
                onNavigateToBatch = { navController.navigate(Screen.Batch.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToReader = { id -> navController.navigate(Screen.Reader.createRoute(id)) },
                onNavigateToGallery = { navController.navigate(Screen.Gallery.route) }
            )
        }
        composable(
            route = Screen.Translate.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri")
            TranslateScreen(
                imageUri = imageUri,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onOpenInReader = { id -> navController.navigate(Screen.Reader.createRoute(id.toString())) }
            )
        }
        composable(Screen.Batch.route) {
            BatchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChapterReader = { ids ->
                    navController.navigate(Screen.Reader.createRoute(ids.joinToString(",")))
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(navArgument("translationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val translationId = backStackEntry.arguments?.getString("translationId") ?: ""
            ReaderScreen(
                translationId = translationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTranslate = { uri -> navController.navigate(Screen.Translate.createRoute(uri)) }
            )
        }
        composable(Screen.Gallery.route) {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { id -> navController.navigate(Screen.Reader.createRoute(id)) },
                onNavigateToTranslate = { uri -> navController.navigate(Screen.Translate.createRoute(uri)) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
