package com.iblu01.portallauncher.transfer

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigTransferCryptoTest {
    private val salt = "salt".toByteArray()
    private val info = "portal-launcher-config-transfer".toByteArray()

    private fun agreedKeys(): Pair<javax.crypto.SecretKey, javax.crypto.SecretKey> {
        val sender = ConfigTransferCrypto.generateKeyPair()
        val receiver = ConfigTransferCrypto.generateKeyPair()
        val receiverPublic =
            ConfigTransferCrypto.decodePublicKey(ConfigTransferCrypto.encodePublicKey(receiver.public))
        val senderPublic =
            ConfigTransferCrypto.decodePublicKey(ConfigTransferCrypto.encodePublicKey(sender.public))
        return ConfigTransferCrypto.deriveSharedKey(sender.private, receiverPublic, salt, info) to
            ConfigTransferCrypto.deriveSharedKey(receiver.private, senderPublic, salt, info)
    }

    @Test fun `both sides derive the same aes key through encoded public keys`() {
        val (a, b) = agreedKeys()
        assertArrayEquals(a.encoded, b.encoded)
        assertEquals(ConfigTransferCrypto.KEY_SIZE_BYTES, a.encoded.size)
        assertEquals("AES", a.algorithm)
    }

    @Test fun `a third party key derives a different secret`() {
        val sender = ConfigTransferCrypto.generateKeyPair()
        val receiver = ConfigTransferCrypto.generateKeyPair()
        val attacker = ConfigTransferCrypto.generateKeyPair()
        val honest = ConfigTransferCrypto.deriveSharedKey(sender.private, receiver.public, salt, info)
        val forged = ConfigTransferCrypto.deriveSharedKey(sender.private, attacker.public, salt, info)
        assertFalse(ConfigTransferCrypto.constantTimeEquals(honest.encoded, forged.encoded))
    }

    @Test fun `different info separates keys from the same shared point`() {
        val sender = ConfigTransferCrypto.generateKeyPair()
        val receiver = ConfigTransferCrypto.generateKeyPair()
        val first = ConfigTransferCrypto.deriveSharedKey(sender.private, receiver.public, salt, info)
        val second =
            ConfigTransferCrypto.deriveSharedKey(sender.private, receiver.public, salt, "other".toByteArray())
        assertFalse(ConfigTransferCrypto.constantTimeEquals(first.encoded, second.encoded))
    }

    @Test fun `round trip returns the plaintext`() {
        val (senderKey, receiverKey) = agreedKeys()
        val payload = """{"ha_url":"http://homeassistant.local:8123"}""".toByteArray()
        val aad = "session-id".toByteArray()
        val sealed = ConfigTransferCrypto.seal(senderKey, payload, aad)
        assertArrayEquals(payload, ConfigTransferCrypto.open(receiverKey, sealed, aad))
    }

    @Test fun `sealing twice uses a fresh nonce`() {
        val (key, _) = agreedKeys()
        val aad = "session-id".toByteArray()
        val first = ConfigTransferCrypto.seal(key, "payload".toByteArray(), aad)
        val second = ConfigTransferCrypto.seal(key, "payload".toByteArray(), aad)
        val nonceSize = ConfigTransferCrypto.NONCE_SIZE_BYTES
        assertNotEquals(
            first.copyOf(nonceSize).toList(),
            second.copyOf(nonceSize).toList(),
        )
        assertFalse(ConfigTransferCrypto.constantTimeEquals(first, second))
    }

    @Test fun `tampered ciphertext fails authentication`() {
        val (senderKey, receiverKey) = agreedKeys()
        val aad = "session-id".toByteArray()
        val sealed = ConfigTransferCrypto.seal(senderKey, "payload".toByteArray(), aad)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        try {
            ConfigTransferCrypto.open(receiverKey, sealed, aad)
            fail("tampered ciphertext must not open")
        } catch (expected: AEADBadTagException) {
            // expected
        }
    }

    @Test fun `wrong aad fails authentication`() {
        val (senderKey, receiverKey) = agreedKeys()
        val sealed = ConfigTransferCrypto.seal(senderKey, "payload".toByteArray(), "session-a".toByteArray())
        try {
            ConfigTransferCrypto.open(receiverKey, sealed, "session-b".toByteArray())
            fail("payload must not open under another session id")
        } catch (expected: AEADBadTagException) {
            // expected
        }
    }

    @Test fun `truncated payload is rejected before decryption`() {
        val (key, _) = agreedKeys()
        try {
            ConfigTransferCrypto.open(key, ByteArray(ConfigTransferCrypto.NONCE_SIZE_BYTES), "aad".toByteArray())
            fail("nonce-only payload must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `malformed public key is rejected`() {
        try {
            ConfigTransferCrypto.decodePublicKey(byteArrayOf(1, 2, 3))
            fail("garbage must not decode as a public key")
        } catch (expected: java.security.GeneralSecurityException) {
            // expected
        }
    }

    @Test fun `hkdf matches rfc 5869 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val hkdfSalt = ByteArray(13) { it.toByte() }
        val hkdfInfo = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = ConfigTransferCrypto.hkdf(ikm, hkdfSalt, hkdfInfo, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test fun `constant time equality behaves like equals`() {
        assertTrue(ConfigTransferCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(ConfigTransferCrypto.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(ConfigTransferCrypto.constantTimeEquals(byteArrayOf(1), byteArrayOf(1, 2)))
        assertTrue(ConfigTransferCrypto.constantTimeEquals("abc", "abc"))
        assertFalse(ConfigTransferCrypto.constantTimeEquals("abc", "abd"))
    }
}
