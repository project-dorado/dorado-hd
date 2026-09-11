package com.heretek.dorado_hd.design.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.heretek.dorado_hd.design.DoradoMotion
import kotlin.math.abs

/**
 * The Zune HD kinetic fling: proportional velocity decay approximating the
 * device glide (`xuidll.dll@0x41841D58`: `pos += (dt/1000)·v` at 62.5 Hz).
 * The per-frame retention is an empirical approximation, not a device constant
 * (docs/zune-hd-parity-audit.md §3); the initial velocity is capped to the
 * device's reconstructed bound (`FUN_4184C310`).
 *
 * Vertical lists use the default [DoradoMotion.KINETIC_FRAME_RETENTION];
 * horizontal lanes pass [DoradoMotion.KINETIC_LANE_FRAME_RETENTION] for a
 * shorter glide (canon §10, post-device tuning).
 *
 * Deceleration only — no springs in navigation (canon §6, invariant 6).
 */
@Composable
fun rememberZuneFlingBehavior(
    frameRetention: Float = DoradoMotion.KINETIC_FRAME_RETENTION,
): FlingBehavior {
    val decay = remember(frameRetention) {
        exponentialDecay<Float>(
            frictionMultiplier = DoradoMotion.kineticFrictionMultiplier(frameRetention),
            absVelocityThreshold = 1f,
        )
    }
    // Compose velocities are in layout px/s. Adaptive mode raises LocalDensity
    // above the display's base density, so without this factor the device cap
    // would shrink as the layout scales (shorter glides on big layouts).
    val baseDensity = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.density
    val velocityScale = androidx.compose.ui.platform.LocalDensity.current.density / baseDensity
    return remember(decay, velocityScale) { ZuneFlingBehavior(decay, velocityScale) }
}

private class ZuneFlingBehavior(
    private val decay: DecayAnimationSpec<Float>,
    private val velocityScale: Float,
    private val velocityThreshold: Float = 1f,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Device velocity cap (FUN_4184C310): clamp before the glide.
        val cappedVelocity = DoradoMotion.clampFlingVelocity(initialVelocity, velocityScale)
        if (abs(cappedVelocity) <= velocityThreshold) return cappedVelocity
        var lastValue = 0f
        var lastVelocity = cappedVelocity
        AnimationState(
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            initialVelocity = cappedVelocity,
        ).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            lastVelocity = velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return lastVelocity
    }
}
