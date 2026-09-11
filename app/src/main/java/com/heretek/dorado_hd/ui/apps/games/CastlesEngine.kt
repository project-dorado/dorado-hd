package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Castles and Cannons — clean-room engine for the Zune HD one-lane siege.
 *
 * Behaviour re-derived from docs/apps/castles-and-cannons.md; no Microsoft
 * code, level data or assets. World x runs 0..960 with a 480 px camera; the
 * ground line sits at y = 195 and cannonballs impact when they cross it.
 */
const val CASTLES_WORLD_W = 960f
const val CASTLES_VIEW_W = 480f
const val CASTLES_VIEW_H = 200f
const val CASTLES_GROUND_Y = 195f
const val CASTLES_HUMAN_GATE = 180f
const val CASTLES_ENEMY_GATE = 780f
const val CASTLES_GRAVITY = 600f
const val CASTLES_MIN_SPEED = 150f
const val CASTLES_MAX_SPEED = 450f
const val CASTLES_GOLD_CAP = 999
const val CASTLES_BUY_COOLDOWN_MS = 5_000L
const val CASTLES_HEAL_COST = 15
const val CASTLES_HEAL_COOLDOWN_MS = 30_000L
const val CASTLES_UNIT_HP = 100f

class CastlesRandom(seed: Int) {
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

enum class CastlesSide { HUMAN, ENEMY }

enum class CastlesBackdrop(val label: String) {
    HILLS("hills"),
    PLAINS("plains"),
    DESERT("desert"),
    WATERFALL("waterfall"),
    MOUNTAIN("mountain"),
    VOLCANO("volcano"),
}

enum class CastlesBossType(val label: String) {
    DRAGON("dragon"),
    TROJAN("trojan"),
    MOLE("mole"),
    IMPS("imps"),
    BRUTE("brute"),
    SUPER_BOSS("super boss"),
}

enum class CastlesUnitKind(
    val label: String,
    val cost: Int,
    val frequencyMs: Long,
    val slot: Int,
) {
    SOLDIER("soldier", 5, 1000, 1),
    ARCHER("archer", 8, 750, 2),
    KNIGHT("knight", 12, 1200, 3),
    BOMBER("bomber", 20, 2500, 4),
}

data class CastlesUnitStats(
    val speed: Float,
    val armor: Float,
    val castleDamage: Float,
    val gold: Int,
    val range: Float,
    val attackCooldownMs: Long,
    val splashRadius: Float,
)

fun CastlesUnitKind.stats(): CastlesUnitStats = when (this) {
    CastlesUnitKind.SOLDIER -> CastlesUnitStats(35f, 10f, 5f, 1, 24f, 800L, 18f)
    CastlesUnitKind.ARCHER -> CastlesUnitStats(40f, 0f, 3f, 1, 140f, 1200L, 16f)
    CastlesUnitKind.KNIGHT -> CastlesUnitStats(55f, 40f, 8f, 2, 26f, 800L, 20f)
    CastlesUnitKind.BOMBER -> CastlesUnitStats(25f, 55f, 8f, 3, 90f, 1500L, 40f)
}

data class CastlesLevel(
    val number: Int,
    val backdrop: CastlesBackdrop,
    val maxHealth: Float,
    val startingGold: Int,
    val goldInterval: Float,
    val snipeEnabled: Boolean,
    val snipeDamage: Float,
    val snipeFrequency: Float,
    val availableUnits: Int,
    val cannons: Int,
    val enemyMaxHealth: Float,
    val enemyGold: Int,
    val enemyGoldInterval: Float,
    val enemyAvailableUnits: Int,
    val boss: CastlesBossType? = null,
    val bossHealth: Float = 0f,
    val bossDelayMs: Long = 0L,
)

data class CastlesSkillTree(
    val offense: Int = 0,
    val defense: Int = 0,
    val utility: Int = 0,
    val unusedPoints: Int = 0,
)

data class CastlesProgress(
    val scores: Map<Int, Int> = emptyMap(),
    val unlocked: Int = 1,
    val skills: CastlesSkillTree = CastlesSkillTree(),
) {
    fun best(level: Int): Int = scores[level] ?: 0
    fun goldMedals(): Int = scores.values.count { it >= 1000 }
    fun completedAll(): Boolean = scores.keys.containsAll((1..CastlesEngine.levels.size).toSet())
}

data class CastlesPlayerStats(
    val maxHealth: Float,
    val startingGold: Int,
    val goldInterval: Float,
    val snipeEnabled: Boolean,
    val snipeDamage: Float,
    val snipeFrequency: Float,
    val availableUnits: Int,
    val cannons: Int,
    val cannonDamage: Float,
    val cannonCooldown: Float,
    val unitDamage: Float,
    val unitArmor: Float,
    val armySize: Int,
)

data class CastlesUnit(
    val id: Int,
    val side: CastlesSide,
    val kind: CastlesUnitKind,
    val x: Float,
    val lane: Int,
    val hp: Float,
    val armor: Float,
    val castleDamage: Float,
    val attackCooldownMs: Long = 0L,
    val boss: CastlesBossType? = null,
    val stage: Int = 1,
)

data class CastlesBall(
    val id: Int,
    val side: CastlesSide,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Float,
)

data class CastlesCoin(val id: Int, val x: Float, val value: Int, val ageMs: Long = 0L)

data class CastlesArmy(
    val id: Int,
    val side: CastlesSide,
    val kind: CastlesUnitKind,
    val remaining: Int,
    val clockMs: Long = 0L,
)

enum class CastlesEvent {
    BUY, INVALID, SPAWN, DEATH, CANNON_FIRE, CANNON_IMPACT, COIN,
    ENEMY_CANNON, SNIPE, HEAL, BOSS_ALERT, CASTLE_HIT, WIN, LOSE,
}

data class CastlesBattleState(
    val level: Int,
    val seed: Int,
    val rng: Int,
    val stats: CastlesPlayerStats,
    val enemyStats: CastlesPlayerStats,
    val humanHp: Float,
    val enemyHp: Float,
    val gold: Int,
    val enemyGold: Int,
    val goldClockMs: Long,
    val enemyGoldClockMs: Long,
    val units: List<CastlesUnit>,
    val balls: List<CastlesBall>,
    val coins: List<CastlesCoin>,
    val armies: List<CastlesArmy>,
    val buyCooldowns: Map<CastlesUnitKind, Long> = CastlesUnitKind.entries.associateWith { 0L },
    val healCooldownMs: Long = 0L,
    val cannonCooldownMs: Long = 0L,
    val snipeClockMs: Long = 0L,
    val enemySpawnClockMs: Long = 0L,
    val enemyCannonClockMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val cannonsFired: Int = 0,
    val unitsKilled: Int = 0,
    val goldAcquired: Int = 0,
    val bossSpawned: Boolean = false,
    val bossTimerMs: Long = 0L,
    val over: Boolean = false,
    val won: Boolean = false,
    val nextId: Int = 1,
    val events: List<CastlesEvent> = emptyList(),
)

data class CastlesScoreParts(val time: Int, val offense: Int, val defense: Int) {
    val total: Int get() = time + offense + defense
}

object CastlesEngine {

    val levels: List<CastlesLevel> = listOf(
        CastlesLevel(1, CastlesBackdrop.HILLS, 400f, 10, 10f, true, 30f, 1f, 2, 1, 400f, 8, 12f, 1),
        CastlesLevel(2, CastlesBackdrop.HILLS, 500f, 12, 10f, true, 30f, 1f, 2, 1, 500f, 10, 12f, 1),
        CastlesLevel(3, CastlesBackdrop.HILLS, 550f, 16, 9f, true, 30f, 1f, 3, 1, 550f, 12, 11f, 2, CastlesBossType.DRAGON, 320f, 42_000L),
        CastlesLevel(4, CastlesBackdrop.PLAINS, 650f, 14, 9f, true, 30f, 1f, 3, 1, 650f, 14, 11f, 2),
        CastlesLevel(5, CastlesBackdrop.PLAINS, 700f, 16, 9f, true, 35f, 1f, 3, 1, 700f, 16, 10f, 2),
        CastlesLevel(6, CastlesBackdrop.PLAINS, 800f, 18, 8f, true, 35f, 1f, 4, 1, 800f, 18, 10f, 3),
        CastlesLevel(7, CastlesBackdrop.DESERT, 850f, 15, 8f, true, 35f, 0.9f, 4, 1, 850f, 20, 9f, 3, CastlesBossType.MOLE, 420f, 46_000L),
        CastlesLevel(8, CastlesBackdrop.DESERT, 950f, 17, 8f, true, 40f, 0.9f, 4, 2, 950f, 22, 9f, 3),
        CastlesLevel(9, CastlesBackdrop.DESERT, 1_050f, 19, 8f, true, 40f, 0.9f, 4, 2, 1_050f, 24, 8f, 3),
        CastlesLevel(10, CastlesBackdrop.WATERFALL, 1_150f, 20, 7f, true, 40f, 0.8f, 4, 2, 1_150f, 26, 8f, 4, CastlesBossType.TROJAN, 520f, 50_000L),
        CastlesLevel(11, CastlesBackdrop.WATERFALL, 1_250f, 22, 7f, true, 45f, 0.8f, 4, 2, 1_250f, 28, 8f, 4),
        CastlesLevel(12, CastlesBackdrop.WATERFALL, 1_350f, 24, 7f, true, 45f, 0.8f, 4, 2, 1_350f, 30, 7f, 4),
        CastlesLevel(13, CastlesBackdrop.MOUNTAIN, 1_450f, 22, 7f, true, 45f, 0.7f, 4, 2, 1_450f, 32, 7f, 4, CastlesBossType.IMPS, 560f, 52_000L),
        CastlesLevel(14, CastlesBackdrop.MOUNTAIN, 1_550f, 24, 6f, true, 50f, 0.7f, 4, 2, 1_550f, 34, 7f, 4),
        CastlesLevel(15, CastlesBackdrop.MOUNTAIN, 1_650f, 26, 6f, true, 50f, 0.7f, 4, 2, 1_650f, 36, 6f, 4),
        CastlesLevel(16, CastlesBackdrop.VOLCANO, 1_800f, 28, 6f, true, 55f, 0.6f, 4, 2, 1_800f, 40, 6f, 4, CastlesBossType.SUPER_BOSS, 900f, 56_000L),
    )

    fun levelFor(number: Int): CastlesLevel = levels[number.coerceIn(1, levels.size) - 1]

    // ------------------------------------------------------------ ballistics --

    fun launchSpeed(length: Float): Float =
        (length * 1.85f).coerceIn(CASTLES_MIN_SPEED, CASTLES_MAX_SPEED)

    fun launchVelocity(dragX: Float, dragY: Float): Pair<Float, Float> {
        val length = sqrt(dragX * dragX + dragY * dragY)
        if (length < 0.001f) return 0f to 0f
        val speed = launchSpeed(length)
        return (dragX / length * speed) to (dragY / length * speed * 1.25f)
    }

    fun splashFactor(distance: Float, size: Float): Float {
        if (distance < size / 4f) return 1f
        val raw = (size * 0.75f - (distance - size / 4f)) / (size * 1.5f)
        return raw.coerceIn(0f, 1f)
    }

    fun splashDamage(damage: Float, armor: Float, distance: Float, size: Float): Float =
        max(0f, damage * splashFactor(distance, size) * ((100f - armor) / 100f))

    // ---------------------------------------------------------------- score --

    fun scoreParts(timeSeconds: Float, kills: Int, fired: Int, goldAcquired: Int, hp: Float, maxHp: Float): CastlesScoreParts {
        val time = if (timeSeconds <= 180f) 250 else ((max(360f - timeSeconds, 0f) / 180f) * 250f).toInt()
        var offense = if (fired > 0) min(400, (kills.toFloat() / fired * 325f).toInt()) else 0
        var defense = (hp / maxHp * 400f).toInt()
        val goldTerm = min(200, (goldAcquired * 3.5f).toInt())
        offense += goldTerm / 2
        defense += (goldTerm + 1) / 2
        return CastlesScoreParts(time, offense, defense)
    }

    fun score(timeSeconds: Float, kills: Int, fired: Int, goldAcquired: Int, hp: Float, maxHp: Float): Int =
        scoreParts(timeSeconds, kills, fired, goldAcquired, hp, maxHp).total

    fun isGoldMedal(score: Int): Boolean = score >= 1000

    // ------------------------------------------------------------- progress --

    fun playerStatsFor(level: CastlesLevel, progress: CastlesProgress): CastlesPlayerStats {
        val skills = progress.skills
        var maxHealth = level.maxHealth
        var cannonDamage = 100f
        var cannonCooldown = 3.5f
        var snipeDamage = level.snipeDamage
        var snipeFrequency = level.snipeFrequency
        var unitDamage = 0f
        var unitArmor = 0f
        var armySize = 4
        var goldInterval = level.goldInterval
        if (skills.offense > 0) {
            unitDamage += 1f
            if (skills.offense > 1) unitArmor += 8f
            if (skills.offense > 2) armySize += 1
        }
        if (skills.defense > 0) {
            cannonDamage += 10f
            if (skills.defense > 1) cannonCooldown -= 0.75f
            if (skills.defense > 2) maxHealth += 200f
        }
        if (skills.utility > 0) {
            snipeDamage += 5f
            if (skills.utility > 1) snipeFrequency -= 0.35f
            if (skills.utility > 2) goldInterval -= 5f
        }
        return CastlesPlayerStats(
            maxHealth = maxHealth,
            startingGold = level.startingGold + progress.goldMedals(),
            goldInterval = max(1f, goldInterval),
            snipeEnabled = level.snipeEnabled,
            snipeDamage = snipeDamage,
            snipeFrequency = max(0.2f, snipeFrequency),
            availableUnits = level.availableUnits,
            cannons = level.cannons,
            cannonDamage = cannonDamage,
            cannonCooldown = max(0.5f, cannonCooldown),
            unitDamage = unitDamage,
            unitArmor = unitArmor,
            armySize = armySize,
        )
    }

    /** First completion pays 1 skill point (levels 1–12) or 2 (13–16). */
    fun recordCompletion(progress: CastlesProgress, level: Int, result: Int): CastlesProgress {
        val first = !progress.scores.containsKey(level)
        val scores = progress.scores.toMutableMap()
        scores[level] = max(scores[level] ?: 0, result)
        val points = if (first) (if (level >= 13) 2 else 1) else 0
        return progress.copy(
            scores = scores,
            unlocked = max(progress.unlocked, min(levels.size, level + 1)),
            skills = progress.skills.copy(unusedPoints = progress.skills.unusedPoints + points),
        )
    }

    /** Branch 0 = offense, 1 = defense, 2 = utility. Tier costs 1/2/3. */
    fun skillUpgrade(progress: CastlesProgress, branch: Int): CastlesProgress {
        val skills = progress.skills
        val stat = when (branch) {
            0 -> skills.offense
            1 -> skills.defense
            else -> skills.utility
        }
        if (stat >= 3 || skills.unusedPoints <= stat) return progress
        val next = stat + 1
        val updated = when (branch) {
            0 -> skills.copy(offense = next, unusedPoints = skills.unusedPoints - next)
            1 -> skills.copy(defense = next, unusedPoints = skills.unusedPoints - next)
            else -> skills.copy(utility = next, unusedPoints = skills.unusedPoints - next)
        }
        return progress.copy(skills = updated)
    }

    fun encodeProgress(progress: CastlesProgress): String {
        val scores = progress.scores.entries.sortedBy { it.key }.joinToString("/") { "${it.key}:${it.value}" }
        val s = progress.skills
        return listOf("cc1", progress.unlocked, s.offense, s.defense, s.utility, s.unusedPoints, scores).joinToString(";")
    }

    fun decodeProgress(blob: String): CastlesProgress? = try {
        val p = blob.split(";")
        if (p.size < 6 || p[0] != "cc1") {
            null
        } else {
            val scores = if (p.size < 7 || p[6].isEmpty()) emptyMap() else p[6].split("/").associate {
                val f = it.split(":")
                f[0].toInt() to f[1].toInt()
            }
            CastlesProgress(
                scores = scores,
                unlocked = p[1].toInt(),
                skills = CastlesSkillTree(p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt()),
            )
        }
    } catch (_: Exception) {
        null
    }

    // ----------------------------------------------------------------- new --

    fun newBattle(levelNumber: Int, progress: CastlesProgress, seed: Int): CastlesBattleState {
        val level = levelFor(levelNumber)
        val stats = playerStatsFor(level, progress)
        val enemyStats = CastlesPlayerStats(
            maxHealth = level.enemyMaxHealth,
            startingGold = level.enemyGold,
            goldInterval = level.enemyGoldInterval,
            snipeEnabled = false,
            snipeDamage = 0f,
            snipeFrequency = 1f,
            availableUnits = level.enemyAvailableUnits,
            cannons = 1,
            cannonDamage = 40f + levelNumber * 4f,
            cannonCooldown = max(3f, 5.5f - levelNumber * 0.1f),
            unitDamage = (levelNumber / 4).toFloat(),
            unitArmor = (levelNumber / 8).toFloat(),
            armySize = 4,
        )
        return CastlesBattleState(
            level = levelNumber,
            seed = seed,
            rng = seed,
            stats = stats,
            enemyStats = enemyStats,
            humanHp = stats.maxHealth,
            enemyHp = enemyStats.maxHealth,
            gold = stats.startingGold,
            enemyGold = enemyStats.startingGold,
            goldClockMs = 0L,
            enemyGoldClockMs = 0L,
            units = emptyList(),
            balls = emptyList(),
            coins = emptyList(),
            armies = emptyList(),
            buyCooldowns = CastlesUnitKind.entries.associateWith { 0L },
            bossTimerMs = level.bossDelayMs,
        )
    }

    // -------------------------------------------------------------- actions --

    fun buy(state: CastlesBattleState, kind: CastlesUnitKind): CastlesBattleState {
        if (state.over) return state.copy(events = listOf(CastlesEvent.INVALID))
        if (state.stats.availableUnits < kind.slot) return state.copy(events = listOf(CastlesEvent.INVALID))
        if (state.gold < kind.cost) return state.copy(events = listOf(CastlesEvent.INVALID))
        if ((state.buyCooldowns[kind] ?: 0L) > 0L) return state.copy(events = listOf(CastlesEvent.INVALID))
        val army = CastlesArmy(state.nextId, CastlesSide.HUMAN, kind, state.stats.armySize)
        return state.copy(
            gold = state.gold - kind.cost,
            armies = state.armies + army,
            buyCooldowns = state.buyCooldowns + (kind to CASTLES_BUY_COOLDOWN_MS),
            nextId = state.nextId + 1,
            events = listOf(CastlesEvent.BUY),
        )
    }

    fun heal(state: CastlesBattleState): CastlesBattleState {
        if (state.over || state.healCooldownMs > 0L || state.gold < CASTLES_HEAL_COST) {
            return state.copy(events = listOf(CastlesEvent.INVALID))
        }
        val healed = min(state.stats.maxHealth, state.humanHp + state.stats.maxHealth * 0.25f)
        return state.copy(
            gold = state.gold - CASTLES_HEAL_COST,
            humanHp = healed,
            healCooldownMs = CASTLES_HEAL_COOLDOWN_MS,
            events = listOf(CastlesEvent.HEAL),
        )
    }

    /** Fires the player cannon; drag is relative to the cannon barrel. */
    fun fireCannon(state: CastlesBattleState, dragX: Float, dragY: Float): CastlesBattleState {
        if (state.over) return state
        val length = sqrt(dragX * dragX + dragY * dragY)
        if (length < 10f) return state.copy(events = listOf(CastlesEvent.INVALID))
        if (state.cannonCooldownMs > 0L) return state.copy(events = listOf(CastlesEvent.INVALID))
        val (vx, vy) = launchVelocity(dragX, dragY)
        val ball = CastlesBall(state.nextId, CastlesSide.HUMAN, 120f, 150f, vx, vy, state.stats.cannonDamage)
        return state.copy(
            balls = state.balls + ball,
            cannonCooldownMs = (state.stats.cannonCooldown * 1000f).toLong(),
            cannonsFired = state.cannonsFired + 1,
            nextId = state.nextId + 1,
            events = listOf(CastlesEvent.CANNON_FIRE),
        )
    }

    fun collectCoins(state: CastlesBattleState, x: Float, y: Float, radius: Float = 34f): CastlesBattleState {
        if (state.over) return state
        val collected = state.coins.filter { abs(it.x - x) <= radius }
        if (collected.isEmpty()) return state
        val value = collected.sumOf { it.value }
        return state.copy(
            coins = state.coins - collected.toSet(),
            gold = min(CASTLES_GOLD_CAP, state.gold + value),
            goldAcquired = state.goldAcquired + value,
            events = listOf(CastlesEvent.COIN),
        )
    }

    /**
     * Maps a battle-canvas x to a world x for the live camera. The canvas
     * letterboxes the 480x200 view, so the scale/offset must be recomputed
     * from the current camera instead of a projection captured on a pan.
     */
    fun screenToWorldX(screenX: Float, viewWidth: Float, viewHeight: Float, camera: Float): Float {
        val scale = min(viewWidth / CASTLES_VIEW_W, viewHeight / CASTLES_VIEW_H)
        val ox = (viewWidth - CASTLES_VIEW_W * scale) / 2f
        return camera + (screenX - ox) / scale
    }

    // ------------------------------------------------------------------ step --

    fun step(state: CastlesBattleState, dtMs: Long): CastlesBattleState {
        if (state.over || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<CastlesEvent>()
        val rng = CastlesRandom(state.rng)
        var humanHp = state.humanHp
        var enemyHp = state.enemyHp
        var gold = state.gold
        var enemyGold = state.enemyGold
        var goldClock = state.goldClockMs + dtMs
        var enemyGoldClock = state.enemyGoldClockMs + dtMs
        var buyCooldowns = state.buyCooldowns.mapValues { max(0L, it.value - dtMs) }
        var healCooldown = max(0L, state.healCooldownMs - dtMs)
        var cannonCooldown = max(0L, state.cannonCooldownMs - dtMs)
        var snipeClock = state.snipeClockMs + dtMs
        var enemySpawnClock = state.enemySpawnClockMs + dtMs
        var enemyCannonClock = state.enemyCannonClockMs + dtMs
        var bossTimer = state.bossTimerMs
        var bossSpawned = state.bossSpawned
        var unitsKilled = state.unitsKilled
        var goldAcquired = state.goldAcquired
        var nextId = state.nextId
        val elapsed = state.elapsedMs + dtMs
        var units = state.units.toMutableList()
        var balls = state.balls.toMutableList()
        var coins = state.coins.toMutableList()
        var armies = state.armies.toMutableList()
        var over = false
        var won = false

        // Gold ticks.
        while (goldClock >= (state.stats.goldInterval * 1000f).toLong()) {
            goldClock -= (state.stats.goldInterval * 1000f).toLong().coerceAtLeast(1L)
            gold = min(CASTLES_GOLD_CAP, gold + 1)
            goldAcquired++
        }
        while (enemyGoldClock >= (state.enemyStats.goldInterval * 1000f).toLong()) {
            enemyGoldClock -= (state.enemyStats.goldInterval * 1000f).toLong().coerceAtLeast(1L)
            enemyGold = min(CASTLES_GOLD_CAP, enemyGold + 1)
        }

        // Armies drip units.
        val keptArmies = mutableListOf<CastlesArmy>()
        for (army in armies) {
            val stats = army.kind.stats()
            val armor = if (army.side == CastlesSide.HUMAN) state.stats.unitArmor else state.enemyStats.unitArmor
            val damage = if (army.side == CastlesSide.HUMAN) state.stats.unitDamage else state.enemyStats.unitDamage
            var remaining = army.remaining
            var clock = army.clockMs + dtMs
            while (remaining > 0 && clock >= army.kind.frequencyMs) {
                clock -= army.kind.frequencyMs
                remaining--
                val x = if (army.side == CastlesSide.HUMAN) CASTLES_HUMAN_GATE else CASTLES_ENEMY_GATE
                units += CastlesUnit(
                    id = nextId++,
                    side = army.side,
                    kind = army.kind,
                    x = x,
                    lane = nextId % 3,
                    hp = CASTLES_UNIT_HP,
                    armor = stats.armor + armor,
                    castleDamage = stats.castleDamage + damage,
                )
                events += CastlesEvent.SPAWN
            }
            if (remaining > 0) keptArmies += army.copy(remaining = remaining, clockMs = clock)
        }
        armies = keptArmies

        // Boss arrives on schedule.
        if (bossTimer > 0L) {
            bossTimer -= dtMs
            if (bossTimer <= 0L && !bossSpawned) {
                val level = levelFor(state.level)
                if (level.boss != null) {
                    bossSpawned = true
                    units += CastlesUnit(
                        id = nextId++,
                        side = CastlesSide.ENEMY,
                        kind = CastlesUnitKind.KNIGHT,
                        x = CASTLES_ENEMY_GATE,
                        lane = 1,
                        hp = level.bossHealth,
                        armor = 20f,
                        castleDamage = 40f,
                        boss = level.boss,
                    )
                    events += CastlesEvent.BOSS_ALERT
                }
            }
        }

        // Enemy AI: buy the strongest affordable army.
        val enemySpawnInterval = 2600L - state.level * 40L
        if (enemySpawnClock >= enemySpawnInterval) {
            enemySpawnClock = 0L
            val options = CastlesUnitKind.entries.filter { it.slot <= state.enemyStats.availableUnits && enemyGold >= it.cost }
            val pick = options.lastOrNull()
            if (pick != null) {
                enemyGold -= pick.cost
                units += CastlesUnit(
                    id = nextId++,
                    side = CastlesSide.ENEMY,
                    kind = pick,
                    x = CASTLES_ENEMY_GATE,
                    lane = nextId % 3,
                    hp = CASTLES_UNIT_HP,
                    armor = pick.stats().armor + state.enemyStats.unitArmor,
                    castleDamage = pick.stats().castleDamage + state.enemyStats.unitDamage,
                )
                events += CastlesEvent.SPAWN
            }
        }

        // Sniper arrow (player side).
        if (state.stats.snipeEnabled && snipeClock >= (state.stats.snipeFrequency * 1000f).toLong()) {
            snipeClock = 0L
            val target = units.filter { it.side == CastlesSide.ENEMY && it.boss == null && it.x >= CASTLES_ENEMY_GATE - 180f }
                .minByOrNull { it.hp }
            if (target != null) {
                val index = units.indexOf(target)
                val damaged = units[index].copy(hp = units[index].hp - splashDamage(state.stats.snipeDamage, units[index].armor, 0f, 10f))
                units[index] = damaged
                events += CastlesEvent.SNIPE
            }
        }

        // Enemy cannon.
        val enemyCannonInterval = max(2500L, 5000L - state.level * 100L)
        if (enemyCannonClock >= enemyCannonInterval) {
            enemyCannonClock = 0L
            val targets = units.filter { it.side == CastlesSide.HUMAN }
            if (targets.isNotEmpty()) {
                val meanX = targets.map { it.x }.average().toFloat()
                val spread = rng.next(80) - 40
                val targeting = (meanX + spread).coerceIn(60f, 420f)
                val dragX = targeting - 800f
                val (vx, vy) = launchVelocity(dragX, -180f)
                balls += CastlesBall(nextId++, CastlesSide.ENEMY, 800f, 150f, vx, vy, state.enemyStats.cannonDamage)
                events += CastlesEvent.ENEMY_CANNON
            }
        }

        // Unit movement and combat.
        val updated = mutableListOf<CastlesUnit>()
        for (unit in units) {
            var u = unit.copy(attackCooldownMs = max(0L, unit.attackCooldownMs - dtMs))
            val stats = u.kind.stats()
            val enemySide = if (u.side == CastlesSide.HUMAN) CastlesSide.ENEMY else CastlesSide.HUMAN
            val target = units.filter { it.side == enemySide && it.id != u.id }
                .minByOrNull { abs(it.x - u.x) }
            val inRange = target != null && abs(target.x - u.x) <= stats.range
            if (inRange && target != null) {
                if (u.attackCooldownMs <= 0L) {
                    val index = units.indexOfFirst { it.id == target.id }
                    if (index >= 0) {
                        val damage = splashDamage((if (u.boss != null) 50f else stats.castleDamage + if (u.side == CastlesSide.HUMAN) state.stats.unitDamage else state.enemyStats.unitDamage), units[index].armor, 0f, stats.splashRadius)
                        units[index] = units[index].copy(hp = units[index].hp - damage)
                    }
                    u = u.copy(attackCooldownMs = stats.attackCooldownMs)
                }
            } else {
                val dir = if (u.side == CastlesSide.HUMAN) 1f else -1f
                u = u.copy(x = u.x + dir * stats.speed * dtMs / 1000f)
                val gate = if (u.side == CastlesSide.HUMAN) CASTLES_ENEMY_GATE else CASTLES_HUMAN_GATE
                val atGate = if (u.side == CastlesSide.HUMAN) u.x >= gate else u.x <= gate
                if (atGate) {
                    if (u.attackCooldownMs <= 0L) {
                        u = u.copy(attackCooldownMs = 1000L)
                        val damage = if (u.boss != null) 40f else u.castleDamage
                        if (u.side == CastlesSide.HUMAN) {
                            enemyHp = max(0f, enemyHp - damage)
                            events += CastlesEvent.CASTLE_HIT
                        } else {
                            humanHp = max(0f, humanHp - damage)
                            events += CastlesEvent.CASTLE_HIT
                        }
                    }
                }
            }
            updated += u
        }
        units = updated

        // Deaths.
        val dead = units.filter { it.hp <= 0f }
        if (dead.isNotEmpty()) {
            for (u in dead) {
                if (u.side == CastlesSide.ENEMY) {
                    unitsKilled += if (u.boss != null) 10 else 1
                    val reward = if (u.boss != null) 25 else u.kind.stats().gold
                    gold = min(CASTLES_GOLD_CAP, gold + reward)
                    goldAcquired += reward
                } else {
                    enemyGold = min(CASTLES_GOLD_CAP, enemyGold + u.kind.stats().gold)
                }
                coins += CastlesCoin(nextId++, u.x, if (u.boss != null) 25 else u.kind.stats().gold)
                events += CastlesEvent.DEATH
            }
            units = units.filter { it.hp > 0f }.toMutableList()
        }

        // Coins age out.
        coins = coins.mapNotNull { coin ->
            val age = coin.ageMs + dtMs
            if (age >= 8_000L) null else coin.copy(ageMs = age)
        }.toMutableList()

        // Projectiles.
        val liveBalls = mutableListOf<CastlesBall>()
        for (ball in balls) {
            var b = ball
            val dt = dtMs / 1000f
            b = b.copy(vy = b.vy + CASTLES_GRAVITY * dt)
            b = b.copy(x = b.x + b.vx * dt, y = b.y + b.vy * dt)
            if (b.y >= CASTLES_GROUND_Y) {
                val side = if (b.side == CastlesSide.HUMAN) CastlesSide.ENEMY else CastlesSide.HUMAN
                for (i in units.indices) {
                    if (units[i].side != side) continue
                    val distance = abs(units[i].x - b.x)
                    if (distance > 60f) continue
                    val damage = splashDamage(b.damage, units[i].armor, distance, 60f)
                    if (damage > 0f) units[i] = units[i].copy(hp = units[i].hp - damage)
                }
                events += CastlesEvent.CANNON_IMPACT
            } else {
                liveBalls += b
            }
        }
        balls = liveBalls

        // Resolution.
        if (enemyHp <= 0f) {
            over = true
            won = true
            events += CastlesEvent.WIN
        } else if (humanHp <= 0f) {
            over = true
            events += CastlesEvent.LOSE
        }

        return state.copy(
            rng = rng.state,
            humanHp = humanHp,
            enemyHp = enemyHp,
            gold = gold,
            enemyGold = enemyGold,
            goldClockMs = goldClock,
            enemyGoldClockMs = enemyGoldClock,
            units = units,
            balls = balls,
            coins = coins,
            armies = armies,
            buyCooldowns = buyCooldowns,
            healCooldownMs = healCooldown,
            cannonCooldownMs = cannonCooldown,
            snipeClockMs = snipeClock,
            enemySpawnClockMs = enemySpawnClock,
            enemyCannonClockMs = enemyCannonClock,
            elapsedMs = elapsed,
            unitsKilled = unitsKilled,
            goldAcquired = goldAcquired,
            bossSpawned = bossSpawned,
            bossTimerMs = bossTimer,
            over = over,
            won = won,
            nextId = nextId,
            events = events,
        )
    }

    fun battleScore(state: CastlesBattleState): Int = score(
        timeSeconds = state.elapsedMs / 1000f,
        kills = state.unitsKilled,
        fired = state.cannonsFired,
        goldAcquired = state.goldAcquired,
        hp = state.humanHp,
        maxHp = state.stats.maxHealth,
    )
}
