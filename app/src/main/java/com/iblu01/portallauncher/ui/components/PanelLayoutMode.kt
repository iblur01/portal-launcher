package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Content composition, derived from the panel container rather than device orientation. */
internal enum class PanelLayoutMode { VERTICAL, HORIZONTAL }

/** The panel's own bounds decide the composition; the device orientation is intentionally ignored. */
internal fun panelLayoutModeFor(widthDp: Float, heightDp: Float): PanelLayoutMode =
    if (widthDp > heightDp) PanelLayoutMode.HORIZONTAL else PanelLayoutMode.VERTICAL

internal val LocalPanelLayoutMode = staticCompositionLocalOf { PanelLayoutMode.VERTICAL }

/** Two genuine compositions: stacked in a side panel, split in wide/fullscreen containers. */
@Composable
internal fun AdaptivePanelSplit(
    modifier: Modifier = Modifier,
    primaryWeight: Float = 0.52f,
    primary: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
) {
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primary(Modifier.weight(primaryWeight).fillMaxSize())
            secondary(Modifier.weight(1f - primaryWeight).fillMaxSize())
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            primary(Modifier.weight(primaryWeight).fillMaxSize())
            secondary(Modifier.weight(1f - primaryWeight).fillMaxSize())
        }
    }
}
