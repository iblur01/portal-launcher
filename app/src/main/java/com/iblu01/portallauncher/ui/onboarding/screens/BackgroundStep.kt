package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import coil.compose.AsyncImage
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.rememberWallpaperImageLoader
import com.iblu01.portallauncher.ui.components.unsplashUrls
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import kotlinx.coroutines.delay
import java.io.File

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
        val layout = LocalOnboardingLayout.current
        val compact = layout.short || layout.size == com.iblu01.portallauncher.ui.onboarding.components.OnboardingSize.COMPACT
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Two columns as soon as a tile can be ~165dp wide: stacked, the four modes would need
            // three screens' worth of scrolling on the very windows that have the least height.
            val columns = if (maxWidth >= 300.dp) 2 else 1
            // One fixed height for every tile: the four descriptions do not have the same length,
            // and a row of tiles that step up and down reads as an accident. The preview absorbs
            // whatever the text does not use, so the images stay the same size too.
            val tileHeight = if (compact) 72.dp else if (!layout.showPreview) 132.dp else 196.dp
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                        row.forEach { (mode, labels) ->
                            ChoiceTile(
                                title = stringResource(labels.first),
                                subtitle = stringResource(labels.second),
                                selected = validated && state.backgroundMode == mode,
                                onClick = { onOpenBranch(mode) },
                                icon = backgroundModeIcon(mode).takeIf { compact },
                                compact = compact,
                                previewFillsHeight = !compact,
                                preview = if (compact) null else {
                                    { TilePreview(mode, state, wallpaperVersion) }
                                },
                                modifier = Modifier.weight(1f).height(tileHeight),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun backgroundModeIcon(mode: String): ImageVector = when (mode) {
    BG_MODE_NATURE -> Icons.Outlined.Landscape
    BG_MODE_PHOTO -> Icons.Outlined.Photo
    BG_MODE_IMMICH -> Icons.Outlined.Cloud
    else -> Icons.Outlined.DarkMode
}

/**
 * What each tile shows.
 *
 * Real content wherever there is any: the gradient and the landscapes are the launcher's own
 * renderers, and a photo already chosen is the photo itself. The two cases with nothing to render
 * yet get a symbol rather than a fake image — a person for "your own photo", the Immich logo for
 * Immich — so a tile never promises a picture the launcher does not have.
 */
@Composable
private fun TilePreview(mode: String, state: OnboardingUiState, wallpaperVersion: Int) {
    val tile = Modifier.fillMaxSize()
    when (mode) {
        BG_MODE_NATURE -> NatureCyclePreview(tile)

        BG_MODE_PHOTO -> {
            val context = LocalContext.current
            val hasPhoto = remember(wallpaperVersion) {
                File(context.filesDir, "wallpaper.jpg").exists()
            }
            if (hasPhoto) {
                AmbientBackground(mode = mode, wallpaperVersion = wallpaperVersion, modifier = tile)
            } else {
                SymbolPreview(tile) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = AppleColors.secondary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        BG_MODE_IMMICH -> {
            val connected = state.backgroundMode == BG_MODE_IMMICH && state.backgroundConfigured
            if (connected) {
                AmbientBackground(mode = mode, modifier = tile)
            } else {
                SymbolPreview(tile) {
                    Image(
                        painter = painterResource(R.drawable.immich_logo),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        else -> AmbientBackground(mode = mode, modifier = tile)
    }
}

/** How long each landscape is held in the tile. Shorter than the launcher's own 30s: the tile has
 * only a few seconds of the user's attention to show that this mode cycles at all. */
private const val NATURE_PREVIEW_MILLIS = 3_000L

/** The same landscapes the launcher cycles, at a pace that reads as a cycle inside a tile. */
@Composable
private fun NatureCyclePreview(modifier: Modifier = Modifier) {
    val loader = rememberWallpaperImageLoader()
    val index by produceState(0) {
        while (true) {
            delay(NATURE_PREVIEW_MILLIS)
            value = (value + 1) % unsplashUrls.size
        }
    }
    // These are the launcher's real landscapes, so they need the network. Offline, the tile falls
    // back to the same symbol treatment as the other modes instead of an empty rectangle.
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        SymbolPreview(modifier) {
            Icon(
                Icons.Outlined.Landscape,
                contentDescription = null,
                tint = AppleColors.secondary,
                modifier = Modifier.size(44.dp),
            )
        }
        return
    }
    Box(modifier.background(AppleColors.elevated)) {
        Crossfade(targetState = index, animationSpec = tween(900), label = "nature-tile") { current ->
            AsyncImage(
                model = unsplashUrls[current].first,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = {
                    Log.w("PortalOnboarding", "landscape preview failed", it.result.throwable)
                    failed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A symbol centred on the neutral surface, for the modes with no image to show yet. */
@Composable
private fun SymbolPreview(modifier: Modifier = Modifier, symbol: @Composable () -> Unit) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF1B2026), Color(0xFF05070A)))
        ),
        contentAlignment = Alignment.Center,
    ) { symbol() }
}
