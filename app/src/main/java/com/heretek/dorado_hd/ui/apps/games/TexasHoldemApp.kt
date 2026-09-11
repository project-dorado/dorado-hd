package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/* ============================================================ */
/*                          Texas Hold 'Em                        */
/* ============================================================ */

@Composable
fun TexasHoldemApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var difficulty by remember { mutableStateOf(PokerDifficulty.EASY) }
    var tournament by remember { mutableStateOf(PokerTournamentEngine.newTournament()) }
    var activeEvent by remember { mutableStateOf<PokerTournamentEvent?>(null) }
    var table by remember { mutableStateOf(PokerTableEngine.newTable()) }
    var inHand by remember { mutableStateOf(false) }
    var raiseTo by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        graph.appState.get("texasholdem")?.let { blob ->
            blob.split(';').mapNotNull { it.split('=').takeIf { p -> p.size == 2 } }
                .associate { it[0] to it[1] }
                .let { map ->
                    map["difficulty"]?.let { d -> difficulty = PokerDifficulty.entries.firstOrNull { it.name.lowercase() == d } ?: difficulty }
                    val bankroll = map["bankroll"]?.toIntOrNull()
                    val completed = map["completed"]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                    if (bankroll != null) {
                        tournament = PokerTournamentEngine.newTournament(bankroll).copy(completed = completed)
                    }
                }
        }
    }

    fun persist() {
        scope.launch {
            graph.appState.put(
                "texasholdem",
                "difficulty=${difficulty.name.lowercase()};bankroll=${tournament.bankroll};completed=${tournament.completed.joinToString(",")}",
            )
        }
    }

    fun dealCash() {
        activeEvent = null
        table = PokerTableEngine.startHand(
            PokerTableEngine.newTable(difficulty, startingStack = 1000),
            Random.Default,
        )
        inHand = true
        raiseTo = 0
    }

    fun dealEvent(event: PokerTournamentEvent) {
        tournament = PokerTournamentEngine.enter(tournament, event.index)
        activeEvent = event
        table = PokerTableEngine.startHand(
            PokerTableEngine.newTable(event.difficulty, startingStack = event.startingChips),
            Random.Default,
        )
        inHand = true
        raiseTo = 0
        persist()
    }

    // One AI action per state change; the human seat is left for the buttons.
    LaunchedEffect(table) {
        val cur = table
        if (!inHand || cur.acting < 0 || cur.acting == cur.humanSeat) return@LaunchedEffect
        if (cur.phase == PokerTablePhase.SHOWDOWN || cur.phase == PokerTablePhase.DONE) return@LaunchedEffect
        delay(220)
        val next = PokerTableEngine.stepAi(cur, Random.Default)
        if (next != cur) table = next
    }

    LaunchedEffect(table.acting) {
        if (table.acting == table.humanSeat) {
            raiseTo = PokerTableEngine.minRaiseTo(table, table.humanSeat)
        }
    }

    // Hand finished: record it, settle tournaments, offer the next hand.
    LaunchedEffect(table.phase) {
        if (!inHand) return@LaunchedEffect
        if (table.phase != PokerTablePhase.SHOWDOWN && table.phase != PokerTablePhase.DONE) return@LaunchedEffect
        val record = table.handHistory.lastOrNull() ?: return@LaunchedEffect
        val humanWon = table.humanSeat in record.winners
        bank.play(if (humanWon) "coin" else "click")
        scope.launch {
            graph.games.record(
                "texasholdem",
                if (humanWon) 1 else 0,
                "${if (activeEvent == null) "cash" else "event ${activeEvent!!.index + 1}"} · ${record.potWon}",
            )
        }
        val human = table.seats[table.humanSeat]
        if (human.stack <= 0) {
            val place = table.seats.count { !it.out && it.stack > 0 } + 1
            activeEvent?.let { tournament = PokerTournamentEngine.settle(tournament, place) }
            persist()
        } else if (table.seats.none { it.index != table.humanSeat && it.stack > 0 }) {
            activeEvent?.let { tournament = PokerTournamentEngine.settle(tournament, 1) }
            persist()
        }
    }

    DetailScaffold(title = "texas hold 'em") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (!inHand) {
                MenuPanel(
                    difficulty = difficulty,
                    tournament = tournament,
                    colors = colors,
                    onDifficulty = { bank.play("click"); difficulty = it; persist() },
                    onCash = { bank.play("select"); dealCash() },
                    onEvent = { event -> bank.play("select"); dealEvent(event) },
                )
            } else {
                TablePanel(
                    table = table,
                    raiseTo = raiseTo,
                    onRaiseTo = { raiseTo = it },
                    onAction = { action ->
                        bank.play("click")
                        table = PokerTableEngine.apply(table, table.humanSeat, action)
                    },
                    onNextHand = {
                        bank.play("select")
                        val human = table.seats[table.humanSeat]
                        if (human.stack <= 0 || table.seats.none { it.index != table.humanSeat && it.stack > 0 }) {
                            inHand = false
                        } else {
                            table = PokerTableEngine.startHand(table, Random.Default)
                        }
                    },
                    onLeave = { bank.play("back"); inHand = false },
                )
            }
        }
    }
}

@Composable
private fun MenuPanel(
    difficulty: PokerDifficulty,
    tournament: PokerTournamentState,
    colors: DoradoColors,
    onDifficulty: (PokerDifficulty) -> Unit,
    onCash: () -> Unit,
    onEvent: (PokerTournamentEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            text = "bankroll ${tournament.bankroll}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(text = "difficulty", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            PokerDifficulty.entries.forEach { d ->
                val active = d == difficulty
                BasicText(
                    text = d.name.lowercase(),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (active) colors.accent else colors.textSecondary),
                    modifier = Modifier.pointerInput(d) { detectTapGestures(onTap = { if (!active) onDifficulty(d) }) },
                )
            }
        }
        HoldemAction("cash game — 6 seats, 1000 chips", colors.accent, onCash)
        Spacer(Modifier.height(2.dp))
        BasicText(text = "tournament ladder", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        PokerTournamentEngine.EVENTS.forEach { event ->
            val done = tournament.completed.contains(event.index)
            val affordable = PokerTournamentEngine.canEnter(tournament, event.index)
            val label = "e${event.index + 1}  buy-in ${event.buyIn} · ${event.players}p · ${event.difficulty.name.lowercase()}" +
                if (done) "  (done)" else if (!affordable) "  (locked)" else ""
            HoldemAction(label, if (affordable) colors.accent else colors.textSecondary) {
                if (affordable) onEvent(event)
            }
        }
    }
}

@Composable
private fun TablePanel(
    table: PokerTableState,
    raiseTo: Int,
    onRaiseTo: (Int) -> Unit,
    onAction: (TableAction) -> Unit,
    onNextHand: () -> Unit,
    onLeave: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val terminal = table.phase == PokerTablePhase.SHOWDOWN || table.phase == PokerTablePhase.DONE
    Column(Modifier.fillMaxSize()) {
        BasicText(
            text = "pot ${table.pot} · ${table.phase.name.lowercase()} · blinds ${table.smallBlind}/${table.bigBlind} · hand ${table.handNumber}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            table.board.forEach { SolCardView(it, width = 26.dp, height = 34.dp) {} }
            if (table.board.isEmpty()) {
                BasicText(text = "board", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            }
        }
        Spacer(Modifier.height(2.dp))
        table.seats.forEach { seat ->
            val active = table.acting == seat.index
            val hole = when {
                seat.out -> "out"
                seat.folded -> "folded"
                seat.index == table.humanSeat || terminal -> seat.hole.joinToString(" ") { "${rankLabel(it.rank)}${suitGlyph(it.suit)}" }
                else -> "-- --"
            }
            BasicText(
                text = "${seat.name} ${seat.stack}${if (seat.streetCommitted > 0) " (+${seat.streetCommitted})" else ""} ${seat.personality.name.lowercase()}" +
                    "  $hole${if (seat.allIn) "  all-in" else ""}${if (seat.lastAction.isNotEmpty()) "  · ${seat.lastAction}" else ""}",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = if (active) colors.accent else if (seat.folded) colors.textSecondary else colors.textPrimary,
                ),
            )
        }
        if (PokerTableEngine.sidePots(table).size > 1) {
            BasicText(
                text = PokerTableEngine.sidePots(table).joinToString("   ") { "${it.label} ${it.amount} (${it.eligible.joinToString(",")})" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
        Spacer(Modifier.height(2.dp))
        if (terminal) {
            Row {
                BasicText(
                    text = table.showdownText ?: "hand over",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    modifier = Modifier.weight(1f),
                )
                HoldemAction("next hand", colors.accent, onNextHand)
                HoldemAction("menu", colors.textSecondary, onLeave)
            }
        } else if (table.acting == table.humanSeat) {
            val legal = PokerTableEngine.legalActions(table, table.humanSeat)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                legal.forEach { kind ->
                    when (kind) {
                        TableActionKind.BET, TableActionKind.RAISE -> {
                            val min = PokerTableEngine.minRaiseTo(table, table.humanSeat)
                            val max = PokerTableEngine.maxRaiseTo(table, table.humanSeat)
                            HoldemAction("-", colors.textSecondary) { onRaiseTo((raiseTo - table.minChip).coerceAtLeast(min)) }
                            BasicText(
                                text = "${kind.name.lowercase()} $raiseTo",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                modifier = Modifier.pointerInput(kind, raiseTo) {
                                    detectTapGestures(onTap = { onAction(TableAction(kind, raiseTo)) })
                                },
                            )
                            HoldemAction("+", colors.textSecondary) { onRaiseTo((raiseTo + table.minChip).coerceAtMost(max)) }
                        }
                        else -> HoldemAction(kind.name.lowercase(), colors.accent) { onAction(TableAction(kind)) }
                    }
                }
            }
        } else {
            BasicText(text = "ai thinking…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
        }
        Spacer(Modifier.height(3.dp))
        val log = table.log.takeLast(2)
        log.forEach { line ->
            BasicText(text = line, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
        val history = table.handHistory.takeLast(2)
        history.forEach { h ->
            BasicText(
                text = "h${h.handNumber} pot ${h.potWon} → ${h.winners.joinToString(",")}${if (h.showdown) " (sd)" else ""}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun HoldemAction(label: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = color),
        modifier = Modifier
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 2.dp, horizontal = 1.dp),
    )
}
