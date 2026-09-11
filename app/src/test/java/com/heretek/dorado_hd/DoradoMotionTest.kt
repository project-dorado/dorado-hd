package com.heretek.dorado_hd

import com.heretek.dorado_hd.design.DoradoMotion
import kotlin.math.exp
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Zune HD kinetic scroller mapping derived from the shell's
 * `XuiTouchSettings`/integrator (docs/zune-hd-touch-settings.md §6,
 * docs/zune-hd-parity-audit.md §3).
 */
class DoradoMotionTest {

    @Test
    fun `kinetic tick matches the device 62 point 5 hz`() {
        assertEquals(62.5f, DoradoMotion.KINETIC_FRAME_HZ, 1e-3f)
    }

    @Test
    fun `fling velocity is clamped to the device cap`() {
        assertEquals(DoradoMotion.KINETIC_MAX_VELOCITY, DoradoMotion.clampFlingVelocity(99_999f), 1e-3f)
        assertEquals(-DoradoMotion.KINETIC_MAX_VELOCITY, DoradoMotion.clampFlingVelocity(-99_999f), 1e-3f)
        assertEquals(50f, DoradoMotion.clampFlingVelocity(50f), 1e-3f)
    }

    @Test
    fun `kinetic multiplier glides longer than the platform default`() {
        val multiplier = DoradoMotion.kineticFrictionMultiplier()
        assertTrue("expected less friction than Compose's default 1.0", multiplier < 1f)
    }

    @Test
    fun `per-second retention matches the shell frame retention`() {
        val multiplier = DoradoMotion.kineticFrictionMultiplier()
        val perSecondFriction = -4.2f * multiplier
        val perSecondRetention = exp(perSecondFriction)
        val expected = DoradoMotion.KINETIC_FRAME_RETENTION.pow(DoradoMotion.KINETIC_FRAME_HZ)
        assertEquals(expected.toDouble(), perSecondRetention.toDouble(), 1e-4)
    }

    @Test
    fun `lane retention glides shorter than the list retention`() {
        val list = DoradoMotion.kineticFrictionMultiplier(DoradoMotion.KINETIC_FRAME_RETENTION)
        val lane = DoradoMotion.kineticFrictionMultiplier(DoradoMotion.KINETIC_LANE_FRAME_RETENTION)
        assertTrue("lane must apply more friction than lists", lane > list)
    }
}
