package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings

/** A message in the signed-in user's cloud inbox (mirrors the server `InboxMessage` store). */
data class CloudInboxMessage(
    val id: String,
    val senderTag: String,
    val recipientTag: String,
    val subject: String,
    val body: String,
    val createdAt: Long,
)

/** The signed-in user's inbox as returned by `GET /v1/social/me/inbox`. */
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
 *  - inbox — `GET /v1/social/me/inbox` (bearer token; JSON, backed by the same
 *            store the legacy `inbox.zune.net` route writes).
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
     * The signed-in user's inbox. Null when disabled, signed out, or the
     * endpoint is unreachable; an empty list of messages when the server
     * answered with none (including accounts with no profile yet).
     */
    suspend fun inbox(limit: Int = 50): CloudInbox? {
        val auth = token() ?: return null
        val client = clientOrNull() ?: return null
        val messages = client.getInbox(limit = limit, token = auth) ?: return null
        return CloudInbox(messages.firstOrNull()?.recipientTag.orEmpty(), messages)
    }

    /**
     * Remote mark-read. The current dorado-cloud inbox is read-only (there is
     * no mark-read route), so this always reports false; the Room store owns
     * read state. Kept on the client so a future server route can be wired
     * without changing callers.
     */
    suspend fun markRead(id: String): Boolean = false
}
