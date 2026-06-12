package com.nastools.app.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.UploadPresetEntity
import com.nastools.app.data.network.RemoteEntry
import com.nastools.app.data.network.StorageAdapter
import com.nastools.app.data.network.StorageAdapterFactory
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.UploadPresetRepository
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.domain.model.UploadPresetOptionsCodec
import com.nastools.app.service.LocalFileMetadata
import com.nastools.app.service.UploadTaskCreator
import com.nastools.app.util.RemotePath
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowserUiState(
    val configId: String = "",
    val configName: String = "",
    val path: String = "/",
    val entries: List<RemoteEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isGrid: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val configRepository: NasConfigRepository,
    private val adapterFactory: StorageAdapterFactory,
    private val uploadTaskCreator: UploadTaskCreator,
    private val presetRepository: UploadPresetRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private var config: NasConfigEntity? = null

    fun load(configId: String) {
        if (_uiState.value.configId == configId && config != null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(configId = configId, isLoading = true, errorMessage = null, message = null)
            }

            val loaded = configRepository.getById(configId)
            if (loaded == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "连接配置不存在")
                }
                return@launch
            }

            config = loaded
            val startPath = RemotePath.normalize(loaded.defaultRemotePath ?: "/")
            _uiState.update {
                it.copy(configName = loaded.name, path = startPath, isLoading = false)
            }
            loadPath(startPath)
        }
    }

    fun refresh() {
        loadPath(_uiState.value.path)
    }

    fun open(entry: RemoteEntry) {
        if (entry.isDirectory) loadPath(entry.path)
    }

    fun goUp() {
        val current = _uiState.value.path
        if (current != "/") loadPath(RemotePath.parent(current))
    }

    fun toggleLayout() {
        _uiState.update { it.copy(isGrid = !it.isGrid) }
    }

    fun createFolder(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入文件夹名称") }
            return
        }

        viewModelScope.launch {
            runWithAdapter { adapter ->
                val currentPath = _uiState.value.path
                _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
                adapter.mkdir(RemotePath.join(currentPath, cleanName))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = adapter.list(currentPath),
                        message = "文件夹已创建"
                    )
                }
            }
        }
    }

    fun delete(entry: RemoteEntry) {
        viewModelScope.launch {
            runWithAdapter { adapter ->
                val currentPath = _uiState.value.path
                _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
                adapter.delete(entry.path)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = adapter.list(currentPath),
                        message = "已删除 ${entry.name}"
                    )
                }
            }
        }
    }

    fun describeLocalSource(localUri: String, sourceType: String): LocalFileMetadata {
        return if (sourceType == "folder") {
            uploadTaskCreator.describeLocalTree(localUri)
        } else {
            uploadTaskCreator.describeLocalUri(localUri)
        }
    }

    fun enqueueUpload(localUri: String) {
        enqueueUpload(
            localUri = localUri,
            sourceType = "file",
            options = UploadPresetOptions(sourceType = "file"),
            saveAsPreset = false,
            presetName = ""
        )
    }

    fun enqueueUpload(
        localUri: String,
        sourceType: String,
        options: UploadPresetOptions,
        saveAsPreset: Boolean,
        presetName: String
    ) {
        val configId = _uiState.value.configId
        if (configId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val normalizedOptions = options.copy(sourceType = sourceType)
                    val metadata = describeLocalSource(localUri, sourceType)

                    if (sourceType == "folder") {
                        uploadTaskCreator.enqueueFolderUpload(
                            nasConfigId = configId,
                            localUri = localUri,
                            remoteDirectory = _uiState.value.path,
                            options = normalizedOptions
                        )
                    } else {
                        uploadTaskCreator.enqueueFileUpload(
                            nasConfigId = configId,
                            localUri = localUri,
                            remoteDirectory = _uiState.value.path,
                            options = normalizedOptions
                        )
                    }

                    if (saveAsPreset) {
                        savePreset(
                            configId = configId,
                            localUri = localUri,
                            metadata = metadata,
                            options = normalizedOptions,
                            presetName = presetName
                        )
                    }
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(message = if (saveAsPreset) "已加入上传任务并保存为预设" else "已加入上传任务")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "创建上传任务失败") }
            }
        }
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(errorMessage = null, message = null) }
    }

    private suspend fun savePreset(
        configId: String,
        localUri: String,
        metadata: LocalFileMetadata,
        options: UploadPresetOptions,
        presetName: String
    ) {
        val now = System.currentTimeMillis()
        presetRepository.upsert(
            UploadPresetEntity(
                id = UUID.randomUUID().toString(),
                nasConfigId = configId,
                name = presetName.ifBlank { metadata.name.substringBeforeLast('.') },
                localUri = localUri,
                localLabel = metadata.name,
                remoteRoot = _uiState.value.path,
                optionsJson = UploadPresetOptionsCodec.encode(options),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun loadPath(path: String) {
        viewModelScope.launch {
            runWithAdapter { adapter ->
                val normalized = RemotePath.normalize(path)
                _uiState.update {
                    it.copy(path = normalized, isLoading = true, errorMessage = null, message = null)
                }
                _uiState.update {
                    it.copy(path = normalized, entries = adapter.list(normalized), isLoading = false)
                }
            }
        }
    }

    private suspend fun runWithAdapter(block: suspend (StorageAdapter) -> Unit) {
        val currentConfig = config
        if (currentConfig == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "连接配置未加载") }
            return
        }

        runCatching {
            block(adapterFactory.create(currentConfig))
        }.onFailure { error ->
            _uiState.update {
                it.copy(isLoading = false, errorMessage = error.message ?: "操作失败")
            }
        }
    }
}
