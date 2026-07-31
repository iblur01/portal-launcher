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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.components.controls.AccessoryGrid
import com.iblu01.portallauncher.ui.components.controls.AccessoryItem
import com.iblu01.portallauncher.ui.components.controls.ControlContentLayout
import com.iblu01.portallauncher.ui.components.controls.FillOrigin
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

/** Named accents so adaptivity is obvious at a glance. */

private enum class Presence { HOME, AWAY, OFF }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(AppleColors.background)
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
                    ThermostatMode.HEAT -> thermoHeatLabel; ThermostatMode.HEAT_COOL -> thermoAutoLabel
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
}

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
