package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.GoalStreakState
import com.heretek.dorado_hd.ui.apps.games.KeeperZone
import com.heretek.dorado_hd.ui.apps.games.KickOutcome
import com.heretek.dorado_hd.ui.apps.games.MadBall
import com.heretek.dorado_hd.ui.apps.games.PenaltyEngine
import com.heretek.dorado_hd.ui.apps.games.PenaltyPowerup
import com.heretek.dorado_hd.ui.apps.games.PenaltySwipe
import com.heretek.dorado_hd.ui.apps.games.PenaltyStage
import com.heretek.dorado_hd.ui.apps.games.PenaltyVec3
import com.heretek.dorado_hd.ui.apps.games.TournamentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PenaltyTest {

    /* ---------------- constants ---------------- */

    @Test
    fun `goal geometry matches the spec`() {
        assertEquals(-3.5, PenaltyEngine.GOAL_POST_LEFT_X, 1e-9)
        assertEquals(3.5, PenaltyEngine.GOAL_POST_RIGHT_X, 1e-9)
        assertEquals(2.2, PenaltyEngine.CROSSBAR_HEIGHT, 1e-9)
        assertEquals(11.0, PenaltyEngine.GOAL_LINE_Z, 1e-9)
        assertEquals(0.6, PenaltyEngine.POST_BOUNCE, 1e-9)
        assertEquals(0.1, PenaltyEngine.NET_BOUNCE, 1e-9)
        assertEquals(0.4, PenaltyEngine.GROUND_BOUNCE, 1e-9)
        assertEquals(0.5, PenaltyEngine.BALL_MASS, 1e-9)
        assertEquals(1.8, PenaltyStage.KICKER.goalDepth, 1e-9)
        assertEquals(3.0, PenaltyStage.SD1.goalDepth, 1e-9)
        assertEquals(2.0, PenaltyStage.SD3.goalDepth, 1e-9)
    }

    @Test
    fun `keeper body and dive geometry match the spec`() {
        assertEquals(0.5, PenaltyEngine.KEEPER_WIDTH, 1e-9)
        assertEquals(0.75, PenaltyEngine.KEEPER_HEIGHT, 1e-9)
        assertEquals(0.5, PenaltyEngine.KEEPER_DEPTH, 1e-9)
        assertEquals(0.6, PenaltyEngine.KEEPER_REACH, 1e-9)
        assertEquals(3.5, PenaltyEngine.DIVE_HIGH_DISTANCE, 1e-9)
        assertEquals(3.0, PenaltyEngine.DIVE_LOW_DISTANCE, 1e-9)
        assertEquals(1.0, PenaltyEngine.STEP_DISTANCE, 1e-9)
        val home = PenaltyEngine.keeperHome(PenaltyStage.KICKER)
        val high = PenaltyEngine.keeperTarget(KeeperZone.HIGH_LEFT, PenaltyStage.KICKER) - home
        val low = PenaltyEngine.keeperTarget(KeeperZone.LOW_RIGHT, PenaltyStage.KICKER) - home
        val jump = PenaltyEngine.keeperTarget(KeeperZone.JUMP, PenaltyStage.KICKER) - home
        assertEquals(PenaltyEngine.DIVE_HIGH_DISTANCE, abs(high.x), 1e-9)
        assertEquals(PenaltyEngine.DIVE_LOW_DISTANCE, abs(low.x), 1e-9)
        assertEquals(0.25, low.y, 1e-9)
        assertEquals(PenaltyEngine.JUMP_UP_DISTANCE, jump.y, 1e-9)
    }

    @Test
    fun `reaction timers ramp by round`() {
        assertEquals(6, PenaltyEngine.MAX_REACTION_TIMES.size)
        assertEquals(0.5, PenaltyEngine.MAX_REACTION_TIMES[0], 1e-9)
        assertEquals(0.0, PenaltyEngine.MAX_REACTION_TIMES[4], 1e-9)
        assertEquals(0.2, PenaltyEngine.MAX_REACTION_TIMES[5], 1e-9)
        assertEquals(0.4, PenaltyEngine.RANDOM_DIVE_PROBABILITY[1], 1e-9)
        assertEquals(0.2, PenaltyStage.SD2.reactionMax, 1e-9)
    }

    /* ---------------- swipe physics ---------------- */

    @Test
    fun `swipe force clamps between 350 and 750`() {
        val weak = PenaltyEngine.kickFromSwipe(PenaltySwipe(1.0, 1.0))
        assertEquals(350.0, weak.speed, 1e-6)
        val strong = PenaltyEngine.kickFromSwipe(PenaltySwipe(40.0, 40.0))
        assertEquals(750.0, strong.speed, 1e-6)
        assertEquals(weak.speed, weak.force.length(), 1e-6)
        assertEquals(strong.speed, strong.force.length(), 1e-6)
    }

    @Test
    fun `stage multiplier scales the sudden death two kick`() {
        val swipe = PenaltySwipe(20.0, 0.0)
        val base = PenaltyEngine.kickFromSwipe(swipe, PenaltyStage.KICKER)
        assertEquals(600.0, base.speed, 1e-6)
        val sd2 = PenaltyEngine.kickFromSwipe(swipe, PenaltyStage.SD2)
        assertEquals(600.0 * 1.3, sd2.speed, 1e-6)
    }

    @Test
    fun `arc spin is the cross of swipe and running average`() {
        val straight = PenaltyEngine.kickFromSwipe(PenaltySwipe(10.0, 0.0, 10.0, 0.0))
        assertEquals(0.0, straight.spin, 1e-9)
        val curved = PenaltyEngine.kickFromSwipe(PenaltySwipe(10.0, 0.0, 10.0, -10.0))
        assertTrue(abs(curved.spin) > 0.0)
        assertTrue(abs(curved.spin) <= PenaltyEngine.SPIN_MAX)
        val extreme = PenaltyEngine.kickFromSwipe(PenaltySwipe(10.0, 0.0, -10.0, 10.0))
        assertEquals(PenaltyEngine.SPIN_MAX, abs(extreme.spin), 1e-9)
    }

    @Test
    fun `upward swipe drives the ball toward the goal`() {
        val kick = PenaltyEngine.kickFromSwipe(PenaltySwipe(0.0, -20.0, 0.0, -20.0))
        assertTrue("forward z", kick.direction.z > 0.0)
        assertTrue("lifted y", kick.direction.y > 0.0)
    }

    /* ---------------- ball flight ---------------- */

    @Test
    fun `corner shot beats a keeper that never moves`() {
        val kick = PenaltyEngine.aimKick(PenaltyVec3(2.8, 0.8, PenaltyEngine.GOAL_LINE_Z), 0.7)
        val shot = PenaltyEngine.simulateShot(kick, KeeperZone.NONE, 5.0, PenaltyStage.KICKER)
        assertEquals(KickOutcome.GOAL, shot.outcome)
        assertTrue(shot.trajectory.size > 4)
    }

    @Test
    fun `wide and high shots miss`() {
        val wide = PenaltyEngine.aimKick(PenaltyVec3(5.0, 1.0, PenaltyEngine.GOAL_LINE_Z), 0.7)
        assertEquals(KickOutcome.MISS, PenaltyEngine.simulateShot(wide, KeeperZone.NONE, 5.0).outcome)
        val high = PenaltyEngine.aimKick(PenaltyVec3(0.0, 3.5, PenaltyEngine.GOAL_LINE_Z), 0.7)
        assertEquals(KickOutcome.MISS, PenaltyEngine.simulateShot(high, KeeperZone.NONE, 5.0).outcome)
    }

    @Test
    fun `post strike deflects and misses`() {
        val post = PenaltyEngine.aimKick(PenaltyVec3(3.5, 1.0, PenaltyEngine.GOAL_LINE_Z), 0.7)
        val shot = PenaltyEngine.simulateShot(post, KeeperZone.NONE, 5.0)
        assertEquals(KickOutcome.MISS, shot.outcome)
        assertTrue(shot.trajectory.any { it.x > 3.5 })
    }

    @Test
    fun `ball never sinks below the ground radius`() {
        val kick = PenaltyEngine.aimKick(PenaltyVec3(0.0, -0.4, 3.0), 0.5)
        val shot = PenaltyEngine.simulateShot(kick, KeeperZone.NONE, 5.0)
        assertTrue(shot.trajectory.drop(1).all { it.y >= PenaltyEngine.BALL_RADIUS - 1e-6 })
    }

    @Test
    fun `a dead ball settles immediately`() {
        val kick = com.heretek.dorado_hd.ui.apps.games.PenaltyKick(PenaltyVec3(0.0, 0.0, 0.0), 0.0, 0.0)
        val shot = PenaltyEngine.simulateShot(kick, KeeperZone.NONE, 5.0)
        assertEquals(KickOutcome.MISS, shot.outcome)
        assertTrue(shot.trajectory.size <= 4)
    }

    /* ---------------- keeper ---------------- */

    @Test
    fun `keeper saves only the zone it commits to`() {
        val corner = PenaltyEngine.aimKick(PenaltyVec3(-3.0, 0.4, PenaltyEngine.GOAL_LINE_Z), 0.7)
        assertEquals(KickOutcome.SAVED, PenaltyEngine.simulateShot(corner, KeeperZone.LOW_LEFT, 0.0).outcome)
        assertEquals(KickOutcome.GOAL, PenaltyEngine.simulateShot(corner, KeeperZone.LOW_RIGHT, 0.0).outcome)
    }

    @Test
    fun `ai keeper reads the shot when the random dive does not fire`() {
        val target = PenaltyVec3(-2.8, 0.5, PenaltyEngine.GOAL_LINE_Z)
        var rng = 1
        var plan = PenaltyEngine.aiKeeperPlan(target, PenaltyStage.SD1, 0, rng)
        var guard = 0
        while (plan.first.zone !in listOf(KeeperZone.LOW_LEFT, KeeperZone.HIGH_LEFT) && guard++ < 200) {
            rng = plan.second
            plan = PenaltyEngine.aiKeeperPlan(target, PenaltyStage.SD1, 0, rng)
        }
        assertTrue(plan.first.zone == KeeperZone.LOW_LEFT || plan.first.zone == KeeperZone.HIGH_LEFT)
        assertTrue(plan.first.reaction <= PenaltyStage.SD1.reactionMax)
    }

    /* ---------------- match rules ---------------- */

    @Test
    fun `five kicks each decide the match`() {
        var match = PenaltyEngine.newMatch(0, 1, 0)
        repeat(PenaltyEngine.KICKS_PER_SIDE) {
            match = PenaltyEngine.recordShot(match, KickOutcome.GOAL)
            match = PenaltyEngine.recordShot(match, KickOutcome.MISS)
        }
        assertTrue(match.finished)
        assertTrue(match.playerWon)
        assertEquals(5, match.playerGoals)
        assertEquals(0, match.opponentGoals)
        assertFalse(match.suddenDeath)
    }

    @Test
    fun `tied regulation enters sudden death until a differential`() {
        var match = PenaltyEngine.newMatch(0, 1, 0)
        repeat(PenaltyEngine.KICKS_PER_SIDE) {
            match = PenaltyEngine.recordShot(match, KickOutcome.GOAL)
            match = PenaltyEngine.recordShot(match, KickOutcome.GOAL)
        }
        assertTrue(match.suddenDeath)
        assertFalse(match.finished)
        match = PenaltyEngine.recordShot(match, KickOutcome.GOAL)
        assertFalse(match.finished)
        match = PenaltyEngine.recordShot(match, KickOutcome.MISS)
        assertTrue(match.finished)
        assertTrue(match.playerWon)
    }

    @Test
    fun `alternating roles drive the match phase`() {
        var match = PenaltyEngine.newMatch(0, 1, 0)
        assertEquals(com.heretek.dorado_hd.ui.apps.games.MatchPhase.SHOOT, PenaltyEngine.matchPhase(match))
        match = PenaltyEngine.recordShot(match, KickOutcome.GOAL)
        assertEquals(com.heretek.dorado_hd.ui.apps.games.MatchPhase.DEFEND, PenaltyEngine.matchPhase(match))
        match = PenaltyEngine.recordShot(match, KickOutcome.SAVED)
        assertEquals(com.heretek.dorado_hd.ui.apps.games.MatchPhase.SHOOT, PenaltyEngine.matchPhase(match))
    }

    /* ---------------- bracket ---------------- */

    @Test
    fun `tournament seeds eight first round matches and fifteen slots`() {
        val tournament = PenaltyEngine.newTournament(3, seed = 7)
        assertEquals(32, PenaltyEngine.TEAMS.size)
        assertEquals(15, tournament.matches.count { it.round >= 0 })
        assertEquals(8, tournament.matches.count { it.round == 0 })
        val entrants = tournament.matches.filter { it.round == 0 }.flatMap { listOf(it.home, it.away) }
        assertEquals(16, entrants.toSet().size)
        assertTrue(entrants.contains(3))
        assertTrue(tournament.matches.any { it.home == tournament.opponent || it.away == tournament.opponent })
    }

    @Test
    fun `winning advances the player and simulates the rest`() {
        var tournament = PenaltyEngine.newTournament(5, seed = 11)
        repeat(PenaltyEngine.KICKS_PER_SIDE * 2) {
            val outcome = if (tournament.match.nextIsPlayer) KickOutcome.GOAL else KickOutcome.MISS
            tournament = tournament.copy(match = PenaltyEngine.recordShot(tournament.match, outcome))
        }
        tournament = PenaltyEngine.advanceTournament(tournament, newSeed = 99)
        assertEquals(1, tournament.round)
        assertEquals(TournamentStatus.PLAYING, tournament.status)
        assertEquals(4, tournament.matches.count { it.round == 1 && it.home >= 0 })
        assertTrue(tournament.matches.filter { it.round == 0 }.all { it.winner >= 0 })
    }

    @Test
    fun `a loss ends the tournament`() {
        var tournament = PenaltyEngine.newTournament(5, seed = 11)
        repeat(PenaltyEngine.KICKS_PER_SIDE * 2) {
            val outcome = if (tournament.match.nextIsPlayer) KickOutcome.MISS else KickOutcome.GOAL
            tournament = tournament.copy(match = PenaltyEngine.recordShot(tournament.match, outcome))
        }
        tournament = PenaltyEngine.advanceTournament(tournament, newSeed = 5)
        assertEquals(TournamentStatus.LOST, tournament.status)
    }

    @Test
    fun `four wins lift the trophy`() {
        var tournament = PenaltyEngine.newTournament(7, seed = 21)
        repeat(PenaltyEngine.ROUNDS) { roundIndex ->
            assertFalse(tournament.match.finished)
            while (!tournament.match.finished) {
                val outcome = if (tournament.match.nextIsPlayer) KickOutcome.GOAL else KickOutcome.MISS
                tournament = tournament.copy(match = PenaltyEngine.recordShot(tournament.match, outcome))
            }
            tournament = PenaltyEngine.advanceTournament(tournament, newSeed = 100 + roundIndex)
        }
        assertEquals(TournamentStatus.WON, tournament.status)
    }

    @Test
    fun `tournament save round trips`() {
        var tournament = PenaltyEngine.newTournament(9, seed = 33)
        tournament = tournament.copy(match = PenaltyEngine.recordShot(tournament.match, KickOutcome.GOAL))
        tournament = tournament.copy(match = PenaltyEngine.recordShot(tournament.match, KickOutcome.SAVED))
        val blob = PenaltyEngine.encodeTournament(tournament)
        assertEquals(tournament, PenaltyEngine.decodeTournament(blob))
        assertNotNull(PenaltyEngine.decodeTournament(blob))
        assertNotEquals(tournament, PenaltyEngine.decodeTournament("pt1;garbage"))
    }

    /* ---------------- Mad Minute ---------------- */

    @Test
    fun `countdown runs out before the clock starts`() {
        var state = PenaltyEngine.newMadMinute(seed = 1)
        assertEquals(3.0, state.countdown, 1e-9)
        state = PenaltyEngine.madMinuteStep(state, 3.0)
        assertEquals(0.0, state.countdown, 1e-9)
        assertEquals(PenaltyEngine.MAD_MINUTE_SECONDS, state.timeLeft, 1e-9)
        state = PenaltyEngine.madMinuteStep(state, 1.0)
        assertEquals(PenaltyEngine.MAD_MINUTE_SECONDS - 1.0, state.timeLeft, 1e-6)
    }

    @Test
    fun `time freeze pauses the clock`() {
        var state = PenaltyEngine.newMadMinute(seed = 1).copy(countdown = 0.0, timeLeft = 30.0)
        state = PenaltyEngine.applyPowerup(state, PenaltyPowerup.TIME_FREEZE)
        state = PenaltyEngine.madMinuteStep(state, 1.0)
        assertEquals(30.0, state.timeLeft, 1e-6)
        assertEquals(4.0, state.freezeLeft, 1e-6)
    }

    @Test
    fun `goals score and streaks reset on a miss`() {
        val goalBall = MadBall(
            trajectory = emptyList(),
            spawnAt = 0.0,
            duration = 0.5,
            goal = true,
            scoreValue = PenaltyEngine.MAD_GOAL_POINTS,
        )
        var state = PenaltyEngine.newMadMinute(seed = 1).copy(countdown = 0.0, timeLeft = 30.0, liveBalls = listOf(goalBall))
        state = PenaltyEngine.madMinuteStep(state, 0.6)
        assertEquals(PenaltyEngine.MAD_GOAL_POINTS, state.score)
        assertEquals(1, state.streak)
        assertEquals(1, state.outcomes.size)

        val missBall = MadBall(emptyList(), spawnAt = state.clock, duration = 0.2, goal = false, scoreValue = 0)
        state = PenaltyEngine.madMinuteStep(state.copy(liveBalls = listOf(missBall)), 0.3)
        assertEquals(0, state.streak)
        assertEquals(1, state.bestStreak)
        assertEquals(PenaltyEngine.MAD_GOAL_POINTS, state.score)
    }

    @Test
    fun `points bonus doubles the next goal`() {
        val goalBall = MadBall(emptyList(), spawnAt = 0.0, duration = 0.5, goal = true, scoreValue = PenaltyEngine.MAD_GOAL_POINTS)
        var state = PenaltyEngine.newMadMinute(seed = 1).copy(countdown = 0.0, timeLeft = 30.0, liveBalls = listOf(goalBall))
        state = PenaltyEngine.applyPowerup(state, PenaltyPowerup.POINTS_BONUS)
        state = PenaltyEngine.madMinuteStep(state, 0.6)
        assertEquals(PenaltyEngine.MAD_GOAL_POINTS * 2, state.score)
    }

    @Test
    fun `ball frenzy adds balls up to the cap of three`() {
        var state = PenaltyEngine.newMadMinute(seed = 5).copy(countdown = 0.0, timeLeft = 30.0, frenzyLeft = 5.0)
        val kick = PenaltyEngine.aimKick(PenaltyVec3(1.0, 1.0, PenaltyEngine.GOAL_LINE_Z), 0.7)
        state = PenaltyEngine.madMinuteKick(state, kick)
        assertEquals(2, state.liveBalls.size)
        state = PenaltyEngine.madMinuteKick(state.copy(cooldown = 0.0), kick)
        assertEquals(PenaltyEngine.MAX_BALLS, state.liveBalls.size)
        state = PenaltyEngine.madMinuteKick(state.copy(cooldown = 0.0), kick)
        assertEquals(PenaltyEngine.MAX_BALLS, state.liveBalls.size)
    }

    @Test
    fun `mad minute ends at zero`() {
        var state = PenaltyEngine.newMadMinute(seed = 2).copy(countdown = 0.0, timeLeft = 0.4)
        state = PenaltyEngine.madMinuteStep(state, 0.5)
        assertTrue(state.over)
        assertEquals(0.0, state.timeLeft, 1e-9)
        assertEquals(state, PenaltyEngine.madMinuteStep(state, 1.0))
    }

    /* ---------------- Goal Streak ---------------- */

    @Test
    fun `streak grows on goals and resets on a miss while best persists`() {
        var state = GoalStreakState(seed = 3)
        state = PenaltyEngine.goalStreakRecord(state, KickOutcome.GOAL)
        state = PenaltyEngine.goalStreakRecord(state, KickOutcome.GOAL)
        assertEquals(2, state.streak)
        assertEquals(2, state.best)
        state = PenaltyEngine.goalStreakRecord(state, KickOutcome.SAVED)
        assertEquals(0, state.streak)
        assertEquals(2, state.best)
        assertEquals(3, state.attempts)
        assertEquals(KickOutcome.SAVED, state.last)
    }

    @Test
    fun `identical inputs are deterministic`() {
        val kick = PenaltyEngine.aimKick(PenaltyVec3(-2.4, 0.9, PenaltyEngine.GOAL_LINE_Z), 0.68, spin = 0.2)
        val a = PenaltyEngine.simulateShot(kick, KeeperZone.LOW_LEFT, 0.1)
        val b = PenaltyEngine.simulateShot(kick, KeeperZone.LOW_LEFT, 0.1)
        assertEquals(a, b)
        var first = PenaltyEngine.newTournament(1, seed = 8)
        var second = PenaltyEngine.newTournament(1, seed = 8)
        repeat(3) {
            first = first.copy(match = PenaltyEngine.recordShot(first.match, KickOutcome.GOAL))
            second = second.copy(match = PenaltyEngine.recordShot(second.match, KickOutcome.GOAL))
        }
        assertEquals(first, second)
    }
}
