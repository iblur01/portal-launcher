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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val contentWidth by animateFloatAsState(
        targetValue = if (selectedFakePanel == null) 1f else 0.67f,
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

        FakePanelLab(
            selected = selectedFakePanel,
            onSelect = { next -> selectedFakePanel = next.takeUnless { it.id == selectedFakePanel?.id } },
        )

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
            val entities = rememberFakePanelEntities()
            val noOpService = remember {
                object : CallService {
                    override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) = Unit
                }
            }
            CompositionLocalProvider(
                LocalCallService provides noOpService,
                LocalHaStates provides entities,
                LocalAreas provides emptyMap(),
            ) {
                when (selection) {
                    is FakePanelSelection.Chip -> ChipActionsPanel(
                        chip = selection.chip,
                        onDismiss = { selectedFakePanel = null },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.33f),
                    )
                    is FakePanelSelection.Media -> MediaPlayerView(
                        media = selection.media,
                        secondaryMedia = emptyList(),
                        haToken = "",
                        onPlayPause = {}, onPrevious = {}, onNext = {},
                        onVolumeChange = { _, _ -> },
                        onSecondaryPlayPause = {}, onSecondaryPrevious = {}, onSecondaryNext = {},
                        onSelectSecondary = {}, onSwipePlayer = {}, onJoinPlayer = {}, onUnjoinPlayer = {},
                        onDismiss = { selectedFakePanel = null },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.33f),
                    )
                }
            }
        }
    }
}

/** Real side-panel routing fed by local fake HA entities, for interaction testing offline. */
@Composable
private fun FakePanelLab(selected: FakePanelSelection?, onSelect: (FakePanelSelection) -> Unit) {
    val chips = remember {
        listOf(
            LauncherChip("lights_group", "light", "Lumières", "3 allumées", "active", details = listOf(
                PillDetail("Salon", "72 %", "light.salon", true),
                PillDetail("Cuisine", "Éteinte", "light.cuisine", false),
            ), kind = PillKind.LIGHTS),
            LauncherChip("purifier_group", "air", "Purificateur", "Auto · air bon", "active", entityId = "fan.purificateur", kind = PillKind.PURIFIER,
                details = listOf(PillDetail("Filtre", "82 %"))),
            LauncherChip("scenes_group", "scene", "Scènes", "4 ambiances", "ok", kind = PillKind.SCENE, details = listOf(
                PillDetail("Soirée", "", "scene.soiree"), PillDetail("Lecture", "", "scene.lecture"),
                PillDetail("Cinéma", "", "scene.cinema"), PillDetail("Tout éteindre", "", "script.tout_eteindre"),
            )),
            LauncherChip("lock_test", "lock", "Porte d’entrée", "Verrouillée", "ok", entityId = "lock.entree", kind = PillKind.LOCK),
            LauncherChip("cover_test", "cover", "Volet salon", "64 %", "active", entityId = "cover.salon", kind = PillKind.COVER),
            LauncherChip("thermostat_test", "temperature", "Thermostat", "21,5 °C", "active", entityId = "climate.salon", kind = PillKind.THERMOSTAT),
            LauncherChip("vacuum_test", "vacuum", "Aspirateur", "Nettoyage", "active", entityId = "vacuum.romy", kind = PillKind.VACUUM, batteryPercent = 78),
            LauncherChip("fan_test", "fan", "Ventilateur %", "40 %", "active", entityId = "fan.chambre", kind = PillKind.FAN),
            LauncherChip("fan_on_off_test", "fan", "Ventilateur simple", "Allumé", "active", entityId = "fan.bureau", kind = PillKind.FAN),
            LauncherChip("fan_modes_test", "fan", "Ventilateur 3 vitesses", "Niveau 2", "active", entityId = "fan.plafond", kind = PillKind.FAN),
            LauncherChip("switch_test", "switch", "Prise TV", "Allumée", "active", entityId = "switch.tv", kind = PillKind.SWITCH),
            LauncherChip("alarm_test", "shield", "Alarme", "Désarmée", "info", entityId = "alarm_control_panel.maison", kind = PillKind.SAFETY),
            LauncherChip("washer_test", "washer", "Machine à laver", "Rinçage", "active", entityId = "sensor.lave_linge_state", kind = PillKind.APPLIANCE,
                progress = 0.62f, details = listOf(PillDetail("Cycle", "Coton"), PillDetail("Fin estimée", "14:35"), PillDetail("Essorage", "1 200 tr/min"))),
            LauncherChip("air_group", "air", "Qualité de l’air", "Bonne · 620 ppm", "active", details = listOf(
                PillDetail("CO₂", "620 ppm"), PillDetail("Humidité", "46 %"), PillDetail("PM2.5", "4 µg/m³"),
            ), kind = PillKind.AIR),
            LauncherChip("generic_test", "sensor", "Capteur balcon", "18,2 °C", "info", kind = PillKind.GENERIC,
                details = listOf(PillDetail("Température", "18,2 °C"), PillDetail("Humidité", "61 %"))),
            LauncherChip("media_group", "media", "Musique", "Midnight City", "active", entityId = "media_player.salon", kind = PillKind.MEDIA),
        )
    }
    val fakeMedia = remember {
        PlayingMedia(
            entityId = "media_player.salon", title = "Midnight City", artist = "M83",
            album = "Hurry Up, We're Dreaming", state = "playing", coverUrl = null,
            volumePercent = 38, isMuted = false, playerNames = listOf("Salon"),
            players = listOf(MediaPlayerVolume("media_player.salon", "Salon", 38, false)),
        )
    }
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
                            onSelect(if (chip.id == "media_group") FakePanelSelection.Media(fakeMedia) else FakePanelSelection.Chip(chip))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFakePanelEntities(): Map<String, HaEntity> = remember {
    listOf(
        fakeEntity("light.salon", "on", "Salon", "{\"brightness\":184,\"color_temp_kelvin\":3800,\"supported_color_modes\":[\"color_temp\"]}"),
        fakeEntity("light.cuisine", "off", "Cuisine", "{\"brightness\":0}"),
        fakeEntity("fan.purificateur", "on", "Purificateur", "{\"preset_mode\":\"auto\",\"preset_modes\":[\"auto\",\"sleep\",\"manual\",\"pet\"]}"),
        fakeEntity("person.alex", "home", "Alex", "{}"),
        fakeEntity("sensor.house_power", "846", "Puissance maison", "{\"unit_of_measurement\":\"W\"}"),
        fakeEntity("lock.entree", "locked", "Porte d’entrée", "{}"),
        fakeEntity("cover.salon", "open", "Volet salon", "{\"current_position\":64,\"supported_features\":15}"),
        fakeEntity("climate.salon", "heat", "Thermostat", "{\"current_temperature\":20.8,\"temperature\":21.5,\"min_temp\":7,\"max_temp\":35,\"hvac_modes\":[\"off\",\"heat\",\"cool\",\"heat_cool\"]}"),
        fakeEntity("vacuum.romy", "cleaning", "Romy", "{\"battery_level\":78,\"fan_speed\":\"standard\",\"fan_speed_list\":[\"silent\",\"standard\",\"turbo\"]}"),
        fakeEntity("fan.chambre", "on", "Ventilateur", "{\"percentage\":40,\"percentage_step\":10,\"supported_features\":1}"),
        fakeEntity("fan.bureau", "on", "Ventilateur bureau", "{}"),
        fakeEntity("fan.plafond", "on", "Ventilateur plafond", "{\"preset_mode\":\"2\",\"preset_modes\":[\"1\",\"2\",\"3\"],\"supported_features\":8}"),
        fakeEntity("switch.tv", "on", "Prise TV", "{}"),
        fakeEntity("alarm_control_panel.maison", "disarmed", "Alarme maison", "{\"code_format\":\"number\",\"code_arm_required\":false,\"supported_features\":15}"),
        fakeEntity("sensor.lave_linge_state", "rinse", "Machine à laver", "{}"),
    ).associateBy { it.entityId }
}

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
