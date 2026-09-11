package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.DecoderRingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Decoder Ring substitution-cipher crossword. */
class DecoderRingTest {

    private fun solvedState(index: Int = 0, difficulty: DecoderRingEngine.Difficulty = DecoderRingEngine.Difficulty.EXPERT): DecoderRingEngine.State {
        val puzzle = DecoderRingEngine.puzzle(index)
        var state = DecoderRingEngine.newGame(index, difficulty)
        for (row in 0 until puzzle.height) {
            for (col in 0 until puzzle.width) {
                val symbol = puzzle.symbolAt(row, col)
                if (symbol <= 0) continue
                state = DecoderRingEngine.highlight(state, symbol)
                state = DecoderRingEngine.reveal(state)
            }
        }
        return state
    }

    @Test
    fun `dataset grids are 15 by 15 with letter and blank cells`() {
        assertEquals(12, DecoderRingEngine.puzzles.size)
        for (puzzle in DecoderRingEngine.puzzles) {
            assertEquals(15, puzzle.width)
            assertEquals(15, puzzle.height)
            for (row in 0 until 15) {
                for (col in 0 until 15) {
                    val ch = puzzle.cell(row, col)
                    assertTrue("bad cell $ch", ch == '.' || ch in 'A'..'Z')
                    assertEquals(ch == '.', puzzle.isBlank(row, col))
                }
            }
            assertTrue(puzzle.used.isNotEmpty())
            assertTrue(puzzle.used.size <= 26)
        }
    }

    @Test
    fun `grid parsing normalizes lowercase and rejects bad shapes`() {
        val rows = DecoderRingEngine.puzzle(0).rows.map { it.lowercase() }
        val parsed = DecoderRingEngine.parseGrid(rows)
        assertEquals('P', parsed[0][0])
        assertEquals('.', parsed[3][0])
        var failed = false
        try {
            DecoderRingEngine.parseGrid(List(14) { ".".repeat(15) })
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
        failed = false
        try {
            DecoderRingEngine.parseGrid(List(15) { "#".repeat(15) })
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `difficulty seeds the documented reveal sets`() {
        assertEquals(7, DecoderRingEngine.Difficulty.EASY.revealed.size)
        assertEquals(4, DecoderRingEngine.Difficulty.NORMAL.revealed.size)
        assertEquals(3, DecoderRingEngine.Difficulty.HARD.revealed.size)
        assertEquals(0, DecoderRingEngine.Difficulty.EXPERT.revealed.size)
        for (puzzle in DecoderRingEngine.puzzles) {
            for (difficulty in DecoderRingEngine.Difficulty.entries) {
                val state = DecoderRingEngine.newGame(puzzle.index, difficulty)
                val usedSeeds = difficulty.revealed.filter { it in puzzle.used }
                assertEquals("stone count for ${difficulty.label}", usedSeeds.size, state.stone.size)
                for (letter in usedSeeds) {
                    val symbol = puzzle.symbolOf(letter)
                    assertEquals(letter, state.mapping[symbol])
                    assertTrue(symbol in state.stone)
                }
            }
        }
        assertEquals(0, DecoderRingEngine.newGame(0, DecoderRingEngine.Difficulty.EXPERT).stone.size)
    }

    @Test
    fun `placing a letter maps the symbol everywhere and moves it off other symbols`() {
        val puzzle = DecoderRingEngine.puzzle(0)
        var state = DecoderRingEngine.newGame(0, DecoderRingEngine.Difficulty.EXPERT)
        val symbol = (1..26).first { puzzle.letterOf(it) != null }
        state = DecoderRingEngine.place(state, symbol, 'Q')
        assertEquals('Q', state.mapping[symbol])
        val other = (1..26).first { it != symbol && puzzle.letterOf(it) != null }
        state = DecoderRingEngine.place(state, other, 'Q')
        assertNull(state.mapping[symbol])
        assertEquals('Q', state.mapping[other])
    }

    @Test
    fun `revealed tiles are locked against place and clear`() {
        val puzzle = DecoderRingEngine.puzzle(0)
        val state = DecoderRingEngine.newGame(0, DecoderRingEngine.Difficulty.EASY)
        val stoneSymbol = state.stone.first()
        val stoneLetter = state.mapping[stoneSymbol]
        assertNotNull(stoneLetter)
        val afterPlace = DecoderRingEngine.place(state, stoneSymbol, 'Z')
        assertEquals(state, afterPlace)
        val afterClear = DecoderRingEngine.clear(state, stoneSymbol)
        assertEquals(state, afterClear)
        val afterReveal = DecoderRingEngine.highlight(state, stoneSymbol).let { DecoderRingEngine.reveal(it) }
        assertEquals(state.mapping[stoneSymbol], afterReveal.mapping[stoneSymbol])
    }

    @Test
    fun `win requires every used letter to be correct`() {
        val solved = solvedState()
        assertTrue(DecoderRingEngine.isWon(solved))
        val puzzle = DecoderRingEngine.puzzle(0)
        val usedSymbol = (1..26).first { puzzle.letterOf(it) != null }
        val broken = solved.copy(mapping = solved.mapping.toMutableList().also { it[usedSymbol] = null })
        assertFalse(DecoderRingEngine.isWon(broken))
    }

    @Test
    fun `unused letters never block the win`() {
        val puzzle = DecoderRingEngine.puzzle(0)
        val solved = solvedState()
        val unusedSymbol = (1..26).first { puzzle.letterOf(it) == null }
        val guess = ('A'..'Z').first { it !in puzzle.used }
        val withGuess = DecoderRingEngine.place(solved, unusedSymbol, guess)
        assertEquals(guess, withGuess.mapping[unusedSymbol])
        assertTrue(DecoderRingEngine.isWon(withGuess))
    }

    @Test
    fun `check toggle confirms correct placements as stone`() {
        val puzzle = DecoderRingEngine.puzzle(0)
        var state = DecoderRingEngine.newGame(0, DecoderRingEngine.Difficulty.EXPERT, checkEnabled = false)
        val symbol = (1..26).first { puzzle.letterOf(it) != null }
        val letter = puzzle.letterOf(symbol)!!
        state = DecoderRingEngine.place(state, symbol, letter)
        assertFalse(symbol in state.stone)
        state = DecoderRingEngine.toggleCheck(state)
        assertTrue(state.checkEnabled)
        assertTrue(symbol in state.stone)
        state = DecoderRingEngine.toggleCheck(state)
        assertFalse(state.checkEnabled)
    }

    @Test
    fun `twenty level slots cycle the authored dataset`() {
        assertEquals(DecoderRingEngine.PACK_SLOTS, 20)
        assertEquals(DecoderRingEngine.puzzle(0), DecoderRingEngine.puzzle(12))
        assertEquals(DecoderRingEngine.puzzle(7), DecoderRingEngine.puzzle(19))
        val highSlot = DecoderRingEngine.newGame(13, DecoderRingEngine.Difficulty.EXPERT)
        assertEquals(highSlot, DecoderRingEngine.decode(DecoderRingEngine.encode(highSlot)))
    }

    @Test
    fun `state round trips through encode and decode`() {
        val state = DecoderRingEngine.newGame(3, DecoderRingEngine.Difficulty.NORMAL)
            .let { DecoderRingEngine.place(it, 5, 'K') }
            .let { DecoderRingEngine.highlight(it, 7) }
        val decoded = DecoderRingEngine.decode(DecoderRingEngine.encode(state))
        assertEquals(state, decoded)
        assertNull(DecoderRingEngine.decode("garbage"))
        assertNull(DecoderRingEngine.decode("1|99|0|1|" + ".".repeat(27) + "|||"))
    }

    @Test
    fun `progress keeps the hardest tier and unlocks levels in order`() {
        var progress = DecoderRingEngine.Progress()
        assertEquals(0, DecoderRingEngine.solvedCount(progress))
        assertEquals(0, DecoderRingEngine.unlockedCount(progress))
        progress = DecoderRingEngine.recordCompletion(progress, 0, DecoderRingEngine.Difficulty.EASY)
        assertEquals(DecoderRingEngine.Difficulty.EASY, progress.best(0))
        progress = DecoderRingEngine.recordCompletion(progress, 0, DecoderRingEngine.Difficulty.HARD)
        assertEquals(DecoderRingEngine.Difficulty.HARD, progress.best(0))
        assertEquals(1, DecoderRingEngine.solvedCount(progress))
        assertEquals(1, DecoderRingEngine.unlockedCount(progress))
        assertTrue(DecoderRingEngine.isLevelUnlocked(progress, 1))
        assertFalse(DecoderRingEngine.isLevelUnlocked(progress, 2))
        assertEquals(progress, DecoderRingEngine.decodeProgress(DecoderRingEngine.encodeProgress(progress)))
    }

    @Test
    fun `flick velocity is clamped and friction decays`() {
        assertEquals(1200f, DecoderRingEngine.flickVelocity(100f, 100L), 0.01f)
        assertEquals(3000f, DecoderRingEngine.flickVelocity(10_000f, 100L), 0.01f)
        assertEquals(-3000f, DecoderRingEngine.flickVelocity(-10_000f, 100L), 0.01f)
        assertEquals(0f, DecoderRingEngine.flickVelocity(50f, 0L), 0.001f)
        val decayed = DecoderRingEngine.applyFriction(1000f, 1000L)
        assertTrue(decayed in 1f..100f)
        assertTrue(DecoderRingEngine.applyFriction(decayed, 1000L) < decayed)
    }

    @Test
    fun `zoom clamps to the two board levels and toggles`() {
        assertEquals(DecoderRingEngine.ZOOM_RACK, DecoderRingEngine.clampZoom(-10f), 0.0001f)
        assertEquals(DecoderRingEngine.ZOOM_BOARD, DecoderRingEngine.clampZoom(10f), 0.0001f)
        assertEquals(DecoderRingEngine.ZOOM_BOARD, DecoderRingEngine.toggleZoom(DecoderRingEngine.ZOOM_RACK), 0.0001f)
        assertEquals(DecoderRingEngine.ZOOM_RACK, DecoderRingEngine.toggleZoom(DecoderRingEngine.ZOOM_BOARD), 0.0001f)
    }

    @Test
    fun `step advances the win cascade and caps it`() {
        var state = solvedState()
        assertTrue(DecoderRingEngine.isWon(state))
        state = DecoderRingEngine.step(state, 400)
        assertTrue(state.cascadeTicks > 0)
        repeat(20) { state = DecoderRingEngine.step(state, 400) }
        assertEquals(DecoderRingEngine.WIN_DELAY_TICKS, state.cascadeTicks)
    }
}
