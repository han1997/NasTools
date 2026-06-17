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
import com.nastools.app.data.repository.TaskRepository
import com.nastools.app.domain.model.UploadPresetOptions
import com.nastools.app.domain.model.UploadTaskPayload
import com.nastools.app.util.RemotePath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class UploadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: NasConfigRepository,
    private val adapterFactory: StorageAdapterFactory,
    private val taskRepository: TaskRepository
) {
    private val gson = Gson()
    private val networkTimeoutMs = 30_000L

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

        try {
            if (sourceType == "folder") {
                uploadFolder(
                    adapter = adapter,
                    payload = payload,
                    options = options.copy(sourceType = "folder"),
                    totalBytes = totalBytes,
                    warnings = warnings,
                    taskId = task.id,
                    originalTitle = task.title,
                    onProgress = onProgress
                )
            } else {
                var completedBytes = 0L
                val remotePath = RemotePath.normalize(payload.remotePath)
                uploadFile(
                    adapter = adapter,
                    uri = Uri.parse(payload.localUri),
                    localDocument = DocumentFile.fromSingleUri(context, Uri.parse(payload.localUri)),
                    localName = payload.localName.ifBlank { RemotePath.basename(payload.remotePath) },
                    remotePath = remotePath,
                    fileSize = totalBytes,
                    options = options.copy(sourceType = "file"),
                    totalTaskBytes = totalBytes,
                    completedBytes = { completedBytes },
                    updateCompletedBytes = { completedBytes = it },
                    warnings = warnings,
                    taskId = task.id,
                    originalTitle = task.title,
                    remoteDirForTitle = RemotePath.parent(remotePath),
                    onProgress = onProgress
                )
            }
        } finally {
            // Restore original title after upload completes or fails
            taskRepository.updateTitle(task.id, task.title)
        }

        return@withContext if (warnings.isEmpty()) null else "上传完成，${warnings.size} 个本地项目未能删除"
    }

    private suspend fun uploadFolder(
        adapter: StorageAdapter,
        payload: UploadTaskPayload,
        options: UploadPresetOptions,
        totalBytes: Long,
        warnings: MutableList<String>,
        taskId: String,
        originalTitle: String,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(payload.localUri))
            ?: throw IOException("无法打开本地文件夹")
        val rootName = payload.localName.ifBlank { root.name ?: "folder" }
        val rootPath = RemotePath.normalize(payload.remotePath.ifBlank { "/$rootName" })

        val remoteRoot = prepareFolderTarget(adapter, rootPath, options)
        val skippedFolders = mutableListOf<String>()

        if (remoteRoot == null) {
            // Root folder exists and skip mode - upload contents in merge mode
            var completedBytes = 0L
            uploadDirectory(
                adapter = adapter,
                directory = root,
                remoteDirectory = rootPath,
                remoteDirForTitle = rootPath,
                options = options,
                totalTaskBytes = totalBytes,
                completedBytes = { completedBytes },
                updateCompletedBytes = { completedBytes = it },
                warnings = warnings,
                skippedFolders = skippedFolders,
                taskId = taskId,
                originalTitle = originalTitle,
                isRoot = true,
                onProgress = onProgress
            )

            // If nothing was uploaded, set warning
            if (completedBytes == 0L) {
                warnings.add("所有文件已存在，跳过上传（已上传 0 字节）")
            }
            if (skippedFolders.isNotEmpty()) {
                warnings.add("跳过 ${skippedFolders.size} 个已存在的文件夹")
            }
            return
        }

        var completedBytes = 0L
        uploadDirectory(
            adapter = adapter,
            directory = root,
            remoteDirectory = remoteRoot,
            remoteDirForTitle = remoteRoot,
            options = options,
            totalTaskBytes = totalBytes,
            completedBytes = { completedBytes },
            updateCompletedBytes = { completedBytes = it },
            warnings = warnings,
            skippedFolders = skippedFolders,
            taskId = taskId,
            originalTitle = originalTitle,
            isRoot = true,
            onProgress = onProgress
        )

        if (skippedFolders.isNotEmpty()) {
            warnings.add("跳过 ${skippedFolders.size} 个已存在的文件夹")
        }
    }

    private suspend fun uploadDirectory(
        adapter: StorageAdapter,
        directory: DocumentFile,
        remoteDirectory: String,
        remoteDirForTitle: String,
        options: UploadPresetOptions,
        totalTaskBytes: Long,
        completedBytes: () -> Long,
        updateCompletedBytes: (Long) -> Unit,
        warnings: MutableList<String>,
        skippedFolders: MutableList<String>,
        taskId: String,
        originalTitle: String,
        isRoot: Boolean = false,
        onProgress: suspend (Long, Long) -> Unit
    ): Boolean {
        try {
            withTimeout(networkTimeoutMs) {
                adapter.mkdir(remoteDirectory)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("网络操作超时")
        }

        var canDeleteDirectory = options.deleteAfterUpload

        // Collect files to upload and subdirectories to recurse
        val filesToUpload = mutableListOf<Triple<DocumentFile, String, Long>>()
        val subdirectories = mutableListOf<Pair<DocumentFile, String>>()

        directory.listFiles().forEach { child ->
            currentCoroutineContext().ensureActive()
            val childName = child.name ?: run {
                canDeleteDirectory = false
                return@forEach
            }
            val desiredPath = RemotePath.join(remoteDirectory, childName)

            when {
                child.isDirectory -> {
                    subdirectories.add(child to desiredPath)
                }
                child.isFile -> {
                    filesToUpload.add(Triple(child, desiredPath, child.length().coerceAtLeast(0)))
                }
                else -> canDeleteDirectory = false
            }
        }

        // Process subdirectories recursively (sequential for simplicity)
        subdirectories.forEach { (child, desiredPath) ->
            currentCoroutineContext().ensureActive()
            val folderPath = prepareFolderTarget(adapter, desiredPath, options)
            if (folderPath == null) {
                canDeleteDirectory = false
                skippedFolders.add(desiredPath)
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
                    remoteDirForTitle = remoteDirForTitle,
                    options = options,
                    totalTaskBytes = totalTaskBytes,
                    completedBytes = completedBytes,
                    updateCompletedBytes = updateCompletedBytes,
                    warnings = warnings,
                    skippedFolders = skippedFolders,
                    taskId = taskId,
                    originalTitle = originalTitle,
                    isRoot = false,
                    onProgress = onProgress
                )
                if (!childMoved) canDeleteDirectory = false
            }
        }

        // Concurrent file uploads
        if (filesToUpload.isNotEmpty()) {
            val semaphore = Semaphore(3)
            val fileWarnings = Collections.synchronizedList(mutableListOf<String>())
            val progressMutex = Mutex()
            val atomicCompletedBytes = AtomicLong(completedBytes())

            coroutineScope {
                val uploadResults = filesToUpload.map { (child, desiredPath, fileSize) ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                uploadFile(
                                    adapter = adapter,
                                    uri = child.uri,
                                    localDocument = child,
                                    localName = child.name ?: "unknown",
                                    remotePath = desiredPath,
                                    fileSize = fileSize,
                                    options = options,
                                    totalTaskBytes = totalTaskBytes,
                                    completedBytes = { atomicCompletedBytes.get() },
                                    updateCompletedBytes = { newValue -> atomicCompletedBytes.set(newValue) },
                                    warnings = fileWarnings,
                                    taskId = taskId,
                                    originalTitle = originalTitle,
                                    remoteDirForTitle = remoteDirForTitle,
                                    onProgress = { uploaded, total ->
                                        progressMutex.withLock {
                                            onProgress(uploaded, total)
                                        }
                                    }
                                )
                            } catch (e: IOException) {
                                // Network/config/auth failures are fatal - propagate
                                if (e.message?.contains("网络") == true ||
                                    e.message?.contains("NAS") == true ||
                                    e.message?.contains("配置") == true ||
                                    e.message?.contains("超时") == true) {
                                    throw e
                                }
                                // Other IO failures (e.g., file read) are non-fatal warnings
                                fileWarnings.add("文件上传失败: ${child.name} (${e.message})")
                                false
                            } catch (e: Exception) {
                                // Unexpected errors - collect as warning but continue
                                fileWarnings.add("文件上传失败: ${child.name} (${e.message})")
                                false
                            }
                        }
                    }
                }.awaitAll()

                // Update shared completedBytes counter after concurrent uploads complete
                updateCompletedBytes(atomicCompletedBytes.get())

                // If any file failed to move, cannot delete directory
                if (uploadResults.any { !it }) {
                    canDeleteDirectory = false
                }

                // Merge file-level warnings into main warnings list
                synchronized(warnings) {
                    warnings.addAll(fileWarnings)
                }
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
        taskId: String,
        originalTitle: String,
        remoteDirForTitle: String,
        onProgress: suspend (Long, Long) -> Unit
    ): Boolean {
        val filterRegex = options.filterRegex?.takeIf { it.isNotBlank() }?.let {
            runCatching { Regex(it) }.getOrElse { throw IOException("过滤正则格式不正确") }
        }
        if (filterRegex != null && !filterRegex.containsMatchIn(localName)) {
            markProgress(fileSize, totalTaskBytes, completedBytes, updateCompletedBytes, onProgress)
            return false
        }

        try {
            withTimeout(networkTimeoutMs) {
                RemotePath.parent(remotePath).takeIf { it != "/" }?.let { adapter.mkdir(it) }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("网络操作超时")
        }

        val plan = prepareFileTarget(adapter, remotePath, fileSize, options)
        if (plan.skip) {
            markProgress(fileSize, totalTaskBytes, completedBytes, updateCompletedBytes, onProgress)
            return false
        }

        // Update task title to show current file
        val relativePath = remotePath.removePrefix(remoteDirForTitle).removePrefix("/")
        val newTitle = "$originalTitle > 当前：$relativePath"
        taskRepository.updateTitle(taskId, newTitle)

        val completedBeforeFile = completedBytes()
        val uploadedBytes = uploadFromUri(
            uri = uri,
            remotePath = plan.path,
            startOffset = plan.startOffset,
            totalBytes = fileSize,
            chunkSize = options.chunkSizeMb.coerceIn(1, 128) * 1024 * 1024,
            uploadChunk = { bytes, offset ->
                try {
                    withTimeout(networkTimeoutMs) {
                        adapter.upload(
                            path = plan.path,
                            data = bytes,
                            offset = offset,
                            totalLength = fileSize.takeIf { it > 0 }
                        )
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    throw IOException("网络操作超时")
                }
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
        val stat = try {
            withTimeout(networkTimeoutMs) {
                runCatching { adapter.stat(normalized) }.getOrNull()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("网络操作超时")
        }

        if (stat == null) {
            try {
                withTimeout(networkTimeoutMs) {
                    adapter.mkdir(normalized)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw IOException("网络操作超时")
            }
            return normalized
        }

        if (stat.isDirectory) {
            return when (options.folderConflictMode) {
                "rename" -> findAvailablePath(adapter, normalized, isDirectory = true).also {
                    try {
                        withTimeout(networkTimeoutMs) {
                            adapter.mkdir(it)
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        throw IOException("网络操作超时")
                    }
                }
                "skip" -> null
                "fail" -> throw IOException("远端文件夹已存在: $normalized")
                else -> normalized
            }
        }

        return when (options.folderConflictMode) {
            "rename" -> findAvailablePath(adapter, normalized, isDirectory = true).also {
                try {
                    withTimeout(networkTimeoutMs) {
                        adapter.mkdir(it)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    throw IOException("网络操作超时")
                }
            }
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
        val stat = try {
            withTimeout(networkTimeoutMs) {
                runCatching { adapter.stat(normalized) }.getOrNull()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("网络操作超时")
        } ?: return FileUploadPlan(path = normalized, startOffset = 0L)

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
            val exists = try {
                withTimeout(networkTimeoutMs) {
                    runCatching { adapter.stat(candidate) }.getOrNull() != null
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw IOException("网络操作超时")
            }
            if (!exists) {
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
