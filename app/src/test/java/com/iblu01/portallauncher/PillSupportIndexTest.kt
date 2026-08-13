package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Freezes the observable output of the indexed discovery: same candidates, same order, same
 * related lists as the full-scan version it replaces.
 */
class PillSupportIndexTest {

    private fun entity(id: String, state: String, deviceClass: String = "") = HaEntity(
        id,
        state,
        JSONObject().put("friendly_name", id.substringAfter('.')).put("device_class", deviceClass),
    )

    // A device with a principal owner and its sub-entities, a media device owning a battery switch,
    // and device-less entities linked only by their logicalKey prefix.
    private val entities = listOf(
        entity("vacuum.nicky", "docked"),
        entity("switch.nicky_spot_clean", "off"),
        entity("sensor.nicky_battery", "80", "battery"),
        entity("binary_sensor.nicky_dustbin", "off", "problem"),
        entity("media_player.hub", "playing"),
        entity("switch.hub_battery_saver", "on", "battery"),
        entity("light.salon", "on"),
        entity("sensor.salon_temperature", "21", "temperature"),
        entity("sensor.salon_humidity", "45", "humidity"),
        entity("light.cuisine", "off"),
        entity("switch.salon_battery", "on", "battery"),
    )

    private val registry = mapOf(
        "vacuum.nicky" to "vac-1",
        "switch.nicky_spot_clean" to "vac-1",
        "sensor.nicky_battery" to "vac-1",
        "binary_sensor.nicky_dustbin" to "vac-1",
        "media_player.hub" to "hub-1",
        "switch.hub_battery_saver" to "hub-1",
    )

    private fun related(candidates: List<PillCandidate>, entityId: String) =
        candidates.first { it.primary.entityId == entityId }.related.map { it.entityId }

    @Test fun `discovery keeps its primaries, their order and their kinds`() {
        val candidates = PillSupport.candidates(entities, registry)

        assertEquals(
            listOf(
                "vacuum.nicky",
                "switch.nicky_spot_clean",
                "media_player.hub",
                "light.salon",
                "light.cuisine",
            ),
            candidates.map { it.primary.entityId },
        )
        assertEquals(
            listOf(PillKind.VACUUM, PillKind.SWITCH, PillKind.MEDIA, PillKind.LIGHTS, PillKind.LIGHTS),
            candidates.map { it.kind },
        )
        assertEquals("Nicky", candidates.first().label)
    }

    @Test fun `related entities keep the snapshot order, by device id or by logical key`() {
        val candidates = PillSupport.candidates(entities, registry)

        assertEquals(
            listOf("sensor.nicky_battery", "binary_sensor.nicky_dustbin"),
            related(candidates, "vacuum.nicky"),
        )
        assertEquals(
            listOf("sensor.nicky_battery", "binary_sensor.nicky_dustbin"),
            related(candidates, "switch.nicky_spot_clean"),
        )
        // Device-less: only the sensors sharing the "salon" logical key, never light.cuisine.
        assertEquals(
            listOf("sensor.salon_temperature", "sensor.salon_humidity"),
            related(candidates, "light.salon"),
        )
        assertTrue(related(candidates, "media_player.hub").isEmpty())
        assertTrue(related(candidates, "light.cuisine").isEmpty())
    }

    @Test fun `a shared index produces exactly the same candidates as a locally built one`() {
        val shared = PillSupport.EntityIndex(entities, registry)
        assertEquals(
            PillSupport.candidates(entities, registry),
            PillSupport.candidates(entities, registry, shared),
        )
    }

    @Test fun `automatic enabling is unchanged whether the index is shared or not`() {
        val categories = mapOf("light.cuisine" to "diagnostic")
        val shared = PillSupport.EntityIndex(entities, registry)
        val expected = mapOf(
            "vacuum.nicky" to true,
            "switch.nicky_spot_clean" to false,   // the vacuum on the same device owns it
            "media_player.hub" to true,
            "light.salon" to true,
            "light.cuisine" to false,             // diagnostic entities stay quiet
        )

        PillSupport.candidates(entities, registry, shared).forEach { candidate ->
            val id = candidate.primary.entityId
            val scanned = PillSupport.isAutomaticallyEnabled(candidate, entities, registry, categories)
            val indexed = PillSupport.isAutomaticallyEnabled(candidate, entities, registry, categories, shared)
            assertEquals(id, expected.getValue(id), scanned)
            assertTrue(id, scanned == indexed)
        }
    }
}
