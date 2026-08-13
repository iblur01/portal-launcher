package com.iblu01.portallauncher.ui

import com.iblu01.portallauncher.HaEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reconciliation policy of the per-entity store (P1/P3): the HA push is authoritative as soon as
 * it brings news, an unconfirmed prediction expires, `assumed_state` never expires.
 */
class HaStatesTest {
    private var now = 1_000L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private fun states() = HaStates(nowMs = { now }, scheduleExpiry = { delay, action ->
        scheduled += delay to action
    })

    private fun entity(id: String, state: String, attrs: String = "{}") =
        HaEntity(id, state, JSONObject(attrs))

    @Test
    fun `la prediction survit a un push qui ne concerne pas l'entite`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off"), "light.b" to entity("light.b", "off")))
        s.applyOptimistic("light.a") { it.copy(state = "on") }
        // New snapshot: light.b moves, light.a is still "off" on the HA side.
        s.apply(mapOf("light.a" to entity("light.a", "off"), "light.b" to entity("light.b", "on")))
        assertEquals("on", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `un push apportant du neuf ecrase la prediction`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        s.applyOptimistic("light.a") { it.copy(state = "on") }
        s.apply(mapOf("light.a" to entity("light.a", "unavailable")))
        assertEquals("unavailable", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `sans confirmation la prediction expire au prochain push apres le TTL`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        s.applyOptimistic("light.a") { it.copy(state = "on") }
        now += 5_000
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        assertEquals("off", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `l'expiration planifiee revient a l'etat confirme meme sans aucun push`() {
        // HA unplugged: no snapshot will ever arrive, only the scheduled expiry can roll back.
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        s.applyOptimistic("light.a") { it.copy(state = "on") }
        assertEquals(1, scheduled.size)
        now += 5_000
        scheduled.single().second.invoke()
        assertEquals("off", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `l'expiration planifiee n'ecrase pas une confirmation arrivee entre temps`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        s.applyOptimistic("light.a") { it.copy(state = "on") }
        s.apply(mapOf("light.a" to entity("light.a", "on")))   // HA confirmed
        now += 5_000
        scheduled.single().second.invoke()
        assertEquals("on", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `assumed_state garde la prediction indefiniment`() {
        val s = states()
        s.apply(mapOf("switch.rf" to entity("switch.rf", "off", """{"assumed_state":true}""")))
        s.applyOptimistic("switch.rf") { it.copy(state = "on") }
        assertEquals(0, scheduled.size)   // no expiry is ever scheduled
        now += 60_000
        s.apply(mapOf("switch.rf" to entity("switch.rf", "off", """{"assumed_state":true}""")))
        assertEquals("on", s.stateOf("switch.rf").value?.state)
    }

    @Test
    fun `une prediction sans changement n'arme pas l'optimisme`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off")))
        s.applyOptimistic("light.a") { it }   // unpredictable service: identity
        assertEquals(0, scheduled.size)
        assertEquals("off", s.stateOf("light.a").value?.state)
    }

    @Test
    fun `une entite retiree du snapshot disparait du magasin`() {
        val s = states()
        s.apply(mapOf("light.a" to entity("light.a", "off"), "light.b" to entity("light.b", "off")))
        s.apply(mapOf("light.b" to entity("light.b", "off")))
        assertNull(s.stateOf("light.a").value)
        assertEquals(setOf("light.b"), s.entityIds())
    }
}
