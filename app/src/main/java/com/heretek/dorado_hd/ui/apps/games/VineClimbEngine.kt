package com.heretek.dorado_hd.ui.apps.games

/**
 * Vine Climb: a four-lane endless climber.
 *
 * Pure Kotlin, no Android dependencies. Deterministic fixed 60 Hz model with a
 * seeded LCG so spawn rolls and vine lengths replay identically. Constants
 * follow docs/apps/vine-climb.md §3 (device ticks, px).
 */
object VineClimbEngine {

    const val VIEW_W = 272
    const val VIEW_H = 480
    const val LANES = 4
    const val LANE_LEFT = 70
    const val LANE_SPACING = 54
    const val TICK_MS = 1000L / 60L
    const val TICK_US = 16_667L

    const val SCROLL_PX = 3
    const val CAMERA_TOP_OFFSET = 350
    const val VINE_LENGTH_MIN = 200
    const val VINE_LENGTH_MAX = 384
    const val VINE_OVERLAP = 80
    const val SPAWN_TICKS_MIN = 100
    const val SPAWN_TICKS_MAX = 180
    const val ELECTRIC_PERIOD = 100
    const val ENEMY_HITBOX = 20
    const val FREEZE_TICKS = 5
    const val GAME_OVER_TICKS = 100
    const val TUTORIAL_FADE_SCORE = 50
    const val COLLECT_FRAMES = 40
    const val JUMPS = 3
    const val JUMP_SPEED = 2
    const val CLIMB_SPEED = 3
    const val FALL_ACCEL = 0.2f

    const val SCORE_MANGO = 2000
    const val SCORE_DRAGONFLY = 500
    const val SCORE_BERRIES = 250
    const val SCORE_NUT = 250

    enum class VineType { GREEN, SPIKY, ELECTRIC }

    enum class EnemyKind { SNAKE, CHAMELEON }

    enum class CollectableKind(val score: Int) {
        MANGO(SCORE_MANGO),
        BERRIES(SCORE_BERRIES),
        DRAGONFLY(SCORE_DRAGONFLY),
        NUT(SCORE_NUT),
    }

    data class Vine(
        val lane: Int,
        val top: Int,
        val length: Int,
        val type: VineType,
        val phase: Int = 0,
    )

    data class Enemy(
        val lane: Int,
        val x: Int,
        val y: Int,
        val kind: EnemyKind,
        val ticksAlive: Int = 0,
    ) {
        val revealed: Boolean get() = kind == EnemyKind.CHAMELEON && ticksAlive >= 100
    }

    data class Collectable(
        val lane: Int,
        val x: Int,
        val y: Int,
        val kind: CollectableKind,
    )

    data class FloatingScore(
        val x: Int,
        val y: Int,
        val value: Int,
        val ticksRemaining: Int = 100,
    )

    data class Input(val left: Boolean = false, val right: Boolean = false)

    data class State(
        val gameY: Int = 0,
        val lane: Int = 1,
        val lanePrevious: Int = 1,
        val x: Int = laneX(1),
        val climbedY: Int = 0,
        val falling: Boolean = false,
        val jumping: Boolean = false,
        val velX: Float = 0f,
        val velY: Float = 0f,
        val flashTicks: Int = 0,
        val flashZap: Boolean = false,
        val score: Int = 0,
        val ticks: Long = 0,
        val inTutorial: Boolean = true,
        val deathTicks: Int = 0,
        val gameOver: Boolean = false,
        val collectFrames: Int = 0,
        val collected: CollectableKind? = null,
        val vines: List<Vine> = emptyList(),
        val enemies: List<Enemy> = emptyList(),
        val collectables: List<Collectable> = emptyList(),
        val floatingScores: List<FloatingScore> = emptyList(),
        val swings: List<Int> = List(LANES) { 0 },
        val enemySpawnIn: Int = 0,
        val collectableSpawnIn: Int = 0,
        val layerFar: Int = 0,
        val layerMid: Int = 0,
        val layerNear: Int = 0,
        val horizontalOffset: Float = 0f,
        val rng: Int = DEFAULT_SEED,
        val accumulatorUs: Long = 0L,
    ) {
        val screenTop: Int get() = gameY - CAMERA_TOP_OFFSET
        val screenBottom: Int get() = screenTop + VIEW_H
        val playerTop: Int get() = -climbedY - 32
        val playerBottom: Int get() = -climbedY + 32
        val gameOverShown: Boolean get() = deathTicks > GAME_OVER_TICKS
    }

    const val DEFAULT_SEED = 0x51ED270B

    fun laneX(lane: Int): Int = LANE_LEFT + LANE_SPACING * lane

    fun newGame(seed: Int = DEFAULT_SEED): State {
        val vines = listOf(
            Vine(0, -350, 500, VineType.GREEN),
            Vine(1, -300, 500, VineType.GREEN),
            Vine(2, -200, 500, VineType.GREEN),
            Vine(3, -100, 500, VineType.GREEN),
        )
        return State(vines = vines, x = laneX(1), rng = if (seed == 0) DEFAULT_SEED else seed)
    }

    /** One 60 Hz frame. */
    fun tick(state: State, input: Input = Input()): State {
        if (state.gameOver) return state
        var s = state
        var rng = s.rng

        // Input is ignored once falling; a hop at a lane edge still ends the
        // tutorial (the device plays the swing even when the hop is blocked).
        if (!s.falling && (input.left || input.right)) {
            val direction = if (input.left) -1 else 1
            if (!s.jumping) {
                val target = s.lane + direction
                if (target in 0 until LANES) {
                    s = s.copy(lanePrevious = s.lane, lane = target, jumping = true)
                    s = s.copy(swings = s.swings.toMutableList().also { it[target] = 20 })
                }
            }
            s = s.copy(inTutorial = false)
        }

        if (!s.falling || s.deathTicks >= FREEZE_TICKS) {
            val gameY = s.gameY - SCROLL_PX
            val layerFar = (s.layerFar + 1).let { if (it > 701) it - 701 else it }
            val layerMid = (s.layerMid + 3).let { if (it > 512) it - 512 else it }
            val layerNear = (s.layerNear + 4).let { if (it > 594) it - 594 else it }
            val horizontalOffset = approach(horizontalGoal(s.lane), s.horizontalOffset, 0.2f)

            var climbedY = s.climbedY
            var x = s.x
            var jumping = s.jumping
            var velX = s.velX
            var velY = s.velY
            if (!s.falling) {
                climbedY += CLIMB_SPEED
                val target = laneX(s.lane)
                x = approach(target, x, JUMP_SPEED.toFloat())
                if (x == target && !jumping) jumping = false
                if (x == target) jumping = false
            } else {
                velY += FALL_ACCEL
                x += velX.toInt()
                climbedY -= velY.toInt()
            }

            var vines = s.vines.map { vine ->
                val phase = if (vine.type == VineType.ELECTRIC) (vine.phase + 1) % ELECTRIC_PERIOD else vine.phase
                vine.copy(phase = phase)
            }
            var enemies = s.enemies.map { enemy ->
                if (enemy.kind == EnemyKind.SNAKE) {
                    val y = enemy.y + 2
                    enemy.copy(y = if (y > 480) -128 else y, ticksAlive = enemy.ticksAlive + 1)
                } else {
                    enemy.copy(ticksAlive = enemy.ticksAlive + 1)
                }
            }
            var collectables = s.collectables
            var floating = s.floatingScores
            var score = s.score
            var collectFrames = s.collectFrames
            var collected = s.collected
            var enemySpawnIn = s.enemySpawnIn
            var collectSpawnIn = s.collectableSpawnIn
            var falling = s.falling
            var flashTicks = s.flashTicks
            var flashZap = s.flashZap
            var death = s.deathTicks
            val tutorial = s.inTutorial

            // Enemy spawn (same cadence as collectables, random lane).
            enemySpawnIn -= 1
            if (enemySpawnIn <= 0 && !tutorial) {
                val (nrng, roll) = rngBound(rng, Int.MAX_VALUE)
                rng = nrng
                val (lrng, lane) = rngBound(rng, LANES)
                rng = lrng
                val kind = if (roll % 2 != 0) EnemyKind.SNAKE else EnemyKind.CHAMELEON
                enemies = enemies + Enemy(lane, laneX(lane), gameY - 500, kind)
                val (srng, delay) = rngBound(rng, SPAWN_TICKS_MAX - SPAWN_TICKS_MIN)
                rng = srng
                enemySpawnIn = SPAWN_TICKS_MIN + delay
            }
            enemies = enemies.filter { it.y <= gameY + 200 }

            // Collectable spawn: multiples of 4 -> mango, 3 -> berries,
            // odd -> dragonfly, else nut.
            collectSpawnIn -= 1
            if (collectSpawnIn <= 0 && !tutorial) {
                val (nrng, roll) = rngBound(rng, Int.MAX_VALUE)
                rng = nrng
                val (lrng, lane) = rngBound(rng, LANES)
                rng = lrng
                collectables = collectables + Collectable(lane, laneX(lane), gameY - 500, collectKindForRoll(roll))
                val (srng, delay) = rngBound(rng, SPAWN_TICKS_MAX - SPAWN_TICKS_MIN)
                rng = srng
                collectSpawnIn = SPAWN_TICKS_MIN + delay
            }
            collectables = collectables.filter { it.y <= gameY + 200 }

            // Collisions (only while climbing).
            if (!falling) {
                val player = Rect(x - 8, -climbedY - 32, 16, 64)
                collectables = collectables.filter { item ->
                    val size = collectableSize(item.kind)
                    val hit = player.intersects(Rect(item.x - size / 2, item.y - size / 2, size, size))
                    if (!hit) return@filter true
                    score += item.kind.score
                    floating = floating + FloatingScore(item.x, item.y - CAMERA_TOP_OFFSET, item.kind.score)
                    collectFrames = COLLECT_FRAMES
                    collected = item.kind
                    false
                }
                for (enemy in enemies) {
                    if (player.intersects(Rect(enemy.x - ENEMY_HITBOX / 2, enemy.y - ENEMY_HITBOX / 2, ENEMY_HITBOX, ENEMY_HITBOX))) {
                        if (!falling) {
                            falling = true
                            flashTicks = 8
                            flashZap = false
                            velX = deathDirection(s.lane).toFloat()
                            velY = -5f
                        }
                    }
                }
                for (vine in vines) {
                    if (!onVine(vine, x, s.lane, climbedY)) continue
                    if (vine.type == VineType.SPIKY) {
                        falling = true
                        flashTicks = 8
                        flashZap = false
                        velX = deathDirection(s.lane).toFloat()
                        velY = -5f
                    } else if (vine.type == VineType.ELECTRIC && electricOn(vine.phase)) {
                        falling = true
                        flashTicks = 8
                        flashZap = true
                        velX = deathDirection(s.lane).toFloat()
                        velY = -5f
                    }
                }
            }

            // Vine maintenance: recycle below the screen, extend above it.
            vines = vines.filter { it.top <= gameY - CAMERA_TOP_OFFSET + VIEW_H }
            val minTop = IntArray(LANES) { Int.MAX_VALUE }
            for (vine in vines) {
                if (vine.top < minTop[vine.lane]) minTop[vine.lane] = vine.top
            }
            for (lane in 0 until LANES) {
                if (minTop[lane] == Int.MAX_VALUE) minTop[lane] = s.gameY - CAMERA_TOP_OFFSET - 100
                if (minTop[lane] > gameY - CAMERA_TOP_OFFSET - 100) {
                    val top = minTop[lane] + VINE_OVERLAP
                    val (lrng, lengthRoll) = rngBound(rng, VINE_LENGTH_MAX - VINE_LENGTH_MIN)
                    rng = lrng
                    val (trng, typeRoll) = rngBound(rng, 200)
                    rng = trng
                    val type = if (tutorial) VineType.GREEN else typeForRoll(lane, typeRoll)
                    vines = vines + Vine(lane, top - (VINE_LENGTH_MIN + lengthRoll), VINE_LENGTH_MIN + lengthRoll, type)
                }
            }

            floating = floating.map { it.copy(ticksRemaining = it.ticksRemaining - 1) }
                .filter { it.ticksRemaining > 0 }

            if (!falling && !tutorial) score += 1

            s = s.copy(
                gameY = gameY,
                layerFar = layerFar,
                layerMid = layerMid,
                layerNear = layerNear,
                horizontalOffset = horizontalOffset,
                climbedY = climbedY,
                x = x,
                jumping = jumping,
                velX = velX,
                velY = velY,
                vines = vines,
                enemies = enemies,
                collectables = collectables,
                floatingScores = floating,
                score = score,
                collectFrames = collectFrames,
                collected = collected,
                enemySpawnIn = enemySpawnIn,
                collectableSpawnIn = collectSpawnIn,
                falling = falling,
                flashTicks = flashTicks,
                flashZap = flashZap,
                deathTicks = death,
                rng = rng,
            )
        }

        // Death timing and animation bookkeeping run even while the world is frozen.
        var deathTicks = s.deathTicks
        if (s.falling) deathTicks += 1
        val gameOver = deathTicks > GAME_OVER_TICKS
        val collectFrames = (s.collectFrames - 1).coerceAtLeast(0)
        return s.copy(
            deathTicks = deathTicks,
            gameOver = gameOver,
            collectFrames = collectFrames,
            flashTicks = (s.flashTicks - 1).coerceAtLeast(0),
            swings = s.swings.map { (it - 1).coerceAtLeast(0) },
            ticks = s.ticks + 1,
        )
    }

    fun step(state: State, dtMs: Long): State = step(state, Input(), dtMs)

    fun step(state: State, input: Input, dtMs: Long): State {
        if (dtMs <= 0L) return state
        var s = state
        var acc = s.accumulatorUs + dtMs * 1000
        while (acc >= TICK_US) {
            s = tick(s, input)
            acc -= TICK_US
        }
        return s.copy(accumulatorUs = acc)
    }

    /** Device roll table: 4|mango, 3|berries, odd|dragonfly, even|nut. */
    fun collectKindForRoll(roll: Int): CollectableKind = when {
        roll % 4 == 0 -> CollectableKind.MANGO
        roll % 3 == 0 -> CollectableKind.BERRIES
        roll % 2 != 0 -> CollectableKind.DRAGONFLY
        else -> CollectableKind.NUT
    }

    /** 10% spiky on outer lanes, 10% electric on inner lanes, else green. */
    fun typeForRoll(lane: Int, roll: Int): VineType {
        val outer = lane == 0 || lane == 3
        if (roll < 20 && outer) return VineType.SPIKY
        if (roll >= 20) return VineType.GREEN
        return VineType.ELECTRIC
    }

    fun electricOn(phase: Int): Boolean = phase == 0 || phase == 2

    /** Starts the fall/zap death sequence (idempotent once falling). */
    fun beginFall(state: State, zap: Boolean = false): State {
        if (state.falling) return state
        return state.copy(
            falling = true,
            flashTicks = 8,
            flashZap = zap,
            velX = deathDirection(state.lane).toFloat(),
            velY = -5f,
        )
    }

    fun tutorialAlpha(score: Int): Float = (1f - score.toFloat() / TUTORIAL_FADE_SCORE).coerceIn(0f, 1f)

    fun updateHighScore(current: Int, score: Int): Int = maxOf(current, score)

    fun deathDirection(lane: Int): Int = when (lane) {
        0 -> 5
        1 -> 2
        2 -> -2
        else -> -5
    }

    fun collectableSize(kind: CollectableKind): Int = when (kind) {
        CollectableKind.MANGO -> 40
        CollectableKind.DRAGONFLY -> 40
        CollectableKind.BERRIES -> 24
        CollectableKind.NUT -> 24
    }

    fun floatingRise(ticksRemaining: Int): Int {
        val progress = 1f - ticksRemaining.coerceIn(0, 100) / 100f
        return (progress * progress * 200f).toInt()
    }

    fun onVine(vine: Vine, playerX: Int, lane: Int, climbedY: Int): Boolean {
        if (vine.lane != lane) return false
        if (kotlin.math.abs(laneX(lane) - playerX) >= 5) return false
        val top = -climbedY - 32
        return top in vine.top..(vine.top + vine.length - VINE_OVERLAP)
    }

    private fun horizontalGoal(lane: Int): Float = when (lane) {
        0 -> -16f
        1 -> -12f
        2 -> -6f
        else -> 0f
    }

    private fun approach(target: Float, current: Float, step: Float): Float = when {
        current < target -> minOf(target, current + step)
        current > target -> maxOf(target, current - step)
        else -> current
    }

    private fun approach(target: Int, current: Int, step: Float): Int {
        val next = approach(target.toFloat(), current.toFloat(), step)
        return next.toInt()
    }

    private fun rngBound(seed: Int, bound: Int): Pair<Int, Int> {
        var s = seed * 1103515245 + 12345
        if (s == 0) s = DEFAULT_SEED
        return s to (((s ushr 16) and 0x7FFF) % bound)
    }

    private data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun intersects(other: Rect): Boolean =
            x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h
    }
}
