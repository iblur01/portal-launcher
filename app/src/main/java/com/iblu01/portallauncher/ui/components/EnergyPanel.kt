package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/** Live power with an auto-scaling gauge, plus today's energy. */
@Composable
fun EnergyActions(chip: LauncherChip) {
    val powerDetail = chip.details.firstOrNull { it.label == stringResource(R.string.energy_power_label) }
    val energyDetail = chip.details.firstOrNull { it.label == stringResource(R.string.energy_today_label) }
    val entity = powerDetail?.entityId?.let { rememberEntity(it) }
    val watts = entity?.state?.toFloatOrNull() ?: powerDetail?.value?.filter { it.isDigit() }?.toFloatOrNull() ?: 0f

    var gaugeMax by remember { mutableFloatStateOf(3000f) }
    if (watts > gaugeMax) gaugeMax = watts
    val fraction by animateFloatAsState((watts / gaugeMax).coerceIn(0f, 1f), AppleMotion.spring(), label = "energyGauge")

    Column(Modifier.fillMaxWidth()) {
        Text("${watts.roundToInt()} W", style = AppleTypography.displayLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.SemiBold), color = AppleColors.primary)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(AppleColors.frostedFill),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(AppleColors.accent))
        }
        Spacer(Modifier.height(6.dp))
        Text("max ${gaugeMax.roundToInt()} W", style = AppleTypography.bodySmall.copy(fontSize = 12.sp), color = AppleColors.tertiary)
        Spacer(Modifier.height(18.dp))
        if (energyDetail != null) PanelDetailRow(PillDetail(stringResource(R.string.energy_today_label), energyDetail.value))
    }
}
