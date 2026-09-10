package com.heretek.dorado_hd.scrobble

/** Persistence boundary for the offline scrobble queue (Room-backed in app). */
interface ScrobbleStore {
    suspend fun enqueue(scrobble: Scrobble)
    suspend fun pending(limit: Int): List<PendingScrobble>
    suspend fun delete(ids: List<Long>)
    suspend fun count(): Int
}
