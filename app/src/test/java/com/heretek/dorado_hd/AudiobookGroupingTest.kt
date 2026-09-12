package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.scan.AudiobookGrouping
import com.heretek.dorado_hd.data.scan.AudiobookGrouping.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure audiobook grouping heuristics (roadmap D4): genre-flagged albums,
 * directory chapter detection, part ordering and title/author resolution.
 */
class AudiobookGroupingTest {

    private fun file(
        id: Long,
        title: String,
        path: String,
        artist: String = "Jane Author",
        album: String = "Book Name",
        albumId: Long = 7L,
        genre: String = "unknown",
        durationMs: Long = 600_000L,
    ) = File(
        mediaId = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        genre = genre,
        durationMs = durationMs,
        dateAdded = 0L,
        path = path,
        uri = "content://media/external/audio/media/$id",
    )

    @Test
    fun `genre flagged albums group by album id`() {
        val files = listOf(
            file(1, "Opening", "/Audio/story/a.mp3", genre = "Audiobook", albumId = 9),
            file(2, "The End", "/Audio/story/b.mp3", genre = "Audiobook", albumId = 9),
            file(3, "Single Track", "/Audio/song.mp3", genre = "Rock", albumId = 10),
        )

        val books = AudiobookGrouping.group(files)

        assertEquals(1, books.size)
        assertEquals("album:9", books.single().key)
        assertEquals(AudiobookGrouping.Source.ALBUM_GENRE, books.single().source)
        assertEquals(2, books.single().parts.size)
    }

    @Test
    fun `spoken word genre is recognized`() {
        val files = listOf(
            file(1, "one", "/Audio/b/1.mp3", genre = "Spoken Word", albumId = 3),
            file(2, "two", "/Audio/b/2.mp3", genre = "spoken-word", albumId = 3),
            file(3, "three", "/Audio/b/3.mp3", genre = "AUDIOBOOKS", albumId = 3),
        )

        assertEquals(1, AudiobookGrouping.group(files).size)
    }

    @Test
    fun `directory of numbered chapters groups and orders numerically`() {
        val files = listOf(
            file(10, "10 - The End", "/Books/Book Name/10 - The End.mp3"),
            file(2, "02 - Chapter Two", "/Books/Book Name/02 - Chapter Two.mp3"),
            file(1, "01 - Chapter One", "/Books/Book Name/01 - Chapter One.mp3"),
        )

        val book = AudiobookGrouping.group(files).single()

        assertEquals("dir:/Books/Book Name", book.key)
        assertEquals(AudiobookGrouping.Source.DIRECTORY, book.source)
        assertEquals(
            listOf("01 - Chapter One", "02 - Chapter Two", "10 - The End"),
            book.parts.map { it.title },
        )
        assertEquals(1_800_000L, book.totalDurationMs)
    }

    @Test
    fun `a lone chapter file does not become a book`() {
        val books = AudiobookGrouping.group(
            listOf(file(1, "01 - Chapter One", "/Books/Lonely/01 - Chapter One.mp3")),
        )
        assertTrue(books.isEmpty())
    }

    @Test
    fun `unnumbered files outside a bookish directory stay music`() {
        val files = listOf(
            file(1, "intro", "/Music/Some Album/intro.mp3"),
            file(2, "outro", "/Music/Some Album/outro.mp3"),
        )
        assertTrue(AudiobookGrouping.group(files).isEmpty())
    }

    @Test
    fun `bookish directory groups word numbered parts`() {
        val files = listOf(
            file(1, "Part One", "/Books/My Novel/Part One.mp3", album = "unknown album"),
            file(2, "Part Two", "/Books/My Novel/Part Two.mp3", album = "unknown album"),
        )

        val book = AudiobookGrouping.group(files).single()

        assertEquals("My Novel", book.title)
        assertEquals(listOf("Part One", "Part Two"), book.parts.map { it.title })
    }

    @Test
    fun `genre grouping wins over directory grouping`() {
        val files = listOf(
            file(1, "Chapter One", "/Books/Mixed/01.mp3", genre = "audiobook", albumId = 42),
            file(2, "Chapter Two", "/Books/Mixed/02.mp3"),
            file(3, "Chapter Three", "/Books/Mixed/03.mp3"),
        )

        val books = AudiobookGrouping.group(files)

        assertEquals(1, books.size)
        assertEquals("album:42", books.single().key)
    }

    @Test
    fun `chapter number understands words and rejects years`() {
        assertEquals(3, AudiobookGrouping.chapterNumber("Chapter Three"))
        assertEquals(12, AudiobookGrouping.chapterNumber("part 12 - the dig"))
        assertEquals(2, AudiobookGrouping.chapterNumber("02 - Chapter Two"))
        assertNull(AudiobookGrouping.chapterNumber("2001 - A Space Odyssey"))
        assertNull(AudiobookGrouping.chapterNumber("Untitled"))
    }

    @Test
    fun `author falls back to the directory prefix`() {
        val files = listOf(
            file(1, "Chapter One", "/Books/Robert Louis Stevenson - Treasure Island/01.mp3", artist = "unknown artist", album = "unknown album"),
            file(2, "Chapter Two", "/Books/Robert Louis Stevenson - Treasure Island/02.mp3", artist = "<unknown>", album = "unknown album"),
        )

        val book = AudiobookGrouping.group(files).single()

        assertEquals("Robert Louis Stevenson", book.author)
        assertEquals("Treasure Island", book.title)
    }

    @Test
    fun `title falls back to the directory name when album is unknown`() {
        val files = listOf(
            file(1, "Chapter One", "/Books/Sherlock Holmes/01.mp3", album = "unknown album"),
            file(2, "Chapter Two", "/Books/Sherlock Holmes/02.mp3", album = "unknown album"),
        )

        assertEquals("Sherlock Holmes", AudiobookGrouping.group(files).single().title)
    }

    @Test
    fun `books sort by title`() {
        val files = listOf(
            file(1, "Chapter One", "/Books/Zebra/01.mp3", album = "Zebra Book"),
            file(2, "Chapter Two", "/Books/Zebra/02.mp3", album = "Zebra Book"),
            file(3, "Chapter One", "/Books/Apple/01.mp3", album = "Apple Book"),
            file(4, "Chapter Two", "/Books/Apple/02.mp3", album = "Apple Book"),
        )

        assertEquals(
            listOf("Apple Book", "Zebra Book"),
            AudiobookGrouping.group(files).map { it.title },
        )
    }
}
