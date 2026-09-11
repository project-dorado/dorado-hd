package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.TwitterSeed
import com.heretek.dorado_hd.ui.apps.mocks.allDmThreads
import com.heretek.dorado_hd.ui.apps.mocks.appendLocalDm
import com.heretek.dorado_hd.ui.apps.mocks.threadMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-12 regression: a DM to a brand-new handle creates a real thread id, so the
 * DM list and the thread view can look it up by id everywhere.
 */
class TwitterDmStoreTest {

    @Test
    fun `dm to a new handle appears in the list and opens by id`() {
        val update = appendLocalDm(
            seedThreads = TwitterSeed.dms,
            localThreads = emptyList(),
            replies = emptyMap(),
            handle = "@nadia",
            body = "hello from the archive",
        )
        val threads = allDmThreads(TwitterSeed.dms, update.threads)
        val created = threads.firstOrNull { it.handle.equals("@nadia", ignoreCase = true) }
        assertNotNull("new handle did not create a thread", created)
        assertTrue(created!!.id > TwitterSeed.dms.maxOf { it.id })
        val byId = threads.firstOrNull { it.id == created.id }
        assertNotNull(byId)
        assertEquals(listOf("hello from the archive"), threadMessages(byId!!, update.replies).map { it.text })
    }

    @Test
    fun `dm to an existing handle appends to that thread only`() {
        val update = appendLocalDm(
            seedThreads = TwitterSeed.dms,
            localThreads = emptyList(),
            replies = emptyMap(),
            handle = "@maria",
            body = "one more thing",
        )
        assertTrue("existing handle should not add a thread", update.threads.isEmpty())
        val thread = TwitterSeed.dms.first { it.handle == "@maria" }
        assertEquals("one more thing", threadMessages(thread, update.replies).last().text)
    }

    @Test
    fun `handle without the at sign is normalized`() {
        val update = appendLocalDm(
            seedThreads = TwitterSeed.dms,
            localThreads = emptyList(),
            replies = emptyMap(),
            handle = "wes",
            body = "hi",
        )
        val created = allDmThreads(TwitterSeed.dms, update.threads).first { it.with == "wes" }
        assertEquals("@wes", created.handle)
    }
}
