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

enum class SolSuit {
    SPADE, HEART, CLUB, DIAMOND;
    val red: Boolean get() = this == HEART || this == DIAMOND
}
data class SolCard(val suit: SolSuit, val rank: Int, val faceUp: Boolean = false) {
    override fun toString(): String = "${suit.name.take(1)}${rank}"
}
data class Pile(
    val name: String,
    val cards: List<SolCard>,
)

object SolitaireEngine {
    fun newGame(rng: Random = Random.Default): List<Pile> {
        val deck = (SolSuit.values()).flatMap { s -> (1..13).map { r -> SolCard(s, r, false) } }.shuffled(rng)
        val tabs = mutableListOf<Pile>()
        var idx = 0
        for (c in 0 until 7) {
            val size = c + 1
            val cards = deck.subList(idx, idx + size).mapIndexed { i, card ->
                if (i == size - 1) card.copy(faceUp = true) else card
            }
            tabs += Pile("tab$c", cards)
            idx += size
        }
        val stock = Pile("stock", deck.subList(idx, deck.size))
        val waste = Pile("waste", emptyList())
        val founds = SolSuit.values().map { Pile("found${it.name}", emptyList()) }
        return tabs + stock + waste + founds
    }

    /** Face-up the newly exposed card after the top card leaves a column. */
    fun exposeTop(cards: List<SolCard>): List<SolCard> {
        val last = cards.lastOrNull() ?: return cards
        return if (!last.faceUp) cards.dropLast(1) + last.copy(faceUp = true) else cards
    }

    fun legalMove(card: SolCard, onto: SolCard?): Boolean {
        if (card.faceUp && onto != null && onto.faceUp && card.suit.red != onto.suit.red && card.rank == onto.rank - 1) return true
        return card.faceUp && onto == null && card.rank == 13 // empty column accepts King
    }

    fun toFoundation(card: SolCard, foundationTop: SolCard?): Boolean {
        if (!card.faceUp) return false
        return if (foundationTop == null) card.rank == 1
        else card.suit == foundationTop.suit && card.rank == foundationTop.rank + 1
    }
}

/* ============================================================ */
/*                            Sudoku                              */
/* ============================================================ */
