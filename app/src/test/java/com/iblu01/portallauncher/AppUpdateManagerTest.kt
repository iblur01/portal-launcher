package com.iblu01.portallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test fun `semantic versions are compared numerically`() {
        assertTrue(AppUpdateManager.isNewer("1.0.1", "0.5"))
        assertTrue(AppUpdateManager.isNewer("v1.10", "1.9"))
        assertFalse(AppUpdateManager.isNewer("1.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewer("0.9.9", "1.0"))
    }

    @Test fun `GitHub release response selects the APK asset`() {
        val release = AppUpdateManager.parseLatestRelease(
            """{
              "tag_name":"v1.0.1",
              "name":"Portal 1.0.1",
              "body":"Fix translations",
              "assets":[
                {"name":"checksums.txt","browser_download_url":"https://example/checksums"},
                {"name":"portal.apk","browser_download_url":"https://example/portal.apk"}
              ]
            }""",
        )
        assertEquals("1.0.1", release.version)
        assertEquals("Fix translations", release.notes)
        assertEquals("https://example/portal.apk", release.apkUrl)
    }
}
