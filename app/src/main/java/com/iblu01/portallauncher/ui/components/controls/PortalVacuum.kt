package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/** A room the robot knows about. [icon] is optional. */
data class VacuumRoom(val id: String, val name: String, val icon: ImageVector? = null)

/** Secondary vacuum command rendered by [VacuumActionChips]. */
data class VacuumAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/** Cleaning mode, à la Roborock / iOS Home. */
enum class VacuumMode { VACUUM, VACUUM_AND_MOP, VACUUM_THEN_MOP, MOP }

/** French label for a [VacuumMode], ready for a [WheelPicker]. */
@Composable
fun VacuumMode.frLabel(): String = when (this) {
    VacuumMode.VACUUM -> stringResource(R.string.vacuum_mode_vacuum)
    VacuumMode.VACUUM_AND_MOP -> stringResource(R.string.vacuum_mode_vacuum_and_mop)
    VacuumMode.VACUUM_THEN_MOP -> stringResource(R.string.vacuum_mode_vacuum_then_mop)
    VacuumMode.MOP -> stringResource(R.string.vacuum_mode_mop)
}

/**
 * The big play/pause disc — the centrepiece of an Apple-style vacuum screen. A plain white circle
 * with a black glyph, springing on press. Deliberately calm: no blinking, no accent noise.
 */
@Composable
fun VacuumRunButton(
    running: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .shadow(14.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
            .then(if (enabled) Modifier.appleClickable { onToggle(!running) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (running) stringResource(R.string.vacuum_pause_desc) else stringResource(R.string.vacuum_start_desc),
            tint = Color.Black,
            modifier = Modifier.size(size * 0.34f),
        )
    }
}

/**
 * A soft status pill ("Préparation", "En charge", …). [prominent] darkens it à la the screenshot's
 * primary state chip; otherwise it's a light frosted pill.
 */
@Composable
fun VacuumStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    Box(
        modifier
            .clip(AppleShapes.pill)
            .background(
                if (prominent) AppleColors.elevated else AppleColors.frostedFill,
                AppleShapes.pill,
            )
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            style = AppleTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (prominent) AppleColors.primary else AppleColors.secondary,
        )
    }
}

/** Reusable secondary command strip for stop, dock, locate and integration-specific actions. */
@Composable
fun VacuumActionChips(
    actions: List<VacuumAction>,
    modifier: Modifier = Modifier,
    accent: Color = AppleColors.accent,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            val background = if (action.active) accent else AppleColors.frostedFill
            val content = if (action.active) contentColorOn(accent) else AppleColors.secondary
            Row(
                Modifier
                    .clip(AppleShapes.pill)
                    .background(background, AppleShapes.pill)
                    .border(0.5.dp, if (action.active) accent else AppleColors.frostedBorder, AppleShapes.pill)
                    .appleClickable(action.onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(action.icon, null, tint = content, modifier = Modifier.size(18.dp))
                Text(
                    action.label,
                    style = AppleTypography.bodyLarge.copy(
                        fontWeight = if (action.active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = content,
                )
            }
        }
    }
}

/**
 * A horizontally-scrolling strip of room chips. An unselected room is a small outlined "+" chip;
 * tapping it fills it with [accent] and morphs it into a state chip that shows [roomState] (e.g.
 * "En cours", "En file"). The room the robot is physically in ([currentRoomId]) carries a small dot.
 */
@Composable
fun VacuumRoomChips(
    rooms: List<VacuumRoom>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppleColors.accent,
    currentRoomId: String? = null,
    enabled: Boolean = true,
    roomState: (String) -> String? = { null },
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rooms.forEach { room ->
            RoomChip(
                room = room,
                selected = room.id in selected,
                isCurrent = room.id == currentRoomId,
                state = roomState(room.id),
                accent = accent,
                enabled = enabled,
                onClick = { onToggle(room.id) },
            )
        }
    }
}

@Composable
private fun RoomChip(
    room: VacuumRoom,
    selected: Boolean,
    isCurrent: Boolean,
    state: String?,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected && enabled) accent else AppleColors.frostedFill, tween(220), label = "chipBg",
    )
    val borderColor by animateColorAsState(
        if (selected && enabled) accent else AppleColors.frostedBorder, tween(220), label = "chipBorder",
    )
    val content = if (selected && enabled) contentColorOn(accent) else AppleColors.primary
    val leading by animateDpAsState(if (selected) 0.dp else 2.dp, tween(150), label = "chipLead")

    Row(
        Modifier
            .clip(AppleShapes.pill)
            .background(background, AppleShapes.pill)
            .border(0.5.dp, borderColor, AppleShapes.pill)
            .then(if (enabled) Modifier.appleClickable(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .padding(start = leading),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when {
            selected && isCurrent -> Box(Modifier.size(7.dp).clip(CircleShape).background(content))
            selected -> Unit
            room.icon != null -> Icon(room.icon, null, tint = content, modifier = Modifier.size(16.dp))
            else -> Icon(Icons.Filled.Add, null, tint = AppleColors.secondary, modifier = Modifier.size(16.dp))
        }
        Text(
            if (selected && state != null) "${room.name} · $state" else room.name,
            style = AppleTypography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected && enabled) content else AppleColors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
