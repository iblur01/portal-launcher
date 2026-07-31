package com.iblu01.portallauncher.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichAlbumIdValidationTest {
    private val albumId = "123e4567-e89b-42d3-a456-426614174000"

    @Test fun `accepts Immich v4 UUID album IDs`() {
        assertTrue(isValidImmichAlbumId(albumId))
    }

    @Test fun `rejects free text and non-v4 UUIDs`() {
        assertFalse(isValidImmichAlbumId("album-1"))
        assertFalse(isValidImmichAlbumId("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(isValidImmichAlbumId(""))
    }

    @Test fun `apply requires URL key and at least one valid album`() {
        assertTrue(canApplyImmichConfig("https://photos.example.com", true, listOf(albumId)))
        assertFalse(canApplyImmichConfig("", true, listOf(albumId)))
        assertFalse(canApplyImmichConfig("https://photos.example.com", false, listOf(albumId)))
        assertFalse(canApplyImmichConfig("https://photos.example.com", true, emptyList()))
        assertFalse(canApplyImmichConfig("https://photos.example.com", true, listOf("not-a-uuid")))
    }
}
