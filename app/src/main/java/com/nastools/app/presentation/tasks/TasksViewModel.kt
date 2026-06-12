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
    val failedTasks: List<TaskEntity> = emptyList()
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val taskManager: TaskManager
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = combine(
        repository.observeByStatus(listOf("waiting", "running", "paused")),
        repository.observeByStatus(listOf("completed")),
        repository.observeByStatus(listOf("failed", "cancelled"))
    ) { active, completed, failed ->
        TasksUiState(active, completed, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun pauseTask(id: String) = viewModelScope.launch { taskManager.pause(id) }
    fun resumeTask(id: String) = viewModelScope.launch { taskManager.resume(id) }
    fun cancelTask(id: String) = viewModelScope.launch { taskManager.cancel(id) }
    fun retryTask(id: String) = viewModelScope.launch { taskManager.retry(id) }
    fun deleteTask(id: String) = viewModelScope.launch { repository.deleteById(id) }
}
