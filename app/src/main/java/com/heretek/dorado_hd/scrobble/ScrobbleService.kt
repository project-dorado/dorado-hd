package com.heretek.dorado_hd.scrobble

/**
 * Offline-first scrobbling: records plays into a durable queue and flushes
 * them in order when a sink is reachable. Sending stops at the first failure so
 * ordering is preserved. Pure JVM — the [ScrobbleSink] and [ScrobbleStore] are
 * injected, so the queue semantics are unit-tested.
 */
class ScrobbleService(
    private val store: ScrobbleStore,
    private val sink: ScrobbleSink,
    private val enabled: suspend () -> Boolean,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
    /**
     * Best-effort mirror of each completed play to the Dorado Cloud social feed
     * (the live cross-device Zune Card). Independent of the Last.fm gate: it
     * fires whenever the cloud is enabled, and never blocks or fails a play.
     */
    private val cloudListen: (suspend (String, String, String) -> Unit)? = null,
) {

    /** Queues a play if scrobbling is on and the fields are usable. */
    suspend fun record(artist: String, title: String, album: String, durationSeconds: Int): Boolean {
        if (artist.isBlank() || title.isBlank()) return false

        val listener = cloudListen
        if (listener != null) {
            runCatching { listener.invoke(artist, title, album) }
        }

        if (!enabled()) return false
        store.enqueue(Scrobble(artist, title, album, durationSeconds, nowSec()))
        return true
    }

    /** Sends up to [limit] queued scrobbles; returns how many were accepted. */
    suspend fun flush(limit: Int = 50): Int {
        if (!enabled()) return 0
        var sent = 0
        val done = mutableListOf<Long>()
        for (pending in store.pending(limit)) {
            val ok = runCatching { sink.scrobble(pending.scrobble) }.getOrDefault(false)
            if (!ok) break
            done += pending.id
            sent++
        }
        if (done.isNotEmpty()) store.delete(done)
        return sent
    }

    suspend fun pendingCount(): Int = store.count()
}
