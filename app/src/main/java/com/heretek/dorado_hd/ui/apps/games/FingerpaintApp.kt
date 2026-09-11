package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
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
import kotlin.math.ceil

/*
 * Fingerpaint (Zune "Quickdraw") — local adaptation of the draw-and-guess
 * party game. The original network lobby is replaced with single-device
 * pass-and-play: the drawer holds "peek" to read the secret word, draws on the
 * board, and the current guesser types an answer below. Solo draw keeps the
 * canvas without rounds. Round timing and scoring rules are unchanged.
 */

/** Palette slots mapped onto theme tokens (engine stores only indices). */
private fun fingerpaintPalette(colors: DoradoColors): List<Color> = listOf(
    DoradoAccent.PINK.primary,
    DoradoAccent.ORANGE.primary,
    DoradoAccent.CYAN.primary,
    DoradoAccent.LIME.primary,
    DoradoAccent.PURPLE.primary,
    DoradoAccent.PINK.bright,
    DoradoAccent.ORANGE.bright,
    colors.textPrimary,
)

@Composable
fun FingerpaintApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    val colors = LocalDoradoColors.current
    val palette = remember(colors) { fingerpaintPalette(colors) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var session by remember { mutableStateOf<FingerpaintSession?>(null) }
    var saved by remember { mutableStateOf<FingerpaintSession?>(null) }
    var brush by remember { mutableStateOf(FingerpaintBrush()) }
    var playerCount by remember { mutableStateOf(2) }
    var recorded by remember { mutableStateOf(false) }
    val scores by graph.games.top("fingerpaint", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        saved = graph.appState.get("fingerpaint")?.let { FingerpaintEngine.decode(it) }
    }

    LaunchedEffect(session) {
        val current = session ?: return@LaunchedEffect
        delay(500)
        if (current.phase == FingerpaintPhase.GAME_OVER) {
            graph.appState.clear("fingerpaint")
        } else {
            graph.appState.put("fingerpaint", FingerpaintEngine.encode(current))
        }
    }

    LaunchedEffect(session?.phase, session?.round, session?.roundState?.running) {
        while (true) {
            val current = session ?: break
            if (current.mode != FingerpaintMode.PARTY) break
            if (current.phase != FingerpaintPhase.DRAW || current.roundState?.running != true) break
            delay(250)
            val next = FingerpaintEngine.tick(current, 250)
            session = next
            if (next.phase == FingerpaintPhase.ROUND_OVER) bank.play("lose")
        }
    }

    LaunchedEffect(session?.phase) {
        val current = session ?: return@LaunchedEffect
        if (current.phase == FingerpaintPhase.GAME_OVER && !recorded) {
            recorded = true
            val best = current.players.maxOfOrNull { it.score } ?: 0
            val winners = FingerpaintEngine.winnerNames(current).joinToString("+")
            graph.games.record(
                "fingerpaint",
                best,
                "party|players=${current.players.size}|rounds=${current.totalRounds}|winner=$winners",
            )
        }
    }

    fun startParty() {
        val seed = System.currentTimeMillis().toInt()
        val names = (1..playerCount).map { "player $it" }
        session = FingerpaintEngine.startRound(FingerpaintEngine.newSession(names, seed), seed)
        brush = FingerpaintBrush()
        recorded = false
        screen = "game"
        bank.play("select")
    }

    fun restart() {
        val names = session?.players?.map { it.name } ?: return
        val seed = System.currentTimeMillis().toInt()
        session = FingerpaintEngine.startRound(FingerpaintEngine.newSession(names, seed), seed)
        brush = FingerpaintBrush()
        recorded = false
        bank.play("select")
    }

    fun continueSaved() {
        val restored = saved ?: return
        session = restored
        brush = FingerpaintBrush()
        recorded = restored.phase == FingerpaintPhase.GAME_OVER
        screen = if (restored.mode == FingerpaintMode.SOLO) "solo" else "game"
    }

    if (screen == "menu") {
        FingerpaintMenu(
            saved = saved,
            best = scores.firstOrNull()?.score ?: 0,
            onSolo = {
                val seed = System.currentTimeMillis().toInt()
                session = FingerpaintEngine.soloSession(seed)
                brush = FingerpaintBrush()
                recorded = false
                screen = "solo"
                bank.play("select")
            },
            onParty = { screen = "setup"; bank.play("select") },
            onHistory = { screen = "history"; bank.play("tick") },
            onContinue = { continueSaved() },
        )
        return
    }
    if (screen == "setup") {
        FingerpaintSetup(
            count = playerCount,
            onCount = { playerCount = it },
            onStart = { startParty() },
            onBack = { screen = "menu"; bank.play("back") },
        )
        return
    }
    if (screen == "history") {
        FingerpaintHistoryScreen(
            history = session?.history.orEmpty(),
            palette = palette,
            onMenu = { screen = "menu"; bank.play("back") },
        )
        return
    }

    val current = session ?: return
    if (screen == "solo") {
        FingerpaintSolo(
            session = current,
            brush = brush,
            palette = palette,
            onBrush = { brush = it },
            onSession = { session = it },
            onMenu = { screen = "menu"; bank.play("back") },
            bank = bank,
        )
        return
    }

    FingerpaintGame(
        session = current,
        brush = brush,
        palette = palette,
        onBrush = { brush = it },
        onSession = { session = it },
        onRestart = { restart() },
        onMenu = { screen = "menu"; bank.play("back") },
        bank = bank,
    )
}

@Composable
private fun FingerpaintMenu(
    saved: FingerpaintSession?,
    best: Int,
    onSolo: () -> Unit,
    onParty: () -> Unit,
    onHistory: () -> Unit,
    onContinue: () -> Unit,
) {
    DetailScaffold(title = "fingerpaint") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null && saved.phase != FingerpaintPhase.GAME_OVER) {
                FingerpaintButton("continue", Modifier.fillMaxWidth()) { onContinue() }
                Spacer(Modifier.height(6.dp))
            }
            FingerpaintButton("solo draw", Modifier.fillMaxWidth()) { onSolo() }
            Spacer(Modifier.height(4.dp))
            FingerpaintButton("pass & play", Modifier.fillMaxWidth()) { onParty() }
            Spacer(Modifier.height(4.dp))
            if (!saved?.history.isNullOrEmpty()) {
                FingerpaintButton("history", Modifier.fillMaxWidth()) { onHistory() }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            FingerpaintCaption("draw the secret word. guessers score by speed.")
            FingerpaintCaption("2 rounds · 90 seconds · one score per player each round")
            if (best > 0) FingerpaintCaption("best $best")
        }
    }
}

@Composable
private fun FingerpaintSetup(
    count: Int,
    onCount: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "fingerpaint · players") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            FingerpaintCaption("pass one device around the table")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(2, 3, 4).forEach { n ->
                    FingerpaintButton("$n players", Modifier.weight(1f), active = count == n) { onCount(n) }
                }
            }
            Spacer(Modifier.height(6.dp))
            (1..count).forEach { index ->
                FingerpaintText("player $index", colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            FingerpaintButton("start game", Modifier.fillMaxWidth()) { onStart() }
            Spacer(Modifier.height(4.dp))
            FingerpaintButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun FingerpaintGame(
    session: FingerpaintSession,
    brush: FingerpaintBrush,
    palette: List<Color>,
    onBrush: (FingerpaintBrush) -> Unit,
    onSession: (FingerpaintSession) -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    bank: SfxBank,
) {
    val colors = LocalDoradoColors.current
    val round = session.roundState
    var guessText by remember(session.round, session.phase) { mutableStateOf("") }
    var peeking by remember { mutableStateOf(false) }
    val guesser = FingerpaintEngine.currentGuesser(session)

    fun commitStroke(stroke: FingerpaintStroke) {
        val next = FingerpaintEngine.addStroke(session.document, stroke)
        if (next !== session.document) {
            onSession(session.copy(document = next))
            bank.play(if (stroke.tool == FingerpaintTool.ERASER) "whoosh" else "pop")
        }
    }

    fun submitGuess(index: Int) {
        val before = session.players.getOrNull(index)?.score ?: 0
        val next = FingerpaintEngine.guess(session, index, guessText)
        if (next === session) return
        val after = next.players.getOrNull(index)?.score ?: 0
        onSession(next)
        guessText = ""
        when {
            after > before -> bank.play("score")
            next.roundState?.lastGuessCorrect == false -> bank.play("error")
        }
    }

    DetailScaffold(title = "fingerpaint") {
        Box(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FingerpaintText("round ${session.round}/${session.totalRounds}", colors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    FingerpaintText(
                        session.players.joinToString("   ") { "${it.name} ${it.score}" },
                        colors.accent,
                    )
                    Spacer(Modifier.weight(1f))
                    if (round != null) {
                        FingerpaintText("time ${ceil(round.remainingMs / 1000.0).toInt()}s", colors.textPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        Modifier
                            .border(0.5.dp, colors.border)
                            .background(if (peeking) colors.tilePressed else colors.tile)
                            .pointerInput(round?.word) {
                                detectTapGestures(
                                    onPress = {
                                        peeking = true
                                        tryAwaitRelease()
                                        peeking = false
                                    },
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        FingerpaintText(
                            if (peeking) "${round?.word} · ${round?.category}" else "hold to peek",
                            if (peeking) colors.accentBright else colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(colors.border)) {
                    val fraction = (round?.remainingMs ?: 0L).toFloat() / FP_ROUND_MS
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(colors.accent))
                }
                Spacer(Modifier.height(4.dp))
                FingerpaintCanvas(
                    document = session.document,
                    brush = brush,
                    palette = palette,
                    enabled = session.phase == FingerpaintPhase.DRAW,
                    onCommit = { commitStroke(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Spacer(Modifier.height(4.dp))
                FingerpaintTools(
                    brush = brush,
                    palette = palette,
                    document = session.document,
                    onBrush = onBrush,
                    onDocument = { onSession(session.copy(document = it)) },
                    bank = bank,
                )
                Spacer(Modifier.height(4.dp))
                if (session.phase == FingerpaintPhase.DRAW && round != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        val name = guesser?.let { session.players.getOrNull(it)?.name } ?: ""
                        FingerpaintText("$name:", colors.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = guessText,
                            onValueChange = { guessText = it.take(FP_GUESS_MAX) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { if (guesser != null) submitGuess(guesser) },
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, colors.border)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        FingerpaintButton("guess", enabled = guesser != null) {
                            if (guesser != null) submitGuess(guesser)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
                FingerpaintText(round?.message.orEmpty(), colors.accentBright)
            }

            if (session.phase == FingerpaintPhase.ROUND_OVER && round != null) {
                FingerpaintPanel(
                    title = round.message,
                    lines = listOf(
                        "the word was ${round.word} · ${round.category}",
                        session.players.joinToString("   ") { "${it.name} ${it.score}" },
                    ),
                ) {
                    FingerpaintButton(if (session.round >= session.totalRounds) "final scores" else "next round") {
                        val next = FingerpaintEngine.advance(session)
                        onSession(next)
                        bank.play(if (next.phase == FingerpaintPhase.GAME_OVER) "select" else "whoosh")
                    }
                    Spacer(Modifier.width(6.dp))
                    FingerpaintButton("menu") { onMenu() }
                }
            }

            if (session.phase == FingerpaintPhase.GAME_OVER) {
                val winners = FingerpaintEngine.winnerNames(session)
                FingerpaintPanel(
                    title = if (winners.size == 1) "${winners.first()} wins" else "tie: ${winners.joinToString(" & ")}",
                    lines = listOf(session.players.joinToString("   ") { "${it.name} ${it.score}" }),
                ) {
                    FingerpaintButton("rematch") { onRestart() }
                    Spacer(Modifier.width(6.dp))
                    FingerpaintButton("menu") { onMenu() }
                }
            }
        }
    }
}

@Composable
private fun FingerpaintSolo(
    session: FingerpaintSession,
    brush: FingerpaintBrush,
    palette: List<Color>,
    onBrush: (FingerpaintBrush) -> Unit,
    onSession: (FingerpaintSession) -> Unit,
    onMenu: () -> Unit,
    bank: SfxBank,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "fingerpaint · solo") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FingerpaintText("solo draw", colors.textSecondary)
                Spacer(Modifier.width(8.dp))
                FingerpaintText("${session.document.strokes.size} strokes", colors.accent)
                Spacer(Modifier.weight(1f))
                FingerpaintButton("menu") { onMenu() }
            }
            Spacer(Modifier.height(4.dp))
            FingerpaintCanvas(
                document = session.document,
                brush = brush,
                palette = palette,
                enabled = true,
                onCommit = { stroke ->
                    val next = FingerpaintEngine.addStroke(session.document, stroke)
                    if (next !== session.document) {
                        onSession(session.copy(document = next))
                        bank.play(if (stroke.tool == FingerpaintTool.ERASER) "whoosh" else "pop")
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(4.dp))
            FingerpaintTools(
                brush = brush,
                palette = palette,
                document = session.document,
                onBrush = onBrush,
                onDocument = { onSession(session.copy(document = it)) },
                bank = bank,
            )
        }
    }
}

@Composable
private fun FingerpaintHistoryScreen(
    history: List<FingerpaintHistory>,
    palette: List<Color>,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    var pick by remember(history) { mutableStateOf(history.size - 1) }
    DetailScaffold(title = "fingerpaint · history") {
        if (history.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                FingerpaintCaption("no drawings yet")
                FingerpaintButton("menu") { onMenu() }
            }
            return@DetailScaffold
        }
        Row(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Column(Modifier.width(150.dp)) {
                history.forEachIndexed { index, item ->
                    FingerpaintButton(
                        "r${item.round} · ${item.drawer} · ${item.word}",
                        Modifier.fillMaxWidth(),
                        active = index == pick.coerceIn(history.indices),
                    ) { pick = index }
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.weight(1f))
                FingerpaintButton("menu", Modifier.fillMaxWidth()) { onMenu() }
            }
            Spacer(Modifier.width(6.dp))
            val selected = history[pick.coerceIn(history.indices)]
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.elevated)
                        .border(0.5.dp, colors.border),
                ) {
                    Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                        for (stroke in selected.strokes) drawFingerpaintStroke(stroke, palette)
                    }
                }
                Spacer(Modifier.height(3.dp))
                FingerpaintCaption("${selected.category} · ${selected.scorers.joinToString(", ").ifEmpty { "no one scored" }}")
            }
        }
    }
}

@Composable
private fun FingerpaintTools(
    brush: FingerpaintBrush,
    palette: List<Color>,
    document: FingerpaintDocument,
    onBrush: (FingerpaintBrush) -> Unit,
    onDocument: (FingerpaintDocument) -> Unit,
    bank: SfxBank,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FingerpaintText("color", colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            palette.forEachIndexed { index, color ->
                FingerpaintSwatch(color = color, selected = brush.colorIndex == index) {
                    onBrush(brush.withColor(index))
                    bank.play("tick")
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FingerpaintText("size", colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            FP_BRUSH_WIDTHS.forEachIndexed { index, width ->
                FingerpaintBrushChip(width = width, selected = brush.widthIndex == index) {
                    onBrush(brush.withWidth(index))
                    bank.play("tick")
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            FingerpaintButton("eraser", active = brush.tool == FingerpaintTool.ERASER) {
                onBrush(brush.withTool(if (brush.tool == FingerpaintTool.ERASER) FingerpaintTool.BRUSH else FingerpaintTool.ERASER))
                bank.play("tick")
            }
            Spacer(Modifier.width(4.dp))
            FingerpaintButton("alpha ${brush.alpha}") {
                onBrush(brush.withAlpha((brush.alphaIndex + 1) % FP_ALPHA_PRESETS.size))
                bank.play("tick")
            }
            Spacer(Modifier.weight(1f))
            FingerpaintButton("undo", enabled = FingerpaintEngine.canUndo(document)) {
                onDocument(FingerpaintEngine.undo(document))
                bank.play("back")
            }
            Spacer(Modifier.width(4.dp))
            FingerpaintButton("redo", enabled = FingerpaintEngine.canRedo(document)) {
                onDocument(FingerpaintEngine.redo(document))
                bank.play("select")
            }
            Spacer(Modifier.width(4.dp))
            FingerpaintButton("clear", enabled = document.strokes.isNotEmpty()) {
                onDocument(FingerpaintEngine.clear(document))
                bank.play("click")
            }
        }
    }
}

@Composable
private fun FingerpaintSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .size(20.dp)
            .background(color)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) colors.textPrimary else colors.border)
            .pointerInput(color, selected) { detectTapGestures(onTap = { onClick() }) },
    )
}

@Composable
private fun FingerpaintBrushChip(width: Float, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .size(22.dp)
            .background(if (selected) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (selected) colors.accent else colors.border)
            .pointerInput(width, selected) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width.dp.coerceAtMost(16.dp)).background(colors.textPrimary))
    }
}

@Composable
private fun FingerpaintButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(26.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .pointerInput(label, enabled, active) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        FingerpaintText(
            label,
            if (enabled) colors.textPrimary else colors.textInactive,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun FingerpaintPanel(
    title: String,
    lines: List<String>,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FingerpaintText(title, colors.accent, DoradoTokens.TYPE_NOW_META.sp)
            lines.forEach { line ->
                Spacer(Modifier.height(2.dp))
                FingerpaintText(line, colors.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
private fun FingerpaintCanvas(
    document: FingerpaintDocument,
    brush: FingerpaintBrush,
    palette: List<Color>,
    enabled: Boolean,
    onCommit: (FingerpaintStroke) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalDoradoColors.current
    val live = remember { mutableStateMapOf<Long, FingerpaintStroke>() }
    val commit = rememberUpdatedState(onCommit)
    Canvas(
        modifier = modifier
            .background(colors.elevated)
            .border(0.5.dp, colors.border)
            .pointerInput(brush, enabled) {
                if (!enabled) return@pointerInput
                live.clear()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val id = change.id.value
                            val position = change.position
                            when {
                                change.pressed && !change.previousPressed ->
                                    live[id] = FingerpaintEngine.beginStroke(0, brush, position.x, position.y)

                                change.pressed -> {
                                    val stroke = live[id]
                                    live[id] = if (stroke == null) {
                                        FingerpaintEngine.beginStroke(0, brush, position.x, position.y)
                                    } else {
                                        FingerpaintEngine.extendStroke(stroke, position.x, position.y)
                                    }
                                }

                                else -> live.remove(id)?.let { commit.value(it) }
                            }
                        }
                    }
                }
            },
    ) {
        drawRect(colors.background)
        for (stroke in document.strokes) drawFingerpaintStroke(stroke, palette)
        for (stroke in live.values) {
            if (stroke.tool == FingerpaintTool.ERASER) {
                drawFingerpaintEraseTrail(stroke, colors.textSecondary.copy(alpha = 0.4f))
            } else {
                drawFingerpaintStroke(stroke, palette)
            }
        }
    }
}

private fun DrawScope.drawFingerpaintStroke(stroke: FingerpaintStroke, palette: List<Color>) {
    if (stroke.points.isEmpty() || palette.isEmpty()) return
    val slot = ((stroke.colorIndex % palette.size) + palette.size) % palette.size
    val color = palette[slot].copy(alpha = stroke.alpha.coerceIn(0, 100) / 100f)
    if (stroke.points.size == 1) {
        val point = stroke.points[0]
        drawCircle(color, stroke.width * point.pressure / 2f, Offset(point.x, point.y))
        return
    }
    for (index in 1 until stroke.points.size) {
        val a = stroke.points[index - 1]
        val b = stroke.points[index]
        drawLine(
            color = color,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = stroke.width * (a.pressure + b.pressure) / 2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawFingerpaintEraseTrail(stroke: FingerpaintStroke, color: Color) {
    if (stroke.points.size < 2) return
    for (index in 1 until stroke.points.size) {
        val a = stroke.points[index - 1]
        val b = stroke.points[index]
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun FingerpaintText(
    text: String,
    color: Color,
    fontSize: TextUnit = DoradoTokens.TYPE_LIST.sp,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = Selawik, fontSize = fontSize, color = color),
        modifier = modifier,
    )
}

@Composable
private fun FingerpaintCaption(text: String) {
    val colors = LocalDoradoColors.current
    FingerpaintText(text, colors.textSecondary, DoradoTokens.TYPE_CAPTION.sp)
}
