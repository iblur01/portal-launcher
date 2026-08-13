package com.iblu01.portallauncher.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.iblu01.portallauncher.HaEntity

/**
 * Per-entity observable store of HA states.
 *
 * The instance stays the same for the whole life of the screen: only the individual
 * [MutableState] slots are rewritten. A `state_changed` on `light.salon` therefore only
 * invalidates the composables that read `light.salon`, instead of recomposing the entire tree
 * under the provider (measured: 122-145 ms per push on the small device before, one frame after).
 *
 * Also carries the optimistic layer (P3): [applyOptimistic] writes the predicted state
 * immediately; [apply] (the real HA push) always wins as soon as it brings news.
 *
 * Main-thread only: [apply] runs from a `LaunchedEffect`, [applyOptimistic] from click handlers,
 * and composition reads happen on the main thread — no synchronization needed.
 */
@Stable
class HaStates(
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Injectable for JVM tests; production lazily uses a main-looper Handler. */
    scheduleExpiry: ((delayMs: Long, action: () -> Unit) -> Unit)? = null,
) {

    private class Slot(
        val state: MutableState<HaEntity?>,
        /** Last state actually confirmed by HA — the rollback target. */
        var confirmed: HaEntity? = null,
        /** Deadline of the pending prediction; 0 = no optimism, MAX_VALUE = assumed_state. */
        var optimisticUntil: Long = 0L,
    )

    private val slots = HashMap<String, Slot>()

    private var scheduler = scheduleExpiry
    private fun schedule(delayMs: Long, action: () -> Unit) {
        // Built lazily so plain-JVM tests never touch android.os.Handler.
        val s = scheduler ?: android.os.Handler(android.os.Looper.getMainLooper())
            .let { handler -> { delay: Long, run: () -> Unit -> handler.postDelayed(run, delay); Unit } }
            .also { scheduler = it }
        s(delayMs, action)
    }

    private fun slot(entityId: String) = slots.getOrPut(entityId) { Slot(mutableStateOf(null)) }

    /** Observable state of one entity, to be read from composition. */
    fun stateOf(entityId: String): State<HaEntity?> = slot(entityId).state

    /** Convenience read: subscribes the calling recompose scope to this entity only. */
    operator fun get(entityId: String): HaEntity? = stateOf(entityId).value

    /** Snapshot of the known entity ids (non-observable), for id-pattern lookups. */
    fun entityIds(): Set<String> =
        slots.entries.mapNotNullTo(mutableSetOf()) { (id, s) -> id.takeIf { s.state.value != null } }

    /**
     * Applies a full HA snapshot. The snapshot carries all entities every time, so optimism cannot
     * be cancelled on mere presence: it is only cancelled when HA actually reports something other
     * than what it had already confirmed.
     */
    fun apply(snapshot: Map<String, HaEntity>) {
        val now = nowMs()
        snapshot.forEach { (id, entity) ->
            val s = slot(id)
            val hasNews = s.confirmed != entity   // HaEntity.equals compares state + attributes
            s.confirmed = entity
            when {
                // HA moved: it is authoritative — confirmation or correction, optimism ends.
                hasNews -> { s.optimisticUntil = 0L; s.state.value = entity }
                // Prediction still valid and HA said nothing new: keep the displayed prediction.
                s.optimisticUntil != 0L && now <= s.optimisticUntil -> Unit
                // Prediction expired without confirmation: back to the real state.
                s.optimisticUntil != 0L -> { s.optimisticUntil = 0L; s.state.value = entity }
                else -> if (s.state.value != entity) s.state.value = entity
            }
        }
        slots.forEach { (id, s) ->
            if (id !in snapshot && s.state.value != null) {
                s.confirmed = null; s.optimisticUntil = 0L; s.state.value = null
            }
        }
    }

    /**
     * Writes the predicted state immediately (P3). The next HA push bringing news for this entity
     * overwrites the prediction; without confirmation, [expire] rolls back to [Slot.confirmed]
     * after [ttlMs] — scheduled locally so the rollback happens even with HA unreachable.
     *
     * `assumed_state` case: HA itself declares it does not know the real state (RF remote, plug
     * without state feedback). No confirmation will ever arrive — the prediction becomes the
     * displayed state, without expiry. This mirrors the HA frontend's own behaviour.
     */
    fun applyOptimistic(
        entityId: String,
        ttlMs: Long = OPTIMISTIC_TTL_MS,
        predict: (HaEntity) -> HaEntity,
    ) {
        val s = slots[entityId] ?: return
        val base = s.state.value ?: return
        val predicted = predict(base)
        if (predicted == base) return   // nothing predictable: leave HA in charge
        if (base.attributes.optBoolean("assumed_state", false)) {
            s.optimisticUntil = Long.MAX_VALUE
        } else {
            s.optimisticUntil = nowMs() + ttlMs
            schedule(ttlMs + EXPIRY_SLACK_MS) { expire(entityId) }
        }
        s.state.value = predicted
    }

    /** Rolls the entity back to its confirmed state if its prediction expired unconfirmed. */
    private fun expire(entityId: String) {
        val s = slots[entityId] ?: return
        if (s.optimisticUntil == 0L || s.optimisticUntil == Long.MAX_VALUE) return
        if (nowMs() < s.optimisticUntil) return
        s.optimisticUntil = 0L
        if (s.state.value != s.confirmed) s.state.value = s.confirmed
    }

    private companion object {
        /** Beyond this, the command is considered lost and the real state takes back over. */
        const val OPTIMISTIC_TTL_MS = 4_000L
        /** Scheduled a little late so the deadline comparison in [expire] is unambiguous. */
        const val EXPIRY_SLACK_MS = 50L
    }
}
