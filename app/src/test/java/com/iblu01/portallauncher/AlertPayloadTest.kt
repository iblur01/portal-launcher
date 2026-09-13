package com.iblu01.portallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPayloadTest {

    @Test
    fun `plain text keeps the pre-JSON behaviour`() {
        val alert = AlertPayload.parse("  Quelqu'un sonne  ")!!
        assertEquals("Quelqu'un sonne", alert.message)
        assertEquals(AlertPayload.DEFAULT_TONE, alert.tone)
        assertEquals(AlertLevel.INFO, alert.level)
        assertTrue(alert.wake)
    }

    @Test
    fun `malformed json falls back to the raw text`() {
        assertEquals("{oops", AlertPayload.parse("{oops")!!.message)
    }

    @Test
    fun `empty payload shows nothing`() {
        assertNull(AlertPayload.parse("   "))
        assertNull(AlertPayload.parse("""{"icon":"mdi:bell"}"""))
    }

    @Test
    fun `full payload is read field by field`() {
        val alert = AlertPayload.parse(
            """{"message":"Colis livre","title":"Livraison","icon":"mdi:package",
               "audio":"http://ha.local:8123/api/tts_proxy/a.mp3","level":"critical",
               "duration":9000,"wake":false}"""
        )!!
        assertEquals("Colis livre", alert.message)
        assertEquals("Livraison", alert.title)
        assertEquals("mdi:package", alert.icon)
        assertEquals(AlertLevel.CRITICAL, alert.level)
        assertEquals(9000L, alert.durationMs)
        assertEquals(false, alert.wake)
        // Audio of its own: no tone on top of it.
        assertNull(alert.tone)
    }

    @Test
    fun `no audio means the fallback tone`() {
        assertEquals("alert", AlertPayload.parse("""{"message":"hop"}""")!!.tone)
        assertEquals("doorbell", AlertPayload.parse("""{"message":"hop","tone":"doorbell"}""")!!.tone)
    }

    @Test
    fun `words to say are carried instead of a clip`() {
        val alert = AlertPayload.parse(
            """{"message":"Colis livre","tts":"Un colis vient d'arriver",
               "engine":"tts.google_ai_tts","language":"fr-FR"}"""
        )!!
        assertEquals("Un colis vient d'arriver", alert.tts)
        assertEquals("tts.google_ai_tts", alert.engine)
        assertEquals("fr-FR", alert.language)
        // Speech and chime would talk over each other, but the chime stays readable as the
        // fallback for when the speech cannot be fetched.
        assertNull(alert.tone)
        assertEquals("doorbell", AlertPayload.parse("""{"message":"m","tts":"x","tone":"doorbell"}""")!!.tone)
    }

    @Test
    fun `speech alone is enough to be worth showing`() {
        assertEquals("", AlertPayload.parse("""{"tts":"Il est huit heures"}""")!!.message)
    }

    @Test
    fun `levels carry their own default duration`() {
        assertNull(AlertPayload.parse("""{"message":"x","level":"critical"}""")!!.level.defaultDurationMs)
        assertEquals(8_000L, AlertLevel.parse("warning").defaultDurationMs)
        assertEquals(AlertLevel.INFO, AlertLevel.parse("nonsense"))
    }

    @Test
    fun `an explicit colour is read as opaque argb, nonsense is ignored`() {
        fun argb(raw: String?) = AlertPayload("m", color = raw).accentArgb()

        assertEquals(0xFF30D158.toInt(), argb("#30D158"))
        assertEquals(0xFF30D158.toInt(), argb("30d158"))
        assertEquals(0xFFAABBCC.toInt(), argb("#abc"))
        assertEquals(0x8030D158.toInt(), argb("#8030D158"))
        assertNull(argb(null))
        assertNull(argb("rouge"))
        assertNull(argb("#12345"))
    }

    @Test
    fun `a duration is read however it was written`() {
        assertEquals(90_000L, AlertPayload.duration("90"))
        assertEquals(90_000L, AlertPayload.duration("1:30"))
        assertEquals(600_000L, AlertPayload.duration("00:10:00"))
        assertEquals(300_000L, AlertPayload.duration("5m"))
        assertEquals(5_400_000L, AlertPayload.duration("1h30m"))
        assertEquals(1_500L, AlertPayload.duration("1.5"))
        assertNull(AlertPayload.duration(null))
        assertNull(AlertPayload.duration(""))
        assertNull(AlertPayload.duration("0"))
        assertNull(AlertPayload.duration("bientôt"))
    }

    @Test
    fun `an absolute end instant beats a duration`() {
        val alert = AlertPayload.parse(
            """{"message":"Cuisson","countdown":"10m","until":"2026-09-05T22:30:00+02:00"}"""
        )!!
        assertTrue(alert.isTimer)
        assertEquals(600_000L, alert.countdownMs)
        val until = AlertPayload.instant("2026-09-05T22:30:00+02:00")
        assertEquals(until, alert.untilEpochMs)
        assertEquals(until, alert.endsAt(1_000L))
        // Home Assistant writes this shape too.
        assertEquals(until, AlertPayload.instant("2026-09-05 22:30:00+02:00"))
        assertNull(AlertPayload.instant("demain"))
    }

    @Test
    fun `a bare duration counts from the moment it lands`() {
        val alert = AlertPayload.parse("""{"message":"Minuteur","countdown":"1:30","end_tone":"chime"}""")!!
        assertEquals(1_000_090_000L, alert.endsAt(1_000_000_000L))
        assertEquals("chime", alert.endTone)
        assertFalse(AlertPayload.parse("""{"message":"Minuteur"}""")!!.isTimer)
    }

    @Test
    fun `an alarm panel gets its beeps, its siren and its cancel`() {
        val delay = AlertPayload.parse(
            """{"message":"Sortez","countdown":"30s","tick":"beep","tick_urgent_at":"5s"}"""
        )!!
        assertEquals("beep", delay.tick)
        assertEquals(5_000L, delay.tickUrgentAtMs)
        assertEquals(10_000L, AlertPayload.parse("""{"message":"m","tick":"beep"}""")!!.tickUrgentAtMs)

        val triggered = AlertPayload.parse("""{"message":"Intrusion","tone":"siren","repeat":"2s"}""")!!
        assertEquals(2_000L, triggered.repeatMs)
        assertNull(AlertPayload.parse("""{"message":"m"}""")!!.repeatMs)

        assertTrue(AlertPayload.isDismiss("""{"dismiss": true}"""))
        assertTrue(AlertPayload.isDismiss("  dismiss "))
        assertTrue(AlertPayload.isDismiss("clear"))
        assertFalse(AlertPayload.isDismiss("""{"message":"Intrusion"}"""))
        assertFalse(AlertPayload.isDismiss("Intrusion"))
    }

    @Test
    fun `detail lines accept both shapes and drop the rest`() {
        val alert = AlertPayload.parse(
            """{"message":"Armement demandé","badge":"ignoré","items":[
                 {"text":"Fenêtre cuisine","icon":"mdi:window-open","note":"ignoré"},
                 "Porte terrasse", "", 42, {"icon":"mdi:door"}]}"""
        )!!
        assertEquals("ignoré", alert.badge)
        assertEquals(2, alert.items.size)
        assertEquals(AlertItem("Fenêtre cuisine", "mdi:window-open", "ignoré"), alert.items[0])
        assertEquals(AlertItem("Porte terrasse"), alert.items[1])
        assertTrue(AlertPayload.parse("""{"message":"m"}""")!!.items.isEmpty())
    }

    @Test
    fun `a keypad turns the notification into an alarm screen`() {
        val alarm = AlertPayload.parse(
            """{"message":"Désarmez","keypad":"alarm_control_panel.alarmo","level":"critical"}"""
        )!!
        assertEquals("alarm_control_panel.alarmo", alarm.keypad)
        // The keypad brings the black ground and the refusal to be tapped away with it.
        assertTrue(alarm.blackout)
        assertFalse(alarm.dismissible)

        val plain = AlertPayload.parse("""{"message":"Colis livré"}""")!!
        assertNull(plain.keypad)
        assertFalse(plain.blackout)
        assertTrue(plain.dismissible)

        // Either half can still be asked for on its own, or waived.
        assertTrue(AlertPayload.parse("""{"message":"m","blackout":true}""")!!.blackout)
        val tappable = AlertPayload.parse(
            """{"message":"m","keypad":"alarm_control_panel.alarmo","dismissible":true}"""
        )!!
        assertTrue(tappable.dismissible)
        // A keypad alone is worth showing, with no message at all.
        assertEquals("", AlertPayload.parse("""{"keypad":"alarm_control_panel.alarmo"}""")!!.message)
    }

    @Test
    fun `only the configured home assistant host is ever fetched`() {
        val ha = "http://ha.local:8123"
        fun audio(url: String) = AlertPayload("m", audio = url).playableAudio(ha)

        assertEquals("http://ha.local:8123/api/tts_proxy/a.mp3", audio("http://ha.local:8123/api/tts_proxy/a.mp3"))
        // A different port on the same host is still the same host.
        assertEquals("https://ha.local/a.mp3", audio("https://ha.local/a.mp3"))
        assertNull(audio("http://evil.example/a.mp3"))
        assertNull(audio("file:///data/data/com.iblu01.portallauncher/x.mp3"))
        assertNull(audio("not a url"))
        assertNull(AlertPayload("m").playableAudio(ha))
        assertNull(AlertPayload("m", audio = "http://ha.local/a.mp3").playableAudio(""))
    }
}
