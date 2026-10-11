package com.nastools.app.presentation.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.domain.backup.BackupRemotePath
import com.nastools.app.domain.backup.BackupResult
import com.nastools.app.domain.backup.SkippedItem
import com.nastools.app.presentation.components.NasConfirmDialog
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasCardBorder
import com.nastools.app.presentation.components.nasCardColors
import com.nastools.app.presentation.components.nasCardElevation
import com.nastools.app.presentation.theme.NasShape
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme
import com.nastools.app.presentation.theme.NasStatusTone
import com.nastools.app.presentation.theme.nasStatusColors

@Composable
fun BackupScreen(
    viewModel: BackupViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNewConnection: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackupContent(
        uiState = uiState,
        onBack = onBack,
        onNewConnection = onNewConnection,
        onStart = viewModel::start,
        onPickConfig = viewModel::pickConfig,
        onConfirmBackup = viewModel::confirmBackup,
        onCancelConfirm = viewModel::cancelConfirm,
        onRetry = viewModel::retry,
        onBackToIdle = viewModel::backToIdle
    )
}

@Composable
private fun BackupContent(
    uiState: BackupUiState,
    onBack: () -> Unit,
    onNewConnection: () -> Unit,
    onStart: (BackupAction) -> Unit,
    onPickConfig: (NasConfigEntity) -> Unit,
    onConfirmBackup: () -> Unit,
    onCancelConfirm: () -> Unit,
    onRetry: () -> Unit,
    onBackToIdle: () -> Unit
) {
    val phase = uiState.phase
    val isPick = phase is BackupPhase.SelectConnection
    val title = if (isPick) "选择连接" else "备份与恢复"
    val subtitle = when {
        isPick -> if ((phase as BackupPhase.SelectConnection).action == BackupAction.Backup) {
            "备份 · WebDAV"
        } else {
            "恢复 · WebDAV"
        }

        else -> "连接与上传预设"
    }

    // 备份风险确认(D4/R5):页面下方保持动作列表,上方弹出确认对话框
    (phase as? BackupPhase.ConfirmBackup)?.let { confirm ->
        val targetPath = BackupRemotePath.file(confirm.config.defaultRemotePath)
        NasConfirmDialog(
            title = "确认备份",
            message = "将把全部连接与上传预设写入:\n$targetPath\n\n" +
                "备份文件包含明文连接密码。请只放到你信任的位置,并确认该目录不会被他人访问。",
            confirmText = "已知晓,开始备份",
            onConfirm = onConfirmBackup,
            onDismiss = onCancelConfirm,
            icon = Icons.Default.Upload
        )
    }

    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = {
                    IconButton(onClick = { if (isPick) onBackToIdle() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
                .padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.lg)
        ) {
            when (phase) {
                is BackupPhase.SelectConnection -> SelectConnectionContent(
                    action = phase.action,
                    configs = uiState.configs,
                    onPickConfig = onPickConfig,
                    onNewConnection = onNewConnection
                )

                is BackupPhase.Running -> RunningContent(phase.action)

                is BackupPhase.BackupDone -> ResultContent(
                    title = "备份完成",
                    summary = "已写入本机全部连接与上传预设。",
                    highlightTitle = phase.remotePath,
                    highlightMeta = "备份文件包含明文密码,请妥善保管",
                    skipped = emptyList(),
                    onBackToIdle = onBackToIdle
                )

                is BackupPhase.RestoreDone -> ResultContent(
                    title = "恢复完成",
                    summary = restoreSummary(phase.result),
                    highlightTitle = null,
                    highlightMeta = null,
                    skipped = phase.result.skipped,
                    onBackToIdle = onBackToIdle
                )

                is BackupPhase.Failed -> FailedContent(phase.message, onRetry, onBackToIdle)

                is BackupPhase.ConfirmBackup, BackupPhase.Idle -> ActionListContent(onStart)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 空闲态:两个动作 + 备份位置说明
// ---------------------------------------------------------------------------

@Composable
private fun ActionListContent(onStart: (BackupAction) -> Unit) {
    Text(
        "把连接与上传预设写成一个文件放到 WebDAV,换机后可从同一位置恢复。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Card {
        SettingRow(
            icon = Icons.Default.Upload,
            title = "备份到 WebDAV",
            subtitle = "把连接与预设写到所选连接",
            onClick = { onStart(BackupAction.Backup) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingRow(
            icon = Icons.Default.Download,
            title = "从 WebDAV 恢复",
            subtitle = "读取备份文件并合并到本机",
            onClick = { onStart(BackupAction.Restore) }
        )
    }

    Text(
        "备份位置",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Card {
        Column(modifier = Modifier.padding(NasSpacing.lg)) {
            InfoRow("目录", "<连接默认目录>/NasTools")
            Spacer(Modifier.height(NasSpacing.md))
            InfoRow("文件名", "backup.json")
        }
    }
    Text(
        "重复备份会覆盖同一个文件,不保留历史版本。\n" +
            "恢复按名称合并:同名连接复用本机配置(不覆盖),同一连接下的同名预设跳过。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------------------------------------------------------------------------
// 选择连接态(复用连接选择页样式)
// ---------------------------------------------------------------------------

@Composable
private fun SelectConnectionContent(
    action: BackupAction,
    configs: List<NasConfigEntity>?,
    onPickConfig: (NasConfigEntity) -> Unit,
    onNewConnection: () -> Unit
) {
    val kind = if (action == BackupAction.Backup) "备份" else "恢复"

    when {
        configs == null -> {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        configs.isEmpty() -> {
            NasEmptyState(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                icon = Icons.Default.Dns,
                title = "还没有连接",
                message = "备份需要一个 WebDAV 连接作为目的地,先新建一个。",
                action = {
                    Button(onClick = onNewConnection) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = NasSpacing.xs))
                        Text("新建连接")
                    }
                }
            )
        }

        else -> {
            Text(
                "选一个已保存的 WebDAV 连接,$kind 数据会写到它的默认目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(NasSpacing.md)) {
                configs.forEach { config ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickConfig(config) }
                                .padding(NasSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NasIconContainer(Icons.Default.Dns, null)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = NasSpacing.md)
                            ) {
                                Text(
                                    config.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    BackupRemotePath.file(config.defaultRemotePath),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "选择 ${config.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 进行中 / 失败 / 结果
// ---------------------------------------------------------------------------

@Composable
private fun RunningContent(action: BackupAction) {
    val kind = if (action == BackupAction.Backup) "备份" else "恢复"
    Text(
        "正在$kind,请稍候……",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(NasSpacing.md)) {
        SkeletonBar()
        SkeletonBar()
        SkeletonBar()
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit, onBackToIdle: () -> Unit) {
    val colors = nasStatusColors(NasStatusTone.Danger)
    Surface(
        shape = NasShape.Card,
        color = colors.container,
        contentColor = colors.content
    ) {
        Row(modifier = Modifier.padding(NasSpacing.lg), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Error, contentDescription = null)
            Spacer(Modifier.width(NasSpacing.md))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("重新加载")
    }
    Button(onClick = onBackToIdle, modifier = Modifier.fillMaxWidth()) {
        Text("返回备份与恢复")
    }
}

@Composable
private fun ResultContent(
    title: String,
    summary: String,
    highlightTitle: String?,
    highlightMeta: String?,
    skipped: List<SkippedItem>,
    onBackToIdle: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Surface(
        shape = NasShape.Card,
        color = nasStatusColors(NasStatusTone.Success).container,
        contentColor = nasStatusColors(NasStatusTone.Success).content
    ) {
        Row(modifier = Modifier.padding(NasSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(NasSpacing.md))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (highlightTitle != null) {
        Card {
            Row(modifier = Modifier.padding(NasSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = nasStatusColors(NasStatusTone.Success).accent
                )
                Spacer(Modifier.width(NasSpacing.md))
                Column {
                    Text(
                        highlightTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (highlightMeta != null) {
                        Text(
                            highlightMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    skipped.forEach { item ->
        Card {
            Row(modifier = Modifier.padding(NasSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = nasStatusColors(NasStatusTone.Warning).accent
                )
                Spacer(Modifier.width(NasSpacing.md))
                Column {
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "已跳过 · ${item.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Button(onClick = onBackToIdle, modifier = Modifier.fillMaxWidth()) {
        Text("返回备份与恢复")
    }
}

// ---------------------------------------------------------------------------
// 共享小组件
// ---------------------------------------------------------------------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        shape = NasShape.Card,
        colors = nasCardColors(),
        elevation = nasCardElevation(),
        border = nasCardBorder()
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(NasSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = NasSpacing.md)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SkeletonBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = NasShape.Field)
    )
}

private fun restoreSummary(result: BackupResult): String {
    val skippedText = if (result.skipped.isEmpty()) "无跳过项。" else "跳过 ${result.skipped.size} 项。"
    return "新增 ${result.addedConnections} 个连接、${result.addedPresets} 个预设;$skippedText"
}

// ---------------------------------------------------------------------------
// @Preview:渲染无状态的 BackupContent(浅色/深色)
// ---------------------------------------------------------------------------

private val previewConfigs = listOf(
    NasConfigEntity(
        id = "1",
        name = "家庭 NAS",
        type = "webdav",
        baseUrl = "https://nas.home.example/dav",
        username = "han",
        password = "pw",
        defaultRemotePath = "/共享文件"
    )
)

@Preview(name = "备份与恢复 · 空闲 · 浅色", showBackground = true)
@Composable
private fun BackupIdleLightPreview() {
    NasToolsTheme(darkTheme = false) {
        BackupContent(
            uiState = BackupUiState(configs = previewConfigs, phase = BackupPhase.Idle),
            onBack = {}, onNewConnection = {}, onStart = {}, onPickConfig = {},
            onConfirmBackup = {}, onCancelConfirm = {}, onRetry = {}, onBackToIdle = {}
        )
    }
}

@Preview(name = "备份与恢复 · 恢复结果 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun BackupResultDarkPreview() {
    NasToolsTheme(darkTheme = true) {
        BackupContent(
            uiState = BackupUiState(
                configs = previewConfigs,
                phase = BackupPhase.RestoreDone(
                    BackupResult(
                        addedConnections = 1,
                        addedPresets = 2,
                        skipped = listOf(
                            SkippedItem("预设已存在,未覆盖", "预设「工作资料归档」"),
                            SkippedItem("找不到连接「旅行 NAS」", "预设「周末相册」")
                        )
                    )
                )
            ),
            onBack = {}, onNewConnection = {}, onStart = {}, onPickConfig = {},
            onConfirmBackup = {}, onCancelConfirm = {}, onRetry = {}, onBackToIdle = {}
        )
    }
}

@Preview(name = "备份与恢复 · 选择连接 · 浅色", showBackground = true)
@Composable
private fun BackupSelectLightPreview() {
    NasToolsTheme(darkTheme = false) {
        BackupContent(
            uiState = BackupUiState(
                configs = previewConfigs,
                phase = BackupPhase.SelectConnection(BackupAction.Backup)
            ),
            onBack = {}, onNewConnection = {}, onStart = {}, onPickConfig = {},
            onConfirmBackup = {}, onCancelConfirm = {}, onRetry = {}, onBackToIdle = {}
        )
    }
}

@Preview(name = "备份与恢复 · 失败 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun BackupFailedDarkPreview() {
    NasToolsTheme(darkTheme = true) {
        BackupContent(
            uiState = BackupUiState(
                configs = previewConfigs,
                phase = BackupPhase.Failed(
                    action = BackupAction.Restore,
                    config = previewConfigs.first(),
                    message = "恢复失败:未找到备份文件:/共享文件/NasTools/backup.json,请先在该位置执行备份"
                )
            ),
            onBack = {}, onNewConnection = {}, onStart = {}, onPickConfig = {},
            onConfirmBackup = {}, onCancelConfirm = {}, onRetry = {}, onBackToIdle = {}
        )
    }
}