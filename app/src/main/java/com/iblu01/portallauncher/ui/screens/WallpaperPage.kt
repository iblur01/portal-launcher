package com.iblu01.portallauncher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.PortalApp
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.photo.OkHttpTransport
import com.iblu01.portallauncher.photo.PhotoAlbum
import com.iblu01.portallauncher.photo.PhotoCoordinatorConfig
import com.iblu01.portallauncher.photo.PhotoSourceException
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.photo.immich.ImmichPhotoSource
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.copyWallpaper
import com.iblu01.portallauncher.ui.components.systemWallpaperSupported
import com.iblu01.portallauncher.ui.components.wallpaperFile
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * The wallpaper settings, split out of the application page: choosing what fills the screen is a
 * visual decision, so the page exposes only the three useful sources. Immich configuration lives
 * next to the source that needs it.
 */
@Composable
internal fun WallpaperPage(
    prefs: Prefs,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    bgOverlayOpacity: Float,
    onOpenOpacityPreview: () -> Unit,
    onOpenSystemWallpaperPicker: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as PortalApp

    var immichUrl by remember { mutableStateOf(prefs.immichUrl) }
    var immichKeyDraft by remember { mutableStateOf("") }
    var immichAlbumIds by remember { mutableStateOf(prefs.immichAlbumIds) }
    var immichAllowInsecure by remember { mutableStateOf(prefs.immichAllowInsecure) }
    var immichShuffle by remember { mutableStateOf(prefs.immichShuffle) }
    var immichRefresh by remember { mutableStateOf(prefs.immichRefreshMinutes) }
    var immichCadence by remember { mutableStateOf(prefs.immichCadenceSeconds) }
    var immichAlbums by remember { mutableStateOf<List<PhotoAlbum>>(emptyList()) }
    var showImmichAlbumPicker by remember { mutableStateOf(false) }
    var showImmichRefreshPresets by remember { mutableStateOf(false) }
    var showImmichCadencePresets by remember { mutableStateOf(false) }
    var immichBusy by remember { mutableStateOf(false) }
    var immichErrorCategory by remember { mutableStateOf<String?>(null) }
    var immichConnectedAlbumCount by remember { mutableStateOf<Int?>(null) }

    // Devices without a wallpaper service (Portal) would show a black screen in "system" mode, so
    // the Android source disappears there and the launcher-drawn photo takes its place.
    val systemSupported = remember(context) { systemWallpaperSupported(context) }
    val modes = remember(systemSupported) { wallpaperSettingsModes(systemSupported) }
    val selectedMode = bgMode.takeIf { mode -> modes.any { it.key == mode } }
        ?: if (systemSupported) "system" else "custom"
    LaunchedEffect(bgMode) {
        if (selectedMode != bgMode) onBgModeChange(selectedMode)
    }

    var wallpaperError by remember { mutableStateOf(false) }
    var wallpaperPresent by remember { mutableStateOf(wallpaperFile(context).exists()) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyWallpaper(context, uri) }
            wallpaperError = !copied
            wallpaperPresent = wallpaperFile(context).exists()
            // Re-emitting even for custom -> custom is what makes the launcher re-read the file.
            if (copied) onBgModeChange("custom")
        }
    }

    fun loadImmichAlbums(openPicker: Boolean) {
        if (immichBusy) return
        immichBusy = true
        immichErrorCategory = null
        immichConnectedAlbumCount = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apiKey = immichKeyDraft.ifBlank { prefs.immichApiKey }
                    val source = ImmichPhotoSource(
                        transport = OkHttpTransport(),
                        baseUrl = immichUrl,
                        apiKey = apiKey,
                        policy = if (immichAllowInsecure) {
                            TransportPolicy.ALLOW_INSECURE
                        } else {
                            TransportPolicy.REQUIRE_SECURE
                        },
                    )
                    val health = source.health()
                    if (!health.ok) throw PhotoSourceException(health.errorCategory ?: "unknown")
                    source.listAlbums()
                }
            }
            result.onSuccess { albums ->
                immichAlbums = albums
                immichConnectedAlbumCount = albums.size
                if (openPicker) showImmichAlbumPicker = true
            }.onFailure { failure ->
                immichErrorCategory = (failure as? PhotoSourceException)?.category ?: "config"
            }
            immichBusy = false
        }
    }

    if (showImmichAlbumPicker) {
        ImmichAlbumPickerDialog(
            albums = immichAlbums,
            selected = immichAlbumIds.toSet(),
            onDismiss = { showImmichAlbumPicker = false },
            onApply = {
                immichAlbumIds = it.toList()
                showImmichAlbumPicker = false
            },
        )
    }
    if (showImmichRefreshPresets) {
        IntPresetDialog(
            title = stringResource(R.string.settings_immich_refresh),
            values = listOf(5, 15, 30, 60, 180, 360, 720, 1440),
            suffix = stringResource(R.string.settings_minutes_short),
            onDismiss = { showImmichRefreshPresets = false },
            onSelect = {
                immichRefresh = it
                showImmichRefreshPresets = false
            },
        )
    }
    if (showImmichCadencePresets) {
        IntPresetDialog(
            title = stringResource(R.string.settings_immich_cadence),
            values = listOf(5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600),
            suffix = stringResource(R.string.settings_seconds_short),
            onDismiss = { showImmichCadencePresets = false },
            onSelect = {
                immichCadence = it
                showImmichCadencePresets = false
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSubPageHeader(
            title = stringResource(R.string.settings_wallpaper_page_title),
            onBack = onBack,
            showBack = showBack,
            breadcrumb = "${stringResource(R.string.settings_main_title)}  ›  ${stringResource(R.string.settings_tile_wallpaper_title)}",
        )

        Text(
            text = stringResource(R.string.settings_wallpaper_sources_hint),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        WallpaperSourceGrid(
            modes = modes,
            bgMode = selectedMode,
            onSelect = onBgModeChange,
        )

        if (selectedMode == "custom") {
            SettingsSection(title = stringResource(R.string.bg_mode_custom)) {
                SettingsRow(
                    label = stringResource(
                        if (wallpaperPresent) {
                            R.string.settings_wallpaper_replace_photo
                        } else {
                            R.string.settings_wallpaper_choose_photo
                        },
                    ),
                    value = when {
                        wallpaperError -> stringResource(R.string.settings_wallpaper_photo_error)
                        !wallpaperPresent -> stringResource(R.string.settings_wallpaper_photo_missing)
                        else -> null
                    },
                    onClick = { pickPhoto.launch("image/*") },
                )
            }
        }

        if (selectedMode == "system") {
            SettingsSection(title = stringResource(R.string.settings_wallpaper_android_section)) {
                SettingsRow(
                    label = stringResource(R.string.settings_wallpaper_android_change),
                    onClick = onOpenSystemWallpaperPicker,
                )
            }
        }

        if (selectedMode != "neutral") {
            SettingsSection(title = stringResource(R.string.settings_wallpaper_section_readability)) {
                SettingsRow(
                    label = stringResource(R.string.settings_app_label_overlay_opacity),
                    value = "${(bgOverlayOpacity * 100).toInt()} %",
                    onClick = onOpenOpacityPreview,
                )
            }
        }

        if (selectedMode == "immich") {
            SettingsSection(title = stringResource(R.string.settings_immich_section)) {
                SettingsTextField(
                    label = stringResource(R.string.settings_immich_url),
                    value = immichUrl,
                    onValueChange = { immichUrl = it },
                    placeholder = "https://photos.example.com",
                )
                SettingsDivider()
                SettingsTextField(
                    label = stringResource(R.string.settings_immich_api_key),
                    value = immichKeyDraft,
                    onValueChange = { immichKeyDraft = it },
                    placeholder = if (prefs.hasImmichApiKey) {
                        stringResource(R.string.settings_immich_key_configured)
                    } else {
                        stringResource(R.string.settings_immich_key_not_configured)
                    },
                    isPassword = true,
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_test_connection),
                    value = when {
                        immichBusy -> stringResource(R.string.settings_immich_testing)
                        immichErrorCategory != null -> stringResource(
                            R.string.settings_immich_test_failed,
                            immichErrorCategory.orEmpty(),
                        )
                        immichConnectedAlbumCount != null -> stringResource(
                            R.string.settings_immich_test_success,
                            immichConnectedAlbumCount ?: 0,
                        )
                        else -> null
                    },
                    onClick = { loadImmichAlbums(openPicker = false) },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_albums),
                    value = stringResource(R.string.settings_immich_albums_selected, immichAlbumIds.size),
                    onClick = { loadImmichAlbums(openPicker = true) },
                )
                SettingsDivider()
                SettingsToggle(
                    label = stringResource(R.string.settings_immich_allow_insecure),
                    checked = immichAllowInsecure,
                    onCheckedChange = { immichAllowInsecure = it },
                )
                if (immichAllowInsecure) {
                    Text(
                        text = stringResource(R.string.settings_immich_insecure_warning),
                        style = AppleTypography.bodySmall,
                        color = AppleColors.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                SettingsDivider()
                SettingsToggle(
                    label = stringResource(R.string.settings_immich_shuffle),
                    checked = immichShuffle,
                    onCheckedChange = { immichShuffle = it },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_refresh),
                    value = "$immichRefresh ${stringResource(R.string.settings_minutes_short)}",
                    onClick = { showImmichRefreshPresets = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_cadence),
                    value = "$immichCadence ${stringResource(R.string.settings_seconds_short)}",
                    onClick = { showImmichCadencePresets = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_apply),
                    value = if (prefs.hasImmichApiKey) stringResource(R.string.settings_immich_key_configured) else null,
                    onClick = applyImmich@{
                        if (!canApplyImmichConfig(
                                url = immichUrl,
                                hasApiKey = immichKeyDraft.isNotBlank() || prefs.hasImmichApiKey,
                                albumIds = immichAlbumIds,
                            )
                        ) {
                            immichErrorCategory = "config"
                            return@applyImmich
                        }
                        prefs.immichUrl = immichUrl
                        if (immichKeyDraft.isNotBlank()) prefs.immichApiKey = immichKeyDraft
                        prefs.immichAlbumIds = immichAlbumIds
                        prefs.immichAllowInsecure = immichAllowInsecure
                        prefs.immichShuffle = immichShuffle
                        prefs.immichRefreshMinutes = immichRefresh
                        prefs.immichCadenceSeconds = immichCadence
                        immichKeyDraft = ""
                        app.photoCoordinator.reconfigure(
                            PhotoCoordinatorConfig(
                                refreshIntervalMinutes = prefs.immichRefreshMinutes,
                                cadenceSeconds = prefs.immichCadenceSeconds,
                                shuffle = prefs.immichShuffle,
                            ),
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_remove),
                    onClick = {
                        app.photoCoordinator.removeProvider("immich")
                        prefs.clearImmichConfiguration()
                        immichUrl = ""
                        immichKeyDraft = ""
                        immichAlbumIds = emptyList()
                        immichAlbums = emptyList()
                        immichAllowInsecure = prefs.immichAllowInsecure
                        immichShuffle = prefs.immichShuffle
                        immichRefresh = prefs.immichRefreshMinutes
                        immichCadence = prefs.immichCadenceSeconds
                        immichErrorCategory = null
                        immichConnectedAlbumCount = null
                        onBgModeChange("system")
                    },
                )
            }
        }
    }
}

private data class WallpaperSettingsMode(
    val key: String,
    val label: Int,
    val icon: ImageVector? = null,
)

private fun wallpaperSettingsModes(systemSupported: Boolean) = listOfNotNull(
    WallpaperSettingsMode("system", R.string.bg_mode_system, Icons.Outlined.Android)
        .takeIf { systemSupported },
    WallpaperSettingsMode("neutral", R.string.bg_mode_neutral, Icons.Outlined.DarkMode),
    WallpaperSettingsMode("custom", R.string.bg_mode_custom, Icons.Outlined.Image),
    WallpaperSettingsMode("immich", R.string.bg_mode_immich, Icons.Outlined.Cloud),
)

/** The wallpaper sources available from settings, kept deliberately compact. */
@Composable
private fun WallpaperSourceGrid(
    modes: List<WallpaperSettingsMode>,
    bgMode: String,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 480.dp) 3 else if (maxWidth >= 300.dp) 2 else 1
        val tileHeight = 78.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            modes.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { mode ->
                        ChoiceTile(
                            title = stringResource(mode.label),
                            selected = mode.key == bgMode,
                            onClick = { onSelect(mode.key) },
                            icon = mode.icon,
                            compact = true,
                            modifier = Modifier.weight(1f).height(tileHeight),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
