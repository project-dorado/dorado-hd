package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudCatalogItem
import com.heretek.dorado_hd.cloud.CloudMetadataSource
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.net.ArtistPhotos
import com.heretek.dorado_hd.net.ArtistPhotoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure rules behind the M13/B5 artist `photos` pivot: source selection (cloud →
 * Cover Art Archive → wallpaper), the cloud release-group URL builder, and the
 * MusicBrainz release-group response parser.
 */
class ArtistPhotosTest {

    private val releaseGroupJson = """
        {
          "release-group-count": 2,
          "release-groups": [
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "title": "Mezzanine",
              "primary-type": "Album",
              "artist-credit": [
                { "artist": { "id": "22222222-2222-2222-2222-222222222222", "name": "Massive Attack" } }
              ]
            },
            {
              "id": "33333333-3333-3333-3333-333333333333",
              "title": "Protection",
              "artist-credit": [
                { "artist": { "id": "22222222-2222-2222-2222-222222222222", "name": "Massive Attack" } }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun select_prefersCloudOverEverythingElse() {
        val selected = ArtistPhotos.select(
            cloud = listOf("https://cloud.example/1"),
            coverArt = listOf("https://coverartarchive.org/1"),
            wallpaper = "file:///data/wall.img",
        )
        assertEquals(1, selected.size)
        assertEquals(ArtistPhotoSource.CLOUD, selected[0].source)
        assertEquals("https://cloud.example/1", selected[0].url)
    }

    @Test
    fun select_fallsBackToCoverArt() {
        val selected = ArtistPhotos.select(
            cloud = emptyList(),
            coverArt = listOf("https://coverartarchive.org/1"),
            wallpaper = "file:///data/wall.img",
        )
        assertEquals(ArtistPhotoSource.COVER_ART, selected.single().source)
    }

    @Test
    fun select_fallsBackToWallpaperSoThePivotIsNeverEmpty() {
        val selected = ArtistPhotos.select(emptyList(), emptyList(), "file:///data/wall.img")
        assertEquals(ArtistPhotoSource.WALLPAPER, selected.single().source)
        assertEquals("file:///data/wall.img", selected.single().url)
    }

    @Test
    fun select_emptyOnlyWhenEverySourceIsEmpty() {
        assertTrue(ArtistPhotos.select(emptyList(), emptyList(), null).isEmpty())
        assertTrue(ArtistPhotos.select(emptyList(), emptyList(), "  ").isEmpty())
    }

    @Test
    fun select_ignoresBlanksAndDedupes() {
        val selected = ArtistPhotos.select(
            cloud = listOf(" https://cloud.example/1 ", "", "https://cloud.example/1", "https://cloud.example/2"),
            coverArt = listOf("https://coverartarchive.org/1"),
            wallpaper = null,
        )
        assertEquals(listOf("https://cloud.example/1", "https://cloud.example/2"), selected.map { it.url })
    }

    @Test
    fun select_capsTheGrid() {
        val selected = ArtistPhotos.select(
            cloud = (1..40).map { "https://cloud.example/$it" },
            coverArt = emptyList(),
            wallpaper = null,
        )
        assertEquals(ArtistPhotos.MAX_PHOTOS, selected.size)
    }

    @Test
    fun cloudCoverUrls_buildsArtworkModuleUrls() {
        val items = listOf(
            CloudCatalogItem("release-group", "rg-1", "Mezzanine", "Massive Attack", "", null),
            CloudCatalogItem("release-group", "rg-2", "Protection", "Massive Attack", "", null),
        )
        assertEquals(
            listOf(
                "https://cloud.example/v1/artwork/front/rg-1?size=500",
                "https://cloud.example/v1/artwork/front/rg-2?size=500",
            ),
            ArtistPhotos.cloudCoverUrls(items, "Massive Attack", "https://cloud.example/"),
        )
    }

    @Test
    fun cloudCoverUrls_skipsOtherArtistsAndBlankIds() {
        val items = listOf(
            CloudCatalogItem("release-group", "rg-1", "Mezzanine", "Massive Attack", "", null),
            CloudCatalogItem("release-group", "rg-2", "Tribute", "Some Cover Band", "", null),
            CloudCatalogItem("release-group", "", "Broken", "Massive Attack", "", null),
        )
        assertEquals(
            listOf("https://cloud.example/v1/artwork/front/rg-1?size=500"),
            ArtistPhotos.cloudCoverUrls(items, "massive attack", "https://cloud.example"),
        )
    }

    @Test
    fun cloudCoverUrls_requiresABaseUrl() {
        val items = listOf(CloudCatalogItem("release-group", "rg-1", "T", "A", "", null))
        assertTrue(ArtistPhotos.cloudCoverUrls(items, "A", "   ").isEmpty())
    }

    @Test
    fun releaseGroupIds_parsesOnlyTheReleaseGroupsArray() {
        assertEquals(
            listOf(
                "11111111-1111-1111-1111-111111111111",
                "33333333-3333-3333-3333-333333333333",
            ),
            ArtistPhotos.releaseGroupIds(releaseGroupJson),
        )
    }

    @Test
    fun releaseGroupIds_toleratesMalformedOrEmptyJson() {
        assertTrue(ArtistPhotos.releaseGroupIds("").isEmpty())
        assertTrue(ArtistPhotos.releaseGroupIds("{ not json").isEmpty())
        assertTrue(ArtistPhotos.releaseGroupIds("{}").isEmpty())
    }

    @Test
    fun coverArtUrls_buildsFrontSizedUrlsAndDedupes() {
        assertEquals(
            listOf("https://coverartarchive.org/release-group/abc/front-500"),
            ArtistPhotos.coverArtUrls(listOf("abc", " abc ", "")),
        )
        assertEquals(
            listOf("https://coverartarchive.org/release-group/abc/front-250"),
            ArtistPhotos.coverArtUrls(listOf("abc"), size = 250),
        )
    }

    // ---- cloud source wiring -------------------------------------------

    private val enabled = DoradoSettings(cloudEnabled = true, cloudBaseUrl = "https://cloud.example/")

    @Test
    fun artistPhotoUrls_emptyWhenCloudDisabled() = runBlocking {
        val source = CloudMetadataSource({ DoradoSettings(cloudEnabled = false, cloudBaseUrl = "https://x/") })
        assertTrue(source.artistPhotoUrls("Massive Attack").isEmpty())
    }

    @Test
    fun artistPhotoUrls_searchesReleaseGroupsAndBuildsUrls() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """{"query":"Massive Attack","type":"release-group","total":1,"attribution":"MusicBrainz","items":[
                {"type":"release-group","mbid":"rg-1","title":"Mezzanine","artist":"Massive Attack","date":"1998","coverArtUrl":null}
            ]}""",
        )
        val source = CloudMetadataSource({ enabled }) { http }

        val urls = source.artistPhotoUrls("Massive Attack")

        assertEquals(listOf("https://cloud.example/v1/artwork/front/rg-1?size=500"), urls)
        assertEquals("v1/catalog/search?q=Massive%20Attack&type=release-group&limit=12", http.lastPath)
    }
}
