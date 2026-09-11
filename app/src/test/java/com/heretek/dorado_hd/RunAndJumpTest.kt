package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.RnJBody
import com.heretek.dorado_hd.ui.apps.games.RnJEngine
import com.heretek.dorado_hd.ui.apps.games.RnJKind
import com.heretek.dorado_hd.ui.apps.games.RnJLevel
import com.heretek.dorado_hd.ui.apps.games.RnJLevels
import com.heretek.dorado_hd.ui.apps.games.RnJPoint
import com.heretek.dorado_hd.ui.apps.games.RnJProgress
import com.heretek.dorado_hd.ui.apps.games.RnJState
import com.heretek.dorado_hd.ui.apps.games.RnJStat
import com.heretek.dorado_hd.ui.apps.games.RnJTier
import com.heretek.dorado_hd.ui.apps.games.RnJTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RunAndJumpTest {

    private fun customLevel(
        cols: Int = 10,
        rows: Int = 9,
        spawnX: Double = 48.0,
        spawnY: Double = 7 * 32.0 - RnJEngine.PLAYER_H / 2,
        tiles: List<RnJTile> = emptyList(),
        coins: List<RnJPoint> = emptyList(),
        id: Int = 900,
    ): RnJLevel = RnJLevel(
        id = id,
        name = "test",
        tier = RnJTier.TUTORIAL,
        cols = cols,
        rows = rows,
        spawnX = spawnX,
        spawnY = spawnY,
        coinsToUnlock = 0,
        hint = "",
        tiles = tiles,
        coins = coins,
    )

    @Test
    fun `fixed substeps are deterministic`() {
        val level = RnJLevels.byId(1)
        val a = RnJEngine.newGame(level)
        val b = RnJEngine.newGame(level)
        val first = RnJEngine.step(a, level, 250L)
        val second = RnJEngine.step(b, level, 250L)
        assertEquals(first, second)
        assertEquals(first.body.vy, second.body.vy, 0.0)
    }

    @Test
    fun `terminal velocity is clamped`() {
        val level = customLevel(cols = 4, rows = 400, spawnX = 64.0, spawnY = 0.0)
        var state = RnJEngine.newGame(level)
        repeat(80) { state = RnJEngine.step(state, level, 100L) }
        val terminal = RnJEngine.terminalVelocity(RnJEngine.SLOW_MOTION_NORMAL)
        assertEquals(terminal, state.body.vy, 1e-6)
        assertTrue(state.body.vy <= terminal + 1e-9)
    }

    @Test
    fun `duck holds 850 ms then unducks after 50 ms`() {
        val level = RnJLevels.byId(1)
        var state = RnJEngine.newGame(level).copy(currentBoostId = 1)
        state = RnJEngine.tap(state, level)
        assertTrue(RnJEngine.isDucking(state))
        assertEquals(RnJEngine.DUCK_HOLD_MS, state.duckRemainingMs)

        state = RnJEngine.step(state, level, 800L)
        assertTrue(RnJEngine.isDucking(state))

        state = RnJEngine.step(state, level, 50L)
        assertTrue(RnJEngine.isDucking(state))
        assertEquals(RnJEngine.UNDUCK_MS, state.unduckRemainingMs)

        state = RnJEngine.step(state, level, 50L)
        assertFalse(RnJEngine.isDucking(state))
    }

    @Test
    fun `walking across floor seams does not kill`() {
        val tiles = mutableListOf<RnJTile>()
        for (col in 0 until 10) {
            tiles += RnJTile(col, 8, RnJKind.SOLID)
            tiles += RnJTile(col, 7, RnJKind.BOOST, id = 0)
        }
        val level = customLevel(
            tiles = tiles,
            spawnX = 3 * 32.0 + 16.0,
            spawnY = 8 * 32.0 - RnJEngine.PLAYER_H / 2,
        )
        var state = RnJEngine.newGame(level)
        state = RnJEngine.step(state, level, 100L)
        assertEquals(0, state.currentBoostId)
        repeat(20) { step ->
            state = state.copy(body = state.body.copy(vx = 200.0))
            state = RnJEngine.step(state, level, 50L)
            assertTrue("seam crossing must not kill at step $step", state.deadMs <= 0L)
        }
    }

    @Test
    fun `side contact kills the player`() {
        val level = customLevel(
            tiles = listOf(RnJTile(3, 4, RnJKind.SOLID)),
            spawnX = 84.0,
            spawnY = 4 * 32.0 + 16.0,
        )
        var state = RnJEngine.newGame(level)
        state = state.copy(body = RnJBody(x = 84.0, y = 4 * 32.0 + 16.0, vx = 300.0))
        state = RnJEngine.step(state, level, 100L)
        assertTrue("side contact must start the death countdown", state.deadMs > 0L)
    }

    @Test
    fun `standing on a null pad kills`() {
        val level = customLevel(
            tiles = listOf(
                RnJTile(2, 8, RnJKind.SOLID),
                RnJTile(2, 7, RnJKind.BOOST, id = 8),
            ),
            spawnX = 2 * 32.0 + 16.0,
            spawnY = 8 * 32.0 - RnJEngine.PLAYER_H / 2,
        )
        val state = RnJEngine.newGame(level)
        val next = RnJEngine.step(state, level, 20L)
        assertTrue("id 8 offers no safe boost", next.deadMs > 0L)
        assertEquals("deaths only count after the first tap", 0, next.deaths)
    }

    @Test
    fun `warp mapping is id minus sixty four with sibling target`() {
        val level = customLevel(
            tiles = listOf(
                RnJTile(1, 6, RnJKind.WARP, id = 64, siblingCol = 5, siblingRow = 6),
                RnJTile(5, 6, RnJKind.WARP, id = 65, siblingCol = 1, siblingRow = 6),
            ),
        )
        assertEquals(0, RnJEngine.warpEntityId(64))
        assertEquals(1, RnJEngine.warpEntityId(65))
        val target = RnJEngine.warpTarget(level, 64)
        assertNotNull(target)
        assertEquals(5 * 32.0 + 16.0, target!!.x, 1e-9)
        assertEquals(6 * 32.0 + 16.0, target.y, 1e-9)

        var state = RnJEngine.newGame(level).copy(
            currentBoostId = 64,
            body = RnJBody(x = 1 * 32.0 + 16.0, y = 6 * 32.0 + 16.0),
        )
        state = RnJEngine.tap(state, level)
        assertEquals(5 * 32.0 + 16.0, state.body.x, 1e-9)
    }

    @Test
    fun `switch pad toggles block solidity`() {
        val first = RnJTile(3, 6, RnJKind.SWITCH, variant = 0, group = 0)
        val second = RnJTile(4, 6, RnJKind.SWITCH, variant = 1, group = 0)
        val purple = RnJTile(5, 6, RnJKind.SWITCH, variant = 0, group = 1)
        val level = customLevel(tiles = listOf(first, second, purple))
        var state = RnJEngine.newGame(level).copy(currentBoostId = 13)
        assertTrue(RnJEngine.isSolid(first, state, level))
        assertFalse(RnJEngine.isSolid(second, state, level))
        state = RnJEngine.tap(state, level)
        assertFalse(RnJEngine.isSolid(first, state, level))
        assertTrue(RnJEngine.isSolid(second, state, level))
        assertTrue("purple group is untouched by the pink pad", RnJEngine.isSolid(purple, state, level))
    }

    @Test
    fun `coin milestones unlock level groups at 31 85 135 220`() {
        assertEquals(6, RnJEngine.unlockedCount(0))
        assertEquals(6, RnJEngine.unlockedCount(30))
        assertEquals(20, RnJEngine.unlockedCount(31))
        assertEquals(20, RnJEngine.unlockedCount(84))
        assertEquals(40, RnJEngine.unlockedCount(85))
        assertEquals(40, RnJEngine.unlockedCount(134))
        assertEquals(55, RnJEngine.unlockedCount(135))
        assertEquals(55, RnJEngine.unlockedCount(219))
        assertEquals(58, RnJEngine.unlockedCount(220))
    }

    @Test
    fun `tap boost is rate limited to a quarter second`() {
        val level = RnJLevels.byId(1)
        var state = RnJEngine.newGame(level).copy(currentBoostId = 0)
        state = RnJEngine.tap(state, level)
        val firstVelocity = state.body.vy
        assertEquals(-RnJEngine.rootVelocity(), firstVelocity, 1e-6)
        assertEquals(RnJEngine.BOOST_COOLDOWN_MS, state.boostCooldownMs)

        state = RnJEngine.tap(state, level)
        assertEquals("second tap inside the window is ignored", firstVelocity, state.body.vy, 0.0)

        state = RnJEngine.step(state, level, 260L)
        state = state.copy(currentBoostId = 0)
        state = RnJEngine.tap(state, level)
        assertEquals(-RnJEngine.rootVelocity(), state.body.vy, 1e-6)
    }

    @Test
    fun `level set matches the shipped structure`() {
        val levels = RnJLevels.all
        assertEquals(58, levels.size)
        assertEquals(6, levels.count { it.tier == RnJTier.TUTORIAL })
        assertEquals(14, levels.count { it.tier == RnJTier.EASY })
        assertEquals(20, levels.count { it.tier == RnJTier.MEDIUM })
        assertEquals(15, levels.count { it.tier == RnJTier.HARD })
        assertEquals(3, levels.count { it.tier == RnJTier.EXTREME })
        assertEquals((1..58).toList(), levels.map { it.id })
        assertTrue(levels.all { it.coins.isNotEmpty() })
        assertTrue(levels.all { level -> level.tiles.any { it.kind == RnJKind.GOAL } })
    }

    @Test
    fun `goal finishes and flags missed coins`() {
        val level = customLevel(
            tiles = listOf(RnJTile(3, 3, RnJKind.GOAL)),
            coins = listOf(RnJPoint(200.0, 200.0)),
        )
        var state = RnJEngine.newGame(level)
        state = state.copy(body = RnJBody(3 * 32.0 + 16.0, 3 * 32.0 + 16.0))
        state = RnJEngine.step(state, level, 17L)
        assertTrue(state.finished)
        assertTrue(state.missedCoins)
    }

    @Test
    fun `progress survives an encode decode round trip`() {
        val progress = RnJProgress(
            stats = mapOf(
                1 to RnJStat(coins = 3, deaths = 2, completed = true),
                7 to RnJStat(coins = 8, deaths = 1, beatFast = true, allCoinsFast = true, completed = true),
            ),
            lastLevel = 7,
            sfxLevel = 1,
            fast = true,
        )
        val decoded = RnJEngine.decodeProgress(RnJEngine.encodeProgress(progress))
        assertNotNull(decoded)
        assertEquals(progress, decoded)
    }

    @Test
    fun `total coins drive progression`() {
        var progress = RnJProgress()
        assertEquals(0, RnJEngine.totalCoins(progress))
        progress = progress.copy(stats = mapOf(1 to RnJStat(coins = 31)))
        assertEquals(31, RnJEngine.totalCoins(progress))
        val easy = RnJLevels.byId(7)
        assertTrue(RnJEngine.isLevelUnlocked(progress, easy))
        val medium = RnJLevels.byId(21)
        assertFalse(RnJEngine.isLevelUnlocked(progress, medium))
        assertTrue(abs(RnJEngine.terminalVelocity(1.0) - 512.0) < 1e-9)
    }
}
