package com.iblu01.portallauncher

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsConfigTransferTest {

    @Test
    fun `transfer restores settings and secrets but keeps receiver identity and widgets`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.haUrl = "https://home.example"
        prefs.haToken = "secret-ha-token"
        prefs.brokerHost = "mqtt.example"
        prefs.password = "secret-mqtt-password"
        prefs.gridScale = 1.2f
        prefs.deviceName = "Sender"
        prefs.widgetIds = listOf(41)
        prefs.appPlacements = listOf(
            AppPlacement("app:one", 0, 0, 0),
            AppPlacement("wg:41", 0, 1, 0, 2, 2),
        )

        val payload = prefs.exportTransferPayload()
        prefs.haUrl = "http://changed"
        prefs.haToken = "changed"
        prefs.brokerHost = "changed"
        prefs.password = "changed"
        prefs.gridScale = 0.8f
        prefs.deviceName = "Receiver"
        prefs.widgetIds = listOf(99)

        assertTrue(prefs.importTransferPayload(payload))
        assertEquals("https://home.example", prefs.haUrl)
        assertEquals("secret-ha-token", prefs.haToken)
        assertEquals("mqtt.example", prefs.brokerHost)
        assertEquals("secret-mqtt-password", prefs.password)
        assertEquals(1.2f, prefs.gridScale)
        assertEquals("Receiver", prefs.deviceName)
        assertEquals(listOf(99), prefs.widgetIds)
        assertEquals(listOf("app:one"), prefs.appPlacements.map { it.key })
        assertTrue(prefs.onboardingCompleted)
    }

    @Test
    fun `invalid payload leaves current configuration untouched`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.haUrl = "https://before.example"
        prefs.haToken = "before-token"

        assertFalse(prefs.importTransferPayload("not-json".toByteArray()))
        assertEquals("https://before.example", prefs.haUrl)
        assertEquals("before-token", prefs.haToken)
    }
}
