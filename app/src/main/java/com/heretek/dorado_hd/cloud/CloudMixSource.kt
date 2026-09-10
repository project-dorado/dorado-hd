package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings

/**
 * A source of "more like this" artist names for DynamicMix. Implemented by
 * [CloudMixSource]; a simple seam so the mix service can be tested without a
 * network.
 */
interface MixSuggester {
    fun isEnabled(): Boolean
    suspend fun similarArtists(seed: String, limit: Int = 20): List<String>
}

/**
 * Cloud-backed music discovery for DynamicMix: resolves "more like this" artist
 * names from the Dorado Cloud Recommendations (QuickMix) endpoint. Reads
 * settings lazily so the cloud can be toggled at runtime; returns nothing when
 * disabled or unreachable, so callers fall back to on-device similarity.
 */
class CloudMixSource(
    private val settings: () -> DoradoSettings,
    private val httpFactory: (String) -> CloudHttp = { base -> HttpUrlConnectionCloudHttp(base) },
) : MixSuggester {
    private var cachedBaseUrl: String? = null
    private var cachedClient: DoradoCloudClient? = null

    override fun isEnabled(): Boolean {
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

    /** Recommended artist names for a seed (artist name or MBID). */
    override suspend fun similarArtists(seed: String, limit: Int): List<String> {
        if (seed.isBlank()) return emptyList()
        val response = client()?.quickMix(seed, limit) ?: return emptyList()
        return response.items
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
