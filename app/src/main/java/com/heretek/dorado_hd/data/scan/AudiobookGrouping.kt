package com.heretek.dorado_hd.data.scan

/**
 * Pure audiobook grouping heuristics (roadmap D4). The scanner feeds in every
 * long-enough audio file it can see; this object decides which of them form a
 * book and in what order the parts play.
 *
 * Two independent signals are honored:
 *  1. **Genre** — a MediaStore album whose genre contains `audiobook` or
 *     `spoken word` groups by `albumId` (explicit, so a single-file book is
 *     allowed).
 *  2. **Directory** — sibling files in one folder whose names look like
 *     chapters, e.g. `Book Name/01 - Chapter.mp3`. The folder must hold at
 *     least two chapter-like files; the part order is the chapter number.
 *
 * Everything here is Android-free so the heuristics are unit-testable.
 */
object AudiobookGrouping {

    /** The subset of a scanned audio file the heuristic needs. */
    data class File(
        val mediaId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val genre: String,
        val durationMs: Long,
        val dateAdded: Long,
        val path: String,
        val uri: String,
    ) {
        /** File name without its directory or extension. */
        val fileName: String
            get() = path.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
    }

    enum class Source { ALBUM_GENRE, DIRECTORY }

    data class Book(
        /** Stable identity across rescans (`album:<id>` or `dir:<path>`). */
        val key: String,
        val title: String,
        val author: String,
        val albumId: Long,
        val source: Source,
        val parts: List<File>,
    ) {
        val totalDurationMs: Long get() = parts.sumOf { it.durationMs }
    }

    // A leading part number: "01 - Chapter", "1. Introduction", "002".
    private val LEADING_NUMBER = Regex("""^0*(\d{1,3})(?!\d)""")
    // "Chapter 4", "Part 12", "Book 2", "Chapter One".
    private val NUMBER_WORD = Regex(
        """(?:chapter|part|book)\s*[-–—.:#]*\s*(\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)""",
        RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_WORD = Regex("""(?:^|[^a-z])(chapter|part|book)(?:[^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val BOOKISH_DIR = Regex(
        """(?:^|[^a-z])(audiobook|audio book|spoken word|unabridged|novel|book)(?:[^a-z]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val UNKNOWN_ARTISTS = setOf(
        "", "unknown artist", "unknown", "<unknown>", "various artists", "various artist",
    )

    private val WORD_NUMBERS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20,
    )

    /** True when a genre tag flags spoken-word/audiobook content. */
    fun isAudiobookGenre(genre: String): Boolean {
        val g = genre.lowercase()
        return "audiobook" in g || "audio book" in g || "spoken word" in g || "spoken-word" in g
    }

    /**
     * Leading chapter/part number of a title or file name, or null when the
     * name carries none. Used for ordering and for chapter-likeness.
     */
    fun chapterNumber(text: String): Int? {
        val trimmed = text.trim()
        LEADING_NUMBER.find(trimmed)?.let { return it.groupValues[1].toIntOrNull() }
        val word = NUMBER_WORD.find(trimmed) ?: return null
        val token = word.groupValues[1].lowercase()
        return token.toIntOrNull() ?: WORD_NUMBERS[token]
    }

    /** "01 - Chapter" / "Chapter One" / "part 3" — a book part, not a song. */
    fun isChapterLike(text: String): Boolean =
        chapterNumber(text) != null || CHAPTER_WORD.containsMatchIn(text)

    /** Directory portion of a path (null for a bare file name / root file). */
    fun parentDirectory(path: String): String? {
        val normalized = path.replace('\\', '/')
        val slash = normalized.lastIndexOf('/')
        return if (slash > 0) normalized.substring(0, slash) else null
    }

    fun directoryName(path: String): String? = parentDirectory(path)?.substringAfterLast('/')

    /**
     * Groups [files] into books. Genre-flagged albums win over directory
     * grouping; a file never lands in two books.
     */
    fun group(files: List<File>): List<Book> {
        val books = mutableListOf<Book>()
        val claimed = mutableSetOf<Long>()

        files.filter { isAudiobookGenre(it.genre) }
            .groupBy { it.albumId }
            .toSortedMap()
            .forEach { (albumId, members) ->
                val ordered = orderParts(members)
                books += Book(
                    key = "album:$albumId",
                    title = titleOf(ordered),
                    author = authorOf(ordered),
                    albumId = albumId,
                    source = Source.ALBUM_GENRE,
                    parts = ordered,
                )
                claimed += members.map { it.mediaId }
            }

        // A directory that already contributed to a genre-flagged book must not
        // spawn a second phantom book from its untagged siblings (track-level
        // genre tags are often missing on some files of the same book).
        val claimedDirectories = files
            .filter { it.mediaId in claimed }
            .mapNotNull { parentDirectory(it.path) }
            .toSet()

        files.asSequence()
            .filter { it.mediaId !in claimed && parentDirectory(it.path) !in claimedDirectories }
            .groupBy { parentDirectory(it.path) }
            .toSortedMap(compareBy { it ?: "" })
            .forEach { (directory, members0) ->
                if (directory == null || members0.size < 2) return@forEach
                val members = members0.sortedBy { it.path }
                val chapterLike = members.count { isChapterLike(it.title) || isChapterLike(it.fileName) }
                val bookishDirectory = BOOKISH_DIR.containsMatchIn(directoryName(members.first().path) ?: "")
                val allChapters = chapterLike == members.size
                if (chapterLike >= 2 && (allChapters || bookishDirectory)) {
                    val ordered = orderParts(members)
                    books += Book(
                        key = "dir:$directory",
                        title = titleOf(ordered),
                        author = authorOf(ordered),
                        albumId = ordered.first().albumId,
                        source = Source.DIRECTORY,
                        parts = ordered,
                    )
                }
            }

        return books.sortedBy { it.title.lowercase() }
    }

    /** Chapter number first, then title — stable and deterministic. */
    private fun orderParts(members: List<File>): List<File> =
        members.sortedWith(
            compareBy(
                { chapterNumber(it.title) ?: chapterNumber(it.fileName) ?: Int.MAX_VALUE },
                { it.title.lowercase() },
            ),
        )

    private fun titleOf(parts: List<File>): String {
        val album = parts.first().album.trim()
        if (album.isNotEmpty() && !album.equals("unknown album", ignoreCase = true)) return album
        val directory = directoryName(parts.first().path)
        if (!directory.isNullOrBlank()) {
            val tail = directory.substringAfterLast(" - ", directory).trim()
            return tail.ifEmpty { directory }
        }
        return parts.first().title
    }

    private fun authorOf(parts: List<File>): String {
        val named = parts.map { it.artist.trim() }.filter { it.lowercase() !in UNKNOWN_ARTISTS }
        val mostCommon = named.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .firstOrNull()?.key
        if (mostCommon != null && named.count { it == mostCommon } * 2 >= parts.size) return mostCommon
        val directory = directoryName(parts.first().path)
        if (directory != null && " - " in directory) {
            val prefix = directory.substringBefore(" - ").trim()
            if (prefix.isNotEmpty()) return prefix
        }
        return mostCommon ?: "unknown author"
    }
}
