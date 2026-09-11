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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun tilesTimeLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun TilesApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var game by remember { mutableStateOf<TilesState?>(null) }
    var saved by remember { mutableStateOf<TilesState?>(null) }
    var stats by remember { mutableStateOf(TilesStats()) }
    var loaded by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val scores by graph.games.top("tiles", 20).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        stats = TilesEngine.decodeStats(graph.appState.get("tiles.stats"))
        saved = graph.appState.get("tiles")?.let { TilesEngine.decode(it) }
        loaded = true
    }

    LaunchedEffect(stats, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("tiles.stats", TilesEngine.encodeStats(stats))
    }

    LaunchedEffect(game) {
        val current = game ?: return@LaunchedEffect
        if (current.phase != TilesPhase.WON) {
            graph.appState.put("tiles", TilesEngine.encode(current))
        }
    }

    LaunchedEffect(game?.phase, screen, confirm) {
        while (screen == "game" && confirm == null) {
            val current = game ?: break
            if (current.phase == TilesPhase.WON) break
            delay(250)
            game = TilesEngine.step(current, 250)
        }
    }

    LaunchedEffect(game?.phase, game?.swapCount) {
        val current = game ?: return@LaunchedEffect
        if (current.phase == TilesPhase.WON && !recorded) {
            recorded = true
            saved = null
            val seconds = TilesEngine.seconds(current)
            val best = TilesEngine.bestBeaten(stats, current.mode, current.swapCount, seconds)
            stats = TilesEngine.applyWin(stats, current.mode, current.swapCount, seconds)
            if (stats.audioEnabled) bank.play(if (best) "win" else "coin")
            scope.launch {
                if (current.mode == TilesMode.CLASSIC) {
                    graph.games.record("tiles", current.swapCount, "classic")
                } else {
                    graph.games.record("tiles", seconds, "time trial")
                }
                graph.appState.clear("tiles")
            }
        }
    }

    fun cue(name: String) {
        if (stats.audioEnabled) bank.play(name)
    }

    fun start(mode: TilesMode) {
        game = TilesEngine.newGame(mode, System.currentTimeMillis().toInt())
        recorded = false
        confirm = null
        screen = "game"
    }

    fun resume() {
        val restored = saved ?: return
        game = restored
        recorded = false
        confirm = null
        screen = "game"
    }

    fun quit(withLoss: Boolean) {
        val current = game
        if (withLoss && current != null && current.phase != TilesPhase.WON) {
            stats = TilesEngine.applyLoss(stats, current.swapCount)
        }
        game = null
        saved = null
        confirm = null
        screen = "menu"
    }

    fun move(index: Int) {
        val current = game ?: return
        val next = TilesEngine.tap(current, index)
        if (next === current) return
        game = next
        cue(listOf("click", "pop", "tick")[next.swapCount % 3])
    }

    if (screen == "menu") {
        DetailScaffold(title = "tiles") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                if (saved != null) {
                    TilesButton("continue ${if (saved!!.mode == TilesMode.CLASSIC) "classic" else "time trial"}", Modifier.fillMaxWidth()) { resume() }
                    Spacer(Modifier.height(6.dp))
                }
                TilesButton("classic mode", Modifier.fillMaxWidth()) { start(TilesMode.CLASSIC) }
                Spacer(Modifier.height(4.dp))
                TilesButton("time trial", Modifier.fillMaxWidth()) { start(TilesMode.TIME_TRIAL) }
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "least moves ${stats.leastMoves} · quickest ${tilesTimeLabel(stats.leastTime)}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
                BasicText(
                    text = "wins ${stats.wins} · losses ${stats.losses}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(6.dp))
                TilesButton("sound: ${if (stats.audioEnabled) "on" else "off"}", Modifier.fillMaxWidth()) {
                    stats = stats.copy(audioEnabled = !stats.audioEnabled)
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = "slide the tiles into order with the blank; the board shuffles while the countdown runs",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                if (scores.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        text = "recent results",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    )
                    scores.take(4).forEach { row ->
                        BasicText(
                            text = "${row.meta ?: ""} · ${row.score}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                }
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "tiles") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = if (current.mode == TilesMode.CLASSIC) "moves ${current.swapCount}" else tilesTimeLabel(TilesEngine.seconds(current)),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = if (current.mode == TilesMode.CLASSIC) "best ${stats.leastMoves}" else "best ${tilesTimeLabel(stats.leastTime)}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.weight(1f))
                    TilesText("restart") { confirm = "restart" }
                    TilesText("menu") { confirm = "menu" }
                }
                Spacer(Modifier.height(4.dp))
                TilesBoard(current, Modifier.fillMaxWidth().weight(1f)) { move(it) }
                if (current.phase == TilesPhase.WON) {
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
                                text = "well done",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            BasicText(
                                text = if (current.mode == TilesMode.CLASSIC) {
                                    "solved in ${current.swapCount} moves · best ${stats.leastMoves}"
                                } else {
                                    "solved in ${tilesTimeLabel(TilesEngine.seconds(current))} · best ${tilesTimeLabel(stats.leastTime)}"
                                },
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TilesButton("play again") { start(current.mode) }
                                TilesButton("main menu") { quit(withLoss = false) }
                            }
                        }
                    }
                }
            }

            if (confirm != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = if (confirm == "restart") "restart this game?" else "quit to the menu?",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        )
                        BasicText(
                            text = "a game in progress counts as a loss",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TilesButton("yes") {
                                val wasRestart = confirm == "restart"
                                val mode = current.mode
                                quit(withLoss = true)
                                if (wasRestart) start(mode)
                            }
                            TilesButton("no") { confirm = null }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TilesButton(
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
private fun TilesText(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    BasicText(
        text = label,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        modifier = Modifier
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun TilesBoard(
    state: TilesState,
    modifier: Modifier,
    onTile: (Int) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val blank = TilesEngine.blankIndex(state.slots)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(
            Modifier
                .size(side)
                .pointerInput(state) {
                    detectTapGestures { offset ->
                        val cell = size.width / 3f
                        val col = (offset.x / cell).toInt().coerceIn(0, 2)
                        val row = (offset.y / cell).toInt().coerceIn(0, 2)
                        onTile(row * 3 + col)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / 3f
                for (i in 0 until TilesEngine.SIZE) {
                    val row = i / 3
                    val col = i % 3
                    val face = when {
                        i == blank -> colors.elevated
                        state.phase == TilesPhase.PLAY && TilesEngine.isLegalMove(state.slots, i) -> colors.tile
                        else -> colors.background
                    }
                    drawRect(
                        color = face,
                        topLeft = Offset(col * cell + 1f, row * cell + 1f),
                        size = Size(cell - 2f, cell - 2f),
                    )
                }
            }
            Column(Modifier.fillMaxSize()) {
                for (row in 0 until 3) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            val value = state.slots[index]
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (value != TilesEngine.BLANK) {
                                    BasicText(
                                        text = (value + 1).toString(),
                                        style = TextStyle(
                                            fontFamily = Selawik,
                                            fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                                            color = if (state.phase == TilesPhase.WON) colors.accent else colors.textPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.phase == TilesPhase.COUNTDOWN) {
                val label = TilesEngine.countdownLabel(state.countdown)
                if (label != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                        )
                    }
                }
            }
        }
    }
}
