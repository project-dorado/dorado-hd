package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.NoteEntity
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ============================== Calculator ============================== */
@Composable
fun CalendarApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    var monthStart by remember { mutableStateOf(java.time.YearMonth.now()) }
    val appts by graph.calendar.appointments().collectAsState(initial = emptyList())

    DetailScaffold(title = "calendar") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(text = "<", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent,
                    modifier = Modifier.combinedClickable(onClick = { monthStart = monthStart.minusMonths(1) }, onLongClick = {}))
                EdgeCropText(text = "%s %d".format(monthStart.month.name.lowercase().replaceFirstChar { it.uppercase() }, monthStart.year), fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    modifier = Modifier.weight(1f))
                EdgeCropText(text = ">", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent,
                    modifier = Modifier.combinedClickable(onClick = { monthStart = monthStart.plusMonths(1) }, onLongClick = {}))
            }
            Spacer(Modifier.height(8.dp))
            MonthGrid(monthStart, appts)
        }
    }
}

@Composable
private fun MonthGrid(monthStart: java.time.YearMonth, appts: List<com.heretek.dorado_hd.data.db.AppointmentEntity>) {
    val firstOfMonth = monthStart.atDay(1)
    val daysInMonth = monthStart.lengthOfMonth()
    val firstWeekday = (firstOfMonth.dayOfWeek.value % 7) // Sunday=0
    val cells = firstWeekday + daysInMonth
    val rows = (cells + 6) / 7
    // Key by full date: a day-of-month map made every August 5 light up in
    // September (and every other month).
    val apptsByDate = appts.groupBy {
        java.time.Instant.ofEpochMilli(it.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current

    fun addAppointment(date: java.time.LocalDate) {
        menus.showPrompt("appointment", "title") { title ->
            scope.launch {
                graph.calendar.add(
                    title = title.ifBlank { "untitled" },
                    notes = null,
                    startAt = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endAt = null,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 7) {
                    val dayIndex = r * 7 + c - firstWeekday + 1
                    val valid = dayIndex in 1..daysInMonth
                    val date = if (valid) monthStart.atDay(dayIndex) else null
                    val dayAppts = date?.let { apptsByDate[it].orEmpty() } ?: emptyList()
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .background(if (valid) colors.elevated else Color.Transparent)
                            .combinedClickable(
                                enabled = valid,
                                onClick = { date?.let { addAppointment(it) } },
                                onLongClick = {
                                    if (date != null && dayAppts.isNotEmpty()) {
                                        menus.show(
                                            title = date.toString(),
                                            actions = dayAppts.map { a ->
                                                MenuAction("delete ${a.title}") {
                                                    scope.launch { graph.calendar.delete(a.id) }
                                                }
                                            } + MenuAction("add appointment") { addAppointment(date) },
                                        )
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (valid) dayIndex.toString() else "",
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (valid && dayAppts.isNotEmpty()) colors.accent else colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
