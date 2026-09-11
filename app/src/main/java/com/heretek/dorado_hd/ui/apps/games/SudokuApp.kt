package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/* ============================================================ */
/*                            Sudoku                              */
/* ============================================================ */

@Composable
fun SudokuApp() {
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
    var type by remember { mutableStateOf(SudokuType.CLASSIC) }
    var level by remember { mutableStateOf(SudokuLevel.NORMAL) }
    var game by remember { mutableStateOf<SudokuGame?>(null) }
    var saved by remember { mutableStateOf<SudokuGame?>(null) }
    var records by remember { mutableStateOf<Map<String, SudokuRecord>>(emptyMap()) }
    var selected by remember { mutableStateOf(-1) }
    var noteMode by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmSolve by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        records = SudokuEngine.decodeRecords(graph.appState.get("sudoku.records"))
        graph.appState.get("sudoku")?.let { blob ->
            SudokuEngine.decode(blob)?.let { g ->
                saved = g
                type = g.type
                level = g.level
            }
        }
        apiLoaded = true
    }

    LaunchedEffect(game, apiLoaded) {
        if (!apiLoaded) return@LaunchedEffect
        val g = game ?: return@LaunchedEffect
        delay(500)
        graph.appState.put("sudoku", SudokuEngine.encode(g.copy(elapsedSeconds = elapsed)))
    }
    // Keep the persisted elapsed time fresh without a write per tick.
    val sudokuSnapshot = rememberUpdatedState(game?.copy(elapsedSeconds = elapsed))
    LaunchedEffect(apiLoaded, running) {
        if (!apiLoaded || !running) return@LaunchedEffect
        while (true) {
            delay(5_000)
            sudokuSnapshot.value?.let { graph.appState.put("sudoku", SudokuEngine.encode(it)) }
        }
    }

    LaunchedEffect(records, apiLoaded) {
        if (!apiLoaded) return@LaunchedEffect
        graph.appState.put("sudoku.records", SudokuEngine.encodeRecords(records))
    }

    LaunchedEffect(running, game?.solved) {
        while (running) {
            delay(1000)
            if (game?.solved == true) break
            elapsed++
        }
    }

    fun startNew() {
        if (generating) return
        generating = true
        val t = type
        val l = level
        scope.launch {
            val board = withContext(Dispatchers.Default) {
                SudokuEngine.newGame(t, l, seed = System.nanoTime())
            }
            game = SudokuEngine.start(board, t, l)
            selected = -1
            noteMode = false
            elapsed = 0
            running = true
            recorded = false
            generating = false
            menu = false
        }
    }

    fun resume() {
        val g = saved ?: return
        game = g
        elapsed = g.elapsedSeconds
        running = !g.solved
        recorded = g.solved
        saved = null
        selected = -1
        menu = false
    }

    val current = game
    // Record the solve from an effect with a once-guard; mutating state during
    // composition double-recorded when undo reset the flag.
    LaunchedEffect(apiLoaded, current?.solved) {
        val g = current ?: return@LaunchedEffect
        if (!apiLoaded || !g.solved || recorded) return@LaunchedEffect
        recorded = true
        running = false
        val key = SudokuEngine.recordKey(g.type, g.level)
        val secs = elapsed
        graph.games.record("sudoku", secs, "$key|won")
        val prev = records[key] ?: SudokuRecord()
        val best = if (prev.bestSeconds in 1..secs) prev.bestSeconds else secs
        records = records + (key to SudokuRecord(best, prev.solved + 1))
    }

    DetailScaffold(title = "sudoku") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                if (current == null) {
                    SetupPanel(
                        type = type,
                        level = level,
                        records = records,
                        saved = saved,
                        generating = generating,
                        onType = { type = it },
                        onLevel = { level = it },
                        onStart = { startNew() },
                        onResume = { resume() },
                    )
                } else {
                    val n = current.board.rows.size
                    val conflicted = remember(current.cells, current.notes) { SudokuEngine.conflicts(current) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        BasicText(
                            text = if (current.solved) "solved" else "%d:%02d".format(elapsed / 60, elapsed % 60),
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (current.solved) colors.accent else colors.textPrimary),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            text = "${current.type.name.lowercase()} · ${current.level.name.lowercase()}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                        )
                        Spacer(Modifier.weight(1f))
                        EdgeText("undo", if (current.history.isEmpty()) colors.textInactive else colors.textPrimary) {
                            val next = SudokuEngine.undo(current)
                            if (next !== current) {
                                game = next
                                bank.play("back")
                            }
                        }
                        EdgeText("hint", colors.textPrimary) {
                            if (selected >= 0 && SudokuEngine.canHint(current, selected)) {
                                game = SudokuEngine.hint(current, selected)
                                bank.play("tick")
                            } else {
                                bank.play("error")
                            }
                        }
                        EdgeText("solve", colors.textPrimary) { confirmSolve = true }
                        EdgeText("menu", colors.textPrimary) { menu = true }
                    }
                    Spacer(Modifier.height(4.dp))
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val cell = (minOf(maxWidth, maxHeight) - (n + 1).dp) / n
                        Column(verticalArrangement = Arrangement.spacedBy(DoradoTokens.BOARD_GAP.dp)) {
                            for (r in 0 until n) {
                                Row(horizontalArrangement = Arrangement.spacedBy(DoradoTokens.BOARD_GAP.dp)) {
                                    for (c in 0 until n) {
                                        val idx = r * n + c
                                        val v = current.cells[idx]
                                        val isGiven = SudokuEngine.isGiven(current, idx)
                                        val isSel = selected == idx
                                        val isConflict = idx in conflicted
                                        Box(
                                            Modifier
                                                .size(cell)
                                                .background(
                                                    when {
                                                        isSel -> colors.accent
                                                        isConflict -> colors.tilePressed
                                                        isGiven -> colors.tile
                                                        else -> colors.elevated
                                                    },
                                                )
                                                .pointerInput(idx, current) { detectTapGestures(onTap = { selected = idx }) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (v != 0) {
                                                BasicText(
                                                    text = v.toString(),
                                                    style = TextStyle(
                                                        fontFamily = Selawik,
                                                        fontSize = (cell.value / 2.4f).sp,
                                                        color = when {
                                                            isSel -> colors.background
                                                            isGiven -> colors.textPrimary
                                                            isConflict -> colors.accentBright
                                                            else -> colors.accent
                                                        },
                                                    ),
                                                )
                                            } else if (current.notes[idx].isNotEmpty()) {
                                                Column {
                                                    current.notes[idx].sorted().chunked(3).forEach { row ->
                                                        Row {
                                                            row.forEach { d ->
                                                                BasicText(
                                                                    text = d.toString(),
                                                                    style = TextStyle(fontFamily = Selawik, fontSize = 6.sp, color = colors.textSecondary),
                                                                )
                                                                Spacer(Modifier.width(1.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        (1..n).forEach { v ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .background(if (noteMode) colors.tile else colors.elevated)
                                    .pointerInput(v, noteMode, selected, current) {
                                        detectTapGestures(onTap = {
                                            if (selected < 0) return@detectTapGestures
                                            game = if (noteMode) {
                                                SudokuEngine.toggleNote(current, selected, v)
                                            } else {
                                                SudokuEngine.place(current, selected, v)
                                            }
                                            bank.play("click")
                                        })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(text = v.toString(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(26.dp)
                                .background(colors.elevated)
                                .pointerInput(noteMode, selected, current) {
                                    detectTapGestures(onTap = {
                                        if (selected >= 0) {
                                            game = SudokuEngine.erase(current, selected)
                                            bank.play("click")
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(text = "erase", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        EdgeText(
                            text = "notes ${if (noteMode) "on" else "off"}",
                            color = if (noteMode) colors.accent else colors.textSecondary,
                            onClick = { noteMode = !noteMode },
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            text = if (current.solved) "recorded" else "tap a cell, then a number",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                        )
                    }
                }
            }

            if (generating) {
                Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
                    BasicText(text = "generating puzzle…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
                }
            }

            if (confirmSolve && current != null) {
                Column(
                    Modifier.fillMaxSize().background(colors.background).padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(text = "solve puzzle?", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
                    BasicText(text = "the whole grid fills as one undo step", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                    Row {
                        EdgeText("yes", colors.accent) {
                            game = SudokuEngine.solve(current)
                            confirmSolve = false
                            bank.play("win")
                        }
                        Spacer(Modifier.width(12.dp))
                        EdgeText("no", colors.textPrimary) { confirmSolve = false }
                    }
                }
            }

            if (menu && current != null) {
                Column(
                    Modifier.fillMaxSize().background(colors.background).padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(text = "options", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
                    EdgeText("new puzzle", colors.accent) {
                        saved = game
                        game = null
                        menu = false
                    }
                    EdgeText("close", colors.textPrimary) { menu = false }
                    Spacer(Modifier.height(4.dp))
                    BasicText(text = "high scores", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                    SudokuType.values().forEach { t ->
                        SudokuLevel.values().forEach { l ->
                            val key = SudokuEngine.recordKey(t, l)
                            val rec = records[key]
                            BasicText(
                                text = "${t.name.lowercase()} ${l.name.lowercase()}  best ${if (rec == null || rec.bestSeconds == 0) "—" else "%d:%02d".format(rec.bestSeconds / 60, rec.bestSeconds % 60)} · solved ${rec?.solved ?: 0}",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPanel(
    type: SudokuType,
    level: SudokuLevel,
    records: Map<String, SudokuRecord>,
    saved: SudokuGame?,
    generating: Boolean,
    onType: (SudokuType) -> Unit,
    onLevel: (SudokuLevel) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = "type", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            Spacer(Modifier.width(8.dp))
            SudokuType.values().forEach { t ->
                EdgeText(
                    text = t.name.lowercase(),
                    color = if (t == type) colors.accent else colors.textSecondary,
                    onClick = { onType(t) },
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = "level", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            Spacer(Modifier.width(8.dp))
            SudokuLevel.values().forEach { l ->
                EdgeText(
                    text = l.name.lowercase(),
                    color = if (l == level) colors.accent else colors.textSecondary,
                    onClick = { onLevel(l) },
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        val rec = records[SudokuEngine.recordKey(type, level)]
        BasicText(
            text = "best ${if (rec == null || rec.bestSeconds == 0) "—" else "%d:%02d".format(rec.bestSeconds / 60, rec.bestSeconds % 60)} · solved ${rec?.solved ?: 0}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Row {
            if (!generating) EdgeText("start", colors.accent) { onStart() }
            if (saved != null) {
                Spacer(Modifier.width(12.dp))
                EdgeText("resume ${saved.type.name.lowercase()}", colors.textPrimary) { onResume() }
            }
        }
    }
}
