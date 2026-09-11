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
fun MetronomeApp() {
    val colors = LocalDoradoColors.current
    var bpm by remember { mutableStateOf(120) }
    var beatsPerBar by remember { mutableStateOf(4) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    var beat by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { synth.stop() }
    }

    // One loop drives both the audible click and the beat readout and re-reads
    // bpm every beat, so a tempo change retimes the running metronome instead
    // of leaving display and clicks permanently desynced.
    LaunchedEffect(running, bpm, beatsPerBar) {
        if (!running) return@LaunchedEffect
        synth.start()
        var beatIndex = 0
        while (isActive) {
            synth.playSamples(metronomeClick(accent = beatIndex % beatsPerBar == 0))
            beat = beatIndex % beatsPerBar
            beatIndex++
            delay(60_000L / bpm.coerceIn(30, 300))
        }
    }

    DetailScaffold(title = "metronome") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "$bpm bpm",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp * 2,
                    color = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(-10, -5, -1, 1, 5, 10).forEach { step ->
                    EdgeCropText(
                        text = if (step > 0) "+$step" else "$step",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.combinedClickable(
                            onClick = { bpm = (bpm + step).coerceIn(30, 300) },
                            onLongClick = {},
                        ).padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4, 6).forEach { n ->
                    EdgeCropText(
                        text = "$n/4",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (n == beatsPerBar) colors.accent else colors.textInactive,
                        modifier = Modifier.combinedClickable(
                            onClick = { beatsPerBar = n },
                            onLongClick = {},
                        ).padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            EdgeCropText(
                text = if (running) "stop" else "start",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        running = !running
                        if (!running) beat = 0
                    },
                    onLongClick = {},
                ).padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(text = "beat ${beat + 1}", fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.6f, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ============================== Level ============================== */
