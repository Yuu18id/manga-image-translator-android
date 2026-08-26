package com.yuu18id.mangatranslator.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuu18id.mangatranslator.ui.batch.BatchScreen
import com.yuu18id.mangatranslator.ui.gallery.GalleryScreen
import com.yuu18id.mangatranslator.ui.home.HomeScreen
import com.yuu18id.mangatranslator.ui.reader.ReaderScreen
import com.yuu18id.mangatranslator.ui.settings.SettingsScreen
import com.yuu18id.mangatranslator.ui.translate.TranslateScreen

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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevelDestination = currentRoute in listOf(
        Screen.Home.route,
        Screen.Gallery.route
    )

    val startDestination = if (initialImageUri != null) {
        Screen.Translate.createRoute(initialImageUri.toString())
    } else {
        Screen.Home.route
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Translate, contentDescription = "Translate") },
                        label = { Text("Translate") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri") },
                        label = { Text("Galeri") },
                        selected = currentRoute == Screen.Gallery.route,
                        onClick = {
                            if (currentRoute != Screen.Gallery.route) {
                                navController.navigate(Screen.Gallery.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToTranslate = { uri -> navController.navigate(Screen.Translate.createRoute(uri)) },
                    onNavigateToBatch = { navController.navigate(Screen.Batch.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToReader = { id -> navController.navigate(Screen.Reader.createRoute(id)) },
                    onNavigateToGallery = {
                        navController.navigate(Screen.Gallery.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                    onNavigateBack = null,
                    onNavigateToReader = { id -> navController.navigate(Screen.Reader.createRoute(id)) },
                    onNavigateToTranslate = { uri -> navController.navigate(Screen.Translate.createRoute(uri)) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
