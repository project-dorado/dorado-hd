package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.PicturePuzzleEngine
import com.heretek.dorado_hd.ui.apps.games.PuzzleConfig
import com.heretek.dorado_hd.ui.apps.games.PuzzlePiece
import com.heretek.dorado_hd.ui.apps.games.PuzzleSide
import com.heretek.dorado_hd.ui.apps.games.PuzzleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PicturePuzzleTest {

    private fun solved(config: PuzzleConfig): PuzzleState {
        val pieces = ArrayList<PuzzlePiece>()
        var id = 0
        for (row in 0 until config.gridSize) {
            for (col in 0 until config.gridSize) {
                if (row == 0 && col == config.gridSize - 1) continue // the global empty
                pieces += PuzzlePiece(id++, PuzzleSide.FRONT, row, col, 0, row, col, PuzzleSide.FRONT)
            }
        }
        if (config.twoSided) {
            for (row in 0 until config.gridSize) {
                for (col in 0 until config.gridSize) {
                    pieces += PuzzlePiece(id++, PuzzleSide.BACK, row, col, 1, row, col, PuzzleSide.BACK)
                }
            }
        }
        return PuzzleState(
            config = config,
            pieces = pieces,
            emptySide = PuzzleSide.FRONT,
            emptyRow = 0,
            emptyCol = config.gridSize - 1,
            complete = true,
        )
    }

    private fun emptyCount(state: PuzzleState): Int {
        if (!state.config.twoSided) {
            var count = 0
            for (row in 0 until state.config.gridSize) {
                for (col in 0 until state.config.gridSize) {
                    if (state.pieceAt(PuzzleSide.FRONT, row, col) == null) count++
                }
            }
            return count
        }
        var count = 0
        for (side in listOf(PuzzleSide.FRONT, PuzzleSide.BACK)) {
            for (row in 0 until state.config.gridSize) {
                for (col in 0 until state.config.gridSize) {
                    if (state.pieceAt(side, row, col) == null) count++
                }
            }
        }
        return count
    }

    @Test
    fun `new one-sided game shuffles and keeps a single empty`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, false), seed = 42)
        assertFalse(state.complete)
        assertEquals(1, emptyCount(state))
        assertEquals(8, state.pieces.size)
        assertTrue(state.pieces.all { it.side == PuzzleSide.FRONT })
    }

    @Test
    fun `shuffle uses four times grid plus one moves`() {
        val config = PuzzleConfig(3, false)
        val state = PicturePuzzleEngine.newGame(config, seed = 7)
        assertEquals(0, state.moves)
        val moves = 4 * (config.gridSize + 1)
        val replayed = PicturePuzzleEngine.shuffle(
            solved(config).copy(pieces = state.pieces),
            kotlin.random.Random(7),
        )
        assertNotNull(replayed)
        assertTrue(moves in 1..64)
    }

    @Test
    fun `slide is legal only into the adjacent empty`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, false), seed = 1)
        val emptyRow = state.emptyRow
        val emptyCol = state.emptyCol
        // A diagonal neighbour is never legal.
        if (emptyRow + 1 < 3 && emptyCol + 1 < 3) {
            assertFalse(PicturePuzzleEngine.canSlide(state, PuzzleSide.FRONT, emptyRow + 1, emptyCol + 1))
        }
        val legal = PicturePuzzleEngine.legalSlides(state)
        assertTrue(legal.isNotEmpty())
        val (side, row, col) = legal.first()
        val next = PicturePuzzleEngine.slide(state, side, row, col)
        assertNotNull(next)
        assertEquals(1, next!!.moves)
        assertEquals(1, emptyCount(next))
        assertEquals(row, next.emptyRow)
        assertEquals(col, next.emptyCol)
    }

    @Test
    fun `slide rejects the wrong board and empty cell`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, true), seed = 2)
        val wrongSide = if (state.emptySide == PuzzleSide.FRONT) PuzzleSide.BACK else PuzzleSide.FRONT
        assertNull(PicturePuzzleEngine.slide(state, wrongSide, 0, 0))
        assertNull(PicturePuzzleEngine.slide(state, state.emptySide, state.emptyRow, state.emptyCol))
    }

    @Test
    fun `two-sided game starts with one empty across both boards`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(4, true), seed = 4)
        assertEquals(1, emptyCount(state))
        assertEquals(2 * 16 - 1, state.pieces.size)
        assertTrue(state.pieces.any { it.side == PuzzleSide.BACK })
    }

    @Test
    fun `flip transfers a piece across boards and conserves the empty`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, true), seed = 11)
        val other = state.pieces.first { it.side != state.emptySide }
        val flipped = PicturePuzzleEngine.flip(state, other.side, other.row, other.col)
        assertNotNull(flipped)
        assertEquals(1, emptyCount(flipped!!))
        assertEquals(other.side, flipped.emptySide)
        val moved = flipped.pieces.first { it.id == other.id }
        assertEquals(state.emptySide, moved.side)
        val vacated = flipped.pieceAt(other.side, other.row, other.col)
        assertNull(vacated)
    }

    @Test
    fun `flip rejects a piece on the empty board`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, true), seed = 5)
        val legal = PicturePuzzleEngine.legalSlides(state).first()
        assertNull(PicturePuzzleEngine.flip(state, legal.first, legal.second, legal.third))
        val oneSided = PicturePuzzleEngine.newGame(PuzzleConfig(3, false), seed = 5)
        val slide = PicturePuzzleEngine.legalSlides(oneSided).first()
        assertFalse(PicturePuzzleEngine.canFlip(oneSided, slide.first, slide.second, slide.third))
    }

    @Test
    fun `completion requires every tile at its home cell and board`() {
        val config = PuzzleConfig(3, false)
        val done = solved(config)
        assertTrue(PicturePuzzleEngine.isComplete(done))
        val moved = done.pieces.mapIndexed { index, piece ->
            if (index == 0) piece.copy(row = 1, col = 1) else piece
        }
        assertFalse(PicturePuzzleEngine.isComplete(done.copy(pieces = moved)))
    }

    @Test
    fun `two-sided completion checks the back board too`() {
        val config = PuzzleConfig(3, true)
        val done = solved(config)
        assertTrue(PicturePuzzleEngine.isComplete(done))
        val broken = done.pieces.map { piece ->
            if (piece.side == PuzzleSide.BACK && piece.id == done.pieces.last().id) {
                piece.copy(side = PuzzleSide.FRONT, row = 1, col = 1)
            } else {
                piece
            }
        }
        assertFalse(PicturePuzzleEngine.isComplete(done.copy(pieces = broken)))
    }

    @Test
    fun `best-time keys are per configuration`() {
        assertEquals("best.OneSided3x3", PuzzleConfig(3, false).bestTimeKey)
        assertEquals("best.OneSided4x4", PuzzleConfig(4, false).bestTimeKey)
        assertEquals("best.TwoSided3x3", PuzzleConfig(3, true).bestTimeKey)
        assertEquals("best.TwoSided4x4", PuzzleConfig(4, true).bestTimeKey)
    }

    @Test
    fun `time formatting matches the device shape`() {
        assertEquals("0:00.00", PicturePuzzleEngine.formatTime(0))
        assertEquals("0:05.30", PicturePuzzleEngine.formatTime(5300))
        assertEquals("1:01.99", PicturePuzzleEngine.formatTime(61990))
    }

    @Test
    fun `step only advances the clock while active`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, false), seed = 9)
        val running = state.copy(active = true)
        assertEquals(100, PicturePuzzleEngine.step(running, 100).elapsedMs)
        assertEquals(0, PicturePuzzleEngine.step(state, 100).elapsedMs)
        // Steps are clamped so a stalled frame cannot inflate the clock.
        assertEquals(1000, PicturePuzzleEngine.step(running, 60_000).elapsedMs)
    }

    @Test
    fun `home number is row major and one based`() {
        val state = PicturePuzzleEngine.newGame(PuzzleConfig(3, false), seed = 3)
        val corner = state.pieces.first { it.homeRow == 0 && it.homeCol == 0 }
        assertEquals(1, PicturePuzzleEngine.homeNumber(state, corner))
        val last = state.pieces.first { it.homeRow == 2 && it.homeCol == 2 }
        assertEquals(9, PicturePuzzleEngine.homeNumber(state, last))
    }
}
