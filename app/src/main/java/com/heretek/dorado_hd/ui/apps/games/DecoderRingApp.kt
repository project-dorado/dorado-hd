package com.heretek.dorado_hd.ui.apps.games

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun DecoderRingApp() {
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
    var difficulty by remember { mutableStateOf(DecoderRingEngine.Difficulty.NORMAL) }
    var state by remember { mutableStateOf<DecoderRingEngine.State?>(null) }
    var saved by remember { mutableStateOf<DecoderRingEngine.State?>(null) }
    var progress by remember { mutableStateOf(DecoderRingEngine.Progress()) }
    var letter by remember { mutableStateOf<Char?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val scores by graph.games.top("decoder-ring", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = graph.appState.get("decoder-ring.progress")?.let { DecoderRingEngine.decodeProgress(it) }
            ?: DecoderRingEngine.Progress()
        saved = graph.appState.get("decoder-ring")?.let { DecoderRingEngine.decode(it) }
    }

    LaunchedEffect(progress) {
        graph.appState.put("decoder-ring.progress", DecoderRingEngine.encodeProgress(progress))
    }

    LaunchedEffect(state, screen, paused) {
        val current = state ?: return@LaunchedEffect
        if (screen == "game" && !current.won && !paused) {
            graph.appState.put("decoder-ring", DecoderRingEngine.encode(current))
        }
    }

    LaunchedEffect(state?.won) {
        val current = state ?: return@LaunchedEffect
        if (current.won && !recorded) {
            recorded = true
            bank.play("win")
            progress = DecoderRingEngine.recordCompletion(progress, current.puzzleIndex, current.difficulty)
            scope.launch(NonCancellable) {
                graph.appState.clear("decoder-ring")
                graph.games.record("decoder-ring", DecoderRingEngine.solvedCount(progress), current.difficulty.label)
            }
        }
    }

    LaunchedEffect(state?.won, state?.cascadeTicks) {
        val current = state ?: return@LaunchedEffect
        if (current.won && current.cascadeTicks < DecoderRingEngine.WIN_DELAY_TICKS) {
            delay(40)
            state = DecoderRingEngine.step(current, 640)
        }
    }

    fun startPuzzle(index: Int) {
        state = DecoderRingEngine.newGame(index, difficulty, checkEnabled = state?.checkEnabled ?: true)
        letter = null
        recorded = false
        paused = false
        notice = ""
        zoom = 1f
        pan = Offset.Zero
        screen = "game"
    }

    fun resumeSaved() {
        val current = saved ?: return
        state = current
        difficulty = current.difficulty
        letter = null
        recorded = false
        paused = false
        notice = ""
        zoom = 1f
        pan = Offset.Zero
        screen = "game"
    }

    if (screen == "menu") {
        DecoderMenu(
            progress = progress,
            saved = saved,
            difficulty = difficulty,
            bestSolved = scores.firstOrNull()?.score ?: 0,
            onDifficulty = { difficulty = it },
            onPick = { startPuzzle(it) },
            onResume = { resumeSaved() },
            onClearSaved = {
                saved = null
                scope.launch(NonCancellable) { graph.appState.clear("decoder-ring") }
            },
        )
        return
    }

    val current = state ?: return
    val puzzle = DecoderRingEngine.puzzle(current.puzzleIndex)
    DetailScaffold(title = "decoder ring", onBack = { screen = "menu"; paused = false }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "puzzle ${current.puzzleIndex + 1} · ${current.difficulty.label}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = if (current.checkEnabled) "check on" else "check off",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(current, zoom) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                pan += panChange
                                zoom = DecoderRingEngine.clampZoom((zoom * zoomChange).coerceIn(1f, 1.8f))
                            }
                        }
                        .pointerInput(current) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val hit = decoderHitTest(offset, viewW, viewH, zoom, pan, puzzle)
                                    if (hit >= 0) {
                                        val existing = current.mapping[hit]
                                        if (existing != null && hit !in current.stone) {
                                            state = DecoderRingEngine.clear(current, hit)
                                            bank.play("back")
                                        } else {
                                            state = DecoderRingEngine.highlight(current, hit)
                                            bank.play("tick")
                                        }
                                    }
                                },
                                onDoubleTap = {
                                    zoom = if (zoom > 1.3f) 1f else 1.8f
                                    pan = Offset.Zero
                                },
                            )
                        },
                ) {
                    drawDecoderBoard(puzzle, current, colors, zoom, pan)
                }
            }
            Spacer(Modifier.height(3.dp))
            DecoderRack(
                state = current,
                selected = letter,
                onPick = { picked ->
                    letter = if (letter == picked) null else picked
                    val highlight = current.highlight
                    if (highlight > 0 && letter == picked) {
                        state = DecoderRingEngine.place(current, highlight, picked)
                        letter = null
                        bank.play("click")
                    }
                },
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                DecoderButton("check", Modifier.weight(1f)) {
                    state = DecoderRingEngine.toggleCheck(current)
                    bank.play("tick")
                }
                DecoderButton(
                    "reveal",
                    Modifier.weight(1f),
                    enabled = current.highlight > 0,
                ) {
                    val before = current.mapping[current.highlight]
                    state = DecoderRingEngine.reveal(current)
                    if (state?.mapping?.get(current.highlight) != before) bank.play("toss")
                }
                DecoderButton("help", Modifier.weight(1f)) { notice = "tap a symbol, then tap a letter to map every matching tile" }
                DecoderButton("pause", Modifier.weight(1f)) { paused = true }
            }
            if (notice.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                BasicText(
                    text = notice,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            }
            if (paused || current.won) {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border)
                        .padding(8.dp),
                ) {
                    BasicText(
                        text = if (current.won) "solved · ${current.difficulty.label}" else "paused",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (paused && !current.won) {
                            DecoderButton("resume") { paused = false }
                            DecoderButton("restart") { startPuzzle(current.puzzleIndex) }
                        }
                        if (current.won) {
                            val next = current.puzzleIndex + 1
                            if (next < DecoderRingEngine.PACK_SLOTS && DecoderRingEngine.isLevelUnlocked(progress, next)) {
                                DecoderButton("next puzzle") { startPuzzle(next) }
                            }
                            DecoderButton("replay harder") {
                                val harder = DecoderRingEngine.Difficulty.entries.getOrNull(current.difficulty.ordinal + 1) ?: current.difficulty
                                difficulty = harder
                                startPuzzle(current.puzzleIndex)
                            }
                        }
                        DecoderButton("main menu") { screen = "menu"; paused = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecoderMenu(
    progress: DecoderRingEngine.Progress,
    saved: DecoderRingEngine.State?,
    difficulty: DecoderRingEngine.Difficulty,
    bestSolved: Int,
    onDifficulty: (DecoderRingEngine.Difficulty) -> Unit,
    onPick: (Int) -> Unit,
    onResume: () -> Unit,
    onClearSaved: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "decoder ring") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                DecoderButton("continue puzzle ${saved.puzzleIndex + 1} (${saved.difficulty.label})", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DecoderRingEngine.Difficulty.entries.forEach { tier ->
                    DecoderButton(tier.label, Modifier.weight(1f), active = tier == difficulty) { onDifficulty(tier) }
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "levels · ${DecoderRingEngine.solvedCount(progress)}/${DecoderRingEngine.PACK_SLOTS} solved",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 5) {
                            val index = row * 5 + col
                            val unlocked = DecoderRingEngine.isLevelUnlocked(progress, index)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(if (unlocked) colors.tile else colors.elevated)
                                    .border(0.5.dp, if (unlocked) colors.border else colors.elevated)
                                    .pointerInput(index, unlocked) {
                                        if (unlocked) detectTapGestures(onTap = { onPick(index) })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    BasicText(
                                        text = if (unlocked) "${index + 1}" else "·",
                                        style = TextStyle(
                                            fontFamily = Selawik,
                                            fontSize = DoradoTokens.TYPE_LIST.sp,
                                            color = if (unlocked) colors.textPrimary else colors.textInactive,
                                        ),
                                    )
                                    val best = progress.best(index)
                                    if (best != null) {
                                        BasicText(
                                            text = "*".repeat(best.ordinal + 1),
                                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "best solved $bestSolved",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
                Spacer(Modifier.weight(1f))
                if (saved != null) {
                    DecoderButton("discard saved") { onClearSaved() }
                }
            }
        }
    }
}

@Composable
private fun DecoderRack(
    state: DecoderRingEngine.State,
    selected: Char?,
    onPick: (Char) -> Unit,
) {
    val colors = LocalDoradoColors.current
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .pointerInput(state.mapping, selected) {
                detectTapGestures { offset ->
                    val tiles = DecoderRingEngine.NUM_LETTERS
                    val columns = 9
                    val rows = (tiles + columns - 1) / columns
                    val cellW = size.width.toFloat() / columns
                    val cellH = size.height.toFloat() / rows
                    val col = (offset.x / cellW).toInt().coerceIn(0, columns - 1)
                    val row = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                    val index = row * columns + col
                    if (index in 0 until tiles) {
                        val ch = if (index == 0) ' ' else ('A' + index - 1)
                        if (ch != ' ') onPick(ch)
                    }
                }
            },
    ) {
        val columns = 9
        val rows = (DecoderRingEngine.NUM_LETTERS + columns - 1) / columns
        val cellW = size.width / columns
        val cellH = size.height / rows
        val used = state.usedLetters()
        for (index in 0 until DecoderRingEngine.NUM_LETTERS) {
            val col = index % columns
            val row = index / columns
            val x = col * cellW
            val y = row * cellH
            val ch = if (index == 0) ' ' else ('A' + index - 1)
            val isSelected = selected == ch
            val mapped = state.mapping.any { it == ch }
            drawRect(
                color = when {
                    isSelected -> colors.accent
                    mapped -> colors.tilePressed
                    else -> colors.tile
                },
                topLeft = Offset(x + 1f, y + 1f),
                size = Size(cellW - 2f, cellH - 2f),
            )
            if (used.contains(ch)) {
                drawRect(
                    colors.border,
                    topLeft = Offset(x + 1f, y + 1f),
                    size = Size(cellW - 2f, cellH - 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f),
                )
            }
            if (ch != ' ') {
                paint.color = if (isSelected) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                paint.textSize = cellH * 0.62f
                paint.alpha = if (mapped) 140 else 255
                val cy = y + cellH / 2f - (paint.ascent() + paint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(ch.toString(), x + cellW / 2f, cy, paint)
            }
        }
    }
}

private fun DecoderRingEngine.State.usedLetters(): Set<Char> = DecoderRingEngine.puzzle(puzzleIndex).used

private typealias DrawScopeCompat = androidx.compose.ui.graphics.drawscope.DrawScope

private fun DrawScopeCompat.drawDecoderBoard(
    puzzle: DecoderRingEngine.Puzzle,
    state: DecoderRingEngine.State,
    colors: com.heretek.dorado_hd.design.DoradoColors,
    zoom: Float,
    pan: Offset,
) {
    val base = min(size.width / (puzzle.width + 1f), size.height / (puzzle.height + 1f))
    val cell = base * zoom
    val boardW = cell * puzzle.width
    val boardH = cell * puzzle.height
    val ox = (size.width - boardW) / 2f + pan.x
    val oy = (size.height - boardH) / 2f + pan.y
    val paint = decoderPaint
    for (row in 0 until puzzle.height) {
        for (col in 0 until puzzle.width) {
            val x = ox + col * cell
            val y = oy + row * cell
            val symbol = puzzle.symbolAt(row, col)
            if (symbol == 0) {
                drawRect(colors.elevated, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f))
                continue
            }
            val placed = state.mapping[symbol]
            val highlighted = state.highlight == symbol
            val correct = placed != null && puzzle.symbolOf(placed) == symbol
            val bg = when {
                highlighted -> colors.tilePressed
                placed != null && state.checkEnabled && !correct -> DoradoAccent.PINK.primary
                placed != null && correct -> colors.accent
                else -> colors.tile
            }
            drawRect(bg, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f))
            if (symbol in state.stone) {
                drawRect(colors.border, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            }
            if (placed != null) {
                paint.color = if (bg == colors.tilePressed || bg == DoradoAccent.PINK.primary) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                paint.textSize = cell * 0.58f
                val cy = y + cell / 2f - (paint.ascent() + paint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(placed.toString(), x + cell / 2f, cy, paint)
            } else {
                drawDecoderGlyph(symbol, Offset(x + cell / 2f, y + cell / 2f), cell * 0.3f, colors.textSecondary)
            }
        }
    }
}

private val decoderPaint: Paint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun DrawScopeCompat.drawDecoderGlyph(symbol: Int, center: Offset, radius: Float, color: Color) {
    val family = DecoderRingEngine.symbolFamily(symbol)
    val pips = DecoderRingEngine.symbolPips(symbol) + 1
    for (i in 0 until pips) {
        val angle = i * (2.0 * Math.PI / pips) + family
        val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
        when (family) {
            0, 1 -> drawCircle(color, radius * 0.28f, Offset(x, y))
            2, 3 -> drawRect(color, Offset(x - radius * 0.22f, y - radius * 0.22f), Size(radius * 0.44f, radius * 0.44f))
            4 -> {
                drawRect(color, Offset(center.x - radius * 0.5f, center.y - 1.4f), Size(radius, 2.8f))
            }

            else -> {
                drawRect(color, Offset(center.x - 1.4f, center.y - radius * 0.5f), Size(2.8f, radius))
            }
        }
    }
}

private fun decoderHitTest(
    offset: Offset,
    width: Float,
    height: Float,
    zoom: Float,
    pan: Offset,
    puzzle: DecoderRingEngine.Puzzle,
): Int {
    val base = min(width / (puzzle.width + 1f), height / (puzzle.height + 1f))
    val cell = base * zoom
    val boardW = cell * puzzle.width
    val boardH = cell * puzzle.height
    val ox = (width - boardW) / 2f + pan.x
    val oy = (height - boardH) / 2f + pan.y
    val col = ((offset.x - ox) / cell).toInt()
    val row = ((offset.y - oy) / cell).toInt()
    if (col !in 0 until puzzle.width || row !in 0 until puzzle.height) return -1
    return puzzle.symbolAt(row, col)
}

@Composable
private fun DecoderButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
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
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}
