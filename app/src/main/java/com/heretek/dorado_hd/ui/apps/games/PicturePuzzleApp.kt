package com.heretek.dorado_hd.ui.apps.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.ui.apps.AppClock
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.apps.MiniSynth
import com.heretek.dorado_hd.ui.apps.SfxBank
import com.heretek.dorado_hd.ui.apps.appDescription
import com.heretek.dorado_hd.ui.apps.appHold
import com.heretek.dorado_hd.ui.apps.appTap
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.MeshData
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3dView
import com.heretek.dorado_hd.ui.apps.engine3d.TextureData
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.engine3d.nodeAt
import com.heretek.dorado_hd.ui.apps.engine3d.rebuild
import com.heretek.dorado_hd.ui.components.DetailScaffold
import kotlin.math.cos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PuzzleScreen { MENU, MIXUP, PLAY, DONE }

private val CATEGORY_LABELS = listOf(
    "animals", "architecture", "cartoons", "plants", "scenery", "patterns", "numbers",
)

/**
 * Cached procedural assets for the puzzle: full pictures, per-tile crops,
 * hint numbers and the shared meshes. Textures only depend on
 * category/grid/home-cell, so a move no longer regenerates any of them.
 */
private class PuzzleAssets {
    private val pictures = HashMap<Long, TextureData>()
    private val tiles = HashMap<Long, TextureData>()
    private val numbers = HashMap<Long, TextureData>()
    private val planes = HashMap<Int, MeshData>()

    val tileMesh: MeshData = MeshFactory.box(1f, 0.16f, 1f)
    val quadMesh: MeshData = MeshFactory.quad(1f, 1f)

    fun plane(grid: Int): MeshData = planes.getOrPut(grid) {
        val span = grid * 1.05f - 0.05f
        MeshFactory.plane(span + 0.5f, span + 0.5f)
    }

    fun picture(category: Int, index: Int): TextureData =
        pictures.getOrPut(pack(category, index, 0, 0, 0)) {
            PuzzleTextures.picture(category, index)
        }

    fun tile(picture: TextureData, category: Int, index: Int, grid: Int, row: Int, col: Int): TextureData =
        tiles.getOrPut(pack(category, index, grid, row, col)) {
            PuzzleTextures.tile(picture, grid, row, col)
        }

    fun number(value: Int, front: Boolean): TextureData =
        numbers.getOrPut(pack(value, if (front) 1 else 0, 0, 0, 0)) {
            PuzzleTextures.number(value, front)
        }

    private fun pack(a: Int, b: Int, c: Int, d: Int, e: Int): Long =
        (a.toLong() shl 32) or (b.toLong() shl 24) or (c.toLong() shl 16) or (d.toLong() shl 8) or e.toLong()
}

/**
 * 3D Picture Puzzle — sliding-tile puzzle on an OpenGL board, with 3x3/4x4
 * grids, one/two-sided play, generated artwork and per-configuration best
 * times. Interaction: tap a tile beside the gap to slide; in two-sided play
 * tap a tile on the other board to flip it through the gap; hold hint to
 * overlay the tiles' home numbers.
 */
@Composable
fun PicturePuzzleApp() {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val colors = LocalDoradoColors.current
    val audioScope = rememberCoroutineScope()
    val synth = remember { MiniSynth(audioScope) }
    DisposableEffect(Unit) {
        synth.start()
        onDispose { synth.stop() }
    }
    val sfx = remember { SfxBank(synth) }

    var screen by remember { mutableStateOf(PuzzleScreen.MENU) }
    var gridSize by remember { mutableStateOf(3) }
    var twoSided by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<PuzzleState?>(null) }
    var hintHeld by remember { mutableStateOf(false) }
    var cameraProgress by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf<Long?>(null) }
    var newBest by remember { mutableStateOf(false) }
    var bestLabel by remember { mutableStateOf<String?>(null) }

    val scene = remember { Scene3d() }
    val assets = remember { PuzzleAssets() }

    fun config() = PuzzleConfig(gridSize, twoSided)

    fun startGame(seed: Int) {
        state = PicturePuzzleEngine.newGame(config(), seed, category)
        lastTime = null
        newBest = false
        cameraProgress = 0f
        screen = PuzzleScreen.MIXUP
    }

    fun finishIfComplete(current: PuzzleState) {
        if (!current.complete) return
        val ms = current.elapsedMs
        lastTime = ms
        newBest = true
        sfx.play("win")
        screen = PuzzleScreen.DONE
        val key = "puzzle.best.${current.config.key}"
        scope.launch {
            val previous = graph.appState.get(key)?.toLongOrNull()
            if (previous == null || ms < previous) {
                graph.appState.put(key, ms.toString())
                bestLabel = PicturePuzzleEngine.formatTime(ms)
            } else {
                newBest = false
            }
            graph.games.record("3d-picture-puzzle", (ms / 1000).toInt(), current.config.key)
        }
    }

    // Game clock.
    LaunchedEffect(screen) {
        if (screen == PuzzleScreen.PLAY) {
            while (true) {
                delay(100)
                val current = state ?: break
                if (current.complete || !current.active) continue
                val next = PicturePuzzleEngine.step(current, 100)
                state = next
                if (next.complete) {
                    finishIfComplete(next)
                    break
                }
            }
        }
    }

    // Camera spin while the empty slot crosses to the other board. This only
    // mutates scene.camera; it never rebuilds the node list.
    fun applyCamera() {
        val current = state ?: return
        val span = current.config.gridSize.toFloat()
        val distance = span * 1.55f + 1.2f
        val angle = cameraProgress * Math.PI.toFloat()
        scene.camera = scene.camera.copy(
            eye = Vec3(0f, span * 1.25f, cos(angle) * distance),
            target = Vec3(0f, 0f, -0.5f * cameraProgress),
        )
    }

    LaunchedEffect(state?.emptySide, screen) {
        val target = if (state?.emptySide == PuzzleSide.BACK) 1f else 0f
        while (cameraProgress != target) {
            val delta = target - cameraProgress
            cameraProgress += delta.coerceIn(-0.08f, 0.08f)
            applyCamera()
            delay(16)
        }
    }

    LaunchedEffect(state?.config, screen) {
        applyCamera()
    }

    LaunchedEffect(gridSize, twoSided) {
        val key = "puzzle.best.${PuzzleConfig(gridSize, twoSided).key}"
        bestLabel = graph.appState.get(key)?.toLongOrNull()?.let { PicturePuzzleEngine.formatTime(it) }
    }

    // Geometry rebuilds only on structural changes: a move changes pieces,
    // hint toggles, screen changes. The 100 ms clock and the 16 ms camera
    // spin no longer touch the node list (A-22).
    DisposableEffect(state?.pieces, state?.config, hintHeld, screen) {
        val current = state
        scene.rebuild {
            backgroundColor = Color4(0.04f, 0.04f, 0.05f)
            ambient = 0.5f
            lightDirection = Vec3(-0.4f, -1f, -0.35f)
            if (current != null && (screen == PuzzleScreen.PLAY || screen == PuzzleScreen.DONE)) {
                buildPuzzleScene(this, current, category, hintHeld, assets)
            }
        }
        onDispose { }
    }

    DetailScaffold(title = "3d picture puzzle") {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            when (screen) {
                PuzzleScreen.MENU -> PuzzleMenu(
                    gridSize = gridSize,
                    twoSided = twoSided,
                    category = category,
                    bestLabel = bestLabel,
                    onGrid = { gridSize = it },
                    onSides = { twoSided = it },
                    onCategory = { category = it },
                    onStart = { startGame(seed = (AppClock.millis() and 0x7fffffff).toInt()) },
                )

                PuzzleScreen.MIXUP -> PuzzleOverlay(
                    title = "mixing",
                    body = "press to start",
                    onClick = {
                        val current = state ?: return@PuzzleOverlay
                        state = current.copy(active = true, elapsedMs = 0, moves = 0)
                        screen = PuzzleScreen.PLAY
                    },
                )

                PuzzleScreen.PLAY, PuzzleScreen.DONE -> {
                    val current = state
                    if (current != null) {
                        Scene3dView(
                            scene = scene,
                            modifier = Modifier
                                .fillMaxSize()
                                .appDescription(
                                    "3d picture puzzle board, ${current.config.key}, moves ${current.moves}, ${PicturePuzzleEngine.formatTime(current.elapsedMs)}",
                                ),
                            onPick = { tag ->
                                if (screen != PuzzleScreen.PLAY) return@Scene3dView
                                val parsed = parseTag(tag) ?: return@Scene3dView
                                val (side, row, col) = parsed
                                val before = state ?: return@Scene3dView
                                val moved = when {
                                    before.pieceAt(side, row, col) == null -> before
                                    side == before.emptySide -> {
                                        val next = PicturePuzzleEngine.slide(before, side, row, col)
                                        if (next != null) sfx.play("tick")
                                        next ?: before
                                    }
                                    before.config.twoSided -> {
                                        val next = PicturePuzzleEngine.flip(before, side, row, col)
                                        if (next != null) sfx.play("whoosh")
                                        next ?: before
                                    }
                                    else -> before
                                }
                                if (moved !== before) {
                                    state = moved
                                    if (moved.complete) finishIfComplete(moved)
                                }
                            },
                        )
                        Column(Modifier.fillMaxSize().padding(DoradoTokens.EDGE.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .appDescription(
                                        "time ${PicturePuzzleEngine.formatTime(current.elapsedMs)}, moves ${current.moves}, ${current.config.key}",
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(
                                    text = PicturePuzzleEngine.formatTime(current.elapsedMs),
                                    style = TextStyle(
                                        fontFamily = Selawik,
                                        fontSize = DoradoTokens.TYPE_NOW_TITLE.sp,
                                        color = colors.textPrimary,
                                    ),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(DoradoTokens.EDGE.dp)) {
                                    if (current.config.twoSided) {
                                        HudButton("flip") {
                                            val piece = current.pieces.firstOrNull { it.side != current.emptySide }
                                            if (piece != null) {
                                                val next = PicturePuzzleEngine.flip(current, piece.side, piece.row, piece.col)
                                                if (next != null) {
                                                    state = next
                                                    sfx.play("whoosh")
                                                }
                                            }
                                        }
                                    }
                                    HudButton(
                                        label = if (hintHeld) "hint on" else "hint",
                                        onPress = { hintHeld = true },
                                        onRelease = { hintHeld = false },
                                    )
                                    HudButton("menu") { screen = PuzzleScreen.MENU }
                                }
                            }
                            Spacer(Modifier.height(DoradoTokens.EDGE.dp))
                            BasicText(
                                text = "${current.config.key.lowercase()}  moves ${current.moves}",
                                style = TextStyle(
                                    fontFamily = Selawik,
                                    fontSize = DoradoTokens.TYPE_CAPTION.sp,
                                    color = colors.textSecondary,
                                ),
                            )
                        }

                        if (screen == PuzzleScreen.DONE) {
                            PuzzleOverlay(
                                title = if (newBest) "new best time" else "solved",
                                body = "${PicturePuzzleEngine.formatTime(lastTime ?: current.elapsedMs)}  —  press for menu",
                                onClick = { screen = PuzzleScreen.MENU },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudButton(
    label: String,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalDoradoColors.current
    val action = if (onPress != null || onRelease != null) {
        Modifier.appHold(
            label = label,
            onPress = { onPress?.invoke() },
            onRelease = { onRelease?.invoke() },
        )
    } else {
        Modifier.appTap(label = label) { onClick?.invoke() }
    }
    Box(
        Modifier
            .background(colors.tile)
            .then(action)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_CAPTION.dp)
    }
}

@Composable
private fun PuzzleOverlay(title: String, body: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.86f))
            .appTap(label = title) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EdgeCropText(text = title, fontSize = DoradoTokens.TYPE_HEADER_CROPPED.dp)
            Spacer(Modifier.height(DoradoTokens.EDGE.dp))
            EdgeCropText(text = body, fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.textSecondary)
        }
    }
}

@Composable
private fun PuzzleMenu(
    gridSize: Int,
    twoSided: Boolean,
    category: Int,
    bestLabel: String?,
    onGrid: (Int) -> Unit,
    onSides: (Boolean) -> Unit,
    onCategory: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DoradoTokens.EDGE.dp),
        verticalArrangement = Arrangement.spacedBy(DoradoTokens.EDGE.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DoradoTokens.EDGE.dp)) {
            MenuChip("3x3", gridSize == 3) { onGrid(3) }
            MenuChip("4x4", gridSize == 4) { onGrid(4) }
            MenuChip("one side", !twoSided) { onSides(false) }
            MenuChip("two sides", twoSided) { onSides(true) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORY_LABELS.forEachIndexed { index, label ->
                MenuChip(label, category == index) { onCategory(index) }
            }
        }
        if (bestLabel != null) {
            EdgeCropText(text = "best $bestLabel", fontSize = DoradoTokens.TYPE_CAPTION.dp, color = colors.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        MenuChip("start", selected = true, onClick = onStart)
        BasicText(
            text = "tap a tile beside the gap to slide — two-sided: tap the other board to flip — hold hint for numbers",
            style = TextStyle(
                fontFamily = Selawik,
                fontSize = DoradoTokens.TYPE_CAPTION.sp,
                color = colors.textSecondary,
                lineHeight = (DoradoTokens.TYPE_CAPTION * 1.4f).sp,
            ),
        )
    }
}

@Composable
private fun MenuChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Box(
        Modifier
            .background(if (selected) colors.accent else colors.tile)
            .appTap(label = label) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        EdgeCropText(
            text = label,
            fontSize = DoradoTokens.TYPE_CAPTION.dp,
            color = if (selected) colors.background else colors.textSecondary,
        )
    }
}

private fun parseTag(tag: String?): Triple<PuzzleSide, Int, Int>? {
    if (tag == null || !tag.startsWith("p:")) return null
    val parts = tag.split(":")
    if (parts.size != 4) return null
    val side = if (parts[1] == "front") PuzzleSide.FRONT else PuzzleSide.BACK
    val row = parts[2].toIntOrNull() ?: return null
    val col = parts[3].toIntOrNull() ?: return null
    return Triple(side, row, col)
}

private fun tileTag(side: PuzzleSide, row: Int, col: Int): String =
    "p:${if (side == PuzzleSide.FRONT) "front" else "back"}:$row:$col"

private fun buildPuzzleScene(
    scene: Scene3d,
    state: PuzzleState,
    category: Int,
    hintHeld: Boolean,
    assets: PuzzleAssets,
) {
    val grid = state.config.gridSize
    val cell = 1f
    val gap = 0.05f
    val step = cell + gap
    val span = grid * step - gap
    val backOffset = -(span + 1.2f)
    val frontPicture = assets.picture(category, 0)
    val backPicture = assets.picture(category, 1)

    scene.add(
        nodeAt(
            assets.plane(grid),
            y = -0.02f,
            material = Material3d(Color4(0.09f, 0.09f, 0.11f), unlit = true),
        ),
    )
    scene.add(
        nodeAt(
            assets.plane(grid),
            y = -0.02f,
            z = backOffset,
            material = Material3d(Color4(0.08f, 0.08f, 0.1f), unlit = true),
        ),
    )

    for (piece in state.pieces) {
        val x = (piece.col - (grid - 1) / 2f) * step
        val z = (piece.row - (grid - 1) / 2f) * step + if (piece.side == PuzzleSide.FRONT) 0f else backOffset
        val yaw = if (piece.side == PuzzleSide.FRONT) 0f else Math.PI.toFloat()
        val tag = tileTag(piece.side, piece.row, piece.col)
        val showNumbers = hintHeld || category == PicturePuzzleEngine.NUMBERS_CATEGORY
        val texture = if (showNumbers) {
            assets.number(PicturePuzzleEngine.homeNumber(state, piece), piece.side == PuzzleSide.FRONT)
        } else {
            val picture = if (piece.side == PuzzleSide.FRONT) frontPicture else backPicture
            val pictureIndex = if (piece.side == PuzzleSide.FRONT) 0 else 1
            assets.tile(picture, category, pictureIndex, grid, piece.homeRow, piece.homeCol)
        }
        scene.add(
            nodeAt(
                assets.tileMesh,
                x = x, y = -0.08f, z = z, tag = tag, yawRad = yaw,
                material = Material3d(Color4(0.16f, 0.16f, 0.18f), unlit = true),
            ),
        )
        scene.add(
            nodeAt(
                assets.quadMesh,
                x = x, y = 0.01f, z = z, tag = tag,
                pitchRad = -Math.PI.toFloat() / 2f,
                yawRad = yaw,
                material = Material3d(Color4(1f, 1f, 1f), texture = texture, unlit = true),
            ),
        )
    }
}
