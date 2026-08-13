package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Image as ImageIconVector
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.copyWallpaper
import com.iblu01.portallauncher.ui.components.systemWallpaperSupported
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val BG_MODE_SYSTEM = "system"
internal const val BG_MODE_CALM = "neutral"
internal const val BG_MODE_CUSTOM = "custom"
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branch by rememberSaveable { mutableStateOf<String?>(null) }
    var validated by rememberSaveable {
        mutableStateOf(state.backgroundConfigured || state.backgroundMode == BG_MODE_SYSTEM)
    }

    fun validate(mode: String) {
        onSelectBackground(mode, true)
        validated = true
        branch = null
    }

    // A device without a wallpaper service draws nothing behind the launcher, so the Android tile
    // is replaced by a photo the launcher renders itself.
    val systemSupported = remember(context) { systemWallpaperSupported(context) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (withContext(Dispatchers.IO) { copyWallpaper(context, uri) }) validate(BG_MODE_CUSTOM)
        }
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
            systemSupported = systemSupported,
            onSelect = { mode ->
                when (mode) {
                    BG_MODE_SYSTEM -> validate(mode)
                    BG_MODE_CUSTOM -> pickPhoto.launch("image/*")
                    else -> branch = mode
                }
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
    systemSupported: Boolean,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tiles = listOfNotNull(
        (BG_MODE_SYSTEM to (R.string.bg_mode_system to R.string.onb_bg_android_desc))
            .takeIf { systemSupported },
        BG_MODE_CALM to (R.string.onb_bg_tile_calm to R.string.onb_bg_calm_desc),
        BG_MODE_CUSTOM to (R.string.bg_mode_custom to R.string.onb_bg_custom_desc),
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
            val tileHeight = if (compact) 142.dp else if (!layout.showPreview) 132.dp else 196.dp
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                        row.forEach { (mode, labels) ->
                            ChoiceTile(
                                title = stringResource(labels.first),
                                subtitle = stringResource(labels.second),
                                selected = validated && state.backgroundMode == mode,
                                onClick = { onSelect(mode) },
                                compact = compact,
                                previewFillsHeight = true,
                                preview = if (compact) ({ CompactModeMark(mode) })
                                else ({ TilePreview(mode, state) }),
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

@Composable
private fun CompactModeMark(mode: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (mode == BG_MODE_IMMICH) {
            Image(
                painterResource(R.drawable.immich_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Icon(
                imageVector = backgroundModeIcon(mode),
                contentDescription = null,
                tint = if (mode == BG_MODE_SYSTEM) AppleColors.active else AppleColors.secondary,
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

private fun backgroundModeIcon(mode: String): ImageVector = when (mode) {
    BG_MODE_SYSTEM -> Icons.Outlined.Android
    BG_MODE_CUSTOM -> Icons.Outlined.ImageIconVector
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
