package com.nastools.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.google.gson.Gson
import com.nastools.app.data.database.entity.TaskEntity
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
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
    ) = withContext(Dispatchers.IO) {
        val configId = task.nasConfigId ?: throw IOException("上传任务缺少 NAS 配置")
        val config = configRepository.getById(configId) ?: throw IOException("NAS 配置不存在")
        val payload = decodePayload(task.payloadJson)
        if (payload.localUri.isBlank()) throw IOException("上传任务缺少本地文件")

        val options = payload.options ?: UploadPresetOptions()
        val totalBytes = payload.totalBytes.takeIf { it > 0 } ?: task.totalBytes
        val remotePath = RemotePath.normalize(payload.remotePath)
        val localName = payload.localName.ifBlank { RemotePath.basename(remotePath) }

        if (options.wifiOnly && !isWifiConnected()) {
            throw IOException("当前不是 Wi-Fi 网络")
        }

        val filterRegex = options.filterRegex?.takeIf { it.isNotBlank() }?.let {
            runCatching { Regex(it) }.getOrElse { throw IOException("过滤正则格式不正确") }
        }
        if (filterRegex != null && !filterRegex.containsMatchIn(localName)) {
            onProgress(totalBytes, totalBytes)
            return@withContext
        }

        val adapter = adapterFactory.create(config)

        RemotePath.parent(remotePath).takeIf { it != "/" }?.let { adapter.mkdir(it) }

        val remoteStat = runCatching { adapter.stat(remotePath) }.getOrNull()
        val startOffset = resolveStartOffset(
            mode = options.overwriteMode,
            remoteSize = remoteStat?.size,
            totalBytes = totalBytes
        )

        if (startOffset >= totalBytes && totalBytes > 0) {
            onProgress(totalBytes, totalBytes)
            return@withContext
        }

        uploadFromUri(
            uri = Uri.parse(payload.localUri),
            remotePath = remotePath,
            startOffset = startOffset,
            totalBytes = totalBytes,
            chunkSize = options.chunkSizeMb.coerceIn(1, 128) * 1024 * 1024,
            uploadChunk = { bytes, offset ->
                adapter.upload(
                    path = remotePath,
                    data = bytes,
                    offset = offset,
                    totalLength = totalBytes.takeIf { it > 0 }
                )
            },
            onProgress = onProgress
        )

        if (options.deleteAfterUpload) {
            runCatching { context.contentResolver.delete(Uri.parse(payload.localUri), null, null) }
        }
    }

    private fun decodePayload(json: String): UploadTaskPayload {
        return runCatching {
            gson.fromJson(json, UploadTaskPayload::class.java)
        }.getOrNull() ?: throw IOException("上传任务 payload 无法解析")
    }

    private fun resolveStartOffset(mode: String, remoteSize: Long?, totalBytes: Long): Long {
        if (remoteSize == null) return 0L
        return when (mode) {
            "overwrite" -> 0L
            "skip_existing" -> {
                if (totalBytes > 0 && remoteSize == totalBytes) totalBytes
                else throw IOException("远端文件已存在")
            }
            else -> {
                when {
                    totalBytes > 0 && remoteSize == totalBytes -> totalBytes
                    totalBytes > 0 && remoteSize in 1 until totalBytes -> remoteSize
                    else -> 0L
                }
            }
        }
    }

    private suspend fun uploadFromUri(
        uri: Uri,
        remotePath: String,
        startOffset: Long,
        totalBytes: Long,
        chunkSize: Int,
        uploadChunk: suspend (ByteArray, Long) -> Unit,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (totalBytes <= 0) {
                val bytes = input.readBytes()
                currentCoroutineContext().ensureActive()
                uploadChunk(bytes, 0L)
                onProgress(bytes.size.toLong(), bytes.size.toLong())
                return
            }

            skipFully(input, startOffset)
            var uploaded = startOffset
            onProgress(uploaded, totalBytes)

            val buffer = ByteArray(chunkSize)
            while (uploaded < totalBytes) {
                currentCoroutineContext().ensureActive()
                val readLimit = minOf(buffer.size.toLong(), totalBytes - uploaded).toInt()
                val read = input.read(buffer, 0, readLimit)
                if (read == -1) break

                val bytes = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                uploadChunk(bytes, uploaded)
                uploaded += read
                onProgress(uploaded.coerceAtMost(totalBytes), totalBytes)
            }
        } ?: throw IOException("无法打开本地文件: $remotePath")
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

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
