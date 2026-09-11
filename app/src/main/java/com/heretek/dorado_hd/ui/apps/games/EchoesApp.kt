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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ECHOES_SAVE_KEY = "echoes"
private const val ECHOES_PROGRESS_KEY = "echoes.progress"
private const val ECHOES_COUNTDOWN_MS = 1800.0
private const val ECHOES_JOYSTICK_RADIUS = 46f

@Composable
fun EchoesApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val audioScope = rememberCoroutineScope()
    val synth = remember { MiniSynth(audioScope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var difficulty by remember { mutableStateOf(EchoesDifficulty.MEDIUM) }
    var mode by remember { mutableStateOf(EchoesMode.CAMPAIGN) }
    var game by remember { mutableStateOf<EchoesGame?>(null) }
    var saved by remember { mutableStateOf<EchoesGame?>(null) }
    var progress by remember { mutableStateOf(EchoesProgress()) }
    var loaded by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var stickOrigin by remember { mutableStateOf<Offset?>(null) }
    var stickVector by remember { mutableStateOf(Offset.Zero) }
    var input by remember { mutableStateOf(EchoesInput.NONE) }
    val best by graph.games.top("echoes", 8).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = EchoesEngine.decodeProgress(graph.appState.get(ECHOES_PROGRESS_KEY))
        saved = graph.appState.get(ECHOES_SAVE_KEY)?.let { EchoesEngine.decode(it) }
        loaded = true
    }

    LaunchedEffect(progress, loaded) {
        if (loaded) graph.appState.put(ECHOES_PROGRESS_KEY, EchoesEngine.encodeProgress(progress))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val current = game
            if (current == null || current.status != EchoesStatus.PLAYING) {
                graph.appState.clear(ECHOES_SAVE_KEY)
            } else {
                graph.appState.put(ECHOES_SAVE_KEY, EchoesEngine.encode(current))
            }
        }
    }

    LaunchedEffect(screen, paused) {
        if (screen != "game") return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                } else {
                    val dt = ((now - last) / 1_000_000.0).coerceIn(0.0, 50.0)
                    last = now
                    if (!paused) {
                        val current = game
                        if (current != null && current.status == EchoesStatus.PLAYING) {
                            val next = EchoesEngine.step(current, dt, input)
                            for (event in next.events) bank.play(soundFor(event))
                            game = next
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(game?.status, recorded) {
        val current = game ?: return@LaunchedEffect
        if (current.status == EchoesStatus.PLAYING || recorded) return@LaunchedEffect
        recorded = true
        saved = null
        val collected = current.collectedCrystals
        val echoes = current.enemies.count { it.kind == EchoKind.ECHO }
        progress = if (current.status == EchoesStatus.WON && current.mode == EchoesMode.CAMPAIGN) {
            progress.copy(
                unlocked = maxOf(progress.unlocked, current.levelIndex + 1).coerceAtMost(EchoesArenas.ALL.lastIndex),
                best = progress.best + (current.levelIndex to maxOf(progress.best[current.levelIndex] ?: 0, current.score)),
                totalCrystals = progress.totalCrystals + collected,
                echoesSpawned = progress.echoesSpawned + echoes,
                campaignCleared = progress.campaignCleared || current.levelIndex >= EchoesArenas.ALL.lastIndex,
            )
        } else {
            progress.copy(
                totalCrystals = progress.totalCrystals + collected,
                echoesSpawned = progress.echoesSpawned + echoes,
            )
        }
        graph.games.record("echoes", current.score, metaFor(current))
    }

    fun startNew(level: Int) {
        game = EchoesEngine.newGame(mode, difficulty, System.currentTimeMillis().toInt(), level, ECHOES_COUNTDOWN_MS)
        recorded = false
        paused = false
        stickOrigin = null
        stickVector = Offset.Zero
        input = EchoesInput.NONE
        screen = "game"
    }

    fun resume() {
        val current = saved ?: return
        game = current
        difficulty = current.difficulty
        mode = current.mode
        recorded = false
        paused = false
        stickOrigin = null
        stickVector = Offset.Zero
        input = EchoesInput.NONE
        screen = "game"
    }

    fun quitToMenu() {
        val current = game
        saved = if (current != null && current.status == EchoesStatus.PLAYING) current else null
        screen = "menu"
        game = null
        paused = false
    }

    when (screen) {
        "menu" -> {
            EchoesMenu(
                colors = colors,
                saved = saved,
                best = best.firstOrNull()?.score ?: 0,
                progress = progress,
                onNew = { screen = "difficulty" },
                onResume = { resume() },
            )
            return
        }

        "difficulty" -> {
            EchoesChoiceScreen(
                title = "echoes · difficulty",
                options = EchoesDifficulty.entries.map { it.name.lowercase() to "${it.lives} lives · echoes ${(it.echoSpeed * 100).toInt()}%" },
                onBack = { screen = "menu" },
            ) { index ->
                difficulty = EchoesDifficulty.entries[index]
                screen = "mode"
            }
            return
        }

        "mode" -> {
            EchoesChoiceScreen(
                title = "echoes · mode",
                options = EchoesMode.entries.map { it.name.lowercase() to modeBlurb(it) },
                onBack = { screen = "difficulty" },
            ) { index ->
                mode = EchoesMode.entries[index]
                if (mode == EchoesMode.CAMPAIGN) screen = "levels" else startNew(0)
            }
            return
        }

        "levels" -> {
            EchoesLevelSelect(
                colors = colors,
                unlocked = progress.unlocked,
                best = progress.best,
                onBack = { screen = "mode" },
                onPick = { startNew(it) },
            )
            return
        }
    }

    val current = game ?: return
    DetailScaffold(title = "echoes", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                EchoesHud(current)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(0.5.dp, colors.border),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            // Keyed only on pause/status: the countdown ticks every
                            // frame and used to cancel in-progress drags (A-25).
                            .pointerInput(paused, current.status) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        stickOrigin = offset
                                        stickVector = Offset.Zero
                                        input = EchoesInput.NONE
                                    },
                                    onDragEnd = {
                                        stickOrigin = null
                                        stickVector = Offset.Zero
                                        input = EchoesInput.NONE
                                    },
                                    onDragCancel = {
                                        stickOrigin = null
                                        stickVector = Offset.Zero
                                        input = EchoesInput.NONE
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val origin = stickOrigin ?: change.position
                                        val delta = change.position - origin
                                        stickVector = delta
                                        input = EchoesInput.fromVector(
                                            (delta.x / ECHOES_JOYSTICK_RADIUS).toDouble(),
                                            (delta.y / ECHOES_JOYSTICK_RADIUS).toDouble(),
                                        )
                                    },
                                )
                            },
                    ) {
                        drawEchoesArena(current, colors, colors.background, stickOrigin, stickVector)
                    }
                    if (current.countdownMs > 0.0) {
                        EchoesOverlay(
                            title = if (ceil(current.countdownMs / 600.0).toInt() <= 1) "go" else "${ceil(current.countdownMs / 600.0).toInt() - 1}",
                            lines = listOf("collect every crystal"),
                            actions = emptyList(),
                        )
                    }
                }
            }
            if (paused && current.status == EchoesStatus.PLAYING) {
                EchoesOverlay(
                    title = "paused",
                    lines = listOf("${current.collectedCrystals}/${current.totalCrystals} crystals · ${current.score} pts"),
                    actions = listOf(
                        "resume" to { paused = false },
                        "restart" to { startNew(current.levelIndex) },
                        "menu" to { quitToMenu() },
                    ),
                )
            }
            if (current.status == EchoesStatus.WON) {
                val nextLevel = current.levelIndex + 1
                val hasNext = current.mode == EchoesMode.CAMPAIGN && nextLevel < EchoesArenas.ALL.size
                EchoesOverlay(
                    title = "victory",
                    lines = listOf("${current.score} pts · ${formatTime(current.elapsedMs)}"),
                    actions = buildList {
                        if (hasNext) add("next level" to { startNew(nextLevel) })
                        add("replay" to { startNew(current.levelIndex) })
                        add("menu" to { quitToMenu() })
                    },
                )
            }
            if (current.status == EchoesStatus.LOST) {
                EchoesOverlay(
                    title = "game over",
                    lines = listOf("${current.collectedCrystals}/${current.totalCrystals} crystals · ${current.score} pts"),
                    actions = listOf(
                        "retry" to { startNew(current.levelIndex) },
                        "menu" to { quitToMenu() },
                    ),
                )
            }
        }
    }
}

private fun soundFor(event: EchoesEvent): String = when (event) {
    EchoesEvent.COLLECT -> "coin"
    EchoesEvent.POWERUP -> "score"
    EchoesEvent.ECHO_SPAWN -> "whoosh"
    EchoesEvent.HIT -> "hit"
    EchoesEvent.DEAD -> "lose"
    EchoesEvent.WIN -> "win"
    EchoesEvent.ROUND -> "score"
    EchoesEvent.PULSE -> "explode"
    EchoesEvent.RESPAWN -> "back"
}

private fun modeBlurb(mode: EchoesMode): String = when (mode) {
    EchoesMode.CAMPAIGN -> "clear every arena in order"
    EchoesMode.ARCADE -> "endless rounds, one life pool"
    EchoesMode.SURVIVAL -> "endless + faster hazards each round"
    EchoesMode.CANDY -> "endless with bonus crystals"
    EchoesMode.HIPPY -> "new ghost every eight seconds"
}

private fun metaFor(game: EchoesGame): String =
    "${game.mode.name.lowercase()}|${game.difficulty.name.lowercase()}|level ${game.levelIndex + 1}"

private fun formatTime(ms: Double): String {
    val total = (ms / 1000.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun EchoesHud(game: EchoesGame) {
    val colors = LocalDoradoColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = "${game.arena.name} ${game.collectedCrystals}/${game.totalCrystals}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = "lives ${game.lives}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "score ${game.score}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = formatTime(game.elapsedMs),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
    }
    val active = buildList {
        if (game.effects.freezeMs > 0.0) add("freeze")
        if (game.effects.ghostMs > 0.0) add("ghost")
        if (game.effects.shrinkMs > 0.0) add("shrink")
        if (game.effects.magnetMs > 0.0) add("magnet")
        if (game.effects.stenchMs > 0.0) add("stench")
        if (game.effects.pulseMs > 0.0) add("pulse")
    }
    if (active.isNotEmpty()) {
        BasicText(
            text = active.joinToString(" · "),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
        )
    }
}

@Composable
private fun EchoesMenu(
    colors: DoradoColors,
    saved: EchoesGame?,
    best: Int,
    progress: EchoesProgress,
    onNew: () -> Unit,
    onResume: () -> Unit,
) {
    DetailScaffold(title = "echoes") {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (saved != null) {
                EchoesButton("continue ${saved.arena.name}", Modifier.fillMaxWidth(), onClick = onResume)
                Spacer(Modifier.height(6.dp))
            }
            EchoesButton("new run", Modifier.fillMaxWidth(), onClick = onNew)
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "best $best · arenas ${min(progress.unlocked + 1, EchoesArenas.ALL.size)}/${EchoesArenas.ALL.size}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "collect every crystal. each one leaves an echo that replays your path.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "trophies",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            EchoesAchievement.entries.forEach { achievement ->
                val rank = EchoesEngine.achievementRank(achievement, progress)
                BasicText(
                    text = "${achievement.name.lowercase().replace('_', ' ')} · ${rank.name.lowercase()}",
                    style = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp,
                        color = if (rank == EchoesRank.NONE) colors.textInactive else colors.accentBright,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EchoesChoiceScreen(
    title: String,
    options: List<Pair<String, String>>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = title, onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            options.forEachIndexed { index, (label, blurb) ->
                EchoesButton(label, Modifier.fillMaxWidth()) { onPick(index) }
                BasicText(
                    text = blurb,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun EchoesLevelSelect(
    colors: DoradoColors,
    unlocked: Int,
    best: Map<Int, Int>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    DetailScaffold(title = "echoes · arenas", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            var row = 0
            while (row < EchoesArenas.ALL.size) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until 4) {
                        val index = row + column
                        if (index >= EchoesArenas.ALL.size) {
                            Spacer(Modifier.weight(1f))
                            continue
                        }
                        val arena = EchoesArenas.ALL[index]
                        val open = index <= unlocked
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.4f)
                                .background(if (open) colors.tile else colors.elevated)
                                .border(
                                    0.5.dp,
                                    if (index < unlocked) colors.accent else colors.border,
                                )
                                .pointerInput(index, open) {
                                    if (open) detectTapGestures(onTap = { onPick(index) })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(
                                    text = arena.name,
                                    style = TextStyle(
                                        fontFamily = Selawik,
                                        fontSize = DoradoTokens.TYPE_LIST.sp,
                                        color = if (open) colors.textPrimary else colors.textInactive,
                                    ),
                                )
                                BasicText(
                                    text = best[index]?.let { "best $it" } ?: if (open) "open" else "locked",
                                    style = TextStyle(
                                        fontFamily = Selawik,
                                        fontSize = DoradoTokens.TYPE_CAPTION.sp,
                                        color = colors.textSecondary,
                                    ),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                row += 4
            }
        }
    }
}

@Composable
private fun EchoesButton(
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
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
    }
}

@Composable
private fun EchoesOverlay(
    title: String,
    lines: List<String>,
    actions: List<Pair<String, () -> Unit>>,
) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.86f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp, color = colors.accent)
            lines.forEach { line ->
                Spacer(Modifier.height(4.dp))
                EdgeCropText(text = line, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEach { (label, action) -> EchoesButton(label) { action() } }
                }
            }
        }
    }
}

private fun DrawScope.drawEchoesArena(
    game: EchoesGame,
    colors: DoradoColors,
    backdrop: Color,
    stickOrigin: Offset?,
    stickVector: Offset,
) {
    val scale = min(size.width / EchoesEngine.ARENA_W.toFloat(), size.height / EchoesEngine.ARENA_H.toFloat())
    val width = EchoesEngine.ARENA_W.toFloat() * scale
    val height = EchoesEngine.ARENA_H.toFloat() * scale
    val ox = (size.width - width) / 2f
    val oy = (size.height - height) / 2f

    fun sx(x: Double) = ox + x.toFloat() * scale
    fun sy(y: Double) = oy + y.toFloat() * scale

    drawRect(backdrop, Offset(ox, oy), Size(width, height))

    val arena = game.arena
    for (row in 0 until arena.rows) {
        for (col in 0 until arena.cols) {
            if (!arena.wallAt(col, row)) continue
            drawRect(
                colors.tile,
                Offset(sx(col * EchoesEngine.TILE), sy(row * EchoesEngine.TILE)),
                Size((EchoesEngine.TILE * scale).toFloat(), (EchoesEngine.TILE * scale).toFloat()),
            )
        }
    }
    for (row in 0 until arena.rows) {
        for (col in 0 until arena.cols) {
            if (!arena.spikeAt(col, row)) continue
            val cx = sx((col + 0.5) * EchoesEngine.TILE)
            val cy = sy((row + 0.5) * EchoesEngine.TILE)
            val r = 7f * scale
            val path = Path()
            path.moveTo(cx - r, cy + r)
            path.lineTo(cx, cy - r)
            path.lineTo(cx + r, cy + r)
            path.close()
            drawPath(path, colors.textSecondary)
        }
    }

    for (point in game.trail) {
        drawCircle(colors.accent.copy(alpha = 0.35f), 3.2f * scale, Offset(sx(point.x), sy(point.y)))
    }

    for (crystal in game.crystals) {
        if (crystal.collected) continue
        val cx = sx(crystal.x)
        val cy = sy(crystal.y)
        val r = 7f * scale
        val tint = if (crystal.secret) DoradoAccent.PINK.bright else DoradoAccent.CYAN.primary
        val path = Path()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r * 0.75f, cy)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r * 0.75f, cy)
        path.close()
        drawPath(path, tint)
    }

    for (enemy in game.enemies) {
        if (!enemy.alive) continue
        val cx = sx(enemy.x)
        val cy = sy(enemy.y)
        val tint = when (enemy.kind) {
            EchoKind.ECHO -> DoradoAccent.PURPLE.primary
            EchoKind.FLYER -> DoradoAccent.ORANGE.primary
            EchoKind.RISER -> DoradoAccent.LIME.primary
        }
        val radius = enemy.radius.toFloat() * scale
        drawCircle(tint.copy(alpha = if (enemy.kind == EchoKind.ECHO) 0.55f else 0.85f), radius, Offset(cx, cy))
        drawCircle(tint.copy(alpha = 0.9f), radius, Offset(cx, cy), style = Stroke(1.4f * scale))
    }

    val player = game.player
    val px = sx(player.x)
    val py = sy(player.y)
    val playerRadius = (EchoesEngine.PLAYER_RADIUS * player.scale).toFloat() * scale
    val playerTint = if (game.effects.ghostMs > 0.0) colors.accent.copy(alpha = 0.55f) else colors.accent
    drawCircle(playerTint, playerRadius, Offset(px, py))
    drawCircle(colors.accentBright, playerRadius, Offset(px, py), style = Stroke(1.6f * scale))
    if (game.effects.invulnMs > 0.0 && game.effects.ghostMs <= 0.0) {
        drawCircle(colors.textPrimary.copy(alpha = 0.7f), playerRadius + 3f * scale, Offset(px, py), style = Stroke(1.2f * scale))
    }

    if (stickOrigin != null) {
        val origin = stickOrigin
        drawCircle(colors.border, ECHOES_JOYSTICK_RADIUS, origin, style = Stroke(2f))
        val clamped = if (stickVector.getDistance() > ECHOES_JOYSTICK_RADIUS) {
            stickVector / stickVector.getDistance() * ECHOES_JOYSTICK_RADIUS
        } else {
            stickVector
        }
        drawCircle(colors.accent.copy(alpha = 0.8f), 14f, origin + clamped)
    }
}
