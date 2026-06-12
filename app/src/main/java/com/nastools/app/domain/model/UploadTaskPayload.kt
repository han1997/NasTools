package com.nastools.app.domain.model

data class UploadTaskPayload(
    val localUri: String = "",
    val localName: String = "",
    val remotePath: String = "/",
    val totalBytes: Long = 0,
    val options: UploadPresetOptions? = null
)
