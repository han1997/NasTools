package com.nastools.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["moduleId", "type"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val moduleId: String,
    val type: String,
    val nasConfigId: String? = null,
    val status: String, // "waiting", "running", "paused", "completed", "failed", "cancelled"
    val progressBytes: Long = 0,
    val totalBytes: Long = 0,
    val title: String,
    val payloadJson: String,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
