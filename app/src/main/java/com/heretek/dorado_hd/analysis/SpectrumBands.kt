package com.heretek.dorado_hd.analysis

import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure spectrum shaping for the live visualizer (M14/C5): FFT magnitudes to
 * log-spaced bars with perceptual scaling and exponential decay. Kept free of
 * Android so the math is unit-testable.
 */
object SpectrumBands {

    /**
     * Map [magnitudes] (linear FFT bins, first half only) to [count] log-spaced
     * bands normalised to 0..1. Bands are averages of their bin range, scaled
     * with sqrt so quiet material still moves the bars; the frame's loudest
     * band drives the normalisation (silence yields all zeros).
     */
    fun fromMagnitudes(magnitudes: DoubleArray, count: Int): FloatArray {
        require(count > 0) { "count must be positive" }
        val out = FloatArray(count)
        val bins = magnitudes.size
        if (bins < 2) return out
        // Log-spaced edges from bin 1 (skip DC) to the last bin.
        val minLog = ln1p(1.0)
        val maxLog = ln1p((bins - 1).toDouble())
        var peak = 0.0
        for (band in 0 until count) {
            val lo = if (count == 1) 1.0 else minLog + (maxLog - minLog) * band / count
            val hi = if (count == 1) maxLog else minLog + (maxLog - minLog) * (band + 1) / count
            val start = kotlin.math.expm1(lo).toInt().coerceIn(1, bins - 1)
            val end = kotlin.math.expm1(hi).toInt().coerceIn(start + 1, bins)
            var sum = 0.0
            for (bin in start until end) sum += magnitudes[bin]
            val avg = sum / (end - start)
            out[band] = avg.toFloat()
            if (avg > peak) peak = avg
        }
        if (peak <= 1e-9) return out
        for (i in out.indices) {
            out[i] = sqrt((out[i] / peak).toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return out
    }

    /**
     * Per-band decay: the bar jumps to [target] when it rises and falls by
     * [decay] per frame otherwise (classic peak-hold feel).
     */
    fun smooth(previous: FloatArray?, target: FloatArray, decay: Float = 0.82f): FloatArray {
        if (previous == null || previous.size != target.size) return target.copyOf()
        val out = FloatArray(target.size)
        for (i in target.indices) {
            val floor = previous[i] * decay
            out[i] = max(target[i], floor).coerceIn(0f, 1f)
        }
        return out
    }
}
