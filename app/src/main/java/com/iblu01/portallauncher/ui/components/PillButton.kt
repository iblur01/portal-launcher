package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.scaled

/**
 * Reusable pill button. Primary = accent fill; secondary = frosted fill.
 * Scales to 0.96 on press and springs back (via [appleClickable]).
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val fill = if (primary) AppleColors.accent else AppleColors.frostedFill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp.scaled())
            .clip(AppleShapes.pill)
            .background(fill, AppleShapes.pill)
            .then(
                if (primary) Modifier
                else Modifier.border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            )
            .appleClickable(onClick)
            .padding(horizontal = 28.dp.scaled(), vertical = 14.dp.scaled()),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = AppleTypography.titleMedium.copy(fontSize = AppleTypography.titleMedium.fontSize.scaled()),
            textAlign = TextAlign.Center,
            color = if (primary) AppleColors.primary else AppleColors.primary.copy(alpha = 0.8f)
        )
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true, widthDp = 320)
@Composable
private fun PillButtonPreview() {
    PortalTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            PillButton(label = "Enregistrer", onClick = {}, primary = true)
            PillButton(label = "Tester MQTT", onClick = {})
        }
    }
}
