package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun novaColor(index: Int, colors: DoradoColors): Color = when (index % 12) {
    0 -> DoradoAccent.PINK.primary
    1 -> DoradoAccent.PINK.bright
    2 -> DoradoAccent.ORANGE.primary
    3 -> DoradoAccent.ORANGE.bright
    4 -> DoradoAccent.CYAN.primary
    5 -> DoradoAccent.CYAN.bright
    6 -> DoradoAccent.LIME.primary
    7 -> DoradoAccent.LIME.bright
    8 -> DoradoAccent.PURPLE.primary
    9 -> DoradoAccent.PURPLE.bright
    10 -> colors.accent
    else -> colors.accentBright
}

private fun modeLabel(mode: NovaMode): String = when (mode) {
    NovaMode.NORMAL -> "normal"
    NovaMode.HARD -> "hard"
    NovaMode.ENDLESS -> "endless"
}

@Composable
fun SupernovaApp() {
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
    var mode by remember { mutableStateOf(NovaMode.NORMAL) }
    var game by remember { mutableStateOf<NovaState?>(null) }
    var saved by remember { mutableStateOf<NovaState?>(null) }
    var intro by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val top by graph.games.top("supernova", SupernovaEngine.MAX_HIGH_SCORES).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        graph.appState.get("supernova.mode")?.let { stored ->
            mode = runCatching { NovaMode.valueOf(stored) }.getOrDefault(NovaMode.NORMAL)
        }
        saved = graph.appState.get("supernova")?.let { SupernovaEngine.decode(it) }
        loaded = true
    }

    LaunchedEffect(mode, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("supernova.mode", mode.name)
    }

    LaunchedEffect(game?.level, game?.phase, screen, loaded) {
        if (!loaded) return@LaunchedEffect
        while (screen == "game") {
            val current = game ?: break
            if (current.phase != NovaPhase.PLAY && current.phase != NovaPhase.RESOLVING) break
            graph.appState.put("supernova", SupernovaEngine.encode(current))
            delay(2000)
        }
    }

    LaunchedEffect(game?.phase) {
        val current = game ?: return@LaunchedEffect
        when (current.phase) {
            NovaPhase.COMPLETE -> if (!recorded) {
                recorded = true
                saved = null
                bank.play("win")
                scope.launch {
                    graph.games.record("supernova", current.score, "${modeLabel(current.mode)} · L${current.level}")
                    // A completed level must not leave a stale "continue".
                    graph.appState.clear("supernova")
                }
            }
            NovaPhase.FAILED -> if (!recorded) {
                recorded = true
                saved = null
                bank.play("lose")
                scope.launch { graph.appState.clear("supernova") }
            }
            else -> {}
        }
    }

    LaunchedEffect(game?.level, game?.phase, screen, intro, paused) {
        while (screen == "game" && !paused && !intro) {
            val current = game ?: break
            if (current.isOver) break
            delay(33)
            val next = SupernovaEngine.step(current, 33)
            val secondChanged = next.timeLeftMs / 1000 != current.timeLeftMs / 1000 && next.timeLeftMs > 0
            if (secondChanged) bank.play("tick")
            if (next.dotsExploded > current.dotsExploded) bank.play("hit")
            game = next
        }
    }

    fun startLevel(level: Int) {
        game = SupernovaEngine.newLevel(mode, level, System.currentTimeMillis().toInt())
        recorded = false
        intro = true
        paused = false
        screen = "game"
    }

    fun resumeGame() {
        val restored = saved ?: return
        game = restored
        mode = restored.mode
        recorded = false
        intro = false
        paused = false
        screen = "game"
    }

    fun fire(x: Float, y: Float) {
        val current = game ?: return
        val next = SupernovaEngine.tap(current, x, y)
        if (next !== current) {
            game = next
            bank.play("explode")
        }
    }

    if (screen == "menu") {
        DetailScaffold(title = "supernova") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                if (saved != null) {
                    NovaButton("continue level ${saved!!.level}", Modifier.fillMaxWidth()) { resumeGame() }
                    Spacer(Modifier.height(6.dp))
                }
                NovaButton("mode: ${modeLabel(mode)}", Modifier.fillMaxWidth()) {
                    mode = when (mode) {
                        NovaMode.NORMAL -> NovaMode.HARD
                        NovaMode.HARD -> NovaMode.ENDLESS
                        NovaMode.ENDLESS -> NovaMode.NORMAL
                    }
                }
                Spacer(Modifier.height(4.dp))
                NovaButton("play", Modifier.fillMaxWidth()) { startLevel(1) }
                Spacer(Modifier.height(10.dp))
                BasicText(
                    text = "high scores",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
                Spacer(Modifier.height(2.dp))
                if (top.isEmpty()) {
                    BasicText(
                        text = "no scores yet",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                } else {
                    top.forEachIndexed { index, row ->
                        BasicText(
                            text = "${index + 1}. ${row.score}  ${row.meta ?: ""}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                }
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "supernova") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = "score ${current.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "${modeLabel(current.mode)} · ${current.level} · ${current.dotsExploded}/${current.neededDots} of ${current.totalDots}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = if (current.mode == NovaMode.ENDLESS) "endless" else "%d".format((current.timeLeftMs + 999) / 1000),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = if (current.timeLeftMs <= 5000 && current.mode != NovaMode.ENDLESS) colors.accentBright else colors.textSecondary),
                )
                Spacer(Modifier.weight(1f))
                NovaText("pause") { paused = true }
                NovaText("menu") { screen = "menu"; game = null; saved = null; paused = false }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                NovaField(current, Modifier.fillMaxSize()) { x, y -> fire(x, y) }
                if (intro || paused || current.isOver) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                intro -> {
                                    BasicText(
                                        text = "level ${current.level}",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                                    )
                                    BasicText(
                                        text = "goal ${current.neededDots} of ${current.totalDots} stars",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    NovaButton("begin") { intro = false }
                                }
                                paused -> {
                                    BasicText(
                                        text = "paused",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NovaButton("resume") { paused = false }
                                        NovaButton("main menu") { screen = "menu"; game = null; saved = null; paused = false }
                                    }
                                }
                                current.phase == NovaPhase.COMPLETE -> {
                                    BasicText(
                                        text = if (SupernovaEngine.isLastLevel(current.mode, current.level)) "all levels cleared" else "level complete",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                                    )
                                    BasicText(
                                        text = "level ${current.level} · ${current.levelScore} x${SupernovaEngine.multiplier(current.mode, current.level)} = ${current.score}",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!SupernovaEngine.isLastLevel(current.mode, current.level)) {
                                            NovaButton("continue") { game = SupernovaEngine.nextLevel(current); recorded = false }
                                        }
                                        NovaButton("main menu") { screen = "menu"; game = null; saved = null }
                                    }
                                }
                                else -> {
                                    BasicText(
                                        text = if (current.timeLeftMs <= 0L) "time up" else "level failed",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                                    )
                                    BasicText(
                                        text = "${current.dotsExploded} of ${current.neededDots} needed",
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NovaButton("retry") { game = SupernovaEngine.retryLevel(current); recorded = false }
                                        NovaButton("main menu") { screen = "menu"; game = null; saved = null }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovaButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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

@Composable
private fun NovaText(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier = Modifier
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

@Composable
private fun NovaField(
    state: NovaState,
    modifier: Modifier,
    onTap: (Float, Float) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Canvas(
        modifier = modifier.pointerInput(state) {
            detectTapGestures { offset ->
                val scale = min(
                    size.width / SupernovaEngine.FIELD_WIDTH,
                    size.height / SupernovaEngine.FIELD_HEIGHT,
                )
                val ox = (size.width - SupernovaEngine.FIELD_WIDTH * scale) / 2f
                val oy = (size.height - SupernovaEngine.FIELD_HEIGHT * scale) / 2f
                val x = (offset.x - ox) / scale
                val y = (offset.y - oy) / scale
                if (x in 0f..SupernovaEngine.FIELD_WIDTH && y in 0f..SupernovaEngine.MAX_Y) {
                    onTap(x, y)
                }
            }
        },
    ) {
        val scale = min(
            size.width / SupernovaEngine.FIELD_WIDTH,
            size.height / SupernovaEngine.FIELD_HEIGHT,
        )
        val ox = (size.width - SupernovaEngine.FIELD_WIDTH * scale) / 2f
        val oy = (size.height - SupernovaEngine.FIELD_HEIGHT * scale) / 2f
        drawRect(
            color = colors.background,
            topLeft = Offset(ox, oy),
            size = Size(SupernovaEngine.FIELD_WIDTH * scale, SupernovaEngine.FIELD_HEIGHT * scale),
        )
        for (dot in state.dots) {
            if (dot.state == NovaDotState.COMPLETE) continue
            val radius = SupernovaEngine.dotSize(dot, state.mode) * scale
            if (radius <= 0.5f) continue
            val center = Offset(ox + dot.x * scale, oy + dot.y * scale)
            val face = if (dot.colorIndex < 0) Color.White else novaColor(dot.colorIndex, colors)
            if (dot.state == NovaDotState.EXPLODING) {
                drawCircle(face.copy(alpha = 0.45f), radius, center)
                drawCircle(face, radius, center, style = Stroke(width = 1.5f))
            } else {
                drawCircle(face, radius, center)
            }
        }
        drawRect(
            color = colors.border,
            topLeft = Offset(ox, oy),
            size = Size(SupernovaEngine.FIELD_WIDTH * scale, (SupernovaEngine.FIELD_HEIGHT - SupernovaEngine.HUD_HEIGHT) * scale),
            style = Stroke(width = 1f),
        )
        val flash = SupernovaEngine.flashAlpha(state)
        if (flash > 0f) {
            drawRect(
                color = Color.White.copy(alpha = flash),
                topLeft = Offset(ox, oy),
                size = Size(SupernovaEngine.FIELD_WIDTH * scale, SupernovaEngine.FIELD_HEIGHT * scale),
            )
        }
    }
}
