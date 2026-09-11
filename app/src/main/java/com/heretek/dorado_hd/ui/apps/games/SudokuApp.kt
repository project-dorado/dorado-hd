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

@Composable
fun SudokuApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val board = remember { SudokuEngine.newGame() }
    val cells = remember { mutableStateListOf<Int>().apply { repeat(81) { add(board.rows.flatten()[it]) } } }
    val given = board.given.flatten()
    var elapsed by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(-1) }
    var recorded by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running) { delay(1000); elapsed++ }
    }

    fun setCell(idx: Int, v: Int) {
        if (given[idx]) return
        cells[idx] = v
    }

    val solved = SudokuEngine.isSolved(cells.toList(), board.solution)
    LaunchedEffect(solved) {
        if (solved && !recorded) {
            recorded = true
            running = false
            scope.launch { graph.games.record("sudoku", (10_000 - elapsed.toInt()).coerceAtLeast(0), null) }
        }
    }

    DetailScaffold(title = "sudoku") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = if (solved) "solved" else "%02d:%02d".format(elapsed / 60, elapsed % 60),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (solved) colors.accent else colors.textPrimary),
            )
            Spacer(Modifier.height(4.dp))
            // Board scales to whichever axis is tightest, so all 9 rows are
            // always visible in device mode.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Account for the 1dp row gaps: 9 cells + 8 gaps must fit.
                val cell = (minOf(maxWidth, maxHeight) - 9.dp) / 9
                Column {
                    for (r in 0 until 9) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            for (c in 0 until 9) {
                                val idx = r * 9 + c
                                val v = cells[idx]
                                val isGiven = given[idx]
                                val isSel = selected == idx
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(
                                            when {
                                                isSel -> colors.accent
                                                isGiven -> colors.tile
                                                else -> colors.elevated
                                            },
                                        )
                                        .pointerInput(idx) { detectTapGestures(onTap = { selected = idx }) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v != 0) BasicText(
                                        text = v.toString(),
                                        style = TextStyle(
                                            fontFamily = Selawik,
                                            fontSize = 13.sp,
                                            color = if (isGiven) colors.textPrimary else colors.accent,
                                        ),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                (1..9).forEach { n ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .background(colors.elevated)
                            .pointerInput(n) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, n) }) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(text = n.toString(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(26.dp)
                        .background(colors.elevated)
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (selected >= 0) setCell(selected, 0) }) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(text = "x", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                }
            }
        }
    }
}
