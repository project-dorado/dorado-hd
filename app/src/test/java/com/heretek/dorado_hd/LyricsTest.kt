package com.heretek.dorado_hd

import com.heretek.dorado_hd.net.LrcLibParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsTest {

    @Test
    fun `parses plain and synced lyrics`() {
        val json = """{"plainLyrics":"hello\nworld","syncedLyrics":"[00:01.00]hello\n[00:02.00]world"}"""
        val lyrics = LrcLibParser.parse(json)!!
        assertEquals("hello\nworld", lyrics.plain)
        assertTrue(lyrics.synced!!.contains("hello"))
    }

    @Test
    fun `returns null when both lyric fields are empty or malformed`() {
        assertNull(LrcLibParser.parse("""{"plainLyrics":"","syncedLyrics":"null"}"""))
        assertNull(LrcLibParser.parse("not json at all"))
        assertNull(LrcLibParser.parse("{}"))
    }

    @Test
    fun `plain from synced strips LRC timestamps`() {
        val synced = "[00:01.00]first line\n[00:12.50]second line\n[00:20.00]"
        assertEquals("first line\nsecond line", LrcLibParser.plainFromSynced(synced))
    }
}
