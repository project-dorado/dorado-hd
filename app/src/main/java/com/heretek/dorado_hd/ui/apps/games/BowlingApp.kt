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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.ui.apps.AppClock
import com.heretek.dorado_hd.data.db.GameScoreEntity
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.apps.engine3d.Camera3d
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.MeshData
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3dView
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.engine3d.nodeAt
import com.heretek.dorado_hd.ui.apps.engine3d.rebuild
import com.heretek.dorado_hd.ui.apps.engine3d.rememberIsResumed
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/* ============================================================ */
/*                    Lucky Lanes Bowling — app                  */
/* ============================================================ */

private enum class BowlingScreen { MENU, SETUP, STATS, GAME }

private const val BOWLING_TIMING_SPEED = 0.05f

@Composable
fun BowlingApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    val stage = remember { BowlingStage() }
    val scene = remember { Scene3d() }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf(BowlingScreen.MENU) }
    var mode by remember { mutableStateOf(BowlingMode.EXHIBITION) }
    var length by remember { mutableStateOf(BowlingLength.FULL) }
    var lane by remember { mutableStateOf(BowlingLane.PINERY) }
    var ball by remember { mutableStateOf(BowlingBall.COMET) }
    var rival by remember { mutableStateOf(BowlingRival.KESTREL) }
    var match by remember { mutableStateOf<BowlingMatch?>(null) }
    var paused by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var series by remember { mutableStateOf(BowlingSeries()) }
    var saved by remember { mutableStateOf<BowlingSave?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }

    var aim by remember { mutableStateOf(0f) }
    var power by remember { mutableStateOf(0f) }
    var angle by remember { mutableStateOf(0f) }
    var timing by remember { mutableStateOf(0f) }
    var timingDir by remember { mutableStateOf(1f) }

    // Physics and timing are wall-clock loops; gate them on the lifecycle so
    // they do not keep simulating while the app is backgrounded (A-28).
    val resumed = rememberIsResumed()

    val history by graph.games.top("lucky-lanes-bowling", 12).collectAsState(initial = emptyList())

    fun play(name: String) {
        if (sound) bank.play(name)
    }

    LaunchedEffect(Unit) {
        series = BowlingEngine.decodeSeries(graph.appState.get("lucky-lanes.series"))
        saved = BowlingEngine.decodeSave(graph.appState.get("lucky-lanes.save"))
        sound = graph.appState.get("lucky-lanes.sound") != "0"
        loaded = true
    }

    LaunchedEffect(series, loaded) {
        if (loaded) graph.appState.put("lucky-lanes.series", BowlingEngine.encodeSeries(series))
    }

    LaunchedEffect(sound, loaded) {
        if (loaded) graph.appState.put("lucky-lanes.sound", if (sound) "1" else "0")
    }

    LaunchedEffect(match?.card, match?.standing, loaded) {
        val current = match ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        if (current.card.finished || current.phase == BowlingPhase.FINISHED) {
            graph.appState.clear("lucky-lanes.save")
            saved = null
        } else {
            val save = BowlingSave(
                card = current.card,
                standing = current.standing,
                lane = current.lane,
                ball = current.ball,
                rival = current.rival,
                seed = current.seed,
            )
            graph.appState.put("lucky-lanes.save", BowlingEngine.encodeSave(save))
            saved = save
        }
    }

    // Fixed sub-step physics loop; 16 ms of wall time maps to 16 ms of sim time.
    LaunchedEffect(screen, paused, match?.phase, resumed) {
        if (screen != BowlingScreen.GAME || paused || !resumed) return@LaunchedEffect
        if (match?.phase != BowlingPhase.ROLLING) return@LaunchedEffect
        while (true) {
            delay(16)
            val current = match ?: break
            val next = BowlingEngine.advance(current, 16)
            match = next
            if (next.phase != BowlingPhase.ROLLING) {
                play(shotSound(next))
                break
            }
        }
    }

    // Let the rack settle on screen, then hand the player the next ball.
    LaunchedEffect(screen, paused, match?.phase, resumed) {
        if (screen != BowlingScreen.GAME || paused || !resumed) return@LaunchedEffect
        if (match?.phase != BowlingPhase.SETTLED) return@LaunchedEffect
        delay(1000)
        val current = match ?: return@LaunchedEffect
        if (current.phase == BowlingPhase.SETTLED) match = BowlingEngine.nextShot(current)
    }

    // The timing/accuracy needle sweeps while the player holds the ball.
    LaunchedEffect(screen, paused, match?.phase, resumed) {
        if (screen != BowlingScreen.GAME || paused || !resumed) return@LaunchedEffect
        if (match?.phase != BowlingPhase.READY) return@LaunchedEffect
        while (true) {
            delay(16)
            var next = timing + timingDir * BOWLING_TIMING_SPEED
            if (next > 1f) {
                next = 2f - next
                timingDir = -1f
            } else if (next < -1f) {
                next = -2f - next
                timingDir = 1f
            }
            timing = next
        }
    }

    LaunchedEffect(match?.phase, match?.card) {
        val current = match ?: return@LaunchedEffect
        if (current.phase != BowlingPhase.FINISHED || recorded) return@LaunchedEffect
        recorded = true
        val value = BowlingEngine.recordValue(current)
        val meta = "${current.mode.name.lowercase()}|${current.lane.label}|${current.length.name.lowercase()}"
        graph.games.record("lucky-lanes-bowling", value, meta)
        series = BowlingEngine.applySeries(series, current)
        play(if (BowlingEngine.matchWon(current)) "win" else "lose")
    }

    fun startGame() {
        match = BowlingEngine.newMatch(mode, length, lane, ball, rival, seed = AppClock.millis().toInt())
        recorded = false
        paused = false
        aim = 0f
        power = 0f
        angle = 0f
        timing = 0f
        screen = BowlingScreen.GAME
    }

    fun resumeGame() {
        val save = saved ?: return
        // Restore the full setup, not the current picker defaults (A-28).
        lane = save.lane
        ball = save.ball
        rival = save.rival
        match = BowlingEngine.resumeMatch(
            save.card, save.standing, save.lane, save.ball, save.rival, seed = save.seed,
        )
        recorded = save.card.finished
        paused = false
        screen = BowlingScreen.GAME
    }

    fun fire() {
        val current = match ?: return
        if (current.phase != BowlingPhase.READY || paused) return
        if (power < 0.08f) return
        val spin = timing.coerceIn(-1f, 1f)
        val params = BowlingThrow(
            aim = aim.coerceIn(-1f, 1f),
            speed = power.coerceIn(0f, 1f),
            angle = angle.coerceIn(-1f, 1f),
            spin = spin,
            accuracy = 1f - abs(spin),
        )
        match = BowlingEngine.release(current, params)
        power = 0f
        angle = 0f
        play("toss")
    }

    when (screen) {
        BowlingScreen.MENU -> BowlingMenu(
            saved = saved,
            series = series,
            sound = sound,
            onContinue = { resumeGame() },
            onQuick = { screen = BowlingScreen.SETUP },
            onRecords = { screen = BowlingScreen.STATS },
            onSound = { sound = !sound },
        )

        BowlingScreen.SETUP -> BowlingSetup(
            mode = mode,
            length = length,
            lane = lane,
            ball = ball,
            rival = rival,
            onMode = { mode = it },
            onLength = { length = it },
            onLane = { lane = it },
            onBall = { ball = it },
            onRival = { rival = it },
            onStart = { startGame() },
            onBack = { screen = BowlingScreen.MENU },
        )

        BowlingScreen.STATS -> BowlingStats(
            series = series,
            history = history,
            onBack = { screen = BowlingScreen.MENU },
        )

        BowlingScreen.GAME -> {
            val current = match
            if (current == null) {
                BowlingMenu(
                    saved = saved,
                    series = series,
                    sound = sound,
                    onContinue = { resumeGame() },
                    onQuick = { screen = BowlingScreen.SETUP },
                    onRecords = { screen = BowlingScreen.STATS },
                    onSound = { sound = !sound },
                )
            } else {
                DisposableEffect(current, aim, paused) {
                    scene.rebuild { stage.render(this, current, aim, paused) }
                    onDispose { }
                }
                BowlingGameScreen(
                    match = current,
                    scene = scene,
                    paused = paused,
                    timing = timing,
                    aim = aim,
                    power = power,
                    angle = angle,
                    sound = sound,
                    onAim = { value ->
                        aim = value
                        power = 0f
                        angle = 0f
                    },
                    onDragDelta = { dPower, dAngle ->
                        power = (power + dPower).coerceIn(0f, 1f)
                        angle = (angle + dAngle).coerceIn(-1f, 1f)
                    },
                    onRelease = { fire() },
                    onPause = { paused = true },
                    onResume = { paused = false },
                    onMenu = {
                        paused = false
                        screen = BowlingScreen.MENU
                    },
                    onAgain = { startGame() },
                    onSound = { sound = !sound },
                )
            }
        }
    }
}

/* ------------------------------- menu ------------------------------- */

@Composable
private fun BowlingMenu(
    saved: BowlingSave?,
    series: BowlingSeries,
    sound: Boolean,
    onContinue: () -> Unit,
    onQuick: () -> Unit,
    onRecords: () -> Unit,
    onSound: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "lucky lanes") {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (saved != null && !saved.card.finished) {
                BowlingButton(
                    "continue · ${saved.card.mode.label} · frame ${min(saved.card.frameIndex + 1, saved.card.frames.size)}",
                    Modifier.fillMaxWidth(),
                    onContinue,
                )
            }
            BowlingButton("new game", Modifier.fillMaxWidth(), onQuick)
            BowlingButton("records", Modifier.fillMaxWidth(), onRecords)
            BowlingButton("sound ${if (sound) "on" else "off"}", Modifier.fillMaxWidth(), onSound)
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "series ${series.played} played · ${series.wins} won · best ${series.best} · " +
                    "${series.strikes} strikes · ${series.spares} spares · ${series.turkeys} turkeys",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun BowlingSetup(
    mode: BowlingMode,
    length: BowlingLength,
    lane: BowlingLane,
    ball: BowlingBall,
    rival: BowlingRival,
    onMode: (BowlingMode) -> Unit,
    onLength: (BowlingLength) -> Unit,
    onLane: (BowlingLane) -> Unit,
    onBall: (BowlingBall) -> Unit,
    onRival: (BowlingRival) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "lucky lanes · setup") {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                text = "mode",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BowlingMode.entries.forEach { option ->
                    BowlingChip(option.label, option == mode) { onMode(option) }
                }
            }
            if (mode != BowlingMode.BLACKJACK) {
                BasicText(
                    text = "length",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BowlingLength.entries.forEach { option ->
                        BowlingChip(option.label, option == length) { onLength(option) }
                    }
                }
            }
            BasicText(
                text = "lane",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BowlingLane.entries.take(3).forEach { option ->
                    BowlingChip(option.label, option == lane) { onLane(option) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BowlingLane.entries.drop(3).forEach { option ->
                    BowlingChip(option.label, option == lane) { onLane(option) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "ball",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(4.dp))
                BowlingBall.entries.forEach { option ->
                    BowlingChip(option.label, option == ball) { onBall(option) }
                }
                Spacer(Modifier.width(8.dp))
                if (mode == BowlingMode.BLACKJACK) {
                    BasicText(
                        text = "dealer",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.width(4.dp))
                    BowlingRival.entries.forEach { option ->
                        BowlingChip(option.label, option == rival) { onRival(option) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BowlingButton("start", Modifier.weight(1f), onStart)
                BowlingButton("back", Modifier.weight(1f), onBack)
            }
        }
    }
}

@Composable
private fun BowlingStats(
    series: BowlingSeries,
    history: List<GameScoreEntity>,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "lucky lanes · records", onBack = onBack) {
        Column(
            Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(
                text = "played ${series.played} · won ${series.wins} · best ${series.best}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            BasicText(
                text = "${series.strikes} strikes · ${series.spares} spares · ${series.turkeys} turkeys",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "recent games",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            history.take(6).forEach { row ->
                BasicText(
                    text = "${row.meta ?: "?"} · ${row.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.weight(1f))
            BowlingButton("back", Modifier.fillMaxWidth(), onBack)
        }
    }
}

/* ------------------------------- game ------------------------------- */

@Composable
private fun BowlingGameScreen(
    match: BowlingMatch,
    scene: Scene3d,
    paused: Boolean,
    timing: Float,
    aim: Float,
    power: Float,
    angle: Float,
    sound: Boolean,
    onAim: (Float) -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onRelease: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMenu: () -> Unit,
    onAgain: () -> Unit,
    onSound: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(
        title = "lucky lanes · ${match.mode.label} · ${match.lane.label}",
        onBack = onPause,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = DoradoTokens.EDGE.dp)) {
                BowlingScoreStrip(match.card)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Scene3dView(
                        scene = scene,
                        modifier = Modifier
                            .fillMaxSize()
                            .appDescription(
                                "bowling lane, ${match.mode.label}, frame ${min(match.card.frameIndex + 1, match.card.frames.size)} of ${match.card.frames.size}, total ${BowlingEngine.totalScore(match.card)}",
                            ),
                    )
                    if (match.phase == BowlingPhase.READY && !paused) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(match.phase, paused) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            onAim(((offset.x / size.width) * 2f - 1f).coerceIn(-1f, 1f))
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            onDragDelta(
                                                (-drag.y / (size.height * 0.75f)).coerceIn(0f, 1f),
                                                (drag.x / (size.width * 0.35f)).coerceIn(-1f, 1f),
                                            )
                                        },
                                        onDragEnd = { onRelease() },
                                    )
                                },
                        )
                    }
                    Column(Modifier.fillMaxSize().padding(4.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .appDescription(
                                    "rolls ${BowlingEngine.displayRolls(match.card, match.card.frameIndex).joinToString(" ")}, ${shotText(match)}",
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                text = BowlingEngine.displayRolls(match.card, match.card.frameIndex)
                                    .joinToString(" "),
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.weight(1f))
                            BasicText(
                                text = shotText(match),
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            Spacer(Modifier.weight(1f))
                            EdgeText("pause", colors.textPrimary) { onPause() }
                        }
                        Spacer(Modifier.weight(1f))
                        ReleaseMeter(timing = timing, power = power, angle = angle, aim = aim)
                    }
                }
                BowlingModePanel(match)
            }

            if (paused) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.94f))
                        .padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        text = "paused · ${match.mode.label}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    BasicText(
                        text = modeProgress(match),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                    EdgeText("resume", colors.textPrimary) { onResume() }
                    EdgeText("main menu", colors.textPrimary) { onMenu() }
                    EdgeText("sound ${if (sound) "on" else "off"}", colors.accent) { onSound() }
                }
            }

            if (match.phase == BowlingPhase.FINISHED) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.92f))
                        .padding(DoradoTokens.EDGE.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    BasicText(
                        text = if (BowlingEngine.matchWon(match)) "well bowled" else "game over",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accentBright),
                    )
                    BasicText(
                        text = finalSummary(match),
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                    )
                    if (match.mode == BowlingMode.BLACKJACK) {
                        BasicText(
                            text = "dealer ${match.dealerRolls.joinToString(" · ")} = ${match.dealerTotal}" +
                                if (match.dealerTotal > BOWLING_BLACKJACK) " (bust)" else "",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BowlingButton("play again", Modifier.weight(1f), onAgain)
                        BowlingButton("main menu", Modifier.weight(1f), onMenu)
                    }
                }
            }
        }
    }
}

@Composable
private fun BowlingScoreStrip(card: BowlingCard) {
    val colors = LocalDoradoColors.current
    val scores = remember(card) { BowlingEngine.scores(card) }
    Row(
        Modifier
            .fillMaxWidth()
            .appDescription("bowling scorecard, total ${BowlingEngine.totalScore(card)}"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        card.frames.forEachIndexed { index, frame ->
            val current = index == card.frameIndex && !card.finished
            Column(
                Modifier
                    .weight(1f)
                    .background(if (current) colors.tilePressed else colors.elevated)
                    .border(0.5.dp, if (current) colors.accent else colors.border)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = BowlingEngine.displayRolls(card, index).joinToString(" ").ifEmpty { " " },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                    maxLines = 1,
                )
                BasicText(
                    text = scores.getOrNull(index)?.toString() ?: " ",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BowlingModePanel(match: BowlingMatch) {
    val colors = LocalDoradoColors.current
    val text = when (match.mode) {
        BowlingMode.EXHIBITION -> "frame ${min(match.card.frameIndex + 1, match.card.frames.size)} of ${match.card.frames.size}" +
            " · total ${BowlingEngine.totalScore(match.card)}"
        BowlingMode.BLACKJACK -> "roll ${min(match.card.frameIndex + 1, match.card.frames.size)} of ${match.card.frames.size}" +
            " · ${BowlingEngine.totalScore(match.card)} / $BOWLING_BLACKJACK" +
            if (BowlingEngine.isBust(match.card)) " · bust" else ""
        BowlingMode.GOLF -> "hole ${min(match.card.frameIndex + 1, match.card.frames.size)} of ${match.card.frames.size}" +
            " · par ${BowlingEngine.golfPar(match.card) / match.card.frames.size}" +
            " · ${BowlingEngine.golfShots(match.card)} shots · lower wins"
    }
    BasicText(
        text = text,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
    )
}

@Composable
private fun ReleaseMeter(timing: Float, power: Float, angle: Float, aim: Float) {
    val colors = LocalDoradoColors.current
    Column {
        BasicText(
            text = "aim %3d%% · angle %+3d · power %3d%% · spin %+3d%% · accuracy %3d%%".format(
                (aim * 100f).toInt(), (angle * 100f).toInt(), (power * 100f).toInt(),
                (timing * 100f).toInt(), ((1f - abs(timing)) * 100f).toInt(),
            ),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawReleaseMeter(timing, power, angle, colors)
        }
    }
}

private fun DrawScope.drawReleaseMeter(
    timing: Float,
    power: Float,
    angle: Float,
    colors: com.heretek.dorado_hd.design.DoradoColors,
) {
    val y = size.height * 0.5f
    val timingWidth = size.width * 0.42f
    val powerLeft = timingWidth + size.width * 0.06f
    val powerWidth = size.width * 0.3f

    // Timing / accuracy track with a centre sweet zone.
    drawRect(colors.elevated, Offset(0f, y - 5f), Size(timingWidth, 10f))
    val sweet = timingWidth * 0.1f
    drawRect(colors.tilePressed, Offset(timingWidth / 2f - sweet, y - 5f), Size(sweet * 2f, 10f))
    drawLine(colors.border, Offset(timingWidth / 2f, y - 8f), Offset(timingWidth / 2f, y + 8f), strokeWidth = 1f)
    val needleX = ((timing.coerceIn(-1f, 1f) + 1f) * 0.5f) * timingWidth
    drawLine(colors.accentBright, Offset(needleX, y - 8f), Offset(needleX, y + 8f), strokeWidth = 2f)

    // Power fill.
    drawRect(colors.elevated, Offset(powerLeft, y - 5f), Size(powerWidth, 10f))
    drawRect(colors.accent, Offset(powerLeft, y - 5f), Size(powerWidth * power.coerceIn(0f, 1f), 10f))
    drawLine(
        colors.textPrimary.copy(alpha = 0.5f),
        Offset(powerLeft, y), Offset(powerLeft + powerWidth, y), strokeWidth = 1f,
    )

    // Angle pointer (centre = straight).
    val angleCenter = powerLeft + powerWidth + size.width * 0.1f
    val angleSpan = size.width * 0.12f
    drawLine(
        colors.border,
        Offset(angleCenter - angleSpan, y), Offset(angleCenter + angleSpan, y), strokeWidth = 1f,
    )
    val pointerX = angleCenter + angleSpan * angle.coerceIn(-1f, 1f)
    drawLine(colors.accent, Offset(angleCenter, y - 6f), Offset(pointerX, y + 6f), strokeWidth = 2f)
}

/* --------------------------- small controls --------------------------- */

@Composable
private fun BowlingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .background(if (selected) colors.accent else colors.tile)
            .appTap(label = label) { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = if (selected) colors.background else colors.textSecondary,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun BowlingButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .appTap(label = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            maxLines = 1,
        )
    }
}

/* ------------------------------ helpers ------------------------------ */

private fun shotSound(match: BowlingMatch): String = when {
    match.shot?.gutter == true -> "error"
    match.lastFelled >= BOWLING_NUM_PINS -> "win"
    match.lastFelled >= 7 -> "score"
    match.lastFelled > 0 -> "hit"
    else -> "tick"
}

private fun shotText(match: BowlingMatch): String = when (match.phase) {
    BowlingPhase.READY -> "swipe up to bowl"
    BowlingPhase.ROLLING -> "rolling"
    else -> shotOutcome(match)
}

private fun shotOutcome(match: BowlingMatch): String {
    if (match.shot?.gutter == true) return "gutterball"
    if (match.lastRollFrame < 0) return ""
    if (match.mode == BowlingMode.GOLF) {
        val holed = match.card.frames.getOrNull(match.card.frameIndex)?.pins ?: 0
        return if (match.lastFelled >= BOWLING_NUM_PINS || holed >= BOWLING_NUM_PINS) "holed!" else "${match.lastFelled} pin"
    }
    val rolls = BowlingEngine.displayRolls(match.card, match.lastRollFrame)
    return when (rolls.lastOrNull()) {
        "X" -> "strike!"
        "/" -> "spare!"
        else -> if (match.lastFelled == 0) "miss" else "${match.lastFelled} pin"
    }
}

private fun modeProgress(match: BowlingMatch): String = when (match.mode) {
    BowlingMode.EXHIBITION -> "frame ${min(match.card.frameIndex + 1, match.card.frames.size)} · total ${BowlingEngine.totalScore(match.card)}"
    BowlingMode.BLACKJACK -> "${BowlingEngine.totalScore(match.card)} / $BOWLING_BLACKJACK"
    BowlingMode.GOLF -> "${BowlingEngine.golfShots(match.card)} shots · par ${BowlingEngine.golfPar(match.card)}"
}

private fun finalSummary(match: BowlingMatch): String = when (match.mode) {
    BowlingMode.EXHIBITION -> "score ${BowlingEngine.totalScore(match.card)} · " +
        "${match.card.frames.indices.count { BowlingEngine.frameMark(match.card, it) == FrameMark.STRIKE }} strikes · " +
        "${match.card.frames.indices.count { BowlingEngine.frameMark(match.card, it) == FrameMark.SPARE }} spares"
    BowlingMode.BLACKJACK -> "player ${BowlingEngine.totalScore(match.card)}" +
        if (BowlingEngine.isBust(match.card)) " (bust)" else ""
    BowlingMode.GOLF -> "${BowlingEngine.golfShots(match.card)} shots · par ${BowlingEngine.golfPar(match.card)} · " +
        if (BowlingEngine.golfShots(match.card) <= BowlingEngine.golfPar(match.card)) "under or even" else "over par"
}

/* ---------------------------- 3D staging ---------------------------- */

private fun rgb(value: Int): Color4 =
    Color4.rgb((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

private fun shade(color: Color4, factor: Float): Color4 =
    Color4(color.r * factor, color.g * factor, color.b * factor, color.a)

/**
 * Renders the lane, gutters, furniture and rack with cached procedural meshes;
 * only the SceneNode list is rebuilt per frame so the GL cache stays warm.
 */
private class BowlingStage {
    private val laneMesh = MeshFactory.box(BOWLING_LANE_WIDTH.toFloat(), 0.6f, BOWLING_LANE_LENGTH.toFloat())
    private val approachMesh = MeshFactory.box(BOWLING_LANE_WIDTH.toFloat(), 0.5f, 70f)
    private val gutterMesh = MeshFactory.box(BOWLING_GUTTER_WIDTH, 1.4f, BOWLING_LANE_LENGTH.toFloat())
    private val wallMesh = MeshFactory.box(3f, 9f, BOWLING_LANE_LENGTH.toFloat())
    private val backMesh = MeshFactory.box(BOWLING_LANE_WIDTH + 24f, 22f, 3f)
    private val pitMesh = MeshFactory.box(BOWLING_LANE_WIDTH + 12f, 2f, 36f)
    private val seamMesh = MeshFactory.box(0.16f, 0.06f, BOWLING_LANE_LENGTH.toFloat())
    private val foulMesh = MeshFactory.box(BOWLING_LANE_WIDTH.toFloat(), 0.08f, 1.4f)
    private val spotMesh = MeshFactory.cylinder(1.3f, 0.12f, 8)
    private val pinBody = MeshFactory.cylinder(BOWLING_PIN_RADIUS, BOWLING_PIN_HEIGHT, 14)
    private val pinBand = MeshFactory.cylinder(BOWLING_PIN_RADIUS * 1.05f, 1.8f, 14)
    private val pinHead = MeshFactory.cylinder(BOWLING_PIN_RADIUS * 0.78f, 4.4f, 14)
    private val ballMesh = MeshFactory.cylinder(BOWLING_BALL_RADIUS, BOWLING_BALL_RADIUS * 2f, 18)
    private val guideMesh = MeshFactory.box(1.1f, 0.08f, 430f)
    private val arrowMesh = MeshFactory.box(1.4f, 0.1f, 4.4f)
    private val beamMesh = MeshFactory.box(BOWLING_LANE_WIDTH + 18f, 1.4f, 1.4f)
    private val postMesh = MeshFactory.cylinder(1.4f, 30f, 10)
    private val glowMesh = MeshFactory.box(0.8f, 0.8f, 300f)
    private val starMesh = MeshFactory.box(1.1f, 1.1f, 1.1f)
    private val oilMeshes = HashMap<BowlingLane, MeshData>()

    private fun oilMesh(lane: BowlingLane): MeshData =
        oilMeshes.getOrPut(lane) {
            MeshFactory.plane(BOWLING_LANE_WIDTH.toFloat(), lane.oilLength * BOWLING_LANE_LENGTH)
        }

    fun render(scene: Scene3d, match: BowlingMatch, aim: Float, paused: Boolean) {
        scene.clear()
        val lane = match.lane
        scene.ambient = 0.5f
        scene.lightDirection = Vec3(-0.35f, -1f, -0.3f)
        scene.backgroundColor = shade(rgb(lane.backRgb), 0.75f)

        val wood = Material3d(rgb(lane.surfaceRgb))
        val woodDark = Material3d(shade(rgb(lane.surfaceRgb), 0.65f))
        val trim = Material3d(rgb(lane.glowRgb), unlit = true, emissive = 0.55f)
        val glow = Material3d(rgb(lane.glowRgb), unlit = true, emissive = 0.8f)

        scene.add(nodeAt(laneMesh, y = -0.3f, z = -BOWLING_LANE_LENGTH / 2f, material = wood))
        scene.add(nodeAt(approachMesh, y = -0.3f, z = BOWLING_BALL_START_Z + 10f, material = woodDark))
        scene.add(nodeAt(foulMesh, y = 0.05f, z = -BOWLING_LANE_LENGTH.toFloat(), material = trim))
        for (x in listOf(-14f, -7f, 7f, 14f)) {
            scene.add(nodeAt(seamMesh, x = x, y = 0.03f, z = -BOWLING_LANE_LENGTH / 2f, material = woodDark))
        }
        scene.add(
            nodeAt(
                oilMesh(lane),
                y = 0.02f,
                z = -BOWLING_LANE_LENGTH + lane.oilLength * BOWLING_LANE_LENGTH / 2f,
                material = Material3d(shade(rgb(lane.surfaceRgb), 1.18f), unlit = true),
            ),
        )
        scene.add(nodeAt(gutterMesh, x = -(BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH / 2f), y = -0.9f, z = -BOWLING_LANE_LENGTH / 2f, material = woodDark))
        scene.add(nodeAt(gutterMesh, x = BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH / 2f, y = -0.9f, z = -BOWLING_LANE_LENGTH / 2f, material = woodDark))
        scene.add(nodeAt(wallMesh, x = -(BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH + 1.5f), y = 4.5f, z = -BOWLING_LANE_LENGTH / 2f, material = woodDark))
        scene.add(nodeAt(wallMesh, x = BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH + 1.5f, y = 4.5f, z = -BOWLING_LANE_LENGTH / 2f, material = woodDark))
        scene.add(nodeAt(glowMesh, x = -(BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH + 1.2f), y = 6.5f, z = -BOWLING_LANE_LENGTH / 2f, material = glow))
        scene.add(nodeAt(glowMesh, x = BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH + 1.2f, y = 6.5f, z = -BOWLING_LANE_LENGTH / 2f, material = glow))
        scene.add(nodeAt(backMesh, y = 11f, z = 4.5f, material = woodDark))
        scene.add(nodeAt(pitMesh, y = -1.2f, z = -14f, material = Material3d(shade(rgb(lane.backRgb), 0.5f))))

        for (i in 0 until BOWLING_NUM_PINS) {
            scene.add(nodeAt(spotMesh, x = pinHomeX(i), y = 0.06f, z = pinHomeZ(i), material = woodDark))
        }
        for (x in listOf(-15f, -7.5f, 0f, 7.5f, 15f)) {
            scene.add(nodeAt(arrowMesh, x = x, y = 0.06f, z = -540f, material = trim))
        }

        when (lane) {
            BowlingLane.PINERY -> for (z in listOf(-560f, -360f, -160f)) {
                scene.add(nodeAt(beamMesh, y = 26f, z = z, material = woodDark))
            }
            BowlingLane.ARCADE -> for (z in listOf(-600f, -400f, -200f)) {
                scene.add(nodeAt(starMesh, x = -26f, y = 14f, z = z, material = glow))
                scene.add(nodeAt(starMesh, x = 26f, y = 14f, z = z, material = glow))
            }
            BowlingLane.DUNES -> for (z in listOf(-640f, -420f, -200f)) {
                scene.add(nodeAt(starMesh, x = -30f, y = 2f, z = z, material = woodDark))
                scene.add(nodeAt(starMesh, x = 30f, y = 2f, z = z, material = woodDark))
            }
            BowlingLane.GROTTO -> for (z in listOf(-620f, -420f, -220f)) {
                scene.add(nodeAt(postMesh, x = -27f, y = 15f, z = z, material = Material3d(shade(rgb(lane.glowRgb), 0.4f))))
                scene.add(nodeAt(postMesh, x = 27f, y = 15f, z = z, material = Material3d(shade(rgb(lane.glowRgb), 0.4f))))
            }
            BowlingLane.ORBIT -> for (i in 0 until 8) {
                val angle = (i / 8f) * 2f * PI.toFloat()
                scene.add(
                    nodeAt(
                        starMesh,
                        x = cos(angle) * 34f,
                        y = 22f + sin(angle * 2f) * 8f,
                        z = -620f + sin(angle) * 260f,
                        material = glow,
                    ),
                )
            }
        }

        val shot = match.shot
        val pins = shot?.pins ?: List(BOWLING_NUM_PINS) { i ->
            BowlingPin(
                index = i,
                x = pinHomeX(i),
                z = pinHomeZ(i),
                standing = match.standing.getOrElse(i) { true },
                inPlay = match.standing.getOrElse(i) { true },
            )
        }
        val pinWhite = Material3d(Color4(0.94f, 0.92f, 0.88f))
        val pinTrim = Material3d(rgb(lane.glowRgb))
        for (pin in pins) {
            if (!pin.inPlay || pin.offDeck) continue
            val ux = sin(pin.tilt) * pin.tiltX
            val uy = cos(pin.tilt)
            val uz = sin(pin.tilt) * pin.tiltZ
            val pitch = pin.tilt * pin.tiltZ
            val roll = -pin.tilt * pin.tiltX
            fun place(mesh: MeshData, offset: Float, material: Material3d) {
                scene.add(
                    nodeAt(
                        mesh,
                        x = pin.x + ux * offset,
                        y = max(BOWLING_PIN_RADIUS, uy * offset),
                        z = pin.z + uz * offset,
                        material = material,
                        pitchRad = pitch,
                        rollRad = roll,
                    ),
                )
            }
            place(pinBody, BOWLING_PIN_HEIGHT / 2f, pinWhite)
            place(pinBand, BOWLING_PIN_HEIGHT * 0.68f, pinTrim)
            place(pinHead, BOWLING_PIN_HEIGHT * 0.87f, pinWhite)
        }

        val ballX = shot?.ball?.x ?: aim * (BOWLING_LANE_HALF - BOWLING_BALL_RADIUS)
        val ballZ = shot?.ball?.z ?: BOWLING_BALL_START_Z
        val ballY = max(BOWLING_BALL_RADIUS, shot?.ball?.y ?: BOWLING_BALL_START_Y)
        val ballMaterial = Material3d(rgb(match.ball.rgb))
        val ballPitch = if (shot == null || shot.ball.z < BOWLING_DECK_FRONT_Z) 0f else 0.5f
        scene.add(nodeAt(ballMesh, x = ballX, y = ballY, z = ballZ, material = ballMaterial, pitchRad = ballPitch))
        scene.add(nodeAt(ballMesh, x = ballX, y = ballY, z = ballZ, material = ballMaterial, rollRad = PI.toFloat() / 2f))
        scene.add(nodeAt(ballMesh, x = ballX, y = ballY, z = ballZ, material = ballMaterial, pitchRad = PI.toFloat() / 2f))

        if (match.phase == BowlingPhase.READY && !paused) {
            val guideX = aim * (BOWLING_LANE_HALF - 1.5f)
            scene.add(nodeAt(guideMesh, x = guideX, y = 0.05f, z = -430f, material = trim))
            scene.add(nodeAt(arrowMesh, x = guideX, y = 0.06f, z = -212f, material = glow))
        }

        scene.camera = when (match.phase) {
            BowlingPhase.ROLLING, BowlingPhase.SETTLED -> {
                val follow = min(ballZ + 180f, -50f)
                Camera3d(
                    eye = Vec3(ballX * 0.25f, 26f, ballZ - 80f),
                    target = Vec3(ballX * 0.15f, 3f, follow),
                    fovYDeg = 55f,
                    near = 0.5f,
                    far = 2400f,
                )
            }
            else -> Camera3d(
                eye = Vec3(0f, 54f, -840f),
                target = Vec3(aim * 6f, 5f, -80f),
                fovYDeg = 50f,
                near = 0.5f,
                far = 2400f,
            )
        }
    }
}
