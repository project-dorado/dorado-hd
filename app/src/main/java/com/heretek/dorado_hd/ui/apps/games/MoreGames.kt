package com.heretek.dorado_hd.ui.apps.games

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            biddingDone = false,
            done = false,
        )
    }

    /** Cards the player may play. Must follow suit; if void in ledSuit and spades broken, any card. */
    fun legalPlays(state: SpadesState, seat: SpadesSeat): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (state.trick.isEmpty()) return hand // any card when leading
        val ledSuit = state.trick.first().second.suit
        val follow = hand.filter { it.suit == ledSuit }
        if (follow.isNotEmpty()) return follow
        // Void in led-suit. Must play spade if any (after spades broken).
        val trickCards = state.trick.map { it.second }
        val spadesBroken = state.hands.values.flatten().any { it.suit == SolSuit.SPADE } &&
            (trickCards + state.tricksTaken.values.flatten()).any { it.suit == SolSuit.SPADE && it !in trickCards }
        if (spadesBroken) return hand // any card
        val spades = hand.filter { it.suit == SolSuit.SPADE }
        return if (spades.isNotEmpty()) spades else hand
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
        val newHands = state.hands.toMutableMap()
        val hand = newHands[state.currentPlayer]!!.toMutableList()
        hand.remove(card)
        newHands[state.currentPlayer] = hand
        val newTrick = state.trick + (state.currentPlayer to card)
        if (newTrick.size < 4) return state.copy(hands = newHands, trick = newTrick, currentPlayer = next(state.currentPlayer))
        // Trick done — spades trump if no other suit won (here: highest of led-suit wins; spade always beats if not led-suit and is spade).
        val ledSuit = newTrick.first().second.suit
        val hasSpade = newTrick.any { it.second.suit == SolSuit.SPADE }
        val winner = if (ledSuit == SolSuit.SPADE || !hasSpade) {
            newTrick.filter { it.second.suit == ledSuit }.maxBy { it.second.rank }
        } else {
            newTrick.filter { it.second.suit == SolSuit.SPADE }.maxBy { it.second.rank }
        }
        val newTricks = state.tricksTaken.toMutableMap()
        newTricks[winner.first] = (newTricks[winner.first]!!) + newTrick.map { it.second }
        val allEmpty = SpadesSeat.values().all { newHands[it]!!.isEmpty() }
        if (!allEmpty) return state.copy(hands = newHands, trick = emptyList(), currentPlayer = winner.first, tricksTaken = newTricks)
        return state.copy(hands = newHands, trick = emptyList(), tricksTaken = newTricks, done = true, currentPlayer = winner.first)
    }

    fun scoreRound(state: SpadesState): SpadesState {
        var (s1, s2) = state.score
        var bag = state.bagCount
        for (seat in SpadesSeat.values()) {
            val bid = state.bids[seat]!!
            val tricks = state.tricksTaken[seat]!!
            val bags = (tricks.size - bid).coerceAtLeast(0)
            val partner = partner(seat).second
            val seatPoints = when {
                state.nilBid[seat]!! -> if (tricks.isEmpty()) if (state.blindNil[seat]!!) 200 else 100 else -100
                else -> bid * 10 + bags
            }
            if (seat == SpadesSeat.SOUTH || seat == SpadesSeat.NORTH) s1 += seatPoints else s2 += seatPoints
            bag += bags
        }
        // Bag penalty: every 10 bags costs -100 to the team.
        val team1Bags = (s1 / 100) * 100
        if (team1Bags > 0 && team1Bags % 100 == 0 && s1 < team1Bags) s1 -= 100
        val team2Bags = (s2 / 100) * 100
        if (team2Bags > 0 && team2Bags % 100 == 0 && s2 < team2Bags) s2 -= 100
        // Reset bag count (carry handled by re-counting tricks each round).
        return state.copy(score = s1 to s2, bagCount = 0)
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
        val winner = if (opponentMoves.isEmpty() && state.turn == nextColor) state.turn else null
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
            newBoard[move.toR][5] = newBoard[move.toR][7]!! // rook h-file -> f-file
            newBoard[move.toR][7] = null
        } else if (move.castleQueenSide) {
            newBoard[move.toR][move.toC] = placed
            newBoard[move.toR][3] = newBoard[move.toR][0]!! // rook a-file -> d-file
            newBoard[move.toR][0] = null
        } else {
            newBoard[move.toR][move.toC] = placed
        }
        val newCastling = when {
            piece.type == ChessPieceType.K && piece.color == ChessColor.WHITE -> state.castling and 0b1100
            piece.type == ChessPieceType.K && piece.color == ChessColor.BLACK -> state.castling and 0b0011
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 0 -> state.castling and 0b1110
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 7 -> state.castling and 0b1101
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 0 -> state.castling and 0b1011
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 7 -> state.castling and 0b0111
            else -> state.castling
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
    val currentBet: Int,             // amount the bettor staked this round
    val lastRaiseSize: Int,         // minimum raise increment (BB)
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
        val community = mutableListOf<SolCard>()
        // Preflop blinds: player posts small (5), AI posts big (10), human acts first preflop? Convention varies.
        // Here: human is the dealer, so AI posts small blind, player posts big blind and acts first preflop.
        return PokerState(
            deck = deck, playerHole = playerHole, aiHole = aiHole, community = community,
            pot = 5 + 10,
            playerStack = 990, aiStack = 985,
            currentBet = 10, lastRaiseSize = 10,
            phase = PokerPhase.PREFLOP, actor = true, winner = null, log = listOf("blinds posted — small 5, big 10"),
        )
    }

    fun legalActions(state: PokerState): List<PokerAction> {
        if (state.phase == PokerPhase.DONE || state.phase == PokerPhase.SHOWDOWN) return emptyList()
        val owed = state.currentBet - amountContributed(state, state.actor)
        return if (owed == 0) listOf(PokerAction.CHECK, PokerAction.BET_RAISE, PokerAction.FOLD)
        else listOf(PokerAction.FOLD, PokerAction.CALL, PokerAction.BET_RAISE)
    }
    private fun amountContributed(state: PokerState, isPlayer: Boolean): Int {
        // Tracked via pot math. For simplicity, derive from currentBet + round starts. We use a coarse model: preflop blinds=10, post-flop currentBet resets to 0 each round.
        // Reconstruct from currentBet (caller's owed) and lastRaiseSize — keep it simple: the actor owes `currentBet - lastContribution`.
        return when {
            state.phase == PokerPhase.PREFLOP -> if (isPlayer) 10 else 5
            else -> 0
        }
    }

    fun applyAction(state: PokerState, action: PokerAction): PokerState {
        val owed = state.currentBet - amountContributed(state, state.actor)
        var newState = when (action) {
            PokerAction.FOLD -> state.copy(phase = PokerPhase.DONE, winner = if (state.actor) "ai" else "player", log = state.log + "${if (state.actor) "you" else "ai"} fold")
            PokerAction.CHECK -> advance(state.copy(log = state.log + "${if (state.actor) "you" else "ai"} check"))
            PokerAction.CALL -> {
                val paying = min(owed, if (state.actor) state.playerStack else state.aiStack)
                advance(state.copy(
                    pot = state.pot + paying,
                    playerStack = if (state.actor) state.playerStack - paying else state.playerStack,
                    aiStack = if (state.actor) state.aiStack else state.aiStack - paying,
                    log = state.log + "${if (state.actor) "you" else "ai"} call $paying",
                ))
            }
            PokerAction.BET_RAISE -> {
                val raise = state.lastRaiseSize
                val total = (if (state.actor) state.playerStack else state.aiStack).coerceAtMost(state.currentBet + raise)
                val paying = total - amountContributed(state, state.actor)
                advance(state.copy(
                    pot = state.pot + paying,
                    currentBet = total,
                    lastRaiseSize = raise,
                    playerStack = if (state.actor) state.playerStack - paying else state.playerStack,
                    aiStack = if (state.actor) state.aiStack else state.aiStack - paying,
                    log = state.log + "${if (state.actor) "you" else "ai"} raise to $total",
                ))
            }
        }
        return newState
    }

    private fun advance(state: PokerState): PokerState {
        // If the other player still owes chips after the action, just flip actor.
        val otherOwed = state.currentBet - amountContributed(state, !state.actor)
        if (otherOwed > 0) return state.copy(actor = !state.actor)
        // Both matched; deal next street or showdown.
        val next = when (state.phase) {
            PokerPhase.PREFLOP -> { val c = state.deck.take(3); state.deck.subList(3, state.deck.size).toMutableList().let { d -> state.copy(deck = d, community = state.community + c) }; PokerPhase.FLOP }
            PokerPhase.FLOP -> { val c = state.deck.take(1); state.deck.subList(1, state.deck.size).toMutableList().let { d -> state.copy(deck = d, community = state.community + c) }; PokerPhase.TURN }
            PokerPhase.TURN -> { val c = state.deck.take(1); state.deck.subList(1, state.deck.size).toMutableList().let { d -> state.copy(deck = d, community = state.community + c) }; PokerPhase.RIVER }
            PokerPhase.RIVER -> PokerPhase.SHOWDOWN
            else -> PokerPhase.SHOWDOWN
        }
        if (next == PokerPhase.SHOWDOWN) {
            val p = scoreHand(state.playerHole + state.community)
            val a = scoreHand(state.aiHole + state.community)
            val winner = when {
                p.first > a.first -> "player"
                a.first > p.first -> "ai"
                else -> "tie"
            }
            return state.copy(phase = PokerPhase.SHOWDOWN, winner = winner, actor = false, log = state.log + "showdown — you $p, ai $a — $winner wins")
        }
        return state.copy(phase = next, actor = state.actor, currentBet = 0, lastRaiseSize = 10)
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
    val colors = LocalDoradoColors.current
    val seat = HeartsSeat.SOUTH

    fun humanAction(s: HeartsState, action: HeartsState.() -> HeartsState) {
        val next = action(s)
        var cur = next
        while (cur.currentPlayer != seat && !cur.done && cur.pendingPassFrom == null) {
            cur = HeartsEngine.aiPlay(cur, Random.Default)
        }
        state = cur
    }

    val passCards = state.pendingPassFrom == seat
    DetailScaffold(title = "hearts") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "scores: ${state.scores.entries.joinToString { "${it.key.name.lowercase()}=${it.value}" }}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            Spacer(Modifier.height(8.dp))
            // N/W/E hands
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerHand(state.hands[HeartsSeat.NORTH]!!, align = Alignment.CenterHorizontally, label = "N")
                PlayerHand(state.hands[HeartsSeat.EAST]!!, align = Alignment.CenterHorizontally, label = "E")
            }
            Spacer(Modifier.height(8.dp))
            // Trick area
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.trick.forEach { (s, c) -> Column { BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary)); SolCardView(c) {} } }
            }
            Spacer(Modifier.height(8.dp))
            PlayerHand(
                hand = state.hands[seat]!!,
                onCard = { c -> if (!state.done && state.currentPlayer == seat && !passCards) humanAction(state) { HeartsEngine.play(state, c) } },
                align = Alignment.CenterHorizontally,
                label = "you",
                interactive = state.currentPlayer == seat && !passCards,
            )
            if (passCards) {
                Spacer(Modifier.height(8.dp))
                BasicText(text = "pass 3 cards", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent))
                PassPicker(hand = state.hands[seat]!!, onConfirm = { cards -> state = HeartsEngine.passCards(state, seat, cards) })
            }
            if (state.done) {
                Spacer(Modifier.height(8.dp))
                BasicText(text = "round complete", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            }
        }
        if (state.done) {
            LaunchedEffect(state.done) {
                scope.launch { graph.games.record("hearts", state.scores.values.sum(), null) }
            }
        }
    }
}

@Composable
private fun PlayerHand(hand: List<SolCard>, onCard: (SolCard) -> Unit = {}, align: Alignment.Horizontal, label: String, interactive: Boolean = true) {
    Column(horizontalAlignment = align) {
        BasicText(text = label, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = LocalDoradoColors.current.textSecondary))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hand.forEach { c ->
                if (interactive) SolCardView(c) { onCard(c) } else SolCardView(c) {}
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
                    SolCardView(if (selected) c.copy(faceUp = true) else c) {}
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
    val colors = LocalDoradoColors.current
    val seat = SpadesSeat.SOUTH
    fun human(s: SpadesState, action: (SpadesState) -> SpadesState) {
        var cur = action(s)
        while (!cur.biddingDone && cur.currentPlayer != seat) {
            val bid = (1..5).random()
            cur = SpadesEngine.bid(cur, bid)
        }
        while (!cur.done && cur.currentPlayer != seat) {
            val legal = SpadesEngine.legalPlays(cur, cur.currentPlayer)
            if (legal.isEmpty()) break
            cur = SpadesEngine.play(cur, legal.random())
        }
        state = cur
    }
    DetailScaffold(title = "spades") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "score ${state.score.first} / ${state.score.second}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpadesBidSeat(state, SpadesSeat.NORTH, seat) { human(state) { s -> s } }
                SpadesBidSeat(state, SpadesSeat.EAST, seat) { human(state) { s -> s } }
            }
            Spacer(Modifier.height(4.dp))
            if (!state.biddingDone) {
                if (state.currentPlayer == seat) {
                    BasicText(text = "your bid:", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..13).forEach { n ->
                            BasicText(text = "$n", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                modifier = Modifier.pointerInput(n) { detectTapGestures(onTap = { human(state) { SpadesEngine.bid(it, n) } }) })
                        }
                    }
                } else {
                    BasicText(text = "ai bidding…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    state.trick.forEach { (s, c) -> Column { BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary)); SolCardView(c) {} } }
                }
                Spacer(Modifier.height(4.dp))
                if (state.currentPlayer == seat) {
                    val legal = SpadesEngine.legalPlays(state, seat)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        legal.forEach { c -> SolCardView(c) { human(state) { SpadesEngine.play(it, c) } } }
                    }
                } else {
                    BasicText(text = "ai playing…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                }
            }
            Spacer(Modifier.height(4.dp))
            PlayerHand(state.hands[seat]!!, align = Alignment.CenterHorizontally, label = "you", interactive = state.currentPlayer == seat && state.biddingDone)
        }
        if (state.done) {
            LaunchedEffect(state.done) {
                val final = SpadesEngine.scoreRound(state)
                scope.launch { graph.games.record("spades", final.score.first, "team1") }
            }
        }
    }
}

@Composable
private fun SpadesBidSeat(state: SpadesState, seat: SpadesSeat, viewer: SpadesSeat, onInteract: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(text = "${seat.name.lowercase()}: ${state.bids[seat]}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = LocalDoradoColors.current.textSecondary))
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            state.hands[seat]!!.take(5).forEach { SolCardView(it) {} }
        }
    }
}

@Composable
fun CheckersApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CheckersEngine.newGame()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
    DetailScaffold(title = "checkers") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = if (state.winner != null) "${state.winner} wins" else "${state.turn} move", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(8.dp))
            val ckSelected = selected
            val ckMoveTo = moveTo
            val ckColors = colors
            val darkSquare = colors.tile
            val darkerSquare = colors.background
            androidx.compose.foundation.Canvas(modifier = Modifier.aspectRatio(1f).fillMaxWidth()) {
                val s = min(size.width, size.height); val cell = s / 8
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 1
                    drawRect(
                        color = if (ckSelected == r to c) ckColors.accent
                        else if (ckMoveTo.contains(r to c)) ckColors.tilePressed
                        else if (light) darkSquare else darkerSquare,
                        topLeft = Offset(c * cell, r * cell),
                        size = Size(cell, cell),
                    )
                }
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    val col = if (p.color == CheckersColor.RED) ckColors.accent else Color.White
                    val radius = cell * if (p.king) 0.42f else 0.36f
                    drawCircle(
                        color = if (p.king) col else col.copy(alpha = 0.85f),
                        center = Offset(c * cell + cell / 2, r * cell + cell / 2),
                        radius = radius,
                    )
                    if (p.king) {
                        drawCircle(
                            color = Color.Black,
                            center = Offset(c * cell + cell / 2, r * cell + cell / 2),
                            radius = cell * 0.18f,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                BasicText(text = "you are red", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            }
        }
    }
    if (state.turn == CheckersColor.BLACK && state.winner == null) {
        LaunchedEffect(state.turn) { delay(200); val mv = CheckersEngine.aiMove(state); if (mv != null) state = CheckersEngine.apply(state, mv.first, mv.second) }
    }
    if (state.winner != null) {
        LaunchedEffect(state.winner) { scope.launch { graph.games.record("checkers", if (state.winner == CheckersColor.RED) 1 else 0, "winner") } }
    }
    // Tap-to-move: a 1:1 transparent overlay so taps route by (r,c) without consuming the canvas drawing.
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(state, selected) {
        detectTapGestures(onTap = { offset ->
            val s = min(size.width, size.height); val cell = s / 8
            val c = (offset.x / cell).toInt().coerceIn(0, 7)
            val r = (offset.y / cell).toInt().coerceIn(0, 7)
            val p = state.board[r][c]
            if (p != null && p.color == state.turn) selected = r to c
            else if (selected != null && (r to c) in moveTo) tryPlay(r to c)
        })
    })
}

@Composable
fun ChessApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ChessEngine.newGame()) }
    val colors = LocalDoradoColors.current
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val moves = remember(state) { ChessEngine.legalMoves(state) }
    val targets = remember(selected, moves) { selected?.let { sel -> moves.filter { it.fromR == sel.first && it.fromC == sel.second }.map { it.toR to it.toC } } ?: emptyList() }
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
    DetailScaffold(title = "chess") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = if (state.status.isNotEmpty()) state.status else if (state.turn == ChessColor.WHITE) "your move" else "ai thinking…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(4.dp))
            val selectedSq = selected
            val targetSquares = targets
            val chColors = colors
            val lightSquare = colors.tile
            val darkSquare = colors.background
            androidx.compose.foundation.Canvas(modifier = Modifier.aspectRatio(1f).fillMaxWidth()) {
                val s = min(size.width, size.height); val cell = s / 8
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 0
                    drawRect(
                        color = if (selectedSq == r to c) chColors.accent
                        else if ((r to c) in targetSquares) chColors.tilePressed
                        else if (light) lightSquare else darkSquare,
                        topLeft = Offset(c * cell, r * cell), size = Size(cell, cell),
                    )
                }
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    val col = if (p.color == ChessColor.WHITE) Color.White else Color.Black
                    drawCircle(col, cell * 0.35f, Offset(c * cell + cell / 2, r * cell + cell / 2))
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "tap a piece, then a target", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
    if (state.turn == ChessColor.BLACK && state.status.isEmpty()) {
        LaunchedEffect(state) { delay(80); val mv = ChessEngine.bestMove(state, depth = 3); if (mv != null) state = ChessEngine.apply(state, mv) }
    }
    if (state.status.isNotEmpty()) {
        LaunchedEffect(state.status) {
            val score = if (state.status.startsWith("checkmate white")) 1 else if (state.status.startsWith("checkmate black")) 0 else 0
            scope.launch { graph.games.record("chess", score, state.status) }
        }
    }
    // Tap overlay
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(state, selected) {
        detectTapGestures(onTap = { offset ->
            val s = min(size.width, size.height); val cell = s / 8
            val c = (offset.x / cell).toInt().coerceIn(0, 7)
            val r = (offset.y / cell).toInt().coerceIn(0, 7)
            click(r, c)
        })
    })
}

@Composable
fun TexasHoldemApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PokerEngine.newGame()) }
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "texas hold 'em") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "pot ${state.pot} • you ${state.playerStack} • ai ${state.aiStack} • ${state.phase}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent))
            Spacer(Modifier.height(8.dp))
            BasicText(text = "ai: ${state.aiHole.joinToString("") { it.toString().take(2) }}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.community.forEach { SolCardView(it) {} }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(text = "you: ${state.playerHole.joinToString("") { it.toString().take(2) }}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.playerHole.forEach { SolCardView(it) {} }
            }
            Spacer(Modifier.height(8.dp))
            if (state.phase == PokerPhase.SHOWDOWN || state.phase == PokerPhase.DONE) {
                BasicText(text = if (state.winner == "tie") "tie" else "${state.winner} wins", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
                BasicText(text = "new hand", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { state = PokerEngine.newGame() }) })
            } else if (state.actor) {
                val legal = PokerEngine.legalActions(state)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    if (!state.actor && state.phase != PokerPhase.DONE && state.phase != PokerPhase.SHOWDOWN) {
        LaunchedEffect(state) { delay(400); state = PokerEngine.applyAction(state, PokerEngine.aiAction(state, Random.Default)) }
    }
    if (state.phase == PokerPhase.DONE) {
        LaunchedEffect(state.phase) {
            val s = when (state.winner) { "player" -> 1; "ai" -> 0; else -> 0 }
            scope.launch { graph.games.record("texasholdem", s, state.winner) }
        }
    }
}
