package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.MetronomeEngine
import com.heretek.dorado_hd.ui.apps.MetronomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 1 metronome rules: 40–340 BPM clamp, dial angle round trip, 0–16 beat
 * slider mapping, accent advance/wrap, pulse decay and persistence.
 */
class MetronomeLogicTest {

    @Test
    fun `bpm clamps to the device range`() {
        assertEquals(40, MetronomeEngine.clampBpm(1))
        assertEquals(40, MetronomeEngine.clampBpm(40))
        assertEquals(340, MetronomeEngine.clampBpm(341))
        assertEquals(340, MetronomeEngine.clampBpm(1000))
    }

    @Test
    fun `beat length matches sixty thousand over bpm`() {
        assertEquals(500.0, MetronomeEngine.beatLengthMs(120), 1e-9)
        assertEquals(1500.0, MetronomeEngine.beatLengthMs(40), 1e-9)
        assertEquals(60_000.0 / 340, MetronomeEngine.beatLengthMs(340), 1e-9)
    }

    @Test
    fun `dial angle round trips across the range`() {
        for (bpm in intArrayOf(40, 60, 120, 200, 339, 340)) {
            assertEquals(bpm, MetronomeEngine.bpmForDialAngle(MetronomeEngine.dialAngleForBpm(bpm))!!)
        }
        assertNull("outside the sweep must not set a tempo", MetronomeEngine.bpmForDialAngle(0.2))
        assertNull(MetronomeEngine.bpmForDialAngle(6.0))
        assertTrue(MetronomeEngine.indexAngleForBpm(120) > 0.0)
    }

    @Test
    fun `slider maps the device coordinates`() {
        assertEquals(0, MetronomeEngine.beatCountFromSlider(22.0))
        assertEquals(8, MetronomeEngine.beatCountFromSlider(135.0))
        assertEquals(16, MetronomeEngine.beatCountFromSlider(248.0))
        assertEquals(0, MetronomeEngine.beatCountFromSlider(-100.0))
        assertEquals(16, MetronomeEngine.beatCountFromSlider(1000.0))
        assertEquals(22.0, MetronomeEngine.sliderXForBeatCount(0), 1e-9)
        assertEquals(248.0, MetronomeEngine.sliderXForBeatCount(16), 1e-9)
    }

    @Test
    fun `advance fires accents and wraps`() {
        val state = MetronomeState(bpm = 120, running = true, beats = listOf(true, false, false, false))
        val result = MetronomeEngine.advance(state, 2000.0)
        assertEquals(4, result.events.size)
        assertEquals(listOf(1, 2, 3, 0), result.events.map { it.beat })
        assertTrue("the wrap back to beat 0 is accented", result.events.last().accent)
        assertEquals(0, result.state.currentBeat)
        assertEquals(1.0, result.state.pulse, 1e-9)
        assertTrue(MetronomeEngine.advance(state.copy(running = false), 2000.0).events.isEmpty())
    }

    @Test
    fun `zero beats still tick unaccented`() {
        val state = MetronomeState(bpm = 60, running = true, beats = emptyList())
        val result = MetronomeEngine.advance(state, 1000.0)
        assertEquals(1, result.events.size)
        assertFalse(result.events.first().accent)
        assertEquals(0, result.state.currentBeat)
    }

    @Test
    fun `tempo change mid beat retimes the stream`() {
        var state = MetronomeState(bpm = 60, running = true, beats = listOf(true))
        var result = MetronomeEngine.advance(state, 900.0)
        assertTrue(result.events.isEmpty())
        state = MetronomeEngine.setBpm(result.state, 120)
        result = MetronomeEngine.advance(state, 200.0)
        assertEquals("a shorter beat length catches the accumulator up", 2, result.events.size)
    }

    @Test
    fun `pulse decays between beats`() {
        val state = MetronomeState(bpm = 120, running = true, beats = listOf(false), pulse = 1.0)
        val result = MetronomeEngine.advance(state, 100.0)
        assertTrue(result.events.isEmpty())
        assertEquals(1.0 - MetronomeEngine.PULSE_DECAY_PER_SEC * 0.1, result.state.pulse, 1e-9)
        val dark = MetronomeEngine.advance(
            MetronomeState(pulse = 0.05, running = true, beats = listOf(false)),
            100.0,
        )
        assertEquals(0.0, dark.state.pulse, 1e-9)
    }

    @Test
    fun `accent grid grows and shrinks from the end`() {
        var state = MetronomeEngine.setBeatCount(MetronomeState(), 2)
        state = MetronomeEngine.toggleAccent(state, 1)
        assertEquals(listOf(false, true), state.beats)
        state = MetronomeEngine.setBeatCount(state, 5)
        assertEquals(5, state.beats.size)
        assertEquals(listOf(false, true, false, false, false), state.beats)
        state = MetronomeEngine.setBeatCount(state, 1)
        assertEquals(listOf(false), state.beats)
        state = MetronomeEngine.toggleCell(state, 3)
        assertEquals(4, state.beats.size)
        assertTrue(state.beats[3])
        assertEquals(16, MetronomeEngine.setBeatCount(state, 99).beats.size)
        assertEquals(0, MetronomeEngine.setBeatCount(state, -5).beats.size)
    }

    @Test
    fun `tap tempo averages intervals and restarts on a long gap`() {
        var state = MetronomeState()
        state = MetronomeEngine.tapTempo(state, 0L)
        state = MetronomeEngine.tapTempo(state, 500L)
        assertEquals(120, state.bpm)
        state = MetronomeEngine.tapTempo(state, 1000L)
        assertEquals(120, state.bpm)
        state = MetronomeEngine.tapTempo(state, 1000L + 10_000L)
        assertEquals(1, state.tapTimes.size)
        assertEquals(120, state.bpm)
    }

    @Test
    fun `state round trips through the saved blob`() {
        var state = MetronomeEngine.setBpm(MetronomeState(), 200)
        state = MetronomeEngine.setBeatCount(state, 6)
        state = MetronomeEngine.toggleAccent(state, 0)
        state = MetronomeEngine.toggleAccent(state, 3)
        val parsed = MetronomeEngine.parse(MetronomeEngine.serialize(state))
        assertEquals(200, parsed.bpm)
        assertEquals(state.beats, parsed.beats)
        assertEquals(MetronomeState(), MetronomeEngine.parse(null))
        assertEquals(40, MetronomeEngine.parse("30|1010").bpm)
    }
}
