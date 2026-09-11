package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.db.AppointmentEntity
import com.heretek.dorado_hd.ui.apps.ApptStatus
import com.heretek.dorado_hd.ui.apps.CalendarEngine
import com.heretek.dorado_hd.ui.apps.CalendarMeta
import com.heretek.dorado_hd.ui.apps.CalendarOccurrence
import com.heretek.dorado_hd.ui.apps.Occurs
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLogicTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun appt(
        id: Long,
        title: String,
        start: Long,
        end: Long?,
        meta: CalendarMeta = CalendarMeta(),
    ): AppointmentEntity =
        AppointmentEntity(id = id, title = title, notes = CalendarEngine.encode(meta), startAt = start, endAt = end)

    private fun occ(start: Long, end: Long, status: ApptStatus = ApptStatus.BUSY, id: Long = 1L) =
        CalendarOccurrence(id, "t", start, end, false, "", status, "", Occurs.ONCE)

    @Test
    fun `metadata blob round trips and escapes separators`() {
        val meta = CalendarMeta(
            location = "room 3; east",
            allDay = true,
            occurs = Occurs.WEEKLY,
            status = ApptStatus.TENTATIVE,
            userNotes = "bring\nnotes",
        )
        assertEquals(meta, CalendarEngine.decode(CalendarEngine.encode(meta)))
    }

    @Test
    fun `plain appointment notes decode as user notes`() {
        assertEquals(CalendarMeta(userNotes = "lunch"), CalendarEngine.decode("lunch"))
    }

    @Test
    fun `once events appear exactly once`() {
        val e = appt(1, "one", at(2026, 1, 5, 9), at(2026, 1, 5, 10))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 1, 1), at(2026, 2, 1), zone)
        assertEquals(1, out.size)
        assertEquals("one", out.first().title)
    }

    @Test
    fun `daily recurrence expands every day`() {
        val e = appt(1, "d", at(2026, 1, 5, 9), at(2026, 1, 5, 10), CalendarMeta(occurs = Occurs.DAILY))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 1, 5), at(2026, 1, 8), zone)
        assertEquals(3, out.size)
    }

    @Test
    fun `weekdays recurrence skips the weekend`() {
        // 2026-01-09 is a Friday; the next working day is Monday the 12th.
        val e = appt(1, "w", at(2026, 1, 9, 9), at(2026, 1, 9, 10), CalendarMeta(occurs = Occurs.WEEKDAYS))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 1, 9), at(2026, 1, 13), zone)
        assertEquals(2, out.size)
        assertEquals(12, java.time.Instant.ofEpochMilli(out.last().startAt).atZone(zone).dayOfMonth)
    }

    @Test
    fun `weekly recurrence adds seven days`() {
        val e = appt(1, "w", at(2026, 1, 5, 9), at(2026, 1, 5, 10), CalendarMeta(occurs = Occurs.WEEKLY))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 1, 5), at(2026, 1, 26, 0), zone)
        assertEquals(3, out.size)
    }

    @Test
    fun `monthly recurrence clamps short months`() {
        val e = appt(1, "m", at(2026, 1, 31, 9), at(2026, 1, 31, 10), CalendarMeta(occurs = Occurs.MONTHLY))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 1, 1), at(2026, 4, 1), zone)
        assertEquals(3, out.size)
        assertEquals("2026-02-28", java.time.Instant.ofEpochMilli(out[1].startAt).atZone(zone).toLocalDate().toString())
    }

    @Test
    fun `annual recurrence keeps february twenty-ninth on leap years`() {
        val e = appt(1, "a", at(2024, 2, 29, 9), at(2024, 2, 29, 10), CalendarMeta(occurs = Occurs.ANNUALLY))
        val out = CalendarEngine.expandAll(listOf(e), at(2024, 1, 1), at(2029, 1, 1), zone)
        assertEquals(5, out.size)
        assertTrue(
            out.any { java.time.Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate().toString() == "2028-02-29" },
        )
    }

    @Test
    fun `recurring occurrence straddling midnight overlaps the next day`() {
        val e = appt(1, "night", at(2026, 2, 5, 23), at(2026, 2, 6, 1), CalendarMeta(occurs = Occurs.DAILY))
        val out = CalendarEngine.expandAll(listOf(e), at(2026, 2, 6, 0), at(2026, 2, 7, 0), zone)
        assertTrue(out.isNotEmpty())
        assertEquals(at(2026, 2, 5, 23), out.first().startAt)
    }

    @Test
    fun `clash ignores free events and touching intervals`() {
        val candidate = occ(at(2026, 1, 5, 10), at(2026, 1, 5, 11), ApptStatus.BUSY, id = 2L)
        val free = occ(at(2026, 1, 5, 10, 30), at(2026, 1, 5, 10, 45), ApptStatus.FREE)
        assertFalse(CalendarEngine.hasClash(listOf(free), candidate))
        val busy = occ(at(2026, 1, 5, 10, 30), at(2026, 1, 5, 10, 45), ApptStatus.BUSY)
        assertTrue(CalendarEngine.hasClash(listOf(busy), candidate))
        val after = occ(at(2026, 1, 5, 11), at(2026, 1, 5, 12), ApptStatus.BUSY)
        assertFalse(CalendarEngine.hasClash(listOf(after), candidate))
    }

    @Test
    fun `free candidate never clashes`() {
        val candidate = occ(at(2026, 1, 5, 10), at(2026, 1, 5, 11), ApptStatus.FREE, id = 2L)
        val busy = occ(at(2026, 1, 5, 10, 30), at(2026, 1, 5, 10, 45), ApptStatus.BUSY)
        assertFalse(CalendarEngine.hasClash(listOf(busy), candidate))
    }

    @Test
    fun `month cells handle leap february and thursday jan first`() {
        val feb = CalendarEngine.monthCells(YearMonth.of(2024, 2))
        assertEquals(42, feb.size)
        assertEquals(29, feb.count { it != null })
        assertEquals(4, feb.indexOfFirst { it != null })
        assertEquals("2024-02-01", feb[4].toString())

        val jan = CalendarEngine.monthCells(YearMonth.of(2026, 1))
        assertEquals("2026-01-01", jan[4].toString())
        assertEquals(31, jan.count { it != null })
    }

    @Test
    fun `columns separate overlapping appointments and reuse free lanes`() {
        val a = occ(at(2026, 1, 5, 9), at(2026, 1, 5, 11), id = 1L)
        val b = occ(at(2026, 1, 5, 9, 30), at(2026, 1, 5, 10, 30), id = 2L)
        val c = occ(at(2026, 1, 5, 10, 45), at(2026, 1, 5, 11, 15), id = 3L)
        val columns = CalendarEngine.assignColumns(listOf(a, b, c))
        assertEquals(listOf(0, 1, 1), columns.map { it.lane })
    }

    @Test
    fun `expand respects the window edges`() {
        val e = appt(1, "later", at(2026, 3, 1, 9), at(2026, 3, 1, 10))
        assertTrue(CalendarEngine.expandAll(listOf(e), at(2026, 1, 1), at(2026, 2, 1), zone).isEmpty())
        assertEquals(1, CalendarEngine.expandAll(listOf(e), at(2026, 1, 1), at(2026, 3, 2, 0), zone).size)
    }
}
