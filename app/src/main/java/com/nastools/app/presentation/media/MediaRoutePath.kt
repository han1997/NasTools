package com.nastools.app.presentation.media

import java.net.URLDecoder

internal object MediaRoutePath {
    fun decodeSavedPath(path: String): String {
        return if (path.startsWith("%2F", ignoreCase = true)) {
            URLDecoder.decode(path, Charsets.UTF_8.name())
        } else {
            path
        }
    }
}
