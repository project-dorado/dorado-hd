package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Pure offline-mail engine for EmailApp (docs/apps/email.md §3). No Android or
 * Compose imports: pivot filtering, read-state overrides, send validation and
 * the draft / state codecs are unit-testable directly.
 */

enum class MailFolder(val label: String) {
    INBOX("inbox"),
    ARCHIVE("archive"),
    SENT("sent"),
    DRAFTS("drafts"),
    JUNK("junk"),
}

enum class MailPivot(val label: String) {
    ALL("all"),
    UNREAD("unread"),
    URGENT("urgent"),
}

enum class MailPriority(val label: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
}

data class MailAccount(
    val id: String,
    val label: String,
    val address: String,
    val mark: String,
)

data class MailAttachment(val name: String, val size: String)

data class Mail(
    val id: Long,
    val accountId: String,
    val folder: MailFolder,
    val from: String,
    val fromAddress: String,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val received: String,
    val unread: Boolean = true,
    val urgent: Boolean = false,
    val replied: Boolean = false,
    val flagged: Boolean = false,
    val attachment: MailAttachment? = null,
)

data class MailDraft(
    val accountId: String,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val signature: Boolean = true,
    val priority: MailPriority = MailPriority.NORMAL,
    val quotedFrom: String? = null,
    val touched: Boolean = false,
) {
    fun isBlank(): Boolean =
        to.isEmpty() && cc.isEmpty() && bcc.isEmpty() && subject.isBlank() && body.isBlank()
}

data class SendValidation(val ok: Boolean, val error: String?)

/** Send is blocked while To, CC and BCC are all empty (email.md §3). */
fun validateSend(to: List<String>, cc: List<String>, bcc: List<String>): SendValidation =
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
        SendValidation(ok = false, error = "add at least one recipient")
    } else {
        SendValidation(ok = true, error = null)
    }

object Mailbox {

    /** Explicit user read/unread overrides; absent ids fall back to the seed flag. */
    fun isUnread(mail: Mail, readState: Map<Long, Boolean>): Boolean =
        readState[mail.id]?.let { !it } ?: mail.unread

    fun visible(
        mails: List<Mail>,
        accountId: String,
        folder: MailFolder,
        pivot: MailPivot,
        readState: Map<Long, Boolean>,
        deleted: Set<Long>,
    ): List<Mail> = mails
        .filter { it.accountId == accountId && it.folder == folder && it.id !in deleted }
        .filter {
            when (pivot) {
                MailPivot.ALL -> true
                MailPivot.UNREAD -> isUnread(it, readState)
                MailPivot.URGENT -> it.urgent
            }
        }

    fun unreadCount(
        mails: List<Mail>,
        accountId: String,
        readState: Map<Long, Boolean>,
        deleted: Set<Long>,
        folder: MailFolder? = null,
    ): Int = mails.count {
        it.accountId == accountId &&
            (folder == null || it.folder == folder) &&
            it.id !in deleted &&
            isUnread(it, readState)
    }

    fun markRead(ids: Set<Long>, readState: Map<Long, Boolean>): Map<Long, Boolean> =
        readState + ids.associateWith { true }

    fun markUnread(ids: Set<Long>, readState: Map<Long, Boolean>): Map<Long, Boolean> =
        readState + ids.associateWith { false }

    fun step(index: Int, size: Int, delta: Int): Int =
        if (size == 0) 0 else (index + delta).coerceIn(0, size - 1)
}

object MailCodec {

    fun encodeIds(ids: Set<Long>): String = ids.sorted().joinToString(",")

    fun decodeIds(blob: String?): Set<Long> =
        blob?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()

    fun encodeReadState(state: Map<Long, Boolean>): String =
        state.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" }

    fun decodeReadState(blob: String?): Map<Long, Boolean> = buildMap {
        blob?.split(',')?.forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                val id = part.substring(0, eq).trim().toLongOrNull()
                val value = part.substring(eq + 1).trim()
                if (id != null) put(id, value == "1")
            }
        }
    }

    fun encodeDraft(draft: MailDraft): String = listOf(
        esc(draft.accountId),
        esc(draft.to.joinToString(",")),
        esc(draft.cc.joinToString(",")),
        esc(draft.bcc.joinToString(",")),
        esc(draft.subject),
        esc(draft.body),
        if (draft.signature) "1" else "0",
        draft.priority.name,
        esc(draft.quotedFrom ?: ""),
        if (draft.touched) "1" else "0",
    ).joinToString("|")

    fun decodeDraft(blob: String?): MailDraft? {
        if (blob.isNullOrBlank()) return null
        val parts = blob.split('|')
        if (parts.size < 10) return null
        val priority = MailPriority.entries.firstOrNull { it.name == parts[7] } ?: MailPriority.NORMAL
        return MailDraft(
            accountId = unesc(parts[0]),
            to = splitAddresses(unesc(parts[1])),
            cc = splitAddresses(unesc(parts[2])),
            bcc = splitAddresses(unesc(parts[3])),
            subject = unesc(parts[4]),
            body = unesc(parts[5]),
            signature = parts[6] == "1",
            priority = priority,
            quotedFrom = unesc(parts[8]).ifBlank { null },
            touched = parts[9] == "1",
        )
    }

    fun formatAddress(name: String, address: String): String =
        if (name.isBlank()) address else "$name <$address>"

    private fun splitAddresses(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

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

object MailSeed {

    val accounts: List<MailAccount> = listOf(
        MailAccount("personal", "personal", "you@zmail.example", "z"),
        MailAccount("work", "work", "you@harbor.example", "h"),
    )

    val folders: List<Pair<MailFolder, Boolean>> = listOf(
        MailFolder.INBOX to true,
        MailFolder.ARCHIVE to true,
        MailFolder.SENT to true,
        MailFolder.DRAFTS to true,
        MailFolder.JUNK to false,
    )

    val mails: List<Mail> = listOf(
        Mail(
            id = 1,
            accountId = "personal",
            folder = MailFolder.INBOX,
            from = "maria",
            fromAddress = "maria@zmail.example",
            to = listOf("you@zmail.example"),
            subject = "the mixtape is done",
            body = "Side A came out warmer than I expected. I kept the long intro and cut the spoken bit.\n\nTell me if the level jumps at track four; I can re-render the whole side in an afternoon.",
            received = "mon 09:12",
            unread = true,
            urgent = true,
            attachment = MailAttachment("tracklist.txt", "2 KB"),
        ),
        Mail(
            id = 2,
            accountId = "personal",
            folder = MailFolder.INBOX,
            from = "field notes",
            fromAddress = "digest@fieldnotes.example",
            to = listOf("you@zmail.example"),
            subject = "weekly digest: albums we missed",
            body = "This week we went back through the shelves and pulled out three records that never made the countdown.\n\nOne is a live session, one is a soundtrack, and one is better than we remembered.",
            received = "sun 18:40",
            unread = true,
        ),
        Mail(
            id = 3,
            accountId = "personal",
            folder = MailFolder.INBOX,
            from = "dana",
            fromAddress = "dana@harbor.example",
            to = listOf("you@zmail.example"),
            cc = listOf("sam@harbor.example"),
            subject = "re: saturday hike",
            body = "Trailhead at eight works. Bring the thermos and the small speaker.\n\nIf it rains we can switch to the ridge route and still be back before dark.",
            received = "sat 20:05",
            unread = false,
            replied = true,
        ),
        Mail(
            id = 4,
            accountId = "personal",
            folder = MailFolder.INBOX,
            from = "the long player",
            fromAddress = "hello@longplayer.example",
            to = listOf("you@zmail.example"),
            subject = "your account summary is ready",
            body = "Your listening year in one page: top records, first plays and the albums you left on repeat.\n\nOpen the summary from any browser, or keep this mail as a keepsake.",
            received = "fri 11:02",
            unread = true,
        ),
        Mail(
            id = 5,
            accountId = "personal",
            folder = MailFolder.ARCHIVE,
            from = "sam",
            fromAddress = "sam@harbor.example",
            to = listOf("you@zmail.example"),
            subject = "photos from the show",
            body = "The balcony shots came out best. I put the blurry ones in a separate folder so you can ignore them.\n\nNext time we get there early for the rail.",
            received = "thu 23:31",
            unread = false,
        ),
        Mail(
            id = 6,
            accountId = "personal",
            folder = MailFolder.JUNK,
            from = "vinyl deals",
            fromAddress = "deals@vinyldeals.example",
            to = listOf("you@zmail.example"),
            subject = "last chance: mystery box",
            body = "Five records, one box, no returns. This sort of thing never works out, which is half the fun.",
            received = "wed 07:15",
            unread = true,
        ),
        Mail(
            id = 11,
            accountId = "work",
            folder = MailFolder.INBOX,
            from = "north desk",
            fromAddress = "desk@harbor.example",
            to = listOf("you@harbor.example"),
            subject = "review moved to thursday",
            body = "The quarterly review slid a day. Same room, same time, deck unchanged.\n\nIf you want a dry run on wednesday, book the small room and I will sit in.",
            received = "mon 08:01",
            unread = true,
            urgent = true,
        ),
        Mail(
            id = 12,
            accountId = "work",
            folder = MailFolder.INBOX,
            from = "people team",
            fromAddress = "people@harbor.example",
            to = listOf("you@harbor.example"),
            subject = "policy reminder: passwords",
            body = "Rotation window closes at the end of the month. The portal walks you through it in three steps.\n\nReply here if the reset form gives you trouble.",
            received = "fri 16:20",
            unread = false,
            replied = true,
        ),
        Mail(
            id = 13,
            accountId = "work",
            folder = MailFolder.INBOX,
            from = "conference desk",
            fromAddress = "program@harbor.example",
            to = listOf("you@harbor.example"),
            subject = "speaker agenda attached",
            body = "Draft agenda for the autumn session. We kept your slot on the second morning.\n\nMark anything that clashes and send it back by friday.",
            received = "thu 10:44",
            unread = true,
            attachment = MailAttachment("agenda.pdf", "184 KB"),
        ),
    )
}
