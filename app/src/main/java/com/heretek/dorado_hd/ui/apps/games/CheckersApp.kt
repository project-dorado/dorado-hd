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

@Composable
fun CheckersApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CheckersEngine.newGame()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val legalMoves = remember(state) { CheckersEngine.legalMoves(state, state.turn) }
    val moveTo = remember(selected, legalMoves) {
        selected?.let { sel -> legalMoves.filter { it.first == sel }.map { it.second } } ?: emptyList()
    }
    fun tryPlay(to: Pair<Int, Int>) {
        val from = selected ?: return
        state = CheckersEngine.apply(state, from, to)
        selected = null
    }

    // AI moves whenever it is black's turn — keyed on the whole state so a
    // multi-jump capture chain continues instead of stalling mid-chain.
    LaunchedEffect(state) {
        if (state.turn == CheckersColor.BLACK && state.winner == null) {
            delay(200)
            val mv = CheckersEngine.aiMove(state)
            if (mv != null) state = CheckersEngine.apply(state, mv.first, mv.second)
        }
    }
    if (state.winner != null && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            scope.launch { graph.games.record("checkers", if (state.winner == CheckersColor.RED) 1 else 0, "winner") }
        }
    }

    DetailScaffold(title = "checkers") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = when {
                    state.winner == CheckersColor.RED -> "you win"
                    state.winner == CheckersColor.BLACK -> "ai wins"
                    state.turn == CheckersColor.RED -> "your move"
                    else -> "ai thinking…"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            val ckSelected = selected
            val ckMoveTo = moveTo
            val ckColors = colors
            val darkSquare = colors.tile
            val darkerSquare = colors.background
            // Draw and hit-test on the SAME canvas: the old transparent overlay
            // was a root-level sibling offset by the header, so taps mis-mapped
            // and covered the back header.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(state, selected) {
                        detectTapGestures(onTap = { offset ->
                            val s = min(size.width, size.height)
                            val cell = s / 8f
                            val ox = (size.width - s) / 2f
                            val oy = (size.height - s) / 2f
                            val c = ((offset.x - ox) / cell).toInt().coerceIn(0, 7)
                            val r = ((offset.y - oy) / cell).toInt().coerceIn(0, 7)
                            val p = state.board[r][c]
                            if (p != null && p.color == state.turn) selected = r to c
                            else if (selected != null && (r to c) in moveTo) tryPlay(r to c)
                        })
                    },
            ) {
                val s = min(size.width, size.height)
                val cell = s / 8
                val ox = (size.width - s) / 2
                val oy = (size.height - s) / 2
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 1
                    drawRect(
                        color = if (ckSelected == r to c) ckColors.accent
                        else if (ckMoveTo.contains(r to c)) ckColors.tilePressed
                        else if (light) darkSquare else darkerSquare,
                        topLeft = Offset(ox + c * cell, oy + r * cell),
                        size = Size(cell, cell),
                    )
                }
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    val col = if (p.color == CheckersColor.RED) ckColors.accent else Color.White
                    val radius = cell * if (p.king) 0.42f else 0.36f
                    drawCircle(
                        color = if (p.king) col else col.copy(alpha = 0.85f),
                        center = Offset(ox + c * cell + cell / 2, oy + r * cell + cell / 2),
                        radius = radius,
                    )
                    if (p.king) {
                        drawCircle(
                            color = Color.Black,
                            center = Offset(ox + c * cell + cell / 2, oy + r * cell + cell / 2),
                            radius = cell * 0.18f,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you are red — tap a piece, then a highlighted square", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}
