package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun CheckersApp() {
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
    var difficulty by remember { mutableStateOf(CheckersDifficulty.INTERMEDIATE) }
    var mode by remember { mutableStateOf(CheckersMode.REGULAR) }
    var playAsRed by remember { mutableStateOf(true) }
    var wooden by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf(CheckersEngine.newGame()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hintMove by remember { mutableStateOf<CheckersMoveRecord?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var quietPlies by remember { mutableStateOf(0) }
    var undoCount by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<CheckersSave?>(null) }
    var redMs by remember { mutableStateOf(0L) }
    var blackMs by remember { mutableStateOf(0L) }
    val records by graph.games.top("checkers", 100).collectAsState(initial = emptyList())

    val humanColor = if (playAsRed) CheckersColor.RED else CheckersColor.BLACK
    val flipped = !playAsRed
    val detailedMoves = remember(state) { CheckersEngine.legalMovesDetailed(state) }
    val destination = remember(selected, detailedMoves) {
        selected?.let { from -> detailedMoves.filter { it.from == from }.map { it.to } } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        saved = graph.appState.get("checkers")?.let { CheckersEngine.decodeSave(it) }
    }

    LaunchedEffect(state, screen, paused) {
        while (screen == "game" && !paused && state.winner == null) {
            delay(500)
            if (state.turn == CheckersColor.RED) redMs += 500 else blackMs += 500
        }
    }

    LaunchedEffect(state, paused) {
        if (state.winner == null && state.history.isEmpty()) return@LaunchedEffect
        if (state.winner == null) {
            graph.appState.put(
                "checkers",
                CheckersEngine.encodeSave(
                    CheckersSave(
                        fen = CheckersEngine.fen(state),
                        difficulty = difficulty.label,
                        mode = mode.name.lowercase(),
                        flipped = flipped,
                        redElapsedMs = redMs,
                        blackElapsedMs = blackMs,
                    ),
                ),
            )
        }
    }

    LaunchedEffect(state.turn, state.winner, screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        if (state.winner != null) return@LaunchedEffect
        if (state.turn == humanColor) return@LaunchedEffect
        delay(250)
        val move = withContext(Dispatchers.Default) { CheckersEngine.bestMove(state, difficulty, mode) }
        if (move != null) {
            state = CheckersEngine.applyMove(state, move)
            // AI plies feed the same 80-ply draw clock as human plies.
            quietPlies = CheckersEngine.quietPliesAfter(quietPlies, move)
            bank.play(if (move.isJump) "hit" else "click")
        }
    }

    LaunchedEffect(state.winner) {
        val winner = state.winner ?: return@LaunchedEffect
        if (recorded) return@LaunchedEffect
        recorded = true
        bank.play(if (winner == humanColor) "win" else "lose")
        graph.games.record(
            "checkers",
            if (winner == humanColor) 1 else 0,
            "${mode.name.lowercase()}:${difficulty.label}",
        )
        graph.appState.clear("checkers")
    }

    LaunchedEffect(quietPlies) {
        if (quietPlies >= CheckersEngine.DRAW_QUIET_PLIES && !recorded) {
            recorded = true
            bank.play("lose")
            graph.games.record("checkers", 0, "${mode.name.lowercase()}:${difficulty.label}:draw")
            graph.appState.clear("checkers")
        }
    }

    fun startNew() {
        state = CheckersEngine.newGame().copy(mode = mode)
        selected = null
        hintMove = null
        quietPlies = 0
        undoCount = 0
        notice = ""
        recorded = false
        paused = false
        redMs = 0
        blackMs = 0
        screen = "game"
    }

    fun resume() {
        val save = saved ?: return
        val restored = CheckersEngine.fromFen(save.fen) ?: return
        difficulty = CheckersDifficulty.entries.firstOrNull { it.label == save.difficulty } ?: CheckersDifficulty.INTERMEDIATE
        mode = if (save.mode == "suicide") CheckersMode.SUICIDE else CheckersMode.REGULAR
        playAsRed = !save.flipped
        wooden = true
        state = restored.copy(mode = mode)
        redMs = save.redElapsedMs
        blackMs = save.blackElapsedMs
        selected = null
        hintMove = null
        quietPlies = 0
        undoCount = 0
        recorded = false
        paused = false
        screen = "game"
    }

    fun tapCell(r: Int, c: Int) {
        if (state.winner != null || paused) return
        if (state.turn != humanColor) return
        val own = state.board[r][c]
        if (selected == null) {
            if (own != null && own.color == state.turn) {
                selected = r to c
                hintMove = null
                bank.play("select")
            }
            return
        }
        val move = detailedMoves.firstOrNull { it.from == selected && it.to == (r to c) }
        if (move != null) {
            state = CheckersEngine.applyMove(state, move)
            state = state.copy(mode = mode)
            bank.play(if (move.isJump) "hit" else "click")
            if (move.promoted) bank.play("coin")
            quietPlies = CheckersEngine.quietPliesAfter(quietPlies, move)
            selected = null
            hintMove = null
        } else if (own != null && own.color == state.turn) {
            selected = r to c
            hintMove = null
            bank.play("select")
        } else {
            selected = null
        }
    }

    fun undo() {
        if (state.history.isEmpty()) return
        val plies = if (state.history.size >= 2 && state.turn == humanColor) 2 else 1
        state = CheckersEngine.undo(state, plies)
        state = state.copy(mode = mode)
        selected = null
        hintMove = null
        recorded = false
        undoCount++
        if (undoCount == 4) notice = "undo used four times"
        bank.play("back")
    }

    fun requestHint() {
        if (state.winner != null || state.turn != humanColor) return
        scope.launch {
            val hint = withContext(Dispatchers.Default) { CheckersEngine.hint(state, mode) }
            if (hint != null) {
                hintMove = hint
                notice = "hint"
            }
        }
    }

    when (screen) {
        "menu" -> {
            CheckersMenu(
                records = records.map { it.score },
                saved = saved,
                onPlay = { screen = "setup" },
                onResume = { resume() },
                onRecords = { screen = "records" },
                onLearn = { screen = "learn" },
            )
            return
        }
        "setup" -> {
            CheckersSetup(
                difficulty = difficulty,
                mode = mode,
                playAsRed = playAsRed,
                wooden = wooden,
                onDifficulty = { difficulty = it },
                onMode = { mode = it },
                onSide = { playAsRed = it },
                onTheme = { wooden = it },
                onStart = { startNew() },
                onBack = { screen = "menu" },
            )
            return
        }
        "records" -> {
            CheckersRecords(records = records, onBack = { screen = "menu" })
            return
        }
        "learn" -> {
            CheckersLearn(onBack = { screen = "menu" })
            return
        }
    }

    DetailScaffold(title = "checkers") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val statusText = if (state.winner != null) {
                if (state.winner == humanColor) "you win" else "ai wins"
            } else if (state.turn == humanColor) {
                "your move · ${mode.name.lowercase()}"
            } else {
                "ai thinking"
            }
            val clockText = "%d:%02d · %d:%02d".format(redMs / 60000, (redMs / 1000) % 60, blackMs / 60000, (blackMs / 1000) % 60)
            Row(
                Modifier.fillMaxWidth().appDescription("$statusText, red $clockText"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = statusText,
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_NOW_META.sp,
                        color = if (state.winner != null) colors.accent else colors.textPrimary,
                    ),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = clockText,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(4.dp))
            val ckColors = colors
            val ckSelected = selected
            val ckTargets = destination
            val ckHint = hintMove
            val flippedBoard = flipped
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .appDescription(
                        "checkers board, 8 by 8, ${state.board.sumOf { row -> row.count { it != null } }} pieces, " +
                            when {
                                state.winner != null -> "game over"
                                state.turn == humanColor -> "your move"
                                else -> "ai thinking"
                            },
                    )
                    .pointerInput(state, selected) {
                        detectTapGestures(onTap = { offset ->
                            val boardSize = min(size.width, size.height)
                            val cell = boardSize / 8f
                            val ox = (size.width - boardSize) / 2f
                            val oy = (size.height - boardSize) / 2f
                            var c = ((offset.x - ox) / cell).toInt().coerceIn(0, 7)
                            var r = ((offset.y - oy) / cell).toInt().coerceIn(0, 7)
                            if (flippedBoard) {
                                r = 7 - r
                                c = 7 - c
                            }
                            tapCell(r, c)
                        })
                    },
            ) {
                val boardSize = min(size.width, size.height)
                val cell = boardSize / 8f
                val ox = (size.width - boardSize) / 2f
                val oy = (size.height - boardSize) / 2f
                val lightSquare = if (wooden) ckColors.tile else ckColors.elevated
                val darkSquare = if (wooden) ckColors.background else ckColors.tile
                for (vr in 0..7) for (vc in 0..7) {
                    val r = if (flippedBoard) 7 - vr else vr
                    val c = if (flippedBoard) 7 - vc else vc
                    val light = (r + c) % 2 == 1
                    val highlight = ckSelected == (r to c) || ckTargets.contains(r to c) ||
                        ckHint?.from == (r to c) || ckHint?.to == (r to c) || ckHint?.path?.contains(r to c) == true
                    drawRect(
                        color = when {
                            highlight -> ckColors.accent
                            light -> lightSquare
                            else -> darkSquare
                        },
                        topLeft = Offset(ox + vc * cell, oy + vr * cell),
                        size = Size(cell, cell),
                    )
                }
                for (r in 0..7) for (c in 0..7) {
                    val piece = state.board[r][c] ?: continue
                    val vr = if (flippedBoard) 7 - r else r
                    val vc = if (flippedBoard) 7 - c else c
                    val center = Offset(ox + vc * cell + cell / 2, oy + vr * cell + cell / 2)
                    val base = if (piece.color == CheckersColor.RED) ckColors.accent else Color.White
                    drawCircle(base.copy(alpha = if (piece.king) 1f else 0.85f), cell * if (piece.king) 0.42f else 0.36f, center)
                    if (piece.king) {
                        drawCircle(Color.Black, cell * 0.18f, center)
                    }
                }
            }
            if (notice.isNotEmpty()) {
                BasicText(
                    text = notice,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CheckersButton("undo", Modifier.weight(1f), enabled = state.history.isNotEmpty() && state.winner == null) { undo() }
                CheckersButton("hint", Modifier.weight(1f), enabled = state.turn == humanColor && state.winner == null) { requestHint() }
                CheckersButton("pause", Modifier.weight(1f)) { paused = true }
                CheckersButton("menu", Modifier.weight(1f)) { screen = "menu"; paused = false }
            }
            if (state.winner != null || paused || quietPlies >= CheckersEngine.DRAW_QUIET_PLIES) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border)
                        .padding(8.dp),
                ) {
                    Column {
                        BasicText(
                            text = when {
                                quietPlies >= CheckersEngine.DRAW_QUIET_PLIES -> "draw — no captures"
                                state.winner == humanColor -> "you win"
                                state.winner != null -> "ai wins"
                                else -> "paused"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (paused && state.winner == null) {
                                CheckersButton("resume") { paused = false }
                            }
                            CheckersButton("play again") { startNew() }
                            CheckersButton("main menu") { screen = "menu"; paused = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckersMenu(
    records: List<Int>,
    saved: CheckersSave?,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onRecords: () -> Unit,
    onLearn: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "checkers") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                CheckersButton("continue", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(4.dp))
            }
            CheckersButton("single player", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            CheckersButton("records", Modifier.fillMaxWidth()) { onRecords() }
            Spacer(Modifier.height(4.dp))
            CheckersButton("learn to play", Modifier.fillMaxWidth()) { onLearn() }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "wins ${records.count { it > 0 }} · games ${records.size}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun CheckersSetup(
    difficulty: CheckersDifficulty,
    mode: CheckersMode,
    playAsRed: Boolean,
    wooden: Boolean,
    onDifficulty: (CheckersDifficulty) -> Unit,
    onMode: (CheckersMode) -> Unit,
    onSide: (Boolean) -> Unit,
    onTheme: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "checkers · setup") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "mode", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CheckersButton("regular", Modifier.weight(1f), active = mode == CheckersMode.REGULAR) { onMode(CheckersMode.REGULAR) }
                CheckersButton("suicide", Modifier.weight(1f), active = mode == CheckersMode.SUICIDE) { onMode(CheckersMode.SUICIDE) }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "difficulty", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CheckersDifficulty.entries.forEach { level ->
                    CheckersButton(level.label, Modifier.weight(1f), active = difficulty == level) { onDifficulty(level) }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "play as", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CheckersButton("red", Modifier.weight(1f), active = playAsRed) { onSide(true) }
                CheckersButton("black", Modifier.weight(1f), active = !playAsRed) { onSide(false) }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "board", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                CheckersButton("wood", Modifier.weight(1f), active = wooden) { onTheme(true) }
                CheckersButton("stone", Modifier.weight(1f), active = !wooden) { onTheme(false) }
            }
            Spacer(Modifier.height(10.dp))
            CheckersButton("play", Modifier.fillMaxWidth()) { onStart() }
            Spacer(Modifier.height(4.dp))
            CheckersButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun CheckersRecords(
    records: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "checkers · records", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "per difficulty and mode",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            val grouped = records.groupBy { it.meta ?: "unknown" }
            grouped.forEach { (meta, games) ->
                val wins = games.count { it.score > 0 }
                BasicText(
                    text = "$meta · $wins-${games.size - wins}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.height(3.dp))
            }
            if (grouped.isEmpty()) {
                BasicText(
                    text = "no games recorded",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            CheckersButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun CheckersLearn(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "checkers · learn", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val lines = listOf(
                "men move one square diagonally forward",
                "kings move and jump in any diagonal",
                "captures are mandatory",
                "chain every jump while one is available",
                "reach the far rank to be crowned",
                "suicide mode plays to lose",
            )
            lines.forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            CheckersButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun CheckersButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .appTap(label = label, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}
