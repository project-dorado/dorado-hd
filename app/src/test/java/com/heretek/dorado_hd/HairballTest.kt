package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_BLOCK_H
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_BLOCK_POOL
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_FALL
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_LEFT_START_X
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_MAX_X
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_MIN_X
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_PLAYER
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_RIGHT_START_X
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_ROW_BASE_Y
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_SPEED_BASE
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_SPEED_CAP
import com.heretek.dorado_hd.ui.apps.games.HAIRBALL_START_X
import com.heretek.dorado_hd.ui.apps.games.HairballEngine
import com.heretek.dorado_hd.ui.apps.games.HairballEvent
import com.heretek.dorado_hd.ui.apps.games.HairballRandom
import com.heretek.dorado_hd.ui.apps.games.HairballRow
import com.heretek.dorado_hd.ui.apps.games.HairballState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HairballTest {

    private fun frame(): Long = 33L

    @Test
    fun `speed ramps every twelve seconds and caps at fifteen`() {
        assertEquals(HAIRBALL_SPEED_BASE, HairballEngine.speedFor(0L), 0.0001f)
        assertEquals(HAIRBALL_SPEED_BASE, HairballEngine.speedFor(11_999L), 0.0001f)
        assertEquals(3.8f, HairballEngine.speedFor(12_000L), 0.0001f)
        assertEquals(4.1f, HairballEngine.speedFor(24_000L), 0.0001f)
        assertEquals(6.5f, HairballEngine.speedFor(120_000L), 0.0001f)
        assertEquals(HAIRBALL_SPEED_CAP, HairballEngine.speedFor(10_000_000L), 0.0001f)
    }

    @Test
    fun `sludge pool tops out at forty blocks on a six by five grid`() {
        var state = HairballEngine.newGame(7)
        repeat(200) { state = HairballEngine.step(state, frame()) }
        val rows = state.rows
        assertTrue("at most five rows", rows.size <= 5)
        assertTrue("at most six slots per row", rows.all { it.slots.size <= 6 })
        assertTrue("at most five placed per row", rows.all { it.slots.size <= 5 })
        assertTrue("slots in range", rows.all { row -> row.slots.all { it in 0..5 } })
        assertTrue("slots unique", rows.all { row -> row.slots.distinct().size == row.slots.size })
        assertTrue("block pool respected", HairballEngine.blockCount(state) <= HAIRBALL_BLOCK_POOL)
        assertTrue("a row always exists", rows.isNotEmpty())
    }

    @Test
    fun `new game starts at the spec spawn point and a 400 line row`() {
        val state = HairballEngine.newGame(3)
        assertEquals(HAIRBALL_START_X, state.playerX, 0.0001f)
        assertEquals(40f, state.playerY, 0.0001f)
        assertEquals(0, state.score)
        assertEquals(1, state.rows.size)
        val first = state.rows.first()
        assertEquals(HAIRBALL_ROW_BASE_Y, first.y, 0.0001f)
        assertTrue(first.slots.size <= 5)
        assertEquals(HAIRBALL_SPEED_BASE, state.speed, 0.0001f)
    }

    @Test
    fun `row slots alternate placement direction at the edges`() {
        val left = HairballRow(400f, listOf(0), leftToRight = true)
        val right = HairballRow(400f, listOf(0), leftToRight = false)
        assertEquals(HAIRBALL_LEFT_START_X, HairballEngine.slotX(left, 0), 0.0001f)
        assertEquals(HAIRBALL_LEFT_START_X + 42f, HairballEngine.slotX(left, 1), 0.0001f)
        assertEquals(HAIRBALL_RIGHT_START_X, HairballEngine.slotX(right, 0), 0.0001f)
        assertEquals(HAIRBALL_RIGHT_START_X - 42f, HairballEngine.slotX(right, 1), 0.0001f)
    }

    @Test
    fun `seeded roll skips slots at about one in six`() {
        val rng = HairballRandom(1234)
        var skipped = 0
        repeat(6_000) { if (rng.next(6) == 1) skipped++ }
        val ratio = skipped / 6_000f
        assertTrue("skip ratio $ratio should sit near 1/6", ratio > 0.13f && ratio < 0.20f)

        // The row roller never places more than five and never scans past six.
        var seed = 99
        repeat(500) {
            val (slots, next) = HairballEngine.rollSlots(seed)
            seed = next
            assertTrue(slots.size <= 5)
            assertTrue(slots.all { it in 0..5 })
            assertEquals(slots.size, slots.distinct().size)
        }
    }

    @Test
    fun `landing snaps the hairball to the block top and pushes it up`() {
        val row = HairballRow(100f, listOf(0), leftToRight = true)
        val state = HairballState(
            playerX = 40f,
            playerY = 85f,
            rows = listOf(row),
            speed = HAIRBALL_SPEED_BASE,
            elapsedMs = 0L,
            score = 0,
            seed = 1,
            nextRowLeftToRight = false,
        )
        val next = HairballEngine.step(state, 100L)
        val frames = 100f / com.heretek.dorado_hd.ui.apps.games.HAIRBALL_FRAME_MS
        val movedRowY = 100f - HairballEngine.speedFor(100L) * frames
        assertEquals(movedRowY - HAIRBALL_PLAYER, next.playerY, 0.01f)
        assertTrue("pushed upward", next.playerY < state.playerY)
        assertTrue(next.events.contains(HairballEvent.Landed))
    }

    @Test
    fun `falling off the top of the screen ends the run`() {
        val state = HairballState(
            playerX = 136f,
            playerY = -34f,
            rows = emptyList(),
            speed = HAIRBALL_SPEED_BASE,
            elapsedMs = 0L,
            score = 12,
            seed = 1,
            nextRowLeftToRight = false,
        )
        val next = HairballEngine.step(state, frame())
        assertTrue(next.lost)
        assertTrue(next.events.contains(HairballEvent.Lost))

        val frozen = HairballEngine.step(next, frame())
        assertEquals(next.score, frozen.score)
        assertTrue(frozen.events.isEmpty())
    }

    @Test
    fun `score ticks once per frame and pauses hold it still`() {
        var state = HairballEngine.newGame(5)
        repeat(4) { state = HairballEngine.step(state, frame()) }
        assertEquals(4, state.score)
        val paused = HairballEngine.step(state.copy(paused = true), frame())
        assertEquals(4, paused.score)
        val resumed = HairballEngine.step(paused.copy(paused = false), frame())
        assertEquals(5, resumed.score)
    }

    @Test
    fun `tilt deadzone ignores noise and full tilt clamps at the walls`() {
        assertEquals(0f, HairballEngine.tiltVelocity(0.04f), 0.0001f)
        assertEquals(0f, HairballEngine.tiltVelocity(-0.04f), 0.0001f)
        assertEquals(10f, HairballEngine.tiltVelocity(0.5f), 0.0001f)
        assertEquals(-10f, HairballEngine.tiltVelocity(-0.5f), 0.0001f)
        assertEquals(13f, HairballEngine.tiltVelocity(1f), 0.0001f)

        var right = HairballEngine.newGame(5).copy(playerY = 40f)
        repeat(80) { right = HairballEngine.step(right, frame(), tiltX = 1f) }
        assertEquals(HAIRBALL_MAX_X, right.playerX, 0.0001f)

        var left = HairballEngine.newGame(5).copy(playerY = 40f)
        repeat(80) { left = HairballEngine.step(left, frame(), tiltX = -1f) }
        assertEquals(HAIRBALL_MIN_X, left.playerX, 0.0001f)
    }

    @Test
    fun `player always falls twelve point eight pixels per frame`() {
        val state = HairballEngine.newGame(11).copy(playerY = 40f)
        val next = HairballEngine.step(state, 100L)
        val frames = 100f / com.heretek.dorado_hd.ui.apps.games.HAIRBALL_FRAME_MS
        // No collision on the first step: the hairball drops the fixed fall step.
        assertEquals(40f + HAIRBALL_FALL * frames, next.playerY, 0.01f)
    }

    @Test
    fun `rows recycle above the top and a fresh row is queued below`() {
        val state = HairballState(
            playerX = 20f,
            playerY = 400f,
            rows = listOf(
                HairballRow(-35f, listOf(0), leftToRight = true),
                HairballRow(700f, listOf(5), leftToRight = false),
            ),
            speed = HAIRBALL_SPEED_BASE,
            elapsedMs = 0L,
            score = 0,
            seed = 5,
            nextRowLeftToRight = true,
        )
        val next = HairballEngine.step(state, 100L)
        assertTrue("top row recycled", next.rows.none { it.y <= -HAIRBALL_BLOCK_H })
        assertTrue("fresh row queued below the deepest", next.rows.any { it.y > 720f })
        assertTrue("never more than five rows", next.rows.size <= 5)
    }

    @Test
    fun `encode decode round trips a run`() {
        var state = HairballEngine.newGame(21)
        repeat(30) { state = HairballEngine.step(state, frame(), tiltX = 0.3f) }
        val decoded = HairballEngine.decode(HairballEngine.encode(state))
        assertNotNull(decoded)
        decoded!!
        assertEquals(state.playerX, decoded.playerX, 0.0001f)
        assertEquals(state.playerY, decoded.playerY, 0.0001f)
        assertEquals(state.score, decoded.score)
        assertEquals(state.elapsedMs, decoded.elapsedMs)
        assertEquals(state.seed, decoded.seed)
        assertEquals(state.rows.size, decoded.rows.size)
        assertEquals(state.rows.first().slots, decoded.rows.first().slots)
        assertEquals(state.rows.first().leftToRight, decoded.rows.first().leftToRight)
        assertNull(HairballEngine.decode("nope"))
    }

    @Test
    fun `a fresh run after a loss steps again`() {
        val over = HairballEngine.step(
            HairballState(
                playerX = 136f,
                playerY = -34f,
                rows = emptyList(),
                speed = HAIRBALL_SPEED_BASE,
                elapsedMs = 0L,
                score = 12,
                seed = 1,
                nextRowLeftToRight = false,
            ),
            frame(),
        )
        assertTrue(over.lost)
        val frozen = HairballEngine.step(over, frame())
        assertEquals(over.score, frozen.score)

        val fresh = HairballEngine.newGame(2)
        val next = HairballEngine.step(fresh, frame())
        assertFalse(next.lost)
        assertEquals(1, next.score)
    }
}
