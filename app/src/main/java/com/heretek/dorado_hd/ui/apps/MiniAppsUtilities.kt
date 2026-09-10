package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp * 1.4f,
                    color = if (result != null) colors.accent else colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.weight(1f))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { key ->
                            KeyButton(key) { label ->
                                when (label) {
                                    "C" -> { expr = ""; result = null }
                                    "=" -> {
                                        val v = CalcEngine.eval(expr)
                                        result = v
                                        expr = v?.let { formatNumber(it) } ?: expr
                                    }
                                    "±" -> {
                                        // Toggle sign of last number
                                        val m = Regex("(-?\\d+(?:\\.\\d+)?)(?!.*\\d)").find(expr)
                                        if (m != null) {
                                            val v = m.value.toDouble()
                                            expr = expr.replaceRange(m.range, if (v < 0) (-v).toString() else "-" + v)
                                        }
                                    }
                                    "sci" -> scientific = !scientific
                                    "bksp" -> { if (expr.isNotEmpty()) expr = expr.dropLast(1); result = null }
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
    listOf("+", "±", "C", "bksp", "="),
    listOf("sci"),
)

private val SCIENTIFIC_KEYS = listOf(
    listOf("7", "8", "9", "/"),
    listOf("4", "5", "6", "*"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "(", ")"),
    listOf("+", "±", "C", "bksp", "="),
    listOf("sci"),
    listOf("sin", "cos", "tan", "%"),
    listOf("log", "ln", "sqrt", "^"),
)

@Composable
private fun KeyButton(label: String, onClick: (String) -> Unit) {
    val colors = LocalDoradoColors.current
    val accent = label in setOf("=", "C", "sci")
    Box(
        Modifier
            .size(width = 40.dp, height = 28.dp)
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
        DetailScaffold(title = "notes") {
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
        modifier = modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .padding(8.dp),
    )
    if (local.isEmpty() && placeholder.isNotEmpty()) {
        EdgeCropText(text = placeholder, fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
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
                text = formatTime(nowMs / 1000),
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
                        onClick = { laps.add(nowMs) },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
                EdgeCropText(
                    text = "reset",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textInactive,
                    modifier = Modifier.combinedClickable(
                        onClick = { running = false; accumulated = 0; laps.clear(); nowMs = 0 },
                        onLongClick = {},
                    ).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Show laps in reverse order.
            laps.asReversed().forEach { lap ->
                EdgeCropText(text = formatTime(lap / 1000), fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.7f, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
            }
        }
    }
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
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var beat by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { synth.stop(); job?.cancel() }
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
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                (-5..5 step 1).forEach { step ->
                    EdgeCropText(
                        text = if (step > 0) "+$step" else "$step",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (step == 0) colors.textInactive else colors.accent,
                        modifier = Modifier.combinedClickable(
                            onClick = { bpm = (bpm + step * 2).coerceIn(30, 300) },
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
                        if (running) {
                            job?.cancel(); running = false
                        } else {
                            synth.start()
                            beat = 0
                            job = metronomeLoop(synth, bpm, beatsPerBar, totalBeats = Int.MAX_VALUE)
                            running = true
                        }
                    },
                    onLongClick = {},
                ).padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            EdgeCropText(text = "beat $beat", fontSize = DoradoTokens.TYPE_LIST.dp, alpha = 0.6f, modifier = Modifier.fillMaxWidth())
            // Lightweight beat pulse: every click increments beat via a separate ticker.
            LaunchedEffect(running) {
                while (running) {
                    delay((60_000L / bpm.coerceAtLeast(30)))
                    beat = (beat + 1) % beatsPerBar
                }
            }
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
                text = if (surface) "surface" else "bubble",
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
                        text = "roll %.1f°".format(xDeg),
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
    var root by remember { mutableStateOf("C") }
    var quality by remember { mutableStateOf("major") }
    DetailScaffold(title = "chord finder") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.ROOTS.forEach { r ->
                    EdgeCropText(text = r, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (r == root) LocalDoradoColors.current.accent else LocalDoradoColors.current.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { root = r }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordData.QUALITIES.forEach { q ->
                    EdgeCropText(text = q, fontSize = DoradoTokens.TYPE_LIST.dp, color = if (q == quality) LocalDoradoColors.current.accent else LocalDoradoColors.current.textPrimary,
                        modifier = Modifier.combinedClickable(onClick = { quality = q }, onLongClick = {}))
                }
            }
            Spacer(Modifier.height(12.dp))
            Fretboard(ChordData.shapeFor(root, quality))
        }
    }
}

@Composable
private fun Fretboard(frets: IntArray) {
    val colors = LocalDoradoColors.current
    val width = 200.dp
    val height = 90.dp
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        val w = size.width; val h = size.height
        // 4 frets shown
        val fretGap = w / 5
        for (i in 1..4) {
            val x = i * fretGap
            drawLine(colors.border.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), 1f)
        }
        // 6 strings
        for (i in 0..5) {
            val y = (i + 1) * h / 7
            drawLine(colors.border, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(w, y), 1f)
        }
        // Dots at frets[0..5]
        for (i in 0 until minOf(frets.size, 6)) {
            val f = frets[i]
            if (f in 0..4) {
                val cx = (f + 0.5f) * fretGap
                val cy = (i + 1) * h / 7
                drawCircle(colors.accent, 6f, androidx.compose.ui.geometry.Offset(cx, cy))
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
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            pick?.let { album ->
                com.heretek.dorado_hd.design.components.AlbumArt(
                    model = album.albumArtUri,
                    contentDescription = album.title,
                    modifier = Modifier.size(120.dp),
                )
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

/* ============================== Music Quiz ============================== */

@Composable
fun MusicQuizApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())
    var question by remember { mutableStateOf<QuizEngine.Question?>(null) }
    var correct by remember { mutableStateOf(0) }
    var wrong by remember { mutableStateOf(0) }

    LaunchedEffect(tracks) {
        if (question == null && tracks.size >= 4) {
            question = QuizEngine.buildQuestion(tracks)
        }
    }

    DetailScaffold(title = "music quiz") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val q = question
            if (q == null) {
                EdgeCropText(text = "loading…", fontSize = DoradoTokens.TYPE_NOW_META.dp, alpha = 0.4f)
                return@Column
            }
            BasicText(
                text = q.prompt,
                style = TextStyle(
                    fontFamily = Selawik,
                    fontWeight = FontWeight.Light,
                    fontSize = DoradoTokens.TYPE_NOW_TITLE.sp * 1.2f,
                    color = LocalDoradoColors.current.textPrimary,
                ),
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
                                scope.launch { question = QuizEngine.buildQuestion(tracks) }
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

@Composable
fun AlarmClockApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val menus = LocalContextMenu.current
    val alarms by graph.alarms.alarms().collectAsState(initial = emptyList())

    fun schedule(alarm: com.heretek.dorado_hd.data.db.AlarmEntity) {
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt = com.heretek.dorado_hd.media.AlarmScheduler.nextFireMs(alarm.hour, alarm.minute)
        val pi = android.app.PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            android.content.Intent(context, com.heretek.dorado_hd.media.AlarmReceiver::class.java).apply {
                putExtra("alarmId", alarm.id)
                putExtra("alarmKind", alarm.alarmKind)
                putExtra("refId", alarm.refId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    DetailScaffold(title = "alarm clock") {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp)
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                val id = graph.alarms.add(
                                    com.heretek.dorado_hd.data.db.AlarmEntity(
                                        hour = 7, minute = 0, enabled = true,
                                        label = "alarm", alarmKind = 0, refId = 0, daysOfWeek = 0,
                                    ),
                                )
                                schedule(com.heretek.dorado_hd.data.db.AlarmEntity(id = id, hour = 7, minute = 0, enabled = true, label = "alarm", alarmKind = 0, refId = 0, daysOfWeek = 0))
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
                                        graph.alarms.setEnabled(alarm.id, !alarm.enabled)
                                        if (!alarm.enabled) {
                                            val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                                            am.cancel(
                                                android.app.PendingIntent.getBroadcast(
                                                    context,
                                                    alarm.id.toInt(),
                                                    android.content.Intent(context, com.heretek.dorado_hd.media.AlarmReceiver::class.java),
                                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                                                ),
                                            )
                                        } else schedule(alarm)
                                    }
                                },
                                onLongClick = {
                                    menus.show(
                                        title = alarm.label,
                                        actions = listOf(
                                            // Bump the hour in place (no time-picker surface yet);
                                            // do NOT duplicate the alarm.
                                            MenuAction("edit time") {
                                                scope.launch {
                                                    val hour = (alarm.hour + 1) % 24
                                                    graph.alarms.setTime(alarm.id, hour, alarm.minute)
                                                    schedule(alarm.copy(hour = hour))
                                                }
                                            },
                                            MenuAction("delete") {
                                                scope.launch { graph.alarms.delete(alarm.id) }
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
    val apptsByDay = appts.groupBy {
        java.time.Instant.ofEpochMilli(it.startAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().dayOfMonth
    }
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 7) {
                    val dayIndex = r * 7 + c - firstWeekday + 1
                    val valid = dayIndex in 1..daysInMonth
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .background(if (valid) colors.elevated else Color.Transparent)
                            .combinedClickable(
                                enabled = valid,
                                onClick = {
                                    val date = monthStart.atDay(dayIndex)
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
                                },
                                onLongClick = {},
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (valid) dayIndex.toString() else "",
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (valid && apptsByDay.containsKey(dayIndex)) colors.accent else colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
