package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.model.PillDetail

/**
 * A compact glance chip (top tier): icon + short label + a value, e.g. "Climate 20–22°".
 *
 * No `@Immutable` needed: under Compose strong-skipping (Kotlin 2.0.20) an equal chip — even with
 * its `List<PillDetail>` — skips recomposition on data-class `equals`, so an unrelated HA push no
 * longer storms open controls (the alarm keypad). Validated headlessly by [ChipSkippabilityTest].
 */
data class LauncherChip(
    val id: String,
    val icon: String,
    val label: String,
    val value: String,
    val state: String = "info",
    val progress: Float = 0f,
    val entityId: String = "",
    val priority: Int = 0,
    val stale: Boolean = false,
    val details: List<PillDetail> = emptyList(),
    /** Accessory kind, drives per-accessory tap behaviour and which control panel opens. */
    val kind: PillKind = PillKind.GENERIC,
    /** Battery percentage resolved from the entity itself or one of its related HA sensors. */
    val batteryPercent: Int? = null,
)
