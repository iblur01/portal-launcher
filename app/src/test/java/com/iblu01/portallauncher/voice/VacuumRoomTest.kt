package com.iblu01.portallauncher.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VacuumRoomTest {

    /** The real map read off the panel's vacuum, kitchen typo included. */
    private val rooms = mapOf(
        "Sdb" to 1, "Couloir" to 2, "Cusine" to 3, "Entrée" to 4, "Chambre" to 6, "Salon" to 8,
    )

    @Test
    fun `rooms are flattened across the vacuum's saved maps`() {
        val attributes = JSONObject(
            """
            {"rooms":{"Le QG":[{"id":1,"name":"Sdb"},{"id":3,"name":"Cusine"}],
                      "Étage":[{"id":9,"name":"Bureau"}]}}
            """.trimIndent(),
        )
        assertEquals(mapOf("Sdb" to 1, "Cusine" to 3, "Bureau" to 9), vacuumRooms(attributes))
        assertEquals(emptyMap<String, Int>(), vacuumRooms(JSONObject()))
        assertEquals(emptyMap<String, Int>(), vacuumRooms(null))
    }

    @Test
    fun `an exact room matches whatever its accents and case`() {
        assertEquals("Salon" to 8, matchRoom(rooms, "salon"))
        assertEquals("Entrée" to 4, matchRoom(rooms, "entree"))
        assertEquals("Entrée" to 4, matchRoom(rooms, "ENTRÉE"))
    }

    /**
     * The case that made "vacuum the kitchen" impossible: the segment is named "Cusine" in the
     * vacuum's app, so an exact match never found the room the user was asking for.
     */
    @Test
    fun `a room whose stored name is misspelled still matches`() {
        assertEquals("Cusine" to 3, matchRoom(rooms, "cuisine"))
        assertEquals("Cusine" to 3, matchRoom(rooms, "la cuisine"))
    }

    @Test
    fun `a room nobody has is refused rather than guessed`() {
        assertNull(matchRoom(rooms, "garage"))
        assertNull(matchRoom(rooms, ""))
        assertNull(matchRoom(emptyMap(), "cuisine"))
    }

    /** Short names must not collapse into each other on edit distance alone. */
    @Test
    fun `short room names stay distinct`() {
        assertEquals("Sdb" to 1, matchRoom(rooms, "sdb"))
        assertEquals("Salon" to 8, matchRoom(rooms, "salon"))
        assertNull(matchRoom(mapOf("Sdb" to 1), "salon"))
    }
}
