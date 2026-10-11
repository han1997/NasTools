package com.nastools.app.presentation.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.network.RemoteEntry
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasErrorState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasConfirmDialog
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme
import com.nastools.app.util.BytesFormat
import com.nastools.app.util.RemoteMedia

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    onSwitchConnection: () -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var showUploadMenu by remember { mutableStateOf(false) }
    var uploadDraft by remember { mutableStateOf<UploadDraft?>(null) }
    var entryPendingDelete by remember { mutableStateOf<RemoteEntry?>(null) }
    val motionEnabled = rememberNasMotionEnabled()

    fun draftFromUri(uri: android.net.Uri, sourceType: String): UploadDraft {
        val label = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: if (sourceType == "folder") "folder" else "upload.bin"
        return UploadDraft(uri.toString(), sourceType, label)
    }

    fun openEntry(entry: RemoteEntry) {
        when {
            entry.isDirectory -> viewModel.open(entry)
            RemoteMedia.isSupported(entry) -> onOpenMedia(entry.path)
            else -> viewModel.showUnsupportedFile(entry)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { uploadDraft = draftFromUri(it, "file") }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { uploadDraft = draftFromUri(it, "folder") }
    }

    LaunchedEffect(Unit) {
        // 加载由会话内选择的连接驱动（见 BrowserViewModel）；
        // 若会话已有选择但页面首次组合（如进程内重建），主动触发一次加载。
        viewModel.loadCurrent()
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

    entryPendingDelete?.let { entry ->
        NasConfirmDialog(
            title = "删除${if (entry.isDirectory) "文件夹" else "文件"}",
            message = "确定要删除 ${entry.name} 吗？此操作会直接删除 NAS 上的远端项目，无法撤销。",
            confirmText = "删除",
            onConfirm = {
                viewModel.delete(entry)
                entryPendingDelete = null
            },
            onDismiss = { entryPendingDelete = null },
            icon = Icons.Default.Delete
        )
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            NasTopAppBar(
                title = uiState.configName.ifBlank { "文件浏览" },
                subtitle = "文件空间",
                navigationIcon = {
                    if (uiState.path != "/") {
                        IconButton(onClick = { viewModel.goUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "返回上一级目录"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSwitchConnection) {
                        Icon(Icons.Default.SwapHoriz, "切换连接")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    Box {
                        IconButton(onClick = { showUploadMenu = true }) {
                            Icon(Icons.Default.Add, "文件操作")
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
                            DropdownMenuItem(
                                text = { Text("新建文件夹") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = {
                                    showUploadMenu = false
                                    showCreateFolderDialog = true
                                }
                            )
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
            // 面包屑（多层目录）+ 视图切换（Concept 01）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = NasSpacing.lg, end = NasSpacing.xs, top = NasSpacing.xs, bottom = NasSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BreadcrumbCrumbs(
                        path = uiState.path,
                        onNavigate = viewModel::navigateTo
                    )
                }
                IconButton(onClick = viewModel::toggleLayout) {
                    Icon(
                        if (uiState.isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                        if (uiState.isGrid) "切换到列表视图" else "切换到网格视图"
                    )
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                uiState.shouldShowBlockingError() -> {
                    NasErrorState(
                        title = "加载目录失败",
                        message = uiState.pageErrorMessage ?: "无法加载目录",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Default.Error
                    )
                }

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
                        contentPadding = PaddingValues(
                            start = NasSpacing.lg,
                            top = NasSpacing.sm,
                            end = NasSpacing.lg,
                            bottom = NasSpacing.xl
                        ),
                        verticalArrangement = Arrangement.spacedBy(NasSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(NasSpacing.md)
                    ) {
                        items(uiState.entries, key = { it.path }) { entry ->
                            EntryGridCard(
                                entry = entry,
                                modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                                onOpen = { openEntry(entry) },
                                onDelete = { entryPendingDelete = entry }
                            )
                        }
                        item { UploadFooter(onUpload = { showUploadMenu = true }) }
                    }
                }

                else -> {
                    // 列表模式：文件夹卡片在上，文件行在下（Concept 01）
                    val folders = uiState.entries.filter { it.isDirectory }
                    val files = uiState.entries.filterNot { it.isDirectory }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = NasSpacing.lg,
                            top = NasSpacing.sm,
                            end = NasSpacing.lg,
                            bottom = NasSpacing.xl
                        ),
                        verticalArrangement = Arrangement.spacedBy(NasSpacing.sm)
                    ) {
                        if (folders.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(NasSpacing.md)) {
                                    folders.chunked(2).forEach { rowFolders ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(NasSpacing.md)) {
                                            rowFolders.forEach { folder ->
                                                FolderCard(
                                                    name = folder.name,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { openEntry(folder) }
                                                )
                                            }
                                            if (rowFolders.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        items(files, key = { it.path }) { entry ->
                            EntryRow(
                                entry = entry,
                                modifier = if (motionEnabled) Modifier.animateItemPlacement() else Modifier,
                                onOpen = { openEntry(entry) },
                                onDelete = { entryPendingDelete = entry }
                            )
                        }
                        item { UploadFooter(onUpload = { showUploadMenu = true }) }
                    }
                }
            }
        }
    }
}

/** 面包屑：根目录 + 逐级路径段，点击跳转（多层目录导航） */
@Composable
private fun BreadcrumbCrumbs(
    path: String,
    onNavigate: (String) -> Unit
) {
    val segments = path.split('/').filter { it.isNotBlank() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CrumbButton(label = "根目录", onClick = { onNavigate("/") })
        segments.forEachIndexed { index, segment ->
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val target = "/" + segments.take(index + 1).joinToString("/")
            CrumbButton(
                label = segment,
                onClick = { onNavigate(target) },
                isLast = index == segments.lastIndex
            )
        }
    }
}

@Composable
private fun CrumbButton(label: String, onClick: () -> Unit, isLast: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isLast) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(NasShape.Field)
            .clickable(onClick = onClick)
            .padding(horizontal = NasSpacing.xs, vertical = NasSpacing.sm)
    )
}

/** 文件夹卡片（列表模式两列，Concept 01） */
@Composable
private fun FolderCard(
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = NasShape.Field,
        colors = nasCardColors(),
        elevation = nasCardElevation(),
        border = nasCardBorder()
    ) {
        Column(modifier = Modifier.padding(NasSpacing.md)) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = NasSpacing.md)
                    .size(30.dp)
            )
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "文件夹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 底部上传入口（Concept 01：上传到当前目录） */
@Composable
private fun UploadFooter(onUpload: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { showMenu = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NasSpacing.lg)
        ) {
            Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(NasSpacing.sm))
            Text("上传到当前目录")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("上传文件") },
                leadingIcon = { Icon(Icons.Default.InsertDriveFile, null) },
                onClick = {
                    showMenu = false
                    onUpload()
                }
            )
            DropdownMenuItem(
                text = { Text("上传文件夹") },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                onClick = {
                    showMenu = false
                    onUpload()
                }
            )
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
                verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
            ) {
                // 来源与目标（上传目标固定为当前目录，按钮文案已说明）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (draft.sourceType == "folder") Icons.Default.FolderOpen else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        draft.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = NasSpacing.sm)
                    )
                }
                Text(
                    "上传到当前目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(
                    "同名文件的默认策略按远端大小判断：一致则跳过，不完整则续传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

    // Concept 01：文件行为裸行 + 底部分隔线（无卡片）
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = NasSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntryIcon(entry)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = NasSpacing.md)
            ) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        shape = NasShape.Card,
        colors = nasCardColors(),
        elevation = nasCardElevation()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(NasSpacing.md),
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
        contentDescription = if (entry.isDirectory) "文件夹" else "文件",
        selected = true
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

// ---------------------------------------------------------------------------
// @Preview：渲染无状态的 EntryRow，不依赖 ViewModel（AC15）。
// ---------------------------------------------------------------------------

@Preview(name = "文件浏览 · 浅色", showBackground = true)
@Composable
private fun BrowserScreenLightPreview() {
    NasToolsTheme(darkTheme = false) { BrowserPreviewContent() }
}

@Preview(name = "文件浏览 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun BrowserScreenDarkPreview() {
    NasToolsTheme(darkTheme = true) { BrowserPreviewContent() }
}

@Composable
private fun BrowserPreviewContent() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.sm)
        ) {
            EntryRow(
                entry = RemoteEntry(path = "/photos", name = "照片", isDirectory = true),
                onOpen = {},
                onDelete = {}
            )
            EntryRow(
                entry = RemoteEntry(
                    path = "/photos/IMG_0001.jpg",
                    name = "IMG_0001.jpg",
                    isDirectory = false,
                    size = 1024L * 512
                ),
                onOpen = {},
                onDelete = {}
            )
        }
    }
}
