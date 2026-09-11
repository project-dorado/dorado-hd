package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.SortKeys
import org.junit.Assert.assertEquals
import org.junit.Test

/** M14 — device article-strip sort-key normalization. */
class SortKeysTest {

    @Test
    fun `leading articles are stripped`() {
        assertEquals("beatles", SortKeys.normalized("The Beatles"))
        assertEquals("apple", SortKeys.normalized("An Apple"))
        assertEquals("tree", SortKeys.normalized("a tree"))
    }

    @Test
    fun `non-article titles are lowercased and trimmed`() {
        assertEquals("zebra", SortKeys.normalized("  Zebra "))
        assertEquals("theatre", SortKeys.normalized("Theatre"))
    }
}
