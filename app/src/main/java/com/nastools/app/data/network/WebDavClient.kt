package com.nastools.app.data.network

import com.nastools.app.util.RemotePath
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class WebDavClient(
    private val client: OkHttpClient,
    private val baseUrl: String
) {
    suspend fun list(path: String): List<RemoteEntry> {
        val request = request(path)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "1")
            .build()

        return execute(request, "PROPFIND") { response ->
            parsePropfind(response.body?.string() ?: throw IOException("Empty response"), path, includeSelf = false)
        }
    }

    suspend fun stat(path: String): RemoteStat? {
        val request = request(path)
            .method("PROPFIND", "".toRequestBody())
            .header("Depth", "0")
            .build()

        return execute(request, "PROPFIND", acceptedCodes = setOf(404)) { response ->
            if (response.code == 404) return@execute null

            val body = response.body?.string() ?: return@execute null
            parsePropfind(body, path, includeSelf = true).firstOrNull()?.let {
                RemoteStat(it.path, it.isDirectory, it.size, it.mtime)
            }
        }
    }

    suspend fun put(
        path: String,
        data: ByteArray,
        offset: Long = 0,
        totalLength: Long? = null
    ) {
        val builder = request(path)
        if (totalLength != null && (offset > 0 || data.size.toLong() < totalLength)) {
            builder.header("Content-Range", "bytes $offset-${offset + data.size - 1}/$totalLength")
        }

        execute(
            request = builder.put(data.toRequestBody("application/octet-stream".toMediaType())).build(),
            operation = "PUT"
        ) {}
    }

    suspend fun get(path: String, start: Long? = null, end: Long? = null): ByteArray {
        val builder = request(path)
        if (start != null || end != null) {
            val range = when {
                start != null && end != null -> "bytes=$start-$end"
                start != null -> "bytes=$start-"
                else -> "bytes=0-$end"
            }
            builder.header("Range", range)
        }

        return execute(builder.get().build(), "GET") { response ->
            response.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    suspend fun mkcol(path: String) {
        execute(
            request = request(path).method("MKCOL", null).build(),
            operation = "MKCOL",
            acceptedCodes = setOf(405)
        ) {}
    }

    suspend fun delete(path: String) {
        execute(request(path).delete().build(), "DELETE") {}
    }

    suspend fun move(from: String, to: String) {
        execute(
            request = request(from)
                .method("MOVE", null)
                .header("Destination", buildUrl(to))
                .build(),
            operation = "MOVE"
        ) {}
    }

    suspend fun copy(from: String, to: String) {
        execute(
            request = request(from)
                .method("COPY", null)
                .header("Destination", buildUrl(to))
                .build(),
            operation = "COPY"
        ) {}
    }

    private fun request(path: String): Request.Builder {
        return Request.Builder().url(buildUrl(path))
    }

    private fun buildUrl(path: String): String {
        return baseUrl.trimEnd('/') + RemotePath.encode(path)
    }

    private suspend fun <T> execute(
        request: Request,
        operation: String,
        acceptedCodes: Set<Int> = emptySet(),
        block: (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in acceptedCodes) {
                throw IOException("$operation failed: ${response.code}")
            }
            block(response)
        }
    }

    private fun parsePropfind(xml: String, basePath: String, includeSelf: Boolean): List<RemoteEntry> {
        val entries = mutableListOf<RemoteEntry>()

        responsePattern.findAll(xml).forEach { match ->
            val content = match.groupValues[1]
            val href = extractTag(content, "href") ?: return@forEach
            val path = normalizeHref(href)

            if (!includeSelf && RemotePath.normalize(path) == RemotePath.normalize(basePath)) return@forEach

            entries.add(
                RemoteEntry(
                    path = path,
                    name = RemotePath.basename(path).ifBlank { "/" },
                    isDirectory = collectionPattern.containsMatchIn(content),
                    size = extractTag(content, "getcontentlength")?.toLongOrNull(),
                    mtime = parseHttpDate(extractTag(content, "getlastmodified"))
                )
            )
        }

        return entries.sortedWith(
            compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    private fun normalizeHref(href: String): String {
        val rawPath = runCatching {
            URI(href).rawPath ?: href
        }.getOrElse {
            href.substringBefore("?")
        }

        val basePath = runCatching {
            URI(baseUrl.trim()).rawPath.orEmpty().trimEnd('/')
        }.getOrDefault("")

        val relativePath = if (basePath.isNotBlank() && rawPath.startsWith(basePath)) {
            rawPath.removePrefix(basePath)
        } else {
            rawPath
        }

        val decoded = URLDecoder.decode(relativePath.ifBlank { "/" }, Charsets.UTF_8.name())
        return RemotePath.normalize(decoded)
    }

    private fun extractTag(xml: String, localName: String): String? {
        val pattern = "<(?:\\w+:)?$localName\\b[^>]*>(.*?)</(?:\\w+:)?$localName>"
            .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return pattern.find(xml)?.groupValues?.get(1)?.trim()
    }

    private fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time
        }.getOrNull()
    }

    companion object {
        private val responsePattern = "<(?:\\w+:)?response\\b[^>]*>(.*?)</(?:\\w+:)?response>"
            .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val collectionPattern = "<(?:\\w+:)?collection\\b[^>]*/>"
            .toRegex(RegexOption.IGNORE_CASE)
    }
}
