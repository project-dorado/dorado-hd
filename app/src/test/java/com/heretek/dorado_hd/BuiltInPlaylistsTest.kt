package com.heretek.dorado_hd

import android.net.Uri
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.data.repo.BuiltInPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Track C — derivable built-in playlists (`BuiltIn-*`). */
@RunWith(RobolectricTestRunner::class)
class BuiltInPlaylistsTest {

    private fun track(id: Long, title: String, artist: String, artistId: Long) = Track(
        mediaId = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = "album",
        albumId = 1L,
        genre = "genre",
        durationMs = 1_000L,
        dateAdded = 0L,
        trackNumber = 0,
        year = "",
        uri = Uri.parse("file:///track/$id"),
    )

    private val library = listOf(
        track(1, "alpha", "A", 10),
        track(2, "beta", "A", 10),
        track(3, "gamma", "B", 20),
        track(4, "delta", "C", 30),
    )

    @Test
    fun `favorite tracks are the hearts sorted by title, limited`() {
        val ratings = mapOf(1L to Rating.HEART.value, 3L to Rating.HEART.value, 2L to Rating.BROKEN.value)

        val favorites = BuiltInPlaylists.favoriteTracks(library, ratings)

        assertEquals(listOf(1L, 3L), favorites.map { it.mediaId })
        assertEquals(listOf(1L), BuiltInPlaylists.favoriteTracks(library, ratings, limit = 1).map { it.mediaId })
    }

    @Test
    fun `most played artists ranks by summed plays then track plays`() {
        // A = 5+3, B = 10, C = 0 (excluded).
        val plays = mapOf(1L to 5, 2L to 3, 3L to 10, 4L to 0)

        val ordered = BuiltInPlaylists.mostPlayedArtists(library, plays)

        // Artist B (10) outranks A (8); A's tracks follow by play count.
        assertEquals(listOf(3L, 1L, 2L), ordered.map { it.mediaId })
        assertTrue("unplayed artists are excluded", ordered.none { it.mediaId == 4L })
    }

    @Test
    fun `most played artists honors artist and track limits`() {
        val plays = mapOf(1L to 5, 2L to 3, 3L to 10, 4L to 0)

        assertEquals(
            listOf(3L),
            BuiltInPlaylists.mostPlayedArtists(library, plays, artistLimit = 1).map { it.mediaId },
        )
        assertEquals(
            listOf(3L, 1L),
            BuiltInPlaylists.mostPlayedArtists(library, plays, trackLimit = 2).map { it.mediaId },
        )
    }

    @Test
    fun `recent tracks dedupe, skip unknown ids and stay newest first`() {
        val recentIds = listOf(99L, 2L, 2L, 1L, 5L)

        val recent = BuiltInPlaylists.recentTracks(library, recentIds)

        assertEquals(listOf(2L, 1L), recent.map { it.mediaId })
        assertEquals(listOf(2L), BuiltInPlaylists.recentTracks(library, recentIds, limit = 1).map { it.mediaId })
    }

    @Test
    fun `derive omits empty built-ins and keeps device order`() {
        val ratings = mapOf(1L to Rating.HEART.value)
        val plays = mapOf(3L to 4)
        val recent = listOf(2L)

        val derived = BuiltInPlaylists.derive(library, ratings, plays, recent)

        assertEquals(
            listOf(
                BuiltInPlaylists.FAVORITE_TRACKS_ID,
                BuiltInPlaylists.MOST_PLAYED_ARTISTS_ID,
                BuiltInPlaylists.RECENT_TRACKS_ID,
            ),
            derived.map { it.id },
        )
        assertTrue(derived.all { it.tracks.isNotEmpty() })

        assertTrue(BuiltInPlaylists.derive(library, emptyMap(), emptyMap(), emptyList()).isEmpty())
    }
}
