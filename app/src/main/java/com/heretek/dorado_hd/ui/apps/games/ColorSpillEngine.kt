package com.heretek.dorado_hd.ui.apps.games

/**
 * Color Spill — classic flood-fill ("flood-it") puzzle, clean-room engine.
 *
 * Corpus note: docs/apps/color-spill.md §1 records that the ColorSpill payload
 * is AES-encrypted and the extracted `color_spill` folder actually contains
 * Reversi (shared package GUID), so no device constants are recoverable. The
 * constants below are standard-genre values chosen to match the only recovered
 * anchor — "fill the entire board with a single color ... in as few steps as
 * possible" — and are provisional until a clean package is mined.
 */
object ColorSpillEngine {

    const val MIN_SIZE = 5
    const val MAX_SIZE = 14
    const val MIN_COLORS = 3
    const val MAX_COLORS = 6

    /** Extra moves allowed over par before the level is lost. */
    const val MOVE_SLACK = 3

    const val WIN_BASE = 500
    const val UNDER_PAR_BONUS = 50

    class SpillRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun next(bound: Int): Int {
            if (bound <= 0) return 0
            return ((nextSeed() ushr 16) and 0x7FFF) % bound
        }
    }

    data class SpillState(
        val level: Int,
        val width: Int,
        val height: Int,
        val colors: Int,
        val cells: List<Int>,
        val moves: Int,
        val par: Int,
        val maxMoves: Int,
        val seed: Int,
        val elapsedMs: Long = 0L,
        val won: Boolean = false,
        val lost: Boolean = false,
    ) {
        val size: Int get() = width * height

        fun cell(x: Int, y: Int): Int =
            if (x in 0 until width && y in 0 until height) cells[y * width + x] else -1

        fun originColor(): Int = cell(0, 0)
    }

    fun sizeForLevel(level: Int): Int =
        (MIN_SIZE + (level - 1)).coerceAtMost(MAX_SIZE)

    fun colorsForLevel(level: Int): Int =
        (MIN_COLORS + (level - 1) / 2).coerceAtMost(MAX_COLORS)

    fun randomBoard(width: Int, height: Int, colors: Int, rng: SpillRandom): List<Int> {
        val palette = colors.coerceAtLeast(1)
        val out = ArrayList<Int>(width * height)
        for (i in 0 until width * height) out += rng.next(palette)
        if (palette > 1 && out.distinct().size < 2 && out.isNotEmpty()) {
            out[out.lastIndex] = (out[0] + 1) % palette
        }
        return out
    }

    private fun neighbors(index: Int, width: Int, height: Int): List<Int> {
        val x = index % width
        val y = index / width
        val out = ArrayList<Int>(4)
        if (x > 0) out += index - 1
        if (x < width - 1) out += index + 1
        if (y > 0) out += index - width
        if (y < height - 1) out += index + width
        return out
    }

    fun regionSize(cells: List<Int>, width: Int, height: Int): Int {
        if (cells.isEmpty()) return 0
        val origin = cells[0]
        val seen = BooleanArray(cells.size)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        seen[0] = true
        var count = 1
        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            for (n in neighbors(idx, width, height)) {
                if (!seen[n] && cells[n] == origin) {
                    seen[n] = true
                    count++
                    stack.addLast(n)
                }
            }
        }
        return count
    }

    fun flood(cells: List<Int>, width: Int, height: Int, newColor: Int): List<Int> {
        if (cells.isEmpty()) return cells
        val origin = cells[0]
        if (origin == newColor) return cells
        val out = cells.toMutableList()
        val stack = ArrayDeque<Int>()
        out[0] = newColor
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            for (n in neighbors(idx, width, height)) {
                if (out[n] == origin) {
                    out[n] = newColor
                    stack.addLast(n)
                }
            }
        }
        return out
    }

    /** Deterministic greedy lower bound used as the level par. */
    fun greedyPar(cells: List<Int>, width: Int, height: Int, colors: Int): Int {
        if (cells.isEmpty()) return 0
        var board = cells
        var moves = 0
        val limit = width * height + colors
        while (regionSize(board, width, height) < board.size && moves < limit) {
            val current = regionSize(board, width, height)
            var bestColor = -1
            var bestSize = current
            for (c in 0 until colors) {
                if (c == board[0]) continue
                val candidate = flood(board, width, height, c)
                val size = regionSize(candidate, width, height)
                if (size > bestSize) {
                    bestSize = size
                    bestColor = c
                }
            }
            if (bestColor < 0) break
            board = flood(board, width, height, bestColor)
            moves++
        }
        return moves
    }

    fun newGame(level: Int = 1, seed: Int): SpillState {
        val side = sizeForLevel(level)
        val colors = colorsForLevel(level)
        val rng = SpillRandom(seed)
        val cells = randomBoard(side, side, colors, rng)
        val par = greedyPar(cells, side, side, colors)
        return SpillState(
            level = level,
            width = side,
            height = side,
            colors = colors,
            cells = cells,
            moves = 0,
            par = par,
            maxMoves = par + MOVE_SLACK,
            seed = seed,
        )
    }

    fun floodFill(state: SpillState, newColor: Int): SpillState {
        if (state.won || state.lost) return state
        if (newColor < 0 || newColor >= state.colors) return state
        if (newColor == state.originColor()) return state
        val filled = flood(state.cells, state.width, state.height, newColor)
        val moves = state.moves + 1
        val complete = filled.distinct().size == 1
        val lost = !complete && moves >= state.maxMoves
        return state.copy(cells = filled, moves = moves, won = complete, lost = lost)
    }

    fun score(state: SpillState): Int {
        if (!state.won) return 0
        val bonus = (state.par - state.moves) * UNDER_PAR_BONUS
        return (WIN_BASE + bonus).coerceAtLeast(WIN_BASE / 2) * state.level
    }

    fun step(state: SpillState, dtMs: Long): SpillState {
        if (dtMs <= 0 || state.won || state.lost) return state
        return state.copy(elapsedMs = state.elapsedMs + dtMs)
    }

    fun encode(state: SpillState): String = listOf(
        "v1",
        state.level,
        state.width,
        state.height,
        state.colors,
        state.moves,
        state.par,
        state.maxMoves,
        state.seed,
        state.elapsedMs,
        if (state.won) 1 else 0,
        if (state.lost) 1 else 0,
        state.cells.joinToString(""),
    ).joinToString(";")

    fun decode(text: String?): SpillState? {
        if (text.isNullOrBlank()) return null
        return try {
            val f = text.split(";")
            if (f.size < 13 || f[0] != "v1") return null
            val width = f[2].toInt()
            val height = f[3].toInt()
            val cells = f[12].map { it - '0' }
            if (cells.size != width * height) return null
            SpillState(
                level = f[1].toInt(),
                width = width,
                height = height,
                colors = f[4].toInt(),
                cells = cells,
                moves = f[5].toInt(),
                par = f[6].toInt(),
                maxMoves = f[7].toInt(),
                seed = f[8].toInt(),
                elapsedMs = f[9].toLong(),
                won = f[10] == "1",
                lost = f[11] == "1",
            )
        } catch (_: Exception) {
            null
        }
    }
}
