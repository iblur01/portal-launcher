package com.iblu01.portallauncher.transfer

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto core for transferring a launcher configuration between two devices.
 *
 * Ephemeral ECDH on P-256 gives a fresh shared secret per transfer, HKDF-SHA256 turns it into an
 * AES-256 key, and AES-GCM seals the payload under caller-provided associated data (the session id,
 * in practice) so a ciphertext captured from one session cannot be replayed into another.
 *
 * Everything here sticks to the JCA subset available on API 27: no AEADBadTagException-free
 * shortcuts, no android.security.keystore, no Base64 flavours added after O.
 */
object ConfigTransferCrypto {
    const val CURVE = "secp256r1"
    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    /** Fresh key pair, used for exactly one transfer and then dropped. */
    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(CURVE), random)
    }.generateKeyPair()

    fun encodePublicKey(key: PublicKey): ByteArray = key.encoded

    /** Rejects anything that is not a parsable X.509 EC point, so a peer cannot feed us garbage. */
    fun decodePublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    /**
     * ECDH + HKDF-SHA256. [salt] and [info] bind the derived key to the transfer context: two
     * sessions that agree on the same point still end up with different AES keys.
     */
    fun deriveSharedKey(
        privateKey: PrivateKey,
        peerPublicKey: PublicKey,
        salt: ByteArray,
        info: ByteArray,
    ): SecretKey {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val secret = agreement.generateSecret()
        val derived = hkdf(secret, salt, info, KEY_SIZE_BYTES)
        secret.fill(0)
        return SecretKeySpec(derived, "AES")
    }

    /** Returns `nonce || ciphertext || tag`. The nonce is random: never reuse a key across keys. */
    fun seal(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    /** Throws (AEADBadTagException or IllegalArgumentException) on tampered payload or wrong AAD. */
    fun open(key: SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(sealed.size > NONCE_SIZE_BYTES) { "sealed payload too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, sealed, 0, NONCE_SIZE_BYTES),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, NONCE_SIZE_BYTES, sealed.size - NONCE_SIZE_BYTES)
    }

    /** Comparison whose duration does not depend on where the first mismatch is. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun constantTimeEquals(a: String, b: String): Boolean =
        constantTimeEquals(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** RFC 5869 extract-then-expand, HMAC-SHA256. */
    fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val macLength = mac.macLength
        require(length in 1..(255 * macLength)) { "invalid HKDF output length" }

        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(macLength) else salt, "HmacSHA256"))
        val prk = mac.doFinal(secret)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, length - written)
            previous.copyInto(output, written, 0, take)
            written += take
            counter++
        }
        prk.fill(0)
        previous.fill(0)
        return output
    }
}
