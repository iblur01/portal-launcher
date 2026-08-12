package com.iblu01.portallauncher.domain.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillPriorityEngine
import com.iblu01.portallauncher.PillRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PillCatalogBuilderTest {
    private lateinit var builder: PillCatalogBuilder

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        builder = PillCatalogBuilder(PillPriorityEngine(context))
    }

    private fun entity(id: String, state: String, deviceClass: String = "") = HaEntity(
        id,
        state,
        JSONObject().put("friendly_name", id.substringAfter('.')).put("device_class", deviceClass),
        "2026-08-08T10:00:00Z",
    )

    private fun rule(entity: HaEntity, label: String = entity.name) =
        PillRule(entity.entityId, PillKind.GENERIC, label)

    @Test fun `catalog has one individual pill per compatible device without nine limit`() {
        val entities = (1..12).map { entity("switch.device_$it", if (it == 1) "on" else "off") }
        val snapshot = builder.build(entities.map(::rule), entities.associateBy { it.entityId })
        assertEquals(12, snapshot.devices.size)
        assertEquals(12, snapshot.resolvedDevices.size)
    }

    @Test fun `compatible discovery is catalogued without an explicit rule but not ranked dynamically`() {
        val discovered = entity("switch.discovered", "on")
        val snapshot = builder.build(emptyList(), mapOf(discovered.entityId to discovered))
        assertTrue(snapshot.devices.containsKey(PillRef.Device(discovered.entityId)))
        assertTrue(snapshot.dynamicCandidates.isEmpty())
    }

    @Test fun `disabled persisted rule remains in settings catalog but is hidden from runtime groups and ranking`() {
        val disabled = entity("switch.disabled", "on")
        val snapshot = builder.build(
            listOf(rule(disabled).copy(enabled = false)),
            mapOf(disabled.entityId to disabled),
        )
        val ref = PillRef.Device(disabled.entityId)
        assertTrue(snapshot.devices.containsKey(ref))
        assertTrue(ref in snapshot.disabledDeviceRefs)
        assertTrue(snapshot.groups.getValue(PillRef.KindGroup(PillKind.SWITCH)).resolvedMembers.isEmpty())
        assertTrue(snapshot.dynamicCandidates.isEmpty())
    }

    @Test fun `entity without existing panel compatibility is excluded`() {
        val unsupported = entity("button.reboot", "unknown")
        val snapshot = builder.build(listOf(rule(unsupported)), mapOf(unsupported.entityId to unsupported))
        assertTrue(snapshot.devices.isEmpty())
        assertNull(snapshot.availability[PillRef.Device(unsupported.entityId)])
    }

    @Test fun `automatic area and kind groups use stable ids and device can belong to both`() {
        val light = entity("light.kitchen", "on")
        val snapshot = builder.build(
            rules = listOf(rule(light)),
            states = mapOf(light.entityId to light),
            areaIdByEntity = mapOf(light.entityId to "area-42"),
            areaNameById = mapOf("area-42" to "Cuisine"),
        )
        val device = PillRef.Device(light.entityId)
        assertEquals(listOf(device), snapshot.groups.getValue(PillRef.AreaGroup("area-42")).members)
        assertEquals(listOf(device), snapshot.groups.getValue(PillRef.KindGroup(PillKind.LIGHTS)).members)
        assertEquals("Cuisine", snapshot.groups.getValue(PillRef.AreaGroup("area-42")).chip.label)
    }

    @Test fun `heterogeneous manual group has no guessed collective action`() {
        val light = entity("light.kitchen", "on")
        val lock = entity("lock.front", "locked")
        val manual = ManualPillGroup(
            "evening",
            "Soirée",
            null,
            listOf(PillRef.Device(light.entityId), PillRef.Device(lock.entityId)),
        )
        val snapshot = builder.build(listOf(rule(light), rule(lock)), listOf(light, lock).associateBy { it.entityId }, manualGroups = listOf(manual))
        val group = snapshot.groups.getValue(PillRef.ManualGroup("evening"))
        assertEquals(2, group.resolvedMembers.size)
        assertNull(group.collectiveAction)
    }

    @Test fun `manual group retains temporarily absent members while resolving only present ones`() {
        val present = entity("light.present", "on")
        val absentRef = PillRef.Device("light.temporarily_absent")
        val manual = ManualPillGroup(
            "stable-membership",
            "Groupe durable",
            null,
            listOf(PillRef.Device(present.entityId), absentRef),
        )

        val snapshot = builder.build(
            rules = listOf(rule(present), PillRule(absentRef.entityId, PillKind.LIGHTS, "Absente")),
            states = mapOf(present.entityId to present),
            manualGroups = listOf(manual),
        )
        val group = snapshot.groups.getValue(PillRef.ManualGroup(manual.id))

        assertEquals(manual.members, group.members)
        assertEquals(listOf(PillRef.Device(present.entityId)), group.resolvedMembers.map { it.ref })
    }

    @Test fun `group remains available with one available member and hides when all unavailable`() {
        val a = entity("light.a", "on")
        val b = entity("light.b", "unavailable")
        val rules = listOf(rule(a), rule(b))
        val areas = mapOf(a.entityId to "room", b.entityId to "room")
        val partial = builder.build(rules, listOf(a, b).associateBy { it.entityId }, areaIdByEntity = areas)
        val areaRef = PillRef.AreaGroup("room")
        assertEquals(Availability.AVAILABLE, partial.availability[areaRef])
        assertEquals(1, partial.groups.getValue(areaRef).resolvedMembers.size)

        val unavailableA = entity("light.a", "unknown")
        val total = builder.build(rules, listOf(unavailableA, b).associateBy { it.entityId }, areaIdByEntity = areas)
        assertEquals(Availability.UNAVAILABLE, total.availability[areaRef])
        assertFalse(total.resolve(areaRef)?.availability?.isRenderable ?: true)
    }

    @Test fun `global disconnect marks last snapshot stale instead of unavailable`() {
        val light = entity("light.kitchen", "on")
        val snapshot = builder.build(listOf(rule(light)), mapOf(light.entityId to light), connected = false)
        assertEquals(Availability.STALE, snapshot.availability[PillRef.Device(light.entityId)])
        assertTrue(snapshot.devices.containsKey(PillRef.Device(light.entityId)))
    }

    @Test fun `critical policy is typed and does not classify low battery as pin overriding alert`() {
        val alarm = entity("alarm_control_panel.home", "triggered")
        val pendingAlarm = entity("alarm_control_panel.pending", "pending")
        val battery = entity("sensor.remote_battery", "5", "battery")
        val snapshot = builder.build(
            listOf(rule(alarm), rule(pendingAlarm), rule(battery)),
            listOf(alarm, pendingAlarm, battery).associateBy { it.entityId },
        )
        assertEquals(AlertSeverity.CRITICAL, snapshot.resolve(PillRef.Device(alarm.entityId))?.alert?.severity)
        assertEquals(AlertSeverity.CRITICAL, snapshot.resolve(PillRef.Device(pendingAlarm.entityId))?.alert?.severity)
        assertNull(snapshot.resolve(PillRef.Device(battery.entityId))?.alert)
    }
}
