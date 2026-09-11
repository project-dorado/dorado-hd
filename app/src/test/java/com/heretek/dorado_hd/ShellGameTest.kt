package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.ShellGameEngine
import com.heretek.dorado_hd.ui.apps.games.ShellPhase
import com.heretek.dorado_hd.ui.apps.games.ShellShakeRing
import com.heretek.dorado_hd.ui.apps.games.ShellState
import com.heretek.dorado_hd.ui.apps.games.ShellTier
import com.heretek.dorado_hd.ui.apps.games.ShakeSample
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the shell game engine (no Android dependency). */
class ShellGameTest {

    @Test
    fun `adjust charge halves the amount and clamps to 186`() {
        assertEquals(5f, ShellGameEngine.adjustCharge(0f, 10f), 0.001f)
        assertEquals(105f, ShellGameEngine.adjustCharge(100f, 10f), 0.001f)
        assertEquals(0f, ShellGameEngine.adjustCharge(0f, -10f), 0.001f)
        assertEquals(182f, ShellGameEngine.adjustCharge(181f, 2f), 0.001f)
        assertEquals(186f, ShellGameEngine.adjustCharge(184f, 10f), 0.001f)
        assertEquals(186f, ShellGameEngine.adjustCharge(200f, 40f), 0.001f)
    }

    @Test
    fun `charge lamps light at 72 and 144`() {
        assertEquals(0, ShellGameEngine.lamps(0f))
        assertEquals(1, ShellGameEngine.lamps(0.5f))
        assertEquals(1, ShellGameEngine.lamps(71.9f))
        assertEquals(2, ShellGameEngine.lamps(72f))
        assertEquals(2, ShellGameEngine.lamps(143.9f))
        assertEquals(3, ShellGameEngine.lamps(144f))
        assertEquals(3, ShellGameEngine.lamps(186f))
    }

    @Test
    fun `shake adds strength times four halved and resets the calm timer`() {
        val idle = ShellGameEngine.idle(seed = 1)
        val shaken = ShellGameEngine.step(idle, 16, shakeStrength = 10f)
        assertEquals(20f, shaken.charge, 0.001f)
        assertEquals(0L, shaken.calmMs)
    }

    @Test
    fun `drain is half of five per frame while not sitting`() {
        val launch = ShellGameEngine.idle(seed = 1).copy(phase = ShellPhase.LAUNCH, charge = 100f)
        val after = ShellGameEngine.step(launch, 100)
        assertEquals(85f, after.charge, 0.05f)
        val flying = launch.copy(phase = ShellPhase.FLYING)
        assertEquals(85f, ShellGameEngine.step(flying, 100).charge, 0.05f)
        val sitting = ShellGameEngine.idle(seed = 1).copy(charge = 100f)
        val held = ShellGameEngine.step(sitting, 100)
        assertEquals(100f, held.charge, 0.001f)
    }

    @Test
    fun `launch waits for 400 ms of calm`() {
        val charged = ShellGameEngine.idle(seed = 1).copy(charge = 50f)
        val early = ShellGameEngine.step(charged, 399)
        assertEquals(ShellPhase.SITTING, early.phase)
        val launched = ShellGameEngine.step(early, 2)
        assertEquals(ShellPhase.LAUNCH, launched.phase)
        val shaken = ShellGameEngine.step(charged, 250, shakeStrength = 5f)
        assertEquals(ShellPhase.SITTING, shaken.phase)
        assertEquals(0L, shaken.calmMs)
    }

    @Test
    fun `charge tiers split at 72 and 144`() {
        assertEquals(ShellTier.EASY, ShellGameEngine.tierFor(0f))
        assertEquals(ShellTier.EASY, ShellGameEngine.tierFor(71.9f))
        assertEquals(ShellTier.MEDIUM, ShellGameEngine.tierFor(72f))
        assertEquals(ShellTier.MEDIUM, ShellGameEngine.tierFor(143.9f))
        assertEquals(ShellTier.HARD, ShellGameEngine.tierFor(144f))
        assertEquals(ShellTier.HARD, ShellGameEngine.tierFor(186f))
    }

    @Test
    fun `path waypoint counts are 11 17 and 21 plus one pad each`() {
        assertEquals(11, ShellGameEngine.waypointCount(ShellTier.EASY))
        assertEquals(17, ShellGameEngine.waypointCount(ShellTier.MEDIUM))
        assertEquals(21, ShellGameEngine.waypointCount(ShellTier.HARD))
        assertEquals(12, ShellGameEngine.pathLength(ShellTier.EASY))
        assertEquals(18, ShellGameEngine.pathLength(ShellTier.MEDIUM))
        assertEquals(22, ShellGameEngine.pathLength(ShellTier.HARD))
        assertEquals(400f, ShellTier.EASY.speedPx, 0.001f)
        assertEquals(475f, ShellTier.MEDIUM.speedPx, 0.001f)
        assertEquals(575f, ShellTier.HARD.speedPx, 0.001f)
    }

    @Test
    fun `built paths end on distinct shuffled pads`() {
        ShellTier.entries.forEach { tier ->
            val paths = ShellGameEngine.buildPaths(tier, Random(7))
            assertEquals(3, paths.size)
            paths.forEach { path ->
                assertEquals("$tier path size", ShellGameEngine.pathLength(tier), path.size)
                val first = path.first()
                val pad = ShellGameEngine.PADS.firstOrNull { it.x == first.x }
                assertNotNull(pad)
                assertEquals(pad!!.y - 160f, first.y, 0.001f)
            }
            val endings = paths.map { it.last() }.toSet()
            assertEquals(ShellGameEngine.PADS.toSet(), endings)
        }
    }

    @Test
    fun `prepare hides one trophy in one robot`() {
        val state = ShellGameEngine.prepare(ShellGameEngine.idle(seed = 99).copy(charge = 100f))
        assertEquals(ShellPhase.LAUNCH, state.phase)
        assertEquals(ShellTier.MEDIUM, state.tier)
        assertTrue(state.hiddenIndex in 0..2)
        assertEquals(-1, state.chosenIndex)
        val trophy = state.trophy
        assertNotNull(trophy)
        assertTrue(trophy in ShellGameEngine.trophiesFor(ShellTier.MEDIUM))
        assertEquals(listOf(trophy), state.lastTrophies)
    }

    @Test
    fun `locked trophies come first and the last five are avoided`() {
        val pool = ShellGameEngine.trophiesFor(ShellTier.EASY)
        val last = pool.dropLast(1)
        val avoided = ShellGameEngine.pickTrophy(ShellTier.EASY, emptySet(), last, Random(3))
        assertEquals(pool.last(), avoided)
        val unlocked = (pool - pool.last()).toSet()
        val locked = ShellGameEngine.pickTrophy(ShellTier.EASY, unlocked, emptyList(), Random(3))
        assertEquals(pool.last(), locked)
    }

    private fun advanceToChoose(prepared: ShellState): ShellState {
        var state = prepared
        var guard = 0
        while (state.phase != ShellPhase.CHOOSE && guard < 5000) {
            state = ShellGameEngine.step(state, 16)
            guard++
        }
        return state
    }

    @Test
    fun `correct pick unlocks once and a second pick is ignored`() {
        val prepared = ShellGameEngine.prepare(ShellGameEngine.idle(seed = 12).copy(charge = 186f))
        val state = advanceToChoose(prepared)
        assertEquals(ShellPhase.CHOOSE, state.phase)
        val trophy = state.trophy!!
        val success = ShellGameEngine.choose(state, state.hiddenIndex)
        assertEquals(ShellPhase.SUCCESS, success.phase)
        assertTrue(trophy in success.unlocked)
        val ignored = ShellGameEngine.choose(success, (state.hiddenIndex + 1) % 3)
        assertSame(success, ignored)
    }

    @Test
    fun `wrong pick reveals the true holder after 400 ms`() {
        val prepared = ShellGameEngine.prepare(ShellGameEngine.idle(seed = 12).copy(charge = 50f))
        val state = advanceToChoose(prepared)
        val wrongIndex = (0 until 3).first { it != state.hiddenIndex }
        val failure = ShellGameEngine.choose(state, wrongIndex)
        assertEquals(ShellPhase.FAILURE, failure.phase)
        assertEquals(wrongIndex, failure.chosenIndex)
        assertFalse(failure.holderRevealed)
        assertTrue(failure.unlocked.isEmpty())
        val early = ShellGameEngine.step(failure, 399)
        assertFalse(early.holderRevealed)
        val revealed = ShellGameEngine.step(early, 2)
        assertTrue(revealed.holderRevealed)
    }

    @Test
    fun `flight completes to the choose phase`() {
        var state = ShellGameEngine.prepare(ShellGameEngine.idle(seed = 5).copy(charge = 186f))
        var guard = 0
        while (state.phase != ShellPhase.CHOOSE && guard < 5000) {
            state = ShellGameEngine.step(state, 16)
            guard++
        }
        assertEquals(ShellPhase.CHOOSE, state.phase)
        assertTrue(ShellGameEngine.allArrived(state))
        assertEquals(3, state.robots.size)
    }

    @Test
    fun `shake ring fires above two and clears`() {
        var ring = ShellShakeRing()
        repeat(9) { ring = ring.push(ShakeSample(0f, 0f, 0f)).first }
        val (cleared, strength) = ring.push(ShakeSample(5f, 0f, 0f))
        assertNotNull(strength)
        assertTrue(strength!! > ShellGameEngine.SHAKE_THRESHOLD)
        assertTrue(cleared.samples.isEmpty())

        var quiet = ShellShakeRing()
        var fired: Float? = null
        repeat(11) {
            val (next, event) = quiet.push(ShakeSample(0.05f * it, 0f, 0f))
            quiet = next
            if (event != null) fired = event
        }
        assertNull(fired)
    }
}
