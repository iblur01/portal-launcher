package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.blurCompat

/** One launchable app surfaced in the overlay. */
data class AppEntry(val label: String, val packageName: String, val activityName: String)

/**
 * The launcher's surface menu: long-press the clock, or an empty cell of the app grid.
 *
 * A blurred backdrop with a centered frosted panel. Tapping the backdrop or swiping the panel down
 * dismisses it. The app drawer that used to live here is now the app pages; what is left is the
 * chrome that belongs to the surface itself rather than to any icon.
 */
@Composable
fun QuickActionsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlayground: () -> Unit,
    onSetWallpaper: () -> Unit = {},
    onAddWidget: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {},
    /** When false, the launcher is not the selected home app — half its features cannot work. */
    isDefaultHome: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppleMotion.spring()) + scaleIn(initialScale = 0.92f, animationSpec = AppleMotion.spring()),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150))
    ) {
        // Both rectangles in root coordinates: the backdrop decides whether a tap was outside the
        // panel rather than relying on the panel to swallow it. Overlapping siblings both receive a
        // gesture, and consuming it from the panel is order-dependent — this is not.
        var backdropRect by remember { mutableStateOf(Rect.Zero) }
        var panelRect by remember { mutableStateOf(Rect.Zero) }

        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred, dimmed backdrop — a SIBLING of the panel so the blur never
            // bleeds onto the menu. Tapping outside the panel dismisses.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        backdropRect = Rect(it.positionInRoot(), it.size.toSize())
                    }
                    .blurCompat(40.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(panelRect, backdropRect) {
                        detectTapGestures { position ->
                            if (!panelRect.contains(backdropRect.topLeft + position)) onDismiss()
                        }
                    }
            )

            // Panel layer, drawn on top and never blurred. Empty area lets taps fall
            // through to the backdrop below.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(AppleShapes.panel)
                    .background(AppleColors.elevated.copy(alpha = 0.96f), AppleShapes.panel)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                    .testTag("quickActionsPanel")
                    .onGloballyPositioned { panelRect = Rect(it.positionInRoot(), it.size.toSize()) }
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { if (drag > 120f) onDismiss(); drag = 0f },
                            onVerticalDrag = { _, dy -> if (dy > 0) drag += dy }
                        )
                    }
                    .padding(8.dp)
            ) {
                if (!isDefaultHome) {
                    // Without the home role there are no app shortcuts and no pinned-shortcut
                    // requests, so this is the first thing to offer, not a footnote.
                    MenuRow(Icons.Outlined.Home, stringResource(R.string.quick_actions_set_default_home)) {
                        onDismiss(); onOpenHomeSettings()
                    }
                    MenuDivider()
                }
                MenuRow(Icons.Outlined.Widgets, stringResource(R.string.quick_actions_add_widget)) { onDismiss(); onAddWidget() }
                MenuDivider()
                MenuRow(Icons.Outlined.Wallpaper, stringResource(R.string.quick_actions_wallpaper)) { onDismiss(); onSetWallpaper() }
                MenuDivider()
                MenuRow(Icons.Outlined.Settings, stringResource(R.string.quick_actions_settings)) { onDismiss(); onSettings() }
                MenuDivider()
                MenuRow(Icons.Outlined.Tune, stringResource(R.string.quick_actions_playground)) { onDismiss(); onOpenPlayground() }
            }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AppleColors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 0.5.dp, max = 0.5.dp)
            .background(AppleColors.quaternary)
    )
}
