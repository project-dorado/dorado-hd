package com.heretek.dorado_hd.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlin.math.ln
import kotlin.math.pow

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

    /**
     * Kinetic scroller velocity retention per 60 Hz frame. This is
     * `XuiTouchSettings[0x1C]` as the Zune HD shell applied it
     * (`gemstone.exe` VA `0x1C900`–`0x1CB64`; the XUI default is 0.7, the
     * shell raised it to 0.95 for the device's long glide).
     * See docs/zune-hd-touch-settings.md §2–3.
     */
    const val KINETIC_FRAME_RETENTION = 0.95f
    const val KINETIC_FRAME_HZ = 60f

    /**
     * Converts [KINETIC_FRAME_RETENTION] into the `frictionMultiplier` expected
     * by `androidx.compose.animation.core.exponentialDecay`, whose spec models
     * `velocity(t) = v0 * exp(-4.2 * multiplier * t)` (t in seconds). A value
     * below 1.0 therefore glides longer than the platform default of 1.0.
     */
    fun kineticFrictionMultiplier(
        frameRetention: Float = KINETIC_FRAME_RETENTION,
        frameHz: Float = KINETIC_FRAME_HZ,
    ): Float = ln(frameRetention.pow(frameHz)) / -4.2f

    fun <T> pivot() = tween<T>(PIVOT_SLIDE_MS, easing = Decelerate)
    fun <T> quickplay() = tween<T>(QUICKPLAY_MS, easing = Decelerate)
    fun <T> depth() = tween<T>(DEPTH_MS, easing = Decelerate)
    fun <T> instant() = tween<T>(1, easing = LinearEasing)
}

