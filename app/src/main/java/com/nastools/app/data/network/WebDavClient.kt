package com.nastools.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WebDavClient(private val client: OkHttpClient, private val baseUrl: String) {

    suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "1")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("PROPFIND failed: ${response.code}")

        val body = response.body?.string() ?: throw IOException("Empty response")
        parsePropfind(body, path)
    }

    suspend fun stat(path: String): RemoteStat? = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "0")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 404) return@withContext null
        if (!response.isSuccessful) throw IOException("PROPFIND failed: ${response.code}")

        val body = response.body?.string() ?: return@withContext null
        parsePropfind(body, path).firstOrNull()?.let {
            RemoteStat(it.path, it.isDirectory, it.size, it.mtime)
        }
    }

    suspend fun put(path: String, data: ByteArray, offset: Long = 0, totalLength: Long? = null) = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val requestBuilder = Request.Builder().url(url)

        if (offset > 0 && totalLength != null) {
            requestBuilder.header("Content-Range", "bytes $offset-${offset + data.size - 1}/$totalLength")
        }

        val request = requestBuilder.put(data.toRequestBody("application/octet-stream".toMediaType())).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("PUT failed: ${response.code}")
    }

    suspend fun get(path: String, start: Long? = null, end: Long? = null): ByteArray = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val requestBuilder = Request.Builder().url(url)

        if (start != null || end != null) {
            val range = when {
                start != null && end != null -> "bytes=$start-$end"
                start != null -> "bytes=$start-"
                else -> "bytes=0-$end"
            }
            requestBuilder.header("Range", range)
        }

        val request = requestBuilder.get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("GET failed: ${response.code}")
        response.body?.bytes() ?: throw IOException("Empty response")
    }

    suspend fun mkcol(path: String) = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val request = Request.Builder().url(url).method("MKCOL", null).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 405) throw IOException("MKCOL failed: ${response.code}")
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        val request = Request.Builder().url(url).delete().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("DELETE failed: ${response.code}")
    }

    suspend fun move(from: String, to: String) = withContext(Dispatchers.IO) {
        val url = buildUrl(from)
        val destination = buildUrl(to)
        val request = Request.Builder()
            .url(url)
            .method("MOVE", null)
            .header("Destination", destination)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("MOVE failed: ${response.code}")
    }

    suspend fun copy(from: String, to: String) = withContext(Dispatchers.IO) {
        val url = buildUrl(from)
        val destination = buildUrl(to)
        val request = Request.Builder()
            .url(url)
            .method("COPY", null)
            .header("Destination", destination)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("COPY failed: ${response.code}")
    }

    private fun buildUrl(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return baseUrl.trimEnd('/') + normalized
    }

    private fun parsePropfind(xml: String, basePath: String): List<RemoteEntry> {
        val entries = mutableListOf<RemoteEntry>()
        val responsePattern = "<D:response>(.*?)</D:response>".toRegex(RegexOption.DOT_MATCHES_ALL)

        responsePattern.findAll(xml).forEach { match ->
            val content = match.groupValues[1]
            val href = extractTag(content, "D:href") ?: return@forEach
            val path = href.substringAfter(baseUrl.trimEnd('/')).ifEmpty { "/" }

            if (path == basePath || path == "$basePath/") return@forEach

            val isDir = content.contains("<D:collection/>")
            val sizeStr = extractTag(content, "D:getcontentlength")
            val size = sizeStr?.toLongOrNull()

            val name = path.trimEnd('/').substringAfterLast('/')
            entries.add(RemoteEntry(path, name, isDir, size, null))
        }

        return entries
    }

    private fun extractTag(xml: String, tag: String): String? {
        val pattern = "<$tag>(.*?)</$tag>".toRegex()
        return pattern.find(xml)?.groupValues?.get(1)
    }
}
