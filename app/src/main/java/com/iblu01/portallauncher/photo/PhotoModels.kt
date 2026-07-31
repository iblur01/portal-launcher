package com.iblu01.portallauncher.photo

import java.io.File

/**
 * Provider-neutral photo asset. The asset ID is provider-scoped; the [cacheKey] is the stable,
 * provider-agnostic identity used for the local disk cache. No provider URL is stored — URLs are
 * ephemeral and must be resolved per-request by the adapter.
 */
data class PhotoAsset(
    val id: String,
    val cacheKey: String,
    val width: Int,
    val height: Int,
    val capturedAt: Long,
    val orientation: Int = 0,
)

/**
 * A photo album / collection exposed by a provider. [assetCount] is the provider's reported count
 * and may differ from the number of assets the coordinator has cached locally.
 */
data class PhotoAlbum(
    val id: String,
    val label: String,
    val assetCount: Int,
)

/**
 * One page of assets returned by a [PhotoSource]. [hasMore] drives the coordinator's pre-fetch loop.
 */
data class PhotoAssetPage(
    val assets: List<PhotoAsset>,
    val hasMore: Boolean,
)

/**
 * Dimensions requested for a display-sized image fetch. The provider may choose the closest
 * available rendition.
 */
data class DisplaySize(
    val width: Int,
    val height: Int,
) {
    val maxDim: Int get() = maxOf(width, height)
}

/**
 * Health as reported by a provider adapter. [errorCategory] is a sanitized, user-facing category
 * (e.g. "network", "auth", "server") and must never contain credentials or raw URLs.
 */
data class PhotoSourceHealth(
    val ok: Boolean,
    val errorCategory: String? = null,
)

/**
 * Status emitted by the coordinator for telemetry and the read-only MQTT/HA sensor. All fields are
 * safe to publish: no credentials, no raw/signed URLs, no file paths beyond the cache directory.
 */
data class PhotoCoordinatorStatus(
    val provider: String,
    val healthy: Boolean,
    val lastSuccessfulRefreshAt: Long? = null,
    val selectedAlbumLabel: String? = null,
    val cachedAssets: Int = 0,
    val cachedBytes: Long = 0L,
    val errorCategory: String? = null,
)

/**
 * Frame produced by the coordinator for the UI. The [file] is the on-disk cached image the
 * background composable reads; [cacheKey] busts Coil's cache when the same slot is reused.
 */
data class PhotoFrame(
    val file: File,
    val cacheKey: String,
    val assetId: String,
    val provider: String,
)

/**
 * Runtime configuration for the coordinator. All policy knobs live here so the same coordinator
 * can be unit-tested with small, deterministic bounds.
 */
data class PhotoCoordinatorConfig(
    val refreshIntervalMinutes: Int = 60,
    val cadenceSeconds: Int = 30,
    val shuffle: Boolean = true,
    val maxCacheEntries: Int = 200,
    val maxCacheBytes: Long = 250 * 1024 * 1024L,
    val prefetchCount: Int = 2,
    val retryBaseSeconds: Int = 2,
    val retryMaxSeconds: Int = 60,
    val targetWidth: Int = 1200,
    val targetHeight: Int = 1200,
)

/** Provider failure carrying only a bounded category safe for status publication. */
open class PhotoSourceException(val category: String) : Exception(category)

/**
 * Sanitized error categories shared across adapters. Keep these values in sync with the UI strings
 * and the MQTT/HA status payload.
 */
object PhotoErrorCategories {
    const val NONE = ""
    const val NETWORK = "network"
    const val AUTH = "auth"
    const val SERVER = "server"
    const val CONFIG = "config"
    const val CACHE = "cache"
    const val TOO_LARGE = "too_large"
    const val UNKNOWN = "unknown"
}

/**
 * Selection of albums configured by the user. The list contains provider album IDs; labels are
 * resolved at runtime by the adapter and are not durable.
 */
data class PhotoAlbumSelection(
    val provider: String,
    val albumIds: List<String>,
)
