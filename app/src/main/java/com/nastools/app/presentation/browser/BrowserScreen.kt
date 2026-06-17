package com.nastools.app.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.network.RemoteEntry
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.presentation.components.NasCardShape
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.util.BytesFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    configId: String,
    viewModel: BrowserViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var showUploadMenu by remember { mutableStateOf(false) }
    var uploadDraft by remember { mutableStateOf<UploadDraft?>(null) }
    val motionEnabled = rememberNasMotionEnabled()

    fun draftFromUri(uri: android.net.Uri, sourceType: String): UploadDraft {
        val label = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: if (sourceType == "folder") "folder" else "upload.bin"
        return UploadDraft(uri.toString(), sourceType, label)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { uploadDraft = draftFromUri(it, "file") }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { uploadDraft = draftFromUri(it, "folder") }
    }

    LaunchedEffect(configId) {
        viewModel.load(configId)
    }

    BackHandler(enabled = uiState.path != "/") {
        viewModel.goUp()
    }

    val transientMessage = uiState.errorMessage ?: uiState.message
    LaunchedEffect(transientMessage) {
        if (!transientMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(transientMessage)
            viewModel.clearTransientMessage()
        }
    }

    uploadDraft?.let { draft ->
        UploadOptionsDialog(
            draft = draft,
            onDismiss = { uploadDraft = null },
            onConfirm = { options, saveAsPreset, presetName ->
                viewModel.enqueueUpload(
                    localUri = draft.uri,
                    sourceType = draft.sourceType,
                    options = options,
                    saveAsPreset = saveAsPreset,
                    presetName = presetName
                )
                uploadDraft = null
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(folderName)
                        folderName = ""
                        showCreateFolderDialog = false
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NasTopAppBar(
                title = uiState.configName.ifBlank { "文件浏览" },
                subtitle = uiState.path,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.path == "/") {
                                onBack()
                            } else {
                                viewModel.goUp()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            if (uiState.path == "/") "返回主页" else "返回上一级目录"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Home, "返回主页")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    Box {
                        IconButton(onClick = { showUploadMenu = true }) {
                            Icon(Icons.Default.UploadFile, "上传")
                        }
                        DropdownMenu(
                            expanded = showUploadMenu,
                            onDismissRequest = { showUploadMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("上传文件") },
                                leadingIcon = { Icon(Icons.Default.InsertDriveFile, null) },
                                onClick = {
                                    showUploadMenu = false
                                    filePicker.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("上传文件夹") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = {
                                    showUploadMenu = false
                                    folderPicker.launch(null)
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "新建文件夹")
                    }
                    IconButton(onClick = viewModel::toggleLayout) {
                        Icon(
                            if (uiState.isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                            if (uiState.isGrid) "列表视图" else "网格视图"
                        )
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
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.entries.isEmpty() -> {
                    NasEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Default.FolderOpen,
                        title = "此目录为空",
                        message = "可以上传文件，或在此处新建文件夹"
                    )
                }

                uiState.isGrid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(132.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.entries, key = { it.path }) { entry ->
                            EntryGridCard(
                                entry = entry,
                                modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                                onOpen = { viewModel.open(entry) },
                                onDelete = { viewModel.delete(entry) }
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.entries, key = { it.path }) { entry ->
                            EntryRow(
                                entry = entry,
                                modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                                onOpen = { viewModel.open(entry) },
                                onDelete = { viewModel.delete(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadOptionsDialog(
    draft: UploadDraft,
    onDismiss: () -> Unit,
    onConfirm: (UploadPresetOptions, Boolean, String) -> Unit
) {
    var fileMode by remember { mutableStateOf("resume_or_overwrite") }
    var folderMode by remember { mutableStateOf("merge") }
    var saveAsPreset by remember { mutableStateOf(false) }
    var deleteAfterUpload by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(draft.label.substringBeforeLast('.')) }
    val motionEnabled = rememberNasMotionEnabled()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.sourceType == "folder") "上传文件夹" else "上传文件") },
        text = {
            Column(
                modifier = Modifier.nasAnimateContentSize(motionEnabled),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    draft.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OptionDropdown(
                    label = "同名文件",
                    selected = fileMode,
                    options = fileConflictOptions,
                    onSelected = { fileMode = it }
                )
                if (draft.sourceType == "folder") {
                    OptionDropdown(
                        label = "同名文件夹",
                        selected = folderMode,
                        options = folderConflictOptions,
                        onSelected = { folderMode = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("移动上传", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "上传完成后删除本地${if (draft.sourceType == "folder") "文件夹" else "文件"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = deleteAfterUpload, onCheckedChange = { deleteAfterUpload = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("保存为预设", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "下次可在上传预设中一键运行",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = saveAsPreset, onCheckedChange = { saveAsPreset = it })
                }
                if (saveAsPreset) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("预设名称") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        UploadPresetOptions(
                            sourceType = draft.sourceType,
                            overwriteMode = fileMode,
                            folderConflictMode = folderMode,
                            deleteAfterUpload = deleteAfterUpload
                        ),
                        saveAsPreset,
                        presetName
                    )
                }
            ) {
                Text("开始上传")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun OptionDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label：$selectedLabel",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: RemoteEntry,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = NasCardShape,
        colors = nasCardColors(),
        border = nasCardBorder(),
        elevation = nasCardElevation()
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(entry.subtitle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                EntryIcon(entry)
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    EntryMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onDelete = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun EntryGridCard(
    entry: RemoteEntry,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier.aspectRatio(1f),
        shape = NasCardShape,
        colors = nasCardColors(),
        border = nasCardBorder(),
        elevation = nasCardElevation()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Top) {
                EntryIcon(entry)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    EntryMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onDelete = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
            Column {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EntryIcon(entry: RemoteEntry) {
    NasIconContainer(
        icon = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
        contentDescription = null,
        selected = entry.isDirectory
    )
}

@Composable
private fun EntryMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("删除") },
            leadingIcon = { Icon(Icons.Default.Delete, null) },
            onClick = onDelete
        )
    }
}

private fun RemoteEntry.subtitle(): String {
    if (isDirectory) return "文件夹"
    return size?.let { BytesFormat.human(it) } ?: "文件"
}

private data class UploadDraft(
    val uri: String,
    val sourceType: String,
    val label: String
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
