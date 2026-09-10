package com.heretek.dorado_hd.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure DSP helpers for the M9.3 PCM extractor: a radix-2 FFT and the spectral
 * descriptors the feature vector needs. No Android dependencies, so every
 * function here is unit-tested directly.
 */
object FeatureMath {

    /** Root-mean-square amplitude of a [-1, 1] sample buffer. */
    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size)
    }

    /** Fraction of adjacent sample pairs that cross zero (0..1). */
    fun zeroCrossingRate(samples: FloatArray): Double {
        if (samples.size < 2) return 0.0
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] < 0f) != (samples[i] < 0f)) crossings++
        }
        return crossings.toDouble() / (samples.size - 1)
    }

    /**
     * Spectral centroid normalized to 0..1 (centroid Hz / Nyquist), using a
     * Hann-windowed FFT magnitude spectrum.
     */
    fun spectralCentroidNormalized(samples: FloatArray, sampleRate: Int): Double {
        if (samples.isEmpty() || sampleRate <= 0) return 0.0
        val magnitudes = fftMagnitudes(samples)
        val binHz = (sampleRate / 2.0) / magnitudes.size
        var weighted = 0.0
        var total = 0.0
        for (k in magnitudes.indices) {
            val freq = (k + 1) * binHz
            weighted += freq * magnitudes[k]
            total += magnitudes[k]
        }
        if (total <= 0.0) return 0.0
        val centroidHz = weighted / total
        return (centroidHz / (sampleRate / 2.0)).coerceIn(0.0, 1.0)
    }

    /** Dominant non-DC frequency in Hz (used by tests and future tempo work). */
    fun dominantFrequency(samples: FloatArray, sampleRate: Int): Double {
        if (samples.isEmpty() || sampleRate <= 0) return 0.0
        val magnitudes = fftMagnitudes(samples)
        var bestIndex = 0
        var best = -1.0
        for (k in magnitudes.indices) {
            if (magnitudes[k] > best) { best = magnitudes[k]; bestIndex = k }
        }
        val binHz = (sampleRate / 2.0) / magnitudes.size
        return (bestIndex + 1) * binHz
    }

    /**
     * Magnitude spectrum (first half) of a Hann-windowed signal, length rounded
     * up to the next power of two.
     */
    fun fftMagnitudes(samples: FloatArray): DoubleArray {
        val n = nextPowerOfTwo(samples.size)
        if (n == 0) return DoubleArray(0)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in samples.indices) re[i] = samples[i].toDouble() * hann(i, samples.size)
        fft(re, im)
        val half = n / 2
        val mags = DoubleArray(half)
        for (k in 0 until half) mags[k] = hypot(re[k], im[k])
        return mags
    }

    /** Perceptual "loudness" in dBFS mapped to 0..1 (silence → 0). */
    fun loudness(rms: Double): Double {
        if (rms <= 0.0) return 0.0
        val db = 20.0 * (ln(rms) / ln(10.0))
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0)
    }

    private fun hann(i: Int, size: Int): Double {
        if (size <= 1) return 1.0
        return 0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))
    }

    private fun nextPowerOfTwo(value: Int): Int {
        if (value <= 0) return 0
        var n = 1
        while (n < value) n = n shl 1
        return n
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle)
            val wLenIm = sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
