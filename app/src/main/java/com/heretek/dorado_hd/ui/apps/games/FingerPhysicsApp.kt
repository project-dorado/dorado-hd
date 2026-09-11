package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt

private const val FP_SLUG = "finger-physics"

@Composable
fun FingerPhysicsApp() {
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
    var progress by remember { mutableStateOf(FpProgress()) }
    var mode by remember { mutableStateOf(FpMode.EGG) }
    var game by remember { mutableStateOf<FpGame?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var dragId by remember { mutableStateOf(0) }
    var frame by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val redrawTick = frame
    val best by graph.games.top(FP_SLUG, 1).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = FingerPhysicsEngine.decodeProgress(graph.appState.get(FP_SLUG))
    }

    LaunchedEffect(game) {
        val current = game ?: return@LaunchedEffect
        if (current.status == FpStatus.PLAYING) {
            val saved = progress.copy(lastMode = current.mode, lastSlot = current.slot)
            graph.appState.put(FP_SLUG, FingerPhysicsEngine.encodeProgress(saved))
        }
    }

    val running = screen == "game" && game?.status == FpStatus.PLAYING && !paused
    LaunchedEffect(running, game) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
                    last = now
                    val current = game ?: return@withFrameNanos
                    FingerPhysicsEngine.tick(current, dt)
                    current.events.forEach { event ->
                        when (event) {
                            "spawn" -> bank.play("toss")
                            "break" -> bank.play("hit")
                            "weld" -> bank.play("coin")
                            "blast" -> bank.play("explode")
                            "flip" -> bank.play("click")
                            "win" -> bank.play("win")
                            "lose" -> bank.play("lose")
                        }
                    }
                    frame++
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.status) {
        val current = game ?: return@LaunchedEffect
        if (current.status == FpStatus.PLAYING || recorded) return@LaunchedEffect
        recorded = true
        progress = progress.record(current.level, current.medal)
        graph.appState.put(FP_SLUG, FingerPhysicsEngine.encodeProgress(progress))
        graph.games.record(
            FP_SLUG,
            progress.points,
            "${current.mode.label} ${current.slot}/9 · ${current.medal.name.lowercase()}",
        )
    }

    fun startLevel(level: FpLevel) {
        game = FingerPhysicsEngine.newGame(level)
        mode = level.mode
        recorded = false
        paused = false
        dragId = 0
        screen = "game"
    }

    fun continueRun() {
        val lastMode = progress.lastMode ?: return
        startLevel(FingerPhysicsEngine.level(lastMode, progress.lastSlot))
    }

    if (screen == "menu") {
        FingerPhysicsMenu(
            progress = progress,
            best = best.firstOrNull()?.score ?: 0,
            onPlay = { mode = progress.lastMode ?: FpMode.EGG; continueRun() },
            onContinue = { continueRun() },
            onMode = { picked ->
                mode = picked
                screen = "levels"
            },
        )
        return
    }

    if (screen == "levels") {
        FingerPhysicsLevels(
            mode = mode,
            progress = progress,
            onPick = { slot -> startLevel(FingerPhysicsEngine.level(mode, slot)) },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "finger physics", onBack = { paused = true }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "${current.mode.label} ${current.slot}/9",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = FingerPhysicsEngine.goalDescription(current.mode),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.weight(1f))
                FpButton(if (paused) "play" else "pause") { paused = !paused }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "shapes ${current.spawnRemaining}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = when (current.mode.medalKind) {
                        FpMedalKind.HEIGHT -> "stack ${current.maxHeight.roundToInt()}/${current.level.heightToWin.roundToInt()}"
                        FpMedalKind.TIME -> "time ${(current.timeLeftMs() / 1000L)}s"
                    },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "stars ${progress.points}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val scale = min(maxWidth.value / FingerPhysicsEngine.DESIGN_W, maxHeight.value / FingerPhysicsEngine.DESIGN_H)
                Canvas(
                    Modifier
                        .size(
                            (FingerPhysicsEngine.DESIGN_W * scale).dp,
                            (FingerPhysicsEngine.DESIGN_H * scale).dp,
                        )
                        .pointerInput(current.mode, current.status) {
                            detectTapGestures { offset ->
                                val point = fpDesignPoint(size, offset)
                                val picked = FingerPhysicsEngine.pickBody(current, point.x, point.y)
                                if (current.mode == FpMode.GRAVITY && picked != 0) {
                                    FingerPhysicsEngine.flipGravity(current, picked)
                                    bank.play("click")
                                } else {
                                    FingerPhysicsEngine.spawnNext(current)
                                }
                            }
                        }
                        .pointerInput(current.mode, current.status) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val point = fpDesignPoint(size, offset)
                                    val picked = FingerPhysicsEngine.pickBody(current, point.x, point.y)
                                    if (picked != 0) {
                                        FingerPhysicsEngine.beginDrag(current, picked)
                                        dragId = picked
                                    }
                                },
                                onDragEnd = {
                                    if (dragId != 0) FingerPhysicsEngine.endDrag(current)
                                    dragId = 0
                                },
                                onDragCancel = {
                                    if (dragId != 0) FingerPhysicsEngine.endDrag(current)
                                    dragId = 0
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val point = fpDesignPoint(size, change.position)
                                    FingerPhysicsEngine.dragTo(current, point.x, point.y)
                                },
                            )
                        },
                ) {
                    drawFingerWorld(current, colors)
                }
                if (paused) {
                    FpOverlay(
                        "paused",
                        "resume" to { paused = false },
                        "restart" to { startLevel(current.level) },
                        "levels" to { paused = false; game = null; screen = "levels" },
                        "main menu" to { paused = false; game = null; screen = "menu" },
                    )
                } else if (current.status == FpStatus.WON) {
                    FpOverlay(
                        "level clear · ${current.medal.name.lowercase()}",
                        "next" to {
                            val next = FingerPhysicsEngine.nextSlot(current.slot)
                            if (next != null) {
                                startLevel(FingerPhysicsEngine.level(current.mode, next))
                            } else {
                                paused = false
                                game = null
                                screen = "levels"
                            }
                        },
                        "replay" to { startLevel(current.level) },
                        "main menu" to { game = null; screen = "menu" },
                    )
                } else if (current.status == FpStatus.LOST) {
                    FpOverlay(
                        current.message.ifEmpty { "failed" },
                        "retry" to { startLevel(current.level) },
                        "main menu" to { game = null; screen = "menu" },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                FpButton("drop", Modifier.weight(1f), enabled = current.spawnRemaining > 0 && current.status == FpStatus.PLAYING && !paused) {
                    FingerPhysicsEngine.spawnNext(current)
                }
                FpButton("retry", Modifier.weight(1f), enabled = !paused) { startLevel(current.level) }
                FpButton("menu", Modifier.weight(1f), enabled = !paused) { game = null; screen = "menu" }
            }
        }
    }
}

@Composable
private fun FingerPhysicsMenu(
    progress: FpProgress,
    best: Int,
    onPlay: () -> Unit,
    onContinue: () -> Unit,
    onMode: (FpMode) -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "finger physics") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val last = progress.lastMode
            if (last != null) {
                FpButton("continue ${last.label} ${progress.lastSlot}/9", Modifier.fillMaxWidth()) { onContinue() }
                Spacer(Modifier.height(6.dp))
            } else {
                FpButton("play", Modifier.fillMaxWidth()) { onPlay() }
                Spacer(Modifier.height(6.dp))
            }
            BasicText(
                text = "modes",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(3.dp))
            FpMode.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEach { entry ->
                        FpButton(entry.label, Modifier.weight(1f)) { onMode(entry) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "stars ${progress.points} · best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "10 modes · 90 levels · drag shapes, tap to drop",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun FingerPhysicsLevels(
    mode: FpMode,
    progress: FpProgress,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "finger physics · ${mode.label}", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = FingerPhysicsEngine.goalDescription(mode),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            FingerPhysicsEngine.levelsFor(mode).forEach { level ->
                val medal = progress.best(level)
                val label = "level ${level.slot} · ${medalLabel(medal)}"
                FpButton(label, Modifier.fillMaxWidth()) { onPick(level.slot) }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

private fun medalLabel(medal: FpMedal): String = when (medal) {
    FpMedal.GOLD -> "gold"
    FpMedal.SILVER -> "silver"
    FpMedal.BRONZE -> "bronze"
    FpMedal.NONE -> "unplayed"
}

private fun fpDesignPoint(size: IntSize, offset: Offset): P2Vec {
    if (size.width <= 0 || size.height <= 0) return P2Vec(0f, 0f)
    return P2Vec(
        offset.x / size.width * FingerPhysicsEngine.DESIGN_W,
        offset.y / size.height * FingerPhysicsEngine.DESIGN_H,
    )
}

private fun DrawScope.drawFingerWorld(game: FpGame, colors: DoradoColors) {
    val scale = size.width / FingerPhysicsEngine.DESIGN_W
    drawRect(colors.background)

    if (game.mode == FpMode.UNDERWATER) {
        val waterY = FingerPhysicsEngine.WATER_LINE * scale
        drawRect(
            DoradoAccent.CYAN.primary.copy(alpha = 0.14f),
            Offset(0f, waterY),
            Size(size.width, size.height - waterY),
        )
        drawRect(DoradoAccent.CYAN.bright.copy(alpha = 0.55f), Offset(0f, waterY), Size(size.width, 1.5f))
    }

    game.goalZone?.let { zone ->
        drawRect(
            colors.accent.copy(alpha = 0.14f),
            Offset(zone.x0 * scale, zone.y0 * scale),
            Size((zone.x1 - zone.x0) * scale, (zone.y1 - zone.y0) * scale),
        )
        drawRect(
            colors.accent.copy(alpha = 0.55f),
            Offset(zone.x0 * scale, zone.y0 * scale),
            Size((zone.x1 - zone.x0) * scale, (zone.y1 - zone.y0) * scale),
            style = Stroke(width = 1.5f),
        )
    }

    if (game.mode.medalKind == FpMedalKind.HEIGHT) {
        drawHeightLine(game.level.bronze, DoradoAccent.ORANGE.primary.copy(alpha = 0.55f), scale)
        drawHeightLine(game.level.silver, colors.textSecondary, scale)
        drawHeightLine(game.level.gold, colors.accentBright, scale)
    }

    for (body in game.world.bodies) {
        val color = fpBodyColor(body, colors)
        if (body.shape == P2Shape.CIRCLE) {
            val center = Offset(body.x * scale, body.y * scale)
            drawCircle(color, body.radius * scale, center)
            if (body.tag == "charged") {
                drawCircle(colors.background.copy(alpha = 0.5f), body.radius * 0.45f * scale, center, style = Stroke(1.5f))
            }
        } else {
            rotate(degrees = body.angle * 180f / PI.toFloat(), pivot = Offset(body.x * scale, body.y * scale)) {
                drawRect(
                    color,
                    Offset((body.x - body.halfW) * scale, (body.y - body.halfH) * scale),
                    Size(body.halfW * 2f * scale, body.halfH * 2f * scale),
                )
            }
        }
    }
}

private fun DrawScope.drawHeightLine(height: Float, color: Color, scale: Float) {
    if (height <= 0f) return
    val y = (FingerPhysicsEngine.FLOOR_Y - height) * scale
    if (y < 0f || y > size.height) return
    drawRect(color, Offset(0f, y), Size(size.width, 1.5f))
}

private fun fpBodyColor(body: P2Body, colors: DoradoColors): Color = when {
    body.isStatic && body.tag == "floor" -> colors.elevated
    body.isStatic -> colors.border
    body.tag == "egg" -> colors.accent
    body.tag == "ball" -> DoradoAccent.CYAN.primary
    body.tag == "capsule" -> DoradoAccent.LIME.primary
    body.tag == "charged" -> DoradoAccent.PURPLE.primary
    body.tag == "float" -> DoradoAccent.CYAN.bright
    body.tag == "explosive" -> DoradoAccent.ORANGE.primary
    body.tag == "paddle" -> colors.accentBright
    body.tag == "pinned" -> DoradoAccent.LIME.bright
    else -> colors.textSecondary
}

@Composable
private fun FpOverlay(title: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            actions.forEach { (label, action) ->
                FpButton(label) { action() }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun FpButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(if (enabled) colors.tile else colors.elevated)
            .border(0.5.dp, colors.border)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
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
