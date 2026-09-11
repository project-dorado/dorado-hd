package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BOWLING_NUM_PINS
import com.heretek.dorado_hd.ui.apps.games.BowlingBall
import com.heretek.dorado_hd.ui.apps.games.BowlingEngine
import com.heretek.dorado_hd.ui.apps.games.BowlingLane
import com.heretek.dorado_hd.ui.apps.games.BowlingLength
import com.heretek.dorado_hd.ui.apps.games.BowlingMode
import com.heretek.dorado_hd.ui.apps.games.BowlingRival
import com.heretek.dorado_hd.ui.apps.games.BowlingSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A-28 regression: an in-progress game must persist its full setup (lane, ball,
 * rival, seed) and resume it instead of reseeding with picker defaults.
 */
class BowlingSaveSetupTest {

    private fun inProgressCard() = BowlingEngine.applyRoll(
        BowlingEngine.newCard(BowlingMode.EXHIBITION, BowlingLength.FULL),
        8,
    )

    @Test
    fun `save round trip keeps lane ball rival and seed`() {
        val standing = List(BOWLING_NUM_PINS) { it % 3 != 0 }
        val save = BowlingSave(
            card = inProgressCard(),
            standing = standing,
            lane = BowlingLane.ORBIT,
            ball = BowlingBall.LASER,
            rival = BowlingRival.VESPER,
            seed = 424242,
        )
        val decoded = BowlingEngine.decodeSave(BowlingEngine.encodeSave(save))
        assertNotNull(decoded)
        assertEquals(BowlingLane.ORBIT, decoded!!.lane)
        assertEquals(BowlingBall.LASER, decoded.ball)
        assertEquals(BowlingRival.VESPER, decoded.rival)
        assertEquals(424242, decoded.seed)
        assertEquals(standing, decoded.standing)
        assertEquals(listOf(8), decoded.card.frames[0].rolls)
    }

    @Test
    fun `legacy save without setup decodes to defaults`() {
        val legacy = "EXHIBITION:FULL:0:0:" + "1".repeat(BOWLING_NUM_PINS) + "|8"
        val decoded = BowlingEngine.decodeSave(legacy)
        assertNotNull(decoded)
        assertEquals(BowlingLane.PINERY, decoded!!.lane)
        assertEquals(BowlingBall.COMET, decoded.ball)
        assertEquals(BowlingRival.KESTREL, decoded.rival)
        assertEquals(0, decoded.seed)
    }

    @Test
    fun `resume restores the saved setup`() {
        val save = BowlingSave(
            card = inProgressCard(),
            standing = List(BOWLING_NUM_PINS) { true },
            lane = BowlingLane.GROTTO,
            ball = BowlingBall.JADE,
            rival = BowlingRival.MORROW,
            seed = 99,
        )
        val match = BowlingEngine.resumeMatch(
            save.card, save.standing, save.lane, save.ball, save.rival, save.seed,
        )
        assertEquals(BowlingLane.GROTTO, match.lane)
        assertEquals(BowlingBall.JADE, match.ball)
        assertEquals(BowlingRival.MORROW, match.rival)
        assertEquals(99, match.seed)
    }
}
