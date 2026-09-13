package com.iblu01.portallauncher.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CameraUrlsTest {
    @Test fun `the MJPEG url carries no credential of any kind`() {
        val url = CameraUrls.mjpeg("http://homeassistant.local:8123/", "camera.hall")

        assertEquals("http://homeassistant.local:8123/api/camera_proxy_stream/camera.hall", url)
        assertFalse(url.contains("token"))
        assertFalse(url.contains("?"))
    }

    @Test fun `a relative signed stream url is resolved against the configured base`() {
        assertEquals(
            "http://ha.local:8123/api/hls/abc/master.m3u8",
            CameraUrls.absolute("http://ha.local:8123", "/api/hls/abc/master.m3u8"),
        )
    }

    @Test fun `an absolute stream url is left untouched`() {
        assertEquals(
            "https://cdn.example/live.m3u8",
            CameraUrls.absolute("http://ha.local:8123", "https://cdn.example/live.m3u8"),
        )
    }

    @Test fun `a trailing slash on the base never doubles up`() {
        assertEquals(
            "http://ha.local:8123/api/hls/a.m3u8",
            CameraUrls.absolute("http://ha.local:8123/", "api/hls/a.m3u8"),
        )
    }
}
