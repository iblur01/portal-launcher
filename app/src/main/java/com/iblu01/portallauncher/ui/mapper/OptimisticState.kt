package com.iblu01.portallauncher.ui.mapper

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.friendlyEntityState
import java.util.Locale

/**
 * Predicted state for a service call, or `null` when not predictable — HA then stays in charge
 * rather than displaying an invented value.
 *
 * Covers return the **transient** state: HA does emit `opening`/`closing` before `open`/`closed`,
 * so that is the honest prediction, and it gets confirmed without a visual jump.
 */
internal fun predictState(service: String, current: String): String? = when (service) {
    "turn_on" -> "on"
    "turn_off" -> "off"
    "toggle" -> if (current == "on") "off" else "on"
    "media_play_pause" -> if (current in PLAYING_STATES) "paused" else "playing"
    "media_play" -> "playing"
    "media_pause" -> "paused"
    "lock" -> "locking"
    "unlock" -> "unlocking"
    "open_cover" -> "opening"
    "close_cover" -> "closing"
    else -> null   // volume, colour, setpoint… : no state prediction
}

private val PLAYING_STATES = setOf("playing", "buffering")

/**
 * Device states rendered as visually "active" — mirrors the calm-chip policy in
 * `PillCatalogBuilder.calmDeviceChip` (kept separate to avoid a cross-layer dependency).
 */
private val ACTIVE_DEVICE_STATES = setOf(
    "on", "open", "opening", "unlocked", "playing", "buffering", "running", "cleaning",
    "washing", "drying", "mowing", "heat", "cool", "heat_cool", "auto",
)

/**
 * Overlays the live per-entity state onto a pipeline-built chip. The snapshot pipeline is sampled
 * and transformed off-main, so the chip model lags the store by up to a few frames; during that
 * window (an optimistic write, or a push already applied to `HaStates`) the live entity is the
 * fresher truth. Alert states computed by the pipeline ("critical"/"warning") are preserved.
 */
internal fun LauncherChip.withLiveState(live: HaEntity?): LauncherChip {
    val device = deviceState ?: return this
    if (live == null) return this
    val liveState = live.state.lowercase(Locale.ROOT)
    if (liveState == device.lowercase(Locale.ROOT)) return this
    return copy(
        deviceState = liveState,
        state = when {
            state in setOf("critical", "warning") -> state
            liveState in ACTIVE_DEVICE_STATES -> "active"
            else -> "ok"
        },
        value = friendlyEntityState(live),
    )
}
