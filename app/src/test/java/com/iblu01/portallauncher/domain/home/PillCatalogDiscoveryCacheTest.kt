package com.iblu01.portallauncher.domain.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillPriorityEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Discovery is quadratic on ~765 entities; it must run on entity-set changes only, not on the
 * several state pushes per second that carry the very same keys.
 */
@RunWith(RobolectricTestRunner::class)
class PillCatalogDiscoveryCacheTest {
    private lateinit var builder: PillCatalogBuilder

    @Before fun setUp() {
        builder = PillCatalogBuilder(PillPriorityEngine(ApplicationProvider.getApplicationContext<Context>()))
    }

    private fun entity(id: String, state: String) = HaEntity(
        id,
        state,
        JSONObject().put("friendly_name", id.substringAfter('.')),
        "2026-08-08T10:00:00Z",
    )

    private fun states(vararg entities: HaEntity) = entities.associateBy { it.entityId }

    @Test fun `state pushes on an unchanged key set reuse the discovered rules`() {
        val on = states(entity("light.salon", "on"), entity("switch.plug", "off"))
        val off = states(entity("light.salon", "off"), entity("switch.plug", "on"))

        builder.build(emptyList(), on)
        assertEquals(1, builder.discoveryPasses)

        repeat(5) {
            builder.build(emptyList(), off)
            builder.build(emptyList(), on)
        }
        assertEquals(1, builder.discoveryPasses)
    }

    @Test fun `adding an entity refreshes the discovery`() {
        val before = states(entity("light.salon", "on"))
        val after = states(entity("light.salon", "on"), entity("light.cuisine", "off"))

        builder.build(emptyList(), before)
        val snapshot = builder.build(emptyList(), after)

        assertEquals(2, builder.discoveryPasses)
        assertTrue(snapshot.devices.containsKey(PillRef.Device("light.cuisine")))
    }

    @Test fun `removing an entity refreshes the discovery`() {
        val both = states(entity("light.salon", "on"), entity("light.cuisine", "off"))

        builder.build(emptyList(), both)
        val snapshot = builder.build(emptyList(), states(entity("light.salon", "on")))

        assertEquals(2, builder.discoveryPasses)
        assertTrue(snapshot.devices.containsKey(PillRef.Device("light.salon")))
        assertFalse(snapshot.devices.containsKey(PillRef.Device("light.cuisine")))
    }

    @Test fun `a registry change refreshes the discovery even on the same key set`() {
        val snapshot = states(entity("media_player.hub", "playing"), entity("switch.hub_led_ring", "on"))

        builder.build(emptyList(), snapshot)
        builder.build(emptyList(), snapshot, deviceIdByEntity = mapOf("media_player.hub" to "hub-1"))
        assertEquals(2, builder.discoveryPasses)

        builder.build(
            emptyList(),
            snapshot,
            deviceIdByEntity = mapOf("media_player.hub" to "hub-1"),
            entityCategoryByEntity = mapOf("switch.hub_led_ring" to "config"),
        )
        assertEquals(3, builder.discoveryPasses)
    }
}
