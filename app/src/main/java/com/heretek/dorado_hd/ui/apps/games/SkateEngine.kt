package com.heretek.dorado_hd.ui.apps.games

import com.heretek.dorado_hd.ui.apps.engine3d.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room pool-skate mechanics re-derived from docs/apps/vans-sk8-pool-service.md.
 * No Microsoft code, strings, level data or assets are involved: every pool,
 * skater, board, wheel, trick pattern and sound cue here is authored for Dorado.
 *
 * The engine is pure Kotlin and frame-deterministic: [SkateEngine.step] drains an
 * accumulator at a fixed 1/60 s timestep, so identical seeds and inputs always
 * produce identical states.
 */
enum class SkatePhase { IN_POOL, IN_AIR, GRINDING, CRASHING, FINISHED }

enum class SkateSwipe { UP, DOWN, LEFT, RIGHT }

enum class GrindKind(val label: String) {
    FIFTY_FIFTY("fifty-fifty"),
    BOARD_SLIDE("board slide"),
}

enum class SkateResult { IN_PROGRESS, SUCCESS, FAILED }

enum class SkateObjective { FREE_RIDE, SCORE, NO_BAILS, COLLECT_BAGS, SPIN, TRICK_CHAIN, UNIQUE_TRICKS }

enum class SkateEventId(val key: String) {
    FREE_RIDE("freeRide"),
    TRAINING("training"),
    TIMED_RUN("timedRun"),
    POOL_CLEANER("poolCleaner"),
    THE_GRIND("theGrind"),
    PRO("pro"),
    SPIN_TO_WIN("spin2Win"),
    NEVER_ENOUGH_TIME("neverEnoughTime"),
    BEST_RUN("bestRun"),
    SKATE("skate"),
    NO_BAILS("noBails"),
    POOL_JAM("poolJam"),
    ;

    companion object {
        fun fromKey(key: String): SkateEventId? = entries.firstOrNull { it.key == key }
    }
}

/** One trick: name, base value, the bail window its animation must clear and a grind flag. */
data class SkateTrick(
    val id: String,
    val name: String,
    val points: Int,
    val bailTimeMs: Int,
    val grind: Boolean = false,
    val special: Boolean = false,
)

object SkateTricks {
    val GRINDS: List<SkateTrick> = listOf(
        SkateTrick("fiftyFifty", "fifty-fifty grind", 0, 1000, grind = true),
        SkateTrick("boardSlide", "board slide", 0, 1000, grind = true),
    )

    val AIRS: List<SkateTrick> = listOf(
        SkateTrick("airWalk", "air walk", 1200, 1600),
        SkateTrick("staleFish", "stale fish", 1000, 1200),
        SkateTrick("backsideAir", "backside air", 1300, 1400),
        SkateTrick("indyGrab", "indy grab", 1200, 1200),
        SkateTrick("frontsideAir", "frontside air", 1200, 1200),
        SkateTrick("christAir", "christ air", 1500, 1600),
        SkateTrick("japanAir", "japan air", 1600, 1500),
        SkateTrick("judoAir", "judo air", 1600, 1400),
        SkateTrick("backFlip", "back flip", 1400, 1600),
        SkateTrick("fiveFortyAir", "540 air", 1400, 1600),
        SkateTrick("kickFlip", "kick flip", 1500, 1400),
        SkateTrick("lienAir", "lien air", 1500, 1400),
        SkateTrick("slobAir", "slob air", 1700, 1600),
    )

    val SPECIALS: List<SkateTrick> = listOf(
        SkateTrick("calderPunch", "calder punch", 1800, 1700, special = true),
        SkateTrick("quinnWhip", "quinn whip", 1800, 1700, special = true),
        SkateTrick("hanHammer", "han hammer", 1800, 1700, special = true),
        SkateTrick("santosSpiral", "santos spiral", 1800, 1700, special = true),
    )

    val ALL: List<SkateTrick> = GRINDS + AIRS + SPECIALS

    fun byId(id: String): SkateTrick? = ALL.firstOrNull { it.id == id }

    fun points(id: String): Int = byId(id)?.points ?: 0
}

data class SkateSkater(
    val id: String,
    val name: String,
    val specialId: String,
    val region: String,
)

data class SkateGear(
    val id: String,
    val name: String,
    /** Normalised 0..1 design property; mapped to stats by [SkateEngine.statsFor]. */
    val propA: Float,
    val propB: Float,
    val unlockStars: Int = 0,
)

data class SkateBag(val angle: Float, val radius: Float)

data class SkateRamp(
    val x: Float,
    val z: Float,
    val halfWidth: Float,
    val halfDepth: Float,
    val launch: Float,
)

data class GrindEdge(val ax: Float, val az: Float, val bx: Float, val bz: Float)

data class SkatePool(
    val id: String,
    val name: String,
    val lip: Float,
    val depth: Float,
    val radius: Float,
    val unlockStars: Int,
    val ramps: List<SkateRamp>,
    val rails: List<GrindEdge>,
    val bags: List<SkateBag>,
)

data class SkateEventDef(
    val id: SkateEventId,
    val title: String,
    val timerSeconds: Float,
    val maxMultiplier: Int,
    val multDecrement: Float,
    val objective: SkateObjective,
    val target: Int,
    val star1: Int,
    val star2: Int,
    val star3: Int,
    val unlockAfter: SkateEventId? = null,
)

data class SkateAchievement(val id: String, val event: SkateEventId, val stars: Int, val title: String)

data class SkateVideo(val id: String, val title: String, val unlockStars: Int)

object SkateContent {

    val POOLS: List<SkatePool> = listOf(
        SkatePool(
            id = "sundeck",
            name = "sundeck bowl",
            lip = 7.1f,
            depth = 3.1f,
            radius = 4.6f,
            unlockStars = 0,
            ramps = listOf(SkateRamp(x = 0f, z = -6.4f, halfWidth = 1.4f, halfDepth = 0.9f, launch = 0.62f)),
            rails = listOf(GrindEdge(ax = -3.4f, az = -7.6f, bx = 3.4f, bz = -7.6f)),
            bags = listOf(
                SkateBag(0.4f, 1.6f),
                SkateBag(2.4f, 2.4f),
                SkateBag(4.1f, 1.2f),
                SkateBag(5.3f, 2.8f),
                SkateBag(1.1f, 3.4f),
            ),
        ),
        SkatePool(
            id = "vapor",
            name = "vapor basin",
            lip = 7.4f,
            depth = 3.4f,
            radius = 5.0f,
            unlockStars = 2,
            ramps = listOf(
                SkateRamp(x = -1.2f, z = -7.0f, halfWidth = 1.5f, halfDepth = 1.0f, launch = 0.6f),
                SkateRamp(x = 1.6f, z = 7.0f, halfWidth = 1.3f, halfDepth = 0.9f, launch = 0.68f),
            ),
            rails = listOf(GrindEdge(ax = -3.8f, az = -8.2f, bx = 3.8f, bz = -8.2f)),
            bags = listOf(
                SkateBag(0.9f, 1.8f),
                SkateBag(2.1f, 3.1f),
                SkateBag(3.7f, 1.4f),
                SkateBag(4.9f, 3.6f),
                SkateBag(5.9f, 1.9f),
            ),
        ),
        SkatePool(
            id = "cinder",
            name = "cinder halo",
            lip = 7.0f,
            depth = 3.0f,
            radius = 4.2f,
            unlockStars = 5,
            ramps = listOf(SkateRamp(x = 5.6f, z = 0f, halfWidth = 0.9f, halfDepth = 1.5f, launch = 0.72f)),
            rails = listOf(GrindEdge(ax = -3.0f, az = -6.4f, bx = 3.0f, bz = -6.4f)),
            bags = listOf(
                SkateBag(0.2f, 1.4f),
                SkateBag(1.8f, 2.6f),
                SkateBag(3.3f, 1.1f),
                SkateBag(4.6f, 2.2f),
                SkateBag(5.5f, 3.0f),
            ),
        ),
        SkatePool(
            id = "night",
            name = "night tide",
            lip = 7.6f,
            depth = 3.6f,
            radius = 5.4f,
            unlockStars = 8,
            ramps = listOf(
                SkateRamp(x = 0f, z = -7.6f, halfWidth = 1.6f, halfDepth = 1.1f, launch = 0.6f),
                SkateRamp(x = -5.0f, z = 5.0f, halfWidth = 1.1f, halfDepth = 1.1f, launch = 0.66f),
            ),
            rails = listOf(GrindEdge(ax = -4.2f, az = -8.8f, bx = 4.2f, bz = -8.8f)),
            bags = listOf(
                SkateBag(0.7f, 2.0f),
                SkateBag(2.2f, 3.4f),
                SkateBag(3.5f, 1.5f),
                SkateBag(4.4f, 3.8f),
                SkateBag(5.8f, 2.4f),
            ),
        ),
    )

    val SKATERS: List<SkateSkater> = listOf(
        SkateSkater("rex", "rex calder", "calderPunch", "backyard"),
        SkateSkater("mila", "mila quinn", "quinnWhip", "rooftop"),
        SkateSkater("duke", "duke han", "hanHammer", "harbor"),
        SkateSkater("ivy", "ivy santos", "santosSpiral", "valley"),
    )

    val BOARDS: List<SkateGear> = listOf(
        "slate ghost", "ember deck", "mint riot", "cobalt kid", "blacktop",
        "neon rig", "paper crane", "dust devil", "glasshouse", "redline",
        "nightshift", "warpath", "honeycomb", "cold brew", "sunspot",
        "tin roof", "velvet axe", "highwire", "back alley", "salt flat", "last call",
    ).mapIndexed { index, name ->
        val t = index / 20f
        SkateGear(
            id = "board${index + 1}",
            name = name,
            propA = t,
            propB = 1f - t,
            unlockStars = index * 7 / 4,
        )
    }

    val WHEELS: List<SkateGear> = listOf(
        "redline 52", "cloud 54", "hard eight", "grit 50", "signal 53", "vapor 55",
        "stone 51", "glide 56", "cut 49", "ember 54", "night 52",
    ).mapIndexed { index, name ->
        val t = index / 10f
        SkateGear(
            id = "wheel${index + 1}",
            name = name,
            propA = t,
            propB = 1f - t,
            unlockStars = index * 7 / 2,
        )
    }

    val EVENTS: List<SkateEventDef> = listOf(
        SkateEventDef(SkateEventId.FREE_RIDE, "free ride", 0f, 5, 5f, SkateObjective.FREE_RIDE, 0, 0, 0, 0),
        SkateEventDef(SkateEventId.TRAINING, "training", 0f, 5, 5f, SkateObjective.FREE_RIDE, 0, 0, 0, 0),
        SkateEventDef(SkateEventId.TIMED_RUN, "timed run", 91f, 5, 5f, SkateObjective.SCORE, 25000, 25000, 40000, 60000, SkateEventId.TRAINING),
        SkateEventDef(SkateEventId.POOL_CLEANER, "pool cleaner", 91f, 5, 5f, SkateObjective.COLLECT_BAGS, 5, 10000, 25000, 40000, SkateEventId.TIMED_RUN),
        SkateEventDef(SkateEventId.THE_GRIND, "the grind", 91f, 5, 2f, SkateObjective.SCORE, 30000, 30000, 45000, 70000, SkateEventId.POOL_CLEANER),
        SkateEventDef(SkateEventId.PRO, "pro", 91f, 5, 5f, SkateObjective.SCORE, 50000, 50000, 75000, 120000, SkateEventId.THE_GRIND),
        SkateEventDef(SkateEventId.SPIN_TO_WIN, "spin to win", 91f, 5, 5f, SkateObjective.SPIN, 7200, 30000, 45000, 70000, SkateEventId.PRO),
        SkateEventDef(SkateEventId.NEVER_ENOUGH_TIME, "never enough time", 31f, 5, 5f, SkateObjective.SCORE, 75000, 75000, 100000, 150000, SkateEventId.SPIN_TO_WIN),
        SkateEventDef(SkateEventId.BEST_RUN, "best run", 91f, 10, 5f, SkateObjective.SCORE, 25000, 25000, 50000, 90000, SkateEventId.NEVER_ENOUGH_TIME),
        SkateEventDef(SkateEventId.SKATE, "skate", 0f, 5, 5f, SkateObjective.TRICK_CHAIN, 5, 15000, 25000, 40000, SkateEventId.BEST_RUN),
        SkateEventDef(SkateEventId.NO_BAILS, "no bails", 91f, 5, 5f, SkateObjective.NO_BAILS, 25000, 25000, 40000, 65000, SkateEventId.SKATE),
        SkateEventDef(SkateEventId.POOL_JAM, "pool jam", 0f, 5, 0.8f, SkateObjective.UNIQUE_TRICKS, 16, 15000, 25000, 40000, SkateEventId.NO_BAILS),
    )

    val SKATE_CHAIN: List<String> = listOf("indyGrab", "japanAir", "kickFlip", "judoAir", "special")

    val ACHIEVEMENTS: List<SkateAchievement> = listOf(
        SkateEventId.POOL_CLEANER, SkateEventId.THE_GRIND, SkateEventId.PRO,
        SkateEventId.SPIN_TO_WIN, SkateEventId.NEVER_ENOUGH_TIME, SkateEventId.BEST_RUN,
        SkateEventId.SKATE, SkateEventId.NO_BAILS, SkateEventId.POOL_JAM,
    ).flatMap { event ->
        listOf(
            SkateAchievement("${event.key}1", event, 1, "${eventTitle(event)} starter"),
            SkateAchievement("${event.key}2", event, 2, "${eventTitle(event)} regular"),
            SkateAchievement("${event.key}3", event, 3, "${eventTitle(event)} legend"),
        )
    }

    val VIDEOS: List<SkateVideo> = listOf(
        SkateVideo("backyard", "backyard session", 9),
        SkateVideo("rooftop", "rooftop session", 18),
        SkateVideo("crew", "crew cut", 27),
        SkateVideo("full", "full part", 36),
    )

    fun event(id: SkateEventId): SkateEventDef = EVENTS.first { it.id == id }

    fun pool(id: String): SkatePool = POOLS.firstOrNull { it.id == id } ?: POOLS[0]

    fun skater(id: String): SkateSkater = SKATERS.firstOrNull { it.id == id } ?: SKATERS[0]

    fun board(id: String): SkateGear = BOARDS.firstOrNull { it.id == id } ?: BOARDS[0]

    fun wheel(id: String): SkateGear = WHEELS.firstOrNull { it.id == id } ?: WHEELS[0]

    fun eventTitle(id: SkateEventId): String = when (id) {
        SkateEventId.FREE_RIDE -> "free ride"
        SkateEventId.TRAINING -> "training"
        SkateEventId.TIMED_RUN -> "timed run"
        SkateEventId.POOL_CLEANER -> "pool cleaner"
        SkateEventId.THE_GRIND -> "the grind"
        SkateEventId.PRO -> "pro"
        SkateEventId.SPIN_TO_WIN -> "spin to win"
        SkateEventId.NEVER_ENOUGH_TIME -> "never enough time"
        SkateEventId.BEST_RUN -> "best run"
        SkateEventId.SKATE -> "skate"
        SkateEventId.NO_BAILS -> "no bails"
        SkateEventId.POOL_JAM -> "pool jam"
    }
}

/** Deterministic 32-bit LCG used for per-seed bag layout. */
class SkateRng(seed: Int) {
    private var state: Int = if (seed == 0) 0x51ED270B.toInt() else seed

    fun nextInt(): Int {
        state = state * 1103515245 + 12345
        return state ushr 16
    }

    fun nextFloat(): Float = (nextInt() and 0x7FFF) / 32767f
}

data class TimedSwipe(val swipe: SkateSwipe, val atMs: Float)

/** The mapped gear stat block. */
data class SkateStats(
    val spin: Float,
    val balance: Float,
    val control: Float,
    val landing: Float,
    val speed: Float,
)

data class SkateState(
    val event: SkateEventId = SkateEventId.FREE_RIDE,
    val pool: SkatePool = SkateContent.POOLS[0],
    val skater: SkateSkater = SkateContent.SKATERS[0],
    val board: SkateGear = SkateContent.BOARDS[0],
    val wheel: SkateGear = SkateContent.WHEELS[0],
    val seed: Int = 1,
    val phase: SkatePhase = SkatePhase.IN_POOL,
    val position: Vec3 = Vec3(0f, 4f, 0f),
    val direction: Vec3 = Vec3(0f, 0f, -1f),
    val boardFacing: Vec3 = Vec3(0f, 0f, -1f),
    val speed: Float = 0f,
    val airVertical: Float = 0f,
    val airPlanar: Float = 0f,
    val spinRad: Float = 0f,
    val spinTotalDeg: Int = 0,
    val balancePoint: Float = 0f,
    val balanceSpeed: Float = 0f,
    val controlSpeed: Float = 0f,
    val grindKind: GrindKind = GrindKind.FIFTY_FIFTY,
    val grindMs: Int = 0,
    val grindMsTotal: Int = 0,
    val grindCount: Int = 0,
    val grindSwitches: Int = 0,
    val grindEdge: Int = -1,
    val grindRight: Boolean = true,
    val combo: List<SkateTrick> = emptyList(),
    val recentSwipes: List<TimedSwipe> = emptyList(),
    val lastTrickMs: Float = -100000f,
    val lastTrickBailMs: Int = 0,
    val score: Int = 0,
    val multiplier: Int = 1,
    val multiplierTimerMs: Float = 0f,
    val elapsedMs: Float = 0f,
    val eventTimerMs: Float = 0f,
    val bailed: Boolean = false,
    val crashes: Int = 0,
    val crashMs: Float = 0f,
    val perfectLandings: Int = 0,
    val landingFeedback: String? = null,
    val collected: List<Int> = emptyList(),
    val collectedTrickIds: List<String> = emptyList(),
    val chainIndex: Int = 0,
    val accumulatorMs: Float = 0f,
    val tiltedX: Float = 0f,
    val tiltedY: Float = 0f,
    val result: SkateResult = SkateResult.IN_PROGRESS,
) {
    val finished: Boolean get() = phase == SkatePhase.FINISHED
}

object SkateEngine {

    const val GRAVITY = 10.8f
    const val DRAG_FACTOR = 0.974f
    const val ACCELERATION = 20f
    const val MIN_ACCEL_MULT = 0.9f
    const val MAX_ACCEL_MULT = 1.19f
    const val MIN_SPIN_MULT = 0.82f
    const val MAX_SPIN_MULT = 1.39f
    const val MIN_BALANCE_MULT = 2f
    const val MAX_BALANCE_MULT = 0.5f
    const val MIN_LANDING_MULT = 0.3f
    const val MAX_LANDING_MULT = 2f
    const val LANDING_ANGLE = PI.toFloat() / 12f
    const val MIN_GRIND_SPEED = 6f
    const val PUSH_SPEED = 11f
    const val BALANCE_POINT_SPEED_MODIFIER = 0.4f
    const val BALANCE_MAX_VALUE = 40f
    const val BALANCE_DEAD_ZONE = 0.05f
    const val ACCELERATOR_MODIFIER = 3f
    const val CONTROL_SPEED_MODIFIER = 8f
    const val SPIN_RATE = 5.1f
    const val TURN_RATE = 1.25f
    const val ROOT_Y = 4f
    const val FIXED_STEP_MS = 1000f / 60f
    const val FIXED_DT = 1f / 60f
    const val SWIPE_WINDOW_MS = 520f
    const val PAIR_WINDOW_MS = 260f
    const val MAX_CHAIN = 4
    const val COPING_BAND = 0.9f
    const val RAIL_SNAP = 1.1f
    const val CRASH_RECOVERY_MS = 1200f
    const val GRIND_MULTIPLIER_STEP_MS = 4000
    const val MULTIPLIER_DECAY_UNIT_MS = 7000f
    const val SPECIAL_PATTERN = "special"

    private val PATTERNS: List<Pair<List<SkateSwipe>, String>> = listOf(
        listOf(SkateSwipe.UP, SkateSwipe.UP) to "judoAir",
        listOf(SkateSwipe.DOWN, SkateSwipe.DOWN) to "airWalk",
        listOf(SkateSwipe.LEFT, SkateSwipe.LEFT) to "slobAir",
        listOf(SkateSwipe.RIGHT, SkateSwipe.RIGHT) to "backFlip",
        listOf(SkateSwipe.UP, SkateSwipe.DOWN) to "christAir",
        listOf(SkateSwipe.DOWN, SkateSwipe.UP) to "backsideAir",
        listOf(SkateSwipe.LEFT, SkateSwipe.RIGHT) to "kickFlip",
        listOf(SkateSwipe.RIGHT, SkateSwipe.LEFT) to "lienAir",
        listOf(SkateSwipe.UP, SkateSwipe.LEFT) to "fiveFortyAir",
        listOf(SkateSwipe.UP, SkateSwipe.RIGHT) to SPECIAL_PATTERN,
        listOf(SkateSwipe.UP) to "japanAir",
        listOf(SkateSwipe.DOWN) to "staleFish",
        listOf(SkateSwipe.LEFT) to "indyGrab",
        listOf(SkateSwipe.RIGHT) to "frontsideAir",
    )

    private val PATTERNS_BY_LENGTH: List<Pair<List<SkateSwipe>, String>> =
        PATTERNS.sortedByDescending { it.first.size }

    fun newSession(
        event: SkateEventId,
        poolId: String = SkateContent.POOLS[0].id,
        skaterId: String = SkateContent.SKATERS[0].id,
        boardId: String = SkateContent.BOARDS[0].id,
        wheelId: String = SkateContent.WHEELS[0].id,
        seed: Int = 1,
    ): SkateState {
        val pool = SkateContent.pool(poolId)
        val def = SkateContent.event(event)
        val start = Vec3(0f, surfaceHeight(pool, 0f, 0f), 0f)
        return SkateState(
            event = event,
            pool = pool,
            skater = SkateContent.skater(skaterId),
            board = SkateContent.board(boardId),
            wheel = SkateContent.wheel(wheelId),
            seed = seed,
            position = start,
            direction = Vec3(0f, 0f, -1f),
            boardFacing = Vec3(0f, 0f, -1f),
            eventTimerMs = def.timerSeconds * 1000f,
        )
    }

    fun statsFor(board: SkateGear, wheel: SkateGear): SkateStats = SkateStats(
        spin = lerp(MIN_SPIN_MULT, MAX_SPIN_MULT, board.propA),
        balance = lerp(MIN_BALANCE_MULT, MAX_BALANCE_MULT, board.propB),
        control = lerp(2.5f, 0.75f, wheel.propA),
        landing = lerp(MIN_LANDING_MULT, MAX_LANDING_MULT, wheel.propA),
        speed = lerp(MIN_ACCEL_MULT, MAX_ACCEL_MULT, wheel.propB),
    )

    fun stats(state: SkateState): SkateStats = statsFor(state.board, state.wheel)

    fun lerp(min: Float, max: Float, t: Float): Float = min + (max - min) * t

    /** Analytic bowl surface: flat deck outside the lip, paraboloid inside. */
    fun surfaceHeight(pool: SkatePool, x: Float, z: Float): Float {
        val r = sqrt(x * x + z * z)
        if (r >= pool.radius) return pool.lip
        val t = (r / pool.radius) * (r / pool.radius)
        return pool.lip - pool.depth * (1f - t)
    }

    fun surfaceNormal(pool: SkatePool, x: Float, z: Float): Vec3 {
        val r = sqrt(x * x + z * z)
        if (r >= pool.radius || r < 1e-4f) return Vec3.UP
        val slope = 2f * pool.depth * r / (pool.radius * pool.radius)
        return Vec3(-slope * x / r, 1f, -slope * z / r).normalized()
    }

    /** Gravity projected onto the surface tangent plane; points down the slope. */
    fun slopeAcceleration(normal: Vec3): Vec3 = Vec3(
        GRAVITY * normal.x * normal.y,
        -GRAVITY * (1f - normal.y * normal.y),
        GRAVITY * normal.z * normal.y,
    )

    /** Ramp launch split into vertical and planar air velocities. */
    fun rampLaunch(speed: Float, direction: Vec3): Pair<Float, Float> {
        val planar = sqrt(direction.x * direction.x + direction.z * direction.z)
        return (speed * direction.y) to (speed * planar)
    }

    fun radiusOf(position: Vec3): Float = sqrt(position.x * position.x + position.z * position.z)

    /** Original rounding: nearest 180 degrees of accumulated spin. */
    fun formatRotation(spinRad: Float): Int {
        val degrees = Math.toDegrees(spinRad.toDouble()).let { abs(it).toFloat() }
        return ((degrees + 45f) / 180f).toInt() * 180
    }

    fun grindPoints(grindMs: Int): Int = grindMs / 100 * 10

    fun trickScore(combo: List<SkateTrick>): Int = combo.sumOf { it.points }

    fun angleBetween(first: Vec3, second: Vec3): Float {
        val a = first.normalized()
        val b = second.normalized()
        if (a.length() < 1e-4f || b.length() < 1e-4f) return 0f
        return acos(a.dot(b).coerceIn(-1f, 1f))
    }

    /** Longest-suffix swipe match; a single swipe resolves to a base aerial. */
    fun detectTrick(swipes: List<SkateSwipe>, specialId: String): Pair<SkateTrick, Int>? {
        for ((pattern, id) in PATTERNS_BY_LENGTH) {
            if (swipes.size < pattern.size) continue
            if (swipes.takeLast(pattern.size) != pattern) continue
            val resolved = if (id == SPECIAL_PATTERN) specialId else id
            val trick = SkateTricks.byId(resolved) ?: continue
            return trick to pattern.size
        }
        return null
    }

    fun push(state: SkateState): SkateState {
        if (state.phase != SkatePhase.IN_POOL || state.finished) return state
        return state.copy(speed = max(state.speed, PUSH_SPEED))
    }

    fun tilt(state: SkateState, x: Float, y: Float): SkateState =
        state.copy(tiltedX = x.coerceIn(-1f, 1f), tiltedY = y.coerceIn(-1f, 1f))

    fun swipe(state: SkateState, swipe: SkateSwipe): SkateState {
        if (state.finished) return state
        if (state.phase == SkatePhase.GRINDING) {
            if (swipe == SkateSwipe.LEFT || swipe == SkateSwipe.RIGHT) {
                val next = if (state.grindKind == GrindKind.FIFTY_FIFTY) GrindKind.BOARD_SLIDE else GrindKind.FIFTY_FIFTY
                return state.copy(grindKind = next, grindSwitches = state.grindSwitches + 1)
            }
            return state
        }
        if (state.phase != SkatePhase.IN_AIR) return state
        var buffer = (state.recentSwipes + TimedSwipe(swipe, state.elapsedMs))
            .filter { state.elapsedMs - it.atMs <= SWIPE_WINDOW_MS }
        val pair = if (buffer.size >= 2) {
            detectTrick(buffer.takeLast(2).map { it.swipe }, state.skater.specialId)?.takeIf { it.second == 2 }
        } else {
            null
        }
        if (pair != null) {
            if (state.combo.size >= MAX_CHAIN) return state.copy(recentSwipes = buffer)
            return state.copy(
                recentSwipes = buffer.dropLast(2),
                combo = state.combo + pair.first,
                lastTrickMs = state.elapsedMs,
                lastTrickBailMs = pair.first.bailTimeMs,
            )
        }
        return resolveExpiredSwipes(state.copy(recentSwipes = buffer))
    }

    /** A lone swipe becomes a base aerial once its window to pair up has passed. */
    private fun resolveExpiredSwipes(state: SkateState): SkateState {
        if (state.phase != SkatePhase.IN_AIR) return state
        var buffer = state.recentSwipes
        var combo = state.combo
        var lastMs = state.lastTrickMs
        var lastBail = state.lastTrickBailMs
        while (buffer.isNotEmpty() && state.elapsedMs - buffer.first().atMs > PAIR_WINDOW_MS) {
            val single = detectTrick(listOf(buffer.first().swipe), state.skater.specialId)?.first
            if (single != null && combo.size < MAX_CHAIN) {
                combo = combo + single
                lastMs = state.elapsedMs
                lastBail = single.bailTimeMs
            }
            buffer = buffer.drop(1)
        }
        if (buffer === state.recentSwipes) return state
        return state.copy(recentSwipes = buffer, combo = combo, lastTrickMs = lastMs, lastTrickBailMs = lastBail)
    }

    /** Two-finger touch: snap to the coping or the nearest rail and ride it. */
    fun startGrind(state: SkateState): SkateState {
        if (state.finished) return state
        if (state.phase != SkatePhase.IN_POOL && state.phase != SkatePhase.IN_AIR) return state
        val speed = if (state.phase == SkatePhase.IN_AIR) {
            sqrt(state.airPlanar * state.airPlanar + state.airVertical * state.airVertical)
        } else {
            abs(state.speed)
        }
        if (speed < MIN_GRIND_SPEED) return state
        val rail = nearestRail(state.pool, state.position)
        if (rail != null) {
            val dir = rail.direction
            val aligned = if (dir.dot(state.direction) < 0f) Vec3(-dir.x, 0f, -dir.z) else dir
            return state.copy(
                phase = SkatePhase.GRINDING,
                position = Vec3(rail.point.x, state.pool.lip, rail.point.z),
                direction = aligned,
                boardFacing = aligned,
                speed = max(speed, MIN_GRIND_SPEED),
                grindKind = GrindKind.FIFTY_FIFTY,
                grindEdge = rail.index,
                grindMs = 0,
                balancePoint = 0f,
                balanceSpeed = 0f,
                controlSpeed = 0f,
                airVertical = 0f,
                airPlanar = 0f,
            )
        }
        if (!onCoping(state.pool, state.position)) return state
        val angle = atan2(state.position.z, state.position.x)
        val tangent = Vec3(-sin(angle), 0f, cos(angle))
        val flip = tangent.dot(state.direction) < 0f
        val dir = if (flip) Vec3(-tangent.x, 0f, -tangent.z) else tangent
        return state.copy(
            phase = SkatePhase.GRINDING,
            position = Vec3(cos(angle) * state.pool.radius, state.pool.lip, sin(angle) * state.pool.radius),
            direction = dir,
            boardFacing = dir,
            speed = max(speed, MIN_GRIND_SPEED),
            grindKind = GrindKind.FIFTY_FIFTY,
            grindEdge = -1,
            grindRight = !flip,
            grindMs = 0,
            balancePoint = 0f,
            balanceSpeed = 0f,
            controlSpeed = 0f,
            airVertical = 0f,
            airPlanar = 0f,
        )
    }

    fun endGrind(state: SkateState): SkateState {
        if (state.phase != SkatePhase.GRINDING) return state
        val speed = max(state.speed, MIN_GRIND_SPEED)
        val lateral = if (state.grindRight) 1f else -1f
        val right = Vec3(-state.direction.z, 0f, state.direction.x)
        val dir = (state.direction + right * (0.4f * lateral)).normalized()
        return state.copy(
            phase = SkatePhase.IN_POOL,
            speed = speed,
            direction = dir,
            boardFacing = dir,
            grindMsTotal = state.grindMsTotal + state.grindMs,
            grindCount = state.grindCount + 1,
            score = state.score + grindPoints(state.grindMs) * state.multiplier,
            grindMs = 0,
            balancePoint = 0f,
            balanceSpeed = 0f,
            controlSpeed = 0f,
        )
    }

    fun onCoping(pool: SkatePool, position: Vec3): Boolean =
        abs(radiusOf(position) - pool.radius) <= COPING_BAND && position.y >= pool.lip - 1.2f

    fun nearestRail(pool: SkatePool, position: Vec3): RailHit? {
        var best: RailHit? = null
        var bestDistance = RAIL_SNAP
        pool.rails.forEachIndexed { index, edge ->
            val abx = edge.bx - edge.ax
            val abz = edge.bz - edge.az
            val lengthSq = abx * abx + abz * abz
            if (lengthSq < 1e-5f) return@forEachIndexed
            val t = (((position.x - edge.ax) * abx + (position.z - edge.az) * abz) / lengthSq).coerceIn(0f, 1f)
            val px = edge.ax + abx * t
            val pz = edge.az + abz * t
            val dx = position.x - px
            val dz = position.z - pz
            val distance = sqrt(dx * dx + dz * dz)
            if (distance < bestDistance) {
                bestDistance = distance
                best = RailHit(index, Vec3(px, 0f, pz), Vec3(abx, 0f, abz).normalized())
            }
        }
        return best
    }

    data class RailHit(val index: Int, val point: Vec3, val direction: Vec3)

    fun canStartGrind(state: SkateState): Boolean {
        if (state.finished) return false
        if (state.phase != SkatePhase.IN_POOL && state.phase != SkatePhase.IN_AIR) return false
        val speed = if (state.phase == SkatePhase.IN_AIR) {
            sqrt(state.airPlanar * state.airPlanar + state.airVertical * state.airVertical)
        } else {
            abs(state.speed)
        }
        if (speed < MIN_GRIND_SPEED) return false
        return nearestRail(state.pool, state.position) != null || onCoping(state.pool, state.position)
    }

    fun bagPositions(pool: SkatePool, seed: Int): List<Vec3> {
        val rng = SkateRng(seed)
        return pool.bags.map { bag ->
            val jitter = (rng.nextFloat() - 0.5f)
            val angle = bag.angle + jitter * 0.5f
            val radius = (bag.radius + jitter * 0.4f).coerceIn(0.6f, pool.radius - 0.4f)
            Vec3(cos(angle) * radius, 0f, sin(angle) * radius)
        }
    }

    fun trickInProgress(state: SkateState): Boolean =
        state.combo.isNotEmpty() && state.elapsedMs - state.lastTrickMs < state.lastTrickBailMs

    /** Advance the simulation; the accumulator keeps arbitrary frame deltas on the fixed grid. */
    fun step(state: SkateState, dtMs: Long): SkateState {
        if (state.finished) return state
        var acc = state.accumulatorMs + dtMs.coerceIn(0L, 100L)
        var current = state
        var guard = 0
        while (acc >= FIXED_STEP_MS && guard < 8) {
            current = fixedStep(current)
            acc -= FIXED_STEP_MS
            guard++
        }
        return current.copy(accumulatorMs = acc)
    }

    /** Advance whole fixed frames; tests use this for exact replay. */
    fun advance(state: SkateState, frames: Int): SkateState {
        var current = state
        repeat(frames) { current = fixedStep(current) }
        return current
    }

    private fun fixedStep(prev: SkateState): SkateState {
        if (prev.finished) return prev
        val def = SkateContent.event(prev.event)
        var state = prev.copy(elapsedMs = prev.elapsedMs + FIXED_STEP_MS)
        state = resolveExpiredSwipes(state)
        if (def.timerSeconds > 0f) {
            state = state.copy(eventTimerMs = (state.eventTimerMs - FIXED_STEP_MS).coerceAtLeast(0f))
        }
        state = when (state.phase) {
            SkatePhase.IN_POOL -> stepPool(state)
            SkatePhase.IN_AIR -> stepAir(state)
            SkatePhase.GRINDING -> stepGrind(state)
            SkatePhase.CRASHING -> stepCrash(state)
            SkatePhase.FINISHED -> state
        }
        state = updateMultiplier(state, def)
        return evaluate(state, def)
    }

    private fun stepPool(state: SkateState): SkateState {
        val pool = state.pool
        val normal = surfaceNormal(pool, state.position.x, state.position.z)
        var dir = state.direction - normal * state.direction.dot(normal)
        if (dir.length() < 1e-4f) dir = state.direction
        dir = dir.normalized()
        val stats = stats(state)
        val steer = state.tiltedX * TURN_RATE * FIXED_DT / stats.control
        dir = rotateY(dir, -steer)
        dir = (dir - normal * dir.dot(normal)).normalized()
        val slope = slopeAcceleration(normal)
        var speed = state.speed + (ACCELERATION * stats.speed + slope.dot(dir)) * FIXED_DT
        speed *= DRAG_FACTOR
        var next = state.position + dir * (speed * FIXED_DT)
        for (ramp in pool.ramps) {
            val inside = abs(next.x - ramp.x) <= ramp.halfWidth && abs(next.z - ramp.z) <= ramp.halfDepth
            if (inside && speed > MIN_GRIND_SPEED) {
                val launchDir = Vec3(dir.x, ramp.launch, dir.z).normalized()
                return state.copy(
                    phase = SkatePhase.IN_AIR,
                    position = Vec3(next.x, surfaceHeight(pool, next.x, next.z), next.z),
                    direction = launchDir,
                    boardFacing = Vec3(dir.x, 0f, dir.z).normalized(),
                    speed = speed,
                    airVertical = speed * ramp.launch,
                    airPlanar = speed * sqrt(dir.x * dir.x + dir.z * dir.z),
                    combo = emptyList(),
                    recentSwipes = emptyList(),
                    spinRad = 0f,
                )
            }
        }
        val wasInside = radiusOf(state.position) < pool.radius
        val nowOutside = radiusOf(next) >= pool.radius
        var out = state.copy(
            position = Vec3(next.x, surfaceHeight(pool, next.x, next.z), next.z),
            direction = dir,
            boardFacing = blend(state.boardFacing, dir, 0.1f),
            speed = speed,
        )
        if (wasInside && nowOutside && dir.y > 0.02f && speed > 0f) {
            val (vertical, planar) = rampLaunch(speed, dir)
            out = out.copy(
                phase = SkatePhase.IN_AIR,
                airVertical = vertical,
                airPlanar = planar,
                direction = dir,
                boardFacing = blend(out.boardFacing, dir, 0.1f),
                combo = emptyList(),
                recentSwipes = emptyList(),
                spinRad = 0f,
            )
        }
        out = collectBags(out)
        return out
    }

    private fun stepAir(state: SkateState): SkateState {
        val stats = stats(state)
        val spinDelta = -state.tiltedX * SPIN_RATE * stats.spin * FIXED_DT
        val facing = rotateY(state.boardFacing, spinDelta)
        val vertical = state.airVertical - GRAVITY * FIXED_DT
        val position = Vec3(
            state.position.x + state.direction.x * state.airPlanar * FIXED_DT,
            state.position.y + vertical * FIXED_DT,
            state.position.z + state.direction.z * state.airPlanar * FIXED_DT,
        )
        var out = state.copy(spinRad = state.spinRad + spinDelta, boardFacing = facing, airVertical = vertical, position = position)
        if (vertical < 0f && position.y <= surfaceHeight(state.pool, position.x, position.z)) {
            out = land(out)
        }
        return out
    }

    private fun stepGrind(state: SkateState): SkateState {
        if (abs(state.balancePoint) > BALANCE_MAX_VALUE) return crash(state)
        val stats = stats(state)
        var control = state.controlSpeed
        if (abs(state.tiltedX) > BALANCE_DEAD_ZONE) {
            control += state.tiltedX * ACCELERATOR_MODIFIER
        }
        val combined = state.balanceSpeed + control
        control += if (combined > 0f) CONTROL_SPEED_MODIFIER * FIXED_DT else -CONTROL_SPEED_MODIFIER * FIXED_DT
        val balanceSpeed = state.balanceSpeed + BALANCE_POINT_SPEED_MODIFIER * FIXED_DT
        val balancePoint = state.balancePoint + combined * stats.balance * FIXED_DT
        val grindSpeed = max(state.speed, MIN_GRIND_SPEED)
        var next = state.copy(
            balancePoint = balancePoint,
            balanceSpeed = balanceSpeed,
            controlSpeed = control,
            grindMs = state.grindMs + (FIXED_STEP_MS.toInt()),
        )
        next = if (state.grindEdge >= 0) {
            val rail = state.pool.rails[state.grindEdge]
            val dir = state.direction
            val position = Vec3(
                state.position.x + dir.x * grindSpeed * FIXED_DT,
                state.pool.lip,
                state.position.z + dir.z * grindSpeed * FIXED_DT,
            )
            val abx = rail.bx - rail.ax
            val abz = rail.bz - rail.az
            val t = ((position.x - rail.ax) * abx + (position.z - rail.az) * abz) / (abx * abx + abz * abz)
            if (t < 0f || t > 1f) return endGrind(next)
            next.copy(position = position, direction = dir, boardFacing = blend(next.boardFacing, dir, 0.2f))
        } else {
            val angle = atan2(state.position.z, state.position.x)
            val omega = if (state.grindRight) grindSpeed / state.pool.radius else -grindSpeed / state.pool.radius
            val nextAngle = angle + omega * FIXED_DT
            val position = Vec3(cos(nextAngle) * state.pool.radius, state.pool.lip, sin(nextAngle) * state.pool.radius)
            val tangent = Vec3(-sin(nextAngle), 0f, cos(nextAngle))
            val dir = if (state.grindRight) tangent else Vec3(-tangent.x, 0f, -tangent.z)
            next.copy(position = position, direction = dir, boardFacing = blend(next.boardFacing, dir, 0.2f))
        }
        return next
    }

    private fun stepCrash(state: SkateState): SkateState {
        val crashMs = state.crashMs + FIXED_STEP_MS
        if (crashMs < CRASH_RECOVERY_MS) return state.copy(crashMs = crashMs)
        val start = Vec3(0f, surfaceHeight(state.pool, 0f, 0f), 0f)
        return state.copy(
            phase = SkatePhase.IN_POOL,
            position = start,
            direction = Vec3(0f, 0f, -1f),
            boardFacing = Vec3(0f, 0f, -1f),
            speed = 0f,
            airVertical = 0f,
            airPlanar = 0f,
            spinRad = 0f,
            combo = emptyList(),
            recentSwipes = emptyList(),
            balancePoint = 0f,
            balanceSpeed = 0f,
            controlSpeed = 0f,
            crashMs = crashMs,
        )
    }

    private fun land(state: SkateState): SkateState {
        val pool = state.pool
        val stats = stats(state)
        if (trickInProgress(state)) return crash(state)
        val travel = Vec3(
            state.airPlanar * state.direction.x,
            state.airVertical,
            state.airPlanar * state.direction.z,
        ).normalized()
        val speed = sqrt(state.airPlanar * state.airPlanar + state.airVertical * state.airVertical)
        val rotation = formatRotation(state.spinRad)
        val angle = angleBetween(travel, state.boardFacing)
        val perfect = angle < LANDING_ANGLE * stats.landing && rotation > 0
        var multiplier = state.multiplier
        if (perfect) multiplier = min(multiplier + 1, SkateContent.event(state.event).maxMultiplier)
        val gained = (trickScore(state.combo) + rotation) * multiplier
        val unique = state.collectedTrickIds + state.combo.map { it.id }
        var chain = state.chainIndex
        if (state.event == SkateEventId.SKATE && state.combo.isNotEmpty()) {
            val required = SkateContent.SKATE_CHAIN.getOrNull(chain)
            val matched = state.combo.any { it.id == required || (required == "special" && it.special) }
            if (matched) chain++
        }
        val projected = (travel - surfaceNormal(pool, state.position.x, state.position.z) *
            travel.dot(surfaceNormal(pool, state.position.x, state.position.z))).normalized()
        val feedback = when {
            perfect -> "perfect landing"
            angle < LANDING_ANGLE * stats.landing * 2f -> "clean"
            else -> "sketchy"
        }
        return state.copy(
            phase = SkatePhase.IN_POOL,
            position = Vec3(state.position.x, surfaceHeight(pool, state.position.x, state.position.z), state.position.z),
            direction = if (projected.length() < 1e-4f) state.direction else projected,
            boardFacing = if (projected.length() < 1e-4f) state.boardFacing else projected,
            speed = speed,
            airVertical = 0f,
            airPlanar = 0f,
            spinRad = 0f,
            spinTotalDeg = state.spinTotalDeg + rotation,
            combo = emptyList(),
            recentSwipes = emptyList(),
            score = state.score + gained,
            multiplier = multiplier,
            perfectLandings = state.perfectLandings + if (perfect) 1 else 0,
            landingFeedback = feedback,
            collectedTrickIds = unique,
            chainIndex = chain,
            balancePoint = 0f,
            balanceSpeed = 0f,
            controlSpeed = 0f,
        )
    }

    private fun crash(state: SkateState): SkateState = state.copy(
        phase = SkatePhase.CRASHING,
        bailed = true,
        crashes = state.crashes + 1,
        crashMs = 0f,
        multiplier = 1,
        multiplierTimerMs = 0f,
        combo = emptyList(),
        recentSwipes = emptyList(),
        spinRad = 0f,
        grindMs = 0,
        balancePoint = 0f,
        balanceSpeed = 0f,
        controlSpeed = 0f,
        airVertical = 0f,
        airPlanar = 0f,
        landingFeedback = "bail",
        lastTrickMs = -100000f,
    )

    private fun updateMultiplier(state: SkateState, def: SkateEventDef): SkateState {
        if (state.phase == SkatePhase.CRASHING) {
            return state.copy(multiplier = 1, multiplierTimerMs = 0f)
        }
        var multiplier = state.multiplier
        var timer = state.multiplierTimerMs
        if (state.phase == SkatePhase.GRINDING) {
            if (state.grindMs / GRIND_MULTIPLIER_STEP_MS >= multiplier && multiplier < def.maxMultiplier) {
                multiplier++
                timer = 0f
            }
        }
        timer += FIXED_STEP_MS
        if (timer / MULTIPLIER_DECAY_UNIT_MS > def.multDecrement) {
            timer = 0f
            multiplier = max(1, multiplier - 1)
        }
        return state.copy(multiplier = multiplier.coerceAtMost(def.maxMultiplier), multiplierTimerMs = timer)
    }

    private fun collectBags(state: SkateState): SkateState {
        if (state.event != SkateEventId.POOL_CLEANER) return state
        val positions = bagPositions(state.pool, state.seed)
        var collected = state.collected
        positions.forEachIndexed { index, bag ->
            if (index in collected) return@forEachIndexed
            val dx = state.position.x - bag.x
            val dz = state.position.z - bag.z
            if (dx * dx + dz * dz < 1.1f * 1.1f) collected = collected + index
        }
        return if (collected.size != state.collected.size) state.copy(collected = collected) else state
    }

    private fun evaluate(state: SkateState, def: SkateEventDef): SkateState {
        if (state.finished) return state
        val success = when (def.objective) {
            SkateObjective.FREE_RIDE -> false
            SkateObjective.SCORE -> state.score >= def.target
            SkateObjective.NO_BAILS -> state.score >= def.target
            SkateObjective.COLLECT_BAGS -> state.collected.size >= state.pool.bags.size
            SkateObjective.SPIN -> state.spinTotalDeg >= def.target
            SkateObjective.TRICK_CHAIN -> state.chainIndex >= SkateContent.SKATE_CHAIN.size
            SkateObjective.UNIQUE_TRICKS -> state.collectedTrickIds.distinct().size >= def.target
        }
        if (success) return finish(state, SkateResult.SUCCESS)
        if (def.objective == SkateObjective.NO_BAILS && state.bailed) return finish(state, SkateResult.FAILED)
        if (def.timerSeconds > 0f && state.eventTimerMs <= 0f) {
            return finish(state, if (def.objective == SkateObjective.NO_BAILS) SkateResult.SUCCESS else SkateResult.FAILED)
        }
        return state
    }

    fun finish(state: SkateState, result: SkateResult): SkateState =
        state.copy(phase = SkatePhase.FINISHED, result = result)

    /** End a session early (free-ride quit); keeps the score for the result screen. */
    fun stop(state: SkateState): SkateState =
        if (state.finished) state else state.copy(phase = SkatePhase.FINISHED, result = SkateResult.SUCCESS)

    fun starsFor(event: SkateEventId, score: Int): Int {
        val def = SkateContent.event(event)
        return when {
            def.star3 > 0 && score >= def.star3 -> 3
            def.star2 > 0 && score >= def.star2 -> 2
            def.star1 > 0 && score >= def.star1 -> 1
            else -> 0
        }
    }

    fun encode(state: SkateState): String = listOf(
        "sk1",
        state.event.key,
        state.pool.id,
        state.skater.id,
        state.board.id,
        state.wheel.id,
        state.seed.toString(),
        state.phase.ordinal.toString(),
        state.position.x.toString(),
        state.position.y.toString(),
        state.position.z.toString(),
        state.direction.x.toString(),
        state.direction.y.toString(),
        state.direction.z.toString(),
        state.boardFacing.x.toString(),
        state.boardFacing.y.toString(),
        state.boardFacing.z.toString(),
        state.speed.toString(),
        state.airVertical.toString(),
        state.airPlanar.toString(),
        state.spinRad.toString(),
        state.spinTotalDeg.toString(),
        state.balancePoint.toString(),
        state.balanceSpeed.toString(),
        state.controlSpeed.toString(),
        state.grindKind.ordinal.toString(),
        state.grindMs.toString(),
        state.grindMsTotal.toString(),
        state.grindCount.toString(),
        state.grindSwitches.toString(),
        state.grindEdge.toString(),
        if (state.grindRight) "1" else "0",
        state.combo.joinToString(",") { it.id },
        state.score.toString(),
        state.multiplier.toString(),
        state.multiplierTimerMs.toString(),
        state.elapsedMs.toString(),
        state.eventTimerMs.toString(),
        if (state.bailed) "1" else "0",
        state.crashes.toString(),
        state.crashMs.toString(),
        state.perfectLandings.toString(),
        state.collected.joinToString(","),
        state.collectedTrickIds.joinToString(","),
        state.chainIndex.toString(),
        state.accumulatorMs.toString(),
        state.tiltedX.toString(),
        state.tiltedY.toString(),
        state.result.ordinal.toString(),
        state.lastTrickMs.toString(),
        state.lastTrickBailMs.toString(),
    ).joinToString(";")

    fun decode(blob: String): SkateState? {
        return try {
            val p = blob.split(";")
            if (p.size < 51 || p[0] != "sk1") return null
            val event = SkateEventId.fromKey(p[1]) ?: return null
            val combo = if (p[32].isEmpty()) emptyList() else p[32].split(",").mapNotNull { SkateTricks.byId(it) }
            val collected = if (p[42].isEmpty()) emptyList() else p[42].split(",").mapNotNull { it.toIntOrNull() }
            val unique = if (p[43].isEmpty()) emptyList() else p[43].split(",")
            SkateState(
                event = event,
                pool = SkateContent.pool(p[2]),
                skater = SkateContent.skater(p[3]),
                board = SkateContent.board(p[4]),
                wheel = SkateContent.wheel(p[5]),
                seed = p[6].toInt(),
                phase = SkatePhase.entries.getOrNull(p[7].toInt()) ?: return null,
                position = Vec3(p[8].toFloat(), p[9].toFloat(), p[10].toFloat()),
                direction = Vec3(p[11].toFloat(), p[12].toFloat(), p[13].toFloat()),
                boardFacing = Vec3(p[14].toFloat(), p[15].toFloat(), p[16].toFloat()),
                speed = p[17].toFloat(),
                airVertical = p[18].toFloat(),
                airPlanar = p[19].toFloat(),
                spinRad = p[20].toFloat(),
                spinTotalDeg = p[21].toInt(),
                balancePoint = p[22].toFloat(),
                balanceSpeed = p[23].toFloat(),
                controlSpeed = p[24].toFloat(),
                grindKind = GrindKind.entries.getOrNull(p[25].toInt()) ?: GrindKind.FIFTY_FIFTY,
                grindMs = p[26].toInt(),
                grindMsTotal = p[27].toInt(),
                grindCount = p[28].toInt(),
                grindSwitches = p[29].toInt(),
                grindEdge = p[30].toInt(),
                grindRight = p[31] == "1",
                combo = combo,
                score = p[33].toInt(),
                multiplier = p[34].toInt(),
                multiplierTimerMs = p[35].toFloat(),
                elapsedMs = p[36].toFloat(),
                eventTimerMs = p[37].toFloat(),
                bailed = p[38] == "1",
                crashes = p[39].toInt(),
                crashMs = p[40].toFloat(),
                perfectLandings = p[41].toInt(),
                collected = collected,
                collectedTrickIds = unique,
                chainIndex = p[44].toInt(),
                accumulatorMs = p[45].toFloat(),
                tiltedX = p[46].toFloat(),
                tiltedY = p[47].toFloat(),
                result = SkateResult.entries.getOrNull(p[48].toInt()) ?: SkateResult.IN_PROGRESS,
                lastTrickMs = p[49].toFloat(),
                lastTrickBailMs = p[50].toInt(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun rotateY(v: Vec3, angle: Float): Vec3 {
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    private fun blend(from: Vec3, to: Vec3, t: Float): Vec3 =
        (from * (1f - t) + to * t).normalized()
}

/** Career record: stars, best scores, loadout and unlocks. */
data class SkateCareer(
    val stars: Map<SkateEventId, Int> = emptyMap(),
    val bestScores: Map<SkateEventId, Int> = emptyMap(),
    val poolId: String = SkateContent.POOLS[0].id,
    val skaterId: String = SkateContent.SKATERS[0].id,
    val boardId: String = SkateContent.BOARDS[0].id,
    val wheelId: String = SkateContent.WHEELS[0].id,
) {
    val totalStars: Int get() = stars.values.sum()

    fun starsFor(event: SkateEventId): Int = stars[event] ?: 0

    fun bestFor(event: SkateEventId): Int = bestScores[event] ?: 0

    fun isEventUnlocked(event: SkateEventId): Boolean {
        val def = SkateContent.event(event)
        val after = def.unlockAfter ?: return true
        if (after == SkateEventId.FREE_RIDE || after == SkateEventId.TRAINING) return true
        return starsFor(after) > 0
    }

    fun unlockedEvents(): List<SkateEventDef> = SkateContent.EVENTS.filter { isEventUnlocked(it.id) }

    fun isPoolUnlocked(pool: SkatePool): Boolean = totalStars >= pool.unlockStars

    fun isBoardUnlocked(board: SkateGear): Boolean = totalStars >= board.unlockStars

    fun isWheelUnlocked(wheel: SkateGear): Boolean = totalStars >= wheel.unlockStars

    fun unlockedBoards(): List<SkateGear> = SkateContent.BOARDS.filter { isBoardUnlocked(it) }

    fun unlockedWheels(): List<SkateGear> = SkateContent.WHEELS.filter { isWheelUnlocked(it) }

    fun unlockedPools(): List<SkatePool> = SkateContent.POOLS.filter { isPoolUnlocked(it) }

    fun earnedAchievements(): List<SkateAchievement> =
        SkateContent.ACHIEVEMENTS.filter { starsFor(it.event) >= it.stars }

    fun unlockedVideos(): List<SkateVideo> =
        SkateContent.VIDEOS.filter { totalStars >= it.unlockStars }

    fun applyResult(event: SkateEventId, score: Int): SkateCareer {
        val newStars = max(starsFor(event), SkateEngine.starsFor(event, score))
        val newBest = max(bestFor(event), score)
        return copy(stars = stars + (event to newStars), bestScores = bestScores + (event to newBest))
    }

    fun withLoadout(poolId: String, skaterId: String, boardId: String, wheelId: String): SkateCareer =
        copy(poolId = poolId, skaterId = skaterId, boardId = boardId, wheelId = wheelId)

    fun encode(): String {
        val starPart = stars.entries.joinToString(",") { "${it.key.key}=${it.value}" }
        val bestPart = bestScores.entries.joinToString(",") { "${it.key.key}=${it.value}" }
        return listOf("skc1", starPart, bestPart, poolId, skaterId, boardId, wheelId).joinToString("|")
    }

    companion object {
        fun decode(blob: String): SkateCareer? = try {
            val parts = blob.split("|")
            if (parts.size < 7 || parts[0] != "skc1") {
                null
            } else {
                fun parsePairs(value: String, map: (String, Int) -> Pair<SkateEventId, Int>?): Map<SkateEventId, Int> =
                    if (value.isEmpty()) {
                        emptyMap()
                    } else {
                        value.split(",").mapNotNull { entry ->
                            val bits = entry.split("=")
                            if (bits.size != 2) return@mapNotNull null
                            val key = SkateEventId.fromKey(bits[0]) ?: return@mapNotNull null
                            val number = bits[1].toIntOrNull() ?: return@mapNotNull null
                            map(key.key, number)
                        }.toMap()
                    }
                SkateCareer(
                    stars = parsePairs(parts[1]) { key, value -> SkateEventId.fromKey(key)?.let { it to value } },
                    bestScores = parsePairs(parts[2]) { key, value -> SkateEventId.fromKey(key)?.let { it to value } },
                    poolId = parts[3],
                    skaterId = parts[4],
                    boardId = parts[5],
                    wheelId = parts[6],
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
