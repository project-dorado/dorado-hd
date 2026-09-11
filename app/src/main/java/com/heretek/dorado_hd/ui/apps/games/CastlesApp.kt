package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private const val CASTLES_SAVE_KEY = "castles-and-cannons"

@Composable
fun CastlesApp() {
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
    var progress by remember { mutableStateOf(CastlesProgress()) }
    var battle by remember { mutableStateOf<CastlesBattleState?>(null) }
    var camera by remember { mutableStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var recorded by remember { mutableStateOf(false) }
    var bossBannerMs by remember { mutableStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragCurrent by remember { mutableStateOf(Offset.Zero) }
    val history by graph.games.top(CASTLES_SAVE_KEY, 5).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        graph.appState.get(CASTLES_SAVE_KEY)?.let { CastlesEngine.decodeProgress(it) }?.let { progress = it }
        sound = graph.appState.get("$CASTLES_SAVE_KEY.sound") != "0"
        loaded = true
    }

    LaunchedEffect(progress, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put(CASTLES_SAVE_KEY, CastlesEngine.encodeProgress(progress))
    }

    LaunchedEffect(battle?.bossSpawned) {
        if (battle?.bossSpawned == true) {
            bossBannerMs = 3_000L
            play("explode")
        }
    }
    LaunchedEffect(bossBannerMs) {
        if (bossBannerMs > 0L) {
            delay(500)
            bossBannerMs = max(0L, bossBannerMs - 500)
        }
    }

    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = battle ?: break
            if (current.over) continue
            val next = CastlesEngine.step(current, 16)
            battle = next
            next.events.forEach { event ->
                when (event) {
                    CastlesEvent.BUY -> play("select")
                    CastlesEvent.INVALID -> play("error")
                    CastlesEvent.SPAWN -> play("click")
                    CastlesEvent.DEATH -> play("hit")
                    CastlesEvent.CANNON_FIRE -> play("explode")
                    CastlesEvent.CANNON_IMPACT -> play("hit")
                    CastlesEvent.ENEMY_CANNON -> play("whoosh")
                    CastlesEvent.COIN -> play("coin")
                    CastlesEvent.SNIPE -> play("tick")
                    CastlesEvent.HEAL -> play("score")
                    CastlesEvent.BOSS_ALERT -> play("lose")
                    CastlesEvent.CASTLE_HIT -> play("error")
                    CastlesEvent.WIN -> play("win")
                    CastlesEvent.LOSE -> play("lose")
                }
            }
        }
    }

    LaunchedEffect(battle?.over, battle?.won) {
        val current = battle ?: return@LaunchedEffect
        if (current.over && !recorded) {
            recorded = true
            if (current.won) {
                val result = CastlesEngine.battleScore(current)
                progress = CastlesEngine.recordCompletion(progress, current.level, result)
                scope.launch {
                    graph.games.record(CASTLES_SAVE_KEY, result, "level ${current.level} · ${if (result >= 1000) "gold" else "silver"}")
                }
            }
        }
    }

    fun startLevel(number: Int) {
        battle = CastlesEngine.newBattle(number, progress, System.currentTimeMillis().toInt())
        camera = 0f
        paused = false
        recorded = false
        bossBannerMs = 0L
        screen = "game"
    }

    fun applyBattle(transform: (CastlesBattleState) -> CastlesBattleState) {
        val snapshot = battle ?: return
        battle = transform(snapshot)
    }

    if (screen == "menu") {
        CastlesMenu(
            progress = progress,
            best = history.firstOrNull()?.score ?: 0,
            sound = sound,
            onSound = {
                sound = !sound
                scope.launch { graph.appState.put("$CASTLES_SAVE_KEY.sound", if (sound) "1" else "0") }
            },
            onPlay = { screen = "levels" },
            onSkills = { screen = "skills" },
        )
        return
    }
    if (screen == "levels") {
        CastlesLevelSelect(progress = progress, onPick = { startLevel(it) }, onBack = { screen = "menu" })
        return
    }
    if (screen == "skills") {
        CastlesSkillScreen(
            progress = progress,
            onUpgrade = { branch -> progress = CastlesEngine.skillUpgrade(progress, branch) },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = battle ?: return
    DetailScaffold(title = "castles and cannons", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .appDescription(
                            "level ${current.level}, gold ${current.gold}, you ${current.humanHp.toInt()}, enemy ${current.enemyHp.toInt()}",
                        ),
                ) {
                    BasicText(
                        text = "level ${current.level}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = "gold ${current.gold}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = "you %d".format(current.humanHp.toInt()),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = "enemy %d".format(current.enemyHp.toInt()),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        text = "%d:%02d".format(current.elapsedMs / 60_000, (current.elapsedMs / 1_000) % 60),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.width(8.dp))
                    EdgeText("pause", colors.textPrimary) { paused = true }
                }
                Spacer(Modifier.height(2.dp))
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val view = castlesView(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), camera)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(current.level, paused) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragging = offset.y > size.height * 0.55f
                                        dragStart = offset
                                        dragCurrent = offset
                                    },
                                    onDrag = { _, drag ->
                                        if (dragging) {
                                            dragCurrent += drag
                                        } else {
                                            val liveScale = castlesView(size.width.toFloat(), size.height.toFloat(), camera).scale
                                            camera = (camera - drag.x / liveScale).coerceIn(0f, CASTLES_WORLD_W - CASTLES_VIEW_W)
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragging) {
                                            val liveScale = castlesView(size.width.toFloat(), size.height.toFloat(), camera).scale
                                            val dx = (dragCurrent.x - dragStart.x) / liveScale
                                            val dy = (dragCurrent.y - dragStart.y) / liveScale
                                            applyBattle { CastlesEngine.fireCannon(it, dx, dy) }
                                        }
                                        dragging = false
                                    },
                                    onDragCancel = { dragging = false },
                                )
                            }
                            .pointerInput(current.level, paused) {
                                detectTapGestures { offset ->
                                    // Recompute the projection from the live camera:
                                    // the gesture block survives pans, so a captured
                                    // view would map the tap to the pre-pan world x.
                                    val worldX = CastlesEngine.screenToWorldX(
                                        screenX = offset.x,
                                        viewWidth = size.width.toFloat(),
                                        viewHeight = size.height.toFloat(),
                                        camera = camera,
                                    )
                                    applyBattle { CastlesEngine.collectCoins(it, worldX, 150f) }
                                }
                            }
                            .appDescription(
                                "castle battle, level ${current.level}, you ${current.humanHp.toInt()}, enemy ${current.enemyHp.toInt()}",
                            ),
                    ) {
                        drawCastlesBattle(current, view, colors)
                        if (dragging) {
                            val start = view.worldX(120f) to view.y(150f)
                            drawLine(
                                colors.accentBright,
                                Offset(start.first, start.second),
                                Offset(dragCurrent.x, dragCurrent.y),
                                strokeWidth = view.s(2f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                CastlesBuyBar(current) { kind -> applyBattle { CastlesEngine.buy(it, kind) } }
                CastlesHealBar(current) { applyBattle { CastlesEngine.heal(it) } }
            }

            if (bossBannerMs > 0L && !paused && !current.over) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "boss incoming",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accentBright),
                    )
                }
            }

            if (paused || current.over) {
                CastlesEndPanel(
                    current = current,
                    paused = paused,
                    onResume = { paused = false },
                    onRetry = { startLevel(current.level) },
                    onLevels = { battle = null; screen = "levels" },
                    onMenu = { battle = null; screen = "menu" },
                )
            }
        }
    }
}

@Composable
private fun CastlesBuyBar(current: CastlesBattleState, onBuy: (CastlesUnitKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CastlesUnitKind.entries.forEach { kind ->
            val unlocked = current.stats.availableUnits >= kind.slot
            val cooldown = current.buyCooldowns[kind] ?: 0L
            CastlesButton(
                label = "${kind.label} ${kind.cost}",
                enabled = unlocked && current.gold >= kind.cost && cooldown <= 0L,
            ) { onBuy(kind) }
        }
    }
}

@Composable
private fun CastlesHealBar(current: CastlesBattleState, onHeal: () -> Unit) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = "cannon " + if (current.cannonCooldownMs <= 0L) "ready" else "%ds".format((current.cannonCooldownMs / 1000) + 1),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = if (current.cannonCooldownMs <= 0L) colors.accent else colors.textInactive),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "drag a cannon to aim · drag the field to pan · tap coins",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.width(8.dp))
        CastlesButton(
            label = if (current.healCooldownMs > 0L) "heal %ds".format((current.healCooldownMs / 1000) + 1) else "heal ${CASTLES_HEAL_COST}",
            enabled = current.gold >= CASTLES_HEAL_COST && current.healCooldownMs <= 0L,
        ) { onHeal() }
    }
}

@Composable
private fun CastlesEndPanel(
    current: CastlesBattleState,
    paused: Boolean,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onLevels: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val parts = CastlesEngine.scoreParts(
        timeSeconds = current.elapsedMs / 1000f,
        kills = current.unitsKilled,
        fired = current.cannonsFired,
        goldAcquired = current.goldAcquired,
        hp = current.humanHp,
        maxHp = current.stats.maxHealth,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = when {
                    current.over && current.won -> "castle breached · victory"
                    current.over -> "your castle fell"
                    else -> "paused"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            if (current.over) {
                BasicText(
                    text = "time ${parts.time} · offense ${parts.offense} · defense ${parts.defense} · total ${parts.total}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                BasicText(
                    text = if (CastlesEngine.isGoldMedal(parts.total)) "gold medal" else "silver medal",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                )
            } else {
                BasicText(
                    text = "level ${current.level} · gold ${current.gold}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (paused && !current.over) {
                    CastlesButton("resume", true, Modifier.width(100.dp)) { onResume() }
                }
                if (current.over) {
                    CastlesButton("retry", true, Modifier.width(100.dp)) { onRetry() }
                }
                CastlesButton("levels", true, Modifier.width(100.dp)) { onLevels() }
                CastlesButton("menu", true, Modifier.width(100.dp)) { onMenu() }
            }
        }
    }
}

@Composable
private fun CastlesButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(if (enabled) colors.tile else colors.background)
            .border(0.5.dp, colors.border)
            .appTap(label = label, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}

@Composable
private fun CastlesMenu(
    progress: CastlesProgress,
    best: Int,
    sound: Boolean,
    onSound: () -> Unit,
    onPlay: () -> Unit,
    onSkills: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "castles and cannons") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CastlesButton("play", true, Modifier.width(240.dp)) { onPlay() }
            CastlesButton("skills (${progress.skills.unusedPoints} points)", true, Modifier.width(240.dp)) { onSkills() }
            CastlesButton("sound ${if (sound) "on" else "off"}", true, Modifier.width(240.dp)) { onSound() }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "Hold the line: buy armies, drag-aim the cannon, and topple the enemy castle. " +
                    "Best score $best · gold medals ${progress.goldMedals()}.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun CastlesLevelSelect(progress: CastlesProgress, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "castles · levels") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 4) {
                        val number = row * 4 + col + 1
                        val unlocked = number <= progress.unlocked
                        val score = progress.best(number)
                        val gold = score >= 1000
                        CastlesButton(
                            label = when {
                                !unlocked -> "$number locked"
                                gold -> "$number gold $score"
                                score > 0 -> "$number silver $score"
                                else -> "$number · $score"
                            },
                            enabled = unlocked,
                            modifier = Modifier.weight(1f),
                        ) { onPick(number) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            CastlesButton("back", true, Modifier.width(120.dp)) { onBack() }
        }
    }
}

@Composable
private fun CastlesSkillScreen(progress: CastlesProgress, onUpgrade: (Int) -> Unit, onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "castles · skills") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                text = "points ${progress.skills.unusedPoints} · each tier costs 1 / 2 / 3",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            val branches = listOf(
                "offense" to progress.skills.offense,
                "defense" to progress.skills.defense,
                "utility" to progress.skills.utility,
            )
            branches.forEachIndexed { index, (label, level) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        text = "$label $level/3",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                    CastlesButton("+${level + 1} point", level < 3 && progress.skills.unusedPoints > level, Modifier.width(110.dp)) { onUpgrade(index) }
                }
            }
            BasicText(
                text = "offense: unit damage, armor, army size · defense: cannon damage, cooldown, health · utility: snipe",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
            CastlesButton("back", true, Modifier.width(120.dp)) { onBack() }
        }
    }
}

private class CastlesView(val scale: Float, val ox: Float, val oy: Float, val camera: Float) {
    fun x(worldX: Float): Float = ox + (worldX - camera) * scale
    fun y(worldY: Float): Float = oy + worldY * scale
    fun s(v: Float): Float = v * scale
    fun worldX(worldX: Float): Float = x(worldX)
}

private fun castlesView(width: Float, height: Float, camera: Float): CastlesView {
    val scale = min(width / CASTLES_VIEW_W, height / CASTLES_VIEW_H)
    return CastlesView(scale, (width - CASTLES_VIEW_W * scale) / 2f, (height - CASTLES_VIEW_H * scale) / 2f, camera)
}

private fun DrawScope.drawCastlesBattle(state: CastlesBattleState, view: CastlesView, colors: DoradoColors) {
    drawRect(colors.background, Offset(view.x(0f), view.y(0f)), Size(view.s(CASTLES_WORLD_W), view.s(CASTLES_VIEW_H)))
    when (CastlesEngine.levelFor(state.level).backdrop) {
        CastlesBackdrop.HILLS -> {
            drawTriangleBackdrop(view, colors, 0.55f)
        }
        CastlesBackdrop.PLAINS -> {
            drawRect(colors.elevated, Offset(view.x(0f), view.y(120f)), Size(view.s(CASTLES_WORLD_W), view.s(46f)))
        }
        CastlesBackdrop.DESERT -> {
            drawRect(DoradoAccent.ORANGE.primary.copy(alpha = 0.25f), Offset(view.x(0f), view.y(120f)), Size(view.s(CASTLES_WORLD_W), view.s(46f)))
        }
        CastlesBackdrop.WATERFALL -> {
            drawRect(DoradoAccent.CYAN.primary.copy(alpha = 0.2f), Offset(view.x(400f), view.y(0f)), Size(view.s(28f), view.s(150f)))
        }
        CastlesBackdrop.MOUNTAIN -> {
            drawTriangleBackdrop(view, colors, 0.72f)
        }
        CastlesBackdrop.VOLCANO -> {
            drawTriangleBackdrop(view, colors, 0.72f)
            drawCircle(DoradoAccent.ORANGE.bright.copy(alpha = 0.5f), view.s(10f), Offset(view.x(700f), view.y(60f)))
        }
    }
    drawRect(colors.tilePressed, Offset(view.x(0f), view.y(CASTLES_GROUND_Y)), Size(view.s(CASTLES_WORLD_W), view.s(CASTLES_VIEW_H - CASTLES_GROUND_Y)))

    // Castles.
    drawRect(colors.tile, Offset(view.x(60f), view.y(80f)), Size(view.s(110f), view.s(115f)))
    drawRect(colors.border, Offset(view.x(60f), view.y(70f)), Size(view.s(110f), view.s(12f)))
    drawRect(DoradoAccent.CYAN.primary, Offset(view.x(80f), view.y(120f)), Size(view.s(70f), view.s(75f)))
    drawRect(colors.tile, Offset(view.x(790f), view.y(80f)), Size(view.s(110f), view.s(115f)))
    drawRect(colors.border, Offset(view.x(790f), view.y(70f)), Size(view.s(110f), view.s(12f)))
    drawRect(DoradoAccent.PINK.primary, Offset(view.x(810f), view.y(120f)), Size(view.s(70f), view.s(75f)))

    // Health bars.
    drawRect(Color.Black, Offset(view.x(60f), view.y(58f)), Size(view.s(110f), view.s(6f)))
    drawRect(colors.accentBright, Offset(view.x(60f), view.y(58f)), Size(view.s(110f * state.humanHp / state.stats.maxHealth), view.s(6f)))
    drawRect(Color.Black, Offset(view.x(790f), view.y(58f)), Size(view.s(110f), view.s(6f)))
    drawRect(DoradoAccent.PINK.bright, Offset(view.x(790f), view.y(58f)), Size(view.s(110f * state.enemyHp / state.enemyStats.maxHealth), view.s(6f)))

    // Cannons.
    for (cannonX in cannonPositions(state.stats.cannons)) {
        drawRect(colors.tilePressed, Offset(view.x(cannonX - 12f), view.y(138f)), Size(view.s(24f), view.s(20f)))
        drawLine(colors.textPrimary, Offset(view.x(cannonX), view.y(148f)), Offset(view.x(cannonX + 18f), view.y(128f)), strokeWidth = view.s(5f))
    }

    // Coins.
    for (coin in state.coins) {
        drawCircle(DoradoAccent.ORANGE.bright, view.s(6f), Offset(view.x(coin.x), view.y(170f)))
    }

    // Units.
    for (unit in state.units) {
        drawCastlesUnit(view, unit, colors)
    }

    // Balls.
    for (ball in state.balls) {
        drawCircle(colors.textPrimary, view.s(5f), Offset(view.x(ball.x), view.y(ball.y)))
    }
}

private fun cannonPositions(count: Int): List<Float> = if (count >= 2) listOf(110f, 160f) else listOf(130f)

private fun DrawScope.drawTriangleBackdrop(view: CastlesView, colors: DoradoColors, heightFraction: Float) {
    val baseY = view.y(CASTLES_GROUND_Y)
    for (i in 0 until 8) {
        val baseX = view.x(i * 130f)
        val peakX = view.x(i * 130f + 65f)
        val peakY = view.y(CASTLES_GROUND_Y - CASTLES_GROUND_Y * heightFraction)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(baseX, baseY)
            lineTo(peakX, peakY)
            lineTo(view.x(i * 130f + 130f), baseY)
            close()
        }
        drawPath(path, colors.elevated)
    }
}

private fun DrawScope.drawCastlesUnit(view: CastlesView, unit: CastlesUnit, colors: DoradoColors) {
    val cx = view.x(unit.x)
    val cy = view.y(170f + unit.lane * 6f)
    val size = view.s(if (unit.boss != null) 26f else 12f)
    val body = if (unit.side == CastlesSide.HUMAN) DoradoAccent.CYAN.bright else DoradoAccent.PINK.bright
    if (unit.boss != null) {
        drawCircle(body, size, Offset(cx, cy))
        drawRect(colors.accentBright, Offset(cx - size * 0.5f, cy - size * 1.4f), Size(size, size * 0.5f))
    } else {
        drawRect(body, Offset(cx - size * 0.4f, cy - size), Size(size * 0.8f, size))
        drawCircle(body, size * 0.35f, Offset(cx, cy - size * 1.1f))
        if (unit.kind == CastlesUnitKind.ARCHER) {
            drawLine(colors.textPrimary, Offset(cx + size * 0.4f, cy - size), Offset(cx + size * 0.4f, cy), strokeWidth = view.s(1.5f))
        }
        if (unit.kind == CastlesUnitKind.BOMBER) {
            drawCircle(Color.Black, size * 0.35f, Offset(cx, cy - size * 1.6f))
        }
        if (unit.kind == CastlesUnitKind.KNIGHT) {
            drawRect(colors.border, Offset(cx - size * 0.6f, cy - size * 0.6f), Size(size * 1.2f, size * 0.6f))
        }
    }
    if (unit.hp < CASTLES_UNIT_HP) {
        drawRect(Color.Black, Offset(cx - size, cy + size * 0.9f), Size(size * 2f, view.s(3f)))
        drawRect(body, Offset(cx - size, cy + size * 0.9f), Size(size * 2f * unit.hp / CASTLES_UNIT_HP, view.s(3f)))
    }
}
