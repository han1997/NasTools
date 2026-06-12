package com.nastools.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
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
    val size: Long,
    val sourceType: String = "file"
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
        val payload = UploadTaskPayload(
            sourceType = "file",
            localUri = localUri,
            localName = metadata.name,
            remotePath = RemotePath.join(remoteDirectory, metadata.name),
            totalBytes = metadata.size,
            options = options.copy(sourceType = "file")
        )

        return enqueueUpload(
            nasConfigId = nasConfigId,
            type = "file",
            title = "上传 ${metadata.name}",
            totalBytes = metadata.size,
            payload = payload
        )
    }

    suspend fun enqueueFolderUpload(
        nasConfigId: String,
        localUri: String,
        remoteDirectory: String,
        options: UploadPresetOptions = UploadPresetOptions()
    ): String {
        persistReadPermission(localUri)

        val metadata = describeLocalTree(localUri)
        val payload = UploadTaskPayload(
            sourceType = "folder",
            localUri = localUri,
            localName = metadata.name,
            remotePath = RemotePath.join(remoteDirectory, metadata.name),
            totalBytes = metadata.size,
            options = options.copy(sourceType = "folder")
        )

        return enqueueUpload(
            nasConfigId = nasConfigId,
            type = "folder",
            title = "上传文件夹 ${metadata.name}",
            totalBytes = metadata.size,
            payload = payload
        )
    }

    fun persistReadPermission(localUri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(localUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun describeLocalUri(localUri: String): LocalFileMetadata {
        return queryFileMetadata(Uri.parse(localUri))
    }

    fun describeLocalTree(localUri: String): LocalFileMetadata {
        val uri = Uri.parse(localUri)
        val root = DocumentFile.fromTreeUri(context, uri)
        val name = root?.name
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "folder"
        val size = root?.let { sumFileSizes(it) } ?: 0L
        return LocalFileMetadata(name = name, size = size, sourceType = "folder")
    }

    private suspend fun enqueueUpload(
        nasConfigId: String,
        type: String,
        title: String,
        totalBytes: Long,
        payload: UploadTaskPayload
    ): String {
        val taskId = UUID.randomUUID().toString()
        taskRepository.insert(
            TaskEntity(
                id = taskId,
                moduleId = "upload",
                type = type,
                nasConfigId = nasConfigId,
                status = "waiting",
                progressBytes = 0,
                totalBytes = totalBytes,
                title = title,
                payloadJson = gson.toJson(payload)
            )
        )
        NasForegroundService.start(context)
        return taskId
    }

    private fun queryFileMetadata(uri: Uri): LocalFileMetadata {
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

        return LocalFileMetadata(name = name, size = size, sourceType = "file")
    }

    private fun sumFileSizes(file: DocumentFile): Long {
        if (file.isFile) return file.length().coerceAtLeast(0)
        return file.listFiles().sumOf { sumFileSizes(it) }
    }
}
