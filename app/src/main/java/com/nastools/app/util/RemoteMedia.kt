package com.nastools.app.util

import com.nastools.app.data.network.RemoteEntry

enum class RemoteMediaType {
    Image,
    Video
}

data class RemoteMediaItem(
    val path: String,
    val name: String,
    val type: RemoteMediaType,
    val size: Long? = null
)

object RemoteMedia {
    private val imageExtensions = setOf(
        "jpg",
        "jpeg",
        "png",
        "gif",
        "webp",
        "bmp",
        "heic",
        "heif"
    )

    private val videoExtensions = setOf(
        "mp4",
        "m4v",
        "mov",
        "mkv",
        "webm",
        "avi",
        "3gp",
        "ts"
    )

    fun typeForName(name: String): RemoteMediaType? {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()
        return when {
            extension in imageExtensions -> RemoteMediaType.Image
            extension in videoExtensions -> RemoteMediaType.Video
            else -> null
        }
    }

    fun isSupported(entry: RemoteEntry): Boolean {
        return !entry.isDirectory && typeForName(entry.name) != null
    }

    fun toMediaItem(entry: RemoteEntry): RemoteMediaItem? {
        val type = typeForName(entry.name) ?: return null
        if (entry.isDirectory) return null
        return RemoteMediaItem(
            path = entry.path,
            name = entry.name,
            type = type,
            size = entry.size
        )
    }
}
