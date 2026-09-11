package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/**
 * 3D Picture Puzzle model (W5, official `PicturePuzzle3D.exe`).
 *
 * Two-sided mode keeps a **single empty slot across both boards**: a slide is
 * legal only on the board that currently owns the empty, and a flip transfers
 * a piece from the other board into that empty, leaving a new empty on the
 * piece's origin. This conserves exactly one empty, makes every move
 * reversible, and matches the device's "every third shuffle move flips a
 * piece" mixing behavior.
 */
enum class PuzzleSide { FRONT, BACK }

data class PuzzleConfig(val gridSize: Int, val twoSided: Boolean) {
    val cells: Int get() = gridSize * gridSize
    val key: String get() = (if (twoSided) "TwoSided" else "OneSided") + "${gridSize}x$gridSize"
    val bestTimeKey: String get() = "best.$key"
}

data class PuzzlePiece(
    val id: Int,
    val side: PuzzleSide,
    val row: Int,
    val col: Int,
    val image: Int,
    val homeRow: Int,
    val homeCol: Int,
    val homeSide: PuzzleSide,
)

data class PuzzleState(
    val config: PuzzleConfig,
    val pieces: List<PuzzlePiece>,
    val emptySide: PuzzleSide,
    val emptyRow: Int,
    val emptyCol: Int,
    val elapsedMs: Long = 0,
    val moves: Int = 0,
    val active: Boolean = false,
    val complete: Boolean = false,
) {
    fun pieceAt(side: PuzzleSide, row: Int, col: Int): PuzzlePiece? =
        pieces.firstOrNull { it.side == side && it.row == row && it.col == col }

    fun emptyAt(side: PuzzleSide, row: Int, col: Int): Boolean =
        side == emptySide && row == emptyRow && col == emptyCol
}

object PicturePuzzleEngine {

    /** Six categories, each with two generated pictures (front/back). */
    const val CATEGORY_COUNT = 6
    const val NUMBERS_CATEGORY = 6

    fun newGame(config: PuzzleConfig, seed: Int, category: Int = 0): PuzzleState {
        require(config.gridSize in 1..6) { "gridSize 1..6" }
        val rng = Random(seed)
        val frontImage = if (category == NUMBERS_CATEGORY) 0 else category * 2
        val backImage = frontImage + 1
        // Exactly one global empty slot: the front board starts one short; in
        // two-sided mode the back board starts full (the empty is on the
        // front). A flip transfers a back piece into the empty and leaves the
        // empty at that piece's origin on the back.
        val pieces = ArrayList<PuzzlePiece>()
        var id = 0
        for (row in 0 until config.gridSize) {
            for (col in 0 until config.gridSize) {
                if (row == 0 && col == config.gridSize - 1) continue
                pieces += PuzzlePiece(id++, PuzzleSide.FRONT, row, col, frontImage, row, col, PuzzleSide.FRONT)
            }
        }
        if (config.twoSided) {
            for (row in 0 until config.gridSize) {
                for (col in 0 until config.gridSize) {
                    pieces += PuzzlePiece(id++, PuzzleSide.BACK, row, col, backImage, row, col, PuzzleSide.BACK)
                }
            }
        }
        val start = PuzzleState(
            config = config,
            pieces = pieces,
            emptySide = PuzzleSide.FRONT,
            emptyRow = 0,
            emptyCol = config.gridSize - 1,
        )
        return shuffle(start, rng)
    }

    /** Device mix-up: 4 * (grid + 1) moves; every third is a flip when two-sided. */
    fun shuffle(state: PuzzleState, rng: Random): PuzzleState {
        var current = state
        val moveCount = 4 * (state.config.gridSize + 1)
        var move = 0
        var flips = 0
        var guard = 0
        while (move < moveCount && guard < moveCount * 20) {
            guard++
            val canFlip = current.config.twoSided && (move + 1) % 3 == 0
            val options = legalSlides(current)
            if (canFlip) {
                val candidates = current.pieces.filter { it.side != current.emptySide }
                if (candidates.isNotEmpty()) {
                    val pick = candidates[rng.nextInt(candidates.size)]
                    current = flip(current, pick.side, pick.row, pick.col) ?: current
                    flips++
                    move++
                    continue
                }
            }
            if (options.isEmpty()) break
            val (side, row, col) = options[rng.nextInt(options.size)]
            current = slide(current, side, row, col) ?: current
            move++
        }
        return current.copy(moves = 0, elapsedMs = 0, active = false, complete = false)
    }

    fun legalSlides(state: PuzzleState): List<Triple<PuzzleSide, Int, Int>> {
        val out = ArrayList<Triple<PuzzleSide, Int, Int>>()
        val side = state.emptySide
        val row = state.emptyRow
        val col = state.emptyCol
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val r = row + dr
            val c = col + dc
            if (r !in 0 until state.config.gridSize) continue
            if (c !in 0 until state.config.gridSize) continue
            if (state.pieceAt(side, r, c) != null) out += Triple(side, r, c)
        }
        return out
    }

    fun canSlide(state: PuzzleState, side: PuzzleSide, row: Int, col: Int): Boolean {
        if (side != state.emptySide) return false
        val dr = kotlin.math.abs(row - state.emptyRow)
        val dc = kotlin.math.abs(col - state.emptyCol)
        return (dr + dc) == 1 && state.pieceAt(side, row, col) != null
    }

    /** Slide a piece into the adjacent empty slot on the same board. */
    fun slide(state: PuzzleState, side: PuzzleSide, row: Int, col: Int): PuzzleState? {
        if (!canSlide(state, side, row, col)) return null
        val piece = state.pieceAt(side, row, col) ?: return null
        val pieces = state.pieces.map {
            if (it.id == piece.id) it.copy(row = state.emptyRow, col = state.emptyCol) else it
        }
        return state.copy(
            pieces = pieces,
            emptyRow = row,
            emptyCol = col,
            moves = state.moves + 1,
        ).let { if (it.complete) it else it.copy(complete = isComplete(it)) }
    }

    /** Two-sided transfer: the piece moves into the other board's empty slot. */
    fun canFlip(state: PuzzleState, side: PuzzleSide, row: Int, col: Int): Boolean =
        state.config.twoSided && side != state.emptySide && state.pieceAt(side, row, col) != null

    fun flip(state: PuzzleState, side: PuzzleSide, row: Int, col: Int): PuzzleState? {
        if (!canFlip(state, side, row, col)) return null
        val piece = state.pieceAt(side, row, col) ?: return null
        val pieces = state.pieces.map {
            if (it.id == piece.id) {
                it.copy(
                    side = state.emptySide,
                    row = state.emptyRow,
                    col = state.emptyCol,
                )
            } else {
                it
            }
        }
        return state.copy(
            pieces = pieces,
            emptySide = side,
            emptyRow = row,
            emptyCol = col,
            moves = state.moves + 1,
        ).let { if (it.complete) it else it.copy(complete = isComplete(it)) }
    }

    /** Every tile on its home cell and board; the empty sits at its own home. */
    fun isComplete(state: PuzzleState): Boolean {
        for (piece in state.pieces) {
            if (piece.side != piece.homeSide || piece.row != piece.homeRow || piece.col != piece.homeCol) {
                return false
            }
        }
        return true
    }

    fun step(state: PuzzleState, dtMs: Long): PuzzleState {
        if (!state.active || state.complete) return state
        return state.copy(elapsedMs = state.elapsedMs + dtMs.coerceIn(0, 1000))
    }

    /** Tile number for the hint overlay (row-major, 1-based). */
    fun homeNumber(state: PuzzleState, piece: PuzzlePiece): Int =
        piece.homeRow * state.config.gridSize + piece.homeCol + 1

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return "%d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}
