package com.iblu01.portallauncher.session

import org.json.JSONObject

/**
 * Serializes session results to the sanitized event/state JSON.
 *
 * Only the bounded fields are emitted: schema_version, lifecycle, request_id, package,
 * expires_at (epoch seconds), reason, and code. `null` values are written as JSON `null` so consumers
 * see a stable schema even when no session is active.
 */
object SessionSerializer {
    private const val SCHEMA_VERSION = 1
    private const val MAX_REASON_LEN = 120

    fun toJson(result: SessionResult): String {
        val obj = JSONObject()
        obj.put("schema_version", SCHEMA_VERSION)
        obj.put("lifecycle", result.lifecycle.name.lowercase())
        obj.put("request_id", result.requestId.ifEmpty { JSONObject.NULL })
        obj.put("package", result.packageName ?: JSONObject.NULL)
        obj.put("expires_at", result.expiresAtMs?.let { it / 1000 } ?: JSONObject.NULL)
        obj.put("reason", result.reason?.let { sanitizeReason(it) } ?: JSONObject.NULL)
        obj.put("code", result.code?.name?.lowercase() ?: JSONObject.NULL)
        return obj.toString()
    }

    private fun sanitizeReason(value: String): String = value
        .filter { it.code >= 32 || it == '\t' }
        .trim()
        .take(MAX_REASON_LEN)

    /**
     * The payload to publish when no session is active after a restart. The lifecycle is
     * `completed` because a restart intentionally ends any incomplete session and never resumes it.
     */
    fun idleState(): SessionResult = SessionResult(
        lifecycle = SessionLifecycle.COMPLETED,
        requestId = "",
        packageName = null,
        expiresAtMs = null,
        reason = null,
        code = null,
    )
}
