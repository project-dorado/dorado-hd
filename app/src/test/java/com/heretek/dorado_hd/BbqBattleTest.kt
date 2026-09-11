package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BbqBattleEngine
import com.heretek.dorado_hd.ui.apps.games.BbqBattleState
import com.heretek.dorado_hd.ui.apps.games.BbqCreep
import com.heretek.dorado_hd.ui.apps.games.BbqCreepKind
import com.heretek.dorado_hd.ui.apps.games.BbqEvent
import com.heretek.dorado_hd.ui.apps.games.BbqTowerType
import com.heretek.dorado_hd.ui.apps.games.stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the BBQ Battle engine (no Android dependency). */
class BbqBattleTest {

    private fun richState(seed: Int = 7): BbqBattleState =
        BbqBattleEngine.newGame(seed).copy(credits = 999)

    @Test
    fun `bfs route runs from the entry to the food`() {
        val route = BbqBattleEngine.route(emptyList())
        assertEquals(11, route.size)
        assertEquals(0, route.first().x)
        assertEquals(3, route.first().y)
        assertEquals(10, route.last().x)
        assertEquals(3, route.last().y)
        route.zipWithNext().forEach { (a, b) ->
            assertEquals(1, kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y))
        }
    }

    @Test
    fun `blocking towers cannot seal the route`() {
        var state = richState()
        for (y in 0 until 7) {
            if (y == 3) continue
            state = BbqBattleEngine.build(state, 3, y, BbqTowerType.SPRAY)
        }
        assertEquals(6, state.towers.size)
        assertTrue(BbqBattleEngine.route(state.towers).isNotEmpty())
        val sealed = BbqBattleEngine.build(state, 3, 3, BbqTowerType.SPRAY)
        assertEquals(6, sealed.towers.size)
        assertTrue(sealed.events.contains(BbqEvent.INVALID))
    }

    @Test
    fun `gum does not block but zapper does`() {
        var state = richState()
        state = BbqBattleEngine.build(state, 5, 3, BbqTowerType.GUM)
        assertEquals(1, state.towers.size)
        assertTrue(BbqBattleEngine.route(state.towers).isNotEmpty())
        val zapper = BbqBattleEngine.build(state, 5, 3, BbqTowerType.ZAPPER)
        assertTrue(zapper.events.contains(BbqEvent.INVALID))
    }

    @Test
    fun `interest formula caps at twenty`() {
        assertEquals(10, BbqBattleEngine.interestFor(0, 0f))
        assertEquals(15, BbqBattleEngine.interestFor(40, 0f))
        assertEquals(18, BbqBattleEngine.interestFor(64, 0f))
        assertEquals(20, BbqBattleEngine.interestFor(80, 0f))
        assertEquals(20, BbqBattleEngine.interestFor(400, 0f))
        assertEquals(19, BbqBattleEngine.interestFor(40, 0.1f))
    }

    @Test
    fun `tower resale refunds half of build and upgrades`() {
        var state = richState()
        state = BbqBattleEngine.build(state, 4, 2, BbqTowerType.SPRAY)
        val fresh = state.towers.first()
        assertEquals(3, BbqBattleEngine.sellValue(fresh))
        state = BbqBattleEngine.upgrade(state, 4, 2)
        state = BbqBattleEngine.upgrade(state, 4, 2)
        assertEquals(6, BbqBattleEngine.sellValue(state.towers.first()))
        val sold = BbqBattleEngine.sell(state, 4, 2)
        assertTrue(sold.towers.isEmpty())
        assertEquals(state.credits + 6, sold.credits)
    }

    @Test
    fun `upgrade level caps at three and follows the cost curve`() {
        var state = richState()
        state = BbqBattleEngine.build(state, 6, 4, BbqTowerType.BOMB)
        val start = state.credits
        state = BbqBattleEngine.upgrade(state, 6, 4)
        state = BbqBattleEngine.upgrade(state, 6, 4)
        state = BbqBattleEngine.upgrade(state, 6, 4)
        assertEquals(3, state.towers.first().level)
        assertEquals(start - (6 + 8 + 10), state.credits)
        val refused = BbqBattleEngine.upgrade(state, 6, 4)
        assertEquals(3, refused.towers.first().level)
        assertTrue(refused.events.contains(BbqEvent.INVALID))
    }

    @Test
    fun `tower level stats follow the authored curves`() {
        val spray = BbqTowerType.SPRAY.stats(3)
        assertEquals(60f, spray.radius, 0.001f)
        assertEquals(10f, spray.damage, 0.001f)
        assertEquals(700L, spray.cooldownMs)
        val gum = BbqTowerType.GUM.stats(3)
        assertEquals(0.25f, gum.slowFactor, 0.001f)
        assertEquals(0.55f, gum.damage, 0.001f)
        val bomb = BbqTowerType.BOMB.stats(3)
        assertEquals(77f, bomb.radius, 0.001f)
        assertEquals(3.15f, bomb.damage, 0.001f)
        assertEquals(650L, bomb.cooldownMs)
        val zapper = BbqTowerType.ZAPPER.stats(3)
        assertEquals(112f, zapper.radius, 0.001f)
        assertEquals(75f, zapper.damage, 0.001f)
        assertEquals(750L, zapper.cooldownMs)
    }

    @Test
    fun `creep levels scale on repeats`() {
        assertEquals(1, BbqBattleEngine.spawnLevel(BbqCreepKind.ANT, 1, 0))
        assertEquals(11, BbqBattleEngine.spawnLevel(BbqCreepKind.ANT, 1, 1))
        assertEquals(27, BbqBattleEngine.spawnLevel(BbqCreepKind.ANT, 1, 2))
        assertEquals(0 + (8 + 1), BbqBattleEngine.spawnLevel(BbqCreepKind.SNAIL, 0, 1))
        assertEquals(0 + (7 + 1), BbqBattleEngine.spawnLevel(BbqCreepKind.CENTIPEDE, 0, 1))
        assertEquals(0 + (6 + 1), BbqBattleEngine.spawnLevel(BbqCreepKind.BEE, 0, 1))
    }

    @Test
    fun `creep stat curves match the spec`() {
        assertEquals(30f, BbqCreepKind.ANT.maxHp(0), 0.001f)
        assertEquals(50f, BbqCreepKind.ANT.maxHp(1), 0.001f)
        assertEquals(21.75f, BbqCreepKind.ANT.speed(1), 0.001f)
        assertEquals(85f, BbqCreepKind.SNAIL.maxHp(0), 0.001f)
        assertEquals(15f, BbqCreepKind.SNAIL.speed(0), 0.001f)
        assertEquals(150f, BbqCreepKind.BEE.maxHp(0), 0.001f)
        assertEquals(25f, BbqCreepKind.BEE.speed(0), 0.001f)
        assertTrue(BbqCreepKind.BEE.flying)
        assertEquals(200f, BbqCreepKind.CENTIPEDE.speed(100), 0.001f)
    }

    @Test
    fun `wave cadence matches the authored schedule`() {
        assertEquals(30, BbqBattleEngine.waves.size)
        assertEquals(5, BbqBattleEngine.waveSpawns(0, 0).size)
        assertEquals(10, BbqBattleEngine.waveSpawns(0, 1).size)
        assertEquals(1000L, BbqBattleEngine.spawnIntervalMs(0, 0))
        assertEquals(500L, BbqBattleEngine.spawnIntervalMs(0, 1))
        assertEquals(8_000L, BbqBattleEngine.waveTotalMs(0, 0))
        assertEquals(1, BbqBattleEngine.previewFor(0).size)
        assertEquals(4, BbqBattleEngine.previewFor(9).size)
    }

    @Test
    fun `easy mode grants a free upgrade on build`() {
        val easy = BbqBattleEngine.newGame(3, easyMode = true).copy(credits = 100)
        val built = BbqBattleEngine.build(easy, 2, 2, BbqTowerType.SPRAY)
        assertEquals(1, built.towers.first().level)
    }

    @Test
    fun `creeps biting the food drain hp and end the run`() {
        val base = BbqBattleEngine.newGame(5).copy(credits = 100)
        val (gx, gy) = BbqBattleEngine.center(10, 3)
        fun bite(food: Int) = base.copy(
            foodHp = food,
            creeps = listOf(
                BbqCreep(
                    id = 99,
                    kind = BbqCreepKind.ANT,
                    level = 0,
                    x = gx,
                    y = gy,
                    hp = 30f,
                    maxHp = 30f,
                    pathIndex = base.route.size - 1,
                ),
            ),
        )
        val after = BbqBattleEngine.step(bite(2), 100)
        assertEquals(1, after.foodHp)
        assertTrue(after.creeps.isEmpty())

        val final = BbqBattleEngine.step(bite(1), 100)
        assertTrue(final.over)
        assertEquals(0, final.foodHp)
        assertTrue(final.events.contains(BbqEvent.GAME_OVER))
    }

    @Test
    fun `milestone choices apply the authored bonuses`() {
        val pending = BbqBattleEngine.newGame(9).copy(milestone = 10)
        val damage = BbqBattleEngine.chooseBonus(pending, 0)
        assertEquals(1.1f, damage.bonuses.damageIncrease, 0.001f)
        assertEquals(null, damage.milestone)
        val cost = BbqBattleEngine.chooseBonus(pending, 1)
        assertEquals(1, cost.bonuses.costReduction)
        val late = BbqBattleEngine.newGame(9).copy(milestone = 30)
        assertEquals(1, BbqBattleEngine.chooseBonus(late, 1).bonuses.upgradeCostReduction)
    }

    @Test
    fun `autosave round trip preserves the run`() {
        var state = BbqBattleEngine.newGame(21, easyMode = false).copy(credits = 200)
        state = BbqBattleEngine.build(state, 1, 1, BbqTowerType.ZAPPER)
        state = BbqBattleEngine.upgrade(state, 1, 1)
        state = BbqBattleEngine.step(state, 6_000)
        val blob = BbqBattleEngine.encode(state)
        val decoded = BbqBattleEngine.decode(blob)
        assertNotNull(decoded)
        assertEquals(state.credits, decoded!!.credits)
        assertEquals(state.foodHp, decoded.foodHp)
        assertEquals(state.completedWaves, decoded.completedWaves)
        assertEquals(state.waveIndex, decoded.waveIndex)
        assertEquals(state.towers, decoded.towers)
        assertEquals(state.creeps.size, decoded.creeps.size)
        assertEquals(state.bonuses, decoded.bonuses)
        assertFalse(decoded.over)
    }

    @Test
    fun `ticks are deterministic for the same seed`() {
        val a = BbqBattleEngine.newGame(123)
        val b = BbqBattleEngine.newGame(123)
        assertEquals(a, b)
        var left = a
        var right = b
        repeat(50) {
            left = BbqBattleEngine.step(left, 100)
            right = BbqBattleEngine.step(right, 100)
        }
        assertEquals(left, right)
    }
}
