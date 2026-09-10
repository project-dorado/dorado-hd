package com.heretek.dorado_hd.analysis

import android.content.Context
import com.heretek.dorado_hd.data.model.Track

/**
 * DSP [FeatureAnalyzer]: decodes a PCM window and refines the metadata prior.
 * Falls back to the prior when the file cannot be decoded (or is unavailable).
 */
class PcmFeatureAnalyzer(private val context: Context) : FeatureAnalyzer {

    override suspend fun analyze(track: Track): AudioFeatures {
        val decoded = PcmDecoder.decodeWindow(context, track.uri)
        return if (decoded != null) {
            AudioFeatureExtractor.fromPcm(track, decoded.first, decoded.second)
        } else {
            AudioFeatureExtractor.extract(track)
        }
    }
}
