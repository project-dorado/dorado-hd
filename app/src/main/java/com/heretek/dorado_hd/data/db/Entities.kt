package com.heretek.dorado_hd.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index("albumId"), Index("artistId"), Index("title")])
data class TrackEntity(
    @PrimaryKey val mediaId: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val genre: String,
    val durationMs: Long,
    val dateAdded: Long,
    val trackNumber: Int,
    val year: String,
    val uri: String,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateCreated: Long,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "mediaId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val mediaId: Long,
    val position: Int,
)

@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey val mediaId: Long,
    val rating: Int,
)

@Entity(tableName = "pins")
data class PinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refId: Long,
    val label: String,
    val subLabel: String,
    val artAlbumId: Long,
    val pinnedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refId: Long,
    val label: String,
    val subLabel: String,
    val artAlbumId: Long,
    val playedAt: Long,
)

@Entity(tableName = "artist_images")
data class ArtistImageEntity(
    @PrimaryKey val artistKey: String,
    val imagePath: String,
    val fetchedAt: Long,
)

/** Mini-app notes (Room CRUD for the Notes app, canon §8). */
@Entity(tableName = "notes", indices = [Index("modifiedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val modifiedAt: Long,
)

/** Calendar appointment (the Calendar mini-app). */
@Entity(tableName = "appointments", indices = [Index("startAt")])
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String?,
    val startAt: Long,
    val endAt: Long?,
)

/** Alarm clock entry (AlarmManager-backed). */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    /** 0 = playlist/track ref, 1 = radio station ref. */
    val alarmKind: Int,
    val refId: Long,
    /** Bitmask Sun..Sat (0..127). 0 = once. */
    val daysOfWeek: Int,
)

/** Saved radio station (the dial UI). */
@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** In kHz (e.g. 98700 for 98.7 MHz). 0 when stream-only. */
    val frequencyKhz: Int,
    val streamUrl: String,
    val isPreset: Boolean,
    val lastPlayedAt: Long,
)

/** RSS feed subscribed in the Podcasts app. */
@Entity(tableName = "podcast_feeds", indices = [Index(value = ["feedUrl"], unique = true)])
data class PodcastFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val feedUrl: String,
    val artworkUrl: String,
    val description: String,
    val subscribedAt: Long,
)

/** Episode of a podcast feed. */
@Entity(
    tableName = "podcast_episodes",
    indices = [Index("feedId"), Index("pubAt")],
)
data class PodcastEpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val title: String,
    val pubAt: Long,
    val durationMs: Long,
    val enclosureUrl: String,
    val played: Boolean,
    val positionMs: Long,
)

/** Read-only projection: episode + its parent feed title, for the "episodes" pivot. */
data class PodcastEpisodeFlat(
    val episodeId: Long,
    val feedId: Long,
    val title: String,
    val pubAt: Long,
    val durationMs: Long,
    val enclosureUrl: String,
    val played: Boolean,
    val positionMs: Long,
    val feedTitle: String,
)

/** Mini-game high score (solitaire, sudoku, hexic, reversi). */
@Entity(tableName = "game_scores", indices = [Index("game"), Index("playedAt")])
data class GameScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: String,
    val score: Int,
    val meta: String?,
    val playedAt: Long,
)
