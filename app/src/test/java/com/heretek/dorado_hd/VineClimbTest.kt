package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.VineClimbEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Vine Climb endless climber. */
class VineClimbTest {

    @Test
    fun `collectable rolls match the authored table`() {
        assertEquals(VineClimbEngine.CollectableKind.MANGO, VineClimbEngine.collectKindForRoll(0))
        assertEquals(VineClimbEngine.CollectableKind.MANGO, VineClimbEngine.collectKindForRoll(8))
        assertEquals(VineClimbEngine.CollectableKind.BERRIES, VineClimbEngine.collectKindForRoll(6))
        assertEquals(VineClimbEngine.CollectableKind.BERRIES, VineClimbEngine.collectKindForRoll(9))
        assertEquals(VineClimbEngine.CollectableKind.DRAGONFLY, VineClimbEngine.collectKindForRoll(5))
        assertEquals(VineClimbEngine.CollectableKind.DRAGONFLY, VineClimbEngine.collectKindForRoll(11))
        assertEquals(VineClimbEngine.CollectableKind.NUT, VineClimbEngine.collectKindForRoll(2))
        assertEquals(VineClimbEngine.CollectableKind.NUT, VineClimbEngine.collectKindForRoll(10))
    }

    @Test
    fun `collectable values match the spec`() {
        assertEquals(2000, VineClimbEngine.CollectableKind.MANGO.score)
        assertEquals(500, VineClimbEngine.CollectableKind.DRAGONFLY.score)
        assertEquals(250, VineClimbEngine.CollectableKind.BERRIES.score)
        assertEquals(250, VineClimbEngine.CollectableKind.NUT.score)
    }

    @Test
    fun `lanes sit at seventy plus fifty four per index`() {
        assertEquals(70, VineClimbEngine.laneX(0))
        assertEquals(124, VineClimbEngine.laneX(1))
        assertEquals(178, VineClimbEngine.laneX(2))
        assertEquals(232, VineClimbEngine.laneX(3))
        assertEquals(4, VineClimbEngine.LANES)
    }

    @Test
    fun `hazard rolls only arm the outer and inner lanes`() {
        assertEquals(VineClimbEngine.VineType.SPIKY, VineClimbEngine.typeForRoll(0, 0))
        assertEquals(VineClimbEngine.VineType.SPIKY, VineClimbEngine.typeForRoll(3, 19))
        assertEquals(VineClimbEngine.VineType.GREEN, VineClimbEngine.typeForRoll(0, 20))
        assertEquals(VineClimbEngine.VineType.GREEN, VineClimbEngine.typeForRoll(3, 199))
        assertEquals(VineClimbEngine.VineType.ELECTRIC, VineClimbEngine.typeForRoll(1, 0))
        assertEquals(VineClimbEngine.VineType.ELECTRIC, VineClimbEngine.typeForRoll(2, 19))
        assertEquals(VineClimbEngine.VineType.GREEN, VineClimbEngine.typeForRoll(1, 20))
        assertEquals(VineClimbEngine.VineType.GREEN, VineClimbEngine.typeForRoll(2, 199))
    }

    @Test
    fun `electric vines are live on phases zero and two only`() {
        assertTrue(VineClimbEngine.electricOn(0))
        assertFalse(VineClimbEngine.electricOn(1))
        assertTrue(VineClimbEngine.electricOn(2))
        assertFalse(VineClimbEngine.electricOn(3))
        assertFalse(VineClimbEngine.electricOn(99))
    }

    @Test
    fun `tutorial line fades out over the first fifty points`() {
        assertEquals(1f, VineClimbEngine.tutorialAlpha(0), 0.0001f)
        assertEquals(0.5f, VineClimbEngine.tutorialAlpha(25), 0.0001f)
        assertEquals(0f, VineClimbEngine.tutorialAlpha(50), 0.0001f)
        assertEquals(0f, VineClimbEngine.tutorialAlpha(80), 0.0001f)
    }

    @Test
    fun `a new run starts on lane one with four green vines`() {
        val state = VineClimbEngine.newGame()
        assertEquals(1, state.lane)
        assertTrue(state.inTutorial)
        assertTrue(state.vines.isNotEmpty())
        assertTrue(state.vines.all { it.type == VineClimbEngine.VineType.GREEN })
        assertEquals(VineClimbEngine.laneX(1), state.x)
    }

    @Test
    fun `fixed step consumes whole ticks at 60 hz`() {
        val state = VineClimbEngine.step(VineClimbEngine.newGame(), 100L)
        assertEquals(-15, state.gameY)
        assertEquals(5L, state.ticks)
    }

    @Test
    fun `the world holds still for the five tick death freeze then keeps scrolling`() {
        var state = VineClimbEngine.beginFall(VineClimbEngine.newGame())
        assertTrue(state.falling)
        val start = state.gameY
        repeat(VineClimbEngine.FREEZE_TICKS) { state = VineClimbEngine.tick(state) }
        assertEquals(start, state.gameY)
        state = VineClimbEngine.tick(state)
        assertTrue(state.gameY < start)
    }

    @Test
    fun `game over pushes after the hundred tick window`() {
        var state = VineClimbEngine.beginFall(VineClimbEngine.newGame())
        var ticks = 0
        while (!state.gameOver && ticks < 200) {
            state = VineClimbEngine.tick(state)
            ticks++
        }
        assertTrue(state.gameOver)
        assertTrue(state.gameOverShown)
        assertTrue(ticks > VineClimbEngine.GAME_OVER_TICKS)
    }

    @Test
    fun `recycled vine segments stay inside the authored length bounds`() {
        var state = VineClimbEngine.newGame(seed = 99)
        repeat(1_500) { state = VineClimbEngine.tick(state) }
        assertTrue(state.vines.isNotEmpty())
        for (vine in state.vines) {
            assertTrue("length ${vine.length}", vine.length in VineClimbEngine.VINE_LENGTH_MIN..VineClimbEngine.VINE_LENGTH_MAX)
            assertTrue(vine.lane in 0 until VineClimbEngine.LANES)
        }
        for (lane in 0 until VineClimbEngine.LANES) {
            assertNotNull(state.vines.firstOrNull { it.lane == lane })
        }
    }

    @Test
    fun `enemies spawn after the tutorial ends and respect the cadence window`() {
        var state = VineClimbEngine.newGame(seed = 5)
        state = VineClimbEngine.tick(state, VineClimbEngine.Input(left = true))
        assertFalse(state.inTutorial)
        var spawned = false
        var ticks = 0
        while (!spawned && ticks < VineClimbEngine.SPAWN_TICKS_MAX + 5) {
            state = VineClimbEngine.tick(state)
            if (state.enemies.isNotEmpty()) spawned = true
            ticks++
        }
        assertTrue(spawned)
        assertTrue(state.enemies.all { it.lane in 0 until VineClimbEngine.LANES })
    }

    @Test
    fun `same seed and input replay identically`() {
        fun run(): VineClimbEngine.State {
            var state = VineClimbEngine.newGame(seed = 1234)
            repeat(700) { index ->
                val input = when {
                    index % 41 == 0 -> VineClimbEngine.Input(left = true)
                    index % 67 == 0 -> VineClimbEngine.Input(right = true)
                    else -> VineClimbEngine.Input()
                }
                state = VineClimbEngine.tick(state, input)
            }
            return state
        }
        assertEquals(run(), run())
    }

    @Test
    fun `high score only rises`() {
        assertEquals(100, VineClimbEngine.updateHighScore(100, 50))
        assertEquals(150, VineClimbEngine.updateHighScore(100, 150))
        assertEquals(0, VineClimbEngine.updateHighScore(0, 0))
    }

    @Test
    fun `floating score rises two hundred pixels across its lifetime`() {
        assertEquals(0, VineClimbEngine.floatingRise(100))
        assertTrue(VineClimbEngine.floatingRise(50) > 0)
        assertEquals(200, VineClimbEngine.floatingRise(0))
    }
}
