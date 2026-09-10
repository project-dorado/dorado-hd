package com.heretek.dorado_hd

import android.net.Uri
import com.heretek.dorado_hd.analysis.AudioAnalysisService
import com.heretek.dorado_hd.analysis.CloudMixService
import com.heretek.dorado_hd.analysis.DynamicMix
import com.heretek.dorado_hd.analysis.DynamicMixKind
import com.heretek.dorado_hd.analysis.DynamicMixService
import com.heretek.dorado_hd.cloud.MixSuggester
import com.heretek.dorado_hd.data.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the cloud-assisted DynamicMix: the on-device result stays
 * authoritative, and cloud QuickMix artist recommendations enrich it
 * (deduped, capped, deterministic) only when the cloud is enabled.
 */
@RunWith(RobolectricTestRunner::class)
class CloudMixServiceTest {

    @Test
    fun cloudDisabled_returnsLocalOnly() = runBlocking {
        val service = CloudMixService(DynamicMixService(AudioAnalysisService()), cloud = null)

        val mix = service.materialize(mix(limit = 50), library())

        assertEquals(listOf("Seed A", "Seed B"), mix.map { it.title })
    }

    @Test
    fun cloudAugmentsWithRecommendedArtists() = runBlocking {
        val service = CloudMixService(DynamicMixService(AudioAnalysisService()), FakeMixSuggester(true, listOf("Other")))

        val mix = service.materialize(mix(limit = 50), library())

        assertEquals(listOf("Seed A", "Seed B", "Other A", "Other B"), mix.map { it.title })
    }

    @Test
    fun cloudRespectsTrackLimit() = runBlocking {
        val service = CloudMixService(DynamicMixService(AudioAnalysisService()), FakeMixSuggester(true, listOf("Other")))

        val mix = service.materialize(mix(limit = 3), library())

        assertEquals(3, mix.size)
        assertEquals(listOf("Seed A", "Seed B", "Other A"), mix.map { it.title })
    }

    @Test
    fun cloudDeduplicatesAgainstLocal() = runBlocking {
        // The suggester recommends the seed's own artist, which is already present.
        val service = CloudMixService(DynamicMixService(AudioAnalysisService()), FakeMixSuggester(true, listOf("Seed")))

        val mix = service.materialize(mix(limit = 50), library())

        assertEquals(listOf("Seed A", "Seed B"), mix.map { it.title })
    }

    @Test
    fun cloudRecommendingUnknownArtistsChangesNothing() = runBlocking {
        val service = CloudMixService(DynamicMixService(AudioAnalysisService()), FakeMixSuggester(true, listOf("Nobody")))

        val mix = service.materialize(mix(limit = 50), library())

        assertEquals(listOf("Seed A", "Seed B"), mix.map { it.title })
        assertTrue(mix.all { it.artist == "Seed" })
    }

    private fun mix(limit: Int) = DynamicMix(
        name = "mix: Seed A",
        kind = DynamicMixKind.PLAYLISTS_INCLUDING_ARTIST,
        seedText = "Seed",
        trackLimit = limit,
    )

    private fun library(): List<Track> = listOf(
        track(1, "Seed A", "Seed"),
        track(2, "Seed B", "Seed"),
        track(3, "Other B", "Other"),
        track(4, "Other A", "Other"),
        track(5, "Third A", "Third"),
    )

    private fun track(id: Long, title: String, artist: String) = Track(
        mediaId = id,
        title = title,
        artist = artist,
        artistId = id,
        album = "Album",
        albumId = 0,
        genre = "",
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "",
        uri = Uri.parse("file:///t$id"),
    )
}

private class FakeMixSuggester(
    private val enabled: Boolean,
    private val artists: List<String>,
) : MixSuggester {
    override fun isEnabled(): Boolean = enabled
    override suspend fun similarArtists(seed: String, limit: Int): List<String> = artists
}
