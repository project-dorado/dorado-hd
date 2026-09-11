package com.heretek.dorado_hd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.AppStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStateStoreTest {

    @Test
    fun `app state round-trips, overwrites and clears`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = AppStateRepository(db)

            assertNull(store.get("solitaire"))
            store.put("solitaire", "deal=123")
            assertEquals("deal=123", store.get("solitaire"))
            assertEquals("deal=123", store.observe("solitaire").first())
            assertTrue(store.get("solitaire")!!.isNotEmpty())

            store.put("solitaire", "deal=456")
            assertEquals("deal=456", store.get("solitaire"))

            store.put("chess", "moves=e2e4")
            assertEquals("deal=456", store.get("solitaire"))
            assertEquals("moves=e2e4", store.get("chess"))

            store.clear("solitaire")
            assertNull(store.get("solitaire"))
            assertEquals("moves=e2e4", store.get("chess"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `state persists across a reopened database file`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "app-state-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, DoradoDatabase::class.java, name).build()
            AppStateRepository(first).put("notesdraft", "hello")
            first.close()

            val second = Room.databaseBuilder(context, DoradoDatabase::class.java, name).build()
            assertEquals("hello", AppStateRepository(second).get("notesdraft"))
            second.close()
        } finally {
            context.deleteDatabase(name)
        }
    }
}
