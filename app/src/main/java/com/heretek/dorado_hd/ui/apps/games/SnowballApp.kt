package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val SNOW_COUNTDOWN_TOTAL_MS = 500L + 3 * 800L

@Composable
fun SnowballApp() {
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
    var progress by remember { mutableStateOf(SnowProgress()) }
    var game by remember { mutableStateOf<SnowState?>(null) }
    var savedRun by remember { mutableStateOf<SnowState?>(null) }
    var countdownMs by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var lastScore by remember { mutableStateOf(0) }
    var menuMessage by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf(0) }
    var touchVector by remember { mutableStateOf(Offset.Zero) }
    val scores by graph.games.top("snowball", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = graph.appState.get("snowball")?.let { SnowballEngine.decodeProgress(it) } ?: SnowProgress()
        savedRun = graph.appState.get("snowball-run")?.let { SnowballEngine.decode(it) }
        loaded = true
    }
    LaunchedEffect(progress, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("snowball", SnowballEngine.encodeProgress(progress))
    }
    // Run snapshots are periodic: keying on the 60 Hz game state wrote the
    // whole run (cell grid included) every frame. Leave the stored run alone
    // while there is no in-memory game (that null used to clobber the load).
    LaunchedEffect(screen, paused, runId, loaded) {
        if (!loaded || screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(1000)
            val current = game ?: break
            if (current.status == SnowStatus.PLAYING) {
                graph.appState.put("snowball-run", SnowballEngine.encode(current))
            } else {
                break
            }
        }
    }
    LaunchedEffect(game?.status, loaded) {
        if (!loaded) return@LaunchedEffect
        if (game?.status == SnowStatus.WON || game?.status == SnowStatus.LOST) {
            graph.appState.clear("snowball-run")
        }
    }

    val playing = screen == "game" && game?.status == SnowStatus.PLAYING && !paused && countdownMs <= 0L
    val tilt = rememberTilt(enabled = playing, smoothing = 0.8f)
    val sensorAvailable = tilt.value.available

    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        var last = 0L
        var lastStep = -1
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 40L)
                    last = now
                    if (countdownMs > 0L) {
                        countdownMs = (countdownMs - dt).coerceAtLeast(0L)
                        val step = ceil((countdownMs - 500L).coerceAtLeast(0L) / 800.0).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            bank.play(if (countdownMs <= 500L) "select" else "tick")
                        }
                    } else {
                        val current = game ?: return@withFrameNanos
                        val t = tilt.value
                        // Sensorless fallback: the drag vector stands in for
                        // tilt and decays so a nudge is a nudge, not a burn.
                        val ax: Double
                        val ay: Double
                        if (t.available) {
                            ax = -sin(t.pitchDeg * PI / 180.0)
                            ay = sin(t.rollDeg * PI / 180.0)
                        } else {
                            ax = touchVector.x.toDouble()
                            ay = touchVector.y.toDouble()
                            touchVector = touchVector * 0.9f
                        }
                        val next = SnowballEngine.step(current, dt, ax, ay)
                        if (next.score > lastScore) bank.play(if (current.mode == SnowMode.CAMPAIGN) "score" else "coin")
                        lastScore = next.score
                        game = next
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
            SnowStatus.WON -> {
                bank.play("win")
                if (current.mode == SnowMode.CAMPAIGN) {
                    progress = progress.copy(
                        completed = progress.completed + current.level,
                        lastLevel = min(current.level + 1, SnowballEngine.CAMPAIGN_LEVELS),
                    )
                }
            }

            SnowStatus.LOST -> bank.play("lose")
            SnowStatus.PLAYING -> Unit
        }
    }

    LaunchedEffect(game?.status, game?.score, recorded) {
        val current = game ?: return@LaunchedEffect
        if (current.status != SnowStatus.PLAYING && !recorded) {
            recorded = true
            if (current.mode == SnowMode.SURVIVAL) {
                graph.games.record("snowball", current.score, "survival")
            } else if (current.status == SnowStatus.WON) {
                graph.games.record("snowball", current.score, "campaign level ${current.level}")
            }
        }
    }

    fun startCampaign(level: Int, seed: Int = System.currentTimeMillis().toInt()) {
        game = SnowballEngine.newCampaign(level, seed)
        showTutorial = !progress.tutorialCampaignSeen
        countdownMs = if (showTutorial) 0L else SNOW_COUNTDOWN_TOTAL_MS
        paused = false
        recorded = false
        lastScore = 0
        runId++
        screen = "game"
    }

    fun startSurvival() {
        game = SnowballEngine.newSurvival(System.currentTimeMillis().toInt())
        showTutorial = !progress.tutorialSurvivalSeen
        countdownMs = if (showTutorial) 0L else SNOW_COUNTDOWN_TOTAL_MS
        paused = false
        recorded = false
        lastScore = 0
        runId++
        screen = "game"
    }

    fun togglePause() {
        val current = game ?: return
        if (current.status != SnowStatus.PLAYING) return
        if (countdownMs > 0L) {
            countdownMs = 0L
            return
        }
        paused = !paused
        bank.play(if (paused) "click" else "select")
    }

    if (screen == "menu") {
        SnowMenu(
            saved = savedRun,
            campaignSeen = progress.tutorialCampaignSeen,
            survivalSeen = progress.tutorialSurvivalSeen,
            best = scores.firstOrNull()?.score ?: 0,
            message = menuMessage,
            onCampaign = { screen = "select" },
            onSurvival = { startSurvival() },
            onResume = {
                val run = savedRun ?: return@SnowMenu
                game = run
                countdownMs = 0L
                paused = true
                recorded = false
                lastScore = run.score
                runId++
                screen = "game"
            },
            onScores = { screen = "scores" },
        )
        return
    }
    if (screen == "select") {
        SnowLevelSelect(
            completed = progress.completed,
            onPick = { level -> startCampaign(level) },
            onBack = { screen = "menu" },
        )
        return
    }
    if (screen == "scores") {
        SnowScores(
            scores = scores.map { it.score to (it.meta ?: "") },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "snowball") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            val levelLine = if (current.mode == SnowMode.CAMPAIGN) "level ${SnowballEngine.levelCode(current.level)}" else "survival"
            val coinsLine = if (current.mode == SnowMode.CAMPAIGN) "coins ${current.coins.size}" else SnowballEngine.scoreLabel(current.score)
            Row(
                Modifier.fillMaxWidth().appDescription("snowball, $levelLine, $coinsLine"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = levelLine,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = coinsLine,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.width(10.dp))
                SnowButton(if (paused) "play" else "pause") { togglePause() }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .appDescription("snowball board, $levelLine, $coinsLine")
                        .appTap(label = "pause") { togglePause() }
                        .pointerInput(sensorAvailable) {
                            if (!sensorAvailable) {
                                detectDragGestures { change, amount ->
                                    change.consume()
                                    touchVector = Offset(
                                        (touchVector.x + amount.x / 160f).coerceIn(-1f, 1f),
                                        (touchVector.y + amount.y / 160f).coerceIn(-1f, 1f),
                                    )
                                }
                            }
                        },
                ) {
                    drawSnowScene(current, colors)
                }
                when {
                    showTutorial -> {
                        SnowOverlay(
                            title = if (current.mode == SnowMode.CAMPAIGN) "campaign" else "survival",
                            subtitle = if (current.mode == SnowMode.CAMPAIGN) {
                                "tilt or drag to roll. collect the number coins from high to low."
                            } else {
                                "tilt or drag to roll. grab stars before the ice opens up."
                            },
                            actions = listOf(
                                "got it" to {
                                    showTutorial = false
                                    progress = if (current.mode == SnowMode.CAMPAIGN) {
                                        progress.copy(tutorialCampaignSeen = true)
                                    } else {
                                        progress.copy(tutorialSurvivalSeen = true)
                                    }
                                    countdownMs = SNOW_COUNTDOWN_TOTAL_MS
                                },
                            ),
                        )
                    }

                    countdownMs > 0L -> {
                        val step = ceil((countdownMs - 500L).coerceAtLeast(0L) / 800.0).toInt()
                        SnowBanner(if (countdownMs <= 500L) "go" else if (step <= 0) "1" else step.toString())
                    }

                    paused && current.status == SnowStatus.PLAYING -> {
                        SnowOverlay(
                            title = "paused",
                            actions = listOf(
                                "resume" to { paused = false },
                                "menu" to { screen = "menu"; game = null },
                            ),
                        )
                    }

                    current.status == SnowStatus.PLAYING && countdownMs <= 0L && !sensorAvailable -> {
                        BasicText(
                            text = "drag to roll",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                                .background(colors.background),
                        )
                    }

                    current.status == SnowStatus.WON || current.status == SnowStatus.LOST -> {
                        val title = when {
                            current.status == SnowStatus.WON -> "level clear"
                            current.loseReason == SnowLoseReason.WRONG_COIN -> "wrong coin"
                            else -> "through the ice"
                        }
                        val actions = mutableListOf<Pair<String, () -> Unit>>()
                        if (current.mode == SnowMode.CAMPAIGN) {
                            if (current.status == SnowStatus.WON && current.level < SnowballEngine.CAMPAIGN_LEVELS) {
                                actions += "next" to { startCampaign(current.level + 1) }
                            }
                            actions += "retry" to { startCampaign(current.level) }
                        } else {
                            actions += "again" to { startSurvival() }
                        }
                        actions += "menu" to { screen = "menu"; game = null }
                        SnowOverlay(title = title, subtitle = "score ${SnowballEngine.scoreLabel(current.score)}", actions = actions)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSnowScene(state: SnowState, colors: DoradoColors) {
    val scale = min(size.width / SnowballEngine.BOARD_WIDTH.toFloat(), size.height / SnowballEngine.BOARD_HEIGHT.toFloat())
    val ox = (size.width - SnowballEngine.BOARD_WIDTH.toFloat() * scale) / 2f
    val oy = (size.height - SnowballEngine.BOARD_HEIGHT.toFloat() * scale) / 2f
    drawRect(colors.background, Offset(ox, oy), Size(SnowballEngine.BOARD_WIDTH.toFloat() * scale, SnowballEngine.BOARD_HEIGHT.toFloat() * scale))

    val block = SnowballEngine.BLOCK.toFloat() * scale
    for (row in 0 until SnowballEngine.ROWS) {
        for (col in 0 until SnowballEngine.COLUMNS) {
            val tile = state.tiles[row * SnowballEngine.COLUMNS + col]
            val base = when (tile.kind) {
                SnowTileKind.NORMAL -> colors.tile
                SnowTileKind.SAFE -> DoradoAccent.LIME.primary
                SnowTileKind.WALL -> colors.border
                SnowTileKind.ICE -> DoradoAccent.CYAN.primary
            }
            val alpha = if (tile.kind == SnowTileKind.NORMAL || tile.kind == SnowTileKind.ICE) tile.alpha else 1f
            if (alpha <= 0f) continue
            drawRect(
                base.copy(alpha = alpha.coerceIn(0f, 1f) * 0.85f),
                Offset(ox + col * block + 1f, oy + row * block + 1f),
                Size(block - 2f, block - 2f),
            )
        }
    }

    for (coin in state.coins) {
        val center = Offset(ox + coin.x.toFloat() * scale, oy + coin.y.toFloat() * scale)
        val radius = SnowballEngine.COIN_RADIUS.toFloat() * scale
        drawCircle(DoradoAccent.ORANGE.primary, radius, center)
        drawCircle(colors.background, radius * 0.55f, center)
        if (coin.number > 0) {
            val spokes = coin.number.coerceIn(1, 9)
            for (i in 0 until spokes) {
                val angle = (2.0 * PI * i / spokes) + coin.rotationDeg * PI / 180.0
                val x = center.x + (radius * 0.35f * cos(angle)).toFloat()
                val y = center.y + (radius * 0.35f * sin(angle)).toFloat()
                drawCircle(DoradoAccent.ORANGE.primary, radius * 0.12f, Offset(x, y))
            }
        } else {
            val angle = coin.rotationDeg * PI / 180.0
            val x = center.x + (radius * 0.4f * cos(angle)).toFloat()
            val y = center.y + (radius * 0.4f * sin(angle)).toFloat()
            drawCircle(colors.textPrimary, radius * 0.18f, Offset(x, y))
        }
    }

    val player = Offset(ox + state.playerX.toFloat() * scale, oy + state.playerY.toFloat() * scale)
    val radius = SnowballEngine.PLAYER_RADIUS.toFloat() * scale
    drawCircle(colors.textPrimary, radius, player)
    drawCircle(colors.background, radius * 0.72f, player)
    val angle = state.angleDeg * PI / 180.0
    for (i in 0 until 2) {
        val a = angle + i * PI / 2
        val x = player.x + (radius * 0.55f * cos(a)).toFloat()
        val y = player.y + (radius * 0.55f * sin(a)).toFloat()
        drawCircle(colors.textPrimary, radius * 0.14f, Offset(x, y))
    }
}

@Composable
private fun SnowBanner(label: String) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = 40.sp, color = colors.accentBright),
        )
    }
}

@Composable
private fun SnowOverlay(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>>) {
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
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = subtitle,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> SnowButton(label) { action() } }
            }
        }
    }
}

@Composable
private fun SnowButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(28.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .appTap(label = label) { onClick() }
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
private fun SnowMenu(
    saved: SnowState?,
    campaignSeen: Boolean,
    survivalSeen: Boolean,
    best: Int,
    message: String,
    onCampaign: () -> Unit,
    onSurvival: () -> Unit,
    onResume: () -> Unit,
    onScores: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "snowball") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (saved != null && saved.status == SnowStatus.PLAYING) {
                SnowButton("continue ${if (saved.mode == SnowMode.CAMPAIGN) "1-${saved.level}" else "survival"}", onResume)
                Spacer(Modifier.height(6.dp))
            }
            SnowButton("campaign", onCampaign)
            Spacer(Modifier.height(4.dp))
            SnowButton("survival", onSurvival)
            Spacer(Modifier.height(4.dp))
            SnowButton("high scores", onScores)
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            BasicText(
                text = "tilt or drag to roll. the ice fades under you and comes back.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
            if (campaignSeen && !survivalSeen) {
                BasicText(
                    text = "survival: collect stars, stay off the holes",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = message,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                )
            }
        }
    }
}

@Composable
private fun SnowLevelSelect(completed: Set<Int>, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    val unlocked = SnowballEngine.highestUnlocked(completed)
    DetailScaffold(title = "snowball · levels", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 5) {
                        val level = row * 5 + col + 1
                        val isCompleted = level in completed
                        val isUnlocked = level <= unlocked
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    when {
                                        isCompleted -> colors.tilePressed
                                        isUnlocked -> colors.tile
                                        else -> colors.elevated
                                    },
                                )
                                .border(0.5.dp, if (isCompleted) colors.accent else colors.border)
                                .appTap(label = "level $level", enabled = isUnlocked) { onPick(level) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = if (isUnlocked) level.toString() else "—",
                                style = TextStyle(
                                    fontFamily = Selawik,
                                    fontSize = DoradoTokens.TYPE_LIST.sp,
                                    color = if (isUnlocked) colors.textPrimary else colors.textInactive,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "five completed levels open the next five",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun SnowScores(scores: List<Pair<Int, String>>, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "snowball · scores", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            if (scores.isEmpty()) {
                BasicText(
                    text = "no runs yet",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                )
            }
            scores.forEachIndexed { index, (score, meta) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "${index + 1}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        modifier = Modifier.width(24.dp),
                    )
                    BasicText(
                        text = SnowballEngine.scoreLabel(score),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = meta,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textInactive),
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
