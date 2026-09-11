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
fun ReversiApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(ReversiEngine.newBoard()) }
    var player by remember { mutableStateOf(ReversiEngine.BLACK) }
    var recorded by remember { mutableStateOf(false) }
    val legal = remember(board, player) { ReversiEngine.legalMoves(board, player) }
    val gameOver = remember(board) {
        ReversiEngine.legalMoves(board, ReversiEngine.BLACK).isEmpty() &&
            ReversiEngine.legalMoves(board, ReversiEngine.WHITE).isEmpty()
    }

    fun apply(r: Int, c: Int) {
        if ((r to c) !in legal) return
        val nextBoard = ReversiEngine.apply(board, r, c, player)
        board = nextBoard
        val opponent = if (player == ReversiEngine.BLACK) ReversiEngine.WHITE else ReversiEngine.BLACK
        // Opponent passes when it has no legal move; board then returns to us.
        player = if (ReversiEngine.legalMoves(nextBoard, opponent).isNotEmpty()) opponent else player
    }

    LaunchedEffect(board, player) {
        if (player == ReversiEngine.WHITE && !gameOver) {
            delay(250)
            // Simple AI: pick the move with the most flips; off the main thread.
            val best = withContext(Dispatchers.Default) {
                legal.maxByOrNull { (r, c) ->
                    ReversiEngine.score(ReversiEngine.apply(board, r, c, player)).second
                }
            } ?: return@LaunchedEffect
            apply(best.first, best.second)
        }
    }

    val (bs, ws) = ReversiEngine.score(board)
    LaunchedEffect(gameOver) {
        if (gameOver && !recorded) {
            recorded = true
            val score = when {
                bs > ws -> 1
                ws > bs -> 0
                else -> 1 // draw counts as a non-loss
            }
            scope.launch { graph.games.record("reversi", score, "B$bs W$ws") }
        }
    }

    DetailScaffold(title = "reversi") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = when {
                        gameOver && bs > ws -> "you win $bs—$ws"
                        gameOver && ws > bs -> "ai wins $bs—$ws"
                        gameOver -> "draw $bs—$ws"
                        player == ReversiEngine.BLACK -> "you $bs — ai $ws   your move"
                        else -> "you $bs — ai $ws   ai…"
                    },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (gameOver) colors.accent else colors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
                if (gameOver) {
                    EdgeText(text = "new game", color = colors.accent, onClick = {
                        board = ReversiEngine.newBoard()
                        player = ReversiEngine.BLACK
                        recorded = false
                    })
                }
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Account for the 1dp gaps between cells.
                val cell = (minOf(maxWidth, maxHeight) - 9.dp) / 8
                Column {
                    for (r in 0 until 8) {
                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            for (c in 0 until 8) {
                                val v = board[r][c]
                                val isLegal = (r to c) in legal && player == ReversiEngine.BLACK && !gameOver
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(colors.elevated)
                                        .pointerInput(r to c) { detectTapGestures(onTap = { apply(r, c) }) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v != ReversiEngine.EMPTY) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(0.7f)
                                                .fillMaxHeight(0.7f)
                                                .background(if (v == ReversiEngine.BLACK) Color.Black else Color.White),
                                        )
                                    } else if (isLegal) {
                                        BasicText(text = "·", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}
