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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EdgeText(text: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = Selawik,
            fontSize = DoradoTokens.TYPE_LIST.sp,
            color = color,
        ),
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
fun SolCardView(card: SolCard, width: Dp = DoradoTokens.CARD_W.dp, height: Dp = DoradoTokens.CARD_H.dp, onClick: () -> Unit = {}) {
    val colors = LocalDoradoColors.current
    val bg = if (card.faceUp) Color.White else colors.tile
    // Black suits must be dark on the white face; using Color.White here made
    // face-up clubs/spades invisible.
    val fg = if (card.suit.red) colors.accent else Color.Black
    Box(
        Modifier
            .size(width = width, height = height)
            .background(bg)
            .padding(1.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        if (card.faceUp) {
            BasicText(
                text = "${rankLabel(card.rank)}${suitGlyph(card.suit)}",
                style = TextStyle(fontFamily = Selawik, fontSize = 10.sp, color = fg),
            )
        }
    }
}

internal fun rankLabel(rank: Int): String = when (rank) {
    1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> rank.toString()
}

internal fun suitGlyph(suit: SolSuit): String = when (suit) {
    SolSuit.SPADE -> "♠"; SolSuit.HEART -> "♥"; SolSuit.CLUB -> "♣"; SolSuit.DIAMOND -> "♦"
}


@Composable
internal fun PlayerHand(hand: List<SolCard>, onCard: (SolCard) -> Unit = {}, align: Alignment.Horizontal, label: String, interactive: Boolean = true, faceDown: Boolean = false) {
    Column(horizontalAlignment = align) {
        BasicText(text = label, style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = LocalDoradoColors.current.textSecondary))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hand.forEach { c ->
                val shown = if (faceDown) c.copy(faceUp = false) else c
                SolCardView(shown, width = 26.dp, height = 34.dp) { if (interactive) onCard(c) }
            }
        }
    }
}

@Composable
internal fun PassPicker(hand: List<SolCard>, onConfirm: (List<SolCard>) -> Unit) {
    val picked = remember { mutableStateListOf<SolCard>() }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            hand.forEach { c ->
                val selected = c in picked
                Box(modifier = Modifier.pointerInput(c) { detectTapGestures(onTap = { if (selected) picked.remove(c) else if (picked.size < 3) picked.add(c) }) }) {
                    SolCardView(if (selected) c.copy(faceUp = true) else c.copy(faceUp = false), width = 26.dp, height = 34.dp) {}
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        BasicText(text = "pass ${picked.size}/3 — ${if (picked.size == 3) "tap confirm" else "pick three"}", style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = LocalDoradoColors.current.accent),
            modifier = Modifier.pointerInput(picked.toList()) { detectTapGestures(onTap = { if (picked.size == 3) onConfirm(picked.toList()) }) })
    }
}
