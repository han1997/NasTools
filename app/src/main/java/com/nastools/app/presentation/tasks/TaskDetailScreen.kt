package com.nastools.app.presentation.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.presentation.components.*
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val motionEnabled = rememberNasMotionEnabled()

    LaunchedEffect(feedback.errorMessage ?: feedback.message) {
        val message = feedback.errorMessage ?: feedback.message
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedback()
        }
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NasTopAppBar(
                title = "任务详情",
                subtitle = when (uiState) {
                    is TaskDetailUiState.Success -> (uiState as TaskDetailUiState.Success).task.title
                    else -> null
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is TaskDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TaskDetailUiState.Error -> {
                NasErrorState(
                    icon = Icons.Default.Error,
                    title = "加载失败",
                    message = state.message,
                    onRetry = viewModel::reload,
                    modifier = Modifier.padding(padding)
                )
            }
            is TaskDetailUiState.Success -> {
                TaskDetailContent(
                    task = state.task,
                    configName = state.configName,
                    files = state.files,
                    sourceDeleted = state.sourceDeleted,
                    motionEnabled = motionEnabled,
                    onDelete = { showDeleteDialog = true },
                    onRetry = { viewModel.retryTask(onRetried = onBack) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (showDeleteDialog) {
        NasConfirmDialog(
            title = "删除任务",
            message = "确定要删除这个任务吗？此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteTask(onDeleted)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
            icon = Icons.Default.Delete
        )
    }
}

@Composable
private fun TaskDetailContent(
    task: TaskEntity,
    configName: String?,
    files: List<FileItem>,
    sourceDeleted: Boolean,
    motionEnabled: Boolean,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(NasSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
    ) {
        // Basic info card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .nasAnimateContentSize(motionEnabled),
                shape = NasShape.Card,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "基本信息",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        NasStatusBadge(
                            text = task.status.statusLabel(),
                            tone = task.status.statusTone()
                        )
                    }
                    Spacer(Modifier.height(NasSpacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(NasSpacing.md))

                    InfoRow("任务 ID", task.id)
                    InfoRow("NAS 配置", configName ?: "未知")
                    InfoRow("创建时间", task.createdAt.formatTimestamp())
                    InfoRow("更新时间", task.updatedAt.formatTimestamp())
                    if (task.retryCount > 0) {
                        InfoRow("重试次数", task.retryCount.toString())
                    }
                }
            }
        }

        // Progress card
        if (task.status in setOf("waiting", "running", "paused", "completed")) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nasAnimateContentSize(motionEnabled),
                    shape = NasShape.Card,
                    colors = nasCardColors(),
                    border = nasCardBorder(),
                    elevation = nasCardElevation()
                ) {
                    Column(modifier = Modifier.padding(NasSpacing.lg)) {
                        Text("上传进度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(NasSpacing.md))

                        val progress = remember(task.progressBytes, task.totalBytes) {
                            task.progressFraction()
                        }
                        val progressMB = remember(task.progressBytes) { task.progressBytes / 1024 / 1024 }
                        val totalMB = remember(task.totalBytes) { task.totalBytes / 1024 / 1024 }
                        val progressPercent = remember(progress) { (progress * 100).toInt() }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(Modifier.height(NasSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${progressMB}MB / ${totalMB}MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${progressPercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Error card
        if (task.errorMessage != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nasAnimateContentSize(motionEnabled),
                    shape = NasShape.Card,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    elevation = nasCardElevation()
                ) {
                    Column(modifier = Modifier.padding(NasSpacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(NasSpacing.sm))
                            Text(
                                if (task.status == "failed") "错误信息" else "警告信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(NasSpacing.sm))
                        Text(
                            task.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Files card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .nasAnimateContentSize(motionEnabled),
                shape = NasShape.Card,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(NasSpacing.lg)) {
                    Text("文件列表", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(NasSpacing.md))

                    if (sourceDeleted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(NasSpacing.sm))
                            Text(
                                "文件已删除，无法查看详情",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (files.isEmpty()) {
                        Text(
                            "无文件信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(NasSpacing.sm))
                        files.forEach { file ->
                            FileItemRow(file)
                        }
                    }
                }
            }
        }

        // Action buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NasSpacing.md)
            ) {
                if (task.status in setOf("failed", "cancelled")) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(NasSpacing.sm))
                        Text("重试")
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(NasSpacing.sm))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NasSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FileItemRow(file: FileItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NasSpacing.xs, horizontal = NasSpacing.lg * file.depth),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (file.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(NasSpacing.sm))
        Text(
            file.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!file.isDirectory) {
            Text(
                formatFileSize(file.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun TaskEntity.progressFraction(): Float {
    if (totalBytes <= 0) return if (status == "completed") 1f else 0f
    return (progressBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

private fun Long.formatTimestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(this))
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024}MB"
        else -> "${bytes / 1024 / 1024 / 1024}GB"
    }
}

// ---------------------------------------------------------------------------
// @Preview：渲染无状态的 TaskDetailContent，不依赖 ViewModel（AC15）。
// ---------------------------------------------------------------------------

@Preview(name = "任务详情 · 浅色", showBackground = true)
@Composable
private fun TaskDetailLightPreview() {
    NasToolsTheme(darkTheme = false) { TaskDetailPreviewContent() }
}

@Preview(name = "任务详情 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun TaskDetailDarkPreview() {
    NasToolsTheme(darkTheme = true) { TaskDetailPreviewContent() }
}

@Composable
private fun TaskDetailPreviewContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        TaskDetailContent(
            task = TaskEntity(
                id = "preview",
                moduleId = "preview",
                type = "upload",
                status = "paused",
                progressBytes = 512L * 1024 * 1024,
                totalBytes = 1024L * 1024 * 1024,
                title = "示例上传任务",
                payloadJson = "{}"
            ),
            configName = "家庭 NAS",
            files = listOf(
                FileItem(name = "照片", sizeBytes = 0, isDirectory = true, depth = 0),
                FileItem(name = "IMG_0001.jpg", sizeBytes = 1024L * 512, isDirectory = false, depth = 1)
            ),
            sourceDeleted = false,
            motionEnabled = false,
            onDelete = {},
            onRetry = {}
        )
    }
}
