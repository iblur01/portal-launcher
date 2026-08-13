package com.iblu01.portallauncher.domain

import com.iblu01.portallauncher.HaEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Behaviour of the pure media rebuild (design §Tests): title|artist grouping, the paused-recency
 * window, previous-primary sort, and cover-URL resolution. Previously 0 coverage over ~80 lines.
 */
class MediaSessionBuilderTest {

    private val base = Instant.parse("2026-07-23T10:00:00Z")
    private val nowMs = base.toEpochMilli()
    private val haUrl = "http://ha.local:8123"

    private fun player(
        id: String,
        state: String = "playing",
        title: String = "",
        artist: String = "",
        picture: String = "",
        ageMs: Long = 0L,
        groupMembers: List<String>? = null,
        appName: String = "",
        source: String = "",
        contentId: String = "",
    ): HaEntity {
        val attrs = JSONObject()
        if (title.isNotBlank()) attrs.put("media_title", title)
        if (artist.isNotBlank()) attrs.put("media_artist", artist)
        if (picture.isNotBlank()) attrs.put("entity_picture", picture)
        if (groupMembers != null) attrs.put("group_members", JSONArray(groupMembers))
        if (appName.isNotBlank()) attrs.put("app_name", appName)
        if (source.isNotBlank()) attrs.put("source", source)
        if (contentId.isNotBlank()) attrs.put("media_content_id", contentId)
        val lastChanged = base.minusMillis(ageMs).toString()
        return HaEntity(entityId = id, state = state, attributes = attrs, lastChanged = lastChanged)
    }

    private fun build(vararg entities: HaEntity, prev: Set<String> = emptySet()) =
        MediaSessionBuilder.build(entities.associateBy { it.entityId }, haUrl, prev, nowMs)

    @Test fun `two players with same title and artist group into one session`() {
        val sessions = build(
            player("media_player.kitchen", title = "Song", artist = "Band"),
            player("media_player.living", title = "Song", artist = "Band"),
        )
        assertEquals(1, sessions.size)
        assertEquals(2, sessions.first().players.size)
    }

    @Test fun `different titles stay separate sessions`() {
        val sessions = build(
            player("media_player.a", title = "One", artist = "X"),
            player("media_player.b", title = "Two", artist = "X"),
        )
        assertEquals(2, sessions.size)
    }

    @Test fun `only playing sessions show when something is playing`() {
        val sessions = build(
            player("media_player.a", state = "playing", title = "One"),
            player("media_player.b", state = "paused", title = "Two", ageMs = 1_000),
        )
        assertEquals(1, sessions.size)
        assertEquals("media_player.a", sessions.first().entityId)
    }

    @Test fun `recently-paused session shows when nothing is playing`() {
        val sessions = build(
            player("media_player.a", state = "paused", title = "One", ageMs = 10_000),
        )
        assertEquals(1, sessions.size)
    }

    @Test fun `stale-paused session is hidden past the 30s window`() {
        val sessions = build(
            player("media_player.a", state = "paused", title = "One", ageMs = 60_000),
        )
        assertTrue(sessions.isEmpty())
    }

    @Test fun `previous-primary session sorts first`() {
        val sessions = build(
            player("media_player.a", title = "One", artist = "X"),
            player("media_player.b", title = "Two", artist = "X"),
            prev = setOf("media_player.b"),
        )
        assertEquals("media_player.b", sessions.first().entityId)
    }

    @Test fun `relative cover path is prefixed with the HA url, absolute is kept`() {
        val rel = build(player("media_player.a", title = "One", picture = "/api/cover.png"))
        assertEquals("$haUrl/api/cover.png", rel.first().coverUrl)

        val abs = build(player("media_player.b", title = "Two", picture = "https://cdn/x.png"))
        assertEquals("https://cdn/x.png", abs.first().coverUrl)
    }

    @Test fun `group_members are exposed on the session`() {
        val sessions = build(
            player("media_player.a", title = "One", groupMembers = listOf("media_player.a", "media_player.c")),
        )
        assertEquals(listOf("media_player.a", "media_player.c"), sessions.first().groupMemberIds)
    }

    @Test fun `source resolves from app_name first`() {
        val sessions = build(
            player("media_player.a", title = "One", appName = "Spotify", source = "Sonos"),
        )
        assertEquals("Spotify", sessions.first().source)
    }

    @Test fun `source falls back to source attribute when app_name is blank`() {
        val sessions = build(
            player("media_player.a", title = "One", source = "Line-in"),
        )
        assertEquals("Line-in", sessions.first().source)
    }

    @Test fun `source infers Spotify from media_content_id prefix`() {
        val sessions = build(
            player("media_player.a", title = "One", contentId = "spotify:track:123"),
        )
        assertEquals("Spotify", sessions.first().source)
    }

    @Test fun `source infers YouTube from media_content_id prefix`() {
        val sessions = build(
            player("media_player.a", title = "One", contentId = "https://www.youtube.com/watch?v=x"),
        )
        assertEquals("YouTube", sessions.first().source)
    }

    @Test fun `source is null when no hint is present`() {
        val sessions = build(
            player("media_player.a", title = "One"),
        )
        assertEquals(null, sessions.first().source)
    }
}
