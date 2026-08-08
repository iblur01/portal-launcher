package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionPreference
import com.iblu01.portallauncher.ui.components.HomePillMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeSectionPreferenceReducerTest {
    private val preferences = HomePillPreferences(
        schemaVersion = 1,
        homePageEnabled = true,
        pinnedOrder = emptyList(),
        homeSections = emptyList(),
        manualGroups = emptyList(),
    )

    @Test
    fun `section move materializes stable order without changing identities`() {
        val moved = moveVisibleHomeSection(
            preferences,
            visibleSectionIds = listOf("favorites", "areas", "kind:LIGHTS"),
            sectionId = "kind:LIGHTS",
            move = HomePillMove.FIRST,
        )

        assertEquals(
            listOf("kind:LIGHTS", "favorites", "areas"),
            moved.homeSections.sortedBy(HomeSectionPreference::order).map { it.sectionId },
        )
    }

    @Test
    fun `hiding a section preserves a persisted preference for later restoration`() {
        val hidden = setHomeSectionVisible(
            preferences,
            visibleSectionIds = listOf("favorites", "areas"),
            sectionId = "areas",
            visible = false,
        )

        assertFalse(hidden.homeSections.single { it.sectionId == "areas" }.visible)
    }
}
