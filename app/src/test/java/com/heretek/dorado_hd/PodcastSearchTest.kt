package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudPodcastResult
import com.heretek.dorado_hd.data.db.PodcastEpisodeFlat
import com.heretek.dorado_hd.data.db.PodcastFeedEntity
import com.heretek.dorado_hd.ui.screens.PodcastSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure rules behind the podcast pivot's M5 search: the local filter over the
 * subscribed library and the cloud-directory merge.
 */
class PodcastSearchTest {

    private fun episode(
        id: Long,
        title: String,
        show: String,
        publishedAt: Long = id,
    ) = PodcastEpisodeFlat(
        episodeId = id,
        feedId = 1,
        title = title,
        pubAt = publishedAt,
        durationMs = 60_000,
        enclosureUrl = "https://example.com/$id.mp3",
        played = false,
        positionMs = 0,
        feedTitle = show,
    )

    private val library = listOf(
        episode(3, "The Future of C#", "Dotnet Rocks"),
        episode(2, "Coffee and Code", "Dotnet Rocks"),
        episode(1, "Zune Deep Dive", "Retro Tech"),
    )

    @Test
    fun filterLocal_matchesEpisodeTitleCaseInsensitively() {
        val hits = PodcastSearch.filterLocal(library, "c#")
        assertEquals(listOf(3L), hits.map { it.episodeId })
    }

    @Test
    fun filterLocal_matchesShowTitle() {
        val hits = PodcastSearch.filterLocal(library, "dotnet rocks")
        assertEquals(listOf(3L, 2L), hits.map { it.episodeId })
    }

    @Test
    fun filterLocal_blankQueryReturnsNothing() {
        assertTrue(PodcastSearch.filterLocal(library, "   ").isEmpty())
    }

    @Test
    fun filterLocal_preservesNewestFirstAndCaps() {
        val many = (1L..60L).map { episode(it, "episode $it", "show") }
        val hits = PodcastSearch.filterLocal(many, "episode", limit = 10)
        assertEquals(10, hits.size)
        assertEquals((1L..10L).toList(), hits.map { it.episodeId })
    }

    @Test
    fun filterLocal_dedupesRepeatedEpisodeIds() {
        val duplicated = library + library
        assertEquals(2, PodcastSearch.filterLocal(duplicated, "dotnet rocks").size)
    }

    @Test
    fun normalizeFeedUrl_trimsCaseAndTrailingSlash() {
        assertEquals(
            "https://feeds.example.com/show.xml",
            PodcastSearch.normalizeFeedUrl("  HTTPS://Feeds.Example.com/show.xml/ "),
        )
    }

    @Test
    fun subscribedFeedUrls_normalizesAndDropsBlanks() {
        val feeds = listOf(
            PodcastFeedEntity(id = 1, title = "A", feedUrl = "https://a.example/feed.xml", artworkUrl = "", description = "", subscribedAt = 0),
            PodcastFeedEntity(id = 2, title = "B", feedUrl = "HTTPS://A.Example/feed.xml/", artworkUrl = "", description = "", subscribedAt = 0),
            PodcastFeedEntity(id = 3, title = "C", feedUrl = "   ", artworkUrl = "", description = "", subscribedAt = 0),
        )
        assertEquals(setOf("https://a.example/feed.xml"), PodcastSearch.subscribedFeedUrls(feeds))
    }

    @Test
    fun markSubscribed_flagsKnownFeedsOnly() {
        val results = listOf(
            CloudPodcastResult("1", "Known", "Me", "", "", "https://a.example/feed.xml", "", "en"),
            CloudPodcastResult("2", "New", "You", "", "", "https://b.example/feed.xml", "", "en"),
        )
        val marked = PodcastSearch.markSubscribed(
            results,
            PodcastSearch.subscribedFeedUrls(
                listOf(
                    PodcastFeedEntity(id = 1, title = "A", feedUrl = "https://a.example/feed.xml/", artworkUrl = "", description = "", subscribedAt = 0),
                ),
            ),
        )
        assertTrue(marked[0].subscribed)
        assertFalse(marked[1].subscribed)
        assertEquals("New", marked[1].feed.title)
    }
}
