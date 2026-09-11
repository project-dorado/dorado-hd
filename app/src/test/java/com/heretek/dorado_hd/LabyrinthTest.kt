package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.LabyrinthBall
import com.heretek.dorado_hd.ui.apps.games.LabyrinthCircle
import com.heretek.dorado_hd.ui.apps.games.LabyrinthEngine
import com.heretek.dorado_hd.ui.apps.games.LabyrinthEvent
import com.heretek.dorado_hd.ui.apps.games.LabyrinthMaze
import com.heretek.dorado_hd.ui.apps.games.LabyrinthMazes
import com.heretek.dorado_hd.ui.apps.games.LabyrinthPack
import com.heretek.dorado_hd.ui.apps.games.LabyrinthPickup
import com.heretek.dorado_hd.ui.apps.games.LabyrinthPickupKind
import com.heretek.dorado_hd.ui.apps.games.LabyrinthPoint
import com.heretek.dorado_hd.ui.apps.games.LabyrinthProgress
import com.heretek.dorado_hd.ui.apps.games.LabyrinthStatus
import com.heretek.dorado_hd.ui.apps.games.LabyrinthWall
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabyrinthTest {

    private val cell = LabyrinthEngine.CELL

    private fun boxMaze(
        cols: Int = 8,
        rows: Int = 8,
        extraWalls: List<LabyrinthWall> = emptyList(),
        pickups: List<LabyrinthPoint> = emptyList(),
        holes: List<LabyrinthPoint> = emptyList(),
        spikes: List<LabyrinthPoint> = emptyList(),
        obstacles: List<LabyrinthCircle> = emptyList(),
        spawn: LabyrinthPoint = LabyrinthPoint(cell / 2, cell / 2),
        goal: LabyrinthPoint = LabyrinthPoint(cols * cell - cell / 2, rows * cell - cell / 2),
        checkpoint: LabyrinthPoint = LabyrinthPoint(cell / 2, cell / 2),
        parSeconds: Int = 30,
    ): LabyrinthMaze {
        val width = cols * cell
        val height = rows * cell
        val half = LabyrinthEngine.WALL_THICKNESS / 2
        val borders = listOf(
            LabyrinthWall(-half, -half, width + LabyrinthEngine.WALL_THICKNESS, LabyrinthEngine.WALL_THICKNESS),
            LabyrinthWall(-half, height - half, width + LabyrinthEngine.WALL_THICKNESS, LabyrinthEngine.WALL_THICKNESS),
            LabyrinthWall(-half, 0.0, LabyrinthEngine.WALL_THICKNESS, height),
            LabyrinthWall(width - half, 0.0, LabyrinthEngine.WALL_THICKNESS, height),
        )
        return LabyrinthMaze(
            levelId = -1,
            pack = LabyrinthPack.ORCHARD,
            name = "test",
            cols = cols,
            rows = rows,
            walls = borders + extraWalls,
            holes = holes,
            spikes = spikes,
            obstacles = obstacles,
            pickups = pickups.mapIndexed { index, point ->
                LabyrinthPickup(index, point.x, point.y, LabyrinthPickupKind.APPLE)
            },
            spawn = spawn,
            goal = goal,
            checkpoint = checkpoint,
            solution = emptyList(),
            parSeconds = parSeconds,
        )
    }

    private fun game(maze: LabyrinthMaze, ball: LabyrinthBall, checkpointMs: Double = 0.0) =
        com.heretek.dorado_hd.ui.apps.games.LabyrinthGame(
            levelId = 0,
            maze = maze,
            ball = ball,
            checkpoint = maze.checkpoint,
            checkpointMs = checkpointMs,
        )

    private fun stepN(
        start: com.heretek.dorado_hd.ui.apps.games.LabyrinthGame,
        ticks: Int,
        tiltX: Double = 0.0,
        tiltY: Double = 0.0,
    ): com.heretek.dorado_hd.ui.apps.games.LabyrinthGame {
        var state = start
        repeat(ticks) { state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, tiltX, tiltY) }
        return state
    }

    private fun stepUntil(
        start: com.heretek.dorado_hd.ui.apps.games.LabyrinthGame,
        maxTicks: Int = 120,
        stop: (com.heretek.dorado_hd.ui.apps.games.LabyrinthGame) -> Boolean,
    ): com.heretek.dorado_hd.ui.apps.games.LabyrinthGame {
        var state = start
        var guard = 0
        while (guard++ < maxTicks && !stop(state)) {
            state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        }
        return state
    }

    @Test
    fun `gravity maps tilt through negated axes and the slider setting`() {
        val flat = LabyrinthEngine.gravity(0.5, 0.0, 1.0, 0.0, 0.0)
        assertEquals(0.0, flat.first, 1e-9)
        assertEquals(-975.0, flat.second, 1e-9)
        val strong = LabyrinthEngine.gravity(0.0, -0.4, 1.5, 0.0, 0.0)
        assertEquals(1170.0, strong.first, 1e-9)
        assertEquals(0.0, strong.second, 1e-9)
        val low = LabyrinthEngine.gravity(1.0, 0.0, 0.5, 0.0, 0.0)
        assertEquals(-975.0, low.second, 1e-9)
    }

    @Test
    fun `braking assist boosts tilt that opposes the current velocity`() {
        val opposing = LabyrinthEngine.gravity(0.5, 0.0, 1.0, 0.0, 200.0)
        assertEquals(-1023.75, opposing.second, 1e-9)
        val with = LabyrinthEngine.gravity(0.5, 0.0, 1.0, 0.0, -200.0)
        assertEquals(-975.0, with.second, 1e-9)
    }

    @Test
    fun `tilt vector converts degrees to gravity units`() {
        val (roll, pitch) = LabyrinthEngine.tiltVector(30.0, 0.0)
        assertEquals(0.5, roll, 1e-3)
        assertEquals(0.0, pitch, 1e-9)
        val steep = LabyrinthEngine.tiltVector(0.0, -90.0)
        assertEquals(0.0, steep.first, 1e-9)
        assertEquals(-1.0, steep.second, 1e-3)
    }

    @Test
    fun `slope acceleration moves the marble away from the tilt`() {
        val maze = boxMaze()
        val start = game(maze, LabyrinthBall(cell * 4, cell * 4))
        val moved = stepN(start, 30, tiltX = 0.5, tiltY = 0.0)
        assertTrue(moved.ball.vy < 0.0)
        assertTrue(moved.ball.y < start.ball.y)
        assertEquals(start.ball.x, moved.ball.x, 1e-9)
    }

    @Test
    fun `speed is capped at the device maximum`() {
        val maze = boxMaze()
        val fast = game(maze, LabyrinthBall(cell, cell, vx = 2000.0, vy = 0.0))
        val capped = LabyrinthEngine.step(fast, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertTrue(hypot(capped.ball.vx, capped.ball.vy) <= LabyrinthEngine.MAX_SPEED + 1e-6)
    }

    @Test
    fun `wall stops and reflects the marble`() {
        val wall = LabyrinthWall(200.0 - 4.0, 0.0, 8.0, 8 * cell)
        val maze = boxMaze(extraWalls = listOf(wall))
        val start = game(maze, LabyrinthBall(100.0, 200.0, vx = 400.0))
        val stopped = stepN(start, 60)
        assertTrue(stopped.ball.x + LabyrinthEngine.BALL_RADIUS <= 196.0 + 0.01)
        assertTrue(stopped.ball.vx <= 0.0)
    }

    @Test
    fun `tunneling guard holds the marble at maximum speed`() {
        val wall = LabyrinthWall(196.0, 0.0, 8.0, 8 * cell)
        val maze = boxMaze(extraWalls = listOf(wall))
        val start = game(maze, LabyrinthBall(100.0, 200.0, vx = LabyrinthEngine.MAX_SPEED))
        val stopped = stepN(start, 30)
        assertTrue(stopped.ball.x < 196.0)
        assertTrue(stopped.ball.x + LabyrinthEngine.BALL_RADIUS <= 196.0 + 0.01)
    }

    @Test
    fun `hole consumes the marble and respawns at the checkpoint`() {
        val maze = boxMaze(holes = listOf(LabyrinthPoint(200.0, 200.0)))
        val start = game(maze, LabyrinthBall(140.0, 200.0, vx = 450.0), checkpointMs = 5000.0)
        val fallen = stepUntil(start) { it.deaths > 0 }
        assertEquals(1, fallen.deaths)
        assertEquals(5000.0, fallen.elapsedMs, 1e-6)
        assertEquals(maze.checkpoint.x, fallen.ball.x, 1e-9)
        assertEquals(maze.checkpoint.y, fallen.ball.y, 1e-9)
    }

    @Test
    fun `checkpoint contact rewinds the timer on a later fall`() {
        val maze = boxMaze(
            holes = listOf(LabyrinthPoint(300.0, 200.0)),
            checkpoint = LabyrinthPoint(200.0, 200.0),
        )
        val start = game(maze, LabyrinthBall(100.0, 200.0, vx = 500.0))
        val fallen = stepUntil(start) { it.deaths > 0 }
        assertEquals(1, fallen.deaths)
        assertTrue(fallen.checkpointMs > 0.0)
        assertEquals(fallen.checkpointMs, fallen.elapsedMs, 1e-6)
        assertEquals(200.0, fallen.ball.x, 1e-9)
    }

    @Test
    fun `pickups must all be collected before the goal opens`() {
        val maze = boxMaze(
            pickups = listOf(LabyrinthPoint(100.0, 300.0), LabyrinthPoint(300.0, 300.0)),
            goal = LabyrinthPoint(350.0, 350.0),
        )
        val base = game(maze, LabyrinthBall(350.0, 350.0))
        val atGoal = LabyrinthEngine.step(base, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertEquals(LabyrinthStatus.PLAYING, atGoal.status)
        assertFalse(atGoal.goalOpen)

        var state = base.copy(ball = LabyrinthBall(100.0, 300.0))
        state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertEquals(1, state.collected.size)
        assertFalse(state.goalOpen)
        state = state.copy(ball = LabyrinthBall(300.0, 300.0))
        state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertTrue(state.goalOpen)
        state = state.copy(ball = LabyrinthBall(350.0, 350.0))
        state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertEquals(LabyrinthStatus.COMPLETE, state.status)
    }

    @Test
    fun `pickups emit an event and completing emits the finish cue`() {
        val maze = boxMaze(pickups = listOf(LabyrinthPoint(100.0, 100.0)))
        val base = game(maze, LabyrinthBall(100.0, 100.0))
        val collected = LabyrinthEngine.step(base, LabyrinthEngine.TICK_MS, 0.0, 0.0)
        assertTrue(collected.events.contains(LabyrinthEvent.PICKUP))
        assertTrue(collected.events.contains(LabyrinthEvent.GOAL_OPEN))
        val finished = LabyrinthEngine.step(
            collected.copy(ball = LabyrinthBall(maze.goal.x, maze.goal.y)),
            LabyrinthEngine.TICK_MS,
            0.0,
            0.0,
        )
        assertTrue(finished.events.contains(LabyrinthEvent.COMPLETE))
    }

    @Test
    fun `par stars step down at one and a half and double par`() {
        assertEquals(3, LabyrinthEngine.parStars(9000.0, 10))
        assertEquals(3, LabyrinthEngine.parStars(10000.0, 10))
        assertEquals(2, LabyrinthEngine.parStars(14000.0, 10))
        assertEquals(1, LabyrinthEngine.parStars(19000.0, 10))
        assertEquals(0, LabyrinthEngine.parStars(21000.0, 10))
    }

    @Test
    fun `tilt setting clamps to the slider range`() {
        assertEquals(0.5, LabyrinthEngine.newGame(0, 0.1).tiltSetting, 1e-9)
        assertEquals(1.5, LabyrinthEngine.newGame(0, 4.0).tiltSetting, 1e-9)
    }

    @Test
    fun `maze generation is deterministic per level`() {
        val a = LabyrinthMazes.generate(42)
        val b = LabyrinthMazes.generate(42)
        assertEquals(a.walls, b.walls)
        assertEquals(a.holes, b.holes)
        assertEquals(a.pickups, b.pickups)
        assertEquals(a.solution, b.solution)
        assertNotEquals(a.walls, LabyrinthMazes.generate(43).walls)
    }

    @Test
    fun `acts ship the spec counts plus bonus levels`() {
        assertEquals(116, LabyrinthEngine.LEVEL_COUNT)
        assertEquals(24, LabyrinthPack.ORCHARD.count)
        assertEquals(24, LabyrinthPack.GARDENS.count)
        assertEquals(24, LabyrinthPack.WORKSHOP.count)
        assertEquals(16, LabyrinthPack.RAPIDS.count)
        assertEquals(24, LabyrinthPack.QUARRY.count)
        assertEquals(4, LabyrinthPack.BONUS.count)
        assertEquals(LabyrinthPack.ORCHARD, LabyrinthEngine.packFor(0))
        assertEquals(LabyrinthPack.GARDENS, LabyrinthEngine.packFor(24))
        assertEquals(LabyrinthPack.WORKSHOP, LabyrinthEngine.packFor(48))
        assertEquals(LabyrinthPack.RAPIDS, LabyrinthEngine.packFor(72))
        assertEquals(LabyrinthPack.QUARRY, LabyrinthEngine.packFor(88))
        assertEquals(LabyrinthPack.BONUS, LabyrinthEngine.packFor(112))
        assertEquals(LabyrinthPack.BONUS, LabyrinthEngine.packFor(115))
    }

    @Test
    fun `every generated maze is solvable and its route is hazard free`() {
        for (levelId in 0 until LabyrinthEngine.LEVEL_COUNT) {
            val maze = LabyrinthMazes.forLevel(levelId)
            val startCell = maze.cellOf(maze.spawn)
            val links = passageLinks(maze)
            val reached = bfs(startCell, links)
            val goalCell = maze.cellOf(maze.goal)
            assertTrue("level $levelId goal unreachable", goalCell in reached)
            assertTrue("level $levelId has fewer than two pickups", maze.pickups.size >= 2)
            for (pickup in maze.pickups) {
                assertTrue("level $levelId pickup ${pickup.id} unreachable", maze.cellOf(LabyrinthPoint(pickup.x, pickup.y)) in reached)
            }
            val hazards = (maze.holes + maze.spikes).map { maze.cellOf(it) }.toHashSet()
            val safe = bfs(startCell, links, hazards)
            assertTrue("level $levelId goal blocked by hazards", goalCell in safe)
            for (pickup in maze.pickups) {
                assertTrue("level $levelId pickup ${pickup.id} blocked by hazards", maze.cellOf(LabyrinthPoint(pickup.x, pickup.y)) in safe)
            }
        }
    }

    @Test
    fun `marble never escapes a real generated board`() {
        val start = LabyrinthEngine.newGame(0)
        var state = start
        repeat(600) { tick ->
            val tiltX = 0.8 * sin(tick * 0.05)
            val tiltY = 0.8 * sin(tick * 0.031 + 1.0)
            state = LabyrinthEngine.step(state, LabyrinthEngine.TICK_MS, tiltX, tiltY)
        }
        assertFalse(state.ball.x.isNaN())
        assertFalse(state.ball.y.isNaN())
        assertTrue(state.ball.x >= -LabyrinthEngine.BALL_RADIUS)
        assertTrue(state.ball.y >= -LabyrinthEngine.BALL_RADIUS)
        assertTrue(state.ball.x <= start.maze.cols * cell + LabyrinthEngine.BALL_RADIUS)
        assertTrue(state.ball.y <= start.maze.rows * cell + LabyrinthEngine.BALL_RADIUS)
    }

    @Test
    fun `progress unlock ordering and encode round trip`() {
        var progress = LabyrinthProgress()
        assertEquals(0, progress.highestUnlocked)
        progress = LabyrinthEngine.complete(progress, 0, 12345.0, 3)
        assertEquals(1, progress.highestUnlocked)
        progress = LabyrinthEngine.complete(progress, 1, 20000.0, 1)
        progress = LabyrinthEngine.complete(progress, 1, 15000.0, 2)
        assertEquals(2, progress.highestUnlocked)
        assertEquals(15000.0, progress.bestMs[1] ?: 0.0, 1e-6)
        assertEquals(2, progress.stars[1])
        val decoded = LabyrinthEngine.decodeProgress(LabyrinthEngine.encodeProgress(progress))
        assertEquals(progress, decoded)
        assertEquals(LabyrinthProgress(), LabyrinthEngine.decodeProgress(null))
    }

    private fun passageLinks(maze: LabyrinthMaze): Map<Int, List<Int>> {
        val links = HashMap<Int, MutableList<Int>>()
        for (row in 0 until maze.rows) {
            for (col in 0 until maze.cols) {
                val cellIndex = row * maze.cols + col
                val center = LabyrinthPoint((col + 0.5) * cell, (row + 0.5) * cell)
                if (col < maze.cols - 1) {
                    val east = LabyrinthPoint((col + 1.5) * cell, (row + 0.5) * cell)
                    if (!separated(maze, center, east)) {
                        links.getOrPut(cellIndex) { mutableListOf() } += cellIndex + 1
                        links.getOrPut(cellIndex + 1) { mutableListOf() } += cellIndex
                    }
                }
                if (row < maze.rows - 1) {
                    val south = LabyrinthPoint((col + 0.5) * cell, (row + 1.5) * cell)
                    if (!separated(maze, center, south)) {
                        links.getOrPut(cellIndex) { mutableListOf() } += cellIndex + maze.cols
                        links.getOrPut(cellIndex + maze.cols) { mutableListOf() } += cellIndex
                    }
                }
            }
        }
        return links
    }

    private fun separated(maze: LabyrinthMaze, a: LabyrinthPoint, b: LabyrinthPoint): Boolean {
        val mx = (a.x + b.x) / 2.0
        val my = (a.y + b.y) / 2.0
        return maze.walls.any { wall ->
            mx >= wall.x - 0.5 && mx <= wall.right + 0.5 && my >= wall.y - 0.5 && my <= wall.bottom + 0.5
        }
    }

    private fun bfs(start: Int, links: Map<Int, List<Int>>, blocked: Set<Int> = emptySet()): Set<Int> {
        if (start in blocked) return emptySet()
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        seen += start
        queue += start
        while (queue.isNotEmpty()) {
            val cellIndex = queue.removeFirst()
            for (next in links[cellIndex] ?: emptyList()) {
                if (next in blocked || next in seen) continue
                seen += next
                queue += next
            }
        }
        return seen
    }

    @Test
    fun `obstacles never sit on the solution path`() {
        for (levelId in 0 until LabyrinthEngine.LEVEL_COUNT) {
            val maze = LabyrinthMazes.forLevel(levelId)
            val path = maze.solution.map { maze.cellOf(centerOf(it, maze)) }.toHashSet()
            for (obstacle in maze.obstacles) {
                for (cellIndex in path) {
                    val row = cellIndex / maze.cols
                    val col = cellIndex % maze.cols
                    val cx = (col + 0.5) * cell
                    val cy = (row + 0.5) * cell
                    assertTrue(
                        "level $levelId obstacle too close to route",
                        hypot(obstacle.x - cx, obstacle.y - cy) > obstacle.r + LabyrinthEngine.BALL_RADIUS,
                    )
                }
            }
        }
    }

    private fun centerOf(cellIndex: Int, maze: LabyrinthMaze): LabyrinthPoint =
        LabyrinthPoint(
            (cellIndex % maze.cols + 0.5) * cell,
            (cellIndex / maze.cols + 0.5) * cell,
        )
}
