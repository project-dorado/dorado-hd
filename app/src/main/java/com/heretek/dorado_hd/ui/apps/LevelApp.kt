package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/* ============================== Engine ============================== */

/**
 * One instrument pass: signed 0.1° readouts plus the three bubble offsets
 * (round ±50, vertical tube ±142, horizontal tube ±67) and squish scales.
 */
data class LevelState(
    val rollDeg: Double = 0.0,
    val pitchDeg: Double = 0.0,
    val bubbleX: Double = 0.0,
    val bubbleY: Double = 0.0,
    val verticalOffset: Double = 0.0,
    val horizontalOffset: Double = 0.0,
    val verticalJitter: Double = 0.0,
    val horizontalJitter: Double = 0.0,
    val bubbleScaleX: Double = 1.0,
    val bubbleScaleY: Double = 1.0,
    val savedRoll: Double? = null,
    val savedPitch: Double? = null,
    val savedBubbleX: Double = 0.0,
    val savedBubbleY: Double = 0.0,
    val hasMemory: Boolean = false,
    val available: Boolean = true,
)

/**
 * Pure re-derivation of the device Level app's accelerometer transform
 * (Level!Game1.Update): 90° per g, clamped linear bubble travel, squish as the
 * device approaches level, and a peak-hold memory.
 */
object LevelEngine {
    const val DEGREES_PER_G = 90.0
    const val ROUND_TRAVEL = 50.0
    const val VERTICAL_TRAVEL = 142.0
    const val HORIZONTAL_TRAVEL = 67.0
    const val MIN_BUBBLE_SCALE = 0.5
    const val VERTICAL_JITTER_PX = 12.0
    const val HORIZONTAL_JITTER_MIN_PX = 6.0
    const val HORIZONTAL_JITTER_MAX_PX = 12.0

    fun fromAccel(ax: Double, ay: Double, az: Double = 0.0): LevelState {
        val roll = DEGREES_PER_G * ax
        val pitch = DEGREES_PER_G * ay
        return LevelState(
            rollDeg = roll,
            pitchDeg = pitch,
            bubbleX = (ROUND_TRAVEL * ax).coerceIn(-ROUND_TRAVEL, ROUND_TRAVEL),
            bubbleY = (ROUND_TRAVEL * ay).coerceIn(-ROUND_TRAVEL, ROUND_TRAVEL),
            verticalOffset = (VERTICAL_TRAVEL * ay).coerceIn(-VERTICAL_TRAVEL, VERTICAL_TRAVEL),
            horizontalOffset = (-HORIZONTAL_TRAVEL * ax).coerceIn(-HORIZONTAL_TRAVEL, HORIZONTAL_TRAVEL),
            verticalJitter = (jitter(ay, ax) * 2.0 - 1.0) * VERTICAL_JITTER_PX,
            horizontalJitter = horizontalJitter(ax, ay),
            bubbleScaleX = (1.0 - abs(ax * 0.5)).coerceIn(MIN_BUBBLE_SCALE, 1.0),
            bubbleScaleY = (1.0 - abs(ay * 1.5)).coerceIn(MIN_BUBBLE_SCALE, 1.0),
        )
    }

    /** Converts the shared tilt helper's roll/pitch into the same mapping. */
    fun fromTilt(rollDeg: Double, pitchDeg: Double): LevelState {
        val r = Math.toRadians(rollDeg)
        val p = Math.toRadians(pitchDeg)
        return fromAccel(ax = -sin(p), ay = sin(r), az = cos(r) * cos(p))
    }

    /** Device readout convention: signed one-decimal degrees, `#0.0°`. */
    fun formatDegrees(value: Double): String = "%.1f\u00B0".format(Locale.US, value)

    /** Freeze the current reading as the red memory bubble. */
    fun snapshot(state: LevelState): LevelState = state.copy(
        savedRoll = state.rollDeg,
        savedPitch = state.pitchDeg,
        savedBubbleX = state.bubbleX,
        savedBubbleY = state.bubbleY,
        hasMemory = true,
    )

    /** Clear the memory bubble. */
    fun clear(state: LevelState): LevelState = state.copy(
        savedRoll = null,
        savedPitch = null,
        savedBubbleX = 0.0,
        savedBubbleY = 0.0,
        hasMemory = false,
    )

    /** Graceful no-sensor state. */
    fun absent(): LevelState = LevelState(available = false)

    private fun jitter(a: Double, b: Double): Double {
        val v = sin(a * 12.9898 + b * 78.233) * 43758.5453
        return v - floor(v)
    }

    private fun horizontalJitter(ax: Double, ay: Double): Double {
        val magnitude = HORIZONTAL_JITTER_MIN_PX +
            (HORIZONTAL_JITTER_MAX_PX - HORIZONTAL_JITTER_MIN_PX) * jitter(ax, ay)
        val sign = if (jitter(ay, 3.7) >= 0.5) 1.0 else -1.0
        return sign * magnitude
    }
}

/* ============================== UI ============================== */

@Composable
fun LevelApp() {
    var about by remember { mutableStateOf(false) }
    if (about) {
        LevelAbout(onBack = { about = false })
        return
    }
    val colors = LocalDoradoColors.current
    val tilt = rememberTilt()
    val live = remember(tilt.value) {
        val t = tilt.value
        if (t.available) LevelEngine.fromTilt(t.rollDeg.toDouble(), t.pitchDeg.toDouble()) else LevelEngine.absent()
    }
    var memory by remember { mutableStateOf<LevelState?>(null) }

    DetailScaffold(title = "level") {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp)) {
                EdgeCropText(
                    text = if (live.available) "x ${LevelEngine.formatDegrees(live.rollDeg)}" else "x --.-",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(end = 18.dp),
                )
                EdgeCropText(
                    text = if (live.available) "y ${LevelEngine.formatDegrees(live.pitchDeg)}" else "y --.-",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(end = 18.dp),
                )
                val ghost = memory
                if (ghost != null && ghost.hasMemory) {
                    EdgeCropText(
                        text = "mem ${LevelEngine.formatDegrees(ghost.savedRoll ?: 0.0)} / ${
                            LevelEngine.formatDegrees(ghost.savedPitch ?: 0.0)
                        }",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                    )
                }
            }
            if (!live.available) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    EdgeCropText(
                        text = "no accelerometer on this device",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LevelCanvas(
                    live = live,
                    memory = memory,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
            Row(Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)) {
                EdgeCropText(
                    text = "save",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { memory = LevelEngine.snapshot(live) },
                            onLongClick = {},
                        )
                        .padding(end = 18.dp),
                )
                if (memory?.hasMemory == true) {
                    EdgeCropText(
                        text = "clear",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { memory = LevelEngine.clear(memory ?: live) },
                                onLongClick = {},
                            )
                            .padding(end = 18.dp),
                    )
                }
                EdgeCropText(
                    text = "about",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.combinedClickable(onClick = { about = true }, onLongClick = {}),
                )
            }
        }
    }
}

@Composable
private fun LevelCanvas(
    live: LevelState,
    memory: LevelState?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val tube = 28.dp.toPx()
        val bubble = 16.dp.toPx()

        // Horizontal X tube across the top.
        val hCenterY = h * 0.16f
        val hHalf = w * 0.40f
        drawRect(
            color = colors.tile,
            topLeft = Offset(w / 2f - hHalf, hCenterY - tube / 2f),
            size = Size(hHalf * 2f, tube),
        )
        drawRect(
            color = colors.border,
            topLeft = Offset(w / 2f - hHalf, hCenterY - tube / 2f),
            size = Size(hHalf * 2f, tube),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawLine(colors.border, Offset(w / 2f, 0f), Offset(w / 2f, hCenterY - tube / 2f), 1f)
        val hBubbleX = (w / 2f + (live.horizontalOffset / LevelEngine.HORIZONTAL_TRAVEL).toFloat() * hHalf * 0.82f)
            .coerceIn(w / 2f - hHalf + bubble, w / 2f + hHalf - bubble)
        val hBubbleY = hCenterY + live.horizontalJitter.toFloat()
        if (memory?.hasMemory == true) {
            val gx = (w / 2f + (memory.horizontalOffset / LevelEngine.HORIZONTAL_TRAVEL).toFloat() * hHalf * 0.82f)
                .coerceIn(w / 2f - hHalf + bubble, w / 2f + hHalf - bubble)
            drawRect(
                color = colors.accent.copy(alpha = 0.4f),
                topLeft = Offset(gx - bubble / 2f, hCenterY + memory.horizontalJitter.toFloat() - bubble / 2f),
                size = Size(bubble, bubble * live.bubbleScaleY.toFloat()),
            )
        }
        drawRect(
            color = colors.accent,
            topLeft = Offset(hBubbleX - bubble / 2f, hBubbleY - bubble / 2f),
            size = Size(bubble, bubble * live.bubbleScaleY.toFloat()),
        )

        // Vertical Y tube down the left.
        val vCenterX = w * 0.13f
        val vCenterY = h * 0.62f
        val vHalf = h * 0.28f
        drawRect(
            color = colors.tile,
            topLeft = Offset(vCenterX - tube / 2f, vCenterY - vHalf),
            size = Size(tube, vHalf * 2f),
        )
        drawRect(
            color = colors.border,
            topLeft = Offset(vCenterX - tube / 2f, vCenterY - vHalf),
            size = Size(tube, vHalf * 2f),
            style = Stroke(width = 1.dp.toPx()),
        )
        val vBubbleY = (vCenterY + (live.verticalOffset / LevelEngine.VERTICAL_TRAVEL).toFloat() * vHalf * 0.82f)
            .coerceIn(vCenterY - vHalf + bubble, vCenterY + vHalf - bubble)
        val vBubbleX = vCenterX + live.verticalJitter.toFloat()
        if (memory?.hasMemory == true) {
            val gy = (vCenterY + (memory.verticalOffset / LevelEngine.VERTICAL_TRAVEL).toFloat() * vHalf * 0.82f)
                .coerceIn(vCenterY - vHalf + bubble, vCenterY + vHalf - bubble)
            drawRect(
                color = colors.accent.copy(alpha = 0.4f),
                topLeft = Offset(vCenterX + memory.verticalJitter.toFloat() - bubble / 2f, gy - bubble / 2f),
                size = Size(bubble * live.bubbleScaleX.toFloat(), bubble),
            )
        }
        drawRect(
            color = colors.accent,
            topLeft = Offset(vBubbleX - bubble / 2f, vBubbleY - bubble / 2f),
            size = Size(bubble * live.bubbleScaleX.toFloat(), bubble),
        )

        // Round bullseye for both axes.
        val bcx = w * 0.62f
        val bcy = h * 0.62f
        val radius = min(w * 0.30f, h * 0.32f)
        drawCircle(colors.tile, radius, Offset(bcx, bcy))
        drawCircle(colors.border, radius, Offset(bcx, bcy), style = Stroke(width = 1.dp.toPx()))
        drawCircle(colors.border, radius * 0.5f, Offset(bcx, bcy), style = Stroke(width = 1f))
        drawLine(colors.border, Offset(bcx - radius, bcy), Offset(bcx + radius, bcy), 1f)
        drawLine(colors.border, Offset(bcx, bcy - radius), Offset(bcx, bcy + radius), 1f)
        if (memory?.hasMemory == true) {
            val gx = bcx + (memory.bubbleX / LevelEngine.ROUND_TRAVEL).toFloat() * radius * 0.78f
            val gy = bcy + (memory.bubbleY / LevelEngine.ROUND_TRAVEL).toFloat() * radius * 0.78f
            drawCircle(colors.accent.copy(alpha = 0.4f), bubble * 0.75f, Offset(gx, gy))
        }
        val liveX = bcx + (live.bubbleX / LevelEngine.ROUND_TRAVEL).toFloat() * radius * 0.78f
        val liveY = bcy + (live.bubbleY / LevelEngine.ROUND_TRAVEL).toFloat() * radius * 0.78f
        drawCircle(colors.accent, bubble * 0.75f, Offset(liveX, liveY))
    }
}

@Composable
private fun LevelAbout(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "level \u2014 about", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            EdgeCropText(
                text = "three instruments",
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(
                text = "x and y tubes read the device axes in degrees; the round bullseye shows both at once.",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(
                text = "save freezes a memory bubble for comparison; clear releases it.",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(
                text = "for calibration advice, use system settings.",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
            )
        }
    }
}
