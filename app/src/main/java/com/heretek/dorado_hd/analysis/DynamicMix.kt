package com.heretek.dorado_hd.analysis

/**
 * A rune-style auto-updating "Mix": a rule that materializes a track list from
 * the library on demand. Mirrors the sibling desktop `DynamicMix`/`DynamicMixKind`
 * (Phase 13a).
 */
enum class DynamicMixKind {
    SIMILAR_TO_ALBUM,
    SIMILAR_TO_TRACK,
    TOP_PLAYED,
    SIMILAR_TO_FAVORITES,
    PLAYLISTS_INCLUDING_ARTIST,
}

data class DynamicMix(
    val name: String,
    val kind: DynamicMixKind = DynamicMixKind.TOP_PLAYED,
    val seedId: Long? = null,
    val seedText: String? = null,
    val trackLimit: Int = 50,
)
