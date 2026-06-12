package com.nastools.app.data.network

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

class AuthInterceptor(
    private val username: String,
    private val password: String
) : Interceptor {
    private val lock = Any()
    private var digestState: DigestState? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        synchronized(lock) {
            val cached = digestState
            if (cached != null && request.header("Authorization") == null) {
                request = request.newBuilder()
                    .header("Authorization", buildDigestHeader(cached, request.method, digestUri(request)))
                    .build()
            }
        }

        val response = chain.proceed(request)
        if (response.code != 401 || request.header("X-Auth-Retried") == "true") {
            return response
        }

        val authHeader = response.header("WWW-Authenticate") ?: return response
        val retried = when {
            authHeader.startsWith("Basic", ignoreCase = true) -> request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .header("X-Auth-Retried", "true")
                .build()

            authHeader.startsWith("Digest", ignoreCase = true) -> {
                val state = parseDigestChallenge(authHeader)
                synchronized(lock) {
                    digestState = state
                }
                request.newBuilder()
                    .header("Authorization", buildDigestHeader(state, request.method, digestUri(request)))
                    .header("X-Auth-Retried", "true")
                    .build()
            }

            else -> return response
        }

        response.close()
        return chain.proceed(retried)
    }

    private fun digestUri(request: okhttp3.Request): String {
        val query = request.url.query
        return if (query.isNullOrEmpty()) {
            request.url.encodedPath
        } else {
            "${request.url.encodedPath}?$query"
        }
    }

    private fun parseDigestChallenge(header: String): DigestState {
        val params = mutableMapOf<String, String>()
        val body = header.substringAfter(' ')
        val regex = Regex("""(\w+)\s*=\s*("([^"]*)"|([^,]+))""")
        regex.findAll(body).forEach { match ->
            params[match.groupValues[1].lowercase(Locale.ROOT)] =
                (match.groupValues[3].ifBlank { match.groupValues[4] }).trim()
        }
        return DigestState(
            realm = params["realm"].orEmpty(),
            nonce = params["nonce"].orEmpty(),
            qop = params["qop"].orEmpty(),
            algorithm = params["algorithm"]?.uppercase(Locale.ROOT) ?: "MD5",
            opaque = params["opaque"]
        )
    }

    private fun buildDigestHeader(state: DigestState, method: String, uri: String): String {
        val cnonce = generateNonce()
        val ncString: String
        synchronized(lock) {
            state.nc += 1
            ncString = state.nc.toString(16).padStart(8, '0')
        }

        val ha1 = md5("$username:${state.realm}:$password")
        val ha2 = md5("$method:$uri")
        val response = if (state.qop.isNotBlank()) {
            md5("$ha1:${state.nonce}:$ncString:$cnonce:${state.qop}:$ha2")
        } else {
            md5("$ha1:${state.nonce}:$ha2")
        }

        val parts = mutableListOf(
            "username=\"$username\"",
            "realm=\"${state.realm}\"",
            "nonce=\"${state.nonce}\"",
            "uri=\"$uri\"",
            "algorithm=${state.algorithm}",
            "response=\"$response\""
        )
        if (state.qop.isNotBlank()) {
            parts += listOf(
                "qop=${state.qop}",
                "nc=$ncString",
                "cnonce=\"$cnonce\""
            )
        }
        state.opaque?.let { parts += "opaque=\"$it\"" }
        return "Digest ${parts.joinToString(", ")}"
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class DigestState(
        val realm: String,
        val nonce: String,
        val qop: String,
        val algorithm: String,
        val opaque: String?,
        var nc: Int = 0
    )
}
