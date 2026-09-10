package com.heretek.dorado_hd.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.heretek.dorado_hd.data.db.TrackEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore-backed library scanner. Mirrors the sync model of the Zune
 * family: the device library is rebuilt from the host's collection
 * (here: MediaStore instead of the desktop sync engine).
 */
class MediaLibraryScanner(
    private val context: Context,
    private val db: DoradoDatabase,
) {
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val found = mutableListOf<TrackEntity>()

        // ARTIST_ID landed in API 29 and GENRE in API 30; projecting unknown
        // columns throws on older devices, so the projection is built
        // conditionally and the missing data is derived instead.
        val sdk = android.os.Build.VERSION.SDK_INT
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        if (sdk >= 29) projection += MediaStore.Audio.Media.ARTIST_ID
        if (sdk >= 30) projection += MediaStore.Audio.Media.GENRE

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000",
            null,
            null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iArtistId = if (sdk >= 29) c.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID) else -1
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iGenre = if (sdk >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndex(MediaStore.Audio.Media.YEAR)

            while (c.moveToNext()) {
                val mediaId = c.getLong(iId)
                val title = c.getString(iTitle)?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown title"
                val artist = c.getString(iArtist)?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown artist"
                val album = c.getString(iAlbum)?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown album"
                val genre = if (iGenre >= 0) c.getString(iGenre)?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown" else "unknown"
                val year = if (iYear >= 0) c.getString(iYear)?.takeUnless { it.isNullOrEmpty() } ?: "unknown" else "unknown"
                val trackNo = c.getInt(iTrack) % 1000

                // Below API 29 group artists by name with a stable derived id.
                val artistId = when {
                    iArtistId >= 0 -> c.getLong(iArtistId)
                    else -> -kotlin.math.abs(artist.lowercase().hashCode().toLong())
                }

                found += TrackEntity(
                    mediaId = mediaId,
                    title = title,
                    artist = artist,
                    artistId = artistId,
                    album = album,
                    albumId = c.getLong(iAlbumId),
                    genre = genre,
                    durationMs = c.getLong(iDur),
                    dateAdded = c.getLong(iDate),
                    trackNumber = trackNo,
                    year = year,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString(),
                )
            }
        }

        val removed: Int
        val foundIds = found.map { it.mediaId }.toHashSet()
        db.openHelper.readableDatabase.query("SELECT mediaId FROM tracks").use { cursor ->
            val stale = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (id !in foundIds) stale += id
            }
            if (stale.isNotEmpty()) db.trackDao().deleteByIds(stale)
            removed = stale.size
        }

        db.trackDao().upsertAll(found)
        ScanResult(found.size, removed)
    }

    data class ScanResult(val scanned: Int, val removed: Int)
}

fun TrackEntity.toModel(): com.heretek.dorado_hd.data.model.Track =
    com.heretek.dorado_hd.data.model.Track(
        mediaId = mediaId,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        genre = genre,
        durationMs = durationMs,
        dateAdded = dateAdded,
        trackNumber = trackNumber,
        year = year,
        uri = Uri.parse(uri),
    )
