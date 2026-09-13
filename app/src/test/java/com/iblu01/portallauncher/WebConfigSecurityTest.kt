package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebConfigSecurityTest {
    @Test
    fun `wallpaper signature allow-list accepts only jpeg png and webp`() {
        assertTrue(hasSupportedImageSignature(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(hasSupportedImageSignature(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )))
        assertTrue(hasSupportedImageSignature("RIFF0000WEBP".toByteArray()))
        assertFalse(hasSupportedImageSignature("GIF89a".toByteArray()))
        assertFalse(hasSupportedImageSignature("<svg></svg>".toByteArray()))
        assertTrue(detectedImageMime("RIFF0000WEBP".toByteArray()) == "image/webp")
        assertFalse(detectedImageMime("GIF89a".toByteArray()) != null)
    }

    @Test
    fun `config read model reports secret presence but never returns secret values`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.haToken = "ha-secret-value"
        prefs.password = "mqtt-secret-value"
        prefs.voiceGeminiApiKey = "gemini-secret-value"

        val raw = webConfigJson(prefs)
        val json = JSONObject(raw)

        assertTrue(json.getBoolean("ha_token_configured"))
        assertTrue(json.getBoolean("mqtt_password_configured"))
        assertTrue(json.getBoolean("voice_gemini_key_configured"))
        assertFalse(json.has("ha_token"))
        assertFalse(json.has("password"))
        assertFalse(json.has("voice_gemini_key"))
        assertFalse(raw.contains("ha-secret-value"))
        assertFalse(raw.contains("mqtt-secret-value"))
        assertFalse(raw.contains("gemini-secret-value"))
    }

    @Test
    fun `blank mqtt password is reused only for the identical broker identity`() {
        val same = mqttTestCredentials(
            "broker.internal", 1883, "portal", "",
            "broker.internal", 1883, "portal", "stored-secret",
        )
        assertTrue(same.password == "stored-secret")

        val changedHost = mqttTestCredentials(
            "evil.internal", 1883, "portal", "",
            "broker.internal", 1883, "portal", "stored-secret",
        )
        val changedPort = mqttTestCredentials(
            "broker.internal", 8883, "portal", "",
            "broker.internal", 1883, "portal", "stored-secret",
        )
        val changedUser = mqttTestCredentials(
            "broker.internal", 1883, "attacker", "",
            "broker.internal", 1883, "portal", "stored-secret",
        )
        assertTrue(changedHost.password.isEmpty())
        assertTrue(changedPort.password.isEmpty())
        assertTrue(changedUser.password.isEmpty())
    }

    @Test
    fun `explicit mqtt password never falls back to stored credential`() {
        val credentials = mqttTestCredentials(
            "new.internal", 8883, "new-user", "new-secret",
            "broker.internal", 1883, "portal", "stored-secret",
        )
        assertTrue(credentials.password == "new-secret")
    }
}
