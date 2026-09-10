package com.heretek.dorado_hd.ui.apps

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tiny PCM synth shared by the piano, drum machine, and metronome mini-apps.
 * One AudioTrack per app instance, kept running for the app's lifetime.
 * Sample rate 22050 keeps the buffer modest on low-end devices.
 */
class MiniSynth(val scope: CoroutineScope, val sampleRate: Int = 22050) {
    private val bufSize = sampleRate / 10
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(bufSize * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    fun playSamples(samples: DoubleArray) {
        val n = samples.size
        val short = ShortArray(n)
        for (i in 0 until n) {
            val v = (samples[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            short[i] = v.toShort()
        }
        track.write(short, 0, n)
    }

    fun stop() {
        try { track.stop() } catch (_: Exception) {}
        try { track.release() } catch (_: Exception) {}
    }

    fun start() {
        track.play()
    }
}

/** Sine note with a short decay envelope (mimics a soft tap). */
fun sineNote(freqHz: Double, durationMs: Int, sampleRate: Int = 22050): DoubleArray {
    val n = (durationMs * sampleRate / 1000)
    val out = DoubleArray(n)
    val decay = exp(-3.0 / (n.toDouble() / sampleRate))
    val w = 2 * PI * freqHz / sampleRate
    for (i in 0 until n) {
        val env = exp(-3.0 * i / n)
        out[i] = sin(i * w) * env * 0.4 * decay
    }
    return out
}

/** Sharp metronome tick: brief filtered noise burst + sine pip. */
fun metronomeClick(accent: Boolean, sampleRate: Int = 22050): DoubleArray {
    val durationMs = if (accent) 30 else 22
    val n = (durationMs * sampleRate / 1000)
    val out = DoubleArray(n)
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val dec = (1.0 - t)
        val noise = (Math.random() * 2.0 - 1.0) * 0.45
        val pip = sin(2.0 * PI * (if (accent) 1800.0 else 1200.0) * i / sampleRate) * 0.6
        out[i] = (noise * 0.6 + pip) * dec
    }
    return out
}

/** Cheap kick: quick frequency-swept sine with sharp decay. */
fun kickDrum(sampleRate: Int = 22050): DoubleArray {
    val n = (160 * sampleRate / 1000)
    val out = DoubleArray(n)
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val freq = 120.0 - 80.0 * t
        val s = sin(2 * PI * freq * i / sampleRate)
        out[i] = s * (1.0 - t) * 0.7
    }
    return out
}

/** Snare: filtered noise burst + body tone. */
fun snareDrum(sampleRate: Int = 22050): DoubleArray {
    val n = (180 * sampleRate / 1000)
    val out = DoubleArray(n)
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val noise = (Math.random() * 2.0 - 1.0) * 0.55
        val tone = sin(2 * PI * 220.0 * i / sampleRate) * 0.4
        out[i] = (noise + tone) * (1.0 - t) * 0.7
    }
    return out
}

/** Closed hi-hat: short noise burst. */
fun hatDrum(sampleRate: Int = 22050): DoubleArray {
    val n = (60 * sampleRate / 1000)
    val out = DoubleArray(n)
    for (i in 0 until n) {
        val t = i.toDouble() / n
        out[i] = (Math.random() * 2.0 - 1.0) * 0.5 * (1.0 - t)
    }
    return out
}

/** Run a metronome at BPM for `totalBeats` beats, accent on the first beat of each bar. */
fun metronomeLoop(
    synth: MiniSynth,
    bpm: Int,
    beatsPerBar: Int,
    totalBeats: Int,
    job: Job? = null,
): Job {
    return synth.scope.let { scope ->
        scope.launch(Dispatchers.IO) {
            val periodMs = (60_000.0 / bpm).toLong()
            for (beat in 0 until totalBeats) {
                if (!isActive) return@launch
                synth.playSamples(metronomeClick(accent = beat % beatsPerBar == 0))
                delay(periodMs)
            }
        }
    }
}
