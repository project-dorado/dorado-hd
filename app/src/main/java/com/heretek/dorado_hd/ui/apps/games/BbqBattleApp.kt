package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sin

private const val BBQ_SAVE_KEY = "bbq-battle"
private const val BBQ_VIEW_W = 480f
private const val BBQ_VIEW_H = 272f

private data class BbqPopup(val cell: BbqPoint, val tower: BbqTower?)

@Composable
fun BbqBattleApp() {
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
    var game by remember { mutableStateOf<BbqBattleState?>(null) }
    val latestGame = rememberUpdatedState(game)
    var saved by remember { mutableStateOf<BbqBattleState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<BbqPopup?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var sound by remember { mutableStateOf(true) }
    var easy by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    val bestFlow by graph.games.top(BBQ_SAVE_KEY, 5).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        saved = graph.appState.get(BBQ_SAVE_KEY)?.let { BbqBattleEngine.decode(it) }
        sound = graph.appState.get("$BBQ_SAVE_KEY.sound") != "0"
        easy = graph.appState.get("$BBQ_SAVE_KEY.easy") == "1"
    }

    // Periodic autosave: ticking state changes every frame, so a keyed debounce
    // would never settle. Snapshot on a fixed cadence plus explicit writes on
    // pause/exit (persistSnapshot).
    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(2_000)
            val current = game ?: continue
            if (!current.over) graph.appState.put(BBQ_SAVE_KEY, BbqBattleEngine.encode(current))
        }
    }

    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = game ?: break
            if (current.over || current.milestone != null) continue
            val next = BbqBattleEngine.step(current, (16 * speed).toLong())
            game = next
            next.events.forEach { event ->
                when (event) {
                    BbqEvent.PLACE -> play("click")
                    BbqEvent.UPGRADE -> play("score")
                    BbqEvent.SELL -> play("coin")
                    BbqEvent.INVALID -> play("error")
                    BbqEvent.SPRAY_SHOT -> play("tick")
                    BbqEvent.GUM_PULSE -> play("pop")
                    BbqEvent.BOMB_BLAST -> play("explode")
                    BbqEvent.ZAPPER_SHOT -> play("hit")
                    BbqEvent.DEATH_ANT, BbqEvent.DEATH_SNAIL, BbqEvent.DEATH_CENTIPEDE -> play("pop")
                    BbqEvent.DEATH_BEE -> play("explode")
                    BbqEvent.BITE -> play("error")
                    BbqEvent.WAVE_START -> play("whoosh")
                    BbqEvent.INTEREST -> play("coin")
                    BbqEvent.MILESTONE -> play("win")
                    BbqEvent.WAVE_END -> Unit
                    BbqEvent.GAME_OVER -> play("lose")
                }
            }
            if (next.over && !recorded) {
                recorded = true
                graph.appState.clear(BBQ_SAVE_KEY)
                scope.launch {
                    graph.games.record(BBQ_SAVE_KEY, next.completedWaves, "wave ${next.completedWaves} · ${next.killed} kills")
                }
            }
        }
    }

    fun startNew() {
        val fresh = BbqBattleEngine.newGame(System.currentTimeMillis().toInt(), easyMode = easy)
        game = fresh
        saved = null
        recorded = false
        paused = false
        popup = null
        speed = 1f
        screen = "game"
        scope.launch { graph.appState.put(BBQ_SAVE_KEY, BbqBattleEngine.encode(fresh)) }
    }

    fun applyGame(transform: (BbqBattleState) -> BbqBattleState) {
        val snapshot = game ?: return
        game = transform(snapshot)
    }

    fun persistSnapshot() {
        val current = game ?: return
        if (!current.over) scope.launch { graph.appState.put(BBQ_SAVE_KEY, BbqBattleEngine.encode(current)) }
    }

    fun resume() {
        val resumeGame = saved ?: return
        game = resumeGame
        recorded = false
        paused = false
        popup = null
        screen = "game"
    }

    if (screen == "menu") {
        BbqMenu(
            saved = saved,
            best = bestFlow.firstOrNull()?.score ?: 0,
            sound = sound,
            easy = easy,
            onSound = {
                sound = !sound
                scope.launch { graph.appState.put("$BBQ_SAVE_KEY.sound", if (sound) "1" else "0") }
            },
            onEasy = {
                easy = !easy
                scope.launch { graph.appState.put("$BBQ_SAVE_KEY.easy", if (easy) "1" else "0") }
            },
            onResume = { resume() },
            onNew = { startNew() },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "bbq battle", onBack = { persistSnapshot(); paused = true }) {
        Box(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Column(Modifier.fillMaxSize()) {
                BbqHud(current, speed, bestFlow.firstOrNull()?.score ?: 0) { persistSnapshot(); paused = true }
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(BbqBattleEngine.runToken(current), paused) {
                                detectTapGestures { offset ->
                                    if (paused) return@detectTapGestures
                                    val snapshot = latestGame.value ?: return@detectTapGestures
                                    val liveView = bbqView(size.width.toFloat(), size.height.toFloat())
                                    val (wx, wy) = liveView.toWorld(offset.x, offset.y)
                                    val cellX = ((wx - BBQ_GRID_X) / BBQ_CELL).toInt()
                                    val cellY = ((wy - BBQ_GRID_Y) / BBQ_CELL).toInt()
                                    if (!BbqBattleEngine.inGrid(cellX, cellY)) {
                                        popup = null
                                        return@detectTapGestures
                                    }
                                    val tower = snapshot.towers.firstOrNull { it.x == cellX && it.y == cellY }
                                    popup = BbqPopup(BbqPoint(cellX, cellY), tower)
                                }
                            }
                            .appDescription(
                                "bbq grid, $BBQ_GRID_W columns by $BBQ_GRID_H rows, food ${current.foodHp}, wave ${current.completedWaves + 1}",
                            ),
                    ) {
                        val view = bbqView(size.width, size.height)
                        drawBbqBoard(current, view, colors)
                    }
                }
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = when {
                        current.preview.isNotEmpty() -> "next: " + current.preview.joinToString(", ") { it.label }
                        else -> "tap a cell to build · sell refunds half"
                    },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
            }

            popup?.let { open ->
                BbqPopupPanel(
                    popup = open,
                    current = current,
                    onClose = { popup = null },
                    onBuild = { type ->
                        applyGame { BbqBattleEngine.build(it, open.cell.x, open.cell.y, type) }
                        popup = null
                    },
                    onUpgrade = {
                        applyGame { BbqBattleEngine.upgrade(it, open.cell.x, open.cell.y) }
                        popup = null
                    },
                    onSell = {
                        applyGame { BbqBattleEngine.sell(it, open.cell.x, open.cell.y) }
                        popup = null
                    },
                )
            }

            BbqSpeedSlider(
                speed = speed,
                onSpeed = { speed = it },
                modifier = Modifier.align(Alignment.CenterEnd).width(24.dp).fillMaxHeight(),
            )

            if (current.milestone != null) {
                BbqMilestone(milestone = current.milestone!!) { option ->
                    applyGame { BbqBattleEngine.chooseBonus(it, option) }
                }
            }

            if (paused || current.over) {
                BbqPausePanel(
                    current = current,
                    onResume = { paused = false },
                    onNew = { startNew() },
                    onMenu = { persistSnapshot(); screen = "menu"; game = null },
                )
            }
        }
    }
}

@Composable
private fun BbqHud(current: BbqBattleState, speed: Float, best: Int, onPause: () -> Unit) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .appDescription(
                "food ${current.foodHp}, credits ${current.credits}, wave ${current.completedWaves + 1} of ${BbqBattleEngine.WIN_WAVES}, speed $speed",
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "food ${current.foodHp}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = if (current.foodHp < 30) colors.accentBright else colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = "credits ${current.credits}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = "wave ${current.completedWaves + 1}/${BbqBattleEngine.WIN_WAVES}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = "kills ${current.killed}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = "best $best",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "x%.1f".format(speed),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(8.dp))
        EdgeText("pause", colors.textPrimary, onPause)
    }
}

@Composable
private fun BbqPopupPanel(
    popup: BbqPopup,
    current: BbqBattleState,
    onClose: () -> Unit,
    onBuild: (BbqTowerType) -> Unit,
    onUpgrade: () -> Unit,
    onSell: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val align = if (popup.cell.x < 5) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        Column(
            Modifier
                .width(150.dp)
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(
                text = if (popup.tower == null) "build (${popup.cell.x},${popup.cell.y})" else "tower",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            val tower = popup.tower
            if (tower == null) {
                BbqTowerType.entries.forEach { type ->
                    val cost = BbqBattleEngine.buildCost(type, current.bonuses)
                    BbqPanelButton(
                        label = "${type.label} $cost",
                        enabled = current.credits >= cost,
                    ) { onBuild(type) }
                }
            } else {
                val stats = tower.type.stats(tower.level)
                BasicText(
                    text = "${tower.type.label} lv${tower.level} · r${stats.radius.toInt()} · d%.1f".format(stats.damage),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
                )
                val cost = BbqBattleEngine.upgradeCost(tower, current.bonuses)
                BbqPanelButton(
                    label = if (cost == null) "max level" else "upgrade $cost",
                    enabled = cost != null && current.credits >= cost,
                ) { onUpgrade() }
                BbqPanelButton(
                    label = "sell ${BbqBattleEngine.sellValue(tower)}",
                    enabled = true,
                ) { onSell() }
            }
            BbqPanelButton(label = "close", enabled = true) { onClose() }
        }
    }
}

@Composable
private fun BbqMilestone(milestone: Int, onPick: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    val labels = when (milestone) {
        10 -> listOf("damage +10%", "build cost −1")
        20 -> listOf("cooldown −10%", "interest +10%")
        else -> listOf("radius +7.5%", "upgrade cost −1")
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                text = "wave $milestone cleared · choose a bonus",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    BbqPanelButton(label = label, enabled = true, width = 150.dp) { onPick(index) }
                }
            }
        }
    }
}

@Composable
private fun BbqPausePanel(
    current: BbqBattleState,
    onResume: () -> Unit,
    onNew: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                text = BbqBattleEngine.outcomeLabel(current),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            BasicText(
                text = "waves ${current.completedWaves} · kills ${current.killed} · %d:%02d".format(current.elapsedMs / 60_000, (current.elapsedMs / 1_000) % 60),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!current.over) BbqPanelButton("resume", true, 110.dp) { onResume() }
                BbqPanelButton("new game", true, 110.dp) { onNew() }
                BbqPanelButton("main menu", true, 110.dp) { onMenu() }
            }
        }
    }
}

@Composable
private fun BbqPanelButton(
    label: String,
    enabled: Boolean,
    width: androidx.compose.ui.unit.Dp = 140.dp,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .width(width)
            .height(26.dp)
            .background(if (enabled) colors.tile else colors.background)
            .border(0.5.dp, colors.border)
            .appTap(label = label, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = if (enabled) colors.textPrimary else colors.textInactive,
            ),
        )
    }
}

@Composable
private fun BbqMenu(
    saved: BbqBattleState?,
    best: Int,
    sound: Boolean,
    easy: Boolean,
    onSound: () -> Unit,
    onEasy: () -> Unit,
    onResume: () -> Unit,
    onNew: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "bbq battle") {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (saved != null) {
                BbqPanelButton("continue wave ${saved.completedWaves + 1}", true, 260.dp) { onResume() }
            }
            BbqPanelButton("new game", true, 260.dp) { onNew() }
            BbqPanelButton("difficulty ${if (easy) "easy" else "normal"}", true, 260.dp) { onEasy() }
            BbqPanelButton("sound ${if (sound) "on" else "off"}", true, 260.dp) { onSound() }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "Ants march to your picnic food. Build towers, bank interest, " +
                    "survive 30 waves. Best wave: $best.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun BbqSpeedSlider(speed: Float, onSpeed: (Float) -> Unit, modifier: Modifier) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .background(colors.elevated)
            .border(0.5.dp, colors.border)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    onSpeed(1f + fraction * 2f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onSpeed(1f + fraction * 2f)
                }
            }
            .appDescription("speed slider, $speed times"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackX = size.width / 2f
            drawLine(colors.border, Offset(trackX, size.height * 0.1f), Offset(trackX, size.height * 0.9f), strokeWidth = 2f)
            val fraction = ((speed - 1f) / 2f).coerceIn(0f, 1f)
            val knobY = size.height * 0.9f - fraction * size.height * 0.8f
            drawCircle(colors.accent, 4f, Offset(trackX, knobY))
        }
    }
}

private class BbqView(val scale: Float, val ox: Float, val oy: Float) {
    fun x(v: Float): Float = ox + v * scale
    fun y(v: Float): Float = oy + v * scale
    fun s(v: Float): Float = v * scale

    fun toWorld(px: Float, py: Float): Pair<Float, Float> =
        (px - ox) / scale to (py - oy) / scale
}

private fun bbqView(width: Float, height: Float): BbqView {
    val scale = min(width / BBQ_VIEW_W, height / BBQ_VIEW_H)
    return BbqView(scale, (width - BBQ_VIEW_W * scale) / 2f, (height - BBQ_VIEW_H * scale) / 2f)
}

private fun DrawScope.drawBbqBoard(state: BbqBattleState, view: BbqView, colors: DoradoColors) {
    drawRect(colors.tilePressed, Offset(view.x(0f), view.y(0f)), Size(view.s(BBQ_VIEW_W), view.s(BBQ_VIEW_H)))
    val stripe = view.s(18f)
    var stripeY = 0f
    var index = 0
    while (stripeY < BBQ_VIEW_H) {
        if (index % 2 == 0) {
            drawRect(colors.elevated, Offset(view.x(0f), view.y(stripeY)), Size(view.s(BBQ_VIEW_W), view.s(stripe)))
        }
        stripeY += stripe
        index++
    }

    // Grid.
    for (gx in 0..BBQ_GRID_W) {
        val gxPx = (BBQ_GRID_X + gx * BBQ_CELL).toFloat()
        drawLine(
            colors.border,
            Offset(view.x(gxPx), view.y(BBQ_GRID_Y.toFloat())),
            Offset(view.x(gxPx), view.y((BBQ_GRID_Y + BBQ_GRID_H * BBQ_CELL).toFloat())),
            strokeWidth = view.s(0.8f),
        )
    }
    for (gy in 0..BBQ_GRID_H) {
        val gyPx = (BBQ_GRID_Y + gy * BBQ_CELL).toFloat()
        drawLine(
            colors.border,
            Offset(view.x(BBQ_GRID_X.toFloat()), view.y(gyPx)),
            Offset(view.x((BBQ_GRID_X + BBQ_GRID_W * BBQ_CELL).toFloat()), view.y(gyPx)),
            strokeWidth = view.s(0.8f),
        )
    }

    // Route.
    if (state.route.size > 1) {
        state.route.zipWithNext().forEach { (a, b) ->
            val (ax, ay) = BbqBattleEngine.center(a.x, a.y)
            val (bx, by) = BbqBattleEngine.center(b.x, b.y)
            drawLine(colors.accent.copy(alpha = 0.25f), Offset(view.x(ax), view.y(ay)), Offset(view.x(bx), view.y(by)), strokeWidth = view.s(3f))
        }
    }

    // Food.
    val (fx, fy) = BbqBattleEngine.center(BBQ_GOAL_X, BBQ_GOAL_Y)
    drawCircle(colors.accent, view.s(13f), Offset(view.x(fx), view.y(fy)))
    drawCircle(Color.White.copy(alpha = 0.85f), view.s(6f), Offset(view.x(fx - 3f), view.y(fy - 4f)))
    drawRect(
        colors.textInactive,
        Offset(view.x(fx - 18f), view.y(fy - 26f)),
        Size(view.s(36f * state.foodHp / BBQ_FOOD_HP), view.s(4f)),
    )

    // Towers.
    for (tower in state.towers) {
        val (tx, ty) = BbqBattleEngine.center(tower.x, tower.y)
        drawBbqTower(view, tx, ty, tower, colors)
    }

    // Creeps.
    for (creep in state.creeps) {
        drawBbqCreep(view, creep, colors)
    }
}

private fun DrawScope.drawBbqTower(view: BbqView, x: Float, y: Float, tower: BbqTower, colors: DoradoColors) {
    val cx = view.x(x)
    val cy = view.y(y)
    val radius = view.s(14f)
    when (tower.type) {
        BbqTowerType.SPRAY -> {
            drawRect(colors.accent, Offset(cx - radius * 0.6f, cy - radius), Size(radius * 1.2f, radius * 2f))
            drawRect(Color.White.copy(alpha = 0.7f), Offset(cx - radius * 0.2f, cy - radius * 1.3f), Size(radius * 0.4f, radius * 0.4f))
        }
        BbqTowerType.GUM -> {
            drawCircle(DoradoAccent.PINK.primary, radius * 0.9f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.35f, Offset(cx - radius * 0.25f, cy - radius * 0.25f))
        }
        BbqTowerType.BOMB -> {
            drawCircle(Color.Black, radius, Offset(cx, cy))
            drawRect(colors.border, Offset(cx - view.s(1f), cy - radius * 1.6f), Size(view.s(2f), radius * 0.7f))
        }
        BbqTowerType.ZAPPER -> {
            drawRect(DoradoAccent.CYAN.primary, Offset(cx - radius * 0.35f, cy - radius), Size(radius * 0.7f, radius * 2f))
            drawCircle(colors.accentBright, radius * 0.4f, Offset(cx, cy - radius))
        }
    }
    if (tower.level > 0) {
        repeat(tower.level) { level ->
            drawRect(
                colors.accentBright,
                Offset(cx - radius * 0.7f + level * view.s(5f), cy + radius * 0.8f),
                Size(view.s(3f), view.s(3f)),
            )
        }
    }
}

private fun DrawScope.drawBbqCreep(view: BbqView, creep: BbqCreep, colors: DoradoColors) {
    val cx = view.x(creep.x)
    val cy = view.y(creep.y)
    val body = view.s(9f)
    when (creep.kind) {
        BbqCreepKind.ANT -> {
            drawCircle(Color.Black, body * 0.55f, Offset(cx, cy))
            drawCircle(Color.Black, body * 0.4f, Offset(cx - body * 0.8f, cy))
            drawCircle(Color.Black, body * 0.4f, Offset(cx + body * 0.8f, cy))
        }
        BbqCreepKind.SNAIL -> {
            drawCircle(Color.White.copy(alpha = 0.85f), body * 0.7f, Offset(cx, cy))
            drawCircle(colors.tilePressed, body * 0.4f, Offset(cx, cy))
        }
        BbqCreepKind.CENTIPEDE -> {
            repeat(5) { segment ->
                drawCircle(colors.accentBright, body * 0.28f, Offset(cx + (segment - 2) * body * 0.5f, cy))
            }
        }
        BbqCreepKind.BEE -> {
            drawCircle(Color.Black, body * 0.5f, Offset(cx, cy))
            drawLine(colors.accent, Offset(cx - body * 0.3f, cy), Offset(cx + body * 0.3f, cy), strokeWidth = view.s(2f))
            val flap = sin((creep.x + creep.y) * 0.4f)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(cx, cy - body * 0.4f), Offset(cx + body * 0.5f, cy - body * (0.9f + flap * 0.15f)), strokeWidth = view.s(1.5f))
        }
    }
    if (creep.hp < creep.maxHp) {
        drawRect(Color.Black, Offset(cx - body, cy - body * 1.6f), Size(body * 2f, view.s(3f)))
        drawRect(colors.accentBright, Offset(cx - body, cy - body * 1.6f), Size(body * 2f * creep.hp / creep.maxHp, view.s(3f)))
    }
}
