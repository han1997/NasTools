package com.nastools.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nastools.app.presentation.home.HomeScreen
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
    }
}
