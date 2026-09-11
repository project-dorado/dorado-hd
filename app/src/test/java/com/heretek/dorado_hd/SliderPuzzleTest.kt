package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SlideSize
import com.heretek.dorado_hd.ui.apps.games.SlideState
import com.heretek.dorado_hd.ui.apps.games.SliderPuzzleEngine
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the slider puzzle engine (no Android dependency). */
class SliderPuzzleTest {

    private val sizes = SlideSize.entries.toList()

    @Test
    fun `sizes map to 4x2 5x3 and 6x4 geometry`() {
        assertEquals(4, SlideSize.EIGHT.rows)
        assertEquals(2, SlideSize.EIGHT.cols)
        assertEquals(8, SlideSize.EIGHT.tiles)
        assertEquals(5, SlideSize.FIFTEEN.rows)
        assertEquals(3, SlideSize.FIFTEEN.cols)
        assertEquals(15, SlideSize.FIFTEEN.tiles)
        assertEquals(6, SlideSize.TWENTY_FOUR.rows)
        assertEquals(4, SlideSize.TWENTY_FOUR.cols)
        assertEquals(24, SlideSize.TWENTY_FOUR.tiles)
        assertEquals(447, SliderPuzzleEngine.BOARD_HEIGHT)
        assertEquals(SlideSize.FIFTEEN, SlideSize.fromLabel("15"))
        assertEquals(SlideSize.TWENTY_FOUR, SlideSize.fromTiles(24))
    }

    @Test
    fun `target layout numbers descend from the bottom right`() {
        assertEquals(
            listOf(7, 6, 5, 4, 3, 2, 1, 0),
            SliderPuzzleEngine.targetCells(SlideSize.EIGHT),
        )
        assertEquals(7, SliderPuzzleEngine.homeCell(SlideSize.EIGHT, 0))
        assertEquals(0, SliderPuzzleEngine.homeCell(SlideSize.EIGHT, 7))
        assertEquals(23, SliderPuzzleEngine.homeCell(SlideSize.TWENTY_FOUR, 0))
    }

    @Test
    fun `shuffle produces a legal solvable unsolved board`() {
        sizes.forEach { size ->
            val a = SliderPuzzleEngine.newGame(size, Random(1234))
            val b = SliderPuzzleEngine.newGame(size, Random(1234))
            val c = SliderPuzzleEngine.newGame(size, Random(4321))
            assertEquals(size.tiles, a.cells.size)
            assertEquals((0 until size.tiles).toSet(), a.cells.toSet())
            assertTrue("$size shuffle must be solvable", SliderPuzzleEngine.isSolvable(a.cells, size))
            assertFalse("$size shuffle must not start solved", a.solved)
            assertEquals(0, a.moves)
            assertEquals("seeded shuffle is deterministic", a.cells, b.cells)
            assertNotEquals("different seeds differ", a.cells, c.cells)
        }
    }

    @Test
    fun `adjacency rule only blank neighbours move`() {
        val solved = SlideState(SlideSize.EIGHT, SliderPuzzleEngine.targetCells(SlideSize.EIGHT))
        assertTrue(SliderPuzzleEngine.canMove(solved, 6))
        assertFalse(SliderPuzzleEngine.canMove(solved, 0))
        assertFalse("the blank itself cannot move", SliderPuzzleEngine.canMove(solved, 7))
        assertSame(solved, SliderPuzzleEngine.applyMove(solved, 0))
        assertSame(solved, SliderPuzzleEngine.applyMove(solved, 7))
        assertNotEquals(solved, SliderPuzzleEngine.applyMove(solved, 6))
    }

    @Test
    fun `move counter increments per legal slide and blocks during animation`() {
        val solved = SlideState(SlideSize.EIGHT, SliderPuzzleEngine.targetCells(SlideSize.EIGHT))
        val moved = SliderPuzzleEngine.applyMove(solved, 6)
        assertEquals(1, moved.moves)
        assertEquals(0, moved.cells[6])
        assertEquals(1, moved.cells[7])
        // A second tap while the 0.15 s slide is running is ignored.
        val busy = SliderPuzzleEngine.applyMove(moved, 7)
        assertSame(moved, busy)
        assertEquals(1, busy.moves)
    }

    @Test
    fun `slide animation ends after 150 ms`() {
        val solved = SlideState(SlideSize.EIGHT, SliderPuzzleEngine.targetCells(SlideSize.EIGHT))
        val moved = SliderPuzzleEngine.applyMove(solved, 6)
        assertEquals(6, moved.anim?.fromCell)
        assertEquals(7, moved.anim?.toCell)
        val mid = SliderPuzzleEngine.step(moved, 100)
        assertEquals(100L, mid.anim?.elapsedMs)
        assertFalse(SliderPuzzleEngine.canMove(mid, 7))
        val done = SliderPuzzleEngine.step(mid, 50)
        assertNull(done.anim)
        assertEquals(moved.cells, done.cells)
        assertSame(done, SliderPuzzleEngine.step(done, 50))
    }

    @Test
    fun `win detection on the target layout`() {
        val target = SliderPuzzleEngine.targetCells(SlideSize.EIGHT)
        val solved = SlideState(SlideSize.EIGHT, target)
        assertTrue(solved.solved)
        val moved = SliderPuzzleEngine.applyMove(solved, 6)
        assertFalse(moved.solved)
        val settled = SliderPuzzleEngine.step(moved, 150)
        val undone = SliderPuzzleEngine.applyMove(settled, 7)
        assertTrue(undone.solved)
        assertEquals(2, undone.moves)
    }

    @Test
    fun `unsolvable permutation is rejected`() {
        val target = SliderPuzzleEngine.targetCells(SlideSize.EIGHT)
        val swapped = target.toMutableList()
        swapped[0] = target[1]
        swapped[1] = target[0]
        assertFalse(SliderPuzzleEngine.isSolvable(swapped, SlideSize.EIGHT))
        assertFalse(SliderPuzzleEngine.isSolvable(listOf(1, 2, 3), SlideSize.EIGHT))
    }

    @Test
    fun `high scores sort fastest first and keep the top ten`() {
        var scores = emptyList<SliderPuzzleEngine.SlideScore>()
        for (i in 1..12) {
            scores = SliderPuzzleEngine.insertScore(scores, SliderPuzzleEngine.SlideScore(i * 1000L, i))
        }
        assertEquals(SliderPuzzleEngine.MAX_SCORES, scores.size)
        assertEquals(1000L, scores.first().elapsedMs)
        assertEquals(10000L, scores.last().elapsedMs)
        val faster = SliderPuzzleEngine.insertScore(scores, SliderPuzzleEngine.SlideScore(500L, 99))
        assertEquals(500L, faster.first().elapsedMs)
        val tie = SliderPuzzleEngine.insertScore(faster, SliderPuzzleEngine.SlideScore(500L, 3))
        assertEquals(3, tie.first().moves)
    }

    @Test
    fun `state and score encode decode round trips`() {
        val state = SlideState(
            size = SlideSize.FIFTEEN,
            cells = SliderPuzzleEngine.newGame(SlideSize.FIFTEEN, Random(9)).cells,
            moves = 42,
            elapsedMs = 65_400,
        )
        assertEquals(state, SliderPuzzleEngine.decode(SliderPuzzleEngine.encode(state)))
        assertNull(SliderPuzzleEngine.decode("garbage"))
        assertNull(SliderPuzzleEngine.decode(null))
        val scores = listOf(
            SliderPuzzleEngine.SlideScore(12_000, 80),
            SliderPuzzleEngine.SlideScore(9_500, 120),
        )
        assertEquals(scores, SliderPuzzleEngine.decodeScores(SliderPuzzleEngine.encodeScores(scores)))
        assertEquals(emptyList<SliderPuzzleEngine.SlideScore>(), SliderPuzzleEngine.decodeScores(null))
    }
}
