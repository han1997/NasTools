package com.nastools.app.presentation.presets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.UploadPresetEntity
import com.nastools.app.domain.model.UploadPresetOptionsCodec
import com.nastools.app.presentation.components.NasConfirmDialog
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasMiniFab
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.presentation.theme.NasListPaddingWithFab
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PresetListScreen(
    viewModel: PresetListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onCreatePreset: () -> Unit = {},
    onEditPreset: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val transientMessage = uiState.errorMessage ?: uiState.message
    val motionEnabled = rememberNasMotionEnabled()
    var presetPendingDelete by remember { mutableStateOf<UploadPresetEntity?>(null) }

    LaunchedEffect(transientMessage) {
        if (!transientMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(transientMessage)
            viewModel.clearTransientMessage()
        }
    }

    presetPendingDelete?.let { preset ->
        NasConfirmDialog(
            title = "删除预设",
            message = "确定要删除预设 ${preset.name} 吗？此操作无法撤销，不会删除已创建的上传任务。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deletePreset(preset.id)
                presetPendingDelete = null
            },
            onDismiss = { presetPendingDelete = null },
            icon = Icons.Default.Delete
        )
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NasTopAppBar(
                title = "上传预设",
                subtitle = "保存常用来源和冲突策略",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            NasMiniFab(onClick = onCreatePreset, icon = Icons.Default.Add, contentDescription = "新建预设")
        }
    ) { padding ->
        if (uiState.presets.isEmpty()) {
            NasEmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Default.Bookmark,
                title = if (uiState.configs.isEmpty()) "先新建连接" else "还没有上传预设",
                message = if (uiState.configs.isEmpty()) {
                    "连接创建后，可以把常用上传保存为预设"
                } else {
                    "保存来源、远端目录和同名处理方式，之后一键运行"
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = NasListPaddingWithFab,
                verticalArrangement = Arrangement.spacedBy(NasSpacing.sm)
            ) {
                items(uiState.presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                        configName = uiState.configNames[preset.nasConfigId] ?: "未知连接",
                        onRun = { viewModel.runPreset(preset.id) },
                        onEdit = { onEditPreset(preset.id) },
                        onDelete = { presetPendingDelete = preset }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: UploadPresetEntity,
    modifier: Modifier = Modifier,
    configName: String,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val motionEnabled = rememberNasMotionEnabled()
    // 冲突策略标签（来自 options 编码）
    val policyLabel = remember(preset.optionsJson) {
        val options = runCatching { UploadPresetOptionsCodec.decode(preset.optionsJson) }.getOrNull()
        fileConflictOptions.firstOrNull { it.first == options?.overwriteMode }?.second ?: "默认策略"
    }

    Card(
        onClick = onEdit,
        modifier = modifier.nasAnimateContentSize(motionEnabled),
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
                NasIconContainer(Icons.Default.Bookmark, null, selected = true)
                Spacer(Modifier.width(NasSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        configName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多操作")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(NasSpacing.md))
            // 来源 → 目标路径块（Concept 01）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = NasShape.Field
                    )
                    .padding(NasSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NasSpacing.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        preset.localLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = NasSpacing.sm)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        preset.remoteRoot,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = NasSpacing.sm)
                    )
                }
            }
            Spacer(Modifier.height(NasSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    policyLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRun, enabled = !uiStateIsRunningPlaceholder()) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(NasSpacing.xs))
                    Text("运行")
                }
            }
        }
    }
}

private fun uiStateIsRunningPlaceholder(): Boolean = false

// ---------------------------------------------------------------------------
// @Preview：渲染无状态的 PresetCard，不依赖 ViewModel（AC15）。
// ---------------------------------------------------------------------------

@Preview(name = "上传预设 · 浅色", showBackground = true)
@Composable
private fun PresetListLightPreview() {
    NasToolsTheme(darkTheme = false) { PresetPreviewContent() }
}

@Preview(name = "上传预设 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun PresetListDarkPreview() {
    NasToolsTheme(darkTheme = true) { PresetPreviewContent() }
}

@Composable
private fun PresetPreviewContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.sm)
        ) {
            PresetCard(
                preset = UploadPresetEntity(
                    id = "preview",
                    nasConfigId = "preview",
                    name = "照片备份",
                    localUri = "content://preview",
                    localLabel = "相册",
                    remoteRoot = "/backup/photos",
                    optionsJson = "{}"
                ),
                configName = "家庭 NAS",
                onRun = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}
