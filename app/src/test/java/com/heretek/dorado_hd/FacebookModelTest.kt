package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.FB_MAX_BODY
import com.heretek.dorado_hd.ui.apps.mocks.FbCodec
import com.heretek.dorado_hd.ui.apps.mocks.FbCompose
import com.heretek.dorado_hd.ui.apps.mocks.FbFolder
import com.heretek.dorado_hd.ui.apps.mocks.FbMail
import com.heretek.dorado_hd.ui.apps.mocks.FbPost
import com.heretek.dorado_hd.ui.apps.mocks.FbTokenKind
import com.heretek.dorado_hd.ui.apps.mocks.FacebookSeed
import com.heretek.dorado_hd.ui.apps.mocks.FeedCache
import com.heretek.dorado_hd.ui.apps.mocks.splitFbLinks
import com.heretek.dorado_hd.ui.apps.mocks.validateFbCompose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookModelTest {

    private fun post(id: Long): FbPost =
        FbPost(id = id, author = "a$id", age = "now", message = "post $id", likes = 0, comments = 0)

    @Test
    fun `compose requires recipient subject and body`() {
        assertFalse(validateFbCompose(emptyList(), "s", "b").ok)
        assertFalse(validateFbCompose(listOf("alex"), "", "b").ok)
        assertFalse(validateFbCompose(listOf("alex"), "s", "  ").ok)
        assertTrue(validateFbCompose(listOf("alex"), "s", "b").ok)
        assertNull(validateFbCompose(listOf("alex"), "s", "b").error)
    }

    @Test
    fun `body length cap rejects at five thousand`() {
        assertTrue(validateFbCompose(listOf("alex"), "s", "a".repeat(FB_MAX_BODY - 1)).ok)
        val atCap = validateFbCompose(listOf("alex"), "s", "a".repeat(FB_MAX_BODY))
        assertFalse(atCap.ok)
        assertEquals("message is too long", atCap.error)
    }

    @Test
    fun `folder filter merges read state and search`() {
        val inbox = FbMail.folderThreads(FacebookSeed.threads, FbFolder.INBOX, emptySet(), emptySet())
        assertEquals(4, inbox.size)
        assertTrue(FbMail.effectiveUnread(inbox.first { it.id == 21L }, emptySet()))
        assertFalse(FbMail.effectiveUnread(inbox.first { it.id == 21L }, setOf(21L)))
        assertEquals(1, FbMail.unreadCount(FacebookSeed.threads, setOf(22L, 23L), emptySet()))
        val deleted = FbMail.folderThreads(FacebookSeed.threads, FbFolder.INBOX, emptySet(), setOf(21L))
        assertEquals(3, deleted.size)
        val searched = FbMail.folderThreads(FacebookSeed.threads, FbFolder.INBOX, emptySet(), emptySet(), "mix")
        assertEquals(listOf(22L), searched.map { it.id })
    }

    @Test
    fun `feed pages slice at the cache page size`() {
        val posts = List(30) { post(it.toLong()) }
        assertEquals(25, FeedCache.page(posts, 0).size)
        assertEquals(5, FeedCache.page(posts, 1).size)
        assertTrue(FeedCache.page(posts, 2).isEmpty())
        assertEquals(2, FeedCache.pageCount(posts))
    }

    @Test
    fun `merge dedupes by id with incoming winning`() {
        val existing = listOf(post(1), post(3).copy(message = "old"))
        val incoming = listOf(post(3).copy(message = "new"), post(5))
        val merged = FeedCache.merge(existing, incoming)
        assertEquals(listOf(5L, 3L, 1L), merged.map { it.id })
        assertEquals("new", merged.first { it.id == 3L }.message)
    }

    @Test
    fun `link splitting isolates urls`() {
        val tokens = splitFbLinks("go to https://a.example/x now")
        assertEquals(3, tokens.size)
        assertEquals(FbTokenKind.TEXT, tokens[0].kind)
        assertEquals(FbTokenKind.LINK, tokens[1].kind)
        assertEquals("https://a.example/x", tokens[1].text)
        assertEquals(" now", tokens[2].text)
    }

    @Test
    fun `compose and decision codecs round trip`() {
        val draft = FbCompose(
            to = listOf("alex", "dana"),
            subject = "re: mix | notes",
            body = "first line\nsecond | line",
        )
        assertEquals(draft, FbCodec.decodeCompose(FbCodec.encodeCompose(draft)))
        assertNull(FbCodec.decodeCompose(null))

        val decisions = mapOf("casey" to true, "morgan" to false)
        assertEquals(decisions, FbCodec.decodeDecisions(FbCodec.encodeDecisions(decisions)))
    }
}
