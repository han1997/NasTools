package com.nastools.app.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.service.TaskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class TasksUiState(
    val activeTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val failedTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet(),
    val message: String? = null,
    val errorMessage: String? = null
)

private data class TaskLists(
    val activeTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val failedTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null
)

private data class TaskTransient(
    val message: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val taskManager: TaskManager
) : ViewModel() {

    private val _selectionState = MutableStateFlow(SelectionState())
    private val transient = MutableStateFlow(TaskTransient())
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val taskLists = reloadRequests
        .onStart { emit(Unit) }
        .flatMapLatest {
            combine(
                repository.observeByStatus(listOf("waiting", "running", "paused")),
                repository.observeByStatus(listOf("completed")),
                repository.observeByStatus(listOf("failed", "cancelled"))
            ) { active, completed, failed ->
                TaskLists(
                    activeTasks = active,
                    completedTasks = completed,
                    failedTasks = failed,
                    isLoading = false
                )
            }
                .onStart { emit(TaskLists()) }
                .catch { error ->
                    emit(
                        TaskLists(
                            isLoading = false,
                            loadErrorMessage = error.message ?: "加载任务列表失败"
                        )
                    )
                }
        }

    val uiState: StateFlow<TasksUiState> = combine(
        taskLists,
        _selectionState,
        transient
    ) { lists, selection, feedback ->
        TasksUiState(
            activeTasks = lists.activeTasks,
            completedTasks = lists.completedTasks,
            failedTasks = lists.failedTasks,
            isLoading = lists.isLoading,
            loadErrorMessage = lists.loadErrorMessage,
            isSelectionMode = selection.isSelectionMode,
            selectedTaskIds = selection.selectedTaskIds,
            message = feedback.message,
            errorMessage = feedback.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun reload() {
        reloadRequests.tryEmit(Unit)
    }

    fun pauseTask(id: String) = runTaskAction("任务已暂停") { taskManager.pause(id) }
    fun resumeTask(id: String) = runTaskAction("任务已恢复") { taskManager.resume(id) }
    fun cancelTask(id: String) = runTaskAction("任务已取消") { taskManager.cancel(id) }
    fun retryTask(id: String) = runTaskAction("任务已加入重试队列") { taskManager.retry(id) }
    fun deleteTask(id: String) = runTaskAction("任务已删除") { repository.deleteById(id) }

    fun enterSelectionMode() {
        _selectionState.update { it.copy(isSelectionMode = true, selectedTaskIds = emptySet()) }
    }

    fun exitSelectionMode() {
        _selectionState.update { SelectionState() }
    }

    fun toggleTaskSelection(taskId: String) {
        _selectionState.update { state ->
            val newSelection = if (taskId in state.selectedTaskIds) {
                state.selectedTaskIds - taskId
            } else {
                state.selectedTaskIds + taskId
            }
            state.copy(selectedTaskIds = newSelection)
        }
    }

    fun selectAll(taskIds: List<String>) {
        _selectionState.update { it.copy(selectedTaskIds = taskIds.toSet()) }
    }

    fun clearSelection() {
        _selectionState.update { it.copy(selectedTaskIds = emptySet()) }
    }

    fun batchDelete(taskIds: Set<String>) = viewModelScope.launch {
        runCatching { repository.deleteByIds(taskIds.toList()) }
            .onSuccess {
                transient.value = TaskTransient(message = "已删除 ${taskIds.size} 个任务")
                exitSelectionMode()
            }
            .onFailure { error ->
                handleFailure(error)
            }
    }

    fun clearTransientMessage() {
        transient.value = TaskTransient()
    }

    private fun runTaskAction(successMessage: String, action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }
            .onSuccess { transient.value = TaskTransient(message = successMessage) }
            .onFailure { error -> handleFailure(error) }
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) throw error
        transient.value = TaskTransient(errorMessage = error.message ?: "任务操作失败")
    }

    private data class SelectionState(
        val isSelectionMode: Boolean = false,
        val selectedTaskIds: Set<String> = emptySet()
    )
}
