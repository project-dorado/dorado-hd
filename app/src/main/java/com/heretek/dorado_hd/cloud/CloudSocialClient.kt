package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

/** A legacy Zune inbox message (mirrors the server `InboxMessage` store). */
data class CloudInboxMessage(
    val id: String,
    val senderTag: String,
    val recipientTag: String,
    val subject: String,
    val body: String,
    val createdAt: Long,
)

/** The signed-in user's inbox as returned by the legacy `inbox.zune.net` route. */
data class CloudInbox(
    val zuneTag: String,
    val messages: List<CloudInboxMessage>,
)

/**
 * Settings-gated, local-first entry point to the Dorado Cloud social surface
 * (roadmap M5/D2). It wraps [DoradoCloudClient] and reads the current
 * [DoradoSettings] lazily so runtime enable/disable and sign-in are honoured.
 *
 * Endpoints (from `dorado-cloud`, verified against the server routes):
 *  - feed  — `GET /v1/social/me/feed`               (bearer token)
 *  - card  — `GET /v1/social/profiles/{handle}/zunecard` (public, handle via
 *            `GET /v1/social/profiles/me`)
 *  - inbox — `GET /messaging/{zuneTag}/inbox` (the legacy `inbox.zune.net`
 *            Atom feed; the modern API exposes no JSON inbox yet).
 *
 * Every method returns null when the cloud is disabled, signed out, or
 * unreachable, so callers degrade to the local Zune Card and the cached inbox.
 * [httpFactory] is the test seam.
 */
class CloudSocialClient(
    private val settings: () -> DoradoSettings,
    private val httpFactory: (String) -> CloudHttp = { base -> HttpUrlConnectionCloudHttp(base) },
) {

    fun isEnabled(): Boolean {
        val current = settings()
        return current.cloudEnabled && current.cloudBaseUrl.isNotBlank()
    }

    /** Signed-in (bearer available) and otherwise configured. */
    fun isSignedIn(): Boolean = isEnabled() && !settings().cloudAccessToken.isBlank()

    private fun token(): String? = settings().cloudAccessToken.takeIf { it.isNotBlank() }

    private fun clientOrNull(): DoradoCloudClient? {
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return null
        return DoradoCloudClient(httpFactory(current.cloudBaseUrl))
    }

    /** The signed-in user's activity feed; null when unavailable. */
    suspend fun feed(limit: Int = 30): List<CloudActivity>? =
        clientOrNull()?.getFeed(limit = limit, offset = 0, token = token())

    /** Public Zune Card stats for an explicit handle; null when unavailable. */
    suspend fun card(handle: String): CloudZuneCard? {
        if (handle.isBlank()) return null
        return clientOrNull()?.getZuneCard(handle)
    }

    /**
     * The signed-in user's Zune Card. Resolves the handle through
     * `profiles/me`, then fetches the public card stats.
     */
    suspend fun myCard(): CloudZuneCard? {
        val auth = token() ?: return null
        val client = clientOrNull() ?: return null
        val handle = client.getMyProfile(auth)?.handle?.takeIf { it.isNotBlank() } ?: return null
        return client.getZuneCard(handle)
    }

    /**
     * The signed-in user's legacy inbox. Null when disabled/signed-out or the
     * Atom endpoint is unreachable; empty when the server answered with no
     * messages.
     */
    suspend fun inbox(limit: Int = 50): CloudInbox? {
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return null
        val auth = token() ?: return null
        val client = clientOrNull() ?: return null
        val handle = client.getMyProfile(auth)?.handle?.takeIf { it.isNotBlank() } ?: return null
        val response = withContext(Dispatchers.IO) {
            runCatching { httpFactory(current.cloudBaseUrl).get("messaging/$handle/inbox?limit=$limit", auth) }
                .getOrNull()
        } ?: return null
        if (!response.isSuccess) return null
        val messages = parseInboxAtom(response.body) ?: return null
        return CloudInbox(handle, messages)
    }

    /**
     * Remote mark-read. The current dorado-cloud inbox is read-only (there is
     * no mark-read route), so this always reports false; the Room store owns
     * read state. Kept on the client so a future server route can be wired
     * without changing callers.
     */
    suspend fun markRead(id: String): Boolean = false

    companion object {

        /** Parses the legacy inbox Atom feed. Null when the body is not a feed. */
        fun parseInboxAtom(xml: String): List<CloudInboxMessage>? = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
            }
            val doc = factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
            val entries = doc.getElementsByTagNameNS("*", "entry")
            (0 until entries.length).map { index ->
                val entry = entries.item(index) as Element
                CloudInboxMessage(
                    id = childText(entry, "id").ifBlank { "entry-$index" },
                    senderTag = childText(entry, "sender"),
                    recipientTag = childText(entry, "recipient"),
                    subject = childText(entry, "subject"),
                    body = childText(entry, "body"),
                    createdAt = parseTimestamp(childText(entry, "receivedAt")),
                )
            }
        }.getOrNull()

        private fun childText(parent: Element, localName: String): String {
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE && node.localName == localName) {
                    return node.textContent?.trim().orEmpty()
                }
            }
            return ""
        }

        private fun parseTimestamp(value: String): Long =
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
    }
}
