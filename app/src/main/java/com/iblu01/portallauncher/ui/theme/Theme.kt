package com.iblu01.portallauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val PortalColorScheme = darkColorScheme(
    primary = AppleColors.accent,
    onPrimary = AppleColors.primary,
    background = AppleColors.background,
    onBackground = AppleColors.primary,
    surface = AppleColors.elevated,
    onSurface = AppleColors.primary,
    surfaceVariant = AppleColors.elevated,
    onSurfaceVariant = AppleColors.secondary,
    error = AppleColors.error
)

/**
 * Wraps [MaterialTheme] with the Apple token set. The launcher is always dark
 * (the ambient display never shows a light theme), so [isSystemInDarkTheme] is
 * only read to keep previews honest.
 */
@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    CompositionLocalProvider(LocalUiScale provides rememberUiScale()) {
        MaterialTheme(
            colorScheme = PortalColorScheme,
            typography = AppleTypography,
            content = content
        )
    }
}
