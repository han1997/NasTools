package com.nastools.app.presentation.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.UploadPresetEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.UploadPresetRepository
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.domain.model.UploadPresetOptionsCodec
import com.nastools.app.service.UploadTaskCreator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PresetEditUiState(
    val id: String? = null,
    val configs: List<NasConfigEntity> = emptyList(),
    val nasConfigId: String = "",
    val name: String = "",
    val sourceType: String = "file",
    val localUri: String = "",
    val localLabel: String = "",
    val remoteRoot: String = "/",
    val chunkSizeMb: String = "8",
    val overwriteMode: String = "resume_or_overwrite",
    val folderConflictMode: String = "merge",
    val wifiOnly: Boolean = false,
    val filterRegex: String = "",
    val deleteAfterUpload: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PresetEditViewModel @Inject constructor(
    private val presetRepository: UploadPresetRepository,
    private val configRepository: NasConfigRepository,
    private val uploadTaskCreator: UploadTaskCreator
) : ViewModel() {
    private val _uiState = MutableStateFlow(PresetEditUiState())
    val uiState: StateFlow<PresetEditUiState> = _uiState.asStateFlow()

    private var loadedPresetId: String? = null
    private var originalPreset: UploadPresetEntity? = null

    fun load(presetId: String?) {
        if (loadedPresetId == presetId && !_uiState.value.isLoading) return
        loadedPresetId = presetId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val configs = configRepository.observeAll().first()

            if (presetId.isNullOrBlank()) {
                originalPreset = null
                _uiState.value = PresetEditUiState(
                    configs = configs,
                    nasConfigId = configs.firstOrNull()?.id.orEmpty(),
                    isLoading = false
                )
                return@launch
            }

            val preset = presetRepository.getById(presetId)
            originalPreset = preset
            if (preset == null) {
                _uiState.update {
                    it.copy(configs = configs, isLoading = false, errorMessage = "上传预设不存在")
                }
                return@launch
            }

            val options = UploadPresetOptionsCodec.decode(preset.optionsJson)
            _uiState.value = PresetEditUiState(
                id = preset.id,
                configs = configs,
                nasConfigId = preset.nasConfigId,
                name = preset.name,
                sourceType = options.sourceType,
                localUri = preset.localUri,
                localLabel = preset.localLabel,
                remoteRoot = preset.remoteRoot,
                chunkSizeMb = options.chunkSizeMb.toString(),
                overwriteMode = options.overwriteMode,
                folderConflictMode = options.folderConflictMode,
                wifiOnly = options.wifiOnly,
                filterRegex = options.filterRegex.orEmpty(),
                deleteAfterUpload = options.deleteAfterUpload,
                isLoading = false
            )
        }
    }

    fun updateNasConfigId(value: String) = update { copy(nasConfigId = value, errorMessage = null) }
    fun updateName(value: String) = update { copy(name = value, errorMessage = null) }
    fun updateSourceType(value: String) = update {
        copy(sourceType = value, localUri = "", localLabel = "", errorMessage = null)
    }
    fun updateRemoteRoot(value: String) = update { copy(remoteRoot = value, errorMessage = null) }
    fun updateChunkSizeMb(value: String) = update {
        copy(chunkSizeMb = value.filter { it.isDigit() }, errorMessage = null)
    }
    fun updateOverwriteMode(value: String) = update { copy(overwriteMode = value) }
    fun updateFolderConflictMode(value: String) = update { copy(folderConflictMode = value) }
    fun updateWifiOnly(value: Boolean) = update { copy(wifiOnly = value) }
    fun updateFilterRegex(value: String) = update { copy(filterRegex = value, errorMessage = null) }
    fun updateDeleteAfterUpload(value: Boolean) = update { copy(deleteAfterUpload = value) }

    fun updateLocalUri(localUri: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val metadata = uploadTaskCreator.describeLocalUri(localUri)
                    uploadTaskCreator.persistReadPermission(localUri)
                    metadata
                }
            }.onSuccess { metadata ->
                _uiState.update {
                    it.copy(
                        sourceType = "file",
                        localUri = localUri,
                        localLabel = metadata.name,
                        name = it.name.ifBlank { metadata.name.substringBeforeLast('.') },
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "无法读取本地文件") }
            }
        }
    }

    fun updateLocalTreeUri(localUri: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val metadata = uploadTaskCreator.describeLocalTree(localUri)
                    uploadTaskCreator.persistReadPermission(localUri)
                    metadata
                }
            }.onSuccess { metadata ->
                _uiState.update {
                    it.copy(
                        sourceType = "folder",
                        localUri = localUri,
                        localLabel = metadata.name,
                        name = it.name.ifBlank { metadata.name },
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "无法读取本地文件夹") }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val validationError = validate()
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val state = _uiState.value
            val now = System.currentTimeMillis()
            val options = UploadPresetOptions(
                sourceType = state.sourceType,
                chunkSizeMb = state.chunkSizeMb.toIntOrNull()?.coerceIn(1, 128) ?: 8,
                overwriteMode = state.overwriteMode,
                folderConflictMode = state.folderConflictMode,
                wifiOnly = state.wifiOnly,
                filterRegex = state.filterRegex.ifBlank { null },
                deleteAfterUpload = state.deleteAfterUpload
            )
            val preset = UploadPresetEntity(
                id = state.id ?: originalPreset?.id ?: UUID.randomUUID().toString(),
                nasConfigId = state.nasConfigId,
                name = state.name.trim(),
                localUri = state.localUri,
                localLabel = state.localLabel,
                remoteRoot = state.remoteRoot.ifBlank { "/" },
                optionsJson = UploadPresetOptionsCodec.encode(options),
                createdAt = originalPreset?.createdAt ?: now,
                updatedAt = now,
                lastRunAt = originalPreset?.lastRunAt
            )

            runCatching { presetRepository.upsert(preset) }
                .onSuccess { onSaved() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.message ?: "保存预设失败")
                    }
                }
        }
    }

    private fun validate(): String? {
        val state = _uiState.value
        if (state.configs.isEmpty()) return "请先创建 NAS 连接"
        if (state.nasConfigId.isBlank()) return "请选择连接"
        if (state.name.isBlank()) return "请输入预设名称"
        if (state.localUri.isBlank()) {
            return if (state.sourceType == "folder") "请选择本地文件夹" else "请选择本地文件"
        }
        if (state.remoteRoot.isBlank()) return "请输入远端目录"
        val chunkSize = state.chunkSizeMb.toIntOrNull()
        if (chunkSize == null || chunkSize !in 1..128) return "分块大小必须在 1 到 128 MB 之间"
        if (state.filterRegex.isNotBlank() && runCatching { Regex(state.filterRegex) }.isFailure) {
            return "过滤正则格式不正确"
        }
        return null
    }

    private fun update(block: PresetEditUiState.() -> PresetEditUiState) {
        _uiState.update { it.block() }
    }
}
