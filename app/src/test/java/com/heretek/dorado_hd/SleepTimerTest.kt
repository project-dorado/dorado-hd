package com.heretek.dorado_hd

import com.heretek.dorado_hd.media.SleepTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun `minutes arm a deadline and non-positive values disarm`() {
        assertEquals(30 * 60_000L, SleepTimer.deadlineMs(0L, 30))
        assertEquals(1_000L + 15 * 60_000L, SleepTimer.deadlineMs(1_000L, 15))
        assertEquals(0L, SleepTimer.deadlineMs(1_000L, 0))
        assertEquals(0L, SleepTimer.deadlineMs(1_000L, -5))
    }

    @Test
    fun `remaining counts down and never goes negative`() {
        val deadline = SleepTimer.deadlineMs(0L, 2)
        assertEquals(120_000L, SleepTimer.remainingMs(deadline, 0L))
        assertEquals(60_000L, SleepTimer.remainingMs(deadline, 60_000L))
        assertEquals(0L, SleepTimer.remainingMs(deadline, 200_000L))
        assertEquals(0L, SleepTimer.remainingMs(0L, 5L))
    }

    @Test
    fun `expiry fires at the deadline and never when disarmed`() {
        val deadline = SleepTimer.deadlineMs(10_000L, 1)
        assertFalse(SleepTimer.expired(deadline, 69_999L))
        assertTrue(SleepTimer.expired(deadline, 70_000L))
        assertTrue(SleepTimer.expired(deadline, 99_000L))
        assertFalse(SleepTimer.expired(0L, Long.MAX_VALUE))
    }
}
