package com.nastools.app.util

object RemotePath {
    fun normalize(path: String): String {
        if (path.isBlank()) return "/"
        var normalized = path.replace(Regex("/+"), "/")
        if (!normalized.startsWith("/")) normalized = "/$normalized"
        if (normalized.length > 1 && normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    fun join(vararg parts: String?): String {
        return normalize(
            parts
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("/")
        )
    }

    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return "/"
        val lastSlash = normalized.lastIndexOf("/")
        if (lastSlash <= 0) return "/"
        return normalized.substring(0, lastSlash)
    }

    fun basename(path: String): String {
        val normalized = normalize(path)
        if (normalized == "/") return ""
        return normalized.substringAfterLast("/")
    }

    fun encode(path: String): String {
        return normalize(path)
            .split("/")
            .joinToString("/") { segment ->
                if (segment.isEmpty()) "" else java.net.URLEncoder.encode(segment, Charsets.UTF_8.name())
                    .replace("+", "%20")
            }
    }
}
