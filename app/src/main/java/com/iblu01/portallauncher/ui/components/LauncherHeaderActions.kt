package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Launcher chrome in the top bar, beside the clock: hidden apps and Settings.
 *
 * They used to be tiles in the grid, which meant they took cells the user wanted for apps and could
 * be mistaken for draggable icons. Up here they are unambiguously chrome, and the grid is nothing
 * but apps.
 */
@Composable
fun LauncherHeaderActions(
    hiddenCount: Int,
    onShowHidden: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hiddenCount > 0) {
            // Only shown when something is actually hidden — otherwise it is a button that opens an
            // empty list, and "Masquer" would have no visible way back.
            HeaderAction(
                icon = Icons.Outlined.VisibilityOff,
                contentDescription = stringResource(R.string.header_hidden_apps_content_desc, hiddenCount),
                badge = hiddenCount.toString(),
                onClick = onShowHidden,
            )
        }
        HeaderAction(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.header_settings_content_desc),
            onClick = onSettings,
        )
    }
}

/** Maison title rendered in the shared compact-clock header rather than inside page content. */
@Composable
fun HomeHeaderTitle() {
    Box(modifier = Modifier.size(width = 112.dp, height = 40.dp), contentAlignment = Alignment.CenterStart) {
        Text(
            text = stringResource(R.string.home_header_title),
            style = AppleTypography.titleLarge,
            color = AppleColors.primary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * Maison-specific top-bar actions.
 *
 * [onGroupingModeClick] is intentionally only an extension point for now: the button advertises
 * the future Type/Pièce switch, but the caller does not mutate the page model yet.
 */
@Composable
fun HomeHeaderActions(
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onGroupingModeClick: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderAction(
            // The current catalog remains grouped by type; the room glyph names the future target.
            icon = Icons.Outlined.MeetingRoom,
            contentDescription = stringResource(R.string.home_header_grouping_content_desc),
            onClick = onGroupingModeClick,
        )
        HeaderAction(
            icon = if (editing) Icons.Outlined.Done else Icons.Outlined.Edit,
            contentDescription = stringResource(
                if (editing) R.string.home_header_finish_content_desc
                else R.string.home_header_edit_content_desc,
            ),
            selected = editing,
            onClick = { onEditingChange(!editing) },
        )
        HeaderAction(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.header_settings_content_desc),
            onClick = onSettings,
        )
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: String? = null,
    selected: Boolean = false,
) {
    Box(modifier = Modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppleShapes.pill)
                .background(
                    if (selected) AppleColors.accent.copy(alpha = 0.28f)
                    else Color.White.copy(alpha = 0.12f),
                    AppleShapes.pill,
                )
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                .appleClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = AppleColors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badge != null) {
            // Sibling of the clipped button, not a child of it — otherwise the pill's own clip cuts
            // the badge off at the boundary instead of letting it sit proud of the corner.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(AppleShapes.pill)
                    .background(AppleColors.accent, AppleShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    style = AppleTypography.bodySmall.copy(fontSize = 10.sp),
                    color = AppleColors.primary,
                )
            }
        }
    }
}
