package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.TrashScene
import com.heretek.dorado_hd.ui.apps.games.TrashThrowEngine
import com.heretek.dorado_hd.ui.apps.games.TrashThrowState
import com.heretek.dorado_hd.ui.apps.games.ThrowPhase
import com.heretek.dorado_hd.ui.apps.games.ThrowResult
import com.heretek.dorado_hd.ui.apps.games.Vec3
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the trash throw engine (no Android dependency). */
class TrashThrowTest {

    private val scene = TrashScene.BEDROOM

    private fun flying(state: TrashThrowState): TrashThrowState =
        if (state.phase == ThrowPhase.WAITING) TrashThrowEngine.flick(state, 0f) else state

    @Test
    fun `gravity integrates at 9_8`() {
        val start = TrashThrowEngine.newGame(scene, seed = 1)
        assertEquals(ThrowPhase.WAITING, start.phase)
        assertEquals(TrashThrowEngine.startPosition(scene), start.pos)
        val airborne = TrashThrowEngine.step(TrashThrowEngine.flick(start, 0f), 1000)
        assertEquals(scene.throwUp - TrashThrowEngine.GRAVITY, airborne.vel.y, 0.001f)
        assertTrue(airborne.pos.y > TrashThrowEngine.startPosition(scene).y)
    }

    @Test
    fun `swipe force clamps to plus or minus 75 newtons`() {
        assertEquals(75f, TrashThrowEngine.throwVelocity(scene, 1000f).x, 0.001f)
        assertEquals(-75f, TrashThrowEngine.throwVelocity(scene, -1000f).x, 0.001f)
        assertEquals(50f, TrashThrowEngine.throwVelocity(scene, 100f).x, 0.001f)
        assertEquals(-20f, TrashThrowEngine.throwVelocity(scene, -40f).x, 0.001f)
        assertEquals(scene.throwUp, TrashThrowEngine.throwVelocity(scene, 100f).y, 0.001f)
        assertEquals(scene.throwForward, TrashThrowEngine.throwVelocity(scene, 100f).z, 0.001f)
    }

    @Test
    fun `wind rerolls are never zero and respect scene maxima`() {
        assertEquals(0.5f, TrashScene.BEDROOM.windMax, 0.001f)
        assertEquals(3.0f, TrashScene.DENTIST.windMax, 0.001f)
        assertEquals(4.0f, TrashScene.OFFICE.windMax, 0.001f)
        TrashScene.entries.forEach { scene ->
            var positives = 0
            var negatives = 0
            for (seed in 0 until 200) {
                val wind = TrashThrowEngine.rollWind(scene, Random(seed))
                assertNotEquals(0f, wind)
                assertTrue("$scene wind $wind exceeds ${scene.windMax}", abs(wind) <= scene.windMax)
                if (wind > 0f) positives++ else negatives++
            }
            assertTrue("$scene should roll both directions", positives > 0 && negatives > 0)
        }
    }

    @Test
    fun `made basket scores one and miss resets the streak`() {
        var made = TrashThrowState(
            scene = scene,
            phase = ThrowPhase.FLYING,
            pos = Vec3(scene.goalX, 1f, scene.goalZ),
            vel = Vec3(0f, -1f, 0f),
            streak = 2,
            best = 2,
            seed = 1,
        )
        repeat(3) { made = TrashThrowEngine.step(made, 100) }
        assertEquals(ThrowPhase.RESOLVED, made.phase)
        assertEquals(ThrowResult.MADE, made.result)
        assertEquals(3, made.streak)
        assertEquals(3, made.best)

        var missed = TrashThrowState(
            scene = scene,
            phase = ThrowPhase.FLYING,
            pos = Vec3(5f, 1f, 3f),
            vel = Vec3(0f, -1f, 0f),
            streak = 4,
            best = 4,
            seed = 1,
        )
        var guard = 0
        while (missed.phase == ThrowPhase.FLYING && guard < 400) {
            missed = TrashThrowEngine.step(missed, 16)
            guard++
        }
        assertEquals(ThrowPhase.RESOLVED, missed.phase)
        assertEquals(ThrowResult.MISS, missed.result)
        assertEquals(0, missed.streak)
        assertEquals(4, missed.best)
    }

    @Test
    fun `reset cadence is thirty ticks`() {
        val resolved = TrashThrowState(
            scene = scene,
            phase = ThrowPhase.RESOLVED,
            result = ThrowResult.MISS,
            resetMs = TrashThrowEngine.RESET_MS,
            fadeMs = TrashThrowEngine.FADE_MS,
            seed = 1,
        )
        assertEquals(ThrowPhase.RESOLVED, resolved.phase)
        var state = resolved
        repeat(TrashThrowEngine.RESET_TICKS - 1) { state = TrashThrowEngine.step(state, 16) }
        assertEquals(ThrowPhase.RESOLVED, state.phase)
        state = TrashThrowEngine.step(state, 16)
        assertEquals(ThrowPhase.WAITING, state.phase)
        assertNotEquals(0f, state.wind)
        assertEquals(TrashThrowEngine.startPosition(scene), state.pos)
        assertEquals(ThrowResult.NONE, state.result)
    }

    @Test
    fun `ground bounce uses half restitution then settles`() {
        var state = TrashThrowState(
            scene = scene,
            phase = ThrowPhase.FLYING,
            pos = Vec3(5f, 0.15f, 3f),
            vel = Vec3(0f, -1f, 0f),
            seed = 1,
        )
        var bounces = 0
        var guard = 0
        while (state.phase == ThrowPhase.FLYING && guard < 400) {
            val before = state.groundTouches
            state = TrashThrowEngine.step(state, 16)
            guard++
            if (state.groundTouches > before && before == 0) {
                assertTrue("first ground contact must bounce upward", state.vel.y > 0f)
                bounces++
            }
        }
        assertEquals(1, bounces)
        assertEquals(ThrowPhase.RESOLVED, state.phase)
        assertEquals(ThrowResult.MISS, state.result)
        assertTrue(state.groundTouches >= 2)
    }

    @Test
    fun `per scene bests persist and only improve`() {
        var scores = TrashThrowEngine.TrashScores()
        scores = TrashThrowEngine.recordBest(scores, TrashScene.BEDROOM, 5)
        scores = TrashThrowEngine.recordBest(scores, TrashScene.OFFICE, 2)
        scores = TrashThrowEngine.recordBest(scores, TrashScene.BEDROOM, 3)
        assertEquals(5, scores.best(TrashScene.BEDROOM))
        assertEquals(2, scores.best(TrashScene.OFFICE))
        assertEquals(0, scores.best(TrashScene.DENTIST))
        assertEquals(5, scores.overall)
        val restored = TrashThrowEngine.decodeScores(TrashThrowEngine.encodeScores(scores))
        TrashScene.entries.forEach { scene ->
            assertEquals(scores.best(scene), restored.best(scene))
        }
        assertEquals(TrashThrowEngine.TrashScores(), TrashThrowEngine.decodeScores("junk"))
    }

    @Test
    fun `swipe classification needs thirty px within sixty ticks`() {
        assertFalse(TrashThrowEngine.isSwipe(20f, 20f, 10))
        assertTrue(TrashThrowEngine.isSwipe(30f, 0f, 60))
        assertTrue(TrashThrowEngine.isSwipe(0f, 30f, 5))
        assertFalse(TrashThrowEngine.isSwipe(30f, 0f, 61))
    }

    @Test
    fun `projection places the goal above the horizon`() {
        val projected = TrashThrowEngine.project(
            scene,
            Vec3(scene.goalX, 0f, scene.goalZ),
        )
        assertTrue(projected.scale > 0f)
        assertTrue(projected.x in 0f..TrashThrowEngine.SCREEN_WIDTH)
        assertTrue(projected.y in 0f..TrashThrowEngine.SCREEN_HEIGHT)
    }
}
