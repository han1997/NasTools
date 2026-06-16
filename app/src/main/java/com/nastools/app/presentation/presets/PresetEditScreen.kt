package com.nastools.app.presentation.presets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.rememberNasMotionEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditScreen(
    presetId: String?,
    viewModel: PresetEditViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val motionEnabled = rememberNasMotionEnabled()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.updateLocalUri(it.toString()) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.updateLocalTreeUri(it.toString()) }
    }

    LaunchedEffect(presetId) {
        viewModel.load(presetId)
    }

    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = if (presetId == null) "新建预设" else "编辑预设",
                subtitle = "来源、目录和冲突策略",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Default.Check, "保存")
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
                .padding(16.dp)
                .nasAnimateContentSize(motionEnabled),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.isLoading || uiState.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ConfigSelector(
                configs = uiState.configs.map { it.id to it.name },
                selectedId = uiState.nasConfigId,
                onSelected = viewModel::updateNasConfigId
            )

            OptionDropdown(
                label = "来源类型",
                selected = uiState.sourceType,
                options = sourceTypeOptions,
                onSelected = viewModel::updateSourceType
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("预设名称") },
                singleLine = true
            )

            OutlinedButton(
                onClick = {
                    if (uiState.sourceType == "folder") {
                        folderPicker.launch(null)
                    } else {
                        filePicker.launch(arrayOf("*/*"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (uiState.sourceType == "folder") Icons.Default.FolderOpen else Icons.Default.InsertDriveFile,
                    null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    uiState.localLabel.ifBlank {
                        if (uiState.sourceType == "folder") "选择本地文件夹" else "选择本地文件"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedTextField(
                value = uiState.remoteRoot,
                onValueChange = viewModel::updateRemoteRoot,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("远端目录") },
                singleLine = true
            )

            OptionDropdown(
                label = "同名文件",
                selected = uiState.overwriteMode,
                options = fileConflictOptions,
                onSelected = viewModel::updateOverwriteMode
            )

            if (uiState.sourceType == "folder") {
                OptionDropdown(
                    label = "同名文件夹",
                    selected = uiState.folderConflictMode,
                    options = folderConflictOptions,
                    onSelected = viewModel::updateFolderConflictMode
                )
            }

            OutlinedTextField(
                value = uiState.chunkSizeMb,
                onValueChange = viewModel::updateChunkSizeMb,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分块大小 MB") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.filterRegex,
                onValueChange = viewModel::updateFilterRegex,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("文件名过滤正则") },
                singleLine = true
            )

            SwitchRow(
                title = "仅 Wi-Fi 上传",
                subtitle = "非 Wi-Fi 网络下任务会失败并保留在任务中心",
                checked = uiState.wifiOnly,
                onCheckedChange = viewModel::updateWifiOnly
            )

            SwitchRow(
                title = "移动上传",
                subtitle = if (uiState.sourceType == "folder") {
                    "上传完成后删除本地文件夹，受系统文件提供方限制"
                } else {
                    "上传完成后删除本地文件，需要文件提供方允许写入"
                },
                checked = uiState.deleteAfterUpload,
                onCheckedChange = viewModel::updateDeleteAfterUpload
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                Text(if (uiState.isSaving) "保存中..." else "保存")
            }
        }
    }
}

@Composable
private fun ConfigSelector(
    configs: List<Pair<String, String>>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    OptionDropdown(
        label = "连接",
        selected = selectedId,
        options = configs,
        fallback = "选择连接",
        onSelected = onSelected
    )
}

@Composable
private fun OptionDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    fallback: String = "请选择",
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selected }?.second ?: fallback

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label：$selectedName",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelected(id)
                    }
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val sourceTypeOptions = listOf(
    "file" to "文件",
    "folder" to "文件夹"
)

private val fileConflictOptions = listOf(
    "resume_or_overwrite" to "续传，完整则跳过",
    "overwrite" to "覆盖",
    "skip_existing" to "跳过",
    "rename" to "自动改名",
    "fail" to "报错停止"
)

private val folderConflictOptions = listOf(
    "merge" to "合并",
    "rename" to "自动改名",
    "skip" to "跳过",
    "fail" to "报错停止"
)
