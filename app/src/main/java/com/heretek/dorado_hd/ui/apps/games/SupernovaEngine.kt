package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SuperNova — one-tap chain-reaction game, clean-room engine.
 *
 * Constants are the mined Supernova.exe values from docs/apps/supernova.md §3:
 * a 60-dot pool in 19 colors, the (total, needed) level tables, 5/10 point
 * values, 0.5/0.75/0.5 s explosion timings and the 272x480 field with a 52 px
 * HUD strip (bottom limit 480 − 10 − 52 = 418).
 */
enum class NovaMode { NORMAL, HARD, ENDLESS }

enum class NovaPhase { PLAY, RESOLVING, COMPLETE, FAILED }

enum class NovaDotState { SPAWNING, MOVING, EXPLODING, SHRINKING, COMPLETE }

data class NovaDot(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val colorIndex: Int,
    val state: NovaDotState = NovaDotState.SPAWNING,
    val chain: Int = 0,
    val score: Int = 0,
    val ageMs: Float = 0f,
    val growMs: Float = 0f,
)

data class NovaState(
    val mode: NovaMode,
    val level: Int,
    val totalDots: Int,
    val neededDots: Int,
    val dots: List<NovaDot>,
    val score: Int = 0,
    val levelScore: Int = 0,
    val dotsExploded: Int = 0,
    val timeLeftMs: Long = 0L,
    val moveAvailable: Boolean = true,
    val levelStarted: Boolean = false,
    val startMs: Long = 0L,
    val phase: NovaPhase = NovaPhase.PLAY,
    val settled: Boolean = false,
    val seed: Int = 1,
    val flashMs: Long = 0L,
) {
    val isOver: Boolean get() = phase == NovaPhase.COMPLETE || phase == NovaPhase.FAILED

    val levelScoreValue: Int get() = levelScore * SupernovaEngine.multiplier(mode, level)
}

object SupernovaEngine {

    const val DOT_POOL = 60
    const val DOT_COLORS = 19
    const val DOT_RADIUS = 10f
    const val EXPLODE_MAX_SIZE = 50f
    const val GROW_MS = 500f
    const val STAY_MS = 750f
    const val SHRINK_MS = 500f
    const val FIELD_WIDTH = 272f
    const val FIELD_HEIGHT = 480f
    const val HUD_HEIGHT = 52f
    const val MAX_X = FIELD_WIDTH - DOT_RADIUS
    const val MAX_Y = FIELD_HEIGHT - DOT_RADIUS - HUD_HEIGHT
    const val BASE_VALUE = 5
    const val HARD_VALUE = 10
    const val SPEED_MIN = 50f
    const val SPEED_SPREAD = 50f
    const val SPEED_SPREAD_HARD = 25f
    const val HARD_SPEED_SCALE = 0.85f
    const val HARD_TIME_SCALE = 0.75f
    const val TIME_NORMAL_MS = 30_000L
    const val TIME_HARD_MS = 15_000L
    const val MAX_HIGH_SCORES = 10
    const val ENDLESS_LEVEL_CAP = 99
    const val FLASH_MS = 750L

    val LEVELS_NORMAL: List<Pair<Int, Int>> = listOf(
        60 to 50, 55 to 48, 50 to 43, 45 to 38, 40 to 30, 35 to 22,
        30 to 17, 25 to 13, 20 to 9, 15 to 6, 10 to 4, 5 to 2,
    )

    val LEVELS_HARD: List<Pair<Int, Int>> = listOf(
        60 to 50, 55 to 48, 50 to 43, 45 to 38, 40 to 30, 35 to 22,
        30 to 17, 25 to 13, 20 to 9, 15 to 6, 10 to 4, 5 to 3,
    )

    val MULTIPLIERS: List<Int> = listOf(1, 1, 1, 2, 2, 4, 4, 8, 12, 30, 60, 150)

    class NovaRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun nextFloat(): Float = ((nextSeed() ushr 8) and 0xFFFF) / 65535f

        fun nextInt(bound: Int): Int =
            if (bound <= 0) 0 else ((nextSeed() ushr 16) and 0x7FFF) % bound
    }

    fun levelTable(mode: NovaMode): List<Pair<Int, Int>> = when (mode) {
        NovaMode.NORMAL -> LEVELS_NORMAL
        NovaMode.HARD -> LEVELS_HARD
        NovaMode.ENDLESS -> listOf(DOT_POOL to 0)
    }

    fun levelEntry(mode: NovaMode, level: Int): Pair<Int, Int> {
        val table = levelTable(mode)
        val idx = (level - 1).coerceIn(0, table.size - 1)
        return table[idx]
    }

    fun multiplier(mode: NovaMode, level: Int): Int {
        if (mode == NovaMode.ENDLESS) return 1
        val idx = (level - 1).coerceIn(0, MULTIPLIERS.size - 1)
        return MULTIPLIERS[idx]
    }

    fun timeLimitMs(mode: NovaMode): Long = when (mode) {
        NovaMode.NORMAL -> TIME_NORMAL_MS
        NovaMode.HARD -> TIME_HARD_MS
        NovaMode.ENDLESS -> 0L
    }

    fun dotValue(mode: NovaMode): Int = if (mode == NovaMode.HARD) HARD_VALUE else BASE_VALUE

    fun spawnSpeed(mode: NovaMode, unit: Float): Float {
        val u = unit.coerceIn(0f, 1f)
        return if (mode == NovaMode.HARD) {
            (SPEED_MIN + u * SPEED_SPREAD_HARD) * HARD_SPEED_SCALE
        } else {
            SPEED_MIN + u * SPEED_SPREAD
        }
    }

    fun explosionTimings(mode: NovaMode): Triple<Float, Float, Float> =
        if (mode == NovaMode.HARD) {
            Triple(GROW_MS * HARD_TIME_SCALE, STAY_MS * HARD_TIME_SCALE, SHRINK_MS * HARD_TIME_SCALE)
        } else {
            Triple(GROW_MS, STAY_MS, SHRINK_MS)
        }

    fun isLastLevel(mode: NovaMode, level: Int): Boolean =
        mode != NovaMode.ENDLESS && level >= levelTable(mode).size

    fun newLevel(mode: NovaMode, level: Int, seed: Int): NovaState {
        val (total, needed) = levelEntry(mode, level)
        val rng = NovaRandom(seed)
        val dots = (0 until total).map { id ->
            val angle = rng.nextFloat() * (2f * PI.toFloat())
            val speed = spawnSpeed(mode, rng.nextFloat())
            val x = rng.nextFloat() * FIELD_WIDTH
            val y = rng.nextFloat() * (FIELD_HEIGHT - HUD_HEIGHT)
            NovaDot(
                id = id,
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                colorIndex = id % DOT_COLORS,
            )
        }
        return NovaState(
            mode = mode,
            level = level,
            totalDots = total,
            neededDots = needed,
            dots = dots,
            timeLeftMs = timeLimitMs(mode),
            seed = seed,
        )
    }

    fun nextLevel(state: NovaState): NovaState {
        if (state.mode != NovaMode.ENDLESS && state.level >= levelTable(state.mode).size) return state
        val next = if (state.mode == NovaMode.ENDLESS) {
            (state.level + 1).coerceAtMost(ENDLESS_LEVEL_CAP)
        } else {
            state.level + 1
        }
        return newLevel(state.mode, next, state.seed + 0x9E3779B9.toInt()).copy(score = state.score)
    }

    fun retryLevel(state: NovaState): NovaState =
        newLevel(state.mode, state.level, state.seed + 1).copy(score = state.score)

    fun tap(state: NovaState, x: Float, y: Float): NovaState {
        if (state.phase != NovaPhase.PLAY || !state.moveAvailable || !state.levelStarted) return state
        val spawner = NovaDot(
            id = DOT_POOL,
            x = x.coerceIn(0f, MAX_X),
            y = y.coerceIn(0f, MAX_Y),
            vx = 0f,
            vy = 0f,
            colorIndex = -1,
            state = NovaDotState.EXPLODING,
            chain = 0,
            score = 0,
        )
        return state.copy(
            dots = state.dots + spawner,
            moveAvailable = false,
            flashMs = FLASH_MS,
        )
    }

    fun dotSize(dot: NovaDot, mode: NovaMode): Float = when (dot.state) {
        NovaDotState.SPAWNING -> DOT_RADIUS * (dot.growMs / GROW_MS).coerceIn(0f, 1f)
        NovaDotState.MOVING -> DOT_RADIUS
        NovaDotState.EXPLODING -> {
            val (grow, stay, shrink) = explosionTimings(mode)
            val age = dot.ageMs
            when {
                age < grow -> DOT_RADIUS + (EXPLODE_MAX_SIZE - DOT_RADIUS) * (age / grow)
                age < grow + stay -> EXPLODE_MAX_SIZE
                age < grow + stay + shrink -> EXPLODE_MAX_SIZE * (1f - (age - grow - stay) / shrink)
                else -> 0f
            }
        }
        NovaDotState.SHRINKING -> DOT_RADIUS * (1f - (dot.ageMs / SHRINK_MS).coerceIn(0f, 1f))
        NovaDotState.COMPLETE -> 0f
    }

    fun dotRadius(dot: NovaDot, mode: NovaMode): Float = dotSize(dot, mode) * 0.83f

    fun collides(a: NovaDot, b: NovaDot, mode: NovaMode): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val radius = dotRadius(a, mode) + dotRadius(b, mode)
        return dx * dx + dy * dy <= radius * radius
    }

    fun flashAlpha(state: NovaState): Float {
        if (state.flashMs <= 0L) return 0f
        val progress = 1f - state.flashMs.toFloat() / FLASH_MS
        val v = progress * 2f
        val n = if (v >= 1f) 2f - v else v
        return n * n * n * n
    }

    private fun advance(dot: NovaDot, dtMs: Long, dt: Float, mode: NovaMode): NovaDot = when (dot.state) {
        NovaDotState.SPAWNING -> {
            val grow = dot.growMs + dtMs
            if (grow >= GROW_MS) dot.copy(growMs = GROW_MS, state = NovaDotState.MOVING)
            else dot.copy(growMs = grow)
        }
        NovaDotState.MOVING -> {
            var x = dot.x + dot.vx * dt
            var y = dot.y + dot.vy * dt
            var vx = dot.vx
            var vy = dot.vy
            if (x > MAX_X) { x = MAX_X; vx = -vx } else if (x < 0f) { x = 0f; vx = -vx }
            if (y > MAX_Y) { y = MAX_Y; vy = -vy } else if (y < 0f) { y = 0f; vy = -vy }
            dot.copy(x = x, y = y, vx = vx, vy = vy)
        }
        NovaDotState.EXPLODING -> {
            val (grow, stay, shrink) = explosionTimings(mode)
            val life = grow + stay + shrink
            val age = dot.ageMs + dtMs
            if (age >= life) dot.copy(ageMs = life, state = NovaDotState.COMPLETE)
            else dot.copy(ageMs = age)
        }
        NovaDotState.SHRINKING -> {
            val age = dot.ageMs + dtMs
            if (age >= SHRINK_MS) dot.copy(ageMs = SHRINK_MS, state = NovaDotState.COMPLETE)
            else dot.copy(ageMs = age)
        }
        NovaDotState.COMPLETE -> dot
    }

    fun step(state: NovaState, dtMs: Long): NovaState {
        if (dtMs <= 0L || state.phase == NovaPhase.COMPLETE || state.phase == NovaPhase.FAILED) return state
        val dt = dtMs / 1000f
        var dots = state.dots.map { advance(it, dtMs, dt, state.mode) }

        var startMs = state.startMs
        var levelStarted = state.levelStarted
        if (!levelStarted) {
            startMs += dtMs
            if (startMs >= GROW_MS.toLong()) levelStarted = true
        }

        var moveAvailable = state.moveAvailable
        var timeLeft = state.timeLeftMs
        if (state.mode != NovaMode.ENDLESS && timeLeft > 0L) {
            timeLeft = (timeLeft - dtMs).coerceAtLeast(0L)
            if (timeLeft == 0L) moveAvailable = false
        }

        var dotsExploded = state.dotsExploded
        var levelScore = state.levelScore

        if (state.phase == NovaPhase.PLAY) {
            var searching = true
            while (searching) {
                val explosions = dots.filter { it.state == NovaDotState.EXPLODING }
                if (explosions.isEmpty()) break
                var fresh = 0
                dots = dots.map { d ->
                    if (d.state != NovaDotState.MOVING && d.state != NovaDotState.SPAWNING) {
                        d
                    } else {
                        val parent = explosions.firstOrNull { collides(d, it, state.mode) }
                        if (parent == null) {
                            d
                        } else {
                            fresh++
                            val chain = parent.chain + 1
                            val value = chain * dotValue(state.mode)
                            levelScore += value
                            dotsExploded++
                            d.copy(
                                state = NovaDotState.EXPLODING,
                                chain = chain,
                                score = value,
                                ageMs = 0f,
                            )
                        }
                    }
                }
                if (fresh == 0) searching = false
            }
        }

        var phase = state.phase
        if (phase == NovaPhase.PLAY) {
            val explosionsLeft = dots.any { it.state == NovaDotState.EXPLODING }
            if (!moveAvailable && !explosionsLeft) {
                dots = dots.map {
                    if (it.state == NovaDotState.COMPLETE || it.state == NovaDotState.SHRINKING) it
                    else it.copy(state = NovaDotState.SHRINKING, ageMs = 0f)
                }
                phase = NovaPhase.RESOLVING
            }
        }
        if (phase == NovaPhase.RESOLVING && dots.all { it.state == NovaDotState.COMPLETE }) {
            phase = if (dotsExploded >= state.neededDots) NovaPhase.COMPLETE else NovaPhase.FAILED
        }

        var score = state.score
        var settled = state.settled
        if (phase == NovaPhase.COMPLETE && !settled) {
            score += levelScore * multiplier(state.mode, state.level)
            settled = true
        }

        return state.copy(
            dots = dots,
            levelStarted = levelStarted,
            startMs = startMs,
            dotsExploded = dotsExploded,
            levelScore = levelScore,
            timeLeftMs = timeLeft,
            moveAvailable = moveAvailable,
            phase = phase,
            settled = settled,
            score = score,
            flashMs = (state.flashMs - dtMs).coerceAtLeast(0L),
        )
    }

    fun insertHighScore(scores: List<Int>, score: Int, max: Int = MAX_HIGH_SCORES): List<Int> {
        if (score <= 0) return scores.take(max)
        return (scores + score).sortedDescending().take(max)
    }

    fun highScoreRank(scores: List<Int>, score: Int): Int {
        if (score <= 0) return -1
        val index = scores.indexOfFirst { score > it }
        val insert = if (index < 0) scores.size else index
        return if (insert < MAX_HIGH_SCORES) insert else -1
    }

    fun encode(state: NovaState): String {
        val head = listOf(
            "v1",
            state.mode.name,
            state.level,
            state.totalDots,
            state.neededDots,
            state.score,
            state.levelScore,
            state.dotsExploded,
            state.timeLeftMs,
            if (state.moveAvailable) 1 else 0,
            if (state.levelStarted) 1 else 0,
            state.startMs,
            state.phase.name,
            if (state.settled) 1 else 0,
            state.seed,
            state.flashMs,
        )
        val body = state.dots.joinToString("|") { d ->
            listOf(
                d.id,
                d.x.toRawBits(),
                d.y.toRawBits(),
                d.vx.toRawBits(),
                d.vy.toRawBits(),
                d.colorIndex,
                d.state.name,
                d.chain,
                d.score,
                d.ageMs.toRawBits(),
                d.growMs.toRawBits(),
            ).joinToString(",")
        }
        return (head + body).joinToString(";")
    }

    fun decode(text: String?): NovaState? {
        if (text.isNullOrBlank()) return null
        return try {
            val f = text.split(";")
            if (f.size < 16 || f[0] != "v1") return null
            val dots = f.getOrNull(16)?.takeIf { it.isNotEmpty() }?.split("|")?.map { row ->
                val p = row.split(",")
                NovaDot(
                    id = p[0].toInt(),
                    x = Float.fromBits(p[1].toInt()),
                    y = Float.fromBits(p[2].toInt()),
                    vx = Float.fromBits(p[3].toInt()),
                    vy = Float.fromBits(p[4].toInt()),
                    colorIndex = p[5].toInt(),
                    state = NovaDotState.valueOf(p[6]),
                    chain = p[7].toInt(),
                    score = p[8].toInt(),
                    ageMs = Float.fromBits(p[9].toInt()),
                    growMs = Float.fromBits(p[10].toInt()),
                )
            } ?: emptyList()
            NovaState(
                mode = NovaMode.valueOf(f[1]),
                level = f[2].toInt(),
                totalDots = f[3].toInt(),
                neededDots = f[4].toInt(),
                score = f[5].toInt(),
                levelScore = f[6].toInt(),
                dotsExploded = f[7].toInt(),
                timeLeftMs = f[8].toLong(),
                moveAvailable = f[9] == "1",
                levelStarted = f[10] == "1",
                startMs = f[11].toLong(),
                phase = NovaPhase.valueOf(f[12]),
                settled = f[13] == "1",
                seed = f[14].toInt(),
                flashMs = f[15].toLong(),
                dots = dots,
            )
        } catch (_: Exception) {
            null
        }
    }
}
