package com.heretek.dorado_hd.analysis

/**
 * Per-track audio feature vector, mirroring the sibling desktop
 * `Dorado.Domain.Models.AudioFeatures` (Phase 13a). Values are normalized 0..1
 * except [bpm]. Populated by [AudioFeatureExtractor]; the current extractor
 * derives a deterministic metadata prior and is designed to be replaced by a
 * DSP extractor behind the same shape.
 */
data class AudioFeatures(
    val trackId: Long,
    val bpm: Double,
    val energy: Double,
    val valence: Double,
    val acousticness: Double,
    val danceability: Double,
    val spectralCentroid: Double,
) {
    /** Six-dimensional normalized vector for cosine similarity. */
    fun toVector(): DoubleArray = doubleArrayOf(
        (bpm / 200.0).coerceIn(0.0, 1.0),
        energy,
        valence,
        acousticness,
        danceability,
        spectralCentroid,
    )
}
