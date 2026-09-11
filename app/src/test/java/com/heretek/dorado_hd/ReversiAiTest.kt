package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.ReversiEngine
import com.heretek.dorado_hd.ui.apps.games.ReversiGame
import com.heretek.dorado_hd.ui.apps.games.ReversiLevel
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReversiAiTest {

    private fun assertBoardEquals(expected: Array<IntArray>, actual: Array<IntArray>) {
        expected.forEachIndexed { r, row -> assertTrue("row $r differs", row.contentEquals(actual[r])) }
    }

    @Test
    fun `easy ai always returns a legal move`() {
        repeat(25) { seed ->
            val board = ReversiEngine.newBoard()
            val move = ReversiEngine.bestMove(board, ReversiEngine.BLACK, ReversiLevel.EASY, Random(seed))
            assertNotNull(move)
            assertTrue(move!! in ReversiEngine.legalMoves(board, ReversiEngine.BLACK))
        }
        val full = Array(8) { IntArray(8) { ReversiEngine.BLACK } }
        assertNull(ReversiEngine.bestMove(full, ReversiEngine.WHITE, ReversiLevel.EASY, Random(0)))
    }

    @Test
    fun `medium maximizes immediate piece count`() {
        val board = ReversiEngine.newBoard()
        val best = ReversiEngine.legalMoves(board, ReversiEngine.BLACK).maxByOrNull {
            ReversiEngine.pieceCount(ReversiEngine.apply(board, it.first, it.second, ReversiEngine.BLACK), ReversiEngine.BLACK)
        }!!
        val picked = ReversiEngine.bestMove(board, ReversiEngine.BLACK, ReversiLevel.MEDIUM, Random(0))!!
        assertEquals(
            ReversiEngine.pieceCount(ReversiEngine.apply(board, best.first, best.second, ReversiEngine.BLACK), ReversiEngine.BLACK),
            ReversiEngine.pieceCount(ReversiEngine.apply(board, picked.first, picked.second, ReversiEngine.BLACK), ReversiEngine.BLACK),
        )
        val whitePick = ReversiEngine.bestMove(board, ReversiEngine.WHITE, ReversiLevel.MEDIUM, Random(0))!!
        assertTrue(whitePick in ReversiEngine.legalMoves(board, ReversiEngine.WHITE))
    }

    @Test
    fun `hard takes an available corner`() {
        val board = Array(8) { IntArray(8) }
        board[1][1] = ReversiEngine.WHITE
        board[2][2] = ReversiEngine.WHITE
        board[3][3] = ReversiEngine.BLACK
        assertTrue((0 to 0) in ReversiEngine.legalMoves(board, ReversiEngine.BLACK))
        assertEquals(0 to 0, ReversiEngine.bestMove(board, ReversiEngine.BLACK, ReversiLevel.HARD, Random(0)))
    }

    @Test
    fun `hard self play is bounded and legal`() {
        var game = ReversiEngine.newGame()
        val rng = Random(4)
        var plies = 0
        while (!game.over && plies < 80) {
            val move = ReversiEngine.bestMove(game.board, game.turn, ReversiLevel.HARD, rng) ?: break
            assertTrue(move in ReversiEngine.legalMoves(game.board, game.turn))
            game = ReversiEngine.play(game, move.first, move.second)
            plies++
        }
        assertTrue("hard self-play should make progress", plies > 10)
    }

    @Test
    fun `next player passes when the opponent is stuck and ends when both are stuck`() {
        val board = Array(8) { IntArray(8) }
        board[1][0] = ReversiEngine.BLACK
        board[0][0] = ReversiEngine.WHITE
        board[0][1] = ReversiEngine.WHITE
        assertTrue(ReversiEngine.legalMoves(board, ReversiEngine.WHITE).isNotEmpty())
        assertTrue("black must be stuck here", ReversiEngine.legalMoves(board, ReversiEngine.BLACK).isEmpty())
        assertEquals(ReversiEngine.WHITE, ReversiEngine.nextPlayer(board, ReversiEngine.WHITE))

        val full = Array(8) { r -> IntArray(8) { c -> if ((r + c) % 2 == 0) ReversiEngine.BLACK else ReversiEngine.WHITE } }
        assertNull(ReversiEngine.nextPlayer(full, ReversiEngine.BLACK))
        assertTrue(ReversiEngine.isGameOver(full))
        assertEquals(32, ReversiEngine.pieceCount(full, ReversiEngine.BLACK))
        assertEquals(ReversiEngine.EMPTY, ReversiEngine.determineWinner(full))
    }

    @Test
    fun `play auto passes when the opponent is stuck`() {
        val board = Array(8) { IntArray(8) }
        board[1][0] = ReversiEngine.BLACK
        board[0][0] = ReversiEngine.WHITE
        board[0][1] = ReversiEngine.WHITE
        var game = ReversiGame(board, ReversiEngine.WHITE, over = false)
        game = ReversiEngine.play(game, 2, 0)
        assertEquals(ReversiEngine.WHITE, game.turn)
        assertTrue(game.over)
        assertEquals(ReversiEngine.WHITE, game.winner)
    }

    @Test
    fun `play flips stones and undo restores the snapshot`() {
        var game = ReversiEngine.newGame()
        val opening = ReversiEngine.legalMoves(game.board, ReversiEngine.BLACK).first()
        game = ReversiEngine.play(game, opening.first, opening.second)
        assertEquals(ReversiEngine.BLACK, game.board[opening.first][opening.second])
        assertTrue(ReversiEngine.pieceCount(game.board, ReversiEngine.BLACK) > 2)

        val back = ReversiEngine.undo(game)
        assertBoardEquals(ReversiEngine.newBoard(), back.board)
        assertEquals(ReversiEngine.BLACK, back.turn)
        assertTrue(back.history.isEmpty())
        assertFalse(back.over)
    }

    @Test
    fun `ai search never mutates the source board`() {
        val board = ReversiEngine.newBoard()
        val before = board.map { it.copyOf() }
        ReversiEngine.bestMove(board, ReversiEngine.BLACK, ReversiLevel.HARD, Random(1))
        before.forEachIndexed { r, row -> assertTrue(row.contentEquals(board[r])) }
    }

    @Test
    fun `stats group results per difficulty`() {
        val stats = ReversiEngine.statsFrom(listOf("hard|black" to 1, "hard|black" to 0, "easy|white" to 2))
        assertEquals(1, stats["hard"]!!.wins)
        assertEquals(1, stats["hard"]!!.losses)
        assertEquals(2, stats["hard"]!!.played)
        assertEquals(1, stats["easy"]!!.draws)
    }
}
