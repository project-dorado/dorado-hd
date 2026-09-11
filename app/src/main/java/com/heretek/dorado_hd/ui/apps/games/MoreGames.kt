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

enum class CheckersColor { RED, BLACK } // RED is the human / bottom.
sealed class CheckersPiece(val color: CheckersColor, val king: Boolean)
class RedMan : CheckersPiece(CheckersColor.RED, false)
class RedKing : CheckersPiece(CheckersColor.RED, true)
class BlackMan : CheckersPiece(CheckersColor.BLACK, false)
class BlackKing : CheckersPiece(CheckersColor.BLACK, true)

fun checkersPiece(color: CheckersColor, king: Boolean): CheckersPiece = when {
    color == CheckersColor.RED && !king -> RedMan()
    color == CheckersColor.RED && king -> RedKing()
    color == CheckersColor.BLACK && !king -> BlackMan()
    else -> BlackKing()
}

data class CheckersState(
    val board: Array<Array<CheckersPiece?>>, // 8x8; only dark squares (r+c odd) used.
    val turn: CheckersColor,
    val captureChain: List<Pair<Int, Int>>?, // non-null when a multi-jump is in progress
    val winner: CheckersColor?,
)

object CheckersEngine {
    fun newGame(): CheckersState {
        val b = Array(8) { arrayOfNulls<CheckersPiece?>(8) }
        for (r in 0 until 3) for (c in 0 until 8) if ((r + c) % 2 == 1) b[r][c] = checkersPiece(CheckersColor.RED, false)
        for (r in 5 until 8) for (c in 0 until 8) if ((r + c) % 2 == 1) b[r][c] = checkersPiece(CheckersColor.BLACK, false)
        return CheckersState(b, CheckersColor.RED, null, null)
    }
    private val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    private fun onBoard(r: Int, c: Int) = r in 0 until 8 && c in 0 until 8

    private fun forwardDirs(color: CheckersColor) = when (color) {
        CheckersColor.RED -> listOf(1 to -1, 1 to 1) // RED at top moves downward (r increases)
        CheckersColor.BLACK -> listOf(-1 to -1, -1 to 1)
    }

    fun legalMoves(state: CheckersState, color: CheckersColor): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        // If currently in a chain, restrict to the chain's last position.
        val lastChain: Pair<Int, Int>? = state.captureChain?.lastOrNull()
        val fromFilter: (Int, Int) -> Boolean = { r, c -> lastChain == null || (r == lastChain.first && c == lastChain.second) }
        val captures = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        val moves = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        for (r in 0 until 8) for (c in 0 until 8) {
            if (!fromFilter(r, c)) continue
            val p = state.board[r][c] ?: continue
            if (p.color != color) continue
            val allowedDirs = if (p.king) dirs else forwardDirs(color)
            for ((dr, dc) in allowedDirs) {
                val nr = r + dr; val nc = c + dc
                if (!onBoard(nr, nc)) continue
                val target = state.board[nr][nc]
                if (target == null) {
                    if (state.captureChain == null) moves.add(r to c to (nr to nc))
                } else if (target.color != color) {
                    val jr = nr + dr; val jc = nc + dc
                    if (onBoard(jr, jc) && state.board[jr][jc] == null) {
                        captures.add(r to c to (jr to jc))
                    }
                }
            }
        }
        return if (captures.isNotEmpty()) captures else moves
    }

    fun apply(state: CheckersState, from: Pair<Int, Int>, to: Pair<Int, Int>): CheckersState {
        val newBoard: Array<Array<CheckersPiece?>> = Array(state.board.size) { state.board[it].copyOf() }
        val piece = newBoard[from.first][from.second]!!
        newBoard[from.first][from.second] = null
        newBoard[to.first][to.second] = checkersPiece(
            color = piece.color,
            king = piece.king ||
                (piece.color == CheckersColor.RED && to.first == 7) ||
                (piece.color == CheckersColor.BLACK && to.first == 0),
        )
        // Capture?
        val dr = to.first - from.first; val dc = to.second - from.second
        if (abs(dr) == 2 && abs(dc) == 2) {
            newBoard[from.first + dr / 2][from.second + dc / 2] = null
        }
        // Continue chain if more captures available from 'to'.
        val newChain = mutableListOf<Pair<Int, Int>>().apply { add(from); add(to) }
        if (abs(dr) == 2) {
            val more = legalMoves(state.copy(board = newBoard, captureChain = newChain), piece.color)
                .filter { it.first == to }
            if (more.isNotEmpty()) {
                return CheckersState(newBoard, state.turn, newChain, state.winner)
            }
        }
        val nextColor = if (state.turn == CheckersColor.RED) CheckersColor.BLACK else CheckersColor.RED
        val opponentMoves = legalMoves(CheckersState(newBoard, nextColor, null, null), nextColor)
        // If the side to move has no legal move, the side that just moved wins
        // (the old comparison state.turn == nextColor was always false).
        val winner = if (opponentMoves.isEmpty()) state.turn else null
        return CheckersState(newBoard, nextColor, null, winner)
    }

    /** Greedy AI: pick the longest capture, else the first non-capture move. */
    fun aiMove(state: CheckersState): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val moves = legalMoves(state, state.turn)
        if (moves.isEmpty()) return null
        return moves.maxBy { m -> abs(m.second.first - m.first.first) }
    }
}

/* ============================================================ */
/*                              Chess                              */
/* ============================================================ */

enum class ChessPieceType { P, N, B, R, Q, K }
enum class ChessColor { WHITE, BLACK }

data class ChessPiece(val type: ChessPieceType, val color: ChessColor)
data class ChessMove(
    val fromR: Int, val fromC: Int,
    val toR: Int, val toC: Int,
    val promotion: ChessPieceType? = null,
    val enPassant: Boolean = false,
    val castleKingSide: Boolean = false,
    val castleQueenSide: Boolean = false,
)
data class ChessState(
    val board: Array<Array<ChessPiece?>>,
    val turn: ChessColor,
    val castling: Int,            // bit 0: WK, 1: WQ, 2: BK, 3: BQ
    val enPassant: Pair<Int, Int>?, // square behind pawn that moved 2 last turn
    val halfmove: Int,
    val fullmove: Int,
    val status: String, // "" or "checkmate white" / "stalemate" / "check black"
)

object ChessEngine {
    fun newGame(): ChessState {
        val b = Array(8) { arrayOfNulls<ChessPiece?>(8) }
        val back = listOf(ChessPieceType.R, ChessPieceType.N, ChessPieceType.B, ChessPieceType.K, ChessPieceType.Q, ChessPieceType.B, ChessPieceType.N, ChessPieceType.R)
        for (c in 0 until 8) { b[0][c] = ChessPiece(back[c], ChessColor.WHITE); b[1][c] = ChessPiece(ChessPieceType.P, ChessColor.WHITE); b[6][c] = ChessPiece(ChessPieceType.P, ChessColor.BLACK); b[7][c] = ChessPiece(back[c], ChessColor.BLACK) }
        return ChessState(b, ChessColor.WHITE, 0b1111, null, 0, 1, "")
    }

    fun at(state: ChessState, r: Int, c: Int): ChessPiece? = if (r in 0..7 && c in 0..7) state.board[r][c] else null
    fun color(state: ChessState, r: Int, c: Int): ChessColor? = at(state, r, c)?.color
    fun inBounds(r: Int, c: Int) = r in 0..7 && c in 0..7
    fun opposite(c: ChessColor) = if (c == ChessColor.WHITE) ChessColor.BLACK else ChessColor.WHITE

    private fun rayTargets(state: ChessState, color: ChessColor, dirs: List<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val out = mutableSetOf<Pair<Int, Int>>()
        // Scan all squares for sliding attacks.
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color) continue
            val pdirs = when (p.type) {
                ChessPieceType.B -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                ChessPieceType.R -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                ChessPieceType.Q -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                else -> emptyList()
            }
            for ((dr, dc) in pdirs) {
                var rr = r + dr; var cc = c + dc
                while (inBounds(rr, cc)) {
                    out.add(rr to cc)
                    if (state.board[rr][cc] != null) break
                    rr += dr; cc += dc
                }
            }
        }
        // Knights
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color || p.type != ChessPieceType.N) continue
            for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) {
                val rr = r + dr; val cc = c + dc
                if (inBounds(rr, cc)) out.add(rr to cc)
            }
        }
        // Pawns
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color || p.type != ChessPieceType.P) continue
            val dir = if (color == ChessColor.WHITE) 1 else -1
            for (dc in listOf(-1, 1)) {
                val rr = r + dir; val cc = c + dc
                if (inBounds(rr, cc)) out.add(rr to cc)
            }
        }
        // King
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color || p.type != ChessPieceType.K) continue
            for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)) {
                val rr = r + dr; val cc = c + dc
                if (inBounds(rr, cc)) out.add(rr to cc)
            }
        }
        return out
    }

    fun isInCheck(state: ChessState, color: ChessColor): Boolean {
        val kingR = (0..7).firstOrNull { r -> (0..7).any { c -> state.board[r][c]?.type == ChessPieceType.K && state.board[r][c]?.color == color } } ?: return false
        val kingC = (0..7).first { c -> state.board[kingR][c]?.type == ChessPieceType.K && state.board[kingR][c]?.color == color }
        return (kingR to kingC) in rayTargets(state, opposite(color), emptyList())
    }

    fun legalMoves(state: ChessState): List<ChessMove> {
        if (state.status.isNotEmpty()) return emptyList()
        val moves = pseudoLegal(state, state.turn)
        return moves.filter { mv ->
            val next = apply(state, mv, skipCheck = true)
            !isInCheck(next, state.turn)
        }
    }

    fun pseudoLegal(state: ChessState, color: ChessColor): List<ChessMove> {
        val out = mutableListOf<ChessMove>()
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color) continue
            when (p.type) {
                ChessPieceType.P -> {
                    val dir = if (color == ChessColor.WHITE) 1 else -1
                    val startRow = if (color == ChessColor.WHITE) 1 else 6
                    val promoRow = if (color == ChessColor.WHITE) 7 else 0
                    val fwdR = r + dir
                    if (inBounds(fwdR, c) && state.board[fwdR][c] == null) {
                        if (fwdR == promoRow) for (t in listOf(ChessPieceType.Q, ChessPieceType.R, ChessPieceType.B, ChessPieceType.N)) out.add(ChessMove(r, c, fwdR, c, t))
                        else out.add(ChessMove(r, c, fwdR, c))
                    }
                    if (r == startRow && fwdR in 0..7 && state.board[fwdR][c] == null) {
                        val fwdR2 = fwdR + dir
                        if (inBounds(fwdR2, c) && state.board[fwdR2][c] == null) out.add(ChessMove(r, c, fwdR2, c))
                    }
                    for (dc in listOf(-1, 1)) {
                        val cc = c + dc
                        if (!inBounds(fwdR, cc)) continue
                        val target = state.board[fwdR][cc]
                        if (target != null && target.color != color) {
                            if (fwdR == promoRow) for (t in listOf(ChessPieceType.Q, ChessPieceType.R, ChessPieceType.B, ChessPieceType.N)) out.add(ChessMove(r, c, fwdR, cc, t))
                            else out.add(ChessMove(r, c, fwdR, cc))
                        } else if (state.enPassant == fwdR to cc) {
                            out.add(ChessMove(r, c, fwdR, cc, enPassant = true))
                        }
                    }
                }
                ChessPieceType.N -> for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) {
                    val rr = r + dr; val cc = c + dc
                    if (inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != color)) out.add(ChessMove(r, c, rr, cc))
                }
                ChessPieceType.B, ChessPieceType.R, ChessPieceType.Q -> {
                    val dirs = when (p.type) {
                        ChessPieceType.B -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                        ChessPieceType.R -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                        else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    }
                    for ((dr, dc) in dirs) {
                        var rr = r + dr; var cc = c + dc
                        while (inBounds(rr, cc)) {
                            val t = state.board[rr][cc]
                            if (t == null) out.add(ChessMove(r, c, rr, cc))
                            else { if (t.color != color) out.add(ChessMove(r, c, rr, cc)); break }
                            rr += dr; cc += dc
                        }
                    }
                }
                ChessPieceType.K -> {
                    for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)) {
                        val rr = r + dr; val cc = c + dc
                        if (inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != color)) out.add(ChessMove(r, c, rr, cc))
                    }
                    // Castling
                    val row = if (color == ChessColor.WHITE) 0 else 7
                    if (r == row && c == 4) {
                        val ksRight = state.castling and (if (color == ChessColor.WHITE) 0b0001 else 0b0100) != 0
                        val qsRight = state.castling and (if (color == ChessColor.WHITE) 0b0010 else 0b1000) != 0
                        if (ksRight && state.board[row][5] == null && state.board[row][6] == null) {
                            if (!isSquareAttacked(state, row, 4, opposite(color)) && !isSquareAttacked(state, row, 5, opposite(color)) && !isSquareAttacked(state, row, 6, opposite(color))) {
                                out.add(ChessMove(row, 4, row, 6, castleKingSide = true))
                            }
                        }
                        if (qsRight && state.board[row][3] == null && state.board[row][2] == null && state.board[row][1] == null) {
                            if (!isSquareAttacked(state, row, 4, opposite(color)) && !isSquareAttacked(state, row, 3, opposite(color)) && !isSquareAttacked(state, row, 2, opposite(color))) {
                                out.add(ChessMove(row, 4, row, 2, castleQueenSide = true))
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun isSquareAttacked(state: ChessState, r: Int, c: Int, byColor: ChessColor): Boolean {
        // Build a temporary state where (r,c) is occupied by a dummy of `opposite(byColor)` to test attacks.
        val probe = ChessState(
            board = Array(state.board.size) { state.board[it].copyOf() }.also { it[r][c] = ChessPiece(ChessPieceType.P, byColor) },
            turn = byColor, castling = 0, enPassant = null, halfmove = 0, fullmove = 1, status = "",
        )
        return rayTargets(probe, byColor, emptyList()).contains(r to c)
    }

    fun apply(state: ChessState, move: ChessMove, skipCheck: Boolean = false): ChessState {
        val newBoard: Array<Array<ChessPiece?>> = Array(state.board.size) { state.board[it].copyOf() }
        val piece = newBoard[move.fromR][move.fromC]!!
        newBoard[move.fromR][move.fromC] = null
        val captured = newBoard[move.toR][move.toC]
        if (move.enPassant) {
            val dir = if (piece.color == ChessColor.WHITE) -1 else 1
            newBoard[move.toR + dir][move.toC] = null
        }
        var placed = piece
        if (move.promotion != null) placed = piece.copy(type = move.promotion)
        if (move.castleKingSide) {
            newBoard[move.toR][move.toC] = placed
            val rook = newBoard[move.toR][7]
            if (rook != null) {
                newBoard[move.toR][5] = rook // rook h-file -> f-file
                newBoard[move.toR][7] = null
            }
        } else if (move.castleQueenSide) {
            newBoard[move.toR][move.toC] = placed
            val rook = newBoard[move.toR][0]
            if (rook != null) {
                newBoard[move.toR][3] = rook // rook a-file -> d-file
                newBoard[move.toR][0] = null
            }
        } else {
            newBoard[move.toR][move.toC] = placed
        }
        var newCastling = when {
            piece.type == ChessPieceType.K && piece.color == ChessColor.WHITE -> state.castling and 0b1100
            piece.type == ChessPieceType.K && piece.color == ChessColor.BLACK -> state.castling and 0b0011
            // Bit 0 = WK (h1), bit 1 = WQ (a1), bit 2 = BK (h8), bit 3 = BQ (a8).
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 0 -> state.castling and 0b1101
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 7 -> state.castling and 0b1110
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 0 -> state.castling and 0b0111
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 7 -> state.castling and 0b1011
            else -> state.castling
        }
        // Capturing a rook on its home square must also revoke the right:
        // otherwise a later castle reached `!!` on a missing rook and crashed.
        if (move.toR == 0) {
            if (move.toC == 0) newCastling = newCastling and 0b1101 // a1 → WQ
            if (move.toC == 7) newCastling = newCastling and 0b1110 // h1 → WK
        }
        if (move.toR == 7) {
            if (move.toC == 0) newCastling = newCastling and 0b0111 // a8 → BQ
            if (move.toC == 7) newCastling = newCastling and 0b1011 // h8 → BK
        }
        val newEP = if (piece.type == ChessPieceType.P && abs(move.toR - move.fromR) == 2) {
            (move.fromR + move.toR) / 2 to move.fromC
        } else null
        val nextTurn = opposite(state.turn)
        val newStatus = if (!skipCheck) {
            val nextMoves = legalMoves(ChessState(newBoard, nextTurn, newCastling, newEP, state.halfmove + 1, state.fullmove + (if (state.turn == ChessColor.BLACK) 1 else 0), ""))
            when {
                isInCheck(ChessState(newBoard, nextTurn, newCastling, newEP, 0, 0, ""), nextTurn) && nextMoves.isEmpty() -> "checkmate ${if (nextTurn == ChessColor.WHITE) "white" else "black"}"
                !isInCheck(ChessState(newBoard, nextTurn, newCastling, newEP, 0, 0, ""), nextTurn) && nextMoves.isEmpty() -> "stalemate"
                isInCheck(ChessState(newBoard, nextTurn, newCastling, newEP, 0, 0, ""), nextTurn) -> "check ${if (nextTurn == ChessColor.WHITE) "white" else "black"}"
                else -> ""
            }
        } else ""
        return ChessState(newBoard, nextTurn, newCastling, newEP, state.halfmove + 1, state.fullmove + (if (state.turn == ChessColor.BLACK) 1 else 0), newStatus)
    }

    fun isCheckmate(state: ChessState) = state.status.startsWith("checkmate")
    fun isStalemate(state: ChessState) = state.status == "stalemate"

    private val pieceValue = mapOf(
        ChessPieceType.P to 100, ChessPieceType.N to 320, ChessPieceType.B to 330,
        ChessPieceType.R to 500, ChessPieceType.Q to 900, ChessPieceType.K to 0,
    )
    fun evaluate(state: ChessState): Int {
        var score = 0
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            val v = pieceValue[p.type]!!
            score += if (p.color == ChessColor.WHITE) v else -v
        }
        return if (state.turn == ChessColor.WHITE) score else -score
    }

    /** Alpha-beta to a fixed depth. */
    fun bestMove(state: ChessState, depth: Int = 3): ChessMove? {
        if (state.status.isNotEmpty()) return null
        val moves = legalMoves(state)
        if (moves.isEmpty()) return null
        var best: ChessMove? = null
        var bestScore = -99999
        val alpha = -99999
        val beta = 99999
        for (mv in moves) {
            val next = apply(state, mv, skipCheck = true)
            val s = -negamax(next, depth - 1, -beta, -alpha)
            if (s > bestScore) { bestScore = s; best = mv }
        }
        return best
    }
    private fun negamax(state: ChessState, depth: Int, alpha: Int, beta: Int): Int {
        if (state.status.startsWith("checkmate")) return -100000 + (5 - depth)
        if (state.status == "stalemate") return 0
        if (depth == 0) return evaluate(state)
        val moves = legalMoves(state)
        if (moves.isEmpty()) return evaluate(state)
        var a = alpha
        var best = -99999
        for (mv in moves) {
            val next = apply(state, mv, skipCheck = true)
            val s = -negamax(next, depth - 1, -beta, -a)
            if (s > best) best = s
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }
}

/* ============================================================ */
/*                          Texas Hold 'Em                         */
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
/*                              UI                                 */
/* ============================================================ */

@Composable
fun HeartsApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(HeartsEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val seat = HeartsSeat.SOUTH

    fun humanAction(s: HeartsState, action: HeartsState.() -> HeartsState) {
        var cur = action(s)
        while (cur.currentPlayer != seat && !cur.done) {
            cur = HeartsEngine.aiPlay(cur, Random.Default)
        }
        state = cur
    }

    val passCards = state.pendingPassFrom == seat
    DetailScaffold(title = "hearts") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "scores: ${state.scores.entries.joinToString { "${it.key.name.lowercase()}=${it.value}" }}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(4.dp))
            // Opponents are summarised by hand size; 13 cards × 3 seats cannot
            // fit a 448dp row.
            BasicText(
                text = HeartsEngine.heartsSeats.filter { it != seat }
                    .joinToString("   ") { "${it.name.lowercase()} ${state.hands[it]?.size ?: 0}" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
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
            PlayerHand(
                hand = state.hands[seat]!!,
                onCard = { c -> if (!state.done && state.currentPlayer == seat && !passCards) humanAction(state) { HeartsEngine.play(state, c) } },
                align = Alignment.CenterHorizontally,
                label = "you",
                interactive = state.currentPlayer == seat && !passCards,
            )
            if (passCards) {
                Spacer(Modifier.height(4.dp))
                PassPicker(hand = state.hands[seat]!!) { cards ->
                    state = HeartsEngine.completePassing(
                        HeartsEngine.passCards(state, seat, cards),
                        Random.Default,
                    )
                }
            } else if (state.done) {
                Spacer(Modifier.height(4.dp))
                BasicText(text = "round complete", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            }
        }
    }
    if (state.done && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            scope.launch { graph.games.record("hearts", state.scores.values.sum(), null) }
        }
    }
}

@Composable
private fun PlayerHand(hand: List<SolCard>, onCard: (SolCard) -> Unit = {}, align: Alignment.Horizontal, label: String, interactive: Boolean = true, faceDown: Boolean = false) {
    Column(horizontalAlignment = align) {
        BasicText(text = label, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = LocalDoradoColors.current.textSecondary))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hand.forEach { c ->
                val shown = if (faceDown) c.copy(faceUp = false) else c
                SolCardView(shown, width = 26.dp, height = 34.dp) { if (interactive) onCard(c) }
            }
        }
    }
}

@Composable
private fun PassPicker(hand: List<SolCard>, onConfirm: (List<SolCard>) -> Unit) {
    val picked = remember { mutableStateListOf<SolCard>() }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hand.forEach { c ->
                val selected = c in picked
                Box(modifier = Modifier.pointerInput(c) { detectTapGestures(onTap = { if (selected) picked.remove(c) else if (picked.size < 3) picked.add(c) }) }) {
                    SolCardView(if (selected) c.copy(faceUp = true) else c.copy(faceUp = false), width = 26.dp, height = 34.dp) {}
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        BasicText(text = "pass ${picked.size}/3 — ${if (picked.size == 3) "tap confirm" else "pick three"}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = LocalDoradoColors.current.accent),
            modifier = Modifier.pointerInput(picked.toList()) { detectTapGestures(onTap = { if (picked.size == 3) onConfirm(picked.toList()) }) })
    }
}

@Composable
fun SpadesApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SpadesEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val seat = SpadesSeat.SOUTH

    // The AI seats drive themselves on every state change (the old UI only
    // advanced when the human tapped something, so a fresh deal stalled).
    LaunchedEffect(state) {
        val cur = state
        if (cur.done || cur.currentPlayer == seat) return@LaunchedEffect
        delay(300)
        state = if (!cur.biddingDone) {
            SpadesEngine.bid(cur, (1..5).random())
        } else {
            val legal = SpadesEngine.legalPlays(cur, cur.currentPlayer)
            if (legal.isEmpty()) cur else SpadesEngine.play(cur, legal.random())
        }
    }

    LaunchedEffect(state.done) {
        val cur = state
        if (cur.done && !recorded) {
            recorded = true
            val scored = SpadesEngine.scoreRound(cur)
            state = scored
            scope.launch {
                graph.games.record(
                    "spades",
                    scored.score.first,
                    "S-N ${scored.score.first} · W-E ${scored.score.second}",
                )
            }
        }
    }

    DetailScaffold(title = "spades") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "score S-N ${state.score.first} · W-E ${state.score.second}   bags ${state.teamBags.first}/${state.teamBags.second}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = SpadesSeat.values().joinToString("   ") { "${it.name.take(1).lowercase()}:${state.bids[it] ?: 0}" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            when {
                !state.biddingDone -> {
                    if (state.currentPlayer == seat) {
                        BasicText(text = "your bid:", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..13).forEach { n ->
                                BasicText(text = "$n", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                    modifier = Modifier.pointerInput(n) { detectTapGestures(onTap = { state = SpadesEngine.bid(state, n) }) })
                            }
                        }
                    } else {
                        BasicText(text = "ai bidding…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                    }
                }
                else -> {
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
                            BasicText(text = "round complete", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent), modifier = Modifier.weight(1f))
                            BasicText(text = "new round", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        state = SpadesEngine.newGame().copy(score = state.score, teamBags = state.teamBags)
                                        recorded = false
                                    })
                                })
                        }
                    } else if (state.currentPlayer == seat) {
                        val legal = SpadesEngine.legalPlays(state, seat)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            legal.forEach { c -> SolCardView(c, width = 26.dp, height = 34.dp) { state = SpadesEngine.play(state, c) } }
                        }
                    } else {
                        BasicText(text = "ai playing…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "your hand (${state.hands[seat]?.size ?: 0})",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                state.hands[seat]!!.forEach { c -> SolCardView(c.copy(faceUp = true), width = 26.dp, height = 34.dp) {} }
            }
        }
    }
}

@Composable
fun CheckersApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CheckersEngine.newGame()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val legalMoves = remember(state) { CheckersEngine.legalMoves(state, state.turn) }
    val moveTo = remember(selected, legalMoves) {
        selected?.let { sel -> legalMoves.filter { it.first == sel }.map { it.second } } ?: emptyList()
    }
    fun tryPlay(to: Pair<Int, Int>) {
        val from = selected ?: return
        state = CheckersEngine.apply(state, from, to)
        selected = null
    }

    // AI moves whenever it is black's turn — keyed on the whole state so a
    // multi-jump capture chain continues instead of stalling mid-chain.
    LaunchedEffect(state) {
        if (state.turn == CheckersColor.BLACK && state.winner == null) {
            delay(200)
            val mv = CheckersEngine.aiMove(state)
            if (mv != null) state = CheckersEngine.apply(state, mv.first, mv.second)
        }
    }
    if (state.winner != null && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            scope.launch { graph.games.record("checkers", if (state.winner == CheckersColor.RED) 1 else 0, "winner") }
        }
    }

    DetailScaffold(title = "checkers") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = when {
                    state.winner == CheckersColor.RED -> "you win"
                    state.winner == CheckersColor.BLACK -> "ai wins"
                    state.turn == CheckersColor.RED -> "your move"
                    else -> "ai thinking…"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            val ckSelected = selected
            val ckMoveTo = moveTo
            val ckColors = colors
            val darkSquare = colors.tile
            val darkerSquare = colors.background
            // Draw and hit-test on the SAME canvas: the old transparent overlay
            // was a root-level sibling offset by the header, so taps mis-mapped
            // and covered the back header.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state, selected) {
                        detectTapGestures(onTap = { offset ->
                            val s = min(size.width, size.height)
                            val cell = s / 8f
                            val ox = (size.width - s) / 2f
                            val oy = (size.height - s) / 2f
                            val c = ((offset.x - ox) / cell).toInt().coerceIn(0, 7)
                            val r = ((offset.y - oy) / cell).toInt().coerceIn(0, 7)
                            val p = state.board[r][c]
                            if (p != null && p.color == state.turn) selected = r to c
                            else if (selected != null && (r to c) in moveTo) tryPlay(r to c)
                        })
                    },
            ) {
                val s = min(size.width, size.height)
                val cell = s / 8
                val ox = (size.width - s) / 2
                val oy = (size.height - s) / 2
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 1
                    drawRect(
                        color = if (ckSelected == r to c) ckColors.accent
                        else if (ckMoveTo.contains(r to c)) ckColors.tilePressed
                        else if (light) darkSquare else darkerSquare,
                        topLeft = Offset(ox + c * cell, oy + r * cell),
                        size = Size(cell, cell),
                    )
                }
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    val col = if (p.color == CheckersColor.RED) ckColors.accent else Color.White
                    val radius = cell * if (p.king) 0.42f else 0.36f
                    drawCircle(
                        color = if (p.king) col else col.copy(alpha = 0.85f),
                        center = Offset(ox + c * cell + cell / 2, oy + r * cell + cell / 2),
                        radius = radius,
                    )
                    if (p.king) {
                        drawCircle(
                            color = Color.Black,
                            center = Offset(ox + c * cell + cell / 2, oy + r * cell + cell / 2),
                            radius = cell * 0.18f,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you are red — tap a piece, then a highlighted square", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}

@Composable
fun ChessApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ChessEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val moves = remember(state) { ChessEngine.legalMoves(state) }
    val targets = remember(selected, moves) { selected?.let { sel -> moves.filter { it.fromR == sel.first && it.fromC == sel.second }.map { it.toR to it.toC } } ?: emptyList() }
    // One reusable native paint for piece glyphs (letters, so pawns/knights/
    // kings are distinguishable — the old UI drew identical circles).
    val piecePaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }

    fun click(r: Int, c: Int) {
        if (state.status.isNotEmpty()) return
        if (selected == null) {
            val p = state.board[r][c]
            if (p?.color == state.turn) selected = r to c
        } else {
            val move = moves.firstOrNull { it.fromR == selected!!.first && it.fromC == selected!!.second && it.toR == r && it.toC == c }
            if (move != null) {
                state = ChessEngine.apply(state, move)
                selected = null
            } else {
                val p = state.board[r][c]
                selected = if (p?.color == state.turn) r to c else null
            }
        }
    }

    if (state.turn == ChessColor.BLACK && state.status.isEmpty()) {
        LaunchedEffect(state) {
            delay(80)
            // Search off the main thread; depth 2 keeps the position sane
            // without ANR-length pauses.
            val mv = withContext(Dispatchers.Default) { ChessEngine.bestMove(state, depth = 2) }
            if (mv != null) state = ChessEngine.apply(state, mv)
        }
    }
    val terminal = state.status.startsWith("checkmate") || state.status == "stalemate"
    if (terminal && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            val score = if (state.status.startsWith("checkmate black")) 1 else 0
            scope.launch { graph.games.record("chess", score, state.status) }
        }
    }

    DetailScaffold(title = "chess") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = if (state.status.isNotEmpty()) state.status else if (state.turn == ChessColor.WHITE) "your move" else "ai thinking…",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            val selectedSq = selected
            val targetSquares = targets
            val chColors = colors
            val lightSquare = colors.tile
            val darkSquare = colors.background
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state, selected) {
                        detectTapGestures(onTap = { offset ->
                            val s = min(size.width, size.height)
                            val cell = s / 8f
                            val ox = (size.width - s) / 2f
                            val oy = (size.height - s) / 2f
                            val c = ((offset.x - ox) / cell).toInt().coerceIn(0, 7)
                            val r = ((offset.y - oy) / cell).toInt().coerceIn(0, 7)
                            click(r, c)
                        })
                    },
            ) {
                val s = min(size.width, size.height)
                val cell = s / 8
                val ox = (size.width - s) / 2
                val oy = (size.height - s) / 2
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 0
                    drawRect(
                        color = if (selectedSq == r to c) chColors.accent
                        else if ((r to c) in targetSquares) chColors.tilePressed
                        else if (light) lightSquare else darkSquare,
                        topLeft = Offset(ox + c * cell, oy + r * cell), size = Size(cell, cell),
                    )
                }
                val paint = piecePaint
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    paint.color = if (p.color == ChessColor.WHITE) android.graphics.Color.WHITE else android.graphics.Color.GRAY
                    paint.textSize = cell * 0.62f
                    val cx = ox + c * cell + cell / 2f
                    val cy = oy + r * cell + cell / 2f - (paint.ascent() + paint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(chessGlyph(p.type), cx, cy, paint)
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you are white — tap a piece, then a target", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}

private fun chessGlyph(type: ChessPieceType): String = when (type) {
    ChessPieceType.P -> "P"
    ChessPieceType.N -> "N"
    ChessPieceType.B -> "B"
    ChessPieceType.R -> "R"
    ChessPieceType.Q -> "Q"
    ChessPieceType.K -> "K"
}

@Composable
fun TexasHoldemApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PokerEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current

    LaunchedEffect(state) {
        if (!state.actor && state.phase != PokerPhase.DONE && state.phase != PokerPhase.SHOWDOWN) {
            delay(350)
            state = PokerEngine.applyAction(state, PokerEngine.aiAction(state, Random.Default))
        }
    }
    val terminal = state.phase == PokerPhase.DONE || state.phase == PokerPhase.SHOWDOWN
    if (terminal && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            val s = when (state.winner) {
                "player" -> 1
                "tie" -> 1
                else -> 0
            }
            scope.launch { graph.games.record("texasholdem", s, state.winner) }
        }
    }

    DetailScaffold(title = "texas hold 'em") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "pot ${state.pot} • you ${state.playerStack} • ai ${state.aiStack} • ${state.phase.name.lowercase()}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(text = "ai", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // AI hole cards stay face-down until showdown; ranks are no
                // longer truncated to unreadable two-char strings.
                val show = state.phase == PokerPhase.SHOWDOWN || state.phase == PokerPhase.DONE
                state.aiHole.forEach { c -> SolCardView(if (show) c else c.copy(faceUp = false), width = 26.dp, height = 34.dp) {} }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                state.community.forEach { SolCardView(it, width = 26.dp, height = 34.dp) {} }
                if (state.community.isEmpty()) {
                    BasicText(text = "community", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                state.playerHole.forEach { SolCardView(it, width = 26.dp, height = 34.dp) {} }
            }
            Spacer(Modifier.height(6.dp))
            if (terminal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = when (state.winner) {
                            "tie" -> "tie — split pot"
                            "player" -> "you win"
                            else -> "ai wins"
                        },
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(text = "new hand", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { state = PokerEngine.newGame(); recorded = false }) })
                }
            } else if (state.actor) {
                val legal = PokerEngine.legalActions(state)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    legal.forEach { a ->
                        BasicText(text = a.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                            modifier = Modifier.pointerInput(a) { detectTapGestures(onTap = { state = PokerEngine.applyAction(state, a) }) })
                    }
                }
            } else {
                BasicText(text = "ai thinking…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            }
        }
    }
}
