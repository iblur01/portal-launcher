package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.ui.components.PanelContent
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPanelRoutingTest {
    private val media = PlayingMedia(
        entityId = "media_player.living",
        title = "Titre",
        artist = "Salon",
        album = null,
        state = "playing",
        coverUrl = null,
        volumePercent = 35,
        isMuted = false,
    )

    @Test fun `individual media pill opens its existing player instead of an empty generic panel`() {
        val content = resolveChipPanelContent(
            request = PanelRequest.Chip(media.entityId, PanelKind.MEDIA),
            panelChip = null,
            mediaDevices = listOf(media),
        )

        assertTrue(content is PanelContent.Media)
        assertSame(media, (content as PanelContent.Media).session)
    }

    @Test fun `media group still opens the browser while light device keeps its action panel`() {
        assertEquals(
            PanelContent.MediaBrowser,
            resolveChipPanelContent(
                request = PanelRequest.Chip("media_group", PanelKind.MEDIA),
                panelChip = null,
                mediaDevices = listOf(media),
            ),
        )
        val light = LauncherChip(
            id = "light.kitchen",
            icon = "light",
            label = "Cuisine",
            value = "Allumée",
            entityId = "light.kitchen",
            kind = PillKind.LIGHTS,
        )
        val lightContent = resolveChipPanelContent(
            request = PanelRequest.Chip(light.id, PanelKind.LIGHTS),
            panelChip = light,
            mediaDevices = emptyList(),
        )
        assertEquals(PanelContent.ChipActions(light), lightContent)
    }
}
