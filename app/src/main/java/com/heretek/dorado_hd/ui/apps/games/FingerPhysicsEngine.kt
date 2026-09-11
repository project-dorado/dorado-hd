package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Physics2d — a small, deterministic 2D rigid-body solver.
//
// Re-authored original work for the Dorado-HD official-app program. It solves
// circles and oriented boxes (static / dynamic / kinematic) with sequential
// impulses, Coulomb friction, restitution and a positional correction pass.
//
// Units are design pixels and seconds. The public entry point is a fixed
// 1/60 s step (Physics2d.stepFixed); Physics2d.advance additionally subdivides
// a step for fast movers so reasonable speeds never tunnel. No third-party
// dependency, no platform imports, no randomness: identical inputs always
// produce identical output.
// ---------------------------------------------------------------------------

/** Immutable 2D vector used by the solver and the game engines. */
data class P2Vec(val x: Float, val y: Float) {
    operator fun plus(other: P2Vec) = P2Vec(x + other.x, y + other.y)
    operator fun minus(other: P2Vec) = P2Vec(x - other.x, y - other.y)
    operator fun times(scale: Float) = P2Vec(x * scale, y * scale)
    fun dot(other: P2Vec) = x * other.x + y * other.y
    fun cross(other: P2Vec) = x * other.y - y * other.x
    fun length(): Float = sqrt(x * x + y * y)
    fun normalized(): P2Vec {
        val len = length()
        return if (len < 1e-6f) P2Vec(0f, 0f) else P2Vec(x / len, y / len)
    }
    fun rotate(angle: Float): P2Vec {
        val c = cos(angle)
        val s = sin(angle)
        return P2Vec(x * c - y * s, x * s + y * c)
    }
    fun perp() = P2Vec(-y, x)
}

enum class P2Shape { CIRCLE, BOX }

/**
 * A rigid body. Position/angle/velocity are mutable so a game loop can step
 * a world in place; all shape and material data is fixed at creation.
 */
class P2Body(
    val id: Int,
    val shape: P2Shape,
    val radius: Float,
    val halfW: Float,
    val halfH: Float,
    var x: Float,
    var y: Float,
    var angle: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var omega: Float = 0f,
    density: Float = 1f,
    val restitution: Float = 0.2f,
    val friction: Float = 0.6f,
    val isStatic: Boolean = false,
    val isSensor: Boolean = false,
    val tag: String = "",
) {
    val mass: Float
    val invMass: Float
    val invInertia: Float

    /** Per-body gravity multiplier; negative values flip personal gravity. */
    var gravityScale: Float = 1f

    /**
     * A kinematic body is moved by its owner (drag, rotating platform): the
     * solver treats it as infinite mass but still reads its velocity.
     */
    var kinematic: Boolean = false

    /** Contacts touched this sub-step; used for resting-body deactivation. */
    var contactCount: Int = 0

    init {
        val m = if (isStatic) {
            0f
        } else {
            density * if (shape == P2Shape.CIRCLE) (PI * radius * radius).toFloat() else 4f * halfW * halfH
        }
        mass = m
        invMass = if (m > 0f) 1f / m else 0f
        invInertia = if (m > 0f) {
            val inertia = if (shape == P2Shape.CIRCLE) {
                0.5f * m * radius * radius
            } else {
                m * (4f * halfW * halfW + 4f * halfH * halfH) / 12f
            }
            if (inertia > 0f) 1f / inertia else 0f
        } else {
            0f
        }
    }

    val effectiveInvMass: Float get() = if (isStatic || kinematic) 0f else invMass
    val effectiveInvInertia: Float get() = if (isStatic || kinematic) 0f else invInertia

    /** Smallest half-extent; drives the adaptive sub-step anti-tunnelling. */
    fun extent(): Float = if (shape == P2Shape.CIRCLE) radius else min(halfW, halfH)

    /** Vertical half-extent (used by stacking-height scoring). */
    fun verticalExtent(): Float = if (shape == P2Shape.CIRCLE) radius else halfH

    fun containsPoint(px: Float, py: Float): Boolean {
        if (shape == P2Shape.CIRCLE) {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy <= radius * radius
        }
        val local = P2Vec(px - x, py - y).rotate(-angle)
        return abs(local.x) <= halfW && abs(local.y) <= halfH
    }

    /** World-space box corners, counter-clockwise. Empty for circles. */
    fun vertices(): List<P2Vec> {
        if (shape != P2Shape.BOX) return emptyList()
        val c = cos(angle)
        val s = sin(angle)
        fun corner(px: Float, py: Float) = P2Vec(x + px * c - py * s, y + px * s + py * c)
        return listOf(
            corner(-halfW, -halfH),
            corner(halfW, -halfH),
            corner(halfW, halfH),
            corner(-halfW, halfH),
        )
    }
}

/** A joint constrains one or two bodies. Solved inside the velocity pass. */
sealed class P2Joint {
    abstract fun attachedTo(body: P2Body): Boolean
    abstract fun solveVelocity(h: Float)
    abstract fun solvePosition()
}

/** Revolute pin: a local body anchor tracks a fixed world point. */
class P2PinJoint(
    val body: P2Body,
    val localX: Float,
    val localY: Float,
    val worldX: Float,
    val worldY: Float,
) : P2Joint() {
    private fun offset(): P2Vec = P2Vec(localX, localY).rotate(body.angle)

    override fun attachedTo(body: P2Body) = this.body === body

    override fun solveVelocity(h: Float) {
        val im = body.effectiveInvMass
        val ii = body.effectiveInvInertia
        if (im <= 0f && ii <= 0f) return
        val r = offset()
        val vpx = body.vx - body.omega * r.y
        val vpy = body.vy + body.omega * r.x
        val k00 = im + ii * r.y * r.y
        val k01 = -ii * r.x * r.y
        val k11 = im + ii * r.x * r.x
        val det = k00 * k11 - k01 * k01
        if (det <= 1e-9f) return
        val jx = -(k11 * vpx - k01 * vpy) / det
        val jy = -(-k01 * vpx + k00 * vpy) / det
        body.vx += jx * im
        body.vy += jy * im
        body.omega += ii * (r.x * jy - r.y * jx)
    }

    override fun solvePosition() {
        val im = body.effectiveInvMass
        val ii = body.effectiveInvInertia
        if (im <= 0f && ii <= 0f) return
        val r = offset()
        val ex = (worldX - (body.x + r.x)) * 0.5f
        val ey = (worldY - (body.y + r.y)) * 0.5f
        val k00 = im + ii * r.y * r.y
        val k01 = -ii * r.x * r.y
        val k11 = im + ii * r.x * r.x
        val det = k00 * k11 - k01 * k01
        if (det <= 1e-9f) return
        val px = (k11 * ex - k01 * ey) / det
        val py = (-k01 * ex + k00 * ey) / det
        body.x += px * im
        body.y += py * im
        body.angle += ii * (r.x * py - r.y * px)
    }
}

/** Soft distance constraint (ribbon / magnet weld / tether). */
class P2DistanceJoint(
    val a: P2Body,
    val b: P2Body,
    val restLength: Float,
    val stiffness: Float = 0.8f,
) : P2Joint() {
    override fun attachedTo(body: P2Body) = a === body || b === body

    override fun solveVelocity(h: Float) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1e-5f) return
        val nx = dx / dist
        val ny = dy / dist
        val imA = a.effectiveInvMass
        val imB = b.effectiveInvMass
        val k = imA + imB
        if (k <= 1e-9f) return
        val vn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        val desired = (-stiffness * (dist - restLength) * 0.2f / h).coerceIn(-400f, 400f)
        val j = (desired - vn) / k
        a.vx -= nx * j * imA
        a.vy -= ny * j * imA
        b.vx += nx * j * imB
        b.vy += ny * j * imB
    }

    override fun solvePosition() {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1e-5f) return
        val nx = dx / dist
        val ny = dy / dist
        val imA = a.effectiveInvMass
        val imB = b.effectiveInvMass
        val k = imA + imB
        if (k <= 1e-9f) return
        val corr = (dist - restLength) * 0.45f * stiffness
        a.x += nx * corr * (imA / k)
        a.y += ny * corr * (imA / k)
        b.x -= nx * corr * (imB / k)
        b.y -= ny * corr * (imB / k)
    }
}

class P2ContactPoint(
    val px: Float,
    val py: Float,
    val separation: Float,
) {
    var normalImpulse: Float = 0f
    var tangentImpulse: Float = 0f
}

class P2Contact(
    val a: P2Body,
    val b: P2Body,
    val nx: Float,
    val ny: Float,
    val points: List<P2ContactPoint>,
    val friction: Float,
    val restitutionBias: Float,
    val impactSpeed: Float,
)

/** A world owns bodies, joints and the contacts found in the last step. */
class P2World(
    var gx: Float,
    var gy: Float,
) {
    val bodies = ArrayList<P2Body>()
    val joints = ArrayList<P2Joint>()
    var solverIterations: Int = Physics2d.SOLVER_ITERATIONS
    var positionIterations: Int = Physics2d.POSITION_ITERATIONS
    var contacts: List<P2Contact> = emptyList()

    private var nextId = 1

    fun createCircle(
        x: Float,
        y: Float,
        radius: Float,
        tag: String = "",
        density: Float = 1f,
        restitution: Float = 0.2f,
        friction: Float = 0.6f,
        isStatic: Boolean = false,
    ): P2Body {
        val body = P2Body(
            id = nextId++,
            shape = P2Shape.CIRCLE,
            radius = radius,
            halfW = 0f,
            halfH = 0f,
            x = x,
            y = y,
            density = density,
            restitution = restitution,
            friction = friction,
            isStatic = isStatic,
            tag = tag,
        )
        bodies.add(body)
        return body
    }

    fun createBox(
        x: Float,
        y: Float,
        halfW: Float,
        halfH: Float,
        angle: Float = 0f,
        tag: String = "",
        density: Float = 1f,
        restitution: Float = 0.2f,
        friction: Float = 0.6f,
        isStatic: Boolean = false,
    ): P2Body {
        val body = P2Body(
            id = nextId++,
            shape = P2Shape.BOX,
            radius = 0f,
            halfW = halfW,
            halfH = halfH,
            x = x,
            y = y,
            angle = angle,
            density = density,
            restitution = restitution,
            friction = friction,
            isStatic = isStatic,
            tag = tag,
        )
        bodies.add(body)
        return body
    }

    fun add(body: P2Body): P2Body {
        bodies.add(body)
        return body
    }

    fun remove(body: P2Body) {
        bodies.remove(body)
        joints.removeAll { it.attachedTo(body) }
    }

    fun clear() {
        bodies.clear()
        joints.clear()
        contacts = emptyList()
        nextId = 1
    }

    fun byTag(tag: String): List<P2Body> = bodies.filter { it.tag == tag }
}

object Physics2d {
    /** The simulation tick. Everything the games schedule is quantised to it. */
    const val FIXED_DT = 1f / 60f

    /** Spec base gravity (units/s^2); callers scale into design pixels. */
    const val DEFAULT_GRAVITY_Y = 100f

    /** Split a step for fast movers so they never skip a thin body. */
    const val MAX_SUBSTEPS = 8

    const val SOLVER_ITERATIONS = 24
    const val POSITION_ITERATIONS = 3
    const val PENETRATION_SLOP = 0.03f
    const val POSITION_BETA = 0.22f

    const val LINEAR_DAMPING = 0.03f
    const val ANGULAR_DAMPING = 0.05f

    /**
     * Restitution is suppressed below this approach speed so that resting
     * contacts (which re-approach at gravity * dt each step) do not jitter.
     */
    const val RESTITUTION_VELOCITY = 40f

    /** Resting-body deactivation thresholds (only while touching). */
    const val SLEEP_LINEAR = 2.5f
    const val SLEEP_ANGULAR = 0.05f

    /** Advance [steps] fixed 1/60 s ticks. */
    fun stepFixed(world: P2World, steps: Int = 1) {
        repeat(steps) { advance(world, FIXED_DT) }
    }

    /** Advance by an arbitrary positive dt with adaptive sub-stepping. */
    fun advance(world: P2World, dt: Float) {
        if (dt <= 0f) return
        val count = substepCount(world, dt)
        val h = dt / count
        val sink = ArrayList<P2Contact>()
        repeat(count) { substep(world, h, sink) }
        world.contacts = sink
    }

    /** How many sub-steps keep the fastest body under half its own extent. */
    fun substepCount(world: P2World, dt: Float): Int {
        var maxTravel = 0f
        var minExtent = Float.MAX_VALUE
        for (body in world.bodies) {
            if (body.isStatic || body.kinematic) continue
            val travel = sqrt(body.vx * body.vx + body.vy * body.vy) * dt
            if (travel > maxTravel) maxTravel = travel
            val extent = body.extent()
            if (extent > 0.01f && extent < minExtent) minExtent = extent
        }
        if (maxTravel <= 0f || minExtent == Float.MAX_VALUE) return 1
        return ceil(maxTravel / (0.45f * minExtent)).toInt().coerceIn(1, MAX_SUBSTEPS)
    }

    private fun substep(world: P2World, h: Float, sink: MutableList<P2Contact>) {
        val linearDamp = 1f / (1f + LINEAR_DAMPING * h)
        val angularDamp = 1f / (1f + ANGULAR_DAMPING * h)
        for (body in world.bodies) {
            body.contactCount = 0
            if (body.isStatic) {
                if (body.kinematic && body.omega != 0f) body.angle += body.omega * h
                continue
            }
            if (body.kinematic) continue
            body.vx = (body.vx + world.gx * body.gravityScale * h) * linearDamp
            body.vy = (body.vy + world.gy * body.gravityScale * h) * linearDamp
            body.omega *= angularDamp
        }

        val contacts = collide(world)
        for (iteration in 0 until world.solverIterations) {
            for (contact in contacts) solveContactVelocity(contact)
            for (joint in world.joints) joint.solveVelocity(h)
        }

        for (body in world.bodies) {
            if (body.isStatic || body.kinematic) continue
            body.x += body.vx * h
            body.y += body.vy * h
            body.angle += body.omega * h
        }

        for (iteration in 0 until world.positionIterations) {
            for (contact in contacts) solveContactPosition(contact)
            for (joint in world.joints) joint.solvePosition()
        }

        for (body in world.bodies) {
            if (body.isStatic || body.kinematic || body.contactCount == 0) continue
            val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
            if (speed < SLEEP_LINEAR && abs(body.omega) < SLEEP_ANGULAR) {
                body.vx = 0f
                body.vy = 0f
                body.omega = 0f
            }
        }

        sink.addAll(contacts)
    }

    private fun solveContactVelocity(contact: P2Contact) {
        val a = contact.a
        val b = contact.b
        val nx = contact.nx
        val ny = contact.ny
        val tx = -ny
        val ty = nx
        val imA = a.effectiveInvMass
        val iiA = a.effectiveInvInertia
        val imB = b.effectiveInvMass
        val iiB = b.effectiveInvInertia
        if (imA <= 0f && iiA <= 0f && imB <= 0f && iiB <= 0f) return
        for (point in contact.points) {
            val rAx = point.px - a.x
            val rAy = point.py - a.y
            val rBx = point.px - b.x
            val rBy = point.py - b.y

            var vax = a.vx - a.omega * rAy
            var vay = a.vy + a.omega * rAx
            var vbx = b.vx - b.omega * rBy
            var vby = b.vy + b.omega * rBx
            val rvx = vbx - vax
            val rvy = vby - vay
            val vn = rvx * nx + rvy * ny

            val crossAN = rAx * ny - rAy * nx
            val crossBN = rBx * ny - rBy * nx
            val kn = imA + imB + iiA * crossAN * crossAN + iiB * crossBN * crossBN
            if (kn <= 1e-9f) continue

            var jn = (contact.restitutionBias - vn) / kn
            val oldNormal = point.normalImpulse
            point.normalImpulse = max(oldNormal + jn, 0f)
            jn = point.normalImpulse - oldNormal

            a.vx -= jn * nx * imA
            a.vy -= jn * ny * imA
            a.omega -= jn * crossAN * iiA
            b.vx += jn * nx * imB
            b.vy += jn * ny * imB
            b.omega += jn * crossBN * iiB

            vax = a.vx - a.omega * rAy
            vay = a.vy + a.omega * rAx
            vbx = b.vx - b.omega * rBy
            vby = b.vy + b.omega * rBx
            val vt = (vbx - vax) * tx + (vby - vay) * ty

            val crossAT = rAx * ty - rAy * tx
            val crossBT = rBx * ty - rBy * tx
            val kt = imA + imB + iiA * crossAT * crossAT + iiB * crossBT * crossBT
            if (kt <= 1e-9f) continue

            var jt = -vt / kt
            val maxFriction = contact.friction * point.normalImpulse
            val oldTangent = point.tangentImpulse
            point.tangentImpulse = (oldTangent + jt).coerceIn(-maxFriction, maxFriction)
            jt = point.tangentImpulse - oldTangent

            a.vx -= jt * tx * imA
            a.vy -= jt * ty * imA
            a.omega -= jt * crossAT * iiA
            b.vx += jt * tx * imB
            b.vy += jt * ty * imB
            b.omega += jt * crossBT * iiB
        }
    }

    private fun solveContactPosition(contact: P2Contact) {
        val a = contact.a
        val b = contact.b
        val imA = a.effectiveInvMass
        val imB = b.effectiveInvMass
        val k = imA + imB
        if (k <= 1e-9f) return
        // A contact is corrected once from its deepest point: correcting every
        // point would multiply the lift and push resting bodies out of contact.
        var deepest = 0f
        for (point in contact.points) {
            if (point.separation > deepest) deepest = point.separation
        }
        val correction = max(deepest - PENETRATION_SLOP, 0f) * POSITION_BETA / k
        if (correction <= 0f) return
        a.x -= contact.nx * correction * imA
        a.y -= contact.ny * correction * imA
        b.x += contact.nx * correction * imB
        b.y += contact.ny * correction * imB
    }

    fun collide(world: P2World): List<P2Contact> {
        val out = ArrayList<P2Contact>()
        val bodies = world.bodies
        for (i in bodies.indices) {
            val a = bodies[i]
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                if (a.isStatic && b.isStatic) continue
                if (a.isSensor || b.isSensor) continue
                val contact = collidePair(a, b) ?: continue
                if (contact.points.isEmpty()) continue
                a.contactCount += contact.points.size
                b.contactCount += contact.points.size
                out.add(contact)
            }
        }
        return out
    }

    private fun collidePair(a: P2Body, b: P2Body): P2Contact? = when {
        a.shape == P2Shape.CIRCLE && b.shape == P2Shape.CIRCLE -> collideCircles(a, b)
        a.shape == P2Shape.CIRCLE && b.shape == P2Shape.BOX -> collideCircleBox(a, b)
        a.shape == P2Shape.BOX && b.shape == P2Shape.CIRCLE -> collideCircleBox(b, a)?.flipped()
        else -> collideBoxes(a, b)
    }

    private fun P2Contact.flipped(): P2Contact = contactOf(
        a = b,
        b = a,
        nx = -nx,
        ny = -ny,
        points = points.map { P2ContactPoint(it.px, it.py, it.separation) },
        impact = 0f,
    )

    private fun contactOf(
        a: P2Body,
        b: P2Body,
        nx: Float,
        ny: Float,
        points: List<P2ContactPoint>,
        impact: Float,
    ): P2Contact {
        val head = points.first()
        val rAx = head.px - a.x
        val rAy = head.py - a.y
        val rBx = head.px - b.x
        val rBy = head.py - b.y
        val vax = a.vx - a.omega * rAy
        val vay = a.vy + a.omega * rAx
        val vbx = b.vx - b.omega * rBy
        val vby = b.vy + b.omega * rBx
        val vn = (vbx - vax) * nx + (vby - vay) * ny
        val restitution = max(a.restitution, b.restitution)
        val bias = if (vn < -RESTITUTION_VELOCITY) -restitution * vn else 0f
        val impactSpeed = if (impact > 0f) impact else if (vn < 0f) -vn else 0f
        return P2Contact(
            a = a,
            b = b,
            nx = nx,
            ny = ny,
            points = points,
            friction = sqrt(a.friction * b.friction),
            restitutionBias = bias,
            impactSpeed = impactSpeed,
        )
    }

    private fun collideCircles(a: P2Body, b: P2Body): P2Contact? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val rsum = a.radius + b.radius
        val d2 = dx * dx + dy * dy
        if (d2 > rsum * rsum) return null
        val dist = sqrt(d2)
        val nx: Float
        val ny: Float
        if (dist < 1e-4f) {
            nx = 0f
            ny = 1f
        } else {
            nx = dx / dist
            ny = dy / dist
        }
        val separation = rsum - dist
        val px = a.x + nx * (a.radius - separation * 0.5f)
        val py = a.y + ny * (a.radius - separation * 0.5f)
        return contactOf(a, b, nx, ny, listOf(P2ContactPoint(px, py, separation)), 0f)
    }

    private fun collideCircleBox(circle: P2Body, box: P2Body): P2Contact? {
        val local = P2Vec(circle.x - box.x, circle.y - box.y).rotate(-box.angle)
        val clampedX = local.x.coerceIn(-box.halfW, box.halfW)
        val clampedY = local.y.coerceIn(-box.halfH, box.halfH)
        val dx = local.x - clampedX
        val dy = local.y - clampedY
        val dist = sqrt(dx * dx + dy * dy)
        val nLocal: P2Vec
        val pointLocal: P2Vec
        val separation: Float
        if (dist > 1e-4f) {
            if (dist > circle.radius) return null
            nLocal = P2Vec(-dx / dist, -dy / dist)
            pointLocal = P2Vec(clampedX, clampedY)
            separation = circle.radius - dist
        } else {
            val faceX = box.halfW - abs(local.x)
            val faceY = box.halfH - abs(local.y)
            if (faceX < faceY) {
                val sign = if (local.x >= 0f) 1f else -1f
                nLocal = P2Vec(-sign, 0f)
                pointLocal = P2Vec(sign * box.halfW, local.y)
                separation = circle.radius + faceX
            } else {
                val sign = if (local.y >= 0f) 1f else -1f
                nLocal = P2Vec(0f, -sign)
                pointLocal = P2Vec(local.x, sign * box.halfH)
                separation = circle.radius + faceY
            }
        }
        val normal = nLocal.rotate(box.angle)
        val worldPoint = P2Vec(box.x, box.y) + pointLocal.rotate(box.angle)
        val pts = listOf(P2ContactPoint(worldPoint.x, worldPoint.y, separation))
        return contactOf(circle, box, normal.x, normal.y, pts, 0f)
    }

    private class P2Face(val v1: P2Vec, val v2: P2Vec, val nx: Float, val ny: Float) {
        val midX: Float get() = (v1.x + v2.x) * 0.5f
        val midY: Float get() = (v1.y + v2.y) * 0.5f
    }

    private fun P2Body.faces(): List<P2Face> {
        val corners = vertices()
        val faces = ArrayList<P2Face>(4)
        for (i in 0 until 4) {
            val p1 = corners[i]
            val p2 = corners[(i + 1) % 4]
            var nx = p2.y - p1.y
            var ny = -(p2.x - p1.x)
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-6f) continue
            nx /= len
            ny /= len
            if ((p1.x - x) * nx + (p1.y - y) * ny < 0f) {
                nx = -nx
                ny = -ny
            }
            faces.add(P2Face(p1, p2, nx, ny))
        }
        return faces
    }

    private fun clipSegment(
        p1: P2Vec,
        p2: P2Vec,
        planeX: Float,
        planeY: Float,
        planeNx: Float,
        planeNy: Float,
    ): Pair<P2Vec, P2Vec>? {
        val d1 = (p1.x - planeX) * planeNx + (p1.y - planeY) * planeNy
        val d2 = (p2.x - planeX) * planeNx + (p2.y - planeY) * planeNy
        if (d1 >= 0f && d2 >= 0f) return p1 to p2
        if (d1 < 0f && d2 < 0f) return null
        val t = d1 / (d1 - d2)
        val px = p1.x + (p2.x - p1.x) * t
        val py = p1.y + (p2.y - p1.y) * t
        val point = P2Vec(px, py)
        return if (d1 >= 0f) point to p2 else p1 to point
    }

    /**
     * Box-box collision by SAT plus face clipping (Box2D-lite style). Face
     * contacts yield two points, which keeps resting stacks from wobbling the
     * way a single deepest-vertex contact would.
     */
    private fun collideBoxes(a: P2Body, b: P2Body): P2Contact? {
        val facesA = a.faces()
        val facesB = b.faces()
        var minOverlap = Float.MAX_VALUE
        var axisX = 0f
        var axisY = 1f
        var refIsA = true
        for (face in facesA) {
            val overlap = overlapOnAxis(a, b, face.nx, face.ny)
            if (overlap <= 0f) return null
            if (overlap < minOverlap) {
                minOverlap = overlap
                axisX = face.nx
                axisY = face.ny
                refIsA = true
            }
        }
        for (face in facesB) {
            val overlap = overlapOnAxis(a, b, face.nx, face.ny)
            if (overlap <= 0f) return null
            if (overlap < minOverlap) {
                minOverlap = overlap
                axisX = face.nx
                axisY = face.ny
                refIsA = false
            }
        }
        if ((b.x - a.x) * axisX + (b.y - a.y) * axisY < 0f) {
            axisX = -axisX
            axisY = -axisY
        }

        val ref = if (refIsA) a else b
        val inc = if (refIsA) b else a
        val refDirX = if (refIsA) axisX else -axisX
        val refDirY = if (refIsA) axisY else -axisY

        var refFace = ref.faces().first()
        var refDot = -Float.MAX_VALUE
        for (face in ref.faces()) {
            val dot = face.nx * refDirX + face.ny * refDirY
            if (dot > refDot) {
                refDot = dot
                refFace = face
            }
        }
        var incFace = inc.faces().first()
        var incDot = Float.MAX_VALUE
        for (face in inc.faces()) {
            val dot = face.nx * refDirX + face.ny * refDirY
            if (dot < incDot) {
                incDot = dot
                incFace = face
            }
        }

        val tangentX = refFace.v2.x - refFace.v1.x
        val tangentY = refFace.v2.y - refFace.v1.y
        val tangentLen = sqrt(tangentX * tangentX + tangentY * tangentY)
        if (tangentLen < 1e-6f) return null
        val tx = tangentX / tangentLen
        val ty = tangentY / tangentLen

        val clipped = clipSegment(incFace.v1, incFace.v2, refFace.v1.x, refFace.v1.y, tx, ty) ?: return null
        val clipped2 = clipSegment(clipped.first, clipped.second, refFace.v2.x, refFace.v2.y, -tx, -ty) ?: return null

        val points = ArrayList<P2ContactPoint>(2)
        for (point in listOf(clipped2.first, clipped2.second)) {
            val separation = (point.x - refFace.midX) * refDirX + (point.y - refFace.midY) * refDirY
            if (separation <= 0.5f) {
                points.add(P2ContactPoint(point.x, point.y, max(-separation, 0f)))
            }
        }
        if (points.isEmpty()) return null

        val normalX = if (refIsA) refDirX else -refDirX
        val normalY = if (refIsA) refDirY else -refDirY
        return contactOf(a, b, normalX, normalY, points, 0f)
    }

    private fun overlapOnAxis(a: P2Body, b: P2Body, axisX: Float, axisY: Float): Float {
        var minA = Float.MAX_VALUE
        var maxA = -Float.MAX_VALUE
        var minB = Float.MAX_VALUE
        var maxB = -Float.MAX_VALUE
        for (v in a.vertices()) {
            val p = v.x * axisX + v.y * axisY
            if (p < minA) minA = p
            if (p > maxA) maxA = p
        }
        for (v in b.vertices()) {
            val p = v.x * axisX + v.y * axisY
            if (p < minB) minB = p
            if (p > maxB) maxB = p
        }
        return min(maxA, maxB) - max(minA, minB)
    }

    /** Round-trip-safe numeric formatting for determinism snapshots. */
    fun round3(value: Float): String = (round(value * 1000f) / 1000f).toString()
}

// ---------------------------------------------------------------------------
// Finger Physics — ten modes, ninety original levels, one shared objective
// loop. Behaviour is re-derived from docs/apps/finger-physics.md; every level
// below is authored here in code (no original data or art is used).
// ---------------------------------------------------------------------------

enum class FpMedalKind { HEIGHT, TIME }

enum class FpMedal(val points: Int) {
    NONE(0),
    BRONZE(1),
    SILVER(2),
    GOLD(3),
}

enum class FpStatus { PLAYING, WON, LOST }

enum class FpLoseReason { NONE, TIME, BROKEN, OUT_OF_BOUNDS }

/** The ten mode names carried by the original binary's BlockitApp array. */
enum class FpMode(val label: String) {
    EGG("egg"),
    LAWN("lawn"),
    LUNAR("lunar"),
    MAGNET("magnet blocks"),
    UNDERWATER("underwater"),
    EXPLOSIVE("explosive blocks"),
    FREE("free"),
    GRAVITY("gravity blocks"),
    GEARED("geared blocks"),
    PINNED("pinned blocks"),
    ;

    val medalKind: FpMedalKind
        get() = if (this == EGG || this == FREE) FpMedalKind.HEIGHT else FpMedalKind.TIME
}

/** Axis-aligned target rectangle in design pixels. */
data class FpZone(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    fun contains(px: Float, py: Float): Boolean = px in x0..x1 && py in y0..y1
}

/**
 * One authored level. Height modes score a stack height in pixels; time modes
 * score elapsed seconds (lower is better). Medal thresholds inherit that
 * direction, so [bronze] is the easiest target in both cases.
 */
data class FpLevel(
    val index: Int,
    val mode: FpMode,
    val slot: Int,
    val seed: Int,
    val heightToWin: Float,
    val bronze: Float,
    val silver: Float,
    val gold: Float,
    val timeLimitMs: Long,
    val goalCount: Int,
    val spawnCount: Int,
)

/**
 * Mutable run state. The world is stepped in place; [tick] is the only writer
 * during play, which keeps replay deterministic.
 */
class FpGame(
    val mode: FpMode,
    val level: FpLevel,
    val world: P2World,
) {
    var status: FpStatus = FpStatus.PLAYING
    var loseReason: FpLoseReason = FpLoseReason.NONE
    var elapsedMs: Long = 0L
    var stableSteps: Int = 0
    var objectiveMet: Boolean = false
    var objectiveWasMet: Boolean = false
    var score: Int = 0
    var medal: FpMedal = FpMedal.NONE
    var spawnRemaining: Int = level.spawnCount
    var spawnedCount: Int = 0
    var brokenCount: Int = 0
    var welds: Int = 0
    var explosions: Int = 0
    var maxHeight: Float = 0f
    var goalZone: FpZone? = null
    var gravityBallId: Int = 0
    var draggedId: Int = 0
    var accumulatorMs: Float = 0f
    var stepCount: Int = 0
    var message: String = ""
    val events = ArrayList<String>()
    val weldedPairs = HashSet<Long>()

    val index: Int get() = level.index
    val slot: Int get() = level.slot
    val stableMs: Long get() = stableSteps * 1000L / 60L

    fun timeLeftMs(): Long = (level.timeLimitMs - elapsedMs).coerceAtLeast(0L)

    fun spawnKey(a: Int, b: Int): Long = if (a < b) a * 100000L + b else b * 100000L + a
}

/** Save-state for the campaign: best medal per level plus the last position. */
data class FpProgress(
    val medals: Map<Int, FpMedal> = emptyMap(),
    val lastMode: FpMode? = null,
    val lastSlot: Int = 1,
) {
    val points: Int get() = medals.values.sumOf { it.points }

    fun best(level: FpLevel): FpMedal = medals[level.index] ?: FpMedal.NONE

    fun record(level: FpLevel, medal: FpMedal): FpProgress {
        val current = medals[level.index] ?: FpMedal.NONE
        val next = if (medal.points > current.points) medal else current
        return copy(
            medals = medals + (level.index to next),
            lastMode = level.mode,
            lastSlot = level.slot,
        )
    }
}

object FingerPhysicsEngine {
    const val DESIGN_W = 272f
    const val DESIGN_H = 480f
    const val FLOOR_Y = 480f

    /** Spec base gravity (0, 100) units/s^2, scaled to design pixels. */
    const val BASE_GRAVITY_Y = 100f
    const val PIXELS_PER_UNIT = 9f
    const val GRAVITY_Y = BASE_GRAVITY_Y * PIXELS_PER_UNIT
    const val LUNAR_GRAVITY_SCALE = 0.2f

    /** Objective must then stay stable for the spec's five seconds to win. */
    const val STABLE_TIME_STEPS = 300
    const val STABLE_LINEAR = 14f
    const val STABLE_ANGULAR = 0.5f

    const val EGG_BREAK_SPEED = 380f
    const val EXPLOSIVE_IMPACT_SPEED = 240f
    const val EXPLOSION_FULL_RADIUS = 48f
    const val EXPLOSION_FALLOFF_RADIUS = 192f
    const val EXPLOSION_SPEED = 420f
    const val MAGNET_FORCE = 10000f
    const val MAGNET_SCALE = 0.07f
    const val MAGNET_RANGE = 190f
    const val WATER_LINE = 170f
    const val LUNAR_SETTLE_SPEED = 30f
    const val ZONE_SETTLE_SPEED = 80f
    const val DRAG_SPEED_LIMIT = 700f

    private val CHARGED_SPAWNS = arrayOf(
        floatArrayOf(52f, 62f),
        floatArrayOf(220f, 62f),
        floatArrayOf(40f, 150f),
        floatArrayOf(232f, 150f),
    )

    /** 10 modes x 9 levels = the advertised 90-level campaign. */
    val LEVELS: List<FpLevel> by lazy { buildLevels() }

    fun level(mode: FpMode, slot: Int): FpLevel = LEVELS[mode.ordinal * 9 + (slot - 1).coerceIn(0, 8)]

    fun levelsFor(mode: FpMode): List<FpLevel> = LEVELS.filter { it.mode == mode }

    /** Linear falloff: full strength inside 48 px, gone at 192 px. */
    fun explosionFalloff(distance: Float): Float = when {
        distance <= EXPLOSION_FULL_RADIUS -> 1f
        distance >= EXPLOSION_FALLOFF_RADIUS -> 0f
        else -> (EXPLOSION_FALLOFF_RADIUS - distance) / (EXPLOSION_FALLOFF_RADIUS - EXPLOSION_FULL_RADIUS)
    }

    fun isStable(world: P2World): Boolean = world.bodies.all { body ->
        if (body.isStatic || body.kinematic) {
            true
        } else {
            val speed = sqrt(body.vx * body.vx + body.vy * body.vy)
            speed <= STABLE_LINEAR && abs(body.omega) <= STABLE_ANGULAR
        }
    }

    fun metricValue(game: FpGame): Float = when (game.mode.medalKind) {
        FpMedalKind.HEIGHT -> game.maxHeight
        FpMedalKind.TIME -> game.elapsedMs / 1000f
    }

    fun medalFor(level: FpLevel, value: Float): FpMedal = when (level.mode.medalKind) {
        FpMedalKind.HEIGHT -> when {
            value >= level.gold -> FpMedal.GOLD
            value >= level.silver -> FpMedal.SILVER
            value >= level.bronze -> FpMedal.BRONZE
            else -> FpMedal.NONE
        }
        FpMedalKind.TIME -> when {
            value <= level.gold -> FpMedal.GOLD
            value <= level.silver -> FpMedal.SILVER
            value <= level.bronze -> FpMedal.BRONZE
            else -> FpMedal.NONE
        }
    }

    fun goalDescription(mode: FpMode): String = when (mode) {
        FpMode.EGG -> "drop eggs to build a stack past the goal line"
        FpMode.LAWN -> "roll the balls into the bin"
        FpMode.LUNAR -> "land the capsule gently on the pad"
        FpMode.MAGNET -> "let charged blocks lock together"
        FpMode.UNDERWATER -> "float the debris above the water line"
        FpMode.EXPLOSIVE -> "chain the explosives until none remain"
        FpMode.FREE -> "drop every shape and let the pile settle"
        FpMode.GRAVITY -> "tap the ball to flip its gravity into the zone"
        FpMode.GEARED -> "let the turning paddles carry the ball home"
        FpMode.PINNED -> "swing the pinned planks to guide the ball home"
    }

    fun newGame(level: FpLevel): FpGame {
        val gravity = when (level.mode) {
            FpMode.UNDERWATER -> -GRAVITY_Y
            FpMode.LUNAR -> GRAVITY_Y * LUNAR_GRAVITY_SCALE
            else -> GRAVITY_Y
        }
        val world = P2World(0f, gravity)
        val game = FpGame(level.mode, level, world)
        buildLevel(game)
        return game
    }

    private fun buildLevel(game: FpGame) {
        val world = game.world
        world.createBox(136f, FLOOR_Y + 6f, 170f, 6f, 0f, "floor", isStatic = true, friction = 0.9f)
        world.createBox(-6f, 240f, 6f, 260f, 0f, "wall", isStatic = true)
        world.createBox(278f, 240f, 6f, 260f, 0f, "wall", isStatic = true)
        world.createBox(136f, -6f, 170f, 6f, 0f, "wall", isStatic = true)
        when (game.mode) {
            FpMode.EGG -> world.createBox(136f, 300f, 64f, 7f, 0f, "platform", isStatic = true, friction = 0.85f)
            FpMode.LAWN -> {
                world.createBox(136f, 300f, 130f, 8f, 0.20f, "platform", isStatic = true, friction = 0.9f)
                world.createBox(232f, 360f, 4f, 48f, 0f, "platform", isStatic = true)
                world.createBox(266f, 372f, 4f, 30f, 0f, "platform", isStatic = true)
                world.createBox(249f, 402f, 22f, 4f, 0f, "platform", isStatic = true, friction = 0.9f)
                game.goalZone = FpZone(236f, 330f, 262f, 400f)
            }
            FpMode.LUNAR -> {
                world.createBox(136f, 360f, 60f, 7f, 0f, "platform", isStatic = true, friction = 0.9f)
                game.goalZone = FpZone(96f, 322f, 176f, 360f)
            }
            FpMode.MAGNET -> world.createBox(136f, 330f, 80f, 7f, 0f, "platform", isStatic = true, friction = 0.8f)
            FpMode.UNDERWATER -> Unit
            FpMode.EXPLOSIVE -> {
                world.createBox(136f, 340f, 70f, 7f, 0f, "platform", isStatic = true, friction = 0.8f)
                world.createBox(78f, 300f, 6f, 40f, 0f, "platform", isStatic = true)
                world.createBox(194f, 300f, 6f, 40f, 0f, "platform", isStatic = true)
            }
            FpMode.FREE -> Unit
            FpMode.GRAVITY -> {
                game.goalZone = if (game.slot % 2 == 1) {
                    FpZone(18f, 18f, 82f, 92f)
                } else {
                    FpZone(190f, 18f, 254f, 92f)
                }
            }
            FpMode.GEARED -> {
                val paddleA = world.createBox(108f, 300f, 36f, 5f, 0f, "paddle", isStatic = true, friction = 0.9f)
                paddleA.kinematic = true
                paddleA.omega = 1.5f
                val paddleB = world.createBox(186f, 370f, 36f, 5f, 1.4f, "paddle", isStatic = true, friction = 0.9f)
                paddleB.kinematic = true
                paddleB.omega = -1.7f
                world.createBox(252f, 400f, 4f, 40f, 0f, "platform", isStatic = true)
                world.createBox(220f, 444f, 36f, 4f, 0f, "platform", isStatic = true, friction = 0.9f)
                game.goalZone = FpZone(224f, 392f, 268f, 444f)
            }
            FpMode.PINNED -> {
                pinPlank(world, 96f, 170f, -0.45f)
                pinPlank(world, 136f, 240f, 0f)
                pinPlank(world, 176f, 310f, 0.45f)
                world.createBox(236f, 420f, 36f, 4f, 0f, "platform", isStatic = true, friction = 0.9f)
                world.createBox(202f, 438f, 4f, 42f, 0f, "platform", isStatic = true)
                world.createBox(270f, 438f, 4f, 42f, 0f, "platform", isStatic = true)
                game.goalZone = FpZone(206f, 380f, 266f, 420f)
            }
        }
    }

    private fun pinPlank(world: P2World, x: Float, y: Float, angle: Float) {
        val plank = world.createBox(x, y, 30f, 5f, angle, "pinned", friction = 0.9f, restitution = 0.05f)
        val offset = P2Vec(0f, -5f).rotate(angle)
        world.joints.add(P2PinJoint(plank, 0f, -5f, x + offset.x, y + offset.y))
    }

    fun spawnNext(game: FpGame): Boolean {
        if (game.status != FpStatus.PLAYING || game.spawnRemaining <= 0) return false
        val world = game.world
        val index = game.spawnedCount
        when (game.mode) {
            FpMode.EGG -> world.createCircle(90f + (index % 3) * 46f, 50f, 16f, "egg", restitution = 0.15f, friction = 0.75f)
            FpMode.LAWN -> world.createCircle(24f + (index % 4) * 9f, 60f - (index % 3) * 12f, 12f, "ball", restitution = 0.25f, friction = 0.5f)
            FpMode.LUNAR -> world.createBox(124f + (index % 2) * 24f, 54f, 14f, 6f, 0f, "capsule", restitution = 0.05f, friction = 0.7f)
            FpMode.MAGNET -> {
                val spawn = CHARGED_SPAWNS[index % CHARGED_SPAWNS.size]
                world.createCircle(spawn[0], spawn[1], 14f, "charged", restitution = 0.1f, friction = 0.5f)
            }
            FpMode.UNDERWATER -> world.createCircle(50f + (index % 4) * 54f, 442f - (index / 4) * 46f, 14f, "float", density = 0.6f, restitution = 0.05f, friction = 0.4f)
            FpMode.EXPLOSIVE -> world.createCircle(80f + (index % 3) * 46f, 48f - (index / 3) * 46f, 15f, "explosive", restitution = 0.1f, friction = 0.55f)
            FpMode.FREE -> {
                val x = 56f + (index % 4) * 44f
                if (index % 2 == 0) {
                    world.createCircle(x, 50f, 14f, "free", restitution = 0.15f, friction = 0.6f)
                } else {
                    world.createBox(x, 50f, 13f, 13f, 0f, "free", restitution = 0.1f, friction = 0.7f)
                }
            }
            FpMode.GRAVITY -> {
                val ball = world.createCircle(136f, 250f, 13f, "ball", restitution = 0.2f, friction = 0.5f)
                game.gravityBallId = ball.id
            }
            FpMode.GEARED -> world.createCircle(36f, 250f, 13f, "ball", restitution = 0.25f, friction = 0.55f)
            FpMode.PINNED -> world.createCircle(40f, 60f, 12f, "ball", restitution = 0.2f, friction = 0.6f)
        }
        game.spawnRemaining--
        game.spawnedCount++
        game.events.add("spawn")
        return true
    }

    fun pickBody(game: FpGame, px: Float, py: Float): Int {
        val bodies = game.world.bodies
        for (i in bodies.indices.reversed()) {
            val body = bodies[i]
            if (body.isStatic || body.isSensor || body.tag == "paddle") continue
            if (body.containsPoint(px, py)) return body.id
        }
        return 0
    }

    fun beginDrag(game: FpGame, id: Int) {
        val body = game.world.bodies.firstOrNull { it.id == id } ?: return
        body.kinematic = true
        body.vx = 0f
        body.vy = 0f
        body.omega = 0f
        game.draggedId = id
    }

    fun dragTo(game: FpGame, px: Float, py: Float) {
        val body = game.world.bodies.firstOrNull { it.id == game.draggedId && it.kinematic } ?: return
        val tx = px.coerceIn(8f, DESIGN_W - 8f)
        val ty = py.coerceIn(8f, FLOOR_Y - 8f)
        body.vx = ((tx - body.x) / Physics2d.FIXED_DT).coerceIn(-DRAG_SPEED_LIMIT, DRAG_SPEED_LIMIT)
        body.vy = ((ty - body.y) / Physics2d.FIXED_DT).coerceIn(-DRAG_SPEED_LIMIT, DRAG_SPEED_LIMIT)
        body.x = tx
        body.y = ty
        body.omega = 0f
    }

    fun endDrag(game: FpGame) {
        val body = game.world.bodies.firstOrNull { it.id == game.draggedId } ?: return
        body.vx *= 0.35f
        body.vy *= 0.35f
        body.kinematic = false
        game.draggedId = 0
    }

    fun flipGravity(game: FpGame, id: Int): Boolean {
        if (game.mode != FpMode.GRAVITY) return false
        val body = game.world.bodies.firstOrNull { it.id == id } ?: return false
        body.gravityScale = if (body.gravityScale <= 0f) 1f else -1f
        game.events.add("flip")
        return true
    }

    private const val STEP_MS_F = 1000f / 60f

    fun tick(game: FpGame, dtMs: Long): FpGame {
        if (game.status != FpStatus.PLAYING) return game
        game.events.clear()
        if (dtMs > 0L) game.accumulatorMs += dtMs.toFloat().coerceAtMost(100f)
        var guard = 0
        while (game.accumulatorMs >= STEP_MS_F && guard < 6) {
            game.accumulatorMs -= STEP_MS_F
            guard++
            if (game.status != FpStatus.PLAYING) break
            applyModeForces(game)
            Physics2d.stepFixed(game.world, 1)
            game.stepCount++
            game.elapsedMs = game.stepCount * 1000L / 60L
            reactContacts(game)
            updateMetrics(game)
            checkLose(game)
            checkGoal(game)
            updateVictory(game)
        }
        if (game.status != FpStatus.PLAYING) game.accumulatorMs = 0f
        return game
    }

    /** Run a fixed number of simulation ticks; used by scripted tests. */
    fun stepSteps(game: FpGame, steps: Int): FpGame {
        repeat(steps) { tick(game, 17L) }
        return game
    }

    private fun applyModeForces(game: FpGame) {
        when (game.mode) {
            FpMode.MAGNET -> {
                val charged = game.world.byTag("charged")
                for (i in charged.indices) {
                    for (j in i + 1 until charged.size) {
                        val a = charged[i]
                        val b = charged[j]
                        if (game.weldedPairs.contains(game.spawnKey(a.id, b.id))) continue
                        val dx = b.x - a.x
                        val dy = b.y - a.y
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < 1f || dist > MAGNET_RANGE) continue
                        val nx = dx / dist
                        val ny = dy / dist
                        val accel = MAGNET_FORCE * MAGNET_SCALE * (1f - dist / MAGNET_RANGE)
                        a.vx += nx * accel * Physics2d.FIXED_DT
                        a.vy += ny * accel * Physics2d.FIXED_DT
                        b.vx -= nx * accel * Physics2d.FIXED_DT
                        b.vy -= ny * accel * Physics2d.FIXED_DT
                    }
                }
            }
            FpMode.GRAVITY -> {
                val ball = game.world.bodies.firstOrNull { it.id == game.gravityBallId } ?: return
                val zone = game.goalZone ?: return
                val inside = zone.contains(ball.x, ball.y)
                if (inside && ball.gravityScale < 0f) ball.gravityScale = 0f
                if (!inside && ball.gravityScale == 0f) ball.gravityScale = 1f
            }
            else -> Unit
        }
    }

    private fun reactContacts(game: FpGame) {
        for (contact in game.world.contacts) {
            val a = contact.a
            val b = contact.b
            when (game.mode) {
                FpMode.EGG -> {
                    if (a.tag == "egg" && contact.impactSpeed > EGG_BREAK_SPEED) breakEgg(game, a)
                    if (b.tag == "egg" && contact.impactSpeed > EGG_BREAK_SPEED) breakEgg(game, b)
                }
                FpMode.MAGNET -> {
                    if (a.tag == "charged" && b.tag == "charged") {
                        val key = game.spawnKey(a.id, b.id)
                        if (game.weldedPairs.add(key)) {
                            game.world.joints.add(P2DistanceJoint(a, b, 0.5f, 1f))
                            game.welds++
                            game.events.add("weld")
                        }
                    }
                }
                FpMode.EXPLOSIVE -> {
                    val aExplosive = a.tag == "explosive"
                    val bExplosive = b.tag == "explosive"
                    if (aExplosive && (bExplosive || contact.impactSpeed > EXPLOSIVE_IMPACT_SPEED)) detonate(game, a)
                    if (bExplosive && (aExplosive || contact.impactSpeed > EXPLOSIVE_IMPACT_SPEED)) detonate(game, b)
                }
                else -> Unit
            }
        }
    }

    private fun breakEgg(game: FpGame, egg: P2Body) {
        if (egg !in game.world.bodies) return
        game.world.remove(egg)
        game.brokenCount++
        game.events.add("break")
    }

    private fun detonate(game: FpGame, body: P2Body) {
        if (body !in game.world.bodies) return
        val blastX = body.x
        val blastY = body.y
        game.world.remove(body)
        game.explosions++
        game.events.add("blast")
        for (other in game.world.bodies) {
            if (other.isStatic) continue
            val dx = other.x - blastX
            val dy = other.y - blastY
            val dist = sqrt(dx * dx + dy * dy)
            val falloff = explosionFalloff(dist)
            if (falloff <= 0f) continue
            val nx: Float
            val ny: Float
            if (dist < 1e-3f) {
                nx = 0f
                ny = -1f
            } else {
                nx = dx / dist
                ny = dy / dist
            }
            other.vx += nx * EXPLOSION_SPEED * falloff
            other.vy += ny * EXPLOSION_SPEED * falloff
            other.omega += (nx - ny) * 0.35f * falloff
        }
    }

    private fun updateMetrics(game: FpGame) {
        var surface = FLOOR_Y
        for (body in game.world.bodies) {
            if (body.isStatic) continue
            val top = body.y - body.verticalExtent()
            if (top < surface) surface = top
        }
        game.maxHeight = (FLOOR_Y - surface).coerceAtLeast(0f)
    }

    private fun checkGoal(game: FpGame) {
        if (game.objectiveMet) return
        val world = game.world
        game.objectiveMet = when (game.mode) {
            FpMode.EGG -> game.spawnRemaining == 0 && game.maxHeight >= game.level.heightToWin
            FpMode.FREE -> game.spawnRemaining == 0
            FpMode.LAWN -> countInZone(world, game.goalZone, "ball") >= game.level.goalCount
            FpMode.LUNAR -> {
                val capsule = world.bodies.firstOrNull { it.tag == "capsule" }
                capsule != null &&
                    game.goalZone?.contains(capsule.x, capsule.y) == true &&
                    speed(capsule) < LUNAR_SETTLE_SPEED
            }
            FpMode.MAGNET -> game.welds >= game.level.goalCount
            FpMode.UNDERWATER -> world.bodies.count { it.tag == "float" && it.y < WATER_LINE } >= game.level.goalCount
            FpMode.EXPLOSIVE -> game.spawnRemaining == 0 && world.bodies.none { it.tag == "explosive" }
            FpMode.GRAVITY, FpMode.GEARED, FpMode.PINNED -> {
                val ball = world.bodies.firstOrNull { it.tag == "ball" }
                ball != null &&
                    game.goalZone?.contains(ball.x, ball.y) == true &&
                    speed(ball) < ZONE_SETTLE_SPEED
            }
        }
    }

    private fun countInZone(world: P2World, zone: FpZone?, tag: String): Int {
        if (zone == null) return 0
        return world.bodies.count { it.tag == tag && zone.contains(it.x, it.y) }
    }

    private fun checkLose(game: FpGame) {
        if (game.status != FpStatus.PLAYING) return
        if (game.elapsedMs >= game.level.timeLimitMs) {
            lose(game, FpLoseReason.TIME)
            return
        }
        if (game.mode == FpMode.EGG && game.spawnRemaining == 0 && game.world.byTag("egg").isEmpty() && game.brokenCount > 0) {
            lose(game, FpLoseReason.BROKEN)
            return
        }
        for (body in game.world.bodies) {
            if (body.isStatic) continue
            if (body.y > FLOOR_Y + 90f || body.y < -90f || body.x < -90f || body.x > DESIGN_W + 90f) {
                lose(game, FpLoseReason.OUT_OF_BOUNDS)
                return
            }
        }
    }

    private fun updateVictory(game: FpGame) {
        if (game.status != FpStatus.PLAYING) return
        if (!game.objectiveMet) {
            game.stableSteps = 0
            return
        }
        if (!game.objectiveWasMet) {
            game.objectiveWasMet = true
            game.stableSteps = 0
        }
        if (isStable(game.world)) {
            game.stableSteps++
        } else {
            game.stableSteps = 0
        }
        if (game.stableSteps >= STABLE_TIME_STEPS) {
            game.status = FpStatus.WON
            game.medal = medalFor(game.level, metricValue(game))
            game.score = game.medal.points
            game.message = "objective clear"
            game.events.add("win")
        }
    }

    private fun lose(game: FpGame, reason: FpLoseReason) {
        game.status = FpStatus.LOST
        game.loseReason = reason
        game.message = when (reason) {
            FpLoseReason.TIME -> "out of time"
            FpLoseReason.BROKEN -> "the eggs broke"
            FpLoseReason.OUT_OF_BOUNDS -> "a shape escaped"
            FpLoseReason.NONE -> ""
        }
        game.events.add("lose")
    }

    private fun speed(body: P2Body): Float = sqrt(body.vx * body.vx + body.vy * body.vy)

    /** Deterministic replay snapshot: every body state, rounded to 1e-3. */
    fun snapshot(game: FpGame): String {
        val sb = StringBuilder(512)
        sb.append(game.status).append('|').append(game.stepCount).append('|')
            .append(game.spawnRemaining).append('|').append(game.brokenCount).append('|')
            .append(game.welds).append('|').append(game.explosions).append('|')
            .append(Physics2d.round3(game.maxHeight)).append('|')
        for (body in game.world.bodies) {
            sb.append(body.id).append(':')
                .append(Physics2d.round3(body.x)).append(',')
                .append(Physics2d.round3(body.y)).append(',')
                .append(Physics2d.round3(body.angle)).append(',')
                .append(Physics2d.round3(body.vx)).append(',')
                .append(Physics2d.round3(body.vy)).append(',')
                .append(Physics2d.round3(body.omega)).append(';')
        }
        return sb.toString()
    }

    fun encodeProgress(progress: FpProgress): String {
        val entries = progress.medals.entries
            .filter { it.value != FpMedal.NONE }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value.ordinal}" }
        return "fp1|$entries|${progress.lastMode?.ordinal ?: -1}|${progress.lastSlot}"
    }

    fun decodeProgress(blob: String?): FpProgress {
        if (blob.isNullOrBlank()) return FpProgress()
        return try {
            val parts = blob.split('|')
            if (parts.size < 4 || parts[0] != "fp1") return FpProgress()
            val medals = HashMap<Int, FpMedal>()
            if (parts[1].isNotEmpty()) {
                parts[1].split(',').forEach { entry ->
                    val bits = entry.split(':')
                    if (bits.size == 2) {
                        val level = bits[0].toIntOrNull() ?: return@forEach
                        val ordinal = bits[1].toIntOrNull() ?: return@forEach
                        val medal = FpMedal.entries.getOrNull(ordinal) ?: return@forEach
                        if (level in 1..90) medals[level] = medal
                    }
                }
            }
            val mode = parts[2].toIntOrNull()?.let { FpMode.entries.getOrNull(it) }
            val slot = parts[3].toIntOrNull()?.coerceIn(1, 9) ?: 1
            FpProgress(medals, mode, slot)
        } catch (_: Exception) {
            FpProgress()
        }
    }

    private fun buildLevels(): List<FpLevel> {
        val out = ArrayList<FpLevel>(90)
        var index = 1
        for (mode in FpMode.entries) {
            for (slot in 1..9) {
                val seed = index * 31 + 7
                val level = when (mode) {
                    FpMode.EGG -> {
                        val win = 60f + slot * 14f
                        FpLevel(index, mode, slot, seed, win, win, win + 26f, win + 52f, 120_000L, 3, 3)
                    }
                    FpMode.LAWN -> FpLevel(
                        index, mode, slot, seed, 0f,
                        50f - slot * 2f, 40f - slot * 2f, 31f - slot * 2f,
                        90_000L, 2 + slot / 4, 3 + slot / 3,
                    )
                    FpMode.LUNAR -> FpLevel(
                        index, mode, slot, seed, 0f,
                        45f - slot * 2f, 36f - slot * 2f, 28f - slot * 2f,
                        75_000L, 1, 1,
                    )
                    FpMode.MAGNET -> FpLevel(
                        index, mode, slot, seed, 0f,
                        55f - slot * 3f, 45f - slot * 3f, 36f - slot * 3f,
                        90_000L, 1 + slot / 4, 4,
                    )
                    FpMode.UNDERWATER -> FpLevel(
                        index, mode, slot, seed, 0f,
                        45f - slot * 2f, 35f - slot * 2f, 27f - slot * 2f,
                        75_000L, 2 + slot / 3, 3 + slot / 3,
                    )
                    FpMode.EXPLOSIVE -> {
                        val count = 3 + slot / 2
                        FpLevel(
                            index, mode, slot, seed, 0f,
                            50f - slot * 2f, 40f - slot * 2f, 32f - slot * 2f,
                            75_000L, count, count,
                        )
                    }
                    FpMode.FREE -> {
                        val win = 70f + slot * 12f
                        val count = 3 + (slot + 1) / 2
                        FpLevel(index, mode, slot, seed, win, win, win + 30f, win + 60f, 120_000L, count, count)
                    }
                    FpMode.GRAVITY -> FpLevel(
                        index, mode, slot, seed, 0f,
                        42f - slot * 2f, 34f - slot * 2f, 27f - slot * 2f,
                        75_000L, 1, 1,
                    )
                    FpMode.GEARED -> FpLevel(
                        index, mode, slot, seed, 0f,
                        50f - slot * 2f, 40f - slot * 2f, 32f - slot * 2f,
                        90_000L, 1, 1,
                    )
                    FpMode.PINNED -> FpLevel(
                        index, mode, slot, seed, 0f,
                        50f - slot * 2f, 42f - slot * 2f, 34f - slot * 2f,
                        90_000L, 1, 1,
                    )
                }
                out.add(level)
                index++
            }
        }
        return out
    }
}
