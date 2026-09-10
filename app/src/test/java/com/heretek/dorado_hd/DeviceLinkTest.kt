package com.heretek.dorado_hd

import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.data.repo.DeviceLinkRepository
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.data.repo.toSyncRuleSettings
import com.heretek.dorado_hd.sync.DeviceContentItem
import com.heretek.dorado_hd.sync.SyncCategoryType
import com.heretek.dorado_hd.sync.SyncEngine
import com.heretek.dorado_hd.sync.SyncMode
import com.heretek.dorado_hd.sync.SyncRulePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceLinkTest {

    @Test
    fun `sync rule presets cycle and wrap`() {
        assertEquals(
            SyncRulePresets.MUSIC[1],
            SyncRulePresets.next(SyncRulePresets.MUSIC, SyncRulePresets.MUSIC[0]),
        )
        assertEquals(
            SyncRulePresets.MUSIC[0],
            SyncRulePresets.next(SyncRulePresets.MUSIC, SyncRulePresets.MUSIC.last()),
        )
        assertEquals(
            SyncRulePresets.MUSIC[0],
            SyncRulePresets.next(SyncRulePresets.MUSIC, "unknown label"),
        )
    }

    @Test
    fun `settings map to engine rule strings and modes`() {
        val settings = DoradoSettings(
            musicSyncRule = "Selected Favorites",
            podcastSyncRule = "All Unplayed Episodes",
            videoSyncRule = "Manual",
            picturesSyncRule = "Manual",
        )
        val rules = settings.toSyncRuleSettings()
        assertEquals("Selected Favorites", rules.musicSyncRule)
        assertEquals("All Unplayed Episodes", rules.podcastSyncRule)
        assertEquals("Manual", rules.videoSyncRule)
        assertEquals("Manual", rules.picturesSyncRule)

        val group = SyncEngine.buildDefaultGroup("S", rules)
        val music = group.categories.first { it.category == SyncCategoryType.MUSIC }
        assertEquals(SyncMode.SELECTED_ITEMS, music.mode)
        assertTrue(music.preferFavorites)
        assertEquals(SyncMode.AUTOMATIC, group.categories.first { it.category == SyncCategoryType.PODCASTS }.mode)
        assertEquals(SyncMode.MANUAL, group.categories.first { it.category == SyncCategoryType.VIDEOS }.mode)
    }

    @Test
    fun `pending imports queue and clear`() {
        val repo = DeviceLinkRepository(ApplicationProvider.getApplicationContext())
        assertTrue(repo.pendingImports.value.isEmpty())

        repo.copyBack(DeviceContentItem(SyncCategoryType.MUSIC, "i1", "Song", "/d1", 1000))
        repo.copyBack(DeviceContentItem(SyncCategoryType.PICTURES, "i2", "Photo", "/d2", 2000))

        assertEquals(2, repo.pendingImports.value.size)
        assertEquals("Song", repo.pendingImports.value.first().title)
        assertEquals(SyncCategoryType.PICTURES, repo.pendingImports.value.last().category)

        repo.clearPending()
        assertTrue(repo.pendingImports.value.isEmpty())
    }

    @Test
    fun `device snapshot reflects the simulated target`() {
        val repo = DeviceLinkRepository(ApplicationProvider.getApplicationContext())
        val snap = repo.snapshot()
        assertEquals(repo.transport.contents().size, snap.contents.size)
        assertEquals(repo.transport.totalCapacityBytes, snap.totalCapacityBytes)
        assertTrue(snap.systemBytes > 0)
        assertTrue("seeded Zune HD target should have stale content", snap.contents.isNotEmpty())
    }
}
