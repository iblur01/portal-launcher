package com.iblu01.portallauncher

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsImmichTest {
    @Test fun `provider removal scrubs credential and configuration`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.immichUrl = "https://photos.example.com/"
        prefs.immichApiKey = "private-key"
        prefs.immichAlbumIds = listOf("one", "two")
        prefs.immichAllowInsecure = true

        assertTrue(prefs.hasImmichApiKey)
        prefs.clearImmichConfiguration()

        assertFalse(prefs.hasImmichApiKey)
        assertEquals("", prefs.immichApiKey)
        assertEquals("", prefs.immichUrl)
        assertTrue(prefs.immichAlbumIds.isEmpty())
        assertFalse(prefs.immichAllowInsecure)
    }

    @Test fun `album selection is normalized deduplicated and capped`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.immichAlbumIds = (1..25).map { " album-$it " } + listOf("album-1", "")

        assertEquals(20, prefs.immichAlbumIds.size)
        assertEquals("album-1", prefs.immichAlbumIds.first())
        assertEquals(20, prefs.immichAlbumIds.distinct().size)
    }
}
