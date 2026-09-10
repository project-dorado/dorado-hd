package com.heretek.dorado_hd.analysis

import com.heretek.dorado_hd.data.model.Track

/**
 * Derives a deterministic audio-feature prior from track metadata (genre, id),
 * 1:1 with the sibling desktop `AudioFeatureExtractor` (Phase 13a). This is a
 * replaceable metadata prior, not a DSP extractor: a future implementation can
 * compute the same [AudioFeatures] shape from decoded PCM without downstream
 * changes.
 */
object AudioFeatureExtractor {

    fun extract(track: Track): AudioFeatures {
        val genre = track.genre.lowercase()

        var energy = 0.5
        var valence = 0.5
        var acousticness = 0.3
        var danceability = 0.5
        var bpm = 120.0

        if (has(genre, "metal", "punk", "hardcore")) { energy += 0.3; valence -= 0.2; bpm = 140.0 }
        if (has(genre, "rock")) { energy += 0.15; valence += 0.05; bpm = 125.0 }
        if (has(genre, "electronic", "edm", "house", "techno", "trance")) { energy += 0.2; danceability += 0.3; valence += 0.1; bpm = 128.0 }
        if (has(genre, "dance", "disco", "funk")) { danceability += 0.3; valence += 0.2; bpm = 118.0 }
        if (has(genre, "hip", "rap")) { danceability += 0.2; energy += 0.1; bpm = 92.0 }
        if (has(genre, "pop")) { valence += 0.2; danceability += 0.15; bpm = 116.0 }
        if (has(genre, "jazz", "blues")) { acousticness += 0.3; valence += 0.05; bpm = 110.0 }
        if (has(genre, "classical", "orchestra", "opera")) { acousticness += 0.5; energy -= 0.2; bpm = 90.0 }
        if (has(genre, "folk", "acoustic", "country", "singer-songwriter")) { acousticness += 0.4; energy -= 0.1; bpm = 100.0 }
        if (has(genre, "ambient", "drone", "new age")) { energy -= 0.25; acousticness += 0.2; bpm = 80.0 }
        if (has(genre, "soul", "r&b", "rnb")) { valence += 0.15; danceability += 0.1; bpm = 102.0 }

        // Deterministic per-track jitter so identical-genre tracks differ.
        val jitter = (track.mediaId.hashCode() and 0xFF) / 255.0
        energy = clamp01(energy + (jitter - 0.5) * 0.12)
        valence = clamp01(valence + (jitter - 0.5) * 0.10)
        bpm = (bpm + (jitter - 0.5) * 12.0).coerceIn(60.0, 200.0)
        val spectralCentroid = clamp01(0.25 + (energy * 0.5) + (jitter - 0.5) * 0.1)

        return AudioFeatures(
            trackId = track.mediaId,
            bpm = bpm,
            energy = energy,
            valence = valence,
            acousticness = clamp01(acousticness),
            danceability = danceability,
            spectralCentroid = spectralCentroid,
        )
    }

    /**
     * Hybrid extractor (M9.3): refines the metadata prior with DSP descriptors
     * computed from a decoded PCM window — loudness/energy, spectral centroid
     * and zero-crossing rate. Valence/danceability remain from the prior, which
     * a real MIR model would replace. Deterministic for the same window.
     */
    fun fromPcm(track: Track, samples: FloatArray, sampleRate: Int): AudioFeatures {
        val prior = extract(track)
        if (samples.isEmpty() || sampleRate <= 0) return prior

        val rms = FeatureMath.rms(samples)
        val centroid = FeatureMath.spectralCentroidNormalized(samples, sampleRate)
        val zcr = FeatureMath.zeroCrossingRate(samples)

        val energy = clamp01(
            FeatureMath.loudness(rms) * 0.6 +
                prior.energy * 0.25 +
                (zcr * 2.0).coerceIn(0.0, 1.0) * 0.15,
        )
        val acousticness = clamp01(prior.acousticness * 0.6 + (1.0 - centroid) * 0.4)

        return prior.copy(
            energy = energy,
            spectralCentroid = centroid,
            acousticness = acousticness,
        )
    }

    private fun has(genre: String, vararg tokens: String): Boolean =
        tokens.any { genre.contains(it) }

    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)
}
