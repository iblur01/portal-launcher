package com.iblu01.portallauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Device-size scale factor for chrome that competes with screen space (pill tray, side panel,
 * pill buttons) — NOT the clock header, which stays full-size by design on every device. 1f on
 * the reference 10.1" Portal (smallest width ~800dp); shrinks toward [MIN_UI_SCALE] on the 8"
 * Portal Mini so pills/panel don't dominate the smaller screen.
 */
val LocalUiScale = compositionLocalOf { 1f }

private const val REFERENCE_SMALLEST_WIDTH_DP = 800f
private const val MIN_UI_SCALE = 0.8f
private const val MAX_UI_SCALE = 1f

@Composable
fun rememberUiScale(): Float {
    val configuration = LocalConfiguration.current
    val smallestWidthDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    return (smallestWidthDp / REFERENCE_SMALLEST_WIDTH_DP).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
}

/** Scales [this] by the current [LocalUiScale] factor. */
@Composable
fun Dp.scaled(): Dp = this * LocalUiScale.current

/** Scales [this] by the current [LocalUiScale] factor. */
@Composable
fun TextUnit.scaled(): TextUnit = (this.value * LocalUiScale.current).sp
