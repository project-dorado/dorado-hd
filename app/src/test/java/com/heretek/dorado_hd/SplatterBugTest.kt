package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BugKind
import com.heretek.dorado_hd.ui.apps.games.BugTap
import com.heretek.dorado_hd.ui.apps.games.SPLATTER_FADE_MS
import com.heretek.dorado_hd.ui.apps.games.SPLATTER_SPAWN_MS
import com.heretek.dorado_hd.ui.apps.games.SPLATTER_START_LIVES
import com.heretek.dorado_hd.ui.apps.games.SPLATTER_TARANTULA_BURST
import com.heretek.dorado_hd.ui.apps.games.SPLATTER_TOAST_MS
import com.heretek.dorado_hd.ui.apps.games.SplatterBug
import com.heretek.dorado_hd.ui.apps.games.SplatterBugEngine
import com.heretek.dorado_hd.ui.apps.games.SplatterBugEvent
import com.heretek.dorado_hd.ui.apps.games.SplatterBugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplatterBugTest {

    private fun bug(
        kind: BugKind,
        x: Float,
        y: Float,
        rotated: Boolean = false,
        id: Int = 1,
    ): SplatterBug = SplatterBug(id = id, kind = kind, x = x, y = y, rotated = rotated)

    private fun stateWith(
        vararg bugs: SplatterBug,
        lives: Int = SPLATTER_START_LIVES,
        score: Int = 0,
    ): SplatterBugState = SplatterBugState(
        score = score,
        lives = lives,
        bugs = bugs.toList(),
        spawnClockMs = 0L,
        elapsedMs = 0L,
        nextId = (bugs.maxOfOrNull { it.id } ?: 0) + 1,
        seed = 42,
    )

    @Test
    fun `bug table matches the spec sizes hit points and speeds`() {
        assertEquals(60f, BugKind.ANT.size, 0.001f)
        assertEquals(10, BugKind.ANT.hp)
        assertEquals(6f, BugKind.ANT.speed, 0.001f)
        assertEquals(70f, BugKind.BUTTERFLY.size, 0.001f)
        assertEquals(30, BugKind.BUTTERFLY.hp)
        assertEquals(8f, BugKind.BUTTERFLY.speed, 0.001f)
        assertEquals(70f, BugKind.COCKROACH.size, 0.001f)
        assertEquals(10, BugKind.COCKROACH.hp)
        assertEquals(7f, BugKind.COCKROACH.speed, 0.001f)
        assertEquals(60f, BugKind.EARWIG.size, 0.001f)
        assertEquals(10, BugKind.EARWIG.hp)
        assertEquals(10f, BugKind.EARWIG.speed, 0.001f)
        assertEquals(60f, BugKind.LADYBIRD.size, 0.001f)
        assertEquals(30, BugKind.LADYBIRD.hp)
        assertEquals(5f, BugKind.LADYBIRD.speed, 0.001f)
        assertEquals(36f, BugKind.SPIDER.size, 0.001f)
        assertEquals(20, BugKind.SPIDER.hp)
        assertEquals(4f, BugKind.SPIDER.speed, 0.001f)
        assertEquals(162f, BugKind.TARANTULA.size, 0.001f)
        assertEquals(50, BugKind.TARANTULA.hp)
        assertEquals(6f, BugKind.TARANTULA.speed, 0.001f)
        assertTrue(BugKind.BUTTERFLY.harmless)
        assertTrue(BugKind.LADYBIRD.harmless)
        assertFalse(BugKind.ANT.harmless)
        assertEquals(3, BugKind.TARANTULA.tapsToKill)
    }

    @Test
    fun `spawn weight bands follow the spec edges`() {
        assertEquals(BugKind.ANT, SplatterBugEngine.rollKind(0))
        assertEquals(BugKind.ANT, SplatterBugEngine.rollKind(19))
        assertEquals(BugKind.BUTTERFLY, SplatterBugEngine.rollKind(20))
        assertEquals(BugKind.BUTTERFLY, SplatterBugEngine.rollKind(34))
        assertEquals(BugKind.COCKROACH, SplatterBugEngine.rollKind(35))
        assertEquals(BugKind.EARWIG, SplatterBugEngine.rollKind(50))
        assertEquals(BugKind.LADYBIRD, SplatterBugEngine.rollKind(65))
        assertEquals(BugKind.SPIDER, SplatterBugEngine.rollKind(80))
        assertEquals(BugKind.TARANTULA, SplatterBugEngine.rollKind(95))
        assertEquals(BugKind.TARANTULA, SplatterBugEngine.rollKind(99))
    }

    @Test
    fun `spawn cadence is seven hundred fifty milliseconds`() {
        var state = SplatterBugEngine.newGame(1)
        state = SplatterBugEngine.step(state, SPLATTER_SPAWN_MS - 50L)
        assertEquals(0, state.bugs.size)
        state = SplatterBugEngine.step(state, 50L)
        assertEquals(1, state.bugs.size)
        state = SplatterBugEngine.step(state, SPLATTER_SPAWN_MS)
        assertEquals(2, state.bugs.size)
    }

    @Test
    fun `spawn positions stay in the forty to two hundred thirty one band`() {
        var state = SplatterBugEngine.newGame(9)
        var sawUp = false
        var sawDown = false
        repeat(60) {
            state = SplatterBugEngine.step(state, SPLATTER_SPAWN_MS)
            for (bug in state.bugs) {
                assertTrue("x=${bug.x} out of band", bug.x in 40f..231f)
                if (bug.rotated) sawDown = true else sawUp = true
            }
        }
        assertTrue(sawUp)
        assertTrue(sawDown)
    }

    @Test
    fun `tapping a harmless critter costs a life and scores nothing`() {
        val state = stateWith(bug(BugKind.BUTTERFLY, 100f, 200f))
        val next = SplatterBugEngine.step(state, 33L, listOf(BugTap(110f, 210f)))
        assertEquals(SPLATTER_START_LIVES - 1, next.lives)
        assertEquals(0, next.score)
        val hurt = next.bugs.single()
        assertTrue(hurt.splatted)
        assertEquals("-life", hurt.label)
        assertTrue(next.events.any { it is SplatterBugEvent.Squish && it.harmless })
    }

    @Test
    fun `tapping a pest awards its hit points`() {
        val state = stateWith(bug(BugKind.ANT, 100f, 200f))
        val next = SplatterBugEngine.step(state, 33L, listOf(BugTap(110f, 210f)))
        assertEquals(10, next.score)
        assertEquals(SPLATTER_START_LIVES, next.lives)
        val killed = next.bugs.single()
        assertTrue(killed.splatted)
        assertEquals("+10", killed.label)
    }

    @Test
    fun `a second tap in the same press cannot double squish`() {
        val state = stateWith(bug(BugKind.ANT, 100f, 200f))
        val next = SplatterBugEngine.step(
            state,
            33L,
            listOf(BugTap(110f, 210f), BugTap(112f, 212f)),
        )
        assertEquals(10, next.score)
        assertEquals(1, next.events.count { it is SplatterBugEvent.Squish })
    }

    @Test
    fun `letting a harmless critter escape awards its hit points`() {
        val state = stateWith(bug(BugKind.LADYBIRD, 80f, -61f))
        val next = SplatterBugEngine.step(state, 33L)
        assertEquals(30, next.score)
        assertEquals(SPLATTER_START_LIVES, next.lives)
        assertTrue(next.bugs.isEmpty())
        assertTrue(next.events.any { it is SplatterBugEvent.Escaped && it.harmless })
    }

    @Test
    fun `an escaped pest costs a life and raises the toast`() {
        val state = stateWith(bug(BugKind.ANT, 80f, -61f))
        val next = SplatterBugEngine.step(state, 33L)
        assertEquals(0, next.score)
        assertEquals(SPLATTER_START_LIVES - 1, next.lives)
        assertEquals(SPLATTER_TOAST_MS, next.toastMs)
        assertTrue(next.events.any { it is SplatterBugEvent.Escaped && !it.harmless })
    }

    @Test
    fun `the second failure leaves zero lives without ending the run`() {
        val state = stateWith(bug(BugKind.ANT, 80f, -61f), lives = 1)
        val next = SplatterBugEngine.step(state, 33L)
        assertEquals(0, next.lives)
        assertFalse(next.over)
    }

    @Test
    fun `the fourth failure reaches minus one and ends the run`() {
        val state = stateWith(bug(BugKind.ANT, 80f, -61f), lives = 0)
        val next = SplatterBugEngine.step(state, 33L)
        assertEquals(-1, next.lives)
        assertTrue(next.over)
        assertTrue(next.events.contains(SplatterBugEvent.GameOver))
    }

    @Test
    fun `a tarantula needs three taps and bursts baby spiders`() {
        var state = stateWith(bug(BugKind.TARANTULA, 40f, 120f, id = 7))
        state = SplatterBugEngine.step(state, 33L, listOf(BugTap(100f, 180f)))
        assertEquals(2, state.bugs.first { it.id == 7 }.tapsLeft)
        assertEquals(0, state.score)
        state = SplatterBugEngine.step(state, 33L, listOf(BugTap(100f, 170f)))
        assertEquals(1, state.bugs.first { it.id == 7 }.tapsLeft)
        assertEquals(0, state.score)
        state = SplatterBugEngine.step(state, 33L, listOf(BugTap(100f, 160f)))
        val dead = state.bugs.first { it.id == 7 }
        assertTrue(dead.splatted)
        assertEquals(50, state.score)
        assertTrue(state.events.contains(SplatterBugEvent.TarantulaBurst))
        assertEquals(SPLATTER_TARANTULA_BURST, state.bugs.count { it.kind == BugKind.SPIDER })
    }

    @Test
    fun `splatted bugs fade for five seconds and then vanish`() {
        val splatted = SplatterBug(
            id = 1,
            kind = BugKind.ANT,
            x = 100f,
            y = 100f,
            rotated = false,
            splatted = true,
            ageMs = 0L,
        )
        var state = stateWith(splatted)
        state = SplatterBugEngine.step(state, SPLATTER_FADE_MS - 100L)
        assertTrue(state.bugs.any { it.id == 1 })
        state = SplatterBugEngine.step(state, 100L)
        assertFalse(state.bugs.any { it.id == 1 })
    }

    @Test
    fun `bugs crawl in the direction they spawned`() {
        val up = stateWith(bug(BugKind.ANT, 100f, 200f, rotated = false))
        val upMoved = SplatterBugEngine.step(up, 33L)
        assertTrue(upMoved.bugs.single().y < 200f)

        val down = stateWith(bug(BugKind.ANT, 100f, 200f, rotated = true))
        val downMoved = SplatterBugEngine.step(down, 33L)
        assertTrue(downMoved.bugs.single().y > 200f)
    }

    @Test
    fun `encode decode round trips a run`() {
        var state = SplatterBugEngine.newGame(31)
        repeat(40) {
            state = SplatterBugEngine.step(state, 33L, listOf(BugTap(120f, 240f)))
        }
        val decoded = SplatterBugEngine.decode(SplatterBugEngine.encode(state))
        assertNotNull(decoded)
        decoded!!
        assertEquals(state.score, decoded.score)
        assertEquals(state.lives, decoded.lives)
        assertEquals(state.spawnClockMs, decoded.spawnClockMs)
        assertEquals(state.seed, decoded.seed)
        assertEquals(state.bugs.size, decoded.bugs.size)
        assertEquals(state.bugs.first().kind, decoded.bugs.first().kind)
        assertEquals(state.bugs.first().x, decoded.bugs.first().x, 0.0001f)
        assertNull(SplatterBugEngine.decode("garbage"))
    }

    @Test
    fun `a fresh run after a game over steps again`() {
        val over = stateWith(lives = -1, score = 40).copy(over = true)
        val frozen = SplatterBugEngine.step(over, SPLATTER_SPAWN_MS)
        assertTrue(frozen.over)
        assertEquals(over.score, frozen.score)

        val fresh = SplatterBugEngine.newGame(99)
        val next = SplatterBugEngine.step(fresh, SPLATTER_SPAWN_MS)
        assertFalse(next.over)
        assertTrue("a restarted run must advance", next.elapsedMs > fresh.elapsedMs)
    }
}
