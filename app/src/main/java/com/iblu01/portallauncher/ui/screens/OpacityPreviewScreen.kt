package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.ClockScreen
import com.iblu01.portallauncher.ui.components.WeatherGlyph
import com.iblu01.portallauncher.ui.components.WeatherUi
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/** Frozen mock content used to judge the overlay against a real wallpaper — no HA dependency. */
private val previewTemperatures = TemperatureSummary("22,5°", "24,5°", "19°")

/**
 * Full-screen replica of the launcher home over the user's real wallpaper, with a big vertical
 * slider to set the background overlay opacity live. The content is static mock data; only the
 * scrim reacts. Value maps 0–100 % onto the [0f, 0.6f] alpha range (the [Prefs] clamp).
 *
 * @param initialOpacity current alpha in `0f..0.6f`.
 * @param onOpacityCommit persist the chosen alpha (fired on slider release).
 * @param onClose finish the activity.
 */
@Composable
fun OpacityPreviewScreen(
    backgroundMode: String,
    initialOpacity: Float,
    onOpacityCommit: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    val previewWeather = WeatherUi(
        temp = "19°",
        indoorTemp = "22°",
        city = stringResource(R.string.opacity_preview_weather_city),
        condition = stringResource(R.string.opacity_preview_weather_condition),
        glyph = WeatherGlyph("clear-day"),
    )

    Box(modifier.fillMaxSize()) {
        // 1 · Real wallpaper.
        AmbientBackground(
            mode = backgroundMode,
            overlayOpacity = opacity,
            modifier = Modifier.fillMaxSize(),
        )

        // 3 · Same bottom gradient as home, so the mock pills read as they do on the launcher.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.28f),
                            0.72f to Color.Black.copy(alpha = 0.68f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        )
                    )
                )
        )

        // 4 · The launcher home content itself, fed frozen mock data.
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
        )

        // 5 · The opacity slider (matches the mockup: dark track, light fill, grip, "45 %").
        VerticalFillSlider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { onOpacityCommit(it) },
            valueRange = 0f..0.6f,
            accent = Color(0xFFEDEDED),
            hapticSteps = 20,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .fillMaxHeight(0.6f)
                .width(96.dp),
        )

        // 6 · Close button, aligned with the clock editor.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(AppleColors.frostedFill)
                .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.opacity_preview_close_desc),
                tint = AppleColors.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
