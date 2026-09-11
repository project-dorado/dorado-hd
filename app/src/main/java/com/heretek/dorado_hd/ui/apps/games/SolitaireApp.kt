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
fun SolitaireApp() {
    val colors = LocalDoradoColors.current
    var piles by remember { mutableStateOf(SolitaireEngine.newGame()) }
    val tabs = piles.subList(0, 7)
    val stock = piles[7]; val waste = piles[8]; val founds = piles.subList(9, 13)

    /** Try to send [top] to a foundation or another column. */
    fun placeTop(fromIndex: Int, from: Pile, top: SolCard) {
        val fi = founds.indexOfFirst { f -> SolitaireEngine.toFoundation(top, f.cards.lastOrNull()) }
        if (fi >= 0) {
            val newPiles = piles.toMutableList()
            newPiles[9 + fi] = founds[fi].copy(cards = founds[fi].cards + top)
            newPiles[fromIndex] = from.copy(cards = SolitaireEngine.exposeTop(from.cards.dropLast(1)))
            piles = newPiles
            return
        }
        for (j in tabs.indices) {
            if (j == fromIndex) continue
            val dest = tabs[j]
            val destTop = dest.cards.lastOrNull()?.takeIf { it.faceUp }
            if (SolitaireEngine.legalMove(top, destTop)) {
                val newPiles = piles.toMutableList()
                newPiles[j] = dest.copy(cards = dest.cards + top)
                newPiles[fromIndex] = from.copy(cards = SolitaireEngine.exposeTop(from.cards.dropLast(1)))
                piles = newPiles
                return
            }
        }
    }

    fun tapTab(c: Int) {
        val tab = tabs[c]
        val top = tab.cards.lastOrNull() ?: return
        if (!top.faceUp) {
            // Flip the exposed top card.
            val newPiles = piles.toMutableList()
            newPiles[c] = tab.copy(cards = tab.cards.dropLast(1) + top.copy(faceUp = true))
            piles = newPiles
            return
        }
        placeTop(c, tab, top)
    }

    fun tapWaste() {
        val top = waste.cards.lastOrNull()?.takeIf { it.faceUp } ?: return
        placeTop(8, waste, top)
    }

    fun tapStock() {
        if (stock.cards.isEmpty()) {
            // recycle waste → stock
            val recyc = waste.cards.reversed()
            piles = piles.toMutableList().also {
                it[7] = stock.copy(cards = recyc.map { c -> c.copy(faceUp = false) })
                it[8] = waste.copy(cards = emptyList())
            }
            return
        }
        val drawn = stock.cards.last()
        piles = piles.toMutableList().also {
            it[7] = stock.copy(cards = stock.cards.dropLast(1))
            it[8] = waste.copy(cards = waste.cards + drawn.copy(faceUp = true))
        }
    }

    DetailScaffold(title = "solitaire") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeText(
                    text = if (stock.cards.isEmpty()) "recycle" else "deal",
                    color = colors.accent,
                    onClick = { tapStock() },
                )
                Spacer(Modifier.width(10.dp))
                // Waste is a real, playable pile now.
                if (waste.cards.lastOrNull() != null) {
                    SolCardView(waste.cards.last(), width = 26.dp, height = 36.dp) { tapWaste() }
                } else {
                    BasicText(
                        text = "waste",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { c, tab ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.elevated)
                            .padding(1.dp),
                        verticalArrangement = Arrangement.spacedBy((-20).dp),
                    ) {
                        tab.cards.forEach { card ->
                            SolCardView(card, width = 28.dp, height = 38.dp) { tapTab(c) }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                founds.forEach { f ->
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 38.dp)
                            .background(colors.elevated)
                            .padding(1.dp),
                    ) {
                        val top = f.cards.lastOrNull()
                        if (top != null) SolCardView(top, width = 26.dp, height = 36.dp) {}
                        else BasicText(
                            text = if (f.name.contains("SPADE")) "♠" else if (f.name.contains("HEART")) "♥" else if (f.name.contains("CLUB")) "♣" else "♦",
                            style = TextStyle(fontFamily = Selawik, fontSize = 10.sp, color = colors.textSecondary),
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}
