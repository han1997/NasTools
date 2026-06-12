package com.nastools.app.presentation.tasks

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("活跃") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("已完成") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("失败") })
            }

            when (selectedTab) {
                0 -> TaskList(tasks = uiState.activeTasks, onAction = { task, action ->
                    when (action) {
                        "pause" -> viewModel.pauseTask(task.id)
                        "resume" -> viewModel.resumeTask(task.id)
                        "cancel" -> viewModel.cancelTask(task.id)
                    }
                })
                1 -> TaskList(tasks = uiState.completedTasks, onAction = { task, action ->
                    if (action == "delete") viewModel.deleteTask(task.id)
                })
                2 -> TaskList(tasks = uiState.failedTasks, onAction = { task, action ->
                    when (action) {
                        "retry" -> viewModel.retryTask(task.id)
                        "delete" -> viewModel.deleteTask(task.id)
                    }
                })
            }
        }
    }
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, onAction: (TaskEntity, String) -> Unit) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks) { task ->
                TaskCard(task = task, onAction = { action -> onAction(task, action) })
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onAction: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { if (task.totalBytes > 0) (task.progressBytes.toFloat() / task.totalBytes) else 0f },
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${task.progressBytes / 1024 / 1024}MB / ${task.totalBytes / 1024 / 1024}MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.errorMessage != null) {
                    Text(task.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
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
