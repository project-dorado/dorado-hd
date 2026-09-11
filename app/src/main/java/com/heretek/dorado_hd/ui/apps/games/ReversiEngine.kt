package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/* ============================================================ */
/*                          Solitaire (Klondike)                  */
/* ============================================================ */

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
}

/* ============================================================ */
/*                              UI                                 */
/* ============================================================ */
