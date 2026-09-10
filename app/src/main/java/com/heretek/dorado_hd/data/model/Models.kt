package com.heretek.dorado_hd.data.model

import android.net.Uri

enum class Rating(val value: Int) {
    NONE(0), HEART(1), BROKEN(2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
    }
}

enum class RepeatMode { OFF, ALL, ONE }

enum class PinKind { TRACK, ALBUM, ARTIST, PLAYLIST, PICTURE, RADIO }

data class Track(
    val mediaId: Long,
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
    val uri: Uri,
) {
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}

data class Album(
    val albumId: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val dateAdded: Long,
) {
    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}

data class Artist(
    val artistId: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
)

data class Genre(
    val name: String,
    val trackCount: Int,
)

data class Playlist(
    val id: Long,
    val name: String,
    val dateCreated: Long,
    val trackCount: Int,
)
