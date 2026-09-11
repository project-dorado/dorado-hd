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

object SudokuEngine {
    data class Board(
        val rows: List<List<Int>>,
        val given: List<List<Boolean>>,
        val solution: List<List<Int>>,
    )

    fun newGame(seed: Long = 0L): Board {
        // Generate a solved board via backtrack, then carve out 36 cells.
        val base = List(9) { MutableList(9) { 0 } }
        val rng = Random(if (seed != 0L) seed else System.nanoTime())
        val solved = solve(base, rng) ?: List(9) { List(9) { 1 } }
        val puzzle = solved.map { it.toMutableList() }
        val keep = 81 - 45
        val cells = (0..80).toMutableList()
        cells.shuffle(rng)
        val clear = cells.take(81 - keep)
        for (c in clear) puzzle[c / 9][c % 9] = 0
        return Board(
            rows = puzzle.map { it.toList() },
            given = puzzle.map { row -> row.map { it != 0 } },
            solution = solved,
        )
    }

    /** True when every cell matches the generated solution. */
    fun isSolved(cells: List<Int>, solution: List<List<Int>>): Boolean =
        cells.size == 81 && cells.indices.all { cells[it] == solution[it / 9][it % 9] }

    private fun solve(grid: List<MutableList<Int>>, rng: Random): List<List<Int>>? {
        for (r in 0 until 9) for (c in 0 until 9) if (grid[r][c] == 0) {
            val nums = (1..9).shuffled(rng)
            for (n in nums) {
                grid[r][c] = n
                if (valid(grid, r, c) && solve(grid, rng) != null) return grid.map { it.toList() }
                grid[r][c] = 0
            }
            return null
        }
        return grid.map { it.toList() }
    }

    private fun valid(grid: List<MutableList<Int>>, r: Int, c: Int): Boolean {
        val v = grid[r][c]
        for (i in 0 until 9) if (i != c && grid[r][i] == v) return false
        for (i in 0 until 9) if (i != r && grid[i][c] == v) return false
        val br = r / 3 * 3; val bc = c / 3 * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            val rr = br + i; val cc = bc + j
            if ((rr != r || cc != c) && grid[rr][cc] == v) return false
        }
        return true
    }
}

/* ============================================================ */
/*                              Hexic                             */
/* ============================================================ */
