package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BugTap
import com.heretek.dorado_hd.ui.apps.games.GOO_JELLY_RADIUS
import com.heretek.dorado_hd.ui.apps.games.GooEvent
import com.heretek.dorado_hd.ui.apps.games.GooSplatEngine
import com.heretek.dorado_hd.ui.apps.games.GooSplatRandom
import com.heretek.dorado_hd.ui.apps.games.GooSplatState
import com.heretek.dorado_hd.ui.apps.games.Jelly
import com.heretek.dorado_hd.ui.apps.games.JellyKind
import com.heretek.dorado_hd.ui.apps.games.JellyState
import com.heretek.dorado_hd.ui.apps.games.Pepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GooSplatTest {

    private fun jelly(
        kind: JellyKind,
        x: Float,
        y: Float,
        state: JellyState = JellyState.SLIDE,
        id: Int = 1,
        speed: Float = 30f,
        vx: Float = 0f,
        vy: Float = 0f,
        frozen: Boolean = false,
        falling: Boolean = false,
    ): Jelly = Jelly(
        id = id,
        kind = kind,
        x = x,
        y = y,
        vy = vy,
        vx = vx,
        speed = speed,
        state = state,
        frozen = frozen,
        falling = falling,
    )

    private fun stateWith(
        vararg jellies: Jelly,
        score: Int = 0,
        sludgeLevel: Float = 370f,
        sludgeTarget: Float = 370f,
        pepper: Pepper? = null,
    ): GooSplatState = GooSplatState(
        level = 1,
        score = score,
        scoreTarget = 5,
        targetStep = 5,
        sludgeLevel = sludgeLevel,
        sludgeTarget = sludgeTarget,
        spawnClockMs = 0L,
        elapsedMs = 0L,
        jellies = jellies.toList(),
        pepper = pepper,
        nextId = (jellies.maxOfOrNull { it.id } ?: 0) + 1,
        seed = 1234,
        breakdown = com.heretek.dorado_hd.ui.apps.games.GooBreakdown(),
    )

    @Test
    fun `spawn batch bands and tick length follow the level`() {
        assertEquals(3, GooSplatEngine.batchMax(1))
        assertEquals(3, GooSplatEngine.batchMax(3))
        assertEquals(4, GooSplatEngine.batchMax(4))
        assertEquals(4, GooSplatEngine.batchMax(8))
        assertEquals(5, GooSplatEngine.batchMax(9))
        assertEquals(1_250L, GooSplatEngine.spawnTickMs(1))
        assertEquals(1_235L, GooSplatEngine.spawnTickMs(2))
        assertEquals(1_130L, GooSplatEngine.spawnTickMs(9))
    }

    @Test
    fun `absorbing a small green lowers the target by radius over one point seven five`() {
        assertEquals(370f - GOO_JELLY_RADIUS / 1.75f, GooSplatEngine.absorbTarget(370f), 0.001f)
        val submerged = jelly(JellyKind.SMALL_GREEN, 100f, 271f, JellyState.SLIDE)
        val state = stateWith(submerged, sludgeLevel = 300f, sludgeTarget = 300f)
        val next = GooSplatEngine.step(state, 33L)
        assertTrue(next.jellies.isEmpty())
        assertEquals(300f - GOO_JELLY_RADIUS / 1.75f, next.sludgeTarget, 0.001f)
    }

    @Test
    fun `an absorbed power up does not lower the sludge target`() {
        val submerged = jelly(JellyKind.BOMB, 100f, 271f, JellyState.SLIDE)
        val state = stateWith(submerged, sludgeLevel = 300f, sludgeTarget = 300f)
        val next = GooSplatEngine.step(state, 33L)
        assertTrue(next.jellies.isEmpty())
        assertEquals(300f, next.sludgeTarget, 0.001f)
    }

    @Test
    fun `falling doubles the score of a caught jelly`() {
        val falling = jelly(JellyKind.SMALL_GREEN, 100f, 100f, JellyState.DESCENDING, falling = true)
        val boosted = GooSplatEngine.step(stateWith(falling), 33L, listOf(BugTap(100f, 100f)))
        assertEquals(2, boosted.score)

        val grounded = jelly(JellyKind.SMALL_GREEN, 100f, 100f, JellyState.SLIDE, id = 2)
        val normal = GooSplatEngine.step(stateWith(grounded), 33L, listOf(BugTap(100f, 100f)))
        assertEquals(1, normal.score)
    }

    @Test
    fun `freezing then tapping a frozen jelly twice shatters it`() {
        val green = jelly(JellyKind.SMALL_GREEN, 100f, 200f, JellyState.SLIDE, id = 1)
        val ice = jelly(JellyKind.ICE, 180f, 200f, JellyState.SLIDE, id = 2)
        var state = GooSplatEngine.step(stateWith(green, ice), 33L, listOf(BugTap(180f, 200f)))
        assertEquals(3, state.score)
        assertTrue(state.jellies.first { it.kind == JellyKind.SMALL_GREEN }.frozen)

        state = GooSplatEngine.step(state, 33L, listOf(BugTap(100f, 200f)))
        assertTrue(state.jellies.first { it.kind == JellyKind.SMALL_GREEN }.cracked)
        assertEquals(3, state.score)

        state = GooSplatEngine.step(state, 33L, listOf(BugTap(100f, 200f)))
        assertEquals(4, state.score)
        val shattered = state.jellies.first { it.kind == JellyKind.SMALL_GREEN }
        assertEquals(JellyState.SQUISHED, shattered.state)
        assertTrue(state.events.contains(GooEvent.ShatterFrozen))
    }

    @Test
    fun `a bomb blast shatters frozen jellies and scores their value`() {
        val frozen = jelly(JellyKind.SMALL_GREEN, 100f, 200f, JellyState.SLIDE, id = 1, frozen = true)
        val bomb = jelly(JellyKind.BOMB, 150f, 200f, JellyState.SLIDE, id = 2)
        val state = GooSplatEngine.step(stateWith(frozen, bomb), 33L, listOf(BugTap(150f, 200f)))
        assertEquals(4, state.score)
        assertTrue(state.jellies.isEmpty())
        assertTrue(state.events.contains(GooEvent.Bomb))
    }

    @Test
    fun `two small greens merge into a large green`() {
        val a = jelly(JellyKind.SMALL_GREEN, 100f, 300f, JellyState.SLIDE, id = 1, speed = 30f)
        val b = jelly(JellyKind.SMALL_GREEN, 125f, 300f, JellyState.SLIDE, id = 2, speed = 38f)
        val state = GooSplatEngine.step(stateWith(a, b), 33L)
        val merged = state.jellies.single()
        assertEquals(JellyKind.LARGE_GREEN, merged.kind)
        assertEquals(5, merged.value)
        assertEquals(34f * 1.5f, merged.speed, 0.01f)
        assertEquals(JellyState.STALL, merged.state)
        assertEquals(50L, merged.stallOverrideMs)
        assertTrue(state.events.contains(GooEvent.Merge))
    }

    @Test
    fun `nearby small greens are attracted at two hundred pixels per second`() {
        val a = jelly(JellyKind.SMALL_GREEN, 100f, 300f, JellyState.SLIDE, id = 1)
        val b = jelly(JellyKind.SMALL_GREEN, 155f, 300f, JellyState.SLIDE, id = 2)
        val state = GooSplatEngine.step(stateWith(a, b), 33L)
        assertEquals(2, state.jellies.size)
        val moved = state.jellies.sortedBy { it.id }
        val gap = moved[1].x - moved[0].x
        assertTrue("gap $gap should close from 55", gap < 55f)
        assertTrue(gap > GOO_JELLY_RADIUS)
    }

    @Test
    fun `pepper adds fifty to the sludge target and clears the board`() {
        val green = jelly(JellyKind.SMALL_GREEN, 50f, 300f, JellyState.SLIDE)
        val state = stateWith(green, sludgeTarget = 370f, pepper = Pepper(100f, 200f, 0f))
        val next = GooSplatEngine.step(state, 33L, listOf(BugTap(100f, 200f)))
        assertEquals(420f, next.sludgeTarget, 0.001f)
        assertEquals(4, next.score)
        assertTrue(next.jellies.isEmpty())
        assertNull(next.pepper)
        assertEquals(1, next.breakdown.peppers)
    }

    @Test
    fun `the run ends when the sludge climbs above level seventy`() {
        val state = stateWith(sludgeLevel = 70.5f, sludgeTarget = 60f)
        val next = GooSplatEngine.step(state, 1_000L)
        assertEquals(60f, next.sludgeLevel, 0.001f)
        assertTrue(next.over)
        assertTrue(next.events.contains(GooEvent.GameOver))
    }

    @Test
    fun `the first four score targets are five fourteen twenty nine fifty two`() {
        var state = GooSplatEngine.newGame(3)
        assertEquals(5, state.scoreTarget)
        state = state.copy(score = 5)
        state = GooSplatEngine.step(state, 1L)
        assertEquals(2, state.level)
        assertEquals(14, state.scoreTarget)
        state = state.copy(score = 14)
        state = GooSplatEngine.step(state, 1L)
        assertEquals(3, state.level)
        assertEquals(29, state.scoreTarget)
        state = state.copy(score = 29)
        state = GooSplatEngine.step(state, 1L)
        assertEquals(4, state.level)
        assertEquals(52, state.scoreTarget)
    }

    @Test
    fun `electricity drops the other jellies into a falling state`() {
        val green = jelly(JellyKind.SMALL_GREEN, 100f, 200f, JellyState.SLIDE, id = 1)
        val electric = jelly(JellyKind.ELECTRIC, 180f, 200f, JellyState.SLIDE, id = 2)
        val other = jelly(JellyKind.SMALL_GREEN, 60f, 200f, JellyState.SLIDE, id = 3)
        val state = GooSplatEngine.step(stateWith(green, electric, other), 33L, listOf(BugTap(180f, 200f)))
        assertEquals(3, state.score)
        assertTrue(state.jellies.filter { it.id != 2 }.all { it.falling })
        assertTrue(state.events.contains(GooEvent.Shock))
    }

    @Test
    fun `frozen jellies thaw after five seconds`() {
        val frozen = jelly(JellyKind.SMALL_GREEN, 100f, 200f, JellyState.SLIDE, id = 1, frozen = true)
        var state = GooSplatEngine.step(stateWith(frozen), 4_900L)
        assertTrue(state.jellies.first { it.id == 1 }.frozen)
        state = GooSplatEngine.step(state, 200L)
        assertFalse(state.jellies.first { it.id == 1 }.frozen)
    }

    @Test
    fun `small and falling jellies get a ten pixel tap pad`() {
        val small = jelly(JellyKind.SMALL_GREEN, 100f, 100f)
        assertTrue(small.contains(135f, 100f))
        val large = jelly(JellyKind.LARGE_GREEN, 100f, 100f)
        assertFalse(large.contains(135f, 100f))
        val falling = jelly(JellyKind.LARGE_GREEN, 100f, 100f, JellyState.DESCENDING, falling = true)
        assertTrue(falling.contains(135f, 100f))
    }

    @Test
    fun `deterministic step under a fixed seed`() {
        var a = GooSplatEngine.newGame(42)
        var b = GooSplatEngine.newGame(42)
        repeat(300) {
            a = GooSplatEngine.step(a, 33L)
            b = GooSplatEngine.step(b, 33L)
        }
        assertEquals(a, b)
    }

    @Test
    fun `spawn positions stay inside the spec band`() {
        val rng = GooSplatRandom(7)
        repeat(300) {
            val position = GooSplatEngine.rollSpawnPosition(emptyList(), 370f, rng)
            assertNotNull(position)
            assertTrue(position!!.first in 35f..237f)
            assertTrue(position.second in -20f..330f)
        }
    }

    @Test
    fun `a crowded band rejects the spawn after sixty tries`() {
        val wall = mutableListOf<Jelly>()
        var id = 1
        for (x in 35..237 step 30) {
            for (y in -20..330 step 30) {
                wall += jelly(JellyKind.SMALL_GREEN, x.toFloat(), y.toFloat(), JellyState.SLIDE, id = id++)
            }
        }
        assertNull(GooSplatEngine.rollSpawnPosition(wall, 370f, GooSplatRandom(5)))
    }

    @Test
    fun `encode decode round trips a live run`() {
        var state = GooSplatEngine.newGame(77)
        repeat(120) { state = GooSplatEngine.step(state, 33L) }
        val decoded = GooSplatEngine.decode(GooSplatEngine.encode(state))
        assertNotNull(decoded)
        decoded!!
        assertEquals(state.level, decoded.level)
        assertEquals(state.score, decoded.score)
        assertEquals(state.scoreTarget, decoded.scoreTarget)
        assertEquals(state.sludgeLevel, decoded.sludgeLevel, 0.001f)
        assertEquals(state.sludgeTarget, decoded.sludgeTarget, 0.001f)
        assertEquals(state.seed, decoded.seed)
        assertEquals(state.jellies.size, decoded.jellies.size)
        assertNull(GooSplatEngine.decode("junk"))
    }
}
