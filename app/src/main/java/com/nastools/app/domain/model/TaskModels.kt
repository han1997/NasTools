package com.nastools.app.domain.model

sealed class TaskResult {
    object Completed : TaskResult()
    object Cancelled : TaskResult()
    object Paused : TaskResult()
    data class Failed(val error: String) : TaskResult()
}

data class TaskDescriptor(
    val id: String,
    val moduleId: String,
    val type: String,
    val nasConfigId: String?,
    val title: String,
    val totalBytes: Long,
    val payload: Map<String, Any>,
    val priority: Int = 0
)
