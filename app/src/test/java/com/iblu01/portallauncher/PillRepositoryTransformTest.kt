package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.iblu01.portallauncher.domain.model.HaSnapshot
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionIds
import com.iblu01.portallauncher.domain.home.PillRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the `snapshotFlow` transform (audit finding M-B — the critical C1/M1/M2 path had no test).
 * Uses the extracted [PillRepository.transformSnapshots] with pure inputs + an injected test
 * dispatcher and `sampleMs = 0` (deterministic; `sample`'s timing is kotlinx's, not ours).
 * Verifies the `scan` carry (previous-primary media sorts first across emissions) and per-emission
 * rule reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PillRepositoryTransformTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = PillRepository(context)

    private fun player(id: String, title: String, state: String = "playing"): HaEntity =
        HaEntity(id, state, JSONObject().put("media_title", title), "2026-07-23T10:00:00Z")

    private fun snap(states: Map<String, HaEntity>) = HaSnapshot(
        states = states, connected = true, areaByEntity = emptyMap(), lastUpdateAt = 1L,
        weatherEntityId = null, hourlyForecast = emptyList(), dailyForecast = emptyList(),
    )

    private fun accessory(id: String, state: String = "off") = HaEntity(
        entityId = "switch.$id",
        state = state,
        attributes = JSONObject().put("friendly_name", id),
        lastChanged = "2026-07-23T10:00:00Z",
    )

    private fun preferences(pins: List<PillRef> = emptyList()) = HomePillPreferences(
        schemaVersion = 1,
        homePageEnabled = true,
        pinnedOrder = pins,
        homeSections = emptyList(),
        manualGroups = emptyList(),
    )

    @Test fun `scan carries previous-primary media across emissions`() = runTest(dispatcher) {
        val a = mapOf("media_player.a" to player("media_player.a", "Song A"))
        val b = mapOf(
            "media_player.a" to player("media_player.a", "Song A"),
            "media_player.b" to player("media_player.b", "Song B"),
        )
        val out = repo.transformSnapshots(
            source = flowOf(snap(a), snap(b)),
            rulesProvider = { emptyList() },   // chip selection covered by PillPriorityEngineTest
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        )
        out.test {
            val first = awaitItem()
            assertEquals(listOf("media_player.a"), first.media.map { it.entityId })

            val second = awaitItem()
            // a was primary last emission → must still sort first now that b appeared (scan carry).
            assertEquals("media_player.a", second.media.first().entityId)
            assertEquals(2, second.media.size)
            assertTrue(second.connected)
            awaitComplete()
        }
    }

    @Test fun `rulesProvider is read on every emission (live rule changes)`() = runTest(dispatcher) {
        var reads = 0
        var preferenceReads = 0
        val source = MutableSharedFlow<HaSnapshot>(replay = 0)
        val out = repo.transformSnapshots(
            source = source,
            rulesProvider = { reads++; emptyList() },
            homePreferencesProvider = { preferenceReads++; preferences() },
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        )
        out.test {
            source.emit(snap(emptyMap()))
            awaitItem()
            source.emit(snap(emptyMap()))
            awaitItem()
            assertEquals(2, reads)   // once per emission, not cached
            assertEquals(2, preferenceReads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `catalog is complete while visual composition owns the nine item limit`() = runTest(dispatcher) {
        val entities = (1..12).map { accessory("device_$it") }
        val states = entities.associateBy { it.entityId }
        val rules = entities.map { PillRule(it.entityId, PillKind.SWITCH, it.name) }
        val pins = entities.map { PillRef.Device(it.entityId) }

        repo.transformSnapshots(
            source = flowOf(snap(states)),
            rulesProvider = { rules },
            homePreferencesProvider = { preferences(pins) },
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        ).test {
            val frame = awaitItem()
            assertEquals(12, frame.catalog.devices.size)
            assertEquals(3, frame.homeComposition.primary.size)
            assertEquals(6, frame.homeComposition.secondary.size)
            assertEquals(3, frame.homeComposition.favoriteOverflow.size)
            assertEquals(9, frame.chips.size)
            awaitComplete()
        }
    }

    @Test fun `compatible unpinned device fills an empty tray without entering dynamic ranking`() = runTest(dispatcher) {
        val entity = accessory("new_device", state = "off")
        repo.transformSnapshots(
            source = flowOf(snap(mapOf(entity.entityId to entity))),
            rulesProvider = { emptyList() },
            homePreferencesProvider = { preferences() },
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        ).test {
            val frame = awaitItem()
            assertTrue(PillRef.Device(entity.entityId) in frame.catalog.devices)
            assertTrue(frame.catalog.dynamicCandidates.isEmpty())
            assertEquals(listOf(entity.entityId), frame.chips.map { it.entityId })
            assertTrue(frame.homePage.sections.any { it.sectionId == HomeSectionIds.kind(PillKind.SWITCH) })
            awaitComplete()
        }
    }

    @Test fun `global disconnect marks last snapshot stale without treating devices as unavailable`() = runTest(dispatcher) {
        val entity = accessory("stale")
        val ref = PillRef.Device(entity.entityId)
        val disconnected = snap(mapOf(entity.entityId to entity)).copy(connected = false)
        repo.transformSnapshots(
            source = flowOf(disconnected),
            rulesProvider = { listOf(PillRule(entity.entityId, PillKind.SWITCH, entity.name)) },
            homePreferencesProvider = { preferences(listOf(ref)) },
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        ).test {
            val frame = awaitItem()
            assertEquals(Availability.STALE, frame.catalog.availability[ref])
            assertEquals(ref, frame.homeComposition.primary.single().ref)
            assertEquals(listOf(ref), frame.homePreferences.pinnedOrder)
            awaitComplete()
        }
    }

    @Test fun `stable area ids and display names reach automatic groups atomically`() = runTest(dispatcher) {
        val entity = accessory("kitchen")
        val frame = snap(mapOf(entity.entityId to entity)).copy(
            areaByEntity = mapOf(entity.entityId to "Cuisine"),
            areaIdByEntity = mapOf(entity.entityId to "kitchen_id"),
            areaNameById = mapOf("kitchen_id" to "Cuisine"),
        )
        repo.transformSnapshots(
            source = flowOf(frame),
            rulesProvider = { emptyList() },
            homePreferencesProvider = { preferences() },
            haUrl = "http://ha.local",
            dispatcher = dispatcher,
            sampleMs = 0,
        ).test {
            val output = awaitItem()
            val ref = PillRef.AreaGroup("kitchen_id")
            assertEquals("Cuisine", output.catalog.groups.getValue(ref).chip.label)
            assertEquals(mapOf(entity.entityId to "kitchen_id"), output.areaIdByEntity)
            assertEquals(mapOf("kitchen_id" to "Cuisine"), output.areaNameById)
            awaitComplete()
        }
    }
}
