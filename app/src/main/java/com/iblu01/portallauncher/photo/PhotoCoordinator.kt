package com.iblu01.portallauncher.photo

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import java.io.File
import kotlin.math.min
import kotlin.random.Random

/**
 * Coordinates a single [PhotoSource]: refresh scheduling, ordering/shuffle, cadence, prefetch,
 * bounded disk cache, retry/backoff, cache reconciliation, and offline fallback. The UI observes
 * [currentFrame] and telemetry observes [status].
 *
 * The coordinator is intentionally provider-agnostic: it never knows the shape of an Immich album,
 * a Google Photos album, or a custom URL. All provider-specific work lives in [PhotoSource].
 */
class PhotoCoordinator(
    private val source: PhotoSource,
    private val cache: PhotoCache,
    @Volatile private var config: PhotoCoordinatorConfig,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val selection: () -> PhotoAlbumSelection? = { null },
    private val random: Random = Random.Default,
) {
    private val _status = MutableStateFlow(
        PhotoCoordinatorStatus(provider = source.provider, healthy = false)
    )
    val status: StateFlow<PhotoCoordinatorStatus> = _status.asStateFlow()

    private val _currentFrame = MutableStateFlow<PhotoFrame?>(null)
    val currentFrame: StateFlow<PhotoFrame?> = _currentFrame.asStateFlow()

    private var job: Job? = null
    @Volatile private var orderedAssets: List<PhotoAsset> = emptyList()
    @Volatile private var currentIndex: Int = 0

    /** Start the refresh-and-cadence loop. Idempotent: multiple calls are no-ops. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            restoreCachedAssets()
            var refreshAttempt = 0
            var lastRefreshAt = 0L
            while (isActive) {
                val now = clock()
                val nextRefreshDue = lastRefreshAt + config.refreshIntervalMinutes * 60_000L
                val shouldRefresh = orderedAssets.isEmpty() || now >= nextRefreshDue
                if (shouldRefresh) {
                    when (val result = refreshAssets()) {
                        is RefreshResult.Success -> {
                            refreshAttempt = 0
                            lastRefreshAt = clock()
                        }
                        is RefreshResult.Error -> {
                            refreshAttempt++
                            val backoff = min(
                                config.retryBaseSeconds * (1 shl min(refreshAttempt, 10)),
                                config.retryMaxSeconds,
                            ).toLong()
                            _status.value = _status.value.copy(
                                healthy = false,
                                cachedAssets = cache.keys().size,
                                cachedBytes = cache.totalBytes(),
                                errorCategory = result.category,
                            )
                            showNextFrame()
                            delay(min(backoff, config.cadenceSeconds.toLong()) * 1000L)
                            continue
                        }
                    }
                }

                // Ensure at least the current and next images are cached (prefetch).
                prefetchAround()

                // Advance the frame on the configured cadence.
                showNextFrame()
                delay(config.cadenceSeconds * 1000L)
            }
        }
    }

    /** Stop the loop. [currentFrame] and [status] remain available for inspection. */
    fun stop() {
        job?.cancel()
        job = null
    }

    fun restart() {
        stop()
        start()
    }

    fun reconfigure(newConfig: PhotoCoordinatorConfig) {
        config = newConfig
        restart()
    }

    fun removeProvider(provider: String) {
        stop()
        source.clearCachedConfiguration()
        scope.launch {
            clearProviderCache(provider)
            orderedAssets = emptyList()
            currentIndex = 0
            _currentFrame.value = null
            _status.value = PhotoCoordinatorStatus(provider = "none", healthy = false)
        }
    }

    /** Force a refresh of the asset list on the next loop iteration. */
    fun requestRefresh() {
        orderedAssets = emptyList()
    }

    private suspend fun refreshAssets(): RefreshResult {
        val currentSelection = selection()
        if (currentSelection == null) {
            return RefreshResult.Error(PhotoErrorCategories.CONFIG)
        }
        if (currentSelection.albumIds.isEmpty()) {
            clearProviderCache(currentSelection.provider)
            orderedAssets = emptyList()
            currentIndex = 0
            _currentFrame.value = null
            return RefreshResult.Error(PhotoErrorCategories.CONFIG)
        }

        val health = runCatching { source.health() }.getOrElse {
            return RefreshResult.Error(categorize(it))
        }
        if (!health.ok) {
            return RefreshResult.Error(health.errorCategory ?: PhotoErrorCategories.UNKNOWN)
        }

        val albums = runCatching { source.listAlbums() }.getOrElse {
            return RefreshResult.Error(categorize(it))
        }.associateBy { it.id }

        val allAssets = linkedMapOf<String, PhotoAsset>()
        val labels = mutableListOf<String>()
        for (albumId in currentSelection.albumIds.distinct()) {
            labels += albums[albumId]?.label ?: albumId
            val assets = runCatching { fetchAllPages(albumId) }.getOrElse {
                return RefreshResult.Error(categorize(it))
            }
            assets.forEach { allAssets[it.cacheKey] = it }
        }

        if (allAssets.isEmpty()) {
            return RefreshResult.Error(PhotoErrorCategories.CONFIG)
        }

        // Reconcile: evict anything that was cached for an asset no longer in the selected albums.
        val wantedKeys = allAssets.keys
        val staleKeys = cache.entries()
            .filter { it.provider == currentSelection.provider && it.cacheKey !in wantedKeys }
            .map { it.cacheKey }
        for (key in staleKeys) {
            cache.evict(key)
        }
        cache.trimTo(config.maxCacheEntries, config.maxCacheBytes)

        val assets = allAssets.values.toList()
        orderedAssets = if (config.shuffle) assets.shuffled(random) else assets.sortedBy { it.capturedAt }
        currentIndex = 0

        val cached = cache.keys()
        _status.value = PhotoCoordinatorStatus(
            provider = currentSelection.provider,
            healthy = true,
            lastSuccessfulRefreshAt = clock(),
            selectedAlbumLabel = labels.joinToString(", "),
            cachedAssets = cached.size,
            cachedBytes = cache.totalBytes(),
            errorCategory = null,
        )
        return RefreshResult.Success
    }

    private suspend fun fetchAllPages(albumId: String): List<PhotoAsset> {
        val assets = mutableListOf<PhotoAsset>()
        for (page in 0 until MAX_PAGES) {
            val result = source.listAssets(albumId, page, PAGE_SIZE)
            assets += result.assets
            if (!result.hasMore) return assets
        }
        throw PhotoSourceException(PhotoErrorCategories.SERVER)
    }

    private suspend fun restoreCachedAssets() {
        val provider = selection()?.provider ?: source.provider
        val cached = cache.entries().filter { it.provider == provider }
        if (cached.isEmpty()) return
        val assets = cached.sortedBy { it.createdAt }.map {
            PhotoAsset(
                id = it.assetId,
                cacheKey = it.cacheKey,
                width = 0,
                height = 0,
                capturedAt = it.createdAt,
            )
        }
        orderedAssets = if (config.shuffle) assets.shuffled(random) else assets
        currentIndex = 0
        _status.value = _status.value.copy(
            provider = provider,
            healthy = false,
            cachedAssets = cached.size,
            cachedBytes = cache.totalBytes(),
            errorCategory = PhotoErrorCategories.NETWORK,
        )
    }

    private suspend fun clearProviderCache(provider: String) {
        cache.entries()
            .filter { it.provider == provider }
            .forEach { cache.evict(it.cacheKey) }
    }

    private suspend fun prefetchAround() {
        val assets = orderedAssets
        if (assets.isEmpty()) return
        val indices = (0..min(config.prefetchCount, assets.size - 1)).map { (currentIndex + it) % assets.size }
        for (i in indices) {
            val asset = assets[i]
            if (!cache.keys().contains(asset.cacheKey)) {
                fetchAndCache(asset)
            }
        }
    }

    private suspend fun showNextFrame() {
        val assets = orderedAssets
        if (assets.isEmpty()) return
        val asset = assets[currentIndex % assets.size]
        currentIndex = (currentIndex + 1) % assets.size

        val file = ensureCached(asset) ?: return
        _currentFrame.value = PhotoFrame(
            file = file,
            cacheKey = asset.cacheKey,
            assetId = asset.id,
            provider = source.provider,
        )
    }

    private suspend fun ensureCached(asset: PhotoAsset): File? {
        cache.get(asset.cacheKey)?.let { return cache.fileFor(asset.cacheKey) }
        return fetchAndCache(asset)
    }

    private suspend fun fetchAndCache(asset: PhotoAsset): File? {
        val result = source.fetchImage(asset, DisplaySize(config.targetWidth, config.targetHeight))
        return result.mapCatching { bytes ->
            if (bytes.isEmpty()) throw PhotoSourceException(PhotoErrorCategories.SERVER)
            val meta = CacheMeta(
                cacheKey = asset.cacheKey,
                assetId = asset.id,
                provider = source.provider,
                createdAt = clock(),
                size = bytes.size.toLong(),
            )
            val file = cache.fileFor(asset.cacheKey)
            cache.put(meta, bytes)
            cache.trimTo(config.maxCacheEntries, config.maxCacheBytes)
            val cached = cache.get(asset.cacheKey)
            _status.value = _status.value.copy(
                cachedAssets = cache.keys().size,
                cachedBytes = cache.totalBytes(),
            )
            if (cached == null) null else file
        }.getOrElse {
            _status.value = _status.value.copy(
                healthy = false,
                errorCategory = categorize(it),
            )
            null
        }
    }

    private fun categorize(t: Throwable): String = when {
        t is PhotoSourceException -> t.category
        t is java.net.UnknownHostException || t is java.net.SocketTimeoutException || t is java.io.IOException -> PhotoErrorCategories.NETWORK
        t.message?.contains("401", ignoreCase = true) == true ||
            t.message?.contains("unauthorized", ignoreCase = true) == true ||
            t.message?.contains("forbidden", ignoreCase = true) == true -> PhotoErrorCategories.AUTH
        t.message?.contains("5", ignoreCase = true) == true ||
            t.message?.contains("server", ignoreCase = true) == true -> PhotoErrorCategories.SERVER
        else -> PhotoErrorCategories.UNKNOWN
    }

    private sealed class RefreshResult {
        object Success : RefreshResult()
        data class Error(val category: String) : RefreshResult()
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
    }
}
