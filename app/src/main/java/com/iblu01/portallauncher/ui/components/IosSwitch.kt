package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.minimumInteractiveComponentSize
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.PortalTheme

/**
 * iOS-style toggle. Track goes translucent-white → iOS green; the thumb slides with
 * spring physics. Replaces Material [androidx.compose.material3.Switch] wholesale.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val travel = trackWidth - thumbSize - 4.dp // 2dp inset either side

    val trackColor by animateColorAsState(
        targetValue = if (checked) AppleColors.iosSwitchGreen else Color.White.copy(alpha = 0.2f),
        animationSpec = AppleMotion.spring(),
        label = "trackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = AppleMotion.spring(),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(trackWidth, trackHeight)
                .background(trackColor, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .shadow(2.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.2f))
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun IosSwitchPreview() {
    PortalTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            IosSwitch(checked = true, onCheckedChange = {})
            IosSwitch(checked = false, onCheckedChange = {})
        }
    }
}
