package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.db.RadioStationEntity
import com.heretek.dorado_hd.media.PlaybackSource
import com.heretek.dorado_hd.ui.screens.radioNowPlayingMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B4 radio Now Playing: the presentation is selected by the explicit
 * [PlaybackSource] that `Radio.kt` sets, not by guessing from track fields.
 */
class RadioNowPlayingTest {

    private fun station(id: Long, name: String, freqKhz: Int) = RadioStationEntity(
        id = id,
        name = name,
        frequencyKhz = freqKhz,
        streamUrl = "https://example.test/stream",
        isPreset = false,
        lastPlayedAt = 0L,
    )

    @Test
    fun `library playback is not radio`() {
        assertNull(
            radioNowPlayingMeta(
                PlaybackSource.LIBRARY,
                "a song",
                1L,
                listOf(station(1L, "fm one", 98_700)),
            ),
        )
    }

    @Test
    fun `radio requires a current track`() {
        assertNull(radioNowPlayingMeta(PlaybackSource.RADIO, null, null, emptyList()))
    }

    @Test
    fun `radio shows station name, dial and live tag`() {
        val meta = radioNowPlayingMeta(
            PlaybackSource.RADIO,
            "fallback name",
            7L,
            listOf(station(7L, "the zone", 98_700)),
        )
        assertEquals("the zone", meta?.stationName)
        assertEquals("98.7 MHz", meta?.dialLabel)
        assertEquals("98.7 MHz", meta?.dialLine)
        assertEquals("live", meta?.live)
    }

    @Test
    fun `stream-only station shows stream instead of a dial`() {
        val meta = radioNowPlayingMeta(
            PlaybackSource.RADIO,
            "web radio",
            9L,
            listOf(station(9L, "web radio", 0)),
        )
        assertNull(meta?.dialLabel)
        assertEquals("stream", meta?.dialLine)
    }

    @Test
    fun `unknown station falls back to the track title`() {
        val meta = radioNowPlayingMeta(PlaybackSource.RADIO, "pinned stream", 42L, emptyList())
        assertEquals("pinned stream", meta?.stationName)
        assertNull(meta?.dialLabel)
    }
}
