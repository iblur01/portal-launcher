package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/** HomeKit tile corners scale with the tile's shortest axis. */
private val AccessoryShape: Shape = RoundedCornerShape(percent = 30)

/**
 * A single HomeKit-style accessory tile: an icon on the left, name and an optional status line.
 * Bright (white / [activeColor]) when [on], neutral dark when off — tap toggles, long-press opens
 * detail. An [warning] flag drops the ⚠ badge on the icon, à la "Bijwerken…" / "Geen reactie".
 *
 * @param icon left glyph — its own [accent] colour when on, neutral when off.
 * @param activeColor the lit background (Home uses white; override to theme it).
 */
@Composable
fun AccessoryTile(
    title: String,
    icon: ImageVector,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = AppleColors.accent,
    activeColor: Color = Color.White,
    warning: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 66.dp,
    onLongPress: (() -> Unit)? = null,
) {
    val contentScale = (height / 66.dp).coerceIn(0.7f, 1.5f)
    val background by animateColorAsState(
        when {
            !enabled -> AppleColors.frostedFill
            on -> activeColor
            else -> Color.White.copy(alpha = 0.10f)
        },
        tween(220), label = "tileBg",
    )
    val onContent = if (on && enabled) contentColorOn(activeColor) else AppleColors.primary
    val titleColor = onContent
    val subtitleColor = if (on && enabled) onContent.copy(alpha = 0.6f) else AppleColors.secondary
    // Icon lives in a circle. Off → neutral disc, light glyph. On → the accent fills the disc and
    // the glyph goes white — the "inverted light" HomeKit look.
    val iconCircle by animateColorAsState(
        when {
            !enabled -> Color.White.copy(alpha = 0.08f)
            on -> accent
            else -> Color.White.copy(alpha = 0.14f)
        },
        tween(220), label = "tileIconCircle",
    )
    val iconTint = when {
        !enabled -> AppleColors.tertiary
        on -> Color.White
        else -> AppleColors.primary
    }

    Row(
        modifier
            .height(height)
            .clip(AccessoryShape)
            .background(background, AccessoryShape)
            .then(
                if (enabled) Modifier.appleClickable({ onToggle(!on) }, onLongPress)
                else Modifier,
            )
            .padding(horizontal = 12.dp * contentScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp * contentScale)) {
            Box(
                Modifier.matchParentSize().clip(CircleShape).background(iconCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(21.dp * contentScale))
            }
            // Alert badge rides above the circle's top-right, protruding onto the tile.
            if (warning) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.accessory_warning_desc),
                    tint = onContent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp * contentScale, y = (-5).dp * contentScale)
                        .size(16.dp * contentScale),
                )
            }
        }
        Spacer(Modifier.width(11.dp * contentScale))
        Column {
            Text(
                title,
                style = AppleTypography.titleMedium.copy(
                    fontSize = 18.sp * contentScale,
                ),
                fontWeight = if (on && enabled) FontWeight.Bold else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = AppleTypography.bodySmall.copy(
                        fontSize = AppleTypography.bodySmall.fontSize * contentScale,
                    ),
                    fontWeight = FontWeight.Normal,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One accessory in an [AccessoryGrid]. Callbacks live on the item so the grid stays data-driven. */
data class AccessoryItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val on: Boolean,
    val subtitle: String? = null,
    val accent: Color = AppleColors.accent,
    val warning: Boolean = false,
    val enabled: Boolean = true,
    val onToggle: (Boolean) -> Unit = {},
    val onLongPress: (() -> Unit)? = null,
)

/**
 * The HomeKit accessory grid: equal-width [AccessoryTile]s laid out in [columns] (2 by default),
 * every tile the same height so rows line up. Feed it a flat list — ordering/zoning is the
 * caller's job.
 */
@Composable
fun AccessoryGrid(
    items: List<AccessoryItem>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    spacing: Dp = 12.dp,
    activeColor: Color = Color.White,
    tileHeight: Dp = 66.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item ->
                    AccessoryTile(
                        title = item.title,
                        icon = item.icon,
                        on = item.on,
                        onToggle = item.onToggle,
                        subtitle = item.subtitle,
                        accent = item.accent,
                        activeColor = activeColor,
                        warning = item.warning,
                        enabled = item.enabled,
                        height = tileHeight,
                        onLongPress = item.onLongPress,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
