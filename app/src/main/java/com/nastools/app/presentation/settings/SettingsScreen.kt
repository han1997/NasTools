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
import androidx.compose.ui.tooling.preview.Preview
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasStatusBadge
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasStatusTone
import com.nastools.app.presentation.theme.NasToolsTheme
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
                .padding(NasSpacing.lg)
                .nasAnimateContentSize(motionEnabled),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.lg)
        ) {
            // 系统权限卡片
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(NasSpacing.sm))
                        Text("系统权限", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(NasSpacing.md))
                    HorizontalDivider()
                    Spacer(Modifier.height(NasSpacing.sm))

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("通知权限") },
                        supportingContent = { Text("允许显示后台上传进度") },
                        leadingContent = { Icon(Icons.Default.Notifications, null) },
                        trailingContent = {
                            val hasPermission = PermissionHelper.hasNotificationPermission(context)
                            NasStatusBadge(
                                text = if (hasPermission) "已授权" else "未授权",
                                tone = if (hasPermission) NasStatusTone.Success else NasStatusTone.Danger
                            )
                        }
                    )

                    Spacer(Modifier.height(NasSpacing.xs))

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text("电池优化") },
                        supportingContent = { Text("防止系统冻结后台上传") },
                        leadingContent = { Icon(Icons.Default.BatterySaver, null) },
                        trailingContent = {
                            val isIgnoring = PermissionHelper.isIgnoringBatteryOptimization(context)
                            NasStatusBadge(
                                text = if (isIgnoring) "已忽略" else "优化中",
                                tone = if (isIgnoring) NasStatusTone.Success else NasStatusTone.Danger
                            )
                        }
                    )
                }
            }

            // 关于卡片
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(NasSpacing.sm))
                        Text("关于", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(NasSpacing.md))
                    HorizontalDivider()
                    Spacer(Modifier.height(NasSpacing.sm))

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

// ---------------------------------------------------------------------------
// @Preview：SettingsScreen 无 ViewModel 依赖，直接渲染（AC15）。
// ---------------------------------------------------------------------------

@Preview(name = "设置 · 浅色", showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    NasToolsTheme(darkTheme = false) { SettingsScreen() }
}

@Preview(name = "设置 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun SettingsScreenDarkPreview() {
    NasToolsTheme(darkTheme = true) { SettingsScreen() }
}
