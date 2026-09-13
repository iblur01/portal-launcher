package com.iblu01.portallauncher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProbeTest {

    private val body = """
        {"models":[
          {"name":"models/gemini-2.5-flash-native-audio-preview-09-2025",
           "supportedGenerationMethods":["bidiGenerateContent","countTokens"]},
          {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]}
        ]}
    """.trimIndent()

    @Test
    fun `only the models that can hold a live session count as live`() {
        val result = GeminiProbe.parseModels(body) as GeminiProbe.Result.Ok

        assertEquals(setOf("gemini-2.5-flash-native-audio-preview-09-2025", "gemini-2.5-flash"), result.all)
        assertEquals(setOf("gemini-2.5-flash-native-audio-preview-09-2025"), result.live)
    }

    /** The settings field accepts both spellings, so the check has to as well. */
    @Test
    fun `the models prefix is optional`() {
        val result = GeminiProbe.parseModels(body) as GeminiProbe.Result.Ok

        assertTrue(GeminiProbe.supportsLive(result, "gemini-2.5-flash-native-audio-preview-09-2025"))
        assertTrue(GeminiProbe.supportsLive(result, "models/gemini-2.5-flash-native-audio-preview-09-2025"))
    }

    /**
     * A real model that only does text is a different mistake from a name that does not exist,
     * and the wizard says something different for each.
     */
    @Test
    fun `a text only model exists but is not live`() {
        val result = GeminiProbe.parseModels(body) as GeminiProbe.Result.Ok

        assertTrue(GeminiProbe.exists(result, "gemini-2.5-flash"))
        assertFalse(GeminiProbe.supportsLive(result, "gemini-2.5-flash"))
        assertFalse(GeminiProbe.exists(result, "gemini-9-imaginary"))
    }

    @Test
    fun `an unreadable answer is not mistaken for an empty model list`() {
        assertTrue(GeminiProbe.parseModels("not json") is GeminiProbe.Result.Unreachable)
        assertTrue(GeminiProbe.parseModels("""{"error":{"code":403}}""") is GeminiProbe.Result.Unreachable)
        val empty = GeminiProbe.parseModels("""{"models":[]}""") as GeminiProbe.Result.Ok
        assertTrue(empty.live.isEmpty())
    }
}
