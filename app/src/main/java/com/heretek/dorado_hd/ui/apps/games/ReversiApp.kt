package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/* ============================================================ */
/*                            Reversi                              */
/* ============================================================ */

private enum class ReversiScreen { SETUP, PLAY }

@Composable
fun ReversiApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var apiLoaded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(ReversiScreen.SETUP) }
    var side by remember { mutableStateOf(ReversiSide.BLACK) }
    var level by remember { mutableStateOf(ReversiLevel.MEDIUM) }
    var showMoves by remember { mutableStateOf(true) }
    var game by remember { mutableStateOf(ReversiEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scores by graph.games.top("reversi", 200).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        graph.appState.get("reversi")?.let { blob ->
            blob.split(";").forEach { kv ->
                val parts = kv.split("=")
                if (parts.size == 2) when (parts[0]) {
                    "side" -> runCatching { side = ReversiSide.valueOf(parts[1]) }
                    "level" -> runCatching { level = ReversiLevel.valueOf(parts[1]) }
                    "moves" -> showMoves = parts[1] == "1"
                }
            }
        }
        apiLoaded = true
    }

    LaunchedEffect(side, level, showMoves, apiLoaded) {
        if (!apiLoaded) return@LaunchedEffect
        graph.appState.put("reversi", "side=${side.name};level=${level.name};moves=${if (showMoves) 1 else 0}")
    }

    val humanColor = when (side) {
        ReversiSide.BLACK -> ReversiEngine.BLACK
        ReversiSide.WHITE -> ReversiEngine.WHITE
        ReversiSide.NONE -> null
    }
    val aiColor = when (side) {
        ReversiSide.BLACK -> ReversiEngine.WHITE
        ReversiSide.WHITE -> ReversiEngine.BLACK
        ReversiSide.NONE -> null
    }
    val stats = remember(scores) {
        ReversiEngine.statsFrom(scores.map { (it.meta ?: "") to it.score })
    }

    fun start() {
        game = ReversiEngine.newGame()
        recorded = false
        menu = false
        screen = ReversiScreen.PLAY
    }

    fun tapCell(r: Int, c: Int) {
        if (game.over) return
        val mine = humanColor
        if (mine != null && game.turn != mine) return
        val next = ReversiEngine.play(game, r, c)
        if (next !== game) {
            game = next
            bank.play("toss")
        }
    }

    fun undoMove() {
        var g = ReversiEngine.undo(game)
        if (g === game) return
        var guard = 0
        while (humanColor != null && !g.over && g.turn != humanColor && g.history.isNotEmpty() && guard++ < 4) {
            g = ReversiEngine.undo(g)
        }
        game = g
        recorded = false
        bank.play("back")
    }

    LaunchedEffect(game, screen, level, side) {
        if (screen != ReversiScreen.PLAY || game.over) return@LaunchedEffect
        val ai = aiColor ?: return@LaunchedEffect
        if (game.turn != ai) return@LaunchedEffect
        delay(250)
        val move = withContext(Dispatchers.Default) { ReversiEngine.bestMove(game.board, ai, level) } ?: return@LaunchedEffect
        val next = ReversiEngine.play(game, move.first, move.second)
        if (next !== game) {
            game = next
            bank.play("toss")
        }
    }

    if (game.over && screen == ReversiScreen.PLAY && !recorded) {
        recorded = true
        val human = humanColor
        val result = when {
            human == null -> 2
            game.winner == human -> 1
            game.winner == ReversiEngine.EMPTY -> 2
            else -> 0
        }
        val key = if (side == ReversiSide.NONE) "two" else level.name.lowercase()
        val (b, w) = ReversiEngine.score(game.board)
        LaunchedEffect(Unit) {
            graph.games.record("reversi", result, "$key|${side.name.lowercase()}|B$b W$w")
        }
    }

    DetailScaffold(title = "reversi") {
        Box(Modifier.fillMaxSize()) {
            if (screen == ReversiScreen.SETUP) {
                Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        text = "reversi",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.textPrimary),
                    )
                    BasicText(
                        text = "black moves first",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = "you", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                        Spacer(Modifier.width(8.dp))
                        ReversiSide.values().forEach { s ->
                            EdgeText(
                                text = when (s) {
                                    ReversiSide.BLACK -> "black"
                                    ReversiSide.WHITE -> "white"
                                    ReversiSide.NONE -> "two-player"
                                },
                                color = if (s == side) colors.accent else colors.textSecondary,
                                onClick = { side = s },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = "ai", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                        Spacer(Modifier.width(8.dp))
                        ReversiLevel.values().forEach { l ->
                            EdgeText(
                                text = l.name.lowercase(),
                                color = if (l == level) colors.accent else colors.textSecondary,
                                onClick = { level = l },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = "hints", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                        Spacer(Modifier.width(8.dp))
                        EdgeText("show", if (showMoves) colors.accent else colors.textSecondary) { showMoves = true }
                        Spacer(Modifier.width(6.dp))
                        EdgeText("hide", if (!showMoves) colors.accent else colors.textSecondary) { showMoves = false }
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "wins/losses vs ai",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                    )
                    ReversiLevel.values().forEach { l ->
                        val s = stats[l.name.lowercase()] ?: ReversiStats()
                        BasicText(
                            text = "${l.name.lowercase()}  ${s.wins}w ${s.losses}l ${s.draws}d",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                    EdgeText("start", colors.accent) { start() }
                }
            } else {
                val myTurn = game.over || humanColor == null || game.turn == humanColor
                val (b, w) = ReversiEngine.score(game.board)
                val legal = remember(game.board, game.turn) { ReversiEngine.legalMoves(game.board, game.turn) }
                Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            text = when {
                                game.over && game.winner == ReversiEngine.EMPTY -> "draw $b—$w"
                                game.over -> "${if (game.winner == ReversiEngine.BLACK) "black" else "white"} wins $b—$w"
                                side == ReversiSide.NONE -> "black $b — white $w · ${if (game.turn == ReversiEngine.BLACK) "black" else "white"} to move"
                                myTurn -> "you $b — ai $w · your move"
                                else -> "you $b — ai $w · ai…"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (game.over) colors.accent else colors.textPrimary),
                            modifier = Modifier.weight(1f),
                        )
                        EdgeText("undo", if (game.history.isEmpty()) colors.textInactive else colors.textPrimary) { undoMove() }
                        EdgeText(if (showMoves) "hints on" else "hints off", if (showMoves) colors.accent else colors.textSecondary) { showMoves = !showMoves }
                        EdgeText("menu", colors.textPrimary) { menu = true }
                    }
                    Spacer(Modifier.width(4.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(game, screen, side) {
                                detectTapGestures(onTap = { offset ->
                                    val s = min(size.width, size.height)
                                    val cell = s / 8f
                                    val ox = (size.width - s) / 2f
                                    val oy = (size.height - s) / 2f
                                    val c = ((offset.x - ox) / cell).toInt().coerceIn(0, 7)
                                    val r = ((offset.y - oy) / cell).toInt().coerceIn(0, 7)
                                    tapCell(r, c)
                                })
                            },
                    ) {
                        val s = min(size.width, size.height)
                        val cell = s / 8
                        val ox = (size.width - s) / 2
                        val oy = (size.height - s) / 2
                        for (r in 0..7) for (c in 0..7) {
                            drawRect(
                                color = if ((r + c) % 2 == 0) colors.tile else colors.background,
                                topLeft = Offset(ox + c * cell, oy + r * cell),
                                size = androidx.compose.ui.geometry.Size(cell, cell),
                            )
                        }
                        for (r in 0..7) for (c in 0..7) {
                            val v = game.board[r][c]
                            val cx = ox + c * cell + cell / 2
                            val cy = oy + r * cell + cell / 2
                            if (v == ReversiEngine.BLACK) {
                                drawCircle(colors.textSecondary, cell * 0.42f, Offset(cx, cy))
                                drawCircle(Color.Black, cell * 0.37f, Offset(cx, cy))
                            } else if (v == ReversiEngine.WHITE) {
                                drawCircle(Color.White, cell * 0.37f, Offset(cx, cy))
                            } else if (showMoves && myTurn && (r to c) in legal) {
                                drawCircle(colors.accent.copy(alpha = 0.55f), cell * 0.12f, Offset(cx, cy))
                            }
                        }
                    }
                }
            }

            if (menu && screen == ReversiScreen.PLAY) {
                Column(
                    Modifier.fillMaxSize().background(colors.background).padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(text = "options", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = "you", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                        Spacer(Modifier.width(6.dp))
                        ReversiSide.values().forEach { s ->
                            EdgeText(
                                text = when (s) {
                                    ReversiSide.BLACK -> "black"
                                    ReversiSide.WHITE -> "white"
                                    ReversiSide.NONE -> "two"
                                },
                                color = if (s == side) colors.accent else colors.textSecondary,
                                onClick = { side = s },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(text = "ai", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                        Spacer(Modifier.width(6.dp))
                        ReversiLevel.values().forEach { l ->
                            EdgeText(
                                text = l.name.lowercase(),
                                color = if (l == level) colors.accent else colors.textSecondary,
                                onClick = { level = l },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Row {
                        EdgeText("new game", colors.accent) { start() }
                        Spacer(Modifier.width(10.dp))
                        EdgeText("setup", colors.textPrimary) { screen = ReversiScreen.SETUP; menu = false }
                        Spacer(Modifier.width(10.dp))
                        EdgeText("close", colors.textPrimary) { menu = false }
                    }
                    Spacer(Modifier.width(4.dp))
                    BasicText(
                        text = "changing side or difficulty applies on the next game",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                    BasicText(
                        text = "tap a highlighted square to place a stone",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                }
            }
        }
    }
}
