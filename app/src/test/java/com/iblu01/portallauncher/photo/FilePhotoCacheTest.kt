package com.iblu01.portallauncher.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilePhotoCacheTest {
    private lateinit var context: Context

    @Before fun clean() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "photos").deleteRecursively()
    }

    @Test fun `entries survive cache recreation for offline restart`() = runBlocking {
        val first = FilePhotoCache(context)
        val bytes = byteArrayOf(1, 2, 3)
        first.put(meta("immich:a", "a", 10L, bytes.size), bytes)

        val recreated = FilePhotoCache(context)
        assertArrayEquals(bytes, recreated.get("immich:a")?.first!!)
        assertEquals(listOf("immich:a"), recreated.entries().map { it.cacheKey })
    }

    @Test fun `trim evicts oldest entries and respects byte bound`() = runBlocking {
        val cache = FilePhotoCache(context)
        cache.put(meta("immich:a", "a", 10L, 4), ByteArray(4) { 1 })
        cache.put(meta("immich:b", "b", 20L, 4), ByteArray(4) { 2 })
        cache.put(meta("immich:c", "c", 30L, 4), ByteArray(4) { 3 })

        cache.trimTo(maxEntries = 2, maxBytes = 8)

        assertFalse("immich:a" in cache.keys())
        assertEquals(setOf("immich:b", "immich:c"), cache.keys())
        assertTrue(cache.totalBytes() <= 8)
    }

    @Test fun `filesystem names are opaque and collision resistant`() = runBlocking {
        val one = FilePhotoCache.cacheKeyToFileName("immich:a/b")
        val two = FilePhotoCache.cacheKeyToFileName("immich:a?b")
        assertNotEquals(one, two)
        assertFalse(one.contains("immich"))

        val cache = FilePhotoCache(context)
        cache.put(meta("immich:a/b", "a", 1L, 1), byteArrayOf(1))
        cache.put(meta("immich:a?b", "b", 2L, 1), byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1), cache.get("immich:a/b")?.first!!)
        assertArrayEquals(byteArrayOf(2), cache.get("immich:a?b")?.first!!)
    }

    @Test fun `malformed manifest fails closed without returning orphan bytes`() = runBlocking {
        val cache = FilePhotoCache(context)
        cache.put(meta("immich:a", "a", 1L, 1), byteArrayOf(7))
        File(context.filesDir, "photos/manifest.json").writeText("not-json")

        val recreated = FilePhotoCache(context)
        assertNull(recreated.get("immich:a"))
        assertTrue(recreated.entries().isEmpty())
    }

    private fun meta(key: String, asset: String, created: Long, size: Int) = CacheMeta(
        cacheKey = key,
        assetId = asset,
        provider = "immich",
        createdAt = created,
        size = size.toLong(),
    )
}
