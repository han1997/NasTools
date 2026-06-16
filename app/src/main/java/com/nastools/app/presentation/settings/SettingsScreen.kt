package com.nastools.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nastools.app.presentation.components.NasCardShape
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasStatusBadge
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val motionEnabled = rememberNasMotionEnabled()

    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = "设置",
                subtitle = "权限和应用信息",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .nasAnimateContentSize(motionEnabled),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 系统权限卡片
            Card(
                shape = NasCardShape,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("系统权限", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("通知权限") },
                        supportingContent = { Text("允许显示后台上传进度") },
                        leadingContent = { Icon(Icons.Default.Notifications, null) },
                        trailingContent = {
                            val hasPermission = PermissionHelper.hasNotificationPermission(context)
                            NasStatusBadge(text = if (hasPermission) "已授权" else "未授权", positive = hasPermission)
                        }
                    )

                    Spacer(Modifier.height(4.dp))

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("电池优化") },
                        supportingContent = { Text("防止系统冻结后台上传") },
                        leadingContent = { Icon(Icons.Default.BatterySaver, null) },
                        trailingContent = {
                            val isIgnoring = PermissionHelper.isIgnoringBatteryOptimization(context)
                            NasStatusBadge(text = if (isIgnoring) "已忽略" else "优化中", positive = isIgnoring)
                        }
                    )
                }
            }

            // 关于卡片
            Card(
                shape = NasCardShape,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("关于", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("版本") },
                        supportingContent = { Text("0.1.0 (Compose 版)") }
                    )
                }
            }
        }
    }
}
