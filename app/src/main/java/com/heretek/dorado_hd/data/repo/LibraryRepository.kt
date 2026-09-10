package com.heretek.dorado_hd.data.repo

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.heretek.dorado_hd.data.db.TrackEntity
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.model.Album
import com.heretek.dorado_hd.data.model.Artist
import com.heretek.dorado_hd.data.model.Genre
import com.heretek.dorado_hd.data.model.Playlist
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.data.scan.MediaLibraryScanner
import com.heretek.dorado_hd.data.scan.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val context: Context,
    private val db: DoradoDatabase,
) {
    val scanning = MutableStateFlow(false)
    val lastScanResult = MutableStateFlow<MediaLibraryScanner.ScanResult?>(null)
    val lastScanAt = MutableStateFlow(0L)
    val importing = MutableStateFlow(false)
    val lastImportResult = MutableStateFlow<ImportResult?>(null)

    fun tracks(): Flow<List<Track>> = db.trackDao().tracks().map { list -> list.map { it.toModel() } }
    fun albums(): Flow<List<Album>> = db.trackDao().albumRows().map { rows -> rows.map { it.toAlbum() } }
    fun artists(): Flow<List<Artist>> = db.trackDao().artistRows().map { rows -> rows.map { it.toArtist() } }
    fun genres(): Flow<List<Genre>> = db.trackDao().genreRows().map { rows -> rows.map { it.toGenre() } }
    fun playlists(): Flow<List<Playlist>> = db.playlistDao().playlists().map { rows -> rows.map { it.toPlaylist() } }

    suspend fun track(id: Long): Track? = db.trackDao().track(id)?.toModel()
    suspend fun tracksByAlbum(albumId: Long): List<Track> = db.trackDao().tracksByAlbum(albumId).map { it.toModel() }
    suspend fun tracksByArtist(artistId: Long): List<Track> = db.trackDao().tracksByArtist(artistId).map { it.toModel() }
    suspend fun tracksByGenre(genre: String): List<Track> = db.trackDao().tracksByGenre(genre).map { it.toModel() }
    suspend fun tracksInPlaylist(playlistId: Long): List<Track> = db.playlistDao().tracks(playlistId).map { it.toModel() }
    suspend fun recentlyAdded(limit: Int = 24): List<Track> = db.trackDao().recentlyAdded(limit).map { it.toModel() }
    suspend fun search(q: String): List<Track> = db.trackDao().search(q).map { it.toModel() }
    suspend fun albumsByArtist(artistId: Long): List<Album> = db.trackDao().albumRowsByArtist(artistId).map { it.toAlbum() }

    /** Artists sharing this artist's genres — the artist page's `related` pivot. */
    suspend fun relatedArtists(artistId: Long): List<Artist> {
        val genres = db.trackDao().tracksByArtist(artistId).map { it.genre }.distinct()
        if (genres.isEmpty()) return emptyList()
        return db.trackDao().relatedArtistRows(artistId, genres, 12).map { it.toArtist() }
    }

    suspend fun trackCount(): Int = db.trackDao().count()

    /** The path to the Room database on disk — surfaced in Settings > collection. */
    fun databasePath(): String = context.getDatabasePath("dorado_hd.db").absolutePath

    suspend fun refresh(): MediaLibraryScanner.ScanResult {
        scanning.value = true
        try {
            return MediaLibraryScanner(context, db).scan().also {
                lastScanResult.value = it
                lastScanAt.value = System.currentTimeMillis()
            }
        } finally {
            scanning.value = false
        }
    }

    /**
     * Recursively walk an SAF document tree the user picked, upserting every
     * audio file into the library. Persistent permission must already have
     * been granted by the caller (SettingsScreen takes it on launcher result).
     * The result also includes the tree root so callers can persist it.
     */
    suspend fun importTree(treeUri: Uri, takePersistablePermission: Boolean = true): ImportResult =
        withContext(Dispatchers.IO) {
            importing.value = true
            try {
                val resolver = context.contentResolver
                if (takePersistablePermission) {
                    runCatching { resolver.takePersistableUriPermission(treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                if (tree == null) {
                    ImportResult(scanned = 0, error = "could not open tree")
                } else {
                    val tracks = mutableListOf<TrackEntity>()
                    walkTree(tree, tracks)
                    if (tracks.isNotEmpty()) db.trackDao().upsertAll(tracks)
                    lastImportResult.value = ImportResult(scanned = tracks.size)
                    ImportResult(scanned = tracks.size)
                }
            } catch (e: Exception) {
                ImportResult(scanned = 0, error = e.message ?: "import failed")
            } finally {
                importing.value = false
            }
        }

    private fun walkTree(node: DocumentFile, into: MutableList<TrackEntity>) {
        if (node.isDirectory) {
            for (child in node.listFiles()) walkTree(child, into)
            return
        }
        val mime = node.type ?: ""
        if (!mime.startsWith("audio/")) return
        val uri = node.uri
        val (displayName, sizeBytes) = readSafMetadata(uri)
        val entity = buildSafTrackEntity(uri, displayName, sizeBytes) ?: return
        into += entity
    }

    private fun readSafMetadata(uri: Uri): Pair<String, Long> {
        var name = "imported"; var size = 0L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nIdx >= 0 && !c.isNull(nIdx)) name = c.getString(nIdx) ?: name
                    val sIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sIdx >= 0 && !c.isNull(sIdx)) size = c.getLong(sIdx)
                }
            }
        }
        return name to size
    }

    private fun buildSafTrackEntity(uri: Uri, displayName: String, sizeBytes: Long): TrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: displayName.substringBeforeLast('.', displayName)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown album"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.takeIf { it.isNotEmpty() } ?: "unknown"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            // Synthesize a stable mediaId from a 64-bit hash of the URI so
            // the SAF-imported tracks don't collide with MediaStore _IDs.
            val mediaId = stableIdFromUri(uri)
            val albumId = -kotlin.math.abs(album.lowercase().hashCode().toLong())
            val artistId = -kotlin.math.abs(artist.lowercase().hashCode().toLong())
            TrackEntity(
                mediaId = mediaId,
                title = title,
                artist = artist,
                artistId = artistId,
                album = album,
                albumId = albumId,
                genre = genre,
                durationMs = durationMs,
                dateAdded = System.currentTimeMillis() / 1000,
                trackNumber = 0,
                year = year,
                uri = uri.toString(),
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun stableIdFromUri(uri: Uri): Long {
        // 63-bit positive hash so SAF imports live in the Long ID space
        // without colliding with MediaStore IDs.
        val raw = uri.toString().hashCode().toLong()
        return raw and 0x7FFFFFFFFFFFFFFFL
    }

    suspend fun createPlaylist(name: String): Long = db.playlistDao().create(
        com.heretek.dorado_hd.data.db.PlaylistEntity(name = name, dateCreated = System.currentTimeMillis()),
    )

    suspend fun addToPlaylist(playlistId: Long, track: Track) {
        val max = db.playlistDao().maxPosition(playlistId) ?: -1
        db.playlistDao().addToPlaylist(
            com.heretek.dorado_hd.data.db.PlaylistTrackEntity(playlistId, track.mediaId, max + 1),
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long) = db.playlistDao().removeFromPlaylist(playlistId, mediaId)
    suspend fun deletePlaylist(playlistId: Long) = db.playlistDao().deletePlaylistAndMembers(playlistId)

    /** Result of a SAF tree import. */
    data class ImportResult(val scanned: Int, val error: String? = null) {
        val ok: Boolean get() = error == null
    }
}
