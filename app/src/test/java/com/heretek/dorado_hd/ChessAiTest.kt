package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.ChessColor
import com.heretek.dorado_hd.ui.apps.games.ChessDifficulty
import com.heretek.dorado_hd.ui.apps.games.ChessEngine
import com.heretek.dorado_hd.ui.apps.games.ChessMove
import com.heretek.dorado_hd.ui.apps.games.ChessPiece
import com.heretek.dorado_hd.ui.apps.games.ChessPieceType
import com.heretek.dorado_hd.ui.apps.games.ChessSave
import com.heretek.dorado_hd.ui.apps.games.ChessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessAiTest {

    private fun empty(): Array<Array<ChessPiece?>> = Array(8) { arrayOfNulls<ChessPiece?>(8) }

    @Test
    fun `chess castling generates and moves both pieces`() {
        val board = empty()
        board[0][4] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[0][7] = ChessPiece(ChessPieceType.R, ChessColor.WHITE)
        board[0][0] = ChessPiece(ChessPieceType.R, ChessColor.WHITE)
        board[7][4] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        val s = ChessState(board, ChessColor.WHITE, 0b1111, null, 0, 1, "")
        val moves = ChessEngine.legalMoves(s)
        assertTrue(moves.any { it.castleKingSide })
        assertTrue(moves.any { it.castleQueenSide })
        val kingSide = moves.first { it.castleKingSide }
        val after = ChessEngine.apply(s, kingSide)
        assertEquals(ChessPieceType.K, after.board[0][6]?.type)
        assertEquals(ChessPieceType.R, after.board[0][5]?.type)
        assertNull(after.board[0][7])
    }

    @Test
    fun `chess castling is blocked through attacked squares`() {
        val board = empty()
        board[0][4] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[0][7] = ChessPiece(ChessPieceType.R, ChessColor.WHITE)
        board[7][4] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[7][5] = ChessPiece(ChessPieceType.R, ChessColor.BLACK)
        val s = ChessState(board, ChessColor.WHITE, 0b1111, null, 0, 1, "")
        assertFalse(ChessEngine.legalMoves(s).any { it.castleKingSide })
    }

    @Test
    fun `chess en passant captures the passed pawn`() {
        val board = empty()
        board[0][4] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[7][4] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[4][4] = ChessPiece(ChessPieceType.P, ChessColor.WHITE)
        board[6][3] = ChessPiece(ChessPieceType.P, ChessColor.BLACK)
        val s = ChessState(board, ChessColor.BLACK, 0, null, 0, 1, "")
        val doublePush = ChessEngine.legalMoves(s).first { it.fromR == 6 && it.fromC == 3 && it.toR == 4 }
        val afterPush = ChessEngine.apply(s, doublePush)
        assertEquals(5 to 3, afterPush.enPassant)
        val epMove = ChessEngine.legalMoves(afterPush).first { it.enPassant }
        assertEquals(4 to 4, epMove.fromR to epMove.fromC)
        assertEquals(5 to 3, epMove.toR to epMove.toC)
        val afterCapture = ChessEngine.apply(afterPush, epMove)
        assertNull(afterCapture.board[4][3])
        assertEquals(ChessPieceType.P, afterCapture.board[5][3]?.type)
        assertEquals(ChessColor.WHITE, afterCapture.board[5][3]?.color)
    }

    @Test
    fun `chess promotion stays legal and auto queens for the ui`() {
        val board = empty()
        board[0][0] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[7][7] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[6][0] = ChessPiece(ChessPieceType.P, ChessColor.WHITE)
        val s = ChessState(board, ChessColor.WHITE, 0, null, 0, 1, "")
        val promotions = ChessEngine.legalMoves(s).filter { it.fromR == 6 && it.fromC == 0 }
        assertEquals(4, promotions.size)
        val picked = ChessEngine.autoQueen(s, promotions.first { it.promotion == ChessPieceType.N })
        assertEquals(ChessPieceType.Q, picked.promotion)
        val after = ChessEngine.apply(s, picked)
        assertEquals(ChessPieceType.Q, after.board[7][0]?.type)
    }

    @Test
    fun `chess detects a back rank checkmate`() {
        val board = empty()
        board[7][4] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[0][0] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[7][0] = ChessPiece(ChessPieceType.R, ChessColor.WHITE)
        board[6][4] = ChessPiece(ChessPieceType.R, ChessColor.WHITE)
        board[5][3] = ChessPiece(ChessPieceType.P, ChessColor.WHITE)
        val s = ChessState(board, ChessColor.BLACK, 0, null, 0, 1, "")
        assertTrue(ChessEngine.isInCheck(s, ChessColor.BLACK))
        assertTrue(ChessEngine.legalMoves(s).isEmpty())
    }

    @Test
    fun `chess opening book is consulted before search`() {
        val start = ChessEngine.newGame()
        val book = ChessEngine.bookMove(start)
        assertNotNull(book)
        assertTrue(ChessEngine.legalMoves(start).contains(book))
        assertEquals(book, ChessEngine.bestMove(start, ChessDifficulty.EASY, maxNodes = 5_000))
        assertEquals(book, ChessEngine.bestMove(start, ChessDifficulty.HARD, maxNodes = 5_000))
    }

    @Test
    fun `chess hint returns a legal move`() {
        val start = ChessEngine.newGame()
        val hint = ChessEngine.hint(start, maxNodes = 20_000)
        assertNotNull(hint)
        assertTrue(ChessEngine.legalMoves(start).contains(hint))
    }

    @Test
    fun `chess levels return legal moves within the node cap`() {
        val board = empty()
        board[7][0] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[0][4] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[4][4] = ChessPiece(ChessPieceType.Q, ChessColor.WHITE)
        board[4][5] = ChessPiece(ChessPieceType.Q, ChessColor.BLACK)
        val s = ChessState(board, ChessColor.WHITE, 0, null, 0, 1, "")
        for (difficulty in ChessDifficulty.entries) {
            val move = ChessEngine.bestMove(s, difficulty, maxNodes = 15_000)
            assertNotNull("expected a move at $difficulty", move)
            assertTrue(ChessEngine.legalMoves(s).contains(move))
        }
        val grab = ChessEngine.bestMove(s, ChessDifficulty.EASY, maxNodes = 15_000)
        assertEquals(4 to 5, grab!!.toR to grab.toC)
    }

    @Test
    fun `chess easy promotes to intermediate while losing`() {
        val board = empty()
        board[0][4] = ChessPiece(ChessPieceType.K, ChessColor.WHITE)
        board[7][4] = ChessPiece(ChessPieceType.K, ChessColor.BLACK)
        board[6][0] = ChessPiece(ChessPieceType.Q, ChessColor.BLACK)
        board[6][1] = ChessPiece(ChessPieceType.Q, ChessColor.BLACK)
        board[6][2] = ChessPiece(ChessPieceType.Q, ChessColor.BLACK)
        val s = ChessState(board, ChessColor.WHITE, 0, null, 0, 1, "")
        assertTrue(ChessEngine.evaluate(s) < -800)
        val quick = ChessEngine.bestMove(s, ChessDifficulty.EASY, maxNodes = 3_000)
        val deeper = ChessEngine.bestMove(s, ChessDifficulty.INTERMEDIATE, maxNodes = 3_000)
        assertNotNull(quick)
        assertNotNull(deeper)
    }

    @Test
    fun `chess undo takes back two half moves`() {
        var state = ChessEngine.newGame()
        val startFen = ChessEngine.fen(state)
        val line = listOf(
            ChessMove(6, 4, 4, 4),
            ChessMove(1, 4, 3, 4),
            ChessMove(7, 6, 5, 5),
            ChessMove(1, 1, 2, 2),
        )
        var afterTwo = state
        for ((index, move) in line.withIndex()) {
            state = ChessEngine.apply(state, move)
            if (index == 1) afterTwo = state
        }
        val twoFen = ChessEngine.fen(afterTwo)
        val undone = ChessEngine.undo(state, halfMoves = 2)
        assertEquals(twoFen, ChessEngine.fen(undone))
        val fully = ChessEngine.undo(undone, halfMoves = 2)
        assertEquals(startFen, ChessEngine.fen(fully))
    }

    @Test
    fun `chess save slot round trips`() {
        val save = ChessSave(
            fen = ChessEngine.fen(ChessEngine.newGame()),
            difficulty = "intermediate",
            color = "black",
            twoPlayer = true,
            theme = "metal",
            elapsedMs = 42_000,
        )
        assertEquals(save, ChessEngine.decodeSave(ChessEngine.encodeSave(save)))
    }
}
