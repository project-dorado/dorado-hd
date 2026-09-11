package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.NovaDot
import com.heretek.dorado_hd.ui.apps.games.NovaDotState
import com.heretek.dorado_hd.ui.apps.games.NovaMode
import com.heretek.dorado_hd.ui.apps.games.NovaPhase
import com.heretek.dorado_hd.ui.apps.games.NovaState
import com.heretek.dorado_hd.ui.apps.games.SupernovaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupernovaTest {

    private fun dot(
        id: Int,
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        state: NovaDotState = NovaDotState.MOVING,
        chain: Int = 0,
    ) = NovaDot(
        id = id,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        colorIndex = id % SupernovaEngine.DOT_COLORS,
        state = state,
        chain = chain,
    )

    private fun nova(
        mode: NovaMode = NovaMode.NORMAL,
        level: Int = 1,
        dots: List<NovaDot>,
        needed: Int = 1,
        total: Int = 0,
        moveAvailable: Boolean = true,
        levelStarted: Boolean = true,
        timeLeftMs: Long = SupernovaEngine.TIME_NORMAL_MS,
        phase: NovaPhase = NovaPhase.PLAY,
    ) = NovaState(
        mode = mode,
        level = level,
        totalDots = if (total == 0) dots.size else total,
        neededDots = needed,
        dots = dots,
        timeLeftMs = timeLeftMs,
        moveAvailable = moveAvailable,
        levelStarted = levelStarted,
        phase = phase,
        seed = 1,
    )

    @Test
    fun `level tables match the mined pairs`() {
        assertEquals(
            listOf(60 to 50, 55 to 48, 50 to 43, 45 to 38, 40 to 30, 35 to 22, 30 to 17, 25 to 13, 20 to 9, 15 to 6, 10 to 4, 5 to 2),
            SupernovaEngine.LEVELS_NORMAL,
        )
        assertEquals(5 to 3, SupernovaEngine.LEVELS_HARD.last())
        assertEquals(5 to 2, SupernovaEngine.LEVELS_NORMAL.last())
        assertEquals(60 to 50, SupernovaEngine.levelEntry(NovaMode.NORMAL, 1))
        assertEquals(5 to 2, SupernovaEngine.levelEntry(NovaMode.NORMAL, 12))
        assertEquals(5 to 3, SupernovaEngine.levelEntry(NovaMode.HARD, 12))
        assertEquals(60 to 0, SupernovaEngine.levelEntry(NovaMode.ENDLESS, 4))
        assertEquals(60, SupernovaEngine.DOT_POOL)
        assertEquals(19, SupernovaEngine.DOT_COLORS)
    }

    @Test
    fun `multiplier table is fixed per level and endless is flat`() {
        assertEquals(listOf(1, 1, 1, 2, 2, 4, 4, 8, 12, 30, 60, 150), SupernovaEngine.MULTIPLIERS)
        assertEquals(1, SupernovaEngine.multiplier(NovaMode.NORMAL, 3))
        assertEquals(8, SupernovaEngine.multiplier(NovaMode.NORMAL, 8))
        assertEquals(150, SupernovaEngine.multiplier(NovaMode.NORMAL, 12))
        assertEquals(150, SupernovaEngine.multiplier(NovaMode.HARD, 12))
        assertEquals(1, SupernovaEngine.multiplier(NovaMode.ENDLESS, 12))
    }

    @Test
    fun `hard doubles the value and scales timings and speed`() {
        assertEquals(5, SupernovaEngine.dotValue(NovaMode.NORMAL))
        assertEquals(10, SupernovaEngine.dotValue(NovaMode.HARD))
        assertEquals(Triple(500f, 750f, 500f), SupernovaEngine.explosionTimings(NovaMode.NORMAL))
        assertEquals(Triple(375f, 562.5f, 375f), SupernovaEngine.explosionTimings(NovaMode.HARD))
        assertEquals(50f, SupernovaEngine.spawnSpeed(NovaMode.NORMAL, 0f), 0.001f)
        assertEquals(100f, SupernovaEngine.spawnSpeed(NovaMode.NORMAL, 1f), 0.001f)
        assertEquals(42.5f, SupernovaEngine.spawnSpeed(NovaMode.HARD, 0f), 0.001f)
        assertEquals(63.75f, SupernovaEngine.spawnSpeed(NovaMode.HARD, 1f), 0.001f)
    }

    @Test
    fun `timers are 30 s normal 15 s hard and none endless`() {
        assertEquals(30_000L, SupernovaEngine.timeLimitMs(NovaMode.NORMAL))
        assertEquals(15_000L, SupernovaEngine.timeLimitMs(NovaMode.HARD))
        assertEquals(0L, SupernovaEngine.timeLimitMs(NovaMode.ENDLESS))
    }

    @Test
    fun `a new level spawns the table dots with a 500 ms grow`() {
        val game = SupernovaEngine.newLevel(NovaMode.NORMAL, 1, seed = 123)
        assertEquals(60, game.dots.size)
        assertEquals(50, game.neededDots)
        assertTrue(game.dots.all { it.state == NovaDotState.SPAWNING })
        assertTrue(game.dots.all { it.colorIndex in 0 until SupernovaEngine.DOT_COLORS })
        assertTrue(game.dots.all { it.x in 0f..SupernovaEngine.FIELD_WIDTH })
        assertTrue(game.dots.all { it.y in 0f..(SupernovaEngine.FIELD_HEIGHT - SupernovaEngine.HUD_HEIGHT) })

        val growing = SupernovaEngine.step(game, 250)
        assertFalse(growing.levelStarted)
        assertTrue(growing.dots.all { it.state == NovaDotState.SPAWNING })

        val moving = SupernovaEngine.step(growing, 300)
        assertTrue(moving.levelStarted)
        assertTrue(moving.dots.all { it.state == NovaDotState.MOVING })
    }

    @Test
    fun `spawned dots stay inside the normal speed band`() {
        val game = SupernovaEngine.newLevel(NovaMode.NORMAL, 5, seed = 9)
        for (d in game.dots) {
            val speed = kotlin.math.hypot(d.vx, d.vy)
            assertTrue("speed $speed out of band", speed in 50f..100f)
        }
    }

    @Test
    fun `tap is accepted once and only after the level starts`() {
        val waiting = nova(dots = listOf(dot(0, 100f, 100f)), levelStarted = false)
        assertSame(waiting, SupernovaEngine.tap(waiting, 50f, 50f))

        val ready = nova(dots = listOf(dot(0, 100f, 100f)))
        val fired = SupernovaEngine.tap(ready, 50f, 50f)
        assertEquals(2, fired.dots.size)
        assertFalse(fired.moveAvailable)
        assertEquals(NovaDotState.EXPLODING, fired.dots.last().state)
        assertSame(fired, SupernovaEngine.tap(fired, 60f, 60f))
    }

    @Test
    fun `tap clamps the spawner to the field`() {
        val ready = nova(dots = listOf(dot(0, 100f, 100f)))
        val fired = SupernovaEngine.tap(ready, 999f, 999f)
        val spawner = fired.dots.last()
        assertEquals(SupernovaEngine.MAX_X, spawner.x, 0.001f)
        assertEquals(SupernovaEngine.MAX_Y, spawner.y, 0.001f)
    }

    @Test
    fun `chain scores the chain number times the dot value`() {
        val first = dot(0, 140f, 200f)
        val second = dot(1, 189f, 200f)
        var game = nova(dots = listOf(first, second), needed = 2)
        game = SupernovaEngine.tap(game, 100f, 200f)
        repeat(80) { game = SupernovaEngine.step(game, 50) }

        assertEquals(NovaPhase.COMPLETE, game.phase)
        assertEquals(2, game.dotsExploded)
        assertEquals(3, game.dots.size)
        assertEquals(2, game.dots.maxOf { it.chain })
        assertEquals(15, game.levelScore)
        assertEquals(15, game.score)
        assertTrue(game.settled)
    }

    @Test
    fun `hard mode awards double value per chain step`() {
        val first = dot(0, 140f, 200f)
        val second = dot(1, 189f, 200f)
        var game = nova(mode = NovaMode.HARD, dots = listOf(first, second), needed = 2, timeLeftMs = SupernovaEngine.TIME_HARD_MS)
        game = SupernovaEngine.tap(game, 100f, 200f)
        repeat(80) { game = SupernovaEngine.step(game, 50) }
        assertEquals(NovaPhase.COMPLETE, game.phase)
        assertEquals(30, game.levelScore)
        assertEquals(30, game.score)
    }

    @Test
    fun `survivors shrink away and resolve the level`() {
        val near = dot(0, 70f, 200f)
        val far = dot(1, 250f, 400f)
        var game = nova(dots = listOf(near, far), needed = 1)
        game = SupernovaEngine.tap(game, 50f, 200f)
        repeat(160) { game = SupernovaEngine.step(game, 50) }
        assertEquals(NovaPhase.COMPLETE, game.phase)
        assertEquals(1, game.dotsExploded)
        assertTrue(game.dots.all { it.state == NovaDotState.COMPLETE })
    }

    @Test
    fun `a missed goal fails the level`() {
        val near = dot(0, 70f, 200f)
        val far = dot(1, 250f, 400f)
        var game = nova(dots = listOf(near, far), needed = 2)
        game = SupernovaEngine.tap(game, 50f, 200f)
        repeat(160) { game = SupernovaEngine.step(game, 50) }
        assertEquals(NovaPhase.FAILED, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun `timeout fails an unresolved level`() {
        var game = nova(dots = listOf(dot(0, 100f, 100f)), needed = 1, timeLeftMs = 1000)
        game = SupernovaEngine.step(game, 1000)
        assertEquals(0L, game.timeLeftMs)
        assertFalse(game.moveAvailable)
        assertEquals(NovaPhase.RESOLVING, game.phase)
        game = SupernovaEngine.step(game, 500)
        assertEquals(NovaPhase.FAILED, game.phase)
        assertEquals(NovaPhase.FAILED, SupernovaEngine.step(game, 1000).phase)
    }

    @Test
    fun `moving dots bounce off all four edges`() {
        var game = nova(dots = listOf(dot(0, 261f, 100f, vx = 50f)))
        game = SupernovaEngine.step(game, 1000)
        assertEquals(SupernovaEngine.MAX_X, game.dots[0].x, 0.001f)
        assertTrue(game.dots[0].vx < 0f)

        game = nova(dots = listOf(dot(0, 0f, 100f, vx = -50f)))
        game = SupernovaEngine.step(game, 1000)
        assertEquals(0f, game.dots[0].x, 0.001f)
        assertTrue(game.dots[0].vx > 0f)

        game = nova(dots = listOf(dot(0, 100f, 417f, vy = 50f)))
        game = SupernovaEngine.step(game, 1000)
        assertEquals(SupernovaEngine.MAX_Y, game.dots[0].y, 0.001f)
        assertTrue(game.dots[0].vy < 0f)

        game = nova(dots = listOf(dot(0, 100f, 0f, vy = -50f)))
        game = SupernovaEngine.step(game, 1000)
        assertEquals(0f, game.dots[0].y, 0.001f)
        assertTrue(game.dots[0].vy > 0f)
    }

    @Test
    fun `explosion size grows holds and shrinks over the mined timings`() {
        var game = nova(dots = emptyList(), needed = 0)
        game = SupernovaEngine.tap(game, 100f, 100f)
        val spawner = game.dots.last()
        assertEquals(SupernovaEngine.DOT_RADIUS, SupernovaEngine.dotSize(spawner, NovaMode.NORMAL), 0.001f)
        game = SupernovaEngine.step(game, 250)
        val growing = SupernovaEngine.dotSize(game.dots.last(), NovaMode.NORMAL)
        assertTrue(growing > SupernovaEngine.DOT_RADIUS && growing < SupernovaEngine.EXPLODE_MAX_SIZE)
        repeat(40) { game = SupernovaEngine.step(game, 50) }
        assertTrue(game.dots.all { it.state == NovaDotState.COMPLETE })
    }

    @Test
    fun `endless mode loops and caps the level counter`() {
        val endless = SupernovaEngine.newLevel(NovaMode.ENDLESS, 7, seed = 1)
        assertEquals(60, endless.totalDots)
        assertEquals(0, endless.neededDots)
        assertEquals(0L, endless.timeLeftMs)
        val complete = endless.copy(phase = NovaPhase.COMPLETE, score = 500, settled = true)
        val next = SupernovaEngine.nextLevel(complete)
        assertEquals(8, next.level)
        assertEquals(500, next.score)
        val capped = SupernovaEngine.nextLevel(next.copy(level = 99))
        assertEquals(99, capped.level)
        assertTrue(SupernovaEngine.isLastLevel(NovaMode.NORMAL, 12))
        assertFalse(SupernovaEngine.isLastLevel(NovaMode.ENDLESS, 12))
    }

    @Test
    fun `high scores insert into the top ten`() {
        assertEquals(listOf(100, 75, 50), SupernovaEngine.insertHighScore(listOf(100, 50), 75))
        assertEquals(listOf(100, 50), SupernovaEngine.insertHighScore(listOf(100, 50), 0))
        assertEquals(listOf(100, 50), SupernovaEngine.insertHighScore(listOf(100, 50), -3))
        val filled = (1..12).map { it * 10 }
        assertEquals(10, SupernovaEngine.insertHighScore(filled, 500).size)
        assertEquals(500, SupernovaEngine.insertHighScore(filled, 500).first())
        assertEquals(0, SupernovaEngine.highScoreRank(listOf(100, 50), 200))
        assertEquals(2, SupernovaEngine.highScoreRank(listOf(100, 50), 10))
        assertEquals(2, SupernovaEngine.highScoreRank(listOf(100, 50), 5))
        assertEquals(-1, SupernovaEngine.highScoreRank(List(10) { 100 }, 50))
    }

    @Test
    fun `encode and decode round trip a running level`() {
        var game = SupernovaEngine.newLevel(NovaMode.HARD, 3, seed = 77)
        game = SupernovaEngine.step(game, 600)
        game = SupernovaEngine.tap(game, 120f, 200f)
        repeat(4) { game = SupernovaEngine.step(game, 33) }
        val restored = SupernovaEngine.decode(SupernovaEngine.encode(game))
        assertEquals(game, restored)
    }

    @Test
    fun `decode rejects malformed blobs`() {
        assertEquals(null, SupernovaEngine.decode(null))
        assertEquals(null, SupernovaEngine.decode("nonsense"))
    }
}
