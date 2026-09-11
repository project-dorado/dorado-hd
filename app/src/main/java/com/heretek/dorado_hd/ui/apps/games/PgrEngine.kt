package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * PGR: Ferrari Edition — clean-room racing simulation for Dorado-HD, derived
 * from the behavioral spec in docs/apps/pgr-ferrari-edition.md §3.
 *
 * Everything here is original code and original content. The model follows the
 * reference's fixed 30 Hz deterministic tic, torque-curve gearbox, grip/drag/
 * downforce handling model, kudos chain scoring, and career progression, with
 * our own cars, tracks, cities and events. No Microsoft code, data or assets
 * are used.
 *
 * Pure Kotlin: no Android dependencies, fully unit-testable on the JVM.
 */
object PgrEngine {

    // ------------------------------------------------------------------
    // Simulation clock (spec §3, BaseApp constants)
    // ------------------------------------------------------------------

    /** Fixed tics per simulated second. */
    const val TICS_PER_SECOND = 30

    /** Fixed time step in seconds (1/30). */
    const val FIXED_TIME_STEP = 1f / TICS_PER_SECOND

    /** Fixed tic length in whole milliseconds (used by the app accumulator). */
    const val TICK_MS = 1000L / TICS_PER_SECOND

    /** Race rules (GameContainer constants). */
    const val MAX_CHECKPOINTS = 3
    const val MAX_LAPS = 6
    const val MAX_PLAYERS = 4

    const val DEFAULT_SEED = 1374496523

    /** The player is always grid slot 0; the rest of the field is AI. */
    const val PLAYER_INDEX = 0

    fun ticksToMs(ticks: Long): Long = ticks * 1000L / TICS_PER_SECOND

    fun msToTicks(ms: Long): Long = ms * TICS_PER_SECOND / 1000L

    // ------------------------------------------------------------------
    // Small math helpers
    // ------------------------------------------------------------------

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))

    fun wrapPi(rad: Float): Float {
        var r = rad
        while (r > PI.toFloat()) r -= (2f * PI.toFloat())
        while (r < -PI.toFloat()) r += (2f * PI.toFloat())
        return r
    }

    fun wrapDistance(distance: Float, length: Float): Float {
        var d = distance % length
        if (d < 0f) d += length
        return d
    }

    /** Deterministic LCG step used for AI jitter and prop variation. */
    fun nextSeed(seed: Int): Int = seed * 1103515245 + 12345

    /** Signed deterministic noise in [-1, 1] for a seed/salt pair. */
    fun noise(seed: Int, salt: Int): Float {
        val mixed = seed xor (salt * 0x9E3779B9.toInt())
        return ((mixed ushr 8) and 0xFFFF) / 32767.5f - 1f
    }

    // ------------------------------------------------------------------
    // Handling model (CPhysCar equivalents)
    // ------------------------------------------------------------------

    /** Frontal grip is a flat 2 in the reference model. */
    const val FORWARD_GRIP = 2f

    /** Side-grip curve: 2 * clamp(lerp(1.05, 1.25, handling * 0.1), 1, 1.2). */
    fun sideGrip(handling: Int): Float =
        2f * clamp(lerp(1.05f, 1.25f, handling * 0.1f), 1f, 1.2f)

    /** Downforce: 0.5 * sideGrip * lerp(v^2/3086.42, 0.2*v^2/3086.42, alignment). */
    const val DOWNFORCE_DIVISOR = 3086.42f

    fun downforce(speed: Float, sideGrip: Float, alignment: Float): Float {
        val v2 = speed * speed / DOWNFORCE_DIVISOR
        return 0.5f * sideGrip * lerp(v2, 0.2f * v2, clamp(alignment, 0f, 1f))
    }

    /** Drag area interpolation by velocity alignment (frontal <-> side). */
    fun dragArea(frontalArea: Float, sideArea: Float, alignment: Float): Float =
        lerp(frontalArea, sideArea, clamp(alignment, 0f, 1f))

    /**
     * Torque curve: full [PgrCarDef.maxTorque] at/below [PgrCarDef.lowRpm],
     * falling linearly to [PgrCarDef.minTorque] at [PgrCarDef.highRpm].
     */
    fun torqueAt(def: PgrCarDef, rpm: Float, throttle: Float): Float {
        val t = ((max(rpm, def.lowRpm) - def.lowRpm) / (def.highRpm - def.lowRpm)).coerceIn(0f, 1f)
        return clamp(throttle, 0f, 1f) * lerp(def.maxTorque, def.minTorque, t)
    }

    /** Engine RPM implied by wheel speed in the selected gear. */
    fun rpmFromWheelSpeed(def: PgrCarDef, speed: Float, gear: Int): Float {
        val ratio = max(abs(gearRatio(def, gear)), 0.3f)
        return abs(speed) / def.wheelRadius * 60f * ratio * def.finalDrive / (2f * PI.toFloat())
    }

    /** Signed gear ratio: gear 0 = reverse, 1..n = forward gears. */
    fun gearRatio(def: PgrCarDef, gear: Int): Float =
        if (gear == 0) -def.reverseRatio else def.gears[(gear - 1).coerceIn(0, def.gears.size - 1)]

    /** Speed at the redline in the tallest gear (the car's gearing limit). */
    fun redlineSpeed(def: PgrCarDef): Float {
        val top = abs(def.gears.last()).coerceAtLeast(0.3f)
        val rpm = def.highRpm * 1.1f
        return rpm / 60f * (2f * PI.toFloat()) * def.wheelRadius / (top * def.finalDrive)
    }

    /** Touch/tilt steering curve: dead zone, smoothstep softening, clamped. */
    fun tiltToSteer(rollDeg: Float, sensitivity: Float = 1f): Float {
        val dead = 2f
        val span = 26f
        if (abs(rollDeg) <= dead) return 0f
        val t = ((abs(rollDeg) - dead) / (span - dead)).coerceIn(0f, 1f)
        val softened = t * t * (3f - 2f * t)
        return sign(rollDeg) * softened * clamp(sensitivity, 0f, 1f)
    }

    // ------------------------------------------------------------------
    // Enums
    // ------------------------------------------------------------------

    enum class PgrPhase { COUNTDOWN, RACING, FINISHED }

    enum class PgrDifficulty(val label: String, val creditReward: Int, val aiSkill: Float) {
        EASY("easy", 100, 0.88f),
        MEDIUM("medium", 200, 0.94f),
        HARD("hard", 300, 1.0f),
    }

    enum class PgrMedal(val label: String, val multiplier: Int, val rank: Int) {
        NONE("none", 0, 0),
        BRONZE("bronze", 1, 1),
        SILVER("silver", 2, 2),
        GOLD("gold", 3, 3),
    }

    /** Kudos maneuver catalogue, exact values from the spec §3. */
    enum class PgrManeuver(val label: String, val value: Int, val continueSeconds: Float) {
        DRIFT("drift", 400, 0.3f),
        BURNOUT("burnout", 100, 2f),
        SPIN360("360", 400, 0.4f),
        RACELINE("raceline", 200, 0.4f),
        DRAFT("draft", 100, 4f),
        OVERTAKE("overtake", 200, 0.5f),
        SLINGSHOT("slingshot", 400, 0.5f),
        CLEAN_SECTION("clean section", 200, 0f),
        CLEAN_LAP("clean lap", 400, 0f),
        CLEAN_RACE("clean race", 800, 0f),
        CLEAN_WIN("clean win", 5000, 0f),
        AIR("air", 400, 0.1f),
        SPEED("speed", 400, 2f),
        CONE_GATE("cone gate", 25, 0f),
    }

    enum class PgrEventType(val label: String) {
        BREAKTHROUGH("breakthrough"),
        CONE_SPRINT("cone sprint"),
        ELIMINATOR("eliminator"),
        KUDOS_CHALLENGE("kudos challenge"),
        ONE_ON_ONE("one-on-one"),
        OVERTAKE("overtake"),
        SPEED_CHALLENGE("speed challenge"),
        TIME_VS_KUDOS("time vs kudos"),
        STREET_RACE("street race"),
    }

    enum class PgrCity(val label: String, val skyArgb: Int, val fogDensity: Float) {
        LONDON("london", 0x3F4650, 0.0072f),
        TOKYO("tokyo", 0x1E2232, 0.0092f),
        NEW_YORK("new york", 0x2C333D, 0.0082f),
    }

    // ------------------------------------------------------------------
    // Cars (original roster, stats-driven)
    // ------------------------------------------------------------------

    data class PgrCarDef(
        val id: String,
        val name: String,
        val classLevel: Int,
        val creditCost: Int,
        val kudosCost: Int,
        val medalsRequired: Int,
        val accel: Int,
        val speedStat: Int,
        val handling: Int,
        val weight: Int,
        val brake: Int,
        val massKg: Float,
        val minTorque: Float,
        val maxTorque: Float,
        val lowRpm: Float,
        val highRpm: Float,
        val gears: List<Float>,
        val reverseRatio: Float,
        val finalDrive: Float,
        val radiusFront: Float,
        val radiusRear: Float,
        val wheelbase: Float,
        val trackWidth: Float,
        val areaFront: Float,
        val areaSide: Float,
        val brakeTorque: Float,
        val maxSteerDeg: Float,
        val colorsArgb: List<Int>,
    ) {
        val maxSteerRad: Float get() = maxSteerDeg * (PI.toFloat() / 180f)
        val suspensionRate: Float get() = 2.5f * massKg
        val wheelRadius: Float get() = (radiusFront + radiusRear) * 0.5f
    }

    object PgrCars {
        val all: List<PgrCarDef> = listOf(
            PgrCarDef(
                id = "vela-gt", name = "vela gt", classLevel = 1, creditCost = 0, kudosCost = 0,
                medalsRequired = 0, accel = 4, speedStat = 4, handling = 5, weight = 5, brake = 4,
                massKg = 1210f, minTorque = 231f, maxTorque = 420f, lowRpm = 1100f, highRpm = 8200f,
                gears = listOf(3.45f, 2.42f, 1.86f, 1.52f, 1.28f, 1.08f), reverseRatio = 3.2f,
                finalDrive = 3.55f, radiusFront = 0.32f, radiusRear = 0.33f, wheelbase = 2.55f,
                trackWidth = 1.86f, areaFront = 0.78f, areaSide = 2.6f, brakeTorque = 7200f,
                maxSteerDeg = 34f, colorsArgb = listOf(0xC9342B, 0x2F4E9E, 0xE8E8E8, 0x1C1C1C),
            ),
            PgrCarDef(
                id = "corso-s", name = "corso s", classLevel = 1, creditCost = 12000, kudosCost = 0,
                medalsRequired = 0, accel = 5, speedStat = 5, handling = 6, weight = 6, brake = 5,
                massKg = 1260f, minTorque = 259f, maxTorque = 470f, lowRpm = 1150f, highRpm = 8400f,
                gears = listOf(3.6f, 2.5f, 1.92f, 1.5f, 1.2f), reverseRatio = 3.3f,
                finalDrive = 3.6f, radiusFront = 0.32f, radiusRear = 0.33f, wheelbase = 2.6f,
                trackWidth = 1.9f, areaFront = 0.8f, areaSide = 2.7f, brakeTorque = 7600f,
                maxSteerDeg = 35f, colorsArgb = listOf(0x2F7A4E, 0xE8B93A, 0x2A2A2E, 0xD8D8D8),
            ),
            PgrCarDef(
                id = "tigre-gts", name = "tigre gts", classLevel = 2, creditCost = 30000, kudosCost = 800,
                medalsRequired = 3, accel = 6, speedStat = 6, handling = 6, weight = 6, brake = 6,
                massKg = 1320f, minTorque = 297f, maxTorque = 540f, lowRpm = 1200f, highRpm = 8600f,
                gears = listOf(3.5f, 2.45f, 1.88f, 1.54f, 1.3f, 1.1f), reverseRatio = 3.35f,
                finalDrive = 3.65f, radiusFront = 0.33f, radiusRear = 0.34f, wheelbase = 2.65f,
                trackWidth = 1.94f, areaFront = 0.84f, areaSide = 2.85f, brakeTorque = 8200f,
                maxSteerDeg = 35f, colorsArgb = listOf(0x1F4FA8, 0xC9C9CE, 0x8B1E2D, 0x20242A),
            ),
            PgrCarDef(
                id = "vulcano-rs", name = "vulcano rs", classLevel = 2, creditCost = 45000, kudosCost = 1500,
                medalsRequired = 6, accel = 7, speedStat = 6, handling = 7, weight = 7, brake = 7,
                massKg = 1360f, minTorque = 330f, maxTorque = 600f, lowRpm = 1250f, highRpm = 8800f,
                gears = listOf(3.55f, 2.5f, 1.9f, 1.55f, 1.3f, 1.08f), reverseRatio = 3.4f,
                finalDrive = 3.7f, radiusFront = 0.33f, radiusRear = 0.34f, wheelbase = 2.68f,
                trackWidth = 1.96f, areaFront = 0.82f, areaSide = 2.9f, brakeTorque = 8600f,
                maxSteerDeg = 36f, colorsArgb = listOf(0xE07B2A, 0x2B2B30, 0x5A6B7A, 0xEFEFEF),
            ),
            PgrCarDef(
                id = "aquila-sc", name = "aquila sc", classLevel = 3, creditCost = 80000, kudosCost = 3000,
                medalsRequired = 9, accel = 8, speedStat = 8, handling = 8, weight = 7, brake = 8,
                massKg = 1290f, minTorque = 363f, maxTorque = 660f, lowRpm = 1300f, highRpm = 9000f,
                gears = listOf(3.6f, 2.55f, 1.95f, 1.58f, 1.32f, 1.1f), reverseRatio = 3.45f,
                finalDrive = 3.75f, radiusFront = 0.33f, radiusRear = 0.35f, wheelbase = 2.7f,
                trackWidth = 1.98f, areaFront = 0.88f, areaSide = 3.0f, brakeTorque = 9200f,
                maxSteerDeg = 37f, colorsArgb = listOf(0xE8E8E8, 0x16233A, 0x9E2B2B, 0x3A3A3E),
            ),
            PgrCarDef(
                id = "falco-stradale", name = "falco stradale", classLevel = 3, creditCost = 120000, kudosCost = 6000,
                medalsRequired = 12, accel = 9, speedStat = 9, handling = 9, weight = 8, brake = 9,
                massKg = 1240f, minTorque = 396f, maxTorque = 720f, lowRpm = 1350f, highRpm = 9200f,
                gears = listOf(3.65f, 2.6f, 1.98f, 1.6f, 1.35f, 1.12f), reverseRatio = 3.5f,
                finalDrive = 3.8f, radiusFront = 0.34f, radiusRear = 0.35f, wheelbase = 2.72f,
                trackWidth = 2.0f, areaFront = 0.9f, areaSide = 3.1f, brakeTorque = 9600f,
                maxSteerDeg = 38f, colorsArgb = listOf(0xC8A02E, 0x101014, 0x27443A, 0xD8D8D8),
            ),
        )

        val starter: PgrCarDef get() = all.first()

        fun byId(id: String): PgrCarDef = all.firstOrNull { it.id == id } ?: starter

        /** AI grid car pick: deterministic, slower cars on easier settings. */
        fun forDifficulty(difficulty: PgrDifficulty, slot: Int): PgrCarDef {
            val pool = when (difficulty) {
                PgrDifficulty.EASY -> all.take(3)
                PgrDifficulty.MEDIUM -> all.drop(1).take(4)
                PgrDifficulty.HARD -> all.dropLast(1)
            }
            return pool[slot % pool.size]
        }
    }

    // ------------------------------------------------------------------
    // Kudos chain scoring (PGRKudosTracker equivalents)
    // ------------------------------------------------------------------

    /** Combo window: one full combo lasts this long without a new maneuver. */
    const val COMBO_SECONDS = 5f

    /** Minimum fill for a partial maneuver to be worth its rounded reward. */
    const val KUDOS_MIN_FILL = 0.025f

    data class PgrStashItem(val maneuver: PgrManeuver, val value: Int)

    data class PgrManeuverProgress(val fill: Float = 0f, val continueSeconds: Float = 0f)

    data class PgrKudosState(
        val bank: Int = 0,
        val stash: List<PgrStashItem> = emptyList(),
        val multiplier: Int = 0,
        val bonus: Int = 0,
        val comboSeconds: Float = 0f,
        val progress: Map<PgrManeuver, PgrManeuverProgress> = emptyMap(),
        val cleanSection: Boolean = true,
        val cleanLap: Boolean = true,
        val cleanRace: Boolean = true,
        val lastManeuver: PgrManeuver? = null,
        val failedFlash: Float = 0f,
    ) {
        val stashValue: Int get() = stash.sumOf { it.value }
        val pending: Int get() = stashValue + bonus
        val total: Int get() = bank + pending
    }

    /** Combo bonus: 0 for the first maneuver, then multiplier * 25. */
    fun comboBonus(multiplier: Int): Int = if (multiplier > 1) multiplier * 25 else 0

    fun maneuverReward(state: PgrKudosState, maneuver: PgrManeuver): Int {
        val p = state.progress[maneuver] ?: return 0
        if (p.fill < KUDOS_MIN_FILL) return 0
        return (p.fill * maneuver.value + 0.5f).toInt()
    }

    /** Partial progress with the reference's diminishing approach curve. */
    fun kudosFill(state: PgrKudosState, maneuver: PgrManeuver, fraction: Float): PgrKudosState {
        val cur = state.progress[maneuver] ?: PgrManeuverProgress()
        val gap = 1f - cur.fill
        val next = (cur.fill + gap * gap * clamp(fraction, 0f, 1f)).coerceIn(0f, 1f)
        return state.copy(
            progress = state.progress + (maneuver to PgrManeuverProgress(next, maneuver.continueSeconds)),
        )
    }

    /** Promote an in-progress maneuver into the stash and extend the combo. */
    fun kudosComplete(state: PgrKudosState, maneuver: PgrManeuver): PgrKudosState {
        val value = maneuverReward(state, maneuver)
        if (value <= 0) return state.copy(progress = state.progress - maneuver)
        val multiplier = state.multiplier + 1
        return state.copy(
            stash = state.stash + PgrStashItem(maneuver, value),
            multiplier = multiplier,
            bonus = comboBonus(multiplier),
            comboSeconds = COMBO_SECONDS,
            progress = state.progress - maneuver,
            lastManeuver = maneuver,
        )
    }

    /** Award a full maneuver immediately (instant maneuvers, clean bonuses). */
    fun kudosAward(state: PgrKudosState, maneuver: PgrManeuver): PgrKudosState =
        kudosComplete(kudosFill(state, maneuver, 1f), maneuver)

    /**
     * Advance continue windows and the combo timer. Completed maneuvers move
     * to the stash; an expired combo banks the stash plus its bonus.
     */
    fun kudosTick(state: PgrKudosState, dt: Float): PgrKudosState {
        var stash = state.stash
        var multiplier = state.multiplier
        var bonus = state.bonus
        var comboSeconds = state.comboSeconds
        val remainingProgress = LinkedHashMap<PgrManeuver, PgrManeuverProgress>()
        for ((maneuver, p) in state.progress) {
            val remaining = (p.continueSeconds - dt).coerceAtLeast(0f)
            val value = maneuverReward(state, maneuver).let { if (remaining <= 0f) it else 0 }
            when {
                value > 0 -> {
                    stash = stash + PgrStashItem(maneuver, value)
                    multiplier += 1
                    bonus = comboBonus(multiplier)
                    comboSeconds = COMBO_SECONDS
                }

                remaining > 0f -> remainingProgress[maneuver] = p.copy(continueSeconds = remaining)
                // window expired with no reward: drop silently
            }
        }
        var out = state.copy(
            stash = stash,
            multiplier = multiplier,
            bonus = bonus,
            comboSeconds = comboSeconds,
            progress = remainingProgress,
            failedFlash = (state.failedFlash - dt).coerceAtLeast(0f),
        )
        if (out.comboSeconds > 0f) {
            val left = out.comboSeconds - dt
            out = if (left <= 0f) kudosEndCombo(out) else out.copy(comboSeconds = left)
        }
        return out
    }

    /** Normal combo close: bank the stash and the combo bonus. */
    fun kudosEndCombo(state: PgrKudosState): PgrKudosState =
        state.copy(
            bank = state.bank + state.stashValue + state.bonus,
            stash = emptyList(),
            multiplier = 0,
            bonus = 0,
            comboSeconds = 0f,
            progress = emptyMap(),
        )

    /**
     * A collision fails the chain: in-progress maneuvers are forfeit (the
     * reference records them as failed, not banked), the stashed maneuvers are
     * banked, and the combo bonus is lost.
     */
    fun kudosFail(state: PgrKudosState): PgrKudosState =
        state.copy(
            bank = state.bank + state.stashValue,
            stash = emptyList(),
            multiplier = 0,
            bonus = 0,
            comboSeconds = 0f,
            progress = emptyMap(),
            lastManeuver = null,
            failedFlash = 1.2f,
            cleanSection = false,
            cleanLap = false,
            cleanRace = false,
        )

    // ------------------------------------------------------------------
    // Car physics (CPhysCar equivalents)
    // ------------------------------------------------------------------

    /**
     * Physics tuning. The model is physical: meters, seconds, kilograms and
     * Newtons, with the reference's grip/downforce/torque curves.
     */
    const val DRAG_SCALE = 0.6f            // 0.5 * air density
    const val ROLLING_RESISTANCE = 0.015f
    const val GRAVITY = 9.80665f
    const val LAT_GRIP_BASE = 6.5f
    const val LAT_DAMPING = 7.2f
    const val HANDBRAKE_GRIP = 0.55f
    const val HANDBRAKE_YAW_BOOST = 1.3f
    const val MIN_GRIP_YAW = 0.35f
    const val HANDBRAKE_DECEL = 5.5f
    const val MAX_YAW_RATE = 2.2f
    const val SHIFT_LOCKOUT_SECONDS = 0.26f
    const val IDLE_RPM = 900f
    const val SKID_LATERAL_SPEED = 8.333334f
    const val DRIFT_MIN_SPEED = 20.833334f
    const val BURNOUT_MAX_SPEED = 19.444445f
    const val SPEED_KUDOS_SPEED = 88.88889f
    const val DRAFT_MIN_SPEED = 27.777779f
    const val DRAFT_RANGE = 14f
    const val DRAFT_DRAG_REDUCTION = 0.55f
    const val BRAKE_STAT_ACCEL = 0.8f
    const val WALL_RESTITUTION = 0.35f
    const val WALL_SPEED_LOSS = 0.82f
    const val OFF_TRACK_DRAG = 0.992f
    const val CAR_HALF_WIDTH = 0.95f

    data class PgrCarState(
        val position: PgrVec2,
        val heading: Float,
        val velocity: PgrVec2 = PgrVec2(0f, 0f),
        val steer: Float = 0f,
        val throttle: Float = 0f,
        val brake: Float = 0f,
        val handbrake: Boolean = false,
        val gear: Int = 1,
        val rpm: Float = IDLE_RPM,
        val filteredRpm: Float = IDLE_RPM,
        val engineLoad: Float = 0f,
        val speed: Float = 0f,
        val forwardSpeed: Float = 0f,
        val lateralSpeed: Float = 0f,
        val alignment: Float = 0f,
        val yawRate: Float = 0f,
        val skidRear: Boolean = false,
        val skidFront: Boolean = false,
        val burnout: Boolean = false,
        val offTrack: Boolean = false,
        val hitWall: Boolean = false,
        val collisionCooldown: Float = 0f,
        val shiftRemaining: Float = 0f,
    )

    fun newCarState(def: PgrCarDef, position: PgrVec2, heading: Float): PgrCarState =
        PgrCarState(position = position, heading = wrapPi(heading), rpm = def.lowRpm, filteredRpm = def.lowRpm)

    /**
     * Advance one car by exactly one 30 Hz tic. [draft] is 0..1 slipstream
     * strength supplied by the race layer.
     */
    fun carTick(
        def: PgrCarDef,
        car: PgrCarState,
        input: PgrInput,
        draft: Float = 0f,
    ): PgrCarState {
        val dt = FIXED_TIME_STEP
        val handbrake = input.handbrake
        val rawThrottle = if (input.autoThrottle) max(input.throttle, 1f) else input.throttle
        val rawBrake = input.brake

        // In reverse the pedals swap, per the reference model.
        var throttleInput = clamp(rawThrottle, 0f, 1f)
        var brakeInput = clamp(rawBrake, 0f, 1f)
        if (car.gear == 0) {
            val swap = throttleInput
            throttleInput = brakeInput * max(0f, (def.highRpm - car.rpm) / def.highRpm)
            brakeInput = swap
        }

        // --- steering ---
        val steerTarget = clamp(input.steer, -1f, 1f)
        val steerRate = 5.5f * dt
        val steer = car.steer + clamp(steerTarget - car.steer, -steerRate, steerRate)

        // --- body yaw first; the velocity direction then lags through tire grip ---
        val speedBefore = max(car.speed, 0.01f)
        val alignBefore = clamp(abs(car.lateralSpeed) / speedBefore, 0f, 1f)
        val grip = sideGrip(def.handling)
        val downBefore = downforce(speedBefore, grip, alignBefore)
        var maxLatBefore = LAT_GRIP_BASE * (1f + downBefore)
        if (handbrake) maxLatBefore *= HANDBRAKE_GRIP
        val gripYaw = if (speedBefore > 1f) maxLatBefore / speedBefore else MAX_YAW_RATE
        val yawCap = min(MAX_YAW_RATE, max(MIN_GRIP_YAW, gripYaw))
        val forwardBefore = PgrVec2(sin(car.heading), cos(car.heading))
        val forwardSpeedBefore = car.velocity.x * forwardBefore.x + car.velocity.z * forwardBefore.z
        val speedSteer = 1f / (1f + speedBefore * 0.028f)
        val steerAngle = steer * def.maxSteerRad * speedSteer
        var yawRate = forwardSpeedBefore * tan(steerAngle) / def.wheelbase
        yawRate = clamp(yawRate, -yawCap, yawCap)
        if (handbrake) yawRate *= HANDBRAKE_YAW_BOOST
        val heading = wrapPi(car.heading + yawRate * dt)

        // --- decompose the unchanged velocity in the new body frame ---
        val forward = PgrVec2(sin(heading), cos(heading))
        val right = PgrVec2(forward.z, -forward.x)
        var forwardSpeed = car.velocity.x * forward.x + car.velocity.z * forward.z
        var lateralSpeed = car.velocity.x * right.x + car.velocity.z * right.z
        var speed = sqrt(forwardSpeed * forwardSpeed + lateralSpeed * lateralSpeed)

        // --- gearbox / engine ---
        var gear = car.gear
        var rpm = car.rpm
        var engineLoad = car.engineLoad
        var shiftLock = 0f

        val shifting = car.shiftRemaining > 0f
        val shiftRemaining = (car.shiftRemaining - dt).coerceAtLeast(0f)
        val wheelRpm = rpmFromWheelSpeed(def, forwardSpeed, gear)
        if (!shifting) {
            val ratio = max(abs(gearRatio(def, gear)), 0.3f)
            rpm += (1f - engineLoad) * (ratio * throttleInput * def.accel * 2f - brakeInput * 0.05f * def.brake)
            rpm = lerp(rpm, wheelRpm, 0.1f)
            rpm = clamp(rpm, IDLE_RPM, def.highRpm * 1.1f)
            val loadTarget = throttleInput * (1f - clamp(abs(rpm - wheelRpm) / (def.highRpm - def.lowRpm), 0f, 0.8f))
            engineLoad = lerp(engineLoad, loadTarget, 0.2f)
            val revving = rpm > def.highRpm * 1.05f && wheelRpm > def.highRpm * 0.95f
            val launch = gear <= 1 && rpm < 2000f && wheelRpm < 2000f && throttleInput > 0f
            val downLow = rpm < def.lowRpm && wheelRpm < def.lowRpm && gear > 2
            val brakeDown = gear <= 2 && rpm < 1000f && wheelRpm < 1000f && rawBrake > 0f
            when {
                throttleInput > 0f && (revving || launch) -> {
                    if (gear < def.gears.size) {
                        gear += 1
                        shiftLock = SHIFT_LOCKOUT_SECONDS
                        engineLoad = 0f
                    }
                }

                brakeInput == 0f && (downLow || brakeDown) -> {
                    if (gear > 0 && !handbrake) {
                        gear -= 1
                        shiftLock = SHIFT_LOCKOUT_SECONDS
                        engineLoad = 0f
                    }
                }

                throttleInput == 0f -> rpm *= 0.997f
            }
        }

        // --- longitudinal forces ---
        val ratio = gearRatio(def, gear)
        val torque = if (shifting || shiftLock > 0f) 0f else torqueAt(def, rpm, throttleInput)
        var driveForce = torque * ratio * def.finalDrive / def.wheelRadius
        if (handbrake) driveForce = 0f

        val alignment = if (speed > 0.1f) clamp(abs(lateralSpeed) / speed, 0f, 1f) else 0f
        val area = dragArea(def.areaFront, def.areaSide, alignment)
        val draftFactor = (1f - DRAFT_DRAG_REDUCTION * clamp(draft, 0f, 1f)).coerceIn(0.2f, 1f)
        val dragForce = DRAG_SCALE * area * speed * speed * draftFactor
        val rollForce = ROLLING_RESISTANCE * def.massKg * GRAVITY
        val resistance = dragForce + rollForce
        val accel = (driveForce - sign(forwardSpeed) * resistance) / def.massKg
        forwardSpeed += accel * dt
        if (handbrake) {
            val hb = min(abs(forwardSpeed) / dt, HANDBRAKE_DECEL)
            forwardSpeed -= sign(forwardSpeed) * hb * dt
        }
        val brakeAccel = brakeInput * def.brake * BRAKE_STAT_ACCEL
        val brakeStep = min(abs(forwardSpeed) / dt, brakeAccel)
        forwardSpeed -= sign(forwardSpeed) * brakeStep * dt
        if (abs(forwardSpeed) < 0.04f && throttleInput == 0f && brakeInput == 0f) forwardSpeed = 0f

        // --- lateral grip / downforce (the slip the body yaw introduced) ---
        val downforceNow = downforce(speed, grip, alignment)
        var maxLat = LAT_GRIP_BASE * (1f + downforceNow)
        if (handbrake) maxLat *= HANDBRAKE_GRIP
        val latAccel = clamp(-lateralSpeed * LAT_DAMPING, -maxLat, maxLat)
        lateralSpeed += latAccel * dt
        lateralSpeed = lateralSpeed.coerceIn(-maxLat * 1.35f, maxLat * 1.35f)

        val velocity = PgrVec2(
            forward.x * forwardSpeed + right.x * lateralSpeed,
            forward.z * forwardSpeed + right.z * lateralSpeed,
        )
        speed = sqrt(forwardSpeed * forwardSpeed + lateralSpeed * lateralSpeed)
        val newAlignment = if (speed > 0.1f) clamp(abs(lateralSpeed) / speed, 0f, 1f) else 0f

        val rearSkid = abs(lateralSpeed) > SKID_LATERAL_SPEED || (handbrake && speed > 5f)
        val frontSkid = abs(lateralSpeed) > SKID_LATERAL_SPEED * 1.6f
        val burnout = speed < BURNOUT_MAX_SPEED && throttleInput >= 0.5f && abs(forwardSpeed) < 8f &&
            driveForce > 600f && !shifting

        val filtered = if (abs(rpm - car.filteredRpm) < 120f) rpm else car.filteredRpm + sign(rpm - car.filteredRpm) * 120f

        return car.copy(
            position = PgrVec2(car.position.x + velocity.x * dt, car.position.z + velocity.z * dt),
            heading = heading,
            velocity = velocity,
            steer = steer,
            throttle = throttleInput,
            brake = brakeInput,
            handbrake = handbrake,
            gear = gear,
            rpm = rpm,
            filteredRpm = filtered,
            engineLoad = engineLoad,
            speed = speed,
            forwardSpeed = forwardSpeed,
            lateralSpeed = lateralSpeed,
            alignment = newAlignment,
            yawRate = yawRate,
            skidRear = rearSkid,
            skidFront = frontSkid,
            burnout = burnout,
            offTrack = car.offTrack,
            hitWall = false,
            collisionCooldown = (car.collisionCooldown - dt).coerceAtLeast(0f),
            shiftRemaining = max(shiftRemaining, shiftLock),
        )
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    data class PgrInput(
        val steer: Float = 0f,
        val throttle: Float = 0f,
        val brake: Float = 0f,
        val handbrake: Boolean = false,
        val autoThrottle: Boolean = false,
    ) {
        companion object {
            val NEUTRAL = PgrInput()
            val FULL_THROTTLE = PgrInput(throttle = 1f, autoThrottle = true)
        }
    }

    // ------------------------------------------------------------------
    // Track model
    // ------------------------------------------------------------------

    data class PgrVec2(val x: Float, val z: Float) {
        operator fun plus(o: PgrVec2) = PgrVec2(x + o.x, z + o.z)
        operator fun minus(o: PgrVec2) = PgrVec2(x - o.x, z - o.z)
        operator fun times(s: Float) = PgrVec2(x * s, z * s)
        infix fun dot(o: PgrVec2): Float = x * o.x + z * o.z
        fun length(): Float = sqrt(x * x + z * z)
        fun normalized(): PgrVec2 {
            val len = length()
            return if (len < 1e-6f) PgrVec2(0f, 0f) else PgrVec2(x / len, z / len)
        }

        fun lerp(o: PgrVec2, t: Float) = PgrVec2(x + (o.x - x) * t, z + (o.z - z) * t)

        companion object {
            val ZERO = PgrVec2(0f, 0f)
        }
    }

    data class PgrSample(
        val position: PgrVec2,
        val forward: PgrVec2,
        val right: PgrVec2,
        val curvature: Float,
    )

    data class PgrProjection(val distance: Float, val lateral: Float, val index: Int)

    data class PgrTrackDef(
        val id: String,
        val name: String,
        val city: PgrCity,
        val controlPoints: List<PgrVec2>,
        val width: Float,
        val checkpointFractions: List<Float> = listOf(0.34f, 0.67f, 0.88f),
        val propSeed: Int = DEFAULT_SEED,
    )

    /**
     * Sampled centerline: a closed Catmull-Rom loop with per-sample tangent,
     * signed curvature and a smoothed racing-line offset.
     */
    class PgrTrack internal constructor(
        val def: PgrTrackDef,
        private val xs: FloatArray,
        private val zs: FloatArray,
        private val fwdX: FloatArray,
        private val fwdZ: FloatArray,
        private val curvature: FloatArray,
        private val cumulative: FloatArray,
        private val racingOffset: FloatArray,
        val length: Float,
        val spacing: Float,
    ) {
        val count: Int get() = xs.size
        val width: Float get() = def.width

        private fun indexAt(distance: Float): Int {
            var lo = 0
            var hi = count - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (cumulative[mid] <= distance) lo = mid else hi = mid - 1
            }
            return lo
        }

        private fun segmentLength(index: Int): Float =
            if (index == count - 1) length - cumulative[index]
            else cumulative[index + 1] - cumulative[index]

        /** Sample index nearest a lap distance; handy for hints and tests. */
        fun sampleIndexAt(distance: Float): Int = indexAt(wrapDistance(distance, length))

        fun sampleAt(distance: Float): PgrSample {
            val d = wrapDistance(distance, length)
            val i0 = indexAt(d)
            val i1 = (i0 + 1) % count
            val seg = segmentLength(i0)
            val t = if (seg < 1e-4f) 0f else ((d - cumulative[i0]) / seg).coerceIn(0f, 1f)
            return PgrSample(
                position = PgrVec2(lerp(xs[i0], xs[i1], t), lerp(zs[i0], zs[i1], t)),
                forward = PgrVec2(lerp(fwdX[i0], fwdX[i1], t), lerp(fwdZ[i0], fwdZ[i1], t)).normalized(),
                right = PgrVec2(lerp(fwdZ[i0], fwdZ[i1], t), -lerp(fwdX[i0], fwdX[i1], t)).normalized(),
                curvature = lerp(curvature[i0], curvature[i1], t),
            )
        }

        fun positionAt(distance: Float, lateral: Float = 0f): PgrVec2 {
            val s = sampleAt(distance)
            return s.position + s.right * lateral
        }

        fun headingAt(distance: Float): Float {
            val s = sampleAt(distance)
            return atan2(s.forward.x, s.forward.z)
        }

        fun racingOffsetAt(distance: Float): Float {
            val d = wrapDistance(distance, length)
            val i0 = indexAt(d)
            val i1 = (i0 + 1) % count
            val seg = segmentLength(i0)
            val t = if (seg < 1e-4f) 0f else ((d - cumulative[i0]) / seg).coerceIn(0f, 1f)
            return lerp(racingOffset[i0], racingOffset[i1], t)
        }

        /** Max |curvature| over [distance, distance + window]; 0 when straight. */
        fun maxCurvature(distance: Float, window: Float): Float {
            val steps = max(2, (window / spacing).toInt())
            var maxCurv = 0f
            for (i in 0..steps) {
                val c = abs(sampleAt(distance + window * i / steps).curvature)
                if (c > maxCurv) maxCurv = c
            }
            return maxCurv
        }

        /**
         * Project a world point onto the centerline. A [hint] index from the
         * previous frame keeps the search local; pass -1 for a full scan.
         */
        fun project(x: Float, z: Float, hint: Int = -1): PgrProjection {
            var bestIndex = 0
            var bestD2 = Float.MAX_VALUE
            if (hint in 0 until count) {
                for (k in -6..6) {
                    val i = ((hint + k) % count + count) % count
                    val dx = x - xs[i]
                    val dz = z - zs[i]
                    val d2 = dx * dx + dz * dz
                    if (d2 < bestD2) {
                        bestD2 = d2
                        bestIndex = i
                    }
                }
            }
            // A teleport (or a lost hint) falls back to a full scan.
            val window = spacing * 8f
            if (hint !in 0 until count || bestD2 > window * window) {
                for (i in 0 until count) {
                    val dx = x - xs[i]
                    val dz = z - zs[i]
                    val d2 = dx * dx + dz * dz
                    if (d2 < bestD2) {
                        bestD2 = d2
                        bestIndex = i
                    }
                }
            }
            val dx = x - xs[bestIndex]
            val dz = z - zs[bestIndex]
            val along = clamp(dx * fwdX[bestIndex] + dz * fwdZ[bestIndex], -spacing, spacing)
            val lateral = dx * fwdZ[bestIndex] - dz * fwdX[bestIndex]
            return PgrProjection(
                wrapDistance(cumulative[bestIndex] + along, length),
                lateral,
                bestIndex,
            )
        }

        val checkpointDistances: List<Float> =
            def.checkpointFractions.map { it * length }.sorted().take(MAX_CHECKPOINTS)

        val checkpointCount: Int get() = checkpointDistances.size
    }

    object PgrTracks {
        private val defs = listOf(
            PgrTrackDef(
                id = "kingsway-loop",
                name = "kingsway loop",
                city = PgrCity.LONDON,
                controlPoints = listOf(
                    PgrVec2(0f, 0f), PgrVec2(95f, -20f), PgrVec2(180f, 0f), PgrVec2(225f, 70f),
                    PgrVec2(200f, 150f), PgrVec2(125f, 195f), PgrVec2(40f, 180f), PgrVec2(-25f, 115f),
                    PgrVec2(-20f, 40f),
                ),
                width = 10f,
                propSeed = 1374496523,
            ),
            PgrTrackDef(
                id = "neon-bay",
                name = "neon bay",
                city = PgrCity.TOKYO,
                controlPoints = listOf(
                    PgrVec2(0f, 0f), PgrVec2(120f, -30f), PgrVec2(235f, 10f), PgrVec2(285f, 95f),
                    PgrVec2(250f, 185f), PgrVec2(160f, 225f), PgrVec2(65f, 205f), PgrVec2(-20f, 150f),
                    PgrVec2(-35f, 65f),
                ),
                width = 11f,
                propSeed = 1684169803,
            ),
            PgrTrackDef(
                id = "harbor-grid",
                name = "harbor grid",
                city = PgrCity.NEW_YORK,
                controlPoints = listOf(
                    PgrVec2(0f, 0f), PgrVec2(75f, -25f), PgrVec2(155f, -10f), PgrVec2(205f, 35f),
                    PgrVec2(190f, 90f), PgrVec2(135f, 110f), PgrVec2(90f, 95f), PgrVec2(85f, 140f),
                    PgrVec2(130f, 165f), PgrVec2(190f, 150f), PgrVec2(215f, 195f), PgrVec2(150f, 235f),
                    PgrVec2(60f, 220f), PgrVec2(0f, 175f), PgrVec2(-30f, 100f), PgrVec2(-20f, 35f),
                ),
                width = 9f,
                propSeed = 2026497481,
            ),
        )

        val all: List<PgrTrack> by lazy { defs.map { build(it) } }

        fun byId(id: String): PgrTrack = all.firstOrNull { it.def.id == id } ?: all.first()

        fun byIndex(index: Int): PgrTrack = all[index.coerceIn(0, all.size - 1)]

        const val SUBDIVISIONS = 12

        private fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            return 0.5f * (
                2f * p1 +
                    (-p0 + p2) * t +
                    (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                    (-p0 + 3f * p1 - 3f * p2 + p3) * t3
                )
        }

        /** Build the sampled loop. Public for tests; callers normally use [all]. */
        fun build(def: PgrTrackDef): PgrTrack {
            val ctrl = def.controlPoints
            val n = ctrl.size
            require(n >= 3) { "a closed track needs at least three control points" }
            val sub = SUBDIVISIONS
            val count = n * sub
            val xs = FloatArray(count)
            val zs = FloatArray(count)
            for (i in 0 until n) {
                val p0 = ctrl[(i - 1 + n) % n]
                val p1 = ctrl[i]
                val p2 = ctrl[(i + 1) % n]
                val p3 = ctrl[(i + 2) % n]
                for (j in 0 until sub) {
                    val t = j.toFloat() / sub
                    xs[i * sub + j] = catmull(p0.x, p1.x, p2.x, p3.x, t)
                    zs[i * sub + j] = catmull(p0.z, p1.z, p2.z, p3.z, t)
                }
            }
            val cumulative = FloatArray(count)
            for (i in 1 until count) {
                cumulative[i] = cumulative[i - 1] + PgrVec2(xs[i] - xs[i - 1], zs[i] - zs[i - 1]).length()
            }
            val length = cumulative[count - 1] +
                PgrVec2(xs[0] - xs[count - 1], zs[0] - zs[count - 1]).length()
            val fwdX = FloatArray(count)
            val fwdZ = FloatArray(count)
            val curvature = FloatArray(count)
            for (i in 0 until count) {
                val prev = (i - 1 + count) % count
                val next = (i + 1) % count
                val dx = xs[next] - xs[prev]
                val dz = zs[next] - zs[prev]
                val len = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
                fwdX[i] = dx / len
                fwdZ[i] = dz / len
            }
            for (i in 0 until count) {
                val prev = (i - 1 + count) % count
                val next = (i + 1) % count
                val hPrev = atan2(fwdX[prev], fwdZ[prev])
                val hNext = atan2(fwdX[next], fwdZ[next])
                val ds = 0.5f * (
                    PgrVec2(xs[next] - xs[i], zs[next] - zs[i]).length() +
                        PgrVec2(xs[i] - xs[prev], zs[i] - zs[prev]).length()
                    ).coerceAtLeast(1e-4f)
                curvature[i] = wrapPi(hNext - hPrev) / ds
            }
            val racingOffset = FloatArray(count)
            for (i in 0 until count) {
                val c = curvature[i]
                racingOffset[i] = -sign(c) * def.width * 0.25f * clamp(abs(c) * 40f, 0f, 1f)
            }
            for (pass in 0 until 2) {
                val copy = racingOffset.copyOf()
                for (i in 0 until count) {
                    val prev = (i - 1 + count) % count
                    val next = (i + 1) % count
                    racingOffset[i] = (copy[prev] + 2f * copy[i] + copy[next]) * 0.25f
                }
            }
            return PgrTrack(
                def = def,
                xs = xs,
                zs = zs,
                fwdX = fwdX,
                fwdZ = fwdZ,
                curvature = curvature,
                cumulative = cumulative,
                racingOffset = racingOffset,
                length = length,
                spacing = length / count,
            )
        }
    }

    // ------------------------------------------------------------------
    // Career events
    // ------------------------------------------------------------------

    data class PgrEventDef(
        val id: String,
        val name: String,
        val type: PgrEventType,
        val trackId: String,
        val laps: Int,
        val difficulty: PgrDifficulty,
        val prerequisites: List<String> = emptyList(),
        val limitMs: Long = 0L,
        val parMs: Long = 0L,
        val targetKudos: Int = 0,
        val targetSpeed: Float = 0f,
        val targetOvertakes: Int = 0,
        val gateCount: Int = 0,
        val rewardCarId: String? = null,
        val blurb: String = "",
    )

    object PgrEvents {
        val all: List<PgrEventDef> = listOf(
            PgrEventDef(
                id = "ev01", name = "first turn", type = PgrEventType.BREAKTHROUGH,
                trackId = "kingsway-loop", laps = 2, difficulty = PgrDifficulty.EASY,
                limitMs = 170_000L, parMs = 150_000L,
                blurb = "learn the loop and finish before the clock runs out.",
            ),
            PgrEventDef(
                id = "ev02", name = "kudos run", type = PgrEventType.KUDOS_CHALLENGE,
                trackId = "kingsway-loop", laps = 2, difficulty = PgrDifficulty.EASY,
                prerequisites = listOf("ev01"), targetKudos = 1500, parMs = 160_000L,
                blurb = "chain drifts, drafts and clean sections for kudos.",
            ),
            PgrEventDef(
                id = "ev03", name = "neon sprint", type = PgrEventType.CONE_SPRINT,
                trackId = "neon-bay", laps = 1, difficulty = PgrDifficulty.MEDIUM,
                prerequisites = listOf("ev02"), limitMs = 110_000L, parMs = 95_000L, gateCount = 6,
                blurb = "thread every cone gate before the clock expires.",
            ),
            PgrEventDef(
                id = "ev04", name = "one on one", type = PgrEventType.ONE_ON_ONE,
                trackId = "neon-bay", laps = 3, difficulty = PgrDifficulty.MEDIUM,
                prerequisites = listOf("ev03"), parMs = 210_000L,
                blurb = "beat the rival in the black car across three laps.",
            ),
            PgrEventDef(
                id = "ev05", name = "elimination", type = PgrEventType.ELIMINATOR,
                trackId = "harbor-grid", laps = 3, difficulty = PgrDifficulty.MEDIUM,
                prerequisites = listOf("ev04"), parMs = 220_000L,
                blurb = "last place is cut every interval. survive to the flag.",
            ),
            PgrEventDef(
                id = "ev06", name = "slipstream", type = PgrEventType.OVERTAKE,
                trackId = "kingsway-loop", laps = 3, difficulty = PgrDifficulty.MEDIUM,
                prerequisites = listOf("ev05"), targetOvertakes = 5, parMs = 210_000L,
                blurb = "draft past five rivals for slingshot kudos.",
            ),
            PgrEventDef(
                id = "ev07", name = "speed trial", type = PgrEventType.SPEED_CHALLENGE,
                trackId = "neon-bay", laps = 2, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev06"), targetSpeed = 75f, parMs = 170_000L,
                blurb = "hit 270 km/h on the back straight.",
            ),
            PgrEventDef(
                id = "ev08", name = "time vs kudos", type = PgrEventType.TIME_VS_KUDOS,
                trackId = "harbor-grid", laps = 3, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev06"), limitMs = 200_000L, parMs = 185_000L,
                targetKudos = 2000,
                blurb = "finish under the limit with a fat kudos bank.",
            ),
            PgrEventDef(
                id = "ev09", name = "street race", type = PgrEventType.STREET_RACE,
                trackId = "kingsway-loop", laps = 4, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev07"), parMs = 260_000L,
                blurb = "four laps, full field, first place only.",
            ),
            PgrEventDef(
                id = "ev10", name = "harbor battle", type = PgrEventType.STREET_RACE,
                trackId = "harbor-grid", laps = 4, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev08"), parMs = 280_000L,
                rewardCarId = "aquila-sc", blurb = "win here to unlock the aquila sc.",
            ),
            PgrEventDef(
                id = "ev11", name = "neon masters", type = PgrEventType.ONE_ON_ONE,
                trackId = "neon-bay", laps = 4, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev09"), parMs = 280_000L,
                blurb = "the masters final. one rival, no mistakes.",
            ),
            PgrEventDef(
                id = "ev12", name = "grand finale", type = PgrEventType.STREET_RACE,
                trackId = "kingsway-loop", laps = MAX_LAPS, difficulty = PgrDifficulty.HARD,
                prerequisites = listOf("ev10", "ev11"), parMs = 340_000L,
                rewardCarId = "falco-stradale", blurb = "six laps for the falco stradale.",
            ),
        )

        fun byId(id: String): PgrEventDef? = all.firstOrNull { it.id == id }
    }

    // ------------------------------------------------------------------
    // Race state and simulation
    // ------------------------------------------------------------------

    data class PgrRacer(
        val index: Int,
        val isPlayer: Boolean,
        val name: String,
        val carId: String,
        val car: PgrCarState,
        val hintIndex: Int = 0,
        val lapsCompleted: Int = 0,
        val nextCheckpoint: Int = 0,
        val progress: Float = 0f,
        val totalProgress: Float = 0f,
        val lapStartTicks: Long = 0L,
        val lastLapTicks: Long? = null,
        val bestLapTicks: Long? = null,
        val finished: Boolean = false,
        val finishTicks: Long? = null,
        val position: Int = 1,
        val lastPosition: Int = 1,
        val overtakes: Int = 0,
        val eliminated: Boolean = false,
        val gatesPassed: Int = 0,
        val nextGate: Int = 0,
        val topSpeed: Float = 0f,
        val spinRadians: Float = 0f,
        val draft: Float = 0f,
        val draftUntilTicks: Long = 0L,
        val kudos: PgrKudosState = PgrKudosState(),
        val aiSkill: Float = 1f,
        val aiOffset: Float = 0f,
        val wallCooldown: Float = 0f,
        val lastLapValid: Boolean = true,
    )

    data class PgrRaceState(
        val trackId: String,
        val eventId: String? = null,
        val difficulty: PgrDifficulty = PgrDifficulty.MEDIUM,
        val laps: Int = 3,
        val phase: PgrPhase = PgrPhase.COUNTDOWN,
        val countdownTicks: Int = 3 * TICS_PER_SECOND,
        val raceTicks: Long = 0L,
        val racers: List<PgrRacer>,
        val rng: Int = DEFAULT_SEED,
        val accumulatorMs: Long = 0L,
        val eliminationIntervalTicks: Long = 30L * TICS_PER_SECOND,
        val nextEliminationTicks: Long = 30L * TICS_PER_SECOND,
        val gates: List<Float> = emptyList(),
        val aiOnly: Boolean = false,
    )

    /**
     * Build a race grid. The player starts at the back of the field so
     * overtake/kudos play has room. All AI parameters are explicit.
     */
    fun newRace(
        trackId: String,
        playerCarId: String,
        difficulty: PgrDifficulty = PgrDifficulty.MEDIUM,
        laps: Int = 3,
        eventId: String? = null,
        seed: Int = DEFAULT_SEED,
        gateCount: Int = 0,
        aiOnly: Boolean = false,
    ): PgrRaceState {
        val track = PgrTracks.byId(trackId)
        val lapCount = laps.coerceIn(1, MAX_LAPS)
        val names = listOf("you", "rival", "rival", "rival")
        val racers = ArrayList<PgrRacer>(MAX_PLAYERS)
        for (slot in 0 until MAX_PLAYERS) {
            val isPlayer = slot == PLAYER_INDEX && !aiOnly
            val def = if (isPlayer) PgrCars.byId(playerCarId) else PgrCars.forDifficulty(difficulty, slot)
            // Grid slots run front to back: slot 0 (the player) starts last.
            val gridDistance = track.length - 14f - (MAX_PLAYERS - 1 - slot) * 8f
            val lateral = if (slot % 2 == 0) -2.6f else 2.6f
            val sample = track.sampleAt(gridDistance)
            val car = newCarState(
                def,
                track.positionAt(gridDistance, lateral),
                atan2(sample.forward.x, sample.forward.z),
            )
            racers += PgrRacer(
                index = slot,
                isPlayer = isPlayer,
                name = if (isPlayer) "you" else names[slot] + " ${slot + 1}",
                carId = def.id,
                car = car,
                hintIndex = 0,
                progress = wrapDistance(gridDistance, track.length),
                totalProgress = gridDistance - track.length,
                lapStartTicks = 0L,
                position = MAX_PLAYERS - slot,
                lastPosition = MAX_PLAYERS - slot,
                aiSkill = if (isPlayer) 1f else difficulty.aiSkill * (0.97f + 0.02f * slot),
                aiOffset = if (isPlayer) 0f else noise(seed, slot) * 1.6f,
            )
        }
        val gates = if (gateCount > 0) {
            List(gateCount) { (it + 1) * track.length / (gateCount + 1) }
        } else {
            emptyList()
        }
        return PgrRaceState(
            trackId = trackId,
            eventId = eventId,
            difficulty = difficulty,
            laps = lapCount,
            racers = racers,
            rng = if (seed == 0) DEFAULT_SEED else seed,
            gates = gates,
            aiOnly = aiOnly,
        )
    }

    /**
     * One fixed 30 Hz race tic. Pass [playerInput] = null to drive the player
     * slot with the AI (deterministic replays and AI-only soak runs).
     */
    fun tickRace(state: PgrRaceState, playerInput: PgrInput? = null): PgrRaceState {
        val track = PgrTracks.byId(state.trackId)
        if (state.phase == PgrPhase.FINISHED) return state
        if (state.phase == PgrPhase.COUNTDOWN) {
            val remaining = state.countdownTicks - 1
            return if (remaining <= 0) {
                state.copy(countdownTicks = 0, phase = PgrPhase.RACING, raceTicks = 0L).let { fresh ->
                    // Re-base lap clocks at the green light.
                    fresh.copy(racers = fresh.racers.map { it.copy(lapStartTicks = 0L) })
                }
            } else {
                state.copy(countdownTicks = remaining)
            }
        }

        var rng = nextSeed(state.rng)
        val draftValues = FloatArray(state.racers.size)
        run {
            val order = orderedIndices(state)
            for (k in 1 until order.size) {
                val ahead = state.racers[order[k - 1]]
                val behind = state.racers[order[k]]
                val dx = ahead.car.position.x - behind.car.position.x
                val dz = ahead.car.position.z - behind.car.position.z
                val dist = sqrt(dx * dx + dz * dz)
                if (dist < DRAFT_RANGE) {
                    draftValues[order[k]] = (1f - dist / DRAFT_RANGE) * 0.85f
                }
            }
        }

        val updated = ArrayList<PgrRacer>(state.racers.size)
        for (racer in state.racers) {
            if (racer.finished || racer.eliminated) {
                updated += racer
                continue
            }
            val input = if (racer.index == PLAYER_INDEX && playerInput != null) {
                playerInput
            } else {
                aiInput(state, racer.index, rng)
            }
            val def = PgrCars.byId(racer.carId)
            var car = carTick(def, racer.car, input, draftValues[racer.index])
            var next = racer.copy(car = car, draft = draftValues[racer.index])

            // Track surface and barriers.
            val projection = track.project(car.position.x, car.position.z, racer.hintIndex)
            val half = track.width * 0.5f - CAR_HALF_WIDTH
            var collided = false
            if (abs(projection.lateral) > half) {
                val clamped = track.positionAt(projection.distance, sign(projection.lateral) * half)
                val forward = PgrVec2(sin(car.heading), cos(car.heading))
                val right = PgrVec2(forward.z, -forward.x)
                var vF = car.velocity.x * forward.x + car.velocity.z * forward.z
                var vR = car.velocity.x * right.x + car.velocity.z * right.z
                vR = -vR * WALL_RESTITUTION
                vF *= WALL_SPEED_LOSS
                car = car.copy(position = clamped, velocity = forward * vF + right * vR, hitWall = true)
                collided = next.wallCooldown <= 0f
                next = next.copy(wallCooldown = 0.45f)
            } else if (abs(projection.lateral) > track.width * 0.5f) {
                car = car.copy(velocity = car.velocity * OFF_TRACK_DRAG, offTrack = true)
            } else {
                car = car.copy(offTrack = false)
            }
            if (collided) {
                next = next.copy(
                    kudos = kudosFail(next.kudos),
                    car = car.copy(collisionCooldown = 0.45f),
                    lastLapValid = false,
                )
            } else {
                next = next.copy(car = car, wallCooldown = (next.wallCooldown - FIXED_TIME_STEP).coerceAtLeast(0f))
            }

            // Lap / checkpoint / gate bookkeeping.
            next = updateRacerOnTrack(next, track, state.raceTicks, state.laps, state.gates)

            // Kudos maneuvers.
            next = updateRacerKudos(next, track, state.raceTicks)

            updated += next
        }

        // Draft bookmarking for slingshot detection.
        var racers = updated.mapIndexed { i, r ->
            if (draftValues[i] > 0.45f) r.copy(draftUntilTicks = state.raceTicks + 2L * TICS_PER_SECOND) else r
        }

        // Positions and overtake kudos.
        racers = updatePositions(racers, state.raceTicks)

        var next = state.copy(
            racers = racers,
            raceTicks = state.raceTicks + 1L,
            rng = rng,
        )

        // Eliminator: cut the last running car at each interval.
        if (state.eventId != null && next.eventId != null) {
            val event = PgrEvents.byId(next.eventId)
            if (event?.type == PgrEventType.ELIMINATOR && next.raceTicks >= next.nextEliminationTicks) {
                val running = next.racers.filter { !it.finished && !it.eliminated }
                val last = running.maxByOrNull { it.position }
                if (last != null && running.size > 1) {
                    next = next.copy(
                        racers = next.racers.map {
                            if (it.index == last.index) it.copy(eliminated = true, kudos = kudosEndCombo(it.kudos)) else it
                        },
                        nextEliminationTicks = next.nextEliminationTicks + next.eliminationIntervalTicks,
                    )
                }
            }
        }

        val player = next.racers.firstOrNull { it.isPlayer }
        val allDone = next.racers.all { it.finished || it.eliminated }
        if ((player != null && (player.finished || player.eliminated)) || allDone) {
            next = next.copy(phase = PgrPhase.FINISHED)
        }
        return next
    }

    /** Advance a race by wall time using the fixed-step accumulator. */
    fun stepRace(state: PgrRaceState, playerInput: PgrInput?, dtMs: Long): PgrRaceState {
        if (dtMs <= 0L) return state
        var current = state
        var acc = current.accumulatorMs + dtMs
        val maxTicks = (dtMs / TICK_MS).toInt() + 4
        var guard = 0
        while (acc >= TICK_MS && guard < maxTicks) {
            current = tickRace(current, playerInput)
            acc -= TICK_MS
            guard++
        }
        return current.copy(accumulatorMs = acc)
    }

    /** Project a racer onto the track and resolve checkpoints/laps/gates. */
    fun updateRacerOnTrack(
        racer: PgrRacer,
        track: PgrTrack,
        raceTicks: Long,
        laps: Int,
        gates: List<Float> = emptyList(),
    ): PgrRacer {
        val projection = track.project(racer.car.position.x, racer.car.position.z, racer.hintIndex)
        val len = track.length
        val prev = racer.progress
        val now = projection.distance
        var next = racer.copy(
            hintIndex = projection.index,
            progress = now,
            topSpeed = max(racer.topSpeed, racer.car.speed),
        )

        // Cone gates (cone sprint).
        if (gates.isNotEmpty()) {
            while (next.nextGate < gates.size && crossedForward(prev, now, gates[next.nextGate], len)) {
                next = next.copy(
                    gatesPassed = next.gatesPassed + 1,
                    nextGate = next.nextGate + 1,
                    kudos = kudosAward(next.kudos, PgrManeuver.CONE_GATE),
                )
            }
        }

        // Checkpoints in order.
        val cp = track.checkpointDistances
        while (next.nextCheckpoint < cp.size && crossedForward(prev, now, cp[next.nextCheckpoint], len)) {
            val clean = next.kudos.cleanSection
            val kudos = if (clean) kudosAward(next.kudos, PgrManeuver.CLEAN_SECTION) else next.kudos
            next = next.copy(
                nextCheckpoint = next.nextCheckpoint + 1,
                kudos = kudos.copy(cleanSection = true),
            )
        }

        // Start/finish line crossing.
        if (crossedLine(prev, now, len)) {
            if (next.nextCheckpoint >= cp.size && cp.isNotEmpty()) {
                val lastLap = raceTicks - next.lapStartTicks
                val best = next.bestLapTicks?.let { min(it, lastLap) } ?: lastLap
                val cleanLap = next.kudos.cleanLap
                var kudos = if (cleanLap) kudosAward(next.kudos, PgrManeuver.CLEAN_LAP) else next.kudos
                kudos = kudos.copy(cleanLap = true)
                val completedLaps = next.lapsCompleted + 1
                if (completedLaps >= laps) {
                    kudos = kudosAward(kudos, PgrManeuver.CLEAN_RACE)
                    if (next.position == 1) kudos = kudosAward(kudos, PgrManeuver.CLEAN_WIN)
                    next = next.copy(
                        lapsCompleted = completedLaps,
                        nextCheckpoint = 0,
                        lastLapTicks = lastLap,
                        bestLapTicks = best,
                        finished = true,
                        finishTicks = raceTicks,
                        kudos = kudosEndCombo(kudos),
                    )
                } else {
                    next = next.copy(
                        lapsCompleted = completedLaps,
                        nextCheckpoint = 0,
                        lapStartTicks = raceTicks,
                        lastLapTicks = lastLap,
                        bestLapTicks = best,
                        kudos = kudos,
                    )
                }
            } else {
                // Missed a checkpoint: the lap does not count, and the clock
                // re-bases on the line so lap one is timed from the green.
                next = next.copy(
                    nextCheckpoint = 0,
                    lastLapValid = false,
                    lapStartTicks = raceTicks,
                )
            }
        }

        return next.copy(totalProgress = next.lapsCompleted * len + now)
    }

    private fun crossedForward(prev: Float, now: Float, target: Float, len: Float): Boolean =
        prev < target && now >= target - CHECKPOINT_EPSILON && now - prev < len * 0.5f

    /** Half-metre tolerance so sampled projections cannot skip a line. */
    const val CHECKPOINT_EPSILON = 0.5f

    private fun crossedLine(prev: Float, now: Float, len: Float): Boolean =
        prev > now && prev - now > len * 0.5f

    /** Per-tic kudos maneuver triggers for one racer. */
    fun updateRacerKudos(racer: PgrRacer, track: PgrTrack, raceTicks: Long): PgrRacer {
        var kudos = kudosTick(racer.kudos, FIXED_TIME_STEP)
        val car = racer.car
        val projection = track.project(car.position.x, car.position.z, racer.hintIndex)

        if (car.speed >= DRIFT_MIN_SPEED && abs(car.lateralSpeed) >= SKID_LATERAL_SPEED && car.skidRear) {
            val rate = clamp(2f * car.speed / 41.666668f, 1f, 3.5f)
            kudos = kudosFill(kudos, PgrManeuver.DRIFT, FIXED_TIME_STEP * rate * 0.6f)
        }
        if (car.burnout) {
            kudos = kudosFill(kudos, PgrManeuver.BURNOUT, FIXED_TIME_STEP * 0.5f)
        }
        if (car.speed >= SPEED_KUDOS_SPEED) {
            kudos = kudosFill(kudos, PgrManeuver.SPEED, FIXED_TIME_STEP * 0.25f)
        }
        if (racer.draft > 0.35f && car.speed >= DRAFT_MIN_SPEED && car.throttle >= 0.5f) {
            kudos = kudosFill(kudos, PgrManeuver.DRAFT, FIXED_TIME_STEP * 0.25f)
        }
        val curv = track.sampleAt(racer.progress).curvature
        if (abs(projection.lateral) < track.width * 0.12f && abs(curv) > 0.008f) {
            kudos = kudosFill(kudos, PgrManeuver.RACELINE, FIXED_TIME_STEP * 0.5f)
        }

        var spin = racer.spinRadians
        if (abs(car.yawRate) > 1.6f && car.speed > 12f) {
            spin += car.yawRate * FIXED_TIME_STEP
        } else {
            spin *= 0.88f
        }
        if (abs(spin) >= 2f * PI.toFloat()) {
            kudos = kudosAward(kudos, PgrManeuver.SPIN360)
            spin = 0f
        }

        return racer.copy(kudos = kudos, spinRadians = spin, draft = racer.draft)
    }

    /** Sort racers: finishers by time, then by race distance. */
    fun orderedIndices(race: PgrRaceState): List<Int> =
        race.racers.indices.sortedWith { a, b ->
            val ra = race.racers[a]
            val rb = race.racers[b]
            when {
                ra.finished && rb.finished -> {
                    val ta = ra.finishTicks ?: Long.MAX_VALUE
                    val tb = rb.finishTicks ?: Long.MAX_VALUE
                    when {
                        ta != tb -> ta.compareTo(tb)
                        else -> a.compareTo(b)
                    }
                }

                ra.finished != rb.finished -> if (ra.finished) -1 else 1
                else -> {
                    val p = rb.totalProgress.compareTo(ra.totalProgress)
                    if (p != 0) p else a.compareTo(b)
                }
            }
        }

    fun positionOf(race: PgrRaceState, index: Int): Int =
        orderedIndices(race).indexOf(index) + 1

    private fun updatePositions(racers: List<PgrRacer>, raceTicks: Long): List<PgrRacer> {
        val order = racers.indices.sortedWith { a, b ->
            val ra = racers[a]
            val rb = racers[b]
            when {
                ra.finished && rb.finished -> {
                    val ta = ra.finishTicks ?: Long.MAX_VALUE
                    val tb = rb.finishTicks ?: Long.MAX_VALUE
                    when {
                        ta != tb -> ta.compareTo(tb)
                        else -> a.compareTo(b)
                    }
                }

                ra.finished != rb.finished -> if (ra.finished) -1 else 1
                else -> {
                    val p = rb.totalProgress.compareTo(ra.totalProgress)
                    if (p != 0) p else a.compareTo(b)
                }
            }
        }
        val positions = IntArray(racers.size)
        order.forEachIndexed { place, index -> positions[index] = place + 1 }
        return racers.map { racer ->
            val newPos = positions[racer.index]
            var kudos = racer.kudos
            var overtakes = racer.overtakes
            if (newPos < racer.position && !racer.finished && !racer.eliminated && racer.car.speed > 3f) {
                overtakes += 1
                kudos = if (raceTicks <= racer.draftUntilTicks) {
                    kudosAward(kudos, PgrManeuver.SLINGSHOT)
                } else {
                    kudosAward(kudos, PgrManeuver.OVERTAKE)
                }
            }
            racer.copy(position = newPos, lastPosition = racer.position, overtakes = overtakes, kudos = kudos)
        }
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------

    const val AI_LOOKAHEAD_BASE = 7f
    const val AI_LOOKAHEAD_SPEED = 0.42f
    const val AI_LAT_ACCEL = 6.4f
    const val AI_CORNER_MARGIN = 0.92f
    const val AI_THROTTLE_BAND = 1.5f
    const val AI_BRAKE_BAND = 1.2f
    const val AI_STEER_JITTER = 0.045f
    const val AI_RUBBER_MIN = 0.90f
    const val AI_RUBBER_MAX = 1.12f

    /** Deterministic rubber-band factor, bounded above and below. */
    fun rubberband(race: PgrRaceState, racer: PgrRacer): Float {
        val leader = race.racers.maxByOrNull { it.totalProgress } ?: return 1f
        val gap = leader.totalProgress - racer.totalProgress
        return clamp(1f + gap * 0.0007f, AI_RUBBER_MIN, AI_RUBBER_MAX)
    }

    /**
     * Pure-pursuit line follower. Steering aims at a look-ahead point on the
     * smoothed racing line, target speed comes from the tightest curvature in
     * the window, and rubber-banding is bounded.
     */
    fun aiInput(race: PgrRaceState, index: Int, seed: Int = DEFAULT_SEED): PgrInput {
        val racer = race.racers.getOrNull(index) ?: return PgrInput(brake = 0.5f)
        if (racer.finished || racer.eliminated) return PgrInput(brake = 0.5f)
        val track = PgrTracks.byId(race.trackId)
        val def = PgrCars.byId(racer.carId)
        val speed = racer.car.speed
        val lookahead = AI_LOOKAHEAD_BASE + speed * AI_LOOKAHEAD_SPEED
        val targetDistance = racer.totalProgress + lookahead
        val lineOffset = track.racingOffsetAt(targetDistance) + racer.aiOffset
        val target = track.positionAt(targetDistance, lineOffset)
        val dx = target.x - racer.car.position.x
        val dz = target.z - racer.car.position.z
        val desired = atan2(dx, dz)
        val delta = wrapPi(desired - racer.car.heading)
        val fullLock = def.maxSteerRad * 1.5f
        var steer = clamp(delta / fullLock, -1f, 1f)
        steer = clamp(steer + noise(seed, index) * AI_STEER_JITTER, -1f, 1f)

        val curvature = track.maxCurvature(targetDistance, lookahead * 1.6f)
        val cornerSpeed = if (curvature > 1e-4f) sqrt(AI_LAT_ACCEL / curvature) else 999f
        val top = redlineSpeed(def)
        val rubber = rubberband(race, racer)
        var targetSpeed = min(top, cornerSpeed * AI_CORNER_MARGIN) * racer.aiSkill * rubber
        targetSpeed = clamp(targetSpeed, 0f, top * 1.12f)

        val throttle = when {
            speed < targetSpeed - AI_THROTTLE_BAND -> 1f
            speed < targetSpeed -> 0.6f
            else -> 0f
        }
        val brake = when {
            speed > targetSpeed + AI_BRAKE_BAND -> 1f
            speed > targetSpeed -> 0.35f
            else -> 0f
        }
        return PgrInput(steer = steer, throttle = throttle, brake = brake)
    }

    // ------------------------------------------------------------------
    // Results, progression and garage
    // ------------------------------------------------------------------

    data class PgrEventResult(
        val position: Int = MAX_PLAYERS,
        val finished: Boolean = false,
        val timeMs: Long = 0L,
        val kudos: Int = 0,
        val topSpeed: Float = 0f,
        val overtakes: Int = 0,
        val gatesPassed: Int = 0,
        val eliminated: Boolean = false,
        val rivalPosition: Int = MAX_PLAYERS,
        val cleanRace: Boolean = true,
        val bestLapMs: Int? = null,
    )

    data class PgrEventOutcome(
        val won: Boolean,
        val medal: PgrMedal,
        val credits: Int,
        val kudos: Int,
        val message: String,
    )

    fun raceResult(race: PgrRaceState): PgrEventResult {
        val player = race.racers.firstOrNull { it.isPlayer } ?: return PgrEventResult()
        val rival = race.racers.firstOrNull { !it.isPlayer }
        return PgrEventResult(
            position = player.position,
            finished = player.finished,
            timeMs = player.finishTicks?.let { ticksToMs(it) } ?: ticksToMs(race.raceTicks),
            kudos = player.kudos.total,
            topSpeed = player.topSpeed,
            overtakes = player.overtakes,
            gatesPassed = player.gatesPassed,
            eliminated = player.eliminated,
            rivalPosition = rival?.position ?: MAX_PLAYERS,
            cleanRace = player.kudos.cleanRace,
            bestLapMs = player.bestLapTicks?.let { ticksToMs(it).toInt() },
        )
    }

    /** Evaluate an event's win condition and medal from a race result. */
    fun evaluateEvent(event: PgrEventDef, result: PgrEventResult): PgrEventOutcome {
        val won = when (event.type) {
            PgrEventType.BREAKTHROUGH -> result.finished && result.timeMs <= event.limitMs
            PgrEventType.CONE_SPRINT -> result.gatesPassed >= event.gateCount && result.timeMs <= event.limitMs
            PgrEventType.ELIMINATOR -> !result.eliminated && result.finished
            PgrEventType.KUDOS_CHALLENGE -> result.kudos >= event.targetKudos
            PgrEventType.ONE_ON_ONE -> result.finished && result.position < result.rivalPosition
            PgrEventType.OVERTAKE -> result.overtakes >= event.targetOvertakes
            PgrEventType.SPEED_CHALLENGE -> result.topSpeed >= event.targetSpeed
            PgrEventType.TIME_VS_KUDOS ->
                result.finished && result.timeMs <= event.limitMs && result.kudos >= event.targetKudos
            PgrEventType.STREET_RACE -> result.position == 1
        }
        val medal = if (!won) {
            PgrMedal.NONE
        } else {
            when (event.type) {
                PgrEventType.BREAKTHROUGH, PgrEventType.CONE_SPRINT, PgrEventType.TIME_VS_KUDOS -> {
                    val ratio = if (event.parMs > 0L) result.timeMs.toFloat() / event.parMs else 1f
                    when {
                        ratio <= 0.88f -> PgrMedal.GOLD
                        ratio <= 1f -> PgrMedal.SILVER
                        else -> PgrMedal.BRONZE
                    }
                }

                PgrEventType.KUDOS_CHALLENGE -> {
                    val ratio = result.kudos.toFloat() / event.targetKudos.coerceAtLeast(1)
                    when {
                        ratio >= 1.3f -> PgrMedal.GOLD
                        ratio >= 1.12f -> PgrMedal.SILVER
                        else -> PgrMedal.BRONZE
                    }
                }

                PgrEventType.SPEED_CHALLENGE -> {
                    val ratio = result.topSpeed / event.targetSpeed.coerceAtLeast(1f)
                    when {
                        ratio >= 1.06f -> PgrMedal.GOLD
                        ratio >= 1.02f -> PgrMedal.SILVER
                        else -> PgrMedal.BRONZE
                    }
                }

                PgrEventType.STREET_RACE, PgrEventType.ONE_ON_ONE -> {
                    val beatPar = event.parMs <= 0L || result.timeMs <= event.parMs
                    when {
                        result.position == 1 && beatPar -> PgrMedal.GOLD
                        result.position == 1 -> PgrMedal.SILVER
                        else -> PgrMedal.BRONZE
                    }
                }

                PgrEventType.OVERTAKE, PgrEventType.ELIMINATOR -> when {
                    result.position == 1 -> PgrMedal.GOLD
                    result.position <= 2 -> PgrMedal.SILVER
                    else -> PgrMedal.BRONZE
                }
            }
        }
        val credits = event.difficulty.creditReward * medal.multiplier
        val message = when {
            !won -> "event not cleared"
            medal == PgrMedal.GOLD -> "gold — ${credits} credits"
            medal == PgrMedal.SILVER -> "silver — ${credits} credits"
            else -> "bronze — ${credits} credits"
        }
        return PgrEventOutcome(won = won, medal = medal, credits = credits, kudos = result.kudos, message = message)
    }

    data class PgrProfile(
        val credits: Int = 1500,
        val kudosBank: Int = 0,
        val cars: Set<String> = setOf(PgrCars.starter.id),
        val paints: Map<String, Int> = emptyMap(),
        val medals: Map<String, PgrMedal> = emptyMap(),
        val bestLaps: Map<String, Long> = emptyMap(),
        val bestTimes: Map<String, Long> = emptyMap(),
        val bestKudos: Map<String, Int> = emptyMap(),
        val eventsPlayed: Int = 0,
    ) {
        val medalCount: Int get() = medals.values.count { it.rank > 0 }
        fun selectedCar(): String = cars.firstOrNull() ?: PgrCars.starter.id
    }

    const val START_CREDITS = 1500

    fun canEnterEvent(profile: PgrProfile, event: PgrEventDef): Boolean =
        event.prerequisites.all { (profile.medals[it] ?: PgrMedal.NONE).rank > 0 }

    data class PgrPurchaseResult(val profile: PgrProfile, val error: String? = null) {
        val ok: Boolean get() = error == null
    }

    /** Purchase rules: credits, kudos and career-medal gates. */
    fun purchase(profile: PgrProfile, carId: String): PgrPurchaseResult {
        val car = PgrCars.byId(carId)
        if (car.creditCost > 0 && profile.cars.contains(car.id)) return PgrPurchaseResult(profile, "owned")
        if (car.creditCost > 0 && profile.credits < car.creditCost) return PgrPurchaseResult(profile, "credits")
        if (profile.medalCount < car.medalsRequired) return PgrPurchaseResult(profile, "locked")
        if (car.kudosCost > profile.kudosBank) return PgrPurchaseResult(profile, "kudos")
        if (profile.cars.contains(car.id)) return PgrPurchaseResult(profile, "owned")
        return PgrPurchaseResult(
            profile.copy(
                credits = profile.credits - car.creditCost,
                cars = profile.cars + car.id,
            ),
        )
    }

    /** Fold a completed race into the profile and return the event outcome. */
    fun applyOutcome(
        profile: PgrProfile,
        event: PgrEventDef?,
        result: PgrEventResult,
        trackId: String? = null,
    ): Pair<PgrProfile, PgrEventOutcome?> {
        val recordTrack = event?.trackId ?: trackId ?: "quick"
        var next = profile.copy(
            kudosBank = profile.kudosBank + result.kudos,
            eventsPlayed = profile.eventsPlayed + (if (event != null) 1 else 0),
        )
        val bestLap = result.bestLapMs?.toLong()
        if (bestLap != null) {
            val prev = next.bestLaps[recordTrack] ?: Long.MAX_VALUE
            if (bestLap < prev) next = next.copy(bestLaps = next.bestLaps + (recordTrack to bestLap))
        }
        if (result.finished && result.timeMs > 0L) {
            val prev = next.bestTimes[recordTrack] ?: Long.MAX_VALUE
            if (result.timeMs < prev) next = next.copy(bestTimes = next.bestTimes + (recordTrack to result.timeMs))
        }
        val prevKudos = next.bestKudos[recordTrack] ?: 0
        if (result.kudos > prevKudos) {
            next = next.copy(bestKudos = next.bestKudos + (recordTrack to result.kudos))
        }
        if (event == null) return next to null
        val outcome = evaluateEvent(event, result)
        if (outcome.won) {
            val existing = next.medals[event.id] ?: PgrMedal.NONE
            if (outcome.medal.rank > existing.rank) {
                next = next.copy(medals = next.medals + (event.id to outcome.medal))
            }
            next = next.copy(credits = next.credits + outcome.credits)
            val reward = event.rewardCarId
            if (reward != null && !next.cars.contains(reward)) {
                next = next.copy(cars = next.cars + reward)
            }
        }
        return next to outcome
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private const val PROFILE_VERSION = "pgr1"

    fun encodeProfile(profile: PgrProfile): String {
        val cars = profile.cars.joinToString(",")
        val paints = profile.paints.entries.joinToString(",") { "${it.key}:${it.value}" }
        val medals = profile.medals.entries.joinToString(",") { "${it.key}:${it.value.name}" }
        val laps = profile.bestLaps.entries.joinToString(",") { "${it.key}:${it.value}" }
        val times = profile.bestTimes.entries.joinToString(",") { "${it.key}:${it.value}" }
        val kudos = profile.bestKudos.entries.joinToString(",") { "${it.key}:${it.value}" }
        return listOf(
            PROFILE_VERSION,
            profile.credits.toString(),
            profile.kudosBank.toString(),
            cars,
            paints,
            medals,
            laps,
            times,
            kudos,
            profile.eventsPlayed.toString(),
        ).joinToString("|")
    }

    fun decodeProfile(blob: String?): PgrProfile? {
        if (blob.isNullOrBlank()) return null
        return runCatching {
            val parts = blob.split("|")
            if (parts.size < 10 || parts[0] != PROFILE_VERSION) return null
            val cars = parts[3].split(",").filter { it.isNotBlank() }.toSet()
            val paints = parts[4].split(",").filter { it.contains(":") }.associate {
                val bits = it.split(":")
                bits[0] to bits[1].toInt()
            }
            val medals = parts[5].split(",").filter { it.contains(":") }.mapNotNull {
                val bits = it.split(":")
                val medal = PgrMedal.entries.firstOrNull { m -> m.name == bits[1] } ?: PgrMedal.NONE
                bits[0] to medal
            }.toMap()
            val laps = parts[6].split(",").filter { it.contains(":") }.associate {
                val bits = it.split(":")
                bits[0] to bits[1].toLong()
            }
            val times = parts[7].split(",").filter { it.contains(":") }.associate {
                val bits = it.split(":")
                bits[0] to bits[1].toLong()
            }
            val kudos = parts[8].split(",").filter { it.contains(":") }.associate {
                val bits = it.split(":")
                bits[0] to bits[1].toInt()
            }
            PgrProfile(
                credits = parts[1].toInt(),
                kudosBank = parts[2].toInt(),
                cars = if (cars.isEmpty()) setOf(PgrCars.starter.id) else cars,
                paints = paints,
                medals = medals,
                bestLaps = laps,
                bestTimes = times,
                bestKudos = kudos,
                eventsPlayed = parts[9].toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // HUD helpers
    // ------------------------------------------------------------------

    /** Four engine bands (idle/low/mid/high) for audio crossfades and HUD. */
    fun engineBand(filteredRpm: Float, def: PgrCarDef): Int = when {
        filteredRpm < def.lowRpm * 1.25f -> 0
        filteredRpm < (def.lowRpm + def.highRpm) * 0.5f -> 1
        filteredRpm < def.highRpm * 0.88f -> 2
        else -> 3
    }

    fun speedKph(speed: Float): Int = (speed * 3.6f).roundToInt()

    fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val minutes = total / 60L
        val seconds = total % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    fun formatLap(ms: Long?): String = if (ms == null) "—" else {
        val m = ms / 60_000L
        val s = (ms % 60_000L) / 1000L
        val cs = (ms % 1000L) / 10L
        "%d:%02d.%02d".format(m, s, cs)
    }

    /** Display counter for finished races: "1st", "2nd", "3rd", "4th". */
    fun ordinal(position: Int): String = when (position) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${position}th"
    }
}
