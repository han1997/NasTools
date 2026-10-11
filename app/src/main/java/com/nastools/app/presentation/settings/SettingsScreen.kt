package com.nastools.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.nastools.app.BuildConfig
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
fun SettingsScreen(
    onNavigateToPresets: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val motionEnabled = rememberNasMotionEnabled()

    NasScaffold(
        bottomBar = bottomBar,
        topBar = {
            // 主标签页：无返回箭头（Concept 01 子页面才显示返回）
            NasTopAppBar(
                title = "设置",
                subtitle = "权限和应用信息"
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
            // 品牌头（Concept 01）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = NasShape.Field),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "n.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(modifier = Modifier.padding(start = NasSpacing.lg)) {
                    Text("NasTools", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "你的私人文件空间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 工作流组
            Text(
                "工作流",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                elevation = nasCardElevation(),
                border = nasCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToPresets)
                        .padding(NasSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = NasSpacing.md)
                    ) {
                        Text("上传预设", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "管理常用来源与上传规则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "打开上传预设",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 后台传输组（真实系统权限，Concept 01：未授权用 Warning 琥珀）
            Text(
                "后台传输",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                elevation = nasCardElevation(),
                border = nasCardBorder()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = NasSpacing.md)
                        ) {
                            Text("通知权限", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "显示后台上传进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val hasPermission = PermissionHelper.hasNotificationPermission(context)
                        NasStatusBadge(
                            text = if (hasPermission) "已允许" else "未允许",
                            tone = if (hasPermission) NasStatusTone.Success else NasStatusTone.Warning
                        )
                    }
                    Spacer(Modifier.height(NasSpacing.lg))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(NasSpacing.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatterySaver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = NasSpacing.md)
                        ) {
                            Text("电池优化", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "避免后台任务被系统中断",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val isIgnoring = PermissionHelper.isIgnoringBatteryOptimization(context)
                        NasStatusBadge(
                            text = if (isIgnoring) "不受限制" else "待设置",
                            tone = if (isIgnoring) NasStatusTone.Success else NasStatusTone.Warning
                        )
                    }
                }
            }

            // 数据组(Concept 01 v1.3:备份与恢复入口)
            Text(
                "数据",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                elevation = nasCardElevation(),
                border = nasCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToBackup)
                        .padding(NasSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = NasSpacing.md)
                    ) {
                        Text("备份与恢复", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "把连接与上传预设备份到 WebDAV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "打开备份与恢复",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于组
            Text(
                "关于",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                shape = NasShape.Card,
                colors = nasCardColors(),
                elevation = nasCardElevation(),
                border = nasCardBorder()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Text("版本", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // R3:版本号来自构建产物 versionName;debug 自动带 -debug 后缀
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
