package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal const val LAUNCHER_PANEL_FRACTION = 0.33f

internal enum class LauncherPanelPresentation { DOCKED, FULLSCREEN }

internal fun panelPresentation(
    compactScreen: Boolean,
    supportsFullscreen: Boolean,
): LauncherPanelPresentation = if (compactScreen && supportsFullscreen) {
    LauncherPanelPresentation.FULLSCREEN
} else {
    LauncherPanelPresentation.DOCKED
}

/**
 * Reserves layout space for the panel while leaving the launcher's wallpaper/scrims full-screen.
 * Clock, Maison and application pages are remeasured inside the remaining area, so no actionable
 * content can sit underneath the panel in either orientation.
 */
@Composable
internal fun LauncherPanelLayout(
    panelVisible: Boolean,
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    presentation: LauncherPanelPresentation = LauncherPanelPresentation.DOCKED,
) {
    BoxWithConstraints(modifier = modifier) {
        val fullscreen = presentation == LauncherPanelPresentation.FULLSCREEN
        val landscape = maxWidth > maxHeight
        val reservedWidth = animateDpAsState(
            targetValue = if (panelVisible && landscape && !fullscreen) maxWidth * LAUNCHER_PANEL_FRACTION else maxWidth * 0f,
            animationSpec = tween(500),
            label = "panelReservedWidth",
        )
        val reservedHeight = animateDpAsState(
            targetValue = if (panelVisible && !landscape && !fullscreen) maxHeight * LAUNCHER_PANEL_FRACTION else maxHeight * 0f,
            animationSpec = tween(500),
            label = "panelReservedHeight",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = reservedWidth.value, bottom = reservedHeight.value),
        ) {
            content()
        }

        if (fullscreen) {
            AnimatedVisibility(
                visible = panelVisible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    panel()
                }
            }
        } else if (landscape) {
            AnimatedVisibility(
                visible = panelVisible,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(LAUNCHER_PANEL_FRACTION),
                enter = slideInHorizontally(tween(500)) { width -> width },
                exit = slideOutHorizontally(tween(500)) { width -> width },
            ) {
                panel()
            }
        } else {
            AnimatedVisibility(
                visible = panelVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(LAUNCHER_PANEL_FRACTION),
                enter = slideInVertically(tween(500)) { height -> height },
                exit = slideOutVertically(tween(500)) { height -> height },
            ) {
                panel()
            }
        }
    }
}
