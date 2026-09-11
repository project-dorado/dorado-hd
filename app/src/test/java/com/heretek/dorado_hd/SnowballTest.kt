package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SnowLoseReason
import com.heretek.dorado_hd.ui.apps.games.SnowMode
import com.heretek.dorado_hd.ui.apps.games.SnowState
import com.heretek.dorado_hd.ui.apps.games.SnowStatus
import com.heretek.dorado_hd.ui.apps.games.SnowTile
import com.heretek.dorado_hd.ui.apps.games.SnowTileKind
import com.heretek.dorado_hd.ui.apps.games.SnowTilePhase
import com.heretek.dorado_hd.ui.apps.games.SnowballEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SnowballTest {

    private val frame = 17L

    @Test
    fun `board is eight by thirteen thirty four pixel cells`() {
        assertEquals(8, SnowballEngine.COLUMNS)
        assertEquals(13, SnowballEngine.ROWS)
        assertEquals(34.0, SnowballEngine.BLOCK, 1e-9)
        assertEquals(25, SnowballEngine.CAMPAIGN_LEVELS)
        assertEquals(2500L, SnowballEngine.FADE_MS)
        assertEquals(3500L, SnowballEngine.RESPAWN_MS)
    }

    @Test
    fun `tile cycles normal fading kill respawning`() {
        var tile = SnowTile(SnowTileKind.NORMAL, SnowTilePhase.FADING)
        tile = SnowballEngine.tickTile(tile, SnowballEngine.FADE_MS, SnowballEngine.FADE_MS, SnowballEngine.RESPAWN_MS)
        assertEquals(SnowTilePhase.FADING, tile.phase)
        assertEquals(1f, tile.alpha, 1e-6f)

        tile = SnowballEngine.tickTile(tile, frame, SnowballEngine.FADE_MS, SnowballEngine.RESPAWN_MS)
        assertTrue("alpha should start dropping 0.1 per frame", tile.alpha < 1f && tile.alpha > 0.8f)

        var guard = 0
        while (tile.phase == SnowTilePhase.FADING && guard++ < 60) {
            tile = SnowballEngine.tickTile(tile, frame, SnowballEngine.FADE_MS, SnowballEngine.RESPAWN_MS)
        }
        assertEquals(SnowTilePhase.KILL, tile.phase)
        assertEquals(0f, tile.alpha, 1e-6f)

        tile = SnowballEngine.tickTile(tile, SnowballEngine.RESPAWN_MS, SnowballEngine.FADE_MS, SnowballEngine.RESPAWN_MS)
        assertEquals(SnowTilePhase.RESPAWNING, tile.phase)

        guard = 0
        while (tile.phase == SnowTilePhase.RESPAWNING && guard++ < 60) {
            tile = SnowballEngine.tickTile(tile, frame, SnowballEngine.FADE_MS, SnowballEngine.RESPAWN_MS)
        }
        assertEquals(SnowTilePhase.NORMAL, tile.phase)
        assertEquals(1f, tile.alpha, 1e-6f)
    }

    @Test
    fun `standing on a killed tile ends the run`() {
        val base = SnowballEngine.newCampaign(1, seed = 7)
        val col = (base.playerX / SnowballEngine.BLOCK).toInt()
        val row = (base.playerY / SnowballEngine.BLOCK).toInt()
        val index = row * SnowballEngine.COLUMNS + col
        val tiles = base.tiles.toMutableList()
        tiles[index] = SnowTile(SnowTileKind.NORMAL, SnowTilePhase.KILL, 0L, 0f)
        val next = SnowballEngine.step(base.copy(tiles = tiles), frame, 0.0, 0.0)
        assertEquals(SnowStatus.LOST, next.status)
        assertEquals(SnowLoseReason.HOLE, next.loseReason)
    }

    @Test
    fun `rolling over a normal tile starts its fade`() {
        val base = SnowballEngine.newCampaign(1, seed = 7)
        val col = (base.playerX / SnowballEngine.BLOCK).toInt()
        val row = (base.playerY / SnowballEngine.BLOCK).toInt()
        val index = row * SnowballEngine.COLUMNS + col
        assertEquals(SnowTileKind.SAFE, base.tiles[index].kind)
        val other = base.tiles.indexOfFirst { it.kind == SnowTileKind.NORMAL }
        val otherCol = other % SnowballEngine.COLUMNS
        val otherRow = other / SnowballEngine.COLUMNS
        val moved = base.copy(
            playerX = otherCol * SnowballEngine.BLOCK + SnowballEngine.BLOCK / 2,
            playerY = otherRow * SnowballEngine.BLOCK + SnowballEngine.BLOCK / 2,
        )
        val next = SnowballEngine.step(moved, frame, 0.0, 0.0)
        assertEquals(SnowTilePhase.FADING, next.tiles[other].phase)
    }

    @Test
    fun `campaign rejects out of order number coins`() {
        val base = SnowballEngine.newCampaign(3, seed = 11)
        val wrong = base.coins.first { it.number == 1 }
        val next = SnowballEngine.step(base.copy(playerX = wrong.x, playerY = wrong.y), frame, 0.0, 0.0)
        assertEquals(SnowStatus.LOST, next.status)
        assertEquals(SnowLoseReason.WRONG_COIN, next.loseReason)
    }

    @Test
    fun `campaign takes descending coins and wins on the last`() {
        var state = SnowballEngine.newCampaign(2, seed = 5)
        val total = state.coins.size
        var remaining = total
        while (state.coins.isNotEmpty()) {
            val coin = state.coins.maxByOrNull { it.number } ?: break
            assertEquals(remaining, coin.number)
            state = SnowballEngine.step(state.copy(playerX = coin.x, playerY = coin.y), frame, 0.0, 0.0)
            remaining--
            assertEquals(remaining, state.coins.size)
            if (state.coins.isNotEmpty()) assertEquals(remaining, state.nextCoin)
        }
        assertEquals(SnowStatus.WON, state.status)
        assertEquals(total * SnowballEngine.CAMPAIGN_COIN_POINTS, state.score)
    }

    @Test
    fun `survival awards a thousand and keeps twelve coins`() {
        var state = SnowballEngine.newSurvival(seed = 42)
        assertEquals(SnowballEngine.SURVIVAL_COINS, state.coins.size)
        val target = state.coins.first()
        state = state.copy(playerX = target.x, playerY = target.y)
        val before = state.score
        val next = SnowballEngine.step(state, frame, 0.0, 0.0)
        assertEquals(before + SnowballEngine.SURVIVAL_STAR_POINTS, next.score)
        assertEquals(SnowballEngine.SURVIVAL_COINS, next.coins.size)
    }

    @Test
    fun `survival replacement respects margins and centre exclusion`() {
        var state = SnowballEngine.newSurvival(seed = 9)
        repeat(12) { index ->
            val target = state.coins.first { it.id == index }
            state = state.copy(playerX = target.x, playerY = target.y)
            state = SnowballEngine.step(state, frame, 0.0, 0.0)
            val fresh = state.coins.first { it.id == index }
            assertTrue(fresh.x >= SnowballEngine.COIN_EDGE_MARGIN)
            assertTrue(fresh.x <= SnowballEngine.BOARD_WIDTH - SnowballEngine.COIN_EDGE_MARGIN)
            assertTrue(fresh.y >= SnowballEngine.COIN_EDGE_MARGIN)
            assertTrue(fresh.y <= SnowballEngine.BOARD_HEIGHT - SnowballEngine.COIN_EDGE_MARGIN)
            val inCentreBox = abs(fresh.x - SnowballEngine.BOARD_WIDTH / 2) <= SnowballEngine.CENTER_EXCLUSION &&
                abs(fresh.y - SnowballEngine.BOARD_HEIGHT / 2) <= SnowballEngine.CENTER_EXCLUSION
            assertTrue("replacement must be outside the centre box", !inCentreBox)
        }
    }

    @Test
    fun `fade cycle shortens fade and lengthens respawn`() {
        val state = SnowballEngine.newSurvival(seed = 1)
        val cycled = SnowballEngine.cycleFade(state)
        assertEquals(SnowballEngine.FADE_MS - 1, cycled.fadeMs)
        assertEquals(SnowballEngine.RESPAWN_MS + 15, cycled.respawnMs)
        assertEquals(1, cycled.fadeCycles)
    }

    @Test
    fun `five level blocks unlock the next five`() {
        assertEquals(1, SnowballEngine.highestUnlocked(emptySet()))
        assertEquals(2, SnowballEngine.highestUnlocked(setOf(1)))
        assertEquals(5, SnowballEngine.highestUnlocked(setOf(1, 2, 3, 4)))
        assertEquals(10, SnowballEngine.highestUnlocked(setOf(1, 2, 3, 4, 5)))
        assertEquals(7, SnowballEngine.highestUnlocked(setOf(1, 2, 3, 4, 5, 6)))
        assertEquals(15, SnowballEngine.highestUnlocked((1..10).toSet()))
        assertEquals(25, SnowballEngine.highestUnlocked((1..25).toSet()))
    }

    @Test
    fun `level codes read as block and index`() {
        assertEquals("1-1", SnowballEngine.levelCode(1))
        assertEquals("1-5", SnowballEngine.levelCode(5))
        assertEquals("2-2", SnowballEngine.levelCode(7))
        assertEquals("5-5", SnowballEngine.levelCode(25))
    }

    @Test
    fun `identical inputs are deterministic`() {
        val first = SnowballEngine.newCampaign(5, seed = 99)
        var a: SnowState = first
        var b: SnowState = first
        repeat(200) {
            a = SnowballEngine.step(a, 16L, 0.3, -0.2)
            b = SnowballEngine.step(b, 16L, 0.3, -0.2)
        }
        assertEquals(a, b)
        assertTrue(a.mode == SnowMode.CAMPAIGN)
    }

    @Test
    fun `no-sensor touch vector rolls the ball`() {
        val start = SnowballEngine.newCampaign(1, seed = 3)
        val next = SnowballEngine.step(start, 200L, 0.6, 0.0)
        assertTrue("a drag vector must move the ball", next.playerX > start.playerX)
        val still = SnowballEngine.step(start, 200L, 0.0, 0.0)
        assertEquals(start.playerX, still.playerX, 1e-9)
        assertEquals(start.playerY, still.playerY, 1e-9)
    }
}
