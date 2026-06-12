package com.nastools.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.NasConfigEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToConfig: (String) -> Unit = {},
    onNavigateToNewConfig: () -> Unit = {},
    onNavigateToBrowser: (String) -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NasTools") },
                actions = {
                    IconButton(onClick = onNavigateToPresets) {
                        Icon(Icons.Default.Bookmark, "预设")
                    }
                    IconButton(onClick = onNavigateToTasks) {
                        Icon(Icons.Default.Task, "任务")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewConfig) {
                Icon(Icons.Default.Add, "新建连接")
            }
        }
    ) { padding ->
        if (uiState.activeTasks.isNotEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToTasks() },
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${uiState.activeTasks.size} 个进行中的任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                ConfigList(
                    configs = uiState.configs,
                    onConfigClick = onNavigateToBrowser,
                    onManageClick = onNavigateToConfig
                )
            }
        } else {
            ConfigList(
                modifier = Modifier.padding(padding),
                configs = uiState.configs,
                onConfigClick = onNavigateToBrowser,
                onManageClick = onNavigateToConfig
            )
        }
    }
}

@Composable
private fun ConfigList(
    configs: List<NasConfigEntity>,
    onConfigClick: (String) -> Unit,
    onManageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (configs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text("还没有连接配置", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(configs) { config ->
                ConfigCard(config = config, onClick = { onConfigClick(config.id) })
            }
        }
    }
}

@Composable
private fun ConfigCard(config: NasConfigEntity, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(config.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
