package com.nastools.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.BuildConfig
import com.nastools.app.data.backup.BackupRepository
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.domain.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 用户在「备份与恢复」页选择的动作 */
enum class BackupAction { Backup, Restore }

/**
 * 页面状态机(design.md §6):
 * 空闲 → 选择连接 → (备份)确认 → 进行中 → 成功/失败;(恢复)→ 进行中 → 成功/失败。
 */
sealed interface BackupPhase {
    data object Idle : BackupPhase

    /** 等待用户选择目标连接 */
    data class SelectConnection(val action: BackupAction) : BackupPhase

    /** 备份前风险确认(含明文密码告知,D4/R5) */
    data class ConfirmBackup(val config: NasConfigEntity) : BackupPhase

    data class Running(val action: BackupAction) : BackupPhase

    /** 备份成功,携带远端完整路径 */
    data class BackupDone(val remotePath: String) : BackupPhase

    data class RestoreDone(val result: BackupResult) : BackupPhase

    /** 失败态:保留动作与目标连接,供重试(AC8) */
    data class Failed(
        val action: BackupAction,
        val config: NasConfigEntity,
        val message: String
    ) : BackupPhase
}

data class BackupUiState(
    /** null 表示连接列表尚未加载完成 */
    val configs: List<NasConfigEntity>? = null,
    val phase: BackupPhase = BackupPhase.Idle
) {
    val isLoadingConfigs: Boolean get() = configs == null
    val isRunning: Boolean get() = phase is BackupPhase.Running
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    configRepository: NasConfigRepository
) : ViewModel() {

    private val phase = MutableStateFlow<BackupPhase>(BackupPhase.Idle)

    val uiState: StateFlow<BackupUiState> = combine(
        configRepository.observeAll().mapToNullable(),
        phase
    ) { configs, phase ->
        BackupUiState(configs = configs, phase = phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    /** 点「备份到 WebDAV」/「从 WebDAV 恢复」 */
    fun start(action: BackupAction) {
        if (phase.value is BackupPhase.Running) return
        phase.value = BackupPhase.SelectConnection(action)
    }

    /** 选定目标连接:备份先弹风险确认,恢复直接执行 */
    fun pickConfig(config: NasConfigEntity) {
        if (phase.value is BackupPhase.Running) return
        val current = phase.value
        val action = (current as? BackupPhase.SelectConnection)?.action ?: return
        if (action == BackupAction.Backup) {
            phase.value = BackupPhase.ConfirmBackup(config)
        } else {
            runRestore(config)
        }
    }

    /** 确认备份(风险告知后) */
    fun confirmBackup() {
        val confirm = phase.value as? BackupPhase.ConfirmBackup ?: return
        runBackup(confirm.config)
    }

    /** 取消备份确认 */
    fun cancelConfirm() {
        if (phase.value !is BackupPhase.ConfirmBackup) return
        phase.value = BackupPhase.SelectConnection(BackupAction.Backup)
    }

    /** 失败后重试同一动作与同一连接 */
    fun retry() {
        val failed = phase.value as? BackupPhase.Failed ?: return
        when (failed.action) {
            BackupAction.Backup -> runBackup(failed.config)
            BackupAction.Restore -> runRestore(failed.config)
        }
    }

    /** 返回动作选择(空闲态) */
    fun backToIdle() {
        if (phase.value is BackupPhase.Running) return
        phase.value = BackupPhase.Idle
    }

    private fun runBackup(config: NasConfigEntity) {
        if (phase.value is BackupPhase.Running) return
        phase.value = BackupPhase.Running(BackupAction.Backup)
        viewModelScope.launch {
            backupRepository.backup(config.id, BuildConfig.VERSION_NAME)
                .onSuccess { remotePath ->
                    phase.value = BackupPhase.BackupDone(remotePath)
                }
                .onFailure { error ->
                    phase.value = BackupPhase.Failed(
                        action = BackupAction.Backup,
                        config = config,
                        message = error.message ?: "备份失败"
                    )
                }
        }
    }

    private fun runRestore(config: NasConfigEntity) {
        if (phase.value is BackupPhase.Running) return
        phase.value = BackupPhase.Running(BackupAction.Restore)
        viewModelScope.launch {
            backupRepository.restore(config.id)
                .onSuccess { result ->
                    phase.value = BackupPhase.RestoreDone(result)
                }
                .onFailure { error ->
                    phase.value = BackupPhase.Failed(
                        action = BackupAction.Restore,
                        config = config,
                        message = error.message ?: "恢复失败"
                    )
                }
        }
    }
}

/** 观察列表无法区分「加载中」与「空」,这里保持 null 语义(与 FilesViewModel 一致) */
private fun <T> Flow<List<T>>.mapToNullable(): Flow<List<T>?> = map { it }