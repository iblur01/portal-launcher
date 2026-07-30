package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.iblu01.portallauncher.domain.model.HaSnapshot
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
        val source = MutableSharedFlow<HaSnapshot>(replay = 0)
        val out = repo.transformSnapshots(
            source = source,
            rulesProvider = { reads++; emptyList() },
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
            cancelAndIgnoreRemainingEvents()
        }
    }
}
