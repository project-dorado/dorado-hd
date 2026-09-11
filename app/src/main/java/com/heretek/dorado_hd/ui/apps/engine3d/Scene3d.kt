package com.heretek.dorado_hd.ui.apps.engine3d

/** RGBA color in 0..1, kept separate from Compose color tokens for the GL path. */
data class Color4(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {
    companion object {
        fun rgb(r: Int, g: Int, b: Int, a: Float = 1f) = Color4(r / 255f, g / 255f, b / 255f, a)
    }
}

/** A CPU-side image for procedural textures (ARGB ints, row-major). */
data class TextureData(val width: Int, val height: Int, val pixels: IntArray) {
    companion object {
        /** Flat fill texture; callers can paint patterns from pure code. */
        fun solid(width: Int = 4, height: Int = 4, argb: Int): TextureData =
            TextureData(width, height, IntArray(width * height) { argb })
    }
}

data class Material3d(
    val color: Color4 = Color4(1f, 1f, 1f),
    val texture: TextureData? = null,
    /** Unlit keeps flat colors (UI-ish elements, ground markers). */
    val unlit: Boolean = false,
    /** Additive-ish emissive term for lamps, projectiles, crystals. */
    val emissive: Float = 0f,
)

data class SceneNode(
    val mesh: MeshData,
    val material: Material3d = Material3d(),
    val transform: Mat4 = Mat4.identity(),
    val tag: String? = null,
    val visible: Boolean = true,
    /** Extra pick radius around the mesh bounds (small objects, sprites). */
    val pickPad: Float = 0.05f,
)

/**
 * A renderable scene. Mutated from the UI thread, consumed by the GL thread;
 * [snapshotNodes] returns a stable copy for a frame.
 */
class Scene3d {
    @Volatile
    var camera: Camera3d = Camera3d()

    @Volatile
    var lightDirection: Vec3 = Vec3(-0.4f, -1f, -0.3f)

    @Volatile
    var ambient: Float = 0.35f

    /** Linear fog blended by distance; 0 disables. */
    @Volatile
    var fogDensity: Float = 0f

    @Volatile
    var backgroundColor: Color4 = Color4(0f, 0f, 0f)

    private val nodes = ArrayList<SceneNode>()

    @Synchronized
    fun clear() = nodes.clear()

    @Synchronized
    fun add(node: SceneNode): SceneNode {
        nodes += node
        return node
    }

    @Synchronized
    fun addAll(newNodes: Collection<SceneNode>) {
        nodes += newNodes
    }

    @Synchronized
    fun snapshotNodes(): List<SceneNode> = ArrayList(nodes)

    @Synchronized
    fun nodeCount(): Int = nodes.size

    /**
     * CPU picking against per-node world-space bounding spheres. Nodes are
     * tested near-to-far; the closest hit's tag is returned.
     */
    fun pick(ray: Ray3d): String? {
        var bestT = Float.MAX_VALUE
        var bestTag: String? = null
        for (node in snapshotNodes()) {
            if (!node.visible) continue
            val center = node.transform.transformPoint(node.mesh.bounds.center)
            val radius = node.mesh.bounds.radius * node.transform.maxScale() + node.pickPad
            val t = raySphere(ray, center, radius) ?: continue
            if (t < bestT) {
                bestT = t
                bestTag = node.tag
            }
        }
        return bestTag
    }

    /** Ray against a horizontal plane at [y] (tile boards, terrain taps). */
    fun pickGround(ray: Ray3d, y: Float = 0f): Vec3? {
        val t = rayPlane(ray, Vec3(0f, y, 0f), Vec3.UP) ?: return null
        return ray.at(t)
    }
}

private fun Mat4.maxScale(): Float {
    val sx = Vec3(m[0], m[1], m[2]).length()
    val sy = Vec3(m[4], m[5], m[6]).length()
    val sz = Vec3(m[8], m[9], m[10]).length()
    return maxOf(sx, sy, sz, 1e-4f)
}

/** Common transform shorthand. */
fun nodeAt(
    mesh: MeshData,
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    tag: String? = null,
    material: Material3d = Material3d(),
    yawRad: Float = 0f,
    pitchRad: Float = 0f,
    rollRad: Float = 0f,
): SceneNode {
    var t = Mat4.translation(Vec3(x, y, z))
    if (rollRad != 0f) t = t * Mat4.rotationZ(rollRad)
    if (pitchRad != 0f) t = t * Mat4.rotationX(pitchRad)
    if (yawRad != 0f) t = t * Mat4.rotationY(yawRad)
    return SceneNode(mesh, material, t, tag)
}
