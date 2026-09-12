package com.heretek.dorado_hd.media

/**
 * Pure queue + played-history model, recovered from the Zune HD playback
 * engine (`zmedia_serv.dll`: `CMediaQueueBase` / `CProgressivePlay` /
 * `CTrackListQueue`, the `AudioQueueCapacity` / `...HistoryCapacity` registry
 * values, and the `ZMediaQueue/MediaItem*` notifications).
 *
 * The engine owns the queue order, a bounded played-history stack, and the
 * capacity-eviction policy. It performs no playback and holds no Android
 * types, so it is unit-testable in isolation. [PlaybackController] mirrors
 * every mutation onto Media3.
 *
 * **Capacity semantics.** The seed list passed to [reset] is never truncated
 * (a whole album/playlist is queued at once); [capacity] bounds incremental
 * growth via [append] / [insertNext] / [insertAt]. When a mutation would
 * exceed capacity the engine evicts the oldest *already-played* entry, or the
 * tail when the current item is first, so the currently playing item is never
 * disturbed (unless the queue holds only it).
 *
 * **History semantics.** [recordPlayed] pushes ids onto a bounded stack with
 * browser-style branches: going [back] then playing something new discards the
 * abandoned forward branch. [back] / [forward] are pure cursor moves; the
 * controller decides restart-vs-back using the playback position.
 *
 * The device's capacity defaults are not recoverable from the reconstructed
 * binaries (only the registry key names survive), so the constants below are
 * documented, generous stand-ins.
 */
class QueueEngine<T>(
    val capacity: Int = DEFAULT_CAPACITY,
    val historyCapacity: Int = DEFAULT_HISTORY_CAPACITY,
    private val idOf: (T) -> Long,
) {
    init {
        require(capacity >= 1) { "capacity must be >= 1" }
        require(historyCapacity >= 1) { "historyCapacity must be >= 1" }
    }

    private val _items = ArrayList<T>()

    /** A snapshot of the current queue order. */
    val items: List<T> get() = _items.toList()

    /** Index of the playing item, or -1 when the queue is empty. */
    var currentIndex: Int = -1
        private set

    private val _history = ArrayList<Long>()

    /** Played item ids, oldest → newest. */
    val history: List<Long> get() = _history.toList()

    /** Cursor into [history]; -1 when nothing has been played. */
    var historyCursor: Int = -1
        private set

    val size: Int get() = _items.size
    val isEmpty: Boolean get() = _items.isEmpty()
    val isNotEmpty: Boolean get() = _items.isNotEmpty()

    /** Replaces the queue wholesale (a fresh `play()`) and clears history. */
    fun reset(newItems: List<T>, startIndex: Int) {
        _items.clear()
        _items.addAll(newItems)
        currentIndex = if (_items.isEmpty()) -1 else startIndex.coerceIn(0, _items.lastIndex)
        _history.clear()
        historyCursor = -1
    }

    fun setCurrentIndex(index: Int) {
        if (index in _items.indices) currentIndex = index
    }

    // ---- played history (device `AudioQueueHistoryCapacity`) ----

    /**
     * Records that [item] started playing. Re-recording the current history
     * entry (a replay) is a no-op; stepping onto the entry we already hold
     * (a `forward()`) advances the cursor instead of branching.
     */
    fun recordPlayed(item: T) {
        val id = idOf(item)
        if (historyCursor in _history.indices && _history[historyCursor] == id) return
        if (historyCursor + 1 <= _history.lastIndex && _history[historyCursor + 1] == id) {
            historyCursor++
            return
        }
        // A genuinely new play abandons any forward branch.
        while (_history.size > historyCursor + 1) _history.removeAt(_history.lastIndex)
        _history.add(id)
        if (_history.size > historyCapacity) {
            repeat(_history.size - historyCapacity) { _history.removeAt(0) }
        }
        historyCursor = _history.lastIndex
    }

    fun canGoBack(): Boolean = historyCursor > 0
    fun canGoForward(): Boolean = historyCursor in 0 until _history.lastIndex

    /** Steps back in history and returns the id, or null at the start. */
    fun back(): Long? {
        if (!canGoBack()) return null
        historyCursor--
        return _history[historyCursor]
    }

    /** Steps forward in history and returns the id, or null at the tip. */
    fun forward(): Long? {
        if (!canGoForward()) return null
        historyCursor++
        return _history[historyCursor]
    }

    // ---- queue mutation ----

    /** Appends [item], evicting an already-played entry first if at capacity. */
    fun append(item: T): QueueMutation<T> {
        val evictAt = evictionIndex()
        if (evictAt != null) removeInternal(evictAt)
        _items.add(item)
        return QueueMutation.Append(item, evictAt)
    }

    /** Inserts [item] immediately after the current item (the showlist action). */
    fun insertNext(item: T): QueueMutation<T> =
        insertAt(if (currentIndex < 0) _items.size else currentIndex + 1, item)

    fun insertAt(index: Int, item: T): QueueMutation<T> {
        if (_items.isEmpty()) {
            _items.add(item)
            currentIndex = 0
            return QueueMutation.Insert(0, item, null)
        }
        val rawAt = index.coerceIn(0, _items.size)
        val evictAt = evictionIndex()
        if (evictAt != null) removeInternal(evictAt)
        val at = (if (evictAt != null && evictAt < rawAt) rawAt - 1 else rawAt).coerceIn(0, _items.size)
        _items.add(at, item)
        if (at <= currentIndex) currentIndex++
        return QueueMutation.Insert(at, item, evictAt)
    }

    /** Moves the item at [from] to [to], following the current item. */
    fun move(from: Int, to: Int): QueueMutation<T> {
        if (from !in _items.indices || to !in _items.indices || from == to) return QueueMutation.None
        val currentId = _items.getOrNull(currentIndex)?.let(idOf)
        val item = _items.removeAt(from)
        _items.add(to, item)
        if (currentId != null) {
            val found = _items.indexOfFirst { idOf(it) == currentId }
            if (found >= 0) currentIndex = found
        }
        return QueueMutation.Move(from, to)
    }

    fun removeAt(index: Int): QueueMutation<T> {
        if (index !in _items.indices) return QueueMutation.None
        removeInternal(index)
        return QueueMutation.Remove(index)
    }

    /** Empties the queue and its history. */
    fun clear(): QueueMutation<T> {
        if (_items.isEmpty()) return QueueMutation.None
        _items.clear()
        currentIndex = -1
        _history.clear()
        historyCursor = -1
        return QueueMutation.Clear
    }

    // ---- internals ----

    private fun removeInternal(index: Int) {
        if (index !in _items.indices) return
        _items.removeAt(index)
        currentIndex = when {
            _items.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(_items.lastIndex)
            else -> currentIndex
        }
    }

    /**
     * Index to evict to make room, or null when there is room. Prefers the
     * oldest already-played entry; if the current item is first, evicts the
     * tail so playback is never interrupted.
     */
    private fun evictionIndex(): Int? {
        if (_items.size < capacity || _items.isEmpty()) return null
        return if (currentIndex > 0) 0 else _items.lastIndex
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val DEFAULT_HISTORY_CAPACITY = 100
    }
}

/**
 * What a queue mutation did, in terms Media3 can replay verbatim. [evictAt] is
 * an index in the *pre-mutation* list (remove it before [Append]/[Insert]).
 */
sealed interface QueueMutation<out T> {
    object None : QueueMutation<Nothing>

    data class Append<T>(val item: T, val evictAt: Int?) : QueueMutation<T>

    data class Insert<T>(val index: Int, val item: T, val evictAt: Int?) : QueueMutation<T>

    data class Move(val from: Int, val to: Int) : QueueMutation<Nothing>

    data class Remove(val index: Int) : QueueMutation<Nothing>

    object Clear : QueueMutation<Nothing>
}
