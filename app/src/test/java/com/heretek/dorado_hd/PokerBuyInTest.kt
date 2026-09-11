package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.PokerTournamentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-10 regression: leaving a tournament event refunds the buy-in and clears
 * the active event so the ladder can never become permanently unaffordable.
 */
class PokerBuyInTest {

    @Test
    fun `abandon refunds the buy-in and clears the active event`() {
        val tournament = PokerTournamentEngine.newTournament(bankroll = 500)
        val entered = PokerTournamentEngine.enter(tournament, 1)
        assertEquals(250, entered.bankroll)
        assertEquals(1, entered.activeEvent)

        val left = PokerTournamentEngine.abandon(entered)
        assertEquals(500, left.bankroll)
        assertEquals(-1, left.activeEvent)
        assertTrue(PokerTournamentEngine.canEnter(left, 1))
    }

    @Test
    fun `abandon is idempotent and never refunds twice`() {
        val entered = PokerTournamentEngine.enter(PokerTournamentEngine.newTournament(1_000), 2)
        val once = PokerTournamentEngine.abandon(entered)
        val twice = PokerTournamentEngine.abandon(once)
        assertEquals(once.bankroll, twice.bankroll)
        assertEquals(1_000, twice.bankroll)
    }

    @Test
    fun `enter refuses unaffordable events and leaves the wallet untouched`() {
        val poor = PokerTournamentEngine.newTournament(bankroll = 100)
        assertFalse(PokerTournamentEngine.canEnter(poor, 2))
        val attempted = PokerTournamentEngine.enter(poor, 2)
        assertEquals(poor, attempted)
        assertEquals(100, attempted.bankroll)
    }

    @Test
    fun `repeated enter-leave cycles keep the ladder enterable`() {
        var tournament = PokerTournamentEngine.newTournament(bankroll = 250)
        repeat(5) {
            tournament = PokerTournamentEngine.enter(tournament, 1)
            assertTrue(PokerTournamentEngine.canEnter(tournament, 1).not())
            tournament = PokerTournamentEngine.abandon(tournament)
            assertTrue("bankroll must recover after leaving", PokerTournamentEngine.canEnter(tournament, 1))
        }
        assertEquals(250, tournament.bankroll)
    }
}
