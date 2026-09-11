package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.PianoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A-13 regression: the octave drag must snap from the live scrub offset, not
 * the offset captured at first composition (a stale `current` reverted every
 * drag to octave 1).
 */
class PianoOctaveLiveTest {

    @Test
    fun `live scrub offsets map to their own octave`() {
        val cases = listOf(
            2f to 1,
            9f to 2,
            23f to 4,
            37f to 6,
        )
        cases.forEach { (offset, octave) ->
            assertEquals("offset $offset", octave, PianoEngine.octaveForOffset(offset))
            assertEquals(PianoEngine.snapWhiteIndex(octave).toFloat(), PianoEngine.snapOffset(offset))
        }
    }

    @Test
    fun `drag end uses the last scrubbed offset not the first`() {
        var liveOffset = PianoEngine.snapWhiteIndex(1).toFloat() // C1, octave 1
        val startedAt = PianoEngine.octaveForOffset(liveOffset)
        // The detector scrubs through the bar before the finger lifts.
        liveOffset = 30f
        liveOffset = 44f
        val snappedOctave = PianoEngine.octaveForOffset(liveOffset)
        assertNotEquals(startedAt, snappedOctave)
        assertEquals(7, snappedOctave)
        assertEquals(PianoEngine.snapWhiteIndex(7).toFloat(), PianoEngine.snapOffset(liveOffset))
    }
}
