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
fun SpadesApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SpadesEngine.newGame()) }
    var recorded by remember { mutableStateOf(false) }
    val colors = LocalDoradoColors.current
    val seat = SpadesSeat.SOUTH

    // The AI seats drive themselves on every state change (the old UI only
    // advanced when the human tapped something, so a fresh deal stalled).
    LaunchedEffect(state) {
        val cur = state
        if (cur.done || cur.currentPlayer == seat) return@LaunchedEffect
        delay(300)
        state = if (!cur.biddingDone) {
            SpadesEngine.bid(cur, (1..5).random())
        } else {
            val legal = SpadesEngine.legalPlays(cur, cur.currentPlayer)
            if (legal.isEmpty()) cur else SpadesEngine.play(cur, legal.random())
        }
    }

    LaunchedEffect(state.done) {
        val cur = state
        if (cur.done && !recorded) {
            recorded = true
            val scored = SpadesEngine.scoreRound(cur)
            state = scored
            scope.launch {
                graph.games.record(
                    "spades",
                    scored.score.first,
                    "S-N ${scored.score.first} · W-E ${scored.score.second}",
                )
            }
        }
    }

    DetailScaffold(title = "spades") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "score S-N ${state.score.first} · W-E ${state.score.second}   bags ${state.teamBags.first}/${state.teamBags.second}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = SpadesSeat.values().joinToString("   ") { "${it.name.take(1).lowercase()}:${state.bids[it] ?: 0}" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            when {
                !state.biddingDone -> {
                    if (state.currentPlayer == seat) {
                        BasicText(text = "your bid:", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..13).forEach { n ->
                                BasicText(text = "$n", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                    modifier = Modifier.pointerInput(n) { detectTapGestures(onTap = { state = SpadesEngine.bid(state, n) }) })
                            }
                        }
                    } else {
                        BasicText(text = "ai bidding…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        state.trick.forEach { (s, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(s.name.lowercase(), style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary))
                                SolCardView(c, width = 30.dp, height = 40.dp) {}
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (state.done) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(text = "round complete", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent), modifier = Modifier.weight(1f))
                            BasicText(text = "new round", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        state = SpadesEngine.newGame().copy(score = state.score, teamBags = state.teamBags)
                                        recorded = false
                                    })
                                })
                        }
                    } else if (state.currentPlayer == seat) {
                        val legal = SpadesEngine.legalPlays(state, seat)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            legal.forEach { c -> SolCardView(c, width = 26.dp, height = 34.dp) { state = SpadesEngine.play(state, c) } }
                        }
                    } else {
                        BasicText(text = "ai playing…", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "your hand (${state.hands[seat]?.size ?: 0})",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                state.hands[seat]!!.forEach { c -> SolCardView(c.copy(faceUp = true), width = 26.dp, height = 34.dp) {} }
            }
        }
    }
}
