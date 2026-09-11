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

enum class HexColor { A, B, C, EMPTY }
object HexicEngine {
    fun newBoard(size: Int = 7, rng: Random = Random.Default): Array<Array<HexColor>> =
        Array(size) { Array(size) { listOf(HexColor.A, HexColor.B, HexColor.C).random(rng) } }

    /** Cycle the tile's color (advance A→B→C→A), reviving cleared tiles. */
    fun rotate(board: Array<Array<HexColor>>, r: Int, c: Int): Array<Array<HexColor>> {
        val next = copyOf(board)
        next[r][c] = when (board[r][c]) {
            HexColor.A -> HexColor.B
            HexColor.B -> HexColor.C
            HexColor.EMPTY -> HexColor.A
            else -> HexColor.A
        }
        return next
    }

    /** Detect all clusters of 3+ adjacent (orthogonal-diagonal) same-color cells.
     *  Cleared (EMPTY) cells never participate, so clearing cannot loop. */
    fun clusters(board: Array<Array<HexColor>>): Set<Pair<Int, Int>> {
        val n = board.size
        val visited = Array(n) { BooleanArray(n) }
        val result = mutableSetOf<Pair<Int, Int>>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (r in 0 until n) for (c in 0 until n) {
            if (visited[r][c]) continue
            val color = board[r][c]
            if (color == HexColor.EMPTY) {
                visited[r][c] = true
                continue
            }
            val stack = ArrayDeque<Pair<Int, Int>>().apply { add(r to c) }
            val comp = mutableListOf<Pair<Int, Int>>()
            while (stack.isNotEmpty()) {
                val (cr, cc) = stack.removeLast()
                if (cr !in 0 until n || cc !in 0 until n || visited[cr][cc]) continue
                if (board[cr][cc] != color) continue
                visited[cr][cc] = true
                comp += cr to cc
                for ((dr, dc) in dirs) stack.add(cr + dr to cc + dc)
            }
            if (comp.size >= 3) result.addAll(comp)
        }
        return result
    }

    /** Clear the cells; returns a new board. */
    fun clearAndScore(board: Array<Array<HexColor>>, cells: Set<Pair<Int, Int>>): Array<Array<HexColor>> {
        val next = copyOf(board)
        for ((r, c) in cells) next[r][c] = HexColor.EMPTY
        return next
    }

    /** Refill cleared cells with random colors (cascades end before refill). */
    fun refill(board: Array<Array<HexColor>>, rng: Random = Random.Default): Array<Array<HexColor>> {
        val next = copyOf(board)
        for (r in next.indices) for (c in next.indices) {
            if (next[r][c] == HexColor.EMPTY) next[r][c] = listOf(HexColor.A, HexColor.B, HexColor.C).random(rng)
        }
        return next
    }

    private fun copyOf(board: Array<Array<HexColor>>): Array<Array<HexColor>> =
        Array(board.size) { board[it].copyOf() }
}

/* ============================================================ */
/*                            Reversi                             */
/* ============================================================ */
