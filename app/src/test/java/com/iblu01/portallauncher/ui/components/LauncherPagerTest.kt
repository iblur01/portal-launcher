package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour of the two-page launcher surface (headless Compose, no emulator).
 *
 * The three things that can silently break: the swipe itself, the swipe *starting on the pinned
 * clock header* (which is drawn above the pager and would otherwise be a dead zone), and the lock
 * that keeps the grid away while a side panel owns a third of the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherPagerTest {

    @get:Rule val rule = createComposeRule()

    private val HEADER_HEIGHT = 200.dp

    private fun harness(
        userScrollEnabled: Boolean = true,
        onHeaderTap: () -> Unit = {},
        onAppsTap: () -> Unit = {},
        appPageCount: Int = 1,
    ): () -> PagerState {
        lateinit var state: PagerState
        rule.setContent {
            state = rememberLauncherPagerState { appPageCount }
            LauncherPager(
                state = state,
                userScrollEnabled = userScrollEnabled,
                onHeaderTap = onHeaderTap,
                header = {
                    // Wraps its width, like the real clock — so this also pins down the centring.
                    Box(Modifier.width(120.dp).height(HEADER_HEIGHT).testTag("header"))
                },
                clockPage = { Box(Modifier.fillMaxSize().testTag("clockPage")) },
                appPage = { page, _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(if (page == 0) "appsPage" else "appsPage$page")
                            .pointerInput(Unit) { detectTapGestures { onAppsTap() } }
                    ) { Text("APPS $page") }
                },
            )
        }
        rule.waitForIdle()
        return { state }
    }

    @Test
    fun `swiping right to left opens the apps page, and back`() {
        val state = harness()

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals("swipe left must land on the apps page", PAGE_FIRST_APP, state().currentPage)
        assertEquals("header fully collapsed on the apps page", 1f, state().collapseFraction(), 0.01f)

        rule.onNodeWithTag("appsPage").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals("swipe right must return to the clock", PAGE_CLOCK, state().currentPage)
        assertEquals("header fully expanded on the clock page", 0f, state().collapseFraction(), 0.01f)
    }

    @Test
    fun `app pages are pages of the same flat pager, with the clock staying collapsed`() {
        val state = harness(appPageCount = 2)

        assertEquals("clock + two app pages", 3, state().pageCount)

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("appsPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(PAGE_FIRST_APP + 1, state().currentPage)
        // The header must not grow back on later pages: collapse saturates at 1.
        assertEquals(1f, state().collapseFraction(), 0.01f)
        assertEquals("pager index 2 is app page 1", 1, appPageOf(state().currentPage))
    }

    @Test
    fun `compact clock removes secondary details while expanded clock keeps them`() {
        assertEquals(1f, clockDetailAlpha(0f), 0.001f)
        assertEquals(0f, clockDetailAlpha(0.5f), 0.001f)
        assertEquals(0f, clockDetailAlpha(1f), 0.001f)
    }

    @Test
    fun `an auto-return lands squarely on the clock even though it clears its own trigger`() {
        lateinit var state: PagerState
        var returnRequested by mutableStateOf(false)
        rule.setContent {
            state = rememberLauncherPagerState { 1 }
            val scope = rememberCoroutineScope()
            LauncherPager(
                state = state,
                userScrollEnabled = true,
                header = { Box(Modifier.width(120.dp).height(HEADER_HEIGHT).testTag("header")) },
                clockPage = { Box(Modifier.fillMaxSize().testTag("clockPage")) },
                appPage = { _, _ -> Box(Modifier.fillMaxSize().testTag("appsPage")) },
            )
            LaunchedEffect(returnRequested) {
                if (returnRequested) returnToClockPage(scope, state)
            }
            // Mirrors the launcher: leaving the apps page disarms auto-return, which clears the very
            // flag the returning effect is keyed on.
            LaunchedEffect(state.currentPage) {
                if (state.currentPage == PAGE_CLOCK) returnRequested = false
            }
        }

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(PAGE_FIRST_APP, state.currentPage)

        rule.runOnIdle { returnRequested = true }
        rule.waitForIdle()

        assertEquals(PAGE_CLOCK, state.currentPage)
        // A cancelled scroll strands the pager between pages, and the app page's fade tracks that
        // offset — which is exactly the "a few icons still faintly visible" symptom.
        assertEquals("the pager must settle exactly", 0f, state.currentPageOffsetFraction, 0.001f)
    }

    @Test
    fun `dragging the pinned header swipes the pager`() {
        val state = harness()

        rule.onNodeWithTag("header").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(
            "the clock area must not be a dead zone for the page swipe",
            PAGE_FIRST_APP,
            state().currentPage,
        )
    }

    @Test
    fun `a locked pager cannot leave the clock page`() {
        val state = harness(userScrollEnabled = false)

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("header").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(
            "with a side panel open the grid stays unreachable",
            PAGE_CLOCK,
            state().currentPage,
        )
    }

    @Test
    fun `the clock stays horizontally centred inside the full-width header band`() {
        harness()

        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val header = rule.onNodeWithTag("header").getUnclippedBoundsInRoot()

        val rootCentre = (root.left + root.right) / 2
        val headerCentre = (header.left + header.right) / 2
        // The header wrapper fills the width to catch drags; without an explicit centre alignment
        // the clock silently falls back to the left edge.
        assertEquals(rootCentre.value, headerCentre.value, 1f)
    }

    @Test
    fun `the collapsed header stops eating taps meant for the app grid`() {
        var appsTaps = 0
        val state = harness(onAppsTap = { appsTaps++ })

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(PAGE_FIRST_APP, state().currentPage)

        // Well below the collapsed clock (34 % of HEADER_HEIGHT) but inside the band it occupied on
        // the clock page. A pointer modifier placed inside collapsingHeight keeps the full-size hit
        // area and swallows this tap, so the first rows of icons become unlaunchable.
        rule.onNodeWithTag("appsPage").performTouchInput {
            click(Offset(width / 2f, (HEADER_HEIGHT * 0.6f).toPx()))
        }
        rule.waitForIdle()

        assertEquals("a tap under the collapsed clock must reach the grid", 1, appsTaps)
    }

    @Test
    fun `tapping the clock opens Home Assistant only while it is expanded`() {
        var taps = 0
        val state = harness(onHeaderTap = { taps++ })

        rule.onNodeWithTag("header").performClick()
        rule.waitForIdle()
        assertEquals("tap on the expanded clock keeps its historical meaning", 1, taps)

        rule.onNodeWithTag("clockPage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(PAGE_FIRST_APP, state().currentPage)

        rule.onNodeWithTag("header").performClick()
        rule.waitForIdle()
        assertEquals("the collapsed clock must not launch HA from the app grid", 1, taps)
    }

    @Test
    fun `logical pages map to explicit physical indices with and without Maison`() {
        val withoutHouse = LauncherPagerLayout(homePageEnabled = false, appPageCount = 2)
        assertNull(withoutHouse.pageOf(PageIdentity.House))
        assertEquals(0, withoutHouse.pageOf(PageIdentity.Clock))
        assertEquals(1, withoutHouse.pageOf(PageIdentity.Apps(0)))
        assertEquals(2, withoutHouse.pageOf(PageIdentity.Apps(1)))
        assertEquals(3, withoutHouse.pageCount)

        val withHouse = LauncherPagerLayout(homePageEnabled = true, appPageCount = 2)
        assertEquals(0, withHouse.pageOf(PageIdentity.House))
        assertEquals(1, withHouse.pageOf(PageIdentity.Clock))
        assertEquals(2, withHouse.pageOf(PageIdentity.Apps(0)))
        assertEquals(3, withHouse.pageOf(PageIdentity.Apps(1)))
        assertEquals(4, withHouse.pageCount)
        assertEquals(PageIdentity.Apps(1), withHouse.identityOf(3))
        assertNull(withHouse.identityOf(4))
        assertEquals(1, appPageOf(3, withHouse))
        assertNull(appPageOf(withHouse.clockPage, withHouse))
    }

    @Test
    fun `hot Maison toggle remaps by identity and never shifts an app page`() {
        val withoutHouse = LauncherPagerLayout(homePageEnabled = false, appPageCount = 3)
        val withHouse = LauncherPagerLayout(homePageEnabled = true, appPageCount = 3)

        assertEquals(
            withHouse.pageOf(PageIdentity.Apps(1)),
            remapPage(withoutHouse.pageOf(PageIdentity.Apps(1))!!, withoutHouse, withHouse),
        )
        assertEquals(
            withoutHouse.pageOf(PageIdentity.Apps(1)),
            remapPage(withHouse.pageOf(PageIdentity.Apps(1))!!, withHouse, withoutHouse),
        )
        assertEquals(
            "disabling Maison while it is visible falls back to the main accueil",
            withoutHouse.clockPage,
            remapPage(withHouse.housePage!!, withHouse, withoutHouse),
        )
    }

    @Test
    fun `a removed app page falls back safely instead of producing an invalid index`() {
        val previous = LauncherPagerLayout(homePageEnabled = true, appPageCount = 3)
        val next = LauncherPagerLayout(homePageEnabled = false, appPageCount = 1)

        assertEquals(
            next.firstAppPage,
            remapPage(previous.pageOf(PageIdentity.Apps(2))!!, previous, next),
        )
        assertEquals(
            LauncherPagerLayout(false, 0).clockPage,
            remapPage(
                previous.pageOf(PageIdentity.Apps(2))!!,
                previous,
                LauncherPagerLayout(false, 0),
            ),
        )
    }

    @Test
    fun `Maison is left of Accueil and shares its collapsed launcher header`() {
        lateinit var state: PagerState
        val layout = LauncherPagerLayout(homePageEnabled = true, appPageCount = 1)
        rule.setContent {
            state = rememberLauncherPagerState(
                homePageEnabled = { true },
                appPageCount = { 1 },
            )
            LauncherPager(
                state = state,
                userScrollEnabled = true,
                pageLayout = layout,
                header = { Box(Modifier.width(120.dp).height(HEADER_HEIGHT).testTag("header")) },
                headerActions = {
                    Box(Modifier.width(40.dp).height(40.dp).testTag("appHeaderActions"))
                },
                houseHeader = {
                    Box(Modifier.width(80.dp).height(40.dp).testTag("houseHeader"))
                },
                houseHeaderActions = {
                    Box(Modifier.width(40.dp).height(40.dp).testTag("houseHeaderActions"))
                },
                housePage = { Box(Modifier.fillMaxSize().testTag("housePage")) },
                clockPage = { Box(Modifier.fillMaxSize().testTag("clockPage")) },
                appPage = { _, _ -> Box(Modifier.fillMaxSize().testTag("appsPage")) },
            )
        }
        rule.waitForIdle()

        assertEquals("the logical accueil remains the initial page", layout.clockPage, state.currentPage)
        rule.onNodeWithTag("appHeaderActions").assertDoesNotExist()
        rule.onNodeWithTag("houseHeader").assertDoesNotExist()
        rule.onNodeWithTag("houseHeaderActions").assertDoesNotExist()
        rule.onNodeWithTag("clockPage").performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(layout.housePage, state.currentPage)
        rule.onNodeWithTag("header").assertIsDisplayed()
        rule.onNodeWithTag("houseHeader").assertIsDisplayed()
        rule.onNodeWithTag("houseHeaderActions").assertIsDisplayed()
        rule.onNodeWithTag("appHeaderActions").assertDoesNotExist()
        assertEquals(
            "Maison drives the same fully collapsed clock state as the app grid",
            1f,
            state.collapseFraction(layout),
            0.01f,
        )
        rule.onNodeWithTag("housePage").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(layout.clockPage, state.currentPage)
    }
}
