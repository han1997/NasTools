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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.presentation.components.NasCardShape
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasMotion
import com.nastools.app.presentation.components.NasMiniFab
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled

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
    val motionEnabled = rememberNasMotionEnabled()

    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = "NasTools",
                subtitle = "连接、浏览和上传 NAS 文件",
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
            NasMiniFab(onClick = onNavigateToNewConfig, icon = Icons.Default.Add, contentDescription = "新建连接")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nasAnimateContentSize(motionEnabled)
        ) {
            if (uiState.activeTasks.isNotEmpty()) {
                ActiveTaskBanner(
                    count = uiState.activeTasks.size,
                    onClick = onNavigateToTasks
                )
            }
            ConfigList(
                motionEnabled = motionEnabled,
                configs = uiState.configs,
                onConfigClick = onNavigateToBrowser,
                onManageClick = onNavigateToConfig,
                onCreateClick = onNavigateToNewConfig
            )
        }
    }
}

@Composable
private fun ActiveTaskBanner(count: Int, onClick: () -> Unit) {
    val motionEnabled = rememberNasMotionEnabled()
    val pulseScale = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "activeTaskPulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = NasMotion.EaseOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "activeTaskPulseScale"
        )
        value
    } else {
        1f
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        shape = NasCardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = nasCardBorder(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NasIconContainer(
                Icons.Default.Sync,
                null,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
                selected = true
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("上传进行中", style = MaterialTheme.typography.titleSmall)
                Text(
                    "$count 个任务正在处理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigList(
    configs: List<NasConfigEntity>,
    onConfigClick: (String) -> Unit,
    onManageClick: (String) -> Unit,
    onCreateClick: () -> Unit,
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
                    Spacer(Modifier.width(8.dp))
                    Text("新建连接")
                }
            }
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = "连接",
                    detail = "${configs.size} 个配置"
                )
            }
            items(configs, key = { it.id }) { config ->
                ConfigCard(
                    config = config,
                    modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                    onClick = { onConfigClick(config.id) },
                    onManageClick = { onManageClick(config.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigCard(
    config: NasConfigEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onManageClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = NasCardShape,
        colors = nasCardColors(),
        border = nasCardBorder(),
        elevation = nasCardElevation()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NasIconContainer(Icons.Default.Storage, null, selected = true)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    config.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!config.defaultRemotePath.isNullOrBlank() && config.defaultRemotePath != "/") {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "默认目录 ${config.defaultRemotePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onManageClick) {
                Icon(Icons.Default.Edit, "编辑连接")
            }
        }
    }
}
