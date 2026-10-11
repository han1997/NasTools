package com.nastools.app.presentation.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nastools.app.presentation.backup.BackupScreen
import com.nastools.app.presentation.browser.FilesScreen
import com.nastools.app.presentation.components.NasBottomBar
import com.nastools.app.presentation.components.NasBottomNavItem
import com.nastools.app.presentation.config.ConfigEditScreen
import com.nastools.app.presentation.home.HomeScreen
import com.nastools.app.presentation.media.MediaViewerScreen
import com.nastools.app.presentation.presets.PresetEditScreen
import com.nastools.app.presentation.presets.PresetListScreen
import com.nastools.app.presentation.settings.SettingsScreen
import com.nastools.app.presentation.tasks.TaskDetailScreen
import com.nastools.app.presentation.tasks.TasksScreen

/** 四个主入口（Concept 01）：底部导航仅在这些路由出现 */
private val bottomNavItems = listOf(
    NasBottomNavItem("home", "连接", Icons.Default.Dns),
    NasBottomNavItem("files", "文件", Icons.Default.Folder),
    NasBottomNavItem("tasks", "传输", Icons.Default.CloudUpload),
    NasBottomNavItem("settings", "设置", Icons.Default.Settings)
)

@Composable
fun NasToolsNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = "home",
        // 默认转场带长延迟，标签切换时会闪出窗口背景；改为快速交叉淡化
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToConfig = { navController.navigate("config/$it") },
                onNavigateToNewConfig = { navController.navigate("config/new") },
                onOpenFiles = { navController.navigateTab("files") },
                onOpenTasks = { navController.navigateTab("tasks") },
                onOpenTaskDetail = { navController.navigate("tasks/$it") },
                onNavigateToPresets = { navController.navigate("presets") },
                bottomBar = { NasBottomBar(bottomNavItems, "home") { navController.navigateTab(it) } }
            )
        }
        composable("files") {
            FilesScreen(
                onBack = { navController.popBackStack() },
                onNewConnection = { navController.navigate("config/new") },
                onOpenMedia = { configId, path ->
                    navController.navigate(mediaViewerRoute(configId, path))
                },
                bottomBar = { NasBottomBar(bottomNavItems, "files") { navController.navigateTab(it) } }
            )
        }
        composable("tasks") {
            TasksScreen(
                onNavigateToDetail = { navController.navigate("tasks/$it") },
                bottomBar = { NasBottomBar(bottomNavItems, "tasks") { navController.navigateTab(it) } }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateToPresets = { navController.navigate("presets") },
                onNavigateToBackup = { navController.navigate("backup") },
                bottomBar = { NasBottomBar(bottomNavItems, "settings") { navController.navigateTab(it) } }
            )
        }
        // 子页面:不传 bottomBar(Concept 01)
        composable("backup") {
            BackupScreen(
                onBack = { navController.popBackStack() },
                onNewConnection = { navController.navigate("config/new") }
            )
        }
        composable("tasks/{taskId}") {
            TaskDetailScreen(
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
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
        composable(
            route = MEDIA_VIEWER_ROUTE,
            arguments = listOf(
                navArgument("configId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = "/"
                }
            )
        ) {
            MediaViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * 主入口切换：singleTop + popUpTo 保存/恢复状态，避免重复堆栈，
 * 并保留各标签的目录、列表位置等状态（用户已确认的导航规则）。
 */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val MEDIA_VIEWER_ROUTE = "media/{configId}?path={path}"

private fun mediaViewerRoute(configId: String, remotePath: String): String {
    return "media/${Uri.encode(configId)}?path=${Uri.encode(remotePath)}"
}
