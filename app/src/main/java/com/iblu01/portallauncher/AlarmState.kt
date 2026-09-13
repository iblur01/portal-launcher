package com.iblu01.portallauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * What an alarm panel is doing, and what this panel should do about it.
 *
 * The rendering choice lives here rather than in Home Assistant: whether a state deserves the whole
 * screen is a property of the state itself, not of whoever wrote the automation. Home Assistant
 * sends what happened; the panel decides how loudly to say it.
 */
enum class AlarmPhase(
    /** The screen goes black and the keypad takes over. */
    val fullScreen: Boolean,
    /** A tap cannot make it go away. */
    val locked: Boolean,
    val level: AlertLevel,
    val tone: String?,
    /** Chime on every second of the countdown. */
    val tick: String?,
    /** Repeat [tone] this often, in milliseconds. */
    val repeatMs: Long?,
    /** Milliseconds on screen for the states that are just news. Null means it stands. */
    val durationMs: Long?,
    /** Accent override. Amber is the notification default, which suits none of the settled states. */
    val color: String?,
) {
    /**
     * Exit delay. Deliberately *not* full screen: you are walking out, there is nothing to type,
     * and blanking the panel of a home someone is still moving through helps nobody.
     */
    ARMING(false, false, AlertLevel.WARNING, null, "beep", null, null, null),

    /** Entry delay. Full screen: a code has to be typed, and quickly. */
    PENDING(true, true, AlertLevel.CRITICAL, null, "beep", null, null, null),

    /** Someone is in. Full screen, siren, and only the code takes it down. */
    TRIGGERED(true, true, AlertLevel.CRITICAL, "siren", null, 3_000L, null, null),

    /** Armed. News, not an incident. */
    ARMED(false, false, AlertLevel.INFO, "success", null, null, 6_000L, "#30D158"),

    /** Disarmed. Clears whatever the alarm had on screen, then says so briefly. */
    DISARMED(false, false, AlertLevel.INFO, "success", null, null, 5_000L, "#30D158"),

    /** Home Assistant refused to arm — an open sensor, usually. Worth reading, not worth locking. */
    FAILED(false, false, AlertLevel.WARNING, "error", null, null, 20_000L, null),

    /** Nothing to show: the panel is idle. */
    NONE(false, false, AlertLevel.INFO, null, null, null, null, null);

    companion object {
        fun parse(raw: String?): AlarmPhase = when (raw?.trim()?.lowercase()) {
            "arming" -> ARMING
            "pending" -> PENDING
            "triggered" -> TRIGGERED
            "armed", "armed_away", "armed_home", "armed_night", "armed_vacation",
            "armed_custom_bypass" -> ARMED
            "disarmed" -> DISARMED
            "failed", "failed_to_arm" -> FAILED
            else -> NONE
        }
    }
}

/**
 * A message on `portal/<deviceId>/alarm`.
 *
 * Only [state] is required — everything else refines what the panel would say on its own, so an
 * automation can be a single `mqtt.publish` of the alarm's state and still get the full treatment.
 */
data class AlarmState(
    val phase: AlarmPhase,
    val entity: String? = null,
    val message: String? = null,
    val title: String? = null,
    val mode: String? = null,
    /** Countdown in milliseconds, for the two delays. */
    val delayMs: Long? = null,
    /** When the delay ends, if Home Assistant knows. Beats [delayMs]. */
    val untilEpochMs: Long? = null,
    /** Sensors worth naming: the ones still open, the one that tripped. */
    val sensors: List<AlertItem> = emptyList(),
    /** What to say. Null falls back to the phrase the panel has for this state. */
    val speak: String? = null,
    /** Say nothing at all, whatever the state would normally announce. */
    val silent: Boolean = false,
    val engine: String? = null,
    val language: String? = null,
) {
    /**
     * The notification this state becomes. Reusing [AlertPayload] is what keeps one renderer for
     * both topics — the alarm decides *what* to show, the overlay still knows *how*.
     */
    fun toAlert(context: Context): AlertPayload? {
        if (phase == AlarmPhase.NONE) return null
        return AlertPayload(
            message = message ?: defaultMessage(context),
            title = title ?: context.getString(R.string.alarm_default_title),
            icon = icon(),
            tts = (speak ?: defaultSpeak(context)).takeUnless { silent },
            engine = engine,
            language = language,
            tone = phase.tone,
            level = phase.level,
            color = phase.color,
            durationMs = phase.durationMs,
            countdownMs = delayMs.takeIf { phase.counts },
            untilEpochMs = untilEpochMs.takeIf { phase.counts },
            // Zero is not a verdict: the next state is. A countdown that runs out here goes quiet
            // and waits to be told what happened.
            endTone = "none",
            tick = phase.tick,
            repeatMs = phase.repeatMs,
            items = sensors,
            keypad = entity.takeIf { phase.fullScreen },
            blackout = phase.fullScreen,
            dismissible = !phase.locked,
            badge = mode.takeIf { phase == AlarmPhase.ARMING || phase == AlarmPhase.ARMED },
        )
    }

    /**
     * What the panel says out loud when Home Assistant does not dictate a phrase.
     *
     * Spoken text is not the written message: the screen can afford a dash and a mode name, a
     * sentence read across a room cannot.
     */
    private fun defaultSpeak(context: Context): String? = when (phase) {
        AlarmPhase.ARMING -> context.byMode(R.string.alarm_say_arming, R.string.alarm_say_arming_mode)
        AlarmPhase.PENDING -> context.getString(R.string.alarm_say_pending)
        AlarmPhase.TRIGGERED -> context.getString(R.string.alarm_say_triggered)
        AlarmPhase.ARMED -> context.byMode(R.string.alarm_say_armed, R.string.alarm_say_armed_mode)
        AlarmPhase.DISARMED -> context.getString(R.string.alarm_say_disarmed)
        AlarmPhase.FAILED -> context.bySensor(R.string.alarm_say_failed, R.string.alarm_say_failed_sensor)
        AlarmPhase.NONE -> null
    }

    private fun defaultMessage(context: Context): String = when (phase) {
        AlarmPhase.ARMING -> context.byMode(R.string.alarm_msg_arming, R.string.alarm_msg_arming_mode)
        AlarmPhase.PENDING -> context.getString(R.string.alarm_msg_pending)
        AlarmPhase.TRIGGERED -> context.getString(R.string.alarm_msg_triggered)
        AlarmPhase.ARMED -> context.byMode(R.string.alarm_msg_armed, R.string.alarm_msg_armed_mode)
        AlarmPhase.DISARMED -> context.getString(R.string.alarm_msg_disarmed)
        AlarmPhase.FAILED -> context.bySensor(R.string.alarm_msg_failed, R.string.alarm_msg_failed_sensor)
        AlarmPhase.NONE -> ""
    }

    /** The wording with the arming mode named, when there is one to name. */
    private fun Context.byMode(plain: Int, withMode: Int): String =
        mode?.let { getString(withMode, it) } ?: getString(plain)

    private fun Context.bySensor(plain: Int, withSensor: Int): String =
        sensors.firstOrNull()?.let { getString(withSensor, it.text) } ?: getString(plain)

    private fun icon(): String = when (phase) {
        AlarmPhase.ARMING -> "mdi:shield-lock-outline"
        AlarmPhase.PENDING -> "mdi:shield-alert-outline"
        AlarmPhase.TRIGGERED -> "mdi:shield-alert"
        AlarmPhase.ARMED -> "mdi:shield-check"
        AlarmPhase.DISARMED -> "mdi:shield-off-outline"
        AlarmPhase.FAILED -> "mdi:door-open"
        AlarmPhase.NONE -> "mdi:shield"
    }

    companion object {
        private const val DEFAULT_TITLE = "Alarme"

        /**
         * Parses an alarm message. A bare state name works (`triggered`), as does the full object.
         * Returns null for anything that names no state — including the empty payload that clears
         * a retained topic.
         */
        fun parse(raw: String): AlarmState? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("{")) {
                return AlarmPhase.parse(trimmed).takeIf { it != AlarmPhase.NONE }?.let { AlarmState(it) }
            }
            val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
            val phase = AlarmPhase.parse(json.optString("state"))
            if (phase == AlarmPhase.NONE) return null
            return AlarmState(
                phase = phase,
                entity = json.str("entity"),
                message = json.str("message"),
                title = json.str("title"),
                mode = json.str("mode"),
                delayMs = json.optLong("delay", -1L).takeIf { it > 0 }?.times(1000L)
                    ?: AlertPayload.duration(json.str("delay")),
                untilEpochMs = AlertPayload.instant(json.str("until")),
                sensors = sensors(json.optJSONArray("sensors")),
                speak = json.str("speak"),
                // `"speak": false` is the natural way to ask for silence, and lands here rather
                // than as a phrase to read out.
                silent = !json.optBoolean("speak", true) || json.optBoolean("silent", false),
                engine = json.str("engine"),
                language = json.str("language"),
            )
        }

        /** Sensor names, bare or with their own icon and verdict. */
        private fun sensors(array: JSONArray?): List<AlertItem> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                when (val entry = array.opt(i)) {
                    is JSONObject -> entry.str("text")?.let {
                        AlertItem(it, entry.str("icon"), entry.str("note"))
                    }
                    is String -> entry.trim().takeIf { it.isNotEmpty() }?.let { AlertItem(it) }
                    else -> null
                }
            }
        }

        private fun JSONObject.str(key: String): String? =
            optString(key).trim().takeIf { it.isNotEmpty() }
    }
}

/** The two delays are the only states that count down. */
private val AlarmPhase.counts: Boolean
    get() = this == AlarmPhase.ARMING || this == AlarmPhase.PENDING
