package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class WasherPhase { PREPARING, WASH, RINSE, SPIN, FINISHED }

data class WasherUiState(
    val running: Boolean,
    val paused: Boolean = false,
    val phase: WasherPhase,
    val progress: Float? = null,
    val remainingLabel: String? = null,
    val program: String? = null,
    val temperature: String? = null,
    val spinSpeed: String? = null,
)

/** Backend-agnostic washer status component used by both real entities and the Playground. */
@Composable
fun WasherStatus(state: WasherUiState, modifier: Modifier = Modifier) {
    val accent = if (state.running && !state.paused) Color(0xFF7C8CFF) else AppleColors.inactive
    val progress = state.progress?.coerceIn(0f, 1f)
    val primary: @Composable (Modifier) -> Unit = { area ->
        Column(area, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            WasherProgress(state, progress, accent)
            Text(phaseLabel(state.phase, state.paused), style = AppleTypography.titleLarge, color = if (state.paused) AppleColors.secondary else accent)
        }
    }
    val secondary: @Composable (Modifier) -> Unit = { area ->
        Column(area, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            WasherTimeline(state.phase, accent)
            Spacer(Modifier.height(20.dp))
            WasherFacts(state)
        }
    }
    AdaptivePanelSplit(modifier = modifier, primaryWeight = 0.44f, primary = primary, secondary = secondary)
}

@Composable
private fun WasherProgress(state: WasherUiState, progress: Float?, accent: Color) {
        Box(Modifier.size(if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) 220.dp else 190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(176.dp)) {
                val stroke = 15.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(AppleColors.quaternary, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (progress != null) drawArc(accent, -90f, progress * 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(phaseIcon(state.phase), null, tint = accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(5.dp))
                Text(
                    progress?.let { "${(it * 100).roundToInt()} %" } ?: "—",
                    style = AppleTypography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium),
                    color = AppleColors.primary,
                )
                state.remainingLabel?.let {
                    Text(it, style = AppleTypography.bodySmall, color = AppleColors.secondary)
                }
            }
        }
}

@Composable
private fun WasherFacts(state: WasherUiState) {
        val facts = listOfNotNull(
            state.program?.let { "Programme" to it },
            state.temperature?.let { "Température" to it },
            state.spinSpeed?.let { "Essorage" to it },
        )
        if (facts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                facts.forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).background(AppleColors.frostedFill, AppleShapes.section)
                            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section).padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(value, style = AppleTypography.bodyLarge, color = AppleColors.primary, maxLines = 1)
                        Text(label, style = AppleTypography.labelSmall, color = AppleColors.tertiary, maxLines = 1)
                    }
                }
            }
        }
}

@Composable
private fun WasherTimeline(current: WasherPhase, accent: Color) {
    val phases = listOf(WasherPhase.WASH, WasherPhase.RINSE, WasherPhase.SPIN, WasherPhase.FINISHED)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        phases.forEach { phase ->
            val reached = phase.ordinal <= current.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.size(40.dp).background(if (reached) accent.copy(alpha = 0.18f) else AppleColors.frostedFill, CircleShape)
                        .border(0.5.dp, if (reached) accent.copy(alpha = 0.5f) else AppleColors.frostedBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(phaseIcon(phase), null, tint = if (reached) accent else AppleColors.tertiary, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.height(6.dp))
                Text(phaseLabel(phase, false), style = AppleTypography.labelSmall.copy(fontSize = 9.sp), color = if (phase == current) AppleColors.primary else AppleColors.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun phaseIcon(phase: WasherPhase): ImageVector = when (phase) {
    WasherPhase.PREPARING, WasherPhase.WASH -> Icons.Outlined.LocalLaundryService
    WasherPhase.RINSE -> Icons.Outlined.WaterDrop
    WasherPhase.SPIN -> Icons.Outlined.Autorenew
    WasherPhase.FINISHED -> Icons.Outlined.CheckCircle
}

private fun phaseLabel(phase: WasherPhase, paused: Boolean): String = when {
    paused -> "En pause"
    phase == WasherPhase.PREPARING -> "Préparation"
    phase == WasherPhase.WASH -> "Lavage"
    phase == WasherPhase.RINSE -> "Rinçage"
    phase == WasherPhase.SPIN -> "Essorage"
    else -> "Terminé"
}

/** Home Assistant adapter; naming heuristics mirror PillPriorityEngine's related sensors. */
@Composable
fun WasherControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val states = LocalHaStates.current
    val entity = states[chip.entityId]
    if (entity.isUnavailable()) { PanelUnavailable(); return }
    val e = entity!!
    val base = e.entityId.substringAfter('.').replace("_machine_state", "").replace("_etat_de_la_machine", "").removeSuffix("_state")
    // Id-pattern lookup over the (quasi-static) id set, then a per-entity read: this scope only
    // subscribes to the entities it actually displays, not to the whole store.
    fun related(vararg tokens: String): HaEntity? = states.entityIds().firstOrNull { id ->
        id != e.entityId && id.substringAfter('.').startsWith(base) && tokens.any(id::contains)
    }?.let { states[it] }
    val phaseRaw = e.attributes.optString("phase").ifBlank { related("cycle", "task_state", "etat_du_cycle")?.state.orEmpty() }.lowercase()
    val phase = when (phaseRaw) {
        "rinse", "ai_rinse" -> WasherPhase.RINSE
        "spin", "ai_spin" -> WasherPhase.SPIN
        "finish", "finished", "done", "complete", "completed" -> WasherPhase.FINISHED
        "prepare", "init", "weight_sensing", "delay_wash" -> WasherPhase.PREPARING
        else -> WasherPhase.WASH
    }
    val progressPercent = listOf("progress", "percentage").firstNotNullOfOrNull { key -> e.attributes.optDouble(key).takeIf { e.attributes.has(key) && !it.isNaN() } }
        ?: related("progress", "progression", "pourcentage")?.state?.toDoubleOrNull()
    val completion = related("completion_time", "end_time", "heure_de_fin")?.state?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val remaining = e.attributes.optString("remaining_time").takeIf(String::isNotBlank) ?: completion?.let {
        val minutes = ceil((it.toEpochMilli() - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(0)
        if (minutes >= 60) "Reste ${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}" else "Reste $minutes min"
    }
    WasherStatus(
        WasherUiState(
            running = e.state.lowercase() in setOf("on", "run", "running", "washing", "active", "paused"),
            paused = e.state.equals("paused", true), phase = phase,
            progress = progressPercent?.div(100.0)?.toFloat() ?: chip.progress.takeIf { it > 0f },
            remainingLabel = remaining,
            program = e.attributes.optString("program").ifBlank { e.attributes.optString("course") }.takeIf(String::isNotBlank),
            temperature = e.attributes.optString("temperature").takeIf(String::isNotBlank)?.let { "$it°" },
            spinSpeed = e.attributes.optString("spin_speed").takeIf(String::isNotBlank)?.let { "$it tr/min" },
        ),
        modifier,
    )
}
