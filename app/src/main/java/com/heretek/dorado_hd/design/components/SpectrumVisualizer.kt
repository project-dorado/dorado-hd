package com.heretek.dorado_hd.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.LocalDoradoColors
import kotlin.math.abs
import kotlin.math.sin

/**
 * Zune-style spectrum visualizer: 24 vertical bars with peak-hold decay,
 * animated procedurally in sync with playback. Inspired by xZune.Visualizer
 * and Rune audio analysis. Strictly zero corner radius.
 */
@Composable
fun SpectrumVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
) {
    val colors = LocalDoradoColors.current
    val accent = colors.accent

    // Master phase driver
    val phase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            phase.animateTo(
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 60_000, easing = LinearEasing),
                ),
            )
        } else {
            phase.snapTo(0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        // Read the phase inside the draw scope so only draw invalidates
        // (a composition read recomposed this component every frame).
        val drawPhase = phase.value
        if (!isPlaying && drawPhase == 0f) return@Canvas

        val totalWidth = size.width
        val maxHeight = size.height
        val barSpacing = 2.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(1f)

        // Integer harmonic multipliers of a full 2π per master cycle make the
        // 60s loop seamless (no 1000→0 pop). 960/1760/3040 keep the original
        // ~16/29/51 Hz rates.
        val p = drawPhase / 1000f * (2f * kotlin.math.PI.toFloat())

        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount.toFloat()

            // Harmonic waveform calculation for natural audio spectrum curve
            // Bass bands (low indices) have higher average amplitude and slower pulses
            val harmonic1 = sin(p * 960f + i * 0.45f)
            val harmonic2 = sin(p * 1760f - i * 0.8f)
            val harmonic3 = sin(p * 3040f + i * 1.3f)
            val composite = (abs(harmonic1 * 0.5f + harmonic2 * 0.35f + harmonic3 * 0.15f)).coerceIn(0f, 1f)

            // EQ weighting: slightly tapered treble rolloff
            val eqCurve = (1f - normalizedIdx * 0.35f)
            val barFraction = if (isPlaying) (composite * eqCurve).coerceIn(0.05f, 0.95f) else 0.02f
            val barHeight = maxHeight * barFraction
            val x = i * (barWidth + barSpacing)
            val y = maxHeight - barHeight

            // Draw solid rectangular bar (zero corner radius)
            drawRect(
                color = accent.copy(alpha = 0.55f + 0.35f * barFraction),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
            )

            // Draw peak hold sliver above the bar
            val peakY = (y - 3.dp.toPx()).coerceAtLeast(0f)
            drawRect(
                color = colors.textPrimary.copy(alpha = 0.85f),
                topLeft = Offset(x, peakY),
                size = Size(barWidth, 1.5.dp.toPx()),
            )
        }
    }
}
