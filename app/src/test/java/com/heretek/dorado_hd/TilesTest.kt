package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.TilesEngine
import com.heretek.dorado_hd.ui.apps.games.TilesMode
import com.heretek.dorado_hd.ui.apps.games.TilesPhase
import com.heretek.dorado_hd.ui.apps.games.TilesState
import com.heretek.dorado_hd.ui.apps.games.TilesStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {

    private fun state(
        slots: List<Int>,
        mode: TilesMode = TilesMode.CLASSIC,
        phase: TilesPhase = TilesPhase.PLAY,
        swapCount: Int = 0,
        countdown: Int = 0,
        elapsedMs: Long = 0L,
    ) = TilesState(
        mode = mode,
        slots = slots,
        swapCount = swapCount,
        elapsedMs = elapsedMs,
        countdown = countdown,
        phase = phase,
        seed = 1,
        rngState = 1,
    )

    private fun inversions(slots: List<Int>): Int {
        val tiles = slots.filter { it != TilesEngine.BLANK }
        var count = 0
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (tiles[i] > tiles[j]) count++
            }
        }
        return count
    }

    @Test
    fun `solved layout is the blank at slot zero with the values ascending`() {
        assertEquals(listOf(8, 0, 1, 2, 3, 4, 5, 6, 7), TilesEngine.solved())
        assertTrue(TilesEngine.isSolved(TilesEngine.solved()))
        assertFalse(TilesEngine.isSolved(listOf(8, 0, 1, 2, 3, 4, 5, 7, 6)))
        assertFalse(TilesEngine.isSolved(listOf(0, 8, 1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `1000 swap shuffle stays a legal solvable permutation`() {
        val rng = TilesEngine.TilesRandom(1234)
        val slots = TilesEngine.shuffle(TilesEngine.solved(), rng)
        assertEquals(9, slots.size)
        assertEquals((0..8).toSet(), slots.toSet())
        assertEquals(0, inversions(slots) % 2)
        assertNotEquals(TilesEngine.solved(), slots)
    }

    @Test
    fun `only tiles orthogonally adjacent to the blank move`() {
        val board = state(listOf(0, 1, 2, 3, 8, 5, 6, 7, 4))
        assertFalse(TilesEngine.isLegalMove(board.slots, 0))
        assertFalse(TilesEngine.isLegalMove(board.slots, 2))
        assertFalse(TilesEngine.isLegalMove(board.slots, 4))
        assertTrue(TilesEngine.isLegalMove(board.slots, 1))
        assertTrue(TilesEngine.isLegalMove(board.slots, 3))
        assertTrue(TilesEngine.isLegalMove(board.slots, 5))
        assertTrue(TilesEngine.isLegalMove(board.slots, 7))

        assertSame(board, TilesEngine.tap(board, 0))
        assertSame(board, TilesEngine.tap(board, 4))
        val moved = TilesEngine.tap(board, 1)
        assertEquals(listOf(0, 8, 2, 3, 1, 5, 6, 7, 4), moved.slots)
        assertEquals(1, moved.swapCount)
    }

    @Test
    fun `moves are ignored outside the play phase`() {
        val board = state(listOf(0, 1, 2, 3, 8, 5, 6, 7, 4), phase = TilesPhase.COUNTDOWN)
        assertSame(board, TilesEngine.tap(board, 1))
        val won = state(listOf(8, 0, 1, 2, 3, 4, 5, 6, 7), phase = TilesPhase.WON)
        assertSame(won, TilesEngine.tap(won, 1))
    }

    @Test
    fun `completing the pattern wins and stops the clock`() {
        val board = state(listOf(0, 8, 1, 2, 3, 4, 5, 6, 7))
        val won = TilesEngine.tap(board, 0)
        assertEquals(TilesPhase.WON, won.phase)
        assertEquals(1, won.swapCount)
        assertTrue(TilesEngine.isSolved(won.slots))
        val frozen = TilesEngine.step(won.copy(elapsedMs = 5000), 5000)
        assertEquals(5000L, frozen.elapsedMs)
    }

    @Test
    fun `countdown reshuffles each second and shows the mined labels`() {
        assertEquals("3", TilesEngine.countdownLabel(8))
        assertEquals("3", TilesEngine.countdownLabel(7))
        assertEquals("2", TilesEngine.countdownLabel(6))
        assertEquals("2", TilesEngine.countdownLabel(5))
        assertEquals("1", TilesEngine.countdownLabel(4))
        assertEquals("1", TilesEngine.countdownLabel(3))
        assertEquals("go", TilesEngine.countdownLabel(2))
        assertEquals("go", TilesEngine.countdownLabel(1))
        assertEquals(null, TilesEngine.countdownLabel(0))

        var game = TilesEngine.newGame(TilesMode.CLASSIC, 5)
        assertEquals(8, game.countdown)
        assertEquals(TilesPhase.COUNTDOWN, game.phase)
        repeat(8) {
            game = TilesEngine.step(game, 1000)
            assertEquals((0..8).toSet(), game.slots.toSet())
        }
        assertEquals(0, game.countdown)
        assertEquals(TilesPhase.PLAY, game.phase)
        assertEquals(0L, game.elapsedMs)
    }

    @Test
    fun `the clock runs only in the play phase`() {
        var game = TilesEngine.newGame(TilesMode.TIME_TRIAL, 6)
        repeat(4) { game = TilesEngine.step(game, 1000) }
        assertEquals(0L, game.elapsedMs)
        assertEquals(TilesPhase.COUNTDOWN, game.phase)
        repeat(4) { game = TilesEngine.step(game, 1000) }
        assertEquals(TilesPhase.PLAY, game.phase)
        game = TilesEngine.step(game, 1000)
        assertEquals(1, TilesEngine.seconds(game))
        game = TilesEngine.step(game, 250)
        assertEquals(1, TilesEngine.seconds(game))
        game = TilesEngine.step(game, 750)
        assertEquals(2, TilesEngine.seconds(game))
    }

    @Test
    fun `best records improve only and wins increment`() {
        val stats = TilesStats(leastMoves = 40, leastTime = 30, wins = 2)
        val worse = TilesEngine.applyWin(stats, TilesMode.CLASSIC, moves = 50, elapsedSeconds = 10)
        assertEquals(40, worse.leastMoves)
        assertEquals(3, worse.wins)
        val better = TilesEngine.applyWin(stats, TilesMode.CLASSIC, moves = 30, elapsedSeconds = 10)
        assertEquals(30, better.leastMoves)
        assertEquals(30, better.leastTime)
        val faster = TilesEngine.applyWin(stats, TilesMode.TIME_TRIAL, moves = 20, elapsedSeconds = 25)
        assertEquals(25, faster.leastTime)
        val slower = TilesEngine.applyWin(stats, TilesMode.TIME_TRIAL, moves = 20, elapsedSeconds = 45)
        assertEquals(30, slower.leastTime)
        assertTrue(TilesEngine.bestBeaten(stats, TilesMode.CLASSIC, 30, 10))
        assertFalse(TilesEngine.bestBeaten(stats, TilesMode.CLASSIC, 50, 10))
        assertTrue(TilesEngine.bestBeaten(stats, TilesMode.TIME_TRIAL, 10, 25))
        assertFalse(TilesEngine.bestBeaten(stats, TilesMode.TIME_TRIAL, 10, 45))
    }

    @Test
    fun `losses increment only when a game had moves`() {
        val stats = TilesStats(wins = 1, losses = 4)
        assertEquals(stats, TilesEngine.applyLoss(stats, 0))
        assertEquals(5, TilesEngine.applyLoss(stats, 3).losses)
    }

    @Test
    fun `stats round trip and default to 500 and 600`() {
        val defaults = TilesEngine.decodeStats(null)
        assertEquals(500, defaults.leastMoves)
        assertEquals(600, defaults.leastTime)
        assertEquals(0, defaults.wins)
        assertEquals(0, defaults.losses)
        assertTrue(defaults.audioEnabled)
        val custom = TilesStats(leastMoves = 123, leastTime = 45, wins = 7, losses = 2, audioEnabled = false)
        assertEquals(custom, TilesEngine.decodeStats(TilesEngine.encodeStats(custom)))
        assertEquals(defaults, TilesEngine.decodeStats("garbage"))
    }

    @Test
    fun `encode and decode round trip a running game`() {
        var game = TilesEngine.newGame(TilesMode.TIME_TRIAL, 42)
        repeat(9) { game = TilesEngine.step(game, 1000) }
        val legal = game.slots.indices.first { TilesEngine.isLegalMove(game.slots, it) }
        game = TilesEngine.tap(game, legal)
        val restored = TilesEngine.decode(TilesEngine.encode(game))
        assertEquals(game, restored)
        assertNotEquals(null, restored)
    }

    @Test
    fun `decode rejects malformed blobs`() {
        assertEquals(null, TilesEngine.decode(null))
        assertEquals(null, TilesEngine.decode("garbage"))
        assertEquals(null, TilesEngine.decode("v1;CLASSIC;12345678;0;0;0;COUNTDOWN;1;1;0"))
    }
}
