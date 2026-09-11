package com.heretek.dorado_hd.ui.apps.games

import kotlin.random.Random

enum class HexColor { A, B, C, D, E, F, G, PEARL1, PEARL2, FLOWER, EMPTY }

val HEX_COLORS: List<HexColor> = listOf(
    HexColor.A, HexColor.B, HexColor.C, HexColor.D, HexColor.E, HexColor.F, HexColor.G,
)

fun HexColor.isPlayableColor(): Boolean = ordinal <= HexColor.G.ordinal

fun HexColor.isPearl(): Boolean = this == HexColor.PEARL1 || this == HexColor.PEARL2

fun HexColor.isSpecial(): Boolean = !isPlayableColor() && this != HexColor.EMPTY

data class HexPos(val col: Int, val row: Int)

data class HexPiece(
    val hue: HexColor,
    val star: Boolean = false,
    val bombTick: Int = -1,
    val locked: Boolean = false,
) {
    val isBomb: Boolean get() = bombTick >= 0
}

data class HexBoard(val columns: List<List<HexPiece?>>) {
    val width: Int get() = columns.size

    fun height(col: Int): Int = columns.getOrNull(col)?.size ?: 0

    operator fun get(pos: HexPos): HexPiece? =
        columns.getOrNull(pos.col)?.getOrNull(pos.row)

    fun contains(pos: HexPos): Boolean =
        pos.col in 0 until width && pos.row in 0 until height(pos.col)

    fun isFilled(pos: HexPos): Boolean = get(pos) != null

    fun isCleared(): Boolean = columns.all { col -> col.all { it == null } }

    fun updated(changes: Map<HexPos, HexPiece?>): HexBoard {
        if (changes.isEmpty()) return this
        val copy = columns.map { it.toMutableList() }
        for ((pos, piece) in changes) {
            copy.getOrNull(pos.col)?.let { if (pos.row in it.indices) it[pos.row] = piece }
        }
        return HexBoard(copy.map { it.toList() })
    }

    fun countPieces(): Int = columns.sumOf { col -> col.count { it != null } }
}

enum class HexCursorKind { STANDARD, FLOWER }

data class HexCursor(
    val anchor: HexPos,
    val kind: HexCursorKind = HexCursorKind.STANDARD,
    val half: Boolean = false,
)

enum class HexicMode { MARATHON, TIMED, SURVIVAL }

enum class HexicDifficulty(val startLevel: Int) { NORMAL(1), HARD(3), EXPERT(5) }

enum class HexicReason {
    PEARL_FLOWER, PEARL_CLUSTER, CLEARED, HIGHEST_LEVEL, BOMB, TIME, LOCKED,
}

enum class HexicMatchKind {
    CLUSTER, BONUS, THREE_BONUS, FLOWER, BOMB, BOMB_BONUS, PEARL_CLUSTER, FLOWER_STAR_CLUSTER,
}

data class HexicMatch(
    val kind: HexicMatchKind,
    val cells: List<HexPos>,
    val center: HexPos? = null,
    val stars: Int = 0,
    val hue: HexColor? = null,
)

data class HexLevel(
    val level: Int,
    val multiplier: Int,
    val combos: Int,
    val colors: Int,
    val allowBonus: Boolean,
    val bonusFreq: Int,
    val allowBombs: Boolean,
    val bombFreq: Int,
    val bombCount: Int,
    val bombGrace: Int,
    val startSeconds: Double,
    val validMoveSeconds: Double,
    val invalidMoveSeconds: Double,
)

data class HexicSpawn(
    val colors: Int,
    val allowBonus: Boolean,
    val bonusFreq: Int,
    val allowBombs: Boolean,
    val bombCountdown: Int,
    val bombGrace: Int,
    val bombCount: Int,
    val bombFreq: Int,
)

data class HexicResolve(
    val board: HexBoard,
    val gained: Int,
    val cleared: Int,
    val matches: List<HexicMatch>,
    val flowers: Int,
    val pearls: Int,
    val won: Boolean,
    val lost: Boolean,
    val reason: HexicReason?,
    val seed: Int,
    val bombCountdown: Int,
    val bombGrace: Int,
)

data class HexicStats(
    val flowers: Int = 0,
    val pearls: Int = 0,
    val bonusClusters: Int = 0,
    val bombsDiffused: Int = 0,
)

data class HexicGame(
    val mode: HexicMode,
    val difficulty: HexicDifficulty,
    val level: Int,
    val board: HexBoard,
    val score: Int = 0,
    val combosLeft: Int = 0,
    val timeLeft: Double = 0.0,
    val timerStarted: Boolean = false,
    val survivalLevel: Int = 1,
    val lockProgress: Int = 0,
    val lockBonus: Int = 0,
    val lockCountdown: Double = 2.0,
    val seed: Int = 1,
    val bombCountdown: Int = 0,
    val bombGrace: Int = 0,
    val moves: Int = 0,
    val stuck: Boolean = false,
    val won: Boolean = false,
    val lost: Boolean = false,
    val reason: HexicReason? = null,
    val message: String = "",
    val stats: HexicStats = HexicStats(),
)

class HexicRandom(seed: Int) {
    var state: Int = seed

    fun nextSeed(): Int {
        state = state * 1103515245 + 12345
        return state
    }

    fun next(bound: Int): Int {
        if (bound <= 0) return 0
        val raw = (nextSeed() ushr 16) and 0x7FFF
        return raw % bound
    }
}

object HexicEngine {
    const val COLUMNS = 10
    const val MAX_SCORE = 999_999_999

    fun cellCount(col: Int): Int = if (col % 2 == 0) 8 else 9

    fun allPositions(): List<HexPos> {
        val out = ArrayList<HexPos>(85)
        for (col in 0 until COLUMNS) {
            for (row in 0 until cellCount(col)) out += HexPos(col, row)
        }
        return out
    }

    fun emptyBoard(): HexBoard =
        HexBoard(List(COLUMNS) { col -> List(cellCount(col)) { null } })

    fun neighbors(pos: HexPos): List<HexPos> {
        val odd = pos.col % 2 == 1
        val diagonalRow = pos.row + if (odd) -1 else 1
        return listOf(
            HexPos(pos.col, pos.row - 1),
            HexPos(pos.col, pos.row + 1),
            HexPos(pos.col - 1, pos.row),
            HexPos(pos.col + 1, pos.row),
            HexPos(pos.col - 1, diagonalRow),
            HexPos(pos.col + 1, diagonalRow),
        )
    }

    fun flowerRing(center: HexPos): List<HexPos> {
        val odd = center.col % 2 == 1
        val offsets = if (odd) {
            listOf(-1 to -1, 0 to -1, 1 to -1, 1 to 0, 0 to 1, -1 to 0)
        } else {
            listOf(-1 to 0, 0 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1)
        }
        return offsets.map { HexPos(center.col + it.first, center.row + it.second) }
    }

    fun standardCells(anchor: HexPos, half: Boolean): List<HexPos> {
        val odd = anchor.col % 2 == 1
        return when {
            odd && !half -> listOf(
                HexPos(anchor.col, anchor.row),
                HexPos(anchor.col + 1, anchor.row),
                HexPos(anchor.col, anchor.row + 1),
            )
            odd && half -> listOf(
                HexPos(anchor.col, anchor.row + 1),
                HexPos(anchor.col + 1, anchor.row),
                HexPos(anchor.col + 1, anchor.row + 1),
            )
            !odd && !half -> listOf(
                HexPos(anchor.col, anchor.row),
                HexPos(anchor.col + 1, anchor.row),
                HexPos(anchor.col + 1, anchor.row + 1),
            )
            else -> listOf(
                HexPos(anchor.col, anchor.row),
                HexPos(anchor.col, anchor.row + 1),
                HexPos(anchor.col + 1, anchor.row + 1),
            )
        }
    }

    fun cursorCells(cursor: HexCursor): List<HexPos> = when (cursor.kind) {
        HexCursorKind.FLOWER -> flowerRing(cursor.anchor)
        HexCursorKind.STANDARD -> standardCells(cursor.anchor, cursor.half)
    }

    fun cursorValid(board: HexBoard, cursor: HexCursor): Boolean {
        val cells = cursorCells(cursor)
        val center = if (cursor.kind == HexCursorKind.FLOWER) cursor.anchor else null
        if (center != null && !board.contains(center)) return false
        return cells.all { board.contains(it) && board.isFilled(it) }
    }

    fun cursorRotatable(board: HexBoard, cursor: HexCursor): Boolean =
        cursorValid(board, cursor) && cursorCells(cursor).none { board[it]?.locked == true }

    fun rotate(board: HexBoard, cursor: HexCursor, clockwise: Boolean): HexBoard {
        if (!cursorValid(board, cursor)) return board
        val cells = cursorCells(cursor)
        val pieces = cells.map { board[it]!! }
        val changes = HashMap<HexPos, HexPiece?>()
        for (i in cells.indices) {
            val source = if (clockwise) (i + cells.size - 1) % cells.size else (i + 1) % cells.size
            changes[cells[i]] = pieces[source]
        }
        return board.updated(changes)
    }

    fun rotations(board: HexBoard): List<HexCursor> {
        val cursors = mutableListOf<HexCursor>()
        for (col in 0 until board.width) {
            for (row in 0 until board.height(col)) {
                val anchor = HexPos(col, row)
                cursors += HexCursor(anchor, HexCursorKind.STANDARD, half = false)
                cursors += HexCursor(anchor, HexCursorKind.STANDARD, half = true)
                cursors += HexCursor(anchor, HexCursorKind.FLOWER)
            }
        }
        return cursors
    }

    fun hints(board: HexBoard): List<HexCursor> =
        rotations(board).filter { cursor ->
            if (!cursorRotatable(board, cursor)) return@filter false
            val rotated = rotate(board, cursor, clockwise = true)
            rotated != board && findMatches(rotated).isNotEmpty()
        }

    fun hasMove(board: HexBoard): Boolean {
        for (cursor in rotations(board)) {
            if (!cursorRotatable(board, cursor)) continue
            val rotated = rotate(board, cursor, clockwise = true)
            if (rotated != board && findMatches(rotated).isNotEmpty()) return true
        }
        return false
    }

    private fun sameMatchHue(a: HexColor?, b: HexColor?): Boolean {
        if (a == null || b == null) return false
        if (a.isPearl() && b.isPearl()) return true
        return a == b
    }

    fun findMatches(board: HexBoard): List<HexicMatch> {
        val matches = mutableListOf<HexicMatch>()
        val consumed = HashSet<HexPos>()
        for (col in 0 until board.width) {
            for (row in 0 until board.height(col)) {
                val anchor = HexPos(col, row)
                for (half in listOf(false, true)) {
                    val cells = standardCells(anchor, half)
                    if (!cells.all { board.isFilled(it) }) continue
                    if (cells.any { consumed.contains(it) }) continue
                    val pieces = cells.map { board[it]!! }
                    if (!pieces.all { it.star }) continue
                    if (pieces.map { it.hue }.distinct().size == 1) continue
                    matches += HexicMatch(HexicMatchKind.THREE_BONUS, cells, stars = 3)
                    consumed += cells
                }
            }
        }
        for (col in 0 until board.width) {
            for (row in 0 until board.height(col)) {
                val center = HexPos(col, row)
                val ring = flowerRing(center)
                if (!ring.all { board.isFilled(it) }) continue
                if (ring.any { consumed.contains(it) }) continue
                val first = board[ring.first()]!!.hue
                if (first == HexColor.EMPTY) continue
                if (!ring.all { sameMatchHue(first, board[it]!!.hue) }) continue
                val stars = ring.count { board[it]!!.star }
                matches += HexicMatch(
                    kind = HexicMatchKind.FLOWER,
                    cells = ring,
                    center = center,
                    stars = stars,
                    hue = first,
                )
                consumed += ring
            }
        }
        val visited = HashSet<HexPos>()
        for (col in 0 until board.width) {
            for (row in 0 until board.height(col)) {
                val start = HexPos(col, row)
                if (visited.contains(start)) continue
                val piece = board[start] ?: continue
                if (consumed.contains(start)) continue
                val hue = piece.hue
                if (hue == HexColor.EMPTY) continue
                val component = mutableListOf<HexPos>()
                val stack = ArrayDeque<HexPos>()
                stack.addLast(start)
                visited.add(start)
                while (stack.isNotEmpty()) {
                    val pos = stack.removeLast()
                    component += pos
                    for (neighbor in neighbors(pos)) {
                        if (visited.contains(neighbor)) continue
                        if (consumed.contains(neighbor)) continue
                        val other = board[neighbor] ?: continue
                        if (!sameMatchHue(hue, other.hue)) continue
                        visited.add(neighbor)
                        stack.addLast(neighbor)
                    }
                }
                if (component.size < 3) continue
                val pieces = component.map { board[it]!! }
                val stars = pieces.count { it.star }
                val bombs = pieces.count { it.isBomb }
                val kind = when {
                    pieces.all { it.star } && component.size == 3 -> HexicMatchKind.THREE_BONUS
                    bombs > 0 && stars > 0 && component.size == 3 -> HexicMatchKind.BOMB_BONUS
                    pieces.all { it.hue.isPearl() } -> HexicMatchKind.PEARL_CLUSTER
                    pieces.all { it.hue == HexColor.FLOWER } -> HexicMatchKind.FLOWER_STAR_CLUSTER
                    stars > 0 -> HexicMatchKind.BONUS
                    bombs > 0 -> HexicMatchKind.BOMB
                    else -> HexicMatchKind.CLUSTER
                }
                matches += HexicMatch(kind, component, stars = stars, hue = hue)
            }
        }
        return matches
    }

    fun rotateGame(game: HexicGame, cursor: HexCursor, clockwise: Boolean): HexicGame {
        if (game.won || game.lost) return game
        if (!cursorRotatable(game.board, cursor)) return game
        val rotated = rotate(game.board, cursor, clockwise)
        if (rotated == game.board) return game
        val level = levelFor(game.mode, game.level)
        var bombCountdown = game.bombCountdown
        var bombGrace = game.bombGrace
        if (level.allowBombs) {
            if (bombGrace > 0) bombGrace-- else bombCountdown--
        }
        val spawn = HexicSpawn(
            colors = level.colors,
            allowBonus = level.allowBonus,
            bonusFreq = level.bonusFreq,
            allowBombs = level.allowBombs,
            bombCountdown = bombCountdown,
            bombGrace = bombGrace,
            bombCount = level.bombCount,
            bombFreq = level.bombFreq,
        )
        val result = resolve(rotated, level, game.seed, spawn)
        var score = (game.score + result.gained).coerceAtMost(MAX_SCORE)
        var stats = game.stats
        stats = stats.copy(
            flowers = stats.flowers + result.flowers,
            pearls = stats.pearls + result.pearls,
            bonusClusters = stats.bonusClusters + result.matches.count { it.kind == HexicMatchKind.THREE_BONUS },
            bombsDiffused = stats.bombsDiffused + result.matches.sumOf { m -> m.cells.count { (game.board[it]?.isBomb ?: false) } },
        )
        var combosLeft = game.combosLeft
        if (combosLeft > 0) combosLeft = (combosLeft - result.cleared).coerceAtLeast(0)
        var timeLeft = game.timeLeft
        var timerStarted = game.timerStarted
        if (game.mode == HexicMode.TIMED) {
            if (result.gained > 0) {
                timerStarted = true
                timeLeft += level.validMoveSeconds * result.cleared
                if (result.flowers > 0) timeLeft = 60.0
            } else if (timerStarted) {
                timeLeft += level.invalidMoveSeconds
            }
        }
        var levelNumber = game.level
        var message = ""
        if (game.mode != HexicMode.SURVIVAL && !result.won && combosLeft <= 0 && levelNumber < 7) {
            levelNumber++
            val next = levelFor(game.mode, levelNumber)
            combosLeft = if (next.combos > 0) next.combos else combosLeft
            message = "level ${next.level}"
            if (game.mode == HexicMode.TIMED) timeLeft += next.startSeconds
        }
        var won = result.won
        var lost = result.lost
        var reason = result.reason
        if (game.mode == HexicMode.TIMED && !won && timeLeft <= 0.0) {
            lost = true
            reason = HexicReason.TIME
        }
        var board = result.board
        if (level.allowBombs && !won) {
            val ticks = HashMap<HexPos, HexPiece?>()
            for (col in 0 until board.width) {
                for (row in 0 until board.height(col)) {
                    val pos = HexPos(col, row)
                    val piece = board[pos] ?: continue
                    if (!piece.isBomb) continue
                    val tick = piece.bombTick - 1
                    if (tick <= 0) {
                        lost = true
                        reason = HexicReason.BOMB
                        ticks[pos] = piece.copy(bombTick = 0)
                    } else {
                        ticks[pos] = piece.copy(bombTick = tick)
                    }
                }
            }
            board = board.updated(ticks)
        }
        var stuck = !lost && !won && !hasMove(board)
        var finalSeed = result.seed
        if (stuck && game.mode != HexicMode.SURVIVAL) {
            val refreshed = fillNoMatches(levelFor(game.mode, levelNumber), finalSeed)
            board = refreshed.first
            finalSeed = refreshed.second
            stuck = false
        }
        return game.copy(
            level = levelNumber,
            board = board,
            score = score,
            combosLeft = combosLeft,
            timeLeft = timeLeft,
            timerStarted = timerStarted,
            seed = finalSeed,
            bombCountdown = result.bombCountdown,
            bombGrace = result.bombGrace,
            moves = game.moves + 1,
            stuck = stuck,
            won = won,
            lost = lost,
            reason = reason,
            message = message,
            stats = stats,
        )
    }

    fun tick(game: HexicGame, seconds: Double): HexicGame {
        if (game.won || game.lost || seconds <= 0.0) return game
        return when (game.mode) {
            HexicMode.TIMED -> {
                if (!game.timerStarted) return game
                val left = game.timeLeft - seconds
                if (left <= 0.0) {
                    game.copy(timeLeft = 0.0, lost = true, reason = HexicReason.TIME)
                } else {
                    game.copy(timeLeft = left)
                }
            }
            HexicMode.SURVIVAL -> {
                if (!game.stuck) return game
                val left = game.lockCountdown - seconds
                if (left > 0.0) return game.copy(lockCountdown = left)
                survivalStep(game.copy(lockCountdown = 2.0))
            }
            HexicMode.MARATHON -> game
        }
    }

    fun survivalStep(game: HexicGame): HexicGame {
        if (game.mode != HexicMode.SURVIVAL || game.won || game.lost) return game
        if (game.board.isCleared()) {
            return game.copy(won = true, reason = HexicReason.CLEARED)
        }
        val level = levelFor(HexicMode.SURVIVAL, game.level)
        val positions = allPositions()
        var progress = game.lockProgress
        var bonus = game.lockBonus
        val changes = HashMap<HexPos, HexPiece?>()
        while (progress < positions.size) {
            val pos = positions[progress]
            progress++
            val piece = game.board[pos]
            if (piece == null) {
                bonus += level.level
                continue
            }
            if (piece.locked) continue
            changes[pos] = piece.copy(locked = true)
            break
        }
        var board = game.board.updated(changes)
        if (board.isCleared()) {
            return game.copy(
                board = board,
                score = (game.score + bonus).coerceAtMost(MAX_SCORE),
                won = true,
                reason = HexicReason.CLEARED,
            )
        }
        if (progress >= positions.size) {
            val unlocked = positions.count { pos ->
                val pc = board[pos] ?: return@count false
                !pc.locked
            }
            if (unlocked == 0) {
                return game.copy(board = board, lockProgress = progress, lockBonus = bonus, lost = true, reason = HexicReason.LOCKED)
            }
        }
        var score = game.score
        var survivalLevel = game.survivalLevel
        var levelNumber = game.level
        var seed = game.seed
        if (progress >= positions.size) {
            score = (score + bonus).coerceAtMost(MAX_SCORE)
            bonus = 0
            survivalLevel++
            if (survivalLevel >= 50) {
                return game.copy(
                    board = board,
                    score = score,
                    lockProgress = 0,
                    lockBonus = 0,
                    survivalLevel = survivalLevel,
                    won = true,
                    reason = HexicReason.HIGHEST_LEVEL,
                )
            }
            levelNumber = levelFor(HexicMode.SURVIVAL, survivalLevel).level
            val refreshed = fillNoMatches(levelFor(HexicMode.SURVIVAL, levelNumber), seed)
            board = refreshed.first
            seed = refreshed.second
            progress = 0
        }
        val stuck = !board.isCleared() && !hasMove(board)
        return game.copy(
            board = board,
            score = score,
            survivalLevel = survivalLevel,
            level = levelNumber,
            lockProgress = progress,
            lockBonus = bonus,
            seed = seed,
            stuck = stuck,
            won = board.isCleared(),
            reason = if (board.isCleared()) HexicReason.CLEARED else game.reason,
        )
    }

    fun scoreFor(board: HexBoard, level: HexLevel): Int {
        val matches = findMatches(board)
        val simultaneous = matches
            .filter { it.kind == HexicMatchKind.CLUSTER || it.kind == HexicMatchKind.BONUS || it.kind == HexicMatchKind.BOMB }
            .mapNotNull { it.hue }
            .distinct()
            .size
        return matches.sumOf { scoreMatch(board, it, simultaneous, level) }.coerceAtMost(MAX_SCORE)
    }

    fun scoreMatch(board: HexBoard, match: HexicMatch, simultaneous: Int, level: HexLevel): Int {
        val starFactor = match.stars + 1
        return when (match.kind) {
            HexicMatchKind.PEARL_CLUSTER -> 50000
            HexicMatchKind.FLOWER_STAR_CLUSTER -> 2500
            HexicMatchKind.THREE_BONUS -> 100 * match.cells.size * level.multiplier
            HexicMatchKind.BOMB_BONUS -> 5 * level.multiplier
            HexicMatchKind.FLOWER -> {
                val center = match.center?.let { board[it] }
                val ring = match.hue
                val base = when {
                    ring != null && ring.isPearl() -> if (center?.hue == HexColor.FLOWER) 75000 else 50000
                    ring == HexColor.FLOWER -> if (center?.hue?.isPearl() == true) 15000 else 10000
                    else -> {
                        var value = 1000
                        if (center?.hue == HexColor.FLOWER) value += 500
                        if (center?.hue?.isPearl() == true) value += 1500
                        value
                    }
                }
                base * level.multiplier * starFactor
            }
            else -> (5 + 10 * (simultaneous - 1).coerceAtLeast(0)) *
                level.multiplier * match.cells.size * starFactor
        }
    }

    fun resolve(
        board: HexBoard,
        level: HexLevel,
        seed: Int,
        spawn: HexicSpawn = HexicSpawn(
            colors = level.colors,
            allowBonus = level.allowBonus,
            bonusFreq = level.bonusFreq,
            allowBombs = level.allowBombs,
            bombCountdown = level.bombFreq,
            bombGrace = level.bombGrace,
            bombCount = level.bombCount,
            bombFreq = level.bombFreq,
        ),
    ): HexicResolve {
        var current = board
        var gained = 0
        var cleared = 0
        var rng = seed
        var bombCountdown = spawn.bombCountdown
        val bombGrace = spawn.bombGrace
        val bombPending = spawn.allowBombs && bombCountdown <= 0 && bombGrace <= 0
        if (bombPending) bombCountdown = spawn.bombFreq
        var bombQueued = bombPending
        val allMatches = mutableListOf<HexicMatch>()
        var flowers = 0
        var pearls = 0
        var won = false
        var reason: HexicReason? = null
        var rounds = 0
        while (rounds < 64) {
            rounds++
            val matches = findMatches(current)
            if (matches.isEmpty()) break
            allMatches += matches
            val simultaneous = matches
                .filter { it.kind == HexicMatchKind.CLUSTER || it.kind == HexicMatchKind.BONUS || it.kind == HexicMatchKind.BOMB }
                .mapNotNull { it.hue }
                .distinct()
                .size
            val removals = HashMap<HexPos, HexPiece?>()
            val pearlDrops = IntArray(COLUMNS)
            val flowerDrops = IntArray(COLUMNS)
            for (match in matches) {
                gained += scoreMatch(current, match, simultaneous, level)
                when (match.kind) {
                    HexicMatchKind.PEARL_CLUSTER -> {
                        won = true
                        reason = HexicReason.PEARL_CLUSTER
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                    }
                    HexicMatchKind.FLOWER_STAR_CLUSTER -> {
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                    }
                    HexicMatchKind.FLOWER -> {
                        flowers++
                        val center = match.center!!
                        val centerPiece = current[center]
                        val ringHue = match.hue
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                        when {
                            ringHue != null && ringHue.isPearl() -> {
                                won = true
                                reason = HexicReason.PEARL_FLOWER
                            }
                            ringHue == HexColor.FLOWER -> {
                                if (centerPiece?.hue?.isPearl() == true) pearlDrops[center.col]++
                                if (centerPiece != null) {
                                    removals[center] = centerPiece.copy(
                                        hue = if (seedParity(rng)) HexColor.PEARL1 else HexColor.PEARL2,
                                        star = false,
                                        bombTick = -1,
                                        locked = false,
                                    )
                                }
                                pearls++
                            }
                            centerPiece != null -> {
                                if (centerPiece.hue == HexColor.FLOWER) flowerDrops[center.col]++
                                if (centerPiece.hue.isPearl()) flowerDrops[center.col]++
                                removals[center] = centerPiece.copy(
                                    hue = HexColor.FLOWER,
                                    star = false,
                                    bombTick = -1,
                                    locked = false,
                                )
                            }
                        }
                    }
                    HexicMatchKind.THREE_BONUS -> {
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                        for (cell in match.cells) {
                            for (neighbor in neighbors(cell)) {
                                val np = current[neighbor] ?: continue
                                if (np.hue == HexColor.FLOWER || np.hue.isPearl()) continue
                                removals[neighbor] = null
                            }
                        }
                    }
                    HexicMatchKind.BOMB_BONUS -> {
                        val hue = match.hue
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                        if (hue != null) {
                            for (col in 0 until current.width) {
                                for (row in 0 until current.height(col)) {
                                    val pos = HexPos(col, row)
                                    val piece = current[pos] ?: continue
                                    if (piece.hue == hue && !removals.containsKey(pos)) removals[pos] = null
                                }
                            }
                        }
                    }
                    HexicMatchKind.CLUSTER, HexicMatchKind.BONUS, HexicMatchKind.BOMB -> {
                        match.cells.forEach { removals[it] = null }
                        cleared += match.cells.size
                    }
                }
            }
            current = current.updated(removals)
            val gravity = applyGravityAndRefill(
                board = current,
                level = level,
                spawn = spawn,
                seed = rng,
                pearlDrops = pearlDrops,
                flowerDrops = flowerDrops,
                bombPending = bombQueued,
            )
            current = gravity.board
            rng = gravity.seed
            bombQueued = false
        }
        return HexicResolve(
            board = current,
            gained = gained.coerceAtMost(MAX_SCORE),
            cleared = cleared,
            matches = allMatches,
            flowers = flowers,
            pearls = pearls,
            won = won,
            lost = false,
            reason = reason,
            seed = rng,
            bombCountdown = bombCountdown,
            bombGrace = bombGrace,
        )
    }

    private fun seedParity(seed: Int): Boolean = ((seed ushr 16) and 1) == 0

    private data class RefillResult(val board: HexBoard, val seed: Int)

    private fun applyGravityAndRefill(
        board: HexBoard,
        level: HexLevel,
        spawn: HexicSpawn,
        seed: Int,
        pearlDrops: IntArray,
        flowerDrops: IntArray,
        bombPending: Boolean,
    ): RefillResult {
        val rand = HexicRandom(seed)
        var bombQueued = bombPending
        val changes = HashMap<HexPos, HexPiece?>()
        for (col in 0 until board.width) {
            val height = board.height(col)
            var write = height - 1
            for (row in height - 1 downTo 0) {
                val piece = board[HexPos(col, row)] ?: continue
                if (write != row) {
                    changes[HexPos(col, write)] = piece
                    changes[HexPos(col, row)] = null
                }
                write--
            }
            var spawnRow = write
            while (spawnRow >= 0) {
                val pos = HexPos(col, spawnRow)
                val piece = when {
                    pearlDrops[col] > 0 -> {
                        pearlDrops[col]--
                        HexPiece(if (rand.next(2) == 0) HexColor.PEARL1 else HexColor.PEARL2)
                    }
                    flowerDrops[col] > 0 -> {
                        flowerDrops[col]--
                        HexPiece(HexColor.FLOWER)
                    }
                    else -> {
                        val hue = HEX_COLORS[rand.next(level.colors.coerceIn(1, 7))]
                        val star = spawn.allowBonus && rand.next(100) < spawn.bonusFreq
                        val bomb = bombQueued
                        if (bombQueued) bombQueued = false
                        HexPiece(hue, star = star && !bomb, bombTick = if (bomb) spawn.bombCount else -1)
                    }
                }
                changes[pos] = piece
                spawnRow--
            }
        }
        return RefillResult(board.updated(changes), rand.state)
    }

    fun levelFor(mode: HexicMode, level: Int): HexLevel {
        val table = levelTable(mode)
        val index = (level - 1).coerceIn(0, table.size - 1)
        return table[index]
    }

    fun levelTable(mode: HexicMode): List<HexLevel> = when (mode) {
        HexicMode.MARATHON, HexicMode.TIMED -> listOf(
            HexLevel(1, 1, 50, 5, false, 10, false, 15, 10, 5, 50.0, 0.5, -0.3),
            HexLevel(2, 2, 60, 5, true, 10, false, 15, 10, 5, 40.0, 0.4, -0.4),
            HexLevel(3, 3, 70, 5, true, 15, true, 15, 10, 5, 30.0, 0.4, -0.4),
            HexLevel(4, 4, 80, 6, true, 15, true, 12, 9, 4, 20.0, 0.3, -0.4),
            HexLevel(5, 5, 90, 6, true, 15, true, 12, 8, 3, 15.0, 0.3, -0.5),
            HexLevel(6, 6, 100, 7, true, 20, true, 10, 7, 2, 10.0, 0.2, -0.5),
            HexLevel(7, 7, -1, 7, true, 20, true, 10, 6, 1, 10.0, 0.2, -0.5),
        )
        HexicMode.SURVIVAL -> listOf(
            HexLevel(1, 1, -1, 4, true, 20, false, -1, 10, 5, 50.0, 0.5, -0.3),
            HexLevel(2, 2, -1, 5, true, 20, false, -1, 10, 5, 40.0, 0.4, -0.4),
            HexLevel(3, 3, -1, 5, true, 20, false, -1, 10, 5, 30.0, 0.4, -0.4),
            HexLevel(4, 4, -1, 6, true, 20, false, -1, 9, 4, 20.0, 0.3, -0.4),
            HexLevel(5, 5, -1, 6, true, 20, false, -1, 8, 3, 15.0, 0.3, -0.5),
            HexLevel(6, 6, -1, 7, true, 20, false, -1, 7, 2, 10.0, 0.2, -0.5),
            HexLevel(7, 7, -1, 7, true, 20, false, -1, 6, 1, 10.0, 0.2, -0.5),
        )
    }

    fun newGame(
        mode: HexicMode,
        difficulty: HexicDifficulty,
        seed: Int = 1,
    ): HexicGame {
        val level = levelFor(mode, difficulty.startLevel)
        val built = fillNoMatches(level, seed)
        val time = if (mode == HexicMode.TIMED) level.startSeconds else 0.0
        return HexicGame(
            mode = mode,
            difficulty = difficulty,
            level = level.level,
            board = built.first,
            combosLeft = if (level.combos > 0) level.combos else 0,
            timeLeft = time,
            survivalLevel = level.level,
            seed = built.second,
            bombCountdown = level.bombFreq,
            bombGrace = level.bombGrace,
            stuck = mode == HexicMode.SURVIVAL && !hasMove(built.first),
        )
    }

    private fun fillNoMatches(level: HexLevel, seed: Int): Pair<HexBoard, Int> {
        val rng = HexicRandom(seed)
        var board = emptyBoard()
        var attempts = 0
        do {
            val changes = HashMap<HexPos, HexPiece?>()
            for (col in 0 until COLUMNS) {
                for (row in 0 until cellCount(col)) {
                    changes[HexPos(col, row)] = HexPiece(HEX_COLORS[rng.next(level.colors.coerceIn(1, 7))])
                }
            }
            board = emptyBoard().updated(changes)
            attempts++
        } while (attempts < 12 && findMatches(board).isNotEmpty())
        return board to rng.state
    }

    fun encode(game: HexicGame): String {
        val cells = game.board.columns.joinToString("/") { col ->
            col.joinToString(",") { piece -> encodePiece(piece) }
        }
        return listOf(
            "hx1",
            game.mode.ordinal.toString(),
            game.difficulty.ordinal.toString(),
            game.level.toString(),
            game.score.toString(),
            game.combosLeft.toString(),
            game.timeLeft.toString(),
            if (game.timerStarted) "1" else "0",
            game.survivalLevel.toString(),
            game.lockProgress.toString(),
            game.lockBonus.toString(),
            game.lockCountdown.toString(),
            game.seed.toString(),
            game.bombCountdown.toString(),
            game.bombGrace.toString(),
            game.moves.toString(),
            game.stats.flowers.toString(),
            game.stats.pearls.toString(),
            game.stats.bonusClusters.toString(),
            game.stats.bombsDiffused.toString(),
            cells,
        ).joinToString(";")
    }

    private fun encodePiece(piece: HexPiece?): String {
        if (piece == null) return "."
        val hue = when (piece.hue) {
            HexColor.A -> "a"
            HexColor.B -> "b"
            HexColor.C -> "c"
            HexColor.D -> "d"
            HexColor.E -> "e"
            HexColor.F -> "f"
            HexColor.G -> "g"
            HexColor.PEARL1 -> "p"
            HexColor.PEARL2 -> "q"
            HexColor.FLOWER -> "z"
            HexColor.EMPTY -> "."
        }
        val star = if (piece.star) "s" else ""
        val bomb = if (piece.isBomb) "B${piece.bombTick.coerceIn(0, 9)}" else ""
        val locked = if (piece.locked) "l" else ""
        return hue + star + bomb + locked
    }

    fun decode(blob: String): HexicGame? {
        return try {
            val parts = blob.split(";")
            if (parts.size < 21 || parts[0] != "hx1") return null
            val mode = HexicMode.entries.getOrNull(parts[1].toInt()) ?: return null
            val difficulty = HexicDifficulty.entries.getOrNull(parts[2].toInt()) ?: return null
            val columns = parts[20].split("/").map { col ->
                col.split(",").map { decodePiece(it) }
            }
            HexicGame(
                mode = mode,
                difficulty = difficulty,
                level = parts[3].toInt(),
                board = HexBoard(columns),
                score = parts[4].toInt(),
                combosLeft = parts[5].toInt(),
                timeLeft = parts[6].toDouble(),
                timerStarted = parts[7] == "1",
                survivalLevel = parts[8].toInt(),
                lockProgress = parts[9].toInt(),
                lockBonus = parts[10].toInt(),
                lockCountdown = parts[11].toDouble(),
                seed = parts[12].toInt(),
                bombCountdown = parts[13].toInt(),
                bombGrace = parts[14].toInt(),
                moves = parts[15].toInt(),
                stats = HexicStats(
                    flowers = parts[16].toInt(),
                    pearls = parts[17].toInt(),
                    bonusClusters = parts[18].toInt(),
                    bombsDiffused = parts[19].toInt(),
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePiece(code: String): HexPiece? {
        if (code.isEmpty() || code == ".") return null
        val hue = when (code[0]) {
            'a' -> HexColor.A
            'b' -> HexColor.B
            'c' -> HexColor.C
            'd' -> HexColor.D
            'e' -> HexColor.E
            'f' -> HexColor.F
            'g' -> HexColor.G
            'p' -> HexColor.PEARL1
            'q' -> HexColor.PEARL2
            'z' -> HexColor.FLOWER
            else -> return null
        }
        val star = code.contains('s')
        val locked = code.contains('l')
        var bombTick = -1
        val bombIndex = code.indexOf('B')
        if (bombIndex >= 0 && bombIndex + 1 < code.length) {
            bombTick = code[bombIndex + 1].digitToIntOrNull() ?: 0
        }
        return HexPiece(hue, star, bombTick, locked)
    }

    fun newBoard(size: Int = 7, rng: Random = Random.Default): Array<Array<HexColor>> =
        Array(size) { Array(size) { listOf(HexColor.A, HexColor.B, HexColor.C).random(rng) } }

    fun rotate(board: Array<Array<HexColor>>, r: Int, c: Int): Array<Array<HexColor>> {
        val next = copyOf(board)
        next[r][c] = when (board[r][c]) {
            HexColor.A -> HexColor.B
            HexColor.B -> HexColor.C
            HexColor.EMPTY -> HexColor.A
            else -> HexColor.A
        }
        return next
    }

    fun clusters(board: Array<Array<HexColor>>): Set<Pair<Int, Int>> {
        val n = board.size
        val visited = Array(n) { BooleanArray(n) }
        val result = mutableSetOf<Pair<Int, Int>>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (r in 0 until n) for (c in 0 until n) {
            if (visited[r][c]) continue
            val color = board[r][c]
            if (color == HexColor.EMPTY || !color.isPlayableColor()) {
                visited[r][c] = true
                continue
            }
            val stack = ArrayDeque<Pair<Int, Int>>().apply { add(r to c) }
            val comp = mutableListOf<Pair<Int, Int>>()
            while (stack.isNotEmpty()) {
                val (cr, cc) = stack.removeLast()
                if (cr !in 0 until n || cc !in 0 until n || visited[cr][cc]) continue
                if (board[cr][cc] != color) continue
                visited[cr][cc] = true
                comp += cr to cc
                for ((dr, dc) in dirs) stack.add(cr + dr to cc + dc)
            }
            if (comp.size >= 3) result.addAll(comp)
        }
        return result
    }

    fun clearAndScore(board: Array<Array<HexColor>>, cells: Set<Pair<Int, Int>>): Array<Array<HexColor>> {
        val next = copyOf(board)
        for ((r, c) in cells) next[r][c] = HexColor.EMPTY
        return next
    }

    fun refill(board: Array<Array<HexColor>>, rng: Random = Random.Default): Array<Array<HexColor>> {
        val next = copyOf(board)
        for (r in next.indices) for (c in next.indices) {
            if (next[r][c] == HexColor.EMPTY) next[r][c] = listOf(HexColor.A, HexColor.B, HexColor.C).random(rng)
        }
        return next
    }

    private fun copyOf(board: Array<Array<HexColor>>): Array<Array<HexColor>> =
        Array(board.size) { board[it].copyOf() }
}
