package com.iblu01.portallauncher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.model.PillSnapshot
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import kotlinx.coroutines.flow.Flow
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.panel.PanelState
import com.iblu01.portallauncher.ui.panel.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable screen state (Finding 1). One object collected once via
 * `collectAsStateWithLifecycle`, replacing the ~15 `mutableStateOf` + listener callbacks.
 */
data class LauncherUiState(
    val chips: List<LauncherChip> = emptyList(),
    val temperatures: TemperatureSummary = TemperatureSummary(),
    val mediaSessions: List<PlayingMedia> = emptyList(),
    val connected: Boolean = true,
    val lastUpdateAt: Long = 0L,
    /** Raw states, for detail panels. */
    val latestStates: Map<String, HaEntity> = emptyMap(),
    /** entity_id -> area name, for the light rooms grouping. */
    val areaByEntity: Map<String, String> = emptyMap(),
)

/**
 * Alarm states that must put the disarm keypad on screen by themselves: `pending` is the entry
 * delay (the siren is about to fire) and `triggered` is the siren itself. `arming` (exit delay) is
 * deliberately excluded — the user just armed and is walking out, forcing a keypad there would be
 * in the way.
 */
private val ALERTING_ALARM_STATES = setOf("pending", "triggered")

/** The alarm chip that is currently alerting, as a panel request — null when none is. */
private fun LauncherUiState.alarmAlert(): PanelRequest.Chip? {
    val chip = chips.firstOrNull {
        it.toPanelKind() == PanelKind.ALARM &&
            latestStates[it.entityId]?.state?.lowercase() in ALERTING_ALARM_STATES
    } ?: return null
    return PanelRequest.Chip(chip.id, PanelKind.ALARM)
}

/**
 * Owns the screen state (MAD/UDF). Collects the injected snapshot `Flow` (transforms already run on
 * `Dispatchers.Default`) into a conflated `StateFlow`. Also drives the panel state machine
 * ([reduce]) via [onEvent] and resolves the panel chip last-known-good.
 *
 * NB on sharing: `uiState` is `WhileSubscribed(5000)`, but the media auto-open collector in `init`
 * subscribes for the VM's whole lifetime, so in practice the upstream stays hot while the VM lives.
 * That is intentional for this always-on kiosk launcher (media must auto-open even before the tray
 * is looked at); the source coalesces bursts via `sample`, so "hot" is cheap, not a storm.
 */
class LauncherViewModel(
    snapshots: Flow<PillSnapshot>,
    private val callServiceFn: (domain: String, service: String, entityId: String?, data: Map<String, Any>?) -> Unit,
) : ViewModel() {
    val uiState: StateFlow<LauncherUiState> = snapshots
        .map { s ->
            LauncherUiState(
                chips = s.chips,
                temperatures = s.temperatures,
                mediaSessions = s.media,
                connected = s.connected,
                lastUpdateAt = s.lastUpdateAt,
                latestStates = s.latestStates,
                areaByEntity = s.areaByEntity,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    /**
     * True while an alarm is in its entry delay or already triggered. The Activity mirrors this
     * onto the screen policy so the idle timeout cannot lock the panel away mid-countdown.
     */
    val alarmAlerting: StateFlow<Boolean> = uiState
        .map { it.alarmAlert() != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _panel = MutableStateFlow(PanelState())
    val panel: StateFlow<PanelState> = _panel.asStateFlow()

    fun onEvent(event: PanelEvent) = _panel.update { reduce(it, event) }

    /**
     * Panel chip resolved **last-known-good** (the bug fix): while a chip panel is open, if the
     * chip drops out of the tray (leaves top-9 / goes inactive) the last non-null snapshot is kept
     * so the panel stays live/frozen instead of closing. Cleared when the panel closes.
     *
     * Pure `scan` (no mutable field): the running value carries the last resolved chip, so the
     * transform stays side-effect-free and correct on any dispatcher.
     */
    val panelChip: StateFlow<LauncherChip?> =
        combine(uiState, _panel) { u, p -> (p.request as? PanelRequest.Chip)?.key to u.chips }
            .scan<Pair<String?, List<LauncherChip>>, LauncherChip?>(null) { last, (key, chips) ->
                if (key == null) null else chips.firstOrNull { it.id == key } ?: last
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Media auto-open / stopped emitted from the state flow (design §1) — kills the multi-frame
        // race the old LaunchedEffect had. Collecting uiState here also keeps snapshotFlow hot.
        //
        // Also keyed on "is the panel closed": auto-open used to be edge-triggered on the primary
        // id alone, so once the panel closed for any reason (auto-return, a dismissed chip panel)
        // it never came back while the same session kept playing. Re-emitting on close makes the
        // resting state converge instead. The reducer still guards a real user dismissal
        // (`dismissedAutoKey`) and never clobbers a USER panel, and `_panel.update` writing an
        // equal state emits nothing — so this converges in one pass, it does not ping-pong.
        viewModelScope.launch {
            combine(
                uiState.map { it.mediaSessions.firstOrNull()?.entityId }.distinctUntilChanged(),
                _panel.map { it.request == null }.distinctUntilChanged(),
            ) { primaryId, _ -> primaryId }
                .collect { id ->
                    if (id != null) onEvent(PanelEvent.MediaAutoOpen(id)) else onEvent(PanelEvent.MediaStopped)
                }
        }

        // Alarm entry delay / triggered: the keypad panel is forced open, over whatever is showing.
        // Keyed on "is the panel closed" for the same reason as media above — if the user navigates
        // elsewhere and comes back to the resting state while the alarm is still counting down, the
        // keypad must come back too. `dismissedAlertKey` in the reducer is what makes an explicit
        // dismissal stick.
        viewModelScope.launch {
            combine(
                uiState.map { it.alarmAlert() }.distinctUntilChanged(),
                _panel.map { it.request == null }.distinctUntilChanged(),
            ) { alert, _ -> alert }
                .collect { alert ->
                    if (alert != null) onEvent(PanelEvent.AlarmAlert(alert)) else onEvent(PanelEvent.AlarmCleared)
                }
        }
    }

    fun callService(domain: String, service: String, entityId: String?, data: Map<String, Any>? = null) =
        callServiceFn(domain, service, entityId, data)
}
