package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
/*                              Spades                              */
/* ============================================================ */

@Composable
fun SpadesApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var level by remember { mutableStateOf(SpadesLevel.EASY) }
    var target by remember { mutableStateOf(500) }
    var blindNilEnabled by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(SpadesEngine.newGame(target = target)) }
    var revealed by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var resumeBlob by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        resumeBlob = graph.appState.get("spades")
    }

    // Autosave at phase boundaries (deal/bid/round/game), not on every card.
    LaunchedEffect(state.biddingDone, state.done, state.gameOver, state.history.size) {
        if (started && !state.gameOver) {
            graph.appState.put("spades", SpadesEngine.encode(state))
        }
    }

    // One AI action per state change keeps the trick loop moving; the human's
    // seat is skipped so the UI can wait for input.
    LaunchedEffect(state) {
        val cur = state
        if (!started || cur.done || cur.gameOver) return@LaunchedEffect
        if (cur.currentPlayer == SpadesSeat.SOUTH) return@LaunchedEffect
        delay(260)
        val next = SpadesEngine.aiAction(cur, level, Random.Default)
        if (next != cur) state = next
    }

    // Score exactly once when the round ends.
    LaunchedEffect(state.done, state.roundScored) {
        if (state.done && !state.roundScored) {
            state = SpadesEngine.scoreRound(state)
        }
    }

    // Record from an effect with a once-guard instead of during composition;
    // wait for the final round to score so the recorded total is final.
    LaunchedEffect(state.gameOver, state.roundScored) {
        if (!state.gameOver || !state.roundScored || recorded) return@LaunchedEffect
        recorded = true
        val human = state.score.first
        val won = state.winnerTeam == 0
        bank.play(if (won) "win" else "lose")
        graph.games.record(
            "spades",
            human,
            "target=${state.target} ${if (won) "win" else if (state.winnerTeam == null) "tie" else "loss"}",
        )
    }

    DetailScaffold(title = "spades") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            when {
                !started -> SetupPanel(
                    level = level,
                    target = target,
                    blindNilEnabled = blindNilEnabled,
                    canResume = resumeBlob != null,
                    onLevel = { bank.play("click"); level = it },
                    onTarget = { bank.play("click"); target = it },
                    onBlindNil = { bank.play("click"); blindNilEnabled = !blindNilEnabled },
                    onPlay = {
                        bank.play("select")
                        state = SpadesEngine.newGame(target = target)
                        revealed = false
                        recorded = false
                        started = true
                    },
                    onResume = {
                        val blob = resumeBlob
                        val restored = blob?.let { SpadesEngine.decode(it) }
                        if (restored != null) {
                            bank.play("back")
                            state = restored
                            revealed = true
                            recorded = false
                            started = true
                        }
                    },
                )
                state.gameOver -> GameOverPanel(
                    state = state,
                    onNewGame = {
                        bank.play("select")
                        state = SpadesEngine.newGame(target = target)
                        revealed = false
                        recorded = false
                    },
                    onMenu = { bank.play("back"); started = false },
                )
                else -> TablePanel(
                    state = state,
                    level = level,
                    revealed = revealed,
                    blindNilEnabled = blindNilEnabled,
                    onReveal = { bank.play("click"); revealed = true },
                    onBid = { bid, blind ->
                        bank.play("click")
                        state = SpadesEngine.bid(state, bid, nil = bid == 0 || blind, blind = blind)
                    },
                    onPlay = { card ->
                        bank.play("click")
                        state = SpadesEngine.play(state, card)
                    },
                    onNextRound = {
                        bank.play("score")
                        state = SpadesEngine.nextRound(state)
                        revealed = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SetupPanel(
    level: SpadesLevel,
    target: Int,
    blindNilEnabled: Boolean,
    canResume: Boolean,
    onLevel: (SpadesLevel) -> Unit,
    onTarget: (Int) -> Unit,
    onBlindNil: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpadesToggle("difficulty", listOf(SpadesLevel.EASY.name.lowercase(), SpadesLevel.HARD.name.lowercase()), level.name.lowercase()) {
            onLevel(if (it == "easy") SpadesLevel.EASY else SpadesLevel.HARD)
        }
        SpadesToggle("game", listOf("500", "300"), target.toString()) { onTarget(it.toInt()) }
        SpadesToggle("blind nil", listOf("on", "off"), if (blindNilEnabled) "on" else "off") { onBlindNil() }
        Spacer(Modifier.height(4.dp))
        SpadesAction("play", colors.accent, onPlay)
        if (canResume) SpadesAction("resume", colors.textSecondary, onResume)
    }
}

@Composable
private fun TablePanel(
    state: SpadesState,
    level: SpadesLevel,
    revealed: Boolean,
    blindNilEnabled: Boolean,
    onReveal: () -> Unit,
    onBid: (Int, Boolean) -> Unit,
    onPlay: (SolCard) -> Unit,
    onNextRound: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val scoreLine = "S-N ${state.score.first} · W-E ${state.score.second}   bags ${state.teamBags.first}/${state.teamBags.second} · to ${state.target} · ${level.name.lowercase()}"
    val bidLine = SpadesSeat.entries.joinToString("   ") {
        "${it.name.take(1).lowercase()}:${state.bids[it] ?: 0}${if (state.nilBid[it] == true) "n" else ""} (${state.tricksTaken[it]?.size ?: 0})"
    }
    Column(Modifier.fillMaxSize()) {
        BasicText(
            text = scoreLine,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            modifier = Modifier.appDescription(scoreLine),
        )
        BasicText(
            text = bidLine,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            modifier = Modifier.appDescription(bidLine),
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            state.history.takeLast(5).forEach { r ->
                BasicText(
                    text = "r${r.round + 1} ${r.teamDelta.first}/${r.teamDelta.second}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (!state.biddingDone) {
            BasicText(
                text = if (state.currentPlayer == SpadesSeat.SOUTH) {
                    if (!revealed) "your bid — blind nil is open" else "your bid (0 = nil)"
                } else "ai bidding…",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            if (state.currentPlayer == SpadesSeat.SOUTH) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    if (!revealed) {
                        SpadesAction("reveal cards", colors.accent) { onReveal() }
                        if (blindNilEnabled) SpadesAction("blind nil", colors.accent) { onBid(0, true) }
                    } else {
                        (0..13).forEach { n ->
                            SpadesAction(if (n == 0) "nil" else "$n", colors.accent) { onBid(n, false) }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.trick.forEach { (s, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                        SolCardView(c, width = 30.dp, height = 40.dp) {}
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (state.done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "round ${state.roundNumber + 1} scored",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                        modifier = Modifier.weight(1f),
                    )
                    SpadesAction("new round", colors.accent) { onNextRound() }
                }
            } else if (state.currentPlayer == SpadesSeat.SOUTH) {
                val legal = SpadesEngine.legalPlays(state, SpadesSeat.SOUTH)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    legal.forEach { c -> SolCardView(c, width = 26.dp, height = 34.dp) { onPlay(c) } }
                }
            } else {
                BasicText(text = "ai playing…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = "your hand (${state.hands[SpadesSeat.SOUTH]?.size ?: 0})",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val hand = state.hands[SpadesSeat.SOUTH].orEmpty()
            if (state.biddingDone || revealed) {
                hand.forEach { c -> SolCardView(c.copy(faceUp = true), width = 26.dp, height = 34.dp) {} }
            } else {
                hand.forEach { c -> SolCardView(c.copy(faceUp = false), width = 26.dp, height = 34.dp) {} }
            }
        }
    }
}

@Composable
private fun GameOverPanel(state: SpadesState, onNewGame: () -> Unit, onMenu: () -> Unit) {
    val colors = LocalDoradoColors.current
    val headline = when (state.winnerTeam) {
        0 -> "your team wins"
        1 -> "west/east win"
        else -> "tie"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = headline, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
        BasicText(
            text = "S-N ${state.score.first} · W-E ${state.score.second} off ${state.roundNumber + 1} rounds",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.height(4.dp))
        SpadesAction("new game", colors.accent, onNewGame)
        SpadesAction("menu", colors.textSecondary, onMenu)
    }
}

@Composable
private fun SpadesAction(label: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = color),
        modifier = Modifier
            .appTap(label = label) { onClick() }
            .padding(vertical = 3.dp, horizontal = 2.dp),
    )
}

@Composable
private fun SpadesToggle(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
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
