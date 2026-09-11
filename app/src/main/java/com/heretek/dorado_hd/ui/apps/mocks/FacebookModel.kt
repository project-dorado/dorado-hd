package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Pure offline-social engine for FacebookApp (docs/apps/facebook.md §3):
 * feed paging/merge, folder filtering with a read overlay, link splitting,
 * compose validation and the state codecs. No Android or Compose imports.
 */

enum class FbFolder(val label: String) {
    INBOX("inbox"),
    SENT("sent"),
    UPDATES("updates"),
}

data class FbPost(
    val id: Long,
    val author: String,
    val age: String,
    val message: String,
    val likes: Int,
    val comments: Int,
    val liked: Boolean = false,
    val attachment: String? = null,
    val album: String? = null,
)

data class FbMessage(
    val from: String,
    val body: String,
    val time: String,
)

data class FbThread(
    val id: Long,
    val folder: FbFolder,
    val with: String,
    val subject: String,
    val snippet: String,
    val time: String,
    val unread: Boolean = false,
    val messages: List<FbMessage> = emptyList(),
)

data class FbCompose(
    val to: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
) {
    fun isBlank(): Boolean = to.isEmpty() && subject.isBlank() && body.isBlank()
}

data class FbValidation(val ok: Boolean, val error: String?)

const val FB_MAX_BODY = 5000

/** One addressee plus subject and body are required; bodies at 5000 are rejected. */
fun validateFbCompose(to: List<String>, subject: String, body: String): FbValidation = when {
    to.isEmpty() -> FbValidation(false, "add at least one recipient")
    subject.isBlank() -> FbValidation(false, "add a subject")
    body.isBlank() -> FbValidation(false, "write a message")
    body.length >= FB_MAX_BODY -> FbValidation(false, "message is too long")
    else -> FbValidation(true, null)
}

enum class FbTokenKind { TEXT, LINK }

data class FbToken(val kind: FbTokenKind, val text: String)

fun splitFbLinks(text: String): List<FbToken> {
    val out = mutableListOf<FbToken>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += FbToken(FbTokenKind.TEXT, plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val link = text.startsWith("http://", i) || text.startsWith("https://", i) || text.startsWith("www.", i)
        if (link) {
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            flush()
            out += FbToken(FbTokenKind.LINK, text.substring(i, j))
            i = j
        } else {
            plain.append(text[i])
            i++
        }
    }
    flush()
    return out
}

object FeedCache {

    const val PAGE_SIZE = 25

    fun page(posts: List<FbPost>, page: Int): List<FbPost> =
        if (page < 0) emptyList() else posts.drop(page * PAGE_SIZE).take(PAGE_SIZE)

    fun pageCount(posts: List<FbPost>): Int =
        if (posts.isEmpty()) 1 else (posts.size + PAGE_SIZE - 1) / PAGE_SIZE

    /** Incoming page wins on id collision; newest ids first. */
    fun merge(existing: List<FbPost>, incoming: List<FbPost>): List<FbPost> =
        (incoming + existing).distinctBy { it.id }.sortedByDescending { it.id }
}

object FbMail {

    fun effectiveUnread(thread: FbThread, read: Set<Long>): Boolean =
        thread.unread && thread.id !in read

    fun folderThreads(
        threads: List<FbThread>,
        folder: FbFolder,
        read: Set<Long>,
        deleted: Set<Long>,
        query: String = "",
    ): List<FbThread> {
        val needle = query.trim().lowercase()
        return threads.filter {
            it.folder == folder && it.id !in deleted &&
                (needle.isEmpty() || it.subject.lowercase().contains(needle) ||
                    it.snippet.lowercase().contains(needle) ||
                    it.messages.any { m -> m.body.lowercase().contains(needle) })
        }
    }

    fun markRead(ids: Set<Long>, read: Set<Long>): Set<Long> = read + ids

    fun unreadCount(threads: List<FbThread>, read: Set<Long>, deleted: Set<Long>): Int =
        threads.count { it.folder == FbFolder.INBOX && it.id !in deleted && effectiveUnread(it, read) }
}

data class FbNotification(
    val id: Long,
    val kind: String,
    val text: String,
    val age: String,
    val unread: Boolean = true,
)

object FbCodec {

    fun encodeIds(ids: Set<Long>): String = ids.sorted().joinToString(",")

    fun decodeIds(blob: String?): Set<Long> =
        blob?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()

    fun encodeCompose(draft: FbCompose): String =
        listOf(esc(draft.to.joinToString(",")), esc(draft.subject), esc(draft.body)).joinToString("|")

    fun decodeCompose(blob: String?): FbCompose? {
        if (blob.isNullOrBlank()) return null
        val parts = blob.split('|')
        if (parts.size < 3) return null
        return FbCompose(
            to = unesc(parts[0]).split(',').map { it.trim() }.filter { it.isNotEmpty() },
            subject = unesc(parts[1]),
            body = unesc(parts[2]),
        )
    }

    /** Friend-request decisions: true = confirmed, false = ignored. */
    fun encodeDecisions(decisions: Map<String, Boolean>): String =
        decisions.entries.sortedBy { it.key }.joinToString(",") {
            "${it.key}=${if (it.value) 1 else 0}"
        }

    fun decodeDecisions(blob: String?): Map<String, Boolean> = buildMap {
        blob?.split(',')?.forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) put(part.substring(0, eq).trim(), part.substring(eq + 1).trim() == "1")
        }
    }

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

object FacebookSeed {

    val user = "you"
    val personalMessage = "still sorting the shelf by mood"

    val posts: List<FbPost> = listOf(
        FbPost(
            id = 5, author = "alex", age = "2h",
            message = "put the whole live set up at https://audio.example/live — side two is the one",
            likes = 12, comments = 3, attachment = "live-set cover", album = "field recordings",
        ),
        FbPost(
            id = 4, author = "jordan", age = "5h",
            message = "photo walk tomorrow if the light holds. bring the small camera.",
            likes = 7, comments = 2,
        ),
        FbPost(
            id = 3, author = "sam", age = "yesterday",
            message = "new mix is up: slow start, loud middle, soft landing. https://tapes.example/mix-nine",
            likes = 21, comments = 6, attachment = "mix art", album = "tapes",
        ),
        FbPost(
            id = 2, author = "dana", age = "yesterday",
            message = "found the record we talked about at the market. sleeve is rough, vinyl is clean.",
            likes = 9, comments = 1,
        ),
        FbPost(
            id = 1, author = "riley", age = "mon",
            message = "reading week: three books, one playlist, no plans.",
            likes = 4, comments = 0,
        ),
    )

    val threads: List<FbThread> = listOf(
        FbThread(
            id = 21, folder = FbFolder.INBOX, with = "dana", subject = "saturday plans",
            snippet = "trailhead at eight, bring the thermos", time = "09:12", unread = true,
            messages = listOf(
                FbMessage("dana", "trailhead at eight, bring the thermos", "09:12"),
                FbMessage("you", "works for me — i'll bring the small speaker", "09:20"),
            ),
        ),
        FbThread(
            id = 22, folder = FbFolder.INBOX, with = "sam", subject = "the mix",
            snippet = "did track four clip for you too?", time = "yesterday", unread = true,
            messages = listOf(FbMessage("sam", "did track four clip for you too?", "yesterday")),
        ),
        FbThread(
            id = 23, folder = FbFolder.INBOX, with = "riley", subject = "book swap",
            snippet = "left two on your step", time = "mon", unread = false,
            messages = listOf(FbMessage("riley", "left two on your step", "mon")),
        ),
        FbThread(
            id = 24, folder = FbFolder.INBOX, with = "alex", subject = "turntable",
            snippet = "the belt arrived, it runs quiet now", time = "mon", unread = false,
            messages = listOf(FbMessage("alex", "the belt arrived, it runs quiet now", "mon")),
        ),
        FbThread(
            id = 25, folder = FbFolder.SENT, with = "maria", subject = "re: sleeve notes",
            snippet = "sending the scan tonight", time = "sun",
            messages = listOf(FbMessage("you", "sending the scan tonight", "sun")),
        ),
        FbThread(
            id = 26, folder = FbFolder.SENT, with = "jordan", subject = "photo walk",
            snippet = "i'll bring the wide lens", time = "sun",
            messages = listOf(FbMessage("you", "i'll bring the wide lens", "sun")),
        ),
        FbThread(
            id = 27, folder = FbFolder.UPDATES, with = "events", subject = "record fair this weekend",
            snippet = "hall two, doors at ten", time = "thu",
            messages = listOf(FbMessage("events", "hall two, doors at ten", "thu")),
        ),
        FbThread(
            id = 28, folder = FbFolder.UPDATES, with = "group: shelf watch", subject = "three new posts",
            snippet = "sam and two others posted", time = "thu",
            messages = listOf(FbMessage("group: shelf watch", "sam and two others posted", "thu")),
        ),
    )

    val friends: List<String> = listOf(
        "alex", "dana", "jordan", "maria", "riley", "sam", "taylor", "wes",
    )

    val requests: List<String> = listOf("casey", "morgan")

    val notifications: List<FbNotification> = listOf(
        FbNotification(31, "like", "alex liked your photo", "2h", unread = true),
        FbNotification(32, "comment", "dana commented on your post", "4h", unread = true),
        FbNotification(33, "wall", "jordan wrote on your wall", "yesterday", unread = true),
        FbNotification(34, "friend", "casey sent a friend request", "yesterday", unread = true),
        FbNotification(35, "photo", "maria tagged you in a photo", "mon", unread = false),
        FbNotification(36, "music", "riley shared a track with you", "mon", unread = false),
    )

    val albums: List<Pair<String, List<String>>> = listOf(
        "summer light" to listOf("porch", "attic window", "back field", "last evening"),
        "the show" to listOf("balcony", "rail", "encore", "empty hall"),
        "market" to listOf("crates", "sleeves", "walk home"),
    )
}
