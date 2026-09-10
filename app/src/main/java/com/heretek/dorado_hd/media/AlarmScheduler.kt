package com.heretek.dorado_hd.media

import java.util.Calendar

/**
 * Pure helper that computes the next-fire epoch-ms for an AlarmManager
 * RTC_WAKEUP based on hour/minute (canon §8). Today if hour:minute hasn't
 * passed, otherwise tomorrow. 7-day weekly recurrences expand to the
 * next matching weekday.
 */
object AlarmScheduler {
    fun nextFireMs(hour: Int, minute: Int, nowMs: Long = System.currentTimeMillis(), daysOfWeek: Int = 0): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (daysOfWeek == 0) {
            if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        // Weekly mask (Sun..Sat bit 0..6). Search up to 7 days ahead.
        for (offset in 0..7) {
            val dow = ((cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + offset) % 7)
            val bit = 1 shl dow
            if (daysOfWeek and bit != 0 && cal.timeInMillis > nowMs) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
