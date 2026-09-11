package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * Trash Throw (official "TrashThrow" re-creation): a pseudo-3D flick-to-basket
 * game. Pure Kotlin and deterministic; behaviour is re-derived from the spec
 * (`docs/apps/trash-throw.md`) with no copied code or art.
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

data class Projected(val x: Float, val y: Float, val scale: Float)

enum class ThrowPhase { WAITING, FLYING, RESOLVED }

enum class ThrowResult { NONE, MADE, MISS }

/**
 * Scene constants from the reference: camera height, goal geometry (base and
 * top radii, top height), fixed throw push (up/forward newtons) and wind max.
 * The ball has a 0.1 m radius and 1 kg mass, so newtons are m/s^2 here.
 */
enum class TrashScene(
    val label: String,
    val cameraHeight: Float,
    val goalX: Float,
    val goalZ: Float,
    val radiusBase: Float,
    val radiusTop: Float,
    val topHeight: Float,
    val throwUp: Float,
    val throwForward: Float,
    val windMax: Float,
) {
    BEDROOM("bedroom", 3.2f, 0.017f, 8.75f, 0.175f, 0.20f, 0.35f, 300f, 310f, 0.5f),
    DENTIST("dentist", 3.2f, 0.05f, 10.25f, 0.30f, 0.30f, 0.50f, 340f, 350f, 3.0f),
    OFFICE("office", 2.8f, 0.017f, 9.9f, 0.19f, 0.25f, 0.60f, 350f, 350f, 4.0f),
}

/**
 * Immutable throw snapshot. The ball is held (no gravity, no wind) during
 * [ThrowPhase.WAITING]; [wind] only acts once [ThrowPhase.FLYING]. A resolved
 * throw counts a [RESET_MS] cadence before the next wind roll and hold.
 */
data class TrashThrowState(
    val scene: TrashScene,
    val phase: ThrowPhase = ThrowPhase.WAITING,
    val pos: Vec3 = TrashThrowEngine.startPosition(scene),
    val vel: Vec3 = Vec3(0f, 0f, 0f),
    val wind: Float = 0f,
    val streak: Int = 0,
    val best: Int = 0,
    val groundTouches: Int = 0,
    val result: ThrowResult = ThrowResult.NONE,
    val resetMs: Long = 0,
    val fadeMs: Long = 0,
    val seed: Int = 0,
)

object TrashThrowEngine {
    const val BALL_RADIUS = 0.1f
    const val GRAVITY = 9.8f
    const val MASS_KG = 1f

    /** Swipe force: dx/10, clamped to +/-15, then x5 => +/-75 N. */
    const val SWIPE_DIVISOR = 10f
    const val SWIPE_CLAMP = 15f
    const val SWIPE_FORCE_SCALE = 5f

    /** Wind applies windSpeed * 0.5 on X while airborne. */
    const val WIND_FACTOR = 0.5f

    /** Ground reflects at 50% and the second touch settles the ball. */
    const val GROUND_RESTITUTION = 0.5f

    /** Resolve wait: 30 ticks (~0.5 s at 60 fps); fade: 11 frames. */
    const val RESET_TICKS = 30
    const val FADE_FRAMES = 11
    const val FRAME_MS = 1000L / 60L
    const val RESET_MS = RESET_TICKS * FRAME_MS
    const val FADE_MS = FADE_FRAMES * FRAME_MS

    /** Shared touch handler: swipe >= 30 px and <= 60 ticks. */
    const val SWIPE_MIN_PX = 30f
    const val SWIPE_MAX_TICKS = 60

    const val SCREEN_WIDTH = 272f
    const val SCREEN_HEIGHT = 480f
    const val FOV_DEGREES = 23.2f
    const val CENTER_X = 136f
    const val CENTER_Y = 36f

    private const val BALL_OFFSET_Y = -0.45f
    private const val BALL_OFFSET_Z = 0.5f
    private const val BALL_FORWARD = 1.25f
    private const val BALL_DROP = 0.75f

    /** Camera + (0,-0.45,0.5), then +1.25 forward and -0.75 down. */
    fun startPosition(scene: TrashScene): Vec3 = Vec3(
        x = 0f,
        y = scene.cameraHeight + BALL_OFFSET_Y - BALL_DROP,
        z = BALL_OFFSET_Z + BALL_FORWARD,
    )

    fun focalLength(): Float =
        (SCREEN_WIDTH / 2f) / tan((FOV_DEGREES / 2f) * (PI / 180f)).toFloat()

    /** Pinhole projection onto the 272x480 scene canvas. */
    fun project(scene: TrashScene, point: Vec3): Projected {
        if (point.z <= 0.05f) return Projected(0f, 0f, 0f)
        val f = focalLength()
        return Projected(
            x = CENTER_X + f * point.x / point.z,
            y = CENTER_Y - f * (point.y - scene.cameraHeight) / point.z,
            scale = f / point.z,
        )
    }

    /** A fresh run: the first wind is rolled immediately (never exactly 0). */
    fun newGame(scene: TrashScene, seed: Int, rng: Random = Random(seed)): TrashThrowState {
        val wind = rollWind(scene, rng)
        return TrashThrowState(scene = scene, wind = wind, seed = rng.nextInt())
    }

    fun rollWind(scene: TrashScene, rng: Random): Float {
        while (true) {
            val wind = (rng.nextFloat() - 0.5f) * 2f * scene.windMax
            if (wind != 0f) return wind
        }
    }

    /** Force vector for a horizontal swipe: X from the swipe, Y/Z fixed. */
    fun throwVelocity(scene: TrashScene, dxPx: Float): Vec3 {
        val lateral = (dxPx / SWIPE_DIVISOR).coerceIn(-SWIPE_CLAMP, SWIPE_CLAMP) * SWIPE_FORCE_SCALE
        return Vec3(x = lateral, y = scene.throwUp, z = scene.throwForward)
    }

    /** Shared swipe classification: distance and duration gates. */
    fun isSwipe(dxPx: Float, dyPx: Float, durationTicks: Int): Boolean =
        sqrt(dxPx * dxPx + dyPx * dyPx) >= SWIPE_MIN_PX && durationTicks <= SWIPE_MAX_TICKS

    /** Releases the held ball; no-op unless waiting for a flick. */
    fun flick(state: TrashThrowState, dxPx: Float): TrashThrowState {
        if (state.phase != ThrowPhase.WAITING) return state
        return state.copy(
            phase = ThrowPhase.FLYING,
            vel = throwVelocity(state.scene, dxPx),
            groundTouches = 0,
            result = ThrowResult.NONE,
            fadeMs = 0,
        )
    }

    /**
     * One physics step. Waiting balls are held; airborne balls integrate
     * gravity, wind and collisions; resolved balls count down the reset.
     */
    fun step(state: TrashThrowState, dtMs: Long): TrashThrowState {
        return when (state.phase) {
            ThrowPhase.WAITING -> state
            ThrowPhase.FLYING -> stepFlight(state, dtMs)
            ThrowPhase.RESOLVED -> {
                val remaining = state.resetMs - dtMs
                val faded = (state.fadeMs - dtMs).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    nextThrow(state)
                } else {
                    state.copy(resetMs = remaining, fadeMs = faded)
                }
            }
        }
    }

    private fun stepFlight(state: TrashThrowState, dtMs: Long): TrashThrowState {
        val dt = dtMs / 1000f
        val previous = state.pos
        var vx = state.vel.x + state.wind * WIND_FACTOR * dt
        var vy = state.vel.y - GRAVITY * dt
        var vz = state.vel.z
        var x = previous.x + vx * dt
        var y = previous.y + vy * dt
        var z = previous.z + vz * dt
        var groundTouches = state.groundTouches
        var fadeMs = state.fadeMs

        val goalTop = state.scene.topHeight
        val relX = x - state.scene.goalX
        val relZ = z - state.scene.goalZ
        val horizontal = sqrt(relX * relX + relZ * relZ)

        // Dropped through the opening.
        if (vy < 0f && previous.y >= goalTop && y < goalTop && horizontal < state.scene.radiusTop) {
            return resolve(state.copy(pos = Vec3(x, y, z), vel = Vec3(vx, vy, vz)), ThrowResult.MADE)
        }

        // Full-efficiency rim reflection: only when the fall crosses the rim.
        if (previous.y >= goalTop && y < goalTop &&
            horizontal in (state.scene.radiusTop - BALL_RADIUS)..(state.scene.radiusTop + BALL_RADIUS) &&
            horizontal > 0.0001f
        ) {
            val nx = relX / horizontal
            val nz = relZ / horizontal
            val along = vx * nx + vz * nz
            vx -= 2f * along * nx
            vz -= 2f * along * nz
            if (vy < 0f) vy = -vy
            y = goalTop + BALL_RADIUS * 0.5f
        }

        if (y - BALL_RADIUS <= 0f) {
            y = BALL_RADIUS
            if (groundTouches == 0) {
                vy = -vy * GROUND_RESTITUTION
                groundTouches = 1
                fadeMs = FADE_MS
            } else {
                return resolve(
                    state.copy(
                        pos = Vec3(x, y, z),
                        vel = Vec3(0f, 0f, 0f),
                        groundTouches = groundTouches + 1,
                    ),
                    ThrowResult.MISS,
                )
            }
        }

        return state.copy(
            pos = Vec3(x, y, z),
            vel = Vec3(vx, vy, vz),
            groundTouches = groundTouches,
            fadeMs = fadeMs,
        )
    }

    private fun resolve(state: TrashThrowState, result: ThrowResult): TrashThrowState {
        val streak = if (result == ThrowResult.MADE) state.streak + 1 else 0
        return state.copy(
            phase = ThrowPhase.RESOLVED,
            result = result,
            streak = streak,
            best = max(state.best, streak),
            resetMs = RESET_MS,
            fadeMs = FADE_MS,
        )
    }

    /** After the reset cadence: re-roll wind and return the ball to the hold. */
    fun nextThrow(state: TrashThrowState): TrashThrowState {
        val rng = Random(state.seed)
        val wind = rollWind(state.scene, rng)
        return state.copy(
            phase = ThrowPhase.WAITING,
            pos = startPosition(state.scene),
            vel = Vec3(0f, 0f, 0f),
            wind = wind,
            groundTouches = 0,
            result = ThrowResult.NONE,
            resetMs = 0,
            fadeMs = 0,
            seed = rng.nextInt(),
        )
    }

    /* ---------------------------------------------------------------- */
    /*                      Per-scene best scores                       */
    /* ---------------------------------------------------------------- */

    data class TrashScores(
        val perScene: Map<TrashScene, Int> = emptyMap(),
    ) {
        val overall: Int get() = perScene.values.maxOrNull() ?: 0

        fun best(scene: TrashScene): Int = perScene[scene] ?: 0
    }

    fun recordBest(scores: TrashScores, scene: TrashScene, streak: Int): TrashScores {
        if (streak <= scores.best(scene)) return scores
        return TrashScores(scores.perScene + (scene to streak))
    }

    fun encodeScores(scores: TrashScores): String =
        TrashScene.entries.joinToString(";") { "${it.label}:${scores.best(it)}" }

    fun decodeScores(blob: String?): TrashScores {
        if (blob.isNullOrBlank()) return TrashScores()
        val map = HashMap<TrashScene, Int>()
        blob.split(";").forEach { part ->
            val bits = part.split(":")
            if (bits.size != 2) return@forEach
            val scene = TrashScene.entries.firstOrNull { it.label == bits[0] } ?: return@forEach
            val value = bits[1].toIntOrNull() ?: return@forEach
            map[scene] = value
        }
        return TrashScores(map)
    }
}
