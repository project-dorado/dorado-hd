package com.heretek.dorado_hd.sync

import com.heretek.dorado_hd.data.model.Rating

/**
 * 1:1 Kotlin port of the sibling Dorado desktop `SyncEngine` (Phase 9).
 * Turns Settings rules into a planned transfer set (add / remove / keep)
 * against the device's current contents. Guest sessions are add-only.
 *
 * Pure JVM: no Android dependencies, so it is exercised by golden tests
 * against fixtures generated from the desktop engine.
 */
object SyncEngine {

    /** Zune's ~128 kbps audio estimate when the exact size is unknown. */
    const val AUDIO_BYTES_PER_SECOND: Long = 16_000L

    /**
     * Builds the default group for a device from the persisted rule strings,
     * mirroring `SyncEngine.BuildDefaultGroup` exactly — including the quirk
     * that the picture rule inherits the video rule's mode.
     */
    fun buildDefaultGroup(
        deviceSerialNumber: String,
        settings: SyncRuleSettings,
        isGuestSession: Boolean = false,
    ): SyncGroup {
        val categories = mutableListOf<SyncCategoryRule>()

        // Music rule
        val musicMode = when {
            settings.musicSyncRule.contains("Manual", ignoreCase = true) -> SyncMode.MANUAL
            settings.musicSyncRule.contains("Selected", ignoreCase = true) -> SyncMode.SELECTED_ITEMS
            else -> SyncMode.AUTOMATIC
        }
        categories += SyncCategoryRule(
            category = SyncCategoryType.MUSIC,
            mode = musicMode,
            ruleText = settings.musicSyncRule,
            preferFavorites = musicMode == SyncMode.SELECTED_ITEMS,
        )

        // Podcast rule
        var podcastMode = SyncMode.SELECTED_ITEMS
        var podcastCount = parseTrailingCount(settings.podcastSyncRule)
        if (settings.podcastSyncRule.contains("Manual", ignoreCase = true)) {
            podcastMode = SyncMode.MANUAL
        } else if (settings.podcastSyncRule.startsWith("All", ignoreCase = true)) {
            podcastMode = SyncMode.AUTOMATIC
            podcastCount = null
        }
        categories += SyncCategoryRule(
            category = SyncCategoryType.PODCASTS,
            mode = podcastMode,
            ruleText = settings.podcastSyncRule,
            newestCount = if (podcastMode == SyncMode.SELECTED_ITEMS) podcastCount else null,
        )

        // Media rules: pictures share the video rule's mode (desktop parity).
        val mediaMode = when {
            settings.videoSyncRule.contains("Manual", ignoreCase = true) ||
                settings.videoSyncRule.contains("Nothing", ignoreCase = true) -> SyncMode.MANUAL
            settings.videoSyncRule.startsWith("All", ignoreCase = true) -> SyncMode.AUTOMATIC
            else -> SyncMode.SELECTED_ITEMS
        }
        categories += SyncCategoryRule(
            category = SyncCategoryType.VIDEOS,
            mode = mediaMode,
            ruleText = settings.videoSyncRule,
            newestCount = if (mediaMode == SyncMode.SELECTED_ITEMS) parseTrailingCount(settings.videoSyncRule) else null,
        )
        categories += SyncCategoryRule(
            category = SyncCategoryType.PICTURES,
            mode = mediaMode,
            ruleText = settings.picturesSyncRule,
            newestCount = if (mediaMode == SyncMode.SELECTED_ITEMS) parseTrailingCount(settings.picturesSyncRule) else null,
        )

        val name = if (isGuestSession) {
            "Guest Session — $deviceSerialNumber"
        } else {
            "Sync Group — $deviceSerialNumber"
        }
        return SyncGroup(deviceSerialNumber, name, isGuestSession, categories)
    }

    /**
     * Computes the planned transfer set. Removals are computed first so their
     * freed space can back later adds; adds and keeps follow in rule order,
     * truncated to projected free space.
     *
     * Remaining desktop parity notes (goldens lock these in): KEEP items carry
     * only title + size, and the picture rule inherits the video rule's mode.
     */
    fun buildPlan(group: SyncGroup, input: SyncInput, device: DeviceSnapshot): SyncPlan {
        val desired = LinkedHashMap<String, TransferItem>()
        for (rule in group.categories) {
            for (item in selectDesired(rule, input)) {
                desired[item.entityId] = item
            }
        }

        val deviceContents = device.contents.associateBy { it.entityId }
        val managedCategories = group.categories
            .filter { it.mode != SyncMode.MANUAL }
            .map { it.category }
            .toSet()

        val items = mutableListOf<TransferItem>()

        if (!group.isGuestSession) {
            for (content in device.contents) {
                if (content.entityId !in desired && content.category in managedCategories) {
                    items += TransferItem(
                        action = TransferAction.REMOVE,
                        category = content.category,
                        entityId = content.entityId,
                        title = content.title,
                        sizeBytes = content.sizeBytes,
                        detail = "No longer in sync group",
                    )
                }
            }
        }

        val removeBytes = items.filter { it.action == TransferAction.REMOVE }.sumOf { it.sizeBytes }
        val projectedUsed = device.contents.sumOf { it.sizeBytes } - removeBytes
        var freeBytes = device.totalCapacityBytes - device.systemBytes - projectedUsed

        for ((entityId, item) in desired) {
            if (deviceContents.containsKey(entityId)) {
                // Desktop parity: KEEP items carry only title + size (no detail/path).
                items += TransferItem(
                    action = TransferAction.KEEP,
                    category = item.category,
                    entityId = entityId,
                    title = item.title,
                    sizeBytes = item.sizeBytes,
                )
            } else if (item.sizeBytes <= freeBytes) {
                items += item
                freeBytes -= item.sizeBytes
            }
        }

        return SyncPlan(group.deviceSerialNumber, group.isGuestSession, items)
    }

    /**
     * Applies a plan to the transport: removals first so their freed space
     * backs the adds that follow (mirrors the desktop `ApplyPlanAsync`). KEEP
     * items are no-ops. [onProgress] receives (index+1)/count per item, then a
     * final 1.0.
     */
    fun applyPlan(plan: SyncPlan, transport: DeviceTransport, onProgress: ((Double) -> Unit)? = null) {
        val ordered = plan.items.filter { it.action == TransferAction.REMOVE } +
            plan.items.filter { it.action == TransferAction.ADD }
        ordered.forEachIndexed { index, item ->
            when (item.action) {
                TransferAction.ADD -> transport.copyToDevice(item)
                TransferAction.REMOVE -> transport.tryGetItem(item.entityId)?.let { transport.removeFromDevice(it) }
                TransferAction.KEEP -> Unit
            }
            onProgress?.invoke((index + 1).toDouble() / ordered.size)
        }
        onProgress?.invoke(1.0)
    }

    private fun selectDesired(rule: SyncCategoryRule, input: SyncInput): List<TransferItem> {
        if (rule.mode == SyncMode.MANUAL) return emptyList()

        return when (rule.category) {
            SyncCategoryType.MUSIC -> {
                val candidates = if (rule.preferFavorites) {
                    input.tracks.filter { it.rating == Rating.HEART }
                } else {
                    input.tracks
                }
                limitIfConfigured(candidates.sortedBy { it.title }, rule).map { track ->
                    TransferItem(
                        action = TransferAction.ADD,
                        category = SyncCategoryType.MUSIC,
                        entityId = track.id,
                        title = track.title,
                        sourcePath = track.filePath,
                        sizeBytes = estimateAudioBytes(track.durationSeconds),
                        detail = "${track.artistName} — ${track.albumTitle}",
                    )
                }
            }

            SyncCategoryType.PODCASTS -> {
                val candidates = input.podcastEpisodes.filter { !it.isPlayed }
                limitIfConfigured(candidates.sortedByDescending { it.publishedAtUtc }, rule).map { episode ->
                    TransferItem(
                        action = TransferAction.ADD,
                        category = SyncCategoryType.PODCASTS,
                        entityId = episode.id,
                        title = episode.title,
                        sourcePath = episode.audioUrl,
                        sizeBytes = estimateAudioBytes(episode.durationSeconds),
                        detail = episode.seriesTitle,
                    )
                }
            }

            SyncCategoryType.VIDEOS -> {
                limitIfConfigured(input.videos.sortedByDescending { it.addedAtUtc }, rule).map { video ->
                    TransferItem(
                        action = TransferAction.ADD,
                        category = SyncCategoryType.VIDEOS,
                        entityId = video.id,
                        title = video.title,
                        sourcePath = video.filePath,
                        sizeBytes = video.sizeBytes,
                    )
                }
            }

            SyncCategoryType.PICTURES -> {
                limitIfConfigured(input.photos.sortedByDescending { it.addedAtUtc }, rule).map { photo ->
                    TransferItem(
                        action = TransferAction.ADD,
                        category = SyncCategoryType.PICTURES,
                        entityId = photo.id,
                        title = photo.title,
                        sourcePath = photo.filePath,
                        sizeBytes = photo.sizeBytes,
                        detail = photo.folderPath,
                    )
                }
            }
        }
    }

    private fun <T> limitIfConfigured(ordered: List<T>, rule: SyncCategoryRule): List<T> =
        rule.newestCount?.let { ordered.take(it) } ?: ordered

    private fun estimateAudioBytes(durationSeconds: Long): Long =
        if (durationSeconds <= 0) 0 else durationSeconds * AUDIO_BYTES_PER_SECOND

    /**
     * Mirrors the desktop: the first run of digits anywhere in the label.
     * "3 Newest Episodes" → 3, "Newest 25 Items" → 25.
     */
    private fun parseTrailingCount(ruleText: String): Int? =
        Regex("\\d+").find(ruleText)?.value?.toIntOrNull()
}
