package com.nastools.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nastools.app.data.database.entity.NasConfigEntity
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HomeUiState(
    val configs: List<NasConfigEntity> = emptyList(),
    val activeTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: NasConfigRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        configRepository.observeAll(),
        taskRepository.observeActive()
    ) { configs, tasks ->
        HomeUiState(configs = configs, activeTasks = tasks, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
