package com.nastools.app.data.network

data class RemoteEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val mtime: Long? = null
)

data class RemoteStat(
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val mtime: Long? = null
)

interface StorageAdapter {
    suspend fun ping()
    suspend fun list(path: String): List<RemoteEntry>
    suspend fun stat(path: String): RemoteStat?
    suspend fun mkdir(path: String)
    suspend fun delete(path: String)
    suspend fun move(from: String, to: String)
    suspend fun copy(from: String, to: String)
    suspend fun upload(path: String, data: ByteArray, offset: Long = 0)
    suspend fun download(path: String, start: Long? = null, end: Long? = null): ByteArray
}
