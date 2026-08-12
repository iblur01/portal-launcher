package com.iblu01.portallauncher.ui

import app.cash.turbine.test
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.model.PillSnapshot
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.PillCatalogSnapshot
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.panel.PanelSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * ViewModel behaviour (design §Tests): uiState mirrors the injected snapshot Flow, the panel
 * reducer is driven via onEvent, callService is delegated, media auto-open fires from the flow,
 * and — the bug this whole refactor fixes — panelChip keeps the last-known-good ChipUi after the
 * chip drops out of the tray. VM is unit-testable now that its deps are injected (chantier #1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    private fun emptySnapshot() = PillSnapshot(
        chips = emptyList(), media = emptyList(), temperatures = TemperatureSummary(),
        connected = true, latestStates = emptyMap(), areaByEntity = emptyMap(),
        lastUpdateAt = 0L, weatherEntityId = null, hourlyForecast = emptyList(), dailyForecast = emptyList(),
    )

    private fun chip(id: String, entityId: String = "light.$id") =
        LauncherChip(id = id, icon = "i", label = id, value = "v", entityId = entityId, kind = PillKind.GENERIC)

    private fun media(id: String) = PlayingMedia(
        entityId = id, title = "T", artist = "A", album = null, state = "playing",
        coverUrl = null, volumePercent = 0, isMuted = false,
    )

    private fun vm(
        source: MutableStateFlow<PillSnapshot>,
        onCall: (String, String, String?, Map<String, Any>?) -> Unit = { _, _, _, _ -> },
        onPreferences: (((HomePillPreferences) -> HomePillPreferences) -> HomePillPreferences)? = null,
    ) = LauncherViewModel(source, onCall, onPreferences)

    private fun preferences(vararg refs: PillRef) = HomePillPreferences(
        schemaVersion = 1,
        homePageEnabled = true,
        pinnedOrder = refs.toList(),
        homeSections = emptyList(),
        manualGroups = emptyList(),
    )

    private fun catalog(vararg entries: Pair<PillRef.Device, Availability>): PillCatalogSnapshot {
        val resolved = entries.filter { it.second.isRenderable }.associate { (ref, availability) ->
            ref to ResolvedPill(ref, chip(ref.entityId.substringAfter('.'), ref.entityId), availability, setOf(ref.entityId))
        }
        return PillCatalogSnapshot(
            devices = resolved.mapValues { it.value.chip },
            groups = emptyMap(),
            availability = entries.toMap(),
            dynamicCandidates = emptyList(),
            resolvedDevices = resolved,
        )
    }

    @Test fun `uiState mirrors snapshot chips`() = runTest {
        val source = MutableStateFlow(emptySnapshot())
        val model = vm(source)
        model.uiState.test {
            assertEquals(emptyList<LauncherChip>(), awaitItem().chips)
            source.value = emptySnapshot().copy(chips = listOf(chip("a")))
            assertEquals(listOf(chip("a")), awaitItem().chips)
        }
    }

    @Test fun `uiState conflates equal consecutive snapshots (m2 dedup)`() = runTest {
        // Two distinct PillSnapshot instances that are value-equal (equal HaEntity via its equals).
        val states = mapOf("light.a" to com.iblu01.portallauncher.HaEntity("light.a", "on", org.json.JSONObject()))
        val snapA = emptySnapshot().copy(chips = listOf(chip("a")), latestStates = states)
        val snapB = emptySnapshot().copy(chips = listOf(chip("a")),
            latestStates = mapOf("light.a" to com.iblu01.portallauncher.HaEntity("light.a", "on", org.json.JSONObject())))
        val source = MutableStateFlow(snapA)
        val model = vm(source)
        model.uiState.test {
            assertEquals(listOf(chip("a")), awaitItem().chips)   // initial (default replaced fast under Unconfined)
            source.value = snapB                                  // value-equal → StateFlow must NOT re-emit
            expectNoEvents()
        }
    }

    @Test fun `onEvent drives the panel reducer`() {
        val model = vm(MutableStateFlow(emptySnapshot()))
        model.onEvent(PanelEvent.OpenChip(PanelRequest.Chip("a", PanelKind.GENERIC_DETAILS)))
        assertEquals(PanelRequest.Chip("a", PanelKind.GENERIC_DETAILS), model.panel.value.request)
        assertEquals(PanelSource.USER, model.panel.value.source)
        // tapping the same key toggles closed
        model.onEvent(PanelEvent.OpenChip(PanelRequest.Chip("a", PanelKind.GENERIC_DETAILS)))
        assertNull(model.panel.value.request)
    }

    @Test fun `callService is delegated to the injected lambda`() {
        val calls = mutableListOf<String>()
        val model = vm(
            MutableStateFlow(emptySnapshot()),
            onCall = { d, s, e, _ -> calls += "$d.$s@$e" },
        )
        model.callService("switch", "toggle", "switch.x")
        assertEquals(listOf("switch.toggle@switch.x"), calls)
    }

    @Test fun `media session auto-opens the panel from the flow`() = runTest {
        val source = MutableStateFlow(emptySnapshot())
        val model = vm(source)
        model.panelChip.test { awaitItem() }          // subscribe to keep uiState hot
        source.value = emptySnapshot().copy(media = listOf(media("media_player.x")))
        assertEquals(PanelRequest.Media("media_player.x"), model.panel.value.request)
        assertEquals(PanelSource.AUTO, model.panel.value.source)
    }

    @Test fun `media does not reopen after the user dismisses while it keeps playing`() = runTest {
        val source = MutableStateFlow(emptySnapshot().copy(media = listOf(media("media_player.x"))))
        val model = vm(source)
        model.panelChip.test { awaitItem() }                       // keep uiState hot
        // auto-opened
        assertEquals(PanelRequest.Media("media_player.x"), model.panel.value.request)
        // user dismisses; the same session keeps playing (no new distinct media id)
        model.onEvent(PanelEvent.Dismiss)
        assertNull(model.panel.value.request)
        source.value = emptySnapshot().copy(media = listOf(media("media_player.x")))
        // distinctUntilChanged suppresses re-emit AND dismissedAutoKey guards it → stays closed
        assertNull(model.panel.value.request)
    }

    @Test fun `media panel reopens once another panel closes while it keeps playing`() = runTest {
        // The regression: auto-open was edge-triggered on the primary id only, so after any close
        // (auto-return, dismissing a chip panel) the media panel never came back mid-session.
        val source = MutableStateFlow(emptySnapshot().copy(media = listOf(media("media_player.x"))))
        val model = vm(source)
        model.panelChip.test { awaitItem() }                       // keep uiState hot
        assertEquals(PanelRequest.Media("media_player.x"), model.panel.value.request)
        // user opens a chip panel over it, then it closes (timeout / toggle) — not a media dismissal
        model.onEvent(PanelEvent.OpenChip(PanelRequest.Chip("lock.b", PanelKind.LOCK)))
        model.onEvent(PanelEvent.Dismiss)
        assertEquals(PanelRequest.Media("media_player.x"), model.panel.value.request)
        assertEquals(PanelSource.AUTO, model.panel.value.source)
    }

    @Test fun `panelChip keeps last-known-good after the chip leaves the tray`() = runTest {
        val source = MutableStateFlow(emptySnapshot().copy(chips = listOf(chip("a"))))
        val model = vm(source)
        model.panelChip.test {
            assertNull(awaitItem())                                   // nothing open yet
            model.onEvent(PanelEvent.OpenChip(PanelRequest.Chip("a", PanelKind.GENERIC_DETAILS)))
            assertEquals("a", awaitItem()?.id)                        // resolved live
            // chip drops out of the tray (left top-9 / went inactive) — the trigger bug
            source.value = emptySnapshot().copy(chips = emptyList())
            expectNoEvents()                                          // panelChip does NOT go null
            assertEquals("a", model.panelChip.value?.id)              // frozen last-known-good
        }
    }

    @Test fun `uiState exposes one atomic catalog composition preferences and stable area frame`() = runTest {
        val ref = PillRef.Device("switch.a")
        val prefs = preferences(ref)
        val source = MutableStateFlow(
            emptySnapshot().copy(
                catalog = catalog(ref to Availability.AVAILABLE),
                homePreferences = prefs,
                areaIdByEntity = mapOf(ref.entityId to "kitchen"),
                areaNameById = mapOf("kitchen" to "Cuisine"),
            ),
        )
        val model = vm(source)
        model.uiState.test {
            val state = awaitItem()
            assertEquals(prefs, state.homePreferences)
            assertEquals(Availability.AVAILABLE, state.catalog.availability[ref])
            assertEquals("kitchen", state.areaIdByEntity[ref.entityId])
            assertEquals("Cuisine", state.areaNameById["kitchen"])
        }
    }

    @Test fun `new pin requires available while stale existing pin can be removed`() = runTest {
        val available = PillRef.Device("switch.available")
        val stale = PillRef.Device("switch.stale")
        val unavailable = PillRef.Device("switch.unavailable")
        var stored = preferences(stale)
        val source = MutableStateFlow(
            emptySnapshot().copy(
                catalog = catalog(
                    available to Availability.AVAILABLE,
                    stale to Availability.STALE,
                    unavailable to Availability.UNAVAILABLE,
                ),
                homePreferences = stored,
            ),
        )
        val model = vm(source, onPreferences = { reducer -> reducer(stored).also { stored = it } })

        assertEquals(true, model.setPinned(stale, pinned = true))
        assertEquals(false, model.setPinned(unavailable, pinned = true))
        assertEquals(true, model.setPinned(available, pinned = true))
        assertEquals(listOf(stale, available), stored.pinnedOrder)
        assertEquals(true, model.setPinned(stale, pinned = false))
        assertEquals(listOf(available), stored.pinnedOrder)
    }

    @Test fun `pin reorder mutates persistent order without touching dynamic slots`() = runTest {
        val a = PillRef.Device("switch.a")
        val b = PillRef.Device("switch.b")
        val c = PillRef.Device("switch.c")
        var stored = preferences(a, b, c)
        val source = MutableStateFlow(emptySnapshot().copy(homePreferences = stored))
        val model = vm(source, onPreferences = { reducer -> reducer(stored).also { stored = it } })

        assertEquals(true, model.movePinned(c, 0))
        assertEquals(listOf(c, a, b), stored.pinnedOrder)
    }

    @Test fun `group device panel resolves from full catalog and remains last known good`() = runTest {
        val device = PillRef.Device("switch.group_member")
        val group = PillRef.AreaGroup("kitchen")
        val source = MutableStateFlow(emptySnapshot().copy(catalog = catalog(device to Availability.AVAILABLE)))
        val model = vm(source)
        model.panelChip.test {
            assertNull(awaitItem())
            model.onEvent(PanelEvent.OpenGroup(PanelRequest.Group(group)))
            model.onEvent(PanelEvent.OpenGroupDevice(PanelRequest.Chip(device.entityId.substringAfter('.'), PanelKind.SWITCH)))
            assertEquals(device.entityId, awaitItem()?.entityId)
            source.value = emptySnapshot()
            expectNoEvents()
            assertEquals(device.entityId, model.panelChip.value?.entityId)
        }
    }
}
