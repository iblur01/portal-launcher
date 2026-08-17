package com.iblu01.portallauncher.ui

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.ui.components.LauncherPagerLayout
import com.iblu01.portallauncher.ui.components.PageIdentity
import com.iblu01.portallauncher.ui.components.remapPage
import com.iblu01.portallauncher.ui.mapper.toChipAction
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.panel.PanelSource
import com.iblu01.portallauncher.ui.panel.PanelState
import com.iblu01.portallauncher.ui.panel.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureAcceptanceNonRegressionTest {
    private fun chip(
        id: String,
        kind: PillKind,
        entityId: String,
    ) = LauncherChip(id, "icon", id, "state", entityId = entityId, kind = kind)

    @Test
    fun `switch uses a panel while fan retains its routing contract`() {
        assertEquals(
            ChipAction.OpenPanel(PanelKind.SWITCH),
            chip("switch", PillKind.SWITCH, "switch.desk").toChipAction(),
        )
        assertEquals(
            ChipAction.OpenPanel(PanelKind.SWITCH),
            chip("boolean", PillKind.SWITCH, "input_boolean.guest").toChipAction(),
        )
        assertEquals(
            ChipAction.OpenPanel(PanelKind.FAN),
            chip("fan", PillKind.FAN, "fan.office").toChipAction(),
        )

    }

    @Test
    fun `media auto source and alert precedence survive group navigation`() {
        var state = reduce(PanelState(), PanelEvent.MediaAutoOpen("media_player.living"))
        assertEquals(PanelRequest.Media("media_player.living"), state.request)
        assertEquals(PanelSource.AUTO, state.source)

        state = reduce(
            state,
            PanelEvent.OpenGroup(PanelRequest.Group(PillRef.AreaGroup("living-room"))),
        )
        assertEquals(PanelSource.USER, state.source)
        assertEquals(PillRef.AreaGroup("living-room"), (state.request as PanelRequest.Group).destination)

        val alarm = PanelRequest.Chip("alarm.home", PanelKind.ALARM)
        state = reduce(state, PanelEvent.AlarmAlert(alarm))
        assertEquals(alarm, state.request)
        assertEquals(PanelSource.ALERT, state.source)

        state = reduce(state, PanelEvent.MediaAutoOpen("media_player.bedroom"))
        assertEquals(alarm, state.request)
        assertEquals(PanelSource.ALERT, state.source)
    }

    @Test
    fun `Maison hot toggle and all logical return decisions target the main accueil`() {
        val withoutHouse = LauncherPagerLayout(homePageEnabled = false, appPageCount = 3)
        val withHouse = LauncherPagerLayout(homePageEnabled = true, appPageCount = 3)

        PageIdentity.Apps(1).let { identity ->
            val oldPage = withoutHouse.pageOf(identity)!!
            assertEquals(withHouse.pageOf(identity), remapPage(oldPage, withoutHouse, withHouse))
            assertEquals(
                withoutHouse.pageOf(identity),
                remapPage(withHouse.pageOf(identity)!!, withHouse, withoutHouse),
            )
        }
        assertEquals(
            withoutHouse.clockPage,
            remapPage(withHouse.housePage!!, withHouse, withoutHouse),
        )

        fun back(page: PageIdentity) = backAction(
            itemMenuOpen = false,
            hiddenListOpen = false,
            quickActionsOpen = false,
            userPanelOpen = false,
            currentPage = page,
        )
        assertEquals(BackAction.GoToClockPage, back(PageIdentity.House))
        assertEquals(BackAction.GoToClockPage, back(PageIdentity.Apps(2)))
        assertEquals(BackAction.Nothing, back(PageIdentity.Clock))
        assertNull(withoutHouse.housePage)
    }
}
