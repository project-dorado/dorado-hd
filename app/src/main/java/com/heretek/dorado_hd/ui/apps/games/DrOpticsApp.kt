package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private const val DR_OPTICS_SLUG = "dr-optics-light-lab"
private const val DR_OPTICS_SAVE = "dr-optics-light-lab"
private const val DR_OPTICS_HISTORY = "dr-optics-history"

/**
 * Dr Optics Light Lab: drag mirrors, lenses, prisms and rocks around a
 * top-down room and bounce the authored laser into every colour-matched sink.
 */
@Composable
fun DrOpticsApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var difficultyTab by remember { mutableStateOf(1) }
    var game by remember { mutableStateOf<DrOpticsState?>(null) }
    var saved by remember { mutableStateOf<DrOpticsState?>(null) }
    var solvedLevels by remember { mutableStateOf(emptySet<Int>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        saved = graph.appState.get(DR_OPTICS_SAVE)?.let { DrOpticsEngine.decode(it) }
        solvedLevels = graph.appState.get(DR_OPTICS_HISTORY)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        loaded = true
    }

    // Periodic autosave: reads the latest state without restarting per frame.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (!loaded) continue
            val current = game
            if (screen == "game" && current != null && current.status == OpticsStatus.PLAYING) {
                graph.appState.put(DR_OPTICS_SAVE, DrOpticsEngine.encode(current))
            }
        }
    }

    LaunchedEffect(screen, paused, game?.levelIndex) {
        if (screen != "game" || paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val current = game
                if (last == 0L) {
                    last = now
                } else {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
                    last = now
                    if (current != null && current.status == OpticsStatus.PLAYING) {
                        game = DrOpticsEngine.step(current, dt)
                    }
                }
            }
        }
    }

    LaunchedEffect(game?.status) {
        val current = game ?: return@LaunchedEffect
        when (current.status) {
            OpticsStatus.SOLVED -> {
                bank.play("win")
                if (!recorded) {
                    recorded = true
                    val level = DrOpticsEngine.level(current.levelIndex)
                    graph.games.record(
                        DR_OPTICS_SLUG,
                        (current.completion * 100f).roundToInt(),
                        "level ${current.levelIndex + 1} · diff ${level.difficulty} · ${current.moves} moves · ${current.clockMs / 1000}s",
                    )
                    solvedLevels = solvedLevels + current.levelIndex
                    scope.launch {
                        graph.appState.put(DR_OPTICS_HISTORY, solvedLevels.sorted().joinToString(","))
                        graph.appState.clear(DR_OPTICS_SAVE)
                    }
                    saved = null
                }
            }
            OpticsStatus.FAILED -> bank.play("lose")
            OpticsStatus.PLAYING -> Unit
        }
    }

    fun startLevel(index: Int) {
        game = DrOpticsEngine.start(index)
        selected = null
        recorded = false
        paused = false
        message = ""
        screen = "game"
        bank.play("select")
    }

    fun resume() {
        val current = saved ?: return
        game = current
        selected = null
        recorded = false
        paused = false
        message = ""
        screen = "game"
        bank.play("select")
    }

    if (screen == "menu") {
        DrOpticsMenu(
            saved = saved,
            solved = solvedLevels.size,
            onContinue = { resume() },
            onPlay = { screen = "levels" },
            onLearn = { screen = "help" },
        )
        return
    }
    if (screen == "help") {
        DrOpticsHelp(onBack = { screen = "menu" })
        return
    }
    if (screen == "levels") {
        DrOpticsLevelSelect(
            solved = solvedLevels,
            difficulty = difficultyTab,
            onDifficulty = { difficultyTab = it },
            onPick = { startLevel(it) },
            onLocked = {
                bank.play("error")
                message = "keep solving to unlock this tier"
            },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    val level = DrOpticsEngine.level(current.levelIndex)
    DetailScaffold(title = "dr optics", onBack = { paused = true }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .appDescription(
                        "level ${current.levelIndex + 1}, ${level.name}, moves ${current.moves}, beams ${current.beams.size}, ${(current.completion * 100f).roundToInt()}% complete",
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "level ${current.levelIndex + 1} · ${level.name}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "moves ${current.moves}" + if (current.maxMoves > 0) " / ${current.maxMoves}" else "",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "beams ${current.beams.size}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "complete ${(current.completion * 100f).roundToInt()}%",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(current.levelIndex, paused) {
                            detectTapGestures { offset ->
                                val snapshot = game
                                if (!paused && snapshot != null && snapshot.status == OpticsStatus.PLAYING) {
                                    val point = toDesignPoint(size, offset)
                                    val picked = DrOpticsEngine.pick(snapshot, point)
                                    selected = picked?.id
                                    bank.play(if (picked != null) "select" else "back")
                                }
                            }
                        }
                        .pointerInput(current.levelIndex, paused) {
                            var dragId: Int? = null
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val snapshot = game
                                    if (!paused && snapshot != null && snapshot.status == OpticsStatus.PLAYING) {
                                        val picked = DrOpticsEngine.pick(snapshot, toDesignPoint(size, offset))
                                        if (picked != null) {
                                            selected = picked.id
                                            dragId = picked.id
                                        }
                                    }
                                },
                                onDragCancel = { dragId = null },
                                onDragEnd = {
                                    val snapshot = game
                                    if (dragId != null && snapshot != null) {
                                        game = DrOpticsEngine.commit(snapshot)
                                        bank.play("click")
                                    }
                                    dragId = null
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val id = dragId
                                    val snapshot = game
                                    if (id != null && snapshot != null && snapshot.status == OpticsStatus.PLAYING) {
                                        val delta = toDesignDelta(size, amount)
                                        game = DrOpticsEngine.translate(snapshot, id, delta.x, delta.y)
                                    }
                                },
                            )
                        }
                        .appDescription(
                            "optics board, level ${current.levelIndex + 1}, ${level.name}, ${(current.completion * 100f).roundToInt()}% complete",
                        ),
                ) {
                    drawOpticsScene(current, colors, selected)
                }

                if (paused) {
                    DrOpticsOverlay(
                        title = "paused",
                        "resume" to { paused = false },
                        "restart" to { startLevel(current.levelIndex) },
                        "levels" to { paused = false; screen = "levels" },
                        "main menu" to { paused = false; screen = "menu"; game = null },
                    )
                } else if (current.solved) {
                    val next = current.levelIndex + 1
                    val nextUnlocked = next < DrOpticsEngine.levels.size &&
                        DrOpticsEngine.isDifficultyUnlocked(DrOpticsEngine.level(next).difficulty, solvedLevels.size)
                    DrOpticsOverlay(
                        title = "level solved",
                        "next level" to {
                            if (nextUnlocked) startLevel(next) else screen = "levels"
                        },
                        "replay" to { startLevel(current.levelIndex) },
                        "levels" to { screen = "levels" },
                    )
                } else if (current.failed) {
                    DrOpticsOverlay(
                        title = "out of moves",
                        "retry" to { startLevel(current.levelIndex) },
                        "levels" to { screen = "levels" },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            DrOpticsControls(
                state = current,
                selected = selected,
                onSelect = { selected = it },
                onRotate = { id, delta ->
                    game = DrOpticsEngine.rotateElement(current, id, delta)
                    bank.play("rotate")
                },
                onGrow = { id, factor ->
                    game = DrOpticsEngine.growElement(current, id, factor)
                    bank.play("tick")
                },
                onLock = { id ->
                    game = DrOpticsEngine.toggleLock(current, id)
                    bank.play("click")
                },
                onRestart = { startLevel(current.levelIndex) },
                onLevels = { screen = "levels" },
            )
            if (message.isNotEmpty()) {
                BasicText(
                    text = message,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
        }
    }
}

@Composable
private fun DrOpticsMenu(
    saved: DrOpticsState?,
    solved: Int,
    onContinue: () -> Unit,
    onPlay: () -> Unit,
    onLearn: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "dr optics") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                DrOpticsButton("continue level ${saved.levelIndex + 1}", Modifier.fillMaxWidth()) { onContinue() }
                Spacer(Modifier.height(6.dp))
            }
            DrOpticsButton("play", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            DrOpticsButton("learn to play", Modifier.fillMaxWidth()) { onLearn() }
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "solved $solved of ${DrOpticsEngine.LEVEL_COUNT}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "bend the laser into every colour-matched sink. drag to place, tap to select, then rotate or resize.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun DrOpticsHelp(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "dr optics · learn", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val lines = listOf(
                "drag mirrors, lenses and rocks onto the beam",
                "tap an object to select it",
                "rotate the selection in 15 degree steps",
                "grow or shrink a lens or mirror to refocus",
                "mirrors reflect, lenses bend light, rocks block",
                "arcs, prisms and swinging mirrors are trickier",
                "a sink only accepts its own colour",
                "light every sink to solve the level",
            )
            lines.forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(10.dp))
            DrOpticsButton("back to menu", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun DrOpticsLevelSelect(
    solved: Set<Int>,
    difficulty: Int,
    onDifficulty: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onLocked: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    var notice by remember { mutableStateOf("") }
    DetailScaffold(title = "dr optics · levels", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "choose a level · solved ${solved.size} of ${DrOpticsEngine.LEVEL_COUNT}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (tier in 1..6) {
                    val unlocked = DrOpticsEngine.isDifficultyUnlocked(tier, solved.size)
                    DrOpticsButton(
                        label = "$tier",
                        modifier = Modifier.weight(1f),
                        active = tier == difficulty,
                    ) {
                        if (unlocked) {
                            notice = ""
                            onDifficulty(tier)
                        } else {
                            onLocked()
                            notice = "tier $tier unlocks at ${DrOpticsEngine.requiredSolvedForDifficulty(tier)} solved levels"
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val levelList = DrOpticsEngine.levels.filter { it.difficulty == difficulty }
            val tierUnlocked = DrOpticsEngine.isDifficultyUnlocked(difficulty, solved.size)
            val done = levelList.count { solved.contains(it.index) }
            BasicText(
                text = "difficulty $difficulty · $done of ${levelList.size} solved",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            Spacer(Modifier.height(4.dp))
            if (!tierUnlocked) {
                val need = DrOpticsEngine.requiredSolvedForDifficulty(difficulty) - solved.size
                BasicText(
                    text = "locked · solve $need more level${if (need == 1) "" else "s"}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textInactive),
                )
            } else {
                levelList.chunked(6).forEach { chunk ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chunk.forEach { level ->
                            DrOpticsButton(
                                label = "${level.index + 1}",
                                modifier = Modifier.weight(1f),
                                active = solved.contains(level.index),
                            ) { onPick(level.index) }
                        }
                        repeat(6 - chunk.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (notice.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = notice,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.weight(1f))
            DrOpticsButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun DrOpticsControls(
    state: DrOpticsState,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onRotate: (Int, Float) -> Unit,
    onGrow: (Int, Float) -> Unit,
    onLock: (Int) -> Unit,
    onRestart: () -> Unit,
    onLevels: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val element = state.elements.firstOrNull { it.id == selected }
    Column(Modifier.fillMaxWidth()) {
        if (element != null) {
            BasicText(
                text = "${element.kind.label()} selected · ${element.rotationDeg.roundToInt()}°",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(3.dp))
            // Six controls wrapped instead of one clipped row (adaptive width).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                DrOpticsButton("-15") { onRotate(element.id, -15f) }
                DrOpticsButton("+15") { onRotate(element.id, 15f) }
                DrOpticsButton("smaller") { onGrow(element.id, 0.9f) }
                DrOpticsButton("larger") { onGrow(element.id, 1.1f) }
                DrOpticsButton(if (element.locked) "unlock" else "lock") { onLock(element.id) }
                DrOpticsButton("clear") { onSelect(null) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "tap an object to select it",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.weight(1f))
                DrOpticsButton("restart") { onRestart() }
                Spacer(Modifier.width(4.dp))
                DrOpticsButton("levels") { onLevels() }
            }
        }
    }
}

@Composable
private fun DrOpticsOverlay(title: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                actions.forEach { (label, action) ->
                    DrOpticsButton(label) { action() }
                }
            }
        }
    }
}

@Composable
private fun DrOpticsButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .appTap(label = label, enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}

private fun OpticsKind.label(): String = when (this) {
    OpticsKind.LASER -> "laser"
    OpticsKind.PLANE_MIRROR -> "mirror"
    OpticsKind.ARC_MIRROR -> "arc mirror"
    OpticsKind.RECT_MIRROR -> "block mirror"
    OpticsKind.POLY_MIRROR -> "prism"
    OpticsKind.OSCILLATING_MIRROR -> "swinging mirror"
    OpticsKind.LENS -> "lens"
    OpticsKind.ROCK -> "rock"
    OpticsKind.SINK -> "sink"
}

private fun designScale(size: IntSize): Float =
    min(size.width / DrOpticsEngine.DESIGN_W, size.height / DrOpticsEngine.DESIGN_H)

private fun toDesignPoint(size: IntSize, offset: Offset): OpticsVec {
    val scale = designScale(size)
    val ox = (size.width - DrOpticsEngine.DESIGN_W * scale) / 2f
    val oy = (size.height - DrOpticsEngine.DESIGN_H * scale) / 2f
    return OpticsVec((offset.x - ox) / scale, (offset.y - oy) / scale)
}

private fun toDesignDelta(size: IntSize, offset: Offset): OpticsVec {
    val scale = designScale(size)
    return OpticsVec(offset.x / scale, offset.y / scale)
}

private fun OpticsVec.toOffset() = Offset(x, y)

private fun channelColor(channel: OpticsColor): Color = when (channel) {
    OpticsColor.RED -> DoradoAccent.PINK.primary
    OpticsColor.GREEN -> DoradoAccent.LIME.primary
    OpticsColor.BLUE -> DoradoAccent.CYAN.primary
}

private fun DrawScope.drawOpticsScene(state: DrOpticsState, colors: DoradoColors, selectedId: Int?) {
    drawRect(colors.background, Offset.Zero, Size(DrOpticsEngine.DESIGN_W, DrOpticsEngine.DESIGN_H))
    var gx = 40f
    while (gx < DrOpticsEngine.DESIGN_W) {
        drawLine(colors.textWatermark, Offset(gx, 0f), Offset(gx, DrOpticsEngine.DESIGN_H), strokeWidth = 1f)
        gx += 40f
    }
    var gy = 40f
    while (gy < DrOpticsEngine.DESIGN_H) {
        drawLine(colors.textWatermark, Offset(0f, gy), Offset(DrOpticsEngine.DESIGN_W, gy), strokeWidth = 1f)
        gy += 40f
    }
    drawRect(
        colors.border,
        Offset.Zero,
        Size(DrOpticsEngine.DESIGN_W, DrOpticsEngine.DESIGN_H),
        style = Stroke(1.5f),
    )
    state.beams.forEach { beam ->
        val color = channelColor(beam.color)
        drawLine(color.copy(alpha = 0.20f), beam.start.toOffset(), beam.end.toOffset(), strokeWidth = 7f)
        drawLine(color.copy(alpha = 0.92f), beam.start.toOffset(), beam.end.toOffset(), strokeWidth = 2.2f)
    }
    state.elements.forEach { element ->
        drawOpticsElement(state, element, colors, element.id == selectedId)
    }
}

private fun DrawScope.drawOpticsElement(
    state: DrOpticsState,
    element: OpticsElement,
    colors: DoradoColors,
    selected: Boolean,
) {
    val pose = DrOpticsEngine.poseAt(element, state.clockMs)
    val body = if (selected) colors.accentBright else colors.textPrimary
    when (element.kind) {
        OpticsKind.LASER -> {
            val color = channelColor(element.color ?: OpticsColor.RED)
            val tip = pose.pos + pose.dir * 22f
            drawLine(color.copy(alpha = 0.5f), pose.pos.toOffset(), tip.toOffset(), strokeWidth = 6f)
            drawLine(color, pose.pos.toOffset(), tip.toOffset(), strokeWidth = 2.5f)
            drawCircle(color, 5f, pose.pos.toOffset())
            drawCircle(color.copy(alpha = 0.35f), 9f, pose.pos.toOffset(), style = Stroke(1.5f))
        }
        OpticsKind.SINK -> {
            val color = channelColor(element.color ?: OpticsColor.GREEN)
            val stored = state.sinks.firstOrNull { it.id == element.id }
            drawCircle(color.copy(alpha = 0.14f), element.size, pose.pos.toOffset())
            if (stored != null && stored.fill > 0f) {
                drawArc(
                    color.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = 360f * stored.fill,
                    useCenter = true,
                    topLeft = Offset(pose.pos.x - element.size, pose.pos.y - element.size),
                    size = Size(element.size * 2f, element.size * 2f),
                )
            }
            drawCircle(color, element.size, pose.pos.toOffset(), style = Stroke(2f))
            if (stored?.satisfied == true) {
                drawCircle(color, element.size * 0.35f, pose.pos.toOffset())
            }
        }
        OpticsKind.PLANE_MIRROR, OpticsKind.OSCILLATING_MIRROR -> {
            val tangent = OpticsVec.dir(pose.rotationDeg)
            val a = pose.pos - tangent * (element.size * 0.5f)
            val b = pose.pos + tangent * (element.size * 0.5f)
            drawLine(body.copy(alpha = 0.35f), a.toOffset(), b.toOffset(), strokeWidth = 8f)
            drawLine(body, a.toOffset(), b.toOffset(), strokeWidth = if (selected) 5f else 3.5f)
        }
        OpticsKind.ARC_MIRROR -> {
            drawArc(
                body,
                startAngle = pose.rotationDeg - element.spanDeg * 0.5f,
                sweepAngle = element.spanDeg,
                useCenter = false,
                topLeft = Offset(pose.pos.x - element.size, pose.pos.y - element.size),
                size = Size(element.size * 2f, element.size * 2f),
                style = Stroke(if (selected) 5f else 3.5f),
            )
        }
        OpticsKind.RECT_MIRROR -> drawBlockElement(
            element,
            pose,
            body.copy(alpha = 0.25f),
            body,
            DrOpticsEngine.RECT_MIRROR_THICKNESS,
            selected,
        )
        OpticsKind.ROCK -> drawBlockElement(
            element,
            pose,
            colors.elevated,
            if (selected) colors.accent else colors.border,
            element.size * DrOpticsEngine.ROCK_ASPECT,
            selected,
        )
        OpticsKind.POLY_MIRROR -> {
            val sideCount = element.sides.coerceAtLeast(3)
            val path = Path()
            for (i in 0 until sideCount) {
                val point = pose.pos + OpticsVec.dir(pose.rotationDeg + 360f * i / sideCount) * element.size
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, body, style = Stroke(if (selected) 5f else 3.5f))
        }
        OpticsKind.LENS -> {
            val lensColor = if (selected) colors.accentBright else DoradoAccent.CYAN.primary
            drawCircle(lensColor.copy(alpha = 0.14f), element.size, pose.pos.toOffset())
            drawCircle(lensColor, element.size, pose.pos.toOffset(), style = Stroke(if (selected) 4.5f else 3f))
        }
    }
}

private fun DrawScope.drawBlockElement(
    element: OpticsElement,
    pose: DrOpticsEngine.OpticsPose,
    fill: Color,
    stroke: Color,
    thickness: Float,
    selected: Boolean,
) {
    val axisX = OpticsVec.dir(pose.rotationDeg)
    val axisY = OpticsVec.dir(pose.rotationDeg + 90f)
    val halfW = element.size * 0.5f
    val halfH = thickness * 0.5f
    val corners = listOf(
        pose.pos - axisX * halfW - axisY * halfH,
        pose.pos + axisX * halfW - axisY * halfH,
        pose.pos + axisX * halfW + axisY * halfH,
        pose.pos - axisX * halfW + axisY * halfH,
    )
    val path = Path()
    path.moveTo(corners[0].x, corners[0].y)
    for (i in 1 until corners.size) path.lineTo(corners[i].x, corners[i].y)
    path.close()
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(if (selected) 4.5f else 2.5f))
}

