package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.WordMongerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordMongerTest {

    private fun board(vararg rows: String): List<WordMongerEngine.Tile?> {
        val out = ArrayList<WordMongerEngine.Tile?>(WordMongerEngine.CELLS)
        var id = 1
        for (r in 0 until WordMongerEngine.ROWS) {
            val row = rows.getOrElse(r) { "EEEEEEE" }.uppercase()
            for (c in 0 until WordMongerEngine.COLS) {
                val ch = row.getOrElse(c) { 'E' }
                out += WordMongerEngine.Tile(id++, ch, WordMongerEngine.VALUE[ch] ?: 1)
            }
        }
        return out
    }

    private fun stateWith(
        rows: List<String>,
        selection: List<Int> = emptyList(),
        axis: Int = WordMongerEngine.AXIS_NONE,
        rngSeed: Int = 1,
        swapCount: Int = 0,
    ): WordMongerEngine.State = WordMongerEngine.State(
        tiles = board(*rows.toTypedArray()),
        multipliers = List(WordMongerEngine.CELLS) { 1 },
        queue = List(28) { 'E' },
        selection = selection,
        axis = axis,
        bagSeed = rngSeed,
        rngSeed = rngSeed,
        swapCount = swapCount,
    )

    private fun selectRange(state: WordMongerEngine.State, indices: List<Int>): WordMongerEngine.State {
        var current = state
        indices.forEach { current = WordMongerEngine.select(current, it) }
        return current
    }

    @Test
    fun `the classic bag holds ninety eight tiles in the authored counts`() {
        val bag = WordMongerEngine.bagTiles()
        assertEquals(98, bag.size)
        val grouped = bag.groupingBy { it }.eachCount()
        WordMongerEngine.DISTRIBUTION.forEach { (letter, count) ->
            assertEquals("letter $letter", count, grouped[letter])
        }
        assertEquals(WordMongerEngine.DISTRIBUTION.map { it.first }.toSet(), grouped.keys)
    }

    @Test
    fun `tile values stay between one and ten`() {
        assertEquals(1, WordMongerEngine.VALUE['E'])
        assertEquals(10, WordMongerEngine.VALUE['Q'])
        assertEquals(10, WordMongerEngine.VALUE['Z'])
        WordMongerEngine.VALUE.forEach { (_, value) -> assertTrue(value in 1..10) }
    }

    @Test
    fun `a level draws forty nine plus seven per level and is seed deterministic`() {
        assertEquals(56, WordMongerEngine.drawLetters(1, 5).size)
        assertEquals(63, WordMongerEngine.drawLetters(2, 5).size)
        assertEquals(
            WordMongerEngine.drawLetters(3, 77),
            WordMongerEngine.drawLetters(3, 77),
        )
    }

    @Test
    fun `the tutorial seeds funo near the draw tail`() {
        val drawn = WordMongerEngine.drawLetters(1, 9, tutorial = true)
        assertEquals(listOf('F', 'U', 'N', 'O'), drawn.takeLast(4))
    }

    @Test
    fun `twelve multiplier cells carry six twos four threes and two fours`() {
        val mask = WordMongerEngine.multiplierMask(99)
        assertEquals(WordMongerEngine.CELLS, mask.size)
        val counts = WordMongerEngine.multiplierCounts(mask)
        assertEquals(6, counts[2])
        assertEquals(4, counts[3])
        assertEquals(2, counts[4])
        assertEquals(12, mask.count { it > 1 })
        for (seed in 1..20) {
            assertTrue(WordMongerEngine.multiplierMask(seed).all { it in 1..4 })
        }
    }

    @Test
    fun `the authored lexicon is compact, lowercase and quality ranked by length`() {
        assertTrue(WordMongerEngine.WORDS.size in 200..400)
        assertTrue(WordMongerEngine.WORDS.all { it == it.lowercase() && it.length in 3..7 })
        assertTrue(WordMongerEngine.OwnLexicon.contains("cat"))
        assertTrue(WordMongerEngine.OwnLexicon.contains("PLANE"))
        assertFalse(WordMongerEngine.OwnLexicon.contains("zzzzz"))
        assertEquals(1, WordMongerEngine.qualityOf("cat"))
        assertEquals(2, WordMongerEngine.qualityOf("word"))
        assertEquals(3, WordMongerEngine.qualityOf("plane"))
        assertEquals(4, WordMongerEngine.qualityOf("button"))
        assertEquals(5, WordMongerEngine.qualityOf("parrots"))
    }

    @Test
    fun `selection stays in a straight contiguous line`() {
        val start = stateWith(listOf("CATEEEE"))
        val row = selectRange(start, listOf(0, 1, 2))
        assertEquals(listOf(0, 1, 2), row.selection)
        assertEquals(WordMongerEngine.AXIS_ROW, row.axis)

        val gap = WordMongerEngine.select(row, 4)
        assertTrue(WordMongerEngine.Event.REJECT in gap.events)
        assertEquals(listOf(4), gap.selection)

        val cross = WordMongerEngine.select(row, 7)
        assertEquals(listOf(7), cross.selection)
        assertEquals(WordMongerEngine.AXIS_NONE, cross.axis)
    }

    @Test
    fun `column selection works and preserves direction`() {
        val start = stateWith(listOf("CATEEEE"))
        val column = selectRange(start, listOf(2, 9, 16))
        assertEquals(WordMongerEngine.AXIS_COL, column.axis)
        assertEquals(listOf(2, 9, 16), column.selection)
        assertEquals("tee", WordMongerEngine.selectedWord(column).lowercase())

        val reversed = selectRange(start, listOf(2, 1, 0))
        assertEquals("tac", WordMongerEngine.selectedWord(reversed).lowercase())
    }

    @Test
    fun `frozen tiles cannot be selected or swapped`() {
        val tiles = board("CATEEEE").toMutableList()
        tiles[5] = tiles[5]!!.copy(frozen = true)
        val state = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = List(10) { 'E' },
            bagSeed = 1,
            rngSeed = 1,
        )
        val refused = WordMongerEngine.select(state, 5)
        assertTrue(WordMongerEngine.Event.REJECT in refused.events)
        assertTrue(refused.selection.isEmpty())

        val frozenNeighbour = tiles.toMutableList()
        frozenNeighbour[1] = frozenNeighbour[1]!!.copy(frozen = true)
        val swapState = state.copy(tiles = frozenNeighbour, selection = emptyList())
        val swapped = WordMongerEngine.swap(swapState, 0, 1)
        assertTrue(WordMongerEngine.Event.REJECT in swapped.events)
        assertEquals(0, swapped.swapCount)
    }

    @Test
    fun `scoring multiplies values then word quality then bonus and hattrick`() {
        assertEquals(26, WordMongerEngine.pointsFor(listOf(1, 2, 3), listOf(2, 1, 3), 2, bonus = false, hattrick = false))
        assertEquals(104, WordMongerEngine.pointsFor(listOf(1, 2, 3), listOf(2, 1, 3), 2, bonus = true, hattrick = false))
        assertEquals(52, WordMongerEngine.pointsFor(listOf(1, 2, 3), listOf(2, 1, 3), 2, bonus = false, hattrick = true))
        assertEquals(208, WordMongerEngine.pointsFor(listOf(1, 2, 3), listOf(2, 1, 3), 2, bonus = true, hattrick = true))
    }

    @Test
    fun `submitting a valid word scores and refills from the queue`() {
        val state = stateWith(listOf("CATEEEE"), selection = listOf(0, 1, 2), axis = WordMongerEngine.AXIS_ROW)
        val next = WordMongerEngine.submit(state)
        assertTrue(WordMongerEngine.Event.ACCEPT in next.events)
        assertEquals(5, next.score)
        assertEquals(1, next.words)
        assertEquals(WordMongerEngine.CELLS, next.liveTiles)
        assertTrue(next.selection.isEmpty())
        assertEquals("cat", next.bestWord)
        assertEquals(WordMongerEngine.AXIS_NONE, next.axis)
        assertEquals(25, next.queue.size)
    }

    @Test
    fun `three long words in a row trigger the hattrick double on the third`() {
        var state = stateWith(
            listOf(
                "PLANEEE",
                "PLACEEE",
                "PANELEE",
            ),
        )
        state = selectRange(state, listOf(0, 1, 2, 3, 4))
        state = WordMongerEngine.submit(state)
        assertEquals(1, state.longStreak)
        val afterFirst = state.score

        state = selectRange(state, listOf(7, 8, 9, 10, 11))
        state = WordMongerEngine.submit(state)
        assertEquals(2, state.longStreak)
        val afterSecond = state.score

        state = selectRange(state, listOf(14, 15, 16, 17, 18))
        state = WordMongerEngine.submit(state)
        assertTrue(WordMongerEngine.Event.HATTRICK in state.events)
        assertEquals(0, state.longStreak)
        val third = state.score - afterSecond
        assertEquals(42, third)
        assertEquals(21, afterFirst - 0)
        assertEquals(27, afterSecond - afterFirst)
    }

    @Test
    fun `a clear board pays one thousand and advances the level`() {
        val state = WordMongerEngine.State(
            tiles = List(WordMongerEngine.CELLS) { null },
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = emptyList(),
            bagSeed = 1,
            rngSeed = 1,
            levelMs = WordMongerEngine.LEVEL_END_GATE_MS,
        )
        val next = WordMongerEngine.step(state, 20L)
        assertEquals(2, next.level)
        assertEquals(WordMongerEngine.CLEAR_BONUS, next.score)
        assertTrue(WordMongerEngine.Event.CLEAR in next.events)
    }

    @Test
    fun `no possible word advances the level after the gate`() {
        val tiles = List<WordMongerEngine.Tile?>(WordMongerEngine.CELLS) { index ->
            if (index < 20) WordMongerEngine.Tile(index + 1, 'Q', 10) else null
        }
        val state = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = emptyList(),
            bagSeed = 1,
            rngSeed = 1,
            levelMs = WordMongerEngine.LEVEL_END_GATE_MS,
        )
        assertEquals(0, WordMongerEngine.possibleWordCount(state))
        val next = WordMongerEngine.step(state, 20L)
        assertEquals(2, next.level)
    }

    @Test
    fun `few possible words start a thirty second rush`() {
        val letters = "QUIT" + "Q".repeat(16)
        val tiles = List<WordMongerEngine.Tile?>(WordMongerEngine.CELLS) { index ->
            if (index < 20) WordMongerEngine.Tile(index + 1, letters[index], WordMongerEngine.VALUE[letters[index]] ?: 1) else null
        }
        val state = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = emptyList(),
            bagSeed = 1,
            rngSeed = 1,
            levelMs = WordMongerEngine.LEVEL_END_GATE_MS,
            bonusWord = "plane",
        )
        val possible = WordMongerEngine.possibleWordCount(state)
        assertTrue("possible $possible", possible in 1 until WordMongerEngine.RUSH_WORD_THRESHOLD)
        val next = WordMongerEngine.step(state, 20L)
        assertTrue(next.rushActive)
        assertEquals(WordMongerEngine.RUSH_MS, next.rushMs)
        assertNull(next.bonusWord)
        assertTrue(WordMongerEngine.Event.RUSH in next.events)
    }

    @Test
    fun `rush expiry forces the next level`() {
        val state = stateWith(listOf("CATEEEE")).copy(rushMs = 20L, levelMs = 0L)
        val next = WordMongerEngine.step(state, 20L)
        assertEquals(2, next.level)
        assertEquals(0L, next.rushMs)
    }

    @Test
    fun `bombs warn at eighteen seconds and explode at zero`() {
        val tiles = board("CATEEEE").toMutableList()
        tiles[0] = tiles[0]!!.copy(bombRemainingMs = 18_010L)
        val warning = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = emptyList(),
            bagSeed = 1,
            rngSeed = 1,
        )
        val warned = WordMongerEngine.step(warning, 20L)
        assertTrue(WordMongerEngine.Event.BOMB_WARN in warned.events)
        assertTrue(warned.tiles[0]!!.warned)
        assertFalse(warned.gameOver)

        tiles[0] = tiles[0]!!.copy(bombRemainingMs = 10L)
        val exploding = warning.copy(tiles = tiles)
        val boom = WordMongerEngine.step(exploding, 20L)
        assertTrue(boom.gameOver)
        assertTrue(WordMongerEngine.Event.EXPLODE in boom.events)
        assertTrue(WordMongerEngine.Event.GAMEOVER in boom.events)
    }

    @Test
    fun `using a bomb tile in a word defuses it`() {
        val tiles = board("CATEEEE").toMutableList()
        tiles[0] = tiles[0]!!.copy(bombRemainingMs = 25_000L)
        val state = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = List(10) { 'E' },
            bagSeed = 1,
            rngSeed = 1,
            selection = listOf(0, 1, 2),
            axis = WordMongerEngine.AXIS_ROW,
        )
        val next = WordMongerEngine.submit(state)
        assertTrue(WordMongerEngine.Event.DEFUSE in next.events)
        assertNotNull(next.tiles[0])
        assertFalse(next.tiles[0]!!.isBomb)
    }

    @Test
    fun `cracked tiles shatter when used`() {
        val tiles = board("CATEEEE").toMutableList()
        tiles[2] = tiles[2]!!.copy(cracked = true)
        val state = WordMongerEngine.State(
            tiles = tiles,
            multipliers = List(WordMongerEngine.CELLS) { 1 },
            queue = List(10) { 'E' },
            bagSeed = 1,
            rngSeed = 1,
            selection = listOf(0, 1, 2),
            axis = WordMongerEngine.AXIS_ROW,
        )
        val next = WordMongerEngine.submit(state)
        assertTrue(WordMongerEngine.Event.CRACK in next.events)
        assertNotNull(next.tiles[2])
        assertFalse(next.tiles[2]!!.cracked)
    }

    @Test
    fun `swaps count and the seventh can call a rocket`() {
        var rocketSeed = -1
        var quietSeed = -1
        for (seed in 1..2_000) {
            val roll = WordMongerEngine.WmRandom(seed).next(100)
            if (rocketSeed < 0 && roll < WordMongerEngine.ROCKET_PERCENT) rocketSeed = seed
            if (quietSeed < 0 && roll >= WordMongerEngine.ROCKET_PERCENT) quietSeed = seed
        }
        assertTrue(rocketSeed > 0)
        assertTrue(quietSeed > 0)

        val base = stateWith(listOf("CATEEEE"), rngSeed = rocketSeed, swapCount = 6)
        val swapped = WordMongerEngine.swap(base, 0, 1)
        assertTrue(WordMongerEngine.Event.SWAP in swapped.events)
        assertTrue(WordMongerEngine.Event.ROCKET in swapped.events)
        assertEquals(0, swapped.swapCount)

        val quiet = stateWith(listOf("CATEEEE"), rngSeed = quietSeed)
        val one = WordMongerEngine.swap(quiet, 0, 1)
        assertEquals(1, one.swapCount)
        assertFalse(WordMongerEngine.Event.ROCKET in one.events)

        val blocked = WordMongerEngine.swap(quiet, 0, 8)
        assertTrue(WordMongerEngine.Event.REJECT in blocked.events)
        assertEquals(0, blocked.swapCount)
    }

    @Test
    fun `the bonus timer announces a word and playing it pays four times`() {
        val state = stateWith(listOf("CATEEEE")).copy(bonusTimerMs = 10L)
        val announced = WordMongerEngine.step(state, 20L)
        assertNotNull(announced.bonusWord)
        assertTrue(WordMongerEngine.Event.BONUS in announced.events)

        val bonus = "cat"
        val ready = stateWith(listOf("CATEEEE"), selection = listOf(0, 1, 2), axis = WordMongerEngine.AXIS_ROW)
            .copy(bonusWord = bonus)
        val scored = WordMongerEngine.submit(ready)
        assertEquals(5 * 4, scored.score)
        assertNull(scored.bonusWord)
    }

    @Test
    fun `resume encode and decode round trip a game`() {
        var state = WordMongerEngine.newGame(level = 2, seed = 42)
        state = state.copy(score = 1_234, words = 7, levelMs = 8_000L, bestWord = "plane", longestWord = "parrot")
        val restored = WordMongerEngine.decode(WordMongerEngine.encode(state))
        assertNotNull(restored)
        restored!!
        assertEquals(state.level, restored.level)
        assertEquals(state.score, restored.score)
        assertEquals(state.words, restored.words)
        assertEquals(state.bestWord, restored.bestWord)
        assertEquals(state.longestWord, restored.longestWord)
        assertEquals(state.levelMs, restored.levelMs)
        assertEquals(state.tiles, restored.tiles)
        assertEquals(state.queue, restored.queue)
        assertEquals(state.multipliers, restored.multipliers)
        assertNull(WordMongerEngine.decode("garbage"))
        assertNull(WordMongerEngine.decode(null))
    }

    @Test
    fun `the local leaderboard ranks by score then level and keeps ten`() {
        val entries = (1..12).map { WordMongerEngine.ScoreEntry("p$it", it * 100, it, "word") }
        val ranked = WordMongerEngine.LocalScores.rank(entries)
        assertEquals(WordMongerEngine.MAX_SCORES, ranked.size)
        assertEquals(1_200, ranked.first().score)
        assertEquals(300, ranked.last().score)
        for (i in 1 until ranked.size) {
            assertTrue(ranked[i - 1].score >= ranked[i].score)
        }

        val tieA = WordMongerEngine.ScoreEntry("a", 500, 3, "cat")
        val tieB = WordMongerEngine.ScoreEntry("b", 500, 5, "cat")
        val tie = WordMongerEngine.LocalScores.rank(listOf(tieA), tieB)
        assertEquals(2, tie.size)
        assertEquals(5, tie.first().level)
    }

    @Test
    fun `step is deterministic for a seed`() {
        val first = WordMongerEngine.newGame(level = 1, seed = 7)
        val second = WordMongerEngine.newGame(level = 1, seed = 7)
        var a = first
        var b = second
        repeat(50) {
            a = WordMongerEngine.step(a, 20L)
            b = WordMongerEngine.step(b, 20L)
        }
        assertEquals(a.tiles, b.tiles)
        assertEquals(a.rngSeed, b.rngSeed)
        assertEquals(a.bonusWord, b.bonusWord)
    }

    @Test
    fun `new games refuse invalid words and clear the selection`() {
        val state = stateWith(listOf("ZZZEEEE"), selection = listOf(0, 1, 2), axis = WordMongerEngine.AXIS_ROW)
        val next = WordMongerEngine.submit(state)
        assertTrue(WordMongerEngine.Event.REJECT in next.events)
        assertTrue(next.selection.isEmpty())
        assertEquals(0, next.score)
    }
}
