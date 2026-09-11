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
     * Kinetic scroller velocity retention per frame — an **empirical
     * approximation**, not a device constant. The Zune HD integrator
     * (`xuidll.dll@0x41841D58`) is a dt-scaled glide (`pos += (dt_ms/1000)·v`,
     * with dt floored at 33.333 ms) that updates `v' = v·(1 + c[+0xB0])` clamped
     * to `±(step·c[+0xB4])` and ticks at 16 ms (62.5 Hz). It reads the element's
     * touch-settings fields `[0x08]/[0x0C]` — **not** `[0x1C]`.
     * See docs/zune-hd-touch-settings.md §6 and docs/zune-hd-parity-audit.md §3.
     */
    const val KINETIC_FRAME_RETENTION = 0.95f

    /** Device tick is 62.5 Hz (`FUN_41848B98`, 16 ms). */
    const val KINETIC_FRAME_HZ = 62.5f

    /**
     * Fling velocity cap. The device clamps drag-derived velocity to `32.0` in
     * its own units (`FUN_4184C310`, literal `@0x41804168`), applied each 16 ms
     * tick. The Compose equivalent is a per-second cap; `32.0 * 62.5 ≈ 2000 px/s`
     * is the reconstructed bound (approximation — see
     * docs/zune-hd-parity-audit.md §3).
     */
    const val KINETIC_MAX_VELOCITY = 2000f

    /**
     * Kinetic retention for horizontal lanes (Quickplay, marketplace, artist
     * albums). **Post-device tuning** (canon §10): the device applied one global
     * retention to every surface, which over-glides a carousel. Lanes use a
     * shorter glide than the vertical lists while keeping the same decay model.
     */
    const val KINETIC_LANE_FRAME_RETENTION = 0.94f

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

    /** Clamp a fling's initial velocity to the device's reconstructed cap. */
    fun clampFlingVelocity(velocity: Float): Float =
        velocity.coerceIn(-KINETIC_MAX_VELOCITY, KINETIC_MAX_VELOCITY)

    fun <T> pivot() = tween<T>(PIVOT_SLIDE_MS, easing = Decelerate)
    fun <T> quickplay() = tween<T>(QUICKPLAY_MS, easing = Decelerate)
    fun <T> depth() = tween<T>(DEPTH_MS, easing = Decelerate)
    fun <T> instant() = tween<T>(1, easing = LinearEasing)
}

