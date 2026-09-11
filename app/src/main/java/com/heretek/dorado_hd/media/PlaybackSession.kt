package com.heretek.dorado_hd.media

/**
 * Shared holder for the playback audio session id. The ExoPlayer lives inside
 * [DoradoPlaybackService]; this exposes its session id so app-side audio effects
 * (`EqualizerController`) can attach. `-1` until the session is created.
 */
object PlaybackSession {
    @Volatile
    var audioSessionId: Int = -1
}
