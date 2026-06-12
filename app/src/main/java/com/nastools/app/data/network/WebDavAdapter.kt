package com.nastools.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavAdapter(private val client: WebDavClient) : StorageAdapter {

    override suspend fun ping() {
        client.list("/")
    }

    override suspend fun list(path: String): List<RemoteEntry> {
        return client.list(path)
    }

    override suspend fun stat(path: String): RemoteStat? {
        return client.stat(path)
    }

    override suspend fun mkdir(path: String) {
        val segments = path.trim('/').split('/')
        var current = ""
        for (segment in segments) {
            current += "/$segment"
            try {
                client.mkcol(current)
            } catch (e: Exception) {
                // Already exists, continue
            }
        }
    }

    override suspend fun delete(path: String) {
        client.delete(path)
    }

    override suspend fun move(from: String, to: String) {
        client.move(from, to)
    }

    override suspend fun copy(from: String, to: String) {
        client.copy(from, to)
    }

    override suspend fun upload(path: String, data: ByteArray, offset: Long) {
        client.put(path, data, offset, if (offset > 0) offset + data.size else null)
    }

    override suspend fun download(path: String, start: Long?, end: Long?): ByteArray {
        return client.get(path, start, end)
    }
}
