package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Clean-room re-derivation of "Penalty! Flick Soccer" (docs/apps/penalty-flick-soccer.md).
 * No decompiled code, art or level data is used; only the behavioural constants
 * called out in the spec are reproduced. The engine is a pure Kotlin object over
 * immutable state with a fixed 60 Hz step and seeded LCG randomness, so every
 * match, bracket and challenge resolves identically for identical input.
 *
 * Space: +x is right, +y is up, +z runs from the kicker toward the goal.
 */

/* ------------------------------------------------------------------ */
/* Vectors                                                             */
/* ------------------------------------------------------------------ */

/** Immutable 3D vector in metres. */
data class PenaltyVec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: PenaltyVec3) = PenaltyVec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: PenaltyVec3) = PenaltyVec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = PenaltyVec3(x * scale, y * scale, z * scale)

    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun normalized(): PenaltyVec3 {
        val len = length()
        return if (len < 1e-9) this else times(1.0 / len)
    }
}

/* ------------------------------------------------------------------ */
/* Enums                                                               */
/* ------------------------------------------------------------------ */

enum class PenaltyMode { WORLD_GAME, MAD_MINUTE, GOAL_STREAK, TUTORIAL, PRACTICE }

enum class KickOutcome { GOAL, MISS, SAVED }

/** Goal-side zones the keeper can commit to (spec §4). */
enum class KeeperZone { NONE, HOLD, JUMP, HIGH_LEFT, LOW_LEFT, HIGH_RIGHT, LOW_RIGHT }

enum class MatchPhase { SHOOT, DEFEND, DONE }

enum class TournamentStatus { PLAYING, WON, LOST }

enum class PenaltyPowerup { TIME_FREEZE, POINTS_BONUS, BALL_RESET_SPEEDUP, BALL_FRENZY }

enum class PenaltyBallState { WAITING, IN_FLIGHT, STOPPED }

/**
 * Pitch variants. Rounds 0-3 in the world game use [KICKER]/[KEEPER] cameras;
 * sudden death and the challenge modes step through SD1-4 with their own kick
 * spots, goal depths and force multipliers (spec §3).
 */
enum class PenaltyStage(
    val label: String,
    val kickX: Double,
    val kickZ: Double,
    val goalDepth: Double,
    val backZ: Double,
    val forceMultiplier: Double,
    val keeperX: Double,
    val keeperZ: Double,
    val reactionMax: Double,
    val randomDiveProb: Double,
) {
    KICKER("kicker", 0.0, 0.5, 1.8, 12.8, 1.0, 0.0, 10.0, 0.5, 0.5),
    KEEPER("keeper", 0.0, 0.5, 1.8, 12.8, 1.0, 0.0, 10.0, 0.5, 0.5),
    SD1("sudden death 1", 0.0, 3.0, 3.0, 14.0, 1.0, 0.0, 10.0, 0.2, 0.2),
    SD2("sudden death 2", 0.0, -24.25, 3.0, 14.0, 1.3, 0.0, 10.0, 0.2, 0.2),
    SD3("sudden death 3", -14.58333, 0.3676471, 2.0, 13.0, 1.0, -3.8, 9.0, 0.2, 0.2),
    SD4("sudden death 4", 15.75, -25.0 / 34.0, 2.0, 13.0, 1.0, 3.8, 9.0, 0.2, 0.2),
    ;

    fun kickSpot(): PenaltyVec3 = PenaltyVec3(kickX, BALL_LAUNCH_HEIGHT, kickZ)
}

private const val BALL_LAUNCH_HEIGHT = 0.18

/** A tournament entrant. Colours are plain ARGB ints; the screen maps them to tokens. */
data class PenaltyTeam(val id: Int, val name: String, val primary: Int, val secondary: Int)

/* ------------------------------------------------------------------ */
/* Swipe -> kick                                                       */
/* ------------------------------------------------------------------ */

/**
 * Swipe sample in screen pixels per frame: the final movement vector plus the
 * running velocity average (used for the arc/spin estimate).
 */
data class PenaltySwipe(
    val vx: Double,
    val vy: Double,
    val avgVx: Double = vx,
    val avgVy: Double = vy,
)

/** Force vector (N), clamped speed and spin (rad-ish, clamped +/-0.5). */
data class PenaltyKick(
    val force: PenaltyVec3,
    val spin: Double,
    val speed: Double,
    val direction: PenaltyVec3 = force.normalized(),
)

/* ------------------------------------------------------------------ */
/* Simulation results                                                  */
/* ------------------------------------------------------------------ */

data class PenaltyShot(
    val outcome: KickOutcome,
    val trajectory: List<PenaltyVec3>,
    val keeperZone: KeeperZone,
    val spin: Double,
    val shots: Int = 1,
)

data class PenaltyKeeperPlan(val zone: KeeperZone, val reaction: Double)

/** One regulation/sudden-death match. Kick order alternates player, opponent. */
data class PenaltyMatch(
    val playerTeam: Int,
    val opponentTeam: Int,
    val roundIndex: Int,
    val playerStrip: List<KickOutcome> = emptyList(),
    val opponentStrip: List<KickOutcome> = emptyList(),
    val suddenDeath: Boolean = false,
    val finished: Boolean = false,
    val playerWon: Boolean = false,
) {
    val playerGoals: Int get() = playerStrip.count { it == KickOutcome.GOAL }
    val opponentGoals: Int get() = opponentStrip.count { it == KickOutcome.GOAL }
    val kicksTaken: Int get() = playerStrip.size + opponentStrip.size
    val nextIsPlayer: Boolean get() = kicksTaken % 2 == 0
}

data class PenaltyBracketMatch(
    val round: Int,
    val slot: Int,
    val home: Int,
    val away: Int,
    val homeScore: Int = -1,
    val awayScore: Int = -1,
    val winner: Int = -1,
)

data class TournamentState(
    val playerTeam: Int,
    val matches: List<PenaltyBracketMatch>,
    val round: Int,
    val playerSlot: Int,
    val opponent: Int,
    val match: PenaltyMatch,
    val status: TournamentStatus = TournamentStatus.PLAYING,
    val seed: Int,
    val rngState: Int,
    val bestMadMinute: Int = 0,
    val bestStreak: Int = 0,
)

/* ------------------------------------------------------------------ */
/* Mad Minute / Goal Streak                                            */
/* ------------------------------------------------------------------ */

data class MadBall(
    val trajectory: List<PenaltyVec3>,
    val spawnAt: Double,
    val duration: Double,
    val goal: Boolean,
    val scoreValue: Int,
    val deflected: Boolean = false,
)

data class MadMinuteState(
    val countdown: Double = 3.0,
    val timeLeft: Double = PenaltyEngine.MAD_MINUTE_SECONDS,
    val freezeLeft: Double = 0.0,
    val bonusLeft: Double = 0.0,
    val speedupLeft: Double = 0.0,
    val frenzyLeft: Double = 0.0,
    val clock: Double = 0.0,
    val cooldown: Double = 0.0,
    val score: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val liveBalls: List<MadBall> = emptyList(),
    val outcomes: List<KickOutcome> = emptyList(),
    val nextPickup: PenaltyPowerup? = null,
    val pickupX: Double = 0.0,
    val over: Boolean = false,
    val seed: Int = 1,
    val rngState: Int = 1,
)

data class GoalStreakState(
    val streak: Int = 0,
    val best: Int = 0,
    val attempts: Int = 0,
    val last: KickOutcome? = null,
    val stage: PenaltyStage = PenaltyStage.SD1,
    val seed: Int = 1,
    val rngState: Int = 1,
)

/* ------------------------------------------------------------------ */
/* Engine                                                              */
/* ------------------------------------------------------------------ */

object PenaltyEngine {
    const val DT = 1.0 / 60.0

    // Goal geometry (spec §3).
    const val GOAL_POST_LEFT_X = -3.5
    const val GOAL_POST_RIGHT_X = 3.5
    const val GOAL_HALF_WIDTH = 3.5
    const val CROSSBAR_HEIGHT = 2.2
    const val GOAL_LINE_Z = 11.0
    const val POST_RADIUS = 0.05
    const val POST_BOUNCE = 0.6
    const val NET_BOUNCE = 0.1
    const val GROUND_BOUNCE = 0.4

    // Ball (spec §3).
    const val BALL_MASS = 0.5
    const val BALL_RADIUS = 0.2
    const val GRAVITY_FORCE = -4.9
    const val SPIN_FORCE_SCALE = 0.5
    const val SPIN_MAX = 0.5
    const val SPIN_SCALE = 3.0
    const val SWIPE_FORCE_SCALE = 30.0
    const val MIN_FORCE = 350.0
    const val MAX_FORCE = 750.0
    const val STOP_DISTANCE = 0.005
    const val MAX_TRAJECTORY_TICKS = 300

    // Keeper body / dive (spec §3).
    const val KEEPER_WIDTH = 0.5
    const val KEEPER_HEIGHT = 0.75
    const val KEEPER_DEPTH = 0.5
    const val KEEPER_REACH = 0.6
    const val DIVE_HIGH_DISTANCE = 3.5
    const val DIVE_LOW_DISTANCE = 3.0
    const val STEP_DISTANCE = 1.0
    const val JUMP_UP_DISTANCE = 0.9
    const val DIVE_TICKS = 20

    // Match / challenge (spec §3).
    const val KICKS_PER_SIDE = 5
    const val MAX_BALLS = 3
    const val MAD_MINUTE_SECONDS = 60.0
    const val MAD_GOAL_POINTS = 100
    const val MAX_CHALLENGE_TICKS = 60 * 60

    /** Reaction timers and random-dive odds by round 0-5 (spec §3). */
    val MAX_REACTION_TIMES = doubleArrayOf(0.5, 0.4, 0.3, 0.2, 0.0, 0.2)
    val RANDOM_DIVE_PROBABILITY = doubleArrayOf(0.5, 0.4, 0.3, 0.2, 0.0, 0.2)

    const val TEAM_COUNT = 32
    const val FIRST_ROUND_MATCHES = 8
    const val ROUNDS = 4
    const val TOTAL_MATCHES = 15
    val ROUND_HEADINGS = listOf("group", "quarter final", "semi final", "final")
    private val ROUND_OFFSETS = intArrayOf(0, 8, 12, 14)

    private val X_BASIS = PenaltyVec3(0.5, 0.0, 0.0)
    private val Y_BASIS = PenaltyVec3(0.0, -0.5, -2.0)

    /** Authored 32-nation pool (spec §5: content must be re-authored). */
    val TEAMS: List<PenaltyTeam> = listOf(
        PenaltyTeam(0, "Algeria", 0xFF2E7D5B.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(1, "Argentina", 0xFF6CA6DC.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(2, "Australia", 0xFFD4A63A.toInt(), 0xFF1F5E3A.toInt()),
        PenaltyTeam(3, "Brazil", 0xFFE7C81E.toInt(), 0xFF1D6B35.toInt()),
        PenaltyTeam(4, "Cameroon", 0xFF2F7A4C.toInt(), 0xFFD2452F.toInt()),
        PenaltyTeam(5, "Chile", 0xFFD03A3A.toInt(), 0xFF2D4E9E.toInt()),
        PenaltyTeam(6, "Colombia", 0xFFE0B33A.toInt(), 0xFF27407E.toInt()),
        PenaltyTeam(7, "Croatia", 0xFFD64545.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(8, "Denmark", 0xFFC63A3A.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(9, "Ecuador", 0xFFE5C231.toInt(), 0xFF25479B.toInt()),
        PenaltyTeam(10, "England", 0xFFE8E8E8.toInt(), 0xFFC0392B.toInt()),
        PenaltyTeam(11, "France", 0xFF25479B.toInt(), 0xFFD64545.toInt()),
        PenaltyTeam(12, "Germany", 0xFF333333.toInt(), 0xFFE0B33A.toInt()),
        PenaltyTeam(13, "Ghana", 0xFFD64545.toInt(), 0xFFE0B33A.toInt()),
        PenaltyTeam(14, "Greece", 0xFF3B6FBE.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(15, "Italy", 0xFF2F5FA8.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(16, "Ivory Coast", 0xFFE07B2A.toInt(), 0xFF2F7A4C.toInt()),
        PenaltyTeam(17, "Japan", 0xFF2D4E9E.toInt(), 0xFFD64545.toInt()),
        PenaltyTeam(18, "Mexico", 0xFF2F7A4C.toInt(), 0xFFD64545.toInt()),
        PenaltyTeam(19, "Morocco", 0xFFC0392B.toInt(), 0xFF2F7A4C.toInt()),
        PenaltyTeam(20, "Netherlands", 0xFFE07B2A.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(21, "Nigeria", 0xFF2F7A4C.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(22, "Paraguay", 0xFFD64545.toInt(), 0xFF2D4E9E.toInt()),
        PenaltyTeam(23, "Portugal", 0xFFC0392B.toInt(), 0xFF1D6B35.toInt()),
        PenaltyTeam(24, "Senegal", 0xFF2F7A4C.toInt(), 0xFFE5C231.toInt()),
        PenaltyTeam(25, "Serbia", 0xFFC0392B.toInt(), 0xFF25479B.toInt()),
        PenaltyTeam(26, "Slovakia", 0xFF2D4E9E.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(27, "South Africa", 0xFFE0B33A.toInt(), 0xFF1D6B35.toInt()),
        PenaltyTeam(28, "South Korea", 0xFFD64545.toInt(), 0xFF25479B.toInt()),
        PenaltyTeam(29, "Spain", 0xFFC0392B.toInt(), 0xFFE0B33A.toInt()),
        PenaltyTeam(30, "Switzerland", 0xFFC0392B.toInt(), 0xFFFFFFFF.toInt()),
        PenaltyTeam(31, "USA", 0xFF2D4E9E.toInt(), 0xFFFFFFFF.toInt()),
    )

    fun team(id: Int): PenaltyTeam = TEAMS[id.coerceIn(0, TEAMS.size - 1)]

    /* ---------------- swipe physics ---------------- */

    /**
     * Swipe speed = recent movement length x30, clamped 350..750, times the
     * stage factor. Arc spin is 3x the 2D cross of the swipe against the
     * running velocity average, clamped +/-0.5 (spec §3).
     */
    fun kickFromSwipe(swipe: PenaltySwipe, stage: PenaltyStage = PenaltyStage.KICKER): PenaltyKick {
        val magnitude = hypot(swipe.vx, swipe.vy) * SWIPE_FORCE_SCALE
        val speed = magnitude.coerceIn(MIN_FORCE, MAX_FORCE) * stage.forceMultiplier
        val swipeNorm = normalize2(swipe.vx, swipe.vy)
        var avgNorm = normalize2(swipe.avgVx, swipe.avgVy)
        if (avgNorm.first == 0.0 && avgNorm.second == 0.0) avgNorm = swipeNorm
        val spin = (SPIN_SCALE * (swipeNorm.first * avgNorm.second - swipeNorm.second * avgNorm.first))
            .coerceIn(-SPIN_MAX, SPIN_MAX)
        val direction = (X_BASIS * avgNorm.first + Y_BASIS * avgNorm.second).normalized()
        return PenaltyKick(direction * speed, spin, speed, direction)
    }

    private fun normalize2(x: Double, y: Double): Pair<Double, Double> {
        val len = hypot(x, y)
        return if (len < 1e-9) 0.0 to 0.0 else (x / len) to (y / len)
    }

    /** Builds a kick that will cross the goal plane at [target] after [flightTime]. */
    fun aimKick(
        target: PenaltyVec3,
        flightTime: Double,
        spin: Double = 0.0,
        stage: PenaltyStage = PenaltyStage.KICKER,
    ): PenaltyKick {
        val start = stage.kickSpot()
        val t = flightTime.coerceAtLeast(0.05)
        val vx = (target.x - start.x) / t
        val vz = (target.z - start.z) / t
        val vy = (target.y - start.y) / t + 0.5 * 9.8 * t
        val velocity = PenaltyVec3(vx, vy, vz)
        val force = velocity * (BALL_MASS / DT)
        return PenaltyKick(force, spin, force.length(), velocity.normalized())
    }

    /** Straight-line estimate of where a kick crosses z = [GOAL_LINE_Z]. */
    fun goalIntersection(kick: PenaltyKick, stage: PenaltyStage = PenaltyStage.KICKER): PenaltyVec3 {
        val start = stage.kickSpot()
        val velocity = kick.force * (DT / BALL_MASS)
        if (abs(velocity.z) < 1e-6) return start
        val t = (GOAL_LINE_Z - start.z) / velocity.z
        if (t <= 0.0) return start
        return PenaltyVec3(
            start.x + velocity.x * t,
            start.y + velocity.y * t - 4.9 * t * t,
            GOAL_LINE_Z,
        )
    }

    /* ---------------- keeper ---------------- */

    fun keeperHome(stage: PenaltyStage): PenaltyVec3 = PenaltyVec3(stage.keeperX, 0.0, stage.keeperZ)

    /** Dive targets honour the 3.5 m high / 3.0 m low / 1.0 m step geometry. */
    fun keeperTarget(zone: KeeperZone, stage: PenaltyStage): PenaltyVec3 {
        val home = keeperHome(stage)
        return when (zone) {
            KeeperZone.HIGH_LEFT -> home + PenaltyVec3(-DIVE_HIGH_DISTANCE, 1.2, 0.0)
            KeeperZone.LOW_LEFT -> home + PenaltyVec3(-DIVE_LOW_DISTANCE, 0.25, 0.0)
            KeeperZone.HIGH_RIGHT -> home + PenaltyVec3(DIVE_HIGH_DISTANCE, 1.2, 0.0)
            KeeperZone.LOW_RIGHT -> home + PenaltyVec3(DIVE_LOW_DISTANCE, 0.25, 0.0)
            KeeperZone.JUMP -> home + PenaltyVec3(0.0, JUMP_UP_DISTANCE, 0.0)
            KeeperZone.HOLD, KeeperZone.NONE -> home
        }
    }

    /**
     * AI keeper decision: reaction time ramps by round, and with the round's
     * random-dive probability the keeper picks a random corner instead of
     * reading the ball. Otherwise it dives/step/jumps according to the
     * estimated goal-plane crossing (spec §3, §4).
     */
    fun aiKeeperPlan(
        target: PenaltyVec3,
        stage: PenaltyStage,
        roundIndex: Int,
        seed: Int,
    ): Pair<PenaltyKeeperPlan, Int> {
        var rng = seed
        val reactionRoll = nextRandom(rng).also { rng = it.first }
        val randomRoll = nextRandom(rng).also { rng = it.first }
        val cornerRoll = nextRandom(rng).also { rng = it.first }
        val reactionMax = if (stage == PenaltyStage.KICKER || stage == PenaltyStage.KEEPER) {
            MAX_REACTION_TIMES[roundIndex.coerceIn(0, MAX_REACTION_TIMES.size - 1)]
        } else {
            stage.reactionMax
        }
        val randomOdds = if (stage == PenaltyStage.KICKER || stage == PenaltyStage.KEEPER) {
            RANDOM_DIVE_PROBABILITY[roundIndex.coerceIn(0, RANDOM_DIVE_PROBABILITY.size - 1)]
        } else {
            stage.randomDiveProb
        }
        val reaction = reactionRoll.second * reactionMax
        val randomDive = randomRoll.second < randomOdds
        val zone = if (randomDive) {
            when {
                cornerRoll.second < 0.25 -> KeeperZone.HIGH_LEFT
                cornerRoll.second < 0.50 -> KeeperZone.LOW_LEFT
                cornerRoll.second < 0.75 -> KeeperZone.HIGH_RIGHT
                else -> KeeperZone.LOW_RIGHT
            }
        } else {
            val dx = target.x - stage.keeperX
            val high = target.y > 1.0
            when {
                abs(dx) > STEP_DISTANCE -> if (dx < 0) {
                    if (high) KeeperZone.HIGH_LEFT else KeeperZone.LOW_LEFT
                } else {
                    if (high) KeeperZone.HIGH_RIGHT else KeeperZone.LOW_RIGHT
                }
                abs(dx) > KEEPER_REACH -> if (dx < 0) KeeperZone.LOW_LEFT else KeeperZone.LOW_RIGHT
                high -> KeeperZone.JUMP
                else -> KeeperZone.HOLD
            }
        }
        return PenaltyKeeperPlan(zone, reaction) to rng
    }

    private fun keeperCenter(stage: PenaltyStage, plan: PenaltyKeeperPlan, t: Double): PenaltyVec3 {
        val home = keeperHome(stage)
        val target = keeperTarget(plan.zone, stage)
        val progress = ((t - plan.reaction) / (DIVE_TICKS * DT)).coerceIn(0.0, 1.0)
        val eased = progress * progress * (3.0 - 2.0 * progress)
        return home + (target - home) * eased
    }

    /** Keeper position at time [t] for canvas playback. */
    fun keeperDisplay(stage: PenaltyStage, zone: KeeperZone, reaction: Double, t: Double): PenaltyVec3 =
        keeperCenter(stage, PenaltyKeeperPlan(zone, reaction), t)

    private fun keeperSaves(ball: PenaltyVec3, keeper: PenaltyVec3): Boolean {
        val dx = abs(ball.x - keeper.x)
        val dz = abs(ball.z - keeper.z)
        val low = keeper.y - KEEPER_REACH
        val high = keeper.y + KEEPER_HEIGHT + KEEPER_REACH
        return dx <= KEEPER_WIDTH / 2 + KEEPER_REACH &&
            dz <= KEEPER_DEPTH / 2 + BALL_RADIUS + 0.15 &&
            ball.y in low..high
    }

    /* ---------------- ball flight ---------------- */

    /**
     * Deterministic point-mass flight with gravity, Magnus-style spin, ground
     * / post / crossbar / net collisions, keeper collision and the stop
     * threshold (spec §3). Returns the sampled trajectory for playback.
     */
    fun simulateShot(
        kick: PenaltyKick,
        keeperZone: KeeperZone = KeeperZone.NONE,
        keeperReaction: Double = 0.0,
        stage: PenaltyStage = PenaltyStage.KICKER,
    ): PenaltyShot {
        val plan = PenaltyKeeperPlan(keeperZone, keeperReaction)
        var pos = stage.kickSpot()
        var vel = kick.force * (DT / BALL_MASS)
        val trajectory = ArrayList<PenaltyVec3>(MAX_TRAJECTORY_TICKS)
        trajectory += pos
        var outcome: KickOutcome? = null
        var keeperTouched = false
        var tick = 0
        while (tick < MAX_TRAJECTORY_TICKS) {
            tick++
            val t = tick * DT
            val keeper = keeperCenter(stage, plan, t)

            val accel = ArrayList<PenaltyVec3>(3)
            accel += PenaltyVec3(0.0, GRAVITY_FORCE / BALL_MASS, 0.0)
            val horizontal = PenaltyVec3(vel.x, 0.0, vel.z)
            val speed = vel.length()
            if (abs(kick.spin) > 1e-9 && horizontal.length() > 1e-9) {
                val perpendicular = PenaltyVec3(horizontal.z, 0.0, -horizontal.x).normalized()
                val magnitude = speed * SPIN_FORCE_SCALE * kick.spin
                accel += perpendicular * (magnitude / BALL_MASS)
            }
            for (a in accel) vel += a * DT
            val previous = pos
            pos += vel * DT

            // Ground: keep the ball on or above the turf.
            if (pos.y < BALL_RADIUS) {
                pos = pos.copy(y = BALL_RADIUS)
                if (vel.y < 0.0) vel = vel.copy(y = -vel.y * GROUND_BOUNCE)
            }

            // Goal posts (vertical cylinders at the goal line).
            for (postX in listOf(GOAL_POST_LEFT_X, GOAL_POST_RIGHT_X)) {
                if (pos.y > CROSSBAR_HEIGHT + BALL_RADIUS) continue
                if (pos.z < GOAL_LINE_Z - 0.05 || pos.z > GOAL_LINE_Z + stage.goalDepth) continue
                val nx = pos.x - postX
                val nz = pos.z - GOAL_LINE_Z
                val distance = hypot(nx, nz)
                val minDistance = BALL_RADIUS + POST_RADIUS
                if (distance < minDistance && distance > 1e-9) {
                    val normal = PenaltyVec3(nx / distance, 0.0, nz / distance)
                    pos = PenaltyVec3(postX, pos.y, GOAL_LINE_Z) + normal * minDistance
                    val vn = vel.x * normal.x + vel.z * normal.z
                    if (vn < 0.0) {
                        vel = PenaltyVec3(
                            vel.x - (1.0 + POST_BOUNCE) * vn * normal.x,
                            vel.y,
                            vel.z - (1.0 + POST_BOUNCE) * vn * normal.z,
                        )
                    }
                }
            }

            // Crossbar (horizontal cylinder along x at the goal line).
            if (abs(pos.x) <= GOAL_HALF_WIDTH + BALL_RADIUS) {
                val dy = pos.y - CROSSBAR_HEIGHT
                val dz = pos.z - GOAL_LINE_Z
                val distance = hypot(dy, dz)
                val minDistance = BALL_RADIUS + POST_RADIUS
                if (distance < minDistance && distance > 1e-9) {
                    val ny = dy / distance
                    val nz = dz / distance
                    pos = PenaltyVec3(pos.x, CROSSBAR_HEIGHT + ny * minDistance, GOAL_LINE_Z + nz * minDistance)
                    val vn = vel.y * ny + vel.z * nz
                    if (vn < 0.0) {
                        vel = PenaltyVec3(
                            vel.x,
                            vel.y - (1.0 + POST_BOUNCE) * vn * ny,
                            vel.z - (1.0 + POST_BOUNCE) * vn * nz,
                        )
                    }
                }
            }

            // Goal line crossing: inside the mouth counts.
            if (outcome == null && previous.z < GOAL_LINE_Z && pos.z >= GOAL_LINE_Z) {
                val insideX = abs(pos.x) <= GOAL_HALF_WIDTH - BALL_RADIUS
                val insideY = pos.y >= BALL_RADIUS && pos.y <= CROSSBAR_HEIGHT - BALL_RADIUS
                if (insideX && insideY) outcome = KickOutcome.GOAL
            }

            // Net absorbs the ball once it is over the line.
            if (outcome == KickOutcome.GOAL && pos.z > GOAL_LINE_Z + stage.goalDepth - BALL_RADIUS) {
                pos = pos.copy(z = GOAL_LINE_Z + stage.goalDepth - BALL_RADIUS)
                vel = vel.copy(z = -abs(vel.z) * NET_BOUNCE, x = vel.x * 0.5, y = vel.y * 0.5)
            }

            // Keeper contact before the line saves the shot.
            if (outcome == null && keeperSaves(pos, keeper)) {
                keeperTouched = true
                outcome = KickOutcome.SAVED
                trajectory += pos
                break
            }

            trajectory += pos

            val moved = (pos - previous).length()
            if (moved < STOP_DISTANCE && pos.y <= BALL_RADIUS + 0.02) break
            if (pos.z > GOAL_LINE_Z + stage.goalDepth + 2.0) break
            if (abs(pos.x) > 15.0 || pos.y > 10.0 || pos.z < stage.kickZ - 3.0) break
        }
        val finalOutcome = outcome ?: if (keeperTouched) KickOutcome.SAVED else KickOutcome.MISS
        return PenaltyShot(finalOutcome, trajectory, keeperZone, kick.spin, 1)
    }

    /**
     * Simulates a keeper-side kick for the player: the AI aims (seeded), the
     * player's held zone and reaction form the keeper plan.
     */
    fun simulatePlayerDefence(
        zone: KeeperZone,
        reaction: Double,
        stage: PenaltyStage,
        roundIndex: Int,
        seed: Int,
    ): Pair<PenaltyShot, Int> {
        val aiRoll = aiKick(seed, stage, roundIndex)
        val shot = simulateShot(aiRoll.first, zone, reaction, stage)
        return shot to aiRoll.second
    }

    /** Seeded AI shot: mostly on target, occasionally wide or over. */
    fun aiKick(seed: Int, stage: PenaltyStage = PenaltyStage.KICKER, roundIndex: Int = 0): Pair<PenaltyKick, Int> {
        var rng = seed
        val r1 = nextRandom(rng).also { rng = it.first }
        val r2 = nextRandom(rng).also { rng = it.first }
        val r3 = nextRandom(rng).also { rng = it.first }
        val r4 = nextRandom(rng).also { rng = it.first }
        val missOdds = 0.15 + roundIndex * 0.02
        val offTarget = r4.second < missOdds
        val x = when {
            !offTarget -> -3.1 + 6.2 * r1.second
            r1.second < 0.5 -> -4.6
            else -> 4.6
        }
        val y = if (offTarget) 3.0 else 0.25 + 1.6 * r2.second
        val flight = 0.55 + 0.35 * r3.second
        return aimKick(PenaltyVec3(x, y, GOAL_LINE_Z), flight, stage = stage) to rng
    }

    /* ---------------- match rules ---------------- */

    fun newMatch(playerTeam: Int, opponentTeam: Int, roundIndex: Int): PenaltyMatch =
        PenaltyMatch(playerTeam, opponentTeam, roundIndex)

    fun matchPhase(match: PenaltyMatch): MatchPhase = when {
        match.finished -> MatchPhase.DONE
        match.nextIsPlayer -> MatchPhase.SHOOT
        else -> MatchPhase.DEFEND
    }

    /**
     * Records a kick and resolves five-kick regulation followed by paired
     * sudden-death kicks decided by the first differential (spec §3).
     */
    fun recordShot(match: PenaltyMatch, outcome: KickOutcome): PenaltyMatch {
        if (match.finished) return match
        val next = if (match.nextIsPlayer) {
            match.copy(playerStrip = match.playerStrip + outcome)
        } else {
            match.copy(opponentStrip = match.opponentStrip + outcome)
        }
        return settleMatch(next)
    }

    private fun settleMatch(match: PenaltyMatch): PenaltyMatch {
        val playerKicks = match.playerStrip.size
        val opponentKicks = match.opponentStrip.size
        if (playerKicks < KICKS_PER_SIDE || opponentKicks < KICKS_PER_SIDE) return match
        if (playerKicks != opponentKicks) return match.copy(suddenDeath = true)
        val difference = match.playerGoals - match.opponentGoals
        return if (difference == 0) {
            match.copy(suddenDeath = true)
        } else {
            match.copy(finished = true, playerWon = difference > 0)
        }
    }

    /* ---------------- bracket ---------------- */

    fun newTournament(playerTeamIndex: Int, seed: Int): TournamentState {
        val playerTeam = playerTeamIndex.coerceIn(0, TEAM_COUNT - 1)
        var rng = seed
        val pool = (0 until TEAM_COUNT).filter { it != playerTeam }.toMutableList()
        // Seeded Fisher-Yates.
        for (i in pool.indices.reversed()) {
            val roll = nextRandom(rng).also { rng = it.first }
            val j = (roll.second * (i + 1)).toInt().coerceIn(0, i)
            val tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp
        }
        val playerSlotRoll = nextRandom(rng).also { rng = it.first }
        val playerSlot = (playerSlotRoll.second * FIRST_ROUND_MATCHES).toInt().coerceIn(0, FIRST_ROUND_MATCHES - 1)
        val matches = ArrayList<PenaltyBracketMatch>(TOTAL_MATCHES)
        repeat(TOTAL_MATCHES) { matches += PenaltyBracketMatch(round = -1, slot = -1, home = -1, away = -1) }
        var draw = 0
        for (slot in 0 until FIRST_ROUND_MATCHES) {
            val home: Int
            val away: Int
            if (slot == playerSlot) {
                home = playerTeam
                away = pool[draw++]
            } else {
                home = pool[draw++]
                away = pool[draw++]
            }
            matches[slot] = PenaltyBracketMatch(0, slot, home, away)
        }
        for (round in 1 until ROUNDS) {
            val slots = FIRST_ROUND_MATCHES shr round
            val offset = roundOffset(round)
            for (slot in 0 until slots) {
                matches[offset + slot] = PenaltyBracketMatch(round, slot, -1, -1)
            }
        }
        val opponent = matches[playerSlot].away
        return TournamentState(
            playerTeam = playerTeam,
            matches = matches,
            round = 0,
            playerSlot = playerSlot,
            opponent = opponent,
            match = newMatch(playerTeam, opponent, 0),
            seed = seed,
            rngState = rng,
        )
    }

    fun roundOffset(round: Int): Int = if (round in ROUND_OFFSETS.indices) ROUND_OFFSETS[round] else 0

    fun roundMatches(round: Int): Int = FIRST_ROUND_MATCHES shr round

    /**
     * Called when the player's match is finished: other fixtures are
     * simulated, winners advance, and a loss resets the tournament (spec §3).
     */
    fun advanceTournament(state: TournamentState, newSeed: Int = state.seed): TournamentState {
        if (state.status != TournamentStatus.PLAYING || !state.match.finished) return state
        var rng = newSeed
        val round = state.round
        val offset = roundOffset(round)
        val count = roundMatches(round)
        val matches = state.matches.toMutableList()

        // Record the player's own fixture.
        val playerIndex = offset + state.playerSlot
        matches[playerIndex] = matches[playerIndex].copy(
            homeScore = state.match.playerGoals,
            awayScore = state.match.opponentGoals,
            winner = if (state.match.playerWon) state.playerTeam else state.opponent,
        )

        // Simulate the rest of the round.
        for (slot in 0 until count) {
            val index = offset + slot
            if (index == playerIndex) continue
            val fixture = matches[index]
            val roll = nextRandom(rng).also { rng = it.first }
            val homeScore = (roll.second * 5.0).toInt().coerceIn(0, 4)
            val awayRoll = nextRandom(rng).also { rng = it.first }
            val awayScore = (awayRoll.second * 5.0).toInt().coerceIn(0, 4)
            val decided = if (homeScore == awayScore) homeScore + 1 else homeScore
            val winner = if (homeScore >= awayScore) fixture.home else fixture.away
            matches[index] = fixture.copy(
                homeScore = if (winner == fixture.home) decided else homeScore,
                awayScore = if (winner == fixture.away) decided else awayScore,
                winner = winner,
            )
        }

        if (!state.match.playerWon) {
            return state.copy(matches = matches, status = TournamentStatus.LOST, rngState = rng)
        }
        if (round >= ROUNDS - 1) {
            return state.copy(matches = matches, status = TournamentStatus.WON, rngState = rng)
        }

        // Build the next round from the winners.
        val nextRound = round + 1
        val nextOffset = roundOffset(nextRound)
        val nextCount = roundMatches(nextRound)
        var nextPlayerSlot = -1
        var opponent = -1
        for (slot in 0 until nextCount) {
            val home = matches[offset + slot * 2].winner
            val away = matches[offset + slot * 2 + 1].winner
            matches[nextOffset + slot] = PenaltyBracketMatch(nextRound, slot, home, away)
            if (home == state.playerTeam || away == state.playerTeam) {
                nextPlayerSlot = slot
                opponent = if (home == state.playerTeam) away else home
            }
        }
        return state.copy(
            matches = matches,
            round = nextRound,
            playerSlot = nextPlayerSlot,
            opponent = opponent,
            match = newMatch(state.playerTeam, opponent, nextRound),
            rngState = rng,
        )
    }

    /* ---------------- Mad Minute ---------------- */

    fun newMadMinute(seed: Int = (System.nanoTime() ushr 3).toInt()): MadMinuteState {
        var rng = seed
        val pickupRoll = nextRandom(rng).also { rng = it.first }
        val xRoll = nextRandom(rng).also { rng = it.first }
        val pickup = PenaltyPowerup.entries[(pickupRoll.second * PenaltyPowerup.entries.size).toInt().coerceIn(0, 3)]
        return MadMinuteState(
            seed = seed,
            rngState = rng,
            nextPickup = pickup,
            pickupX = -3.0 + 6.0 * xRoll.second,
        )
    }

    /** Fires a Mad Minute kick; the ball resolves later during [madMinuteStep]. */
    fun madMinuteKick(state: MadMinuteState, swipe: PenaltySwipe, stage: PenaltyStage = PenaltyStage.KICKER): MadMinuteState {
        return madMinuteKick(state, kickFromSwipe(swipe, stage), stage)
    }

    /** Deterministic variant used by tests and the tutorial. */
    fun madMinuteKick(state: MadMinuteState, kick: PenaltyKick, stage: PenaltyStage = PenaltyStage.KICKER): MadMinuteState {
        if (state.over || state.countdown > 0.0 || state.cooldown > 0.0) return state
        if (state.liveBalls.size >= MAX_BALLS) return state
        var rng = state.rngState
        val target = goalIntersection(kick, stage)
        val planRoll = aiKeeperPlan(target, stage, 0, rng)
        rng = planRoll.second
        val shot = simulateShot(kick, planRoll.first.zone, planRoll.first.reaction, stage)

        // Vuvuzelas deflect shots aimed within their reach.
        val vuvuzelaX = -3.4 + 6.8 * nextRandom(rng).let { rng = it.first; it.second }
        var goal = shot.outcome == KickOutcome.GOAL
        var deflected = false
        if (goal && abs(target.x - vuvuzelaX) < 0.4) {
            goal = false
            deflected = true
        }

        val pickupRoll = nextRandom(rng).also { rng = it.first }
        val pickupX = -3.0 + 6.0 * pickupRoll.second
        val pickup = state.nextPickup
        val picked = pickup != null && abs(target.x - pickupX) < 0.7

        val duration = shot.trajectory.size * DT
        val ball = MadBall(
            trajectory = shot.trajectory,
            spawnAt = state.clock,
            duration = duration,
            goal = goal,
            scoreValue = MAD_GOAL_POINTS,
            deflected = deflected,
        )
        var balls = state.liveBalls + ball
        val frenzy = state.frenzyLeft > 0.0
        if (frenzy && balls.size < MAX_BALLS) {
            val extraTarget = PenaltyVec3((target.x + 1.4).coerceIn(-3.2, 3.2), target.y, GOAL_LINE_Z)
            val extra = simulateShot(aimKick(extraTarget, 0.7, stage = stage), planRoll.first.zone, planRoll.first.reaction, stage)
            balls = balls + ball.copy(trajectory = extra.trajectory, goal = extra.outcome == KickOutcome.GOAL)
        }
        balls = balls.take(MAX_BALLS)

        var next = state.copy(
            liveBalls = balls,
            cooldown = if (state.speedupLeft > 0.0) 0.15 else 0.4,
            nextPickup = PenaltyPowerup.entries[(pickupRoll.second * PenaltyPowerup.entries.size).toInt().coerceIn(0, 3)],
            pickupX = -3.0 + 6.0 * nextRandom(rng).let { rng = it.first; it.second },
            rngState = rng,
        )
        if (picked) next = applyPowerup(next, pickup!!)
        return next
    }

    fun applyPowerup(state: MadMinuteState, powerup: PenaltyPowerup): MadMinuteState = when (powerup) {
        PenaltyPowerup.TIME_FREEZE -> state.copy(freezeLeft = max(state.freezeLeft, 5.0))
        PenaltyPowerup.POINTS_BONUS -> state.copy(bonusLeft = max(state.bonusLeft, 8.0))
        PenaltyPowerup.BALL_RESET_SPEEDUP -> state.copy(speedupLeft = max(state.speedupLeft, 8.0))
        PenaltyPowerup.BALL_FRENZY -> state.copy(frenzyLeft = max(state.frenzyLeft, 8.0))
    }

    /** Fixed-step clock: countdown, time freeze, powerup timers and ball resolution. */
    fun madMinuteStep(state: MadMinuteState, dt: Double): MadMinuteState {
        if (state.over || dt <= 0.0) return state
        var next = state.copy(clock = state.clock + dt)
        next = if (next.countdown > 0.0) {
            next.copy(countdown = (next.countdown - dt).coerceAtLeast(0.0))
        } else {
            val timeLeft = if (next.freezeLeft > 0.0) next.timeLeft else (next.timeLeft - dt).coerceAtLeast(0.0)
            next.copy(
                timeLeft = timeLeft,
                freezeLeft = (next.freezeLeft - dt).coerceAtLeast(0.0),
                bonusLeft = (next.bonusLeft - dt).coerceAtLeast(0.0),
                speedupLeft = (next.speedupLeft - dt).coerceAtLeast(0.0),
                frenzyLeft = (next.frenzyLeft - dt).coerceAtLeast(0.0),
            )
        }
        next = next.copy(cooldown = (next.cooldown - dt).coerceAtLeast(0.0))

        val due = next.liveBalls.filter { next.clock - it.spawnAt >= it.duration }
        if (due.isNotEmpty()) {
            var score = next.score
            var streak = next.streak
            var best = next.bestStreak
            for (ball in due) {
                if (ball.goal) {
                    streak++
                    val multiplier = if (next.bonusLeft > 0.0) 2 else 1
                    score += ball.scoreValue * multiplier + (streak - 1) * 10
                    best = max(best, streak)
                } else {
                    streak = 0
                }
            }
            next = next.copy(
                score = score,
                streak = streak,
                bestStreak = best,
                liveBalls = next.liveBalls.filterNot { it in due },
                outcomes = next.outcomes + due.map { if (it.goal) KickOutcome.GOAL else KickOutcome.MISS },
            )
        }
        return if (next.timeLeft <= 0.0) next.copy(timeLeft = 0.0, over = true, cooldown = 0.0) else next
    }

    /* ---------------- Goal Streak ---------------- */

    fun newGoalStreak(seed: Int = (System.nanoTime() ushr 4).toInt(), stage: PenaltyStage = PenaltyStage.SD1): GoalStreakState =
        GoalStreakState(stage = stage, seed = seed, rngState = seed)

    fun goalStreakTake(state: GoalStreakState, swipe: PenaltySwipe): Pair<GoalStreakState, PenaltyShot> =
        goalStreakTake(state, kickFromSwipe(swipe, state.stage))

    /** Goals extend the streak; a miss or save resets it, best persists. */
    fun goalStreakRecord(state: GoalStreakState, outcome: KickOutcome): GoalStreakState {
        val streak = if (outcome == KickOutcome.GOAL) state.streak + 1 else 0
        return state.copy(
            streak = streak,
            best = max(state.best, streak),
            attempts = state.attempts + 1,
            last = outcome,
        )
    }

    fun goalStreakTake(state: GoalStreakState, kick: PenaltyKick): Pair<GoalStreakState, PenaltyShot> {
        val target = goalIntersection(kick, state.stage)
        val planRoll = aiKeeperPlan(target, state.stage, 0, state.rngState)
        val shot = simulateShot(kick, planRoll.first.zone, planRoll.first.reaction, state.stage)
        return goalStreakRecord(state.copy(rngState = planRoll.second), shot.outcome) to shot
    }

    /* ---------------- persistence ---------------- */

    fun encodeTournament(state: TournamentState): String {
        val matches = state.matches.joinToString("/") { m ->
            listOf(m.round, m.slot, m.home, m.away, m.homeScore, m.awayScore, m.winner).joinToString(":")
        }
        return listOf(
            "pt1",
            state.playerTeam,
            state.round,
            state.playerSlot,
            state.opponent,
            state.status.ordinal,
            state.seed,
            state.rngState,
            state.bestMadMinute,
            state.bestStreak,
            encodeStrip(state.match.playerStrip),
            encodeStrip(state.match.opponentStrip),
            if (state.match.suddenDeath) "1" else "0",
            if (state.match.finished) "1" else "0",
            if (state.match.playerWon) "1" else "0",
            matches,
        ).joinToString(";")
    }

    fun decodeTournament(blob: String): TournamentState? = try {
        val parts = blob.split(";")
        if (parts.size < 16 || parts[0] != "pt1") return null
        val playerTeam = parts[1].toInt()
        val matches = parts[15].split("/").mapNotNull { entry ->
            val f = entry.split(":")
            if (f.size < 7) return@mapNotNull null
            PenaltyBracketMatch(f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt())
        }
        if (matches.size != TOTAL_MATCHES) return null
        val round = parts[2].toInt().coerceIn(0, ROUNDS - 1)
        val opponent = parts[4].toInt()
        val match = PenaltyMatch(
            playerTeam = playerTeam,
            opponentTeam = opponent,
            roundIndex = round,
            playerStrip = decodeStrip(parts[10]),
            opponentStrip = decodeStrip(parts[11]),
            suddenDeath = parts[12] == "1",
            finished = parts[13] == "1",
            playerWon = parts[14] == "1",
        )
        TournamentState(
            playerTeam = playerTeam,
            matches = matches,
            round = round,
            playerSlot = parts[3].toInt(),
            opponent = opponent,
            match = match,
            status = TournamentStatus.entries.getOrNull(parts[5].toInt()) ?: TournamentStatus.PLAYING,
            seed = parts[6].toInt(),
            rngState = parts[7].toInt(),
            bestMadMinute = parts[8].toInt(),
            bestStreak = parts[9].toInt(),
        )
    } catch (_: Exception) {
        null
    }

    private fun encodeStrip(strip: List<KickOutcome>): String =
        if (strip.isEmpty()) "-" else strip.joinToString(",") { it.ordinal.toString() }

    private fun decodeStrip(value: String): List<KickOutcome> =
        if (value == "-" || value.isEmpty()) emptyList()
        else value.split(",").mapNotNull { KickOutcome.entries.getOrNull(it.toIntOrNull() ?: return@mapNotNull null) }

    /* ---------------- deterministic RNG ---------------- */

    /** LCG step returning (newState, value in 0..1). */
    fun nextRandom(state: Int): Pair<Int, Double> {
        val next = state * 1664525 + 1013904223
        val value = ((next ushr 8) and 0xFFFF) / 65535.0
        return next to value
    }

    /** Deterministic pseudo-random integer for non-gameplay flavour. */
    fun seedFrom(text: String): Int = text.fold(17) { acc, c -> acc * 31 + c.code }
}
