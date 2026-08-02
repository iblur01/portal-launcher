package com.iblu01.portallauncher.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAllowlistCodecTest {
    @Test fun `round trip preserves classified entries`() {
        val original = SessionAllowlist(
            mapOf(
                "com.example.home" to AppClassification.HOME,
                "com.example.media" to AppClassification.MEDIA,
            )
        )
        assertEquals(original, SessionAllowlistCodec.decode(SessionAllowlistCodec.encode(original)))
    }

    @Test fun `empty storage is fail closed`() {
        val decoded = SessionAllowlistCodec.decode(null)
        assertNull(decoded.classificationFor("com.google.android.youtube"))
    }

    @Test fun `malformed entries are ignored`() {
        val decoded = SessionAllowlistCodec.decode(
            setOf(
                "bad package|HOME",
                "com.example.app|NOT_A_CLASS",
                "com.example.valid|UTILITY",
                "missing-separator",
            )
        )
        assertEquals(AppClassification.UTILITY, decoded.classificationFor("com.example.valid"))
        assertNull(decoded.classificationFor("bad package"))
        assertNull(decoded.classificationFor("com.example.app"))
    }

    @Test fun `storage is capped at thirty two entries`() {
        val stored = (1..40).map { "com.example.app$it|UTILITY" }.toSet()
        assertEquals(32, SessionAllowlistCodec.decode(stored).toMap().size)
    }

    @Test fun `encoding is capped at thirty two entries`() {
        val entries = (1..40).associate { "com.example.app$it" to AppClassification.UTILITY }
        assertEquals(32, SessionAllowlistCodec.encode(SessionAllowlist(entries)).size)
    }
}
