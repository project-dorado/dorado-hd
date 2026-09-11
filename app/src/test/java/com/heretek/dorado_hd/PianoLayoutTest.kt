package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.PianoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PianoLayoutTest {

    @Test
    fun `keyboard has 52 white and 36 black keys`() {
        assertEquals(52, PianoEngine.whiteKeys().size)
        assertEquals(36, PianoEngine.blackKeys().size)
        assertEquals(52, PianoEngine.WHITE_KEYS)
        assertEquals(36, PianoEngine.BLACK_KEYS)
    }

    @Test
    fun `white keys run A0 to C8`() {
        assertEquals(21, PianoEngine.whiteMidi(0))
        assertEquals(108, PianoEngine.whiteMidi(51))
        assertEquals("A0", PianoEngine.noteName(PianoEngine.whiteMidi(0)))
        assertEquals("C8", PianoEngine.noteName(PianoEngine.whiteMidi(51)))
        assertEquals(60, PianoEngine.whiteMidi(23))
        assertEquals("C4", PianoEngine.noteName(60))
        assertEquals("C#4", PianoEngine.noteName(61))
    }

    @Test
    fun `black keys sit after A C D F and G only`() {
        val placed = PianoEngine.blackKeys().map { it.whiteIndex }
        val expected = (0 until 52).filter { i ->
            val pc = PianoEngine.whiteMidi(i) % 12
            i < 51 && pc in setOf(9, 0, 2, 5, 7)
        }
        assertEquals(expected, placed)
        assertTrue(PianoEngine.hasBlackAfter(0)) // A0 -> A#0
        assertFalse(PianoEngine.hasBlackAfter(1)) // B0 has no sharp
        assertTrue(PianoEngine.hasBlackAfter(2)) // C1 -> C#1
        assertTrue(PianoEngine.hasBlackAfter(6)) // G1 -> G#1
        assertFalse(PianoEngine.hasBlackAfter(8)) // B1 has no sharp
        assertFalse(PianoEngine.hasBlackAfter(51)) // C8 ends the keyboard
        assertEquals(106, PianoEngine.blackKeys().last().midi) // A#7
    }

    @Test
    fun `frequencies follow equal temperament`() {
        assertEquals(440.0, PianoEngine.midiToFrequency(69), 0.0001)
        assertEquals(261.6256, PianoEngine.midiToFrequency(60), 0.01)
        assertEquals(880.0, PianoEngine.midiToFrequency(81), 0.0001)
    }

    @Test
    fun `octave bar snaps to C keys`() {
        assertEquals(2, PianoEngine.snapWhiteIndex(1))
        assertEquals(23, PianoEngine.snapWhiteIndex(4))
        assertEquals(51, PianoEngine.snapWhiteIndex(8))
        assertEquals(4, PianoEngine.octaveForOffset(23f))
        assertEquals(4, PianoEngine.octaveForOffset(22.4f))
        assertEquals(23f, PianoEngine.snapOffset(25f))
        assertEquals(1, PianoEngine.octaveForOffset(-100f))
        assertEquals(7, PianoEngine.octaveForOffset(999f))
    }

    @Test
    fun `voices are capped at fourteen oldest first`() {
        var voices = emptyList<PianoEngine.Voice>()
        for (midi in 21 until 21 + PianoEngine.VOICE_CAP) {
            voices = PianoEngine.allocate(voices, PianoEngine.Voice(midi, 0L))
        }
        assertEquals(PianoEngine.VOICE_CAP, voices.size)
        voices = PianoEngine.allocate(voices, PianoEngine.Voice(99, 1L))
        assertEquals(PianoEngine.VOICE_CAP, voices.size)
        assertFalse(voices.any { it.midi == 21 })
        assertEquals(99, voices.last().midi)
    }

    @Test
    fun `sustain keeps released notes ringing until unsustained`() {
        val key = PianoEngine.PianoKey(0, false, 21)
        var state = PianoEngine.PianoState(sustain = true)
        state = PianoEngine.press(state, key, 0L)
        state = PianoEngine.release(state, 21, 100L)
        val ringing = state.voices.single()
        assertFalse(ringing.held)
        assertNull("sustained release must not start the fade", ringing.releasedAtMs)
        assertEquals(1f, PianoEngine.gainFor(ringing, 5_000L, dampen = false), 0.001f)

        state = PianoEngine.unsustain(state, 200L)
        val fading = state.voices.single()
        assertEquals(200L, fading.releasedAtMs)
        assertEquals(0.5f, PianoEngine.gainFor(fading, 700L, dampen = false), 0.001f)
        assertEquals(0f, PianoEngine.gainFor(fading, 1_200L, dampen = false), 0.001f)
    }

    @Test
    fun `dampen drops the gain to seventy percent`() {
        val voice = PianoEngine.Voice(60, 0L)
        assertEquals(PianoEngine.DAMPEN_GAIN, PianoEngine.gainFor(voice, 100L, dampen = true), 0.001f)
        assertEquals(1f, PianoEngine.gainFor(voice, 100L, dampen = false), 0.001f)
        val state = PianoEngine.PianoState(dampen = true, volume = 0)
        assertEquals(PianoEngine.VOLUME_GAINS[0] * PianoEngine.DAMPEN_GAIN, state.gain, 0.001f)
    }

    @Test
    fun `volume cycles through three levels`() {
        assertEquals(1, PianoEngine.cycleVolume(0))
        assertEquals(2, PianoEngine.cycleVolume(1))
        assertEquals(0, PianoEngine.cycleVolume(2))
    }

    @Test
    fun `glissando path samples every ten pixels`() {
        val path = PianoEngine.samplePath(0f, 35f, 10f)
        assertEquals(4, path.size)
        assertEquals(35f, path.last(), 0.001f)
        var previous = 0f
        for (point in path) {
            assertTrue("step must not exceed 10px", abs(point - previous) <= 10.001f)
            previous = point
        }
        assertEquals(listOf(7f), PianoEngine.samplePath(7f, 7f, 10f))
    }

    @Test
    fun `hit test claims black keys above white keys`() {
        val whiteWidth = 60f
        val blackWidth = 40f
        val blackHeight = 127f
        val aSharp = PianoEngine.hitTest(60f, 5f, whiteWidth, blackWidth, blackHeight, 0f)
        assertNotNull(aSharp)
        assertTrue(aSharp!!.black)
        assertEquals(22, aSharp.midi)

        val cSharp = PianoEngine.hitTest(180f, 5f, whiteWidth, blackWidth, blackHeight, 0f)
        assertEquals(25, cSharp!!.midi)

        val white = PianoEngine.hitTest(59f, 200f, whiteWidth, blackWidth, blackHeight, 0f)
        assertFalse(white!!.black)
        assertEquals(21, white.midi)

        // Scrolled one white key right: the same point is now D0's black key region.
        val scrolled = PianoEngine.hitTest(0f, 5f, whiteWidth, blackWidth, blackHeight, 1f)
        assertEquals(22, scrolled!!.midi)
        assertNull(PianoEngine.hitTest(-5f, 200f, whiteWidth, blackWidth, blackHeight, 0f))
    }
}
