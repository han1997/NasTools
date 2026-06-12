package com.nastools.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nas_configs",
    indices = [Index(value = ["name"])]
)
data class NasConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "webdav", "sftp", "smb"
    val baseUrl: String,
    val username: String,
    val passwordEncrypted: String,
    val trustSelfSigned: Boolean = false,
    val defaultRemotePath: String? = null,
    val extraJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
