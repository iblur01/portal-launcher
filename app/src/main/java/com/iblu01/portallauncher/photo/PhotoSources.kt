package com.iblu01.portallauncher.photo

import com.iblu01.portallauncher.Prefs

/**
 * Default [PhotoSourceProvider] implementation. It consults [Prefs] at build time; for runtime mode
 * switching use a [PhotoSource] wrapper such as [ProxyPhotoSource].
 */
class DefaultPhotoSourceProvider(
    private val prefs: Prefs,
    private val transport: HttpTransport = OkHttpTransport(),
) : PhotoSourceProvider {
    private var cachedUrl: String? = null
    private var cachedApiKey: String? = null
    private var cachedPolicy: TransportPolicy? = null
    private var cachedSource: PhotoSource? = null

    @Synchronized
    override fun current(): PhotoSource? {
        if (prefs.backgroundMode != "immich") return clearCached()
        val url = prefs.immichUrl.takeIf { it.isNotBlank() } ?: return clearCached()
        val apiKey = prefs.immichApiKey.takeIf { it.isNotBlank() } ?: return clearCached()
        val policy = prefs.immichTransportPolicy
        if (url == cachedUrl && apiKey == cachedApiKey && policy == cachedPolicy) return cachedSource
        return com.iblu01.portallauncher.photo.immich.ImmichPhotoSource(
            transport = transport,
            baseUrl = url,
            apiKey = apiKey,
            policy = policy,
        ).also {
            cachedUrl = url
            cachedApiKey = apiKey
            cachedPolicy = policy
            cachedSource = it
        }
    }

    @Synchronized
    override fun clearCachedSource() {
        cachedUrl = null
        cachedApiKey = null
        cachedPolicy = null
        cachedSource = null
    }

    private fun clearCached(): PhotoSource? {
        clearCachedSource()
        return null
    }
}

/**
 * A [PhotoSource] that delegates to the provider's current source. This lets a single
 * [PhotoCoordinator] follow mode changes (e.g. neutral/unsplash/custom -> immich) without
 * recreating the whole coordinator.
 */
class ProxyPhotoSource(private val sourceProvider: PhotoSourceProvider) : PhotoSource {
    override val provider: String
        get() = runCatching { sourceProvider.current()?.provider }.getOrNull() ?: "none"

    private fun currentOrThrow(): PhotoSource = runCatching { sourceProvider.current() }
        .getOrElse { throw PhotoSourceException(PhotoErrorCategories.CONFIG) }
        ?: throw PhotoSourceException(PhotoErrorCategories.CONFIG)

    override suspend fun health(): PhotoSourceHealth = try {
        currentOrThrow().health()
    } catch (_: PhotoSourceException) {
        PhotoSourceHealth(ok = false, errorCategory = PhotoErrorCategories.CONFIG)
    }

    override suspend fun listAlbums(): List<PhotoAlbum> = currentOrThrow().listAlbums()

    override suspend fun listAssets(albumId: String, page: Int, pageSize: Int): PhotoAssetPage =
        currentOrThrow().listAssets(albumId, page, pageSize)

    override suspend fun fetchImage(asset: PhotoAsset, size: DisplaySize): Result<ByteArray> =
        runCatching { currentOrThrow() }
            .fold(
                onSuccess = { it.fetchImage(asset, size) },
                onFailure = { Result.failure(it) },
            )

    override fun clearCachedConfiguration() = sourceProvider.clearCachedSource()
}

/** Convenience builder for the v1 Immich-only photo coordinator. */
fun createDefaultPhotoCoordinator(
    provider: PhotoSourceProvider,
    cache: PhotoCache,
    scope: kotlinx.coroutines.CoroutineScope,
    prefs: Prefs,
): PhotoCoordinator = PhotoCoordinator(
    source = ProxyPhotoSource(provider),
    cache = cache,
    config = PhotoCoordinatorConfig(
        refreshIntervalMinutes = prefs.immichRefreshMinutes,
        cadenceSeconds = prefs.immichCadenceSeconds,
        shuffle = prefs.immichShuffle,
    ),
    scope = scope,
    selection = {
        if (prefs.backgroundMode != "immich") null
        else PhotoAlbumSelection(provider = "immich", albumIds = prefs.immichAlbumIds)
    },
)
