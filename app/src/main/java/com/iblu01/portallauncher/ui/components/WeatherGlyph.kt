package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** A bundled Meteocons asset name. Assets come from @meteocons/svg-static 0.1.0 (MIT). */
@JvmInline
value class WeatherGlyph(val assetName: String = "not-available")

/** Renders an official static Meteocons icon from the APK, including when the launcher is offline. */
@Composable
fun WeatherIcon(glyph: WeatherGlyph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model = remember(glyph.assetName) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/meteocons/flat/${glyph.assetName}.svg")
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
