package com.heretek.dorado_hd.ui.screens

import com.heretek.dorado_hd.cloud.CloudPodcastResult
import com.heretek.dorado_hd.data.db.PodcastEpisodeFlat
import com.heretek.dorado_hd.data.db.PodcastFeedEntity

/**
 * Pure filter/merge rules behind the podcast pivot's on-device search (M5).
 *
 * Source one is the subscribed library: episodes whose title *or* show title
 * matches, newest first (the DAO already orders by `pubAt DESC`). Source two
 * is the Dorado Cloud directory of shows — hits are tagged when the user is
 * already subscribed so tapping can skip the re-fetch. Kept free of Compose so
 * the rules are unit-testable without Robolectric.
 */
object PodcastSearch {
    const val LOCAL_LIMIT = 50

    /** A cloud directory hit plus whether this device already subscribes. */
    data class CloudHit(val feed: CloudPodcastResult, val subscribed: Boolean)

    /** Case-insensitive match on episode or show title, de-duplicated and capped. */
    fun filterLocal(
        episodes: List<PodcastEpisodeFlat>,
        query: String,
        limit: Int = LOCAL_LIMIT,
    ): List<PodcastEpisodeFlat> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return episodes.asSequence()
            .filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.feedTitle.contains(q, ignoreCase = true)
            }
            .distinctBy { it.episodeId }
            .take(limit)
            .toList()
    }

    /** Feed-URL identity for the "already subscribed" check. */
    fun normalizeFeedUrl(url: String): String =
        url.trim().lowercase().removeSuffix("/")

    fun subscribedFeedUrls(feeds: List<PodcastFeedEntity>): Set<String> =
        feeds.asSequence()
            .map { normalizeFeedUrl(it.feedUrl) }
            .filter { it.isNotEmpty() }
            .toSet()

    fun markSubscribed(
        results: List<CloudPodcastResult>,
        subscribedUrls: Set<String>,
    ): List<CloudHit> = results.map { hit ->
        CloudHit(hit, normalizeFeedUrl(hit.feedUrl) in subscribedUrls)
    }
}
