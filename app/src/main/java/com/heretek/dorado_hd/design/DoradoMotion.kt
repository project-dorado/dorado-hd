package com.heretek.dorado_hd.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

/**
 * Motion spec: movement starts fast and decelerates gently — the signature
 * Zune "kinetic" curve. All navigation slides use these durations.
 */
object DoradoMotion {
    val Decelerate: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.0f)

    const val PIVOT_SLIDE_MS = 380
    const val QUICKPLAY_MS = 420
    const val DEPTH_MS = 340
    const val STAGGER_MS = 20L

    fun <T> pivot() = tween<T>(PIVOT_SLIDE_MS, easing = Decelerate)
    fun <T> quickplay() = tween<T>(QUICKPLAY_MS, easing = Decelerate)
    fun <T> depth() = tween<T>(DEPTH_MS, easing = Decelerate)
    fun <T> instant() = tween<T>(1, easing = LinearEasing)
}

