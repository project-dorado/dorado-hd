package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CheckersColor { RED, BLACK }

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

enum class CheckersDifficulty(val depth: Int, val label: String) {
    EASY(1, "easy"),
    INTERMEDIATE(3, "intermediate"),
    HARD(8, "hard"),
}

enum class CheckersMode { REGULAR, SUICIDE }

data class CheckersUndo(
    val board: Array<Array<CheckersPiece?>>,
    val turn: CheckersColor,
    val captureChain: List<Pair<Int, Int>>?,
    val winner: CheckersColor?,
)

data class CheckersMoveRecord(
    val from: Pair<Int, Int>,
    val to: Pair<Int, Int>,
    val captures: List<Pair<Int, Int>> = emptyList(),
    val promoted: Boolean = false,
    val path: List<Pair<Int, Int>> = listOf(from, to),
) {
    val isJump: Boolean get() = captures.isNotEmpty()
    val isMultiJump: Boolean get() = captures.size > 1
}

data class CheckersSave(
    val fen: String,
    val difficulty: String,
    val mode: String,
    val flipped: Boolean,
    val redElapsedMs: Long,
    val blackElapsedMs: Long,
)

data class CheckersState(
    val board: Array<Array<CheckersPiece?>>,
    val turn: CheckersColor,
    val captureChain: List<Pair<Int, Int>>?,
    val winner: CheckersColor?,
    val history: List<CheckersUndo> = emptyList(),
    val mode: CheckersMode = CheckersMode.REGULAR,
)

object CheckersEngine {
    /** Plies without a capture that trigger the draw clock. */
    const val DRAW_QUIET_PLIES = 80

    /**
     * Advance the quiet-ply draw counter: any capture resets it, otherwise it
     * increments. Both human and AI plies must move the same counter (A-30).
     */
    fun quietPliesAfter(current: Int, move: CheckersMoveRecord): Int =
        if (move.captures.isEmpty()) current + 1 else 0

    private val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    private fun onBoard(r: Int, c: Int) = r in 0 until 8 && c in 0 until 8

    private fun forwardDirs(color: CheckersColor) = when (color) {
        CheckersColor.RED -> listOf(1 to -1, 1 to 1)
        CheckersColor.BLACK -> listOf(-1 to -1, -1 to 1)
    }

    fun newGame(): CheckersState {
        val b = Array(8) { arrayOfNulls<CheckersPiece?>(8) }
        for (r in 0 until 3) for (c in 0 until 8) if ((r + c) % 2 == 1) b[r][c] = checkersPiece(CheckersColor.RED, false)
        for (r in 5 until 8) for (c in 0 until 8) if ((r + c) % 2 == 1) b[r][c] = checkersPiece(CheckersColor.BLACK, false)
        return CheckersState(b, CheckersColor.RED, null, null)
    }

    fun legalMoves(state: CheckersState, color: CheckersColor): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
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
                val nr = r + dr
                val nc = c + dc
                if (!onBoard(nr, nc)) continue
                val target = state.board[nr][nc]
                if (target == null) {
                    if (state.captureChain == null) moves.add(r to c to (nr to nc))
                } else if (target.color != color) {
                    val jr = nr + dr
                    val jc = nc + dc
                    if (onBoard(jr, jc) && state.board[jr][jc] == null) {
                        captures.add(r to c to (jr to jc))
                    }
                }
            }
        }
        return if (captures.isNotEmpty()) captures else moves
    }

    fun legalMovesDetailed(state: CheckersState, color: CheckersColor = state.turn): List<CheckersMoveRecord> {
        if (state.winner != null) return emptyList()
        val flat = toFlat(state.board)
        val chain = state.captureChain
        val root = chain?.lastOrNull()
        val moves = generate(flat, color, root)
        return moves.map { it.toRecord() }
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
        val dr = to.first - from.first
        val dc = to.second - from.second
        if (abs(dr) == 2 && abs(dc) == 2) {
            newBoard[from.first + dr / 2][from.second + dc / 2] = null
        }
        val newChain = mutableListOf<Pair<Int, Int>>().apply { add(from); add(to) }
        if (abs(dr) == 2) {
            val more = legalMoves(state.copy(board = newBoard, captureChain = newChain), piece.color)
                .filter { it.first == to }
            if (more.isNotEmpty()) {
                return withHistory(
                    state,
                    CheckersState(newBoard, state.turn, newChain, state.winner),
                )
            }
        }
        val nextColor = if (state.turn == CheckersColor.RED) CheckersColor.BLACK else CheckersColor.RED
        val opponentMoves = legalMoves(CheckersState(newBoard, nextColor, null, null), nextColor)
        val winner = if (opponentMoves.isEmpty()) state.turn else null
        return withHistory(
            state,
            CheckersState(newBoard, nextColor, null, winner),
        )
    }

    fun applyMove(state: CheckersState, move: CheckersMoveRecord): CheckersState {
        val newBoard: Array<Array<CheckersPiece?>> = Array(state.board.size) { state.board[it].copyOf() }
        val piece = newBoard[move.from.first][move.from.second] ?: return state
        newBoard[move.from.first][move.from.second] = null
        var king = piece.king
        for (capture in move.captures) newBoard[capture.first][capture.second] = null
        if (move.promoted) {
            king = true
        } else if (piece.color == CheckersColor.RED && move.to.first == 7) {
            king = true
        } else if (piece.color == CheckersColor.BLACK && move.to.first == 0) {
            king = true
        }
        newBoard[move.to.first][move.to.second] = checkersPiece(piece.color, king)
        val nextColor = if (state.turn == CheckersColor.RED) CheckersColor.BLACK else CheckersColor.RED
        val opponentMoves = generate(toFlat(newBoard), nextColor, null)
        val winner = if (opponentMoves.isEmpty()) state.turn else null
        return withHistory(
            state,
            CheckersState(newBoard, nextColor, null, winner),
        )
    }

    private fun withHistory(previous: CheckersState, next: CheckersState): CheckersState {
        val snapshot = CheckersUndo(previous.board, previous.turn, previous.captureChain, previous.winner)
        val history = (previous.history + snapshot).takeLast(64)
        return next.copy(history = history, mode = previous.mode)
    }

    fun undo(state: CheckersState, plies: Int = 2): CheckersState {
        val count = min(plies, state.history.size)
        if (count <= 0) return state
        val snapshot = state.history[state.history.size - count]
        return CheckersState(
            board = snapshot.board,
            turn = snapshot.turn,
            captureChain = snapshot.captureChain,
            winner = snapshot.winner,
            history = state.history.dropLast(count),
            mode = state.mode,
        )
    }

    fun aiMove(state: CheckersState): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val moves = legalMoves(state, state.turn)
        if (moves.isEmpty()) return null
        return moves.maxBy { m -> abs(m.second.first - m.first.first) }
    }

    fun hint(state: CheckersState, mode: CheckersMode = state.mode, maxNodes: Int = 30_000): CheckersMoveRecord? =
        bestMove(state, CheckersDifficulty.INTERMEDIATE, mode, maxNodes, hintDepth = 4)

    fun bestMove(
        state: CheckersState,
        difficulty: CheckersDifficulty = CheckersDifficulty.INTERMEDIATE,
        mode: CheckersMode = state.mode,
        maxNodes: Int = when (difficulty) {
            CheckersDifficulty.EASY -> 3_000
            CheckersDifficulty.INTERMEDIATE -> 60_000
            CheckersDifficulty.HARD -> 220_000
        },
        hintDepth: Int? = null,
    ): CheckersMoveRecord? {
        if (state.winner != null) return null
        val flat = toFlat(state.board)
        val root = state.captureChain?.lastOrNull()
        val moves = generate(flat, state.turn, root)
        if (moves.isEmpty()) return null
        val depth = hintDepth ?: difficulty.depth
        orderMoves(flat, moves, state.turn)
        var best = moves.first()
        val budget = SearchBudget(maxNodes)
        for (currentDepth in 1..depth) {
            var localBest: IntMove? = null
            var alpha = -INF
            val beta = INF
            for (move in moves) {
                val next = applyInternal(flat, move, state.turn)
                val score = -negascout(next, other(state.turn), currentDepth - 1, -beta, -alpha, 1, budget, mode)
                if (score > alpha || localBest == null) {
                    alpha = score
                    localBest = move
                }
                if (budget.exhausted) break
            }
            if (localBest != null) best = localBest
            if (budget.exhausted) break
        }
        return best.toRecord()
    }

    private class SearchBudget(val maxNodes: Int) {
        var nodes = 0
        val exhausted: Boolean get() = nodes >= maxNodes
        fun visit(): Boolean {
            nodes++
            return nodes < maxNodes
        }
    }

    private fun negascout(
        board: IntArray,
        color: CheckersColor,
        depth: Int,
        alphaIn: Int,
        beta: Int,
        ply: Int,
        budget: SearchBudget,
        mode: CheckersMode,
    ): Int {
        if (!budget.visit()) return evaluate(board, color, mode)
        val moves = generate(board, color, null)
        if (moves.isEmpty()) {
            return if (mode == CheckersMode.SUICIDE) MATE - ply else -(MATE - ply)
        }
        if (depth <= 0) return evaluate(board, color, mode)
        orderMoves(board, moves, color)
        var alpha = alphaIn
        var best = -INF
        var first = true
        for (move in moves) {
            val next = applyInternal(board, move, color)
            val score: Int
            if (first) {
                score = -negascout(next, other(color), depth - 1, -beta, -alpha, ply + 1, budget, mode)
                first = false
            } else {
                score = -negascout(next, other(color), depth - 1, -alpha - 1, -alpha, ply + 1, budget, mode)
                if (score > alpha && score < beta) {
                    val re = -negascout(next, other(color), depth - 1, -beta, -score, ply + 1, budget, mode)
                    if (re > score) {
                        if (re > best) best = re
                        if (re > alpha) alpha = re
                        if (alpha >= beta) break
                        continue
                    }
                }
            }
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
            if (budget.exhausted) break
        }
        return best
    }

    private fun other(color: CheckersColor) =
        if (color == CheckersColor.RED) CheckersColor.BLACK else CheckersColor.RED

    private val MATE = 1_000_000
    private const val INF = 10_000_000

    private const val MAN = 1000
    private const val KING = 1399
    private const val CROWNING_BONUS = 200
    private const val MULTI_JUMP_BONUS = 1000

    private val centerWeight = intArrayOf(0, 1, 2, 4, 4, 2, 1, 0)

    private fun evaluate(board: IntArray, color: CheckersColor, mode: CheckersMode): Int {
        var red = 0
        var black = 0
        var redMoves = 0
        var blackMoves = 0
        for (sq in 0 until 64) {
            val piece = board[sq]
            if (piece == 0) continue
            val r = sq / 8
            val c = sq % 8
            val value = if (piece == 1 || piece == 3) MAN else KING
            val advance = when (piece) {
                1 -> min(1500, r * 250 + CROWNING_BONUS)
                3 -> min(1500, (7 - r) * 250 + CROWNING_BONUS)
                else -> 0
            }
            val center = centerWeight[c] + centerWeight[r]
            val edge = if (c == 0 || c == 7) 4 else 0
            val total = value + advance + center + edge
            if (piece == 1 || piece == 2) red += total else black += total
            val steps = stepCount(board, sq, piece)
            if (piece == 1 || piece == 2) redMoves += steps else blackMoves += steps
        }
        val raw = red - black + 2 * (redMoves - blackMoves)
        val perspective = if (color == CheckersColor.RED) raw else -raw
        return if (mode == CheckersMode.SUICIDE) -perspective else perspective
    }

    private fun stepCount(board: IntArray, sq: Int, piece: Int): Int {
        val r = sq / 8
        val c = sq % 8
        var count = 0
        val dirsFor = if (piece == 1) listOf(1 to -1, 1 to 1)
        else if (piece == 3) listOf(-1 to -1, -1 to 1)
        else listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((dr, dc) in dirsFor) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until 8 && nc in 0 until 8 && board[nr * 8 + nc] == 0) count++
        }
        return count
    }

    private data class IntMove(
        val from: Int,
        val to: Int,
        val path: IntArray,
        val captures: IntArray,
        val promoted: Boolean,
    ) {
        fun toRecord(): CheckersMoveRecord = CheckersMoveRecord(
            from = from / 8 to from % 8,
            to = to / 8 to to % 8,
            captures = captures.map { it / 8 to it % 8 },
            promoted = promoted,
            path = path.map { it / 8 to it % 8 },
        )
    }

    private fun generate(board: IntArray, color: CheckersColor, fromOnly: Pair<Int, Int>?): MutableList<IntMove> {
        val moves = mutableListOf<IntMove>()
        val only = fromOnly?.let { it.first * 8 + it.second }
        for (sq in 0 until 64) {
            val piece = board[sq]
            if (piece == 0) continue
            val isRed = piece == 1 || piece == 2
            if (isRed != (color == CheckersColor.RED)) continue
            if (only != null && sq != only) continue
            collectCaptures(board, sq, piece, IntArray(1) { sq }, emptyList(), false, moves)
        }
        if (moves.isEmpty()) {
            for (sq in 0 until 64) {
                val piece = board[sq]
                if (piece == 0) continue
                val isRed = piece == 1 || piece == 2
                if (isRed != (color == CheckersColor.RED)) continue
                if (only != null && sq != only) continue
                val r = sq / 8
                val c = sq % 8
                val pieceDirs = when (piece) {
                    1 -> listOf(1 to -1, 1 to 1)
                    3 -> listOf(-1 to -1, -1 to 1)
                    else -> dirs
                }
                for ((dr, dc) in pieceDirs) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until 8 || nc !in 0 until 8) continue
                    val target = nr * 8 + nc
                    if (board[target] != 0) continue
                    val promoted = (piece == 1 && nr == 7) || (piece == 3 && nr == 0)
                    moves += IntMove(sq, target, intArrayOf(sq, target), IntArray(0), promoted)
                }
            }
        }
        return moves
    }

    private fun collectCaptures(
        board: IntArray,
        from: Int,
        piece: Int,
        path: IntArray,
        captures: List<Int>,
        crowned: Boolean,
        out: MutableList<IntMove>,
    ) {
        val r = from / 8
        val c = from % 8
        val king = piece == 2 || piece == 4
        val dirsFor = if (king) dirs else if (piece == 1) listOf(1 to -1, 1 to 1) else listOf(-1 to -1, -1 to 1)
        var extended = false
        for ((dr, dc) in dirsFor) {
            val midR = r + dr
            val midC = c + dc
            val landR = r + 2 * dr
            val landC = c + 2 * dc
            if (landR !in 0 until 8 || landC !in 0 until 8) continue
            val mid = midR * 8 + midC
            val land = landR * 8 + landC
            val victim = board[mid]
            if (victim == 0 || board[land] != 0) continue
            if (captures.contains(mid)) continue
            val isRed = piece == 1 || piece == 2
            if (isRed == (victim == 1 || victim == 2)) continue
            extended = true
            val nextPiece = when {
                piece == 1 && landR == 7 -> 2
                piece == 3 && landR == 0 -> 4
                else -> piece
            }
            val nowCrowned = crowned || nextPiece != piece
            val nextBoard = board.copyOf()
            nextBoard[from] = 0
            nextBoard[mid] = 0
            nextBoard[land] = nextPiece
            collectCaptures(nextBoard, land, nextPiece, path + land, captures + mid, nowCrowned, out)
        }
        if (!extended && captures.isNotEmpty()) {
            out += IntMove(
                from = path.first(),
                to = from,
                path = path,
                captures = captures.toIntArray(),
                promoted = crowned,
            )
        }
    }

    private fun applyInternal(board: IntArray, move: IntMove, color: CheckersColor): IntArray {
        val next = board.copyOf()
        val piece = next[move.from]
        next[move.from] = 0
        for (capture in move.captures) next[capture] = 0
        var placed = piece
        val promoted = move.promoted ||
            (piece == 1 && move.to / 8 == 7) ||
            (piece == 3 && move.to / 8 == 0)
        if (promoted) {
            placed = if (color == CheckersColor.RED) 2 else 4
        }
        next[move.to] = placed
        return next
    }

    private fun orderMoves(board: IntArray, moves: MutableList<IntMove>, color: CheckersColor) {
        moves.sortWith(
            compareByDescending<IntMove> { it.captures.size * MULTI_JUMP_BONUS }
                .thenByDescending { if (it.promoted) CROWNING_BONUS else 0 }
                .thenByDescending { centerWeight[it.to % 8] + centerWeight[it.to / 8] },
        )
    }

    private fun toFlat(board: Array<Array<CheckersPiece?>>): IntArray {
        val flat = IntArray(64)
        for (r in 0 until 8) for (c in 0 until 8) {
            val piece = board[r][c] ?: continue
            flat[r * 8 + c] = when {
                piece.color == CheckersColor.RED && !piece.king -> 1
                piece.color == CheckersColor.RED && piece.king -> 2
                piece.color == CheckersColor.BLACK && !piece.king -> 3
                else -> 4
            }
        }
        return flat
    }

    private fun fromFlat(flat: IntArray): Array<Array<CheckersPiece?>> {
        val board = Array(8) { arrayOfNulls<CheckersPiece?>(8) }
        for (sq in 0 until 64) {
            val piece = flat[sq]
            if (piece == 0) continue
            board[sq / 8][sq % 8] = checkersPiece(
                color = if (piece == 1 || piece == 2) CheckersColor.RED else CheckersColor.BLACK,
                king = piece == 2 || piece == 4,
            )
        }
        return board
    }

    private fun squareNumber(r: Int, c: Int): Int {
        var number = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if ((row + col) % 2 == 1) {
                    number++
                    if (row == r && col == c) return number
                }
            }
        }
        return 0
    }

    private fun squareAt(number: Int): Pair<Int, Int>? {
        if (number !in 1..32) return null
        var count = 0
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if ((row + col) % 2 == 1) {
                    count++
                    if (count == number) return row to col
                }
            }
        }
        return null
    }

    fun fen(state: CheckersState): String {
        val red = mutableListOf<String>()
        val black = mutableListOf<String>()
        for (r in 0 until 8) for (c in 0 until 8) {
            val piece = state.board[r][c] ?: continue
            val number = squareNumber(r, c)
            val text = if (piece.king) "K$number" else "$number"
            if (piece.color == CheckersColor.RED) red += text else black += text
        }
        val turn = if (state.turn == CheckersColor.RED) "R" else "B"
        return "$turn:${red.joinToString(",")}:${black.joinToString(",")}"
    }

    fun fromFen(fen: String): CheckersState? {
        return try {
            val parts = fen.trim().split(":")
            if (parts.size < 3) return null
            val turn = when (parts[0]) {
                "R", "r" -> CheckersColor.RED
                "B", "b" -> CheckersColor.BLACK
                else -> return null
            }
            val board = Array(8) { arrayOfNulls<CheckersPiece?>(8) }
            fun place(field: String, color: CheckersColor) {
                if (field.isBlank()) return
                for (token in field.split(",")) {
                    val king = token.startsWith("K")
                    val number = token.removePrefix("K").toInt()
                    val square = squareAt(number) ?: continue
                    board[square.first][square.second] = checkersPiece(color, king)
                }
            }
            place(parts[1], CheckersColor.RED)
            place(parts[2], CheckersColor.BLACK)
            CheckersState(board, turn, null, null)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeSave(save: CheckersSave): String = listOf(
        save.fen,
        save.difficulty,
        save.mode,
        if (save.flipped) "1" else "0",
        save.redElapsedMs.toString(),
        save.blackElapsedMs.toString(),
    ).joinToString("|")

    fun decodeSave(blob: String): CheckersSave? {
        return try {
            val parts = blob.split("|")
            if (parts.size < 6) return null
            CheckersSave(
                fen = parts[0],
                difficulty = parts[1],
                mode = parts[2],
                flipped = parts[3] == "1",
                redElapsedMs = parts[4].toLong(),
                blackElapsedMs = parts[5].toLong(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun boardFromFlat(flat: IntArray): Array<Array<CheckersPiece?>> = fromFlat(flat)

    fun evaluateFor(
        state: CheckersState,
        color: CheckersColor = state.turn,
        mode: CheckersMode = state.mode,
    ): Int = evaluate(toFlat(state.board), color, mode)
}
