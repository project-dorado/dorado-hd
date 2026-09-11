package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A Beanstalk Tale — clean-room endless vertical climber engine.
 *
 * Re-derived from the behavioral spec in docs/apps/a-beanstalk-tale.md. All
 * mechanics, tier tables, enemy schedules and cup thresholds here are our own
 * authored content: no asset bytes, level data or Microsoft strings are used.
 * The world is authored 272 px wide and 480 px tall (portrait); the UI scales it.
 */
object BeanstalkEngine {

    const val WORLD_W = 272f
    const val VIEW_H = 480f
    const val GROUND_Y = 499f
    const val START_LINE = GROUND_Y
    const val CAMERA_PIG_Y = 385f
    const val DEATH_SCREEN_Y = 520f
    const val GAME_OVER_SCREEN_Y = 720f

    const val PIG_HALF_W = 9f
    const val PIG_HALF_H = 11f

    const val GRAVITY = 0.001f
    const val MAX_FALL = 0.75f
    const val JUMP_VELOCITY = -0.5f
    const val JUMPPAD_VELOCITY = -0.9f
    const val HOP_VELOCITY = -0.4f

    const val TILT_ACCEL = 1.2f
    const val H_DAMPING = 4f
    const val MAX_RUN = 0.4f
    const val BALLOON_TILT_CLAMP = 0.5f
    const val BALLOON_LIFT = -0.2f
    const val BALLOON_MS = 5_000L
    const val SHIELD_MS = 10_000L
    const val SHIELD_BLINK_MS = 200L

    const val BOW_COOLDOWN_MS = 200L
    const val ARROW_SPEED = 0.4f
    const val ARROW_BOX = 4f
    const val ARROW_UP_MIN_Y = 300f

    const val LEAF_MIN_X = 10f
    const val LEAF_MAX_X = 252f
    const val LEAF_WIDTH = 48f
    const val LEAF_GAP_MIN = 20f
    const val LEAF_GAP_MAX = 100f
    const val LEAF_SPAWN_MARGIN = 120f

    const val PICKUP_PERCENT = 10
    const val FOOD_BASE = 500
    const val FOOD_STEP = 50
    const val FOOD_PATTERNS = 16

    const val SPECIAL_CADENCE = 1_500f
    const val BOSS_CADENCE = 8_000f
    const val SPECIAL_LEAF_COUNT = 10
    const val JUMPPAD_COUNT = 5
    const val JUMPPAD_GAP = 300f
    const val BOSS_GROUND_COUNT = 6
    const val BOSS_HP = 5

    const val COUNTDOWN_MS = 3_000L
    const val BANNER_MS = 5_000L

    class BeanstalkRandom(seed: Int) {
        var state: Int = seed
            private set

        fun nextSeed(): Int {
            state = state * 1103515245 + 12345
            return state
        }

        fun next(bound: Int): Int = if (bound <= 0) 0 else ((nextSeed() ushr 16) and 0x7FFF) % bound

        fun nextFloat(): Float = ((nextSeed() ushr 8) and 0xFFFF) / 65_536f
    }

    enum class Phase { COUNTDOWN, PLAYING, DYING, GAME_OVER }

    enum class LeafKind { NORMAL, DRY, JUMPPAD, MOVER_H, MOVER_V, MOVER_CIRCLE, BOSS_GROUND }

    enum class SpecialKind { DRY_RUN, JUMPPAD_RUN, ENEMY, MOVER_RUN }

    enum class EnemyKind(val family: String) {
        WASP("wasps"),
        SPIDER("spiders"),
        BUG("bugs"),
        TREANT("treants"),
        GAZER("gazers"),
        MUSHROOM("mushroom men"),
        SWARM("swarm"),
    }

    enum class BossKind { FROG, BEEHIVE, SPIDER }

    enum class PickupKind { FOOD, SHIELD, BALLOON }

    enum class Event {
        COUNTDOWN, START, JUMP, BOW, LEAF_BREAK, FOOD, SHIELD, BALLOON, BALLOON_POP,
        ARROW_HIT, ENEMY_HIT, JUMP_KILL, HIT, BOSS_ARRIVE, BOSS_RETREAT, GAME_OVER,
    }

    data class Pig(
        val x: Float,
        val y: Float,
        val vx: Float = 0f,
        val vy: Float = 0f,
        val grounded: Boolean = true,
        val facing: Int = 1,
        val riding: Boolean = false,
    )

    data class LeafPlan(val kind: LeafKind, val gap: Float, val dx: Float = 0f)

    data class Leaf(
        val id: Int,
        val x: Float,
        val y: Float,
        val kind: LeafKind,
        val anchorX: Float = x,
        val anchorY: Float = y,
        val phase: Float = 0f,
        val broken: Boolean = false,
        val pickup: PickupKind? = null,
        val pickupPattern: Int = 0,
        val pickupTaken: Boolean = false,
    )

    data class Enemy(
        val id: Int,
        val kind: EnemyKind,
        val x: Float,
        val y: Float,
        val vx: Float = 0f,
        val vy: Float = 0f,
        val scale: Int = 1,
        val skin: Int = 0,
        val phase: Float = 0f,
    )

    data class Boss(
        val kind: BossKind,
        val x: Float,
        val y: Float,
        val vx: Float = 0f,
        val hp: Int = BOSS_HP,
        val retreat: Boolean = false,
    )

    data class Arrow(val id: Int, val x: Float, val y: Float, val vx: Float, val vy: Float)

    data class FloatingText(val x: Float, val y: Float, val text: String, val remainingMs: Long)

    data class Particle(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val remainingMs: Long,
        val tint: Int,
    )

    data class RunStats(
        val jumps: Int = 0,
        val flowerJumps: Int = 0,
        val arrowsFired: Int = 0,
        val arrowsHit: Int = 0,
        val arrowsMissed: Int = 0,
        val balloonRides: Int = 0,
        val shields: Int = 0,
        val starts: Int = 0,
        val bossKills: Int = 0,
        val kills: Map<EnemyKind, Int> = emptyMap(),
    )

    fun withKill(stats: RunStats, kind: EnemyKind): RunStats =
        stats.copy(kills = stats.kills + (kind to (stats.kills[kind] ?: 0) + 1))

    data class State(
        val phase: Phase = Phase.COUNTDOWN,
        val clockMs: Long = 0L,
        val countdownMs: Long = COUNTDOWN_MS,
        val pig: Pig = Pig(120f, GROUND_Y - PIG_HALF_H),
        val cameraTop: Float = 0f,
        val leaves: List<Leaf> = emptyList(),
        val plans: List<LeafPlan> = emptyList(),
        val enemies: List<Enemy> = emptyList(),
        val boss: Boss? = null,
        val arrows: List<Arrow> = emptyList(),
        val floats: List<FloatingText> = emptyList(),
        val particles: List<Particle> = emptyList(),
        val minPigY: Float = GROUND_Y - PIG_HALF_H,
        val bonus: Int = 0,
        val nextSpecialAt: Float = SPECIAL_CADENCE,
        val nextBossAt: Float = BOSS_CADENCE,
        val specialIndex: Int = 0,
        val specialsSpawned: Int = 0,
        val bossesSpawned: Int = 0,
        val bossGroundLeft: Int = 0,
        val pendingBoss: BossKind? = null,
        val nextId: Int = 1,
        val rngSeed: Int = 1,
        val stats: RunStats = RunStats(),
        val shieldMs: Long = 0L,
        val balloonMs: Long = 0L,
        val bowCooldownMs: Long = 0L,
        val message: String = "",
        val events: List<Event> = emptyList(),
    ) {
        val score: Int get() = maxHeight + bonus
        val maxHeight: Int get() = floor(START_LINE - minPigY).toInt().coerceAtLeast(0)
        val pigScreenY: Float get() = pig.y - cameraTop
    }

    data class Input(val tilt: Float = 0f, val tapScreenX: Float? = null, val tapScreenY: Float? = null)

    fun foodWorth(pattern: Int): Int = FOOD_BASE + FOOD_STEP * pattern.coerceIn(0, FOOD_PATTERNS - 1)

    fun newRun(seed: Int = 1): State {
        val rng = BeanstalkRandom(seed)
        val first = Leaf(
            id = 1,
            x = 136f,
            y = GROUND_Y - 60f,
            kind = LeafKind.NORMAL,
        )
        return State(
            phase = Phase.COUNTDOWN,
            leaves = listOf(first),
            rngSeed = rng.nextSeed(),
        )
    }

    fun step(state: State, input: Input, dtMs: Long): State {
        if (state.phase == Phase.GAME_OVER) return state
        val dt = dtMs.coerceIn(0L, 100L).toFloat()
        val rng = BeanstalkRandom(state.rngSeed)
        val events = ArrayList<Event>()
        var clock = state.clockMs + dtMs
        var countdown = state.countdownMs
        var phase = state.phase
        var pig = state.pig
        var camera = state.cameraTop
        var leaves = state.leaves
        var plans = state.plans
        var enemies = state.enemies
        var boss = state.boss
        var arrows = state.arrows
        var floats = state.floats
        var particles = state.particles
        var bonus = state.bonus
        var nextSpecialAt = state.nextSpecialAt
        var nextBossAt = state.nextBossAt
        var specialIndex = state.specialIndex
        var specials = state.specialsSpawned
        var bosses = state.bossesSpawned
        var bossGroundLeft = state.bossGroundLeft
        var pendingBoss = state.pendingBoss
        var nextId = state.nextId
        var stats = state.stats
        var shieldMs = state.shieldMs
        var balloonMs = state.balloonMs
        var bowCooldown = (state.bowCooldownMs - dtMs).coerceAtLeast(0L)
        var message = state.message

        if (phase == Phase.COUNTDOWN) {
            val before = countdown
            countdown = (countdown - dtMs).coerceAtLeast(0L)
            if (before > 0L && countdown == 0L) {
                phase = Phase.PLAYING
                stats = stats.copy(starts = stats.starts + 1)
                events += Event.START
                message = "go"
            } else {
                events += Event.COUNTDOWN
                message = "${(countdown / 1000L + 1).coerceAtMost(3)}"
            }
        } else if (phase == Phase.PLAYING) {
            val tap = input.tapScreenX != null && input.tapScreenY != null
            val tilt = input.tilt.coerceIn(-1f, 1f)

            if (tap) {
                val pigScreenY = pig.y - camera
                if (pig.grounded) {
                    pig = pig.copy(vy = JUMP_VELOCITY, grounded = false)
                    stats = stats.copy(jumps = stats.jumps + 1)
                    events += Event.JUMP
                } else if (pigScreenY < ARROW_UP_MIN_Y && bowCooldown == 0L) {
                    val touchX = input.tapScreenX ?: pig.x
                    val touchY = input.tapScreenY ?: (pigScreenY - 10f)
                    val direction = arrowDirection(pig.x, pigScreenY, touchX, touchY)
                    arrows = arrows + Arrow(nextId++, pig.x, pig.y, direction.first, direction.second)
                    bowCooldown = BOW_COOLDOWN_MS
                    stats = stats.copy(arrowsFired = stats.arrowsFired + 1)
                    events += Event.BOW
                }
            }

            if (pig.riding) {
                val steer = tilt.coerceIn(-BALLOON_TILT_CLAMP, BALLOON_TILT_CLAMP)
                pig = pig.copy(vx = steer * MAX_RUN, vy = BALLOON_LIFT)
            } else {
                val accel = tilt * TILT_ACCEL
                val damp = (1f - H_DAMPING * dt / 1000f).coerceAtLeast(0f)
                var vx = pig.vx * damp + accel * dt
                vx = vx.coerceIn(-MAX_RUN, MAX_RUN)
                var vy = (pig.vy + GRAVITY * dt).coerceAtMost(MAX_FALL)
                pig = pig.copy(vx = vx, vy = vy)
            }
            if (abs(pig.vx) > 0.01f) pig = pig.copy(facing = if (pig.vx < 0f) -1 else 1)
            pig = pig.copy(
                x = (pig.x + pig.vx * dt).coerceIn(PIG_HALF_W, WORLD_W - PIG_HALF_W),
                y = pig.y + pig.vy * dt,
                grounded = false,
            )

            val landed = findLanding(leaves, pig)
            if (landed != null) {
                val (leaf, kind) = landed
                pig = pig.copy(y = leaf.y - PIG_HALF_H, vy = 0f, grounded = true)
                when (kind) {
                    Landing.KIND_DRY -> {
                        leaves = leaves.map { if (it.id == leaf.id) it.copy(broken = true) else it }
                        events += Event.LEAF_BREAK
                    }
                    Landing.KIND_PAD -> {
                        pig = pig.copy(vy = JUMPPAD_VELOCITY, grounded = false)
                        stats = stats.copy(jumps = stats.jumps + 1)
                        events += Event.JUMP
                    }
                    else -> Unit
                }
                val taken = collectPickup(leaf)
                if (taken != null) {
                    when (taken.kind) {
                        PickupKind.FOOD -> {
                            val worth = foodWorth(taken.pattern)
                            bonus += worth
                            floats = floats + FloatingText(leaf.x, leaf.y - 14f, "+$worth", 1_200L)
                            events += Event.FOOD
                        }
                        PickupKind.SHIELD -> {
                            shieldMs = SHIELD_MS
                            stats = stats.copy(shields = stats.shields + 1)
                            events += Event.SHIELD
                        }
                        PickupKind.BALLOON -> {
                            balloonMs = BALLOON_MS
                            pig = pig.copy(riding = true, vy = BALLOON_LIFT)
                            stats = stats.copy(balloonRides = stats.balloonRides + 1)
                            events += Event.BALLOON
                        }
                    }
                    leaves = leaves.map {
                        if (it.id == leaf.id) it.copy(pickupTaken = true) else it
                    }
                }
            } else if (camera >= -0.5f && pig.y + PIG_HALF_H >= GROUND_Y) {
                pig = pig.copy(y = GROUND_Y - PIG_HALF_H, vy = 0f, grounded = true)
            }

            if (shieldMs > 0L) shieldMs = (shieldMs - dtMs).coerceAtLeast(0L)
            if (balloonMs > 0L) {
                balloonMs = (balloonMs - dtMs).coerceAtLeast(0L)
                if (balloonMs == 0L) {
                    pig = pig.copy(riding = false)
                    particles = particles + balloonPop(pig.x, pig.y)
                    events += Event.BALLOON_POP
                }
            }

            val movedLeaves = moveLeaves(leaves, dt)
            leaves = movedLeaves

            enemies = enemies.mapNotNull { enemy -> stepEnemy(enemy, dt, camera) }
            boss = boss?.let { stepBoss(it, dt) }

            var arrowHits = 0
            val aliveArrows = ArrayList<Arrow>()
            for (arrow in arrows) {
                val ax = arrow.x + arrow.vx * dt
                val ay = arrow.y + arrow.vy * dt
                val hitEnemy = enemies.firstOrNull { enemy ->
                    abs(enemy.x - ax) <= ARROW_BOX && abs(enemy.y - ay) <= ARROW_BOX
                }
                if (hitEnemy != null) {
                    enemies = enemies.filterNot { it.id == hitEnemy.id }
                    floats = floats + FloatingText(hitEnemy.x, hitEnemy.y, "1", 700L)
                    stats = withKill(stats, hitEnemy.kind)
                    events += Event.ARROW_HIT
                    arrowHits++
                    continue
                }
                if (boss != null && !boss.retreat && abs(boss.x - ax) <= 16f && abs(boss.y - ay) <= 20f) {
                    val hp = boss.hp - 1
                    boss = if (hp <= 0) boss.copy(hp = 0, retreat = true) else boss.copy(hp = hp)
                    events += Event.ARROW_HIT
                    arrowHits++
                    continue
                }
                if (ax < -8f || ax > WORLD_W + 8f || ay < camera - 16f || ay > camera + VIEW_H + 16f) {
                    stats = stats.copy(arrowsMissed = stats.arrowsMissed + 1)
                    continue
                }
                aliveArrows += arrow.copy(x = ax, y = ay)
            }
            if (arrowHits > 0) stats = stats.copy(arrowsHit = stats.arrowsHit + arrowHits)
            if (boss?.retreat == true) {
                stats = stats.copy(bossKills = stats.bossKills + 1)
                floats = floats + FloatingText(boss.x, boss.y - 20f, "boss down", 1_400L)
                events += Event.BOSS_RETREAT
                boss = null
            }
            arrows = aliveArrows

            val contactEnemy = enemies.firstOrNull { enemy ->
                abs(enemy.x - pig.x) <= PIG_HALF_W + 8f && abs(enemy.y - pig.y) <= PIG_HALF_H + 8f
            }
            if (contactEnemy != null) {
                if (shieldMs > 0L) {
                    enemies = enemies.filterNot { it.id == contactEnemy.id }
                    stats = withKill(stats, contactEnemy.kind)
                    events += Event.ENEMY_HIT
                } else if (pig.vy > 0.05f && pig.y < contactEnemy.y + 6f) {
                    enemies = enemies.filterNot { it.id == contactEnemy.id }
                    stats = withKill(stats, contactEnemy.kind)
                    stats = stats.copy(flowerJumps = stats.flowerJumps + 1)
                    pig = pig.copy(vy = HOP_VELOCITY * 0.85f)
                    floats = floats + FloatingText(contactEnemy.x, contactEnemy.y, "1", 700L)
                    events += Event.JUMP_KILL
                } else {
                    phase = Phase.DYING
                    message = "the stalk wins"
                    events += Event.HIT
                }
            }
            if (boss != null && !boss.retreat &&
                abs(boss!!.x - pig.x) <= PIG_HALF_W + 16f && abs(boss!!.y - pig.y) <= PIG_HALF_H + 20f && shieldMs == 0L
            ) {
                phase = Phase.DYING
                message = "bossed"
                events += Event.HIT
            }

            if (phase == Phase.PLAYING && pig.y - camera > DEATH_SCREEN_Y) {
                phase = Phase.DYING
                message = "down the stalk"
                for (arrow in arrows) {
                    if (arrow.x in -8f..(WORLD_W + 8f)) stats = stats.copy(arrowsMissed = stats.arrowsMissed + 1)
                }
                arrows = emptyList()
                events += Event.HIT
            }

            if (phase == Phase.DYING && pig.y - camera > GAME_OVER_SCREEN_Y) {
                phase = Phase.GAME_OVER
                events += Event.GAME_OVER
            }

            camera = if (pig.y - CAMERA_PIG_Y < 0f) pig.y - CAMERA_PIG_Y else 0f

            val grown = spawnLeaves(leaves, plans, camera, rng, nextId, boss != null)
            leaves = grown.leaves
            plans = grown.plans
            nextId = grown.nextId
            if (grown.bossGroundSpawned > 0) bossGroundLeft = (bossGroundLeft - grown.bossGroundSpawned).coerceAtLeast(0)

            val height = floor(START_LINE - minOf(state.minPigY, pig.y)).toInt().coerceAtLeast(0)
            while (phase == Phase.PLAYING && height >= nextSpecialAt.toInt()) {
                val special = SpecialKind.values()[specialIndex % SpecialKind.values().size]
                specialIndex = (specialIndex + 1) % SpecialKind.values().size
                specials++
                when (special) {
                    SpecialKind.DRY_RUN -> {
                        repeat(SPECIAL_LEAF_COUNT) {
                            plans = plans + LeafPlan(LeafKind.DRY, 30f + rng.next(41))
                        }
                    }
                    SpecialKind.JUMPPAD_RUN -> {
                        plans = plans + LeafPlan(LeafKind.JUMPPAD, 50f)
                        repeat(JUMPPAD_COUNT - 1) { plans = plans + LeafPlan(LeafKind.JUMPPAD, JUMPPAD_GAP) }
                    }
                    SpecialKind.ENEMY -> {
                        val kind = basicEnemies()[rng.next(basicEnemies().size)]
                        val x = 30f + rng.next(212)
                        enemies = enemies + Enemy(nextId++, kind, x, camera + 140f, vx = if (rng.next(2) == 0) -0.04f else 0.04f)
                    }
                    SpecialKind.MOVER_RUN -> {
                        repeat(SPECIAL_LEAF_COUNT) {
                            plans = plans + LeafPlan(LeafKind.MOVER_H, 60f, -40f + rng.next(81))
                        }
                    }
                }
                nextSpecialAt += SPECIAL_CADENCE
            }
            while (phase == Phase.PLAYING && height >= nextBossAt.toInt()) {
                bosses++
                bossGroundLeft += BOSS_GROUND_COUNT
                pendingBoss = BossKind.values()[rng.next(BossKind.values().size)]
                repeat(BOSS_GROUND_COUNT) { plans = plans + LeafPlan(LeafKind.BOSS_GROUND, 55f) }
                nextBossAt += BOSS_CADENCE
            }

            if (bossGroundLeft <= 0 && pendingBoss != null && boss == null && plans.none { it.kind == LeafKind.BOSS_GROUND }) {
                val kind = pendingBoss
                val top = leaves.minByOrNull { it.y }
                if (top != null) {
                    boss = Boss(kind, top.x, top.y - 40f, vx = 0.05f)
                    events += Event.BOSS_ARRIVE
                }
                pendingBoss = null
            }
        }

        if (phase == Phase.DYING) {
            pig = pig.copy(y = pig.y + pig.vy * dt, vy = (pig.vy + GRAVITY * dt).coerceAtMost(MAX_FALL))
            camera = if (pig.y - CAMERA_PIG_Y < 0f) pig.y - CAMERA_PIG_Y else 0f
            if (pig.y - camera > GAME_OVER_SCREEN_Y) {
                phase = Phase.GAME_OVER
                events += Event.GAME_OVER
            }
        }

        floats = floats.mapNotNull { it.copy(remainingMs = it.remainingMs - dtMs).takeIf { f -> f.remainingMs > 0L } }
        particles = particles.mapNotNull { p ->
            p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt,
                vy = p.vy + 0.001f * dt,
                remainingMs = p.remainingMs - dtMs,
            ).takeIf { it.remainingMs > 0L }
        }

        return state.copy(
            phase = phase,
            clockMs = clock,
            countdownMs = countdown,
            pig = pig,
            cameraTop = camera,
            leaves = leaves,
            plans = plans,
            enemies = enemies,
            boss = boss,
            arrows = arrows,
            floats = floats,
            particles = particles,
            minPigY = minOf(state.minPigY, pig.y),
            bonus = bonus,
            nextSpecialAt = nextSpecialAt,
            nextBossAt = nextBossAt,
            specialIndex = specialIndex,
            specialsSpawned = specials,
            bossesSpawned = bosses,
            bossGroundLeft = bossGroundLeft,
            pendingBoss = pendingBoss,
            nextId = nextId,
            rngSeed = rng.state,
            stats = stats,
            shieldMs = shieldMs,
            balloonMs = balloonMs,
            bowCooldownMs = bowCooldown,
            message = message,
            events = events,
        )
    }

    private fun basicEnemies(): List<EnemyKind> =
        listOf(EnemyKind.WASP, EnemyKind.SPIDER, EnemyKind.BUG, EnemyKind.TREANT, EnemyKind.GAZER, EnemyKind.MUSHROOM, EnemyKind.SWARM)

    private fun arrowDirection(pigX: Float, pigY: Float, touchX: Float, touchY: Float): Pair<Float, Float> {
        val dx = touchX - pigX
        val dy = touchY - pigY
        if (dx < -30f) {
            val angle = Math.atan2(dy.toDouble(), dx.toDouble())
            val clamped = angle.coerceIn(Math.toRadians(-160.0), Math.toRadians(-20.0))
            return (ARROW_SPEED * cos(clamped)).toFloat() to (ARROW_SPEED * sin(clamped)).toFloat()
        }
        if (dx > 30f) {
            val angle = Math.atan2(dy.toDouble(), dx.toDouble())
            val clamped = angle.coerceIn(Math.toRadians(20.0), Math.toRadians(160.0))
            return (ARROW_SPEED * cos(clamped)).toFloat() to (ARROW_SPEED * sin(clamped)).toFloat()
        }
        val len = hypot(dx.toDouble(), dy.toDouble()).coerceAtLeast(1.0)
        val nx = (dx / len).toFloat()
        val ny = (dy / len).toFloat()
        return (ARROW_SPEED * nx.coerceIn(-0.2f, 0.2f)) to (ARROW_SPEED * ny.coerceAtMost(-0.5f))
    }

    private enum class Landing { KIND_NORMAL, KIND_DRY, KIND_PAD }

    private fun findLanding(leaves: List<Leaf>, pig: Pig): Pair<Leaf, Landing>? {
        if (pig.vy < 0f) return null
        val bottom = pig.y + PIG_HALF_H
        for (leaf in leaves) {
            if (leaf.broken && leaf.kind == LeafKind.DRY) continue
            if (leaf.kind == LeafKind.DRY && !leaf.broken && bottom >= leaf.y && bottom <= leaf.y + 14f &&
                abs(pig.x - leaf.x) <= LEAF_WIDTH / 2f
            ) {
                return leaf to Landing.KIND_DRY
            }
            if (bottom >= leaf.y && bottom <= leaf.y + 14f && abs(pig.x - leaf.x) <= LEAF_WIDTH / 2f) {
                return leaf to if (leaf.kind == LeafKind.JUMPPAD) Landing.KIND_PAD else Landing.KIND_NORMAL
            }
        }
        return null
    }

    private data class Taken(val kind: PickupKind, val pattern: Int)

    private fun collectPickup(leaf: Leaf): Taken? {
        val pickup = leaf.pickup ?: return null
        if (leaf.pickupTaken) return null
        return Taken(pickup, leaf.pickupPattern)
    }

    private fun moveLeaves(leaves: List<Leaf>, dt: Float): List<Leaf> = leaves.map { leaf ->
        when (leaf.kind) {
            LeafKind.MOVER_H -> {
                val phase = leaf.phase + dt
                val x = leaf.anchorX + sin(phase / 700.0).toFloat() * 40f
                leaf.copy(phase = phase, x = x.coerceIn(LEAF_MIN_X, LEAF_MAX_X))
            }
            LeafKind.MOVER_V -> {
                val phase = leaf.phase + dt
                val y = leaf.anchorY + sin(phase / 800.0).toFloat() * 40f
                leaf.copy(phase = phase, y = y)
            }
            LeafKind.MOVER_CIRCLE -> {
                val phase = leaf.phase + dt
                val x = leaf.anchorX + cos(phase / 900.0).toFloat() * 26f
                val y = leaf.anchorY + sin(phase / 900.0).toFloat() * 26f
                leaf.copy(phase = phase, x = x.coerceIn(LEAF_MIN_X, LEAF_MAX_X), y = y)
            }
            else -> leaf
        }
    }

    private fun stepEnemy(enemy: Enemy, dt: Float, camera: Float): Enemy? {
        if (enemy.y > camera + VIEW_H + 240f) return null
        return when (enemy.kind) {
            EnemyKind.WASP -> enemy.copy(x = (enemy.x + enemy.vx * dt).coerceIn(12f, WORLD_W - 12f), phase = enemy.phase + dt)
            EnemyKind.SWARM -> enemy.copy(x = (enemy.x + enemy.vx * dt * 1.4f).coerceIn(12f, WORLD_W - 12f))
            else -> enemy.copy(phase = enemy.phase + dt)
        }
    }

    private fun stepBoss(boss: Boss, dt: Float): Boss? {
        if (boss.retreat) return null
        var x = boss.x + boss.vx * dt
        var vx = boss.vx
        if (x < 40f || x > WORLD_W - 40f) {
            vx = -vx
            x = x.coerceIn(40f, WORLD_W - 40f)
        }
        return boss.copy(x = x, vx = vx)
    }

    private data class SpawnResult(
        val leaves: List<Leaf>,
        val plans: List<LeafPlan>,
        val nextId: Int,
        val bossGroundSpawned: Int,
    )

    private fun spawnLeaves(
        leaves: List<Leaf>,
        plans: List<LeafPlan>,
        camera: Float,
        rng: BeanstalkRandom,
        nextId: Int,
        bossActive: Boolean,
    ): SpawnResult {
        var out = leaves
        var queue = plans
        var id = nextId
        var bossGround = 0
        val firstY = out.minOfOrNull { it.y } ?: return SpawnResult(out, queue, id, 0)
        var highest = firstY
        while (highest > camera + LEAF_SPAWN_MARGIN) {
            val plan = queue.firstOrNull()
            val kind: LeafKind
            val gap: Float
            val dx: Float
            if (plan != null) {
                queue = queue.drop(1)
                kind = plan.kind
                gap = plan.gap
                dx = plan.dx
            } else {
                kind = LeafKind.NORMAL
                gap = LEAF_GAP_MIN + rng.next((LEAF_GAP_MAX - LEAF_GAP_MIN).toInt() + 1)
                dx = 0f
            }
            if (kind == LeafKind.BOSS_GROUND) bossGround++
            val x = when (kind) {
                LeafKind.MOVER_H, LeafKind.MOVER_V, LeafKind.MOVER_CIRCLE -> (LEAF_MIN_X + LEAF_WIDTH / 2f + rng.next(160)).coerceIn(LEAF_MIN_X, LEAF_MAX_X)
                else -> LEAF_MIN_X + rng.next((LEAF_MAX_X - LEAF_MIN_X).toInt() + 1)
            }
            val anchorX = if (kind == LeafKind.MOVER_H || kind == LeafKind.MOVER_V || kind == LeafKind.MOVER_CIRCLE) {
                (x + dx).coerceIn(LEAF_MIN_X + LEAF_WIDTH / 2f, LEAF_MAX_X - LEAF_WIDTH / 2f)
            } else {
                x
            }
            val y = highest - gap
            var pickup: PickupKind? = null
            var pattern = 0
            if (kind != LeafKind.BOSS_GROUND && kind != LeafKind.DRY && rng.next(100) < PICKUP_PERCENT) {
                val roll = rng.next(10)
                pickup = when {
                    roll < 7 -> PickupKind.FOOD
                    roll < 9 -> PickupKind.SHIELD
                    else -> if (bossActive) null else PickupKind.BALLOON
                }
                pattern = rng.next(FOOD_PATTERNS)
            }
            out = out + Leaf(id = id++, x = anchorX, y = y, kind = kind, anchorX = anchorX, anchorY = y, pickup = pickup, pickupPattern = pattern)
            highest = y
        }
        if (out.size > 80) {
            out = out.filter { it.y < camera + VIEW_H + 300f }
        }
        return SpawnResult(out, queue, id, bossGround)
    }

    private fun balloonPop(x: Float, y: Float): List<Particle> {
        val out = ArrayList<Particle>(6)
        for (i in 0 until 6) {
            val angle = Math.toRadians(60.0 * i)
            out += Particle(
                x = x,
                y = y,
                vx = (cos(angle) * 0.12).toFloat(),
                vy = (sin(angle) * 0.12).toFloat(),
                remainingMs = 600L,
                tint = i % 3,
            )
        }
        return out
    }

    /* ------------------------------------------------------------------ */
    /* Cup cabinet: 10 materials, five tiers each, authored in-house.      */
    /* ------------------------------------------------------------------ */

    enum class CupMaterial(val label: String) {
        WOODEN("wooden"), STONE("stone"), COPPER("copper"), BRONZE("bronze"), SILVER("silver"),
        GOLD("gold"), EMERALD("emerald"), SAPPHIRE("sapphire"), RUBY("ruby"), DIAMOND("diamond"),
    }

    enum class CupCounter(val material: CupMaterial, val label: String, val tiers: IntArray) {
        HEIGHT(CupMaterial.WOODEN, "height", intArrayOf(10_000, 50_000, 200_000, 1_000_000, 5_000_000)),
        JUMPS(CupMaterial.STONE, "jumps", intArrayOf(100, 500, 2_500, 10_000, 25_000)),
        FLOWER_JUMPS(CupMaterial.COPPER, "flower jumps", intArrayOf(50, 100, 250, 500, 1_000)),
        ARROWS_FIRED(CupMaterial.BRONZE, "arrows fired", intArrayOf(100, 1_000, 10_000, 100_000, 1_000_000)),
        ARROWS_HIT(CupMaterial.SILVER, "arrows hit", intArrayOf(50, 500, 5_000, 50_000, 500_000)),
        ARROWS_MISSED(CupMaterial.GOLD, "arrows missed", intArrayOf(50, 500, 5_000, 50_000, 500_000)),
        BALLOONS(CupMaterial.EMERALD, "balloon rides", intArrayOf(10, 50, 200, 1_000, 5_000)),
        SHIELDS(CupMaterial.SAPPHIRE, "shields", intArrayOf(10, 50, 200, 1_000, 5_000)),
        STARTS(CupMaterial.RUBY, "starts", intArrayOf(10, 100, 1_000, 10_000, 50_000)),
        BOSSES(CupMaterial.DIAMOND, "bosses", intArrayOf(1, 5, 25, 100, 500)),
    }

    data class Banner(val counter: CupCounter, val tier: Int, val remainingMs: Long = BANNER_MS)

    data class Cups(
        val values: Map<CupCounter, Int> = emptyMap(),
        val tiers: Map<CupCounter, Int> = emptyMap(),
        val banners: List<Banner> = emptyList(),
    )

    fun tierFor(counter: CupCounter, value: Int): Int = counter.tiers.count { value >= it }

    fun mergeRun(cups: Cups, stats: RunStats, maxHeight: Int): Cups {
        var values = cups.values
        var tiers = cups.tiers
        var banners = cups.banners
        fun bump(counter: CupCounter, delta: Int) {
            if (delta <= 0 && counter != CupCounter.HEIGHT) return
            val value = (values[counter] ?: 0) + delta
            values = values + (counter to value)
            val tier = tierFor(counter, value)
            if (tier > (tiers[counter] ?: 0)) {
                tiers = tiers + (counter to tier)
                banners = banners + Banner(counter, tier)
            }
        }
        bump(CupCounter.HEIGHT, maxHeight)
        bump(CupCounter.JUMPS, stats.jumps)
        bump(CupCounter.FLOWER_JUMPS, stats.flowerJumps)
        bump(CupCounter.ARROWS_FIRED, stats.arrowsFired)
        bump(CupCounter.ARROWS_HIT, stats.arrowsHit)
        bump(CupCounter.ARROWS_MISSED, stats.arrowsMissed)
        bump(CupCounter.BALLOONS, stats.balloonRides)
        bump(CupCounter.SHIELDS, stats.shields)
        bump(CupCounter.STARTS, stats.starts)
        bump(CupCounter.BOSSES, stats.bossKills)
        return Cups(values, tiers, banners)
    }

    fun advanceBanners(cups: Cups, dtMs: Long): Cups {
        val next = cups.banners.mapNotNull { it.copy(remainingMs = it.remainingMs - dtMs).takeIf { b -> b.remainingMs > 0L } }
        return cups.copy(banners = next)
    }

    fun encodeCups(cups: Cups): String {
        val values = cups.values.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}:${it.value}" }
        val tiers = cups.tiers.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}:${it.value}" }
        return "v1|$values|$tiers"
    }

    fun decodeCups(blob: String?): Cups {
        if (blob.isNullOrBlank()) return Cups()
        val parts = blob.split("|")
        if (parts.size != 3 || parts[0] != "v1") return Cups()
        val values = decodeCounterMap(parts[1])
        val tiers = decodeCounterMap(parts[2])
        return Cups(values, tiers)
    }

    private fun decodeCounterMap(text: String): Map<CupCounter, Int> {
        if (text.isBlank()) return emptyMap()
        val out = HashMap<CupCounter, Int>()
        for (entry in text.split(",")) {
            val pair = entry.split(":")
            if (pair.size != 2) continue
            val counter = CupCounter.values().firstOrNull { it.name == pair[0] } ?: continue
            out[counter] = pair[1].toIntOrNull() ?: 0
        }
        return out
    }
}
