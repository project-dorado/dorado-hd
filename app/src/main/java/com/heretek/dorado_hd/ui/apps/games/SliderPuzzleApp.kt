package com.heretek.dorado_hd.ui.apps.games

import android.graphics.Paint
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/* ============================================================ */
/*                        Slider Puzzle                           */
/* ============================================================ */

@Composable
fun SliderPuzzleApp() {
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
    var game by remember { mutableStateOf<SlideState?>(null) }
    var saved by remember { mutableStateOf<SlideState?>(null) }
    var elapsed by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var showNumbers by remember { mutableStateOf(true) }
    var peek by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scoreFlow by graph.games.top("slider-puzzle", 60).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        saved = SliderPuzzleEngine.decode(graph.appState.get("slider-puzzle"))
        loaded = true
    }

    LaunchedEffect(running) {
        while (running) {
            delay(100)
            elapsed = SliderPuzzleEngine.advanceClock(elapsed, 100L, running)
        }
    }

    // 60 Hz slide interpolation while a tapped tile is animating. Pause holds
    // the animation too, so this loop is keyed on `paused`.
    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = game ?: continue
            if (current.anim != null) game = SliderPuzzleEngine.step(current, 16)
        }
    }

    // Periodic snapshot: a debounce keyed on `elapsed` could never land (100 ms
    // ticks beat the 500 ms delay), and a pending write died with the screen.
    LaunchedEffect(screen, paused, loaded, game?.solved) {
        if (!loaded || screen != "game" || paused || game?.solved != false) return@LaunchedEffect
        while (true) {
            delay(2000)
            val snapshot = game ?: break
            if (snapshot.solved) break
            graph.appState.put("slider-puzzle", SliderPuzzleEngine.encode(snapshot.copy(elapsedMs = elapsed)))
        }
    }

    fun persistRun() {
        val snapshot = game ?: return
        if (snapshot.solved) return
        scope.launch(NonCancellable) {
            graph.appState.put("slider-puzzle", SliderPuzzleEngine.encode(snapshot.copy(elapsedMs = elapsed)))
        }
    }

    LaunchedEffect(game?.solved) {
        val current = game ?: return@LaunchedEffect
        if (current.solved && !recorded) {
            recorded = true
            running = false
            graph.games.record("slider-puzzle", -elapsed.toInt(), current.size.label)
            graph.appState.clear("slider-puzzle")
            bank.play("win")
        }
    }

    fun startNew(size: SlideSize) {
        val fresh = SliderPuzzleEngine.newGame(size, kotlin.random.Random(System.nanoTime()))
        game = fresh
        elapsed = 0
        running = true
        paused = false
        showResults = false
        recorded = false
        peek = false
        screen = "game"
    }

    fun resume() {
        val resumeGame = saved ?: return
        game = resumeGame
        elapsed = resumeGame.elapsedMs
        running = true
        paused = false
        showResults = false
        recorded = false
        peek = false
        screen = "game"
    }

    if (screen == "menu") {
        SliderMenu(
            saved = saved,
            scores = scoreFlow.mapNotNull { row ->
                val size = row.meta?.let { SlideSize.fromLabel(it) } ?: return@mapNotNull null
                Triple(size, -row.score.toLong(), row.playedAt)
            },
            onResume = { resume() },
            onStart = { startNew(it) },
        )
        return
    }

    val current = game ?: return
    val solved = current.solved

    DetailScaffold(title = "slider puzzle · ${current.size.label}", onBack = { paused = true; running = false }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "moves ${current.moves}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "%d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = "${current.size.rows}×${current.size.cols}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val metrics = sliderMetrics(current.size, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(current, solved) {
                                detectTapGestures { offset ->
                                    if (solved) {
                                        showResults = true
                                        return@detectTapGestures
                                    }
                                    if (peek) return@detectTapGestures
                                    val cell = metrics.cellAt(offset.x, offset.y)
                                    if (cell != null && SliderPuzzleEngine.canMove(current, cell)) {
                                        game = SliderPuzzleEngine.applyMove(current, cell)
                                        bank.play("pop")
                                    }
                                }
                            },
                    ) {
                        drawSliderBoard(current, metrics, colors, showNumbers, peek)
                    }
                    if (solved && !showResults) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) { detectTapGestures { showResults = true } },
                        ) {
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(colors.background.copy(alpha = 0.85f))
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                BasicText(
                                    text = "solved · tap to continue",
                                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accentBright),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    EdgeText("numbers ${if (showNumbers) "on" else "off"}", if (showNumbers) colors.accent else colors.textSecondary) {
                        showNumbers = !showNumbers
                    }
                    Spacer(Modifier.width(10.dp))
                    EdgeText("peek", if (peek) colors.accent else colors.textSecondary) { peek = !peek }
                    Spacer(Modifier.weight(1f))
                    EdgeText("pause", colors.textPrimary) { paused = true; running = false }
                }
            }

            if (showResults) {
                SliderResults(
                    state = current,
                    elapsedMs = elapsed,
                    scores = scoreFlow,
                    onPlayAgain = { startNew(current.size) },
                    onMenu = { screen = "menu"; game = null; saved = null; paused = false; showResults = false },
                )
            }

            if (paused && !showResults) {
                SliderPause(
                    numbers = showNumbers,
                    peek = peek,
                    onNumbers = { showNumbers = !showNumbers },
                    onPeek = { peek = !peek },
                    onResume = { paused = false; running = true },
                    onRestart = { startNew(current.size) },
                    onMenu = {
                        persistRun()
                        screen = "menu"
                        game = null
                        saved = null
                        paused = false
                        showResults = false
                    },
                )
            }
        }
    }
}

@Composable
private fun sliderButton(label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun SliderMenu(
    saved: SlideState?,
    scores: List<Triple<SlideSize, Long, Long>>,
    onResume: () -> Unit,
    onStart: (SlideSize) -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "slider puzzle") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                sliderButton("continue ${saved.size.label} · ${saved.moves} moves", Modifier.fillMaxWidth(), active = true) { onResume() }
                Spacer(Modifier.height(8.dp))
            }
            SlideSize.entries.forEach { size ->
                val best = scores.filter { it.first == size }.minByOrNull { it.second }?.second
                sliderButton(
                    "${size.label} tiles · ${size.rows}×${size.cols}" + (best?.let { " · best ${formatTime(it)}" } ?: ""),
                    Modifier.fillMaxWidth(),
                ) { onStart(size) }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "tap a tile next to the hole to slide it",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun SliderResults(
    state: SlideState,
    elapsedMs: Long,
    scores: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val forSize = scores.filter { it.meta == state.size.label }.take(10)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = "solved",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
        )
        BasicText(
            text = "${formatTime(elapsedMs)} · ${state.moves} moves",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "fastest ${state.size.label} tiles",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        if (forSize.isEmpty()) {
            BasicText(
                text = "no times yet",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textInactive),
            )
        } else {
            forSize.forEachIndexed { index, row ->
                BasicText(
                    text = "${index + 1}. ${formatTime(-row.score.toLong())}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (index == 0) colors.accentBright else colors.textPrimary),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sliderButton("play again", Modifier.weight(1f)) { onPlayAgain() }
            sliderButton("choose size", Modifier.weight(1f)) { onMenu() }
        }
    }
}

@Composable
private fun SliderPause(
    numbers: Boolean,
    peek: Boolean,
    onNumbers: () -> Unit,
    onPeek: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.94f))
            .padding(DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = "paused",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        sliderButton("resume", Modifier.fillMaxWidth()) { onResume() }
        sliderButton("number overlay ${if (numbers) "on" else "off"}", Modifier.fillMaxWidth(), active = numbers) { onNumbers() }
        sliderButton("image peek", Modifier.fillMaxWidth(), active = peek) { onPeek() }
        sliderButton("restart puzzle", Modifier.fillMaxWidth()) { onRestart() }
        sliderButton("main menu", Modifier.fillMaxWidth()) { onMenu() }
    }
}

private class SlideMetrics(
    val cellW: Float,
    val cellH: Float,
    val ox: Float,
    val oy: Float,
    val rows: Int,
    val cols: Int,
) {
    fun topLeft(cell: Int): Offset =
        Offset(ox + (cell % cols) * cellW, oy + (cell / cols) * cellH)

    fun cellAt(x: Float, y: Float): Int? {
        if (x < ox || y < oy) return null
        val col = ((x - ox) / cellW).toInt()
        val row = ((y - oy) / cellH).toInt()
        if (col !in 0 until cols || row !in 0 until rows) return null
        return row * cols + col
    }
}

private fun sliderMetrics(size: SlideSize, width: Float, height: Float): SlideMetrics {
    val baseW = SliderPuzzleEngine.SCREEN_WIDTH.toFloat() / size.cols
    val baseH = SliderPuzzleEngine.BOARD_HEIGHT.toFloat() / size.rows
    val scale = min(width / (baseW * size.cols), height / (baseH * size.rows))
    val cellW = baseW * scale
    val cellH = baseH * scale
    return SlideMetrics(
        cellW = cellW,
        cellH = cellH,
        ox = (width - cellW * size.cols) / 2f,
        oy = (height - cellH * size.rows) / 2f,
        rows = size.rows,
        cols = size.cols,
    )
}

private fun DrawScope.drawSliderBoard(
    state: SlideState,
    metrics: SlideMetrics,
    colors: DoradoColors,
    showNumbers: Boolean,
    peek: Boolean,
) {
    val size = state.size
    val boardW = metrics.cellW * size.cols
    val boardH = metrics.cellH * size.rows
    drawRect(
        color = colors.elevated,
        topLeft = Offset(metrics.ox, metrics.oy),
        size = Size(boardW, boardH),
    )

    if (peek) {
        drawSlidePicture(Rect(metrics.ox, metrics.oy, metrics.ox + boardW, metrics.oy + boardH), colors)
        return
    }

    val anim = state.anim
    val t = anim?.let { (it.elapsedMs.toFloat() / SliderPuzzleEngine.SLIDE_MS).coerceIn(0f, 1f) } ?: 0f
    for (cell in 0 until size.tiles) {
        val tile = state.cells[cell]
        if (tile == SliderPuzzleEngine.BLANK) continue
        val home = SliderPuzzleEngine.homeCell(size, tile)
        val target = metrics.topLeft(cell)
        val origin = metrics.topLeft(home)
        var drawAt = target
        if (anim != null && cell == anim.toCell) {
            val from = metrics.topLeft(anim.fromCell)
            drawAt = Offset(
                from.x + (target.x - from.x) * t,
                from.y + (target.y - from.y) * t,
            )
        }
        clipRect(drawAt.x, drawAt.y, drawAt.x + metrics.cellW, drawAt.y + metrics.cellH) {
            translate(drawAt.x - origin.x, drawAt.y - origin.y) {
                drawSlidePicture(Rect(metrics.ox, metrics.oy, metrics.ox + boardW, metrics.oy + boardH), colors)
            }
        }
        drawRect(
            color = colors.border,
            topLeft = drawAt,
            size = Size(metrics.cellW, metrics.cellH),
            style = Stroke(width = 1f),
        )
        if (showNumbers) {
            drawTileNumber(size.tiles - tile, drawAt, metrics.cellW, metrics.cellH, colors)
        }
    }
}

/** Re-authored procedural tile art: a token-gradient sky with hills and sun. */
private fun DrawScope.drawSlidePicture(rect: Rect, colors: DoradoColors) {
    val top = colors.elevated
    val skyTop = colors.tilePressed
    val skyBottom = colors.accent
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(skyTop, skyBottom),
            start = Offset(rect.left, rect.top),
            end = Offset(rect.left, rect.bottom),
        ),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
    drawCircle(
        color = colors.accentBright,
        radius = rect.width * 0.14f,
        center = Offset(rect.left + rect.width * 0.72f, rect.top + rect.height * 0.26f),
    )
    val hills = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.left, rect.top + rect.height * 0.66f)
        lineTo(rect.left + rect.width * 0.30f, rect.top + rect.height * 0.44f)
        lineTo(rect.left + rect.width * 0.55f, rect.top + rect.height * 0.70f)
        lineTo(rect.left + rect.width * 0.78f, rect.top + rect.height * 0.50f)
        lineTo(rect.right, rect.top + rect.height * 0.72f)
        lineTo(rect.right, rect.bottom)
        close()
    }
    drawPath(hills, color = DoradoAccent.CYAN.primary.copy(alpha = 0.85f))
    drawRect(
        color = top,
        topLeft = Offset(rect.left, rect.top + rect.height * 0.82f),
        size = Size(rect.width, rect.height * 0.18f),
    )
    drawLine(
        color = DoradoAccent.ORANGE.primary,
        start = Offset(rect.left + rect.width * 0.12f, rect.top + rect.height * 0.88f),
        end = Offset(rect.left + rect.width * 0.34f, rect.top + rect.height * 0.88f),
        strokeWidth = rect.width * 0.03f,
    )
}

private fun DrawScope.drawTileNumber(number: Int, topLeft: Offset, cellW: Float, cellH: Float, colors: DoradoColors) {
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = cellH * 0.30f
        textAlign = Paint.Align.CENTER
        color = if (number <= 2) colors.background.toArgb() else Color.White.toArgb()
    }
    val x = topLeft.x + cellW / 2f
    val y = topLeft.y + cellH / 2f - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(number.toString(), x, y, paint)
}

private fun formatTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60000, (safe / 1000) % 60)
}
