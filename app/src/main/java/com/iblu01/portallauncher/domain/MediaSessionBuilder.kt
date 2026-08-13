package com.iblu01.portallauncher.domain

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.domain.model.MediaPlayerVolume
import com.iblu01.portallauncher.domain.model.PlayingMedia
import org.json.JSONObject

/**
 * Pure media-session rebuild extracted from the `PillHub` listener
 * (ex-HaStateRepository.kt:361-434). No behavior change: groups media_player entities by
 * title|artist, applies the paused-recency window, and sorts previous-primary first.
 *
 * Stateless & Compose-free → unit-testable and safe to run off the main thread.
 */
object MediaSessionBuilder {
    fun build(
        states: Map<String, HaEntity>,
        haUrl: String,
        previousPrimaryIds: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<PlayingMedia> {
        val mediaEntities = states.values.filter { it.domain == "media_player" }
        val playingEntities = mediaEntities.filter { it.state in setOf("playing", "buffering") }
        val visibleEntities = playingEntities.ifEmpty {
            mediaEntities.filter { entity ->
                if (entity.state != "paused") false
                else {
                    val epoch = runCatching { java.time.Instant.parse(entity.lastChanged).toEpochMilli() }
                        .recoverCatching { java.time.OffsetDateTime.parse(entity.lastChanged).toInstant().toEpochMilli() }
                        .getOrDefault(nowMs)
                    (nowMs - epoch) < 30_000
                }
            }
        }
        val rebuiltSessions = visibleEntities
            .groupBy { entity ->
                val title = entity.attributes.optString("media_title").trim().lowercase()
                val artist = entity.attributes.optString("media_artist").trim().lowercase()
                if (title.isNotBlank()) "$title|$artist"
                else entity.attributes.optString("media_content_id").trim().lowercase().ifBlank { entity.entityId }
            }
            .values
            .map { matchingPlayers ->
                val entity = matchingPlayers.first()
                val volume = (entity.attributes.optDouble("volume_level", 0.0) * 100).toInt()
                val muted = entity.attributes.optBoolean("is_volume_muted", false)
                val cover = entity.attributes.optString("entity_picture").takeIf { it.isNotBlank() }
                val fullCoverUrl = cover?.let { path ->
                    if (path.startsWith("http")) path else haUrl.trimEnd('/') + path
                }
                PlayingMedia(
                    entityId = entity.entityId,
                    title = entity.attributes.optString("media_title").ifBlank { "Média" },
                    artist = entity.attributes.optString("media_artist").ifBlank { entity.name },
                    album = entity.attributes.optString("media_album_name").takeIf { it.isNotBlank() },
                    state = entity.state,
                    coverUrl = fullCoverUrl,
                    source = resolveSource(entity.attributes),
                    volumePercent = volume,
                    isMuted = muted,
                    playerNames = matchingPlayers
                        .map { it.name }
                        .distinct(),
                    players = matchingPlayers.map { player ->
                        MediaPlayerVolume(
                            entityId = player.entityId,
                            name = player.name,
                            volumePercent = (player.attributes.optDouble("volume_level", 0.0) * 100).toInt(),
                            isMuted = player.attributes.optBoolean("is_volume_muted", false),
                        )
                    },
                    groupablePlayers = mediaEntities.filter { candidate ->
                        candidate.attributes.optJSONArray("group_members") != null
                    }.map { candidate ->
                        MediaPlayerVolume(
                            entityId = candidate.entityId,
                            name = candidate.name,
                            volumePercent = (candidate.attributes.optDouble("volume_level", 0.0) * 100).toInt(),
                            isMuted = candidate.attributes.optBoolean("is_volume_muted", false),
                        )
                    },
                    groupMemberIds = entity.attributes.optJSONArray("group_members")?.let { members ->
                        (0 until members.length()).mapNotNull { index -> members.optString(index).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                )
            }
        return rebuiltSessions.sortedWith(
            compareByDescending<PlayingMedia> { session ->
                session.players.any { it.entityId in previousPrimaryIds }
            }.thenBy { session ->
                session.players.mapNotNull { player -> states[player.entityId]?.lastChanged }.minOrNull().orEmpty()
            }.thenBy { it.entityId }
        )
    }

    /**
     * Résout la source du flux multimédia à partir des attributs exposés par HA, dans l'ordre de
     * fiabilité décroissant : `app_name` (Cast), `source` (Sonos, etc.), puis inférence par préfixe
     * de `media_content_id` (spotify:, youtube…). Retourne null quand aucune source n'est décelable.
     */
    private fun resolveSource(attributes: JSONObject): String? {
        val appName = attributes.optString("app_name").trim()
        if (appName.isNotBlank()) return appName

        val source = attributes.optString("source").trim()
        if (source.isNotBlank()) return source

        val contentId = attributes.optString("media_content_id").trim()
        return when {
            contentId.startsWith("spotify:") -> "Spotify"
            contentId.startsWith("https://open.spotify.com") -> "Spotify"
            contentId.startsWith("yt:") -> "YouTube"
            contentId.startsWith("youtube:") -> "YouTube"
            contentId.startsWith("https://www.youtube.com") -> "YouTube"
            else -> null
        }
    }
}
