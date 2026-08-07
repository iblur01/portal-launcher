package com.iblu01.portallauncher.ui.mapper

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.ChipPlacement
import com.iblu01.portallauncher.ui.model.PanelKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the chip → PanelKind / ChipAction / ChipPlacement routing rules (design §4/§7).
 * These are the rules that replaced the scattered `chip.id == "…"` / `chip.kind` branching.
 */
class ChipMapperTest {

    private fun chip(
        id: String = "x",
        kind: PillKind = PillKind.GENERIC,
        entityId: String = "domain.x",
    ) = LauncherChip(id = id, icon = "i", label = "l", value = "v", entityId = entityId, kind = kind)

    // --- toPanelKind: string-id groups ---------------------------------------------------------

    @Test fun `group ids map to their PanelKind`() {
        assertEquals(PanelKind.MEDIA, chip(id = "media_group").toPanelKind())
        assertEquals(PanelKind.LIGHTS, chip(id = "lights_group").toPanelKind())
        assertEquals(PanelKind.PURIFIER, chip(id = "purifier_group").toPanelKind())
        assertEquals(PanelKind.SCENES, chip(id = "scenes_group").toPanelKind())
        assertEquals(PanelKind.PRESENCE, chip(id = "presence_group").toPanelKind())
        assertEquals(PanelKind.ENERGY, chip(id = "energy_group").toPanelKind())
        assertEquals(PanelKind.AIR_QUALITY, chip(id = "air_group").toPanelKind())
    }

    // --- toPanelKind: PillKind ------------------------------------------------------------------

    @Test fun `pill kinds map to their PanelKind`() {
        assertEquals(PanelKind.LOCK, chip(kind = PillKind.LOCK).toPanelKind())
        assertEquals(PanelKind.COVER, chip(kind = PillKind.COVER).toPanelKind())
        assertEquals(PanelKind.THERMOSTAT, chip(kind = PillKind.THERMOSTAT).toPanelKind())
        assertEquals(PanelKind.VACUUM, chip(kind = PillKind.VACUUM).toPanelKind())
        assertEquals(PanelKind.FAN, chip(kind = PillKind.FAN).toPanelKind())
        assertEquals(PanelKind.SWITCH, chip(kind = PillKind.SWITCH).toPanelKind())
        assertEquals(PanelKind.WASHER, chip(kind = PillKind.APPLIANCE).toPanelKind())
    }

    // --- alarm-vs-generic-safety split (was SidePanel.kt:179) ----------------------------------

    @Test fun `safety on an alarm_control_panel entity is ALARM`() {
        assertEquals(
            PanelKind.ALARM,
            chip(kind = PillKind.SAFETY, entityId = "alarm_control_panel.home").toPanelKind(),
        )
    }

    @Test fun `safety on a non-alarm entity falls back to generic details`() {
        assertEquals(
            PanelKind.GENERIC_DETAILS,
            chip(kind = PillKind.SAFETY, entityId = "binary_sensor.smoke").toPanelKind(),
        )
    }

    @Test fun `unknown kind and id falls back to generic details`() {
        assertEquals(PanelKind.GENERIC_DETAILS, chip(id = "weird", kind = PillKind.GENERIC).toPanelKind())
    }

    @Test fun `string-id group wins over pill kind`() {
        // A media_group chip should route to MEDIA regardless of its kind.
        assertEquals(PanelKind.MEDIA, chip(id = "media_group", kind = PillKind.SWITCH).toPanelKind())
    }

    // --- toChipAction ---------------------------------------------------------------------------

    @Test fun `switch and fan tap toggle their service, not open a panel`() {
        assertEquals(ChipAction.ServiceToggle("switch", "toggle"), chip(kind = PillKind.SWITCH).toChipAction())
        assertEquals(ChipAction.ServiceToggle("fan", "toggle"), chip(kind = PillKind.FAN).toChipAction())
    }

    @Test fun `other chips open their panel on tap`() {
        assertEquals(ChipAction.OpenPanel(PanelKind.LOCK), chip(kind = PillKind.LOCK).toChipAction())
        assertEquals(ChipAction.OpenPanel(PanelKind.MEDIA), chip(id = "media_group").toChipAction())
        assertEquals(ChipAction.OpenPanel(PanelKind.LIGHTS), chip(id = "lights_group").toChipAction())
    }

    // --- chipPlacement --------------------------------------------------------------------------

    @Test fun `presence floats, everything else is tray`() {
        assertEquals(ChipPlacement.FLOATING, chip(id = "presence_group").chipPlacement())
        assertEquals(ChipPlacement.TRAY, chip(id = "lights_group").chipPlacement())
        assertEquals(ChipPlacement.TRAY, chip(kind = PillKind.LOCK).chipPlacement())
    }
}
