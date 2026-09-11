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
fun ChessApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ChessEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val moves = remember(state) { ChessEngine.legalMoves(state) }
    val targets = remember(selected, moves) { selected?.let { sel -> moves.filter { it.fromR == sel.first && it.fromC == sel.second }.map { it.toR to it.toC } } ?: emptyList() }
    // One reusable native paint for piece glyphs (letters, so pawns/knights/
    // kings are distinguishable — the old UI drew identical circles).
    val piecePaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }

    fun click(r: Int, c: Int) {
        if (state.status.isNotEmpty()) return
        if (selected == null) {
            val p = state.board[r][c]
            if (p?.color == state.turn) selected = r to c
        } else {
            val move = moves.firstOrNull { it.fromR == selected!!.first && it.fromC == selected!!.second && it.toR == r && it.toC == c }
            if (move != null) {
                state = ChessEngine.apply(state, move)
                selected = null
            } else {
                val p = state.board[r][c]
                selected = if (p?.color == state.turn) r to c else null
            }
        }
    }

    if (state.turn == ChessColor.BLACK && state.status.isEmpty()) {
        LaunchedEffect(state) {
            delay(80)
            // Search off the main thread; depth 2 keeps the position sane
            // without ANR-length pauses.
            val mv = withContext(Dispatchers.Default) { ChessEngine.bestMove(state, depth = 2) }
            if (mv != null) state = ChessEngine.apply(state, mv)
        }
    }
    val terminal = state.status.startsWith("checkmate") || state.status == "stalemate"
    if (terminal && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            val score = if (state.status.startsWith("checkmate black")) 1 else 0
            scope.launch { graph.games.record("chess", score, state.status) }
        }
    }

    DetailScaffold(title = "chess") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = if (state.status.isNotEmpty()) state.status else if (state.turn == ChessColor.WHITE) "your move" else "ai thinking…",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            val selectedSq = selected
            val targetSquares = targets
            val chColors = colors
            val lightSquare = colors.tile
            val darkSquare = colors.background
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
                            click(r, c)
                        })
                    },
            ) {
                val s = min(size.width, size.height)
                val cell = s / 8
                val ox = (size.width - s) / 2
                val oy = (size.height - s) / 2
                for (r in 0..7) for (c in 0..7) {
                    val light = (r + c) % 2 == 0
                    drawRect(
                        color = if (selectedSq == r to c) chColors.accent
                        else if ((r to c) in targetSquares) chColors.tilePressed
                        else if (light) lightSquare else darkSquare,
                        topLeft = Offset(ox + c * cell, oy + r * cell), size = Size(cell, cell),
                    )
                }
                val paint = piecePaint
                for (r in 0..7) for (c in 0..7) {
                    val p = state.board[r][c] ?: continue
                    paint.color = if (p.color == ChessColor.WHITE) android.graphics.Color.WHITE else android.graphics.Color.GRAY
                    paint.textSize = cell * 0.62f
                    val cx = ox + c * cell + cell / 2f
                    val cy = oy + r * cell + cell / 2f - (paint.ascent() + paint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(chessGlyph(p.type), cx, cy, paint)
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you are white — tap a piece, then a target", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
        }
    }
}

private fun chessGlyph(type: ChessPieceType): String = when (type) {
    ChessPieceType.P -> "P"
    ChessPieceType.N -> "N"
    ChessPieceType.B -> "B"
    ChessPieceType.R -> "R"
    ChessPieceType.Q -> "Q"
    ChessPieceType.K -> "K"
}
