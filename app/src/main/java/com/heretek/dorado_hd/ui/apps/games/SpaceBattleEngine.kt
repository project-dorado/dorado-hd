package com.heretek.dorado_hd.ui.apps.games

/**
 * Space Battle 2: a script-driven vertical shoot-'em-up with a racing mode and
 * a 4x10 part customization layer.
 *
 * Pure Kotlin, no Android dependencies. Deterministic fixed 60 Hz (`tick`) with
 * a seeded LCG for drops and AI. Constants follow docs/apps/space-battle-2.md
 * §3. All stage scripts and race tracks are original data authored for Dorado.
 */
object SpaceBattleEngine {

    const val VIEW_W = 272
    const val VIEW_H = 480
    const val PLAY_AREA_DEFAULT = 400f
    const val TICK_US = 16_667L

    // Ship
    const val START_LIVES = 3
    const val START_AMMO = 3
    const val ACCEL_BASE = 8f
    const val ACCEL_PER_SPEED = 2.4f
    const val MAX_VELOCITY = 10f
    const val TOUCH_Y_OFFSET = 60f
    const val SECONDARY_FIRE_THRESHOLD = 50f
    const val RESPAWN_INVINCIBLE_MS = 3000L
    const val COLLISION_PENALTY_MS = 500L
    const val SHIELD_DEGRADATION_PER_MS = 0.005f
    const val MAX_AMMO_NORMAL = 9
    const val MAX_AMMO_ELITE = 5
    const val MAX_LIVES = 9

    // Scoring
    const val NEXT_LIFE_NORMAL = 2000
    const val NEXT_LIFE_NORMAL_INC = 3000
    const val NEXT_LIFE_HARD = 5000
    const val NEXT_LIFE_HARD_INC = 5000
    const val NEXT_LIFE_STEP = 1500

    // Weapon cadence
    const val PRIMARY_RELOAD_BASE = 500f
    const val PRIMARY_RELOAD_PER_BOOST = 50f
    val PHOTON_DAMAGE = intArrayOf(0, 25, 20, 18, 15)

    // Databases
    const val NUM_STAGES = 10
    const val NUM_RACES = 5
    const val CATEGORIES = 4
    const val PARTS_PER_CATEGORY = 10
    const val PALETTES = 5
    const val DROP_CHANCE = 0.35f
    const val DEFAULT_SEED = 0x51ED270B

    enum class Difficulty(val label: String) { NORMAL("normal"), HARD("hard"), ELITE("elite") }

    enum class ControlStyle { TOUCH_ABSOLUTE, TILT }

    enum class GameMode { CAMPAIGN, RACE }

    enum class SecondaryWeapon(val label: String) {
        WAVE("wave"),
        SWEEP("sweep"),
        LASER("laser"),
        ROCKETS("rockets"),
        BOMB("bomb"),
        AURA("aura"),
        DUAL_SWEEP("dual sweep"),
        UBER_LASER("uber laser"),
        MORE_ROCKETS("more rockets"),
        DROID("droid"),
    }

    enum class PowerUpKind { SHIELDS, UPGRADE, AMMO, ZAURIUM, EXTRA_LIFE, TIME }

    enum class EnemyKind(val hp: Float, val payload: Float, val value: Int, val radius: Float) {
        DRONE_MINDLESS(40f, 50f, 25, 9f),
        DRONE_CHARGER(60f, 50f, 100, 10f),
        DRONE_SEEKER(25f, 50f, 200, 9f),
        ASTEROID_SMALL(25f, 100f, 10, 10f),
        ASTEROID_MEDIUM(25f, 100f, 10, 14f),
        ASTEROID_LARGE(50f, 100f, 10, 20f),
        ASTEROID_EPIC(50f, 100f, 10, 28f),
        TURRET_PHOTON(200f, 50f, 50, 16f),
        TURRET_ROCKET(250f, 50f, 100, 16f),
    }

    enum class SbEvent {
        SHOOT, LAUNCH, ENEMY_HIT, EXPLODE, PLAYER_HIT, PLAYER_DIE, PICKUP,
        EXTRA_LIFE, BOSS_DIE, GAME_OVER, STAGE_CLEAR, RACE_WIN, RACE_LOSE, UI,
    }

    // ------------------------------------------------------------------
    // Customization
    // ------------------------------------------------------------------

    /** 4 categories x 10 parts x {power, armor, speed, boost, cost}. */
    val PART_VALUES: List<List<IntArray>> = listOf(
        listOf(
            intArrayOf(1, 0, 0, 0, 0), intArrayOf(2, -1, 0, 0, 10), intArrayOf(1, -1, 1, 1, 20),
            intArrayOf(1, 1, 0, 0, 30), intArrayOf(2, 0, 0, 1, 40), intArrayOf(3, -1, 2, -1, 50),
            intArrayOf(4, 1, -1, -1, 60), intArrayOf(3, 2, -1, 1, 70), intArrayOf(4, 0, -1, 1, 80),
            intArrayOf(3, 1, 1, 1, 100),
        ),
        listOf(
            intArrayOf(0, 1, 0, 1, 0), intArrayOf(1, 0, 0, 1, 10), intArrayOf(-1, -2, 1, 2, 20),
            intArrayOf(0, 0, 1, 1, 30), intArrayOf(1, -1, 0, 2, 40), intArrayOf(1, -1, 2, 2, 50),
            intArrayOf(2, 1, -1, 2, 60), intArrayOf(0, 0, 0, 4, 70), intArrayOf(2, -1, -1, 3, 80),
            intArrayOf(1, 1, 1, 3, 100),
        ),
        listOf(
            intArrayOf(0, 0, 1, 0, 0), intArrayOf(1, 0, 1, 0, 10), intArrayOf(-1, -1, 2, 1, 20),
            intArrayOf(-1, 1, 2, 1, 30), intArrayOf(0, -1, 2, 1, 40), intArrayOf(1, 0, 2, 1, 50),
            intArrayOf(2, 2, 1, -1, 60), intArrayOf(0, 1, 3, 0, 70), intArrayOf(1, 1, 2, 1, 80),
            intArrayOf(1, 1, 3, 1, 100),
        ),
        listOf(
            intArrayOf(0, 1, 0, 0, 0), intArrayOf(0, 1, 1, 0, 10), intArrayOf(0, 1, 0, 1, 20),
            intArrayOf(-1, 2, 1, 0, 30), intArrayOf(2, 0, 0, 2, 40), intArrayOf(0, 1, 1, 1, 50),
            intArrayOf(0, 3, -1, 0, 60), intArrayOf(-1, 4, -2, 0, 70), intArrayOf(2, 2, 0, -1, 80),
            intArrayOf(1, 3, 1, 1, 100),
        ),
    )

    val CATEGORY_LABELS = listOf("weapon", "boost", "wing", "hull")
    val STAT_LABELS = listOf("power", "armor", "speed", "boost")

    /** Part ids 5..6 need a Normal win, 7..8 Hard, 9 Elite. */
    fun isPartUnlocked(partId: Int, wins: Set<Difficulty>): Boolean = when {
        partId in 5..6 -> Difficulty.NORMAL in wins
        partId in 7..8 -> Difficulty.HARD in wins
        partId >= 9 -> Difficulty.ELITE in wins
        else -> true
    }

    fun statCost(category: Int, partId: Int): Int = PART_VALUES[category][partId][4]

    fun shipStat(build: SbBuild, stat: Int): Int {
        var value = 1
        for (category in 0 until CATEGORIES) {
            val part = build.parts[category].coerceIn(0, PARTS_PER_CATEGORY - 1)
            value += PART_VALUES[category][part][stat]
        }
        return value.coerceIn(1, 5)
    }

    data class SbBuild(
        val parts: List<Int> = List(CATEGORIES) { 0 },
        val colors: List<Int> = List(CATEGORIES) { PALETTES - 1 },
    ) {
        fun withPart(category: Int, part: Int): SbBuild =
            copy(parts = parts.toMutableList().also { it[category] = part.coerceIn(0, PARTS_PER_CATEGORY - 1) })

        fun withColor(category: Int, color: Int): SbBuild =
            copy(colors = colors.toMutableList().also { it[category] = color.coerceIn(0, PALETTES - 1) })
    }

    data class SbStats(
        val acceleration: Float,
        val maxVelocity: Float,
        val maxShields: Float,
        val shieldRechargeDelayMs: Float,
        val shieldRechargeSpeed: Float,
        val power: Int,
        val boost: Int,
        val speed: Int,
        val armor: Int,
        val secondary: SecondaryWeapon,
    )

    fun shipStats(build: SbBuild, difficulty: Difficulty): SbStats {
        val speedTier = shipStat(build, 2)
        val armorTier = shipStat(build, 1)
        var shields = 75f
        var delay = 12_500f
        var recharge = 0.01f
        when (armorTier) {
            2 -> { delay = 11_000f; recharge = 0.02f }
            3 -> { shields = 100f; delay = 9_000f; recharge = 0.04f }
            4 -> { shields = 125f; delay = 7_500f; recharge = 0.06f }
        }
        if (difficulty == Difficulty.ELITE) {
            shields = kotlin.math.floor(shields / 2f)
            delay *= 1.25f
        }
        return SbStats(
            acceleration = ACCEL_BASE + ACCEL_PER_SPEED * speedTier,
            maxVelocity = MAX_VELOCITY,
            maxShields = shields,
            shieldRechargeDelayMs = delay,
            shieldRechargeSpeed = recharge,
            power = shipStat(build, 0),
            boost = shipStat(build, 3),
            speed = speedTier,
            armor = armorTier,
            secondary = SecondaryWeapon.entries[build.parts[0].coerceIn(0, SecondaryWeapon.entries.size - 1)],
        )
    }

    fun maxAmmo(difficulty: Difficulty): Int = if (difficulty == Difficulty.ELITE) MAX_AMMO_ELITE else MAX_AMMO_NORMAL

    fun primaryReloadMs(build: SbBuild): Float = PRIMARY_RELOAD_BASE - PRIMARY_RELOAD_PER_BOOST * shipStat(build, 3)

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    data class SbProgress(
        val zaurium: Int = 0,
        val collected: List<List<Boolean>> = List(CATEGORIES) { category ->
            List(PARTS_PER_CATEGORY) { part -> category in 0 until CATEGORIES && part == 0 }
        },
        val build: SbBuild = SbBuild(),
        val wins: Set<Difficulty> = emptySet(),
        val highestStage: Map<Difficulty, Int> = Difficulty.entries.associateWith { 1 },
        val racesBeaten: List<Boolean> = List(NUM_RACES) { false },
        val ghosts: Map<Int, SbGhost> = emptyMap(),
        val bestTotals: Map<Difficulty, Int> = Difficulty.entries.associateWith { 0 },
    )

    fun owns(progress: SbProgress, category: Int, part: Int): Boolean =
        progress.collected.getOrNull(category)?.getOrNull(part) == true

    fun canBuy(progress: SbProgress, category: Int, part: Int): Boolean =
        part in 0 until PARTS_PER_CATEGORY &&
            !owns(progress, category, part) &&
            isPartUnlocked(part, progress.wins) &&
            progress.zaurium >= statCost(category, part)

    fun buy(progress: SbProgress, category: Int, part: Int): SbProgress {
        if (!canBuy(progress, category, part)) return progress
        val collected = progress.collected.mapIndexed { c, row ->
            row.mapIndexed { p, owned -> owned || (c == category && p == part) }
        }
        return progress.copy(
            zaurium = progress.zaurium - statCost(category, part),
            collected = collected,
            build = progress.build.withPart(category, part),
        )
    }

    fun equip(progress: SbProgress, category: Int, part: Int): SbProgress {
        if (!owns(progress, category, part)) return progress
        return progress.copy(build = progress.build.withPart(category, part))
    }

    fun encodeProgress(progress: SbProgress): String {
        val collected = progress.collected.joinToString(",") { row -> row.joinToString("") { if (it) "1" else "0" } }
        val wins = progress.wins.joinToString(",") { it.name }
        val highest = Difficulty.entries.joinToString(",") { "${it.name}:${progress.highestStage[it] ?: 1}" }
        val races = progress.racesBeaten.joinToString("") { if (it) "1" else "0" }
        val totals = Difficulty.entries.joinToString(",") { "${it.name}:${progress.bestTotals[it] ?: 0}" }
        val ghosts = progress.ghosts.entries.joinToString(";") { (track, ghost) -> "$track=${encodeGhost(ghost)}" }
        val parts = progress.build.parts.joinToString(",")
        val colors = progress.build.colors.joinToString(",")
        return listOf(
            "1", progress.zaurium.toString(), collected, wins, highest, races, totals,
            parts, colors, ghosts,
        ).joinToString("#")
    }

    fun decodeProgress(blob: String): SbProgress? {
        val parts = blob.split("#")
        if (parts.size < 10 || parts[0] != "1") return null
        val zaurium = parts[1].toIntOrNull() ?: return null
        val collected = parts[2].split(",").map { row -> row.map { it == '1' } }
        val wins = parts[3].split(",").mapNotNull { name -> Difficulty.entries.firstOrNull { it.name == name } }.toSet()
        val highest = parts[4].split(",").mapNotNull { entry ->
            val fields = entry.split(":")
            if (fields.size != 2) return@mapNotNull null
            val difficulty = Difficulty.entries.firstOrNull { it.name == fields[0] } ?: return@mapNotNull null
            difficulty to (fields[1].toIntOrNull() ?: 1)
        }.toMap()
        val races = parts[5].map { it == '1' }
        val totals = parts[6].split(",").mapNotNull { entry ->
            val fields = entry.split(":")
            if (fields.size != 2) return@mapNotNull null
            val difficulty = Difficulty.entries.firstOrNull { it.name == fields[0] } ?: return@mapNotNull null
            difficulty to (fields[1].toIntOrNull() ?: 0)
        }.toMap()
        val buildParts = parts[7].split(",").mapNotNull { it.toIntOrNull() }
        val buildColors = parts[8].split(",").mapNotNull { it.toIntOrNull() }
        val ghosts = parts[9].split(";").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val track = entry.substring(0, eq).toIntOrNull() ?: return@mapNotNull null
            val ghost = decodeGhost(entry.substring(eq + 1)) ?: return@mapNotNull null
            track to ghost
        }.toMap()
        val build = SbBuild(
            parts = if (buildParts.size == CATEGORIES) buildParts else SbBuild().parts,
            colors = if (buildColors.size == CATEGORIES) buildColors else SbBuild().colors,
        )
        return SbProgress(
            zaurium = zaurium,
            collected = if (collected.size == CATEGORIES && collected.all { it.size == PARTS_PER_CATEGORY }) collected else SbProgress().collected,
            build = build,
            wins = wins,
            highestStage = Difficulty.entries.associateWith { highest[it] ?: 1 },
            racesBeaten = if (races.size == NUM_RACES) races else SbProgress().racesBeaten,
            ghosts = ghosts,
            bestTotals = Difficulty.entries.associateWith { totals[it] ?: 0 },
        )
    }

    // ------------------------------------------------------------------
    // Stage scripts (original data; vocabulary: stage/spawn/boss)
    // ------------------------------------------------------------------

    data class SbSpawn(
        val atMs: Long,
        val kind: EnemyKind,
        val x: Float,
        val count: Int = 1,
        val gapMs: Long = 350L,
        val hpBonus: Float = 0f,
    )

    data class SbStage(
        val id: Int,
        val world: Int,
        val width: Float,
        val bossKind: String?,
        val bossHp: Float,
        val bossAtMs: Long,
        val spawns: List<SbSpawn>,
    )

    private val STAGE_SCRIPT: String = """
        stage 1 world 0 width 400 boss scout hp 700 at 14000
        spawn 0 drone_mindless 60
        spawn 900 drone_mindless 140
        spawn 2200 drone_mindless 90 2 400
        spawn 4200 asteroid_medium 200
        spawn 5600 drone_mindless 60 3 300
        spawn 7800 asteroid_small 120
        spawn 9200 drone_charger 180
        spawn 11000 asteroid_large 80
        stage 2 world 0 width 400 boss wasp hp 1200 at 16000
        spawn 0 drone_mindless 200 2 500
        spawn 1600 asteroid_small 60 3 350
        spawn 3400 drone_charger 160
        spawn 5000 drone_seeker 100
        spawn 6600 turret_photon 60
        spawn 8200 asteroid_medium 220 2 600
        spawn 10400 drone_charger 120 2 450
        spawn 12600 turret_photon 210
        stage 3 world 1 width 400 boss hornet hp 1500 at 18000
        spawn 0 drone_seeker 90 2 300
        spawn 1500 asteroid_medium 200
        spawn 3000 turret_rocket 70
        spawn 4600 drone_mindless 150 3 250
        spawn 6400 asteroid_large 90 2 700
        spawn 8400 drone_charger 220 2 400
        spawn 10800 turret_rocket 200
        spawn 13000 drone_seeker 130 3 300
        stage 4 world 1 width 480 boss redeye hp 2000 at 20000
        spawn 0 asteroid_small 120 4 250
        spawn 1800 drone_charger 60 2 500
        spawn 3600 turret_photon 240
        spawn 5400 drone_seeker 90 2 350
        spawn 7200 asteroid_epic 160
        spawn 9200 turret_rocket 200
        spawn 11200 drone_mindless 60 4 220
        spawn 13600 drone_charger 180 3 350
        stage 5 world 2 width 400 boss telson hp 3000 at 22000
        spawn 0 drone_seeker 100 3 260
        spawn 2000 asteroid_large 200 2 500
        spawn 4200 turret_photon 70
        spawn 6000 drone_charger 200 3 320
        spawn 8200 turret_rocket 60
        spawn 10200 asteroid_epic 120
        spawn 12200 drone_seeker 220 3 300
        spawn 14800 drone_mindless 90 4 240
        stage 6 world 2 width 480 boss orbital hp 4000 at 24000
        spawn 0 asteroid_medium 100 4 300
        spawn 2200 turret_rocket 220
        spawn 4000 drone_seeker 60 3 280
        spawn 6200 drone_charger 140 4 260
        spawn 8600 asteroid_epic 240
        spawn 10800 turret_photon 80
        spawn 13000 drone_seeker 160 3 260
        spawn 15800 turret_rocket 90
        stage 7 world 3 width 400 boss general hp 5500 at 26000
        spawn 0 drone_charger 120 3 280
        spawn 2400 asteroid_epic 200
        spawn 4400 turret_photon 60 2 500
        spawn 6800 drone_seeker 220 4 240
        spawn 9200 turret_rocket 120
        spawn 11600 asteroid_large 70 3 420
        spawn 14200 drone_charger 200 3 280
        spawn 17000 turret_photon 200
        stage 8 world 3 width 480 boss angel hp 7000 at 28000
        spawn 0 drone_seeker 80 4 220
        spawn 2000 turret_rocket 200
        spawn 4200 asteroid_epic 100 2 600
        spawn 6600 drone_charger 60 4 240
        spawn 9000 turret_photon 220
        spawn 11400 drone_seeker 200 3 260
        spawn 14000 turret_rocket 70
        spawn 16800 asteroid_epic 180
        stage 9 world 4 width 400 boss ram hp 9000 at 30000
        spawn 0 turret_photon 90 2 400
        spawn 2400 drone_charger 200 4 240
        spawn 5000 drone_seeker 70 4 220
        spawn 7600 turret_rocket 230
        spawn 10200 asteroid_epic 60 3 380
        spawn 13000 drone_charger 120 3 260
        spawn 15800 turret_photon 200
        spawn 18600 drone_seeker 140 4 220
        stage 10 world 4 width 480 boss core hp 12000 at 34000
        spawn 0 drone_seeker 100 4 220
        spawn 2600 turret_rocket 80
        spawn 5200 drone_charger 220 4 240
        spawn 8000 asteroid_epic 120 2 500
        spawn 10800 turret_photon 200
        spawn 13600 drone_seeker 70 5 200
        spawn 16600 turret_rocket 220
        spawn 19600 drone_charger 100 4 240
        spawn 22600 drone_seeker 200 4 200
    """.trimIndent()

    val stages: List<SbStage> by lazy { parseStages(STAGE_SCRIPT) }

    fun parseStages(text: String): List<SbStage> {
        val stages = mutableListOf<SbStage>()
        var id = 0
        var world = 0
        var width = PLAY_AREA_DEFAULT
        var bossKind: String? = null
        var bossHp = 0f
        var bossAt = Long.MAX_VALUE
        var spawns = mutableListOf<SbSpawn>()

        fun flush() {
            if (id > 0) stages += SbStage(id, world, width, bossKind, bossHp, bossAt, spawns.toList())
            spawns = mutableListOf()
            bossKind = null
            bossHp = 0f
            bossAt = Long.MAX_VALUE
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val tokens = line.split(Regex("\\s+"))
            when (tokens[0]) {
                "stage" -> {
                    flush()
                    id = tokens[1].toInt()
                    var i = 2
                    while (i + 1 < tokens.size) {
                        when (tokens[i]) {
                            "world" -> world = tokens[i + 1].toInt()
                            "width" -> width = tokens[i + 1].toFloat()
                            "boss" -> bossKind = tokens[i + 1]
                            "hp" -> bossHp = tokens[i + 1].toFloat()
                            "at" -> bossAt = tokens[i + 1].toLong()
                        }
                        i += 2
                    }
                }

                "spawn" -> {
                    val at = tokens[1].toLong()
                    val kind = EnemyKind.entries.firstOrNull { it.name.equals(tokens[2], ignoreCase = true) }
                        ?: throw IllegalArgumentException("unknown enemy kind: ${tokens[2]}")
                    val x = tokens[3].toFloat()
                    val count = tokens.getOrNull(4)?.toIntOrNull() ?: 1
                    val gap = tokens.getOrNull(5)?.toLongOrNull() ?: 350L
                    spawns += SbSpawn(at, kind, x, count, gap)
                }

                else -> throw IllegalArgumentException("unknown script record: ${tokens[0]}")
            }
        }
        flush()
        return stages
    }

    fun stage(id: Int): SbStage = stages[(id - 1).coerceIn(0, stages.size - 1)]

    // ------------------------------------------------------------------
    // Races
    // ------------------------------------------------------------------

    data class SbRaceTrack(val id: Int, val name: String, val lengthPx: Float, val gates: List<Float>)

    val races: List<SbRaceTrack> = listOf(
        SbRaceTrack(1, "lunar sprint", 6_000f, listOf(1_200f, 2_600f, 4_200f)),
        SbRaceTrack(2, "belt run", 8_000f, listOf(1_000f, 2_400f, 3_800f, 5_600f, 7_000f)),
        SbRaceTrack(3, "ring drift", 10_000f, listOf(1_400f, 3_000f, 4_600f, 6_400f, 8_200f)),
        SbRaceTrack(4, "comet trail", 12_000f, listOf(1_200f, 2_800f, 4_400f, 6_000f, 7_800f, 9_600f)),
        SbRaceTrack(5, "core gauntlet", 15_000f, listOf(1_000f, 2_400f, 3_800f, 5_400f, 7_000f, 8_800f, 10_600f, 12_600f)),
    )

    fun race(id: Int): SbRaceTrack = races[(id - 1).coerceIn(0, races.size - 1)]

    data class GhostSample(val tMs: Long, val distancePx: Float, val x: Float)

    data class SbGhost(val trackId: Int, val finishMs: Long, val samples: List<GhostSample>)

    fun encodeGhost(ghost: SbGhost): String =
        "${ghost.trackId}|${ghost.finishMs}|" + ghost.samples.joinToString(";") { "${it.tMs},${it.distancePx},${it.x}" }

    fun decodeGhost(blob: String): SbGhost? {
        val parts = blob.split("|")
        if (parts.size != 3) return null
        val track = parts[0].toIntOrNull() ?: return null
        val finish = parts[1].toLongOrNull() ?: return null
        val samples = parts[2].split(";").mapNotNull { entry ->
            val fields = entry.split(",")
            if (fields.size != 3) return@mapNotNull null
            val t = fields[0].toLongOrNull() ?: return@mapNotNull null
            val d = fields[1].toFloatOrNull() ?: return@mapNotNull null
            val x = fields[2].toFloatOrNull() ?: return@mapNotNull null
            GhostSample(t, d, x)
        }
        return SbGhost(track, finish, samples)
    }

    fun ghostAt(ghost: SbGhost, tMs: Long): GhostSample? {
        val index = ghost.samples.indexOfLast { it.tMs <= tMs }
        return ghost.samples.getOrNull(index)
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    data class SbInput(
        val touching: Boolean = false,
        val touchX: Float = 0f,
        val touchY: Float = 0f,
        val control: ControlStyle = ControlStyle.TOUCH_ABSOLUTE,
        val tiltX: Float = 0f,
        val tiltY: Float = 0f,
        val tiltActive: Boolean = false,
        val fire: Boolean = false,
    )

    data class Ship(
        val x: Float,
        val y: Float,
        val vx: Float = 0f,
        val vy: Float = 0f,
        val shields: Float,
        val maxShields: Float,
        val shieldDelayMs: Float = 0f,
        val rechargeSpeed: Float,
        val invincibleMs: Long = RESPAWN_INVINCIBLE_MS,
        val collisionPenaltyMs: Long = 0L,
        val primaryLevel: Int = 1,
        val primaryCooldownMs: Float = 0f,
        val alive: Boolean = true,
        val respawnMs: Long = 0L,
    )

    data class Enemy(
        val kind: EnemyKind,
        val x: Float,
        val y: Float,
        val hp: Float,
        val vx: Float = 0f,
        val vy: Float = 0f,
        val fireInMs: Long = 1_400L,
        val rotation: Float = 0f,
    )

    data class Bolt(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val damage: Float,
        val friendly: Boolean,
        val kind: Int = 0,
    )

    data class Pickup(val x: Float, val y: Float, val kind: PowerUpKind, val vy: Float = 0.02f)

    data class Boss(
        val kind: String,
        val x: Float,
        val y: Float,
        val hp: Float,
        val maxHp: Float,
        val exposed: Boolean = false,
        val attackMs: Long = 0L,
        val fireInMs: Long = 1_200L,
        val entryY: Float = 110f,
        val dir: Float = 1f,
    )

    data class SbState(
        val mode: GameMode,
        val difficulty: Difficulty,
        val stage: Int,
        val race: Int = 0,
        val build: SbBuild = SbBuild(),
        val ship: Ship,
        val lives: Int = START_LIVES,
        val ammo: Int = START_AMMO,
        val score: Int = 0,
        val totalScore: Int = 0,
        val zaurium: Int = 0,
        val nextLife: Int,
        val nextLifeInc: Int,
        val enemies: List<Enemy> = emptyList(),
        val bolts: List<Bolt> = emptyList(),
        val pickups: List<Pickup> = emptyList(),
        val boss: Boss? = null,
        val bossSpawned: Boolean = false,
        val scriptIndex: Int = 0,
        val stageTimeMs: Long = 0L,
        val playAreaWidth: Float = PLAY_AREA_DEFAULT,
        val panningOffset: Float = 0f,
        val distancePx: Float = 0f,
        val raceTimeMs: Long = 0L,
        val raceFinished: Boolean = false,
        val raceWon: Boolean? = null,
        val ghost: SbGhost? = null,
        val ghostSamples: List<GhostSample> = emptyList(),
        val gatesPassed: Int = 0,
        val overdriveMs: Long = 0L,
        val stageComplete: Boolean = false,
        val victory: Boolean = false,
        val gameOver: Boolean = false,
        val events: List<SbEvent> = emptyList(),
        val rng: Int = DEFAULT_SEED,
        val accumulatorUs: Long = 0L,
    )

    fun newGame(
        stageId: Int,
        difficulty: Difficulty = Difficulty.NORMAL,
        build: SbBuild = SbBuild(),
        seed: Int = DEFAULT_SEED,
        mode: GameMode = GameMode.CAMPAIGN,
        raceId: Int = 1,
        ghost: SbGhost? = null,
        playAreaWidth: Float = PLAY_AREA_DEFAULT,
    ): SbState {
        val stats = shipStats(build, difficulty)
        val target = stage(stageId)
        val width = if (mode == GameMode.CAMPAIGN) target.width else playAreaWidth
        val nextLife = if (difficulty == Difficulty.NORMAL) NEXT_LIFE_NORMAL else NEXT_LIFE_HARD
        val nextLifeInc = if (difficulty == Difficulty.NORMAL) NEXT_LIFE_NORMAL_INC else NEXT_LIFE_HARD_INC
        return SbState(
            mode = mode,
            difficulty = difficulty,
            stage = stageId,
            race = raceId,
            build = build,
            ship = Ship(
                x = width / 2f,
                y = VIEW_H - 20f,
                shields = stats.maxShields,
                maxShields = stats.maxShields,
                rechargeSpeed = stats.shieldRechargeSpeed,
            ),
            nextLife = nextLife,
            nextLifeInc = nextLifeInc,
            playAreaWidth = width,
            ghost = ghost,
            rng = if (seed == 0) DEFAULT_SEED else seed,
        )
    }

    /** One 60 Hz frame. */
    fun tick(state: SbState, input: SbInput = SbInput()): SbState {
        if (state.gameOver || state.stageComplete || state.victory || state.raceFinished) return state
        var s = state.copy(events = emptyList())
        val stats = shipStats(s.build, s.difficulty)
        s = s.copy(stageTimeMs = s.stageTimeMs + 1_000L / 60L)
        if (s.mode == GameMode.RACE) s = s.copy(raceTimeMs = s.raceTimeMs + 1_000L / 60L)
        s = steer(s, input, stats)
        s = updateShip(s, stats)
        s = firePrimary(s)
        s = fireSecondary(s, input, stats)
        s = updateBolts(s)
        s = updateEnemies(s)
        s = updateBoss(s)
        s = updatePickups(s)
        if (s.mode == GameMode.CAMPAIGN) s = spawnFromScript(s)
        s = updateRace(s, stats)
        return if (s.mode == GameMode.CAMPAIGN) settle(s) else s
    }

    fun step(state: SbState, dtMs: Long): SbState = step(state, SbInput(), dtMs)

    fun step(state: SbState, input: SbInput, dtMs: Long): SbState {
        if (dtMs <= 0L) return state
        var s = state
        var acc = s.accumulatorUs + dtMs * 1000
        while (acc >= TICK_US) {
            s = tick(s, input)
            acc -= TICK_US
        }
        return s.copy(accumulatorUs = acc)
    }

    // ------------------------------------------------------------------
    // Ship control
    // ------------------------------------------------------------------

    private fun steer(state: SbState, input: SbInput, stats: SbStats): SbState {
        val ship = state.ship
        if (!ship.alive) return state
        var vx = ship.vx
        var vy = ship.vy
        if (input.control == ControlStyle.TOUCH_ABSOLUTE && input.touching) {
            val targetX = input.touchX * state.playAreaWidth / VIEW_W.toFloat()
            val targetY = input.touchY - TOUCH_Y_OFFSET
            val dx = targetX - ship.x
            val dy = targetY - ship.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance > 0.5f) {
                val step = minOf(stats.acceleration, distance / 4f)
                vx += dx / distance * step
                vy += dy / distance * step
            }
        } else if (input.control == ControlStyle.TILT && input.tiltActive) {
            val mag = kotlin.math.sqrt(input.tiltX * input.tiltX + input.tiltY * input.tiltY)
            if (mag > 0.01f) {
                val clamped = minOf(mag, 1f)
                vx += input.tiltX / mag * stats.acceleration * clamped
                vy += input.tiltY / mag * stats.acceleration * clamped
            }
        }
        return state.copy(ship = ship.copy(vx = vx, vy = vy))
    }

    private fun updateShip(state: SbState, stats: SbStats): SbState {
        val ship = state.ship
        if (!ship.alive) {
            if (ship.respawnMs <= 0L) return state
            val remaining = ship.respawnMs - 1_000L / 60L
            if (remaining > 0L) return state.copy(ship = ship.copy(respawnMs = remaining))
            val fresh = Ship(
                x = state.playAreaWidth / 2f,
                y = VIEW_H - 20f,
                shields = stats.maxShields,
                maxShields = stats.maxShields,
                rechargeSpeed = stats.shieldRechargeSpeed,
                invincibleMs = RESPAWN_INVINCIBLE_MS,
                primaryLevel = ship.primaryLevel,
            )
            return state.copy(ship = fresh)
        }
        var x = ship.x + ship.vx
        var y = ship.y + ship.vy
        var vx = ship.vx * 0.6f
        var vy = ship.vy * 0.6f
        val speed = kotlin.math.sqrt(vx * vx + vy * vy)
        if (speed > stats.maxVelocity) {
            vx = vx / speed * stats.maxVelocity
            vy = vy / speed * stats.maxVelocity
        }
        x = x.coerceIn(8f, state.playAreaWidth - 8f)
        y = y.coerceIn(60f, VIEW_H - 12f)
        var shields = ship.shields
        var shieldDelay = (ship.shieldDelayMs - 1_000L / 60L).coerceAtLeast(0f)
        if (shields < stats.maxShields && shieldDelay <= 0f) {
            shields = minOf(stats.maxShields, shields + stats.shieldRechargeSpeed * (1_000f / 60f))
        } else if (shields > stats.maxShields && shieldDelay <= 0f) {
            shields = (shields - SHIELD_DEGRADATION_PER_MS * (1_000f / 60f)).coerceAtLeast(stats.maxShields)
        }
        val panning = (x - VIEW_W / 2f).coerceIn(0f, maxOf(0f, state.playAreaWidth - VIEW_W))
        return state.copy(
            ship = ship.copy(
                x = x,
                y = y,
                vx = vx,
                vy = vy,
                shields = shields,
                shieldDelayMs = shieldDelay,
                invincibleMs = (ship.invincibleMs - 1_000L / 60L).coerceAtLeast(0L),
                collisionPenaltyMs = (ship.collisionPenaltyMs - 1_000L / 60L).coerceAtLeast(0L),
                primaryCooldownMs = (ship.primaryCooldownMs - 1_000f / 60f).coerceAtLeast(0f),
            ),
            panningOffset = panning,
        )
    }

    // ------------------------------------------------------------------
    // Weapons
    // ------------------------------------------------------------------

    private fun firePrimary(state: SbState): SbState {
        val ship = state.ship
        if (!ship.alive || ship.primaryCooldownMs > 0f) return state
        val level = ship.primaryLevel.coerceIn(1, 4)
        val damage = PHOTON_DAMAGE[level].toFloat()
        val bolts = state.bolts.toMutableList()
        fun photon(angleDeg: Float) {
            val rad = (angleDeg - 90f) * (kotlin.math.PI.toFloat() / 180f)
            bolts += Bolt(
                x = ship.x + kotlin.math.cos(rad) * 9f,
                y = ship.y + kotlin.math.sin(rad) * 9f,
                vx = kotlin.math.cos(rad) * 8f,
                vy = kotlin.math.sin(rad) * 8f,
                damage = damage,
                friendly = true,
            )
        }
        when (level) {
            1 -> photon(0f)
            2 -> { photon(-3f); photon(3f) }
            3 -> { photon(-7f); photon(0f); photon(7f) }
            else -> { photon(-14f); photon(-5f); photon(5f); photon(14f) }
        }
        return state.copy(
            bolts = bolts,
            ship = ship.copy(primaryCooldownMs = primaryReloadMs(state.build)),
            events = state.events + SbEvent.SHOOT,
        )
    }

    private fun fireSecondary(state: SbState, input: SbInput, stats: SbStats): SbState {
        val ship = state.ship
        if (!input.fire || state.ammo <= 0 || !ship.alive) return state
        if (input.control == ControlStyle.TOUCH_ABSOLUTE && input.touching) {
            val dx = input.touchX * state.playAreaWidth / VIEW_W.toFloat() - ship.x
            val dy = (input.touchY - TOUCH_Y_OFFSET) - ship.y
            if (kotlin.math.sqrt(dx * dx + dy * dy) > SECONDARY_FIRE_THRESHOLD) return state
        }
        val bolts = state.bolts.toMutableList()
        val events = state.events.toMutableList()
        when (stats.secondary) {
            SecondaryWeapon.WAVE -> {
                for (i in -1..1) bolts += Bolt(ship.x + i * 8f, ship.y, i * 1.2f, -6.5f, 25f, true, kind = 1)
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.SWEEP, SecondaryWeapon.DUAL_SWEEP -> {
                val shots = if (stats.secondary == SecondaryWeapon.DUAL_SWEEP) 9 else 5
                for (i in 0 until shots) {
                    val spread = if (shots == 9) 20f else 30f
                    val angle = (-((shots - 1) * spread) / 2f + i * spread) * (kotlin.math.PI.toFloat() / 180f)
                    bolts += Bolt(
                        ship.x,
                        ship.y,
                        kotlin.math.sin(angle) * 7f,
                        -kotlin.math.cos(angle) * 7f,
                        25f,
                        true,
                        kind = 2,
                    )
                }
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.LASER, SecondaryWeapon.UBER_LASER -> {
                val lanes = if (stats.secondary == SecondaryWeapon.UBER_LASER) 3 else 1
                for (i in 0 until lanes) {
                    bolts += Bolt(ship.x + (i - (lanes - 1) / 2f) * 14f, ship.y - 120f, 0f, -7f, 60f, true, kind = 3)
                }
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.ROCKETS, SecondaryWeapon.MORE_ROCKETS -> {
                val count = if (stats.secondary == SecondaryWeapon.MORE_ROCKETS) 4 else 2
                for (i in 0 until count) {
                    val side = if (i % 2 == 0) -1f else 1f
                    bolts += Bolt(ship.x, ship.y, side * 1.8f, -5.5f, 40f, true, kind = 4)
                }
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.BOMB -> {
                bolts += Bolt(ship.x, ship.y - 10f, 0f, -4f, 80f, true, kind = 5)
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.AURA -> {
                for (i in 0 until 12) {
                    val angle = i * (2f * kotlin.math.PI.toFloat() / 12f)
                    bolts += Bolt(ship.x, ship.y, kotlin.math.cos(angle) * 5f, kotlin.math.sin(angle) * 5f, 20f, true, kind = 6)
                }
                events += SbEvent.LAUNCH
            }

            SecondaryWeapon.DROID -> {
                bolts += Bolt(ship.x, ship.y - 14f, 0f, -4f, 18f, true, kind = 7)
                events += SbEvent.LAUNCH
            }
        }
        return state.copy(bolts = bolts, ammo = state.ammo - 1, events = events)
    }

    // ------------------------------------------------------------------
    // Projectiles, kills and drops
    // ------------------------------------------------------------------

    private fun updateBolts(state: SbState): SbState {
        if (state.bolts.isEmpty()) return state
        var s = state
        val events = s.events.toMutableList()
        var score = s.score
        var zaurium = s.zaurium
        var pickups = s.pickups
        var rng = s.rng
        var ship = s.ship
        val remaining = mutableListOf<Bolt>()
        val enemies = s.enemies.toMutableList()
        var boss = s.boss
        val incoming = mutableListOf<Float>()

        for (bolt in s.bolts) {
            val nx = bolt.x + bolt.vx
            val ny = bolt.y + bolt.vy
            if (nx < -40f || nx > s.playAreaWidth + 40f || ny < -40f || ny > VIEW_H + 40f) continue
            var consumed = false
            if (bolt.friendly) {
                for (i in enemies.indices) {
                    val enemy = enemies[i]
                    val dx = nx - enemy.x
                    val dy = ny - enemy.y
                    val radius = enemy.kind.radius + 5f
                    if (dx * dx + dy * dy <= radius * radius) {
                        consumed = true
                        val hp = enemy.hp - bolt.damage
                        if (hp <= 0f) {
                            enemies.removeAt(i)
                            score += enemy.kind.value
                            events += SbEvent.EXPLODE
                            val (nrng, dropRoll) = rngUnit(rng)
                            rng = nrng
                            if (dropRoll < DROP_CHANCE) {
                                val (krng, kindRoll) = rngBound(rng, PowerUpKind.entries.size)
                                rng = krng
                                pickups = pickups + Pickup(enemy.x, enemy.y, resolvePowerUpKind(s, PowerUpKind.entries[kindRoll]))
                            }
                        } else {
                            enemies[i] = enemy.copy(hp = hp)
                            events += SbEvent.ENEMY_HIT
                        }
                        break
                    }
                }
                if (!consumed && boss != null) {
                    val b = boss
                    val dx = nx - b.x
                    val dy = ny - b.y
                    val radius = bossRadius(b.kind) + 5f
                    if (dx * dx + dy * dy <= radius * radius) {
                        consumed = true
                        if (b.exposed) {
                            val hp = b.hp - bolt.damage
                            if (hp <= 0f) {
                                boss = null
                                score += 1_500
                                events += SbEvent.BOSS_DIE
                                pickups = pickups + Pickup(b.x - 12f, b.y, resolvePowerUpKind(s, PowerUpKind.UPGRADE))
                                pickups = pickups + Pickup(b.x + 12f, b.y, resolvePowerUpKind(s, PowerUpKind.SHIELDS))
                            } else {
                                boss = b.copy(hp = hp)
                                events += SbEvent.ENEMY_HIT
                            }
                        } else {
                            // Weak points only open while the boss is attacking.
                            events += SbEvent.ENEMY_HIT
                        }
                    }
                }
            } else if (ship.alive && ship.invincibleMs <= 0L) {
                val dx = nx - ship.x
                val dy = ny - ship.y
                if (dx * dx + dy * dy <= 100f) {
                    consumed = true
                    incoming += bolt.damage
                }
            }
            if (!consumed) remaining += bolt.copy(x = nx, y = ny)
        }

        if (incoming.isNotEmpty() && ship.alive) {
            val total = incoming.sum()
            if (ship.shields > 0f) {
                ship = ship.copy(
                    shields = (ship.shields - total).coerceAtLeast(0f),
                    shieldDelayMs = statsShieldDelay(s),
                    collisionPenaltyMs = COLLISION_PENALTY_MS,
                )
                events += SbEvent.PLAYER_HIT
            } else {
                events += SbEvent.PLAYER_DIE
                val remainingLives = s.lives - 1
                if (s.mode == GameMode.RACE) {
                    ship = ship.copy(alive = false, respawnMs = 1_000L)
                } else if (remainingLives <= 0) {
                    return s.copy(
                        score = score,
                        zaurium = zaurium,
                        ship = ship,
                        lives = 0,
                        enemies = enemies,
                        boss = boss,
                        bolts = remaining,
                        pickups = pickups,
                        gameOver = true,
                        events = events + SbEvent.GAME_OVER,
                        rng = rng,
                    )
                } else {
                    ship = ship.copy(alive = false, respawnMs = 1_000L)
                    s = s.copy(lives = remainingLives)
                }
            }
        }

        // Extra-life thresholds.
        var nextLife = s.nextLife
        var nextLifeInc = s.nextLifeInc
        var lives = s.lives
        while (score + s.totalScore > nextLife) {
            nextLife += nextLifeInc
            nextLifeInc += NEXT_LIFE_STEP
            if (lives < MAX_LIVES) {
                pickups = pickups + Pickup(ship.x.coerceIn(20f, 220f), -20f, PowerUpKind.EXTRA_LIFE)
                events += SbEvent.EXTRA_LIFE
            } else {
                pickups = pickups + Pickup(ship.x.coerceIn(20f, 220f), -20f, PowerUpKind.ZAURIUM)
            }
        }
        return s.copy(
            score = score,
            zaurium = zaurium,
            lives = lives,
            ship = ship,
            enemies = enemies,
            boss = boss,
            bolts = remaining,
            pickups = pickups,
            nextLife = nextLife,
            nextLifeInc = nextLifeInc,
            events = events,
            rng = rng,
        )
    }

    fun resolvePowerUpKind(state: SbState, requested: PowerUpKind): PowerUpKind {
        var kind = requested
        if (kind == PowerUpKind.UPGRADE && state.ship.primaryLevel >= 4) kind = PowerUpKind.SHIELDS
        val valid = mutableListOf(
            PowerUpKind.SHIELDS,
            PowerUpKind.UPGRADE,
            PowerUpKind.AMMO,
        ).filter { candidate ->
            when (candidate) {
                PowerUpKind.SHIELDS -> state.ship.shields <= state.ship.maxShields
                PowerUpKind.UPGRADE -> state.ship.primaryLevel < 4
                PowerUpKind.AMMO -> state.ammo < maxAmmo(state.difficulty)
                else -> true
            }
        }
        if (valid.isEmpty() && kind != PowerUpKind.EXTRA_LIFE) return PowerUpKind.ZAURIUM
        if (kind == PowerUpKind.SHIELDS && PowerUpKind.SHIELDS !in valid) {
            return valid.getOrElse(pickIndex(state.rng, valid.size)) { PowerUpKind.ZAURIUM }
        }
        if (kind == PowerUpKind.UPGRADE && PowerUpKind.UPGRADE !in valid) {
            return valid.getOrElse(pickIndex(state.rng, valid.size)) { PowerUpKind.ZAURIUM }
        }
        if (kind == PowerUpKind.AMMO && PowerUpKind.AMMO !in valid) {
            return valid.getOrElse(pickIndex(state.rng, valid.size)) { PowerUpKind.ZAURIUM }
        }
        return kind
    }

    private fun statsShieldDelay(state: SbState): Float = shipStats(state.build, state.difficulty).shieldRechargeDelayMs

    /**
     * Adds score and resolves the extra-life ladder: Normal 2000 with +3000
     * then +1500 steps, Hard/Elite 5000 with +5000 steps. Threshold crossings
     * spawn extra-life drops (or zaurium when lives are full).
     */
    fun grantScore(state: SbState, value: Int): SbState {
        var score = state.score + value
        var nextLife = state.nextLife
        var nextLifeInc = state.nextLifeInc
        var pickups = state.pickups
        var events = state.events
        val lives = state.lives
        while (score + state.totalScore > nextLife) {
            nextLife += nextLifeInc
            nextLifeInc += NEXT_LIFE_STEP
            if (lives < MAX_LIVES) {
                pickups = pickups + Pickup(state.ship.x.coerceIn(20f, 220f), -20f, PowerUpKind.EXTRA_LIFE)
                events = events + SbEvent.EXTRA_LIFE
            } else {
                pickups = pickups + Pickup(state.ship.x.coerceIn(20f, 220f), -20f, PowerUpKind.ZAURIUM)
            }
        }
        return state.copy(score = score, nextLife = nextLife, nextLifeInc = nextLifeInc, pickups = pickups, events = events)
    }

    // ------------------------------------------------------------------
    // Enemies, boss, pickups, script, race
    // ------------------------------------------------------------------

    private fun updateEnemies(state: SbState): SbState {
        if (state.enemies.isEmpty()) return state
        val events = state.events.toMutableList()
        val bolts = state.bolts.toMutableList()
        var ship = state.ship
        val updated = mutableListOf<Enemy>()
        var dead = false
        for (enemy in state.enemies) {
            var vx = enemy.vx
            var vy = enemy.vy
            var fireIn = enemy.fireInMs - 1_000L / 60L
            when (enemy.kind) {
                EnemyKind.DRONE_MINDLESS -> vy = 1.2f
                EnemyKind.DRONE_CHARGER -> {
                    vy = 1.6f
                    if (ship.alive && enemy.y < ship.y && kotlin.math.abs(enemy.x - ship.x) < 90f) {
                        vx = if (ship.x > enemy.x) 1.2f else -1.2f
                    }
                }

                EnemyKind.DRONE_SEEKER -> {
                    if (ship.alive) {
                        val dx = ship.x - enemy.x
                        val dy = ship.y - enemy.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                        vx = dx / dist * 1.4f
                        vy = dy / dist * 1.4f
                    } else {
                        vy = 1.2f
                    }
                }

                EnemyKind.ASTEROID_SMALL -> vy = 1.0f
                EnemyKind.ASTEROID_MEDIUM -> vy = 0.9f
                EnemyKind.ASTEROID_LARGE -> vy = 0.8f
                EnemyKind.ASTEROID_EPIC -> vy = 0.6f
                EnemyKind.TURRET_PHOTON, EnemyKind.TURRET_ROCKET -> {
                    vy = 0.25f
                    if (fireIn <= 0L && ship.alive) {
                        val dx = ship.x - enemy.x
                        val dy = ship.y - enemy.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                        val speed = if (enemy.kind == EnemyKind.TURRET_ROCKET) 3.2f else 4f
                        bolts += Bolt(enemy.x, enemy.y + 10f, dx / dist * speed, dy / dist * speed, enemy.kind.payload, false)
                        fireIn = 1_800L
                    }
                }
            }
            val nx = enemy.x + vx
            val ny = enemy.y + vy
            if (ny > VIEW_H + 60f || nx < -80f || nx > state.playAreaWidth + 80f) continue
            val moved = enemy.copy(x = nx, y = ny, vx = vx, vy = vy, fireInMs = fireIn, rotation = enemy.rotation + 0.06f)
            if (ship.alive && ship.invincibleMs <= 0L && ship.collisionPenaltyMs <= 0L) {
                val dx = nx - ship.x
                val dy = ny - ship.y
                val radius = enemy.kind.radius + 7f
                if (dx * dx + dy * dy <= radius * radius) {
                    if (ship.shields > 0f) {
                        ship = ship.copy(
                            shields = (ship.shields - enemy.kind.payload).coerceAtLeast(0f),
                            shieldDelayMs = statsShieldDelay(state),
                            collisionPenaltyMs = COLLISION_PENALTY_MS,
                        )
                        events += SbEvent.PLAYER_HIT
                        val hp = moved.hp - 25f
                        if (hp <= 0f) {
                            events += SbEvent.EXPLODE
                        } else {
                            updated += moved.copy(hp = hp)
                        }
                        continue
                    } else {
                        dead = true
                    }
                }
            }
            updated += moved
        }

        var after = state.copy(enemies = updated, bolts = bolts, ship = ship, events = events)
        if (dead && ship.alive) {
            events += SbEvent.PLAYER_DIE
            val remainingLives = state.lives - 1
            after = if (state.mode == GameMode.RACE) {
                after.copy(ship = ship.copy(alive = false, respawnMs = 1_000L))
            } else if (remainingLives <= 0) {
                after.copy(lives = 0, ship = ship.copy(alive = false), gameOver = true, events = events + SbEvent.GAME_OVER)
            } else {
                after.copy(lives = remainingLives, ship = ship.copy(alive = false, respawnMs = 1_000L))
            }
        }
        return after
    }

    private fun updateBoss(state: SbState): SbState {
        val boss = state.boss ?: return state
        val events = state.events.toMutableList()
        val bolts = state.bolts.toMutableList()
        var b = boss
        if (b.y < b.entryY) {
            b = b.copy(y = (b.y + 0.8f).coerceAtMost(b.entryY))
        } else {
            var x = b.x + b.dir * 0.7f
            var dir = b.dir
            if (x < 40f) { x = 40f; dir = 1f }
            if (x > state.playAreaWidth - 40f) { x = state.playAreaWidth - 40f; dir = -1f }
            val attackMs = (b.attackMs + 1_000L / 60L) % 3_300L
            val exposed = attackMs < 1_800L
            var fireIn = b.fireInMs - 1_000L / 60L
            if (exposed && fireIn <= 0L && state.ship.alive) {
                for (i in -1..1) {
                    val dx = state.ship.x - x + i * 26f
                    val dy = state.ship.y - b.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                    bolts += Bolt(x, b.y + 20f, dx / dist * 3.6f, dy / dist * 3.6f, 50f, false)
                }
                fireIn = 1_200L
            }
            b = b.copy(x = x, dir = dir, attackMs = attackMs, exposed = exposed, fireInMs = fireIn)
        }
        if (b.y >= b.entryY && b.exposed && state.ship.alive) {
            val dx = b.x - state.ship.x
            val dy = b.y - state.ship.y
            val radius = bossRadius(b.kind) + 8f
            if (dx * dx + dy * dy <= radius * radius && state.ship.invincibleMs <= 0L && state.ship.collisionPenaltyMs <= 0L) {
                val ship = state.ship
                if (ship.shields > 0f) {
                    return state.copy(
                        boss = b,
                        bolts = bolts,
                        ship = ship.copy(
                            shields = (ship.shields - 100f).coerceAtLeast(0f),
                            shieldDelayMs = statsShieldDelay(state),
                            collisionPenaltyMs = COLLISION_PENALTY_MS,
                        ),
                        events = events + SbEvent.PLAYER_HIT,
                    )
                }
            }
        }
        return state.copy(boss = b, bolts = bolts, events = events)
    }

    private fun updatePickups(state: SbState): SbState {
        if (state.pickups.isEmpty()) return state
        val events = state.events.toMutableList()
        var ship = state.ship
        var lives = state.lives
        var ammo = state.ammo
        var zaurium = state.zaurium
        var raceTime = state.raceTimeMs
        val remaining = mutableListOf<Pickup>()
        for (pickup in state.pickups) {
            val y = pickup.y + pickup.vy * (1_000f / 60f)
            if (y > VIEW_H + 40f) continue
            val moved = pickup.copy(y = y)
            val dx = moved.x - ship.x
            val dy = moved.y - ship.y
            if (ship.alive && dx * dx + dy * dy <= 400f) {
                when (moved.kind) {
                    PowerUpKind.SHIELDS -> {
                        ship = ship.copy(
                            shields = (ship.maxShields * 2f).coerceAtMost(ship.maxShields * 2f),
                            shieldDelayMs = 2_500f,
                        )
                        events += SbEvent.PICKUP
                    }

                    PowerUpKind.UPGRADE -> {
                        ship = ship.copy(primaryLevel = (ship.primaryLevel + 1).coerceAtMost(4))
                        events += SbEvent.PICKUP
                    }

                    PowerUpKind.AMMO -> {
                        ammo = (ammo + 1).coerceAtMost(maxAmmo(state.difficulty))
                        events += SbEvent.PICKUP
                    }

                    PowerUpKind.ZAURIUM -> {
                        zaurium += 1
                        events += SbEvent.PICKUP
                    }

                    PowerUpKind.EXTRA_LIFE -> {
                        lives = (lives + 1).coerceAtMost(MAX_LIVES)
                        events += SbEvent.EXTRA_LIFE
                    }

                    PowerUpKind.TIME -> {
                        if (state.mode == GameMode.RACE) raceTime = (raceTime - 2_000L).coerceAtLeast(0L)
                        events += SbEvent.PICKUP
                    }
                }
                continue
            }
            remaining += moved
        }
        return state.copy(
            ship = ship,
            lives = lives,
            ammo = ammo,
            zaurium = zaurium,
            raceTimeMs = raceTime,
            pickups = remaining,
            events = events,
        )
    }

    private fun spawnFromScript(state: SbState): SbState {
        var s = state
        val target = stage(s.stage)
        val multiplier = when (s.difficulty) {
            Difficulty.NORMAL -> 1f
            Difficulty.HARD -> 1.25f
            Difficulty.ELITE -> 1.5f
        }
        var enemies = s.enemies
        var index = s.scriptIndex
        while (index < target.spawns.size && s.stageTimeMs >= target.spawns[index].atMs) {
            val spawn = target.spawns[index]
            for (i in 0 until spawn.count.coerceAtLeast(1)) {
                val offset = (i - (spawn.count - 1) / 2f) * 26f
                val x = (spawn.x + offset).coerceIn(20f, s.playAreaWidth - 20f)
                enemies = enemies + Enemy(
                    kind = spawn.kind,
                    x = x,
                    y = -20f - i * 6f,
                    hp = (spawn.kind.hp + spawn.hpBonus) * multiplier,
                )
            }
            index++
        }
        s = s.copy(enemies = enemies, scriptIndex = index)
        if (!s.bossSpawned && s.boss == null && target.bossKind != null && s.stageTimeMs >= target.bossAtMs) {
            val hp = target.bossHp * multiplier
            s = s.copy(
                boss = Boss(
                    kind = target.bossKind,
                    x = s.playAreaWidth / 2f,
                    y = -60f,
                    hp = hp,
                    maxHp = hp,
                ),
                bossSpawned = true,
            )
        }
        return s
    }

    private fun updateRace(state: SbState, stats: SbStats): SbState {
        if (state.mode != GameMode.RACE || state.raceFinished) return state
        val track = race(state.race)
        var s = state
        val overdrive = s.overdriveMs > 0L
        var speed = 3f + 0.12f * stats.speed
        if (overdrive) speed *= 1.5f
        var distance = s.distancePx + speed
        var overdriveMs = (s.overdriveMs - 1_000L / 60L).coerceAtLeast(0L)
        var gates = s.gatesPassed
        val events = s.events.toMutableList()
        while (gates < track.gates.size && distance >= track.gates[gates]) {
            overdriveMs = 2_000L
            gates++
            events += SbEvent.UI
        }
        var samples = s.ghostSamples
        val sampleAt = (s.raceTimeMs / 250L) * 250L
        if (samples.isEmpty() || samples.last().tMs < sampleAt) {
            samples = samples + GhostSample(sampleAt, distance, s.ship.x)
        }
        var finished = s.raceFinished
        var won = s.raceWon
        if (distance >= track.lengthPx) {
            distance = track.lengthPx
            finished = true
            won = s.ghost == null || s.raceTimeMs <= s.ghost.finishMs
            events += if (won) SbEvent.RACE_WIN else SbEvent.RACE_LOSE
        }
        return s.copy(
            distancePx = distance,
            overdriveMs = overdriveMs,
            gatesPassed = gates,
            ghostSamples = samples,
            raceFinished = finished,
            raceWon = won,
            events = events,
        )
    }

    private fun settle(state: SbState): SbState {
        if (state.gameOver || state.raceFinished || state.victory) return state
        val target = stage(state.stage)
        val bossRequirementMet = target.bossKind == null || (state.bossSpawned && state.boss == null)
        if (state.scriptIndex >= target.spawns.size && state.enemies.isEmpty() && bossRequirementMet && state.boss == null) {
            return if (state.stage >= NUM_STAGES) {
                state.copy(victory = true, events = state.events + SbEvent.STAGE_CLEAR)
            } else {
                state.copy(stageComplete = true, events = state.events + SbEvent.STAGE_CLEAR)
            }
        }
        return state
    }

    fun bossRadius(kind: String): Float = when (kind) {
        "scout" -> 26f
        "wasp" -> 30f
        "hornet" -> 32f
        "redeye" -> 34f
        "telson" -> 36f
        "orbital" -> 38f
        "general" -> 40f
        "angel" -> 40f
        "ram" -> 42f
        else -> 46f
    }

    private fun rngNext(seed: Int): Pair<Int, Int> {
        var s = seed * 1103515245 + 12345
        if (s == 0) s = DEFAULT_SEED
        return s to ((s ushr 16) and 0x7FFF)
    }

    private fun rngUnit(seed: Int): Pair<Int, Float> {
        val (next, value) = rngNext(seed)
        return next to value / 32768f
    }

    private fun rngBound(seed: Int, bound: Int): Pair<Int, Int> {
        val (next, value) = rngNext(seed)
        return next to (value % bound.coerceAtLeast(1))
    }

    private fun pickIndex(seed: Int, bound: Int): Int = (rngNext(seed).second % bound.coerceAtLeast(1))
}
