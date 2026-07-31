package com.iblu01.portallauncher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private val allowlist = SessionAllowlist(
        mapOf(
            "com.example.app" to AppClassification.HOME,
            "com.media.player" to AppClassification.MEDIA,
        )
    )
    private val launcherPackage = "com.iblu01.portallauncher"

    private fun manager(
        enabled: Boolean = false,
        rateLimitMs: Long = 0L,
    ) = TestFixture(enabled, rateLimitMs)

    private inner class TestFixture(
        enabled: Boolean = false,
        rateLimitMs: Long = 0L,
    ) {
        val timeSource = TestTimeSource(1_000_000L)
        val manager = SessionManager(
            timeSource = timeSource,
            allowlist = allowlist,
            launcherPackage = launcherPackage,
            rateLimitMs = rateLimitMs,
        )

        init {
            if (enabled) manager.setSessionsEnabled(true)
        }

        fun start(
            requestId: String = "req-1",
            packageName: String = "com.example.app",
            durationSeconds: Int = 60,
            expiresAtMs: Long = timeSource.now() + 60_000L,
            reason: String? = null,
        ) = SessionCommand(requestId, SessionAction.START, packageName, durationSeconds, expiresAtMs, reason)

        fun end(
            requestId: String = "req-2",
            packageName: String = "com.example.app",
            reason: String? = null,
        ) = SessionCommand(requestId, SessionAction.END, packageName, 0, 0L, reason)

        fun cancel(
            requestId: String = "req-2",
            packageName: String = "com.example.app",
            reason: String? = null,
        ) = SessionCommand(requestId, SessionAction.CANCEL, packageName, 0, 0L, reason)
    }

    // --- lifecycle / basics ------------------------------------------------

    @Test fun `fresh manager has no active session and is disabled`() {
        val f = manager()
        assertNull(f.manager.activeSession)
        assertEquals(false, f.manager.isEnabled)
    }

    @Test fun `start rejected when kill switch disabled`() {
        val f = manager()
        val transitions = f.manager.process(f.start())
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.REJECTED, transitions[0].result.lifecycle)
        assertEquals(SessionRejectionCode.KILL_SWITCH_DISABLED, transitions[0].result.code)
        assertEquals(0, transitions[0].sideEffects.size)
    }

    @Test fun `start accepted and launches when kill switch enabled`() {
        val f = manager(enabled = true)
        val transitions = f.manager.process(f.start())
        assertEquals(2, transitions.size)
        assertEquals(SessionLifecycle.ACCEPTED, transitions[0].result.lifecycle)
        assertEquals(SessionLifecycle.LAUNCHING, transitions[1].result.lifecycle)
        assertEquals(1, transitions[1].sideEffects.size)
        assertTrue(transitions[1].sideEffects[0] is SessionSideEffect.LaunchApp)
        assertNotNull(f.manager.activeSession)
    }

    @Test fun `only one active session is allowed`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        val second = f.manager.process(f.start(requestId = "req-2"))
        assertEquals(1, second.size)
        assertEquals(SessionLifecycle.REJECTED, second[0].result.lifecycle)
        assertEquals(SessionRejectionCode.SESSION_ALREADY_ACTIVE, second[0].result.code)
    }

    @Test fun `end transitions to ending and returns to launcher`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        val end = f.manager.process(f.end(requestId = "req-2"))
        assertEquals(1, end.size)
        assertEquals(SessionLifecycle.ENDING, end[0].result.lifecycle)
        assertEquals(1, end[0].sideEffects.size)
        assertTrue(end[0].sideEffects[0] is SessionSideEffect.ReturnToLauncher)
    }

    @Test fun `cancel works like end`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        val cancel = f.manager.process(f.cancel(requestId = "req-2"))
        assertEquals(1, cancel.size)
        assertEquals(SessionLifecycle.ENDING, cancel[0].result.lifecycle)
    }

    @Test fun `end with mismatched package is rejected`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1", packageName = "com.example.app"))
        val end = f.manager.process(f.end(requestId = "req-2", packageName = "com.media.player"))
        assertEquals(SessionRejectionCode.PACKAGE_MISMATCH, end[0].result.code)
    }

    @Test fun `end with no active session is rejected`() {
        val f = manager(enabled = true)
        val end = f.manager.process(f.end())
        assertEquals(SessionRejectionCode.NO_ACTIVE_SESSION, end[0].result.code)
    }

    @Test fun `end when already ending is rejected`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.process(f.end(requestId = "req-2"))
        val again = f.manager.process(f.end(requestId = "req-3"))
        assertEquals(SessionRejectionCode.SESSION_ALREADY_ENDING, again[0].result.code)
    }

    // --- device state transitions ------------------------------------------

    @Test fun `launching becomes active when foreground matches`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1", packageName = "com.example.app"))
        val transitions = f.manager.onDeviceState("com.example.app")
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.ACTIVE, transitions[0].result.lifecycle)
        assertEquals(SessionLifecycle.ACTIVE, f.manager.activeSession?.lifecycle)
    }

    @Test fun `ending becomes completed when launcher returns to foreground`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1", packageName = "com.example.app"))
        f.manager.onDeviceState("com.example.app")
        f.manager.process(f.end(requestId = "req-2"))
        val transitions = f.manager.onDeviceState(launcherPackage)
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.COMPLETED, transitions[0].result.lifecycle)
        assertNull(f.manager.activeSession)
    }

    @Test fun `onReturnToLauncher completes an ending session`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.onDeviceState("com.example.app")
        f.manager.process(f.end(requestId = "req-2"))
        val transitions = f.manager.onReturnToLauncher()
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.COMPLETED, transitions[0].result.lifecycle)
        assertNull(f.manager.activeSession)
    }

    @Test fun `ending is not overwritten by expiry while launcher return is pending`() {
        val f = manager(enabled = true)
        val expires = f.timeSource.now() + 5_000L
        f.manager.process(f.start(requestId = "req-1", expiresAtMs = expires))
        f.manager.onDeviceState("com.example.app")
        f.manager.process(f.end(requestId = "req-2"))
        f.timeSource.advance(6_000L)
        assertTrue(f.manager.onDeviceState("com.example.app").isEmpty())
        assertEquals(SessionLifecycle.ENDING, f.manager.activeSession?.lifecycle)
        assertEquals(SessionLifecycle.COMPLETED, f.manager.onReturnToLauncher().single().result.lifecycle)
    }

    // --- expiry ------------------------------------------------------------

    @Test fun `expired session emits exactly one return side effect`() {
        val f = manager(enabled = true)
        val startTime = f.timeSource.now()
        f.manager.process(f.start(requestId = "req-1", expiresAtMs = startTime + 5_000L))
        f.manager.onDeviceState("com.example.app") // ACTIVE

        f.timeSource.advance(6_000L)
        val transitions = f.manager.onDeviceState("com.example.app")
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.EXPIRED, transitions[0].result.lifecycle)
        assertEquals(1, transitions[0].sideEffects.size)
        assertTrue(transitions[0].sideEffects[0] is SessionSideEffect.ReturnToLauncher)
        assertNull(f.manager.activeSession)

        // A second poll after expiry must not emit another side effect.
        val again = f.manager.onDeviceState("com.example.app")
        assertEquals(0, again.size)
    }

    @Test fun `expiry while launching still returns to launcher`() {
        val f = manager(enabled = true)
        val startTime = f.timeSource.now()
        f.manager.process(f.start(requestId = "req-1", expiresAtMs = startTime + 5_000L))
        // never reaches foreground
        f.timeSource.advance(6_000L)
        val transitions = f.manager.onDeviceState(null)
        assertEquals(SessionLifecycle.EXPIRED, transitions[0].result.lifecycle)
        assertEquals(1, transitions[0].sideEffects.size)
        assertTrue(transitions[0].sideEffects[0] is SessionSideEffect.ReturnToLauncher)
    }

    @Test fun `expired start command is rejected`() {
        val f = manager(enabled = true)
        val start = f.start(expiresAtMs = f.timeSource.now() - 1_000L)
        val transitions = f.manager.process(start)
        assertEquals(SessionRejectionCode.EXPIRED_BEFORE_LAUNCH, transitions[0].result.code)
    }

    // --- idempotency -------------------------------------------------------

    @Test fun `duplicate accepted start returns latest result without side effects`() {
        val f = manager(enabled = true)
        val cmd = f.start(requestId = "req-1")
        f.manager.process(cmd)
        f.manager.onDeviceState("com.example.app") // ACTIVE

        val duplicate = f.manager.process(cmd)
        assertEquals(1, duplicate.size)
        assertEquals(SessionLifecycle.ACTIVE, duplicate[0].result.lifecycle)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `duplicate after completed returns completed`() {
        val f = manager(enabled = true)
        val cmd = f.start(requestId = "req-1")
        f.manager.process(cmd)
        f.manager.onDeviceState("com.example.app")
        f.manager.process(f.end(requestId = "req-2"))
        f.manager.onDeviceState(launcherPackage)

        val duplicate = f.manager.process(cmd)
        assertEquals(1, duplicate.size)
        assertEquals(SessionLifecycle.COMPLETED, duplicate[0].result.lifecycle)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `duplicate after expired returns expired`() {
        val f = manager(enabled = true)
        val cmd = f.start(requestId = "req-1", expiresAtMs = f.timeSource.now() + 5_000L)
        f.manager.process(cmd)
        f.manager.onDeviceState("com.example.app")
        f.timeSource.advance(6_000L)
        f.manager.onDeviceState("com.example.app")

        val duplicate = f.manager.process(cmd)
        assertEquals(1, duplicate.size)
        assertEquals(SessionLifecycle.EXPIRED, duplicate[0].result.lifecycle)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `duplicate rejected command replays the rejection`() {
        val f = manager(enabled = true)
        val cmd = f.start(requestId = "req-1", packageName = "com.unknown.app")
        val first = f.manager.process(cmd)
        assertEquals(SessionRejectionCode.UNKNOWN_PACKAGE, first[0].result.code)

        val duplicate = f.manager.process(cmd)
        assertEquals(1, duplicate.size)
        assertEquals(SessionLifecycle.REJECTED, duplicate[0].result.lifecycle)
        assertEquals(SessionRejectionCode.UNKNOWN_PACKAGE, duplicate[0].result.code)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `conflicting duplicate with same request_id is rejected`() {
        val f = manager(enabled = true)
        val cmd1 = f.start(requestId = "req-1", packageName = "com.example.app", durationSeconds = 60)
        val cmd2 = f.start(requestId = "req-1", packageName = "com.example.app", durationSeconds = 90)
        f.manager.process(cmd1)
        val conflict = f.manager.process(cmd2)
        assertEquals(1, conflict.size)
        assertEquals(SessionLifecycle.REJECTED, conflict[0].result.lifecycle)
        assertEquals(SessionRejectionCode.REQUEST_ID_CONFLICT, conflict[0].result.code)
    }

    @Test fun `conflicting duplicate does not overwrite original request binding`() {
        val f = manager(enabled = true)
        val original = f.start(requestId = "req-1", durationSeconds = 60)
        val conflict = f.start(requestId = "req-1", durationSeconds = 90)
        f.manager.process(original)

        assertEquals(
            SessionRejectionCode.REQUEST_ID_CONFLICT,
            f.manager.process(conflict)[0].result.code,
        )

        val replay = f.manager.process(original)
        assertEquals(SessionLifecycle.LAUNCHING, replay[0].result.lifecycle)
        assertEquals("req-1", replay[0].result.requestId)
        assertEquals(0, replay[0].sideEffects.size)
    }

    @Test fun `conflicting duplicate compares all command fields including reason`() {
        val f = manager(enabled = true)
        val cmd1 = f.start(requestId = "req-1", reason = "first")
        val cmd2 = f.start(requestId = "req-1", reason = "second")
        f.manager.process(cmd1)
        val conflict = f.manager.process(cmd2)
        assertEquals(SessionRejectionCode.REQUEST_ID_CONFLICT, conflict[0].result.code)
    }

    @Test fun `conflicting duplicate with different expiry is rejected`() {
        val f = manager(enabled = true)
        val cmd1 = f.start(requestId = "req-1", expiresAtMs = f.timeSource.now() + 60_000L)
        val cmd2 = f.start(requestId = "req-1", expiresAtMs = f.timeSource.now() + 120_000L)
        f.manager.process(cmd1)
        val conflict = f.manager.process(cmd2)
        assertEquals(SessionRejectionCode.REQUEST_ID_CONFLICT, conflict[0].result.code)
    }

    @Test fun `identical duplicate with different request_id is treated as new command`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        val second = f.manager.process(f.start(requestId = "req-2"))
        assertEquals(SessionRejectionCode.SESSION_ALREADY_ACTIVE, second[0].result.code)
    }

    // --- rate limit --------------------------------------------------------

    @Test fun `rate limit rejects new commands within window`() {
        val f = manager(enabled = true, rateLimitMs = 200L)
        f.manager.process(f.start(requestId = "req-1"))
        val rapid = f.manager.process(f.start(requestId = "req-2"))
        assertEquals(SessionRejectionCode.RATE_LIMITED, rapid[0].result.code)
    }

    @Test fun `rate limit window allows command after interval`() {
        val f = manager(enabled = true, rateLimitMs = 200L)
        f.manager.process(f.start(requestId = "req-1"))
        f.timeSource.advance(250L)
        val next = f.manager.process(f.start(requestId = "req-2"))
        assertEquals(SessionRejectionCode.SESSION_ALREADY_ACTIVE, next[0].result.code)
    }

    @Test fun `duplicate replay is checked before rate limit`() {
        val f = manager(enabled = true, rateLimitMs = 200L)
        val cmd = f.start(requestId = "req-1")
        f.manager.process(cmd)
        f.manager.onDeviceState("com.example.app")
        // Within rate limit window but duplicate
        val duplicate = f.manager.process(cmd)
        assertEquals(SessionLifecycle.ACTIVE, duplicate[0].result.lifecycle)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `rate limited result is idempotent`() {
        val f = manager(enabled = true, rateLimitMs = 200L)
        f.manager.process(f.start(requestId = "req-1"))
        val rapidCommand = f.start(requestId = "req-2")
        val rapid = f.manager.process(rapidCommand)
        assertEquals(SessionRejectionCode.RATE_LIMITED, rapid[0].result.code)

        f.timeSource.advance(10_000L) // well past the rate limit
        val duplicate = f.manager.process(rapidCommand)
        assertEquals(SessionRejectionCode.RATE_LIMITED, duplicate[0].result.code)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    // --- kill switch -------------------------------------------------------

    @Test fun `kill switch disabled ends active session`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.onDeviceState("com.example.app")
        val transitions = f.manager.setSessionsEnabled(false)
        assertEquals(1, transitions.size)
        assertEquals(SessionLifecycle.ENDING, transitions[0].result.lifecycle)
        assertEquals(1, transitions[0].sideEffects.size)
        assertTrue(transitions[0].sideEffects[0] is SessionSideEffect.ReturnToLauncher)
    }

    @Test fun `kill switch disabled rejects new commands`() {
        val f = manager(enabled = true)
        f.manager.setSessionsEnabled(false)
        val start = f.manager.process(f.start())
        assertEquals(SessionRejectionCode.KILL_SWITCH_DISABLED, start[0].result.code)
    }

    // --- restart -----------------------------------------------------------

    @Test fun `restart returns idle completed state and does not resume`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.onDeviceState("com.example.app")

        // A fresh manager instance represents a restart.
        val restarted = SessionManager(
            timeSource = f.timeSource,
            allowlist = allowlist,
            launcherPackage = launcherPackage,
        )
        assertNull(restarted.activeSession)
        val idle = SessionSerializer.idleState()
        assertEquals(SessionLifecycle.COMPLETED, idle.lifecycle)
        assertEquals("", idle.requestId)
        assertNull(idle.packageName)
    }

    // --- side effect sanity ------------------------------------------------

    @Test fun `duplicate replay does not re-emit launch side effect`() {
        val f = manager(enabled = true)
        val cmd = f.start(requestId = "req-1")
        f.manager.process(cmd)
        val duplicate = f.manager.process(cmd)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `duplicate end does not re-emit return side effect`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.onDeviceState("com.example.app")
        val end = f.end(requestId = "req-2")
        f.manager.process(end)
        val duplicate = f.manager.process(end)
        assertEquals(SessionLifecycle.ENDING, duplicate[0].result.lifecycle)
        assertEquals(0, duplicate[0].sideEffects.size)
    }

    @Test fun `completed end replay preserves end request id`() {
        val f = manager(enabled = true)
        f.manager.process(f.start(requestId = "req-1"))
        f.manager.onDeviceState("com.example.app")
        val end = f.end(requestId = "req-2", reason = "done")
        f.manager.process(end)
        f.manager.onReturnToLauncher()

        val duplicate = f.manager.process(end)
        assertEquals(SessionLifecycle.COMPLETED, duplicate[0].result.lifecycle)
        assertEquals("req-2", duplicate[0].result.requestId)
        assertEquals("done", duplicate[0].result.reason)
        assertEquals(0, duplicate[0].sideEffects.size)
    }
}
