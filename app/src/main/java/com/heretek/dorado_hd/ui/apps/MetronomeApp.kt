package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* ============================== Engine ============================== */

/**
 * Immutable metronome state: tempo, run flag, up-to-16 accent cells, the
 * running beat cursor and the decaying pulse indicator.
 */
data class MetronomeState(
    val bpm: Int = 120,
    val running: Boolean = false,
    val beats: List<Boolean> = List(4) { false },
    val currentBeat: Int = 0,
    val pulse: Double = 0.0,
    val timerMs: Double = 0.0,
    val tapTimes: List<Long> = emptyList(),
)

data class BeatEvent(val beat: Int, val accent: Boolean)

data class AdvanceResult(val state: MetronomeState, val events: List<BeatEvent>)

/**
 * Pure re-derivation of the device metronome (Metronome!Metronome.CalcBeatSettings
 * / Update / HandleInput): 40–340 BPM, ring-angle mapping, 16-cell accent
 * grid, 0.5 s slider snap and per-beat indicator decay.
 */
object MetronomeEngine {
    const val BPM_MIN = 40
    const val BPM_MAX = 340
    const val MAX_BEATS = 16
    const val DIAL_ANGLE_MIN = 0.5
    const val DIAL_ANGLE_MAX = 5.7831855
    const val SLIDER_X_MIN = 22.0
    const val SLIDER_X_MAX = 248.0
    const val TAP_RESET_MS = 3000L
    const val PULSE_DECAY_PER_SEC = 1500.0 / 255.0

    fun clampBpm(bpm: Int): Int = bpm.coerceIn(BPM_MIN, BPM_MAX)

    fun beatLengthMs(bpm: Int): Double = 60_000.0 / clampBpm(bpm)

    /** Drag mapping: `degrees(angle) + 11`, only inside the dial sweep. */
    fun bpmForDialAngle(angleRad: Double): Int? {
        if (angleRad < DIAL_ANGLE_MIN || angleRad > DIAL_ANGLE_MAX) return null
        return clampBpm(Math.toDegrees(angleRad).roundToInt() + 11)
    }

    /** Inverse of [bpmForDialAngle] (pointer follows the finger). */
    fun dialAngleForBpm(bpm: Int): Double = Math.toRadians(clampBpm(bpm) - 11.0)

    /** Pointer art angle (device places the index at `BPM − 12`). */
    fun indexAngleForBpm(bpm: Int): Double = Math.toRadians(clampBpm(bpm) - 12.0)

    fun beatCountFromSlider(x: Double): Int =
        (MAX_BEATS * (x.coerceIn(SLIDER_X_MIN, SLIDER_X_MAX) - SLIDER_X_MIN) / (SLIDER_X_MAX - SLIDER_X_MIN))
            .roundToInt().coerceIn(0, MAX_BEATS)

    fun sliderXForBeatCount(count: Int): Double =
        SLIDER_X_MIN + (SLIDER_X_MAX - SLIDER_X_MIN) * count.coerceIn(0, MAX_BEATS) / MAX_BEATS

    fun setBpm(state: MetronomeState, bpm: Int): MetronomeState = state.copy(bpm = clampBpm(bpm))

    fun setBeatCount(state: MetronomeState, count: Int): MetronomeState {
        val n = count.coerceIn(0, MAX_BEATS)
        val beats = if (n <= state.beats.size) {
            state.beats.take(n)
        } else {
            state.beats + List(n - state.beats.size) { false }
        }
        return state.copy(
            beats = beats,
            currentBeat = if (n == 0) 0 else state.currentBeat.coerceIn(0, n - 1),
        )
    }

    fun toggleAccent(state: MetronomeState, index: Int): MetronomeState {
        if (index !in state.beats.indices) return state
        val beats = state.beats.toMutableList().also { it[index] = !it[index] }
        return state.copy(beats = beats)
    }

    /** Tap a grid cell: toggles inside the pattern, grows the pattern outside. */
    fun toggleCell(state: MetronomeState, index: Int): MetronomeState {
        if (index !in 0 until MAX_BEATS) return state
        val grown = if (index < state.beats.size) state else setBeatCount(state, index + 1)
        return toggleAccent(grown, index)
    }

    fun toggleRunning(state: MetronomeState): MetronomeState =
        state.copy(running = !state.running, timerMs = 0.0, currentBeat = 0, pulse = 0.0)

    /**
     * Advances the beat clock by [elapsedMs]. Tempo changes retime the running
     * stream because the accumulator keeps its remainder and the beat length is
     * recomputed from the current BPM.
     */
    fun advance(state: MetronomeState, elapsedMs: Double): AdvanceResult {
        if (!state.running || elapsedMs <= 0.0) return AdvanceResult(state, emptyList())
        var timer = state.timerMs + elapsedMs
        var pulse = (state.pulse - PULSE_DECAY_PER_SEC * elapsedMs / 1000.0).coerceAtLeast(0.0)
        var beat = state.currentBeat.coerceIn(0, (state.beats.size - 1).coerceAtLeast(0))
        val events = mutableListOf<BeatEvent>()
        val length = beatLengthMs(state.bpm)
        while (timer >= length) {
            timer -= length
            beat = if (state.beats.isEmpty()) 0 else (beat + 1) % state.beats.size
            val accent = state.beats.getOrElse(beat) { false }
            events += BeatEvent(beat, accent)
            pulse = 1.0
        }
        return AdvanceResult(
            state = state.copy(timerMs = timer, currentBeat = beat, pulse = pulse),
            events = events,
        )
    }

    /** Averages the last up-to-four tap intervals; long gaps restart the set. */
    fun tapTempo(state: MetronomeState, nowMs: Long): MetronomeState {
        val times = if (state.tapTimes.isNotEmpty() && nowMs - state.tapTimes.last() > TAP_RESET_MS) {
            listOf(nowMs)
        } else {
            (state.tapTimes + nowMs).takeLast(5)
        }
        if (times.size < 2) return state.copy(tapTimes = times)
        val intervals = times.zipWithNext { a, b -> (b - a).toDouble() }.takeLast(4)
        val average = intervals.average()
        if (average <= 0.0) return state.copy(tapTimes = times)
        return state.copy(bpm = clampBpm((60_000.0 / average).roundToInt()), tapTimes = times)
    }

    fun serialize(state: MetronomeState): String =
        "${state.bpm}|${state.beats.joinToString("") { if (it) "1" else "0" }}"

    fun parse(raw: String?): MetronomeState {
        val base = MetronomeState()
        if (raw.isNullOrBlank()) return base
        val parts = raw.split("|")
        val bpm = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: base.bpm
        val pattern = parts.getOrNull(1)?.trim() ?: return base.copy(bpm = clampBpm(bpm))
        val beats = pattern.take(MAX_BEATS).map { it == '1' }
        return base.copy(bpm = clampBpm(bpm), beats = beats)
    }
}

/* ============================== UI ============================== */

@Composable
fun MetronomeApp() {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val graph = LocalDoradoGraph.current
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    var metro by remember { mutableStateOf(MetronomeState()) }
    var loaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }
    LaunchedEffect(Unit) {
        metro = MetronomeEngine.parse(graph.appState.get("metronome"))
        loaded = true
    }
    LaunchedEffect(metro.bpm, metro.beats) {
        if (loaded) graph.appState.put("metronome", MetronomeEngine.serialize(metro))
    }

    // Frame-clock loop: events are collected per frame and synthesized on the
    // app scope, so the audio write never blocks the frame callback.
    LaunchedEffect(metro.running) {
        if (!metro.running) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val result = MetronomeEngine.advance(metro, (now - last) / 1_000_000.0)
                    metro = result.state
                    result.events.forEach { event ->
                        val click = metronomeClick(event.accent)
                        scope.launch { synth.playSamples(click) }
                    }
                }
                last = now
            }
        }
    }

    DetailScaffold(title = "metronome") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeCropText(
                    text = "${metro.bpm} bpm",
                    fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                EdgeCropText(
                    text = if (metro.running) "pause" else "play",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                metro = MetronomeEngine.toggleRunning(metro)
                                bank.play("click")
                            },
                            onLongClick = {},
                        )
                        .padding(horizontal = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeCropText(
                    text = "-1",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { metro = MetronomeEngine.setBpm(metro, metro.bpm - 1) },
                            onLongClick = { metro = MetronomeEngine.setBpm(metro, metro.bpm - 10) },
                        )
                        .padding(end = 10.dp),
                )
                EdgeCropText(
                    text = "+1",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { metro = MetronomeEngine.setBpm(metro, metro.bpm + 1) },
                            onLongClick = { metro = MetronomeEngine.setBpm(metro, metro.bpm + 10) },
                        )
                        .padding(end = 16.dp),
                )
                EdgeCropText(
                    text = "tap",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                metro = MetronomeEngine.tapTempo(metro, AppClock.millis())
                                bank.play("tick")
                            },
                            onLongClick = {},
                        )
                        .padding(end = 16.dp),
                )
                EdgeCropText(
                    text = "beat ${if (metro.beats.isEmpty()) 0 else metro.currentBeat + 1} / ${metro.beats.size}",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                    alpha = (0.35f + 0.65f * metro.pulse.toFloat()).coerceIn(0.35f, 1f),
                )
            }
            MetronomeDial(
                bpm = metro.bpm,
                running = metro.running,
                onBpm = { metro = MetronomeEngine.setBpm(metro, it) },
                onToggle = { metro = MetronomeEngine.toggleRunning(metro) },
            )
            BeatSlider(
                beatCount = metro.beats.size,
                onBeatCount = { metro = MetronomeEngine.setBeatCount(metro, it) },
            )
            BeatGrid(
                beats = metro.beats,
                currentBeat = metro.currentBeat,
                onCell = {
                    metro = MetronomeEngine.toggleCell(metro, it)
                    bank.play("click")
                },
            )
            EdgeCropText(
                text = "tap a cell to accent it; drag the slider for 0-16 beats",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textInactive,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MetronomeDial(
    bpm: Int,
    running: Boolean,
    onBpm: (Int) -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val latestOnBpm = rememberUpdatedState(onBpm)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .appDescription("metronome: $bpm bpm, ${if (running) "running" else "stopped"}")
            // Keyed on Unit so the gesture coroutine survives bpm changes;
            // keying on bpm cancelled the drag after a single step.
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val angle = PI + atan2(
                        (cy - change.position.y).toDouble(),
                        (cx - change.position.x).toDouble(),
                    )
                    MetronomeEngine.bpmForDialAngle(angle)?.let(latestOnBpm.value)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = min(size.width, size.height) / 2f * 0.92f
                    val distance = kotlin.math.hypot(
                        (position.x - cx).toDouble(),
                        (position.y - cy).toDouble(),
                    ).toFloat()
                    if (distance < radius - 30.dp.toPx()) onToggle()
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f * 0.92f
        drawCircle(colors.tile, radius, Offset(cx, cy))
        drawCircle(colors.border, radius, Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        val start = Math.toDegrees(MetronomeEngine.DIAL_ANGLE_MIN).toFloat()
        val sweep = Math.toDegrees(MetronomeEngine.DIAL_ANGLE_MAX - MetronomeEngine.DIAL_ANGLE_MIN).toFloat()
        drawArc(
            color = colors.border,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - radius + 8.dp.toPx(), cy - radius + 8.dp.toPx()),
            size = Size((radius - 8.dp.toPx()) * 2f, (radius - 8.dp.toPx()) * 2f),
            style = Stroke(width = 3.dp.toPx()),
        )
        val angle = MetronomeEngine.dialAngleForBpm(bpm)
        val arcRadius = radius - 8.dp.toPx()
        val ex = cx + cos(angle).toFloat() * arcRadius
        val ey = cy + sin(angle).toFloat() * arcRadius
        drawLine(colors.accent, Offset(cx, cy), Offset(ex, ey), strokeWidth = 2.dp.toPx())
        drawCircle(colors.accent, 8.dp.toPx(), Offset(ex, ey))
        if (running) {
            drawCircle(colors.accent.copy(alpha = 0.25f), radius * 0.55f, Offset(cx, cy))
        }
    }
}

@Composable
private fun BeatSlider(beatCount: Int, onBeatCount: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    val fraction = (beatCount.toFloat() / MetronomeEngine.MAX_BEATS).coerceIn(0f, 1f)
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val fraction = position.x / size.width
                        val x = MetronomeEngine.SLIDER_X_MIN +
                            fraction.toDouble() * (MetronomeEngine.SLIDER_X_MAX - MetronomeEngine.SLIDER_X_MIN)
                        onBeatCount(MetronomeEngine.beatCountFromSlider(x))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val fraction = change.position.x / size.width
                        val x = MetronomeEngine.SLIDER_X_MIN +
                            fraction.toDouble() * (MetronomeEngine.SLIDER_X_MAX - MetronomeEngine.SLIDER_X_MIN)
                        onBeatCount(MetronomeEngine.beatCountFromSlider(x))
                    }
                },
        ) {
            val y = size.height / 2f
            drawLine(colors.border, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            drawLine(colors.accent, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2.dp.toPx())
            drawCircle(colors.accent, 6.dp.toPx(), Offset(size.width * fraction, y))
        }
        EdgeCropText(
            text = "$beatCount beats",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun BeatGrid(
    beats: List<Boolean>,
    currentBeat: Int,
    onCell: (Int) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.padding(top = 4.dp)) {
        repeat(2) { row ->
            Row {
                repeat(8) { col ->
                    val index = row * 8 + col
                    val active = index < beats.size
                    val accented = active && beats[index]
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 30.dp)
                            .padding(2.dp)
                            .background(
                                when {
                                    accented -> colors.accent
                                    active -> colors.tile
                                    else -> colors.background
                                },
                            )
                            .combinedClickable(
                                onClick = { onCell(index) },
                                onLongClick = { onCell(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            EdgeCropText(
                                text = "${index + 1}",
                                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                color = if (accented) colors.background else if (index == currentBeat) colors.accent else colors.textInactive,
                            )
                        }
                    }
                }
            }
        }
    }
}
