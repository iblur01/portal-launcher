package com.iblu01.portallauncher.photo

/**
 * Provider-neutral source of display-sized photos. Each source owns its identity, album enumeration,
 * paged asset enumeration, and image fetch. It intentionally knows nothing about cadence,
 * presentation, ordering, caching, or offline fallback — those are the coordinator's job.
 */
interface PhotoSource {
    /** Provider key, e.g. "immich". */
    val provider: String

    /** Quick liveness probe. Must return fast; used for status telemetry. */
    suspend fun health(): PhotoSourceHealth

    /** All albums the user may choose from. */
    suspend fun listAlbums(): List<PhotoAlbum>

    /**
     * Enumerate one page of assets inside [albumId]. The page semantics are provider-specific;
     * the coordinator only relies on [PhotoAssetPage.hasMore] to stop paging.
     */
    suspend fun listAssets(albumId: String, page: Int, pageSize: Int): PhotoAssetPage

    /**
     * Fetch a display-sized image for [asset]. The requested size is a hint; the source may return
     * the nearest available rendition. The result is a byte array so the coordinator can write it to
     * the bounded disk cache and decide which file to expose to the UI.
     */
    suspend fun fetchImage(asset: PhotoAsset, size: DisplaySize): Result<ByteArray>

    /** Drop any provider configuration cached in process memory. */
    fun clearCachedConfiguration() = Unit
}

/**
 * Aggregate source that combines zero or more configured sources. For v1 only one source is active
 * at a time, but the indirection keeps the door open for multi-provider later without touching the
 * coordinator.
 */
interface PhotoSourceProvider {
    fun current(): PhotoSource?
    fun clearCachedSource() = Unit
}

/**
 * TLS / transport policy for a photo provider. Explicitly stored so tests can assert the policy is
 * honoured rather than relying on the global [usesCleartextTraffic] flag.
 */
enum class TransportPolicy {
    REQUIRE_SECURE,
    ALLOW_INSECURE,
}
