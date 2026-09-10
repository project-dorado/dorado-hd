package com.heretek.dorado_hd.analysis

import com.heretek.dorado_hd.data.model.Track

/**
 * Materializes rune-style auto-updating Mixes from the current library,
 * mirroring the sibling desktop `DynamicMixService` (Phase 13a). Pure JVM.
 *
 * `TOP_PLAYED` reads an injected play-count map; until play counts are
 * persisted (M9.2b) it degrades to title order.
 */
class DynamicMixService(private val analysis: AudioAnalysisService) {

    fun buildDefaultMixes(): List<DynamicMix> = listOf(
        DynamicMix(name = "Favorites Mix", kind = DynamicMixKind.SIMILAR_TO_FAVORITES, trackLimit = 50),
    )

    fun materialize(
        mix: DynamicMix,
        library: List<Track>,
        favorites: List<Track> = emptyList(),
        playCounts: Map<Long, Int> = emptyMap(),
    ): List<Track> {
        if (library.isEmpty()) return emptyList()

        return when (mix.kind) {
            DynamicMixKind.TOP_PLAYED ->
                library
                    .sortedWith(
                        compareByDescending<Track> { playCounts[it.mediaId] ?: 0 }
                            .thenBy { it.title.lowercase() },
                    )
                    .take(mix.trackLimit)

            DynamicMixKind.SIMILAR_TO_FAVORITES ->
                analysis.findSimilarToFavorites(favorites, library, mix.trackLimit)

            DynamicMixKind.SIMILAR_TO_TRACK -> {
                val seed = mix.seedId?.let { id -> library.firstOrNull { it.mediaId == id } }
                    ?: return emptyList()
                analysis.findSimilar(seed, library, mix.trackLimit)
            }

            DynamicMixKind.SIMILAR_TO_ALBUM -> {
                val seed = mix.seedId?.let { id -> library.firstOrNull { it.mediaId == id } }
                    ?: return emptyList()
                // Use a *different* track from the seed's album as the representative.
                val representative = library
                    .firstOrNull { it.albumId == seed.albumId && it.mediaId != seed.mediaId }
                    ?: seed
                analysis.findSimilar(representative, library, mix.trackLimit)
            }

            DynamicMixKind.PLAYLISTS_INCLUDING_ARTIST -> {
                val text = mix.seedText?.takeIf { it.isNotBlank() } ?: return emptyList()
                library
                    .filter { it.artist.contains(text, ignoreCase = true) }
                    .sortedWith(
                        compareByDescending<Track> { playCounts[it.mediaId] ?: 0 }
                            .thenBy { it.title.lowercase() },
                    )
                    .take(mix.trackLimit)
            }
        }
    }
}
