package com.iblu01.portallauncher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {
    private val allowlist = SessionAllowlist(mapOf("com.example.app" to AppClassification.HOME))

    private class Runtime : SessionRuntime {
        val events = mutableListOf<SessionResult>()
        val states = mutableListOf<SessionResult>()
        var launchCalls = 0
        var returnCalls = 0
        var launchSucceeds = true
        var returnSucceeds = true

        override fun publishEvent(result: SessionResult) { events += result }
        override fun publishState(result: SessionResult) { states += result }
        override fun launchApp(packageName: String): Boolean {
            launchCalls++
            return launchSucceeds
        }
        override fun returnToLauncher(): Boolean {
            returnCalls++
            return returnSucceeds
        }
    }

    private data class Fixture(
        val time: TestTimeSource,
        val manager: SessionManager,
        val runtime: Runtime,
        val coordinator: SessionCoordinator,
    )

    private fun fixture(enabled: Boolean = true): Fixture {
        val time = TestTimeSource(1_000_000L)
        val manager = SessionManager(time, allowlist, "com.iblu01.portallauncher", rateLimitMs = 0)
        val runtime = Runtime()
        val coordinator = SessionCoordinator(manager, allowlist, time, runtime)
        coordinator.setEnabled(enabled)
        return Fixture(time, manager, runtime, coordinator)
    }

    private fun startJson(requestId: String = "req-1", expiresAt: Long = 1060) =
        """{"schema_version":1,"request_id":"$requestId","action":"start","package":"com.example.app","duration_s":60,"expires_at":$expiresAt}"""

    private fun endJson(requestId: String = "req-end") =
        """{"schema_version":1,"request_id":"$requestId","action":"end","package":"com.example.app"}"""

    private fun defaultStartJson(requestId: String = "req-default") =
        """{"schema_version":1,"request_id":"$requestId","action":"start","package":"com.example.app"}"""

    @Test fun `empty retained clear is ignored`() {
        val f = fixture()
        assertFalse(f.coordinator.onCommand("  "))
        assertTrue(f.runtime.events.isEmpty())
        assertTrue(f.runtime.states.isEmpty())
        assertEquals(0, f.runtime.launchCalls)
    }

    @Test fun `valid start publishes transitions and launches exactly once`() {
        val f = fixture()
        assertTrue(f.coordinator.onCommand(startJson()))
        assertEquals(listOf(SessionLifecycle.ACCEPTED, SessionLifecycle.LAUNCHING), f.runtime.events.map { it.lifecycle })
        assertEquals(2, f.runtime.states.size)
        assertEquals(1, f.runtime.launchCalls)

        f.coordinator.onCommand(startJson())
        assertEquals(1, f.runtime.launchCalls)
    }

    @Test fun `qos replay with derived expiry is idempotent after clock advances`() {
        val f = fixture()
        f.coordinator.onCommand(defaultStartJson())
        f.time.advance(1_500L)
        f.coordinator.onCommand(defaultStartJson())

        assertEquals(1, f.runtime.launchCalls)
        assertEquals(SessionLifecycle.LAUNCHING, f.runtime.states.last().lifecycle)
        assertEquals(null, f.runtime.states.last().code)
    }

    @Test fun `same request id with different requested temporal fields conflicts`() {
        val f = fixture()
        f.coordinator.onCommand(defaultStartJson())
        f.coordinator.onCommand(
            """{"schema_version":1,"request_id":"req-default","action":"start","package":"com.example.app","duration_s":60}"""
        )

        assertEquals(1, f.runtime.launchCalls)
        assertEquals(SessionRejectionCode.REQUEST_ID_CONFLICT, f.runtime.states.last().code)
    }

    @Test fun `invalid command is rejected without side effects`() {
        val f = fixture()
        f.coordinator.onCommand("not-json")
        assertEquals(SessionLifecycle.REJECTED, f.runtime.states.last().lifecycle)
        assertEquals(SessionRejectionCode.INVALID_JSON, f.runtime.states.last().code)
        assertEquals(0, f.runtime.launchCalls)
    }

    @Test fun `launch failure publishes failed and clears active session`() {
        val f = fixture()
        f.runtime.launchSucceeds = false
        f.coordinator.onCommand(startJson())
        assertEquals(SessionLifecycle.FAILED, f.runtime.states.last().lifecycle)
        assertEquals(SessionRejectionCode.LAUNCH_FAILED, f.runtime.states.last().code)
        assertNull(f.manager.activeSession)
    }

    @Test fun `expiry returns once`() {
        val f = fixture()
        f.coordinator.onCommand(startJson(expiresAt = 1005))
        f.coordinator.onDeviceState("com.example.app")
        f.time.advance(6_000L)
        f.coordinator.onDeviceState("com.example.app")
        f.coordinator.onDeviceState("com.example.app")
        assertEquals(1, f.runtime.returnCalls)
        assertEquals(SessionLifecycle.EXPIRED, f.runtime.events.last().lifecycle)
    }

    @Test fun `local disable ends active session`() {
        val f = fixture()
        f.coordinator.onCommand(startJson())
        f.coordinator.onDeviceState("com.example.app")
        f.coordinator.setEnabled(false)
        assertEquals(1, f.runtime.returnCalls)
        assertEquals(SessionLifecycle.COMPLETED, f.runtime.states.last().lifecycle)
    }

    @Test fun `successful end returns once and completes without foreground callback`() {
        val f = fixture()
        f.coordinator.onCommand(startJson())
        f.coordinator.onDeviceState("com.example.app")
        f.coordinator.onCommand(endJson())
        assertEquals(1, f.runtime.returnCalls)
        assertEquals(SessionLifecycle.COMPLETED, f.runtime.states.last().lifecycle)
        assertNull(f.manager.activeSession)
    }

    @Test fun `disabling with no active session has no side effects`() {
        val f = fixture()
        f.coordinator.setEnabled(false)
        assertEquals(0, f.runtime.returnCalls)
        assertTrue(f.runtime.events.isEmpty())
    }

    @Test fun `return failure publishes bounded failed status`() {
        val f = fixture()
        f.runtime.returnSucceeds = false
        f.coordinator.onCommand(startJson(expiresAt = 1005))
        f.coordinator.onDeviceState("com.example.app")
        f.time.advance(6_000L)
        f.coordinator.onDeviceState("com.example.app")
        assertEquals(SessionLifecycle.FAILED, f.runtime.states.last().lifecycle)
        assertEquals(SessionRejectionCode.RETURN_TO_LAUNCHER_FAILED, f.runtime.states.last().code)
    }

    @Test fun `restart publishes retained-state payload only`() {
        val f = fixture()
        f.coordinator.publishCurrentState()
        assertTrue(f.runtime.events.isEmpty())
        assertEquals(1, f.runtime.states.size)
        assertEquals(SessionLifecycle.COMPLETED, f.runtime.states.single().lifecycle)
        assertEquals("", f.runtime.states.single().requestId)
    }

    @Test fun `mqtt reconnect republishes active state without relaunching`() {
        val f = fixture()
        f.coordinator.onCommand(startJson())
        f.coordinator.onDeviceState("com.example.app")
        f.runtime.events.clear()
        f.runtime.states.clear()

        f.coordinator.publishCurrentState()

        assertTrue(f.runtime.events.isEmpty())
        assertEquals(SessionLifecycle.ACTIVE, f.runtime.states.single().lifecycle)
        assertEquals(1, f.runtime.launchCalls)
    }

    @Test fun `mqtt reconnect republishes expiry reached while disconnected`() {
        val f = fixture()
        f.coordinator.onCommand(startJson(expiresAt = 1005))
        f.coordinator.onDeviceState("com.example.app")
        f.time.advance(6_000L)
        f.coordinator.onDeviceState("com.example.app")
        f.runtime.events.clear()
        f.runtime.states.clear()

        f.coordinator.publishCurrentState()

        assertEquals(SessionLifecycle.EXPIRED, f.runtime.states.single().lifecycle)
        assertEquals(1, f.runtime.returnCalls)
    }
}
