package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun spillColor(index: Int, colors: DoradoColors): Color = when (index) {
    0 -> DoradoAccent.PINK.primary
    1 -> DoradoAccent.ORANGE.primary
    2 -> DoradoAccent.CYAN.primary
    3 -> DoradoAccent.LIME.primary
    4 -> DoradoAccent.PURPLE.primary
    else -> colors.accentBright
}

private fun clockLabel(ms: Long): String {
    val total = (ms / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun ColorSpillApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var game by remember { mutableStateOf<ColorSpillEngine.SpillState?>(null) }
    var saved by remember { mutableStateOf<ColorSpillEngine.SpillState?>(null) }
    var recorded by remember { mutableStateOf(false) }
    val best by graph.games.top("color-spill", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        saved = graph.appState.get("color-spill")?.let { ColorSpillEngine.decode(it) }
    }

    LaunchedEffect(game) {
        val current = game ?: return@LaunchedEffect
        if (!current.won && !current.lost) {
            graph.appState.put("color-spill", ColorSpillEngine.encode(current))
        }
    }

    LaunchedEffect(game?.won, game?.lost) {
        val current = game ?: return@LaunchedEffect
        if ((current.won || current.lost) && !recorded) {
            recorded = true
            saved = null
            scope.launch {
                if (current.won) {
                    graph.games.record("color-spill", ColorSpillEngine.score(current), "level ${current.level}")
                }
                graph.appState.clear("color-spill")
            }
        }
    }

    LaunchedEffect(game?.level, game?.won, game?.lost, screen) {
        while (screen == "game") {
            val current = game ?: break
            if (current.won || current.lost) break
            delay(1000)
            game = ColorSpillEngine.step(current, 1000)
        }
    }

    fun start(level: Int) {
        game = ColorSpillEngine.newGame(level = level, seed = System.currentTimeMillis().toInt())
        recorded = false
        screen = "game"
    }

    fun resume() {
        val restored = saved ?: return
        game = restored
        recorded = false
        screen = "game"
    }

    fun pick(index: Int) {
        val current = game ?: return
        val next = ColorSpillEngine.floodFill(current, index)
        if (next === current) return
        game = next
        bank.play(
            when {
                next.won -> "win"
                next.lost -> "lose"
                else -> "pop"
            },
        )
    }

    if (screen == "menu") {
        DetailScaffold(title = "color spill") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                if (saved != null) {
                    SpillButton("continue level ${saved!!.level}", Modifier.fillMaxWidth()) { resume() }
                    Spacer(Modifier.height(6.dp))
                }
                SpillButton("new run", Modifier.fillMaxWidth()) { start(1) }
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "best ${best.firstOrNull()?.score ?: 0}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = "fill the whole board from the top-left with one color, in as few picks as possible",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "color spill") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = "level ${current.level}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "moves ${current.moves}/${current.maxMoves} · par ${current.par}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = clockLabel(current.elapsedMs),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(4.dp))
            SpillBoard(current, Modifier.weight(1f)) { pick(it) }
            Spacer(Modifier.height(6.dp))
            val palette = List(current.colors) { spillColor(it, colors) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until current.colors) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .background(palette[i])
                            .border(0.5.dp, if (i == current.originColor()) colors.accent else colors.border)
                            .pointerInput(i, current) { detectTapGestures { pick(i) } },
                    )
                }
            }
            if (current.won || current.lost) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border)
                        .padding(8.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        BasicText(
                            text = if (current.won) "level cleared" else "out of moves",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        )
                        BasicText(
                            text = "moves ${current.moves} · par ${current.par} · score ${ColorSpillEngine.score(current)}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (current.won) {
                                SpillButton("next level") { start(current.level + 1) }
                            } else {
                                SpillButton("retry") { start(current.level) }
                            }
                            SpillButton("main menu") { screen = "menu"; game = null }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpillButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun SpillBoard(
    state: ColorSpillEngine.SpillState,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val palette = List(state.colors) { spillColor(it, colors) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(
            Modifier.size(side).pointerInput(state) {
                detectTapGestures { offset ->
                    val cellW = size.width.toFloat() / state.width
                    val cellH = size.height.toFloat() / state.height
                    val col = (offset.x / cellW).toInt().coerceIn(0, state.width - 1)
                    val row = (offset.y / cellH).toInt().coerceIn(0, state.height - 1)
                    val value = state.cell(col, row)
                    if (value >= 0) onPick(value)
                }
            },
        ) {
            val cellW = size.width / state.width
            val cellH = size.height / state.height
            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    drawRect(
                        color = palette[state.cell(x, y)],
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW - 1f, cellH - 1f),
                    )
                }
            }
        }
    }
}
