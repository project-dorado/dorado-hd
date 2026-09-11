package com.heretek.dorado_hd

import com.heretek.dorado_hd.widget.NowPlayingWidgetStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingWidgetStateMapperTest {

    @Test
    fun `null fields map to empty strings`() {
        val state = NowPlayingWidgetStateMapper.from(null, null, null, null, isPlaying = false)

        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertEquals("", state.album)
        assertFalse(state.isPlaying)
        assertNull(state.artUri)
    }

    @Test
    fun `blank art uri is dropped`() {
        val state = NowPlayingWidgetStateMapper.from("T", "A", "Al", "   ", isPlaying = true)

        assertNull(state.artUri)
    }

    @Test
    fun `values and playing flag are preserved`() {
        val state = NowPlayingWidgetStateMapper.from("T", "A", "Al", "content://art/1", isPlaying = true)

        assertEquals("T", state.title)
        assertEquals("A", state.artist)
        assertEquals("Al", state.album)
        assertEquals("content://art/1", state.artUri)
        assertEquals(true, state.isPlaying)
    }
}
