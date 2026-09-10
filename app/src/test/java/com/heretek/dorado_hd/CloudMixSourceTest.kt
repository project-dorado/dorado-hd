package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudMixSource
import com.heretek.dorado_hd.data.repo.DoradoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the QuickMix-backed mix suggester: enable gating, request path, and
 * parsing/dedup of recommended artist names.
 */
class CloudMixSourceTest {

    private val enabled = DoradoSettings(cloudEnabled = true, cloudBaseUrl = "https://cloud.example/")

    @Test
    fun disabledWhenSettingOff() {
        val source = CloudMixSource({ DoradoSettings(cloudEnabled = false) })
        assertFalse(source.isEnabled())
    }

    @Test
    fun similarArtists_parsesAndDedupes() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """{"seed":"Massive Attack","seedMbid":null,"total":3,"attribution":"MusicBrainz","items":[{"mbid":"a","name":"Portishead","score":0.9,"reasons":["tag:trip-hop"]},{"mbid":"b","name":"Portishead","score":0.8,"reasons":[]},{"mbid":"c","name":"Tricky","score":0.7,"reasons":[]}]}""",
        )
        val source = CloudMixSource({ enabled }) { http }

        val artists = source.similarArtists("Massive Attack", limit = 10)

        assertEquals(listOf("Portishead", "Tricky"), artists)
        assertTrue(http.lastPath!!.startsWith("v1/recs/quickmix?seed=Massive%20Attack"))
        assertTrue(http.lastPath!!.contains("limit=10"))
    }

    @Test
    fun similarArtists_emptyWhenDisabled() = runBlocking {
        val source = CloudMixSource({ DoradoSettings(cloudEnabled = true, cloudBaseUrl = "") })
        assertTrue(source.similarArtists("Massive Attack").isEmpty())
    }

    @Test
    fun similarArtists_emptyOnError() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(500, "boom")
        val source = CloudMixSource({ enabled }) { http }

        assertTrue(source.similarArtists("Massive Attack").isEmpty())
    }
}
