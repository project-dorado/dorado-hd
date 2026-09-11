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
import androidx.compose.ui.graphics.nativeCanvas
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

@Composable
fun ChessApp() {
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
    var difficulty by remember { mutableStateOf(ChessDifficulty.INTERMEDIATE) }
    var playAsWhite by remember { mutableStateOf(true) }
    var twoPlayer by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("wood") }
    var state by remember { mutableStateOf(ChessEngine.newGame()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastMove by remember { mutableStateOf<ChessMove?>(null) }
    var hintMove by remember { mutableStateOf<ChessMove?>(null) }
    var paused by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<ChessSave?>(null) }
    var elapsedMs by remember { mutableStateOf(0L) }
    val records by graph.games.top("chess", 100).collectAsState(initial = emptyList())

    val humanColor = if (playAsWhite) ChessColor.WHITE else ChessColor.BLACK
    val flipped = !playAsWhite
    val piecePaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }

    LaunchedEffect(Unit) {
        graph.appState.get("chess")?.let { blob ->
            val save = ChessEngine.decodeSave(blob)
            if (save != null) saved = save
        }
    }

    LaunchedEffect(state, screen, paused) {
        while (screen == "game" && !paused && state.status.isEmpty()) {
            delay(1000)
            elapsedMs += 1000
        }
    }

    LaunchedEffect(state, paused) {
        if (state.status.isNotEmpty() || state.history.isEmpty()) return@LaunchedEffect
        graph.appState.put(
            "chess",
            ChessEngine.encodeSave(
                ChessSave(
                    fen = ChessEngine.fen(state),
                    difficulty = if (twoPlayer) "two player" else difficulty.label,
                    color = if (playAsWhite) "white" else "black",
                    twoPlayer = twoPlayer,
                    theme = theme,
                    elapsedMs = elapsedMs,
                ),
            ),
        )
    }

    LaunchedEffect(state.turn, state.status, screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        if (twoPlayer || state.status.isNotEmpty()) return@LaunchedEffect
        if (state.turn == humanColor) return@LaunchedEffect
        delay(250)
        val move = withContext(Dispatchers.Default) { ChessEngine.bestMove(state, difficulty) }
        if (move != null) {
            state = ChessEngine.apply(state, move)
            lastMove = move
            bank.play(if (state.status.isNotEmpty()) "score" else "click")
            selected = null
        }
    }

    LaunchedEffect(state.status) {
        if (state.status.isEmpty()) return@LaunchedEffect
        if (recorded) return@LaunchedEffect
        recorded = true
        val score = when {
            state.status.startsWith("checkmate white") && humanColor == ChessColor.WHITE -> 1
            state.status.startsWith("checkmate black") && humanColor == ChessColor.BLACK -> 1
            state.status == "stalemate" -> 0
            twoPlayer -> 0
            else -> 0
        }
        bank.play(if (score > 0) "win" else "lose")
        graph.games.record("chess", score, if (twoPlayer) "two player" else difficulty.label)
        graph.appState.clear("chess")
    }

    fun startNew() {
        state = ChessEngine.newGame()
        selected = null
        lastMove = null
        hintMove = null
        recorded = false
        paused = false
        elapsedMs = 0
        screen = "game"
    }

    fun resume() {
        val save = saved ?: return
        val parsed = ChessEngine.fromFen(save.fen) ?: return
        state = parsed
        difficulty = ChessDifficulty.entries.firstOrNull { it.label == save.difficulty } ?: ChessDifficulty.INTERMEDIATE
        playAsWhite = save.color != "black"
        twoPlayer = save.twoPlayer
        theme = save.theme
        elapsedMs = save.elapsedMs
        selected = null
        lastMove = null
        hintMove = null
        recorded = false
        paused = false
        screen = "game"
    }

    fun click(r: Int, c: Int) {
        if (state.status.isNotEmpty() || paused || confirmUndo) return
        if (!twoPlayer && state.turn != humanColor) return
        if (selected == null) {
            val piece = state.board[r][c]
            if (piece?.color == state.turn) {
                selected = r to c
                hintMove = null
                bank.play("select")
            }
            return
        }
        val from = selected!!
        val moves = ChessEngine.legalMoves(state)
        val candidates = moves.filter { it.fromR == from.first && it.fromC == from.second && it.toR == r && it.toC == c }
        if (candidates.isNotEmpty()) {
            val move = candidates.firstOrNull { it.promotion == ChessPieceType.Q } ?: candidates.first()
            val applied = ChessEngine.apply(state, ChessEngine.autoQueen(state, move))
            state = applied
            lastMove = move
            bank.play(
                when {
                    applied.status.startsWith("checkmate") -> "score"
                    move.castleKingSide || move.castleQueenSide -> "coin"
                    applied.status.startsWith("check") -> "hit"
                    else -> "click"
                },
            )
            selected = null
            hintMove = null
            notice = ""
        } else {
            val piece = state.board[r][c]
            if (piece?.color == state.turn) {
                selected = r to c
                bank.play("select")
            } else {
                selected = null
            }
        }
    }

    fun requestHint() {
        if (state.status.isNotEmpty() || paused) return
        if (!twoPlayer && state.turn != humanColor) return
        scope.launch {
            val hint = withContext(Dispatchers.Default) { ChessEngine.hint(state) }
            if (hint != null) {
                hintMove = hint
                notice = "hint"
            }
        }
    }

    when (screen) {
        "menu" -> {
            ChessMenu(
                saved = saved,
                onSingle = { twoPlayer = false; screen = "setup" },
                onTwoPlayer = { twoPlayer = true; startNew() },
                onResume = { resume() },
                onRecords = { screen = "records" },
                onLearn = { screen = "learn" },
                onOptions = { screen = "options" },
            )
            return
        }
        "setup" -> {
            ChessSetup(
                difficulty = difficulty,
                playAsWhite = playAsWhite,
                onDifficulty = { difficulty = it },
                onSide = { playAsWhite = it },
                onStart = { startNew() },
                onBack = { screen = "menu" },
            )
            return
        }
        "records" -> {
            ChessRecords(records = records, onBack = { screen = "menu" })
            return
        }
        "learn" -> {
            ChessLearn(onBack = { screen = "menu" })
            return
        }
        "options" -> {
            ChessOptions(
                theme = theme,
                onTheme = { theme = it },
                onBack = { screen = "menu" },
            )
            return
        }
    }

    DetailScaffold(title = "chess") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = when {
                        state.status.isNotEmpty() -> state.status
                        twoPlayer -> "${state.turn.name.lowercase()} to move"
                        state.turn == humanColor -> "your move"
                        else -> "thinking…"
                    },
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_NOW_META.sp,
                        color = if (state.status.isNotEmpty()) colors.accent else colors.textPrimary,
                    ),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "%d:%02d".format(elapsedMs / 60000, (elapsedMs / 1000) % 60),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(4.dp))
            val selectedSquare = selected
            val fromSquare = lastMove?.let { it.fromR to it.fromC }
            val toSquare = lastMove?.let { it.toR to it.toC }
            val hint = hintMove
            val flippedBoard = flipped
            val current = state
            val engine = twoPlayer || state.turn == humanColor
            val lightSquare = if (theme == "wood") colors.tile else colors.elevated
            val darkSquare = if (theme == "wood") colors.background else colors.tile
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state, selected, confirmUndo) {
                        detectTapGestures(onTap = { offset ->
                            if (!engine && !twoPlayer) return@detectTapGestures
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
                            click(r, c)
                        })
                    },
            ) {
                val boardSize = min(size.width, size.height)
                val cell = boardSize / 8f
                val ox = (size.width - boardSize) / 2f
                val oy = (size.height - boardSize) / 2f
                val hintFrom = hint?.let { it.fromR to it.fromC }
                val hintTo = hint?.let { it.toR to it.toC }
                for (vr in 0..7) for (vc in 0..7) {
                    val r = if (flippedBoard) 7 - vr else vr
                    val c = if (flippedBoard) 7 - vc else vc
                    val light = (r + c) % 2 == 0
                    val highlight = selectedSquare == (r to c) || fromSquare == (r to c) || toSquare == (r to c) ||
                        hintFrom == (r to c) || hintTo == (r to c)
                    drawRect(
                        color = when {
                            highlight -> colors.tilePressed
                            selectedSquare == (r to c) -> colors.accent
                            light -> lightSquare
                            else -> darkSquare
                        },
                        topLeft = Offset(ox + vc * cell, oy + vr * cell),
                        size = Size(cell, cell),
                    )
                }
                val paint = piecePaint
                for (r in 0..7) for (c in 0..7) {
                    val piece = current.board[r][c] ?: continue
                    val vr = if (flippedBoard) 7 - r else r
                    val vc = if (flippedBoard) 7 - c else c
                    paint.color = if (piece.color == ChessColor.WHITE) android.graphics.Color.WHITE else android.graphics.Color.GRAY
                    paint.textSize = cell * 0.62f
                    val cx = ox + vc * cell + cell / 2f
                    val cy = oy + vr * cell + cell / 2f - (paint.ascent() + paint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(chessGlyph(piece.type), cx, cy, paint)
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
                ChessButton("undo", Modifier.weight(1f), enabled = state.history.isNotEmpty() && state.status.isEmpty()) {
                    confirmUndo = true
                }
                ChessButton("hint", Modifier.weight(1f), enabled = engine && state.status.isEmpty()) { requestHint() }
                ChessButton("pause", Modifier.weight(1f)) { paused = true }
                ChessButton("menu", Modifier.weight(1f)) { screen = "menu"; paused = false }
            }
            if (confirmUndo) {
                Spacer(Modifier.height(6.dp))
                ChessPanel(title = "take back two half moves?") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChessButton("yes") {
                            val take = if (state.history.size >= 2) 2 else 1
                            state = ChessEngine.undo(state, take)
                            selected = null
                            lastMove = null
                            hintMove = null
                            recorded = false
                            confirmUndo = false
                            bank.play("back")
                        }
                        ChessButton("no") { confirmUndo = false }
                    }
                }
            }
            if (state.status.isNotEmpty() || paused) {
                Spacer(Modifier.height(6.dp))
                ChessPanel(
                    title = when {
                        state.status == "stalemate" -> "draw"
                        state.status.startsWith("checkmate") -> "checkmate"
                        paused -> "paused"
                        else -> state.status
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (paused && state.status.isEmpty()) {
                            ChessButton("resume") { paused = false }
                        }
                        ChessButton("play again") { startNew() }
                        ChessButton("main menu") { screen = "menu"; paused = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessMenu(
    saved: ChessSave?,
    onSingle: () -> Unit,
    onTwoPlayer: () -> Unit,
    onResume: () -> Unit,
    onRecords: () -> Unit,
    onLearn: () -> Unit,
    onOptions: () -> Unit,
) {
    DetailScaffold(title = "chess") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                ChessButton("continue", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(4.dp))
            }
            ChessButton("single player", Modifier.fillMaxWidth()) { onSingle() }
            Spacer(Modifier.height(4.dp))
            ChessButton("two player", Modifier.fillMaxWidth()) { onTwoPlayer() }
            Spacer(Modifier.height(4.dp))
            ChessButton("how to play", Modifier.fillMaxWidth()) { onLearn() }
            Spacer(Modifier.height(4.dp))
            ChessButton("records", Modifier.fillMaxWidth()) { onRecords() }
            Spacer(Modifier.height(4.dp))
            ChessButton("options", Modifier.fillMaxWidth()) { onOptions() }
        }
    }
}

@Composable
private fun ChessSetup(
    difficulty: ChessDifficulty,
    playAsWhite: Boolean,
    onDifficulty: (ChessDifficulty) -> Unit,
    onSide: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "chess · setup") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "difficulty", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                ChessDifficulty.entries.forEach { level ->
                    ChessButton(level.label, Modifier.weight(1f), active = difficulty == level) { onDifficulty(level) }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "play as", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                ChessButton("white", Modifier.weight(1f), active = playAsWhite) { onSide(true) }
                ChessButton("black", Modifier.weight(1f), active = !playAsWhite) { onSide(false) }
            }
            Spacer(Modifier.height(10.dp))
            ChessButton("play", Modifier.fillMaxWidth()) { onStart() }
            Spacer(Modifier.height(4.dp))
            ChessButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun ChessRecords(
    records: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "chess · trophies", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
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
                    text = "no trophies yet",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            ChessButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun ChessLearn(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "chess · learn", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            listOf(
                "tap a piece then a target square",
                "castling moves the king two squares",
                "pawns promote to a queen",
                "en passant captures a double push",
                "checkmate ends the game",
            ).forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ChessButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun ChessOptions(theme: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "chess · options") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "theme", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                ChessButton("wood", Modifier.weight(1f), active = theme == "wood") { onTheme("wood") }
                ChessButton("metal", Modifier.weight(1f), active = theme == "metal") { onTheme("metal") }
            }
            Spacer(Modifier.height(10.dp))
            ChessButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun ChessPanel(title: String, content: @Composable () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .border(0.5.dp, colors.border)
            .padding(8.dp),
    ) {
        Column {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ChessButton(
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
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
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

private fun chessGlyph(type: ChessPieceType): String = when (type) {
    ChessPieceType.P -> "P"
    ChessPieceType.N -> "N"
    ChessPieceType.B -> "B"
    ChessPieceType.R -> "R"
    ChessPieceType.Q -> "Q"
    ChessPieceType.K -> "K"
}
