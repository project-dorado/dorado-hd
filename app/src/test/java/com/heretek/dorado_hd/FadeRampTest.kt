package com.heretek.dorado_hd

import com.heretek.dorado_hd.media.FadeRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeRampTest {

    @Test
    fun `linear interpolation hits the endpoints and midpoint`() {
        assertEquals(0f, FadeRamp.value(0f, 1f, 0, 1000), 0.001f)
        assertEquals(1f, FadeRamp.value(0f, 1f, 1000, 1000), 0.001f)
        assertEquals(0.5f, FadeRamp.value(0f, 1f, 500, 1000), 0.001f)
        assertEquals(0.5f, FadeRamp.value(1f, 0f, 500, 1000), 0.001f)
    }

    @Test
    fun `clamps beyond the duration and zero duration jumps`() {
        assertEquals(1f, FadeRamp.value(0f, 1f, 5000, 1000), 0.001f)
        assertEquals(0f, FadeRamp.value(0f, 1f, -50, 1000), 0.001f)
        assertEquals(1f, FadeRamp.value(0f, 1f, 10, 0), 0.001f)
    }

    @Test
    fun `steps and effective duration are bounded`() {
        assertEquals(1, FadeRamp.steps(0))
        assertEquals(25, FadeRamp.steps(1000))
        assertEquals(0L, FadeRamp.effectiveMs(-5))
        assertEquals(15_000L, FadeRamp.effectiveMs(60_000))
        assertEquals(4_000L, FadeRamp.effectiveMs(4_000))
    }

    @Test
    fun `fade-out window only applies when the track is long enough`() {
        assertTrue(FadeRamp.inFadeOutWindow(9_000, 10_000, 2_000))
        assertFalse(FadeRamp.inFadeOutWindow(7_900, 10_000, 2_000))
        assertFalse(FadeRamp.inFadeOutWindow(2_500, 3_000, 2_000))
        assertFalse(FadeRamp.inFadeOutWindow(9_000, 10_000, 0))
    }
}
