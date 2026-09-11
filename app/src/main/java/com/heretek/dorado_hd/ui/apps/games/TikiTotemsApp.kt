package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.min

private const val TIKI_SLUG = "tiki-totems"

@Composable
fun TikiTotemsApp() {
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
    var progress by remember { mutableStateOf(TikiProgress()) }
    var packIndex by remember { mutableStateOf(0) }
    var levelIndex by remember { mutableStateOf(0) }
    var game by remember { mutableStateOf<TikiGame?>(null) }
    var paused by remember { mutableStateOf(false) }
    var skipConfirm by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val redrawTick = frame
    val best by graph.games.top(TIKI_SLUG, 1).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        progress = TikiEngine.decodeProgress(graph.appState.get(TIKI_SLUG))
        graph.appState.get("$TIKI_SLUG.last")?.split(':')?.let { bits ->
            if (bits.size == 2) {
                packIndex = (bits[0].toIntOrNull() ?: 0).coerceIn(0, TikiPacks.ALL.lastIndex)
                levelIndex = (bits[1].toIntOrNull() ?: 0).coerceIn(0, TikiPacks.LEVELS_PER_PACK - 1)
            }
        }
    }

    val running = screen == "game" && game?.status == TikiStatus.PLAYING && !paused
    LaunchedEffect(running, game) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
                    last = now
                    val current = game ?: return@withFrameNanos
                    TikiEngine.step(current, dt)
                    current.events.forEach { event ->
                        when (event) {
                            "break" -> bank.play("pop")
                            "boing" -> bank.play("toss")
                            "fuse" -> bank.play("tick")
                            "blast" -> bank.play("explode")
                            "win" -> bank.play("win")
                            "lose" -> bank.play("lose")
                        }
                    }
                    frame++
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.status) {
        val current = game ?: return@LaunchedEffect
        if (current.status == TikiStatus.PLAYING || recorded) return@LaunchedEffect
        recorded = true
        if (current.status == TikiStatus.WON) {
            val stars = TikiEngine.starsFor(current.elapsedMs)
            progress = progress.complete(packIndex, levelIndex, stars)
            graph.appState.put(TIKI_SLUG, TikiEngine.encodeProgress(progress))
            graph.games.record(
                TIKI_SLUG,
                progress.totalStars,
                "${TikiPacks.pack(packIndex).label} ${levelIndex + 1} · $stars stars",
            )
        }
    }

    LaunchedEffect(packIndex, levelIndex) {
        graph.appState.put("$TIKI_SLUG.last", "$packIndex:$levelIndex")
    }

    fun startLevel(pack: Int, level: Int) {
        packIndex = pack
        levelIndex = level
        game = TikiEngine.newGame(TikiPacks.level(pack, level))
        paused = false
        skipConfirm = false
        recorded = false
        screen = "game"
    }

    fun nextLevel() {
        val next = levelIndex + 1
        if (next < TikiPacks.LEVELS_PER_PACK) {
            startLevel(packIndex, next)
        } else {
            game = null
            screen = "levels"
        }
    }

    if (screen == "menu") {
        TikiMenu(
            progress = progress,
            best = best.firstOrNull()?.score ?: 0,
            onContinue = { startLevel(packIndex, levelIndex) },
            onPacks = { screen = "packs" },
        )
        return
    }

    if (screen == "packs") {
        TikiPacksScreen(
            progress = progress,
            onPick = { index ->
                packIndex = index
                levelIndex = 0
                screen = "levels"
            },
            onBack = { screen = "menu" },
        )
        return
    }

    if (screen == "levels") {
        TikiLevelsScreen(
            packIndex = packIndex,
            progress = progress,
            onPick = { level -> startLevel(packIndex, level) },
            onBack = { screen = "packs" },
        )
        return
    }

    val current = game ?: return
    val pack = TikiPacks.pack(packIndex)
    DetailScaffold(title = "tiki totems", onBack = { paused = true }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "${pack.label} ${levelIndex + 1}/${TikiPacks.LEVELS_PER_PACK}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "blocks ${current.toDestroyRemaining}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "stars ${progress.totalStars}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
                Spacer(Modifier.weight(1f))
                TikiButton(if (paused) "play" else "pause") { paused = !paused }
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val scale = min(maxWidth.value / TikiEngine.DESIGN_W, maxHeight.value / TikiEngine.DESIGN_H)
                Canvas(
                    Modifier
                        .size(
                            (TikiEngine.DESIGN_W * scale).dp,
                            (TikiEngine.DESIGN_H * scale).dp,
                        )
                        .pointerInput(current.status) {
                            detectTapGestures { offset ->
                                if (paused || current.status != TikiStatus.PLAYING) return@detectTapGestures
                                val point = tikiDesignPoint(size, offset)
                                when (TikiEngine.tap(current, point.x, point.y)) {
                                    TikiTapResult.REMOVED -> bank.play("pop")
                                    TikiTapResult.FUSE -> bank.play("tick")
                                    TikiTapResult.DENIED -> bank.play("error")
                                    else -> Unit
                                }
                            }
                        },
                ) {
                    drawTikiWorld(current, packIndex, colors)
                }
                if (paused) {
                    TikiOverlay(
                        "paused",
                        "resume" to { paused = false },
                        "restart" to { startLevel(packIndex, levelIndex) },
                        "skip level" to { skipConfirm = true },
                        "main menu" to { paused = false; game = null; screen = "menu" },
                    )
                } else if (skipConfirm) {
                    TikiOverlay(
                        "are you sure you want to skip this level?",
                        "skip it!" to {
                            skipConfirm = false
                            current.status = TikiStatus.SKIPPED
                            nextLevel()
                        },
                        "cancel" to { skipConfirm = false },
                    )
                } else if (current.status == TikiStatus.WON) {
                    val stars = TikiEngine.starsFor(current.elapsedMs)
                    TikiOverlay(
                        "totem appeased · ${"*".repeat(stars)}",
                        "continue" to { nextLevel() },
                        "replay" to { startLevel(packIndex, levelIndex) },
                        "main menu" to { game = null; screen = "menu" },
                    )
                } else if (current.status == TikiStatus.LOST) {
                    TikiOverlay(
                        tikiLoseMessage(current.loseReason),
                        "restart" to { startLevel(packIndex, levelIndex) },
                        "main menu" to { game = null; screen = "menu" },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TikiButton("restart", Modifier.weight(1f), enabled = !paused) { startLevel(packIndex, levelIndex) }
                TikiButton("levels", Modifier.weight(1f), enabled = !paused) { game = null; screen = "levels" }
                TikiButton("menu", Modifier.weight(1f), enabled = !paused) { game = null; screen = "menu" }
            }
        }
    }
}

@Composable
private fun TikiMenu(
    progress: TikiProgress,
    best: Int,
    onContinue: () -> Unit,
    onPacks: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "tiki totems") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            TikiButton("continue", Modifier.fillMaxWidth()) { onContinue() }
            Spacer(Modifier.height(5.dp))
            TikiButton("packs", Modifier.fillMaxWidth()) { onPacks() }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "stars ${progress.totalStars} · best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = "appease the tiki gods by carefully removing the unnecessary blocks that dim the might and glory of their beautiful totems.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun TikiPacksScreen(
    progress: TikiProgress,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "tiki totems · packs", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TikiPacks.ALL.forEachIndexed { index, pack ->
                val entry = progress.pack(index)
                val solved = entry.stars.size
                val label = "${pack.label} · $solved/${TikiPacks.LEVELS_PER_PACK}"
                TikiButton(label, Modifier.fillMaxWidth()) { onPick(index) }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "all packs are free · 13 packs · ${TikiPacks.ALL.size * TikiPacks.LEVELS_PER_PACK} levels",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun TikiLevelsScreen(
    packIndex: Int,
    progress: TikiProgress,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val pack = TikiPacks.pack(packIndex)
    val entry = progress.pack(packIndex)
    DetailScaffold(title = "tiki totems · ${pack.label}", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            pack.levels.forEach { level ->
                val unlocked = entry.isUnlocked(level.levelIndex)
                val stars = entry.stars(level.levelIndex)
                val label = "level ${level.levelIndex + 1} · " + when {
                    !unlocked -> "locked"
                    stars > 0 -> "*".repeat(stars)
                    else -> "blocks ${level.toDestroy}"
                }
                TikiButton(label, Modifier.fillMaxWidth(), enabled = unlocked) { onPick(level.levelIndex) }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

private fun tikiDesignPoint(size: IntSize, offset: Offset): P2Vec {
    if (size.width <= 0 || size.height <= 0) return P2Vec(0f, 0f)
    return P2Vec(
        offset.x / size.width * TikiEngine.DESIGN_W,
        offset.y / size.height * TikiEngine.DESIGN_H,
    )
}

private fun tikiLoseMessage(reason: TikiLoseReason): String = when (reason) {
    TikiLoseReason.LAVA -> "the totem touched the lava"
    TikiLoseReason.LOST -> "the totem left the screen"
    TikiLoseReason.BROKEN -> "the totem was broken"
    TikiLoseReason.NONE -> "the totem fell"
}

private fun DrawScope.drawTikiWorld(game: TikiGame, packIndex: Int, colors: DoradoColors) {
    val scale = size.width / TikiEngine.DESIGN_W
    drawRect(colors.background)

    drawCloud(44f * scale, 64f * scale, 24f * scale, colors.elevated)
    drawCloud(206f * scale, 108f * scale, 32f * scale, colors.elevated)
    drawCloud(126f * scale, 34f * scale, 20f * scale, colors.elevated)

    val lavaY = TikiEngine.LAVA_Y * scale
    if (lavaY < size.height) {
        drawRect(
            DoradoAccent.ORANGE.primary.copy(alpha = 0.35f),
            Offset(0f, lavaY),
            Size(size.width, size.height - lavaY),
        )
        drawRect(DoradoAccent.PINK.primary.copy(alpha = 0.7f), Offset(0f, lavaY), Size(size.width, 2f))
    }

    for (block in game.blocks.values) {
        if (!block.alive) continue
        val body = block.body
        val center = Offset(body.x * scale, body.y * scale)
        rotate(degrees = body.angle * 180f / PI.toFloat(), pivot = center) {
            drawRect(
                tikiBlockColor(block, packIndex, colors),
                Offset((body.x - body.halfW) * scale, (body.y - body.halfH) * scale),
                Size(body.halfW * 2f * scale, body.halfH * 2f * scale),
            )
            drawRect(
                colors.background.copy(alpha = 0.4f),
                Offset((body.x - body.halfW) * scale, (body.y - body.halfH) * scale),
                Size(body.halfW * 2f * scale, body.halfH * 2f * scale),
                style = Stroke(width = 1f),
            )
        }
        if (block.def.type.explosive) {
            drawCircle(DoradoAccent.PINK.bright, 3.5f * scale, center)
        }
        if (block.def.type.timed && block.fuseMs >= 0L) {
            val blink = (block.fuseMs / 150L) % 2L == 0L
            if (blink) {
                drawCircle(
                    DoradoAccent.PINK.bright,
                    4f * scale,
                    Offset(body.x * scale, (body.y - body.halfH - 6f) * scale),
                )
            }
        }
    }
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, radius: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.55f), radius, Offset(cx, cy))
    drawCircle(color.copy(alpha = 0.45f), radius * 0.75f, Offset(cx - radius * 0.85f, cy + radius * 0.2f))
    drawCircle(color.copy(alpha = 0.45f), radius * 0.65f, Offset(cx + radius * 0.9f, cy + radius * 0.25f))
}

private fun tikiBlockColor(block: TikiBlock, packIndex: Int, colors: DoradoColors): Color =
    when (block.def.type) {
        TikiBlockType.BLOCK_TOTEM -> when (packIndex % 3) {
            0 -> colors.textSecondary
            1 -> colors.accent
            else -> colors.accentBright
        }
        TikiBlockType.BLOCK_NORMAL -> colors.tilePressed
        TikiBlockType.BLOCK_INDESTRUCT -> colors.border
        TikiBlockType.BLOCK_ELASTIC -> DoradoAccent.LIME.primary
        TikiBlockType.BLOCK_ELASTIC_INDESTRUCT -> DoradoAccent.LIME.primary.copy(alpha = 0.5f)
        TikiBlockType.BLOCK_VANISH -> DoradoAccent.PURPLE.primary
        TikiBlockType.BLOCK_EXPLOSIVE_VANISH -> DoradoAccent.PINK.primary
        TikiBlockType.BLOCK_EXPLOSIVE_TIMER -> DoradoAccent.ORANGE.primary
    }

@Composable
private fun TikiOverlay(title: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            Spacer(Modifier.height(6.dp))
            actions.forEach { (label, action) ->
                TikiButton(label) { action() }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun TikiButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(if (enabled) colors.tile else colors.elevated)
            .border(0.5.dp, colors.border)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}
