package com.iblu01.portallauncher.transfer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Wire-format and address-policy tests. Everything here is pure JVM: the protocol object has no
 * Android dependency precisely so these rules can be pinned down without an emulator.
 */
class ConfigTransferProtocolTest {

    private val sessionId = "0123456789abcdef0123456789abcdef"
    private val publicKey = ConfigTransferCrypto.encodePublicKey(
        ConfigTransferCrypto.generateKeyPair().public,
    )
    private val fingerprint = ConfigTransferProtocol.fingerprint(publicKey)

    @Test
    fun `fingerprint is lowercase hex sha256 of the encoded key`() {
        val expected = MessageDigest.getInstance("SHA-256").digest(publicKey)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, fingerprint)
        assertEquals(64, fingerprint.length)
    }

    @Test
    fun `key encoding round trips`() {
        val decoded = ConfigTransferProtocol.decodeKey(ConfigTransferProtocol.encodeKey(publicKey))
        assertNotNull(decoded)
        assertTrue(publicKey.contentEquals(decoded))
    }

    @Test
    fun `key decoding rejects garbage and oversize input`() {
        assertNull(ConfigTransferProtocol.decodeKey(null))
        assertNull(ConfigTransferProtocol.decodeKey("   "))
        assertNull(ConfigTransferProtocol.decodeKey("not base64 !!"))
        assertNull(ConfigTransferProtocol.decodeKey("A".repeat(600)))
    }

    @Test
    fun `advertisement carries only public material`() {
        val attributes = ConfigTransferProtocol.advertisementAttributes(
            sessionId = sessionId,
            displayName = "Kitchen  panel ",
            fingerprint = fingerprint,
        )
        assertEquals(setOf("v", "sid", "dn", "fp"), attributes.keys)
        assertEquals("1", attributes["v"])
        assertEquals("Kitchen panel", attributes["dn"])
        assertEquals(fingerprint, attributes["fp"])
        // No key material, no token: everything in the record is a public identifier.
        val encodedKey = ConfigTransferProtocol.encodeKey(publicKey)
        assertFalse(attributes.values.any { it.contains(encodedKey) })
    }

    @Test
    fun `parseAdvertisement accepts a well formed private-address record`() {
        val candidate = ConfigTransferProtocol.parseAdvertisement(
            serviceName = "portal-abc",
            host = "192.168.1.42",
            port = 8765,
            attributes = ConfigTransferProtocol.advertisementAttributes(sessionId, "Hall", fingerprint),
        )
        assertNotNull(candidate)
        assertEquals("Hall", candidate!!.displayName)
        assertEquals(sessionId, candidate.sessionId)
        assertEquals("http://192.168.1.42:8765", candidate.baseUrl)
        assertEquals(fingerprint.take(8), candidate.shortFingerprint)
    }

    @Test
    fun `parseAdvertisement rejects bad version session fingerprint host and port`() {
        val valid = ConfigTransferProtocol.advertisementAttributes(sessionId, "Hall", fingerprint)
        fun parse(
            host: String = "192.168.1.42",
            port: Int = 8765,
            attributes: Map<String, String> = valid,
        ) = ConfigTransferProtocol.parseAdvertisement("portal-abc", host, port, attributes)

        assertNull(parse(attributes = valid + ("v" to "2")))
        assertNull(parse(attributes = valid - "v"))
        assertNull(parse(attributes = valid + ("sid" to "short")))
        assertNull(parse(attributes = valid + ("fp" to "zz")))
        assertNull(parse(host = "8.8.8.8"))
        assertNull(parse(host = "portal.local"))
        assertNull(parse(port = 0))
        assertNull(parse(port = 70000))
    }

    @Test
    fun `parseAdvertisement falls back to the service name when the display name is empty`() {
        val attributes = ConfigTransferProtocol.advertisementAttributes(sessionId, "  ", fingerprint)
        assertEquals(
            "portal-abc",
            ConfigTransferProtocol.parseAdvertisement("portal-abc", "10.0.0.5", 8765, attributes)
                ?.displayName,
        )
    }

    @Test
    fun `hello round trips and rejects malformed bodies`() {
        val body = ConfigTransferProtocol.helloJson(sessionId, publicKey, 1_000L)
        val hello = ConfigTransferProtocol.parseHello(body)
        assertNotNull(hello)
        assertEquals(sessionId, hello!!.sessionId)
        assertEquals(1_000L, hello.expiresInMs)
        assertEquals(fingerprint, hello.fingerprint)

        assertNull(ConfigTransferProtocol.parseHello(null))
        assertNull(ConfigTransferProtocol.parseHello("nonsense"))
        assertNull(ConfigTransferProtocol.parseHello(JSONObject(body).put("v", 99).toString()))
        assertNull(ConfigTransferProtocol.parseHello(JSONObject(body).put("session", "x").toString()))
        assertNull(ConfigTransferProtocol.parseHello(JSONObject(body).put("pubkey", "!!").toString()))
        // A TTL longer than the session's own is a protocol violation, not a generous receiver.
        assertNull(
            ConfigTransferProtocol.parseHello(
                JSONObject(body).put("expires_in_ms", ConfigTransferSession.TTL_MS + 1).toString(),
            ),
        )
        assertNull(
            ConfigTransferProtocol.parseHello(JSONObject(body).put("expires_in_ms", 0).toString()),
        )
    }

    @Test
    fun `offer round trips and rejects malformed bodies`() {
        val offer = ConfigTransferProtocol.parseOffer(
            ConfigTransferProtocol.offerJson(sessionId, publicKey),
        )
        assertNotNull(offer)
        assertEquals(sessionId, offer!!.sessionId)
        assertEquals(fingerprint, offer.fingerprint)

        assertNull(ConfigTransferProtocol.parseOffer("{}"))
        assertNull(ConfigTransferProtocol.parseOffer("""{"v":1,"session":"$sessionId"}"""))
    }

    @Test
    fun `isLocalAddress accepts loopback link-local and private ranges`() {
        listOf(
            "127.0.0.1",
            "10.1.2.3",
            "172.16.0.9",
            "192.168.0.1",
            "169.254.4.4",
            "::1",
            "fe80::1",
            "fd12:3456::1",
            "[fd12:3456::1]",
            "fe80::1%wlan0",
        ).forEach { assertTrue(it, ConfigTransferProtocol.isLocalAddress(it)) }
    }

    @Test
    fun `isLocalAddress rejects public addresses and anything needing a lookup`() {
        listOf(
            null,
            "",
            "  ",
            "8.8.8.8",
            "172.32.0.1",
            "2001:4860:4860::8888",
            "example.com",
            "portal.local",
            "not-an-address",
        ).forEach { assertFalse(it.orEmpty(), ConfigTransferProtocol.isLocalAddress(it)) }
    }

    @Test
    fun `display name is single line and bounded`() {
        assertEquals("a b", ConfigTransferProtocol.sanitizeDisplayName("  a 	 b  "))
        assertEquals(64, ConfigTransferProtocol.sanitizeDisplayName("x".repeat(200)).length)
        assertEquals("clean", ConfigTransferProtocol.sanitizeDisplayName("clean "))
    }
}
