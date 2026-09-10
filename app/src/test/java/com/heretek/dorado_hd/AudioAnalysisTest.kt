package com.heretek.dorado_hd

import android.net.Uri
import com.heretek.dorado_hd.analysis.AudioAnalysisService
import com.heretek.dorado_hd.analysis.AudioFeatureExtractor
import com.heretek.dorado_hd.data.model.Artist
import com.heretek.dorado_hd.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioAnalysisTest {

    private fun track(id: Long, artistId: Long, genre: String, title: String = "T$id") = Track(
        mediaId = id,
        title = title,
        artist = "Artist $artistId",
        artistId = artistId,
        album = "Album",
        albumId = artistId,
        genre = genre,
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "",
        uri = Uri.parse("file:///t$id"),
    )

    private fun artist(id: Long) = Artist(id, "Artist $id", albumCount = 1, trackCount = 1)

    @Test
    fun `extractor is deterministic and id-sensitive`() {
        val a = track(1, 10, "rock")
        val b = track(1, 10, "rock")
        assertEquals(AudioFeatureExtractor.extract(a), AudioFeatureExtractor.extract(b))

        val different = track(2, 10, "rock")
        assertFalse(AudioFeatureExtractor.extract(a) == AudioFeatureExtractor.extract(different))
    }

    @Test
    fun `genre shifts the feature vector`() {
        val metal = AudioFeatureExtractor.extract(track(1, 1, "metal"))
        val classical = AudioFeatureExtractor.extract(track(2, 2, "classical"))
        assertTrue("metal should be more energetic", metal.energy > classical.energy)
        assertTrue("classical should be more acoustic", classical.acousticness > metal.acousticness)
        assertTrue("metal tempo should be higher", metal.bpm > classical.bpm)
    }

    @Test
    fun `cosine similarity behaves`() {
        val v = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
        assertEquals(1.0, AudioAnalysisService.cosine(v, v), 1e-9)
        assertEquals(0.0, AudioAnalysisService.cosine(doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), v), 1e-9)
    }

    @Test
    fun `findSimilar ranks the same-genre track first`() {
        val service = AudioAnalysisService()
        val seed = track(1, 10, "rock")
        val rock = track(2, 11, "rock")
        val classical = track(3, 12, "classical")
        val electronic = track(4, 13, "electronic")

        val ranked = service.findSimilar(seed, listOf(seed, rock, classical, electronic), count = 3)
        assertEquals(rock.mediaId, ranked.first().mediaId)
        assertFalse("seed must be excluded", ranked.any { it.mediaId == seed.mediaId })
    }

    @Test
    fun `findSimilarToFavorites uses the centroid and excludes favorites`() {
        val service = AudioAnalysisService()
        val favA = track(1, 10, "rock")
        val favB = track(2, 11, "rock")
        val candidateRock = track(3, 12, "rock")
        val candidateClassical = track(4, 13, "classical")

        val ranked = service.findSimilarToFavorites(
            listOf(favA, favB),
            listOf(favA, favB, candidateRock, candidateClassical),
            count = 2,
        )
        assertEquals(candidateRock.mediaId, ranked.first().mediaId)
        assertTrue(ranked.none { it.mediaId == favA.mediaId || it.mediaId == favB.mediaId })
    }

    @Test
    fun `findSimilarToFavorites with no favorites falls back deterministically`() {
        val service = AudioAnalysisService()
        val b = track(1, 10, "rock", title = "bravo")
        val a = track(2, 11, "rock", title = "alpha")
        val ranked = service.findSimilarToFavorites(emptyList(), listOf(b, a), count = 2)
        assertEquals(listOf("alpha", "bravo"), ranked.map { it.title })
    }

    @Test
    fun `relatedArtists excludes the seed and ranks comparable catalogs first`() {
        val service = AudioAnalysisService()
        val library = listOf(
            track(1, artistId = 10, genre = "rock"),
            track(2, artistId = 10, genre = "rock"),
            track(3, artistId = 11, genre = "rock"),
            track(4, artistId = 12, genre = "classical"),
            track(5, artistId = 13, genre = "electronic"),
        )
        val artists = listOf(artist(10), artist(11), artist(12), artist(13))

        val related = service.relatedArtists(seedArtistId = 10, artists = artists, library = library, count = 3)
        assertEquals("comparable rock catalog should rank first", 11L, related.first().artistId)
        assertTrue("seed artist must be excluded", related.none { it.artistId == 10L })

        // No seed tracks -> empty (screen then falls back to genre overlap).
        assertTrue(service.relatedArtists(99, artists, library).isEmpty())
    }
}
