package com.nastools.app.presentation.tasks

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.domain.model.UploadTaskPayload
import com.nastools.app.service.TaskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileItem(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val depth: Int
)

sealed class TaskDetailUiState {
    object Loading : TaskDetailUiState()
    data class Success(
        val task: TaskEntity,
        val configName: String?,
        val files: List<FileItem>,
        val sourceDeleted: Boolean
    ) : TaskDetailUiState()
    data class Error(val message: String) : TaskDetailUiState()
}

data class TaskDetailFeedback(
    val message: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val configRepository: NasConfigRepository,
    private val taskManager: TaskManager
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])
    private val gson = Gson()
    private val reloadRequests = MutableStateFlow(0)
    private val _feedback = MutableStateFlow(TaskDetailFeedback())
    val feedback: StateFlow<TaskDetailFeedback> = _feedback.asStateFlow()

    val uiState: StateFlow<TaskDetailUiState> = reloadRequests
        .flatMapLatest {
            repository.observeById(taskId)
                .map { task ->
                    if (task == null) {
                        TaskDetailUiState.Error("任务不存在")
                    } else {
                        val configName = task.nasConfigId?.let { configRepository.getById(it)?.name }
                        val payload = try {
                            gson.fromJson(task.payloadJson, UploadTaskPayload::class.java)
                        } catch (e: Exception) {
                            null
                        }

                        val (files, sourceDeleted) = if (payload != null) {
                            scanFiles(payload)
                        } else {
                            Pair(emptyList(), false)
                        }

                        TaskDetailUiState.Success(
                            task = task,
                            configName = configName,
                            files = files,
                            sourceDeleted = sourceDeleted
                        )
                    }
                }
                .catch { emit(TaskDetailUiState.Error(it.message ?: "加载任务详情失败")) }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskDetailUiState.Loading
        )

    fun reload() {
        reloadRequests.update { it + 1 }
    }

    private fun scanFiles(payload: UploadTaskPayload): Pair<List<FileItem>, Boolean> {
        val uri = Uri.parse(payload.localUri)
        val sourceType = payload.sourceType.ifBlank { payload.options?.sourceType ?: "file" }

        return try {
            val files = mutableListOf<FileItem>()

            if (sourceType == "folder") {
                val tree = DocumentFile.fromTreeUri(context, uri)
                if (tree == null || !tree.exists()) {
                    return Pair(emptyList(), true)
                }
                scanDirectory(tree, files, depth = 0)
            } else {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc == null || !doc.exists()) {
                    return Pair(emptyList(), true)
                }
                val name = doc.name ?: payload.localName.ifBlank { "未知文件" }
                val size = doc.length()
                files.add(FileItem(name, size, false, 0))
            }

            Pair(files, false)
        } catch (e: Exception) {
            Pair(emptyList(), true)
        }
    }

    private fun scanDirectory(directory: DocumentFile, result: MutableList<FileItem>, depth: Int) {
        if (depth > 10) return

        try {
            directory.listFiles().forEach { file ->
                val name = file.name ?: "未知"
                if (file.isDirectory) {
                    result.add(FileItem(name, 0, true, depth))
                    scanDirectory(file, result, depth + 1)
                } else {
                    result.add(FileItem(name, file.length(), false, depth))
                }
            }
        } catch (e: Exception) {
            // Ignore errors during scanning; the source is shown as unavailable below.
        }
    }

    fun deleteTask(onDeleted: () -> Unit) = viewModelScope.launch {
        try {
            repository.deleteById(taskId)
            onDeleted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _feedback.value = TaskDetailFeedback(errorMessage = e.message ?: "删除任务失败")
        }
    }

    fun retryTask(onRetried: () -> Unit) = viewModelScope.launch {
        try {
            taskManager.retry(taskId)
            onRetried()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _feedback.value = TaskDetailFeedback(errorMessage = e.message ?: "重试任务失败")
        }
    }

    fun clearFeedback() {
        _feedback.value = TaskDetailFeedback()
    }
}
