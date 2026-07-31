package com.iblu01.portallauncher.photo.immich


import com.iblu01.portallauncher.photo.DisplaySize
import com.iblu01.portallauncher.photo.HttpTransport
import com.iblu01.portallauncher.photo.PhotoErrorCategories
import com.iblu01.portallauncher.photo.TransportPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImmichApiClientTest {

    private val secret = "immich-api-key-secret"

    @Test
    fun `health returns ok on ping 200`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond("https://photos.example.com/api/server-info/ping", "{\"res\":\"pong\"}")
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val health = client.health()
        assertTrue(health.ok)
        assertNull(health.errorCategory)
    }

    @Test
    fun `health returns auth category on 401`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond("https://photos.example.com/api/server-info/ping", "", 401)
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val health = client.health()
        assertFalse(health.ok)
        assertEquals(PhotoErrorCategories.AUTH, health.errorCategory)
    }

    @Test
    fun `listAlbums parses page and keeps IDs`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond(
            "https://photos.example.com/api/albums",
            """
            [
              {"id":"album-1","albumName":"Vacation","assetCount":3},
              {"id":"album-2","albumName":"Pets","assetCount":2}
            ]
            """.trimIndent(),
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val albums = client.listAlbums()
        assertEquals(2, albums.size)
        assertEquals("album-1", albums[0].id)
        assertEquals("Vacation", albums[0].label)
        assertEquals(3, albums[0].assetCount)
    }

    @Test
    fun `listAlbumAssets pages correctly`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond(
            "https://photos.example.com/api/albums/album-1?withoutAssets=false",
            """
            {
              "id":"album-1",
              "albumName":"Vacation",
              "assets":[
                {"id":"a1","fileCreatedAt":"2024-01-01T00:00:00Z","exifInfo":{"exifImageWidth":1000}},
                {"id":"a2","fileCreatedAt":"2024-01-02T00:00:00Z"},
                {"id":"a3","fileCreatedAt":"2024-01-03T00:00:00Z"}
              ]
            }
            """.trimIndent(),
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val page0 = client.listAlbumAssets("album-1", page = 0, pageSize = 2)
        assertEquals(2, page0.assets.size)
        assertTrue(page0.hasMore)
        assertEquals("a1", page0.assets[0].id)
        assertEquals("immich:a1", page0.assets[0].cacheKey)

        val page1 = client.listAlbumAssets("album-1", page = 1, pageSize = 2)
        assertEquals(1, page1.assets.size)
        assertFalse(page1.hasMore)
        assertEquals("a3", page1.assets[0].id)
        assertEquals(
            1,
            transport.requests.count { it.url.contains("/api/albums/album-1?") },
        )
    }

    @Test
    fun `fetchThumbnail requests preview size for large display`() = runBlocking {
        val transport = FakeHttpTransport()
        val imageBytes = ByteArray(8) { it.toByte() }
        transport.respondWithBytes(
            "https://photos.example.com/api/assets/a1/thumbnail?size=preview",
            imageBytes,
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val bytes = client.fetchThumbnail("a1", DisplaySize(1200, 800))
        assertEquals(8, bytes.size)
    }

    @Test
    fun `fetchThumbnail requests thumbnail size for small display`() = runBlocking {
        val transport = FakeHttpTransport()
        val imageBytes = ByteArray(4) { it.toByte() }
        transport.respondWithBytes(
            "https://photos.example.com/api/assets/a1/thumbnail?size=thumbnail",
            imageBytes,
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        client.fetchThumbnail("a1", DisplaySize(400, 300))
        assertTrue(
            transport.requests.any { it.url.contains("size=thumbnail") },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `require secure policy rejects http base url`(): Unit = runBlocking {
        ImmichApiClient(FakeHttpTransport(), "http://photos.example.com", secret, TransportPolicy.REQUIRE_SECURE)
    }

    @Test
    fun `allow insecure policy accepts http base url`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond("http://photos.example.com/api/server-info/ping", "{\"res\":\"pong\"}")
        val client = ImmichApiClient(transport, "http://photos.example.com", secret, TransportPolicy.ALLOW_INSECURE)
        val health = client.health()
        assertTrue(health.ok)
    }

    @Test
    fun `api key is sent but never leaked in transport results`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond("https://photos.example.com/api/albums", "[]")
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        client.listAlbums()
        transport.assertApiKeyHeaderPresent()
        transport.assertApiKeyNotInRequestsOrResponses(secret)
    }
    @Test
    fun `malformed album response is rejected`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond("https://photos.example.com/api/albums", "not-json")
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val failure = runCatching { client.listAlbums() }.exceptionOrNull()
        assertTrue(failure is org.json.JSONException)
    }

    @Test
    fun `video assets are excluded`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respond(
            "https://photos.example.com/api/albums/album-1?withoutAssets=false",
            """{"assets":[{"id":"image","type":"IMAGE"},{"id":"video","type":"VIDEO"}]}""",
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        assertEquals(listOf("image"), client.listAlbumAssets("album-1", 0, 10).assets.map { it.id })
    }

    @Test
    fun `invalid scheme and user info are rejected without echoing url`() {
        val sensitiveUrl = "ftp://user:password@photos.example.com"
        val failure = runCatching {
            ImmichApiClient(FakeHttpTransport(), sensitiveUrl, secret, TransportPolicy.ALLOW_INSECURE)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(failure?.message.orEmpty().contains("password"))
        assertFalse(failure?.message.orEmpty().contains(sensitiveUrl))
    }

    @Test
    fun `empty thumbnail payload is categorized as server error`() = runBlocking {
        val transport = FakeHttpTransport()
        transport.respondWithBytes(
            "https://photos.example.com/api/assets/a1/thumbnail?size=preview",
            byteArrayOf(),
        )
        val client = ImmichApiClient(transport, "https://photos.example.com", secret)
        val failure = runCatching { client.fetchThumbnail("a1", DisplaySize(1200, 800)) }.exceptionOrNull()
        assertTrue(failure is com.iblu01.portallauncher.photo.PhotoSourceException)
        assertEquals(
            PhotoErrorCategories.SERVER,
            (failure as com.iblu01.portallauncher.photo.PhotoSourceException).category,
        )
    }
}

// Duplicate name needed because the real FakeHttpTransport is in the photo package and tests import
// it. This alias avoids a clash if the test file above accidentally uses the wrong scope.
private typealias FakeHttpTransport = com.iblu01.portallauncher.photo.FakeHttpTransport
