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
