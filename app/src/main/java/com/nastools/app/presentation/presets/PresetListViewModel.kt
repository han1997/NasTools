package com.nastools.app.presentation.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.UploadPresetEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.UploadPresetRepository
import com.nastools.app.domain.model.UploadPresetOptionsCodec
import com.nastools.app.service.UploadTaskCreator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetListUiState(
    val presets: List<UploadPresetEntity> = emptyList(),
    val configs: List<NasConfigEntity> = emptyList(),
    val message: String? = null,
    val errorMessage: String? = null
) {
    val configNames: Map<String, String> = configs.associate { it.id to it.name }
}

private data class PresetListTransient(
    val message: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PresetListViewModel @Inject constructor(
    private val presetRepository: UploadPresetRepository,
    private val configRepository: NasConfigRepository,
    private val uploadTaskCreator: UploadTaskCreator
) : ViewModel() {
    private val transient = MutableStateFlow(PresetListTransient())

    val uiState: StateFlow<PresetListUiState> = combine(
        presetRepository.observeAll(),
        configRepository.observeAll(),
        transient
    ) { presets, configs, transient ->
        PresetListUiState(
            presets = presets,
            configs = configs,
            message = transient.message,
            errorMessage = transient.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PresetListUiState())

    fun runPreset(id: String) {
        viewModelScope.launch {
            runCatching {
                val preset = presetRepository.getById(id) ?: error("上传预设不存在")
                if (preset.localUri.isBlank()) error("预设缺少本地文件")
                val options = UploadPresetOptionsCodec.decode(preset.optionsJson)
                if (options.sourceType == "folder") {
                    uploadTaskCreator.enqueueFolderUpload(
                        nasConfigId = preset.nasConfigId,
                        localUri = preset.localUri,
                        remoteDirectory = preset.remoteRoot,
                        options = options
                    )
                } else {
                    uploadTaskCreator.enqueueFileUpload(
                        nasConfigId = preset.nasConfigId,
                        localUri = preset.localUri,
                        remoteDirectory = preset.remoteRoot,
                        options = options
                    )
                }
                presetRepository.touchLastRun(id)
            }.onSuccess {
                transient.value = PresetListTransient(message = "已加入上传任务")
            }.onFailure { error ->
                transient.value = PresetListTransient(errorMessage = error.message ?: "运行预设失败")
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            runCatching { presetRepository.deleteById(id) }
                .onSuccess {
                    transient.value = PresetListTransient(message = "预设已删除")
                }
                .onFailure { error ->
                    transient.value = PresetListTransient(errorMessage = error.message ?: "删除预设失败")
                }
        }
    }

    fun clearTransientMessage() {
        transient.update { PresetListTransient() }
    }
}
