package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import com.heretek.dorado_hd.ui.apps.games.SpadesEngine
import com.heretek.dorado_hd.ui.apps.games.SpadesLevel
import com.heretek.dorado_hd.ui.apps.games.SpadesSeat
import com.heretek.dorado_hd.ui.apps.games.SpadesState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpadesRulesTest {

    private fun base() = SpadesEngine.newGame(Random(11))

    private fun card(suit: SolSuit, rank: Int) = SolCard(suit, rank, true)

    private fun tricks(vararg counts: Int): Map<SpadesSeat, List<SolCard>> =
        SpadesSeat.entries.withIndex().associate { (i, seat) ->
            seat to List(counts.getOrElse(i) { 0 }) { n -> card(SolSuit.CLUB, 2 + n) }
        }

    private fun bids(vararg values: Int): Map<SpadesSeat, Int> =
        SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to values.getOrElse(i) { 0 } }

    @Test
    fun `nil bid pays plus one hundred when the bidder takes no trick`() {
        val s = base().copy(
            bids = bids(0, 0, 0, 0),
            nilBid = SpadesSeat.entries.associateWith { it == SpadesSeat.SOUTH },
            tricksTaken = tricks(0, 0, 0, 0),
            done = true,
        )
        val scored = SpadesEngine.scoreRound(s)
        assertEquals(100 to 0, scored.score)
    }

    @Test
    fun `nil bid costs minus one hundred when the bidder takes a trick`() {
        val s = base().copy(
            bids = bids(0, 0, 0, 0),
            nilBid = SpadesSeat.entries.associateWith { it == SpadesSeat.SOUTH },
            tricksTaken = tricks(1, 0, 0, 0),
            done = true,
        )
        val scored = SpadesEngine.scoreRound(s)
        assertEquals(-100 to 0, scored.score)
    }

    @Test
    fun `blind nil pays two hundred and short blind nil costs two hundred`() {
        val good = base().copy(
            bids = bids(0, 0, 0, 0),
            nilBid = SpadesSeat.entries.associateWith { it == SpadesSeat.SOUTH },
            blindNil = SpadesSeat.entries.associateWith { it == SpadesSeat.SOUTH },
            tricksTaken = tricks(0, 0, 0, 0),
            done = true,
        )
        assertEquals(200 to 0, SpadesEngine.scoreRound(good).score)
        val bad = good.copy(tricksTaken = tricks(2, 0, 0, 0))
        assertEquals(-200 to 0, SpadesEngine.scoreRound(bad).score)
    }

    @Test
    fun `undertrick penalty is minus ten times the team bid`() {
        val s = base().copy(
            bids = bids(2, 3, 5, 0),
            tricksTaken = tricks(2, 2, 3, 2),
            done = true,
        )
        // Team S-N bid 7, made 5 -> -70. Team W-E bid 3, made 4 -> +30 and 1 bag.
        val scored = SpadesEngine.scoreRound(s)
        assertEquals(-70 to 30, scored.score)
        assertEquals(0 to 1, scored.teamBags)
    }

    @Test
    fun `bag out fires when score mod ten plus the round bags reaches ten`() {
        val s = base().copy(
            score = 0 to 0,
            teamBags = 9 to 0,
            bids = bids(2, 0, 0, 0),
            tricksTaken = tricks(5, 0, 0, 0),
            done = true,
        )
        // S-N: 20 for the bid + 3 bags -> 9 + 3 = 12 >= 10 -> -100, bags 2.
        val scored = SpadesEngine.scoreRound(s)
        assertEquals(-80 to 0, scored.score)
        assertEquals(2 to 0, scored.teamBags)
    }

    @Test
    fun `game target ends at five hundred and at minus five hundred`() {
        val win = base().copy(
            score = 490 to 0,
            bids = bids(1, 0, 0, 0),
            tricksTaken = tricks(1, 0, 0, 0),
            done = true,
            target = 500,
        )
        val won = SpadesEngine.scoreRound(win)
        assertTrue(won.gameOver)
        assertEquals(0, won.winnerTeam)
        assertEquals(500, won.score.first)

        val loss = base().copy(
            score = 0 to -490,
            bids = bids(0, 0, 0, 7),
            tricksTaken = tricks(0, 0, 0, 1),
            done = true,
            target = 500,
        )
        val lost = SpadesEngine.scoreRound(loss)
        assertTrue(lost.gameOver)
        assertEquals(0, lost.winnerTeam)
        assertTrue(lost.score.second <= -500)
    }

    @Test
    fun `three hundred mode ends the game at three hundred`() {
        val s = base().copy(
            score = 290 to 0,
            bids = bids(1, 0, 0, 0),
            tricksTaken = tricks(1, 0, 0, 0),
            done = true,
            target = 300,
        )
        val scored = SpadesEngine.scoreRound(s)
        assertTrue(scored.gameOver)
        assertEquals(300, scored.score.first)
    }

    @Test
    fun `easy and hard bids stay bounded and deterministic`() {
        val s = base()
        SpadesSeat.entries.forEach { seat ->
            val easy = SpadesEngine.aiBid(s, seat, SpadesLevel.EASY, Random(1))
            assertTrue(easy.value in 0..6)
            val hard = SpadesEngine.aiBid(s, seat, SpadesLevel.HARD, Random(1))
            assertTrue(hard.value in 0..10)
            assertEquals(hard, SpadesEngine.aiBid(s, seat, SpadesLevel.HARD, Random(1)))
        }
    }

    @Test
    fun `autoPlayRound terminates with every card accounted for`() {
        val start = SpadesEngine.newGame(Random(2))
        val done = SpadesEngine.autoPlayRound(start, SpadesLevel.HARD, Random(3))
        assertTrue("round must finish without stalling", done.done)
        assertEquals(52, done.tricksTaken.values.sumOf { it.size })
        assertEquals(4, done.bids.values.count { true })
    }

    @Test
    fun `scoring then dealing rotates the dealer and keeps the score`() {
        val done = SpadesEngine.autoPlayRound(SpadesEngine.newGame(Random(5)), SpadesLevel.EASY, Random(6))
        val scored = SpadesEngine.scoreRound(done)
        assertEquals(1, scored.history.size)
        assertEquals("scoring twice must not double count", scored.score, SpadesEngine.scoreRound(scored).score)
        val next = SpadesEngine.nextRound(scored, Random(7))
        assertEquals(1, next.roundNumber)
        assertEquals(next.dealer, next.currentPlayer)
        assertEquals(next.dealer, next.bidder)
        assertEquals(scored.score, next.score)
        assertFalse(next.biddingDone)
        assertEquals(13, next.hands[SpadesSeat.SOUTH]!!.size)
        assertEquals(52, next.hands.values.sumOf { it.size })
    }

    @Test
    fun `unbroken spades may not be led unless the hand is all spades`() {
        val mixed = base().copy(
            hands = SpadesSeat.entries.associateWith { seat ->
                if (seat == SpadesSeat.SOUTH) listOf(card(SolSuit.SPADE, 14), card(SolSuit.CLUB, 5))
                else emptyList()
            },
            currentPlayer = SpadesSeat.SOUTH,
            biddingDone = true,
        )
        val legal = SpadesEngine.legalPlays(mixed, SpadesSeat.SOUTH)
        assertEquals(listOf(SolSuit.CLUB), legal.map { it.suit })

        val allSpades = mixed.copy(hands = mixed.hands + (SpadesSeat.SOUTH to listOf(card(SolSuit.SPADE, 14), card(SolSuit.SPADE, 3))))
        assertEquals(2, SpadesEngine.legalPlays(allSpades, SpadesSeat.SOUTH).size)
    }

    @Test
    fun `spades encode decode round trips a scored game`() {
        val done = SpadesEngine.autoPlayRound(SpadesEngine.newGame(Random(8)), SpadesLevel.HARD, Random(9))
        val scored = SpadesEngine.scoreRound(done)
        val decoded = SpadesEngine.decode(SpadesEngine.encode(scored))
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(scored.score, decoded.score)
        assertEquals(scored.teamBags, decoded.teamBags)
        assertEquals(scored.roundNumber, decoded.roundNumber)
        assertEquals(scored.roundScored, decoded.roundScored)
        SpadesSeat.entries.forEach { seat ->
            assertEquals(scored.hands[seat], decoded.hands[seat])
            assertEquals(scored.tricksTaken[seat]?.size, decoded.tricksTaken[seat]?.size)
        }
    }
}
