package com.nastools.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.domain.model.UploadTaskPayload
import com.nastools.app.util.RemotePath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LocalFileMetadata(
    val name: String,
    val size: Long
)

@Singleton
class UploadTaskCreator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository
) {
    private val gson = Gson()

    suspend fun enqueueFileUpload(
        nasConfigId: String,
        localUri: String,
        remoteDirectory: String,
        options: UploadPresetOptions = UploadPresetOptions()
    ): String {
        persistReadPermission(localUri)

        val metadata = describeLocalUri(localUri)
        val remotePath = RemotePath.join(remoteDirectory, metadata.name)
        val payload = UploadTaskPayload(
            localUri = localUri,
            localName = metadata.name,
            remotePath = remotePath,
            totalBytes = metadata.size,
            options = options
        )
        val taskId = UUID.randomUUID().toString()

        taskRepository.insert(
            TaskEntity(
                id = taskId,
                moduleId = "upload",
                type = "file",
                nasConfigId = nasConfigId,
                status = "waiting",
                progressBytes = 0,
                totalBytes = metadata.size,
                title = "上传 ${metadata.name}",
                payloadJson = gson.toJson(payload)
            )
        )
        NasForegroundService.start(context)
        return taskId
    }

    fun persistReadPermission(localUri: String) {
        val uri = Uri.parse(localUri)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun describeLocalUri(localUri: String): LocalFileMetadata {
        return queryMetadata(Uri.parse(localUri))
    }

    private fun queryMetadata(uri: Uri): LocalFileMetadata {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "upload.bin"
        var size = 0L

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)?.ifBlank { name } ?: name
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                }
            }
        }

        if (size <= 0) {
            size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0 } ?: 0L
                } ?: 0L
            }.getOrDefault(0L)
        }

        return LocalFileMetadata(name = name, size = size)
    }
}
