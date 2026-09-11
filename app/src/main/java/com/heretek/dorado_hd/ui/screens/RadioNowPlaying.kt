package com.heretek.dorado_hd.ui.screens

import com.heretek.dorado_hd.data.db.RadioStationEntity
import com.heretek.dorado_hd.media.PlaybackSource

/**
 * The dedicated radio Now Playing presentation (device
 * `GemNowPlayingRadioScene`): station identity and a live tag instead of a
 * seek position. Queue, heart rating, shuffle/repeat and lyrics are track-only
 * chrome and stay hidden.
 */
data class RadioNowPlayingMeta(
    val stationName: String,
    /** "98.7 MHz" when the station carries a dial frequency; null when not. */
    val dialLabel: String?,
    val live: String = "live",
) {
    /** Dial line shown in the radio presentation ("stream" when dial-less). */
    val dialLine: String get() = dialLabel ?: "stream"
}

/**
 * Resolves the radio presentation for [source]; null means "not radio".
 *
 * Detection is explicit: `Radio.kt` marks its queue [PlaybackSource.RADIO]
 * when it starts a stream (see `PlaybackController.play`), so the unified
 * Now Playing never has to guess from the track's URI or dummy album fields.
 * Stations are looked up by `mediaId` to recover the dial frequency, which the
 * device showed alongside the station name.
 */
fun radioNowPlayingMeta(
    source: PlaybackSource,
    currentTitle: String?,
    currentMediaId: Long?,
    stations: List<RadioStationEntity>,
): RadioNowPlayingMeta? {
    if (source != PlaybackSource.RADIO || currentTitle == null) return null
    val station = currentMediaId?.let { id -> stations.firstOrNull { it.id == id } }
    return RadioNowPlayingMeta(
        stationName = station?.name ?: currentTitle,
        dialLabel = station?.frequencyKhz
            ?.takeIf { it > 0 }
            ?.let { formatDial(it) },
    )
}
