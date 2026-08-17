package com.iblu01.portallauncher.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUrlsTest {

    @Test
    fun `a bare host gets an http scheme`() {
        assertEquals("http://homeassistant.local:8123", OnboardingUrls.normalizeHaUrl("homeassistant.local:8123"))
    }

    @Test
    fun `trailing slashes and blanks are trimmed`() {
        assertEquals("http://192.168.1.20:8123", OnboardingUrls.normalizeHaUrl("  http://192.168.1.20:8123/  "))
    }

    @Test
    fun `https is preserved`() {
        assertEquals("https://home.example.com", OnboardingUrls.normalizeHaUrl("https://home.example.com/"))
    }

    @Test
    fun `valid addresses are accepted`() {
        listOf(
            "http://homeassistant.local:8123",
            "https://ha.example.com",
            "192.168.1.20:8123",
            "homeassistant.local",
        ).forEach { assertTrue(it, OnboardingUrls.isValidHaUrl(it)) }
    }

    @Test
    fun `empty and malformed addresses are rejected`() {
        listOf("", "   ", "ftp://homeassistant.local", "http://", "http:// spaced host").forEach {
            assertFalse(it, OnboardingUrls.isValidHaUrl(it))
        }
    }

    @Test
    fun `the mqtt broker is suggested from the home assistant host`() {
        assertEquals("192.168.1.20", OnboardingUrls.suggestedMqttHost("http://192.168.1.20:8123"))
        assertEquals("homeassistant.local", OnboardingUrls.suggestedMqttHost("http://homeassistant.local:8123"))
    }

    @Test
    fun `mdns warning is limited to local hostnames`() {
        assertTrue(OnboardingUrls.usesMdnsHostname("http://homeassistant.local:8123"))
        assertTrue(OnboardingUrls.usesMdnsHostname("HTTPS://HA.LOCAL"))
        assertFalse(OnboardingUrls.usesMdnsHostname("http://192.168.1.20:8123"))
        assertFalse(OnboardingUrls.usesMdnsHostname("https://ha.example.com"))
    }

    @Test
    fun `an unusable address falls back to the default broker host`() {
        assertEquals("homeassistant.local", OnboardingUrls.suggestedMqttHost(""))
        assertEquals("homeassistant.local", OnboardingUrls.suggestedMqttHost("nonsense://"))
    }

    @Test
    fun `ports are validated`() {
        assertTrue(OnboardingUrls.isValidPort("1883"))
        assertTrue(OnboardingUrls.isValidPort(" 8883 "))
        assertFalse(OnboardingUrls.isValidPort("0"))
        assertFalse(OnboardingUrls.isValidPort("70000"))
        assertFalse(OnboardingUrls.isValidPort(""))
        assertFalse(OnboardingUrls.isValidPort("abc"))
    }
}
