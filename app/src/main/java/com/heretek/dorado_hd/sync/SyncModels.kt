package com.heretek.dorado_hd.sync

import com.heretek.dorado_hd.data.model.Rating

/**
 * Kotlin mirror of the sibling Dorado desktop sync contract
 * (`Dorado.Domain/Models/SyncModels.cs`). Field names and semantics are kept
 * 1:1 so the phone (device) and desktop (source) compute identical plans;
 * M8 adds the transport + protocol on top of this pure-JVM core.
 *
 * entity ids are opaque strings here; the desktop uses `Guid`, the phone will
 * use its MediaStore ids rendered canonically.
 */

enum class SyncMode { AUTOMATIC, SELECTED_ITEMS, MANUAL }

enum class SyncCategoryType { MUSIC, PODCASTS, VIDEOS, PICTURES }

enum class TransferAction { ADD, REMOVE, KEEP }

/** One persisted sync rule per category (ZMDB SchemaSyncGroup parity). */
data class SyncCategoryRule(
    val category: SyncCategoryType,
    val mode: SyncMode,
    val ruleText: String = "",
    val newestCount: Int? = null,
    val preferFavorites: Boolean = false,
)

/** A persisted sync group per device; guest sessions are add-only. */
data class SyncGroup(
    val deviceSerialNumber: String,
    val name: String = "",
    val isGuestSession: Boolean = false,
    val categories: List<SyncCategoryRule> = emptyList(),
)

data class TransferItem(
    val action: TransferAction,
    val category: SyncCategoryType,
    val entityId: String,
    val title: String,
    val sourcePath: String = "",
    val sizeBytes: Long = 0,
    val detail: String? = null,
)

data class DeviceContentItem(
    val category: SyncCategoryType,
    val entityId: String,
    val title: String,
    val devicePath: String = "",
    val sizeBytes: Long = 0,
    val durationSeconds: Long? = null,
    val playCount: Int? = null,
)

/** The computed difference between a group's rules and the device contents. */
data class SyncPlan(
    val deviceSerialNumber: String,
    val isGuestSession: Boolean = false,
    val items: List<TransferItem> = emptyList(),
) {
    val addCount: Int get() = items.count { it.action == TransferAction.ADD }
    val removeCount: Int get() = items.count { it.action == TransferAction.REMOVE }
    val keepCount: Int get() = items.count { it.action == TransferAction.KEEP }

    val totalAddBytes: Long get() = items.filter { it.action == TransferAction.ADD }.sumOf { it.sizeBytes }
    val totalRemoveBytes: Long get() = items.filter { it.action == TransferAction.REMOVE }.sumOf { it.sizeBytes }

    fun addBytesFor(category: SyncCategoryType): Long =
        items.filter { it.action == TransferAction.ADD && it.category == category }.sumOf { it.sizeBytes }

    fun removeBytesFor(category: SyncCategoryType): Long =
        items.filter { it.action == TransferAction.REMOVE && it.category == category }.sumOf { it.sizeBytes }
}

/** The library snapshot the rules are evaluated against. */
data class SyncInput(
    val tracks: List<SyncTrack> = emptyList(),
    val videos: List<SyncVideo> = emptyList(),
    val photos: List<SyncPhoto> = emptyList(),
    val podcastEpisodes: List<SyncPodcastEpisode> = emptyList(),
)

data class SyncTrack(
    val id: String,
    val title: String,
    val artistName: String = "",
    val albumTitle: String = "",
    val durationSeconds: Long = 0,
    val filePath: String = "",
    val rating: Rating = Rating.NONE,
)

data class SyncVideo(
    val id: String,
    val title: String,
    val filePath: String = "",
    val sizeBytes: Long = 0,
    val addedAtUtc: Long = 0,
)

data class SyncPhoto(
    val id: String,
    val title: String,
    val filePath: String = "",
    val folderPath: String = "",
    val sizeBytes: Long = 0,
    val addedAtUtc: Long = 0,
)

data class SyncPodcastEpisode(
    val id: String,
    val title: String,
    val seriesTitle: String = "",
    val publishedAtUtc: Long = 0,
    val durationSeconds: Long = 0,
    val audioUrl: String = "",
    val isPlayed: Boolean = false,
)

/**
 * The device side of the plan computation. A real transport (MTPZ, or the
 * phone's own store during LAN sync) supplies the same three values.
 */
data class DeviceSnapshot(
    val totalCapacityBytes: Long,
    val systemBytes: Long = 0,
    val contents: List<DeviceContentItem> = emptyList(),
)

/** The four Zune device sync rules, mirroring `AppSettings` on the desktop. */
data class SyncRuleSettings(
    val musicSyncRule: String = "All Music (Automatic Sync)",
    val podcastSyncRule: String = "3 Newest Episodes",
    val videoSyncRule: String = "All Videos & Pictures",
    val picturesSyncRule: String = "Newest 25 Items",
)
