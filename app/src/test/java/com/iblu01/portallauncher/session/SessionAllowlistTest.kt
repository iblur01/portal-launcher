package com.iblu01.portallauncher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAllowlistTest {

    @Test fun `DEFAULT seed contains known packages`() {
        assertEquals(AppClassification.HOME, SessionAllowlist.DEFAULT.classificationFor("io.homeassistant.companion.android"))
        assertEquals(AppClassification.MEDIA, SessionAllowlist.DEFAULT.classificationFor("com.google.android.youtube"))
        assertEquals(AppClassification.UTILITY, SessionAllowlist.DEFAULT.classificationFor("com.android.chrome"))
        assertEquals(AppClassification.COMMUNICATION, SessionAllowlist.DEFAULT.classificationFor("com.whatsapp"))
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
        assertNull(SessionAllowlist.DEFAULT.classificationFor("com.not.in.list"))
    }

    @Test fun `empty allowlist rejects everything`() {
        val empty = SessionAllowlist(emptyMap())
        assertNull(empty.classificationFor("io.homeassistant.companion.android"))
    }
}
