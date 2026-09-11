package com.heretek.dorado_hd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.RoomPlayCountStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayCountTest {

    @Test
    fun `room play counts increment and are independent per track`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = RoomPlayCountStore(db.playCountDao())

            assertEquals(emptyMap<Long, Int>(), store.counts())

            store.increment(11, nowMillis = 1_000)
            store.increment(11, nowMillis = 2_000)
            store.increment(22, nowMillis = 3_000)

            assertEquals(mapOf(11L to 2, 22L to 1), store.counts())
        } finally {
            db.close()
        }
    }

    @Test
    fun `increment records the supplied timestamp`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.playCountDao()
            dao.increment(7, now = 42_000)
            val rows = dao.all()
            assertEquals(1, rows.size)
            assertEquals(42_000L, rows.first().lastPlayedAt)
            assertEquals(1, rows.first().count)
        } finally {
            db.close()
        }
    }
}
