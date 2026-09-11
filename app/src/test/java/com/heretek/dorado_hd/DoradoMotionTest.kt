package com.heretek.dorado_hd

import com.heretek.dorado_hd.design.DoradoMotion
import kotlin.math.exp
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Zune HD kinetic scroller mapping derived from the shell's
 * `XuiTouchSettings` (docs/zune-hd-touch-settings.md §3).
 */
class DoradoMotionTest {

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
