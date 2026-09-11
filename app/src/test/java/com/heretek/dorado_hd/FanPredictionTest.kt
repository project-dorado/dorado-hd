package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.FanEvent
import com.heretek.dorado_hd.ui.apps.games.FanPick
import com.heretek.dorado_hd.ui.apps.games.FanPredictionEngine
import com.heretek.dorado_hd.ui.apps.games.FanSport
import com.heretek.dorado_hd.ui.apps.games.FixtureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FanPredictionTest {

    private val deviceId = "6a8e69e4-fb60-4eb1-b582-ffb70bbbb45d"
    private val now = 1_700_000_000_000L

    private fun newState(seed: Int = 7) = FanPredictionEngine.newState(deviceId, now, seed)

    @Test
    fun `new state registers the device and seeds a local slate`() {
        val state = newState()
        assertEquals(deviceId, state.profile.deviceId)
        assertTrue(state.fixtures.isNotEmpty())
        assertTrue(state.fixtures.all { it.kickoffMs > now })
        assertTrue(state.fixtures.any { it.sport == FanSport.SOCCER })
        assertTrue(state.fixtures.any { it.sport == FanSport.BASKETBALL })
        assertTrue(state.fixtures.any { it.sport == FanSport.HOCKEY })
        assertEquals(1, state.standings.count { it.isYou })
        assertEquals(FanPredictionEngine.OFFLINE_BANNER, state.banner)
    }

    @Test
    fun `fixtures are deterministic for the same seed`() {
        assertEquals(newState(seed = 7).fixtures, newState(seed = 7).fixtures)
    }

    @Test
    fun `making a prediction stores the pick and exact score`() {
        var state = newState()
        val fixture = state.fixtures.first()
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME, 2, 1))
        val prediction = state.predictions[fixture.id]
        assertNotNull(prediction)
        assertEquals(FanPick.HOME, prediction!!.pick)
        assertEquals(2, prediction.homeScore)
        assertEquals(1, prediction.awayScore)
        assertNull(state.banner)
    }

    @Test
    fun `exact scores derive the pick`() {
        var state = newState()
        val fixture = state.fixtures.first()
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME, 1, 2))
        assertEquals(FanPick.AWAY, state.predictions[fixture.id]!!.pick)
    }

    @Test
    fun `draw picks are rejected outside soccer`() {
        var state = newState()
        val fixture = state.fixtures.first { !it.sport.hasDraw }
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.DRAW))
        assertNull(state.predictions[fixture.id])
        assertEquals(FanPredictionEngine.DRAW_BANNER, state.banner)
    }

    @Test
    fun `predictions lock at kickoff`() {
        var state = newState()
        val fixture = state.fixtures.first()
        assertTrue(FanPredictionEngine.canPredict(state, fixture))
        state = FanPredictionEngine.apply(state, FanEvent.Refresh(fixture.kickoffMs))
        val locked = state.fixtures.first { it.id == fixture.id }
        assertFalse(FanPredictionEngine.canPredict(state, locked))
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME))
        assertNull(state.predictions[fixture.id])
        assertEquals(FanPredictionEngine.LOCKED_BANNER, state.banner)
    }

    @Test
    fun `settlement awards outcome and exact score points`() {
        var state = newState()
        val fixture = state.fixtures.first()
        val (home, away) = FanPredictionEngine.simulateScore(fixture)
        val pick = when {
            home > away -> FanPick.HOME
            home < away -> FanPick.AWAY
            else -> FanPick.DRAW
        }
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, pick, home, away))
        val settled = FanPredictionEngine.settle(state, fixture.kickoffMs)
        val final = settled.fixtures.first { it.id == fixture.id }
        assertEquals(FixtureStatus.FINAL, final.status)
        val you = settled.standings.first { it.isYou }
        assertEquals(FanPredictionEngine.OUTCOME_POINTS + FanPredictionEngine.EXACT_BONUS, you.points)
        assertEquals(1, you.total)
        assertEquals(1, you.correct)
    }

    @Test
    fun `wrong outcome scores nothing`() {
        var state = newState()
        val fixture = state.fixtures.first { !it.sport.hasDraw }
        val (home, away) = FanPredictionEngine.simulateScore(fixture)
        val wrong = if (home >= away) FanPick.AWAY else FanPick.HOME
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, wrong))
        val settled = FanPredictionEngine.settle(state, fixture.kickoffMs)
        assertEquals(0, settled.standings.first { it.isYou }.points)
        assertEquals(1, settled.standings.first { it.isYou }.total)
    }

    @Test
    fun `settlement is idempotent`() {
        var state = newState()
        val fixture = state.fixtures.first()
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME))
        val once = FanPredictionEngine.settle(state, fixture.kickoffMs)
        val twice = FanPredictionEngine.settle(once, fixture.kickoffMs + 60_000)
        assertEquals(once.standings, twice.standings)
        assertEquals(once.fixtures, twice.fixtures)
    }

    @Test
    fun `standings keep one highlighted row sorted by points`() {
        var state = newState()
        state = FanPredictionEngine.settle(state, state.fixtures.maxOf { it.kickoffMs })
        val sorted = FanPredictionEngine.sortedStandings(state)
        assertEquals(1, sorted.count { it.isYou })
        for (i in 0 until sorted.size - 1) {
            assertTrue(sorted[i].points >= sorted[i + 1].points)
        }
        assertEquals("you", sorted.first { it.isYou }.name)
    }

    @Test
    fun `favorite team round trips through persistence`() {
        var state = newState()
        state = FanPredictionEngine.apply(state, FanEvent.SetFavoriteTeam(FanSport.SOCCER, 3))
        assertEquals(3, state.profile.favorites[FanSport.SOCCER])
        val decoded = FanPredictionEngine.decode(FanPredictionEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(3, decoded!!.profile.favorites[FanSport.SOCCER])
        val unset = FanPredictionEngine.apply(decoded, FanEvent.SetFavoriteTeam(FanSport.SOCCER, null))
        assertNull(unset.profile.favorites[FanSport.SOCCER])
    }

    @Test
    fun `favorite team must belong to the sport`() {
        var state = newState()
        state = FanPredictionEngine.apply(state, FanEvent.SetFavoriteTeam(FanSport.HOCKEY, 0))
        assertNull(state.profile.favorites[FanSport.HOCKEY])
        assertTrue(state.banner!!.contains("unknown team"))
    }

    @Test
    fun `nickname is sanitized and persists`() {
        var state = newState()
        state = FanPredictionEngine.apply(state, FanEvent.SetNickname("  bad;name:with,stuff  "))
        assertEquals("bad name with stuff", state.profile.nickname)
        val decoded = FanPredictionEngine.decode(FanPredictionEngine.encode(state))
        assertEquals("bad name with stuff", decoded!!.profile.nickname)
    }

    @Test
    fun `offline toggle keeps the local slate usable`() {
        var state = FanPredictionEngine.apply(newState(), FanEvent.SetOffline(false))
        assertFalse(state.offline)
        state = FanPredictionEngine.apply(state, FanEvent.Refresh(now + 1000))
        assertTrue(state.fixtures.isNotEmpty())
        assertEquals(1, state.standings.count { it.isYou })
    }

    @Test
    fun `refresh rolls a fresh window once the slate is done`() {
        var state = newState()
        val last = state.fixtures.maxOf { it.kickoffMs }
        state = FanPredictionEngine.apply(state, FanEvent.Refresh(last + 60_000))
        assertTrue(state.fixtures.all { it.kickoffMs > last + 60_000 })
        assertEquals(0, state.predictions.size)
        assertEquals(1, state.standings.count { it.isYou })
    }

    @Test
    fun `full state round trips through encode decode`() {
        var state = newState(seed = 21)
        val fixture = state.fixtures.first()
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME, 3, 0))
        state = FanPredictionEngine.apply(state, FanEvent.SetFavoriteTeam(FanSport.BASKETBALL, 13))
        val settled = FanPredictionEngine.settle(state, fixture.kickoffMs)
        val decoded = FanPredictionEngine.decode(FanPredictionEngine.encode(settled))
        assertNotNull(decoded)
        assertEquals(settled.fixtures, decoded!!.fixtures)
        assertEquals(settled.standings, decoded.standings)
        assertEquals(settled.predictions, decoded.predictions)
        assertEquals(settled.profile, decoded.profile)
    }
}
