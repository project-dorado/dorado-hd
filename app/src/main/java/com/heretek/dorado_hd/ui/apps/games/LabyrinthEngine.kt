package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Labyrinth model (W6, official `Labyrinth.exe`). Clean-room re-derivation
 * from docs/apps/labyrinth.md: tilt-driven marble physics (gravity 1950 px/s²,
 * 0.97 per-step friction, restitution 0.5, 750 px/s cap), holes/spikes that
 * rewind to the last checkpoint, pickups that open the goal, and five acts of
 * entirely original generated mazes. Deterministic fixed step, no Android deps.
 */
enum class LabyrinthPack(
    val label: String,
    val firstLevel: Int,
    val count: Int,
    val braid: Double,
    val holeDensity: Double,
    val spikeDensity: Double,
) {
    ORCHARD("orchard", 0, 24, 0.15, 0.020, 0.0),
    GARDENS("gardens", 24, 24, 0.35, 0.022, 0.0),
    WORKSHOP("workshop", 48, 24, 0.20, 0.030, 0.012),
    RAPIDS("rapids", 72, 16, 0.60, 0.045, 0.0),
    QUARRY("quarry", 88, 24, 0.10, 0.035, 0.008),
    BONUS("bonus", 112, 4, 0.30, 0.050, 0.015),
}

enum class LabyrinthPickupKind { APPLE, AXE, FLOWER, HEART, KEY }

enum class LabyrinthStatus { PLAYING, COMPLETE }

enum class LabyrinthEvent { WALL, PICKUP, CHECKPOINT, FALL, GOAL_OPEN, COMPLETE }

data class LabyrinthPoint(val x: Double, val y: Double)

data class LabyrinthWall(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
}

data class LabyrinthCircle(val x: Double, val y: Double, val r: Double)

data class LabyrinthPickup(
    val id: Int,
    val x: Double,
    val y: Double,
    val kind: LabyrinthPickupKind,
)

data class LabyrinthMaze(
    val levelId: Int,
    val pack: LabyrinthPack,
    val name: String,
    val cols: Int,
    val rows: Int,
    val walls: List<LabyrinthWall>,
    val holes: List<LabyrinthPoint>,
    val spikes: List<LabyrinthPoint>,
    val obstacles: List<LabyrinthCircle>,
    val pickups: List<LabyrinthPickup>,
    val spawn: LabyrinthPoint,
    val goal: LabyrinthPoint,
    val checkpoint: LabyrinthPoint,
    val solution: List<Int>,
    val parSeconds: Int,
) {
    fun cellOf(point: LabyrinthPoint): Int =
        (point.y / LabyrinthEngine.CELL).toInt() * cols + (point.x / LabyrinthEngine.CELL).toInt()
}

data class LabyrinthBall(
    val x: Double,
    val y: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
)

data class LabyrinthGame(
    val levelId: Int,
    val maze: LabyrinthMaze,
    val ball: LabyrinthBall,
    val elapsedMs: Double = 0.0,
    val deaths: Int = 0,
    val checkpoint: LabyrinthPoint,
    val checkpointMs: Double = 0.0,
    val collected: Set<Int> = emptySet(),
    val goalOpen: Boolean = false,
    val status: LabyrinthStatus = LabyrinthStatus.PLAYING,
    val stars: Int = 0,
    val tiltSetting: Double = LabyrinthEngine.DEFAULT_TILT_SETTING,
    val accumulatorMs: Double = 0.0,
    val events: List<LabyrinthEvent> = emptyList(),
)

data class LabyrinthProgress(
    val completed: Set<Int> = emptySet(),
    val bestMs: Map<Int, Double> = emptyMap(),
    val stars: Map<Int, Int> = emptyMap(),
    val tiltSetting: Double = 1.0,
) {
    val highestUnlocked: Int
        get() {
            if (completed.isEmpty()) return 0
            val highest = completed.max()
            return (highest + 1).coerceAtMost(LabyrinthEngine.LEVEL_COUNT - 1)
        }
}

object LabyrinthEngine {

    const val TICK_MS = 1000.0 / 60.0
    const val BALL_RADIUS = 13.0
    const val BALL_MASS = 1.0
    const val RESTITUTION = 0.5
    const val FRICTION = 0.97
    const val MAX_SPEED = 750.0
    const val TILT_ACCEL = 1950.0
    const val BRAKE_ASSIST = 1.05
    const val MIN_TILT_SETTING = 0.5
    const val MAX_TILT_SETTING = 1.5
    const val DEFAULT_TILT_SETTING = 1.0
    const val CELL = 48.0
    const val WALL_THICKNESS = 8.0
    const val HOLE_RADIUS = 16.0
    const val SPIKE_RADIUS = 12.0
    const val PICKUP_RADIUS = 12.0
    const val GOAL_RADIUS = 22.0
    const val CHECKPOINT_RADIUS = 20.0
    const val SUBSTEP_MAX = 6.0
    const val LEVEL_COUNT = 116

    fun packFor(levelId: Int): LabyrinthPack {
        val id = levelId.coerceIn(0, LEVEL_COUNT - 1)
        return LabyrinthPack.entries.first { id >= it.firstLevel && id < it.firstLevel + it.count }
    }

    fun seedFor(levelId: Int): Int = 7919 * (levelId + 1) + 104_729 * packFor(levelId).ordinal + 17

    fun levelName(levelId: Int): String {
        val pack = packFor(levelId)
        val local = levelId - pack.firstLevel + 1
        return "${pack.label} $local"
    }

    fun colsFor(levelId: Int): Int {
        val pack = packFor(levelId)
        val local = levelId - pack.firstLevel
        return when (pack) {
            LabyrinthPack.ORCHARD -> 14 + local / 6
            LabyrinthPack.GARDENS -> 15 + local / 12
            LabyrinthPack.WORKSHOP -> 16 + local / 12
            LabyrinthPack.RAPIDS -> 18
            LabyrinthPack.QUARRY -> 17 + local / 12
            LabyrinthPack.BONUS -> 20
        }
    }

    fun rowsFor(levelId: Int): Int {
        val pack = packFor(levelId)
        val local = levelId - pack.firstLevel
        return when (pack) {
            LabyrinthPack.ORCHARD -> 9 + local / 12
            LabyrinthPack.GARDENS -> 10
            LabyrinthPack.WORKSHOP -> 10 + local / 12
            LabyrinthPack.RAPIDS -> 11
            LabyrinthPack.QUARRY -> 11
            LabyrinthPack.BONUS -> 12
        }
    }

    fun newGame(levelId: Int, tiltSetting: Double = DEFAULT_TILT_SETTING): LabyrinthGame {
        val maze = LabyrinthMazes.forLevel(levelId)
        return LabyrinthGame(
            levelId = maze.levelId,
            maze = maze,
            ball = LabyrinthBall(maze.spawn.x, maze.spawn.y),
            checkpoint = maze.spawn,
            tiltSetting = tiltSetting.coerceIn(MIN_TILT_SETTING, MAX_TILT_SETTING),
        )
    }

    fun step(game: LabyrinthGame, dtMs: Double, tiltX: Double, tiltY: Double): LabyrinthGame {
        if (game.status != LabyrinthStatus.PLAYING) return game
        var accumulator = game.accumulatorMs + dtMs.coerceIn(0.0, 100.0)
        var state = game.copy(accumulatorMs = 0.0, events = emptyList())
        val events = ArrayList<LabyrinthEvent>()
        while (accumulator >= TICK_MS) {
            accumulator -= TICK_MS
            state = tick(state, tiltX, tiltY, events)
            if (state.status != LabyrinthStatus.PLAYING) {
                accumulator = 0.0
                break
            }
        }
        return state.copy(accumulatorMs = accumulator, events = events)
    }

    private fun tick(game: LabyrinthGame, tiltX: Double, tiltY: Double, events: MutableList<LabyrinthEvent>): LabyrinthGame {
        val dt = TICK_MS / 1000.0
        val ball = game.ball
        var vx = ball.vx
        var vy = ball.vy
        var x = ball.x
        var y = ball.y
        var state = game.copy(elapsedMs = game.elapsedMs + TICK_MS)

        val gravity = gravity(tiltX, tiltY, state.tiltSetting, vx, vy)
        vx = (vx + gravity.first * dt) * FRICTION
        vy = (vy + gravity.second * dt) * FRICTION
        val speed = hypot(vx, vy)
        if (speed > MAX_SPEED) {
            vx = vx / speed * MAX_SPEED
            vy = vy / speed * MAX_SPEED
        }

        val moveX = vx * dt
        val moveY = vy * dt
        val steps = ceil(maxOf(abs(moveX), abs(moveY)) / SUBSTEP_MAX).toInt().coerceAtLeast(1)
        var bounced = 0.0
        var fell = false
        for (substep in 0 until steps) {
            x += moveX / steps
            y += moveY / steps
            val wallResult = resolveWalls(state.maze, x, y, vx, vy)
            x = wallResult.x
            y = wallResult.y
            vx = wallResult.vx
            vy = wallResult.vy
            bounced = maxOf(bounced, wallResult.impact)

            val obstacleResult = resolveObstacles(state.maze, x, y, vx, vy)
            x = obstacleResult.x
            y = obstacleResult.y
            vx = obstacleResult.vx
            vy = obstacleResult.vy
            bounced = maxOf(bounced, obstacleResult.impact)

            val hitHole = state.maze.holes.any { hypot(x - it.x, y - it.y) < HOLE_RADIUS }
            val hitSpike = state.maze.spikes.any { hypot(x - it.x, y - it.y) < SPIKE_RADIUS }
            if (hitHole || hitSpike) {
                events += LabyrinthEvent.FALL
                state = state.copy(
                    deaths = state.deaths + 1,
                    ball = LabyrinthBall(state.checkpoint.x, state.checkpoint.y),
                    elapsedMs = state.checkpointMs,
                )
                x = state.checkpoint.x
                y = state.checkpoint.y
                vx = 0.0
                vy = 0.0
                fell = true
                break
            }
        }

        if (!fell) {
            state = state.copy(ball = LabyrinthBall(x, y, vx, vy))
        }
        if (bounced > 180.0) events += LabyrinthEvent.WALL

        state = collectPickups(state, events)
        state = updateCheckpoint(state, events)
        state = checkGoal(state, events)
        return state
    }

    private fun collectPickups(game: LabyrinthGame, events: MutableList<LabyrinthEvent>): LabyrinthGame {
        val ball = game.ball
        val hit = game.maze.pickups.filter { pickup ->
            pickup.id !in game.collected &&
                hypot(ball.x - pickup.x, ball.y - pickup.y) < BALL_RADIUS + PICKUP_RADIUS
        }
        if (hit.isEmpty()) return game
        var state = game.copy(collected = game.collected + hit.map { it.id })
        hit.forEach { events += LabyrinthEvent.PICKUP }
        if (!state.goalOpen && state.collected.size >= state.maze.pickups.size) {
            state = state.copy(goalOpen = true)
            events += LabyrinthEvent.GOAL_OPEN
        }
        return state
    }

    private fun updateCheckpoint(game: LabyrinthGame, events: MutableList<LabyrinthEvent>): LabyrinthGame {
        val ball = game.ball
        val distance = hypot(ball.x - game.maze.checkpoint.x, ball.y - game.maze.checkpoint.y)
        if (distance >= BALL_RADIUS + CHECKPOINT_RADIUS) return game
        if (game.checkpoint == game.maze.checkpoint && game.checkpointMs > 0.0) return game
        events += LabyrinthEvent.CHECKPOINT
        return game.copy(checkpoint = game.maze.checkpoint, checkpointMs = game.elapsedMs)
    }

    private fun checkGoal(game: LabyrinthGame, events: MutableList<LabyrinthEvent>): LabyrinthGame {
        if (!game.goalOpen || game.status != LabyrinthStatus.PLAYING) return game
        val ball = game.ball
        val distance = hypot(ball.x - game.maze.goal.x, ball.y - game.maze.goal.y)
        if (distance >= GOAL_RADIUS) return game
        events += LabyrinthEvent.COMPLETE
        return game.copy(
            status = LabyrinthStatus.COMPLETE,
            stars = parStars(game.elapsedMs, game.maze.parSeconds),
        )
    }

    fun gravity(tiltX: Double, tiltY: Double, setting: Double, vx: Double, vy: Double): Pair<Double, Double> {
        val magnitude = TILT_ACCEL * setting.coerceIn(MIN_TILT_SETTING, MAX_TILT_SETTING)
        var gx = -tiltY * magnitude
        var gy = -tiltX * magnitude
        if (gx * vx + gy * vy < 0.0) {
            gx *= BRAKE_ASSIST
            gy *= BRAKE_ASSIST
        }
        return gx to gy
    }

    fun tiltVector(rollDeg: Double, pitchDeg: Double): Pair<Double, Double> =
        sin(Math.toRadians(rollDeg)).coerceIn(-1.0, 1.0) to
            sin(Math.toRadians(pitchDeg)).coerceIn(-1.0, 1.0)

    fun parStars(elapsedMs: Double, parSeconds: Int): Int {
        val par = parSeconds * 1000.0
        return when {
            elapsedMs <= par -> 3
            elapsedMs <= par * 1.5 -> 2
            elapsedMs <= par * 2.0 -> 1
            else -> 0
        }
    }

    private data class CollisionResult(
        val x: Double,
        val y: Double,
        val vx: Double,
        val vy: Double,
        val impact: Double,
    )

    private fun resolveWalls(
        maze: LabyrinthMaze,
        startX: Double,
        startY: Double,
        startVx: Double,
        startVy: Double,
    ): CollisionResult {
        var x = startX
        var y = startY
        var vx = startVx
        var vy = startVy
        var impact = 0.0
        for (wall in maze.walls) {
            if (x < wall.x - BALL_RADIUS || x > wall.right + BALL_RADIUS) continue
            if (y < wall.y - BALL_RADIUS || y > wall.bottom + BALL_RADIUS) continue
            val nearestX = x.coerceIn(wall.x, wall.right)
            val nearestY = y.coerceIn(wall.y, wall.bottom)
            val dx = x - nearestX
            val dy = y - nearestY
            var distance = hypot(dx, dy)
            var nx: Double
            var ny: Double
            if (distance < 1e-9) {
                val left = x - wall.x
                val right = wall.right - x
                val top = y - wall.y
                val bottom = wall.bottom - y
                val least = minOf(left, right, top, bottom)
                nx = 0.0
                ny = 0.0
                when (least) {
                    left -> nx = -1.0
                    right -> nx = 1.0
                    top -> ny = -1.0
                    else -> ny = 1.0
                }
                x = if (nx < 0) wall.x - BALL_RADIUS else if (nx > 0) wall.right + BALL_RADIUS else x
                y = if (ny < 0) wall.y - BALL_RADIUS else if (ny > 0) wall.bottom + BALL_RADIUS else y
                distance = 0.0
            } else {
                nx = dx / distance
                ny = dy / distance
                x = nearestX + nx * BALL_RADIUS
                y = nearestY + ny * BALL_RADIUS
            }
            val normalSpeed = vx * nx + vy * ny
            if (normalSpeed < 0.0) {
                vx -= (1.0 + RESTITUTION) * normalSpeed * nx
                vy -= (1.0 + RESTITUTION) * normalSpeed * ny
                impact = maxOf(impact, -normalSpeed)
            }
        }
        return CollisionResult(x, y, vx, vy, impact)
    }

    private fun resolveObstacles(
        maze: LabyrinthMaze,
        startX: Double,
        startY: Double,
        startVx: Double,
        startVy: Double,
    ): CollisionResult {
        var x = startX
        var y = startY
        var vx = startVx
        var vy = startVy
        var impact = 0.0
        for (obstacle in maze.obstacles) {
            val dx = x - obstacle.x
            val dy = y - obstacle.y
            val distance = hypot(dx, dy)
            val minimum = obstacle.r + BALL_RADIUS
            if (distance >= minimum) continue
            val nx = if (distance < 1e-9) 1.0 else dx / distance
            val ny = if (distance < 1e-9) 0.0 else dy / distance
            x = obstacle.x + nx * minimum
            y = obstacle.y + ny * minimum
            val normalSpeed = vx * nx + vy * ny
            if (normalSpeed < 0.0) {
                vx -= (1.0 + RESTITUTION) * normalSpeed * nx
                vy -= (1.0 + RESTITUTION) * normalSpeed * ny
                impact = maxOf(impact, -normalSpeed)
            }
        }
        return CollisionResult(x, y, vx, vy, impact)
    }

    fun encodeProgress(progress: LabyrinthProgress): String {
        val entries = progress.completed.sorted().joinToString(",") { level ->
            val best = progress.bestMs[level] ?: 0.0
            val stars = progress.stars[level] ?: 0
            "$level:${(best * 100.0).roundToInt()}:$stars"
        }
        return "${fmt(progress.tiltSetting)};$entries"
    }

    fun decodeProgress(blob: String?): LabyrinthProgress {
        if (blob.isNullOrBlank()) return LabyrinthProgress()
        return try {
            val parts = blob.split(';')
            val setting = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
            val completed = LinkedHashSet<Int>()
            val best = HashMap<Int, Double>()
            val stars = HashMap<Int, Int>()
            parts.getOrNull(1)?.split(',')?.filter { it.isNotBlank() }?.forEach { entry ->
                val bits = entry.split(':')
                val level = bits.getOrNull(0)?.toIntOrNull() ?: return@forEach
                completed += level
                best[level] = (bits.getOrNull(1)?.toIntOrNull() ?: 0) / 100.0
                stars[level] = bits.getOrNull(2)?.toIntOrNull() ?: 0
            }
            LabyrinthProgress(completed, best, stars, setting.coerceIn(MIN_TILT_SETTING, MAX_TILT_SETTING))
        } catch (_: Exception) {
            LabyrinthProgress()
        }
    }

    fun complete(progress: LabyrinthProgress, levelId: Int, timeMs: Double, stars: Int): LabyrinthProgress {
        val previous = progress.bestMs[levelId]
        val best = if (previous == null || timeMs < previous) timeMs else previous
        return progress.copy(
            completed = progress.completed + levelId,
            bestMs = progress.bestMs + (levelId to best),
            stars = progress.stars + (levelId to maxOf(stars, progress.stars[levelId] ?: 0)),
        )
    }

    private fun fmt(value: Double): String = (value * 100.0).roundToInt().let { "${it / 100.0}" }
}

object LabyrinthMazes {

    private val cache = HashMap<Int, LabyrinthMaze>()

    @Synchronized
    fun forLevel(levelId: Int): LabyrinthMaze = cache.getOrPut(levelId.coerceIn(0, LabyrinthEngine.LEVEL_COUNT - 1)) {
        generate(levelId.coerceIn(0, LabyrinthEngine.LEVEL_COUNT - 1))
    }

    fun generate(levelId: Int): LabyrinthMaze {
        val id = levelId.coerceIn(0, LabyrinthEngine.LEVEL_COUNT - 1)
        val pack = LabyrinthEngine.packFor(id)
        val cols = LabyrinthEngine.colsFor(id)
        val rows = LabyrinthEngine.rowsFor(id)
        val rng = Random(LabyrinthEngine.seedFor(id))
        val cells = cols * rows
        val openRight = BooleanArray(cells)
        val openDown = BooleanArray(cells)
        val visited = BooleanArray(cells)

        val start = rng.nextInt(cells)
        val stack = ArrayDeque<Int>()
        visited[start] = true
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val cell = stack.last()
            val neighbourList = gridNeighbours(cell, cols, rows).filter { !visited[it] }
            if (neighbourList.isEmpty()) {
                stack.removeLast()
                continue
            }
            val next = neighbourList[rng.nextInt(neighbourList.size)]
            carve(openRight, openDown, cols, cell, next)
            visited[next] = true
            stack.addLast(next)
        }

        for (cell in 0 until cells) {
            if (degree(cell, cols, rows, openRight, openDown) != 1) continue
            if (rng.nextDouble() > pack.braid) continue
            val options = wallSides(cell, cols, rows, openRight, openDown)
            if (options.isNotEmpty()) {
                carve(openRight, openDown, cols, cell, options[rng.nextInt(options.size)])
            }
        }

        val solution = solve(start, cols, rows, openRight, openDown)
        val goal = solution.last()
        val checkpoint = solution[solution.size / 2]
        val pathCells = solution.toSet()

        val deadEnds = (0 until cells).filter {
            it != start && it != goal && it != checkpoint &&
                it !in pathCells && degree(it, cols, rows, openRight, openDown) == 1
        }.shuffled(rng)

        val pickupCount = (2 + id % 3).coerceAtMost(deadEnds.size)
        val pickupCells = deadEnds.take(pickupCount)
        val pickups = pickupCells.mapIndexed { index, cell ->
            val center = centerOf(cell, cols)
            LabyrinthPickup(index, center.x, center.y, LabyrinthPickupKind.entries[index % LabyrinthPickupKind.entries.size])
        }

        val reserved = HashSet<Int>()
        reserved += solution
        reserved += pickupCells
        val candidates = (0 until cells).filter { it !in reserved }.shuffled(rng)
        val holeCount = (cells * pack.holeDensity).roundToInt().coerceIn(0, 14)
        val holeCells = candidates.take(holeCount)
        val spikeCount = if (pack.spikeDensity > 0.0) {
            (cells * pack.spikeDensity).roundToInt().coerceIn(0, 6)
        } else {
            0
        }
        val spikeCells = candidates.drop(holeCount).take(spikeCount)

        val mustReach = pickupCells + goal
        var hazards = holeCells + spikeCells
        fun reachableWithout(blocked: Set<Int>): Set<Int> {
            val seen = HashSet<Int>()
            val queue = ArrayDeque<Int>()
            seen += start
            queue += start
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                val col = cell % cols
                val row = cell / cols
                val links = ArrayList<Int>(4)
                if (col > 0 && openRight[cell - 1]) links += cell - 1
                if (col < cols - 1 && openRight[cell]) links += cell + 1
                if (row > 0 && openDown[cell - cols]) links += cell - cols
                if (row < rows - 1 && openDown[cell]) links += cell + cols
                for (next in links) {
                    if (next in blocked || next in seen) continue
                    seen += next
                    queue += next
                }
            }
            return seen
        }
        while (hazards.isNotEmpty()) {
            val blocked = hazards.toHashSet()
            if (mustReach.all { it in reachableWithout(blocked) }) break
            hazards = hazards.dropLast(1)
        }
        val keptHoles = holeCells.filter { it in hazards }
        val keptSpikes = spikeCells.filter { it in hazards }

        val obstacles = if (pack == LabyrinthPack.GARDENS || pack == LabyrinthPack.QUARRY) {
            planterObstacles(cols, rows, openRight, openDown, reserved, rng)
        } else {
            emptyList()
        }

        val walls = buildWalls(cols, rows, openRight, openDown)
        val parSeconds = (6.0 + solution.size * 1.2 + keptHoles.size * 0.4).roundToInt()

        return LabyrinthMaze(
            levelId = id,
            pack = pack,
            name = LabyrinthEngine.levelName(id),
            cols = cols,
            rows = rows,
            walls = walls,
            holes = keptHoles.map { centerOf(it, cols) },
            spikes = keptSpikes.map { centerOf(it, cols) },
            obstacles = obstacles,
            pickups = pickups,
            spawn = centerOf(start, cols),
            goal = centerOf(goal, cols),
            checkpoint = centerOf(checkpoint, cols),
            solution = solution,
            parSeconds = parSeconds,
        )
    }

    private fun planterObstacles(
        cols: Int,
        rows: Int,
        openRight: BooleanArray,
        openDown: BooleanArray,
        reserved: Set<Int>,
        rng: Random,
    ): List<LabyrinthCircle> {
        val out = ArrayList<LabyrinthCircle>()
        for (row in 0 until rows - 1) {
            for (col in 0 until cols - 1) {
                val a = row * cols + col
                val b = a + 1
                val c = a + cols
                val d = c + 1
                val block = listOf(a, b, c, d)
                if (block.any { it in reserved }) continue
                val connected = openRight[a] && openRight[c] && openDown[a] && openDown[b]
                if (!connected) continue
                if (rng.nextDouble() > 0.18) continue
                val cx = (col + 1) * LabyrinthEngine.CELL
                val cy = (row + 1) * LabyrinthEngine.CELL
                out += LabyrinthCircle(cx, cy, 12.0)
                if (out.size >= 6) return out
            }
        }
        return out
    }

    private fun gridNeighbours(cell: Int, cols: Int, rows: Int): List<Int> {
        val col = cell % cols
        val row = cell / cols
        val out = ArrayList<Int>(4)
        if (col > 0) out += cell - 1
        if (col < cols - 1) out += cell + 1
        if (row > 0) out += cell - cols
        if (row < rows - 1) out += cell + cols
        return out
    }

    private fun carve(openRight: BooleanArray, openDown: BooleanArray, cols: Int, from: Int, to: Int) {
        if (to == from + 1) openRight[from] = true
        if (to == from - 1) openRight[to] = true
        if (to == from + cols) openDown[from] = true
        if (to == from - cols) openDown[to] = true
    }

    private fun wallSides(cell: Int, cols: Int, rows: Int, openRight: BooleanArray, openDown: BooleanArray): List<Int> {
        val col = cell % cols
        val row = cell / cols
        val out = ArrayList<Int>(4)
        if (col > 0 && !openRight[cell - 1]) out += cell - 1
        if (col < cols - 1 && !openRight[cell]) out += cell + 1
        if (row > 0 && !openDown[cell - cols]) out += cell - cols
        if (row < rows - 1 && !openDown[cell]) out += cell + cols
        return out
    }

    private fun degree(cell: Int, cols: Int, rows: Int, openRight: BooleanArray, openDown: BooleanArray): Int {
        val col = cell % cols
        val row = cell / cols
        var count = 0
        if (col > 0 && openRight[cell - 1]) count++
        if (col < cols - 1 && openRight[cell]) count++
        if (row > 0 && openDown[cell - cols]) count++
        if (row < rows - 1 && openDown[cell]) count++
        return count
    }

    private fun solve(start: Int, cols: Int, rows: Int, openRight: BooleanArray, openDown: BooleanArray): List<Int> {
        val parent = IntArray(cols * rows) { -1 }
        val distance = IntArray(cols * rows) { -1 }
        distance[start] = 0
        val queue = ArrayDeque<Int>()
        queue.addLast(start)
        var farthest = start
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val col = cell % cols
            val row = cell / cols
            val links = ArrayList<Int>(4)
            if (col > 0 && openRight[cell - 1]) links += cell - 1
            if (col < cols - 1 && openRight[cell]) links += cell + 1
            if (row > 0 && openDown[cell - cols]) links += cell - cols
            if (row < rows - 1 && openDown[cell]) links += cell + cols
            for (next in links) {
                if (distance[next] >= 0) continue
                distance[next] = distance[cell] + 1
                parent[next] = cell
                if (distance[next] > distance[farthest]) farthest = next
                queue.addLast(next)
            }
        }
        val path = ArrayList<Int>()
        var cursor = farthest
        while (cursor >= 0) {
            path += cursor
            if (cursor == start) break
            cursor = parent[cursor]
        }
        path.reverse()
        return path
    }

    private fun centerOf(cell: Int, cols: Int): LabyrinthPoint =
        LabyrinthPoint(
            (cell % cols + 0.5) * LabyrinthEngine.CELL,
            (cell / cols + 0.5) * LabyrinthEngine.CELL,
        )

    private fun buildWalls(cols: Int, rows: Int, openRight: BooleanArray, openDown: BooleanArray): List<LabyrinthWall> {
        val half = LabyrinthEngine.WALL_THICKNESS / 2
        val walls = ArrayList<LabyrinthWall>()
        for (row in 0..rows) {
            var runStart = -1
            for (col in 0..cols) {
                val wall = col < cols && (row == 0 || row == rows || !openDown[(row - 1) * cols + col])
                if (wall && runStart < 0) runStart = col
                if ((!wall || col == cols) && runStart >= 0) {
                    val end = col - 1
                    walls += LabyrinthWall(
                        runStart * LabyrinthEngine.CELL - half,
                        row * LabyrinthEngine.CELL - half,
                        (end - runStart + 1) * LabyrinthEngine.CELL + LabyrinthEngine.WALL_THICKNESS,
                        LabyrinthEngine.WALL_THICKNESS,
                    )
                    runStart = -1
                }
            }
        }
        for (col in 0..cols) {
            var runStart = -1
            for (row in 0..rows) {
                val wall = row < rows && (col == 0 || col == cols || !openRight[row * cols + (col - 1)])
                if (wall && runStart < 0) runStart = row
                if ((!wall || row == rows) && runStart >= 0) {
                    val end = row - 1
                    walls += LabyrinthWall(
                        col * LabyrinthEngine.CELL - half,
                        runStart * LabyrinthEngine.CELL - half,
                        LabyrinthEngine.WALL_THICKNESS,
                        (end - runStart + 1) * LabyrinthEngine.CELL + LabyrinthEngine.WALL_THICKNESS,
                    )
                    runStart = -1
                }
            }
        }
        return walls
    }
}
