package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.NoteEntity
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.design.components.KineticList
import com.heretek.dorado_hd.design.components.firstLetterOf
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ============================== Calculator ============================== */

@Composable
fun CalculatorApp() {
    val colors = LocalDoradoColors.current
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Double?>(null) }
    var scientific by remember { mutableStateOf(false) }

    val rows = if (scientific) SCIENTIFIC_KEYS else BASIC_KEYS

    DetailScaffold(title = "calculator") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = result?.let { formatNumber(it) } ?: expr.ifEmpty { "0" },
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                    color = if (result != null) colors.accent else colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.height(6.dp))
            // Weighted rows so every keypad row (basic or scientific) always
            // fits the 224dp device-mode content area; nothing is clipped.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        row.forEach { key ->
                            KeyButton(key, Modifier.weight(1f).fillMaxHeight()) { label ->
                                when (label) {
                                    "C" -> { expr = ""; result = null }
                                    "=" -> {
                                        val v = CalcEngine.eval(expr)
                                        result = v
                                        expr = v?.let { formatNumber(it) } ?: expr
                                    }
                                    "±" -> {
                                        // Toggle the sign of the last operand without
                                        // corrupting a preceding subtraction: "5-3" →
                                        // "5-(-3)" and "5-(-3)" → "5-(3)".
                                        val num = Regex("\\d+(?:\\.\\d+)?$").find(expr)
                                        if (num != null) {
                                            val start = num.range.first
                                            val raw = num.value
                                            val prev = expr.getOrNull(start - 1)
                                            val beforeMinus = if (prev == '-') expr.getOrNull(start - 2) else null
                                            val minusIsSign = prev == '-' &&
                                                (beforeMinus == null || beforeMinus in "+-*/^(")
                                            expr = when {
                                                minusIsSign -> expr.removeRange(start - 1, start)
                                                prev == '-' -> expr.substring(0, start) + "(-" + raw + ")"
                                                else -> expr.substring(0, start) + "-" + raw
                                            }
                                            result = null
                                        }
                                    }
                                    "sci" -> scientific = !scientific
                                    "del" -> { if (expr.isNotEmpty()) expr = expr.dropLast(1); result = null }
                                    else -> { expr += label; result = null }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.6f".format(v).trimEnd('0').trimEnd('.')

private val BASIC_KEYS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "±", "C", "del", "="),
    listOf("sci"),
)

private val SCIENTIFIC_KEYS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "±", "C", "del", "="),
    listOf("sci"),
    listOf("sin", "cos", "tan", "%"),
    listOf("log", "ln", "sqrt", "^"),
)

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    val accent = label in setOf("=", "C", "sci")
    Box(
        modifier
            .background(if (accent) colors.elevated else Color.Transparent)
            .combinedClickable(onClick = { onClick(label) }, onLongClick = {})
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (accent) colors.accent else colors.textPrimary,
            ),
        )
    }
}

/* ============================== Notes ============================== */

@Composable
fun NotesApp() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    var selected by remember { mutableStateOf<NoteEntity?>(null) }
    val notes by graph.notes.notes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (selected != null) {
        val s = selected!!
        var title by remember(s.id) { mutableStateOf(s.title) }
        var body by remember(s.id) { mutableStateOf(s.body) }
        DetailScaffold(
            title = "notes",
            onBack = {
                // Autosave on the way out; NonCancellable so the write is not
                // torn down with the composable after nav.pop().
                scope.launch(kotlinx.coroutines.NonCancellable) {
                    graph.notes.update(s.id, title.ifBlank { "untitled" }, body)
                }
                graph.nav.pop()
            },
        ) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                EditableLine(value = title, onChange = { title = it }, placeholder = "title")
                Spacer(Modifier.height(8.dp))
                EditableLine(value = body, onChange = { body = it }, placeholder = "body", multiLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        scope.launch { graph.notes.update(s.id, title.ifBlank { "untitled" }, body); selected = null }
                    },
                    onLongClick = {},
                ).padding(vertical = 8.dp)) {
                    EdgeCropText(text = "save", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().combinedClickable(
                    onClick = {
                        scope.launch { graph.notes.delete(s.id); selected = null }
                    },
                    onLongClick = {},
                ).padding(vertical = 8.dp)) {
                    EdgeCropText(text = "delete", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.textInactive)
                }
            }
        }
        return
    }

    DetailScaffold(title = "notes") {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = { scope.launch { graph.notes.add("untitled", "") } },
                        onLongClick = {},
                    ),
            ) {
                EdgeCropText(text = "+ new note", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
            KineticList(
                items = notes,
                key = { it.id },
                letter = { firstLetterOf(it.title) },
                rowContent = { n, _ ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = { selected = n },
                                onLongClick = {
                                    menus.show(
                                        title = n.title,
                                        actions = listOf(MenuAction("delete") { scope.launch { graph.notes.delete(n.id) } }),
                                    )
                                },
                            )
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        EdgeCropText(text = n.title, fontSize = DoradoTokens.TYPE_LIST.dp, modifier = Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

@Composable
fun EditableLine(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multiLine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    var local by remember(value) { mutableStateOf(value) }
    Box(modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicTextField(
            value = local,
            onValueChange = {
                local = it
                onChange(it)
            },
            singleLine = !multiLine,
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = if (multiLine) DoradoTokens.TYPE_LIST.sp else DoradoTokens.TYPE_NOW_META.sp,
                color = colors.textPrimary,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.elevated)
                .padding(8.dp),
        )
        // Placeholder is overlaid inside the field, never a sibling row.
        if (local.isEmpty() && placeholder.isNotEmpty()) {
            EdgeCropText(
                text = placeholder,
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )
        }
    }
}

/* ============================== Stopwatch ============================== */

@Composable
fun StopwatchApp() {
    val colors = LocalDoradoColors.current
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var accumulated by remember { mutableStateOf(0L) }
    var nowMs by remember { mutableStateOf(0L) }
    var lastLapMs by remember { mutableStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) {
            nowMs = System.currentTimeMillis() - startedAt + accumulated
            delay(33)
        }
    }

    DetailScaffold(title = "stopwatch") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = formatTimeCs(nowMs),
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                    color = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EdgeCropText(
                    text = if (running) "stop" else "start",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (running) {
                                accumulated += System.currentTimeMillis() - startedAt
                                running = false
                            } else {
                                startedAt = System.currentTimeMillis()
                                running = true
                            }
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
                EdgeCropText(
                    text = "lap",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = if (running) colors.accent else colors.textInactive,
                    modifier = Modifier.combinedClickable(
                        enabled = running,
                        onClick = {
                            laps.add(0, nowMs - lastLapMs)
                            lastLapMs = nowMs
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
                EdgeCropText(
                    text = "reset",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textInactive,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            running = false; accumulated = 0; laps.clear(); nowMs = 0; lastLapMs = 0
                        },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Split times, newest first, in a scrolling list so any number of
            // laps stays reachable.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                laps.forEachIndexed { index, split ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        EdgeCropText(
                            text = "lap ${laps.size - index}",
                            fontSize = DoradoTokens.TYPE_CAPTION.dp,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        EdgeCropText(text = formatTimeCs(split), fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.7f)
                    }
                }
            }
        }
    }
}

private fun formatTimeCs(ms: Long): String {
    val s = ms.coerceAtLeast(0)
    val h = s / 3_600_000; val m = (s % 3_600_000) / 60_000; val sec = (s % 60_000) / 1000; val cs = (s % 1000) / 10
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, sec, cs) else "%d:%02d.%02d".format(m, sec, cs)
}

private fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/* ============================== Metronome ============================== */

@Composable
fun MetronomeApp() {
    val colors = LocalDoradoColors.current
    var bpm by remember { mutableStateOf(120) }
    var beatsPerBar by remember { mutableStateOf(4) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    var beat by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { synth.stop() }
    }

    // One loop drives both the audible click and the beat readout and re-reads
    // bpm every beat, so a tempo change retimes the running metronome instead
    // of leaving display and clicks permanently desynced.
    LaunchedEffect(running, bpm, beatsPerBar) {
        if (!running) return@LaunchedEffect
        synth.start()
        var beatIndex = 0
        while (isActive) {
            synth.playSamples(metronomeClick(accent = beatIndex % beatsPerBar == 0))
            beat = beatIndex % beatsPerBar
            beatIndex++
            delay(60_000L / bpm.coerceIn(30, 300))
        }
    }

    DetailScaffold(title = "metronome") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "$bpm bpm",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp * 2,
                    color = colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(-10, -5, -1, 1, 5, 10).forEach { step ->
                    EdgeCropText(
                        text = if (step > 0) "+$step" else "$step",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.accent,
                        modifier = Modifier.combinedClickable(
                            onClick = { bpm = (bpm + step).coerceIn(30, 300) },
                            onLongClick = {},
                        ).padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4, 6).forEach { n ->
                    EdgeCropText(
                        text = "$n/4",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (n == beatsPerBar) colors.accent else colors.textInactive,
                        modifier = Modifier.combinedClickable(
                            onClick = { beatsPerBar = n },
                            onLongClick = {},
                        ).padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            EdgeCropText(
                text = if (running) "stop" else "start",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        running = !running
                        if (!running) beat = 0
                    },
                    onLongClick = {},
                ).padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(text = "beat ${beat + 1}", fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.6f, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ============================== Level ============================== */

@Composable
fun LevelApp() {
    val colors = LocalDoradoColors.current
    var xDeg by remember { mutableStateOf(0.0) }
    var yDeg by remember { mutableStateOf(0.0) }
    var surface by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                // Convert accelerometer to pitch/roll in degrees.
                val ax = e.values[0].toDouble(); val ay = e.values[1].toDouble(); val az = e.values[2].toDouble()
                val roll = Math.toDegrees(kotlin.math.atan2(ax, kotlin.math.sqrt(ay * ay + az * az)))
                val pitch = Math.toDegrees(kotlin.math.atan2(ay, az))
                xDeg = -roll
                yDeg = pitch
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    DetailScaffold(title = "level") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EdgeCropText(
                text = if (surface) "bubble" else "surface",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(onClick = { surface = !surface }, onLongClick = {}),
            )
            if (surface) {
                Box(
                    Modifier.fillMaxSize().background(colors.elevated),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "roll %.1f°   pitch %.1f°".format(xDeg, yDeg),
                        style = TextStyle(
                            fontFamily = Selawik,
                            fontWeight = FontWeight.Light,
                            fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp,
                            color = colors.textPrimary,
                        ),
                    )
                }
            } else {
                BubbleLevel(xDeg, yDeg)
            }
        }
    }
}

@Composable
private fun BubbleLevel(xDeg: Double, yDeg: Double) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.elevated),
    ) {
        // Center cross lines
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.border).align(Alignment.Center))
        Box(Modifier.fillMaxHeight().width(0.5.dp).background(colors.border).align(Alignment.Center))
        // Bubble circle: position proportional to tilt, clamped.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .graphicsLayer {
                    translationX = (xDeg / 30.0).toFloat().coerceIn(-1f, 1f) * 80f
                    translationY = (yDeg / 30.0).toFloat().coerceIn(-1f, 1f) * 60f
                }
                .background(if (abs(xDeg) < 1.0 && abs(yDeg) < 1.0) colors.accent else colors.tile),
        )
    }
}

/* ============================== Chord Finder ============================== */

@Composable
fun ChordFinderApp() {
    val colors = LocalDoradoColors.current
    var root by remember { mutableStateOf("C") }
    var quality by remember { mutableStateOf("major") }
    DetailScaffold(title = "chord finder") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.ROOTS.forEach { r ->
                    EdgeCropText(text = r, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (r == root) colors.accent else colors.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { root = r }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.QUALITIES.forEach { q ->
                    EdgeCropText(text = q, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (q == quality) colors.accent else colors.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { quality = q }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(12.dp))
            val shape = ChordData.shapeFor(root, quality)
            if (shape != null) {
                Fretboard(shape)
            } else {
                EdgeCropText(
                    text = "no open shape for $root $quality",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Fretboard(frets: IntArray) {
    val colors = LocalDoradoColors.current
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width; val h = size.height
        val topPad = h * 0.18f
        val boardH = h - topPad
        val fretCount = 5
        val fretGap = w / fretCount
        val stringYs = FloatArray(6) { topPad + (it + 1) * boardH / 7f }
        // Fret wires plus a thicker nut at the left edge.
        for (i in 1..fretCount) {
            val x = i * fretGap
            drawLine(colors.border.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, topPad), androidx.compose.ui.geometry.Offset(x, h), 1f)
        }
        drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(0f, topPad), androidx.compose.ui.geometry.Offset(0f, h), 3f)
        for (y in stringYs) {
            drawLine(colors.border, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), 1f)
        }
        for (i in 0 until minOf(frets.size, 6)) {
            val f = frets[i]
            val y = stringYs[i]
            val markerY = y - topPad * 0.7f
            when {
                f < 0 -> {
                    // Muted: X marker above the nut.
                    val s = 5f
                    drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(4f - s, markerY - s), androidx.compose.ui.geometry.Offset(4f + s, markerY + s), 1.5f)
                    drawLine(colors.textSecondary, androidx.compose.ui.geometry.Offset(4f - s, markerY + s), androidx.compose.ui.geometry.Offset(4f + s, markerY - s), 1.5f)
                }
                f == 0 -> drawCircle(colors.textSecondary, 5f, androidx.compose.ui.geometry.Offset(4f, markerY), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                else -> drawCircle(colors.accent, 6f, androidx.compose.ui.geometry.Offset((f - 0.5f) * fretGap, y))
            }
        }
    }
}

/* ============================== Shuffle By Album ============================== */

@Composable
fun ShuffleByAlbumApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val albums by graph.library.albums().collectAsState(initial = emptyList())
    var pick by remember { mutableStateOf<com.heretek.dorado_hd.data.model.Album?>(null) }

    DetailScaffold(title = "shuffle by album") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EdgeCropText(
                text = "shuffle",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = colors.accent,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (albums.isNotEmpty()) pick = albums.random()
                    },
                    onLongClick = {},
                ),
            )
            if (albums.isEmpty()) {
                EdgeCropText(text = "no albums on device", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = colors.textSecondary)
                return@Column
            }
            pick?.let { album ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.heretek.dorado_hd.design.components.AlbumArt(
                        model = album.albumArtUri,
                        contentDescription = album.title,
                        modifier = Modifier.size(88.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EdgeCropText(text = album.title, fontSize = DoradoTokens.TYPE_NOW_META.dp)
                        EdgeCropText(text = album.artist, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
                        EdgeCropText(
                            text = "play",
                            fontSize = DoradoTokens.TYPE_LIST.dp,
                            color = colors.accent,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    scope.launch {
                                        val tracks = graph.library.tracksByAlbum(album.albumId)
                                        if (tracks.isNotEmpty()) graph.controller.play(tracks, 0)
                                    }
                                },
                                onLongClick = {},
                            ),
                        )
                    }
                }
            }
        }
    }
}

/* ============================== Music Quiz ============================== */

@Composable
fun MusicQuizApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())
    var question by remember { mutableStateOf<QuizEngine.Question?>(null) }
    var correct by remember { mutableStateOf(0) }
    var wrong by remember { mutableStateOf(0) }
    var exhausted by remember { mutableStateOf(false) }

    LaunchedEffect(tracks) {
        if (question == null && !exhausted) {
            question = QuizEngine.buildQuestion(tracks)
            if (question == null && tracks.isNotEmpty()) exhausted = true
        }
    }

    DetailScaffold(title = "music quiz") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val q = question
            if (q == null) {
                EdgeCropText(
                    text = if (exhausted) "need at least 4 artists or albums to quiz" else "loading…",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    alpha = 0.6f,
                )
                if (exhausted) {
                    Spacer(Modifier.height(8.dp))
                    EdgeCropText(text = "correct $correct — wrong $wrong", fontSize = DoradoTokens.TYPE_CAPTION.dp, alpha = 0.6f)
                }
                return@Column
            }
            BasicText(
                text = q.prompt,
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                    color = LocalDoradoColors.current.textPrimary,
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            q.options.forEachIndexed { i, opt ->
                EdgeCropText(
                    text = "${i + 1}. $opt",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = LocalDoradoColors.current.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (opt == q.answer) {
                                    correct++
                                } else {
                                    wrong++
                                }
                                val next = QuizEngine.buildQuestion(tracks)
                                if (next == null) exhausted = true
                                question = next
                            },
                            onLongClick = {},
                        )
                        .padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            EdgeCropText(text = "correct $correct — wrong $wrong", fontSize = DoradoTokens.TYPE_CAPTION.dp, alpha = 0.6f)
        }
    }
}

/* ============================== Alarm Clock ============================== */

/** Stable PendingIntent request code from a Long alarm id (no truncation). */
private fun alarmRequestCode(id: Long): Int = (id xor (id ushr 32)).toInt()

@Composable
fun AlarmClockApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val menus = LocalContextMenu.current
    val alarms by graph.alarms.alarms().collectAsState(initial = emptyList())

    fun alarmPi(alarm: com.heretek.dorado_hd.data.db.AlarmEntity): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            context,
            alarmRequestCode(alarm.id),
            android.content.Intent(context, com.heretek.dorado_hd.media.AlarmReceiver::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("alarmKind", alarm.alarmKind)
                putExtra("refId", alarm.refId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(alarm: com.heretek.dorado_hd.data.db.AlarmEntity) {
        if (!alarm.enabled) return
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt = com.heretek.dorado_hd.media.AlarmScheduler.nextFireMs(alarm.hour, alarm.minute, daysOfWeek = alarm.daysOfWeek)
        am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, alarmPi(alarm))
    }

    fun cancel(alarm: com.heretek.dorado_hd.data.db.AlarmEntity) {
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        am.cancel(alarmPi(alarm))
    }

    fun parseTime(input: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})").find(input.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    DetailScaffold(title = "alarm clock") {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = {
                            menus.showPrompt("new alarm", "hh:mm (24h)") { value ->
                                val (h, m) = parseTime(value) ?: (7 to 0)
                                scope.launch {
                                    val entity = com.heretek.dorado_hd.data.db.AlarmEntity(
                                        hour = h, minute = m, enabled = true,
                                        label = "alarm", alarmKind = 0, refId = 0, daysOfWeek = 0,
                                    )
                                    val id = graph.alarms.add(entity)
                                    schedule(entity.copy(id = id))
                                }
                            }
                        },
                        onLongClick = {},
                    ),
            ) {
                EdgeCropText(text = "+ new alarm", fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.accent)
            }
            KineticList(
                items = alarms,
                key = { it.id },
                letter = { firstLetterOf(it.label) },
                rowContent = { alarm, _ ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        val enable = !alarm.enabled
                                        graph.alarms.setEnabled(alarm.id, enable)
                                        if (enable) schedule(alarm.copy(enabled = true)) else cancel(alarm)
                                    }
                                },
                                onLongClick = {
                                    menus.show(
                                        title = "%02d:%02d %s".format(alarm.hour, alarm.minute, alarm.label),
                                        actions = listOf(
                                            MenuAction("edit time") {
                                                menus.showPrompt(
                                                    "edit alarm",
                                                    "%02d:%02d".format(alarm.hour, alarm.minute),
                                                ) { value ->
                                                    val (h, m) = parseTime(value) ?: (alarm.hour to alarm.minute)
                                                    scope.launch {
                                                        graph.alarms.setTime(alarm.id, h, m)
                                                        val updated = alarm.copy(hour = h, minute = m)
                                                        if (updated.enabled) schedule(updated) else cancel(updated)
                                                    }
                                                }
                                            },
                                            MenuAction("delete") {
                                                scope.launch {
                                                    cancel(alarm)
                                                    graph.alarms.delete(alarm.id)
                                                }
                                            },
                                        ),
                                    )
                                },
                            )
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            EdgeCropText(
                                text = "%02d:%02d".format(alarm.hour, alarm.minute),
                                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                                color = if (alarm.enabled) LocalDoradoColors.current.textPrimary else LocalDoradoColors.current.textInactive,
                            )
                            EdgeCropText(text = alarm.label, fontSize = DoradoTokens.TYPE_LIST.dp, color = LocalDoradoColors.current.textSecondary)
                        }
                        EdgeCropText(text = if (alarm.enabled) "on" else "off", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = LocalDoradoColors.current.textSecondary)
                    }
                },
            )
        }
    }
}

/* ============================== Calendar ============================== */

@Composable
fun CalendarApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val menus = LocalContextMenu.current
    var monthStart by remember { mutableStateOf(java.time.YearMonth.now()) }
    val appts by graph.calendar.appointments().collectAsState(initial = emptyList())

    DetailScaffold(title = "calendar") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                EdgeCropText(text = "<", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent,
                    modifier = Modifier.combinedClickable(onClick = { monthStart = monthStart.minusMonths(1) }, onLongClick = {}))
                EdgeCropText(text = "%s %d".format(monthStart.month.name.lowercase().replaceFirstChar { it.uppercase() }, monthStart.year), fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    modifier = Modifier.weight(1f))
                EdgeCropText(text = ">", fontSize = DoradoTokens.TYPE_NOW_META.dp, color = LocalDoradoColors.current.accent,
                    modifier = Modifier.combinedClickable(onClick = { monthStart = monthStart.plusMonths(1) }, onLongClick = {}))
            }
            Spacer(Modifier.height(8.dp))
            MonthGrid(monthStart, appts)
        }
    }
}

@Composable
private fun MonthGrid(monthStart: java.time.YearMonth, appts: List<com.heretek.dorado_hd.data.db.AppointmentEntity>) {
    val firstOfMonth = monthStart.atDay(1)
    val daysInMonth = monthStart.lengthOfMonth()
    val firstWeekday = (firstOfMonth.dayOfWeek.value % 7) // Sunday=0
    val cells = firstWeekday + daysInMonth
    val rows = (cells + 6) / 7
    // Key by full date: a day-of-month map made every August 5 light up in
    // September (and every other month).
    val apptsByDate = appts.groupBy {
        java.time.Instant.ofEpochMilli(it.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current

    fun addAppointment(date: java.time.LocalDate) {
        menus.showPrompt("appointment", "title") { title ->
            scope.launch {
                graph.calendar.add(
                    title = title.ifBlank { "untitled" },
                    notes = null,
                    startAt = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endAt = null,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 7) {
                    val dayIndex = r * 7 + c - firstWeekday + 1
                    val valid = dayIndex in 1..daysInMonth
                    val date = if (valid) monthStart.atDay(dayIndex) else null
                    val dayAppts = date?.let { apptsByDate[it].orEmpty() } ?: emptyList()
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .background(if (valid) colors.elevated else Color.Transparent)
                            .combinedClickable(
                                enabled = valid,
                                onClick = { date?.let { addAppointment(it) } },
                                onLongClick = {
                                    if (date != null && dayAppts.isNotEmpty()) {
                                        menus.show(
                                            title = date.toString(),
                                            actions = dayAppts.map { a ->
                                                MenuAction("delete ${a.title}") {
                                                    scope.launch { graph.calendar.delete(a.id) }
                                                }
                                            } + MenuAction("add appointment") { addAppointment(date) },
                                        )
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (valid) dayIndex.toString() else "",
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (valid && dayAppts.isNotEmpty()) colors.accent else colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
