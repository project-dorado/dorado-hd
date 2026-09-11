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
