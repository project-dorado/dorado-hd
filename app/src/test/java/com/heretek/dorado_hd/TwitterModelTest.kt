package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.ComposeTarget
import com.heretek.dorado_hd.ui.apps.mocks.MAX_TWEET_LENGTH
import com.heretek.dorado_hd.ui.apps.mocks.Tweet
import com.heretek.dorado_hd.ui.apps.mocks.TweetCache
import com.heretek.dorado_hd.ui.apps.mocks.TweetTokenKind
import com.heretek.dorado_hd.ui.apps.mocks.TwitterCodec
import com.heretek.dorado_hd.ui.apps.mocks.composeState
import com.heretek.dorado_hd.ui.apps.mocks.dmTarget
import com.heretek.dorado_hd.ui.apps.mocks.splitTweet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitterModelTest {

    private fun tweet(id: Long, text: String = "t[$id]"): Tweet =
        Tweet(id = id, author = "a$id", handle = "@a$id", text = text, age = "now")

    @Test
    fun `counter counts down and gates send`() {
        val empty = composeState("")
        assertFalse(empty.enabled)
        assertEquals(MAX_TWEET_LENGTH, empty.remaining)

        val ok = composeState("hello")
        assertTrue(ok.enabled)
        assertEquals(MAX_TWEET_LENGTH - 5, ok.remaining)
        assertFalse(ok.overLimit)

        val exact = composeState("a".repeat(MAX_TWEET_LENGTH))
        assertTrue(exact.enabled)
        assertEquals(0, exact.remaining)

        val over = composeState("a".repeat(MAX_TWEET_LENGTH + 1))
        assertFalse(over.enabled)
        assertTrue(over.overLimit)
        assertEquals(-1, over.remaining)
    }

    @Test
    fun `leading mention retargets compose to a direct message`() {
        val state = composeState("@maria the mix is ready")
        assertEquals(ComposeTarget.DM, state.target)
        assertEquals("@maria", state.recipient)
        assertTrue(state.enabled)
        assertEquals("@maria" to "the mix is ready", dmTarget("@maria the mix is ready"))

        val bare = composeState("@maria")
        assertFalse(bare.enabled)
        assertNull(dmTarget("hello @maria"))
    }

    @Test
    fun `cache paginates page sized blocks`() {
        val cache = TweetCache(pageSize = 20)
        val tweets = List(25) { tweet(it.toLong()) }.sortedByDescending { it.id }
        assertEquals(20, cache.page(tweets, 0).size)
        assertEquals(5, cache.page(tweets, 1).size)
        assertEquals(2, cache.pageCount(tweets))
    }

    @Test
    fun `merge is id based newest first and incoming wins`() {
        val cache = TweetCache()
        val existing = listOf(tweet(1), tweet(3, "old"))
        val incoming = listOf(tweet(3, "new"), tweet(5))
        val merged = cache.merge(existing, incoming)
        assertEquals(listOf(5L, 3L, 1L), merged.map { it.id })
        assertEquals("new", merged.first { it.id == 3L }.text)
    }

    @Test
    fun `tokenizer splits links hashtags and mentions`() {
        val tokens = splitTweet("see #now and @you at https://x.example/a")
        assertEquals(
            listOf(
                TweetTokenKind.TEXT,
                TweetTokenKind.HASHTAG,
                TweetTokenKind.TEXT,
                TweetTokenKind.MENTION,
                TweetTokenKind.TEXT,
                TweetTokenKind.LINK,
            ),
            tokens.map { it.kind },
        )
        assertEquals("#now", tokens[1].text)
        assertEquals("@you", tokens[3].text)
        assertEquals("https://x.example/a", tokens[5].text)
    }

    @Test
    fun `draft and favourite codecs round trip`() {
        val draft = "first line\nsecond @handle #tag"
        assertEquals(draft, TwitterCodec.decodeDraft(TwitterCodec.encodeDraft(draft)))
        val favs = setOf(105L, 7L)
        assertEquals(favs, TwitterCodec.decodeIds(TwitterCodec.encodeIds(favs)))
    }
}
