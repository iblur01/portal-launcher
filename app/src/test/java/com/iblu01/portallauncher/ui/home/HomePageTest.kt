package com.iblu01.portallauncher.ui.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.AlertSeverity
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.HomePageModel
import com.iblu01.portallauncher.domain.home.HomeSectionModel
import com.iblu01.portallauncher.domain.home.HomeSectionType
import com.iblu01.portallauncher.domain.home.PillAlert
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.components.ClockHeaderCollapsedHeight
import com.iblu01.portallauncher.ui.components.HomePillActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h600dp")
class HomePageTest {
    @get:Rule val rule = createComposeRule()

    private fun pill(
        id: String,
        availability: Availability = Availability.AVAILABLE,
        alert: PillAlert? = null,
    ): ResolvedPill {
        val ref = PillRef.Device("light.$id")
        return ResolvedPill(
            ref = ref,
            chip = LauncherChip(
                id = id,
                icon = "light",
                label = id.replaceFirstChar(Char::uppercase),
                value = "Allumée",
                state = if (alert == null) "active" else "critical",
                // No CompositionLocal HA repository is needed for this rendering test.
                entityId = "",
                kind = PillKind.LIGHTS,
            ),
            availability = availability,
            sourceEntityIds = setOf(ref.entityId),
            alert = alert,
        )
    }

    @Test
    fun `sections render in model order below the fixed header`() {
        val salon = pill("salon")
        val cuisine = pill("cuisine")
        val model = HomePageModel(
            sections = listOf(
                HomeSectionModel("favorites", HomeSectionType.FAVORITES, "Favoris", listOf(salon)),
                HomeSectionModel("lights", HomeSectionType.KIND, "Lumières", listOf(cuisine)),
            ),
            hasCompatibleDevices = true,
        )

        rule.setContent {
            HomePage(
                model = model,
                pinnedRefs = setOf(salon.ref),
                actions = HomePillActions(),
            )
        }

        rule.onNodeWithTag("homeSection:favorites").assertExists()
        rule.onNodeWithTag("homeSection:lights").assertExists()
        rule.onNodeWithTag("homeGrid:favorites").assertExists()
        rule.onNodeWithTag("homeGrid:lights").assertExists()
        rule.onNodeWithContentDescription(
            "Salon, Allumée, Appareil, section Favoris, épinglé",
        ).assertExists()
        assertTrue(
            "the complete Maison scroll surface must start below the fixed header",
            rule.onNodeWithTag("homeScrollableContent").getUnclippedBoundsInRoot().top >=
                ClockHeaderCollapsedHeight,
        )
    }

    @Test
    fun `empty and stale states explain the next safe action`() {
        rule.setContent {
            HomePage(
                model = HomePageModel(emptyList(), hasCompatibleDevices = false),
                pinnedRefs = emptySet(),
                actions = HomePillActions(),
                stale = true,
            )
        }

        rule.onNodeWithTag("homeStaleState").assertIsDisplayed()
        rule.onNodeWithTag("homeEmptyState").assertIsDisplayed()
        rule.onNodeWithText("Aucun appareil compatible").assertIsDisplayed()
        rule.onNodeWithText("Ouvrir les réglages Home Assistant").assertIsDisplayed()
    }

    @Test
    fun `long click opens full pill menu without opening the device`() {
        val target = pill("salon")
        var opened = 0
        rule.setContent {
            HomePage(
                model = HomePageModel(
                    listOf(HomeSectionModel("lights", HomeSectionType.KIND, "Lumières", listOf(target))),
                    hasCompatibleDevices = true,
                ),
                pinnedRefs = emptySet(),
                actions = HomePillActions(onOpen = { opened++ }),
            )
        }

        rule.onNodeWithTag("homePill:${target.ref.stableKey}")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitForIdle()

        rule.onNodeWithText("Épingler").assertIsDisplayed()
        rule.onNodeWithText("Ajouter à un groupe manuel").assertIsDisplayed()
        rule.onNodeWithText("Réorganiser").assertIsDisplayed()
        rule.onNodeWithText("Ouvrir les commandes").assertIsDisplayed()
        assertEquals(0, opened)
    }

    @Test
    fun `accessibility label announces critical and stale state without relying on color`() {
        val target = pill(
            id = "alarme",
            availability = Availability.STALE,
            alert = PillAlert(AlertSeverity.CRITICAL, incidentEntityIds = setOf("light.alarme")),
        )
        val label = homePillAccessibilityLabel(target, pinned = true, sectionTitle = "Favoris")

        assertTrue(label.contains("alerte critique"))
        assertTrue(label.contains("épinglé"))
        assertTrue(label.contains("données figées"))
        assertTrue(label.contains("section Favoris"))
    }

    @Test
    fun `every section wraps its pills on the same width driven column count`() {
        val short = listOf(pill("one"), pill("two"))
        val dense = (1..10).map { pill("dense$it") }
        rule.setContent {
            HomePage(
                model = HomePageModel(
                    sections = listOf(
                        HomeSectionModel("short", HomeSectionType.KIND, "Courte", short),
                        HomeSectionModel("dense", HomeSectionType.KIND, "Dense", dense),
                    ),
                    hasCompatibleDevices = true,
                ),
                pinnedRefs = emptySet(),
                actions = HomePillActions(),
            )
        }

        // w800dp minus the 24dp gutters fits three 220dp pills, whatever a section holds.
        rule.onNodeWithTag("homeGridColumns:short:3").assertExists()
        rule.onNodeWithTag("homeGridColumns:dense:3").assertExists()
        rule.onNodeWithTag("homePill:${dense.last().ref.stableKey}").assertExists()
    }

    @Test
    fun `armed pill exposes a real drag surface and commits only on drop`() {
        val target = pill("salon")
        val drops = mutableListOf<Int>()
        var active by mutableStateOf(false)
        var finished = 0
        rule.setContent {
            HomePage(
                model = HomePageModel(
                    listOf(HomeSectionModel("favorites", HomeSectionType.FAVORITES, "Favoris", listOf(target))),
                    hasCompatibleDevices = true,
                ),
                pinnedRefs = setOf(target.ref),
                reordering = active,
                actions = HomePillActions(
                    isDragReordering = { it.ref == target.ref },
                    onDragActiveChange = { active = it },
                    onDragDrop = { _, offset -> drops += offset },
                    onDragFinished = { finished++ },
                ),
            )
        }

        rule.onNodeWithTag("homePillReorder:${target.ref.stableKey}")
            .performTouchInput { swipeRight(durationMillis = 500) }
        rule.waitForIdle()

        assertTrue(drops.single() > 0)
        assertEquals(1, finished)
        assertEquals(false, active)
    }
}
