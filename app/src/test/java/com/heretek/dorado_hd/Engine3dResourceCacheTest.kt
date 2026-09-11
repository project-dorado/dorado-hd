package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.engine3d.FrameSweepCache
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.nodeAt
import com.heretek.dorado_hd.ui.apps.engine3d.rebuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the engine3d per-frame churn fixes (A-22): the GPU
 * caches are bounded by a sweep policy, and scene rebuilds swap atomically.
 */
class Engine3dResourceCacheTest {

    @Test
    fun `touched keys survive the idle window`() {
        val cache = FrameSweepCache(maxIdleFrames = 2)
        val key = Any()
        repeat(20) {
            cache.touch(key)
            cache.tick()
            assertTrue("live key was evicted", cache.evictable().isEmpty())
        }
        assertEquals(1, cache.size())
    }

    @Test
    fun `untouched keys are evicted after the idle window`() {
        val cache = FrameSweepCache(maxIdleFrames = 2)
        val mesh = MeshFactory.box()
        cache.touch(mesh)
        val evicted = ArrayList<Any>()
        repeat(8) {
            cache.tick()
            evicted.addAll(cache.evictable())
        }
        assertTrue("mesh was never evicted", evicted.any { it === mesh })
        assertEquals(0, cache.size())
    }

    @Test
    fun `fresh identities every frame keep the cache bounded`() {
        val cache = FrameSweepCache(maxIdleFrames = 1)
        repeat(64) { frame ->
            val fresh = MeshFactory.box(1f + frame, 1f, 1f)
            cache.touch(fresh)
            cache.tick()
            cache.evictable()
            assertTrue("cache grew to ${cache.size()}", cache.size() <= 2)
        }
    }

    @Test
    fun `rebuild publishes a complete node list`() {
        val scene = Scene3d()
        scene.add(nodeAt(MeshFactory.box(), tag = "old"))
        scene.rebuild {
            add(nodeAt(MeshFactory.box(), tag = "a"))
            add(nodeAt(MeshFactory.box(), tag = "b"))
        }
        assertEquals(2, scene.nodeCount())
        assertEquals(listOf("a", "b"), scene.snapshotNodes().map { it.tag })
    }
}
