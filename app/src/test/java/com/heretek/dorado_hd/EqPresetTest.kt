package com.heretek.dorado_hd

import com.heretek.dorado_hd.analysis.EqPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M14 — device equalizer presets (zmedia_serv `Preset%02d`). */
class EqPresetTest {

    @Test
    fun `fromName resolves names and labels, defaulting to flat`() {
        assertEquals(EqPreset.ROCK, EqPreset.fromName("rock"))
        assertEquals(EqPreset.ROCK, EqPreset.fromName("ROCK"))
        assertEquals(EqPreset.FLAT, EqPreset.fromName("bogus"))
        assertEquals(EqPreset.FLAT, EqPreset.fromName(null))
    }

    @Test
    fun `every preset has five bands`() {
        EqPreset.entries.forEach { assertEquals(5, it.gainsDb.size) }
    }

    @Test
    fun `gains resample to the requested band count`() {
        val five = EqPreset.gainsForBands(EqPreset.ROCK, 5)
        assertEquals(EqPreset.ROCK.gainsDb, five)
        val three = EqPreset.gainsForBands(EqPreset.ROCK, 3)
        assertEquals(3, three.size)
        assertTrue(EqPreset.gainsForBands(EqPreset.FLAT, 0).isEmpty())
    }
}
