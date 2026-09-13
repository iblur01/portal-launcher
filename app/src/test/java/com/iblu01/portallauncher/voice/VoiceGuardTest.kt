package com.iblu01.portallauncher.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGuardTest {

    @Test
    fun `locks and alarms are guarded, lights are not`() {
        assertTrue(VoiceGuard.isGuardedDomain("lock"))
        assertTrue(VoiceGuard.isGuardedDomain("alarm_control_panel"))
        assertFalse(VoiceGuard.isGuardedDomain("light"))
        assertTrue(VoiceGuard.isGuardedEntity("lock.porte_entree"))
        assertFalse(VoiceGuard.isGuardedEntity("light.cuisine"))
    }

    /** A blind is a cover and needs no confirmation; a garage door is a cover and does. */
    @Test
    fun `only the covers that give access to the house are guarded`() {
        assertTrue(VoiceGuard.isGuardedEntity("cover.garage", deviceClass = "garage"))
        assertFalse(VoiceGuard.isGuardedEntity("cover.salon", deviceClass = "blind"))
    }

    @Test
    fun `an explicit guarded domain or device class needs confirmation`() {
        assertTrue(
            VoiceGuard.intentNeedsConfirmation(
                domain = "lock", deviceClass = null, name = "porte", guardedNames = { emptyList() },
            ),
        )
        assertTrue(
            VoiceGuard.intentNeedsConfirmation(
                domain = "cover", deviceClass = "garage", name = null, guardedNames = { emptyList() },
            ),
        )
    }

    /**
     * The case that matters: the model passes no domain at all, just the name the user said. Left
     * unmatched, "ouvre la porte d'entrée" would unlock a door on a voice alone.
     */
    @Test
    fun `a guarded entity is recognised from the spoken name alone`() {
        val guarded = listOf("Porte d'entrée", "lock.porte_entree", "Alarme maison")
        assertTrue(
            VoiceGuard.intentNeedsConfirmation(
                domain = null, deviceClass = null, name = "porte d'entrée", guardedNames = { guarded },
            ),
        )
        // The user says less than the entity is called, and the other way round.
        assertTrue(
            VoiceGuard.intentNeedsConfirmation(
                domain = null, deviceClass = null, name = "la porte d'entrée du bas", guardedNames = { guarded },
            ),
        )
        assertFalse(
            VoiceGuard.intentNeedsConfirmation(
                domain = null, deviceClass = null, name = "plafond cuisine", guardedNames = { guarded },
            ),
        )
        assertFalse(
            VoiceGuard.intentNeedsConfirmation(
                domain = "light", deviceClass = null, name = null, guardedNames = { guarded },
            ),
        )
    }

    /** A call decided by the domain must never touch the entity list. */
    @Test
    fun `the guarded name list is only read when the name is the only clue`() {
        var reads = 0
        val names = { reads++; listOf("Porte d'entrée") }

        VoiceGuard.intentNeedsConfirmation("lock", null, "porte", names)
        VoiceGuard.intentNeedsConfirmation("light", null, null, names)
        VoiceGuard.intentNeedsConfirmation(null, "garage", null, names)
        assertEquals(0, reads)

        VoiceGuard.intentNeedsConfirmation(null, null, "porte", names)
        assertEquals(1, reads)
    }

    @Test
    fun `scheduled actions survive a round trip and drop what is unreadable`() {
        val actions = listOf(
            ScheduledAction("s1", 1_700_000_000_000L, "éteindre le salon", GeminiLive.INTENT_TOOL, JSONObject().put("intent", "HassTurnOff")),
        )
        val decoded = decodeScheduledActions(encodeScheduledActions(actions))
        assertEquals(1, decoded.size)
        assertEquals("s1", decoded[0].id)
        assertEquals("HassTurnOff", decoded[0].args.getString("intent"))

        assertTrue(decodeScheduledActions("not json").isEmpty())
        // Entries without an id, a tool or a deadline are dropped rather than fired at epoch.
        assertTrue(decodeScheduledActions("""[{"id":"x"},{"tool":"y"},{"id":"z","tool":"t"}]""").isEmpty())
    }

    @Test
    fun `a schedule call is rejected unless its delay and payload are usable`() {
        assertNull(VoiceScheduler.actionFrom(JSONObject().put("tool", "ha_intent").put("args", JSONObject())))
        assertNull(VoiceScheduler.actionFrom(JSONObject().put("delay_minutes", 0).put("tool", "ha_intent").put("args", JSONObject())))
        assertNull(
            VoiceScheduler.actionFrom(
                JSONObject().put("delay_minutes", MAX_SCHEDULE_DELAY_MINUTES + 1)
                    .put("tool", "ha_intent").put("args", JSONObject()),
            ),
        )
        assertNull(VoiceScheduler.actionFrom(JSONObject().put("delay_minutes", 5).put("tool", "ha_intent")))

        // The arguments arrive as a JSON string as often as as an object.
        val action = VoiceScheduler.actionFrom(
            JSONObject()
                .put("delay_minutes", 10)
                .put("title", "éteindre le salon")
                .put("tool", GeminiLive.INTENT_TOOL)
                .put("args", """{"intent":"HassTurnOff","name":"salon"}"""),
        )
        assertEquals("salon", action?.args?.getString("name"))
        assertTrue((action?.atMs ?: 0L) > System.currentTimeMillis())
    }
}
