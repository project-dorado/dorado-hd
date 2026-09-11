package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/* ============================================================ */
/*                              Spades                              */
/* ============================================================ */

enum class SpadesSeat { SOUTH, WEST, NORTH, EAST }

/** Only Easy and Hard are selectable (Spades!SinglePlayerSetUpScreen). */
enum class SpadesLevel { EASY, HARD }

/** A resolved bid: 0 -> nil, the blind sentinel is represented by [blindNil]. */
data class SpadesBid(val value: Int, val nil: Boolean = false, val blindNil: Boolean = false)

/** One scored round for the running scoreboard (Spades!ScoreboardScreen). */
data class SpadesRoundRecord(
    val round: Int,
    val bids: Map<SpadesSeat, Int>,
    val tricks: Map<SpadesSeat, Int>,
    val teamDelta: Pair<Int, Int>,
    val teamBags: Pair<Int, Int>,
)

data class SpadesState(
    val hands: Map<SpadesSeat, List<SolCard>>,
    val bids: Map<SpadesSeat, Int>,
    val nilBid: Map<SpadesSeat, Boolean>,
    val blindNil: Map<SpadesSeat, Boolean>,
    val trick: List<Pair<SpadesSeat, SolCard>>,
    val tricksTaken: Map<SpadesSeat, List<SolCard>>,
    val currentPlayer: SpadesSeat,
    val bidder: SpadesSeat,
    val score: Pair<Int, Int>, // S+N, W+E
    val bagCount: Int,
    val teamBags: Pair<Int, Int>, // S+N, W+E
    val spadesBroken: Boolean,
    val biddingDone: Boolean,
    val done: Boolean,
    // --- Wave 2 fidelity additions (defaulted: legacy constructors keep working) ---
    val target: Int = 500,
    val roundNumber: Int = 0,
    val dealer: SpadesSeat = SpadesSeat.WEST,
    val history: List<SpadesRoundRecord> = emptyList(),
    val gameOver: Boolean = false,
    val winnerTeam: Int? = null, // 0 = S-N, 1 = W-E
    val roundScored: Boolean = false,
)

object SpadesEngine {
    val TEAM_ONE = listOf(SpadesSeat.SOUTH, SpadesSeat.NORTH)
    val TEAM_TWO = listOf(SpadesSeat.WEST, SpadesSeat.EAST)

    private fun next(s: SpadesSeat): SpadesSeat = when (s) {
        SpadesSeat.SOUTH -> SpadesSeat.WEST
        SpadesSeat.WEST -> SpadesSeat.NORTH
        SpadesSeat.NORTH -> SpadesSeat.EAST
        SpadesSeat.EAST -> SpadesSeat.SOUTH
    }

    fun partner(s: SpadesSeat) = if (s == SpadesSeat.SOUTH || s == SpadesSeat.NORTH) s to (if (s == SpadesSeat.NORTH) SpadesSeat.SOUTH else SpadesSeat.NORTH) else s to (if (s == SpadesSeat.WEST) SpadesSeat.EAST else SpadesSeat.WEST)

    fun teamOf(s: SpadesSeat): Int = if (s == SpadesSeat.SOUTH || s == SpadesSeat.NORTH) 0 else 1

    private fun deal(rng: Random): Map<SpadesSeat, List<SolCard>> {
        val deck = SolSuit.entries.flatMap { s -> (2..14).map { r -> SolCard(s, r, true) } }.shuffled(rng)
        val hands = mutableMapOf<SpadesSeat, MutableList<SolCard>>()
        SpadesSeat.entries.forEach { hands[it] = mutableListOf() }
        deck.forEachIndexed { i, c -> hands[SpadesSeat.entries[i % 4]]!!.add(c) }
        hands.forEach { (_, list) -> list.sortBy { c -> c.suit.ordinal * 100 + c.rank } }
        return hands
    }

    fun newGame(rng: Random = Random.Default, target: Int = 500): SpadesState {
        val hands = deal(rng)
        return SpadesState(
            hands = hands,
            bids = SpadesSeat.entries.associateWith { 0 },
            nilBid = SpadesSeat.entries.associateWith { false },
            blindNil = SpadesSeat.entries.associateWith { false },
            trick = emptyList(),
            tricksTaken = SpadesSeat.entries.associateWith { emptyList<SolCard>() },
            currentPlayer = SpadesSeat.WEST,
            bidder = SpadesSeat.WEST,
            score = 0 to 0,
            bagCount = 0,
            teamBags = 0 to 0,
            spadesBroken = false,
            biddingDone = false,
            done = false,
            target = target,
            dealer = SpadesSeat.WEST,
        )
    }

    /** Cards the player may play (Spades!Hand.ValidCardsToLead). */
    fun legalPlays(state: SpadesState, seat: SpadesSeat): List<SolCard> {
        val hand = state.hands[seat].orEmpty()
        if (hand.isEmpty()) return emptyList()
        if (state.trick.isEmpty()) {
            if (state.spadesBroken) return hand
            val nonSpades = hand.filter { it.suit != SolSuit.SPADE }
            return if (nonSpades.isNotEmpty()) nonSpades else hand
        }
        val ledSuit = state.trick.first().second.suit
        val follow = hand.filter { it.suit == ledSuit }
        return if (follow.isNotEmpty()) follow else hand
    }

    fun bid(state: SpadesState, n: Int, nil: Boolean = false, blind: Boolean = false): SpadesState {
        val newBids = state.bids.toMutableMap(); newBids[state.currentPlayer] = n
        val newNil = state.nilBid.toMutableMap(); newNil[state.currentPlayer] = nil || blind || n == 0
        val newBlind = state.blindNil.toMutableMap(); newBlind[state.currentPlayer] = blind
        val nextPlayer = next(state.currentPlayer)
        val biddingDone = nextPlayer == state.bidder
        return state.copy(
            bids = newBids,
            nilBid = newNil,
            blindNil = newBlind,
            currentPlayer = if (biddingDone) state.bidder else nextPlayer,
            biddingDone = biddingDone,
        )
    }

    /** Nil is bid 0; blind nil is offered before the cards are revealed. */
    fun bidNil(state: SpadesState, blind: Boolean = false): SpadesState = bid(state, 0, nil = true, blind = blind)

    fun play(state: SpadesState, card: SolCard): SpadesState {
        require(card in legalPlays(state, state.currentPlayer)) { "illegal spades play: $card" }
        val spadesBroken = state.spadesBroken || card.suit == SolSuit.SPADE
        val newHands = state.hands.toMutableMap()
        val hand = newHands[state.currentPlayer]!!.toMutableList()
        hand.remove(card)
        newHands[state.currentPlayer] = hand
        val newTrick = state.trick + (state.currentPlayer to card)
        if (newTrick.size < 4) {
            return state.copy(hands = newHands, trick = newTrick, currentPlayer = next(state.currentPlayer), spadesBroken = spadesBroken)
        }
        // Trick done: highest spade wins, otherwise highest of the led suit (Spades!Trick.Resolve).
        val ledSuit = newTrick.first().second.suit
        val spades = newTrick.filter { it.second.suit == SolSuit.SPADE }
        val winner = if (spades.isNotEmpty()) spades.maxBy { it.second.rank }
        else newTrick.filter { it.second.suit == ledSuit }.maxBy { it.second.rank }
        val newTricks = state.tricksTaken.toMutableMap()
        newTricks[winner.first] = (newTricks[winner.first]!!) + newTrick.map { it.second }
        val allEmpty = SpadesSeat.entries.all { newHands[it]!!.isEmpty() }
        return state.copy(
            hands = newHands,
            trick = emptyList(),
            currentPlayer = winner.first,
            tricksTaken = newTricks,
            spadesBroken = spadesBroken,
            done = allEmpty,
        )
    }

    private fun positiveMod(n: Int, m: Int): Int = ((n % m) + m) % m

    /**
     * Team round scoring (Spades!GameManager.GetScoreForRound).
     *
     * Team bid = sum of positive (non-nil) bids. Made: bid x 10; short: -10 x bid.
     * Overtricks are carried as bags and the bag-out penalty (-100) fires when
     * cumulative score modulo 10 plus this round's bags reaches 10. Nil is
     * +/-100, blind nil +/-200 and is settled outside the team bid.
     */
    fun scoreRound(state: SpadesState): SpadesState {
        if (state.history.any { it.round == state.roundNumber }) return state
        var s1 = state.score.first
        var s2 = state.score.second
        var bags1 = state.teamBags.first
        var bags2 = state.teamBags.second
        var nil1 = 0
        var nil2 = 0
        for (seat in SpadesSeat.entries) {
            if (state.nilBid[seat] == true) {
                val tricks = state.tricksTaken[seat]?.size ?: 0
                val value = if (state.blindNil[seat] == true) 200 else 100
                val pts = if (tricks == 0) value else -value
                if (teamOf(seat) == 0) nil1 += pts else nil2 += pts
            }
        }
        for (team in 0..1) {
            val seats = if (team == 0) TEAM_ONE else TEAM_TWO
            val bid = seats.sumOf { if (state.nilBid[it] == true) 0 else state.bids[it] ?: 0 }
            val tricks = seats.sumOf { state.tricksTaken[it]?.size ?: 0 }
            val delta = tricks - bid
            if (team == 0) {
                s1 += if (delta >= 0) bid * 10 else -bid * 10
                bags1 += delta.coerceAtLeast(0)
            } else {
                s2 += if (delta >= 0) bid * 10 else -bid * 10
                bags2 += delta.coerceAtLeast(0)
            }
        }
        s1 += nil1
        s2 += nil2
        while (positiveMod(s1, 10) + bags1 >= 10) { s1 -= 100; bags1 -= 10 }
        while (positiveMod(s2, 10) + bags2 >= 10) { s2 -= 100; bags2 -= 10 }
        val record = SpadesRoundRecord(
            round = state.roundNumber,
            bids = state.bids.toMap(),
            tricks = SpadesSeat.entries.associateWith { state.tricksTaken[it]?.size ?: 0 },
            teamDelta = (s1 - state.score.first) to (s2 - state.score.second),
            teamBags = bags1 to bags2,
        )
        val over = s1 >= state.target || s2 >= state.target || s1 <= -state.target || s2 <= -state.target
        return state.copy(
            score = s1 to s2,
            teamBags = bags1 to bags2,
            bagCount = bags1 + bags2,
            history = state.history + record,
            gameOver = over,
            winnerTeam = if (over) when {
                s1 > s2 -> 0
                s2 > s1 -> 1
                else -> null
            } else null,
            roundScored = true,
        )
    }

    /** Rotate the dealer, deal the next round and carry the running scores. */
    fun nextRound(state: SpadesState, rng: Random = Random.Default): SpadesState {
        if (!state.done || state.gameOver) return state
        val dealer = next(state.dealer)
        return state.copy(
            hands = deal(rng),
            bids = SpadesSeat.entries.associateWith { 0 },
            nilBid = SpadesSeat.entries.associateWith { false },
            blindNil = SpadesSeat.entries.associateWith { false },
            trick = emptyList(),
            tricksTaken = SpadesSeat.entries.associateWith { emptyList<SolCard>() },
            currentPlayer = dealer,
            bidder = dealer,
            dealer = dealer,
            spadesBroken = false,
            biddingDone = false,
            done = false,
            roundNumber = state.roundNumber + 1,
            roundScored = false,
        )
    }

    /* ------------------------------ AI ------------------------------ */

    fun teamTricks(state: SpadesState, team: Int): Int = (if (team == 0) TEAM_ONE else TEAM_TWO)
        .sumOf { state.tricksTaken[it]?.size ?: 0 }

    fun teamBid(state: SpadesState, team: Int): Int = (if (team == 0) TEAM_ONE else TEAM_TWO)
        .sumOf { if (state.nilBid[it] == true) 0 else state.bids[it] ?: 0 }

    /** Easy: min(6, cards above Jack). Hard: spade run + honors + voids +/- partner. */
    fun aiBid(state: SpadesState, seat: SpadesSeat, level: SpadesLevel, rng: Random = Random.Default): SpadesBid {
        val hand = state.hands[seat].orEmpty()
        val value = when (level) {
            SpadesLevel.EASY -> {
                hand.count { it.rank > 11 }.coerceAtMost(6)
            }
            SpadesLevel.HARD -> {
                val spades = hand.filter { it.suit == SolSuit.SPADE }.map { it.rank }.sortedDescending()
                val honors = spades.count { it >= 12 }
                var run = 0
                var expect = 14
                for (r in spades) {
                    if (r == expect) { run += 1; expect -= 1 } else if (r < expect) break
                }
                var bid = honors + if (run >= 3) 1 else 0
                bid += SolSuit.entries.filter { it != SolSuit.SPADE }.count { suit -> hand.none { it.suit == suit } }
                val partner = partner(seat).second
                val partnerBid = state.bids[partner] ?: 0
                if (state.nilBid[partner] == true) bid += 1
                if (partnerBid >= 4 && bid + partnerBid > 10) bid = 10 - partnerBid
                bid.coerceIn(0, 10)
            }
        }
        return SpadesBid(value = value, nil = value == 0)
    }

    fun aiChoose(state: SpadesState, seat: SpadesSeat, level: SpadesLevel, rng: Random = Random.Default): SolCard? {
        val legal = legalPlays(state, seat)
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal.first()
        if (state.nilBid[seat] == true) return nilPlay(state, seat, legal)
        return when (level) {
            SpadesLevel.EASY -> easyPlay(state, seat, legal)
            SpadesLevel.HARD -> hardPlay(state, seat, legal)
        }
    }

    /** A nil bidder wants zero tricks: always duck with the lowest legal card. */
    private fun nilPlay(state: SpadesState, seat: SpadesSeat, legal: List<SolCard>): SolCard {
        val safe = legal.filter { it.suit != SolSuit.SPADE }
        return (safe.ifEmpty { legal }).minBy { it.rank }
    }

    private fun leadingCardOnTable(state: SpadesState): SolCard? {
        if (state.trick.isEmpty()) return null
        val led = state.trick.first().second.suit
        val spades = state.trick.filter { it.second.suit == SolSuit.SPADE }
        return if (spades.isNotEmpty()) spades.maxBy { it.second.rank }.second
        else state.trick.filter { it.second.suit == led }.maxBy { it.second.rank }.second
    }

    private fun easyPlay(state: SpadesState, seat: SpadesSeat, legal: List<SolCard>): SolCard {
        val team = teamOf(seat)
        val behind = teamTricks(state, team) < teamBid(state, team)
        if (state.trick.isEmpty()) {
            if (!behind) {
                val low = legal.filter { it.suit != SolSuit.SPADE }
                return (low.ifEmpty { legal }).minBy { it.rank }
            }
            return legal.maxByOrNull { if (it.suit == SolSuit.SPADE) it.rank + 15 else it.rank } ?: legal.first()
        }
        return if (behind) legal.maxBy { it.rank } else legal.minBy { it.rank }
    }

    private fun hardPlay(state: SpadesState, seat: SpadesSeat, legal: List<SolCard>): SolCard {
        val team = teamOf(seat)
        val partner = partner(seat).second
        val tricksNeeded = (state.bids[seat] ?: 0) + (if (state.nilBid[partner] == true) 0 else state.bids[partner] ?: 0)
        val tricksSoFar = (state.tricksTaken[seat]?.size ?: 0) + (state.tricksTaken[partner]?.size ?: 0)
        val behind = tricksSoFar < tricksNeeded
        if (state.trick.isEmpty()) {
            if (!behind) {
                val low = legal.filter { it.suit != SolSuit.SPADE }
                return (low.ifEmpty { legal }).minBy { it.rank }
            }
            return legal.maxByOrNull { it.rank } ?: legal.first()
        }
        val led = state.trick.first().second.suit
        val winner = state.trick.maxBy { p ->
            if (p.second.suit == SolSuit.SPADE) p.second.rank + 20 else if (p.second.suit == led) p.second.rank else 0
        }.first
        val following = legal.any { it.suit == led } || legal.any { it.suit == SolSuit.SPADE }
        if (winner == partner && following) {
            // Do not overtake the partner — shed the lowest legal card.
            return legal.minBy { it.rank }
        }
        val currentWinning = leadingCardOnTable(state)
        val canWin = currentWinning != null && legal.any { c ->
            if (c.suit == SolSuit.SPADE) currentWinning.suit != SolSuit.SPADE || c.rank > currentWinning.rank
            else c.suit == led && currentWinning.suit != SolSuit.SPADE && c.rank > currentWinning.rank
        }
        return when {
            behind && canWin -> legal.filter { c ->
                if (c.suit == SolSuit.SPADE) currentWinning.suit != SolSuit.SPADE || c.rank > currentWinning.rank
                else c.suit == led && currentWinning.suit != SolSuit.SPADE && c.rank > currentWinning.rank
            }.minBy { it.rank }
            else -> legal.minBy { it.rank }
        }
    }

    /** One AI action: bid while bidding, otherwise play a card. */
    fun aiAction(state: SpadesState, level: SpadesLevel = SpadesLevel.EASY, rng: Random = Random.Default): SpadesState {
        if (state.done || state.gameOver) return state
        if (!state.biddingDone) {
            val b = aiBid(state, state.currentPlayer, level, rng)
            return bid(state, b.value, nil = b.nil, blind = b.blindNil)
        }
        val card = aiChoose(state, state.currentPlayer, level, rng) ?: return state
        return play(state, card)
    }

    /**
     * Deterministically drive a whole round to its end with AI for every seat.
     * Bounded: 52 cards + 4 bids is the theoretical maximum.
     */
    fun autoPlayRound(state: SpadesState, level: SpadesLevel = SpadesLevel.EASY, rng: Random = Random.Default): SpadesState {
        var s = state
        var guard = 0
        while (!s.done && !s.gameOver && guard < 80) {
            val nextState = aiAction(s, level, rng)
            if (nextState == s) break
            s = nextState
            guard += 1
        }
        return s
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

    private fun handMapCsv(map: Map<SpadesSeat, List<SolCard>>): String =
        SpadesSeat.entries.joinToString(";") { seat -> cardsCsv(map[seat].orEmpty()) }

    private fun intMapCsv(map: Map<SpadesSeat, Int>): String =
        SpadesSeat.entries.joinToString(",") { (map[it] ?: 0).toString() }

    private fun boolMapCsv(map: Map<SpadesSeat, Boolean>): String =
        SpadesSeat.entries.joinToString(",") { if (map[it] == true) "1" else "0" }

    fun encode(state: SpadesState): String {
        val trick = state.trick.joinToString(" ") { (seat, card) -> "${seat.name}:${cardCode(card)}" }
        return listOf(
            "spades1",
            state.roundNumber.toString(),
            state.target.toString(),
            state.dealer.name,
            state.bidder.name,
            state.currentPlayer.name,
            if (state.spadesBroken) "1" else "0",
            if (state.biddingDone) "1" else "0",
            if (state.done) "1" else "0",
            if (state.gameOver) "1" else "0",
            state.winnerTeam?.toString() ?: "-",
            state.score.first.toString(),
            state.score.second.toString(),
            state.teamBags.first.toString(),
            state.teamBags.second.toString(),
            intMapCsv(state.bids),
            boolMapCsv(state.nilBid),
            boolMapCsv(state.blindNil),
            handMapCsv(state.hands),
            handMapCsv(state.tricksTaken),
            trick,
            if (state.roundScored) "1" else "0",
        ).joinToString("|")
    }

    fun decode(blob: String): SpadesState? {
        return try {
            val p = blob.split('|')
            if (p.size < 22 || p[0] != "spades1") return null
            fun ints(csv: String) = csv.split(',').map { it.toInt() }
            fun bools(csv: String) = csv.split(',').map { it == "1" }
            val bids = ints(p[15]); val nilB = bools(p[16]); val blindB = bools(p[17])
            val hands = p[18].split(';'); val taken = p[19].split(';')
            if (bids.size != 4 || nilB.size != 4 || blindB.size != 4 || hands.size != 4 || taken.size != 4) return null
            val trick = p[20].split(' ').mapNotNull { token ->
                if (token.isBlank()) return@mapNotNull null
                val sep = token.indexOf(':')
                if (sep <= 0) return@mapNotNull null
                val seat = SpadesSeat.entries.firstOrNull { it.name == token.substring(0, sep) } ?: return@mapNotNull null
                val card = parseCard(token.substring(sep + 1)) ?: return@mapNotNull null
                seat to card
            }
            SpadesState(
                hands = SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to parseCards(hands[i]) },
                bids = SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to bids[i] },
                nilBid = SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to nilB[i] },
                blindNil = SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to blindB[i] },
                trick = trick,
                tricksTaken = SpadesSeat.entries.withIndex().associate { (i, seat) -> seat to parseCards(taken[i]) },
                currentPlayer = SpadesSeat.entries.firstOrNull { it.name == p[5] } ?: SpadesSeat.WEST,
                bidder = SpadesSeat.entries.firstOrNull { it.name == p[4] } ?: SpadesSeat.WEST,
                score = (p[11].toIntOrNull() ?: 0) to (p[12].toIntOrNull() ?: 0),
                bagCount = (p[13].toIntOrNull() ?: 0) + (p[14].toIntOrNull() ?: 0),
                teamBags = (p[13].toIntOrNull() ?: 0) to (p[14].toIntOrNull() ?: 0),
                spadesBroken = p[6] == "1",
                biddingDone = p[7] == "1",
                done = p[8] == "1",
                target = p[2].toIntOrNull() ?: 500,
                roundNumber = p[1].toIntOrNull() ?: 0,
                dealer = SpadesSeat.entries.firstOrNull { it.name == p[3] } ?: SpadesSeat.WEST,
                gameOver = p[9] == "1",
                winnerTeam = p[10].toIntOrNull(),
                roundScored = p[21] == "1",
            )
        } catch (_: Exception) {
            null
        }
    }
}
