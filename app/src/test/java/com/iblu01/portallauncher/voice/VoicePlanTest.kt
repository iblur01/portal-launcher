package com.iblu01.portallauncher.voice

import com.iblu01.portallauncher.ui.model.PanelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlanTest {

    @Test
    fun `a new plan starts on its first step`() {
        val plan = voicePlanOf(listOf("Aspirer la cuisine", "Baisser les volets"))
        assertEquals(0, plan.runningIndex)
        assertEquals(VoiceTaskStatus.PENDING, plan.tasks[1].status)
        assertFalse(plan.isFinished)
    }

    @Test
    fun `blank titles are dropped and the plan is capped`() {
        assertTrue(voicePlanOf(listOf(" ", "")).isEmpty)
        assertEquals(
            MAX_VOICE_TASKS,
            voicePlanOf((1..20).map { "étape $it" }).tasks.size,
        )
        assertEquals("étape", voicePlanOf(listOf("  étape  ")).tasks.single().title)
    }

    @Test
    fun `completing a step advances to the next one`() {
        val plan = voicePlanOf(listOf("a", "b", "c")).completeCurrent()
        assertEquals(VoiceTaskStatus.DONE, plan.tasks[0].status)
        assertEquals(1, plan.runningIndex)
    }

    @Test
    fun `a failed step is kept as failed and does not stop the plan`() {
        val plan = voicePlanOf(listOf("a", "b")).completeCurrent(failed = true)
        assertEquals(VoiceTaskStatus.FAILED, plan.tasks[0].status)
        assertEquals(1, plan.runningIndex)
    }

    /**
     * The model repeats and renumbers its own calls; a checklist that walks past the end of the
     * list on the third duplicate is worse than one that stops.
     */
    @Test
    fun `completing past the end is a no op`() {
        var plan = voicePlanOf(listOf("a", "b"))
        repeat(5) { plan = plan.completeCurrent() }
        assertTrue(plan.isFinished)
        assertNull(plan.runningIndex)
        assertEquals(listOf(VoiceTaskStatus.DONE, VoiceTaskStatus.DONE), plan.tasks.map { it.status })
    }

    @Test
    fun `portal targets map onto the launcher's own panels`() {
        assertEquals(PortalCommand.ShowPanel(PanelKind.THERMOSTAT), portalCommandOf("thermostat"))
        assertEquals(PortalCommand.ShowPanel(PanelKind.WEATHER), portalCommandOf(" Weather "))
        assertEquals(PortalCommand.ClosePanel, portalCommandOf("close"))
        assertNull(portalCommandOf("fridge"))
        // The fallback shell is not a destination anyone can name out loud.
        assertNull(portalCommandOf("generic_details"))
        assertFalse("generic_details" in PORTAL_PANEL_TARGETS)
        assertTrue("vacuum" in PORTAL_PANEL_TARGETS)
    }
}
