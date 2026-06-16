package com.nastools.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.nastools.app.data.database.entity.TaskEntity
import com.nastools.app.data.network.StorageAdapter
import com.nastools.app.data.network.StorageAdapterFactory
import com.nastools.app.data.repository.NasConfigRepository
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.domain.model.UploadTaskPayload
import com.nastools.app.util.RemotePath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class UploadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: NasConfigRepository,
    private val adapterFactory: StorageAdapterFactory
) {
    private val gson = Gson()

    suspend fun execute(
        task: TaskEntity,
        onProgress: suspend (uploadedBytes: Long, totalBytes: Long) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val configId = task.nasConfigId ?: throw IOException("上传任务缺少 NAS 配置")
        val config = configRepository.getById(configId) ?: throw IOException("NAS 配置不存在")
        val payload = decodePayload(task.payloadJson)
        if (payload.localUri.isBlank()) throw IOException("上传任务缺少本地来源")

        val options = payload.options ?: UploadPresetOptions(sourceType = payload.sourceType)
        if (options.wifiOnly && !isWifiConnected()) {
            throw IOException("当前不是 Wi-Fi 网络")
        }

        val adapter = adapterFactory.create(config)
        val totalBytes = payload.totalBytes.takeIf { it > 0 } ?: task.totalBytes
        val sourceType = payload.sourceType.ifBlank { options.sourceType }

        val warnings = mutableListOf<String>()

        if (sourceType == "folder") {
            uploadFolder(
                adapter = adapter,
                payload = payload,
                options = options.copy(sourceType = "folder"),
                totalBytes = totalBytes,
                warnings = warnings,
                onProgress = onProgress
            )
        } else {
            var completedBytes = 0L
            uploadFile(
                adapter = adapter,
                uri = Uri.parse(payload.localUri),
                localDocument = DocumentFile.fromSingleUri(context, Uri.parse(payload.localUri)),
                localName = payload.localName.ifBlank { RemotePath.basename(payload.remotePath) },
                remotePath = RemotePath.normalize(payload.remotePath),
                fileSize = totalBytes,
                options = options.copy(sourceType = "file"),
                totalTaskBytes = totalBytes,
                completedBytes = { completedBytes },
                updateCompletedBytes = { completedBytes = it },
                warnings = warnings,
                onProgress = onProgress
            )
        }

        return@withContext if (warnings.isEmpty()) null else "上传完成，${warnings.size} 个本地项目未能删除"
    }

    private suspend fun uploadFolder(
        adapter: StorageAdapter,
        payload: UploadTaskPayload,
        options: UploadPresetOptions,
        totalBytes: Long,
        warnings: MutableList<String>,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(payload.localUri))
            ?: throw IOException("无法打开本地文件夹")
        val rootName = payload.localName.ifBlank { root.name ?: "folder" }
        val rootPath = RemotePath.normalize(payload.remotePath.ifBlank { "/$rootName" })
        val remoteRoot = prepareFolderTarget(adapter, rootPath, options) ?: run {
            onProgress(totalBytes, totalBytes)
            return
        }

        var completedBytes = 0L
        uploadDirectory(
            adapter = adapter,
            directory = root,
            remoteDirectory = remoteRoot,
            options = options,
            totalTaskBytes = totalBytes,
            completedBytes = { completedBytes },
            updateCompletedBytes = { completedBytes = it },
            warnings = warnings,
            isRoot = true,
            onProgress = onProgress
        )
    }

    private suspend fun uploadDirectory(
        adapter: StorageAdapter,
        directory: DocumentFile,
        remoteDirectory: String,
        options: UploadPresetOptions,
        totalTaskBytes: Long,
        completedBytes: () -> Long,
        updateCompletedBytes: (Long) -> Unit,
        warnings: MutableList<String>,
        isRoot: Boolean = false,
        onProgress: suspend (Long, Long) -> Unit
    ): Boolean {
        adapter.mkdir(remoteDirectory)
        var canDeleteDirectory = options.deleteAfterUpload
        directory.listFiles().forEach { child ->
            currentCoroutineContext().ensureActive()
            val childName = child.name ?: run {
                canDeleteDirectory = false
                return@forEach
            }
            val desiredPath = RemotePath.join(remoteDirectory, childName)

            when {
                child.isDirectory -> {
                    val folderPath = prepareFolderTarget(adapter, desiredPath, options)
                    if (folderPath == null) {
                        canDeleteDirectory = false
                        markProgress(
                            bytes = sumFileSizes(child),
                            totalTaskBytes = totalTaskBytes,
                            completedBytes = completedBytes,
                            updateCompletedBytes = updateCompletedBytes,
                            onProgress = onProgress
                        )
                    } else {
                        val childMoved = uploadDirectory(
                            adapter = adapter,
                            directory = child,
                            remoteDirectory = folderPath,
                            options = options,
                            totalTaskBytes = totalTaskBytes,
                            completedBytes = completedBytes,
                            updateCompletedBytes = updateCompletedBytes,
                            warnings = warnings,
                            isRoot = false,
                            onProgress = onProgress
                        )
                        if (!childMoved) canDeleteDirectory = false
                    }
                }

                child.isFile -> {
                    val childMoved = uploadFile(
                        adapter = adapter,
                        uri = child.uri,
                        localDocument = child,
                        localName = childName,
                        remotePath = desiredPath,
                        fileSize = child.length().coerceAtLeast(0),
                        options = options,
                        totalTaskBytes = totalTaskBytes,
                        completedBytes = completedBytes,
                        updateCompletedBytes = updateCompletedBytes,
                        warnings = warnings,
                        onProgress = onProgress
                    )
                    if (!childMoved) canDeleteDirectory = false
                }

                else -> canDeleteDirectory = false
            }
        }

        if (isRoot) {
            return true
        }

        if (options.deleteAfterUpload && canDeleteDirectory) {
            val deleted = deleteLocalSource(
                document = directory,
                uri = directory.uri,
                description = "本地文件夹 ${directory.name ?: remoteDirectory}",
                warnings = warnings
            )
            return deleted
        }
        return !options.deleteAfterUpload
    }

    private suspend fun uploadFile(
        adapter: StorageAdapter,
        uri: Uri,
        localDocument: DocumentFile?,
        localName: String,
        remotePath: String,
        fileSize: Long,
        options: UploadPresetOptions,
        totalTaskBytes: Long,
        completedBytes: () -> Long,
        updateCompletedBytes: (Long) -> Unit,
        warnings: MutableList<String>,
        onProgress: suspend (Long, Long) -> Unit
    ): Boolean {
        val filterRegex = options.filterRegex?.takeIf { it.isNotBlank() }?.let {
            runCatching { Regex(it) }.getOrElse { throw IOException("过滤正则格式不正确") }
        }
        if (filterRegex != null && !filterRegex.containsMatchIn(localName)) {
            markProgress(fileSize, totalTaskBytes, completedBytes, updateCompletedBytes, onProgress)
            return false
        }

        RemotePath.parent(remotePath).takeIf { it != "/" }?.let { adapter.mkdir(it) }

        val plan = prepareFileTarget(adapter, remotePath, fileSize, options)
        if (plan.skip) {
            markProgress(fileSize, totalTaskBytes, completedBytes, updateCompletedBytes, onProgress)
            return false
        }

        val completedBeforeFile = completedBytes()
        val uploadedBytes = uploadFromUri(
            uri = uri,
            remotePath = plan.path,
            startOffset = plan.startOffset,
            totalBytes = fileSize,
            chunkSize = options.chunkSizeMb.coerceIn(1, 128) * 1024 * 1024,
            uploadChunk = { bytes, offset ->
                adapter.upload(
                    path = plan.path,
                    data = bytes,
                    offset = offset,
                    totalLength = fileSize.takeIf { it > 0 }
                )
            },
            onFileProgress = { fileProgress, _ ->
                onProgress(completedBeforeFile + fileProgress, totalTaskBytes)
            }
        )

        updateCompletedBytes(completedBeforeFile + (fileSize.takeIf { it > 0 } ?: uploadedBytes))

        if (options.deleteAfterUpload) {
            val deleted = deleteLocalSource(
                document = localDocument ?: DocumentFile.fromSingleUri(context, uri),
                uri = uri,
                description = "本地文件 $localName",
                warnings = warnings
            )
            return deleted
        }
        return true
    }

    private suspend fun prepareFolderTarget(
        adapter: StorageAdapter,
        desiredPath: String,
        options: UploadPresetOptions
    ): String? {
        val normalized = RemotePath.normalize(desiredPath)
        val stat = runCatching { adapter.stat(normalized) }.getOrNull()

        if (stat == null) {
            adapter.mkdir(normalized)
            return normalized
        }

        if (stat.isDirectory) {
            return when (options.folderConflictMode) {
                "rename" -> findAvailablePath(adapter, normalized, isDirectory = true).also { adapter.mkdir(it) }
                "skip" -> null
                "fail" -> throw IOException("远端文件夹已存在: $normalized")
                else -> normalized
            }
        }

        return when (options.folderConflictMode) {
            "rename" -> findAvailablePath(adapter, normalized, isDirectory = true).also { adapter.mkdir(it) }
            "skip" -> null
            else -> throw IOException("远端存在同名文件，无法创建文件夹: $normalized")
        }
    }

    private suspend fun prepareFileTarget(
        adapter: StorageAdapter,
        desiredPath: String,
        fileSize: Long,
        options: UploadPresetOptions
    ): FileUploadPlan {
        val normalized = RemotePath.normalize(desiredPath)
        val stat = runCatching { adapter.stat(normalized) }.getOrNull()
            ?: return FileUploadPlan(path = normalized, startOffset = 0L)

        if (stat.isDirectory) {
            return when (options.overwriteMode) {
                "rename" -> FileUploadPlan(findAvailablePath(adapter, normalized, isDirectory = false), 0L)
                else -> throw IOException("远端存在同名文件夹: $normalized")
            }
        }

        return when (options.overwriteMode) {
            "overwrite" -> FileUploadPlan(path = normalized, startOffset = 0L)
            "skip_existing" -> FileUploadPlan(path = normalized, startOffset = 0L, skip = true)
            "rename" -> FileUploadPlan(path = findAvailablePath(adapter, normalized, isDirectory = false), startOffset = 0L)
            "fail" -> throw IOException("远端文件已存在: $normalized")
            else -> {
                val remoteSize = stat.size ?: 0L
                when {
                    fileSize > 0 && remoteSize == fileSize -> FileUploadPlan(normalized, 0L, skip = true)
                    fileSize > 0 && remoteSize in 1 until fileSize -> FileUploadPlan(normalized, remoteSize)
                    else -> FileUploadPlan(normalized, 0L)
                }
            }
        }
    }

    private suspend fun findAvailablePath(
        adapter: StorageAdapter,
        desiredPath: String,
        isDirectory: Boolean
    ): String {
        val parent = RemotePath.parent(desiredPath)
        val name = RemotePath.basename(desiredPath)
        val dotIndex = if (!isDirectory) name.lastIndexOf('.') else -1
        val stem = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val extension = if (dotIndex > 0) name.substring(dotIndex) else ""

        for (index in 1..999) {
            val candidateName = "$stem ($index)$extension"
            val candidate = RemotePath.join(parent, candidateName)
            if (runCatching { adapter.stat(candidate) }.getOrNull() == null) {
                return candidate
            }
        }
        throw IOException("无法生成可用远端名称: $desiredPath")
    }

    private suspend fun uploadFromUri(
        uri: Uri,
        remotePath: String,
        startOffset: Long,
        totalBytes: Long,
        chunkSize: Int,
        uploadChunk: suspend (ByteArray, Long) -> Unit,
        onFileProgress: suspend (Long, Long) -> Unit
    ): Long {
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (totalBytes <= 0) {
                val bytes = input.readBytes()
                currentCoroutineContext().ensureActive()
                uploadChunk(bytes, 0L)
                onFileProgress(bytes.size.toLong(), bytes.size.toLong())
                return bytes.size.toLong()
            }

            skipFully(input, startOffset)
            var uploaded = startOffset
            onFileProgress(uploaded, totalBytes)

            val buffer = ByteArray(chunkSize)
            while (uploaded < totalBytes) {
                currentCoroutineContext().ensureActive()
                val readLimit = minOf(buffer.size.toLong(), totalBytes - uploaded).toInt()
                val read = input.read(buffer, 0, readLimit)
                if (read == -1) break

                val bytes = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                uploadChunk(bytes, uploaded)
                uploaded += read
                onFileProgress(uploaded.coerceAtMost(totalBytes), totalBytes)
            }
            return uploaded.coerceAtLeast(totalBytes)
        } ?: throw IOException("无法打开本地文件: $remotePath")
    }

    private suspend fun markProgress(
        bytes: Long,
        totalTaskBytes: Long,
        completedBytes: () -> Long,
        updateCompletedBytes: (Long) -> Unit,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val updated = completedBytes() + bytes.coerceAtLeast(0)
        updateCompletedBytes(updated)
        onProgress(updated, totalTaskBytes)
    }

    private fun deleteLocalSource(
        document: DocumentFile?,
        uri: Uri,
        description: String,
        warnings: MutableList<String>
    ): Boolean {
        val deleted = runCatching {
            if (document?.delete() == true) {
                true
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)

        if (!deleted) {
            warnings.add(description)
        }
        return deleted
    }

    private fun decodePayload(json: String): UploadTaskPayload {
        return runCatching {
            gson.fromJson(json, UploadTaskPayload::class.java)
        }.getOrNull() ?: throw IOException("上传任务 payload 无法解析")
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discard = ByteArray(8192)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
                if (read == -1) throw IOException("本地文件短于续传偏移")
                remaining -= read
            }
        }
    }

    private fun sumFileSizes(file: DocumentFile): Long {
        if (file.isFile) return file.length().coerceAtLeast(0)
        return file.listFiles().sumOf { sumFileSizes(it) }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private data class FileUploadPlan(
        val path: String,
        val startOffset: Long,
        val skip: Boolean = false
    )
}
