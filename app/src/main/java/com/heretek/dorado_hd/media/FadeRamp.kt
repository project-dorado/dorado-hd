package com.heretek.dorado_hd.media

/**
 * Pure volume-ramp math for the playback fade transition (post-device
 * extension, canon §10). Media3 has no built-in crossfade and a single
 * ExoPlayer cannot overlap two items, so Dorado-HD implements a
 * **fade-through**: the outgoing track fades out over the tail of its
 * duration and the incoming track fades in — no overlap.
 */
object FadeRamp {

    /** Ramp step; 40 ms keeps the volume staircase inaudible. */
    const val STEP_MS = 40L

    /** Linear interpolation from [from] to [to] at [elapsedMs] / [durationMs]. */
    fun value(from: Float, to: Float, elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return to
        val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        return from + (to - from) * t
    }

    /** Number of ramp steps for a duration (at least one). */
    fun steps(durationMs: Long): Int = (durationMs / STEP_MS).coerceAtLeast(1).toInt()

    /** The fade duration actually used, clamped to sane bounds. */
    fun effectiveMs(configuredMs: Long): Long = configuredMs.coerceIn(0L, 15_000L)

    /** True when the playhead is inside the fade-out window of the track. */
    fun inFadeOutWindow(positionMs: Long, durationMs: Long, fadeMs: Long): Boolean {
        if (fadeMs <= 0L || durationMs <= fadeMs * 2) return false
        return positionMs >= durationMs - fadeMs
    }
}
