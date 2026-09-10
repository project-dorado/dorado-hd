package com.heretek.dorado_hd

import android.net.Uri
import com.heretek.dorado_hd.analysis.AudioAnalysisService
import com.heretek.dorado_hd.analysis.DynamicMix
import com.heretek.dorado_hd.analysis.DynamicMixKind
import com.heretek.dorado_hd.analysis.DynamicMixService
import com.heretek.dorado_hd.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicMixTest {

    private fun track(
        id: Long,
        artistId: Long,
        genre: String,
        title: String = "T$id",
        albumId: Long = artistId,
        artist: String = "Artist $artistId",
    ) = Track(
        mediaId = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = "Album $albumId",
        albumId = albumId,
        genre = genre,
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "",
        uri = Uri.parse("file:///t$id"),
    )

    private fun service() = DynamicMixService(AudioAnalysisService())

    @Test
    fun `top played orders by count then title, and degrades to title order`() {
        val a = track(1, 10, "rock", title = "bravo")
        val b = track(2, 11, "rock", title = "alpha")
        val c = track(3, 12, "rock", title = "charlie")
        val lib = listOf(a, b, c)

        val ranked = service().materialize(
            DynamicMix("top", DynamicMixKind.TOP_PLAYED, trackLimit = 3),
            lib,
            playCounts = mapOf(1L to 5, 2L to 9, 3L to 0),
        )
        assertEquals(listOf(2L, 1L, 3L), ranked.map { it.mediaId })

        val byTitle = service().materialize(
            DynamicMix("top", DynamicMixKind.TOP_PLAYED, trackLimit = 3),
            lib,
        )
        assertEquals(listOf("alpha", "bravo", "charlie"), byTitle.map { it.title })
    }

    @Test
    fun `similar to track ranks comparable tracks and excludes the seed`() {
        val seed = track(1, 10, "rock")
        val rock = track(2, 11, "rock")
        val classical = track(3, 12, "classical")
        val lib = listOf(seed, rock, classical)

        val mix = service().materialize(
            DynamicMix("mix", DynamicMixKind.SIMILAR_TO_TRACK, seedId = 1, trackLimit = 3),
            lib,
        )
        assertEquals(rock.mediaId, mix.first().mediaId)
        assertTrue("seed excluded", mix.none { it.mediaId == seed.mediaId })
    }

    @Test
    fun `similar to album uses a sibling track as representative`() {
        val seed = track(1, 10, "rock", albumId = 50)
        val sibling = track(2, 10, "rock", albumId = 50)
        val other = track(3, 11, "classical", albumId = 60)
        val lib = listOf(seed, sibling, other)

        val mix = service().materialize(
            DynamicMix("album mix", DynamicMixKind.SIMILAR_TO_ALBUM, seedId = 1, trackLimit = 3),
            lib,
        )
        assertTrue("mix should be non-empty", mix.isNotEmpty())
        assertTrue("mix capped at limit", mix.size <= 3)
    }

    @Test
    fun `similar to favorites delegates and excludes favorites`() {
        val favA = track(1, 10, "rock")
        val favB = track(2, 11, "rock")
        val candidate = track(3, 12, "rock")
        val classical = track(4, 13, "classical")
        val lib = listOf(favA, favB, candidate, classical)

        val mix = service().materialize(
            DynamicMix("fav mix", DynamicMixKind.SIMILAR_TO_FAVORITES, trackLimit = 2),
            lib,
            favorites = listOf(favA, favB),
        )
        assertEquals(candidate.mediaId, mix.first().mediaId)
        assertTrue(mix.none { it.mediaId == favA.mediaId || it.mediaId == favB.mediaId })
    }

    @Test
    fun `artist mix filters by artist and needs seed text`() {
        val lib = listOf(
            track(1, 10, "rock", artist = "The Cure"),
            track(2, 11, "rock", artist = "The Cure"),
            track(3, 12, "classical", artist = "Bach"),
        )
        val mix = service().materialize(
            DynamicMix("artist mix", DynamicMixKind.PLAYLISTS_INCLUDING_ARTIST, seedText = "cure", trackLimit = 10),
            lib,
        )
        assertEquals(listOf(1L, 2L), mix.map { it.mediaId })

        assertTrue(
            service().materialize(
                DynamicMix("artist mix", DynamicMixKind.PLAYLISTS_INCLUDING_ARTIST, seedText = "  "),
                lib,
            ).isEmpty(),
        )
    }

    @Test
    fun `empty library yields no mix and defaults include favorites`() {
        assertTrue(
            service().materialize(DynamicMix("x", DynamicMixKind.TOP_PLAYED), emptyList()).isEmpty(),
        )
        assertTrue(service().buildDefaultMixes().any { it.kind == DynamicMixKind.SIMILAR_TO_FAVORITES })
    }
}
