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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.presentation.components.*
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    val motionEnabled = rememberNasMotionEnabled()

    NasScaffold(
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
                NasEmptyState(
                    icon = Icons.Default.Error,
                    title = "加载失败",
                    message = state.message,
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
                    onRetry = { viewModel.retryTask(); onBack() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("删除任务") },
            text = { Text("确定要删除这个任务吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask()
                        showDeleteDialog = false
                        onDeleted()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Basic info card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .nasAnimateContentSize(motionEnabled),
                shape = NasCardShape,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "基本信息",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        NasStatusBadge(
                            text = task.status.statusLabel(),
                            positive = task.status !in setOf("failed", "cancelled")
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

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
                    shape = NasCardShape,
                    colors = nasCardColors(),
                    border = nasCardBorder(),
                    elevation = nasCardElevation()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("上传进度", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))

                        val progress = task.progressFraction()
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${task.progressBytes / 1024 / 1024}MB / ${task.totalBytes / 1024 / 1024}MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
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
                    shape = NasCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    elevation = nasCardElevation()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (task.status == "failed") "错误信息" else "警告信息",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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
                shape = NasCardShape,
                colors = nasCardColors(),
                border = nasCardBorder(),
                elevation = nasCardElevation()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("文件列表", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

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
                            Spacer(Modifier.width(8.dp))
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
                        Spacer(Modifier.height(8.dp))
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (task.status in setOf("failed", "cancelled")) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
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
                    Spacer(Modifier.width(8.dp))
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
            .padding(vertical = 4.dp),
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
            .padding(vertical = 4.dp, horizontal = (file.depth * 16).dp),
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
        Spacer(Modifier.width(8.dp))
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

private fun String.statusLabel(): String {
    return when (this) {
        "waiting" -> "等待"
        "running" -> "运行中"
        "paused" -> "已暂停"
        "completed" -> "完成"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> this
    }
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
