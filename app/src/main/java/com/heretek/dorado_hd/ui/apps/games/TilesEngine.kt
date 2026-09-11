package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs

/**
 * Tiles — 3x3 sliding-number puzzle, clean-room engine.
 *
 * Constants from docs/apps/tiles.md §3: a 9-slot board, a 1000-swap legal
 * shuffle, an 8-tick countdown that re-shuffles once per second, best defaults
 * of 500 moves / 600 s, and wins/losses persisted with the audio flag.
 *
 * Value 8 is the blank sprite; the solved board holds the blank in slot 0 with
 * the numbered tiles ascending across the rest (slots 1..7 hold 0..6).
 */
const val TILES_SIZE = 9
const val TILES_BLANK = 8
const val TILES_COUNTDOWN = 8
const val TILES_SHUFFLE_SWAPS = 1000
const val TILES_DEFAULT_MOVES = 500
const val TILES_DEFAULT_TIME = 600

enum class TilesMode { CLASSIC, TIME_TRIAL }

enum class TilesPhase { COUNTDOWN, PLAY, WON }

data class TilesState(
    val mode: TilesMode,
    val slots: List<Int>,
    val swapCount: Int = 0,
    val elapsedMs: Long = 0L,
    val countdown: Int = TILES_COUNTDOWN,
    val phase: TilesPhase = TilesPhase.COUNTDOWN,
    val seed: Int = 1,
    val rngState: Int = 1,
    val tickAccumMs: Long = 0L,
)

data class TilesStats(
    val leastMoves: Int = TILES_DEFAULT_MOVES,
    val leastTime: Int = TILES_DEFAULT_TIME,
    val wins: Int = 0,
    val losses: Int = 0,
    val audioEnabled: Boolean = true,
)

object TilesEngine {

    const val SIZE = TILES_SIZE
    const val BLANK = TILES_BLANK
    const val DEFAULT_MOVES = TILES_DEFAULT_MOVES
    const val DEFAULT_TIME = TILES_DEFAULT_TIME

    class TilesRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun next(bound: Int): Int =
            if (bound <= 0) 0 else ((nextSeed() ushr 16) and 0x7FFF) % bound
    }

    fun solved(): List<Int> = listOf(8, 0, 1, 2, 3, 4, 5, 6, 7)

    fun isSolved(slots: List<Int>): Boolean {
        if (slots.size != SIZE) return false
        for (i in 1 until SIZE - 1) {
            if (slots[i] != i - 1) return false
        }
        return slots[0] == BLANK
    }

    fun blankIndex(slots: List<Int>): Int = slots.indexOf(BLANK)

    fun isAdjacent(a: Int, b: Int): Boolean {
        if (a !in 0 until SIZE || b !in 0 until SIZE) return false
        val rows = 3
        return abs(a / rows - b / rows) + abs(a % rows - b % rows) == 1
    }

    fun isLegalMove(slots: List<Int>, index: Int): Boolean =
        index in 0 until SIZE &&
            slots.getOrNull(index) != BLANK &&
            isAdjacent(index, blankIndex(slots))

    fun shuffle(slots: List<Int>, rng: TilesRandom, swaps: Int = TILES_SHUFFLE_SWAPS): List<Int> {
        val out = slots.toMutableList()
        if (out.size != SIZE) return out
        repeat(swaps) {
            val pick = rng.next(SIZE)
            val blank = out.indexOf(BLANK)
            if (pick != blank && isAdjacent(pick, blank)) {
                val tmp = out[pick]
                out[pick] = out[blank]
                out[blank] = tmp
            }
        }
        return out
    }

    fun newGame(mode: TilesMode, seed: Int): TilesState {
        val rng = TilesRandom(seed)
        val slots = shuffle(solved(), rng)
        return TilesState(mode = mode, slots = slots, seed = seed, rngState = rng.state)
    }

    fun tap(state: TilesState, index: Int): TilesState {
        if (state.phase != TilesPhase.PLAY) return state
        if (!isLegalMove(state.slots, index)) return state
        val blank = blankIndex(state.slots)
        val out = state.slots.toMutableList()
        val tmp = out[index]
        out[index] = out[blank]
        out[blank] = tmp
        val moves = state.swapCount + 1
        return state.copy(
            slots = out,
            swapCount = moves,
            phase = if (isSolved(out)) TilesPhase.WON else TilesPhase.PLAY,
        )
    }

    fun countdownLabel(countdown: Int): String? = when {
        countdown > 6 -> "3"
        countdown > 4 -> "2"
        countdown > 2 -> "1"
        countdown > 0 -> "go"
        else -> null
    }

    fun step(state: TilesState, dtMs: Long): TilesState {
        if (dtMs <= 0L || state.phase == TilesPhase.WON) return state
        if (state.phase == TilesPhase.PLAY) {
            return state.copy(elapsedMs = state.elapsedMs + dtMs)
        }
        var accum = state.tickAccumMs + dtMs
        var countdown = state.countdown
        var slots = state.slots
        var rngState = state.rngState
        while (countdown > 0 && accum >= 1000L) {
            accum -= 1000L
            val rng = TilesRandom(rngState)
            slots = shuffle(slots, rng)
            rngState = rng.state
            countdown--
        }
        val phase = if (countdown == 0) TilesPhase.PLAY else TilesPhase.COUNTDOWN
        val elapsed = if (phase == TilesPhase.PLAY) accum else state.elapsedMs
        val leftover = if (phase == TilesPhase.PLAY) 0L else accum
        return state.copy(
            slots = slots,
            countdown = countdown,
            rngState = rngState,
            tickAccumMs = leftover,
            elapsedMs = elapsed,
            phase = phase,
        )
    }

    fun seconds(state: TilesState): Int = (state.elapsedMs / 1000L).toInt()

    fun applyWin(stats: TilesStats, mode: TilesMode, moves: Int, elapsedSeconds: Int): TilesStats = when (mode) {
        TilesMode.CLASSIC -> stats.copy(
            leastMoves = minOf(stats.leastMoves, moves),
            wins = stats.wins + 1,
        )
        TilesMode.TIME_TRIAL -> stats.copy(
            leastTime = minOf(stats.leastTime, elapsedSeconds),
            wins = stats.wins + 1,
        )
    }

    fun bestBeaten(stats: TilesStats, mode: TilesMode, moves: Int, elapsedSeconds: Int): Boolean = when (mode) {
        TilesMode.CLASSIC -> moves < stats.leastMoves
        TilesMode.TIME_TRIAL -> elapsedSeconds < stats.leastTime
    }

    fun applyLoss(stats: TilesStats, swapCount: Int): TilesStats =
        if (swapCount > 0) stats.copy(losses = stats.losses + 1) else stats

    fun encode(state: TilesState): String = listOf(
        "v1",
        state.mode.name,
        state.slots.joinToString(""),
        state.swapCount,
        state.elapsedMs,
        state.countdown,
        state.phase.name,
        state.seed,
        state.rngState,
        state.tickAccumMs,
    ).joinToString(";")

    fun decode(text: String?): TilesState? {
        if (text.isNullOrBlank()) return null
        return try {
            val f = text.split(";")
            if (f.size < 10 || f[0] != "v1") return null
            val slots = f[2].map { it - '0' }
            if (slots.size != SIZE || slots.toSet().size != SIZE) return null
            TilesState(
                mode = TilesMode.valueOf(f[1]),
                slots = slots,
                swapCount = f[3].toInt(),
                elapsedMs = f[4].toLong(),
                countdown = f[5].toInt(),
                phase = TilesPhase.valueOf(f[6]),
                seed = f[7].toInt(),
                rngState = f[8].toInt(),
                tickAccumMs = f[9].toLong(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeStats(stats: TilesStats): String = listOf(
        "v1",
        stats.leastMoves,
        stats.leastTime,
        stats.wins,
        stats.losses,
        if (stats.audioEnabled) 1 else 0,
    ).joinToString(";")

    fun decodeStats(text: String?): TilesStats {
        if (text.isNullOrBlank()) return TilesStats()
        return try {
            val f = text.split(";")
            if (f.size < 6 || f[0] != "v1") return TilesStats()
            TilesStats(
                leastMoves = f[1].toInt(),
                leastTime = f[2].toInt(),
                wins = f[3].toInt(),
                losses = f[4].toInt(),
                audioEnabled = f[5] == "1",
            )
        } catch (_: Exception) {
            TilesStats()
        }
    }
}
