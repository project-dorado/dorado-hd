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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RunAndJumpApp() {
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
    var progress by remember { mutableStateOf(RnJProgress()) }
    var level by remember { mutableStateOf<RnJLevel?>(null) }
    var game by remember { mutableStateOf<RnJState?>(null) }
    var page by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var pendingSpeed by remember { mutableStateOf<Boolean?>(null) }
    var showingReset by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(false) }
    var seenHints by remember { mutableStateOf(emptySet<Int>()) }
    var recorded by remember { mutableStateOf(false) }
    val scores by graph.games.top("run-and-jump", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = graph.appState.get("run-and-jump")?.let { RnJEngine.decodeProgress(it) } ?: RnJProgress()
    }
    LaunchedEffect(progress) {
        graph.appState.put("run-and-jump", RnJEngine.encodeProgress(progress))
    }

    val active = screen == "game" && game != null && !paused && pendingSpeed == null && !hintVisible &&
        game?.finished == false && (game?.deadMs ?: 0L) <= 0L
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 40L)
                    last = now
                    val currentLevel = level ?: return@withFrameNanos
                    val current = game ?: return@withFrameNanos
                    val next = RnJEngine.step(current, currentLevel, dt)
                    next.events.forEach { event -> bank.play(sfxFor(event)) }
                    game = next
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.finished, game?.missedCoins) {
        val current = game ?: return@LaunchedEffect
        val currentLevel = level ?: return@LaunchedEffect
        if (current.finished && !recorded) {
            recorded = true
            bank.play("win")
            progress = RnJEngine.finishLevel(progress, currentLevel, current, progress.fast)
            graph.games.record("run-and-jump", RnJEngine.totalCoins(progress), "level ${currentLevel.id} deaths ${current.deaths}")
        }
    }

    fun startLevel(picked: RnJLevel, fast: Boolean = progress.fast) {
        level = picked
        game = RnJEngine.newGame(picked, if (fast) RnJEngine.SLOW_MOTION_FAST else RnJEngine.SLOW_MOTION_NORMAL)
        recorded = false
        paused = false
        pendingSpeed = null
        hintVisible = picked.id !in seenHints
        if (hintVisible) seenHints = seenHints + picked.id
        screen = "game"
    }

    if (screen == "menu") {
        RnJMenu(
            progress = progress,
            best = scores.firstOrNull()?.score ?: 0,
            onPlay = {
                page = ((progress.lastLevel - 1) / 4).coerceIn(0, 14)
                screen = "select"
            },
            onOptions = { screen = "options" },
            onAbout = { screen = "about" },
        )
        return
    }
    if (screen == "options") {
        RnJOptions(
            progress = progress,
            onSfx = { progress = progress.copy(sfxLevel = it) },
            onFast = { progress = progress.copy(fast = it) },
            onReset = { showingReset = true },
            onBack = { screen = "menu" },
        )
        if (showingReset) {
            RnJOverlay(
                title = "reset all progress?",
                subtitle = "coins, deaths and unlocks are erased",
                actions = listOf(
                    "reset" to {
                        progress = RnJProgress()
                        showingReset = false
                    },
                    "cancel" to { showingReset = false },
                ),
            )
        }
        return
    }
    if (screen == "about") {
        RnJAbout(onBack = { screen = "menu" })
        return
    }
    if (screen == "select") {
        RnJLevelSelect(
            levels = RnJLevels.all,
            progress = progress,
            page = page,
            onPage = { page = it },
            onPick = { startLevel(it) },
            onBack = { screen = "menu" },
        )
        return
    }

    val currentLevel = level ?: return
    val current = game ?: return
    DetailScaffold(title = "run and jump") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "level ${currentLevel.id}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(
                    text = "coins ${current.collected.size}/${current.totalCoins}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = if (progress.fast) "fast" else "normal",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(8.dp))
                RnJButton(if (paused) "play" else "pause") { paused = !paused }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(active) {
                            detectTapGestures { if (active) game = RnJEngine.tap(game ?: return@detectTapGestures, level ?: return@detectTapGestures) }
                        },
                ) {
                    drawRnJScene(currentLevel, current, colors)
                }
                when {
                    hintVisible -> {
                        RnJOverlay(
                            title = currentLevel.name,
                            subtitle = currentLevel.hint,
                            actions = listOf("start" to { hintVisible = false }),
                        )
                    }

                    pendingSpeed != null -> {
                        RnJOverlay(
                            title = if (pendingSpeed == true) "switch to fast?" else "switch to normal?",
                            subtitle = "the level will restart",
                            actions = listOf(
                                "restart" to {
                                    val fast = pendingSpeed ?: progress.fast
                                    progress = progress.copy(fast = fast)
                                    startLevel(currentLevel, fast)
                                },
                                "cancel" to { pendingSpeed = null },
                            ),
                        )
                    }

                    paused -> {
                        RnJOverlay(
                            title = "paused",
                            actions = listOf(
                                "resume" to { paused = false },
                                "speed" to { pendingSpeed = !progress.fast },
                                "levels" to { screen = "select" },
                                "menu" to { screen = "menu" },
                            ),
                        )
                    }

                    current.finished -> {
                        val actions = mutableListOf<Pair<String, () -> Unit>>()
                        if (current.missedCoins) {
                            actions += "retry" to { startLevel(currentLevel) }
                        }
                        val nextLevel = RnJLevels.all.firstOrNull { it.id == currentLevel.id + 1 }
                        if (nextLevel != null && RnJEngine.isLevelUnlocked(progress, nextLevel)) {
                            actions += "next" to { startLevel(nextLevel) }
                        }
                        actions += "levels" to { screen = "select" }
                        RnJOverlay(
                            title = if (current.missedCoins) "some coins missed" else "level clear",
                            subtitle = "coins ${current.collected.size}/${current.totalCoins} · deaths ${current.deaths}",
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

private fun sfxFor(event: RnJEvent): String = when (event) {
    RnJEvent.JUMP -> "jump"
    RnJEvent.DUCK -> "toss"
    RnJEvent.MOVE_SLOW, RnJEvent.MOVE_NORMAL, RnJEvent.MOVE_FAST -> "whoosh"
    RnJEvent.GRAVITY -> "rotate"
    RnJEvent.WARP -> "whoosh"
    RnJEvent.COIN -> "coin"
    RnJEvent.GOAL -> "win"
    RnJEvent.WOOD -> "hit"
    RnJEvent.SWITCH -> "click"
    RnJEvent.DEAD -> "lose"
    RnJEvent.NULL -> "back"
}

private fun DrawScope.drawRnJScene(level: RnJLevel, state: RnJState, colors: com.heretek.dorado_hd.design.DoradoColors) {
    val scale = min(size.width / RnJEngine.VIEW_W.toFloat(), size.height / (level.heightPx.toFloat()))
    val ox = (size.width - RnJEngine.VIEW_W.toFloat() * scale) / 2f
    val oy = (size.height - level.heightPx.toFloat() * scale) / 2f
    drawRect(colors.background, Offset(ox, oy), Size(RnJEngine.VIEW_W.toFloat() * scale, level.heightPx.toFloat() * scale))

    val camera = state.cameraX.toFloat()
    val tile = RnJEngine.TILE_PX.toFloat() * scale
    val firstCol = (camera / RnJEngine.TILE_PX).toInt().coerceAtLeast(0)
    val lastCol = min(level.cols - 1, ((camera + RnJEngine.VIEW_W) / RnJEngine.TILE_PX).toInt())
    for (row in 0 until level.rows) {
        for (col in firstCol..lastCol) {
            val cellX = ox + (col * RnJEngine.TILE_PX - camera).toFloat() * scale
            val cellY = oy + row * (RnJEngine.TILE_PX.toFloat() * scale)
            for (t in level.tilesAt(col, row)) {
                when (t.kind) {
                    RnJKind.SOLID -> drawRect(colors.border, Offset(cellX, cellY), Size(tile, tile))
                    RnJKind.WOOD -> drawRect(DoradoAccent.ORANGE.primary, Offset(cellX, cellY), Size(tile, tile))
                    RnJKind.SWITCH -> drawRect(
                        if (t.group == 0) DoradoAccent.PINK.primary else DoradoAccent.PURPLE.primary,
                        Offset(cellX, cellY),
                        Size(tile, tile),
                    )

                    RnJKind.GOAL -> {
                        drawRect(colors.accentBright, Offset(cellX, cellY), Size(tile, tile))
                        drawRect(colors.background, Offset(cellX + tile * 0.3f, cellY + tile * 0.2f), Size(tile * 0.4f, tile * 0.6f))
                    }

                    RnJKind.BOOST, RnJKind.WARP -> {
                        if (RnJEngine.isSolid(t, state, level)) {
                            drawRect(colors.border, Offset(cellX, cellY), Size(tile, tile))
                        } else if (t.kind == RnJKind.WARP) {
                            drawRect(colors.accent, Offset(cellX, cellY), Size(tile, tile))
                            drawRect(colors.background, Offset(cellX + tile * 0.25f, cellY + tile * 0.25f), Size(tile * 0.5f, tile * 0.5f))
                        } else {
                            drawBoostPad(t.id, Offset(cellX, cellY), tile, colors)
                        }
                    }
                }
            }
        }
    }

    for ((index, coin) in level.coins.withIndex()) {
        if (state.collected.contains(index)) continue
        val x = ox + (coin.x.toFloat() - camera) * scale
        if (x < ox - tile || x > ox + RnJEngine.VIEW_W.toFloat() * scale + tile) continue
        val y = oy + coin.y.toFloat() * scale
        val wobble = (sin(state.clockMs * 0.01 + index) * 2.0 * scale).toFloat()
        drawCircle(DoradoAccent.ORANGE.primary, RnJEngine.COIN_RADIUS.toFloat() * scale, Offset(x, y + wobble))
        drawCircle(DoradoAccent.ORANGE.bright, RnJEngine.COIN_RADIUS.toFloat() * 0.45f * scale, Offset(x, y + wobble))
    }

    val body = state.body
    val playerX = ox + (body.x.toFloat() - camera) * scale
    val playerY = oy + body.y.toFloat() * scale
    val ducking = RnJEngine.isDucking(state)
    val playerHalfW = RnJEngine.PLAYER_W.toFloat() / 2 * scale
    val playerHalfH = (if (ducking) RnJEngine.PLAYER_DUCK_H else RnJEngine.PLAYER_H).toFloat() / 2 * scale
    if (state.deadMs > 0L) {
        drawRect(colors.textInactive, Offset(playerX - playerHalfW, playerY - playerHalfH), Size(playerHalfW * 2, playerHalfH * 2))
    } else {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(60.0 * i - 90.0)
            val px = playerX + (playerHalfW * cos(angle)).toFloat()
            val py = playerY + (playerHalfH * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, colors.textPrimary)
        drawPath(path, colors.background, style = Stroke(width = 1.5f * scale))
    }
    if (state.currentBoostId >= 0 && state.deadMs <= 0L) {
        drawCircle(colors.accent, 3f * scale, Offset(playerX, playerY - playerHalfH - 6f * scale))
    }
}

private fun DrawScope.drawBoostPad(id: Int, topLeft: Offset, tile: Float, colors: com.heretek.dorado_hd.design.DoradoColors) {
    val inset = tile * 0.18f
    val inner = tile - inset * 2
    drawRect(colors.tilePressed, Offset(topLeft.x + inset, topLeft.y + inset), Size(inner, inner))
    drawRect(colors.accent, Offset(topLeft.x + inset, topLeft.y + inset), Size(inner, inner), style = Stroke(1.5f))
    val base = RnJEngine.baseBoostId(id)
    val up = base in setOf(0, 2, 3)
    val down = base == 1
    val left = base in setOf(4, 9, 11)
    val right = base in setOf(5, 10, 12)
    val center = Offset(topLeft.x + tile / 2, topLeft.y + tile / 2)
    val direction = when {
        base == 6 -> "flip"
        base == 7 -> "goal"
        base == 8 -> "null"
        base == 13 || base == 14 -> "switch"
        up -> "up"
        down -> "down"
        left -> "left"
        right -> "right"
        else -> "up"
    }
    when (direction) {
        "up" -> drawTriangle(center, inner * 0.5f, up = true, colors.accentBright)
        "down" -> drawTriangle(center, inner * 0.5f, up = false, colors.accentBright)
        "left" -> drawTriangle(center, inner * 0.5f, up = true, DoradoAccent.CYAN.bright, rotate = -90.0)
        "right" -> drawTriangle(center, inner * 0.5f, up = true, DoradoAccent.CYAN.bright, rotate = 90.0)
        "flip" -> drawCircle(colors.accentBright, inner * 0.28f, center)
        "goal" -> drawRect(colors.accentBright, Offset(center.x - inner * 0.2f, center.y - inner * 0.3f), Size(inner * 0.4f, inner * 0.6f))
        "switch" -> drawCircle(DoradoAccent.LIME.bright, inner * 0.28f, center)
        else -> drawRect(colors.textInactive, Offset(center.x - inner * 0.15f, center.y - inner * 0.15f), Size(inner * 0.3f, inner * 0.3f))
    }
}

private fun DrawScope.drawTriangle(center: Offset, radius: Float, up: Boolean, color: Color, rotate: Double = 0.0) {
    val baseAngle = if (up) -90.0 else 90.0
    val path = Path()
    for (i in 0 until 3) {
        val angle = Math.toRadians(baseAngle + rotate + 120.0 * i)
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
private fun RnJOverlay(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = subtitle,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> RnJButton(label) { action() } }
            }
        }
    }
}

@Composable
private fun RnJButton(label: String, onClick: () -> Unit) {
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
private fun RnJMenu(progress: RnJProgress, best: Int, onPlay: () -> Unit, onOptions: () -> Unit, onAbout: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "run and jump") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            RnJButton("play!", onPlay)
            Spacer(Modifier.height(4.dp))
            RnJButton("options", onOptions)
            Spacer(Modifier.height(4.dp))
            RnJButton("about", onAbout)
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "coins ${RnJEngine.totalCoins(progress)} · deaths ${progress.stats.values.sumOf { it.deaths }}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            BasicText(
                text = "best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun RnJOptions(
    progress: RnJProgress,
    onSfx: (Int) -> Unit,
    onFast: (Boolean) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "run and jump · options", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "sfx volume",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("off", "low", "med", "high").forEachIndexed { index, label ->
                    RnJButton(if (progress.sfxLevel == index) "$label*" else label) { onSfx(index) }
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "speed",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RnJButton(if (!progress.fast) "normal*" else "normal") { onFast(false) }
                RnJButton(if (progress.fast) "fast*" else "fast") { onFast(true) }
            }
            Spacer(Modifier.height(10.dp))
            RnJButton("reset data", onReset)
            Spacer(Modifier.height(6.dp))
            RnJButton("back", onBack)
        }
    }
}

@Composable
private fun RnJAbout(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "run and jump · about", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "run and/or jump. yay!",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "tap anywhere to fire the pad under the player. plain ground is fatal, so keep a pad beneath you.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "original levels, 58 of them. clean-room re-creation.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun RnJLevelSelect(
    levels: List<RnJLevel>,
    progress: RnJProgress,
    page: Int,
    onPage: (Int) -> Unit,
    onPick: (RnJLevel) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val pages = (levels.size + 3) / 4
    val safePage = page.coerceIn(0, pages - 1)
    DetailScaffold(title = "run and jump · levels", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val from = safePage * 4
            levels.drop(from).take(4).forEach { level ->
                val unlocked = RnJEngine.isLevelUnlocked(progress, level)
                val stat = progress.stats[level.id]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(if (stat?.completed == true) colors.tilePressed else colors.tile)
                            .border(0.5.dp, if (unlocked) colors.border else colors.elevated)
                            .pointerInput(level.id, unlocked) {
                                if (unlocked) detectTapGestures(onTap = { onPick(level) })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (unlocked) level.id.toString() else "—",
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (unlocked) colors.textPrimary else colors.textInactive,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        BasicText(
                            text = level.name,
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        val coins = stat?.coins ?: 0
                        val deaths = stat?.deaths ?: 0
                        val flags = buildString {
                            if (stat?.completed == true) append("cleared · ")
                            if (stat?.allCoinsFast == true) append("all coins fast · ")
                            else if (stat?.beatFast == true) append("fast · ")
                            if (!unlocked && level.coinsToUnlock > 0) append("needs ${level.coinsToUnlock} coins")
                        }
                        BasicText(
                            text = "coins $coins/${level.coins.size} · deaths $deaths ${if (flags.isEmpty()) "" else "· $flags"}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RnJButton("prev") { onPage((safePage - 1).coerceAtLeast(0)) }
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = "page ${safePage + 1}/$pages",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(6.dp))
                RnJButton("next") { onPage((safePage + 1).coerceAtMost(pages - 1)) }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "lifetime coins ${RnJEngine.totalCoins(progress)} · group unlocks at 31 / 85 / 135 / 220",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}
