package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Blinds
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.domain.model.MediaPlayerVolume
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.LocalAreas
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.ui.components.ChipActionsPanel
import com.iblu01.portallauncher.ui.components.MediaPlayerView
import com.iblu01.portallauncher.ui.components.StatusChip
import com.iblu01.portallauncher.ui.components.launcherIcon
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.components.isCompactClockScreen
import com.iblu01.portallauncher.ui.components.controls.AccessoryGrid
import com.iblu01.portallauncher.ui.components.controls.AccessoryItem
import com.iblu01.portallauncher.ui.components.controls.ControlContentLayout
import com.iblu01.portallauncher.ui.components.controls.FillOrigin
import com.iblu01.portallauncher.ui.components.controls.HorizontalSegmentedSelector
import com.iblu01.portallauncher.ui.components.controls.PinKeypad
import com.iblu01.portallauncher.ui.components.controls.PortalThreeWayControl
import com.iblu01.portallauncher.ui.components.controls.ThermostatArc
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode
import com.iblu01.portallauncher.ui.components.controls.VacuumMode
import com.iblu01.portallauncher.ui.components.controls.VacuumRoom
import com.iblu01.portallauncher.ui.components.controls.VacuumRoomChips
import com.iblu01.portallauncher.ui.components.controls.VacuumRunButton
import com.iblu01.portallauncher.ui.components.controls.VacuumStatusChip
import com.iblu01.portallauncher.ui.components.controls.VerticalColorTempSlider
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.components.controls.controlSize
import com.iblu01.portallauncher.ui.components.controls.VerticalSegmentedSelector
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import org.json.JSONObject

/** Named accents so adaptivity is obvious at a glance. */

private enum class Presence { HOME, AWAY, OFF }

private sealed interface FakePanelSelection {
    val id: String
    data class Chip(val chip: LauncherChip) : FakePanelSelection { override val id = chip.id }
    data class Media(val media: PlayingMedia) : FakePanelSelection { override val id = "media_group" }
}

private data class HaCapabilityGroup(
    val title: String,
    val chips: List<LauncherChip>,
)

/**
 * Dev-only gallery: every reusable control from
 * [com.iblu01.portallauncher.ui.components.controls], driven live by a single accent so the
 * theme-adaptivity is visible. Reached from the home-screen long-press menu.
 */
@Composable
fun PlaygroundScreen(onBack: () -> Unit) {
    val accentSwatches = listOf(
        stringResource(R.string.playground_swatch_blue) to AppleColors.accent,
        stringResource(R.string.playground_swatch_green) to Color(0xFF30D158),
        stringResource(R.string.playground_swatch_mint) to Color(0xFF63E6BE),
        stringResource(R.string.playground_swatch_yellow) to AppleColors.warning,
        stringResource(R.string.playground_swatch_red) to AppleColors.error,
        stringResource(R.string.playground_swatch_purple) to Color(0xFFAF52DE),
        stringResource(R.string.playground_swatch_gray) to Color(0xFFD8D8DA),
    )
    var accent by remember { mutableStateOf(accentSwatches.first().second) }
    var selectedFakePanel by remember { mutableStateOf<FakePanelSelection?>(null) }
    val fakeEntities = rememberFakePanelEntities()
    val fakeService = rememberFakeCallService(fakeEntities)
    val fakeTracks = remember { listOf("Midnight City" to "M83", "Nightcall" to "Kavinsky", "Genesis" to "Grimes") }
    var fakeTrackIndex by remember { mutableIntStateOf(0) }
    var fakeMediaPlaying by remember { mutableStateOf(true) }
    var fakeMediaVolume by remember { mutableIntStateOf(38) }
    val fakeChips = fakePanelChips(fakeEntities, fakeMediaPlaying, fakeTracks[fakeTrackIndex].first)
    val capabilityGroups = fakeCapabilityGroups(fakeEntities)
    val allFakeChips = fakeChips + capabilityGroups.flatMap(HaCapabilityGroup::chips)
    val fakeMedia = PlayingMedia(
        entityId = "media_player.salon", title = fakeTracks[fakeTrackIndex].first, artist = fakeTracks[fakeTrackIndex].second,
        album = "Simulation locale", state = if (fakeMediaPlaying) "playing" else "paused", coverUrl = null,
        volumePercent = fakeMediaVolume, isMuted = false, playerNames = listOf("Salon"),
        players = listOf(MediaPlayerVolume("media_player.salon", "Salon", fakeMediaVolume, false)),
    )
    val playgroundMetrics = LocalContext.current.resources.displayMetrics
    val compactPlayground = isCompactClockScreen(
        playgroundMetrics.widthPixels,
        playgroundMetrics.heightPixels,
        playgroundMetrics.xdpi,
        playgroundMetrics.ydpi,
    )
    val fullscreenMedia = compactPlayground && selectedFakePanel is FakePanelSelection.Media
    val contentWidth by animateFloatAsState(
        targetValue = if (selectedFakePanel == null || fullscreenMedia) 1f else 0.67f,
        animationSpec = tween(500),
        label = "playgroundPanelWidth",
    )

    Box(Modifier.fillMaxSize().background(AppleColors.background)) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(contentWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppleColors.frostedFill)
                    .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                    .appleClickable(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.playground_back_desc), tint = AppleColors.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.playground_title), style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text(stringResource(R.string.playground_subtitle), style = AppleTypography.bodySmall, color = AppleColors.secondary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Accent picker — the whole point: everything below re-skins instantly.
        Text(stringResource(R.string.playground_accent_color_label), style = AppleTypography.bodySmall, color = AppleColors.secondary)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            accentSwatches.forEach { (name, color) ->
                val active = color == accent
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (active) 3.dp else 0.5.dp,
                                if (active) AppleColors.primary else AppleColors.frostedBorder,
                                CircleShape,
                            )
                            .appleClickable { accent = color },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = AppleTypography.labelSmall, color = if (active) AppleColors.primary else AppleColors.tertiary)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        CompositionLocalProvider(
            LocalHaStates provides fakeEntities,
            LocalAreas provides emptyMap(),
        ) {
            FakePanelLab(
                chips = fakeChips,
                media = fakeMedia,
                selected = selectedFakePanel,
                onSelect = { next -> selectedFakePanel = next.takeUnless { it.id == selectedFakePanel?.id } },
            )
            Spacer(Modifier.height(28.dp))
            HaCapabilityLab(
                groups = capabilityGroups,
                selected = selectedFakePanel,
                onSelect = { chip ->
                    val next = FakePanelSelection.Chip(chip)
                    selectedFakePanel = next.takeUnless { it.id == selectedFakePanel?.id }
                },
            )
        }

        Spacer(Modifier.height(28.dp))

        // 1 · Fill sliders
        SectionTitle(stringResource(R.string.playground_section_vertical_slider))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var bottom by remember { mutableFloatStateOf(0.45f) }
            var top by remember { mutableFloatStateOf(0.45f) }
            var disabled by remember { mutableFloatStateOf(0.7f) }
            LabeledControl(stringResource(R.string.playground_slider_from_bottom)) {
                VerticalFillSlider(
                    value = bottom, onValueChange = { bottom = it },
                    origin = FillOrigin.BOTTOM, accent = accent,
                    icon = Icons.Filled.WbSunny,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl(stringResource(R.string.playground_slider_from_top)) {
                VerticalFillSlider(
                    value = top, onValueChange = { top = it },
                    origin = FillOrigin.TOP, accent = accent,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl(stringResource(R.string.playground_slider_disabled)) {
                VerticalFillSlider(
                    value = disabled, onValueChange = { disabled = it },
                    accent = accent, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Three-way controls — the same shell, specialised by content for each domain.
        SectionTitle(stringResource(R.string.playground_section_three_way_control))
        var mediaPlaying by remember { mutableStateOf(true) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LabeledControl(stringResource(R.string.playground_three_way_media)) {
                PortalThreeWayControl(
                    leadingIcon = Icons.Filled.SkipPrevious,
                    leadingContentDescription = stringResource(R.string.media_previous_track_desc),
                    onLeadingClick = {},
                    centerIcon = if (mediaPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    centerContentDescription = if (mediaPlaying) stringResource(R.string.media_pause_desc) else stringResource(R.string.media_play_desc),
                    onCenterClick = { mediaPlaying = !mediaPlaying },
                    trailingIcon = Icons.Filled.SkipNext,
                    trailingContentDescription = stringResource(R.string.media_next_track_desc),
                    onTrailingClick = {},
                )
            }
            LabeledControl(stringResource(R.string.playground_three_way_cover)) {
                PortalThreeWayControl(
                    leadingIcon = Icons.Outlined.KeyboardArrowDown,
                    leadingContentDescription = stringResource(R.string.cover_button_close),
                    leadingLabel = stringResource(R.string.cover_button_close),
                    onLeadingClick = {},
                    centerIcon = Icons.Filled.Pause,
                    centerContentDescription = stringResource(R.string.cover_button_stop),
                    onCenterClick = {},
                    trailingIcon = Icons.Outlined.KeyboardArrowUp,
                    trailingContentDescription = stringResource(R.string.cover_button_open),
                    trailingLabel = stringResource(R.string.cover_button_open),
                    onTrailingClick = {},
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 2 · Gradient sliders
        SectionTitle(stringResource(R.string.playground_section_gradient_slider))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var kelvin by remember { mutableIntStateOf(4000) }
            var disabledKelvin by remember { mutableIntStateOf(3000) }
            LabeledControl(stringResource(R.string.playground_slider_temperature)) {
                VerticalColorTempSlider(
                    kelvin = kelvin, onKelvinChange = { kelvin = it },
                    minKelvin = 2200, maxKelvin = 6500,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl(stringResource(R.string.playground_slider_disabled)) {
                VerticalColorTempSlider(
                    kelvin = disabledKelvin, onKelvinChange = { disabledKelvin = it },
                    minKelvin = 2200, maxKelvin = 6500, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 3 · Segmented selectors
        SectionTitle(stringResource(R.string.playground_section_selector))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            var two by remember { mutableStateOf(true) }
            var three by remember { mutableStateOf(Presence.HOME) }
            var six by remember { mutableIntStateOf(2) }
            val selectorAutoLabel = stringResource(R.string.playground_selector_auto)
            val selectorManualLabel = stringResource(R.string.playground_selector_manual)
            LabeledControl(stringResource(R.string.playground_selector_2_options)) {
                VerticalSegmentedSelector(
                    options = listOf(true, false),
                    selected = two, onSelect = { two = it },
                    label = { if (it) selectorAutoLabel else selectorManualLabel },
                    accent = accent,
                    modifier = Modifier.width(88.dp),
                )
            }
            val presenceHomeLabel = stringResource(R.string.playground_presence_home)
            val presenceAwayLabel = stringResource(R.string.playground_presence_away)
            val presenceOffLabel = stringResource(R.string.playground_presence_off)
            LabeledControl(stringResource(R.string.playground_selector_icon_stacked)) {
                VerticalSegmentedSelector(
                    options = Presence.entries.toList(),
                    selected = three, onSelect = { three = it },
                    label = {
                        when (it) {
                            Presence.HOME -> presenceHomeLabel; Presence.AWAY -> presenceAwayLabel; Presence.OFF -> presenceOffLabel
                        }
                    },
                    icon = {
                        when (it) {
                            Presence.HOME -> Icons.Filled.Home; Presence.AWAY -> Icons.Filled.DirectionsRun; Presence.OFF -> Icons.Filled.Block
                        }
                    },
                    accent = accent,
                    isNeutral = { it == Presence.OFF },
                    modifier = Modifier.width(88.dp),
                )
            }
            LabeledControl(stringResource(R.string.playground_selector_6_options)) {
                VerticalSegmentedSelector(
                    options = listOf(1, 2, 3, 4, 5, 6),
                    selected = six, onSelect = { six = it },
                    label = { "Niv. $it" },
                    icon = { Icons.Filled.Star },
                    contentLayout = ControlContentLayout.Horizontal,
                    accent = accent,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        var horizontalSelection by remember { mutableStateOf(Presence.HOME) }
        val horizontalHomeLabel = stringResource(R.string.playground_presence_home)
        val horizontalAwayLabel = stringResource(R.string.playground_presence_away)
        val horizontalOffLabel = stringResource(R.string.playground_presence_off)
        LabeledControl(stringResource(R.string.playground_selector_horizontal)) {
            HorizontalSegmentedSelector(
                options = Presence.entries.toList(),
                selected = horizontalSelection,
                onSelect = { horizontalSelection = it },
                label = {
                    when (it) {
                        Presence.HOME -> horizontalHomeLabel
                        Presence.AWAY -> horizontalAwayLabel
                        Presence.OFF -> horizontalOffLabel
                    }
                },
                icon = {
                    when (it) {
                        Presence.HOME -> Icons.Filled.Home
                        Presence.AWAY -> Icons.Filled.DirectionsRun
                        Presence.OFF -> Icons.Filled.Block
                    }
                },
                accent = accent,
                isNeutral = { it == Presence.OFF },
                modifier = Modifier.width(280.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // 4 · Vertical switches
        SectionTitle(stringResource(R.string.playground_section_switch))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            var on by remember { mutableStateOf(true) }
            var off by remember { mutableStateOf(false) }
            LabeledControl(if (on) stringResource(R.string.playground_switch_on) else stringResource(R.string.playground_switch_off)) {
                VerticalSwitch(
                    checked = on, onCheckedChange = { on = it }, accent = accent,
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl(stringResource(R.string.playground_switch_icon_text)) {
                VerticalSwitch(
                    checked = off, onCheckedChange = { off = it }, accent = accent,
                    icon = { Icons.Filled.PowerSettingsNew },
                    label = { if (it) "ON" else "OFF" },
                    modifier = Modifier.controlSize(),
                )
            }
            LabeledControl(stringResource(R.string.playground_switch_disabled)) {
                VerticalSwitch(
                    checked = true, onCheckedChange = {}, accent = accent, enabled = false,
                    modifier = Modifier.controlSize(),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // 5 · Keypad
        SectionTitle(stringResource(R.string.playground_section_keypad))
        var pinError by remember { mutableStateOf(false) }
        var unlocked by remember { mutableStateOf(false) }
        var keypadEnabled by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.playground_keypad_active), style = AppleTypography.bodyLarge, color = AppleColors.secondary)
            Spacer(Modifier.width(12.dp))
            VerticalSwitch(
                checked = keypadEnabled, onCheckedChange = { keypadEnabled = it }, accent = accent,
                modifier = Modifier.width(44.dp).height(72.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        PinKeypad(
            codeLength = 4,
            title = if (unlocked) stringResource(R.string.playground_keypad_unlocked) else stringResource(R.string.playground_keypad_code_hint),
            subtitle = stringResource(R.string.playground_keypad_wrong_code_hint),
            accent = accent,
            error = pinError,
            onErrorConsumed = { pinError = false },
            enabled = keypadEnabled,
            onSubmit = { entered ->
                if (entered == "1234") unlocked = true else pinError = true
            },
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(28.dp))

        // 6 · Thermostat
        SectionTitle(stringResource(R.string.playground_section_thermostat))
        var tMode by remember { mutableStateOf(ThermostatMode.HEAT_COOL) }
        var low by remember { mutableFloatStateOf(63f) }
        var high by remember { mutableFloatStateOf(70f) }
        var single by remember { mutableFloatStateOf(68f) }
        ThermostatArc(
            mode = tMode,
            target = single, onTargetChange = { single = it },
            lowTarget = low, highTarget = high, onRangeChange = { l, h -> low = l; high = h },
            current = 66f,
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Spacer(Modifier.height(8.dp))
        val thermoOffLabel = stringResource(R.string.playground_thermo_mode_off)
        val thermoCoolLabel = stringResource(R.string.playground_thermo_mode_cool)
        val thermoHeatLabel = stringResource(R.string.playground_thermo_mode_heat)
        val thermoAutoLabel = stringResource(R.string.playground_thermo_mode_auto)
        val modes = listOf(ThermostatMode.OFF, ThermostatMode.COOL, ThermostatMode.HEAT, ThermostatMode.HEAT_COOL)
        WheelPicker(
            options = modes,
            selected = tMode,
            onSelect = { tMode = it },
            label = {
                when (it) {
                    ThermostatMode.OFF -> thermoOffLabel; ThermostatMode.COOL -> thermoCoolLabel
                    ThermostatMode.HEAT -> thermoHeatLabel; ThermostatMode.AUTO, ThermostatMode.HEAT_COOL -> thermoAutoLabel
                }
            },
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        // 7 · Robot vacuum
        SectionTitle(stringResource(R.string.playground_section_vacuum))
        val livingName = stringResource(R.string.playground_vacuum_living_room)
        val kitchenName = stringResource(R.string.playground_vacuum_kitchen)
        val bedroomName = stringResource(R.string.playground_vacuum_bedroom)
        val bathName = stringResource(R.string.playground_vacuum_bathroom)
        val rooms = remember {
            listOf(
                VacuumRoom("living", livingName, Icons.Outlined.Weekend),
                VacuumRoom("kitchen", kitchenName, Icons.Outlined.Kitchen),
                VacuumRoom("bedroom", bedroomName, Icons.Outlined.Bed),
                VacuumRoom("bath", bathName, Icons.Outlined.Bathtub),
            )
        }
        var running by remember { mutableStateOf(true) }
        var mode by remember { mutableStateOf(VacuumMode.VACUUM) }
        var vacRooms by remember { mutableStateOf(setOf<String>()) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.playground_vacuum_name), style = AppleTypography.headlineLarge, color = AppleColors.primary)
            Text(
                if (running) stringResource(R.string.playground_vacuum_cleaning) else stringResource(R.string.playground_vacuum_paused),
                style = AppleTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
            Spacer(Modifier.height(28.dp))
            VacuumRunButton(running = running, onToggle = { running = it })
            Spacer(Modifier.height(28.dp))
            val vacuumLabelVacuum = stringResource(R.string.vacuum_mode_vacuum)
            val vacuumLabelVacAndMop = stringResource(R.string.vacuum_mode_vacuum_and_mop)
            val vacuumLabelVacThenMop = stringResource(R.string.vacuum_mode_vacuum_then_mop)
            val vacuumLabelMop = stringResource(R.string.vacuum_mode_mop)
            val vacuumModeLabels = mapOf(
                VacuumMode.VACUUM to vacuumLabelVacuum,
                VacuumMode.VACUUM_AND_MOP to vacuumLabelVacAndMop,
                VacuumMode.VACUUM_THEN_MOP to vacuumLabelVacThenMop,
                VacuumMode.MOP to vacuumLabelMop,
            )
            val modes = listOf(
                VacuumMode.VACUUM, VacuumMode.VACUUM_AND_MOP,
                VacuumMode.VACUUM_THEN_MOP, VacuumMode.MOP,
            )
            WheelPicker(
                options = modes,
                selected = mode,
                onSelect = { mode = it },
                label = { vacuumModeLabels[it]!! },
                accent = AppleColors.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val inProgressLabel = stringResource(R.string.playground_vacuum_in_progress)
            val queuedLabel = stringResource(R.string.playground_vacuum_queued)
            VacuumStatusChip(if (running) stringResource(R.string.playground_vacuum_preparing) else stringResource(R.string.playground_vacuum_paused), prominent = true)
            Spacer(Modifier.height(20.dp))
            VacuumRoomChips(
                rooms = rooms,
                selected = vacRooms,
                onToggle = { id ->
                    vacRooms = if (id in vacRooms) vacRooms - id else vacRooms + id
                },
                currentRoomId = "living",
                roomState = { if (running) inProgressLabel else queuedLabel },
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))

        // 8 · Accessories
        SectionTitle(stringResource(R.string.playground_section_accessories))
        var deskOn by remember { mutableStateOf(true) }
        var lampOn by remember { mutableStateOf(false) }
        var plugOn by remember { mutableStateOf(true) }
        val accessories = listOf(
            AccessoryItem(
                "desk", stringResource(R.string.playground_accessory_desk), Icons.Outlined.Lightbulb, deskOn,
                subtitle = if (deskOn) "36 %" else stringResource(R.string.playground_accessory_off),
                accent = AppleColors.warning, onToggle = { deskOn = it },
            ),
            AccessoryItem(
                "blinds", stringResource(R.string.playground_accessory_blinds), Icons.Outlined.Blinds, false,
                subtitle = stringResource(R.string.playground_accessory_updating), accent = accent, warning = true,
            ),
            AccessoryItem(
                "lamp", stringResource(R.string.playground_accessory_lamp), Icons.Outlined.Lightbulb, lampOn,
                subtitle = if (lampOn) stringResource(R.string.playground_accessory_on) else stringResource(R.string.playground_accessory_off),
                accent = AppleColors.warning, onToggle = { lampOn = it },
            ),
            AccessoryItem(
                "plug", stringResource(R.string.playground_accessory_plug_tv), Icons.Outlined.Power, plugOn,
                subtitle = if (plugOn) stringResource(R.string.playground_accessory_active) else stringResource(R.string.playground_accessory_inactive),
                accent = accent, onToggle = { plugOn = it },
            ),
            AccessoryItem(
                "plug2", stringResource(R.string.playground_accessory_plug2), Icons.Outlined.Power, false,
                subtitle = stringResource(R.string.playground_accessory_no_response), warning = true,
            ),
        )
        AccessoryGrid(items = accessories)

        Spacer(Modifier.height(40.dp))
    }

        selectedFakePanel?.let { selection ->
            CompositionLocalProvider(
                LocalCallService provides fakeService,
                LocalHaStates provides fakeEntities,
                LocalAreas provides emptyMap(),
            ) {
                when (selection) {
                    is FakePanelSelection.Chip -> ChipActionsPanel(
                        chip = allFakeChips.firstOrNull { it.id == selection.id } ?: selection.chip,
                        onDismiss = { selectedFakePanel = null },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.33f),
                    )
                    is FakePanelSelection.Media -> MediaPlayerView(
                        media = fakeMedia,
                        secondaryMedia = emptyList(),
                        haToken = "",
                        onPlayPause = { fakeMediaPlaying = !fakeMediaPlaying },
                        onPrevious = { fakeTrackIndex = (fakeTrackIndex - 1 + fakeTracks.size) % fakeTracks.size },
                        onNext = { fakeTrackIndex = (fakeTrackIndex + 1) % fakeTracks.size },
                        onVolumeChange = { _, volume -> fakeMediaVolume = volume.toInt().coerceIn(0, 100) },
                        onSecondaryPlayPause = {}, onSecondaryPrevious = {}, onSecondaryNext = {},
                        onSelectSecondary = {}, onSwipePlayer = {}, onJoinPlayer = {}, onUnjoinPlayer = {},
                        onDismiss = { selectedFakePanel = null },
                        modifier = if (fullscreenMedia) {
                            Modifier.fillMaxSize().background(Color.Black)
                        } else {
                            Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.33f)
                        },
                    )
                }
            }
        }
    }
}

/** Real side-panel routing fed by local fake HA entities, for interaction testing offline. */
@Composable
private fun FakePanelLab(
    chips: List<LauncherChip>,
    media: PlayingMedia,
    selected: FakePanelSelection?,
    onSelect: (FakePanelSelection) -> Unit,
) {
    SectionTitle(stringResource(R.string.playground_section_fake_panels))
    Text(stringResource(R.string.playground_fake_panels_hint), style = AppleTypography.bodySmall, color = AppleColors.secondary)
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                row.forEach { chip ->
                    StatusChip(
                        chip = chip,
                        selected = selected?.id == chip.id,
                        onClick = {
                            onSelect(if (chip.id == "media_group") FakePanelSelection.Media(media) else FakePanelSelection.Chip(chip))
                        },
                    )
                }
            }
        }
    }
}

/** Matrix of HA capability combinations. Every chip routes through the production panel. */
@Composable
private fun HaCapabilityLab(
    groups: List<HaCapabilityGroup>,
    selected: FakePanelSelection?,
    onSelect: (LauncherChip) -> Unit,
) {
    SectionTitle(stringResource(R.string.playground_section_ha_capabilities))
    Text(
        stringResource(R.string.playground_ha_capabilities_hint),
        style = AppleTypography.bodySmall,
        color = AppleColors.secondary,
    )
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.title, style = AppleTypography.titleMedium, color = AppleColors.primary)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    group.chips.forEach { chip ->
                        StatusChip(
                            chip = chip,
                            selected = selected?.id == chip.id,
                            onClick = { onSelect(chip) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun fakeCapabilityGroups(entities: Map<String, HaEntity>): List<HaCapabilityGroup> {
    fun chip(id: String, kind: PillKind, icon: String, capability: String): LauncherChip {
        val entity = entities.getValue(id)
        val state = when (kind) {
            PillKind.SIREN -> if (entity.state == "on") "critical" else "ok"
            PillKind.SAFETY -> when (entity.state) {
                "triggered" -> "critical"
                "pending", "arming" -> "warning"
                "disarmed", "disarming" -> "ok"
                else -> "active"
            }
            else -> if (entity.state in setOf("on", "open", "opening", "mowing", "eco")) "active" else "ok"
        }
        return LauncherChip(
            id = "cap_${id.replace('.', '_')}", icon = icon, label = entity.name, value = capability,
            state = state, entityId = id, kind = kind, deviceState = entity.state,
        )
    }
    return listOf(
        HaCapabilityGroup(stringResource(R.string.playground_capability_cover), listOf(
            chip("cover.salon", PillKind.COVER, "cover", stringResource(R.string.playground_capability_position_stop)),
            chip("cover.simple", PillKind.COVER, "cover", stringResource(R.string.playground_capability_on_off)),
            chip("cover.with_stop", PillKind.COVER, "cover", stringResource(R.string.playground_capability_three_actions)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_fan), listOf(
            chip("fan.bureau", PillKind.FAN, "fan", stringResource(R.string.playground_capability_on_off)),
            chip("fan.chambre", PillKind.FAN, "fan", stringResource(R.string.playground_capability_percentage)),
            chip("fan.plafond", PillKind.FAN, "fan", stringResource(R.string.playground_capability_presets)),
            chip("fan.oscillant", PillKind.FAN, "fan", stringResource(R.string.playground_capability_percentage_oscillation)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_climate), listOf(
            chip("climate.salon", PillKind.THERMOSTAT, "temperature", stringResource(R.string.playground_capability_temperature_modes)),
            chip("climate.range", PillKind.THERMOSTAT, "temperature", stringResource(R.string.playground_capability_temperature_range)),
            chip("climate.modes_only", PillKind.THERMOSTAT, "temperature", stringResource(R.string.playground_capability_modes_only)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_vacuum), listOf(
            chip("vacuum.romy", PillKind.VACUUM, "vacuum", stringResource(R.string.playground_capability_suction_modes)),
            chip("vacuum.full", PillKind.VACUUM, "vacuum", stringResource(R.string.playground_capability_pause_dock_locate)),
            chip("vacuum.basic", PillKind.VACUUM, "vacuum", stringResource(R.string.playground_capability_start_only)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_security), listOf(
            chip("lock.entree", PillKind.LOCK, "lock", stringResource(R.string.playground_capability_lock_normal)),
            chip("lock.jammed", PillKind.LOCK, "lock", stringResource(R.string.playground_capability_lock_jammed)),
            chip("alarm_control_panel.maison", PillKind.SAFETY, "shield", stringResource(R.string.playground_capability_alarm_no_code)),
            chip("alarm_control_panel.code", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_capability_alarm_with_code)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.armed_away", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_armed_away)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.armed_home", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_armed_home)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.armed_night", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_armed_night)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.armed_vacation", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_armed_vacation)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.armed_custom_bypass", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_armed_custom_bypass)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.arming", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_arming)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.pending", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_pending)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.triggered", PillKind.SAFETY, "shield", "${stringResource(R.string.playground_alarm_state_triggered)} · ${stringResource(R.string.playground_alarm_test_code_hint)}"),
            chip("alarm_control_panel.disarming", PillKind.SAFETY, "shield", stringResource(R.string.playground_alarm_state_disarming)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_humidifier), listOf(
            chip("humidifier.chambre", PillKind.HUMIDIFIER, "humidity", stringResource(R.string.playground_capability_modes)),
            chip("humidifier.simple", PillKind.HUMIDIFIER, "humidity", stringResource(R.string.playground_capability_on_off)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_water_heater), listOf(
            chip("water_heater.ballon", PillKind.WATER_HEATER, "temperature", stringResource(R.string.playground_capability_temperature_modes)),
            chip("water_heater.simple", PillKind.WATER_HEATER, "temperature", stringResource(R.string.playground_capability_temperature_only)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_valve), listOf(
            chip("valve.eau", PillKind.VALVE, "valve", stringResource(R.string.playground_capability_position_stop)),
            chip("valve.simple", PillKind.VALVE, "valve", stringResource(R.string.playground_capability_on_off)),
            chip("valve.three_way", PillKind.VALVE, "valve", stringResource(R.string.playground_capability_three_actions)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_siren), listOf(
            chip("siren.alarme", PillKind.SIREN, "shield", stringResource(R.string.playground_capability_tones)),
            chip("siren.simple", PillKind.SIREN, "shield", stringResource(R.string.playground_capability_on_off)),
        )),
        HaCapabilityGroup(stringResource(R.string.playground_capability_mower), listOf(
            chip("lawn_mower.jardin", PillKind.LAWN_MOWER, "mower", stringResource(R.string.playground_capability_all_actions)),
            chip("lawn_mower.simple", PillKind.LAWN_MOWER, "mower", stringResource(R.string.playground_capability_without_pause)),
        )),
    )
}

@Composable
private fun fakePanelChips(
    entities: Map<String, HaEntity>,
    mediaPlaying: Boolean,
    mediaTitle: String,
): List<LauncherChip> {
    fun entity(id: String) = entities.getValue(id)
    fun on(id: String) = entity(id).state.equals("on", true)
    fun percent(id: String, key: String) = entity(id).attributes.optInt(key, 0).coerceIn(0, 100)
    fun mode(id: String, key: String) = entity(id).attributes.optString(key)

    val lights = listOf("light.salon", "light.cuisine")
    val lightsOn = lights.count(::on)
    val purifier = entity("fan.purificateur")
    val co2 = entity("sensor.air_co2").state.toIntOrNull() ?: 0
    val pm25 = entity("sensor.air_pm25").state.toIntOrNull() ?: 0
    val airState = when {
        co2 > 1_000 && pm25 > 25 -> "critical"
        co2 > 1_000 || pm25 > 25 -> "warning"
        else -> "active"
    }
    val climate = entity("climate.salon")
    val climateTarget = climate.attributes.optDouble("temperature", 21.5)
    val vacuum = entity("vacuum.romy")
    val fanPercent = entity("fan.chambre")
    val fanSimple = entity("fan.bureau")
    val fanModes = entity("fan.plafond")
    val cover = entity("cover.salon")
    val lock = entity("lock.entree")
    val socket = entity("switch.tv")
    val alarm = entity("alarm_control_panel.maison")

    return listOf(
        LauncherChip("lights_group", "light", "Lumières", if (lightsOn == 0) "Éteintes" else "$lightsOn allumée${if (lightsOn > 1) "s" else ""}", if (lightsOn > 0) "active" else "ok", details = lights.map { id ->
            val light = entity(id); val active = light.state == "on"
            PillDetail(light.name, if (active) "${((light.attributes.optInt("brightness") / 255f) * 100).toInt()} %" else "Éteinte", id, active)
        }, kind = PillKind.LIGHTS, deviceState = if (lightsOn > 0) "on" else "off"),
        LauncherChip("purifier_group", "air", "Purificateur", if (purifier.state == "on") mode(purifier.entityId, "preset_mode").replaceFirstChar { it.uppercase() } else "Éteint", airState, entityId = purifier.entityId, kind = PillKind.PURIFIER,
            details = listOf(
                PillDetail("CO₂", "$co2 ppm", "sensor.air_co2"),
                PillDetail("PM2.5", "$pm25 µg/m³", "sensor.air_pm25"),
                PillDetail("Filtre", "82 %", "sensor.air_filter"),
            ), deviceState = purifier.state),
        LauncherChip("lock_test", "lock", "Porte d’entrée", if (lock.state == "locked") "Verrouillée" else "Déverrouillée", if (lock.state == "locked") "ok" else "critical", entityId = lock.entityId, kind = PillKind.LOCK, deviceState = lock.state),
        LauncherChip("cover_test", "cover", "Volet salon", "${percent(cover.entityId, "current_position")} %", if (cover.state == "closed") "ok" else "active", entityId = cover.entityId, kind = PillKind.COVER, deviceState = cover.state),
        LauncherChip("thermostat_test", "temperature", "Thermostat", "${if (climateTarget % 1.0 == 0.0) climateTarget.toInt() else climateTarget} °C", if (climate.state == "off") "ok" else "active", entityId = climate.entityId, kind = PillKind.THERMOSTAT, deviceState = climate.state),
        LauncherChip("vacuum_test", "vacuum", "Aspirateur", vacuum.state.replaceFirstChar { it.uppercase() }, if (vacuum.state in setOf("cleaning", "returning")) "active" else "ok", entityId = vacuum.entityId, kind = PillKind.VACUUM, batteryPercent = vacuum.attributes.optInt("battery_level"), deviceState = vacuum.state),
        LauncherChip("fan_test", "fan", "Ventilateur %", if (fanPercent.state == "on") "${percent(fanPercent.entityId, "percentage")} %" else "Éteint", if (fanPercent.state == "on") "active" else "ok", entityId = fanPercent.entityId, kind = PillKind.FAN, deviceState = fanPercent.state),
        LauncherChip("fan_on_off_test", "fan", "Ventilateur simple", if (fanSimple.state == "on") "Allumé" else "Éteint", if (fanSimple.state == "on") "active" else "ok", entityId = fanSimple.entityId, kind = PillKind.FAN, deviceState = fanSimple.state),
        LauncherChip("fan_modes_test", "fan", "Ventilateur 3 vitesses", if (fanModes.state == "on") "Niveau ${mode(fanModes.entityId, "preset_mode")}" else "Éteint", if (fanModes.state == "on") "active" else "ok", entityId = fanModes.entityId, kind = PillKind.FAN, deviceState = fanModes.state),
        LauncherChip("switch_test", "switch", "Prise TV", if (socket.state == "on") "Allumée" else "Éteinte", if (socket.state == "on") "active" else "ok", entityId = socket.entityId, kind = PillKind.SWITCH, deviceState = socket.state),
        LauncherChip("alarm_test", "shield", "Alarme", alarm.state.replace('_', ' ').replaceFirstChar { it.uppercase() }, if (alarm.state == "triggered") "critical" else if (alarm.state == "disarmed") "info" else "active", entityId = alarm.entityId, kind = PillKind.SAFETY, deviceState = alarm.state),
        LauncherChip("washer_test", "washer", "Machine à laver", "Rinçage", "active", entityId = "sensor.lave_linge_state", kind = PillKind.APPLIANCE, progress = 0.62f, details = listOf(PillDetail("Cycle", "Coton"), PillDetail("Fin estimée", "14:35"), PillDetail("Essorage", "1 200 tr/min"))),
        LauncherChip("media_group", "media", "Musique", mediaTitle, if (mediaPlaying) "active" else "ok", entityId = "media_player.salon", kind = PillKind.MEDIA, deviceState = if (mediaPlaying) "playing" else "paused"),
    )
}

@Composable
private fun rememberFakePanelEntities(): SnapshotStateMap<String, HaEntity> = remember {
    mutableStateMapOf<String, HaEntity>().apply { putAll(listOf(
        fakeEntity("light.salon", "on", "Salon", "{\"brightness\":184,\"color_temp_kelvin\":3800,\"supported_color_modes\":[\"color_temp\"]}"),
        fakeEntity("light.cuisine", "off", "Cuisine", "{\"brightness\":0}"),
        fakeEntity("fan.purificateur", "on", "Purificateur", "{\"preset_mode\":\"auto\",\"preset_modes\":[\"auto\",\"sleep\",\"manual\",\"pet\"]}"),
        fakeEntity("person.alex", "home", "Alex", "{}"),
        fakeEntity("sensor.house_power", "846", "Puissance maison", "{\"unit_of_measurement\":\"W\"}"),
        fakeEntity("lock.entree", "locked", "Porte d’entrée", "{}"),
        fakeEntity("lock.jammed", "jammed", "Serrure bloquée", "{}"),
        fakeEntity("cover.salon", "open", "Volet salon", "{\"current_position\":64,\"supported_features\":15}"),
        fakeEntity("cover.simple", "closed", "Volet simple", "{\"supported_features\":3}"),
        fakeEntity("cover.with_stop", "open", "Volet avec arrêt", "{\"supported_features\":11}"),
        fakeEntity("climate.salon", "heat", "Thermostat", "{\"current_temperature\":20.8,\"temperature\":21.5,\"min_temp\":7,\"max_temp\":35,\"hvac_modes\":[\"off\",\"heat\",\"cool\",\"heat_cool\"]}"),
        fakeEntity("climate.range", "heat_cool", "Thermostat double consigne", "{\"current_temperature\":22,\"target_temp_low\":19,\"target_temp_high\":25,\"min_temp\":7,\"max_temp\":35,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat_cool\"],\"supported_features\":2}"),
        fakeEntity("climate.modes_only", "fan_only", "Thermostat sans consigne", "{\"current_temperature\":23,\"hvac_modes\":[\"off\",\"dry\",\"fan_only\"],\"supported_features\":0}"),
        fakeEntity("vacuum.romy", "cleaning", "Romy", "{\"battery_level\":78,\"fan_speed\":\"standard\",\"fan_speed_list\":[\"silent\",\"standard\",\"turbo\"]}"),
        fakeEntity("vacuum.full", "cleaning", "Aspirateur complet", "{\"battery_level\":64,\"status\":\"Nettoyage\",\"fan_speed\":\"standard\",\"fan_speed_list\":[\"silent\",\"standard\",\"turbo\"],\"supported_features\":8732}"),
        fakeEntity("vacuum.basic", "docked", "Aspirateur simple", "{\"status\":\"Sur la base\",\"supported_features\":8192}"),
        fakeEntity("fan.chambre", "on", "Ventilateur", "{\"percentage\":40,\"percentage_step\":10,\"supported_features\":1}"),
        fakeEntity("fan.bureau", "on", "Ventilateur bureau", "{}"),
        fakeEntity("fan.plafond", "on", "Ventilateur plafond", "{\"preset_mode\":\"2\",\"preset_modes\":[\"1\",\"2\",\"3\"],\"supported_features\":8}"),
        fakeEntity("fan.oscillant", "on", "Ventilateur oscillant", "{\"percentage\":55,\"percentage_step\":5,\"oscillating\":true,\"supported_features\":3}"),
        fakeEntity("switch.tv", "on", "Prise TV", "{}"),
        fakeEntity("humidifier.chambre", "on", "Humidificateur", "{\"humidity\":55,\"current_humidity\":48,\"min_humidity\":30,\"max_humidity\":80,\"target_humidity_step\":5,\"mode\":\"auto\",\"available_modes\":[\"auto\",\"sleep\"],\"supported_features\":1}"),
        fakeEntity("humidifier.simple", "off", "Humidificateur simple", "{\"humidity\":45,\"current_humidity\":51,\"min_humidity\":30,\"max_humidity\":80,\"target_humidity_step\":5,\"supported_features\":0}"),
        fakeEntity("water_heater.ballon", "eco", "Chauffe-eau", "{\"temperature\":55,\"current_temperature\":51,\"min_temp\":40,\"max_temp\":65,\"target_temperature_step\":1,\"current_operation\":\"eco\",\"operation_list\":[\"eco\",\"electric\",\"off\"],\"supported_features\":11}"),
        fakeEntity("water_heater.simple", "heat", "Chauffe-eau simple", "{\"temperature\":52,\"current_temperature\":49,\"min_temp\":40,\"max_temp\":65,\"target_temperature_step\":1,\"supported_features\":1}"),
        fakeEntity("valve.eau", "open", "Vanne principale", "{\"current_valve_position\":42,\"reports_position\":true,\"supported_features\":15}"),
        fakeEntity("valve.simple", "closed", "Vanne tout-ou-rien", "{\"supported_features\":3}"),
        fakeEntity("valve.three_way", "open", "Vanne avec arrêt", "{\"supported_features\":11}"),
        fakeEntity("siren.alarme", "off", "Sirène", "{\"supported_features\":31,\"available_tones\":[\"alarm\",\"doorbell\"]}"),
        fakeEntity("siren.simple", "off", "Sirène simple", "{\"supported_features\":0}"),
        fakeEntity("lawn_mower.jardin", "docked", "Tondeuse", "{\"supported_features\":7}"),
        fakeEntity("lawn_mower.simple", "docked", "Tondeuse sans pause", "{\"supported_features\":5}"),
        fakeEntity("alarm_control_panel.maison", "disarmed", "Alarme sans code", "{\"supported_features\":63}"),
        fakeEntity("alarm_control_panel.code", "disarmed", "Alarme avec code", "{\"code_format\":\"number\",\"code_arm_required\":true,\"supported_features\":63}"),
        fakeAlarmState("armed_away", "Armée · absence"),
        fakeAlarmState("armed_home", "Armée · présence"),
        fakeAlarmState("armed_night", "Armée · nuit"),
        fakeAlarmState("armed_vacation", "Armée · vacances"),
        fakeAlarmState("armed_custom_bypass", "Armée · contournement"),
        fakeAlarmState("arming", "Armement en cours"),
        fakeAlarmState("pending", "Événement détecté"),
        fakeAlarmState("triggered", "Alarme déclenchée"),
        fakeAlarmState("disarming", "Désarmement en cours"),
        fakeEntity("sensor.lave_linge_state", "running", "Machine à laver", "{\"progress\":62,\"phase\":\"rinse\",\"remaining_time\":\"Reste 38 min\",\"program\":\"Coton\",\"temperature\":40,\"spin_speed\":1200}"),
        fakeEntity("sensor.air_co2", "620", "CO₂", "{\"device_class\":\"carbon_dioxide\",\"unit_of_measurement\":\"ppm\"}"),
        fakeEntity("sensor.air_pm25", "4", "PM2.5", "{\"device_class\":\"pm25\",\"unit_of_measurement\":\"µg/m³\"}"),
        fakeEntity("sensor.air_filter", "82", "Filtre", "{\"unit_of_measurement\":\"%\"}"),
    ).associateBy { it.entityId }) }
}

@Composable
private fun rememberFakeCallService(entities: SnapshotStateMap<String, HaEntity>): CallService = remember(entities) {
    object : CallService {
        override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) {
            val id = entityId ?: return
            if (domain == "scene" || domain == "script") {
                // Give scene buttons an immediately visible whole-home effect.
                val cinema = id.contains("cinema")
                listOf("light.salon", "light.cuisine").forEachIndexed { index, lightId ->
                    val light = entities[lightId] ?: return@forEachIndexed
                    val attrs = JSONObject(light.attributes.toString())
                    val enabled = !id.contains("tout_eteindre") && (!cinema || index == 0)
                    attrs.put("brightness", if (enabled) if (cinema) 54 else 190 else 0)
                    entities[lightId] = HaEntity(lightId, if (enabled) "on" else "off", attrs, light.lastChanged)
                }
                return
            }
            val current = entities[id] ?: return
            val attributes = JSONObject(current.attributes.toString())
            fun replace(state: String = current.state, mutate: JSONObject.() -> Unit = {}) {
                attributes.mutate()
                entities[id] = HaEntity(id, state, attributes, current.lastChanged)
            }
            when (domain) {
                "lock" -> replace(if (service == "lock") "locked" else "unlocked")
                "switch", "light" -> when (service) {
                    "turn_on" -> replace("on") { data?.forEach { (key, value) -> put(key, value) } }
                    "turn_off" -> replace("off")
                    "toggle" -> replace(if (current.state == "on") "off" else "on")
                }
                "fan" -> when (service) {
                    "turn_on" -> replace("on")
                    "turn_off" -> replace("off")
                    "toggle" -> replace(if (current.state == "on") "off" else "on")
                    "set_percentage" -> replace(if ((data?.get("percentage") as? Number)?.toInt() == 0) "off" else "on") { put("percentage", (data?.get("percentage") as? Number)?.toInt() ?: 0) }
                    "set_preset_mode" -> replace("on") { put("preset_mode", data?.get("preset_mode")?.toString()) }
                    "oscillate" -> replace { put("oscillating", data?.get("oscillating") as? Boolean ?: false) }
                }
                "cover" -> when (service) {
                    "open_cover" -> replace("open") { put("current_position", 100) }
                    "close_cover" -> replace("closed") { put("current_position", 0) }
                    "set_cover_position" -> replace("open") { put("current_position", (data?.get("position") as? Number)?.toInt() ?: 0) }
                    "stop_cover" -> replace("open")
                }
                "climate" -> when (service) {
                    "set_hvac_mode" -> replace(data?.get("hvac_mode")?.toString() ?: current.state)
                    "set_temperature" -> replace { data?.forEach { (key, value) -> put(key, value) } }
                }
                "vacuum" -> when (service) {
                    "start" -> replace("cleaning")
                    "pause" -> replace("paused")
                    "stop" -> replace("idle")
                    "return_to_base" -> replace("returning")
                    "set_fan_speed" -> replace { put("fan_speed", data?.get("fan_speed")?.toString()) }
                }
                "humidifier" -> when (service) {
                    "turn_on" -> replace("on")
                    "turn_off" -> replace("off")
                    "set_humidity" -> replace { put("humidity", (data?.get("humidity") as? Number)?.toInt() ?: attributes.optInt("humidity")) }
                    "set_mode" -> replace("on") { put("mode", data?.get("mode")?.toString()) }
                }
                "water_heater" -> when (service) {
                    "turn_on" -> replace("on")
                    "turn_off" -> replace("off")
                    "set_temperature" -> replace { put("temperature", (data?.get("temperature") as? Number)?.toDouble() ?: attributes.optDouble("temperature")) }
                    "set_operation_mode" -> {
                        val mode = data?.get("operation_mode")?.toString() ?: current.state
                        replace(mode) { put("current_operation", mode) }
                    }
                    "set_away_mode" -> replace { put("away_mode", data?.get("away_mode") ?: true) }
                }
                "valve" -> when (service) {
                    "open_valve" -> replace("open") { if (has("current_valve_position")) put("current_valve_position", 100) }
                    "close_valve" -> replace("closed") { if (has("current_valve_position")) put("current_valve_position", 0) }
                    "stop_valve" -> replace("open")
                    "set_valve_position" -> {
                        val position = (data?.get("position") as? Number)?.toInt() ?: 0
                        replace(if (position == 0) "closed" else "open") { put("current_valve_position", position) }
                    }
                }
                "siren" -> when (service) {
                    "turn_on" -> replace("on") { data?.forEach { (key, value) -> put(key, value) } }
                    "turn_off" -> replace("off")
                }
                "lawn_mower" -> replace(when (service) {
                    "start_mowing" -> "mowing"
                    "pause" -> "paused"
                    "dock" -> "docked"
                    else -> current.state
                })
                "alarm_control_panel" -> {
                    // Every code-protected fixture accepts 1234. Any other entry intentionally
                    // leaves the state unchanged so the production keypad's rejection animation
                    // can be exercised in the Playground too.
                    val protected = attributes.optString("code_format").isNotBlank()
                    if (protected && data?.get("code")?.toString() != "1234") return
                    replace(when (service) {
                        "alarm_disarm" -> "disarmed"
                        "alarm_arm_home" -> "armed_home"
                        "alarm_arm_night" -> "armed_night"
                        "alarm_arm_vacation" -> "armed_vacation"
                        "alarm_arm_custom_bypass" -> "armed_custom_bypass"
                        "alarm_trigger" -> "triggered"
                        else -> "armed_away"
                    })
                }
            }
        }
    }
}

private fun fakeAlarmState(state: String, name: String): HaEntity = fakeEntity(
    id = "alarm_control_panel.$state",
    state = state,
    name = name,
    attributes = "{\"code_format\":\"number\",\"code_arm_required\":true,\"supported_features\":63}",
)

private fun fakeEntity(id: String, state: String, name: String, attributes: String): HaEntity =
    HaEntity(id, state, JSONObject(attributes).put("friendly_name", name))

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = AppleTypography.titleLarge, color = AppleColors.primary)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun LabeledControl(caption: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(8.dp))
        Text(caption, style = AppleTypography.labelSmall, color = AppleColors.tertiary)
    }
}
