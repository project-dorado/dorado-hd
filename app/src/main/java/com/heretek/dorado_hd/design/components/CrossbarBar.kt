package com.heretek.dorado_hd.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens

/**
 * The Zune crossbar: lowercase pivot labels across the top of the screen,
 * cropped at the right edge (canon §2 signature). Active pivot is white; the
 * rest dim to 40%. Tapping selects; horizontal content drags page between
 * pivots (the screen wires a pager to [selected]).
 */
@Composable
fun CrossbarBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val requesters = remember(labels.size) { List(labels.size) { BringIntoViewRequester() } }

    LaunchedEffect(selected) {
        requesters.getOrNull(selected)?.bringIntoView()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DoradoTokens.CROSSBAR_HEIGHT.dp)
            .horizontalScroll(rememberScrollState())
            .padding(start = DoradoTokens.CROSSBAR_LEAD.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val target = if (isSelected) colors.textPrimary else colors.textInactive
            val color by animateColorAsState(target, label = "crossbar")
            // EdgeCropText implements the device's right-edge signature clipping.
            // The clickable fills the full 34dp bar and the inter-pivot gap is
            // outside it, so the gap no longer selects the previous pivot.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .bringIntoViewRequester(requesters[index])
                    .clickable(onClick = { onSelect(index) })
                    .padding(end = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                EdgeCropText(
                    text = label,
                    fontSize = DoradoTokens.TYPE_CROSSBAR.dp,
                    color = color,
                    fontWeight = if (isSelected) FontWeight.Normal else FontWeight.Light,
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(colors.border),
    )
}
