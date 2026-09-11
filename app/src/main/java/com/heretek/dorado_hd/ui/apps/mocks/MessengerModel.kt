package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Pure offline-im engine for MessengerApp (docs/apps/messenger.md §3):
 * presence gating, emoticon span encoding, contact sorting/grouping and the
 * account/options codecs. Android-free.
 */

enum class Presence(val label: String) {
    AVAILABLE("available"),
    BUSY("busy"),
    AWAY("away"),
    OFFLINE("offline"),
}

data class MsgrAccount(
    val displayName: String,
    val email: String,
    val presence: Presence,
    val personalMessage: String,
)

data class MsgrContact(
    val id: String,
    val name: String,
    val personal: String,
    val presence: Presence,
    val group: String,
    val typing: Boolean = false,
    val unread: Int = 0,
)

data class MsgrMessage(
    val from: String,
    val text: String,
    val time: String,
    val mine: Boolean = false,
    val nudge: Boolean = false,
    val media: String? = null,
)

data class MsgrConversation(
    val id: String,
    val title: String,
    val contactIds: List<String>,
    val messages: List<MsgrMessage> = emptyList(),
)

sealed class RichSpan {
    data class Text(val text: String) : RichSpan()
    data class Emoticon(val code: String, val name: String) : RichSpan()
}

object EmoticonEncoder {

    private val codes: List<Pair<String, String>> = listOf(
        ":-)" to "smile",
        ":)" to "smile",
        ":D" to "grin",
        ":-(" to "frown",
        ":(" to "frown",
        ";)" to "wink",
        ";-)" to "wink",
        ":P" to "tongue",
        ":-P" to "tongue",
        ":'(" to "cry",
        "<3" to "heart",
        "(H)" to "cool",
        ":o" to "surprised",
    ).sortedByDescending { it.first.length }

    fun encode(text: String): List<RichSpan> {
        val out = mutableListOf<RichSpan>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                out += RichSpan.Text(plain.toString())
                plain.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            val match = codes.firstOrNull { (code, _) -> text.startsWith(code, i) }
            if (match != null) {
                flush()
                out += RichSpan.Emoticon(match.first, match.second)
                i += match.first.length
            } else {
                plain.append(text[i])
                i++
            }
        }
        flush()
        return out
    }

    fun plain(spans: List<RichSpan>): String = spans.joinToString("") {
        when (it) {
            is RichSpan.Text -> it.text
            is RichSpan.Emoticon -> it.code
        }
    }
}

private val PRESENCE_ORDER = mapOf(
    Presence.AVAILABLE to 0,
    Presence.BUSY to 1,
    Presence.AWAY to 2,
    Presence.OFFLINE to 3,
)

/** Options > Contacts sort: status groups first, or strict alphabetical. */
fun sortContacts(contacts: List<MsgrContact>, byStatus: Boolean): List<MsgrContact> =
    if (byStatus) {
        contacts.sortedWith(
            compareBy({ PRESENCE_ORDER[it.presence] ?: 9 }, { it.name.lowercase() }),
        )
    } else {
        contacts.sortedBy { it.name.lowercase() }
    }

/** Group headers in the given order; anything unmatched lands in "other contacts". */
fun partitionGroups(contacts: List<MsgrContact>, order: List<String>): List<Pair<String, List<MsgrContact>>> {
    val remaining = contacts.toMutableList()
    val out = mutableListOf<Pair<String, List<MsgrContact>>>()
    order.forEach { group ->
        val members = remaining.filter { it.group == group }
        if (members.isNotEmpty()) {
            out += group to members
            remaining -= members.toSet()
        }
    }
    if (remaining.isNotEmpty()) out += "other contacts" to remaining.toList()
    return out
}

/** Presence gates the input bar: offline contacts cannot be messaged. */
fun canSend(contact: MsgrContact?): Boolean = contact != null && contact.presence != Presence.OFFLINE

fun conversationEnabled(contactIds: List<String>, contacts: List<MsgrContact>): Boolean {
    val byId = contacts.associateBy { it.id }
    return contactIds.any { canSend(byId[it]) }
}

object MessengerCodec {

    fun encodeAccount(account: MsgrAccount): String = listOf(
        esc(account.displayName),
        esc(account.email),
        account.presence.name,
        esc(account.personalMessage),
    ).joinToString("|")

    fun decodeAccount(blob: String?): MsgrAccount? {
        if (blob.isNullOrBlank()) return null
        val parts = blob.split('|')
        if (parts.size < 4) return null
        val presence = Presence.entries.firstOrNull { it.name == parts[2] } ?: Presence.AVAILABLE
        return MsgrAccount(unesc(parts[0]), unesc(parts[1]), presence, unesc(parts[3]))
    }

    data class MsgrOptions(
        val showEmoticons: Boolean = true,
        val showTimestamps: Boolean = true,
        val autoCorrect: Boolean = true,
        val sounds: Boolean = true,
        val byStatus: Boolean = false,
    )

    fun encodeOptions(options: MsgrOptions): String = listOf(
        flag(options.showEmoticons),
        flag(options.showTimestamps),
        flag(options.autoCorrect),
        flag(options.sounds),
        flag(options.byStatus),
    ).joinToString("|")

    fun decodeOptions(blob: String?): MsgrOptions {
        if (blob.isNullOrBlank()) return MsgrOptions()
        val parts = blob.split('|')
        fun at(index: Int, fallback: Boolean) = parts.getOrNull(index)?.let { it == "1" } ?: fallback
        return MsgrOptions(
            showEmoticons = at(0, true),
            showTimestamps = at(1, true),
            autoCorrect = at(2, true),
            sounds = at(3, true),
            byStatus = at(4, false),
        )
    }

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    private fun esc(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '|' -> append("\\p")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    private fun unesc(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'p' -> append('|')
                    'n' -> append('\n')
                    '\\' -> append('\\')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}

object MessengerSeed {

    val account = MsgrAccount(
        displayName = "you",
        email = "you@live.example",
        presence = Presence.AVAILABLE,
        personalMessage = "listening to side b again",
    )

    val groups: List<String> = listOf("friends", "work", "family")

    val contacts: List<MsgrContact> = listOf(
        MsgrContact("alex", "alex", "still collecting tapes", Presence.AVAILABLE, "friends", typing = true, unread = 2),
        MsgrContact("dana", "dana", "trail maps and thermos lids", Presence.AVAILABLE, "friends"),
        MsgrContact("jordan", "jordan", "light is best after five", Presence.AWAY, "friends"),
        MsgrContact("maria", "maria", "mix in progress", Presence.BUSY, "work", unread = 1),
        MsgrContact("sam", "sam", "sleeve notes take longer", Presence.AVAILABLE, "work"),
        MsgrContact("nadine", "nadine", "desk moved to the north side", Presence.OFFLINE, "work"),
        MsgrContact("riley", "riley", "reading week is a lifestyle", Presence.AWAY, "family"),
        MsgrContact("wes", "wes", "cat has the good chair", Presence.OFFLINE, "family"),
    )

    val conversations: List<MsgrConversation> = listOf(
        MsgrConversation(
            id = "c1",
            title = "alex",
            contactIds = listOf("alex"),
            messages = listOf(
                MsgrMessage("alex", "are you still on the old player? :)", "09:04"),
                MsgrMessage("you", "every day. it still holds a charge", "09:06", mine = true),
                MsgrMessage("alex", "that <3 — send me the battery trick later", "09:07"),
            ),
        ),
        MsgrConversation(
            id = "c2",
            title = "maria",
            contactIds = listOf("maria"),
            messages = listOf(
                MsgrMessage("maria", "stems are up. listen to track two first :P", "yesterday"),
                MsgrMessage("you", "on it. give me an hour", "yesterday", mine = true),
            ),
        ),
        MsgrConversation(
            id = "c3",
            title = "sam",
            contactIds = listOf("sam"),
            messages = listOf(
                MsgrMessage("sam", "fair on saturday? doors at ten", "mon"),
                MsgrMessage("you", "yes — meet by the crates", "mon", mine = true),
                MsgrMessage("sam", "perfect :D", "mon"),
            ),
        ),
    )

    val social: List<Pair<String, String>> = listOf(
        "maria" to "rendered side b (H)",
        "sam" to "made a mix for the walk home",
        "dana" to "record fair haul photo dump",
        "alex" to "new belt, quiet table :)",
    )
}
