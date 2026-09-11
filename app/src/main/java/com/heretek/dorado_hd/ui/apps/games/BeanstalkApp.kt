package com.heretek.dorado_hd.ui.apps.games

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
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.min
import kotlin.math.sin

private const val BEANSTALK_SLUG = "a-beanstalk-tale"

@Composable
fun BeanstalkApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    val tilt = rememberTilt()

    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var cups by remember { mutableStateOf(BeanstalkEngine.Cups()) }
    var run by remember { mutableStateOf<BeanstalkEngine.State?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var tap by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val scores by graph.games.top(BEANSTALK_SLUG, 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        cups = BeanstalkEngine.decodeCups(graph.appState.get(BEANSTALK_SLUG))
    }

    LaunchedEffect(cups.banners) {
        if (cups.banners.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        cups = BeanstalkEngine.advanceBanners(cups, 200)
    }

    val active = screen == "game" && run != null && !paused && run?.phase != BeanstalkEngine.Phase.GAME_OVER
    LaunchedEffect(active, tilt.value.available) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 40L)
                    last = now
                    val current = run ?: return@withFrameNanos
                    val steer = if (tilt.value.available) (-tilt.value.pitchDeg / 30f).coerceIn(-1f, 1f) else 0f
                    val next = BeanstalkEngine.step(
                        current,
                        BeanstalkEngine.Input(tilt = steer, tapScreenX = tap?.first, tapScreenY = tap?.second),
                        dt,
                    )
                    tap = null
                    next.events.forEach { event -> beanstalkSfx(event)?.let(bank::play) }
                    run = next
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(run?.phase) {
        val current = run ?: return@LaunchedEffect
        if (current.phase == BeanstalkEngine.Phase.GAME_OVER && !recorded) {
            recorded = true
            cups = BeanstalkEngine.mergeRun(cups, current.stats, current.maxHeight)
            graph.appState.put(BEANSTALK_SLUG, BeanstalkEngine.encodeCups(cups))
            graph.games.record(BEANSTALK_SLUG, current.score, "height ${current.maxHeight}")
            bank.play("lose")
        }
    }

    fun startRun() {
        run = BeanstalkEngine.newRun(seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt())
        tap = null
        paused = false
        recorded = false
        screen = "game"
    }

    if (screen == "menu") {
        BeanstalkMenu(
            cups = cups,
            best = scores.firstOrNull()?.score ?: 0,
            onPlay = { startRun() },
            onCups = { screen = "cups" },
            onHelp = { screen = "help" },
        )
        return
    }
    if (screen == "cups") {
        BeanstalkCupsScreen(cups = cups, onBack = { screen = "menu" })
        return
    }
    if (screen == "help") {
        BeanstalkHelp(onBack = { screen = "menu" })
        return
    }

    val current = run ?: return
    DetailScaffold(title = "a beanstalk tale", onBack = { paused = true }) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BeanstalkHud(current = current, banner = cups.banners.firstOrNull())
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val scale = min(maxHeight.value / BeanstalkEngine.VIEW_H, maxWidth.value / BeanstalkEngine.WORLD_W)
                val canvasWidth = (BeanstalkEngine.WORLD_W * scale).dp
                val canvasHeight = (BeanstalkEngine.VIEW_H * scale).dp
                Canvas(
                    Modifier
                        .size(canvasWidth, canvasHeight)
                        .pointerInput(active) {
                            detectTapGestures { offset ->
                                if (!active) return@detectTapGestures
                                val worldX = offset.x / size.width * BeanstalkEngine.WORLD_W
                                val worldY = offset.y / size.height * BeanstalkEngine.VIEW_H
                                tap = worldX to worldY
                            }
                        }
                        .appDescription(
                            "beanstalk world, score ${current.score}, height ${current.maxHeight}, boss ${current.bossesSpawned}",
                        ),
                ) {
                    drawBeanstalkWorld(current, colors)
                }
                if (paused) {
                    BeanstalkOverlay(
                        title = "paused",
                        actions = listOf(
                            "resume" to { paused = false },
                            "retry" to { startRun() },
                            "main menu" to { paused = false; run = null; screen = "menu" },
                        ),
                    )
                } else if (current.phase == BeanstalkEngine.Phase.GAME_OVER) {
                    BeanstalkOverlay(
                        title = "game over",
                        subtitle = "score ${current.score} · height ${current.maxHeight} · bonus ${current.bonus}",
                        actions = listOf(
                            "play again" to { startRun() },
                            "main menu" to { run = null; screen = "menu" },
                        ),
                    )
                } else if (current.phase == BeanstalkEngine.Phase.COUNTDOWN) {
                    BeanstalkOverlay(
                        title = current.message.ifEmpty { "3" },
                        subtitle = "tilt to steer · tap to leap or loose an arrow",
                        actions = emptyList(),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BeanstalkButton("pause", Modifier.weight(1f)) { paused = true }
                BeanstalkButton("cups", Modifier.weight(1f)) { paused = false; run = null; screen = "cups" }
                BeanstalkButton("menu", Modifier.weight(1f)) { paused = false; run = null; screen = "menu" }
            }
        }
    }
}

private fun beanstalkSfx(event: BeanstalkEngine.Event): String? = when (event) {
    BeanstalkEngine.Event.JUMP -> "jump"
    BeanstalkEngine.Event.BOW -> "whoosh"
    BeanstalkEngine.Event.LEAF_BREAK -> "hit"
    BeanstalkEngine.Event.FOOD -> "coin"
    BeanstalkEngine.Event.SHIELD -> "select"
    BeanstalkEngine.Event.BALLOON, BeanstalkEngine.Event.BALLOON_POP -> "pop"
    BeanstalkEngine.Event.ARROW_HIT, BeanstalkEngine.Event.ENEMY_HIT, BeanstalkEngine.Event.HIT -> "hit"
    BeanstalkEngine.Event.JUMP_KILL -> "score"
    BeanstalkEngine.Event.BOSS_ARRIVE -> "explode"
    BeanstalkEngine.Event.BOSS_RETREAT -> "win"
    BeanstalkEngine.Event.GAME_OVER -> "lose"
    BeanstalkEngine.Event.START -> "select"
    BeanstalkEngine.Event.COUNTDOWN -> null
}

@Composable
private fun BeanstalkHud(current: BeanstalkEngine.State, banner: BeanstalkEngine.Banner?) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .appDescription("score ${current.score}, height ${current.maxHeight}, bonus ${current.bonus}, boss ${current.bossesSpawned}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "score ${current.score}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = "height ${current.maxHeight}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = "bonus ${current.bonus}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.weight(1f))
        if (banner != null) {
            BasicText(
                text = "cup! ${banner.counter.material.label} ${banner.tier}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
            )
        } else {
            BasicText(
                text = "boss ${current.bossesSpawned}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

private fun DrawScope.drawBeanstalkWorld(state: BeanstalkEngine.State, colors: DoradoColors) {
    val w = size.width
    val h = size.height
    val camera = state.cameraTop
    val skyTop = when {
        state.maxHeight > 4_000 -> colors.tile
        state.maxHeight > 1_500 -> colors.elevated
        else -> colors.background
    }
    drawRect(colors.background)
    drawRect(skyTop, Offset(0f, 0f), Size(w, h * 0.55f))
    val parallax = (camera * 0.35f)
    for (i in 0 until 5) {
        val y = ((i * 220f - parallax) % (h + 220f) + h + 220f) % (h + 220f) - 110f
        drawRect(colors.border.copy(alpha = 0.35f), Offset(0f, y), Size(w, 1.5f))
    }

    val stalkX = w / 2f
    drawRect(colors.border, Offset(stalkX - 12f, 0f), Size(24f, h))
    drawRect(colors.tile, Offset(stalkX - 8f, 0f), Size(16f, h))
    val segment = ((camera % 40f) + 40f) % 40f
    var y = -segment
    while (y < h) {
        drawRect(colors.elevated, Offset(stalkX - 16f, y + 10f), Size(32f, 8f))
        y += 40f
    }

    if (camera > -2f) {
        drawRect(DoradoAccent.ORANGE.primary, Offset(0f, BeanstalkEngine.GROUND_Y - camera), Size(w, h))
        drawRect(DoradoAccent.LIME.primary, Offset(0f, BeanstalkEngine.GROUND_Y - camera), Size(w, 6f))
    }

    for (leaf in state.leaves) {
        val sy = leaf.y - camera
        if (sy < -30f || sy > h + 30f) continue
        val left = leaf.x - BeanstalkEngine.LEAF_WIDTH / 2f
        val top = sy
        val leafColor = when (leaf.kind) {
            BeanstalkEngine.LeafKind.DRY -> if (leaf.broken) colors.elevated else DoradoAccent.ORANGE.primary
            BeanstalkEngine.LeafKind.JUMPPAD -> DoradoAccent.CYAN.primary
            BeanstalkEngine.LeafKind.BOSS_GROUND -> DoradoAccent.PURPLE.primary
            BeanstalkEngine.LeafKind.MOVER_H, BeanstalkEngine.LeafKind.MOVER_V, BeanstalkEngine.LeafKind.MOVER_CIRCLE -> DoradoAccent.LIME.bright
            BeanstalkEngine.LeafKind.NORMAL -> DoradoAccent.LIME.primary
        }
        drawRect(leafColor, Offset(left, top), Size(BeanstalkEngine.LEAF_WIDTH, 10f))
        if (leaf.kind == BeanstalkEngine.LeafKind.JUMPPAD) {
            drawRect(colors.background, Offset(leaf.x - 8f, top - 6f), Size(16f, 4f))
        }
        if (leaf.kind == BeanstalkEngine.LeafKind.DRY && !leaf.broken) {
            drawRect(colors.background.copy(alpha = 0.7f), Offset(leaf.x - 6f, top + 2f), Size(12f, 1.5f))
        }
        if (leaf.kind == BeanstalkEngine.LeafKind.MOVER_H || leaf.kind == BeanstalkEngine.LeafKind.MOVER_V ||
            leaf.kind == BeanstalkEngine.LeafKind.MOVER_CIRCLE
        ) {
            drawRect(colors.textPrimary, Offset(left, top), Size(BeanstalkEngine.LEAF_WIDTH, 10f), style = Stroke(1f))
        }
        val pickup = leaf.pickup
        if (pickup != null && !leaf.pickupTaken) {
            val center = Offset(leaf.x, top - 10f)
            when (pickup) {
                BeanstalkEngine.PickupKind.FOOD -> {
                    drawCircle(DoradoAccent.ORANGE.bright, 6f, center)
                    drawCircle(colors.background, 2f, center)
                }
                BeanstalkEngine.PickupKind.SHIELD -> drawCircle(DoradoAccent.CYAN.bright, 7f, center, style = Stroke(2f))
                BeanstalkEngine.PickupKind.BALLOON -> {
                    drawCircle(DoradoAccent.PINK.primary, 7f, center)
                    drawRect(colors.textPrimary, Offset(center.x - 0.5f, center.y + 6f), Size(1f, 6f))
                }
            }
        }
    }

    for (enemy in state.enemies) {
        val sy = enemy.y - camera
        if (sy < -20f || sy > h + 20f) continue
        val enemyColor = when (enemy.kind) {
            BeanstalkEngine.EnemyKind.WASP -> DoradoAccent.ORANGE.primary
            BeanstalkEngine.EnemyKind.SPIDER -> DoradoAccent.PURPLE.primary
            BeanstalkEngine.EnemyKind.BUG -> DoradoAccent.LIME.primary
            BeanstalkEngine.EnemyKind.TREANT -> DoradoAccent.LIME.bright
            BeanstalkEngine.EnemyKind.GAZER -> DoradoAccent.CYAN.primary
            BeanstalkEngine.EnemyKind.MUSHROOM -> DoradoAccent.PINK.primary
            BeanstalkEngine.EnemyKind.SWARM -> DoradoAccent.ORANGE.bright
        }
        val bob = sin(enemy.phase / 240.0).toFloat() * 2f
        drawCircle(enemyColor, 8f, Offset(enemy.x, sy + bob))
        drawRect(colors.background, Offset(enemy.x - 5f, sy + bob - 1f), Size(10f, 2f))
    }

    state.boss?.let { boss ->
        val sy = boss.y - camera
        drawRect(DoradoAccent.PURPLE.primary, Offset(boss.x - 20f, sy - 18f), Size(40f, 36f))
        drawRect(DoradoAccent.PURPLE.bright, Offset(boss.x - 20f, sy - 18f), Size(40f, 36f), style = Stroke(2f))
        val hpWidth = 40f * boss.hp / BeanstalkEngine.BOSS_HP
        drawRect(colors.textPrimary, Offset(boss.x - 20f, sy - 24f), Size(hpWidth, 3f))
    }

    for (arrow in state.arrows) {
        val sy = arrow.y - camera
        drawLine(colors.textPrimary, Offset(arrow.x, sy), Offset(arrow.x - arrow.vx * 12f, sy - arrow.vy * 12f), 1.5f)
    }

    for (particle in state.particles) {
        val tint = when (particle.tint) {
            0 -> DoradoAccent.PINK.primary
            1 -> DoradoAccent.CYAN.primary
            else -> DoradoAccent.ORANGE.primary
        }
        drawCircle(tint, 3f, Offset(particle.x, particle.y - camera))
    }

    val pigY = state.pig.y - camera
    val pigColor = if (state.shieldMs > 0L && (state.shieldMs / BeanstalkEngine.SHIELD_BLINK_MS) % 2L == 0L) {
        colors.textInactive
    } else {
        DoradoAccent.PINK.primary
    }
    drawRect(pigColor, Offset(state.pig.x - BeanstalkEngine.PIG_HALF_W, pigY - BeanstalkEngine.PIG_HALF_H), Size(BeanstalkEngine.PIG_HALF_W * 2, BeanstalkEngine.PIG_HALF_H * 2))
    drawRect(colors.background, Offset(state.pig.x - 3f, pigY - 6f), Size(2f, 2f))
    drawRect(colors.background, Offset(state.pig.x + 1f, pigY - 6f), Size(2f, 2f))
    if (state.pig.riding) {
        drawCircle(DoradoAccent.CYAN.bright, 10f, Offset(state.pig.x, pigY - 22f), style = Stroke(2f))
    }
}

@Composable
private fun BeanstalkOverlay(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>>) {
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
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEach { (label, action) -> BeanstalkButton(label) { action() } }
                }
            }
        }
    }
}

@Composable
private fun BeanstalkMenu(
    cups: BeanstalkEngine.Cups,
    best: Int,
    onPlay: () -> Unit,
    onCups: () -> Unit,
    onHelp: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "a beanstalk tale") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BeanstalkButton("play", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            BeanstalkButton("cup cabinet", Modifier.fillMaxWidth()) { onCups() }
            Spacer(Modifier.height(4.dp))
            BeanstalkButton("how to play", Modifier.fillMaxWidth()) { onHelp() }
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = "best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            val earned = BeanstalkEngine.CupCounter.values().sumOf { cups.tiers[it] ?: 0 }
            BasicText(
                text = "cups $earned/${BeanstalkEngine.CupCounter.values().size * 5}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun BeanstalkCupsScreen(cups: BeanstalkEngine.Cups, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "a beanstalk tale · cups", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState())) {
            BeanstalkEngine.CupCounter.values().forEach { counter ->
                val tier = cups.tiers[counter] ?: 0
                val value = cups.values[counter] ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = counter.material.label,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (tier > 0) colors.accent else colors.textInactive),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "${counter.label} · tier $tier/5 · $value",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun BeanstalkHelp(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "a beanstalk tale · help", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp).verticalScroll(rememberScrollState())) {
            listOf(
                "tilt the device to steer the pig left and right.",
                "tap while standing to leap; tap again in the air (above the lower third) to loose an arrow.",
                "arrows travel left, up or right toward your touch and kill most enemies.",
                "food adds 500 plus 50 per pattern; shields last ten seconds; balloons lift you for five.",
                "dry leaves crumble, jumppads throw you high, movers carry you sideways.",
                "specials appear every 1500 px; a boss guards the stalk every 8000 px.",
                "fall too far below the camera and the run ends.",
                "the cropped header pauses the climb.",
            ).forEach {
                BasicText(
                    text = it,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BeanstalkButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
