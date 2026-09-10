package com.heretek.dorado_hd.scrobble

/** One scrobble payload (Last.fm `track.scrobble` fields). */
data class Scrobble(
    val artist: String,
    val title: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val timestampSec: Long,
)

/** A queued scrobble with its store id. */
data class PendingScrobble(val id: Long, val scrobble: Scrobble)

/** Sends a single scrobble; returns false when it should stay queued. */
fun interface ScrobbleSink {
    suspend fun scrobble(scrobble: Scrobble): Boolean
}
