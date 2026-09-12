package com.heretek.dorado_hd

import com.heretek.dorado_hd.media.QueueEngine
import com.heretek.dorado_hd.media.QueueMutation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track C — deep queue engine (`CMediaQueueBase`/`CTrackListQueue`). */
class QueueEngineTest {

    private fun engine(capacity: Int = 5, historyCapacity: Int = 10) =
        QueueEngine<Int>(capacity = capacity, historyCapacity = historyCapacity) { it.toLong() }

    @Test
    fun `append evicts the oldest played item at capacity`() {
        val q = engine(capacity = 4)
        q.reset(listOf(1, 2, 3, 4), startIndex = 2)

        val mutation = q.append(5)

        assertEquals(QueueMutation.Append(5, 0), mutation)
        assertEquals(listOf(2, 3, 4, 5), q.items)
        assertEquals(1, q.currentIndex)
        assertEquals(3, q.items[q.currentIndex])
    }

    @Test
    fun `append at capacity with current first evicts the tail`() {
        val q = engine(capacity = 2)
        q.reset(listOf(1, 2), startIndex = 0)

        q.append(3)

        assertEquals(listOf(1, 3), q.items)
        assertEquals(0, q.currentIndex)
        assertEquals(1, q.items[q.currentIndex])
    }

    @Test
    fun `insert next places the item after the current one`() {
        val q = engine(capacity = 5)
        q.reset(listOf(1, 2, 3), startIndex = 1)

        val mutation = q.insertNext(9)

        assertEquals(QueueMutation.Insert(2, 9, null), mutation)
        assertEquals(listOf(1, 2, 9, 3), q.items)
        assertEquals(1, q.currentIndex)
    }

    @Test
    fun `insert next evicts an already-played item at capacity`() {
        val q = engine(capacity = 4)
        q.reset(listOf(1, 2, 3, 4), startIndex = 2)

        val mutation = q.insertNext(9)

        assertEquals(QueueMutation.Insert(2, 9, 0), mutation)
        assertEquals(listOf(2, 3, 9, 4), q.items)
        assertEquals(1, q.currentIndex)
        assertEquals(3, q.items[q.currentIndex])
    }

    @Test
    fun `move follows the current item and rejects bad bounds`() {
        val q = engine()
        q.reset(listOf(1, 2, 3, 4), startIndex = 1)

        val mutation = q.move(0, 2)

        assertEquals(QueueMutation.Move(0, 2), mutation)
        assertEquals(listOf(2, 3, 1, 4), q.items)
        assertEquals(0, q.currentIndex)
        assertEquals(2, q.items[q.currentIndex])

        assertEquals(QueueMutation.None, q.move(0, 0))
        assertEquals(QueueMutation.None, q.move(-1, 1))
        assertEquals(QueueMutation.None, q.move(0, 9))
    }

    @Test
    fun `remove at keeps a valid current index`() {
        val q = engine()
        q.reset(listOf(1, 2, 3, 4), startIndex = 2)

        q.removeAt(0)
        assertEquals(listOf(2, 3, 4), q.items)
        assertEquals(1, q.currentIndex)
        assertEquals(3, q.items[q.currentIndex])

        q.removeAt(q.currentIndex)
        assertEquals(listOf(2, 4), q.items)
        assertEquals(1, q.currentIndex)
        assertEquals(4, q.items[q.currentIndex])
    }

    @Test
    fun `clear empties queue and history`() {
        val q = engine()
        q.reset(listOf(1, 2, 3), startIndex = 1)
        q.recordPlayed(1)
        q.recordPlayed(2)

        assertEquals(QueueMutation.Clear, q.clear())
        assertTrue(q.isEmpty)
        assertEquals(-1, q.currentIndex)
        assertTrue(q.history.isEmpty())
        assertEquals(QueueMutation.None, q.clear())
    }

    @Test
    fun `reset clamps the start index and clears history`() {
        val q = engine()
        q.recordPlayed(1)

        q.reset(listOf(1, 2, 3), startIndex = 99)

        assertEquals(2, q.currentIndex)
        assertTrue(q.history.isEmpty())
    }

    @Test
    fun `history steps back and forward`() {
        val q = engine()
        q.recordPlayed(1)
        q.recordPlayed(2)
        q.recordPlayed(3)

        assertEquals(listOf(1L, 2L, 3L), q.history)
        assertTrue(q.canGoBack())
        assertFalse(q.canGoForward())

        assertEquals(2L, q.back())
        assertEquals(1L, q.back())
        assertNull(q.back())
        assertEquals(2L, q.forward())
        assertEquals(3L, q.forward())
        assertNull(q.forward())
    }

    @Test
    fun `recording after going back discards the forward branch`() {
        val q = engine()
        q.recordPlayed(1)
        q.recordPlayed(2)
        q.recordPlayed(3)

        assertEquals(2L, q.back())
        assertEquals(1L, q.back())
        q.recordPlayed(9)

        assertEquals(listOf(1L, 9L), q.history)
        assertEquals(1, q.historyCursor)
        assertFalse(q.canGoForward())
    }

    @Test
    fun `consecutive replays do not duplicate history`() {
        val q = engine()
        q.recordPlayed(1)
        q.recordPlayed(1)
        q.recordPlayed(2)

        assertEquals(listOf(1L, 2L), q.history)
    }

    @Test
    fun `history is bounded`() {
        val q = engine(historyCapacity = 3)
        for (i in 1..4) q.recordPlayed(i)

        assertEquals(listOf(2L, 3L, 4L), q.history)
    }
}
