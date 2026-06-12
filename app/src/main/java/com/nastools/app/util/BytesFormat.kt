package com.nastools.app.util

import java.util.Locale

object BytesFormat {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun human(bytes: Long, fractionDigits: Int = 1): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val digits = if (unitIndex == 0) 0 else fractionDigits
        return "${String.format(Locale.US, "%.${digits}f", value)} ${units[unitIndex]}"
    }
}
