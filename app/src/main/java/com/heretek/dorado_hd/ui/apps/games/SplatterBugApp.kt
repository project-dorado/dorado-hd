package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.min
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SplatterViewport(val scale: Float, val ox: Float, val oy: Float) {
    fun x(value: Float): Float = ox + value * scale
    fun y(value: Float): Float = oy + value * scale
    fun s(value: Float): Float = value * scale
}

private fun splatterViewport(width: Float, height: Float): SplatterViewport {
    val scale = min(width / SPLATTER_VIEW_W, height / SPLATTER_VIEW_H)
    return SplatterViewport(scale, (width - SPLATTER_VIEW_W * scale) / 2f, (height - SPLATTER_VIEW_H * scale) / 2f)
}

private fun BugKind.hue(colors: DoradoColors): Color = when (this) {
    BugKind.ANT -> colors.textSecondary
    BugKind.BUTTERFLY -> DoradoAccent.PURPLE.primary
    BugKind.COCKROACH -> DoradoAccent.ORANGE.primary
    BugKind.EARWIG -> DoradoAccent.LIME.primary
    BugKind.LADYBIRD -> DoradoAccent.PINK.primary
    BugKind.SPIDER -> colors.border
    BugKind.TARANTULA -> DoradoAccent.ORANGE.bright
}

@Composable
fun SplatterBugApp() {
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
    var game by remember { mutableStateOf<SplatterBugState?>(null) }
    var saved by remember { mutableStateOf<SplatterBugState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    val pendingTaps = remember { mutableStateListOf<BugTap>() }
    val labelPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val scores by graph.games.top("splatter-bug", 1).collectAsState(initial = emptyList())
    val best = scores.firstOrNull()?.score ?: 0

    fun cue(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        saved = graph.appState.get("splatter-bug")?.let { SplatterBugEngine.decode(it) }
        sound = graph.appState.get("splatter-bug-sound") != "0"
    }

    LaunchedEffect(screen, paused) {
        while (screen == "game" && !paused) {
            val current = game ?: break
            if (current.over) break
            delay(33L)
            val taps = pendingTaps.toList()
            pendingTaps.clear()
            val next = SplatterBugEngine.step(current, 33L, taps)
            game = next
            next.events.forEach { event ->
                when (event) {
                    is SplatterBugEvent.Spawned -> if (event.kind == BugKind.TARANTULA) cue("pop")
                    is SplatterBugEvent.Squish -> when {
                        event.harmless -> cue("error")
                        event.points > 0 -> cue("score")
                        else -> cue("hit")
                    }
                    is SplatterBugEvent.Escaped -> if (event.harmless) cue("coin") else cue("lose")
                    SplatterBugEvent.TarantulaBurst -> cue("explode")
                    SplatterBugEvent.GameOver -> cue("lose")
                }
            }
            if (next.elapsedMs / 3_000L > current.elapsedMs / 3_000L) {
                val blob = SplatterBugEngine.encode(next)
                scope.launch(NonCancellable) { graph.appState.put("splatter-bug", blob) }
            }
        }
    }

    LaunchedEffect(game?.over, game?.score) {
        val current = game ?: return@LaunchedEffect
        if (current.over && !recorded) {
            recorded = true
            saved = null
            scope.launch(NonCancellable) {
                graph.games.record("splatter-bug", current.score, null)
                graph.appState.clear("splatter-bug")
            }
        }
    }

    fun startNew() {
        game = SplatterBugEngine.newGame(System.currentTimeMillis().toInt())
        recorded = false
        paused = false
        pendingTaps.clear()
        screen = "game"
    }

    fun toggleSound() {
        sound = !sound
        val value = if (sound) "1" else "0"
        scope.launch(NonCancellable) { graph.appState.put("splatter-bug-sound", value) }
    }

    if (screen == "menu") {
        DetailScaffold(title = "splatter bug") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                BasicText(
                    text = "squish the pests, free the butterflies and ladybirds. tarantulas need three taps.",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(10.dp))
                saved?.let { resume ->
                    SplatterButton("continue · score ${resume.score}", Modifier.fillMaxWidth()) {
                        game = resume
                        recorded = false
                        paused = false
                        pendingTaps.clear()
                        screen = "game"
                    }
                    Spacer(Modifier.height(4.dp))
                }
                SplatterButton("new game", Modifier.fillMaxWidth()) { startNew() }
                Spacer(Modifier.height(4.dp))
                SplatterButton("how to play", Modifier.fillMaxWidth()) { screen = "help" }
                Spacer(Modifier.height(4.dp))
                SplatterButton(if (sound) "sound on" else "sound off", Modifier.fillMaxWidth()) { toggleSound() }
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = "best $best",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
            }
        }
        return
    }

    if (screen == "help") {
        DetailScaffold(title = "splatter bug · help", onBack = { screen = "menu" }) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                HelpLine(colors, "tap pests to squish them — ants, cockroaches, earwigs, spiders and tarantulas.")
                HelpLine(colors, "butterflies and ladybirds are harmless: tapping one costs a life.")
                HelpLine(colors, "let harmless critters leave the screen to score their hit points.")
                HelpLine(colors, "escaped pests cost a life. three failures and the fourth ends the run.")
                HelpLine(colors, "a tarantula takes three taps and bursts into baby spiders when it dies.")
                Spacer(Modifier.height(10.dp))
                SplatterButton("back", Modifier.fillMaxWidth()) { screen = "menu" }
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "splatter bug") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${current.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                if (current.lives > 0) {
                    BasicText(
                        text = "Lives: " + "♥".repeat(current.lives),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = DoradoAccent.PINK.bright),
                    )
                } else {
                    BasicText(
                        text = "Lives: 0",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textInactive),
                    )
                }
                Spacer(Modifier.weight(1f))
                EdgeText("pause", colors.textPrimary) { if (!current.over) paused = true }
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = "best $best",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(current.over) {
                                if (!current.over) {
                                    detectTapGestures { offset ->
                                        val vp = splatterViewport(size.width.toFloat(), size.height.toFloat())
                                        val x = (offset.x - vp.ox) / vp.scale
                                        val y = (offset.y - vp.oy) / vp.scale
                                        if (x in 0f..SPLATTER_VIEW_W && y in 0f..SPLATTER_VIEW_H) {
                                            pendingTaps += BugTap(x, y)
                                        }
                                    }
                                }
                            },
                    ) {
                        val vp = splatterViewport(size.width, size.height)
                        drawRect(colors.background)
                        for (i in 0 until 6) {
                            val y = vp.y(i * 80f) - vp.s(40f)
                            drawRect(
                                colors.elevated,
                                topLeft = Offset(0f, y),
                                size = Size(size.width, vp.s(1f)),
                            )
                        }
                        for (bug in current.bugs) drawSplatterBug(bug, vp, colors, labelPaint)
                    }
                    if (current.toastMs > 0L && !current.over) {
                        BasicText(
                            text = "Bug escaped! -life",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = DoradoAccent.PINK.bright),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                                .background(colors.background),
                        )
                    }
                    if (paused && !current.over) {
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .background(colors.elevated)
                                .border(0.5.dp, colors.border)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                text = "paused",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.height(8.dp))
                            SplatterButton("resume") { paused = false }
                            Spacer(Modifier.height(4.dp))
                            SplatterButton("restart") { startNew() }
                            Spacer(Modifier.height(4.dp))
                            SplatterButton("main menu") {
                                paused = false
                                screen = "menu"
                            }
                            Spacer(Modifier.height(4.dp))
                            SplatterButton(if (sound) "sound on" else "sound off") { toggleSound() }
                        }
                    }
                    if (current.over) {
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .background(colors.elevated)
                                .border(0.5.dp, colors.border)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                text = "game over",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = "final score ${current.score}",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            BasicText(
                                text = if (current.score > best) "new high score" else "high score $best",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                            )
                            Spacer(Modifier.height(8.dp))
                            SplatterButton("restart") { startNew() }
                            Spacer(Modifier.height(4.dp))
                            SplatterButton("main menu") {
                                game = null
                                screen = "menu"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpLine(colors: DoradoColors, text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun SplatterButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

private fun DrawScope.drawSplatterBug(
    bug: SplatterBug,
    vp: SplatterViewport,
    colors: DoradoColors,
    paint: android.graphics.Paint,
) {
    val size = vp.s(bug.size)
    val left = vp.x(bug.x)
    val top = vp.y(bug.y)
    val body = bug.kind.hue(colors)
    if (bug.splatted) {
        val alpha = (1f - bug.ageMs / SPLATTER_FADE_MS.toFloat()).coerceIn(0f, 1f)
        drawOval(
            body.copy(alpha = alpha * 0.7f),
            topLeft = Offset(left, top + size * 0.3f),
            size = Size(size, size * 0.4f),
        )
        if (bug.ageMs <= SPLATTER_LABEL_MS && bug.label.isNotEmpty()) {
            paint.color = colors.textPrimary.toArgb()
            paint.textSize = vp.s(11f)
            drawContext.canvas.nativeCanvas.drawText(
                bug.label,
                left + size / 2f - paint.measureText(bug.label) / 2f,
                top + size * 0.22f,
                paint,
            )
        }
        return
    }

    val stroke = vp.s(1.6f).coerceAtLeast(1f)
    val cx = left + size / 2f
    val cy = top + size / 2f
    val bodyTop = top + size * 0.26f
    val bodySize = Size(size, size * 0.48f)
    for (i in 0 until 3) {
        val legY = top + size * (0.34f + i * 0.16f)
        drawLine(body, Offset(left + size * 0.08f, legY), Offset(left + size * 0.35f, legY - size * 0.12f), strokeWidth = stroke)
        drawLine(body, Offset(left + size * 0.92f, legY), Offset(left + size * 0.65f, legY - size * 0.12f), strokeWidth = stroke)
    }
    if (bug.kind == BugKind.TARANTULA) {
        for (i in 0 until 4) {
            val legY = top + size * (0.3f + i * 0.1f)
            drawLine(body, Offset(left + size * 0.2f, legY), Offset(left - size * 0.08f, legY + size * 0.16f), strokeWidth = stroke)
            drawLine(body, Offset(left + size * 0.8f, legY), Offset(left + size * 1.08f, legY + size * 0.16f), strokeWidth = stroke)
        }
    }
    if (bug.kind.harmless) {
        val wing = if (bug.kind == BugKind.BUTTERFLY) DoradoAccent.PURPLE.bright else DoradoAccent.PINK.bright
        drawOval(
            wing.copy(alpha = 0.9f),
            topLeft = Offset(left - size * 0.12f, bodyTop - size * 0.06f),
            size = Size(size * 0.38f, size * 0.5f),
        )
        drawOval(
            wing.copy(alpha = 0.9f),
            topLeft = Offset(left + size * 0.74f, bodyTop - size * 0.06f),
            size = Size(size * 0.38f, size * 0.5f),
        )
    }
    drawOval(body, topLeft = Offset(left, bodyTop), size = bodySize)
    drawCircle(body, radius = size * 0.17f, center = Offset(cx, top + size * 0.2f))
    if (bug.kind == BugKind.TARANTULA) {
        drawCircle(colors.background, radius = size * 0.045f, center = Offset(cx - size * 0.07f, top + size * 0.18f))
        drawCircle(colors.background, radius = size * 0.045f, center = Offset(cx + size * 0.07f, top + size * 0.18f))
    }
    val damage = bug.kind.tapsToKill - bug.tapsLeft
    for (i in 0 until damage) {
        drawCircle(
            colors.accentBright,
            radius = size * 0.045f,
            center = Offset(left + size * (0.36f + i * 0.18f), top + size * 0.08f),
        )
    }
}
