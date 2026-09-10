package com.heretek.dorado_hd.analysis

import com.heretek.dorado_hd.cloud.MixSuggester
import com.heretek.dorado_hd.data.model.Track

/**
 * Materializes DynamicMixes with an optional cloud assist: the on-device
 * similarity result is augmented by artist names recommended by the Dorado
 * Cloud QuickMix endpoint, matched against the local library. Ordering is
 * deterministic (local tracks first, then cloud matches by title) so mixes are
 * stable and testable; the local result is always authoritative and complete on
 * its own, so the cloud being offline never changes the contract.
 */
class CloudMixService(
    private val local: DynamicMixService,
    private val cloud: MixSuggester? = null,
) {
    suspend fun materialize(
        mix: DynamicMix,
        library: List<Track>,
        favorites: List<Track> = emptyList(),
        playCounts: Map<Long, Int> = emptyMap(),
    ): List<Track> {
        val localResult = local.materialize(mix, library, favorites, playCounts)
        if (library.isEmpty() || cloud?.isEnabled() != true) {
            return localResult
        }

        val seed = seedArtist(mix, library, favorites) ?: return localResult
        val recommended = cloud.similarArtists(seed, mix.trackLimit)
        if (recommended.isEmpty()) {
            return localResult
        }

        val names = recommended.map { it.lowercase() }.toSet()
        val seen = localResult.map { it.mediaId }.toSet()
        val remaining = (mix.trackLimit - localResult.size).coerceAtLeast(0)
        if (remaining == 0) {
            return localResult
        }

        val cloudTracks = library
            .filter { it.artist.lowercase() in names && it.mediaId !in seen }
            .sortedBy { it.title.lowercase() }
            .take(remaining)

        return localResult + cloudTracks
    }

    private fun seedArtist(mix: DynamicMix, library: List<Track>, favorites: List<Track>): String? = when (mix.kind) {
        DynamicMixKind.SIMILAR_TO_TRACK, DynamicMixKind.SIMILAR_TO_ALBUM ->
            mix.seedId?.let { id -> library.firstOrNull { it.mediaId == id }?.artist }
        DynamicMixKind.SIMILAR_TO_FAVORITES -> favorites.firstOrNull()?.artist
        DynamicMixKind.PLAYLISTS_INCLUDING_ARTIST -> mix.seedText?.takeIf { it.isNotBlank() }
        DynamicMixKind.TOP_PLAYED -> null
    }?.takeIf { it.isNotBlank() }
}
