package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.CrossbarBar
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/* ============================== Engine ============================== */

/**
 * Pure maths for the Chord Finder's built-in practising metronome
 * (ChordFinder!MetronomeViewController): 40–226 BPM, a y∈[144,330] drag
 * weight mapped as `BPM = y − 104`, and an accent every fourth beat.
 */
object ChordFinderEngine {
    const val BPM_MIN = 40
    const val BPM_MAX = 226
    const val WEIGHT_Y_MIN = 144.0
    const val WEIGHT_Y_MAX = 330.0
    const val ACCENT_EVERY = 4

    fun clampBpm(bpm: Int): Int = bpm.coerceIn(BPM_MIN, BPM_MAX)

    fun intervalMs(bpm: Int): Double = 60_000.0 / clampBpm(bpm)

    fun bpmForWeight(y: Double): Int =
        (y.coerceIn(WEIGHT_Y_MIN, WEIGHT_Y_MAX) - 104.0).roundToInt().coerceIn(BPM_MIN, BPM_MAX)

    fun weightForBpm(bpm: Int): Double = clampBpm(bpm) + 104.0

    fun isAccent(beatIndex: Int): Boolean = beatIndex % ACCENT_EVERY == 0
}

/* ============================== UI ============================== */

@Composable
fun ChordFinderApp() {
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    var page by remember { mutableStateOf(0) }
    var rootIndex by remember { mutableStateOf(0) }
    var qualityIndex by remember { mutableStateOf(0) }
    var voicingIndex by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    val root = ChordData.ROOTS[rootIndex]
    val quality = ChordData.QUALITIES[qualityIndex]
    val voicings = remember(root, quality) { ChordData.voicingsFor(root, quality) }
    LaunchedEffect(root, quality) { voicingIndex = 0 }
    val voicing = voicings.getOrNull(voicingIndex.coerceIn(0, (voicings.size - 1).coerceAtLeast(0)))

    DetailScaffold(title = "chord finder") {
        Column(Modifier.fillMaxSize()) {
            CrossbarBar(
                labels = listOf("chords", "metronome"),
                selected = page,
                onSelect = { page = it },
            )
            if (page == 0) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = DoradoTokens.EDGE.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SnappingColumn(
                            label = "root",
                            items = ChordData.ROOTS,
                            index = rootIndex,
                            onChange = { rootIndex = it },
                        )
                        SnappingColumn(
                            label = "quality",
                            items = ChordData.QUALITIES.map { ChordData.qualityName(it) },
                            index = qualityIndex,
                            onChange = { qualityIndex = it },
                        )
                        SnappingColumn(
                            label = "voicing",
                            items = if (voicings.isEmpty()) listOf("none") else voicings.indices.map { "shape ${it + 1}" },
                            index = voicingIndex,
                            onChange = { voicingIndex = it },
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            EdgeCropText(
                                text = "$root ${ChordData.qualityName(quality).lowercase()}",
                                fontSize = DoradoTokens.TYPE_LIST.dp,
                                color = colors.textPrimary,
                            )
                            val enharmonic = ChordData.enharmonicOf(root)
                            if (enharmonic != root) {
                                EdgeCropText(
                                    text = "also $enharmonic",
                                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (voicing != null) {
                        Fretboard(voicing)
                    } else {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            EdgeCropText(
                                text = "no shape for $root ${ChordData.qualityName(quality).lowercase()}",
                                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    EdgeCropText(
                        text = "drag a wheel to browse; numbers in the dots are fingers",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = colors.textInactive,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                ChordMetronomePage(synth = synth)
            }
        }
    }
}

@Composable
private fun SnappingColumn(
    label: String,
    items: List<String>,
    index: Int,
    onChange: (Int) -> Unit,
    width: Dp = 82.dp,
) {
    if (items.isEmpty()) return
    val colors = LocalDoradoColors.current
    val n = items.size
    val center = ((index % n) + n) % n
    var drag by remember { mutableStateOf(0f) }
    Column(
        modifier = Modifier
            .width(width)
            .height(128.dp)
            .pointerInput(items, index) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val steps = (drag / 45f).roundToInt()
                        if (steps != 0) onChange(((center - steps) % n + n) % n)
                        drag = 0f
                    },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        drag += delta
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EdgeCropText(
            text = label,
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
        )
        EdgeCropText(
            text = items[(center - 1 + n) % n],
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.textInactive,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        EdgeCropText(
            text = items[center],
            fontSize = DoradoTokens.TYPE_NOW_META.dp,
            color = colors.textPrimary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        EdgeCropText(
            text = items[(center + 1) % n],
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.textInactive,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/**
 * Fretboard with the device's finger-number stamps, open circles, mute
 * crosses, the fret-number column and the thick/thin nut window rule.
 */
@Composable
private fun Fretboard(voicing: ChordData.Voicing) {
    val colors = LocalDoradoColors.current
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .appDescription("chord diagram: frets ${voicing.frets.joinToString(",")}, base fret ${voicing.baseFret}"),
    ) {
        val w = size.width
        val h = size.height
        val topPad = h * 0.16f
        val boardH = h - topPad
        val fretCount = 5
        val fretGap = w / fretCount
        val stringYs = FloatArray(6) { topPad + (it + 1) * boardH / 7f }

        // Fret wires with the nut: thick at the open window, thin when shifted.
        for (i in 1..fretCount) {
            drawLine(colors.border, Offset(i * fretGap, topPad), Offset(i * fretGap, h), 1f)
        }
        val nutThick = voicing.baseFret == 1
        drawLine(
            if (nutThick) colors.textSecondary else colors.border,
            Offset(0f, topPad),
            Offset(0f, h),
            if (nutThick) 4f else 1.5f,
        )
        for (y in stringYs) {
            drawLine(colors.border, Offset(0f, y), Offset(w, y), 1f)
        }

        // Fret-number column along the bottom of each window cell.
        for (i in 0 until fretCount) {
            val label = "${voicing.baseFret + i}"
            val layout = textMeasurer.measure(
                AnnotatedString(label),
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textSecondary,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(
                    (i + 0.5f) * fretGap - layout.size.width / 2f,
                    h - layout.size.height,
                ),
            )
        }

        voicing.frets.forEachIndexed { string, fret ->
            val y = stringYs[string]
            val markerY = y - topPad * 0.65f
            val markerX = 8f
            when {
                fret < 0 -> {
                    val s = 5f
                    drawLine(colors.textSecondary, Offset(markerX - s, markerY - s), Offset(markerX + s, markerY + s), 1.5f)
                    drawLine(colors.textSecondary, Offset(markerX - s, markerY + s), Offset(markerX + s, markerY - s), 1.5f)
                }
                fret == 0 -> drawCircle(
                    colors.textSecondary,
                    5f,
                    Offset(markerX, markerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f),
                )
                else -> {
                    val windowIndex = (fret - voicing.baseFret).coerceIn(0, fretCount - 1)
                    val cx = (windowIndex + 0.5f) * fretGap
                    drawCircle(colors.accent, 9f, Offset(cx, y))
                    val finger = voicing.fingers.getOrElse(string) { 0 }
                    if (finger > 0) {
                        val layout = textMeasurer.measure(
                            AnnotatedString("$finger"),
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = colors.background,
                            ),
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                cx - layout.size.width / 2f,
                                y - layout.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChordMetronomePage(synth: MiniSynth) {
    val colors = LocalDoradoColors.current
    val bank = remember { SfxBank(synth) }
    var bpm by remember { mutableStateOf(120) }
    var running by remember { mutableStateOf(false) }
    var beat by remember { mutableStateOf(0) }

    // Keyed on `running` only: a BPM change adjusts the next interval but
    // must not restart the loop and reset the beat phase.
    val liveBpm = rememberUpdatedState(bpm)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var index = beat
        while (isActive) {
            beat = index
            val accent = ChordFinderEngine.isAccent(index)
            synth.playSamples(if (accent) metronomeClick(true) else bank.samples("tick"))
            index = (index + 1) % ChordFinderEngine.ACCENT_EVERY
            delay(ChordFinderEngine.intervalMs(liveBpm.value).toLong())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(DoradoTokens.EDGE.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeCropText(
                text = "$bpm bpm",
                fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            EdgeCropText(
                text = if (running) "stop" else "start",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier
                    .combinedClickable(onClick = { running = !running }, onLongClick = {})
                    .padding(horizontal = 6.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WeightTrack(bpm = bpm, onBpm = { bpm = it })
            Column(Modifier.padding(start = 16.dp)) {
                repeat(ChordFinderEngine.ACCENT_EVERY) { index ->
                    EdgeCropText(
                        text = if (index == 0) "accent" else "beat ${index + 1}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (running && beat == index) colors.accent else colors.textInactive,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
                EdgeCropText(
                    text = "40-226 bpm",
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WeightTrack(bpm: Int, onBpm: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Canvas(
        modifier = Modifier
            .width(56.dp)
            .height(190.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = change.position.y / size.height
                    val y = ChordFinderEngine.WEIGHT_Y_MIN +
                        fraction.toDouble() * (ChordFinderEngine.WEIGHT_Y_MAX - ChordFinderEngine.WEIGHT_Y_MIN)
                    onBpm(ChordFinderEngine.bpmForWeight(y))
                }
            },
    ) {
        val x = size.width / 2f
        drawLine(colors.border, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
        val fraction = ((ChordFinderEngine.weightForBpm(bpm) - ChordFinderEngine.WEIGHT_Y_MIN) /
            (ChordFinderEngine.WEIGHT_Y_MAX - ChordFinderEngine.WEIGHT_Y_MIN)).toFloat().coerceIn(0f, 1f)
        val capY = fraction * size.height
        drawRect(
            color = colors.tile,
            topLeft = Offset(4.dp.toPx(), capY - 10.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), 20.dp.toPx()),
        )
        drawRect(
            color = colors.accent,
            topLeft = Offset(4.dp.toPx(), capY - 10.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), 20.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
    }
}
