package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Zune Reader rules for the archived ebook library (docs/apps/zunereader.md).
 *
 * The OPDS catalog and the device's importer chain are gone. The three books
 * below are original clean-room text authored for Dorado-HD; there is no
 * third-party content here. Pagination, TOC parsing, bookmarks, the history
 * stack and the state codec are pure Kotlin so they can be unit-tested.
 */

data class ReaderBook(
    val id: String,
    val title: String,
    val author: String,
    val lines: List<String>,
)

data class TocEntry(
    val title: String,
    val level: Int,
    val blockIndex: Int,
)

data class ReaderDoc(
    val title: String,
    val author: String,
    val blocks: List<String>,
    val toc: List<TocEntry>,
)

data class Bookmark(
    val id: String,
    val blockIndex: Int,
    val label: String,
)

data class Pagination(
    val pages: List<String>,
    val startBlocks: List<Int>,
    val blockPage: List<Int>,
)

enum class ReaderTheme(val id: String, val label: String) {
    DARK("dark", "dark"),
    LIGHT("light", "light"),
    ;

    companion object {
        fun fromId(id: String?): ReaderTheme = entries.firstOrNull { it.id == id } ?: DARK
    }
}

data class ReaderState(
    val fontIndex: Int = 2,
    val theme: ReaderTheme = ReaderTheme.DARK,
    val lastBookId: String? = null,
    val positions: Map<String, Int> = emptyMap(),
    val bookmarks: Map<String, List<Bookmark>> = emptyMap(),
)

object ReaderModel {

    val FONT_STOPS: List<Int> = listOf(12, 14, 16, 18, 22, 26)

    const val HISTORY_CAP = 24

    /** Original clean-room sample texts, written for the Dorado-HD library. */
    val BOOKS: List<ReaderBook> = listOf(
        ReaderBook(
            id = "lantern-keeper",
            title = "the lantern keeper",
            author = "a. vance",
            lines = listOf(
                "## one — the harbor lamp",
                "every evening in the month of salt, the keeper climbed the ninety-nine steps to the harbor lamp and lit it before the first star showed. the town below arranged itself by that light: boats came in, shutters closed, and the baker set the morning dough to rise.",
                "the keeper had kept the lamp for thirty years and had never once been late. the ledger in the stairwell recorded each lighting in a small, steady hand, and the pages were as even as the tide tables.",
                "on the night the bell buoy drifted, the keeper saw a second light on the water — too low for a ship, too patient for a signal. it held its place while the fog came in, and then it began, very slowly, to climb the steps.",
                "## two — the ledger",
                "in the morning the keeper found a line in the ledger that was not in the keeper's hand. it read: the light was late by nine minutes. the keeper checked the clock, the wick, the oil, and found nothing wrong.",
                "that evening the keeper lit the lamp nine minutes early, and the strange light on the water did not appear at all. the town slept. the ledger stayed blank.",
                "it was the baker, carrying the first tray of the morning, who noticed the ledger open on the step and asked whether the keeper had begun writing a new chapter. the keeper said nothing, but from then on the lamp was lit at the same minute every evening, and the pages stayed even.",
                "## three — the quiet season",
                "when the salt month ended, the keeper took the ledger down to the harbormaster and asked for a second signature on each page. the harbormaster agreed, and for a while the arrangement held.",
                "the second light never returned. but on foggy nights the keeper would stand at the top of the steps a little longer than necessary, watching the water, listening to the bell that had drifted and been recovered and now sounded slightly out of tune.",
                "the ledger stayed open on the table. the keeper kept the pen beside it, in case the hand that was not the keeper's decided to write again.",
            ),
        ),
        ReaderBook(
            id = "tide-house",
            title = "notes from the tide house",
            author = "m. ellery",
            lines = listOf(
                "## the house",
                "the tide house stands on pilings, which is the polite way of saying it is a shed that has learned to float. its floor is cold in every month and its windows are small and placed for watching, not for light.",
                "i came here to catalogue the birds and stayed for the water. the water does not care what you planned.",
                "## what the tide leaves",
                "a tide line is a sentence written in rope, glass, seed pods and one shoe. i began keeping a list of the words: a green bottle, a toy boat with no sail, a length of orange line, a comb with three teeth.",
                "the list taught me that the sea edits. it keeps what is heavy and returns what is light, and it does not explain its reasons.",
                "## the neighbor",
                "the only other person on the point is a retired signalman who walks the beach at low water and says good morning to the gulls. he told me once that weather is only water being honest about itself.",
                "i wrote that down on the back of the tide table, where it still sits between the moon's phases.",
                "## a closing note",
                "by the end of the season the list had four hundred words and i had learned the difference between a thing that is lost and a thing that is only waiting. the tide house creaks at night, the way any patient building does, and in the morning the point is new.",
            ),
        ),
        ReaderBook(
            id = "small-machines",
            title = "a field guide to small machines",
            author = "t. arden",
            lines = listOf(
                "## how to look",
                "a small machine is any device that turns one kind of effort into another with fewer parts than you would guess. the can opener, the bicycle bell, the mousetrap: each is a sentence about leverage.",
                "the first rule of the field guide is to watch the machine do its work before you take it apart. the second rule is that you will take it apart anyway.",
                "## the bicycle bell",
                "inside the bell there is a strip of flexible metal, a small star wheel and a dome. pull the lever and the strip is lifted and released against the star, which spins the dome and strikes the shell.",
                "the whole mechanism is older than the bicycle. that is the secret of small machines: they are borrowed, improved and passed along.",
                "## the mousetrap",
                "the snap trap is a lesson in stored effort. the bar holds the energy, the latch holds the bar, and the trigger holds the latch. every part protects the next until one small movement lets all of them go.",
                "read the trap in the other direction and it becomes a diagram of a promise: energy, restraint, release.",
                "## the tin whistle",
                "a whistle has no moving parts, which makes it the hardest machine in this book to explain. the air splits on an edge, chooses a side, and the column of the tube decides how long the note lives.",
                "blow harder and the note climbs. blow softer and it fades. there is nothing to repair, only something to learn.",
                "## on repair",
                "a machine is never only its parts. it is also the hands that made it and the habit of using it. repair is how a machine stays in the conversation.",
            ),
        ),
    )

    fun books(): List<ReaderBook> = BOOKS

    fun bookById(id: String?): ReaderBook? = BOOKS.firstOrNull { it.id == id }

    /** Builds the paragraph/heading block list and the table of contents. */
    fun parseBook(book: ReaderBook): ReaderDoc {
        val blocks = mutableListOf<String>()
        val toc = mutableListOf<TocEntry>()
        book.lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val level = line.takeWhile { it == '#' }.length
            if (level > 0) {
                val title = line.drop(level).trim()
                if (title.isNotEmpty()) toc.add(TocEntry(title, level, blocks.size))
                blocks.add(title)
            } else {
                blocks.add(line)
            }
        }
        return ReaderDoc(book.title, book.author, blocks, toc)
    }

    /**
     * TOC fixture parser: `#`-prefixed lines become entries; blank lines are
     * ignored and paragraph lines advance the block index.
     */
    fun parseToc(lines: List<String>): List<TocEntry> {
        val toc = mutableListOf<TocEntry>()
        var blockIndex = 0
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val level = line.takeWhile { it == '#' }.length
            if (level > 0) {
                val title = line.drop(level).trim()
                if (title.isNotEmpty()) toc.add(TocEntry(title, level, blockIndex))
            }
            blockIndex++
        }
        return toc
    }

    /** Larger type means fewer characters per page. */
    fun charsPerPage(fontSp: Int, widthChars: Int = 420): Int =
        (widthChars.toFloat() * 14f / fontSp.coerceAtLeast(1)).toInt().coerceAtLeast(80)

    /**
     * Greedy word pagination. `blockPage[i]` is the page a block starts on so
     * TOC entries can jump; `startBlocks[p]` is the block that opens page p so
     * the reading position can be stored as a block index.
     */
    fun paginate(blocks: List<String>, charsPerPage: Int): Pagination {
        val budget = charsPerPage.coerceAtLeast(1)
        val pages = mutableListOf<String>()
        val pageStarts = mutableListOf<Int>()
        val blockPage = IntArray(blocks.size)
        val sb = StringBuilder()
        blocks.forEachIndexed { bi, block ->
            val words = block.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                blockPage[bi] = pages.size
                return@forEachIndexed
            }
            var first = true
            for (word in words) {
                val needed = if (sb.isEmpty()) word.length else sb.length + 1 + word.length
                if (sb.isNotEmpty() && needed > budget) {
                    pages.add(sb.toString())
                    sb.clear()
                }
                if (pageStarts.size == pages.size && sb.isEmpty()) pageStarts.add(bi)
                if (first) {
                    blockPage[bi] = pages.size
                    first = false
                }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(word)
            }
        }
        if (sb.isNotEmpty()) pages.add(sb.toString())
        if (pageStarts.size < pages.size) pageStarts.add(blocks.lastIndex.coerceAtLeast(0))
        return Pagination(pages, pageStarts, blockPage.toList())
    }

    fun pageForBlock(pagination: Pagination, blockIndex: Int): Int =
        pagination.blockPage.getOrElse(blockIndex) { 0 }.coerceIn(0, (pagination.pages.size - 1).coerceAtLeast(0))

    /** Preferred font stops; clamps at both ends. */
    fun nextFontStop(current: Int, up: Boolean, stops: List<Int> = FONT_STOPS): Int {
        if (stops.isEmpty()) return current
        val index = when (val exact = stops.indexOf(current)) {
            -1 -> stops.withIndex().minByOrNull { kotlin.math.abs(it.value - current) }?.index ?: 0
            else -> exact
        }
        val target = if (up) index + 1 else index - 1
        return stops[target.coerceIn(0, stops.lastIndex)]
    }

    fun pushHistory(stack: List<Int>, page: Int, cap: Int = HISTORY_CAP): List<Int> {
        val without = stack.filterNot { it == page }
        val next = without + page
        return if (next.size > cap) next.takeLast(cap) else next
    }

    fun popHistory(stack: List<Int>): List<Int> = if (stack.isEmpty()) stack else stack.dropLast(1)

    fun addBookmark(list: List<Bookmark>, bookmark: Bookmark): List<Bookmark> {
        val without = list.filterNot { it.blockIndex == bookmark.blockIndex || it.id == bookmark.id }
        return (without + bookmark).sortedBy { it.blockIndex }
    }

    fun removeBookmark(list: List<Bookmark>, id: String): List<Bookmark> =
        list.filterNot { it.id == id }

    fun progressPercent(blockIndex: Int, doc: ReaderDoc): Int {
        if (doc.blocks.isEmpty()) return 0
        return ((blockIndex.coerceIn(0, doc.blocks.size - 1) + 1) * 100 / doc.blocks.size)
    }
}

/**
 * `PrefMgr`-style compact state: one header line plus optional position and
 * bookmark lines. Labels are sanitized because the format is line-based.
 */
object ReaderStateCodec {

    fun encode(state: ReaderState): String = buildString {
        append("v1|")
        append(state.fontIndex).append('|')
        append(state.theme.id).append('|')
        append(state.lastBookId.orEmpty().replace('|', ' ')).append('\n')
        state.positions.forEach { (book, block) ->
            append("pos|").append(book.replace('|', ' ')).append('|').append(block).append('\n')
        }
        state.bookmarks.forEach { (book, marks) ->
            marks.forEach { mark ->
                append("mark|").append(book.replace('|', ' ')).append('|')
                    .append(mark.blockIndex).append('|')
                    .append(mark.label.replace('|', ' ').replace('\n', ' ')).append('\n')
            }
        }
    }

    fun decode(raw: String?): ReaderState? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.lines().map { it.trimEnd() }
        val header = lines.firstOrNull()?.split('|') ?: return null
        if (header.firstOrNull() != "v1") return null
        val fontIndex = header.getOrNull(1)?.toIntOrNull() ?: 2
        val theme = ReaderTheme.fromId(header.getOrNull(2))
        val lastBook = header.getOrNull(3)?.takeIf { it.isNotEmpty() }
        val positions = mutableMapOf<String, Int>()
        val bookmarks = mutableMapOf<String, MutableList<Bookmark>>()
        lines.drop(1).forEach { line ->
            val f = line.split('|')
            when (f.firstOrNull()) {
                "pos" -> if (f.size >= 3) {
                    val block = f[2].toIntOrNull() ?: return@forEach
                    positions[f[1]] = block
                }
                "mark" -> if (f.size >= 4) {
                    val block = f[2].toIntOrNull() ?: return@forEach
                    val label = f[3]
                    val list = bookmarks.getOrPut(f[1]) { mutableListOf() }
                    list.add(Bookmark("m$block", block, label))
                }
            }
        }
        return ReaderState(
            fontIndex = fontIndex.coerceIn(0, ReaderModel.FONT_STOPS.lastIndex),
            theme = theme,
            lastBookId = lastBook,
            positions = positions,
            bookmarks = bookmarks.mapValues { it.value.sortedBy { mark -> mark.blockIndex } },
        )
    }
}
