package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SudokuEngine
import com.heretek.dorado_hd.ui.apps.games.SudokuGame
import com.heretek.dorado_hd.ui.apps.games.SudokuLevel
import com.heretek.dorado_hd.ui.apps.games.SudokuRecord
import com.heretek.dorado_hd.ui.apps.games.SudokuType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGeneratorTest {

    private fun clues(board: SudokuEngine.Board): Int = board.given.flatten().count { it }

    @Test
    fun `seeded generator is deterministic and uniqueness checked`() {
        val a = SudokuEngine.newGame(seed = 7L)
        val b = SudokuEngine.newGame(seed = 7L)
        assertEquals(a.rows, b.rows)
        assertTrue("generated puzzle must be unique", SudokuEngine.hasUniqueSolution(a.rows))
        assertEquals(a.solution, SudokuEngine.solve(a.rows))
        assertTrue("puzzle must contain blanks", a.rows.flatten().contains(0))
        a.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, v ->
                assertEquals("given mask mismatch at $r,$c", v != 0, a.given[r][c])
            }
        }
    }

    @Test
    fun `difficulties carve progressively fewer clues`() {
        val easy = SudokuEngine.newGame(SudokuType.CLASSIC, SudokuLevel.EASY, seed = 21L)
        val normal = SudokuEngine.newGame(SudokuType.CLASSIC, SudokuLevel.NORMAL, seed = 21L)
        val hard = SudokuEngine.newGame(SudokuType.CLASSIC, SudokuLevel.HARD, seed = 21L)
        assertTrue(clues(easy) >= SudokuEngine.clueTarget(SudokuType.CLASSIC, SudokuLevel.EASY))
        assertTrue(clues(normal) >= SudokuEngine.clueTarget(SudokuType.CLASSIC, SudokuLevel.NORMAL))
        assertTrue(clues(hard) >= SudokuEngine.clueTarget(SudokuType.CLASSIC, SudokuLevel.HARD))
        assertTrue("easy ${clues(easy)} should out-clue normal ${clues(normal)}", clues(easy) > clues(normal))
        assertTrue("normal ${clues(normal)} should out-clue hard ${clues(hard)}", clues(normal) > clues(hard))
        assertTrue(SudokuEngine.hasUniqueSolution(easy.rows))
        assertTrue(SudokuEngine.hasUniqueSolution(normal.rows))
        assertTrue(SudokuEngine.hasUniqueSolution(hard.rows))
    }

    @Test
    fun `mini board is six by six and unique`() {
        val board = SudokuEngine.newGame(SudokuType.MINI, SudokuLevel.NORMAL, seed = 3L)
        assertEquals(6, board.rows.size)
        assertTrue(board.rows.all { it.size == 6 })
        assertEquals(36, board.given.flatten().size)
        assertTrue(SudokuEngine.hasUniqueSolution(board.rows, SudokuType.MINI))
        assertEquals(board.solution, SudokuEngine.solve(board.rows, SudokuType.MINI))
    }

    @Test
    fun `conflicts mark every duplicate in a house`() {
        val rows = MutableList(9) { MutableList(9) { 0 } }
        rows[0][0] = 5
        rows[0][1] = 5
        rows[1][0] = 5
        val conflicts = SudokuEngine.conflicts(rows)
        assertTrue(conflicts.containsAll(setOf(0, 1, 9)))
        assertTrue(SudokuEngine.conflicts(MutableList(9) { MutableList(9) { 0 } }).isEmpty())
    }

    @Test
    fun `candidates exclude row column and box values`() {
        val rows = MutableList(9) { MutableList(9) { 0 } }
        for (i in 0 until 8) rows[0][i] = i + 1
        assertEquals(setOf(9), SudokuEngine.candidates(rows, 8))
        assertEquals((4..9).toSet(), SudokuEngine.candidates(rows, 9))
        rows[0][0] = 0
        assertTrue(SudokuEngine.candidates(rows, 0).contains(1))
    }

    @Test
    fun `hint fills candidate notes and rejects a repeat on the same cell`() {
        val game = SudokuEngine.start(SudokuEngine.newGame(seed = 11L), SudokuType.CLASSIC, SudokuLevel.NORMAL)
        val index = game.cells.indices.first { game.cells[it] == 0 }
        assertTrue(SudokuEngine.canHint(game, index))
        val hinted = SudokuEngine.hint(game, index)
        val expected = SudokuEngine.candidates(game.cells.chunked(9), index)
        assertEquals(expected, hinted.notes[index])
        assertEquals(index, hinted.lastHintCell)
        assertSame("second hint on the same cell is rejected", hinted, SudokuEngine.hint(hinted, index))
    }

    @Test
    fun `place toggle erase and undo track session state`() {
        val base = SudokuEngine.start(SudokuEngine.newGame(seed = 15L), SudokuType.CLASSIC, SudokuLevel.NORMAL)
        val index = base.cells.indices.first { base.cells[it] == 0 }
        val value = base.board.solution[index / 9][index % 9]

        val noted = SudokuEngine.toggleNote(base, index, value)
        assertTrue(noted.notes[index].contains(value))
        assertEquals(base.notes[index], SudokuEngine.toggleNote(noted, index, value).notes[index])

        val placed = SudokuEngine.place(noted, index, value)
        assertEquals(value, placed.cells[index])
        assertTrue(placed.notes[index].isEmpty())
        val undone = SudokuEngine.undo(placed)
        assertEquals(0, undone.cells[index])
        assertTrue(undone.notes[index].contains(value))

        val erased = SudokuEngine.erase(noted, index)
        assertTrue(erased.notes[index].isEmpty())
        assertEquals(0, erased.cells[index])
    }

    @Test
    fun `solve fills the grid as one undo step`() {
        val game = SudokuEngine.start(SudokuEngine.newGame(seed = 19L), SudokuType.CLASSIC, SudokuLevel.NORMAL)
        val solved = SudokuEngine.solve(game)
        assertTrue(solved.solved)
        assertEquals(game.board.solution.flatten(), solved.cells)
        val undone = SudokuEngine.undo(solved)
        assertFalse(undone.solved)
        assertEquals(game.cells, undone.cells)
    }

    @Test
    fun `session serialization roundtrips`() {
        var game: SudokuGame = SudokuEngine.start(SudokuEngine.newGame(seed = 23L), SudokuType.CLASSIC, SudokuLevel.NORMAL)
        val index = game.cells.indices.first { game.cells[it] == 0 }
        game = SudokuEngine.hint(game, index)
        val other = game.cells.indices.first { game.cells[it] == 0 && it != index }
        game = SudokuEngine.place(game, other, game.board.solution[other / 9][other % 9] % 9 + 1)
        val decoded = SudokuEngine.decode(SudokuEngine.encode(game))
        assertEquals(game.type, decoded?.type)
        assertEquals(game.level, decoded?.level)
        assertEquals(game.cells, decoded?.cells)
        assertEquals(game.notes, decoded?.notes)
        assertEquals(game.lastHintCell, decoded?.lastHintCell)
        assertEquals(game.board.rows, decoded?.board?.rows)
        assertEquals(game.board.solution, decoded?.board?.solution)
        assertNull(SudokuEngine.decode("junk"))
    }

    @Test
    fun `records roundtrip and unsolvable grids are rejected`() {
        val records = mapOf(
            "classic:easy" to SudokuRecord(bestSeconds = 45, solved = 2),
            "mini:hard" to SudokuRecord(bestSeconds = 0, solved = 0),
        )
        assertEquals(records, SudokuEngine.decodeRecords(SudokuEngine.encodeRecords(records)))

        val contradictory = MutableList(9) { MutableList(9) { 0 } }
        contradictory[0][0] = 5
        contradictory[0][1] = 5
        assertFalse(SudokuEngine.hasUniqueSolution(contradictory))
        assertNull(SudokuEngine.solve(contradictory))
    }
}
