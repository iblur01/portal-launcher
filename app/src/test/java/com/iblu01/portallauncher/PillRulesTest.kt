package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillRulesTest {

    private fun entity(id: String, state: String, deviceClass: String = "", unit: String = "") =
        HaEntity(
            id,
            state,
            JSONObject()
                .put("friendly_name", id.substringAfter('.'))
                .put("device_class", deviceClass)
                .put("unit_of_measurement", unit),
        )

    // --- PillFamily ---

    @Test fun `every kind belongs to exactly one family`() {
        PillKind.values().forEach { kind ->
            val families = PillFamily.values().filter { kind in it.kinds }
            assertEquals("kind $kind should be in exactly one family", 1, families.size)
        }
    }

    @Test fun `family grouping matches plain-language buckets`() {
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.SAFETY))
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.OPENING))
        assertEquals(PillFamily.COMFORT, PillFamily.of(PillKind.THERMOSTAT))
        assertEquals(PillFamily.APPLIANCES, PillFamily.of(PillKind.VACUUM))
        assertEquals(PillFamily.LIGHTS_SCENES, PillFamily.of(PillKind.LIGHTS))
        assertEquals(PillFamily.MEDIA, PillFamily.of(PillKind.MEDIA))
        assertEquals(PillFamily.HOME, PillFamily.of(PillKind.PRESENCE))
    }

    // --- friendlyEntityState ---

    @Test fun `binary sensors use plain words`() {
        assertEquals("Ouverte", friendlyEntityState(entity("binary_sensor.front_door", "on", "door")))
        assertEquals("Fermée", friendlyEntityState(entity("binary_sensor.front_door", "off", "door")))
        assertEquals("Mouvement", friendlyEntityState(entity("binary_sensor.hall", "on", "motion")))
        assertEquals("Alerte", friendlyEntityState(entity("binary_sensor.smoke", "on", "smoke")))
    }

    @Test fun `sensors show their value and unit`() {
        assertEquals("21 °C", friendlyEntityState(entity("sensor.living_temp", "21.0", "temperature", "°C")))
        assertEquals("45 %", friendlyEntityState(entity("sensor.living_hum", "45", "humidity", "%")))
    }

    @Test fun `people and locks use everyday words`() {
        assertEquals("À la maison", friendlyEntityState(entity("person.marie", "home")))
        assertEquals("Absente", friendlyEntityState(entity("person.marie", "not_home")))
        assertEquals("Verrouillée", friendlyEntityState(entity("lock.door", "locked")))
        assertEquals("Déverrouillée", friendlyEntityState(entity("lock.door", "unlocked")))
    }

    @Test fun `common on off and media states`() {
        assertEquals("Allumé", friendlyEntityState(entity("light.salon", "on")))
        assertEquals("Éteint", friendlyEntityState(entity("light.salon", "off")))
        assertEquals("En lecture", friendlyEntityState(entity("media_player.tv", "playing")))
        assertEquals("En pause", friendlyEntityState(entity("media_player.tv", "paused")))
    }

    @Test fun `degraded states are readable`() {
        assertEquals("Indisponible", friendlyEntityState(entity("sensor.x", "unavailable")))
        assertEquals("—", friendlyEntityState(entity("sensor.x", "unknown")))
    }

    // --- deriveHaUrl (mDNS discovery) ---

    @Test fun `deriveHaUrl prefers the advertised internal url`() {
        val url = deriveHaUrl("192.168.1.10", 8123, mapOf("internal_url" to "http://home.local:8123/"))
        assertEquals("http://home.local:8123", url)
    }

    @Test fun `deriveHaUrl falls back to host and port`() {
        assertEquals("http://192.168.1.10:8123", deriveHaUrl("192.168.1.10", 8123, emptyMap()))
        assertEquals("http://192.168.1.10:8123", deriveHaUrl("192.168.1.10", 8123, mapOf("internal_url" to "  ")))
    }

    @Test fun `deriveHaUrl rejects unusable input`() {
        assertEquals(null, deriveHaUrl(null, 8123, emptyMap()))
        assertEquals(null, deriveHaUrl("", 0, emptyMap()))
    }

    @Test fun `candidates are unaffected by the family mapping`() {
        // Guard: introducing PillFamily must not change which entities become candidates.
        assertTrue(PillSupport.isSupported(entity("light.salon", "on")))
        assertEquals(PillKind.LIGHTS, PillSupport.kind(entity("light.salon", "on")))
    }

    @Test fun `quick controllable domains are discovered without name heuristics`() {
        val expected = mapOf(
            "humidifier.x" to PillKind.HUMIDIFIER, "water_heater.x" to PillKind.WATER_HEATER,
            "valve.x" to PillKind.VALVE, "siren.x" to PillKind.SIREN,
            "lawn_mower.x" to PillKind.LAWN_MOWER, "device_tracker.x" to PillKind.PRESENCE,
        )
        expected.forEach { (id, kind) ->
            val e = entity(id, "on")
            assertTrue(id, PillSupport.isSupported(e))
            assertEquals(id, kind, PillSupport.kind(e))
        }
    }

    @Test fun `activity sensors are distinct from people presence`() {
        listOf("motion", "occupancy", "presence", "moving", "vibration", "sound", "running").forEach {
            assertEquals(PillKind.GENERIC, PillSupport.kind(entity("binary_sensor.x", "on", it)))
            assertTrue(PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
        assertEquals(PillKind.PRESENCE, PillSupport.kind(entity("person.x", "home")))
        assertEquals(PillKind.PRESENCE, PillSupport.kind(entity("device_tracker.x", "home")))
    }

    @Test fun `only actionable binary alerts are chip candidates`() {
        listOf("problem", "safety", "connectivity", "battery").forEach {
            assertTrue(it, PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
        listOf("battery_charging", "plug", "power", "heat", "cold", "light").forEach {
            assertFalse(it, PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
    }

    @Test fun `air and energy sensors remain available but passive diagnostics do not`() {
        listOf("co", "co2", "pm1", "pm4", "ozone", "radon", "energy", "power", "current", "voltage").forEach {
            assertTrue(it, PillSupport.isSupported(entity("sensor.x", "1", it)))
        }
        listOf("illuminance", "atmospheric_pressure", "signal_strength", "water", "volume_storage", "duration", "timestamp", "uptime").forEach {
            assertFalse(it, PillSupport.isSupported(entity("sensor.x", "1", it)))
        }
    }

    @Test fun `advanced controls are not permanent chip candidates`() {
        listOf("button.x", "input_button.x", "number.x", "input_number.x", "select.x", "input_select.x", "camera.x").forEach {
            assertFalse(it, PillSupport.isSupported(entity(it, "on")))
        }
    }

    @Test fun `device registry links sensors whose names do not match`() {
        val valve = entity("valve.main", "open")
        val battery = entity("sensor.unrelated_name", "70", "battery")
        val candidate = PillSupport.candidates(
            listOf(valve, battery),
            mapOf(valve.entityId to "device-1", battery.entityId to "device-1"),
        ).single()
        assertEquals(valve.entityId, candidate.primary.entityId)
        assertEquals(listOf(battery.entityId), candidate.related.map { it.entityId })
    }

    @Test fun `device registry prevents similarly named devices from mixing`() {
        val valve = entity("valve.garden", "open")
        val battery = entity("sensor.garden_battery", "70", "battery")
        val candidates = PillSupport.candidates(
            listOf(valve, battery),
            mapOf(valve.entityId to "device-1", battery.entityId to "device-2"),
        )
        assertTrue(candidates.first { it.primary == valve }.related.isEmpty())
    }

    @Test fun `entities absent from registry keep legacy fallback`() {
        val lock = entity("lock.entry", "locked")
        val battery = entity("sensor.entry_battery", "70", "battery")
        val candidate = PillSupport.candidates(listOf(lock, battery)).single()
        assertEquals(listOf(battery.entityId), candidate.related.map { it.entityId })
    }
}
