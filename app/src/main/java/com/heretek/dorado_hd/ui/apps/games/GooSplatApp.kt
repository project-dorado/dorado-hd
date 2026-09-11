package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.ui.apps.AppClock
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
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class GooViewport(val scale: Float, val ox: Float, val oy: Float) {
    fun x(value: Float): Float = ox + value * scale
    fun y(value: Float): Float = oy + value * scale
    fun s(value: Float): Float = value * scale
}

private fun gooViewport(width: Float, height: Float): GooViewport {
    val scale = min(width / GOO_VIEW_W, height / GOO_VIEW_H)
    return GooViewport(scale, (width - GOO_VIEW_W * scale) / 2f, (height - GOO_VIEW_H * scale) / 2f)
}

private fun JellyKind.hue(): Color = when (this) {
    JellyKind.SMALL_GREEN, JellyKind.LARGE_GREEN -> DoradoAccent.LIME.bright
    JellyKind.BOMB -> DoradoAccent.PINK.primary
    JellyKind.ICE -> DoradoAccent.CYAN.bright
    JellyKind.ELECTRIC -> DoradoAccent.ORANGE.bright
}

@Composable
fun GooSplatApp() {
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
    var game by remember { mutableStateOf<GooSplatState?>(null) }
    var saved by remember { mutableStateOf<GooSplatState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    // Bumped on every new run: the frame loop keys on it so "play again"
    // restarts a loop that had already exited on `over`.
    var runId by remember { mutableStateOf(0) }
    var pepperFlashMs by remember { mutableStateOf(-1_000L) }
    val pendingTaps = remember { mutableStateListOf<BugTap>() }
    val scores by graph.games.top("goo-splat", 1).collectAsState(initial = emptyList())
    val bestScore = scores.firstOrNull()?.score ?: 0
    val bestMeta = scores.firstOrNull()?.meta ?: ""

    LaunchedEffect(Unit) {
        saved = graph.appState.get("goo-splat")?.let { GooSplatEngine.decode(it) }
    }

    LaunchedEffect(screen, paused, runId) {
        while (screen == "game" && !paused) {
            val current = game ?: break
            if (current.over) break
            delay(33L)
            val taps = pendingTaps.toList()
            pendingTaps.clear()
            val next = GooSplatEngine.step(current, 33L, taps)
            game = next
            next.events.forEach { event ->
                when (event) {
                    GooEvent.Materialize -> bank.play("click")
                    GooEvent.Splat -> bank.play("pop")
                    GooEvent.SplatLarge -> bank.play("score")
                    GooEvent.CrackFrozen -> bank.play("click")
                    GooEvent.ShatterFrozen -> bank.play("coin")
                    GooEvent.Bomb -> bank.play("explode")
                    GooEvent.Freeze -> bank.play("whoosh")
                    GooEvent.Shock -> bank.play("rotate")
                    GooEvent.Pepper -> {
                        bank.play("coin")
                        pepperFlashMs = next.elapsedMs
                    }
                    GooEvent.Merge -> bank.play("select")
                    GooEvent.HitWall -> bank.play("tick")
                    GooEvent.LevelUp -> bank.play("win")
                    GooEvent.GameOver -> bank.play("lose")
                }
            }
            if (next.elapsedMs / 3_000L > current.elapsedMs / 3_000L) {
                val blob = GooSplatEngine.encode(next)
                scope.launch(NonCancellable) { graph.appState.put("goo-splat", blob) }
            }
        }
    }

    LaunchedEffect(game?.over, game?.score) {
        val current = game ?: return@LaunchedEffect
        if (current.over && !recorded) {
            recorded = true
            saved = null
            scope.launch(NonCancellable) {
                graph.games.record("goo-splat", current.score, "L${current.level}")
                graph.appState.clear("goo-splat")
            }
        }
    }

    fun startNew() {
        game = GooSplatEngine.newGame(AppClock.millis().toInt())
        recorded = false
        paused = false
        pendingTaps.clear()
        pepperFlashMs = -1_000L
        runId++
        screen = "game"
    }

    if (screen == "menu") {
        DetailScaffold(title = "goo splat") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                BasicText(
                    text = "tap jellies before the sludge swallows them. bombs blast, ice freezes, electricity drops them.",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(10.dp))
                saved?.let { resume ->
                    GooSplatButton(
                        "continue · level ${resume.level} · ${resume.score} points",
                        Modifier.fillMaxWidth(),
                    ) {
                        game = resume
                        recorded = false
                        paused = false
                        pendingTaps.clear()
                        screen = "game"
                    }
                    Spacer(Modifier.height(4.dp))
                }
                GooSplatButton("play", Modifier.fillMaxWidth()) { startNew() }
                Spacer(Modifier.height(4.dp))
                GooSplatButton("how to play", Modifier.fillMaxWidth()) { screen = "help" }
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = if (bestMeta.isNotEmpty()) "best $bestScore ($bestMeta)" else "best $bestScore",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
            }
        }
        return
    }

    if (screen == "help") {
        DetailScaffold(title = "goo splat · help", onBack = { screen = "menu" }) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                GooHelpLine(colors, "green jellies score 1 (small) or 5 (large); falling jellies score double.")
                GooHelpLine(colors, "red bomb blasts nearby jellies; blue ice freezes the board; yellow electricity drops them.")
                GooHelpLine(colors, "two small greens that meet on the floor merge into one large green.")
                GooHelpLine(colors, "a pepper bonus clears the whole board and pushes the sludge back down.")
                GooHelpLine(colors, "the run ends when the sludge surface climbs past level 70.")
                Spacer(Modifier.height(10.dp))
                GooSplatButton("back", Modifier.fillMaxWidth()) { screen = "menu" }
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "goo splat") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(
                Modifier.fillMaxWidth().appDescription(
                    "score ${current.score}, level ${current.level}, target ${current.scoreTarget}, best $bestScore",
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "score ${current.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "level ${current.level} · target ${current.scoreTarget}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "best $bestScore",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(10.dp))
                EdgeText("pause", colors.textPrimary) { if (!current.over) paused = true }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .appDescription("goo splat board, score ${current.score}, level ${current.level}, tap the jellies")
                            .pointerInput(current.over) {
                                if (!current.over) {
                                    val vp = gooViewport(size.width.toFloat(), size.height.toFloat())
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            for (change in event.changes) {
                                                if (change.pressed && !change.previousPressed) {
                                                    val x = (change.position.x - vp.ox) / vp.scale
                                                    val y = (change.position.y - vp.oy) / vp.scale
                                                    if (x in 0f..GOO_VIEW_W && y in 0f..GOO_VIEW_H) {
                                                        pendingTaps += BugTap(x, y)
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            },
                    ) {
                        val vp = gooViewport(size.width, size.height)
                        drawRect(colors.background)
                        val surface = vp.y(current.sludgeLevel)
                        val wave = sin(current.elapsedMs / 260.0).toFloat() * vp.s(3f)
                        val stepX = vp.s(8f).coerceAtLeast(1f)
                        val waveLength = vp.s(24f).coerceAtLeast(1f)
                        val sludge = Path().apply {
                            moveTo(0f, surface + wave)
                            var x = 0f
                            while (x <= size.width) {
                                val phase = (x / waveLength).toInt()
                                lineTo(x, surface + sin(current.elapsedMs / 260.0 + phase).toFloat() * vp.s(3f))
                                x += stepX
                            }
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(sludge, colors.tile)
                        val onFire = current.elapsedMs - pepperFlashMs < 1_000L
                        drawLine(
                            if (onFire) DoradoAccent.ORANGE.bright else colors.border,
                            Offset(0f, surface),
                            Offset(size.width, surface),
                            strokeWidth = vp.s(if (onFire) 3f else 2f),
                        )
                        for (jelly in current.jellies) drawGooJelly(jelly, vp, colors)
                        current.pepper?.let { pepper ->
                            val px = vp.x(pepper.x)
                            val py = vp.y(pepper.y)
                            drawOval(
                                DoradoAccent.PINK.bright,
                                topLeft = Offset(px - vp.s(10f), py - vp.s(14f)),
                                size = Size(vp.s(20f), vp.s(28f)),
                            )
                            drawLine(
                                DoradoAccent.LIME.primary,
                                Offset(px, py - vp.s(14f)),
                                Offset(px + vp.s(6f), py - vp.s(22f)),
                                strokeWidth = vp.s(2f),
                            )
                        }
                    }
                    if (paused && !current.over) {
                        GooPanel(Modifier.align(Alignment.Center)) {
                            BasicText(
                                text = "paused",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.height(8.dp))
                            GooSplatButton("resume") { paused = false }
                            Spacer(Modifier.height(4.dp))
                            GooSplatButton("restart") { startNew() }
                            Spacer(Modifier.height(4.dp))
                            GooSplatButton("main menu") {
                                paused = false
                                screen = "menu"
                            }
                        }
                    }
                    if (current.over) {
                        GooPanel(Modifier.align(Alignment.Center)) {
                            BasicText(
                                text = "breakdown",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            BasicText(
                                text = "level ${current.level}",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            BasicText(
                                text = "score ${current.score}",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row {
                                BreakdownCount("green", current.breakdown.green, DoradoAccent.LIME.bright)
                                BreakdownCount("red", current.breakdown.red, DoradoAccent.PINK.bright)
                                BreakdownCount("blue", current.breakdown.blue, DoradoAccent.CYAN.bright)
                                BreakdownCount("yellow", current.breakdown.yellow, DoradoAccent.ORANGE.bright)
                                BreakdownCount("peppers", current.breakdown.peppers, colors.textPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (current.score > bestScore) {
                                BasicText(
                                    text = "new high score",
                                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                                )
                            }
                            GooSplatButton("play again") { startNew() }
                            Spacer(Modifier.height(4.dp))
                            GooSplatButton("main menu") {
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
private fun GooHelpLine(colors: DoradoColors, text: String) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun GooPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        modifier
            .background(colors.elevated)
            .border(0.5.dp, colors.border)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun BreakdownCount(label: String, value: Int, tint: Color) {
    Column(Modifier.padding(horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = "$value",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = tint),
        )
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = LocalDoradoColors.current.textSecondary),
        )
    }
}

@Composable
private fun GooSplatButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .appTap(label = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

private fun DrawScope.drawGooJelly(jelly: Jelly, vp: GooViewport, colors: DoradoColors) {
    val radius = vp.s(jelly.radius)
    val cx = vp.x(jelly.x)
    val cy = vp.y(jelly.y)
    val center = Offset(cx, cy)
    val hue = jelly.kind.hue()
    when (jelly.state) {
        JellyState.SHADOW -> {
            val shrink = 2.5f - (jelly.stateMs / GOO_SHADOW_MS.toFloat()) * 2f
            drawCircle(
                colors.textSecondary.copy(alpha = 0.4f),
                radius = (radius * shrink * 0.4f).coerceAtLeast(vp.s(2f)),
                center = center,
                style = Stroke(width = vp.s(2f)),
            )
            return
        }
        JellyState.SQUISHED -> {
            val alpha = (1f - jelly.squishMs / GOO_SQUISH_FADE_MS.toFloat()).coerceIn(0f, 1f)
            drawOval(
                hue.copy(alpha = alpha),
                topLeft = Offset(cx - radius * 1.1f, cy - radius * 0.35f),
                size = Size(radius * 2.2f, radius * 0.7f),
            )
            return
        }
        else -> Unit
    }
    drawCircle(hue.copy(alpha = 0.92f), radius = radius, center = center)
    if (jelly.kind == JellyKind.LARGE_GREEN) {
        drawCircle(hue, radius = radius * 0.62f, center = center, style = Stroke(width = vp.s(2f)))
    }
    when (jelly.kind) {
        JellyKind.BOMB -> {
            drawCircle(colors.background, radius = radius * 0.32f, center = center)
            drawLine(
                colors.background,
                Offset(cx + radius * 0.2f, cy - radius * 0.28f),
                Offset(cx + radius * 0.42f, cy - radius * 0.5f),
                strokeWidth = vp.s(2f),
            )
        }
        JellyKind.ICE -> {
            for (i in 0 until 3) {
                val angle = Math.toRadians(i * 60.0)
                val dx = (radius * 0.42f * kotlin.math.cos(angle)).toFloat()
                val dy = (radius * 0.42f * sin(angle)).toFloat()
                drawLine(colors.background, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = vp.s(2f))
            }
        }
        JellyKind.ELECTRIC -> {
            val bolt = Path().apply {
                moveTo(cx - radius * 0.22f, cy - radius * 0.4f)
                lineTo(cx + radius * 0.08f, cy - radius * 0.05f)
                lineTo(cx - radius * 0.08f, cy)
                lineTo(cx + radius * 0.22f, cy + radius * 0.4f)
            }
            drawPath(bolt, colors.background, style = Stroke(width = vp.s(2.4f)))
        }
        else -> Unit
    }
    if (jelly.frozen) {
        drawCircle(Color.White.copy(alpha = 0.7f), radius = radius, center = center, style = Stroke(width = vp.s(2f)))
        if (jelly.cracked) {
            drawLine(
                Color.White.copy(alpha = 0.9f),
                Offset(cx - radius * 0.3f, cy - radius * 0.3f),
                Offset(cx + radius * 0.35f, cy + radius * 0.25f),
                strokeWidth = vp.s(2f),
            )
        }
    }
    if (jelly.falling) {
        val arrow = Path().apply {
            moveTo(cx - radius * 0.25f, cy + radius * 0.45f)
            lineTo(cx, cy + radius * 0.72f)
            lineTo(cx + radius * 0.25f, cy + radius * 0.45f)
        }
        drawPath(arrow, colors.textPrimary, style = Stroke(width = vp.s(2f)))
    }
}
