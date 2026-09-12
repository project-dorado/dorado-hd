package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.data.model.Track

/**
 * The device's read-only built-in playlists — `BuiltIn-FavoriteTracks`,
 * `BuiltIn-MostPlayedArtists`, `BuiltIn-RecentTracks` (parity audit §5).
 *
 * They are derived on demand from data Dorado-HD already owns: the tri-state
 * hearts ([Rating.HEART]), persisted play counts (`PlayCountStore`) and the
 * Quickplay play history. Nothing is persisted, so no schema change is needed.
 * The derivation is pure and unit-tested; the UI re-derives whenever the
 * playlists pivot opens (or its inputs change), which is the "refresh on open"
 * behavior.
 */
object BuiltInPlaylists {
    const val FAVORITE_TRACKS_ID = "builtin-favorite-tracks"
    const val MOST_PLAYED_ARTISTS_ID = "builtin-most-played-artists"
    const val RECENT_TRACKS_ID = "builtin-recent-tracks"

    const val FAVORITE_LIMIT = 50
    const val MOST_PLAYED_ARTIST_LIMIT = 10
    const val MOST_PLAYED_TRACK_LIMIT = 100
    const val RECENT_LIMIT = 25

    /** A resolved, playable built-in playlist. */
    data class BuiltInPlaylist(
        val id: String,
        val title: String,
        val subLabel: String,
        val tracks: List<Track>,
    )

    /**
     * Every hearted track, by title. The device's favorites list is defined by
     * the heart rating, not by play count.
     */
    fun favoriteTracks(
        library: List<Track>,
        ratings: Map<Long, Int>,
        limit: Int = FAVORITE_LIMIT,
    ): List<Track> =
        library
            .filter { ratings[it.mediaId] == Rating.HEART.value }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.mediaId }))
            .take(limit)

    /**
     * The tracks of the most-played artists, artist rank first then track play
     * count. Artists with no plays are excluded (the device playlist is
     * literally about most-played artists).
     */
    fun mostPlayedArtists(
        library: List<Track>,
        playCounts: Map<Long, Int>,
        artistLimit: Int = MOST_PLAYED_ARTIST_LIMIT,
        trackLimit: Int = MOST_PLAYED_TRACK_LIMIT,
    ): List<Track> {
        val byArtist = library.groupBy { it.artistId }
        val ranked = byArtist.entries
            .map { (artistId, tracks) ->
                ArtistPlays(
                    artistId = artistId,
                    name = tracks.firstOrNull()?.artist ?: "",
                    plays = tracks.sumOf { playCounts[it.mediaId] ?: 0 },
                )
            }
            .filter { it.plays > 0 }
            .sortedWith(
                compareByDescending<ArtistPlays> { it.plays }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.artistId },
            )
            .take(artistLimit)

        val ordered = ArrayList<Track>()
        for (artist in ranked) {
            val tracks = byArtist[artist.artistId].orEmpty().sortedWith(
                compareByDescending<Track> { playCounts[it.mediaId] ?: 0 }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.mediaId },
            )
            ordered += tracks
            if (ordered.size >= trackLimit) break
        }
        return ordered.take(trackLimit)
    }

    /**
     * The most recently played distinct tracks, newest first. [recentMediaIds]
     * is ordered newest → oldest (Quickplay history is already trimmed).
     */
    fun recentTracks(
        library: List<Track>,
        recentMediaIds: List<Long>,
        limit: Int = RECENT_LIMIT,
    ): List<Track> {
        val byId = library.associateBy { it.mediaId }
        val seen = HashSet<Long>()
        val out = ArrayList<Track>()
        for (id in recentMediaIds) {
            if (!seen.add(id)) continue
            val track = byId[id] ?: continue
            out += track
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * Resolves the three built-ins in device order. Empty lists are omitted so
     * the pivot never shows a dead row.
     */
    fun derive(
        library: List<Track>,
        ratings: Map<Long, Int>,
        playCounts: Map<Long, Int>,
        recentMediaIds: List<Long>,
    ): List<BuiltInPlaylist> {
        val favorites = favoriteTracks(library, ratings)
        val mostPlayed = mostPlayedArtists(library, playCounts)
        val recent = recentTracks(library, recentMediaIds)
        return buildList {
            if (favorites.isNotEmpty()) {
                add(
                    BuiltInPlaylist(
                        id = FAVORITE_TRACKS_ID,
                        title = "favorite tracks",
                        subLabel = "built-in · ${favorites.size} songs",
                        tracks = favorites,
                    ),
                )
            }
            if (mostPlayed.isNotEmpty()) {
                add(
                    BuiltInPlaylist(
                        id = MOST_PLAYED_ARTISTS_ID,
                        title = "most played artists",
                        subLabel = "built-in · ${mostPlayed.size} songs",
                        tracks = mostPlayed,
                    ),
                )
            }
            if (recent.isNotEmpty()) {
                add(
                    BuiltInPlaylist(
                        id = RECENT_TRACKS_ID,
                        title = "recent tracks",
                        subLabel = "built-in · ${recent.size} songs",
                        tracks = recent,
                    ),
                )
            }
        }
    }

    private data class ArtistPlays(val artistId: Long, val name: String, val plays: Int)
}
