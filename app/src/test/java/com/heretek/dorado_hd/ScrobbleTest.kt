package com.heretek.dorado_hd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.RoomScrobbleStore
import com.heretek.dorado_hd.scrobble.LastFmSignature
import com.heretek.dorado_hd.scrobble.PendingScrobble
import com.heretek.dorado_hd.scrobble.Scrobble
import com.heretek.dorado_hd.scrobble.ScrobbleService
import com.heretek.dorado_hd.scrobble.ScrobbleSink
import com.heretek.dorado_hd.scrobble.ScrobbleStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrobbleTest {

    private class FakeStore : ScrobbleStore {
        val rows = mutableListOf<PendingScrobble>()
        private var nextId = 1L

        override suspend fun enqueue(scrobble: Scrobble) {
            rows += PendingScrobble(nextId++, scrobble)
        }

        override suspend fun pending(limit: Int): List<PendingScrobble> = rows.take(limit)

        override suspend fun delete(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }

        override suspend fun count(): Int = rows.size
    }

    @Test
    fun `md5 matches a known vector`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", LastFmSignature.md5Hex("abc"))
    }

    @Test
    fun `signature is order independent and excludes format`() {
        val a = LastFmSignature.apiSignature(mapOf("b" to "2", "a" to "1", "format" to "json"), "secret")
        val b = LastFmSignature.apiSignature(mapOf("a" to "1", "b" to "2"), "secret")
        assertEquals(a, b)
        assertNotEquals(a, LastFmSignature.apiSignature(mapOf("a" to "1", "b" to "2"), "other-secret"))
    }

    @Test
    fun `record queues when enabled and rejects unusable fields`() = runBlocking {
        val store = FakeStore()
        var enabled = true
        val service = ScrobbleService(store, ScrobbleSink { true }, enabled = { enabled }, nowSec = { 1_000L })

        assertTrue(service.record("A", "T", "Al", 200))
        assertEquals(1, store.count())

        assertFalse(service.record("", "T", "Al", 200))
        assertFalse(service.record("A", "  ", "Al", 200))

        enabled = false
        assertFalse(service.record("A", "T2", "Al", 200))
        assertEquals(1, store.count())
    }

    @Test
    fun `flush preserves order and stops at the first failure`() = runBlocking {
        val store = FakeStore()
        val sent = mutableListOf<String>()
        var accept = true
        val sink = ScrobbleSink { s -> if (accept) { sent += s.title; true } else false }
        val service = ScrobbleService(store, sink, enabled = { true }, nowSec = { 1_000L })

        service.record("A", "one", "", 1)
        service.record("A", "two", "", 1)
        service.record("A", "three", "", 1)

        accept = false
        assertEquals(0, service.flush())
        assertEquals(3, store.count())

        accept = true
        assertEquals(3, service.flush())
        assertEquals(0, store.count())
        assertEquals(listOf("one", "two", "three"), sent)
    }

    @Test
    fun `room scrobble store round-trips`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = RoomScrobbleStore(db.scrobbleDao())
            store.enqueue(Scrobble("A", "T", "Al", 200, 1234))
            val pending = store.pending(10)
            assertEquals(1, pending.size)
            assertEquals("T", pending.first().scrobble.title)
            assertEquals(1234L, pending.first().scrobble.timestampSec)
            store.delete(listOf(pending.first().id))
            assertEquals(0, store.count())
        } finally {
            db.close()
        }
    }
}
