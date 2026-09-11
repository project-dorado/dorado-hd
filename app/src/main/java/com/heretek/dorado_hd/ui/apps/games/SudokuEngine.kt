package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

/* ============================================================ */
/*                            Sudoku                              */
/* ============================================================ */

/** Classic 9x9 and Mini 6x6 boards (official PuzzleType). */
enum class SudokuType { CLASSIC, MINI }

/** Easy / Normal / Hard selection buckets (official PuzzleDifficulty). */
enum class SudokuLevel { EASY, NORMAL, HARD }

/** Per-bucket timing record for the High Scores surface. */
data class SudokuRecord(val bestSeconds: Int = 0, val solved: Int = 0)

/**
 * Immutable Sudoku session: the carved [board] plus the player's [cells] and
 * pencil-mark [notes]. [history] holds flattened snapshots for one-step undo.
 */
data class SudokuGame(
    val type: SudokuType,
    val level: SudokuLevel,
    val board: SudokuEngine.Board,
    val cells: List<Int>,
    val notes: List<Set<Int>>,
    val history: List<SudokuGame> = emptyList(),
    val lastHintCell: Int = -1,
    val solved: Boolean = false,
)

object SudokuEngine {
    data class Board(
        val rows: List<List<Int>>,
        val given: List<List<Boolean>>,
        val solution: List<List<Int>>,
    )

    private class Geometry(val n: Int, val boxR: Int, val boxC: Int)

    private fun geometry(type: SudokuType): Geometry = when (type) {
        SudokuType.CLASSIC -> Geometry(9, 3, 3)
        SudokuType.MINI -> Geometry(6, 2, 3)
    }

    /** Number of clues each type/level carves toward (lower = harder). */
    fun clueTarget(type: SudokuType, level: SudokuLevel): Int = when (type) {
        SudokuType.CLASSIC -> when (level) {
            SudokuLevel.EASY -> 45
            SudokuLevel.NORMAL -> 33
            SudokuLevel.HARD -> 26
        }
        SudokuType.MINI -> when (level) {
            SudokuLevel.EASY -> 22
            SudokuLevel.NORMAL -> 17
            SudokuLevel.HARD -> 13
        }
    }

    /**
     * Uniqueness-checked generator: carve a random solution while a bounded
     * solution counter still finds exactly one completion. Deterministic for a
     * fixed seed.
     */
    fun newGame(
        type: SudokuType = SudokuType.CLASSIC,
        level: SudokuLevel = SudokuLevel.NORMAL,
        seed: Long = 0L,
    ): Board {
        val g = geometry(type)
        val rng = Random(if (seed != 0L) seed else System.nanoTime())
        val solution = generateSolution(g, rng)
        val puzzle = solution.copyOf()
        val target = clueTarget(type, level)
        var clues = puzzle.size
        val order = (0 until puzzle.size).toMutableList().also { it.shuffle(rng) }
        val solver = Solver(g)
        for (pos in order) {
            if (clues <= target) break
            val keep = puzzle[pos]
            puzzle[pos] = 0
            if (solver.countSolutions(puzzle.copyOf(), limit = 2) != 1) {
                puzzle[pos] = keep
            } else {
                clues--
            }
        }
        val rows = puzzle.toList().chunked(g.n)
        return Board(
            rows = rows,
            given = rows.map { row -> row.map { it != 0 } },
            solution = solution.toList().chunked(g.n),
        )
    }

    /** True when every cell matches the generated solution. */
    fun isSolved(cells: List<Int>, solution: List<List<Int>>): Boolean {
        val n = solution.size
        if (n == 0 || cells.size != n * n) return false
        return cells.indices.all { cells[it] == solution[it / n][it % n] }
    }

    /* ---------------- solver ---------------- */

    /** Solves any valid grid; null when the givens already contradict. */
    fun solve(rows: List<List<Int>>, type: SudokuType = SudokuType.CLASSIC): List<List<Int>>? {
        val g = geometry(type)
        val grid = flatten(rows, g.n) ?: return null
        val solver = Solver(g)
        return if (solver.fillFirst(grid)) grid.toList().chunked(g.n) else null
    }

    /** Official HasUniqueSolution: a bounded solver must find exactly one. */
    fun hasUniqueSolution(rows: List<List<Int>>, type: SudokuType = SudokuType.CLASSIC): Boolean {
        val g = geometry(type)
        val grid = flatten(rows, g.n) ?: return false
        return Solver(g).countSolutions(grid, limit = 2) == 1
    }

    /** Values still legal for cell [index] (empty for filled/given cells). */
    fun candidates(rows: List<List<Int>>, index: Int, type: SudokuType = SudokuType.CLASSIC): Set<Int> {
        val g = geometry(type)
        val grid = flatten(rows, g.n) ?: return emptySet()
        if (index !in grid.indices || grid[index] != 0) return emptySet()
        val solver = Solver(g)
        solver.load(grid)
        return bits(solver.candidatesAt(grid, index)).toSet()
    }

    /** Cells that duplicate a value in any row, column or box. */
    fun conflicts(rows: List<List<Int>>, type: SudokuType = SudokuType.CLASSIC): Set<Int> {
        val g = geometry(type)
        if (rows.size != g.n || rows.any { it.size != g.n }) return emptySet()
        val out = mutableSetOf<Int>()
        for (house in houses(g)) {
            val seen = mutableMapOf<Int, MutableList<Int>>()
            for (i in house) {
                val v = rows[i / g.n][i % g.n]
                if (v != 0) seen.getOrPut(v) { mutableListOf() }.add(i)
            }
            seen.values.filter { it.size > 1 }.forEach { out.addAll(it) }
        }
        return out
    }

    private fun houses(g: Geometry): List<List<Int>> {
        val n = g.n
        val out = mutableListOf<List<Int>>()
        for (r in 0 until n) out += (0 until n).map { r * n + it }
        for (c in 0 until n) out += (0 until n).map { it * n + c }
        for (br in 0 until n / g.boxR) {
            for (bc in 0 until n / g.boxC) {
                val cells = mutableListOf<Int>()
                for (i in 0 until g.boxR) for (j in 0 until g.boxC) {
                    cells += (br * g.boxR + i) * n + (bc * g.boxC + j)
                }
                out += cells
            }
        }
        return out
    }

    private fun flatten(rows: List<List<Int>>, n: Int): IntArray? {
        if (rows.size != n || rows.any { it.size != n }) return null
        val grid = IntArray(n * n)
        for (r in 0 until n) for (c in 0 until n) {
            val v = rows[r][c]
            if (v !in 0..n) return null
            grid[r * n + c] = v
        }
        return grid
    }

    private fun bits(mask: Int): List<Int> {
        val out = mutableListOf<Int>()
        var m = mask
        while (m != 0) {
            val bit = m and -m
            m = m xor bit
            out += Integer.numberOfTrailingZeros(bit) + 1
        }
        return out
    }

    private fun generateSolution(g: Geometry, rng: Random): IntArray {
        val solver = Solver(g)
        val grid = IntArray(g.n * g.n)
        fun fill(): Boolean {
            var best = -1
            var bestMask = 0
            var bestBits = g.n + 1
            for (i in grid.indices) {
                if (grid[i] != 0) continue
                val mask = solver.candidatesAt(grid, i)
                val nBits = Integer.bitCount(mask)
                if (nBits == 0) return false
                if (nBits < bestBits) {
                    best = i
                    bestMask = mask
                    bestBits = nBits
                    if (nBits == 1) break
                }
            }
            if (best == -1) return true
            val values = bits(bestMask).shuffled(rng)
            for (v in values) {
                grid[best] = v
                solver.place(best, v)
                if (fill()) return true
                solver.unplace(best, v)
                grid[best] = 0
            }
            return false
        }
        fill()
        return grid
    }

    /** MRV backtracking with incrementally maintained row/col/box bitmasks. */
    private class Solver(private val g: Geometry) {
        private val n = g.n
        private val full = (1 shl n) - 1
        private val rowMask = IntArray(n)
        private val colMask = IntArray(n)
        private val boxMask = IntArray(n)
        private var count = 0
        private var limit = 2

        private fun boxOf(i: Int): Int = (i / n / g.boxR) * (n / g.boxC) + (i % n) / g.boxC

        fun load(grid: IntArray): Boolean {
            rowMask.fill(0)
            colMask.fill(0)
            boxMask.fill(0)
            for (i in grid.indices) {
                val v = grid[i]
                if (v == 0) continue
                if (v !in 1..n) return false
                val bit = 1 shl (v - 1)
                if (rowMask[i / n] and bit != 0 || colMask[i % n] and bit != 0 || boxMask[boxOf(i)] and bit != 0) return false
                rowMask[i / n] = rowMask[i / n] or bit
                colMask[i % n] = colMask[i % n] or bit
                boxMask[boxOf(i)] = boxMask[boxOf(i)] or bit
            }
            return true
        }

        fun candidatesAt(grid: IntArray, i: Int): Int = full and (rowMask[i / n] or colMask[i % n] or boxMask[boxOf(i)]).inv()

        fun place(i: Int, value: Int) {
            val bit = 1 shl (value - 1)
            rowMask[i / n] = rowMask[i / n] or bit
            colMask[i % n] = colMask[i % n] or bit
            boxMask[boxOf(i)] = boxMask[boxOf(i)] or bit
        }

        fun unplace(i: Int, value: Int) {
            val bit = (1 shl (value - 1)).inv()
            rowMask[i / n] = rowMask[i / n] and bit
            colMask[i % n] = colMask[i % n] and bit
            boxMask[boxOf(i)] = boxMask[boxOf(i)] and bit
        }

        fun countSolutions(grid: IntArray, limit: Int): Int {
            this.limit = limit
            count = 0
            if (!load(grid)) return 0
            searchCount(grid)
            return count
        }

        private fun searchCount(grid: IntArray) {
            var best = -1
            var bestMask = 0
            var bestBits = n + 1
            for (i in grid.indices) {
                if (grid[i] != 0) continue
                val mask = candidatesAt(grid, i)
                val nBits = Integer.bitCount(mask)
                if (nBits == 0) return
                if (nBits < bestBits) {
                    best = i
                    bestMask = mask
                    bestBits = nBits
                    if (nBits == 1) break
                }
            }
            if (best == -1) {
                count++
                return
            }
            var m = bestMask
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit) + 1
                grid[best] = v
                place(best, v)
                searchCount(grid)
                unplace(best, v)
                grid[best] = 0
                if (count >= limit) return
            }
        }

        fun fillFirst(grid: IntArray): Boolean {
            if (!load(grid)) return false
            return searchFill(grid)
        }

        private fun searchFill(grid: IntArray): Boolean {
            var best = -1
            var bestMask = 0
            var bestBits = n + 1
            for (i in grid.indices) {
                if (grid[i] != 0) continue
                val mask = candidatesAt(grid, i)
                val nBits = Integer.bitCount(mask)
                if (nBits == 0) return false
                if (nBits < bestBits) {
                    best = i
                    bestMask = mask
                    bestBits = nBits
                    if (nBits == 1) break
                }
            }
            if (best == -1) return true
            var m = bestMask
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                val v = Integer.numberOfTrailingZeros(bit) + 1
                grid[best] = v
                place(best, v)
                if (searchFill(grid)) return true
                unplace(best, v)
                grid[best] = 0
            }
            return false
        }
    }

    /* ---------------- session actions ---------------- */

    fun start(board: Board, type: SudokuType, level: SudokuLevel): SudokuGame {
        val size = board.rows.size
        return SudokuGame(
            type = type,
            level = level,
            board = board,
            cells = board.rows.flatten(),
            notes = List(size * size) { emptySet() },
        )
    }

    private fun push(g: SudokuGame, next: SudokuGame): SudokuGame =
        next.copy(history = g.history + g.copy(history = emptyList()))

    fun undo(g: SudokuGame): SudokuGame = g.history.lastOrNull() ?: g

    fun isGiven(g: SudokuGame, index: Int): Boolean =
        index in g.cells.indices && g.board.given[index / g.board.rows.size][index % g.board.rows.size]

    fun place(g: SudokuGame, index: Int, value: Int): SudokuGame {
        if (g.solved || index !in g.cells.indices || isGiven(g, index)) return g
        if (value !in 1..g.board.rows.size) return g
        val cells = g.cells.toMutableList().also { it[index] = value }
        val notes = g.notes.toMutableList().also { it[index] = emptySet() }
        val complete = cells.none { it == 0 } && conflicts(cells.chunked(g.board.rows.size), g.type).isEmpty()
        return push(g, g.copy(cells = cells, notes = notes, lastHintCell = -1, solved = complete))
    }

    fun toggleNote(g: SudokuGame, index: Int, value: Int): SudokuGame {
        if (g.solved || index !in g.cells.indices || isGiven(g, index)) return g
        if (g.cells[index] != 0) return g
        if (value !in 1..g.board.rows.size) return g
        val current = g.notes[index]
        val next = if (value in current) current - value else current + value
        val notes = g.notes.toMutableList().also { it[index] = next }
        return push(g, g.copy(notes = notes, lastHintCell = -1))
    }

    fun erase(g: SudokuGame, index: Int): SudokuGame {
        if (g.solved || index !in g.cells.indices || isGiven(g, index)) return g
        if (g.cells[index] == 0 && g.notes[index].isEmpty()) return g
        val cells = g.cells.toMutableList().also { it[index] = 0 }
        val notes = g.notes.toMutableList().also { it[index] = emptySet() }
        return push(g, g.copy(cells = cells, notes = notes, lastHintCell = -1, solved = false))
    }

    /** Official CanInsertHint: empty, not given, and not the previous hint cell. */
    fun canHint(g: SudokuGame, index: Int): Boolean {
        if (g.solved || index !in g.cells.indices || isGiven(g, index)) return false
        if (g.cells[index] != 0 || index == g.lastHintCell) return false
        return candidates(g.cells.chunked(g.board.rows.size), index, g.type).isNotEmpty()
    }

    /** Hint copies the solver's candidate set into the cell's pencil marks. */
    fun hint(g: SudokuGame, index: Int): SudokuGame {
        if (!canHint(g, index)) return g
        val values = candidates(g.cells.chunked(g.board.rows.size), index, g.type)
        if (values.isEmpty()) return g
        val notes = g.notes.toMutableList().also { it[index] = values }
        return push(g, g.copy(notes = notes, lastHintCell = index))
    }

    /** Solve fills the whole grid as one undoable step. */
    fun solve(g: SudokuGame): SudokuGame {
        if (g.solved) return g
        val cells = g.board.solution.flatten()
        return push(g, g.copy(cells = cells, notes = List(cells.size) { emptySet() }, lastHintCell = -1, solved = true))
    }

    /** Current duplicate map, sized for the board. */
    fun conflicts(g: SudokuGame): Set<Int> = conflicts(g.cells.chunked(g.board.rows.size), g.type)

    fun progressSolved(g: SudokuGame): Boolean = g.solved

    /* ---------------- persistence ---------------- */

    fun encode(g: SudokuGame): String {
        fun flat(rows: List<List<Int>>) = rows.joinToString("") { row -> row.joinToString("") { it.toString() } }
        val notes = g.notes.joinToString(",") { set ->
            set.fold(0) { acc, v -> acc or (1 shl (v - 1)) }.toString()
        }
        val given = g.board.given.joinToString("") { row -> row.joinToString("") { if (it) "1" else "0" } }
        return listOf(
            "v1",
            g.type.name,
            g.level.name,
            g.cells.joinToString(""),
            notes,
            given,
            flat(g.board.rows),
            flat(g.board.solution),
            g.lastHintCell.toString(),
            if (g.solved) "1" else "0",
        ).joinToString(";")
    }

    fun decode(text: String?): SudokuGame? {
        if (text.isNullOrBlank()) return null
        return try {
            val f = text.split(";")
            if (f.size < 10 || f[0] != "v1") return null
            val type = SudokuType.valueOf(f[1])
            val level = SudokuLevel.valueOf(f[2])
            val n = geometry(type).n
            val size = n * n
            fun digits(s: String): List<Int> {
                if (s.length != size) error("bad digits")
                return s.map { it - '0' }
            }
            fun masks(s: String): List<Boolean> {
                if (s.length != size) error("bad mask")
                return s.map { it == '1' }
            }
            val cells = digits(f[3])
            val notes = f[4].split(",").map { raw ->
                val m = raw.toInt()
                (1..n).filter { m and (1 shl (it - 1)) != 0 }.toSet()
            }
            if (notes.size != size) return null
            val given = masks(f[5])
            val board = Board(
                rows = digits(f[6]).chunked(n),
                given = given.chunked(n),
                solution = digits(f[7]).chunked(n),
            )
            SudokuGame(
                type = type,
                level = level,
                board = board,
                cells = cells,
                notes = notes,
                lastHintCell = f[8].toInt(),
                solved = f[9] == "1",
            )
        } catch (_: Exception) {
            null
        }
    }

    fun recordKey(type: SudokuType, level: SudokuLevel): String =
        "${type.name.lowercase()}:${level.name.lowercase()}"

    fun encodeRecords(records: Map<String, SudokuRecord>): String =
        records.entries.joinToString(";") { (key, r) -> "$key,${r.bestSeconds},${r.solved}" }

    fun decodeRecords(text: String?): Map<String, SudokuRecord> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            text.split(";").associate { row ->
                val parts = row.split(",")
                parts[0] to SudokuRecord(parts[1].toInt(), parts[2].toInt())
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
