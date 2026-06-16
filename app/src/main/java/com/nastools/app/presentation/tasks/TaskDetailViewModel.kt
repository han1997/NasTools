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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    val uiState: StateFlow<TaskDetailUiState> = repository.observeById(taskId)
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
        .catch { emit(TaskDetailUiState.Error(it.message ?: "加载失败")) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TaskDetailUiState.Loading
        )

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
        if (depth > 10) return // Prevent infinite recursion

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
            // Ignore errors during scanning
        }
    }

    fun deleteTask() = viewModelScope.launch {
        repository.deleteById(taskId)
    }

    fun retryTask() = viewModelScope.launch {
        taskManager.retry(taskId)
    }
}
