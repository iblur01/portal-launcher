package com.iblu01.portallauncher.domain.scene

/**
 * Transient feedback of a scene activation. A scene has no state to watch — Home Assistant only
 * records *when* it last ran — so the pill has to carry the outcome of the request itself.
 */
enum class SceneActivationStatus { PENDING, SUCCEEDED, FAILED }

/**
 * Pure state machine behind the scene pills, kept out of Compose so the double-activation guard is
 * unit-testable.
 *
 * Contract:
 * - [request] returns null while that scene already has a request in flight, so a double tap (or a
 *   tap during a slow network round-trip) never produces a second Home Assistant call;
 * - [settle] applies Home Assistant's own verdict, ignoring any answer that no longer matches the
 *   in-flight token — a late answer from a previous request cannot overwrite a fresher one;
 * - [expire] clears a settled outcome once its display window has elapsed, returning the pill to
 *   its neutral, tappable state.
 */
data class SceneActivationState(
    val entries: Map<String, Entry> = emptyMap(),
    private val nextToken: Long = 1L,
) {
    data class Entry(
        val status: SceneActivationStatus,
        /** Identifies the request an answer belongs to; a stale answer is discarded. */
        val token: Long,
        /** When the outcome was recorded; only meaningful once settled. */
        val settledAtMs: Long = 0L,
    )

    fun statusOf(entityId: String): SceneActivationStatus? = entries[entityId]?.status

    fun isPending(entityId: String): Boolean = statusOf(entityId) == SceneActivationStatus.PENDING

    /**
     * Registers an activation request for [entityId], or returns `null` when one is already in
     * flight. The token must be handed back to [settle].
     */
    fun request(entityId: String): Pair<SceneActivationState, Long>? {
        if (isPending(entityId)) return null
        val token = nextToken
        val next = copy(
            entries = entries + (entityId to Entry(SceneActivationStatus.PENDING, token)),
            nextToken = token + 1,
        )
        return next to token
    }

    fun settle(entityId: String, token: Long, success: Boolean, nowMs: Long): SceneActivationState {
        val entry = entries[entityId] ?: return this
        if (entry.token != token || entry.status != SceneActivationStatus.PENDING) return this
        val status = if (success) SceneActivationStatus.SUCCEEDED else SceneActivationStatus.FAILED
        return copy(entries = entries + (entityId to entry.copy(status = status, settledAtMs = nowMs)))
    }

    /** Drops every settled outcome older than [ttlMs]; pending requests are never expired here. */
    fun expire(nowMs: Long, ttlMs: Long = FEEDBACK_TTL_MS): SceneActivationState {
        val kept = entries.filterValues { entry ->
            entry.status == SceneActivationStatus.PENDING || nowMs - entry.settledAtMs < ttlMs
        }
        return if (kept.size == entries.size) this else copy(entries = kept)
    }

    companion object {
        /** How long a success/failure stays on the pill before it returns to its neutral label. */
        const val FEEDBACK_TTL_MS = 2_000L
    }
}
