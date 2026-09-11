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
