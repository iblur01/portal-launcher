package com.iblu01.portallauncher.photo

import com.iblu01.portallauncher.HaDiscovery
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStatusContractTest {
    @Test fun `attributes contain only bounded source agnostic diagnostics`() {
        val secret = "top-secret-key"
        val rawUrl = "https://photos.example.com/api"
        val attributes = PhotoStatusSerializer.attributes(
            PhotoCoordinatorStatus(
                provider = "immich",
                healthy = false,
                lastSuccessfulRefreshAt = 1234,
                selectedAlbumLabel = "Family\nPhotos" + "x".repeat(200),
                cachedAssets = 5,
                cachedBytes = 99,
                errorCategory = PhotoErrorCategories.NETWORK,
            ),
        )
        val json = JSONObject(attributes)

        assertEquals("immich", json.getString("provider"))
        assertEquals(PhotoErrorCategories.NETWORK, json.getString("error_category"))
        assertTrue(json.getString("selected_album").length <= 120)
        assertFalse(json.getString("selected_album").contains('\n'))
        assertFalse(attributes.contains(secret))
        assertFalse(attributes.contains(rawUrl))
        assertFalse(attributes.contains("asset_id"))
        assertFalse(attributes.contains("file"))
    }

    @Test fun `unknown provider and error are not reflected`() {
        val attributes = JSONObject(
            PhotoStatusSerializer.attributes(
                PhotoCoordinatorStatus(provider = "https://private", healthy = false, errorCategory = "secret detail"),
            ),
        )
        assertEquals("unknown", attributes.getString("provider"))
        assertTrue(attributes.isNull("error_category"))
    }

    @Test fun `home assistant entity is read only and expires offline`() {
        val payload = HaDiscovery.photoStatusConfigPayload("portal-test", "Portal")
        assertFalse(payload.contains("command_topic"))
        assertTrue(payload.contains("\"expire_after\":15"))
        assertEquals("portal/portal-test/photo/status", HaDiscovery.photoStatusStateTopic("portal-test"))
        assertEquals("portal/portal-test/photo/attributes", HaDiscovery.photoStatusAttributesTopic("portal-test"))
    }

    @Test fun `state distinguishes healthy cached offline and disabled`() {
        assertEquals("ok", PhotoStatusSerializer.state(PhotoCoordinatorStatus(provider = "immich", healthy = true)))
        assertEquals(
            "offline_cached",
            PhotoStatusSerializer.state(
                PhotoCoordinatorStatus(
                    provider = "immich",
                    healthy = false,
                    cachedAssets = 2,
                    errorCategory = PhotoErrorCategories.NETWORK,
                ),
            ),
        )
        assertEquals("disabled", PhotoStatusSerializer.state(PhotoCoordinatorStatus(provider = "none", healthy = false)))
    }
}
