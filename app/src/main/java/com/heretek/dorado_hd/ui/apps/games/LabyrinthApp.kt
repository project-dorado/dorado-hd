package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.TiltState
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Mat4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.MeshData
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3dView
import com.heretek.dorado_hd.ui.apps.engine3d.SceneNode
import com.heretek.dorado_hd.ui.apps.engine3d.TextureData
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.rememberTilt
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val LAB_SAVE_KEY = "labyrinth.progress"
private const val LAB_TOUCH_RADIUS = 70f

private enum class LabControl { AUTO, TILT, TOUCH }

private class LabPalette(
    val floorA: Int,
    val floorB: Int,
    val wall: Int,
    val hole: Int,
    val holeRim: Int,
    val goalClosed: Color4,
    val goalOpen: Color4,
    val accentBright: Color4,
    val ball: Color4,
)

@Composable
fun LabyrinthApp() {
    val colors = LocalDoradoColors.current
    val graph = LocalDoradoGraph.current
    val audioScope = rememberCoroutineScope()
    val synth = remember { MiniSynth(audioScope) }
    val bank = remember { SfxBank(synth) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }

    var screen by remember { mutableStateOf("menu") }
    var progress by remember { mutableStateOf(LabyrinthProgress()) }
    var loaded by remember { mutableStateOf(false) }
    var levelId by remember { mutableStateOf(0) }
    var game by remember { mutableStateOf<LabyrinthGame?>(null) }
    var paused by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var control by remember { mutableStateOf(LabControl.AUTO) }
    var touchTilt by remember { mutableStateOf(Offset.Zero) }
    var clockMs by remember { mutableStateOf(0L) }
    var newBest by remember { mutableStateOf(false) }
    val best by graph.games.top("labyrinth", 8).collectAsState(initial = emptyList())

    val scene = remember { Scene3d() }
    val ballMesh = remember { unitSphere() }
    val boxMesh = remember { MeshFactory.box(1f, 1f, 1f) }
    val planeMesh = remember { MeshFactory.plane(1f, 1f) }
    val discMesh = remember { unitDisc() }
    val palette = remember(colors) {
        LabPalette(
            floorA = argb(0.10f, 0.16f, 0.12f),
            floorB = argb(0.13f, 0.20f, 0.15f),
            wall = argb(0.42f, 0.50f, 0.44f),
            hole = argb(0.02f, 0.02f, 0.03f),
            holeRim = argb(0.30f, 0.26f, 0.22f),
            goalClosed = Color4(0.35f, 0.35f, 0.38f),
            goalOpen = Color4(colors.accentBright.red, colors.accentBright.green, colors.accentBright.blue),
            accentBright = Color4(colors.accentBright.red, colors.accentBright.green, colors.accentBright.blue),
            ball = Color4(0.94f, 0.55f, 0.16f),
        )
    }
    val staticNodes = remember(game?.levelId, palette) {
        game?.let { buildStaticNodes(it.maze, palette, planeMesh, boxMesh, discMesh) } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        progress = LabyrinthEngine.decodeProgress(graph.appState.get(LAB_SAVE_KEY))
        levelId = progress.highestUnlocked
        loaded = true
    }

    LaunchedEffect(progress, loaded) {
        if (loaded) graph.appState.put(LAB_SAVE_KEY, LabyrinthEngine.encodeProgress(progress))
    }

    LaunchedEffect(game?.status, recorded) {
        val current = game ?: return@LaunchedEffect
        if (current.status != LabyrinthStatus.COMPLETE || recorded) return@LaunchedEffect
        recorded = true
        val previous = progress.bestMs[current.levelId]
        newBest = previous == null || current.elapsedMs < previous
        progress = LabyrinthEngine.complete(progress, current.levelId, current.elapsedMs, current.stars)
        val seconds = (current.elapsedMs / 1000.0).toInt()
        val score = current.stars * 100_000 + (99_999 - min(seconds, 99_999))
        graph.games.record("labyrinth", score, "level ${current.levelId + 1}")
    }

    val tilt = rememberTilt(
        enabled = screen == "game" && !paused && control != LabControl.TOUCH,
        smoothing = 0.86f,
    )

    fun startLevel(id: Int) {
        levelId = id
        game = LabyrinthEngine.newGame(id, progress.tiltSetting)
        recorded = false
        newBest = false
        paused = false
        touchTilt = Offset.Zero
        screen = "game"
    }

    LaunchedEffect(screen, paused, game?.levelId) {
        if (screen != "game") return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                } else {
                    val dt = ((now - last) / 1_000_000.0).coerceIn(0.0, 60.0)
                    last = now
                    clockMs = now / 1_000_000L
                    val current = game
                    if (current != null && !paused && current.status == LabyrinthStatus.PLAYING) {
                        val useTouch = control == LabControl.TOUCH || (control == LabControl.AUTO && !tilt.value.available)
                        val vector = activeTilt(tilt.value, useTouch, touchTilt)
                        val next = LabyrinthEngine.step(current, dt, vector.first, vector.second)
                        for (event in next.events) bank.play(soundFor(event))
                        game = next
                        renderFrame(scene, staticNodes, next, palette, ballMesh, discMesh, clockMs)
                    } else if (current != null) {
                        renderFrame(scene, staticNodes, current, palette, ballMesh, discMesh, clockMs)
                    }
                }
            }
        }
    }

    when (screen) {
        "menu" -> {
            LabMenu(
                colors = colors,
                progress = progress,
                best = best.firstOrNull()?.score ?: 0,
                onPlay = { screen = "levels" },
                onOptions = { screen = "options" },
                onAbout = { screen = "about" },
            )
            return
        }

        "levels" -> {
            LabLevelSelect(
                colors = colors,
                progress = progress,
                onBack = { screen = "menu" },
                onPick = { startLevel(it) },
            )
            return
        }

        "options" -> {
            LabOptions(
                colors = colors,
                progress = progress,
                control = control,
                onTilt = { progress = progress.copy(tiltSetting = it) },
                onControl = { control = it },
                onReset = {
                    progress = LabyrinthProgress()
                    levelId = 0
                },
                onBack = { screen = "menu" },
            )
            return
        }

        "about" -> {
            LabAbout(colors = colors, onBack = { screen = "menu" })
            return
        }
    }

    val current = game ?: return
    DetailScaffold(title = "labyrinth", onBack = { paused = true }) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Scene3dView(
                scene = scene,
                modifier = Modifier
                    .fillMaxSize()
                    .appDescription(
                        "labyrinth, ${current.maze.name}, pickups ${current.collected.size} of ${current.maze.pickups.size}, deaths ${current.deaths}",
                    ),
            )
            Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .appDescription(
                            "${current.maze.name}, ${formatTime(current.elapsedMs)}, pickups ${current.collected.size} of ${current.maze.pickups.size}, deaths ${current.deaths}",
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = "${current.maze.name} · ${formatTime(current.elapsedMs)}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_NOW_META.sp, color = colors.accent),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = "pickups ${current.collected.size}/${current.maze.pickups.size}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textPrimary),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        text = "deaths ${current.deaths}",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                    )
                }
                if (current.goalOpen) {
                    BasicText(
                        text = "goal open",
                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accentBright),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (control == LabControl.TOUCH || !tilt.value.available) {
                    LabTouchPad { touchTilt = it }
                }
            }
            if (paused && current.status == LabyrinthStatus.PLAYING) {
                LabOverlay(
                    title = "paused",
                    lines = listOf("${current.maze.name} · ${formatTime(current.elapsedMs)}"),
                    actions = listOf(
                        "resume" to { paused = false },
                        "restart" to { startLevel(current.levelId) },
                        "levels" to { screen = "levels"; game = null },
                    ),
                )
            }
            if (current.status == LabyrinthStatus.COMPLETE) {
                val next = current.levelId + 1
                LabOverlay(
                    title = if (newBest) "new best time" else "level complete",
                    lines = listOf(
                        "${formatTime(current.elapsedMs)} · par ${current.maze.parSeconds}s · par ${current.stars}/3",
                        "best ${formatTime(progress.bestMs[current.levelId] ?: current.elapsedMs)}",
                    ),
                    actions = buildList {
                        if (next < LabyrinthEngine.LEVEL_COUNT) add("next" to { startLevel(next) })
                        add("replay" to { startLevel(current.levelId) })
                        add("levels" to { screen = "levels"; game = null })
                    },
                )
            }
        }
    }
}

private fun activeTilt(tiltState: TiltState, useTouch: Boolean, touch: Offset): Pair<Double, Double> {
    if (useTouch) {
        val tx = (-touch.x / LAB_TOUCH_RADIUS).toDouble().coerceIn(-1.0, 1.0)
        val ty = (-touch.y / LAB_TOUCH_RADIUS).toDouble().coerceIn(-1.0, 1.0)
        return tx to ty
    }
    if (!tiltState.available) return 0.0 to 0.0
    return LabyrinthEngine.tiltVector(tiltState.rollDeg.toDouble(), tiltState.pitchDeg.toDouble())
}

private fun soundFor(event: LabyrinthEvent): String = when (event) {
    LabyrinthEvent.WALL -> "hit"
    LabyrinthEvent.PICKUP -> "coin"
    LabyrinthEvent.CHECKPOINT -> "tick"
    LabyrinthEvent.FALL -> "lose"
    LabyrinthEvent.GOAL_OPEN -> "score"
    LabyrinthEvent.COMPLETE -> "win"
}

private fun formatTime(ms: Double): String {
    val total = (ms / 1000.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun argb(r: Float, g: Float, b: Float): Int {
    val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
    val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
    val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}

private fun intColor(value: Int): Color4 = Color4(
    ((value shr 16) and 0xFF) / 255f,
    ((value shr 8) and 0xFF) / 255f,
    (value and 0xFF) / 255f,
)

private fun unitSphere(rings: Int = 12, segments: Int = 18): MeshData {
    val positions = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
    val normals = ArrayList<Float>((rings + 1) * (segments + 1) * 3)
    val uvs = ArrayList<Float>((rings + 1) * (segments + 1) * 2)
    val indices = ArrayList<Int>(rings * segments * 6)
    for (ring in 0..rings) {
        val phi = PI * ring / rings
        for (segment in 0..segments) {
            val theta = 2.0 * PI * segment / segments
            val x = (sin(phi) * cos(theta)).toFloat()
            val y = cos(phi).toFloat()
            val z = (sin(phi) * sin(theta)).toFloat()
            positions += x
            positions += y
            positions += z
            normals += x
            normals += y
            normals += z
            uvs += (segment.toFloat() / segments)
            uvs += 1f - ring.toFloat() / rings
        }
    }
    for (ring in 0 until rings) {
        for (segment in 0 until segments) {
            val a = ring * (segments + 1) + segment
            val b = a + segments + 1
            indices += a
            indices += b
            indices += a + 1
            indices += a + 1
            indices += b
            indices += b + 1
        }
    }
    return MeshData(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

private fun unitDisc(segments: Int = 24): MeshData {
    val positions = ArrayList<Float>((segments + 2) * 3)
    val normals = ArrayList<Float>((segments + 2) * 3)
    val uvs = ArrayList<Float>((segments + 2) * 2)
    val indices = ArrayList<Int>(segments * 3)
    positions += 0f
    positions += 0f
    positions += 0f
    normals += 0f
    normals += 1f
    normals += 0f
    uvs += 0.5f
    uvs += 0.5f
    for (segment in 0..segments) {
        val angle = 2.0 * PI * segment / segments
        val x = cos(angle).toFloat()
        val z = sin(angle).toFloat()
        positions += x
        positions += 0f
        positions += z
        normals += 0f
        normals += 1f
        normals += 0f
        uvs += (x + 1f) * 0.5f
        uvs += (z + 1f) * 0.5f
    }
    for (segment in 0 until segments) {
        indices += 0
        indices += segment + 1
        indices += segment + 2
    }
    return MeshData(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

private fun boardTexture(maze: LabyrinthMaze, palette: LabPalette): TextureData {
    val pixelsPerCell = 6
    val width = maze.cols * pixelsPerCell
    val height = maze.rows * pixelsPerCell
    val pixels = IntArray(width * height)
    for (row in 0 until maze.rows) {
        for (col in 0 until maze.cols) {
            val base = if ((row + col) % 2 == 0) palette.floorA else palette.floorB
            for (py in 0 until pixelsPerCell) {
                for (px in 0 until pixelsPerCell) {
                    val x = col * pixelsPerCell + px
                    val y = row * pixelsPerCell + py
                    pixels[y * width + x] = base
                }
            }
        }
    }
    return TextureData(width, height, pixels)
}

private fun buildStaticNodes(
    maze: LabyrinthMaze,
    palette: LabPalette,
    planeMesh: MeshData,
    boxMesh: MeshData,
    discMesh: MeshData,
): List<SceneNode> {
    val nodes = ArrayList<SceneNode>()
    val halfCols = maze.cols / 2f
    val halfRows = maze.rows / 2f
    nodes += SceneNode(
        planeMesh,
        Material3d(Color4(1f, 1f, 1f), texture = boardTexture(maze, palette), unlit = true),
        Mat4.translation(Vec3(0f, 0f, 0f)) * Mat4.scale(Vec3(maze.cols.toFloat(), 1f, maze.rows.toFloat())),
    )
    for (wall in maze.walls) {
        val cx = ((wall.x + wall.w / 2.0) / LabyrinthEngine.CELL - halfCols).toFloat()
        val cz = ((wall.y + wall.h / 2.0) / LabyrinthEngine.CELL - halfRows).toFloat()
        val sx = (wall.w / LabyrinthEngine.CELL).toFloat()
        val sz = (wall.h / LabyrinthEngine.CELL).toFloat()
        nodes += SceneNode(
            boxMesh,
            Material3d(intColor(palette.wall)),
            Mat4.translation(Vec3(cx, 0.35f, cz)) * Mat4.scale(Vec3(sx, 0.7f, sz)),
            tag = "wall",
        )
    }
    for (hole in maze.holes) {
        nodes += SceneNode(
            discMesh,
            Material3d(intColor(palette.holeRim), unlit = true),
            Mat4.translation(worldOf(hole, maze)) * Mat4.scale(Vec3(0.55f, 1f, 0.55f)),
        )
        nodes += SceneNode(
            discMesh,
            Material3d(intColor(palette.hole), unlit = true),
            Mat4.translation(worldOf(hole, maze).copy(y = 0.03f)) * Mat4.scale(Vec3(0.42f, 1f, 0.42f)),
        )
    }
    for (spike in maze.spikes) {
        nodes += SceneNode(
            boxMesh,
            Material3d(Color4(0.72f, 0.74f, 0.78f)),
            Mat4.translation(worldOf(spike, maze).copy(y = 0.22f)) * Mat4.scale(Vec3(0.22f, 0.45f, 0.22f)),
        )
    }
    return nodes
}

private fun worldOf(point: LabyrinthPoint, maze: LabyrinthMaze): Vec3 = Vec3(
    (point.x / LabyrinthEngine.CELL - maze.cols / 2f).toFloat(),
    0.02f,
    (point.y / LabyrinthEngine.CELL - maze.rows / 2f).toFloat(),
)

private fun renderFrame(
    scene: Scene3d,
    staticNodes: List<SceneNode>,
    game: LabyrinthGame,
    palette: LabPalette,
    ballMesh: MeshData,
    discMesh: MeshData,
    timeMs: Long,
) {
    val maze = game.maze
    scene.clear()
    scene.backgroundColor = Color4(0.03f, 0.035f, 0.03f)
    scene.ambient = 0.55f
    scene.lightDirection = Vec3(-0.35f, -1f, -0.25f)
    scene.fogDensity = 0f
    scene.addAll(staticNodes)

    scene.add(
        SceneNode(
            discMesh,
            Material3d(palette.accentBright, unlit = true),
            Mat4.translation(worldOf(maze.checkpoint, maze)) * Mat4.scale(Vec3(0.5f, 1f, 0.5f)),
        ),
    )
    val goalTint = if (game.goalOpen) palette.goalOpen else palette.goalClosed
    scene.add(
        SceneNode(
            discMesh,
            Material3d(goalTint, unlit = true),
            Mat4.translation(worldOf(maze.goal, maze)) * Mat4.scale(Vec3(0.72f, 1f, 0.72f)),
        ),
    )

    val bob = (sin(timeMs * 0.004) * 0.05).toFloat()
    for (pickup in maze.pickups) {
        if (pickup.id in game.collected) continue
        val tint = when (pickup.kind) {
            LabyrinthPickupKind.APPLE -> Color4(0.86f, 0.30f, 0.26f)
            LabyrinthPickupKind.AXE -> Color4(0.58f, 0.62f, 0.68f)
            LabyrinthPickupKind.FLOWER -> Color4(0.86f, 0.48f, 0.72f)
            LabyrinthPickupKind.HEART -> Color4(0.90f, 0.32f, 0.46f)
            LabyrinthPickupKind.KEY -> Color4(0.88f, 0.78f, 0.34f)
        }
        scene.add(
            SceneNode(
                ballMesh,
                Material3d(tint, emissive = 0.35f),
                Mat4.translation(worldOf(LabyrinthPoint(pickup.x, pickup.y), maze).copy(y = 0.24f + bob)) *
                    Mat4.scale(Vec3(0.17f, 0.17f, 0.17f)),
            ),
        )
    }

    val ball = game.ball
    val ballWorld = Vec3(
        (ball.x / LabyrinthEngine.CELL - maze.cols / 2f).toFloat(),
        (LabyrinthEngine.BALL_RADIUS / LabyrinthEngine.CELL).toFloat(),
        (ball.y / LabyrinthEngine.CELL - maze.rows / 2f).toFloat(),
    )
    val radius = ballWorld.y
    scene.add(
        SceneNode(
            discMesh,
            Material3d(Color4(0f, 0f, 0f, 0.35f), unlit = true),
            Mat4.translation(Vec3(ballWorld.x, 0.035f, ballWorld.z)) * Mat4.scale(Vec3(radius * 1.7f, 1f, radius * 1.7f)),
        ),
    )
    scene.add(
        SceneNode(
            ballMesh,
            Material3d(palette.ball, emissive = 0.12f),
            Mat4.translation(ballWorld) * Mat4.scale(Vec3(radius, radius, radius)),
        ),
    )

    scene.camera = scene.camera.copy(
        eye = Vec3(ballWorld.x, 10.5f, ballWorld.z + 8.5f),
        target = Vec3(ballWorld.x, 0f, ballWorld.z),
        fovYDeg = 52f,
    )
}

@Composable
private fun LabMenu(
    colors: DoradoColors,
    progress: LabyrinthProgress,
    best: Int,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onAbout: () -> Unit,
) {
    DetailScaffold(title = "labyrinth") {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LabButton("play", Modifier.fillMaxWidth(), onClick = onPlay)
            Spacer(Modifier.height(6.dp))
            LabButton("options", Modifier.fillMaxWidth(), onClick = onOptions)
            Spacer(Modifier.height(6.dp))
            LabButton("about", Modifier.fillMaxWidth(), onClick = onAbout)
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "acts cleared ${progress.completed.size}/${LabyrinthEngine.LEVEL_COUNT} · best $best",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "tilt to roll the marble. collect every pickup to open the goal. holes and spikes rewind to the last switch.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
        }
    }
}

@Composable
private fun LabLevelSelect(
    colors: DoradoColors,
    progress: LabyrinthProgress,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val unlocked = progress.highestUnlocked
    DetailScaffold(title = "labyrinth · acts", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LabyrinthPack.entries.forEach { pack ->
                BasicText(
                    text = pack.label,
                    style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.accent),
                )
                Spacer(Modifier.height(4.dp))
                val levels = (pack.firstLevel until pack.firstLevel + pack.count).toList()
                levels.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { id ->
                            val open = id <= unlocked
                            val stars = progress.stars[id] ?: 0
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1.5f)
                                    .background(if (open) colors.tile else colors.elevated)
                                    .border(0.5.dp, if (id in progress.completed) colors.accent else colors.border)
                                    .appTap(label = "level ${id - pack.firstLevel + 1}", enabled = open) { onPick(id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    BasicText(
                                        text = "level ${id - pack.firstLevel + 1}",
                                        style = TextStyle(
                                            fontFamily = Selawik,
                                            fontSize = DoradoTokens.TYPE_LIST.sp,
                                            color = if (open) colors.textPrimary else colors.textInactive,
                                        ),
                                    )
                                    BasicText(
                                        text = when {
                                            !open -> "locked"
                                            id in progress.completed -> "par $stars/3"
                                            else -> "open"
                                        },
                                        style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textSecondary),
                                    )
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LabOptions(
    colors: DoradoColors,
    progress: LabyrinthProgress,
    control: LabControl,
    onTilt: (Double) -> Unit,
    onControl: (LabControl) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "labyrinth · options", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
            BasicText(
                text = "tilt sensitivity ${(progress.tiltSetting * 100).toInt()}%",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (step in 0..10) {
                    val value = 0.5 + step * 0.1
                    val active = abs(value - progress.tiltSetting) < 0.001
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .background(if (active) colors.accent else colors.tile)
                            .border(0.5.dp, colors.border)
                            .appTap(label = "tilt ${(value * 100).toInt()}") { onTilt(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = "${(value * 100).toInt()}",
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                                color = if (active) colors.background else colors.textSecondary,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "control",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textPrimary),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabControl.entries.forEach { option ->
                    val active = option == control
                    Box(
                        Modifier
                            .background(if (active) colors.accent else colors.tile)
                            .border(0.5.dp, colors.border)
                            .appTap(label = option.name.lowercase()) { onControl(option) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        BasicText(
                            text = option.name.lowercase(),
                            style = TextStyle(
                                fontFamily = Selawik,
                                fontSize = DoradoTokens.TYPE_LIST.sp,
                                color = if (active) colors.background else colors.textPrimary,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "auto uses the accelerometer, touch draws a stick when no sensor exists",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
            Spacer(Modifier.height(12.dp))
            LabButton("clear progress", Modifier.fillMaxWidth(), onClick = onReset)
        }
    }
}

@Composable
private fun LabAbout(colors: DoradoColors, onBack: () -> Unit) {
    DetailScaffold(title = "labyrinth · about", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(DoradoTokens.EDGE.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = "five acts, ${LabyrinthEngine.LEVEL_COUNT} original mazes. every maze is generated from an authored seed; no device level data or art is used.",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_LIST.sp, color = colors.textSecondary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "made for dorado-hd · clean-room reimplementation",
                style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
            )
            Spacer(Modifier.height(12.dp))
            LabButton("back", Modifier.fillMaxWidth(), onClick = onBack)
        }
    }
}

@Composable
private fun LabTouchPad(onTilt: (Offset) -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .border(0.5.dp, colors.border)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onTilt(Offset.Zero) },
                    onDragCancel = { onTilt(Offset.Zero) },
                    onDrag = { change, _ ->
                        change.consume()
                        onTilt(change.position - Offset(size.width / 2f, size.height / 2f))
                    },
                )
            }
            .appDescription("touch steering pad, drag to tilt"),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "drag here to tilt",
            style = TextStyle(fontFamily = Selawik, fontSize = DoradoTokens.TYPE_CAPTION.sp, color = colors.textInactive),
        )
    }
}

@Composable
private fun LabButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Box(
        modifier
            .height(30.dp)
            .background(colors.tile)
            .border(0.5.dp, colors.border)
            .appTap(label = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textPrimary)
    }
}

@Composable
private fun LabOverlay(
    title: String,
    lines: List<String>,
    actions: List<Pair<String, () -> Unit>>,
) {
    val colors = LocalDoradoColors.current
    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(colors.elevated)
                .border(0.5.dp, colors.border)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp, color = colors.accent)
            lines.forEach { line ->
                Spacer(Modifier.height(4.dp))
                EdgeCropText(text = line, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { (label, action) -> LabButton(label) { action() } }
            }
        }
    }
}
