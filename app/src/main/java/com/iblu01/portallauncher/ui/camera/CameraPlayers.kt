package com.iblu01.portallauncher.ui.camera

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * HLS playback through ExoPlayer. The url Home Assistant hands out is already signed, so no
 * credential is attached here and none can leak into the player's own logging.
 *
 * The player is created by [DisposableEffect] and released by it: leaving the composition — which
 * is exactly what closing the camera center or scrolling a tile off screen does — stops decoding
 * and tears the connection down, with no separate "please stop" call to forget.
 */
@Composable
internal fun HlsCameraPlayer(
    url: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onAudioTrackDetected: (Boolean) -> Unit = {},
    onError: () -> Unit = {},
    onPlaying: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player, url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) onPlaying()
            }

            override fun onPlayerError(error: PlaybackException) = onError()

            override fun onTracksChanged(tracks: Tracks) {
                // The only honest audio signal: Home Assistant advertises no per-camera audio flag,
                // so the answer is whatever the playlist actually turned out to contain.
                onAudioTrackDetected(
                    tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSupported },
                )
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = true
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            SurfaceView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                player.setVideoSurfaceView(this)
            }
        },
        onRelease = { player.clearVideoSurface() },
    )
}

/**
 * MJPEG playback. The reader runs on the IO dispatcher inside a [LaunchedEffect], so cancelling
 * the composition cancels the coroutine, which closes the response — again, stopping is a
 * consequence of leaving the screen rather than a call someone has to remember.
 *
 * MJPEG has no audio track by construction, so this player exposes no sound control.
 */
@Composable
internal fun MjpegCameraPlayer(
    url: String,
    token: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onError: () -> Unit = {},
    onPlaying: () -> Unit = {},
) {
    var frame by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url, token) {
        frame = null
        val reader = MjpegReader(mjpegClient(), url, token)
        runCatching {
            withContext(Dispatchers.IO) {
                reader.read { bitmap ->
                    // Returning false ends the read and closes the response; checking the job here
                    // is what makes a cancelled composition stop the network immediately.
                    if (!coroutineContext.isActiveSafe()) return@read false
                    frame = bitmap
                    true
                }
            }
        }.onFailure { failure ->
            coroutineContext.ensureActive()   // a cancellation is a requested stop, not an error
            onError()
            return@LaunchedEffect
        }
        // A stream that simply ends without an error is still a stream the user cannot watch.
        coroutineContext.ensureActive()
        onError()
    }

    LaunchedEffect(frame != null) {
        if (frame != null) onPlaying()
    }

    Box(modifier = modifier) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean =
    this[kotlinx.coroutines.Job]?.isActive != false

/**
 * A client of its own: the camera proxy holds each response open for as long as the camera
 * streams, which no ordinary read timeout tolerates. It must also ignore the panel's HTTP proxy,
 * exactly like the Home Assistant socket does.
 */
private var sharedMjpegClient: OkHttpClient? = null

private fun mjpegClient(): OkHttpClient = sharedMjpegClient ?: synchronized(CameraUrls) {
    sharedMjpegClient ?: OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(10, TimeUnit.SECONDS)
        // A live stream never "finishes"; only the connection attempt is allowed to time out.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
        .also { sharedMjpegClient = it }
}
