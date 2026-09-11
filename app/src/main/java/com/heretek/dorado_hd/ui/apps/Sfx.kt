package com.heretek.dorado_hd.ui.apps

import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Deterministic white noise (LCG) so synthesized game SFX are reproducible in
 * tests and identical for every player. The generator functions in `Synth.kt`
 * predate this and use `Math.random()`; new apps should prefer [Noise].
 */
class Noise(seed: Int = 0x51ED270B.toInt()) {
    private var state: Int = seed

    fun next(): Double {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7FFF) / 16383.5 - 1.0
    }
}

private fun envelope(t: Double, decay: Double): Double = exp(-decay * t)

/** Short sine blip; the workhorse UI/game cue. */
fun sfxTone(
    freqHz: Double,
    durationMs: Int,
    sampleRate: Int = 22050,
    decay: Double = 5.0,
    gain: Double = 0.35,
): DoubleArray {
    val n = (durationMs * sampleRate / 1000).coerceAtLeast(1)
    val out = DoubleArray(n)
    val w = 2 * PI * freqHz / sampleRate
    for (i in 0 until n) {
        val t = i.toDouble() / n
        out[i] = sin(i * w) * envelope(t, decay) * gain
    }
    return out
}

/** Pitch sweep (jump, whoosh, power-up). */
fun sfxSweep(
    fromHz: Double,
    toHz: Double,
    durationMs: Int,
    sampleRate: Int = 22050,
    gain: Double = 0.35,
    decay: Double = 2.5,
): DoubleArray {
    val n = (durationMs * sampleRate / 1000).coerceAtLeast(1)
    val out = DoubleArray(n)
    var phase = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val freq = fromHz + (toHz - fromHz) * t
        phase += 2 * PI * freq / sampleRate
        out[i] = sin(phase) * envelope(t, decay) * gain
    }
    return out
}

/** Noise burst with a low-pass shelf (hit, explosion, splat). */
fun sfxNoise(
    durationMs: Int,
    sampleRate: Int = 22050,
    gain: Double = 0.4,
    decay: Double = 4.0,
    seed: Int = 1,
    lowPass: Double = 0.5,
): DoubleArray {
    val n = (durationMs * sampleRate / 1000).coerceAtLeast(1)
    val out = DoubleArray(n)
    val rng = Noise(seed)
    var prev = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / n
        val raw = rng.next()
        prev += lowPass * (raw - prev)
        out[i] = prev * envelope(t, decay) * gain
    }
    return out
}

/** Two-note cue (success / selection confirm). */
fun sfxArpeggio(
    freqs: List<Double>,
    noteMs: Int = 90,
    sampleRate: Int = 22050,
    gain: Double = 0.3,
): DoubleArray {
    val parts = freqs.map { f -> sfxTone(f, noteMs, sampleRate, decay = 3.0, gain = gain) }
    val total = parts.sumOf { it.size }
    val out = DoubleArray(total)
    var pos = 0
    for (p in parts) {
        p.copyInto(out, pos)
        pos += p.size
    }
    return out
}

/** Every cue the [SfxBank] can synthesize. */
val GAME_SFX_NAMES: List<String> = listOf(
    "select", "back", "tick", "click", "score", "coin", "win", "lose",
    "hit", "explode", "pop", "jump", "whoosh", "rotate", "error", "toss",
)

/**
 * Named, cached SFX bank for the official-app games. Sounds are synthesized
 * on first use; nothing is loaded from disk and no sampled audio ships.
 */
class SfxBank(private val synth: MiniSynth, val sampleRate: Int = 22050) {
    private val cache = mutableMapOf<String, DoubleArray>()

    fun samples(name: String): DoubleArray = cache.getOrPut(name) {
        when (name) {
            "select" -> sfxTone(880.0, 60, sampleRate, decay = 6.0, gain = 0.3)
            "back" -> sfxTone(440.0, 70, sampleRate, decay = 5.0, gain = 0.3)
            "tick" -> sfxNoise(24, sampleRate, gain = 0.32, decay = 8.0, seed = 11, lowPass = 0.9)
            "click" -> sfxNoise(18, sampleRate, gain = 0.28, decay = 9.0, seed = 3, lowPass = 0.95)
            "score" -> sfxArpeggio(listOf(660.0, 990.0), sampleRate = sampleRate)
            "coin" -> sfxArpeggio(listOf(988.0, 1319.0), noteMs = 55, sampleRate = sampleRate)
            "win" -> sfxArpeggio(listOf(523.0, 659.0, 784.0, 1047.0), noteMs = 110, sampleRate = sampleRate)
            "lose" -> sfxSweep(392.0, 130.0, 380, sampleRate, gain = 0.32, decay = 1.4)
            "hit" -> sfxNoise(90, sampleRate, gain = 0.45, decay = 6.0, seed = 7, lowPass = 0.35)
            "explode" -> sfxNoise(320, sampleRate, gain = 0.5, decay = 3.2, seed = 5, lowPass = 0.18)
            "pop" -> sfxTone(720.0, 45, sampleRate, decay = 10.0, gain = 0.35)
            "jump" -> sfxSweep(300.0, 760.0, 160, sampleRate, gain = 0.3, decay = 2.0)
            "whoosh" -> sfxNoise(180, sampleRate, gain = 0.3, decay = 3.5, seed = 13, lowPass = 0.08)
            "rotate" -> sfxSweep(520.0, 340.0, 90, sampleRate, gain = 0.28, decay = 4.0)
            "error" -> sfxTone(160.0, 140, sampleRate, decay = 3.0, gain = 0.35)
            "toss" -> sfxSweep(500.0, 200.0, 120, sampleRate, gain = 0.25, decay = 3.0)
            else -> sfxTone(600.0, 50, sampleRate, gain = 0.28)
        }
    }

    fun play(name: String) {
        synth.scope.launch { synth.playSamples(samples(name)) }
    }

    /** Every banked cue; used by tests to lock the inventory. */
    fun names(): List<String> = GAME_SFX_NAMES
}
