package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val TUG_DESIGN_W = 272f
private const val TUG_DESIGN_H = 480f
private const val TUG_COUNTDOWN_STEP_MS = 800L

@Composable
fun TugOWarApp() {
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
    var mode by remember { mutableStateOf(TugInputMode.TAP) }
    var hotseat by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<TugState?>(null) }
    var saved by remember { mutableStateOf<TugState?>(null) }
    var countdownMs by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragDistance by remember { mutableStateOf(0f) }
    var previousTilt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf(0) }
    val best by graph.games.top("tug-o-war", 1).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        saved = graph.appState.get("tug-o-war")?.let { TugOWarEngine.decode(it) }
        loaded = true
    }

    // H-08: the engine ticks at 60 Hz, so persist on a one-second cadence
    // instead of writing app-state on every frame.
    LaunchedEffect(screen, paused, runId, loaded) {
        if (!loaded || screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(1000)
            val current = game ?: break
            if (current.status == TugStatus.PLAYING || current.status == TugStatus.ROUND_OVER) {
                graph.appState.put("tug-o-war", TugOWarEngine.encode(current))
            } else {
                break
            }
        }
    }

    // Write once more the moment the player pauses; clear when the match ends.
    LaunchedEffect(paused, loaded) {
        if (!loaded || !paused) return@LaunchedEffect
        val current = game ?: return@LaunchedEffect
        if (current.status == TugStatus.PLAYING || current.status == TugStatus.ROUND_OVER) {
            graph.appState.put("tug-o-war", TugOWarEngine.encode(current))
        }
    }

    LaunchedEffect(game?.status, loaded) {
        if (!loaded) return@LaunchedEffect
        if (game?.status == TugStatus.MATCH_OVER) graph.appState.clear("tug-o-war")
    }

    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        var last = 0L
        var lastStep = -1
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
                    last = now
                    if (countdownMs > 0L) {
                        countdownMs = (countdownMs - dt).coerceAtLeast(0L)
                        val step = ceil(countdownMs / TUG_COUNTDOWN_STEP_MS.toDouble()).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            bank.play(if (step == 0) "select" else "tick")
                        }
                    } else {
                        val current = game
                        if (current != null && current.status == TugStatus.PLAYING) {
                            game = TugOWarEngine.step(current, dt)
                        }
                    }
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.status) {
        val current = game ?: return@LaunchedEffect
        when (current.status) {
            TugStatus.ROUND_OVER -> bank.play(if (current.roundWinner == TugSide.PLAYER) "score" else "hit")
            TugStatus.MATCH_OVER -> bank.play(if (current.matchWinner == TugSide.PLAYER) "win" else "lose")
            TugStatus.PLAYING -> Unit
        }
    }

    LaunchedEffect(game?.matchWinner, game?.status) {
        val current = game ?: return@LaunchedEffect
        if (current.status == TugStatus.MATCH_OVER && !recorded) {
            recorded = true
            graph.games.record("tug-o-war", current.playerScore, if (current.hotseat) "hotseat" else "ai")
        }
    }

    fun startNew() {
        game = TugOWarEngine.newMatch(mode, hotseat)
        countdownMs = TUG_COUNTDOWN_STEP_MS * 4
        paused = false
        recorded = false
        dragStart = null
        previousTilt = null
        runId++
        screen = "game"
    }

    fun resume() {
        val current = saved ?: return
        game = current
        mode = current.mode
        hotseat = current.hotseat
        countdownMs = 0L
        paused = false
        recorded = false
        previousTilt = null
        runId++
        screen = "game"
    }

    fun pull(side: TugSide) {
        val current = game ?: return
        val next = TugOWarEngine.pull(current, side, System.currentTimeMillis())
        if (next !== current) {
            game = next
            bank.play("click")
        }
    }

    if (screen == "menu") {
        TugMenu(
            saved = saved,
            best = best.firstOrNull()?.score ?: 0,
            onSingle = { hotseat = false; screen = "mode" },
            onMulti = { hotseat = true; screen = "mode" },
            onResume = { resume() },
        )
        return
    }
    if (screen == "mode") {
        TugModeSelect(
            mode = mode,
            hotseat = hotseat,
            onMode = { mode = it },
            onHotseat = { hotseat = it },
            onStart = { startNew() },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    val tilt = rememberTilt(
        enabled = screen == "game" && mode == TugInputMode.SHAKE && !paused && current.status == TugStatus.PLAYING,
        smoothing = 0.5f,
    )
    // A sensorless device degrades SHAKE to the TAP timing window instead of
    // dead input; the header says which of the two is live.
    val sensorFallback = mode == TugInputMode.SHAKE && !tilt.value.available
    val inputLabel = if (sensorFallback) "shake (tap)" else mode.name.lowercase()

    fun pullAt(point: Offset) {
        val snapshot = game ?: return
        if (countdownMs > 0L || paused || snapshot.status != TugStatus.PLAYING) return
        when {
            TugOWarEngine.tapRegisters(snapshot, TugSide.PLAYER, point.y) -> pull(TugSide.PLAYER)
            hotseat && TugOWarEngine.tapRegisters(snapshot, TugSide.OPPONENT, point.y) -> pull(TugSide.OPPONENT)
        }
    }

    LaunchedEffect(tilt.value) {
        val value = tilt.value
        if (!value.available || current.status != TugStatus.PLAYING || paused || countdownMs > 0L) return@LaunchedEffect
        val previous = previousTilt
        previousTilt = value.rollDeg to value.pitchDeg
        if (previous == null) return@LaunchedEffect
        if (TugOWarEngine.isShakeFromTilt(
                (value.rollDeg - previous.first).toDouble(),
                (value.pitchDeg - previous.second).toDouble(),
            )
        ) {
            pull(TugSide.PLAYER)
        }
    }

    DetailScaffold(title = "tug-o-war") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "round ${min(current.round, TugOWarEngine.ROUND_COUNT)}/${TugOWarEngine.ROUND_COUNT}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = if (hotseat) "p1 ${current.playerScore} · p2 ${current.opponentScore}" else "you ${current.playerScore} · ai ${current.opponentScore}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = inputLabel,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(8.dp))
                TugButton(if (paused) "play" else "pause") {
                    paused = !paused
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val shakeTint = mode == TugInputMode.SHAKE
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(mode, hotseat, sensorFallback) {
                            when (mode) {
                                TugInputMode.TAP -> detectTapGestures { offset ->
                                    pullAt(designPoint(size, offset))
                                }

                                TugInputMode.DRAG -> detectDragGestures(
                                    onDragStart = { dragStart = it; dragDistance = 0f },
                                    onDragEnd = {
                                        val start = dragStart
                                        val snapshot = game
                                        if (start != null && snapshot != null && countdownMs == 0L && !paused && snapshot.status == TugStatus.PLAYING) {
                                            val end = Offset(start.x, start.y + dragDistance)
                                            val startPoint = designPoint(size, start)
                                            val endPoint = designPoint(size, end)
                                            when {
                                                TugOWarEngine.dragRegisters(snapshot, TugSide.PLAYER, startPoint.y, endPoint.y, startPoint.x) -> pull(TugSide.PLAYER)
                                                hotseat && TugOWarEngine.dragRegisters(snapshot, TugSide.OPPONENT, startPoint.y, endPoint.y, startPoint.x) -> pull(TugSide.OPPONENT)
                                            }
                                        }
                                        dragStart = null
                                        dragDistance = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDistance += amount.y
                                    },
                                )

                                TugInputMode.SHAKE -> {
                                    if (sensorFallback) {
                                        detectTapGestures { offset ->
                                            pullAt(designPoint(size, offset))
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    drawTugScene(current, colors, countdownMs, if (shakeTint) current else null)
                }

                if (paused) {
                    TugOverlay(
                        "paused",
                        "resume" to { paused = false },
                        "input $inputLabel" to {
                            mode = TugInputMode.entries[(mode.ordinal + 1) % TugInputMode.entries.size]
                        },
                        "menu" to { screen = "menu"; game = null },
                    )
                } else if (current.status == TugStatus.ROUND_OVER) {
                    val title = when (current.roundWinner) {
                        TugSide.PLAYER -> if (hotseat) "player one takes it" else "you take the round"
                        TugSide.OPPONENT -> if (hotseat) "player two takes it" else "the ai takes it"
                        null -> "round over"
                    }
                    TugOverlay(title, "continue" to {
                        game = TugOWarEngine.continueRound(current)
                    }, "menu" to { screen = "menu"; game = null })
                } else if (current.status == TugStatus.MATCH_OVER) {
                    val title = when {
                        current.matchWinner == TugSide.PLAYER -> if (hotseat) "player one wins" else "you win"
                        current.matchWinner == TugSide.OPPONENT -> if (hotseat) "player two wins" else "you lose"
                        else -> "draw"
                    }
                    TugOverlay(
                        title,
                        "rematch" to { startNew() },
                        "menu" to { screen = "menu"; game = null },
                    )
                }
            }
        }
    }
}

private fun designPoint(size: IntSize, offset: Offset): Offset {
    val scale = min(size.width / TUG_DESIGN_W, size.height / TUG_DESIGN_H)
    val ox = (size.width - TUG_DESIGN_W * scale) / 2f
    val oy = (size.height - TUG_DESIGN_H * scale) / 2f
    return Offset((offset.x - ox) / scale, (offset.y - oy) / scale)
}

private fun DrawScope.drawTugScene(
    state: TugState,
    colors: DoradoColors,
    countdownMs: Long,
    shakeState: TugState?,
) {
    val scale = min(size.width / TUG_DESIGN_W, size.height / TUG_DESIGN_H)
    val ox = (size.width - TUG_DESIGN_W * scale) / 2f
    val oy = (size.height - TUG_DESIGN_H * scale) / 2f
    val width = TUG_DESIGN_W * scale
    val height = TUG_DESIGN_H * scale

    drawRect(colors.background, Offset(ox, oy), Size(width, height))
    drawRect(DoradoAccent.LIME.primary.copy(alpha = 0.30f), Offset(ox, oy + 260f * scale), Size(width, 220f * scale))
    drawRect(colors.border, Offset(ox + 24f * scale, oy + 240f * scale), Size(width - 48f * scale, 1f))

    val centreY = oy + 240f * scale
    val displacement = (state.ropeY - TugOWarEngine.ROPE_START).toFloat()
    val wobble = sin(state.clockMs * 0.006) * TugOWarEngine.WOBBLE_PX * scale
    val ropeY = centreY + displacement * scale
    val tier = TugOWarEngine.ropeTier(state.player.velocity - state.opponent.velocity)
    val ropeColor = if (tier > 0) colors.accentBright else colors.textPrimary
    val ropeThickness = (5f + tier * 2f) * scale
    drawRect(
        ropeColor,
        Offset(ox + 34f * scale + wobble.toFloat(), ropeY - ropeThickness / 2f),
        Size(width - 68f * scale, ropeThickness),
    )

    val teamColor = DoradoAccent.CYAN.primary
    val foeColor = DoradoAccent.PINK.primary
    drawTeam(Offset(ox + width / 2f, ropeY + 22f * scale), teamColor, scale, upward = false)
    drawTeam(Offset(ox + width / 2f, ropeY - 22f * scale), foeColor, scale, upward = true)

    if (shakeState != null) {
        val alpha = TugOWarEngine.shakeTintAlpha(shakeState) / 255f
        if (alpha > 0f) {
            val tint = if (TugOWarEngine.shakeTintBlue(shakeState)) DoradoAccent.CYAN.primary else DoradoAccent.PINK.primary
            drawRect(tint.copy(alpha = alpha * 0.6f), Offset(ox, oy), Size(width, height))
        }
    }

    if (countdownMs > 0L) {
        val step = ceil(countdownMs / TUG_COUNTDOWN_STEP_MS.toDouble()).toInt()
        val label = if (step <= 1) "go" else (step - 1).toString()
        drawCircle(colors.background.copy(alpha = 0.75f), 46f * scale, Offset(ox + width / 2f, centreY))
        drawCircle(colors.accent, 46f * scale, Offset(ox + width / 2f, centreY), style = androidx.compose.ui.graphics.drawscope.Stroke(2f * scale))
        val textSize = 40f * scale
        if (label == "go") {
            drawRect(
                colors.accentBright,
                Offset(ox + width / 2f - textSize * 0.9f, centreY - textSize * 0.25f),
                Size(textSize * 1.8f, textSize * 0.5f),
            )
        } else {
            drawRect(
                colors.textPrimary,
                Offset(ox + width / 2f - textSize * 0.12f, centreY - textSize * 0.5f),
                Size(textSize * 0.24f, textSize),
            )
            drawRect(
                colors.textPrimary,
                Offset(ox + width / 2f + textSize * 0.12f, centreY - textSize * 0.5f),
                Size(textSize * 0.24f, textSize),
            )
        }
    }
}

private fun DrawScope.drawTeam(center: Offset, color: Color, scale: Float, upward: Boolean) {
    val head = 13f * scale
    val direction = if (upward) -1f else 1f
    drawCircle(color, head, Offset(center.x, center.y))
    drawRect(
        color,
        Offset(center.x - head * 0.8f, center.y + direction * head * 1.1f - (if (upward) head * 2.2f else 0f)),
        Size(head * 1.6f, head * 2.2f),
    )
}

@Composable
private fun TugOverlay(title: String, vararg actions: Pair<String, () -> Unit>) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) ->
                    TugButton(label) { action() }
                }
            }
        }
    }
}

@Composable
private fun TugButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
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

@Composable
private fun TugMenu(
    saved: TugState?,
    best: Int,
    onSingle: () -> Unit,
    onMulti: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "tug-o-war") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null) {
                TugButton("continue round ${min(saved.round, TugOWarEngine.ROUND_COUNT)}", onResume)
                Spacer(Modifier.height(6.dp))
            }
            TugButton("single player", onSingle)
            Spacer(Modifier.height(4.dp))
            TugButton("multiplayer", onMulti)
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "win 10 rounds against a rising ai. mistimed pulls cost double.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun TugModeSelect(
    mode: TugInputMode,
    hotseat: Boolean,
    onMode: (TugInputMode) -> Unit,
    onHotseat: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "tug-o-war · mode", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "input",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            TugInputMode.entries.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TugButton(
                        label = option.name.lowercase(),
                        onClick = { onMode(option) },
                    )
                    if (option == mode) {
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = " selected",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TugButton(if (hotseat) "hotseat on" else "hotseat off") { onHotseat(!hotseat) }
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = if (hotseat) "  both halves are human" else "  you versus the ai",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(12.dp))
            TugButton("new game", onStart)
            Spacer(Modifier.height(6.dp))
            TugButton("back", onBack)
        }
    }
}
