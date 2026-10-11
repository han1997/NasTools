package com.nastools.app.presentation.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.presentation.components.NasConfirmDialog
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasErrorState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasMotion
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.presentation.theme.NasElevation
import com.nastools.app.presentation.theme.NasListPadding
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToConfig: (String) -> Unit = {},
    onNavigateToNewConfig: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenTaskDetail: (String) -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val motionEnabled = rememberNasMotionEnabled()
    var pendingDeleteConfig by remember { mutableStateOf<NasConfigEntity?>(null) }

    NasScaffold(
        bottomBar = bottomBar,
        topBar = {
            NasTopAppBar(
                title = "NasTools",
                subtitle = "你的私人文件空间",
                actions = {
                    IconButton(onClick = onNavigateToNewConfig) {
                        Icon(Icons.Default.Add, "新建连接")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.activeTasks.isNotEmpty()) {
                ActiveTaskBanner(
                    task = uiState.activeTasks.first(),
                    count = uiState.activeTasks.size,
                    onClick = { onOpenTaskDetail(uiState.activeTasks.first().id) }
                )
            }

            when {
                uiState.loadErrorMessage != null -> {
                    NasErrorState(
                        title = "加载首页失败",
                        message = uiState.loadErrorMessage ?: "无法加载首页",
                        onRetry = viewModel::reload,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                uiState.isLoading -> {
                    // 显示骨架屏加载状态以提升感知速度
                    LoadingSkeleton(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    ConfigList(
                        motionEnabled = motionEnabled,
                        configs = uiState.configs,
                        onConfigClick = { configId ->
                            viewModel.selectConnection(configId)
                            onOpenFiles()
                        },
                        onManageClick = onNavigateToConfig,
                        onCreateClick = onNavigateToNewConfig,
                        onConfigDelete = { pendingDeleteConfig = it },
                        onOpenPresets = onNavigateToPresets
                    )
                }
            }
        }
    }

    pendingDeleteConfig?.let { config ->
        NasConfirmDialog(
            title = "删除连接",
            message = "确定要删除连接「${config.name}」吗？将同时删除此连接相关的上传预设，NAS 上的文件不受影响，此操作不可撤销。",
            confirmText = "删除",
            onConfirm = {
                viewModel.delete(config.id)
                pendingDeleteConfig = null
            },
            onDismiss = { pendingDeleteConfig = null }
        )
    }
}

@Composable
private fun ActiveTaskBanner(
    task: TaskEntity,
    count: Int,
    onClick: () -> Unit
) {
    // 原型：accent 绿横幅；进度与字节数来自真实任务数据，未知总量显示已传大小
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = NasShape.Card,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(modifier = Modifier.padding(NasSpacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (count > 1) "$count 个任务进行中" else "1 个任务进行中",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = "查看任务详情")
            }
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = NasSpacing.xs, bottom = NasSpacing.sm)
            )
            val progress = remember(task.progressBytes, task.totalBytes) {
                if (task.totalBytes > 0) {
                    (task.progressBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
                } else null
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NasSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatBytes(task.progressBytes, task.totalBytes),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                )
                Text(
                    "${formatBytes(task.progressBytes, null)} 已上传 · 总计大小未知",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = NasSpacing.xs)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long, total: Long?): String {
    fun fmt(b: Long): String = when {
        b >= 1_000_000_000 -> String.format("%.2f GB", b / 1_000_000_000.0)
        b >= 1_000_000 -> String.format("%.1f MB", b / 1_000_000.0)
        b >= 1_000 -> String.format("%.0f KB", b / 1_000.0)
        else -> "$b B"
    }
    return if (total != null && total > 0) "${fmt(bytes)} / ${fmt(total)}" else fmt(bytes)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigList(
    configs: List<NasConfigEntity>,
    onConfigClick: (String) -> Unit,
    onManageClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onConfigDelete: (NasConfigEntity) -> Unit,
    onOpenPresets: () -> Unit,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = rememberNasMotionEnabled()
) {
    if (configs.isEmpty()) {
        NasEmptyState(
            modifier = modifier,
            icon = Icons.Default.Dns,
            title = "还没有连接配置",
            message = "添加 WebDAV 连接后即可浏览和上传文件",
            action = {
                Button(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(NasSpacing.sm))
                    Text("新建连接")
                }
            }
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = NasListPadding,
            verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
        ) {
            item {
                SectionHeader(
                    title = "我的连接",
                    count = configs.size,
                    detail = "WebDAV"
                )
            }
            items(configs, key = { it.id }) { config ->
                ConfigCard(
                    config = config,
                    modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                    onClick = { onConfigClick(config.id) },
                    onManageClick = { onManageClick(config.id) },
                    onDeleteClick = { onConfigDelete(config) }
                )
            }
            item {
                SectionHeader(title = "常用工作流", detail = null)
                Spacer(Modifier.height(NasSpacing.sm))
                PresetShortcut(onClick = onOpenPresets)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, detail: String?, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = NasSpacing.xs, bottom = NasSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (count != null) {
                Spacer(Modifier.width(NasSpacing.xs))
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfigCard(
    config: NasConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onManageClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = NasShape.Card,
        colors = nasCardColors(),
        elevation = nasCardElevation(),
        border = nasCardBorder()
    ) {
        Column(modifier = Modifier.padding(NasSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NasIconContainer(Icons.Default.Storage, null)
                Spacer(Modifier.width(NasSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(config.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        hostOf(config.baseUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "管理连接")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑连接") },
                            onClick = {
                                showMenu = false
                                onManageClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除连接") },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = NasSpacing.md),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "已配置认证",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClick) {
                    Text("浏览文件")
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun hostOf(baseUrl: String): String =
    android.net.Uri.parse(baseUrl).host ?: baseUrl

@Composable
private fun PresetShortcut(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = NasShape.Field,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NasIconContainer(Icons.Default.Bookmark, null, modifier = Modifier, selected = false)
            Spacer(Modifier.width(NasSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("上传预设", style = MaterialTheme.typography.titleSmall)
                Text(
                    "照片备份、工作归档，一次设置",
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
}

@Composable
private fun LoadingSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = NasListPadding,
        verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
    ) {
        item {
            SectionHeader(
                title = "我的连接",
                count = null,
                detail = "加载中..."
            )
        }
        items(3) {
            SkeletonCard()
        }
    }
}

@Composable
private fun SkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NasShape.Card,
        colors = nasCardColors(),
        elevation = nasCardElevation()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(NasSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon placeholder
            Surface(
                modifier = Modifier.size(40.dp).graphicsLayer { this.alpha = alpha },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {}
            Spacer(Modifier.width(NasSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Surface(
                    modifier = Modifier.fillMaxWidth(0.6f).height(20.dp).graphicsLayer { this.alpha = alpha },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
                Spacer(Modifier.height(NasSpacing.sm))
                // URL placeholder
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).graphicsLayer { this.alpha = alpha * 0.8f },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {}
            }
            Spacer(Modifier.width(48.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// @Preview：渲染无状态的 ActiveTaskBanner / ConfigCard，不依赖 ViewModel（AC15）。
// ---------------------------------------------------------------------------

@Preview(name = "主页 · 浅色", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    NasToolsTheme(darkTheme = false) { HomePreviewContent() }
}

@Preview(name = "主页 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun HomeScreenDarkPreview() {
    NasToolsTheme(darkTheme = true) { HomePreviewContent() }
}

@Composable
private fun HomePreviewContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
        ) {
            ActiveTaskBanner(
                task = TaskEntity(
                    id = "preview-task",
                    moduleId = "upload",
                    type = "file",
                    nasConfigId = "preview",
                    status = "running",
                    progressBytes = 1_750_000_000,
                    totalBytes = 2_580_000_000,
                    title = "夏日山野 · 照片备份",
                    payloadJson = ""
                ),
                count = 2,
                onClick = {}
            )
            ConfigCard(config = previewConfig(), onClick = {}, onManageClick = {}, onDeleteClick = {})
        }
    }
}

private fun previewConfig() = NasConfigEntity(
    id = "preview",
    name = "家庭 NAS",
    type = "webdav",
    baseUrl = "https://nas.example.com/dav",
    username = "user",
    password = "secret",
    defaultRemotePath = "/photos"
)
