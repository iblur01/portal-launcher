package com.iblu01.portallauncher.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveTest {

    @Test
    fun `setup declares audio output the tools and both transcriptions`() {
        val setup = JSONObject(
            GeminiLive.setupMessage(
                model = "gemini-live-test",
                voiceName = "Puck",
                languageCode = "fr-FR",
                systemPrompt = "sois bref",
            ),
        ).getJSONObject("setup")

        assertEquals("models/gemini-live-test", setup.getString("model"))
        val generation = setup.getJSONObject("generationConfig")
        assertEquals("AUDIO", generation.getJSONArray("responseModalities").getString(0))
        assertEquals("fr-FR", generation.getJSONObject("speechConfig").getString("languageCode"))
        assertTrue(setup.has("inputAudioTranscription"))
        assertTrue(setup.has("outputAudioTranscription"))

        val tools = setup.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(GeminiLive.END_CONVERSATION_TOOL in names)
        assertTrue(GeminiLive.DISMISS_CONVERSATION_TOOL in names)
        assertTrue(GeminiLive.SLEEP_ASSISTANT_TOOL in names)
        assertTrue(GeminiLive.PORTAL_SETTINGS_TOOL in names)
        assertTrue(GeminiLive.FIND_ENTITIES_TOOL in names)
        assertTrue(GeminiLive.CALL_SERVICE_TOOL in names)
        assertTrue(GeminiLive.GET_STATE_TOOL in names)
        assertTrue(GeminiLive.INTENT_TOOL in names)
    }

    /**
     * The intent vocabulary is handed over as an enum, which is what keeps one function doing the
     * work of Home Assistant's forty intent tools. A missing enum would let the model invent
     * intent names, and HA answers those with a flat error.
     */
    @Test
    fun `the intent tool enumerates home assistant's intents`() {
        val tools = JSONObject(GeminiLive.setupMessage("m", "Puck", "fr-FR", "p"))
            .getJSONObject("setup").getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations")
        val intentTool = (0 until tools.length())
            .map { tools.getJSONObject(it) }
            .single { it.getString("name") == GeminiLive.INTENT_TOOL }

        val properties = intentTool.getJSONObject("parameters").getJSONObject("properties")
        val enum = properties.getJSONObject("intent").getJSONArray("enum")
        val values = (0 until enum.length()).map { enum.getString(it) }
        assertEquals(GeminiLive.HA_INTENTS, values)
        assertTrue("HassTurnOn" in values)
        assertTrue("HassLightSet" in values)
        // Deprecated intents must not be offered: HassTurnOn/HassSetPosition replace them.
        assertTrue("HassToggle" !in values)
        assertTrue("HassOpenCover" !in values)
        // Target slots are shared by most intents and stay named; the rest travel in extras.
        for (slot in listOf("name", "area", "floor", "domain", "device_class", "extras")) {
            assertTrue("slot $slot missing", properties.has(slot))
        }
        assertEquals("intent", intentTool.getJSONObject("parameters").getJSONArray("required").getString(0))
    }

    /** An already-prefixed model name must not become `models/models/…`. */
    @Test
    fun `model name is prefixed once`() {
        val setup = JSONObject(GeminiLive.setupMessage("models/x", "Puck", "en-US", "p"))
            .getJSONObject("setup")
        assertEquals("models/x", setup.getString("model"))
    }

    /**
     * A session that is not compressed dies at 15 minutes, and a handle is what carries a
     * follow-up across a hang-up. Both are setup-only: forgetting them is silent.
     */
    @Test
    fun `setup asks for context compression and session resumption`() {
        val setup = JSONObject(GeminiLive.setupMessage("m", "Puck", "fr-FR", "p"))
            .getJSONObject("setup")
        assertTrue(setup.getJSONObject("contextWindowCompression").has("slidingWindow"))
        // No handle on a first session: the field is present but empty, which asks for one.
        assertFalse(setup.getJSONObject("sessionResumption").has("handle"))

        val resumed = JSONObject(GeminiLive.setupMessage("m", "Puck", "fr-FR", "p", "h-42"))
            .getJSONObject("setup")
        assertEquals("h-42", resumed.getJSONObject("sessionResumption").getString("handle"))
    }

    /**
     * generationComplete arrives before the turn is over: a turn continues past it whenever a
     * tool call is answered mid-turn. Treating it as the end reopens the microphone while the
     * assistant is still talking.
     */
    @Test
    fun `generation complete is not the end of the turn`() {
        assertEquals(
            listOf(GeminiEvent.GenerationComplete),
            parseGeminiMessage("""{"serverContent":{"generationComplete":true}}"""),
        )
        assertEquals(
            listOf(GeminiEvent.GenerationComplete, GeminiEvent.TurnComplete),
            parseGeminiMessage("""{"serverContent":{"generationComplete":true,"turnComplete":true}}"""),
        )
    }

    @Test
    fun `cancelled tool calls and resumption handles are surfaced`() {
        assertEquals(
            listOf(GeminiEvent.ToolCallCancellation(listOf("c1", "c2"))),
            parseGeminiMessage("""{"toolCallCancellation":{"ids":["c1","c2"]}}"""),
        )
        assertEquals(
            listOf(GeminiEvent.ResumptionHandle("h-9")),
            parseGeminiMessage("""{"sessionResumptionUpdate":{"newHandle":"h-9","resumable":true}}"""),
        )
        // A handle the server says is not resumable must not be kept.
        assertEquals(
            emptyList<GeminiEvent>(),
            parseGeminiMessage("""{"sessionResumptionUpdate":{"newHandle":"h-9","resumable":false}}"""),
        )
    }

    @Test
    fun `the panel's own tools are declared`() {
        val tools = JSONObject(GeminiLive.setupMessage("m", "Puck", "fr-FR", "p"))
            .getJSONObject("setup").getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue(GeminiLive.PLAN_TOOL in names)
        assertTrue(GeminiLive.COMPLETE_TASK_TOOL in names)
        assertTrue(GeminiLive.PORTAL_SHOW_TOOL in names)

        val portal = (0 until tools.length()).map { tools.getJSONObject(it) }
            .single { it.getString("name") == GeminiLive.PORTAL_SHOW_TOOL }
        val enum = portal.getJSONObject("parameters").getJSONObject("properties")
            .getJSONObject("target").getJSONArray("enum")
        val targets = (0 until enum.length()).map { enum.getString(it) }
        assertEquals(PORTAL_PANEL_TARGETS + PORTAL_CLOSE_TARGET, targets)

        val settings = (0 until tools.length()).map { tools.getJSONObject(it) }
            .single { it.getString("name") == GeminiLive.PORTAL_SETTINGS_TOOL }
        val actions = settings.getJSONObject("parameters").getJSONObject("properties")
            .getJSONObject("action").getJSONArray("enum")
        assertEquals(
            PortalSettingsTool.ACTIONS,
            (0 until actions.length()).map { actions.getString(it) },
        )
    }

    @Test
    fun `audio frames carry the input sample rate`() {
        val audio = JSONObject(GeminiLive.audioMessage("QUJD"))
            .getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertEquals("QUJD", audio.getString("data"))
    }

    @Test
    fun `tool response keeps the call id so the model can match it`() {
        val call = JSONObject(GeminiLive.toolResponseMessage("c1", "ha_get_state", mapOf("state" to "on")))
            .getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("c1", call.getString("id"))
        assertEquals("ha_get_state", call.getString("name"))
        assertEquals("on", call.getJSONObject("response").getString("state"))
    }

    @Test
    fun `audio transcripts and turn end are read from one frame`() {
        val events = parseGeminiMessage(
            """
            {"serverContent":{
              "outputTranscription":{"text":"bonjour"},
              "modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AAAA"}}]},
              "turnComplete":true}}
            """.trimIndent(),
        )
        assertEquals(
            listOf(GeminiEvent.BotTranscript("bonjour"), GeminiEvent.Audio("AAAA"), GeminiEvent.TurnComplete),
            events,
        )
    }

    /** The playback queue must be flushed before the turn is declared over, in that order. */
    @Test
    fun `interruption is reported before the turn end it arrives with`() {
        val events = parseGeminiMessage("""{"serverContent":{"interrupted":true,"turnComplete":true}}""")
        assertEquals(listOf(GeminiEvent.Interrupted, GeminiEvent.TurnComplete), events)
    }

    @Test
    fun `tool calls are parsed with their arguments`() {
        val events = parseGeminiMessage(
            """{"toolCall":{"functionCalls":[{"id":"c7","name":"ha_call_service","args":{"domain":"light"}}]}}""",
        )
        val call = events.single() as GeminiEvent.ToolCall
        assertEquals("c7", call.id)
        assertEquals("ha_call_service", call.name)
        assertEquals("light", call.args.getString("domain"))
    }

    /** The Live API grows fields faster than a panel gets updated; an unknown frame is not fatal. */
    @Test
    fun `unknown frames yield nothing rather than an error`() {
        assertEquals(emptyList<GeminiEvent>(), parseGeminiMessage("""{"somethingNew":{"a":1}}"""))
        assertEquals(listOf(GeminiEvent.SetupComplete), parseGeminiMessage("""{"setupComplete":{}}"""))
        assertTrue(parseGeminiMessage("not json").single() is GeminiEvent.Error)
    }
}
