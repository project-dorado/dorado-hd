package com.heretek.dorado_hd.ui.apps.games

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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

/* ============================================================ */
/*                          Solitaire (Klondike)                  */
/* ============================================================ */

private data class SolPick(val from: SolPileRef, val count: Int)

private class SolModeStats {
    var played = 0
    var wins = 0
    var losses = 0
    var best = Int.MIN_VALUE
    var fastest = Int.MAX_VALUE
    var fewest = Int.MAX_VALUE
}

@Composable
private fun SolCardSlot(
    card: SolCard,
    width: Dp,
    height: Dp,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(Modifier.border(1.dp, if (highlighted) colors.accent else Color.Transparent)) {
        SolCardView(card, width, height, onClick)
    }
}

@Composable
private fun EdgeToggle(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    EdgeText(
        text = label,
        color = if (active) colors.accent else colors.textSecondary,
        onClick = onClick,
    )
}

@Composable
fun SolitaireApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var state by remember { mutableStateOf(SolitaireEngine.deal()) }
    var elapsed by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<SolPick?>(null) }
    var hint by remember { mutableStateOf<SolHint?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scores by graph.games.top("solitaire", 200).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        graph.appState.get("solitaire")?.let { blob ->
            SolitaireEngine.decode(blob)?.let { saved ->
                state = saved
                elapsed = saved.elapsedSeconds
                running = !saved.won
                recorded = saved.won
            }
        }
        loaded = true
    }

    LaunchedEffect(state, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(600)
        graph.appState.put("solitaire", SolitaireEngine.encode(state.copy(elapsedSeconds = elapsed)))
    }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsed++
        }
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(3000)
            hint = null
        }
    }

    // Win settlement pays the Standard time bonus exactly once.
    LaunchedEffect(state.won) {
        if (state.won && !state.timeBonusApplied) {
            state = SolitaireEngine.complete(state, elapsed)
            bank.play("win")
        }
    }

    val over = remember(state) { !state.won && SolitaireEngine.isGameOver(state) }
    // Record from an effect with a once-guard: recording during composition
    // re-fired after undo reset the flag and double-counted game-over rows.
    LaunchedEffect(loaded, state.won, over) {
        if (!loaded || recorded || !(state.won || over)) return@LaunchedEffect
        recorded = true
        running = false
        val mode = state.scoring.name.lowercase()
        val result = if (state.won) "win" else "loss"
        graph.games.record("solitaire", state.score, "$mode|$result|$elapsed|${state.moves}")
        if (result == "loss") bank.play("lose")
    }

    val stats = remember(scores) {
        val out = mapOf("standard" to SolModeStats(), "vegas" to SolModeStats())
        scores.forEach { row ->
            val parts = (row.meta ?: "").split("|")
            if (parts.size >= 4) {
                val bucket = out[parts[0]] ?: return@forEach
                bucket.played++
                if (parts[1] == "win") {
                    bucket.wins++
                    val sec = parts[2].toIntOrNull() ?: 0
                    val mv = parts[3].toIntOrNull() ?: 0
                    if (sec > 0 && sec < bucket.fastest) bucket.fastest = sec
                    if (mv > 0 && mv < bucket.fewest) bucket.fewest = mv
                } else {
                    bucket.losses++
                }
                if (row.score > bucket.best) bucket.best = row.score
            }
        }
        out
    }

    fun newGame(deal: SolDealType = state.deal, scoring: SolScoringMethod = state.scoring) {
        state = SolitaireEngine.deal(deal, scoring)
        elapsed = 0
        running = true
        recorded = false
        pick = null
        hint = null
        menu = false
    }

    fun attempt(from: SolPileRef, to: SolPileRef, count: Int): Boolean {
        val next = SolitaireEngine.move(state, from, to, count)
        if (next === state) return false
        state = next
        pick = null
        hint = null
        bank.play(if (to.kind == SolPileKind.FOUNDATION) "score" else "select")
        return true
    }

    fun selectOrMove(from: SolPileRef, count: Int) {
        val cur = pick
        if (cur == null) {
            pick = SolPick(from, count)
            return
        }
        if (cur.from == from) {
            pick = null
            return
        }
        if (!attempt(cur.from, from, cur.count)) pick = SolPick(from, count)
    }

    fun tapTableau(col: Int, card: Int) {
        val cards = state.tableau[col]
        val top = cards.lastOrNull() ?: run {
            pick = null
            return
        }
        if (!top.faceUp) {
            val next = SolitaireEngine.flip(state, col)
            if (next !== state) {
                state = next
                pick = null
                bank.play("click")
            }
            return
        }
        if (SolitaireEngine.isRun(cards, card)) selectOrMove(SolPileRef(SolPileKind.TABLEAU, col), cards.size - card)
    }

    fun tapFoundation(f: Int) {
        val cur = pick ?: return
        attempt(cur.from, SolPileRef(SolPileKind.FOUNDATION, f), 1)
    }

    fun tapWaste() {
        if (pick != null) {
            pick = null
            return
        }
        if (state.waste.lastOrNull() != null) pick = SolPick(SolPileRef(SolPileKind.WASTE), 1)
    }

    fun tapStock() {
        val next = SolitaireEngine.draw(state)
        if (next !== state) {
            state = next
            pick = null
            bank.play("click")
        }
    }

    fun showHint() {
        hint = SolitaireEngine.hint(state)
        bank.play(if (hint == null) "error" else "tick")
    }

    fun autoFinish() {
        val next = SolitaireEngine.autoComplete(state)
        if (next !== state) {
            state = next
            pick = null
            hint = null
            bank.play("win")
        }
    }

    DetailScaffold(title = "solitaire") {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(DoradoTokens.EDGE.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // Score/time live in a weighted column so the action labels
                    // keep their intrinsic width and never break mid-word.
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = "score ${state.score}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            maxLines = 1,
                            softWrap = false,
                        )
                        BasicText(
                            text = "%d:%02d · %s · %s".format(elapsed / 60, elapsed % 60, if (state.deal == SolDealType.ONE) "1-card" else "3-card", state.scoring.name.lowercase()),
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    EdgeText("undo", if (state.history.isEmpty()) colors.textInactive else colors.textPrimary) {
                        val next = SolitaireEngine.undo(state)
                        if (next !== state) {
                            state = next
                            pick = null
                            if (over) {
                                recorded = false
                                running = true
                            }
                            bank.play("back")
                        }
                    }
                    EdgeText("hint", colors.textPrimary) { showHint() }
                    EdgeText("auto", if (SolitaireEngine.canAutoComplete(state)) colors.accent else colors.textInactive) { autoFinish() }
                    EdgeText("menu", colors.textPrimary) { menu = true }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dealing = hint?.kind == SolHintKind.DRAW
                    EdgeText(
                        text = when {
                            state.stock.isNotEmpty() -> "deal"
                            SolitaireEngine.canRecycle(state) -> "recycle"
                            else -> "empty"
                        },
                        color = if (dealing) colors.accentBright else colors.accent,
                        onClick = { tapStock() },
                    )
                    if (state.waste.isNotEmpty()) {
                        SolCardSlot(state.waste.last(), 26.dp, 36.dp, pick?.from?.kind == SolPileKind.WASTE) { tapWaste() }
                    } else {
                        Box(Modifier.size(width = 26.dp, height = 36.dp).background(colors.elevated)) {}
                    }
                    Spacer(Modifier.weight(1f))
                    state.foundations.forEachIndexed { i, f ->
                        val hinted = hint?.to == SolPileRef(SolPileKind.FOUNDATION, i)
                        val top = f.lastOrNull()
                        if (top != null) {
                            SolCardSlot(top, 26.dp, 36.dp, hinted) { tapFoundation(i) }
                        } else {
                            Box(
                                Modifier
                                    .size(width = 26.dp, height = 36.dp)
                                    .background(if (hinted) colors.tilePressed else colors.elevated)
                                    .pointerInput(i) { detectTapGestures(onTap = { tapFoundation(i) }) },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    text = suitGlyph(SolSuit.values()[i]),
                                    style = TextStyle(fontFamily = Selawik, fontSize = 10.sp, color = colors.textSecondary),
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until 7) {
                        val col = state.tableau[c]
                        val hintedTo = hint?.to?.kind == SolPileKind.TABLEAU && hint?.to?.index == c
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(if (hintedTo) colors.tilePressed else colors.elevated)
                                .padding(1.dp),
                            verticalArrangement = Arrangement.spacedBy((-22).dp),
                        ) {
                            col.forEachIndexed { idx, card ->
                                val selected = pick?.from?.kind == SolPileKind.TABLEAU && pick?.from?.index == c &&
                                    idx >= col.size - (pick?.count ?: 0)
                                val hintedCard = hint?.from?.kind == SolPileKind.TABLEAU && hint?.from?.index == c &&
                                    idx >= col.size - (hint?.count ?: 0)
                                SolCardSlot(card, 26.dp, 36.dp, selected || hintedCard) { tapTableau(c, idx) }
                            }
                        }
                    }
                }
            }

            if (menu) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(
                        text = "options",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "deal",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        EdgeToggle("one", state.deal == SolDealType.ONE) { newGame(deal = SolDealType.ONE) }
                        EdgeToggle("three", state.deal == SolDealType.THREE) { newGame(deal = SolDealType.THREE) }
                        Spacer(Modifier.width(12.dp))
                        BasicText(
                            text = "score",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        EdgeToggle("standard", state.scoring == SolScoringMethod.STANDARD) { newGame(scoring = SolScoringMethod.STANDARD) }
                        EdgeToggle("vegas", state.scoring == SolScoringMethod.VEGAS) { newGame(scoring = SolScoringMethod.VEGAS) }
                    }
                    BasicText(
                        text = "changing deal or scoring starts a new game",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EdgeText("new game", colors.accent) { newGame() }
                        Spacer(Modifier.width(12.dp))
                        EdgeText("close", colors.textPrimary) { menu = false }
                    }
                    state.scoring.let { mode ->
                        val key = mode.name.lowercase()
                        val s = stats[key] ?: SolModeStats()
                        BasicText(
                            text = "$key  played ${s.played} · wins ${s.wins} · losses ${s.losses} · best ${if (s.best == Int.MIN_VALUE) 0 else s.best} · fastest ${if (s.fastest == Int.MAX_VALUE) "—" else "${s.fastest}s"} · fewest ${if (s.fewest == Int.MAX_VALUE) "—" else s.fewest}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                    BasicText(
                        text = "tap a card to pick a run, then tap its destination",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                    )
                    BasicText(
                        text = if (over) "no moves left — game over" else if (state.won) "you win" else " ",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                    )
                }
            }
        }
    }
}
