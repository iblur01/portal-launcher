package com.iblu01.portallauncher.photo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoCoordinatorTest {
    @Test fun `refresh pages deduplicates orders prefetches and rotates`() = runTest {
        val source = FakePhotoSource(
            pages = mapOf(
                0 to PhotoAssetPage(listOf(asset("b", 20), asset("a", 10)), hasMore = true),
                1 to PhotoAssetPage(listOf(asset("b", 20), asset("c", 30)), hasMore = false),
            ),
        )
        val cache = MemoryPhotoCache()
        val coordinator = PhotoCoordinator(
            source = source,
            cache = cache,
            config = PhotoCoordinatorConfig(
                refreshIntervalMinutes = 60,
                cadenceSeconds = 5,
                shuffle = false,
                prefetchCount = 2,
            ),
            scope = this,
            selection = { PhotoAlbumSelection("immich", listOf("album-1")) },
        )

        coordinator.start()
        runCurrent()

        assertEquals(listOf(0, 1), source.requestedPages)
        assertEquals(setOf("immich:a", "immich:b", "immich:c"), cache.keys())
        assertEquals("a", coordinator.currentFrame.value?.assetId)
        assertEquals("Family", coordinator.status.value.selectedAlbumLabel)
        assertTrue(coordinator.status.value.healthy)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("b", coordinator.currentFrame.value?.assetId)
        coordinator.stop()
    }

    @Test fun `offline startup restores and displays cached frame without provider`() = runTest {
        val cache = MemoryPhotoCache()
        cache.put(
            CacheMeta("immich:cached", "cached", "immich", createdAt = 1, size = 3),
            byteArrayOf(1, 2, 3),
        )
        val source = FakePhotoSource(healthy = false)
        val coordinator = PhotoCoordinator(
            source = source,
            cache = cache,
            config = PhotoCoordinatorConfig(cadenceSeconds = 5, retryBaseSeconds = 2),
            scope = this,
            selection = { PhotoAlbumSelection("immich", listOf("album-1")) },
        )

        coordinator.start()
        runCurrent()

        assertEquals("cached", coordinator.currentFrame.value?.assetId)
        assertEquals("offline_cached", PhotoStatusSerializer.state(coordinator.status.value))
        assertFalse(coordinator.status.value.healthy)
        assertEquals(PhotoErrorCategories.NETWORK, coordinator.status.value.errorCategory)
        coordinator.stop()
    }

    @Test fun `empty provider payload becomes bounded server error`() = runTest {
        val source = FakePhotoSource(imageBytes = byteArrayOf())
        val coordinator = PhotoCoordinator(
            source = source,
            cache = MemoryPhotoCache(),
            config = PhotoCoordinatorConfig(cadenceSeconds = 5, retryBaseSeconds = 2),
            scope = this,
            selection = { PhotoAlbumSelection("immich", listOf("album-1")) },
        )

        coordinator.start()
        runCurrent()

        assertEquals(PhotoErrorCategories.SERVER, coordinator.status.value.errorCategory)
        assertNotNull(coordinator.status.value)
        coordinator.stop()
    }

    @Test fun `empty album selection reconciles provider cache`() = runTest {
        val cache = MemoryPhotoCache()
        cache.put(CacheMeta("immich:old", "old", "immich", 1, 1), byteArrayOf(1))
        val coordinator = PhotoCoordinator(
            source = FakePhotoSource(),
            cache = cache,
            config = PhotoCoordinatorConfig(cadenceSeconds = 5, retryBaseSeconds = 2),
            scope = this,
            selection = { PhotoAlbumSelection("immich", emptyList()) },
        )

        coordinator.start()
        runCurrent()

        assertTrue(cache.keys().isEmpty())
        assertEquals(null, coordinator.currentFrame.value)
        assertEquals(PhotoErrorCategories.CONFIG, coordinator.status.value.errorCategory)
        coordinator.stop()
    }

    private fun asset(id: String, captured: Long) = PhotoAsset(
        id = id,
        cacheKey = "immich:$id",
        width = 100,
        height = 100,
        capturedAt = captured,
    )
}

private class FakePhotoSource(
    private val healthy: Boolean = true,
    private val pages: Map<Int, PhotoAssetPage> = mapOf(
        0 to PhotoAssetPage(listOf(PhotoAsset("a", "immich:a", 100, 100, 1)), false),
    ),
    private val imageBytes: ByteArray = byteArrayOf(1, 2, 3),
) : PhotoSource {
    override val provider = "immich"
    val requestedPages = mutableListOf<Int>()

    override suspend fun health() = PhotoSourceHealth(
        ok = healthy,
        errorCategory = if (healthy) null else PhotoErrorCategories.NETWORK,
    )

    override suspend fun listAlbums() = listOf(PhotoAlbum("album-1", "Family", 3))

    override suspend fun listAssets(albumId: String, page: Int, pageSize: Int): PhotoAssetPage {
        requestedPages += page
        return pages[page] ?: PhotoAssetPage(emptyList(), false)
    }

    override suspend fun fetchImage(asset: PhotoAsset, size: DisplaySize): Result<ByteArray> =
        Result.success(imageBytes)
}

private class MemoryPhotoCache : PhotoCache {
    private val dir = kotlin.io.path.createTempDirectory("portal-photo-test").toFile()
    private val metadata = linkedMapOf<String, CacheMeta>()
    private val data = linkedMapOf<String, ByteArray>()

    override suspend fun get(cacheKey: String): Pair<ByteArray, CacheMeta>? {
        val bytes = data[cacheKey] ?: return null
        val meta = metadata[cacheKey] ?: return null
        return bytes to meta
    }

    override suspend fun put(meta: CacheMeta, bytes: ByteArray) {
        metadata[meta.cacheKey] = meta.copy(size = bytes.size.toLong())
        data[meta.cacheKey] = bytes
        fileFor(meta.cacheKey).writeBytes(bytes)
    }

    override suspend fun evict(cacheKey: String) {
        metadata.remove(cacheKey)
        data.remove(cacheKey)
        fileFor(cacheKey).delete()
    }

    override suspend fun keys(): Set<String> = metadata.keys.toSet()
    override suspend fun entries(): List<CacheMeta> = metadata.values.toList()

    override suspend fun trimTo(maxEntries: Int, maxBytes: Long) {
        while (metadata.size > maxEntries || totalBytes() > maxBytes) {
            evict(metadata.values.minByOrNull { it.createdAt }!!.cacheKey)
        }
    }

    override suspend fun totalBytes(): Long = data.values.sumOf { it.size.toLong() }
    override fun fileFor(cacheKey: String): File = File(dir, cacheKey.replace(':', '_'))
}
