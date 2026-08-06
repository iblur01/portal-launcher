package com.iblu01.portallauncher.ui.onboarding.screens

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

/** How long a coach mark stays highlighted before the demo moves to the next one. */
private const val HIGHLIGHT_DURATION_MS = 2600L

/**
 * The three gestures the launcher relies on, shown as coach marks over a mock of the home screen.
 *
 * Deliberately short: no tutorial to read, no fourth hint. The highlight cycles slowly so the eye
 * follows it once, and holds still when the system has animations turned off.
 */
@Composable
fun GesturesStep(
    state: OnboardingUiState,
    onUnderstood: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hints = listOf(
        R.string.onb_gestures_hint_apps,
        R.string.onb_gestures_hint_clock,
        R.string.onb_gestures_hint_hold,
    )

    val context = LocalContext.current
    val animated = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    var highlighted by remember { mutableIntStateOf(0) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        while (true) {
            delay(HIGHLIGHT_DURATION_MS)
            highlighted = (highlighted + 1) % hints.size
        }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_gestures_title),
        modifier = modifier,
        aside = { HomeScreenMock(highlighted) },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_i_understand),
                onPrimary = onUnderstood,
            )
        },
    ) {
        hints.forEachIndexed { index, hint ->
            HintRow(
                index = index,
                text = stringResource(hint),
                highlighted = index == highlighted,
            )
        }
    }
}

@Composable
private fun HintRow(index: Int, text: String, highlighted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(
                width = if (highlighted) 1.dp else 0.5.dp,
                color = if (highlighted) AppleColors.accent else AppleColors.frostedBorder,
                shape = AppleShapes.card,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoachMarker(index = index, highlighted = highlighted)
        Spacer(Modifier.width(14.dp))
        Text(text, style = AppleTypography.titleMedium, color = AppleColors.primary)
    }
}

/** The numbered dot shared by the hint list and the mock, so the two read as one demo. */
@Composable
private fun CoachMarker(index: Int, highlighted: Boolean, size: Int = 26) {
    val alpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0.35f,
        animationSpec = AppleMotion.spring(),
        label = "marker-alpha",
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                if (highlighted) AppleColors.accent.copy(alpha = alpha)
                else AppleColors.frostedFill,
                CircleShape,
            )
            .border(0.5.dp, AppleColors.frostedBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (index + 1).toString(),
            style = AppleTypography.labelSmall,
            color = if (highlighted) AppleColors.primary else AppleColors.secondary,
        )
    }
}

/**
 * A hint of the real home screen — the clock, the edge of the app grid, empty space — just detailed
 * enough for the three markers to point at something recognisable.
 */
@Composable
private fun HomeScreenMock(highlighted: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(AppleShapes.card)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1B2026), Color(0xFF05070A))),
                AppleShapes.card,
            )
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(18.dp)
    ) {
        // The clock, target of hint 2.
        Column(Modifier.align(Alignment.CenterStart)) {
            Box(
                Modifier
                    .width(96.dp)
                    .size(width = 96.dp, height = 26.dp)
                    .background(AppleColors.primary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(width = 58.dp, height = 9.dp)
                    .background(AppleColors.tertiary, RoundedCornerShape(4.dp))
            )
        }

        // The edge of the app grid, target of hint 1.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(AppleColors.frostedFill, RoundedCornerShape(7.dp))
                        )
                    }
                }
            }
        }

        CoachMarker(index = 0, highlighted = highlighted == 0, size = 22)
        Box(Modifier.align(Alignment.CenterStart).padding(start = 100.dp)) {
            CoachMarker(index = 1, highlighted = highlighted == 1, size = 22)
        }
        Box(Modifier.align(Alignment.BottomStart)) {
            CoachMarker(index = 2, highlighted = highlighted == 2, size = 22)
        }
    }
}
