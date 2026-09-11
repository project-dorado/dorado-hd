package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings

/**
 * Settings-gated entry point to the Dorado Cloud podcast directory
 * (`v1/directory/podcasts/search`, Podcast Index on the server). Returns null
 * whenever the cloud is disabled, unconfigured, or unreachable, so the podcast
 * search can degrade to the subscribed library and say so.
 *
 * The [settings] provider is a suspension function because the podcast screen
 * only observes the settings flow; reading it at search time avoids a stale
 * base URL. [httpFactory] is the test seam.
 */
class CloudPodcastDirectory(
    private val settings: suspend () -> DoradoSettings,
    private val httpFactory: (String) -> CloudHttp = { base -> HttpUrlConnectionCloudHttp(base) },
) {

    suspend fun isEnabled(): Boolean {
        val current = settings()
        return current.cloudEnabled && current.cloudBaseUrl.isNotBlank()
    }

    suspend fun search(query: String, limit: Int = 20): CloudPodcastSearch? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return null
        return DoradoCloudClient(httpFactory(current.cloudBaseUrl)).directoryPodcastSearch(q, limit)
    }
}
