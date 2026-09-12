package com.heretek.dorado_hd.media

import kotlin.math.abs

/**
 * Audiobook playback-speed steps (roadmap D4). Kept Android-free so the
 * selector mapping and labels are unit-testable; [PlaybackController.setSpeed]
 * feeds the chosen value straight into Media3's `player.setPlaybackSpeed`.
 */
object AudiobookSpeeds {
    val STEPS: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /** Closest offered step to [speed] (ties resolve to the lower step). */
    fun nearest(speed: Float): Float =
        STEPS.minByOrNull { abs(it - speed) } ?: 1.0f

    /** True when [speed] is exactly one of the offered steps. */
    fun isStep(speed: Float): Boolean = STEPS.any { abs(it - speed) < 0.0001f }

    /** "0.75×", "1×" — the selector's label. */
    fun label(speed: Float): String {
        val value = nearest(speed)
        return if (value == value.toInt().toFloat()) "${value.toInt()}×" else "$value×"
    }

    /** Keeps arbitrary input in a sane range while preserving the step value. */
    fun clamp(speed: Float): Float = speed.coerceIn(0.25f, 3.0f)
}
