package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/* ============================================================ */
/*                              Hearts                              */
/* ============================================================ */

@Composable
fun HeartsApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var mode by remember { mutableStateOf(HeartsMode.STANDARD) }
    var level by remember { mutableStateOf(HeartsLevel.EASY) }
    var started by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(HeartsEngine.newGame(mode)) }
    var recorded by remember { mutableStateOf(false) }
    var resumeBlob by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { resumeBlob = graph.appState.get("hearts") }

    // Autosave at round/game boundaries.
    LaunchedEffect(state.roundNumber, state.done, state.gameOver, state.resolved) {
        if (started && !state.gameOver) graph.appState.put("hearts", HeartsEngine.encode(state))
    }

    // One AI play per state change; the watchdog retries with fresh entropy so
    // an unchanged AI result cannot silently stall the trick loop.
    LaunchedEffect(state) {
        val cur = state
        if (!started || cur.done || cur.gameOver) return@LaunchedEffect
        if (cur.pendingPassFrom != null) return@LaunchedEffect
        if (cur.currentPlayer == HeartsSeat.SOUTH) return@LaunchedEffect
        delay(240)
        var next = HeartsEngine.aiPlay(cur, level, Random.Default)
        var attempt = 0
        while (next == cur && attempt < 3) {
            next = HeartsEngine.aiPlay(cur, level, Random(attempt * 31 + 7))
            attempt++
        }
        if (next == cur) next = HeartsEngine.forceAdvance(cur, Random.Default)
        if (next != cur) state = next
    }

    // Resolve the round exactly once (shoot the moon / 100-point game end).
    LaunchedEffect(state.done, state.resolved) {
        if (state.done && !state.resolved) {
            bank.play("score")
            state = HeartsEngine.resolveRound(state)
        }
    }

    // Record from an effect with a once-guard instead of during composition.
    LaunchedEffect(state.gameOver) {
        if (!state.gameOver || recorded) return@LaunchedEffect
        recorded = true
        val won = state.winner == HeartsSeat.SOUTH
        bank.play(if (won) "win" else "lose")
        graph.games.record(
            "hearts",
            state.totals[HeartsSeat.SOUTH] ?: state.scores[HeartsSeat.SOUTH] ?: 0,
            "${state.mode.name.lowercase()} ${if (won) "win" else if (state.winner == null) "tie" else "loss"}",
        )
    }

    DetailScaffold(title = "hearts") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            when {
                !started -> HeartsSetup(
                    mode = mode,
                    level = level,
                    canResume = resumeBlob != null,
                    onMode = { bank.play("click"); mode = it },
                    onLevel = { bank.play("click"); level = it },
                    onPlay = {
                        bank.play("select")
                        state = HeartsEngine.newGame(mode)
                        recorded = false
                        started = true
                    },
                    onResume = {
                        val restored = resumeBlob?.let { HeartsEngine.decode(it) }
                        if (restored != null) {
                            bank.play("back")
                            mode = restored.mode
                            state = restored
                            recorded = false
                            started = true
                        }
                    },
                )
                state.gameOver -> HeartsGameOver(
                    state = state,
                    onNewGame = {
                        bank.play("select")
                        state = HeartsEngine.newGame(mode)
                        recorded = false
                    },
                    onMenu = { bank.play("back"); started = false },
                )
                else -> HeartsTable(
                    state = state,
                    level = level,
                    onPlay = { card ->
                        bank.play("click")
                        state = HeartsEngine.play(state, card)
                    },
                    onPass = { cards ->
                        bank.play("click")
                        state = HeartsEngine.completePassing(
                            HeartsEngine.passCards(state, HeartsSeat.SOUTH, cards),
                            Random.Default,
                            level,
                        )
                    },
                    onNextRound = {
                        bank.play("score")
                        state = HeartsEngine.nextRound(state)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeartsSetup(
    mode: HeartsMode,
    level: HeartsLevel,
    canResume: Boolean,
    onMode: (HeartsMode) -> Unit,
    onLevel: (HeartsLevel) -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HeartsToggle("mode", HeartsMode.entries.map { it.name.lowercase() }, mode.name.lowercase()) {
            onMode(HeartsMode.entries.first { m -> m.name.lowercase() == it })
        }
        HeartsToggle("difficulty", listOf("easy", "hard"), level.name.lowercase()) {
            onLevel(if (it == "easy") HeartsLevel.EASY else HeartsLevel.HARD)
        }
        BasicText(
            text = when (mode) {
                HeartsMode.STANDARD -> "point cards: hearts 1 · queen of spades 13"
                HeartsMode.WILDCARD -> "point cards: hearts 1 · queen of spades 13 · j♦ -10 · 7♣ +7"
                HeartsMode.TURBO -> "turbo: hearts double for a seat holding an ace"
            },
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.height(4.dp))
        HeartsAction("play", colors.accent, onPlay)
        if (canResume) HeartsAction("resume", colors.textSecondary, onResume)
    }
}

@Composable
private fun HeartsTable(
    state: HeartsState,
    level: HeartsLevel,
    onPlay: (SolCard) -> Unit,
    onPass: (List<SolCard>) -> Unit,
    onNextRound: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val seat = HeartsSeat.SOUTH
    val passing = state.pendingPassFrom == seat && !state.pendingPasses.containsKey(seat)
    val totals = HeartsEngine.heartsSeats.joinToString("   ") {
        "${it.name.take(1).lowercase()}=${state.totals[it] ?: 0}"
    }
    val passLabel = when (HeartsPass.fromCode(state.passDirection)) {
        HeartsPass.LEFT -> "passing left"
        HeartsPass.RIGHT -> "passing right"
        HeartsPass.ACROSS -> "passing across"
        HeartsPass.NONE -> "no pass this round"
    }
    val totalsLine = "totals $totals   ·   round ${state.scores.values.sum()} pts · $passLabel · ${level.name.lowercase()}"
    val opponentsLine = HeartsEngine.heartsSeats.filter { it != seat }.joinToString("   ") {
        "${it.name.lowercase()} ${state.hands[it]?.size ?: 0}"
    } + if (state.heartsBroken) "   · hearts broken" else ""
    Column(Modifier.fillMaxSize()) {
        BasicText(
            text = totalsLine,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            modifier = Modifier.appDescription(totalsLine),
        )
        BasicText(
            text = opponentsLine,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            modifier = Modifier.appDescription(opponentsLine),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            state.trick.forEach { (s, c) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                    SolCardView(c, width = 30.dp, height = 40.dp) {}
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (passing) {
            PassPicker(hand = state.hands[seat].orEmpty(), onConfirm = onPass)
        } else {
            val interactive = !state.done && state.currentPlayer == seat
            PlayerHand(
                hand = state.hands[seat].orEmpty(),
                onCard = { c -> if (interactive) onPlay(c) },
                align = Alignment.CenterHorizontally,
                label = if (state.done) "you · round done" else "you",
                interactive = interactive,
            )
        }
        Spacer(Modifier.height(4.dp))
        when {
            state.done && state.shootMoon != null ->
                BasicText(
                    text = "${state.shootMoon.name.lowercase()} shot the moon!",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
            state.done -> Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "round ${state.roundNumber + 1} complete",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    modifier = Modifier.weight(1f),
                )
                HeartsAction("next round", colors.accent, onNextRound)
            }
            state.pendingPassFrom != null -> BasicText(
                text = "choose three cards to pass",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
            )
            state.currentPlayer == seat -> BasicText(
                text = "your play",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            else -> BasicText(
                text = "ai playing…",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun HeartsGameOver(state: HeartsState, onNewGame: () -> Unit, onMenu: () -> Unit) {
    val colors = LocalDoradoColors.current
    val low = HeartsEngine.heartsSeats.minByOrNull { state.totals[it] ?: 0 }
    val headline = when {
        state.winner == HeartsSeat.SOUTH -> "you win"
        state.winner == null -> "tie for lowest — game over"
        else -> "${state.winner.name.lowercase()} wins"
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(text = headline, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
        HeartsEngine.heartsSeats.sortedBy { state.totals[it] ?: 0 }.forEach { s ->
            BasicText(
                text = "${s.name.lowercase()} ${state.totals[s] ?: 0}${if (s == low) "  (lowest)" else ""}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
        }
        Spacer(Modifier.height(4.dp))
        HeartsAction("new game", colors.accent, onNewGame)
        HeartsAction("menu", colors.textSecondary, onMenu)
    }
}

@Composable
private fun HeartsAction(label: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = color),
        modifier = Modifier
            .appTap(label = label) { onClick() }
            .padding(vertical = 3.dp, horizontal = 2.dp),
    )
}

@Composable
private fun HeartsToggle(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        BasicText(text = label, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        options.forEach { option ->
            val active = option == selected
            BasicText(
                text = option,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (active) colors.accent else colors.textSecondary),
                modifier = Modifier.appTap(label = option) { if (!active) onPick(option) }.padding(2.dp),
            )
        }
    }
}
