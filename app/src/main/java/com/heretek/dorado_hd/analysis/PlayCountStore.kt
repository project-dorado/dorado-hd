package com.heretek.dorado_hd.analysis

/**
 * Persisted per-track play counts (M9.2b). A pure seam so the DynamicMix
 * "Top Played" ordering and the playback recorder can be tested without Room.
 * Implementations must be safe to call from a background coroutine.
 */
interface PlayCountStore {
    /** Records one play of [mediaId] at [nowMillis] (defaults to now). */
    suspend fun increment(mediaId: Long, nowMillis: Long = System.currentTimeMillis())

    /** All known counts keyed by media id; absent ids are unplayed. */
    suspend fun counts(): Map<Long, Int>
}
