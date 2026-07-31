package com.iblu01.portallauncher.session

/** Runtime boundary used by [SessionCoordinator]. Android and MQTT stay behind this interface. */
interface SessionRuntime {
    fun publishEvent(result: SessionResult)
    fun publishState(result: SessionResult)
    fun launchApp(packageName: String): Boolean
    fun returnToLauncher(): Boolean
}

/**
 * Coordinates strict command parsing, the pure session state machine, MQTT publication, and
 * exactly-once runtime side effects.
 */
class SessionCoordinator(
    private val manager: SessionManager,
    private val allowlist: SessionAllowlist,
    private val timeSource: SessionTimeSource,
    private val runtime: SessionRuntime,
) {
    @Volatile
    private var latestState: SessionResult = SessionSerializer.idleState()

    /** Publishes current state after MQTT connects; a fresh process naturally reports idle. */
    @Synchronized
    fun publishCurrentState() {
        runtime.publishState(latestState)
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        apply(manager.setSessionsEnabled(enabled))
    }

    /** Empty retained-command clears are ignored rather than parsed as commands. */
    @Synchronized
    fun onCommand(payload: String): Boolean {
        if (payload.isBlank()) return false
        when (val parsed = SessionCommandParser.parse(payload, timeSource.now(), allowlist)) {
            is SessionCommandParser.ParseResult.Valid -> apply(manager.process(parsed.command))
            is SessionCommandParser.ParseResult.Invalid -> publish(
                SessionResult(
                    lifecycle = SessionLifecycle.REJECTED,
                    requestId = "",
                    packageName = null,
                    expiresAtMs = null,
                    reason = null,
                    code = parsed.code,
                )
            )
        }
        return true
    }

    @Synchronized
    fun onDeviceState(foregroundPackage: String?) {
        apply(manager.onDeviceState(foregroundPackage))
    }

    private fun apply(transitions: List<SessionTransition>) {
        transitions.forEach { transition ->
            publish(transition.result)
            transition.sideEffects.forEach { effect ->
                val failure = when (effect) {
                    is SessionSideEffect.LaunchApp -> {
                        if (runtime.launchApp(effect.packageName)) null
                        else SessionRejectionCode.LAUNCH_FAILED
                    }
                    SessionSideEffect.ReturnToLauncher -> {
                        if (runtime.returnToLauncher()) {
                            manager.onReturnToLauncher().forEach { publish(it.result) }
                            null
                        } else SessionRejectionCode.RETURN_TO_LAUNCHER_FAILED
                    }
                }
                if (failure != null) {
                    val failed = manager.failActive(failure)
                    if (failed.isEmpty()) {
                        publish(
                            transition.result.copy(
                                lifecycle = SessionLifecycle.FAILED,
                                expiresAtMs = null,
                                code = failure,
                            )
                        )
                    } else {
                        failed.forEach { publish(it.result) }
                    }
                }
            }
        }
    }

    private fun publish(result: SessionResult) {
        latestState = result
        runtime.publishEvent(result)
        runtime.publishState(result)
    }
}
