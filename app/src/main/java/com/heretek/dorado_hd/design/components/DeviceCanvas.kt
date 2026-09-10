package com.heretek.dorado_hd.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import kotlin.math.min

/**
 * The 480x272 Zune HD canvas.
 *
 * In device mode the entire UI is laid out at the authentic design size and
 * scaled to fit (letterboxed on matte black) — hold the phone in landscape
 * for the full-device feel. In adaptive mode the same components reflow to
 * the natural screen size.
 */
@Composable
fun DeviceCanvas(
    deviceMode: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (canvasWidth: Dp, canvasHeight: Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LocalDoradoColors.current.background),
    ) {
        if (!deviceMode) {
            // Adaptive mode: the Zune type/geometry ramp is authored for the
            // 480x272 canvas, so rendering it 1:1 as dp looks tiny on a phone.
            // Scale the whole composition's density so the type carries the
            // same visual weight it does on the device (~2x on a phone).
            val base = LocalDensity.current
            val scale = (min(maxWidth.value, maxHeight.value) / DoradoTokens.CANVAS_HEIGHT.toFloat() * 1.2f)
                .coerceIn(1.4f, 2.1f)
            val density = remember(base, scale) {
                Density(density = base.density * scale, fontScale = base.fontScale)
            }
            CompositionLocalProvider(LocalDensity provides density) {
                content(maxWidth, maxHeight)
            }
        } else {
            val isPortrait = maxWidth < maxHeight
            val targetWidth = if (isPortrait) DoradoTokens.CANVAS_HEIGHT.dp else DoradoTokens.CANVAS_WIDTH.dp
            val targetHeight = if (isPortrait) DoradoTokens.CANVAS_WIDTH.dp else DoradoTokens.CANVAS_HEIGHT.dp
            val scale = min(
                maxWidth.value / targetWidth.value,
                maxHeight.value / targetHeight.value,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(targetWidth, targetHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clipToBounds(),
            ) {
                content(targetWidth, targetHeight)
            }
        }
    }
}
