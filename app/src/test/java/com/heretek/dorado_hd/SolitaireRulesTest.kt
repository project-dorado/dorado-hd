package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SolCard
import com.heretek.dorado_hd.ui.apps.games.SolDealType
import com.heretek.dorado_hd.ui.apps.games.SolHintKind
import com.heretek.dorado_hd.ui.apps.games.SolPileKind
import com.heretek.dorado_hd.ui.apps.games.SolPileRef
import com.heretek.dorado_hd.ui.apps.games.SolScoringMethod
import com.heretek.dorado_hd.ui.apps.games.SolSuit
import com.heretek.dorado_hd.ui.apps.games.SolitaireEngine
import com.heretek.dorado_hd.ui.apps.games.SolitaireState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SolitaireRulesTest {

    private fun card(suit: SolSuit, rank: Int, faceUp: Boolean = true) = SolCard(suit, rank, faceUp)

    private fun stateOf(
        tableau: List<List<SolCard>> = List(7) { emptyList() },
        foundations: List<List<SolCard>> = List(4) { emptyList() },
        stock: List<SolCard> = emptyList(),
        waste: List<SolCard> = emptyList(),
        deal: SolDealType = SolDealType.THREE,
        scoring: SolScoringMethod = SolScoringMethod.STANDARD,
        score: Int = 0,
        recycles: Int = 0,
    ) = SolitaireState(
        tableau = tableau,
        foundations = foundations,
        stock = stock,
        waste = waste,
        deal = deal,
        scoring = scoring,
        score = score,
        recycles = recycles,
    )

    /* ---------------- Standard scoring ---------------- */

    @Test
    fun `standard pays five for waste to tableau`() {
        val s = stateOf(
            tableau = listOf(listOf(card(SolSuit.SPADE, 8)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            waste = listOf(card(SolSuit.HEART, 7)),
        )
        val next = SolitaireEngine.move(s, SolPileRef(SolPileKind.WASTE), SolPileRef(SolPileKind.TABLEAU, 0), 1)
        assertNotSame(s, next)
        assertEquals(5, next.score)
        assertEquals(7, next.tableau[0].last().rank)
    }

    @Test
    fun `standard pays ten for foundation foundation move and clamps pullback`() {
        val tableau = List(7) { emptyList<SolCard>() }.toMutableList()
        tableau[1] = listOf(card(SolSuit.HEART, 1))
        val s = stateOf(tableau = tableau)
        val sent = SolitaireEngine.move(s, SolPileRef(SolPileKind.TABLEAU, 1), SolPileRef(SolPileKind.FOUNDATION, SolSuit.HEART.ordinal), 1)
        assertEquals(10, sent.score)
        assertEquals(1, sent.foundations[SolSuit.HEART.ordinal].size)

        // Ace can only be pulled back onto an opposite-color two.
        val pulled = stateOf(
            tableau = listOf(listOf(card(SolSuit.SPADE, 2)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            foundations = List(4) { i -> if (i == SolSuit.HEART.ordinal) listOf(card(SolSuit.HEART, 1)) else emptyList() },
            score = 20,
        )
        val back = SolitaireEngine.move(pulled, SolPileRef(SolPileKind.FOUNDATION, SolSuit.HEART.ordinal), SolPileRef(SolPileKind.TABLEAU, 0), 1)
        assertEquals(5, back.score)

        val clamped = pulled.copy(score = 5)
        assertEquals(0, SolitaireEngine.move(clamped, SolPileRef(SolPileKind.FOUNDATION, SolSuit.HEART.ordinal), SolPileRef(SolPileKind.TABLEAU, 0), 1).score)
    }

    @Test
    fun `standard pays five for auto flip and penalizes only past the third pass`() {
        val s = stateOf(
            tableau = listOf(
                listOf(card(SolSuit.CLUB, 9, faceUp = false), card(SolSuit.SPADE, 7), card(SolSuit.HEART, 6)),
                listOf(card(SolSuit.DIAMOND, 8)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        val moved = SolitaireEngine.move(s, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.TABLEAU, 1), 2)
        assertTrue(moved.tableau[0].last().faceUp)
        assertEquals(5, moved.score)

        val free = stateOf(waste = listOf(card(SolSuit.CLUB, 4)), recycles = 1, score = 100)
        assertEquals(100, SolitaireEngine.draw(free).score)
        val charged = stateOf(waste = listOf(card(SolSuit.CLUB, 4)), recycles = 2, score = 100)
        val recycled = SolitaireEngine.draw(charged)
        assertEquals(3, recycled.recycles)
        assertEquals(80, recycled.score)
    }

    @Test
    fun `vegas stakes fifty two and clamps at the cap`() {
        val dealt = SolitaireEngine.deal(SolDealType.THREE, SolScoringMethod.VEGAS, Random(3))
        assertEquals(-52, dealt.score)

        val tableau = List(7) { emptyList<SolCard>() }.toMutableList()
        tableau[0] = listOf(card(SolSuit.SPADE, 1))
        val capped = stateOf(tableau = tableau, scoring = SolScoringMethod.VEGAS, score = SolitaireEngine.VEGAS_CLAMP)
        val sent = SolitaireEngine.move(capped, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.FOUNDATION, SolSuit.SPADE.ordinal), 1)
        assertEquals(SolitaireEngine.VEGAS_CLAMP, sent.score)
    }

    @Test
    fun `vegas refuses a fourth deck recycle`() {
        val s = stateOf(
            scoring = SolScoringMethod.VEGAS,
            recycles = SolitaireEngine.VEGAS_MAX_RECYCLES,
            waste = listOf(card(SolSuit.CLUB, 4)),
        )
        val next = SolitaireEngine.draw(s)
        assertSame("deal button must disable after the third recycle", s, next)
    }

    @Test
    fun `vegas pays five per foundation card and five back`() {
        val tableau = List(7) { emptyList<SolCard>() }.toMutableList()
        tableau[0] = listOf(card(SolSuit.SPADE, 1))
        val s = stateOf(tableau = tableau, scoring = SolScoringMethod.VEGAS, score = -52)
        val sent = SolitaireEngine.move(s, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.FOUNDATION, SolSuit.SPADE.ordinal), 1)
        assertEquals(-47, sent.score)
    }

    @Test
    fun `standard win pays the time bonus and vegas does not`() {
        assertEquals(0, SolitaireEngine.timeBonus(29))
        assertEquals(700_000 / 60, SolitaireEngine.timeBonus(60))
        val full = SolSuit.values().map { suit -> (1..13).map { card(suit, it) } }
        val won = stateOf(foundations = full, score = 100, scoring = SolScoringMethod.STANDARD).copy(won = true)
        val completed = SolitaireEngine.complete(won, 60)
        assertEquals(100 + SolitaireEngine.timeBonus(60), completed.score)
        assertTrue(completed.timeBonusApplied)

        val vegasWon = stateOf(foundations = full, score = 100, scoring = SolScoringMethod.VEGAS).copy(won = true)
        assertEquals(100, SolitaireEngine.complete(vegasWon, 60).score)
    }

    /* ---------------- deal / draw ---------------- */

    @Test
    fun `three card draw exposes three with only the last drawn playable`() {
        val s = SolitaireEngine.deal(SolDealType.THREE, SolScoringMethod.STANDARD, Random(11))
        val expectedTop = s.stock[s.stock.size - 3]
        val next = SolitaireEngine.draw(s)
        assertEquals(3, next.waste.size)
        assertEquals(expectedTop.suit, next.waste.last().suit)
        assertEquals(expectedTop.rank, next.waste.last().rank)
        assertTrue(next.waste.last().faceUp)
        assertEquals(s.stock.size - 3, next.stock.size)
        assertEquals(1, next.moves)
    }

    @Test
    fun `one card draw turns a single card`() {
        val s = SolitaireEngine.deal(SolDealType.ONE, SolScoringMethod.STANDARD, Random(11))
        val expected = s.stock.last()
        val next = SolitaireEngine.draw(s)
        assertEquals(1, next.waste.size)
        assertEquals(expected.suit, next.waste.last().suit)
        assertEquals(expected.rank, next.waste.last().rank)
    }

    /* ---------------- run moves / undo ---------------- */

    @Test
    fun `multi card runs move whole and illegal runs are refused`() {
        val s = stateOf(
            tableau = listOf(
                listOf(card(SolSuit.SPADE, 7), card(SolSuit.HEART, 6), card(SolSuit.CLUB, 5)),
                listOf(card(SolSuit.DIAMOND, 8)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        assertTrue(SolitaireEngine.canMove(s, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.TABLEAU, 1), 3))
        val moved = SolitaireEngine.move(s, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.TABLEAU, 1), 3)
        assertEquals(0, moved.tableau[0].size)
        assertEquals(4, moved.tableau[1].size)

        val sameColor = stateOf(
            tableau = listOf(
                listOf(card(SolSuit.SPADE, 7), card(SolSuit.CLUB, 6)),
                listOf(card(SolSuit.DIAMOND, 8)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        assertFalse(SolitaireEngine.isRun(sameColor.tableau[0], 0))
        assertSame(sameColor, SolitaireEngine.move(sameColor, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.TABLEAU, 1), 2))
        assertFalse(SolitaireEngine.canMove(s, SolPileRef(SolPileKind.TABLEAU, 0), SolPileRef(SolPileKind.TABLEAU, 1), 4))
    }

    @Test
    fun `undo unwinds a move and a draw`() {
        val s = stateOf(
            tableau = listOf(listOf(card(SolSuit.SPADE, 8)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            waste = listOf(card(SolSuit.HEART, 7)),
            stock = listOf(card(SolSuit.CLUB, 3, faceUp = false)),
        )
        val moved = SolitaireEngine.move(s, SolPileRef(SolPileKind.WASTE), SolPileRef(SolPileKind.TABLEAU, 0), 1)
        val undone = SolitaireEngine.undo(moved)
        assertEquals(1, undone.tableau[0].size)
        assertEquals(1, undone.waste.size)
        assertEquals(0, undone.score)

        val drawn = SolitaireEngine.draw(s)
        assertEquals(2, drawn.waste.size)
        val undoneDraw = SolitaireEngine.undo(drawn)
        assertEquals(1, undoneDraw.waste.size)
        assertEquals(1, undoneDraw.stock.size)
        assertEquals(0, undoneDraw.moves)
    }

    /* ---------------- hint / game over / autocomplete ---------------- */

    @Test
    fun `hint sends an ace to its foundation before anything else`() {
        val s = stateOf(
            waste = listOf(card(SolSuit.HEART, 1)),
            stock = listOf(card(SolSuit.CLUB, 9, faceUp = false)),
        )
        val hint = SolitaireEngine.hint(s)
        assertEquals(SolHintKind.MOVE, hint?.kind)
        assertEquals(SolPileKind.FOUNDATION, hint?.to?.kind)
        assertEquals(SolSuit.HEART.ordinal, hint?.to?.index)
    }

    @Test
    fun `hint falls back to dealing when no card move exists`() {
        val s = stateOf(stock = listOf(card(SolSuit.CLUB, 9, faceUp = false)))
        val hint = SolitaireEngine.hint(s)
        assertEquals(SolHintKind.DRAW, hint?.kind)
    }

    @Test
    fun `stuck board with no deck reports game over`() {
        val s = stateOf(tableau = listOf(listOf(card(SolSuit.SPADE, 12)), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        assertNull(SolitaireEngine.hint(s))
        assertTrue(SolitaireEngine.isGameOver(s))
    }

    @Test
    fun `autocomplete finishes a trivially won position`() {
        val fullMinusKing = SolSuit.values().map { suit -> (1..12).map { card(suit, it) } }
        val kings = listOf(
            listOf(card(SolSuit.SPADE, 13)), listOf(card(SolSuit.HEART, 13)),
            listOf(card(SolSuit.CLUB, 13)), listOf(card(SolSuit.DIAMOND, 13)),
            emptyList(), emptyList(), emptyList(),
        )
        val s = stateOf(tableau = kings, foundations = fullMinusKing)
        assertTrue(SolitaireEngine.canAutoComplete(s))
        val done = SolitaireEngine.autoComplete(s)
        assertTrue(done.won)
        assertEquals(1, done.history.size)
        done.foundations.forEach { assertEquals(13, it.size) }
        done.tableau.forEach { assertTrue(it.isEmpty()) }
    }

    /* ---------------- persistence ---------------- */

    @Test
    fun `deal serialization roundtrips across moves`() {
        var s = SolitaireEngine.deal(SolDealType.THREE, SolScoringMethod.VEGAS, Random(7))
        s = SolitaireEngine.draw(s)
        s = SolitaireEngine.undo(s)
        val decoded = SolitaireEngine.decode(SolitaireEngine.encode(s))
        assertEquals(s.copy(history = emptyList()), decoded)
        assertNull(SolitaireEngine.decode("not a state"))
    }
}
