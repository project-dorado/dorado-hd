package com.heretek.dorado_hd.media

/**
 * In-memory playback state events, analogues of the device's
 * `ZMediaQueue/MediaItemPositioned|Stopped|Paused|Playing` notifications
 * (`zmedia_serv.dll`). They are delivered as one [kotlinx.coroutines.flow.StateFlow]
 * of the most recent event; consumers reconstruct the stream themselves. No
 * event is persisted.
 */
sealed interface PlaybackEvent {
    /** The item the event describes. */
    val mediaId: Long

    /** Device `MediaItemPlaying`. */
    data class Playing(override val mediaId: Long) : PlaybackEvent

    /** Device `MediaItemPaused`. */
    data class Paused(override val mediaId: Long) : PlaybackEvent

    /** Device `MediaItemStopped` — ended, stopped, or the queue was cleared. */
    data class Stopped(override val mediaId: Long) : PlaybackEvent

    /** Device `MediaItemPositioned` — periodic position report. */
    data class Positioned(
        override val mediaId: Long,
        val positionMs: Long,
        val durationMs: Long,
    ) : PlaybackEvent
}
