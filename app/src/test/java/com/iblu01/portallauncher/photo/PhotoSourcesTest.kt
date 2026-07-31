package com.iblu01.portallauncher.photo

import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoSourcesTest {
    @Test fun `invalid provider configuration is bounded instead of thrown`() = runBlocking {
        val proxy = ProxyPhotoSource(object : PhotoSourceProvider {
            override fun current(): PhotoSource? = throw IllegalArgumentException("private-url")
        })

        assertEquals("none", proxy.provider)
        assertFalse(proxy.health().ok)
        assertEquals(PhotoErrorCategories.CONFIG, proxy.health().errorCategory)
        val failure = runCatching { proxy.listAlbums() }.exceptionOrNull()
        assertTrue(failure is PhotoSourceException)
        assertEquals(PhotoErrorCategories.CONFIG, (failure as PhotoSourceException).category)
    }

    @Test fun `configured source is reused and explicit clear drops cached instance`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.backgroundMode = "immich"
        prefs.immichUrl = "https://photos.example.com"
        prefs.immichApiKey = "private-key"
        val provider = DefaultPhotoSourceProvider(prefs, FailingTransport())

        val first = provider.current()
        assertSame(first, provider.current())

        provider.clearCachedSource()
        val second = provider.current()
        assertNotSame(first, second)

        prefs.clearImmichConfiguration()
        assertNull(provider.current())
        prefs.backgroundMode = "neutral"
    }
}
