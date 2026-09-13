package com.iblu01.portallauncher

import org.json.JSONArray
import org.json.JSONObject

/** How loud a notification means to be: picks its colour and how long it stands. */
enum class AlertLevel(val defaultDurationMs: Long?) {
    INFO(5_000L),
    WARNING(8_000L),

    /** Stands until someone taps it — that is what makes it critical. */
    CRITICAL(null);

    companion object {
        fun parse(raw: String?): AlertLevel = when (raw?.trim()?.lowercase()) {
            "warning", "warn" -> WARNING
            "critical", "error", "alarm" -> CRITICAL
            else -> INFO
        }
    }
}

/**
 * One line of detail under a notification's message: the sensor that is still open, the device
 * that did not answer, the item that was skipped.
 *
 * [note] is the verdict on that line — "ignoré", "hors ligne" — drawn in the notification's accent
 * so the reason reads at a glance without lengthening the message itself.
 */
data class AlertItem(
    val text: String,
    val icon: String? = null,
    val note: String? = null,
)

/**
 * A notification pushed on `portal/<deviceId>/notification`.
 *
 * The audio is always rendered by Home Assistant. Either it hands over a finished clip in [audio],
 * or it hands over the words in [tts] and the panel asks Home Assistant — with its own credentials
 * — for the URL of the clip. Nothing is ever synthesised on the panel.
 */
data class AlertPayload(
    val message: String,
    val title: String? = null,
    val icon: String? = null,
    val audio: String? = null,
    val tts: String? = null,
    val engine: String? = null,
    val language: String? = null,
    val tone: String? = null,
    val level: AlertLevel = AlertLevel.INFO,
    val color: String? = null,
    val durationMs: Long? = null,
    /** Seconds to count down from the moment the payload lands, when it carries a timer. */
    val countdownMs: Long? = null,
    /** The instant the timer runs out, when Home Assistant knows it (a `timer.*` finishes_at). */
    val untilEpochMs: Long? = null,
    /** Replaces [message] once the timer runs out. */
    val endMessage: String? = null,
    /** Chime at zero. Null falls back to [DEFAULT_END_TONE]; "none" rings nothing. */
    val endTone: String? = null,
    /** Chime on every second of the countdown — an alarm panel's exit/entry beeps. */
    val tick: String? = null,
    /** Seconds left below which [tick] doubles its rate. */
    val tickUrgentAtMs: Long = 10_000L,
    /** Repeat [tone] this often for as long as the notification stands — a siren, in practice. */
    val repeatMs: Long? = null,
    /** Short word beside the title: `IGNORÉ`, `BYPASS`, `HORS LIGNE`. */
    val badge: String? = null,
    /** Detail lines under the message. */
    val items: List<AlertItem> = emptyList(),
    /**
     * The `alarm_control_panel` to disarm, drawn as a full-screen keypad — what a real alarm panel
     * shows when it is counting down or howling.
     */
    val keypad: String? = null,
    /** Hide the wallpaper behind a black ground: nothing but the alarm is worth looking at. */
    val blackout: Boolean = false,
    /**
     * Whether a tap takes the notification down. False for an alarm: silencing a siren must go
     * through the code, not through whoever reaches the panel first.
     */
    val dismissible: Boolean = true,
    val wake: Boolean = true,
) {
    /**
     * [audio] if it is safe to fetch, null otherwise.
     *
     * Anyone able to publish on the broker can put a URL in here, so only the configured Home
     * Assistant host is ever fetched: an alert must not be able to turn the panel into an
     * arbitrary HTTP client.
     */
    fun playableAudio(haUrl: String): String? {
        val url = audio?.trim().orEmpty()
        if (url.isEmpty()) return null
        val target = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (target.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = target.host ?: return null
        val allowed = runCatching { java.net.URI(haUrl.trim()) }.getOrNull()?.host ?: return null
        return url.takeIf { host.equals(allowed, ignoreCase = true) }
    }

    /** True when this notification is a timer: it stands and counts instead of fading out. */
    val isTimer: Boolean get() = countdownMs != null || untilEpochMs != null

    /**
     * When the timer runs out, in `System.currentTimeMillis()` terms, for a payload that landed at
     * [receivedAtMs]. Null when there is no timer.
     *
     * An absolute [untilEpochMs] wins: Home Assistant knows when its own timer ends, where a
     * duration only knows how long ago the panel heard about it.
     */
    fun endsAt(receivedAtMs: Long): Long? = untilEpochMs ?: countdownMs?.let { receivedAtMs + it }

    /**
     * [color] as an opaque ARGB int, or null when the sender picked none (or wrote nonsense) and
     * the level's own colour should stand.
     *
     * Accepts `#RGB`, `#RRGGBB` and `#AARRGGBB`, with or without the `#`.
     */
    fun accentArgb(): Int? {
        val hex = color?.trim()?.removePrefix("#")?.lowercase() ?: return null
        if (hex.any { it !in "0123456789abcdef" }) return null
        val rgb = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        val value = rgb.toLongOrNull(16) ?: return null
        return if (rgb.length == 8) value.toInt() else (0xFF000000L or value).toInt()
    }

    companion object {
        /** What a notification carrying no sound of its own falls back to. */
        const val DEFAULT_TONE = "alert"

        /** A timer that reaches zero rings, unless the payload says otherwise. */
        const val DEFAULT_END_TONE = "alert"

        /**
         * True for the payload that takes down whatever notification stands — `{"dismiss": true}`,
         * or the bare word.
         *
         * An alarm panel needs this more than anything else: disarming during the entry delay has
         * to clear the countdown at once, and no timer or tap can be relied on to do it.
         */
        fun isDismiss(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.lowercase() in setOf("dismiss", "clear", "cancel")) return true
            if (!trimmed.startsWith("{")) return false
            val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return false
            return json.optBoolean("dismiss", false)
        }

        /**
         * Parses a notification payload, JSON or not.
         *
         * Anything that does not open with `{` is the plain message the topic accepted before this
         * schema existed, and keeps behaving exactly as it did. Returns null for a payload with
         * nothing to show and nothing to play.
         */
        fun parse(raw: String): AlertPayload? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("{")) {
                return AlertPayload(message = trimmed, tone = DEFAULT_TONE)
            }
            val json = runCatching { JSONObject(trimmed) }.getOrNull()
                ?: return AlertPayload(message = trimmed, tone = DEFAULT_TONE)

            val message = json.str("message").orEmpty()
            val audio = json.str("audio")
            val tts = json.str("tts")
            val keypad = json.str("keypad")
            if (message.isEmpty() && audio == null && tts == null && keypad == null) return null

            return AlertPayload(
                message = message,
                title = json.str("title"),
                icon = json.str("icon"),
                audio = audio,
                tts = tts,
                engine = json.str("engine"),
                language = json.str("language"),
                // A payload that speaks stays silent otherwise: the two would talk over each other.
                // The tone is still read, as it is what plays if the speech cannot be fetched.
                tone = json.str("tone") ?: DEFAULT_TONE.takeIf { audio == null && tts == null },
                level = AlertLevel.parse(json.str("level")),
                color = json.str("color"),
                durationMs = json.optLong("duration", -1L).takeIf { it > 0 },
                countdownMs = duration(json.str("countdown")),
                untilEpochMs = instant(json.str("until")),
                endMessage = json.str("end_message"),
                endTone = json.str("end_tone"),
                tick = json.str("tick"),
                tickUrgentAtMs = duration(json.str("tick_urgent_at")) ?: 10_000L,
                repeatMs = duration(json.str("repeat")),
                badge = json.str("badge"),
                items = items(json.optJSONArray("items")),
                keypad = keypad,
                // A keypad implies the whole alarm treatment; either half can still be asked for
                // on its own.
                blackout = json.optBoolean("blackout", keypad != null),
                dismissible = json.optBoolean("dismissible", keypad == null),
                wake = json.optBoolean("wake", true),
            )
        }

        /**
         * Detail lines, written either as bare strings or as objects carrying their own icon and
         * verdict. A malformed entry is dropped rather than failing the whole notification.
         */
        private fun items(array: JSONArray?): List<AlertItem> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                when (val entry = array.opt(i)) {
                    is JSONObject -> entry.str("text")?.let {
                        AlertItem(text = it, icon = entry.str("icon"), note = entry.str("note"))
                    }
                    is String -> entry.trim().takeIf { it.isNotEmpty() }?.let { AlertItem(text = it) }
                    else -> null
                }
            }
        }

        private fun JSONObject.str(key: String): String? =
            optString(key).trim().takeIf { it.isNotEmpty() }

        /**
         * A duration in milliseconds, written the way whoever sent it found natural: `90` seconds,
         * `"1:30"`, `"00:10:00"`, or `"5m"` / `"90s"` / `"1h30m"`.
         */
        @JvmStatic
        internal fun duration(raw: String?): Long? {
            val value = raw?.trim()?.lowercase() ?: return null
            if (value.isEmpty()) return null

            value.toDoubleOrNull()?.let { return (it * 1000).toLong().takeIf { ms -> ms > 0 } }

            if (':' in value) {
                val parts = value.split(':').map { it.trim().toLongOrNull() ?: return null }
                val seconds = when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> return null
                }
                return (seconds * 1000).takeIf { it > 0 }
            }

            // 1h30m, 5m, 90s — every unit optional, in that order.
            val match = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").find(value) ?: return null
            val (h, m, sec) = match.destructured
            if (h.isEmpty() && m.isEmpty() && sec.isEmpty()) return null
            val total = h.toLongOrNull().orZero() * 3600 +
                m.toLongOrNull().orZero() * 60 +
                sec.toLongOrNull().orZero()
            return (total * 1000).takeIf { it > 0 }
        }

        /** An ISO-8601 instant (what a Home Assistant `timer` puts in `finishes_at`). */
        @JvmStatic
        internal fun instant(raw: String?): Long? {
            val value = raw?.trim() ?: return null
            if (value.isEmpty()) return null
            return runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
                .recoverCatching {
                    // Home Assistant also writes "2026-09-05 22:30:00+00:00" in places.
                    java.time.OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli()
                }
                .getOrNull()
        }

        private fun Long?.orZero() = this ?: 0L
    }
}
