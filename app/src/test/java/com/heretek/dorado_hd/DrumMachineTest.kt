package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.DrumMachineEngine
import com.heretek.dorado_hd.ui.apps.DrumMachineEngine.DrumPad
import com.heretek.dorado_hd.ui.apps.DrumMachineEngine.DrumType
import com.heretek.dorado_hd.ui.apps.DrumMachineEngine.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrumMachineTest {

    private fun pad(
        id: Int = 1,
        type: DrumType,
        x: Float = 0f,
        y: Float = 0f,
        size: Float = 100f,
        loop: Boolean = true,
    ) = DrumPad(id, x, y, size, type, "a", "b", loop = loop)

    @Test
    fun `left right split sends hats open left closed right`() {
        val hat = pad(type = DrumType.HIHAT, size = 100f)
        assertEquals(Zone.ONE, DrumMachineEngine.zoneFor(hat, 5f, 10f))
        assertEquals(Zone.TWO, DrumMachineEngine.zoneFor(hat, 20f, 10f))
        val kick = pad(type = DrumType.KICK, size = 100f)
        assertEquals(Zone.ONE, DrumMachineEngine.zoneFor(kick, 14f, 50f))
        assertEquals(Zone.TWO, DrumMachineEngine.zoneFor(kick, 16f, 50f))
    }

    @Test
    fun `ride splits bell from edge radially`() {
        val ride = pad(type = DrumType.RIDE, size = 1f)
        assertEquals(Zone.ONE, DrumMachineEngine.zoneFor(ride, 0.5f, 0.5f))
        assertEquals(Zone.TWO, DrumMachineEngine.zoneFor(ride, 50f, 50f))
    }

    @Test
    fun `held pads retrigger on their loop period`() {
        assertEquals(250L, DrumMachineEngine.loopIntervalMs(DrumMachineEngine.DEFAULT_LOOP_SECONDS))
        assertEquals(
            listOf(250L, 500L, 750L, 1_000L),
            DrumMachineEngine.pendingLoopTriggers(heldSinceMs = 0L, nowMs = 1_000L, loopSeconds = 0.25),
        )
        assertTrue(DrumMachineEngine.pendingLoopTriggers(500L, 500L, 0.25).isEmpty())
        assertEquals(listOf(400L), DrumMachineEngine.pendingLoopTriggers(0L, 400L, 0.4))
    }

    @Test
    fun `polyphony is capped at twelve oldest first`() {
        val active = (1L..12L).toList()
        val next = DrumMachineEngine.allocateVoices(active, 13L)
        assertEquals(12, next.size)
        assertEquals(2L, next.first())
        assertEquals(13L, next.last())
        assertEquals(12, DrumMachineEngine.allocateVoices(active.take(11), 12L).size)
    }

    @Test
    fun `record to playback keeps timestamp order and fires each event once`() {
        var state = DrumMachineEngine.beginRecording(DrumMachineEngine.DrumMachineState(), nowMs = 1_000L)
        state = DrumMachineEngine.record(state, 1_100L, padId = 1, zone = Zone.ONE)
        state = DrumMachineEngine.record(state, 1_050L, padId = 2, zone = Zone.TWO)
        state = DrumMachineEngine.record(state, 1_300L, padId = 3, zone = Zone.ONE)
        assertEquals(listOf(100L, 50L, 300L), state.recorded.map { it.atMs })

        state = DrumMachineEngine.beginPlayback(state)
        assertEquals(listOf(50L, 100L, 300L), state.playback.map { it.atMs })

        val (early, afterEarly) = DrumMachineEngine.dueEvents(state, elapsedMs = 120L)
        assertEquals(listOf(50L, 100L), early.map { it.atMs })
        assertEquals(listOf(300L), afterEarly.playback.map { it.atMs })

        val (late, afterLate) = DrumMachineEngine.dueEvents(afterEarly, elapsedMs = 300L)
        assertEquals(listOf(300L), late.map { it.atMs })
        assertTrue(afterLate.playback.isEmpty())

        val (none, _) = DrumMachineEngine.dueEvents(afterLate, elapsedMs = 10_000L)
        assertTrue(none.isEmpty())
    }

    @Test
    fun `bpm clamps to the device range`() {
        val state = DrumMachineEngine.DrumMachineState()
        assertEquals(40, DrumMachineEngine.setBpm(state, 10).bpm)
        assertEquals(340, DrumMachineEngine.setBpm(state, 500).bpm)
        assertEquals(120, DrumMachineEngine.setBpm(state, 120).bpm)
        assertEquals(500L, DrumMachineEngine.metronomePeriodMs(120))
        assertEquals(1_500L, DrumMachineEngine.metronomePeriodMs(40))
    }

    @Test
    fun `pad geometry and topmost hit`() {
        val first = pad(id = 1, type = DrumType.KICK, x = 0f, y = 0f, size = 100f)
        val second = pad(id = 2, type = DrumType.SNARE, x = 40f, y = 40f, size = 100f)
        val state = DrumMachineEngine.DrumMachineState(
            pads = listOf(first.copy(layerDepth = 0), second.copy(layerDepth = 5)),
        )
        assertTrue(DrumMachineEngine.contains(first, 50f, 50f))
        assertEquals(2, DrumMachineEngine.padAt(state, 50f, 50f)?.id)
        val moved = DrumMachineEngine.movePad(state, 2, -500f, -500f)
        assertEquals(0f, moved.pads.first { it.id == 2 }.x, 0.001f)
        assertEquals(0f, moved.pads.first { it.id == 2 }.y, 0.001f)
    }

    @Test
    fun `layout and bpm round trip through the state blob`() {
        val state = DrumMachineEngine.DrumMachineState(
            bpm = 155,
            metronome = true,
            pads = DrumMachineEngine.preset(2),
        )
        val decoded = DrumMachineEngine.decode(DrumMachineEngine.encode(state))!!
        assertEquals(155, decoded.bpm)
        assertTrue(decoded.metronome)
        assertEquals(state.pads, decoded.pads)
        assertEquals(null, DrumMachineEngine.decode(null))
    }

    @Test
    fun `three presets rebuild fixed layouts`() {
        val ids = (0..2).map { DrumMachineEngine.preset(it).map { pad -> pad.id }.toSet() }
        assertEquals(3, ids.toSet().size)
        assertTrue(DrumMachineEngine.preset(0).isNotEmpty())
        assertTrue(DrumMachineEngine.preset(1).isNotEmpty())
        assertTrue(DrumMachineEngine.preset(2).isNotEmpty())
    }
}
