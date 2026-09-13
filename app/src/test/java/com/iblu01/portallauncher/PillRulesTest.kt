package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PillRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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

    @Test fun `every supported kind belongs to exactly one family`() {
        val legacyUnsupported = setOf(
            PillKind.AIR, PillKind.CLIMATE, PillKind.BATTERY,
            PillKind.PRESENCE, PillKind.ENERGY,
        )
        PillKind.values().filterNot { it in legacyUnsupported }.forEach { kind ->
            val families = PillFamily.values().filter { kind in it.kinds }
            assertEquals("kind $kind should be in exactly one family", 1, families.size)
        }
        legacyUnsupported.forEach { assertEquals(null, PillFamily.of(it)) }
    }

    @Test fun `scenes and cameras have their own settings families`() {
        assertEquals(PillFamily.SCENES, PillFamily.of(PillKind.SCENE))
        assertEquals(PillFamily.CAMERAS, PillFamily.of(PillKind.CAMERA))
    }

    @Test fun `family grouping matches plain-language buckets`() {
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.SAFETY))
        assertEquals(PillFamily.SECURITY, PillFamily.of(PillKind.OPENING))
        assertEquals(PillFamily.COMFORT, PillFamily.of(PillKind.THERMOSTAT))
        assertEquals(PillFamily.APPLIANCES, PillFamily.of(PillKind.VACUUM))
        assertEquals(PillFamily.LIGHTS, PillFamily.of(PillKind.LIGHTS))
        assertEquals(PillFamily.MEDIA, PillFamily.of(PillKind.MEDIA))
    }

    // --- friendlyEntityState ---

    @Test fun `binary sensors use plain words`() {
        assertEquals("Open", friendlyEntityState(context, entity("binary_sensor.front_door", "on", "door")))
        assertEquals("Closed", friendlyEntityState(context, entity("binary_sensor.front_door", "off", "door")))
        assertEquals("Motion detected", friendlyEntityState(context, entity("binary_sensor.hall", "on", "motion")))
        assertEquals("Alert", friendlyEntityState(context, entity("binary_sensor.smoke", "on", "smoke")))
    }

    @Test fun `sensors show their value and unit`() {
        assertEquals("21 °C", friendlyEntityState(context, entity("sensor.living_temp", "21.0", "temperature", "°C")))
        assertEquals("45 %", friendlyEntityState(context, entity("sensor.living_hum", "45", "humidity", "%")))
    }

    @Test fun `people and locks use everyday words`() {
        assertEquals("At home", friendlyEntityState(context, entity("person.marie", "home")))
        assertEquals("Away", friendlyEntityState(context, entity("person.marie", "not_home")))
        assertEquals("Locked", friendlyEntityState(context, entity("lock.door", "locked")))
        assertEquals("Unlocked", friendlyEntityState(context, entity("lock.door", "unlocked")))
    }

    @Test fun `common on off and media states`() {
        assertEquals("On", friendlyEntityState(context, entity("light.salon", "on")))
        assertEquals("Off", friendlyEntityState(context, entity("light.salon", "off")))
        assertEquals("Playing", friendlyEntityState(context, entity("media_player.tv", "playing")))
        assertEquals("Paused", friendlyEntityState(context, entity("media_player.tv", "paused")))
    }

    @Test fun `degraded states are readable`() {
        assertEquals("Unavailable", friendlyEntityState(context, entity("sensor.x", "unavailable")))
        assertEquals("—", friendlyEntityState(context, entity("sensor.x", "unknown")))
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
            "lawn_mower.x" to PillKind.LAWN_MOWER,
        )
        expected.forEach { (id, kind) ->
            val e = entity(id, "on")
            assertTrue(id, PillSupport.isSupported(e))
            assertEquals(id, kind, PillSupport.kind(e))
        }
    }

    @Test fun `principal appliances stay automatic while their switches do not`() {
        val vacuum = entity("vacuum.nicky", "docked")
        val carpetBoost = entity("switch.nicky_carpet_boost", "on")
        val candidates = PillSupport.candidates(
            listOf(vacuum, carpetBoost),
            mapOf(vacuum.entityId to "nicky", carpetBoost.entityId to "nicky"),
        ).associateBy { it.primary.entityId }

        assertTrue(PillSupport.isAutomaticallyEnabled(candidates.getValue(vacuum.entityId), listOf(vacuum, carpetBoost)))
        assertFalse(PillSupport.isAutomaticallyEnabled(
            candidates.getValue(carpetBoost.entityId),
            listOf(vacuum, carpetBoost),
            mapOf(vacuum.entityId to "nicky", carpetBoost.entityId to "nicky"),
        ))
    }

    @Test fun `physical standalone outlet stays automatic`() {
        val outlet = entity("switch.coffee_machine", "off", "outlet")
        val candidate = PillSupport.candidates(listOf(outlet), mapOf(outlet.entityId to "plug-1")).single()
        assertTrue(PillSupport.isAutomaticallyEnabled(candidate, listOf(outlet), mapOf(outlet.entityId to "plug-1")))
    }

    @Test fun `registry config and diagnostic entities are quiet by default`() {
        val config = entity("switch.sonos_crossfade", "off")
        val candidate = PillSupport.candidates(listOf(config)).single()
        assertFalse(PillSupport.isAutomaticallyEnabled(
            candidate,
            listOf(config),
            entityCategoryByEntity = mapOf(config.entityId to "config"),
        ))
    }

    @Test fun `main washer state stays automatic`() {
        val washer = entity("sensor.machine_a_laver_machine_state", "run", "enum")
        val candidate = PillSupport.candidates(listOf(washer), mapOf(washer.entityId to "washer-1")).single()
        assertTrue(PillSupport.isAutomaticallyEnabled(candidate, listOf(washer), mapOf(washer.entityId to "washer-1")))
    }

    @Test fun `only movement and opening binary sensors are supported`() {
        listOf("motion", "occupancy", "moving").forEach {
            assertEquals(PillKind.MOTION, PillSupport.kind(entity("binary_sensor.x", "on", it)))
            assertTrue(PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
        listOf("door", "window", "opening", "garage_door").forEach {
            assertEquals(PillKind.OPENING, PillSupport.kind(entity("binary_sensor.x", "on", it)))
            assertTrue(it, PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
        listOf("presence", "vibration", "sound", "running", "problem", "safety", "connectivity", "battery").forEach {
            assertFalse(it, PillSupport.isSupported(entity("binary_sensor.x", "on", it)))
        }
        assertFalse(PillSupport.isSupported(entity("person.x", "home")))
        assertFalse(PillSupport.isSupported(entity("device_tracker.x", "home")))
    }

    @Test fun `passive sensors and location entities are not pill candidates`() {
        listOf(
            "co", "co2", "pm1", "pm4", "ozone", "radon", "energy", "power", "current",
            "voltage", "temperature", "humidity", "battery", "illuminance", "signal_strength",
        ).forEach {
            assertFalse(it, PillSupport.isSupported(entity("sensor.x", "1", it)))
        }
        listOf("script.good_night", "person.alex", "device_tracker.phone").forEach {
            assertFalse(it, PillSupport.isSupported(entity(it, "on")))
        }
    }

    @Test fun `scenes and cameras are discovered and enabled like any other device`() {
        val scene = entity("scene.evening", "2024-01-01T00:00:00+00:00")
        val camera = entity("camera.hall", "idle")
        listOf(scene, camera).forEach { assertTrue(it.entityId, PillSupport.isSupported(it)) }
        assertEquals(PillKind.SCENE, PillSupport.kind(scene))
        assertEquals(PillKind.CAMERA, PillSupport.kind(camera))

        // Enabled by default: a disabled rule is hidden from the Maison page and cannot be
        // pinned, so opting them out would make requirements 3 and 5 unreachable. What keeps
        // them out of the way is their low base priority, not a disabled flag.
        val entities = listOf(scene, camera)
        PillSupport.candidates(entities).forEach { candidate ->
            assertTrue(
                candidate.primary.entityId,
                PillSupport.isAutomaticallyEnabled(candidate, entities),
            )
        }
    }

    @Test fun `advanced controls are not permanent chip candidates`() {
        listOf("button.x", "input_button.x", "number.x", "input_number.x", "select.x", "input_select.x").forEach {
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
