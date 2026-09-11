package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs

/**
 * Clean-room re-derivation of Tug-O-War's rhythm tug mechanics. Behaviour is
 * taken from docs/apps/tug-o-war.md; no Microsoft code, strings or assets are
 * involved. All positions use the mined rope coordinate space: the rope starts
 * at [TugOWarEngine.ROPE_START], the player wins at [TugOWarEngine.ROPE_PLAYER_WIN]
 * and the opponent at [TugOWarEngine.ROPE_OPPONENT_WIN].
 */
enum class TugInputMode { TAP, DRAG, SHAKE }

enum class TugSide { PLAYER, OPPONENT }

enum class TugStatus { PLAYING, ROUND_OVER, MATCH_OVER }

/** One side's input velocity register and rhythm bookkeeping. */
data class TugRhythm(
    val velocity: Double = 0.0,
    val lastPullMs: Long? = null,
    val intervalMs: Long? = null,
)

data class TugState(
    val mode: TugInputMode = TugInputMode.TAP,
    val hotseat: Boolean = false,
    val round: Int = 1,
    val ropeY: Double = TugOWarEngine.ROPE_START,
    val player: TugRhythm = TugRhythm(),
    val opponent: TugRhythm = TugRhythm(),
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val difficulty: Double = TugOWarEngine.AI_START_DIFFICULTY,
    val clockMs: Long = 0L,
    val status: TugStatus = TugStatus.PLAYING,
    val roundWinner: TugSide? = null,
    val matchWinner: TugSide? = null,
)

object TugOWarEngine {
    const val ROUND_COUNT = 10
    const val AI_START_DIFFICULTY = 0.05
    const val AI_DIFFICULTY_STEP = 0.05
    const val PULL_AMOUNT = 2.0
    const val TIME_TOLERANCE_MS = 400L
    const val NO_INPUT_TIME_MS = 500L
    const val DECAY_PER_SECOND = 1.0
    const val FRAME_MS = 1000.0 / 60.0

    const val ROPE_START = -360.0
    const val ROPE_CENTRE = -360.0
    const val ROPE_PLAYER_WIN = -222.0
    const val ROPE_OPPONENT_WIN = -498.0
    const val ROPE_TRAVEL = 138.0

    const val TIER_MEDIUM = 10.0
    const val TIER_FAST = 20.0
    const val TIER_BLAZING = 30.0
    const val WOBBLE_PX = 4.0
    const val SHAKE_TINT_MAX = 180

    const val TAP_PLAYER_MIN_Y = 378f
    const val TAP_OPPONENT_MAX_Y = 102f
    const val DRAG_THRESHOLD_PX = 50f
    const val DRAG_PLAYER_MIN_X = 170f
    const val DRAG_OPPONENT_MAX_X = 102f
    const val SHAKE_THRESHOLD = 0.55
    const val DEGREES_PER_G = 57.2957795

    fun newMatch(mode: TugInputMode, hotseat: Boolean): TugState =
        TugState(mode = mode, hotseat = hotseat)

    /** Resolve a single pull from one side at [nowMs] on the input's clock. */
    fun pull(state: TugState, side: TugSide, nowMs: Long, amount: Double = PULL_AMOUNT): TugState {
        if (state.status != TugStatus.PLAYING) return state
        val rhythm = if (side == TugSide.PLAYER) state.player else state.opponent
        val updated = when {
            rhythm.lastPullMs == null -> rhythm.copy(
                velocity = rhythm.velocity + amount,
                lastPullMs = nowMs,
            )

            rhythm.intervalMs == null -> rhythm.copy(
                velocity = rhythm.velocity + amount,
                lastPullMs = nowMs,
                intervalMs = (nowMs - (rhythm.lastPullMs ?: nowMs)).coerceAtLeast(0L),
            )

            else -> {
                val interval = rhythm.intervalMs ?: 0L
                val expected = (rhythm.lastPullMs ?: nowMs) + interval
                val inWindow = abs(nowMs - expected) <= TIME_TOLERANCE_MS
                rhythm.copy(
                    velocity = if (inWindow) {
                        rhythm.velocity + amount
                    } else {
                        (rhythm.velocity - 2.0 * amount).coerceAtLeast(0.0)
                    },
                    lastPullMs = nowMs,
                )
            }
        }
        return if (side == TugSide.PLAYER) state.copy(player = updated) else state.copy(opponent = updated)
    }

    /**
     * Advance the live match. The AI (single player) adds [difficulty] units per
     * 60 Hz frame; velocity decays 1/s after [NO_INPUT_TIME_MS] of silence; the
     * rope integrates (playerVelocity - opponentVelocity) in px/s.
     */
    fun step(state: TugState, dtMs: Long): TugState {
        if (state.status != TugStatus.PLAYING || dtMs <= 0L) return state
        val dtSec = dtMs / 1000.0
        val frames = dtMs / FRAME_MS
        val idleMs = state.clockMs + dtMs
        var player = decay(state.player, idleMs, dtMs)
        var opponent = decay(state.opponent, idleMs, dtMs)
        if (!state.hotseat) {
            opponent = opponent.copy(velocity = opponent.velocity + state.difficulty * frames)
        }
        val rope = state.ropeY + (player.velocity - opponent.velocity) * dtSec
        val next = state.copy(
            clockMs = state.clockMs + dtMs,
            player = player,
            opponent = opponent,
            ropeY = rope,
        )
        return when {
            rope >= ROPE_PLAYER_WIN -> resolveRound(next, TugSide.PLAYER)
            rope <= ROPE_OPPONENT_WIN -> resolveRound(next, TugSide.OPPONENT)
            else -> next
        }
    }

    /** Dismiss a round-results page and reset the rope for the next round. */
    fun continueRound(state: TugState): TugState =
        if (state.status == TugStatus.ROUND_OVER) {
            state.copy(
                status = TugStatus.PLAYING,
                roundWinner = null,
                ropeY = ROPE_START,
                player = TugRhythm(),
                opponent = TugRhythm(),
            )
        } else {
            state
        }

    private fun decay(rhythm: TugRhythm, nowMs: Long, dtMs: Long): TugRhythm {
        val last = rhythm.lastPullMs ?: return rhythm
        if (nowMs - last <= NO_INPUT_TIME_MS) return rhythm
        val drop = DECAY_PER_SECOND * dtMs / 1000.0
        return rhythm.copy(velocity = (rhythm.velocity - drop).coerceAtLeast(0.0))
    }

    private fun resolveRound(state: TugState, winner: TugSide): TugState {
        var playerScore = state.playerScore
        var opponentScore = state.opponentScore
        var difficulty = state.difficulty
        if (state.hotseat) {
            if (winner == TugSide.PLAYER) playerScore++ else opponentScore++
        } else if (winner == TugSide.PLAYER) {
            playerScore++
            difficulty += AI_DIFFICULTY_STEP
        } else {
            playerScore = 0
            difficulty = AI_START_DIFFICULTY
        }
        val nextRound = state.round + 1
        val matchOver = nextRound > ROUND_COUNT
        val matchWinner: TugSide? = if (!matchOver) {
            null
        } else if (state.hotseat) {
            when {
                playerScore > opponentScore -> TugSide.PLAYER
                opponentScore > playerScore -> TugSide.OPPONENT
                else -> null
            }
        } else {
            if (playerScore > 0) TugSide.PLAYER else TugSide.OPPONENT
        }
        return state.copy(
            playerScore = playerScore,
            opponentScore = opponentScore,
            difficulty = difficulty,
            round = nextRound,
            status = if (matchOver) TugStatus.MATCH_OVER else TugStatus.ROUND_OVER,
            roundWinner = if (matchOver) null else winner,
            matchWinner = matchWinner,
            ropeY = if (matchOver) state.ropeY else ROPE_START,
            player = if (matchOver) state.player else TugRhythm(),
            opponent = if (matchOver) state.opponent else TugRhythm(),
        )
    }

    /** Tap-mode zone: the player owns y > 378, hot-seat player two y < 102. */
    fun tapRegisters(state: TugState, side: TugSide, y: Float): Boolean = when (side) {
        TugSide.PLAYER -> y > TAP_PLAYER_MIN_Y
        TugSide.OPPONENT -> state.hotseat && y < TAP_OPPONENT_MAX_Y
    }

    /** Drag-mode rule: >50 px downward in the side's half of the screen. */
    fun dragRegisters(state: TugState, side: TugSide, startY: Float, endY: Float, x: Float): Boolean {
        if (endY - startY <= DRAG_THRESHOLD_PX) return false
        return when (side) {
            TugSide.PLAYER -> x > DRAG_PLAYER_MIN_X
            TugSide.OPPONENT -> state.hotseat && x < DRAG_OPPONENT_MAX_X
        }
    }

    fun shakeMagnitude(dx: Double, dy: Double, dz: Double): Double = abs(dx) + abs(dy) + abs(dz)

    fun isShake(dx: Double, dy: Double, dz: Double): Boolean = shakeMagnitude(dx, dy, dz) > SHAKE_THRESHOLD

    /**
     * Shake detection from the shared tilt helper. Roll/pitch are gimbal angles,
     * so the change is converted back to g units before applying the same
     * threshold.
     */
    fun isShakeFromTilt(deltaRollDeg: Double, deltaPitchDeg: Double): Boolean =
        (abs(deltaRollDeg) + abs(deltaPitchDeg)) / DEGREES_PER_G > SHAKE_THRESHOLD

    /** Rope art tier: 0 centre, 1..3 for a velocity difference over 10/20/30. */
    fun ropeTier(velocityDifference: Double): Int {
        val magnitude = abs(velocityDifference)
        return when {
            magnitude > TIER_BLAZING -> 3
            magnitude > TIER_FAST -> 2
            magnitude > TIER_MEDIUM -> 1
            else -> 0
        }
    }

    fun ropeDisplacement(state: TugState): Double = state.ropeY - ROPE_START

    /** True when the rope sits above centre (opponent pulling). */
    fun shakeTintBlue(state: TugState): Boolean = state.ropeY < ROPE_CENTRE

    fun shakeTintAlpha(state: TugState): Int {
        val fraction = (abs(ropeDisplacement(state)) / ROPE_TRAVEL).coerceIn(0.0, 1.0)
        return (fraction * SHAKE_TINT_MAX).toInt().coerceIn(0, SHAKE_TINT_MAX)
    }

    fun encode(state: TugState): String = listOf(
        "tw1",
        state.mode.ordinal.toString(),
        if (state.hotseat) "1" else "0",
        state.round.toString(),
        state.ropeY.toString(),
        state.player.velocity.toString(),
        (state.player.lastPullMs ?: -1L).toString(),
        (state.player.intervalMs ?: -1L).toString(),
        state.opponent.velocity.toString(),
        (state.opponent.lastPullMs ?: -1L).toString(),
        (state.opponent.intervalMs ?: -1L).toString(),
        state.playerScore.toString(),
        state.opponentScore.toString(),
        state.difficulty.toString(),
        state.clockMs.toString(),
        state.status.ordinal.toString(),
        (state.roundWinner?.ordinal ?: -1).toString(),
        (state.matchWinner?.ordinal ?: -1).toString(),
    ).joinToString(";")

    fun decode(blob: String): TugState? = try {
        val p = blob.split(";")
        if (p.size < 18 || p[0] != "tw1") return null
        val mode = TugInputMode.entries.getOrNull(p[1].toInt()) ?: return null
        fun optional(value: Long): Long? = if (value < 0L) null else value
        TugState(
            mode = mode,
            hotseat = p[2] == "1",
            round = p[3].toInt(),
            ropeY = p[4].toDouble(),
            player = TugRhythm(
                velocity = p[5].toDouble(),
                lastPullMs = optional(p[6].toLong()),
                intervalMs = optional(p[7].toLong()),
            ),
            opponent = TugRhythm(
                velocity = p[8].toDouble(),
                lastPullMs = optional(p[9].toLong()),
                intervalMs = optional(p[10].toLong()),
            ),
            playerScore = p[11].toInt(),
            opponentScore = p[12].toInt(),
            difficulty = p[13].toDouble(),
            clockMs = p[14].toLong(),
            status = TugStatus.entries.getOrNull(p[15].toInt()) ?: return null,
            roundWinner = TugSide.entries.getOrNull(p[16].toInt()),
            matchWinner = TugSide.entries.getOrNull(p[17].toInt()),
        )
    } catch (_: Exception) {
        null
    }
}
