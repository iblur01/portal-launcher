package com.iblu01.portallauncher.session

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCommandParserTest {

    private val allowlist = SessionAllowlist(
        mapOf(
            "com.example.app" to AppClassification.HOME,
            "com.media.player" to AppClassification.MEDIA,
            "com.util.tool" to AppClassification.UTILITY,
        )
    )

    private fun nowMs() = 1_000_000L // epoch seconds = 1000

    private fun validStart(
        requestId: String = "req-1",
        packageName: String = "com.example.app",
        duration: Int? = null,
        expiresAt: Int? = null,
        reason: String? = null,
    ): String {
        val obj = JSONObject()
        obj.put("schema_version", 1)
        obj.put("request_id", requestId)
        obj.put("action", "start")
        obj.put("package", packageName)
        if (duration != null) obj.put("duration_s", duration)
        if (expiresAt != null) obj.put("expires_at", expiresAt)
        if (reason != null) obj.put("reason", reason)
        return obj.toString()
    }

    private fun assertCode(payload: String, code: SessionRejectionCode) {
        val result = SessionCommandParser.parse(payload, nowMs(), allowlist)
        assertTrue("expected Invalid but got $result", result is SessionCommandParser.ParseResult.Invalid)
        assertEquals(code, (result as SessionCommandParser.ParseResult.Invalid).code)
    }

    private fun assertValidCommand(payload: String, assertion: (SessionCommand) -> Unit) {
        val result = SessionCommandParser.parse(payload, nowMs(), allowlist)
        assertTrue("expected Valid but got $result", result is SessionCommandParser.ParseResult.Valid)
        assertion((result as SessionCommandParser.ParseResult.Valid).command)
    }

    // --- happy path --------------------------------------------------------

    @Test fun `start with default duration uses classification default`() = assertValidCommand(validStart()) {
        assertEquals("req-1", it.requestId)
        assertEquals(SessionAction.START, it.action)
        assertEquals("com.example.app", it.packageName)
        assertEquals(AppClassification.HOME.defaultDurationSeconds, it.durationSeconds)
        assertEquals((nowMs() / 1000 + AppClassification.HOME.defaultDurationSeconds) * 1000, it.expiresAtMs)
        assertEquals(null, it.reason)
    }

    @Test fun `start with explicit duration`() = assertValidCommand(validStart(duration = 45)) {
        assertEquals(45, it.durationSeconds)
        assertEquals((nowMs() / 1000 + 45) * 1000, it.expiresAtMs)
    }

    @Test fun `start with explicit expires_at`() = assertValidCommand(validStart(expiresAt = 1100)) {
        assertEquals(AppClassification.HOME.defaultDurationSeconds, it.durationSeconds)
        assertEquals(1100L * 1000, it.expiresAtMs)
    }

    @Test fun `start with reason is sanitized and bounded`() = assertValidCommand(
        validStart(reason = "  user reason  ")
    ) {
        assertEquals("user reason", it.reason)
    }

    @Test fun `end command is valid without temporal fields`() {
        val payload = JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "end")
            put("package", "com.example.app")
        }.toString()
        assertValidCommand(payload) {
            assertEquals(SessionAction.END, it.action)
            assertEquals(0, it.durationSeconds)
            assertEquals(0L, it.expiresAtMs)
        }
    }

    @Test fun `cancel command is valid without temporal fields`() {
        val payload = JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "cancel")
            put("package", "com.example.app")
        }.toString()
        assertValidCommand(payload) {
            assertEquals(SessionAction.CANCEL, it.action)
        }
    }

    // --- payload size ------------------------------------------------------

    @Test fun `payload exceeding byte cap is rejected`() {
        val hugeReason = "a".repeat(SessionCommandParser.MAX_PAYLOAD_BYTES + 1)
        val payload = validStart(reason = hugeReason)
        assertCode(payload, SessionRejectionCode.PAYLOAD_TOO_LARGE)
    }

    // --- JSON / schema -----------------------------------------------------

    @Test fun `invalid JSON is rejected`() = assertCode("{ not json", SessionRejectionCode.INVALID_JSON)

    @Test fun `missing schema_version is rejected`() = assertCode(
        JSONObject().apply { put("request_id", "req-1"); put("action", "start"); put("package", "com.example.app") }.toString(),
        SessionRejectionCode.UNKNOWN_SCHEMA_VERSION
    )

    @Test fun `wrong schema_version is rejected`() = assertCode(
        validStart(requestId = "req-1").replace("\"schema_version\":1", "\"schema_version\":2"),
        SessionRejectionCode.UNKNOWN_SCHEMA_VERSION
    )

    @Test fun `string schema_version is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", "1")
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
        }.toString(),
        SessionRejectionCode.UNKNOWN_SCHEMA_VERSION
    )

    @Test fun `unknown fields are rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("extra", "value")
        }.toString(),
        SessionRejectionCode.UNKNOWN_FIELDS
    )

    // --- request_id --------------------------------------------------------

    @Test fun `missing request_id is rejected`() = assertCode(
        JSONObject().apply { put("schema_version", 1); put("action", "start"); put("package", "com.example.app") }.toString(),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    @Test fun `blank request_id is rejected`() = assertCode(
        validStart(requestId = "  "),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    @Test fun `request_id with spaces is rejected`() = assertCode(
        validStart(requestId = "req 1"),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    @Test fun `request_id with control char is rejected`() = assertCode(
        validStart(requestId = "req\u00001"),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    @Test fun `request_id longer than 64 is rejected`() = assertCode(
        validStart(requestId = "a".repeat(65)),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    @Test fun `numeric request_id is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", 123)
            put("action", "start")
            put("package", "com.example.app")
        }.toString(),
        SessionRejectionCode.MALFORMED_REQUEST_ID
    )

    // --- action ------------------------------------------------------------

    @Test fun `missing action is rejected`() = assertCode(
        JSONObject().apply { put("schema_version", 1); put("request_id", "req-1"); put("package", "com.example.app") }.toString(),
        SessionRejectionCode.UNKNOWN_ACTION
    )

    @Test fun `unknown action is rejected`() = assertCode(
        validStart().replace("\"action\":\"start\"", "\"action\":\"pause\""),
        SessionRejectionCode.UNKNOWN_ACTION
    )

    @Test fun `numeric action is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", 1)
            put("package", "com.example.app")
        }.toString(),
        SessionRejectionCode.UNKNOWN_ACTION
    )

    // --- package -----------------------------------------------------------

    @Test fun `missing package is rejected`() = assertCode(
        JSONObject().apply { put("schema_version", 1); put("request_id", "req-1"); put("action", "start") }.toString(),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `blank package is rejected`() = assertCode(
        validStart(packageName = "  "),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `package with space is rejected`() = assertCode(
        validStart(packageName = "com.example.app name"),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `package starting with digit is rejected`() = assertCode(
        validStart(packageName = "1com.example.app"),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `package with control char is rejected`() = assertCode(
        validStart(packageName = "com.example\u0000app"),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `package longer than 128 is rejected`() = assertCode(
        validStart(packageName = "com." + "a".repeat(130)),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `numeric package is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", 123)
        }.toString(),
        SessionRejectionCode.MALFORMED_PACKAGE
    )

    @Test fun `unknown package is rejected`() = assertCode(
        validStart(packageName = "com.unknown.app"),
        SessionRejectionCode.UNKNOWN_PACKAGE
    )

    // --- duration / expiry type handling -----------------------------------

    @Test fun `string duration is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("duration_s", "60")
        }.toString(),
        SessionRejectionCode.DURATION_OUT_OF_RANGE
    )

    @Test fun `float duration is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("duration_s", 60.5)
        }.toString(),
        SessionRejectionCode.DURATION_OUT_OF_RANGE
    )

    @Test fun `zero duration is rejected`() = assertCode(
        validStart(duration = 0),
        SessionRejectionCode.DURATION_OUT_OF_RANGE
    )

    @Test fun `negative duration is rejected`() = assertCode(
        validStart(duration = -1),
        SessionRejectionCode.DURATION_OUT_OF_RANGE
    )

    @Test fun `duration exceeding classification max is rejected`() = assertCode(
        validStart(duration = AppClassification.HOME.maxDurationSeconds + 1),
        SessionRejectionCode.DURATION_EXCEEDS_MAX
    )

    @Test fun `duration at classification max is accepted`() = assertValidCommand(
        validStart(duration = AppClassification.HOME.maxDurationSeconds)
    ) {
        assertEquals(AppClassification.HOME.maxDurationSeconds, it.durationSeconds)
    }

    @Test fun `string expires_at is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("expires_at", "1234")
        }.toString(),
        SessionRejectionCode.EXPIRES_AT_INVALID
    )

    @Test fun `float expires_at is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("expires_at", 1234.5)
        }.toString(),
        SessionRejectionCode.EXPIRES_AT_INVALID
    )

    @Test fun `expires_at in the past is rejected`() = assertCode(
        validStart(expiresAt = 500),
        SessionRejectionCode.EXPIRES_AT_IN_THE_PAST
    )

    @Test fun `expires_at now is rejected`() = assertCode(
        validStart(expiresAt = 1000),
        SessionRejectionCode.EXPIRES_AT_IN_THE_PAST
    )

    @Test fun `expires_at beyond classification max is rejected`() = assertCode(
        validStart(expiresAt = 1000 + AppClassification.HOME.maxDurationSeconds + 1),
        SessionRejectionCode.EXPIRES_AT_EXCEEDS_MAX
    )

    @Test fun `default duration plus now is bounded by classification max`() = assertValidCommand(
        validStart(packageName = "com.media.player") // default 30, max 120
    ) {
        assertEquals(AppClassification.MEDIA.defaultDurationSeconds, it.durationSeconds)
    }

    // --- end/cancel grammar ------------------------------------------------

    @Test fun `end with duration_s is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "end")
            put("package", "com.example.app")
            put("duration_s", 60)
        }.toString(),
        SessionRejectionCode.END_COMMAND_WITH_TEMPORAL_FIELDS
    )

    @Test fun `cancel with expires_at is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "cancel")
            put("package", "com.example.app")
            put("expires_at", 1234)
        }.toString(),
        SessionRejectionCode.END_COMMAND_WITH_TEMPORAL_FIELDS
    )

    // --- reason ------------------------------------------------------------

    @Test fun `reason longer than 120 chars is truncated`() = assertValidCommand(
        validStart(reason = "a".repeat(200))
    ) {
        assertEquals("a".repeat(120), it.reason)
    }

    @Test fun `reason with control chars is sanitized`() = assertValidCommand(
        validStart(reason = "hello\u0000world\nline")
    ) {
        assertEquals("helloworldline", it.reason)
    }

    @Test fun `reason keeps tabs and printable chars`() = assertValidCommand(
        validStart(reason = "tab\there")
    ) {
        assertEquals("tab\there", it.reason)
    }

    @Test fun `non string reason is rejected`() = assertCode(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("reason", 42)
        }.toString(),
        SessionRejectionCode.MALFORMED_REASON
    )

    @Test fun `json null reason is treated as absent`() = assertValidCommand(
        JSONObject().apply {
            put("schema_version", 1)
            put("request_id", "req-1")
            put("action", "start")
            put("package", "com.example.app")
            put("reason", JSONObject.NULL)
        }.toString()
    ) {
        assertEquals(null, it.reason)
    }

    // --- classification specific defaults/max ------------------------------

    @Test fun `utility classification has lower max`() = assertCode(
        validStart(packageName = "com.util.tool", duration = 120),
        SessionRejectionCode.DURATION_EXCEEDS_MAX
    )

    @Test fun `media default duration`() = assertValidCommand(validStart(packageName = "com.media.player")) {
        assertEquals(AppClassification.MEDIA.defaultDurationSeconds, it.durationSeconds)
    }
}
