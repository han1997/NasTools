package com.nastools.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_presets",
    foreignKeys = [
        ForeignKey(
            entity = NasConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["nasConfigId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["nasConfigId"])]
)
data class UploadPresetEntity(
    @PrimaryKey
    val id: String,
    val nasConfigId: String,
    val name: String,
    val localUri: String,
    val localLabel: String,
    val remoteRoot: String,
    val optionsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null
)
