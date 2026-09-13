package com.iblu01.portallauncher.transfer

import org.json.JSONObject
import java.net.InetAddress
import java.security.MessageDigest
import java.util.Base64

/**
 * Wire format for the nearby configuration transfer, kept free of Android so the whole protocol —
 * discovery attributes, JSON bodies, address policy — is unit-testable on the JVM.
 *
 * Three routes, one direction of trust: the receiver publishes an ephemeral public key, the sender
 * offers its own, and the sealed configuration travels once. The discovery record carries only
 * public material (version, session id, display name, key fingerprint) — never a secret, because
 * mDNS is broadcast to the whole link.
 */
object ConfigTransferProtocol {
    const val VERSION = 1

    /** Registered with NsdManager verbatim; the trailing dot is what the platform expects. */
    const val SERVICE_TYPE = "_portal-config._tcp."

    const val PATH_HELLO = "/portal/v1/hello"
    const val PATH_OFFER = "/portal/v1/offer"
    const val PATH_PAYLOAD = "/portal/v1/payload"

    /** Lowercase: NanoHTTPD and OkHttp both normalise header names to lowercase on lookup. */
    const val HEADER_SESSION = "x-portal-session"

    /** Fingerprint of the sender key offered earlier — binds the payload POST to that offer. */
    const val HEADER_PEER_FINGERPRINT = "x-portal-peer"

    const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
    const val CONTENT_TYPE_SEALED = "application/octet-stream"

    /** A launcher configuration with tokens and layout is kilobytes; 1 MiB is already generous. */
    const val MAX_PAYLOAD_BYTES = 1 shl 20

    const val TXT_VERSION = "v"
    const val TXT_SESSION = "sid"
    const val TXT_NAME = "dn"
    const val TXT_FINGERPRINT = "fp"

    /** HKDF context string: a key derived here can never be mistaken for one from another feature. */
    val HKDF_INFO: ByteArray get() = "portal-config-v1".toByteArray(Charsets.UTF_8)

    private const val SESSION_ID_HEX_LENGTH = ConfigTransferSession.ID_SIZE_BYTES * 2
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val MAX_DISPLAY_NAME_LENGTH = 64

    private val HEX = Regex("[0-9a-f]+")
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val IPV6_CHARS = Regex("[0-9a-fA-F:.]+")

    /** SHA-256 of the X.509-encoded public key, lowercase hex. Public by design: it is a checksum. */
    fun fingerprint(encodedPublicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encodedPublicKey).toHex()

    fun encodeKey(encodedPublicKey: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(encodedPublicKey)

    /** Null rather than throwing: a malformed key is peer input, not a programming error. */
    fun decodeKey(encoded: String?): ByteArray? {
        val trimmed = encoded?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > 512) return null
        return runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Everything the sender needs to find and identify the receiver — and nothing more. */
    fun advertisementAttributes(
        sessionId: String,
        displayName: String,
        fingerprint: String,
    ): Map<String, String> = linkedMapOf(
        TXT_VERSION to VERSION.toString(),
        TXT_SESSION to sessionId,
        TXT_NAME to sanitizeDisplayName(displayName),
        TXT_FINGERPRINT to fingerprint,
    )

    /**
     * Turns a resolved mDNS record into a candidate, or null when it is not a usable peer: wrong
     * protocol version, malformed session id or fingerprint, unroutable host.
     */
    fun parseAdvertisement(
        serviceName: String,
        host: String,
        port: Int,
        attributes: Map<String, String>,
    ): ConfigTransferCandidate? {
        if (attributes[TXT_VERSION]?.toIntOrNull() != VERSION) return null
        if (port !in 1..65535) return null
        if (!isLocalAddress(host)) return null
        val sessionId = attributes[TXT_SESSION]?.lowercase()?.takeIf { it.isSessionId() } ?: return null
        val fingerprint = attributes[TXT_FINGERPRINT]?.lowercase()?.takeIf { it.isFingerprint() }
            ?: return null
        val displayName = sanitizeDisplayName(attributes[TXT_NAME].orEmpty())
            .ifEmpty { sanitizeDisplayName(serviceName) }
        return ConfigTransferCandidate(
            serviceName = serviceName,
            host = host,
            port = port,
            sessionId = sessionId,
            displayName = displayName,
            fingerprint = fingerprint,
        )
    }

    fun helloJson(sessionId: String, encodedPublicKey: ByteArray, expiresInMs: Long): String =
        JSONObject()
            .put("v", VERSION)
            .put("session", sessionId)
            .put("pubkey", encodeKey(encodedPublicKey))
            .put("expires_in_ms", expiresInMs)
            .toString()

    fun parseHello(body: String?): Hello? {
        val json = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (json.optInt("v", -1) != VERSION) return null
        val sessionId = json.optString("session").lowercase().takeIf { it.isSessionId() } ?: return null
        val publicKey = decodeKey(json.optString("pubkey")) ?: return null
        val expiresInMs = json.optLong("expires_in_ms", -1L)
        if (expiresInMs <= 0L || expiresInMs > ConfigTransferSession.TTL_MS) return null
        return Hello(sessionId, publicKey, expiresInMs)
    }

    fun offerJson(sessionId: String, encodedPublicKey: ByteArray): String = JSONObject()
        .put("v", VERSION)
        .put("session", sessionId)
        .put("pubkey", encodeKey(encodedPublicKey))
        .toString()

    fun parseOffer(body: String?): Offer? {
        val json = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (json.optInt("v", -1) != VERSION) return null
        val sessionId = json.optString("session").lowercase().takeIf { it.isSessionId() } ?: return null
        val publicKey = decodeKey(json.optString("pubkey")) ?: return null
        return Offer(sessionId, publicKey)
    }

    /**
     * CFG-006's local-only rule, enforced on the receiver against the socket's peer address.
     * Only numeric literals are accepted so no code path here can trigger a DNS lookup, and the
     * IPv6 unique-local range is checked by hand — [InetAddress.isSiteLocalAddress] misses fc00::/7.
     */
    fun isLocalAddress(remote: String?): Boolean {
        val raw = remote?.trim()?.substringBefore('%').orEmpty().removeSurrounding("[", "]")
        val literal = when {
            raw.isEmpty() -> return false
            raw.contains(':') -> IPV6_CHARS.matches(raw)
            else -> IPV4.matches(raw)
        }
        if (!literal) return false
        val address = runCatching { InetAddress.getByName(raw) }.getOrNull() ?: return false
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /**
     * The name is peer-controlled and ends up on screen and in an mDNS record: single-line, bounded,
     * no control characters.
     */
    fun sanitizeDisplayName(name: String): String = name
        .map { if (it.isISOControl() || it == '�') ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(MAX_DISPLAY_NAME_LENGTH)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.isSessionId(): Boolean =
        length == SESSION_ID_HEX_LENGTH && HEX.matches(this)

    private fun String.isFingerprint(): Boolean =
        length == FINGERPRINT_HEX_LENGTH && HEX.matches(this)

    data class Hello(
        val sessionId: String,
        val receiverPublicKey: ByteArray,
        val expiresInMs: Long,
    ) {
        val fingerprint: String get() = ConfigTransferProtocol.fingerprint(receiverPublicKey)
    }

    data class Offer(val sessionId: String, val senderPublicKey: ByteArray) {
        val fingerprint: String get() = ConfigTransferProtocol.fingerprint(senderPublicKey)
    }
}

/**
 * An immutable, resolved receiver. Discovery hands these out; nothing in here is a secret, so a
 * candidate can safely be held in UI state and shown in the consent prompt.
 */
data class ConfigTransferCandidate(
    val serviceName: String,
    val host: String,
    val port: Int,
    val sessionId: String,
    val displayName: String,
    val fingerprint: String,
) {
    /** Bracketed for IPv6, so this is directly usable as an OkHttp URL prefix. */
    val baseUrl: String
        get() = if (host.contains(':')) "http://[$host]:$port" else "http://$host:$port"

    /** Short form for the consent prompt: enough for a human to compare two devices. */
    val shortFingerprint: String get() = fingerprint.take(8)
}

/** Why a transfer step failed, without ever surfacing a peer or crypto exception message. */
enum class ConfigTransferFailure {
    NETWORK,
    PROTOCOL,
    SESSION_MISMATCH,
    FINGERPRINT_MISMATCH,
    EXPIRED,
    REJECTED,
    TOO_LARGE,
    CRYPTO,
}

/** Deliberately message-only: the reason is an enum, never a string built from peer input. */
class ConfigTransferError(val reason: ConfigTransferFailure) :
    Exception("config transfer failed: $reason")
