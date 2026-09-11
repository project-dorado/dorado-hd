package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.FingerPhysicsEngine
import com.heretek.dorado_hd.ui.apps.games.FpGame
import com.heretek.dorado_hd.ui.apps.games.FpMedal
import com.heretek.dorado_hd.ui.apps.games.FpMedalKind
import com.heretek.dorado_hd.ui.apps.games.FpMode
import com.heretek.dorado_hd.ui.apps.games.FpProgress
import com.heretek.dorado_hd.ui.apps.games.FpStatus
import com.heretek.dorado_hd.ui.apps.games.P2World
import com.heretek.dorado_hd.ui.apps.games.Physics2d
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Finger Physics engine and shared Physics2d solver. */
class FingerPhysicsTest {

    /* ---------------------------------------------------------------- */
    /*                          Physics2d solver                        */
    /* ---------------------------------------------------------------- */

    @Test
    fun `solver prevents tunneling at reasonable speed`() {
        val world = P2World(0f, 900f)
        world.createBox(136f, 480f, 120f, 10f, 0f, "floor", isStatic = true)
        val ball = world.createCircle(136f, 100f, 8f, "ball")
        ball.vy = 420f
        var lowest = Float.MAX_VALUE
        repeat(400) {
            Physics2d.stepFixed(world, 1)
            assertTrue("ball tunnelled the floor at y=${ball.y}", ball.y <= 470f)
            if (ball.y > lowest) lowest = ball.y
        }
        assertTrue("ball never reached the floor", lowest > 400f)
        assertEquals(462f, ball.y, 3f)
        assertTrue(abs(ball.vy) < 1f)
    }

    @Test
    fun `solver loses energy across bounces`() {
        val world = P2World(0f, 900f)
        world.createBox(136f, 480f, 120f, 10f, 0f, "floor", isStatic = true)
        val ball = world.createCircle(136f, 200f, 10f, "ball", restitution = 0.5f)
        val peaks = ArrayList<Float>()
        var rising = false
        var currentMinY = ball.y
        repeat(900) {
            Physics2d.stepFixed(world, 1)
            if (ball.y < currentMinY) currentMinY = ball.y
            if (ball.vy < 0f) rising = true
            if (rising && ball.vy >= 0f) {
                peaks.add(470f - currentMinY)
                rising = false
                currentMinY = ball.y
            }
        }
        assertTrue("expected at least two bounce peaks, got $peaks", peaks.size >= 2)
        assertTrue("bounce peaks must decay: $peaks", peaks[1] < peaks[0])
        assertTrue("energy must keep decaying: $peaks", peaks.last() < peaks.first())
    }

    @Test
    fun `solver holds a resting box on its contact`() {
        val world = P2World(0f, 900f)
        world.createBox(136f, 480f, 120f, 10f, 0f, "floor", isStatic = true)
        val box = world.createBox(136f, 100f, 20f, 20f, 0f, "box")
        Physics2d.stepFixed(world, 600)
        assertEquals(450f, box.y, 3f)
        assertTrue(abs(box.vy) < 1f)
        assertTrue(abs(box.omega) < 0.05f)
    }

    /* ---------------------------------------------------------------- */
    /*                          Level tables                            */
    /* ---------------------------------------------------------------- */

    @Test
    fun `level tables cover ten modes and ninety levels`() {
        assertEquals(10, FpMode.entries.size)
        assertEquals(90, FingerPhysicsEngine.LEVELS.size)
        assertEquals((1..90).toList(), FingerPhysicsEngine.LEVELS.map { it.index })
        FpMode.entries.forEach { mode ->
            val levels = FingerPhysicsEngine.levelsFor(mode)
            assertEquals(9, levels.size)
            levels.forEachIndexed { index, level ->
                assertEquals(mode, level.mode)
                assertEquals(index + 1, level.slot)
            }
        }
    }

    @Test
    fun `medal thresholds tighten with level difficulty`() {
        FpMode.entries.forEach { mode ->
            val levels = FingerPhysicsEngine.levelsFor(mode)
            levels.forEach { level ->
                if (mode.medalKind == FpMedalKind.HEIGHT) {
                    assertTrue(level.bronze < level.silver)
                    assertTrue(level.silver < level.gold)
                } else {
                    assertTrue("gold must stay positive for $mode", level.gold > 0f)
                    assertTrue(level.gold < level.silver)
                    assertTrue(level.silver < level.bronze)
                }
            }
            levels.zipWithNext().forEach { (earlier, later) ->
                if (mode.medalKind == FpMedalKind.HEIGHT) {
                    assertTrue(later.bronze > earlier.bronze)
                    assertTrue(later.gold > earlier.gold)
                } else {
                    assertTrue(later.bronze < earlier.bronze)
                    assertTrue(later.gold < earlier.gold)
                }
            }
        }
    }

    @Test
    fun `medal award boundaries match the thresholds`() {
        val lawn = FingerPhysicsEngine.level(FpMode.LAWN, 3)
        assertEquals(FpMedal.GOLD, FingerPhysicsEngine.medalFor(lawn, lawn.gold))
        assertEquals(FpMedal.SILVER, FingerPhysicsEngine.medalFor(lawn, lawn.silver))
        assertEquals(FpMedal.BRONZE, FingerPhysicsEngine.medalFor(lawn, lawn.bronze))
        assertEquals(FpMedal.NONE, FingerPhysicsEngine.medalFor(lawn, lawn.bronze + 1f))

        val egg = FingerPhysicsEngine.level(FpMode.EGG, 4)
        assertEquals(FpMedal.GOLD, FingerPhysicsEngine.medalFor(egg, egg.gold + 1f))
        assertEquals(FpMedal.NONE, FingerPhysicsEngine.medalFor(egg, egg.bronze - 1f))
    }

    /* ---------------------------------------------------------------- */
    /*                          Objectives                              */
    /* ---------------------------------------------------------------- */

    @Test
    fun `underwater flips gravity upward`() {
        val game = FingerPhysicsEngine.newGame(FingerPhysicsEngine.level(FpMode.UNDERWATER, 1))
        assertTrue(game.world.gy < 0f)
        val dry = FingerPhysicsEngine.newGame(FingerPhysicsEngine.level(FpMode.EGG, 1))
        assertTrue(dry.world.gy > 0f)
    }

    @Test
    fun `gravity flip sends the ball upward`() {
        val game = FingerPhysicsEngine.newGame(FingerPhysicsEngine.level(FpMode.GRAVITY, 1))
        assertTrue(FingerPhysicsEngine.spawnNext(game))
        val ball = game.world.bodies.first { it.id == game.gravityBallId }
        val startY = ball.y
        assertTrue(FingerPhysicsEngine.flipGravity(game, ball.id))
        assertEquals(-1f, ball.gravityScale, 0.001f)
        FingerPhysicsEngine.stepSteps(game, 30)
        assertTrue("flipped ball should rise: $startY -> ${ball.y}", ball.y < startY)
    }

    @Test
    fun `objective must stay stable for five seconds`() {
        val level = FingerPhysicsEngine.level(FpMode.FREE, 1)
        val game = FingerPhysicsEngine.newGame(level)
        repeat(level.spawnCount) { assertTrue(FingerPhysicsEngine.spawnNext(game)) }
        FingerPhysicsEngine.stepSteps(game, 240)
        assertEquals(FpStatus.PLAYING, game.status)
        assertTrue(game.objectiveMet)
        FingerPhysicsEngine.stepSteps(game, 420)
        assertEquals(FpStatus.WON, game.status)
        assertEquals(FingerPhysicsEngine.medalFor(level, game.maxHeight), game.medal)
        assertEquals(300, FingerPhysicsEngine.STABLE_TIME_STEPS)
    }

    @Test
    fun `explosion falloff matches the spec curve`() {
        assertEquals(1f, FingerPhysicsEngine.explosionFalloff(0f), 0.001f)
        assertEquals(1f, FingerPhysicsEngine.explosionFalloff(48f), 0.001f)
        assertEquals(0.5f, FingerPhysicsEngine.explosionFalloff(120f), 0.001f)
        assertEquals(0f, FingerPhysicsEngine.explosionFalloff(192f), 0.001f)
        assertEquals(0f, FingerPhysicsEngine.explosionFalloff(400f), 0.001f)
    }

    @Test
    fun `drag moves a body and releases it`() {
        val game = FingerPhysicsEngine.newGame(FingerPhysicsEngine.level(FpMode.FREE, 1))
        assertTrue(FingerPhysicsEngine.spawnNext(game))
        val ball = game.world.bodies.last()
        val id = FingerPhysicsEngine.pickBody(game, ball.x, ball.y)
        assertEquals(ball.id, id)
        FingerPhysicsEngine.beginDrag(game, id)
        FingerPhysicsEngine.dragTo(game, 120f, 200f)
        assertEquals(120f, ball.x, 0.01f)
        assertEquals(200f, ball.y, 0.01f)
        assertTrue(ball.kinematic)
        FingerPhysicsEngine.endDrag(game)
        assertFalse(ball.kinematic)
    }

    /* ---------------------------------------------------------------- */
    /*                     Determinism & persistence                    */
    /* ---------------------------------------------------------------- */

    private fun replay(): List<String> {
        val game: FpGame = FingerPhysicsEngine.newGame(FingerPhysicsEngine.level(FpMode.EGG, 2))
        val snapshots = ArrayList<String>()
        FingerPhysicsEngine.spawnNext(game)
        repeat(40) {
            FingerPhysicsEngine.tick(game, 17L)
            snapshots.add(FingerPhysicsEngine.snapshot(game))
        }
        FingerPhysicsEngine.spawnNext(game)
        repeat(120) {
            FingerPhysicsEngine.tick(game, 17L)
            snapshots.add(FingerPhysicsEngine.snapshot(game))
        }
        return snapshots
    }

    @Test
    fun `fixed-step replay is deterministic`() {
        val first = replay()
        val second = replay()
        assertEquals(first, second)
        assertTrue(first.distinct().size > 1)
    }

    @Test
    fun `progress serialization round trips`() {
        var progress = FpProgress()
        progress = progress.record(FingerPhysicsEngine.level(FpMode.EGG, 1), FpMedal.BRONZE)
        progress = progress.record(FingerPhysicsEngine.level(FpMode.EGG, 1), FpMedal.SILVER)
        progress = progress.record(FingerPhysicsEngine.level(FpMode.LAWN, 2), FpMedal.GOLD)
        assertEquals(5, progress.points)

        val decoded = FingerPhysicsEngine.decodeProgress(FingerPhysicsEngine.encodeProgress(progress))
        assertEquals(FpMedal.SILVER, decoded.best(FingerPhysicsEngine.level(FpMode.EGG, 1)))
        assertEquals(FpMedal.GOLD, decoded.best(FingerPhysicsEngine.level(FpMode.LAWN, 2)))
        assertEquals(FpMedal.NONE, decoded.best(FingerPhysicsEngine.level(FpMode.FREE, 5)))
        assertEquals(5, decoded.points)
        assertEquals(FpMode.LAWN, decoded.lastMode)
        assertEquals(2, decoded.lastSlot)
        assertEquals(FpProgress(), FingerPhysicsEngine.decodeProgress("junk"))
        assertEquals(FpProgress(), FingerPhysicsEngine.decodeProgress(null))
    }
}
