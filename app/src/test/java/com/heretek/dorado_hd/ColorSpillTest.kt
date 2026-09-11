package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.ColorSpillEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSpillTest {

    private fun state(
        cells: List<Int>,
        width: Int,
        height: Int,
        colors: Int,
        moves: Int = 0,
        par: Int = 4,
        maxMoves: Int = par + ColorSpillEngine.MOVE_SLACK,
        won: Boolean = false,
        lost: Boolean = false,
    ) = ColorSpillEngine.SpillState(
        level = 1,
        width = width,
        height = height,
        colors = colors,
        cells = cells,
        moves = moves,
        par = par,
        maxMoves = maxMoves,
        seed = 1,
        won = won,
        lost = lost,
    )

    @Test
    fun `filling a single colour board wins immediately`() {
        val board = state(List(9) { 0 }, 3, 3, 3)
        val next = ColorSpillEngine.floodFill(board, 1)
        assertTrue(next.won)
        assertFalse(next.lost)
        assertEquals(1, next.moves)
        assertTrue(next.cells.all { it == 1 })
    }

    @Test
    fun `flood fill grows the connected region and keeps it`() {
        val board = state(
            listOf(
                0, 0, 1,
                0, 0, 1,
                1, 1, 1,
            ),
            3,
            3,
            2,
        )
        val next = ColorSpillEngine.floodFill(board, 1)
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1, 1, 1), next.cells)
        assertTrue(next.won)
    }

    @Test
    fun `diagonal cells are not connected`() {
        val board = state(listOf(0, 1, 1, 0), 2, 2, 2)
        val next = ColorSpillEngine.floodFill(board, 1)
        assertEquals(listOf(1, 1, 1, 0), next.cells)
        assertFalse(next.won)
        assertEquals(1, next.moves)
    }

    @Test
    fun `picking the current origin colour is a no-op`() {
        val board = state(listOf(0, 1, 1, 0), 2, 2, 2)
        val next = ColorSpillEngine.floodFill(board, 0)
        assertSame(board, next)
        assertEquals(0, next.moves)
    }

    @Test
    fun `flood fill is ignored after the level ends`() {
        val won = state(List(4) { 1 }, 2, 2, 2, won = true)
        assertSame(won, ColorSpillEngine.floodFill(won, 0))
        val lost = state(listOf(0, 1, 1, 0), 2, 2, 2, lost = true)
        assertSame(lost, ColorSpillEngine.floodFill(lost, 1))
    }

    @Test
    fun `running out of moves loses the level`() {
        val board = state(listOf(0, 1, 1, 0), 2, 2, 2, maxMoves = 1)
        val next = ColorSpillEngine.floodFill(board, 1)
        assertFalse(next.won)
        assertTrue(next.lost)
        assertEquals(1, next.moves)
    }

    @Test
    fun `levels grow the board and the palette`() {
        assertEquals(5, ColorSpillEngine.sizeForLevel(1))
        assertEquals(14, ColorSpillEngine.sizeForLevel(10))
        assertEquals(14, ColorSpillEngine.sizeForLevel(40))
        assertEquals(3, ColorSpillEngine.colorsForLevel(1))
        assertEquals(4, ColorSpillEngine.colorsForLevel(3))
        assertEquals(5, ColorSpillEngine.colorsForLevel(5))
        assertEquals(6, ColorSpillEngine.colorsForLevel(7))
        assertEquals(6, ColorSpillEngine.colorsForLevel(20))
    }

    @Test
    fun `a new game has a greedy par and a move allowance`() {
        val game = ColorSpillEngine.newGame(level = 1, seed = 42)
        assertEquals(5, game.width)
        assertEquals(5, game.height)
        assertEquals(3, game.colors)
        assertTrue("par should be at least one move", game.par >= 1)
        assertEquals(game.par + ColorSpillEngine.MOVE_SLACK, game.maxMoves)
        assertEquals(25, game.cells.size)
        assertTrue(game.cells.all { it in 0 until game.colors })
        assertTrue(game.cells.distinct().size > 1)
    }

    @Test
    fun `board generation is deterministic for a seed`() {
        assertEquals(
            ColorSpillEngine.newGame(1, 7).cells,
            ColorSpillEngine.newGame(1, 7).cells,
        )
        assertNotEquals(
            ColorSpillEngine.newGame(1, 7).cells,
            ColorSpillEngine.newGame(1, 8).cells,
        )
    }

    @Test
    fun `score rewards finishing under par`() {
        val par = state(List(4) { 1 }, 2, 2, 2, moves = 4, par = 4, won = true)
        val under = state(List(4) { 1 }, 2, 2, 2, moves = 2, par = 4, won = true)
        val unfinished = state(listOf(0, 1, 1, 0), 2, 2, 2)
        assertEquals(ColorSpillEngine.WIN_BASE, ColorSpillEngine.score(par))
        assertTrue(ColorSpillEngine.score(under) > ColorSpillEngine.score(par))
        assertEquals(0, ColorSpillEngine.score(unfinished))
    }

    @Test
    fun `greedy par is deterministic and bounded`() {
        val first = ColorSpillEngine.randomBoard(6, 6, 3, ColorSpillEngine.SpillRandom(99))
        val second = ColorSpillEngine.randomBoard(6, 6, 3, ColorSpillEngine.SpillRandom(99))
        assertEquals(first, second)
        val par = ColorSpillEngine.greedyPar(first, 6, 6, 3)
        assertEquals(par, ColorSpillEngine.greedyPar(second, 6, 6, 3))
        assertTrue(par in 1..36)
        assertEquals(0, ColorSpillEngine.greedyPar(List(36) { 1 }, 6, 6, 3))
    }

    @Test
    fun `encode and decode round trip a running game`() {
        val game = ColorSpillEngine.newGame(level = 2, seed = 99)
        val filled = ColorSpillEngine.floodFill(game, (game.originColor() + 1) % game.colors)
        val restored = ColorSpillEngine.decode(ColorSpillEngine.encode(filled))
        assertEquals(filled, restored)
        assertEquals(1, restored?.moves)
    }

    @Test
    fun `decode rejects malformed blobs`() {
        assertEquals(null, ColorSpillEngine.decode(null))
        assertEquals(null, ColorSpillEngine.decode(""))
        assertEquals(null, ColorSpillEngine.decode("garbage"))
    }

    @Test
    fun `step advances the clock and freezes once the level ends`() {
        val game = ColorSpillEngine.newGame(1, 5)
        val ticked = ColorSpillEngine.step(game, 1000)
        assertEquals(1000L, ticked.elapsedMs)
        val won = state(List(4) { 1 }, 2, 2, 2, won = true)
        assertSame(won, ColorSpillEngine.step(won, 1000))
    }
}
