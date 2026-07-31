package com.iblu01.portallauncher.session

import java.util.LinkedHashMap

/**
 * Pure, in-memory state machine for bounded external-app sessions.
 *
 * Responsibilities:
 * - enforce one session at a time,
 * - enforce the local kill switch,
 * - drive the lifecycle (accepted → launching → active → ending → completed),
 * - handle expiry and cancellation,
 * - replay results for duplicate [request_id] without side effects,
 * - ask the runtime to perform [SessionSideEffect] via returned transitions.
 *
 * The manager does not touch Android APIs, Context or MQTT directly. It is instantiated once per
 * process; a restart loses any incomplete session intentionally.
 */
class SessionManager(
    private val timeSource: SessionTimeSource,
    private val allowlist: SessionAllowlist,
    private val launcherPackage: String,
    private val rateLimitMs: Long = 200L,
    private val idempotencyTtlMs: Long = 5 * 60_000L,
    private val maxIdempotencyEntries: Int = 100,
) {

    private data class CurrentSession(
        val command: SessionCommand,
        val classification: AppClassification,
        val startedAtMs: Long,
        var lifecycle: SessionLifecycle,
        var endingCommand: SessionCommand? = null,
    )

    private data class ResultRecord(
        val command: SessionCommand,
        val result: SessionResult,
        val recordedAtMs: Long,
    )

    private val lock = Object()

    @Volatile
    private var currentSession: CurrentSession? = null
    private val results = LinkedHashMap<String, ResultRecord>()
    @Volatile
    private var sessionsEnabled: Boolean = false
    private var lastCommandAtMs: Long = 0L

    /**
     * Toggles the local kill switch. Disabling an active session forces it to end.
     */
    fun setSessionsEnabled(enabled: Boolean): List<SessionTransition> = synchronized(lock) {
        sessionsEnabled = enabled
        if (!enabled) {
            val session = currentSession ?: return@synchronized emptyList<SessionTransition>()
            killActiveSession(session, timeSource.now())
        } else {
            emptyList()
        }
    }

    fun process(command: SessionCommand): List<SessionTransition> = synchronized(lock) {
        val now = timeSource.now()
        cleanIdempotency(now)

        val existing = results[command.requestId]
        if (existing != null) {
            return if (commandsMatch(existing.command, command)) {
                listOf(SessionTransition(existing.result, emptyList()))
            } else {
                // Preserve the original request-ID binding; conflicts are never cached over it.
                listOf(rejected(command, SessionRejectionCode.REQUEST_ID_CONFLICT))
            }
        }

        if (now - lastCommandAtMs < rateLimitMs) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.RATE_LIMITED)), command, now)
        }
        lastCommandAtMs = now

        when (command.action) {
            SessionAction.START -> start(command, now)
            SessionAction.END, SessionAction.CANCEL -> endOrCancel(command, now)
        }
    }

    /**
     * Should be called on every foreground-package change and periodically. It transitions
     * launching → active when the requested app reaches the foreground, and ending → completed
     * when the launcher returns to the foreground.
     */
    fun onDeviceState(
        foregroundPackage: String?,
        nowMs: Long = timeSource.now(),
    ): List<SessionTransition> = synchronized(lock) {
        val transitions = mutableListOf<SessionTransition>()
        val session = currentSession ?: return transitions

        if (nowMs >= session.command.expiresAtMs) {
            transitions += expire(session, nowMs)
            return transitions
        }

        when (session.lifecycle) {
            SessionLifecycle.LAUNCHING -> {
                if (foregroundPackage == session.command.packageName) {
                    session.lifecycle = SessionLifecycle.ACTIVE
                    val result = SessionResult(
                        lifecycle = SessionLifecycle.ACTIVE,
                        requestId = session.command.requestId,
                        packageName = session.command.packageName,
                        expiresAtMs = session.command.expiresAtMs,
                        reason = session.command.reason,
                    )
                    rememberResult(session.command, result, nowMs)
                    session.endingCommand?.let {
                        rememberResult(it, result.copy(requestId = it.requestId, reason = it.reason), nowMs)
                    }
                    transitions += SessionTransition(result, emptyList())
                }
            }
            SessionLifecycle.ENDING -> {
                if (foregroundPackage == launcherPackage || foregroundPackage == null) {
                    currentSession = null
                    val result = SessionResult(
                        lifecycle = SessionLifecycle.COMPLETED,
                        requestId = session.command.requestId,
                        packageName = session.command.packageName,
                        expiresAtMs = null,
                        reason = session.command.reason,
                    )
                    rememberResult(session.command, result, nowMs)
                    session.endingCommand?.let {
                        rememberResult(it, result.copy(requestId = it.requestId, reason = it.reason), nowMs)
                    }
                    transitions += SessionTransition(result, emptyList())
                }
            }
            else -> Unit
        }
        transitions
    }

    /**
     * Called by the runtime once it has initiated a return to the launcher. Mirrors the transition
     * in [onDeviceState] but does not require a foreground-package report.
     */
    fun onReturnToLauncher(nowMs: Long = timeSource.now()): List<SessionTransition> = synchronized(lock) {
        val session = currentSession ?: return emptyList()
        if (session.lifecycle != SessionLifecycle.ENDING) return emptyList()
        currentSession = null
        val result = SessionResult(
            lifecycle = SessionLifecycle.COMPLETED,
            requestId = session.command.requestId,
            packageName = session.command.packageName,
            expiresAtMs = null,
            reason = session.command.reason,
        )
        rememberResult(session.command, result, nowMs)
        session.endingCommand?.let {
            rememberResult(it, result.copy(requestId = it.requestId, reason = it.reason), nowMs)
        }
        return listOf(SessionTransition(result, emptyList()))
    }

    val isEnabled: Boolean get() = synchronized(lock) { sessionsEnabled }

    val activeSession: SessionResult? get() = synchronized(lock) {
        currentSession?.let {
            SessionResult(
                lifecycle = it.lifecycle,
                requestId = it.command.requestId,
                packageName = it.command.packageName,
                expiresAtMs = it.command.expiresAtMs,
                reason = it.command.reason,
            )
        }
    }

    private fun start(command: SessionCommand, nowMs: Long): List<SessionTransition> {
        if (!sessionsEnabled) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.KILL_SWITCH_DISABLED)), command, nowMs)
        }
        if (currentSession != null) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.SESSION_ALREADY_ACTIVE)), command, nowMs)
        }
        val classification = allowlist.classificationFor(command.packageName)
            ?: return rememberLast(listOf(rejected(command, SessionRejectionCode.UNKNOWN_PACKAGE)), command, nowMs)
        if (command.expiresAtMs <= nowMs) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.EXPIRED_BEFORE_LAUNCH)), command, nowMs)
        }

        val session = CurrentSession(
            command = command,
            classification = classification,
            startedAtMs = nowMs,
            lifecycle = SessionLifecycle.ACCEPTED,
        )
        currentSession = session
        session.lifecycle = SessionLifecycle.LAUNCHING

        val accepted = SessionTransition(
            SessionResult(
                lifecycle = SessionLifecycle.ACCEPTED,
                requestId = command.requestId,
                packageName = command.packageName,
                expiresAtMs = command.expiresAtMs,
                reason = command.reason,
            ),
            emptyList(),
        )
        val launching = SessionTransition(
            SessionResult(
                lifecycle = SessionLifecycle.LAUNCHING,
                requestId = command.requestId,
                packageName = command.packageName,
                expiresAtMs = command.expiresAtMs,
                reason = command.reason,
            ),
            sideEffects = listOf(SessionSideEffect.LaunchApp(command.packageName)),
        )
        return rememberLast(listOf(accepted, launching), command, nowMs)
    }

    private fun endOrCancel(command: SessionCommand, nowMs: Long): List<SessionTransition> {
        val session = currentSession
        if (session == null) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.NO_ACTIVE_SESSION)), command, nowMs)
        }
        if (session.lifecycle == SessionLifecycle.ENDING) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.SESSION_ALREADY_ENDING)), command, nowMs)
        }
        if (!isActiveLifecycle(session.lifecycle)) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.NO_ACTIVE_SESSION)), command, nowMs)
        }
        if (session.command.packageName != command.packageName) {
            return rememberLast(listOf(rejected(command, SessionRejectionCode.PACKAGE_MISMATCH)), command, nowMs)
        }

        session.lifecycle = SessionLifecycle.ENDING
        session.endingCommand = command
        val result = SessionResult(
            lifecycle = SessionLifecycle.ENDING,
            requestId = command.requestId,
            packageName = session.command.packageName,
            expiresAtMs = session.command.expiresAtMs,
            reason = command.reason,
        )
        rememberResult(
            session.command,
            result.copy(requestId = session.command.requestId, reason = session.command.reason),
            nowMs,
        )
        rememberResult(command, result, nowMs)
        return listOf(SessionTransition(result, listOf(SessionSideEffect.ReturnToLauncher)))
    }

    private fun expire(session: CurrentSession, nowMs: Long): List<SessionTransition> {
        currentSession = null
        val result = SessionResult(
            lifecycle = SessionLifecycle.EXPIRED,
            requestId = session.command.requestId,
            packageName = session.command.packageName,
            expiresAtMs = session.command.expiresAtMs,
            reason = null,
        )
        rememberResult(session.command, result, nowMs)
        return listOf(
            SessionTransition(
                result,
                sideEffects = if (session.lifecycle == SessionLifecycle.LAUNCHING || session.lifecycle == SessionLifecycle.ACTIVE) {
                    listOf(SessionSideEffect.ReturnToLauncher)
                } else {
                    emptyList()
                },
            ),
        )
    }

    private fun killActiveSession(session: CurrentSession, nowMs: Long): List<SessionTransition> {
        session.lifecycle = SessionLifecycle.ENDING
        val result = SessionResult(
            lifecycle = SessionLifecycle.ENDING,
            requestId = session.command.requestId,
            packageName = session.command.packageName,
            expiresAtMs = session.command.expiresAtMs,
            reason = session.command.reason,
        )
        rememberResult(session.command, result, nowMs)
        return listOf(SessionTransition(result, listOf(SessionSideEffect.ReturnToLauncher)))
    }

    private fun cleanIdempotency(nowMs: Long) {
        val cutoff = nowMs - idempotencyTtlMs
        results.entries.removeAll { it.value.recordedAtMs < cutoff }
        while (results.size > maxIdempotencyEntries) {
            results.remove(results.keys.first())
        }
    }

    private fun rememberLast(transitions: List<SessionTransition>, command: SessionCommand, nowMs: Long): List<SessionTransition> {
        transitions.lastOrNull()?.let { rememberResult(command, it.result, nowMs) }
        return transitions
    }

    private fun rememberResult(command: SessionCommand, result: SessionResult, nowMs: Long) {
        results[command.requestId] = ResultRecord(command, result, nowMs)
    }

    private fun commandsMatch(a: SessionCommand, b: SessionCommand): Boolean =
        a.requestId == b.requestId &&
            a.action == b.action &&
            a.packageName == b.packageName &&
            a.durationSeconds == b.durationSeconds &&
            a.expiresAtMs == b.expiresAtMs &&
            a.reason == b.reason

    private fun isActiveLifecycle(lifecycle: SessionLifecycle): Boolean =
        lifecycle == SessionLifecycle.ACCEPTED ||
            lifecycle == SessionLifecycle.LAUNCHING ||
            lifecycle == SessionLifecycle.ACTIVE

    private fun rejected(command: SessionCommand, code: SessionRejectionCode): SessionTransition = SessionTransition(
        SessionResult(
            lifecycle = SessionLifecycle.REJECTED,
            requestId = command.requestId,
            packageName = command.packageName,
            expiresAtMs = command.expiresAtMs,
            reason = command.reason,
            code = code,
        ),
        emptyList(),
    )
}
