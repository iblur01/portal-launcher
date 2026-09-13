package com.iblu01.portallauncher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantTest {

    @Test
    fun `voice sleep accepts minutes hours converted to minutes and indefinite mode`() {
        val now = 1_000_000L
        assertEquals(now + 15 * 60_000L, voiceSleepUntil(now, 15, false))
        assertEquals(now + 120 * 60_000L, voiceSleepUntil(now, 120, false))
        assertEquals(Long.MAX_VALUE, voiceSleepUntil(now, null, true))
        assertNull(voiceSleepUntil(now, null, false))
        assertNull(voiceSleepUntil(now, 0, false))
        assertNull(voiceSleepUntil(now, MAX_VOICE_SLEEP_MINUTES.toLong() + 1, false))
    }

    @Test
    fun `calibration derives a gate above the room noise and clamps both ends`() {
        // Quiet room: gate stays at the historical default, never below.
        assertEquals(0.002f, MicCalibration(noiseFloor = 0.0001f, playbackRms = 0.05f).noiseGateRms, 1e-6f)
        // Normal room: 3x the floor.
        assertEquals(0.0045f, MicCalibration(noiseFloor = 0.0015f, playbackRms = 0.05f).noiseGateRms, 1e-6f)
        // Loud room: capped below anything measured as speech on this panel, so a high floor can
        // never gate a real sentence away.
        assertEquals(0.005f, MicCalibration(noiseFloor = 0.05f, playbackRms = 0.2f).noiseGateRms, 1e-6f)
    }

    @Test
    fun `calibration sizes the gain cap from the room noise floor`() {
        // Quiet room: distant speech is estimated well below target, so it earns headroom.
        assertEquals(10f, MicCalibration(noiseFloor = 0.001f, playbackRms = 0.1f).maxGain, 1e-6f)
        assertEquals(12f, MicCalibration(noiseFloor = 0.0001f, playbackRms = 0.1f).maxGain, 1e-6f)
        // A noisy room reduces the gain but never switches it off.
        assertEquals(2f, MicCalibration(noiseFloor = 0.01f, playbackRms = 0.1f).maxGain, 1e-6f)
        // The loudspeaker level must not influence the cap: that was the bug that disabled gain.
        assertEquals(
            MicCalibration(noiseFloor = 0.002f, playbackRms = 0.01f).maxGain,
            MicCalibration(noiseFloor = 0.002f, playbackRms = 0.30f).maxGain,
            1e-6f,
        )
    }

    /**
     * The measurement that silenced a panel in the field: 0.0286 is speech level, so the silent
     * step had heard someone talk. Derived as-is it produced a gate of 0.02 — above the 0.008 to
     * 0.017 this panel reads for real speech — and a gain cap of 1, i.e. no gain at all. The wake
     * word then scored 0.001 on a clearly spoken phrase and never fired again.
     */
    @Test
    fun `a calibration measured on a noisy room is refused rather than applied`() {
        val bogus = MicCalibration(noiseFloor = 0.028592935f, playbackRms = 0.083941f)

        assertFalse(bogus.isPlausible)
        // Even if it were applied, it can no longer gate speech away nor disable the gain.
        assertTrue(bogus.noiseGateRms < 0.008f)
        assertTrue(bogus.maxGain >= 2f)

        assertTrue(MicCalibration(noiseFloor = 0.0008f, playbackRms = 0.08f).isPlausible)
        assertFalse(MicCalibration(noiseFloor = 0f, playbackRms = 0.08f).isPlausible)
        assertFalse(MicCalibration(noiseFloor = 0.0008f, playbackRms = 0f).isPlausible)
    }

    @Test
    fun `cues render the announced duration and stay inside the sample range`() {
        val sampleRate = 44100
        for (cue in VoiceCue.entries) {
            val pcm = cue.render(sampleRate)
            assertEquals(cue.durationMs * sampleRate / 1000, pcm.size)
            // Fades run to silence at both ends: a hard edge clicks, and a click is exactly what
            // the wake classifier and the server VAD mistake for speech.
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm.last().toInt())
            assertTrue("cue $cue must be audible", pcm.any { kotlin.math.abs(it.toInt()) > 1000 })
        }
    }

    @Test
    fun `wake word label drops the path and the model version`() {
        assertEquals("hey jarvis", wakeWordLabel("wakeword/hey_jarvis_v0.1.onnx"))
        assertEquals("alexa", wakeWordLabel("wakeword/alexa_v0.1.onnx"))
        assertEquals("custom word", wakeWordLabel("custom_word.onnx"))
    }

    @Test
    fun `entity search ignores accents and plurals`() {
        assertTrue(matchesQuery("binary_sensor.salon Fenetre salon window", "fenêtres"))
        assertTrue(matchesQuery("binary_sensor.x Salon window", "fenetre salon"))
        assertTrue(matchesQuery("light.cuisine Plafond cuisine", "cuisine plafond"))
        // Every word still has to hit: one shared word must not return the whole house.
        assertFalse(matchesQuery("light.cuisine Plafond cuisine", "cuisine jardin"))
        assertFalse(matchesQuery("light.cuisine Plafond cuisine", "   "))
    }
}
