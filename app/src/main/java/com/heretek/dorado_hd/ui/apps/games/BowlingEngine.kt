package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/* ============================================================ */
/*              Lucky Lanes Bowling — rules + simulation         */
/* ============================================================ */

/**
 * Lucky Lanes Bowling (official `bowling.exe` re-creation). Pure Kotlin and
 * deterministic: behaviour is re-derived from `docs/apps/lucky-lanes-bowling.md`
 * and the constants it cites, with no copied code, lane data or assets. Our
 * five lanes, ball skins and rivals are original.
 */

// Reference geometry (behavioural spec): 42-unit lane, 720 units long, ball
// released at y=4.78 / z=-765 behind the foul line. The simulation advances
// in fixed 4 ms sub-steps so a replay is frame-rate independent.
const val BOWLING_LANE_LENGTH = 720
const val BOWLING_LANE_WIDTH = 42
const val BOWLING_BALL_START_Y = 4.78f
const val BOWLING_BALL_START_Z = -765f
const val BOWLING_NUM_PINS = 10

/** The reference catch-up constant: 8 simulation sub-steps per 32 ms tick. */
const val BOWLING_SIM_THROW_TIME = 8

// Throw classifier clamps (touch arc: min swipe 30, velocity 0.35..0.95).
const val BOWLING_BALL_MIN_VELOCITY = 0.35f
const val BOWLING_BALL_MAX_VELOCITY = 0.95f
const val BOWLING_BALL_VELOCITY_MODIFIER = 0.95f
const val BOWLING_SPIN_VELOCITY_MODIFIER = 1.3f
const val BOWLING_MIN_SWIPE = 30f

// Mode constants from the spec.
const val BOWLING_BLACKJACK = 21
const val BOWLING_NUM_BLACKJACK_FRAMES = 3
const val BOWLING_MAX_GOLF_SHOTS = 5
const val BOWLING_MAX_GOLF_SCORE = 999

const val BOWLING_LANE_HALF = BOWLING_LANE_WIDTH / 2f
const val BOWLING_GUTTER_WIDTH = 6f
const val BOWLING_BALL_RADIUS = 4.5f
const val BOWLING_PIN_RADIUS = 2.4f
const val BOWLING_PIN_HEIGHT = 15f
const val BOWLING_DECK_FRONT_Z = -130f
const val BOWLING_PIT_Z = 0f

private const val SUBSTEP_MS = 32 / BOWLING_SIM_THROW_TIME
private const val DT = SUBSTEP_MS / 1000f
private const val SPEED_SCALE = 900f
private const val ANGLE_SPEED = 34f
private const val HOOK_ACCEL = 210f
private const val LANE_SLOW = 0.35f
private const val GUTTER_FRICTION = 2.2f
private const val PIN_DECK_FRICTION = 16f
private const val BALL_PIN_TRANSFER = 1.04f
private const val BALL_PIN_RECOIL = 0.14f
private const val PIN_PIN_TRANSFER = 0.82f
private const val PIN_PIN_RECOIL = 0.55f
private const val KNOCK_SPEED = 24f
private const val PIN_TILT_SPEED = 5.5f
private const val SETTLE_SPEED = 2.5f
private const val PIN_REST_SPEED = 6f
private const val BALL_SETTLE_SPEED = 10f
private const val MIN_SHOT_MS = 320
private const val MAX_SHOT_MS = 7000
private val PIN_FLAT = (PI / 2.0).toFloat()

/** Game modes from the spec. */
enum class BowlingMode(val label: String) {
    EXHIBITION("exhibition"),
    BLACKJACK("blackjack"),
    GOLF("golf"),
}

/** FullRound (10 frames) or the shorter league HalfRound (5). */
enum class BowlingLength(val label: String, val frames: Int) {
    FULL("full round", 10),
    HALF("half round", 5),
}

/**
 * Our five original lanes. [friction] is longitudinal deceleration (u/s^2),
 * [hook] the lateral acceleration scale (u/s^2), [oilLength] the fraction of
 * the lane before hook engages, [deck] the pin-deck restitution, and the rgb
 * fields drive the original decor palette.
 */
enum class BowlingLane(
    val label: String,
    val friction: Float,
    val hook: Float,
    val oilLength: Float,
    val deck: Float,
    val surfaceRgb: Int,
    val backRgb: Int,
    val glowRgb: Int,
) {
    PINERY("pinery", 28f, 34f, 0.66f, 0.50f, 0x7A5230, 0x2A1B10, 0xFFC46B),
    ARCADE("neon arcade", 18f, 92f, 0.32f, 0.64f, 0x1C2A4A, 0x0A0E1C, 0x46E0FF),
    DUNES("sunset dunes", 38f, 26f, 0.76f, 0.44f, 0x96613A, 0x39220F, 0xFF8B45),
    GROTTO("tidal grotto", 24f, 64f, 0.48f, 0.56f, 0x1E4E48, 0x08211F, 0x5CF2C6),
    ORBIT("stardust station", 14f, 104f, 0.26f, 0.70f, 0x2C2B40, 0x0F0F1A, 0xC9A0FF),
}

/** Our own ball roster; [grip] scales the hook the timing meter can impart. */
enum class BowlingBall(val label: String, val rgb: Int, val grip: Float) {
    COMET("comet", 0xF0F0F0, 1.0f),
    EMBER("ember", 0xE04B2A, 0.9f),
    JADE("jade", 0x2CB57E, 1.0f),
    TIDE("tide", 0x3388E0, 0.95f),
    LASER("laser", 0xC9A0FF, 1.05f),
}

/** Original CPU rivals; [skill] 0..1 drives the throw distribution. */
enum class BowlingRival(val label: String, val skill: Float) {
    WREN("wren", 0.40f),
    DASH("dash", 0.54f),
    MORROW("morrow", 0.63f),
    KESTREL("kestrel", 0.73f),
    VESPER("vesper", 0.82f),
}

/**
 * A classified release: [aim] lateral start (-1..1), [speed] swipe power (0..1),
 * [angle] launch direction (-1..1), [spin] the timing meter's hook reading
 * (-1..1) and [accuracy] how close the meter stopped to its sweet spot.
 */
data class BowlingThrow(
    val aim: Float,
    val speed: Float,
    val angle: Float,
    val spin: Float,
    val accuracy: Float = 1f,
)

/** Classic ten-pin rack, authored from standard 12-unit pin spacing. */
private val PIN_HOME_X = floatArrayOf(0f, -6f, 6f, -12f, 0f, 12f, -18f, -6f, 6f, 18f)
private val PIN_HOME_Z = floatArrayOf(-96f, -84f, -84f, -72f, -72f, -72f, -60f, -60f, -60f, -60f)

fun pinHomeX(index: Int): Float = PIN_HOME_X[index]

fun pinHomeZ(index: Int): Float = PIN_HOME_Z[index]

/** One pin. [inPlay] is false for pins already down from a previous ball. */
data class BowlingPin(
    val index: Int,
    val x: Float,
    val z: Float,
    val vx: Float = 0f,
    val vz: Float = 0f,
    val tilt: Float = 0f,
    val tiltX: Float = 0f,
    val tiltZ: Float = 0f,
    val standing: Boolean = true,
    val offDeck: Boolean = false,
    val inPlay: Boolean = true,
)

data class BowlingBallState(
    val x: Float,
    val z: Float,
    val y: Float,
    val vx: Float,
    val vz: Float,
    val spin: Float,
    val roll: Float = 0f,
    val inGutter: Boolean = false,
    val done: Boolean = false,
)

/**
 * A single throw's deterministic simulation. [startStanding] records which
 * pins were up when the ball was released so knockdowns can be counted after
 * the rack settles.
 */
data class BowlingShot(
    val lane: BowlingLane,
    val ball: BowlingBallState,
    val pins: List<BowlingPin>,
    val startStanding: List<Boolean>,
    val seed: Int,
    val rng: Int,
    val elapsedMs: Int = 0,
    val pendingMs: Int = 0,
    val done: Boolean = false,
    val gutter: Boolean = false,
    val firstContactX: Float? = null,
) {
    fun standingMask(): List<Boolean> = pins.map { it.standing }

    fun felledCount(): Int =
        startStanding.indices.count { startStanding[it] && !pins[it].standing }

    fun knockedIndices(): List<Int> =
        startStanding.indices.filter { startStanding[it] && !pins[it].standing }
}

enum class FrameMark { NONE, STRIKE, SPARE }

data class BowlingFrame(val rolls: List<Int> = emptyList()) {
    val pins: Int get() = rolls.sum()
}

/** A scorecard. Roll/pin accounting follows the classic frame rules. */
data class BowlingCard(
    val mode: BowlingMode,
    val length: BowlingLength,
    val frames: List<BowlingFrame>,
    val frameIndex: Int = 0,
    val finished: Boolean = false,
) {
    val rollCount: Int get() = frames.sumOf { it.rolls.size }
    val totalPins: Int get() = frames.sumOf { it.pins }
    val currentFrame: BowlingFrame get() = frames[min(frameIndex, frames.size - 1)]
}

/** Per-mode rule table (asserted by tests). */
data class BowlingRules(
    val frames: Int,
    val rollsPerFrame: Int,
    val bonusRolls: Boolean,
    val strikeSpareBonus: Boolean,
    val maxShotsPerFrame: Int,
    val lowerWins: Boolean,
    val targetTotal: Int?,
)

/** Series progress kept in `graph.appState`. */
data class BowlingSeries(
    val played: Int = 0,
    val wins: Int = 0,
    val best: Int = 0,
    val strikes: Int = 0,
    val spares: Int = 0,
    val turkeys: Int = 0,
)

/** Saved in-progress card plus its standing-pin mask. */
data class BowlingSave(val card: BowlingCard, val standing: List<Boolean>)

enum class BowlingPhase { READY, ROLLING, SETTLED, FINISHED }

data class BowlingMatch(
    val mode: BowlingMode,
    val length: BowlingLength,
    val lane: BowlingLane,
    val ball: BowlingBall,
    val rival: BowlingRival,
    val card: BowlingCard,
    val standing: List<Boolean>,
    val seed: Int,
    val phase: BowlingPhase = BowlingPhase.READY,
    val shot: BowlingShot? = null,
    val lastFelled: Int = 0,
    val lastRollFrame: Int = -1,
    val dealerRolls: List<Int> = emptyList(),
    val dealerTotal: Int = 0,
)

object BowlingEngine {

    /* ----------------------------- rules ----------------------------- */

    fun rulesFor(mode: BowlingMode, length: BowlingLength): BowlingRules = when (mode) {
        BowlingMode.EXHIBITION -> BowlingRules(length.frames, 2, true, true, 3, false, null)
        BowlingMode.BLACKJACK -> BowlingRules(
            BOWLING_NUM_BLACKJACK_FRAMES, 1, false, false, 1, false, BOWLING_BLACKJACK,
        )
        BowlingMode.GOLF -> BowlingRules(length.frames, 1, false, false, BOWLING_MAX_GOLF_SHOTS, true, BOWLING_NUM_PINS)
    }

    fun newCard(mode: BowlingMode, length: BowlingLength): BowlingCard {
        val rules = rulesFor(mode, length)
        return BowlingCard(mode, length, List(rules.frames) { BowlingFrame() })
    }

    fun frameComplete(
        mode: BowlingMode,
        length: BowlingLength,
        index: Int,
        frame: BowlingFrame,
    ): Boolean {
        val rolls = frame.rolls
        if (rolls.isEmpty()) return false
        return when (mode) {
            BowlingMode.BLACKJACK -> true
            BowlingMode.GOLF -> rolls.sum() >= BOWLING_NUM_PINS || rolls.size >= BOWLING_MAX_GOLF_SHOTS
            BowlingMode.EXHIBITION -> {
                if (index < length.frames - 1) {
                    rolls.size >= 2 || rolls[0] == BOWLING_NUM_PINS
                } else {
                    when {
                        rolls.size >= 3 -> true
                        rolls.size == 2 -> rolls[0] != BOWLING_NUM_PINS &&
                            rolls[0] + rolls[1] < BOWLING_NUM_PINS
                        else -> false
                    }
                }
            }
        }
    }

    fun frameMark(card: BowlingCard, index: Int): FrameMark {
        val rolls = card.frames.getOrNull(index)?.rolls ?: return FrameMark.NONE
        if (rolls.isEmpty()) return FrameMark.NONE
        return when (card.mode) {
            BowlingMode.BLACKJACK -> FrameMark.NONE
            BowlingMode.GOLF -> if (rolls.sum() >= BOWLING_NUM_PINS) FrameMark.SPARE else FrameMark.NONE
            BowlingMode.EXHIBITION -> when {
                rolls[0] == BOWLING_NUM_PINS -> FrameMark.STRIKE
                rolls.size >= 2 && rolls[0] != BOWLING_NUM_PINS &&
                    rolls[0] + rolls[1] == BOWLING_NUM_PINS -> FrameMark.SPARE
                else -> FrameMark.NONE
            }
        }
    }

    /** Rolls as the scorecard prints them (X, /, digits, -). */
    fun displayRolls(card: BowlingCard, index: Int): List<String> {
        val rolls = card.frames.getOrNull(index)?.rolls ?: return emptyList()
        return rolls.mapIndexed { i, pins ->
            when {
                pins >= BOWLING_NUM_PINS -> "X"
                i == 1 && rolls[0] != BOWLING_NUM_PINS && rolls[0] + pins == BOWLING_NUM_PINS -> "/"
                i == 2 && rolls[1] != BOWLING_NUM_PINS && rolls[1] + pins == BOWLING_NUM_PINS -> "/"
                pins == 0 -> "-"
                else -> pins.toString()
            }
        }
    }

    /**
     * Append one roll. [felled] is 0..10; the card advances frames and closes
     * the game exactly where the rules say (bonus rolls included).
     */
    fun applyRoll(card: BowlingCard, felled: Int): BowlingCard {
        if (card.finished) return card
        val index = card.frameIndex
        val frame = card.frames[index]
        if (frameComplete(card.mode, card.length, index, frame)) return card
        val pins = felled.coerceIn(0, BOWLING_NUM_PINS)
        val updated = frame.copy(rolls = frame.rolls + pins)
        val frames = card.frames.toMutableList().also { it[index] = updated }
        val done = frameComplete(card.mode, card.length, index, updated)
        val totalPins = frames.sumOf { it.pins }
        val bust = card.mode == BowlingMode.BLACKJACK && totalPins > BOWLING_BLACKJACK
        val lastIndex = frames.size - 1
        val finished = bust || (done && index >= lastIndex)
        val nextIndex = if (done && index < lastIndex && !bust) index + 1 else index
        return card.copy(frames = frames, frameIndex = nextIndex, finished = finished)
    }

    private fun nextRolls(card: BowlingCard, frameIndex: Int, count: Int): Int? {
        val out = ArrayList<Int>(count)
        var i = frameIndex + 1
        while (i < card.frames.size && out.size < count) {
            for (roll in card.frames[i].rolls) {
                if (out.size >= count) break
                out += roll
            }
            i++
        }
        return if (out.size == count) out.sum() else null
    }

    /** Cumulative total after each frame; null until a frame can be scored. */
    fun scores(card: BowlingCard): List<Int?> {
        val out = ArrayList<Int?>(card.frames.size)
        when (card.mode) {
            BowlingMode.GOLF -> {
                var running = 0
                for (frame in card.frames) {
                    running += frame.rolls.sum()
                    out += running
                }
            }
            BowlingMode.BLACKJACK -> {
                var running = 0
                for (frame in card.frames) {
                    running += frame.rolls.sum()
                    out += running
                }
            }
            BowlingMode.EXHIBITION -> {
                var running = 0
                var blocked = false
                val last = card.frames.size - 1
                for (i in card.frames.indices) {
                    if (blocked) {
                        out += null
                        continue
                    }
                    val value = frameScore(card, i, last)
                    if (value == null) {
                        blocked = true
                        out += null
                    } else {
                        running += value
                        out += running
                    }
                }
            }
        }
        return out
    }

    private fun frameScore(card: BowlingCard, index: Int, last: Int): Int? {
        val frame = card.frames[index]
        if (frame.rolls.isEmpty()) return null
        if (index == last) {
            return if (frameComplete(card.mode, card.length, index, frame)) frame.pins else null
        }
        val rolls = frame.rolls
        if (rolls[0] == BOWLING_NUM_PINS) {
            val bonus = nextRolls(card, index, 2) ?: return null
            return BOWLING_NUM_PINS + bonus
        }
        if (rolls.size < 2) return null
        if (rolls[0] + rolls[1] == BOWLING_NUM_PINS) {
            val bonus = nextRolls(card, index, 1) ?: return null
            return BOWLING_NUM_PINS + bonus
        }
        return frame.pins
    }

    fun totalScore(card: BowlingCard): Int = when (card.mode) {
        BowlingMode.GOLF, BowlingMode.BLACKJACK -> card.totalPins
        BowlingMode.EXHIBITION -> scores(card).filterNotNull().lastOrNull() ?: 0
    }

    fun golfShots(card: BowlingCard): Int = card.totalPins

    fun golfPar(card: BowlingCard): Int = card.frames.size * 2

    fun isBust(card: BowlingCard): Boolean =
        card.mode == BowlingMode.BLACKJACK && card.totalPins > BOWLING_BLACKJACK

    /**
     * Rack reset rule: a fresh rack after a strike, a completed spare, an empty
     * frame or any frame change; golf keeps standing pins within a hole.
     */
    fun shouldResetPins(card: BowlingCard, rolledIndex: Int): Boolean {
        if (card.frameIndex != rolledIndex) return true
        val rolls = card.frames.getOrNull(rolledIndex)?.rolls ?: return true
        if (rolls.isEmpty()) return true
        if (card.mode == BowlingMode.GOLF) return false
        if (card.mode == BowlingMode.BLACKJACK) return true
        if (rolls.last() == BOWLING_NUM_PINS) return true
        return rolls.size == 2 && rolls[0] != BOWLING_NUM_PINS &&
            rolls[0] + rolls[1] == BOWLING_NUM_PINS
    }

    /* --------------------------- simulation --------------------------- */

    fun startShot(
        lane: BowlingLane,
        standing: List<Boolean>,
        params: BowlingThrow,
        ball: BowlingBall,
        seed: Int,
    ): BowlingShot {
        val aim = params.aim.coerceIn(-1f, 1f)
        val power = params.speed.coerceIn(0f, 1f)
        val accuracy = params.accuracy.coerceIn(0f, 1f)
        val spin = params.spin.coerceIn(-1f, 1f) * ball.grip
        val (rng, jitter) = lcg(seed)
        val angle = (params.angle + (1f - accuracy) * 0.22f * jitter).coerceIn(-1f, 1f)
        val speed = (BOWLING_BALL_MIN_VELOCITY +
            (BOWLING_BALL_MAX_VELOCITY - BOWLING_BALL_MIN_VELOCITY) * power) *
            BOWLING_BALL_VELOCITY_MODIFIER * SPEED_SCALE
        val pins = List(BOWLING_NUM_PINS) { i ->
            val up = standing.getOrElse(i) { true }
            BowlingPin(
                index = i,
                x = PIN_HOME_X[i],
                z = PIN_HOME_Z[i],
                standing = up,
                inPlay = up,
            )
        }
        return BowlingShot(
            lane = lane,
            ball = BowlingBallState(
                x = aim * (BOWLING_LANE_HALF - BOWLING_BALL_RADIUS),
                z = BOWLING_BALL_START_Z,
                y = BOWLING_BALL_START_Y,
                vx = angle * ANGLE_SPEED,
                vz = speed,
                spin = spin,
            ),
            pins = pins,
            startStanding = standing,
            seed = seed,
            rng = rng,
        )
    }

    /** Advance by [millis]; only fixed sub-steps run, so replays are stable. */
    fun tickShot(shot: BowlingShot, millis: Int): BowlingShot {
        if (shot.done || millis <= 0) return shot
        var current = shot.copy(pendingMs = shot.pendingMs + millis)
        while (current.pendingMs >= SUBSTEP_MS && !current.done) {
            current = substep(current).copy(
                pendingMs = current.pendingMs - SUBSTEP_MS,
                elapsedMs = current.elapsedMs + SUBSTEP_MS,
            )
        }
        if (!current.done && current.elapsedMs >= MAX_SHOT_MS) current = current.copy(done = true)
        return current
    }

    /** Run a throw to rest; used by CPU throws and tests. */
    fun simulateShot(
        lane: BowlingLane,
        standing: List<Boolean>,
        params: BowlingThrow,
        ball: BowlingBall,
        seed: Int,
    ): BowlingShot {
        var shot = startShot(lane, standing, params, ball, seed)
        var guard = 0
        while (!shot.done && guard < 1024) {
            shot = tickShot(shot, 32)
            guard++
        }
        return shot
    }

    private fun substep(shot: BowlingShot): BowlingShot {
        val lane = shot.lane
        var ball = shot.ball
        var rng = shot.rng
        var firstContact = shot.firstContactX
        var gutter = shot.gutter
        val pins = shot.pins.toMutableList()

        if (!ball.done) {
            var vx = ball.vx
            var vz = ball.vz
            if (ball.inGutter) {
                val speed = hypot(vx, vz)
                if (speed > 1e-3f) {
                    val drop = lane.friction * GUTTER_FRICTION * DT
                    vx -= (vx / speed) * min(drop, speed)
                    vz -= (vz / speed) * min(drop, speed)
                }
            } else {
                val travel = ball.z - BOWLING_BALL_START_Z
                if (travel > lane.oilLength * BOWLING_LANE_LENGTH) {
                    vx += ball.spin * lane.hook * DT
                }
                val speed = hypot(vx, vz)
                if (speed > 1e-3f) {
                    val drop = min(lane.friction * DT, speed)
                    vz -= (vz / speed) * drop
                    vx -= (vx / speed) * drop * LANE_SLOW
                }
            }
            var x = ball.x + vx * DT
            var z = ball.z + vz * DT
            val y = max(BOWLING_BALL_RADIUS, ball.y - 26f * DT)
            var inGutter = ball.inGutter
            if (!inGutter && abs(x) > BOWLING_LANE_HALF - BOWLING_BALL_RADIUS * 0.5f) {
                inGutter = true
                gutter = true
                x = (if (x < 0f) -1f else 1f) * (BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH / 2f)
                vx = 0f
            }
            var roll = ball.roll + hypot(vx, vz) * DT / BOWLING_BALL_RADIUS
            var done = z >= BOWLING_PIT_Z - 6f
            if (!done && inGutter && z >= BOWLING_DECK_FRONT_Z) done = true
            if (!done && hypot(vx, vz) < BALL_SETTLE_SPEED && z > BOWLING_DECK_FRONT_Z) done = true

            if (!inGutter && z > BOWLING_DECK_FRONT_Z - 24f) {
                for (i in pins.indices) {
                    val pin = pins[i]
                    if (!pin.inPlay || pin.offDeck) continue
                    val dx = pin.x - x
                    val dz = pin.z - z
                    val dist = hypot(dx, dz)
                    val minDist = BOWLING_BALL_RADIUS + BOWLING_PIN_RADIUS
                    if (dist >= minDist || dist < 1e-4f) continue
                    val nx = dx / dist
                    val nz = dz / dist
                    val rel = (vx - pin.vx) * nx + (vz - pin.vz) * nz
                    if (rel <= 0f) continue
                    val impulse = rel * BALL_PIN_TRANSFER
                    val pinVx = pin.vx + nx * impulse
                    val pinVz = pin.vz + nz * impulse
                    val pinSpeed = hypot(pinVx, pinVz)
                    val toppled = pin.standing && pinSpeed >= KNOCK_SPEED
                    val stays = pin.standing && !toppled
                    val push = minDist - dist
                    x -= nx * push
                    z -= nz * push
                    vx -= nx * impulse * BALL_PIN_RECOIL
                    vz -= nz * impulse * BALL_PIN_RECOIL
                    pins[i] = pin.copy(
                        vx = pinVx,
                        vz = pinVz,
                        standing = stays,
                        tiltX = if (toppled) nx else pin.tiltX,
                        tiltZ = if (toppled) nz else pin.tiltZ,
                    )
                    if (firstContact == null) firstContact = x
                }
            }
            ball = ball.copy(
                x = x,
                z = z,
                y = y,
                vx = vx,
                vz = vz,
                roll = roll,
                inGutter = inGutter,
                done = done,
            )
        }

        // Pin-on-pin chain: a single ordered pass per sub-step keeps the
        // outcome deterministic while still carrying energy through the rack.
        for (i in pins.indices) {
            val a = pins[i]
            if (!a.inPlay || a.offDeck) continue
            if (a.standing && hypot(a.vx, a.vz) < 1f) continue
            for (j in pins.indices) {
                if (i == j) continue
                val b = pins[j]
                if (!b.inPlay || b.offDeck) continue
                val dx = b.x - a.x
                val dz = b.z - a.z
                val dist = hypot(dx, dz)
                val minDist = BOWLING_PIN_RADIUS * 2f
                if (dist >= minDist || dist < 1e-4f) continue
                val nx = dx / dist
                val nz = dz / dist
                val rel = (a.vx - b.vx) * nx + (a.vz - b.vz) * nz
                if (rel <= 0f) continue
                val (nextRng, jitter) = lcg(rng)
                rng = nextRng
                val transfer = rel * PIN_PIN_TRANSFER * (1f + jitter * 0.05f)
                val bvx = b.vx + nx * transfer
                val bvz = b.vz + nz * transfer
                val bSpeed = hypot(bvx, bvz)
                val toppled = b.standing && bSpeed >= KNOCK_SPEED
                pins[j] = b.copy(
                    vx = bvx,
                    vz = bvz,
                    standing = b.standing && !toppled,
                    tiltX = if (toppled) nx else b.tiltX,
                    tiltZ = if (toppled) nz else b.tiltZ,
                )
                pins[i] = pins[i].copy(
                    vx = pins[i].vx - nx * transfer * PIN_PIN_RECOIL,
                    vz = pins[i].vz - nz * transfer * PIN_PIN_RECOIL,
                )
            }
        }

        for (i in pins.indices) {
            val pin = pins[i]
            if (!pin.inPlay || pin.offDeck) continue
            val speed = hypot(pin.vx, pin.vz)
            var vx = pin.vx
            var vz = pin.vz
            if (speed > 1e-3f) {
                val drop = min(PIN_DECK_FRICTION * DT, speed)
                vx -= (vx / speed) * drop
                vz -= (vz / speed) * drop
            }
            val x = pin.x + vx * DT
            val z = pin.z + vz * DT
            val tilt = if (pin.standing) pin.tilt else min(PIN_FLAT, pin.tilt + PIN_TILT_SPEED * DT)
            val off = z > BOWLING_PIT_Z || abs(x) > BOWLING_LANE_HALF + BOWLING_GUTTER_WIDTH
            pins[i] = pin.copy(x = x, z = z, vx = vx, vz = vz, tilt = tilt, offDeck = off)
        }

        val pinsMoving = pins.any { pin ->
            if (!pin.inPlay || pin.offDeck) return@any false
            val speed = hypot(pin.vx, pin.vz)
            if (pin.standing) {
                speed > SETTLE_SPEED
            } else {
                pin.tilt < PIN_FLAT - 0.02f || speed > PIN_REST_SPEED
            }
        }
        val done = shot.elapsedMs >= MIN_SHOT_MS && ball.done && !pinsMoving
        return shot.copy(
            ball = ball,
            pins = pins,
            rng = rng,
            firstContactX = firstContact,
            gutter = gutter,
            done = done,
        )
    }

    /* ------------------------------- CPU ------------------------------ */

    fun cpuThrow(rival: BowlingRival, seed: Int, index: Int): BowlingThrow {
        val rng = Random(seed * 131 + index * 17 + 5)
        val skill = rival.skill.coerceIn(0f, 1f)
        val aim = (rng.nextFloat() * 2f - 1f) * (1f - skill) * 0.7f
        val speed = (0.55f + skill * 0.32f + rng.nextFloat() * 0.06f)
            .coerceIn(BOWLING_BALL_MIN_VELOCITY, BOWLING_BALL_MAX_VELOCITY)
        val angle = (rng.nextFloat() * 2f - 1f) * (1f - skill) * 0.5f
        val spin = (rng.nextFloat() * 2f - 1f) * skill * 0.8f
        val accuracy = (skill + rng.nextFloat() * 0.2f).coerceIn(0f, 1f)
        return BowlingThrow(aim, speed, angle, spin, accuracy)
    }

    /* ------------------------------ match ----------------------------- */

    fun newMatch(
        mode: BowlingMode,
        length: BowlingLength,
        lane: BowlingLane,
        ball: BowlingBall,
        rival: BowlingRival,
        seed: Int,
    ): BowlingMatch = BowlingMatch(
        mode = mode,
        length = length,
        lane = lane,
        ball = ball,
        rival = rival,
        card = newCard(mode, length),
        standing = List(BOWLING_NUM_PINS) { true },
        seed = seed,
    )

    fun release(match: BowlingMatch, params: BowlingThrow): BowlingMatch {
        if (match.phase != BowlingPhase.READY) return match
        val shotSeed = match.seed + match.card.rollCount * 7919
        val shot = startShot(match.lane, match.standing, params, match.ball, shotSeed)
        return match.copy(shot = shot, phase = BowlingPhase.ROLLING)
    }

    fun resumeMatch(
        card: BowlingCard,
        standing: List<Boolean>,
        lane: BowlingLane,
        ball: BowlingBall,
        rival: BowlingRival,
        seed: Int,
    ): BowlingMatch = BowlingMatch(
        mode = card.mode,
        length = card.length,
        lane = lane,
        ball = ball,
        rival = rival,
        card = card,
        standing = standing,
        seed = seed,
        phase = if (card.finished) BowlingPhase.FINISHED else BowlingPhase.READY,
    )

    /** Step a rolling throw; on rest the roll is applied to the scorecard. */
    fun advance(match: BowlingMatch, millis: Int): BowlingMatch {
        if (match.phase != BowlingPhase.ROLLING) return match
        val shot = match.shot ?: return match.copy(phase = BowlingPhase.READY)
        val stepped = tickShot(shot, millis)
        if (!stepped.done) return match.copy(shot = stepped)
        val felled = stepped.felledCount()
        val rolledIndex = match.card.frameIndex
        val card = applyRoll(match.card, felled)
        val reset = shouldResetPins(card, rolledIndex)
        val standing = if (reset) List(BOWLING_NUM_PINS) { true } else stepped.standingMask()
        val finished = card.finished
        val dealer = if (finished && match.mode == BowlingMode.BLACKJACK) {
            dealerRolls(match)
        } else {
            emptyList()
        }
        return match.copy(
            card = card,
            standing = standing,
            shot = stepped,
            phase = if (finished) BowlingPhase.FINISHED else BowlingPhase.SETTLED,
            lastFelled = felled,
            lastRollFrame = rolledIndex,
            dealerRolls = dealer,
            dealerTotal = dealer.sum(),
        )
    }

    fun nextShot(match: BowlingMatch): BowlingMatch {
        if (match.phase != BowlingPhase.SETTLED) return match
        return match.copy(shot = null, phase = BowlingPhase.READY, lastFelled = 0)
    }

    private fun dealerRolls(match: BowlingMatch): List<Int> {
        val out = ArrayList<Int>(BOWLING_NUM_BLACKJACK_FRAMES)
        for (i in 0 until BOWLING_NUM_BLACKJACK_FRAMES) {
            val params = cpuThrow(match.rival, match.seed, 300 + i)
            val shot = simulateShot(
                match.lane,
                List(BOWLING_NUM_PINS) { true },
                params,
                match.ball,
                match.seed + 900 + i,
            )
            out += shot.felledCount()
        }
        return out
    }

    fun matchScore(match: BowlingMatch): Int = when (match.mode) {
        BowlingMode.GOLF -> golfShots(match.card)
        else -> totalScore(match.card)
    }

    fun matchWon(match: BowlingMatch): Boolean = when (match.mode) {
        BowlingMode.EXHIBITION -> totalScore(match.card) >= 150
        BowlingMode.GOLF -> match.card.finished && golfShots(match.card) <= golfPar(match.card)
        BowlingMode.BLACKJACK -> !isBust(match.card) && match.dealerTotal <= BOWLING_BLACKJACK &&
            totalScore(match.card) >= match.dealerTotal
    }

    /** Leaderboard value: golf rewards fewer shots, so it is inverted. */
    fun recordValue(match: BowlingMatch): Int = when (match.mode) {
        BowlingMode.GOLF -> (BOWLING_MAX_GOLF_SCORE - golfShots(match.card)).coerceAtLeast(0)
        else -> matchScore(match)
    }

    fun applySeries(series: BowlingSeries, match: BowlingMatch): BowlingSeries {
        val card = match.card
        val strikes = card.frames.indices.count { frameMark(card, it) == FrameMark.STRIKE }
        val spares = card.frames.indices.count { frameMark(card, it) == FrameMark.SPARE }
        var turkeys = 0
        var run = 0
        for (i in card.frames.indices) {
            if (frameMark(card, i) == FrameMark.STRIKE) {
                run++
                if (run == 3) turkeys++
            } else {
                run = 0
            }
        }
        return series.copy(
            played = series.played + 1,
            wins = series.wins + if (matchWon(match)) 1 else 0,
            best = max(series.best, recordValue(match)),
            strikes = series.strikes + strikes,
            spares = series.spares + spares,
            turkeys = series.turkeys + turkeys,
        )
    }

    /* ----------------------------- storage ---------------------------- */

    fun encodeSave(save: BowlingSave): String {
        val mask = save.standing.joinToString("") { if (it) "1" else "0" }
        val rolls = save.card.frames.joinToString(",") { frame ->
            frame.rolls.joinToString("-")
        }
        return "${save.card.mode.name}:${save.card.length.name}:${save.card.frameIndex}:" +
            "${if (save.card.finished) 1 else 0}:$mask|$rolls"
    }

    fun decodeSave(text: String?): BowlingSave? {
        if (text.isNullOrBlank()) return null
        return try {
            val sections = text.split("|")
            if (sections.size != 2) return null
            val head = sections[0].split(":")
            if (head.size != 5) return null
            val mode = BowlingMode.valueOf(head[0])
            val length = BowlingLength.valueOf(head[1])
            val frameIndex = head[2].toInt()
            val finished = head[3] == "1"
            val mask = head[4].padEnd(BOWLING_NUM_PINS, '1').take(BOWLING_NUM_PINS)
                .map { it == '1' }
            val frames = if (sections[1].isEmpty()) {
                emptyList()
            } else {
                sections[1].split(",").map { chunk ->
                    if (chunk.isEmpty()) BowlingFrame() else {
                        BowlingFrame(chunk.split("-").mapNotNull { it.toIntOrNull() })
                    }
                }
            }
            val expected = rulesFor(mode, length).frames
            val cards = List(expected) { i -> frames.getOrElse(i) { BowlingFrame() } }
            val card = BowlingCard(
                mode = mode,
                length = length,
                frames = cards,
                frameIndex = frameIndex.coerceIn(0, expected - 1),
                finished = finished,
            )
            BowlingSave(card, mask)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeSeries(series: BowlingSeries): String = listOf(
        series.played, series.wins, series.best, series.strikes, series.spares, series.turkeys,
    ).joinToString(",")

    fun decodeSeries(text: String?): BowlingSeries {
        if (text.isNullOrBlank()) return BowlingSeries()
        val parts = text.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size < 6) return BowlingSeries()
        return BowlingSeries(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
    }

    /* ---------------------------- helpers ----------------------------- */

    private fun lcg(state: Int): Pair<Int, Float> {
        val next = state * 1103515245 + 12345
        val value = ((next ushr 16) and 0x7FFF) / 16383.5f - 1f
        return next to value
    }
}
