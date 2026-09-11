package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.games.GrindKind
import com.heretek.dorado_hd.ui.apps.games.SkateCareer
import com.heretek.dorado_hd.ui.apps.games.SkateContent
import com.heretek.dorado_hd.ui.apps.games.SkateEngine
import com.heretek.dorado_hd.ui.apps.games.SkateEventId
import com.heretek.dorado_hd.ui.apps.games.SkatePhase
import com.heretek.dorado_hd.ui.apps.games.SkateResult
import com.heretek.dorado_hd.ui.apps.games.SkateState
import com.heretek.dorado_hd.ui.apps.games.SkateSwipe
import com.heretek.dorado_hd.ui.apps.games.SkateTricks
import com.heretek.dorado_hd.ui.apps.games.bowlMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the clean-room pool-skate engine. */
class SkateTest {

    private fun session(event: SkateEventId = SkateEventId.FREE_RIDE, seed: Int = 7): SkateState =
        SkateEngine.newSession(
            event = event,
            poolId = "sundeck",
            skaterId = "rex",
            boardId = "board1",
            wheelId = "wheel1",
            seed = seed,
        )

    private fun airborne(state: SkateState, vertical: Float = 3f, planar: Float = 1f): SkateState {
        val surface = SkateEngine.surfaceHeight(state.pool, state.position.x, state.position.z)
        return state.copy(
            phase = SkatePhase.IN_AIR,
            position = Vec3(state.position.x, surface + 0.4f, state.position.z),
            direction = Vec3(0f, 0f, -1f),
            boardFacing = Vec3(0f, 0f, -1f),
            airVertical = vertical,
            airPlanar = planar,
            speed = 0f,
        )
    }

    private fun resolvedAirborne(vararg swipes: SkateSwipe): SkateState {
        var state = airborne(session())
        swipes.forEach { state = SkateEngine.swipe(state, it) }
        return SkateEngine.advance(state, 20)
    }

    @Test
    fun `bowl surface is lowest at the centre and flat past the lip`() {
        val pool = SkateContent.POOLS[0]
        assertEquals(pool.lip - pool.depth, SkateEngine.surfaceHeight(pool, 0f, 0f), 1e-4f)
        assertEquals(pool.lip, SkateEngine.surfaceHeight(pool, pool.radius * 1.5f, 0f), 1e-4f)
        val quarter = SkateEngine.surfaceHeight(pool, pool.radius * 0.5f, 0f)
        assertTrue(quarter > pool.lip - pool.depth)
        assertTrue(quarter < pool.lip)
    }

    @Test
    fun `surface normals point up on the deck and inward on the wall`() {
        val pool = SkateContent.POOLS[0]
        val deck = SkateEngine.surfaceNormal(pool, pool.radius * 2f, 0f)
        assertEquals(1f, deck.y, 1e-5f)
        val wall = SkateEngine.surfaceNormal(pool, 2f, 0f)
        assertTrue(wall.y > 0f)
        assertTrue(wall.x < 0f)
        assertEquals(1f, wall.length(), 1e-4f)
    }

    @Test
    fun `gravity along the slope points downhill and never exceeds g`() {
        val pool = SkateContent.POOLS[0]
        val wall = SkateEngine.surfaceNormal(pool, 2f, 0f)
        val acceleration = SkateEngine.slopeAcceleration(wall)
        assertTrue(acceleration.x < 0f)
        assertTrue(acceleration.y < 0f)
        assertTrue(acceleration.length() <= SkateEngine.GRAVITY + 1e-4f)
        assertTrue(acceleration.length() > 0f)
    }

    @Test
    fun `uphill travel loses more speed than flat travel`() {
        val pool = SkateContent.POOLS[0]
        val deck = session().copy(
            position = Vec3(pool.radius + 1f, pool.lip, 0f),
            direction = Vec3(1f, 0f, 0f),
            boardFacing = Vec3(1f, 0f, 0f),
            speed = 8f,
        )
        val wall = session().copy(
            position = Vec3(2f, 0f, 0f),
            direction = Vec3(1f, 0f, 0f),
            boardFacing = Vec3(1f, 0f, 0f),
            speed = 8f,
        )
        val flatRun = SkateEngine.advance(deck, 10)
        val uphillRun = SkateEngine.advance(wall, 10)
        assertTrue(uphillRun.speed < flatRun.speed)
    }

    @Test
    fun `ramp launch splits speed into vertical and planar parts`() {
        val (vertical, planar) = SkateEngine.rampLaunch(10f, Vec3(0.6f, 0.8f, 0f))
        assertEquals(8f, vertical, 1e-4f)
        assertEquals(6f, planar, 1e-4f)
    }

    @Test
    fun `riding over the lip launches the skater`() {
        val pool = SkateContent.POOLS[0]
        val onLip = session().copy(
            position = Vec3(pool.radius - 0.05f, SkateEngine.surfaceHeight(pool, pool.radius - 0.05f, 0f), 0f),
            direction = Vec3(1f, 0f, 0f),
            boardFacing = Vec3(1f, 0f, 0f),
            speed = 9f,
        )
        val next = SkateEngine.advance(onLip, 1)
        assertEquals(SkatePhase.IN_AIR, next.phase)
        assertTrue(next.airVertical > 0f)
        assertTrue(next.airPlanar > 0f)
    }

    @Test
    fun `airtime integrates gravity and returns to the surface`() {
        val start = airborne(session(), vertical = 2f, planar = 0f)
        var state = start
        var frames = 0
        while (state.phase == SkatePhase.IN_AIR && frames < 200) {
            state = SkateEngine.advance(state, 1)
            frames++
        }
        assertEquals(SkatePhase.IN_POOL, state.phase)
        assertTrue("airtime was $frames frames", frames in 25..60)
        val surface = SkateEngine.surfaceHeight(state.pool, state.position.x, state.position.z)
        assertEquals(surface, state.position.y, 1e-3f)
    }

    @Test
    fun `grind needs the minimum speed and a coping`() {
        val pool = SkateContent.POOLS[0]
        val nearCoping = session().copy(
            phase = SkatePhase.IN_POOL,
            position = Vec3(pool.radius, pool.lip, 0f),
            direction = Vec3(0f, 0f, 1f),
            speed = 4f,
        )
        assertFalse(SkateEngine.canStartGrind(nearCoping))
        assertTrue(SkateEngine.canStartGrind(nearCoping.copy(speed = 7f)))
        val farAway = nearCoping.copy(position = Vec3(0f, pool.lip - pool.depth, 0f), speed = 9f)
        assertFalse(SkateEngine.canStartGrind(farAway))
    }

    @Test
    fun `grind start and stop preserve the ride speed`() {
        val pool = SkateContent.POOLS[0]
        val fast = session().copy(
            phase = SkatePhase.IN_POOL,
            position = Vec3(pool.radius, pool.lip, 0.05f),
            direction = Vec3(0f, 0f, 1f),
            speed = 7f,
        )
        val grinding = SkateEngine.startGrind(fast)
        assertEquals(SkatePhase.GRINDING, grinding.phase)
        assertEquals(7f, grinding.speed, 1e-4f)
        assertEquals(GrindKind.FIFTY_FIFTY, grinding.grindKind)
        val released = SkateEngine.endGrind(grinding)
        assertEquals(SkatePhase.IN_POOL, released.phase)
        assertTrue(released.speed >= SkateEngine.MIN_GRIND_SPEED)
        assertEquals(1, released.grindCount)
    }

    @Test
    fun `grind balance drifts and tilt accelerates it`() {
        val pool = SkateContent.POOLS[0]
        val fast = session().copy(
            phase = SkatePhase.IN_POOL,
            position = Vec3(pool.radius, pool.lip, 0.05f),
            direction = Vec3(0f, 0f, 1f),
            speed = 7f,
        )
        var state = SkateEngine.tilt(SkateEngine.startGrind(fast), 1f, 0f)
        val before = state.balanceSpeed
        state = SkateEngine.advance(state, 1)
        assertTrue(state.balanceSpeed > before)
        assertTrue(state.controlSpeed > 0f)
    }

    @Test
    fun `grind balance past the limit bails`() {
        val pool = SkateContent.POOLS[0]
        val fast = session().copy(
            phase = SkatePhase.IN_POOL,
            position = Vec3(pool.radius, pool.lip, 0.05f),
            direction = Vec3(0f, 0f, 1f),
            speed = 7f,
        )
        var state = SkateEngine.startGrind(fast).copy(
            balancePoint = 39f,
            balanceSpeed = 200f,
            tiltedX = 1f,
        )
        state = SkateEngine.advance(state, 5)
        assertEquals(SkatePhase.CRASHING, state.phase)
        assertTrue(state.bailed)
    }

    @Test
    fun `landing during a trick bail window crashes`() {
        val state = resolvedAirborne(SkateSwipe.UP)
        assertTrue(state.combo.isNotEmpty())
        val landed = SkateEngine.advance(state.copy(airVertical = -3f), 30)
        assertEquals(SkatePhase.CRASHING, landed.phase)
        assertTrue(landed.bailed)
    }

    @Test
    fun `swipe patterns map to the re-authored trick table`() {
        val special = SkateContent.SKATERS[0].specialId
        fun trick(vararg swipes: SkateSwipe) = SkateEngine.detectTrick(swipes.toList(), special)
        assertEquals("japanAir", trick(SkateSwipe.UP)?.first?.id)
        assertEquals("staleFish", trick(SkateSwipe.DOWN)?.first?.id)
        assertEquals("indyGrab", trick(SkateSwipe.LEFT)?.first?.id)
        assertEquals("frontsideAir", trick(SkateSwipe.RIGHT)?.first?.id)
        assertEquals("judoAir", trick(SkateSwipe.UP, SkateSwipe.UP)?.first?.id)
        assertEquals("kickFlip", trick(SkateSwipe.LEFT, SkateSwipe.RIGHT)?.first?.id)
        assertEquals("calderPunch", trick(SkateSwipe.UP, SkateSwipe.RIGHT)?.first?.id)
        assertEquals(2, trick(SkateSwipe.UP, SkateSwipe.DOWN)?.second)
        assertNull(trick())
    }

    @Test
    fun `trick point table matches the mined values`() {
        assertEquals(1200, SkateTricks.points("airWalk"))
        assertEquals(1000, SkateTricks.points("staleFish"))
        assertEquals(1300, SkateTricks.points("backsideAir"))
        assertEquals(1500, SkateTricks.points("christAir"))
        assertEquals(1600, SkateTricks.points("japanAir"))
        assertEquals(1400, SkateTricks.points("backFlip"))
        assertEquals(1700, SkateTricks.points("slobAir"))
        assertEquals(1800, SkateTricks.points("calderPunch"))
        assertEquals(0, SkateTricks.points("fiftyFifty"))
    }

    @Test
    fun `fast swipe pairs chain and lone swipes resolve after the window`() {
        var pair = airborne(session())
        pair = SkateEngine.swipe(pair, SkateSwipe.UP)
        pair = SkateEngine.swipe(pair, SkateSwipe.UP)
        assertEquals(1, pair.combo.size)
        assertEquals("judoAir", pair.combo.last().id)

        var single = airborne(session())
        single = SkateEngine.swipe(single, SkateSwipe.UP)
        assertTrue(single.combo.isEmpty())
        single = SkateEngine.advance(single, 20)
        assertEquals("japanAir", single.combo.last().id)
    }

    @Test
    fun `combo scoring pays trick points times the multiplier`() {
        var state = airborne(session(), vertical = 6f)
        state = SkateEngine.swipe(state, SkateSwipe.UP)
        state = SkateEngine.advance(state, 20)
        state = SkateEngine.swipe(state, SkateSwipe.DOWN)
        state = SkateEngine.advance(state, 20)
        assertEquals(listOf("japanAir", "staleFish"), state.combo.map { it.id })
        state = state.copy(lastTrickMs = state.elapsedMs - 99999f, airVertical = -3f)
        state = SkateEngine.advance(state, 40)
        assertEquals(SkatePhase.IN_POOL, state.phase)
        assertEquals((1600 + 1000) * state.multiplier, state.score)
    }

    @Test
    fun `rotation rounds to the nearest 180 degrees`() {
        assertEquals(0, SkateEngine.formatRotation(0f))
        assertEquals(0, SkateEngine.formatRotation(1.0f))
        assertEquals(180, SkateEngine.formatRotation(2.6f))
        assertEquals(360, SkateEngine.formatRotation(5.5f))
    }

    @Test
    fun `grind score pays ten points per hundred milliseconds`() {
        assertEquals(0, SkateEngine.grindPoints(99))
        assertEquals(10, SkateEngine.grindPoints(100))
        assertEquals(50, SkateEngine.grindPoints(500))
    }

    @Test
    fun `bailing resets the multiplier and the chain`() {
        var state = resolvedAirborne(SkateSwipe.UP).copy(multiplier = 4, score = 500)
        state = SkateEngine.advance(state.copy(airVertical = -3f), 30)
        assertEquals(SkatePhase.CRASHING, state.phase)
        assertEquals(1, state.multiplier)
        assertTrue(state.combo.isEmpty())
    }

    @Test
    fun `multiplier decay follows the event schedule`() {
        var best = session(SkateEventId.BEST_RUN).copy(multiplier = 3, multiplierTimerMs = 5f * 7000f + 1f)
        best = SkateEngine.advance(best, 1)
        assertEquals(2, best.multiplier)

        var jam = session(SkateEventId.POOL_JAM).copy(multiplier = 3, multiplierTimerMs = 0.8f * 7000f + 1f)
        jam = SkateEngine.advance(jam, 1)
        assertEquals(2, jam.multiplier)

        var grind = session(SkateEventId.THE_GRIND).copy(multiplier = 3, multiplierTimerMs = 2f * 7000f + 1f)
        grind = SkateEngine.advance(grind, 1)
        assertEquals(2, grind.multiplier)
    }

    @Test
    fun `reaching the score target finishes the run as a success`() {
        var state = session(SkateEventId.TIMED_RUN).copy(score = 25000, eventTimerMs = 500f)
        state = SkateEngine.advance(state, 1)
        assertEquals(SkatePhase.FINISHED, state.phase)
        assertEquals(SkateResult.SUCCESS, state.result)
    }

    @Test
    fun `running out of time finishes the run as a failure`() {
        var state = session(SkateEventId.TIMED_RUN).copy(score = 100, eventTimerMs = 1f)
        state = SkateEngine.advance(state, 3)
        assertEquals(SkatePhase.FINISHED, state.phase)
        assertEquals(SkateResult.FAILED, state.result)
    }

    @Test
    fun `no bails fails on the first bail`() {
        val state = SkateEngine.advance(session(SkateEventId.NO_BAILS).copy(bailed = true), 1)
        assertEquals(SkateResult.FAILED, state.result)
    }

    @Test
    fun `career stars gate events pools and gear`() {
        val base = SkateCareer()
        assertTrue(base.isEventUnlocked(SkateEventId.TIMED_RUN))
        assertFalse(base.isEventUnlocked(SkateEventId.POOL_CLEANER))
        assertFalse(base.isPoolUnlocked(SkateContent.pool("vapor")))
        assertFalse(base.isBoardUnlocked(SkateContent.BOARDS[4]))
        assertFalse(base.isWheelUnlocked(SkateContent.WHEELS[4]))

        val earned = base.applyResult(SkateEventId.TIMED_RUN, 45000)
        assertEquals(2, earned.starsFor(SkateEventId.TIMED_RUN))
        assertEquals(45000, earned.bestFor(SkateEventId.TIMED_RUN))
        assertTrue(earned.isEventUnlocked(SkateEventId.POOL_CLEANER))
        assertTrue(earned.isPoolUnlocked(SkateContent.pool("vapor")))
        assertTrue(earned.unlockedBoards().size > base.unlockedBoards().size)

        val deeper = earned.applyResult(SkateEventId.POOL_CLEANER, 30000)
        assertTrue(deeper.earnedAchievements().isNotEmpty())
        assertTrue(deeper.unlockedVideos().isEmpty())
    }

    @Test
    fun `career survives an encode decode round trip`() {
        val career = SkateCareer()
            .applyResult(SkateEventId.TIMED_RUN, 41000)
            .applyResult(SkateEventId.POOL_CLEANER, 26000)
            .withLoadout("vapor", "mila", "board3", "wheel2")
        val decoded = SkateCareer.decode(career.encode())
        assertEquals(career, decoded)
    }

    @Test
    fun `session survives an encode decode round trip`() {
        var state = session(SkateEventId.THE_GRIND).copy(score = 1234, multiplier = 3, spinTotalDeg = 540)
        state = SkateEngine.swipe(airborne(state), SkateSwipe.UP)
        state = SkateEngine.advance(state, 20)
        state = state.copy(grindKind = GrindKind.BOARD_SLIDE, grindMsTotal = 1200, grindCount = 2)
        val decoded = SkateEngine.decode(SkateEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(state, decoded)
    }

    @Test
    fun `seeded bag layout is reproducible and seed dependent`() {
        val pool = SkateContent.POOLS[1]
        assertEquals(SkateEngine.bagPositions(pool, 5), SkateEngine.bagPositions(pool, 5))
        assertNotEquals(SkateEngine.bagPositions(pool, 5), SkateEngine.bagPositions(pool, 6))
        assertEquals(pool.bags.size, SkateEngine.bagPositions(pool, 5).size)
    }

    @Test
    fun `identical seeds and inputs replay to the same state`() {
        fun run(seed: Int): String {
            var state = SkateEngine.newSession(
                event = SkateEventId.POOL_CLEANER,
                poolId = "vapor",
                skaterId = "mila",
                boardId = "board5",
                wheelId = "wheel3",
                seed = seed,
            )
            state = SkateEngine.push(state)
            state = SkateEngine.tilt(state, 0.4f, -0.2f)
            repeat(30) { state = SkateEngine.advance(state, 1) }
            state = SkateEngine.swipe(state, SkateSwipe.UP)
            repeat(30) { state = SkateEngine.advance(state, 1) }
            return SkateEngine.encode(state)
        }
        assertEquals(run(11), run(11))
    }

    @Test
    fun `gear props map onto the stat block`() {
        val stats = SkateEngine.statsFor(SkateContent.BOARDS[0], SkateContent.WHEELS[0])
        assertEquals(0.82f, stats.spin, 1e-4f)
        assertEquals(0.5f, stats.balance, 1e-4f)
        assertEquals(2.5f, stats.control, 1e-4f)
        assertEquals(0.3f, stats.landing, 1e-4f)
        assertEquals(1.19f, stats.speed, 1e-4f)

        val top = SkateEngine.statsFor(SkateContent.BOARDS.last(), SkateContent.WHEELS.last())
        assertEquals(1.39f, top.spin, 1e-4f)
        assertEquals(2f, top.balance, 1e-4f)
        assertEquals(0.75f, top.control, 1e-4f)
        assertEquals(2f, top.landing, 1e-4f)
        assertEquals(0.9f, top.speed, 1e-4f)
    }

    @Test
    fun `bowl mesh closes the rim and lies on the analytic surface`() {
        val pool = SkateContent.POOLS[0]
        val mesh = bowlMesh(pool)
        assertTrue(mesh.vertexCount > 0)
        assertTrue(mesh.indexCount > 0)
        val positions = mesh.positions
        var index = 0
        var maxY = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        while (index < positions.size) {
            val x = positions[index]
            val y = positions[index + 1]
            val z = positions[index + 2]
            assertEquals(SkateEngine.surfaceHeight(pool, x, z), y, 1e-4f)
            maxY = maxOf(maxY, y)
            minY = minOf(minY, y)
            index += 3
        }
        assertEquals(pool.lip, maxY, 1e-4f)
        assertEquals(pool.lip - pool.depth, minY, 1e-4f)
    }

    @Test
    fun `push sets the push speed once`() {
        val pushed = SkateEngine.push(session())
        assertEquals(SkateEngine.PUSH_SPEED, pushed.speed, 1e-4f)
    }
}
