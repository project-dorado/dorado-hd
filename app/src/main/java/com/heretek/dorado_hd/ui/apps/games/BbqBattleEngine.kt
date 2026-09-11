package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * BBQ Battle — clean-room engine for the Zune HD picnic tower defence.
 *
 * Behaviour re-derived from docs/apps/bbq-battle.md; no Microsoft code, data or
 * assets. The board is 11x7 cells of 36 px at (20, 20) in the 480x272 device
 * space. Creeps walk a BFS route from the left edge to the food on the right
 * edge; blocking towers may never seal that route.
 */
const val BBQ_GRID_W = 11
const val BBQ_GRID_H = 7
const val BBQ_CELL = 36
const val BBQ_GRID_X = 20
const val BBQ_GRID_Y = 20
const val BBQ_FOOD_HP = 100
const val BBQ_START_CREDITS = 20
const val BBQ_ENTRY_X = 0
const val BBQ_ENTRY_Y = 3
const val BBQ_GOAL_X = 10
const val BBQ_GOAL_Y = 3
const val BBQ_FIRST_WAVE_MS = 5_000L
const val BBQ_PREVIEW_MS = 5_000L
const val BBQ_MAX_TOWER_LEVEL = 3
const val BBQ_SLOW_MS = 250L

data class BbqPoint(val x: Int, val y: Int)

/** Deterministic linear-congruential generator (no platform Random). */
class BbqRandom(seed: Int) {
    var state: Int = seed
    fun nextSeed(): Int {
        state = state * 1103515245 + 12345
        return state
    }

    fun next(bound: Int): Int {
        if (bound <= 0) return 0
        return ((nextSeed() ushr 16) and 0x7FFF) % bound
    }
}

enum class BbqTowerType(
    val label: String,
    val cost: Int,
    val upgradeCosts: List<Int>,
    val blockPath: Boolean,
) {
    SPRAY("spray", 6, listOf(3, 4, 5), true),
    GUM("gum", 8, listOf(4, 5, 6), false),
    BOMB("bomb", 12, listOf(6, 8, 10), true),
    ZAPPER("zapper", 15, listOf(7, 9, 11), true),
}

data class BbqTowerStats(
    val radius: Float,
    val damage: Float,
    val cooldownMs: Long,
    val slowFactor: Float,
)

/** Level 0 is a fresh tower; each level applies the next authored upgrade. */
fun BbqTowerType.stats(level: Int): BbqTowerStats {
    val l = level.coerceIn(0, BBQ_MAX_TOWER_LEVEL)
    return when (this) {
        BbqTowerType.SPRAY -> BbqTowerStats(
            radius = 48f + if (l >= 2) 12f else 0f,
            damage = 5f + if (l >= 1) 5f else 0f,
            cooldownMs = ((1.125f - if (l >= 3) 0.425f else 0f) * 1000f).toLong(),
            slowFactor = 1f,
        )
        BbqTowerType.GUM -> BbqTowerStats(
            radius = 20f,
            damage = if (l >= 2) 0.55f else 0f,
            cooldownMs = 240L,
            slowFactor = 0.45f - (if (l >= 1) 0.10f else 0f) - (if (l >= 3) 0.10f else 0f),
        )
        BbqTowerType.BOMB -> BbqTowerStats(
            radius = 66f + if (l >= 1) 11f else 0f,
            damage = 2.15f + if (l >= 3) 1f else 0f,
            cooldownMs = ((0.8f - if (l >= 2) 0.15f else 0f) * 1000f).toLong(),
            slowFactor = 1f,
        )
        BbqTowerType.ZAPPER -> BbqTowerStats(
            radius = 96f + if (l >= 1) 16f else 0f,
            damage = 50f + if (l >= 3) 25f else 0f,
            cooldownMs = ((1.5f - if (l >= 2) 0.75f else 0f) * 1000f).toLong(),
            slowFactor = 1f,
        )
    }
}

enum class BbqCreepKind(val label: String) {
    ANT("ant"),
    SNAIL("snail"),
    CENTIPEDE("centipede"),
    BEE("bee");

    val flying: Boolean get() = this == BEE

    fun maxHp(level: Int): Float = when (this) {
        ANT -> 30f + 20f * level
        SNAIL -> 85f + 43f * level
        CENTIPEDE -> 25f + 16f * level
        BEE -> 150f + 30.5f * level
    }

    fun speed(level: Int): Float = when (this) {
        ANT -> 20f + 1.75f * level
        SNAIL -> 15f + 1f * level
        CENTIPEDE -> min(200f, 50f + 4f * level)
        BEE -> 25f
    }

    /** Repeat scaling base: ants ramp hardest, bees slowest. */
    val repeatStep: Int get() = when (this) {
        ANT -> 9
        SNAIL -> 8
        CENTIPEDE -> 7
        BEE -> 6
    }
}

data class BbqBonuses(
    val damageIncrease: Float = 1f,
    val cooldownReduction: Float = 1f,
    val costReduction: Int = 0,
    val interestIncrease: Float = 0f,
    val radiusIncrease: Float = 1f,
    val upgradeCostReduction: Int = 0,
    val count: Int = 0,
)

data class BbqTower(
    val id: Int,
    val type: BbqTowerType,
    val x: Int,
    val y: Int,
    val level: Int = 0,
    val fireTimerMs: Long = 0L,
)

data class BbqCreep(
    val id: Int,
    val kind: BbqCreepKind,
    val level: Int,
    val x: Float,
    val y: Float,
    val hp: Float,
    val maxHp: Float,
    val pathIndex: Int,
    val slowMs: Long = 0L,
    val slowFactor: Float = 1f,
)

data class BbqSpawnRow(val kind: BbqCreepKind, val level: Int, val count: Int)

data class BbqWave(
    val spawnFrequencyMs: Long,
    val delayMs: Long,
    val repeat: Int,
    val rows: List<BbqSpawnRow>,
)

data class BbqSpawn(val kind: BbqCreepKind, val level: Int)

enum class BbqEvent {
    PLACE, UPGRADE, SELL, INVALID,
    SPRAY_SHOT, GUM_PULSE, BOMB_BLAST, ZAPPER_SHOT,
    DEATH_ANT, DEATH_SNAIL, DEATH_CENTIPEDE, DEATH_BEE,
    BITE, WAVE_START, WAVE_END, INTEREST, MILESTONE, GAME_OVER,
}

data class BbqBattleState(
    val seed: Int,
    val rng: Int,
    val easyMode: Boolean = false,
    val credits: Int = BBQ_START_CREDITS,
    val foodHp: Int = BBQ_FOOD_HP,
    val foodId: Int = 0,
    val towers: List<BbqTower> = emptyList(),
    val creeps: List<BbqCreep> = emptyList(),
    val route: List<BbqPoint> = emptyList(),
    val completedWaves: Int = 0,
    val waveIndex: Int = 0,
    val repeats: Int = 0,
    val waveStartsInMs: Long = BBQ_FIRST_WAVE_MS,
    val waveTimeMs: Long = 0L,
    val settling: Boolean = false,
    val settleMs: Long = 0L,
    val spawnQueue: List<BbqSpawn> = emptyList(),
    val spawnClockMs: Long = 0L,
    val interestGiven: Boolean = false,
    val preview: List<BbqCreepKind> = emptyList(),
    val milestone: Int? = null,
    val bonuses: BbqBonuses = BbqBonuses(),
    val killed: Int = 0,
    val elapsedMs: Long = 0L,
    val over: Boolean = false,
    val nextId: Int = 1,
    val events: List<BbqEvent> = emptyList(),
)

object BbqBattleEngine {

    /** Cell centres in device pixels. */
    fun center(x: Int, y: Int): Pair<Float, Float> =
        (BBQ_GRID_X + x * BBQ_CELL + BBQ_CELL / 2).toFloat() to
            (BBQ_GRID_Y + y * BBQ_CELL + BBQ_CELL / 2).toFloat()

    /** Authored 30-wave schedule. Difficulty ramps from ants to mixed swarms. */
    val waves: List<BbqWave> = listOf(
        BbqWave(1000, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 0, 5))),
        BbqWave(950, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 0, 7))),
        BbqWave(900, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 1, 5), BbqSpawnRow(BbqCreepKind.ANT, 0, 4))),
        BbqWave(900, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 1, 6), BbqSpawnRow(BbqCreepKind.SNAIL, 0, 2))),
        BbqWave(850, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 2, 7), BbqSpawnRow(BbqCreepKind.SNAIL, 0, 3))),
        BbqWave(850, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 2, 6), BbqSpawnRow(BbqCreepKind.SNAIL, 1, 3))),
        BbqWave(800, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 2, 8), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 0, 1))),
        BbqWave(800, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.SNAIL, 1, 4), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 1, 2))),
        BbqWave(800, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 3, 8), BbqSpawnRow(BbqCreepKind.SNAIL, 1, 4), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 1, 1))),
        BbqWave(750, 4000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 3, 8), BbqSpawnRow(BbqCreepKind.BEE, 0, 2), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 2, 1))),
        BbqWave(750, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 4, 8), BbqSpawnRow(BbqCreepKind.SNAIL, 2, 4), BbqSpawnRow(BbqCreepKind.BEE, 0, 3))),
        BbqWave(700, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.CENTIPEDE, 2, 3), BbqSpawnRow(BbqCreepKind.BEE, 1, 3), BbqSpawnRow(BbqCreepKind.SNAIL, 2, 4))),
        BbqWave(700, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 5, 10), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 2, 3), BbqSpawnRow(BbqCreepKind.BEE, 1, 3))),
        BbqWave(700, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 5, 8), BbqSpawnRow(BbqCreepKind.SNAIL, 3, 4))),
        BbqWave(650, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 6, 10), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 3, 4), BbqSpawnRow(BbqCreepKind.BEE, 2, 3))),
        BbqWave(650, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 5, 8), BbqSpawnRow(BbqCreepKind.SNAIL, 3, 5))),
        BbqWave(600, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 6, 10), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 3, 4), BbqSpawnRow(BbqCreepKind.BEE, 2, 4))),
        BbqWave(600, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.SNAIL, 4, 5), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 3, 3))),
        BbqWave(600, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 7, 12), BbqSpawnRow(BbqCreepKind.BEE, 3, 4), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 4, 3))),
        BbqWave(550, 4000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 7, 10), BbqSpawnRow(BbqCreepKind.SNAIL, 5, 5), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 4, 3), BbqSpawnRow(BbqCreepKind.BEE, 3, 4))),
        BbqWave(550, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 7, 10), BbqSpawnRow(BbqCreepKind.BEE, 3, 3))),
        BbqWave(550, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.CENTIPEDE, 5, 5), BbqSpawnRow(BbqCreepKind.SNAIL, 5, 6), BbqSpawnRow(BbqCreepKind.BEE, 4, 4))),
        BbqWave(500, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 8, 10), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 5, 3))),
        BbqWave(500, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.BEE, 4, 6), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 5, 4), BbqSpawnRow(BbqCreepKind.SNAIL, 6, 5))),
        BbqWave(500, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 8, 12), BbqSpawnRow(BbqCreepKind.BEE, 5, 4))),
        BbqWave(450, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.CENTIPEDE, 6, 5), BbqSpawnRow(BbqCreepKind.SNAIL, 6, 6), BbqSpawnRow(BbqCreepKind.BEE, 5, 5))),
        BbqWave(450, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 9, 12), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 6, 4))),
        BbqWave(450, 3000, 1, listOf(BbqSpawnRow(BbqCreepKind.BEE, 6, 6), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 7, 5), BbqSpawnRow(BbqCreepKind.SNAIL, 7, 6))),
        BbqWave(400, 3000, 2, listOf(BbqSpawnRow(BbqCreepKind.ANT, 10, 14), BbqSpawnRow(BbqCreepKind.BEE, 6, 5))),
        BbqWave(400, 4000, 1, listOf(BbqSpawnRow(BbqCreepKind.ANT, 11, 12), BbqSpawnRow(BbqCreepKind.SNAIL, 8, 6), BbqSpawnRow(BbqCreepKind.CENTIPEDE, 8, 6), BbqSpawnRow(BbqCreepKind.BEE, 8, 6))),
    )

    fun newGame(seed: Int, easyMode: Boolean = false): BbqBattleState {
        val rng = BbqRandom(seed)
        val foodId = rng.next(4)
        return BbqBattleState(
            seed = seed,
            rng = rng.state,
            easyMode = easyMode,
            foodId = foodId,
            route = route(emptyList()),
            preview = previewFor(0),
        )
    }

    // ---------------------------------------------------------------- route --

    fun inGrid(x: Int, y: Int): Boolean = x in 0 until BBQ_GRID_W && y in 0 until BBQ_GRID_H

    /**
     * BFS from the food (10, 3) over cells not blocked by a blocking tower,
     * then reconstruct the route from the left-edge entry (0, 3). Returns an
     * empty list when blockers have sealed the route.
     */
    fun route(towers: List<BbqTower>): List<BbqPoint> {
        val blocked = BooleanArray(BBQ_GRID_W * BBQ_GRID_H)
        for (t in towers) {
            if (t.type.blockPath && inGrid(t.x, t.y)) blocked[t.y * BBQ_GRID_W + t.x] = true
        }
        val dist = IntArray(BBQ_GRID_W * BBQ_GRID_H) { -1 }
        val queue = ArrayDeque<Int>()
        val goal = BBQ_GOAL_Y * BBQ_GRID_W + BBQ_GOAL_X
        dist[goal] = 0
        queue.addLast(goal)
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val cx = cell % BBQ_GRID_W
            val cy = cell / BBQ_GRID_W
            for ((dx, dy) in dirs) {
                val nx = cx + dx
                val ny = cy + dy
                if (!inGrid(nx, ny)) continue
                val index = ny * BBQ_GRID_W + nx
                if (blocked[index] || dist[index] >= 0) continue
                dist[index] = dist[cell] + 1
                queue.addLast(index)
            }
        }
        val entry = BBQ_ENTRY_Y * BBQ_GRID_W + BBQ_ENTRY_X
        if (dist[entry] < 0) return emptyList()
        val path = mutableListOf(BbqPoint(BBQ_ENTRY_X, BBQ_ENTRY_Y))
        var x = BBQ_ENTRY_X
        var y = BBQ_ENTRY_Y
        var guard = 0
        while (dist[y * BBQ_GRID_W + x] != 0 && guard < BBQ_GRID_W * BBQ_GRID_H) {
            guard++
            var best: Pair<Int, Int>? = null
            var bestDist = dist[y * BBQ_GRID_W + x]
            for ((dx, dy) in dirs) {
                val nx = x + dx
                val ny = y + dy
                if (!inGrid(nx, ny)) continue
                val d = dist[ny * BBQ_GRID_W + nx]
                if (d in 0 until bestDist) {
                    best = nx to ny
                    bestDist = d
                }
            }
            val next = best ?: return path
            x = next.first
            y = next.second
            path.add(BbqPoint(x, y))
        }
        return path
    }

    // ------------------------------------------------------------- economy --

    fun buildCost(type: BbqTowerType, bonuses: BbqBonuses): Int =
        max(0, type.cost - bonuses.costReduction)

    fun upgradeCost(tower: BbqTower, bonuses: BbqBonuses): Int? {
        if (tower.level >= BBQ_MAX_TOWER_LEVEL) return null
        return max(0, tower.type.upgradeCosts[tower.level] - bonuses.upgradeCostReduction)
    }

    /** 50 % of build plus every paid upgrade, floored per component. */
    fun sellValue(tower: BbqTower): Int {
        var total = floor(tower.type.cost * 0.5f).toInt()
        for (i in 0 until tower.level) total += floor(tower.type.upgradeCosts[i] * 0.5f).toInt()
        return total
    }

    /** min(20, floor(credits * (0.125 + bonus)) + 10). */
    fun interestFor(credits: Int, increase: Float): Int =
        min(20, floor(credits * (0.125 + increase)).toInt() + 10)

    // ---------------------------------------------------------------- waves --

    fun spawnLevel(kind: BbqCreepKind, authoredLevel: Int, repeats: Int): Int =
        authoredLevel + repeats * (kind.repeatStep + repeats * repeats)

    /** Spawns in decompiled order: repeat block, then rows, then per-row count. */
    fun waveSpawns(waveIndex: Int, repeats: Int): List<BbqSpawn> {
        val wave = waves[waveIndex.coerceIn(0, waves.size - 1)]
        val out = mutableListOf<BbqSpawn>()
        repeat(wave.repeat * (repeats + 1)) {
            for (row in wave.rows) {
                repeat(row.count) {
                    out += BbqSpawn(row.kind, spawnLevel(row.kind, row.level, repeats))
                }
            }
        }
        return out
    }

    fun spawnIntervalMs(waveIndex: Int, repeats: Int): Long {
        val wave = waves[waveIndex.coerceIn(0, waves.size - 1)]
        return wave.spawnFrequencyMs / (repeats + 1)
    }

    fun waveTotalMs(waveIndex: Int, repeats: Int): Long =
        waveSpawns(waveIndex, repeats).size * spawnIntervalMs(waveIndex, repeats) + waves[waveIndex].delayMs

    /** Three-wave type preview ahead of the given wave. */
    fun previewFor(waveIndex: Int): List<BbqCreepKind> {
        val out = mutableListOf<BbqCreepKind>()
        for (offset in 0 until 3) {
            val wave = waves[(waveIndex + offset) % waves.size]
            for (row in wave.rows) if (row.kind !in out) out += row.kind
        }
        return out
    }

    // -------------------------------------------------------------- actions --

    private fun withEvent(state: BbqBattleState, event: BbqEvent): BbqBattleState =
        state.copy(events = listOf(event))

    fun build(state: BbqBattleState, x: Int, y: Int, type: BbqTowerType): BbqBattleState {
        if (state.over || state.milestone != null) return withEvent(state, BbqEvent.INVALID)
        if (!inGrid(x, y)) return withEvent(state, BbqEvent.INVALID)
        if (x == BBQ_GOAL_X && y == BBQ_GOAL_Y) return withEvent(state, BbqEvent.INVALID)
        if (state.towers.any { it.x == x && it.y == y }) return withEvent(state, BbqEvent.INVALID)
        val cost = buildCost(type, state.bonuses)
        if (state.credits < cost) return withEvent(state, BbqEvent.INVALID)
        val level = if (state.easyMode) 1 else 0
        val candidate = state.towers + BbqTower(state.nextId, type, x, y, level)
        val newRoute = route(candidate)
        if (type.blockPath && newRoute.isEmpty()) return withEvent(state, BbqEvent.INVALID)
        return state.copy(
            credits = state.credits - cost,
            towers = candidate,
            route = newRoute,
            creeps = realign(state.creeps, newRoute),
            nextId = state.nextId + 1,
            events = listOf(BbqEvent.PLACE),
        )
    }

    fun upgrade(state: BbqBattleState, x: Int, y: Int): BbqBattleState {
        if (state.over || state.milestone != null) return withEvent(state, BbqEvent.INVALID)
        val index = state.towers.indexOfFirst { it.x == x && it.y == y }
        if (index < 0) return withEvent(state, BbqEvent.INVALID)
        val tower = state.towers[index]
        val cost = upgradeCost(tower, state.bonuses) ?: return withEvent(state, BbqEvent.INVALID)
        if (state.credits < cost) return withEvent(state, BbqEvent.INVALID)
        val towers = state.towers.toMutableList()
        towers[index] = tower.copy(level = tower.level + 1)
        return state.copy(
            credits = state.credits - cost,
            towers = towers,
            events = listOf(BbqEvent.UPGRADE),
        )
    }

    fun sell(state: BbqBattleState, x: Int, y: Int): BbqBattleState {
        if (state.over || state.milestone != null) return withEvent(state, BbqEvent.INVALID)
        val index = state.towers.indexOfFirst { it.x == x && it.y == y }
        if (index < 0) return withEvent(state, BbqEvent.INVALID)
        val tower = state.towers[index]
        val refund = sellValue(tower)
        val towers = state.towers.toMutableList().also { it.removeAt(index) }
        val newRoute = route(towers)
        return state.copy(
            credits = state.credits + refund,
            towers = towers,
            route = newRoute,
            creeps = realign(state.creeps, newRoute),
            events = listOf(BbqEvent.SELL),
        )
    }

    /** Applies milestone choice 0 or 1 after waves 10/20/30. */
    fun chooseBonus(state: BbqBattleState, option: Int): BbqBattleState {
        val wave = state.milestone ?: return withEvent(state, BbqEvent.INVALID)
        val bonuses = state.bonuses
        val next = when (wave) {
            10 -> if (option == 0) bonuses.copy(damageIncrease = bonuses.damageIncrease + 0.1f) else bonuses.copy(costReduction = bonuses.costReduction + 1)
            20 -> if (option == 0) bonuses.copy(cooldownReduction = bonuses.cooldownReduction - 0.1f) else bonuses.copy(interestIncrease = bonuses.interestIncrease + 0.1f)
            else -> if (option == 0) bonuses.copy(radiusIncrease = bonuses.radiusIncrease + 0.075f) else bonuses.copy(upgradeCostReduction = bonuses.upgradeCostReduction + 1)
        }
        return startWave(state.copy(bonuses = next.copy(count = next.count + 1), milestone = null), state.completedWaves)
    }

    // ---------------------------------------------------------------- step --

    fun step(state: BbqBattleState, dtMs: Long): BbqBattleState = step(state, dtMs, emptyList())

    fun step(state: BbqBattleState, dtMs: Long, extra: List<BbqSpawn>): BbqBattleState {
        if (state.over || state.milestone != null || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<BbqEvent>()
        var credits = state.credits
        var foodHp = state.foodHp
        var killed = state.killed
        var over = state.over
        var nextId = state.nextId
        var creeps = state.creeps.toMutableList()
        var route = state.route
        var waveIndex = state.waveIndex
        var repeats = state.repeats
        var completed = state.completedWaves
        var waveStartsIn = state.waveStartsInMs
        var waveTime = state.waveTimeMs
        var settling = state.settling
        var settle = state.settleMs
        var queue = state.spawnQueue
        var spawnClock = state.spawnClockMs
        var interestGiven = state.interestGiven
        var preview = state.preview
        var milestone = state.milestone
        val elapsed = state.elapsedMs + dtMs

        if (waveStartsIn > 0) {
            waveStartsIn -= dtMs
            if (waveStartsIn <= 0) {
                waveStartsIn = 0
                queue = waveSpawns(waveIndex, repeats)
                spawnClock = 0
                waveTime = 0
                interestGiven = false
                settling = false
                preview = emptyList()
                events += BbqEvent.WAVE_START
            }
        }

        // Spawning / settling.
        if (waveStartsIn <= 0 && !settling) {
            waveTime += dtMs
            val interval = spawnIntervalMs(waveIndex, repeats).coerceAtLeast(1L)
            spawnClock += dtMs
            while (spawnClock >= interval && queue.isNotEmpty()) {
                spawnClock -= interval
                val spawn = queue.first()
                queue = queue.drop(1)
                val (cx, cy) = center(BBQ_ENTRY_X, BBQ_ENTRY_Y)
                creeps += BbqCreep(
                    id = nextId++,
                    kind = spawn.kind,
                    level = spawn.level,
                    x = cx,
                    y = cy,
                    hp = spawn.kind.maxHp(spawn.level),
                    maxHp = spawn.kind.maxHp(spawn.level),
                    pathIndex = 0,
                )
            }
            if (queue.isEmpty()) {
                settling = true
                settle = waves[waveIndex].delayMs
            }
        }

        // Movement and bites.
        val stillMoving = mutableListOf<BbqCreep>()
        for (creep in creeps) {
            var c = creep
            if (c.slowMs > 0L) c = c.copy(slowMs = max(0L, c.slowMs - dtMs))
            var remaining = c.kind.speed(c.level) * (if (c.slowMs > 0L) c.slowFactor else 1f) * dtMs / 1000f
            var reached = false
            var guard = 0
            while (remaining > 0f && !reached && guard < 64) {
                guard++
                val target: Pair<Float, Float> = if (c.kind.flying) {
                    center(BBQ_GOAL_X, BBQ_GOAL_Y)
                } else {
                    val next = route.getOrNull(c.pathIndex + 1)
                    if (next == null) center(BBQ_GOAL_X, BBQ_GOAL_Y) else center(next.x, next.y)
                }
                val dx = target.first - c.x
                val dy = target.second - c.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist <= remaining || dist < 0.001f) {
                    c = c.copy(x = target.first, y = target.second)
                    remaining -= dist
                    if (c.kind.flying || c.pathIndex + 1 >= route.size) {
                        reached = true
                    } else {
                        c = c.copy(pathIndex = c.pathIndex + 1)
                    }
                } else {
                    c = c.copy(x = c.x + dx / dist * remaining, y = c.y + dy / dist * remaining)
                    remaining = 0f
                }
            }
            if (reached) {
                foodHp -= 1
                events += BbqEvent.BITE
                if (foodHp <= 0) {
                    foodHp = 0
                    over = true
                    events += BbqEvent.GAME_OVER
                    break
                }
            } else {
                stillMoving += c
            }
        }
        creeps = stillMoving

        if (over) {
            return state.copy(
                credits = credits,
                foodHp = foodHp,
                creeps = creeps,
                route = route,
                killed = killed,
                elapsedMs = elapsed,
                over = true,
                events = events,
            )
        }

        // Tower fire (towers sorted by id keeps the tick deterministic).
        val updatedTowers = mutableListOf<BbqTower>()
        for (tower in state.towers.sortedBy { it.id }) {
            val stats = tower.type.stats(tower.level)
            val cooldown = (stats.cooldownMs * state.bonuses.cooldownReduction).roundToLong().coerceAtLeast(50L)
            var timer = tower.fireTimerMs + dtMs
            if (timer <= cooldown) {
                updatedTowers += tower.copy(fireTimerMs = timer)
                continue
            }
            val fired = tryFire(tower, stats, state.bonuses, creeps, events)
            if (fired) timer -= cooldown
            updatedTowers += tower.copy(fireTimerMs = timer)
        }

        // Harvest deaths.
        val survivors = mutableListOf<BbqCreep>()
        for (creep in creeps) {
            if (creep.hp <= 0f) {
                killed++
                events += when (creep.kind) {
                    BbqCreepKind.ANT -> BbqEvent.DEATH_ANT
                    BbqCreepKind.SNAIL -> BbqEvent.DEATH_SNAIL
                    BbqCreepKind.CENTIPEDE -> BbqEvent.DEATH_CENTIPEDE
                    BbqCreepKind.BEE -> BbqEvent.DEATH_BEE
                }
            } else {
                survivors += creep
            }
        }
        creeps = survivors

        // Settle countdown, interest and preview.
        if (settling) {
            settle -= dtMs
            if (!interestGiven && settle <= BBQ_PREVIEW_MS) {
                credits += interestFor(credits, state.bonuses.interestIncrease)
                interestGiven = true
                preview = previewFor((completed + 1) % waves.size)
                events += BbqEvent.INTEREST
            }
            if (settle <= 0L) {
                completed++
                events += BbqEvent.WAVE_END
                if (state.bonuses.count < 3 && completed in listOf(10, 20, 30) && repeats <= 1) {
                    milestone = completed
                    events += BbqEvent.MILESTONE
                } else {
                    val started = startWave(state, completed)
                    waveIndex = started.waveIndex
                    repeats = started.repeats
                    queue = started.spawnQueue
                    spawnClock = started.spawnClockMs
                    waveTime = started.waveTimeMs
                    settling = started.settling
                    settle = started.settleMs
                    interestGiven = started.interestGiven
                    preview = started.preview
                    events += BbqEvent.WAVE_START
                }
            }
        }

        // `extra` spawns let the UI (or tests) inject creeps deterministically.
        for (spawn in extra) {
            val (cx, cy) = center(BBQ_ENTRY_X, BBQ_ENTRY_Y)
            creeps += BbqCreep(
                id = nextId++,
                kind = spawn.kind,
                level = spawn.level,
                x = cx,
                y = cy,
                hp = spawn.kind.maxHp(spawn.level),
                maxHp = spawn.kind.maxHp(spawn.level),
                pathIndex = 0,
            )
        }

        return state.copy(
            credits = credits,
            foodHp = foodHp,
            towers = updatedTowers,
            creeps = creeps,
            route = route,
            completedWaves = completed,
            waveIndex = waveIndex,
            repeats = repeats,
            waveStartsInMs = waveStartsIn,
            waveTimeMs = waveTime,
            settling = settling,
            settleMs = settle,
            spawnQueue = queue,
            spawnClockMs = spawnClock,
            interestGiven = interestGiven,
            preview = preview,
            milestone = milestone,
            killed = killed,
            elapsedMs = elapsed,
            over = over,
            nextId = nextId,
            events = events,
        )
    }

    private fun startWave(base: BbqBattleState, completed: Int): BbqBattleState {
        val waveIndex = completed % waves.size
        val repeats = completed / waves.size
        return base.copy(
            completedWaves = completed,
            waveIndex = waveIndex,
            repeats = repeats,
            waveStartsInMs = 0L,
            waveTimeMs = 0L,
            settling = false,
            settleMs = 0L,
            spawnQueue = waveSpawns(waveIndex, repeats),
            spawnClockMs = 0L,
            interestGiven = false,
            preview = emptyList(),
            events = listOf(BbqEvent.WAVE_START),
        )
    }

    private fun tryFire(
        tower: BbqTower,
        stats: BbqTowerStats,
        bonuses: BbqBonuses,
        creeps: MutableList<BbqCreep>,
        events: MutableList<BbqEvent>,
    ): Boolean {
        val (cx, cy) = center(tower.x, tower.y)
        val radius = stats.radius * bonuses.radiusIncrease
        val radiusSq = radius * radius
        fun within(c: BbqCreep): Boolean {
            val dx = c.x - cx
            val dy = c.y - cy
            return dx * dx + dy * dy <= radiusSq
        }
        val damage = stats.damage * bonuses.damageIncrease
        var fired = false
        when (tower.type) {
            BbqTowerType.SPRAY -> {
                var best = -1
                var bestDist = Float.MAX_VALUE
                creeps.forEachIndexed { index, c ->
                    if (!c.kind.flying && within(c)) {
                        val d = (c.x - cx) * (c.x - cx) + (c.y - cy) * (c.y - cy)
                        if (d < bestDist) {
                            bestDist = d
                            best = index
                        }
                    }
                }
                if (best >= 0) {
                    creeps[best] = creeps[best].copy(hp = creeps[best].hp - damage)
                    events += BbqEvent.SPRAY_SHOT
                    fired = true
                }
            }
            BbqTowerType.GUM -> {
                for (i in creeps.indices) {
                    val c = creeps[i]
                    if (!c.kind.flying && within(c)) {
                        var next = c.copy(slowMs = BBQ_SLOW_MS, slowFactor = stats.slowFactor)
                        if (damage > 0f) next = next.copy(hp = next.hp - damage)
                        creeps[i] = next
                        fired = true
                    }
                }
                if (fired) events += BbqEvent.GUM_PULSE
            }
            BbqTowerType.BOMB -> {
                for (i in creeps.indices) {
                    val c = creeps[i]
                    if (!c.kind.flying && within(c)) {
                        creeps[i] = c.copy(hp = c.hp - damage)
                        fired = true
                    }
                }
                if (fired) events += BbqEvent.BOMB_BLAST
            }
            BbqTowerType.ZAPPER -> {
                var best = -1
                var bestDist = Float.MAX_VALUE
                creeps.forEachIndexed { index, c ->
                    if (c.kind.flying && within(c)) {
                        val d = (c.x - cx) * (c.x - cx) + (c.y - cy) * (c.y - cy)
                        if (d < bestDist) {
                            bestDist = d
                            best = index
                        }
                    }
                }
                if (best < 0) {
                    creeps.forEachIndexed { index, c ->
                        if (!c.kind.flying && within(c)) {
                            val d = (c.x - cx) * (c.x - cx) + (c.y - cy) * (c.y - cy)
                            if (d < bestDist) {
                                bestDist = d
                                best = index
                            }
                        }
                    }
                }
                if (best >= 0) {
                    creeps[best] = creeps[best].copy(hp = creeps[best].hp - damage)
                    var chain = -1
                    var chainDist = Float.MAX_VALUE
                    creeps.forEachIndexed { index, c ->
                        if (index != best && within(c)) {
                            val d = (c.x - cx) * (c.x - cx) + (c.y - cy) * (c.y - cy)
                            if (d < chainDist) {
                                chainDist = d
                                chain = index
                            }
                        }
                    }
                    if (chain >= 0) {
                        creeps[chain] = creeps[chain].copy(hp = creeps[chain].hp - damage / 15f)
                    }
                    events += BbqEvent.ZAPPER_SHOT
                    fired = true
                }
            }
        }
        return fired
    }

    /** Keeps creeps walking forward when the route is recomputed. */
    private fun realign(creeps: List<BbqCreep>, route: List<BbqPoint>): List<BbqCreep> {
        if (route.isEmpty()) return creeps
        return creeps.map { c ->
            if (c.kind.flying) return@map c
            var best = 0
            var bestDist = Float.MAX_VALUE
            route.forEachIndexed { index, point ->
                val (px, py) = center(point.x, point.y)
                val d = (c.x - px) * (c.x - px) + (c.y - py) * (c.y - py)
                if (d < bestDist) {
                    bestDist = d
                    best = index
                }
            }
            c.copy(pathIndex = max(0, min(best, route.size - 1)))
        }
    }

    // ----------------------------------------------------------- persistence --

    fun encode(state: BbqBattleState): String {
        val towers = state.towers.joinToString("/") {
            listOf(it.id, it.type.ordinal, it.x, it.y, it.level, it.fireTimerMs).joinToString(",")
        }
        val creeps = state.creeps.joinToString("/") {
            listOf(
                it.id, it.kind.ordinal, it.level, it.x, it.y, it.hp, it.maxHp, it.pathIndex,
                it.slowMs, it.slowFactor,
            ).joinToString(",")
        }
        val queue = state.spawnQueue.joinToString("/") { "${it.kind.ordinal}:${it.level}" }
        val b = state.bonuses
        return listOf(
            "bbq1",
            state.seed,
            state.rng,
            if (state.easyMode) 1 else 0,
            state.credits,
            state.foodHp,
            state.foodId,
            state.completedWaves,
            state.waveIndex,
            state.repeats,
            state.waveStartsInMs,
            state.waveTimeMs,
            if (state.settling) 1 else 0,
            state.settleMs,
            state.spawnClockMs,
            if (state.interestGiven) 1 else 0,
            state.milestone ?: -1,
            b.damageIncrease,
            b.cooldownReduction,
            b.costReduction,
            b.interestIncrease,
            b.radiusIncrease,
            b.upgradeCostReduction,
            b.count,
            state.killed,
            state.elapsedMs,
            if (state.over) 1 else 0,
            state.nextId,
            towers,
            creeps,
            queue,
        ).joinToString(";")
    }

    fun decode(blob: String): BbqBattleState? = try {
        val p = blob.split(";")
        if (p.size < 31 || p[0] != "bbq1") {
            null
        } else {
            val towers = if (p[28].isEmpty()) emptyList() else p[28].split("/").map { raw ->
                val f = raw.split(",")
                BbqTower(f[0].toInt(), BbqTowerType.entries[f[1].toInt()], f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toLong())
            }
            val creeps = if (p[29].isEmpty()) emptyList() else p[29].split("/").map { raw ->
                val f = raw.split(",")
                BbqCreep(
                    f[0].toInt(), BbqCreepKind.entries[f[1].toInt()], f[2].toInt(),
                    f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), f[6].toFloat(),
                    f[7].toInt(), f[8].toLong(), f[9].toFloat(),
                )
            }
            val queue = if (p[30].isEmpty()) emptyList() else p[30].split("/").map { raw ->
                val f = raw.split(":")
                BbqSpawn(BbqCreepKind.entries[f[0].toInt()], f[1].toInt())
            }
            BbqBattleState(
                seed = p[1].toInt(),
                rng = p[2].toInt(),
                easyMode = p[3] == "1",
                credits = p[4].toInt(),
                foodHp = p[5].toInt(),
                foodId = p[6].toInt(),
                completedWaves = p[7].toInt(),
                waveIndex = p[8].toInt(),
                repeats = p[9].toInt(),
                waveStartsInMs = p[10].toLong(),
                waveTimeMs = p[11].toLong(),
                settling = p[12] == "1",
                settleMs = p[13].toLong(),
                spawnClockMs = p[14].toLong(),
                interestGiven = p[15] == "1",
                milestone = p[16].toInt().takeIf { it >= 0 },
                bonuses = BbqBonuses(
                    p[17].toFloat(), p[18].toFloat(), p[19].toInt(), p[20].toFloat(),
                    p[21].toFloat(), p[22].toInt(), p[23].toInt(),
                ),
                killed = p[24].toInt(),
                elapsedMs = p[25].toLong(),
                over = p[26] == "1",
                nextId = p[27].toInt(),
                towers = towers,
                creeps = creeps,
                spawnQueue = queue,
                route = route(towers),
                preview = previewFor(p[8].toInt()),
            )
        }
    } catch (_: Exception) {
        null
    }
}
