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

enum class HeartsSeat { SOUTH, WEST, NORTH, EAST }

data class HeartsState(
    val hands: Map<HeartsSeat, List<SolCard>>,
    val trick: List<Pair<HeartsSeat, SolCard>>,
    val ledSuit: SolSuit?,
    val passDirection: Int,    // -1 = left, 0 = none, 1 = right
    val currentPlayer: HeartsSeat,
    val scores: Map<HeartsSeat, Int>,
    val heartsBroken: Boolean,
    val done: Boolean,
    val lastTrick: List<Pair<HeartsSeat, SolCard>>?,
    val pendingPassFrom: HeartsSeat?,
)

object HeartsEngine {
    fun newGame(rng: Random = Random.Default): HeartsState {
        val deck = (SolSuit.values()).flatMap { s -> (2..14).map { r -> SolCard(s, r, true) } }.shuffled(rng)
        val hands = mutableMapOf<HeartsSeat, MutableList<SolCard>>()
        heartsSeats.forEach { hands[it] = mutableListOf() }
        deck.forEachIndexed { i, c -> hands[heartsSeats[i % 4]]!!.add(c) }
        hands.forEach { it.value.sortBy { c -> c.suit.ordinal * 100 + c.rank } }
        return HeartsState(
            hands = hands,
            trick = emptyList(),
            ledSuit = null,
            passDirection = 1,
            currentPlayer = HeartsSeat.WEST,
            scores = heartsSeats.associateWith { 0 },
            heartsBroken = false,
            done = false,
            lastTrick = null,
            pendingPassFrom = HeartsSeat.SOUTH,
        )
    }

    val heartsSeats: List<HeartsSeat> = listOf(HeartsSeat.SOUTH, HeartsSeat.WEST, HeartsSeat.NORTH, HeartsSeat.EAST)

    private fun next(s: HeartsSeat): HeartsSeat = when (s) {
        HeartsSeat.SOUTH -> HeartsSeat.WEST
        HeartsSeat.WEST -> HeartsSeat.NORTH
        HeartsSeat.NORTH -> HeartsSeat.EAST
        HeartsSeat.EAST -> HeartsSeat.SOUTH
    }

    /** Cards the player may play. Must follow suit; if leading and hearts not broken, only non-hearts (unless all hearts). */
    fun legalPlays(state: HeartsState, seat: HeartsSeat): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (state.trick.isEmpty()) {
            // Leading: avoid hearts if hearts not broken, unless forced.
            if (state.heartsBroken) return hand
            val nonHearts = hand.filter { it.suit != SolSuit.HEART }
            return if (nonHearts.isNotEmpty()) nonHearts else hand
        }
        val followSuit = hand.filter { it.suit == state.ledSuit }
        return if (followSuit.isNotEmpty()) followSuit else hand
    }

    fun play(state: HeartsState, card: SolCard): HeartsState {
        require(card in legalPlays(state, state.currentPlayer))
        val newHands = state.hands.toMutableMap()
        val hand = newHands[state.currentPlayer]!!.toMutableList()
        hand.remove(card)
        newHands[state.currentPlayer] = hand
        val newTrick = state.trick + (state.currentPlayer to card)
        val ledSuit = state.ledSuit ?: card.suit
        val broken = state.heartsBroken || card.suit == SolSuit.HEART
        if (newTrick.size < 4) {
            return state.copy(hands = newHands, trick = newTrick, ledSuit = ledSuit, currentPlayer = next(state.currentPlayer), heartsBroken = broken)
        }
        // Trick complete — score it: highest card of ledSuit wins; queen of spades = -13.
        val winner = newTrick.maxBy { p -> if (p.second.suit == ledSuit) p.second.rank else 0 }
        val trickPoints = newTrick.sumOf { (_, c) ->
            when (c.suit) {
                SolSuit.HEART -> 1
                SolSuit.SPADE -> if (c.rank == 12) 13 else 0
                else -> 0
            }
        }
        val newScores = state.scores.toMutableMap()
        newScores[winner.first] = (newScores[winner.first] ?: 0) + trickPoints
        val nextPlayer = winner.first
        val allHandsEmpty = heartsSeats.all { newHands[it]!!.isEmpty() }
        return state.copy(
            hands = newHands,
            trick = emptyList(),
            ledSuit = null,
            currentPlayer = nextPlayer,
            heartsBroken = broken,
            scores = newScores,
            lastTrick = newTrick,
            done = allHandsEmpty,
            pendingPassFrom = if (allHandsEmpty) null else null,
        )
    }

    /** On deal: each seat passes 3 cards to the direction indicated. */
    fun passCards(state: HeartsState, fromSeat: HeartsSeat, cards: List<SolCard>): HeartsState {
        require(cards.size == 3)
        require(cards.all { it in state.hands[fromSeat]!! })
        val toSeat = when (state.passDirection) {
            1 -> when (fromSeat) { HeartsSeat.SOUTH -> HeartsSeat.WEST; HeartsSeat.WEST -> HeartsSeat.NORTH; HeartsSeat.NORTH -> HeartsSeat.EAST; HeartsSeat.EAST -> HeartsSeat.SOUTH }
            -1 -> when (fromSeat) { HeartsSeat.SOUTH -> HeartsSeat.EAST; HeartsSeat.EAST -> HeartsSeat.NORTH; HeartsSeat.NORTH -> HeartsSeat.WEST; HeartsSeat.WEST -> HeartsSeat.SOUTH }
            else -> fromSeat
        }
        val newHands = state.hands.toMutableMap()
        newHands[fromSeat] = (newHands[fromSeat]!! - cards.toSet()).toMutableList()
        newHands[toSeat] = (newHands[toSeat]!! + cards).sortedBy { it.suit.ordinal * 100 + it.rank }.toMutableList()
        return state.copy(hands = newHands)
    }

    /**
     * Complete the passing phase after the human picked their three cards:
     * the other three seats pass three random cards and the phase closes
     * (the old code left `pendingPassFrom` set forever, blocking the round).
     */
    fun completePassing(state: HeartsState, rng: Random): HeartsState {
        var s = state
        for (seat in heartsSeats) {
            if (seat == HeartsSeat.SOUTH) continue
            val picks = (s.hands[seat] ?: continue).shuffled(rng).take(3)
            s = passCards(s, seat, picks)
        }
        return s.copy(pendingPassFrom = null)
    }

    /** A trivial AI: play a random legal card, preferring to dump hearts and the queen of spades. */
    fun aiPlay(state: HeartsState, rng: Random): HeartsState {
        val legal = legalPlays(state, state.currentPlayer)
        if (legal.isEmpty()) return state
        val sorted = legal.sortedBy { c -> if (c.suit == SolSuit.HEART) 0 else if (c.suit == SolSuit.SPADE && c.rank == 12) 0 else 1 }
        val pick = if (rng.nextInt(5) == 0) sorted.first() else legal.random(rng)
        return play(state, pick)
    }
}

/* ============================================================ */
/*                              Spades                              */
/* ============================================================ */
