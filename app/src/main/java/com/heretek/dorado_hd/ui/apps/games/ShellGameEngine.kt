package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Shell Game of the Future (official "Shells" re-creation).
 *
 * Pure Kotlin and deterministic: path generation, the hidden robot and the
 * trophy pick all consume a seeded [Random] whose state lives in
 * [ShellState.seed]. Behaviour is re-derived from the spec
 * (`docs/apps/shell-game-of-the-future.md`); no Microsoft code or art is used.
 */
enum class ShellTier(val label: String, val speedPx: Float, val pathStart: Double, val prefix: String) {
    EASY("easy", 400f, 1.5, "easy-"),
    MEDIUM("medium", 475f, 2.5, "med-"),
    HARD("hard", 575f, 3.0, "hard-"),
}

enum class ShellPhase { SITTING, LAUNCH, FLYING, CHOOSE, SUCCESS, FAILURE }

data class ShellPoint(val x: Float, val y: Float)

data class ShellRobot(val pos: ShellPoint, val pathIndex: Int = 0, val carry: Float = 0f)

data class ShakeSample(val x: Float, val y: Float, val z: Float)

/**
 * Accelerometer shake detector: keeps the last 10 samples and fires when the
 * summed per-sample distance exceeds [ShellGameEngine.SHAKE_THRESHOLD].
 */
data class ShellShakeRing(val samples: List<ShakeSample> = emptyList()) {
    fun push(sample: ShakeSample): Pair<ShellShakeRing, Float?> {
        val next = (samples + sample).takeLast(ShellGameEngine.SHAKE_WINDOW)
        if (next.size < ShellGameEngine.SHAKE_WINDOW) return ShellShakeRing(next) to null
        var sum = 0f
        for (i in 1 until next.size) {
            val a = next[i - 1]
            val b = next[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            sum += sqrt(dx * dx + dy * dy + dz * dz)
        }
        return if (sum > ShellGameEngine.SHAKE_THRESHOLD) {
            ShellShakeRing(emptyList()) to sum
        } else {
            ShellShakeRing(next) to null
        }
    }
}

/**
 * Immutable game snapshot. During [ShellPhase.FLYING] each robot walks its
 * [paths] entry at the tier cruise speed; [paths] ends with the three shuffled
 * launch pads. [hiddenIndex] indexes [robots] (the only robot holding [trophy]).
 */
data class ShellState(
    val phase: ShellPhase = ShellPhase.SITTING,
    val charge: Float = 0f,
    val calmMs: Long = 0,
    val tier: ShellTier = ShellTier.EASY,
    val robots: List<ShellRobot> = ShellGameEngine.startRobots(),
    val paths: List<List<ShellPoint>> = emptyList(),
    val hiddenIndex: Int = -1,
    val trophy: String? = null,
    val chosenIndex: Int = -1,
    val holderRevealed: Boolean = false,
    val revealMs: Long = 0,
    val launchMs: Long = 0,
    val unlocked: Set<String> = emptySet(),
    val lastTrophies: List<String> = emptyList(),
    val seed: Int = 0,
) {
    val tierLamps: Int get() = ShellGameEngine.lamps(charge)
    val readyToChoose: Boolean get() = phase == ShellPhase.CHOOSE
}

object ShellGameEngine {
    const val CHARGE_MAX = 186f
    const val MEDIUM_LAMP = 72f
    const val HARD_LAMP = 144f
    const val LAUNCH_CHARGE_MIN = 1f
    const val LAUNCH_CALM_MS = 400L

    /** Shake detector: 10-sample window, summed distance over 2 fires. */
    const val SHAKE_WINDOW = 10
    const val SHAKE_THRESHOLD = 2f

    /** Each shake adds Strength * 4, and AdjustCharge halves that. */
    const val SHAKE_CHARGE_SCALE = 4f

    /**
     * Touch fallback charge injected by one tap on a sensorless device: the
     * same ladder as a stout shake, so the tier lamps remain reachable.
     */
    const val TAP_CHARGE = 60f

    /** Not-all-sitting drain: AdjustCharge(-5) => 2.5 units per 60 Hz frame. */
    const val DRAIN_PER_FRAME = 2.5f
    const val FRAME_MS = 1000f / 60f

    const val WAYPOINT_DECREMENT = 0.15
    const val REVEAL_DELAY_MS = 400L
    const val LAUNCH_TRANSITION_MS = 600L

    val GRID_X: List<Float> = listOf(10f, 52.5f, 95f, 137.5f, 180f)
    val PADS: List<ShellPoint> = listOf(
        ShellPoint(10f, 270f),
        ShellPoint(95f, 276f),
        ShellPoint(180f, 270f),
    )

    /**
     * Re-authored trophy pool. Names encode their tier (`easy-`, `med-`,
     * `hard-`); none of these are Microsoft trophy names.
     */
    val TROPHIES: List<String> = listOf(
        "easy-ignition", "easy-spanner", "easy-cog", "easy-bolt",
        "easy-spark", "easy-antenna", "easy-fuel-cell", "easy-dial",
        "med-turbine", "med-orbit", "med-comet", "med-gyro",
        "med-piston", "med-radar", "med-plasma", "med-capsule",
        "hard-nebula", "hard-quasar", "hard-pulsar", "hard-galaxy",
        "hard-warp", "hard-singularity", "hard-crown", "hard-event-horizon",
    )

    fun trophiesFor(tier: ShellTier): List<String> = TROPHIES.filter { it.startsWith(tier.prefix) }

    /** 11 / 17 / 21 waypoints are generated before the three pads. */
    fun waypointCount(tier: ShellTier): Int =
        (tier.pathStart / WAYPOINT_DECREMENT).toInt() + 1

    /**
     * Waypoints plus the robot's single final pad (the three pads are dealt
     * one per robot): 12 / 18 / 22 total path points.
     */
    fun pathLength(tier: ShellTier): Int = waypointCount(tier) + 1

    fun lamps(charge: Float): Int = when {
        charge >= HARD_LAMP -> 3
        charge >= MEDIUM_LAMP -> 2
        charge > 0f -> 1
        else -> 0
    }

    fun tierFor(charge: Float): ShellTier = when {
        charge < MEDIUM_LAMP -> ShellTier.EASY
        charge < HARD_LAMP -> ShellTier.MEDIUM
        else -> ShellTier.HARD
    }

    fun adjustCharge(charge: Float, amount: Float): Float =
        (charge + amount / 2f).coerceIn(0f, CHARGE_MAX)

    fun startRobots(): List<ShellRobot> = PADS.map { ShellRobot(it) }

    /** Fresh sitting state with nothing charged. */
    fun idle(unlocked: Set<String> = emptySet(), seed: Int = 0): ShellState =
        ShellState(unlocked = unlocked, seed = seed)

    /**
     * Touch fallback for devices without an accelerometer: a tap on the
     * SITTING table charges the boosters exactly as a shake would and resets
     * the launch-calm timer. Ignored outside [ShellPhase.SITTING].
     */
    fun tapCharge(state: ShellState): ShellState {
        if (state.phase != ShellPhase.SITTING) return state
        return state.copy(charge = adjustCharge(state.charge, TAP_CHARGE), calmMs = 0L)
    }

    /**
     * Returns to the sitting table after a reveal (or a manual reset), keeping
     * the unlocked collection and the last-five ring.
     */
    fun reset(state: ShellState): ShellState = ShellState(
        phase = ShellPhase.SITTING,
        charge = 0f,
        calmMs = 0,
        robots = startRobots(),
        unlocked = state.unlocked,
        lastTrophies = state.lastTrophies,
        seed = state.seed,
    )

    /**
     * One fixed-step update. [shakeStrength] is the summed accelerometer
     * distance from [ShellShakeRing] (null when no shake fired this frame).
     */
    fun step(state: ShellState, dtMs: Long, shakeStrength: Float? = null): ShellState {
        return when (state.phase) {
            ShellPhase.SITTING -> stepSitting(state, dtMs, shakeStrength)
            ShellPhase.LAUNCH -> {
                val drained = state.copy(
                    charge = drainedCharge(state.charge, dtMs),
                    launchMs = state.launchMs + dtMs,
                )
                if (drained.launchMs >= LAUNCH_TRANSITION_MS) drained.copy(phase = ShellPhase.FLYING, launchMs = 0) else drained
            }
            ShellPhase.FLYING -> {
                val drained = state.copy(charge = drainedCharge(state.charge, dtMs))
                val moved = moveRobots(drained, dtMs)
                if (allArrived(moved)) moved.copy(phase = ShellPhase.CHOOSE) else moved
            }
            ShellPhase.CHOOSE -> state
            ShellPhase.FAILURE -> {
                if (state.holderRevealed) {
                    state
                } else {
                    val elapsed = state.revealMs + dtMs
                    if (elapsed >= REVEAL_DELAY_MS) state.copy(revealMs = elapsed, holderRevealed = true)
                    else state.copy(revealMs = elapsed)
                }
            }
            ShellPhase.SUCCESS -> state
        }
    }

    private fun stepSitting(state: ShellState, dtMs: Long, shakeStrength: Float?): ShellState {
        var charge = state.charge
        var calm = state.calmMs + dtMs
        if (shakeStrength != null && shakeStrength > SHAKE_THRESHOLD) {
            charge = adjustCharge(charge, shakeStrength * SHAKE_CHARGE_SCALE)
            calm = 0
        }
        val charged = state.copy(charge = charge, calmMs = calm)
        return if (charged.charge > LAUNCH_CHARGE_MIN && charged.calmMs >= LAUNCH_CALM_MS) {
            prepare(charged)
        } else {
            charged
        }
    }

    private fun drainedCharge(charge: Float, dtMs: Long): Float {
        val frames = dtMs / FRAME_MS
        return (charge - DRAIN_PER_FRAME * frames).coerceIn(0f, CHARGE_MAX)
    }

    /**
     * Quantises the charge into a tier, builds the three flight paths from a
     * seeded RNG, hides one trophy behind one robot and enters [ShellPhase.LAUNCH].
     */
    fun prepare(state: ShellState): ShellState {
        val rng = Random(state.seed)
        val tier = tierFor(state.charge)
        val paths = buildPaths(tier, rng)
        val hidden = rng.nextInt(3)
        val trophy = pickTrophy(tier, state.unlocked, state.lastTrophies, rng)
        val last = if (trophy == null) state.lastTrophies else (state.lastTrophies + trophy).takeLast(5)
        return state.copy(
            phase = ShellPhase.LAUNCH,
            tier = tier,
            paths = paths,
            hiddenIndex = hidden,
            trophy = trophy,
            chosenIndex = -1,
            holderRevealed = false,
            revealMs = 0,
            launchMs = 0,
            lastTrophies = last,
            seed = rng.nextInt(),
        )
    }

    /**
     * Pick a trophy for [tier]: locked trophies first, avoiding the last five
     * awarded when possible, then falling back to any tier trophy.
     */
    fun pickTrophy(tier: ShellTier, unlocked: Set<String>, last: List<String>, rng: Random): String? {
        val pool = trophiesFor(tier)
        if (pool.isEmpty()) return null
        val locked = pool.filter { it !in unlocked }
        val preferred = (if (locked.isNotEmpty()) locked else pool).filter { it !in last }
        val candidates = if (preferred.isNotEmpty()) preferred else pool
        return candidates[rng.nextInt(candidates.size)]
    }

    /** Builds the three paths: waypoints then the shuffled launch pads. */
    fun buildPaths(tier: ShellTier, rng: Random): List<List<ShellPoint>> {
        val iterations = waypointCount(tier)
        val grid = ArrayList<ShellPoint>(gridPoints())
        val paths = MutableList(3) { ArrayList<ShellPoint>() }
        for (i in 0 until iterations) {
            if (i == 0) {
                for (robot in 0 until 3) {
                    val start = PADS[robot]
                    paths[robot].add(ShellPoint(start.x, start.y - 160f))
                }
                continue
            }
            val taken = ArrayList<ShellPoint>(3)
            for (robot in 0 until 3) {
                if (grid.isEmpty()) {
                    grid.addAll(gridPoints())
                    grid.removeAll(taken.toSet())
                }
                var index = rng.nextInt(grid.size)
                var candidate = grid[index]
                val previous = paths[robot].last()
                var guard = 0
                while ((candidate == previous || candidate in taken) && guard < grid.size * 4) {
                    index = rng.nextInt(grid.size)
                    candidate = grid[index]
                    guard++
                }
                paths[robot].add(candidate)
                grid.removeAt(index)
                taken.add(candidate)
            }
        }
        val pads = ArrayList(PADS)
        for (robot in 0 until 3) {
            val index = rng.nextInt(pads.size)
            paths[robot].add(pads.removeAt(index))
        }
        return paths.map { it.toList() }
    }

    fun gridPoints(): List<ShellPoint> {
        val out = ArrayList<ShellPoint>(250)
        for (y in 5..250 step 5) {
            for (x in GRID_X) out.add(ShellPoint(x, y.toFloat()))
        }
        return out
    }

    private fun moveRobots(state: ShellState, dtMs: Long): ShellState {
        if (state.paths.size < 3) return state
        val stepPx = state.tier.speedPx * dtMs / 1000f
        val robots = state.robots.mapIndexed { index, robot ->
            val path = state.paths[index]
            if (path.isEmpty() || robot.pathIndex >= path.lastIndex && robot.pos == path.last()) return@mapIndexed robot
            var pos = robot.pos
            var waypoint = robot.pathIndex
            var move = stepPx + robot.carry
            var guard = 0
            while (move > 0f && waypoint < path.lastIndex && guard < 1000) {
                guard++
                val target = path[waypoint + 1]
                val dx = target.x - pos.x
                val dy = target.y - pos.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= 0.0001f) {
                    waypoint++
                    pos = target
                    continue
                }
                if (move >= distance) {
                    pos = target
                    move -= distance
                    waypoint++
                } else {
                    pos = ShellPoint(pos.x + dx / distance * move, pos.y + dy / distance * move)
                    move = 0f
                }
            }
            ShellRobot(pos = pos, pathIndex = waypoint, carry = if (waypoint >= path.lastIndex) 0f else move)
        }
        return state.copy(robots = robots)
    }

    fun allArrived(state: ShellState): Boolean {
        if (state.paths.size < 3) return false
        return state.robots.indices.all { i ->
            val path = state.paths[i]
            path.isNotEmpty() && state.robots[i].pathIndex >= path.lastIndex && state.robots[i].pos == path.last()
        }
    }

    /** Tap a robot after the flight; before that the caller shows the tip. */
    fun choose(state: ShellState, robotIndex: Int): ShellState {
        if (state.phase != ShellPhase.CHOOSE) return state
        if (robotIndex !in 0 until 3) return state
        val correct = robotIndex == state.hiddenIndex && state.trophy != null
        return if (correct) {
            state.copy(
                phase = ShellPhase.SUCCESS,
                chosenIndex = robotIndex,
                unlocked = state.trophy?.let { state.unlocked + it } ?: state.unlocked,
            )
        } else {
            state.copy(
                phase = ShellPhase.FAILURE,
                chosenIndex = robotIndex,
                holderRevealed = false,
                revealMs = 0,
            )
        }
    }
}
