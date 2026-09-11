package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.CheckersDifficulty
import com.heretek.dorado_hd.ui.apps.games.CheckersEngine
import com.heretek.dorado_hd.ui.apps.games.CheckersMode
import com.heretek.dorado_hd.ui.apps.games.CheckersMoveRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-30 regression: the 80-ply draw clock counts every ply — human and AI —
 * and a capture resets it.
 */
class CheckersQuietPlyTest {

    private val quietMove = CheckersMoveRecord(from = 0 to 1, to = 1 to 2)
    private val captureMove = CheckersMoveRecord(
        from = 0 to 1,
        to = 2 to 3,
        captures = listOf(1 to 2),
    )

    @Test
    fun `captures reset the counter and quiet plies increment`() {
        assertEquals(1, CheckersEngine.quietPliesAfter(0, quietMove))
        assertEquals(41, CheckersEngine.quietPliesAfter(40, quietMove))
        assertEquals(0, CheckersEngine.quietPliesAfter(40, captureMove))
        assertEquals(0, CheckersEngine.quietPliesAfter(0, captureMove))
        assertTrue(quietMove.captures.isEmpty())
        assertFalse(captureMove.captures.isEmpty())
    }

    @Test
    fun `an AI-selected opening moves advances the same draw clock`() {
        var state = CheckersEngine.newGame()
        var quietPlies = 0
        val aiMove = CheckersEngine.bestMove(state, CheckersDifficulty.EASY, CheckersMode.REGULAR)
        assertTrue("opening has legal moves", aiMove != null)
        state = CheckersEngine.applyMove(state, aiMove!!)
        quietPlies = CheckersEngine.quietPliesAfter(quietPlies, aiMove)
        assertEquals("AI ply must count toward the draw clock", 1, quietPlies)
        assertTrue(aiMove.captures.isEmpty())
    }

    @Test
    fun `draw clock constant matches the device eighty plies`() {
        assertEquals(80, CheckersEngine.DRAW_QUIET_PLIES)
    }
}
