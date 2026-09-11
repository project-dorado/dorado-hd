package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class HairballViewport(private val scale: Float, private val ox: Float, private val oy: Float) {
    fun x(value: Float): Float = ox + value * scale
    fun y(value: Float): Float = oy + value * scale
    fun s(value: Float): Float = value * scale
}

private fun hairballViewport(width: Float, height: Float): HairballViewport {
    val scale = min(width / HAIRBALL_VIEW_W, height / HAIRBALL_VIEW_H)
    return HairballViewport(
        scale = scale,
        ox = (width - HAIRBALL_VIEW_W * scale) / 2f,
        oy = (height - HAIRBALL_VIEW_H * scale) / 2f,
    )
}

@Composable
fun HairballApp() {
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
    var game by remember { mutableStateOf<HairballState?>(null) }
    var saved by remember { mutableStateOf<HairballState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    // Bumped on every new run: the frame loop keys on it so retry restarts a
    // loop that had already exited on `lost`.
    var runId by remember { mutableStateOf(0) }
    val scores by graph.games.top("hairball", 1).collectAsState(initial = emptyList())
    val best = scores.firstOrNull()?.score ?: 0
    val tilt by rememberTilt(enabled = screen == "game" && !paused)
    val latestTilt = rememberUpdatedState(
        -sin(Math.toRadians(tilt.pitchDeg.toDouble())).toFloat(),
    )

    LaunchedEffect(Unit) {
        saved = graph.appState.get("hairball")?.let { HairballEngine.decode(it) }
    }

    LaunchedEffect(screen, paused, runId) {
        while (screen == "game" && !paused) {
            val current = game ?: break
            if (current.lost) break
            delay(33L)
            val next = HairballEngine.step(current, 33L, latestTilt.value)
            game = next
            next.events.forEach { event ->
                when (event) {
                    HairballEvent.Landed -> bank.play("click")
                    HairballEvent.SpeedUp -> bank.play("tick")
                    HairballEvent.Lost -> bank.play("lose")
                    HairballEvent.RowSpawned -> Unit
                }
            }
            if (next.elapsedMs / 3_000L > current.elapsedMs / 3_000L) {
                val blob = HairballEngine.encode(next)
                scope.launch(NonCancellable) { graph.appState.put("hairball", blob) }
            }
        }
    }

    LaunchedEffect(game?.lost, game?.score) {
        val current = game ?: return@LaunchedEffect
        if (current.lost && !recorded) {
            recorded = true
            saved = null
            scope.launch(NonCancellable) {
                graph.games.record("hairball", current.score, null)
                graph.appState.clear("hairball")
            }
        }
    }

    fun startNew() {
        game = HairballEngine.newGame(System.currentTimeMillis().toInt())
        recorded = false
        paused = false
        runId++
        screen = "game"
    }

    if (screen == "menu") {
        DetailScaffold(title = "hairball") {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                BasicText(
                    text = "tilt to steer. sludge rises and carries you up; losing the top edge ends the run.",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(10.dp))
                saved?.let { resume ->
                    HairballButton("continue · score ${resume.score}", Modifier.fillMaxWidth()) {
                        game = resume
                        recorded = false
                        paused = false
                        screen = "game"
                    }
                    Spacer(Modifier.height(4.dp))
                }
                HairballButton("play", Modifier.fillMaxWidth()) { startNew() }
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = "best $best",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
                )
            }
        }
        return
    }

    val current = game ?: return
    DetailScaffold(title = "hairball") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score %06d".format(current.score),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "speed %.1f".format(current.speed),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "best $best",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
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
                            .pointerInput(current.lost) {
                                if (!current.lost) detectTapGestures { paused = !paused }
                            },
                    ) {
                        val vp = hairballViewport(size.width, size.height)
                        drawRect(colors.background)
                        for (row in current.rows) {
                            for (slot in row.slots) {
                                val bx = vp.x(HairballEngine.slotX(row, slot))
                                val by = vp.y(row.y)
                                val bw = vp.s(HAIRBALL_BLOCK_W)
                                val bh = vp.s(HAIRBALL_BLOCK_H)
                                drawRect(colors.tile, topLeft = Offset(bx, by), size = Size(bw, bh))
                                drawCircle(colors.elevated, radius = bh * 0.34f, center = Offset(bx + bw / 2f, by + bh / 2f))
                                drawRect(
                                    colors.border,
                                    topLeft = Offset(bx, by),
                                    size = Size(bw, bh),
                                    style = Stroke(width = vp.s(1.5f)),
                                )
                            }
                        }
                        val travel = (current.elapsedMs % 2_400L) / 2_400f
                        val stripY = size.height - vp.s(16f + 10f * travel)
                        drawRect(colors.elevated, topLeft = Offset(0f, stripY), size = Size(size.width, size.height - stripY))
                        drawRect(colors.border, topLeft = Offset(0f, stripY), size = Size(size.width, vp.s(1.5f)))

                        val cx = vp.x(current.playerX + HAIRBALL_PLAYER / 2f)
                        val cy = vp.y(current.playerY + HAIRBALL_PLAYER / 2f)
                        val radius = vp.s(HAIRBALL_PLAYER / 2f)
                        drawCircle(colors.tile, radius = radius, center = Offset(cx, cy))
                        drawCircle(colors.textSecondary, radius = radius, center = Offset(cx, cy), style = Stroke(width = vp.s(1.5f)))
                        val spin = latestTilt.value * 180f
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(spin + i * 45.0)
                            val distance = radius * 0.62f
                            drawCircle(
                                colors.accentBright,
                                radius = radius * 0.28f,
                                center = Offset(
                                    cx + (distance * cos(angle)).toFloat(),
                                    cy + (distance * sin(angle)).toFloat(),
                                ),
                            )
                        }
                    }
                    BasicText(
                        text = "Score: %06d".format(current.score),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(colors.background),
                    )
                    if (paused && !current.lost) {
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
                            HairballButton("resume") { paused = false }
                            Spacer(Modifier.height(4.dp))
                            HairballButton("quit") {
                                paused = false
                                screen = "menu"
                            }
                        }
                    }
                    if (current.lost) {
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
                            HairballButton("retry") { startNew() }
                            Spacer(Modifier.height(4.dp))
                            HairballButton("menu") {
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
private fun HairballButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
