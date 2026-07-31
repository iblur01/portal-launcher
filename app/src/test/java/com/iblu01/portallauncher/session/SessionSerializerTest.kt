package com.iblu01.portallauncher.session

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSerializerTest {

    private fun json(result: SessionResult): JSONObject {
        return JSONObject(SessionSerializer.toJson(result))
    }

    @Test fun `emits schema version and lifecycle`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.ACTIVE,
            requestId = "req-1",
            packageName = "com.example.app",
            expiresAtMs = 1200_000L,
            reason = "user reason",
        )
        val obj = json(result)
        assertEquals(1, obj.getInt("schema_version"))
        assertEquals("active", obj.getString("lifecycle"))
        assertEquals("req-1", obj.getString("request_id"))
        assertEquals("com.example.app", obj.getString("package"))
        assertEquals(1200L, obj.getLong("expires_at"))
        assertEquals("user reason", obj.getString("reason"))
        assertTrue(obj.isNull("code"))
    }

    @Test fun `emits rejection code for rejected results`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.REJECTED,
            requestId = "req-1",
            packageName = "com.example.app",
            expiresAtMs = 1200_000L,
            reason = "user reason",
            code = SessionRejectionCode.KILL_SWITCH_DISABLED,
        )
        val obj = json(result)
        assertEquals("kill_switch_disabled", obj.getString("code"))
        assertEquals("rejected", obj.getString("lifecycle"))
    }

    @Test fun `null fields are emitted as JSON null`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.COMPLETED,
            requestId = "",
            packageName = null,
            expiresAtMs = null,
            reason = null,
        )
        val obj = json(result)
        assertTrue(obj.isNull("package"))
        assertTrue(obj.isNull("expires_at"))
        assertTrue(obj.isNull("reason"))
        assertTrue(obj.isNull("code"))
    }

    @Test fun `reason is bounded and sanitized`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.ACTIVE,
            requestId = "req-1",
            packageName = "com.example.app",
            expiresAtMs = 1200_000L,
            reason = "hello\u0000world".repeat(20),
        )
        val obj = json(result)
        val emitted = obj.getString("reason")
        assertEquals(120, emitted.length)
        assertFalse(emitted.contains("\u0000"))
    }

    @Test fun `idle state has completed lifecycle and no identifiers`() {
        val idle = SessionSerializer.idleState()
        assertEquals(SessionLifecycle.COMPLETED, idle.lifecycle)
        assertEquals("", idle.requestId)
        assertNull(idle.packageName)
        assertNull(idle.expiresAtMs)
        assertNull(idle.reason)
        assertNull(idle.code)
    }

    @Test fun `no extra fields are emitted`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.ENDING,
            requestId = "req-1",
            packageName = "com.example.app",
            expiresAtMs = 1200_000L,
            reason = null,
        )
        val obj = json(result)
        assertEquals(7, obj.length())
        assertTrue(obj.has("schema_version"))
        assertTrue(obj.has("lifecycle"))
        assertTrue(obj.has("request_id"))
        assertTrue(obj.has("package"))
        assertTrue(obj.has("expires_at"))
        assertTrue(obj.has("reason"))
        assertTrue(obj.has("code"))
    }

    @Test fun `expires_at is serialized as epoch seconds`() {
        val result = SessionResult(
            lifecycle = SessionLifecycle.ACTIVE,
            requestId = "req-1",
            packageName = "com.example.app",
            expiresAtMs = 1500_500L,
            reason = null,
        )
        val obj = json(result)
        assertEquals(1500L, obj.getLong("expires_at"))
    }
}
