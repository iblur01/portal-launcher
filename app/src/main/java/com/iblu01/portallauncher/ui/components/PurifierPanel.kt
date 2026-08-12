package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalSegmentedSelector
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

private enum class PurifierMode(val preset: String?, val labelRes: Int, val icon: ImageVector) {
    AUTO("auto", R.string.purifier_mode_auto, Icons.Outlined.AutoMode),
    SLEEP("sleep", R.string.purifier_mode_sleep, Icons.Outlined.Bedtime),
    MANUAL("manual", R.string.purifier_mode_manual, Icons.Outlined.Tune),
    PET("pet", R.string.purifier_mode_pet, Icons.Outlined.Pets),
    OFF(null, R.string.purifier_mode_off, Icons.Outlined.PowerSettingsNew),
}

internal enum class PurifierMetricKind { CO2, PM25, PM10, VOC, AQI, FILTER, OTHER }
internal enum class PurifierQuality { GOOD, FAIR, POOR, UNKNOWN }

internal data class PurifierMetric(
    val label: String,
    val value: Float?,
    val displayValue: String,
    val unit: String,
    val kind: PurifierMetricKind,
    val quality: PurifierQuality,
)

internal fun purifierMetric(detail: PillDetail, entity: HaEntity?): PurifierMetric {
    val id = (entity?.entityId ?: detail.entityId).lowercase()
    val label = detail.label.lowercase()
    val deviceClass = entity?.deviceClass.orEmpty()
    val kind = when {
        deviceClass == "carbon_dioxide" || "co2" in id || "co₂" in label -> PurifierMetricKind.CO2
        deviceClass == "pm25" || "pm2" in id || "pm2" in label -> PurifierMetricKind.PM25
        deviceClass == "pm10" || "pm10" in id || "pm10" in label -> PurifierMetricKind.PM10
        deviceClass == "volatile_organic_compounds" || "voc" in id || "tvoc" in id || "cov" in label -> PurifierMetricKind.VOC
        deviceClass == "aqi" || "aqi" in id || "qualit" in id -> PurifierMetricKind.AQI
        "filter" in id || "filtre" in id || "filter" in label || "filtre" in label -> PurifierMetricKind.FILTER
        else -> PurifierMetricKind.OTHER
    }
    val rawState = entity?.state ?: detail.value
    val value = rawState.replace(',', '.').let { Regex("-?\\d+(?:\\.\\d+)?").find(it)?.value?.toFloatOrNull() }
    val unit = entity?.attributes?.optString("unit_of_measurement")?.takeIf(String::isNotBlank)
        ?: detail.value.replace(Regex("^[\\s-]*\\d+(?:[.,]\\d+)?\\s*"), "").trim()
    val displayValue = value?.let { if (it % 1f == 0f) it.roundToInt().toString() else String.format("%.1f", it) }
        ?: rawState.ifBlank { "—" }
    return PurifierMetric(detail.label, value, displayValue, unit, kind, purifierQuality(kind, value))
}

internal fun purifierQuality(kind: PurifierMetricKind, value: Float?): PurifierQuality {
    value ?: return PurifierQuality.UNKNOWN
    return when (kind) {
        PurifierMetricKind.CO2 -> when { value <= 800 -> PurifierQuality.GOOD; value <= 1_000 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.PM25 -> when { value <= 10 -> PurifierQuality.GOOD; value <= 25 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.PM10 -> when { value <= 20 -> PurifierQuality.GOOD; value <= 50 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.VOC -> when { value <= 400 -> PurifierQuality.GOOD; value <= 800 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.AQI -> when { value <= 50 -> PurifierQuality.GOOD; value <= 100 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.FILTER -> when { value >= 60 -> PurifierQuality.GOOD; value >= 25 -> PurifierQuality.FAIR; else -> PurifierQuality.POOR }
        PurifierMetricKind.OTHER -> PurifierQuality.UNKNOWN
    }
}

@Composable
fun PurifierActions(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    val metrics = chip.details.map { detail -> purifierMetric(detail, detail.entityId.takeIf(String::isNotBlank)?.let { rememberEntity(it) }) }
    val overallQuality = metrics.map(PurifierMetric::quality).maxByOrNull(::qualityRank) ?: PurifierQuality.UNKNOWN
    var detailsVisible by remember(chip.entityId) { mutableStateOf(false) }

    if (detailsVisible) {
        PurifierDetailsPanel(metrics, overallQuality, onBack = { detailsVisible = false }, modifier)
        return
    }

    val running = entity?.state?.equals("on", true) == true
    val currentPreset = entity?.attributes?.optString("preset_mode")?.takeIf { it.isNotBlank() }
    val currentMode = when {
        !running -> PurifierMode.OFF
        currentPreset != null -> PurifierMode.entries.firstOrNull { it.preset == currentPreset.lowercase() } ?: PurifierMode.MANUAL
        else -> PurifierMode.MANUAL
    }
    var optimisticMode by remember(chip.entityId) { mutableStateOf<PurifierMode?>(null) }
    LaunchedEffect(currentMode, optimisticMode) {
        val pending = optimisticMode ?: return@LaunchedEffect
        if (currentMode == pending) optimisticMode = null else {
            kotlinx.coroutines.delay(5000)
            optimisticMode = null
        }
    }

    val purifierLabels = PurifierMode.entries.associateWith { stringResource(it.labelRes) }
    val summary: @Composable (Modifier) -> Unit = { summaryModifier ->
        Column(summaryModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            QualitySummary(overallQuality)
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                CircleActionButton(Icons.Outlined.Info, stringResource(R.string.purifier_details_open), { detailsVisible = true })
            }
        }
    }
    val selector: @Composable (Modifier) -> Unit = { selectorModifier ->
        BoxWithConstraints(selectorModifier, contentAlignment = Alignment.Center) {
            val ratio = 96f / 240f
            val selectorHeight = minOf(maxHeight, maxWidth / ratio, 310.dp)
            val selectorWidth = selectorHeight * ratio
            val options = PurifierMode.entries.toList()
            VerticalSegmentedSelector(
                options = options, selected = optimisticMode ?: currentMode,
                onSelect = { mode -> if (mode != currentMode) {
                    optimisticMode = mode
                    if (mode == PurifierMode.OFF) callService("fan", "turn_off", chip.entityId)
                    else callService("fan", "set_preset_mode", chip.entityId, mapOf("preset_mode" to mode.preset!!))
                } },
                label = { purifierLabels.getValue(it) }, icon = { it.icon }, accent = AppleColors.active,
                isNeutral = { it == PurifierMode.OFF }, enabled = entity != null,
                segmentHeight = selectorHeight / options.size, segmentPadding = 4.dp,
                modifier = Modifier.size(selectorWidth, selectorHeight),
            )
        }
    }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        AdaptivePanelSplit(modifier, primary = { selector(it) }, secondary = { summary(it) })
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            QualitySummary(overallQuality, Modifier.align(Alignment.Center))
            if (metrics.isNotEmpty()) {
                CircleActionButton(
                    icon = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.purifier_details_open),
                    onClick = { detailsVisible = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        selector(Modifier.fillMaxWidth(0.54f).weight(1f))
    }
}

@Composable
private fun PurifierDetailsPanel(
    metrics: List<PurifierMetric>,
    overallQuality: PurifierQuality,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.purifier_details_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.purifier_details_title), style = AppleTypography.titleLarge, color = AppleColors.primary)
                Text(qualityLabel(overallQuality), style = AppleTypography.bodySmall, color = qualityColor(overallQuality))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (metrics.isEmpty()) {
            PanelUnavailable(stringResource(R.string.purifier_details_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(2).forEach { rowMetrics ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowMetrics.forEach { metric -> MetricCard(metric, Modifier.weight(1f)) }
                        if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QualitySummary(quality: PurifierQuality, modifier: Modifier = Modifier) {
    val color = qualityColor(quality)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Air, null, tint = color, modifier = Modifier.size(25.dp))
        }
        Column {
            Text(stringResource(R.string.purifier_air_quality), style = AppleTypography.bodySmall, color = AppleColors.secondary)
            Text(qualityLabel(quality), style = AppleTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
    }
}

@Composable
private fun MetricCard(metric: PurifierMetric, modifier: Modifier = Modifier) {
    val color = qualityColor(metric.quality)
    val trend = when {
        metric.quality == PurifierQuality.FAIR -> Icons.Outlined.TrendingFlat
        metric.kind == PurifierMetricKind.FILTER && metric.quality == PurifierQuality.GOOD -> Icons.Outlined.TrendingUp
        metric.kind == PurifierMetricKind.FILTER -> Icons.Outlined.TrendingDown
        metric.quality == PurifierQuality.GOOD -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.TrendingUp
    }
    Column(
        modifier.clip(AppleShapes.card).background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, color.copy(alpha = 0.38f), AppleShapes.card)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(metric.label, style = AppleTypography.bodySmall, color = AppleColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.displayValue, style = AppleTypography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold), color = AppleColors.primary)
            if (metric.unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(metric.unit, style = AppleTypography.labelSmall, color = AppleColors.secondary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(trend, null, tint = color, modifier = Modifier.size(17.dp))
            Text(qualityLabel(metric.quality), style = AppleTypography.labelSmall.copy(fontWeight = FontWeight.Medium), color = color)
        }
    }
}

@Composable
private fun CircleActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = AppleColors.primary, modifier = Modifier.size(23.dp))
    }
}

private fun qualityRank(quality: PurifierQuality): Int = when (quality) {
    PurifierQuality.UNKNOWN -> 0
    PurifierQuality.GOOD -> 1
    PurifierQuality.FAIR -> 2
    PurifierQuality.POOR -> 3
}

@Composable
private fun qualityLabel(quality: PurifierQuality): String = stringResource(when (quality) {
    PurifierQuality.GOOD -> R.string.purifier_quality_good
    PurifierQuality.FAIR -> R.string.purifier_quality_fair
    PurifierQuality.POOR -> R.string.purifier_quality_poor
    PurifierQuality.UNKNOWN -> R.string.purifier_quality_unknown
})

private fun qualityColor(quality: PurifierQuality): Color = when (quality) {
    PurifierQuality.GOOD -> AppleColors.active
    PurifierQuality.FAIR -> AppleColors.warning
    PurifierQuality.POOR -> AppleColors.error
    PurifierQuality.UNKNOWN -> AppleColors.secondary
}
