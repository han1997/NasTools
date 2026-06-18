package com.nastools.app.presentation.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.network.StorageAdapterFactory
import com.nastools.app.data.repository.NasConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigEditUiState(
    val id: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val defaultRemotePath: String = "/",
    val trustSelfSigned: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val errorMessage: String? = null,
    val testMessage: String? = null
)

@HiltViewModel
class ConfigEditViewModel @Inject constructor(
    private val repository: NasConfigRepository,
    private val adapterFactory: StorageAdapterFactory
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConfigEditUiState())
    val uiState: StateFlow<ConfigEditUiState> = _uiState.asStateFlow()

    private var loadedConfigId: String? = null
    private var originalConfig: NasConfigEntity? = null

    fun load(configId: String?) {
        if (loadedConfigId == configId && !_uiState.value.isLoading) return
        loadedConfigId = configId

        if (configId.isNullOrBlank()) {
            originalConfig = null
            _uiState.value = ConfigEditUiState(defaultRemotePath = "/")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val config = repository.getById(configId)
            originalConfig = config
            if (config == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "连接配置不存在")
                }
                return@launch
            }

            _uiState.value = ConfigEditUiState(
                id = config.id,
                name = config.name,
                baseUrl = config.baseUrl,
                username = config.username,
                password = config.password,
                defaultRemotePath = config.defaultRemotePath ?: "/",
                trustSelfSigned = config.trustSelfSigned,
                isLoading = false
            )
        }
    }

    fun updateName(value: String) = update { copy(name = value, errorMessage = null) }
    fun updateBaseUrl(value: String) = update { copy(baseUrl = value, errorMessage = null) }
    fun updateUsername(value: String) = update { copy(username = value, errorMessage = null) }
    fun updatePassword(value: String) = update { copy(password = value, errorMessage = null) }
    fun updateDefaultRemotePath(value: String) = update { copy(defaultRemotePath = value, errorMessage = null) }
    fun updateTrustSelfSigned(value: Boolean) = update { copy(trustSelfSigned = value) }

    fun testConnection() {
        val validationError = validate()
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, testMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, testMessage = null) }
            val result = runCatching {
                adapterFactory.create(buildEntity()).ping()
            }
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testMessage = if (result.isSuccess) "连接测试成功" else null,
                    errorMessage = result.exceptionOrNull()?.message
                )
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
            val entity = buildEntity()
            runCatching {
                if (originalConfig == null) repository.insert(entity) else repository.update(entity)
            }.onSuccess {
                onSaved()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = error.message ?: "保存失败")
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _uiState.value.id ?: return
        viewModelScope.launch {
            runCatching { repository.deleteById(id) }
                .onSuccess { onDeleted() }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "删除失败") }
                }
        }
    }

    private fun validate(): String? {
        val state = _uiState.value
        if (state.name.isBlank()) return "请输入连接名称"
        if (state.baseUrl.isBlank()) return "请输入 WebDAV 地址"
        val uri = runCatching { URI(state.baseUrl.trim()) }.getOrNull()
        if (uri?.scheme !in listOf("http", "https") || uri?.host.isNullOrBlank()) {
            return "WebDAV 地址必须是 http 或 https URL"
        }
        return null
    }

    private fun buildEntity(): NasConfigEntity {
        val state = _uiState.value
        val original = originalConfig
        return NasConfigEntity(
            id = state.id ?: original?.id ?: UUID.randomUUID().toString(),
            name = state.name.trim(),
            type = "webdav",
            baseUrl = state.baseUrl.trim().trimEnd('/'),
            username = state.username.trim(),
            password = state.password,
            trustSelfSigned = state.trustSelfSigned,
            defaultRemotePath = state.defaultRemotePath.ifBlank { "/" },
            extraJson = original?.extraJson,
            createdAt = original?.createdAt ?: System.currentTimeMillis()
        )
    }

    private fun update(block: ConfigEditUiState.() -> ConfigEditUiState) {
        _uiState.update { it.block() }
    }
}
