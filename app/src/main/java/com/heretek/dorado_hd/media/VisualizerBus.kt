package com.heretek.dorado_hd.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.heretek.dorado_hd.analysis.FeatureMath
import com.heretek.dorado_hd.analysis.SpectrumBands
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Live PCM tap for the Now Playing visualizer (M14/C5). The playback
 * `AudioProcessor` chain copies every rendered buffer into a mono ring buffer;
 * the UI polls [snapshot] per frame and shapes the FFT with [SpectrumBands].
 *
 * The tap is passive and permission-free (no `RECORD_AUDIO`): it reads the
 * samples already destined for the audio sink.
 */
@OptIn(UnstableApi::class)
object VisualizerBus {

    private const val WINDOW = 2048

    private val lock = Any()
    private var ring = FloatArray(WINDOW)
    private var writeIndex = 0
    private var filled = 0
    private var sampleRate = 44_100
    private var channels = 2
    private var floatOutput = false
    private var smoothed: FloatArray? = null

    /** The audio processor that mirrors rendered PCM into the ring buffer. */
    fun pcmSink(): TeeAudioProcessor.AudioBufferSink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            synchronized(lock) {
                sampleRate = sampleRateHz.coerceAtLeast(8_000)
                channels = channelCount.coerceAtLeast(1)
                floatOutput = encoding == C.ENCODING_PCM_FLOAT
                writeIndex = 0
                filled = 0
            }
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val copy = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            synchronized(lock) {
                if (floatOutput) drainFloat(copy) else drainShort(copy)
            }
        }
    }

    private fun drainShort(buffer: ByteBuffer) {
        while (buffer.remaining() >= 2 * channels) {
            var sum = 0f
            for (c in 0 until channels) sum += buffer.short.toFloat() / 32768f
            push(sum / channels)
        }
    }

    private fun drainFloat(buffer: ByteBuffer) {
        while (buffer.remaining() >= 4 * channels) {
            var sum = 0f
            for (c in 0 until channels) sum += buffer.float
            push(sum / channels)
        }
    }

    private fun push(sample: Float) {
        ring[writeIndex] = sample
        writeIndex = (writeIndex + 1) % WINDOW
        if (filled < WINDOW) filled++
    }

    /** Configured sample rate of the last flush (for tests/diagnostics). */
    fun sampleRateHz(): Int = sampleRate

    /**
     * Latest spectrum as [barCount] bars in 0..1, smoothed frame to frame, or
     * null while the ring has not filled yet (start of playback).
     */
    fun snapshot(barCount: Int): FloatArray? {
        val window = FloatArray(WINDOW)
        synchronized(lock) {
            if (filled < WINDOW) return null
            val start = writeIndex
            for (i in 0 until WINDOW) {
                window[i] = ring[(start + i) % WINDOW]
            }
        }
        val bands = SpectrumBands.fromMagnitudes(FeatureMath.fftMagnitudes(window), barCount)
        smoothed = SpectrumBands.smooth(smoothed, bands)
        return smoothed?.copyOf()
    }

    /** Clears history on pause so the next start does not replay old bars. */
    fun reset() {
        synchronized(lock) {
            writeIndex = 0
            filled = 0
        }
        smoothed = null
    }
}
