package com.iblu01.portallauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.HomePageModel
import com.iblu01.portallauncher.domain.home.HomeSectionModel
import com.iblu01.portallauncher.domain.home.HomeSectionType
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.components.HomePillActions
import com.iblu01.portallauncher.ui.components.LauncherPager
import com.iblu01.portallauncher.ui.components.LauncherPagerLayout
import com.iblu01.portallauncher.ui.components.rememberLauncherPagerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h600dp")
class HomeRailPagerGestureTest {
    @get:Rule val rule = createComposeRule()

    private fun pill(index: Int): ResolvedPill {
        val ref = PillRef.Device("switch.device_$index")
        return ResolvedPill(
            ref,
            LauncherChip(
                id = "device_$index",
                icon = "switch",
                label = "Pill $index",
                value = "Prête",
                entityId = "",
                kind = PillKind.SWITCH,
            ),
            sourceEntityIds = setOf(ref.entityId),
        )
    }

    @Test
    fun `gesture started in a Maison rail scrolls it while a header gesture returns to Accueil`() {
        lateinit var pagerState: PagerState
        val layout = LauncherPagerLayout(homePageEnabled = true, appPageCount = 0)
        val model = HomePageModel(
            sections = listOf(
                HomeSectionModel(
                    sectionId = "dense",
                    type = HomeSectionType.KIND,
                    title = "Appareils",
                    items = (1..20).map(::pill),
                ),
            ),
            hasCompatibleDevices = true,
        )
        rule.setContent {
            var railGestureActive by remember { mutableStateOf(false) }
            pagerState = rememberLauncherPagerState(
                homePageEnabled = { true },
                appPageCount = { 0 },
            )
            LauncherPager(
                state = pagerState,
                userScrollEnabled = !railGestureActive,
                pageLayout = layout,
                header = { Box(Modifier.width(120.dp).height(160.dp)) },
                housePage = {
                    HomePage(
                        model = model,
                        pinnedRefs = emptySet(),
                        actions = HomePillActions(),
                        onRailGestureActiveChange = { railGestureActive = it },
                    )
                },
                clockPage = { Box(Modifier.fillMaxSize().testTag("clockPage")) },
                appPage = { _, _ -> Text("unused") },
            )
        }

        rule.onNodeWithTag("clockPage").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(layout.housePage, pagerState.currentPage)

        val rail = rule.onNodeWithTag("homeRail:dense")
        val before = rail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        rail.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val after = rail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("the rail did not consume its horizontal gesture: $before -> $after", after > before)
        assertEquals("rail scrolling must not page away from Maison", layout.housePage, pagerState.currentPage)

        rule.onNodeWithTag("homePage").performTouchInput {
            swipe(
                start = Offset(width * 0.85f, 70.dp.toPx()),
                end = Offset(width * 0.15f, 70.dp.toPx()),
                durationMillis = 300,
            )
        }
        rule.waitForIdle()
        assertEquals("a header gesture belongs to the parent pager", layout.clockPage, pagerState.currentPage)
    }
}
