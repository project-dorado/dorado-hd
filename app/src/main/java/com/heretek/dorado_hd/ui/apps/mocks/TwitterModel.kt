package com.heretek.dorado_hd.ui.apps.mocks

/**
 * Pure offline-timeline engine for TwitterApp (docs/apps/twitter.md §3):
 * 140-character compose state, id-based cache merge, page slicing, link
 * splitting and the draft / favourite codecs. Android-free.
 */

const val MAX_TWEET_LENGTH = 140

enum class TweetTokenKind { TEXT, LINK, HASHTAG, MENTION }

data class TweetToken(val kind: TweetTokenKind, val text: String)

fun splitTweet(text: String): List<TweetToken> {
    val out = mutableListOf<TweetToken>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += TweetToken(TweetTokenKind.TEXT, plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '#' || c == '@') {
            var j = i + 1
            while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
            if (j > i + 1) {
                flush()
                out += TweetToken(
                    if (c == '#') TweetTokenKind.HASHTAG else TweetTokenKind.MENTION,
                    text.substring(i, j),
                )
                i = j
                continue
            }
        }
        if (text.startsWith("http://", i) || text.startsWith("https://", i)) {
            var j = i
            while (j < text.length && !text[j].isWhitespace()) j++
            flush()
            out += TweetToken(TweetTokenKind.LINK, text.substring(i, j))
            i = j
            continue
        }
        plain.append(c)
        i++
    }
    flush()
    return out
}

data class Tweet(
    val id: Long,
    val author: String,
    val handle: String,
    val text: String,
    val age: String,
    val favorite: Boolean = false,
)

enum class ComposeTarget { TWEET, DM }

data class ComposeState(
    val enabled: Boolean,
    val remaining: Int,
    val overLimit: Boolean,
    val target: ComposeTarget,
    val recipient: String?,
)

/** Remaining count and send gating: non-empty, within the limit (twitter.md §3). */
fun composeState(text: String, max: Int = MAX_TWEET_LENGTH): ComposeState {
    val dm = dmTarget(text)
    val over = text.length > max
    val enabled = if (dm != null) {
        dm.second.isNotBlank() && !over
    } else {
        text.isNotBlank() && !over
    }
    return ComposeState(
        enabled = enabled,
        remaining = max - text.length,
        overLimit = over,
        target = if (dm != null) ComposeTarget.DM else ComposeTarget.TWEET,
        recipient = dm?.first,
    )
}

/** A leading `@handle` retargets the tweet composer to a direct message. */
fun dmTarget(text: String): Pair<String, String>? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("@")) return null
    val handle = trimmed.takeWhile { !it.isWhitespace() }
    if (handle.length < 2) return null
    val body = trimmed.drop(handle.length).trimStart()
    return handle to body
}

class TweetCache(private val pageSize: Int = 20) {

    /** Incoming wins on id collision; newest ids first. */
    fun merge(existing: List<Tweet>, incoming: List<Tweet>): List<Tweet> =
        (incoming + existing).distinctBy { it.id }.sortedByDescending { it.id }

    fun page(items: List<Tweet>, page: Int): List<Tweet> =
        if (page < 0) emptyList() else items.drop(page * pageSize).take(pageSize)

    fun pageCount(items: List<Tweet>): Int =
        if (items.isEmpty()) 1 else (items.size + pageSize - 1) / pageSize
}

data class DmMessage(val from: String, val text: String, val age: String, val mine: Boolean = false)

data class DmThread(
    val id: Long,
    val with: String,
    val handle: String,
    val messages: List<DmMessage> = emptyList(),
) {
    fun preview(): String = messages.lastOrNull()?.text ?: ""
}

/** Outcome of appending an outgoing DM to the offline store. */
data class LocalDmUpdate(
    val threads: List<DmThread>,
    val replies: Map<Long, List<DmMessage>>,
)

/**
 * Appends an outgoing message. Threads are matched by handle across the seed
 * and local lists; a brand-new handle gets its own thread id so the DM list
 * and thread view can look it up by id everywhere (A-12).
 */
fun appendLocalDm(
    seedThreads: List<DmThread>,
    localThreads: List<DmThread>,
    replies: Map<Long, List<DmMessage>>,
    handle: String,
    body: String,
    age: String = "now",
): LocalDmUpdate {
    val normalized = handle.trim().let { if (it.startsWith("@")) it else "@$it" }
    val existing = (seedThreads + localThreads)
        .firstOrNull { it.handle.equals(normalized, ignoreCase = true) }
    val message = DmMessage("you", body, age, mine = true)
    return if (existing != null) {
        LocalDmUpdate(
            threads = localThreads,
            replies = replies + (existing.id to ((replies[existing.id] ?: emptyList()) + message)),
        )
    } else {
        val id = ((seedThreads + localThreads).maxOfOrNull { it.id } ?: 0L) + 1
        val thread = DmThread(id = id, with = normalized.trimStart('@'), handle = normalized)
        LocalDmUpdate(localThreads + thread, replies + (id to listOf(message)))
    }
}

/** Every DM thread, seed first, then locally created ones. */
fun allDmThreads(seedThreads: List<DmThread>, localThreads: List<DmThread>): List<DmThread> =
    seedThreads + localThreads

/** Full message list for [thread], including locally appended replies by id. */
fun threadMessages(thread: DmThread, replies: Map<Long, List<DmMessage>>): List<DmMessage> =
    thread.messages + (replies[thread.id] ?: emptyList())

object TwitterCodec {

    fun encodeIds(ids: Set<Long>): String = ids.sorted().joinToString(",")

    fun decodeIds(blob: String?): Set<Long> =
        blob?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()

    fun encodeDraft(draft: String): String = esc(draft)

    fun decodeDraft(blob: String?): String = if (blob == null) "" else unesc(blob)

    private fun esc(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

    private fun unesc(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
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

object TwitterSeed {

    val user = "you"
    val handle = "@you"

    val tweets: List<Tweet> = listOf(
        Tweet(106, "field notes", "@fieldnotes", "listening room open until nine. bring a record, take a record. #latenight", "18m"),
        Tweet(105, "maria", "@maria", "rendered side b four times today. the tape hiss is staying in. it earns its place.", "42m", favorite = true),
        Tweet(104, "harbor radio", "@harborradio", "tonight: two hours of slow electronics and one very loud surprise. stream at https://radio.example/live", "2h"),
        Tweet(103, "sam", "@samtapes", "made a mix for the walk home. starts with rain, ends with brass. https://tapes.example/rain-brass", "4h", favorite = true),
        Tweet(102, "dana", "@dana", "record fair haul: three sleeves, one scratch, zero regrets. #recordfair", "7h"),
        Tweet(101, "riley", "@rileyreads", "reading week stack is taller than the shelf. accepting this as a lifestyle.", "yesterday"),
    )

    val replies: List<Tweet> = listOf(
        Tweet(206, "alex", "@alex", "@you agreed about side b — the long fade is the whole point", "31m"),
        Tweet(205, "maria", "@maria", "@you i kept the spoken bit on the cd version only", "1h"),
        Tweet(204, "sam", "@samtapes", "@you level is fine here, it was my old speakers", "3h"),
    )

    val following: List<String> = listOf("field notes", "harbor radio", "maria", "sam", "dana", "alex")
    val followers: List<String> = listOf("alex", "dana", "field notes", "jordan", "maria", "riley", "sam")

    val dms: List<DmThread> = listOf(
        DmThread(
            id = 301, with = "maria", handle = "@maria",
            messages = listOf(
                DmMessage("maria", "sent the stems, check the low end on track two", "09:02"),
                DmMessage("you", "got them — track two sits better already", "09:11", mine = true),
                DmMessage("maria", "knew it. the room was lying to me", "09:12"),
            ),
        ),
        DmThread(
            id = 302, with = "sam", handle = "@samtapes",
            messages = listOf(
                DmMessage("sam", "are you going to the fair on saturday?", "yesterday"),
                DmMessage("you", "planning on it, doors at ten", "yesterday", mine = true),
            ),
        ),
    )

    val profile: List<Tweet> = listOf(
        Tweet(401, "you", "@you", "making mixtapes for a device that no longer connects. #offline", "2h"),
        Tweet(402, "you", "@you", "the shelf is alphabetical again. for now.", "mon"),
    )
}
