package com.iblu01.portallauncher.ui.panel

import com.iblu01.portallauncher.ui.model.PanelKind

/**
 * Side-panel state machine (design §3). Pure, unit-tested; the VM holds a `StateFlow<PanelState>`
 * and applies [reduce] on each [PanelEvent]. Precedence is explicit here (not last-write-wins),
 * so a chip panel no longer closes itself when its chip leaves the tray — closing is a user
 * [PanelEvent.Dismiss] only.
 */

/** What the panel is showing. `key` is the stable identity used for toggle-close + auto-open guards. */
sealed interface PanelRequest {
    val key: String
    data class Chip(override val key: String, val panelKind: PanelKind) : PanelRequest
    data class Media(override val key: String) : PanelRequest
    data object Weather : PanelRequest {
        override val key: String get() = "weather"
    }
}

/**
 * Who opened the panel. Precedence is AUTO < USER < ALERT: an AUTO (media) open must never clobber
 * a USER intent, and an ALERT (alarm entry delay / triggered) outranks both — the disarm keypad has
 * to be on screen before the siren fires, whatever the user was doing.
 */
enum class PanelSource { USER, AUTO, ALERT }

/**
 * @param dismissedAutoKey the media key the user dismissed; auto-open is suppressed for it until
 *   the session stops ([PanelEvent.MediaStopped] rearms). Mirrors the old `mediaPlayerDismissed`.
 * @param dismissedAlertKey the alarm key the user dismissed; the alert panel stays closed for it
 *   until the alarm leaves its alerting state ([PanelEvent.AlarmCleared] rearms). Without this a
 *   dismiss would be undone on the next state push.
 */
data class PanelState(
    val request: PanelRequest? = null,
    val source: PanelSource = PanelSource.USER,
    val dismissedAutoKey: String? = null,
    val dismissedAlertKey: String? = null,
)

/**
 * Events reaching the reducer. A chip tap that only calls a service (`ServiceToggle`) never
 * reaches here — the VM invokes the service directly (design §4); only panel-opening taps do.
 * Hence [OpenChip] == "ChipTap on OpenPanel". [LongPressChip] always opens (no toggle-close).
 */
sealed interface PanelEvent {
    data class OpenChip(val request: PanelRequest.Chip) : PanelEvent
    data class LongPressChip(val request: PanelRequest.Chip) : PanelEvent
    data object WeatherTap : PanelEvent
    data class MediaAutoOpen(val key: String) : PanelEvent
    data object MediaStopped : PanelEvent

    /** The alarm entered `pending` (entry delay) or `triggered`: force its keypad panel up. */
    data class AlarmAlert(val request: PanelRequest.Chip) : PanelEvent

    /** The alarm left its alerting states (disarmed, or armed again): drop the forced panel. */
    data object AlarmCleared : PanelEvent
    data object Dismiss : PanelEvent
}

fun reduce(state: PanelState, event: PanelEvent): PanelState = when (event) {
    // User tap that opens: same key toggles closed, otherwise replaces (weather-vs-chip: last wins).
    is PanelEvent.OpenChip ->
        if (state.request?.key == event.request.key) state.copy(request = null, source = PanelSource.USER)
        else state.copy(request = event.request, source = PanelSource.USER)

    // Long-press always opens the control panel; no toggle-close.
    is PanelEvent.LongPressChip ->
        state.copy(request = event.request, source = PanelSource.USER)

    // Weather is a user intent; toggle-close on repeat.
    PanelEvent.WeatherTap ->
        if (state.request is PanelRequest.Weather) state.copy(request = null, source = PanelSource.USER)
        else state.copy(request = PanelRequest.Weather, source = PanelSource.USER)

    // Auto-open when nothing is shown, OR follow the primary when an AUTO media panel is already
    // up (primary A→B swap must retarget, not stay stale) — but never clobber a USER panel, and
    // never reopen a user-dismissed session while it keeps playing.
    is PanelEvent.MediaAutoOpen -> {
        val canAutoOpen = state.request == null ||
            (state.request is PanelRequest.Media && state.source == PanelSource.AUTO)
        if (canAutoOpen && event.key != state.dismissedAutoKey)
            state.copy(request = PanelRequest.Media(event.key), source = PanelSource.AUTO)
        else state
    }

    // Session ended: close if we were showing media, and rearm auto-open (clear the dismiss guard).
    // The alert guard is carried over — it belongs to the alarm episode, not to the media session.
    PanelEvent.MediaStopped ->
        if (state.request is PanelRequest.Media) PanelState(dismissedAlertKey = state.dismissedAlertKey)
        else state.copy(dismissedAutoKey = null)

    // Alarm alerting: opens over anything, including a USER panel — this is the one case where the
    // user's current panel is overridden, because the entry delay is running out. Only an explicit
    // dismissal of this very alarm holds it back.
    is PanelEvent.AlarmAlert ->
        if (event.request.key == state.dismissedAlertKey) state
        else state.copy(request = event.request, source = PanelSource.ALERT)

    // Alarm back to a calm state: close the forced panel and rearm the alert for the next episode.
    PanelEvent.AlarmCleared ->
        if (state.source == PanelSource.ALERT) PanelState(dismissedAutoKey = state.dismissedAutoKey)
        else state.copy(dismissedAlertKey = null)

    // Dismiss: remember a dismissed media/alert key so it won't reopen while the cause persists.
    PanelEvent.Dismiss -> {
        val req = state.request
        when {
            req is PanelRequest.Media -> state.copy(request = null, dismissedAutoKey = req.key)
            req != null && state.source == PanelSource.ALERT ->
                state.copy(request = null, source = PanelSource.USER, dismissedAlertKey = req.key)
            else -> state.copy(request = null)
        }
    }
}
