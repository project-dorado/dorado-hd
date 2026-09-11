package com.heretek.dorado_hd.ui.apps.games

import com.heretek.dorado_hd.analysis.AudioFeatures
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Clean-room re-derivation of Audiosurf Tilt's ride simulation. Behaviour comes
 * from docs/apps/audiosurf-tilt.md: a three-lane downhill course built from the
 * song profile, coloured blocks that build a chain, grey blocks that break it,
 * speed and jump elements, per-difficulty medal thresholds and a scoreless
 * Visualizer ride. No Microsoft code, strings or data are involved; every
 * course is original procedural content.
 *
 * The engine is pure Kotlin (no Android types) so the whole simulation is
 * deterministic and unit-testable. [AudiosurfEngine.step] advances a ride to an
 * explicit course position, which lets the app drive it from either an internal
 * clock or the shared playback position.
 */
enum class AudiosurfMode { NORMAL, HARD, VISUALIZER }

/** What a generated course element is. */
enum class CourseElementKind { COLOR_BLOCK, GREY_BLOCK, SPEED_UP, SLOW_DOWN, JUMP }

enum class RideStatus { READY, RIDING, FINISHED }

/** Events emitted by one [AudiosurfEngine.step] so the app can fire SFX/HUD. */
enum class RideEventKind { COLLECT, MISS, STONE, CHAIN, JUMP, SPEED_UP, SLOW_DOWN, FINISH, PERFECT }

enum class AudiosurfMedal { NONE, BRONZE, SILVER, GOLD }

/**
 * The song parameters that shape a course. Mapped from the analysis package's
 * [AudioFeatures] when available, or synthesized deterministically when the
 * library has no analysis for the track.
 */
data class TrackProfile(
    val trackId: Long,
    val bpm: Double,
    val energy: Double,
    val valence: Double,
    val centroid: Double,
    val durationMs: Long,
) {
    companion object {
        /**
         * Deterministic fallback profile: a stable "song" for any seed so a
         * ride is always possible without analysis or a media library.
         */
        fun synthetic(
            seed: Int,
            durationMs: Long = AudiosurfEngine.SYNTHETIC_DURATION_MS,
            trackId: Long = -1L,
        ): TrackProfile {
            val h = mix(seed, 0x5EED)
            val bpm = 88.0 + ((h ushr 3) and 0x7F) / 127.0 * 60.0
            val energy = 0.35 + ((h ushr 11) and 0x3F) / 63.0 * 0.6
            val valence = ((h ushr 17) and 0x3F) / 63.0
            val centroid = 0.20 + ((h ushr 23) and 0x1F) / 31.0 * 0.6
            return TrackProfile(
                trackId = trackId,
                bpm = bpm,
                energy = energy,
                valence = valence,
                centroid = centroid,
                durationMs = durationMs.coerceAtLeast(AudiosurfEngine.MIN_DURATION_MS),
            )
        }
    }
}

/** One generated course element, positioned in song time and lane. */
data class CourseElement(
    val index: Int,
    val beat: Int,
    val positionMs: Long,
    val lane: Int,
    val kind: CourseElementKind,
)

/**
 * A generated ride. The same [profile], [mode] and [seed] always produce the
 * same elements (spec: "the same song always produces the same track").
 */
data class RideCourse(
    val profile: TrackProfile,
    val mode: AudiosurfMode,
    val seed: Int,
    val elements: List<CourseElement>,
    val coloredTotal: Int,
    val greyTotal: Int,
    val durationMs: Long,
) {
    /** Elements crossed when moving from [fromExclusiveMs] to [toInclusiveMs]. */
    fun elementsInRange(fromExclusiveMs: Long, toInclusiveMs: Long): List<CourseElement> {
        if (toInclusiveMs <= fromExclusiveMs) return emptyList()
        var lo = 0
        var hi = elements.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (elements[mid].positionMs <= fromExclusiveMs) lo = mid + 1 else hi = mid
        }
        if (lo >= elements.size) return emptyList()
        var end = lo
        while (end < elements.size && elements[end].positionMs <= toInclusiveMs) end++
        return elements.subList(lo, end)
    }
}

/** One discrete thing that happened during a step. */
data class RideEvent(
    val kind: RideEventKind,
    val positionMs: Long,
    val lane: Int = -1,
    val chain: Int = 0,
    val points: Int = 0,
    val label: String? = null,
)

/** Full ride state. [events] holds only the events from the most recent step. */
data class RideState(
    val course: RideCourse,
    val trackPositionMs: Long = 0L,
    val lane: Int = 1,
    val lanePosition: Float = 1f,
    val score: Int = 0,
    val chain: Int = 0,
    val longestChain: Int = 0,
    val collected: Int = 0,
    val misses: Int = 0,
    val stones: Int = 0,
    val jumpMsLeft: Long = 0L,
    val speedScale: Float = 1f,
    val speedMsLeft: Long = 0L,
    val status: RideStatus = RideStatus.READY,
    val events: List<RideEvent> = emptyList(),
) {
    val mode: AudiosurfMode get() = course.mode
    val riding: Boolean get() = status == RideStatus.RIDING
    val jumping: Boolean get() = jumpMsLeft > 0L

    /** Normalized hover arc height 0..1 over the 1.5 s jump. */
    val jumpHeight: Float
        get() {
            if (jumpMsLeft <= 0L) return 0f
            val t = (1f - jumpMsLeft.toFloat() / AudiosurfEngine.JUMP_TIME_MS.toFloat()).coerceIn(0f, 1f)
            return AudiosurfEngine.JUMP_MAX_HEIGHT * sin(PI.toFloat() * t)
        }
}

object AudiosurfEngine {
    const val LANE_COUNT = 3
    const val COLOR_POINT_GAIN_NORMAL = 1
    const val COLOR_POINT_GAIN_HARD = 2

    // Medal fractions (docs/apps/audiosurf-tilt.md §3).
    const val MEDAL_BRONZE = 0.35
    const val MEDAL_SILVER = 0.70
    const val MEDAL_GOLD_NORMAL = 0.94
    const val MEDAL_GOLD_HARD = 0.975

    // Racer jump (mined: 1.5 s, max height 1).
    const val JUMP_TIME_MS = 1_500L
    const val JUMP_MAX_HEIGHT = 1f

    // Speed elements.
    const val SPEED_UP_SCALE = 1.25f
    const val SLOW_DOWN_SCALE = 0.8f
    const val SPEED_EFFECT_MS = 4_000L

    // Accelerometer dead zone (mined: 0.05 + 0.3 * (1 - sensitivity)).
    const val TILT_DEAD_ZONE_BASE = 0.05
    const val TILT_DEAD_ZONE_RANGE = 0.3
    const val DEGREES_PER_G = 57.29577951308232

    const val MIN_DURATION_MS = 8_000L
    const val SYNTHETIC_DURATION_MS = 180_000L
    const val LANE_SNAP_PER_MS = 0.02f

    /** Grace period before a player-synced ride falls back to the wall clock. */
    const val STALL_TIMEOUT_MS = 1_500L

    /**
     * Target position for a player-synced step. While [playerPositionMs] is
     * advancing it wins; once the player has been stalled for
     * [STALL_TIMEOUT_MS] the ride advances by the wall clock at
     * [speedScale], so a dead or paused player can never soft-lock the ride
     * before FINISH (A-27).
     */
    fun syncedTarget(
        currentPositionMs: Long,
        playerPositionMs: Long,
        dtMs: Long,
        speedScale: Float,
        stalledMs: Long,
    ): Long {
        if (playerPositionMs > currentPositionMs) return playerPositionMs
        if (stalledMs < STALL_TIMEOUT_MS) return currentPositionMs
        return currentPositionMs + (dtMs * speedScale).toLong()
    }

    /**
     * Maps analysis features to a ride profile. [features] is null when no
     * analysis exists (empty library, decode failure); the deterministic
     * synthetic profile is the fallback.
     */
    fun profileFor(
        features: AudioFeatures?,
        durationMs: Long,
        seed: Int = 0,
        trackId: Long = -1L,
    ): TrackProfile = if (features != null) {
        TrackProfile(
            trackId = trackId,
            bpm = features.bpm.coerceIn(60.0, 200.0),
            energy = features.energy.coerceIn(0.0, 1.0),
            valence = features.valence.coerceIn(0.0, 1.0),
            centroid = features.spectralCentroid.coerceIn(0.0, 1.0),
            durationMs = durationMs.coerceAtLeast(MIN_DURATION_MS),
        )
    } else {
        TrackProfile.synthetic(seed, durationMs, trackId)
    }

    fun pointGain(mode: AudiosurfMode): Int = when (mode) {
        AudiosurfMode.HARD -> COLOR_POINT_GAIN_HARD
        AudiosurfMode.NORMAL -> COLOR_POINT_GAIN_NORMAL
        AudiosurfMode.VISUALIZER -> 0
    }

    fun goldThreshold(mode: AudiosurfMode): Double =
        if (mode == AudiosurfMode.HARD) MEDAL_GOLD_HARD else MEDAL_GOLD_NORMAL

    /**
     * Builds a deterministic lane course. Blocks land on a beat grid derived
     * from the profile BPM; Normal spawns a block every other beat, Hard on
     * every beat with a higher grey chance, Visualizer stays sparse. A per-beat
     * seeded generator keeps shared beats identical across modes, so Hard is a
     * strict superset of Normal's obstacle pressure.
     */
    fun buildCourse(profile: TrackProfile, mode: AudiosurfMode, seed: Int = 0): RideCourse {
        val bpm = profile.bpm.coerceIn(60.0, 200.0)
        val beatMs = 60_000.0 / bpm
        val duration = profile.durationMs.coerceAtLeast(MIN_DURATION_MS)
        val totalBeats = (duration / beatMs).toInt().coerceAtLeast(1)
        val blockEvery = if (mode == AudiosurfMode.HARD) 1 else 2
        val greyChance = when (mode) {
            AudiosurfMode.NORMAL -> 0.16 + 0.14 * (1.0 - profile.energy)
            AudiosurfMode.HARD -> 0.28 + 0.18 * (1.0 - profile.energy)
            AudiosurfMode.VISUALIZER -> 0.10 + 0.10 * (1.0 - profile.energy)
        }
        val elements = ArrayList<CourseElement>()
        var index = 0
        for (beat in 1 until totalBeats) {
            val position = (beat * beatMs).toLong()
            val beatSeed = mix(mix(seed, 0x51ED), beat)
            if (beat % 32 == 0) {
                elements += CourseElement(index++, beat, position, 1, CourseElementKind.JUMP)
                continue
            }
            if (beat % 16 == 0) {
                val rng = Lcg(beatSeed)
                val kind = if (rng.nextDouble() < 0.5) {
                    CourseElementKind.SPEED_UP
                } else {
                    CourseElementKind.SLOW_DOWN
                }
                elements += CourseElement(index++, beat, position, 1, kind)
                continue
            }
            if (beat % blockEvery != 0) continue
            val rng = Lcg(beatSeed)
            val lane = rng.nextInt(LANE_COUNT)
            val kind = if (rng.nextDouble() < greyChance) {
                CourseElementKind.GREY_BLOCK
            } else {
                CourseElementKind.COLOR_BLOCK
            }
            elements += CourseElement(index++, beat, position, lane, kind)
        }
        return RideCourse(
            profile = profile,
            mode = mode,
            seed = seed,
            elements = elements,
            coloredTotal = elements.count { it.kind == CourseElementKind.COLOR_BLOCK },
            greyTotal = elements.count { it.kind == CourseElementKind.GREY_BLOCK },
            durationMs = duration,
        )
    }

    fun newRide(course: RideCourse): RideState = RideState(course = course)

    fun startRide(state: RideState): RideState =
        if (state.status == RideStatus.READY) state.copy(status = RideStatus.RIDING) else state

    /**
     * Advance the ride to [trackPositionMs]. Positions must be monotonic: a
     * backwards or equal position is ignored (events cleared), so a paused or
     * seeked player can never double-score a section. Collisions are resolved
     * at crossing time against the lane held when the step began.
     */
    fun step(state: RideState, trackPositionMs: Long): RideState {
        if (state.status == RideStatus.FINISHED) return state.copy(events = emptyList())
        val target = trackPositionMs.coerceAtLeast(0L)
        if (target <= state.trackPositionMs) return state.copy(events = emptyList())
        val duration = state.course.durationMs
        val clamped = target.coerceAtMost(duration)
        val delta = clamped - state.trackPositionMs
        val events = ArrayList<RideEvent>()
        val visualizer = state.mode == AudiosurfMode.VISUALIZER
        val gain = pointGain(state.mode)
        var score = state.score
        var chain = state.chain
        var longest = state.longestChain
        var collected = state.collected
        var misses = state.misses
        var stones = state.stones
        var jumpMsLeft = (state.jumpMsLeft - delta).coerceAtLeast(0L)
        var speedMsLeft = (state.speedMsLeft - delta).coerceAtLeast(0L)
        var speedScale = if (speedMsLeft <= 0L) 1f else state.speedScale

        for (element in state.course.elementsInRange(state.trackPositionMs, clamped)) {
            when (element.kind) {
                CourseElementKind.COLOR_BLOCK -> {
                    if (element.lane == state.lane) {
                        if (!visualizer) {
                            score += gain
                            collected += 1
                            chain += 1
                            longest = maxOf(longest, chain)
                        }
                        events += RideEvent(
                            RideEventKind.COLLECT,
                            element.positionMs,
                            element.lane,
                            chain = if (visualizer) 0 else chain,
                            points = if (visualizer) 0 else gain,
                        )
                        if (!visualizer) {
                            chainRewardLabel(chain)?.let { label ->
                                events += RideEvent(
                                    RideEventKind.CHAIN,
                                    element.positionMs,
                                    element.lane,
                                    chain,
                                    0,
                                    label,
                                )
                            }
                        }
                    } else if (!visualizer) {
                        chain = 0
                        misses += 1
                        events += RideEvent(RideEventKind.MISS, element.positionMs, element.lane)
                    }
                }

                CourseElementKind.GREY_BLOCK -> {
                    if (element.lane == state.lane) {
                        events += RideEvent(RideEventKind.STONE, element.positionMs, element.lane)
                        if (!visualizer) {
                            chain = 0
                            stones += 1
                        }
                    }
                }

                CourseElementKind.JUMP -> {
                    jumpMsLeft = JUMP_TIME_MS
                    events += RideEvent(RideEventKind.JUMP, element.positionMs, element.lane)
                }

                CourseElementKind.SPEED_UP -> {
                    speedScale = SPEED_UP_SCALE
                    speedMsLeft = SPEED_EFFECT_MS
                    events += RideEvent(RideEventKind.SPEED_UP, element.positionMs, element.lane)
                }

                CourseElementKind.SLOW_DOWN -> {
                    speedScale = SLOW_DOWN_SCALE
                    speedMsLeft = SPEED_EFFECT_MS
                    events += RideEvent(RideEventKind.SLOW_DOWN, element.positionMs, element.lane)
                }
            }
        }

        val finished = clamped >= duration
        val status = when {
            finished -> RideStatus.FINISHED
            state.status == RideStatus.READY -> RideStatus.RIDING
            else -> state.status
        }
        if (finished) {
            events += RideEvent(RideEventKind.FINISH, duration, chain = chain, points = score)
            if (!visualizer && misses == 0 && stones == 0 && collected == state.course.coloredTotal) {
                events += RideEvent(
                    RideEventKind.PERFECT,
                    duration,
                    chain = chain,
                    points = score,
                    label = "perfect",
                )
            }
        }

        val laneTarget = state.lane.toFloat()
        val snap = LANE_SNAP_PER_MS * delta
        val lanePosition = when {
            state.lanePosition < laneTarget -> (state.lanePosition + snap).coerceAtMost(laneTarget)
            state.lanePosition > laneTarget -> (state.lanePosition - snap).coerceAtLeast(laneTarget)
            else -> state.lanePosition
        }

        return state.copy(
            trackPositionMs = clamped,
            lanePosition = lanePosition,
            score = score,
            chain = chain,
            longestChain = longest,
            collected = collected,
            misses = misses,
            stones = stones,
            jumpMsLeft = jumpMsLeft,
            speedScale = speedScale,
            speedMsLeft = speedMsLeft,
            status = status,
            events = events,
        )
    }

    /** One lane step in the given direction, clamped to the three lanes. */
    fun steer(state: RideState, laneDelta: Int): RideState {
        if (laneDelta == 0) return state
        val lane = (state.lane + laneDelta).coerceIn(0, LANE_COUNT - 1)
        return if (lane == state.lane) state else state.copy(lane = lane)
    }

    fun setLane(state: RideState, lane: Int): RideState {
        val clamped = lane.coerceIn(0, LANE_COUNT - 1)
        return if (clamped == state.lane) state else state.copy(lane = clamped)
    }

    /** DirectTouch: left / centre / right third of the screen width. */
    fun absoluteTouchLane(fractionX: Float): Int =
        (fractionX.coerceIn(0f, 0.999f) * LANE_COUNT).toInt().coerceIn(0, LANE_COUNT - 1)

    /** Mined tilt dead zone in g units: 0.05 + 0.3 * (1 - sensitivity). */
    fun tiltDeadZone(sensitivity: Double): Double =
        TILT_DEAD_ZONE_BASE + TILT_DEAD_ZONE_RANGE * (1.0 - sensitivity.coerceIn(0.0, 1.0))

    /** -1/0/+1 lane intent once the roll exceeds the sensitivity dead zone. */
    fun tiltDirection(rollDeg: Double, sensitivity: Double): Int {
        val g = rollDeg / DEGREES_PER_G
        val deadZone = tiltDeadZone(sensitivity)
        return when {
            g > deadZone -> 1
            g < -deadZone -> -1
            else -> 0
        }
    }

    /** 0..1 magnitude past the dead zone, used for camera roll and strafe feel. */
    fun tiltMagnitude(rollDeg: Double, sensitivity: Double): Double {
        val g = abs(rollDeg / DEGREES_PER_G)
        val deadZone = tiltDeadZone(sensitivity)
        if (g <= deadZone) return 0.0
        return ((g - deadZone) / (1.0 - deadZone)).coerceIn(0.0, 1.0)
    }

    /** Chain call-outs fire at 5, 11, 25, 50 and every 100 after that. */
    fun chainRewardLabel(chain: Int): String? = when {
        chain == 5 || chain == 11 || chain == 25 || chain == 50 -> "chain $chain"
        chain >= 100 && chain % 100 == 0 -> "chain $chain"
        else -> null
    }

    fun medalFor(collected: Int, coloredTotal: Int, mode: AudiosurfMode): AudiosurfMedal {
        if (mode == AudiosurfMode.VISUALIZER || coloredTotal <= 0) return AudiosurfMedal.NONE
        val fraction = collected.toDouble() / coloredTotal
        return when {
            fraction >= goldThreshold(mode) -> AudiosurfMedal.GOLD
            fraction >= MEDAL_SILVER -> AudiosurfMedal.SILVER
            fraction >= MEDAL_BRONZE -> AudiosurfMedal.BRONZE
            else -> AudiosurfMedal.NONE
        }
    }

    /** HUD label for the medal still in reach, or null in Visualizer. */
    fun nextMedalLabel(collected: Int, coloredTotal: Int, mode: AudiosurfMode): String? {
        if (mode == AudiosurfMode.VISUALIZER || coloredTotal <= 0) return null
        val fraction = collected.toDouble() / coloredTotal
        val gold = goldThreshold(mode)
        return when {
            fraction >= gold -> "gold secured"
            fraction >= MEDAL_SILVER -> "next gold ${(gold * 100.0).roundToInt()}%"
            fraction >= MEDAL_BRONZE -> "next silver 70%"
            else -> "next bronze 35%"
        }
    }

    /** Compact persistence blob for `graph.appState` (course is rebuilt). */
    fun encode(state: RideState): String {
        val p = state.course.profile
        return listOf(
            "as1",
            state.mode.ordinal.toString(),
            state.course.seed.toString(),
            p.bpm.toString(),
            p.energy.toString(),
            p.valence.toString(),
            p.centroid.toString(),
            p.durationMs.toString(),
            p.trackId.toString(),
            state.trackPositionMs.toString(),
            state.lane.toString(),
            state.lanePosition.toString(),
            state.score.toString(),
            state.chain.toString(),
            state.longestChain.toString(),
            state.collected.toString(),
            state.misses.toString(),
            state.stones.toString(),
            state.jumpMsLeft.toString(),
            state.speedScale.toString(),
            state.speedMsLeft.toString(),
            state.status.ordinal.toString(),
        ).joinToString(";")
    }

    fun decode(blob: String): RideState? {
        val parts = blob.split(";")
        if (parts.size < 22 || parts[0] != "as1") return null
        return try {
            val mode = AudiosurfMode.entries.getOrNull(parts[1].toInt()) ?: return null
            val profile = TrackProfile(
                trackId = parts[8].toLong(),
                bpm = parts[3].toDouble(),
                energy = parts[4].toDouble(),
                valence = parts[5].toDouble(),
                centroid = parts[6].toDouble(),
                durationMs = parts[7].toLong(),
            )
            RideState(
                course = buildCourse(profile, mode, parts[2].toInt()),
                trackPositionMs = parts[9].toLong(),
                lane = parts[10].toInt().coerceIn(0, LANE_COUNT - 1),
                lanePosition = parts[11].toFloat().coerceIn(0f, (LANE_COUNT - 1).toFloat()),
                score = parts[12].toInt(),
                chain = parts[13].toInt(),
                longestChain = parts[14].toInt(),
                collected = parts[15].toInt(),
                misses = parts[16].toInt(),
                stones = parts[17].toInt(),
                jumpMsLeft = parts[18].toLong(),
                speedScale = parts[19].toFloat(),
                speedMsLeft = parts[20].toLong(),
                status = RideStatus.entries.getOrNull(parts[21].toInt()) ?: RideStatus.READY,
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Small deterministic mixer so per-beat draws are stable across modes. */
private fun mix(a: Int, b: Int): Int {
    var h = a * 374_761_393 + b * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    return h xor (h ushr 16)
}

/** Minimal LCG: deterministic across platforms and Kotlin versions. */
private class Lcg(seed: Int) {
    private var state: Int = seed or 1

    fun nextInt(bound: Int): Int {
        state = state * 1_103_515_245 + 12_345
        return ((state ushr 16) and 0x7FFF) % bound.coerceAtLeast(1)
    }

    fun nextDouble(): Double {
        state = state * 1_103_515_245 + 12_345
        return ((state ushr 8) and 0xFFFFFF).toDouble() / 16_777_216.0
    }
}
