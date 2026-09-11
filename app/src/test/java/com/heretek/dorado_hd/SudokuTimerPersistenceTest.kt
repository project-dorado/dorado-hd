package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SudokuEngine
import com.heretek.dorado_hd.ui.apps.games.SudokuLevel
import com.heretek.dorado_hd.ui.apps.games.SudokuType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A-31 regression: the elapsed timer is part of the saved game, so resume no
 * longer restarts it at zero.
 */
class SudokuTimerPersistenceTest {

    private fun game() = SudokuEngine.start(
        SudokuEngine.newGame(SudokuType.MINI, SudokuLevel.EASY, seed = 7L),
        SudokuType.MINI,
        SudokuLevel.EASY,
    ).copy(elapsedSeconds = 321)

    @Test
    fun `elapsed seconds survive an encode decode round trip`() {
        val original = game()
        val restored = SudokuEngine.decode(SudokuEngine.encode(original))
        assertNotNull(restored)
        assertEquals(321, restored!!.elapsedSeconds)
        assertEquals(original.cells, restored.cells)
        assertEquals(original.type, restored.type)
        assertEquals(original.level, restored.level)
    }

    @Test
    fun `legacy blobs without an elapsed field decode as zero`() {
        val blob = SudokuEngine.encode(game())
        val legacy = blob.substringBeforeLast(';')
        val restored = SudokuEngine.decode(legacy)
        assertNotNull(restored)
        assertEquals(0, restored!!.elapsedSeconds)
    }

    @Test
    fun `garbage does not decode`() {
        assertNull(SudokuEngine.decode("v1;nonsense"))
        assertNull(SudokuEngine.decode(null))
    }
}
