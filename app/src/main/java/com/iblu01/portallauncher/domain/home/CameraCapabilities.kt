package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillSupport

/**
 * How a camera's live picture can actually be fetched from Home Assistant.
 *
 * `HLS` is the only format that carries audio, and it exists only when the `stream` integration
 * is set up and the camera advertises [CameraSupport.FEATURE_STREAM]. `MJPEG` is the universal
 * fallback served by the camera proxy: every camera that can produce a picture has it, and it
 * never has sound.
 */
enum class CameraStreamFormat { HLS, MJPEG }

/** A PTZ movement, named after the ONVIF vocabulary Home Assistant integrations reuse. */
enum class PtzAction { PAN_LEFT, PAN_RIGHT, TILT_UP, TILT_DOWN, ZOOM_IN, ZOOM_OUT }

/**
 * What a given camera can actually do — never inferred from its name.
 *
 * @param ptz the movements that are genuinely reachable, empty for a fixed camera.
 * @param ptzEntityIds companion entities implementing each movement, when the integration exposes
 *   them that way (Reolink, Tapo…). Empty when [ptz] comes from the ONVIF service instead.
 */
data class CameraCapabilities(
    val formats: List<CameraStreamFormat>,
    val ptz: Set<PtzAction> = emptySet(),
    val ptzEntityIds: Map<PtzAction, String> = emptyMap(),
) {
    val supportsPtz: Boolean get() = ptz.isNotEmpty()
}

/**
 * Derives [CameraCapabilities] from a Home Assistant snapshot. Pure, so every rule here is
 * testable without a server.
 *
 * ## On PTZ detection
 *
 * The `camera` domain of Home Assistant core defines **no** PTZ service, and no camera entity
 * advertises PTZ through `supported_features` — the only PTZ bits are `ON_OFF` (1) and
 * `STREAM` (2). PTZ is therefore integration-specific, and there are exactly two honest signals:
 *
 * 1. **Companion entities.** Integrations such as Reolink or Tapo create one `button.*`/`select.*`
 *    entity per movement on the camera's own device. That is per-camera and per-direction truth:
 *    a fixed camera simply has none, so it gets no controls.
 * 2. **The ONVIF service.** `onvif.ptz` exists as soon as any ONVIF camera is configured, and it
 *    is offered only to entities the entity registry attributes to the `onvif` platform.
 *
 * Known limitation of the second signal: Home Assistant does not expose per-camera ONVIF PTZ
 * capability to clients (the ONVIF integration checks it internally and logs a warning on an
 * unsupported device). A fixed ONVIF camera can therefore still be offered controls; the action
 * is refused server-side rather than doing something wrong. Signal 1 always wins when present.
 */
object CameraSupport {
    const val FEATURE_STREAM = 2

    const val ONVIF_PLATFORM = "onvif"
    const val ONVIF_PTZ_SERVICE = "ptz"

    /** Entity-id fragments each integration uses for its per-direction PTZ helper entities. */
    private val ptzTokens: Map<PtzAction, List<String>> = mapOf(
        PtzAction.PAN_LEFT to listOf("ptz_left", "pan_left", "ptz_gauche"),
        PtzAction.PAN_RIGHT to listOf("ptz_right", "pan_right", "ptz_droite"),
        PtzAction.TILT_UP to listOf("ptz_up", "tilt_up", "ptz_haut"),
        PtzAction.TILT_DOWN to listOf("ptz_down", "tilt_down", "ptz_bas"),
        PtzAction.ZOOM_IN to listOf("ptz_zoom_in", "zoom_in"),
        PtzAction.ZOOM_OUT to listOf("ptz_zoom_out", "zoom_out"),
    )

    /** The domains a per-direction PTZ helper can live in. All are one-shot actions. */
    private val ptzDomains = setOf("button")

    fun isAvailable(entity: HaEntity?): Boolean =
        entity != null && PillSupport.isIndividuallyAvailable(entity)

    /**
     * Formats to try, best first. HLS is only listed when the camera advertises the STREAM
     * feature: asking for a stream url without it makes Home Assistant answer an error, and a
     * failed round-trip per open is a worse default than starting on the format that always works.
     */
    fun formatsFor(entity: HaEntity): List<CameraStreamFormat> {
        val features = entity.attributes.optInt("supported_features", 0)
        return if (features and FEATURE_STREAM != 0) {
            listOf(CameraStreamFormat.HLS, CameraStreamFormat.MJPEG)
        } else {
            listOf(CameraStreamFormat.MJPEG)
        }
    }

    /**
     * @param services the Home Assistant service catalogue, as `domain -> service names`.
     */
    fun capabilitiesOf(
        entity: HaEntity,
        states: Map<String, HaEntity>,
        deviceIdByEntity: Map<String, String>,
        entityPlatformByEntity: Map<String, String>,
        services: Map<String, Set<String>>,
    ): CameraCapabilities {
        val companions = ptzCompanions(entity, states, deviceIdByEntity)
        if (companions.isNotEmpty()) {
            return CameraCapabilities(formatsFor(entity), companions.keys, companions)
        }
        val onvif = entityPlatformByEntity[entity.entityId] == ONVIF_PLATFORM &&
            ONVIF_PTZ_SERVICE in services[ONVIF_PLATFORM].orEmpty()
        return CameraCapabilities(
            formats = formatsFor(entity),
            ptz = if (onvif) PtzAction.values().toSet() else emptySet(),
        )
    }

    /** Per-direction helper entities sitting on the camera's own device. */
    private fun ptzCompanions(
        entity: HaEntity,
        states: Map<String, HaEntity>,
        deviceIdByEntity: Map<String, String>,
    ): Map<PtzAction, String> {
        val deviceId = deviceIdByEntity[entity.entityId] ?: return emptyMap()
        val siblings = states.values.filter {
            it.domain in ptzDomains && deviceIdByEntity[it.entityId] == deviceId
        }
        if (siblings.isEmpty()) return emptyMap()
        val found = linkedMapOf<PtzAction, String>()
        ptzTokens.forEach { (action, tokens) ->
            val match = siblings.firstOrNull { sibling ->
                val slug = sibling.entityId.substringAfter('.').lowercase()
                tokens.any(slug::contains)
            }
            if (match != null) found[action] = match.entityId
        }
        return found
    }
}
