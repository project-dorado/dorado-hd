package com.heretek.dorado_hd.ui.apps.engine3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Minimal right-handed 3D math for the engine3d renderer. Pure Kotlin. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-6f) Vec3(0f, 0f, 0f) else Vec3(x / len, y / len, z / len)
    }
    fun lerp(o: Vec3, t: Float) = Vec3(
        x + (o.x - x) * t,
        y + (o.y - y) * t,
        z + (o.z - z) * t,
    )

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
        val RIGHT = Vec3(1f, 0f, 0f)
    }
}

/** Column-major 4x4 matrix (OpenGL convention), 16 floats. */
class Mat4(val m: FloatArray = FloatArray(16)) {

    fun copy(): Mat4 = Mat4(m.copyOf())

    operator fun times(o: Mat4): Mat4 {
        val a = m
        val b = o.m
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[c * 4 + k]
                r[c * 4 + row] = sum
            }
        }
        return Mat4(r)
    }

    /** Transform a point (w = 1) and divide by w. */
    fun transformPoint(p: Vec3): Vec3 {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        return if (abs(w) < 1e-6f) Vec3(x, y, z) else Vec3(x / w, y / w, z / w)
    }

    /** Transform a direction (w = 0). */
    fun transformDirection(v: Vec3): Vec3 = Vec3(
        m[0] * v.x + m[4] * v.y + m[8] * v.z,
        m[1] * v.x + m[5] * v.y + m[9] * v.z,
        m[2] * v.x + m[6] * v.y + m[10] * v.z,
    )

    fun inverted(): Mat4? {
        val inv = FloatArray(16)
        val a = m
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] +
            a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10]
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] -
            a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10]
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] +
            a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9]
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] -
            a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9]
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] -
            a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10]
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] +
            a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10]
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] -
            a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9]
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] +
            a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9]
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] +
            a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6]
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] -
            a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6]
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] +
            a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5]
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] -
            a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5]
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] -
            a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6]
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] +
            a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6]
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] -
            a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5]
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] +
            a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5]
        var det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12]
        if (abs(det) < 1e-9f) return null
        det = 1f / det
        for (i in 0 until 16) inv[i] *= det
        return Mat4(inv)
    }

    companion object {
        fun identity(): Mat4 {
            val m = FloatArray(16)
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
            return Mat4(m)
        }

        fun translation(t: Vec3): Mat4 {
            val m = identity().m
            m[12] = t.x; m[13] = t.y; m[14] = t.z
            return Mat4(m)
        }

        fun scale(s: Vec3): Mat4 {
            val m = identity().m
            m[0] = s.x; m[5] = s.y; m[10] = s.z
            return Mat4(m)
        }

        fun rotationX(rad: Float): Mat4 {
            val c = cos(rad); val s = sin(rad)
            val m = identity().m
            m[5] = c; m[6] = s; m[9] = -s; m[10] = c
            return Mat4(m)
        }

        fun rotationY(rad: Float): Mat4 {
            val c = cos(rad); val s = sin(rad)
            val m = identity().m
            m[0] = c; m[2] = -s; m[8] = s; m[10] = c
            return Mat4(m)
        }

        fun rotationZ(rad: Float): Mat4 {
            val c = cos(rad); val s = sin(rad)
            val m = identity().m
            m[0] = c; m[1] = s; m[4] = -s; m[5] = c
            return Mat4(m)
        }

        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalized()
            val s = f.cross(up).normalized()
            val u = s.cross(f)
            val m = identity().m
            m[0] = s.x; m[4] = s.y; m[8] = s.z
            m[1] = u.x; m[5] = u.y; m[9] = u.z
            m[2] = -f.x; m[6] = -f.y; m[10] = -f.z
            m[12] = -s.dot(eye); m[13] = -u.dot(eye); m[14] = f.dot(eye)
            return Mat4(m)
        }

        fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(fovYDeg * (Math.PI.toFloat() / 180f) / 2f)
            val m = FloatArray(16)
            m[0] = f / aspect
            m[5] = f
            m[10] = (far + near) / (near - far)
            m[11] = -1f
            m[14] = 2f * far * near / (near - far)
            return Mat4(m)
        }

        /** Normal matrix = upper-left 3x3 of the inverse transpose, as a 4x4. */
        fun normalMatrix(model: Mat4): Mat4 {
            val inv = model.inverted() ?: return identity()
            val src = inv.m
            val m = identity().m
            m[0] = src[0]; m[1] = src[4]; m[2] = src[8]
            m[4] = src[1]; m[5] = src[5]; m[6] = src[9]
            m[8] = src[2]; m[9] = src[6]; m[10] = src[10]
            return Mat4(m)
        }
    }
}

/** Orthonormal camera basis and projection. */
data class Camera3d(
    val eye: Vec3 = Vec3(0f, 0f, 5f),
    val target: Vec3 = Vec3.ZERO,
    val up: Vec3 = Vec3.UP,
    val fovYDeg: Float = 60f,
    val near: Float = 0.1f,
    val far: Float = 200f,
) {
    fun view(): Mat4 = Mat4.lookAt(eye, target, up)

    fun projection(aspect: Float): Mat4 = Mat4.perspective(fovYDeg, aspect, near, far)

    /** Build a world-space ray from normalized device coordinates (-1..1). */
    fun ray(ndcX: Float, ndcY: Float, aspect: Float): Ray3d {
        val proj = projection(aspect)
        val view = view()
        val inv = (proj * view).inverted() ?: Mat4.identity()
        val nearPoint = inv.transformPoint(Vec3(ndcX, ndcY, -1f))
        val farPoint = inv.transformPoint(Vec3(ndcX, ndcY, 1f))
        return Ray3d(nearPoint, (farPoint - nearPoint).normalized())
    }
}

data class Ray3d(val origin: Vec3, val direction: Vec3) {
    fun at(t: Float): Vec3 = origin + direction * t
}

/** Ray/sphere intersection; returns nearest positive t or null. */
fun raySphere(ray: Ray3d, center: Vec3, radius: Float): Float? {
    val oc = ray.origin - center
    val b = oc.dot(ray.direction)
    val c = oc.dot(oc) - radius * radius
    val disc = b * b - c
    if (disc < 0f) return null
    val sqrtDisc = sqrt(disc)
    val t0 = -b - sqrtDisc
    val t1 = -b + sqrtDisc
    return when {
        t0 >= 0f -> t0
        t1 >= 0f -> t1
        else -> null
    }
}

/** Ray/plane intersection (plane through [point] with [normal]); t >= 0 or null. */
fun rayPlane(ray: Ray3d, point: Vec3, normal: Vec3): Float? {
    val denom = normal.dot(ray.direction)
    if (abs(denom) < 1e-6f) return null
    val t = (point - ray.origin).dot(normal) / denom
    return if (t >= 0f) t else null
}
