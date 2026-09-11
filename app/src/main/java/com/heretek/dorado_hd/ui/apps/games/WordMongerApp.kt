package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min

private const val WORDMONGER_SLUG = "wordmonger"

@Composable
fun WordMongerApp() {
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
    var game by remember { mutableStateOf<WordMongerEngine.State?>(null) }
    var saved by remember { mutableStateOf<WordMongerEngine.State?>(null) }
    var paused by remember { mutableStateOf(false) }
    var swapMode by remember { mutableStateOf(false) }
    var swapAnchor by remember { mutableStateOf<Int?>(null) }
    var dragStart by remember { mutableStateOf<Int?>(null) }
    var dragEnd by remember { mutableStateOf<Int?>(null) }
    var nickname by remember { mutableStateOf("player") }
    var tutorial by remember { mutableStateOf(true) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scores by graph.games.top(WORDMONGER_SLUG, 10).collectAsState(initial = emptyList())
    val latestGame = rememberUpdatedState(game)

    LaunchedEffect(Unit) {
        nickname = graph.appState.get("$WORDMONGER_SLUG.nick") ?: "player"
        saved = WordMongerEngine.decode(graph.appState.get(WORDMONGER_SLUG))
        loaded = true
    }

    val active = screen == "game" && game != null && !paused && game?.gameOver == false
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(20)
            val current = latestGame.value ?: break
            if (current.gameOver) break
            val next = WordMongerEngine.step(current, 20)
            next.events.forEach { wordmongerSfx(it)?.let(bank::play) }
            game = next
        }
    }

    LaunchedEffect(game, loaded) {
        val current = game ?: return@LaunchedEffect
        if (loaded && !current.gameOver) {
            kotlinx.coroutines.delay(400)
            graph.appState.put(WORDMONGER_SLUG, WordMongerEngine.encode(current))
        }
    }

    LaunchedEffect(game?.gameOver) {
        val current = game ?: return@LaunchedEffect
        if (current.gameOver && !recorded) {
            recorded = true
            graph.appState.clear(WORDMONGER_SLUG)
            graph.games.record(WORDMONGER_SLUG, current.score, "level ${current.level} best ${current.bestWord}")
            bank.play("lose")
        }
    }

    fun startGame(fresh: Boolean) {
        game = if (fresh) WordMongerEngine.newGame(1, (System.currentTimeMillis() and 0x7FFFFFFF).toInt(), tutorial) else saved
        saved = null
        paused = false
        swapMode = false
        swapAnchor = null
        dragStart = null
        dragEnd = null
        recorded = false
        screen = "game"
    }

    fun commitWord() {
        val state = game ?: return
        if (state.selection.size < 2) return
        val next = WordMongerEngine.submit(state)
        game = next
        next.events.forEach { wordmongerSfx(it)?.let(bank::play) }
    }

    if (screen == "menu") {
        WordMongerMenu(
            saved = saved,
            best = scores.firstOrNull()?.score ?: 0,
            onPlay = { startGame(fresh = true) },
            onResume = { startGame(fresh = false) },
            onScores = { screen = "scores" },
            onRules = { screen = "rules" },
            onOptions = { screen = "options" },
        )
        return
    }
    if (screen == "scores") {
        WordMongerScores(scores = scores, onBack = { screen = "menu" })
        return
    }
    if (screen == "rules") {
        WordMongerRules(onBack = { screen = "menu" })
        return
    }
    if (screen == "options") {
        WordMongerOptions(
            nickname = nickname,
            tutorial = tutorial,
            onNickname = { nickname = it.take(12) },
            onTutorial = { tutorial = it },
            onSave = {
                scope.launch { graph.appState.put("$WORDMONGER_SLUG.nick", nickname) }
                screen = "menu"
            },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "wordmonger", onBack = { paused = true }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${current.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = "level ${current.level}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = if (current.rushActive) "rush ${current.rushMs / 1000}s" else "tiles ${current.liveTiles}",
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_CAPTION.sp,
                        color = if (current.rushActive) colors.accentBright else colors.textSecondary,
                    ),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = current.bonusWord?.let { "bonus ${it.length}" } ?: "hattrick ${current.longStreak}/3",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val textMeasurer = rememberTextMeasurer()
                val letterStyle = TextStyle(fontFamily = Selawik, fontSize = 20.sp, color = colors.textPrimary)
                val smallStyle = TextStyle(fontFamily = Selawik, fontSize = 9.sp, color = colors.accentBright)
                val boardSide = min(maxWidth.value, maxHeight.value).dp
                Canvas(
                    Modifier
                        .size(boardSide)
                        .pointerInput(active, swapMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val cell = wordmongerCellAt(offset, size.width, size.height)
                                    dragStart = cell
                                    dragEnd = cell
                                    if (cell != null && swapMode) swapAnchor = cell
                                },
                                onDrag = { change, _ ->
                                    val cell = wordmongerCellAt(change.position, size.width, size.height)
                                    if (cell != null && cell != dragEnd) {
                                        dragEnd = cell
                                        val state = latestGame.value
                                        if (!swapMode && state != null) {
                                            game = WordMongerEngine.select(state, cell)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val end = dragEnd
                                    val state = latestGame.value
                                    if (state != null && start != null && end != null && start != end) {
                                        if (swapMode) {
                                            val next = WordMongerEngine.swap(state, start, end)
                                            game = next
                                            next.events.forEach { wordmongerSfx(it)?.let(bank::play) }
                                        } else if (state.selection.size >= 2) {
                                            commitWord()
                                        }
                                    }
                                    dragStart = null
                                    dragEnd = null
                                    swapAnchor = null
                                },
                            )
                        }
                        .pointerInput(active, swapMode) {
                            detectTapGestures { offset ->
                                val cell = wordmongerCellAt(offset, size.width, size.height) ?: return@detectTapGestures
                                val state = latestGame.value ?: return@detectTapGestures
                                if (swapMode) {
                                    val anchor = swapAnchor
                                    if (anchor == null) {
                                        swapAnchor = cell
                                    } else if (anchor != cell) {
                                        val next = WordMongerEngine.swap(state, anchor, cell)
                                        game = next
                                        next.events.forEach { wordmongerSfx(it)?.let(bank::play) }
                                        swapAnchor = cell
                                    }
                                } else {
                                    val next = WordMongerEngine.select(state, cell)
                                    game = next
                                    next.events.forEach { wordmongerSfx(it)?.let(bank::play) }
                                }
                            }
                        },
                ) {
                    drawWordMongerBoard(current, colors, textMeasurer, letterStyle, smallStyle)
                }
                if (paused) {
                    WordMongerOverlay(
                        title = "paused",
                        subtitle = "score ${current.score} · level ${current.level}",
                        actions = listOf(
                            "resume" to { paused = false },
                            "save & exit" to {
                                paused = false
                                game = null
                                screen = "menu"
                            },
                        ),
                    )
                } else if (current.gameOver) {
                    WordMongerOverlay(
                        title = "game over",
                        subtitle = "score ${current.score} · level ${current.level} · best ${current.bestWord.ifEmpty { "—" }}",
                        actions = listOf(
                            "play again" to { startGame(fresh = true) },
                            "scores" to { game = null; screen = "scores" },
                            "main menu" to { game = null; screen = "menu" },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                WordMongerButton(if (swapMode) "swap on" else "select on", Modifier.weight(1f)) {
                    swapMode = !swapMode
                    swapAnchor = null
                }
                WordMongerButton("submit", Modifier.weight(1f), enabled = current.selection.size >= 2) { commitWord() }
                WordMongerButton("clear", Modifier.weight(1f), enabled = current.selection.isNotEmpty()) {
                    game = WordMongerEngine.clearSelection(current)
                }
                WordMongerButton("pause", Modifier.weight(1f)) { paused = true }
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = current.message.ifEmpty { if (swapMode) "drag or tap two neighbours to swap" else "drag a straight line of letters, then release" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            BasicText(
                text = "words ${current.words} · power ${current.wordPower.toInt()} · best word ${current.bestWord.ifEmpty { "—" }}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

private fun wordmongerCellAt(offset: Offset, width: Int, height: Int): Int? {
    val side = min(width, height).toFloat()
    val ox = (width - side) / 2f
    val oy = (height - side) / 2f
    if (offset.x < ox || offset.y < oy || offset.x > ox + side || offset.y > oy + side) return null
    val cell = side / WordMongerEngine.COLS
    val column = floor((offset.x - ox) / cell).toInt().coerceIn(0, WordMongerEngine.COLS - 1)
    val rowIndex = floor((offset.y - oy) / cell).toInt().coerceIn(0, WordMongerEngine.ROWS - 1)
    return WordMongerEngine.cellIndex(rowIndex, column)
}

private fun DrawScope.drawWordMongerBoard(
    state: WordMongerEngine.State,
    colors: DoradoColors,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    letterStyle: TextStyle,
    smallStyle: TextStyle,
) {
    val side = min(size.width, size.height)
    val ox = (size.width - side) / 2f
    val oy = (size.height - side) / 2f
    val cell = side / WordMongerEngine.COLS
    drawRect(colors.background, Offset(ox, oy), Size(side, side))
    for (index in 0 until WordMongerEngine.CELLS) {
        val r = WordMongerEngine.row(index)
        val c = WordMongerEngine.col(index)
        val tile = state.tiles[index]
        val x = ox + c * cell
        val y = oy + r * cell
        val fall = (tile?.fallCells ?: 0f) * cell
        drawRect(colors.elevated, Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f))
        val multiplier = state.multipliers[index]
        if (multiplier > 1) {
            drawRect(colors.accent.copy(alpha = 0.20f), Offset(x + 1f, y + 1f), Size(cell - 2f, cell - 2f))
        }
        if (tile != null) {
            val selected = index in state.selection
            drawRect(
                if (selected) colors.tilePressed else colors.tile,
                Offset(x + 2f, y + 2f - fall),
                Size(cell - 4f, cell - 4f),
            )
            drawRect(
                if (selected) colors.accent else colors.border,
                Offset(x + 2f, y + 2f - fall),
                Size(cell - 4f, cell - 4f),
                style = Stroke(if (selected) 2f else 1f),
            )
            val measured = textMeasurer.measure(tile.letter.uppercaseChar().toString(), letterStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = tile.letter.uppercaseChar().toString(),
                topLeft = Offset(x + (cell - measured.size.width) / 2f, y + (cell - measured.size.height) / 2f - fall),
                style = letterStyle,
            )
            if (multiplier > 1) {
                val tag = textMeasurer.measure("x$multiplier", smallStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "x$multiplier",
                    topLeft = Offset(x + cell - tag.size.width - 4f, y + 3f - fall),
                    style = smallStyle,
                )
            }
            if (tile.frozen) {
                drawRect(colors.textPrimary, Offset(x + cell * 0.3f, y + cell * 0.3f - fall), Size(cell * 0.4f, cell * 0.3f), style = Stroke(2f))
                drawCircle(colors.textPrimary, cell * 0.12f, Offset(x + cell * 0.5f, y + cell * 0.32f - fall), style = Stroke(2f))
            }
            if (tile.cracked) {
                drawLine(colors.accentBright, Offset(x + cell * 0.3f, y + cell * 0.35f - fall), Offset(x + cell * 0.6f, y + cell * 0.7f - fall), 1.5f)
                drawLine(colors.accentBright, Offset(x + cell * 0.6f, y + cell * 0.35f - fall), Offset(x + cell * 0.5f, y + cell * 0.6f - fall), 1.5f)
            }
            if (tile.isBomb) {
                drawCircle(DoradoAccent.ORANGE.primary, cell * 0.26f, Offset(x + cell * 0.5f, y + cell * 0.56f - fall))
                drawLine(
                    colors.textPrimary,
                    Offset(x + cell * 0.58f, y + cell * 0.38f - fall),
                    Offset(x + cell * 0.72f, y + cell * 0.24f - fall),
                    2f,
                )
                if (tile.bombRemainingMs <= WordMongerEngine.BOMB_WARN_MS) {
                    drawCircle(colors.accentBright, cell * 0.3f, Offset(x + cell * 0.5f, y + cell * 0.52f - fall), style = Stroke(2f))
                }
            }
        }
    }
    if (state.selection.size >= 2) {
        val first = state.selection.first()
        val last = state.selection.last()
        drawLine(
            colors.accentBright,
            Offset(ox + (WordMongerEngine.col(first) + 0.5f) * cell, oy + (WordMongerEngine.row(first) + 0.5f) * cell),
            Offset(ox + (WordMongerEngine.col(last) + 0.5f) * cell, oy + (WordMongerEngine.row(last) + 0.5f) * cell),
            3f,
        )
    }
    state.bird?.let { bird ->
        val bx = ox + (bird.xCells + 0.5f) * cell
        val by = oy + (bird.row + 0.5f) * cell
        drawCircle(DoradoAccent.ORANGE.bright, cell * 0.28f, Offset(bx, by))
        drawCircle(colors.background, cell * 0.08f, Offset(bx + cell * 0.14f, by - cell * 0.06f))
        drawRect(DoradoAccent.ORANGE.primary, Offset(bx - cell * 0.2f, by + cell * 0.2f), Size(cell * 0.4f, cell * 0.1f))
    }
    state.egg?.let { egg ->
        val ex = ox + (egg.col + 0.5f) * cell
        val ey = oy + egg.row.coerceAtLeast(0f) * cell
        drawCircle(colors.textPrimary, cell * 0.16f, Offset(ex, ey))
    }
}

private fun wordmongerSfx(event: WordMongerEngine.Event): String? = when (event) {
    WordMongerEngine.Event.TILE -> "click"
    WordMongerEngine.Event.DESELECT -> "back"
    WordMongerEngine.Event.ACCEPT -> "score"
    WordMongerEngine.Event.REJECT -> "error"
    WordMongerEngine.Event.HATTRICK -> "coin"
    WordMongerEngine.Event.BONUS -> "tick"
    WordMongerEngine.Event.LEVEL -> "win"
    WordMongerEngine.Event.RUSH -> "whoosh"
    WordMongerEngine.Event.CLEAR -> "coin"
    WordMongerEngine.Event.SWAP -> "rotate"
    WordMongerEngine.Event.ROCKET -> "explode"
    WordMongerEngine.Event.BOMB -> "tick"
    WordMongerEngine.Event.BOMB_WARN -> "tick"
    WordMongerEngine.Event.EXPLODE, WordMongerEngine.Event.GAMEOVER -> "explode"
    WordMongerEngine.Event.BIRD -> "whoosh"
    WordMongerEngine.Event.EGG -> "pop"
    WordMongerEngine.Event.BIRD_HIT -> "hit"
    WordMongerEngine.Event.CRACK -> "hit"
    WordMongerEngine.Event.DEFUSE -> "select"
    WordMongerEngine.Event.RESUME -> null
}

@Composable
private fun WordMongerOverlay(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = subtitle,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> WordMongerButton(label) { action() } }
            }
        }
    }
}

@Composable
private fun WordMongerMenu(
    saved: WordMongerEngine.State?,
    best: Int,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onScores: () -> Unit,
    onRules: () -> Unit,
    onOptions: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "wordmonger") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                WordMongerButton("continue level ${saved.level} · ${saved.score}", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(4.dp))
            }
            WordMongerButton("play", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            WordMongerButton("high scores", Modifier.fillMaxWidth()) { onScores() }
            Spacer(Modifier.height(4.dp))
            WordMongerButton("rules", Modifier.fillMaxWidth()) { onRules() }
            Spacer(Modifier.height(4.dp))
            WordMongerButton("options", Modifier.fillMaxWidth()) { onOptions() }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun WordMongerScores(scores: List<com.heretek.dorado_hd.data.db.GameScoreEntity>, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "wordmonger · scores", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState())) {
            if (scores.isEmpty()) {
                BasicText(
                    text = "no scores yet",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textInactive),
                )
            }
            scores.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "${index + 1}.",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = row.meta ?: "",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = "${row.score}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordMongerRules(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "wordmonger · rules", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState())) {
            listOf(
                "drag a straight line of tiles across a row or column to build a word, then release.",
                "shorter words are easy; longer words score more and build your word power.",
                "marked cells multiply tile values by two, three or four.",
                "three words in a row longer than four letters earn a hattrick: the third doubles.",
                "a bonus word is announced periodically and scores four times.",
                "bombs tick down from thirty seconds; use the bomb tile in a word to defuse it.",
                "frozen tiles cannot move; cracked tiles shatter when you use them.",
                "switch to swap and drag a neighbour to shift tiles; seven swaps can call a rocket.",
                "when few words remain, rush mode gives you thirty seconds to finish the level.",
                "our own compact dictionary is used, not the device word list.",
            ).forEach {
                BasicText(
                    text = it,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun WordMongerOptions(
    nickname: String,
    tutorial: Boolean,
    onNickname: (String) -> Unit,
    onTutorial: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "wordmonger · options", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "nickname",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(colors.tile)
                    .border(0.5.dp, colors.border),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = nickname,
                    onValueChange = onNickname,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "tutorial",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WordMongerButton(if (tutorial) "on*" else "on") { onTutorial(true) }
                WordMongerButton(if (!tutorial) "off*" else "off") { onTutorial(false) }
            }
            Spacer(Modifier.height(10.dp))
            WordMongerButton("save", Modifier.fillMaxWidth()) { onSave() }
            Spacer(Modifier.height(4.dp))
            WordMongerButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun WordMongerButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, if (enabled) colors.border else colors.elevated)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures(onTap = { onClick() }) },
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
