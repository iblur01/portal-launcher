package com.iblu01.portallauncher.ui.camera

import com.iblu01.portallauncher.domain.home.CameraStreamFormat

/**
 * A resolved, ready-to-play camera source.
 *
 * The Home Assistant token never appears here as text: an [Hls] url is signed by Home Assistant
 * itself and carries no credential, and [Mjpeg] is fetched with an `Authorization` header set by
 * the client. Neither is ever handed to another application.
 */
sealed interface CameraStreamSource {
    /**
     * HLS playlist, from the `stream` integration. The url is relative to the Home Assistant base
     * and already signed, so it needs no header — and it is the only format that can carry audio.
     */
    data class Hls(val url: String) : CameraStreamSource

    /** Multipart MJPEG from the camera proxy. Universal, never has audio. */
    data class Mjpeg(val url: String) : CameraStreamSource

    val format: CameraStreamFormat
        get() = when (this) {
            is Hls -> CameraStreamFormat.HLS
            is Mjpeg -> CameraStreamFormat.MJPEG
        }
}

/** Why a camera cannot be shown. Each maps to a distinct, actionable message. */
enum class CameraStreamError {
    /** Home Assistant reports the camera itself as unavailable. */
    UNAVAILABLE,

    /** Home Assistant is unreachable, or refused to resolve a stream for this camera. */
    UNREACHABLE,

    /** A source was resolved but the player could not read it. */
    PLAYBACK,
}

/** The camera tile's lifecycle. Loading, playing, unavailable and error are all distinct. */
sealed interface CameraStreamState {
    data object Loading : CameraStreamState
    data class Playing(val source: CameraStreamSource, val hasAudio: Boolean) : CameraStreamState
    data class Failed(val error: CameraStreamError) : CameraStreamState
}

/** Builds the Home Assistant urls the camera center fetches. Pure, so the shapes stay testable. */
object CameraUrls {
    /**
     * Multipart MJPEG proxy. Deliberately built **without** the `token=` query parameter Home
     * Assistant's own frontend uses: the request carries an `Authorization` header instead, so no
     * credential is ever written into a url, a log line, or anything an external app could receive.
     */
    fun mjpeg(baseUrl: String, entityId: String): String =
        "${baseUrl.trimEnd('/')}/api/camera_proxy_stream/$entityId"

    /** Resolves the relative, already-signed url Home Assistant returns for an HLS stream. */
    fun absolute(baseUrl: String, url: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) url
        else "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
}
