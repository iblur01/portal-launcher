package com.iblu01.portallauncher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantTest {

    @Test
    fun `offer url is kept and its token extracted`() {
        val endpoint = parseVoiceEndpoint("http://ha.example.com:7860/api/offer?token=s3cret")
        assertEquals("http://ha.example.com:7860/api/offer", endpoint?.offerUrl)
        assertEquals("s3cret", endpoint?.token)
    }

    /** The address the add-on page shows for ESPHome satellites: same host, different path. */
    @Test
    fun `esphome satellite websocket url is rewritten to the offer path`() {
        val endpoint = parseVoiceEndpoint("ws://ha.example.com:7860/api/assist/esphome?token=abc&flow_id=x")
        assertEquals("http://ha.example.com:7860/api/offer", endpoint?.offerUrl)
        assertEquals("abc", endpoint?.token)
    }

    @Test
    fun `bare host gets the add-on scheme port and path`() {
        val endpoint = parseVoiceEndpoint("  homeassistant.local  ")
        assertEquals("http://homeassistant.local:7860/api/offer", endpoint?.offerUrl)
        assertNull(endpoint?.token)
    }

    @Test
    fun `https and explicit port survive`() {
        val endpoint = parseVoiceEndpoint("https://voice.example.com:8443")
        assertEquals("https://voice.example.com:8443/api/offer", endpoint?.offerUrl)
    }

    @Test
    fun `unusable input is rejected rather than guessed`() {
        assertNull(parseVoiceEndpoint(""))
        assertNull(parseVoiceEndpoint("   "))
        assertNull(parseVoiceEndpoint("ftp://ha.example.com/api/offer"))
    }

    @Test
    fun `percent encoded token is decoded`() {
        val endpoint = parseVoiceEndpoint("http://ha:7860/api/offer?token=a%2Fb%3Dc")
        assertEquals("a/b=c", endpoint?.token)
    }

    @Test
    fun `transport states map onto phases`() {
        assertEquals(VoicePhase.CONNECTING, phaseForTransportState("Connecting", VoicePhase.WAITING_FOR_WAKE))
        assertEquals(VoicePhase.LISTENING, phaseForTransportState("Ready", VoicePhase.CONNECTING))
        assertEquals(VoicePhase.WAITING_FOR_WAKE, phaseForTransportState("Disconnected", VoicePhase.SPEAKING))
        assertEquals(VoicePhase.ERROR, phaseForTransportState("Error", VoicePhase.LISTENING))
    }

    /** A live turn's finer phase must not be flattened by a repeated transport notification. */
    @Test
    fun `connected does not overwrite an in-progress turn`() {
        assertEquals(VoicePhase.SPEAKING, phaseForTransportState("Connected", VoicePhase.SPEAKING))
        assertEquals(VoicePhase.THINKING, phaseForTransportState("Ready", VoicePhase.THINKING))
    }

    @Test
    fun `calibration derives a gate above the room noise and clamps both ends`() {
        // Quiet room: gate stays at the historical default, never below.
        assertEquals(0.002f, MicCalibration(noiseFloor = 0.0001f, playbackRms = 0.05f).noiseGateRms, 1e-6f)
        // Normal room: 3x the floor.
        assertEquals(0.009f, MicCalibration(noiseFloor = 0.003f, playbackRms = 0.05f).noiseGateRms, 1e-6f)
        // Loud room: capped so distant speech is not gated away.
        assertEquals(0.02f, MicCalibration(noiseFloor = 0.05f, playbackRms = 0.2f).noiseGateRms, 1e-6f)
    }

    @Test
    fun `calibration sizes the gain cap from the room noise floor`() {
        // Quiet room: distant speech is estimated well below target, so it earns headroom.
        assertEquals(10f, MicCalibration(noiseFloor = 0.001f, playbackRms = 0.1f).maxGain, 1e-6f)
        assertEquals(12f, MicCalibration(noiseFloor = 0.0001f, playbackRms = 0.1f).maxGain, 1e-6f)
        // Noisy room: speech already reaches target level, amplifying would only raise the noise.
        assertEquals(1f, MicCalibration(noiseFloor = 0.01f, playbackRms = 0.1f).maxGain, 1e-6f)
        // The loudspeaker level must not influence the cap: that was the bug that disabled gain.
        assertEquals(
            MicCalibration(noiseFloor = 0.002f, playbackRms = 0.01f).maxGain,
            MicCalibration(noiseFloor = 0.002f, playbackRms = 0.30f).maxGain,
            1e-6f,
        )
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
}
