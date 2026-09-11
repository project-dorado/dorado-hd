package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val BEES_SAVE_KEY = "bees"
private const val BEES_VIEW_W = 272f
private const val BEES_VIEW_H = 420f

@Composable
fun BeesApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var progress by remember { mutableStateOf(BeesEngine.BeesProgress()) }
    var game by remember { mutableStateOf<BeesState?>(null) }
    val latestGame = rememberUpdatedState(game)
    var saved by remember { mutableStateOf<BeesState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var selectedBee by remember { mutableStateOf<Int?>(null) }
    var recorded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var shopTheme by remember { mutableStateOf(BeesTheme.GRASS) }
    var mode by remember { mutableStateOf(BeesMode.ADVENTURE) }
    val history by graph.games.top(BEES_SAVE_KEY, 5).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        graph.appState.get("$BEES_SAVE_KEY.progress")?.let { BeesEngine.decodeProgress(it) }?.let { progress = it }
        saved = graph.appState.get("$BEES_SAVE_KEY.game")?.let { BeesEngine.decode(it) }
        sound = graph.appState.get("$BEES_SAVE_KEY.sound") != "0"
        loaded = true
    }

    LaunchedEffect(progress, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("$BEES_SAVE_KEY.progress", BeesEngine.encodeProgress(progress))
    }

    // Periodic autosave: the run ticks every 16 ms, so a keyed debounce never
    // settles. Snapshot on a fixed cadence and on pause/exit.
    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(2_000)
            val current = game ?: continue
            if (!current.over) graph.appState.put("$BEES_SAVE_KEY.game", BeesEngine.encode(current))
        }
    }

    LaunchedEffect(screen, paused) {
        if (screen != "game" || paused) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = game ?: break
            if (current.over) continue
            val next = BeesEngine.step(current, 16)
            game = next
            next.events.forEach { event ->
                when (event) {
                    BeesEvent.GATHER -> play("tick")
                    BeesEvent.DEPOSIT -> play("score")
                    BeesEvent.EXPLODE -> {
                        play("explode")
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    BeesEvent.JAR -> play("win")
                    BeesEvent.BANK -> play("coin")
                    BeesEvent.BADDIE_DOWN -> {
                        play("hit")
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    BeesEvent.BADDIE_SPAWN -> play("whoosh")
                    BeesEvent.TIME_WARNING -> play("error")
                    BeesEvent.WIN -> play("win")
                    BeesEvent.LOSE -> play("lose")
                    BeesEvent.SPAWN -> play("pop")
                }
            }
            val state = next
            if (state.bees.isNotEmpty() && state.bees.none { it.id == selectedBee }) selectedBee = state.bees.first().id
            if (state.over && !recorded) {
                recorded = true
                graph.appState.clear("$BEES_SAVE_KEY.game")
                progress = BeesEngine.recordResult(progress, state)
                scope.launch {
                    graph.games.record(BEES_SAVE_KEY, state.score, "level ${state.levelId} · ${state.theme.label}")
                }
            }
        }
    }

    fun startLevel(id: Int, mode: BeesMode) {
        val fresh = BeesEngine.newGame(id, mode, System.currentTimeMillis().toInt(), progress.upgrades)
        game = fresh
        saved = null
        recorded = false
        paused = false
        selectedBee = null
        screen = "game"
        scope.launch { graph.appState.put("$BEES_SAVE_KEY.game", BeesEngine.encode(fresh)) }
    }

    fun applyGame(transform: (BeesState) -> BeesState) {
        val snapshot = game ?: return
        game = transform(snapshot)
    }

    fun persistSnapshot() {
        val current = game ?: return
        if (!current.over) scope.launch { graph.appState.put("$BEES_SAVE_KEY.game", BeesEngine.encode(current)) }
    }

    fun resume() {
        val resumeGame = saved ?: return
        game = resumeGame
        recorded = false
        paused = false
        selectedBee = null
        screen = "game"
    }

    if (screen == "menu") {
        BeesMenu(
            progress = progress,
            saved = saved,
            best = history.firstOrNull()?.score ?: 0,
            sound = sound,
            onSound = {
                sound = !sound
                scope.launch { graph.appState.put("$BEES_SAVE_KEY.sound", if (sound) "1" else "0") }
            },
            onWorld = { screen = "map" },
            onShop = { screen = "shop" },
            onResume = { resume() },
        )
        return
    }
    if (screen == "map") {
        BeesWorldMap(
            progress = progress,
            mode = mode,
            onMode = { mode = it },
            onPick = { startLevel(it, mode) },
            onBack = { screen = "menu" },
        )
        return
    }
    if (screen == "shop") {
        BeesShop(
            progress = progress,
            theme = shopTheme,
            onTheme = { shopTheme = it },
            onUnlock = { kind ->
                val (upgrades, jars) = BeesEngine.purchaseUnlock(progress.upgrades, kind, progress.jarsFor(shopTheme))
                if (jars != progress.jarsFor(shopTheme) || kind in upgrades.unlocked) {
                    progress = progress.copy(jars = progress.jars + (shopTheme to jars), upgrades = upgrades)
                }
            },
            onHive = {
                val (upgrades, jars) = BeesEngine.purchaseHiveUpgrade(progress.upgrades, progress.jarsFor(shopTheme))
                if (jars != progress.jarsFor(shopTheme) || upgrades.hiveUpgrade != progress.upgrades.hiveUpgrade) {
                    progress = progress.copy(jars = progress.jars + (shopTheme to jars), upgrades = upgrades)
                }
            },
            onBack = { screen = "menu" },
        )
        return
    }

    val current = game ?: return
    DetailScaffold(title = "bees", onBack = { persistSnapshot(); paused = true }) {
        Box(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Column(Modifier.fillMaxSize()) {
                BeesHud(current, progress)
                Spacer(Modifier.height(4.dp))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val view = beesView(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(BeesEngine.runToken(current), paused) {
                                detectTapGestures { offset ->
                                    if (paused) return@detectTapGestures
                                    val snapshot = latestGame.value ?: return@detectTapGestures
                                    val liveView = beesView(size.width.toFloat(), size.height.toFloat())
                                    val (wx, wy) = liveView.toWorld(offset.x, offset.y)
                                    val node = snapshot.nodes.minByOrNull { candidate ->
                                        val dx = candidate.x - wx
                                        val dy = candidate.y - wy
                                        dx * dx + dy * dy
                                    } ?: return@detectTapGestures
                                    val dx = node.x - wx
                                    val dy = node.y - wy
                                    if (dx * dx + dy * dy > 28f * 28f) return@detectTapGestures
                                    val bee = selectedBee ?: snapshot.bees.firstOrNull()?.id
                                    if (bee != null) {
                                        game = BeesEngine.sendBee(snapshot, bee, node.id)
                                        play("select")
                                    }
                                }
                            },
                    ) {
                        drawBeesBoard(current, view, colors, selectedBee)
                    }
                }
                Spacer(Modifier.height(2.dp))
                BeesBeeBar(current, selectedBee, onSelect = { selectedBee = it })
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    BeesButton("bank", true, Modifier.weight(1f)) {
                        applyGame { BeesEngine.bank(it) }
                        play("coin")
                    }
                    BeesButton("move bees", true, Modifier.weight(1f)) {
                        applyGame { BeesEngine.moveBees(it) }
                        play("select")
                    }
                    BeesButton("pause", true, Modifier.weight(1f)) { persistSnapshot(); paused = true }
                }
            }

            if (paused || current.over) {
                BeesEndPanel(
                    current = current,
                    paused = paused,
                    onResume = { paused = false },
                    onRetry = { startLevel(current.levelId, current.mode) },
                    onMap = { persistSnapshot(); game = null; screen = "map" },
                )
            }
        }
    }
}

@Composable
private fun BeesHud(current: BeesState, progress: BeesEngine.BeesProgress) {
    val colors = LocalDoradoColors.current
    val remaining = (BEES_TIME_LIMIT_MS - current.elapsedMs).coerceAtLeast(0L)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = "score ${current.score}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "streak ${current.streak} · bees ${current.bees.size}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = "%d:%02d".format(remaining / 60_000, (remaining / 1_000) % 60),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = if (remaining < 30_000) colors.accentBright else colors.textSecondary),
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = "jars ${progress.jarsFor(current.theme) + current.honeyJars}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
    }
        BasicText(
            text = "honey ${current.tank.honey}/%d · pollen %d · jar %d/%d".format(
            BeesEngine.tankCapacity(current.hiveUpgrade),
            current.tank.pollen.toInt(),
            current.jarProgress.toInt(),
            current.theme.jarCost.toInt(),
        ),
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
    )
    BasicText(
        text = "${current.theme.label} ${current.levelId} · ${current.mode.label}",
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
    )
}

@Composable
private fun BeesBeeBar(current: BeesState, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = LocalDoradoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        if (current.bees.isEmpty()) {
            BasicText(
                text = "the hive is spawning…",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textInactive),
            )
        }
        current.bees.take(5).forEach { bee ->
            Box(
                Modifier
                    .weight(1f)
                    .height(24.dp)
                    .background(if (bee.id == selected) colors.tilePressed else colors.tile)
                    .border(0.5.dp, if (bee.id == selected) colors.accent else colors.border)
                    .pointerInput(bee.id) { detectTapGestures { onSelect(bee.id) } },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = bee.kind.label.take(4),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
                )
            }
        }
    }
}

@Composable
private fun BeesEndPanel(
    current: BeesState,
    paused: Boolean,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onMap: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText(
                text = when {
                    current.over && current.won -> "adventure complete"
                    current.over -> "the hive fell"
                    else -> "paused"
                },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            if (current.over) {
                BasicText(
                    text = "score ${current.score} · jars ${current.honeyJars} · streak ${current.streak}",
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
                    BeesButton("resume", true, Modifier.width(90.dp)) { onResume() }
                }
                if (current.over) {
                    BeesButton("retry", true, Modifier.width(90.dp)) { onRetry() }
                }
                BeesButton("world map", true, Modifier.width(90.dp)) { onMap() }
            }
        }
    }
}

@Composable
private fun BeesMenu(
    progress: BeesEngine.BeesProgress,
    saved: BeesState?,
    best: Int,
    sound: Boolean,
    onSound: () -> Unit,
    onWorld: () -> Unit,
    onShop: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "bees") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (saved != null) {
                BeesButton("continue level ${saved.levelId} (${saved.theme.label})", true, Modifier.width(250.dp)) { onResume() }
            }
            BeesButton("play", true, Modifier.width(250.dp)) { onWorld() }
            BeesButton("hive shop", true, Modifier.width(250.dp)) { onShop() }
            BeesButton("sound ${if (sound) "on" else "off"}", true, Modifier.width(250.dp)) { onSound() }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "Grow your hive, gather pollen, bank honey into jars. " +
                    "Adventure always wins at the buzzer. Best score $best.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun BeesWorldMap(
    progress: BeesEngine.BeesProgress,
    mode: BeesMode,
    onMode: (BeesMode) -> Unit,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "bees · world map") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BeesButton("adventure", true, Modifier.width(110.dp), active = mode == BeesMode.ADVENTURE) { onMode(BeesMode.ADVENTURE) }
                BeesButton("challenge", true, Modifier.width(110.dp), active = mode == BeesMode.CHALLENGE) { onMode(BeesMode.CHALLENGE) }
            }
            BeesTheme.entries.forEach { theme ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        text = theme.label,
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                        modifier = Modifier.width(70.dp),
                    )
                    for (index in 1..5) {
                        val id = theme.ordinal * 5 + index
                        val unlocked = id <= progress.unlockedLevel
                        BeesButton(
                            label = if (unlocked) "$index" else "·",
                            enabled = unlocked,
                            modifier = Modifier.width(52.dp),
                        ) { onPick(id) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "jars: " + BeesTheme.entries.joinToString(" · ") { "${it.label} ${progress.jarsFor(it)}" },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textInactive),
            )
            BeesButton("back", true, Modifier.width(120.dp)) { onBack() }
        }
    }
}

@Composable
private fun BeesShop(
    progress: BeesEngine.BeesProgress,
    theme: BeesTheme,
    onTheme: (BeesTheme) -> Unit,
    onUnlock: (BeesKind) -> Unit,
    onHive: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "bees · hive shop") {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BeesTheme.entries.forEach { candidate ->
                    BeesButton(
                        label = "${candidate.label} ${progress.jarsFor(candidate)}",
                        enabled = true,
                        active = candidate == theme,
                        modifier = Modifier.width(80.dp),
                    ) { onTheme(candidate) }
                }
            }
            BasicText(
                text = "spending ${theme.label} jars",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            BeesButton("unlock worker bee (8 jars)", BeesKind.WORKER !in progress.upgrades.unlocked && progress.jarsFor(theme) >= 8, Modifier.width(250.dp)) {
                onUnlock(BeesKind.WORKER)
            }
            BeesButton("unlock ninja bee (12 jars)", BeesKind.NINJA !in progress.upgrades.unlocked && progress.jarsFor(theme) >= 12, Modifier.width(250.dp)) {
                onUnlock(BeesKind.NINJA)
            }
            BeesButton("honeycomb upgrade ${progress.upgrades.hiveUpgrade}/4 (${progress.upgrades.hiveUpgrade + 1} jars)", progress.upgrades.hiveUpgrade < 4 && progress.jarsFor(theme) >= progress.upgrades.hiveUpgrade + 1, Modifier.width(250.dp)) {
                onHive()
            }
            BasicText(
                text = "unlocked: " + progress.upgrades.unlocked.joinToString(", ") { it.label },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
            BeesButton("back", true, Modifier.width(120.dp)) { onBack() }
        }
    }
}

@Composable
private fun BeesButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } },
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

private class BeesView(val scale: Float, val ox: Float, val oy: Float, val offsetY: Float) {
    fun x(v: Float): Float = ox + v * scale
    fun y(v: Float): Float = oy + (v - offsetY) * scale
    fun s(v: Float): Float = v * scale
    fun toWorld(px: Float, py: Float): Pair<Float, Float> =
        (px - ox) / scale to ((py - oy) / scale + offsetY)
}

private fun beesView(width: Float, height: Float): BeesView {
    val scale = min(width / BEES_VIEW_W, height / BEES_VIEW_H)
    return BeesView(scale, (width - BEES_VIEW_W * scale) / 2f, (height - BEES_VIEW_H * scale) / 2f, 40f)
}

private fun DrawScope.drawBeesBoard(state: BeesState, view: BeesView, colors: DoradoColors, selected: Int?) {
    val themeTint = when (state.theme) {
        BeesTheme.GRASS -> DoradoAccent.LIME.primary
        BeesTheme.SWAMP -> DoradoAccent.CYAN.primary
        BeesTheme.DESERT -> DoradoAccent.ORANGE.primary
        BeesTheme.SNOW -> DoradoAccent.CYAN.bright
        BeesTheme.SPACE -> DoradoAccent.PURPLE.primary
    }
    drawRect(themeTint.copy(alpha = 0.12f), Offset(view.x(0f), view.y(0f)), Size(view.s(BEES_VIEW_W), view.s(BEES_VIEW_H)))

    for (path in state.paths) {
        val a = state.nodes.firstOrNull { it.id == path.a } ?: continue
        val b = state.nodes.firstOrNull { it.id == path.b } ?: continue
        drawLine(colors.border, Offset(view.x(a.x), view.y(a.y)), Offset(view.x(b.x), view.y(b.y)), strokeWidth = view.s(3f))
    }

    for (node in state.nodes) {
        when (node.type) {
            BeesNodeType.HIVE -> {
                drawCircle(DoradoAccent.ORANGE.primary, view.s(16f), Offset(view.x(node.x), view.y(node.y)))
                drawCircle(DoradoAccent.ORANGE.bright, view.s(8f), Offset(view.x(node.x), view.y(node.y)))
            }
            BeesNodeType.FLOWER -> {
                val flower = node.flower ?: continue
                drawCircle(colors.tile, view.s(12f), Offset(view.x(node.x), view.y(node.y)))
                if (flower.petals > 0) {
                    val petalColor = flowerColor(flower.color)
                    for (petal in 0 until flower.petals) {
                        val angle = petal * (2 * Math.PI / flower.petals)
                        drawCircle(
                            petalColor,
                            view.s(5f),
                            Offset(
                                view.x(node.x + (kotlin.math.cos(angle) * 6f).toFloat()),
                                view.y(node.y + (kotlin.math.sin(angle) * 6f).toFloat()),
                            ),
                        )
                    }
                } else {
                    drawCircle(colors.textInactive, view.s(4f), Offset(view.x(node.x), view.y(node.y)))
                }
            }
        }
    }

    for (baddie in state.baddies) {
        drawCircle(baddieColor(baddie.kind), view.s(11f), Offset(view.x(baddie.x), view.y(baddie.y)))
    }

    for (bee in state.bees) {
        val cx = view.x(bee.x)
        val cy = view.y(bee.y)
        val body = when (bee.kind) {
            BeesKind.NORMAL -> colors.textPrimary
            BeesKind.POLLINATOR -> DoradoAccent.LIME.bright
            BeesKind.WORKER -> DoradoAccent.ORANGE.bright
            BeesKind.NINJA -> DoradoAccent.PURPLE.bright
        }
        drawCircle(body, view.s(6f), Offset(cx, cy))
        drawLine(colors.textInactive, Offset(cx, cy - view.s(5f)), Offset(cx + view.s(5f), cy - view.s(9f)), strokeWidth = view.s(1.5f))
        if (bee.id == selected) {
            drawCircle(colors.accentBright, view.s(9f), Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(view.s(2f)))
        }
        if (bee.carried > 0f) {
            drawCircle(DoradoAccent.ORANGE.bright, view.s(3f), Offset(cx, cy + view.s(8f)))
        }
    }
}

private fun flowerColor(color: Int): Color = when (color % 6) {
    0 -> DoradoAccent.PINK.primary
    1 -> DoradoAccent.ORANGE.primary
    2 -> DoradoAccent.CYAN.primary
    3 -> DoradoAccent.LIME.primary
    4 -> DoradoAccent.PURPLE.primary
    else -> Color.White
}

private fun baddieColor(kind: BeesBaddieKind): Color = when (kind) {
    BeesBaddieKind.BROWN_BEAR -> DoradoAccent.ORANGE.primary
    BeesBaddieKind.BLACK_BEAR -> Color.Black
    BeesBaddieKind.POLAR_BEAR -> Color.White
    BeesBaddieKind.MOON_BEAR -> DoradoAccent.PURPLE.primary
    BeesBaddieKind.BUNNY -> Color.White
    BeesBaddieKind.GOPHER -> DoradoAccent.ORANGE.bright
    BeesBaddieKind.HUMMINGBIRD -> DoradoAccent.CYAN.bright
}
