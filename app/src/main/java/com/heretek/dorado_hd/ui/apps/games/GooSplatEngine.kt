package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Goo Splat — clean-room engine for the Zune HD jelly-splatting game.
 *
 * Behaviour re-derived from docs/apps/goo-splat.md; no Microsoft code or
 * assets. Jellies drop from a band above a rising sludge pool; the player taps
 * them before the sludge absorbs them, uses power-ups to clear the board and
 * drives the sludge surface ("level", a y coordinate) toward the fail line.
 * Coordinates are the device design space (272x480, y grows downward).
 */
const val GOO_VIEW_W = 272f
const val GOO_VIEW_H = 480f
const val GOO_FRAME_MS = 1000f / 30f
const val GOO_JELLY_RADIUS = 30f
const val GOO_SLUDGE_START = 370f
const val GOO_SLUDGE_CAP = 420f
const val GOO_SLUDGE_SPEED = 20f
const val GOO_SLUDGE_LEVEL_STEP = 15f
const val GOO_FAIL_LEVEL = 70f
const val GOO_SPAWN_TICK_S = 1.25f
const val GOO_SPAWN_TICK_STEP_S = 0.015f
const val GOO_SPAWN_X_MIN = 35f
const val GOO_SPAWN_X_MAX = 237f
const val GOO_SPAWN_Y_MIN = -20f
const val GOO_SPAWN_TRIES = 60
const val GOO_SPAWN_CLEARANCE = 20f + GOO_JELLY_RADIUS
const val GOO_SHADOW_MS = 400L
const val GOO_HIT_WALL_MS = 100L
const val GOO_STALL_BASE_S = 0.5f
const val GOO_STALL_PER_LEVEL_S = 0.03f
const val GOO_FALL_GRAVITY = 700f
const val GOO_FREEZE_MS = 5_000L
const val GOO_SQUISH_FADE_MS = 1_000L
const val GOO_MERGE_SPEED = 200f
const val GOO_MERGE_STALL_S = 0.05f
const val GOO_PEPPER_ODDS = 500
const val GOO_PEPPER_MIN_SLUDGE = 280f
const val GOO_PEPPER_BONUS_TARGET = 50f
const val GOO_PEPPER_POINTS = 3
const val GOO_TARGET_START = 5
const val GOO_TARGET_STEP_START = 5
const val GOO_EXPLOSION_RADIUS = 130f

enum class JellyKind(val value: Int, val powerUp: Boolean = false) {
    SMALL_GREEN(1),
    LARGE_GREEN(5),
    BOMB(3, powerUp = true),
    ICE(3, powerUp = true),
    ELECTRIC(3, powerUp = true),
}

enum class JellyState { SHADOW, DESCENDING, HIT_WALL, STALL, SLIDE, SQUISHED }

data class Jelly(
    val id: Int,
    val kind: JellyKind,
    val x: Float,
    val y: Float,
    val vy: Float = 0f,
    val vx: Float = 0f,
    val speed: Float = 30f,
    val state: JellyState = JellyState.SHADOW,
    val stateMs: Long = 0L,
    val falling: Boolean = false,
    val frozen: Boolean = false,
    val cracked: Boolean = false,
    val thawMs: Long = 0L,
    val squishMs: Long = 0L,
    val stallOverrideMs: Long = 0L,
) {
    val radius: Float get() = GOO_JELLY_RADIUS

    /** Falling jellies are worth double. */
    val value: Int get() = kind.value * (if (falling) 2 else 1)

    fun onFloor(): Boolean =
        state == JellyState.HIT_WALL || state == JellyState.STALL || state == JellyState.SLIDE

    fun contains(px: Float, py: Float): Boolean {
        val pad = if (kind == JellyKind.SMALL_GREEN || falling) 10f else 0f
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy < (radius + pad) * (radius + pad)
    }
}

data class Pepper(val x: Float, val y: Float, val vx: Float, val vy: Float = 0f) {
    fun contains(px: Float, py: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy < GOO_JELLY_RADIUS * GOO_JELLY_RADIUS
    }
}

data class GooBreakdown(
    val green: Int = 0,
    val red: Int = 0,
    val blue: Int = 0,
    val yellow: Int = 0,
    val peppers: Int = 0,
)

sealed interface GooEvent {
    data object Materialize : GooEvent
    data object Splat : GooEvent
    data object SplatLarge : GooEvent
    data object CrackFrozen : GooEvent
    data object ShatterFrozen : GooEvent
    data object Bomb : GooEvent
    data object Freeze : GooEvent
    data object Shock : GooEvent
    data object Pepper : GooEvent
    data object Merge : GooEvent
    data object HitWall : GooEvent
    data object LevelUp : GooEvent
    data object GameOver : GooEvent
}

data class GooSplatState(
    val level: Int,
    val score: Int,
    val scoreTarget: Int,
    val targetStep: Int,
    val sludgeLevel: Float,
    val sludgeTarget: Float,
    val spawnClockMs: Long,
    val elapsedMs: Long,
    val jellies: List<Jelly>,
    val pepper: Pepper?,
    val nextId: Int,
    val seed: Int,
    val breakdown: GooBreakdown,
    val over: Boolean = false,
    val paused: Boolean = false,
    val events: List<GooEvent> = emptyList(),
)

class GooSplatRandom(seed: Int) {
    var state: Int = seed

    fun nextSeed(): Int {
        state = state * 1103515245 + 12345
        return state
    }

    fun next(bound: Int): Int {
        if (bound <= 0) return 0
        val raw = (nextSeed() ushr 16) and 0x7FFF
        return raw % bound
    }
}

object GooSplatEngine {

    fun newGame(seed: Int): GooSplatState = GooSplatState(
        level = 1,
        score = 0,
        scoreTarget = GOO_TARGET_START,
        targetStep = GOO_TARGET_STEP_START,
        sludgeLevel = GOO_SLUDGE_START,
        sludgeTarget = GOO_SLUDGE_START,
        spawnClockMs = 0L,
        elapsedMs = 0L,
        jellies = emptyList(),
        pepper = null,
        nextId = 1,
        seed = seed,
        breakdown = GooBreakdown(),
    )

    /** Spawn batch ceiling: 3 below level 4, 4 for levels 4-8, 5 above 8. */
    fun batchMax(level: Int): Int = when {
        level < 4 -> 3
        level <= 8 -> 4
        else -> 5
    }

    /** 1.25 s shrinking by 0.015 s per level (integer ms to stay exact). */
    fun spawnTickMs(level: Int): Long =
        (1_250L - 15L * (level - 1)).coerceAtLeast(250L)

    fun stallSeconds(level: Int): Float =
        max(0.05f, GOO_STALL_BASE_S - GOO_STALL_PER_LEVEL_S * level)

    fun descentSpeed(level: Int, roll: Float): Float =
        (25f + roll * 15f) * max(1f, level / 5.5f)

    fun absorbTarget(target: Float): Float = target - GOO_JELLY_RADIUS / 1.75f

    fun rollKind(roll: Int): JellyKind = when {
        roll < 82 -> JellyKind.SMALL_GREEN
        roll < 89 -> JellyKind.BOMB
        roll < 95 -> JellyKind.ICE
        else -> JellyKind.ELECTRIC
    }

    private fun bump(breakdown: GooBreakdown, kind: JellyKind): GooBreakdown = when (kind) {
        JellyKind.SMALL_GREEN, JellyKind.LARGE_GREEN -> breakdown.copy(green = breakdown.green + 1)
        JellyKind.BOMB -> breakdown.copy(red = breakdown.red + 1)
        JellyKind.ICE -> breakdown.copy(blue = breakdown.blue + 1)
        JellyKind.ELECTRIC -> breakdown.copy(yellow = breakdown.yellow + 1)
    }

    private fun moveToward(current: Float, target: Float, maxDelta: Float): Float = when {
        current < target -> min(target, current + maxDelta)
        current > target -> max(target, current - maxDelta)
        else -> current
    }

    private fun distance(a: Jelly, b: Jelly): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * A spawn candidate must be clear of every jelly within 20 + radius px;
     * up to 60 tries (x 35..237, y -20..sludge level - 40). Returns null when
     * the band is too crowded.
     */
    fun rollSpawnPosition(
        jellies: List<Jelly>,
        sludgeLevel: Float,
        rng: GooSplatRandom,
    ): Pair<Float, Float>? {
        val yMax = sludgeLevel - 40f
        val ySpan = (yMax - GOO_SPAWN_Y_MIN).toInt().coerceAtLeast(1)
        repeat(GOO_SPAWN_TRIES) {
            val x = GOO_SPAWN_X_MIN + rng.next((GOO_SPAWN_X_MAX - GOO_SPAWN_X_MIN).toInt() + 1)
            val y = GOO_SPAWN_Y_MIN + rng.next(ySpan)
            val clear = jellies.none { j ->
                val dx = x - j.x
                val dy = y - j.y
                dx * dx + dy * dy < GOO_SPAWN_CLEARANCE * GOO_SPAWN_CLEARANCE
            }
            if (clear) return x to y
        }
        return null
    }

    fun step(state: GooSplatState, dtMs: Long, taps: List<BugTap> = emptyList()): GooSplatState {
        if (state.paused || state.over || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<GooEvent>()
        val dt = dtMs / 1000f
        val rng = GooSplatRandom(state.seed)
        var nextId = state.nextId
        var score = state.score
        var breakdown = state.breakdown
        var sludgeTarget = state.sludgeTarget
        var jellies = state.jellies.toMutableList()
        var pepper = state.pepper

        // ---- input ------------------------------------------------------
        for (tap in taps) {
            val p = pepper
            if (p != null && p.contains(tap.x, tap.y)) {
                var gained = GOO_PEPPER_POINTS
                for (j in jellies) {
                    if (j.state == JellyState.SHADOW || j.state == JellyState.SQUISHED) continue
                    gained += j.value
                    breakdown = bump(breakdown, j.kind)
                }
                score += gained
                jellies = mutableListOf()
                sludgeTarget = min(GOO_SLUDGE_CAP, sludgeTarget + GOO_PEPPER_BONUS_TARGET)
                pepper = null
                breakdown = breakdown.copy(peppers = breakdown.peppers + 1)
                events += GooEvent.Pepper
                continue
            }
            val index = jellies.indexOfLast {
                it.state != JellyState.SHADOW && it.state != JellyState.SQUISHED && it.contains(tap.x, tap.y)
            }
            if (index < 0) continue
            val jelly = jellies[index]
            if (jelly.frozen) {
                if (!jelly.cracked) {
                    jellies[index] = jelly.copy(cracked = true)
                    events += GooEvent.CrackFrozen
                } else {
                    score += jelly.value
                    jellies[index] = jelly.copy(
                        state = JellyState.SQUISHED,
                        squishMs = 0L,
                        frozen = false,
                        cracked = false,
                    )
                    breakdown = bump(breakdown, jelly.kind)
                    events += GooEvent.ShatterFrozen
                }
                continue
            }
            when (jelly.kind) {
                JellyKind.SMALL_GREEN, JellyKind.LARGE_GREEN -> {
                    score += jelly.value
                    jellies[index] = jelly.copy(state = JellyState.SQUISHED, squishMs = 0L)
                    breakdown = bump(breakdown, jelly.kind)
                    events += if (jelly.kind == JellyKind.LARGE_GREEN) GooEvent.SplatLarge else GooEvent.Splat
                }
                JellyKind.BOMB -> {
                    var gained = jelly.value
                    val survivors = mutableListOf<Jelly>()
                    for (j in jellies) {
                        if (j.id == jelly.id) continue
                        val near = j.state != JellyState.SHADOW && j.state != JellyState.SQUISHED &&
                            distance(j, jelly) <= GOO_EXPLOSION_RADIUS
                        if (near) {
                            gained += j.value
                            breakdown = bump(breakdown, j.kind)
                        } else {
                            survivors += j
                        }
                    }
                    score += gained
                    jellies = survivors
                    events += GooEvent.Bomb
                }
                JellyKind.ICE -> {
                    var gained = jelly.value
                    val survivors = mutableListOf<Jelly>()
                    for (j in jellies) {
                        if (j.id == jelly.id) continue
                        when {
                            j.state == JellyState.SQUISHED -> survivors += j
                            j.frozen -> {
                                gained += j.value
                                breakdown = bump(breakdown, j.kind)
                            }
                            else -> survivors += j.copy(frozen = true, cracked = false, thawMs = 0L, falling = false)
                        }
                    }
                    score += gained
                    jellies = survivors
                    events += GooEvent.Freeze
                }
                JellyKind.ELECTRIC -> {
                    score += jelly.value
                    jellies = jellies.map { j ->
                        if (j.id == jelly.id) {
                            j
                        } else if (j.state != JellyState.SHADOW && j.state != JellyState.SQUISHED && !j.frozen) {
                            j.copy(falling = true, state = JellyState.DESCENDING, stateMs = 0L, cracked = false)
                        } else {
                            j
                        }
                    }.toMutableList()
                    events += GooEvent.Shock
                }
            }
        }

        // ---- jelly simulation -------------------------------------------
        val survivors = mutableListOf<Jelly>()
        for (jelly in jellies) {
            when {
                jelly.state == JellyState.SQUISHED -> {
                    val age = jelly.squishMs + dtMs
                    if (age < GOO_SQUISH_FADE_MS) survivors += jelly.copy(squishMs = age)
                }
                jelly.state == JellyState.SHADOW -> {
                    val age = jelly.stateMs + dtMs
                    if (age >= GOO_SHADOW_MS) {
                        survivors += jelly.copy(state = JellyState.DESCENDING, stateMs = 0L, vy = jelly.speed)
                        events += GooEvent.Materialize
                    } else {
                        survivors += jelly.copy(stateMs = age)
                    }
                }
                jelly.frozen -> {
                    val thaw = jelly.thawMs + dtMs
                    if (thaw >= GOO_FREEZE_MS) {
                        survivors += jelly.copy(frozen = false, cracked = false, thawMs = 0L)
                    } else {
                        survivors += jelly.copy(thawMs = thaw)
                    }
                }
                jelly.falling -> {
                    val vy = jelly.vy + GOO_FALL_GRAVITY * dt
                    val y = jelly.y + vy * dt
                    if (y - GOO_JELLY_RADIUS <= GOO_VIEW_H) survivors += jelly.copy(vy = vy, y = y)
                }
                jelly.state == JellyState.DESCENDING -> {
                    val y = jelly.y + jelly.vy * dt
                    if (y + GOO_JELLY_RADIUS >= state.sludgeLevel) {
                        survivors += jelly.copy(y = state.sludgeLevel - GOO_JELLY_RADIUS, state = JellyState.HIT_WALL, stateMs = 0L)
                        events += GooEvent.HitWall
                    } else {
                        survivors += jelly.copy(y = y)
                    }
                }
                jelly.state == JellyState.HIT_WALL -> {
                    val age = jelly.stateMs + dtMs
                    if (age >= GOO_HIT_WALL_MS) {
                        survivors += jelly.copy(state = JellyState.STALL, stateMs = 0L)
                    } else {
                        survivors += jelly.copy(stateMs = age)
                    }
                }
                jelly.state == JellyState.STALL -> {
                    val age = jelly.stateMs + dtMs
                    val stallMs = if (jelly.stallOverrideMs > 0L) {
                        jelly.stallOverrideMs
                    } else {
                        (stallSeconds(state.level) * 1000f).toLong()
                    }
                    if (age >= stallMs) {
                        val vx = if (jelly.vx != 0f) jelly.vx else if (jelly.id % 2 == 0) 20f else -20f
                        survivors += jelly.copy(state = JellyState.SLIDE, stateMs = 0L, vx = vx)
                    } else {
                        survivors += jelly.copy(stateMs = age)
                    }
                }
                jelly.state == JellyState.SLIDE -> {
                    var x = jelly.x + jelly.vx * dt
                    var vx = jelly.vx
                    if (x < GOO_JELLY_RADIUS) {
                        x = GOO_JELLY_RADIUS
                        vx = -vx
                    } else if (x > GOO_VIEW_W - GOO_JELLY_RADIUS) {
                        x = GOO_VIEW_W - GOO_JELLY_RADIUS
                        vx = -vx
                    }
                    survivors += jelly.copy(x = x, vx = vx)
                }
            }
        }

        jellies = survivors

        // Absorb settled jellies the rising sludge has reached.
        jellies = jellies.filter { jelly ->
            val submerged = (jelly.onFloor() || jelly.frozen) &&
                jelly.state != JellyState.SQUISHED &&
                jelly.y + GOO_JELLY_RADIUS > state.sludgeLevel + 0.5f
            if (submerged && !jelly.kind.powerUp) sludgeTarget = absorbTarget(sludgeTarget)
            !submerged
        }.toMutableList()

        // ---- small-green merge economy ----------------------------------
        run {
            val candidates = jellies
                .filter {
                    it.kind == JellyKind.SMALL_GREEN && !it.frozen && !it.falling &&
                        (it.state == JellyState.SLIDE || it.state == JellyState.STALL)
                }
                .sortedBy { it.id }
            val order = jellies.map { it.id }
            val byId = jellies.associateBy { it.id }.toMutableMap()
            val mergedIds = mutableSetOf<Int>()
            for (a in candidates) {
                if (a.id in mergedIds) continue
                for (b in candidates) {
                    if (b.id <= a.id || b.id in mergedIds) continue
                    val ja = byId[a.id] ?: break
                    val jb = byId[b.id] ?: continue
                    val d = distance(ja, jb)
                    if (d <= 0f || d > GOO_JELLY_RADIUS * 2f) continue
                    if (d <= GOO_JELLY_RADIUS) {
                        val merged = Jelly(
                            id = nextId++,
                            kind = JellyKind.LARGE_GREEN,
                            x = (ja.x + jb.x) / 2f,
                            y = (ja.y + jb.y) / 2f,
                            vy = 0f,
                            vx = (ja.vx + jb.vx) / 2f,
                            speed = (ja.speed + jb.speed) / 2f * 1.5f,
                            state = JellyState.STALL,
                            stateMs = 0L,
                            stallOverrideMs = (GOO_MERGE_STALL_S * 1000f).toLong(),
                        )
                        byId[a.id] = merged
                        byId.remove(b.id)
                        mergedIds += a.id
                        mergedIds += b.id
                        events += GooEvent.Merge
                        break
                    } else {
                        val pull = GOO_MERGE_SPEED / 2f * dt
                        val nx = (jb.x - ja.x) / d
                        val ny = (jb.y - ja.y) / d
                        byId[a.id] = ja.copy(x = ja.x + nx * pull, y = ja.y + ny * pull)
                        byId[b.id] = jb.copy(x = jb.x - nx * pull, y = jb.y - ny * pull)
                    }
                }
            }
            jellies = order.mapNotNull { byId[it] }.toMutableList()
        }

        // ---- sludge -----------------------------------------------------
        var sludgeLevel = moveToward(state.sludgeLevel, sludgeTarget, GOO_SLUDGE_SPEED * dt)
        var over = sludgeLevel < GOO_FAIL_LEVEL
        if (over) events += GooEvent.GameOver

        // ---- spawns -----------------------------------------------------
        var clock = state.spawnClockMs + dtMs
        if (!over) {
            val tick = spawnTickMs(state.level)
            while (clock >= tick) {
                clock -= tick
                val batch = 1 + rng.next(batchMax(state.level))
                repeat(batch) {
                    val position = rollSpawnPosition(jellies, sludgeLevel, rng) ?: return@repeat
                    val kind = rollKind(rng.next(100))
                    val roll = rng.next(1000) / 1000f
                    val speed = descentSpeed(state.level, roll)
                    jellies += Jelly(
                        id = nextId++,
                        kind = kind,
                        x = position.first,
                        y = position.second,
                        speed = speed,
                        vy = speed,
                    )
                }
            }
        }

        // Pepper: 1-in-500 per frame while the sludge is below 280.
        if (pepper == null && !over && sludgeLevel < GOO_PEPPER_MIN_SLUDGE && rng.next(GOO_PEPPER_ODDS) == 0) {
            val side = rng.next(2)
            val speed = state.level + 70f
            pepper = if (side == 0) {
                Pepper(x = -GOO_JELLY_RADIUS, y = 80f + rng.next(200), vx = speed)
            } else {
                Pepper(x = GOO_VIEW_W + GOO_JELLY_RADIUS, y = 80f + rng.next(200), vx = -speed)
            }
        }
        pepper = pepper?.let {
            val x = it.x + it.vx * dt
            if (x < -60f || x > GOO_VIEW_W + 60f) null else it.copy(x = x, y = it.y + it.vy * dt)
        }

        // ---- level up ---------------------------------------------------
        var level = state.level
        var scoreTarget = state.scoreTarget
        var targetStep = state.targetStep
        while (score >= scoreTarget) {
            level++
            targetStep += 2 * level
            scoreTarget += targetStep
            sludgeTarget = min(GOO_SLUDGE_CAP, sludgeTarget + GOO_SLUDGE_LEVEL_STEP)
            events += GooEvent.LevelUp
        }
        sludgeLevel = min(sludgeLevel, GOO_SLUDGE_CAP)

        return state.copy(
            level = level,
            score = score,
            scoreTarget = scoreTarget,
            targetStep = targetStep,
            sludgeLevel = sludgeLevel,
            sludgeTarget = sludgeTarget,
            spawnClockMs = clock,
            elapsedMs = state.elapsedMs + dtMs,
            jellies = jellies,
            pepper = pepper,
            nextId = nextId,
            seed = rng.state,
            breakdown = breakdown,
            over = over,
            events = events,
        )
    }

    fun encode(state: GooSplatState): String {
        val jellies = state.jellies.joinToString("/") { j ->
            listOf(
                j.id,
                j.kind.ordinal,
                j.state.ordinal,
                j.x,
                j.y,
                j.vy,
                j.vx,
                j.speed,
                j.stateMs,
                if (j.falling) 1 else 0,
                if (j.frozen) 1 else 0,
                if (j.cracked) 1 else 0,
                j.thawMs,
                j.squishMs,
                j.stallOverrideMs,
            ).joinToString(",")
        }
        val pepper = state.pepper?.let { listOf(it.x, it.y, it.vx, it.vy).joinToString(",") } ?: ""
        val b = state.breakdown
        return listOf(
            "gs1",
            state.level,
            state.score,
            state.scoreTarget,
            state.targetStep,
            state.sludgeLevel,
            state.sludgeTarget,
            state.spawnClockMs,
            state.elapsedMs,
            state.nextId,
            state.seed,
            if (state.over) 1 else 0,
            if (state.paused) 1 else 0,
            "${b.green},${b.red},${b.blue},${b.yellow},${b.peppers}",
            jellies,
            pepper,
        ).joinToString(";")
    }

    fun decode(blob: String): GooSplatState? = try {
        val parts = blob.split(";")
        if (parts.size < 16 || parts[0] != "gs1") {
            null
        } else {
            val jellies = if (parts[14].isEmpty()) {
                emptyList()
            } else {
                parts[14].split("/").map { raw ->
                    val f = raw.split(",")
                    Jelly(
                        id = f[0].toInt(),
                        kind = JellyKind.entries[f[1].toInt()],
                        state = JellyState.entries[f[2].toInt()],
                        x = f[3].toFloat(),
                        y = f[4].toFloat(),
                        vy = f[5].toFloat(),
                        vx = f[6].toFloat(),
                        speed = f[7].toFloat(),
                        stateMs = f[8].toLong(),
                        falling = f[9] == "1",
                        frozen = f[10] == "1",
                        cracked = f[11] == "1",
                        thawMs = f[12].toLong(),
                        squishMs = f[13].toLong(),
                        stallOverrideMs = f.getOrNull(14)?.toLongOrNull() ?: 0L,
                    )
                }
            }
            val pepper = if (parts[15].isEmpty()) {
                null
            } else {
                val f = parts[15].split(",")
                Pepper(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat())
            }
            val b = parts[13].split(",")
            GooSplatState(
                level = parts[1].toInt(),
                score = parts[2].toInt(),
                scoreTarget = parts[3].toInt(),
                targetStep = parts[4].toInt(),
                sludgeLevel = parts[5].toFloat(),
                sludgeTarget = parts[6].toFloat(),
                spawnClockMs = parts[7].toLong(),
                elapsedMs = parts[8].toLong(),
                nextId = parts[9].toInt(),
                seed = parts[10].toInt(),
                over = parts[11] == "1",
                paused = parts[12] == "1",
                breakdown = GooBreakdown(
                    green = b.getOrNull(0)?.toIntOrNull() ?: 0,
                    red = b.getOrNull(1)?.toIntOrNull() ?: 0,
                    blue = b.getOrNull(2)?.toIntOrNull() ?: 0,
                    yellow = b.getOrNull(3)?.toIntOrNull() ?: 0,
                    peppers = b.getOrNull(4)?.toIntOrNull() ?: 0,
                ),
                jellies = jellies,
                pepper = pepper,
            )
        }
    } catch (_: Exception) {
        null
    }
}
