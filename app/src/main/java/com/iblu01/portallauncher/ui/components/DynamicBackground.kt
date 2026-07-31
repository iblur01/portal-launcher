package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iblu01.portallauncher.PortalApp
import com.iblu01.portallauncher.R

private val unsplashUrls = listOf(
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200&q=80" to "Mer",
    "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=1200&q=80" to "Montagne",
    "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200&q=80" to "Forêt",
    "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1200&q=80" to "Brume",
    "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1200&q=80" to "Nature",
)

val backgroundModes = listOf(
    "neutral" to R.string.bg_mode_neutral,
    "nature" to R.string.bg_mode_nature,
    "custom" to R.string.bg_mode_custom,
    "immich" to R.string.bg_mode_immich,
)

@Composable
fun AmbientBackground(mode: String, wallpaperVersion: Int = 0, modifier: Modifier = Modifier) {
    when (mode) {
        "nature" -> UnsplashCycling(modifier)
        "custom" -> CustomWallpaper(modifier, wallpaperVersion)
        "immich" -> ImmichBackground(modifier)
        else -> NeutralGradient(modifier)
    }
}

@Composable
private fun NeutralGradient(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF1B2026), Color(0xFF05070A)),
                radius = 1400f
            )
        )
    )
}

@Composable
private fun CustomWallpaper(modifier: Modifier = Modifier, wallpaperVersion: Int = 0) {
    val context = LocalContext.current
    // Re-created (not `remember { }`-cached) so a bump of wallpaperVersion forces a fresh
    // file.exists()/lastModified() read, picking up a just-replaced photo.
    val file = remember(wallpaperVersion) { java.io.File(context.filesDir, "wallpaper.jpg") }
    if (file.exists()) {
        val cacheKey = remember(wallpaperVersion, file.lastModified()) {
            "wallpaper-${file.lastModified()}"
        }
        val request = remember(cacheKey) {
            ImageRequest.Builder(context)
                .data(file)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        NeutralGradient(modifier)
    }
}

@Composable
private fun UnsplashCycling(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .proxy(Proxy.NO_PROXY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(1_200)
            .respectCacheHeaders(false)
            .build()
    }
    val index by produceState(0) {
        while (true) {
            delay(30_000L)
            value = (value + 1) % unsplashUrls.size
        }
    }
    Crossfade(targetState = index, animationSpec = tween(2000), label = "bg") { i ->
        val (url, _) = unsplashUrls[i]
        AsyncImage(
            model = url,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { Log.w("PortalUnsplash", "Unable to load ${url.substringBefore('?')}: ${it.result.throwable.message}") },
            modifier = modifier.fillMaxSize()
        )
    }
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
