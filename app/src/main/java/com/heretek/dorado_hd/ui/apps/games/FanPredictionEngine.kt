package com.heretek.dorado_hd.ui.apps.games

/**
 * Clean-room re-derivation of the dead Fan Prediction companion service
 * (docs/apps/fan-prediction.md). The original registered the device against a
 * live scoring service; this build is an explicitly offline simulation. The
 * device-GUID account, favourite teams and predictions live locally, while
 * fixtures, results and rival standings are generated deterministically from a
 * seed. Pure Kotlin, deterministic and testable with no transport.
 */

enum class FanSport(val id: String, val label: String, val hasDraw: Boolean) {
    SOCCER("soccer", "soccer", true),
    BASKETBALL("basketball", "basketball", false),
    HOCKEY("hockey", "hockey", false),
}

data class FanTeam(val id: Int, val sport: FanSport, val name: String)

object FanTeams {
    val ALL: List<FanTeam> = listOf(
        FanTeam(0, FanSport.SOCCER, "Northport FC"),
        FanTeam(1, FanSport.SOCCER, "Riverton United"),
        FanTeam(2, FanSport.SOCCER, "Cedar City"),
        FanTeam(3, FanSport.SOCCER, "Harbour Rovers"),
        FanTeam(4, FanSport.SOCCER, "Old Mill Athletic"),
        FanTeam(5, FanSport.SOCCER, "Junction Wanderers"),
        FanTeam(6, FanSport.SOCCER, "Kestrel Park"),
        FanTeam(7, FanSport.SOCCER, "Southbank FC"),
        FanTeam(8, FanSport.SOCCER, "Grand Union"),
        FanTeam(9, FanSport.SOCCER, "Lantern Hill"),
        FanTeam(10, FanSport.SOCCER, "Ironworks FC"),
        FanTeam(11, FanSport.SOCCER, "Fairhaven Town"),
        FanTeam(12, FanSport.BASKETBALL, "Northport Flight"),
        FanTeam(13, FanSport.BASKETBALL, "Riverton Rollers"),
        FanTeam(14, FanSport.BASKETBALL, "Cedar City Cinders"),
        FanTeam(15, FanSport.BASKETBALL, "Harbour Breeze"),
        FanTeam(16, FanSport.BASKETBALL, "Old Mill Foundry"),
        FanTeam(17, FanSport.BASKETBALL, "Junction Jacks"),
        FanTeam(18, FanSport.BASKETBALL, "Kestrel Park Kites"),
        FanTeam(19, FanSport.BASKETBALL, "Southbank Tide"),
        FanTeam(20, FanSport.HOCKEY, "Northport Frost"),
        FanTeam(21, FanSport.HOCKEY, "Riverton Rapids"),
        FanTeam(22, FanSport.HOCKEY, "Cedar City Chill"),
        FanTeam(23, FanSport.HOCKEY, "Harbour Ice"),
        FanTeam(24, FanSport.HOCKEY, "Old Mill Wolves"),
        FanTeam(25, FanSport.HOCKEY, "Junction Forge"),
        FanTeam(26, FanSport.HOCKEY, "Kestrel Park Kestrels"),
        FanTeam(27, FanSport.HOCKEY, "Southbank Sharks"),
    )

    fun of(sport: FanSport): List<FanTeam> = ALL.filter { it.sport == sport }

    fun byId(id: Int): FanTeam? = ALL.firstOrNull { it.id == id }
}

enum class FanPick { HOME, DRAW, AWAY }

enum class FixtureStatus { SCHEDULED, FINAL }

data class FanFixture(
    val id: Int,
    val sport: FanSport,
    val homeId: Int,
    val awayId: Int,
    val kickoffMs: Long,
    val status: FixtureStatus = FixtureStatus.SCHEDULED,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
)

data class FanPrediction(
    val fixtureId: Int,
    val pick: FanPick,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
)

data class FanStandingRow(
    val name: String,
    val points: Int,
    val correct: Int,
    val total: Int,
    val isYou: Boolean,
) {
    val percent: Int get() = if (total <= 0) 0 else correct * 100 / total
}

data class FanProfile(
    val deviceId: String,
    val nickname: String = "",
    val favorites: Map<FanSport, Int> = emptyMap(),
)

data class FanState(
    val profile: FanProfile,
    val fixtures: List<FanFixture>,
    val predictions: Map<Int, FanPrediction>,
    val standings: List<FanStandingRow>,
    val anchorMs: Long,
    val nowMs: Long,
    val seed: Int,
    val offline: Boolean = false,
    val banner: String? = null,
)

sealed interface FanEvent {
    data class SetNickname(val value: String) : FanEvent
    data class SetFavoriteTeam(val sport: FanSport, val teamId: Int?) : FanEvent
    data class MakePrediction(
        val fixtureId: Int,
        val pick: FanPick,
        val homeScore: Int? = null,
        val awayScore: Int? = null,
    ) : FanEvent

    data class RemovePrediction(val fixtureId: Int) : FanEvent
    data class SetOffline(val value: Boolean) : FanEvent
    data class Refresh(val nowMs: Long) : FanEvent
    data object ResetScores : FanEvent
}

object FanPredictionEngine {
    const val OUTCOME_POINTS = 3
    const val EXACT_BONUS = 2
    const val MAX_NICKNAME = 20
    const val MAX_SCORE_DIGITS = 2
    const val MAX_SCORE = 99

    /**
     * Appends a keypad digit to a score field. Returns [value] unchanged once
     * the field is full or the key is not a single digit.
     */
    fun appendScoreDigit(value: String, key: String): String {
        if (key.length != 1 || !key[0].isDigit()) return value
        if (value.length >= MAX_SCORE_DIGITS) return value
        return value + key
    }

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val HOUR_MS = 60L * 60L * 1000L

    private val RIVALS = listOf(
        "harbourlight", "northwind", "oldpier", "cedarblue", "lantern",
        "southbank", "juniorj", "kestrel", "ironmike", "fairhaven",
    )

    fun newState(deviceId: String, nowMs: Long, seed: Int): FanState {
        val fixtures = generateFixtures(nowMs + 2 * HOUR_MS, seed)
        return FanState(
            profile = FanProfile(deviceId = deviceId),
            fixtures = fixtures,
            predictions = emptyMap(),
            standings = seedStandings(),
            anchorMs = nowMs,
            nowMs = nowMs,
            seed = seed,
            offline = true,
            banner = OFFLINE_BANNER,
        )
    }

    const val OFFLINE_BANNER = "offline simulation - no network, results are generated locally"
    const val LOCKED_BANNER = "predictions lock at kickoff"
    const val DRAW_BANNER = "draw picks are soccer only"

    /* ---------------- fixture generation ---------------- */

    /**
     * Deterministic matchweeks for each sport anchored at [anchorMs]; the
     * first kickoff is within the next day and the last is inside the
     * original's 90-day window (spec §3).
     */
    fun generateFixtures(anchorMs: Long, seed: Int): List<FanFixture> {
        val out = ArrayList<FanFixture>()
        var id = 1
        for (sport in FanSport.entries) {
            val teams = FanTeams.of(sport)
            if (teams.size < 2) continue
            val order = shuffled(teams, seed + sport.ordinal * 7919)
            val matchdays = 2
            for (day in 0 until matchdays) {
                val rotated = order.drop(day).plus(order.take(day))
                for (i in 0 until teams.size / 2) {
                    val home = rotated[i]
                    val away = rotated[rotated.size - 1 - i]
                    val kickoff = anchorMs + day * 2 * DAY_MS + i * 3 * HOUR_MS
                    out += FanFixture(
                        id = id++,
                        sport = sport,
                        homeId = home.id,
                        awayId = away.id,
                        kickoffMs = kickoff,
                    )
                }
            }
        }
        return out
    }

    private fun shuffled(teams: List<FanTeam>, seed: Int): List<FanTeam> {
        var rng = seed
        val list = teams.toMutableList()
        for (i in list.indices.reversed()) {
            val roll = PenaltyEngine.nextRandom(rng)
            rng = roll.first
            val j = (roll.second * (i + 1)).toInt().coerceIn(0, i)
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        return list
    }

    private fun seedStandings(): List<FanStandingRow> =
        RIVALS.map { FanStandingRow(name = it, points = 0, correct = 0, total = 0, isYou = false) } +
            FanStandingRow(name = "you", points = 0, correct = 0, total = 0, isYou = true)

    /* ---------------- events ---------------- */

    fun apply(state: FanState, event: FanEvent): FanState = when (event) {
        is FanEvent.SetNickname -> {
            val nick = sanitizeNickname(event.value)
            state.copy(profile = state.profile.copy(nickname = nick), banner = null)
        }

        is FanEvent.SetFavoriteTeam -> {
            val team = event.teamId?.let { FanTeams.byId(it) }
            when {
                event.teamId == null -> state.copy(
                    profile = state.profile.copy(favorites = state.profile.favorites - event.sport),
                    banner = null,
                )

                team == null || team.sport != event.sport -> state.copy(banner = "unknown team for ${event.sport.label}")
                else -> state.copy(
                    profile = state.profile.copy(favorites = state.profile.favorites + (event.sport to team.id)),
                    banner = null,
                )
            }
        }

        is FanEvent.MakePrediction -> makePrediction(state, event)
        is FanEvent.RemovePrediction -> state.copy(predictions = state.predictions - event.fixtureId)
        is FanEvent.SetOffline -> state.copy(
            offline = event.value,
            banner = if (event.value) OFFLINE_BANNER else "local simulation stays active",
        )

        is FanEvent.Refresh -> refresh(state, event.nowMs)
        FanEvent.ResetScores -> state.copy(
            predictions = emptyMap(),
            standings = seedStandings(),
            banner = "local scores reset",
        )
    }

    private fun makePrediction(state: FanState, event: FanEvent.MakePrediction): FanState {
        val fixture = state.fixtures.firstOrNull { it.id == event.fixtureId }
            ?: return state.copy(banner = "fixture not found")
        if (isLocked(state, fixture)) return state.copy(banner = LOCKED_BANNER)
        val home = event.homeScore
        val away = event.awayScore
        val pick = if (home != null && away != null) {
            when {
                home > away -> FanPick.HOME
                home < away -> FanPick.AWAY
                else -> FanPick.DRAW
            }
        } else {
            event.pick
        }
        if (pick == FanPick.DRAW && !fixture.sport.hasDraw) return state.copy(banner = DRAW_BANNER)
        if (home != null && (home < 0 || home > MAX_SCORE)) return state.copy(banner = "scores are 0 to $MAX_SCORE")
        if (away != null && (away < 0 || away > MAX_SCORE)) return state.copy(banner = "scores are 0 to $MAX_SCORE")
        val prediction = FanPrediction(fixtureId = fixture.id, pick = pick, homeScore = home, awayScore = away)
        return state.copy(predictions = state.predictions + (fixture.id to prediction), banner = null)
    }

    /* ---------------- locking + settlement ---------------- */

    fun isLocked(state: FanState, fixture: FanFixture): Boolean =
        fixture.status == FixtureStatus.FINAL || state.nowMs >= fixture.kickoffMs

    fun canPredict(state: FanState, fixture: FanFixture): Boolean =
        fixture.status == FixtureStatus.SCHEDULED && !isLocked(state, fixture)

    fun fixtureOutcome(fixture: FanFixture): FanPick = when {
        fixture.homeScore > fixture.awayScore -> FanPick.HOME
        fixture.homeScore < fixture.awayScore -> FanPick.AWAY
        else -> FanPick.DRAW
    }

    /** Outcome match = 3 points, exact score adds 2 (spec §3). */
    fun scorePrediction(prediction: FanPrediction, fixture: FanFixture): Int {
        if (fixture.status != FixtureStatus.FINAL) return 0
        if (prediction.pick != fixtureOutcome(fixture)) return 0
        val exact = prediction.homeScore == fixture.homeScore && prediction.awayScore == fixture.awayScore
        return OUTCOME_POINTS + if (exact) EXACT_BONUS else 0
    }

    /** Deterministic simulated final score for a fixture. */
    fun simulateScore(fixture: FanFixture): Pair<Int, Int> {
        val base = PenaltyEngine.seedFrom("${fixture.id}:${fixture.sport.id}")
        val rng = PenaltyEngine.nextRandom(base)
        val high = if (fixture.sport == FanSport.BASKETBALL) 4 else 3
        val home = (rng.second * (high + 1)).toInt().coerceIn(0, high)
        val second = PenaltyEngine.nextRandom(rng.first)
        val away = (second.second * (high + 1)).toInt().coerceIn(0, high)
        return home to away
    }

    /**
     * Marks kicked-off fixtures final, awards prediction points and advances
     * the simulated rivals. Idempotent for a given [nowMs].
     */
    fun settle(state: FanState, nowMs: Long): FanState {
        val due = state.fixtures.filter { it.status == FixtureStatus.SCHEDULED && it.kickoffMs <= nowMs }
        if (due.isEmpty()) return state.copy(nowMs = nowMs)
        var fixtures = state.fixtures
        var standings = state.standings
        for (fixture in due) {
            val (home, away) = simulateScore(fixture)
            val final = fixture.copy(status = FixtureStatus.FINAL, homeScore = home, awayScore = away)
            fixtures = fixtures.map { if (it.id == final.id) final else it }
            val prediction = state.predictions[final.id]
            val outcome = fixtureOutcome(final)
            standings = standings.mapIndexed { index, row ->
                if (row.isYou) {
                    val awarded = prediction?.let { scorePrediction(it, final) } ?: 0
                    val hit = prediction != null && prediction.pick == outcome
                    row.copy(
                        points = row.points + awarded,
                        total = row.total + (if (prediction != null) 1 else 0),
                        correct = row.correct + (if (hit) 1 else 0),
                    )
                } else {
                    val rivalRoll = PenaltyEngine.nextRandom(PenaltyEngine.seedFrom("${row.name}:${final.id}"))
                    val points = (rivalRoll.second * 4.0).toInt().coerceIn(0, 3)
                    row.copy(
                        points = row.points + points,
                        total = row.total + 1,
                        correct = row.correct + (if (points >= OUTCOME_POINTS) 1 else 0),
                    )
                }
            }
        }
        return state.copy(fixtures = fixtures, standings = standings, nowMs = nowMs)
    }

    /**
     * Settles and, when the whole slate has kicked off, rolls a fresh window
     * forward (preserving the account, favourites and standings).
     */
    fun refresh(state: FanState, nowMs: Long): FanState {
        val settled = settle(state, nowMs)
        if (settled.fixtures.isNotEmpty() && settled.fixtures.all { it.kickoffMs <= nowMs }) {
            val nextSeed = settled.seed + 1
            return settled.copy(
                fixtures = generateFixtures(nowMs + 6 * HOUR_MS, nextSeed),
                predictions = emptyMap(),
                anchorMs = nowMs,
                nowMs = nowMs,
                seed = nextSeed,
                banner = "new fixture window generated locally",
            )
        }
        return settled
    }

    /* ---------------- views ---------------- */

    fun fixtureLabel(state: FanState, fixture: FanFixture): String {
        val home = FanTeams.byId(fixture.homeId)?.name ?: "?"
        val away = FanTeams.byId(fixture.awayId)?.name ?: "?"
        return "$home vs $away"
    }

    fun nicknameOrHandle(state: FanState): String =
        state.profile.nickname.ifBlank { "device-${state.profile.deviceId.take(8)}" }

    fun sortedStandings(state: FanState): List<FanStandingRow> =
        state.standings.sortedWith(compareByDescending<FanStandingRow> { it.points }.thenBy { it.name })

    fun matchesFor(state: FanState, sport: FanSport): List<FanFixture> =
        state.fixtures.filter { it.sport == sport }

    fun favoritesLine(state: FanState): String {
        if (state.profile.favorites.isEmpty()) return "no favourite team set"
        return state.profile.favorites.entries.joinToString(" · ") { (sport, id) ->
            "${sport.label}: ${FanTeams.byId(id)?.name ?: "?"}"
        }
    }

    fun sanitizeNickname(value: String): String =
        value.trim()
            .map { if (it in ";:,|=" || it.isISOControl()) ' ' else it }
            .joinToString("")
            .trim()
            .replace(Regex(" +"), " ")
            .take(MAX_NICKNAME)

    /* ---------------- persistence ---------------- */

    fun encode(state: FanState): String {
        val favorites = state.profile.favorites.entries.joinToString(",") { "${it.key.ordinal}:${it.value}" }
        val predictions = state.predictions.values.joinToString(",") { p ->
            "${p.fixtureId}:${p.pick.ordinal}:${p.homeScore ?: -1}:${p.awayScore ?: -1}"
        }
        val fixtures = state.fixtures.joinToString(",") { f ->
            "${f.id}:${f.sport.ordinal}:${f.homeId}:${f.awayId}:${f.kickoffMs}:${f.status.ordinal}:${f.homeScore}:${f.awayScore}"
        }
        val standings = state.standings.joinToString(",") { row ->
            "${row.name}|${row.points}|${row.correct}|${row.total}|${if (row.isYou) 1 else 0}"
        }
        return listOf(
            "fp1",
            state.profile.deviceId,
            state.profile.nickname,
            state.seed,
            state.anchorMs,
            state.nowMs,
            if (state.offline) "1" else "0",
            favorites,
            predictions,
            fixtures,
            standings,
            state.banner ?: "-",
        ).joinToString(";")
    }

    fun decode(blob: String): FanState? = try {
        val parts = blob.split(";")
        if (parts.size < 12 || parts[0] != "fp1") return null
        val favorites = parts[7].split(",").mapNotNull { entry ->
            val f = entry.split(":")
            if (f.size != 2) return@mapNotNull null
            val sport = FanSport.entries.getOrNull(f[0].toInt()) ?: return@mapNotNull null
            sport to f[1].toInt()
        }.toMap()
        val predictions = parts[8].split(",").mapNotNull { entry ->
            val f = entry.split(":")
            if (f.size != 4) return@mapNotNull null
            val pick = FanPick.entries.getOrNull(f[1].toInt()) ?: return@mapNotNull null
            FanPrediction(
                fixtureId = f[0].toInt(),
                pick = pick,
                homeScore = f[2].toInt().takeIf { it >= 0 },
                awayScore = f[3].toInt().takeIf { it >= 0 },
            )
        }.associateBy { it.fixtureId }
        val fixtures = parts[9].split(",").mapNotNull { entry ->
            val f = entry.split(":")
            if (f.size != 8) return@mapNotNull null
            val sport = FanSport.entries.getOrNull(f[1].toInt()) ?: return@mapNotNull null
            val status = FixtureStatus.entries.getOrNull(f[5].toInt()) ?: return@mapNotNull null
            FanFixture(f[0].toInt(), sport, f[2].toInt(), f[3].toInt(), f[4].toLong(), status, f[6].toInt(), f[7].toInt())
        }
        val standings = parts[10].split(",").mapNotNull { entry ->
            val f = entry.split("|")
            if (f.size != 5) return@mapNotNull null
            FanStandingRow(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4] == "1")
        }
        FanState(
            profile = FanProfile(parts[1], parts[2], favorites),
            fixtures = fixtures,
            predictions = predictions,
            standings = standings,
            anchorMs = parts[4].toLong(),
            nowMs = parts[5].toLong(),
            seed = parts[3].toInt(),
            offline = parts[6] == "1",
            banner = parts[11].takeIf { it != "-" },
        )
    } catch (_: Exception) {
        null
    }

    /** Percent formatting for the standings table (Name / Pts / %). */
    fun percentLabel(row: FanStandingRow): String = "${row.percent}%"
}
