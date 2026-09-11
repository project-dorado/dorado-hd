package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bees!!! — clean-room engine for the Zune HD hive harvester.
 *
 * Behaviour re-derived from docs/apps/bees.md; no Microsoft code, level data
 * or assets. The board is a 5x6 node graph of a hive and six-colour flowers;
 * bees harvest petals into the hive tank, which converts pollen to honey that
 * banks into theme jars.
 */
const val BEES_POLLEN_PER_HONEY = 25
const val BEES_POLLEN_RATE = 32f
const val BEES_BANK_BONUS = 0.15f
const val BEES_HARVEST_SCORE = 15
const val BEES_MAX_PETALS = 4
const val BEES_GROW_MS = 21_000L
const val BEES_WILT_PER_PETAL_MS = 15_000L
const val BEES_TIME_LIMIT_MS = 240_000L
const val BEES_TIME_WARNING_MS = 30_000L
const val BEES_DRAIN_PER_SEC = 8.4f
const val BEES_SPAWN_MIN_MS = 5_000L
const val BEES_SPAWN_MAX_MS = 20_000L
const val BEES_JAR_CAP = 99

class BeesRandom(seed: Int) {
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

enum class BeesTheme(val label: String, val jarCost: Float, val jarValue: Int) {
    GRASS("grass", 80f, 150),
    SWAMP("swamp", 100f, 200),
    DESERT("desert", 150f, 250),
    SNOW("snow", 150f, 300),
    SPACE("space", 200f, 500),
}

enum class BeesMode(val label: String, val canLose: Boolean) {
    ADVENTURE("adventure", false),
    CHALLENGE("challenge", false),
    BATTLE("battle", true),
    POLLEN_BATTLE("pollen battle", true),
}

enum class BeesKind(
    val label: String,
    val harvestMs: Long,
    val strength: Int,
    val orbitDegPerSec: Float,
    val moveSpeed: Float,
    val damage: Int,
) {
    NORMAL("normal", 7_000L, 2, 130f, 100f, 5),
    POLLINATOR("pollinator", 6_000L, 2, 100f, 80f, 5),
    WORKER("worker", 1_800L, 3, 130f, 100f, 2),
    NINJA("ninja", 11_000L, 1, 150f, 120f, 50),
}

enum class BeesNodeType { HIVE, FLOWER }

enum class BeesFlowerState { GROWING, BLOOM, WILTED }

enum class BeesBeeState { IDLE, FLYING, HARVESTING, RETURNING }

enum class BeesBaddieKind(val label: String, val maxHp: Float, val value: Int, val speed: Float) {
    BROWN_BEAR("brown bear", 15f, 100, 18f),
    BLACK_BEAR("black bear", 20f, 250, 18f),
    POLAR_BEAR("polar bear", 15f, 200, 20f),
    MOON_BEAR("moon bear", 20f, 250, 16f),
    BUNNY("snow bunny", 8f, 75, 40f),
    GOPHER("gopher", 10f, 150, 0f),
    HUMMINGBIRD("hummingbird", 1f, 500, 120f),
}

data class BeesFlower(
    val color: Int,
    val petals: Int = BEES_MAX_PETALS,
    val state: BeesFlowerState = BeesFlowerState.BLOOM,
    val timerMs: Long = 0L,
    val wiltMs: Long = BEES_WILT_PER_PETAL_MS * BEES_MAX_PETALS,
) {
    val value: Int get() = 15 + 10 * color
}

data class BeesNode(
    val id: Int,
    val type: BeesNodeType,
    val row: Int,
    val col: Int,
    val x: Float,
    val y: Float,
    val flower: BeesFlower? = null,
)

data class BeesPath(val a: Int, val b: Int)

data class BeesBee(
    val id: Int,
    val kind: BeesKind,
    val x: Float,
    val y: Float,
    val state: BeesBeeState = BeesBeeState.IDLE,
    val path: List<Int> = emptyList(),
    val target: Int = -1,
    val carried: Float = 0f,
    val carriedPetals: Int = 0,
    val harvestMs: Long = 0L,
    val orbitDeg: Float = 0f,
)

data class BeesBaddie(
    val id: Int,
    val kind: BeesBaddieKind,
    val x: Float,
    val y: Float,
    val hp: Float,
    val targetNode: Int,
    val warningMs: Long = 0L,
    val danceMs: Long = 0L,
    val state: Int = 0,
    val phaseMs: Long = 0L,
    val combo: Int = 1,
)

data class BeesTank(val honey: Int = 0, val pollen: Float = 0f, val convertBuffer: Float = 0f)

data class BeesLevel(
    val id: Int,
    val theme: BeesTheme,
    val indexInTheme: Int,
    val baddieBoost: Int,
    val preBloomed: Int,
)

enum class BeesEvent {
    GATHER, DEPOSIT, EXPLODE, JAR, BANK, BADDIE_DOWN, BADDIE_SPAWN,
    TIME_WARNING, WIN, LOSE, SPAWN,
}

data class BeesState(
    val levelId: Int,
    val theme: BeesTheme,
    val mode: BeesMode,
    val seed: Int,
    val rng: Int,
    val nodes: List<BeesNode>,
    val paths: List<BeesPath>,
    val bees: List<BeesBee> = emptyList(),
    val tank: BeesTank = BeesTank(),
    val honeyJars: Int = 0,
    val jarProgress: Float = 0f,
    val score: Int = 0,
    val streak: Int = 0,
    val combo: Int = 1,
    val baddies: List<BeesBaddie> = emptyList(),
    val elapsedMs: Long = 0L,
    val spawnClockMs: Long = 0L,
    val baddieClockMs: Long = 0L,
    val lastDepositMs: Long = -1L,
    val hiveUpgrade: Int = 0,
    val unlocked: Set<BeesKind> = setOf(BeesKind.NORMAL, BeesKind.POLLINATOR),
    val hiveHits: Int = 0,
    val warningShown: Boolean = false,
    val over: Boolean = false,
    val won: Boolean = false,
    val nextId: Int = 1,
    val events: List<BeesEvent> = emptyList(),
)

object BeesEngine {

    val levels: List<BeesLevel> = (1..25).map { id ->
        val theme = BeesTheme.entries[(id - 1) / 5]
        val index = (id - 1) % 5 + 1
        BeesLevel(
            id = id,
            theme = theme,
            indexInTheme = index,
            baddieBoost = (id - 1) / 5,
            preBloomed = 2 + (index % 2),
        )
    }

    fun levelFor(id: Int): BeesLevel = levels[id.coerceIn(1, levels.size) - 1]

    fun nodeX(col: Int): Float = 36f + col * 50f
    fun nodeY(row: Int): Float = 96f + row * 56f

    /** Authored per-theme flower positions (row, col, colour). */
    private val THEME_FLOWERS: Map<BeesTheme, List<Triple<Int, Int, Int>>> = mapOf(
        BeesTheme.GRASS to listOf(
            Triple(0, 0, 0), Triple(0, 2, 1), Triple(0, 4, 2), Triple(2, 0, 3),
            Triple(2, 4, 0), Triple(3, 1, 4), Triple(4, 3, 5), Triple(1, 3, 1),
        ),
        BeesTheme.SWAMP to listOf(
            Triple(0, 1, 1), Triple(0, 3, 0), Triple(1, 0, 2), Triple(2, 2, 3),
            Triple(2, 4, 1), Triple(3, 0, 5), Triple(4, 2, 4), Triple(4, 4, 0),
        ),
        BeesTheme.DESERT to listOf(
            Triple(0, 0, 2), Triple(0, 4, 3), Triple(1, 1, 0), Triple(1, 3, 1),
            Triple(2, 0, 4), Triple(3, 2, 5), Triple(3, 4, 2), Triple(4, 1, 3),
        ),
        BeesTheme.SNOW to listOf(
            Triple(0, 2, 5), Triple(1, 0, 0), Triple(1, 4, 1), Triple(2, 1, 2),
            Triple(2, 3, 4), Triple(3, 0, 3), Triple(4, 2, 0), Triple(4, 4, 5),
        ),
        BeesTheme.SPACE to listOf(
            Triple(0, 1, 4), Triple(0, 3, 5), Triple(1, 2, 0), Triple(2, 0, 1),
            Triple(2, 4, 2), Triple(3, 1, 3), Triple(3, 3, 4), Triple(4, 2, 5),
        ),
    )

    private val THEME_CROSS: Map<BeesTheme, List<Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>>> = mapOf(
        BeesTheme.GRASS to listOf(Triple(0, 0, 0) to Triple(0, 2, 1), Triple(2, 0, 3) to Triple(4, 3, 5)),
        BeesTheme.SWAMP to listOf(Triple(0, 1, 1) to Triple(0, 3, 0), Triple(1, 0, 2) to Triple(3, 0, 5)),
        BeesTheme.DESERT to listOf(Triple(0, 0, 2) to Triple(0, 4, 3), Triple(2, 0, 4) to Triple(3, 4, 2)),
        BeesTheme.SNOW to listOf(Triple(1, 0, 0) to Triple(2, 1, 2), Triple(2, 3, 4) to Triple(4, 4, 5)),
        BeesTheme.SPACE to listOf(Triple(0, 1, 4) to Triple(1, 2, 0), Triple(3, 1, 3) to Triple(3, 3, 4)),
    )

    /**
     * Builds the level's node graph: a five-cell spine from the hive up the
     * middle column, each flower attached to its nearest spine cell, plus the
     * theme's authored cross-links.
     */
    fun buildBoard(level: BeesLevel): Pair<List<BeesNode>, List<BeesPath>> {
        val flowers = THEME_FLOWERS.getValue(level.theme)
        val active = 6 + (level.indexInTheme % 3)
        val rotated = flowers.mapIndexed { index, f ->
            Triple(f.first, f.second, (f.third + level.indexInTheme - 1) % 6)
        }
        val nodes = mutableListOf<BeesNode>()
        val hive = BeesNode(0, BeesNodeType.HIVE, 5, 2, nodeX(2), nodeY(5))
        nodes += hive
        val spine = listOf(4 to 2, 3 to 2, 2 to 2, 1 to 2, 0 to 2)
        val spineIds = mutableMapOf<Pair<Int, Int>, Int>()
        for ((row, col) in spine) {
            val id = nodes.size
            spineIds[row to col] = id
            nodes += BeesNode(id, BeesNodeType.HIVE, row, col, nodeX(col), nodeY(row))
        }
        val paths = mutableListOf<BeesPath>()
        paths += BeesPath(0, spineIds.getValue(4 to 2))
        for (i in 0 until spine.size - 1) {
            paths += BeesPath(spineIds.getValue(spine[i]), spineIds.getValue(spine[i + 1]))
        }
        val chosen = rotated.take(active)
        val flowerIds = mutableListOf<Int>()
        for (flower in chosen) {
            val id = nodes.size
            flowerIds += id
            nodes += BeesNode(
                id = id,
                type = BeesNodeType.FLOWER,
                row = flower.first,
                col = flower.second,
                x = nodeX(flower.second),
                y = nodeY(flower.first),
                flower = BeesFlower(color = flower.third),
            )
            val anchor = spine.minByOrNull { (r, c) ->
                abs(r - flower.first) + abs(c - flower.second)
            } ?: (2 to 2)
            paths += BeesPath(spineIds.getValue(anchor), id)
        }
        for ((a, b) in THEME_CROSS.getValue(level.theme)) {
            val idA = chosen.indexOfFirst { it.first == a.first && it.second == a.second }
            val idB = chosen.indexOfFirst { it.first == b.first && it.second == b.second }
            if (idA >= 0 && idB >= 0) paths += BeesPath(flowerIds[idA], flowerIds[idB])
        }
        return nodes to paths
    }

    fun newGame(levelId: Int, mode: BeesMode, seed: Int, upgrades: BeesUpgrades = BeesUpgrades()): BeesState {
        val level = levelFor(levelId)
        val (nodes, paths) = buildBoard(level)
        val rng = BeesRandom(seed)
        val withPetals = nodes.map { node ->
            if (node.type == BeesNodeType.FLOWER && node.flower != null) {
                val index = node.id
                if (index % 5 < level.preBloomed) node
                else node.copy(flower = node.flower.copy(state = BeesFlowerState.GROWING, petals = 0, timerMs = BEES_GROW_MS))
            } else {
                node
            }
        }
        return BeesState(
            levelId = levelId,
            theme = level.theme,
            mode = mode,
            seed = seed,
            rng = rng.state,
            nodes = withPetals,
            paths = paths,
            hiveUpgrade = upgrades.hiveUpgrade,
            unlocked = upgrades.unlocked,
        )
    }

    /**
     * Stable gesture-input identity for a run. [step] rebuilds the node list
     * every tick, so pointer handlers must key on this token or they restart
     * each frame and drop taps.
     */
    fun runToken(state: BeesState): Int = state.seed

    /** Persisted per-theme shop purchases. */
    data class BeesUpgrades(
        val unlocked: Set<BeesKind> = setOf(BeesKind.NORMAL, BeesKind.POLLINATOR),
        val hiveUpgrade: Int = 0,
    )

    /** Campaign persistence: jars per theme, unlocked level and per-level bests. */
    data class BeesProgress(
        val jars: Map<BeesTheme, Int> = emptyMap(),
        val unlockedLevel: Int = 1,
        val best: Map<Int, Int> = emptyMap(),
        val upgrades: BeesUpgrades = BeesUpgrades(),
    ) {
        fun jarsFor(theme: BeesTheme): Int = jars[theme] ?: 0
    }

    /** Folds a finished run into the campaign: bests, jars and level unlock. */
    fun recordResult(progress: BeesProgress, state: BeesState): BeesProgress {
        val best = progress.best.toMutableMap()
        best[state.levelId] = max(best[state.levelId] ?: 0, state.score)
        val jars = progress.jars.toMutableMap()
        jars[state.theme] = (jars[state.theme] ?: 0) + state.honeyJars
        val unlocked = if (state.won && state.levelId < levels.size) max(progress.unlockedLevel, state.levelId + 1) else progress.unlockedLevel
        return progress.copy(best = best, jars = jars, unlockedLevel = unlocked)
    }

    fun encodeProgress(progress: BeesProgress): String {
        val jars = BeesTheme.entries.joinToString("+") { "${it.ordinal}:${progress.jarsFor(it)}" }
        val best = progress.best.entries.sortedBy { it.key }.joinToString("/") { "${it.key}:${it.value}" }
        val unlocked = progress.upgrades.unlocked.joinToString("+") { it.ordinal.toString() }
        return listOf("beesp1", progress.unlockedLevel, jars, best, progress.upgrades.hiveUpgrade, unlocked).joinToString(";")
    }

    fun decodeProgress(blob: String): BeesProgress? = try {
        val p = blob.split(";")
        if (p.size < 6 || p[0] != "beesp1") {
            null
        } else {
            val jars = p[2].split("+").mapNotNull {
                val f = it.split(":")
                BeesTheme.entries.getOrNull(f[0].toInt())?.let { theme -> theme to f[1].toInt() }
            }.toMap()
            val best = if (p[3].isEmpty()) emptyMap() else p[3].split("/").associate {
                val f = it.split(":")
                f[0].toInt() to f[1].toInt()
            }
            val unlocked = if (p[5].isEmpty()) setOf(BeesKind.NORMAL, BeesKind.POLLINATOR) else p[5].split("+").map { BeesKind.entries[it.toInt()] }.toSet()
            BeesProgress(
                jars = jars,
                unlockedLevel = p[1].toInt(),
                best = best,
                upgrades = BeesUpgrades(unlocked, p[4].toInt()),
            )
        }
    } catch (_: Exception) {
        null
    }

    // -------------------------------------------------------------- helpers --

    fun flowerValue(color: Int): Int = 15 + 10 * color

    /** min(streak + 1, hive_upgrade + 2), never below 2. */
    fun beeCap(streak: Int, hiveUpgrade: Int): Int =
        max(2, min(streak + 1, hiveUpgrade + 2))

    fun tankCapacity(hiveUpgrade: Int, extras: Int = 0): Int =
        (3 + hiveUpgrade + extras).coerceIn(3, 10)

    fun bankTotal(tank: BeesTank): Float =
        (tank.honey * BEES_POLLEN_PER_HONEY + tank.pollen) * (1f + BEES_BANK_BONUS)

    /** Converts the banked total into awarded jars for the theme. */
    fun jarsFor(banked: Float, theme: BeesTheme): Int =
        if (banked < theme.jarCost) 0 else floor(banked / theme.jarCost).toInt()

    private fun nodeDistance(a: BeesNode, b: BeesNode): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun pathBetween(paths: List<BeesPath>, from: Int, to: Int): List<Int> {
        if (from == to) return emptyList()
        val adjacency = mutableMapOf<Int, MutableList<Int>>()
        for (p in paths) {
            adjacency.getOrPut(p.a) { mutableListOf() }.add(p.b)
            adjacency.getOrPut(p.b) { mutableListOf() }.add(p.a)
        }
        val prev = mutableMapOf<Int, Int>()
        val seen = mutableSetOf(from)
        val queue = ArrayDeque<Int>()
        queue.addLast(from)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in adjacency[current].orEmpty().sorted()) {
                if (!seen.add(next)) continue
                prev[next] = current
                if (next == to) {
                    val out = mutableListOf<Int>()
                    var step = to
                    while (step != from) {
                        out.add(0, step)
                        step = prev[step] ?: break
                    }
                    return out
                }
                queue.addLast(next)
            }
        }
        return emptyList()
    }

    fun pathTo(state: BeesState, from: Int, to: Int): List<Int> = pathBetween(state.paths, from, to)

    // ----------------------------------------------------------------- shops --

    fun purchaseUnlock(upgrades: BeesUpgrades, kind: BeesKind, jars: Int): Pair<BeesUpgrades, Int> {
        if (kind in upgrades.unlocked) return upgrades to jars
        val cost = when (kind) {
            BeesKind.WORKER -> 8
            BeesKind.NINJA -> 12
            else -> 0
        }
        if (jars < cost) return upgrades to jars
        return upgrades.copy(unlocked = upgrades.unlocked + kind) to (jars - cost)
    }

    fun purchaseHiveUpgrade(upgrades: BeesUpgrades, jars: Int): Pair<BeesUpgrades, Int> {
        if (upgrades.hiveUpgrade >= 4) return upgrades to jars
        val cost = upgrades.hiveUpgrade + 1
        if (jars < cost) return upgrades to jars
        return upgrades.copy(hiveUpgrade = upgrades.hiveUpgrade + 1) to (jars - cost)
    }

    // ---------------------------------------------------------------- actions --

    fun bank(state: BeesState): BeesState {
        if (state.over) return state.copy(events = emptyList())
        val total = bankTotal(state.tank)
        if (total <= 0f) return state.copy(events = emptyList())
        var progress = state.jarProgress + total
        var jars = state.honeyJars
        var gained = 0
        while (progress >= state.theme.jarCost && jars < BEES_JAR_CAP) {
            progress -= state.theme.jarCost
            jars++
            gained++
        }
        return state.copy(
            tank = BeesTank(),
            jarProgress = progress,
            honeyJars = jars,
            score = state.score + state.theme.jarValue * gained,
            events = if (gained > 0) listOf(BeesEvent.JAR) else listOf(BeesEvent.BANK),
        )
    }

    /** Sends every landed bee to the nearest bloom, or a specific one. */
    fun moveBees(state: BeesState, explicit: Map<Int, Int> = emptyMap()): BeesState {
        var next = state
        for (bee in state.bees) {
            if (bee.state != BeesBeeState.IDLE) continue
            val target = explicit[bee.id] ?: nearestBloom(state, bee) ?: continue
            next = sendBee(next, bee.id, target)
        }
        return next
    }

    fun sendBee(state: BeesState, beeId: Int, nodeId: Int): BeesState {
        val index = state.bees.indexOfFirst { it.id == beeId }
        if (index < 0) return state
        val bee = state.bees[index]
        if (bee.state == BeesBeeState.HARVESTING) return state
        val from = nearestNode(state, bee.x, bee.y)
        val path = pathTo(state, from, nodeId)
        if (path.isEmpty() && from != nodeId) return state
        val bees = state.bees.toMutableList()
        bees[index] = bee.copy(state = BeesBeeState.FLYING, path = path, target = nodeId)
        return state.copy(bees = bees)
    }

    fun nearestBloom(state: BeesState, bee: BeesBee): Int? {
        val flowers = state.nodes.filter { it.type == BeesNodeType.FLOWER }
        val bloom = flowers.filter { (it.flower?.petals ?: 0) > 0 }
        val pool = if (bloom.isNotEmpty()) bloom else flowers.filter { bee.kind == BeesKind.POLLINATOR }
        return pool.filter { it.id != nearestNode(state, bee.x, bee.y) }
            .minByOrNull { nodeDistance(it, state.nodes[nearestNode(state, bee.x, bee.y)]) }?.id
    }

    fun nearestNode(state: BeesState, x: Float, y: Float): Int =
        state.nodes.minByOrNull { node ->
            val dx = node.x - x
            val dy = node.y - y
            dx * dx + dy * dy
        }?.id ?: 0

    // ------------------------------------------------------------------ step --

    fun step(state: BeesState, dtMs: Long): BeesState {
        if (state.over || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<BeesEvent>()
        val rng = BeesRandom(state.rng)
        val dt = dtMs / 1000f
        val elapsed = state.elapsedMs + dtMs
        var nodes = state.nodes
        var bees = state.bees.toMutableList()
        var baddies = state.baddies.toMutableList()
        var tank = state.tank
        var score = state.score
        var streak = state.streak
        var combo = state.combo
        var hiveHits = state.hiveHits
        var nextId = state.nextId
        var warningShown = state.warningShown
        var lastDeposit = state.lastDepositMs

        // Flower growth and wilting.
        nodes = nodes.map { node ->
            val flower = node.flower ?: return@map node
            when (flower.state) {
                BeesFlowerState.GROWING -> {
                    val timer = flower.timerMs - dtMs
                    if (timer <= 0L) {
                        node.copy(flower = flower.copy(state = BeesFlowerState.BLOOM, petals = BEES_MAX_PETALS, wiltMs = BEES_WILT_PER_PETAL_MS * BEES_MAX_PETALS, timerMs = 0L))
                    } else {
                        node.copy(flower = flower.copy(timerMs = timer))
                    }
                }
                BeesFlowerState.BLOOM -> {
                    val wilt = flower.wiltMs - dtMs
                    if (wilt <= 0L) {
                        node.copy(flower = flower.copy(state = BeesFlowerState.WILTED, petals = 0, timerMs = BEES_GROW_MS))
                    } else {
                        node.copy(flower = flower.copy(wiltMs = wilt))
                    }
                }
                BeesFlowerState.WILTED -> {
                    val timer = flower.timerMs - dtMs
                    if (timer <= 0L) {
                        node.copy(flower = flower.copy(state = BeesFlowerState.BLOOM, petals = BEES_MAX_PETALS, wiltMs = BEES_WILT_PER_PETAL_MS * BEES_MAX_PETALS, timerMs = 0L))
                    } else {
                        node.copy(flower = flower.copy(timerMs = timer))
                    }
                }
            }
        }

        // Tank conversion: 32 pollen/s, 25 pollen per honey.
        val room = (tankCapacity(state.hiveUpgrade) - tank.honey).coerceAtLeast(0)
        val convertible = if (room > 0) min(tank.pollen, BEES_POLLEN_RATE * dt) else 0f
        var buffer = tank.convertBuffer + convertible
        var honey = tank.honey
        var pollen = tank.pollen - convertible
        val conversions = min(floor(buffer / BEES_POLLEN_PER_HONEY).toInt(), room)
        if (conversions > 0) {
            honey += conversions
            buffer -= conversions * BEES_POLLEN_PER_HONEY
        }
        tank = BeesTank(honey, pollen, buffer)

        // Challenge drain when no deposit has arrived for a while.
        val sinceDeposit = if (lastDeposit < 0L) elapsed else elapsed - lastDeposit
        val drainAllowed = (state.mode == BeesMode.CHALLENGE || state.mode == BeesMode.POLLEN_BATTLE) && sinceDeposit >= 3_000L
        if (drainAllowed) {
            var drained = tank.pollen - BEES_DRAIN_PER_SEC * dt
            var drainedHoney = tank.honey
            while (drained < 0f && drainedHoney > 0) {
                drainedHoney--
                drained += BEES_POLLEN_PER_HONEY
            }
            tank = tank.copy(pollen = max(0f, drained), honey = drainedHoney)
        }

        // Bee cap by mode.
        val cap = when (state.mode) {
            BeesMode.ADVENTURE -> beeCap(streak, state.hiveUpgrade)
            BeesMode.CHALLENGE, BeesMode.POLLEN_BATTLE ->
                max(2, (elapsed / 90_000L).toInt().coerceAtLeast(2)) + if (streak >= 2) 1 else 0
            BeesMode.BATTLE -> beeCap(streak, state.hiveUpgrade)
        }.coerceAtMost(state.nodes.size)

        // Hive spawns replacements while under the cap.
        var spawnClock = state.spawnClockMs + dtMs
        val spawningBlocked = state.mode == BeesMode.ADVENTURE && elapsed >= BEES_TIME_LIMIT_MS - BEES_TIME_WARNING_MS
        if (bees.size < cap && !spawningBlocked) {
            val interval = BEES_SPAWN_MIN_MS + (rng.next((BEES_SPAWN_MAX_MS - BEES_SPAWN_MIN_MS).toInt())) + bees.size * 3_000L
            if (spawnClock >= interval) {
                spawnClock = 0L
                val pool = state.unlocked.toList()
                val kind = if (pool.isEmpty()) BeesKind.NORMAL else pool[rng.next(pool.size)]
                val hive = state.nodes.first { it.type == BeesNodeType.HIVE }
                bees += BeesBee(nextId++, kind, hive.x, hive.y)
                events += BeesEvent.SPAWN
            }
        } else {
            spawnClock = 0L
        }

        // Bee movement.
        var updatedBees = mutableListOf<BeesBee>()
        for (bee in bees) {
            var b = bee
            when (b.state) {
                BeesBeeState.IDLE -> {
                    updatedBees += b
                }
                BeesBeeState.FLYING, BeesBeeState.RETURNING -> {
                    var remaining = b.kind.moveSpeed * dt
                    while (remaining > 0f && b.path.isNotEmpty()) {
                        val node = state.nodes.first { it.id == b.path.first() }
                        val dx = node.x - b.x
                        val dy = node.y - b.y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance <= remaining || distance < 0.01f) {
                            b = b.copy(x = node.x, y = node.y, path = b.path.drop(1), orbitDeg = b.orbitDeg + b.kind.orbitDegPerSec * 0.2f)
                            remaining -= distance
                        } else {
                            b = b.copy(x = b.x + dx / distance * remaining, y = b.y + dy / distance * remaining)
                            remaining = 0f
                        }
                    }
                    if (b.path.isEmpty()) {
                        val node = state.nodes.firstOrNull { it.id == b.target }
                        when {
                            node == null -> b = b.copy(state = BeesBeeState.IDLE, target = -1)
                            node.type == BeesNodeType.HIVE -> {
                                val deposited = b.carried
                                score += deposited.toInt()
                                tank = tank.copy(pollen = tank.pollen + deposited)
                                streak++
                                lastDeposit = elapsed
                                combo = 1
                                events += BeesEvent.DEPOSIT
                                b = b.copy(state = BeesBeeState.IDLE, target = -1, carried = 0f, carriedPetals = 0, harvestMs = 0L)
                            }
                            else -> {
                                val flower = node.flower
                                if (flower != null && flower.petals > 0) {
                                    b = b.copy(state = BeesBeeState.HARVESTING, harvestMs = 0L, target = node.id)
                                } else if (b.kind == BeesKind.POLLINATOR) {
                                    b = b.copy(state = BeesBeeState.HARVESTING, harvestMs = 0L, target = node.id)
                                } else if (state.theme == BeesTheme.SPACE && b.kind != BeesKind.NINJA) {
                                    // Empty space flowers bite.
                                    events += BeesEvent.EXPLODE
                                    b = b.copy(state = BeesBeeState.IDLE, target = -1, carried = 0f, carriedPetals = 0)
                                    streak = 0
                                    combo = 1
                                } else {
                                    b = b.copy(state = BeesBeeState.IDLE, target = -1)
                                }
                            }
                        }
                    }
                    updatedBees += b
                }
                BeesBeeState.HARVESTING -> {
                    val node = state.nodes.firstOrNull { it.id == b.target }
                    val flower = node?.flower
                    if (node == null || flower == null) {
                        b = b.copy(state = BeesBeeState.IDLE, target = -1)
                        updatedBees += b
                        continue
                    }
                    if (flower.petals <= 0 && b.kind == BeesKind.POLLINATOR) {
                        // Re-bloom on the pollinator's harvest cadence.
                        var harvest = b.harvestMs + dtMs
                        if (harvest >= b.kind.harvestMs) {
                            harvest = 0L
                            nodes = nodes.map { if (it.id == node.id) it.copy(flower = flower.copy(state = BeesFlowerState.BLOOM, petals = BEES_MAX_PETALS, wiltMs = BEES_WILT_PER_PETAL_MS * BEES_MAX_PETALS)) else it }
                        }
                        updatedBees += b.copy(harvestMs = harvest, orbitDeg = b.orbitDeg + b.kind.orbitDegPerSec * dt)
                        continue
                    }
                    if (flower.petals <= 0) {
                        val home = state.nodes.first { it.type == BeesNodeType.HIVE }
                        b = b.copy(state = BeesBeeState.RETURNING, path = pathTo(state, node.id, home.id), target = home.id)
                        updatedBees += b
                        continue
                    }
                    var harvest = b.harvestMs + dtMs
                    var petals = flower.petals
                    var carried = b.carried
                    var carriedPetals = b.carriedPetals
                    var harvested = false
                    while (harvest >= b.kind.harvestMs && carriedPetals < b.kind.strength && petals > 0) {
                        harvest -= b.kind.harvestMs
                        petals--
                        carriedPetals++
                        carried += flower.value.toFloat()
                        score += BEES_HARVEST_SCORE
                        harvested = true
                    }
                    if (harvested) {
                        events += BeesEvent.GATHER
                        val updatedFlower = flower.copy(
                            petals = petals,
                            wiltMs = petals * BEES_WILT_PER_PETAL_MS,
                            state = if (petals <= 0) BeesFlowerState.WILTED else flower.state,
                            timerMs = if (petals <= 0) BEES_GROW_MS else flower.timerMs,
                        )
                        nodes = nodes.map { if (it.id == node.id) it.copy(flower = updatedFlower) else it }
                    }
                    val done = carriedPetals >= b.kind.strength || petals <= 0
                    b = if (done) {
                        val home = state.nodes.first { it.type == BeesNodeType.HIVE }
                        b.copy(
                            state = BeesBeeState.RETURNING,
                            path = pathTo(state, node.id, home.id),
                            target = home.id,
                            carried = carried,
                            carriedPetals = carriedPetals,
                            harvestMs = harvest,
                        )
                    } else {
                        b.copy(harvestMs = harvest, carried = carried, carriedPetals = carriedPetals, orbitDeg = b.orbitDeg + b.kind.orbitDegPerSec * dt)
                    }
                    updatedBees += b
                }
            }
        }
        bees = updatedBees

        // Idle bees are auto-assigned to the nearest bloom.
        val assigned = mutableListOf<BeesBee>()
        for (bee in bees) {
            var b = bee
            if (b.state == BeesBeeState.IDLE) {
                val target = nearestBloomFromNodes(nodes, b)
                if (target != null) {
                    val from = nodes.minByOrNull { node ->
                        val dx = node.x - b.x
                        val dy = node.y - b.y
                        dx * dx + dy * dy
                    }?.id ?: 0
                    val path = pathBetween(state.paths, from, target)
                    if (path.isNotEmpty()) b = b.copy(state = BeesBeeState.FLYING, path = path, target = target)
                }
            }
            assigned += b
        }
        bees = assigned

        // Baddie spawning once the streak reaches 2.
        var baddieClock = state.baddieClockMs + dtMs
        val baddieLimit = 2 + state.mode.ordinal + (levels[state.levelId - 1].baddieBoost)
        if (streak >= 2 && !spawningBlocked && baddies.size < baddieLimit) {
            val interval = max(6_000L, 16_000L - streak * 400L) + rng.next(4_000)
            if (baddieClock >= interval) {
                baddieClock = 0L
                val kind = rollBaddie(state, rng)
                val target = if (kind == BeesBaddieKind.GOPHER || kind == BeesBaddieKind.HUMMINGBIRD) {
                    val flowers = nodes.filter { it.type == BeesNodeType.FLOWER }
                    flowers[rng.next(flowers.size)].id
                } else {
                    nodes.first { it.type == BeesNodeType.HIVE }.id
                }
                val spawnX = if (rng.next(2) == 0) 10f else 262f
                val gopher = kind == BeesBaddieKind.GOPHER
                val targetNode = nodes.first { it.id == target }
                baddies += BeesBaddie(
                    id = nextId++,
                    kind = kind,
                    x = if (gopher) targetNode.x else spawnX,
                    y = if (gopher) targetNode.y else 60f,
                    hp = kind.maxHp,
                    targetNode = target,
                    warningMs = if (gopher) 5_000L else 0L,
                    danceMs = if (gopher) 10_000L else 0L,
                    state = if (gopher) 0 else 1,
                    combo = combo,
                )
                events += BeesEvent.BADDIE_SPAWN
            }
        } else {
            baddieClock = 0L
        }

        // Baddie movement and behaviour.
        var updatedBaddies = mutableListOf<BeesBaddie>()
        for (baddie in baddies) {
            var baddieState = baddie
            val target = nodes.firstOrNull { it.id == baddie.targetNode }
            when (baddie.kind) {
                BeesBaddieKind.GOPHER -> {
                    if (baddie.state == 0) {
                        val warning = baddie.warningMs - dtMs
                        if (warning <= 0L) {
                            baddieState = baddie.copy(warningMs = 0L, state = 1, phaseMs = baddie.danceMs)
                        } else {
                            baddieState = baddie.copy(warningMs = warning)
                        }
                    } else {
                        val dance = baddie.phaseMs - dtMs
                        if (dance <= 0L) {
                            // Eats the flower and leaves.
                            if (target?.flower != null) {
                                nodes = nodes.map { if (it.id == target.id) it.copy(flower = target.flower.copy(petals = 0, state = BeesFlowerState.WILTED, timerMs = BEES_GROW_MS)) else it }
                            }
                            continue
                        }
                        baddieState = baddie.copy(phaseMs = dance)
                    }
                }
                BeesBaddieKind.HUMMINGBIRD -> {
                    if (target != null) {
                        val dx = target.x - baddie.x
                        val dy = target.y - baddie.y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < 4f || baddie.phaseMs >= 10_000L) {
                            baddieState = baddie.copy(phaseMs = baddie.phaseMs + dtMs)
                        } else {
                            baddieState = baddie.copy(
                                x = baddie.x + dx / distance * baddie.kind.speed * dt,
                                y = baddie.y + dy / distance * baddie.kind.speed * dt,
                            )
                        }
                    }
                }
                else -> {
                    if (target != null) {
                        val dx = target.x - baddie.x
                        val dy = target.y - baddie.y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance <= baddie.kind.speed * dt || distance < 4f) {
                            // Bears steal honey at the hive.
                            if (baddie.kind != BeesBaddieKind.BUNNY && tank.honey > 0) {
                                tank = tank.copy(honey = max(0, tank.honey - 2))
                            }
                            if (state.mode.canLose) {
                                hiveHits++
                                if (hiveHits >= 3) {
                                    events += BeesEvent.LOSE
                                    return state.copy(over = true, won = false, elapsedMs = elapsed, events = events)
                                }
                            }
                            continue
                        }
                        baddieState = baddie.copy(
                            x = baddie.x + dx / distance * baddie.kind.speed * dt,
                            y = baddie.y + dy / distance * baddie.kind.speed * dt,
                        )
                    }
                }
            }
            updatedBaddies += baddieState
        }
        baddies = updatedBaddies

        // Collisions: bee-bee and bee-baddie explode both (ninja bites through).
        val aliveBees = mutableListOf<BeesBee>()
        val aliveBaddies = baddies.toMutableList()
        val exploded = mutableSetOf<Int>()
        for (b in bees) {
            if (b.id in exploded) continue
            var collided = false
            for (other in bees) {
                if (other.id == b.id || other.id in exploded) continue
                val dx = other.x - b.x
                val dy = other.y - b.y
                if (dx * dx + dy * dy <= 22f * 22f) {
                    exploded += other.id
                    exploded += b.id
                    collided = true
                    events += BeesEvent.EXPLODE
                    break
                }
            }
            if (collided) continue
            for (baddieIndex in aliveBaddies.indices) {
                val baddie = aliveBaddies[baddieIndex]
                val dx = baddie.x - b.x
                val dy = baddie.y - b.y
                if (dx * dx + dy * dy <= 22f * 22f) {
                    if (b.kind == BeesKind.NINJA) {
                        val damage = b.kind.damage
                        val hp = aliveBaddies[baddieIndex].hp - damage
                        if (hp <= 0f) {
                            aliveBaddies.removeAt(baddieIndex)
                            score += baddie.kind.value * combo
                            combo++
                            events += BeesEvent.BADDIE_DOWN
                        } else {
                            aliveBaddies[baddieIndex] = aliveBaddies[baddieIndex].copy(hp = hp)
                        }
                    } else {
                        exploded += b.id
                        aliveBaddies.removeAt(baddieIndex)
                        events += BeesEvent.EXPLODE
                    }
                    break
                }
            }
        }
        if (exploded.isNotEmpty()) {
            bees = bees.filter { it.id !in exploded }.toMutableList()
            streak = 0
            combo = 1
        }

        // Time limit and warnings.
        if (!warningShown && elapsed >= BEES_TIME_LIMIT_MS - BEES_TIME_WARNING_MS) {
            warningShown = true
            events += BeesEvent.TIME_WARNING
        }
        var over = state.over
        var won = state.won
        if (elapsed >= BEES_TIME_LIMIT_MS) {
            over = true
            won = true
            events += BeesEvent.WIN
        }

        return state.copy(
            rng = rng.state,
            nodes = nodes,
            bees = bees,
            tank = tank,
            score = score,
            streak = streak,
            combo = combo,
            baddies = aliveBaddies,
            elapsedMs = elapsed,
            spawnClockMs = spawnClock,
            baddieClockMs = baddieClock,
            lastDepositMs = lastDeposit,
            hiveHits = hiveHits,
            warningShown = warningShown,
            over = over,
            won = won,
            nextId = nextId,
            events = events,
        )
    }

    private fun nearestBloomFromNodes(nodes: List<BeesNode>, bee: BeesBee): Int? {
        val bloom = nodes.filter { it.type == BeesNodeType.FLOWER && (it.flower?.petals ?: 0) > 0 }
        return bloom.minByOrNull { node ->
            val dx = node.x - bee.x
            val dy = node.y - bee.y
            dx * dx + dy * dy
        }?.id
    }

    private fun rollBaddie(state: BeesState, rng: BeesRandom): BeesBaddieKind {
        val roll = rng.next(100)
        return when {
            state.theme == BeesTheme.SNOW && roll < 20 -> BeesBaddieKind.BUNNY
            state.theme == BeesTheme.SNOW && roll < 60 -> BeesBaddieKind.POLAR_BEAR
            state.theme == BeesTheme.SPACE && roll < 40 -> BeesBaddieKind.MOON_BEAR
            roll < 30 -> BeesBaddieKind.BROWN_BEAR
            roll < 50 -> BeesBaddieKind.BLACK_BEAR
            roll < 70 -> BeesBaddieKind.GOPHER
            else -> BeesBaddieKind.HUMMINGBIRD
        }
    }

    // ----------------------------------------------------------- persistence --

    fun encode(state: BeesState): String {
        val nodes = state.nodes.joinToString("/") { node ->
            val f = node.flower
            listOf(
                node.id, node.type.ordinal, node.row, node.col,
                f?.color ?: -1, f?.petals ?: 0, f?.state?.ordinal ?: 0, f?.timerMs ?: 0L, f?.wiltMs ?: 0L,
            ).joinToString(",")
        }
        val bees = state.bees.joinToString("/") { bee ->
            listOf(
                bee.id, bee.kind.ordinal, bee.x, bee.y, bee.state.ordinal,
                bee.path.joinToString("+"), bee.target, bee.carried, bee.carriedPetals, bee.harvestMs, bee.orbitDeg,
            ).joinToString(",")
        }
        val baddies = state.baddies.joinToString("/") { b ->
            listOf(b.id, b.kind.ordinal, b.x, b.y, b.hp, b.targetNode, b.warningMs, b.danceMs, b.state, b.phaseMs, b.combo).joinToString(",")
        }
        val unlocked = state.unlocked.joinToString("+") { it.ordinal.toString() }
        return listOf(
            "bees1", state.levelId, state.theme.ordinal, state.mode.ordinal, state.seed, state.rng,
            state.tank.honey, state.tank.pollen, state.tank.convertBuffer,
            state.honeyJars, state.jarProgress, state.score, state.streak, state.combo,
            state.elapsedMs, state.spawnClockMs, state.baddieClockMs, state.lastDepositMs,
            state.hiveUpgrade, unlocked, state.hiveHits, if (state.warningShown) 1 else 0,
            if (state.over) 1 else 0, if (state.won) 1 else 0, state.nextId,
            nodes, bees, baddies,
        ).joinToString(";")
    }

    fun decode(blob: String): BeesState? = try {
        val p = blob.split(";")
        if (p.size < 27 || p[0] != "bees1") {
            null
        } else {
            val level = levelFor(p[1].toInt())
            val nodes = p[25].split("/").map { raw ->
                val f = raw.split(",")
                val color = f[4].toInt()
                BeesNode(
                    id = f[0].toInt(),
                    type = BeesNodeType.entries[f[1].toInt()],
                    row = f[2].toInt(),
                    col = f[3].toInt(),
                    x = nodeX(f[3].toInt()),
                    y = nodeY(f[2].toInt()),
                    flower = if (color >= 0) BeesFlower(color, f[5].toInt(), BeesFlowerState.entries[f[6].toInt()], f[7].toLong(), f[8].toLong()) else null,
                )
            }
            val bees = if (p[26].isEmpty()) emptyList() else p[26].split("/").map { raw ->
                val f = raw.split(",")
                BeesBee(
                    id = f[0].toInt(),
                    kind = BeesKind.entries[f[1].toInt()],
                    x = f[2].toFloat(),
                    y = f[3].toFloat(),
                    state = BeesBeeState.entries[f[4].toInt()],
                    path = if (f[5].isEmpty()) emptyList() else f[5].split("+").map { it.toInt() },
                    target = f[6].toInt(),
                    carried = f[7].toFloat(),
                    carriedPetals = f[8].toInt(),
                    harvestMs = f[9].toLong(),
                    orbitDeg = f[10].toFloat(),
                )
            }
            val baddies = if (p.size < 28 || p[27].isEmpty()) emptyList() else p[27].split("/").map { raw ->
                val f = raw.split(",")
                BeesBaddie(
                    f[0].toInt(), BeesBaddieKind.entries[f[1].toInt()], f[2].toFloat(), f[3].toFloat(),
                    f[4].toFloat(), f[5].toInt(), f[6].toLong(), f[7].toLong(), f[8].toInt(), f[9].toLong(), f[10].toInt(),
                )
            }
            val (fallbackNodes, fallbackPaths) = buildBoard(level)
            BeesState(
                levelId = level.id,
                theme = level.theme,
                mode = BeesMode.entries[p[3].toInt()],
                seed = p[4].toInt(),
                rng = p[5].toInt(),
                nodes = nodes.ifEmpty { fallbackNodes },
                paths = fallbackPaths,
                bees = bees,
                tank = BeesTank(p[6].toInt(), p[7].toFloat(), p[8].toFloat()),
                honeyJars = p[9].toInt(),
                jarProgress = p[10].toFloat(),
                score = p[11].toInt(),
                streak = p[12].toInt(),
                combo = p[13].toInt(),
                elapsedMs = p[14].toLong(),
                spawnClockMs = p[15].toLong(),
                baddieClockMs = p[16].toLong(),
                lastDepositMs = p[17].toLong(),
                hiveUpgrade = p[18].toInt(),
                unlocked = if (p[19].isEmpty()) emptySet() else p[19].split("+").map { BeesKind.entries[it.toInt()] }.toSet(),
                hiveHits = p[20].toInt(),
                warningShown = p[21] == "1",
                over = p[22] == "1",
                won = p[23] == "1",
                nextId = p[24].toInt(),
                baddies = baddies,
            )
        }
    } catch (_: Exception) {
        null
    }
}
