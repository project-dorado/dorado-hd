package com.heretek.dorado_hd.analysis

import com.heretek.dorado_hd.data.model.Track

/** Persistence boundary for analyzed [AudioFeatures] (Room-backed in the app). */
interface FeatureStore {
    suspend fun load(): List<AudioFeatures>
    suspend fun save(features: AudioFeatures)
}

/**
 * Computes features for a track. The app supplies a PCM/DSP analyzer;
 * the default is the pure metadata prior, which keeps the service testable.
 */
fun interface FeatureAnalyzer {
    suspend fun analyze(track: Track): AudioFeatures
}
