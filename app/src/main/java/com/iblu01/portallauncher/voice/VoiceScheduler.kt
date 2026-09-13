package com.iblu01.portallauncher.voice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iblu01.portallauncher.HaApiClient
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.VoiceScheduleReceiver
import org.json.JSONObject

/**
 * Fires the actions the assistant promised for later.
 *
 * One `AlarmManager` alarm for the earliest entry, rearmed after every fire — not one alarm per
 * entry: exact alarms are a scarce, throttled resource on these panels, and a single "next
 * deadline" alarm is both cheaper and self-healing (a missed one is caught by the next arm, and
 * boot rearms from the persisted queue).
 *
 * The queue lives in [Prefs] because an action promised out loud must survive the launcher being
 * restarted, which on a kiosk happens for reasons nobody asked for.
 */
object VoiceScheduler {
    const val ACTION_FIRE = "com.iblu01.portallauncher.VOICE_SCHEDULE_FIRE"
    private const val TAG = "VoiceScheduler"
    private const val RC_FIRE = 4711

    /** Fired actions are announced through this, so the assistant can speak the outcome. */
    @Volatile
    var onFired: ((ScheduledAction, Map<String, Any?>) -> Unit)? = null

    fun pending(prefs: Prefs): List<ScheduledAction> =
        decodeScheduledActions(prefs.voiceScheduledActions).sortedBy { it.atMs }

    /**
     * Adds one action. Returns null when the queue is full: refusing is honest, and silently
     * dropping the eleventh promise is the kind of bug nobody finds until it matters.
     */
    fun add(context: Context, prefs: Prefs, action: ScheduledAction): ScheduledAction? {
        val queue = pending(prefs)
        if (queue.size >= MAX_SCHEDULED_ACTIONS) return null
        prefs.voiceScheduledActions = encodeScheduledActions(queue + action)
        arm(context, prefs)
        return action
    }

    fun cancel(context: Context, prefs: Prefs, id: String): Boolean {
        val queue = pending(prefs)
        val next = queue.filterNot { it.id == id }
        if (next.size == queue.size) return false
        prefs.voiceScheduledActions = encodeScheduledActions(next)
        arm(context, prefs)
        return true
    }

    /** Rearms for the earliest pending deadline, or cancels the alarm when the queue is empty. */
    fun arm(context: Context, prefs: Prefs = Prefs(context)) {
        val next = pending(prefs).minByOrNull { it.atMs }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (next == null) {
            pendingIntent(context, create = false)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            return
        }
        val pending = pendingIntent(context, create = true) ?: return
        val atMs = maxOf(next.atMs, System.currentTimeMillis() + 1_000L)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }.onFailure {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }
        Log.i(TAG, "next scheduled action '${next.title}' in ${(atMs - System.currentTimeMillis()) / 1000}s")
    }

    /**
     * Runs everything that is due, then rearms. Blocking HTTP, so callers must be off the main
     * thread ([VoiceScheduleReceiver] uses `goAsync`).
     *
     * Due actions are replayed through [VoiceTools] with the tool name and arguments the model
     * originally gave, which is what keeps a promised action identical to an immediate one.
     */
    fun runDue(context: Context) {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val queue = pending(prefs)
        val (due, later) = queue.partition { it.atMs <= now }
        if (due.isEmpty()) {
            arm(context, prefs)
            return
        }
        prefs.voiceScheduledActions = encodeScheduledActions(later)

        val tools = VoiceTools(HaApiClient(prefs.haUrl, prefs.haToken))
        for (action in due) {
            val result = runCatching { tools.execute(action.tool, action.args) }
                .getOrElse { mapOf<String, Any?>("error" to (it.message ?: "failed")) }
            Log.i(TAG, "fired '${action.title}' -> $result")
            onFired?.invoke(action, result)
        }
        arm(context, prefs)
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            RC_FIRE,
            Intent(context, VoiceScheduleReceiver::class.java).setAction(ACTION_FIRE),
            flags,
        )
    }

    /** Ids are only ever spoken back to the model, so short and readable beats a UUID. */
    fun newId(): String = "s${System.currentTimeMillis() % 100_000}"

    /** Builds one action from a `schedule_action` tool call, or null when the call is unusable. */
    fun actionFrom(args: JSONObject): ScheduledAction? {
        val minutes = args.optInt("delay_minutes", 0)
        if (minutes < 1 || minutes > MAX_SCHEDULE_DELAY_MINUTES) return null
        val tool = args.optString("tool").takeIf { it.isNotBlank() } ?: return null
        val inner = when (val raw = args.opt("args")) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrNull()
            else -> null
        } ?: return null
        return ScheduledAction(
            id = newId(),
            atMs = System.currentTimeMillis() + minutes * 60_000L,
            title = args.optString("title").ifBlank { tool },
            tool = tool,
            args = inner,
        )
    }
}
