package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.mocks.EmoticonEncoder
import com.heretek.dorado_hd.ui.apps.mocks.MessengerCodec
import com.heretek.dorado_hd.ui.apps.mocks.MsgrAccount
import com.heretek.dorado_hd.ui.apps.mocks.MsgrContact
import com.heretek.dorado_hd.ui.apps.mocks.Presence
import com.heretek.dorado_hd.ui.apps.mocks.RichSpan
import com.heretek.dorado_hd.ui.apps.mocks.canSend
import com.heretek.dorado_hd.ui.apps.mocks.conversationEnabled
import com.heretek.dorado_hd.ui.apps.mocks.partitionGroups
import com.heretek.dorado_hd.ui.apps.mocks.sortContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerModelTest {

    private fun contact(name: String, presence: Presence, group: String = "friends"): MsgrContact =
        MsgrContact(id = name, name = name, personal = "hi", presence = presence, group = group)

    @Test
    fun `emoticon encoding splits and reconstructs text`() {
        val text = "hi :) meet <3 (H) later :P"
        val spans = EmoticonEncoder.encode(text)
        assertEquals(text, EmoticonEncoder.plain(spans))
        assertTrue(spans.any { it is RichSpan.Emoticon && it.name == "heart" })
        assertTrue(spans.any { it is RichSpan.Emoticon && it.code == ":)" })

        val plain = EmoticonEncoder.encode("no symbols here")
        assertEquals(1, plain.size)
        assertTrue(plain.first() is RichSpan.Text)
    }

    @Test
    fun `sorting groups by presence or alphabetically`() {
        val contacts = listOf(
            contact("zoe", Presence.OFFLINE),
            contact("amy", Presence.AVAILABLE),
            contact("bob", Presence.AWAY),
            contact("cal", Presence.AVAILABLE),
        )
        assertEquals(listOf("amy", "cal", "bob", "zoe"), sortContacts(contacts, byStatus = true).map { it.name })
        assertEquals(listOf("amy", "bob", "cal", "zoe"), sortContacts(contacts, byStatus = false).map { it.name })
    }

    @Test
    fun `presence gates the send input`() {
        assertTrue(canSend(contact("amy", Presence.AVAILABLE)))
        assertTrue(canSend(contact("bob", Presence.BUSY)))
        assertFalse(canSend(contact("zoe", Presence.OFFLINE)))
        assertFalse(canSend(null))
        assertTrue(conversationEnabled(listOf("amy", "zoe"), listOf(contact("amy", Presence.AVAILABLE), contact("zoe", Presence.OFFLINE))))
        assertFalse(conversationEnabled(listOf("zoe"), listOf(contact("zoe", Presence.OFFLINE))))
    }

    @Test
    fun `groups partition with leftovers in other contacts`() {
        val contacts = listOf(
            contact("amy", Presence.AVAILABLE, "friends"),
            contact("bob", Presence.BUSY, "work"),
            contact("cal", Presence.AWAY, "band"),
        )
        val groups = partitionGroups(contacts, listOf("friends", "work", "family"))
        assertEquals(listOf("friends", "work", "other contacts"), groups.map { it.first })
        assertEquals(listOf("cal"), groups.last().second.map { it.name })
    }

    @Test
    fun `account and options codecs round trip`() {
        val account = MsgrAccount("you | me", "you@live.example", Presence.BUSY, "listening\nagain")
        assertEquals(account, MessengerCodec.decodeAccount(MessengerCodec.encodeAccount(account)))
        assertNull(MessengerCodec.decodeAccount(null))

        val options = MessengerCodec.MsgrOptions(
            showEmoticons = false,
            showTimestamps = true,
            autoCorrect = false,
            sounds = true,
            byStatus = true,
        )
        assertEquals(options, MessengerCodec.decodeOptions(MessengerCodec.encodeOptions(options)))
        assertEquals(MessengerCodec.MsgrOptions(), MessengerCodec.decodeOptions(null))
    }
}
