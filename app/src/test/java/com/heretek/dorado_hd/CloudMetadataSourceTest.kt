package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudMetadataSource
import com.heretek.dorado_hd.data.repo.DoradoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the HD app's cloud-first artist metadata hydration: enabled gating,
 * catalog resolution, artwork fetch, bio fallback, and listen-activity posting.
 */
class CloudMetadataSourceTest {

    private val enabled = DoradoSettings(cloudEnabled = true, cloudBaseUrl = "https://cloud.example/")

    private val searchJson =
        """{"query":"Massive Attack","type":"artist","total":1,"attribution":"MusicBrainz","items":[{"type":"artist","mbid":"abc-123","title":"Massive Attack","artist":"Massive Attack","date":"","coverArtUrl":null}]}"""

    @Test
    fun disabledWhenSettingOff() {
        val source = CloudMetadataSource({ DoradoSettings(cloudEnabled = false, cloudBaseUrl = "https://x/") })
        assertFalse(source.isEnabled())
    }

    @Test
    fun disabledWhenBaseUrlBlank() {
        val source = CloudMetadataSource({ DoradoSettings(cloudEnabled = true, cloudBaseUrl = "") })
        assertFalse(source.isEnabled())
    }

    @Test
    fun artistMbid_usesCatalogSearch() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, searchJson)
        val source = CloudMetadataSource({ enabled }) { http }

        assertEquals("abc-123", source.artistMbid("Massive Attack"))
        assertEquals("v1/catalog/search?q=Massive%20Attack&type=artist&limit=1", http.lastPath)
    }

    @Test
    fun artistImage_fetchesArtworkBytes() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, searchJson)
        http.enqueueBytes(ByteArray(2048) { 7 })
        val source = CloudMetadataSource({ enabled }) { http }

        val bytes = source.artistImage("Massive Attack")

        assertNotNull(bytes)
        assertEquals(2048, bytes!!.size)
        assertEquals("v1/artwork/front/abc-123?size=500", http.lastPath)
    }

    @Test
    fun artistBio_usesDisambiguation() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, searchJson)
        http.enqueue(200, """{"mbid":"abc-123","name":"Massive Attack","sortName":"","country":"GB","disambiguation":"UK trip-hop collective","coverArtUrl":null}""")
        val source = CloudMetadataSource({ enabled }) { http }

        assertEquals("UK trip-hop collective", source.artistBio("Massive Attack"))
        assertEquals("v1/catalog/artists/abc-123", http.lastPath)
    }

    @Test
    fun artistImage_nullWhenArtistUnknown() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"query":"nobody","type":"artist","total":0,"attribution":"","items":[]}""")
        val source = CloudMetadataSource({ enabled }) { http }

        assertNull(source.artistImage("nobody"))
    }

    @Test
    fun recordListen_requiresAccessToken() = runBlocking {
        val withoutToken = DoradoSettings(cloudEnabled = true, cloudBaseUrl = "https://cloud.example/", cloudAccessToken = "")
        val source = CloudMetadataSource({ withoutToken })

        assertFalse(source.recordListen("Artist", "Track", "Album"))
    }

    @Test
    fun recordListen_postsActivityWithBearer() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(201, """{"id":"a1","handle":"","kind":"listen","payloadJson":"{}","createdAt":""}""")
        val withToken = enabled.copy(cloudAccessToken = "tok")
        val source = CloudMetadataSource({ withToken }) { http }

        val recorded = source.recordListen("Massive Attack", "Teardrop", "Mezzanine")
        assertTrue("recorded=$recorded path=${http.lastPath} body=${http.lastBody}", recorded)

        assertEquals("v1/social/me/activities", http.lastPath)
        assertEquals("Bearer tok", http.lastAuth)
        // The listen metadata rides inside the activity's payloadJson string.
        assertTrue(http.lastBody!!.contains("Teardrop"))
        assertTrue(http.lastBody!!.contains("Mezzanine"))
        assertTrue(http.lastBody!!.contains("\"kind\":\"listen\""))
    }
}
