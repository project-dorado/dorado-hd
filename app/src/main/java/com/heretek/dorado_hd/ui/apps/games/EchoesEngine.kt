package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round

/**
 * Echoes model (W6, official `Echoes.exe`). Clean-room re-derivation from
 * docs/apps/echoes.md: collect every crystal in a tile arena while ghosts
 * re-simulate the player's own recorded input history. All twelve arenas are
 * original layouts, generated from authored wall plans and validated for a
 * radius-21 puck. Deterministic 60 Hz fixed step, seeded LCG, no Android deps.
 */
enum class EchoesDifficulty(val label: String, val lives: Int, val echoSpeed: Double, val flyerSpeed: Double) {
    EASY("easy", 5, 0.85, 0.75),
    MEDIUM("medium", 3, 1.0, 1.0),
    HARD("hard", 2, 1.25, 1.3),
}

enum class EchoesMode { CAMPAIGN, ARCADE, SURVIVAL, CANDY, HIPPY }

enum class EchoesStatus { PLAYING, WON, LOST }

enum class EchoKind { ECHO, FLYER, RISER }

enum class EchoesPowerup { NONE, TIME_FREEZE, PLAYER_GHOST, EXTRA_LIFE, PULSE_RING, MULTI_RING, SHRINK, CRYSTAL_MAGNET, SECRET, STENCH }

enum class EchoesEvent { COLLECT, POWERUP, ECHO_SPAWN, HIT, DEAD, WIN, ROUND, PULSE, RESPAWN }

enum class EchoesAchievement { COOL_CAT, SPEED_DEMON, TREASURE_SEEKER, ECHO_CENTURION, CHAMPION }

enum class EchoesRank { NONE, BRONZE, SILVER, GOLD, JEWEL }

/** Quantised 8-way movement command; the history of these is the echo path. */
data class EchoesInput(val dx: Int = 0, val dy: Int = 0) {
    val code: Int get() = (dx + 1) + (dy + 1) * 3

    companion object {
        val NONE = EchoesInput()

        fun fromCode(code: Int): EchoesInput {
            val c = code.coerceIn(0, 8)
            return EchoesInput(c % 3 - 1, c / 3 - 1)
        }

        fun fromVector(dx: Double, dy: Double, deadZone: Double = 0.3): EchoesInput {
            val length = hypot(dx, dy)
            if (length < deadZone) return NONE
            val octant = round(atan2(dy, dx) / (Math.PI / 4.0)).toInt()
            val dirs = listOf(
                1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1,
            )
            val (ex, ey) = dirs[((octant % 8) + 8) % 8]
            return EchoesInput(ex, ey)
        }
    }
}

data class EchoesPoint(val x: Double, val y: Double)

data class EchoesArena(
    val id: Int,
    val name: String,
    val cols: Int,
    val rows: Int,
    val walls: List<Boolean>,
    val spikes: List<Boolean>,
    val spawn: EchoesPoint,
    val crystals: List<EchoesPoint>,
    val flyers: List<EchoesPoint>,
    val risers: List<EchoesPoint>,
    val floorCells: List<EchoesPoint>,
) {
    fun wallAt(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && walls[row * cols + col]

    fun spikeAt(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && spikes[row * cols + col]
}

data class EchoesCrystal(
    val id: Int,
    val x: Double,
    val y: Double,
    val powerup: EchoesPowerup,
    val collected: Boolean = false,
    val secret: Boolean = false,
    val value: Int = 1,
)

data class EchoesEnemy(
    val id: Int,
    val kind: EchoKind,
    val x: Double,
    val y: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val spawnTick: Long = 0L,
    val replayIndex: Int = 0,
    val phase: Double = 0.0,
    val baseX: Double = 0.0,
    val baseY: Double = 0.0,
    val amplitude: Double = 0.0,
    val speed: Double = 1.0,
    val alive: Boolean = true,
) {
    val radius: Double get() = if (kind == EchoKind.ECHO) EchoesEngine.ECHO_RADIUS else EchoesEngine.HAZARD_RADIUS
}

data class EchoesPlayer(
    val x: Double,
    val y: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val scale: Double = 1.0,
)

data class EchoesEffects(
    val invulnMs: Double = 0.0,
    val freezeMs: Double = 0.0,
    val ghostMs: Double = 0.0,
    val shrinkMs: Double = 0.0,
    val magnetMs: Double = 0.0,
    val stenchMs: Double = 0.0,
    val pulseMs: Double = 0.0,
)

data class EchoesGame(
    val mode: EchoesMode,
    val difficulty: EchoesDifficulty,
    val levelIndex: Int,
    val arena: EchoesArena,
    val seed: Int,
    val player: EchoesPlayer,
    val spawn: EchoesPoint,
    val crystals: List<EchoesCrystal>,
    val enemies: List<EchoesEnemy>,
    val inputs: List<EchoesInput>,
    val effects: EchoesEffects = EchoesEffects(),
    val tick: Long = 0L,
    val elapsedMs: Double = 0.0,
    val accumulatorMs: Double = 0.0,
    val countdownMs: Double = 0.0,
    val score: Int = 0,
    val lives: Int = 3,
    val round: Int = 1,
    val nextEnemyId: Int = 0,
    val rngState: Int = 0,
    val hippyTimerMs: Double = 0.0,
    val trail: List<EchoesPoint> = emptyList(),
    val status: EchoesStatus = EchoesStatus.PLAYING,
    val events: List<EchoesEvent> = emptyList(),
) {
    val totalCrystals: Int get() = crystals.count { !it.secret }
    val collectedCrystals: Int get() = crystals.count { !it.secret && it.collected }
}

data class EchoesProgress(
    val unlocked: Int = 0,
    val best: Map<Int, Int> = emptyMap(),
    val totalCrystals: Int = 0,
    val echoesSpawned: Int = 0,
    val campaignCleared: Boolean = false,
)

private data class RawArena(val name: String, val rows: List<String>)

private val POWERUP_TABLE = listOf(
    EchoesPowerup.TIME_FREEZE,
    EchoesPowerup.PLAYER_GHOST,
    EchoesPowerup.PULSE_RING,
    EchoesPowerup.SHRINK,
    EchoesPowerup.CRYSTAL_MAGNET,
    EchoesPowerup.MULTI_RING,
    EchoesPowerup.EXTRA_LIFE,
    EchoesPowerup.STENCH,
    EchoesPowerup.SECRET,
)

object EchoesArenas {

    private val DRUMLIN = listOf(
        "##############################",
        "#............................#",
        "#.................f..........#",
        "#............................#",
        "#............................#",
        "#...##########...............#",
        "#...##########...............#",
        "#.....................*......#",
        "#............................#",
        "#.............*..............#",
        "#...............##########...#",
        "#...............##########.*.#",
        "#............................#",
        "#..s.....*...................#",
        "#............................#",
        "#............................#",
        "##############################",
    )

    private val CAIRN = listOf(
        "##############################",
        "#............................#",
        "#.*..##.............##.......#",
        "#....##.............##.......#",
        "#....##.............##.......#",
        "#..f.##.............##.......#",
        "#....##.............##.......#",
        "#.*..##.............##.......#",
        "#............................#",
        "#........##.##...........##..#",
        "#.....*..##.##...........##..#",
        "#........##.##...........##..#",
        "#........##.##...........##..#",
        "#..s........##...........##..#",
        "#.....*...*.##...........##..#",
        "#............................#",
        "##############################",
    )

    private val DELTA = listOf(
        "##############################",
        "#............................#",
        "#............................#",
        "#.....######.................#",
        "#.....######.*......*........#",
        "#......................*.....#",
        "#.###........######..........#",
        "#.###........######.......*..#",
        "#............................#",
        "#.....######........######...#",
        "#.....######........######...#",
        "#............................#",
        "#..............*..f..........#",
        "#..s....*....................#",
        "#............................#",
        "#............................#",
        "##############################",
    )

    private val FJORD = listOf(
        "##############################",
        "#....##..........##..........#",
        "#....##..........##..........#",
        "#.*..##..*.......##..........#",
        "#....##..........##....##....#",
        "#....##....##....##....##....#",
        "#.f..##....##....##....##....#",
        "#....##....##....##....##.*..#",
        "#....##....##....##....##....#",
        "#..........##..*.##....##....#",
        "#.^........##..........##....#",
        "#......*...##..........##....#",
        "#..........##..........##....#",
        "#..s.......##......*...##....#",
        "#..........##..........##....#",
        "#..........##..........##....#",
        "##############################",
    )

    private val GEYSER = listOf(
        "##############################",
        "#............................#",
        "#...*................*.......#",
        "#............................#",
        "#............................#",
        "#............................#",
        "#...........######...........#",
        "#..r........######...........#",
        "#.........*.######...*....^..#",
        "#...........######...........#",
        "#...........######...........#",
        "#............................#",
        "#..................*.........#",
        "#..s...*.....................#",
        "#......................*.....#",
        "#............................#",
        "##############################",
    )

    private val HARBOR = listOf(
        "##############################",
        "#............................#",
        "#........*.....*.............#",
        "#............................#",
        "#...##....##......##..*.##...#",
        "#...##....##......##....##...#",
        "#.............*..............#",
        "#............................#",
        "#..s.................*.......#",
        "#............*...............#",
        "#............................#",
        "#...##.*..##......##....##...#",
        "#...##....##......##....##...#",
        "#............................#",
        "#.........f...........*......#",
        "#............................#",
        "##############################",
    )

    private val ISTHMUS = listOf(
        "##############################",
        "#............................#",
        "#............................#",
        "#............................#",
        "##############..##############",
        "##############..##############",
        "#............................#",
        "#.......*......*.......*.....#",
        "#..s................*....f*..#",
        "#.....*^..*..................#",
        "#.............*..............#",
        "##############..##############",
        "##############..##############",
        "#............................#",
        "#............................#",
        "#............................#",
        "##############################",
    )

    private val KARST = listOf(
        "##############################",
        "#............................#",
        "#............................#",
        "#..######..######..######....#",
        "#..######..######..######....#",
        "#.........*.......*........*.#",
        "#...*..r.....................#",
        "#........................*...#",
        "#..######..######..######....#",
        "#..######..######..######..*.#",
        "#.........*..................#",
        "#......s.........*...........#",
        "#............................#",
        "#.....######.*.######........#",
        "#.....######...######........#",
        "#............................#",
        "##############################",
    )

    private val LAGOON = listOf(
        "##############################",
        "#............................#",
        "#..######################..*.#",
        "#..######################....#",
        "#......................##....#",
        "#..*.##.#############..##.*..#",
        "#....##.#############..##....#",
        "#....##................##....#",
        "#....##................##....#",
        "#..*.##................##....#",
        "#....##................##.*..#",
        "#....####################.r..#",
        "#....####################....#",
        "#........................f.*.#",
        "#..*...*....s...*.....*......#",
        "#............................#",
        "##############################",
    )

    private val MESA = listOf(
        "##############################",
        "#............................#",
        "#............................#",
        "#............................#",
        "##########..........##########",
        "##########..........##########",
        "#............................#",
        "#...........######...........#",
        "#...........######...........#",
        "#............................#",
        "#.............*..............#",
        "##########.*....*f..##########",
        "##########...*....*.##########",
        "#............................#",
        "#.*....s..*...*......*....*..#",
        "#............................#",
        "##############################",
    )

    private val NOTCH = listOf(
        "##############################",
        "#............................#",
        "#.*...*........*.........f...#",
        "#............................#",
        "#...#########....#########...#",
        "#...#########....#########...#",
        "#..*.........................#",
        "#......s......*..............#",
        "#....................*.......#",
        "#...#######.*.#######...##...#",
        "#...#######...#######...##.*.#",
        "#.....................*......#",
        "#..............*.............#",
        "#......*........r.......*....#",
        "#............................#",
        "#............................#",
        "##############################",
    )

    private val OXBOW = listOf(
        "##############################",
        "#............................#",
        "#..##..######...######....##.#",
        "#..##..######...######....##.#",
        "#..##.....................##.#",
        "#..##.*...................##.#",
        "#..##...##...........##...##.#",
        "#..##.*.##...........##...##.#",
        "#..##...##...#####...##...##.#",
        "#....*..##...#####...##......#",
        "#.*..r*.##...........##......#",
        "#...*...##...........##......#",
        "#.*...*.##...........##......#",
        "#..s...*...######.......######",
        "#.*..*.f.*.######.......######",
        "#............................#",
        "##############################",
    )

    val ALL: List<EchoesArena> = listOf(
        RawArena("drumlin", DRUMLIN),
        RawArena("cairn", CAIRN),
        RawArena("delta", DELTA),
        RawArena("fjord", FJORD),
        RawArena("geyser", GEYSER),
        RawArena("harbor", HARBOR),
        RawArena("isthmus", ISTHMUS),
        RawArena("karst", KARST),
        RawArena("lagoon", LAGOON),
        RawArena("mesa", MESA),
        RawArena("notch", NOTCH),
        RawArena("oxbow", OXBOW),
    ).mapIndexed { index, raw -> parseArena(index, raw) }

    fun byId(id: Int): EchoesArena = ALL[id.coerceIn(0, ALL.lastIndex)]

    private fun parseArena(id: Int, raw: RawArena): EchoesArena {
        val rows = raw.rows
        val cols = rows.first().length
        require(rows.all { it.length == cols }) { "arena ${raw.name} rows must be equal length" }
        val walls = ArrayList<Boolean>(cols * rows.size)
        val spikes = ArrayList<Boolean>(cols * rows.size)
        val floorCells = ArrayList<EchoesPoint>()
        val crystals = ArrayList<EchoesPoint>()
        val flyers = ArrayList<EchoesPoint>()
        val risers = ArrayList<EchoesPoint>()
        var spawn = EchoesPoint(cols * EchoesEngine.TILE / 2, rows.size * EchoesEngine.TILE / 2)
        for (row in rows.indices) {
            val line = rows[row]
            for (col in line.indices) {
                val ch = line[col]
                val wall = ch == '#'
                walls += wall
                spikes += ch == '^'
                val x = (col + 0.5) * EchoesEngine.TILE
                val y = (row + 0.5) * EchoesEngine.TILE
                if (ch == 's') spawn = EchoesPoint(x, y)
                if (ch == '*') crystals += EchoesPoint(x, y)
                if (ch == 'f') flyers += EchoesPoint(x, y)
                if (ch == 'r') risers += EchoesPoint(x, y)
                if (!wall) floorCells += EchoesPoint(x, y)
            }
        }
        return EchoesArena(
            id = id,
            name = raw.name,
            cols = cols,
            rows = rows.size,
            walls = walls,
            spikes = spikes,
            spawn = spawn,
            crystals = crystals,
            flyers = flyers,
            risers = risers,
            floorCells = floorCells,
        )
    }
}

object EchoesEngine {

    const val TILE = 16.0
    const val ARENA_W = 480.0
    const val ARENA_H = 272.0
    const val TICK_MS = 1000.0 / 60.0
    const val SNAPSHOT_FREQUENCY_S = 0.05
    val SNAPSHOT_TICKS: Int = (SNAPSHOT_FREQUENCY_S * 60.0).toInt().coerceAtLeast(1)
    const val PLAYER_RADIUS = 21.0
    const val MOVE_SPEED = 5.25
    const val MOVE_DAMPEN = 0.7
    const val MIN_SCALE = 0.4
    const val MAX_SCALE = 5.0
    const val ECHO_RADIUS = 16.0
    const val HAZARD_RADIUS = 11.0
    const val CRYSTAL_RADIUS = 9.0
    const val SPIKE_RADIUS = 10.0
    const val STILL_SAFE_TIME_MS = 100.0
    const val STILL_SAFE_AFTER_HIT_MS = 250.0
    const val HIT_KNOCKBACK = 11.0
    const val TIME_FREEZE_MS = 30_000.0 / 7.0
    const val PLAYER_GHOST_MS = 40_000.0 / 7.0
    const val SHRINK_MS = 80_000.0 / 7.0
    const val PULSE_RING_MS = 500.0
    const val SHRINK_SCALE = 0.4
    const val MAGNET_MS = 5000.0
    const val STENCH_MS = 4000.0
    const val MAGNET_SPEED = 2.4
    const val MAGNET_RANGE = 72.0
    const val MULTI_RING_RANGE = 96.0
    const val PULSE_RANGE = 110.0
    const val PULSE_KILL_RANGE = 76.0
    const val SECRET_VALUE = 2
    const val MAX_ECHOES = 32
    const val HIPPY_INTERVAL_MS = 8000.0
    const val CAMPAIGN_BONUS_MS = 1500.0
    const val MAX_INPUT_HISTORY = 36_000

    fun newGame(
        mode: EchoesMode,
        difficulty: EchoesDifficulty,
        seed: Int,
        levelIndex: Int = 0,
        countdownMs: Double = 0.0,
    ): EchoesGame {
        val arena = EchoesArenas.byId(levelIndex)
        var rng = seed
        val crystals = ArrayList<EchoesCrystal>()
        arena.crystals.forEachIndexed { index, point ->
            val carries = index % 4 == 3
            val powerup = if (carries) {
                val roll = nextRandom(rng)
                rng = roll.first
                POWERUP_TABLE[roll.second % POWERUP_TABLE.size]
            } else {
                EchoesPowerup.NONE
            }
            crystals += EchoesCrystal(index, point.x, point.y, powerup)
        }
        val enemies = ArrayList<EchoesEnemy>()
        var enemyId = 0
        arena.flyers.forEach { point ->
            enemies += EchoesEnemy(
                id = enemyId++,
                kind = EchoKind.FLYER,
                x = point.x,
                y = point.y,
                baseX = point.x,
                baseY = point.y,
                amplitude = 28.0,
                speed = 1.0,
                phase = point.x * 0.01,
            )
        }
        arena.risers.forEach { point ->
            enemies += EchoesEnemy(
                id = enemyId++,
                kind = EchoKind.RISER,
                x = point.x,
                y = point.y,
                baseX = point.x,
                baseY = point.y,
                amplitude = 34.0,
                speed = 1.35,
                phase = point.y * 0.013,
            )
        }
        return EchoesGame(
            mode = mode,
            difficulty = difficulty,
            levelIndex = levelIndex,
            arena = arena,
            seed = seed,
            player = EchoesPlayer(arena.spawn.x, arena.spawn.y),
            spawn = arena.spawn,
            crystals = crystals,
            enemies = enemies,
            inputs = emptyList(),
            countdownMs = countdownMs,
            lives = difficulty.lives,
            nextEnemyId = enemyId,
            rngState = rng,
        )
    }

    fun step(game: EchoesGame, dtMs: Double, input: EchoesInput = EchoesInput.NONE): EchoesGame {
        if (game.status != EchoesStatus.PLAYING) return game
        val clamp = dtMs.coerceIn(0.0, 100.0)
        var accumulator = game.accumulatorMs + clamp
        var state = game.copy(accumulatorMs = 0.0, events = emptyList())
        val events = ArrayList<EchoesEvent>()
        while (accumulator >= TICK_MS) {
            accumulator -= TICK_MS
            state = tick(state, input, events)
            if (state.status != EchoesStatus.PLAYING) {
                accumulator = 0.0
                break
            }
        }
        return state.copy(accumulatorMs = accumulator, events = events)
    }

    private fun tick(game: EchoesGame, input: EchoesInput, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.countdownMs > 0.0) {
            val remaining = game.countdownMs - TICK_MS
            return game.copy(countdownMs = remaining.coerceAtLeast(0.0))
        }
        var state = game.copy(
            tick = game.tick + 1,
            elapsedMs = game.elapsedMs + TICK_MS,
            inputs = appendInput(game.inputs, input),
        )
        state = expireEffects(state)
        state = movePlayer(state, input, events)
        state = recordTrail(state)
        state = moveEnemies(state, events)
        state = applyMagnet(state, events)
        state = collectCrystals(state, events)
        state = collideEnemies(state, events)
        state = tickPulse(state, events)
        state = tickHippy(state, events)
        state = checkRoundRules(state, events)
        return state
    }

    private fun appendInput(inputs: List<EchoesInput>, input: EchoesInput): List<EchoesInput> {
        if (inputs.size >= MAX_INPUT_HISTORY) return inputs
        return inputs + input
    }

    private fun expireEffects(game: EchoesGame): EchoesGame {
        val e = game.effects
        val shrink = (e.shrinkMs - TICK_MS).coerceAtLeast(0.0)
        val player = game.player.copy(scale = if (shrink > 0.0) SHRINK_SCALE else 1.0)
        return game.copy(
            player = player,
            effects = e.copy(
                invulnMs = (e.invulnMs - TICK_MS).coerceAtLeast(0.0),
                freezeMs = (e.freezeMs - TICK_MS).coerceAtLeast(0.0),
                ghostMs = (e.ghostMs - TICK_MS).coerceAtLeast(0.0),
                shrinkMs = shrink,
                magnetMs = (e.magnetMs - TICK_MS).coerceAtLeast(0.0),
                stenchMs = (e.stenchMs - TICK_MS).coerceAtLeast(0.0),
                pulseMs = (e.pulseMs - TICK_MS).coerceAtLeast(0.0),
            ),
        )
    }

    private fun movePlayer(game: EchoesGame, input: EchoesInput, events: MutableList<EchoesEvent>): EchoesGame {
        val player = game.player
        val norm = if (input.dx != 0 && input.dy != 0) 0.70710678 else 1.0
        val targetX = input.dx * MOVE_SPEED * norm
        val targetY = input.dy * MOVE_SPEED * norm
        var vx = player.vx * MOVE_DAMPEN + targetX * (1.0 - MOVE_DAMPEN)
        var vy = player.vy * MOVE_DAMPEN + targetY * (1.0 - MOVE_DAMPEN)
        val radius = PLAYER_RADIUS * player.scale
        val movedX = moveAxisX(game.arena, player.x, player.y, vx, radius)
        var x = movedX
        if (abs(movedX - (player.x + vx)) > 1e-9) vx = 0.0
        val movedY = moveAxisY(game.arena, x, player.y, vy, radius)
        var y = movedY
        if (abs(movedY - (player.y + vy)) > 1e-9) vy = 0.0
        return game.copy(player = player.copy(x = x, y = y, vx = vx, vy = vy))
    }

    private fun recordTrail(game: EchoesGame): EchoesGame {
        if (game.tick % SNAPSHOT_TICKS != 0L) return game
        val trail = game.trail + EchoesPoint(game.player.x, game.player.y)
        return game.copy(trail = if (trail.size > 60) trail.takeLast(60) else trail)
    }

    private fun moveEnemies(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.effects.freezeMs > 0.0) return game
        val stench = game.effects.stenchMs > 0.0
        val speedBoost = 1.0 + 0.08 * (game.round - 1)
        val updated = game.enemies.map { enemy ->
            if (!enemy.alive) return@map enemy
            when (enemy.kind) {
                EchoKind.ECHO -> {
                    val history = game.inputs
                    val replayLength = enemy.replayLength(history.size)
                    val nextIndex = enemy.replayIndex + 1
                    if (replayLength <= 0) {
                        enemy.copy(replayIndex = nextIndex)
                    } else {
                        val replayInput = history[enemy.replayIndex % replayLength]
                        val norm = if (replayInput.dx != 0 && replayInput.dy != 0) 0.70710678 else 1.0
                        val factor = game.difficulty.echoSpeed * (if (stench) 0.5 else 1.0) * speedBoost
                        val targetX = replayInput.dx * MOVE_SPEED * norm * factor
                        val targetY = replayInput.dy * MOVE_SPEED * norm * factor
                        var vx = enemy.vx * MOVE_DAMPEN + targetX * (1.0 - MOVE_DAMPEN)
                        var vy = enemy.vy * MOVE_DAMPEN + targetY * (1.0 - MOVE_DAMPEN)
                        val movedX = moveAxisX(game.arena, enemy.x, enemy.y, vx, ECHO_RADIUS)
                        val x = movedX
                        if (abs(movedX - (enemy.x + vx)) > 1e-9) vx = 0.0
                        val movedY = moveAxisY(game.arena, x, enemy.y, vy, ECHO_RADIUS)
                        val y = movedY
                        if (abs(movedY - (enemy.y + vy)) > 1e-9) vy = 0.0
                        enemy.copy(x = x, y = y, vx = vx, vy = vy, replayIndex = nextIndex)
                    }
                }

                EchoKind.FLYER -> {
                    val phase = enemy.phase + 0.028 * game.difficulty.flyerSpeed * speedBoost
                    enemy.copy(
                        phase = phase,
                        x = enemy.baseX + Math.sin(phase) * enemy.amplitude,
                        y = enemy.baseY + Math.cos(phase * 0.8) * enemy.amplitude * 0.55,
                    )
                }

                EchoKind.RISER -> {
                    val phase = enemy.phase + 0.04 * game.difficulty.flyerSpeed * speedBoost
                    enemy.copy(
                        phase = phase,
                        x = enemy.baseX + Math.cos(phase * 1.1) * enemy.amplitude * 0.4,
                        y = enemy.baseY + Math.sin(phase) * enemy.amplitude,
                    )
                }
            }
        }
        return game.copy(enemies = updated)
    }

    private fun EchoesEnemy.replayLength(historySize: Int): Int =
        minOf(spawnTick.toInt(), historySize)

    private fun applyMagnet(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.effects.magnetMs <= 0.0) return game
        val player = game.player
        val crystals = game.crystals.map { crystal ->
            if (crystal.collected) return@map crystal
            val dx = player.x - crystal.x
            val dy = player.y - crystal.y
            val distance = hypot(dx, dy)
            if (distance > MAGNET_RANGE || distance < 1e-6) return@map crystal
            val step = MAGNET_SPEED
            crystal.copy(x = crystal.x + dx / distance * step, y = crystal.y + dy / distance * step)
        }
        return game.copy(crystals = crystals)
    }

    private fun collectCrystals(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        var state = game
        val radius = PLAYER_RADIUS * state.player.scale
        val toCollect = state.crystals.filter { crystal ->
            !crystal.collected && circleOverlap(state.player.x, state.player.y, radius, crystal.x, crystal.y, CRYSTAL_RADIUS)
        }
        for (crystal in toCollect) {
            state = collectCrystal(state, crystal, events)
        }
        return state
    }

    private fun collectCrystal(game: EchoesGame, crystal: EchoesCrystal, events: MutableList<EchoesEvent>): EchoesGame {
        var state = game
        val target = state.crystals.firstOrNull { it.id == crystal.id } ?: return state
        if (target.collected) return state
        state = state.copy(
            crystals = state.crystals.map { if (it.id == target.id) target.copy(collected = true) else it },
            score = state.score + target.value,
        )
        events += EchoesEvent.COLLECT
        if (!target.secret) {
            state = spawnEcho(state, events)
        }
        if (target.powerup != EchoesPowerup.NONE) {
            state = applyPowerup(state, target.powerup, events)
        }
        return state
    }

    private fun spawnEcho(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        val living = game.enemies.count { it.kind == EchoKind.ECHO && it.alive }
        if (living >= MAX_ECHOES) return game
        val spawn = EchoesPoint(game.spawn.x, game.spawn.y)
        val echo = EchoesEnemy(
            id = game.nextEnemyId,
            kind = EchoKind.ECHO,
            x = spawn.x,
            y = spawn.y,
            spawnTick = game.tick,
            replayIndex = 0,
        )
        events += EchoesEvent.ECHO_SPAWN
        return game.copy(enemies = game.enemies + echo, nextEnemyId = game.nextEnemyId + 1)
    }

    private fun applyPowerup(game: EchoesGame, powerup: EchoesPowerup, events: MutableList<EchoesEvent>): EchoesGame {
        var state = game
        val e = state.effects
        state = when (powerup) {
            EchoesPowerup.TIME_FREEZE -> state.copy(effects = e.copy(freezeMs = TIME_FREEZE_MS))
            EchoesPowerup.PLAYER_GHOST -> state.copy(effects = e.copy(ghostMs = PLAYER_GHOST_MS))
            EchoesPowerup.EXTRA_LIFE -> state.copy(lives = state.lives + 1)
            EchoesPowerup.PULSE_RING -> {
                events += EchoesEvent.PULSE
                state.copy(effects = e.copy(pulseMs = PULSE_RING_MS))
            }

            EchoesPowerup.MULTI_RING -> {
                var current = state
                val radius = PLAYER_RADIUS * current.player.scale
                val ids = current.crystals.filter { crystal ->
                    !crystal.collected &&
                        hypot(crystal.x - current.player.x, crystal.y - current.player.y) <= MULTI_RING_RANGE + radius
                }.map { it.id }
                for (id in ids) {
                    val crystal = current.crystals.firstOrNull { it.id == id && !it.collected } ?: continue
                    current = collectCrystal(current, crystal, events)
                }
                current
            }

            EchoesPowerup.SHRINK -> state.copy(
                player = state.player.copy(scale = SHRINK_SCALE),
                effects = e.copy(shrinkMs = SHRINK_MS),
            )

            EchoesPowerup.CRYSTAL_MAGNET -> state.copy(effects = e.copy(magnetMs = MAGNET_MS))
            EchoesPowerup.STENCH -> state.copy(effects = e.copy(stenchMs = STENCH_MS))
            EchoesPowerup.SECRET -> {
                val roll = nextRandom(state.rngState)
                val cell = state.arena.floorCells[roll.second % state.arena.floorCells.size]
                val secret = EchoesCrystal(
                    id = state.crystals.size,
                    x = cell.x,
                    y = cell.y,
                    powerup = EchoesPowerup.NONE,
                    secret = true,
                    value = SECRET_VALUE,
                )
                state.copy(crystals = state.crystals + secret, rngState = roll.first)
            }

            EchoesPowerup.NONE -> state
        }
        events += EchoesEvent.POWERUP
        return state
    }

    private fun collideEnemies(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.effects.invulnMs > 0.0 || game.effects.ghostMs > 0.0) return game
        val radius = PLAYER_RADIUS * game.player.scale
        val spikeHit = game.arena.let { arena ->
            val col = floor(game.player.x / TILE).toInt()
            val row = floor(game.player.y / TILE).toInt()
            var hit = false
            for (r in row - 1..row + 1) {
                for (c in col - 1..col + 1) {
                    if (!arena.spikeAt(c, r)) continue
                    val sx = (c + 0.5) * TILE
                    val sy = (r + 0.5) * TILE
                    if (circleOverlap(game.player.x, game.player.y, radius, sx, sy, SPIKE_RADIUS)) hit = true
                }
            }
            hit
        }
        val enemy = game.enemies.firstOrNull { it.alive && circleOverlap(game.player.x, game.player.y, radius, it.x, it.y, it.radius) }
        if (!spikeHit && enemy == null) return game
        val hits = game.lives - 1
        val source = enemy ?: EchoesEnemy(-1, EchoKind.ECHO, game.player.x, game.player.y)
        val dx = game.player.x - source.x
        val dy = game.player.y - source.y
        val length = hypot(dx, dy)
        val knockX = if (length < 1e-6) 0.0 else dx / length * HIT_KNOCKBACK
        val knockY = if (length < 1e-6) 0.0 else dy / length * HIT_KNOCKBACK
        var state = game.copy(
            lives = hits,
            effects = game.effects.copy(invulnMs = STILL_SAFE_AFTER_HIT_MS),
            player = game.player.copy(vx = knockX, vy = knockY),
            status = if (hits < 0) EchoesStatus.LOST else EchoesStatus.PLAYING,
        )
        events += if (state.status == EchoesStatus.LOST) EchoesEvent.DEAD else EchoesEvent.HIT
        return state
    }

    private fun tickPulse(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.effects.pulseMs <= 0.0) return game
        val progress = 1.0 - game.effects.pulseMs / PULSE_RING_MS
        val radius = PULSE_KILL_RANGE * progress + 10.0
        val enemies = game.enemies.map { enemy ->
            if (enemy.alive && enemy.kind == EchoKind.ECHO &&
                circleOverlap(game.player.x, game.player.y, radius, enemy.x, enemy.y, ECHO_RADIUS)
            ) {
                enemy.copy(alive = false)
            } else {
                enemy
            }
        }
        return game.copy(enemies = enemies)
    }

    private fun tickHippy(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.mode != EchoesMode.HIPPY) return game
        val timer = game.hippyTimerMs - TICK_MS
        if (timer > 0.0) return game.copy(hippyTimerMs = timer)
        val echoed = spawnEcho(game, events)
        return echoed.copy(hippyTimerMs = HIPPY_INTERVAL_MS)
    }

    private fun checkRoundRules(game: EchoesGame, events: MutableList<EchoesEvent>): EchoesGame {
        if (game.status != EchoesStatus.PLAYING) return game
        if (game.totalCrystals == 0 || game.collectedCrystals < game.totalCrystals) return game
        if (game.mode == EchoesMode.CAMPAIGN) {
            events += EchoesEvent.WIN
            return game.copy(status = EchoesStatus.WON, events = emptyList())
        }
        val nextRound = game.round + 1
        val crystals = game.crystals.map { crystal ->
            if (crystal.secret) crystal else crystal.copy(collected = false)
        }
        val bonus = when (game.mode) {
            EchoesMode.CANDY -> 10
            EchoesMode.HIPPY -> 6
            EchoesMode.SURVIVAL -> 4
            else -> 5
        } * game.round
        var state = game.copy(
            round = nextRound,
            crystals = crystals,
            score = game.score + bonus,
            hippyTimerMs = if (game.mode == EchoesMode.HIPPY) HIPPY_INTERVAL_MS else game.hippyTimerMs,
        )
        events += EchoesEvent.ROUND
        if (game.mode == EchoesMode.SURVIVAL || game.mode == EchoesMode.CANDY) {
            val roll = nextRandom(state.rngState)
            val cell = state.arena.floorCells[roll.second % state.arena.floorCells.size]
            state = state.copy(
                rngState = roll.first,
                crystals = state.crystals + EchoesCrystal(
                    id = state.crystals.size,
                    x = cell.x,
                    y = cell.y,
                    powerup = if (game.mode == EchoesMode.CANDY) POWERUP_TABLE[nextRound % POWERUP_TABLE.size] else EchoesPowerup.NONE,
                ),
            )
        }
        return state
    }

    fun totalCrystalCount(game: EchoesGame): Int = game.totalCrystals

    fun collectedCrystalCount(game: EchoesGame): Int = game.collectedCrystals

    fun moveAxisX(arena: EchoesArena, x: Double, y: Double, dx: Double, r: Double): Double {
        if (dx == 0.0) return x
        val nx = x + dx
        val rowMin = floor((y - r) / TILE).toInt().coerceIn(0, arena.rows - 1)
        val rowMax = floor((y + r) / TILE).toInt().coerceIn(0, arena.rows - 1)
        val leadCol = floor((if (dx > 0) nx + r else nx - r) / TILE).toInt()
        if (leadCol < 0 || leadCol >= arena.cols) return x
        for (row in rowMin..rowMax) {
            if (arena.wallAt(leadCol, row)) {
                return if (dx > 0) leadCol * TILE - r else (leadCol + 1) * TILE + r
            }
        }
        return nx
    }

    fun moveAxisY(arena: EchoesArena, x: Double, y: Double, dy: Double, r: Double): Double {
        if (dy == 0.0) return y
        val ny = y + dy
        val colMin = floor((x - r) / TILE).toInt().coerceIn(0, arena.cols - 1)
        val colMax = floor((x + r) / TILE).toInt().coerceIn(0, arena.cols - 1)
        val leadRow = floor((if (dy > 0) ny + r else ny - r) / TILE).toInt()
        if (leadRow < 0 || leadRow >= arena.rows) return y
        for (col in colMin..colMax) {
            if (arena.wallAt(col, leadRow)) {
                return if (dy > 0) leadRow * TILE - r else (leadRow + 1) * TILE + r
            }
        }
        return ny
    }

    fun circleOverlap(ax: Double, ay: Double, ar: Double, bx: Double, by: Double, br: Double): Boolean =
        hypot(ax - bx, ay - by) <= ar + br

    fun nextRandom(state: Int): Pair<Int, Int> {
        val next = state * 1103515245 + 12345
        return next to ((next ushr 16) and 0x7FFF)
    }

    fun achievementRank(achievement: EchoesAchievement, progress: EchoesProgress): EchoesRank = when (achievement) {
        EchoesAchievement.COOL_CAT -> when {
            progress.totalCrystals >= 400 -> EchoesRank.JEWEL
            progress.totalCrystals >= 250 -> EchoesRank.GOLD
            progress.totalCrystals >= 120 -> EchoesRank.SILVER
            progress.totalCrystals >= 40 -> EchoesRank.BRONZE
            else -> EchoesRank.NONE
        }

        EchoesAchievement.SPEED_DEMON -> when {
            progress.best.values.any { it >= 3 } -> EchoesRank.GOLD
            progress.best.values.any { it >= 2 } -> EchoesRank.SILVER
            progress.best.values.any { it >= 1 } -> EchoesRank.BRONZE
            else -> EchoesRank.NONE
        }

        EchoesAchievement.TREASURE_SEEKER -> when {
            progress.totalCrystals >= 300 -> EchoesRank.JEWEL
            progress.totalCrystals >= 180 -> EchoesRank.GOLD
            progress.totalCrystals >= 90 -> EchoesRank.SILVER
            progress.totalCrystals >= 30 -> EchoesRank.BRONZE
            else -> EchoesRank.NONE
        }

        EchoesAchievement.ECHO_CENTURION -> when {
            progress.echoesSpawned >= 300 -> EchoesRank.JEWEL
            progress.echoesSpawned >= 150 -> EchoesRank.GOLD
            progress.echoesSpawned >= 60 -> EchoesRank.SILVER
            progress.echoesSpawned >= 20 -> EchoesRank.BRONZE
            else -> EchoesRank.NONE
        }

        EchoesAchievement.CHAMPION -> when {
            progress.campaignCleared -> EchoesRank.JEWEL
            progress.unlocked >= 10 -> EchoesRank.GOLD
            progress.unlocked >= 6 -> EchoesRank.SILVER
            progress.unlocked >= 3 -> EchoesRank.BRONZE
            else -> EchoesRank.NONE
        }
    }

    fun encodeProgress(progress: EchoesProgress): String = buildString {
        append(progress.unlocked)
        append(';')
        append(progress.totalCrystals)
        append(';')
        append(progress.echoesSpawned)
        append(';')
        append(if (progress.campaignCleared) 1 else 0)
        append(';')
        append(progress.best.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
    }

    fun decodeProgress(blob: String?): EchoesProgress {
        if (blob.isNullOrBlank()) return EchoesProgress()
        return try {
            val parts = blob.split(';')
            val unlocked = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val totalCrystals = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val echoesSpawned = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val campaignCleared = parts.getOrNull(3) == "1"
            val best = parts.getOrNull(4)?.split(',')?.mapNotNull { entry ->
                val bits = entry.split(':')
                val key = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val value = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                key to value
            }?.toMap() ?: emptyMap()
            EchoesProgress(unlocked, best, totalCrystals, echoesSpawned, campaignCleared)
        } catch (_: Exception) {
            EchoesProgress()
        }
    }

    fun encode(game: EchoesGame): String {
        val p = game.player
        val e = game.effects
        val crystals = game.crystals.joinToString(";") { crystal ->
            listOf(
                crystal.id,
                fmt(crystal.x),
                fmt(crystal.y),
                crystal.powerup.ordinal,
                if (crystal.collected) 1 else 0,
                if (crystal.secret) 1 else 0,
                crystal.value,
            ).joinToString(",")
        }
        val enemies = game.enemies.joinToString(";") { enemy ->
            listOf(
                enemy.id,
                enemy.kind.ordinal,
                fmt(enemy.x),
                fmt(enemy.y),
                fmt(enemy.vx),
                fmt(enemy.vy),
                enemy.spawnTick,
                enemy.replayIndex,
                fmt(enemy.phase),
                fmt(enemy.baseX),
                fmt(enemy.baseY),
                fmt(enemy.amplitude),
                fmt(enemy.speed),
                if (enemy.alive) 1 else 0,
            ).joinToString(",")
        }
        return listOf(
            "e1",
            game.mode.ordinal,
            game.difficulty.ordinal,
            game.levelIndex,
            game.seed,
            game.status.ordinal,
            game.score,
            game.lives,
            game.round,
            game.tick,
            fmt(game.elapsedMs),
            fmt(game.accumulatorMs),
            fmt(game.countdownMs),
            fmt(game.rngState.toDouble()),
            fmt(game.hippyTimerMs),
            fmt(game.spawn.x),
            fmt(game.spawn.y),
            listOf(fmt(p.x), fmt(p.y), fmt(p.vx), fmt(p.vy), fmt(p.scale)).joinToString(","),
            listOf(
                fmt(e.invulnMs), fmt(e.freezeMs), fmt(e.ghostMs), fmt(e.shrinkMs),
                fmt(e.magnetMs), fmt(e.stenchMs), fmt(e.pulseMs),
            ).joinToString(","),
            crystals,
            enemies,
            encodeInputs(game.inputs),
        ).joinToString("|")
    }

    fun decode(blob: String): EchoesGame? {
        return try {
            val parts = blob.split('|')
            if (parts.size < 22 || parts[0] != "e1") return null
            val mode = EchoesMode.entries[parts[1].toInt()]
            val difficulty = EchoesDifficulty.entries[parts[2].toInt()]
            val levelIndex = parts[3].toInt()
            val arena = EchoesArenas.byId(levelIndex)
            val seed = parts[4].toInt()
            val status = EchoesStatus.entries[parts[5].toInt()]
            val playerBits = parts[17].split(',')
            val effectBits = parts[18].split(',')
            val player = EchoesPlayer(
                x = playerBits[0].toDouble(),
                y = playerBits[1].toDouble(),
                vx = playerBits[2].toDouble(),
                vy = playerBits[3].toDouble(),
                scale = playerBits[4].toDouble(),
            )
            val effects = EchoesEffects(
                invulnMs = effectBits[0].toDouble(),
                freezeMs = effectBits[1].toDouble(),
                ghostMs = effectBits[2].toDouble(),
                shrinkMs = effectBits[3].toDouble(),
                magnetMs = effectBits[4].toDouble(),
                stenchMs = effectBits[5].toDouble(),
                pulseMs = effectBits[6].toDouble(),
            )
            val crystals = parts[19].split(';').filter { it.isNotBlank() }.map { entry ->
                val bits = entry.split(',')
                EchoesCrystal(
                    id = bits[0].toInt(),
                    x = bits[1].toDouble(),
                    y = bits[2].toDouble(),
                    powerup = EchoesPowerup.entries[bits[3].toInt()],
                    collected = bits[4] == "1",
                    secret = bits[5] == "1",
                    value = bits[6].toInt(),
                )
            }
            val enemies = parts[20].split(';').filter { it.isNotBlank() }.map { entry ->
                val bits = entry.split(',')
                EchoesEnemy(
                    id = bits[0].toInt(),
                    kind = EchoKind.entries[bits[1].toInt()],
                    x = bits[2].toDouble(),
                    y = bits[3].toDouble(),
                    vx = bits[4].toDouble(),
                    vy = bits[5].toDouble(),
                    spawnTick = bits[6].toLong(),
                    replayIndex = bits[7].toInt(),
                    phase = bits[8].toDouble(),
                    baseX = bits[9].toDouble(),
                    baseY = bits[10].toDouble(),
                    amplitude = bits[11].toDouble(),
                    speed = bits[12].toDouble(),
                    alive = bits[13] == "1",
                )
            }
            EchoesGame(
                mode = mode,
                difficulty = difficulty,
                levelIndex = levelIndex,
                arena = arena,
                seed = seed,
                player = player,
                spawn = EchoesPoint(parts[15].toDouble(), parts[16].toDouble()),
                crystals = crystals,
                enemies = enemies,
                inputs = decodeInputs(parts[21]),
                effects = effects,
                tick = parts[9].toLong(),
                elapsedMs = parts[10].toDouble(),
                accumulatorMs = parts[11].toDouble(),
                countdownMs = parts[12].toDouble(),
                score = parts[6].toInt(),
                lives = parts[7].toInt(),
                round = parts[8].toInt(),
                nextEnemyId = (enemies.maxOfOrNull { it.id } ?: -1) + 1,
                rngState = parts[13].toDouble().toInt(),
                hippyTimerMs = parts[14].toDouble(),
                status = status,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fmt(value: Double): String = (round(value * 100.0) / 100.0).toString()

    private fun encodeInputs(inputs: List<EchoesInput>): String {
        if (inputs.isEmpty()) return ""
        val builder = StringBuilder()
        var current = inputs[0]
        var count = 1
        for (index in 1 until inputs.size) {
            val next = inputs[index]
            if (next == current) {
                count++
            } else {
                builder.append(current.code).append(':').append(count.toString(36)).append(';')
                current = next
                count = 1
            }
        }
        builder.append(current.code).append(':').append(count.toString(36))
        return builder.toString()
    }

    private fun decodeInputs(blob: String): List<EchoesInput> {
        if (blob.isBlank()) return emptyList()
        val out = ArrayList<EchoesInput>()
        for (run in blob.split(';')) {
            val bits = run.split(':')
            val code = bits.getOrNull(0)?.toIntOrNull() ?: continue
            val count = bits.getOrNull(1)?.toIntOrNull(36) ?: continue
            val input = EchoesInput.fromCode(code)
            repeat(count) { out += input }
        }
        return out
    }
}
