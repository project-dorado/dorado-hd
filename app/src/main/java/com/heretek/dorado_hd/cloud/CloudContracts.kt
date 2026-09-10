package com.heretek.dorado_hd.cloud

/**
 * Kotlin mirrors of the Dorado Cloud contracts (DoradoCloud.Shared.Contracts).
 * Field names match the server's `JsonSerializerDefaults.Web` wire shape
 * (camelCase) so the desktop and the phone speak the same schema. These are
 * intentionally plain data classes — no framework, consistent with the rest of
 * the `net/` layer.
 */

data class CloudPing(val module: String, val status: String, val version: String)

data class CloudCatalogItem(
    val type: String,
    val mbid: String,
    val title: String,
    val artist: String,
    val date: String,
    val coverArtUrl: String?,
)

data class CloudCatalogSearch(
    val query: String,
    val type: String,
    val total: Int,
    val attribution: String,
    val items: List<CloudCatalogItem>,
)

data class CloudArtistDetail(
    val mbid: String,
    val name: String,
    val sortName: String,
    val country: String,
    val disambiguation: String,
    val coverArtUrl: String?,
)

data class CloudPodcastResult(
    val feedId: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String,
    val feedUrl: String,
    val categories: String,
    val language: String,
)

data class CloudPodcastSearch(
    val query: String,
    val total: Int,
    val configured: Boolean,
    val attribution: String,
    val items: List<CloudPodcastResult>,
)

data class CloudRadioStation(
    val stationId: String,
    val name: String,
    val url: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
)

data class CloudRadioSearch(
    val query: String,
    val total: Int,
    val configured: Boolean,
    val attribution: String,
    val items: List<CloudRadioStation>,
)

data class CloudQuickMixCandidate(
    val mbid: String,
    val name: String,
    val score: Double,
    val reasons: List<String>,
)

data class CloudQuickMix(
    val seed: String,
    val seedMbid: String?,
    val total: Int,
    val attribution: String,
    val items: List<CloudQuickMixCandidate>,
)

/** A signed application-update manifest (`UpdateManifest` on the server). */
data class CloudUpdateManifest(
    val app: String,
    val channel: String,
    val version: String,
    val url: String,
    val sha256: String,
    val publishedAt: String,
    val notes: String,
)

data class CloudUpdateRelease(
    val app: String,
    val channel: String,
    val version: String,
    val url: String,
    val sha256: String,
    val publishedAt: String,
    val notes: String,
    val signature: String,
    val algorithm: String,
)

data class CloudUpdateCheck(
    val app: String,
    val channel: String,
    val available: Boolean,
    val release: CloudUpdateRelease?,
    val note: String?,
)

data class CloudMe(
    val accountId: String?,
    val subject: String?,
    val name: String?,
    val email: String?,
)

data class CloudDevice(
    val id: String,
    val name: String,
    val platform: String,
    val serial: String?,
    val appVersion: String?,
    val createdAt: String,
    val lastSeenAt: String,
)

data class CloudSettings(val payloadJson: String, val version: Int, val updatedAt: String)

data class CloudProfile(
    val accountId: String,
    val handle: String,
    val displayName: String,
    val bio: String,
    val followers: Int,
    val following: Int,
    val activities: Int,
    val isFollowing: Boolean,
    val isBlocked: Boolean,
    val createdAt: String,
)

data class CloudActivity(
    val id: String,
    val handle: String,
    val kind: String,
    val payloadJson: String,
    val createdAt: String,
)

data class CloudBadge(
    val code: String,
    val name: String,
    val description: String,
    val earnedAt: String?,
)

data class CloudZuneCard(
    val handle: String,
    val displayName: String,
    val bio: String,
    val followers: Int,
    val following: Int,
    val activities: Int,
    val badges: List<CloudBadge>,
    val recent: List<CloudActivity>,
)
