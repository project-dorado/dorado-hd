package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.HeartsEngine
import com.heretek.dorado_hd.ui.apps.games.HeartsLevel
import com.heretek.dorado_hd.ui.apps.games.HeartsMode
import com.heretek.dorado_hd.ui.apps.games.HeartsPass
import com.heretek.dorado_hd.ui.apps.games.HeartsSeat
import com.heretek.dorado_hd.ui.apps.games.HeartsState
import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartsRulesTest {

    private fun card(suit: SolSuit, rank: Int) = SolCard(suit, rank, true)

    private fun state(
        hands: Map<HeartsSeat, List<SolCard>>,
        trick: List<Pair<HeartsSeat, SolCard>> = emptyList(),
        ledSuit: SolSuit? = null,
        currentPlayer: HeartsSeat = HeartsSeat.SOUTH,
        heartsBroken: Boolean = true,
        done: Boolean = false,
        taken: Map<HeartsSeat, List<SolCard>> = emptyMap(),
        totals: Map<HeartsSeat, Int> = HeartsEngine.heartsSeats.associateWith { 0 },
        scores: Map<HeartsSeat, Int> = HeartsEngine.heartsSeats.associateWith { 0 },
        mode: HeartsMode = HeartsMode.STANDARD,
        trickNumber: Int = 0,
    ) = HeartsState(
        hands = heartsSeats(hands),
        trick = trick,
        ledSuit = ledSuit,
        passDirection = 0,
        currentPlayer = currentPlayer,
        scores = scores,
        heartsBroken = heartsBroken,
        done = done,
        lastTrick = null,
        pendingPassFrom = null,
        mode = mode,
        trickNumber = trickNumber,
        taken = taken,
        totals = totals,
    )

    private fun heartsSeats(hands: Map<HeartsSeat, List<SolCard>>): Map<HeartsSeat, List<SolCard>> =
        HeartsEngine.heartsSeats.associateWith { hands[it].orEmpty() }

    @Test
    fun `first trick forces the two of clubs into the lead`() {
        val s = state(
            hands = mapOf(HeartsSeat.SOUTH to listOf(card(SolSuit.CLUB, 2), card(SolSuit.HEART, 5), card(SolSuit.SPADE, 9))),
            trick = emptyList(),
            heartsBroken = true,
        )
        val legal = HeartsEngine.legalPlays(s, HeartsSeat.SOUTH)
        assertEquals(1, legal.size)
        assertEquals(2, legal.single().rank)
        assertEquals(SolSuit.CLUB, legal.single().suit)
    }

    @Test
    fun `first trick ban when void only permits zero value discards`() {
        val s = state(
            hands = mapOf(HeartsSeat.SOUTH to listOf(card(SolSuit.SPADE, 12), card(SolSuit.CLUB, 5), card(SolSuit.DIAMOND, 3))),
            trick = listOf(HeartsSeat.WEST to card(SolSuit.HEART, 7)),
            ledSuit = SolSuit.HEART,
        )
        val legal = HeartsEngine.legalPlays(s, HeartsSeat.SOUTH)
        assertEquals(2, legal.size)
        assertFalse("queen of spades must not be discarded on trick one", legal.any { it.suit == SolSuit.SPADE })
    }

    @Test
    fun `first trick following suit must still follow when only point cards are held`() {
        val s = state(
            hands = mapOf(HeartsSeat.SOUTH to listOf(card(SolSuit.HEART, 5), card(SolSuit.CLUB, 9))),
            trick = listOf(HeartsSeat.WEST to card(SolSuit.HEART, 7)),
            ledSuit = SolSuit.HEART,
        )
        val legal = HeartsEngine.legalPlays(s, HeartsSeat.SOUTH)
        assertEquals(1, legal.size)
        assertEquals(SolSuit.HEART, legal.single().suit)
    }

    @Test
    fun `wildcard values j diamonds at minus ten and seven clubs at plus seven`() {
        assertEquals(-10, HeartsEngine.cardPoints(card(SolSuit.DIAMOND, 11), HeartsMode.WILDCARD))
        assertEquals(7, HeartsEngine.cardPoints(card(SolSuit.CLUB, 7), HeartsMode.WILDCARD))
        assertEquals(0, HeartsEngine.cardPoints(card(SolSuit.DIAMOND, 11), HeartsMode.STANDARD))
        assertEquals(0, HeartsEngine.cardPoints(card(SolSuit.CLUB, 7), HeartsMode.STANDARD))
        assertEquals(13, HeartsEngine.cardPoints(card(SolSuit.SPADE, 12), HeartsMode.STANDARD))
        assertEquals(1, HeartsEngine.cardPoints(card(SolSuit.HEART, 4), HeartsMode.STANDARD))
    }

    @Test
    fun `turbo doubles hearts for a seat that has taken an ace`() {
        val s = state(
            hands = mapOf(HeartsSeat.EAST to listOf(card(SolSuit.HEART, 2))),
            trick = listOf(
                HeartsSeat.SOUTH to card(SolSuit.CLUB, 3),
                HeartsSeat.WEST to card(SolSuit.CLUB, 4),
                HeartsSeat.NORTH to card(SolSuit.CLUB, 5),
            ),
            ledSuit = SolSuit.CLUB,
            currentPlayer = HeartsSeat.EAST,
            taken = mapOf(HeartsSeat.NORTH to listOf(card(SolSuit.SPADE, 14))),
            mode = HeartsMode.TURBO,
            trickNumber = 5,
        )
        val after = HeartsEngine.play(s, card(SolSuit.HEART, 2))
        assertEquals("north takes the trick", HeartsSeat.NORTH, after.lastTrick!!.maxBy { if (it.second.suit == SolSuit.CLUB) it.second.rank else 0 }.first)
        assertEquals("doubled heart is worth two", 2, after.scores[HeartsSeat.NORTH])
    }

    @Test
    fun `shooting the moon gives opponents twenty six and shooter zero`() {
        val penalties = HeartsEngine.penaltyCards(HeartsMode.STANDARD)
        val s = state(
            hands = emptyMap(),
            done = true,
            taken = HeartsEngine.heartsSeats.associateWith { if (it == HeartsSeat.WEST) penalties else emptyList() },
        )
        val after = HeartsEngine.resolveRound(s)
        assertEquals(HeartsSeat.WEST, after.shootMoon)
        assertEquals(0, after.totals[HeartsSeat.WEST])
        assertEquals(26, after.totals[HeartsSeat.SOUTH])
        assertEquals(26, after.totals[HeartsSeat.NORTH])
        assertEquals(26, after.totals[HeartsSeat.EAST])
        assertFalse(after.gameOver)
    }

    @Test
    fun `moon doubling applies when an opponent crosses one hundred and the shooter is tied low`() {
        val penalties = HeartsEngine.penaltyCards(HeartsMode.STANDARD)
        val s = state(
            hands = emptyMap(),
            done = true,
            taken = HeartsEngine.heartsSeats.associateWith { if (it == HeartsSeat.SOUTH) penalties else emptyList() },
            totals = mapOf(
                HeartsSeat.SOUTH to 95,
                HeartsSeat.WEST to 90,
                HeartsSeat.NORTH to 69,
                HeartsSeat.EAST to 0,
            ),
        )
        val after = HeartsEngine.resolveRound(s)
        assertEquals(HeartsSeat.SOUTH, after.shootMoon)
        assertEquals("shooter doubles to -26", 69, after.totals[HeartsSeat.SOUTH])
        assertEquals(90, after.totals[HeartsSeat.WEST])
        assertEquals(69, after.totals[HeartsSeat.NORTH])
        assertEquals(0, after.totals[HeartsSeat.EAST])
        assertFalse("doubling keeps the game alive", after.gameOver)
    }

    @Test
    fun `game ends at one hundred and the lowest score wins`() {
        val s = state(
            hands = emptyMap(),
            done = true,
            scores = mapOf(
                HeartsSeat.SOUTH to 10,
                HeartsSeat.WEST to 0,
                HeartsSeat.NORTH to 0,
                HeartsSeat.EAST to 0,
            ),
            totals = mapOf(
                HeartsSeat.SOUTH to 95,
                HeartsSeat.WEST to 80,
                HeartsSeat.NORTH to 70,
                HeartsSeat.EAST to 60,
            ),
        )
        val after = HeartsEngine.resolveRound(s)
        assertTrue(after.gameOver)
        assertEquals(HeartsSeat.EAST, after.winner)
        assertEquals(105, after.totals[HeartsSeat.SOUTH])
    }

    @Test
    fun `pass rotation cycles left right across none and each deal keeps thirteen cards`() {
        var s = HeartsEngine.newGame(HeartsMode.STANDARD, Random(1))
        assertEquals(HeartsPass.LEFT.code, s.passDirection)
        val human = s.hands[HeartsSeat.SOUTH]!!.take(3)
        s = HeartsEngine.passCards(s, HeartsSeat.SOUTH, human)
        assertNull("AI passes fill in at completion", s.pendingPasses[HeartsSeat.NORTH])
        s = HeartsEngine.completePassing(s, Random(2), HeartsLevel.HARD)
        assertNull(s.pendingPassFrom)
        HeartsEngine.heartsSeats.forEach { assertEquals(13, s.hands[it]!!.size) }
        assertTrue(
            "the two of clubs holder leads the first trick",
            s.hands[s.currentPlayer]!!.any { it.suit == SolSuit.CLUB && it.rank == 2 },
        )

        val expected = listOf(HeartsPass.RIGHT, HeartsPass.ACROSS, HeartsPass.NONE, HeartsPass.LEFT)
        for (pass in expected) {
            val resolved = HeartsEngine.resolveRound(s.copy(done = true))
            s = HeartsEngine.nextRound(resolved, Random(3))
            assertEquals(pass.code, s.passDirection)
            HeartsEngine.heartsSeats.forEach { assertEquals(13, s.hands[it]!!.size) }
        }
    }

    @Test
    fun `hard ai finishes a round and plays every card exactly once`() {
        var s = HeartsEngine.completePassing(
            HeartsEngine.newGame(HeartsMode.STANDARD, Random(3)),
            Random(4),
            HeartsLevel.HARD,
        )
        var guard = 0
        while (!s.done && guard < 80) {
            val next = HeartsEngine.aiPlay(s, HeartsLevel.HARD, Random(guard))
            assertTrue("every ai step must advance the state", next != s)
            s = next
            guard++
        }
        assertTrue("round must terminate", s.done)
        assertEquals(52, s.taken.values.sumOf { it.size })
    }

    @Test
    fun `resolve round is idempotent so a finished round cannot double count`() {
        val s = state(
            hands = emptyMap(),
            done = true,
            scores = mapOf(
                HeartsSeat.SOUTH to 5,
                HeartsSeat.WEST to 0,
                HeartsSeat.NORTH to 0,
                HeartsSeat.EAST to 0,
            ),
            totals = mapOf(
                HeartsSeat.SOUTH to 30,
                HeartsSeat.WEST to 20,
                HeartsSeat.NORTH to 10,
                HeartsSeat.EAST to 0,
            ),
        )
        val once = HeartsEngine.resolveRound(s)
        val twice = HeartsEngine.resolveRound(once)
        assertTrue(once.resolved)
        assertEquals(35, once.totals[HeartsSeat.SOUTH])
        assertEquals(once.totals, twice.totals)
    }

    @Test
    fun `hearts encode decode round trips a live game`() {
        val g = HeartsEngine.completePassing(
            HeartsEngine.newGame(HeartsMode.WILDCARD, Random(7)),
            Random(8),
            HeartsLevel.EASY,
        )
        val decoded = HeartsEngine.decode(HeartsEngine.encode(g))
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(g.mode, decoded.mode)
        assertEquals(g.currentPlayer, decoded.currentPlayer)
        HeartsEngine.heartsSeats.forEach { seat -> assertEquals(g.hands[seat], decoded.hands[seat]) }
        assertEquals(g.totals, decoded.totals)
        assertEquals(g.resolved, decoded.resolved)
    }
}
