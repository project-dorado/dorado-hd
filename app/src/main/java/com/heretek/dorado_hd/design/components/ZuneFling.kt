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
 * The Zune HD kinetic fling: proportional (exponential) velocity decay whose
 * per-frame retention is the shell's `XuiTouchSettings[0x1C]`
 * (docs/zune-hd-touch-settings.md). Replaces the platform spline fling so
 * lists glide for the device's longer duration before coming to rest.
 *
 * Deceleration only — no springs (canon §6, invariant 6).
 */
@Composable
fun rememberZuneFlingBehavior(): FlingBehavior {
    val decay = remember {
        exponentialDecay<Float>(
            frictionMultiplier = DoradoMotion.kineticFrictionMultiplier(),
            absVelocityThreshold = 1f,
        )
    }
    return remember(decay) { ZuneFlingBehavior(decay) }
}

private class ZuneFlingBehavior(
    private val decay: DecayAnimationSpec<Float>,
    private val velocityThreshold: Float = 1f,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= velocityThreshold) return initialVelocity
        var lastValue = 0f
        var lastVelocity = initialVelocity
        AnimationState(
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            initialVelocity = initialVelocity,
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
