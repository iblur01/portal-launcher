package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.ClockScreen
import com.iblu01.portallauncher.ui.components.WeatherGlyph
import com.iblu01.portallauncher.ui.components.WeatherUi
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint
import com.iblu01.portallauncher.ui.theme.clockFontFamily
import kotlin.math.roundToInt

private val previewWeather = WeatherUi(temp = "19°", indoorTemp = "22°", glyph = WeatherGlyph("clear-day"))
private val previewTemperatures = TemperatureSummary("22,5°", "24,5°", "19°")

/**
 * Full-screen live editor for the clock theme: the launcher home (frozen mock data) over the real
 * wallpaper, with a docked control panel. Every control updates the live clock and persists via
 * [onThemeChange]. [LauncherActivity] re-reads the theme on resume, so home restyles on return.
 */
@Composable
fun ClockThemeScreen(
    backgroundMode: String,
    overlayOpacity: Float,
    initialTheme: ClockTheme,
    onThemeChange: (ClockTheme) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var theme by remember { mutableStateOf(initialTheme) }
    fun update(next: ClockTheme) { theme = next; onThemeChange(next) }

    Box(modifier.fillMaxSize()) {
        AmbientBackground(mode = backgroundMode, modifier = Modifier.fillMaxSize())
        if (backgroundMode != "neutral") {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayOpacity)))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        )
                    )
                )
        )

        ClockScreen(
            backgroundMode = backgroundMode,
            weather = previewWeather,
            temperatures = previewTemperatures,
            chips = emptyList(),
            onTap = {},
            onLongPress = {},
            pillsExpanded = false,
            onPillsExpandedChange = {},
            drawBackground = false,
            clockTheme = theme,
        )

        // Docked control panel.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.44f)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Font picker.
            ControlLabel(stringResource(R.string.clock_theme_label_font))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockFont.entries.forEach { font ->
                    val selected = font == theme.font
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AppleColors.accent else AppleColors.frostedFill)
                            .border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(14.dp))
                            .clickable { update(theme.copy(font = font)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            font.label,
                            style = AppleTypography.bodyLarge.copy(
                                fontFamily = clockFontFamily(font, FontWeight.Medium),
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                            ),
                            color = if (selected) Color.White else AppleColors.primary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Weight.
            LabeledSlider(
                label = stringResource(R.string.clock_theme_label_weight),
                value = theme.weight.toFloat(),
                range = ClockTheme.WeightRange,
                valueText = theme.weight.toString(),
                onChange = { update(theme.copy(weight = (it / 100f).roundToInt() * 100)) },
            )
            // Size.
            LabeledSlider(
                label = stringResource(R.string.clock_theme_label_size),
                value = theme.size,
                range = ClockTheme.SizeRange,
                valueText = "${theme.size.roundToInt()}",
                onChange = { update(theme.copy(size = it)) },
            )
            // Letter spacing.
            LabeledSlider(
                label = stringResource(R.string.clock_theme_label_letter_spacing),
                value = theme.letterSpacing,
                range = ClockTheme.LetterSpacingRange,
                valueText = "%.1f".format(theme.letterSpacing),
                onChange = { update(theme.copy(letterSpacing = it)) },
            )
            // Vertical gap between date / time / weather-temperature rows.
            LabeledSlider(
                label = stringResource(R.string.clock_theme_label_element_spacing),
                value = theme.elementSpacing,
                range = ClockTheme.ElementSpacingRange,
                valueText = "%.1f×".format(theme.elementSpacing),
                onChange = { update(theme.copy(elementSpacing = it)) },
            )

            // Tint.
            ControlLabel(stringResource(R.string.clock_theme_label_color))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ClockTint.entries.forEach { tint ->
                    val selected = tint == theme.tint
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(tint.color)
                            .border(
                                width = if (selected) 3.dp else 0.5.dp,
                                color = if (selected) Color.White else AppleColors.frostedBorder,
                                shape = CircleShape,
                            )
                            .clickable { update(theme.copy(tint = tint)) },
                    )
                }
            }

            // 24h / 12h.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlLabel(stringResource(R.string.clock_theme_label_format_24h))
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = theme.format24h,
                    onCheckedChange = { update(theme.copy(format24h = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AppleColors.accent),
                )
            }

            ControlLabel(stringResource(R.string.clock_theme_label_date_format))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockDateFormat.entries.forEach { format ->
                    val selected = format == theme.dateFormat
                    val label = when (format) {
                        ClockDateFormat.LONG -> stringResource(R.string.clock_date_format_long)
                        ClockDateFormat.DAY_MONTH_YEAR -> stringResource(R.string.clock_date_format_day_month_year)
                        ClockDateFormat.MONTH_DAY_YEAR -> stringResource(R.string.clock_date_format_month_day_year)
                        ClockDateFormat.ISO -> stringResource(R.string.clock_date_format_iso)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AppleColors.accent else AppleColors.frostedFill)
                            .border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(14.dp))
                            .clickable { update(theme.copy(dateFormat = format)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(label, style = AppleTypography.bodyLarge, color = if (selected) Color.White else AppleColors.primary)
                    }
                }
            }
        }

        // Close button.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(AppleColors.frostedFill)
                .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.clock_theme_close), tint = AppleColors.secondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ControlLabel(text: String) {
    Text(text, style = AppleTypography.bodySmall, color = AppleColors.secondary)
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ControlLabel(label)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = AppleTypography.bodySmall, color = AppleColors.primary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AppleColors.primary,
                activeTrackColor = AppleColors.accent,
                inactiveTrackColor = AppleColors.quaternary,
            ),
        )
    }
}
