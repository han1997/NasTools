package com.nastools.app.data.network

import com.nastools.app.data.database.entity.NasConfigEntity
import okhttp3.OkHttpClient
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageAdapterFactory @Inject constructor(
    private val baseClient: OkHttpClient
) {
    fun create(config: NasConfigEntity): StorageAdapter {
        return when (config.type) {
            "webdav" -> WebDavAdapter(
                WebDavClient(
                    client = clientFor(config),
                    baseUrl = config.baseUrl.trim()
                )
            )

            "sftp" -> error("SFTP is not implemented yet")
            "smb" -> error("SMB is not implemented yet")
            else -> error("Unsupported config type: ${config.type}")
        }
    }

    private fun clientFor(config: NasConfigEntity): OkHttpClient {
        val builder = baseClient.newBuilder()
        if (config.username.isNotBlank() || config.password.isNotBlank()) {
            builder.addInterceptor(AuthInterceptor(config.username, config.password))
        }
        if (config.trustSelfSigned) {
            val host = runCatching { URI(config.baseUrl.trim()).host }.getOrNull().orEmpty()
            builder.trustSelfSignedHost(host)
        }
        return builder.build()
    }
}
