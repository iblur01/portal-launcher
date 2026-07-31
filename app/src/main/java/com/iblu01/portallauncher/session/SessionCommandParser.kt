package com.iblu01.portallauncher.session

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses a session command from the MQTT `portal/<deviceId>/session/command` topic.
 *
 * The parser is strict and fail-closed:
 * - only the bounded schema fields are accepted,
 * - unknown or missing fields are rejected,
 * - the package must be in the local allowlist with a known classification,
 * - durations and expiry are bounded by the classification defaults/maxima,
 * - commands that are already expired are rejected before any side effect,
 * - integer fields are accepted only as JSON integers; strings and floats are rejected.
 */
object SessionCommandParser {
    const val SCHEMA_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 2048
    private const val MAX_REQUEST_ID_LEN = 64
    private const val MAX_REASON_LEN = 120

    // Reasonable package-name grammar; avoids control chars and spaces.
    private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9._-]{0,127}$")
    private val REQUEST_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

    private val ALLOWED_KEYS = setOf(
        "schema_version",
        "request_id",
        "action",
        "package",
        "duration_s",
        "expires_at",
        "reason",
    )

    sealed class ParseResult {
        data class Valid(val command: SessionCommand) : ParseResult()
        data class Invalid(val code: SessionRejectionCode) : ParseResult()
    }

    fun parse(payload: String, nowMs: Long, allowlist: SessionAllowlist): ParseResult {
        if (payload.toByteArray().size > MAX_PAYLOAD_BYTES) {
            return ParseResult.Invalid(SessionRejectionCode.PAYLOAD_TOO_LARGE)
        }

        val obj = try {
            JSONObject(payload)
        } catch (_: JSONException) {
            return ParseResult.Invalid(SessionRejectionCode.INVALID_JSON)
        }

        val extra = obj.keys().asSequence().toSet() - ALLOWED_KEYS
        if (extra.isNotEmpty()) {
            return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_FIELDS)
        }

        if (!obj.has("schema_version")) {
            return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_SCHEMA_VERSION)
        }
        when (val raw = obj.opt("schema_version")) {
            is Int, is Long -> if ((raw as Number).toInt() != SCHEMA_VERSION) {
                return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_SCHEMA_VERSION)
            }
            else -> return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_SCHEMA_VERSION)
        }

        val requestId = when (val raw = obj.opt("request_id")) {
            is String -> if (raw.isBlank() || !REQUEST_ID_REGEX.matches(raw)) {
                return ParseResult.Invalid(SessionRejectionCode.MALFORMED_REQUEST_ID)
            } else {
                raw
            }
            else -> return ParseResult.Invalid(SessionRejectionCode.MALFORMED_REQUEST_ID)
        }

        val action = when (val raw = obj.opt("action")) {
            is String -> when (raw.lowercase()) {
                "start" -> SessionAction.START
                "end" -> SessionAction.END
                "cancel" -> SessionAction.CANCEL
                else -> return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_ACTION)
            }
            else -> return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_ACTION)
        }

        val packageName = when (val raw = obj.opt("package")) {
            is String -> if (raw.isBlank() || !PACKAGE_REGEX.matches(raw)) {
                return ParseResult.Invalid(SessionRejectionCode.MALFORMED_PACKAGE)
            } else {
                raw
            }
            else -> return ParseResult.Invalid(SessionRejectionCode.MALFORMED_PACKAGE)
        }

        val reason = when (val raw = obj.opt("reason")) {
            null, JSONObject.NULL -> null
            is String -> sanitizeReason(raw)
            else -> return ParseResult.Invalid(SessionRejectionCode.MALFORMED_REASON)
        }

        val nowSeconds = nowMs / 1000

        return when (action) {
            SessionAction.START -> parseStart(
                obj = obj,
                requestId = requestId,
                packageName = packageName,
                reason = reason,
                nowSeconds = nowSeconds,
                nowMs = nowMs,
                allowlist = allowlist,
            )
            SessionAction.END, SessionAction.CANCEL -> {
                if (obj.has("duration_s") || obj.has("expires_at")) {
                    return ParseResult.Invalid(SessionRejectionCode.END_COMMAND_WITH_TEMPORAL_FIELDS)
                }
                ParseResult.Valid(
                    SessionCommand(
                        requestId = requestId,
                        action = action,
                        packageName = packageName,
                        durationSeconds = 0,
                        expiresAtMs = 0L,
                        reason = reason,
                    )
                )
            }
        }
    }

    private fun parseStart(
        obj: JSONObject,
        requestId: String,
        packageName: String,
        reason: String?,
        nowSeconds: Long,
        nowMs: Long,
        allowlist: SessionAllowlist,
    ): ParseResult {
        val classification = allowlist.classificationFor(packageName)
            ?: return ParseResult.Invalid(SessionRejectionCode.UNKNOWN_PACKAGE)

        val requestedDuration = if (obj.has("duration_s")) {
            when (val raw = obj.opt("duration_s")) {
                is Int, is Long -> {
                    val value = (raw as Number).toInt()
                    if (value <= 0) return ParseResult.Invalid(SessionRejectionCode.DURATION_OUT_OF_RANGE)
                    value
                }
                else -> return ParseResult.Invalid(SessionRejectionCode.DURATION_OUT_OF_RANGE)
            }
        } else {
            classification.defaultDurationSeconds
        }

        if (requestedDuration > classification.maxDurationSeconds) {
            return ParseResult.Invalid(SessionRejectionCode.DURATION_EXCEEDS_MAX)
        }
        val effectiveDuration = requestedDuration.coerceAtMost(classification.maxDurationSeconds)

        val requestedExpiresAt = if (obj.has("expires_at")) {
            when (val raw = obj.opt("expires_at")) {
                is Int, is Long -> (raw as Number).toLong()
                else -> return ParseResult.Invalid(SessionRejectionCode.EXPIRES_AT_INVALID)
            }
        } else {
            nowSeconds + effectiveDuration
        }

        if (requestedExpiresAt <= nowSeconds) {
            return ParseResult.Invalid(SessionRejectionCode.EXPIRES_AT_IN_THE_PAST)
        }
        if (requestedExpiresAt > nowSeconds + classification.maxDurationSeconds) {
            return ParseResult.Invalid(SessionRejectionCode.EXPIRES_AT_EXCEEDS_MAX)
        }

        val expiresAtMs = requestedExpiresAt * 1000

        return ParseResult.Valid(
            SessionCommand(
                requestId = requestId,
                action = SessionAction.START,
                packageName = packageName,
                durationSeconds = effectiveDuration,
                expiresAtMs = expiresAtMs,
                reason = reason,
            )
        )
    }

    private fun sanitizeReason(value: String): String = value
        .filter { it.code >= 32 || it == '\t' }
        .trim()
        .take(MAX_REASON_LEN)
}
