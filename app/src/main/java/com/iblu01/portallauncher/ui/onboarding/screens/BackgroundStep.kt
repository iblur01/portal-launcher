package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors

internal const val BG_MODE_SYSTEM = "system"
internal const val BG_MODE_CALM = "neutral"
internal const val BG_MODE_IMMICH = "immich"

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
    var validated by rememberSaveable {
        mutableStateOf(state.backgroundConfigured || state.backgroundMode == BG_MODE_SYSTEM)
    }

    fun validate(mode: String) {
        onSelectBackground(mode, true)
        validated = true
        branch = null
    }

    when (branch) {
        BG_MODE_CALM -> CalmSubPage(
            state = state,
            onSetOpacity = onSetOpacity,
            onBack = { branch = null },
            onValidate = { validate(BG_MODE_CALM) },
            modifier = modifier,
        )

        BG_MODE_IMMICH -> ImmichSubPage(
            state = state,
            onBack = { branch = null },
            onValidate = { validate(BG_MODE_IMMICH) },
            modifier = modifier,
        )

        else -> BackgroundTiles(
            state = state,
            validated = validated,
            onSelect = { mode ->
                if (mode == BG_MODE_SYSTEM) validate(mode) else branch = mode
            },
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
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        BG_MODE_SYSTEM to (R.string.bg_mode_system to R.string.onb_bg_android_desc),
        BG_MODE_CALM to (R.string.onb_bg_tile_calm to R.string.onb_bg_calm_desc),
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
            val columns = if (maxWidth >= 480.dp) 3 else if (maxWidth >= 300.dp) 2 else 1
            val tileHeight = if (compact) 72.dp else if (!layout.showPreview) 132.dp else 196.dp
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                        row.forEach { (mode, labels) ->
                            ChoiceTile(
                                title = stringResource(labels.first),
                                subtitle = stringResource(labels.second),
                                selected = validated && state.backgroundMode == mode,
                                onClick = { onSelect(mode) },
                                icon = backgroundModeIcon(mode).takeIf { compact },
                                compact = compact,
                                previewFillsHeight = !compact,
                                preview = if (compact) null else ({ TilePreview(mode, state) }),
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
    BG_MODE_SYSTEM -> Icons.Outlined.Android
    BG_MODE_IMMICH -> Icons.Outlined.Cloud
    else -> Icons.Outlined.DarkMode
}

@Composable
private fun TilePreview(mode: String, state: OnboardingUiState) {
    val tile = Modifier.fillMaxSize()
    when (mode) {
        BG_MODE_SYSTEM -> SymbolPreview(tile) {
            Icon(Icons.Outlined.Android, null, tint = AppleColors.active, modifier = Modifier.size(48.dp))
        }
        BG_MODE_IMMICH -> {
            val connected = state.backgroundMode == BG_MODE_IMMICH && state.backgroundConfigured
            if (connected) AmbientBackground(mode, modifier = tile) else SymbolPreview(tile) {
                Image(painterResource(R.drawable.immich_logo), null, modifier = Modifier.size(52.dp))
            }
        }
        else -> AmbientBackground(mode, modifier = tile)
    }
}

@Composable
private fun SymbolPreview(modifier: Modifier = Modifier, symbol: @Composable () -> Unit) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B2026), Color(0xFF05070A)))),
        contentAlignment = Alignment.Center,
    ) { symbol() }
}
