package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudSocialClient
import com.heretek.dorado_hd.data.repo.DoradoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CloudSocialClient] against a captured [FakeCloudHttp]. No
 * Android runtime is required, so they run under plain `testDebugUnitTest`.
 */
class CloudSocialClientTest {

    private val base = "https://cloud.example.invalid"

    private fun settings(enabled: Boolean) = DoradoSettings(
        cloudEnabled = enabled,
        cloudBaseUrl = base,
        cloudAccessToken = "tok",
    )

    @Test
    fun `disabled client is inert and makes no request`() = runBlocking {
        val http = FakeCloudHttp()
        val client = CloudSocialClient({ settings(false) }, { http })

        assertFalse(client.isEnabled())
        assertNull(client.feed())
        assertNull(client.myCard())
        assertNull(client.inbox())
        assertNull(http.lastPath)
    }

    @Test
    fun `feed hits me feed with bearer and parses activities`() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """[{"id":"a1","handle":"jane","kind":"listen","payloadJson":"{\"track\":\"x\"}","createdAt":"2026-01-02T00:00:00+00:00"}]""",
        )
        val client = CloudSocialClient({ settings(true) }, { http })

        val feed = client.feed(limit = 5)

        assertEquals("v1/social/me/feed?limit=5&offset=0", http.lastPath)
        assertEquals("Bearer tok", http.lastAuth)
        assertEquals("a1", feed!!.single().id)
        assertEquals("listen", feed.single().kind)
    }

    @Test
    fun `inbox hits the modern json route with bearer and parses messages`() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, INBOX_JSON)
        val client = CloudSocialClient({ settings(true) }, { http })

        val inbox = client.inbox(limit = 25)

        assertEquals("v1/social/me/inbox?limit=25", http.lastPath)
        assertEquals("Bearer tok", http.lastAuth)
        assertNotNull(inbox)
        assertEquals("jane", inbox!!.zuneTag)
        val message = inbox.messages.single()
        assertEquals("11111111-1111-1111-1111-111111111111", message.id)
        assertEquals("mira", message.senderTag)
        assertEquals("hello", message.subject)
        assertEquals("hi there", message.body)
        assertTrue(message.createdAt > 0)
    }

    @Test
    fun `unreachable inbox returns null so callers fall back to cache`() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(503, "unavailable")
        val client = CloudSocialClient({ settings(true) }, { http })

        assertNull(client.inbox())
    }

    @Test
    fun `mark read reports the remote cannot accept it`() = runBlocking {
        val http = FakeCloudHttp()
        val client = CloudSocialClient({ settings(true) }, { http })

        assertFalse(client.markRead("11111111-1111-1111-1111-111111111111"))
        assertNull(http.lastPath)
    }

    @Test
    fun `empty inbox is a successful empty list`() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, "[]")
        val client = CloudSocialClient({ settings(true) }, { http })

        val inbox = client.inbox()

        assertNotNull(inbox)
        assertTrue(inbox!!.messages.isEmpty())
        assertEquals("", inbox.zuneTag)
    }

    private companion object {
        val INBOX_JSON =
            """[{"id":"11111111-1111-1111-1111-111111111111","senderTag":"mira","recipientTag":"jane","subject":"hello","body":"hi there","createdAt":"2026-01-02T03:04:05+00:00"}]"""
    }
}
