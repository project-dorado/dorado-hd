package com.heretek.dorado_hd

import com.heretek.dorado_hd.analysis.SpectrumBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumBandsTest {

    @Test
    fun `silence maps to zero bars`() {
        val bands = SpectrumBands.fromMagnitudes(DoubleArray(1024), 24)
        assertEquals(24, bands.size)
        assertTrue(bands.all { it == 0f })
    }

    @Test
    fun `bars stay normalised and the peak band reaches one`() {
        val mags = DoubleArray(1024) { if (it in 4..40) 1.0 else 0.0 }
        val bands = SpectrumBands.fromMagnitudes(mags, 16)
        assertTrue(bands.all { it in 0f..1f })
        assertEquals(1f, bands.max(), 0.001f)
    }

    @Test
    fun `low-frequency energy lands in the lower half of the bands`() {
        val mags = DoubleArray(1024) { if (it < 16) 2.0 else 0.0 }
        val bands = SpectrumBands.fromMagnitudes(mags, 16)
        val low = bands.take(8).average()
        val high = bands.drop(8).average()
        assertTrue("low=$low high=$high", low > high)
    }

    @Test
    fun `smoothing rises instantly and decays gradually`() {
        val previous = floatArrayOf(0.8f, 0.4f)
        val target = floatArrayOf(0.2f, 0.9f)
        val out = SpectrumBands.smooth(previous, target)
        assertEquals(0.9f, out[1], 0.001f)          // rises to the target
        assertEquals(0.8f * 0.82f, out[0], 0.001f)  // decays by the factor
        val fresh = SpectrumBands.smooth(null, target)
        assertEquals(target.toList(), fresh.toList())
    }
}
