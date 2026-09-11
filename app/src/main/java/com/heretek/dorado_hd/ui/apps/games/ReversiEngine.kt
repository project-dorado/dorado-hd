package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/* ============================================================ */
/*                            Reversi                             */
/* ============================================================ */

/** Official AIDifficulty: random, greedy piece count, greedy + weights. */
enum class ReversiLevel { EASY, MEDIUM, HARD }

/** Human side selection; [NONE] = local two-player. */
enum class ReversiSide { BLACK, WHITE, NONE }

data class ReversiStats(
    val played: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
)

data class ReversiGame(
    val board: Array<IntArray>,
    val turn: Int,
    val over: Boolean,
    val winner: Int = ReversiEngine.EMPTY,
    val history: List<ReversiGame> = emptyList(),
)

object ReversiEngine {
    const val EMPTY = 0; const val BLACK = 1; const val WHITE = 2

    fun newBoard(): Array<IntArray> = Array(8) { IntArray(8) }.also {
        it[3][3] = WHITE; it[4][4] = WHITE; it[3][4] = BLACK; it[4][3] = BLACK
    }

    fun legalMoves(board: Array<IntArray>, player: Int): Set<Pair<Int, Int>> {
        val moves = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until 8) for (c in 0 until 8) if (board[r][c] == EMPTY && flips(board, r, c, player).isNotEmpty()) moves.add(r to c)
        return moves
    }

    fun apply(board: Array<IntArray>, r: Int, c: Int, player: Int): Array<IntArray> {
        val next: Array<IntArray> = Array(board.size) { board[it].copyOf() }
        val flips = flips(board, r, c, player)
        if (flips.isEmpty()) return board
        next[r][c] = player
        for ((fr, fc) in flips) next[fr][fc] = player
        return next
    }

    private fun flips(board: Array<IntArray>, r: Int, c: Int, player: Int): List<Pair<Int, Int>> {
        if (board[r][c] != EMPTY) return emptyList()
        val opp = if (player == BLACK) WHITE else BLACK
        val out = mutableListOf<Pair<Int, Int>>()
        for ((dr, dc) in dirs()) {
            var rr = r + dr; var cc = c + dc
            val line = mutableListOf<Pair<Int, Int>>()
            while (rr in 0 until 8 && cc in 0 until 8 && board[rr][cc] == opp) {
                line.add(rr to cc); rr += dr; cc += dc
            }
            if (line.isNotEmpty() && rr in 0 until 8 && cc in 0 until 8 && board[rr][cc] == player) out.addAll(line)
        }
        return out
    }

    private fun dirs(): List<Pair<Int, Int>> = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

    fun score(board: Array<IntArray>): Pair<Int, Int> {
        var b = 0; var w = 0
        for (r in 0 until 8) for (c in 0 until 8) {
            if (board[r][c] == BLACK) b++ else if (board[r][c] == WHITE) w++
        }
        return b to w
    }

    fun opponent(player: Int): Int = if (player == BLACK) WHITE else BLACK

    fun pieceCount(board: Array<IntArray>, color: Int): Int {
        var n = 0
        for (r in 0 until 8) for (c in 0 until 8) if (board[r][c] == color) n++
        return n
    }

    /**
     * Next side to move after [mover] played: the opponent normally, [mover]
     * again when the opponent must pass, or null when neither side can move.
     */
    fun nextPlayer(board: Array<IntArray>, mover: Int): Int? {
        val opp = opponent(mover)
        return when {
            legalMoves(board, opp).isNotEmpty() -> opp
            legalMoves(board, mover).isNotEmpty() -> mover
            else -> null
        }
    }

    fun isGameOver(board: Array<IntArray>): Boolean =
        legalMoves(board, BLACK).isEmpty() && legalMoves(board, WHITE).isEmpty()

    /** BLACK/WHITE on a decided board; EMPTY for a draw. */
    fun determineWinner(board: Array<IntArray>): Int {
        val (b, w) = score(board)
        return when {
            b > w -> BLACK
            w > b -> WHITE
            else -> EMPTY
        }
    }

    /* ---------------- session ---------------- */

    fun newGame(): ReversiGame = ReversiGame(newBoard(), BLACK, over = false)

    private fun snapshot(g: ReversiGame): ReversiGame = g.copy(history = emptyList())

    fun play(g: ReversiGame, r: Int, c: Int): ReversiGame {
        if (g.over) return g
        if ((r to c) !in legalMoves(g.board, g.turn)) return g
        val next = apply(g.board, r, c, g.turn)
        val nextTurn = nextPlayer(next, g.turn)
        val over = nextTurn == null
        return ReversiGame(
            board = next,
            turn = nextTurn ?: g.turn,
            over = over,
            winner = if (over) determineWinner(next) else EMPTY,
            history = g.history + snapshot(g),
        )
    }

    fun undo(g: ReversiGame): ReversiGame = g.history.lastOrNull() ?: g

    /* ---------------- AI ---------------- */

    /**
     * Easy picks any legal move, Medium maximizes its own piece count after one
     * ply, Hard adds corner/edge weights and a bounded one-ply opponent reply.
     */
    fun bestMove(
        board: Array<IntArray>,
        player: Int,
        level: ReversiLevel = ReversiLevel.MEDIUM,
        rng: Random = Random.Default,
    ): Pair<Int, Int>? {
        val moves = legalMoves(board, player).toList()
        if (moves.isEmpty()) return null
        return when (level) {
            ReversiLevel.EASY -> moves[rng.nextInt(moves.size)]
            ReversiLevel.MEDIUM -> moves.maxByOrNull { pieceCount(apply(board, it.first, it.second, player), player) }
            ReversiLevel.HARD -> moves.maxByOrNull { hardScore(board, it, player) }
        }
    }

    private fun isCorner(r: Int, c: Int): Boolean = (r == 0 || r == 7) && (c == 0 || c == 7)

    private fun isEdge(r: Int, c: Int): Boolean = r == 0 || r == 7 || c == 0 || c == 7

    private fun hardScore(board: Array<IntArray>, move: Pair<Int, Int>, player: Int): Int {
        val next = apply(board, move.first, move.second, player)
        var s = pieceCount(next, player) * 2
        if (isCorner(move.first, move.second)) s += 40
        if (isEdge(move.first, move.second)) s += 10
        val opp = opponent(player)
        // Shallow lookahead: subtract the opponent's best immediate piece count.
        val reply = legalMoves(next, opp).maxOfOrNull {
            pieceCount(apply(next, it.first, it.second, opp), opp)
        } ?: 0
        return s - reply
    }

    /** Group recorded results (meta key, 1=win / 0=loss / 2=draw) per key. */
    fun statsFrom(records: List<Pair<String, Int>>): Map<String, ReversiStats> {
        val out = mutableMapOf<String, ReversiStats>()
        records.forEach { (meta, score) ->
            val key = meta.substringBefore("|")
            val cur = out[key] ?: ReversiStats()
            out[key] = when (score) {
                1 -> cur.copy(played = cur.played + 1, wins = cur.wins + 1)
                0 -> cur.copy(played = cur.played + 1, losses = cur.losses + 1)
                else -> cur.copy(played = cur.played + 1, draws = cur.draws + 1)
            }
        }
        return out
    }
}
