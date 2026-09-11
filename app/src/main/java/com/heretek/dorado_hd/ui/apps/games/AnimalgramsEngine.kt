package com.heretek.dorado_hd.ui.apps.games

/**
 * Animalgrams — clean-room anagram game engine.
 *
 * Re-derived from the behavioral spec in docs/apps/animalgrams.md (25 animals
 * across five habitats; tap tiles to spell words formed from the animal's
 * letters; hints reveal the next letter of an unfound word; per-animal,
 * per-habitat and global completion drive stars).
 *
 * Corpus note: the device's `WordBank.csv` is NOT used. [WORD_BANK] below is
 * our own compact corpus, authored for Dorado, of common English words that
 * are each spellable from the letters of their animal key. Tests assert every
 * entry is formable and that the lists are sorted shortest-first.
 */
object AnimalgramsEngine {

    data class Habitat(val key: String, val label: String, val animals: List<String>)

    val HABITATS: List<Habitat> = listOf(
        Habitat("jungle", "jungle", listOf("TIGER", "MONKEY", "PARROT", "IGUANA", "PANTHER")),
        Habitat("savanna", "savanna", listOf("LION", "ZEBRA", "GIRAFFE", "CHEETAH", "RHINO")),
        Habitat("forest", "forest", listOf("RABBIT", "BADGER", "SQUIRREL", "HEDGEHOG", "OTTER")),
        Habitat("aqua", "aqua", listOf("WHALE", "DOLPHIN", "OCTOPUS", "TURTLE", "SHARK")),
        Habitat("aviary", "aviary", listOf("EAGLE", "FALCON", "PELICAN", "TOUCAN", "SPARROW")),
    )

    private val AUTHORED: Map<String, List<String>> = mapOf(
        "TIGER" to listOf("tie", "tier", "tire", "rite", "grit", "ire", "rig", "get", "tiger"),
        "MONKEY" to listOf("one", "eon", "key", "yoke", "omen", "monk", "money", "monkey"),
        "PARROT" to listOf("pat", "rat", "tar", "art", "rot", "oar", "par", "part", "port", "trap", "roar", "parrot"),
        "IGUANA" to listOf("gun", "gnu", "gin", "nag", "gain", "again", "iguana"),
        "PANTHER" to listOf("ant", "eat", "tea", "ear", "the", "hat", "rat", "part", "pear", "heat", "hare", "near", "path", "earth", "heart", "panther"),
        "LION" to listOf("oil", "ion", "nil", "lin", "lion", "loin"),
        "ZEBRA" to listOf("ear", "era", "are", "bar", "bra", "bear", "bare", "raze", "zebra"),
        "GIRAFFE" to listOf("fire", "fair", "fare", "fear", "gear", "rage", "riff", "fife", "grief", "giraffe"),
        "CHEETAH" to listOf("cat", "hat", "eat", "tea", "act", "the", "heat", "hate", "each", "chat", "teach", "cheetah"),
        "RHINO" to listOf("nor", "ion", "iron", "horn", "noir", "rhino"),
        "RABBIT" to listOf("rat", "bat", "bit", "rib", "bar", "art", "air", "bait", "brat", "barb", "rabbit"),
        "BADGER" to listOf("bad", "bag", "bed", "red", "beg", "rag", "dare", "dear", "read", "bear", "rage", "grab", "bread", "badger"),
        "SQUIRREL" to listOf("sir", "ire", "lie", "use", "rule", "ruse", "user", "sure", "rise", "quire", "squire", "squirrel"),
        "HEDGEHOG" to listOf("dog", "doe", "hoe", "ego", "ode", "egg", "edge", "hedge", "geode", "hedgehog"),
        "OTTER" to listOf("toe", "rot", "tore", "rote", "tote", "tort", "otter"),
        "WHALE" to listOf("ale", "law", "hew", "wale", "hale", "heal", "weal", "whale"),
        "DOLPHIN" to listOf("pin", "pod", "nod", "hip", "hop", "oil", "ion", "dip", "old", "idol", "plod", "pond", "hold", "dolphin"),
        "OCTOPUS" to listOf("cop", "cot", "top", "pot", "put", "out", "cup", "opt", "coup", "pout", "soup", "cost", "spout", "octopus"),
        "TURTLE" to listOf("let", "rut", "tut", "true", "rule", "lure", "lute", "utter", "turtle"),
        "SHARK" to listOf("ask", "ash", "ark", "has", "hark", "rash", "shark"),
        "EAGLE" to listOf("ale", "gel", "leg", "age", "eel", "glee", "gale", "eagle"),
        "FALCON" to listOf("con", "fan", "clan", "loan", "foal", "coal", "cola", "calf", "flan", "falcon"),
        "PELICAN" to listOf("pan", "pen", "pin", "can", "ice", "nice", "lace", "pace", "plan", "plane", "panel", "clean", "place", "pelican"),
        "TOUCAN" to listOf("can", "cot", "cut", "not", "out", "tan", "ton", "tuna", "coat", "cant", "count", "canto", "toucan"),
        "SPARROW" to listOf("sap", "rap", "war", "raw", "spar", "roar", "soap", "wrap", "warp", "arrow", "sparrow"),
    )

    val WORD_BANK: Map<String, List<String>> = AUTHORED.mapValues { (_, words) ->
        words.distinct().sortedWith(compareBy({ it.length }, { it }))
    }

    val ALL_ANIMALS: List<String> = HABITATS.flatMap { it.animals }

    fun wordsFor(animal: String): List<String> = WORD_BANK[animal.uppercase()] ?: emptyList()

    fun lettersFor(animal: String): List<Char> = animal.uppercase().toList()

    fun allWords(): List<String> = WORD_BANK.values.flatten().distinct()

    fun isEnglishTag(languageTag: String): Boolean = languageTag.lowercase().startsWith("en")

    fun habitatOf(animal: String): Habitat? =
        HABITATS.firstOrNull { animal.uppercase() in it.animals }

    fun isWord(animal: String, word: String): Boolean =
        WORD_BANK[animal.uppercase()]?.contains(word.lowercase()) == true

    class AnagramRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun next(bound: Int): Int = if (bound <= 0) 0 else ((nextSeed() ushr 16) and 0x7FFF) % bound
    }

    enum class Event { TYPE, ERASE, CLEAR, INVALID, ACCEPTED, LONG_WORD, HINT, HINT_LOCKED, WIN }

    data class Tile(val ch: Char, val used: Boolean = false)

    data class AnagramState(
        val animal: String,
        val tiles: List<Tile>,
        val typed: List<Int> = emptyList(),
        val found: Set<String> = emptySet(),
        val lastSubmitted: String = "",
        val hintLockMs: Long = 0L,
        val eraseHoldMs: Long = 0L,
        val message: String = "",
        val rngSeed: Int = 7,
        val events: List<Event> = emptyList(),
    ) {
        val typedWord: String get() = typed.joinToString("") { tiles[it].ch.lowercaseChar().toString() }
        val available: Int get() = wordsFor(animal).size
        val won: Boolean get() = found.size >= available && available > 0
    }

    fun newRound(animal: String, seed: Int = 7): AnagramState {
        val key = animal.uppercase()
        return AnagramState(
            animal = key,
            tiles = lettersFor(key).map { Tile(it) },
            rngSeed = seed,
        )
    }

    fun type(state: AnagramState, tileIndex: Int): AnagramState {
        if (state.won) return state
        val tile = state.tiles.getOrNull(tileIndex) ?: return state
        if (tile.used) return state
        val tiles = state.tiles.mapIndexed { index, t -> if (index == tileIndex) t.copy(used = true) else t }
        return state.copy(tiles = tiles, typed = state.typed + tileIndex, message = "", events = listOf(Event.TYPE))
    }

    fun erase(state: AnagramState): AnagramState {
        if (state.typed.isEmpty()) return state
        val index = state.typed.last()
        val tiles = state.tiles.mapIndexed { i, t -> if (i == index) t.copy(used = false) else t }
        return state.copy(tiles = tiles, typed = state.typed.dropLast(1), message = "", events = listOf(Event.ERASE))
    }

    fun clear(state: AnagramState): AnagramState {
        if (state.typed.isEmpty()) return state
        return state.copy(tiles = state.tiles.map { it.copy(used = false) }, typed = emptyList(), message = "", events = listOf(Event.CLEAR))
    }

    fun reenter(state: AnagramState): AnagramState {
        val word = state.lastSubmitted
        if (word.isEmpty()) return state
        val cleared = state.copy(tiles = state.tiles.map { it.copy(used = false) }, typed = emptyList())
        var current = cleared
        for (ch in word) {
            val index = current.tiles.indexOfFirst { !it.used && it.ch.lowercaseChar() == ch }
            if (index < 0) break
            current = type(current, index)
        }
        return current.copy(events = listOf(Event.TYPE))
    }

    fun submit(state: AnagramState): AnagramState {
        if (state.won) return state
        val word = state.typedWord
        if (word.length < 3) {
            return state.copy(message = "words need three letters", events = listOf(Event.INVALID))
        }
        if (word in state.found) {
            return state.copy(message = "already found", events = listOf(Event.INVALID))
        }
        if (!isWord(state.animal, word)) {
            return state.copy(message = "not in this animal's list", events = listOf(Event.INVALID))
        }
        val found = state.found + word
        val events = if (word.length >= 6) listOf(Event.ACCEPTED, Event.LONG_WORD) else listOf(Event.ACCEPTED)
        val won = found.size >= state.available
        return state.copy(
            found = found,
            lastSubmitted = word,
            tiles = state.tiles.map { it.copy(used = false) },
            typed = emptyList(),
            message = "found $word",
            events = if (won) events + Event.WIN else events,
        )
    }

    fun hint(state: AnagramState): AnagramState {
        if (state.won || state.available == 0) {
            return state.copy(message = "all words found", hintLockMs = 1000L, events = listOf(Event.HINT_LOCKED))
        }
        val prefix = state.typedWord
        val unfound = wordsFor(state.animal).filterNot { it in state.found }
        if (unfound.isEmpty()) {
            return state.copy(message = "all words found", hintLockMs = 1000L, events = listOf(Event.HINT_LOCKED))
        }
        val aligned = if (prefix.isNotEmpty()) unfound.filter { it.startsWith(prefix) && it.length > prefix.length } else emptyList()
        val pool = if (aligned.isNotEmpty()) aligned else unfound
        val rng = AnagramRandom(state.rngSeed)
        val target = pool[rng.next(pool.size)]
        val reveal = target.take(prefix.length + 1)
        var tiles = state.tiles.map { it.copy(used = false) }
        val typed = ArrayList<Int>()
        for (ch in reveal) {
            val index = tiles.indexOfFirst { !it.used && it.ch.lowercaseChar() == ch }
            if (index < 0) break
            tiles = tiles.mapIndexed { i, t -> if (i == index) t.copy(used = true) else t }
            typed += index
        }
        return state.copy(
            tiles = tiles,
            typed = typed,
            hintLockMs = 1000L,
            message = "next letter: ${reveal.last().uppercaseChar()}",
            rngSeed = rng.state,
            events = listOf(Event.HINT),
        )
    }

    fun holdErase(state: AnagramState, dtMs: Long): AnagramState {
        if (state.typed.isEmpty()) return state.copy(eraseHoldMs = 0L)
        val held = state.eraseHoldMs + dtMs
        if (held >= 500L) {
            return clear(state).copy(eraseHoldMs = 0L)
        }
        return state.copy(eraseHoldMs = held)
    }

    fun step(state: AnagramState, dtMs: Long): AnagramState {
        var next = state
        if (state.hintLockMs > 0L) next = next.copy(hintLockMs = (state.hintLockMs - dtMs).coerceAtLeast(0L))
        if (state.eraseHoldMs > 0L && dtMs > 0L) {
            next = next.copy(eraseHoldMs = state.eraseHoldMs - dtMs).let {
                if (it.eraseHoldMs < 0L) it.copy(eraseHoldMs = 0L) else it
            }
        }
        return next.copy(events = emptyList())
    }

    fun isValidCandidate(state: AnagramState): Boolean {
        val word = state.typedWord
        return word.length >= 3 && word !in state.found && isWord(state.animal, word)
    }

    data class AnagramProgress(
        val found: Map<String, Set<String>> = emptyMap(),
        val sound: Boolean = true,
    ) {
        fun foundFor(animal: String): Set<String> = found[animal.uppercase()] ?: emptySet()
    }

    fun foundWords(progress: AnagramProgress, animal: String): Set<String> = progress.foundFor(animal)

    fun animalPercent(progress: AnagramProgress, animal: String): Int {
        val total = wordsFor(animal).size
        if (total == 0) return 0
        val found = foundWords(progress, animal).count { isWord(animal, it) }
        return found * 100 / total
    }

    fun habitatPercent(progress: AnagramProgress, habitat: Habitat): Int {
        val total = habitat.animals.sumOf { wordsFor(it).size }
        if (total == 0) return 0
        val found = habitat.animals.sumOf { animal ->
            foundWords(progress, animal).count { isWord(animal, it) }
        }
        return found * 100 / total
    }

    fun globalPercent(progress: AnagramProgress): Int {
        val total = ALL_ANIMALS.sumOf { wordsFor(it).size }
        if (total == 0) return 0
        val found = ALL_ANIMALS.sumOf { animal -> foundWords(progress, animal).count { isWord(animal, it) } }
        return found * 100 / total
    }

    fun hasStar(progress: AnagramProgress, habitat: Habitat): Boolean =
        habitat.animals.all { animal -> foundWords(progress, animal).count { isWord(animal, it) } >= wordsFor(animal).size }

    fun record(progress: AnagramProgress, animal: String, word: String): AnagramProgress {
        if (!isWord(animal, word)) return progress
        val key = animal.uppercase()
        return progress.copy(found = progress.found + (key to (progress.foundFor(key) + word.lowercase())))
    }

    fun reset(progress: AnagramProgress): AnagramProgress = progress.copy(found = emptyMap())

    fun encodeProgress(progress: AnagramProgress): String {
        val parts = ArrayList<String>()
        for (animal in ALL_ANIMALS) {
            val words = progress.foundFor(animal).filter { isWord(animal, it) }.sorted()
            if (words.isNotEmpty()) parts += "$animal:${words.joinToString(",")}"
        }
        return parts.joinToString("|")
    }

    fun decodeProgress(blob: String?): AnagramProgress {
        if (blob.isNullOrBlank()) return AnagramProgress()
        val found = HashMap<String, Set<String>>()
        for (entry in blob.split("|")) {
            val split = entry.split(":")
            if (split.size != 2) continue
            val animal = split[0].uppercase()
            if (animal !in WORD_BANK) continue
            val words = split[1].split(",").map { it.lowercase() }.filter { isWord(animal, it) }.toSet()
            if (words.isNotEmpty()) found[animal] = words
        }
        return AnagramProgress(found)
    }
}
