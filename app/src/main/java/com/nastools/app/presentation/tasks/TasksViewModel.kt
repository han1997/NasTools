package com.nastools.app.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.service.TaskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val activeTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val failedTasks: List<TaskEntity> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet()
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val taskManager: TaskManager
) : ViewModel() {

    private val _selectionState = MutableStateFlow(SelectionState())

    val uiState: StateFlow<TasksUiState> = combine(
        repository.observeByStatus(listOf("waiting", "running", "paused")),
        repository.observeByStatus(listOf("completed")),
        repository.observeByStatus(listOf("failed", "cancelled")),
        _selectionState
    ) { active, completed, failed, selection ->
        TasksUiState(
            activeTasks = active,
            completedTasks = completed,
            failedTasks = failed,
            isSelectionMode = selection.isSelectionMode,
            selectedTaskIds = selection.selectedTaskIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun pauseTask(id: String) = viewModelScope.launch { taskManager.pause(id) }
    fun resumeTask(id: String) = viewModelScope.launch { taskManager.resume(id) }
    fun cancelTask(id: String) = viewModelScope.launch { taskManager.cancel(id) }
    fun retryTask(id: String) = viewModelScope.launch { taskManager.retry(id) }
    fun deleteTask(id: String) = viewModelScope.launch { repository.deleteById(id) }

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
        repository.deleteByIds(taskIds.toList())
        exitSelectionMode()
    }

    private data class SelectionState(
        val isSelectionMode: Boolean = false,
        val selectedTaskIds: Set<String> = emptySet()
    )
}
