package com.iblu01.portallauncher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAllowlistTest {

    @Test fun `EMPTY defaults fail closed`() {
        assertNull(SessionAllowlist.EMPTY.classificationFor("io.homeassistant.companion.android"))
        assertNull(SessionAllowlist.EMPTY.classificationFor("com.google.android.youtube"))
    }

    @Test fun `custom entries override default behavior`() {
        val custom = SessionAllowlist(
            mapOf(
                "com.local.app" to AppClassification.HOME,
                "com.local.video" to AppClassification.MEDIA,
            )
        )
        assertEquals(AppClassification.HOME, custom.classificationFor("com.local.app"))
        assertEquals(AppClassification.MEDIA, custom.classificationFor("com.local.video"))
        assertNull(custom.classificationFor("com.google.android.youtube"))
    }

    @Test fun `classification exposes default and max durations`() {
        assertEquals(60, AppClassification.HOME.defaultDurationSeconds)
        assertEquals(300, AppClassification.HOME.maxDurationSeconds)
        assertEquals(30, AppClassification.MEDIA.defaultDurationSeconds)
        assertEquals(120, AppClassification.MEDIA.maxDurationSeconds)
        assertEquals(30, AppClassification.UTILITY.defaultDurationSeconds)
        assertEquals(60, AppClassification.UTILITY.maxDurationSeconds)
        assertEquals(30, AppClassification.COMMUNICATION.defaultDurationSeconds)
        assertEquals(60, AppClassification.COMMUNICATION.maxDurationSeconds)
    }

    @Test fun `unknown package returns null`() {
        assertNull(SessionAllowlist.EMPTY.classificationFor("com.not.in.list"))
    }

    @Test fun `empty allowlist rejects everything`() {
        val empty = SessionAllowlist(emptyMap())
        assertNull(empty.classificationFor("io.homeassistant.companion.android"))
    }
}
