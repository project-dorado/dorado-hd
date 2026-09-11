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
private fun alarmRequestCode(id: Long): Int = (id xor (id ushr 32)).toInt()

@Composable
fun AlarmClockApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val menus = LocalContextMenu.current
    val alarms by graph.alarms.alarms().collectAsState(initial = emptyList())

    fun alarmPi(alarm: com.heretek.dorado_hd.data.db.AlarmEntity): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            context,
            alarmRequestCode(alarm.id),
            android.content.Intent(context, com.heretek.dorado_hd.media.AlarmReceiver::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("alarmKind", alarm.alarmKind)
                putExtra("refId", alarm.refId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(alarm: com.heretek.dorado_hd.data.db.AlarmEntity) {
        if (!alarm.enabled) return
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt = com.heretek.dorado_hd.media.AlarmScheduler.nextFireMs(alarm.hour, alarm.minute, daysOfWeek = alarm.daysOfWeek)
        am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, alarmPi(alarm))
    }

    fun cancel(alarm: com.heretek.dorado_hd.data.db.AlarmEntity) {
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        am.cancel(alarmPi(alarm))
    }

    fun parseTime(input: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})").find(input.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    DetailScaffold(title = "alarm clock") {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = {
                            menus.showPrompt("new alarm", "hh:mm (24h)") { value ->
                                val (h, m) = parseTime(value) ?: (7 to 0)
                                scope.launch {
                                    val entity = com.heretek.dorado_hd.data.db.AlarmEntity(
                                        hour = h, minute = m, enabled = true,
                                        label = "alarm", alarmKind = 0, refId = 0, daysOfWeek = 0,
                                    )
                                    val id = graph.alarms.add(entity)
                                    schedule(entity.copy(id = id))
                                }
                            }
                        },
                        onLongClick = {},
                    ),
            ) {
                EdgeCropText(text = "+ new alarm", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
            KineticList(
                items = alarms,
                key = { it.id },
                letter = { firstLetterOf(it.label) },
                rowContent = { alarm, _ ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        val enable = !alarm.enabled
                                        graph.alarms.setEnabled(alarm.id, enable)
                                        if (enable) schedule(alarm.copy(enabled = true)) else cancel(alarm)
                                    }
                                },
                                onLongClick = {
                                    menus.show(
                                        title = "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label),
                                        actions = listOf(
                                            MenuAction("edit time") {
                                                menus.showPrompt(
                                                    "edit alarm",
                                                    "%02d:%02d".format(alarm.hour, alarm.minute),
                                                ) { value ->
                                                    val (h, m) = parseTime(value) ?: (alarm.hour to alarm.minute)
                                                    scope.launch {
                                                        graph.alarms.setTime(alarm.id, h, m)
                                                        val updated = alarm.copy(hour = h, minute = m)
                                                        if (updated.enabled) schedule(updated) else cancel(updated)
                                                    }
                                                }
                                            },
                                            MenuAction("delete") {
                                                scope.launch {
                                                    cancel(alarm)
                                                    graph.alarms.delete(alarm.id)
                                                }
                                            },
                                        ),
                                    )
                                },
                            )
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            EdgeCropText(
                                text = "%02d:%02d".format(alarm.hour, alarm.minute),
                                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                                color = if (alarm.enabled) LocalDoradoColors.current.textPrimary else LocalDoradoColors.current.textInactive,
                            )
                            EdgeCropText(text = alarm.label, fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.textSecondary)
                        }
                        EdgeCropText(text = if (alarm.enabled) "on" else "off", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                    }
                },
            )
        }
    }
}

/* ============================== Calendar ============================== */
