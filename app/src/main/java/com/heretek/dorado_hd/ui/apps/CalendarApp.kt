package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.AppointmentEntity
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/* ============================== Calendar ============================== */

/**
 * Calendar engine: recurrence expansion, clash detection, month-grid maths and
 * the metadata blob that rides in `AppointmentEntity.notes` (no schema change).
 * Pure Kotlin so the logic is unit-testable without Android.
 */
object CalendarEngine {

    const val META_PREFIX = "[[dorado-cal:v1]]"

    fun encode(meta: CalendarMeta): String = buildString {
        append(META_PREFIX)
        append("allDay=").append(if (meta.allDay) 1 else 0)
        append(";occurs=").append(meta.occurs.name.lowercase(Locale.US))
        append(";status=").append(meta.status.name.lowercase(Locale.US))
        append(";location=").append(escape(meta.location))
        append('\n').append(meta.userNotes)
    }

    fun decode(notes: String?): CalendarMeta {
        if (notes == null || !notes.startsWith(META_PREFIX)) return CalendarMeta(userNotes = notes.orEmpty())
        val nl = notes.indexOf('\n')
        val header = if (nl < 0) notes.substring(META_PREFIX.length) else notes.substring(META_PREFIX.length, nl)
        val body = if (nl < 0) "" else notes.substring(nl + 1)
        val fields = header.split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq) to it.substring(eq + 1)
        }.toMap()
        return CalendarMeta(
            location = unescape(fields["location"].orEmpty()),
            allDay = fields["allDay"] == "1",
            occurs = Occurs.entries.firstOrNull { it.name.equals(fields["occurs"], true) } ?: Occurs.ONCE,
            status = ApptStatus.entries.firstOrNull { it.name.replace("_", "").equals(fields["status"], true) }
                ?: ApptStatus.BUSY,
            userNotes = body,
        )
    }

    /** Expand one entity's recurrences that overlap [fromMs, toMs). */
    fun expand(entity: AppointmentEntity, fromMs: Long, toMs: Long, zone: ZoneId): List<CalendarOccurrence> {
        val meta = decode(entity.notes)
        val duration = when {
            meta.allDay -> (entity.endAt ?: 0L) - entity.startAt
            entity.endAt != null -> entity.endAt - entity.startAt
            else -> 3_600_000L
        }.let { if (it <= 0L) if (meta.allDay) 86_400_000L else 3_600_000L else it }
        val startLocal = Instant.ofEpochMilli(entity.startAt).atZone(zone)
        val out = mutableListOf<CalendarOccurrence>()
        var i = 0
        while (i < 4000) {
            val start = when (meta.occurs) {
                Occurs.ONCE -> if (i == 0) startLocal else break
                Occurs.DAILY -> startLocal.plusDays(i.toLong())
                Occurs.WEEKDAYS -> startLocal.plusDays(i.toLong())
                Occurs.WEEKLY -> startLocal.plusWeeks(i.toLong())
                Occurs.MONTHLY -> startLocal.plusMonths(i.toLong())
                Occurs.ANNUALLY -> startLocal.plusYears(i.toLong())
            }
            if (meta.occurs == Occurs.WEEKDAYS &&
                (start.dayOfWeek == DayOfWeek.SATURDAY || start.dayOfWeek == DayOfWeek.SUNDAY)
            ) {
                i++
                continue
            }
            val at = start.toInstant().toEpochMilli()
            if (at >= toMs) break
            if (at + duration > fromMs) {
                out += CalendarOccurrence(
                    entityId = entity.id,
                    title = entity.title,
                    startAt = at,
                    endAt = at + duration,
                    allDay = meta.allDay,
                    location = meta.location,
                    status = meta.status,
                    notes = meta.userNotes,
                    occurs = meta.occurs,
                )
            }
            i++
        }
        return out
    }

    fun expandAll(
        entities: List<AppointmentEntity>,
        fromMs: Long,
        toMs: Long,
        zone: ZoneId,
    ): List<CalendarOccurrence> =
        entities.flatMap { expand(it, fromMs, toMs, zone) }.sortedWith(compareBy({ it.startAt }, { it.entityId }))

    /** Busy/tentative/OOO events that overlap the candidate; free never clashes. */
    fun hasClash(existing: List<CalendarOccurrence>, candidate: CalendarOccurrence): Boolean {
        if (candidate.status == ApptStatus.FREE) return false
        return existing.any { other ->
            other.entityId != candidate.entityId &&
                other.status != ApptStatus.FREE &&
                candidate.startAt < other.endAt && other.startAt < candidate.endAt
        }
    }

    /** 42 month cells (6 weeks), Sunday-first, null for leading/trailing blanks. */
    fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return (0 until 42).map { i ->
            val day = i - lead + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }

    /** Lane index for each occurrence; overlapping rows get distinct lanes. */
    fun assignColumns(occurrences: List<CalendarOccurrence>): List<CalendarColumn> {
        val lanes = mutableListOf<MutableList<CalendarOccurrence>>()
        val out = mutableListOf<CalendarColumn>()
        for (occ in occurrences.sortedBy { it.startAt }) {
            var lane = lanes.indexOfFirst { it.last().endAt <= occ.startAt }
            if (lane < 0) {
                lanes += mutableListOf(occ)
                lane = lanes.size - 1
            } else {
                lanes[lane] += occ
            }
            out += CalendarColumn(occ, lane)
        }
        return out
    }

    fun timeLabel(ts: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            .format(Instant.ofEpochMilli(ts).atZone(zone))
            .lowercase(Locale.US)

    fun dateLabel(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).format(date)

    private fun escape(s: String): String =
        s.replace("%", "%25").replace(";", "%3B").replace("\n", "%0A")

    private fun unescape(s: String): String =
        s.replace("%0A", "\n").replace("%3B", ";").replace("%25", "%")
}

enum class CalendarView { DAY, AGENDA, MONTH }

enum class Occurs { ONCE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, ANNUALLY }

enum class ApptStatus { FREE, TENTATIVE, BUSY, OUT_OF_OFFICE }

data class CalendarMeta(
    val location: String = "",
    val allDay: Boolean = false,
    val occurs: Occurs = Occurs.ONCE,
    val status: ApptStatus = ApptStatus.BUSY,
    val userNotes: String = "",
)

data class CalendarOccurrence(
    val entityId: Long,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean,
    val location: String,
    val status: ApptStatus,
    val notes: String,
    val occurs: Occurs,
)

data class CalendarColumn(val occurrence: CalendarOccurrence, val lane: Int)

private const val DAY_MS = 86_400_000L

@Composable
fun CalendarApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    val colors = LocalDoradoColors.current
    val appts by graph.calendar.appointments().collectAsState(initial = emptyList())
    val zone = remember { ZoneId.systemDefault() }
    var monthStart by remember { mutableStateOf(AppClock.yearMonth()) }
    var selectedDate by remember { mutableStateOf(AppClock.localDate()) }
    var view by remember { mutableStateOf(CalendarView.DAY) }

    val dayStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayOccs = remember(appts, selectedDate) { CalendarEngine.expandAll(appts, dayStart, dayStart + DAY_MS, zone) }
    val agendaOccs = remember(appts, selectedDate) {
        CalendarEngine.expandAll(appts, dayStart, dayStart + 31 * DAY_MS, zone)
    }
    val monthFrom = monthStart.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val monthTo = monthStart.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val monthOccs = remember(appts, monthStart) { CalendarEngine.expandAll(appts, monthFrom, monthTo, zone) }

    fun shiftDate(days: Long) {
        selectedDate = selectedDate.plusDays(days)
        monthStart = YearMonth.from(selectedDate)
    }

    fun addAt(date: LocalDate, allDay: Boolean, withNotes: Boolean) {
        menus.showPrompt("new appointment", "title") { rawTitle ->
            val title = rawTitle.trim().ifBlank { "untitled" }
            val commit: (String) -> Unit = { notesText ->
                val start = if (allDay) {
                    date.atStartOfDay(zone).toInstant().toEpochMilli()
                } else {
                    date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                }
                val end = start + if (allDay) DAY_MS else 3_600_000L
                scope.launch {
                    graph.calendar.add(
                        title = title,
                        notes = CalendarEngine.encode(
                            CalendarMeta(allDay = allDay, occurs = Occurs.ONCE, userNotes = notesText),
                        ),
                        startAt = start,
                        endAt = end,
                    )
                }
            }
            if (withNotes) {
                menus.showPrompt("notes", "notes") { commit(it) }
            } else {
                commit("")
            }
        }
    }

    DetailScaffold(title = "calendar") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CalendarView.entries.forEach { mode ->
                    val on = mode == view
                    EdgeCropText(
                        text = mode.name.lowercase(Locale.US),
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (on) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .combinedClickable(onClick = { view = mode }, onLongClick = {})
                            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                EdgeCropText(
                    text = "+ new",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                menus.show(
                                    title = "new appointment",
                                    actions = listOf(
                                        MenuAction("timed") { addAt(selectedDate, allDay = false, withNotes = false) },
                                        MenuAction("all-day") { addAt(selectedDate, allDay = true, withNotes = false) },
                                        MenuAction("with notes") { addAt(selectedDate, allDay = false, withNotes = true) },
                                    ),
                                )
                            },
                            onLongClick = {},
                        )
                        .padding(4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(
                    text = "<",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(onClick = { shiftDate(-1) }, onLongClick = {})
                        .padding(4.dp),
                )
                EdgeCropText(
                    text = ControllerDate.label(selectedDate, monthStart, view),
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(
                    text = ">",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(onClick = { shiftDate(1) }, onLongClick = {})
                        .padding(4.dp),
                )
                EdgeCropText(
                    text = "today",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                selectedDate = AppClock.localDate()
                                monthStart = AppClock.yearMonth()
                            },
                            onLongClick = {},
                        )
                        .padding(4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            when (view) {
                CalendarView.DAY -> DayTimeline(dayOccs, selectedDate, zone)
                CalendarView.AGENDA -> AgendaView(agendaOccs, zone)
                CalendarView.MONTH -> MonthGrid(
                    month = monthStart,
                    occurrences = monthOccs,
                    selected = selectedDate,
                    onSelect = { date ->
                        selectedDate = date
                        view = CalendarView.DAY
                    },
                    onAdd = { date -> addAt(date, allDay = false, withNotes = false) },
                    onLongPress = { date, occs ->
                        menus.show(
                            title = CalendarEngine.dateLabel(date),
                            actions = occs.distinctBy { it.entityId }.map { occ ->
                                MenuAction("delete ${occ.title}") { scope.launch { graph.calendar.delete(occ.entityId) } }
                            } + listOf(
                                MenuAction("add timed") { addAt(date, allDay = false, withNotes = false) },
                                MenuAction("add all-day") { addAt(date, allDay = true, withNotes = false) },
                            ),
                        )
                    },
                )
            }
        }
    }
}

private object ControllerDate {
    fun label(selected: LocalDate, month: YearMonth, view: CalendarView): String =
        if (view == CalendarView.MONTH) {
            month.month.name.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) } + " " + month.year
        } else {
            CalendarEngine.dateLabel(selected)
        }
}

@Composable
private fun DayTimeline(
    occurrences: List<CalendarOccurrence>,
    date: LocalDate,
    zone: ZoneId,
) {
    val colors = LocalDoradoColors.current
    val allDay = occurrences.filter { it.allDay }
    val timed = occurrences.filter { !it.allDay }
    val clashing = timed.filter { CalendarEngine.hasClash(timed, it) }.map { it.startAt }.toSet()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (occurrences.isEmpty()) {
            EdgeCropText(
                text = "no appointments",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textInactive,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (allDay.isNotEmpty()) {
            EdgeCropText(text = "all day", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
            allDay.forEach { occ ->
                AppointmentRow(occ, zone, clashing = false)
            }
            Spacer(Modifier.height(4.dp))
        }
        val hours = (0..23).toList()
        hours.forEach { hour ->
            val at = timed.filter {
                Instant.ofEpochMilli(it.startAt).atZone(zone).hour == hour
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                EdgeCropText(
                    text = "%02d:00".format(hour),
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                    modifier = Modifier.width(40.dp).padding(top = 2.dp),
                )
                Column(Modifier.weight(1f)) {
                    at.forEach { occ -> AppointmentRow(occ, zone, clashing = occ.startAt in clashing) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AgendaView(occurrences: List<CalendarOccurrence>, zone: ZoneId) {
    val colors = LocalDoradoColors.current
    val grouped = occurrences.groupBy { Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (grouped.isEmpty()) {
            EdgeCropText(text = "no appointments", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textInactive)
        }
        grouped.forEach { (date, occs) ->
            EdgeCropText(
                text = CalendarEngine.dateLabel(date),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            val clashing = occs.filter { CalendarEngine.hasClash(occs, it) }.map { it.startAt }.toSet()
            occs.forEach { occ -> AppointmentRow(occ, zone, clashing = occ.startAt in clashing) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AppointmentRow(
    occ: CalendarOccurrence,
    zone: ZoneId,
    clashing: Boolean,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeCropText(
                text = if (occ.allDay) "all day" else CalendarEngine.timeLabel(occ.startAt, zone),
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
                modifier = Modifier.width(56.dp),
            )
            EdgeCropText(
                text = occ.title,
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (clashing) colors.accentBright else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (clashing) {
                EdgeCropText(text = "clash", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.accentBright)
            }
            if (occ.occurs != Occurs.ONCE) {
                EdgeCropText(
                    text = occ.occurs.name.lowercase(Locale.US),
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        val sub = listOfNotNull(
            occ.location.ifBlank { null },
            occ.status.name.lowercase(Locale.US).replace('_', ' '),
            occ.notes.ifBlank { null },
        ).joinToString("  ")
        if (sub.isNotEmpty()) {
            EdgeCropText(
                text = sub,
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
                modifier = Modifier.padding(start = 56.dp),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    occurrences: List<CalendarOccurrence>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onLongPress: (LocalDate, List<CalendarOccurrence>) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val byDate = occurrences.groupBy {
        Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val cells = CalendarEngine.monthCells(month)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            listOf("s", "m", "t", "w", "t", "f", "s").forEach { d ->
                EdgeCropText(
                    text = d,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textInactive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val dayOccs = date?.let { byDate[it].orEmpty() } ?: emptyList()
                    val clashing = dayOccs.isNotEmpty() && dayOccs.any { CalendarEngine.hasClash(dayOccs, it) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .background(if (date == selected) colors.tilePressed else Color.Transparent)
                            .combinedClickable(
                                enabled = date != null,
                                onClick = { date?.let(onSelect) },
                                onLongClick = {
                                    if (date != null) {
                                        if (dayOccs.isEmpty()) onAdd(date) else onLongPress(date, dayOccs)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = date?.dayOfMonth?.toString().orEmpty(),
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontWeight = if (date == selected) FontWeight.SemiBold else FontWeight.Light,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = when {
                                    date == null -> colors.textInactive
                                    clashing -> colors.accentBright
                                    dayOccs.isNotEmpty() -> colors.accent
                                    else -> colors.textPrimary
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}
