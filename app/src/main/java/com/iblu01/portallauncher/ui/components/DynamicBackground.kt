package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iblu01.portallauncher.PortalApp
import com.iblu01.portallauncher.R

val backgroundModes = listOf(
    "system" to R.string.bg_mode_system,
    "neutral" to R.string.bg_mode_neutral,
    "custom" to R.string.bg_mode_custom,
    "immich" to R.string.bg_mode_immich,
)

@Composable
fun AmbientBackground(mode: String, wallpaperVersion: Int = 0, modifier: Modifier = Modifier) {
    AmbientBackground(mode, wallpaperVersion, overlayOpacity = 0f, modifier = modifier)
}

/** Draws the source and its readability veil as one indivisible, full-surface background. */
@Composable
fun AmbientBackground(
    mode: String,
    wallpaperVersion: Int = 0,
    overlayOpacity: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (mode) {
            // Android draws static and live wallpapers in the activity's wallpaper window layer.
            "system" -> Box(Modifier.fillMaxSize())
            "custom" -> CustomWallpaper(wallpaperVersion, Modifier.fillMaxSize())
            "immich" -> ImmichBackground(Modifier.fillMaxSize())
            else -> NeutralGradient(Modifier.fillMaxSize())
        }
        if (mode != "neutral" && overlayOpacity > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayOpacity.coerceIn(0f, 1f))),
            )
        }
    }
}

@Composable
private fun NeutralGradient(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF1B2026), Color(0xFF05070A)),
                radius = 1400f,
            ),
        ),
    )
}

/**
 * The photo the user picked, drawn by the launcher itself — the only background that works on
 * devices whose OS has no wallpaper service. [wallpaperVersion] plus the file's lastModified()
 * form the cache key so replacing the photo repaints instead of showing Coil's stale bitmap.
 */
@Composable
private fun CustomWallpaper(wallpaperVersion: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(context, wallpaperVersion) { wallpaperFile(context) }
    if (!file.exists()) {
        NeutralGradient(modifier)
        return
    }
    val request = remember(file, wallpaperVersion) {
        val key = "wallpaper-${file.lastModified()}-$wallpaperVersion"
        ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun ImmichBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coordinator = remember(context) {
        (context.applicationContext as PortalApp).photoCoordinator
    }
    val frame by coordinator.currentFrame.collectAsStateWithLifecycle()

    Crossfade(targetState = frame, animationSpec = tween(2000), label = "immich-bg") { currentFrame ->
        if (currentFrame == null) {
            NeutralGradient(modifier)
        } else {
            val request = remember(context, currentFrame.cacheKey) {
                ImageRequest.Builder(context)
                    .data(currentFrame.file)
                    .memoryCacheKey(currentFrame.cacheKey)
                    .diskCacheKey(currentFrame.cacheKey)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}
