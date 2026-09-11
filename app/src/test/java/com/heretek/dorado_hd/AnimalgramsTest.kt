package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.AnimalgramsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalgramsTest {

    private fun typeWord(state: AnimalgramsEngine.AnagramState, word: String): AnimalgramsEngine.AnagramState {
        var current = state
        for (ch in word) {
            val index = current.tiles.indexOfFirst { !it.used && it.ch.lowercaseChar() == ch }
            assertTrue("no tile for $ch", index >= 0)
            current = AnimalgramsEngine.type(current, index)
        }
        return current
    }

    @Test
    fun `the corpus has five habitats of five animals`() {
        assertEquals(5, AnimalgramsEngine.HABITATS.size)
        AnimalgramsEngine.HABITATS.forEach { habitat ->
            assertEquals(5, habitat.animals.size)
            habitat.animals.forEach { animal ->
                assertTrue(AnimalgramsEngine.wordsFor(animal).size >= 5)
            }
        }
        assertEquals(25, AnimalgramsEngine.ALL_ANIMALS.distinct().size)
    }

    @Test
    fun `every dictionary word is formable from its animal and at least three letters`() {
        for (animal in AnimalgramsEngine.ALL_ANIMALS) {
            val available = HashMap<Char, Int>()
            for (ch in animal) available[ch] = (available[ch] ?: 0) + 1
            AnimalgramsEngine.wordsFor(animal).forEach { word ->
                assertTrue("$word too short", word.length >= 3)
                assertTrue("$word not lowercase", word == word.lowercase())
                val counts = HashMap<Char, Int>()
                for (ch in word.uppercase()) counts[ch] = (counts[ch] ?: 0) + 1
                counts.forEach { (ch, need) ->
                    assertTrue("$word cannot be made from $animal", (available[ch] ?: 0) >= need)
                }
            }
        }
    }

    @Test
    fun `word lists sort shortest first then alphabetically`() {
        for (animal in AnimalgramsEngine.ALL_ANIMALS) {
            val words = AnimalgramsEngine.wordsFor(animal)
            assertEquals(words, words.distinct())
            for (i in 1 until words.size) {
                val previous = words[i - 1]
                val current = words[i]
                assertTrue(
                    "$animal list out of order: $previous then $current",
                    previous.length < current.length || (previous.length == current.length && previous <= current),
                )
            }
        }
    }

    @Test
    fun `submitting a valid word records it and clears the field`() {
        var state = typeWord(AnimalgramsEngine.newRound("TIGER"), "tiger")
        state = AnimalgramsEngine.submit(state)
        assertTrue("tiger" in state.found)
        assertEquals("tiger", state.lastSubmitted)
        assertEquals("", state.typedWord)
        assertTrue(state.tiles.none { it.used })
        assertTrue(AnimalgramsEngine.Event.ACCEPTED in state.events)
    }

    @Test
    fun `six letter words emit the distinct long reveal event`() {
        var state = typeWord(AnimalgramsEngine.newRound("PARROT"), "parrot")
        state = AnimalgramsEngine.submit(state)
        assertTrue(AnimalgramsEngine.Event.LONG_WORD in state.events)
        assertTrue(AnimalgramsEngine.Event.ACCEPTED in state.events)
    }

    @Test
    fun `duplicate words buzz and are not recorded twice`() {
        var state = typeWord(AnimalgramsEngine.newRound("TIGER"), "tie")
        state = AnimalgramsEngine.submit(state)
        state = typeWord(state, "tie")
        state = AnimalgramsEngine.submit(state)
        assertEquals(1, state.found.count { it == "tie" })
        assertTrue(AnimalgramsEngine.Event.INVALID in state.events)
    }

    @Test
    fun `words outside the animal column are rejected`() {
        var state = typeWord(AnimalgramsEngine.newRound("TIGER"), "get")
        state = AnimalgramsEngine.submit(state)
        assertTrue("get" in state.found)
        val foreign = typeWord(AnimalgramsEngine.newRound("TIGER"), "tig")
        val rejected = AnimalgramsEngine.submit(foreign)
        assertTrue(AnimalgramsEngine.Event.INVALID in rejected.events)
        assertTrue(rejected.found.isEmpty())
    }

    @Test
    fun `erase pops one letter and re-enables the exact duplicate tile`() {
        var state = AnimalgramsEngine.newRound("OTTER")
        val first = state.tiles.indexOfFirst { !it.used && it.ch == 'T' }
        state = AnimalgramsEngine.type(state, first)
        val second = state.tiles.indexOfFirst { !it.used && it.ch == 'T' }
        state = AnimalgramsEngine.type(state, second)
        assertEquals(2, state.typed.size)
        state = AnimalgramsEngine.erase(state)
        assertEquals(1, state.typed.size)
        assertFalse(state.tiles[second].used)
        assertTrue(state.tiles[first].used)
    }

    @Test
    fun `hold to clear erases the whole field after half a second`() {
        var state = typeWord(AnimalgramsEngine.newRound("TIGER"), "tie")
        state = AnimalgramsEngine.holdErase(state, 250L)
        assertEquals(3, state.typed.size)
        state = AnimalgramsEngine.holdErase(state, 250L)
        assertEquals(0, state.typed.size)
        assertTrue(state.tiles.none { it.used })
        assertTrue(AnimalgramsEngine.Event.CLEAR in state.events)
    }

    @Test
    fun `reenter restores the last submitted word`() {
        var state = typeWord(AnimalgramsEngine.newRound("TIGER"), "tier")
        state = AnimalgramsEngine.submit(state)
        state = AnimalgramsEngine.reenter(state)
        assertEquals("tier", state.typedWord)
    }

    @Test
    fun `hints fill one letter, lock for a second, and prefer the typed prefix`() {
        var state = AnimalgramsEngine.newRound("TIGER", seed = 12)
        state = typeWord(state, "ti")
        val hinted = AnimalgramsEngine.hint(state)
        assertEquals(1_000L, hinted.hintLockMs)
        assertTrue(hinted.typedWord.startsWith("ti"))
        assertEquals(3, hinted.typedWord.length)
        assertTrue(AnimalgramsEngine.Event.HINT in hinted.events)
        val unlocked = AnimalgramsEngine.step(hinted, 1_000L)
        assertEquals(0L, unlocked.hintLockMs)
    }

    @Test
    fun `hints never contradict the typed prefix`() {
        for (seed in 1..20) {
            var state = AnimalgramsEngine.newRound("PARROT", seed = seed)
            state = typeWord(state, "p")
            val hinted = AnimalgramsEngine.hint(state)
            assertTrue(hinted.typedWord.startsWith("p"))
        }
    }

    @Test
    fun `a full animal wins and a habitat star needs every animal`() {
        var state = AnimalgramsEngine.newRound("LION")
        AnimalgramsEngine.wordsFor("LION").forEach { word ->
            state = typeWord(state, word)
            state = AnimalgramsEngine.submit(state)
        }
        assertTrue(state.won)
        assertTrue(AnimalgramsEngine.Event.WIN in state.events)

        val habitat = AnimalgramsEngine.HABITATS.first { "LION" in it.animals }
        var progress = AnimalgramsEngine.AnagramProgress()
        progress = AnimalgramsEngine.record(progress, "LION", "lion")
        assertTrue(AnimalgramsEngine.animalPercent(progress, "LION") > 0)
        assertFalse(AnimalgramsEngine.hasStar(progress, habitat))
        habitat.animals.forEach { animal ->
            AnimalgramsEngine.wordsFor(animal).forEach { word -> progress = AnimalgramsEngine.record(progress, animal, word) }
        }
        assertTrue(AnimalgramsEngine.hasStar(progress, habitat))
        assertEquals(100, AnimalgramsEngine.habitatPercent(progress, habitat))
    }

    @Test
    fun `percent math floors found over available`() {
        val progress = AnimalgramsEngine.AnagramProgress(
            found = mapOf("LION" to setOf("lion", "oil")),
        )
        val total = AnimalgramsEngine.wordsFor("LION").size
        assertEquals(2 * 100 / total, AnimalgramsEngine.animalPercent(progress, "LION"))
        assertEquals(1, progress.found.size)
    }

    @Test
    fun `progress encode and decode round trip and ignore foreign words`() {
        var progress = AnimalgramsEngine.AnagramProgress()
        progress = AnimalgramsEngine.record(progress, "TIGER", "tie")
        progress = AnimalgramsEngine.record(progress, "TIGER", "doesnotexist")
        progress = AnimalgramsEngine.record(progress, "OTTER", "otter")
        val blob = AnimalgramsEngine.encodeProgress(progress)
        val restored = AnimalgramsEngine.decodeProgress(blob)
        assertEquals(setOf("tie"), AnimalgramsEngine.foundWords(restored, "TIGER"))
        assertEquals(setOf("otter"), AnimalgramsEngine.foundWords(restored, "OTTER"))
        assertTrue(AnimalgramsEngine.decodeProgress("junk:junk").found.isEmpty())
        assertTrue(AnimalgramsEngine.decodeProgress(null).found.isEmpty())
    }

    @Test
    fun `reset clears all found words`() {
        var progress = AnimalgramsEngine.AnagramProgress(found = mapOf("TIGER" to setOf("tie")))
        progress = AnimalgramsEngine.reset(progress)
        assertTrue(progress.found.isEmpty())
    }

    @Test
    fun `the english only gate keys off the language tag`() {
        assertTrue(AnimalgramsEngine.isEnglishTag("en"))
        assertTrue(AnimalgramsEngine.isEnglishTag("en-US"))
        assertTrue(AnimalgramsEngine.isEnglishTag("EN-gb"))
        assertFalse(AnimalgramsEngine.isEnglishTag("fr"))
        assertFalse(AnimalgramsEngine.isEnglishTag(""))
    }

    @Test
    fun `the compact corpus stays within a few hundred words`() {
        assertTrue(AnimalgramsEngine.allWords().size in 150..400)
        assertTrue(AnimalgramsEngine.allWords().all { it.length >= 3 })
    }

    @Test
    fun `typing and step are deterministic`() {
        val first = AnimalgramsEngine.newRound("PANTHER", seed = 99)
        val second = AnimalgramsEngine.newRound("PANTHER", seed = 99)
        assertEquals(first.tiles, second.tiles)
        val hintedFirst = AnimalgramsEngine.hint(AnimalgramsEngine.type(first, 0))
        val hintedSecond = AnimalgramsEngine.hint(AnimalgramsEngine.type(second, 0))
        assertEquals(hintedFirst.typed, hintedSecond.typed)
        assertEquals(hintedFirst.rngSeed, hintedSecond.rngSeed)
        assertEquals(1_000L, hintedFirst.hintLockMs)
    }
}
