package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.floor

/**
 * WordMonger — clean-room timed word-building puzzle engine.
 *
 * Re-derived from the behavioral spec in docs/apps/wordmonger.md: a 7x7
 * refilling letter board, the classic 98-tile English distribution, straight
 * line word selection, per-tile flying refills, multiplier cells, a bonus word
 * worth x4, bombs / frozen / cracked hazards, a chicken that drops eggs, swaps
 * with an occasional rocket, Rush Mode and a local named leaderboard.
 *
 * Corpus note: the device `.dwg`/`.rnk` dictionary and `BonusWords.txt` are NOT
 * used. [WordMongerEngine.WORDS] is our own compact corpus of common English
 * words, authored for Dorado; word quality is authored by length (3..7 -> 1..5)
 * rather than loaded from the device rank table. Rush Mode's possible-word
 * threshold is scaled to this compact corpus.
 */
object WordMongerEngine {

    const val COLS = 7
    const val ROWS = 7
    const val CELLS = 49
    const val CELL_PX = 44

    const val RUSH_MS = 30_000L
    const val LEVEL_END_GATE_MS = 15_000L
    const val BOMB_FUSE_MS = 30_000L
    const val BOMB_WARN_MS = 18_000L
    const val MAX_BOMBS = 4
    const val BOMB_SPAWN_PERIOD_MS = 15_000L
    const val SWAP_ROCKET_AT = 7
    const val ROCKET_PERCENT = 20
    const val EGG_DROP_NUM = 4
    const val EGG_DROP_DEN = 7
    const val BONUS_PERIOD_MS = 45_000L
    const val CLEAR_BONUS = 1_000
    const val HATTRICK_LENGTH = 5
    const val MULTIPLIER_CELLS = 12
    const val RUSH_WORD_THRESHOLD = 12
    const val BIRD_FLY_MS = 20_000L
    const val MAX_SCORES = 10

    const val AXIS_NONE = 0
    const val AXIS_ROW = 1
    const val AXIS_COL = 2

    val DISTRIBUTION: List<Pair<Char, Int>> = listOf(
        'E' to 12, 'A' to 9, 'I' to 9, 'O' to 8,
        'N' to 6, 'R' to 6, 'T' to 6,
        'L' to 4, 'S' to 4, 'U' to 4, 'D' to 4,
        'G' to 3,
        'B' to 2, 'C' to 2, 'M' to 2, 'P' to 2,
        'F' to 2, 'H' to 2, 'V' to 2, 'W' to 2, 'Y' to 2,
        'K' to 1, 'J' to 1, 'X' to 1, 'Q' to 1, 'Z' to 1,
    )

    val VALUE: Map<Char, Int> = mapOf(
        'E' to 1, 'A' to 1, 'I' to 1, 'O' to 1, 'N' to 1, 'R' to 1, 'T' to 1,
        'L' to 1, 'S' to 1, 'U' to 1, 'D' to 2, 'G' to 2,
        'B' to 3, 'C' to 3, 'M' to 3, 'P' to 3,
        'F' to 4, 'H' to 4, 'V' to 4, 'W' to 4, 'Y' to 4,
        'K' to 5, 'J' to 8, 'X' to 8, 'Q' to 10, 'Z' to 10,
    )

    val WORDS: Set<String> = """
able about ace action air alone also ape are around art ate aunt bag
bald bat bath bee before bell better bid bird bit black bolt born box
brain brew bright bring brother bug build burn but button call camera can care
cat change chat check city clear club cog cold copy cot country crew cry
cup curl dam dance date debt dew dial dim dish doe done drag drink
dry duck each ear earth eel elf else end exit eye fall far fear
feud few fight fin firm fix flea foam fog force ford forest free fry
fuse gag garden gas gave get glad golf grand green grid gulp gum gym
hand has have heat hen here hid hire his history hog hood horse host
how hug hurt icy inch inn ivy jar jeep jig jot journey just keg
kin king knot lad lamb law lawn left leg lie light like lion list
loft log lose lung mad male market mask mat meat mere mind mix monk
morning move mud music nag nail nation need new nip none not oak oath
odd oil only opt option orange order our over owl pair pal panel parrot
parrots past pat pea peace peck person pet picture piece pill pin place plane
planet plea pod poet point pope pray problem program promise pry pulp pun quiet
quit rag raid rainbow rap rare ray rear reason rid right ring rip roar
roe room round rub rule rum rye said sang sat scene sea season seed
sent sew shape sheep shell shirt sick silver sin sit size skill sky slim
smile soar sod solid some sound sour sow space speed spice sport start station
steel step stone story student sty sum summer sunk table tack tag tank tar
target teacher tear ten tend thank thaw think thud tie tiger till toe toll
tooth top tour toy track treat trick trio truth tune tux union unit urban
veal victory visit void walk warp watch weather weed west wife winter wipe wonder
wool word yard yellow yoke
    """.trimIndent().split(Regex("\\s+")).filter { it.length in 3..7 }.toSet()

    private val BONUS_POOL: List<String> = WORDS.filter { it.length >= 5 }.sorted()

    fun qualityOf(word: String): Int = when {
        word.length <= 3 -> 1
        word.length == 4 -> 2
        word.length == 5 -> 3
        word.length == 6 -> 4
        else -> 5
    }

    interface Lexicon {
        fun contains(word: String): Boolean
        fun quality(word: String): Int
        fun bonusWords(): List<String>
    }

    object OwnLexicon : Lexicon {
        override fun contains(word: String): Boolean = word.lowercase() in WORDS
        override fun quality(word: String): Int = qualityOf(word.lowercase())
        override fun bonusWords(): List<String> = BONUS_POOL
    }

    class WmRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun next(bound: Int): Int = if (bound <= 0) 0 else ((nextSeed() ushr 16) and 0x7FFF) % bound

        fun nextFloat(): Float = ((nextSeed() ushr 8) and 0xFFFF) / 65_536f
    }

    enum class Event {
        TILE, DESELECT, ACCEPT, REJECT, HATTRICK, BONUS, LEVEL, RUSH, CLEAR,
        SWAP, ROCKET, BOMB, BOMB_WARN, EXPLODE, BIRD, EGG, BIRD_HIT, CRACK,
        DEFUSE, GAMEOVER, RESUME,
    }

    data class Tile(
        val id: Int,
        val letter: Char,
        val value: Int,
        val frozen: Boolean = false,
        val cracked: Boolean = false,
        val bombRemainingMs: Long = -1L,
        val warned: Boolean = false,
        val fallCells: Float = 0f,
    ) {
        val isBomb: Boolean get() = bombRemainingMs >= 0L
    }

    data class Bird(val row: Int, val xCells: Float, val vx: Float, val drops: Int)

    data class Egg(val col: Int, val row: Float, val vy: Float)

    data class ScoreEntry(val name: String, val score: Int, val level: Int, val bestWord: String)

    data class State(
        val level: Int = 1,
        val tiles: List<Tile?>,
        val multipliers: List<Int>,
        val queue: List<Char>,
        val selection: List<Int> = emptyList(),
        val axis: Int = AXIS_NONE,
        val bagSeed: Int,
        val rngSeed: Int,
        val nextId: Int = 1,
        val score: Int = 0,
        val words: Int = 0,
        val levelMs: Long = 0L,
        val bonusWord: String? = null,
        val bonusTimerMs: Long = BONUS_PERIOD_MS,
        val rushMs: Long = 0L,
        val bombSpawnMs: Long = BOMB_SPAWN_PERIOD_MS,
        val bird: Bird? = null,
        val birdTimerMs: Long = BIRD_FLY_MS,
        val egg: Egg? = null,
        val swapCount: Int = 0,
        val longStreak: Int = 0,
        val wordPower: Float = 0f,
        val speedPower: Float = 0f,
        val bestWord: String = "",
        val longestWord: String = "",
        val message: String = "",
        val gameOver: Boolean = false,
        val tutorial: Boolean = false,
        val events: List<Event> = emptyList(),
    ) {
        val liveTiles: Int get() = tiles.count { it != null }
        val rushActive: Boolean get() = rushMs > 0L
    }

    fun bagTiles(): List<Char> = buildList {
        for ((letter, count) in DISTRIBUTION) repeat(count) { add(letter) }
    }

    private fun shuffle(list: MutableList<Char>, rng: WmRandom) {
        for (i in list.size - 1 downTo 1) {
            val j = rng.next(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    fun drawLetters(level: Int, seed: Int, tutorial: Boolean = false): List<Char> {
        val rng = WmRandom(seed)
        val bag = bagTiles().toMutableList()
        repeat(3) { shuffle(bag, rng) }
        val pool = bag.toMutableList()
        val count = CELLS + level * COLS
        val drawn = ArrayList<Char>(count)
        repeat(count) {
            if (pool.isEmpty()) {
                pool += bagTiles()
                shuffle(pool, rng)
            }
            drawn += pool.removeAt(pool.lastIndex)
        }
        if (tutorial && drawn.size >= 4) {
            drawn[drawn.size - 4] = 'F'
            drawn[drawn.size - 3] = 'U'
            drawn[drawn.size - 2] = 'N'
            drawn[drawn.size - 1] = 'O'
        }
        return drawn
    }

    fun multiplierMask(seed: Int): List<Int> {
        val rng = WmRandom(seed)
        val mask = MutableList(CELLS) { 1 }
        val cells = (0 until CELLS).toMutableList()
        for (i in cells.size - 1 downTo 1) {
            val j = rng.next(i + 1)
            val tmp = cells[i]
            cells[i] = cells[j]
            cells[j] = tmp
        }
        for (i in 0 until MULTIPLIER_CELLS) {
            mask[cells[i]] = when {
                i < MULTIPLIER_CELLS / 2 -> 2
                i < MULTIPLIER_CELLS - 2 -> 3
                else -> 4
            }
        }
        return mask
    }

    fun multiplierCounts(mask: List<Int>): Map<Int, Int> =
        mask.filter { it > 1 }.groupingBy { it }.eachCount()

    fun newGame(level: Int = 1, seed: Int = 1, tutorial: Boolean = false): State {
        val letters = drawLetters(level, seed, tutorial)
        val mask = multiplierMask(seed xor 0x5A5A)
        val tiles = letters.take(CELLS).mapIndexed { index, ch ->
            Tile(id = index + 1, letter = ch, value = VALUE[ch] ?: 1)
        }
        return State(
            level = level,
            tiles = tiles,
            multipliers = mask,
            queue = letters.drop(CELLS),
            bagSeed = seed,
            rngSeed = seed,
            nextId = CELLS + 1,
            tutorial = tutorial,
        )
    }

    fun nextLevel(state: State, seed: Int? = null): State {
        val nextSeed = seed ?: (state.rngSeed * 31 + state.level + 17)
        val level = state.level + 1
        val letters = drawLetters(level, nextSeed)
        val mask = multiplierMask(nextSeed xor 0x1234)
        val tiles = letters.take(CELLS).mapIndexed { index, ch ->
            Tile(id = state.nextId + index, letter = ch, value = VALUE[ch] ?: 1)
        }
        return state.copy(
            level = level,
            tiles = tiles,
            multipliers = mask,
            queue = letters.drop(CELLS),
            selection = emptyList(),
            axis = AXIS_NONE,
            levelMs = 0L,
            bonusWord = null,
            bonusTimerMs = BONUS_PERIOD_MS,
            rushMs = 0L,
            bombSpawnMs = BOMB_SPAWN_PERIOD_MS,
            bird = null,
            birdTimerMs = BIRD_FLY_MS,
            egg = null,
            swapCount = 0,
            longStreak = 0,
            nextId = state.nextId + CELLS,
            message = "level $level",
            events = (state.events + Event.LEVEL).distinct(),
        )
    }

    fun row(index: Int): Int = index / COLS

    fun col(index: Int): Int = index % COLS

    fun cellIndex(row: Int, col: Int): Int = row * COLS + col

    fun isContiguous(selection: List<Int>, axis: Int): Boolean {
        if (selection.size < 2) return true
        for (i in 1 until selection.size) {
            val previous = selection[i - 1]
            val current = selection[i]
            val adjacency = when (axis) {
                AXIS_ROW -> row(previous) == row(current) && abs(col(previous) - col(current)) == 1
                AXIS_COL -> col(previous) == col(current) && abs(row(previous) - row(current)) == 1
                else -> false
            }
            if (!adjacency) return false
        }
        return true
    }

    fun select(state: State, index: Int): State {
        if (state.gameOver) return state
        val tile = state.tiles.getOrNull(index) ?: return state.copy(events = listOf(Event.REJECT))
        if (tile.frozen) return state.copy(events = listOf(Event.REJECT), message = "frozen")
        if (state.selection.isEmpty()) {
            return state.copy(selection = listOf(index), axis = AXIS_NONE, events = listOf(Event.TILE))
        }
        if (index == state.selection.last()) {
            val selection = state.selection.dropLast(1)
            return state.copy(selection = selection, axis = if (selection.size < 2) AXIS_NONE else state.axis, events = listOf(Event.TILE))
        }
        if (index in state.selection) {
            val position = state.selection.indexOf(index)
            val selection = state.selection.take(position + 1)
            return state.copy(selection = selection, axis = if (selection.size < 2) AXIS_NONE else state.axis, events = listOf(Event.DESELECT))
        }
        val first = state.selection.first()
        val newAxis = when {
            state.axis != AXIS_NONE -> state.axis
            row(first) == row(index) -> AXIS_ROW
            col(first) == col(index) -> AXIS_COL
            else -> AXIS_NONE
        }
        if (newAxis == AXIS_NONE) {
            return state.copy(selection = listOf(index), axis = AXIS_NONE, events = listOf(Event.REJECT))
        }
        val candidate = state.selection + index
        if (!isContiguous(candidate, newAxis) || candidate.size > ROWS) {
            return state.copy(selection = listOf(index), axis = AXIS_NONE, events = listOf(Event.REJECT))
        }
        return state.copy(selection = candidate, axis = newAxis, events = listOf(Event.TILE))
    }

    fun clearSelection(state: State): State {
        if (state.selection.isEmpty()) return state
        return state.copy(selection = emptyList(), axis = AXIS_NONE, events = listOf(Event.DESELECT))
    }

    fun selectedWord(state: State): String =
        state.selection.joinToString("") { state.tiles[it]?.letter?.lowercaseChar()?.toString() ?: "" }

    fun pointsFor(values: List<Int>, multipliers: List<Int>, quality: Int, bonus: Boolean, hattrick: Boolean): Int {
        val multiplied = values.indices.sumOf { values[it] * multipliers.getOrElse(it) { 1 } }
        var points = multiplied * quality
        if (bonus) points *= 4
        if (hattrick) points *= 2
        return points
    }

    fun submit(state: State, lexicon: Lexicon = OwnLexicon): State {
        if (state.gameOver || state.selection.isEmpty()) return state
        val word = selectedWord(state).lowercase()
        if (word.length < 3 || state.axis == AXIS_NONE || !isContiguous(state.selection, state.axis)) {
            return state.copy(selection = emptyList(), axis = AXIS_NONE, message = "straight lines only", events = listOf(Event.REJECT))
        }
        if (!lexicon.contains(word)) {
            return state.copy(selection = emptyList(), axis = AXIS_NONE, message = "not a word", events = listOf(Event.REJECT))
        }
        val indices = state.selection
        val tiles = indices.map { state.tiles[it] ?: return state }
        val values = tiles.map { it.value }
        val multipliers = indices.map { state.multipliers[it] }
        val quality = lexicon.quality(word)
        val isBonus = word == state.bonusWord
        val hattrick = state.longStreak + 1 >= 3 && word.length >= HATTRICK_LENGTH
        val points = pointsFor(values, multipliers, quality, isBonus, hattrick)
        val events = ArrayList<Event>()
        events += Event.ACCEPT
        if (isBonus) events += Event.BONUS
        if (hattrick) events += Event.HATTRICK

        val board = state.tiles.toMutableList()
        var crackedBroken = 0
        for (index in indices) {
            val tile = board[index] ?: continue
            if (tile.isBomb) {
                events += Event.DEFUSE
                board[index] = tile.copy(bombRemainingMs = -1L, warned = false)
            } else {
                if (tile.cracked) crackedBroken++
                board[index] = null
            }
        }
        if (crackedBroken > 0) events += Event.CRACK

        val birdHit = state.bird?.let { bird ->
            val column = floor(bird.xCells).toInt().coerceIn(0, COLS - 1)
            val birdCell = cellIndex(bird.row, column)
            birdCell in indices
        } ?: false
        if (birdHit) events += Event.BIRD_HIT

        val refilled = refill(board, state.queue, state.nextId, state.rngSeed)
        val streak = if (word.length >= HATTRICK_LENGTH) {
            if (hattrick) 0 else state.longStreak + 1
        } else {
            0
        }
        return state.copy(
            tiles = refilled.tiles,
            queue = refilled.queue,
            nextId = refilled.nextId,
            rngSeed = refilled.rngSeed,
            selection = emptyList(),
            axis = AXIS_NONE,
            score = state.score + points,
            words = state.words + 1,
            wordPower = state.wordPower * 0.8f + points * 0.2f,
            speedPower = state.speedPower * 0.7f + word.length * 0.3f,
            bestWord = if (word.length > state.bestWord.length) word else state.bestWord,
            longestWord = if (word.length > state.longestWord.length) word else state.longestWord,
            bonusWord = if (isBonus) null else state.bonusWord,
            bonusTimerMs = if (isBonus) BONUS_PERIOD_MS else state.bonusTimerMs,
            longStreak = streak,
            bird = if (birdHit) null else state.bird,
            message = "$word +$points",
            events = events,
        )
    }

    private data class Refill(val tiles: List<Tile?>, val queue: List<Char>, val nextId: Int, val rngSeed: Int)

    private fun refill(removed: List<Tile?>, queue: List<Char>, nextId: Int, rngSeed: Int): Refill {
        val tiles = removed.toMutableList()
        val pending = queue.toMutableList()
        var id = nextId
        for (column in 0 until COLS) {
            var write = ROWS - 1
            for (r in ROWS - 1 downTo 0) {
                val index = cellIndex(r, column)
                val tile = tiles[index]
                if (tile != null) {
                    if (write != r) {
                        tiles[cellIndex(write, column)] = tile.copy(fallCells = (write - r).toFloat())
                        tiles[index] = null
                    }
                    write--
                }
            }
            for (r in write downTo 0) {
                if (pending.isEmpty()) break
                val letter = pending.removeAt(0)
                tiles[cellIndex(r, column)] = Tile(
                    id = id++,
                    letter = letter,
                    value = VALUE[letter] ?: 1,
                    fallCells = (write - r + 1).toFloat(),
                )
            }
        }
        return Refill(tiles, pending, id, rngSeed)
    }

    fun swap(state: State, from: Int, to: Int): State {
        if (state.gameOver) return state
        if (from !in 0 until CELLS || to !in 0 until CELLS) return state
        val a = state.tiles[from] ?: return state.copy(events = listOf(Event.REJECT))
        val b = state.tiles[to] ?: return state.copy(events = listOf(Event.REJECT))
        if (a.frozen || b.frozen) return state.copy(events = listOf(Event.REJECT), message = "locked")
        val adjacent = (row(from) == row(to) && abs(col(from) - col(to)) == 1) ||
            (col(from) == col(to) && abs(row(from) - row(to)) == 1)
        if (!adjacent) return state.copy(events = listOf(Event.REJECT), message = "wrong axis")
        val tiles = state.tiles.toMutableList()
        tiles[from] = b
        tiles[to] = a
        val rng = WmRandom(state.rngSeed)
        var swapCount = state.swapCount + 1
        val events = mutableListOf(Event.SWAP)
        var message = "swap"
        if (swapCount >= SWAP_ROCKET_AT) {
            swapCount = 0
            if (rng.next(100) < ROCKET_PERCENT) {
                val candidates = (0 until CELLS).filter { tiles[it]?.frozen != true }
                if (candidates.size >= 2) {
                    val first = candidates[rng.next(candidates.size)]
                    var second = candidates[rng.next(candidates.size)]
                    if (second == first) second = candidates[(candidates.indexOf(first) + 1) % candidates.size]
                    val tmp = tiles[first]
                    tiles[first] = tiles[second]
                    tiles[second] = tmp
                    events += Event.ROCKET
                    message = "rocket"
                }
            }
        }
        return state.copy(tiles = tiles, swapCount = swapCount, rngSeed = rng.state, message = message, events = events)
    }

    fun possibleWordCount(state: State): Int =
        WORDS.count { word -> word.length <= ROWS && formable(state.tiles, word) }

    private fun formable(tiles: List<Tile?>, word: String): Boolean {
        val available = HashMap<Char, Int>()
        for (tile in tiles) {
            if (tile != null) available[tile.letter] = (available[tile.letter] ?: 0) + 1
        }
        for (ch in word.uppercase()) {
            val count = available[ch] ?: return false
            if (count <= 0) return false
            available[ch] = count - 1
        }
        return true
    }

    fun step(state: State, dtMs: Long): State {
        if (state.gameOver) return state
        val dt = dtMs.coerceIn(0L, 200L).toFloat()
        val rng = WmRandom(state.rngSeed)
        val events = ArrayList<Event>()
        var message = state.message

        val levelMs = state.levelMs + dtMs
        var bonusWord = state.bonusWord
        var bonusTimer = state.bonusTimerMs
        if (bonusWord == null) {
            bonusTimer -= dtMs
            if (bonusTimer <= 0L) {
                if (BONUS_POOL.isNotEmpty()) {
                    bonusWord = BONUS_POOL[rng.next(BONUS_POOL.size)]
                    events += Event.BONUS
                    message = "bonus word: ${bonusWord.length} letters"
                }
                bonusTimer = BONUS_PERIOD_MS
            }
        }

        val tiles = state.tiles.toMutableList()
        var bombCount = 0
        for (index in tiles.indices) {
            val tile = tiles[index] ?: continue
            if (!tile.isBomb) continue
            bombCount++
            val remaining = tile.bombRemainingMs - dtMs
            if (remaining <= 0L) {
                return state.copy(
                    tiles = tiles,
                    gameOver = true,
                    message = "the bomb went off",
                    events = events + Event.EXPLODE + Event.GAMEOVER,
                )
            }
            val warned = tile.warned || remaining <= BOMB_WARN_MS
            if (!tile.warned && warned) {
                events += Event.BOMB_WARN
                message = "watch out"
            }
            tiles[index] = tile.copy(bombRemainingMs = remaining, warned = warned)
        }

        var bombSpawn = state.bombSpawnMs - dtMs
        if (bombSpawn <= 0L) {
            bombSpawn = BOMB_SPAWN_PERIOD_MS
            if (bombCount < MAX_BOMBS) {
                val candidates = tiles.indices.filter { tiles[it]?.frozen != true && tiles[it]?.isBomb != true }
                if (candidates.isNotEmpty()) {
                    val index = candidates[rng.next(candidates.size)]
                    tiles[index] = tiles[index]?.copy(bombRemainingMs = BOMB_FUSE_MS, warned = false)
                    events += Event.BOMB
                }
            }
        }

        var bird = state.bird
        var egg = state.egg
        if (egg != null) {
            val target = (bird?.row ?: ROWS - 1).coerceIn(0, ROWS - 1)
            val row = egg.row + egg.vy * dt / 1000f
            if (row >= target) {
                val index = cellIndex(target, egg.col.coerceIn(0, COLS - 1))
                val tile = tiles[index]
                if (tile != null && !tile.cracked) {
                    tiles[index] = tile.copy(cracked = true)
                    events += Event.EGG
                }
                egg = null
            } else {
                egg = egg.copy(row = row)
            }
        }

        var birdTimer = state.birdTimerMs - dtMs
        if (bird == null && birdTimer <= 0L) {
            birdTimer = BIRD_FLY_MS
            bird = Bird(row = rng.next(ROWS), xCells = -1f, vx = 2.4f, drops = 0)
            events += Event.BIRD
        }
        if (bird != null) {
            val moved = bird.xCells + bird.vx * dt / 1000f
            val crossed = floor(moved) > floor(bird.xCells)
            var drops = bird.drops
            if (crossed && egg == null && rng.next(EGG_DROP_DEN) < EGG_DROP_NUM) {
                egg = Egg(col = rng.next(COLS), row = bird.row.toFloat() - 1f, vy = 5.5f)
                drops++
            }
            bird = if (moved > COLS + 1f) null else bird.copy(xCells = moved, drops = drops)
        }

        var rushMs = state.rushMs
        if (rushMs > 0L) {
            rushMs = (rushMs - dtMs).coerceAtLeast(0L)
            if (rushMs == 0L) {
                message = "rush over"
                return nextLevel(
                    state.copy(
                        tiles = tiles,
                        egg = egg,
                        bird = bird,
                        birdTimerMs = birdTimer,
                        bombSpawnMs = bombSpawn,
                        levelMs = levelMs,
                        bonusWord = null,
                        bonusTimerMs = bonusTimer,
                        rushMs = 0L,
                        rngSeed = rng.state,
                        message = message,
                        events = events + Event.RUSH,
                    ),
                    rng.state + 1,
                )
            }
        }

        var out = state.copy(
            tiles = tiles,
            egg = egg,
            bird = bird,
            birdTimerMs = birdTimer,
            bombSpawnMs = bombSpawn,
            levelMs = levelMs,
            bonusWord = bonusWord,
            bonusTimerMs = bonusTimer,
            rushMs = rushMs,
            rngSeed = rng.state,
            message = message,
            events = events,
        )

        if (levelMs >= LEVEL_END_GATE_MS && rushMs == 0L) {
            val live = out.liveTiles
            if (live == 0) {
                out = out.copy(score = out.score + CLEAR_BONUS, message = "board cleared +$CLEAR_BONUS")
                return nextLevel(out.copy(events = events + Event.CLEAR), out.rngSeed + 1)
            }
            if (live <= 20) {
                val possible = possibleWordCount(out)
                if (possible == 0) {
                    return nextLevel(out.copy(events = events + Event.LEVEL), out.rngSeed + 1)
                }
                if (possible < RUSH_WORD_THRESHOLD) {
                    out = out.copy(rushMs = RUSH_MS, bonusWord = null, message = "rush mode", events = events + Event.RUSH)
                }
            }
        }

        return settleFalls(out, dt)
    }

    private fun settleFalls(state: State, dt: Float): State {
        if (state.tiles.none { (it?.fallCells ?: 0f) > 0f }) return state
        val tiles = state.tiles.map { tile ->
            if (tile == null) null else tile.copy(fallCells = (tile.fallCells - 14f * dt / 1000f).coerceAtLeast(0f))
        }
        return state.copy(tiles = tiles)
    }

    object LocalScores {
        fun rank(entries: List<ScoreEntry>, entry: ScoreEntry): List<ScoreEntry> =
            (entries + entry)
                .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.level }.thenBy { it.name })
                .take(MAX_SCORES)

        fun rank(entries: List<ScoreEntry>): List<ScoreEntry> =
            entries.sortedWith(compareByDescending<ScoreEntry> { it.score }.thenByDescending { it.level }.thenBy { it.name })
                .take(MAX_SCORES)

        fun encode(entries: List<ScoreEntry>): String =
            entries.joinToString("|") { "${it.name.replace("|", " ").replace(",", " ")};${it.score};${it.level};${it.bestWord}" }

        fun decode(blob: String?): List<ScoreEntry> {
            if (blob.isNullOrBlank()) return emptyList()
            return blob.split("|").mapNotNull { row ->
                val parts = row.split(";")
                if (parts.size != 4) return@mapNotNull null
                val score = parts[1].toIntOrNull() ?: return@mapNotNull null
                val level = parts[2].toIntOrNull() ?: 1
                ScoreEntry(parts[0], score, level, parts[3])
            }
        }
    }

    fun encode(state: State): String {
        val board = state.tiles.joinToString(",") { tile ->
            if (tile == null) {
                "."
            } else {
                "${tile.letter}:${if (tile.frozen) 1 else 0}:${if (tile.cracked) 1 else 0}:${tile.bombRemainingMs}:${if (tile.warned) 1 else 0}"
            }
        }
        return "v1|${state.level}|${state.score}|${state.words}|${state.bestWord}|${state.longestWord}|" +
            "${state.levelMs}|${state.bonusTimerMs}|${state.rushMs}|${state.bagSeed}|${state.rngSeed}|" +
            "${state.multipliers.joinToString("")}|$board|${state.queue.joinToString("")}"
    }

    fun decode(blob: String?): State? {
        if (blob.isNullOrBlank()) return null
        val parts = blob.split("|")
        if (parts.size != 14 || parts[0] != "v1") return null
        val level = parts[1].toIntOrNull() ?: return null
        val score = parts[2].toIntOrNull() ?: return null
        val words = parts[3].toIntOrNull() ?: return null
        val best = parts[4]
        val longest = parts[5]
        val levelMs = parts[6].toLongOrNull() ?: return null
        val bonusTimer = parts[7].toLongOrNull() ?: return null
        val rushMs = parts[8].toLongOrNull() ?: return null
        val bagSeed = parts[9].toIntOrNull() ?: return null
        val rngSeed = parts[10].toIntOrNull() ?: return null
        val multipliers = parts[11].mapNotNull { it.digitToIntOrNull() }.takeIf { it.size == CELLS } ?: return null
        val board = parts[12].split(",")
        if (board.size != CELLS) return null
        var nextId = 1
        val tiles = ArrayList<Tile?>(CELLS)
        for (token in board) {
            if (token == ".") {
                tiles += null
                continue
            }
            val fields = token.split(":")
            if (fields.size != 5 || fields[0].isEmpty()) return null
            val letter = fields[0][0]
            tiles += Tile(
                id = nextId++,
                letter = letter,
                value = VALUE[letter] ?: 1,
                frozen = fields[1] == "1",
                cracked = fields[2] == "1",
                bombRemainingMs = fields[3].toLongOrNull() ?: return null,
                warned = fields[4] == "1",
            )
        }
        return State(
            level = level,
            tiles = tiles,
            multipliers = multipliers,
            queue = parts[13].toList(),
            bagSeed = bagSeed,
            rngSeed = rngSeed,
            score = score,
            words = words,
            levelMs = levelMs,
            bonusTimerMs = bonusTimer,
            rushMs = rushMs,
            bestWord = best,
            longestWord = longest,
            message = "resumed",
            events = listOf(Event.RESUME),
        )
    }
}
