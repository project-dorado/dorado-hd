package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.CheckersColor
import com.heretek.dorado_hd.ui.apps.games.CheckersDifficulty
import com.heretek.dorado_hd.ui.apps.games.CheckersEngine
import com.heretek.dorado_hd.ui.apps.games.CheckersMode
import com.heretek.dorado_hd.ui.apps.games.CheckersPiece
import com.heretek.dorado_hd.ui.apps.games.CheckersState
import com.heretek.dorado_hd.ui.apps.games.checkersPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckersAiTest {

    private fun empty(): Array<Array<CheckersPiece?>> = Array(8) { arrayOfNulls<CheckersPiece?>(8) }

    private fun state(vararg cells: Triple<Int, Int, CheckersPiece>): CheckersState {
        val board = empty()
        for ((r, c, piece) in cells) board[r][c] = piece
        return CheckersState(board, CheckersColor.RED, null, null)
    }

    @Test
    fun `checkers forced capture stays mandatory at every level`() {
        val s = state(
            Triple(2, 1, checkersPiece(CheckersColor.RED, false)),
            Triple(3, 2, checkersPiece(CheckersColor.BLACK, false)),
        )
        for (difficulty in CheckersDifficulty.entries) {
            val moves = CheckersEngine.legalMovesDetailed(s, CheckersColor.RED)
            assertEquals(1, moves.size)
            assertTrue(moves.first().isJump)
        }
    }

    @Test
    fun `checkers multi-jump chains are generated as a single move`() {
        val s = state(
            Triple(0, 1, checkersPiece(CheckersColor.RED, false)),
            Triple(1, 2, checkersPiece(CheckersColor.BLACK, false)),
            Triple(3, 4, checkersPiece(CheckersColor.BLACK, false)),
        )
        val moves = CheckersEngine.legalMovesDetailed(s, CheckersColor.RED)
        assertEquals(1, moves.size)
        val move = moves.first()
        assertEquals(2, move.captures.size)
        assertTrue(move.isMultiJump)
        assertEquals(4 to 5, move.to)
        val after = CheckersEngine.applyMove(s, move)
        assertEquals(CheckersColor.BLACK, after.turn)
        assertNull(after.board[1][2])
        assertNull(after.board[3][4])
    }

    @Test
    fun `checkers king moves and captures in every diagonal direction`() {
        val s = state(
            Triple(4, 3, checkersPiece(CheckersColor.RED, true)),
            Triple(5, 2, checkersPiece(CheckersColor.BLACK, false)),
            Triple(3, 4, checkersPiece(CheckersColor.BLACK, false)),
        )
        val moves = CheckersEngine.legalMovesDetailed(s, CheckersColor.RED)
        assertTrue(moves.any { it.to == 6 to 1 })
        assertTrue(moves.any { it.to == 2 to 5 })
    }

    @Test
    fun `checkers crowning promotes at the back rank`() {
        val s = state(Triple(6, 1, checkersPiece(CheckersColor.RED, false)))
        val move = CheckersEngine.legalMovesDetailed(s, CheckersColor.RED).first { it.to == 7 to 0 || it.to == 7 to 2 }
        val after = CheckersEngine.applyMove(s, move)
        val king = after.board[move.to.first][move.to.second]
        assertTrue(king!!.king)
    }

    @Test
    fun `checkers victory fires when the opponent has no move`() {
        val board = empty()
        board[0][1] = checkersPiece(CheckersColor.RED, false)
        val s = CheckersState(board, CheckersColor.RED, null, null)
        val after = CheckersEngine.apply(s, 0 to 1, 1 to 0)
        assertEquals(CheckersColor.RED, after.winner)
    }

    @Test
    fun `checkers ai returns a legal move for every difficulty`() {
        val s = CheckersEngine.newGame()
        for (difficulty in CheckersDifficulty.entries) {
            val move = CheckersEngine.bestMove(s, difficulty, maxNodes = 20_000)
            assertNotNull("expected a move at $difficulty", move)
            val legal = CheckersEngine.legalMovesDetailed(s).map { it.from to it.to }
            assertTrue("AI move must be legal at $difficulty", legal.contains(move!!.from to move.to))
        }
    }

    @Test
    fun `checkers ai takes a free capture`() {
        val s = state(
            Triple(2, 1, checkersPiece(CheckersColor.RED, false)),
            Triple(3, 2, checkersPiece(CheckersColor.BLACK, false)),
            Triple(1, 0, checkersPiece(CheckersColor.BLACK, false)),
            Triple(0, 7, checkersPiece(CheckersColor.BLACK, false)),
        )
        val move = CheckersEngine.bestMove(s, CheckersDifficulty.INTERMEDIATE, maxNodes = 30_000)
        assertNotNull(move)
        assertTrue(move!!.isJump)
        assertEquals(2 to 1, move.from)
        assertEquals(4 to 3, move.to)
    }

    @Test
    fun `checkers hint returns a legal move when one exists`() {
        val s = CheckersEngine.newGame()
        val hint = CheckersEngine.hint(s)
        assertNotNull(hint)
        val legal = CheckersEngine.legalMovesDetailed(s).map { it.from to it.to }
        assertTrue(legal.contains(hint!!.from to hint.to))
    }

    @Test
    fun `checkers undo takes back two half moves`() {
        val s = CheckersEngine.newGame()
        val first = CheckersEngine.legalMovesDetailed(s).first()
        val afterFirst = CheckersEngine.applyMove(s, first)
        val second = CheckersEngine.legalMovesDetailed(afterFirst).first()
        val afterSecond = CheckersEngine.applyMove(afterFirst, second)
        assertEquals(s.turn, afterSecond.turn)
        val undone = CheckersEngine.undo(afterSecond)
        assertEquals(CheckersColor.RED, undone.turn)
        assertEquals(12, undone.board.sumOf { row -> row.count { it?.color == CheckersColor.RED } })
        assertEquals(12, undone.board.sumOf { row -> row.count { it?.color == CheckersColor.BLACK } })
    }

    @Test
    fun `checkers fen round trips the position`() {
        val s = CheckersEngine.newGame()
        val fen = CheckersEngine.fen(s)
        val restored = CheckersEngine.fromFen(fen)
        assertNotNull(restored)
        assertEquals(fen, CheckersEngine.fen(restored!!))
        assertEquals(12, restored.board.sumOf { row -> row.count { it?.color == CheckersColor.RED } })

        val board = empty()
        board[6][1] = checkersPiece(CheckersColor.RED, true)
        board[0][1] = checkersPiece(CheckersColor.BLACK, false)
        val custom = CheckersState(board, CheckersColor.BLACK, null, null)
        val customFen = CheckersEngine.fen(custom)
        val customRestored = CheckersEngine.fromFen(customFen)
        assertNotNull(customRestored)
        assertEquals(CheckersColor.BLACK, customRestored!!.turn)
        assertTrue(customRestored.board[6][1]!!.king)
        assertFalse(customRestored.board[0][1]!!.king)
    }

    @Test
    fun `checkers save slot round trips`() {
        val save = com.heretek.dorado_hd.ui.apps.games.CheckersSave(
            fen = "R:1,2,3:B29,30",
            difficulty = "hard",
            mode = "suicide",
            flipped = true,
            redElapsedMs = 1234,
            blackElapsedMs = 5678,
        )
        val decoded = CheckersEngine.decodeSave(CheckersEngine.encodeSave(save))
        assertEquals(save, decoded)
    }

    @Test
    fun `checkers suicide evaluation inverts the sign`() {
        val s = state(
            Triple(0, 1, checkersPiece(CheckersColor.RED, false)),
            Triple(1, 0, checkersPiece(CheckersColor.RED, false)),
            Triple(2, 1, checkersPiece(CheckersColor.RED, false)),
            Triple(7, 0, checkersPiece(CheckersColor.BLACK, false)),
        )
        val regular = CheckersEngine.evaluateFor(s, CheckersColor.RED, CheckersMode.REGULAR)
        val suicide = CheckersEngine.evaluateFor(s, CheckersColor.RED, CheckersMode.SUICIDE)
        assertTrue("regular eval favours the material lead", regular > 0)
        assertEquals(-regular, suicide)
        val move = CheckersEngine.bestMove(s, CheckersDifficulty.EASY, CheckersMode.SUICIDE, maxNodes = 5_000)
        assertNotNull(move)
        val legal = CheckersEngine.legalMovesDetailed(s).map { it.from to it.to }
        assertTrue(legal.contains(move!!.from to move.to))
    }
}
