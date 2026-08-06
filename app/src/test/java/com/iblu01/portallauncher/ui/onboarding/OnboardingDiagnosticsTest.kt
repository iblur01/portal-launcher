package com.iblu01.portallauncher.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingDiagnosticsTest {

    @Test
    fun `a refused token reads as unauthorized`() {
        assertEquals(OnboardingError.UNAUTHORIZED, OnboardingDiagnostics.classifyHaFailure(401, null))
        assertEquals(OnboardingError.UNAUTHORIZED, OnboardingDiagnostics.classifyHaFailure(403, null))
    }

    @Test
    fun `a wrong address reads as unreachable`() {
        assertEquals(OnboardingError.HOST_UNREACHABLE, OnboardingDiagnostics.classifyHaFailure(404, null))
        assertEquals(
            OnboardingError.HOST_UNREACHABLE,
            OnboardingDiagnostics.classifyHaFailure(-1, "Unable to resolve host \"homeassistant.local\""),
        )
    }

    @Test
    fun `transport failures are told apart`() {
        assertEquals(OnboardingError.TIMEOUT, OnboardingDiagnostics.classifyHaFailure(-1, "connect timed out"))
        assertEquals(
            OnboardingError.INVALID_CERTIFICATE,
            OnboardingDiagnostics.classifyHaFailure(-1, "Trust anchor for certification path not found"),
        )
    }

    @Test
    fun `a server error is an invalid response, not a bad token`() {
        assertEquals(OnboardingError.INVALID_RESPONSE, OnboardingDiagnostics.classifyHaFailure(500, null))
    }

    @Test
    fun `entities are counted and bucketed`() {
        val json = """
            [
              {"entity_id":"light.salon"},
              {"entity_id":"light.cuisine"},
              {"entity_id":"cover.garage"},
              {"entity_id":"media_player.tv"},
              {"entity_id":"alarm_control_panel.maison"},
              {"entity_id":"weather.home"},
              {"entity_id":"sensor.cpu"}
            ]
        """.trimIndent()

        val summary = OnboardingDiagnostics.summarize(json)

        assertEquals(7, summary.entityCount)
        assertEquals(2, summary.breakdown[SummaryCategory.LIGHTS])
        assertEquals(1, summary.breakdown[SummaryCategory.OPENINGS])
        assertEquals(1, summary.breakdown[SummaryCategory.MEDIA])
        assertEquals(1, summary.breakdown[SummaryCategory.ALARM])
        assertEquals(1, summary.breakdown[SummaryCategory.WEATHER])
        // "Other" is counted in the total but never shown as its own line.
        assertEquals(null, summary.breakdown[SummaryCategory.OTHER])
    }

    @Test
    fun `an unusable payload summarises to nothing rather than throwing`() {
        assertEquals(0, OnboardingDiagnostics.summarize("not json").entityCount)
        assertEquals(0, OnboardingDiagnostics.summarize(null).entityCount)
    }

    @Test
    fun `broker failures map to their own causes`() {
        assertEquals(
            OnboardingError.BROKER_BAD_CREDENTIALS,
            OnboardingDiagnostics.classifyMqttFailure(OnboardingDiagnostics.REASON_BAD_CREDENTIALS, null),
        )
        assertEquals(
            OnboardingError.BROKER_UNREACHABLE,
            OnboardingDiagnostics.classifyMqttFailure(OnboardingDiagnostics.REASON_SERVER_CONNECT_ERROR, null),
        )
        assertEquals(
            OnboardingError.TIMEOUT,
            OnboardingDiagnostics.classifyMqttFailure(OnboardingDiagnostics.REASON_CLIENT_TIMEOUT, null),
        )
        assertEquals(
            OnboardingError.BROKER_UNREACHABLE,
            OnboardingDiagnostics.classifyMqttFailure(0, "Failed to connect to /192.168.1.20:1883"),
        )
    }

    @Test
    fun `an authorization failure names the operation that was refused`() {
        val code = OnboardingDiagnostics.REASON_NOT_AUTHORIZED
        assertEquals(
            OnboardingError.BROKER_REFUSED,
            OnboardingDiagnostics.classifyMqttFailure(code, null, TestPhase.CONNECTING_BROKER),
        )
        assertEquals(
            OnboardingError.PUBLISH_FORBIDDEN,
            OnboardingDiagnostics.classifyMqttFailure(code, null, TestPhase.PUBLISHING_DEVICE),
        )
        assertEquals(
            OnboardingError.SUBSCRIBE_FORBIDDEN,
            OnboardingDiagnostics.classifyMqttFailure(code, null, TestPhase.VERIFYING_ROUNDTRIP),
        )
    }
}
