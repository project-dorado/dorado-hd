package com.heretek.dorado_hd.widget

/**
 * Snapshot of playback pushed to the home-screen Now Playing widget (M10).
 * Pure data so the mapping can be unit-tested without Android/Glance.
 */
data class NowPlayingWidgetState(
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val artUri: String?,
) {
    companion object {
        val Empty = NowPlayingWidgetState("", "", "", false, null)
    }
}

/** Maps controller output to a widget snapshot. */
object NowPlayingWidgetStateMapper {
    fun from(
        title: String?,
        artist: String?,
        album: String?,
        artUri: String?,
        isPlaying: Boolean,
    ): NowPlayingWidgetState = NowPlayingWidgetState(
        title = title.orEmpty(),
        artist = artist.orEmpty(),
        album = album.orEmpty(),
        isPlaying = isPlaying,
        artUri = artUri?.takeIf { it.isNotBlank() },
    )
}
