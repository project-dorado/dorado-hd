package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/* ============================== Piano ============================== */

private val PIANO_WHITE_FREQS = listOf(
    261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88,
    523.25, 587.33, 659.25, 698.46, 783.99, 880.00, 987.77,
)

/** White-key indices that carry a black key to their upper right. */
private val BLACK_KEY_AFTER = setOf(0, 1, 3, 4, 5, 7, 8, 10, 11, 12)

@Composable
fun PianoApp() {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }
    var lastPlayed by remember { mutableStateOf<Int?>(null) }

    fun playIndex(whiteIndex: Int, black: Boolean) {
        val base = PIANO_WHITE_FREQS.getOrNull(whiteIndex) ?: return
        val freq = if (black) base * 2.0.pow(1.0 / 12.0) else base
        lastPlayed = whiteIndex
        scope.launch { synth.playSamples(sineNote(freq, 500)) }
    }

    DetailScaffold(title = "piano") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.PIANO_KEY_H.dp)
                    .background(colors.elevated),
            ) {
                val whiteW = maxWidth / PIANO_WHITE_FREQS.size
                Row(Modifier.fillMaxSize()) {
                    repeat(PIANO_WHITE_FREQS.size) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (lastPlayed == i) colors.accent else Color.White)
                                .pointerInput(i) {
                                    detectTapGestures(onPress = { playIndex(i, black = false) })
                                },
                        )
                    }
                }
                // Black keys overlay the white-key boundaries.
                BLACK_KEY_AFTER.forEach { i ->
                    if (i + 1 >= PIANO_WHITE_FREQS.size) return@forEach
                    Box(
                        Modifier
                            .offset(x = whiteW * (i + 1) - DoradoTokens.PIANO_BLACK_W.dp / 2)
                            .size(width = DoradoTokens.PIANO_BLACK_W.dp, height = DoradoTokens.PIANO_BLACK_H.dp)
                            .background(Color.Black)
                            .pointerInput(i) {
                                detectTapGestures(onPress = { playIndex(i, black = true) })
                            },
                    )
                }
            }
            BasicText(
                text = lastPlayed?.let { "playing ${it + 1}" } ?: "tap a key",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
        }
    }
}

/* ============================== Drum Machine ============================== */

@Composable
fun DrumMachineApp() {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    var bpm by remember { mutableStateOf(120) }
    var playing by remember { mutableStateOf(false) }
    val trackNames = remember { listOf("kick", "snare", "hat") }
    // Snapshot-backed so step toggles recompose the grid.
    val grid = remember {
        mutableStateListOf<Boolean>().apply { repeat(trackNames.size * 16) { add(false) } }
    }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    LaunchedEffect(playing, bpm) {
        // Re-read bpm every bar so tempo changes retime the running loop.
        while (playing) {
            val period = (60_000L / bpm.coerceIn(30, 300)).toLong() / 4
            for (s in 0 until 16) {
                if (!isActive) return@LaunchedEffect
                delay(period)
                for (r in trackNames.indices) {
                    if (grid[r * 16 + s]) synth.playSamples(
                        when (r) {
                            0 -> kickDrum()
                            1 -> snareDrum()
                            else -> hatDrum()
                        },
                    )
                }
            }
        }
    }

    DetailScaffold(title = "drum machine") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(
                    text = "$bpm bpm",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    modifier = Modifier.weight(1f),
                )
                listOf(-10, -1, 1, 10).forEach { delta ->
                    EdgeCropText(
                        text = if (delta > 0) "+$delta" else "$delta",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .pointerInput(Unit) { detectTapGestures { bpm = (bpm + delta).coerceIn(30, 300) } },
                    )
                }
                BasicText(
                    text = if (playing) "stop" else "start",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { playing = !playing } },
                )
            }
            Spacer(Modifier.height(8.dp))
            trackNames.forEachIndexed { rowIdx, name ->
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    BasicText(
                        text = name,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                        modifier = Modifier.size(width = 32.dp, height = 24.dp),
                    )
                    repeat(16) { stepIdx ->
                        Box(
                            Modifier
                                .size(width = 18.dp, height = 24.dp)
                                .padding(horizontal = 1.dp)
                                .background(if (grid[rowIdx * 16 + stepIdx]) colors.accent else colors.tile)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        val i = rowIdx * 16 + stepIdx
                                        grid[i] = !grid[i]
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
