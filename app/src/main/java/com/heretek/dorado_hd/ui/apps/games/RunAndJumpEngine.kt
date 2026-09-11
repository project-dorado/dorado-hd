package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Clean-room re-derivation of Run and Jump's rigid-body platformer. Behaviour
 * comes from docs/apps/run-and-jump.md; every level layout is original
 * procedural content. Physics keeps the mined constants (gravity 24 m/s² at
 * 32 px/m, 1/60 fixed step scaled by SlowMotion, terminal 16 m/s) but uses a
 * small AABB solver instead of Box2D.
 */
enum class RnJTier { TUTORIAL, EASY, MEDIUM, HARD, EXTREME }

enum class RnJKind { SOLID, WOOD, GOAL, BOOST, SWITCH, WARP }

enum class RnJBoost {
    NONE, JUMP, DUCK, ANGLED_LEFT, ANGLED_RIGHT, MOVE_LEFT, MOVE_RIGHT, GRAVITY,
    GOAL, NULL, SLOW_LEFT, SLOW_RIGHT, FAST_LEFT, FAST_RIGHT,
    SWITCH_PINK_GREEN, SWITCH_PURPLE_ORANGE,
}

enum class RnJEvent {
    JUMP, DUCK, MOVE_SLOW, MOVE_NORMAL, MOVE_FAST, GRAVITY, WARP, COIN, GOAL,
    WOOD, SWITCH, DEAD, NULL,
}

data class RnJPoint(val x: Double, val y: Double)

data class RnJTile(
    val col: Int,
    val row: Int,
    val kind: RnJKind,
    val id: Int = -1,
    val variant: Int = 0,
    val group: Int = 0,
    val siblingCol: Int = -1,
    val siblingRow: Int = -1,
)

data class RnJLevel(
    val id: Int,
    val name: String,
    val tier: RnJTier,
    val cols: Int,
    val rows: Int,
    val spawnX: Double,
    val spawnY: Double,
    val coinsToUnlock: Int,
    val hint: String,
    val tiles: List<RnJTile>,
    val coins: List<RnJPoint>,
) {
    val widthPx: Double get() = cols * RnJEngine.TILE_PX
    val heightPx: Double get() = rows * RnJEngine.TILE_PX

    fun tilesAt(col: Int, row: Int): List<RnJTile> =
        tiles.filter { it.col == col && it.row == row }

    fun tileAt(col: Int, row: Int): RnJTile? = tiles.lastOrNull { it.col == col && it.row == row }
}

data class RnJBody(
    val x: Double,
    val y: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val onGround: Boolean = false,
)

data class RnJState(
    val levelId: Int,
    val body: RnJBody,
    val gravitySign: Int = 1,
    val duckRemainingMs: Long = 0L,
    val unduckRemainingMs: Long = 0L,
    val boostCooldownMs: Long = 0L,
    val currentBoostId: Int = -1,
    val switchA: Boolean = false,
    val switchB: Boolean = false,
    val collected: Set<Int> = emptySet(),
    val totalCoins: Int = 0,
    val destroyedWood: Set<Int> = emptySet(),
    val woodTimers: Map<Int, Long> = emptyMap(),
    val deaths: Int = 0,
    val hasTapped: Boolean = false,
    val deadMs: Long = 0L,
    val clockMs: Long = 0L,
    val spawnX: Double,
    val spawnY: Double,
    val finished: Boolean = false,
    val missedCoins: Boolean = false,
    val slowMotion: Double = RnJEngine.SLOW_MOTION_NORMAL,
    val accumulatorMs: Double = 0.0,
    val cameraX: Double = 0.0,
    val events: List<RnJEvent> = emptyList(),
)

data class RnJStat(
    val coins: Int = 0,
    val deaths: Int = 0,
    val beatFast: Boolean = false,
    val allCoinsFast: Boolean = false,
    val completed: Boolean = false,
)

data class RnJProgress(
    val stats: Map<Int, RnJStat> = emptyMap(),
    val lastLevel: Int = 1,
    val sfxLevel: Int = 2,
    val fast: Boolean = false,
)

/** Original procedural level set: 6 tutorial, 14 easy, 20 medium, 15 hard, 3 extreme. */
object RnJLevels {
    val all: List<RnJLevel> by lazy { build() }

    fun byId(id: Int): RnJLevel = all.firstOrNull { it.id == id } ?: all.first()

    private fun build(): List<RnJLevel> {
        val levels = mutableListOf<RnJLevel>()
        var id = 1
        for (i in 1..6) {
            levels += build(id, RnJTier.TUTORIAL, 30 + i)
            id++
        }
        for (i in 1..14) {
            levels += build(id, RnJTier.EASY, 38 + (i % 4) * 2)
            id++
        }
        for (i in 1..20) {
            levels += build(id, RnJTier.MEDIUM, 44 + (i % 5) * 2)
            id++
        }
        for (i in 1..15) {
            levels += build(id, RnJTier.HARD, 50 + (i % 5) * 2)
            id++
        }
        for (i in 1..3) {
            levels += build(id, RnJTier.EXTREME, 60 + i * 2)
            id++
        }
        return levels
    }

    private fun build(id: Int, tier: RnJTier, cols: Int): RnJLevel {
        val rng = Random(id * 2654435761L.toInt() + 17)
        val rows = 9
        val floorRow = 7
        val tiles = mutableListOf<RnJTile>()
        val coins = mutableListOf<RnJPoint>()

        fun solid(col: Int, row: Int) {
            if (col in 0 until cols && row in 0 until rows) tiles += RnJTile(col, row, RnJKind.SOLID)
        }

        fun pad(col: Int, row: Int, boostId: Int) {
            if (col in 0 until cols && row in 0 until rows) tiles += RnJTile(col, row, RnJKind.BOOST, id = boostId)
        }

        fun coin(col: Int, row: Int) {
            if (col in 0 until cols && row in 0 until rows) {
                coins += RnJPoint(col * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2, row * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2)
            }
        }

        for (col in 0 until cols) {
            val gap = tier != RnJTier.TUTORIAL && col > 5 && col < cols - 5 && col % 10 == 5
            if (gap) continue
            solid(col, floorRow)
            solid(col, floorRow + 1)
            val boostId = when (tier) {
                RnJTier.TUTORIAL -> 0
                else -> when (col % 3) {
                    0 -> 0
                    1 -> if (col % 2 == 0) 2 else 3
                    else -> if (rng.nextBoolean()) 4 else 5
                }
            }
            pad(col, floorRow - 1, boostId)
        }
        pad(1, floorRow - 1, 0)
        pad(2, floorRow - 1, 0)

        for (col in 6 until cols - 4 step 4) {
            coin(col, floorRow - 3)
            if (col % 8 == 2) coin(col, floorRow - 4)
        }

        if (tier != RnJTier.TUTORIAL) {
            var col = 12
            while (col < cols - 8) {
                if (rng.nextBoolean()) {
                    for (k in 0 until 4) solid(col + k, floorRow - 3)
                    pad(col - 1, floorRow - 1, 1)
                    coin(col + 1, floorRow - 1)
                }
                col += 14 + rng.nextInt(6)
            }
        }

        if (tier == RnJTier.MEDIUM || tier == RnJTier.HARD || tier == RnJTier.EXTREME) {
            val switchCol = cols / 2
            for (k in 0 until 3) {
                tiles += RnJTile(switchCol, floorRow - 1 - k, RnJKind.SWITCH, variant = 0, group = 0)
                tiles += RnJTile(switchCol + 1, floorRow - 1 - k, RnJKind.SWITCH, variant = 1, group = 0)
            }
            pad(switchCol - 1, floorRow - 1, 13)
            coins += RnJPoint(switchCol * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2, (floorRow - 3) * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2)
        }

        if (tier == RnJTier.HARD || tier == RnJTier.EXTREME) {
            val woodCol = cols - 12
            for (k in 0 until 2) {
                tiles += RnJTile(woodCol, floorRow - 1 - k, RnJKind.WOOD)
                tiles += RnJTile(woodCol + 1, floorRow - 1 - k, RnJKind.WOOD)
            }
            pad(woodCol - 2, floorRow - 1, 5)
            val warpA = cols / 3
            val warpB = cols - cols / 4
            tiles += RnJTile(warpA, floorRow - 2, RnJKind.WARP, id = 64, siblingCol = warpB, siblingRow = floorRow - 2)
            tiles += RnJTile(warpB, floorRow - 2, RnJKind.WARP, id = 65, siblingCol = warpA, siblingRow = floorRow - 2)
            coins += RnJPoint(warpB * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2, (floorRow - 4) * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2)
        }

        if (tier == RnJTier.EXTREME) {
            val gravityCol = 2 * cols / 3
            for (k in 0 until 6) solid(gravityCol + k, 0)
            pad(gravityCol + 1, 1, 16)
            pad(gravityCol + 4, 1, 16)
            pad(gravityCol - 1, floorRow - 1, 6)
            coin(gravityCol + 2, 4)
        }

        tiles += RnJTile(cols - 2, floorRow - 1, RnJKind.GOAL)
        val hint = if (tier == RnJTier.TUTORIAL) {
            "tap the screen to use the pad under your feet"
        } else {
            "keep a pad under your feet - plain ground is fatal"
        }
        val coinsToUnlock = if (id % 10 == 0) 20 else 0
        return RnJLevel(
            id = id,
            name = "level $id",
            tier = tier,
            cols = cols,
            rows = rows,
            spawnX = 1 * RnJEngine.TILE_PX + RnJEngine.TILE_PX / 2,
            spawnY = floorRow * RnJEngine.TILE_PX - RnJEngine.PLAYER_H / 2,
            coinsToUnlock = coinsToUnlock,
            hint = hint,
            tiles = tiles,
            coins = coins,
        )
    }
}

object RnJEngine {
    const val GRAVITY = 24.0
    const val PX_PER_M = 32.0
    const val FIXED_HZ = 60.0
    const val TERMINAL_MPS = 16.0
    const val TILE_PX = 32.0
    const val BOOST_FORCE = 775.0
    const val BOOST_VELOCITY_SCALE = 0.5
    const val ANGLED_MULTIPLIER = 1.35
    const val ANGLED_DEGREES = 60.0
    const val SLOW_MULTIPLIER = 0.5
    const val FAST_MULTIPLIER = 2.0
    const val BOOST_COOLDOWN_MS = 250L
    const val DUCK_HOLD_MS = 850L
    const val UNDUCK_MS = 50L
    const val RESPAWN_MS = 500L
    const val BOUNDS_INFLATE = 56.0
    const val PLAYER_W = 22.0
    const val PLAYER_H = 34.0
    const val PLAYER_DUCK_H = 20.0
    const val WOOD_BREAK_MS = 1000L
    const val SLOW_MOTION_NORMAL = 0.85
    const val SLOW_MOTION_FAST = 1.0
    const val VIEW_W = 480.0
    const val COIN_RADIUS = 9.0
    val COIN_MILESTONES = listOf(31, 85, 135, 220)

    fun newGame(level: RnJLevel, slowMotion: Double = SLOW_MOTION_NORMAL): RnJState = RnJState(
        levelId = level.id,
        body = RnJBody(level.spawnX, level.spawnY),
        totalCoins = level.coins.size,
        spawnX = level.spawnX,
        spawnY = level.spawnY,
        slowMotion = slowMotion,
    )

    fun isDucking(state: RnJState): Boolean = state.duckRemainingMs > 0L || state.unduckRemainingMs > 0L

    fun terminalVelocity(slowMotion: Double): Double = TERMINAL_MPS * PX_PER_M * slowMotion

    fun rootVelocity(): Double = BOOST_FORCE * BOOST_VELOCITY_SCALE

    fun baseBoostId(padId: Int): Int = when {
        padId < 0 -> -1
        padId < 16 -> padId
        padId < 32 -> padId - 16
        padId < 64 -> padId - 32
        else -> padId - 64
    }

    fun boostOf(padId: Int): RnJBoost = when (baseBoostId(padId)) {
        0 -> RnJBoost.JUMP
        1 -> RnJBoost.DUCK
        2 -> RnJBoost.ANGLED_LEFT
        3 -> RnJBoost.ANGLED_RIGHT
        4 -> RnJBoost.MOVE_LEFT
        5 -> RnJBoost.MOVE_RIGHT
        6 -> RnJBoost.GRAVITY
        7 -> RnJBoost.GOAL
        8 -> RnJBoost.NULL
        9 -> RnJBoost.SLOW_LEFT
        10 -> RnJBoost.SLOW_RIGHT
        11 -> RnJBoost.FAST_LEFT
        12 -> RnJBoost.FAST_RIGHT
        13 -> RnJBoost.SWITCH_PINK_GREEN
        14 -> RnJBoost.SWITCH_PURPLE_ORANGE
        else -> RnJBoost.NONE
    }

    fun warpEntityId(padId: Int): Int = if (padId >= 64) padId - 64 else -1

    fun warpTarget(level: RnJLevel, padId: Int): RnJPoint? {
        val pad = level.tiles.firstOrNull { it.kind == RnJKind.WARP && it.id == padId } ?: return null
        if (pad.siblingCol < 0 || pad.siblingRow < 0) return null
        return RnJPoint(pad.siblingCol * TILE_PX + TILE_PX / 2, pad.siblingRow * TILE_PX + TILE_PX / 2)
    }

    fun isSolid(tile: RnJTile, state: RnJState, level: RnJLevel): Boolean = when (tile.kind) {
        RnJKind.SOLID -> true
        RnJKind.WOOD -> !state.destroyedWood.contains(tile.row * level.cols + tile.col)
        RnJKind.SWITCH -> if (tile.group == 0) {
            state.switchA == (tile.variant == 1)
        } else {
            state.switchB == (tile.variant == 1)
        }

        else -> false
    }

    /** Death rule: standing on a pad that is missing, null or a plain switch is fatal. */
    fun isSafeStanding(padId: Int): Boolean {
        if (padId < 0) return false
        val base = baseBoostId(padId)
        return base != 8 && base != 13
    }

    fun step(state: RnJState, level: RnJLevel, dtMs: Long): RnJState {
        if (state.finished || dtMs <= 0L) return state
        if (state.deadMs > 0L) {
            val remaining = state.deadMs - dtMs
            return if (remaining > 0L) state.copy(deadMs = remaining) else respawn(state)
        }

        var work = state.copy(boostCooldownMs = (state.boostCooldownMs - dtMs).coerceAtLeast(0L), events = emptyList())
        if (work.duckRemainingMs > 0L) {
            val left = work.duckRemainingMs - dtMs
            work = if (left > 0L) {
                work.copy(duckRemainingMs = left)
            } else {
                work.copy(duckRemainingMs = 0L, unduckRemainingMs = UNDUCK_MS)
            }
        } else if (work.unduckRemainingMs > 0L) {
            work = work.copy(unduckRemainingMs = (work.unduckRemainingMs - dtMs).coerceAtLeast(0L))
        }

        if (work.woodTimers.isNotEmpty()) {
            val timers = HashMap<Int, Long>()
            var broke = false
            val destroyed = work.destroyedWood.toMutableSet()
            for ((index, remaining) in work.woodTimers) {
                val left = remaining - dtMs
                if (left <= 0L) {
                    destroyed += index
                    broke = true
                } else {
                    timers[index] = left
                }
            }
            work = work.copy(
                woodTimers = timers,
                destroyedWood = destroyed,
                events = work.events + if (broke) listOf(RnJEvent.WOOD) else emptyList(),
            )
        }

        val stepMs = (1000.0 / FIXED_HZ) * work.slowMotion
        var accumulator = work.accumulatorMs + dtMs
        var substeps = 0
        while (accumulator >= stepMs && substeps < 64) {
            accumulator -= stepMs
            substeps++
            work = substep(work, level, stepMs / 1000.0)
            if (work.deadMs > 0L || work.finished) break
        }
        return work.copy(accumulatorMs = accumulator, clockMs = state.clockMs + dtMs)
    }

    fun tap(state: RnJState, level: RnJLevel): RnJState {
        if (state.finished || state.deadMs > 0L) return state
        val tapped = state.copy(hasTapped = true, events = emptyList())
        if (state.boostCooldownMs > 0L) return tapped
        val padId = state.currentBoostId
        var next = tapped.copy(boostCooldownMs = BOOST_COOLDOWN_MS)
        if (padId < 0) return next
        if (padId >= 64) return activateWarp(next, level, padId)
        val padGravity = if (padId in 16..31) -1 else 1
        val root = rootVelocity()
        val angled = root * ANGLED_MULTIPLIER * cos(ANGLED_DEGREES * PI / 180.0)
        val angledRise = root * ANGLED_MULTIPLIER * sin(ANGLED_DEGREES * PI / 180.0)
        next = when (baseBoostId(padId)) {
            0 -> next.copy(body = next.body.copy(vy = -root * padGravity), events = listOf(RnJEvent.JUMP))
            1 -> next.copy(duckRemainingMs = DUCK_HOLD_MS, unduckRemainingMs = 0L, events = listOf(RnJEvent.DUCK))
            2 -> next.copy(body = next.body.copy(vx = next.body.vx - angled, vy = -angledRise * padGravity), events = listOf(RnJEvent.MOVE_NORMAL))
            3 -> next.copy(body = next.body.copy(vx = next.body.vx + angled, vy = -angledRise * padGravity), events = listOf(RnJEvent.MOVE_NORMAL))
            4 -> next.copy(body = next.body.copy(vx = next.body.vx - root), events = listOf(RnJEvent.MOVE_NORMAL))
            5 -> next.copy(body = next.body.copy(vx = next.body.vx + root), events = listOf(RnJEvent.MOVE_NORMAL))
            6 -> next.copy(gravitySign = -next.gravitySign, events = listOf(RnJEvent.GRAVITY))
            7 -> next.copy(
                finished = true,
                missedCoins = next.collected.size < level.coins.size,
                events = listOf(RnJEvent.GOAL),
            )

            8 -> next.copy(events = listOf(RnJEvent.NULL))
            9 -> next.copy(body = next.body.copy(vx = next.body.vx - root * SLOW_MULTIPLIER), events = listOf(RnJEvent.MOVE_SLOW))
            10 -> next.copy(body = next.body.copy(vx = next.body.vx + root * SLOW_MULTIPLIER), events = listOf(RnJEvent.MOVE_SLOW))
            11 -> next.copy(body = next.body.copy(vx = next.body.vx - root * FAST_MULTIPLIER), events = listOf(RnJEvent.MOVE_FAST))
            12 -> next.copy(body = next.body.copy(vx = next.body.vx + root * FAST_MULTIPLIER), events = listOf(RnJEvent.MOVE_FAST))
            13 -> next.copy(switchA = !next.switchA, events = listOf(RnJEvent.SWITCH))
            14 -> next.copy(switchB = !next.switchB, events = listOf(RnJEvent.SWITCH))
            else -> next.copy(events = listOf(RnJEvent.NULL))
        }
        return next
    }

    fun setSpeed(state: RnJState, fast: Boolean): RnJState =
        state.copy(slowMotion = if (fast) SLOW_MOTION_FAST else SLOW_MOTION_NORMAL)

    fun totalCoins(progress: RnJProgress): Int = progress.stats.values.sumOf { it.coins }

    fun unlockedCount(totalCoins: Int): Int = when {
        totalCoins >= COIN_MILESTONES[3] -> 58
        totalCoins >= COIN_MILESTONES[2] -> 55
        totalCoins >= COIN_MILESTONES[1] -> 40
        totalCoins >= COIN_MILESTONES[0] -> 20
        else -> 6
    }

    fun isLevelUnlocked(progress: RnJProgress, level: RnJLevel): Boolean {
        val total = totalCoins(progress)
        if (level.id <= unlockedCount(total)) return true
        return level.coinsToUnlock > 0 && total >= level.coinsToUnlock
    }

    fun finishLevel(progress: RnJProgress, level: RnJLevel, state: RnJState, fast: Boolean): RnJProgress {
        val previous = progress.stats[level.id] ?: RnJStat()
        val stat = previous.copy(
            coins = maxOf(previous.coins, state.collected.size),
            deaths = previous.deaths + state.deaths,
            beatFast = previous.beatFast || (state.finished && fast),
            allCoinsFast = previous.allCoinsFast || (state.finished && fast && !state.missedCoins),
            completed = previous.completed || state.finished,
        )
        return progress.copy(stats = progress.stats + (level.id to stat), lastLevel = level.id)
    }

    fun encodeProgress(progress: RnJProgress): String {
        val stats = progress.stats.entries.sortedBy { it.key }.joinToString(",") { (id, stat) ->
            val flags = (if (stat.beatFast) 1 else 0) or (if (stat.allCoinsFast) 2 else 0) or (if (stat.completed) 4 else 0)
            "$id:${stat.coins}:${stat.deaths}:$flags"
        }
        return listOf(
            "rj1",
            stats,
            progress.lastLevel.toString(),
            progress.sfxLevel.toString(),
            if (progress.fast) "1" else "0",
        ).joinToString(";")
    }

    fun decodeProgress(blob: String): RnJProgress? = try {
        val parts = blob.split(";")
        if (parts.size < 5 || parts[0] != "rj1") return null
        val stats = parts[1].split(",").mapNotNull { entry ->
            if (entry.isBlank()) return@mapNotNull null
            val fields = entry.split(":")
            if (fields.size < 4) return@mapNotNull null
            val id = fields[0].toIntOrNull() ?: return@mapNotNull null
            val flags = fields[3].toIntOrNull() ?: 0
            id to RnJStat(
                coins = fields[1].toIntOrNull() ?: 0,
                deaths = fields[2].toIntOrNull() ?: 0,
                beatFast = flags and 1 != 0,
                allCoinsFast = flags and 2 != 0,
                completed = flags and 4 != 0,
            )
        }.toMap()
        RnJProgress(
            stats = stats,
            lastLevel = parts[2].toIntOrNull() ?: 1,
            sfxLevel = parts[3].toIntOrNull()?.coerceIn(0, 3) ?: 2,
            fast = parts[4] == "1",
        )
    } catch (_: Exception) {
        null
    }

    private fun substep(state: RnJState, level: RnJLevel, dt: Double): RnJState {
        val ducking = isDucking(state)
        val halfW = PLAYER_W / 2
        val halfH = (if (ducking) PLAYER_DUCK_H else PLAYER_H) / 2
        val terminal = terminalVelocity(state.slowMotion)

        val body = state.body
        var vx = body.vx
        var vy = (body.vy + state.gravitySign * GRAVITY * PX_PER_M * dt).coerceIn(-terminal, terminal)
        var x = body.x + vx * dt
        var y = body.y + vy * dt
        var grounded = false
        var died = false
        val events = mutableListOf<RnJEvent>()
        val woodTimers = state.woodTimers.toMutableMap()
        val destroyed = state.destroyedWood.toMutableSet()

        val prevLeft = body.x - halfW
        val prevRight = body.x + halfW
        val prevTop = body.y - halfH
        val prevBottom = body.y + halfH
        val epsilon = 0.01

        val c0 = floor((x - halfW) / TILE_PX).toInt().coerceIn(0, level.cols - 1)
        val c1 = floor((x + halfW - 0.001) / TILE_PX).toInt().coerceIn(0, level.cols - 1)
        val r0 = floor((y - halfH) / TILE_PX).toInt().coerceIn(0, level.rows - 1)
        val r1 = floor((y + halfH - 0.001) / TILE_PX).toInt().coerceIn(0, level.rows - 1)
        outer@ for (row in r0..r1) {
            for (col in c0..c1) {
                for (tile in level.tilesAt(col, row)) {
                    if (!isSolid(tile, state, level)) continue
                    val tileX = col * TILE_PX
                    val tileY = row * TILE_PX
                    val overlapX = min(x + halfW, tileX + TILE_PX) - maxOf(x - halfW, tileX)
                    val overlapY = min(y + halfH, tileY + TILE_PX) - maxOf(y - halfH, tileY)
                    if (overlapX <= 0.0 || overlapY <= 0.0) continue

                    // Classify by which face the player crossed this substep so
                    // walking across floor seams never reads as a side hit.
                    val wasAbove = prevBottom <= tileY + epsilon
                    val wasBelow = prevTop >= tileY + TILE_PX - epsilon
                    val wasLeft = prevRight <= tileX + epsilon
                    val wasRight = prevLeft >= tileX + TILE_PX - epsilon
                    val vertical = wasAbove || wasBelow || (!wasLeft && !wasRight && overlapY <= overlapX)
                    val landing = when {
                        wasAbove -> state.gravitySign == 1
                        wasBelow -> state.gravitySign == -1
                        !wasLeft && !wasRight -> {
                            val tileCentreY = tileY + TILE_PX / 2
                            if (state.gravitySign == 1) y < tileCentreY else y > tileCentreY
                        }

                        else -> false
                    }
                    if (vertical && landing) {
                        y = if (state.gravitySign == 1) tileY - halfH else tileY + TILE_PX + halfH
                        if (state.gravitySign == 1 && vy > 0) vy = 0.0
                        if (state.gravitySign == -1 && vy < 0) vy = 0.0
                        grounded = true
                        if (tile.kind == RnJKind.WOOD) {
                            val index = row * level.cols + col
                            if (!destroyed.contains(index)) {
                                woodTimers[index] = WOOD_BREAK_MS
                                events += RnJEvent.WOOD
                            }
                        }
                    } else if (vertical) {
                        died = true
                        break@outer
                    } else if (tile.kind == RnJKind.WOOD) {
                        val index = row * level.cols + col
                        if (!destroyed.contains(index)) {
                            woodTimers[index] = WOOD_BREAK_MS
                            events += RnJEvent.WOOD
                        }
                    } else {
                        died = true
                        break@outer
                    }
                }
            }
        }

        if (!died && (x < -BOUNDS_INFLATE || x > level.widthPx + BOUNDS_INFLATE ||
                y < -BOUNDS_INFLATE || y > level.heightPx + BOUNDS_INFLATE)
        ) {
            died = true
        }

        var next = state.copy(
            body = RnJBody(x, y, vx, vy, grounded),
            destroyedWood = destroyed,
            woodTimers = woodTimers,
            events = state.events + events,
        )
        val padId = currentBoostAt(next, level)
        next = next.copy(currentBoostId = padId)
        if (!died && grounded && !isSafeStanding(padId)) died = true

        val coinIndex = level.coins.withIndex().firstOrNull { (index, coin) ->
            !next.collected.contains(index) && coinOverlap(next.body, halfW, halfH, coin)
        }?.index ?: -1
        if (coinIndex >= 0) {
            next = next.copy(collected = next.collected + coinIndex, events = next.events + RnJEvent.COIN)
        }

        val goal = level.tiles.any { tile ->
            tile.kind == RnJKind.GOAL && bodyOverlaps(next.body, halfW, halfH, tile.col * TILE_PX, tile.row * TILE_PX)
        }
        if (goal) {
            next = next.copy(
                finished = true,
                missedCoins = next.collected.size < level.coins.size,
                events = next.events + RnJEvent.GOAL,
            )
        }

        next = next.copy(
            cameraX = (next.body.x - VIEW_W / 2).coerceIn(0.0, maxOf(0.0, level.widthPx - VIEW_W)),
        )
        return if (died) die(next) else next
    }

    private fun currentBoostAt(state: RnJState, level: RnJLevel): Int {
        val halfW = PLAYER_W / 2
        val halfH = (if (isDucking(state)) PLAYER_DUCK_H else PLAYER_H) / 2
        var found = -1
        for (tile in level.tiles) {
            if (tile.kind != RnJKind.BOOST && tile.kind != RnJKind.WARP) continue
            if (bodyOverlaps(state.body, halfW, halfH, tile.col * TILE_PX, tile.row * TILE_PX)) {
                found = tile.id
            }
        }
        return found
    }

    private fun bodyOverlaps(body: RnJBody, halfW: Double, halfH: Double, tileX: Double, tileY: Double): Boolean {
        if (body.x + halfW <= tileX || body.x - halfW >= tileX + TILE_PX) return false
        if (body.y + halfH <= tileY || body.y - halfH >= tileY + TILE_PX) return false
        return true
    }

    private fun coinOverlap(body: RnJBody, halfW: Double, halfH: Double, coin: RnJPoint): Boolean {
        val nearestX = body.x.coerceIn(coin.x - COIN_RADIUS, coin.x + COIN_RADIUS)
        val nearestY = body.y.coerceIn(coin.y - COIN_RADIUS, coin.y + COIN_RADIUS)
        return abs(nearestX - coin.x) <= COIN_RADIUS && abs(nearestY - coin.y) <= COIN_RADIUS &&
            abs(body.x - nearestX) <= halfW && abs(body.y - nearestY) <= halfH
    }

    private fun activateWarp(state: RnJState, level: RnJLevel, padId: Int): RnJState {
        val target = warpTarget(level, padId) ?: return state.copy(events = listOf(RnJEvent.NULL))
        val offset = target.x - state.body.x
        return state.copy(
            body = state.body.copy(x = target.x, y = target.y, vx = 0.0, vy = 0.0),
            cameraX = (state.cameraX + offset).coerceIn(0.0, maxOf(0.0, level.widthPx - VIEW_W)),
            events = listOf(RnJEvent.WARP),
        )
    }

    private fun die(state: RnJState): RnJState {
        val deaths = if (state.hasTapped) state.deaths + 1 else state.deaths
        return state.copy(
            deaths = deaths,
            deadMs = RESPAWN_MS,
            body = state.body.copy(vx = 0.0, vy = 0.0),
            events = state.events + RnJEvent.DEAD,
        )
    }

    private fun respawn(state: RnJState): RnJState = state.copy(
        body = RnJBody(state.spawnX, state.spawnY),
        gravitySign = 1,
        duckRemainingMs = 0L,
        unduckRemainingMs = 0L,
        boostCooldownMs = 0L,
        currentBoostId = -1,
        switchA = false,
        switchB = false,
        deadMs = 0L,
        events = state.events + RnJEvent.DEAD,
    )
}
