package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.AlarmClockEngine
import com.heretek.dorado_hd.ui.apps.AlarmSource
import com.heretek.dorado_hd.ui.apps.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 1 alarm-clock rules: minute-match firing, snooze re-arm, the 30-minute
 * bow-out, playlist/radio fallback, and the sleep timer ladder.
 */
class AlarmLogicTest {

    private fun armedAt(hour: Int, minute: Int) = AlarmClockEngine.setAlarmEnabled(
        AlarmClockEngine.setAlarmTime(AlarmClockEngine.initial(), hour, minute),
        true,
    )

    @Test
    fun `minute match fires once per minute`() {
        val fired = AlarmClockEngine.tick(armedAt(7, 0), 7 * 60)
        assertTrue(fired.ringing)
        assertEquals(AlarmSource.BUZZER, fired.ringingSource)
        val sameMinute = AlarmClockEngine.tick(fired, 7 * 60)
        assertTrue(sameMinute.ringing)
        assertEquals("same minute must tick, not re-fire", 1, sameMinute.alertSeconds)
        val otherMinute = AlarmClockEngine.tick(armedAt(7, 0), 7 * 60 + 1)
        assertFalse(otherMinute.ringing)
    }

    @Test
    fun `minute match re-arms after the fired minute passes`() {
        var state = AlarmClockEngine.tick(armedAt(7, 0), 7 * 60)
        assertTrue(state.ringing)
        state = AlarmClockEngine.dismiss(state)
        state = AlarmClockEngine.tick(state, 7 * 60 + 1)
        assertEquals(-1, state.lastFiredMinuteOfDay)
        state = AlarmClockEngine.tick(state, 7 * 60 + 1440)
        assertTrue("next day must fire again", state.ringing)
    }

    @Test
    fun `snooze defers and re-arms`() {
        var state = AlarmClockEngine.tick(armedAt(7, 0), 7 * 60)
        state = AlarmClockEngine.setSnoozeMinutes(state, 10)
        state = AlarmClockEngine.snooze(state, 7 * 60 + 1)
        assertFalse(state.ringing)
        assertTrue(state.snoozed)
        assertEquals(7 * 60 + 11, state.snoozeMinuteOfDay)
        state = AlarmClockEngine.tick(state, 7 * 60 + 10)
        assertFalse("snooze target not reached yet", state.ringing)
        state = AlarmClockEngine.tick(state, 7 * 60 + 11)
        assertTrue("snooze target must fire", state.ringing)
    }

    @Test
    fun `buzzer bows out after thirty minutes and disables the alarm`() {
        var state = AlarmClockEngine.tick(armedAt(7, 0), 7 * 60)
        repeat(29 * 60) { state = AlarmClockEngine.tick(state, 7 * 60) }
        assertTrue("29 minutes is still ringing", state.ringing)
        repeat(60) { state = AlarmClockEngine.tick(state, 7 * 60) }
        assertFalse("30 minutes bows out", state.ringing)
        assertFalse(state.alarmEnabled)
        assertEquals(0, state.alertSeconds)
    }

    @Test
    fun `playlist source falls back to the buzzer when unavailable`() {
        var state = AlarmClockEngine.setAlarmSource(armedAt(7, 0), AlarmSource.PLAYLIST)
        state = AlarmClockEngine.tick(state, 7 * 60, playlistAvailable = false)
        assertTrue(state.ringing)
        assertEquals(AlarmSource.BUZZER, state.ringingSource)
        assertTrue(state.contentError)
    }

    @Test
    fun `radio source stays radio when available`() {
        var state = AlarmClockEngine.setAlarmSource(armedAt(7, 0), AlarmSource.RADIO)
        state = AlarmClockEngine.tick(state, 7 * 60, radioAvailable = true)
        assertEquals(AlarmSource.RADIO, state.ringingSource)
        assertFalse(state.contentError)
    }

    @Test
    fun `sleep countdown pings then runs out of time`() {
        var state = AlarmClockEngine.setSleep(AlarmClockEngine.initial(), minutes = 1, volume = 2)
        repeat(60) { state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.0) }
        assertEquals(0, state.sleepSecondsLeft)
        assertTrue(AlarmClockEngine.isSleepPinging(state))
        repeat(9) { state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.0) }
        assertEquals(-9, state.sleepSecondsLeft)
        assertTrue("ping window spans nine seconds", AlarmClockEngine.isSleepPinging(state))
        state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.0)
        assertEquals(-10, state.sleepSecondsLeft)
        assertFalse(AlarmClockEngine.isSleepPinging(state))
        assertTrue(state.sleepOutOfTime)
    }

    @Test
    fun `shake over one g restarts the sleep countdown`() {
        var state = AlarmClockEngine.setSleep(AlarmClockEngine.initial(), minutes = 1, volume = 1)
        repeat(60) { state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.0) }
        state = AlarmClockEngine.sleepTick(state, accelDeltaG = 1.4, seconds = 0)
        assertEquals(60, state.sleepSecondsLeft)
        assertFalse(state.sleepOutOfTime)
        // Below the threshold the countdown keeps ticking.
        state = AlarmClockEngine.sleepTick(state, accelDeltaG = 0.8)
        assertEquals(59, state.sleepSecondsLeft)
    }

    @Test
    fun `proxy g recovers a raw one g smoothed step`() {
        val smoothing = 0.85f
        val stepDeg = 90f * (1f - smoothing)
        val g = AlarmClockEngine.proxyG(0f, 0f, 0f, stepDeg, smoothing)
        assertEquals(1.0, g, 1e-6)
        assertEquals(0.0, AlarmClockEngine.proxyG(10f, 10f, 10f, 10f), 1e-9)
    }

    @Test
    fun `clock formats in twelve and twenty four hour`() {
        assertEquals("07:05", AlarmClockEngine.formatClock(7 * 60 + 5, ClockFormat.H24))
        assertEquals("7:05 am", AlarmClockEngine.formatClock(7 * 60 + 5, ClockFormat.H12))
        assertEquals("12:00 pm", AlarmClockEngine.formatClock(12 * 60, ClockFormat.H12))
        assertEquals("12:30 am", AlarmClockEngine.formatClock(30, ClockFormat.H12))
        assertEquals("0:05", AlarmClockEngine.formatCountdown(5))
        assertEquals("20:00", AlarmClockEngine.formatCountdown(1200))
        assertEquals("0:00", AlarmClockEngine.formatCountdown(-4))
    }

    @Test
    fun `minutes until alarm follows the snooze target`() {
        var state = armedAt(7, 0)
        assertEquals(60, AlarmClockEngine.minutesUntilAlarm(state, 6 * 60))
        state = AlarmClockEngine.snooze(
            AlarmClockEngine.tick(state, 7 * 60),
            7 * 60,
        )
        assertEquals(10, AlarmClockEngine.minutesUntilAlarm(state, 7 * 60))
        assertEquals(-1, AlarmClockEngine.minutesUntilAlarm(AlarmClockEngine.initial(), 0))
    }

    @Test
    fun `settings round trip through the state blob`() {
        var state = AlarmClockEngine.initial()
        state = AlarmClockEngine.setAlarmTime(state, 6, 45)
        state = AlarmClockEngine.setClockFormat(state, ClockFormat.H24)
        state = AlarmClockEngine.setSnoozeMinutes(state, 25)
        state = AlarmClockEngine.setSleep(state, minutes = 45, volume = 3)
        state = state.copy(alarmSource = AlarmSource.RADIO, radioPreset = 7L)
        val parsed = AlarmClockEngine.parse(AlarmClockEngine.serialize(state))
        assertEquals(ClockFormat.H24, parsed.clockFormat)
        assertEquals(6 * 60 + 45, parsed.alarmMinuteOfDay)
        assertEquals(25, parsed.snoozeMinutes)
        assertEquals(45, parsed.sleepMinutes)
        assertEquals(3, parsed.sleepVolume)
        assertEquals(AlarmSource.RADIO, parsed.alarmSource)
        assertEquals(7L, parsed.radioPreset)
        assertEquals(45 * 60, parsed.sleepSecondsLeft)
        assertEquals(AlarmClockEngine.initial(), AlarmClockEngine.parse(null))
    }
}
