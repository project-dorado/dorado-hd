package com.heretek.dorado_hd.ui.apps.games

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun SpaceBattleApp() {
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
    var difficulty by remember { mutableStateOf(SpaceBattleEngine.Difficulty.NORMAL) }
    var control by remember { mutableStateOf(SpaceBattleEngine.ControlStyle.TOUCH_ABSOLUTE) }
    var progress by remember { mutableStateOf(SpaceBattleEngine.SbProgress()) }
    var game by remember { mutableStateOf<SpaceBattleEngine.SbState?>(null) }
    var resumeStage by remember { mutableStateOf<Int?>(null) }
    var stagePage by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var completedHandled by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(SpaceBattleEngine.SbInput()) }
    val tilt = rememberTilt()
    val scores by graph.games.top("space-battle-2", 20).collectAsState(initial = emptyList())

    fun play(name: String) = bank.play(name)

    LaunchedEffect(Unit) {
        progress = graph.appState.get("space-battle-2.progress")?.let { SpaceBattleEngine.decodeProgress(it) }
            ?: SpaceBattleEngine.SbProgress()
        resumeStage = graph.appState.get("space-battle-2")?.split("|")?.firstOrNull()?.toIntOrNull()
        control = if (graph.appState.get("space-battle-2.control") == "tilt") {
            SpaceBattleEngine.ControlStyle.TILT
        } else {
            SpaceBattleEngine.ControlStyle.TOUCH_ABSOLUTE
        }
        loaded = true
    }
    // Cold-start gate: defaults must not be written until the stored profile
    // has been read back.
    LaunchedEffect(progress, loaded) {
        if (loaded) graph.appState.put("space-battle-2.progress", SpaceBattleEngine.encodeProgress(progress))
    }
    LaunchedEffect(control, loaded) {
        if (loaded) graph.appState.put("space-battle-2.control", if (control == SpaceBattleEngine.ControlStyle.TILT) "tilt" else "touch")
    }
    // Persist the campaign checkpoint after the state settles; writing straight
    // after the assignment can capture the previous (or null) stage.
    LaunchedEffect(game?.stage, difficulty, screen, loaded) {
        if (!loaded || screen != "game") return@LaunchedEffect
        val current = game ?: return@LaunchedEffect
        if (current.mode != SpaceBattleEngine.GameMode.CAMPAIGN) return@LaunchedEffect
        if (current.gameOver || current.victory || current.stageComplete) return@LaunchedEffect
        resumeStage = current.stage
        graph.appState.put("space-battle-2", "${current.stage}|${difficulty.label}")
    }

    val active = screen == "game" && game != null && !paused &&
        game?.gameOver == false && game?.stageComplete == false &&
        game?.victory == false && game?.raceFinished == false
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000L).coerceIn(0L, 40L)
                    last = now
                    val current = game ?: return@withFrameNanos
                    val effective = if (control == SpaceBattleEngine.ControlStyle.TILT) {
                        input.copy(control = control, tiltX = tilt.first, tiltY = tilt.second, tiltActive = true)
                    } else {
                        input.copy(control = control)
                    }
                    val next = SpaceBattleEngine.step(current, effective.copy(fire = false), dt)
                    next.events.forEach { event -> play(sfxFor(event)) }
                    input = input.copy(fire = false)
                    game = next
                } else {
                    last = now
                }
            }
        }
    }

    LaunchedEffect(game?.gameOver, game?.victory, game?.raceFinished) {
        val current = game ?: return@LaunchedEffect
        if (current.gameOver && !recorded) {
            recorded = true
            resumeStage = null
            scope.launch(NonCancellable) {
                graph.games.record("space-battle-2", current.totalScore + current.score, "${current.difficulty.label}|stage ${current.stage}")
                graph.appState.clear("space-battle-2")
            }
        }
        if (current.victory && !recorded) {
            recorded = true
            resumeStage = null
            progress = progress.copy(wins = progress.wins + current.difficulty).let { p ->
                p.copy(highestStage = p.highestStage + (current.difficulty to SpaceBattleEngine.NUM_STAGES))
            }
            scope.launch(NonCancellable) {
                graph.games.record("space-battle-2", current.totalScore + current.score, "${current.difficulty.label}|victory")
                graph.appState.clear("space-battle-2")
            }
        }
        if (current.raceFinished && !recorded) {
            recorded = true
            val track = current.race
            val finish = current.raceTimeMs
            val previous = progress.ghosts[track]
            val ghost = SpaceBattleEngine.SbGhost(track, finish, current.ghostSamples)
            val beat = previous == null || finish <= previous.finishMs
            progress = progress.copy(
                racesBeaten = progress.racesBeaten.toMutableList().also { it[track - 1] = true },
                ghosts = if (beat) progress.ghosts + (track to ghost) else progress.ghosts,
                zaurium = progress.zaurium + 5,
            )
            scope.launch(NonCancellable) {
                graph.games.record("space-battle-2", current.score, "race $track|${finish}ms")
                graph.appState.clear("space-battle-2")
            }
        }
    }

    fun startStage(id: Int, carry: SpaceBattleEngine.SbState? = null) {
        val fresh = SpaceBattleEngine.newGame(
            stageId = min(id, SpaceBattleEngine.NUM_STAGES),
            difficulty = difficulty,
            build = progress.build,
            seed = System.currentTimeMillis().toInt(),
        )
        game = if (carry == null) {
            fresh
        } else {
            fresh.copy(
                totalScore = carry.totalScore + carry.score,
                lives = carry.lives,
                ammo = carry.ammo,
                zaurium = carry.zaurium,
                ship = fresh.ship.copy(primaryLevel = carry.ship.primaryLevel),
            )
        }
        recorded = false
        completedHandled = false
        paused = false
        screen = "game"
        input = SpaceBattleEngine.SbInput()
    }

    fun startRace(trackId: Int) {
        game = SpaceBattleEngine.newGame(
            stageId = 1,
            difficulty = difficulty,
            build = progress.build,
            seed = System.currentTimeMillis().toInt(),
            mode = SpaceBattleEngine.GameMode.RACE,
            raceId = trackId,
            ghost = progress.ghosts[trackId],
        )
        recorded = false
        completedHandled = false
        paused = false
        screen = "game"
        input = SpaceBattleEngine.SbInput()
    }

    fun advanceStage() {
        val current = game ?: return
        completedHandled = true
        startStage(current.stage + 1, current)
    }

    LaunchedEffect(game?.stageComplete, completedHandled) {
        val current = game ?: return@LaunchedEffect
        if (current.stageComplete && !completedHandled && current.stage < SpaceBattleEngine.NUM_STAGES) {
            completedHandled = true
            progress = progress.copy(
                zaurium = progress.zaurium + current.zaurium,
                highestStage = progress.highestStage + (current.difficulty to maxOf(current.stage + 1, progress.highestStage[current.difficulty] ?: 1)),
            )
        }
    }

    when (screen) {
        "menu" -> {
            SpaceBattleMenu(
                progress = progress,
                difficulty = difficulty,
                control = control,
                resumeStage = resumeStage,
                best = scores.firstOrNull()?.score ?: 0,
                onDifficulty = { difficulty = it },
                onControl = { control = it },
                onPlay = { stagePage = ((progress.highestStage[difficulty] ?: 1) - 1) / 5; screen = "stages" },
                onRace = { screen = "races" },
                onCustomize = { screen = "customize" },
                onResume = { resumeStage?.let { startStage(it) } },
                onHelp = { screen = "help" },
            )
            return
        }

        "help" -> {
            SpaceBattleHelp(onBack = { screen = "menu" })
            return
        }

        "stages" -> {
            SpaceBattleStageSelect(
                difficulty = difficulty,
                progress = progress,
                scores = scores,
                page = stagePage,
                onPage = { stagePage = it },
                onPick = { startStage(it) },
                onBack = { screen = "menu" },
            )
            return
        }

        "races" -> {
            SpaceBattleRaceSelect(
                progress = progress,
                onPick = { startRace(it) },
                onBack = { screen = "menu" },
            )
            return
        }

        "customize" -> {
            SpaceBattleCustomize(
                progress = progress,
                onProgress = { progress = it },
                onBack = { screen = "menu" },
            )
            return
        }
    }

    val current = game ?: return
    SpaceBattleGameScreen(
        state = current,
        colors = colors,
        control = control,
        onInput = { input = it },
        onPause = { paused = true },
        onFire = {
            game = SpaceBattleEngine.step(
                current,
                input.copy(control = control, fire = true),
                20,
            )
            input = input.copy(fire = false)
        },
        paused = paused,
        onResume = { paused = false },
        onRetry = { if (current.mode == SpaceBattleEngine.GameMode.RACE) startRace(current.race) else startStage(current.stage) },
        onNext = { advanceStage() },
        onMenu = { screen = "menu"; paused = false },
    )
}

private fun sfxFor(event: SpaceBattleEngine.SbEvent): String = when (event) {
    SpaceBattleEngine.SbEvent.SHOOT -> "tick"
    SpaceBattleEngine.SbEvent.LAUNCH -> "whoosh"
    SpaceBattleEngine.SbEvent.ENEMY_HIT -> "hit"
    SpaceBattleEngine.SbEvent.EXPLODE -> "explode"
    SpaceBattleEngine.SbEvent.PLAYER_HIT -> "hit"
    SpaceBattleEngine.SbEvent.PLAYER_DIE -> "lose"
    SpaceBattleEngine.SbEvent.PICKUP -> "coin"
    SpaceBattleEngine.SbEvent.EXTRA_LIFE -> "score"
    SpaceBattleEngine.SbEvent.BOSS_DIE -> "win"
    SpaceBattleEngine.SbEvent.GAME_OVER -> "lose"
    SpaceBattleEngine.SbEvent.STAGE_CLEAR -> "win"
    SpaceBattleEngine.SbEvent.RACE_WIN -> "win"
    SpaceBattleEngine.SbEvent.RACE_LOSE -> "lose"
    SpaceBattleEngine.SbEvent.UI -> "select"
}

@Composable
private fun rememberTilt(): Pair<Float, Float> {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(0f to 0f) }
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var listener: SensorEventListener? = null
        if (manager != null && sensor != null) {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    tilt = x to y
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            listener?.let { manager?.unregisterListener(it) }
            tilt = 0f to 0f
        }
    }
    return tilt
}

@Composable
private fun SpaceBattleMenu(
    progress: SpaceBattleEngine.SbProgress,
    difficulty: SpaceBattleEngine.Difficulty,
    control: SpaceBattleEngine.ControlStyle,
    resumeStage: Int?,
    best: Int,
    onDifficulty: (SpaceBattleEngine.Difficulty) -> Unit,
    onControl: (SpaceBattleEngine.ControlStyle) -> Unit,
    onPlay: () -> Unit,
    onRace: () -> Unit,
    onCustomize: () -> Unit,
    onResume: () -> Unit,
    onHelp: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "space battle 2") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DoradoTokens.EDGE.dp),
        ) {
            if (resumeStage != null) {
                SbButton("continue stage $resumeStage", Modifier.fillMaxWidth()) { onResume() }
                Spacer(Modifier.height(4.dp))
            }
            SbButton("play campaign", Modifier.fillMaxWidth()) { onPlay() }
            Spacer(Modifier.height(4.dp))
            SbButton("race", Modifier.fillMaxWidth()) { onRace() }
            Spacer(Modifier.height(4.dp))
            SbButton("customize ship", Modifier.fillMaxWidth()) { onCustomize() }
            Spacer(Modifier.height(4.dp))
            SbButton("how to play", Modifier.fillMaxWidth()) { onHelp() }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SpaceBattleEngine.Difficulty.entries.forEach { tier ->
                    SbButton(tier.label, Modifier.weight(1f), active = tier == difficulty) { onDifficulty(tier) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SbButton("touch", Modifier.weight(1f), active = control == SpaceBattleEngine.ControlStyle.TOUCH_ABSOLUTE) {
                    onControl(SpaceBattleEngine.ControlStyle.TOUCH_ABSOLUTE)
                }
                SbButton("tilt", Modifier.weight(1f), active = control == SpaceBattleEngine.ControlStyle.TILT) {
                    onControl(SpaceBattleEngine.ControlStyle.TILT)
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "zaurium ${progress.zaurium} · best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun SpaceBattleHelp(onBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "space battle 2 · help", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "your ship auto-fires. drag to steer, tap fire for the secondary weapon.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "kill drops give shields, weapon upgrades, ammo, zaurium, extra lives and race time.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "boss weak points only take damage while the boss attacks. racing never kills.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            SbButton("back") { onBack() }
        }
    }
}

@Composable
private fun SpaceBattleStageSelect(
    difficulty: SpaceBattleEngine.Difficulty,
    progress: SpaceBattleEngine.SbProgress,
    scores: List<com.heretek.dorado_hd.data.db.GameScoreEntity>,
    page: Int,
    onPage: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val highest = progress.highestStage[difficulty] ?: 1
    DetailScaffold(title = "space battle 2 · ${difficulty.label} · stages", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            for (row in 0 until 5) {
                val id = page * 5 + row + 1
                if (id > SpaceBattleEngine.NUM_STAGES) break
                val unlocked = id <= highest
                val best = scores.firstOrNull { it.meta == "${difficulty.label}|stage $id" }?.score ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .height(30.dp)
                            .width(44.dp)
                            .background(if (unlocked) colors.tile else colors.elevated)
                            .border(0.5.dp, if (unlocked) colors.border else colors.elevated)
                            .appTap(label = "stage $id", enabled = unlocked) { onPick(id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (unlocked) "$id" else "—",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (unlocked) colors.textPrimary else colors.textInactive),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        BasicText(
                            text = "stage $id · ${SpaceBattleEngine.stage(id).world + 1}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        BasicText(
                            text = if (best > 0) "best $best" else if (unlocked) "not cleared" else "locked",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbButton("prev") { onPage((page - 1).coerceAtLeast(0)) }
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = "page ${page + 1}/2",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(6.dp))
                SbButton("next") { onPage((page + 1).coerceAtMost(1)) }
            }
        }
    }
}

@Composable
private fun SpaceBattleRaceSelect(
    progress: SpaceBattleEngine.SbProgress,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "space battle 2 · races", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            for (track in SpaceBattleEngine.races) {
                val ghost = progress.ghosts[track.id]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .height(28.dp)
                            .width(44.dp)
                            .background(colors.tile)
                            .border(0.5.dp, colors.border)
                            .appTap(label = track.name) { onPick(track.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = "${track.id}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        BasicText(
                            text = track.name,
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                        )
                        BasicText(
                            text = ghost?.let { "ghost ${it.finishMs / 1000}.${(it.finishMs % 1000).toString().padStart(3, '0')}" }
                                ?: "no ghost",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SpaceBattleCustomize(
    progress: SpaceBattleEngine.SbProgress,
    onProgress: (SpaceBattleEngine.SbProgress) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    var category by remember { mutableStateOf(0) }
    var part by remember { mutableStateOf(0) }
    val build = progress.build
    DetailScaffold(title = "space battle 2 · customize", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SpaceBattleEngine.CATEGORY_LABELS.forEachIndexed { index, label ->
                    SbButton(label, Modifier.weight(1f), active = index == category) {
                        category = index
                        part = build.parts[index]
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SbButton("prev") { part = (part - 1 + SpaceBattleEngine.PARTS_PER_CATEGORY) % SpaceBattleEngine.PARTS_PER_CATEGORY }
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = "part ${part + 1}/10",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.weight(1f))
                SbButton("next") { part = (part + 1) % SpaceBattleEngine.PARTS_PER_CATEGORY }
            }
            Spacer(Modifier.height(4.dp))
            val values = SpaceBattleEngine.PART_VALUES[category][part]
            val owned = SpaceBattleEngine.owns(progress, category, part)
            val equipped = build.parts[category] == part
            val unlocked = SpaceBattleEngine.isPartUnlocked(part, progress.wins)
            val cost = values[4]
            val preview = build.withPart(category, part)
            Column(Modifier.fillMaxWidth().background(colors.elevated).border(0.5.dp, colors.border).padding(8.dp)) {
                BasicText(
                    text = "${SpaceBattleEngine.CATEGORY_LABELS[category]} ${part + 1}${if (equipped) " · equipped" else ""}${if (!owned) " · cost $cost" else ""}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.height(4.dp))
                for (stat in 0 until 4) {
                    val currentValue = SpaceBattleEngine.shipStat(build, stat)
                    val previewValue = SpaceBattleEngine.shipStat(preview, stat)
                    BasicText(
                        text = "${SpaceBattleEngine.STAT_LABELS[stat]} $currentValue${if (previewValue != currentValue) " -> $previewValue" else ""}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        !unlocked -> BasicText(
                            text = "locked · beat ${if (part in 5..6) "normal" else if (part in 7..8) "hard" else "elite"}",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = DoradoAccent.PINK.primary),
                        )

                        !owned -> SbButton(
                            "buy · ${cost}z",
                            enabled = progress.zaurium >= cost,
                        ) { onProgress(SpaceBattleEngine.buy(progress, category, part)) }

                        !equipped -> SbButton("equip") { onProgress(SpaceBattleEngine.equip(progress, category, part)) }
                        else -> BasicText(
                            text = "installed",
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "zaurium ${progress.zaurium} · pick a palette for the hull",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (palette in 0 until SpaceBattleEngine.PALETTES) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(24.dp)
                            .background(sbPaletteColor(palette))
                            .border(if (build.colors[category] == palette) 1.5.dp else 0.5.dp, colors.border)
                            .appTap(label = "palette ${palette + 1}") {
                                onProgress(progress.copy(build = build.withColor(category, palette)))
                            },
                    )
                }
            }
        }
    }
}

private fun sbPaletteColor(palette: Int): Color = when (palette) {
    0 -> Color.White
    1 -> DoradoAccent.ORANGE.primary
    2 -> DoradoAccent.LIME.primary
    3 -> DoradoAccent.PURPLE.primary
    else -> DoradoAccent.PINK.primary
}

@Composable
private fun SbButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(28.dp)
            .background(if (active) colors.tilePressed else colors.tile)
            .border(0.5.dp, if (active) colors.accent else colors.border)
            .appTap(label = label, enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp),
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

@Composable
private fun SpaceBattleGameScreen(
    state: SpaceBattleEngine.SbState,
    colors: DoradoColors,
    control: SpaceBattleEngine.ControlStyle,
    onInput: (SpaceBattleEngine.SbInput) -> Unit,
    onPause: () -> Unit,
    onFire: () -> Unit,
    paused: Boolean,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onMenu: () -> Unit,
) {
    DetailScaffold(title = "space battle 2", onBack = onMenu) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "score ${state.score}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    text = if (state.mode == SpaceBattleEngine.GameMode.RACE) {
                        "race ${state.race} · ${formatTime(state.raceTimeMs)}"
                    } else {
                        "stage ${state.stage}/10"
                    },
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                )
                Spacer(Modifier.weight(1f))
                BasicText(
                    text = "lives ${state.lives} · ammo ${state.ammo} · z ${state.zaurium}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
                Spacer(Modifier.width(6.dp))
                SbButton("fire", enabled = state.ammo > 0 && !paused) { onFire() }
                Spacer(Modifier.width(4.dp))
                SbButton("pause") { onPause() }
            }
            Spacer(Modifier.height(3.dp))
            if (state.mode == SpaceBattleEngine.GameMode.CAMPAIGN) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .appDescription(
                        if (state.mode == SpaceBattleEngine.GameMode.RACE) {
                            "score ${state.score}, race ${state.race}, lives ${state.lives}, ammo ${state.ammo}"
                        } else {
                            "score ${state.score}, stage ${state.stage} of 10, lives ${state.lives}, ammo ${state.ammo}"
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    BasicText(
                        text = "shields",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f).height(6.dp).background(colors.elevated).border(0.5.dp, colors.border)) {
                        val fraction = (state.ship.shields / state.ship.maxShields).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(colors.accent))
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "${state.ship.shields.toInt()}/${state.ship.maxShields.toInt()}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
                val boss = state.boss
                if (boss != null) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.elevated).border(0.5.dp, colors.border)) {
                        Box(
                            Modifier
                                .fillMaxWidth((boss.hp / boss.maxHp).coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(if (boss.exposed) DoradoAccent.PINK.primary else colors.textInactive),
                        )
                    }
                }
            } else {
                val track = SpaceBattleEngine.race(state.race)
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(colors.elevated).border(0.5.dp, colors.border)) {
                    Box(
                        Modifier
                            .fillMaxWidth((state.distancePx / track.lengthPx).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(if (state.overdriveMs > 0L) DoradoAccent.ORANGE.bright else colors.accent),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val canvasW = constraints.maxWidth.toFloat()
                val canvasH = constraints.maxHeight.toFloat()
                val viewport = SpaceBattleEngine.viewportFor(canvasW, canvasH)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(control, paused, viewport) {
                            if (paused) return@pointerInput
                            fun touch(x: Float, y: Float) = SpaceBattleEngine.SbInput(
                                touching = true,
                                touchX = viewport.viewX(x),
                                touchY = viewport.viewY(y),
                            )
                            detectDragGestures(
                                onDragStart = { offset -> onInput(touch(offset.x, offset.y)) },
                                onDrag = { change, _ -> onInput(touch(change.position.x, change.position.y)) },
                                onDragEnd = { onInput(SpaceBattleEngine.SbInput()) },
                                onDragCancel = { onInput(SpaceBattleEngine.SbInput()) },
                            )
                        }
                        .appDescription(
                            if (state.mode == SpaceBattleEngine.GameMode.RACE) {
                                "space battle race scene, score ${state.score}, race ${state.race}"
                            } else {
                                "space battle scene, score ${state.score}, stage ${state.stage}"
                            },
                        ),
                ) {
                    drawSpaceBattleScene(state, colors, canvasW, canvasH)
                }
                if (paused || state.gameOver || state.stageComplete || state.victory || state.raceFinished) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier
                                .background(colors.elevated)
                                .border(0.5.dp, colors.border)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                text = when {
                                    paused -> "paused"
                                    state.victory -> "victory"
                                    state.stageComplete -> "stage clear"
                                    state.raceFinished -> if (state.raceWon == true) "race won" else "race over"
                                    else -> "game over"
                                },
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                text = when {
                                    state.raceFinished -> "time ${formatTime(state.raceTimeMs)}" +
                                        (state.ghost?.let { " · ghost ${formatTime(it.finishMs)}" } ?: "")
                                    state.stageComplete || state.victory -> "total ${state.totalScore + state.score}"
                                    else -> "score ${state.score} · stage ${state.stage}"
                                },
                                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (paused) {
                                    SbButton("resume") { onResume() }
                                    SbButton("retry") { onRetry() }
                                }
                                if (state.stageComplete) {
                                    SbButton("next stage") { onNext() }
                                    SbButton("replay") { onRetry() }
                                }
                                if (state.gameOver || state.victory || state.raceFinished) {
                                    SbButton("retry") { onRetry() }
                                }
                                SbButton("main menu") { onMenu() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String = "${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}"

private fun DrawScope.drawSpaceBattleScene(
    state: SpaceBattleEngine.SbState,
    colors: DoradoColors,
    canvasW: Float,
    canvasH: Float,
) {
    // Fit by the smaller ratio so the full 272x480 view stays on screen; a
    // width-only scale lets `oy` go negative on short/wide canvases.
    val vp = SpaceBattleEngine.viewportFor(canvasW, canvasH)
    val scale = vp.scale
    val viewW = SpaceBattleEngine.VIEW_W * scale
    val viewH = SpaceBattleEngine.VIEW_H * scale
    drawRect(colors.background)
    drawRect(colors.background, Offset(vp.ox, vp.oy), Size(viewW, viewH))

    val world = if (state.mode == SpaceBattleEngine.GameMode.CAMPAIGN) {
        (SpaceBattleEngine.stage(state.stage).world % 5)
    } else {
        state.race
    }
    val worldColor = when (world % 5) {
        0 -> DoradoAccent.LIME
        1 -> DoradoAccent.CYAN
        2 -> DoradoAccent.PURPLE
        3 -> DoradoAccent.ORANGE
        else -> DoradoAccent.PINK
    }
    drawCircle(worldColor.primary.copy(alpha = 0.16f), viewW * 0.36f, Offset(vp.ox + viewW * 0.2f, vp.oy + viewH * 0.24f))

    // Starfield with a slow vertical drift.
    val scroll = ((state.stageTimeMs / 60L) + state.distancePx.toLong() / 8L) % 480L
    for (i in 0 until 44) {
        val sx = vp.ox + ((i * 173 + 41) % 480) / 480f * viewW
        val sy = (((i * 97 + 13) % 480) + scroll) % 480L
        val alpha = if (i % 5 == 0) 0.55f else 0.28f
        drawCircle(Color.White.copy(alpha = alpha), (1.1f + (i % 3) * 0.5f) * scale, Offset(sx, vp.oy + sy * scale))
    }

    fun sx(worldX: Float): Float = vp.screenX(worldX, state.panningOffset)
    fun sy(worldY: Float): Float = vp.screenY(worldY)

    if (state.mode == SpaceBattleEngine.GameMode.RACE) {
        val track = SpaceBattleEngine.race(state.race)
        for (gate in track.gates) {
            val ahead = gate - state.distancePx
            if (ahead < 0f || ahead > 420f) continue
            val y = sy(120f + ahead * 0.7f)
            drawRect(DoradoAccent.CYAN.bright.copy(alpha = 0.7f), Offset(vp.ox, y), Size(viewW, 4f * scale))
        }
        val finishAhead = track.lengthPx - state.distancePx
        if (finishAhead in 0f..420f) {
            val y = sy(120f + finishAhead * 0.7f)
            for (x in 0 until 10) {
                drawRect(
                    if (x % 2 == 0) Color.White else colors.background,
                    Offset(vp.ox + x * viewW / 10f, y),
                    Size(viewW / 10f, 10f * scale),
                )
            }
        }
    }

    for (pickup in state.pickups) {
        val color = when (pickup.kind) {
            SpaceBattleEngine.PowerUpKind.SHIELDS -> DoradoAccent.CYAN.bright
            SpaceBattleEngine.PowerUpKind.UPGRADE -> DoradoAccent.LIME.bright
            SpaceBattleEngine.PowerUpKind.AMMO -> DoradoAccent.ORANGE.bright
            SpaceBattleEngine.PowerUpKind.ZAURIUM -> DoradoAccent.PURPLE.bright
            SpaceBattleEngine.PowerUpKind.EXTRA_LIFE -> DoradoAccent.PINK.bright
            SpaceBattleEngine.PowerUpKind.TIME -> Color.White
        }
        drawRect(color, Offset(sx(pickup.x) - 5f * scale, sy(pickup.y) - 5f * scale), Size(10f * scale, 10f * scale))
    }

    for (enemy in state.enemies) {
        val x = sx(enemy.x)
        val y = sy(enemy.y)
        val r = enemy.kind.radius * scale
        when (enemy.kind) {
            SpaceBattleEngine.EnemyKind.DRONE_MINDLESS,
            SpaceBattleEngine.EnemyKind.DRONE_CHARGER,
            SpaceBattleEngine.EnemyKind.DRONE_SEEKER,
            -> {
                val color = when (enemy.kind) {
                    SpaceBattleEngine.EnemyKind.DRONE_MINDLESS -> DoradoAccent.PINK.primary
                    SpaceBattleEngine.EnemyKind.DRONE_CHARGER -> DoradoAccent.ORANGE.primary
                    else -> DoradoAccent.PURPLE.primary
                }
                drawCircle(color, r, Offset(x, y))
                drawCircle(colors.background, r * 0.35f, Offset(x, y))
            }

            SpaceBattleEngine.EnemyKind.TURRET_PHOTON,
            SpaceBattleEngine.EnemyKind.TURRET_ROCKET,
            -> {
                drawRect(DoradoAccent.CYAN.primary, Offset(x - r, y - r), Size(r * 2f, r * 2f))
                drawRect(colors.background, Offset(x - r * 0.3f, y - r * 0.3f), Size(r * 0.6f, r * 0.6f))
            }

            else -> {
                drawCircle(colors.textInactive, r, Offset(x, y))
                drawCircle(colors.border, r * 0.55f, Offset(x + r * 0.25f, y - r * 0.2f))
            }
        }
    }

    val boss = state.boss
    if (boss != null) {
        val x = sx(boss.x)
        val y = sy(boss.y)
        val r = SpaceBattleEngine.bossRadius(boss.kind) * scale
        drawRect(
            if (boss.exposed) DoradoAccent.PINK.primary else colors.tilePressed,
            Offset(x - r, y - r * 0.7f),
            Size(r * 2f, r * 1.4f),
        )
        drawRect(colors.background, Offset(x - r * 0.4f, y - r * 0.2f), Size(r * 0.8f, r * 0.4f))
        if (boss.exposed) drawCircle(DoradoAccent.ORANGE.bright, r * 0.22f, Offset(x, y))
    }

    for (bolt in state.bolts) {
        val x = sx(bolt.x)
        val y = sy(bolt.y)
        val color = when {
            !bolt.friendly -> DoradoAccent.PINK.bright
            bolt.kind == 3 -> DoradoAccent.CYAN.bright
            bolt.kind == 5 -> DoradoAccent.ORANGE.bright
            else -> DoradoAccent.LIME.bright
        }
        drawRect(color, Offset(x - 2f * scale, y - 5f * scale), Size(4f * scale, 10f * scale))
    }

    val ship = state.ship
    val shipX = sx(ship.x)
    val shipY = sy(ship.y)
    if (ship.alive) {
        val blink = ship.invincibleMs > 0L && (ship.invincibleMs / 120L) % 2L == 0L
        if (!blink) {
            val path = Path()
            path.moveTo(shipX, shipY - 12f * scale)
            path.lineTo(shipX - 10f * scale, shipY + 8f * scale)
            path.lineTo(shipX + 10f * scale, shipY + 8f * scale)
            path.close()
            drawPath(path, sbPaletteColor(state.build.colors[3]))
            drawRect(DoradoAccent.ORANGE.bright, Offset(shipX - 3f * scale, shipY + 8f * scale), Size(6f * scale, 6f * scale))
        }
        if (ship.shields > ship.maxShields) {
            drawCircle(DoradoAccent.CYAN.bright.copy(alpha = 0.35f), 18f * scale, Offset(shipX, shipY))
        }
    }
}

