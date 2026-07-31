package com.iblu01.portallauncher.session

enum class SessionAction {
    START,
    END,
    CANCEL,
}

enum class SessionLifecycle {
    ACCEPTED,
    REJECTED,
    LAUNCHING,
    ACTIVE,
    ENDING,
    COMPLETED,
    FAILED,
    EXPIRED,
}

/**
 * Machine-readable, bounded rejection/reason codes for every fail-closed path.
 *
 * The user-provided `reason` field is kept separately in [SessionResult.reason] and is never the
 * operational error text. Codes are emitted by the parser and manager and serialized back to MQTT
 * as `code` when the lifecycle is [REJECTED].
 */
enum class SessionRejectionCode {
    PAYLOAD_TOO_LARGE,
    INVALID_JSON,
    UNKNOWN_SCHEMA_VERSION,
    UNKNOWN_FIELDS,
    MALFORMED_REQUEST_ID,
    UNKNOWN_ACTION,
    MALFORMED_PACKAGE,
    UNKNOWN_PACKAGE,
    MALFORMED_REASON,
    DURATION_OUT_OF_RANGE,
    DURATION_EXCEEDS_MAX,
    EXPIRES_AT_INVALID,
    EXPIRES_AT_IN_THE_PAST,
    EXPIRES_AT_EXCEEDS_MAX,
    END_COMMAND_WITH_TEMPORAL_FIELDS,
    RATE_LIMITED,
    KILL_SWITCH_DISABLED,
    SESSION_ALREADY_ACTIVE,
    EXPIRED_BEFORE_LAUNCH,
    NO_ACTIVE_SESSION,
    PACKAGE_MISMATCH,
    SESSION_ALREADY_ENDING,
    REQUEST_ID_CONFLICT,
    LAUNCH_FAILED,
    RETURN_TO_LAUNCHER_FAILED,
}

/**
 * A validated, bounded session command. All fields are non-nullable where required by the schema.
 */
data class SessionCommand(
    val requestId: String,
    val action: SessionAction,
    val packageName: String,
    val durationSeconds: Int,
    val expiresAtMs: Long,
    val reason: String?,
)

/**
 * A state/event payload emitted by the session manager.
 *
 * [reason] is the sanitized, user-provided reason from the command, if any. [code] is only present
 * for [REJECTED] results and is the machine-readable rejection cause; operational strings are never
 * exposed here.
 */
data class SessionResult(
    val lifecycle: SessionLifecycle,
    val requestId: String,
    val packageName: String?,
    val expiresAtMs: Long?,
    val reason: String?,
    val code: SessionRejectionCode? = null,
)

/**
 * A side effect the manager asks the runtime to perform.
 */
sealed class SessionSideEffect {
    data class LaunchApp(val packageName: String) : SessionSideEffect()
    data object ReturnToLauncher : SessionSideEffect()
}

/**
 * One state change together with any side effects that must happen for it.
 */
data class SessionTransition(
    val result: SessionResult,
    val sideEffects: List<SessionSideEffect> = emptyList(),
)

/**
 * Runtime interface for launching/returning from an external session. The manager keeps this pure:
 * it only returns [SessionSideEffect] values; the caller executes them.
 */
interface SessionLauncher {
    fun launchApp(packageName: String)
    fun returnToLauncher()
}
