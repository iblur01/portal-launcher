package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.SettingsSlider
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.onboarding.GridPreset
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.specForScale
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes

/**
 * Grid density, chosen through what it actually produces rather than through a percentage.
 *
 * The counts under each preset are computed from the real screen with the same arithmetic the
 * launcher uses ([specForScale]), so "5 × 3" is a promise, not an illustration.
 */
@Composable
fun GridStep(
    state: OnboardingUiState,
    onSelectPreset: (GridPreset) -> Unit,
    onSelectScale: (Float) -> Unit,
    onSetManual: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_grid_title),
        description = stringResource(R.string.onb_grid_body),
        modifier = modifier,
        aside = { GridPreview(state.gridScale, screenWidth, screenHeight) },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = onContinue,
            )
        },
    ) {
        GridPreset.values().forEach { preset ->
            val spec = specForScale(screenWidth, screenHeight, preset.scale)
            ChoiceTile(
                title = stringResource(presetLabel(preset)),
                subtitle = stringResource(
                    R.string.onb_grid_preset_subtitle_format,
                    spec.columns,
                    spec.rows,
                ),
                selected = state.gridPreset == preset,
                onClick = { onSelectPreset(preset) },
            )
        }

        SettingsToggle(
            label = stringResource(R.string.onb_grid_manual_toggle),
            checked = state.gridManual,
            onCheckedChange = onSetManual,
        )
        if (state.gridManual) {
            SettingsSlider(
                label = stringResource(R.string.onb_grid_slider_label),
                value = state.gridScale * 100f,
                valueRange = 70f..130f,
                steps = 11,
                onValueChange = { onSelectScale(it / 100f) },
                valueText = stringResource(
                    R.string.onb_grid_slider_percent_format,
                    (state.gridScale * 100f).toInt(),
                ),
            )
        }
    }
}

private fun presetLabel(preset: GridPreset): Int = when (preset) {
    GridPreset.LARGE_ICONS -> R.string.onb_grid_preset_large_icons
    GridPreset.BALANCED -> R.string.onb_grid_preset_balanced
    GridPreset.MORE_APPS -> R.string.onb_grid_preset_more_apps
}

/**
 * A page of the app grid at the chosen density.
 *
 * The cells are placeholders — what matters here is how much room an icon gets, not which app sits
 * where — but the layout is the screen's own aspect ratio so the density reads true.
 */
@Composable
private fun GridPreview(scale: Float, screenWidthDp: Float, screenHeightDp: Float) {
    val spec = specForScale(screenWidthDp, screenHeightDp, scale)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio((screenWidthDp / screenHeightDp).coerceIn(0.6f, 2.2f))
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(14.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellWidth = maxWidth / spec.columns
            val cellHeight = maxHeight / spec.rows
            Column(Modifier.fillMaxSize()) {
                repeat(spec.rows) {
                    Row(Modifier.height(cellHeight)) {
                        repeat(spec.columns) {
                            PreviewCell(Modifier.width(cellWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCell(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .background(AppleColors.primary.copy(alpha = 0.18f), AppleShapes.section)
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(0.5f)
                .height(3.dp)
                .background(AppleColors.primary.copy(alpha = 0.10f), AppleShapes.pill)
        )
    }
}
