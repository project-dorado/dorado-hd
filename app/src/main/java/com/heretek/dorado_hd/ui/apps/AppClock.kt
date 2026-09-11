package com.heretek.dorado_hd.ui.apps

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Wall-clock seam for mini-apps. Production uses the system clock; tests
 * freeze it so clock-bearing entry screens (alarm, calendar, weather, notes)
 * can be captured as deterministic golden screenshots.
 */
object AppClock {
    @Volatile
    var millis: () -> Long = { System.currentTimeMillis() }

    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()

    fun localTime(): LocalTime = Instant.ofEpochMilli(millis()).atZone(zone).toLocalTime()

    fun localDate(): LocalDate = Instant.ofEpochMilli(millis()).atZone(zone).toLocalDate()

    fun yearMonth(): YearMonth = YearMonth.from(localDate())

    fun reset() {
        millis = { System.currentTimeMillis() }
        zone = ZoneId.systemDefault()
    }
}
