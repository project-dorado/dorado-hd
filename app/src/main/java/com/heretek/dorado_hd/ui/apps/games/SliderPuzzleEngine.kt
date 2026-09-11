package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/**
 * Sliding picture puzzle (official "Slider Puzzle" re-creation).
 *
 * Pure Kotlin: no Android imports, deterministic when a seeded [Random] is
 * passed to [newGame]. Behaviour is re-derived from the behavioural spec
 * (`docs/apps/slider-puzzle.md`); no Microsoft code or art is used.
 */
enum class SlideSize(val label: String, val rows: Int, val cols: Int) {
    EIGHT("8", 4, 2),
    FIFTEEN("15", 5, 3),
    TWENTY_FOUR("24", 6, 4);

    val tiles: Int get() = rows * cols

    companion object {
        fun fromLabel(label: String): SlideSize? = entries.firstOrNull { it.label == label }
        fun fromTiles(tiles: Int): SlideSize? = entries.firstOrNull { it.tiles == tiles }
    }
}

/** A tile sliding from [fromCell] into the blank at [toCell]. */
data class SlideAnim(val fromCell: Int, val toCell: Int, val elapsedMs: Long = 0)

/**
 * Immutable board snapshot. [cells] maps a cell index (row-major from the top
 * left) to the tile id occupying it; tile id [SliderPuzzleEngine.BLANK] (0) is
 * the hole. A tile's home cell is `tiles - 1 - id`, matching the reference
 * build order (bottom-right first, descending number overlay).
 */
data class SlideState(
    val size: SlideSize,
    val cells: List<Int>,
    val moves: Int = 0,
    val elapsedMs: Long = 0,
    val anim: SlideAnim? = null,
) {
    val blankCell: Int get() = cells.indexOf(SliderPuzzleEngine.BLANK)
    val solved: Boolean get() = cells == SliderPuzzleEngine.targetCells(size)
}

object SliderPuzzleEngine {
    const val BLANK = 0

    /** Portrait board metrics: 272 wide, 480 tall with a 33 px top strip. */
    const val SCREEN_WIDTH = 272
    const val SCREEN_HEIGHT = 480
    const val BOARD_TOP = 33
    const val BOARD_HEIGHT = SCREEN_HEIGHT - BOARD_TOP

    /** A tap-adjacent slide interpolates for 0.15 s. */
    const val SLIDE_MS = 150L

    /** Legal blank-shuffle moves: rand.Next(200, 301). */
    const val SHUFFLE_MIN = 200
    const val SHUFFLE_MAX = 300

    /** Results keep only the fastest ten times per size. */
    const val MAX_SCORES = 10

    /** Target layout: cell c holds tile `tiles - 1 - c`. */
    fun targetCells(size: SlideSize): List<Int> = List(size.tiles) { size.tiles - 1 - it }

    fun homeCell(size: SlideSize, tile: Int): Int = size.tiles - 1 - tile

    fun cellRow(size: SlideSize, cell: Int): Int = cell / size.cols

    fun cellCol(size: SlideSize, cell: Int): Int = cell % size.cols

    fun neighbors(size: SlideSize, cell: Int): List<Int> {
        val row = cellRow(size, cell)
        val col = cellCol(size, cell)
        val out = ArrayList<Int>(4)
        if (row > 0) out += cell - size.cols
        if (row < size.rows - 1) out += cell + size.cols
        if (col > 0) out += cell - 1
        if (col < size.cols - 1) out += cell + 1
        return out
    }

    /** True when [cell] holds a tile orthogonally adjacent to the blank. */
    fun canMove(state: SlideState, cell: Int): Boolean {
        if (state.anim != null) return false
        if (cell !in state.cells.indices) return false
        return cell in neighbors(state.size, state.blankCell)
    }

    /** Applies a legal move; illegal taps return [state] unchanged. */
    fun applyMove(state: SlideState, cell: Int): SlideState {
        if (!canMove(state, cell)) return state
        val blank = state.blankCell
        val next = state.cells.toMutableList()
        next[blank] = next[cell]
        next[cell] = BLANK
        return state.copy(
            cells = next,
            moves = state.moves + 1,
            anim = SlideAnim(fromCell = cell, toCell = blank),
        )
    }

    /** Advances the 0.15 s slide interpolation; clears it when finished. */
    fun step(state: SlideState, dtMs: Long): SlideState {
        val anim = state.anim ?: return state
        val elapsed = anim.elapsedMs + dtMs
        return if (elapsed >= SLIDE_MS) {
            state.copy(anim = null)
        } else {
            state.copy(anim = anim.copy(elapsedMs = elapsed))
        }
    }

    /**
     * Fresh solvable board: exactly 200..300 uniformly-chosen legal blank
     * slides away from the solved layout, so every start is reachable.
     */
    fun newGame(size: SlideSize, rng: Random): SlideState {
        var cells = targetCells(size)
        val shuffles = SHUFFLE_MIN + rng.nextInt(SHUFFLE_MAX - SHUFFLE_MIN + 1)
        repeat(shuffles) {
            val blank = cells.indexOf(BLANK)
            val options = neighbors(size, blank)
            val pick = options[rng.nextInt(options.size)]
            val next = cells.toMutableList()
            next[blank] = next[pick]
            next[pick] = BLANK
            cells = next
        }
        if (cells == targetCells(size)) {
            val blank = cells.indexOf(BLANK)
            val options = neighbors(size, blank)
            val pick = options[rng.nextInt(options.size)]
            val next = cells.toMutableList()
            next[blank] = next[pick]
            next[pick] = BLANK
            cells = next
        }
        return SlideState(size = size, cells = cells)
    }

    /**
     * Classic N-puzzle parity check: reachable states share the same
     * (inversions + blank row from bottom) parity as the target layout for
     * even-width boards, and the same inversion parity for odd-width boards.
     */
    fun isSolvable(cells: List<Int>, size: SlideSize): Boolean {
        if (cells.size != size.tiles) return false
        if (cells.toSet() != (0 until size.tiles).toSet()) return false
        return parity(cells, size) == parity(targetCells(size), size)
    }

    private fun parity(cells: List<Int>, size: SlideSize): Int {
        val values = cells.filter { it != BLANK }
        var inversions = 0
        for (i in values.indices) {
            for (j in i + 1 until values.size) {
                if (values[i] > values[j]) inversions++
            }
        }
        if (size.cols % 2 == 1) return inversions % 2
        val blank = cells.indexOf(BLANK)
        val rowFromBottom = size.rows - cellRow(size, blank)
        return (inversions + rowFromBottom) % 2
    }

    /* ---------------------------------------------------------------- */
    /*                       Persistence helpers                        */
    /* ---------------------------------------------------------------- */

    fun encode(state: SlideState): String {
        val cells = state.cells.joinToString(",")
        return "slide1|${state.size.label}|${state.moves}|${state.elapsedMs}|$cells"
    }

    fun decode(blob: String?): SlideState? {
        if (blob.isNullOrBlank()) return null
        val parts = blob.split("|")
        if (parts.size != 5 || parts[0] != "slide1") return null
        val size = SlideSize.fromLabel(parts[1]) ?: return null
        val moves = parts[2].toIntOrNull() ?: return null
        val elapsed = parts[3].toLongOrNull() ?: return null
        val cells = parts[4].split(",").map { it.toIntOrNull() ?: return null }
        if (cells.size != size.tiles) return null
        if (cells.toSet() != (0 until size.tiles).toSet()) return null
        return SlideState(size = size, cells = cells, moves = moves, elapsedMs = elapsed)
    }

    /** One completed run: elapsed time (primary) then move count. */
    data class SlideScore(val elapsedMs: Long, val moves: Int)

    fun encodeScores(scores: List<SlideScore>): String =
        scores.joinToString(";") { "${it.elapsedMs}:${it.moves}" }

    fun decodeScores(blob: String?): List<SlideScore> {
        if (blob.isNullOrBlank()) return emptyList()
        return blob.split(";").mapNotNull { part ->
            val bits = part.split(":")
            if (bits.size != 2) return@mapNotNull null
            val ms = bits[0].toLongOrNull() ?: return@mapNotNull null
            val moves = bits[1].toIntOrNull() ?: return@mapNotNull null
            SlideScore(ms, moves)
        }
    }

    /** Inserts [score], sorts fastest-first, keeps the top [max]. */
    fun insertScore(scores: List<SlideScore>, score: SlideScore, max: Int = MAX_SCORES): List<SlideScore> =
        (scores + score)
            .sortedWith(compareBy({ it.elapsedMs }, { it.moves }))
            .take(max)
}
