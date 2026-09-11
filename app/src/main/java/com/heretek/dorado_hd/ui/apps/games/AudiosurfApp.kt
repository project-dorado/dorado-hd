package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.DoradoGraph
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.Mat4
import com.heretek.dorado_hd.ui.apps.engine3d.MeshData
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3dView
import com.heretek.dorado_hd.ui.apps.engine3d.SceneNode
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val AS_APP_KEY = "audiosurf-tilt"
private const val AS_SETTINGS_KEY = "audiosurf-tilt.settings"
private const val AS_MEDAL_PREFIX = "audiosurf-tilt.medal"

private const val AS_METRES_PER_MS = 0.02f
private const val AS_ROAD_WIDTH = 7.2f
private const val AS_BLOCK_SIZE = 1.05f
private const val AS_WINDOW_MS = 4_000L
private const val AS_AHEAD_MS = 18_000L
private const val AS_BEHIND_MS = 4_000L
private const val AS_MENU_LIBRARY_LIMIT = 4

private val AS_DEMO_NAMES = listOf("pulse demo", "drift demo", "storm demo")

private data class RidePick(
    val title: String,
    val subtitle: String,
    val track: Track?,
    val profile: TrackProfile,
) {
    val id: Long get() = profile.trackId
}

/**
 * Audiosurf Tilt: a three-lane downhill ride generated from the current
 * library track's audio features (or a deterministic synthetic profile when
 * none exists). Normal/Hard are scored surf modes; Visualizer is a scoreless
 * camera ride.
 *
 * Audio binding: the ride never starts playback on its own. When the player
 * picks "music on" and starts a library track, that explicit tap is the only
 * call to `graph.controller.play(...)`; while riding with music the course
 * position is read from `graph.controller.positionMs` (see the frame loop
 * below). Otherwise a local clock drives [AudiosurfEngine.step]. A resumed
 * ride always restarts as a free ride, again without touching playback.
 */
@Composable
fun AudiosurfApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var mode by remember { mutableStateOf(AudiosurfMode.NORMAL) }
    var sensitivity by remember { mutableStateOf(0.6f) }
    var laneSnapping by remember { mutableStateOf(true) }
    var usePlayer by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<RideState?>(null) }
    var savedRide by remember { mutableStateOf<RideState?>(null) }
    var lastPick by remember { mutableStateOf<RidePick?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<String?>(null) }
    var medalAward by remember { mutableStateOf<AudiosurfMedal?>(null) }
    var medals by remember { mutableStateOf<Map<Long, AudiosurfMedal>>(emptyMap()) }
    var lastTiltDir by remember { mutableStateOf(0) }
    var dragPx by remember { mutableStateOf(0f) }
    var stallMs by remember { mutableStateOf(0L) }

    val library by graph.library.tracks().collectAsState(initial = emptyList())
    val picks = remember(library) { buildPicks(graph, library) }

    LaunchedEffect(Unit) {
        graph.appState.get(AS_SETTINGS_KEY)?.let { raw ->
            val parts = raw.split(";")
            if (parts.size >= 4) {
                mode = AudiosurfMode.entries.getOrNull(parts[0].toIntOrNull() ?: -1) ?: mode
                usePlayer = parts[1] == "1"
                sensitivity = parts[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: sensitivity
                laneSnapping = parts[3] == "1"
            }
        }
        savedRide = graph.appState.get(AS_APP_KEY)
            ?.let { AudiosurfEngine.decode(it) }
            ?.takeIf { it.status != RideStatus.FINISHED }
        settingsLoaded = true
    }

    LaunchedEffect(settingsLoaded, mode, usePlayer, sensitivity, laneSnapping) {
        if (!settingsLoaded) return@LaunchedEffect
        graph.appState.put(
            AS_SETTINGS_KEY,
            listOf(
                mode.ordinal,
                if (usePlayer) 1 else 0,
                sensitivity,
                if (laneSnapping) 1 else 0,
            ).joinToString(";"),
        )
    }

    LaunchedEffect(picks, mode) { medals = loadMedals(graph, picks, mode) }

    // Progress checkpoints; cleared when the ride finishes.
    LaunchedEffect(screen) {
        while (screen == "ride") {
            delay(2_000)
            val current = state
            if (current != null && current.status == RideStatus.RIDING) {
                graph.appState.put(AS_APP_KEY, AudiosurfEngine.encode(current))
            }
        }
    }

    val tilt = rememberTilt(
        enabled = screen == "ride" && !paused && state?.status == RideStatus.RIDING,
        smoothing = 0.5f,
    )

    LaunchedEffect(tilt.value) {
        val value = tilt.value
        if (!value.available) return@LaunchedEffect
        val direction = AudiosurfEngine.tiltDirection(value.rollDeg.toDouble(), sensitivity.toDouble())
        if (direction != 0 && direction != lastTiltDir) {
            state = state?.let { AudiosurfEngine.steer(it, direction) }
        }
        lastTiltDir = direction
    }

    // Frame loop: advances a free ride or follows the shared playback position.
    LaunchedEffect(screen, paused) {
        if (screen != "ride" || paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val current = state ?: return@withFrameNanos
                if (current.status == RideStatus.FINISHED) return@withFrameNanos
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dtMs = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
                last = now
                val target = if (usePlayer) {
                    val playerPosition = graph.controller.positionMs.value
                    if (playerPosition <= current.trackPositionMs) {
                        stallMs += dtMs
                    } else {
                        stallMs = 0L
                    }
                    // A stalled player falls back to the wall clock after the
                    // grace period so the ride always reaches FINISH (A-27).
                    AudiosurfEngine.syncedTarget(
                        currentPositionMs = current.trackPositionMs,
                        playerPositionMs = playerPosition,
                        dtMs = dtMs,
                        speedScale = current.speedScale,
                        stalledMs = stallMs,
                    )
                } else {
                    current.trackPositionMs + (dtMs * current.speedScale).toLong()
                }
                if (target <= current.trackPositionMs) return@withFrameNanos
                val next = AudiosurfEngine.step(current, target)
                state = next
                var text = reward
                for (event in next.events) {
                    bank.play(rideSfx(event.kind))
                    when (event.kind) {
                        RideEventKind.CHAIN -> text = event.label
                        RideEventKind.MISS -> text = "miss"
                        RideEventKind.STONE -> text = "stone"
                        RideEventKind.PERFECT -> text = "perfect"
                        else -> Unit
                    }
                }
                if (text != reward) reward = text
            }
        }
    }

    LaunchedEffect(reward) {
        if (reward != null) {
            delay(1_200)
            reward = null
        }
    }

    LaunchedEffect(state?.status) {
        val current = state ?: return@LaunchedEffect
        if (current.status != RideStatus.FINISHED || recorded) return@LaunchedEffect
        recorded = true
        val medal = AudiosurfEngine.medalFor(current.collected, current.course.coloredTotal, current.mode)
        medalAward = medal
        bank.play(if (medal == AudiosurfMedal.GOLD || medal == AudiosurfMedal.SILVER) "win" else "lose")
        val meta = buildString {
            append(current.mode.name.lowercase())
            append('|')
            append(current.collected)
            append('/')
            append(current.course.coloredTotal)
            append('|')
            append(current.misses)
            append("miss|")
            append(current.stones)
            append("stone")
        }
        if (medal != AudiosurfMedal.NONE) {
            graph.appState.put(medalKey(current.course.profile.trackId, current.mode), medal.name)
        }
        graph.appState.clear(AS_APP_KEY)
        graph.games.record("audiosurf-tilt", current.score, meta)
        savedRide = null
    }

    val scene = remember { Scene3d() }
    val roadMesh = remember { MeshFactory.plane(1f, 1f) }
    val blockMesh = remember { MeshFactory.box(1f, 1f, 1f) }
    val current = state
    val windowIndex = current?.let { (it.trackPositionMs / AS_WINDOW_MS).toInt() } ?: 0

    DisposableEffect(current?.course, windowIndex, screen) {
        scene.clear()
        val ride = state
        if (ride != null && screen == "ride") {
            buildRideScene(scene, ride, colors, roadMesh, blockMesh)
        }
        onDispose { }
    }

    SideEffect {
        val ride = state
        if (ride != null && screen == "ride") {
            val tiltValue = tilt.value
            val roll = if (tiltValue.available) {
                val sign = if (tiltValue.rollDeg < 0f) -1f else 1f
                (
                    AudiosurfEngine.tiltMagnitude(tiltValue.rollDeg.toDouble(), sensitivity.toDouble()) *
                        0.22 * sign
                    ).toFloat()
            } else {
                0f
            }
            val z = -ride.trackPositionMs * AS_METRES_PER_MS
            val x = laneOffset(ride.lanePosition)
            val lift = ride.jumpHeight
            scene.camera = scene.camera.copy(
                eye = Vec3(x, 1.5f + lift, z),
                target = Vec3(x * 0.72f, 0.7f + lift * 0.6f, z - 14f),
                up = Vec3(sin(roll), cos(roll), 0f),
                fovYDeg = 72f,
                near = 0.1f,
                far = 900f,
            )
        }
    }

    fun beginRide(pick: RidePick) {
        val course = AudiosurfEngine.buildCourse(pick.profile, mode, seed = pick.profile.trackId.toInt())
        state = AudiosurfEngine.startRide(AudiosurfEngine.newRide(course))
        lastPick = pick
        recorded = false
        paused = false
        reward = null
        medalAward = null
        stallMs = 0L
        screen = "ride"
        // Explicit user action only: never start playback implicitly.
        if (usePlayer && pick.track != null) {
            graph.controller.play(listOf(pick.track), 0)
        }
    }

    fun resumeRide() {
        val saved = savedRide ?: return
        // Resuming is a free ride; playback is still only started by the player.
        state = AudiosurfEngine.startRide(saved)
        mode = saved.mode
        usePlayer = false
        lastPick = null
        recorded = false
        paused = false
        reward = null
        medalAward = null
        stallMs = 0L
        screen = "ride"
    }

    fun rideAgain() {
        lastPick?.let { beginRide(it) }
    }

    DetailScaffold(
        title = "audiosurf tilt",
        onBack = {
            if (screen == "ride") {
                paused = true
            } else {
                graph.nav.pop()
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            val ride = state
            if (screen != "ride" || ride == null) {
                RideMenu(
                    mode = mode,
                    sensitivity = sensitivity,
                    laneSnapping = laneSnapping,
                    usePlayer = usePlayer,
                    picks = picks,
                    medals = medals,
                    savedRide = savedRide,
                    onMode = { mode = it },
                    onSensitivity = { sensitivity = it },
                    onSnapping = { laneSnapping = it },
                    onUsePlayer = { usePlayer = it },
                    onStart = { beginRide(it) },
                    onResume = { resumeRide() },
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    Scene3dView(scene = scene, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(ride.course) {
                                detectTapGestures { offset ->
                                    if (paused || ride.status != RideStatus.RIDING) return@detectTapGestures
                                    state = state?.let {
                                        AudiosurfEngine.setLane(
                                            it,
                                            AudiosurfEngine.absoluteTouchLane(offset.x / size.width),
                                        )
                                    }
                                }
                            }
                            .pointerInput(ride.course) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragPx = 0f },
                                    onDragEnd = { dragPx = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragPx += amount
                                        while (dragPx > 56f) {
                                            dragPx -= 56f
                                            state = state?.let { AudiosurfEngine.steer(it, 1) }
                                        }
                                        while (dragPx < -56f) {
                                            dragPx += 56f
                                            state = state?.let { AudiosurfEngine.steer(it, -1) }
                                        }
                                    },
                                )
                            },
                    )
                    RideHud(
                        ride = ride,
                        reward = reward,
                        onPause = { paused = true },
                    )
                    if (paused && ride.status != RideStatus.FINISHED) {
                        RideOverlay(
                            title = "paused",
                            lines = listOf(
                                "score ${ride.score}",
                                "chain ${ride.chain} · miss ${ride.misses} · stone ${ride.stones}",
                            ),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RideButton("resume") { paused = false }
                                RideButton("restart") { rideAgain() }
                                RideButton("menu") {
                                    screen = "menu"
                                    state = null
                                    paused = false
                                }
                            }
                        }
                    }
                    if (ride.status == RideStatus.FINISHED) {
                        val medal = medalAward
                            ?: AudiosurfEngine.medalFor(ride.collected, ride.course.coloredTotal, ride.mode)
                        RideOverlay(
                            title = medalLabel(medal).ifEmpty { "ride complete" },
                            lines = listOf(
                                "score ${ride.score} · ${ride.collected}/${ride.course.coloredTotal} blocks",
                                "longest chain ${ride.longestChain} · miss ${ride.misses} · stone ${ride.stones}",
                                if (ride.misses == 0 && ride.stones == 0 && ride.collected == ride.course.coloredTotal) {
                                    "perfect ride"
                                } else {
                                    AudiosurfEngine.nextMedalLabel(ride.collected, ride.course.coloredTotal, ride.mode)
                                        ?: "visualizer flight"
                                },
                            ),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (lastPick != null) RideButton("ride again") { rideAgain() }
                                RideButton("menu") {
                                    screen = "menu"
                                    state = null
                                    paused = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================ */
/*                              Menu                              */
/* ============================================================ */

@Composable
private fun RideMenu(
    mode: AudiosurfMode,
    sensitivity: Float,
    laneSnapping: Boolean,
    usePlayer: Boolean,
    picks: List<RidePick>,
    medals: Map<Long, AudiosurfMedal>,
    savedRide: RideState?,
    onMode: (AudiosurfMode) -> Unit,
    onSensitivity: (Float) -> Unit,
    onSnapping: (Boolean) -> Unit,
    onUsePlayer: (Boolean) -> Unit,
    onStart: (RidePick) -> Unit,
    onResume: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(DoradoTokens.EDGE.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AudiosurfMode.entries.forEach { option ->
                RideChip(option.name.lowercase(), selected = option == mode) { onMode(option) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RideChip("-") { onSensitivity((sensitivity - 0.1f).coerceAtLeast(0f)) }
            BasicText(
                text = "sense ${(sensitivity * 100f).roundToInt()}%",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textSecondary,
                ),
            )
            RideChip("+") { onSensitivity((sensitivity + 0.1f).coerceAtMost(1f)) }
            Spacer(Modifier.width(4.dp))
            RideChip(if (laneSnapping) "snap on" else "snap off", selected = laneSnapping) { onSnapping(!laneSnapping) }
            RideChip(if (usePlayer) "music on" else "music off", selected = usePlayer) { onUsePlayer(!usePlayer) }
        }
        if (savedRide != null) {
            RideButton("continue · ${savedRide.mode.name.lowercase()} · score ${savedRide.score}") { onResume() }
        }
        EdgeCropText(
            text = "rides",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textSecondary,
        )
        picks.forEach { pick ->
            RidePickRow(
                pick = pick,
                medal = medals[pick.id],
                onStart = { onStart(pick) },
            )
        }
        EdgeCropText(
            text = "tilt or tap a lane to steer · hold a drag to strafe · header pauses",
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = colors.textInactive,
        )
    }
}

@Composable
private fun RidePickRow(pick: RidePick, medal: AudiosurfMedal?, onStart: () -> Unit) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(pick) { detectTapGestures(onTap = { onStart() }) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = pick.title,
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_LIST.sp,
                    color = colors.textPrimary,
                ),
            )
            BasicText(
                text = pick.subtitle,
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_LIST_SECONDARY.sp,
                    color = colors.textSecondary,
                ),
            )
        }
        val label = medalLabel(medal)
        if (label.isNotEmpty()) {
            EdgeCropText(
                text = label,
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.accentBright,
            )
        }
    }
}

/* ============================================================ */
/*                               HUD                              */
/* ============================================================ */

@Composable
private fun RideHud(ride: RideState, reward: String?, onPause: () -> Unit) {
    val colors = LocalDoradoColors.current
    val fraction = (ride.trackPositionMs.toFloat() / ride.course.durationMs.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "score ${ride.score}",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_NOW_META.sp,
                    color = colors.accent,
                ),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "chain ${ride.chain}",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textPrimary,
                ),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "speed ${(ride.speedScale * 100f).roundToInt()}%",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textSecondary,
                ),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = ride.mode.name.lowercase(),
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textSecondary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            RideButton("pause") { onPause() }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.border)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(colors.accent))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = reward ?: AudiosurfEngine.nextMedalLabel(
                    ride.collected,
                    ride.course.coloredTotal,
                    ride.mode,
                ) ?: "visualizer",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = if (reward != null) colors.accentBright else colors.textInactive,
                ),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "${ride.collected}/${ride.course.coloredTotal}",
                style = TextStyle(
                    fontFamily = Selawik,
                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                    color = colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
private fun RideOverlay(title: String, lines: List<String>, actions: @Composable () -> Unit) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EdgeCropText(
                text = title,
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                color = colors.accent,
            )
            Spacer(Modifier.height(4.dp))
            lines.forEach { line ->
                EdgeCropText(
                    text = line,
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            actions()
        }
    }
}

@Composable
private fun RideButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(26.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_LIST.sp,
                color = colors.textPrimary,
            ),
        )
    }
}

@Composable
private fun RideChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(26.dp)
            .background(if (selected) colors.accent else colors.tile)
            .border(0.5.dp, if (selected) colors.accent else colors.border)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = if (selected) colors.background else colors.textSecondary,
            ),
        )
    }
}

/* ============================================================ */
/*                          Scene helpers                         */
/* ============================================================ */

private fun buildRideScene(
    scene: Scene3d,
    state: RideState,
    colors: DoradoColors,
    roadMesh: MeshData,
    blockMesh: MeshData,
) {
    val course = state.course
    scene.backgroundColor = Color4(0.02f, 0.02f, 0.035f)
    scene.ambient = 0.42f
    scene.lightDirection = Vec3(-0.35f, -1f, -0.22f)
    scene.fogDensity = 0.008f + 0.012f * (1f - course.profile.energy.toFloat()).coerceIn(0f, 1f)

    val length = (course.durationMs * AS_METRES_PER_MS).coerceAtLeast(20f)
    val laneWidth = AS_ROAD_WIDTH / AudiosurfEngine.LANE_COUNT.toFloat()
    val divider = laneWidth / 2f

    scene.add(
        scaledNode(
            roadMesh,
            y = -0.06f,
            z = -length / 2f,
            sx = AS_ROAD_WIDTH,
            sy = 1f,
            sz = length,
            material = Material3d(Color4(0.07f, 0.07f, 0.09f), unlit = true),
        ),
    )
    for (x in listOf(-divider, divider)) {
        scene.add(
            scaledNode(
                roadMesh,
                x = x,
                y = 0.005f,
                z = -length / 2f,
                sx = 0.08f,
                sy = 1f,
                sz = length,
                material = Material3d(c4(colors.border), unlit = true),
            ),
        )
    }
    for (x in listOf(-AS_ROAD_WIDTH / 2f, AS_ROAD_WIDTH / 2f)) {
        scene.add(
            scaledNode(
                blockMesh,
                x = x,
                y = 0.1f,
                z = -length / 2f,
                sx = 0.22f,
                sy = 0.3f,
                sz = length,
                material = Material3d(c4(colors.tile)),
            ),
        )
    }

    val palette = listOf(
        colors.accent,
        DoradoAccent.CYAN.primary,
        DoradoAccent.LIME.primary,
        DoradoAccent.ORANGE.primary,
        DoradoAccent.PURPLE.primary,
    )
    val from = state.trackPositionMs - AS_BEHIND_MS
    val to = state.trackPositionMs + AS_AHEAD_MS
    for (element in course.elements) {
        if (element.positionMs < from || element.positionMs > to) continue
        val z = -element.positionMs * AS_METRES_PER_MS
        val x = laneOffset(element.lane.toFloat())
        when (element.kind) {
            CourseElementKind.COLOR_BLOCK -> scene.add(
                scaledNode(
                    blockMesh,
                    x = x,
                    y = 0.5f,
                    z = z,
                    sx = AS_BLOCK_SIZE,
                    sy = AS_BLOCK_SIZE,
                    sz = AS_BLOCK_SIZE,
                    material = Material3d(c4(palette[element.index % palette.size]), emissive = 0.35f),
                ),
            )

            CourseElementKind.GREY_BLOCK -> scene.add(
                scaledNode(
                    blockMesh,
                    x = x,
                    y = 0.5f,
                    z = z,
                    sx = AS_BLOCK_SIZE * 1.05f,
                    sy = AS_BLOCK_SIZE * 1.05f,
                    sz = AS_BLOCK_SIZE * 1.05f,
                    material = Material3d(c4(colors.tilePressed)),
                ),
            )

            CourseElementKind.SPEED_UP -> scene.add(
                scaledNode(
                    blockMesh,
                    x = x,
                    y = 0.25f,
                    z = z,
                    sx = 2.6f,
                    sy = 0.08f,
                    sz = 1.1f,
                    material = Material3d(c4(DoradoAccent.LIME.primary), emissive = 0.5f),
                ),
            )

            CourseElementKind.SLOW_DOWN -> scene.add(
                scaledNode(
                    blockMesh,
                    x = x,
                    y = 0.25f,
                    z = z,
                    sx = 2.6f,
                    sy = 0.08f,
                    sz = 1.1f,
                    material = Material3d(c4(DoradoAccent.CYAN.primary), emissive = 0.5f),
                ),
            )

            CourseElementKind.JUMP -> scene.add(
                scaledNode(
                    blockMesh,
                    x = x,
                    y = 0.18f,
                    z = z,
                    sx = 2.0f,
                    sy = 0.36f,
                    sz = 0.9f,
                    material = Material3d(c4(DoradoAccent.ORANGE.primary), emissive = 0.4f),
                ),
            )
        }
    }
}

private fun scaledNode(
    mesh: MeshData,
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    sx: Float = 1f,
    sy: Float = 1f,
    sz: Float = 1f,
    material: Material3d = Material3d(),
): SceneNode =
    SceneNode(mesh, material, Mat4.translation(Vec3(x, y, z)) * Mat4.scale(Vec3(sx, sy, sz)))

private fun laneOffset(position: Float): Float =
    (position - 1f) * (AS_ROAD_WIDTH / AudiosurfEngine.LANE_COUNT.toFloat())

private fun c4(color: Color): Color4 = Color4(color.red, color.green, color.blue, color.alpha)

/* ============================================================ */
/*                          App helpers                           */
/* ============================================================ */

private fun buildPicks(graph: DoradoGraph, library: List<Track>): List<RidePick> {
    val picks = ArrayList<RidePick>()
    library.take(AS_MENU_LIBRARY_LIMIT).forEach { track ->
        val duration = if (track.durationMs > 0L) track.durationMs else AudiosurfEngine.SYNTHETIC_DURATION_MS
        picks += RidePick(
            title = track.title,
            subtitle = track.artist,
            track = track,
            profile = AudiosurfEngine.profileFor(
                features = graph.analysis.featuresFor(track),
                durationMs = duration,
                seed = track.mediaId.hashCode(),
                trackId = track.mediaId,
            ),
        )
    }
    AS_DEMO_NAMES.forEachIndexed { index, name ->
        picks += RidePick(
            title = name,
            subtitle = "synthetic ride",
            track = null,
            profile = AudiosurfEngine.profileFor(
                features = null,
                durationMs = 150_000L,
                seed = 4_200 + index,
                trackId = -(index + 1).toLong(),
            ),
        )
    }
    return picks
}

private suspend fun loadMedals(
    graph: DoradoGraph,
    picks: List<RidePick>,
    mode: AudiosurfMode,
): Map<Long, AudiosurfMedal> {
    val out = HashMap<Long, AudiosurfMedal>()
    for (pick in picks) {
        val raw = graph.appState.get(medalKey(pick.id, mode)) ?: continue
        AudiosurfMedal.entries.firstOrNull { it.name == raw }?.let { out[pick.id] = it }
    }
    return out
}

private fun medalKey(trackId: Long, mode: AudiosurfMode): String =
    "$AS_MEDAL_PREFIX.$trackId.${mode.name.lowercase()}"

private fun medalLabel(medal: AudiosurfMedal?): String = when (medal) {
    AudiosurfMedal.GOLD -> "gold"
    AudiosurfMedal.SILVER -> "silver"
    AudiosurfMedal.BRONZE -> "bronze"
    else -> ""
}

private fun rideSfx(kind: RideEventKind): String = when (kind) {
    RideEventKind.COLLECT -> "coin"
    RideEventKind.MISS -> "tick"
    RideEventKind.STONE -> "hit"
    RideEventKind.CHAIN -> "score"
    RideEventKind.JUMP -> "jump"
    RideEventKind.SPEED_UP -> "whoosh"
    RideEventKind.SLOW_DOWN -> "toss"
    RideEventKind.FINISH -> "pop"
    RideEventKind.PERFECT -> "win"
}
