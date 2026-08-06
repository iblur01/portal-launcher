package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold

/** The four background modes, using the same keys as [com.iblu01.portallauncher.ui.components.backgroundModes]. */
internal const val BG_MODE_CALM = "neutral"
internal const val BG_MODE_NATURE = "nature"
internal const val BG_MODE_PHOTO = "custom"
internal const val BG_MODE_IMMICH = "immich"

/**
 * "Choose your mood": four illustrated modes, each opening its own sub-page where it is configured
 * and then validated.
 *
 * The sub-page is local navigation, not an onboarding step: the flow's step list stays flat, and
 * leaving a sub-page is always possible — including the Immich branch, which can be abandoned
 * half-configured without blocking the assistant.
 */
@Composable
fun BackgroundStep(
    state: OnboardingUiState,
    onSelectBackground: (mode: String, configured: Boolean) -> Unit,
    onSetOpacity: (Float) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var branch by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped whenever filesDir/wallpaper.jpg is replaced, so every AmbientBackground("custom") on
    // this step — the tile preview and the sub-page preview — reloads the new file.
    var wallpaperVersion by rememberSaveable { mutableIntStateOf(0) }
    // A mode already stored by a previous run counts as validated: re-running the assistant must not
    // force the user back through a branch they have already been through.
    var validated by rememberSaveable {
        mutableStateOf(state.backgroundConfigured || state.backgroundMode != BG_MODE_CALM)
    }

    fun validate(mode: String) {
        onSelectBackground(mode, true)
        validated = true
        branch = null
    }

    val leave = { branch = null }

    when (branch) {
        BG_MODE_CALM -> CalmSubPage(
            state = state,
            onSetOpacity = onSetOpacity,
            onBack = leave,
            onValidate = { validate(BG_MODE_CALM) },
            modifier = modifier,
        )

        BG_MODE_NATURE -> NatureSubPage(
            state = state,
            onBack = leave,
            onValidate = { validate(BG_MODE_NATURE) },
            modifier = modifier,
        )

        BG_MODE_PHOTO -> PhotoSubPage(
            state = state,
            wallpaperVersion = wallpaperVersion,
            onWallpaperReplaced = { wallpaperVersion++ },
            onSetOpacity = onSetOpacity,
            onBack = leave,
            onValidate = { validate(BG_MODE_PHOTO) },
            modifier = modifier,
        )

        BG_MODE_IMMICH -> ImmichSubPage(
            state = state,
            onBack = leave,
            onValidate = { validate(BG_MODE_IMMICH) },
            modifier = modifier,
        )

        else -> BackgroundTiles(
            state = state,
            validated = validated,
            wallpaperVersion = wallpaperVersion,
            onOpenBranch = { branch = it },
            onBack = onBack,
            onContinue = onContinue.takeIf { validated },
            modifier = modifier,
        )
    }
}

@Composable
private fun BackgroundTiles(
    state: OnboardingUiState,
    validated: Boolean,
    wallpaperVersion: Int,
    onOpenBranch: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        BG_MODE_CALM to (R.string.onb_bg_tile_calm to R.string.onb_bg_calm_desc),
        BG_MODE_NATURE to (R.string.onb_bg_tile_nature to R.string.onb_bg_nature_desc),
        BG_MODE_PHOTO to (R.string.onb_bg_tile_my_photo to R.string.onb_bg_photo_desc),
        BG_MODE_IMMICH to (R.string.onb_bg_tile_immich to R.string.onb_bg_immich_desc),
    )

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_bg_title),
        description = stringResource(R.string.onb_bg_body),
        modifier = modifier,
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = onContinue,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 520.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { (mode, labels) ->
                            Box(Modifier.weight(1f)) {
                                ChoiceTile(
                                    title = stringResource(labels.first),
                                    subtitle = stringResource(labels.second),
                                    selected = validated && state.backgroundMode == mode,
                                    onClick = { onOpenBranch(mode) },
                                    preview = { TilePreview(mode, state, wallpaperVersion) },
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * A real miniature of the mode wherever it is cheap: [AmbientBackground] already knows how to render
 * the gradient, the landscapes and the user's own photo. Immich has nothing to show before it is
 * connected, so it falls back to the neutral gradient rather than to an invented placeholder image.
 */
@Composable
private fun TilePreview(mode: String, state: OnboardingUiState, wallpaperVersion: Int) {
    val immichReady = state.backgroundMode == BG_MODE_IMMICH && state.backgroundConfigured
    val effective = if (mode == BG_MODE_IMMICH && !immichReady) BG_MODE_CALM else mode
    AmbientBackground(
        mode = effective,
        wallpaperVersion = wallpaperVersion,
        modifier = Modifier.fillMaxWidth().height(104.dp),
    )
}
