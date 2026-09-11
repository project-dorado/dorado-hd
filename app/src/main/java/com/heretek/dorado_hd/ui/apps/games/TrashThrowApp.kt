package com.heretek.dorado_hd.ui.apps.games

import android.graphics.Paint
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/* ============================================================ */
/*                          Trash Throw                           */
/* ============================================================ */

private const val TRASH_VW = 272f
private const val TRASH_VH = 480f

@Composable
fun TrashThrowApp() {
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
    var game by remember { mutableStateOf<TrashThrowState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var scores by remember { mutableStateOf(TrashThrowEngine.TrashScores()) }
    var resetConfirm by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val history by graph.games.top("trash-throw", 60).collectAsState(initial = emptyList())

    var dragStartMs by remember { mutableStateOf(0L) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    fun play(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        scores = TrashThrowEngine.decodeScores(graph.appState.get("trash-throw.scores"))
        sound = graph.appState.get("trash-throw.sound") != "0"
        loaded = true
    }

    LaunchedEffect(scores, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("trash-throw.scores", TrashThrowEngine.encodeScores(scores))
    }

    LaunchedEffect(sound, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("trash-throw.sound", if (sound) "1" else "0")
    }

    // Physics loop at 60 Hz; score/miss settlement happens on phase transitions.
    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = game ?: continue
            val before = current.result
            val next = TrashThrowEngine.step(current, 16)
            game = next
            if (before != next.result) {
                when (next.result) {
                    ThrowResult.MADE -> {
                        val best = scores.best(next.scene)
                        if (next.streak > best) {
                            scores = TrashThrowEngine.recordBest(scores, next.scene, next.streak)
                            graph.games.record("trash-throw", next.streak, next.scene.label)
                            play("coin")
                        } else {
                            play("score")
                        }
                    }
                    ThrowResult.MISS -> play("lose")
                    ThrowResult.NONE -> Unit
                }
            }
        }
    }

    fun startScene(scene: TrashScene) {
        game = TrashThrowEngine.newGame(scene, System.currentTimeMillis().toInt())
        paused = false
        screen = "game"
    }

    if (screen == "menu") {
        TrashMenu(
            sound = sound,
            onSound = { sound = !sound },
            onNewGame = { screen = "scenes" },
            onScores = { screen = "scores" },
            onAbout = { screen = "about" },
        )
        return
    }
    if (screen == "scenes") {
        TrashSceneSelect(scores, onPick = { startScene(it) }, onBack = { screen = "menu" })
        return
    }
    if (screen == "scores") {
        TrashScoresScreen(
            scores = scores,
            history = history,
            confirm = resetConfirm,
            onReset = { resetConfirm = true },
            onCancel = { resetConfirm = false },
            onConfirm = {
                scores = TrashThrowEngine.TrashScores()
                resetConfirm = false
            },
            onBack = { screen = "menu" },
        )
        return
    }
    if (screen == "about") {
        TrashAbout(onBack = { screen = "menu" })
        return
    }

    val current = game ?: return
    DetailScaffold(title = "trash throw · ${current.scene.label}", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "score ${current.streak}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "best ${scores.best(current.scene)}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = windLabel(current),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.width(8.dp))
                    EdgeText("pause", colors.textPrimary) { paused = true }
                }
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val view = trashView(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(current.phase, paused) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragStartMs = System.currentTimeMillis()
                                        dragX = 0f
                                        dragY = 0f
                                    },
                                    onDrag = { _, drag ->
                                        dragX += drag.x
                                        dragY += drag.y
                                    },
                                    onDragEnd = {
                                        val ticks = ((System.currentTimeMillis() - dragStartMs) / 16L).toInt()
                                        val currentGame = game
                                        if (currentGame != null &&
                                            TrashThrowEngine.isSwipe(dragX, dragY, ticks) &&
                                            currentGame.phase == ThrowPhase.WAITING
                                        ) {
                                            game = TrashThrowEngine.flick(currentGame, dragX)
                                            play("toss")
                                        }
                                    },
                                )
                            },
                    ) {
                        drawTrashScene(current, view, colors)
                    }
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = if (current.phase == ThrowPhase.WAITING) "swipe to throw" else " ",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
            }

            if (paused) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.94f))
                        .padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        text = "paused",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    EdgeText("resume", colors.textPrimary) { paused = false }
                    EdgeText("main menu", colors.textPrimary) { screen = "menu"; game = null; paused = false }
                    EdgeText("sound ${if (sound) "on" else "off"}", colors.accent) { sound = !sound }
                }
            }
        }
    }
}

@Composable
private fun TrashMenu(
    sound: Boolean,
    onSound: () -> Unit,
    onNewGame: () -> Unit,
    onScores: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "trash throw") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            trashButton("new game", Modifier.fillMaxWidth()) { onNewGame() }
            trashButton("high scores", Modifier.fillMaxWidth()) { onScores() }
            trashButton("about", Modifier.fillMaxWidth()) { onAbout() }
            trashButton("turn sound ${if (sound) "off" else "on"}", Modifier.fillMaxWidth()) { onSound() }
        }
    }
}

@Composable
private fun TrashSceneSelect(scores: TrashThrowEngine.TrashScores, onPick: (TrashScene) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "trash throw · scenes") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrashScene.entries.forEach { scene ->
                trashButton("${scene.label} · best ${scores.best(scene)}", Modifier.fillMaxWidth()) { onPick(scene) }
            }
            Spacer(Modifier.height(4.dp))
            trashButton("back", Modifier.fillMaxWidth()) { onBack() }
        }
    }
}

@Composable
private fun TrashScoresScreen(
    scores: TrashThrowEngine.TrashScores,
    history: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    confirm: Boolean,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "trash throw · high scores") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                text = "overall best ${scores.overall}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            TrashScene.entries.forEach { scene ->
                BasicText(
                    text = "${scene.label} best ${scores.best(scene)}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
            }
            if (history.isNotEmpty()) {
                BasicText(
                    text = "recent runs",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                history.take(6).forEach { row ->
                    BasicText(
                        text = "${row.meta ?: "?"} · streak ${row.score}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (confirm) {
                BasicText(
                    text = "reset all scores?",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accentBright),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EdgeText("reset", colors.accent) { onConfirm() }
                    EdgeText("cancel", colors.textPrimary) { onCancel() }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EdgeText("reset scores", colors.textPrimary) { onReset() }
                    EdgeText("back", colors.textPrimary) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun TrashAbout(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "trash throw · about") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                text = "Flick a crumpled ball into the bin. Wind drifts the throw; " +
                    "a made basket extends the streak, a miss resets it. " +
                    "Three scenes, three records.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(4.dp))
            EdgeText("back", colors.textPrimary) { onBack() }
        }
    }
}

@Composable
private fun trashButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

private class TrashView(val scale: Float, val ox: Float, val oy: Float) {
    fun x(v: Float): Float = ox + v * scale
    fun y(v: Float): Float = oy + v * scale
    fun s(v: Float): Float = v * scale
}

private fun trashView(width: Float, height: Float): TrashView {
    val scale = min(width / TRASH_VW, height / TRASH_VH)
    return TrashView(scale, (width - TRASH_VW * scale) / 2f, (height - TRASH_VH * scale) / 2f)
}

private fun windLabel(state: TrashThrowState): String {
    val value = abs(state.wind)
    val arrow = if (state.wind < 0f) "←" else "→"
    return "wind $arrow %.1f".format(value)
}

private fun DrawScope.drawTrashScene(state: TrashThrowState, view: TrashView, colors: DoradoColors) {
    drawSceneBackdrop(state.scene, view, colors)
    drawWind(state, view, colors)
    drawBin(state, view, colors)
    drawBall(state, view, colors)
    if (state.result == ThrowResult.MADE) {
        val label = Paint().apply {
            isAntiAlias = true
            textSize = view.s(16f)
            textAlign = Paint.Align.CENTER
            color = colors.accentBright.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("+1", view.x(TRASH_VW / 2f), view.y(120f), label)
    }
}

private fun DrawScope.drawSceneBackdrop(scene: TrashScene, view: TrashView, colors: DoradoColors) {
    val wallBottom = 300f
    val wallBrush = Brush.verticalGradient(
        colors = listOf(colors.background, colors.elevated),
        startY = view.y(0f),
        endY = view.y(wallBottom),
    )
    drawRect(wallBrush, Offset(view.x(0f), view.y(0f)), Size(view.s(TRASH_VW), view.s(wallBottom)))
    drawRect(colors.tile, Offset(view.x(0f), view.y(wallBottom)), Size(view.s(TRASH_VW), view.s(TRASH_VH - wallBottom)))
    when (scene) {
        TrashScene.BEDROOM -> {
            drawRect(colors.tilePressed, Offset(view.x(24f), view.y(60f)), Size(view.s(90f), view.s(80f)))
            drawRect(colors.accent.copy(alpha = 0.5f), Offset(view.x(28f), view.y(64f)), Size(view.s(82f), view.s(72f)))
            drawRect(colors.tilePressed, Offset(view.x(150f), view.y(230f)), Size(view.s(110f), view.s(50f)))
            drawRect(colors.border, Offset(view.x(150f), view.y(226f)), Size(view.s(110f), view.s(6f)))
        }
        TrashScene.OFFICE -> {
            drawRect(colors.tilePressed, Offset(view.x(20f), view.y(200f)), Size(view.s(120f), view.s(8f)))
            drawRect(colors.tilePressed, Offset(view.x(30f), view.y(208f)), Size(view.s(8f), view.s(70f)))
            drawRect(colors.tilePressed, Offset(view.x(122f), view.y(208f)), Size(view.s(8f), view.s(70f)))
            drawRect(colors.tilePressed, Offset(view.x(40f), view.y(150f)), Size(view.s(70f), view.s(50f)))
            drawRect(colors.accent.copy(alpha = 0.4f), Offset(view.x(44f), view.y(154f)), Size(view.s(62f), view.s(42f)))
        }
        TrashScene.DENTIST -> {
            for (col in 0..6) {
                drawLine(
                    color = colors.border,
                    start = Offset(view.x(20f + col * 38f), view.y(40f)),
                    end = Offset(view.x(20f + col * 38f), view.y(300f)),
                    strokeWidth = view.s(0.8f),
                )
            }
            for (row in 0..6) {
                drawLine(
                    color = colors.border,
                    start = Offset(view.x(20f), view.y(40f + row * 43f)),
                    end = Offset(view.x(248f), view.y(40f + row * 43f)),
                    strokeWidth = view.s(0.8f),
                )
            }
            drawRect(colors.tilePressed, Offset(view.x(150f), view.y(190f)), Size(view.s(90f), view.s(70f)))
            drawRect(colors.accent.copy(alpha = 0.35f), Offset(view.x(120f), view.y(180f)), Size(view.s(80f), view.s(16f)))
        }
    }
}

private fun DrawScope.drawWind(state: TrashThrowState, view: TrashView, colors: DoradoColors) {
    val magnitude = abs(state.wind) / state.scene.windMax
    if (magnitude <= 0.01f) return
    val length = view.s(30f + 60f * magnitude)
    val y = view.y(46f)
    val left = view.x(TRASH_VW / 2f) - length / 2f
    val right = view.x(TRASH_VW / 2f) + length / 2f
    val direction = if (state.wind < 0f) -1f else 1f
    drawLine(colors.accent, Offset(left, y), Offset(right, y), strokeWidth = view.s(2f))
    val headX = if (direction < 0) left else right
    val head = Path().apply {
        moveTo(headX, y)
        lineTo(headX - direction * view.s(7f), y - view.s(5f))
        lineTo(headX - direction * view.s(7f), y + view.s(5f))
        close()
    }
    drawPath(head, colors.accentBright)
}

private fun DrawScope.drawBin(state: TrashThrowState, view: TrashView, colors: DoradoColors) {
    val scene = state.scene
    val top = TrashThrowEngine.project(scene, Vec3(scene.goalX, scene.topHeight, scene.goalZ))
    val base = TrashThrowEngine.project(scene, Vec3(scene.goalX, 0f, scene.goalZ))
    if (top.scale <= 0f) return
    val topY = view.y(top.y)
    val baseY = view.y(base.y)
    val topRadius = view.s(top.scale * scene.radiusTop)
    val baseRadius = view.s(base.scale * scene.radiusBase)
    val cx = view.x(top.x)
    val body = Path().apply {
        moveTo(cx - topRadius, topY)
        lineTo(cx + topRadius, topY)
        lineTo(cx + baseRadius, baseY)
        lineTo(cx - baseRadius, baseY)
        close()
    }
    drawPath(body, color = colors.tilePressed)
    drawPath(body, color = colors.border, style = Stroke(view.s(1f)))
    drawOval(
        color = colors.accent.copy(alpha = 0.8f),
        topLeft = Offset(cx - topRadius, topY - view.s(4f)),
        size = Size(topRadius * 2f, view.s(8f)),
        style = Stroke(view.s(2f)),
    )
}

private fun DrawScope.drawBall(state: TrashThrowState, view: TrashView, colors: DoradoColors) {
    val projected = TrashThrowEngine.project(state.scene, state.pos)
    if (projected.scale <= 0f) return
    val alpha = if (state.phase == ThrowPhase.RESOLVED) {
        (state.fadeMs.toFloat() / TrashThrowEngine.FADE_MS).coerceIn(0f, 1f)
    } else {
        1f
    }
    if (alpha <= 0f) return
    val radius = view.s(projected.scale * TrashThrowEngine.BALL_RADIUS)
    drawCircle(
        color = colors.textPrimary.copy(alpha = alpha),
        radius = radius,
        center = Offset(view.x(projected.x), view.y(projected.y)),
    )
    drawCircle(
        color = colors.border.copy(alpha = alpha),
        radius = radius,
        center = Offset(view.x(projected.x), view.y(projected.y)),
        style = Stroke(view.s(1f)),
    )
}
