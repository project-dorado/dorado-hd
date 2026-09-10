package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/* ============================== Piano ============================== */

private val PIANO_WHITE_FREQS = listOf(
    261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88,
    523.25, 587.33, 659.25, 698.46, 783.99, 880.00, 987.77,
)

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

    DetailScaffold(title = "piano") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(DoradoTokens.PIANO_KEY_H.dp).background(colors.elevated)) {
                Row(Modifier.fillMaxSize()) {
                    repeat(14) { i ->
                        Box(
                            Modifier
                                .size(width = DoradoTokens.PIANO_KEY_W.dp, height = DoradoTokens.PIANO_KEY_H.dp)
                                .background(Color.White)
                                .pointerInput(i) {
                                    detectTapGestures(onPress = {
                                        if (i in PIANO_WHITE_FREQS.indices) {
                                            synth.playSamples(sineNote(PIANO_WHITE_FREQS[i], 500))
                                            lastPlayed = i
                                        }
                                    })
                                },
                        )
                    }
                }
            }
            if (lastPlayed != null) {
                BasicText(
                    text = "playing ${(lastPlayed ?: 0) + 1}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
            }
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
        while (playing) {
            val period = (60_000L / bpm).toLong() / 4
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
                BasicText(
                    text = "$bpm bpm",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
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
