package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.PokerBetStrategy
import com.heretek.dorado_hd.ui.apps.games.PokerDifficulty
import com.heretek.dorado_hd.ui.apps.games.PokerPersonality
import com.heretek.dorado_hd.ui.apps.games.PokerSeat
import com.heretek.dorado_hd.ui.apps.games.PokerTableEngine
import com.heretek.dorado_hd.ui.apps.games.PokerTablePhase
import com.heretek.dorado_hd.ui.apps.games.PokerTableState
import com.heretek.dorado_hd.ui.apps.games.PokerTournamentEngine
import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerTableTest {

    private fun card(suit: SolSuit, rank: Int) = SolCard(suit, rank, true)

    @Test
    fun `difficulty maps to the official personality bags`() {
        fun count(d: PokerDifficulty, p: PokerPersonality) = PokerTableEngine.personalitiesFor(d).count { it == p }
        assertEquals(6, count(PokerDifficulty.EASY, PokerPersonality.NOVICE))
        assertEquals(2, count(PokerDifficulty.EASY, PokerPersonality.ROCK))
        assertEquals(2, count(PokerDifficulty.MEDIUM, PokerPersonality.MANIAC))
        assertEquals(2, count(PokerDifficulty.MEDIUM, PokerPersonality.NOVICE))
        assertEquals(2, count(PokerDifficulty.MEDIUM, PokerPersonality.SHARK))
        assertEquals(2, count(PokerDifficulty.MEDIUM, PokerPersonality.ROCK))
        assertEquals(3, count(PokerDifficulty.HARD, PokerPersonality.MANIAC))
        assertEquals(4, count(PokerDifficulty.HARD, PokerPersonality.SHARK))
        assertEquals(1, count(PokerDifficulty.HARD, PokerPersonality.ROCK))
    }

    @Test
    fun `six seat table deals a hand with blinds posted`() {
        val table = PokerTableEngine.newTable(difficulty = PokerDifficulty.MEDIUM, rng = Random(1))
        assertEquals(6, table.seats.size)
        val hand = PokerTableEngine.startHand(table, Random(2))
        assertEquals(PokerTablePhase.PREFLOP, hand.phase)
        assertEquals(1, hand.handNumber)
        assertEquals(15, hand.pot)
        assertTrue("both hole cards are dealt", hand.seats.all { it.hole.size == 2 })
        assertEquals(2, hand.seats.count { it.streetCommitted > 0 })
        assertTrue(hand.acting >= 0)
        assertTrue(PokerTableEngine.legalActions(hand, hand.acting).isNotEmpty())
    }

    @Test
    fun `heads up button posts the small blind`() {
        val table = PokerTableEngine.newTable(seatCount = 2, rng = Random(3))
        val hand = PokerTableEngine.startHand(table, Random(4))
        assertEquals(5, hand.seats[0].streetCommitted)
        assertEquals(10, hand.seats[1].streetCommitted)
        assertEquals(0, hand.acting)
        assertEquals(15, hand.pot)
    }

    @Test
    fun `side pots layer commitments and award each layer to the best eligible hand`() {
        val base = PokerTableEngine.newTable(seatCount = 4)
        fun seat(index: Int, hole: List<SolCard>, commit: Int, folded: Boolean = false) = PokerSeat(
            index = index,
            name = "s$index",
            personality = PokerPersonality.BASELINE,
            hole = hole,
            stack = 0,
            streetCommitted = 0,
            totalCommitted = commit,
            folded = folded,
            allIn = !folded,
        )
        val board = listOf(
            card(SolSuit.SPADE, 2),
            card(SolSuit.HEART, 7),
            card(SolSuit.DIAMOND, 9),
            card(SolSuit.CLUB, 11),
            card(SolSuit.SPADE, 4),
        )
        val state = base.copy(
            seats = listOf(
                seat(0, listOf(card(SolSuit.SPADE, 14), card(SolSuit.HEART, 14)), 50),
                seat(1, listOf(card(SolSuit.SPADE, 13), card(SolSuit.HEART, 13)), 100),
                seat(2, listOf(card(SolSuit.SPADE, 12), card(SolSuit.HEART, 12)), 200),
                seat(3, emptyList(), 50, folded = true),
            ),
            board = board,
            phase = PokerTablePhase.RIVER,
            pot = 400,
        )
        val pots = PokerTableEngine.sidePots(state)
        assertEquals(3, pots.size)
        assertEquals(200, pots[0].amount)
        assertEquals(listOf(0, 1, 2), pots[0].eligible)
        assertEquals(100, pots[1].amount)
        assertEquals(listOf(1, 2), pots[1].eligible)
        assertEquals(100, pots[2].amount)
        assertEquals(listOf(2), pots[2].eligible)

        val done = PokerTableEngine.settleShowdown(state)
        assertEquals("aces take the main pot", 200, done.seats[0].stack)
        assertEquals("kings take the first side pot", 100, done.seats[1].stack)
        assertEquals("the uncalled layer returns", 100, done.seats[2].stack)
        assertEquals(0, done.pot)
        assertNotNull(done.showdownText)
    }

    @Test
    fun `ai actions are always legal and hands terminate for many seeds`() {
        repeat(25) { seed ->
            val start = PokerTableEngine.startHand(
                PokerTableEngine.newTable(rng = Random(seed.toLong() + 100)),
                Random(seed.toLong() + 200),
            )
            var state: PokerTableState = start
            var guard = 0
            while (state.acting >= 0 && state.phase != PokerTablePhase.SHOWDOWN && state.phase != PokerTablePhase.DONE && guard < 200) {
                val seat = state.acting
                val legal = PokerTableEngine.legalActions(state, seat)
                val action = PokerTableEngine.aiAction(state, seat, Random(seed * 31 + guard))
                assertTrue("ai chose $action not in $legal", action.kind in legal)
                val next = PokerTableEngine.apply(state, seat, action)
                assertTrue("ai must advance the hand", next != state)
                state = next
                guard++
            }
            assertTrue("hand $seed must end", state.phase == PokerTablePhase.SHOWDOWN || state.phase == PokerTablePhase.DONE)
            assertEquals("pot must be awarded", 0, state.pot)
            assertEquals(1, state.handHistory.size)
            if (state.phase == PokerTablePhase.SHOWDOWN) assertEquals(5, state.board.size)
        }
    }

    @Test
    fun `all in runs the board out to showdown`() {
        val start = PokerTableEngine.startHand(PokerTableEngine.newTable(seatCount = 2, rng = Random(9)), Random(10))
        val acting = start.acting
        val shoved = PokerTableEngine.apply(start, acting, com.heretek.dorado_hd.ui.apps.games.TableAction(com.heretek.dorado_hd.ui.apps.games.TableActionKind.ALL_IN))
        val other = shoved.acting
        val called = PokerTableEngine.apply(shoved, other, com.heretek.dorado_hd.ui.apps.games.TableAction(com.heretek.dorado_hd.ui.apps.games.TableActionKind.CALL))
        val done = PokerTableEngine.autoPlayHand(called, Random(11))
        assertEquals(PokerTablePhase.SHOWDOWN, done.phase)
        assertEquals(5, done.board.size)
        assertEquals(0, done.pot)
    }

    @Test
    fun `bet sizing floors to the lowest chip in play and scales with the pot`() {
        val state = PokerTableEngine.startHand(PokerTableEngine.newTable(rng = Random(12)), Random(13))
        val small = PokerTableEngine.betAmount(state.copy(pot = 20), 1, PokerBetStrategy.BET)
        val big = PokerTableEngine.betAmount(state.copy(pot = 400), 1, PokerBetStrategy.BET)
        assertTrue(big > small)
        assertEquals(0, big % state.minChip)
        assertTrue(big >= state.bigBlind)
        assertTrue(big <= PokerTableEngine.maxRaiseTo(state, 1))
    }

    @Test
    fun `maniac promotes the hand bucket above baseline`() {
        val base = PokerTableEngine.newTable(seatCount = 2)
        val board = listOf(card(SolSuit.SPADE, 2), card(SolSuit.HEART, 7), card(SolSuit.DIAMOND, 9))
        val hole = listOf(card(SolSuit.CLUB, 9), card(SolSuit.SPADE, 3))
        fun withPersonality(p: PokerPersonality) = base.copy(
            seats = base.seats.map { if (it.index == 1) it.copy(personality = p, hole = hole) else it },
            board = board,
            phase = PokerTablePhase.FLOP,
        )
        val baseline = PokerTableEngine.bucket(withPersonality(PokerPersonality.BASELINE), 1)
        val maniac = PokerTableEngine.bucket(withPersonality(PokerPersonality.MANIAC), 1)
        assertTrue(maniac.ordinal >= baseline.ordinal)
    }

    @Test
    fun `busted seats are eliminated instead of being dealt in`() {
        val table = PokerTableEngine.newTable(seatCount = 4, rng = Random(21))
        val withBust = table.copy(seats = table.seats.map { if (it.index == 2) it.copy(stack = 0) else it })
        val hand = PokerTableEngine.startHand(withBust, Random(22))
        assertTrue("zero stack seat is out", hand.seats[2].out)
        assertEquals("only three seats are dealt in", 3, hand.seats.count { it.hole.size == 2 })
    }

    @Test
    fun `tournament bankroll gates buy ins and pays fifty thirty twenty`() {
        var t = PokerTournamentEngine.newTournament(500)
        assertTrue(PokerTournamentEngine.canEnter(t, 0))
        assertFalse(PokerTournamentEngine.canEnter(t, 6))
        t = PokerTournamentEngine.enter(t, 0)
        assertEquals(500, t.bankroll)
        t = PokerTournamentEngine.settle(t, 1)
        assertTrue(t.completed.contains(0))
        assertTrue(PokerTournamentEngine.canEnter(t, 1))
        t = PokerTournamentEngine.enter(t, 1)
        assertEquals(250, t.bankroll)
        assertEquals(listOf(1500, 900, 600), PokerTournamentEngine.payouts(1))
        t = PokerTournamentEngine.settle(t, 1)
        assertEquals(1750, t.bankroll)
        assertFalse(PokerTournamentEngine.canEnter(t, 1))
    }
}
