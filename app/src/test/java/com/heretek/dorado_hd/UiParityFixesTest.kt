package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.AnimalgramsEngine
import com.heretek.dorado_hd.ui.apps.games.BbqBattleEngine
import com.heretek.dorado_hd.ui.apps.games.BbqTowerType
import com.heretek.dorado_hd.ui.apps.games.BeesEngine
import com.heretek.dorado_hd.ui.apps.games.BeesMode
import com.heretek.dorado_hd.ui.apps.games.CastlesCoin
import com.heretek.dorado_hd.ui.apps.games.CastlesEngine
import com.heretek.dorado_hd.ui.apps.games.CastlesProgress
import com.heretek.dorado_hd.ui.apps.games.FanEvent
import com.heretek.dorado_hd.ui.apps.games.FanPick
import com.heretek.dorado_hd.ui.apps.games.FanPredictionEngine
import com.heretek.dorado_hd.ui.apps.games.SpaceBattleEngine
import com.heretek.dorado_hd.ui.apps.games.VineClimbEngine
import com.heretek.dorado_hd.ui.apps.games.WordMongerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the Phase 1 UI-parity P0 fixes and the small pure helpers
 * that back the layout/input repairs. Engine-level where the defect is
 * expressible without Android.
 */
class UiParityFixesTest {

    // ------------------------------------------------------------- bbq battle --

    @Test
    fun `bbq run token is stable while towers are rebuilt every step`() {
        var state = BbqBattleEngine.newGame(7).copy(credits = 999)
        state = BbqBattleEngine.build(state, 4, 2, BbqTowerType.SPRAY)
        val token = BbqBattleEngine.runToken(state)
        val after = BbqBattleEngine.step(state, 16)
        assertNotSame("the engine rebuilds the tower list each step", state.towers, after.towers)
        assertEquals("the gesture key must not change while the run advances", token, BbqBattleEngine.runToken(after))
    }

    @Test
    fun `bbq end panel reports the completed defence as victory`() {
        assertEquals("paused", BbqBattleEngine.outcomeLabel(BbqBattleEngine.newGame(1)))
        val lost = BbqBattleEngine.newGame(1).copy(over = true, completedWaves = 3)
        assertEquals("food eaten · game over", BbqBattleEngine.outcomeLabel(lost))
        val won = BbqBattleEngine.newGame(1).copy(over = true, completedWaves = BbqBattleEngine.WIN_WAVES)
        assertTrue(BbqBattleEngine.outcomeLabel(won).contains("victory"))
    }

    // ------------------------------------------------------------------- bees --

    @Test
    fun `bees run token is stable while nodes are rebuilt every step`() {
        val state = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 99)
        val token = BeesEngine.runToken(state)
        val after = BeesEngine.step(state, 16)
        assertNotSame("the engine rebuilds the node list each step", state.nodes, after.nodes)
        assertEquals("the gesture key must not change while the run advances", token, BeesEngine.runToken(after))
    }

    // ------------------------------------------------------------- vine climb --

    @Test
    fun `vine climb input advances the latest run not the captured start`() {
        val initial = VineClimbEngine.newGame(seed = 42)
        val latest = VineClimbEngine.tick(initial, VineClimbEngine.Input(left = true))
        assertEquals(0, latest.lane)

        // A live handler reads the latest state: the second tap lands while the
        // first hop is still in flight, so the lane must not change again.
        val correct = VineClimbEngine.tick(latest, VineClimbEngine.Input(right = true))
        assertEquals(0, correct.lane)

        // A handler still holding the first composition would rewind to the
        // start and jump to lane two instead.
        val stale = VineClimbEngine.tick(initial, VineClimbEngine.Input(right = true))
        assertEquals(2, stale.lane)
        assertNotEquals(correct.lane, stale.lane)
    }

    // ------------------------------------------------ castles and cannons ----

    @Test
    fun `castle coin taps use the live camera after panning`() {
        val state = CastlesEngine.newBattle(1, CastlesProgress(), 5).copy(
            coins = listOf(CastlesCoin(1, 440f, 7)),
        )
        val pannedWorldX = CastlesEngine.screenToWorldX(
            screenX = 240f,
            viewWidth = 480f,
            viewHeight = 200f,
            camera = 200f,
        )
        assertEquals(440f, pannedWorldX, 0.001f)
        assertEquals(state.gold + 7, CastlesEngine.collectCoins(state, pannedWorldX, 150f).gold)

        // The pre-pan projection lands somewhere else and must collect nothing.
        val staleWorldX = CastlesEngine.screenToWorldX(240f, 480f, 200f, camera = 0f)
        assertEquals(240f, staleWorldX, 0.001f)
        assertEquals(state.gold, CastlesEngine.collectCoins(state, staleWorldX, 150f).gold)
        assertNotEquals(pannedWorldX, staleWorldX)
    }

    // ------------------------------------------------------------- wordmonger --

    @Test
    fun `wordmonger mid-run snapshots round trip through the codec`() {
        var state = WordMongerEngine.newGame(1, 4242, tutorial = false)
        repeat(30) { state = WordMongerEngine.step(state, 20) }
        assertFalse(state.gameOver)
        val decoded = WordMongerEngine.decode(WordMongerEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(state.score, decoded!!.score)
        assertEquals(state.level, decoded.level)
        assertEquals(state.levelMs, decoded.levelMs)
        assertEquals(state.words, decoded.words)
        assertFalse(decoded.gameOver)
    }

    // ---------------------------------------------------------- fan prediction --

    @Test
    fun `score keypad appends digits up to two places`() {
        assertEquals("1", FanPredictionEngine.appendScoreDigit("", "1"))
        assertEquals("12", FanPredictionEngine.appendScoreDigit("1", "2"))
        assertEquals("12", FanPredictionEngine.appendScoreDigit("12", "3"))
        assertEquals("7", FanPredictionEngine.appendScoreDigit("7", "x"))
    }

    @Test
    fun `two digit predictions are accepted by the engine`() {
        var state = FanPredictionEngine.newState("device", 1_700_000_000_000L, 7)
        val fixture = state.fixtures.first()
        state = FanPredictionEngine.apply(state, FanEvent.MakePrediction(fixture.id, FanPick.HOME, 12, 3))
        val prediction = state.predictions[fixture.id]
        assertNotNull(prediction)
        assertEquals(12, prediction!!.homeScore)
        assertEquals(3, prediction.awayScore)
    }

    // ----------------------------------------------------------- space battle --

    @Test
    fun `space battle viewport letterboxes without negative offsets`() {
        val wide = SpaceBattleEngine.viewportFor(544f, 480f)
        assertEquals(1f, wide.scale, 0.001f)
        assertEquals(136f, wide.ox, 0.001f)
        assertEquals(0f, wide.oy, 0.001f)
        assertEquals(136f, wide.screenX(0f, 0f), 0.001f)

        val short = SpaceBattleEngine.viewportFor(272f, 300f)
        assertEquals(300f / 480f, short.scale, 0.001f)
        assertTrue(short.ox >= 0f)
        assertTrue(short.oy >= 0f)
        // The view is centred: equal letterbox on both sides.
        assertEquals(272f, 2f * short.ox + SpaceBattleEngine.VIEW_W * short.scale, 0.001f)

        val tall = SpaceBattleEngine.viewportFor(272f, 960f)
        assertTrue(tall.oy >= 0f)
        assertEquals(960f, 2f * tall.oy + SpaceBattleEngine.VIEW_H * tall.scale, 0.001f)
    }

    // ------------------------------------------------------------ animalgrams --

    @Test
    fun `animalgrams sound option stars the active choice`() {
        val on = AnimalgramsEngine.soundOptionLabels(soundOn = true)
        assertEquals("on*", on.first)
        assertEquals("off", on.second)
        val off = AnimalgramsEngine.soundOptionLabels(soundOn = false)
        assertEquals("on", off.first)
        assertEquals("off*", off.second)
    }
}
