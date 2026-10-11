package com.nastools.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.presentation.browser.FileTabSession
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn


data class HomeUiState(
    val configs: List<NasConfigEntity> = emptyList(),
    val activeTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: NasConfigRepository,
    private val taskRepository: TaskRepository,
    private val fileTabSession: FileTabSession
) : ViewModel() {

    /** 首页卡片点击 = 明确选择连接，文件标签页据此恢复目录 */
    fun selectConnection(configId: String) {
        fileTabSession.select(configId)
    }

    /**
     * 删除连接。上传预设通过 Room 外键 CASCADE 一并删除（见 UploadPresetEntity）
     * ；文件标签会话的选中清理由 FilesScreen 观察配置列表后 clearSelection() 完成。
     */
    fun delete(configId: String) {
        viewModelScope.launch {
            configRepository.deleteById(configId)
        }
    }

    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<HomeUiState> = reloadRequests
        .onStart { emit(Unit) }
        .flatMapLatest {
            combine(
                configRepository.observeAll(),
                taskRepository.observeActive()
            ) { configs, tasks ->
                HomeUiState(configs = configs, activeTasks = tasks, isLoading = false)
            }
                .onStart { emit(HomeUiState()) }
                .catch { error ->
                    emit(
                        HomeUiState(
                            isLoading = false,
                            loadErrorMessage = error.message ?: "加载首页失败"
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    fun reload() {
        reloadRequests.tryEmit(Unit)
    }
}
