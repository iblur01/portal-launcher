package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.PillKind

/** Central, locale-independent definition of incidents allowed to overtake user pins. */
object PillAlertPolicy {
    private val criticalSafetyStates = setOf("triggered", "on", "open", "detected", "unsafe")
    private val criticalAlarmStates = criticalSafetyStates + "pending"

    fun evaluate(pill: ResolvedPill, occurredAtMs: Long = 0L): PillAlert? {
        val state = pill.chip.deviceState?.trim()?.lowercase().orEmpty()
        val severity = when (pill.chip.kind) {
            PillKind.SAFETY -> if (state in safetyAlertStates(pill)) AlertSeverity.CRITICAL else null
            PillKind.SIREN -> if (state == "on") AlertSeverity.CRITICAL else null
            PillKind.LOCK -> when (state) {
                "jammed" -> AlertSeverity.CRITICAL
                "unlocked", "open" -> AlertSeverity.HIGH
                else -> null
            }
            else -> null
        } ?: return null

        val incidents = pill.sourceEntityIds.ifEmpty { setOf(pill.ref.stableKey) }
        return PillAlert(severity, occurredAtMs, incidents)
    }

    private fun safetyAlertStates(pill: ResolvedPill): Set<String> =
        if (pill.sourceEntityIds.any(::isAlarmControlPanel) ||
            (pill.ref as? PillRef.Device)?.entityId?.let(::isAlarmControlPanel) == true
        ) {
            criticalAlarmStates
        } else {
            criticalSafetyStates
        }

    private fun isAlarmControlPanel(entityId: String): Boolean =
        entityId.substringBefore('.') == "alarm_control_panel"

    fun comparator(): Comparator<ResolvedPill> =
        compareByDescending<ResolvedPill> { it.alert?.severity?.rank ?: Int.MIN_VALUE }
            .thenByDescending { it.alert?.occurredAtMs ?: 0L }
            .thenBy { it.ref.stableKey }
}
