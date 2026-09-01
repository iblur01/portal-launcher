package com.iblu01.portallauncher.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HaIntegrationsSettingsPageTest {
    @Test
    fun `groups entities by integration and counts distinct devices`() {
        val entries = buildHaIntegrationEntries(
            platformByEntity = mapOf(
                "media_player.kitchen" to "sonos",
                "media_player.living_room" to "sonos",
                "sensor.living_room_battery" to "sonos",
                "media_player.tv" to "samsungtv",
            ),
            deviceIdByEntity = mapOf(
                "media_player.kitchen" to "speaker-1",
                "media_player.living_room" to "speaker-2",
                "sensor.living_room_battery" to "speaker-2",
                "media_player.tv" to "tv-1",
            ),
        )

        assertEquals(listOf("sonos", "samsungtv"), entries.map { it.domain })
        assertEquals(3, entries[0].entityCount)
        assertEquals(2, entries[0].deviceCount)
        assertEquals("Samsung Smart TV", entries[1].label)
    }

    @Test
    fun `keeps device-less integrations visible`() {
        val entries = buildHaIntegrationEntries(
            platformByEntity = mapOf("weather.home" to "met"),
            deviceIdByEntity = emptyMap(),
        )

        assertEquals(1, entries.single().entityCount)
        assertEquals(0, entries.single().deviceCount)
    }
}
