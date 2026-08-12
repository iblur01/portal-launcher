package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PillAlertPolicyTest {
    private fun alarm(state: String, entityId: String = "alarm_control_panel.home") = ResolvedPill(
        ref = PillRef.Device(entityId),
        chip = LauncherChip(
            id = entityId,
            icon = "shield",
            label = "Arbitrary display label",
            value = state,
            state = "active",
            entityId = entityId,
            kind = PillKind.SAFETY,
            deviceState = state,
        ),
        sourceEntityIds = setOf(entityId),
    )

    @Test fun `alarm pending is a critical alert independent of display label`() {
        val alert = PillAlertPolicy.evaluate(alarm("pending"), occurredAtMs = 42)

        assertEquals(AlertSeverity.CRITICAL, alert?.severity)
        assertEquals(42L, alert?.occurredAtMs)
        assertEquals(setOf("alarm_control_panel.home"), alert?.incidentEntityIds)
    }

    @Test fun `calm alarm state is not an alert`() {
        assertNull(PillAlertPolicy.evaluate(alarm("disarmed")))
    }

    @Test fun `alert comparator orders severity then recency then stable key`() {
        fun alerted(id: String, severity: AlertSeverity, occurredAtMs: Long): ResolvedPill {
            val base = alarm("triggered", id)
            return base.copy(alert = PillAlert(severity, occurredAtMs, setOf(id)))
        }
        val highRecent = alerted("alarm_control_panel.high", AlertSeverity.HIGH, 300)
        val criticalOld = alerted("alarm_control_panel.old", AlertSeverity.CRITICAL, 100)
        val criticalB = alerted("alarm_control_panel.b", AlertSeverity.CRITICAL, 200)
        val criticalA = alerted("alarm_control_panel.a", AlertSeverity.CRITICAL, 200)

        assertEquals(
            listOf(criticalA.ref, criticalB.ref, criticalOld.ref, highRecent.ref),
            listOf(highRecent, criticalOld, criticalB, criticalA)
                .sortedWith(PillAlertPolicy.comparator())
                .map { it.ref },
        )
    }
}
