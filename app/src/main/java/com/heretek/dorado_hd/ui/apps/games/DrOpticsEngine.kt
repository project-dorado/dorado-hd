package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* ================================================================== */
/*  Dr Optics Light Lab - clean-room 2D optics tracer                  */
/*                                                                     */
/*  Re-authored from the behavioral spec in docs/apps/                  */
/*  dr-optics-light-lab.md. No device data, level content, or code was  */
/*  copied: every level below is authored here from scratch.            */
/* ================================================================== */

enum class OpticsColor { RED, GREEN, BLUE }

enum class OpticsKind {
    LASER,
    PLANE_MIRROR,
    ARC_MIRROR,
    RECT_MIRROR,
    POLY_MIRROR,
    OSCILLATING_MIRROR,
    LENS,
    ROCK,
    SINK,
}

/** Animated element motion. Speeds are per second; see [DrOpticsEngine.poseAt]. */
enum class OpticsMotion { FIXED, MOVING, ROTATING, ORBITING }

enum class OpticsStatus { PLAYING, SOLVED, FAILED }

data class OpticsVec(val x: Float, val y: Float) {
    operator fun plus(other: OpticsVec) = OpticsVec(x + other.x, y + other.y)
    operator fun minus(other: OpticsVec) = OpticsVec(x - other.x, y - other.y)
    operator fun times(scale: Float) = OpticsVec(x * scale, y * scale)
    operator fun unaryMinus() = OpticsVec(-x, -y)

    fun dot(other: OpticsVec) = x * other.x + y * other.y
    fun length() = sqrt(x * x + y * y)
    fun normalized(): OpticsVec {
        val len = length()
        return if (len < 1e-5f) OpticsVec(1f, 0f) else OpticsVec(x / len, y / len)
    }

    fun perp() = OpticsVec(-y, x)
    fun angleDeg() = (atan2(y.toDouble(), x.toDouble()) * 180.0 / PI).toFloat()

    companion object {
        fun dir(deg: Float): OpticsVec {
            val r = deg.toDouble() * PI / 180.0
            return OpticsVec(cos(r).toFloat(), sin(r).toFloat())
        }
    }
}

/**
 * One authored optics object. [size] is the full segment length for plane
 * mirrors, the radius for arcs/lenses/sinks, the circumradius for polygon
 * mirrors and the long side for rectangular mirrors and rocks.
 */
data class OpticsElement(
    val id: Int,
    val kind: OpticsKind,
    val pos: OpticsVec,
    val size: Float,
    val rotationDeg: Float = 0f,
    val color: OpticsColor? = null,
    val refractiveIndex: Float = 1.5f,
    val sides: Int = 6,
    val spanDeg: Float = 170f,
    val locked: Boolean = false,
    val movable: Boolean = true,
    val motion: OpticsMotion = OpticsMotion.FIXED,
    val motionRadius: Float = 0f,
    val motionSpeed: Float = 0f,
    val oscillationDeg: Float = 0f,
    val oscillationSpeed: Float = 0f,
)

data class OpticsBeam(
    val color: OpticsColor,
    val start: OpticsVec,
    val end: OpticsVec,
    val depth: Int,
)

data class OpticsHit(
    val element: OpticsElement,
    val point: OpticsVec,
    val distance: Float,
    val normal: OpticsVec,
)

data class OpticsSimulation(
    val beams: List<OpticsBeam>,
    val applied: Map<Int, Float>,
)

data class OpticsSinkState(val id: Int, val stored: Float = 0f) {
    /** Fraction of the 1.0 satisfaction threshold, clamped, for the HUD. */
    val fill: Float get() = (stored / DrOpticsEngine.SINK_SATISFIED).coerceIn(0f, 1f)
    val satisfied: Boolean get() = stored > DrOpticsEngine.SINK_SATISFIED
}

/** Per-element scramble applied to the authored [DrOpticsLevel.solution]. */
data class OpticsTweak(
    val id: Int,
    val deltaDeg: Float = 0f,
    val dx: Float = 0f,
    val dy: Float = 0f,
)

data class DrOpticsLevel(
    val index: Int,
    val name: String,
    val difficulty: Int,
    val maxBeams: Int,
    val maxMoves: Int,
    val solution: List<OpticsElement>,
    val tweaks: List<OpticsTweak> = emptyList(),
) {
    /** Initial, scrambled layout shown to the player. */
    val elements: List<OpticsElement>
        get() = solution.map { element ->
            val tweak = tweaks.firstOrNull { it.id == element.id } ?: return@map element
            element.copy(
                pos = OpticsVec(element.pos.x + tweak.dx, element.pos.y + tweak.dy),
                rotationDeg = normalizeAngle(element.rotationDeg + tweak.deltaDeg),
            )
        }
}

data class DrOpticsState(
    val levelIndex: Int,
    val elements: List<OpticsElement>,
    val sinks: List<OpticsSinkState>,
    val beams: List<OpticsBeam>,
    val maxMoves: Int,
    val maxBeams: Int,
    val clockMs: Long = 0L,
    val moves: Int = 0,
    val status: OpticsStatus = OpticsStatus.PLAYING,
) {
    val completion: Float
        get() = if (sinks.isEmpty()) 0f else (sinks.sumOf { it.fill.toDouble() } / sinks.size).toFloat()

    val solved: Boolean get() = status == OpticsStatus.SOLVED
    val failed: Boolean get() = status == OpticsStatus.FAILED
}

object DrOpticsEngine {

    // Geometry and simulation constants from docs/apps/dr-optics-light-lab.md.
    const val DESIGN_W = 480f
    const val DESIGN_H = 272f
    const val DEFAULT_MAX_BEAMS = 75
    const val MAX_BEAM_DEPTH = 15
    const val SINK_STORED_MAX = 1.25f
    const val SINK_SATISFIED = 1.0f
    const val SINK_DECAY = 0.8f
    const val PICK_RADIUS_SQ = 5000f
    const val DRAG_IGNORE_DELTA = 100f
    const val EDGE_PAD = 10f
    const val RECT_MIRROR_THICKNESS = 10f
    const val ROCK_ASPECT = 0.6f
    const val LEVEL_COUNT = 41

    // Difficulty unlock cutoffs (solved levels needed for tiers 2-5).
    const val UNLOCK_DIFFICULTY_2 = 6
    const val UNLOCK_DIFFICULTY_3 = 16
    const val UNLOCK_DIFFICULTY_4 = 20
    const val UNLOCK_DIFFICULTY_5 = 24
    const val UNLOCK_SPECIAL = 40

    val levels: List<DrOpticsLevel> = buildLevels()

    fun level(index: Int): DrOpticsLevel = levels[index.coerceIn(0, levels.lastIndex)]

    fun requiredSolvedForDifficulty(difficulty: Int): Int = when (difficulty) {
        1 -> 0
        2 -> UNLOCK_DIFFICULTY_2
        3 -> UNLOCK_DIFFICULTY_3
        4 -> UNLOCK_DIFFICULTY_4
        5 -> UNLOCK_DIFFICULTY_5
        else -> UNLOCK_SPECIAL
    }

    fun isDifficultyUnlocked(difficulty: Int, solvedLevels: Int): Boolean =
        solvedLevels >= requiredSolvedForDifficulty(difficulty)

    fun difficultyCounts(): List<Int> {
        val counts = IntArray(7)
        levels.forEach { counts[it.difficulty.coerceIn(0, 6)]++ }
        return counts.toList()
    }

    /**
     * Beam budget per laser. The spec caps recursion at 15 and also divides the
     * pool across the lasers ("recomputed as maxBeams / laserCount").
     */
    fun maxBeamDepth(maxBeams: Int, laserCount: Int): Int =
        min(MAX_BEAM_DEPTH, max(1, maxBeams / max(1, laserCount)))

    // ---------------------------------------------------------------- math --

    /** Reflection: dir' = dir - 2*(dir . n)*n. Normal sign does not matter. */
    fun reflect(dir: OpticsVec, normal: OpticsVec): OpticsVec =
        (dir - normal * (2f * dir.dot(normal))).normalized()

    /**
     * Snell refraction with total-internal-reflection fallback. [normal] may
     * point either way; it is re-oriented against the incident ray. Returns
     * null when the ray undergoes total internal reflection.
     */
    fun refract(dir: OpticsVec, normal: OpticsVec, from: Float, to: Float): OpticsVec? {
        val oriented = if (dir.dot(normal) > 0f) -normal else normal
        val cosI = -dir.dot(oriented)
        if (cosI <= 0f) return null
        val eta = from / to
        val sin2T = eta * eta * (1f - cosI * cosI)
        if (sin2T > 1f) return null
        val cosT = sqrt(1f - sin2T)
        return (dir * eta + oriented * (eta * cosI - cosT)).normalized()
    }

    /** Closest ray/segment hit distance, or null when parallel/miss. */
    fun raySegment(origin: OpticsVec, dir: OpticsVec, a: OpticsVec, b: OpticsVec): Float? {
        val s = b - a
        val denom = cross(dir, s)
        if (abs(denom) < 1e-6f) return null
        val ao = a - origin
        val t = cross(ao, s) / denom
        val u = cross(ao, dir) / denom
        if (t > 1e-4f && u >= 0f && u <= 1f) return t
        return null
    }

    private fun cross(a: OpticsVec, b: OpticsVec) = a.x * b.y - a.y * b.x

    private fun rayCircle(origin: OpticsVec, dir: OpticsVec, center: OpticsVec, radius: Float): Float? {
        val m = origin - center
        val b = m.dot(dir)
        val c = m.dot(m) - radius * radius
        if (c > 0f && b > 0f) return null
        val disc = b * b - c
        if (disc < 0f) return null
        val sq = sqrt(disc)
        var t = -b - sq
        if (t < 1e-4f) t = -b + sq
        if (t < 1e-4f) return null
        return t
    }

    private fun rayBox(
        origin: OpticsVec,
        dir: OpticsVec,
        halfW: Float,
        halfH: Float,
    ): Pair<Float, OpticsVec>? {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        var minNx = 0f
        var minNy = 0f
        var maxNx = 0f
        var maxNy = 0f
        if (abs(dir.x) < 1e-6f) {
            if (origin.x < -halfW || origin.x > halfW) return null
        } else {
            var t1 = (-halfW - origin.x) / dir.x
            var t2 = (halfW - origin.x) / dir.x
            var sign = -1f
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
                sign = 1f
            }
            if (t1 > tmin) {
                tmin = t1
                minNx = sign
                minNy = 0f
            }
            if (t2 < tmax) {
                tmax = t2
                maxNx = -sign
                maxNy = 0f
            }
            if (tmin > tmax) return null
        }
        if (abs(dir.y) < 1e-6f) {
            if (origin.y < -halfH || origin.y > halfH) return null
        } else {
            var t1 = (-halfH - origin.y) / dir.y
            var t2 = (halfH - origin.y) / dir.y
            var sign = -1f
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
                sign = 1f
            }
            if (t1 > tmin) {
                tmin = t1
                minNx = 0f
                minNy = sign
            }
            if (t2 < tmax) {
                tmax = t2
                maxNx = 0f
                maxNy = -sign
            }
            if (tmin > tmax) return null
        }
        return if (tmin > 1e-4f) {
            tmin to OpticsVec(minNx, minNy)
        } else if (tmax > 1e-4f) {
            tmax to OpticsVec(maxNx, maxNy)
        } else {
            null
        }
    }

    private fun rotateVec(v: OpticsVec, deg: Float): OpticsVec {
        val r = deg.toDouble() * PI / 180.0
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return OpticsVec(v.x * c - v.y * s, v.x * s + v.y * c)
    }

    // --------------------------------------------------------- animation ----

    data class OpticsPose(val pos: OpticsVec, val rotationDeg: Float) {
        val dir: OpticsVec get() = OpticsVec.dir(rotationDeg)
    }

    /**
     * Pose of an element at [clockMs]. Deterministic and seed-free: time-based
     * elements are pure functions of the clock, so replays are identical.
     */
    fun poseAt(element: OpticsElement, clockMs: Long): OpticsPose {
        val seconds = clockMs / 1000f
        val pos = when (element.motion) {
            OpticsMotion.MOVING -> OpticsVec(
                element.pos.x + sin(seconds * element.motionSpeed) * element.motionRadius,
                element.pos.y,
            )
            OpticsMotion.ORBITING -> OpticsVec(
                element.pos.x + cos(seconds * element.motionSpeed) * element.motionRadius,
                element.pos.y + sin(seconds * element.motionSpeed) * element.motionRadius,
            )
            else -> element.pos
        }
        var rotation = when (element.motion) {
            OpticsMotion.ROTATING -> element.rotationDeg + seconds * element.motionSpeed
            else -> element.rotationDeg
        }
        if (element.kind == OpticsKind.OSCILLATING_MIRROR) {
            rotation += element.oscillationDeg * sin(seconds * element.oscillationSpeed)
        }
        return OpticsPose(pos, normalizeAngle(rotation))
    }

    /** Refractive index under a point: lens media override vacuum. */
    fun mediumAt(elements: List<OpticsElement>, clockMs: Long, point: OpticsVec): Float {
        var index = 1f
        for (element in elements) {
            if (element.kind != OpticsKind.LENS) continue
            val pose = poseAt(element, clockMs)
            if ((point - pose.pos).length() <= element.size) index = element.refractiveIndex
        }
        return index
    }

    // ------------------------------------------------------------ tracing --

    private fun intersect(
        element: OpticsElement,
        pose: OpticsPose,
        origin: OpticsVec,
        dir: OpticsVec,
    ): OpticsHit? {
        return when (element.kind) {
            OpticsKind.LASER -> null
            OpticsKind.PLANE_MIRROR, OpticsKind.OSCILLATING_MIRROR -> {
                val tangent = OpticsVec.dir(pose.rotationDeg)
                val a = pose.pos - tangent * (element.size * 0.5f)
                val b = pose.pos + tangent * (element.size * 0.5f)
                val t = raySegment(origin, dir, a, b) ?: return null
                OpticsHit(element, origin + dir * t, t, tangent.perp())
            }
            OpticsKind.ARC_MIRROR -> {
                val t = rayCircle(origin, dir, pose.pos, element.size) ?: return null
                val point = origin + dir * t
                val rel = point - pose.pos
                if (angleDistance(rel.angleDeg(), pose.rotationDeg) > element.spanDeg * 0.5f) return null
                OpticsHit(element, point, t, rel.normalized())
            }
            OpticsKind.RECT_MIRROR -> {
                val local = rotateVec(origin - pose.pos, -pose.rotationDeg)
                val localDir = rotateVec(dir, -pose.rotationDeg)
                val hit = rayBox(local, localDir, element.size * 0.5f, RECT_MIRROR_THICKNESS * 0.5f)
                    ?: return null
                val t = hit.first
                val normal = rotateVec(hit.second, pose.rotationDeg)
                OpticsHit(element, origin + dir * t, t, normal)
            }
            OpticsKind.POLY_MIRROR -> {
                val sides = element.sides.coerceAtLeast(3)
                var best: OpticsHit? = null
                var previous = vertexPos(pose, element.size, sides, 0)
                for (i in 1..sides) {
                    val current = vertexPos(pose, element.size, sides, i)
                    val t = raySegment(origin, dir, previous, current)
                    if (t != null && (best == null || t < best.distance)) {
                        val edge = (current - previous).normalized()
                        best = OpticsHit(element, origin + dir * t, t, edge.perp())
                    }
                    previous = current
                }
                best
            }
            OpticsKind.LENS, OpticsKind.SINK -> {
                val t = rayCircle(origin, dir, pose.pos, element.size) ?: return null
                val point = origin + dir * t
                OpticsHit(element, point, t, (point - pose.pos).normalized())
            }
            OpticsKind.ROCK -> {
                val local = rotateVec(origin - pose.pos, -pose.rotationDeg)
                val localDir = rotateVec(dir, -pose.rotationDeg)
                val hit = rayBox(local, localDir, element.size * 0.5f, element.size * ROCK_ASPECT * 0.5f)
                    ?: return null
                OpticsHit(element, origin + dir * hit.first, hit.first, rotateVec(hit.second, pose.rotationDeg))
            }
        }
    }

    private fun vertexPos(pose: OpticsPose, radius: Float, sides: Int, index: Int): OpticsVec {
        val deg = pose.rotationDeg + 360f * index / sides
        return pose.pos + OpticsVec.dir(deg) * radius
    }

    /**
     * Traces every laser into a beam list. The nearest contact wins; matching
     * sinks absorb, rocks stop the ray, mirrors reflect, lenses refract (with a
     * mirror fallback on total internal reflection). The beam pool and the
     * per-laser recursion depth are both capped.
     */
    fun simulation(
        elements: List<OpticsElement>,
        clockMs: Long,
        maxBeams: Int = DEFAULT_MAX_BEAMS,
    ): OpticsSimulation {
        val beams = ArrayList<OpticsBeam>()
        val applied = HashMap<Int, Float>()
        val lasers = elements.filter { it.kind == OpticsKind.LASER && it.color != null }
        if (lasers.isEmpty()) return OpticsSimulation(beams, applied)
        val poses = HashMap<Int, OpticsPose>()
        elements.forEach { poses[it.id] = poseAt(it, clockMs) }
        val depthCap = maxBeamDepth(maxBeams, lasers.size)

        for (laser in lasers) {
            val color = laser.color ?: continue
            val pose = poses.getValue(laser.id)
            var origin = pose.pos
            var dir = pose.dir
            var depth = 0
            while (true) {
                if (beams.size >= maxBeams) return OpticsSimulation(beams, applied)
                val hit = nearestHit(elements, poses, origin, dir, color)
                val end = hit?.point ?: screenExit(origin, dir)
                beams.add(OpticsBeam(color, origin, end, depth))
                if (hit == null) break
                when (hit.element.kind) {
                    OpticsKind.SINK -> {
                        applied[hit.element.id] = (applied[hit.element.id] ?: 0f) + 1f
                        break
                    }
                    OpticsKind.ROCK -> break
                    OpticsKind.LENS -> {
                        val before = mediumAt(elements, clockMs, hit.point - dir * 0.25f)
                        val after = mediumAt(elements, clockMs, hit.point + dir * 0.25f)
                        dir = refract(dir, hit.normal, before, after) ?: reflect(dir, hit.normal)
                    }
                    else -> dir = reflect(dir, hit.normal)
                }
                depth++
                if (depth >= depthCap) break
                origin = hit.point + dir * 0.05f
            }
        }
        return OpticsSimulation(beams, applied)
    }

    private fun nearestHit(
        elements: List<OpticsElement>,
        poses: Map<Int, OpticsPose>,
        origin: OpticsVec,
        dir: OpticsVec,
        color: OpticsColor,
    ): OpticsHit? {
        var best: OpticsHit? = null
        for (element in elements) {
            if (element.kind == OpticsKind.LASER) continue
            if (element.kind == OpticsKind.SINK && element.color != color) continue
            val pose = poses.getValue(element.id)
            val hit = intersect(element, pose, origin, dir) ?: continue
            if (hit.distance <= 1e-3f) continue
            if (best == null || hit.distance < best.distance) best = hit
        }
        return best
    }

    private fun screenExit(origin: OpticsVec, dir: OpticsVec): OpticsVec {
        var t = Float.MAX_VALUE
        val candidates = listOf(
            (0f - origin.x) / dir.x,
            (DESIGN_W - origin.x) / dir.x,
            (0f - origin.y) / dir.y,
            (DESIGN_H - origin.y) / dir.y,
        )
        candidates.forEach { candidate ->
            if (candidate > 1e-4f && candidate < t) t = candidate
        }
        if (t == Float.MAX_VALUE) return origin + dir * 2000f
        return origin + dir * t
    }

    // ------------------------------------------------------- level state ---

    fun stateOf(
        elements: List<OpticsElement>,
        maxBeams: Int = DEFAULT_MAX_BEAMS,
        maxMoves: Int = 0,
    ): DrOpticsState {
        val sinks = elements.filter { it.kind == OpticsKind.SINK }.map { OpticsSinkState(it.id) }
        val state = DrOpticsState(
            levelIndex = -1,
            elements = elements,
            sinks = sinks,
            beams = emptyList(),
            maxMoves = maxMoves,
            maxBeams = maxBeams,
        )
        return refresh(state)
    }

    fun stateAt(levelIndex: Int, elements: List<OpticsElement>): DrOpticsState {
        val level = level(levelIndex)
        val state = stateOf(elements, level.maxBeams, level.maxMoves)
        return state.copy(levelIndex = level.index)
    }

    fun start(index: Int): DrOpticsState = stateAt(index, level(index).elements)

    fun refresh(state: DrOpticsState): DrOpticsState {
        val simulation = simulation(state.elements, state.clockMs, state.maxBeams)
        return state.copy(beams = simulation.beams)
    }

    /**
     * Advance the world by [dtMs]. Sink energy follows the spec formula
     * `stored += (applied - 0.8*stored) * dt`, clamped to [0, 1.25]; the level
     * solves once every sink is above 1.0.
     */
    fun step(state: DrOpticsState, dtMs: Long): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        val dt = dtMs.coerceAtLeast(0L)
        val clock = state.clockMs + dt
        val simulation = simulation(state.elements, clock, state.maxBeams)
        val seconds = dt / 1000f
        var allSatisfied = state.sinks.isNotEmpty()
        val sinks = state.sinks.map { sink ->
            val applied = simulation.applied[sink.id] ?: 0f
            val stored = (sink.stored + (applied - SINK_DECAY * sink.stored) * seconds)
                .coerceIn(0f, SINK_STORED_MAX)
            if (!(stored > SINK_SATISFIED)) allSatisfied = false
            sink.copy(stored = stored)
        }
        val failed = !allSatisfied && state.maxMoves > 0 && state.moves > state.maxMoves
        val status = when {
            allSatisfied -> OpticsStatus.SOLVED
            failed -> OpticsStatus.FAILED
            else -> OpticsStatus.PLAYING
        }
        return state.copy(clockMs = clock, sinks = sinks, beams = simulation.beams, status = status)
    }

    /** Nearest movable, unlocked element within the device's 5000 sq units. */
    fun pick(state: DrOpticsState, point: OpticsVec): OpticsElement? {
        var best: OpticsElement? = null
        var bestDistance = PICK_RADIUS_SQ
        for (element in state.elements) {
            if (!element.movable || element.locked) continue
            val pose = poseAt(element, state.clockMs)
            val delta = pose.pos - point
            val distance = delta.dot(delta)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = element
            }
        }
        return best
    }

    /** Continuous drag update; does not spend a move (see [commit]). */
    fun translate(state: DrOpticsState, id: Int, dx: Float, dy: Float): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        if (abs(dx) > DRAG_IGNORE_DELTA || abs(dy) > DRAG_IGNORE_DELTA) return state
        val index = state.elements.indexOfFirst { it.id == id }
        if (index < 0) return state
        val element = state.elements[index]
        if (!element.movable || element.locked) return state
        val moved = element.copy(
            pos = OpticsVec(
                (element.pos.x + dx).coerceIn(EDGE_PAD, DESIGN_W - EDGE_PAD),
                (element.pos.y + dy).coerceIn(EDGE_PAD, DESIGN_H - EDGE_PAD),
            ),
        )
        val elements = state.elements.toMutableList()
        elements[index] = moved
        return refresh(state.copy(elements = elements))
    }

    /** Spends one move and applies the out-of-moves failure state. */
    fun commit(state: DrOpticsState): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        val moved = refresh(state.copy(moves = state.moves + 1))
        return if (moved.maxMoves > 0 && moved.moves > moved.maxMoves) {
            moved.copy(status = OpticsStatus.FAILED)
        } else {
            moved
        }
    }

    fun rotateElement(state: DrOpticsState, id: Int, deltaDeg: Float): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        val index = state.elements.indexOfFirst { it.id == id }
        if (index < 0) return state
        val element = state.elements[index]
        if (!element.movable || element.locked) return state
        val elements = state.elements.toMutableList()
        elements[index] = element.copy(rotationDeg = normalizeAngle(element.rotationDeg + deltaDeg))
        return commit(refresh(state.copy(elements = elements)))
    }

    fun growElement(state: DrOpticsState, id: Int, factor: Float): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        val index = state.elements.indexOfFirst { it.id == id }
        if (index < 0) return state
        val element = state.elements[index]
        if (!element.movable || element.locked) return state
        val size = (element.size * factor).coerceIn(14f, 90f)
        val elements = state.elements.toMutableList()
        elements[index] = element.copy(size = size)
        return commit(refresh(state.copy(elements = elements)))
    }

    /** Lock mode toggle: locked objects reject dragging until re-enabled. */
    fun toggleLock(state: DrOpticsState, id: Int): DrOpticsState {
        if (state.status != OpticsStatus.PLAYING) return state
        val index = state.elements.indexOfFirst { it.id == id }
        if (index < 0) return state
        val elements = state.elements.toMutableList()
        elements[index] = elements[index].copy(locked = !elements[index].locked)
        return refresh(state.copy(elements = elements))
    }

    // -------------------------------------------------------- persistence --

    fun encode(state: DrOpticsState): String {
        val sinks = state.sinks.joinToString(",") { "${it.id}:${(it.stored * 10000f).roundToInt()}" }
        val elements = state.elements.joinToString("|") { element ->
            listOf(
                element.id,
                (element.pos.x * 10f).roundToInt(),
                (element.pos.y * 10f).roundToInt(),
                element.rotationDeg.roundToInt(),
                element.size.roundToInt(),
                if (element.locked) 1 else 0,
            ).joinToString(":")
        }
        return listOf(
            "do1",
            state.levelIndex,
            state.moves,
            state.clockMs,
            state.status.name,
            sinks,
            elements,
        ).joinToString(";")
    }

    fun decode(blob: String): DrOpticsState? = try {
        val parts = blob.split(";")
        if (parts.size < 7 || parts[0] != "do1") {
            null
        } else {
            val levelIndex = parts[1].toInt()
            val level = level(levelIndex)
            val stored = parts[5].split(",").filter { it.isNotEmpty() }.associate { token ->
                val field = token.split(":")
                field[0].toInt() to field[1].toFloat() / 10000f
            }
            val poses = parts[6].split("|").filter { it.isNotEmpty() }.associate { token ->
                val field = token.split(":")
                field[0].toInt() to field
            }
            val elements = level.elements.map { element ->
                val pose = poses[element.id] ?: return@map element
                element.copy(
                    pos = OpticsVec(pose[1].toFloat() / 10f, pose[2].toFloat() / 10f),
                    rotationDeg = pose[3].toFloat(),
                    size = pose[4].toFloat(),
                    locked = pose[5].toInt() != 0,
                )
            }
            val sinks = elements.filter { it.kind == OpticsKind.SINK }.map {
                OpticsSinkState(it.id, stored[it.id] ?: 0f)
            }
            val status = OpticsStatus.entries.firstOrNull { it.name == parts[4] } ?: OpticsStatus.PLAYING
            val state = DrOpticsState(
                levelIndex = levelIndex,
                elements = elements,
                sinks = sinks,
                beams = emptyList(),
                maxMoves = level.maxMoves,
                maxBeams = level.maxBeams,
                clockMs = parts[3].toLong(),
                moves = parts[2].toInt(),
                status = status,
            )
            refresh(state)
        }
    } catch (_: Exception) {
        null
    }

    /** Structural audit used by tests; empty means the table is valid. */
    fun validate(): List<String> {
        val problems = ArrayList<String>()
        if (levels.size != LEVEL_COUNT) problems += "expected $LEVEL_COUNT levels, found ${levels.size}"
        levels.forEachIndexed { position, level ->
            if (level.index != position) problems += "level $position has index ${level.index}"
            if (level.difficulty !in 1..6) problems += "level $position has difficulty ${level.difficulty}"
            if (level.maxBeams !in 1..200) problems += "level $position has maxBeams ${level.maxBeams}"
            if (level.solution.isEmpty()) problems += "level $position has no elements"
            val ids = level.solution.map { it.id }
            if (ids.size != ids.toSet().size) problems += "level $position has duplicate ids"
            if (level.solution.none { it.kind == OpticsKind.LASER && it.color != null }) {
                problems += "level $position has no coloured laser"
            }
            if (level.solution.none { it.kind == OpticsKind.SINK && it.color != null }) {
                problems += "level $position has no coloured sink"
            }
            level.solution.forEach { element ->
                if (element.pos.x !in -20f..(DESIGN_W + 20f) || element.pos.y !in -20f..(DESIGN_H + 20f)) {
                    problems += "level $position element ${element.id} is off the 480x272 room"
                }
            }
        }
        val counts = difficultyCounts()
        val expected = listOf(0, 8, 14, 8, 6, 4, 1)
        if (counts != expected) problems += "difficulty counts $counts != $expected"
        return problems
    }
}

/* ================================================================== */
/*  Authored level table (41 levels, 8/14/8/6/4 + 1 special)           */
/* ================================================================== */

private fun normalizeAngle(deg: Float): Float {
    var a = deg % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

private fun lineAngle(deg: Float): Float {
    var a = deg % 180f
    if (a >= 90f) a -= 180f
    if (a < -90f) a += 180f
    return a
}

private fun angleDistance(a: Float, b: Float): Float {
    val delta = ((a - b) % 360f + 540f) % 360f - 180f
    return abs(delta)
}

private fun v(x: Float, y: Float) = OpticsVec(x, y)

/**
 * Authoring helper. [route] walks a laser through a polyline of corners and
 * places the correct mirror at each bend, then the sink at the end. Corner
 * geometry is derived from the intended outgoing direction, so the authored
 * path is exactly the traced path for its [DrOpticsLevel.solution].
 */
private class OpticsLevelDraft(private val index: Int, private val difficulty: Int) {
    var name: String = "level ${index + 1}"
    var maxBeams: Int = DrOpticsEngine.DEFAULT_MAX_BEAMS
    var maxMoves: Int = 0

    private val solution = ArrayList<OpticsElement>()
    private val tweaks = ArrayList<OpticsTweak>()
    private var nextId = 1

    private fun id(): Int = nextId++

    fun add(element: OpticsElement): OpticsElement {
        solution += element
        return element
    }

    fun laser(
        x: Float,
        y: Float,
        deg: Float,
        color: OpticsColor,
        motion: OpticsMotion = OpticsMotion.FIXED,
        radius: Float = 0f,
        speed: Float = 0f,
    ) = add(
        OpticsElement(
            id = id(),
            kind = OpticsKind.LASER,
            pos = v(x, y),
            size = 9f,
            rotationDeg = deg,
            color = color,
            movable = false,
            motion = motion,
            motionRadius = radius,
            motionSpeed = speed,
        ),
    )

    fun plane(x: Float, y: Float, deg: Float, size: Float = 46f, locked: Boolean = false) = add(
        OpticsElement(id(), OpticsKind.PLANE_MIRROR, v(x, y), size, rotationDeg = lineAngle(deg), locked = locked),
    )

    fun oscillating(
        x: Float,
        y: Float,
        deg: Float,
        amplitude: Float,
        speed: Float,
        size: Float = 46f,
    ) = add(
        OpticsElement(
            id(),
            OpticsKind.OSCILLATING_MIRROR,
            v(x, y),
            size,
            rotationDeg = lineAngle(deg),
            oscillationDeg = amplitude,
            oscillationSpeed = speed,
        ),
    )

    fun lens(x: Float, y: Float, radius: Float, index: Float) =
        add(OpticsElement(id(), OpticsKind.LENS, v(x, y), radius, refractiveIndex = index))

    fun rock(
        x: Float,
        y: Float,
        size: Float,
        locked: Boolean = true,
        motion: OpticsMotion = OpticsMotion.FIXED,
        radius: Float = 0f,
        speed: Float = 0f,
    ) = add(
        OpticsElement(
            id(),
            OpticsKind.ROCK,
            v(x, y),
            size,
            locked = locked,
            motion = motion,
            motionRadius = radius,
            motionSpeed = speed,
        ),
    )

    fun sink(
        x: Float,
        y: Float,
        color: OpticsColor,
        radius: Float = 12f,
        motion: OpticsMotion = OpticsMotion.FIXED,
        motionRadius: Float = 0f,
        motionSpeed: Float = 0f,
    ) = add(
        OpticsElement(
            id(),
            OpticsKind.SINK,
            v(x, y),
            radius,
            color = color,
            movable = false,
            motion = motion,
            motionRadius = motionRadius,
            motionSpeed = motionSpeed,
        ),
    )

    private fun cornerElement(id: Int, kind: OpticsKind, corner: OpticsVec, incoming: OpticsVec, outgoing: OpticsVec): OpticsElement {
        val n = (outgoing - incoming).normalized()
        return when (kind) {
            OpticsKind.ARC_MIRROR -> OpticsElement(
                id,
                kind,
                corner - n * 40f,
                40f,
                rotationDeg = n.angleDeg(),
            )
            OpticsKind.POLY_MIRROR -> {
                val sides = 6
                val apothem = 40f * cos(PI.toFloat() / sides)
                OpticsElement(
                    id,
                    kind,
                    corner - n * apothem,
                    40f,
                    rotationDeg = lineAngle(n.angleDeg() - 180f / sides),
                    sides = sides,
                )
            }
            OpticsKind.RECT_MIRROR -> OpticsElement(
                id,
                kind,
                corner - n * (DrOpticsEngine.RECT_MIRROR_THICKNESS * 0.5f),
                56f,
                rotationDeg = lineAngle(n.angleDeg() - 90f),
            )
            OpticsKind.OSCILLATING_MIRROR -> OpticsElement(
                id,
                kind,
                corner,
                46f,
                rotationDeg = lineAngle(n.angleDeg() - 90f),
                oscillationDeg = 2f,
                oscillationSpeed = 1.5f,
            )
            else -> OpticsElement(
                id,
                kind,
                corner,
                46f,
                rotationDeg = lineAngle(n.angleDeg() - 90f),
            )
        }
    }

    fun route(
        laserPos: OpticsVec,
        laserDeg: Float,
        color: OpticsColor,
        corners: List<OpticsVec>,
        sinkPos: OpticsVec,
        kinds: List<OpticsKind> = emptyList(),
        laserMotion: OpticsMotion = OpticsMotion.FIXED,
        laserRadius: Float = 0f,
        laserSpeed: Float = 0f,
        sinkRadius: Float = 12f,
        sinkMotion: OpticsMotion = OpticsMotion.FIXED,
        sinkMotionRadius: Float = 0f,
        sinkMotionSpeed: Float = 0f,
    ) {
        laser(laserPos.x, laserPos.y, laserDeg, color, laserMotion, laserRadius, laserSpeed)
        var incoming = OpticsVec.dir(laserDeg)
        corners.forEachIndexed { i, corner ->
            val next = corners.getOrNull(i + 1) ?: sinkPos
            val outgoing = (next - corner).normalized()
            val kind = kinds.getOrNull(i) ?: OpticsKind.PLANE_MIRROR
            solution += cornerElement(id(), kind, corner, incoming, outgoing)
            incoming = outgoing
        }
        sink(sinkPos.x, sinkPos.y, color, sinkRadius, sinkMotion, sinkMotionRadius, sinkMotionSpeed)
    }

    /**
     * Deterministic scramble: every movable element starts turned and nudged
     * away from the authored answer, so the player has to reason it back.
     */
    private fun autoScramble() {
        solution.forEachIndexed { position, element ->
            if (!element.movable || element.locked) return@forEachIndexed
            val sign = if (position % 2 == 0) 1f else -1f
            when (element.kind) {
                OpticsKind.LENS -> tweaks += OpticsTweak(
                    element.id,
                    dx = sign * 40f,
                    dy = if (position % 4 < 2) -30f else 34f,
                )
                OpticsKind.SINK -> Unit
                else -> tweaks += OpticsTweak(
                    element.id,
                    deltaDeg = sign * (16f + (position % 4) * 6f),
                    dx = ((position % 3) - 1) * 7f,
                    dy = (((position + 1) % 3) - 1) * 7f,
                )
            }
        }
    }

    fun finish(): DrOpticsLevel {
        autoScramble()
        return DrOpticsLevel(
            index = index,
            name = name,
            difficulty = difficulty,
            maxBeams = maxBeams,
            maxMoves = maxMoves,
            solution = solution.toList(),
            tweaks = tweaks.toList(),
        )
    }
}

private fun draft(index: Int, difficulty: Int, block: OpticsLevelDraft.() -> Unit): DrOpticsLevel =
    OpticsLevelDraft(index, difficulty).apply(block).finish()

private fun buildLevels(): List<DrOpticsLevel> = listOf(
    // ---------------------------------------------------- difficulty 1 ----
    draft(0, 1) {
        name = "first light"
        maxBeams = 24
        maxMoves = 22
        route(v(30f, 136f), 0f, OpticsColor.RED, listOf(v(250f, 136f)), v(250f, 50f))
    },
    draft(1, 1) {
        name = "turn down"
        maxBeams = 24
        maxMoves = 22
        route(v(30f, 100f), 0f, OpticsColor.RED, listOf(v(250f, 100f)), v(250f, 222f))
    },
    draft(2, 1) {
        name = "from above"
        maxBeams = 24
        maxMoves = 22
        route(v(136f, 30f), 90f, OpticsColor.BLUE, listOf(v(136f, 200f)), v(410f, 200f))
    },
    draft(3, 1) {
        name = "left field"
        maxBeams = 24
        maxMoves = 22
        route(v(450f, 136f), 180f, OpticsColor.GREEN, listOf(v(230f, 136f)), v(230f, 240f))
    },
    draft(4, 1) {
        name = "upward"
        maxBeams = 24
        maxMoves = 26
        route(v(450f, 150f), 180f, OpticsColor.BLUE, listOf(v(230f, 150f)), v(230f, 50f))
    },
    draft(5, 1) {
        name = "slant"
        maxBeams = 24
        maxMoves = 26
        route(v(30f, 220f), -35f, OpticsColor.GREEN, listOf(v(250f, 66f)), v(430f, 66f))
    },
    draft(6, 1) {
        name = "around the rock"
        maxBeams = 30
        maxMoves = 30
        rock(250f, 136f, 48f)
        route(v(30f, 136f), 0f, OpticsColor.RED, listOf(v(140f, 136f), v(140f, 60f)), v(440f, 60f))
    },
    draft(7, 1) {
        name = "two turns"
        maxBeams = 30
        maxMoves = 30
        route(v(30f, 136f), 0f, OpticsColor.BLUE, listOf(v(150f, 136f), v(150f, 230f)), v(420f, 230f))
    },
    // ---------------------------------------------------- difficulty 2 ----
    draft(8, 2) {
        name = "dogleg"
        maxBeams = 36
        maxMoves = 34
        route(v(30f, 60f), 0f, OpticsColor.RED, listOf(v(180f, 60f), v(180f, 200f)), v(430f, 200f))
    },
    draft(9, 2) {
        name = "up and over"
        maxBeams = 36
        maxMoves = 34
        route(v(30f, 220f), 0f, OpticsColor.GREEN, listOf(v(160f, 220f), v(160f, 60f), v(400f, 60f)), v(400f, 220f))
    },
    draft(10, 2) {
        name = "uphill"
        maxBeams = 36
        maxMoves = 34
        route(v(30f, 200f), -30f, OpticsColor.BLUE, listOf(v(200f, 102f)), v(200f, 40f))
    },
    draft(11, 2) {
        name = "zigzag"
        maxBeams = 36
        maxMoves = 34
        route(v(30f, 136f), 0f, OpticsColor.RED, listOf(v(140f, 136f), v(140f, 80f), v(300f, 80f)), v(300f, 230f))
    },
    draft(12, 2) {
        name = "rock field"
        maxBeams = 36
        maxMoves = 38
        rock(250f, 80f, 46f)
        rock(350f, 40f, 42f)
        rock(250f, 250f, 40f)
        route(v(30f, 136f), 0f, OpticsColor.GREEN, listOf(v(140f, 136f), v(140f, 220f)), v(430f, 220f))
    },
    draft(13, 2) {
        name = "slalom"
        maxBeams = 36
        maxMoves = 38
        route(
            v(30f, 40f), 0f, OpticsColor.BLUE,
            listOf(v(120f, 40f), v(120f, 120f), v(300f, 120f), v(300f, 200f)),
            v(450f, 200f),
        )
    },
    draft(14, 2) {
        name = "steep"
        maxBeams = 36
        maxMoves = 38
        route(v(30f, 240f), -60f, OpticsColor.RED, listOf(v(150f, 32f)), v(450f, 32f))
    },
    draft(15, 2) {
        name = "glass bend"
        maxBeams = 36
        maxMoves = 38
        laser(30f, 110f, 0f, OpticsColor.BLUE)
        lens(250f, 136f, 40f, 1.5f)
        rock(350f, 110f, 48f)
        sink(420f, 203f, OpticsColor.BLUE)
    },
    draft(16, 2) {
        name = "lens chute"
        maxBeams = 36
        maxMoves = 38
        laser(30f, 150f, 0f, OpticsColor.GREEN)
        lens(260f, 136f, 44f, 1.6f)
        rock(360f, 150f, 48f)
        sink(440f, 105f, OpticsColor.GREEN)
    },
    draft(17, 2) {
        name = "arc turn"
        maxBeams = 36
        maxMoves = 38
        route(v(30f, 136f), 0f, OpticsColor.RED, listOf(v(240f, 136f)), v(240f, 40f), kinds = listOf(OpticsKind.ARC_MIRROR))
    },
    draft(18, 2) {
        name = "arc bend"
        maxBeams = 36
        maxMoves = 38
        route(v(30f, 220f), -35f, OpticsColor.GREEN, listOf(v(250f, 66f)), v(430f, 66f), kinds = listOf(OpticsKind.ARC_MIRROR))
    },
    draft(19, 2) {
        name = "polygon corner"
        maxBeams = 36
        maxMoves = 38
        route(v(30f, 136f), 0f, OpticsColor.BLUE, listOf(v(250f, 136f)), v(250f, 50f), kinds = listOf(OpticsKind.POLY_MIRROR))
    },
    draft(20, 2) {
        name = "polygon zig"
        maxBeams = 36
        maxMoves = 42
        route(
            v(30f, 60f), 0f, OpticsColor.RED,
            listOf(v(170f, 60f), v(170f, 210f)),
            v(430f, 210f),
            kinds = listOf(OpticsKind.POLY_MIRROR, OpticsKind.PLANE_MIRROR),
        )
    },
    draft(21, 2) {
        name = "block turn"
        maxBeams = 36
        maxMoves = 38
        route(v(30f, 136f), 0f, OpticsColor.GREEN, listOf(v(250f, 136f)), v(250f, 50f), kinds = listOf(OpticsKind.RECT_MIRROR))
    },
    draft(22, 3) {
        name = "block double"
        maxBeams = 36
        maxMoves = 42
        route(
            v(30f, 220f), 0f, OpticsColor.BLUE,
            listOf(v(180f, 220f), v(180f, 60f)),
            v(430f, 60f),
            kinds = listOf(OpticsKind.RECT_MIRROR, OpticsKind.RECT_MIRROR),
        )
    },
    // ---------------------------------------------------- difficulty 3 ----
    draft(23, 3) {
        name = "color crossing"
        maxBeams = 48
        maxMoves = 42
        laser(180f, 30f, 90f, OpticsColor.BLUE)
        sink(180f, 136f, OpticsColor.BLUE)
        route(v(30f, 136f), 0f, OpticsColor.RED, listOf(v(300f, 136f)), v(300f, 50f))
    },
    draft(24, 3) {
        name = "moving beam"
        maxBeams = 48
        maxMoves = 44
        route(
            v(136f, 30f), 90f, OpticsColor.GREEN,
            listOf(v(136f, 200f)),
            v(430f, 200f),
            laserMotion = OpticsMotion.MOVING,
            laserRadius = 5f,
            laserSpeed = 1.2f,
        )
    },
    draft(25, 3) {
        name = "orbit source"
        maxBeams = 48
        maxMoves = 44
        route(
            v(30f, 136f), 0f, OpticsColor.BLUE,
            listOf(v(200f, 136f), v(200f, 60f)),
            v(440f, 60f),
            laserMotion = OpticsMotion.ORBITING,
            laserRadius = 4f,
            laserSpeed = 2f,
        )
    },
    draft(26, 3) {
        name = "arc pair"
        maxBeams = 48
        maxMoves = 44
        route(
            v(30f, 240f), -45f, OpticsColor.RED,
            listOf(v(180f, 90f), v(380f, 90f)),
            v(380f, 40f),
            kinds = listOf(OpticsKind.ARC_MIRROR, OpticsKind.PLANE_MIRROR),
        )
    },
    draft(27, 3) {
        name = "prism run"
        maxBeams = 48
        maxMoves = 48
        route(
            v(30f, 136f), 0f, OpticsColor.GREEN,
            listOf(v(150f, 136f), v(150f, 60f)),
            v(430f, 60f),
            kinds = listOf(OpticsKind.POLY_MIRROR, OpticsKind.ARC_MIRROR),
        )
    },
    draft(28, 3) {
        name = "hard shapes"
        maxBeams = 48
        maxMoves = 48
        route(
            v(30f, 220f), 0f, OpticsColor.BLUE,
            listOf(v(160f, 220f), v(160f, 70f)),
            v(450f, 70f),
            kinds = listOf(OpticsKind.RECT_MIRROR, OpticsKind.POLY_MIRROR),
        )
    },
    draft(29, 3) {
        name = "gauntlet"
        maxBeams = 48
        maxMoves = 48
        rock(120f, 60f, 44f)
        rock(360f, 220f, 44f)
        rock(250f, 30f, 36f)
        route(v(30f, 220f), -35f, OpticsColor.RED, listOf(v(250f, 66f)), v(430f, 66f), kinds = listOf(OpticsKind.ARC_MIRROR))
    },
    // ---------------------------------------------------- difficulty 4 ----
    draft(30, 4) {
        name = "oscillating gate"
        maxBeams = 60
        maxMoves = 48
        laser(30f, 136f, 0f, OpticsColor.RED)
        oscillating(240f, 136f, -45f, amplitude = 2f, speed = 1.5f)
        sink(240f, 80f, OpticsColor.RED, radius = 14f)
    },
    draft(31, 4) {
        name = "rock belt"
        maxBeams = 60
        maxMoves = 52
        rock(300f, 60f, 40f, motion = OpticsMotion.MOVING, radius = 7f, speed = 0.9f)
        rock(300f, 250f, 40f, motion = OpticsMotion.MOVING, radius = 7f, speed = 1.3f)
        route(v(30f, 60f), 0f, OpticsColor.GREEN, listOf(v(180f, 60f), v(180f, 210f)), v(430f, 210f))
    },
    draft(32, 4) {
        name = "chasing target"
        maxBeams = 60
        maxMoves = 52
        laser(30f, 136f, 0f, OpticsColor.BLUE)
        plane(220f, 136f, 45f)
        sink(220f, 240f, OpticsColor.BLUE, radius = 16f, motion = OpticsMotion.MOVING, motionRadius = 8f, motionSpeed = 1.8f)
    },
    draft(33, 4) {
        name = "glass and grit"
        maxBeams = 60
        maxMoves = 52
        laser(30f, 150f, 0f, OpticsColor.GREEN)
        lens(260f, 136f, 44f, 1.6f)
        rock(360f, 150f, 48f)
        sink(440f, 105f, OpticsColor.GREEN, radius = 12f)
    },
    draft(34, 4) {
        name = "swinging arc"
        maxBeams = 60
        maxMoves = 56
        oscillating(400f, 220f, 30f, amplitude = 3f, speed = 1.1f)
        route(v(30f, 220f), -35f, OpticsColor.RED, listOf(v(250f, 66f)), v(430f, 66f), kinds = listOf(OpticsKind.ARC_MIRROR))
    },
    draft(35, 4) {
        name = "twin timing"
        maxBeams = 60
        maxMoves = 56
        route(
            v(30f, 70f), 0f, OpticsColor.RED,
            listOf(v(200f, 70f)),
            v(200f, 30f),
            kinds = listOf(OpticsKind.OSCILLATING_MIRROR),
        )
        route(v(30f, 200f), 0f, OpticsColor.BLUE, listOf(v(320f, 200f)), v(320f, 250f))
    },
    // ---------------------------------------------------- difficulty 5 ----
    draft(36, 5) {
        name = "three lanes"
        maxBeams = 75
        maxMoves = 60
        route(v(30f, 70f), 0f, OpticsColor.RED, listOf(v(180f, 70f)), v(180f, 30f))
        route(v(30f, 136f), 0f, OpticsColor.GREEN, listOf(v(300f, 136f)), v(300f, 76f))
        route(v(30f, 202f), 0f, OpticsColor.BLUE, listOf(v(180f, 202f)), v(180f, 242f))
    },
    draft(37, 5) {
        name = "orbit weave"
        maxBeams = 75
        maxMoves = 60
        route(
            v(30f, 240f), 0f, OpticsColor.BLUE,
            listOf(v(120f, 240f), v(120f, 100f), v(300f, 100f), v(300f, 240f)),
            v(440f, 240f),
            laserMotion = OpticsMotion.ORBITING,
            laserRadius = 5f,
            laserSpeed = 1.6f,
        )
    },
    draft(38, 5) {
        name = "moving parts"
        maxBeams = 75
        maxMoves = 60
        rock(260f, 40f, 40f)
        route(
            v(136f, 30f), 90f, OpticsColor.GREEN,
            listOf(v(136f, 200f)),
            v(430f, 200f),
            laserMotion = OpticsMotion.MOVING,
            laserRadius = 5f,
            laserSpeed = 1.4f,
            sinkRadius = 16f,
            sinkMotion = OpticsMotion.MOVING,
            sinkMotionRadius = 8f,
            sinkMotionSpeed = 1.7f,
        )
    },
    draft(39, 5) {
        name = "master circuit"
        maxBeams = 75
        maxMoves = 64
        rock(60f, 60f, 40f)
        route(
            v(30f, 136f), 0f, OpticsColor.RED,
            listOf(v(120f, 136f), v(120f, 40f), v(340f, 40f), v(340f, 200f)),
            v(450f, 200f),
            kinds = listOf(
                OpticsKind.PLANE_MIRROR,
                OpticsKind.POLY_MIRROR,
                OpticsKind.ARC_MIRROR,
                OpticsKind.PLANE_MIRROR,
            ),
        )
    },
    // ---------------------------------------------- difficulty 6 special --
    draft(40, 6) {
        name = "the light lab"
        maxBeams = 75
        maxMoves = 72
        rock(80f, 250f, 40f)
        oscillating(420f, 120f, 20f, amplitude = 4f, speed = 1.4f)
        route(v(30f, 70f), 0f, OpticsColor.RED, listOf(v(160f, 70f), v(160f, 20f)), v(420f, 20f))
        route(v(30f, 136f), 0f, OpticsColor.GREEN, listOf(v(250f, 136f), v(250f, 240f)), v(430f, 240f))
        route(v(30f, 202f), 0f, OpticsColor.BLUE, listOf(v(350f, 202f)), v(350f, 250f))
    },
)

