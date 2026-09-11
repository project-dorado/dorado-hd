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

enum class SpadesSeat { SOUTH, WEST, NORTH, EAST }

data class SpadesState(
    val hands: Map<SpadesSeat, List<SolCard>>,
    val bids: Map<SpadesSeat, Int>,
    val nilBid: Map<SpadesSeat, Boolean>,
    val blindNil: Map<SpadesSeat, Boolean>,
    val trick: List<Pair<SpadesSeat, SolCard>>,
    val tricksTaken: Map<SpadesSeat, List<SolCard>>,
    val currentPlayer: SpadesSeat,
    val bidder: SpadesSeat,
    val score: Pair<Int, Int>, // S+N, W+E
    val bagCount: Int,
    val teamBags: Pair<Int, Int>, // S+N, W+E
    val spadesBroken: Boolean,
    val biddingDone: Boolean,
    val done: Boolean,
)

object SpadesEngine {
    private fun next(s: SpadesSeat): SpadesSeat = when (s) {
        SpadesSeat.SOUTH -> SpadesSeat.WEST
        SpadesSeat.WEST -> SpadesSeat.NORTH
        SpadesSeat.NORTH -> SpadesSeat.EAST
        SpadesSeat.EAST -> SpadesSeat.SOUTH
    }
    fun partner(s: SpadesSeat) = if (s == SpadesSeat.SOUTH || s == SpadesSeat.NORTH) s to (if (s == SpadesSeat.NORTH) SpadesSeat.SOUTH else SpadesSeat.NORTH) else s to (if (s == SpadesSeat.WEST) SpadesSeat.EAST else SpadesSeat.WEST)

    fun newGame(rng: Random = Random.Default): SpadesState {
        val deck = (SolSuit.values()).flatMap { s -> (2..14).map { r -> SolCard(s, r, true) } }.shuffled(rng)
        val hands = mutableMapOf<SpadesSeat, MutableList<SolCard>>()
        SpadesSeat.values().forEach { hands[it] = mutableListOf() }
        deck.forEachIndexed { i, c -> hands[SpadesSeat.values()[i % 4]]!!.add(c) }
        return SpadesState(
            hands = hands,
            bids = SpadesSeat.values().associateWith { 0 },
            nilBid = SpadesSeat.values().associateWith { false },
            blindNil = SpadesSeat.values().associateWith { false },
            trick = emptyList(),
            tricksTaken = SpadesSeat.values().associateWith { emptyList<SolCard>() },
            currentPlayer = SpadesSeat.WEST,
            bidder = SpadesSeat.WEST,
            score = 0 to 0,
            bagCount = 0,
            teamBags = 0 to 0,
            spadesBroken = false,
            biddingDone = false,
            done = false,
        )
    }

    /** Cards the player may play: follow suit if possible; otherwise anything
     *  (spades are simply trump and may be led once broken). */
    fun legalPlays(state: SpadesState, seat: SpadesSeat): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (state.trick.isEmpty()) {
            if (state.spadesBroken) return hand
            val nonSpades = hand.filter { it.suit != SolSuit.SPADE }
            return if (nonSpades.isNotEmpty()) nonSpades else hand
        }
        val ledSuit = state.trick.first().second.suit
        val follow = hand.filter { it.suit == ledSuit }
        return if (follow.isNotEmpty()) follow else hand
    }

    fun bid(state: SpadesState, n: Int, nil: Boolean = false, blind: Boolean = false): SpadesState {
        val newBids = state.bids.toMutableMap(); newBids[state.currentPlayer] = n
        val newNil = state.nilBid.toMutableMap(); newNil[state.currentPlayer] = nil || blind
        val newBlind = state.blindNil.toMutableMap(); newBlind[state.currentPlayer] = blind
        val nextPlayer = next(state.currentPlayer)
        val biddingDone = nextPlayer == state.bidder
        return state.copy(
            bids = newBids,
            nilBid = newNil,
            blindNil = newBlind,
            currentPlayer = if (biddingDone) state.bidder else nextPlayer,
            biddingDone = biddingDone,
        )
    }

    fun play(state: SpadesState, card: SolCard): SpadesState {
        require(card in legalPlays(state, state.currentPlayer))
        val spadesBroken = state.spadesBroken || card.suit == SolSuit.SPADE
        val newHands = state.hands.toMutableMap()
        val hand = newHands[state.currentPlayer]!!.toMutableList()
        hand.remove(card)
        newHands[state.currentPlayer] = hand
        val newTrick = state.trick + (state.currentPlayer to card)
        if (newTrick.size < 4) return state.copy(hands = newHands, trick = newTrick, currentPlayer = next(state.currentPlayer), spadesBroken = spadesBroken)
        // Trick done: highest spade wins, otherwise highest of the led suit.
        val ledSuit = newTrick.first().second.suit
        val spades = newTrick.filter { it.second.suit == SolSuit.SPADE }
        val winner = if (spades.isNotEmpty()) spades.maxBy { it.second.rank }
        else newTrick.filter { it.second.suit == ledSuit }.maxBy { it.second.rank }
        val newTricks = state.tricksTaken.toMutableMap()
        newTricks[winner.first] = (newTricks[winner.first]!!) + newTrick.map { it.second }
        val allEmpty = SpadesSeat.values().all { newHands[it]!!.isEmpty() }
        if (!allEmpty) return state.copy(hands = newHands, trick = emptyList(), currentPlayer = winner.first, tricksTaken = newTricks, spadesBroken = spadesBroken)
        return state.copy(hands = newHands, trick = emptyList(), tricksTaken = newTricks, done = true, currentPlayer = winner.first, spadesBroken = spadesBroken)
    }

    /** Round scoring: bid*10 per made bid, -10 per undertrick, nil ±100, and
     *  a -100 team penalty per completed 10 bags. Returns a scored copy. */
    fun scoreRound(state: SpadesState): SpadesState {
        var s1 = state.score.first
        var s2 = state.score.second
        var (bags1, bags2) = state.teamBags
        for (seat in SpadesSeat.values()) {
            val bid = state.bids[seat] ?: 0
            val tricks = state.tricksTaken[seat]?.size ?: 0
            val onTeamOne = seat == SpadesSeat.SOUTH || seat == SpadesSeat.NORTH
            var points: Int
            if (state.nilBid[seat] == true) {
                points = if (tricks == 0) if (state.blindNil[seat] == true) 200 else 100 else -100
            } else {
                val delta = tricks - bid
                points = bid * 10
                if (onTeamOne) {
                    if (delta >= 0) bags1 += delta else s1 -= (-delta) * 10
                } else {
                    if (delta >= 0) bags2 += delta else s2 -= (-delta) * 10
                }
            }
            if (onTeamOne) s1 += points else s2 += points
        }
        // Bag penalties.
        while (bags1 >= 10) { s1 -= 100; bags1 -= 10 }
        while (bags2 >= 10) { s2 -= 100; bags2 -= 10 }
        return state.copy(score = s1 to s2, teamBags = bags1 to bags2, bagCount = bags1 + bags2)
    }
}

/* ============================================================ */
/*                             Checkers                             */
/* ============================================================ */
