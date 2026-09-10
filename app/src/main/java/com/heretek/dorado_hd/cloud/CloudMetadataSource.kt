package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings

/**
 * Cloud-first artist metadata hydration for the HD artist pages. Reads the
 * current [DoradoSettings] lazily (the user may enable/disable the cloud at
 * runtime) and returns null whenever the cloud is disabled, unconfigured, or
 * does not resolve the artist — so callers fall back to their local providers.
 *
 * The [httpFactory] seam lets tests inject a fake transport; production uses
 * [HttpUrlConnectionCloudHttp] against the configured base URL.
 */
class CloudMetadataSource(
    private val settings: () -> DoradoSettings,
    private val httpFactory: (String) -> CloudHttp = { base -> HttpUrlConnectionCloudHttp(base) },
) {
    private var cachedBaseUrl: String? = null
    private var cachedClient: DoradoCloudClient? = null

    fun isEnabled(): Boolean {
        val current = settings()
        return current.cloudEnabled && current.cloudBaseUrl.isNotBlank()
    }

    private fun client(): DoradoCloudClient? {
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return null
        if (cachedClient == null || cachedBaseUrl != current.cloudBaseUrl) {
            cachedBaseUrl = current.cloudBaseUrl
            cachedClient = DoradoCloudClient(httpFactory(current.cloudBaseUrl))
        }
        return cachedClient
    }

    /** Resolves the MusicBrainz artist id via cloud catalog search. */
    suspend fun artistMbid(artist: String): String? {
        val search = client()?.catalogSearch(artist, type = "artist", limit = 1) ?: return null
        return search.items.firstOrNull()?.mbid?.takeIf { it.isNotBlank() }
    }

    /** Fetches the artist's cover-art bytes from the cloud artwork CDN. */
    suspend fun artistImage(artist: String): ByteArray? {
        val mbid = artistMbid(artist) ?: return null
        return client()?.artworkFront(mbid, size = 500)?.takeIf { it.isNotEmpty() }
    }

    /** Returns the MusicBrainz disambiguation as a lightweight bio fallback. */
    suspend fun artistBio(artist: String): String? {
        val mbid = artistMbid(artist) ?: return null
        val detail = client()?.catalogArtist(mbid) ?: return null
        return detail.disambiguation.takeIf { it.isNotBlank() }
    }

    /** Publishes a listening activity so the desktop/web Zune Card stays live. */
    suspend fun recordListen(artist: String, track: String, album: String?): Boolean {
        val current = settings()
        val token = current.cloudAccessToken.takeIf { it.isNotBlank() } ?: return false
        val payload = CloudJson.write(
            buildMap {
                put("artist", artist)
                put("track", track)
                if (!album.isNullOrBlank()) put("album", album)
            },
        )
        return client()?.postActivity("listen", payload, token = token) != null
    }
}
