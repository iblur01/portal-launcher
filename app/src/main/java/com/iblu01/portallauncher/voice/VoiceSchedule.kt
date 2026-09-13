package com.iblu01.portallauncher.voice

import org.json.JSONArray
import org.json.JSONObject

/**
 * One action the assistant promised to do later ("in ten minutes, turn the lounge off").
 *
 * What is stored is the *tool call itself* — the same name and arguments [VoiceTools] executes
 * live. Nothing else has to be modelled: an intent, a service call and anything added later are
 * all replayed through the same path, so a scheduled action can never drift from what the
 * assistant would have done immediately.
 */
data class ScheduledAction(
    val id: String,
    val atMs: Long,
    /** What to say it is, for the screen and for the assistant reading its own list back. */
    val title: String,
    val tool: String,
    val args: JSONObject,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("at", atMs)
        .put("title", title)
        .put("tool", tool)
        .put("args", args)

    companion object {
        fun fromJson(json: JSONObject): ScheduledAction? {
            val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
            val tool = json.optString("tool").takeIf { it.isNotEmpty() } ?: return null
            val at = json.optLong("at").takeIf { it > 0L } ?: return null
            return ScheduledAction(
                id = id,
                atMs = at,
                title = json.optString("title"),
                tool = tool,
                args = json.optJSONObject("args") ?: JSONObject(),
            )
        }
    }
}

/** Codec for the persisted queue. Unreadable entries are dropped, never fatal. */
fun encodeScheduledActions(actions: List<ScheduledAction>): String =
    JSONArray().apply { actions.forEach { put(it.toJson()) } }.toString()

fun decodeScheduledActions(raw: String): List<ScheduledAction> {
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(ScheduledAction::fromJson)
    }
}

/**
 * A promise that far out is not a promise a voice assistant should keep: the house has moved on,
 * and nobody remembers asking. Anything longer belongs in a Home Assistant automation.
 */
const val MAX_SCHEDULE_DELAY_MINUTES = 24 * 60

/** Never more than this many pending: the queue is a convenience, not a task manager. */
const val MAX_SCHEDULED_ACTIONS = 10

/** One entry of the tool-call journal shown in the voice settings page. */
data class VoiceToolCall(
    val at: Long,
    val name: String,
    val args: String,
    val result: String,
    val ok: Boolean,
)

/** Enough to see what went wrong in a session, short enough to stay in memory for free. */
const val MAX_TOOL_CALL_LOG = 20
