package com.iblu01.portallauncher.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * The sending half: takes a discovered [ConfigTransferCandidate] and pushes the configuration to it.
 *
 * Two steps on purpose. [handshake] exchanges public keys only — safe to run as soon as a receiver
 * is discovered, so the consent prompt can already show which device it is talking to. Only
 * [sendPayload] releases anything confidential, and the caller must not reach it before the user has
 * approved: this class holds no consent state and will happily send whatever it is handed.
 *
 * Failures come back as [ConfigTransferError] with an enum reason; the underlying exception is
 * dropped rather than propagated, because its message can quote a URL, a header or key material.
 */
class ConfigSenderClient(
    client: OkHttpClient? = null,
) {
    private val http = client ?: OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * A negotiated session. Carries the derived AES key, hence: not loggable, not persistable, and
     * dead once the receiver's five minutes are up.
     */
    class Handshake internal constructor(
        val candidate: ConfigTransferCandidate,
        val sessionId: String,
        val receiverFingerprint: String,
        val senderFingerprint: String,
        val expiresAtMs: Long,
        internal val key: SecretKey,
    ) {
        fun isExpiredAt(nowMs: Long): Boolean = nowMs >= expiresAtMs

        /** Redacted: this object is a capability to write a configuration onto another device. */
        override fun toString(): String = "Handshake(session=<redacted>, peer=$receiverFingerprint)"
    }

    /**
     * GET /hello then POST /offer. Verifies the receiver answers for the advertised session id and
     * that its public key really hashes to the advertised fingerprint, so a device that hijacked the
     * mDNS name cannot pose as the one the user is about to approve.
     */
    suspend fun handshake(
        candidate: ConfigTransferCandidate,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<Handshake> = withContext(Dispatchers.IO) {
        val hello = get(candidate.baseUrl + ConfigTransferProtocol.PATH_HELLO)
            .getOrElse { return@withContext Result.failure(it) }
        val parsed = ConfigTransferProtocol.parseHello(hello)
            ?: return@withContext fail(ConfigTransferFailure.PROTOCOL)
        if (!ConfigTransferCrypto.constantTimeEquals(parsed.sessionId, candidate.sessionId)) {
            return@withContext fail(ConfigTransferFailure.SESSION_MISMATCH)
        }
        if (!ConfigTransferCrypto.constantTimeEquals(parsed.fingerprint, candidate.fingerprint)) {
            return@withContext fail(ConfigTransferFailure.FINGERPRINT_MISMATCH)
        }

        val keyPair = ConfigTransferCrypto.generateKeyPair()
        val encodedPublicKey = ConfigTransferCrypto.encodePublicKey(keyPair.public)
        val aad = parsed.sessionId.toByteArray(Charsets.UTF_8)
        val derived = runCatching {
            ConfigTransferCrypto.deriveSharedKey(
                privateKey = keyPair.private,
                peerPublicKey = ConfigTransferCrypto.decodePublicKey(parsed.receiverPublicKey),
                salt = aad,
                info = ConfigTransferProtocol.HKDF_INFO,
            )
        }.getOrElse { return@withContext fail(ConfigTransferFailure.CRYPTO) }

        post(
            url = candidate.baseUrl + ConfigTransferProtocol.PATH_OFFER,
            body = ConfigTransferProtocol.offerJson(parsed.sessionId, encodedPublicKey)
                .toByteArray(Charsets.UTF_8),
            contentType = ConfigTransferProtocol.CONTENT_TYPE_JSON,
        ).getOrElse { return@withContext Result.failure(it) }

        Result.success(
            Handshake(
                candidate = candidate,
                sessionId = parsed.sessionId,
                receiverFingerprint = parsed.fingerprint,
                senderFingerprint = ConfigTransferProtocol.fingerprint(encodedPublicKey),
                expiresAtMs = nowMs + parsed.expiresInMs,
                key = derived,
            ),
        )
    }

    /**
     * Seals [plaintext] under the handshake key and POSTs it. Call only after explicit user
     * approval — everything confidential in the configuration leaves the device here.
     */
    suspend fun sendPayload(
        handshake: Handshake,
        plaintext: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (handshake.isExpiredAt(nowMs)) return@withContext fail(ConfigTransferFailure.EXPIRED)
        val aad = handshake.sessionId.toByteArray(Charsets.UTF_8)
        val sealed = runCatching { ConfigTransferCrypto.seal(handshake.key, plaintext, aad) }
            .getOrElse { return@withContext fail(ConfigTransferFailure.CRYPTO) }
        if (sealed.size > ConfigTransferProtocol.MAX_PAYLOAD_BYTES) {
            return@withContext fail(ConfigTransferFailure.TOO_LARGE)
        }
        post(
            url = handshake.candidate.baseUrl + ConfigTransferProtocol.PATH_PAYLOAD,
            body = sealed,
            contentType = ConfigTransferProtocol.CONTENT_TYPE_SEALED,
            headers = mapOf(
                ConfigTransferProtocol.HEADER_SESSION to handshake.sessionId,
                ConfigTransferProtocol.HEADER_PEER_FINGERPRINT to handshake.senderFingerprint,
            ),
        ).map { }
    }

    private fun get(url: String): Result<String> = execute(Request.Builder().url(url).get())

    private fun post(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = execute(
        Request.Builder().url(url).post(body.toRequestBody(contentType.toMediaType())).apply {
            headers.forEach { (name, value) -> header(name, value) }
        },
    )

    /** One place to map transport and status outcomes onto [ConfigTransferFailure]. */
    private fun execute(builder: Request.Builder): Result<String> = runCatching {
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return statusFailure(response)
            val source = response.body?.source() ?: return fail(ConfigTransferFailure.PROTOCOL)
            source.request(MAX_RESPONSE_BYTES + 1L)
            if (source.buffer.size > MAX_RESPONSE_BYTES) {
                return fail(ConfigTransferFailure.TOO_LARGE)
            }
            Result.success(source.buffer.readUtf8())
        }
    }.getOrElse { fail(ConfigTransferFailure.NETWORK) }

    private fun statusFailure(response: Response): Result<String> = fail(
        when (response.code) {
            403, 404 -> ConfigTransferFailure.REJECTED
            410 -> ConfigTransferFailure.EXPIRED
            413 -> ConfigTransferFailure.TOO_LARGE
            else -> ConfigTransferFailure.PROTOCOL
        },
    )

    private fun <T> fail(reason: ConfigTransferFailure): Result<T> =
        Result.failure(ConfigTransferError(reason))

    private companion object {
        private const val MAX_RESPONSE_BYTES = 8L * 1024
    }
}
