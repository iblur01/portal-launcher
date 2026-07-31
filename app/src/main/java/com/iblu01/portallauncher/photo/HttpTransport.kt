package com.iblu01.portallauncher.photo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal coroutine-based HTTP transport. The implementation is intentionally thin so tests can
 * swap it for a fake; provider-specific parsing lives in the adapter, not here.
 */
interface HttpTransport {
    data class Response(
        val code: Int,
        val body: String? = null,
        val bytes: ByteArray? = null,
        val error: Throwable? = null,
    )

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Response
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): Response = Response(code = -1, error = UnsupportedOperationException("post_not_supported"))
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): Response
}

/**
 * OkHttp-backed transport. [client] is injected so the caller can configure TLS / proxy / timeouts.
 */
class OkHttpTransport(client: OkHttpClient? = null) : HttpTransport {
    private val http = client ?: OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, headers: Map<String, String>): HttpTransport.Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }.build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    val body = response.body
                    if (body == null) {
                        HttpTransport.Response(code = response.code)
                    } else {
                        val source = body.source()
                        source.request(MAX_JSON_BYTES + 1L)
                        if (source.buffer.size > MAX_JSON_BYTES) {
                            HttpTransport.Response(code = 413)
                        } else {
                            HttpTransport.Response(code = response.code, body = source.buffer.readUtf8())
                        }
                    }
                }
            }.getOrElse { HttpTransport.Response(code = -1, error = it) }
        }

    override suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): HttpTransport.Response = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.post(body.toRequestBody(JSON_MEDIA_TYPE)).build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val responseBody = response.body
                if (responseBody == null) {
                    HttpTransport.Response(code = response.code)
                } else {
                    val source = responseBody.source()
                    source.request(MAX_JSON_BYTES + 1L)
                    if (source.buffer.size > MAX_JSON_BYTES) {
                        HttpTransport.Response(code = 413)
                    } else {
                        HttpTransport.Response(code = response.code, body = source.buffer.readUtf8())
                    }
                }
            }
        }.getOrElse { HttpTransport.Response(code = -1, error = it) }
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): HttpTransport.Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }.build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    val body = response.body
                    if (body == null) {
                        HttpTransport.Response(code = response.code)
                    } else {
                        val source = body.source()
                        source.request(MAX_IMAGE_BYTES + 1L)
                        if (source.buffer.size > MAX_IMAGE_BYTES) {
                            HttpTransport.Response(code = 413)
                        } else {
                            HttpTransport.Response(code = response.code, bytes = source.buffer.readByteArray())
                        }
                    }
                }
            }.getOrElse { HttpTransport.Response(code = -1, error = it) }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_JSON_BYTES = 10 * 1024 * 1024L
        const val MAX_IMAGE_BYTES = 25 * 1024 * 1024L
    }
}

/**
 * Transport that fails every request. Used for offline-fallback tests.
 */
class FailingTransport(private val error: Throwable = IOException("offline")) : HttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): HttpTransport.Response =
        HttpTransport.Response(code = -1, error = error)

    override suspend fun getBytes(url: String, headers: Map<String, String>): HttpTransport.Response =
        HttpTransport.Response(code = -1, error = error)

    override suspend fun post(url: String, body: String, headers: Map<String, String>): HttpTransport.Response =
        HttpTransport.Response(code = -1, error = error)
}

/** Builds a user-facing error category from a transport response. */
fun HttpTransport.Response.toErrorCategory(): String = when (code) {
    401, 403 -> PhotoErrorCategories.AUTH
    413 -> PhotoErrorCategories.TOO_LARGE
    in 500..599 -> PhotoErrorCategories.SERVER
    in 400..499 -> PhotoErrorCategories.CONFIG
    else -> if (error != null) PhotoErrorCategories.NETWORK else PhotoErrorCategories.UNKNOWN
}
