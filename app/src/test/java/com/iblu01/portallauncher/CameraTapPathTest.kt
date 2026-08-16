package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.domain.home.PillCatalogBuilder
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.ui.mapper.toChipAction
import com.iblu01.portallauncher.ui.model.ChipAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tapping a camera pill must reach the camera centre, with that camera's own id. */
@RunWith(RobolectricTestRunner::class)
class CameraTapPathTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entity(id: String, state: String, attrs: JSONObject = JSONObject()) =
        HaEntity(id, state, attrs)

    private val states = listOf(
        entity("camera.hall", "idle"),
        entity("sensor.hall_signal", "-60", JSONObject().put("device_class", "signal_strength")),
    ).associateBy { it.entityId }

    @Test fun `the pill built for a camera carries the camera kind and its entity id`() {
        val catalog = PillCatalogBuilder(PillPriorityEngine(context))
            .build(rules = emptyList(), states = states)

        val chip = catalog.resolvedDevices.getValue(PillRef.Device("camera.hall")).chip

        assertEquals(PillKind.CAMERA, chip.kind)
        assertEquals("camera.hall", chip.entityId)
    }

    @Test fun `tapping it opens the camera centre on that camera, not a panel`() {
        val catalog = PillCatalogBuilder(PillPriorityEngine(context))
            .build(rules = emptyList(), states = states)
        val chip = catalog.resolvedDevices.getValue(PillRef.Device("camera.hall")).chip

        val action = chip.toChipAction()

        assertTrue("expected OpenCameraCenter, got $action", action is ChipAction.OpenCameraCenter)
        assertEquals("camera.hall", (action as ChipAction.OpenCameraCenter).entityId)
    }

    @Test fun `the general Cameras pill opens the centre with no specific camera`() {
        val catalog = PillCatalogBuilder(PillPriorityEngine(context))
            .build(rules = emptyList(), states = states)
        val chip = catalog.specials.values.single().chip

        val action = chip.toChipAction()

        assertTrue("expected OpenCameraCenter, got $action", action is ChipAction.OpenCameraCenter)
        assertEquals(null, (action as ChipAction.OpenCameraCenter).entityId)
    }

    /**
     * The long-press router keys on the pill kind, not on the action type: a fan is a
     * ServiceToggle too, and long-pressing it must keep opening its control panel rather than
     * toggling it. This mirrors the branch in LauncherActivity.onOpenResolvedCommands.
     */
    @Test fun `only cameras and scenes have no commands to long-press`() {
        val withoutCommands = setOf(PillKind.CAMERA, PillKind.SCENE)

        PillKind.values().forEach { kind ->
            val chip = LauncherChip(
                id = "x", icon = "i", label = "l", value = "v",
                entityId = "domain.x", kind = kind,
            )
            val handledAsTap = chip.kind in withoutCommands
            if (kind == PillKind.FAN) {
                assertTrue("a fan must keep its long-press panel", !handledAsTap)
                assertTrue(chip.toChipAction() is ChipAction.ServiceToggle)
            }
            if (kind == PillKind.CAMERA) {
                assertTrue(handledAsTap)
                assertTrue(chip.toChipAction() is ChipAction.OpenCameraCenter)
            }
            if (kind == PillKind.SCENE) {
                assertTrue(handledAsTap)
                assertTrue(chip.toChipAction() is ChipAction.ActivateScene)
            }
        }
    }
}
