package com.heretek.dorado_hd

import com.heretek.dorado_hd.analysis.AudioFeatures
import com.heretek.dorado_hd.ui.apps.games.AudiosurfEngine
import com.heretek.dorado_hd.ui.apps.games.AudiosurfMedal
import com.heretek.dorado_hd.ui.apps.games.AudiosurfMode
import com.heretek.dorado_hd.ui.apps.games.CourseElement
import com.heretek.dorado_hd.ui.apps.games.CourseElementKind
import com.heretek.dorado_hd.ui.apps.games.RideCourse
import com.heretek.dorado_hd.ui.apps.games.RideEventKind
import com.heretek.dorado_hd.ui.apps.games.RideStatus
import com.heretek.dorado_hd.ui.apps.games.TrackProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Audiosurf Tilt engine: profile mapping and synthetic
 * fallback, course determinism and difficulty shape, surf scoring/chain/miss
 * rules, monotonic stepping, tilt/steering math and state persistence.
 */
class AudiosurfTest {

    private val profile = TrackProfile(
        trackId = 7L,
        bpm = 120.0,
        energy = 0.8,
        valence = 0.5,
        centroid = 0.4,
        durationMs = 30_000L,
    )

    private fun element(
        positionMs: Long,
        kind: CourseElementKind,
        lane: Int = 1,
        index: Int = 0,
    ) = CourseElement(index, beat = index, positionMs = positionMs, lane = lane, kind = kind)

    private fun testCourse(
        vararg elements: CourseElement,
        mode: AudiosurfMode = AudiosurfMode.NORMAL,
        durationMs: Long = 30_000L,
    ) = RideCourse(
        profile = profile.copy(durationMs = durationMs),
        mode = mode,
        seed = 0,
        elements = elements.toList(),
        coloredTotal = elements.count { it.kind == CourseElementKind.COLOR_BLOCK },
        greyTotal = elements.count { it.kind == CourseElementKind.GREY_BLOCK },
        durationMs = durationMs,
    )

    private fun ride(course: RideCourse) = AudiosurfEngine.newRide(course)

    /* ------------------------------------------------------------ */
    /*                        profile mapping                         */
    /* ------------------------------------------------------------ */

    @Test
    fun `profile maps analysis features and clamps outliers`() {
        val features = AudioFeatures(
            trackId = 9L,
            bpm = 300.0,
            energy = 1.4,
            valence = -0.2,
            acousticness = 0.1,
            danceability = 0.5,
            spectralCentroid = 0.77,
        )
        val mapped = AudiosurfEngine.profileFor(features, 200_000L, trackId = 9L)
        assertEquals(200.0, mapped.bpm, 1e-9)
        assertEquals(1.0, mapped.energy, 1e-9)
        assertEquals(0.0, mapped.valence, 1e-9)
        assertEquals(0.77, mapped.centroid, 1e-9)
        assertEquals(200_000L, mapped.durationMs)
        assertEquals(9L, mapped.trackId)
    }

    @Test
    fun `synthetic profile fallback is deterministic and seed distinct`() {
        val a = AudiosurfEngine.profileFor(null, 90_000L, seed = 11, trackId = -1L)
        val b = AudiosurfEngine.profileFor(null, 90_000L, seed = 11, trackId = -1L)
        assertEquals(a, b)
        assertTrue(a.bpm in 60.0..200.0)
        assertTrue(a.energy in 0.0..1.0)
        assertTrue(a.valence in 0.0..1.0)
        assertTrue(a.centroid in 0.0..1.0)
        assertEquals(90_000L, a.durationMs)
        val other = AudiosurfEngine.profileFor(null, 90_000L, seed = 12, trackId = -1L)
        assertTrue("different seeds should shape different synthetic songs", a != other)
    }

    @Test
    fun `short durations are floored so a course always exists`() {
        val tiny = AudiosurfEngine.profileFor(null, 500L, seed = 1, trackId = -1L)
        assertEquals(AudiosurfEngine.MIN_DURATION_MS, tiny.durationMs)
        assertTrue(AudiosurfEngine.buildCourse(tiny, AudiosurfMode.NORMAL, 1).elements.isNotEmpty())
    }

    /* ------------------------------------------------------------ */
    /*                       course generation                        */
    /* ------------------------------------------------------------ */

    @Test
    fun `course generation is deterministic for a fixed profile`() {
        val a = AudiosurfEngine.buildCourse(profile, AudiosurfMode.NORMAL, seed = 42)
        val b = AudiosurfEngine.buildCourse(profile, AudiosurfMode.NORMAL, seed = 42)
        assertEquals(a, b)
        assertTrue(a.elements.isNotEmpty())
        assertTrue(a.coloredTotal + a.greyTotal > 0)
        assertTrue(a.elements.all { it.beat >= 1 && it.positionMs > 0L })
        assertTrue(a.elements.zipWithNext().all { (x, y) -> x.positionMs <= y.positionMs })
    }

    @Test
    fun `hard builds a denser course with more grey pressure and double points`() {
        val normal = AudiosurfEngine.buildCourse(profile, AudiosurfMode.NORMAL, seed = 5)
        val hard = AudiosurfEngine.buildCourse(profile, AudiosurfMode.HARD, seed = 5)
        assertTrue(hard.elements.size > normal.elements.size)
        assertTrue(hard.greyTotal >= normal.greyTotal)
        assertEquals(normal.durationMs, hard.durationMs)
        assertEquals(1, AudiosurfEngine.pointGain(AudiosurfMode.NORMAL))
        assertEquals(2, AudiosurfEngine.pointGain(AudiosurfMode.HARD))
        assertEquals(0, AudiosurfEngine.pointGain(AudiosurfMode.VISUALIZER))
        assertTrue(AudiosurfEngine.goldThreshold(AudiosurfMode.HARD) > AudiosurfEngine.goldThreshold(AudiosurfMode.NORMAL))
    }

    @Test
    fun `course contains speed and jump elements`() {
        val long = profile.copy(durationMs = 120_000L)
        val course = AudiosurfEngine.buildCourse(long, AudiosurfMode.NORMAL, seed = 3)
        assertTrue(course.elements.any { it.kind == CourseElementKind.SPEED_UP || it.kind == CourseElementKind.SLOW_DOWN })
        assertTrue(course.elements.any { it.kind == CourseElementKind.JUMP })
    }

    /* ------------------------------------------------------------ */
    /*                            scoring                             */
    /* ------------------------------------------------------------ */

    @Test
    fun `clean pickups score and extend the chain`() {
        val course = testCourse(
            element(1_000, CourseElementKind.COLOR_BLOCK),
            element(2_000, CourseElementKind.COLOR_BLOCK),
            element(3_000, CourseElementKind.COLOR_BLOCK),
        )
        var state = AudiosurfEngine.step(ride(course), 1_000)
        assertEquals(1, state.score)
        assertEquals(1, state.chain)
        assertEquals(1, state.collected)
        assertTrue(state.events.any { it.kind == RideEventKind.COLLECT })
        state = AudiosurfEngine.step(state, 3_000)
        assertEquals(3, state.score)
        assertEquals(3, state.chain)
        assertEquals(3, state.collected)
        assertEquals(0, state.misses)
        assertEquals(3, state.longestChain)
    }

    @Test
    fun `hard pickups are worth double`() {
        val course = testCourse(element(1_000, CourseElementKind.COLOR_BLOCK), mode = AudiosurfMode.HARD)
        val state = AudiosurfEngine.step(ride(course), 1_000)
        assertEquals(2, state.score)
    }

    @Test
    fun `chain rewards fire at the mined thresholds`() {
        val elements = (1..5).map { element(it * 1_000L, CourseElementKind.COLOR_BLOCK, index = it) }
        val course = testCourse(*elements.toTypedArray())
        val state = AudiosurfEngine.step(ride(course), 5_000)
        assertEquals(5, state.chain)
        val reward = state.events.firstOrNull { it.kind == RideEventKind.CHAIN }
        assertNotNull(reward)
        assertEquals("chain 5", reward!!.label)
        assertNull(AudiosurfEngine.chainRewardLabel(4))
        assertNull(AudiosurfEngine.chainRewardLabel(6))
        assertEquals("chain 11", AudiosurfEngine.chainRewardLabel(11))
        assertEquals("chain 25", AudiosurfEngine.chainRewardLabel(25))
        assertEquals("chain 50", AudiosurfEngine.chainRewardLabel(50))
        assertEquals("chain 100", AudiosurfEngine.chainRewardLabel(100))
        assertEquals("chain 200", AudiosurfEngine.chainRewardLabel(200))
        assertNull(AudiosurfEngine.chainRewardLabel(101))
    }

    @Test
    fun `missed coloured blocks break the chain and count`() {
        val course = testCourse(
            element(1_000, CourseElementKind.COLOR_BLOCK, lane = 1),
            element(2_000, CourseElementKind.COLOR_BLOCK, lane = 1),
            element(3_000, CourseElementKind.COLOR_BLOCK, lane = 0),
            element(4_000, CourseElementKind.COLOR_BLOCK, lane = 1),
        )
        var state = AudiosurfEngine.step(ride(course), 2_000)
        assertEquals(2, state.chain)
        state = AudiosurfEngine.step(state, 3_000)
        assertEquals(0, state.chain)
        assertEquals(1, state.misses)
        assertEquals(2, state.score)
        assertTrue(state.events.any { it.kind == RideEventKind.MISS })
        state = AudiosurfEngine.step(state, 4_000)
        assertEquals(3, state.score)
        assertEquals(1, state.chain)
    }

    @Test
    fun `grey blocks in the lane are a stone hit and score nothing`() {
        val course = testCourse(
            element(1_000, CourseElementKind.COLOR_BLOCK, lane = 1),
            element(2_000, CourseElementKind.GREY_BLOCK, lane = 1),
            element(3_000, CourseElementKind.GREY_BLOCK, lane = 0),
        )
        var state = AudiosurfEngine.step(ride(course), 1_000)
        assertEquals(1, state.chain)
        state = AudiosurfEngine.step(state, 2_000)
        assertEquals(0, state.chain)
        assertEquals(1, state.stones)
        assertEquals(1, state.score)
        assertTrue(state.events.any { it.kind == RideEventKind.STONE })
        state = AudiosurfEngine.step(state, 3_000)
        assertEquals(1, state.stones)
        assertEquals(0, state.misses)
        assertEquals(0, state.chain)
    }

    @Test
    fun `a clean full pickup ends in a perfect ride`() {
        val course = testCourse(
            element(1_000, CourseElementKind.COLOR_BLOCK),
            element(2_000, CourseElementKind.COLOR_BLOCK),
        )
        val state = AudiosurfEngine.step(ride(course), 30_000)
        assertEquals(2, state.collected)
        assertEquals(RideStatus.FINISHED, state.status)
        assertTrue(state.events.any { it.kind == RideEventKind.PERFECT })
    }

    @Test
    fun `step is monotonic and clamps at the finish`() {
        val course = testCourse(element(500, CourseElementKind.COLOR_BLOCK))
        var state = AudiosurfEngine.step(ride(course), 1_000)
        assertEquals(1_000L, state.trackPositionMs)
        val backwards = AudiosurfEngine.step(state, 500)
        assertEquals(1_000L, backwards.trackPositionMs)
        assertTrue(backwards.events.isEmpty())
        val finished = AudiosurfEngine.step(state, course.durationMs + 5_000)
        assertEquals(RideStatus.FINISHED, finished.status)
        assertEquals(course.durationMs, finished.trackPositionMs)
        assertTrue(finished.events.any { it.kind == RideEventKind.FINISH })
        val after = AudiosurfEngine.step(finished, finished.trackPositionMs + 1_000)
        assertEquals(RideStatus.FINISHED, after.status)
        assertEquals(finished.score, after.score)
        assertTrue(after.events.isEmpty())
    }

    @Test
    fun `visualizer rides score nothing and never miss`() {
        val course = testCourse(
            element(1_000, CourseElementKind.COLOR_BLOCK, lane = 1),
            element(2_000, CourseElementKind.GREY_BLOCK, lane = 1),
            element(3_000, CourseElementKind.COLOR_BLOCK, lane = 0),
            mode = AudiosurfMode.VISUALIZER,
        )
        val state = AudiosurfEngine.step(ride(course), 3_000)
        assertEquals(0, state.score)
        assertEquals(0, state.chain)
        assertEquals(0, state.collected)
        assertEquals(0, state.misses)
        assertEquals(0, state.stones)
        assertFalse(state.events.any { it.kind == RideEventKind.MISS })
    }

    @Test
    fun `jump and speed elements drive the ride modifiers`() {
        val course = testCourse(
            element(1_000, CourseElementKind.JUMP),
            element(2_000, CourseElementKind.SPEED_UP),
        )
        var state = AudiosurfEngine.step(ride(course), 1_000)
        assertTrue(state.jumping)
        assertEquals(AudiosurfEngine.JUMP_TIME_MS, state.jumpMsLeft)
        state = AudiosurfEngine.step(state, 2_000)
        assertTrue(state.jumping)
        assertTrue(state.jumpHeight > 0f)
        assertEquals(AudiosurfEngine.SPEED_UP_SCALE, state.speedScale, 1e-6f)
        assertEquals(AudiosurfEngine.SPEED_EFFECT_MS, state.speedMsLeft)
        state = AudiosurfEngine.step(state, 6_500)
        assertEquals(1f, state.speedScale, 1e-6f)
        assertFalse(state.jumping)
    }

    @Test
    fun `slowing elements drop the ride speed`() {
        val course = testCourse(element(1_000, CourseElementKind.SLOW_DOWN))
        val state = AudiosurfEngine.step(ride(course), 1_000)
        assertEquals(AudiosurfEngine.SLOW_DOWN_SCALE, state.speedScale, 1e-6f)
    }

    /* ------------------------------------------------------------ */
    /*                          medals                                */
    /* ------------------------------------------------------------ */

    @Test
    fun `medal thresholds match the spec fractions`() {
        assertEquals(AudiosurfMedal.NONE, AudiosurfEngine.medalFor(69, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.BRONZE, AudiosurfEngine.medalFor(70, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.BRONZE, AudiosurfEngine.medalFor(139, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.SILVER, AudiosurfEngine.medalFor(140, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.SILVER, AudiosurfEngine.medalFor(187, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.GOLD, AudiosurfEngine.medalFor(188, 200, AudiosurfMode.NORMAL))
        assertEquals(AudiosurfMedal.SILVER, AudiosurfEngine.medalFor(194, 200, AudiosurfMode.HARD))
        assertEquals(AudiosurfMedal.GOLD, AudiosurfEngine.medalFor(195, 200, AudiosurfMode.HARD))
        assertEquals(AudiosurfMedal.NONE, AudiosurfEngine.medalFor(200, 200, AudiosurfMode.VISUALIZER))
        assertEquals(AudiosurfMedal.NONE, AudiosurfEngine.medalFor(10, 0, AudiosurfMode.NORMAL))
    }

    @Test
    fun `next medal label tracks the next threshold`() {
        assertEquals("next bronze 35%", AudiosurfEngine.nextMedalLabel(10, 200, AudiosurfMode.NORMAL))
        assertEquals("next silver 70%", AudiosurfEngine.nextMedalLabel(100, 200, AudiosurfMode.NORMAL))
        assertEquals("next gold 94%", AudiosurfEngine.nextMedalLabel(150, 200, AudiosurfMode.NORMAL))
        assertEquals("next gold 98%", AudiosurfEngine.nextMedalLabel(150, 200, AudiosurfMode.HARD))
        assertEquals("gold secured", AudiosurfEngine.nextMedalLabel(190, 200, AudiosurfMode.NORMAL))
        assertNull(AudiosurfEngine.nextMedalLabel(10, 200, AudiosurfMode.VISUALIZER))
    }

    /* ------------------------------------------------------------ */
    /*                   steering, tilt and persistence               */
    /* ------------------------------------------------------------ */

    @Test
    fun `steering clamps to three lanes and touch maps thirds`() {
        var state = ride(testCourse())
        state = AudiosurfEngine.steer(state, -1)
        assertEquals(0, state.lane)
        state = AudiosurfEngine.steer(state, -1)
        assertEquals(0, state.lane)
        state = AudiosurfEngine.steer(state, 1)
        assertEquals(1, state.lane)
        state = AudiosurfEngine.setLane(state, 9)
        assertEquals(2, state.lane)
        assertEquals(0, AudiosurfEngine.absoluteTouchLane(-0.5f))
        assertEquals(0, AudiosurfEngine.absoluteTouchLane(0.1f))
        assertEquals(1, AudiosurfEngine.absoluteTouchLane(0.5f))
        assertEquals(2, AudiosurfEngine.absoluteTouchLane(0.9f))
        assertEquals(2, AudiosurfEngine.absoluteTouchLane(1.5f))
    }

    @Test
    fun `lane position eases toward the selected lane`() {
        var state = AudiosurfEngine.steer(ride(testCourse()), -1)
        assertEquals(0, state.lane)
        assertEquals(1f, state.lanePosition, 1e-6f)
        state = AudiosurfEngine.step(state, 1_000)
        assertEquals(0f, state.lanePosition, 1e-6f)
    }

    @Test
    fun `tilt dead zone follows the sensitivity control`() {
        assertEquals(0.35, AudiosurfEngine.tiltDeadZone(0.0), 1e-9)
        assertEquals(0.05, AudiosurfEngine.tiltDeadZone(1.0), 1e-9)
        assertEquals(0, AudiosurfEngine.tiltDirection(2.0, 1.0))
        assertEquals(1, AudiosurfEngine.tiltDirection(10.0, 1.0))
        assertEquals(-1, AudiosurfEngine.tiltDirection(-10.0, 1.0))
        assertTrue(AudiosurfEngine.tiltMagnitude(10.0, 1.0) > 0.0)
        assertEquals(0.0, AudiosurfEngine.tiltMagnitude(1.0, 1.0), 1e-9)
    }

    @Test
    fun `a started ride can be encoded and decoded for resume`() {
        val course = AudiosurfEngine.buildCourse(profile, AudiosurfMode.HARD, seed = 3)
        var state = AudiosurfEngine.step(AudiosurfEngine.startRide(ride(course)), 5_000)
        state = AudiosurfEngine.setLane(state, 2)
        assertEquals(RideStatus.RIDING, state.status)
        val decoded = AudiosurfEngine.decode(AudiosurfEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(course, decoded!!.course)
        assertEquals(state.trackPositionMs, decoded.trackPositionMs)
        assertEquals(state.score, decoded.score)
        assertEquals(state.chain, decoded.chain)
        assertEquals(state.lane, decoded.lane)
        assertEquals(state.status, decoded.status)
        assertNull(AudiosurfEngine.decode("not a ride"))
        assertNull(AudiosurfEngine.decode("as1;bogus"))
    }
}
