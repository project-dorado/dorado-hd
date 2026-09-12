package com.heretek.dorado_hd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.cloud.CloudSocialClient
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.data.repo.InboxRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Local-first inbox semantics (D2): Room is the source of truth, a cloud sync
 * merges rows while preserving on-device read state, and a disabled/unreachable
 * cloud degrades to the cache with an explicit offline label.
 */
@RunWith(RobolectricTestRunner::class)
class InboxRepositoryTest {

    private lateinit var db: DoradoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun repository(http: FakeCloudHttp, enabled: Boolean): InboxRepository =
        InboxRepository(
            db,
            CloudSocialClient(
                settings = {
                    DoradoSettings(
                        cloudEnabled = enabled,
                        cloudBaseUrl = "https://cloud.example.invalid",
                        cloudAccessToken = "tok",
                    )
                },
                httpFactory = { http },
            ),
        )

    @Test
    fun `sync upserts cloud messages and unread count`() = runBlocking {
        val http = FakeCloudHttp()
        enqueueInbox(http, ATOM_ONE)
        val repo = repository(http, enabled = true)

        val result = repo.sync()

        assertTrue(result.synced)
        val message = repo.messages().first().single()
        assertEquals("hello", message.subject)
        assertEquals("mira", message.senderTag)
        assertFalse(message.isRead)
        assertEquals(1, repo.unreadCount().first())
    }

    @Test
    fun `sync preserves local read state across a refresh`() = runBlocking {
        val http = FakeCloudHttp()
        enqueueInbox(http, ATOM_ONE)
        enqueueInbox(http, ATOM_ONE)
        val repo = repository(http, enabled = true)

        repo.sync()
        val id = repo.messages().first().single().id
        repo.markRead(id)

        repo.sync()

        assertTrue(repo.message(id)!!.isRead)
        assertEquals(0, repo.unreadCount().first())
    }

    @Test
    fun `disabled cloud reports the offline label without a request`() = runBlocking {
        val http = FakeCloudHttp()
        val repo = repository(http, enabled = false)

        val result = repo.sync()

        assertFalse(result.synced)
        assertEquals(InboxRepository.OFFLINE_DISABLED, result.label)
        assertTrue(repo.messages().first().isEmpty())
        assertEquals(null, http.lastPath)
    }

    @Test
    fun `unreachable cloud keeps the cache and labels offline`() = runBlocking {
        val http = FakeCloudHttp()
        enqueueInbox(http, ATOM_ONE)
        val repo = repository(http, enabled = true)

        repo.sync()
        enqueueProfile(http)
        http.enqueue(503, "unavailable")
        val result = repo.sync()

        assertFalse(result.synced)
        assertEquals(InboxRepository.OFFLINE_UNREACHABLE, result.label)
        assertEquals(1, repo.messages().first().size)
    }

    private fun enqueueInbox(http: FakeCloudHttp, atom: String) {
        enqueueProfile(http)
        http.enqueue(200, atom)
    }

    private fun enqueueProfile(http: FakeCloudHttp) {
        http.enqueue(
            200,
            """{"accountId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","handle":"jane","displayName":"Jane","bio":"","followers":0,"following":0,"activities":0,"isFollowing":false,"isBlocked":false,"createdAt":"2026-01-01T00:00:00+00:00"}""",
        )
    }

    private companion object {
        val ATOM_ONE = """
            <?xml version="1.0" encoding="utf-8"?>
            <a:feed xmlns:a="http://www.w3.org/2005/Atom" xmlns="http://schemas.zune.net/social/2007/10">
              <a:entry>
                <a:title type="text">hello</a:title>
                <a:id>11111111-1111-1111-1111-111111111111</a:id>
                <sender>mira</sender>
                <subject>hello</subject>
                <body>hi there</body>
                <receivedAt>2026-01-02T03:04:05+00:00</receivedAt>
              </a:entry>
            </a:feed>
        """.trimIndent()
    }
}
