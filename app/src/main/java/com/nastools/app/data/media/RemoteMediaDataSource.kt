package com.nastools.app.data.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.nastools.app.data.network.StorageAdapter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@UnstableApi
class RemoteMediaDataSource(
    private val adapter: StorageAdapter,
    private val remotePath: String,
    private val contentLength: Long?
) : BaseDataSource(true) {
    private var dataSpec: DataSpec? = null
    private var uri: Uri? = null
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var nextRangeStart = 0L
    private var chunk = ByteArray(0)
    private var chunkOffset = 0
    private var opened = false
    private var endAfterChunk = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        uri = dataSpec.uri
        nextRangeStart = dataSpec.position
        chunk = ByteArray(0)
        chunkOffset = 0
        endAfterChunk = false
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            contentLength != null -> (contentLength - dataSpec.position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        if (chunkOffset >= chunk.size) {
            if (!loadNextChunk()) return C.RESULT_END_OF_INPUT
        }

        val available = chunk.size - chunkOffset
        val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            length.coerceAtMost(bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        val bytesToCopy = requested.coerceAtMost(available)
        System.arraycopy(chunk, chunkOffset, buffer, offset, bytesToCopy)
        chunkOffset += bytesToCopy

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesToCopy
        }
        bytesTransferred(bytesToCopy)
        return bytesToCopy
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        chunk = ByteArray(0)
        chunkOffset = 0
        uri = null
        dataSpec = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun loadNextChunk(): Boolean {
        if (endAfterChunk) return false
        val remainingForRequest = bytesRemaining
        if (remainingForRequest == 0L) return false

        val requestLength = if (remainingForRequest == C.LENGTH_UNSET.toLong()) {
            CHUNK_SIZE_BYTES
        } else {
            remainingForRequest.coerceAtMost(CHUNK_SIZE_BYTES.toLong()).toInt()
        }
        val start = nextRangeStart
        val end = start + requestLength - 1
        val bytes = try {
            runBlocking(Dispatchers.IO) {
                adapter.download(remotePath, start, end)
            }
        } catch (error: Exception) {
            throw IOException(error.message ?: "Failed to read remote media", error)
        }

        if (bytes.isEmpty()) return false
        if (bytes.size > requestLength) {
            throw IOException("Range response exceeded requested chunk size")
        }

        chunk = bytes
        chunkOffset = 0
        nextRangeStart += bytes.size
        if (bytes.size < requestLength) {
            endAfterChunk = true
        }
        return true
    }

    companion object {
        private const val CHUNK_SIZE_BYTES = 256 * 1024
    }
}

@UnstableApi
class RemoteMediaDataSourceFactory(
    private val adapter: StorageAdapter,
    private val remotePath: String,
    private val contentLength: Long?
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return RemoteMediaDataSource(adapter, remotePath, contentLength)
    }
}
