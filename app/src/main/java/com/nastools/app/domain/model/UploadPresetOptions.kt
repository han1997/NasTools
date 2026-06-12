package com.nastools.app.domain.model

import com.google.gson.Gson

data class UploadPresetOptions(
    val sourceType: String = "file",
    val chunkSizeMb: Int = 8,
    val overwriteMode: String = "resume_or_overwrite",
    val folderConflictMode: String = "merge",
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
            val decoded = gson.fromJson(json, UploadPresetOptions::class.java) ?: UploadPresetOptions()
            decoded.sanitized()
        }.getOrElse {
            UploadPresetOptions()
        }
    }

    private fun UploadPresetOptions.sanitized(): UploadPresetOptions {
        return UploadPresetOptions(
            sourceType = sourceType.orEmpty().takeIf { it in setOf("file", "folder") } ?: "file",
            chunkSizeMb = chunkSizeMb.takeIf { it in 1..128 } ?: 8,
            overwriteMode = overwriteMode.orEmpty().takeIf {
                it in setOf("resume_or_overwrite", "overwrite", "skip_existing", "rename", "fail")
            } ?: "resume_or_overwrite",
            folderConflictMode = folderConflictMode.orEmpty().takeIf {
                it in setOf("merge", "rename", "skip", "fail")
            } ?: "merge",
            wifiOnly = wifiOnly,
            filterRegex = filterRegex,
            deleteAfterUpload = deleteAfterUpload
        )
    }
}
