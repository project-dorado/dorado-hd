package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Clean-room re-derivation of Snowball's tile-fade physics. Behaviour comes
 * from docs/apps/snowball.md; level layouts here are original procedural
 * content, never the shipped XML. Board space is 8x13 blocks of 34 px, with
 * the player as a circle rolling under tilt.
 */
enum class SnowMode { CAMPAIGN, SURVIVAL }

enum class SnowStatus { PLAYING, WON, LOST }

enum class SnowLoseReason { HOLE, WRONG_COIN }

enum class SnowTileKind { NORMAL, SAFE, WALL, ICE }

enum class SnowTilePhase { NORMAL, FADING, KILL, RESPAWNING }

data class SnowTile(
    val kind: SnowTileKind = SnowTileKind.NORMAL,
    val phase: SnowTilePhase = SnowTilePhase.NORMAL,
    val timerMs: Long = 0L,
    val alpha: Float = 1f,
) {
    /** Normal and ice blocks crumble; safe zones and walls never do. */
    val fadeable: Boolean get() = kind == SnowTileKind.NORMAL || kind == SnowTileKind.ICE
}

data class SnowCoin(
    val id: Int,
    val x: Double,
    val y: Double,
    val number: Int = 0,
    val rotationDeg: Double = 0.0,
    val spinDegPerSec: Double = 0.0,
)

data class SnowState(
    val mode: SnowMode = SnowMode.CAMPAIGN,
    val level: Int = 1,
    val seed: Int = 1,
    val tiles: List<SnowTile>,
    val playerX: Double,
    val playerY: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val angleDeg: Double = 0.0,
    val coins: List<SnowCoin> = emptyList(),
    val nextCoin: Int = 0,
    val score: Int = 0,
    val clockMs: Long = 0L,
    val fadeMs: Long = SnowballEngine.FADE_MS,
    val respawnMs: Long = SnowballEngine.RESPAWN_MS,
    val cycleAccumMs: Long = 0L,
    val fadeCycles: Int = 0,
    val status: SnowStatus = SnowStatus.PLAYING,
    val loseReason: SnowLoseReason? = null,
    val rngState: Int = 1,
)

/** Campaign progress persisted through the app-state store. */
data class SnowProgress(
    val completed: Set<Int> = emptySet(),
    val lastLevel: Int = 1,
    val tutorialCampaignSeen: Boolean = false,
    val tutorialSurvivalSeen: Boolean = false,
)

data class SnowPoint(val x: Double, val y: Double)

object SnowballEngine {
    const val COLUMNS = 8
    const val ROWS = 13
    const val BLOCK = 34.0
    const val BOARD_WIDTH = COLUMNS * BLOCK
    const val BOARD_HEIGHT = ROWS * BLOCK
    const val HUD_HEIGHT = 40.0
    const val PLAYER_RADIUS = 13.0
    const val COIN_RADIUS = 11.0
    const val WALL_BOUNCE = 0.4
    const val FRAME_MS = 1000.0 / 60.0

    const val CAMPAIGN_LEVELS = 25
    const val CAMPAIGN_COIN_POINTS = 1000
    const val SURVIVAL_STAR_POINTS = 1000
    const val SURVIVAL_COINS = 12
    const val FADE_MS = 2500L
    const val RESPAWN_MS = 3500L
    const val FADE_STEP = 0.1f
    const val FADE_CYCLE_MS = 10_000L
    const val FADE_DECREMENT_MS = 1L
    const val RESPAWN_INCREMENT_MS = 15L
    const val MIN_FADE_MS = 400L
    const val COIN_EDGE_MARGIN = 25.0
    const val CENTER_EXCLUSION = 35.0
    const val SPEED_SCALE = 100.0
    const val DEADZONE = 0.1
    const val COUNTER_STEER = 0.98

    private const val MAX_COIN_SPAWN_TRIES = 64

    fun newCampaign(level: Int, seed: Int = level * 7919 + 13): SnowState {
        val safeLevel = level.coerceIn(1, CAMPAIGN_LEVELS)
        val rng = Random(seed)
        val tiles = MutableList(COLUMNS * ROWS) { SnowTile() }
        fun put(col: Int, row: Int, kind: SnowTileKind) {
            if (col in 0 until COLUMNS && row in 0 until ROWS) {
                tiles[row * COLUMNS + col] = SnowTile(kind)
            }
        }

        val spawnCol = 4
        val spawnRow = 3
        for (col in (spawnCol - 1)..spawnCol) {
            for (row in (spawnRow - 2)..spawnRow) put(col, row, SnowTileKind.SAFE)
        }

        val wallClusters = 1 + safeLevel / 2
        repeat(wallClusters) {
            val col = rng.nextInt(COLUMNS)
            val row = 4 + rng.nextInt(ROWS - 6)
            put(col, row, SnowTileKind.WALL)
            if (rng.nextBoolean()) put(col + 1, row, SnowTileKind.WALL)
            if (safeLevel >= 6 && rng.nextBoolean()) put(col, row + 1, SnowTileKind.WALL)
        }

        if (safeLevel >= 8) {
            repeat(1 + (safeLevel - 8) / 3) {
                val col = rng.nextInt(COLUMNS)
                val row = 5 + rng.nextInt(ROWS - 6)
                put(col, row, SnowTileKind.ICE)
                if (rng.nextBoolean()) put(col, row + 1, SnowTileKind.ICE)
            }
        }

        val coinCount = (4 + safeLevel / 3).coerceIn(4, 9)
        val coins = mutableListOf<SnowCoin>()
        var guard = 0
        while (coins.size < coinCount && guard++ < 512) {
            val col = rng.nextInt(COLUMNS)
            val row = 5 + rng.nextInt(ROWS - 6)
            if (tiles[row * COLUMNS + col].kind == SnowTileKind.WALL) continue
            val x = col * BLOCK + BLOCK / 2
            val y = row * BLOCK + BLOCK / 2
            if (coins.any { it.x == x && it.y == y }) continue
            coins += SnowCoin(
                id = coins.size,
                x = x,
                y = y,
                number = coins.size + 1,
                spinDegPerSec = rng.nextDouble(-30.0, 30.0),
            )
        }

        return SnowState(
            mode = SnowMode.CAMPAIGN,
            level = safeLevel,
            seed = seed,
            tiles = tiles.toList(),
            playerX = spawnCol * BLOCK + BLOCK / 2,
            playerY = spawnRow * BLOCK + BLOCK / 2,
            coins = coins,
            nextCoin = coins.maxOfOrNull { it.number } ?: 0,
            rngState = seed,
        )
    }

    fun newSurvival(seed: Int = (System.nanoTime() ushr 3).toInt()): SnowState {
        val tiles = List(COLUMNS * ROWS) { SnowTile() }
        var state = SnowState(
            mode = SnowMode.SURVIVAL,
            level = 0,
            seed = seed,
            tiles = tiles,
            playerX = BOARD_WIDTH / 2,
            playerY = BOARD_HEIGHT / 2,
            rngState = seed,
        )
        repeat(SURVIVAL_COINS) { index ->
            val spawned = spawnSurvivalCoin(state, index)
            state = state.copy(coins = state.coins + spawned.first, rngState = spawned.second)
        }
        return state
    }

    fun step(
        state: SnowState,
        dtMs: Long,
        tiltX: Double = 0.0,
        tiltY: Double = 0.0,
    ): SnowState {
        if (state.status != SnowStatus.PLAYING || dtMs <= 0L) return state
        val dtSec = dtMs / 1000.0
        val frames = dtMs / FRAME_MS

        var vx = state.vx
        var vy = state.vy
        val ax = if (abs(tiltX) < DEADZONE) 0.0 else tiltX
        val ay = if (abs(tiltY) < DEADZONE) 0.0 else tiltY
        vx += ax * SPEED_SCALE * dtSec
        vy += ay * SPEED_SCALE * dtSec
        if (ax * vx < 0.0) vx *= counterSteer(frames)
        if (ay * vy < 0.0) vy *= counterSteer(frames)

        var x = state.playerX + vx * dtSec
        var y = state.playerY + vy * dtSec

        val radius = PLAYER_RADIUS
        if (x < radius) {
            x = radius
            vx = -vx * WALL_BOUNCE
        } else if (x > BOARD_WIDTH - radius) {
            x = BOARD_WIDTH - radius
            vx = -vx * WALL_BOUNCE
        }
        if (y < radius) {
            y = radius
            vy = -vy * WALL_BOUNCE
        } else if (y > BOARD_HEIGHT - radius) {
            y = BOARD_HEIGHT - radius
            vy = -vy * WALL_BOUNCE
        }

        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val tile = state.tiles[row * COLUMNS + col]
                if (tile.kind != SnowTileKind.WALL) continue
                val left = col * BLOCK
                val top = row * BLOCK
                val overlapX = min(x + radius, left + BLOCK) - maxOf(x - radius, left)
                val overlapY = min(y + radius, top + BLOCK) - maxOf(y - radius, top)
                if (overlapX <= 0.0 || overlapY <= 0.0) continue
                if (overlapX < overlapY) {
                    x = if (x < left + BLOCK / 2) x - overlapX else x + overlapX
                    vx = -vx * WALL_BOUNCE
                } else {
                    y = if (y < top + BLOCK / 2) y - overlapY else y + overlapY
                    vy = -vy * WALL_BOUNCE
                }
            }
        }

        var tiles = state.tiles
        val touched = HashSet<Int>()
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val index = row * COLUMNS + col
                val tile = tiles[index]
                if (!tile.fadeable || tile.phase != SnowTilePhase.NORMAL) continue
                val left = col * BLOCK
                val top = row * BLOCK
                if (x + radius <= left || x - radius >= left + BLOCK) continue
                if (y + radius <= top || y - radius >= top + BLOCK) continue
                touched += index
            }
        }
        if (touched.isNotEmpty()) {
            val updated = tiles.toMutableList()
            for (index in touched) updated[index] = updated[index].copy(phase = SnowTilePhase.FADING, timerMs = 0L, alpha = 1f)
            tiles = updated
        }
        tiles = tiles.map { tickTile(it, dtMs, state.fadeMs, state.respawnMs) }

        val col = (x / BLOCK).toInt().coerceIn(0, COLUMNS - 1)
        val row = (y / BLOCK).toInt().coerceIn(0, ROWS - 1)
        val under = tiles[row * COLUMNS + col]
        if (under.phase == SnowTilePhase.KILL) {
            return state.copy(
                tiles = tiles,
                playerX = x,
                playerY = y,
                vx = vx,
                vy = vy,
                angleDeg = advanceAngle(state.angleDeg, vx, vy, dtSec),
                status = SnowStatus.LOST,
                loseReason = SnowLoseReason.HOLE,
            )
        }

        var score = state.score
        var coins = state.coins
        var nextCoin = state.nextCoin
        var status = SnowStatus.PLAYING
        var loseReason: SnowLoseReason? = null
        var rngState = state.rngState
        val hitIndex = coins.indexOfFirst { coin ->
            val dx = coin.x - x
            val dy = coin.y - y
            sqrt(dx * dx + dy * dy) <= PLAYER_RADIUS + COIN_RADIUS
        }
        if (hitIndex >= 0) {
            val coin = coins[hitIndex]
            if (state.mode == SnowMode.CAMPAIGN) {
                if (coin.number == nextCoin) {
                    coins = coins.toMutableList().also { it.removeAt(hitIndex) }
                    nextCoin--
                    score += CAMPAIGN_COIN_POINTS
                    if (coins.isEmpty()) status = SnowStatus.WON
                } else {
                    status = SnowStatus.LOST
                    loseReason = SnowLoseReason.WRONG_COIN
                }
            } else {
                score += SURVIVAL_STAR_POINTS
                coins = coins.toMutableList().also { it.removeAt(hitIndex) }
                val spawned = spawnSurvivalCoin(state.copy(coins = coins, rngState = rngState), coin.id)
                coins = coins + spawned.first
                rngState = spawned.second
            }
        }

        val spinning = coins.map { coin ->
            coin.copy(rotationDeg = (coin.rotationDeg + coin.spinDegPerSec * dtSec) % 360.0)
        }

        var fadeMs = state.fadeMs
        var respawnMs = state.respawnMs
        var cycleAccum = state.cycleAccumMs + dtMs
        var fadeCycles = state.fadeCycles
        if (state.mode == SnowMode.SURVIVAL) {
            while (cycleAccum >= FADE_CYCLE_MS) {
                cycleAccum -= FADE_CYCLE_MS
                fadeCycles++
                fadeMs = (fadeMs - FADE_DECREMENT_MS).coerceAtLeast(MIN_FADE_MS)
                respawnMs += RESPAWN_INCREMENT_MS
            }
        }

        return state.copy(
            tiles = tiles,
            playerX = x,
            playerY = y,
            vx = vx,
            vy = vy,
            angleDeg = advanceAngle(state.angleDeg, vx, vy, dtSec),
            coins = spinning,
            nextCoin = nextCoin,
            score = score,
            clockMs = state.clockMs + dtMs,
            fadeMs = fadeMs,
            respawnMs = respawnMs,
            cycleAccumMs = cycleAccum,
            fadeCycles = fadeCycles,
            status = status,
            loseReason = loseReason,
            rngState = rngState,
        )
    }

    /** Advance one tile through Normal -> Fading -> Kill -> Respawning. */
    fun tickTile(
        tile: SnowTile,
        dtMs: Long,
        fadeMs: Long = FADE_MS,
        respawnMs: Long = RESPAWN_MS,
    ): SnowTile {
        if (dtMs <= 0L) return tile
        return when (tile.phase) {
            SnowTilePhase.NORMAL -> tile
            SnowTilePhase.FADING -> {
                val timer = tile.timerMs + dtMs
                if (timer < fadeMs) {
                    tile.copy(timerMs = timer, alpha = 1f)
                } else {
                    val dropFrames = (timer - fadeMs) / FRAME_MS
                    val alpha = (1f - FADE_STEP * dropFrames.toFloat()).coerceAtLeast(0f)
                    if (alpha <= 0f) {
                        tile.copy(phase = SnowTilePhase.KILL, timerMs = 0L, alpha = 0f)
                    } else {
                        tile.copy(timerMs = timer, alpha = alpha)
                    }
                }
            }

            SnowTilePhase.KILL -> {
                val timer = tile.timerMs + dtMs
                if (timer >= respawnMs) {
                    tile.copy(phase = SnowTilePhase.RESPAWNING, timerMs = 0L, alpha = 0f)
                } else {
                    tile.copy(timerMs = timer, alpha = 0f)
                }
            }

            SnowTilePhase.RESPAWNING -> {
                val alpha = tile.alpha + FADE_STEP * (dtMs / FRAME_MS).toFloat()
                if (alpha >= 1f) {
                    tile.copy(phase = SnowTilePhase.NORMAL, timerMs = 0L, alpha = 1f)
                } else {
                    tile.copy(alpha = alpha)
                }
            }
        }
    }

    /** Survival colour cycle: fade -1 ms, respawn +15 ms. */
    fun cycleFade(state: SnowState): SnowState = state.copy(
        fadeMs = (state.fadeMs - FADE_DECREMENT_MS).coerceAtLeast(MIN_FADE_MS),
        respawnMs = state.respawnMs + RESPAWN_INCREMENT_MS,
        fadeCycles = state.fadeCycles + 1,
    )

    /** Five-level block unlock: finish every level of a block to open the next five. */
    fun highestUnlocked(completed: Set<Int>, total: Int = CAMPAIGN_LEVELS): Int {
        var unlocked = if (completed.isEmpty()) 1 else 1
        for (level in 1 until total) {
            if (level !in completed) break
            unlocked = level + 1
            if (level % 5 == 0) unlocked = min(total, level + 5)
        }
        return unlocked
    }

    fun levelCode(level: Int): String = "${(level - 1) / 5 + 1}-${(level - 1) % 5 + 1}"

    fun scoreLabel(score: Int): String = "%,d".format(score)

    fun encodeProgress(progress: SnowProgress): String = listOf(
        "sb1",
        progress.completed.sorted().joinToString(","),
        progress.lastLevel.toString(),
        if (progress.tutorialCampaignSeen) "1" else "0",
        if (progress.tutorialSurvivalSeen) "1" else "0",
    ).joinToString(";")

    fun decodeProgress(blob: String): SnowProgress? = try {
        val parts = blob.split(";")
        if (parts.size < 5 || parts[0] != "sb1") return null
        SnowProgress(
            completed = parts[1].split(",").mapNotNull { it.toIntOrNull() }.toSet(),
            lastLevel = parts[2].toInt().coerceIn(1, CAMPAIGN_LEVELS),
            tutorialCampaignSeen = parts[3] == "1",
            tutorialSurvivalSeen = parts[4] == "1",
        )
    } catch (_: Exception) {
        null
    }

    fun encode(state: SnowState): String = listOf(
        "sb2",
        state.mode.ordinal.toString(),
        state.level.toString(),
        state.seed.toString(),
        state.playerX.toString(),
        state.playerY.toString(),
        state.vx.toString(),
        state.vy.toString(),
        state.nextCoin.toString(),
        state.score.toString(),
        state.clockMs.toString(),
        state.fadeMs.toString(),
        state.respawnMs.toString(),
        state.status.ordinal.toString(),
        state.rngState.toString(),
    ).joinToString(";")

    fun decode(blob: String): SnowState? = try {
        val parts = blob.split(";")
        if (parts.size < 15 || parts[0] != "sb2") return null
        val mode = SnowMode.entries.getOrNull(parts[1].toInt()) ?: return null
        val base = if (mode == SnowMode.CAMPAIGN) {
            newCampaign(parts[2].toInt(), parts[3].toInt())
        } else {
            newSurvival(parts[3].toInt())
        }
        val nextCoin = parts[8].toInt()
        val coins = if (mode == SnowMode.CAMPAIGN && nextCoin > 0) {
            base.coins.filter { it.number <= nextCoin }
        } else {
            base.coins
        }
        return base.copy(
            playerX = parts[4].toDouble(),
            playerY = parts[5].toDouble(),
            vx = parts[6].toDouble(),
            vy = parts[7].toDouble(),
            coins = coins,
            nextCoin = nextCoin,
            score = parts[9].toInt(),
            clockMs = parts[10].toLong(),
            fadeMs = parts[11].toLong(),
            respawnMs = parts[12].toLong(),
            status = SnowStatus.entries.getOrNull(parts[13].toInt()) ?: SnowStatus.PLAYING,
            rngState = parts[14].toInt(),
        )
    } catch (_: Exception) {
        null
    }

    /** Position of the next campaign-style survival replacement. */
    fun nextSurvivalPosition(
        state: SnowState,
        width: Double = BOARD_WIDTH,
        height: Double = BOARD_HEIGHT,
    ): SnowPoint {
        var rng = state.rngState
        var x = width / 2
        var y = height / 2
        var guard = 0
        while (guard++ < MAX_COIN_SPAWN_TRIES) {
            val rx = nextRandom(rng)
            rng = rx.first
            val ry = nextRandom(rng)
            rng = ry.first
            x = COIN_EDGE_MARGIN + rx.second * (width - COIN_EDGE_MARGIN * 2)
            y = COIN_EDGE_MARGIN + ry.second * (height - COIN_EDGE_MARGIN * 2)
            val insideCentreX = abs(x - width / 2) <= CENTER_EXCLUSION
            val insideCentreY = abs(y - height / 2) <= CENTER_EXCLUSION
            if (!(insideCentreX && insideCentreY)) break
        }
        return SnowPoint(x, y)
    }

    private fun spawnSurvivalCoin(state: SnowState, id: Int): Pair<SnowCoin, Int> {
        val point = nextSurvivalPosition(state)
        val spinRoll = nextRandom(state.rngState)
        val spin = (spinRoll.second * 60.0) - 30.0
        return Pair(
            SnowCoin(id = id, x = point.x, y = point.y, spinDegPerSec = spin),
            spinRoll.first,
        )
    }

    private fun nextRandom(state: Int): Pair<Int, Double> {
        val next = state * 1664525 + 1013904223
        val value = ((next ushr 8) and 0xFFFF) / 65535.0
        return Pair(next, value)
    }

    private fun counterSteer(frames: Double): Double =
        Math.pow(COUNTER_STEER, frames.coerceAtMost(64.0))

    private fun advanceAngle(angle: Double, vx: Double, vy: Double, dtSec: Double): Double {
        val speed = sqrt(vx * vx + vy * vy)
        if (speed < 1e-6) return angle
        val direction = if (vx < 0) -1.0 else 1.0
        return (angle + direction * (speed / PLAYER_RADIUS) * dtSec * 180.0 / PI) % 360.0
    }
}
