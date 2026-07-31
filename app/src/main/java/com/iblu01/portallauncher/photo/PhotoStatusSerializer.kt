package com.iblu01.portallauncher.photo

import org.json.JSONObject

/** Read-only, bounded MQTT/HA serialization. Never add credentials, URLs, asset IDs, or file paths. */
object PhotoStatusSerializer {
    private val providers = setOf("immich", "none")
    private val errors = setOf(
        PhotoErrorCategories.NETWORK,
        PhotoErrorCategories.AUTH,
        PhotoErrorCategories.SERVER,
        PhotoErrorCategories.CONFIG,
        PhotoErrorCategories.CACHE,
        PhotoErrorCategories.TOO_LARGE,
        PhotoErrorCategories.UNKNOWN,
    )

    fun state(status: PhotoCoordinatorStatus): String = when {
        status.provider == "none" -> "disabled"
        status.healthy -> "ok"
        status.cachedAssets > 0 && status.errorCategory == PhotoErrorCategories.NETWORK -> "offline_cached"
        else -> sanitizeError(status.errorCategory) ?: "error"
    }

    fun attributes(status: PhotoCoordinatorStatus): String = JSONObject().apply {
        put("provider", status.provider.takeIf { it in providers } ?: "unknown")
        put("healthy", status.healthy)
        put("last_successful_refresh", status.lastSuccessfulRefreshAt ?: JSONObject.NULL)
        put("selected_album", sanitizeLabel(status.selectedAlbumLabel) ?: JSONObject.NULL)
        put("cached_assets", status.cachedAssets.coerceIn(0, 100_000))
        put("cached_bytes", status.cachedBytes.coerceIn(0L, 10L * 1024 * 1024 * 1024))
        put("error_category", sanitizeError(status.errorCategory) ?: JSONObject.NULL)
    }.toString()

    private fun sanitizeError(value: String?): String? = value?.takeIf { it in errors }

    private fun sanitizeLabel(value: String?): String? = value
        ?.filter { it >= ' ' && it != '\u007f' }
        ?.trim()
        ?.take(120)
        ?.takeIf { it.isNotEmpty() }
}
