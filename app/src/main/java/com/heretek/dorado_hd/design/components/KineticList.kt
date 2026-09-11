package com.heretek.dorado_hd.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoMotion
import com.heretek.dorado_hd.design.DoradoTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kinetic list with the Zune alphabet rail: faint letters alongside the list;
 * dragging the rail jumps directly, and tapping a letter pops up the full
 * A–Z index — "tap any of the letters ... and that pops up the full alphabet"
 * (canon §3.5). The giant letter overlay confirms both paths.
 */
@Composable
fun <T> KineticList(
    items: List<T>,
    key: (T) -> Any,
    letter: (T) -> Char?,
    rowContent: @Composable (item: T, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 0.dp,
    showAlphabet: Boolean = true,
    /** Snap the fling to the item grid (device `XuiTouchSnapToTarget`). */
    snap: Boolean = false,
) {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()

    val letters = remember(items) {
        items.mapNotNull { letter(it) }.distinct().sorted()
    }
    val present = remember(letters) { letters.toSet() }

    var indexOpen by remember { mutableStateOf(false) }
    var draggingLetter by remember { mutableStateOf<Char?>(null) }
    val overlayAlpha = remember { Animatable(0f) }

    fun jumpTo(letterChar: Char) {
        draggingLetter = letterChar
        scope.launch { overlayAlpha.snapTo(1f) }
        val index = items.indexOfFirst { letter(it) == letterChar }
        if (index >= 0) scope.launch { listState.scrollToItem(index) }
        scope.launch {
            delay(350)
            overlayAlpha.animateTo(0f, DoradoMotion.pivot())
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
            flingBehavior = if (snap) {
                androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)
            } else {
                rememberZuneFlingBehavior()
            },
        ) {
            itemsIndexed(
                items,
                key = { _, item -> key(item) },
            ) { index, item ->
                rowContent(item, index)
            }
        }

        if (showAlphabet && letters.isNotEmpty()) {
            AlphabetRail(
                present = present,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(32.dp),
                onLetterTap = { indexOpen = true },
                onLetterFocus = { letterChar ->
                    draggingLetter = letterChar
                    scope.launch { overlayAlpha.snapTo(1f) }
                    val index = items.indexOfFirst { letter(it) == letterChar }
                    if (index >= 0) scope.launch { listState.scrollToItem(index) }
                },
                onDragEnd = {
                    scope.launch {
                        delay(350)
                        overlayAlpha.animateTo(0f, DoradoMotion.pivot())
                    }
                },
            )

            AnimatedVisibility(
                visible = overlayAlpha.value > 0.01f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                BasicText(
                    text = draggingLetter?.toString() ?: "",
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontWeight = FontWeight.Light,
                        fontSize = 120.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.graphicsLayer { alpha = overlayAlpha.value },
                )
            }

            // The full A–Z index, canon §3.5.
            AnimatedVisibility(
                visible = indexOpen,
                enter = fadeIn(DoradoMotion.pivot()),
                exit = fadeOut(DoradoMotion.pivot()),
            ) {
                AlphabetIndex(
                    present = present,
                    onPick = { letterChar ->
                        indexOpen = false
                        jumpTo(letterChar)
                    },
                    onDismiss = { indexOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The full A–Z index popup. Letters without content stay dim. */
@Composable
private fun AlphabetIndex(
    present: Set<Char>,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val alphabet = remember { ('A'..'Z').toList() + '#' }
    Box(
        modifier
            .background(colors.background.copy(alpha = 0.96f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
        ) {
            alphabet.chunked(4).forEach { rowLetters ->
                Row {
                    rowLetters.forEach { letterChar ->
                        val isPresent = letterChar in present
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 44.dp)
                                .padding(2.dp)
                                .background(if (isPresent) colors.elevated else colors.background)
                                .border(
                                    width = 0.5.dp,
                                    color = if (isPresent) colors.border else colors.border.copy(alpha = 0.25f),
                                )
                                .clickable(enabled = isPresent) { onPick(letterChar) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = letterChar.toString(),
                                style = TextStyle(
                                    fontFamily = Selawik,
                                    fontWeight = if (isPresent) FontWeight.Normal else FontWeight.Light,
                                    fontSize = DoradoTokens.TYPE_CROSSBAR.sp,
                                    color = if (isPresent) colors.textPrimary else colors.textInactive,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetRail(
    present: Set<Char>,
    modifier: Modifier = Modifier,
    onLetterTap: () -> Unit,
    onLetterFocus: (Char) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val letters = remember(present) { present.sorted() }
    Column(
        modifier = modifier.pointerInput(letters) {
            if (letters.isEmpty()) return@pointerInput
            fun letterAt(y: Float): Char {
                val idx = ((y / size.height) * letters.size).toInt()
                return letters[idx.coerceIn(0, letters.size - 1)]
            }
            detectDragGestures(
                onDragStart = { offset -> onLetterFocus(letterAt(offset.y)) },
                onDrag = { change, _ ->
                    change.consume()
                    onLetterFocus(letterAt(change.position.y))
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
            )
        },
    ) {
        letters.forEach { letterChar ->
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .clickable { onLetterTap() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = letterChar.toString(),
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_ALPHABET.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** Uppercase first letter used by alphabet grouping. */
fun firstLetterOf(text: String): Char =
    text.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
