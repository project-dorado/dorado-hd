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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val HEXIC_HINT_COOLDOWN = 30

@Composable
fun HexicApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        onDispose { synth.stop() }
    }
    var screen by remember { mutableStateOf("menu") }
    var mode by remember { mutableStateOf(HexicMode.MARATHON) }
    var difficulty by remember { mutableStateOf(HexicDifficulty.NORMAL) }
    var game by remember { mutableStateOf<HexicGame?>(null) }
    var saved by remember { mutableStateOf<HexicGame?>(null) }
    var cursor by remember { mutableStateOf(HexCursor(HexPos(0, 0))) }
    var paused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var hintCooldown by remember { mutableStateOf(0) }
    var recorded by remember { mutableStateOf(false) }
    val bestFlow by graph.games.top("hexic", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        saved = graph.appState.get("hexic")?.let { HexicEngine.decode(it) }
    }

    LaunchedEffect(game) {
        val current = game
        if (current != null && !current.won && !current.lost) {
            graph.appState.put("hexic", HexicEngine.encode(current))
        } else if (current != null) {
            graph.appState.clear("hexic")
        }
    }

    LaunchedEffect(game?.mode, game?.won, game?.lost, paused, screen) {
        while (screen == "game" && !paused) {
            val current = game ?: break
            if (current.won || current.lost) break
            delay(250)
            game = HexicEngine.tick(current, 0.25)
        }
    }

    LaunchedEffect(game?.won, game?.lost, game?.score) {
        val current = game ?: return@LaunchedEffect
        if ((current.won || current.lost) && !recorded) {
            recorded = true
            scope.launch(NonCancellable) {
                graph.games.record("hexic", current.score, current.mode.name.lowercase())
            }
        }
    }

    fun startNew() {
        val fresh = HexicEngine.newGame(mode, difficulty, seed = System.currentTimeMillis().toInt())
        game = fresh
        cursor = HexCursor(HexPos(0, 0))
        recorded = false
        paused = false
        hintCooldown = 0
        screen = "game"
    }

    fun resume() {
        val resumeGame = saved ?: return
        game = resumeGame
        mode = resumeGame.mode
        difficulty = resumeGame.difficulty
        cursor = HexCursor(HexPos(0, 0))
        recorded = false
        paused = false
        screen = "game"
    }

    fun rotate(clockwise: Boolean) {
        val current = game ?: return
        if (busy) return
        busy = true
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                HexicEngine.rotateGame(current, cursor, clockwise)
            }
            game = next
            bank.play(
                when {
                    next.won -> "win"
                    next.lost -> "lose"
                    next.score > current.score -> if (next.stats.pearls > current.stats.pearls) "coin" else "score"
                    else -> "rotate"
                },
            )
            if (next.reason != null && (next.won || next.lost)) {
                saved = null
            }
            busy = false
        }
    }

    fun moveCursor(pos: HexPos) {
        cursor = cursor.copy(anchor = pos)
    }

    if (screen == "menu") {
        HexicMenu(
            saved = saved,
            best = bestFlow.firstOrNull()?.score ?: 0,
            onMode = { picked -> mode = picked; screen = "difficulty" },
            onResume = { resume() },
        )
        return
    }
    if (screen == "difficulty") {
        HexicDifficultyScreen(
            mode = mode,
            onPick = { picked -> difficulty = picked; startNew() },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "hexic") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val status = when {
                current.won -> "win · ${reasonLabel(current.reason)}"
                current.lost -> "game over · ${reasonLabel(current.reason)}"
                current.mode == HexicMode.TIMED -> "time %04.1f".format(current.timeLeft)
                current.mode == HexicMode.SURVIVAL -> "survival level ${current.survivalLevel}"
                else -> "target ${current.combosLeft}"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${current.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "level ${current.level}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = status,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
            }
            Spacer(Modifier.height(4.dp))
            HexicBoardCanvas(
                board = current.board,
                cursor = cursor,
                colors = colors,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onTap = { moveCursor(it) },
            )
            Spacer(Modifier.height(4.dp))
            if (current.message.isNotEmpty()) {
                BasicText(
                    text = current.message,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
                Spacer(Modifier.height(2.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                HexicButton("pair a", Modifier.weight(1f), active = cursor.kind == HexCursorKind.STANDARD && !cursor.half) {
                    cursor = cursor.copy(kind = HexCursorKind.STANDARD, half = false)
                }
                HexicButton("pair b", Modifier.weight(1f), active = cursor.kind == HexCursorKind.STANDARD && cursor.half) {
                    cursor = cursor.copy(kind = HexCursorKind.STANDARD, half = true)
                }
                HexicButton("flower", Modifier.weight(1f), active = cursor.kind == HexCursorKind.FLOWER) {
                    cursor = cursor.copy(kind = HexCursorKind.FLOWER)
                }
                HexicButton("hint", Modifier.weight(1f), enabled = hintCooldown == 0) {
                    if (hintCooldown > 0) return@HexicButton
                    scope.launch {
                        val hint = withContext(Dispatchers.Default) { HexicEngine.hints(current.board).firstOrNull() }
                        if (hint != null) {
                            cursor = hint
                            hintCooldown = HEXIC_HINT_COOLDOWN
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                HexicButton("rotate left", Modifier.weight(1f), enabled = !busy) { rotate(clockwise = false) }
                HexicButton("rotate right", Modifier.weight(1f), enabled = !busy) { rotate(clockwise = true) }
                HexicButton("pause", Modifier.weight(1f)) {
                    paused = true
                }
                HexicButton("new game", Modifier.weight(1f)) { screen = "menu"; game = null; saved = null }
            }
            if (hintCooldown > 0) {
                LaunchedEffect(hintCooldown) {
                    delay(1000)
                    hintCooldown--
                }
            }
            if (current.won || current.lost || paused) {
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
                            text = when {
                                current.won -> "you win"
                                current.lost -> "game over"
                                else -> "paused"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        )
                        BasicText(
                            text = "score ${current.score}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (paused && !current.won && !current.lost) {
                                HexicButton("resume") { paused = false }
                            }
                            if (current.won || current.lost) {
                                HexicButton("play again") { startNew() }
                            }
                            HexicButton("main menu") { screen = "menu"; game = null; saved = null }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexicMenu(
    saved: HexicGame?,
    best: Int,
    onMode: (HexicMode) -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "hexic") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                HexicButton("continue ${saved.mode.name.lowercase()}", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(6.dp))
            }
            HexicButton("marathon", Modifier.fillMaxWidth()) { onMode(HexicMode.MARATHON) }
            Spacer(Modifier.height(4.dp))
            HexicButton("timed", Modifier.fillMaxWidth()) { onMode(HexicMode.TIMED) }
            Spacer(Modifier.height(4.dp))
            HexicButton("survival", Modifier.fillMaxWidth()) { onMode(HexicMode.SURVIVAL) }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "trip best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun HexicDifficultyScreen(
    mode: HexicMode,
    onPick: (HexicDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "hexic · ${mode.name.lowercase()}") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "starting level",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            HexicDifficulty.entries.forEach { difficulty ->
                HexicButton("${difficulty.name.lowercase()} · level ${difficulty.startLevel}", Modifier.fillMaxWidth()) { onPick(difficulty) }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(6.dp))
            HexicButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun HexicButton(
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

@Composable
private fun HexicBoardCanvas(
    board: HexBoard,
    cursor: HexCursor,
    colors: DoradoColors,
    modifier: Modifier,
    onTap: (HexPos) -> Unit,
) {
    val cursorColor = colors.accent
    Canvas(
        modifier = modifier.pointerInput(board, cursor) {
            detectTapGestures { offset ->
                val metrics = hexicMetrics(size.width.toFloat(), size.height.toFloat())
                var best: HexPos? = null
                var bestDistance = Float.MAX_VALUE
                for (pos in HexicEngine.allPositions()) {
                    val center = metrics.center(pos)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val distance = dx * dx + dy * dy
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = pos
                    }
                }
                val threshold = metrics.radius * 1.15f
                if (best != null && bestDistance <= threshold * threshold) onTap(best)
            }
        },
    ) {
        val metrics = hexicMetrics(size.width, size.height)
        for (pos in HexicEngine.allPositions()) {
            val center = metrics.center(pos)
            val piece = board[pos]
            drawHexagon(center, metrics.radius, colors.elevated)
            if (piece != null) {
                drawHexPiece(center, metrics.radius, piece, colors)
            }
        }
        val cursorCells = HexicEngine.cursorCells(cursor)
        for (cell in cursorCells) {
            if (!board.contains(cell)) continue
            drawHexagon(metrics.center(cell), metrics.radius * 0.98f, cursorColor, stroke = 2.2f)
        }
        if (cursor.kind == HexCursorKind.FLOWER && board.contains(cursor.anchor)) {
            drawHexagon(metrics.center(cursor.anchor), metrics.radius * 0.62f, cursorColor, stroke = 2.2f)
        }
    }
}

private class HexicMetrics(val cellW: Float, val cellH: Float, val radius: Float, val ox: Float) {
    fun center(pos: HexPos): Offset {
        val y = (pos.row + if (pos.col % 2 == 0) 0.5f else 1.0f) * cellH
        return Offset(ox + (pos.col + 0.5f) * cellW, y)
    }
}

private fun hexicMetrics(width: Float, height: Float): HexicMetrics {
    val cellH = height / 9.6f
    val cellW = min(width / 10.4f, cellH * 1.18f)
    val radius = min(cellW * 0.52f, cellH * 0.46f)
    val ox = (width - cellW * 10f) / 2f
    return HexicMetrics(cellW, cellH, radius, ox)
}

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color, stroke: Float = 0f) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i - 90.0))
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (stroke > 0f) {
        drawPath(path, color, style = Stroke(width = stroke))
    } else {
        drawPath(path, color)
    }
}

private fun DrawScope.drawHexPiece(center: Offset, radius: Float, piece: HexPiece, colors: DoradoColors) {
    val hue = when (piece.hue) {
        HexColor.A -> colors.accent
        HexColor.B -> DoradoAccent.CYAN.primary
        HexColor.C -> DoradoAccent.ORANGE.primary
        HexColor.D -> DoradoAccent.LIME.primary
        HexColor.E -> DoradoAccent.PURPLE.primary
        HexColor.F -> Color.White
        HexColor.G -> colors.accentBright
        HexColor.PEARL1, HexColor.PEARL2 -> Color.Black
        HexColor.FLOWER -> DoradoAccent.PINK.bright
        HexColor.EMPTY -> colors.elevated
    }
    val alpha = if (piece.locked) 0.45f else 1f
    drawHexagon(center, radius * 0.88f, hue.copy(alpha = alpha))
    if (piece.hue.isPearl()) {
        drawHexagon(center, radius * 0.62f, Color.White.copy(alpha = alpha), stroke = 1.6f)
    }
    if (piece.hue == HexColor.FLOWER) {
        for (i in 0 until 6) {
            val angle = Math.toRadians(60.0 * i)
            val petal = Offset(
                center.x + (radius * 0.5f * cos(angle)).toFloat(),
                center.y + (radius * 0.5f * sin(angle)).toFloat(),
            )
            drawCircle(Color.White.copy(alpha = alpha * 0.9f), radius * 0.16f, petal)
        }
    }
    if (piece.star) {
        drawCircle(Color.White.copy(alpha = alpha), radius * 0.22f, center)
    }
    if (piece.isBomb) {
        drawHexagon(center, radius * 0.66f, Color.Black.copy(alpha = alpha), stroke = 2.0f)
    }
}

private fun reasonLabel(reason: HexicReason?): String = when (reason) {
    HexicReason.PEARL_FLOWER -> "black pearl flower"
    HexicReason.PEARL_CLUSTER -> "pearl cluster"
    HexicReason.CLEARED -> "board cleared"
    HexicReason.HIGHEST_LEVEL -> "survival level 50"
    HexicReason.BOMB -> "bomb exploded"
    HexicReason.TIME -> "time up"
    HexicReason.LOCKED -> "board locked"
    null -> ""
}
