package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Visual density for [PortalThreeWayControl]. */
enum class ThreeWayControlSize { Compact, Regular }

/**
 * Shared two/three-action capsule used by media playback and directional devices such as covers.
 *
 * This component owns presentation only: callers provide icons, accessible descriptions and
 * callbacks. Optional side labels create the cover variation without coupling it to a domain.
 */
@Composable
fun PortalThreeWayControl(
    leadingIcon: ImageVector,
    leadingContentDescription: String,
    onLeadingClick: () -> Unit,
    centerIcon: ImageVector?,
    centerContentDescription: String?,
    onCenterClick: (() -> Unit)?,
    trailingIcon: ImageVector,
    trailingContentDescription: String,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    trailingLabel: String? = null,
    size: ThreeWayControlSize = ThreeWayControlSize.Regular,
    leadingEnabled: Boolean = true,
    centerEnabled: Boolean = true,
    trailingEnabled: Boolean = true,
) {
    val metrics = when (size) {
        ThreeWayControlSize.Compact -> ThreeWayMetrics(
            sideButton = 40.dp, centerButton = 50.dp, sideIcon = 22.dp,
            centerIcon = 28.dp, horizontalPadding = 10.dp, verticalPadding = 6.dp, gap = 14.dp,
        )
        ThreeWayControlSize.Regular -> ThreeWayMetrics(
            sideButton = 46.dp, centerButton = 58.dp, sideIcon = 26.dp,
            centerIcon = 32.dp, horizontalPadding = 14.dp, verticalPadding = 8.dp, gap = 18.dp,
        )
    }

    Row(
        modifier = modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideAction(
            icon = leadingIcon,
            contentDescription = leadingContentDescription,
            label = leadingLabel,
            onClick = onLeadingClick,
            buttonSize = metrics.sideButton,
            iconSize = metrics.sideIcon,
            enabled = leadingEnabled,
        )
        if (centerIcon != null && centerContentDescription != null && onCenterClick != null) {
            IconButton(
                onClick = onCenterClick,
                enabled = centerEnabled,
                modifier = Modifier
                    .size(metrics.centerButton)
                    .background(Color.White, CircleShape),
            ) {
                Icon(
                    centerIcon,
                    contentDescription = centerContentDescription,
                    tint = Color.Black,
                    modifier = Modifier.size(metrics.centerIcon),
                )
            }
        }
        SideAction(
            icon = trailingIcon,
            contentDescription = trailingContentDescription,
            label = trailingLabel,
            onClick = onTrailingClick,
            buttonSize = metrics.sideButton,
            iconSize = metrics.sideIcon,
            enabled = trailingEnabled,
        )
    }
}

@Composable
private fun SideAction(
    icon: ImageVector,
    contentDescription: String,
    label: String?,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    enabled: Boolean,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(buttonSize)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-2).dp),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) AppleColors.primary else AppleColors.tertiary,
                modifier = Modifier.size(iconSize),
            )
            if (label != null) {
                Text(
                    text = label,
                    color = AppleColors.primary,
                    style = AppleTypography.bodySmall.copy(fontSize = 9.sp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class ThreeWayMetrics(
    val sideButton: Dp,
    val centerButton: Dp,
    val sideIcon: Dp,
    val centerIcon: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val gap: Dp,
)
