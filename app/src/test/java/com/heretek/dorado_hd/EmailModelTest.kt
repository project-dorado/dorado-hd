package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.Mail
import com.heretek.dorado_hd.ui.apps.mocks.MailCodec
import com.heretek.dorado_hd.ui.apps.mocks.MailDraft
import com.heretek.dorado_hd.ui.apps.mocks.MailFolder
import com.heretek.dorado_hd.ui.apps.mocks.MailPivot
import com.heretek.dorado_hd.ui.apps.mocks.MailPriority
import com.heretek.dorado_hd.ui.apps.mocks.MailSeed
import com.heretek.dorado_hd.ui.apps.mocks.Mailbox
import com.heretek.dorado_hd.ui.apps.mocks.validateSend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailModelTest {

    private fun personalInbox(): List<Mail> =
        MailSeed.mails.filter { it.accountId == "personal" }

    @Test
    fun `send is blocked until a recipient exists`() {
        assertFalse(validateSend(emptyList(), emptyList(), emptyList()).ok)
        assertEquals("add at least one recipient", validateSend(emptyList(), emptyList(), emptyList()).error)
        assertTrue(validateSend(listOf("a@b.example"), emptyList(), emptyList()).ok)
        assertTrue(validateSend(emptyList(), listOf("c@d.example"), emptyList()).ok)
        assertTrue(validateSend(emptyList(), emptyList(), listOf("e@f.example")).ok)
        assertNull(validateSend(listOf("a@b.example"), emptyList(), emptyList()).error)
    }

    @Test
    fun `read overrides win in both directions`() {
        val unreadSeed = MailSeed.mails.first { it.id == 1L }
        val readSeed = MailSeed.mails.first { it.id == 3L }
        assertTrue(Mailbox.isUnread(unreadSeed, emptyMap()))
        assertFalse(Mailbox.isUnread(unreadSeed, mapOf(1L to true)))
        assertFalse(Mailbox.isUnread(readSeed, emptyMap()))
        assertTrue(Mailbox.isUnread(readSeed, mapOf(3L to false)))
    }

    @Test
    fun `pivot filtering honours unread urgent and deletions`() {
        val mails = personalInbox()
        val all = Mailbox.visible(mails, "personal", MailFolder.INBOX, MailPivot.ALL, emptyMap(), emptySet())
        assertEquals(4, all.size)
        val unread = Mailbox.visible(mails, "personal", MailFolder.INBOX, MailPivot.UNREAD, emptyMap(), emptySet())
        assertEquals(listOf(1L, 2L, 4L), unread.map { it.id })
        val urgent = Mailbox.visible(mails, "personal", MailFolder.INBOX, MailPivot.URGENT, emptyMap(), emptySet())
        assertEquals(listOf(1L), urgent.map { it.id })
        val withoutFirst = Mailbox.visible(mails, "personal", MailFolder.INBOX, MailPivot.ALL, emptyMap(), setOf(1L))
        assertEquals(listOf(2L, 3L, 4L), withoutFirst.map { it.id })
        val readFirst = Mailbox.visible(mails, "personal", MailFolder.INBOX, MailPivot.UNREAD, mapOf(1L to true), emptySet())
        assertEquals(listOf(2L, 4L), readFirst.map { it.id })
    }

    @Test
    fun `unread count follows read overrides and ignores deleted`() {
        val mails = MailSeed.mails
        assertEquals(4, Mailbox.unreadCount(mails, "personal", emptyMap(), emptySet()))
        assertEquals(3, Mailbox.unreadCount(mails, "personal", emptyMap(), setOf(1L)))
        assertEquals(2, Mailbox.unreadCount(mails, "personal", mapOf(1L to true, 2L to true), emptySet()))
        assertEquals(1, Mailbox.unreadCount(mails, "personal", emptyMap(), setOf(1L, 2L), MailFolder.JUNK))
        assertEquals(0, Mailbox.unreadCount(mails, "personal", emptyMap(), emptySet(), MailFolder.ARCHIVE))
    }

    @Test
    fun `draft codec round trips separators and lists`() {
        val draft = MailDraft(
            accountId = "personal",
            to = listOf("a@b.example", "c@d.example"),
            cc = listOf("e@f.example"),
            bcc = emptyList(),
            subject = "re: plan | notes",
            body = "line one\nline two with a | pipe and \\ slash",
            signature = false,
            priority = MailPriority.HIGH,
            quotedFrom = "> older message",
            touched = true,
        )
        assertEquals(draft, MailCodec.decodeDraft(MailCodec.encodeDraft(draft)))
        assertNull(MailCodec.decodeDraft(null))
        assertNull(MailCodec.decodeDraft(""))
    }

    @Test
    fun `id and read-state codecs round trip`() {
        val ids = setOf(9L, 2L, 5L)
        assertEquals(ids, MailCodec.decodeIds(MailCodec.encodeIds(ids)))
        assertTrue(MailCodec.decodeIds(null).isEmpty())

        val state = mapOf(3L to true, 9L to false)
        assertEquals(state, MailCodec.decodeReadState(MailCodec.encodeReadState(state)))
        assertTrue(MailCodec.decodeReadState("garbage").isEmpty())
    }

    @Test
    fun `viewer walk clamps at both ends`() {
        assertEquals(0, Mailbox.step(0, 4, -1))
        assertEquals(3, Mailbox.step(3, 4, 1))
        assertEquals(2, Mailbox.step(1, 4, 1))
        assertEquals(0, Mailbox.step(0, 0, 1))
    }
}
