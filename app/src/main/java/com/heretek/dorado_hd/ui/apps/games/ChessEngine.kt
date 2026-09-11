package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.min

enum class ChessPieceType { P, N, B, R, Q, K }
enum class ChessColor { WHITE, BLACK }

enum class ChessDifficulty(val depth: Int, val label: String) {
    EASY(1, "easy"),
    INTERMEDIATE(3, "intermediate"),
    HARD(5, "hard"),
}

data class ChessPiece(val type: ChessPieceType, val color: ChessColor)

data class ChessMove(
    val fromR: Int, val fromC: Int,
    val toR: Int, val toC: Int,
    val promotion: ChessPieceType? = null,
    val enPassant: Boolean = false,
    val castleKingSide: Boolean = false,
    val castleQueenSide: Boolean = false,
)

data class ChessUndo(
    val board: Array<Array<ChessPiece?>>,
    val turn: ChessColor,
    val castling: Int,
    val enPassant: Pair<Int, Int>?,
    val halfmove: Int,
    val fullmove: Int,
    val status: String,
)

data class ChessState(
    val board: Array<Array<ChessPiece?>>,
    val turn: ChessColor,
    val castling: Int,
    val enPassant: Pair<Int, Int>?,
    val halfmove: Int,
    val fullmove: Int,
    val status: String,
    val history: List<ChessUndo> = emptyList(),
)

data class ChessSave(
    val fen: String,
    val difficulty: String,
    val color: String,
    val twoPlayer: Boolean,
    val theme: String,
    val elapsedMs: Long,
)

object ChessEngine {
    const val MATE_VALUE = 50000

    fun newGame(): ChessState {
        val b = Array(8) { arrayOfNulls<ChessPiece?>(8) }
        val back = listOf(
            ChessPieceType.R, ChessPieceType.N, ChessPieceType.B, ChessPieceType.K,
            ChessPieceType.Q, ChessPieceType.B, ChessPieceType.N, ChessPieceType.R,
        )
        for (c in 0 until 8) {
            b[0][c] = ChessPiece(back[c], ChessColor.WHITE)
            b[1][c] = ChessPiece(ChessPieceType.P, ChessColor.WHITE)
            b[6][c] = ChessPiece(ChessPieceType.P, ChessColor.BLACK)
            b[7][c] = ChessPiece(back[c], ChessColor.BLACK)
        }
        return ChessState(b, ChessColor.WHITE, 0b1111, null, 0, 1, "")
    }

    fun at(state: ChessState, r: Int, c: Int): ChessPiece? = if (r in 0..7 && c in 0..7) state.board[r][c] else null
    fun color(state: ChessState, r: Int, c: Int): ChessColor? = at(state, r, c)?.color
    fun inBounds(r: Int, c: Int) = r in 0..7 && c in 0..7
    fun opposite(c: ChessColor) = if (c == ChessColor.WHITE) ChessColor.BLACK else ChessColor.WHITE

    private fun rayTargets(state: ChessState, color: ChessColor): Set<Pair<Int, Int>> {
        val out = mutableSetOf<Pair<Int, Int>>()
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            if (p.color != color) continue
            when (p.type) {
                ChessPieceType.B, ChessPieceType.R, ChessPieceType.Q -> {
                    val pdirs = when (p.type) {
                        ChessPieceType.B -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                        ChessPieceType.R -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                        else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    }
                    for ((dr, dc) in pdirs) {
                        var rr = r + dr
                        var cc = c + dc
                        while (inBounds(rr, cc)) {
                            out.add(rr to cc)
                            if (state.board[rr][cc] != null) break
                            rr += dr
                            cc += dc
                        }
                    }
                }
                ChessPieceType.N -> for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) {
                    val rr = r + dr
                    val cc = c + dc
                    if (inBounds(rr, cc)) out.add(rr to cc)
                }
                ChessPieceType.P -> {
                    val dir = if (color == ChessColor.WHITE) 1 else -1
                    for (dc in listOf(-1, 1)) {
                        val rr = r + dir
                        val cc = c + dc
                        if (inBounds(rr, cc)) out.add(rr to cc)
                    }
                }
                ChessPieceType.K -> for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)) {
                    val rr = r + dr
                    val cc = c + dc
                    if (inBounds(rr, cc)) out.add(rr to cc)
                }
            }
        }
        return out
    }

    fun isInCheck(state: ChessState, color: ChessColor): Boolean {
        var kingR = -1
        var kingC = -1
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c]
            if (p?.type == ChessPieceType.K && p.color == color) {
                kingR = r
                kingC = c
            }
        }
        if (kingR < 0) return false
        return (kingR to kingC) in rayTargets(state, opposite(color))
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
                        if (fwdR == promoRow) {
                            for (t in listOf(ChessPieceType.Q, ChessPieceType.R, ChessPieceType.B, ChessPieceType.N)) {
                                out.add(ChessMove(r, c, fwdR, c, t))
                            }
                        } else {
                            out.add(ChessMove(r, c, fwdR, c))
                        }
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
                            if (fwdR == promoRow) {
                                for (t in listOf(ChessPieceType.Q, ChessPieceType.R, ChessPieceType.B, ChessPieceType.N)) {
                                    out.add(ChessMove(r, c, fwdR, cc, t))
                                }
                            } else {
                                out.add(ChessMove(r, c, fwdR, cc))
                            }
                        } else if (state.enPassant == fwdR to cc) {
                            out.add(ChessMove(r, c, fwdR, cc, enPassant = true))
                        }
                    }
                }
                ChessPieceType.N -> for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) {
                    val rr = r + dr
                    val cc = c + dc
                    if (inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != color)) {
                        out.add(ChessMove(r, c, rr, cc))
                    }
                }
                ChessPieceType.B, ChessPieceType.R, ChessPieceType.Q -> {
                    val dirs = when (p.type) {
                        ChessPieceType.B -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                        ChessPieceType.R -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                        else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    }
                    for ((dr, dc) in dirs) {
                        var rr = r + dr
                        var cc = c + dc
                        while (inBounds(rr, cc)) {
                            val t = state.board[rr][cc]
                            if (t == null) {
                                out.add(ChessMove(r, c, rr, cc))
                            } else {
                                if (t.color != color) out.add(ChessMove(r, c, rr, cc))
                                break
                            }
                            rr += dr
                            cc += dc
                        }
                    }
                }
                ChessPieceType.K -> {
                    for ((dr, dc) in listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)) {
                        val rr = r + dr
                        val cc = c + dc
                        if (inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != color)) {
                            out.add(ChessMove(r, c, rr, cc))
                        }
                    }
                    val row = if (color == ChessColor.WHITE) 0 else 7
                    if (r == row && c == 4) {
                        val ksRight = state.castling and (if (color == ChessColor.WHITE) 0b0001 else 0b0100) != 0
                        val qsRight = state.castling and (if (color == ChessColor.WHITE) 0b0010 else 0b1000) != 0
                        val rookKs = state.board[row][7]
                        val rookQs = state.board[row][0]
                        if (ksRight && rookKs?.type == ChessPieceType.R && rookKs.color == color &&
                            state.board[row][5] == null && state.board[row][6] == null
                        ) {
                            if (!isSquareAttacked(state, row, 4, opposite(color)) &&
                                !isSquareAttacked(state, row, 5, opposite(color)) &&
                                !isSquareAttacked(state, row, 6, opposite(color))
                            ) {
                                out.add(ChessMove(row, 4, row, 6, castleKingSide = true))
                            }
                        }
                        if (qsRight && rookQs?.type == ChessPieceType.R && rookQs.color == color &&
                            state.board[row][1] == null && state.board[row][2] == null && state.board[row][3] == null
                        ) {
                            if (!isSquareAttacked(state, row, 4, opposite(color)) &&
                                !isSquareAttacked(state, row, 3, opposite(color)) &&
                                !isSquareAttacked(state, row, 2, opposite(color))
                            ) {
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
        val probe = ChessState(
            board = Array(state.board.size) { state.board[it].copyOf() }.also {
                it[r][c] = ChessPiece(ChessPieceType.P, byColor)
            },
            turn = byColor,
            castling = 0,
            enPassant = null,
            halfmove = 0,
            fullmove = 1,
            status = "",
        )
        return rayTargets(probe, byColor).contains(r to c)
    }

    fun apply(state: ChessState, move: ChessMove, skipCheck: Boolean = false): ChessState {
        val newBoard: Array<Array<ChessPiece?>> = Array(state.board.size) { state.board[it].copyOf() }
        val piece = newBoard[move.fromR][move.fromC] ?: return state
        newBoard[move.fromR][move.fromC] = null
        if (move.enPassant) {
            val dir = if (piece.color == ChessColor.WHITE) -1 else 1
            newBoard[move.toR + dir][move.toC] = null
        }
        var placed = piece
        if (move.promotion != null) placed = piece.copy(type = move.promotion)
        if (move.castleKingSide) {
            newBoard[move.toR][move.toC] = placed
            val rook = newBoard[move.toR][7]
            if (rook != null && rook.type == ChessPieceType.R && rook.color == piece.color) {
                newBoard[move.toR][5] = rook
                newBoard[move.toR][7] = null
            }
        } else if (move.castleQueenSide) {
            newBoard[move.toR][move.toC] = placed
            val rook = newBoard[move.toR][0]
            if (rook != null && rook.type == ChessPieceType.R && rook.color == piece.color) {
                newBoard[move.toR][3] = rook
                newBoard[move.toR][0] = null
            }
        } else {
            newBoard[move.toR][move.toC] = placed
        }
        var newCastling = when {
            piece.type == ChessPieceType.K && piece.color == ChessColor.WHITE -> state.castling and 0b1100
            piece.type == ChessPieceType.K && piece.color == ChessColor.BLACK -> state.castling and 0b0011
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 0 -> state.castling and 0b1101
            piece.type == ChessPieceType.R && piece.color == ChessColor.WHITE && move.fromR == 0 && move.fromC == 7 -> state.castling and 0b1110
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 0 -> state.castling and 0b0111
            piece.type == ChessPieceType.R && piece.color == ChessColor.BLACK && move.fromR == 7 && move.fromC == 7 -> state.castling and 0b1011
            else -> state.castling
        }
        if (move.toR == 0) {
            if (move.toC == 0) newCastling = newCastling and 0b1101
            if (move.toC == 7) newCastling = newCastling and 0b1110
        }
        if (move.toR == 7) {
            if (move.toC == 0) newCastling = newCastling and 0b0111
            if (move.toC == 7) newCastling = newCastling and 0b1011
        }
        val newEP = if (piece.type == ChessPieceType.P && abs(move.toR - move.fromR) == 2) {
            (move.fromR + move.toR) / 2 to move.fromC
        } else {
            null
        }
        val nextTurn = opposite(state.turn)
        val halfmove = if (piece.type == ChessPieceType.P) 0 else state.halfmove + 1
        val fullmove = state.fullmove + (if (state.turn == ChessColor.BLACK) 1 else 0)
        val base = ChessState(newBoard, nextTurn, newCastling, newEP, halfmove, fullmove, "")
        val newStatus = if (!skipCheck) statusFor(base, nextTurn) else ""
        val result = base.copy(status = newStatus)
        if (skipCheck) return result
        val snapshot = ChessUndo(
            board = state.board,
            turn = state.turn,
            castling = state.castling,
            enPassant = state.enPassant,
            halfmove = state.halfmove,
            fullmove = state.fullmove,
            status = state.status,
        )
        return result.copy(history = (state.history + snapshot).takeLast(128))
    }

    private fun statusFor(state: ChessState, turn: ChessColor): String {
        val moves = legalMoves(state)
        val inCheck = isInCheck(state, turn)
        return when {
            inCheck && moves.isEmpty() -> "checkmate ${if (turn == ChessColor.WHITE) "white" else "black"}"
            !inCheck && moves.isEmpty() -> "stalemate"
            inCheck -> "check ${if (turn == ChessColor.WHITE) "white" else "black"}"
            else -> ""
        }
    }

    fun undo(state: ChessState, halfMoves: Int = 2): ChessState {
        val count = min(halfMoves, state.history.size)
        if (count <= 0) return state
        val snapshot = state.history[state.history.size - count]
        return ChessState(
            board = snapshot.board,
            turn = snapshot.turn,
            castling = snapshot.castling,
            enPassant = snapshot.enPassant,
            halfmove = snapshot.halfmove,
            fullmove = snapshot.fullmove,
            status = snapshot.status,
            history = state.history.dropLast(count),
        )
    }

    fun isCheckmate(state: ChessState) = state.status.startsWith("checkmate")
    fun isStalemate(state: ChessState) = state.status == "stalemate"

    private val pieceValue = mapOf(
        ChessPieceType.P to 1000,
        ChessPieceType.N to 3000,
        ChessPieceType.B to 3299,
        ChessPieceType.R to 5000,
        ChessPieceType.Q to 10000,
        ChessPieceType.K to 100000,
    )

    fun evaluate(state: ChessState): Int {
        var white = 0
        var black = 0
        var whiteMobility = 0
        var blackMobility = 0
        val pawnFilesWhite = IntArray(8)
        val pawnFilesBlack = IntArray(8)
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c] ?: continue
            val value = pieceValue[p.type]!!
            var bonus = centerBonus(r, c)
            if (p.type == ChessPieceType.N && (r == 0 || r == 7 || c == 0 || c == 7)) bonus -= 13
            if (p.type == ChessPieceType.P) {
                if (p.color == ChessColor.WHITE) pawnFilesWhite[c]++ else pawnFilesBlack[c]++
            }
            if (p.type == ChessPieceType.R) {
                val fileBlocked = (0..7).any { rr ->
                    state.board[rr][c]?.let { it.type == ChessPieceType.P && it.color == p.color } == true
                }
                if (!fileBlocked) bonus += 2
            }
            val mobility = roughMobility(state, r, c, p)
            if (p.color == ChessColor.WHITE) {
                white += value + bonus
                whiteMobility += mobility
            } else {
                black += value + bonus
                blackMobility += mobility
            }
        }
        white -= doubledAndIsolated(pawnFilesWhite)
        black -= doubledAndIsolated(pawnFilesBlack)
        white += castlingBonus(state, ChessColor.WHITE)
        black += castlingBonus(state, ChessColor.BLACK)
        val raw = white - black + (whiteMobility - blackMobility)
        return if (state.turn == ChessColor.WHITE) raw else -raw
    }

    private fun centerBonus(r: Int, c: Int): Int {
        val centerDistance = abs(r - 3) + abs(c - 3)
        return (7 - centerDistance) * 13 / 2
    }

    private fun doubledAndIsolated(files: IntArray): Int {
        var penalty = 0
        for (file in 0 until 8) {
            if (files[file] > 1) penalty += 500 * (files[file] - 1)
            if (files[file] > 0) {
                val left = if (file > 0) files[file - 1] else 0
                val right = if (file < 7) files[file + 1] else 0
                if (left == 0 && right == 0) penalty += 200
            }
        }
        return penalty
    }

    private fun castlingBonus(state: ChessState, color: ChessColor): Int {
        val ks = state.castling and (if (color == ChessColor.WHITE) 0b0001 else 0b0100) != 0
        val qs = state.castling and (if (color == ChessColor.WHITE) 0b0010 else 0b1000) != 0
        return (if (ks) 100 else 0) + (if (qs) 10 else 0)
    }

    private fun roughMobility(state: ChessState, r: Int, c: Int, p: ChessPiece): Int {
        return when (p.type) {
            ChessPieceType.N -> listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                .count { (dr, dc) ->
                    val rr = r + dr
                    val cc = c + dc
                    inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != p.color)
                }
            ChessPieceType.K -> listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
                .count { (dr, dc) ->
                    val rr = r + dr
                    val cc = c + dc
                    inBounds(rr, cc) && (state.board[rr][cc] == null || state.board[rr][cc]!!.color != p.color)
                }
            ChessPieceType.B, ChessPieceType.R, ChessPieceType.Q -> {
                val dirs = when (p.type) {
                    ChessPieceType.B -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                    ChessPieceType.R -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                }
                var count = 0
                for ((dr, dc) in dirs) {
                    var rr = r + dr
                    var cc = c + dc
                    while (inBounds(rr, cc)) {
                        count++
                        if (state.board[rr][cc] != null) break
                        rr += dr
                        cc += dc
                    }
                }
                count
            }
            ChessPieceType.P -> 0
        }
    }

    private class SearchBudget(val maxNodes: Int) {
        var nodes = 0
        val exhausted: Boolean get() = nodes >= maxNodes
        fun visit(): Boolean {
            nodes++
            return nodes < maxNodes
        }
    }

    fun bestMove(
        state: ChessState,
        difficulty: ChessDifficulty = ChessDifficulty.INTERMEDIATE,
        maxNodes: Int = when (difficulty) {
            ChessDifficulty.EASY -> 6_000
            ChessDifficulty.INTERMEDIATE -> 90_000
            ChessDifficulty.HARD -> 260_000
        },
    ): ChessMove? {
        if (state.status.isNotEmpty()) return null
        bookMove(state)?.let { return it }
        var effective = difficulty
        if (difficulty == ChessDifficulty.EASY && evaluate(state) < -800) {
            effective = ChessDifficulty.INTERMEDIATE
        }
        return searchBestMove(state, effective.depth, maxNodes)
    }

    fun hint(state: ChessState, maxNodes: Int = 80_000): ChessMove? {
        if (state.status.isNotEmpty()) return null
        return searchBestMove(state, depth = 4, maxNodes = maxNodes)
    }

    fun bestMove(state: ChessState, depth: Int): ChessMove? = searchBestMove(state, depth, 120_000)

    private fun searchBestMove(state: ChessState, depth: Int, maxNodes: Int): ChessMove? {
        val moves = orderedMoves(state, legalMoves(state))
        if (moves.isEmpty()) return null
        var best = moves.first()
        val budget = SearchBudget(maxNodes)
        for (currentDepth in 1..depth) {
            var alpha = -1_000_000
            var localBest: ChessMove? = null
            for (move in moves) {
                val next = apply(state, move, skipCheck = true)
                val score = -negamax(next, currentDepth - 1, -1_000_000, -alpha, 1, budget)
                if (score > alpha || localBest == null) {
                    alpha = score
                    localBest = move
                }
                if (budget.exhausted) break
            }
            if (localBest != null) best = localBest
            if (budget.exhausted) break
        }
        return best
    }

    private fun negamax(
        state: ChessState,
        depth: Int,
        alphaIn: Int,
        beta: Int,
        ply: Int,
        budget: SearchBudget,
    ): Int {
        if (!budget.visit()) return evaluate(state)
        val moves = orderedMoves(state, legalMoves(state))
        if (moves.isEmpty()) {
            return if (isInCheck(state, state.turn)) -(MATE_VALUE - ply) else 0
        }
        if (depth <= 0) return evaluate(state)
        var alpha = alphaIn
        var best = -2_000_000
        for (move in moves) {
            val next = apply(state, move, skipCheck = true)
            val score = -negamax(next, depth - 1, -beta, -alpha, ply + 1, budget)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
            if (budget.exhausted) break
        }
        return best
    }

    private fun orderedMoves(state: ChessState, moves: List<ChessMove>): List<ChessMove> =
        moves.sortedByDescending { move ->
            val target = state.board[move.toR][move.toC]
            var score = if (target != null) pieceValue[target.type]!! else 0
            if (move.promotion != null) score += pieceValue[move.promotion]!!
            if (move.castleKingSide || move.castleQueenSide) score += 150
            score
        }

    private val openingBook: Map<String, List<ChessMove>> by lazy { buildOpeningBook() }

    fun bookMove(state: ChessState): ChessMove? {
        val entry = openingBook[positionKey(state)] ?: return null
        val legal = legalMoves(state)
        return entry.firstOrNull { candidate -> legal.any { it == candidate } }
    }

    private fun buildOpeningBook(): Map<String, List<ChessMove>> {
        val lines = listOf(
            listOf(m(1, 4, 3, 4), m(6, 4, 4, 4), m(0, 6, 2, 5), m(7, 1, 5, 2), m(0, 5, 3, 2)),
            listOf(m(1, 4, 3, 4), m(6, 2, 4, 2), m(0, 6, 2, 5), m(6, 3, 5, 3), m(1, 3, 3, 3)),
            listOf(m(1, 4, 3, 4), m(6, 4, 5, 4), m(1, 3, 3, 3), m(6, 3, 4, 3), m(0, 1, 2, 2)),
            listOf(m(1, 4, 3, 4), m(6, 2, 5, 2), m(1, 3, 3, 3), m(6, 3, 4, 3), m(0, 1, 2, 2)),
            listOf(m(1, 3, 3, 3), m(6, 3, 4, 3), m(1, 2, 3, 2), m(7, 6, 5, 5), m(0, 1, 2, 2)),
            listOf(m(1, 3, 3, 3), m(7, 6, 5, 5), m(1, 2, 3, 2), m(6, 4, 5, 4), m(0, 1, 2, 2)),
            listOf(m(0, 6, 2, 5), m(6, 3, 4, 3), m(1, 2, 3, 2), m(6, 4, 5, 4), m(0, 1, 2, 2)),
            listOf(m(1, 2, 3, 2), m(6, 4, 4, 4), m(0, 6, 2, 5), m(6, 3, 5, 3), m(0, 1, 2, 2)),
        )
        val book = mutableMapOf<String, MutableList<ChessMove>>()
        for (line in lines) {
            var state = newGame()
            for (i in line.indices) {
                val key = positionKey(state)
                book.getOrPut(key) { mutableListOf() }.add(line[i])
                state = apply(state, line[i], skipCheck = true)
            }
        }
        return book
    }

    private fun positionKey(state: ChessState): String {
        val sb = StringBuilder(80)
        for (r in 0..7) for (c in 0..7) {
            val p = state.board[r][c]
            if (p == null) {
                sb.append('.')
            } else {
                sb.append(when (p.type) {
                    ChessPieceType.P -> 'p'
                    ChessPieceType.N -> 'n'
                    ChessPieceType.B -> 'b'
                    ChessPieceType.R -> 'r'
                    ChessPieceType.Q -> 'q'
                    ChessPieceType.K -> 'k'
                })
                sb.append(if (p.color == ChessColor.WHITE) 'w' else 'x')
            }
        }
        sb.append(if (state.turn == ChessColor.WHITE) 'W' else 'B')
        sb.append(state.castling)
        return sb.toString()
    }

    private fun m(fromR: Int, fromC: Int, toR: Int, toC: Int) = ChessMove(fromR, fromC, toR, toC)

    fun fromFen(fen: String): ChessState? {
        return try {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.size < 4) return null
            val rows = parts[0].split("/")
            if (rows.size != 8) return null
            val board = Array(8) { arrayOfNulls<ChessPiece?>(8) }
            for (r in 0..7) {
                var c = 0
                for (ch in rows[r]) {
                    if (ch.isDigit()) {
                        c += ch.digitToInt()
                        continue
                    }
                    val color = if (ch.isUpperCase()) ChessColor.WHITE else ChessColor.BLACK
                    val type = when (ch.lowercaseChar()) {
                        'p' -> ChessPieceType.P
                        'n' -> ChessPieceType.N
                        'b' -> ChessPieceType.B
                        'r' -> ChessPieceType.R
                        'q' -> ChessPieceType.Q
                        'k' -> ChessPieceType.K
                        else -> return null
                    }
                    if (c > 7) return null
                    board[r][c] = ChessPiece(type, color)
                    c++
                }
            }
            val turn = if (parts[1] == "w") ChessColor.WHITE else ChessColor.BLACK
            var castling = 0
            for (ch in parts[2]) {
                castling = when (ch) {
                    'K' -> castling or 0b0001
                    'Q' -> castling or 0b0010
                    'k' -> castling or 0b0100
                    'q' -> castling or 0b1000
                    else -> castling
                }
            }
            val ep = if (parts[3] == "-") {
                null
            } else {
                val file = parts[3][0] - 'a'
                val row = 8 - (parts[3][1] - '0')
                row to file
            }
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            refreshStatus(ChessState(board, turn, castling, ep, half, full, ""))
        } catch (_: Exception) {
            null
        }
    }

    fun refreshStatus(state: ChessState): ChessState =
        state.copy(status = statusFor(state, state.turn))

    fun fen(state: ChessState): String {
        val placement = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val p = state.board[r][c]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        placement.append(empty)
                        empty = 0
                    }
                    val letter = when (p.type) {
                        ChessPieceType.P -> 'p'
                        ChessPieceType.N -> 'n'
                        ChessPieceType.B -> 'b'
                        ChessPieceType.R -> 'r'
                        ChessPieceType.Q -> 'q'
                        ChessPieceType.K -> 'k'
                    }
                    placement.append(if (p.color == ChessColor.WHITE) letter.uppercaseChar() else letter)
                }
            }
            if (empty > 0) placement.append(empty)
            if (r < 7) placement.append('/')
        }
        val active = if (state.turn == ChessColor.WHITE) "w" else "b"
        val castling = buildString {
            if (state.castling and 0b0001 != 0) append('K')
            if (state.castling and 0b0010 != 0) append('Q')
            if (state.castling and 0b0100 != 0) append('k')
            if (state.castling and 0b1000 != 0) append('q')
            if (isEmpty()) append('-')
        }
        val ep = state.enPassant?.let { "${('a' + it.second)}${8 - it.first}" } ?: "-"
        return "$placement $active $castling $ep ${state.halfmove} ${state.fullmove}"
    }

    fun encodeSave(save: ChessSave): String = listOf(
        save.fen,
        save.difficulty,
        save.color,
        if (save.twoPlayer) "1" else "0",
        save.theme,
        save.elapsedMs.toString(),
    ).joinToString("|")

    fun decodeSave(blob: String): ChessSave? {
        return try {
            val parts = blob.split("|")
            if (parts.size < 6) return null
            ChessSave(
                fen = parts[0],
                difficulty = parts[1],
                color = parts[2],
                twoPlayer = parts[3] == "1",
                theme = parts[4],
                elapsedMs = parts[5].toLong(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun autoQueen(state: ChessState, move: ChessMove): ChessMove {
        if (move.promotion == null || move.promotion == ChessPieceType.Q) return move
        return move.copy(promotion = ChessPieceType.Q)
    }
}
