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
fun TexasHoldemApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PokerEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current

    LaunchedEffect(state) {
        if (!state.actor && state.phase != PokerPhase.DONE && state.phase != PokerPhase.SHOWDOWN) {
            delay(350)
            state = PokerEngine.applyAction(state, PokerEngine.aiAction(state, Random.Default))
        }
    }
    val terminal = state.phase == PokerPhase.DONE || state.phase == PokerPhase.SHOWDOWN
    if (terminal && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            val s = when (state.winner) {
                "player" -> 1
                "tie" -> 1
                else -> 0
            }
            scope.launch { graph.games.record("texasholdem", s, state.winner) }
        }
    }

    DetailScaffold(title = "texas hold 'em") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "pot ${state.pot} • you ${state.playerStack} • ai ${state.aiStack} • ${state.phase.name.lowercase()}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(text = "ai", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // AI hole cards stay face-down until showdown; ranks are no
                // longer truncated to unreadable two-char strings.
                val show = state.phase == PokerPhase.SHOWDOWN || state.phase == PokerPhase.DONE
                state.aiHole.forEach { c -> SolCardView(if (show) c else c.copy(faceUp = false), width = 26.dp, height = 34.dp) {} }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                state.community.forEach { SolCardView(it, width = 26.dp, height = 34.dp) {} }
                if (state.community.isEmpty()) {
                    BasicText(text = "community", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(text = "you", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                state.playerHole.forEach { SolCardView(it, width = 26.dp, height = 34.dp) {} }
            }
            Spacer(Modifier.height(6.dp))
            if (terminal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = when (state.winner) {
                            "tie" -> "tie — split pot"
                            "player" -> "you win"
                            else -> "ai wins"
                        },
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(text = "new hand", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { state = PokerEngine.newGame(); recorded = false }) })
                }
            } else if (state.actor) {
                val legal = PokerEngine.legalActions(state)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    legal.forEach { a ->
                        BasicText(text = a.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                            modifier = Modifier.pointerInput(a) { detectTapGestures(onTap = { state = PokerEngine.applyAction(state, a) }) })
                    }
                }
            } else {
                BasicText(text = "ai thinking…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
            }
        }
    }
}
