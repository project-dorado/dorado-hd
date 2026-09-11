package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.HeartsEngine
import com.heretek.dorado_hd.ui.apps.games.HeartsLevel
import com.heretek.dorado_hd.ui.apps.games.HeartsMode
import com.heretek.dorado_hd.ui.apps.games.HeartsSeat
import com.heretek.dorado_hd.ui.apps.games.HeartsState
import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A-14 regression: the hearts pass picker toggles against live selection
 * state (no duplicate picks, capped at three), and an AI turn always makes
 * progress so the trick loop cannot stall.
 */
class HeartsPassToggleTest {

    private val hand = listOf(
        SolCard(SolSuit.CLUB, 2, true),
        SolCard(SolSuit.HEART, 5, true),
        SolCard(SolSuit.SPADE, 12, true),
        SolCard(SolSuit.DIAMOND, 9, true),
    )

    @Test
    fun `tapping a selected card removes it and never duplicates`() {
        var picked = emptyList<SolCard>()
        picked = HeartsEngine.togglePassPick(picked, hand[0])
        assertEquals(listOf(hand[0]), picked)
        // Tapping the same card again must undo the pick, not add a duplicate.
        picked = HeartsEngine.togglePassPick(picked, hand[0])
        assertEquals(emptyList<SolCard>(), picked)
        assertEquals(0, picked.size)
    }

    @Test
    fun `picks cap at three distinct cards`() {
        var picked = emptyList<SolCard>()
        picked = HeartsEngine.togglePassPick(picked, hand[0])
        picked = HeartsEngine.togglePassPick(picked, hand[1])
        picked = HeartsEngine.togglePassPick(picked, hand[2])
        assertEquals(3, picked.size)
        picked = HeartsEngine.togglePassPick(picked, hand[3])
        assertEquals("a fourth pick is ignored", 3, picked.size)
        assertEquals(listOf(hand[0], hand[1], hand[2]), picked)
    }

    @Test
    fun `passCards rejects duplicates but accepts the toggle result`() {
        val state = HeartsEngine.newGame(HeartsMode.STANDARD, Random(3))
        val picks = listOf(
            state.hands[HeartsSeat.SOUTH]!![0],
            state.hands[HeartsSeat.SOUTH]!![1],
            state.hands[HeartsSeat.SOUTH]!![2],
        )
        val withPass = HeartsEngine.passCards(state, HeartsSeat.SOUTH, picks)
        assertEquals(picks, withPass.pendingPasses[HeartsSeat.SOUTH])
        val duplicate = listOf(picks[0], picks[0], picks[1])
        var threw = false
        try {
            HeartsEngine.passCards(state, HeartsSeat.SOUTH, duplicate)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("duplicate picks must be rejected", threw)
    }

    @Test
    fun `ai turn advances a seat after passing`() {
        var state = HeartsEngine.newGame(HeartsMode.STANDARD, Random(11))
        state = HeartsEngine.completePassing(state, Random(11))
        val next = HeartsEngine.aiPlay(state, HeartsLevel.EASY, Random(11))
        assertNotEquals("watchdog precondition: aiPlay must move the state", state, next)
    }

    @Test
    fun `watchdog closes a stalled all-empty hand`() {
        val stalled = HeartsState(
            hands = HeartsEngine.heartsSeats.associateWith { emptyList() },
            trick = emptyList(),
            ledSuit = null,
            passDirection = 0,
            currentPlayer = HeartsSeat.NORTH,
            scores = HeartsEngine.heartsSeats.associateWith { 0 },
            heartsBroken = false,
            done = false,
            lastTrick = null,
            pendingPassFrom = null,
        )
        val advanced = HeartsEngine.forceAdvance(stalled, Random(1))
        assertTrue("the watchdog must not leave the loop stalled", advanced.done)
    }
}
