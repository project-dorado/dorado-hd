package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.GAME_SFX_NAMES
import com.heretek.dorado_hd.ui.apps.Noise
import com.heretek.dorado_hd.ui.apps.sfxArpeggio
import com.heretek.dorado_hd.ui.apps.sfxNoise
import com.heretek.dorado_hd.ui.apps.sfxSweep
import com.heretek.dorado_hd.ui.apps.sfxTone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the synthesized SFX bank (no Android dependency). */
class SfxTest {

    @Test
    fun `noise is deterministic per seed and differs across seeds`() {
        val a = Noise(42)
        val b = Noise(42)
        val c = Noise(43)
        val first = (1..64).map { a.next() }
        val second = (1..64).map { b.next() }
        assertEquals(first, second)
        assertNotEquals(first, (1..64).map { c.next() })
        assertTrue(first.all { it in -1.0..1.0 })
    }

    @Test
    fun `tone length follows duration and is reproducible`() {
        val one = sfxTone(440.0, 100)
        val two = sfxTone(440.0, 100)
        assertEquals(2205, one.size)
        assertArrayEquals(one, two, 0.0)
        assertTrue(one.any { it != 0.0 })
    }

    @Test
    fun `sweep, noise and arpeggio produce bounded non-silent buffers`() {
        val sweep = sfxSweep(200.0, 900.0, 150)
        val noise = sfxNoise(120, seed = 9)
        val chord = sfxArpeggio(listOf(440.0, 660.0))
        for (buf in listOf(sweep, noise, chord)) {
            assertTrue(buf.isNotEmpty())
            assertTrue(buf.all { it in -1.0..1.0 })
            assertTrue(buf.any { kotlin.math.abs(it) > 0.001 })
        }
        assertEquals(150 * 22050 / 1000, sweep.size)
    }

    @Test
    fun `sfx bank inventory is unique and complete`() {
        assertEquals(16, GAME_SFX_NAMES.size)
        assertEquals(GAME_SFX_NAMES.size, GAME_SFX_NAMES.toSet().size)
        assertTrue(GAME_SFX_NAMES.contains("win") && GAME_SFX_NAMES.contains("explode"))
    }
}
