package com.iblu01.portallauncher.voice

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire protocol of the Gemini Live API (`BidiGenerateContent` over WebSocket), kept free of
 * Android and of the socket itself so it can be unit-tested on the JVM.
 *
 * Why this replaced the Pipecat satellite: the add-on chained STT, an LLM and TTS as three
 * network hops behind a WebRTC negotiation, which is what made the panel slow and fragile.
 * Gemini Live is speech-to-speech in one socket — audio in, audio out, no transcription round
 * trip in the critical path — so the panel talks to it directly and keeps the wake word local.
 *
 * Audio is raw PCM both ways: 16-bit mono, [INPUT_SAMPLE_RATE] up, [OUTPUT_SAMPLE_RATE] down.
 */
object GeminiLive {
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000

    /** Client-side tool the model calls to hang up ("stop", "au revoir", …). */
    const val END_CONVERSATION_TOOL = "end_conversation"
    const val DISMISS_CONVERSATION_TOOL = "dismiss_unaddressed_conversation"
    const val SLEEP_ASSISTANT_TOOL = "sleep_voice_assistant"
    const val PORTAL_SETTINGS_TOOL = "portal_settings"
    const val CALL_SERVICE_TOOL = "ha_call_service"
    const val INTENT_TOOL = "ha_intent"
    const val VACUUM_ROOM_TOOL = "ha_vacuum_room"

    /** Multi-step work: announce the plan, then settle its steps one by one. */
    const val PLAN_TOOL = "plan_tasks"
    const val COMPLETE_TASK_TOOL = "complete_task"

    /** The launcher itself: open or close one of its panels. */
    const val PORTAL_SHOW_TOOL = "portal_show"

    /** Actions promised for later, and the facts the panel keeps between sessions. */
    const val SCHEDULE_TOOL = "schedule_action"
    const val LIST_SCHEDULED_TOOL = "list_scheduled_actions"
    const val CANCEL_SCHEDULED_TOOL = "cancel_scheduled_action"
    const val REMEMBER_TOOL = "remember_fact"
    const val FORGET_TOOL = "forget_fact"
    const val GET_STATE_TOOL = "ha_get_state"
    const val FIND_ENTITIES_TOOL = "ha_find_entities"

    /**
     * The prebuilt voices the Live API exposes, in Google's own order (the first eight are the
     * ones the half-cascade models also carry). Hardcoded rather than fetched: the list has no
     * endpoint, and a free-text field meant a typo silently fell back to the default voice.
     */
    val VOICES = listOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina",
        "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar",
        "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia",
        "Sadaltager", "Sulafat",
    )

    /**
     * Shown until the panel has asked Google what it actually offers. Not a source of truth —
     * these names are retired without notice, which is why the settings page refreshes the list
     * from the API and keeps whatever is stored as a choice of its own.
     */
    val FALLBACK_LIVE_MODELS = listOf(
        "gemini-2.5-flash-native-audio-preview-09-2025",
        "gemini-2.5-flash-preview-native-audio-dialog",
        "gemini-live-2.5-flash-preview",
        "gemini-2.0-flash-live-001",
    )

    fun endpoint(apiKey: String): String =
        "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
            "?key=$apiKey"

    /**
     * The one message that has to be sent before any audio. Everything the session can do is
     * declared here: modality, voice, language, system prompt and the tool surface.
     *
     * Voice activity detection stays server-side and always on. Barge-in is not a setting here
     * but a client one: closing the microphone while the model speaks is what makes a panel
     * without echo cancellation half duplex, and no server flag can substitute for it.
     */
    fun setupMessage(
        model: String,
        voiceName: String,
        languageCode: String,
        systemPrompt: String,
        resumptionHandle: String? = null,
    ): String {
        val speech = JSONObject()
            .put("languageCode", languageCode)
            .put(
                "voiceConfig",
                JSONObject().put(
                    "prebuiltVoiceConfig",
                    JSONObject().put("voiceName", voiceName),
                ),
            )
        val setup = JSONObject()
            .put("model", if (model.startsWith("models/")) model else "models/$model")
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", speech),
            )
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
            )
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", toolDeclarations())))
            // Transcriptions are not in the audio path; they are what the overlay prints, and
            // what makes a silent failure debuggable instead of mysterious.
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            // Without this the session is hard-capped at 15 minutes (and the socket itself at
            // about 10). A sliding window costs nothing on the exchanges a panel actually has,
            // and is what lets a long plan run to the end instead of being cut off mid-step.
            .put(
                "contextWindowCompression",
                JSONObject().put("slidingWindow", JSONObject()),
            )
        // Asking for a handle costs nothing; using one is what makes "and the kitchen too" work
        // after the panel has already hung up and re-woken (see VoiceAssistantController).
        setup.put(
            "sessionResumption",
            JSONObject().apply { resumptionHandle?.takeIf { it.isNotBlank() }?.let { put("handle", it) } },
        )
        return JSONObject().put("setup", setup).toString()
    }

    /** One 16 kHz PCM chunk. */
    fun audioMessage(base64Pcm: String): String = JSONObject()
        .put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                    .put("data", base64Pcm),
            ),
        )
        .toString()

    /**
     * A turn spoken on the user's behalf, for what happens outside a turn: a guarded action the
     * user has just confirmed with a tap, a scheduled action that fired. Without this the model
     * would have no way of knowing, and would keep waiting or lie about the outcome.
     */
    fun textMessage(text: String): String = JSONObject()
        .put(
            "clientContent",
            JSONObject()
                .put(
                    "turns",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text))),
                    ),
                )
                .put("turnComplete", true),
        )
        .toString()

    fun toolResponseMessage(id: String?, name: String, result: Map<String, Any?>): String {
        val response = JSONObject()
        result.forEach { (k, v) -> response.put(k, v ?: JSONObject.NULL) }
        val call = JSONObject().put("name", name).put("response", response)
        if (!id.isNullOrBlank()) call.put("id", id)
        return JSONObject()
            .put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(call)))
            .toString()
    }

    /**
     * Home Assistant's own intent vocabulary, which is what its Assist pipeline and its MCP
     * server expose to an LLM. Handing the model the list as an enum costs one array; declaring
     * one function per intent would cost forty schemas in every session's setup for the same
     * reach. Deprecated intents (HassOpenCover, HassToggle, …) are left out: their non-deprecated
     * equivalents cover them.
     */
    val HA_INTENTS = listOf(
        "HassTurnOn", "HassTurnOff", "HassGetState", "HassRespond", "HassBroadcast",
        "HassSetPosition", "HassGetCurrentDate", "HassGetCurrentTime", "HassLightSet",
        "HassClimateSetTemperature", "HassClimateGetTemperature", "HassGetWeather",
        "HassListAddItem", "HassListCompleteItem", "HassListRemoveItem",
        "HassShoppingListAddItem", "HassShoppingListCompleteItem",
        "HassVacuumStart", "HassVacuumReturnToBase", "HassVacuumCleanArea",
        "HassMediaPause", "HassMediaUnpause", "HassMediaNext", "HassMediaPrevious",
        "HassMediaPlayerMute", "HassMediaPlayerUnmute", "HassMediaSearchAndPlay",
        "HassSetVolume", "HassSetVolumeRelative",
        "HassStartTimer", "HassCancelTimer", "HassCancelAllTimers", "HassPauseTimer",
        "HassUnpauseTimer", "HassIncreaseTimer", "HassDecreaseTimer", "HassTimerStatus",
        "HassFanSetSpeed", "HassLawnMowerStartMowing", "HassLawnMowerDock",
    )

    /**
     * What the panel lets the model do. Entity ids are not listed anywhere: a Home Assistant has
     * hundreds and pasting them into the prompt would cost more than the conversation. The model
     * looks up what it needs by name through [FIND_ENTITIES_TOOL] instead.
     */
    fun toolDeclarations(): JSONArray {
        fun params(required: List<String>, properties: JSONObject) = JSONObject()
            .put("type", "OBJECT")
            .put("properties", properties)
            .put("required", JSONArray(required))

        fun string(description: String) = JSONObject().put("type", "STRING").put("description", description)

        return JSONArray()
            .put(
                JSONObject()
                    .put("name", END_CONVERSATION_TOOL)
                    .put(
                        "description",
                        "End the conversation and release the microphone. Call this as soon as " +
                            "the user says goodbye, says they are done, tells you to stop, or " +
                            "when the exchange is otherwise finished. Say a short goodbye in the " +
                            "same turn: the panel hangs up once you stop speaking.",
                    )
                    .put("parameters", params(emptyList(), JSONObject())),
            )
            .put(
                JSONObject()
                    .put("name", DISMISS_CONVERSATION_TOOL)
                    .put(
                        "description",
                        "Silently close this accidental activation. Call it immediately, before " +
                            "speaking, when the audio is background conversation, is clearly " +
                            "addressed to another person, or is too incoherent to be a request " +
                            "to this assistant. Do not use it merely because a real request needs " +
                            "clarification. The panel must make no goodbye or acknowledgement.",
                    )
                    .put("parameters", params(emptyList(), JSONObject())),
            )
            .put(
                JSONObject()
                    .put("name", SLEEP_ASSISTANT_TOOL)
                    .put(
                        "description",
                        "Turn off wake-word listening when the user asks you to disable, sleep, " +
                            "mute, or stop listening for a duration or until reactivated. Convert " +
                            "hours to minutes. For an indefinite request set until_reactivated=true. " +
                            "Briefly acknowledge first; the panel mutes after your turn ends.",
                    )
                    .put(
                        "parameters",
                        params(
                            emptyList(),
                            JSONObject()
                                .put(
                                    "duration_minutes",
                                    JSONObject().put("type", "INTEGER").put(
                                        "description",
                                        "Requested duration in minutes (1 to $MAX_VOICE_SLEEP_MINUTES). Omit for an indefinite sleep.",
                                    ),
                                )
                                .put(
                                    "until_reactivated",
                                    JSONObject().put("type", "BOOLEAN").put(
                                        "description",
                                        "True when the assistant must remain off until the user taps the muted-microphone button or re-enables it remotely.",
                                    ),
                                ),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", PORTAL_SETTINGS_TOOL)
                    .put(
                        "description",
                        "Change Portal Launcher itself or open an installed Android app. Use " +
                            "pin_device to enable a Home Assistant device as a pill and put it " +
                            "first among pinned pills; set_device_grouping to organize the Maison " +
                            "device page by room or by type; open_app to open an Android app by " +
                            "its spoken display name or exact package name. It also controls the " +
                            "Maison page, screen timeout, automatic return, clock format, app-grid " +
                            "density, background source and voice behavior. Change only settings " +
                            "the user explicitly requested.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("action"),
                            JSONObject()
                                .put(
                                    "action",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("enum", JSONArray(PortalSettingsTool.ACTIONS)),
                                )
                                .put("device", string("Device friendly name or exact Home Assistant entity_id for pin_device."))
                                .put(
                                    "grouping",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "New organization for set_device_grouping.")
                                        .put("enum", JSONArray(listOf("room", "type"))),
                                )
                                .put("app", string("Installed app display name or exact Android package for open_app."))
                                .put("enabled", JSONObject().put("type", "BOOLEAN").put("description", "Enable/disable set_home_page, set_screen_timeout or set_auto_return."))
                                .put("minutes", JSONObject().put("type", "INTEGER").put("description", "Screen timeout in minutes, 1 to 240."))
                                .put("seconds", JSONObject().put("type", "INTEGER").put("description", "Automatic return delay in seconds, 5 to 60."))
                                .put("clock_format", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("12h", "24h"))))
                                .put("grid_scale", JSONObject().put("type", "NUMBER").put("description", "App-grid scale from 0.7 (compact) to 1.3 (large)."))
                                .put("background", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("system", "neutral", "custom", "immich"))))
                                .put("barge_in", JSONObject().put("type", "BOOLEAN").put("description", "Whether speech may interrupt the assistant."))
                                .put("idle_seconds", JSONObject().put("type", "INTEGER").put("description", "Voice conversation idle timeout, 5 to 300 seconds."))
                                .put("wake_sensitivity", JSONObject().put("type", "INTEGER").put("description", "Wake-word detection threshold from 1 to 95; lower is more sensitive."))
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", FIND_ENTITIES_TOOL)
                    .put(
                        "description",
                        "Search the Home Assistant entities of this home by name, to turn what " +
                            "the user said (\"the kitchen light\") into an entity_id. Needed " +
                            "only for $CALL_SERVICE_TOOL and $GET_STATE_TOOL; $INTENT_TOOL " +
                            "resolves names by itself. Returns at most 20 matches.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("query"),
                            JSONObject()
                                .put("query", string("Words from the entity's name, e.g. \"kitchen\"."))
                                .put("domain", string("Optional domain filter: light, switch, cover, climate, media_player, sensor…")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", INTENT_TOOL)
                    .put(
                        "description",
                        "Act on the home the way Home Assistant's own assistant does, by name: " +
                            "\"turn on the kitchen light\" is HassTurnOn with name=\"kitchen " +
                            "light\". Home Assistant resolves the target itself from its names, " +
                            "aliases, areas and floors, so prefer this over $CALL_SERVICE_TOOL " +
                            "for anything it covers and do not look the entity up first. " +
                            "Returns the sentence Home Assistant would have spoken.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("intent"),
                            JSONObject()
                                .put(
                                    "intent",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Which intent to handle.")
                                        .put("enum", JSONArray(HA_INTENTS)),
                                )
                                .put("name", string("Target name as the user said it, e.g. \"kitchen light\". For list intents, the list's name."))
                                .put("area", string("Room name, e.g. \"kitchen\". Use instead of name to hit everything in a room."))
                                .put("floor", string("Floor name, e.g. \"upstairs\"."))
                                .put("domain", string("Restrict to a domain: light, switch, cover, fan, media_player…"))
                                .put("device_class", string("Restrict to a device class, e.g. blind, garage, window."))
                                .put(
                                    "extras",
                                    string(
                                        "The intent's own slots as a flat JSON object: " +
                                            "brightness, color, temperature, position, " +
                                            "volume_level, volume_step, percentage, item, " +
                                            "search_query, state, message, response, hours, " +
                                            "minutes, seconds.",
                                    ),
                                ),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", PLAN_TOOL)
                    .put(
                        "description",
                        "Announce a multi-step plan before doing it, so the panel can show it on " +
                            "screen. Call this first whenever the request needs more than one " +
                            "action (\"prepare the evening\", \"vacuum the kitchen then the " +
                            "hall\"), with one short title per step, in order, at most " +
                            "$MAX_VOICE_TASKS. Then carry the steps out with the other tools, " +
                            "calling $COMPLETE_TASK_TOOL after each one. Do not use it for a " +
                            "single action: a one-line plan is noise.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("tasks"),
                            JSONObject().put(
                                "tasks",
                                JSONObject()
                                    .put("type", "ARRAY")
                                    .put("description", "Short titles, a few words each, in the order you will do them.")
                                    .put("items", JSONObject().put("type", "STRING")),
                            ),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", COMPLETE_TASK_TOOL)
                    .put(
                        "description",
                        "Mark the step you are on as finished and move to the next one. Call it " +
                            "once per step, right after the step's own tool call, so the panel " +
                            "keeps up. Set failed=true when the step did not work: the panel " +
                            "shows it as failed and you should say so out loud.",
                    )
                    .put(
                        "parameters",
                        params(
                            emptyList(),
                            JSONObject()
                                .put("failed", JSONObject().put("type", "BOOLEAN").put("description", "True when the step failed."))
                                .put("note", string("One short sentence on the outcome, if it is worth showing.")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", PORTAL_SHOW_TOOL)
                    .put(
                        "description",
                        "Show something on the panel's own screen, for when the answer is better " +
                            "looked at than heard: the weather, a thermostat, the media player, " +
                            "the cameras. Use \"$PORTAL_CLOSE_TARGET\" to put the screen back. " +
                            "This only changes what is displayed; it controls nothing in the home.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("target"),
                            JSONObject().put(
                                "target",
                                JSONObject()
                                    .put("type", "STRING")
                                    .put("description", "What to display on the panel.")
                                    .put("enum", JSONArray(PORTAL_PANEL_TARGETS + PORTAL_CLOSE_TARGET)),
                            ),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", SCHEDULE_TOOL)
                    .put(
                        "description",
                        "Promise an action for later, when the user asks for one (\"in ten " +
                            "minutes, turn the lounge off\"). The panel fires it on its own, " +
                            "even if the conversation is long over. Give the action exactly as " +
                            "you would call it now: the tool to run and its arguments. Say out " +
                            "loud that it is scheduled and when. Anything further out than a " +
                            "day belongs in a Home Assistant automation, not here.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("delay_minutes", "title", "tool", "args"),
                            JSONObject()
                                .put(
                                    "delay_minutes",
                                    JSONObject()
                                        .put("type", "INTEGER")
                                        .put("description", "How long from now, in minutes (1 to $MAX_SCHEDULE_DELAY_MINUTES)."),
                                )
                                .put("title", string("What it is, in a few words, for the panel to show."))
                                .put(
                                    "tool",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Which tool to run then.")
                                        .put("enum", JSONArray(listOf(INTENT_TOOL, CALL_SERVICE_TOOL))),
                                )
                                .put("args", string("That tool's arguments, as a flat JSON object.")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", LIST_SCHEDULED_TOOL)
                    .put("description", "List what is still scheduled to fire, with the id needed to cancel one.")
                    .put("parameters", params(emptyList(), JSONObject())),
            )
            .put(
                JSONObject()
                    .put("name", CANCEL_SCHEDULED_TOOL)
                    .put("description", "Cancel one scheduled action by the id $LIST_SCHEDULED_TOOL gave.")
                    .put(
                        "parameters",
                        params(listOf("id"), JSONObject().put("id", string("The scheduled action's id."))),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", REMEMBER_TOOL)
                    .put(
                        "description",
                        "Remember one durable fact about this home or this household, so you " +
                            "still know it in the next conversation: which room \"my bedroom\" " +
                            "means, who lives here, a habit worth keeping. One short sentence. " +
                            "Only for what is stable — never a passing state, never something " +
                            "you can read from Home Assistant.",
                    )
                    .put(
                        "parameters",
                        params(listOf("fact"), JSONObject().put("fact", string("The fact, in one short sentence."))),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", FORGET_TOOL)
                    .put("description", "Forget a remembered fact, when the user says it is wrong or no longer true.")
                    .put(
                        "parameters",
                        params(listOf("fact"), JSONObject().put("fact", string("The fact to drop, as it was remembered."))),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", VACUUM_ROOM_TOOL)
                    .put(
                        "description",
                        "Send the robot vacuum to clean one room. Use this for any \"vacuum the " +
                            "kitchen\" request: the vacuum's rooms are segments it stores itself, " +
                            "so HassVacuumCleanArea cannot reach them and answers an error. For " +
                            "the whole home, use HassVacuumStart instead.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("room"),
                            JSONObject()
                                .put("room", string("Room as the user said it, e.g. \"cuisine\"."))
                                .put("vacuum", string("Which vacuum, when the home has several.")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", GET_STATE_TOOL)
                    .put("description", "Read the current state and attributes of one entity.")
                    .put(
                        "parameters",
                        params(
                            listOf("entity_id"),
                            JSONObject().put("entity_id", string("Full entity id, e.g. light.kitchen.")),
                        ),
                    ),
            )
            .put(
                JSONObject()
                    .put("name", CALL_SERVICE_TOOL)
                    .put(
                        "description",
                        "Call a Home Assistant service directly. The escape hatch for what " +
                            "$INTENT_TOOL cannot express (scripts, scenes, notifications, any " +
                            "custom service), e.g. domain=light service=turn_on " +
                            "entity_id=light.kitchen. Needs a real entity id, so look it up " +
                            "with $FIND_ENTITIES_TOOL first.",
                    )
                    .put(
                        "parameters",
                        params(
                            listOf("domain", "service"),
                            JSONObject()
                                .put("domain", string("Service domain, e.g. light, cover, climate, script."))
                                .put("service", string("Service name, e.g. turn_on, turn_off, set_temperature."))
                                .put("entity_id", string("Target entity id. Comma-separate several."))
                                .put("data", string("Optional extra service data as a flat JSON object, e.g. {\"brightness_pct\":40}.")),
                        ),
                    ),
            )
    }
}

/** What one server frame means to the session. A frame can carry several. */
sealed interface GeminiEvent {
    object SetupComplete : GeminiEvent

    /** Raw 24 kHz PCM to play, base64 as it arrived. */
    data class Audio(val base64Pcm: String) : GeminiEvent

    /** Incremental transcript chunks: append, never replace. */
    data class UserTranscript(val text: String) : GeminiEvent
    data class BotTranscript(val text: String) : GeminiEvent

    /**
     * The model's turn is over. The end of the *audio*, and the only safe moment to consider the
     * assistant done — [GenerationComplete] fires earlier and a turn can continue past it (a
     * tool call answered mid-turn is followed by more speech).
     */
    object TurnComplete : GeminiEvent

    /** The model finished generating this piece of output. Not the end of the turn. */
    object GenerationComplete : GeminiEvent

    /** The user spoke over the model: whatever is queued for playback is now stale. */
    object Interrupted : GeminiEvent

    data class ToolCall(val id: String?, val name: String, val args: JSONObject) : GeminiEvent

    /**
     * The user cut the model off while these calls were in flight. Their results are no longer
     * wanted and must not be sent: the model has moved on and an unsolicited response for a
     * cancelled call is at best ignored, at worst answered out of context.
     */
    data class ToolCallCancellation(val ids: List<String>) : GeminiEvent

    /** A handle that can resume this conversation on a later connection (valid ~2 hours). */
    data class ResumptionHandle(val handle: String) : GeminiEvent

    /** The server is about to close this session (idle, or quota). */
    data class GoAway(val reason: String) : GeminiEvent

    data class Error(val message: String) : GeminiEvent
}

/**
 * Turns one server frame into events. Unknown frames yield an empty list rather than an error:
 * the Live API adds fields faster than a wall panel gets updated, and a frame we do not
 * understand is not a reason to drop a live conversation.
 */
fun parseGeminiMessage(raw: String): List<GeminiEvent> {
    val root = runCatching { JSONObject(raw) }.getOrNull()
        ?: return listOf(GeminiEvent.Error("unparseable frame"))
    val events = mutableListOf<GeminiEvent>()

    if (root.has("setupComplete")) events += GeminiEvent.SetupComplete

    root.optJSONObject("serverContent")?.let { content ->
        content.optJSONObject("inputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }?.let { events += GeminiEvent.UserTranscript(it) }
        content.optJSONObject("outputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }?.let { events += GeminiEvent.BotTranscript(it) }

        val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
        for (i in 0 until (parts?.length() ?: 0)) {
            val inline = parts?.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
            inline.optString("data").takeIf { it.isNotEmpty() }
                ?.let { events += GeminiEvent.Audio(it) }
        }

        // Interruption is checked before turnComplete: a frame can carry both, and the playback
        // queue must be flushed before the turn is declared over.
        if (content.optBoolean("interrupted")) events += GeminiEvent.Interrupted
        // Order matters as much as the distinction: generationComplete arrives first and must
        // not be mistaken for the turn's end, or the panel reopens the microphone while the
        // assistant is still mid-answer.
        if (content.optBoolean("generationComplete")) events += GeminiEvent.GenerationComplete
        if (content.optBoolean("turnComplete")) events += GeminiEvent.TurnComplete
    }

    root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val name = call.optString("name").takeIf { it.isNotEmpty() } ?: continue
            events += GeminiEvent.ToolCall(
                id = call.optString("id").takeIf { it.isNotEmpty() },
                name = name,
                args = call.optJSONObject("args") ?: JSONObject(),
            )
        }
    }

    root.optJSONObject("toolCallCancellation")?.optJSONArray("ids")?.let { ids ->
        val cancelled = (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotEmpty) }
        if (cancelled.isNotEmpty()) events += GeminiEvent.ToolCallCancellation(cancelled)
    }
    root.optJSONObject("sessionResumptionUpdate")
        ?.takeIf { it.optBoolean("resumable") }
        ?.optString("newHandle")
        ?.takeIf { it.isNotEmpty() }
        ?.let { events += GeminiEvent.ResumptionHandle(it) }

    root.optJSONObject("goAway")?.let {
        events += GeminiEvent.GoAway(it.optString("timeLeft").ifEmpty { "server closing" })
    }
    root.optJSONObject("error")?.let {
        events += GeminiEvent.Error(it.optString("message").ifEmpty { "server error" })
    }

    return events
}
