package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.exp

/**
 * Decoder Ring: a substitution-cipher crossword.
 *
 * Pure Kotlin, no Android dependencies. The dataset below is original work for
 * Dorado: twelve 15x15 grids assembled from an original pool of 3x3 word
 * squares (each block is a genuine across/down word square; blocks are spaced
 * by blank rows/columns so every run of letters is a word). The symbol cipher
 * is a deterministic permutation of A-Z seeded by the puzzle index, so a
 * puzzle looks identical on replay.
 */
object DecoderRingEngine {

    const val GRID = 15
    const val PACK_SLOTS = 20
    const val NUM_LETTERS = 27
    const val LETTER_BLANK = 0
    const val MAX_DIFFICULTIES = 4

    // Motion / interaction constants (docs/apps/decoder-ring.md §4).
    const val ZOOM_BOARD = 0.597f
    const val ZOOM_RACK = 0.36f
    const val FLICK_MULT = 1.2f
    const val FLICK_MAX = 3000f
    const val FRICTION = 3f
    const val MOVE_THRESHOLD = 40
    const val DOUBLE_TAP_MS = 500L
    const val WIN_DELAY_TICKS = 60

    enum class Difficulty(val label: String, val revealed: List<Char>) {
        EASY("easy", listOf('T', 'N', 'S', 'R', 'L', 'A', 'E')),
        NORMAL("normal", listOf('T', 'N', 'S', 'E')),
        HARD("hard", listOf('T', 'N', 'S')),
        EXPERT("expert", emptyList()),
    }

    private val LETTERS: List<Char> = ('A'..'Z').toList()

    // ------------------------------------------------------------------
    // Puzzle data (original; see class doc)
    // ------------------------------------------------------------------

    private val PUZZLE_ROWS: List<List<String>> = listOf(
        listOf(
            "pea.car.one.mom",
            "urn.ado.ray.awe",
            "tad.bow.bye.den",
            "...............",
            "big.raw.fan.sit",
            "ice.ate.ate.ace",
            "gem.net.dew.pen",
            "...............",
            "pod.day.hat.hit",
            "awe.age.aye.ire",
            "den.yen.yea.den",
            "...............",
            "nap.rot.got.nap",
            "age.are.air.axe",
            "yet.yea.ply.yen",
        ),
        listOf(
            "beg.gel.can.sub",
            "aye.era.ale.era",
            "gel.tag.dew.any",
            "...............",
            "nib.ewe.pal.new",
            "ire.low.are.ear",
            "let.foe.leg.wry",
            "...............",
            "tag.yew.pad.tap",
            "age.era.ado.ale",
            "got.war.don.bet",
            "...............",
            "gem.was.bay.awe",
            "awe.ago.ale.car",
            "pen.sod.get.err",
        ),
        listOf(
            "say.van.paw.hat",
            "owe.owe.awe.ash",
            "yen.wet.led.sky",
            "...............",
            "dog.day.jot.rip",
            "are.age.ode.ore",
            "met.yen.ten.bet",
            "...............",
            "dam.win.tip.wee",
            "age.ore.ace.err",
            "don.new.peg.ear",
            "...............",
            "cap.pig.sat.bat",
            "ace.ire.ore.ate",
            "beg.gel.pea.den",
        ),
        listOf(
            "sag.tab.nab.hat",
            "age.are.ice.axe",
            "pet.net.beg.den",
            "...............",
            "rat.rag.sod.ran",
            "ale.ire.awe.are",
            "tea.met.yew.new",
            "...............",
            "pad.ash.map.boa",
            "ire.ski.awe.owl",
            "new.hip.net.bee",
            "...............",
            "ray.sap.ram.sap",
            "ode.ape.owe.are",
            "dot.peg.den.yen",
        ),
        listOf(
            "paw.cod.way.sod",
            "ate.owe.are.ode",
            "wed.yen.sea.dew",
            "...............",
            "sat.yaw.gap.ham",
            "axe.ace.ado.ale",
            "tea.met.got.yen",
            "...............",
            "may.zap.bet.ebb",
            "age.ago.ego.boy",
            "ton.pot.top.bye",
            "...............",
            "web.nay.bog.sap",
            "ore.ape.are.ore",
            "ore.yet.get.wet",
        ),
        listOf(
            "rip.owl.sob.pan",
            "are.woe.one.ape",
            "wet.leg.bed.yew",
            "...............",
            "tap.yaw.law.nab",
            "ate.ado.age.ago",
            "pet.wok.pot.boa",
            "...............",
            "soy.foe.roe.dab",
            "ode.inn.owl.aye",
            "yew.bed.elf.bet",
            "...............",
            "tap.low.nap.had",
            "are.awe.ire.axe",
            "beg.wet.pea.ten",
        ),
        listOf(
            "men.bed.hem.rag",
            "ego.ego.ago.ace",
            "not.got.mod.gem",
            "...............",
            "air.had.tea.lot",
            "coo.age.ear.era",
            "end.don.arm.ten",
            "...............",
            "ace.ham.say.lay",
            "cur.ape.ate.ode",
            "tea.men.yen.bow",
            "...............",
            "yap.ebb.ton.boo",
            "air.eye.ire.odd",
            "ply.let.pet.ode",
        ),
        listOf(
            "awe.use.few.led",
            "ray.sag.are.owe",
            "eye.egg.dab.ten",
            "...............",
            "fin.hay.say.bee",
            "ace.ape.owe.awe",
            "new.yew.dew.gel",
            "...............",
            "yap.gas.tab.bay",
            "ado.ode.ago.eye",
            "mop.toe.row.den",
            "...............",
            "rag.tea.lab.wet",
            "aye.wag.ago.awe",
            "yet.ore.boy.den",
        ),
        listOf(
            "say.rag.mat.cab",
            "ire.ape.ice.axe",
            "pea.wet.den.peg",
            "...............",
            "cat.bay.dip.toy",
            "ago.age.ace.ore",
            "too.rot.yet.yen",
            "...............",
            "cop.try.may.aid",
            "owe.hoe.ewe.ice",
            "yea.yet.net.den",
            "...............",
            "sod.mad.jab.hat",
            "awe.axe.are.ice",
            "yen.ten.yet.sea",
        ),
        listOf(
            "fog.ail.rod.cab",
            "one.ire.ore.ado",
            "gel.red.den.boa",
            "...............",
            "sea.rib.far.sap",
            "par.ice.ago.ate",
            "arm.beg.not.yet",
            "...............",
            "his.has.job.cam",
            "ace.ago.awe.ate",
            "sew.toy.met.net",
            "...............",
            "awe.got.gas.tip",
            "leg.era.ire.are",
            "leg.led.net.pet",
        ),
        listOf(
            "wag.hot.sip.cad",
            "ego.ore.ore.ago",
            "bet.pea.yet.bet",
            "...............",
            "pan.pet.pan.fad",
            "are.ore.ire.icy",
            "yet.pan.net.bee",
            "...............",
            "dip.pit.guy.cog",
            "ire.ore.use.ode",
            "pea.pen.men.pet",
            "...............",
            "sat.yes.sap.mop",
            "ire.awe.ore.owe",
            "pen.pet.bet.bet",
        ),
        listOf(
            "nap.its.awe.par",
            "era.rip.coo.are",
            "ten.key.ton.wed",
            "...............",
            "fin.pot.owl.pub",
            "ice.our.foe.era",
            "bet.try.fog.any",
            "...............",
            "sap.jab.fit.coy",
            "oil.ate.ire.owe",
            "wry.bet.tea.pea",
            "...............",
            "rat.lap.nip.web",
            "ore.ate.ore.era",
            "ten.bet.den.tar",
        ),
    )

    data class Puzzle(
        val index: Int,
        val rows: List<String>,
        val used: Set<Char>,
        private val cipher: Map<Char, Int>,
    ) {
        val width: Int get() = GRID
        val height: Int get() = rows.size

        fun cell(row: Int, col: Int): Char = rows[row][col]

        fun isBlank(row: Int, col: Int): Boolean = rows[row][col] == '.'

        /** Symbol shown for a board cell; blank cells use symbol 0. */
        fun symbolAt(row: Int, col: Int): Int {
            val ch = rows[row][col]
            return if (ch == '.') 0 else cipher[ch] ?: 0
        }

        /** Correct symbol for a letter (1..26); blank letters use 0. */
        fun symbolOf(letter: Char): Int = cipher[letter.uppercaseChar()] ?: 0

        /** Correct letter for a symbol, or null when the symbol is unused/blank. */
        fun letterOf(symbol: Int): Char? {
            if (symbol <= 0) return null
            val letter = cipher.entries.firstOrNull { it.value == symbol }?.key ?: return null
            return if (letter in used) letter else null
        }
    }

    val puzzles: List<Puzzle> by lazy { PUZZLE_ROWS.mapIndexed { index, rows -> buildPuzzle(index, rows) } }

    fun puzzle(index: Int): Puzzle {
        val size = puzzles.size
        return puzzles[((index % size) + size) % size]
    }

    fun parseGrid(rows: List<String>): List<List<Char>> {
        require(rows.size == GRID) { "a puzzle is $GRID rows" }
        return rows.map { row ->
            require(row.length == GRID) { "a puzzle row is $GRID wide" }
            row.map { ch ->
                when {
                    ch == '.' -> '.'
                    ch in 'A'..'Z' -> ch
                    ch in 'a'..'z' -> ch.uppercaseChar()
                    else -> throw IllegalArgumentException("bad puzzle character: $ch")
                }
            }
        }
    }

    private fun buildPuzzle(index: Int, rows: List<String>): Puzzle {
        val cells = parseGrid(rows)
        val normalized = cells.map { it.joinToString("") }
        val used = cells.flatten().filter { it != '.' }.toSet()
        val pool = (1..26).toMutableList()
        var rng = 0x2F6E2B1 + index * 0x9E3779B
        for (i in pool.indices.reversed()) {
            rng = rng * 1103515245 + 12345
            val j = ((rng ushr 16) and 0x7FFF) % (i + 1)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }
        val cipher = LETTERS.mapIndexed { i, ch -> ch to pool[i] }.toMap()
        return Puzzle(index, normalized, used, cipher)
    }

    // ------------------------------------------------------------------
    // Progression
    // ------------------------------------------------------------------

    /** Best difficulty ordinal per level slot, -1 when not completed. */
    data class Progress(val completions: List<Int> = List(PACK_SLOTS) { -1 }) {
        fun best(puzzleIndex: Int): Difficulty? {
            val value = completions.getOrNull(puzzleIndex) ?: -1
            return Difficulty.entries.getOrNull(value)
        }
    }

    fun recordCompletion(progress: Progress, puzzleIndex: Int, difficulty: Difficulty): Progress {
        if (puzzleIndex !in 0 until PACK_SLOTS) return progress
        val current = progress.completions[puzzleIndex]
        val next = difficulty.ordinal
        if (next <= current) return progress
        return progress.copy(completions = progress.completions.toMutableList().also { it[puzzleIndex] = next })
    }

    fun unlockedCount(progress: Progress): Int {
        for (i in 0 until PACK_SLOTS) {
            if (progress.completions[i] < 0) return i
        }
        return PACK_SLOTS
    }

    fun isLevelUnlocked(progress: Progress, puzzleIndex: Int): Boolean = puzzleIndex <= unlockedCount(progress)

    fun solvedCount(progress: Progress): Int = progress.completions.count { it >= 0 }

    fun encodeProgress(progress: Progress): String = progress.completions.joinToString(",")

    fun decodeProgress(blob: String): Progress {
        val values = blob.split(",").mapNotNull { it.toIntOrNull() }
        return Progress(List(PACK_SLOTS) { index -> values.getOrNull(index) ?: -1 })
    }

    // ------------------------------------------------------------------
    // Game state
    // ------------------------------------------------------------------

    data class State(
        val puzzleIndex: Int,
        val difficulty: Difficulty,
        /** symbol index (0..26) -> placed letter; null when unmapped. */
        val mapping: List<Char?>,
        /** symbols whose mapping is confirmed and cannot be dragged off. */
        val stone: Set<Int>,
        val highlight: Int,
        val checkEnabled: Boolean,
        val cascadeTicks: Int,
        val moves: Int,
    ) {
        val won: Boolean get() = DecoderRingEngine.isWon(this)
    }

    fun newGame(puzzleIndex: Int, difficulty: Difficulty, checkEnabled: Boolean = true): State {
        val p = puzzle(puzzleIndex)
        val mapping = MutableList<Char?>(NUM_LETTERS) { null }
        val stone = mutableSetOf<Int>()
        difficulty.revealed.forEach { letter ->
            if (letter in p.used) {
                val symbol = p.symbolOf(letter)
                mapping[symbol] = letter
                stone += symbol
            }
        }
        return State(
            puzzleIndex = puzzleIndex,
            difficulty = difficulty,
            mapping = mapping,
            stone = stone,
            highlight = -1,
            checkEnabled = checkEnabled,
            cascadeTicks = 0,
            moves = 0,
        )
    }

    fun highlight(state: State, symbol: Int): State =
        state.copy(highlight = if (symbol in 0 until NUM_LETTERS) symbol else -1)

    fun toggleCheck(state: State): State {
        val next = !state.checkEnabled
        if (!next) return state.copy(checkEnabled = false)
        return state.copy(checkEnabled = true, stone = confirmAll(state))
    }

    private fun confirmAll(state: State): Set<Int> {
        val p = puzzle(state.puzzleIndex)
        val stone = state.stone.toMutableSet()
        for (symbol in 1 until NUM_LETTERS) {
            val placed = state.mapping[symbol] ?: continue
            if (p.symbolOf(placed) == symbol) stone += symbol
        }
        return stone
    }

    /**
     * Drops [letter] on [symbol]. The mapping propagates to every board cell
     * sharing the symbol. A stone slot refuses the drop; placing a letter that
     * already lives on another symbol moves it.
     */
    fun place(state: State, symbol: Int, letter: Char): State {
        if (symbol <= 0 || symbol >= NUM_LETTERS) return state
        if (symbol in state.stone) return state
        val upper = letter.uppercaseChar()
        if (upper !in 'A'..'Z') return state
        if (state.mapping[symbol] == upper) return state
        val p = puzzle(state.puzzleIndex)
        val mapping = MutableList(NUM_LETTERS) { state.mapping[it] }
        for (i in 1 until NUM_LETTERS) {
            if (i != symbol && mapping[i] == upper) mapping[i] = null
        }
        mapping[symbol] = upper
        val stone = state.stone.toMutableSet()
        if (state.checkEnabled && p.symbolOf(upper) == symbol) stone += symbol
        return state.copy(mapping = mapping, stone = stone, moves = state.moves + 1)
    }

    /** Dragging the letter off any slot clears the mapping for that symbol. */
    fun clear(state: State, symbol: Int): State {
        if (symbol <= 0 || symbol >= NUM_LETTERS) return state
        if (symbol in state.stone) return state
        if (state.mapping[symbol] == null) return state
        return state.copy(
            mapping = state.mapping.toMutableList().also { it[symbol] = null },
            moves = state.moves + 1,
        )
    }

    /** Reveal maps the highlighted symbol to its true letter and confirms it. */
    fun reveal(state: State): State {
        val symbol = state.highlight
        if (symbol <= 0 || symbol >= NUM_LETTERS) return state
        if (symbol in state.stone) return state
        val p = puzzle(state.puzzleIndex)
        val correct = p.letterOf(symbol) ?: return state
        val mapping = MutableList(NUM_LETTERS) { state.mapping[it] }
        for (i in 1 until NUM_LETTERS) {
            if (i != symbol && mapping[i] == correct) mapping[i] = null
        }
        mapping[symbol] = correct
        return state.copy(mapping = mapping, stone = state.stone + symbol, moves = state.moves + 1)
    }

    fun isWon(state: State): Boolean {
        val p = puzzle(state.puzzleIndex)
        return p.used.all { letter -> state.mapping[p.symbolOf(letter)] == letter }
    }

    fun isCorrect(state: State, symbol: Int): Boolean {
        val p = puzzle(state.puzzleIndex)
        return state.mapping[symbol] == p.letterOf(symbol)
    }

    /** Event-driven app calls [step] only to advance the win cascade. */
    fun step(state: State, dtMs: Long): State {
        val won = isWon(state)
        val ticks = (dtMs / 16L).coerceAtLeast(1L).toInt()
        val cascade = if (won) (state.cascadeTicks + ticks).coerceAtMost(WIN_DELAY_TICKS) else 0
        return state.copy(cascadeTicks = cascade)
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    fun encode(state: State): String {
        val mapping = state.mapping.joinToString("") { it?.toString() ?: "." }
        val stone = state.stone.sorted().joinToString(",")
        return "1|${state.puzzleIndex}|${state.difficulty.ordinal}|${if (state.checkEnabled) 1 else 0}|$mapping|$stone|${state.highlight}|${state.moves}"
    }

    fun decode(blob: String): State? {
        val parts = blob.split("|")
        if (parts.size < 8 || parts[0] != "1") return null
        val puzzleIndex = parts[1].toIntOrNull() ?: return null
        if (puzzleIndex !in 0 until PACK_SLOTS) return null
        val difficulty = Difficulty.entries.getOrNull(parts[2].toIntOrNull() ?: return null) ?: return null
        val check = parts[3] == "1"
        val mappingText = parts[4]
        if (mappingText.length != NUM_LETTERS) return null
        val mapping = mappingText.map { if (it == '.') null else it }
        val stone = parts[5].split(",").mapNotNull { it.toIntOrNull() }.toSet()
        val highlight = parts[6].toIntOrNull() ?: -1
        val moves = parts[7].toIntOrNull() ?: 0
        return State(puzzleIndex, difficulty, mapping, stone, highlight, check, 0, moves)
    }

    // ------------------------------------------------------------------
    // Interaction math (docs/apps/decoder-ring.md §4)
    // ------------------------------------------------------------------

    /** Flick velocity: recent movement × 1.2, clamped to ±3000 px/s. */
    fun flickVelocity(deltaPx: Float, dtMs: Long): Float {
        if (dtMs <= 0L) return 0f
        return (deltaPx / dtMs * 1000f * FLICK_MULT).coerceIn(-FLICK_MAX, FLICK_MAX)
    }

    /** Friction 3: velocity decays exponentially toward zero. */
    fun applyFriction(velocity: Float, dtMs: Long): Float {
        if (dtMs <= 0L) return velocity
        return (velocity * exp((-FRICTION * dtMs / 1000.0))).toFloat()
    }

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(ZOOM_RACK, ZOOM_BOARD)

    /** Picks the zoom level toggled by a double tap. */
    fun toggleZoom(current: Float): Float =
        if (current > (ZOOM_RACK + ZOOM_BOARD) / 2f) ZOOM_RACK else ZOOM_BOARD

    /**
     * 27 distinguishable glyphs: shape family in 0..5, pip count 0..4.
     * The renderer draws the shapes; the engine only names them.
     */
    fun symbolFamily(symbol: Int): Int = symbol % 6
    fun symbolPips(symbol: Int): Int = symbol / 6
}
