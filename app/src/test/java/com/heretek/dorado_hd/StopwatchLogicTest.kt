package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.CountdownState
import com.heretek.dorado_hd.ui.apps.StopwatchEngine
import com.heretek.dorado_hd.ui.apps.StopwatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchLogicTest {

    @Test
    fun `elapsed advances with the wall clock`() {
        val s = StopwatchEngine.start(StopwatchState(), nowWall = 1_000)
        assertEquals(3_000L, s.elapsed(4_000))
    }

    @Test
    fun `pause freezes and resume adds the new span`() {
        val running = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        val paused = StopwatchEngine.pause(running, nowWall = 5_000)
        assertEquals(5_000L, paused.elapsed(9_000))
        val resumed = StopwatchEngine.start(paused, nowWall = 9_000)
        assertEquals(8_000L, resumed.elapsed(12_000))
    }

    @Test
    fun `lap split is total minus the previous total`() {
        var s = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        s = StopwatchEngine.lap(s, nowWall = 3_000)
        s = StopwatchEngine.lap(s, nowWall = 5_000)
        assertEquals(2, s.laps.size)
        assertEquals(3_000L, s.laps[0].splitMs)
        assertEquals(3_000L, s.laps[0].totalMs)
        assertEquals(2_000L, s.laps[1].splitMs)
        assertEquals(5_000L, s.laps[1].totalMs)
    }

    @Test
    fun `lap is disabled while paused`() {
        val paused = StopwatchEngine.pause(StopwatchEngine.start(StopwatchState(), nowWall = 0), nowWall = 2_000)
        assertEquals(paused, StopwatchEngine.lap(paused, nowWall = 3_000))
    }

    @Test
    fun `reset clears only stopwatch state`() {
        var s = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        s = StopwatchEngine.lap(s, nowWall = 1_000)
        assertEquals(StopwatchState(), StopwatchEngine.reset(s))
    }

    @Test
    fun `auto-stops at the twenty-four hour cap`() {
        val running = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        val capped = StopwatchEngine.tick(running, nowWall = StopwatchEngine.MAX_STOPWATCH_MS + 1)
        assertFalse(capped.running)
        assertEquals(StopwatchEngine.MAX_STOPWATCH_MS, capped.accumulatedMs)
        // Re-arming at the cap resets instead of continuing.
        val rearmed = StopwatchEngine.start(capped, nowWall = 100)
        assertTrue(rearmed.running)
        assertEquals(0L, rearmed.accumulatedMs)
    }

    @Test
    fun `countdown wheels wrap and clamp`() {
        assertEquals(StopwatchEngine.countdownFromWheels(23, 59, 0), StopwatchEngine.countdownFromWheels(25, 70, 0))
        assertEquals(0L, StopwatchEngine.countdownFromWheels(-1, 0, 0))
        val adjusted = StopwatchEngine.adjustWheel(CountdownState(setupMs = 0L), unit = 0, delta = -1)
        assertEquals(23L * 3_600_000L, adjusted.setupMs)
    }

    @Test
    fun `countdown starts with a one second display grace`() {
        val cd = StopwatchEngine.startCountdown(CountdownState(setupMs = 65_000), nowWall = 0)
        assertTrue(cd.running)
        assertEquals(60_000L, cd.remaining(5_000))
        assertEquals("01:01", StopwatchEngine.formatCountdown(cd.remaining(5_000) + 1_000))
    }

    @Test
    fun `countdown expires into the alarm`() {
        val cd = StopwatchEngine.startCountdown(CountdownState(setupMs = 1_000), nowWall = 0)
        val expired = StopwatchEngine.tickCountdown(cd, nowWall = 2_000)
        assertFalse(expired.running)
        assertEquals(0L, expired.remainingMs)
        assertTrue(expired.alarming)
        assertEquals(2_000L, expired.alarmStartedAtWall)
        assertFalse(StopwatchEngine.stopAlarm(expired).alarming)
    }

    @Test
    fun `alarm flashes alternating and crescendos to expiry`() {
        assertEquals(0.85, StopwatchEngine.alarmPhase(0).volume, 1e-9)
        assertTrue(StopwatchEngine.alarmPhase(0).flashOn)
        assertFalse(StopwatchEngine.alarmPhase(500).flashOn)
        assertEquals(0.925, StopwatchEngine.alarmPhase(15_000).volume, 1e-9)
        assertFalse(StopwatchEngine.alarmPhase(29_000).expired)
        assertTrue(StopwatchEngine.alarmPhase(29_001).expired)
    }

    @Test
    fun `serialize round trips laps and mode`() {
        var s = StopwatchEngine.start(StopwatchState(), nowWall = 1_000)
        s = StopwatchEngine.lap(s, nowWall = 2_000)
        val cd = CountdownState(setupMs = 30_000, remainingMs = 12_000)
        val blob = StopwatchEngine.serialize(s, cd, StopwatchEngine.MODE_TIMER)
        val restored = StopwatchEngine.restore(blob, nowWall = 4_000)
        assertEquals(s.running, restored.stopwatch.running)
        assertEquals(s.startedAtWall, restored.stopwatch.startedAtWall)
        assertEquals(s.laps, restored.stopwatch.laps)
        assertEquals(30_000L, restored.countdown.setupMs)
        assertEquals(12_000L, restored.countdown.remainingMs)
        assertEquals(StopwatchEngine.MODE_TIMER, restored.mode)
    }

    @Test
    fun `restore catches up wall clock and expires quietly`() {
        val running = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        val runningBlob = StopwatchEngine.serialize(running, CountdownState(), StopwatchEngine.MODE_STOPWATCH)
        val caughtUp = StopwatchEngine.restore(runningBlob, nowWall = 5_000)
        assertTrue(caughtUp.stopwatch.running)
        assertEquals(5_000L, caughtUp.stopwatch.elapsed(5_000))

        val cd = StopwatchEngine.startCountdown(CountdownState(setupMs = 1_000), nowWall = 0)
        val cdBlob = StopwatchEngine.serialize(running, cd, StopwatchEngine.MODE_TIMER)
        val expired = StopwatchEngine.restore(cdBlob, nowWall = 10_000)
        assertFalse(expired.countdown.running)
        assertEquals(0L, expired.countdown.remainingMs)
        assertFalse(expired.countdown.alarming)
    }

    @Test
    fun `restore caps an over-run stopwatch`() {
        val running = StopwatchEngine.start(StopwatchState(), nowWall = 0)
        val blob = StopwatchEngine.serialize(running, CountdownState(), StopwatchEngine.MODE_STOPWATCH)
        val restored = StopwatchEngine.restore(blob, nowWall = StopwatchEngine.MAX_STOPWATCH_MS + 5_000)
        assertFalse(restored.stopwatch.running)
        assertEquals(StopwatchEngine.MAX_STOPWATCH_MS, restored.stopwatch.accumulatedMs)
    }

    @Test
    fun `device formatting`() {
        assertEquals("01:05.03", StopwatchEngine.formatStopwatch(65_030))
        assertEquals("1:02:03.00", StopwatchEngine.formatStopwatch(3_723_000))
        assertEquals("01:05", StopwatchEngine.formatCountdown(65_000))
        assertEquals("1:00:00", StopwatchEngine.formatCountdown(3_600_000))
    }
}
