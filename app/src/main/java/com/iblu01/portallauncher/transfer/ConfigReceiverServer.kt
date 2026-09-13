package com.iblu01.portallauncher.transfer

import fi.iki.elonen.NanoHTTPD
import java.io.DataInputStream
import javax.crypto.SecretKey

/**
 * The receiving half of the transfer: a short-lived HTTP server the onboarding screen starts while
 * it advertises itself on the LAN.
 *
 * Trust model — the sender is the one who confirms, so this server has to defend itself alone:
 *
 *  - the peer address must be loopback, link-local or private (CFG-006);
 *  - every request must quote this instance's session id, checked in constant time;
 *  - the payload must quote the fingerprint of the public key offered earlier, so a second device
 *    on the link cannot slip a payload into a session it did not negotiate;
 *  - the session lives [ConfigTransferSession.TTL_MS] and its payload is consumed exactly once —
 *    the session is marked CONSUMED *before* the callback runs, so a retry after a failed import
 *    needs a brand new session rather than getting a second shot at the same one;
 *  - responses are error codes, never exception text, and no key, plaintext or session id is logged.
 *
 * Deliberately Android-free (no `android.util.Log`, no `Context`) so the entire route table can be
 * exercised over a real socket from a JVM unit test.
 */
class ConfigReceiverServer private constructor(
    port: Int,
    private val displayName: String,
    private val nowMs: () -> Long,
    private val onOffer: () -> Unit,
    private val onPayload: (ByteArray) -> Boolean,
) : NanoHTTPD(BIND_ADDRESS, port) {

    private val keyPair = ConfigTransferCrypto.generateKeyPair()
    private val encodedPublicKey = ConfigTransferCrypto.encodePublicKey(keyPair.public)

    /** Public checksum of our ephemeral key, published in the mDNS record. */
    val fingerprint: String = ConfigTransferProtocol.fingerprint(encodedPublicKey)

    private val lock = Any()
    private var session = ConfigTransferSession.create(nowMs())
    private var sharedKey: SecretKey? = null
    private var peerFingerprint: String? = null

    val sessionId: String get() = synchronized(lock) { session.id }

    val state: ConfigTransferState get() = synchronized(lock) { session.stateAt(nowMs()) }

    /** What the advertiser publishes. Public material only. */
    fun advertisementAttributes(): Map<String, String> =
        ConfigTransferProtocol.advertisementAttributes(sessionId, displayName, fingerprint)

    override fun serve(http: IHTTPSession): Response {
        if (!ConfigTransferProtocol.isLocalAddress(http.remoteIpAddress)) {
            return error(Response.Status.FORBIDDEN, ConfigTransferFailure.REJECTED)
        }
        return runCatching {
            when {
                http.uri == ConfigTransferProtocol.PATH_HELLO && http.method == Method.GET ->
                    hello()

                http.uri == ConfigTransferProtocol.PATH_OFFER && http.method == Method.POST ->
                    offer(readText(http))

                http.uri == ConfigTransferProtocol.PATH_PAYLOAD && http.method == Method.POST ->
                    payload(http)

                else -> error(Response.Status.NOT_FOUND, ConfigTransferFailure.PROTOCOL)
            }
        }.getOrElse {
            // Never the exception message: it can quote peer bytes or crypto internals.
            error(Response.Status.INTERNAL_ERROR, ConfigTransferFailure.PROTOCOL)
        }
    }

    private fun hello(): Response = synchronized(lock) {
        val now = nowMs()
        val live = session
        if (live.stateAt(now).terminal) {
            return error(Response.Status.GONE, ConfigTransferFailure.EXPIRED)
        }
        json(
            ConfigTransferProtocol.helloJson(
                sessionId = live.id,
                encodedPublicKey = encodedPublicKey,
                expiresInMs = ConfigTransferSession.TTL_MS - (now - live.createdAtMs),
            ),
        )
    }

    /**
     * Establishes the ECDH secret. Public keys only: nothing confidential has been exchanged at
     * this point, which is why the sender is free to run this before its user has approved.
     */
    private fun offer(body: String): Response = synchronized(lock) {
        val offer = ConfigTransferProtocol.parseOffer(body)
            ?: return error(Response.Status.BAD_REQUEST, ConfigTransferFailure.PROTOCOL)
        if (!session.matchesId(offer.sessionId)) {
            return error(Response.Status.FORBIDDEN, ConfigTransferFailure.SESSION_MISMATCH)
        }
        val peerKey = runCatching { ConfigTransferCrypto.decodePublicKey(offer.senderPublicKey) }
            .getOrNull()
            ?: return error(Response.Status.BAD_REQUEST, ConfigTransferFailure.CRYPTO)

        val advanced = session.offer(offer.senderPublicKey, nowMs())
            .getOrElse { return failure(it) }
        val derived = runCatching {
            ConfigTransferCrypto.deriveSharedKey(
                privateKey = keyPair.private,
                peerPublicKey = peerKey,
                salt = advanced.aad,
                info = ConfigTransferProtocol.HKDF_INFO,
            )
        }.getOrElse {
            session = advanced.failed()
            return error(Response.Status.BAD_REQUEST, ConfigTransferFailure.CRYPTO)
        }

        session = advanced
        sharedKey = derived
        peerFingerprint = offer.fingerprint
        runCatching(onOffer)
        json("""{"v":${ConfigTransferProtocol.VERSION},"ok":true}""")
    }

    private fun payload(http: IHTTPSession): Response {
        val declaredSize = http.headers["content-length"]?.toLongOrNull() ?: -1L
        if (declaredSize > ConfigTransferProtocol.MAX_PAYLOAD_BYTES) {
            return error(Response.Status.PAYLOAD_TOO_LARGE, ConfigTransferFailure.TOO_LARGE)
        }
        val quotedSession = http.headers[ConfigTransferProtocol.HEADER_SESSION]
        val quotedPeer = http.headers[ConfigTransferProtocol.HEADER_PEER_FINGERPRINT]

        val sealed: ByteArray
        val key: SecretKey
        val aad: ByteArray
        synchronized(lock) {
            if (quotedSession == null || !session.matchesId(quotedSession)) {
                return error(Response.Status.FORBIDDEN, ConfigTransferFailure.SESSION_MISMATCH)
            }
            val expectedPeer = peerFingerprint
            if (expectedPeer == null || quotedPeer == null ||
                !ConfigTransferCrypto.constantTimeEquals(expectedPeer, quotedPeer.lowercase())
            ) {
                return error(Response.Status.FORBIDDEN, ConfigTransferFailure.FINGERPRINT_MISMATCH)
            }
            key = sharedKey
                ?: return error(Response.Status.FORBIDDEN, ConfigTransferFailure.PROTOCOL)

            // Consume first: the one-shot guarantee must not depend on the import succeeding.
            val consumed = session.consume(nowMs()).getOrElse { return failure(it) }
            aad = consumed.aad
            sealed = readSealed(http, declaredSize)
                ?: return error(Response.Status.BAD_REQUEST, ConfigTransferFailure.TOO_LARGE)
            session = consumed
            sharedKey = null
        }

        val plaintext = runCatching { ConfigTransferCrypto.open(key, sealed, aad) }.getOrElse {
            return error(Response.Status.BAD_REQUEST, ConfigTransferFailure.CRYPTO)
        }
        val applied = runCatching { onPayload(plaintext) }.getOrDefault(false)
        plaintext.fill(0)
        return if (applied) {
            json("""{"v":${ConfigTransferProtocol.VERSION},"ok":true}""")
        } else {
            error(Response.Status.INTERNAL_ERROR, ConfigTransferFailure.REJECTED)
        }
    }

    /** Reads exactly the declared body, refusing anything over the cap without buffering it. */
    private fun readSealed(http: IHTTPSession, declaredSize: Long): ByteArray? {
        if (declaredSize <= 0L || declaredSize > ConfigTransferProtocol.MAX_PAYLOAD_BYTES) return null
        val bytes = ByteArray(declaredSize.toInt())
        DataInputStream(http.inputStream).readFully(bytes)
        return bytes
    }

    private fun readText(http: IHTTPSession): String {
        val declaredSize = http.headers["content-length"]?.toLongOrNull() ?: return ""
        if (declaredSize <= 0L || declaredSize > MAX_JSON_BYTES) return ""
        val bytes = ByteArray(declaredSize.toInt())
        DataInputStream(http.inputStream).readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    /** A rejected transition carries the session forward (expired / failed) so state stays truthful. */
    private fun failure(cause: Throwable): Response {
        val reason = when {
            cause is ConfigTransferException -> {
                session = cause.session
                if (cause.session.state == ConfigTransferState.EXPIRED) {
                    ConfigTransferFailure.EXPIRED
                } else {
                    ConfigTransferFailure.REJECTED
                }
            }
            else -> ConfigTransferFailure.PROTOCOL
        }
        val status = if (reason == ConfigTransferFailure.EXPIRED) {
            Response.Status.GONE
        } else {
            Response.Status.FORBIDDEN
        }
        return error(status, reason)
    }

    private fun json(body: String) = harden(
        newFixedLengthResponse(Response.Status.OK, ConfigTransferProtocol.CONTENT_TYPE_JSON, body),
    )

    private fun error(status: Response.Status, reason: ConfigTransferFailure) = harden(
        newFixedLengthResponse(
            status,
            ConfigTransferProtocol.CONTENT_TYPE_JSON,
            """{"v":${ConfigTransferProtocol.VERSION},"ok":false,"error":"${reason.name.lowercase()}"}""",
        ),
    )

    private fun harden(response: Response): Response = response.apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    companion object {
        /** All interfaces: the sender arrives over Wi-Fi, the tests over loopback. */
        private const val BIND_ADDRESS = "0.0.0.0"
        private const val PREFERRED_PORT = 8765
        private const val SOCKET_READ_TIMEOUT_MS = 20_000
        private const val MAX_JSON_BYTES = 8 * 1024

        /**
         * Starts a listening server, [PREFERRED_PORT] first then any free port, or null when neither
         * binds. [onPayload] receives the decrypted configuration at most once and returns whether
         * the import succeeded; it runs on a NanoHTTPD worker thread, not the main thread.
         */
        fun launch(
            displayName: String,
            nowMs: () -> Long = System::currentTimeMillis,
            onOffer: () -> Unit = {},
            onPayload: (ByteArray) -> Boolean,
        ): ConfigReceiverServer? {
            intArrayOf(PREFERRED_PORT, 0).forEach { port ->
                val server = ConfigReceiverServer(port, displayName, nowMs, onOffer, onPayload)
                if (runCatching { server.start(SOCKET_READ_TIMEOUT_MS, true) }.isSuccess) {
                    return server
                }
                server.stop()
            }
            return null
        }
    }
}
