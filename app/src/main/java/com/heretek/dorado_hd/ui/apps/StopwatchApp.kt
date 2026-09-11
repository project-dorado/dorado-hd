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
fun StopwatchApp() {
    val colors = LocalDoradoColors.current
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var accumulated by remember { mutableStateOf(0L) }
    var nowMs by remember { mutableStateOf(0L) }
    var lastLapMs by remember { mutableStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) {
            nowMs = System.currentTimeMillis() - startedAt + accumulated
            delay(33)
        }
    }

    DetailScaffold(title = "stopwatch") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = formatTimeCs(nowMs),
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                    color = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EdgeCropText(
                    text = if (running) "stop" else "start",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (running) {
                                accumulated += System.currentTimeMillis() - startedAt
                                running = false
                            } else {
                                startedAt = System.currentTimeMillis()
                                running = true
                            }
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
                EdgeCropText(
                    text = "lap",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = if (running) colors.accent else colors.textInactive,
                    modifier = Modifier.combinedClickable(
                        enabled = running,
                        onClick = {
                            laps.add(0, nowMs - lastLapMs)
                            lastLapMs = nowMs
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
                EdgeCropText(
                    text = "reset",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textInactive,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            running = false; accumulated = 0; laps.clear(); nowMs = 0; lastLapMs = 0
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Split times, newest first, in a scrolling list so any number of
            // laps stays reachable.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                laps.forEachIndexed { index, split ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        EdgeCropText(
                            text = "lap ${laps.size - index}",
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        EdgeCropText(text = formatTimeCs(split), fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.7f)
                    }
                }
            }
        }
    }
}

private fun formatTimeCs(ms: Long): String {
    val s = ms.coerceAtLeast(0)
    val h = s / 3_600_000; val m = (s % 3_600_000) / 60_000; val sec = (s % 60_000) / 1000; val cs = (s % 1000) / 10
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, sec, cs) else "%d:%02d.%02d".format(m, sec, cs)
}

private fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/* ============================== Metronome ============================== */
