package com.heretek.dorado_hd.ui.apps.engine3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Interleaved triangle mesh: positions, normals and UVs with u32 indices. */
class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val indexCount: Int get() = indices.size

    /** Axis-aligned box in local space, used for picking and shadows. */
    val bounds: Bounds3 by lazy {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        Bounds3(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
    }
}

data class Bounds3(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = (min + max) * 0.5f
    val radius: Float get() = ((max - min) * 0.5f).length()
}

/** Procedural mesh builders (no model files; all geometry is code). */
object MeshFactory {

    fun box(width: Float = 1f, height: Float = 1f, depth: Float = 1f): MeshData {
        val w = width / 2f; val h = height / 2f; val d = depth / 2f
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        fun face(
            a: Vec3, b: Vec3, c: Vec3, dd: Vec3,
            normal: Vec3,
        ) {
            val base = positions.size / 3
            for (p in listOf(a, b, c, dd)) {
                positions += listOf(p.x, p.y, p.z)
                normals += listOf(normal.x, normal.y, normal.z)
            }
            uvs += listOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f)
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }

        face(Vec3(-w, -h, d), Vec3(w, -h, d), Vec3(w, h, d), Vec3(-w, h, d), Vec3(0f, 0f, 1f))
        face(Vec3(w, -h, -d), Vec3(-w, -h, -d), Vec3(-w, h, -d), Vec3(w, h, -d), Vec3(0f, 0f, -1f))
        face(Vec3(-w, h, d), Vec3(w, h, d), Vec3(w, h, -d), Vec3(-w, h, -d), Vec3(0f, 1f, 0f))
        face(Vec3(-w, -h, -d), Vec3(w, -h, -d), Vec3(w, -h, d), Vec3(-w, -h, d), Vec3(0f, -1f, 0f))
        face(Vec3(w, -h, d), Vec3(w, -h, -d), Vec3(w, h, -d), Vec3(w, h, d), Vec3(1f, 0f, 0f))
        face(Vec3(-w, -h, -d), Vec3(-w, -h, d), Vec3(-w, h, d), Vec3(-w, h, -d), Vec3(-1f, 0f, 0f))
        return MeshData(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
    }

    /** Horizontal plane on XZ facing +Y. */
    fun plane(width: Float = 1f, depth: Float = 1f): MeshData {
        val w = width / 2f; val d = depth / 2f
        return MeshData(
            floatArrayOf(
                -w, 0f, -d, w, 0f, -d, w, 0f, d, -w, 0f, d,
            ),
            floatArrayOf(
                0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f,
            ),
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
            intArrayOf(0, 1, 2, 0, 2, 3),
        )
    }

    /** Y-axis cylinder (tracks, posts, tree trunks). */
    fun cylinder(radius: Float = 0.5f, height: Float = 1f, segments: Int = 16): MeshData {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        val h = height / 2f
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI.toFloat()
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val u = i.toFloat() / segments
            positions += listOf(x, -h, z)
            normals += listOf(cos(angle), 0f, sin(angle))
            uvs += listOf(u, 1f)
            positions += listOf(x, h, z)
            normals += listOf(cos(angle), 0f, sin(angle))
            uvs += listOf(u, 0f)
        }
        for (i in 0 until segments) {
            val a = i * 2; val b = a + 1; val c = a + 2; val d = a + 3
            indices += listOf(a, c, b, b, c, d)
        }
        return MeshData(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
    }

    /** Vertical quad facing +Z, one-sided (billboards, gates, ramps). */
    fun quad(width: Float = 1f, height: Float = 1f): MeshData {
        val w = width / 2f; val h = height / 2f
        return MeshData(
            floatArrayOf(
                -w, -h, 0f, w, -h, 0f, w, h, 0f, -w, h, 0f,
            ),
            floatArrayOf(
                0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            ),
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
            intArrayOf(0, 1, 2, 0, 2, 3),
        )
    }
}
