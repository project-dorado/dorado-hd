package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.PgrEngine
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrCarDef
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrEventResult
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrInput
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrMedal
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrManeuver
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrProfile
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrRacer
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrTrack
import com.heretek.dorado_hd.ui.apps.games.PgrEngine.PgrVec2
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the PGR engine: torque/gearbox math, grip and downforce
 * curves, fixed-step determinism, lap/checkpoint rules, position ordering,
 * kudos chain scoring, event and garage progression, spline sampling and the
 * bounded AI line follower.
 */
class PgrTest {

    private val car: PgrCarDef get() = PgrEngine.PgrCars.starter

    // ------------------------------------------------------------------
    // Clock and constants
    // ------------------------------------------------------------------

    @Test
    fun `simulation constants match the spec`() {
        assertEquals(30, PgrEngine.TICS_PER_SECOND)
        assertEquals(1f / 30f, PgrEngine.FIXED_TIME_STEP, 1e-7f)
        assertEquals(1000L, PgrEngine.ticksToMs(30L))
        assertEquals(3, PgrEngine.MAX_CHECKPOINTS)
        assertEquals(6, PgrEngine.MAX_LAPS)
        assertEquals(4, PgrEngine.MAX_PLAYERS)
    }

    // ------------------------------------------------------------------
    // Torque / gearbox
    // ------------------------------------------------------------------

    @Test
    fun `torque curve is full off idle and falls to min torque at redline`() {
        assertEquals(car.maxTorque, PgrEngine.torqueAt(car, 0f, 1f), 1e-3f)
        assertEquals(car.maxTorque, PgrEngine.torqueAt(car, car.lowRpm, 1f), 1e-3f)
        assertEquals(car.minTorque, PgrEngine.torqueAt(car, car.highRpm, 1f), 1e-3f)
        assertEquals(car.minTorque, PgrEngine.torqueAt(car, car.highRpm * 2f, 1f), 1e-3f)
        assertEquals(0f, PgrEngine.torqueAt(car, 4000f, 0f), 1e-4f)
        val mid = PgrEngine.torqueAt(car, car.highRpm * 0.5f, 1f)
        val high = PgrEngine.torqueAt(car, car.highRpm * 0.9f, 1f)
        assertTrue("torque should fall with rpm: $mid vs $high", mid > high)
    }

    @Test
    fun `wheel rpm tracks speed, gear ratio and direction`() {
        val third = PgrEngine.rpmFromWheelSpeed(car, 30f, 3)
        val doubled = PgrEngine.rpmFromWheelSpeed(car, 60f, 3)
        assertEquals(third * 2f, doubled, 1e-2f)
        assertTrue(PgrEngine.rpmFromWheelSpeed(car, 30f, 2) > PgrEngine.rpmFromWheelSpeed(car, 30f, 4))
        assertEquals(third, PgrEngine.rpmFromWheelSpeed(car, -30f, 3), 1e-3f)
        assertTrue(PgrEngine.redlineSpeed(car) > PgrEngine.speedKph(1f) / 3.6f)
    }

    @Test
    fun `gearbox upshifts under full throttle to top gear`() {
        var state = PgrEngine.newCarState(car, PgrVec2.ZERO, 0f)
        val gas = PgrInput(throttle = 1f)
        var maxGear = state.gear
        var maxSpeed = 0f
        repeat(30 * 60) {
            state = PgrEngine.carTick(car, state, gas)
            maxGear = maxOf(maxGear, state.gear)
            maxSpeed = maxOf(maxSpeed, state.speed)
        }
        assertEquals("should reach the tallest gear", car.gears.size, maxGear)
        assertTrue("should be quick: $maxSpeed m/s", maxSpeed > 40f)
        assertTrue(state.rpm <= car.highRpm * 1.1f + 0.5f)
        assertTrue(state.rpm >= PgrEngine.IDLE_RPM - 0.5f)
        // The engine is not driving backwards the whole time.
        assertTrue(state.forwardSpeed > 0f)
    }

    @Test
    fun `longitudinal resistance bounds the top speed`() {
        var state = PgrEngine.newCarState(car, PgrVec2.ZERO, 0f)
        val gas = PgrInput(throttle = 1f)
        repeat(30 * 90) { state = PgrEngine.carTick(car, state, gas) }
        val redline = PgrEngine.redlineSpeed(car)
        assertTrue("top ${state.speed} below redline speed $redline", state.speed <= redline + 1f)
        assertTrue("top ${state.speed} should be fast", state.speed > 55f)
    }

    // ------------------------------------------------------------------
    // Grip / downforce curves
    // ------------------------------------------------------------------

    @Test
    fun `side grip rises with handling and clamps`() {
        assertTrue(PgrEngine.sideGrip(1) < PgrEngine.sideGrip(10))
        assertEquals(2.14f, PgrEngine.sideGrip(1), 1e-3f)
        assertEquals(2.4f, PgrEngine.sideGrip(20), 1e-4f)
        assertTrue(PgrEngine.sideGrip(0) >= 2.1f)
        assertEquals(2f, PgrEngine.FORWARD_GRIP, 1e-6f)
    }

    @Test
    fun `downforce grows with speed squared and collapses sideways`() {
        val straight = PgrEngine.downforce(50f, 2.2f, 0f)
        val side = PgrEngine.downforce(50f, 2.2f, 1f)
        assertEquals(0.2f * straight, side, 1e-4f)
        assertEquals(4f * straight, PgrEngine.downforce(100f, 2.2f, 0f), 1e-3f)
        assertEquals(0f, PgrEngine.downforce(0f, 2.2f, 0f), 1e-6f)
        assertTrue(PgrEngine.downforce(40f, 2.4f, 0f) > PgrEngine.downforce(40f, 2.0f, 0f))
    }

    @Test
    fun `drag area interpolates between frontal and side`() {
        assertEquals(0.8f, PgrEngine.dragArea(0.8f, 2.6f, 0f), 1e-4f)
        assertEquals(2.6f, PgrEngine.dragArea(0.8f, 2.6f, 1f), 1e-4f)
        assertEquals(1.7f, PgrEngine.dragArea(0.8f, 2.6f, 0.5f), 1e-4f)
    }

    @Test
    fun `tilt steering is softened around zero and clamped`() {
        assertEquals(0f, PgrEngine.tiltToSteer(0f), 1e-6f)
        assertEquals(0f, PgrEngine.tiltToSteer(1.5f), 1e-6f)
        assertTrue(PgrEngine.tiltToSteer(8f) < 8f / 26f)
        assertEquals(1f, PgrEngine.tiltToSteer(90f, 1f), 1e-3f)
        assertTrue(PgrEngine.tiltToSteer(-30f) < 0f)
        assertEquals(PgrEngine.tiltToSteer(20f, 0.5f), PgrEngine.tiltToSteer(20f, 1f) * 0.5f, 1e-4f)
    }

    @Test
    fun `handbrake cuts drive and lets the car slide`() {
        var state = PgrEngine.newCarState(car, PgrVec2.ZERO, 0f)
        val gas = PgrInput(throttle = 1f)
        repeat(240) { state = PgrEngine.carTick(car, state, gas) }
        val speedBefore = state.speed
        assertTrue("needs speed to drift: $speedBefore", speedBefore > 20f)
        val drift = PgrInput(steer = 1f, throttle = 1f, handbrake = true)
        var maxLateral = 0f
        repeat(45) {
            state = PgrEngine.carTick(car, state, drift)
            maxLateral = maxOf(maxLateral, abs(state.lateralSpeed))
        }
        assertTrue("handbrake should slide: $maxLateral", maxLateral > 1f)
        assertTrue(state.speed < speedBefore)
        assertTrue(state.skidRear)
    }

    // ------------------------------------------------------------------
    // Determinism
    // ------------------------------------------------------------------

    @Test
    fun `fixed step races replay bit for bit`() {
        fun run(): PgrEngine.PgrRaceState {
            var state = PgrEngine.newRace(
                trackId = "kingsway-loop",
                playerCarId = "vela-gt",
                difficulty = PgrEngine.PgrDifficulty.MEDIUM,
                laps = 2,
                seed = 4242,
            )
            repeat(30 * 45) { state = PgrEngine.tickRace(state, null) }
            return state
        }

        val first = run()
        val second = run()
        assertEquals(first, second)
        assertTrue(first.raceTicks > 0L)
    }

    @Test
    fun `fixed step accumulator converts wall time to tics deterministically`() {
        val fresh = PgrEngine.newRace("neon-bay", "vela-gt", PgrEngine.PgrDifficulty.EASY, laps = 1, seed = 11)
        val duringCountdown = PgrEngine.stepRace(fresh, null, 1000L)
        assertEquals(60, duringCountdown.countdownTicks)
        assertEquals(10L, duringCountdown.accumulatorMs)
        val racing = PgrEngine.stepRace(duringCountdown, null, 2000L)
        assertEquals(PgrEngine.PgrPhase.RACING, racing.phase)
        assertEquals(0L, racing.raceTicks)
        assertEquals(30L, racing.accumulatorMs)
        val green = PgrEngine.stepRace(racing, null, 1000L)
        // One second at 30 Hz, plus the 30 ms carried by the accumulator.
        assertTrue("racing tics ${green.raceTicks}", green.raceTicks in 30L..31L)
    }

    // ------------------------------------------------------------------
    // Laps and positions
    // ------------------------------------------------------------------

    private fun fakeRacer(index: Int = 0): PgrRacer {
        val def = PgrEngine.PgrCars.starter
        val track = PgrEngine.PgrTracks.byId("kingsway-loop")
        return PgrRacer(
            index = index,
            isPlayer = index == 0,
            name = "test",
            carId = def.id,
            car = PgrEngine.newCarState(def, track.positionAt(0f), track.headingAt(0f)),
        )
    }

    private fun moveRacer(
        racer: PgrRacer,
        track: PgrTrack,
        distance: Float,
        ticks: Long,
        laps: Int = 3,
    ): PgrRacer {
        val d = PgrEngine.wrapDistance(distance, track.length)
        val car = racer.car.copy(position = track.positionAt(d, 0f))
        // Keep the hint on the true target: gameplay moves a couple of metres
        // per tic, so the engine's local projection never sees a stale hint.
        return PgrEngine.updateRacerOnTrack(
            racer.copy(car = car, hintIndex = track.sampleIndexAt(d)),
            track,
            ticks,
            laps,
        )
    }

    @Test
    fun `checkpoints must be passed in order before a lap counts`() {
        val track = PgrEngine.PgrTracks.byId("kingsway-loop")
        val len = track.length
        var racer = fakeRacer()
        var ticks = 0L
        for (fraction in listOf(0.1f, 0.34f, 0.5f, 0.67f, 0.8f, 0.88f, 0.95f, 1.01f)) {
            ticks += 30
            racer = moveRacer(racer, track, fraction * len, ticks)
        }
        assertEquals(1, racer.lapsCompleted)
        assertEquals(0, racer.nextCheckpoint)
        assertNotNull(racer.lastLapTicks)
        assertNotNull(racer.bestLapTicks)

        var skipped = fakeRacer()
        for (fraction in listOf(0.1f, 0.5f, 1.01f)) {
            ticks += 30
            skipped = moveRacer(skipped, track, fraction * len, ticks)
        }
        assertEquals("missed checkpoints cannot complete a lap", 0, skipped.lapsCompleted)
    }

    @Test
    fun `crossing the line with all checkpoints completes every lap to the flag`() {
        val track = PgrEngine.PgrTracks.byId("kingsway-loop")
        val laps = 2
        var racer = fakeRacer()
        var ticks = 0L
        repeat(laps) {
            for (fraction in listOf(0.34f, 0.67f, 0.88f, 1.01f)) {
                ticks += 30
                racer = moveRacer(racer, track, fraction * track.length, ticks, laps)
            }
        }
        assertEquals(laps, racer.lapsCompleted)
        assertTrue(racer.finished)
        assertNotNull(racer.finishTicks)
        assertEquals(0, racer.nextCheckpoint)
    }

    @Test
    fun `position ordering puts finishers first then race distance`() {
        var race = PgrEngine.newRace("neon-bay", "vela-gt", PgrEngine.PgrDifficulty.MEDIUM, laps = 2, seed = 5)
        race = race.copy(
            racers = race.racers.mapIndexed { i, r -> r.copy(totalProgress = i * 100f, finished = false, finishTicks = null) },
        )
        assertEquals(listOf(3, 2, 1, 0), PgrEngine.orderedIndices(race))
        race = race.copy(racers = race.racers.map { r -> if (r.index == 2) r.copy(finished = true, finishTicks = 1L) else r })
        assertEquals(2, PgrEngine.orderedIndices(race).first())
        assertEquals(1, PgrEngine.positionOf(race, 2))

        race = race.copy(
            racers = race.racers.map {
                when (it.index) {
                    2 -> it.copy(finished = true, finishTicks = 500L)
                    3 -> it.copy(finished = true, finishTicks = 400L)
                    else -> it
                }
            },
        )
        assertEquals(listOf(3, 2), PgrEngine.orderedIndices(race).take(2))
    }

    // ------------------------------------------------------------------
    // Kudos
    // ------------------------------------------------------------------

    @Test
    fun `kudos maneuver values match the spec`() {
        assertEquals(400, PgrManeuver.DRIFT.value)
        assertEquals(100, PgrManeuver.BURNOUT.value)
        assertEquals(400, PgrManeuver.SPIN360.value)
        assertEquals(200, PgrManeuver.RACELINE.value)
        assertEquals(100, PgrManeuver.DRAFT.value)
        assertEquals(200, PgrManeuver.OVERTAKE.value)
        assertEquals(400, PgrManeuver.SLINGSHOT.value)
        assertEquals(200, PgrManeuver.CLEAN_SECTION.value)
        assertEquals(400, PgrManeuver.CLEAN_LAP.value)
        assertEquals(800, PgrManeuver.CLEAN_RACE.value)
        assertEquals(5000, PgrManeuver.CLEAN_WIN.value)
        assertEquals(400, PgrManeuver.AIR.value)
        assertEquals(400, PgrManeuver.SPEED.value)
        assertEquals(25, PgrManeuver.CONE_GATE.value)
    }

    @Test
    fun `combo stash accrues a multiplier bonus and banks on end`() {
        var kudos = PgrEngine.kudosAward(PgrEngine.PgrKudosState(), PgrManeuver.DRIFT)
        assertEquals(400, kudos.total)
        assertEquals(1, kudos.multiplier)
        assertEquals(0, kudos.bonus)
        kudos = PgrEngine.kudosAward(kudos, PgrManeuver.SPEED)
        assertEquals(850, kudos.total)
        assertEquals(50, kudos.bonus)
        assertEquals(2, kudos.stash.size)
        kudos = PgrEngine.kudosEndCombo(kudos)
        assertEquals(850, kudos.bank)
        assertEquals(0, kudos.multiplier)
        assertTrue(kudos.stash.isEmpty())
    }

    @Test
    fun `combo expires after five seconds without a maneuver`() {
        var kudos = PgrEngine.kudosAward(PgrEngine.PgrKudosState(), PgrManeuver.OVERTAKE)
        kudos = PgrEngine.kudosTick(kudos, 1f)
        assertEquals(1, kudos.multiplier)
        kudos = PgrEngine.kudosTick(kudos, 10f)
        assertEquals(0, kudos.multiplier)
        assertEquals(200, kudos.bank)
    }

    @Test
    fun `a collision loses the combo bonus but banks the stash`() {
        var kudos = PgrEngine.kudosAward(PgrEngine.PgrKudosState(), PgrManeuver.DRIFT)
        kudos = PgrEngine.kudosAward(kudos, PgrManeuver.SPEED)
        kudos = PgrEngine.kudosFail(kudos)
        assertEquals(800, kudos.bank)
        assertEquals(0, kudos.bonus)
        assertEquals(0, kudos.multiplier)
        assertFalse(kudos.cleanSection)
        assertFalse(kudos.cleanLap)
        assertFalse(kudos.cleanRace)
    }

    @Test
    fun `partial maneuvers scale with the fill curve and complete on timeout`() {
        var kudos = PgrEngine.kudosFill(PgrEngine.PgrKudosState(), PgrManeuver.DRIFT, 0.5f)
        val partial = PgrEngine.maneuverReward(kudos, PgrManeuver.DRIFT)
        assertTrue("partial fill should be worth something: $partial", partial in 1..399)
        // Let the continue window expire; the partial maneuver lands in the stash.
        kudos = PgrEngine.kudosTick(kudos, PgrManeuver.DRIFT.continueSeconds + 0.01f)
        assertEquals(1, kudos.stash.size)
        assertEquals(partial, kudos.stash[0].value)
        // A full award from empty banks exactly the catalogue value.
        assertEquals(400, PgrEngine.kudosAward(PgrEngine.PgrKudosState(), PgrManeuver.DRIFT).total)
    }

    @Test
    fun `clean maneuvers only award while the racer is clean`() {
        val track = PgrEngine.PgrTracks.byId("kingsway-loop")
        var racer = fakeRacer()
        var ticks = 0L
        // Pass the first checkpoint cleanly: clean section kudos lands.
        ticks += 30
        racer = moveRacer(racer, track, 0.34f * track.length, ticks)
        assertFalse(racer.kudos.stash.isEmpty())
        // Dirty the racer; the next section awards nothing but resets.
        var dirty = fakeRacer()
        dirty = dirty.copy(kudos = PgrEngine.kudosFail(dirty.kudos))
        ticks += 30
        dirty = moveRacer(dirty, track, 0.34f * track.length, ticks)
        assertEquals(0, dirty.kudos.total)
        assertTrue(dirty.kudos.cleanSection)
    }

    // ------------------------------------------------------------------
    // Events and garage
    // ------------------------------------------------------------------

    @Test
    fun `event win conditions cover every type`() {
        val events = PgrEngine.PgrEvents
        val ev01 = events.byId("ev01")!!
        assertTrue(PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 100_000)).won)
        assertFalse(PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 200_000)).won)

        val ev02 = events.byId("ev02")!!
        assertTrue(PgrEngine.evaluateEvent(ev02, PgrEventResult(kudos = 1500)).won)
        assertFalse(PgrEngine.evaluateEvent(ev02, PgrEventResult(kudos = 1499)).won)

        val ev03 = events.byId("ev03")!!
        assertTrue(PgrEngine.evaluateEvent(ev03, PgrEventResult(gatesPassed = 6, timeMs = 90_000)).won)
        assertFalse(PgrEngine.evaluateEvent(ev03, PgrEventResult(gatesPassed = 5, timeMs = 90_000)).won)

        val ev04 = events.byId("ev04")!!
        assertTrue(PgrEngine.evaluateEvent(ev04, PgrEventResult(finished = true, position = 1, rivalPosition = 2)).won)
        assertFalse(PgrEngine.evaluateEvent(ev04, PgrEventResult(finished = true, position = 2, rivalPosition = 1)).won)

        val ev05 = events.byId("ev05")!!
        assertTrue(PgrEngine.evaluateEvent(ev05, PgrEventResult(finished = true, eliminated = false)).won)
        assertFalse(PgrEngine.evaluateEvent(ev05, PgrEventResult(finished = true, eliminated = true)).won)

        val ev06 = events.byId("ev06")!!
        assertTrue(PgrEngine.evaluateEvent(ev06, PgrEventResult(overtakes = 5)).won)
        assertFalse(PgrEngine.evaluateEvent(ev06, PgrEventResult(overtakes = 4)).won)

        val ev07 = events.byId("ev07")!!
        assertTrue(PgrEngine.evaluateEvent(ev07, PgrEventResult(topSpeed = 75f)).won)
        assertFalse(PgrEngine.evaluateEvent(ev07, PgrEventResult(topSpeed = 74.9f)).won)

        val ev08 = events.byId("ev08")!!
        assertTrue(PgrEngine.evaluateEvent(ev08, PgrEventResult(finished = true, timeMs = 190_000, kudos = 2000)).won)
        assertFalse(PgrEngine.evaluateEvent(ev08, PgrEventResult(finished = true, timeMs = 210_000, kudos = 2000)).won)

        val ev09 = events.byId("ev09")!!
        assertTrue(PgrEngine.evaluateEvent(ev09, PgrEventResult(position = 1)).won)
        assertFalse(PgrEngine.evaluateEvent(ev09, PgrEventResult(position = 2)).won)
    }

    @Test
    fun `event medals and credit payouts follow the difficulty tiers`() {
        val ev01 = PgrEngine.PgrEvents.byId("ev01")!!
        assertEquals(
            PgrMedal.GOLD,
            PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 100_000)).medal,
        )
        assertEquals(
            PgrMedal.SILVER,
            PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 140_000)).medal,
        )
        assertEquals(
            PgrMedal.BRONZE,
            PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 165_000)).medal,
        )
        assertEquals(PgrEngine.PgrDifficulty.EASY.creditReward * 3, PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 100_000)).credits)
        assertEquals(PgrEngine.PgrDifficulty.EASY.creditReward, PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 165_000)).credits)
        assertEquals(PgrMedal.NONE, PgrEngine.evaluateEvent(ev01, PgrEventResult(finished = true, timeMs = 300_000)).medal)
    }

    @Test
    fun `career events gate on prerequisite medals`() {
        val fresh = PgrProfile()
        assertTrue(PgrEngine.canEnterEvent(fresh, PgrEngine.PgrEvents.byId("ev01")!!))
        assertFalse(PgrEngine.canEnterEvent(fresh, PgrEngine.PgrEvents.byId("ev02")!!))
        val cleared = fresh.copy(medals = mapOf("ev01" to PgrMedal.BRONZE))
        assertTrue(PgrEngine.canEnterEvent(cleared, PgrEngine.PgrEvents.byId("ev02")!!))
        assertFalse(PgrEngine.canEnterEvent(cleared, PgrEngine.PgrEvents.byId("ev03")!!))
    }

    @Test
    fun `purchase enforces credits, kudos and medal locks`() {
        val starter = PgrProfile()
        assertTrue(starter.cars.contains("vela-gt"))
        assertEquals("credits", PgrEngine.purchase(starter, "corso-s").error)

        val rich = starter.copy(credits = 200_000)
        val bought = PgrEngine.purchase(rich, "corso-s")
        assertNull(bought.error)
        assertEquals(200_000 - 12_000, bought.profile.credits)
        assertEquals("owned", PgrEngine.purchase(bought.profile, "corso-s").error)

        assertEquals("locked", PgrEngine.purchase(rich, "tigre-gts").error)
        val decorated = rich.copy(
            medals = mapOf(
                "ev01" to PgrMedal.BRONZE,
                "ev02" to PgrMedal.SILVER,
                "ev03" to PgrMedal.GOLD,
            ),
        )
        assertEquals("kudos", PgrEngine.purchase(decorated, "tigre-gts").error)
        val flush = decorated.copy(kudosBank = 2_000)
        val race = PgrEngine.purchase(flush, "tigre-gts")
        assertNull(race.error)
        assertEquals(flush.credits - 30_000, race.profile.credits)
        assertTrue(race.profile.cars.contains("tigre-gts"))
    }

    @Test
    fun `winning an event banks rewards, records times and unlocks cars`() {
        val ev01 = PgrEngine.PgrEvents.byId("ev01")!!
        val (profile, outcome) = PgrEngine.applyOutcome(
            PgrProfile(),
            ev01,
            PgrEventResult(
                finished = true,
                timeMs = 80_000,
                kudos = 1_000,
                bestLapMs = 40_000,
                position = 1,
            ),
        )
        assertNotNull(outcome)
        assertTrue(outcome!!.won)
        assertTrue(profile.credits > 1_500)
        assertEquals(PgrMedal.GOLD, profile.medals["ev01"])
        assertEquals(1_000, profile.kudosBank)
        assertEquals(40_000L, profile.bestLaps["kingsway-loop"])
        assertEquals(1, profile.eventsPlayed)

        val ev10 = PgrEngine.PgrEvents.byId("ev10")!!
        val (unlocked, _) = PgrEngine.applyOutcome(
            PgrProfile(),
            ev10,
            PgrEventResult(position = 1, finished = true, timeMs = 100_000),
        )
        assertTrue(unlocked.cars.contains("aquila-sc"))
    }

    @Test
    fun `profile encoding round-trips and rejects garbage`() {
        val profile = PgrProfile(
            credits = 12_345,
            kudosBank = 678,
            cars = setOf("vela-gt", "corso-s"),
            medals = mapOf("ev01" to PgrMedal.SILVER, "ev02" to PgrMedal.GOLD),
            bestLaps = mapOf("kingsway-loop" to 42_123L),
            bestTimes = mapOf("neon-bay" to 123_456L),
            bestKudos = mapOf("harbor-grid" to 4_321),
            eventsPlayed = 7,
        )
        assertEquals(profile, PgrEngine.decodeProfile(PgrEngine.encodeProfile(profile)))
        assertNull(PgrEngine.decodeProfile(null))
        assertNull(PgrEngine.decodeProfile("garbage"))
    }

    // ------------------------------------------------------------------
    // Tracks and AI
    // ------------------------------------------------------------------

    @Test
    fun `track splines sample smoothly, close the loop and carry metadata`() {
        for (track in PgrEngine.PgrTracks.all) {
            assertTrue("length ${track.length}", track.length > 300f)
            assertTrue(track.count > 48)
            assertTrue(track.width > 4f)
            val start = track.sampleAt(0f)
            val end = track.sampleAt(track.length)
            assertTrue("loop closes: ${(start.position - end.position).length()}", (start.position - end.position).length() < 0.5f)
            assertEquals(1f, start.forward.length(), 1e-3f)
            assertEquals(1f, start.right.length(), 1e-3f)
            val a = track.sampleAt(0f)
            val b = track.sampleAt(track.spacing * 6f)
            val c = track.sampleAt(track.spacing * 12f)
            assertTrue((b.position - a.position).length() > 0.5f)
            assertTrue((c.position - b.position).length() > 0.5f)

            val distance = track.length * 0.4f
            val point = track.positionAt(distance, 2f)
            val projected = track.project(point.x, point.z, -1)
            assertEquals(distance, projected.distance, 6f)
            assertEquals(2f, projected.lateral, 1.5f)

            assertEquals(PgrEngine.MAX_CHECKPOINTS, track.checkpointDistances.size)
            assertEquals(track.checkpointDistances.sorted(), track.checkpointDistances)
            assertTrue(track.checkpointDistances.all { it > 0f && it < track.length })
        }
    }

    @Test
    fun `ai follows the racing line inside the barriers`() {
        val track = PgrEngine.PgrTracks.byId("kingsway-loop")
        var race = PgrEngine.newRace(
            trackId = "kingsway-loop",
            playerCarId = "vela-gt",
            difficulty = PgrEngine.PgrDifficulty.MEDIUM,
            laps = 3,
            seed = 7,
        )
        var maxLateral = 0f
        repeat(30 * 45) {
            race = PgrEngine.tickRace(race, null)
            for (racer in race.racers) {
                val lateral = abs(
                    track.project(racer.car.position.x, racer.car.position.z, racer.hintIndex).lateral,
                )
                maxLateral = maxOf(maxLateral, lateral)
            }
        }
        assertTrue("ai stayed within the track: max lateral $maxLateral", maxLateral < track.width * 0.55f)
        val totalProgress = race.racers.sumOf { it.totalProgress.toDouble() }
        assertTrue("ai made progress: $totalProgress", totalProgress > 0.0)
        assertTrue(race.racers.any { it.lapsCompleted > 0 || it.progress > track.length * 0.5f })
    }

    @Test
    fun `ai inputs stay in range and rubber banding is bounded`() {
        var race = PgrEngine.newRace("neon-bay", "vela-gt", PgrEngine.PgrDifficulty.HARD, laps = 2, seed = 99)
        repeat(120) { race = PgrEngine.tickRace(race, null) }
        for (index in race.racers.indices) {
            val input = PgrEngine.aiInput(race, index, 123)
            assertTrue(input.steer in -1f..1f)
            assertTrue(input.throttle in 0f..1f)
            assertTrue(input.brake in 0f..1f)
            val rubber = PgrEngine.rubberband(race, race.racers[index])
            assertTrue(rubber in PgrEngine.AI_RUBBER_MIN..PgrEngine.AI_RUBBER_MAX)
        }
    }

    @Test
    fun `countdown holds the field until the green light`() {
        var race = PgrEngine.newRace("harbor-grid", "vela-gt", PgrEngine.PgrDifficulty.EASY, laps = 1, seed = 3)
        assertTrue(race.phase == PgrEngine.PgrPhase.COUNTDOWN)
        val startPositions = race.racers.map { it.car.position }
        repeat(PgrEngine.TICS_PER_SECOND * 2) { race = PgrEngine.tickRace(race, null) }
        assertEquals(startPositions, race.racers.map { it.car.position })
        repeat(PgrEngine.TICS_PER_SECOND) { race = PgrEngine.tickRace(race, null) }
        assertTrue(race.phase == PgrEngine.PgrPhase.RACING)
    }
}
