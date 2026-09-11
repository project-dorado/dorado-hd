package com.heretek.dorado_hd.ui.apps.games

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun VineClimbApp() {
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
    var game by remember { mutableStateOf<VineClimbEngine.State?>(null) }
    var highScore by remember { mutableStateOf(0) }
    var soundOn by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    val scores by graph.games.top("vine-climb", 5).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (soundOn) bank.play(name)
    }

    LaunchedEffect(Unit) {
        highScore = graph.appState.get("vine-climb.high")?.toIntOrNull() ?: 0
        soundOn = graph.appState.get("vine-climb.sound") != "0"
    }
    LaunchedEffect(soundOn) { graph.appState.put("vine-climb.sound", if (soundOn) "1" else "0") }
    LaunchedEffect(highScore) { graph.appState.put("vine-climb.high", highScore.toString()) }

    val active = screen == "game" && game != null && !paused && game?.gameOverShown == false
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 40L)
                    last = now
                    val current = game ?: return@withFrameNanos
                    game = VineClimbEngine.step(current, VineClimbEngine.Input(), dt)
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.collectFrames) {
        val current = game ?: return@LaunchedEffect
        if (current.collectFrames == VineClimbEngine.COLLECT_FRAMES) {
            val kind = current.collected
            play(if (kind == VineClimbEngine.CollectableKind.MANGO) "score" else "coin")
        }
    }

    LaunchedEffect(game?.gameOverShown) {
        val current = game ?: return@LaunchedEffect
        if (current.gameOverShown && !recorded) {
            recorded = true
            val record = current.score > highScore
            if (record) {
                highScore = current.score
                newRecord = true
                play("score")
            } else {
                newRecord = false
                play("lose")
            }
            scope.launch(NonCancellable) {
                graph.games.record("vine-climb", current.score, null)
            }
        }
    }

    fun startGame() {
        game = VineClimbEngine.newGame(seed = System.currentTimeMillis().toInt())
        screen = "game"
        paused = false
        recorded = false
        newRecord = false
    }

    if (screen == "menu") {
        VineClimbMenu(
            highScore = highScore,
            bestRecorded = scores.firstOrNull()?.score ?: 0,
            soundOn = soundOn,
            onPlay = { startGame() },
            onHelp = { screen = "help" },
            onSound = { soundOn = !soundOn },
        )
        return
    }
    if (screen == "help") {
        VineClimbHelp(onBack = { screen = "menu" })
        return
    }

    val current = game ?: return
    DetailScaffold(title = "vine climb", onBack = { screen = "menu"; paused = false }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${current.score.toString().padStart(6, '0')}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "best ${maxOf(highScore, current.score)}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.weight(1f))
                VineClimbButton("pause") { paused = true }
            }
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(current.gameOverShown, paused) {
                            detectTapGestures { offset ->
                                if (paused || current.gameOverShown) return@detectTapGestures
                                val scale = min(viewW / VineClimbEngine.VIEW_W.toFloat(), viewH / VineClimbEngine.VIEW_H.toFloat())
                                val ox = (viewW - VineClimbEngine.VIEW_W * scale) / 2f
                                val oy = (viewH - VineClimbEngine.VIEW_H * scale) / 2f
                                val vx = (offset.x - ox) / scale
                                val vy = (offset.y - oy) / scale
                                when {
                                    vx >= VineClimbEngine.VIEW_W - 56f && vy <= 50f -> paused = true
                                    vy >= 352f && vx <= 136f -> {
                                        game = VineClimbEngine.tick(current, VineClimbEngine.Input(left = true))
                                        play("jump")
                                    }

                                    vy >= 352f -> {
                                        game = VineClimbEngine.tick(current, VineClimbEngine.Input(right = true))
                                        play("jump")
                                    }
                                }
                            }
                        },
                ) {
                    drawVineClimbScene(current, colors)
                }
                if (paused || current.gameOverShown) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier
                                .background(colors.elevated)
                                .border(0.5.dp, colors.border)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                text = if (current.gameOverShown) "game over" else "paused",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = "score ${current.score}${if (current.gameOverShown && newRecord) " · new record" else ""}",
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (paused && !current.gameOverShown) {
                                    VineClimbButton("resume") { paused = false }
                                }
                                VineClimbButton(if (current.gameOverShown) "play again" else "restart") { startGame() }
                                VineClimbButton("main menu") { screen = "menu"; paused = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VineClimbMenu(
    highScore: Int,
    bestRecorded: Int,
    soundOn: Boolean,
    onPlay: () -> Unit,
    onHelp: () -> Unit,
    onSound: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "vine climb") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            VineClimbButton("play", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            VineClimbButton("help", Modifier.fillMaxWidth()) { onHelp() }
            Spacer(Modifier.height(4.dp))
            VineClimbButton(if (soundOn) "sound on" else "sound off", Modifier.fillMaxWidth()) { onSound() }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "high score $highScore",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            BasicText(
                text = "records best $bestRecorded",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "climb forever. tap the paws to hop lanes.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun VineClimbHelp(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "vine climb · help", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "collect these",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            BasicText(
                text = "mango 2000 · dragonfly 500 · berries 250 · nut 250",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "avoid these",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = DoradoAccent.PINK.primary),
            )
            BasicText(
                text = "spiky vines, live electric vines, snakes and chameleons",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "left paw hops left, right paw hops right. pause top-right.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            VineClimbButton("back") { onBack() }
        }
    }
}

private fun DrawScope.drawVineClimbScene(state: VineClimbEngine.State, colors: DoradoColors) {
    val scale = min(size.width / VineClimbEngine.VIEW_W.toFloat(), size.height / VineClimbEngine.VIEW_H.toFloat())
    val ox = (size.width - VineClimbEngine.VIEW_W * scale) / 2f
    val oy = (size.height - VineClimbEngine.VIEW_H * scale) / 2f
    val w = VineClimbEngine.VIEW_W * scale
    val h = VineClimbEngine.VIEW_H * scale
    drawRect(colors.background, Offset(ox, oy), Size(w, h))

    // Parallax canopy: far, mid and near tree bands scroll at 1/3/4 px per tick.
    drawLayerBand(state.layerFar + 0, 701, 0.30f, DoradoAccent.LIME.primary.copy(alpha = 0.22f), ox, oy, scale)
    drawLayerBand(state.layerMid + 0, 512, 0.55f, DoradoAccent.LIME.primary.copy(alpha = 0.38f), ox, oy, scale)
    drawLayerBand(state.layerNear + 0, 594, 0.85f, DoradoAccent.LIME.bright.copy(alpha = 0.55f), ox, oy, scale)

    val screenTop = state.screenTop
    for (vine in state.vines) {
        val x = ox + (VineClimbEngine.laneX(vine.lane) - 27) * scale
        val top = oy + (vine.top - screenTop) * scale
        val len = vine.length * scale
        val body = when (vine.type) {
            VineClimbEngine.VineType.GREEN -> DoradoAccent.LIME.primary
            VineClimbEngine.VineType.SPIKY -> DoradoAccent.ORANGE.primary
            VineClimbEngine.VineType.ELECTRIC -> if (VineClimbEngine.electricOn(vine.phase)) DoradoAccent.CYAN.bright else DoradoAccent.CYAN.primary
        }
        drawRect(body, Offset(x, top), Size(54f * scale, len))
        drawRect(colors.background.copy(alpha = 0.25f), Offset(x + 24f * scale, top), Size(4f * scale, len))
        if (vine.type == VineClimbEngine.VineType.SPIKY) {
            var spike = top
            while (spike < top + len) {
                val path = Path()
                path.moveTo(x, spike)
                path.lineTo(x - 6f * scale, spike + 8f * scale)
                path.lineTo(x, spike + 16f * scale)
                path.close()
                drawPath(path, DoradoAccent.ORANGE.bright)
                spike += 24f * scale
            }
        }
    }

    for (item in state.collectables) {
        val center = Offset(ox + item.x * scale, oy + (item.y - screenTop) * scale)
        val sizePx = VineClimbEngine.collectableSize(item.kind) * scale
        when (item.kind) {
            VineClimbEngine.CollectableKind.MANGO -> {
                drawCircle(DoradoAccent.ORANGE.primary, sizePx * 0.5f, center)
                drawCircle(DoradoAccent.LIME.primary, sizePx * 0.18f, Offset(center.x, center.y - sizePx * 0.4f))
            }

            VineClimbEngine.CollectableKind.BERRIES -> {
                drawCircle(DoradoAccent.PURPLE.primary, sizePx * 0.28f, Offset(center.x - sizePx * 0.2f, center.y))
                drawCircle(DoradoAccent.PURPLE.bright, sizePx * 0.28f, Offset(center.x + sizePx * 0.2f, center.y))
            }

            VineClimbEngine.CollectableKind.DRAGONFLY -> {
                drawRect(DoradoAccent.CYAN.bright, Offset(center.x - sizePx * 0.5f, center.y - sizePx * 0.1f), Size(sizePx, sizePx * 0.2f))
                drawRect(DoradoAccent.CYAN.primary, Offset(center.x - sizePx * 0.1f, center.y - sizePx * 0.5f), Size(sizePx * 0.2f, sizePx))
            }

            VineClimbEngine.CollectableKind.NUT -> {
                drawRect(DoradoAccent.ORANGE.primary.copy(alpha = 0.9f), Offset(center.x - sizePx * 0.4f, center.y - sizePx * 0.4f), Size(sizePx * 0.8f, sizePx * 0.8f))
            }
        }
    }

    for (enemy in state.enemies) {
        val center = Offset(ox + enemy.x * scale, oy + (enemy.y - screenTop) * scale)
        val masked = enemy.kind == VineClimbEngine.EnemyKind.CHAMELEON && !enemy.revealed
        if (masked) continue
        if (enemy.kind == VineClimbEngine.EnemyKind.SNAKE) {
            drawRect(DoradoAccent.LIME.primary, Offset(center.x - 10f * scale, center.y - 5f * scale), Size(28f * scale, 10f * scale))
            drawCircle(DoradoAccent.LIME.bright, 5f * scale, Offset(center.x + 12f * scale, center.y))
        } else {
            drawCircle(DoradoAccent.CYAN.primary, 10f * scale, center)
            drawCircle(DoradoAccent.CYAN.bright, 4f * scale, Offset(center.x + 4f * scale, center.y - 2f * scale))
        }
    }

    val monkeyY = oy + (-state.climbedY - screenTop) * scale
    val monkeyX = ox + state.x * scale
    drawRect(colors.textPrimary, Offset(monkeyX - 8f * scale, monkeyY - 16f * scale), Size(16f * scale, 32f * scale))
    drawCircle(colors.textPrimary, 9f * scale, Offset(monkeyX, monkeyY - 22f * scale))
    val armOffset = if (state.jumping) 8f * scale else 4f * scale
    drawRect(colors.textSecondary, Offset(monkeyX - 14f * scale, monkeyY - armOffset), Size(28f * scale, 4f * scale))
    if (state.flashTicks > 0) {
        val flash = if (state.flashZap) DoradoAccent.CYAN.bright else DoradoAccent.PINK.primary
        drawRect(flash.copy(alpha = 0.4f), Offset(ox, oy), Size(w, h))
    }

    val paint = vinePaint
    for (score in state.floatingScores) {
        val alpha = score.ticksRemaining.coerceIn(0, 100) / 100f * 255f
        paint.color = android.graphics.Color.WHITE
        paint.alpha = alpha.toInt()
        paint.textSize = 13f * scale
        val rise = VineClimbEngine.floatingRise(score.ticksRemaining)
        val x = ox + score.x * scale
        val y = oy + (score.y - rise) * scale
        drawContext.canvas.nativeCanvas.drawText(score.value.toString(), x, y, paint)
    }

    val tutorialAlpha = VineClimbEngine.tutorialAlpha(state.score)
    if (tutorialAlpha > 0f && state.inTutorial) {
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (tutorialAlpha * 255f).toInt()
        paint.textSize = 12f * scale
        drawContext.canvas.nativeCanvas.drawText("tap the paw prints", ox + w / 2f, oy + h * 0.55f, paint)
    }

    // Paw buttons.
    val pawY = oy + 352f * scale
    drawPaw(ox + 8f * scale, pawY, 56f * scale, colors.accent)
    drawPaw(ox + 96f * scale, pawY, 56f * scale, colors.accent)
}

private fun DrawScope.drawLayerBand(
    offset: Int,
    period: Int,
    heightFraction: Float,
    color: Color,
    ox: Float,
    oy: Float,
    scale: Float,
) {
    val bandHeight = VineClimbEngine.VIEW_H * heightFraction * scale
    val shifted = oy + (offset % period) * scale * 0.1f
    drawRect(color, Offset(ox - 4f * scale, shifted), Size(VineClimbEngine.VIEW_W * scale + 8f * scale, bandHeight))
}

private fun DrawScope.drawPaw(x: Float, y: Float, sizePx: Float, color: Color) {
    drawRect(color.copy(alpha = 0.35f), Offset(x, y), Size(sizePx, sizePx * 0.8f))
    drawRect(color, Offset(x + sizePx * 0.2f, y + sizePx * 0.2f), Size(sizePx * 0.6f, sizePx * 0.5f))
}

private val vinePaint: Paint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}

@Composable
private fun VineClimbButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}
