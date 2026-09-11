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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.engine3d.Camera3d
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.Mat4
import com.heretek.dorado_hd.ui.apps.engine3d.MeshData
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3dView
import com.heretek.dorado_hd.ui.apps.engine3d.SceneNode
import com.heretek.dorado_hd.ui.apps.engine3d.TextureData
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.engine3d.nodeAt
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

private const val PGR_SLUG = "pgr-ferrari-edition"
private const val PGR_SELECTED_KEY = "pgr-ferrari-edition.selected"

private enum class PgrScreen { MENU, QUICK, CAREER, GARAGE, RECORDS, OPTIONS, RACE, RESULT }

/**
 * PGR: Ferrari Edition — 3D street racing over engine3d.
 *
 * The screen layer is deliberately thin: all rules and simulation live in
 * [PgrEngine]; this file builds procedural track geometry and city dressing,
 * runs the fixed-step accumulator from the frame clock, and renders the HUD
 * with design tokens.
 */
@Composable
fun PgrApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    val scene = remember { Scene3d() }
    val assets = remember { PgrAssets() }
    val engineSamples = remember { HashMap<Int, DoubleArray>() }

    var screen by remember { mutableStateOf(PgrScreen.MENU) }
    var profile by remember { mutableStateOf(PgrEngine.PgrProfile()) }
    var selectedCarId by remember { mutableStateOf(PgrEngine.PgrCars.starter.id) }
    var loaded by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(true) }
    var tiltEnabled by remember { mutableStateOf(false) }
    var tiltSensitivity by remember { mutableStateOf(0.85f) }
    var autoGas by remember { mutableStateOf(true) }

    var race by remember { mutableStateOf<PgrEngine.PgrRaceState?>(null) }
    var trackVisual by remember { mutableStateOf<PgrTrackVisual?>(null) }
    var input by remember { mutableStateOf(PgrEngine.PgrInput()) }
    var paused by remember { mutableStateOf(false) }
    var pendingEvent by remember { mutableStateOf<PgrEngine.PgrEventDef?>(null) }
    var outcome by remember { mutableStateOf<PgrEngine.PgrEventOutcome?>(null) }
    var lastResult by remember { mutableStateOf<PgrEngine.PgrEventResult?>(null) }
    var recorded by remember { mutableStateOf(false) }

    // Quick-race selections.
    var quickCar by remember { mutableStateOf(PgrEngine.PgrCars.starter.id) }
    var quickTrack by remember { mutableStateOf(0) }
    var quickDifficulty by remember { mutableStateOf(PgrEngine.PgrDifficulty.MEDIUM) }
    var quickLaps by remember { mutableStateOf(3) }
    var careerIndex by remember { mutableStateOf(0) }
    var garageIndex by remember { mutableStateOf(0) }

    val tilt = rememberTilt(
        enabled = screen == PgrScreen.RACE && tiltEnabled && !paused,
        smoothing = 0.82f,
    )

    LaunchedEffect(Unit) {
        PgrEngine.decodeProfile(graph.appState.get(PGR_SLUG))?.let { profile = it }
        graph.appState.get(PGR_SELECTED_KEY)?.let { selectedCarId = it }
        loaded = true
    }

    LaunchedEffect(profile, selectedCarId, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put(PGR_SLUG, PgrEngine.encodeProfile(profile))
        graph.appState.put(PGR_SELECTED_KEY, selectedCarId)
    }

    fun ownedCars(): List<PgrEngine.PgrCarDef> =
        PgrEngine.PgrCars.all.filter { profile.cars.contains(it.id) || it.creditCost == 0 }

    fun startRace(event: PgrEngine.PgrEventDef?) {
        val track = if (event != null) PgrEngine.PgrTracks.byId(event.trackId) else PgrEngine.PgrTracks.byIndex(quickTrack)
        val carId = if (event != null) selectedCarId else quickCar
        val difficulty = event?.difficulty ?: quickDifficulty
        val laps = event?.laps ?: quickLaps
        trackVisual = assets.visual(track)
        race = PgrEngine.newRace(
            trackId = track.def.id,
            playerCarId = carId,
            difficulty = difficulty,
            laps = laps,
            eventId = event?.id,
            gateCount = event?.gateCount ?: 0,
            seed = System.currentTimeMillis().toInt(),
        )
        pendingEvent = event
        outcome = null
        lastResult = null
        recorded = false
        paused = false
        input = PgrEngine.PgrInput(autoThrottle = autoGas)
        screen = PgrScreen.RACE
    }

    // Fixed-step race loop driven by the frame clock.
    LaunchedEffect(screen, paused) {
        if (screen != PgrScreen.RACE || paused) return@LaunchedEffect
        var last = 0L
        var lastCountdown = -1
        var lastGear = -1
        var skidCooldown = 0f
        var lastStash = 0
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                } else {
                    val dtMs = ((now - last) / 1_000_000L).coerceIn(0L, 66L)
                    last = now
                    val current = race ?: return@withFrameNanos
                    if (current.phase == PgrEngine.PgrPhase.RACING) {
                        val steer = if (tiltEnabled && tilt.value.available) {
                            PgrEngine.tiltToSteer(tilt.value.rollDeg, tiltSensitivity)
                        } else {
                            input.steer
                        }
                        val frameInput = input.copy(steer = steer, autoThrottle = autoGas || input.autoThrottle)
                        val next = PgrEngine.stepRace(current, frameInput, dtMs)
                        race = next
                        val player = next.racers.firstOrNull { it.isPlayer }
                        if (soundOn && player != null) {
                            if (player.car.gear != lastGear) {
                                if (lastGear != -1) bank.play("click")
                                lastGear = player.car.gear
                            }
                            skidCooldown -= dtMs / 1000f
                            if (player.car.skidRear && skidCooldown <= 0f) {
                                bank.play("whoosh")
                                skidCooldown = 0.7f
                            }
                            if (player.kudos.stash.size > lastStash) {
                                bank.play("coin")
                                lastStash = player.kudos.stash.size
                            }
                            if (player.kudos.failedFlash > 0f && player.kudos.failedFlash > 1.1f) bank.play("error")
                            if (player.car.hitWall) bank.play("hit")
                        }
                        if (next.phase == PgrEngine.PgrPhase.FINISHED && !recorded) {
                            recorded = true
                            val result = PgrEngine.raceResult(next)
                            lastResult = result
                            val (nextProfile, raceOutcome) = PgrEngine.applyOutcome(
                                profile,
                                pendingEvent,
                                result,
                                trackId = next.trackId,
                            )
                            profile = nextProfile
                            outcome = raceOutcome
                            val score = result.kudos
                            val meta = listOf(
                                pendingEvent?.id ?: "quick",
                                next.trackId,
                                next.racers.firstOrNull { it.isPlayer }?.carId ?: selectedCarId,
                                next.difficulty.label,
                                result.timeMs,
                                result.position,
                            ).joinToString("|")
                            scope.launch { graph.games.record(PGR_SLUG, score, meta) }
                            bank.play(if (result.position == 1) "win" else "lose")
                            screen = PgrScreen.RESULT
                        }
                    } else {
                        val step = ceil(current.countdownTicks / PgrEngine.TICS_PER_SECOND.toFloat()).toInt()
                        if (step != lastCountdown) {
                            lastCountdown = step
                            if (soundOn) bank.play(if (step <= 0) "score" else "select")
                        }
                        race = PgrEngine.stepRace(current, null, dtMs)
                    }
                }
            }
        }
    }

    // Engine loop: one short banded sample per write; the audio stream paces it.
    LaunchedEffect(screen, paused) {
        if (screen != PgrScreen.RACE || paused || !soundOn) return@LaunchedEffect
        while (true) {
            val current = race ?: break
            val player = current.racers.firstOrNull { it.isPlayer } ?: break
            val def = PgrEngine.PgrCars.byId(player.carId)
            val band = PgrEngine.engineBand(player.car.filteredRpm, def)
            val sample = engineSamples.getOrPut(band) { engineBandSample(band, synth.sampleRate) }
            synth.playSamples(sample)
        }
    }

    // Rebuild the 3D scene whenever the race state changes.
    DisposableEffect(race, trackVisual, paused) {
        val current = race
        val visual = trackVisual
        scene.clear()
        if (current != null && visual != null && screen == PgrScreen.RACE) {
            val track = PgrEngine.PgrTracks.byId(current.trackId)
            scene.backgroundColor = color4(track.def.city.skyArgb)
            scene.fogDensity = track.def.city.fogDensity
            scene.ambient = 0.42f
            scene.lightDirection = Vec3(-0.35f, -1f, 0.25f)
            scene.addAll(visual.staticNodes)
            for (racer in current.racers) {
                val def = PgrEngine.PgrCars.byId(racer.carId)
                addCarNodes(scene, assets, racer, def)
            }
            val player = current.racers.firstOrNull { it.isPlayer } ?: current.racers.first()
            scene.camera = chaseCamera(player)
        }
        onDispose { }
    }

    if (screen != PgrScreen.RACE) {
        when (screen) {
            PgrScreen.MENU -> PgrMenu(
                title = "pgr: ferrari edition",
                profile = profile,
                onQuick = { quickCar = selectedCarId; screen = PgrScreen.QUICK },
                onCareer = { screen = PgrScreen.CAREER },
                onGarage = { screen = PgrScreen.GARAGE },
                onRecords = { screen = PgrScreen.RECORDS },
                onOptions = { screen = PgrScreen.OPTIONS },
            )

            PgrScreen.QUICK -> PgrQuickRace(
                profile = profile,
                carId = quickCar,
                trackIndex = quickTrack,
                difficulty = quickDifficulty,
                laps = quickLaps,
                tiltEnabled = tiltEnabled,
                autoGas = autoGas,
                onCar = { quickCar = it },
                onTrack = { quickTrack = it },
                onDifficulty = { quickDifficulty = it },
                onLaps = { quickLaps = it },
                onTilt = { tiltEnabled = it },
                onAutoGas = { autoGas = it },
                onStart = { startRace(null) },
                onBack = { screen = PgrScreen.MENU },
            )

            PgrScreen.CAREER -> PgrCareer(
                profile = profile,
                selected = careerIndex,
                onSelect = { careerIndex = it },
                onRace = { startRace(it) },
                onBack = { screen = PgrScreen.MENU },
            )

            PgrScreen.GARAGE -> PgrGarage(
                profile = profile,
                selected = garageIndex,
                selectedCarId = selectedCarId,
                onSelect = { garageIndex = it },
                onChoose = { selectedCarId = it; bank.play("select") },
                onBuy = { carId ->
                    val result = PgrEngine.purchase(profile, carId)
                    if (result.ok) {
                        profile = result.profile
                        selectedCarId = carId
                        bank.play("coin")
                    } else {
                        bank.play("error")
                    }
                },
                onBack = { screen = PgrScreen.MENU },
            )

            PgrScreen.RECORDS -> PgrRecords(
                profile = profile,
                onBack = { screen = PgrScreen.MENU },
            )

            PgrScreen.OPTIONS -> PgrOptions(
                tiltEnabled = tiltEnabled,
                sensitivity = tiltSensitivity,
                autoGas = autoGas,
                soundOn = soundOn,
                onTilt = { tiltEnabled = it },
                onSensitivity = { tiltSensitivity = it },
                onAutoGas = { autoGas = it },
                onSound = { soundOn = it },
                onBack = { screen = PgrScreen.MENU },
            )

            PgrScreen.RESULT -> PgrResult(
                event = pendingEvent,
                result = lastResult,
                outcome = outcome,
                onAgain = { startRace(pendingEvent) },
                onMenu = { screen = PgrScreen.MENU },
            )

            PgrScreen.RACE -> Unit
        }
        return
    }

    val current = race ?: return
    val player = current.racers.firstOrNull { it.isPlayer } ?: current.racers.first()
    DetailScaffold(title = "pgr: ferrari edition", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Scene3dView(scene = scene, modifier = Modifier.fillMaxSize())

            // Swipe up/down on the scene pulls the handbrake (spec §4).
            var swipe by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(current.trackId) {
                        detectDragGestures(
                            onDragStart = { swipe = 0f },
                            onDragEnd = {
                                input = input.copy(handbrake = false)
                                swipe = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                swipe += amount.y + amount.x
                                input = input.copy(handbrake = swipe > 20f || swipe < -20f)
                            },
                        )
                    },
            )

            PgrHud(
                race = current,
                player = player,
                modifier = Modifier.fillMaxSize(),
            )

            PgrControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                onSteer = { input = input.copy(steer = it) },
                onThrottle = { input = input.copy(throttle = it) },
                onBrake = { input = input.copy(brake = it) },
                onHandbrake = { input = input.copy(handbrake = it) },
            )

            if (current.phase == PgrEngine.PgrPhase.COUNTDOWN) {
                val seconds = ceil(current.countdownTicks / PgrEngine.TICS_PER_SECOND.toFloat()).toInt()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EdgeCropText(
                        text = if (seconds <= 0) "go" else seconds.toString(),
                        fontSize = DoradoTokens.TYPE_MENU_ITEM.dp,
                        color = colors.accentBright,
                    )
                }
            }

            if (paused) {
                PgrPauseOverlay(
                    onResume = { paused = false },
                    onRestart = { startRace(pendingEvent) },
                    onQuit = { race = null; paused = false; screen = PgrScreen.MENU },
                )
            }
        }
    }
}

// ======================================================================
// Menus
// ======================================================================

@Composable
private fun PgrMenu(
    title: String,
    profile: PgrEngine.PgrProfile,
    onQuick: () -> Unit,
    onCareer: () -> Unit,
    onGarage: () -> Unit,
    onRecords: () -> Unit,
    onOptions: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = title) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = "credits ${profile.credits} · kudos ${profile.kudosBank} · medals ${profile.medalCount}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(6.dp))
            PgrRow("quick race", "car · track · difficulty", onClick = onQuick)
            PgrRow("career", "12 events · medals unlock the field", onClick = onCareer)
            PgrRow("garage", "${profile.cars.size} cars owned", onClick = onGarage)
            PgrRow("records", "lap, race and kudos bests", onClick = onRecords)
            PgrRow("options", "tilt, auto-gas, sound", onClick = onOptions)
        }
    }
}

@Composable
private fun PgrQuickRace(
    profile: PgrEngine.PgrProfile,
    carId: String,
    trackIndex: Int,
    difficulty: PgrEngine.PgrDifficulty,
    laps: Int,
    tiltEnabled: Boolean,
    autoGas: Boolean,
    onCar: (String) -> Unit,
    onTrack: (Int) -> Unit,
    onDifficulty: (PgrEngine.PgrDifficulty) -> Unit,
    onLaps: (Int) -> Unit,
    onTilt: (Boolean) -> Unit,
    onAutoGas: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val cars = PgrEngine.PgrCars.all.filter { profile.cars.contains(it.id) || it.creditCost == 0 }
    val car = PgrEngine.PgrCars.byId(carId)
    val track = PgrEngine.PgrTracks.byIndex(trackIndex)
    DetailScaffold(title = "pgr: quick race", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PgrSelector("car", car.name) { dir ->
                val index = cars.indexOfFirst { it.id == carId }.coerceAtLeast(0)
                onCar(cars[(index + dir + cars.size) % cars.size].id)
            }
            PgrStatsLine(car)
            PgrSelector("track", "${track.def.name} · ${track.def.city.label}") { dir ->
                onTrack((trackIndex + dir + PgrEngine.PgrTracks.all.size) % PgrEngine.PgrTracks.all.size)
            }
            PgrSelector("difficulty", difficulty.label) { dir ->
                val values = PgrEngine.PgrDifficulty.entries
                onDifficulty(values[(difficulty.ordinal + dir + values.size) % values.size])
            }
            PgrSelector("laps", laps.toString()) { dir ->
                onLaps((laps + dir).coerceIn(1, PgrEngine.MAX_LAPS))
            }
            PgrToggle("tilt steering", tiltEnabled, onTilt)
            PgrToggle("auto gas", autoGas, onAutoGas)
            Spacer(Modifier.height(8.dp))
            PgrRow("start race", "${track.def.name} · ${laps} laps · ${difficulty.label}", selected = true, onClick = onStart)
            BasicText(
                text = "steer with the on-screen pads or tilt · swipe the view for the handbrake",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun PgrCareer(
    profile: PgrEngine.PgrProfile,
    selected: Int,
    onSelect: (Int) -> Unit,
    onRace: (PgrEngine.PgrEventDef) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val index = selected.coerceIn(0, PgrEngine.PgrEvents.all.size - 1)
    val event = PgrEngine.PgrEvents.all[index]
    val unlocked = PgrEngine.canEnterEvent(profile, event)
    val medal = profile.medals[event.id] ?: PgrEngine.PgrMedal.NONE
    DetailScaffold(title = "pgr: career", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PgrSelector("event ${index + 1}/${PgrEngine.PgrEvents.all.size}", "${event.name} [${medal.label}]") { dir ->
                onSelect((index + dir + PgrEngine.PgrEvents.all.size) % PgrEngine.PgrEvents.all.size)
            }
            BasicText(
                text = "${event.type.label} · ${PgrEngine.PgrTracks.byId(event.trackId).def.name} · ${event.laps} laps · ${event.difficulty.label}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            BasicText(
                text = eventRequirement(event),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
            BasicText(
                text = event.blurb,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            if (!unlocked) {
                BasicText(
                    text = "locked — earn ${event.prerequisites.joinToString(", ")} medals first",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
            }
            Spacer(Modifier.height(8.dp))
            PgrRow(
                "race event",
                if (unlocked) "pays up to ${event.difficulty.creditReward * 3} credits" else "locked",
                selected = true,
                enabled = unlocked,
                onClick = { onRace(event) },
            )
            BasicText(
                text = "bronze/silver/gold medals gate the next event; some events unlock cars.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun PgrGarage(
    profile: PgrEngine.PgrProfile,
    selected: Int,
    selectedCarId: String,
    onSelect: (Int) -> Unit,
    onChoose: (String) -> Unit,
    onBuy: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val index = selected.coerceIn(0, PgrEngine.PgrCars.all.size - 1)
    val car = PgrEngine.PgrCars.all[index]
    val owned = profile.cars.contains(car.id)
    val active = selectedCarId == car.id
    DetailScaffold(title = "pgr: garage", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PgrSelector(
                label = "car ${index + 1}/${PgrEngine.PgrCars.all.size}",
                value = "${car.name} · class ${car.classLevel}" + if (active) " · selected" else "",
            ) { dir ->
                onSelect((index + dir + PgrEngine.PgrCars.all.size) % PgrEngine.PgrCars.all.size)
            }
            PgrStatsLine(car)
            BasicText(
                text = "cost ${car.creditCost} credits" + if (car.kudosCost > 0) " + ${car.kudosCost} kudos" else "",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            if (car.medalsRequired > 0) {
                BasicText(
                    text = "requires ${car.medalsRequired} career medals (you have ${profile.medalCount})",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                active -> PgrRow("selected", "this car is on the grid", selected = true, onClick = {})
                owned -> PgrRow("choose", "set as your car", selected = true, onClick = { onChoose(car.id) })
                else -> PgrRow("buy", "purchase this car", selected = true, onClick = { onBuy(car.id) })
            }
        }
    }
}

@Composable
private fun PgrRecords(
    profile: PgrEngine.PgrProfile,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "pgr: records", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(
                text = "events played ${profile.eventsPlayed} · medals ${profile.medalCount} · kudos ${profile.kudosBank}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
            )
            for (track in PgrEngine.PgrTracks.all) {
                val lap = profile.bestLaps[track.def.id]
                val time = profile.bestTimes[track.def.id]
                val kudos = profile.bestKudos[track.def.id] ?: 0
                BasicText(
                    text = "${track.def.name} · ${track.def.city.label}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                BasicText(
                    text = "best lap ${PgrEngine.formatLap(lap)} · best race ${PgrEngine.formatLap(time)} · kudos $kudos",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
                )
            }
        }
    }
}

@Composable
private fun PgrOptions(
    tiltEnabled: Boolean,
    sensitivity: Float,
    autoGas: Boolean,
    soundOn: Boolean,
    onTilt: (Boolean) -> Unit,
    onSensitivity: (Float) -> Unit,
    onAutoGas: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val sensitivities = listOf(0.6f, 0.75f, 0.85f, 1f)
    DetailScaffold(title = "pgr: options", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PgrToggle("tilt steering", tiltEnabled, onTilt)
            PgrSelector("tilt sensitivity", "%.2f".format(sensitivity)) { dir ->
                val i = sensitivities.indexOfFirst { it == sensitivity }.coerceAtLeast(0)
                onSensitivity(sensitivities[(i + dir + sensitivities.size) % sensitivities.size])
            }
            PgrToggle("auto gas", autoGas, onAutoGas)
            PgrToggle("sound", soundOn, onSound)
            BasicText(
                text = "tilt is softened around zero, so the car stays straight on a level surface.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun PgrResult(
    event: PgrEngine.PgrEventDef?,
    result: PgrEngine.PgrEventResult?,
    outcome: PgrEngine.PgrEventOutcome?,
    onAgain: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    DetailScaffold(title = "pgr: race result", onBack = onMenu) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val r = result
            if (r == null) {
                BasicText(
                    text = "race abandoned",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
                )
            } else {
                BasicText(
                    text = "${PgrEngine.ordinal(r.position)} of ${PgrEngine.MAX_PLAYERS}" +
                        if (r.finished) " · ${PgrEngine.formatTime(r.timeMs)}" else " · dnf",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
                )
                BasicText(
                    text = "kudos ${r.kudos} · best lap ${PgrEngine.formatLap(r.bestLapMs?.toLong())} · top ${PgrEngine.speedKph(r.topSpeed)} km/h",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
                )
                if (event != null && outcome != null) {
                    BasicText(
                        text = "${event.name}: ${outcome.message} (${outcome.medal.label})",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accentBright),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PgrRow("race again", if (event != null) event.name else "quick race", selected = true, onClick = onAgain)
            PgrRow("main menu", "back to the pit wall", onClick = onMenu)
        }
    }
}

// ======================================================================
// HUD and controls
// ======================================================================

@Composable
private fun PgrHud(
    race: PgrEngine.PgrRaceState,
    player: PgrEngine.PgrRacer,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    val def = PgrEngine.PgrCars.byId(player.carId)
    Column(modifier.padding(DoradoTokens.EDGE.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "${PgrEngine.ordinal(player.position)}/${race.racers.size}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = "lap ${min(player.lapsCompleted + 1, race.laps)}/${race.laps}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = PgrEngine.formatTime(PgrEngine.ticksToMs(race.raceTicks)),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "kudos ${player.kudos.total}" +
                    if (player.kudos.multiplier > 1) " x${player.kudos.multiplier}" else "",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accentBright),
            )
        }
        val ticker = player.kudos.lastManeuver
        if (ticker != null && player.kudos.pending > 0) {
            BasicText(
                text = "${ticker.label} +${player.kudos.pending} stashed",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column {
                BasicText(
                    text = PgrEngine.speedKph(player.car.speed).toString(),
                    style = TextStyle(fontFamily = Selawik, fontWeight = FontWeight.Light, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.textPrimary),
                )
                BasicText(
                    text = "km/h · gear ${if (player.car.gear == 0) "r" else player.car.gear}",
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                )
            }
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.width(150.dp).height(12.dp).padding(bottom = 4.dp)) {
                val fraction = (player.car.filteredRpm / (def.highRpm * 1.1f)).coerceIn(0f, 1f)
                drawRect(colors.elevated, Offset.Zero, size)
                drawRect(
                    colors.accentBright,
                    Offset.Zero,
                    Size(size.width * fraction, size.height),
                )
                drawRect(
                    colors.accent,
                    Offset(size.width * 0.82f, 0f),
                    Size(size.width * 0.18f, size.height),
                )
            }
        }
    }
}

@Composable
private fun PgrControls(
    modifier: Modifier = Modifier,
    onSteer: (Float) -> Unit,
    onThrottle: (Float) -> Unit,
    onBrake: (Float) -> Unit,
    onHandbrake: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PgrHold("brake", onDown = { onBrake(1f) }, onUp = { onBrake(0f) })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PgrHold("◀", onDown = { onSteer(-1f) }, onUp = { onSteer(0f) })
            PgrHold("▶", onDown = { onSteer(1f) }, onUp = { onSteer(0f) })
        }
        PgrHold("hb", onDown = { onHandbrake(true) }, onUp = { onHandbrake(false) })
        PgrHold("gas", onDown = { onThrottle(1f) }, onUp = { onThrottle(0f) })
    }
}

@Composable
private fun PgrPauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(DoradoTokens.EDGE.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "paused",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
            )
            PgrRow("resume", null, selected = true, onClick = onResume)
            PgrRow("restart", null, onClick = onRestart)
            PgrRow("quit to menu", null, onClick = onQuit)
        }
    }
}

// ======================================================================
// Small widgets
// ======================================================================

@Composable
private fun PgrRow(
    label: String,
    value: String?,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(label, enabled) { detectTapGestures { if (enabled) onClick() } }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeCropText(
            text = label,
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = if (!enabled) colors.textInactive else if (selected) colors.accent else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            BasicText(
                text = value,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
    }
}

@Composable
private fun PgrSelector(
    label: String,
    value: String,
    onCycle: (Int) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            modifier = Modifier.width(90.dp),
        )
        PgrHold("<", onDown = { onCycle(-1) }, onUp = {})
        EdgeCropText(
            text = value,
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        PgrHold(">", onDown = { onCycle(1) }, onUp = {})
    }
}

@Composable
private fun PgrToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    PgrRow(label, if (value) "on" else "off", selected = value, onClick = { onChange(!value) })
}

@Composable
private fun PgrStatsLine(car: PgrEngine.PgrCarDef) {
    val colors = LocalDoradoColors.current
    BasicText(
        text = "accel ${car.accel} · speed ${car.speedStat} · handling ${car.handling} · weight ${car.weight} · brake ${car.brake}",
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp, color = colors.textSecondary),
    )
}

@Composable
private fun PgrHold(label: String, onDown: () -> Unit, onUp: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onDown()
                        tryAwaitRelease()
                        onUp()
                    },
                )
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
        )
    }
}

// ======================================================================
// Procedural 3D content
// ======================================================================

private class PgrTrackVisual(
    val road: MeshData,
    val edgeLeft: MeshData,
    val edgeRight: MeshData,
    val centre: MeshData,
    val startLine: SceneNode,
    val props: List<SceneNode>,
    val gates: List<SceneNode>,
) {
    val staticNodes: List<SceneNode> = listOf(
        SceneNode(road, Material3d(Color4(0.16f, 0.17f, 0.19f))),
        SceneNode(edgeLeft, Material3d(Color4(0.72f, 0.72f, 0.70f), unlit = true)),
        SceneNode(edgeRight, Material3d(Color4(0.72f, 0.72f, 0.70f), unlit = true)),
        SceneNode(centre, Material3d(Color4(0.55f, 0.52f, 0.30f), unlit = true)),
        startLine,
    ) + props + gates
}

private class PgrAssets {
    val unitBox: MeshData = MeshFactory.box(1f, 1f, 1f)
    val cabin: MeshData = MeshFactory.box(1.35f, 0.34f, 1.8f)
    val wing: MeshData = MeshFactory.box(1.55f, 0.08f, 0.4f)
    val wheel: MeshData = MeshFactory.cylinder(0.34f, 0.28f, 10)
    val checker: TextureData = checkerTexture()
    private val bodies = HashMap<String, MeshData>()
    private val visuals = HashMap<String, PgrTrackVisual>()

    fun body(def: PgrEngine.PgrCarDef): MeshData =
        bodies.getOrPut(def.id) { MeshFactory.box(def.trackWidth, 0.46f, 4.25f) }

    fun visual(track: PgrEngine.PgrTrack): PgrTrackVisual =
        visuals.getOrPut(track.def.id) { buildTrackVisual(track) }

    private fun buildTrackVisual(track: PgrEngine.PgrTrack): PgrTrackVisual {
        val width = track.width
        val road = ribbonMesh(track, -width * 0.5f, width * 0.5f, 0.02f)
        val edgeLeft = ribbonMesh(track, -width * 0.5f + 0.15f, -width * 0.5f + 0.5f, 0.035f)
        val edgeRight = ribbonMesh(track, width * 0.5f - 0.5f, width * 0.5f - 0.15f, 0.035f)
        val centre = ribbonMesh(track, -0.12f, 0.12f, 0.03f)

        val startSample = track.sampleAt(0f)
        val startLine = nodeAt(
            MeshFactory.quad(width, 3.2f),
            x = startSample.position.x,
            y = 0.04f,
            z = startSample.position.z,
            pitchRad = -PI.toFloat() / 2f,
            yawRad = kotlin.math.atan2(startSample.forward.x, startSample.forward.z),
            material = Material3d(Color4(1f, 1f, 1f), texture = checker, unlit = true),
        )

        val props = buildCityProps(track)
        val gates = buildGates(track)
        return PgrTrackVisual(road, edgeLeft, edgeRight, centre, startLine, props, gates)
    }

    private fun buildCityProps(track: PgrEngine.PgrTrack): List<SceneNode> {
        val nodes = ArrayList<SceneNode>(64)
        var seed = track.def.propSeed
        for (i in 0 until 64) {
            seed = PgrEngine.nextSeed(seed)
            val t = ((seed ushr 8) and 0xFFFF) / 65535f
            val side = if (PgrEngine.noise(seed, i) > 0f) 1f else -1f
            val width = 4f + t * 10f
            val height = 6f + ((PgrEngine.noise(seed, i + 17) + 1f) * 0.5f) * 26f
            val depth = 4f + ((PgrEngine.noise(seed, i + 31) + 1f) * 0.5f) * 12f
            val distance = t * track.length
            val offset = side * (track.width * 0.5f + 6f + t * 34f)
            val sample = track.sampleAt(distance)
            val pos = sample.position + sample.right * offset
            val shade = 0.10f + 0.06f * (((seed ushr 16) and 0x0F) / 15f)
            nodes += SceneNode(
                unitBox,
                Material3d(Color4(shade, shade * 1.03f, shade * 1.12f)),
                Mat4.translation(Vec3(pos.x, height * 0.5f, pos.z)) *
                    Mat4.rotationY(sample.forward.x) *
                    Mat4.scale(Vec3(width, height, depth)),
            )
        }
        return nodes
    }

    private fun buildGates(track: PgrEngine.PgrTrack): List<SceneNode> {
        val nodes = ArrayList<SceneNode>()
        for (cp in track.checkpointDistances) {
            val sample = track.sampleAt(cp)
            val half = track.width * 0.5f + 0.4f
            for (side in listOf(-1f, 1f)) {
                val pos = sample.position + sample.right * (half * side)
                nodes += SceneNode(
                    unitBox,
                    Material3d(Color4(0.85f, 0.72f, 0.22f), unlit = true),
                    Mat4.translation(Vec3(pos.x, 2f, pos.z)) *
                        Mat4.rotationY(kotlin.math.atan2(sample.forward.x, sample.forward.z)) *
                        Mat4.scale(Vec3(0.28f, 4f, 0.28f)),
                )
            }
        }
        return nodes
    }
}

private fun ribbonMesh(track: PgrEngine.PgrTrack, inner: Float, outer: Float, y: Float): MeshData {
    val n = track.count
    val positions = FloatArray((n + 1) * 2 * 3)
    val normals = FloatArray((n + 1) * 2 * 3)
    val uvs = FloatArray((n + 1) * 2 * 2)
    val indices = IntArray(n * 6)
    for (i in 0..n) {
        val distance = track.length * i / n
        val sample = track.sampleAt(distance)
        val a = sample.position + sample.right * inner
        val b = sample.position + sample.right * outer
        val base = i * 6
        positions[base] = a.x
        positions[base + 1] = y
        positions[base + 2] = a.z
        positions[base + 3] = b.x
        positions[base + 4] = y
        positions[base + 5] = b.z
        for (k in 0 until 6) normals[base + k] = if (k % 3 == 1) 1f else 0f
        uvs[i * 4] = 0f
        uvs[i * 4 + 1] = distance / 6f
        uvs[i * 4 + 2] = 1f
        uvs[i * 4 + 3] = distance / 6f
        if (i < n) {
            val v = i * 2
            val o = i * 6
            indices[o] = v
            indices[o + 1] = v + 2
            indices[o + 2] = v + 1
            indices[o + 3] = v + 1
            indices[o + 4] = v + 2
            indices[o + 5] = v + 3
        }
    }
    return MeshData(positions, normals, uvs, indices)
}

private fun addCarNodes(scene: Scene3d, assets: PgrAssets, racer: PgrEngine.PgrRacer, def: PgrEngine.PgrCarDef) {
    val car = racer.car
    val heading = car.heading
    val forward = PgrEngine.PgrVec2(sin(heading), kotlin.math.cos(heading))
    val right = PgrEngine.PgrVec2(forward.z, -forward.x)
    val color = color4(def.colorsArgb[racer.index % def.colorsArgb.size])
    val dark = Color4(0.07f, 0.08f, 0.1f)

    scene.add(
        nodeAt(
            assets.body(def),
            car.position.x,
            0.36f,
            car.position.z,
            yawRad = heading,
            material = if (car.skidRear && car.speed > 12f) {
                Material3d(color, emissive = 0.12f)
            } else {
                Material3d(color)
            },
        ),
    )
    val cabinPos = car.position + forward * (-0.25f)
    scene.add(nodeAt(assets.cabin, cabinPos.x, 0.72f, cabinPos.z, yawRad = heading, material = Material3d(dark)))
    val wingPos = car.position + forward * (-1.95f)
    scene.add(nodeAt(assets.wing, wingPos.x, 0.82f, wingPos.z, yawRad = heading, material = Material3d(color)))
    val wheels = listOf(-1.35f to -0.5f, -1.35f to 0.5f, 1.4f to -0.52f, 1.4f to 0.52f)
    for ((longitudinal, lateral) in wheels) {
        val p = car.position + forward * longitudinal + right * (lateral * def.trackWidth)
        scene.add(
            nodeAt(
                assets.wheel,
                p.x,
                0.34f,
                p.z,
                yawRad = heading,
                rollRad = PI.toFloat() / 2f,
                material = Material3d(dark),
            ),
        )
    }
    val brakePos = car.position + forward * (-2.1f)
    scene.add(
        nodeAt(
            MeshFactory.box(1.4f, 0.12f, 0.12f),
            brakePos.x,
            0.5f,
            brakePos.z,
            yawRad = heading,
            material = Material3d(Color4(0.9f, 0.15f, 0.1f), unlit = true, emissive = 0.6f),
        ),
    )
}

private fun chaseCamera(racer: PgrEngine.PgrRacer): Camera3d {
    val heading = racer.car.heading
    val forward = PgrEngine.PgrVec2(sin(heading), kotlin.math.cos(heading))
    val speed = racer.car.speed
    val distance = 8.2f + speed * 0.045f
    val eye = Vec3(
        racer.car.position.x - forward.x * distance,
        3.2f + speed * 0.012f,
        racer.car.position.z - forward.z * distance,
    )
    val target = Vec3(
        racer.car.position.x + forward.x * (5f + speed * 0.12f),
        0.9f,
        racer.car.position.z + forward.z * (5f + speed * 0.12f),
    )
    return Camera3d(eye = eye, target = target, up = Vec3.UP, fovYDeg = 62f, near = 0.1f, far = 700f)
}

private fun color4(argb: Int): Color4 = Color4(
    (argb shr 16 and 0xFF) / 255f,
    (argb shr 8 and 0xFF) / 255f,
    (argb and 0xFF) / 255f,
)

private fun checkerTexture(): TextureData {
    val size = 8
    val pixels = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if ((x + y) % 2 == 0) 0xFFF0F0F0.toInt() else 0xFF15171B.toInt()
    }
    return TextureData(size, size, pixels)
}

/** Short detuned-tone engine loop per RPM band (idle/low/mid/high). */
private fun engineBandSample(band: Int, sampleRate: Int): DoubleArray {
    val base = when (band) {
        0 -> 52.0
        1 -> 88.0
        2 -> 140.0
        else -> 205.0
    }
    val n = sampleRate / 5
    val out = DoubleArray(n)
    var phase = 0.0
    var phase2 = 0.0
    for (i in 0 until n) {
        val t = i.toDouble() / n
        phase += 2 * PI * base / sampleRate
        phase2 += 2 * PI * base * 1.52 / sampleRate
        val pulse = 0.6 + 0.4 * sin(2 * PI * 7.0 * t)
        out[i] = (sin(phase) * 0.5 + sin(phase2) * 0.22) * pulse * 0.14
    }
    return out
}

private fun eventRequirement(event: PgrEngine.PgrEventDef): String = when (event.type) {
    PgrEngine.PgrEventType.BREAKTHROUGH -> "finish ${event.laps} laps under ${PgrEngine.formatTime(event.limitMs)}"
    PgrEngine.PgrEventType.CONE_SPRINT ->
        "clear ${event.gateCount} gates under ${PgrEngine.formatTime(event.limitMs)}"
    PgrEngine.PgrEventType.ELIMINATOR -> "never be last at an elimination check"
    PgrEngine.PgrEventType.KUDOS_CHALLENGE -> "bank ${event.targetKudos} kudos"
    PgrEngine.PgrEventType.ONE_ON_ONE -> "finish ahead of the rival"
    PgrEngine.PgrEventType.OVERTAKE -> "make ${event.targetOvertakes} overtakes"
    PgrEngine.PgrEventType.SPEED_CHALLENGE -> "reach ${PgrEngine.speedKph(event.targetSpeed)} km/h"
    PgrEngine.PgrEventType.TIME_VS_KUDOS ->
        "under ${PgrEngine.formatTime(event.limitMs)} with ${event.targetKudos} kudos"
    PgrEngine.PgrEventType.STREET_RACE -> "win ${event.laps} laps"
}
