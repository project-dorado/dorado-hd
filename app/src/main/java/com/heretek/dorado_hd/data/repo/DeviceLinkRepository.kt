package com.heretek.dorado_hd.data.repo

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.sync.DeviceSnapshot
import com.heretek.dorado_hd.sync.DeviceTransport
import com.heretek.dorado_hd.sync.SimulatedDeviceTransport
import com.heretek.dorado_hd.sync.SyncCategoryType
import com.heretek.dorado_hd.sync.SyncInput
import com.heretek.dorado_hd.sync.SyncPhoto
import com.heretek.dorado_hd.sync.SyncPodcastEpisode
import com.heretek.dorado_hd.sync.SyncRuleSettings
import com.heretek.dorado_hd.sync.SyncTrack
import com.heretek.dorado_hd.sync.SyncVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** An item copied back from the linked device into the phone collection queue. */
data class PendingImport(
    val title: String,
    val category: SyncCategoryType,
    val devicePath: String,
    val sizeBytes: Long,
)

/**
 * Phone side of Device Link (M8.3). Owns the sync target transport and the
 * reverse-sync queue. The transport is simulated for now; M8.2b swaps in a LAN
 * implementation with no change to the Device view or this repository's API.
 */
class DeviceLinkRepository(private val context: Context) {

    val transport: DeviceTransport =
        SimulatedDeviceTransport("DORADO-HD", "Zune HD (linked)", 32L * 1024 * 1024 * 1024)

    private val _pendingImports = MutableStateFlow<List<PendingImport>>(emptyList())
    val pendingImports: StateFlow<List<PendingImport>> = _pendingImports.asStateFlow()

    fun snapshot(): DeviceSnapshot =
        DeviceSnapshot(transport.totalCapacityBytes, transport.systemBytes, transport.contents())

    fun copyBack(item: com.heretek.dorado_hd.sync.DeviceContentItem) {
        _pendingImports.update {
            it + PendingImport(item.title, item.category, item.devicePath, item.sizeBytes)
        }
    }

    fun clearPending() {
        _pendingImports.value = emptyList()
    }

    /** Snapshot of the phone library as a [SyncInput] for the sync engine. */
    suspend fun buildInput(
        library: LibraryRepository,
        quickplay: QuickplayRepository,
        podcasts: PodcastRepository,
    ): SyncInput = withContext(Dispatchers.IO) {
        val ratings = quickplay.ratings()
        val tracks = library.tracks().first().map { t ->
            SyncTrack(
                id = t.mediaId.toString(),
                title = t.title,
                artistName = t.artist,
                albumTitle = t.album,
                durationSeconds = t.durationMs / 1000,
                filePath = t.uri.toString(),
                rating = Rating.from(ratings[t.mediaId] ?: 0),
            )
        }
        val episodes = podcasts.episodesFlat().first().map { e ->
            SyncPodcastEpisode(
                id = e.episodeId.toString(),
                title = e.title,
                seriesTitle = e.feedTitle,
                publishedAtUtc = e.pubAt,
                durationSeconds = e.durationMs / 1000,
                audioUrl = e.enclosureUrl,
                isPlayed = e.played,
            )
        }
        SyncInput(tracks, queryVideos(), queryPhotos(), episodes)
    }

    private fun queryVideos(): List<SyncVideo> {
        val list = mutableListOf<SyncVideo>()
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
            ),
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT 500",
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list += SyncVideo(
                    id = id.toString(),
                    title = c.getString(titleCol) ?: "untitled",
                    filePath = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    sizeBytes = c.getLong(sizeCol),
                    addedAtUtc = c.getLong(addedCol) * 1000,
                )
            }
        }
        return list
    }

    private fun queryPhotos(): List<SyncPhoto> {
        val list = mutableListOf<SyncPhoto>()
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
            ),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 500",
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list += SyncPhoto(
                    id = id.toString(),
                    title = c.getString(nameCol) ?: "untitled",
                    filePath = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    folderPath = c.getString(bucketCol) ?: "",
                    sizeBytes = c.getLong(sizeCol),
                    addedAtUtc = c.getLong(addedCol) * 1000,
                )
            }
        }
        return list
    }
}

fun DoradoSettings.toSyncRuleSettings(): SyncRuleSettings = SyncRuleSettings(
    musicSyncRule = musicSyncRule,
    podcastSyncRule = podcastSyncRule,
    videoSyncRule = videoSyncRule,
    picturesSyncRule = picturesSyncRule,
)
