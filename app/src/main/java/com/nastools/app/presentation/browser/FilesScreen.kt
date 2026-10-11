package com.nastools.app.presentation.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.presentation.components.NasEmptyState
import com.nastools.app.presentation.components.NasIconContainer
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.theme.NasSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 文件标签页的容器状态：观察会话内选择的连接与配置列表，
 * 负责区分「未选择」「加载中」「已选连接有效」「已选连接被删除」。
 */
data class FilesUiState(
    val selectedConfigId: String? = null,
    val configs: List<NasConfigEntity>? = null
)
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val fileTabSession: FileTabSession,
    configRepository: NasConfigRepository
) : ViewModel() {

    val uiState: StateFlow<FilesUiState> = combine(
        fileTabSession.selectedConfigId,
        // null 表示配置列表尚未加载完成，不能与「无连接」混淆
        configRepository.observeAll().map<List<NasConfigEntity>, List<NasConfigEntity>?> { it }
    ) { selected, configs ->
        FilesUiState(selectedConfigId = selected, configs = configs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilesUiState()
    )

    init {
        // 当前连接配置被删除：清理会话选择，回到连接选择页。
        // 配置列表尚未加载完成（null）时不能误判为已删除。
        viewModelScope.launch {
            uiState.collect { state ->
                val configs = state.configs ?: return@collect
                val selected = state.selectedConfigId ?: return@collect
                if (configs.none { it.id == selected }) {
                    fileTabSession.clearSelection()
                }
            }
        }
    }

    fun select(configId: String) {
        fileTabSession.select(configId)
    }
}

/**
 * 文件标签页：根据会话内选择展示连接选择页或浏览器。
 *
 * - 未选择连接（即使只有一台）→ 连接选择页
 * - 已选择且配置存在 → 浏览器（顶部有切换入口）
 * - 切换连接：先展示选择页，选中才替换；取消则保留原连接
 * - 当前配置被删除 → 清理选择，回连接选择页
 */
@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel(),
    bottomBar: @Composable () -> Unit = {},
    onNewConnection: () -> Unit = {},
    onOpenMedia: (configId: String, path: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var switching by remember { mutableStateOf(false) }

    val selected = uiState.selectedConfigId
    val configs = uiState.configs
    val selectedExists = selected != null && configs != null && configs.any { it.id == selected }

    when {
        selected != null && switching -> {
            ConnectionSelectScreen(
                configs = configs.orEmpty(),
                isLoading = configs == null,
                onBack = { switching = false },
                onPick = { id ->
                    viewModel.select(id)
                    switching = false
                },
                onNewConnection = onNewConnection
            )
        }

        selectedExists && selected != null -> {
            val configId = selected
            BrowserScreen(
                onSwitchConnection = { switching = true },
                onOpenMedia = { path -> onOpenMedia(configId, path) },
                bottomBar = bottomBar
            )
        }

        // 已选择但配置列表还在加载：短暂加载态，避免闪烁成选择页
        selected != null && configs == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> {
            ConnectionSelectScreen(
                configs = configs.orEmpty(),
                isLoading = configs == null,
                onBack = { /* 由系统返回处理：回到上一个主标签 */ },
                onPick = viewModel::select,
                onNewConnection = onNewConnection
            )
        }
    }
}

/**
 * 连接选择页（用户已确认）：文件入口未选连接时显示（仅一台也不默选）；
 * 无连接引导新建；顶部返回回到来源。
 */
@Composable
fun ConnectionSelectScreen(
    configs: List<NasConfigEntity>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onNewConnection: () -> Unit
) {
    NasScaffold(
        topBar = {
            NasTopAppBar(
                title = "选择连接",
                subtitle = "文件空间",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            configs.isEmpty() -> {
                NasEmptyState(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    icon = Icons.Default.Dns,
                    title = "还没有连接",
                    message = "先新建一个 WebDAV 连接，才能浏览文件。",
                    action = {
                        Button(onClick = onNewConnection) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = 4.dp))
                            Text("新建连接")
                        }
                    }
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = NasSpacing.lg,
                        end = NasSpacing.lg,
                        top = NasSpacing.sm,
                        bottom = NasSpacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
                ) {
                    items(configs, key = { it.id }) { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(config.id) }
                                .padding(vertical = NasSpacing.md),
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
                                    maxLines = 1
                                )
                                Text(
                                    text = config.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
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
