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
fun HexicApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val n = 7
    var board by remember { mutableStateOf(HexicEngine.newBoard(n)) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    val bestFlow by graph.games.top("hexic").collectAsState(initial = emptyList())

    DisposableEffect(Unit) {
        onDispose {
            val finalScore = score
            if (finalScore > 0) {
                scope.launch(NonCancellable) { graph.games.record("hexic", finalScore, null) }
            }
        }
    }

    fun tap(r: Int, c: Int) {
        var next = HexicEngine.rotate(board, r, c)
        var gained = 0
        // Cascade: EMPTY cells never form clusters, so each pass strictly
        // reduces the non-empty tile count and the loop terminates.
        while (true) {
            val cells = HexicEngine.clusters(next)
            if (cells.isEmpty()) break
            gained += cells.size
            next = HexicEngine.clearAndScore(next, cells)
        }
        next = HexicEngine.refill(next)
        board = next
        score += gained
        if (score > best) best = score
    }

    DetailScaffold(title = "hexic") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "score $score  best ${bestFlow.firstOrNull()?.score ?: 0}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            Spacer(Modifier.height(6.dp))
            val hexicAccent = colors.accent
            val hexicBg = colors.elevated
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(n) {
                        // Hit-test on the same Canvas that draws the board; the
                        // old overlay was offset from the drawing and unreachable.
                        detectTapGestures { offset ->
                            val cellW = size.width / n.toFloat()
                            val cellH = size.height / n.toFloat()
                            val c = (offset.x / cellW).toInt().coerceIn(0, n - 1)
                            val r = (offset.y / cellH).toInt().coerceIn(0, n - 1)
                            tap(r, c)
                        }
                    },
            ) {
                val w = size.width; val h = size.height
                val cellW = w / n
                val cellH = h / n
                // Radius follows the tighter axis; using cellW alone made
                // circles overflow their cells (and clip the score line).
                val slot = minOf(cellW, cellH)
                for (r in 0 until n) for (c in 0 until n) {
                    val cx = c * cellW + cellW / 2
                    val cy = r * cellH + cellH / 2
                    drawCircle(hexicBg, slot * 0.45f, Offset(cx, cy))
                    val color = when (board[r][c]) {
                        HexColor.A -> Color.White
                        HexColor.B -> hexicAccent
                        HexColor.C -> Color.Yellow
                        HexColor.EMPTY -> hexicBg
                    }
                    drawCircle(color, slot * 0.25f, Offset(cx, cy))
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(text = "rotate a tile • clear 3+ same-color adjacents", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}
