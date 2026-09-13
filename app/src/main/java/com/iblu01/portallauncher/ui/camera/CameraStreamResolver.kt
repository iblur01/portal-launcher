package com.iblu01.portallauncher.ui.camera

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.domain.home.CameraStreamFormat
import com.iblu01.portallauncher.domain.home.CameraSupport
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Turns a camera entity into something playable, one format at a time.
 *
 * Resolution is not a guess: HLS is only attempted when the camera advertises the STREAM feature,
 * and it is only used if Home Assistant actually answers with a url. Anything else falls back to
 * the camera proxy, which every camera able to produce a picture supports.
 */
class CameraStreamResolver(
    private val baseUrl: String,
    /** Sends a websocket request; the callback receives null on any failure. */
    private val request: (JSONObject, (JSONObject?) -> Unit) -> Unit,
) {
    /**
     * Resolves the best source for [entity], or null when the camera is unavailable.
     * [skipFormats] lets a caller retry past a format that already failed to play.
     */
    suspend fun resolve(
        entity: HaEntity,
        skipFormats: Set<CameraStreamFormat> = emptySet(),
    ): CameraStreamSource? {
        if (!CameraSupport.isAvailable(entity)) return null
        for (format in CameraSupport.formatsFor(entity)) {
            if (format in skipFormats) continue
            val source = when (format) {
                CameraStreamFormat.HLS -> hlsSource(entity.entityId)
                CameraStreamFormat.MJPEG ->
                    CameraStreamSource.Mjpeg(CameraUrls.mjpeg(baseUrl, entity.entityId))
            }
            if (source != null) return source
        }
        return null
    }

    /**
     * `camera/stream` answers `{"url": "/api/hls/<signed>/master.m3u8"}`. The url is signed by
     * Home Assistant, so it is played as-is with no credential of ours attached to it.
     */
    private suspend fun hlsSource(entityId: String): CameraStreamSource.Hls? {
        val result = suspendCoroutine<JSONObject?> { continuation ->
            request(
                JSONObject()
                    .put("type", "camera/stream")
                    .put("entity_id", entityId)
                    .put("format", "hls"),
            ) { continuation.resume(it) }
        } ?: return null
        val url = result.optString("url").takeIf(String::isNotBlank) ?: return null
        return CameraStreamSource.Hls(CameraUrls.absolute(baseUrl, url))
    }

    /**
     * The Home Assistant service catalogue as `domain -> service names`, used to tell whether
     * `onvif.ptz` exists at all. An unreachable or refused catalogue yields an empty map, which
     * means "no PTZ" — never a control the camera cannot honour.
     */
    suspend fun services(): Map<String, Set<String>> {
        val result = suspendCoroutine<JSONObject?> { continuation ->
            request(JSONObject().put("type", "get_services")) { continuation.resume(it) }
        } ?: return emptyMap()
        return result.keys().asSequence().mapNotNull { domain ->
            val services = result.optJSONObject(domain) ?: return@mapNotNull null
            domain to services.keys().asSequence().toSet()
        }.toMap()
    }
}
