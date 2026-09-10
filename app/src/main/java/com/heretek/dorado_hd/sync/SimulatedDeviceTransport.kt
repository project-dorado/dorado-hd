package com.heretek.dorado_hd.sync

import java.util.UUID
import kotlin.math.max

/**
 * In-memory device content store with plausible defaults (32 GB Zune HD, some
 * pre-synced content). 1:1 with the desktop `SimulatedDeviceTransport` so the
 * phone-side Device view and the dry-run manifest behave identically before a
 * real transport exists.
 */
class SimulatedDeviceTransport(
    override val deviceSerialNumber: String,
    name: String,
    capacityBytes: Long,
) : DeviceTransport {

    override val deviceName: String = name.ifBlank { DEFAULT_NAME }
    override val totalCapacityBytes: Long = if (capacityBytes <= 0) DEFAULT_CAPACITY else capacityBytes
    override val systemBytes: Long = (totalCapacityBytes * 0.035).toLong()

    private val items = LinkedHashMap<String, DeviceContentItem>()

    init {
        if (totalCapacityBytes >= FOUR_GB) seedDefaultContent()
    }

    override fun contents(): List<DeviceContentItem> =
        items.values.sortedWith(compareBy({ it.category.ordinal }, { it.title }))

    override fun tryGetItem(entityId: String): DeviceContentItem? = items[entityId]

    override fun copyToDevice(item: TransferItem) {
        items[item.entityId] = DeviceContentItem(
            category = item.category,
            entityId = item.entityId,
            title = item.title,
            devicePath = "\\Content\\${item.category}\\${item.entityId}",
            sizeBytes = item.sizeBytes,
        )
    }

    override fun removeFromDevice(item: DeviceContentItem) {
        items.remove(item.entityId)
    }

    override val usedBytes: Long
        get() = systemBytes + items.values.sumOf { it.sizeBytes }

    override val freeBytes: Long
        get() = max(0L, totalCapacityBytes - usedBytes)

    /** Two stale music items plus a podcast, a video and a photo. */
    private fun seedDefaultContent() {
        listOf(
            DeviceContentItem(SyncCategoryType.MUSIC, UUID.randomUUID().toString(), "Ice Ice Baby", "\\Content\\Music\\legacy1", 4_800_000),
            DeviceContentItem(SyncCategoryType.MUSIC, UUID.randomUUID().toString(), "Macarena", "\\Content\\Music\\legacy2", 4_100_000),
            DeviceContentItem(SyncCategoryType.PODCASTS, UUID.randomUUID().toString(), "KEXP — Music That Matters 512", "\\Content\\Podcasts\\kexp512", 58_000_000),
            DeviceContentItem(SyncCategoryType.VIDEOS, UUID.randomUUID().toString(), "Zune Marketing Reel 2006", "\\Content\\Videos\\reel", 412_000_000),
            DeviceContentItem(SyncCategoryType.PICTURES, UUID.randomUUID().toString(), "Wallpaper", "\\Content\\Pictures\\wallpaper", 2_400_000),
        ).forEach { items[it.entityId] = it }
    }

    companion object {
        private const val DEFAULT_NAME = "Zune HD 32GB"
        private const val DEFAULT_CAPACITY = 32L * 1024 * 1024 * 1024
        private const val FOUR_GB = 4L * 1024 * 1024 * 1024
    }
}
