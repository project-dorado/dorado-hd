package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.AlarmClockEngine
import com.heretek.dorado_hd.ui.apps.AlarmScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-20 / A-21 regression: exact-alarm revocation chooses a safe schedule
 * mode, and the sleep-timer state survives the persistence round trip.
 */
class AlarmPersistenceTest {

    @Test
    fun `sleep timer enabled and remaining are serialized`() {
        val running = AlarmClockEngine.sleepTick(
            AlarmClockEngine.setSleep(AlarmClockEngine.initial(), minutes = 20, volume = 2),
            accelDeltaG = 0.0,
            seconds = 5,
        )
        val restored = AlarmClockEngine.parse(AlarmClockEngine.serialize(running))
        assertTrue(restored.sleepEnabled)
        assertEquals(20 * 60 - 5, restored.sleepSecondsLeft)
        assertFalse(restored.sleepOutOfTime)
        assertEquals(20, restored.sleepMinutes)
    }

    @Test
    fun `legacy blobs without sleep fields parse to a disabled timer`() {
        val legacy = listOf("H12", "5", "false", "420", "BUZZER", "10", "20", "2", "0", "0")
            .joinToString(";")
        val parsed = AlarmClockEngine.parse(legacy)
        assertFalse(parsed.sleepEnabled)
        assertEquals(20 * 60, parsed.sleepSecondsLeft)
        assertEquals(420, parsed.alarmMinuteOfDay)
    }

    @Test
    fun `schedule mode avoids exact alarms once the permission is revoked`() {
        assertEquals(AlarmScheduleMode.EXACT, AlarmClockEngine.scheduleMode(sdkInt = 30, canScheduleExact = false))
        assertEquals(AlarmScheduleMode.EXACT, AlarmClockEngine.scheduleMode(sdkInt = 34, canScheduleExact = true))
        assertEquals(AlarmScheduleMode.WINDOW, AlarmClockEngine.scheduleMode(sdkInt = 31, canScheduleExact = false))
        assertEquals(AlarmScheduleMode.WINDOW, AlarmClockEngine.scheduleMode(sdkInt = 37, canScheduleExact = false))
    }

    @Test
    fun `out of time sleep state persists too`() {
        var state = AlarmClockEngine.setSleep(AlarmClockEngine.initial(), minutes = 1, volume = 0)
        state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.0, seconds = 60 + AlarmClockEngine.SLEEP_OUT_SECONDS)
        assertTrue(state.sleepOutOfTime)
        val restored = AlarmClockEngine.parse(AlarmClockEngine.serialize(state))
        assertTrue(restored.sleepOutOfTime)
    }
}
