package com.heretek.dorado_hd.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoMotion
import kotlinx.coroutines.delay

/**
 * Square album art tile. No corner radius — a hard square with a hairline
 * border on the placeholder, exactly like the Zune collection grid.
 */
@Composable
fun AlbumArt(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier = modifier
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .clipToBounds(),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Staggered Metro cascade entrance: each item fades/slides in slightly after
 * the previous one (15-25 ms stagger per the design tokens).
 */
@Composable
fun StaggerEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = DoradoMotion.Decelerate),
        label = "stagger",
    )
    LaunchedEffect(Unit) {
        delay(index * DoradoMotion.STAGGER_MS)
        appeared = true
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationX = (1f - progress) * 24f
        },
    ) {
        content()
    }
}
