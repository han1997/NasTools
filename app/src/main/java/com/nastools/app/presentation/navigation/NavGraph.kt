package com.nastools.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nastools.app.presentation.browser.BrowserScreen
import com.nastools.app.presentation.config.ConfigEditScreen
import com.nastools.app.presentation.home.HomeScreen
import com.nastools.app.presentation.presets.PresetEditScreen
import com.nastools.app.presentation.presets.PresetListScreen
import com.nastools.app.presentation.settings.SettingsScreen
import com.nastools.app.presentation.tasks.TasksScreen

@Composable
fun NasToolsNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToConfig = { navController.navigate("config/$it") },
                onNavigateToNewConfig = { navController.navigate("config/new") },
                onNavigateToBrowser = { navController.navigate("browser/$it") },
                onNavigateToTasks = { navController.navigate("tasks") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPresets = { navController.navigate("presets") }
            )
        }
        composable("tasks") {
            TasksScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("presets") {
            PresetListScreen(
                onBack = { navController.popBackStack() },
                onCreatePreset = { navController.navigate("presets/edit/new") },
                onEditPreset = { navController.navigate("presets/edit/$it") }
            )
        }
        composable("presets/edit/new") {
            PresetEditScreen(
                presetId = null,
                onBack = { navController.popBackStack() }
            )
        }
        composable("presets/edit/{id}") { backStackEntry ->
            PresetEditScreen(
                presetId = backStackEntry.arguments?.getString("id"),
                onBack = { navController.popBackStack() }
            )
        }
        composable("config/new") {
            ConfigEditScreen(
                configId = null,
                onBack = { navController.popBackStack() }
            )
        }
        composable("config/{id}") { backStackEntry ->
            ConfigEditScreen(
                configId = backStackEntry.arguments?.getString("id"),
                onBack = { navController.popBackStack() }
            )
        }
        composable("browser/{id}") { backStackEntry ->
            BrowserScreen(
                configId = backStackEntry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
