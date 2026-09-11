package com.heretek.dorado_hd.net

import com.heretek.dorado_hd.cloud.CloudCatalogItem
import com.heretek.dorado_hd.cloud.CloudJson

/** Where an artist photo came from, in the order the photos pivot prefers. */
enum class ArtistPhotoSource { CLOUD, COVER_ART, WALLPAPER }

/** One full-resolution artist photo for the detail pivot's grid. */
data class ArtistPhoto(val url: String, val source: ArtistPhotoSource)

/**
 * Pure selection/parsing rules for the artist `photos` pivot (M13/B5). The
 * device showed a band-photo grid pulled from catalog.zune.net; with that host
 * gone the pivot degrades through three sources, preferring the configured
 * Dorado Cloud artwork module, then the MusicBrainz / Cover Art Archive
 * release-group covers, and finally the single cached wallpaper so the pivot is
 * never empty.
 *
 * Kept free of Android/network types so the rules are unit-testable.
 */
object ArtistPhotos {
    const val MAX_PHOTOS = 12
    const val COVER_ART_SIZE = 500
    private const val COVER_ART_BASE = "https://coverartarchive.org"

    /**
     * Picks the non-empty source with the highest priority, drops blanks,
     * de-duplicates and caps the grid. Never mixes sources in one grid.
     */
    fun select(
        cloud: List<String>,
        coverArt: List<String>,
        wallpaper: String?,
    ): List<ArtistPhoto> {
        val (urls, source) = when {
            cloud.any { it.isNotBlank() } -> cloud to ArtistPhotoSource.CLOUD
            coverArt.any { it.isNotBlank() } -> coverArt to ArtistPhotoSource.COVER_ART
            !wallpaper.isNullOrBlank() -> listOf(wallpaper) to ArtistPhotoSource.WALLPAPER
            else -> return emptyList()
        }
        return urls.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PHOTOS)
            .map { ArtistPhoto(it, source) }
            .toList()
    }

    /**
     * Absolute URLs into the Dorado Cloud artwork module for the artist's
     * release-group covers. Catalog search may return records by other artists
     * for a loose query, so only entries whose credit mentions the artist pass.
     */
    fun cloudCoverUrls(
        items: List<CloudCatalogItem>,
        artist: String,
        baseUrl: String,
        size: Int = COVER_ART_SIZE,
    ): List<String> {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) return emptyList()
        val wanted = artist.trim()
        return items.asSequence()
            .filter { it.mbid.isNotBlank() }
            .filter { wanted.isEmpty() || it.artist.contains(wanted, ignoreCase = true) }
            .map { "$root/v1/artwork/front/${it.mbid}?size=$size" }
            .distinct()
            .toList()
    }

    /**
     * Release-group ids out of a MusicBrainz
     * `ws/2/release-group?artist={mbid}` response. Parsing the typed array (not
     * a UUID regex) keeps artist-credit ids out of the grid.
     */
    fun releaseGroupIds(musicBrainzJson: String): List<String> {
        if (musicBrainzJson.isBlank()) return emptyList()
        val root = runCatching {
            CloudJson.asObject(CloudJson.parse(musicBrainzJson))
        }.getOrNull() ?: return emptyList()
        return CloudJson.asArray(root["release-groups"])
            .mapNotNull { group ->
                CloudJson.string(CloudJson.asObject(group), "id")?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .toList()
    }

    /** Cover Art Archive front covers (`/front-500`) for release-group MBIDs. */
    fun coverArtUrls(ids: List<String>, size: Int = COVER_ART_SIZE): List<String> =
        ids.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { "$COVER_ART_BASE/release-group/$it/front-$size" }
            .toList()
}
