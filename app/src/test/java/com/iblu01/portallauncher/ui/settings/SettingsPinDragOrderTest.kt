package com.iblu01.portallauncher.ui.settings

import com.iblu01.portallauncher.HomePillPreferencesCodec
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.ui.screens.SettingsPinDragOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPinDragOrderTest {
    private val a = PillRef.Device("light.a")
    private val b = PillRef.Device("light.b")
    private val c = PillRef.Device("light.c")

    @Test fun `drag stages every visual move and commits only at drop`() {
        val started = requireNotNull(SettingsPinDragOrder.start(listOf(a, b, c), a))

        val staged = SettingsPinDragOrder.dragBy(started, deltaPx = 70f, rowHeightPx = 64f)

        assertEquals(listOf(b, a, c), staged.stagedOrder)
        assertEquals(listOf(a, b, c), started.initialOrder)
        assertEquals(listOf(b, a, c), SettingsPinDragOrder.drop(staged))
    }

    @Test fun `cancel always restores original persisted ordering`() {
        val started = requireNotNull(SettingsPinDragOrder.start(listOf(a, b, c), c))
        val staged = SettingsPinDragOrder.dragBy(started, deltaPx = -140f, rowHeightPx = 64f)

        assertEquals(listOf(c, a, b), staged.stagedOrder)
        assertEquals(listOf(a, b, c), SettingsPinDragOrder.cancel(staged))
    }

    @Test fun `drag order is reducible in one persistence action`() {
        val started = requireNotNull(SettingsPinDragOrder.start(listOf(a, b, c), b))
        val staged = SettingsPinDragOrder.dragBy(started, deltaPx = 80f, rowHeightPx = 64f)

        val persisted = HomeSettingsReducer.reduce(
            HomePillPreferencesCodec.defaults().copy(pinnedOrder = listOf(a, b, c)),
            HomeSettingsAction.SetPinnedOrder(SettingsPinDragOrder.drop(staged)),
        )

        assertEquals(listOf(a, c, b), persisted.pinnedOrder)
    }

    @Test fun `cannot start a session for a pin that is no longer in the order`() {
        assertNull(SettingsPinDragOrder.start(listOf(a, b), c))
    }
}
