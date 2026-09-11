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
fun HeartsApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(HeartsEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val seat = HeartsSeat.SOUTH

    fun humanAction(s: HeartsState, action: HeartsState.() -> HeartsState) {
        var cur = action(s)
        while (cur.currentPlayer != seat && !cur.done) {
            cur = HeartsEngine.aiPlay(cur, Random.Default)
        }
        state = cur
    }

    val passCards = state.pendingPassFrom == seat
    DetailScaffold(title = "hearts") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(text = "scores: ${state.scores.entries.joinToString { "${it.key.name.lowercase()}=${it.value}" }}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
            Spacer(Modifier.height(4.dp))
            // Opponents are summarised by hand size; 13 cards × 3 seats cannot
            // fit a 448dp row.
            BasicText(
                text = HeartsEngine.heartsSeats.filter { it != seat }
                    .joinToString("   ") { "${it.name.lowercase()} ${state.hands[it]?.size ?: 0}" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                state.trick.forEach { (s, c) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                        SolCardView(c, width = 30.dp, height = 40.dp) {}
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            PlayerHand(
                hand = state.hands[seat]!!,
                onCard = { c -> if (!state.done && state.currentPlayer == seat && !passCards) humanAction(state) { HeartsEngine.play(state, c) } },
                align = Alignment.CenterHorizontally,
                label = "you",
                interactive = state.currentPlayer == seat && !passCards,
            )
            if (passCards) {
                Spacer(Modifier.height(4.dp))
                PassPicker(hand = state.hands[seat]!!) { cards ->
                    state = HeartsEngine.completePassing(
                        HeartsEngine.passCards(state, seat, cards),
                        Random.Default,
                    )
                }
            } else if (state.done) {
                Spacer(Modifier.height(4.dp))
                BasicText(text = "round complete", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent))
            }
        }
    }
    if (state.done && !recorded) {
        recorded = true
        LaunchedEffect(Unit) {
            scope.launch { graph.games.record("hearts", state.scores.values.sum(), null) }
        }
    }
}
