package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/* ============================================================ */
/*                          Solitaire (Klondike)                  */
/* ============================================================ */

enum class SolSuit {
    SPADE, HEART, CLUB, DIAMOND;
    val red: Boolean get() = this == HEART || this == DIAMOND
}

data class SolCard(val suit: SolSuit, val rank: Int, val faceUp: Boolean = false) {
    override fun toString(): String = "${suit.name.take(1)}${rank}"
}

data class Pile(
    val name: String,
    val cards: List<SolCard>,
)

/** How many cards one tap on the deck turns over (official Options.DealType). */
enum class SolDealType { ONE, THREE }

/** Standard vs Vegas scoring (official ScoringMethod). */
enum class SolScoringMethod { STANDARD, VEGAS }

enum class SolPileKind { TABLEAU, FOUNDATION, WASTE, STOCK }

data class SolPileRef(val kind: SolPileKind, val index: Int = 0)

/** A single-card move (foundation moves are always one card). */
data class SolMove(val from: SolPileRef, val to: SolPileRef, val count: Int = 1)

enum class SolHintKind { MOVE, FLIP, DRAW }

data class SolHint(
    val kind: SolHintKind,
    val from: SolPileRef? = null,
    val to: SolPileRef? = null,
    val count: Int = 1,
)

/**
 * Immutable Klondike table. [history] holds snapshots pushed by every mutation
 * so [SolitaireEngine.undo] can walk the action log; snapshots are flattened
 * (a pushed snapshot never carries its own history).
 */
data class SolitaireState(
    val tableau: List<List<SolCard>>,
    val foundations: List<List<SolCard>>,
    val stock: List<SolCard>,
    val waste: List<SolCard>,
    val deal: SolDealType = SolDealType.THREE,
    val scoring: SolScoringMethod = SolScoringMethod.STANDARD,
    val score: Int = 0,
    val recycles: Int = 0,
    val moves: Int = 0,
    val elapsedSeconds: Int = 0,
    val won: Boolean = false,
    val timeBonusApplied: Boolean = false,
    val history: List<SolitaireState> = emptyList(),
)

object SolitaireEngine {
    // Standard scoring (official Solitaire!Scoring.Standard).
    const val STANDARD_DRAW_BONUS = 5
    const val STANDARD_FLIP_BONUS = 5
    const val STANDARD_FOUNDATION_BONUS = 10
    const val STANDARD_PULLBACK_PENALTY = 15
    const val STANDARD_RECYCLE_PENALTY = 20

    // Vegas scoring (official Solitaire!Scoring.Vegas).
    const val VEGAS_STAKE = 52
    const val VEGAS_FOUNDATION_BONUS = 5
    const val VEGAS_PULLBACK_PENALTY = 5
    const val VEGAS_MAX_RECYCLES = 3
    const val VEGAS_CLAMP = 9999

    /** Time bonus is only paid for wins of at least 30 seconds (Standard). */
    fun timeBonus(seconds: Int): Int = if (seconds >= 30) 700_000 / seconds else 0

    /** Legacy pile-list deal kept for compatibility. */
    fun newGame(rng: Random = Random.Default): List<Pile> {
        val deck = (SolSuit.values()).flatMap { s -> (1..13).map { r -> SolCard(s, r, false) } }.shuffled(rng)
        val tabs = mutableListOf<Pile>()
        var idx = 0
        for (c in 0 until 7) {
            val size = c + 1
            val cards = deck.subList(idx, idx + size).mapIndexed { i, card ->
                if (i == size - 1) card.copy(faceUp = true) else card
            }
            tabs += Pile("tab$c", cards)
            idx += size
        }
        val stock = Pile("stock", deck.subList(idx, deck.size))
        val waste = Pile("waste", emptyList())
        val founds = SolSuit.values().map { Pile("found${it.name}", emptyList()) }
        return tabs + stock + waste + founds
    }

    /** A fresh game with a Standard-or-Vegas score ledger. */
    fun deal(
        deal: SolDealType = SolDealType.THREE,
        scoring: SolScoringMethod = SolScoringMethod.STANDARD,
        rng: Random = Random.Default,
    ): SolitaireState {
        val deck = (SolSuit.values()).flatMap { s -> (1..13).map { r -> SolCard(s, r, false) } }.shuffled(rng)
        val tableau = mutableListOf<List<SolCard>>()
        var idx = 0
        for (c in 0 until 7) {
            val size = c + 1
            tableau += deck.subList(idx, idx + size).mapIndexed { i, card ->
                if (i == size - 1) card.copy(faceUp = true) else card
            }
            idx += size
        }
        return SolitaireState(
            tableau = tableau,
            foundations = SolSuit.values().map { emptyList() },
            stock = deck.subList(idx, deck.size),
            waste = emptyList(),
            deal = deal,
            scoring = scoring,
            score = if (scoring == SolScoringMethod.VEGAS) -VEGAS_STAKE else 0,
        )
    }

    private fun snapshot(state: SolitaireState): SolitaireState = state.copy(history = emptyList())

    fun undo(state: SolitaireState): SolitaireState =
        state.history.lastOrNull() ?: state

    /** Face-up the newly exposed card after the top card leaves a column. */
    fun exposeTop(cards: List<SolCard>): List<SolCard> {
        val last = cards.lastOrNull() ?: return cards
        return if (!last.faceUp) cards.dropLast(1) + last.copy(faceUp = true) else cards
    }

    fun legalMove(card: SolCard, onto: SolCard?): Boolean {
        if (card.faceUp && onto != null && onto.faceUp && card.suit.red != onto.suit.red && card.rank == onto.rank - 1) return true
        return card.faceUp && onto == null && card.rank == 13 // empty column accepts King
    }

    fun toFoundation(card: SolCard, foundationTop: SolCard?): Boolean {
        if (!card.faceUp) return false
        return if (foundationTop == null) card.rank == 1
        else card.suit == foundationTop.suit && card.rank == foundationTop.rank + 1
    }

    fun allFoundationsFull(state: SolitaireState): Boolean = state.foundations.all { it.size == 13 }

    /** True when [cards] from [from] to the end is a face-up descending alt-color run. */
    fun isRun(cards: List<SolCard>, from: Int): Boolean {
        if (from !in cards.indices) return false
        for (i in from until cards.size) {
            val c = cards[i]
            if (!c.faceUp) return false
            if (i > from) {
                val prev = cards[i - 1]
                if (prev.rank != c.rank + 1 || prev.suit.red == c.suit.red) return false
            }
        }
        return true
    }

    fun cardsFor(state: SolitaireState, ref: SolPileRef): List<SolCard> = when (ref.kind) {
        SolPileKind.TABLEAU -> state.tableau.getOrElse(ref.index) { emptyList() }
        SolPileKind.FOUNDATION -> state.foundations.getOrElse(ref.index) { emptyList() }
        SolPileKind.WASTE -> state.waste
        SolPileKind.STOCK -> state.stock
    }

    fun canMove(state: SolitaireState, from: SolPileRef, to: SolPileRef, count: Int): Boolean {
        if (state.won || count < 1) return false
        if (from.kind == SolPileKind.STOCK || to.kind != SolPileKind.TABLEAU && to.kind != SolPileKind.FOUNDATION) return false
        if (from.kind == SolPileKind.FOUNDATION && to.kind != SolPileKind.TABLEAU) return false
        val source = cardsFor(state, from)
        if (source.size < count) return false
        if (count > 1 && from.kind != SolPileKind.TABLEAU) return false
        val moved = source.takeLast(count)
        if (!isRun(source, source.size - count)) return false
        return when (to.kind) {
            SolPileKind.FOUNDATION -> {
                val f = state.foundations.getOrNull(to.index) ?: return false
                // Each foundation slot belongs to one suit, in SolSuit order.
                if (moved[0].suit != SolSuit.values().getOrNull(to.index)) false
                else toFoundation(moved[0], f.lastOrNull())
            }
            SolPileKind.TABLEAU -> {
                if (from.kind == SolPileKind.TABLEAU && from.index == to.index) return false
                val t = state.tableau.getOrNull(to.index) ?: return false
                legalMove(moved[0], t.lastOrNull()?.takeIf { it.faceUp })
            }
        }
    }

    /**
     * Applies [from] -> [to] (multi-card tableau runs allowed) and records the
     * pre-move snapshot on [SolitaireState.history]. Illegal moves return the
     * receiver unchanged (same instance).
     */
    fun move(state: SolitaireState, from: SolPileRef, to: SolPileRef, count: Int = 1): SolitaireState {
        if (!canMove(state, from, to, count)) return state
        val source = cardsFor(state, from)
        val moved = source.takeLast(count)

        var tableau = state.tableau
        var foundations = state.foundations
        var waste = state.waste
        var flipped = false

        when (from.kind) {
            SolPileKind.TABLEAU -> {
                val col = tableau[from.index].dropLast(count)
                val exposed = if (col.isNotEmpty() && !col.last().faceUp) {
                    flipped = true
                    col.dropLast(1) + col.last().copy(faceUp = true)
                } else {
                    col
                }
                tableau = tableau.toMutableList().also { it[from.index] = exposed }
            }
            SolPileKind.WASTE -> waste = waste.dropLast(1)
            SolPileKind.FOUNDATION -> foundations = foundations.toMutableList().also {
                it[from.index] = it[from.index].dropLast(1)
            }
            SolPileKind.STOCK -> return state
        }

        when (to.kind) {
            SolPileKind.TABLEAU -> tableau = tableau.toMutableList().also { it[to.index] = it[to.index] + moved }
            SolPileKind.FOUNDATION -> foundations = foundations.toMutableList().also { it[to.index] = it[to.index] + moved }
            else -> return state
        }

        var score = state.score
        if (state.scoring == SolScoringMethod.STANDARD) {
            if (from.kind == SolPileKind.WASTE && to.kind == SolPileKind.TABLEAU) score += STANDARD_DRAW_BONUS
            if (to.kind == SolPileKind.FOUNDATION) score += STANDARD_FOUNDATION_BONUS
            if (from.kind == SolPileKind.FOUNDATION && to.kind == SolPileKind.TABLEAU) score -= STANDARD_PULLBACK_PENALTY
            if (flipped) score += STANDARD_FLIP_BONUS
            score = score.coerceAtLeast(0)
        } else {
            if (to.kind == SolPileKind.FOUNDATION) score += VEGAS_FOUNDATION_BONUS
            if (from.kind == SolPileKind.FOUNDATION) score -= VEGAS_PULLBACK_PENALTY
            score = score.coerceIn(-VEGAS_CLAMP, VEGAS_CLAMP)
        }

        val won = foundations.all { it.size == 13 }
        return state.copy(
            tableau = tableau,
            foundations = foundations,
            waste = waste,
            score = score,
            moves = state.moves + 1,
            won = won,
            history = state.history + snapshot(state),
        )
    }

    /** Flip the face-down top card of tableau [index] (Standard pays +5). */
    fun flip(state: SolitaireState, index: Int): SolitaireState {
        val col = state.tableau.getOrNull(index) ?: return state
        val top = col.lastOrNull() ?: return state
        if (top.faceUp) return state
        var score = state.score
        if (state.scoring == SolScoringMethod.STANDARD) score = (score + STANDARD_FLIP_BONUS).coerceAtLeast(0)
        val tableau = state.tableau.toMutableList().also {
            it[index] = col.dropLast(1) + top.copy(faceUp = true)
        }
        return state.copy(
            tableau = tableau,
            score = score,
            history = state.history + snapshot(state),
        )
    }

    fun canRecycle(state: SolitaireState): Boolean = when (state.scoring) {
        SolScoringMethod.STANDARD -> state.waste.isNotEmpty()
        SolScoringMethod.VEGAS -> state.waste.isNotEmpty() && state.recycles < VEGAS_MAX_RECYCLES
    }

    /** Draw without history/score side effects; null when nothing can move. */
    private fun drawRaw(state: SolitaireState): SolitaireState? {
        if (state.stock.isEmpty()) {
            if (state.waste.isEmpty()) return null
            return when (state.scoring) {
                SolScoringMethod.STANDARD -> {
                    val recycles = state.recycles + 1
                    val penalty = if (recycles >= 3) STANDARD_RECYCLE_PENALTY else 0
                    state.copy(
                        stock = state.waste.reversed().map { it.copy(faceUp = false) },
                        waste = emptyList(),
                        recycles = recycles,
                        score = (state.score - penalty).coerceAtLeast(0),
                    )
                }
                SolScoringMethod.VEGAS -> {
                    if (state.recycles >= VEGAS_MAX_RECYCLES) return null
                    state.copy(
                        stock = state.waste.reversed().map { it.copy(faceUp = false) },
                        waste = emptyList(),
                        recycles = state.recycles + 1,
                    )
                }
            }
        }
        val n = if (state.deal == SolDealType.ONE) 1 else 3
        val drawn = state.stock.takeLast(n)
        return state.copy(
            stock = state.stock.dropLast(n),
            waste = state.waste + drawn.reversed().map { it.copy(faceUp = true) },
        )
    }

    /** Tap the deck: turn one or three cards; recycle when exhausted. */
    fun draw(state: SolitaireState): SolitaireState {
        if (state.won) return state
        val next = drawRaw(state) ?: return state
        return next.copy(
            moves = state.moves + 1,
            history = state.history + snapshot(state),
        )
    }

    /**
     * A "good" move for the hint glow: foundations first, then waste, then
     * runs that expose a face-down card, then any run, then a flip, then a draw.
     */
    fun hint(state: SolitaireState): SolHint? {
        if (state.won) return null
        findCardHint(state)?.let { return it }
        for (t in state.tableau.indices) {
            if (state.tableau[t].lastOrNull()?.faceUp == false) {
                return SolHint(SolHintKind.FLIP, from = SolPileRef(SolPileKind.TABLEAU, t))
            }
        }
        if (state.stock.isNotEmpty() || canRecycle(state)) {
            return SolHint(SolHintKind.DRAW, from = SolPileRef(SolPileKind.STOCK))
        }
        return null
    }

    private fun foundationAccepts(state: SolitaireState, f: Int, card: SolCard): Boolean {
        if (SolSuit.values().getOrNull(f) != card.suit) return false
        return toFoundation(card, state.foundations.getOrNull(f)?.lastOrNull())
    }

    private fun findCardHint(state: SolitaireState): SolHint? {
        // Waste -> foundation.
        state.waste.lastOrNull()?.let { top ->
            state.foundations.indices.firstOrNull { foundationAccepts(state, it, top) }?.let {
                return SolHint(SolHintKind.MOVE, SolPileRef(SolPileKind.WASTE), SolPileRef(SolPileKind.FOUNDATION, it), 1)
            }
        }
        // Tableau top -> foundation.
        for (t in state.tableau.indices) {
            val top = state.tableau[t].lastOrNull() ?: continue
            if (!top.faceUp) continue
            state.foundations.indices.firstOrNull { foundationAccepts(state, it, top) }?.let {
                return SolHint(SolHintKind.MOVE, SolPileRef(SolPileKind.TABLEAU, t), SolPileRef(SolPileKind.FOUNDATION, it), 1)
            }
        }
        // Waste -> tableau.
        state.waste.lastOrNull()?.let { top ->
            for (t in state.tableau.indices) {
                if (legalMove(top, state.tableau[t].lastOrNull()?.takeIf { it.faceUp })) {
                    return SolHint(SolHintKind.MOVE, SolPileRef(SolPileKind.WASTE), SolPileRef(SolPileKind.TABLEAU, t), 1)
                }
            }
        }
        // Tableau runs -> tableau; prefer moves that expose a face-down card.
        for (exposeOnly in listOf(true, false)) {
            for (t in state.tableau.indices) {
                val col = state.tableau[t]
                for (start in col.indices) {
                    if (!isRun(col, start)) continue
                    if (exposeOnly && !(start > 0 && !col[start - 1].faceUp)) continue
                    if (!exposeOnly && start == 0) continue // pointless full-column move
                    val moved = col[start]
                    for (d in state.tableau.indices) {
                        if (d == t) continue
                        if (legalMove(moved, state.tableau[d].lastOrNull()?.takeIf { it.faceUp })) {
                            return SolHint(
                                SolHintKind.MOVE,
                                SolPileRef(SolPileKind.TABLEAU, t),
                                SolPileRef(SolPileKind.TABLEAU, d),
                                col.size - start,
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * True when no move remains even after drawing through the whole deck. A
     * full pass that finds no card move and leaves every tableau card face-up
     * means the deal is stuck.
     */
    fun isGameOver(state: SolitaireState): Boolean {
        if (state.won) return false
        if (findCardHint(state) != null) return false
        if (state.tableau.any { col -> col.lastOrNull()?.faceUp == false }) return false
        if (state.stock.isEmpty() && state.waste.isEmpty()) return true
        var s = state
        val budget = 2 * (state.stock.size + state.waste.size) + 6
        repeat(budget) {
            s = drawRaw(s) ?: return true
            if (findCardHint(s) != null) return false
            if (s.tableau.any { col -> col.lastOrNull()?.faceUp == false }) return false
        }
        return true
    }

    /** Auto-complete is only offered from a trivially winnable position. */
    fun canAutoComplete(state: SolitaireState): Boolean {
        if (state.won || state.stock.isNotEmpty() || state.waste.isNotEmpty()) return false
        if (state.tableau.any { col -> col.any { !it.faceUp } }) return false
        return autoComplete(state).won
    }

    /**
     * Repeatedly sends eligible cards to the foundations. The whole sequence is
     * one undo step (the pre-sequence snapshot is pushed exactly once).
     */
    fun autoComplete(state: SolitaireState): SolitaireState {
        if (state.won) return state
        if (state.tableau.any { col -> col.any { !it.faceUp } }) return state
        if (state.stock.isNotEmpty() || state.waste.isNotEmpty()) return state
        var s = state
        var guard = 0
        while (!s.won && guard++ < 60) {
            val mv = firstFoundationMove(s) ?: break
            s = move(s, mv.from, mv.to, 1)
        }
        if (s === state) return state
        return s.copy(history = state.history + snapshot(state))
    }

    private fun firstFoundationMove(state: SolitaireState): SolMove? {
        for (t in state.tableau.indices) {
            val top = state.tableau[t].lastOrNull() ?: continue
            if (!top.faceUp) continue
            val f = top.suit.ordinal
            if (!foundationAccepts(state, f, top)) continue
            return SolMove(SolPileRef(SolPileKind.TABLEAU, t), SolPileRef(SolPileKind.FOUNDATION, f), 1)
        }
        return null
    }

    /** Win settlement: Standard pays a time bonus; Vegas does not. */
    fun complete(state: SolitaireState, elapsedSeconds: Int): SolitaireState {
        if (!state.won || state.timeBonusApplied) return state
        var score = state.score
        if (state.scoring == SolScoringMethod.STANDARD) {
            score = (score + timeBonus(elapsedSeconds)).coerceAtLeast(0)
        }
        return state.copy(score = score, elapsedSeconds = elapsedSeconds, timeBonusApplied = true)
    }

    /* ---------------- persistence: resumable deal ---------------- */

    fun encode(state: SolitaireState): String {
        fun pile(cards: List<SolCard>): String = cards.joinToString(".") { card(it) }
        val fields = mutableListOf(
            "v1",
            state.deal.name,
            state.scoring.name,
            state.score.toString(),
            state.recycles.toString(),
            state.moves.toString(),
            state.elapsedSeconds.toString(),
            if (state.won) "1" else "0",
            pile(state.stock),
            pile(state.waste),
        )
        state.foundations.forEach { fields += pile(it) }
        state.tableau.forEach { fields += pile(it) }
        return fields.joinToString(";")
    }

    fun decode(text: String?): SolitaireState? {
        if (text.isNullOrBlank()) return null
        return try {
            val f = text.split(";")
            if (f.size < 21 || f[0] != "v1") return null
            var i = 8
            val stock = cards(f[i++])
            val waste = cards(f[i++])
            val foundations = (0 until 4).map { cards(f[i++]) }
            val tableau = (0 until 7).map { cards(f[i++]) }
            SolitaireState(
                tableau = tableau,
                foundations = foundations,
                stock = stock,
                waste = waste,
                deal = SolDealType.valueOf(f[1]),
                scoring = SolScoringMethod.valueOf(f[2]),
                score = f[3].toInt(),
                recycles = f[4].toInt(),
                moves = f[5].toInt(),
                elapsedSeconds = f[6].toInt(),
                won = f[7] == "1",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun card(c: SolCard): String = "${c.suit.name.take(1)}${c.rank}${if (c.faceUp) "U" else "D"}"

    private fun cards(text: String): List<SolCard> {
        if (text.isEmpty()) return emptyList()
        return text.split(".").map { token ->
            val suit = when (token[0]) {
                'S' -> SolSuit.SPADE
                'H' -> SolSuit.HEART
                'C' -> SolSuit.CLUB
                'D' -> SolSuit.DIAMOND
                else -> error("bad suit")
            }
            val faceUp = token.last() == 'U'
            val rank = token.substring(1, token.length - 1).toInt()
            SolCard(suit, rank, faceUp)
        }
    }
}
