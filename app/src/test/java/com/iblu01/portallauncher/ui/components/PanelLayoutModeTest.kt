package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelLayoutModeTest {
    @Test fun `wide panel uses horizontal composition`() {
        assertEquals(PanelLayoutMode.HORIZONTAL, panelLayoutModeFor(widthDp = 760f, heightDp = 360f))
    }

    @Test fun `tall side panel uses vertical composition`() {
        assertEquals(PanelLayoutMode.VERTICAL, panelLayoutModeFor(widthDp = 360f, heightDp = 760f))
    }

    @Test fun `square panel stays vertical`() {
        assertEquals(PanelLayoutMode.VERTICAL, panelLayoutModeFor(widthDp = 480f, heightDp = 480f))
    }
}
