package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/* ============================================================ */
/*                              Hearts                             */
/* ============================================================ */

enum class PokerAction { FOLD, CHECK, CALL, BET_RAISE }
enum class PokerPhase { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, DONE }

data class PokerState(
    val deck: List<SolCard>,
    val playerHole: List<SolCard>,
    val aiHole: List<SolCard>,
    val community: List<SolCard>,
    val pot: Int,
    val playerStack: Int,
    val aiStack: Int,
    val playerContributed: Int,     // chips this seat has put in this street
    val aiContributed: Int,
    val currentBet: Int,             // highest street contribution
    val lastRaiseSize: Int,         // minimum raise increment (BB)
    val acted: Int,                  // bit 1 = player acted, bit 2 = ai acted this street
    val phase: PokerPhase,
    val actor: Boolean,             // true = human, false = AI
    val winner: String?,            // "player" | "ai" | "tie"
    val log: List<String>,
)

object PokerEngine {
    private val RANK = 2..14
    fun freshDeck(rng: Random = Random.Default): MutableList<SolCard> = (SolSuit.values()).flatMap { s -> RANK.map { r -> SolCard(s, r, true) } }.shuffled(rng).toMutableList()

    fun newGame(rng: Random = Random.Default): PokerState {
        val deck = freshDeck(rng)
        val playerHole = listOf(deck.removeAt(0), deck.removeAt(0))
        val aiHole = listOf(deck.removeAt(0), deck.removeAt(0))
        // Human posts the big blind (10), AI the small blind (5); human acts first preflop.
        return PokerState(
            deck = deck, playerHole = playerHole, aiHole = aiHole, community = emptyList(),
            pot = 15,
            playerStack = 990, aiStack = 985,
            playerContributed = 10, aiContributed = 5,
            currentBet = 10, lastRaiseSize = 10, acted = 2,
            phase = PokerPhase.PREFLOP, actor = true, winner = null, log = listOf("blinds posted — small 5, big 10"),
        )
    }

    fun legalActions(state: PokerState): List<PokerAction> {
        if (state.phase == PokerPhase.DONE || state.phase == PokerPhase.SHOWDOWN) return emptyList()
        val myContribution = if (state.actor) state.playerContributed else state.aiContributed
        val owed = (state.currentBet - myContribution).coerceAtLeast(0)
        return if (owed == 0) listOf(PokerAction.CHECK, PokerAction.BET_RAISE, PokerAction.FOLD)
        else listOf(PokerAction.FOLD, PokerAction.CALL, PokerAction.BET_RAISE)
    }

    fun applyAction(state: PokerState, action: PokerAction): PokerState {
        if (state.phase == PokerPhase.DONE || state.phase == PokerPhase.SHOWDOWN) return state
        val isPlayer = state.actor
        val who = if (isPlayer) "you" else "ai"
        val myContribution = if (isPlayer) state.playerContributed else state.aiContributed
        val myStack = if (isPlayer) state.playerStack else state.aiStack
        val owed = (state.currentBet - myContribution).coerceAtLeast(0)
        var acted = state.acted or (if (isPlayer) 1 else 2)
        var newState = when (action) {
            PokerAction.FOLD -> return state.copy(phase = PokerPhase.DONE, winner = if (isPlayer) "ai" else "player", actor = false, log = state.log + "$who folds")
            PokerAction.CHECK -> {
                if (owed > 0) return state
                state.copy(log = state.log + "$who checks")
            }
            PokerAction.CALL -> {
                val paying = min(owed, myStack)
                if (isPlayer) state.copy(playerStack = myStack - paying, playerContributed = myContribution + paying) else state.copy(aiStack = myStack - paying, aiContributed = myContribution + paying)
                    .let { it.copy(pot = state.pot + paying, log = state.log + "$who calls $paying") }
            }
            PokerAction.BET_RAISE -> {
                val raiseTo = (state.currentBet + state.lastRaiseSize).coerceAtMost(myContribution + myStack)
                val paying = (raiseTo - myContribution).coerceAtLeast(0)
                // A raise resets the opponent's "acted" flag — they must respond.
                acted = if (isPlayer) 1 else 2
                val updated = if (isPlayer) state.copy(playerStack = myStack - paying, playerContributed = myContribution + paying) else state.copy(aiStack = myStack - paying, aiContributed = myContribution + paying)
                updated.copy(pot = state.pot + paying, currentBet = maxOf(state.currentBet, raiseTo), log = state.log + "$who raises to $raiseTo")
            }
        }
        newState = newState.copy(acted = acted)
        val matched = newState.playerContributed == newState.aiContributed
        val bothActed = newState.acted == 3
        return if (matched && bothActed) nextStreet(newState) else newState.copy(actor = !isPlayer)
    }

    private fun nextStreet(state: PokerState): PokerState {
        fun fresh(next: PokerPhase, cards: List<SolCard>): PokerState = state.copy(
            phase = next,
            deck = state.deck.drop(cards.size),
            community = state.community + cards,
            playerContributed = 0,
            aiContributed = 0,
            currentBet = 0,
            lastRaiseSize = 10,
            acted = 0,
            actor = true,
        )
        return when (state.phase) {
            PokerPhase.PREFLOP -> fresh(PokerPhase.FLOP, state.deck.take(3))
            PokerPhase.FLOP -> fresh(PokerPhase.TURN, state.deck.take(1))
            PokerPhase.TURN -> fresh(PokerPhase.RIVER, state.deck.take(1))
            PokerPhase.RIVER -> showdown(state)
            else -> state
        }
    }

    private fun showdown(state: PokerState): PokerState {
        val p = scoreHand(state.playerHole + state.community)
        val a = scoreHand(state.aiHole + state.community)
        val cmp = compareHands(p, a)
        val winner = if (cmp > 0) "player" else if (cmp < 0) "ai" else "tie"
        val pot = state.pot
        val playerStack = when (winner) {
            "player" -> state.playerStack + pot
            "tie" -> state.playerStack + pot / 2
            else -> state.playerStack
        }
        val aiStack = when (winner) {
            "ai" -> state.aiStack + pot
            "tie" -> state.aiStack + (pot - pot / 2)
            else -> state.aiStack
        }
        return state.copy(
            phase = PokerPhase.SHOWDOWN,
            winner = winner,
            playerStack = playerStack,
            aiStack = aiStack,
            actor = false,
            log = state.log + "showdown — $winner wins ${pot}",
        )
    }

    /** Positive when `a` beats `b`, comparison includes kickers. */
    private fun compareHands(a: Pair<Int, List<Int>>, b: Pair<Int, List<Int>>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        val pa = kickersPadded(a.second)
        val pb = kickersPadded(b.second)
        for (i in pa.indices) if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        return 0
    }

    /** A simple "tight-passive bluff" AI. */
    fun aiAction(state: PokerState, rng: Random): PokerAction {
        val legal = legalActions(state)
        if (legal.isEmpty()) return PokerAction.CHECK
        // Tight: preflop, fold weak hole cards with probability 0.6.
        val holeScore = state.aiHole.sumOf { if (it.rank >= 11) 4 else if (it.rank >= 7) 2 else 0 }
        if (state.phase == PokerPhase.PREFLOP && holeScore < 4 && rng.nextInt(5) < 3) return PokerAction.FOLD
        // Bluff 1 in 8 times if all checks.
        if (legal.contains(PokerAction.CHECK) && rng.nextInt(8) == 0) return PokerAction.BET_RAISE
        return when {
            legal.contains(PokerAction.CHECK) -> PokerAction.CHECK
            else -> PokerAction.CALL
        }
    }

    /** Hand evaluator. Returns (category, kickers) — larger is better. Picks the best 5 of 7. */
    fun scoreHand(seven: List<SolCard>): Pair<Int, List<Int>> {
        val cards = seven.take(7)
        if (cards.isEmpty()) return 0 to emptyList()
        // enumerate all 5-of-7 subsets and keep the best scoring one
        val idx = (0 until cards.size).toList()
        val subsets = subsetsOfSize5(idx)
        var bestCat = 0
        var bestKickers: List<Int> = emptyList()
        for (sub in subsets) {
            val five = sub.map { cards[it] }
            val ranks = five.map { it.rank }.sortedDescending()
            val suits = five.map { it.suit }
            val rankCounts = ranks.groupingBy { it }.eachCount()
            val flush = suits.toSet().size == 1
            val straight = isStraight(ranks.distinct().sorted())
            val (cat, kickers) = when {
                flush && straight -> 9 to listOf(straightHigh(ranks.distinct().sorted()))
                rankCounts.values.contains(4) -> {
                    val quad = rankCounts.entries.first { it.value == 4 }.key
                    8 to listOf(quad, ranks.first { it != quad })
                }
                rankCounts.values.contains(3) && rankCounts.values.contains(2) -> {
                    val trip = rankCounts.entries.first { it.value == 3 }.key
                    val pair = rankCounts.entries.first { it.value == 2 }.key
                    7 to listOf(trip, pair)
                }
                flush -> 6 to ranks
                straight -> 5 to listOf(straightHigh(ranks.distinct().sorted()))
                rankCounts.values.contains(3) -> {
                    val trip = rankCounts.entries.first { it.value == 3 }.key
                    4 to (listOf(trip) + ranks.filter { it != trip }.take(2))
                }
                rankCounts.filter { it.value == 2 }.size == 2 -> {
                    val pairs = rankCounts.filter { it.value == 2 }.keys.sortedDescending()
                    val (p1, p2) = pairs
                    3 to (listOf(p1, p2) + ranks.filter { it != p1 && it != p2 }.take(1))
                }
                rankCounts.values.contains(2) -> {
                    val p = rankCounts.filter { it.value == 2 }.keys.first()
                    2 to (listOf(p) + ranks.filter { it != p }.take(3))
                }
                else -> 1 to ranks.take(5)
            }
            if (cat > bestCat || (cat == bestCat && kicksGreater(kickers, bestKickers))) {
                bestCat = cat; bestKickers = kickers
            }
        }
        return bestCat to bestKickers
    }

    private fun subsetsOfSize5(idx: List<Int>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        fun rec(start: Int, picked: List<Int>) {
            if (picked.size == 5) { out += picked; return }
            for (i in start until idx.size) rec(i + 1, picked + idx[i])
        }
        rec(0, emptyList())
        return out
    }

    /** Pad kicker lists to a common length with zeros so the elements can be compared pairwise. */
    private fun kickersPadded(k: List<Int>, length: Int = 5): List<Int> = (k + List(length) { 0 }).take(length)

    private fun kicksGreater(a: List<Int>, b: List<Int>): Boolean {
        val pa = kickersPadded(a); val pb = kickersPadded(b)
        for (i in pa.indices) if (pa[i] != pb[i]) return pa[i] > pb[i]
        return false
    }

    private fun isStraight(sortedRanks: List<Int>): Boolean {
        if (sortedRanks.size != 5) return false
        // Wheel: A-2-3-4-5
        if (sortedRanks == listOf(2, 3, 4, 5, 14)) return true
        for (i in 1 until 5) if (sortedRanks[i] != sortedRanks[i - 1] + 1) return false
        return true
    }
    private fun straightHigh(sortedRanks: List<Int>): Int = if (sortedRanks == listOf(2, 3, 4, 5, 14)) 5 else sortedRanks.last()
}

/* ============================================================ */
/*                Texas Hold 'Em — six-seat table                 */
/* ============================================================ */

/** Holdem!PokerAIType. */
enum class PokerPersonality { BASELINE, NOVICE, ROCK, MANIAC, SHARK, EXPERIMENTAL }

enum class PokerDifficulty { EASY, MEDIUM, HARD }

/** Holdem!PokerHandType — the AI's coarse strength bucket. */
enum class PokerHandBucket { NOTHING, PRAYER, DRAW, POWER, MONSTER, NUTS }

/** Holdem!PokerBetStrategy. */
enum class PokerBetStrategy { FOLD, BLUFF, CALL, BET, RAISE, ALL_IN }

enum class TableActionKind { FOLD, CHECK, CALL, BET, RAISE, ALL_IN }

data class TableAction(val kind: TableActionKind, val amount: Int = 0)

enum class PokerTablePhase { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, DONE }

data class PokerSeat(
    val index: Int,
    val name: String,
    val personality: PokerPersonality,
    val hole: List<SolCard> = emptyList(),
    val stack: Int = 0,
    val streetCommitted: Int = 0,
    val totalCommitted: Int = 0,
    val folded: Boolean = false,
    val allIn: Boolean = false,
    val out: Boolean = false,
    val lastAction: String = "",
)

data class PokerSidePot(val amount: Int, val eligible: List<Int>, val label: String)

data class PokerHandRecord(
    val handNumber: Int,
    val board: List<SolCard>,
    val potWon: Int,
    val winners: List<Int>,
    val showdown: Boolean,
    val log: List<String>,
)

data class PokerTableState(
    val seats: List<PokerSeat>,
    val board: List<SolCard>,
    val deck: List<SolCard>,
    val phase: PokerTablePhase,
    val dealer: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val minChip: Int,
    val currentBet: Int,
    val minRaise: Int,
    val pot: Int,
    val acting: Int,
    val needsToAct: Set<Int>,
    val handNumber: Int,
    val log: List<String>,
    val handHistory: List<PokerHandRecord> = emptyList(),
    val showdownText: String? = null,
    val finished: Boolean = false,
    val humanSeat: Int = 0,
)

object PokerTableEngine {
    val SEAT_NAMES = listOf("you", "ivy", "rex", "nova", "taj", "kade")

    fun personalitiesFor(difficulty: PokerDifficulty): List<PokerPersonality> = when (difficulty) {
        PokerDifficulty.EASY -> List(6) { PokerPersonality.NOVICE } + List(2) { PokerPersonality.ROCK }
        PokerDifficulty.MEDIUM -> List(2) { PokerPersonality.MANIAC } + List(2) { PokerPersonality.NOVICE } +
            List(2) { PokerPersonality.SHARK } + List(2) { PokerPersonality.ROCK }
        PokerDifficulty.HARD -> List(3) { PokerPersonality.MANIAC } + List(4) { PokerPersonality.SHARK } + List(1) { PokerPersonality.ROCK }
    }

    /** Seat 0 is the human; the remaining seats draw from the difficulty bag. */
    fun newTable(
        difficulty: PokerDifficulty = PokerDifficulty.EASY,
        startingStack: Int = 1000,
        smallBlind: Int = 5,
        bigBlind: Int = 10,
        seatCount: Int = 6,
        rng: Random = Random.Default,
    ): PokerTableState {
        val bag = personalitiesFor(difficulty).shuffled(rng)
        val seats = (0 until seatCount).map { i ->
            PokerSeat(
                index = i,
                name = SEAT_NAMES.getOrElse(i) { "seat $i" },
                personality = if (i == 0) PokerPersonality.BASELINE else bag[(i - 1) % bag.size],
                stack = startingStack,
            )
        }
        return PokerTableState(
            seats = seats,
            board = emptyList(),
            deck = emptyList(),
            phase = PokerTablePhase.DONE,
            dealer = seatCount - 1,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            minChip = smallBlind,
            currentBet = 0,
            minRaise = bigBlind,
            pot = 0,
            acting = -1,
            needsToAct = emptySet(),
            handNumber = 0,
            log = listOf("table ready · ${difficulty.name.lowercase()}"),
        )
    }

    private fun activeSeats(state: PokerTableState): List<PokerSeat> = state.seats.filter { !it.out }
    private fun contenders(state: PokerTableState): List<PokerSeat> = state.seats.filter { !it.out && !it.folded }
    private fun canAct(state: PokerTableState): List<PokerSeat> = state.seats.filter { !it.out && !it.folded && !it.allIn && it.stack > 0 }

    private fun nextIndex(state: PokerTableState, from: Int): Int {
        var i = from
        repeat(state.seats.size) {
            i = (i + 1) % state.seats.size
            if (!state.seats[i].out) return i
        }
        return from
    }

    private fun firstCanAct(state: PokerTableState, from: Int): Int {
        var i = from
        repeat(state.seats.size) {
            i = (i + 1) % state.seats.size
            if (!state.seats[i].out && !state.seats[i].folded && !state.seats[i].allIn && state.seats[i].stack > 0) return i
        }
        return -1
    }

    private fun potOf(state: PokerTableState): Int = state.seats.sumOf { it.totalCommitted }

    fun potOfSeats(seats: List<PokerSeat>): Int = seats.sumOf { it.totalCommitted }

    /** Deal a fresh hand: rotate the button, post blinds and deal two cards. */
    fun startHand(state: PokerTableState, rng: Random = Random.Default): PokerTableState {
        // A seat that cannot cover a chip is eliminated (Holdem!CPlayer.EBlindState).
        val normalized = state.copy(seats = state.seats.map { if (!it.out && it.stack <= 0) it.copy(out = true) else it })
        val alive = activeSeats(normalized)
        if (alive.size < 2) {
            return normalized.copy(phase = PokerTablePhase.DONE, acting = -1, needsToAct = emptySet(), finished = true)
        }
        var dealer = nextIndex(normalized, normalized.dealer)
        if (normalized.seats[dealer].out) dealer = nextIndex(normalized, dealer)
        // Heads-up: the button posts the small blind.
        val sbIndex = if (alive.size == 2) dealer else nextIndex(normalized, dealer)
        val bbIndex = nextIndex(normalized, sbIndex)

        var deck = PokerEngine.freshDeck(rng)
        val freshSeats = normalized.seats.map { seat ->
            if (seat.out) seat
            else seat.copy(
                hole = emptyList(),
                streetCommitted = 0,
                totalCommitted = 0,
                folded = false,
                allIn = false,
                lastAction = "",
            )
        }.toMutableList()
        for (seat in freshSeats) {
            if (seat.out) continue
            val first = deck.removeAt(0)
            val second = deck.removeAt(0)
            freshSeats[seat.index] = freshSeats[seat.index].copy(hole = listOf(first, second))
        }
        fun postBlind(index: Int, amount: Int) {
            val seat = freshSeats[index]
            val pay = minOf(amount, seat.stack)
            freshSeats[index] = seat.copy(
                stack = seat.stack - pay,
                streetCommitted = pay,
                totalCommitted = seat.totalCommitted + pay,
                allIn = seat.stack - pay == 0,
                lastAction = if (index == sbIndex) "small blind" else "big blind",
            )
        }
        postBlind(sbIndex, state.smallBlind)
        postBlind(bbIndex, state.bigBlind)
        val base = state.copy(
            seats = freshSeats.toList(),
            board = emptyList(),
            deck = deck,
            phase = PokerTablePhase.PREFLOP,
            dealer = dealer,
            currentBet = state.bigBlind,
            minRaise = state.bigBlind,
            pot = potOfSeats(freshSeats),
            acting = -1,
            needsToAct = emptySet(),
            handNumber = state.handNumber + 1,
            log = (state.log + "hand ${state.handNumber + 1} — blinds ${state.smallBlind}/${state.bigBlind}").takeLast(60),
            showdownText = null,
            finished = false,
        )
        val next = base.copy(
            acting = firstCanAct(base, bbIndex),
            needsToAct = canAct(base).map { it.index }.toSet(),
        )
        // If a blind was too short to fulfil the bet, the remaining action is
        // still correct: players act against currentBet and the short blind is
        // all-in. If nobody can act, run the board out immediately.
        return if (next.needsToAct.isEmpty()) runOutBoard(next) else next
    }

    fun legalActions(state: PokerTableState, seatIndex: Int): List<TableActionKind> {
        if (state.acting != seatIndex) return emptyList()
        if (state.phase == PokerTablePhase.SHOWDOWN || state.phase == PokerTablePhase.DONE) return emptyList()
        val seat = state.seats.getOrNull(seatIndex) ?: return emptyList()
        if (seat.out || seat.folded || seat.allIn) return emptyList()
        val owed = (state.currentBet - seat.streetCommitted).coerceAtLeast(0)
        val out = mutableListOf(TableActionKind.FOLD)
        if (owed == 0) out += TableActionKind.CHECK else out += TableActionKind.CALL
        if (seat.stack > owed) {
            if (owed == 0) out += TableActionKind.BET else out += TableActionKind.RAISE
        }
        if (seat.stack > 0) out += TableActionKind.ALL_IN
        return out
    }

    fun minRaiseTo(state: PokerTableState, seatIndex: Int): Int {
        val seat = state.seats.getOrNull(seatIndex) ?: return state.bigBlind
        return (if (state.currentBet == 0) state.bigBlind else state.currentBet + state.minRaise)
            .coerceAtMost(seat.streetCommitted + seat.stack)
    }

    fun maxRaiseTo(state: PokerTableState, seatIndex: Int): Int {
        val seat = state.seats.getOrNull(seatIndex) ?: return 0
        return seat.streetCommitted + seat.stack
    }

    private fun floorToChip(value: Int, chip: Int): Int = if (chip <= 1) value else value - value % chip

    /**
     * Apply a seat action. Amounts on BET/RAISE are clamped into the legal
     * window and floored to the lowest chip denomination in play.
     */
    fun apply(state: PokerTableState, seatIndex: Int, action: TableAction): PokerTableState {
        if (state.acting != seatIndex) return state
        if (state.phase == PokerTablePhase.SHOWDOWN || state.phase == PokerTablePhase.DONE) return state
        val seat = state.seats[seatIndex]
        if (seat.out || seat.folded || seat.allIn) return state
        val owed = (state.currentBet - seat.streetCommitted).coerceAtLeast(0)
        val seats = state.seats.toMutableList()
        var currentBet = state.currentBet
        var minRaise = state.minRaise
        var needsToAct = state.needsToAct.toMutableSet()
        var log = state.log

        fun commit(index: Int, pay: Int, label: String, fold: Boolean = false) {
            val s = seats[index]
            val realPay = pay.coerceAtMost(s.stack).coerceAtLeast(0)
            seats[index] = s.copy(
                stack = s.stack - realPay,
                streetCommitted = s.streetCommitted + realPay,
                totalCommitted = s.totalCommitted + realPay,
                allIn = s.stack - realPay == 0,
                folded = s.folded || fold,
                lastAction = label,
            )
        }

        when (action.kind) {
            TableActionKind.FOLD -> {
                commit(seatIndex, 0, "fold", fold = true)
                needsToAct.remove(seatIndex)
                log = log + "${seat.name} folds"
            }
            TableActionKind.CHECK -> {
                if (owed > 0) return state
                commit(seatIndex, 0, "check")
                needsToAct.remove(seatIndex)
                log = log + "${seat.name} checks"
            }
            TableActionKind.CALL -> {
                if (owed <= 0) return state
                commit(seatIndex, owed, "call")
                needsToAct.remove(seatIndex)
                log = log + "${seat.name} calls ${minOf(owed, seat.stack)}"
            }
            TableActionKind.BET, TableActionKind.RAISE -> {
                if (seat.stack <= owed) return state
                val minTarget = if (currentBet == 0) state.bigBlind else currentBet + minRaise
                val maxTarget = seat.streetCommitted + seat.stack
                val lower = minTarget.coerceAtMost(maxTarget)
                var target = action.amount.coerceIn(lower, maxTarget)
                val floored = floorToChip(target, state.minChip)
                target = if (floored >= lower) floored else target
                val pay = target - seat.streetCommitted
                commit(seatIndex, pay, if (currentBet == 0) "bet $target" else "raise $target")
                val reopened = target >= currentBet + minRaise || currentBet == 0
                if (reopened) {
                    minRaise = maxOf(minRaise, target - currentBet)
                    currentBet = target
                    needsToAct = canAct(state.copy(seats = seats.toList())).map { it.index }.toMutableSet()
                }
                needsToAct.remove(seatIndex)
                log = log + "${seat.name} ${if (state.currentBet == 0) "bets" else "raises to"} $target"
            }
            TableActionKind.ALL_IN -> {
                val target = seat.streetCommitted + seat.stack
                val pay = seat.stack
                commit(seatIndex, pay, "all in $target")
                if (target > currentBet) {
                    minRaise = maxOf(minRaise, target - currentBet)
                    currentBet = target
                    needsToAct = canAct(state.copy(seats = seats.toList())).map { it.index }.toMutableSet()
                }
                needsToAct.remove(seatIndex)
                log = log + "${seat.name} is all in $target"
            }
        }
        val updated = state.copy(
            seats = seats.toList(),
            currentBet = currentBet,
            minRaise = minRaise,
            needsToAct = needsToAct,
            pot = potOfSeats(seats),
            log = log.takeLast(60),
        )
        return finishOrContinue(updated)
    }

    private fun finishOrContinue(state: PokerTableState): PokerTableState {
        val contenders = contenders(state)
        if (contenders.size == 1) return awardByFold(state, contenders.single())
        if (canAct(state).isEmpty()) return runOutBoard(state)
        if (state.needsToAct.isEmpty()) return advanceStreet(state)
        val next = firstInNeedsToAct(state, state.acting)
        return state.copy(acting = next)
    }

    private fun firstInNeedsToAct(state: PokerTableState, from: Int): Int {
        var i = from
        repeat(state.seats.size) {
            i = (i + 1) % state.seats.size
            if (i in state.needsToAct) return i
        }
        return -1
    }

    /** Deal the next street, skipping betting when no one can act. */
    private fun advanceStreet(state: PokerTableState): PokerTableState {
        var s = state
        var guard = 0
        while (guard < 5) {
            guard += 1
            val nextPhase = when (s.phase) {
                PokerTablePhase.PREFLOP -> PokerTablePhase.FLOP
                PokerTablePhase.FLOP -> PokerTablePhase.TURN
                PokerTablePhase.TURN -> PokerTablePhase.RIVER
                else -> return showdown(s)
            }
            val count = if (nextPhase == PokerTablePhase.FLOP) 3 else 1
            val deck = s.deck.toMutableList()
            val dealt = (0 until count).map { deck.removeAt(0) }
            val resetSeats = s.seats.map { seat ->
                if (seat.out) seat else seat.copy(streetCommitted = 0, lastAction = "")
            }
            s = s.copy(
                seats = resetSeats,
                board = s.board + dealt,
                deck = deck,
                phase = nextPhase,
                currentBet = 0,
                minRaise = s.bigBlind,
                pot = potOfSeats(resetSeats),
                needsToAct = emptySet(),
                acting = -1,
                log = (s.log + nextPhase.name.lowercase()).takeLast(60),
            )
            if (contenders(s).size <= 1) {
                val c = contenders(s).singleOrNull() ?: return s
                return awardByFold(s, c)
            }
            val actors = canAct(s)
            if (actors.isEmpty()) continue
            val first = firstCanAct(s, s.dealer)
            s = s.copy(acting = first, needsToAct = actors.map { it.index }.toSet())
            return s
        }
        return showdown(s)
    }

    private fun runOutBoard(state: PokerTableState): PokerTableState {
        var s = state
        var guard = 0
        while (s.phase != PokerTablePhase.RIVER && s.phase != PokerTablePhase.SHOWDOWN && s.phase != PokerTablePhase.DONE && guard < 5) {
            guard += 1
            val nextPhase = if (s.phase == PokerTablePhase.PREFLOP) PokerTablePhase.FLOP
            else if (s.phase == PokerTablePhase.FLOP) PokerTablePhase.TURN
            else PokerTablePhase.RIVER
            val count = if (nextPhase == PokerTablePhase.FLOP) 3 else 1
            val deck = s.deck.toMutableList()
            val dealt = (0 until count).map { deck.removeAt(0) }
            s = s.copy(board = s.board + dealt, deck = deck, phase = nextPhase, log = (s.log + nextPhase.name.lowercase()).takeLast(60))
        }
        return showdown(s)
    }

    /** Build the layered pots from every seat's total commitment (Holdem!CPot). */
    fun sidePots(state: PokerTableState): List<PokerSidePot> {
        val levels = state.seats.filter { it.totalCommitted > 0 }.map { it.totalCommitted }.distinct().sorted()
        val pots = mutableListOf<PokerSidePot>()
        var prev = 0
        for (level in levels) {
            val contributors = state.seats.filter { it.totalCommitted >= level }
            val amount = (level - prev) * contributors.size
            if (amount > 0) {
                val eligible = contributors.filter { !it.folded && !it.out }.map { it.index }
                if (eligible.isEmpty() && pots.isNotEmpty()) {
                    // Dead layer (only folded money): fold it into the pot below.
                    pots[pots.size - 1] = pots.last().copy(amount = pots.last().amount + amount)
                } else {
                    val label = if (pots.isEmpty()) "main" else "side ${pots.size}"
                    pots += PokerSidePot(amount, eligible, label)
                }
            }
            prev = level
        }
        return pots
    }

    private fun compareHands(a: Pair<Int, List<Int>>, b: Pair<Int, List<Int>>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        fun pad(k: List<Int>) = (k + List(5) { 0 }).take(5)
        val pa = pad(a.second); val pb = pad(b.second)
        for (i in pa.indices) if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        return 0
    }

    private fun awardByFold(state: PokerTableState, winner: PokerSeat): PokerTableState {
        val pot = potOf(state)
        val seats = state.seats.map { if (it.index == winner.index) it.copy(stack = it.stack + pot) else it }
        val record = PokerHandRecord(state.handNumber, state.board, pot, listOf(winner.index), false, state.log.takeLast(40))
        return state.copy(
            seats = seats,
            pot = 0,
            phase = PokerTablePhase.DONE,
            acting = -1,
            needsToAct = emptySet(),
            log = (state.log + "${winner.name} wins $pot (everyone folded)").takeLast(60),
            showdownText = "${winner.name} wins $pot",
            handHistory = (state.handHistory + record).takeLast(50),
        )
    }

    private fun showdown(state: PokerTableState): PokerTableState {
        val contenders = contenders(state)
        val pots = sidePots(state)
        val seats = state.seats.toMutableList()
        val results = mutableListOf<String>()
        val allWinners = mutableSetOf<Int>()
        for (pot in pots) {
            val eligible = pot.eligible.filter { !state.seats[it].folded && !state.seats[it].out }
            if (eligible.isEmpty()) continue
            val scored = eligible.map { it to PokerEngine.scoreHand(state.seats[it].hole + state.board) }
            var best = scored.first().second
            for ((_, score) in scored) if (compareHands(score, best) > 0) best = score
            val winners = scored.filter { compareHands(it.second, best) == 0 }.map { it.first }
            val share = pot.amount / winners.size
            var remainder = pot.amount - share * winners.size
            for (w in winners) {
                val extra = if (remainder > 0) { remainder -= 1; 1 } else 0
                seats[w] = seats[w].copy(stack = seats[w].stack + share + extra)
                allWinners += w
            }
            results += "${pot.label} ${pot.amount} -> " + winners.joinToString(",") { state.seats[it].name }
        }
        val total = potOf(state)
        val text = results.joinToString(" · ")
        val record = PokerHandRecord(state.handNumber, state.board, total, allWinners.toList(), true, state.log.takeLast(40))
        return state.copy(
            seats = seats.toList(),
            pot = 0,
            phase = PokerTablePhase.SHOWDOWN,
            acting = -1,
            needsToAct = emptySet(),
            log = (state.log + "showdown — $text").takeLast(60),
            showdownText = text,
            handHistory = (state.handHistory + record).takeLast(50),
        )
    }

    /* ------------------------------ AI ------------------------------ */

    private fun promote(b: PokerHandBucket): PokerHandBucket =
        PokerHandBucket.entries[(b.ordinal + 1).coerceAtMost(PokerHandBucket.entries.lastIndex)]

    private fun preflopBucket(hole: List<SolCard>): PokerHandBucket {
        if (hole.size < 2) return PokerHandBucket.NOTHING
        val high = maxOf(hole[0].rank, hole[1].rank)
        val low = minOf(hole[0].rank, hole[1].rank)
        val pair = high == low
        val suited = hole[0].suit == hole[1].suit
        return when {
            pair && high >= 12 -> PokerHandBucket.MONSTER
            pair && high >= 10 -> PokerHandBucket.POWER
            pair -> PokerHandBucket.DRAW
            high == 14 && low >= 13 -> PokerHandBucket.POWER
            high == 14 && low >= 11 -> PokerHandBucket.DRAW
            suited && high >= 12 && low >= 9 -> PokerHandBucket.DRAW
            high + low >= 25 -> PokerHandBucket.DRAW
            high + low >= 20 -> PokerHandBucket.PRAYER
            else -> PokerHandBucket.NOTHING
        }
    }

    private fun postflopBucket(seven: List<SolCard>, board: List<SolCard>): PokerHandBucket {
        val (cat, _) = PokerEngine.scoreHand(seven)
        val bucket = when {
            cat >= 8 -> PokerHandBucket.NUTS
            cat == 7 || cat == 6 -> PokerHandBucket.MONSTER
            cat == 5 || cat == 4 -> PokerHandBucket.POWER
            cat == 3 -> PokerHandBucket.DRAW
            cat == 2 -> {
                val pairRank = seven.groupBy { it.rank }.filter { it.value.size >= 2 }.keys.maxOrNull() ?: 0
                if (pairRank >= 11) PokerHandBucket.DRAW else PokerHandBucket.PRAYER
            }
            else -> {
                val suitCount = seven.groupBy { it.suit }.values.maxOfOrNull { it.size } ?: 0
                val ranks = seven.map { it.rank }.distinct().sorted()
                val straightDraw = ranks.windowed(4).any { w -> w.last() - w.first() <= 3 } || ranks.containsAll(listOf(2, 3, 4, 5))
                if (suitCount >= 4 || straightDraw) PokerHandBucket.DRAW else PokerHandBucket.NOTHING
            }
        }
        // If the board plays better than our hand, step down one bucket.
        if (board.size >= 5) {
            val boardScore = PokerEngine.scoreHand(board)
            val mine = PokerEngine.scoreHand(seven)
            if (compareHands(mine, boardScore) <= 0) {
                val idx = bucket.ordinal - 1
                return PokerHandBucket.entries[idx.coerceAtLeast(0)]
            }
        }
        return bucket
    }

    fun bucket(state: PokerTableState, seatIndex: Int): PokerHandBucket {
        val seat = state.seats.getOrNull(seatIndex) ?: return PokerHandBucket.NOTHING
        if (seat.hole.isEmpty()) return PokerHandBucket.NOTHING
        var b = if (state.board.isEmpty()) preflopBucket(seat.hole) else postflopBucket(seat.hole + state.board, state.board)
        val headsUp = contenders(state).size == 2
        val shortStacked = seat.stack <= state.bigBlind * 5
        val p = seat.personality
        if (p == PokerPersonality.MANIAC || p == PokerPersonality.NOVICE) b = promote(b)
        if ((p == PokerPersonality.MANIAC || p == PokerPersonality.SHARK) && (shortStacked || headsUp)) b = promote(b)
        return b
    }

    private fun bluffThreshold(state: PokerTableState, seat: PokerSeat, bucket: PokerHandBucket): Int {
        val p = seat.personality
        if (p != PokerPersonality.MANIAC && p != PokerPersonality.SHARK) return 0
        val active = contenders(state).size
        val headsUp = active == 2
        var threshold = 5
        if (p == PokerPersonality.MANIAC) threshold *= 2
        if (bucket.ordinal >= PokerHandBucket.PRAYER.ordinal) threshold *= 2
        if (headsUp && state.phase == PokerTablePhase.PREFLOP) threshold *= 2
        if (state.phase != PokerTablePhase.PREFLOP) threshold /= 2
        if (active > 2) threshold /= 2
        return threshold
    }

    fun betAmount(state: PokerTableState, seatIndex: Int, strategy: PokerBetStrategy): Int {
        val seat = state.seats[seatIndex]
        val owed = (state.currentBet - seat.streetCommitted).coerceAtLeast(0)
        val stackTo = seat.streetCommitted + seat.stack
        val raw = when (strategy) {
            PokerBetStrategy.BLUFF -> state.pot * 2 / 3
            PokerBetStrategy.BET -> state.pot / 2 + state.bigBlind
            PokerBetStrategy.RAISE -> state.currentBet + maxOf(state.minRaise, state.pot / 3)
            PokerBetStrategy.CALL -> seat.streetCommitted + owed
            PokerBetStrategy.ALL_IN -> stackTo
            PokerBetStrategy.FOLD -> 0
        }
        val scale = when (seat.personality) {
            PokerPersonality.MANIAC -> 1.5
            PokerPersonality.ROCK -> 0.75
            PokerPersonality.SHARK -> 1.25
            PokerPersonality.NOVICE -> 0.5
            PokerPersonality.BASELINE -> 1.0
            PokerPersonality.EXPERIMENTAL -> 0.9
        }
        val minTarget = if (state.currentBet == 0) state.bigBlind else state.currentBet + state.minRaise
        val maxTarget = seat.streetCommitted + seat.stack
        val lower = minTarget.coerceAtMost(maxTarget)
        var target = (raw * scale).toInt().coerceIn(lower, maxTarget)
        val floored = floorToChip(target, state.minChip)
        target = if (floored >= lower) floored else target
        return target.coerceIn(lower, maxTarget)
    }

    private fun strategyFor(state: PokerTableState, seatIndex: Int, rng: Random): PokerBetStrategy {
        val seat = state.seats[seatIndex]
        val legal = legalActions(state, seatIndex)
        val owed = (state.currentBet - seat.streetCommitted).coerceAtLeast(0)
        val bucket = bucket(state, seatIndex)
        val p = seat.personality
        return when (bucket) {
            PokerHandBucket.NUTS -> if (legal.contains(TableActionKind.ALL_IN)) PokerBetStrategy.ALL_IN else PokerBetStrategy.RAISE
            PokerHandBucket.MONSTER -> when {
                legal.contains(TableActionKind.RAISE) -> PokerBetStrategy.RAISE
                legal.contains(TableActionKind.BET) -> PokerBetStrategy.BET
                else -> PokerBetStrategy.CALL
            }
            PokerHandBucket.POWER -> when {
                owed == 0 && legal.contains(TableActionKind.BET) -> PokerBetStrategy.BET
                legal.contains(TableActionKind.RAISE) -> PokerBetStrategy.RAISE
                else -> PokerBetStrategy.CALL
            }
            else -> {
                val threshold = bluffThreshold(state, seat, bucket)
                val disabled = owed > maxOf(state.pot / 2, state.bigBlind * 4) && bucket.ordinal <= PokerHandBucket.DRAW.ordinal
                if (threshold > 0 && !disabled && rng.nextInt(100) < threshold) {
                    PokerBetStrategy.BLUFF
                } else when (p) {
                    PokerPersonality.ROCK -> PokerBetStrategy.FOLD
                    PokerPersonality.NOVICE -> PokerBetStrategy.CALL
                    PokerPersonality.MANIAC -> if (owed <= maxOf(state.minChip, state.pot / 4)) PokerBetStrategy.CALL else PokerBetStrategy.FOLD
                    PokerPersonality.SHARK -> if (owed == 0) PokerBetStrategy.CALL else if (owed <= state.pot / 3) PokerBetStrategy.CALL else PokerBetStrategy.FOLD
                    else -> if (owed <= state.pot / 4) PokerBetStrategy.CALL else PokerBetStrategy.FOLD
                }
            }
        }
    }

    fun aiAction(state: PokerTableState, seatIndex: Int, rng: Random = Random.Default): TableAction {
        val legal = legalActions(state, seatIndex)
        if (legal.isEmpty()) return TableAction(TableActionKind.CHECK)
        val strategy = strategyFor(state, seatIndex, rng)
        var kind = when (strategy) {
            PokerBetStrategy.FOLD -> if (legal.contains(TableActionKind.CHECK)) TableActionKind.CHECK else TableActionKind.FOLD
            PokerBetStrategy.CALL -> if (legal.contains(TableActionKind.CHECK)) TableActionKind.CHECK else TableActionKind.CALL
            PokerBetStrategy.BET -> when {
                legal.contains(TableActionKind.BET) -> TableActionKind.BET
                legal.contains(TableActionKind.RAISE) -> TableActionKind.RAISE
                legal.contains(TableActionKind.CALL) -> TableActionKind.CALL
                else -> TableActionKind.CHECK
            }
            PokerBetStrategy.BLUFF -> when {
                legal.contains(TableActionKind.BET) -> TableActionKind.BET
                legal.contains(TableActionKind.RAISE) -> TableActionKind.RAISE
                legal.contains(TableActionKind.CALL) -> TableActionKind.CALL
                else -> TableActionKind.CHECK
            }
            PokerBetStrategy.RAISE -> when {
                legal.contains(TableActionKind.RAISE) -> TableActionKind.RAISE
                legal.contains(TableActionKind.BET) -> TableActionKind.BET
                legal.contains(TableActionKind.CALL) -> TableActionKind.CALL
                else -> TableActionKind.CHECK
            }
            PokerBetStrategy.ALL_IN -> if (legal.contains(TableActionKind.ALL_IN)) TableActionKind.ALL_IN else TableActionKind.CHECK
        }
        // DontFold: never fold when checked to, and call when clearly priced in.
        if (kind == TableActionKind.FOLD) {
            val seat = state.seats[seatIndex]
            val owed = (state.currentBet - seat.streetCommitted).coerceAtLeast(0)
            val bucket = bucket(state, seatIndex)
            val pricedIn = owed > 0 && owed <= maxOf(state.minChip, state.pot / 10) && bucket.ordinal >= PokerHandBucket.PRAYER.ordinal
            kind = when {
                legal.contains(TableActionKind.CHECK) && owed == 0 -> TableActionKind.CHECK
                legal.contains(TableActionKind.CALL) && pricedIn -> TableActionKind.CALL
                else -> TableActionKind.FOLD
            }
        }
        val amount = when (kind) {
            TableActionKind.BET, TableActionKind.RAISE -> betAmount(state, seatIndex, strategy)
            TableActionKind.ALL_IN -> maxRaiseTo(state, seatIndex)
            else -> 0
        }
        return TableAction(kind, amount)
    }

    /** Act for the current seat (AI) once; used by the UI loop. */
    fun stepAi(state: PokerTableState, rng: Random = Random.Default): PokerTableState {
        val acting = state.acting
        if (acting < 0 || acting == state.humanSeat) return state
        return apply(state, acting, aiAction(state, acting, rng))
    }

    /** Drive a hand to completion with AI for every seat (tests + tournament sim). */
    fun autoPlayHand(state: PokerTableState, rng: Random = Random.Default): PokerTableState {
        var s = state
        var guard = 0
        while (s.acting >= 0 && s.phase != PokerTablePhase.SHOWDOWN && s.phase != PokerTablePhase.DONE && guard < 400) {
            val next = apply(s, s.acting, aiAction(s, s.acting, rng))
            if (next == s) break
            s = next
            guard += 1
        }
        return s
    }

    /** Resolve the current board at showdown (public so tests can lock pot splits). */
    fun settleShowdown(state: PokerTableState): PokerTableState = showdown(state)
}

/* ============================================================ */
/*                  Tournament ladder + bankroll                  */
/* ============================================================ */

data class PokerTournamentEvent(
    val index: Int,
    val buyIn: Int,
    val startingChips: Int,
    val players: Int,
    val tables: Int,
    val difficulty: PokerDifficulty,
    val blindStartIndex: Int,
)

data class PokerTournamentState(
    val bankroll: Int,
    val events: List<PokerTournamentEvent>,
    val activeEvent: Int = -1,
    val completed: Set<Int> = emptySet(),
    val history: List<String> = emptyList(),
)

object PokerTournamentEngine {
    /** Holdem!Constants.TournamentAIDifficulty + buy-in ladder. */
    val EVENTS: List<PokerTournamentEvent> = listOf(
        PokerTournamentEvent(0, 0, 100, 6, 1, PokerDifficulty.EASY, 0),
        PokerTournamentEvent(1, 250, 250, 12, 2, PokerDifficulty.EASY, 2),
        PokerTournamentEvent(2, 500, 500, 18, 3, PokerDifficulty.MEDIUM, 4),
        PokerTournamentEvent(3, 1000, 1000, 24, 4, PokerDifficulty.MEDIUM, 6),
        PokerTournamentEvent(4, 1500, 1500, 30, 5, PokerDifficulty.HARD, 8),
        PokerTournamentEvent(5, 5000, 5000, 36, 6, PokerDifficulty.HARD, 10),
        PokerTournamentEvent(6, 10000, 10000, 48, 8, PokerDifficulty.HARD, 12),
    )

    fun newTournament(bankroll: Int = 500): PokerTournamentState =
        PokerTournamentState(bankroll = bankroll, events = EVENTS)

    fun canEnter(state: PokerTournamentState, index: Int): Boolean {
        val event = state.events.getOrNull(index) ?: return false
        return !state.completed.contains(index) &&
            state.activeEvent != index &&
            state.bankroll >= event.buyIn
    }

    fun enter(state: PokerTournamentState, index: Int): PokerTournamentState {
        if (!canEnter(state, index)) return state
        val event = state.events[index]
        return state.copy(
            bankroll = state.bankroll - event.buyIn,
            activeEvent = index,
            history = state.history + "entered ${eventLabel(event)}",
        )
    }

    /**
     * Leave an event mid-run: refund the buy-in and clear the active event so
     * backing out can never permanently lock the ladder (A-10). Safe to call
     * with no active event (returns the state unchanged).
     */
    fun abandon(state: PokerTournamentState): PokerTournamentState {
        val index = state.activeEvent
        if (index < 0) return state
        val event = state.events.getOrNull(index) ?: return state
        return state.copy(
            bankroll = state.bankroll + event.buyIn,
            activeEvent = -1,
            history = state.history + "left ${eventLabel(event)} — buy-in refunded",
        )
    }

    /** 50/30/20 payout split for the top three places (Holdem!Constants). */
    fun payouts(index: Int): List<Int> {
        val event = EVENTS.getOrNull(index) ?: return emptyList()
        val pool = event.buyIn * event.players
        if (pool <= 0) return listOf(0, 0, 0)
        return listOf(pool * 50 / 100, pool * 30 / 100, pool * 20 / 100)
    }

    fun settle(state: PokerTournamentState, place: Int): PokerTournamentState {
        val index = state.activeEvent
        if (index < 0) return state
        val event = state.events[index]
        val payout = payouts(index).getOrElse(place - 1) { 0 }
        return state.copy(
            bankroll = state.bankroll + payout,
            activeEvent = -1,
            completed = state.completed + index,
            history = state.history + "${eventLabel(event)} — place $place, paid $payout",
        )
    }

    private fun eventLabel(event: PokerTournamentEvent): String =
        "event ${event.index + 1} (buy-in ${event.buyIn}, ${event.players} players)"
}
