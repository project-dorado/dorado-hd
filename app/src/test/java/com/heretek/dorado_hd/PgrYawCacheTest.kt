package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.PgrEngine
import com.heretek.dorado_hd.ui.apps.games.pgrPropYaw
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-24 regression: city-prop yaw must come from atan2, not from a single
 * direction component (which produced angles in -1..1 instead of radians).
 */
class PgrYawCacheTest {

    private fun assertYaw(expected: Float, actual: Float) {
        assertTrue("expected $expected rad, got $actual", abs(expected - actual) < 1e-4f)
    }

    @Test
    fun `prop yaw maps cardinal directions to quadrants`() {
        assertYaw(PI.toFloat() / 2f, pgrPropYaw(PgrEngine.PgrVec2(1f, 0f)))
        assertYaw(0f, pgrPropYaw(PgrEngine.PgrVec2(0f, 1f)))
        assertYaw(-PI.toFloat() / 2f, pgrPropYaw(PgrEngine.PgrVec2(-1f, 0f)))
        assertYaw(PI.toFloat(), pgrPropYaw(PgrEngine.PgrVec2(0f, -1f)))
    }

    @Test
    fun `prop yaw varies across the track, unlike a raw x component`() {
        val track = PgrEngine.PgrTracks.byIndex(0)
        val yawns = (0 until 8).map { i ->
            pgrPropYaw(track.sampleAt(track.length * i / 8f).forward)
        }
        assertTrue("expected varied yaw, got $yawns", yawns.distinct().size > 3)
    }
}
