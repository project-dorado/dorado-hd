package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.Bookmark
import com.heretek.dorado_hd.ui.apps.mocks.ReaderBook
import com.heretek.dorado_hd.ui.apps.mocks.ReaderModel
import com.heretek.dorado_hd.ui.apps.mocks.ReaderState
import com.heretek.dorado_hd.ui.apps.mocks.ReaderStateCodec
import com.heretek.dorado_hd.ui.apps.mocks.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 7 Zune Reader rules: TOC parsing, word pagination and its block map,
 * bookmark ordering, font-step clamping, the history stack and the codec.
 */
class ZuneReaderModelTest {

    @Test
    fun `toc parsing tracks levels and block indexes`() {
        val lines = listOf(
            "# one",
            "paragraph a",
            "## two",
            "",
            "paragraph b",
            "### three",
        )
        val toc = ReaderModel.parseToc(lines)
        assertEquals(3, toc.size)
        assertEquals("one", toc[0].title)
        assertEquals(1, toc[0].level)
        assertEquals(0, toc[0].blockIndex)
        assertEquals("two", toc[1].title)
        assertEquals(2, toc[1].level)
        assertEquals(2, toc[1].blockIndex)
        assertEquals("three", toc[2].title)
        assertEquals(3, toc[2].level)
        assertEquals(4, toc[2].blockIndex)
    }

    @Test
    fun `parse book keeps headings as blocks and toc targets`() {
        val book = ReaderBook(
            id = "fixture",
            title = "fixture book",
            author = "anon",
            lines = listOf("## first", "alpha beta", "## second", "gamma"),
        )
        val doc = ReaderModel.parseBook(book)
        assertEquals(4, doc.blocks.size)
        assertEquals(2, doc.toc.size)
        assertEquals(0, doc.toc[0].blockIndex)
        assertEquals(2, doc.toc[1].blockIndex)
    }

    @Test
    fun `pagination splits to budget and maps blocks to pages`() {
        val pagination = ReaderModel.paginate(listOf("one two three", "four five", "six"), 10)
        assertEquals(listOf("one two", "three four", "five six"), pagination.pages)
        assertEquals(listOf(0, 0, 1), pagination.startBlocks)
        assertEquals(listOf(0, 1, 2), pagination.blockPage)

        assertEquals(0, ReaderModel.pageForBlock(pagination, 0))
        assertEquals(1, ReaderModel.pageForBlock(pagination, 1))
        assertEquals(2, ReaderModel.pageForBlock(pagination, 2))
    }

    @Test
    fun `pagination handles a single tiny page`() {
        val pagination = ReaderModel.paginate(listOf("short"), 100)
        assertEquals(1, pagination.pages.size)
        assertEquals(listOf(0), pagination.startBlocks)
        assertEquals(listOf(0), pagination.blockPage)
        assertEquals("short", pagination.pages.first())
    }

    @Test
    fun `larger type means fewer characters per page`() {
        assertTrue(ReaderModel.charsPerPage(12) > ReaderModel.charsPerPage(26))
        assertEquals(ReaderModel.charsPerPage(14), ReaderModel.charsPerPage(14))
    }

    @Test
    fun `font steps clamp at both ends`() {
        val stops = ReaderModel.FONT_STOPS
        assertEquals(stops[1], ReaderModel.nextFontStop(stops[0], up = true))
        assertEquals(stops[0], ReaderModel.nextFontStop(stops[0], up = false))
        assertEquals(stops.last(), ReaderModel.nextFontStop(stops.last(), up = true))
        assertEquals(stops[stops.lastIndex - 1], ReaderModel.nextFontStop(stops.last(), up = false))
    }

    @Test
    fun `bookmarks insert in order and replace the same block`() {
        var list = emptyList<Bookmark>()
        list = ReaderModel.addBookmark(list, Bookmark("m5", 5, "page 2"))
        list = ReaderModel.addBookmark(list, Bookmark("m2", 2, "page 1"))
        list = ReaderModel.addBookmark(list, Bookmark("m8", 8, "page 3"))
        assertEquals(listOf(2, 5, 8), list.map { it.blockIndex })

        val replaced = ReaderModel.addBookmark(list, Bookmark("m5", 5, "renamed"))
        assertEquals(3, replaced.size)
        assertEquals("renamed", replaced.first { it.blockIndex == 5 }.label)

        val removed = ReaderModel.removeBookmark(replaced, "m2")
        assertEquals(listOf(5, 8), removed.map { it.blockIndex })
    }

    @Test
    fun `history stack is lifo and capped`() {
        var stack = emptyList<Int>()
        stack = ReaderModel.pushHistory(stack, 1)
        stack = ReaderModel.pushHistory(stack, 2)
        assertEquals(listOf(1, 2), stack)
        stack = ReaderModel.pushHistory(stack, 1)
        assertEquals("revisiting drops the older entry", listOf(2, 1), stack)

        var long = emptyList<Int>()
        repeat(30) { long = ReaderModel.pushHistory(long, it, cap = 24) }
        assertEquals(24, long.size)
        assertEquals(29, long.last())
        assertEquals(6, long.first())

        assertEquals(listOf(2), ReaderModel.popHistory(listOf(2, 1)))
        assertEquals(emptyList<Int>(), ReaderModel.popHistory(emptyList()))
    }

    @Test
    fun `reader state codec round trips`() {
        val state = ReaderState(
            fontIndex = 4,
            theme = ReaderTheme.LIGHT,
            lastBookId = "lantern-keeper",
            positions = mapOf("lantern-keeper" to 7, "tide-house" to 0),
            bookmarks = mapOf(
                "lantern-keeper" to listOf(
                    Bookmark("m2", 2, "page 1 · every evening in the"),
                    Bookmark("m7", 7, "page 3"),
                ),
            ),
        )
        assertEquals(state, ReaderStateCodec.decode(ReaderStateCodec.encode(state)))
        assertNull(ReaderStateCodec.decode(null))
        assertNull(ReaderStateCodec.decode("junk"))

        val clamped = ReaderStateCodec.decode("v1|99|light|book")!!
        assertTrue(clamped.fontIndex <= ReaderModel.FONT_STOPS.lastIndex)
    }

    @Test
    fun `sample library is original and well formed`() {
        assertEquals(3, ReaderModel.books().size)
        ReaderModel.books().forEach { book ->
            val doc = ReaderModel.parseBook(book)
            assertTrue("${book.id} needs paragraphs", doc.blocks.size >= 5)
            assertTrue("${book.id} needs a toc", doc.toc.isNotEmpty())
        }
    }
}
