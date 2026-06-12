package com.nastools.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["taskId"])
    ]
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "trace", "debug", "info", "warn", "error"
    val tag: String,
    val message: String,
    val taskId: String? = null
)
