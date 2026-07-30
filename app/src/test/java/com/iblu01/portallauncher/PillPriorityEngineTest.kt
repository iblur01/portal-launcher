package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class PillPriorityEngineTest {
    private lateinit var engine: PillPriorityEngine
    private lateinit var context: Context

    @Before fun setUp() {
        Locale.setDefault(Locale.US)
        context = ApplicationProvider.getApplicationContext()
        engine = PillPriorityEngine(context)
    }

    private fun entity(id: String, state: String, deviceClass: String = "", changed: String = "2026-07-16T20:00:00Z") =
        HaEntity(id, state, JSONObject().put("friendly_name", id.substringAfter('.')).put("device_class", deviceClass), changed)

    @Test fun `active washer wins over closed door`() {
        val washer = entity("sensor.washer", "running")
        val door = entity("binary_sensor.front_door", "off", "door")
        val rules = listOf(PillRule("sensor.washer", PillKind.APPLIANCE, "Machine"), PillRule("binary_sensor.front_door", PillKind.OPENING, "Porte"))
        val result = engine.select(rules, mapOf(washer.entityId to washer, door.entityId to door))
        assertEquals("sensor.washer", result.first().entityId)
        assertEquals("All closed", result.last().value)
    }

    @Test fun `danger wins over activity`() {
        val smoke = entity("binary_sensor.smoke", "on", "smoke")
        val washer = entity("sensor.washer", "running")
        val rules = listOf(PillRule(smoke.entityId, PillKind.SAFETY, "Fumée"), PillRule(washer.entityId, PillKind.APPLIANCE, "Machine"))
        val result = engine.select(rules, mapOf(smoke.entityId to smoke, washer.entityId to washer))
        assertEquals(smoke.entityId, result.first().entityId)
        assertEquals("critical", result.first().state)
    }

    @Test fun `selection is limited to nine`() {
        val entities = (1..12).associate { i -> "sensor.task_$i" to entity("sensor.task_$i", "running") }
        val rules = entities.keys.map { PillRule(it, PillKind.APPLIANCE, it) }
        assertEquals(9, engine.select(rules, entities).size)
    }

    @Test fun `supported opening is classified`() {
        assertEquals(PillKind.OPENING, PillSupport.kind(entity("binary_sensor.kitchen_window", "on", "window")))
        assertTrue(PillSupport.isSupported(entity("binary_sensor.kitchen_window", "on", "window")))
    }

    @Test fun `lock absorbs its battery instead of creating another pill`() {
        val lock = entity("lock.serrure", "locked")
        val battery = entity("sensor.serrure_batterie", "67", "battery")
        val candidates = PillSupport.candidates(listOf(lock, battery))
        assertEquals(1, candidates.size)
        assertEquals(lock.entityId, candidates.single().primary.entityId)
        assertEquals(listOf(battery.entityId), candidates.single().related.map { it.entityId })
    }

    @Test fun `washing machine state absorbs cycle and completion sensors`() {
        val state = entity("sensor.machine_a_laver_machine_state", "run", "enum")
        val cycle = entity("sensor.machine_a_laver_etat_du_cycle", "wash", "enum")
        val completion = entity("sensor.machine_a_laver_completion_time", "2026-07-16T23:53:15Z", "timestamp")
        val candidate = PillSupport.candidates(listOf(state, cycle, completion)).single()
        assertEquals(PillKind.APPLIANCE, candidate.kind)
        assertEquals(setOf(cycle.entityId, completion.entityId), candidate.related.map { it.entityId }.toSet())
    }

    @Test fun `washing machine accepts HA completion timestamp with numeric offset`() {
        val state = entity("sensor.machine_a_laver_machine_state", "run", "enum", "2026-07-17T08:20:17.506099+00:00")
        val cycle = entity("sensor.machine_a_laver_etat_du_cycle", "wash", "enum")
        val completion = entity("sensor.machine_a_laver_completion_time", "2026-07-17T09:38:03+00:00", "timestamp", "2026-07-17T08:20:03.788505+00:00")
        val rule = PillRule(state.entityId, PillKind.APPLIANCE, "Machine à laver", relatedEntityIds = listOf(cycle.entityId, completion.entityId))
        val states = listOf(state, cycle, completion).associateBy { it.entityId }

        val chip = engine.select(listOf(rule), states, nowMs = java.time.Instant.parse("2026-07-17T08:45:00Z").toEpochMilli()).single()

        assertEquals("Reste 54 min", chip.value)
        assertTrue(chip.progress in 0.31f..0.33f)
    }

    @Test fun `normal temperature remains as low priority fallback`() {
        val temperature = entity("sensor.salon_temperature", "21.4", "temperature")
        val rule = PillRule(temperature.entityId, PillKind.CLIMATE, "Salon")
        val chip = engine.select(listOf(rule), mapOf(temperature.entityId to temperature)).single()
        assertEquals(2, chip.priority)
        assertEquals("ok", chip.state)
    }

    @Test fun `temperature candidate absorbs matching humidity`() {
        val temperature = entity("sensor.salon_temperature", "21.4", "temperature")
        val humidity = entity("sensor.salon_humidity", "48", "humidity")
        val candidate = PillSupport.candidates(listOf(temperature, humidity)).single()
        assertEquals(temperature.entityId, candidate.primary.entityId)
        assertEquals(listOf(humidity.entityId), candidate.related.map { it.entityId })
    }

    @Test fun `openings are grouped without battery in value`() {
        val kitchen = entity("binary_sensor.kitchen_window", "on", "window")
        val terrace = entity("binary_sensor.terrace_door", "off", "door")
        val battery = entity("sensor.terrace_door_battery", "87", "battery")
        val rules = listOf(
            PillRule(kitchen.entityId, PillKind.OPENING, "Cuisine"),
            PillRule(terrace.entityId, PillKind.OPENING, "Terrasse", relatedEntityIds = listOf(battery.entityId)),
        )
        val chip = engine.select(rules, listOf(kitchen, terrace, battery).associateBy { it.entityId }).single()
        assertEquals("Doors & windows", chip.label)
        assertEquals("1 open", chip.value)
        assertTrue(!chip.value.contains("87"))
        assertEquals(setOf("Open", "Closed"), chip.details.map { it.value }.toSet())
    }

    @Test fun `temperatures are grouped with min max and room details`() {
        val salon = entity("sensor.salon_temperature", "21.4", "temperature")
        val chambre = entity("sensor.chambre_temperature", "19.2", "temperature")
        val rules = listOf(
            PillRule(salon.entityId, PillKind.CLIMATE, "Salon Température"),
            PillRule(chambre.entityId, PillKind.CLIMATE, "Chambre Température"),
        )
        val chip = engine.select(rules, listOf(salon, chambre).associateBy { it.entityId }).single()
        assertEquals("Temperatures", chip.label)
        assertEquals("Min 19.2° · Max 21.4°", chip.value)
        assertEquals(setOf("Salon", "Chambre"), chip.details.map { it.label }.toSet())
    }

    @Test fun `nominal alarm and lock remain visible for reassurance`() {
        val alarm = entity("alarm_control_panel.home", "disarmed")
        val lock = entity("lock.front_door", "locked")
        val rules = listOf(
            PillRule(alarm.entityId, PillKind.SAFETY, "Home Alarm"),
            PillRule(lock.entityId, PillKind.LOCK, "Serrure"),
        )
        val chips = engine.select(rules, listOf(alarm, lock).associateBy { it.entityId })
        assertEquals(setOf("Security", "Serrure"), chips.map { it.label }.toSet())
        assertEquals(setOf("Désarmée", "Locked"), chips.map { it.value }.toSet())
    }

    @Test fun `lights are grouped into one pill`() {
        val salon = entity("light.salon", "on")
        val cuisine = entity("light.cuisine", "off")
        val rules = listOf(
            PillRule(salon.entityId, PillKind.LIGHTS, "Salon"),
            PillRule(cuisine.entityId, PillKind.LIGHTS, "Cuisine"),
        )
        val chip = engine.select(rules, listOf(salon, cuisine).associateBy { it.entityId }).single()
        assertEquals("Lights", chip.label)
        assertEquals("1 on", chip.value)
        assertEquals(setOf("On", "Off"), chip.details.map { it.value }.toSet())
    }

    @Test fun `media players are grouped`() {
        val tv = entity("media_player.tv", "playing")
        val speaker = entity("media_player.speaker", "idle")
        val rules = listOf(PillRule(tv.entityId, PillKind.MEDIA, "TV"), PillRule(speaker.entityId, PillKind.MEDIA, "Enceinte"))
        val chip = engine.select(rules, listOf(tv, speaker).associateBy { it.entityId }).single()
        assertEquals("Media", chip.label)
        assertEquals("Paused", chip.value)
    }

    private fun entityWith(id: String, state: String, attrs: JSONObject) =
        HaEntity(id, state, attrs.put("friendly_name", id.substringAfter('.')), "2026-07-16T20:00:00Z")

    @Test fun `switch cover fan climate are classified by domain`() {
        assertEquals(PillKind.SWITCH, PillSupport.kind(entity("switch.bureau", "on")))
        assertEquals(PillKind.COVER, PillSupport.kind(entity("cover.salon", "open")))
        assertEquals(PillKind.THERMOSTAT, PillSupport.kind(entity("climate.salon", "heat")))
        assertEquals(PillKind.FAN, PillSupport.kind(entity("fan.chambre", "on")))
        assertEquals(PillKind.PURIFIER, PillSupport.kind(entity("fan.purificateur_qg", "on")))
    }

    @Test fun `switch pill shows on off state`() {
        val sw = entity("switch.bureau", "on")
        val chip = engine.select(listOf(PillRule(sw.entityId, PillKind.SWITCH, "Bureau")), mapOf(sw.entityId to sw)).single()
        assertEquals("On", chip.value)
        assertEquals("active", chip.state)
        assertEquals(PillKind.SWITCH, chip.kind)
    }

    @Test fun `cover pill shows position when available`() {
        val cover = entityWith("cover.salon", "open", JSONObject().put("current_position", 60))
        val chip = engine.select(listOf(PillRule(cover.entityId, PillKind.COVER, "Salon")), mapOf(cover.entityId to cover)).single()
        assertEquals("Open · 60%", chip.value)
        assertEquals(PillKind.COVER, chip.kind)
    }

    @Test fun `thermostat pill shows target temperature`() {
        val climate = entityWith("climate.salon", "heat", JSONObject().put("temperature", 21.0).put("current_temperature", 19.5))
        val chip = engine.select(listOf(PillRule(climate.entityId, PillKind.THERMOSTAT, "Salon")), mapOf(climate.entityId to climate)).single()
        assertEquals("21°", chip.value)
        assertEquals(PillKind.THERMOSTAT, chip.kind)
        assertEquals("active", chip.state)
    }
}
