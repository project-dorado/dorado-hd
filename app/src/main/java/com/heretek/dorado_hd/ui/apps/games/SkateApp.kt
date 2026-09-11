package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.TiltState
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
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class SkateScreen { MENU, CAREER, LOADOUT, TRICKS, SESSION, RESULTS }

private val BASIN_MATERIAL = Material3d(Color4.rgb(118, 132, 140))
private val DECK_MATERIAL = Material3d(Color4.rgb(92, 88, 80))
private val COPING_MATERIAL = Material3d(Color4.rgb(196, 200, 204))
private val RAIL_MATERIAL = Material3d(Color4.rgb(172, 176, 180))
private val RAMP_MATERIAL = Material3d(Color4.rgb(150, 122, 86))
private val BOARD_MATERIAL = Material3d(Color4.rgb(36, 38, 42))
private val WHEEL_MATERIAL = Material3d(Color4.rgb(228, 226, 214))
private val BAG_MATERIAL = Material3d(Color4.rgb(58, 118, 74))
private val SHADOW_MATERIAL = Material3d(Color4(0f, 0f, 0f, 1f), unlit = true)
private val SKATER_COLORS = listOf(
    Color4.rgb(196, 74, 58),
    Color4.rgb(64, 132, 190),
    Color4.rgb(216, 168, 62),
    Color4.rgb(108, 176, 104),
    Color4.rgb(178, 104, 186),
)

private val bodyMesh: MeshData = MeshFactory.box(0.46f, 0.62f, 0.34f)
private val headMesh: MeshData = MeshFactory.box(0.28f, 0.28f, 0.28f)
private val boardMesh: MeshData = MeshFactory.box(0.46f, 0.06f, 1.42f)
private val wheelMesh: MeshData = MeshFactory.box(0.1f, 0.16f, 0.16f)
private val bagMesh: MeshData = MeshFactory.box(0.42f, 0.42f, 0.42f)

private class PoolAssets(val pool: SkatePool) {
    val bowl: MeshData = bowlMesh(pool)
    val deck: MeshData = MeshFactory.plane(pool.radius * 2f + 13f, pool.radius * 2f + 13f)
    val rails: List<MeshData> = pool.rails.map { edge ->
        val dx = edge.bx - edge.ax
        val dz = edge.bz - edge.az
        MeshFactory.box(0.1f, 0.1f, sqrt(dx * dx + dz * dz))
    }
    val ramps: List<MeshData> = pool.ramps.map { ramp ->
        MeshFactory.box(ramp.halfWidth * 2f, 0.3f, ramp.halfDepth * 2f)
    }
}

fun bowlMesh(pool: SkatePool): MeshData {
    val rings = 9
    val segments = 28
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (ring in 0..rings) {
        val r = pool.radius * ring / rings
        for (segment in 0..segments) {
            val angle = 2.0 * PI * segment / segments
            val x = (cos(angle) * r).toFloat()
            val z = (sin(angle) * r).toFloat()
            val y = SkateEngine.surfaceHeight(pool, x, z)
            val normal = SkateEngine.surfaceNormal(pool, x, z)
            positions += x
            positions += y
            positions += z
            normals += normal.x
            normals += normal.y
            normals += normal.z
            uvs += segment.toFloat() / segments
            uvs += ring.toFloat() / rings
        }
    }
    val stride = segments + 1
    for (ring in 0 until rings) {
        for (segment in 0 until segments) {
            val a = ring * stride + segment
            val b = a + 1
            val c = a + stride
            val d = c + 1
            indices += a
            indices += b
            indices += c
            indices += b
            indices += d
            indices += c
        }
    }
    return MeshData(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

private fun tiltX(tilt: TiltState): Float = (tilt.rollDeg / 45f).coerceIn(-1f, 1f)

private fun tiltY(tilt: TiltState): Float = (tilt.pitchDeg / 45f).coerceIn(-1f, 1f)

private fun directionOf(dx: Float, dy: Float): SkateSwipe = if (abs(dx) > abs(dy)) {
    if (dx > 0f) SkateSwipe.RIGHT else SkateSwipe.LEFT
} else {
    if (dy > 0f) SkateSwipe.DOWN else SkateSwipe.UP
}

@Composable
fun SkateApp() {
    val graph = LocalDoradoGraph.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val synth = remember { MiniSynth(scope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var career by remember { mutableStateOf(SkateCareer()) }
    var loaded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(SkateScreen.MENU) }
    var pendingEvent by remember { mutableStateOf(SkateEventId.FREE_RIDE) }
    var session by remember { mutableStateOf<SkateState?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<SkateState?>(null) }
    var resultNewBest by remember { mutableStateOf(false) }
    val scene = remember { Scene3d() }
    val topScores by graph.games.top("vans-sk8-pool-service", 5).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        graph.appState.get("vans-sk8-pool-service")?.let { blob ->
            SkateCareer.decode(blob)?.let { career = it }
        }
        loaded = true
    }

    LaunchedEffect(career, loaded) {
        if (!loaded) return@LaunchedEffect
        graph.appState.put("vans-sk8-pool-service", career.encode())
    }

    val inSession = screen == SkateScreen.SESSION
    val tilt = rememberTilt(enabled = inSession && !paused && session?.finished == false)

    // Pausing mid-grind must not strand the session in GRINDING: the gesture
    // handler used to be detached with the release handler still pending (A-26).
    LaunchedEffect(paused) {
        if (paused) {
            val current = session
            if (current != null && current.phase == SkatePhase.GRINDING) {
                session = SkateEngine.endGrind(current)
            }
        }
    }

    LaunchedEffect(screen, paused) {
        if (screen != SkateScreen.SESSION || paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val current = session ?: return@withFrameNanos
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
                last = now
                if (current.finished) return@withFrameNanos
                val tiltValue = tilt.value
                val before = current
                val next = SkateEngine.step(SkateEngine.tilt(current, tiltX(tiltValue), tiltY(tiltValue)), dt)
                session = next
                playCues(bank, before, next)
                if (next.finished) {
                    lastResult = next
                    resultNewBest = next.score > career.bestFor(next.event)
                    if (!recorded) {
                        recorded = true
                        val finishedEvent = next.event
                        val finishedScore = next.score
                        val finishedPool = next.pool.id
                        scope.launch {
                            graph.games.record(
                                "vans-sk8-pool-service",
                                finishedScore,
                                "${finishedEvent.key}|$finishedPool|${SkateEngine.starsFor(finishedEvent, finishedScore)}",
                            )
                            career = career.applyResult(finishedEvent, finishedScore)
                        }
                    }
                    screen = SkateScreen.RESULTS
                }
            }
        }
    }

    fun startSession(event: SkateEventId) {
        session = SkateEngine.newSession(
            event = event,
            poolId = career.poolId,
            skaterId = career.skaterId,
            boardId = career.boardId,
            wheelId = career.wheelId,
            seed = (System.currentTimeMillis() and 0x7fffffff).toInt(),
        )
        pendingEvent = event
        recorded = false
        paused = false
        lastResult = null
        screen = SkateScreen.SESSION
    }

    when (screen) {
        SkateScreen.MENU -> DetailScaffold(title = "vans sk8 pool service") {
            SkateMenu(
                career = career,
                topScores = topScores.map { it.score },
                onCareer = { screen = SkateScreen.CAREER },
                onFreeRide = { pendingEvent = SkateEventId.FREE_RIDE; startSession(SkateEventId.FREE_RIDE) },
                onLoadout = { screen = SkateScreen.LOADOUT },
                onTricks = { screen = SkateScreen.TRICKS },
            )
        }

        SkateScreen.CAREER -> DetailScaffold(title = "career", onBack = { screen = SkateScreen.MENU }) {
            SkateCareerMenu(career) { event ->
                pendingEvent = event
                screen = SkateScreen.LOADOUT
            }
        }

        SkateScreen.LOADOUT -> DetailScaffold(title = "loadout", onBack = { screen = SkateScreen.MENU }) {
            SkateLoadout(
                career = career,
                event = pendingEvent,
                onChange = { career = it },
                onStart = { startSession(pendingEvent) },
                onBack = { screen = SkateScreen.MENU },
            )
        }

        SkateScreen.TRICKS -> DetailScaffold(title = "trick list", onBack = { screen = SkateScreen.MENU }) {
            SkateTrickList()
        }

        SkateScreen.SESSION -> {
            val current = session
            if (current == null) {
                SkateMessage("no session")
            } else {
                DetailScaffold(
                    title = SkateContent.eventTitle(current.event),
                    onBack = { paused = !paused },
                ) {
                    val assets = remember(current.pool.id) { PoolAssets(current.pool) }
                    DisposableEffect(current, paused) {
                        scene.rebuild {
                            backgroundColor = Color4.rgb(18, 20, 26)
                            ambient = 0.36f
                            lightDirection = Vec3(-0.35f, -1f, -0.28f)
                            buildSkateScene(this, current, assets)
                        }
                        onDispose { }
                    }
                    // The handler stays attached while paused; it reads the live
                    // enabled flag so a pause can still deliver the release (A-26).
                    val gesturesEnabled = rememberUpdatedState(!paused && !current.finished)
                    Box(Modifier.fillMaxSize().background(colors.background)) {
                        Scene3dView(scene = scene, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier
                                .fillMaxSize()
                                .skateGestures(
                                    enabled = { gesturesEnabled.value },
                                    onSwipe = { swipe ->
                                        val snapshot = session
                                        if (snapshot != null) session = SkateEngine.swipe(snapshot, swipe)
                                    },
                                    onTap = {
                                        val snapshot = session
                                        if (snapshot != null) session = SkateEngine.push(snapshot)
                                    },
                                    onGrind = {
                                        val snapshot = session
                                        if (snapshot != null &&
                                            SkateEngine.canStartGrind(snapshot) &&
                                            snapshot.phase != SkatePhase.GRINDING
                                        ) {
                                            session = SkateEngine.startGrind(snapshot)
                                        }
                                    },
                                    onRelease = {
                                        val snapshot = session
                                        if (snapshot != null && snapshot.phase == SkatePhase.GRINDING) {
                                            session = SkateEngine.endGrind(snapshot)
                                        }
                                    },
                                ),
                        )
                        SkateHud(current)
                        if (paused) {
                            SkateOverlay(
                                title = "paused",
                                "resume" to { paused = false },
                                "restart" to { startSession(current.event) },
                                "end run" to {
                                    val stopped = SkateEngine.stop(current)
                                    session = stopped
                                    lastResult = stopped
                                    resultNewBest = stopped.score > career.bestFor(stopped.event)
                                    if (!recorded) {
                                        recorded = true
                                        val endedEvent = stopped.event
                                        val endedScore = stopped.score
                                        val endedPool = stopped.pool.id
                                        scope.launch {
                                            graph.games.record(
                                                "vans-sk8-pool-service",
                                                endedScore,
                                                "${endedEvent.key}|$endedPool|${SkateEngine.starsFor(endedEvent, endedScore)}",
                                            )
                                            career = career.applyResult(endedEvent, endedScore)
                                        }
                                    }
                                    screen = SkateScreen.RESULTS
                                },
                            )
                        }
                    }
                }
            }
        }

        SkateScreen.RESULTS -> {
            val result = lastResult
            DetailScaffold(title = "results", onBack = { screen = SkateScreen.MENU }) {
                if (result == null) {
                    SkateMessage("no result")
                } else {
                    SkateResults(
                        result = result,
                        newBest = resultNewBest,
                        career = career,
                        onRetry = { startSession(result.event) },
                        onMenu = { screen = SkateScreen.MENU },
                    )
                }
            }
        }
    }
}

private fun playCues(bank: SfxBank, before: SkateState, after: SkateState) {
    if (after.phase != before.phase) {
        when (after.phase) {
            SkatePhase.IN_AIR -> bank.play("whoosh")
            SkatePhase.GRINDING -> bank.play("hit")
            SkatePhase.CRASHING -> bank.play("explode")
            SkatePhase.FINISHED -> bank.play(if (after.result == SkateResult.SUCCESS) "win" else "lose")
            SkatePhase.IN_POOL -> Unit
        }
    }
    if (after.combo.size > before.combo.size) bank.play("coin")
    if (after.perfectLandings > before.perfectLandings) bank.play("score")
    if (after.score > before.score && after.phase == SkatePhase.IN_POOL && before.phase == SkatePhase.IN_AIR) bank.play("score")
    if (after.grindSwitches > before.grindSwitches) bank.play("rotate")
    if (after.collected.size > before.collected.size) bank.play("pop")
}

private fun Modifier.skateGestures(
    enabled: () -> Boolean,
    onSwipe: (SkateSwipe) -> Unit,
    onTap: () -> Unit,
    onGrind: () -> Unit,
    onRelease: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var start: Offset? = null
        var grinding = false
        while (true) {
            val event = awaitPointerEvent()
            if (!enabled()) {
                if (grinding) {
                    grinding = false
                    onRelease()
                }
                start = null
                event.changes.forEach { it.consume() }
                continue
            }
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2 && !grinding) {
                grinding = true
                onGrind()
            }
            if (pressed == 0) {
                val end = event.changes.firstOrNull()
                val from = start
                if (!grinding && end != null && from != null) {
                    val dx = end.position.x - from.x
                    val dy = end.position.y - from.y
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance >= 36.dp.toPx()) onSwipe(directionOf(dx, dy)) else onTap()
                }
                start = null
                grinding = false
                onRelease()
            } else if (start == null) {
                start = event.changes.firstOrNull { it.pressed }?.position
            }
            event.changes.forEach { it.consume() }
        }
    }
}

private fun buildSkateScene(scene: Scene3d, state: SkateState, assets: PoolAssets) {
    val pool = state.pool
    scene.add(nodeAt(assets.deck, y = pool.lip, material = DECK_MATERIAL))
    scene.add(nodeAt(assets.bowl, material = BASIN_MATERIAL))
    pool.rails.forEachIndexed { index, edge ->
        val yaw = atan2(edge.bx - edge.ax, edge.bz - edge.az)
        scene.add(
            nodeAt(
                assets.rails[index],
                x = (edge.ax + edge.bx) / 2f,
                y = pool.lip + 0.06f,
                z = (edge.az + edge.bz) / 2f,
                yawRad = yaw,
                material = RAIL_MATERIAL,
            ),
        )
    }
    pool.ramps.forEachIndexed { index, ramp ->
        scene.add(
            nodeAt(
                assets.ramps[index],
                x = ramp.x,
                y = pool.lip + 0.1f,
                z = ramp.z,
                pitchRad = -ramp.launch,
                material = RAMP_MATERIAL,
            ),
        )
    }

    if (state.event == SkateEventId.POOL_CLEANER) {
        SkateEngine.bagPositions(pool, state.seed).forEachIndexed { index, bag ->
            if (index in state.collected) return@forEachIndexed
            scene.add(
                nodeAt(
                    bagMesh,
                    x = bag.x,
                    y = SkateEngine.surfaceHeight(pool, bag.x, bag.z) + 0.24f,
                    z = bag.z,
                    material = BAG_MATERIAL,
                ),
            )
        }
    }

    val yaw = atan2(state.direction.x, state.direction.z)
    val roll = when (state.phase) {
        SkatePhase.IN_AIR -> 0f
        SkatePhase.GRINDING -> state.balancePoint / SkateEngine.BALANCE_MAX_VALUE * 0.4f
        else -> 0f
    }
    val spinTilt = if (state.phase == SkatePhase.IN_AIR) state.spinRad * 0.5f else 0f
    val bodyColor = SKATER_COLORS[SkateContent.SKATERS.indexOf(state.skater).coerceAtLeast(0) % SKATER_COLORS.size]
    val x = state.position.x
    val z = state.position.z
    val y = state.position.y
    scene.add(nodeAt(bodyMesh, x = x, y = y + 0.72f, z = z, yawRad = yaw, rollRad = roll + spinTilt, material = Material3d(bodyColor)))
    scene.add(nodeAt(headMesh, x = x, y = y + 1.18f, z = z, yawRad = yaw, rollRad = roll + spinTilt, material = Material3d(Color4.rgb(226, 190, 158))))
    scene.add(nodeAt(boardMesh, x = x, y = y + 0.34f, z = z, yawRad = yaw, rollRad = roll + spinTilt, material = BOARD_MATERIAL))
    val lateral = 0.19f
    val long = 0.46f
    for (sx in listOf(-lateral, lateral)) {
        for (sz in listOf(-long, long)) {
            val ox = sx * cos(yaw) + sz * sin(yaw)
            val oz = -sx * sin(yaw) + sz * cos(yaw)
            scene.add(nodeAt(wheelMesh, x = x + ox, y = y + 0.22f, z = z + oz, yawRad = yaw, material = WHEEL_MATERIAL))
        }
    }

    val back = Vec3(-state.direction.x, 0f, -state.direction.z).normalized()
    scene.camera = Camera3d(
        eye = state.position + back * 6.8f + Vec3(0f, 3.6f, 0f),
        target = state.position + state.direction * 1.4f + Vec3(0f, 1.1f, 0f),
        fovYDeg = 58f,
    )
}

@Composable
private fun SkateHud(state: SkateState) {
    val colors = LocalDoradoColors.current
    val def = SkateContent.event(state.event)
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "score ${state.score}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "x${state.multiplier}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = colors.accent),
            )
            Spacer(Modifier.weight(1f))
            if (def.timerSeconds > 0f) {
                BasicText(
                    text = formatClock(state.eventTimerMs),
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textSecondary),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = objectiveLabel(state),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = state.phase.name.lowercase(),
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
            )
        }
        if (state.combo.isNotEmpty()) {
            BasicText(
                text = state.combo.joinToString(" + ") { it.name },
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accentBright),
            )
        }
        if (state.phase == SkatePhase.GRINDING) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(150.dp).height(8.dp).background(colors.elevated))
                Box(
                    Modifier
                        .offset(x = ((state.balancePoint / SkateEngine.BALANCE_MAX_VALUE) * 70f).dp)
                        .width(3.dp)
                        .height(10.dp)
                        .background(colors.accentBright),
                )
            }
            BasicText(
                text = state.grindKind.label,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
        Spacer(Modifier.weight(1f))
        state.landingFeedback?.let { feedback ->
            BasicText(
                text = feedback,
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
        }
    }
}

private fun objectiveLabel(state: SkateState): String {
    val def = SkateContent.event(state.event)
    return when (def.objective) {
        SkateObjective.FREE_RIDE -> "free ride"
        SkateObjective.SCORE -> "target ${def.target}"
        SkateObjective.NO_BAILS -> "target ${def.target} · no bails"
        SkateObjective.COLLECT_BAGS -> "bags ${state.collected.size}/${state.pool.bags.size}"
        SkateObjective.SPIN -> "spin ${state.spinTotalDeg} of ${def.target}"
        SkateObjective.TRICK_CHAIN -> "letters ${state.chainIndex}/${SkateContent.SKATE_CHAIN.size}"
        SkateObjective.UNIQUE_TRICKS -> "tricks ${state.collectedTrickIds.distinct().size}/${def.target}"
    }
}

private fun formatClock(ms: Float): String {
    val total = (ms / 1000f).coerceAtLeast(0f).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun SkateOverlay(title: String, vararg actions: Pair<String, () -> Unit>) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated.copy(alpha = 0.94f))
                .border(0.5.dp, colors.border)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> SkateButton(label, action) }
            }
        }
    }
}

@Composable
private fun SkateMessage(text: String) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.textSecondary),
        )
    }
}

@Composable
private fun SkateMenu(
    career: SkateCareer,
    topScores: List<Int>,
    onCareer: () -> Unit,
    onFreeRide: () -> Unit,
    onLoadout: () -> Unit,
    onTricks: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        BasicText(
            text = "stars ${career.totalStars} · ${career.earnedAchievements().size} achievements · ${career.unlockedVideos().size} videos",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.height(8.dp))
        SkateButton("career", onCareer)
        Spacer(Modifier.height(4.dp))
        SkateButton("free ride", onFreeRide)
        Spacer(Modifier.height(4.dp))
        SkateButton("skater & gear", onLoadout)
        Spacer(Modifier.height(4.dp))
        SkateButton("trick list", onTricks)
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = if (topScores.isEmpty()) "no runs yet" else "best ${topScores.joinToString(" · ")}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "tap to push · swipe in the air for tricks · two fingers to grind · tilt to steer and balance",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
        )
    }
}

@Composable
private fun SkateCareerMenu(career: SkateCareer, onPick: (SkateEventId) -> Unit) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SkateContent.EVENTS.forEach { def ->
            val unlocked = career.isEventUnlocked(def.id)
            val action: (() -> Unit)? = if (unlocked) { { onPick(def.id) } } else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .border(0.5.dp, if (unlocked) colors.border else colors.elevated)
                        .tapAction(action)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Column {
                        BasicText(
                            text = def.title,
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (unlocked) colors.textPrimary else colors.textInactive,
                            ),
                        )
                        BasicText(
                            text = if (unlocked) {
                                val stars = career.starsFor(def.id)
                                "stars $stars/3 · best ${career.bestFor(def.id)}"
                            } else {
                                "locked — earn a star in ${SkateContent.eventTitle(def.unlockAfter ?: def.id)}"
                            },
                            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkateLoadout(
    career: SkateCareer,
    event: SkateEventId,
    onChange: (SkateCareer) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        BasicText(
            text = "event ${SkateContent.eventTitle(event)}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "pool",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        SkateContent.POOLS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { pool ->
                    val unlocked = career.isPoolUnlocked(pool)
                    SkateChip(
                        label = if (unlocked) pool.name else "${pool.name} (${pool.unlockStars})",
                        selected = career.poolId == pool.id,
                        enabled = unlocked,
                    ) {
                        onChange(career.withLoadout(pool.id, career.skaterId, career.boardId, career.wheelId))
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "skater",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SkateContent.SKATERS.forEach { skater ->
                SkateChip(skater.name, career.skaterId == skater.id, true) {
                    onChange(career.withLoadout(career.poolId, skater.id, career.boardId, career.wheelId))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SkateGearRow(
            label = "board",
            gears = SkateContent.BOARDS,
            selectedId = career.boardId,
            isUnlocked = { career.isBoardUnlocked(it) },
        ) { id -> onChange(career.withLoadout(career.poolId, career.skaterId, id, career.wheelId)) }
        SkateGearRow(
            label = "wheels",
            gears = SkateContent.WHEELS,
            selectedId = career.wheelId,
            isUnlocked = { career.isWheelUnlocked(it) },
        ) { id -> onChange(career.withLoadout(career.poolId, career.skaterId, career.boardId, id)) }
        Spacer(Modifier.weight(1f))
        val stats = SkateEngine.statsFor(SkateContent.board(career.boardId), SkateContent.wheel(career.wheelId))
        BasicText(
            text = "spin %.2f · balance %.2f · control %.2f · landing %.2f · speed %.2f".format(
                stats.spin, stats.balance, stats.control, stats.landing, stats.speed,
            ),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkateButton("back", onBack)
            SkateButton("skate", onStart)
        }
    }
}

@Composable
private fun SkateGearRow(
    label: String,
    gears: List<SkateGear>,
    selectedId: String,
    isUnlocked: (SkateGear) -> Boolean,
    onPick: (String) -> Unit,
) {
    val colors = LocalDoradoColors.current
    BasicText(
        text = label,
        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
    )
    gears.chunked(7).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            row.forEach { gear ->
                SkateChip(
                    label = if (isUnlocked(gear)) gear.name else "${gear.name} (${gear.unlockStars})",
                    selected = selectedId == gear.id,
                    enabled = isUnlocked(gear),
                ) { onPick(gear.id) }
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun SkateTrickList() {
    val colors = LocalDoradoColors.current
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SkateTricks.ALL.forEach { trick ->
            BasicText(
                text = "${trick.name}  ${trick.points}${if (trick.grind) " · grind" else ""}${if (trick.special) " · special" else ""}",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = if (trick.special) colors.accent else colors.textPrimary),
            )
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "chains: up up · down down · left left · right right · up down · down up · left right · right left · up left · up right",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
    }
}

@Composable
private fun SkateResults(
    result: SkateState,
    newBest: Boolean,
    career: SkateCareer,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    val stars = SkateEngine.starsFor(result.event, result.score)
    Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
        BasicText(
            text = if (result.result == SkateResult.SUCCESS) "run complete" else "run failed",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_TITLE.sp, color = if (result.result == SkateResult.SUCCESS) colors.accent else colors.textPrimary),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "${SkateContent.eventTitle(result.event)} · ${result.pool.name}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = "score ${result.score}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_HEADER_CROPPED.sp, color = colors.textPrimary),
        )
        BasicText(
            text = "stars $stars/3 · best ${career.bestFor(result.event)}${if (newBest) " · new best" else ""}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.accent),
        )
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = objectiveLabel(result),
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
        )
        BasicText(
            text = "perfect landings ${result.perfectLandings} · grinds ${result.grindCount} · bails ${result.crashes}",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
        )
        val newAchievements = career.earnedAchievements().size
        val newVideos = career.unlockedVideos().size
        if (newAchievements > 0 || newVideos > 0) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "unlocked $newAchievements achievements · $newVideos videos",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkateButton("retry", onRetry)
            SkateButton("menu", onMenu)
        }
    }
}

@Composable
private fun SkateButton(label: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .height(28.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .tapAction(onClick)
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
private fun SkateChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .background(if (selected) colors.accent else colors.tile)
            .tapAction(if (enabled) onClick else null)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = when {
                    !enabled -> colors.textInactive
                    selected -> colors.background
                    else -> colors.textSecondary
                },
            ),
        )
    }
}

private fun Modifier.tapAction(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
