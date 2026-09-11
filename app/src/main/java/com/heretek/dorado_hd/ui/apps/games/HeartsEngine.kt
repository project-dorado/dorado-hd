package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/* ============================================================ */
/*                              Hearts                             */
/* ============================================================ */

enum class HeartsSeat { SOUTH, WEST, NORTH, EAST }

/** The three shipped modes (Hearts!GameManager.GameMode). */
enum class HeartsMode { STANDARD, WILDCARD, TURBO }

/** Only Easy and Hard are selectable in the shipped setup UI. */
enum class HeartsLevel { EASY, HARD }

/**
 * Pass direction cycle: Left -> Right -> Across -> None -> Left
 * (Hearts!GameManager.PassStyle). The legacy `passDirection` int is kept as
 * the wire format so existing states stay valid.
 */
enum class HeartsPass(val code: Int) {
    LEFT(-1), RIGHT(1), ACROSS(2), NONE(0);

    companion object {
        fun fromCode(code: Int): HeartsPass = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** One completed trick, retained so the Hard AI can infer who is void where. */
data class HeartsTrickRecord(
    val leader: HeartsSeat,
    val plays: List<Pair<HeartsSeat, SolCard>>,
    val winner: HeartsSeat,
)

data class HeartsState(
    val hands: Map<HeartsSeat, List<SolCard>>,
    val trick: List<Pair<HeartsSeat, SolCard>>,
    val ledSuit: SolSuit?,
    val passDirection: Int,    // -1 = left, 0 = none, 1 = right, 2 = across
    val currentPlayer: HeartsSeat,
    val scores: Map<HeartsSeat, Int>,   // points taken this round
    val heartsBroken: Boolean,
    val done: Boolean,                  // round complete (hands empty)
    val lastTrick: List<Pair<HeartsSeat, SolCard>>?,
    val pendingPassFrom: HeartsSeat?,
    // --- Wave 2 fidelity additions (all defaulted: legacy constructors keep working) ---
    val mode: HeartsMode = HeartsMode.STANDARD,
    val roundNumber: Int = 0,
    val trickNumber: Int = 0,            // completed tricks this round; 0 = first trick
    val taken: Map<HeartsSeat, List<SolCard>> = emptyMap(),
    val trickHistory: List<HeartsTrickRecord> = emptyList(),
    val totals: Map<HeartsSeat, Int> = emptyMap(),
    val pendingPasses: Map<HeartsSeat, List<SolCard>> = emptyMap(),
    val shootMoon: HeartsSeat? = null,
    val gameOver: Boolean = false,
    val winner: HeartsSeat? = null,
    val resolved: Boolean = false,
)

object HeartsEngine {
    val heartsSeats: List<HeartsSeat> = listOf(HeartsSeat.SOUTH, HeartsSeat.WEST, HeartsSeat.NORTH, HeartsSeat.EAST)

    private val PASS_CYCLE = listOf(HeartsPass.LEFT, HeartsPass.RIGHT, HeartsPass.ACROSS, HeartsPass.NONE)

    private fun next(s: HeartsSeat): HeartsSeat = when (s) {
        HeartsSeat.SOUTH -> HeartsSeat.WEST
        HeartsSeat.WEST -> HeartsSeat.NORTH
        HeartsSeat.NORTH -> HeartsSeat.EAST
        HeartsSeat.EAST -> HeartsSeat.SOUTH
    }

    /** Direction-coded pass target (Hearts!GameManager.PassCards). */
    fun passTarget(from: HeartsSeat, direction: Int): HeartsSeat? = when (HeartsPass.fromCode(direction)) {
        HeartsPass.LEFT -> when (from) {
            HeartsSeat.SOUTH -> HeartsSeat.EAST
            HeartsSeat.EAST -> HeartsSeat.NORTH
            HeartsSeat.NORTH -> HeartsSeat.WEST
            HeartsSeat.WEST -> HeartsSeat.SOUTH
        }
        HeartsPass.RIGHT -> next(from)
        HeartsPass.ACROSS -> when (from) {
            HeartsSeat.SOUTH -> HeartsSeat.NORTH
            HeartsSeat.NORTH -> HeartsSeat.SOUTH
            HeartsSeat.WEST -> HeartsSeat.EAST
            HeartsSeat.EAST -> HeartsSeat.WEST
        }
        HeartsPass.NONE -> null
    }

    private fun dealHands(rng: Random): Map<HeartsSeat, List<SolCard>> {
        val deck = SolSuit.entries.flatMap { s -> (2..14).map { r -> SolCard(s, r, true) } }.shuffled(rng)
        val hands = mutableMapOf<HeartsSeat, MutableList<SolCard>>()
        heartsSeats.forEach { hands[it] = mutableListOf() }
        deck.forEachIndexed { i, c -> hands[heartsSeats[i % 4]]!!.add(c) }
        hands.forEach { (_, list) -> list.sortBy { c -> c.suit.ordinal * 100 + c.rank } }
        return hands
    }

    private fun holderOfTwoOfClubs(hands: Map<HeartsSeat, List<SolCard>>): HeartsSeat =
        heartsSeats.firstOrNull { seat -> hands[seat].orEmpty().any { it.suit == SolSuit.CLUB && it.rank == 2 } }
            ?: HeartsSeat.SOUTH

    fun newGame(rng: Random = Random.Default): HeartsState = newGame(HeartsMode.STANDARD, rng)

    fun newGame(mode: HeartsMode, rng: Random = Random.Default): HeartsState {
        val hands = dealHands(rng)
        return HeartsState(
            hands = hands,
            trick = emptyList(),
            ledSuit = null,
            passDirection = HeartsPass.LEFT.code,
            currentPlayer = holderOfTwoOfClubs(hands),
            scores = heartsSeats.associateWith { 0 },
            heartsBroken = false,
            done = false,
            lastTrick = null,
            pendingPassFrom = HeartsSeat.SOUTH,
            mode = mode,
            roundNumber = 0,
            trickNumber = 0,
            taken = heartsSeats.associateWith { emptyList() },
            totals = heartsSeats.associateWith { 0 },
        )
    }

    /** Every penalty card for the mode (the moon only counts positive points). */
    fun penaltyCards(mode: HeartsMode): List<SolCard> {
        val cards = mutableListOf<SolCard>()
        (2..14).forEach { cards += SolCard(SolSuit.HEART, it, true) }
        cards += SolCard(SolSuit.SPADE, 12, true)
        if (mode == HeartsMode.WILDCARD) cards += SolCard(SolSuit.CLUB, 7, true)
        return cards
    }

    /**
     * Point value of a card (Hearts!Deck.AssignPointValues). Wildcard adds
     * J-diamonds = -10 and 7-clubs = +7; Turbo doubles hearts for a seat that
     * has taken an Ace.
     */
    fun cardPoints(card: SolCard, mode: HeartsMode, doubleHearts: Boolean = false): Int = when {
        card.suit == SolSuit.HEART -> if (doubleHearts) 2 else 1
        card.suit == SolSuit.SPADE && card.rank == 12 -> 13
        mode == HeartsMode.WILDCARD && card.suit == SolSuit.DIAMOND && card.rank == 11 -> -10
        mode == HeartsMode.WILDCARD && card.suit == SolSuit.CLUB && card.rank == 7 -> 7
        else -> 0
    }

    /** Cards the player may play (Hearts!Hand.ValidCardsToLead / ValidCardsToFollow). */
    fun legalPlays(state: HeartsState, seat: HeartsSeat): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (hand.isEmpty()) return emptyList()
        val firstTrick = state.trickNumber == 0
        if (state.trick.isEmpty()) {
            // The 2 of clubs must lead the first trick.
            if (firstTrick && hand.any { it.suit == SolSuit.CLUB && it.rank == 2 }) {
                return listOf(SolCard(SolSuit.CLUB, 2, true))
            }
            if (state.heartsBroken) return hand
            val nonHearts = hand.filter { it.suit != SolSuit.HEART }
            return if (nonHearts.isNotEmpty()) nonHearts else hand
        }
        val followSuit = hand.filter { it.suit == state.ledSuit }
        if (followSuit.isNotEmpty()) {
            if (!firstTrick) return followSuit
            val zero = followSuit.filter { cardPoints(it, state.mode) == 0 }
            return if (zero.isNotEmpty()) zero else followSuit
        }
        if (!firstTrick) return hand
        // Void on trick one: only zero-value discards are legal when available.
        val zero = hand.filter { cardPoints(it, state.mode) == 0 }
        return if (zero.isNotEmpty()) zero else hand
    }

    fun play(state: HeartsState, card: SolCard): HeartsState {
        require(card in legalPlays(state, state.currentPlayer)) { "illegal hearts play: $card" }
        val seat = state.currentPlayer
        val newHands = state.hands.toMutableMap()
        val hand = newHands[seat]!!.toMutableList()
        hand.remove(card)
        newHands[seat] = hand
        val newTrick = state.trick + (seat to card)
        val ledSuit = state.ledSuit ?: card.suit
        val broken = state.heartsBroken || card.suit == SolSuit.HEART
        if (newTrick.size < 4) {
            return state.copy(
                hands = newHands,
                trick = newTrick,
                ledSuit = ledSuit,
                currentPlayer = next(seat),
                heartsBroken = broken,
            )
        }
        // Trick complete: highest of the led suit wins.
        val winner = newTrick.maxBy { p -> if (p.second.suit == ledSuit) p.second.rank else 0 }
        val taken = state.taken.toMutableMap()
        val winnerTaken = taken[winner.first].orEmpty() + newTrick.map { it.second }
        taken[winner.first] = winnerTaken
        val doubleHearts = state.mode == HeartsMode.TURBO && winnerTaken.any { it.rank == 14 }
        val trickPoints = newTrick.sumOf { (_, c) -> cardPoints(c, state.mode, doubleHearts) }
        val newScores = state.scores.toMutableMap()
        newScores[winner.first] = (newScores[winner.first] ?: 0) + trickPoints
        val record = HeartsTrickRecord(newTrick.first().first, newTrick, winner.first)
        val allHandsEmpty = heartsSeats.all { newHands[it]!!.isEmpty() }
        return state.copy(
            hands = newHands,
            trick = emptyList(),
            ledSuit = null,
            currentPlayer = winner.first,
            heartsBroken = broken,
            scores = newScores,
            lastTrick = newTrick,
            done = allHandsEmpty,
            trickNumber = state.trickNumber + 1,
            taken = taken,
            trickHistory = state.trickHistory + record,
        )
    }

    /**
     * Record a seat's three pass picks. Cards stay in hand until
     * [completePassing] applies every seat's picks simultaneously, so a seat
     * can never pass a card it just received.
     */
    fun passCards(state: HeartsState, fromSeat: HeartsSeat, cards: List<SolCard>): HeartsState {
        require(cards.size == 3) { "must pass exactly three cards" }
        require(cards.distinct().size == 3) { "pass picks must be distinct" }
        require(cards.all { it in state.hands[fromSeat].orEmpty() }) { "pass picks must be held" }
        return state.copy(pendingPasses = state.pendingPasses + (fromSeat to cards))
    }

    /**
     * Complete the passing phase: fill in AI picks for every seat that has not
     * recorded a pass, then move all picks simultaneously and hand the lead to
     * the 2-clubs holder.
     */
    fun completePassing(state: HeartsState, rng: Random, level: HeartsLevel = HeartsLevel.EASY): HeartsState {
        if (HeartsPass.fromCode(state.passDirection) == HeartsPass.NONE) {
            val leader = holderOfTwoOfClubs(state.hands)
            return state.copy(pendingPassFrom = null, pendingPasses = emptyMap(), currentPlayer = leader, trickNumber = 0)
        }
        val picks = heartsSeats.associateWith { seat ->
            state.pendingPasses[seat] ?: aiPass(state, seat, level, rng)
        }
        picks.values.forEach { require(it.size == 3 && it.distinct().size == 3) }
        val newHands = state.hands.mapValues { (_, list) -> list.toMutableList() }.toMutableMap()
        heartsSeats.forEach { seat -> newHands[seat]!!.removeAll(picks.getValue(seat).toSet()) }
        heartsSeats.forEach { seat ->
            val target = passTarget(seat, state.passDirection) ?: seat
            newHands[target]!!.addAll(picks.getValue(seat))
        }
        newHands.forEach { (_, list) -> list.sortBy { it.suit.ordinal * 100 + it.rank } }
        return state.copy(
            hands = newHands,
            pendingPasses = emptyMap(),
            pendingPassFrom = null,
            currentPlayer = holderOfTwoOfClubs(newHands),
            trickNumber = 0,
        )
    }

    /**
     * Shooting the moon and 100-point game end (Hearts!GameManager.ResolveRound).
     * Normally the shooter scores 0 and opponents +26; when an opponent would be
     * pushed to >=100 and the shooter is not the sole lowest, the alternative
     * doubling resolution applies (shooter -26, others 0).
     */
    fun resolveRound(state: HeartsState): HeartsState {
        if (state.gameOver || state.resolved || !state.done) return state
        val shooter = if (state.done) {
            val penalties = penaltyCards(state.mode)
            val shooters = heartsSeats.filter { seat ->
                val mine = state.taken[seat].orEmpty()
                penalties.all { p -> mine.any { it.suit == p.suit && it.rank == p.rank } }
            }
            shooters.singleOrNull()
        } else null
        val adjusted: Map<HeartsSeat, Int> = if (shooter == null) {
            heartsSeats.associateWith { state.scores[it] ?: 0 }
        } else {
            val normalTotals = heartsSeats.associateWith { (state.totals[it] ?: 0) + if (it == shooter) 0 else 26 }
            val opponentOver = heartsSeats.any { it != shooter && (normalTotals[it] ?: 0) >= 100 }
            val shooterSoleLowest = heartsSeats.all { it == shooter || (normalTotals[shooter] ?: 0) < (normalTotals[it] ?: 0) }
            if (opponentOver && !shooterSoleLowest) {
                heartsSeats.associateWith { if (it == shooter) -26 else 0 }
            } else {
                heartsSeats.associateWith { if (it == shooter) 0 else 26 }
            }
        }
        val newTotals = heartsSeats.associateWith { (state.totals[it] ?: 0) + (adjusted[it] ?: 0) }
        val over = newTotals.values.any { it >= 100 }
        val low = newTotals.values.min()
        val lowest = heartsSeats.filter { newTotals[it] == low }
        return state.copy(
            scores = adjusted,
            totals = newTotals,
            shootMoon = shooter,
            gameOver = over,
            winner = if (over && lowest.size == 1) lowest.single() else null,
            resolved = true,
        )
    }

    /** Deal the next round, rotating the pass direction Left->Right->Across->None. */
    fun nextRound(state: HeartsState, rng: Random = Random.Default): HeartsState {
        if (!state.done || state.gameOver) return state
        val round = state.roundNumber + 1
        val direction = PASS_CYCLE[round % PASS_CYCLE.size].code
        val hands = dealHands(rng)
        val passing = HeartsPass.fromCode(direction) != HeartsPass.NONE
        return state.copy(
            hands = hands,
            trick = emptyList(),
            ledSuit = null,
            passDirection = direction,
            currentPlayer = holderOfTwoOfClubs(hands),
            scores = heartsSeats.associateWith { 0 },
            heartsBroken = false,
            done = false,
            lastTrick = null,
            pendingPassFrom = if (passing) HeartsSeat.SOUTH else null,
            roundNumber = round,
            trickNumber = 0,
            taken = heartsSeats.associateWith { emptyList() },
            trickHistory = emptyList(),
            pendingPasses = emptyMap(),
            shootMoon = null,
            resolved = false,
        )
    }

    /* ------------------------------ AI ------------------------------ */

    /** Suits each seat has shown void in, inferred from completed tricks. */
    fun knownVoids(state: HeartsState): Map<HeartsSeat, Set<SolSuit>> {
        val voids = heartsSeats.associateWith { mutableSetOf<SolSuit>() }
        for (trick in state.trickHistory) {
            val led = trick.plays.firstOrNull()?.second?.suit ?: continue
            for ((seat, card) in trick.plays) {
                if (card.suit != led) voids.getValue(seat) += led
            }
        }
        return voids
    }

    /** Medium/Hard shoot-the-moon detection (Hearts!MediumAIPlayer.IdentifyAbilityToShoot). */
    fun canShootTheMoon(state: HeartsState, seat: HeartsSeat): Boolean {
        val hand = state.hands[seat].orEmpty()
        if (hand.size < 8) return false
        val aces = hand.count { it.rank == 14 }
        val highHearts = hand.count { it.suit == SolSuit.HEART && it.rank >= 12 }
        val spadeLords = hand.count { it.suit == SolSuit.SPADE && it.rank >= 13 }
        return (aces >= 3) || (aces >= 2 && highHearts >= 2) || (aces >= 2 && spadeLords >= 2)
    }

    /** Strategic pass picks (Easy: three highest; Hard: guard Q-spades / J-diamonds). */
    fun aiPass(state: HeartsState, seat: HeartsSeat, level: HeartsLevel, rng: Random = Random.Default): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (hand.size < 3) return hand
        val picks = when (level) {
            HeartsLevel.EASY -> hand.sortedByDescending { it.rank }.take(3)
            HeartsLevel.HARD -> {
                val danger = mutableListOf<SolCard>()
                val queen = hand.firstOrNull { it.suit == SolSuit.SPADE && it.rank == 12 }
                val jack = hand.firstOrNull { it.suit == SolSuit.DIAMOND && it.rank == 11 }
                val spades = hand.count { it.suit == SolSuit.SPADE }
                // Shed the queen when spades are short, the J-diamonds when
                // diamonds are shallow, then dump the shortest weak suit.
                if (queen != null && spades <= 4) danger += queen
                if (state.mode == HeartsMode.WILDCARD && jack != null && danger.size < 3) danger += jack
                val orderedSuits = SolSuit.entries
                    .filter { it != SolSuit.HEART }
                    .sortedBy { s -> hand.count { it.suit == s } }
                for (suit in orderedSuits) {
                    if (danger.size >= 3) break
                    val low = hand.filter { it.suit == suit && !danger.contains(it) }
                        .sortedByDescending { it.rank }
                    for (c in low) {
                        if (danger.size >= 3) break
                        danger += c
                    }
                }
                (danger + hand.filter { it !in danger }).take(3)
            }
        }
        return picks.distinct().take(3).ifEmpty { hand.take(3) }
    }

    /** Choose a card for [seat]; bounded, deterministic given [rng]. */
    fun aiChoose(state: HeartsState, seat: HeartsSeat, level: HeartsLevel, rng: Random = Random.Default): SolCard? {
        val legal = legalPlays(state, seat)
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal.first()
        return when (level) {
            HeartsLevel.EASY -> if (state.trick.isEmpty()) easyLead(state, legal) else easyFollow(state, legal)
            HeartsLevel.HARD -> if (state.trick.isEmpty()) hardLead(state, seat, legal) else hardFollow(state, seat, legal)
        }
    }

    private fun easyLead(state: HeartsState, legal: List<SolCard>): SolCard {
        val shortest = legal.groupBy { it.suit }.minBy { it.value.size }.value
        val safe = shortest.filter { cardPoints(it, state.mode) == 0 }
        return (safe.ifEmpty { shortest }).minBy { it.rank }
    }

    private fun easyFollow(state: HeartsState, legal: List<SolCard>): SolCard {
        val safe = legal.filter { cardPoints(it, state.mode) == 0 }
        return (safe.ifEmpty { legal }).minBy { it.rank }
    }

    private fun hardLead(state: HeartsState, seat: HeartsSeat, legal: List<SolCard>): SolCard {
        if (canShootTheMoon(state, seat)) {
            // Run aces first to pull the point cards, preferring clubs/spades.
            val ace = legal.filter { it.rank == 14 }.minByOrNull { it.suit.ordinal }
            if (ace != null) return ace
            return legal.maxBy { it.rank }
        }
        val voids = knownVoids(state)
        // Avoid leading a suit an opponent is known void in (they would shed
        // points safely); fall back to the Easy shortest-suit lead.
        val group = legal.groupBy { it.suit }
        val safeGroups = group.filter { (suit, _) ->
            heartsSeats.none { it != seat && (voids[it]?.contains(suit) == true) }
        }
        val pool = (safeGroups.ifEmpty { group }).minBy { it.value.size }.value
        val safe = pool.filter { cardPoints(it, state.mode) == 0 }
        return (safe.ifEmpty { pool }).minBy { it.rank }
    }

    private fun hardFollow(state: HeartsState, seat: HeartsSeat, legal: List<SolCard>): SolCard {
        val led = state.ledSuit
        val leaderCard = state.trick.maxByOrNull { (_, c) -> if (c.suit == led) c.rank else 0 }?.second
        val pointsOnTable = state.trick.sumOf { cardPoints(it.second, state.mode) }
        val following = led != null && legal.any { it.suit == led }
        if (following) {
            val winningRank = if (leaderCard?.suit == led) leaderCard.rank else 0
            val winners = legal.filter { it.rank > winningRank }
            if (pointsOnTable > 0 && winners.isNotEmpty()) return winners.minBy { it.rank }
            if (pointsOnTable > 0 && canShootTheMoon(state, seat)) return legal.maxBy { it.rank }
            return legal.minBy { it.rank }
        }
        // Dumping: shed the biggest positive point card on a high trick that is
        // already lost, otherwise duck with a low safe card.
        val danger = legal.filter { cardPoints(it, state.mode) > 0 }.maxByOrNull { cardPoints(it, state.mode) }
        val safe = legal.filter { cardPoints(it, state.mode) == 0 }
        return when {
            danger != null && leaderCard != null && leaderCard.rank >= 12 -> danger
            safe.isNotEmpty() -> safe.minBy { it.rank }
            danger != null -> danger
            else -> legal.minBy { it.rank }
        }
    }

    /** Advance one AI turn (legacy surface: one random-ish Easy seat). */
    fun aiPlay(state: HeartsState, rng: Random): HeartsState = aiPlay(state, HeartsLevel.EASY, rng)

    fun aiPlay(state: HeartsState, level: HeartsLevel, rng: Random = Random.Default): HeartsState {
        if (state.done || state.gameOver || state.pendingPassFrom != null) return state
        val seat = state.currentPlayer
        val pick = aiChoose(state, seat, level, rng) ?: return state
        return play(state, pick)
    }

    /* --------------------------- persistence --------------------------- */

    private fun cardCode(c: SolCard): String = "${c.suit.ordinal}-${c.rank}-${if (c.faceUp) 1 else 0}"

    private fun parseCard(code: String): SolCard? {
        val parts = code.split('-')
        if (parts.size != 3) return null
        val suit = SolSuit.entries.getOrNull(parts[0].toIntOrNull() ?: return null) ?: return null
        val rank = parts[1].toIntOrNull() ?: return null
        if (rank !in 2..14) return null
        return SolCard(suit, rank, parts[2] == "1")
    }

    private fun cardsCsv(cards: List<SolCard>): String = cards.joinToString(" ") { cardCode(it) }

    private fun parseCards(csv: String): List<SolCard> =
        csv.split(' ').mapNotNull { if (it.isBlank()) null else parseCard(it) }

    private fun handMapCsv(map: Map<HeartsSeat, List<SolCard>>): String =
        heartsSeats.joinToString(";") { seat -> cardsCsv(map[seat].orEmpty()) }

    private fun intMapCsv(map: Map<HeartsSeat, Int>): String =
        heartsSeats.joinToString(",") { (map[it] ?: 0).toString() }

    fun encode(state: HeartsState): String {
        val hands = handMapCsv(state.hands)
        val taken = handMapCsv(state.taken)
        val trick = state.trick.joinToString(" ") { (seat, card) -> "${seat.name}:${cardCode(card)}" }
        return listOf(
            "hearts1",
            state.mode.name,
            state.roundNumber.toString(),
            state.passDirection.toString(),
            state.trickNumber.toString(),
            if (state.heartsBroken) "1" else "0",
            if (state.done) "1" else "0",
            state.currentPlayer.name,
            state.pendingPassFrom?.name ?: "-",
            intMapCsv(state.scores),
            intMapCsv(state.totals),
            hands,
            taken,
            trick,
            if (state.gameOver) "1" else "0",
            state.winner?.name ?: "-",
            if (state.resolved) "1" else "0",
        ).joinToString("|")
    }

    fun decode(blob: String): HeartsState? {
        return try {
            val p = blob.split('|')
            if (p.size < 17 || p[0] != "hearts1") return null
            fun ints(csv: String) = csv.split(',').map { it.toInt() }
            val hands = p[11].split(';')
            val taken = p[12].split(';')
            val scores = ints(p[9])
            val totals = ints(p[10])
            if (hands.size != 4 || taken.size != 4 || scores.size != 4 || totals.size != 4) return null
            val trick = p[13].split(' ').mapNotNull { token ->
                if (token.isBlank()) return@mapNotNull null
                val sep = token.indexOf(':')
                if (sep <= 0) return@mapNotNull null
                val seat = HeartsSeat.entries.firstOrNull { it.name == token.substring(0, sep) } ?: return@mapNotNull null
                val card = parseCard(token.substring(sep + 1)) ?: return@mapNotNull null
                seat to card
            }
            HeartsState(
                hands = heartsSeats.withIndex().associate { (i, seat) -> seat to parseCards(hands[i]) },
                trick = trick,
                ledSuit = trick.firstOrNull()?.second?.suit,
                passDirection = p[3].toIntOrNull() ?: 0,
                currentPlayer = HeartsSeat.entries.firstOrNull { it.name == p[7] } ?: HeartsSeat.SOUTH,
                scores = heartsSeats.withIndex().associate { (i, seat) -> seat to scores[i] },
                heartsBroken = p[5] == "1",
                done = p[6] == "1",
                lastTrick = null,
                pendingPassFrom = HeartsSeat.entries.firstOrNull { it.name == p[8] },
                mode = HeartsMode.entries.firstOrNull { it.name == p[1] } ?: HeartsMode.STANDARD,
                roundNumber = p[2].toIntOrNull() ?: 0,
                trickNumber = p[4].toIntOrNull() ?: 0,
                taken = heartsSeats.withIndex().associate { (i, seat) -> seat to parseCards(taken[i]) },
                totals = heartsSeats.withIndex().associate { (i, seat) -> seat to totals[i] },
                gameOver = p[14] == "1",
                winner = HeartsSeat.entries.firstOrNull { it.name == p[15] },
                resolved = p[16] == "1",
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Latest cumulative total; kept for callers that only track one number. */
    fun bestTotal(state: HeartsState): Int = state.totals.values.minOrNull() ?: state.scores.values.minOrNull() ?: 0

    /** Convenience for tests/UI: seats ordered by cumulative score ascending. */
    fun standings(state: HeartsState): List<HeartsSeat> =
        heartsSeats.sortedBy { state.totals[it] ?: state.scores[it] ?: 0 }
}
