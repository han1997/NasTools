package com.nastools.app.domain.model

import com.google.gson.Gson

data class UploadPresetOptions(
    val chunkSizeMb: Int = 8,
    val overwriteMode: String = "resume_or_overwrite",
    val wifiOnly: Boolean = false,
    val filterRegex: String? = null,
    val deleteAfterUpload: Boolean = false
)

object UploadPresetOptionsCodec {
    private val gson = Gson()

    fun encode(options: UploadPresetOptions): String = gson.toJson(options)

    fun decode(json: String?): UploadPresetOptions {
        if (json.isNullOrBlank()) return UploadPresetOptions()
        return runCatching {
            gson.fromJson(json, UploadPresetOptions::class.java) ?: UploadPresetOptions()
        }.getOrElse {
            UploadPresetOptions()
        }
    }
}
