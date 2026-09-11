package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.CountdownState
import com.heretek.dorado_hd.ui.apps.StopwatchEngine
import com.heretek.dorado_hd.ui.apps.StopwatchLap
import com.heretek.dorado_hd.ui.apps.StopwatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H-08 regression: the stopwatch persists via a periodic snapshot, not a Room
 * write on every ticking state emission.
 */
class StopwatchSnapshotTest {

    @Test
    fun `snapshot fires once per interval instead of every tick`() {
        assertFalse(StopwatchEngine.snapshotDue(lastWriteMs = 0L, nowMs = 999L))
        assertTrue(StopwatchEngine.snapshotDue(lastWriteMs = 0L, nowMs = 1_000L))
        assertTrue("30 Hz emissions coalesce into one write", StopwatchEngine.snapshotDue(lastWriteMs = 0L, nowMs = 30_000L))
        assertFalse(StopwatchEngine.snapshotDue(lastWriteMs = 5_000L, nowMs = 5_999L))
        assertTrue(StopwatchEngine.snapshotDue(lastWriteMs = 5_000L, nowMs = 6_000L))
    }

    @Test
    fun `serialized snapshot round trips a running stopwatch and countdown`() {
        val sw = StopwatchState(
            running = true,
            accumulatedMs = 12_345L,
            startedAtWall = 1_000_000L,
            laps = listOf(StopwatchLap(1, 2_000L, 2_000L), StopwatchLap(2, 3_000L, 5_000L)),
        )
        val cd = CountdownState(
            setupMs = 60_000L,
            remainingMs = 42_000L,
            running = true,
            startedAtWall = 1_000_000L,
            alarming = false,
        )
        val blob = StopwatchEngine.serialize(sw, cd, StopwatchEngine.MODE_TIMER)
        val restored = StopwatchEngine.restore(blob, nowWall = 1_001_000L)
        assertEquals(StopwatchEngine.MODE_TIMER, restored.mode)
        assertEquals(12_345L + 1_000L, restored.stopwatch.elapsed(1_001_000L))
        assertEquals(2, restored.stopwatch.laps.size)
        assertEquals(41_000L, restored.countdown.remaining(1_001_000L))
        assertTrue(restored.countdown.running)
    }
}
