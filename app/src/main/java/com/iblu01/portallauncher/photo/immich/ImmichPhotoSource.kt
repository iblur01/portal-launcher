package com.iblu01.portallauncher.photo.immich

import com.iblu01.portallauncher.photo.DisplaySize
import com.iblu01.portallauncher.photo.HttpTransport
import com.iblu01.portallauncher.photo.PhotoAlbum
import com.iblu01.portallauncher.photo.PhotoAsset
import com.iblu01.portallauncher.photo.PhotoAssetPage
import com.iblu01.portallauncher.photo.PhotoErrorCategories
import com.iblu01.portallauncher.photo.PhotoSource
import com.iblu01.portallauncher.photo.PhotoSourceHealth
import com.iblu01.portallauncher.photo.PhotoSourceException
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.photo.toErrorCategory
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.net.URI

/**
 * Immich v1 HTTP adapter. All provider-specific JSON parsing and URL construction lives here.
 *
 * The adapter is given an [HttpTransport] so tests can run it against a fake server without a network.
 * It never logs or exposes the API key, and it never treats a provider URL as durable: the only
 * stable identifier stored by the coordinator is the asset cache key derived from the asset id.
 */
class ImmichApiClient(
    private val transport: HttpTransport,
    private val baseUrl: String,
    private val apiKey: String,
    private val policy: TransportPolicy = TransportPolicy.REQUIRE_SECURE,
) {
    private val cleanBaseUrl = baseUrl.trim().trimEnd('/')
    private val albumCache = mutableMapOf<String, CachedAlbum>()

    init {
        val uri = runCatching { URI(cleanBaseUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        require(uri?.host != null && uri.userInfo == null) { "invalid_base_url" }
        require(scheme == "https" || (scheme == "http" && policy == TransportPolicy.ALLOW_INSECURE)) {
            "transport_policy_rejected"
        }
        require(apiKey.isNotBlank()) { "missing_api_key" }
    }

    suspend fun health(): PhotoSourceHealth {
        val response = transport.get(
            url = "$cleanBaseUrl/api/server-info/ping",
            headers = authHeaders(),
        )
        return if (response.code == 200) {
            PhotoSourceHealth(ok = true)
        } else {
            PhotoSourceHealth(ok = false, errorCategory = response.toErrorCategory())
        }
    }

    suspend fun listAlbums(): List<PhotoAlbum> {
        val response = transport.get(
            url = "$cleanBaseUrl/api/albums",
            headers = authHeaders(),
        )
        if (response.code != 200 || response.body == null) {
            throw ImmichApiException(response.toErrorCategory())
        }
        val array = JSONArray(response.body)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.getString("id")
            PhotoAlbum(
                id = id,
                label = o.optString("albumName").takeIf { it.isNotBlank() } ?: id,
                assetCount = o.optInt("assetCount", 0),
            )
        }
    }

    /**
     * Fetches one album with its assets. Immich v1 returns the full album on `GET /api/albums/{id}`;
     * this method turns that into a paged window starting at [page] * [pageSize].
     */
    suspend fun listAlbumAssets(albumId: String, page: Int, pageSize: Int): ImmichAlbumPage {
        require(page in 0..MAX_LOGICAL_PAGE && pageSize in 1..MAX_PAGE_SIZE) { "invalid_page" }
        val now = System.currentTimeMillis()
        val cached = albumCache[albumId]?.takeIf { now - it.loadedAtMs <= ALBUM_CACHE_TTL_MS }
        val album = if (cached != null) {
            cached
        } else {
            val encoded = URLEncoder.encode(albumId, "UTF-8")
            val response = transport.get(
                url = "$cleanBaseUrl/api/albums/$encoded?withoutAssets=false",
                headers = authHeaders(),
            )
            if (response.code != 200 || response.body == null) {
                throw ImmichApiException(response.toErrorCategory())
            }
            val json = JSONObject(response.body)
            CachedAlbum(
                label = json.optString("albumName", albumId),
                assets = parseAssets(json.optJSONArray("assets") ?: JSONArray()),
                loadedAtMs = now,
            ).also { albumCache[albumId] = it }
        }
        val start = page * pageSize
        if (start >= album.assets.size) {
            return ImmichAlbumPage(
                label = album.label,
                assets = emptyList(),
                hasMore = false,
            )
        }
        val end = minOf(start + pageSize, album.assets.size)
        return ImmichAlbumPage(
            label = album.label,
            assets = album.assets.subList(start, end),
            hasMore = end < album.assets.size,
        )
    }

    suspend fun fetchThumbnail(assetId: String, size: DisplaySize): ByteArray {
        val encoded = URLEncoder.encode(assetId, "UTF-8")
        // Immich thumbnail sizes: THUMBNAIL (small), PREVIEW (larger). We request PREVIEW for
        // display-sized backgrounds; the coordinator can downscale if needed.
        val sizeParam = if (size.maxDim >= 720) "preview" else "thumbnail"
        val response = transport.getBytes(
            url = "$cleanBaseUrl/api/assets/$encoded/thumbnail?size=$sizeParam",
            headers = authHeaders(),
        )
        val bytes = response.bytes
        if (response.code != 200 || bytes == null) {
            throw ImmichApiException(response.toErrorCategory())
        }
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) {
            throw ImmichApiException(PhotoErrorCategories.SERVER)
        }
        return bytes
    }

    private fun parseAssets(array: JSONArray): List<PhotoAsset> =
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            if (!o.has("id")) return@mapNotNull null
            if (o.optString("type", "IMAGE").uppercase() != "IMAGE") return@mapNotNull null
            val id = o.getString("id")
            val exif = o.optJSONObject("exifInfo") ?: JSONObject()
            val width = o.optInt("width", exif.optInt("exifImageWidth", 0))
            val height = o.optInt("height", exif.optInt("exifImageHeight", 0))
            val capturedAt = parseImmichTime(o.optString("fileCreatedAt"))
            PhotoAsset(
                id = id,
                cacheKey = "immich:$id",
                width = width,
                height = height,
                capturedAt = capturedAt,
                orientation = exif.optInt("orientation", 0),
            )
        }

    private fun parseImmichTime(value: String?): Long =
        value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private fun authHeaders(): Map<String, String> = mapOf(
        "x-api-key" to apiKey,
        "Accept" to "application/json",
    )

    data class ImmichAlbumPage(
        val label: String,
        val assets: List<PhotoAsset>,
        val hasMore: Boolean,
    )

    class ImmichApiException(category: String) : PhotoSourceException(category)

    private data class CachedAlbum(
        val label: String,
        val assets: List<PhotoAsset>,
        val loadedAtMs: Long,
    )

    private companion object {
        const val MAX_IMAGE_BYTES = 25 * 1024 * 1024
        const val ALBUM_CACHE_TTL_MS = 60_000L
        const val MAX_LOGICAL_PAGE = 10_000
        const val MAX_PAGE_SIZE = 500
    }
}

/**
 * [PhotoSource] wrapper around [ImmichApiClient]. The coordinator sees a generic source; all Immich
 * details are encapsulated.
 */
class ImmichPhotoSource(
    private val api: ImmichApiClient,
) : PhotoSource {
    override val provider: String = "immich"

    override suspend fun health(): PhotoSourceHealth = api.health()

    override suspend fun listAlbums(): List<PhotoAlbum> = api.listAlbums()

    override suspend fun listAssets(albumId: String, page: Int, pageSize: Int): PhotoAssetPage {
        val result = api.listAlbumAssets(albumId, page, pageSize)
        return PhotoAssetPage(result.assets, result.hasMore)
    }

    override suspend fun fetchImage(asset: PhotoAsset, size: DisplaySize): Result<ByteArray> =
        runCatching { api.fetchThumbnail(asset.id, size) }
}

/**
 * Convenience factory that builds the v1 source from plain settings. The API key is not stored or
 * logged inside this factory; it is passed straight into the client.
 */
fun ImmichPhotoSource(
    transport: HttpTransport,
    baseUrl: String,
    apiKey: String,
    policy: TransportPolicy = TransportPolicy.REQUIRE_SECURE,
): ImmichPhotoSource = ImmichPhotoSource(
    ImmichApiClient(transport, baseUrl, apiKey, policy)
)
