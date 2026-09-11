package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.MetronomeEngine
import com.heretek.dorado_hd.ui.apps.MetronomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-15 regression: a dial drag is a stream of samples. Applying several
 * successive angle samples must move the tempo and must not stop the beat
 * clock (the UI gesture now survives because it is keyed on Unit).
 */
class MetronomeDragTest {

    @Test
    fun `successive dial samples keep updating the tempo`() {
        var state = MetronomeState(bpm = 120, running = true)
        val angles = listOf(1.2, 1.8, 2.4, 3.0, 3.6)
        val expected = angles.map { MetronomeEngine.bpmForDialAngle(it) ?: error("angle $it should map") }
        angles.forEach { angle ->
            state = MetronomeEngine.setBpm(state, MetronomeEngine.bpmForDialAngle(angle)!!)
        }
        assertEquals(expected.distinct().size, 5)
        assertEquals(expected.last(), state.bpm)
    }

    @Test
    fun `tempo changes while running preserve the beat clock`() {
        var state = MetronomeState(bpm = 120, running = true, beats = listOf(true, false, false, false))
        state = MetronomeEngine.setBpm(state, 200)
        assertTrue(state.running)
        val result = MetronomeEngine.advance(state, 1_450.0)
        assertEquals(4, result.events.size)
        assertEquals(0, result.state.currentBeat)
        assertTrue("the accumulator keeps the partial beat", result.state.timerMs > 0.0)
    }
}
