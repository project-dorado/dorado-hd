package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.engine3d.Camera3d
import com.heretek.dorado_hd.ui.apps.engine3d.Color4
import com.heretek.dorado_hd.ui.apps.engine3d.Material3d
import com.heretek.dorado_hd.ui.apps.engine3d.Mat4
import com.heretek.dorado_hd.ui.apps.engine3d.MeshFactory
import com.heretek.dorado_hd.ui.apps.engine3d.Ray3d
import com.heretek.dorado_hd.ui.apps.engine3d.Scene3d
import com.heretek.dorado_hd.ui.apps.engine3d.SceneNode
import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import com.heretek.dorado_hd.ui.apps.engine3d.nodeAt
import com.heretek.dorado_hd.ui.apps.engine3d.rayPlane
import com.heretek.dorado_hd.ui.apps.engine3d.raySphere
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the engine3d math, mesh and picking core. */
class Engine3dTest {

    private fun assertVec(expected: Vec3, actual: Vec3, eps: Float = 1e-4f) {
        assertTrue(
            "expected $expected, got $actual",
            abs(expected.x - actual.x) < eps && abs(expected.y - actual.y) < eps &&
                abs(expected.z - actual.z) < eps,
        )
    }

    @Test
    fun `matrix multiply matches identity and translation`() {
        val identity = Mat4.identity()
        val t = Mat4.translation(Vec3(1f, 2f, 3f))
        val r = t * identity
        assertVec(Vec3(1f, 2f, 3f), r.transformPoint(Vec3.ZERO))
        assertVec(Vec3(2f, 3f, 4f), r.transformPoint(Vec3(1f, 1f, 1f)))
    }

    @Test
    fun `inverse undoes a rotation and translation`() {
        val m = Mat4.translation(Vec3(3f, -1f, 2f)) * Mat4.rotationY(0.7f)
        val inv = m.inverted()
        assertNotNull(inv)
        val p = Vec3(0.5f, 1.5f, -0.25f)
        assertVec(p, inv!!.transformPoint(m.transformPoint(p)), 1e-3f)
    }

    @Test
    fun `singular matrix has no inverse`() {
        val m = Mat4.scale(Vec3(1f, 0f, 1f))
        assertNull(m.inverted())
    }

    @Test
    fun `camera ray through screen centre points at the target`() {
        val camera = Camera3d(eye = Vec3(0f, 0f, 5f), target = Vec3.ZERO)
        val ray = camera.ray(0f, 0f, 16f / 9f)
        assertVec(Vec3(0f, 0f, -1f), ray.direction, 1e-3f)
    }

    @Test
    fun `ray sphere returns nearest positive hit`() {
        val ray = Ray3d(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))
        val t = raySphere(ray, Vec3(0f, 0f, -5f), 1f)
        assertNotNull(t)
        assertEquals(4f, t!!, 1e-3f)
        assertNull(raySphere(ray, Vec3(0f, 0f, 5f), 1f))
    }

    @Test
    fun `ray plane hits ground and misses parallel`() {
        val ray = Ray3d(Vec3(0f, 4f, 0f), Vec3(0f, -1f, 0.5f).normalized())
        val p = rayPlane(ray, Vec3.ZERO, Vec3.UP)
        assertNotNull(p)
        assertVec(Vec3(0f, 0f, 2f), ray.at(p!!), 1e-2f)
        assertNull(rayPlane(Ray3d(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f)), Vec3.ZERO, Vec3.UP))
    }

    @Test
    fun `mesh bounds and counts are sane`() {
        val box = MeshFactory.box(2f, 4f, 6f)
        assertEquals(24, box.vertexCount)
        assertEquals(36, box.indexCount)
        assertVec(Vec3(-1f, -2f, -3f), box.bounds.min)
        assertVec(Vec3(1f, 2f, 3f), box.bounds.max)
        val plane = MeshFactory.plane(10f, 10f)
        assertEquals(4, plane.vertexCount)
        assertEquals(6, plane.indexCount)
        val cylinder = MeshFactory.cylinder(1f, 2f, 12)
        assertEquals(26, cylinder.vertexCount)
    }

    @Test
    fun `scene picking respects transforms and returns nearest tag`() {
        val scene = Scene3d()
        val near = nodeAt(MeshFactory.box(), x = 0f, y = 0f, z = -2f, tag = "near", material = Material3d(Color4(1f, 0f, 0f)))
        val far = nodeAt(MeshFactory.box(), x = 0f, y = 0f, z = -6f, tag = "far", material = Material3d(Color4(0f, 1f, 0f)))
        scene.add(near)
        scene.add(far)
        val hit = scene.pick(Ray3d(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f)))
        assertEquals("near", hit)
    }

    @Test
    fun `scene picking returns null when the ray misses`() {
        val scene = Scene3d()
        scene.add(nodeAt(MeshFactory.box(), x = 10f, y = 0f, z = 0f, tag = "side"))
        assertNull(scene.pick(Ray3d(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))))
    }

    @Test
    fun `ground picking yields a board coordinate`() {
        val scene = Scene3d()
        val camera = Camera3d(eye = Vec3(0f, 6f, 6f), target = Vec3.ZERO)
        val ray = camera.ray(0f, -0.4f, 1f)
        val p = scene.pickGround(ray)
        assertNotNull(p)
        assertEquals(0f, p!!.y, 1e-3f)
    }

    @Test
    fun `scene snapshots are isolated from later mutation`() {
        val scene = Scene3d()
        scene.add(nodeAt(MeshFactory.box(), tag = "a"))
        val snapshot = scene.snapshotNodes()
        scene.add(nodeAt(MeshFactory.box(), tag = "b"))
        assertEquals(1, snapshot.size)
        assertEquals(2, scene.nodeCount())
        assertTrue(snapshot.all { it is SceneNode })
    }
}
