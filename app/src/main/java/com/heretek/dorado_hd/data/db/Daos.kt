package com.heretek.dorado_hd.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.heretek.dorado_hd.data.model.Album
import com.heretek.dorado_hd.data.model.Artist
import com.heretek.dorado_hd.data.model.Genre
import kotlinx.coroutines.flow.Flow

data class AlbumRow(
    val albumId: Long,
    val album: String,
    val artist: String,
    val artistId: Long,
    val year: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val dateAdded: Long,
) {
    fun toAlbum() = Album(albumId, album, artist, artistId, year, trackCount, totalDurationMs, dateAdded)
}

data class ArtistRow(
    val artistId: Long,
    val artist: String,
    val albumCount: Int,
    val trackCount: Int,
) {
    fun toArtist() = Artist(artistId, artist, albumCount, trackCount)
}

data class GenreRow(val genre: String, val trackCount: Int) {
    fun toGenre() = Genre(genre, trackCount)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun tracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE mediaId = :id")
    suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE mediaId IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY trackNumber, title COLLATE NOCASE")
    suspend fun tracksByAlbum(albumId: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY album COLLATE NOCASE, trackNumber")
    suspend fun tracksByArtist(artistId: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY album COLLATE NOCASE, trackNumber")
    suspend fun tracksByGenre(genre: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC LIMIT :limit")
    suspend fun recentlyAdded(limit: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' COLLATE NOCASE " +
            "OR artist LIKE '%' || :q || '%' COLLATE NOCASE " +
            "OR album LIKE '%' || :q || '%' COLLATE NOCASE " +
            "ORDER BY title COLLATE NOCASE LIMIT 100"
    )
    suspend fun search(q: String): List<TrackEntity>

    @Query(
        "SELECT albumId AS albumId, album AS album, artist AS artist, artistId AS artistId, " +
            "MAX(year) AS year, COUNT(*) AS trackCount, SUM(durationMs) AS totalDurationMs, " +
            "MAX(dateAdded) AS dateAdded FROM tracks GROUP BY albumId ORDER BY album COLLATE NOCASE"
    )
    fun albumRows(): Flow<List<AlbumRow>>

    @Query(
        "SELECT albumId AS albumId, album AS album, artist AS artist, artistId AS artistId, " +
            "MAX(year) AS year, COUNT(*) AS trackCount, SUM(durationMs) AS totalDurationMs, " +
            "MAX(dateAdded) AS dateAdded FROM tracks WHERE artistId = :artistId " +
            "GROUP BY albumId ORDER BY year, album COLLATE NOCASE"
    )
    suspend fun albumRowsByArtist(artistId: Long): List<AlbumRow>

    @Query(
        "SELECT artistId AS artistId, artist AS artist, COUNT(DISTINCT albumId) AS albumCount, " +
            "COUNT(*) AS trackCount FROM tracks GROUP BY artistId ORDER BY artist COLLATE NOCASE"
    )
    fun artistRows(): Flow<List<ArtistRow>>

    @Query(
        "SELECT artistId AS artistId, artist AS artist, COUNT(DISTINCT albumId) AS albumCount, " +
            "COUNT(*) AS trackCount FROM tracks WHERE genre IN (:genres) AND artistId != :excludeArtistId " +
            "GROUP BY artistId ORDER BY trackCount DESC, artist COLLATE NOCASE LIMIT :limit"
    )
    suspend fun relatedArtistRows(excludeArtistId: Long, genres: List<String>, limit: Int): List<ArtistRow>

    @Query("SELECT genre AS genre, COUNT(*) AS trackCount FROM tracks GROUP BY genre ORDER BY genre COLLATE NOCASE")
    fun genreRows(): Flow<List<GenreRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT DISTINCT artist FROM tracks ORDER BY artist COLLATE NOCASE")
    suspend fun distinctArtistNames(): List<String>
}

@Dao
interface RatingDao {
    @Query("SELECT rating FROM ratings WHERE mediaId = :id")
    fun ratingOf(id: Long): Flow<Int?>

    @Query("SELECT * FROM ratings WHERE rating = :rating")
    suspend fun tracksWithRating(rating: Int): List<RatingEntity>

    @Query("SELECT * FROM ratings")
    suspend fun all(): List<RatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: RatingEntity)

    @Query("DELETE FROM ratings WHERE mediaId = :id")
    suspend fun clear(id: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT id, name, dateCreated, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount FROM playlists p ORDER BY name COLLATE NOCASE")
    fun playlists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: Long): PlaylistEntity?

    @Query(
        "SELECT t.* FROM tracks t JOIN playlist_tracks pt ON pt.mediaId = t.mediaId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position"
    )
    suspend fun tracks(playlistId: Long): List<TrackEntity>

    @Insert
    suspend fun create(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteMemberships(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(item: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long)

    @Transaction
    suspend fun deletePlaylistAndMembers(id: Long) {
        deleteMemberships(id)
        delete(id)
    }

    data class PlaylistRow(val id: Long, val name: String, val dateCreated: Long, val trackCount: Int) {
        fun toPlaylist() = com.heretek.dorado_hd.data.model.Playlist(id, name, dateCreated, trackCount)
    }
}

@Dao
interface PinDao {
    @Query("SELECT * FROM pins ORDER BY pinnedAt DESC")
    fun pins(): Flow<List<PinEntity>>

    @Insert
    suspend fun pin(entity: PinEntity)

    @Query("DELETE FROM pins WHERE kind = :kind AND refId = :refId")
    suspend fun unpin(kind: String, refId: Long)

    @Query("SELECT COUNT(*) FROM pins WHERE kind = :kind AND refId = :refId")
    suspend fun isPinned(kind: String, refId: Long): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface ArtistImageDao {
    @Query("SELECT * FROM artist_images WHERE artistKey = :key")
    suspend fun get(key: String): ArtistImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ArtistImageEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY modifiedAt DESC")
    fun all(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY startAt")
    fun all(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE startAt BETWEEN :from AND :to ORDER BY startAt")
    suspend fun inRange(from: Long, to: Long): List<AppointmentEntity>

    @Insert
    suspend fun insert(a: AppointmentEntity): Long

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun all(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(a: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET hour = :hour, minute = :minute WHERE id = :id")
    suspend fun setTime(id: Long, hour: Int, minute: Int)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RadioDao {
    @Query("SELECT * FROM radio_stations ORDER BY isPreset DESC, frequencyKhz")
    fun all(): Flow<List<RadioStationEntity>>

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun count(): Int

    @Insert
    suspend fun insert(s: RadioStationEntity): Long

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE radio_stations SET lastPlayedAt = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long)
}

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcast_feeds ORDER BY subscribedAt DESC")
    fun feeds(): Flow<List<PodcastFeedEntity>>

    @Query("SELECT * FROM podcast_feeds WHERE id = :id")
    suspend fun feed(id: Long): PodcastFeedEntity?

    @Insert
    suspend fun insertFeed(f: PodcastFeedEntity): Long

    @Query("DELETE FROM podcast_feeds WHERE id = :id")
    suspend fun deleteFeed(id: Long)

    @Query("SELECT * FROM podcast_episodes WHERE feedId = :feedId ORDER BY pubAt DESC")
    fun episodesFor(feedId: Long): Flow<List<PodcastEpisodeEntity>>

    /**
     * Flat list of every episode across all feeds, joined with the feed
     * title — used by the Podcasts screen's "episodes" pivot (canon §3.6).
     */
    @Query(
        "SELECT e.id AS episodeId, e.feedId, e.title, e.pubAt, e.durationMs, e.enclosureUrl, " +
            "e.played, e.positionMs, f.title AS feedTitle " +
            "FROM podcast_episodes e JOIN podcast_feeds f ON e.feedId = f.id " +
            "ORDER BY e.pubAt DESC"
    )
    fun episodesFlat(): Flow<List<com.heretek.dorado_hd.data.db.PodcastEpisodeFlat>>

    @Query("SELECT * FROM podcast_episodes WHERE id = :id")
    suspend fun episode(id: Long): PodcastEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(e: PodcastEpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(es: List<PodcastEpisodeEntity>)

    @Query("UPDATE podcast_episodes SET positionMs = :pos WHERE id = :id")
    suspend fun setPosition(id: Long, pos: Long)

    @Query("UPDATE podcast_episodes SET played = 1 WHERE id = :id")
    suspend fun markPlayed(id: Long)
}

@Dao
interface GameScoreDao {
    @Query("SELECT * FROM game_scores WHERE game = :game ORDER BY score DESC LIMIT :limit")
    fun top(game: String, limit: Int = 10): Flow<List<GameScoreEntity>>

    @Insert
    suspend fun insert(score: GameScoreEntity): Long
}

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE app = :app")
    suspend fun get(app: String): AppStateEntity?

    @Query("SELECT * FROM app_state WHERE app = :app")
    fun observe(app: String): Flow<AppStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: AppStateEntity)

    @Query("DELETE FROM app_state WHERE app = :app")
    suspend fun delete(app: String)
}

@Dao
interface TrackFeatureDao {
    @Query("SELECT * FROM track_features")
    suspend fun all(): List<TrackFeatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feature: TrackFeatureEntity)

    @Query("SELECT COUNT(*) FROM track_features")
    suspend fun count(): Int
}

@Dao
interface ScrobbleDao {
    @Insert
    suspend fun insert(row: ScrobbleEntity): Long

    @Query("SELECT * FROM scrobble_queue ORDER BY id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<ScrobbleEntity>

    @Query("DELETE FROM scrobble_queue WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM scrobble_queue")
    suspend fun count(): Int
}

@Dao
interface PlayCountDao {
    @Query("SELECT * FROM play_counts")
    suspend fun all(): List<PlayCountEntity>

    @Query("SELECT count FROM play_counts WHERE mediaId = :mediaId")
    suspend fun countFor(mediaId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PlayCountEntity)

    @Query("DELETE FROM play_counts")
    suspend fun clear()

    /** Atomic read-increment-write so concurrent transitions cannot lose a play. */
    @Transaction
    suspend fun increment(mediaId: Long, now: Long) {
        val next = (countFor(mediaId) ?: 0) + 1
        upsert(PlayCountEntity(mediaId = mediaId, count = next, lastPlayedAt = now))
    }
}
