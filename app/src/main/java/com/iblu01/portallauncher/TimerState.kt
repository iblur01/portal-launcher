package com.iblu01.portallauncher

import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

data class TimerState(
    val running: Boolean,
    val paused: Boolean,
    val remainingSeconds: Long,
    val durationSeconds: Long,
) {
    val progress: Float
        get() = if (durationSeconds <= 0L) 0f
        else (remainingSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
}

fun timerStateOf(entity: HaEntity, nowMs: Long = System.currentTimeMillis()): TimerState {
    val state = entity.state.trim().lowercase()
    val duration = parseTimerDuration(entity.attributes.optString("duration"))
    val attributeRemaining = parseTimerDuration(entity.attributes.optString("remaining"))
    val finishMs = parseTimerInstant(entity.attributes.optString("finishes_at"))?.toEpochMilli()
    val running = state == "active"
    val remaining = when {
        running && finishMs != null -> ceil((finishMs - nowMs).coerceAtLeast(0L) / 1_000.0).toLong()
        attributeRemaining != null -> attributeRemaining
        else -> 0L
    }
    return TimerState(
        running = running,
        paused = state == "paused",
        remainingSeconds = remaining,
        durationSeconds = (duration ?: remaining).coerceAtLeast(remaining),
    )
}

fun parseTimerDuration(value: String): Long? {
    val parts = value.trim().split(':')
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toLongOrNull() ?: return null }
    return when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        else -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
    }.takeIf { it >= 0L }
}

fun formatTimerDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val secs = safe % 60
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%02d:%02d".format(minutes, secs)
}

private fun parseTimerInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrElse {
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    }
