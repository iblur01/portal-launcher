package com.iblu01.portallauncher.ui.onboarding.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iblu01.portallauncher.OpacityPreviewActivity
import com.iblu01.portallauncher.PortalApp
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.photo.DisplaySize
import com.iblu01.portallauncher.photo.OkHttpTransport
import com.iblu01.portallauncher.photo.PhotoAlbum
import com.iblu01.portallauncher.photo.PhotoCoordinatorConfig
import com.iblu01.portallauncher.photo.PhotoErrorCategories
import com.iblu01.portallauncher.photo.PhotoSourceException
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.photo.immich.ImmichPhotoSource
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSlider
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingSize
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The four background branches.
 *
 * `Prefs` is touched here — and only here among the onboarding screens — for the Immich settings:
 * the ViewModel exposes no Immich surface, and the photo source reads its configuration straight
 * from `Prefs`. The access stays contained in this file, is limited to the `immich*` keys plus a
 * read-only `bgOverlayOpacity` (needed to show the slider at its real position after the full-screen
 * preview has written to it), and the API key is never logged nor rendered unmasked.
 */

// --- Calm ---------------------------------------------------------------------------------------

@Composable
internal fun CalmSubPage(
    state: OnboardingUiState,
    onSetOpacity: (Float) -> Unit,
    onBack: () -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var opacity by remember { mutableFloatStateOf(readOverlayOpacity(context)) }
    // The full-screen preview persists on release; re-read it on return so the slider never lies.
    OnResume { opacity = readOverlayOpacity(context) }

    BackgroundSubPage(
        state = state,
        title = stringResource(R.string.onb_bg_tile_calm),
        description = stringResource(R.string.onb_bg_calm_desc),
        onBack = onBack,
        onValidate = onValidate,
        modifier = modifier,
    ) {
        WallpaperPreviewWithControls(
            overlay = opacity,
            preview = { AmbientBackground(BG_MODE_CALM, modifier = Modifier.fillMaxSize()) },
        ) {
            OpacitySlider(
                label = stringResource(R.string.onb_bg_calm_opacity_label),
                value = opacity,
                onValueChange = { opacity = it },
                onCommit = { onSetOpacity(opacity) },
            )
            PillButton(
                label = stringResource(R.string.onb_bg_calm_preview_action),
                onClick = { openOpacityPreview(context) },
            )
        }
    }
}

// --- Immich -------------------------------------------------------------------------------------

/** What the "test connection" action is currently reporting. */
private sealed interface ImmichTest {
    data object Idle : ImmichTest
    data object Running : ImmichTest
    data class Failed(val messageRes: Int) : ImmichTest
    data class Connected(val albums: List<PhotoAlbum>, val sample: ImageBitmap?) : ImmichTest
}

@Composable
internal fun ImmichSubPage(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { Prefs(context.applicationContext) }

    var url by remember { mutableStateOf(prefs.immichUrl) }
    var apiKey by remember { mutableStateOf(prefs.immichApiKey) }
    var albumIds by remember { mutableStateOf(prefs.immichAlbumIds) }
    var cadenceIndex by remember { mutableStateOf(nearestCadenceIndex(prefs.immichCadenceSeconds)) }
    var shuffle by remember { mutableStateOf(prefs.immichShuffle) }
    var allowInsecure by remember { mutableStateOf(prefs.immichAllowInsecure) }
    var test by remember { mutableStateOf<ImmichTest>(ImmichTest.Idle) }

    val connected = test is ImmichTest.Connected

    BackgroundSubPage(
        state = state,
        title = stringResource(R.string.onb_bg_tile_immich),
        description = stringResource(R.string.onb_bg_immich_desc),
        onBack = onBack,
        onValidate = if (connected) {
            {
                prefs.immichAlbumIds = albumIds
                context.applicationContext.let { app ->
                    (app as? PortalApp)?.photoCoordinator?.reconfigure(
                        PhotoCoordinatorConfig(
                            refreshIntervalMinutes = prefs.immichRefreshMinutes,
                            cadenceSeconds = prefs.immichCadenceSeconds,
                            shuffle = prefs.immichShuffle,
                        )
                    )
                }
                onValidate()
            }
        } else {
            null
        },
        modifier = modifier,
    ) {
        (test as? ImmichTest.Connected)?.sample?.let { sample ->
            BackgroundPreview(height = 220.dp, compact = compactWallpaperLayout()) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        SettingsSection(title = stringResource(R.string.onb_bg_tile_immich)) {
            SettingsTextField(
                label = stringResource(R.string.onb_bg_immich_field_server),
                value = url,
                onValueChange = {
                    url = it
                    prefs.immichUrl = it
                    test = ImmichTest.Idle
                },
            )
            SettingsDivider()
            SettingsTextField(
                label = stringResource(R.string.onb_bg_immich_field_api_key),
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    prefs.immichApiKey = it
                    test = ImmichTest.Idle
                },
                isPassword = true,
            )
            SettingsDivider()
            SettingsSlider(
                label = stringResource(R.string.onb_bg_immich_field_change_frequency),
                value = cadenceIndex.toFloat(),
                valueRange = 0f..(IMMICH_CADENCES.size - 1).toFloat(),
                steps = IMMICH_CADENCES.size - 2,
                onValueChange = { cadenceIndex = it.toInt().coerceIn(IMMICH_CADENCES.indices) },
                valueText = "${IMMICH_CADENCES[cadenceIndex]} " +
                    stringResource(R.string.settings_seconds_short),
                onValueChangeFinished = { prefs.immichCadenceSeconds = IMMICH_CADENCES[cadenceIndex] },
            )
            SettingsDivider()
            SettingsToggle(
                label = stringResource(R.string.onb_bg_immich_field_shuffle),
                checked = shuffle,
                onCheckedChange = {
                    shuffle = it
                    prefs.immichShuffle = it
                },
            )
            SettingsDivider()
            SettingsToggle(
                label = stringResource(R.string.onb_bg_immich_field_allow_insecure),
                checked = allowInsecure,
                onCheckedChange = {
                    allowInsecure = it
                    prefs.immichAllowInsecure = it
                    test = ImmichTest.Idle
                },
            )
        }

        PillButton(
            label = stringResource(R.string.onb_common_nav_test_connection),
            onClick = {
                if (test == ImmichTest.Running) return@PillButton
                test = ImmichTest.Running
                scope.launch {
                    test = probeImmich(
                        url = url,
                        apiKey = apiKey,
                        allowInsecure = allowInsecure,
                        preferredAlbumId = albumIds.firstOrNull(),
                    )
                }
            },
        )

        when (val current = test) {
            ImmichTest.Running -> NoticeText(stringResource(R.string.onb_bg_immich_loading))
            is ImmichTest.Failed -> ErrorText(stringResource(current.messageRes))
            is ImmichTest.Connected -> {
                NoticeText(stringResource(R.string.onb_bg_immich_success))
                if (current.albums.isNotEmpty()) {
                    SettingsSection(title = stringResource(R.string.onb_bg_immich_field_albums)) {
                        current.albums.forEachIndexed { index, album ->
                            if (index > 0) SettingsDivider()
                            SettingsToggle(
                                label = album.label,
                                checked = album.id in albumIds,
                                onCheckedChange = { checked ->
                                    albumIds = if (checked) albumIds + album.id
                                    else albumIds - album.id
                                    prefs.immichAlbumIds = albumIds
                                },
                            )
                        }
                    }
                }
            }

            ImmichTest.Idle -> Unit
        }

        NoticeText(stringResource(R.string.onb_bg_immich_leave_hint))
    }
}

/** A wide preview on tablets; a small landscape swatch beside the controls on compact screens. */
@Composable
private fun WallpaperPreviewWithControls(
    overlay: Float = 0f,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    preview: @Composable () -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    val compact = compactWallpaperLayout()
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            BackgroundPreview(overlay = overlay, compact = true, content = preview)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = controls,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackgroundPreview(overlay = overlay, height = height, content = preview)
            controls()
        }
    }
}

@Composable
private fun compactWallpaperLayout(): Boolean {
    val layout = LocalOnboardingLayout.current
    return layout.short || layout.size == OnboardingSize.COMPACT
}

/** Change-frequency choices, matching the range accepted by `Prefs.immichCadenceSeconds`. */
private val IMMICH_CADENCES = listOf(5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600)

private fun nearestCadenceIndex(seconds: Int): Int =
    IMMICH_CADENCES.indices.minByOrNull { kotlin.math.abs(IMMICH_CADENCES[it] - seconds) } ?: 3

/**
 * Hits the real server through the existing Immich adapter: ping, album list, then one thumbnail so
 * the success state shows an actual photo instead of a claim. The API key is passed straight to the
 * client and never logged.
 */
private suspend fun probeImmich(
    url: String,
    apiKey: String,
    allowInsecure: Boolean,
    preferredAlbumId: String?,
): ImmichTest = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImmichPhotoSource(
            transport = OkHttpTransport(),
            baseUrl = url,
            apiKey = apiKey,
            policy = if (allowInsecure) TransportPolicy.ALLOW_INSECURE else TransportPolicy.REQUIRE_SECURE,
        )
        val health = source.health()
        if (!health.ok) throw PhotoSourceException(health.errorCategory ?: PhotoErrorCategories.UNKNOWN)
        val albums = source.listAlbums()
        val albumId = preferredAlbumId?.takeIf { id -> albums.any { it.id == id } }
            ?: albums.firstOrNull()?.id
        val sample = albumId?.let { id ->
            source.listAssets(id, page = 0, pageSize = 1).assets.firstOrNull()
                ?.let { asset -> source.fetchImage(asset, DisplaySize(1280, 720)).getOrNull() }
        }?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        ImmichTest.Connected(albums, sample)
    }.getOrElse { failure -> ImmichTest.Failed(immichErrorMessage(failure)) }
}

private fun immichErrorMessage(failure: Throwable): Int =
    when ((failure as? PhotoSourceException)?.category) {
        PhotoErrorCategories.AUTH -> R.string.onb_bg_immich_error_api_key
        PhotoErrorCategories.NETWORK, PhotoErrorCategories.CONFIG -> R.string.onb_bg_immich_error_unreachable
        PhotoErrorCategories.SERVER -> R.string.onb_bg_immich_error_unexpected
        // An invalid address or a rejected transport policy fails in the client's `init` block, so
        // it arrives as an IllegalArgumentException rather than a categorised source exception.
        else -> if (failure is IllegalArgumentException) R.string.onb_bg_immich_error_unreachable
        else R.string.onb_bg_immich_error_unexpected
    }

// --- Shared chrome ------------------------------------------------------------------------------

/**
 * The frame the four branches share: the mode's own title, a back that returns to the tiles, and a
 * primary action that validates the mode. A null [onValidate] leaves the action inert, which is how
 * a branch says "not configured yet" without hiding its own button.
 */
@Composable
private fun BackgroundSubPage(
    state: OnboardingUiState,
    title: String,
    description: String,
    onBack: () -> Unit,
    onValidate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = title,
        description = description,
        modifier = modifier,
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_done),
                onPrimary = onValidate,
            )
        },
        content = content,
    )
}

/** A framed miniature of a background, optionally under the interface's own darkening scrim. */
@Composable
private fun BackgroundPreview(
    modifier: Modifier = Modifier,
    overlay: Float = 0f,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .then(if (compact) Modifier.width(132.dp).height(84.dp) else Modifier.fillMaxWidth().height(height))
            .clip(AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
    ) {
        content()
        if (overlay > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlay)))
        }
    }
}

@Composable
private fun OpacitySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppleShapes.section)
            .background(AppleColors.frostedFill, AppleShapes.section),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsSlider(
            label = label,
            value = value,
            // Mirrors the range Prefs.bgOverlayOpacity clamps to.
            valueRange = 0f..0.6f,
            steps = 11,
            onValueChange = onValueChange,
            valueText = stringResource(
                R.string.onb_grid_slider_percent_format,
                (value * 100).toInt(),
            ),
            onValueChangeFinished = onCommit,
        )
    }
}

@Composable
private fun NoticeText(text: String) {
    Text(
        text,
        style = AppleTypography.bodySmall,
        color = AppleColors.secondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        style = AppleTypography.bodySmall,
        color = AppleColors.error,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Read-only: the slider has to start where the interface actually is. */
private fun readOverlayOpacity(context: Context): Float =
    Prefs(context.applicationContext).bgOverlayOpacity

private fun openOpacityPreview(context: Context) {
    val intent = Intent(context, OpacityPreviewActivity::class.java)
    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
