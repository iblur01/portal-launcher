package com.iblu01.portallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AlarmStateTest {

    @Test
    fun `only the two urgent states take the whole screen`() {
        // Walking out has nothing to type; walking in does.
        assertFalse(AlarmPhase.ARMING.fullScreen)
        assertTrue(AlarmPhase.PENDING.fullScreen)
        assertTrue(AlarmPhase.TRIGGERED.fullScreen)
        assertFalse(AlarmPhase.ARMED.fullScreen)
        assertFalse(AlarmPhase.DISARMED.fullScreen)
        assertFalse(AlarmPhase.FAILED.fullScreen)
    }

    @Test
    fun `only the screens that demand a code refuse the tap`() {
        assertTrue(AlarmPhase.PENDING.locked)
        assertTrue(AlarmPhase.TRIGGERED.locked)
        assertFalse(AlarmPhase.ARMING.locked)
        assertFalse(AlarmPhase.ARMED.locked)
    }

    @Test
    fun `a bare state name is enough`() {
        val alert = AlarmState.parse("triggered")!!.render()
        assertEquals("Intrusion detected", alert.message)
        assertEquals("Alarm", alert.title)
        assertEquals(AlertLevel.CRITICAL, alert.level)
        assertEquals("siren", alert.tone)
        assertEquals(3_000L, alert.repeatMs)
        assertTrue(alert.blackout)
        assertFalse(alert.dismissible)
        // No entity was named, so there is no keypad to draw.
        assertNull(alert.keypad)
    }

    @Test
    fun `an entry delay counts down, beeps, and asks for the code`() {
        val alert = AlarmState.parse(
            """{"state":"pending","entity":"alarm_control_panel.alarmo","delay":60,
               "sensors":[{"text":"Porte entrée","icon":"mdi:door-open"}]}"""
        )!!.render()
        assertEquals(60_000L, alert.countdownMs)
        assertEquals("beep", alert.tick)
        assertEquals("alarm_control_panel.alarmo", alert.keypad)
        assertEquals(1, alert.items.size)
        // Zero is not a verdict here: the next state is.
        assertEquals("none", alert.endTone)
    }

    @Test
    fun `an exit delay counts down without taking the screen`() {
        val alert = AlarmState.parse("""{"state":"arming","mode":"Away","delay":"30s"}""")!!.render()
        assertEquals("Arming in Away mode, please leave", alert.message)
        assertEquals(30_000L, alert.countdownMs)
        assertEquals("Away", alert.badge)
        assertFalse(alert.blackout)
        assertTrue(alert.dismissible)
    }

    @Test
    fun `settled states are news, not incidents`() {
        val armed = AlarmState.parse("""{"state":"armed_away","mode":"Away"}""")!!.render()
        assertEquals("System armed in Away mode", armed.message)
        assertEquals(6_000L, armed.durationMs)
        assertNull(armed.countdownMs)

        val failed = AlarmState.parse(
            """{"state":"failed_to_arm","sensors":["Kitchen window"]}"""
        )!!.render()
        assertEquals("Arming refused, Kitchen window is open", failed.message)
        assertEquals("error", failed.tone)
        assertTrue(failed.dismissible)
    }

    @Test
    fun `an empty or unknown payload clears the alarm`() {
        assertNull(AlarmState.parse(""))
        assertNull(AlarmState.parse("   "))
        assertNull(AlarmState.parse("unavailable"))
        assertNull(AlarmState.parse("""{"state":"disarming"}"""))
        assertNull(AlarmState.parse("pas du json"))
    }

    @Test
    fun `every state has something to say without being told`() {
        // The wording itself lives in resources; what matters here is that no state stays mute.
        listOf("arming", "pending", "triggered", "armed_away", "disarmed", "failed_to_arm")
            .forEach { state ->
                assertNotNull("$state should speak", AlarmState.parse(state)!!.render().tts)
            }
    }

    @Test
    fun `silence can be asked for, either way round`() {
        assertNull(AlarmState.parse("""{"state":"armed_away","speak":false}""")!!.render().tts)
        assertNull(AlarmState.parse("""{"state":"armed_away","silent":true}""")!!.render().tts)
        // A phrase still wins over the default.
        assertEquals("Bonne nuit", AlarmState.parse("""{"state":"armed_night","speak":"Bonne nuit"}""")!!.render().tts)
    }

    @Test
    fun `what Home Assistant writes overrides what the panel would say`() {
        val alert = AlarmState.parse(
            """{"state":"triggered","message":"Intrusion — garage","title":"LE QG",
               "speak":"Alarme au garage","engine":"tts.google_ai_tts","language":"fr-FR"}"""
        )!!.render()
        assertEquals("Intrusion — garage", alert.message)
        assertEquals("LE QG", alert.title)
        assertEquals("Alarme au garage", alert.tts)
        assertEquals("tts.google_ai_tts", alert.engine)
    }

    /** The composed wording comes from resources, so rendering needs a real context. */
    private fun AlarmState.render(): AlertPayload = toAlert(RuntimeEnvironment.getApplication())!!
}
