package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Value equality on [HaEntity] (m2): two entities with the same id/state/attributes must be equal
 * even though `attributes` is a `JSONObject` with no structural equals. Without this, every HA push
 * yields an unequal `latestStates` map and the UI state emits on every push, defeating conflation.
 */
class HaEntityTest {

    private fun entity(id: String, state: String, attrs: Map<String, Any> = emptyMap()) =
        HaEntity(id, state, JSONObject(attrs))

    @Test fun `same id state and attributes are equal`() {
        val a = entity("light.a", "on", mapOf("brightness" to 255))
        val b = entity("light.a", "on", mapOf("brightness" to 255))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test fun `different state is not equal`() {
        assertNotEquals(entity("light.a", "on"), entity("light.a", "off"))
    }

    @Test fun `different attributes are not equal`() {
        assertNotEquals(
            entity("light.a", "on", mapOf("brightness" to 255)),
            entity("light.a", "on", mapOf("brightness" to 10)),
        )
    }

    @Test fun `different id is not equal`() {
        assertNotEquals(entity("light.a", "on"), entity("light.b", "on"))
    }

    @Test fun `equal entities collapse in a set (map dedup basis)`() {
        val set = setOf(
            entity("light.a", "on", mapOf("brightness" to 255)),
            entity("light.a", "on", mapOf("brightness" to 255)),
        )
        assertEquals(1, set.size)
    }

    @Test fun `copy preserves equality`() {
        val a = entity("light.a", "on", mapOf("brightness" to 255))
        assertEquals(a, a.copy())
        assertNotEquals(a, a.copy(state = "off"))
    }

    @Test fun `area resolver prefers entity assignment over device assignment`() {
        assertEquals(
            mapOf("light.kitchen" to "entity-area"),
            resolveAreaIds(
                entityAreaId = mapOf("light.kitchen" to "entity-area"),
                entityDeviceId = mapOf("light.kitchen" to "device-1"),
                deviceAreaId = mapOf("device-1" to "device-area"),
            ),
        )
    }

    @Test fun `area resolver falls back to device and omits unresolved entities`() {
        assertEquals(
            mapOf("light.kitchen" to "device-area"),
            resolveAreaIds(
                entityAreaId = mapOf("light.kitchen" to null, "switch.orphan" to null),
                entityDeviceId = mapOf("light.kitchen" to "device-1", "switch.orphan" to null),
                deviceAreaId = mapOf("device-1" to "device-area"),
            ),
        )
    }
}
