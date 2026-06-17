package com.nastools.app.presentation.tasks

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.presentation.components.NasCardShape
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasMotion
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasStatusBadge
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.nasMotionSpec
import com.nastools.app.presentation.components.rememberNasMotionEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val motionEnabled = rememberNasMotionEnabled()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    val canBatchDelete = selectedTab in setOf(1, 2) // Completed or Failed tabs

    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = "任务中心",
                subtitle = if (uiState.isSelectionMode) {
                    "已选 ${uiState.selectedTaskIds.size} 项"
                } else {
                    "查看上传、暂停和重试"
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSelectionMode) {
                            viewModel.exitSelectionMode()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (uiState.isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            if (uiState.isSelectionMode) "取消选择" else "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        // Selection mode actions
                        IconButton(
                            onClick = {
                                val currentTasks = when (selectedTab) {
                                    1 -> uiState.completedTasks
                                    2 -> uiState.failedTasks
                                    else -> emptyList()
                                }
                                viewModel.selectAll(currentTasks.map { it.id })
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, "全选")
                        }
                        IconButton(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = uiState.selectedTaskIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    } else if (canBatchDelete) {
                        // Default mode - show batch delete entry
                        IconButton(onClick = { viewModel.enterSelectionMode() }) {
                            Icon(Icons.Default.Checklist, "批量删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nasAnimateContentSize(motionEnabled)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; viewModel.exitSelectionMode() }, text = { Text("活跃") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; viewModel.exitSelectionMode() }, text = { Text("已完成") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2; viewModel.exitSelectionMode() }, text = { Text("失败") })
            }

            when (selectedTab) {
                0 -> TaskList(
                    tasks = uiState.activeTasks,
                    isSelectionMode = false,
                    selectedTaskIds = emptySet(),
                    onTaskClick = { onNavigateToDetail(it.id) },
                    onAction = { task, action ->
                        when (action) {
                            "pause" -> viewModel.pauseTask(task.id)
                            "resume" -> viewModel.resumeTask(task.id)
                            "cancel" -> viewModel.cancelTask(task.id)
                        }
                    },
                    motionEnabled = motionEnabled
                )
                1 -> TaskList(
                    tasks = uiState.completedTasks,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedTaskIds = uiState.selectedTaskIds,
                    onTaskClick = { if (!uiState.isSelectionMode) onNavigateToDetail(it.id) else viewModel.toggleTaskSelection(it.id) },
                    onAction = { task, action ->
                        if (action == "delete") viewModel.deleteTask(task.id)
                    },
                    motionEnabled = motionEnabled
                )
                2 -> TaskList(
                    tasks = uiState.failedTasks,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedTaskIds = uiState.selectedTaskIds,
                    onTaskClick = { if (!uiState.isSelectionMode) onNavigateToDetail(it.id) else viewModel.toggleTaskSelection(it.id) },
                    onAction = { task, action ->
                        when (action) {
                            "retry" -> viewModel.retryTask(task.id)
                            "delete" -> viewModel.deleteTask(task.id)
                        }
                    },
                    motionEnabled = motionEnabled
                )
            }
        }
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("批量删除") },
            text = { Text("确定要删除选中的 ${uiState.selectedTaskIds.size} 个任务吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete(uiState.selectedTaskIds)
                        showBatchDeleteDialog = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskList(
    tasks: List<TaskEntity>,
    isSelectionMode: Boolean,
    selectedTaskIds: Set<String>,
    motionEnabled: Boolean,
    onTaskClick: (TaskEntity) -> Unit,
    onAction: (TaskEntity, String) -> Unit
) {
    if (tasks.isEmpty()) {
        NasEmptyState(
            icon = Icons.Default.Task,
            title = "暂无任务",
            message = "上传任务会在这里显示进度和状态"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    isSelectionMode = isSelectionMode,
                    isSelected = task.id in selectedTaskIds,
                    modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                    onClick = { onTaskClick(task) },
                    onAction = { action -> onAction(task, action) }
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val motionEnabled = rememberNasMotionEnabled()

    // Memoize progress calculation to avoid recomputation on every recomposition
    val progress = remember(task.progressBytes, task.totalBytes) {
        task.progressFraction()
    }

    // Memoize MB conversions to avoid repeated division operations
    val progressMB = remember(task.progressBytes) { task.progressBytes / 1024 / 1024 }
    val totalMB = remember(task.totalBytes) { task.totalBytes / 1024 / 1024 }

    val iconScale = if (motionEnabled && task.status == "running") {
        val transition = rememberInfiniteTransition(label = "runningTaskPulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = NasMotion.EaseOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "runningTaskPulseScale"
        )
        value
    } else {
        1f
    }

    Card(
        modifier = modifier
            .nasAnimateContentSize(motionEnabled),
        shape = NasCardShape,
        colors = nasCardColors(),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            nasCardBorder()
        },
        elevation = nasCardElevation(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null, // Let card onClick handle the toggle
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            NasIconContainer(
                icon = if (task.status == "completed") Icons.Default.CheckCircle else Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
                selected = task.status == "running" || task.status == "completed"
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    NasStatusBadge(
                        text = task.status.statusLabel(),
                        positive = task.status !in setOf("failed", "cancelled")
                    )
                }
                Spacer(Modifier.height(8.dp))
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = nasMotionSpec(motionEnabled, NasMotion.Standard),
                    label = "taskProgress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${progressMB}MB / ${totalMB}MB",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Calculate percentage from animated progress (no need to memoize animated state)
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (task.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        when (task.status) {
                            "running", "waiting" -> {
                                DropdownMenuItem(text = { Text("暂停") }, onClick = { onAction("pause"); showMenu = false })
                                DropdownMenuItem(text = { Text("取消") }, onClick = { onAction("cancel"); showMenu = false })
                            }
                            "paused" -> {
                                DropdownMenuItem(text = { Text("恢复") }, onClick = { onAction("resume"); showMenu = false })
                                DropdownMenuItem(text = { Text("取消") }, onClick = { onAction("cancel"); showMenu = false })
                            }
                            "failed", "cancelled" -> {
                                DropdownMenuItem(text = { Text("重试") }, onClick = { onAction("retry"); showMenu = false })
                                DropdownMenuItem(text = { Text("删除") }, onClick = { onAction("delete"); showMenu = false })
                            }
                            "completed" -> {
                                DropdownMenuItem(text = { Text("删除") }, onClick = { onAction("delete"); showMenu = false })
                            }
                        }
                    }
                }
            }
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
