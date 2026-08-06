package com.iblu01.portallauncher.ui.onboarding

import org.json.JSONArray

/**
 * Turns raw connection outcomes into the handful of cases the UI knows how to explain.
 *
 * Pure on purpose: classification is where a confusing error screen comes from, so it is tested
 * directly. Nothing here ever carries a token or a password into its result.
 */
object OnboardingDiagnostics {

    /** Maps an HTTP status (or a transport failure, status -1) onto an actionable error. */
    fun classifyHaFailure(statusCode: Int, failureMessage: String?): OnboardingError {
        if (statusCode in 200..299) return OnboardingError.UNKNOWN
        return when {
            statusCode == 401 || statusCode == 403 -> OnboardingError.UNAUTHORIZED
            statusCode == 404 -> OnboardingError.HOST_UNREACHABLE
            statusCode >= 400 -> OnboardingError.INVALID_RESPONSE
            else -> classifyTransport(failureMessage)
        }
    }

    private fun classifyTransport(message: String?): OnboardingError {
        val text = message.orEmpty().lowercase()
        return when {
            text.contains("timeout") || text.contains("timed out") -> OnboardingError.TIMEOUT
            text.contains("certificate") || text.contains("ssl") || text.contains("trust anchor") ->
                OnboardingError.INVALID_CERTIFICATE
            text.contains("unable to resolve host") || text.contains("unknownhost") ||
                text.contains("econnrefused") || text.contains("connect") || text.contains("network") ->
                OnboardingError.HOST_UNREACHABLE
            text.isBlank() -> OnboardingError.HOST_UNREACHABLE
            else -> OnboardingError.UNKNOWN
        }
    }

    /**
     * Counts what `/api/states` returned, so the success screen can say what was actually found
     * instead of "connected".
     */
    fun summarize(statesJson: String?): TestSummary {
        val array = runCatching { JSONArray(statesJson ?: "[]") }.getOrNull()
            ?: return TestSummary()
        val breakdown = linkedMapOf<SummaryCategory, Int>()
        var total = 0
        for (i in 0 until array.length()) {
            val entityId = array.optJSONObject(i)?.optString("entity_id").orEmpty()
            if (entityId.isBlank()) continue
            total++
            val category = categoryOf(entityId.substringBefore('.'))
            breakdown[category] = (breakdown[category] ?: 0) + 1
        }
        return TestSummary(
            entityCount = total,
            breakdown = breakdown.filterKeys { it != SummaryCategory.OTHER },
        )
    }

    /** Maps a Home Assistant domain onto the coarse buckets shown on the success screen. */
    fun categoryOf(domain: String): SummaryCategory = when (domain) {
        "light" -> SummaryCategory.LIGHTS
        "cover", "binary_sensor", "lock" -> SummaryCategory.OPENINGS
        "media_player" -> SummaryCategory.MEDIA
        "alarm_control_panel" -> SummaryCategory.ALARM
        "weather" -> SummaryCategory.WEATHER
        "climate" -> SummaryCategory.CLIMATE
        else -> SummaryCategory.OTHER
    }

    /**
     * Maps a Paho failure onto an actionable error.
     *
     * [reasonCode] is `MqttException.getReasonCode()`; 0 means "no MQTT-level code", i.e. the
     * failure came from the socket and only [failureMessage] is available.
     */
    fun classifyMqttFailure(
        reasonCode: Int,
        failureMessage: String?,
        phase: TestPhase = TestPhase.CONNECTING_BROKER,
    ): OnboardingError = when (reasonCode) {
        REASON_BROKER_UNAVAILABLE, REASON_SERVER_CONNECT_ERROR -> OnboardingError.BROKER_UNREACHABLE
        REASON_BAD_CREDENTIALS -> OnboardingError.BROKER_BAD_CREDENTIALS
        // "Not authorized" means different things depending on what we were doing: refused at
        // connect, refused a topic once connected.
        REASON_NOT_AUTHORIZED -> when (phase) {
            TestPhase.PUBLISHING_DEVICE -> OnboardingError.PUBLISH_FORBIDDEN
            TestPhase.VERIFYING_ROUNDTRIP -> OnboardingError.SUBSCRIBE_FORBIDDEN
            else -> OnboardingError.BROKER_REFUSED
        }
        REASON_CONNECTION_LOST, REASON_INVALID_CLIENT_ID -> OnboardingError.BROKER_REFUSED
        REASON_CLIENT_TIMEOUT -> OnboardingError.TIMEOUT
        else -> when (classifyTransport(failureMessage)) {
            OnboardingError.HOST_UNREACHABLE -> OnboardingError.BROKER_UNREACHABLE
            OnboardingError.TIMEOUT -> OnboardingError.TIMEOUT
            OnboardingError.INVALID_CERTIFICATE -> OnboardingError.INVALID_CERTIFICATE
            else -> OnboardingError.UNKNOWN
        }
    }

    // MqttException reason codes, spelled out so the mapping reads without the Paho javadoc.
    const val REASON_CLIENT_TIMEOUT = 32000
    const val REASON_CONNECTION_LOST = 32109
    const val REASON_INVALID_CLIENT_ID = 2
    const val REASON_BROKER_UNAVAILABLE = 3
    const val REASON_BAD_CREDENTIALS = 4
    const val REASON_NOT_AUTHORIZED = 5
    const val REASON_SERVER_CONNECT_ERROR = 32103
}
