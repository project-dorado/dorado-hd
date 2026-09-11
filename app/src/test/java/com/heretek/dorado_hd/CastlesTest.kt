package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.CastlesBackdrop
import com.heretek.dorado_hd.ui.apps.games.CastlesBossType
import com.heretek.dorado_hd.ui.apps.games.CastlesEngine
import com.heretek.dorado_hd.ui.apps.games.CastlesEvent
import com.heretek.dorado_hd.ui.apps.games.CastlesProgress
import com.heretek.dorado_hd.ui.apps.games.CastlesSkillTree
import com.heretek.dorado_hd.ui.apps.games.CastlesUnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Castles and Cannons engine (no Android dependency). */
class CastlesTest {

    private val progress = CastlesProgress()

    @Test
    fun `launch speed clamps between 150 and 450`() {
        assertEquals(150f, CastlesEngine.launchSpeed(0f), 0.001f)
        assertEquals(185f, CastlesEngine.launchSpeed(100f), 0.001f)
        assertEquals(450f, CastlesEngine.launchSpeed(1_000f), 0.001f)
        val (vx, vy) = CastlesEngine.launchVelocity(0f, -100f)
        assertEquals(0f, vx, 0.001f)
        assertEquals(-231.25f, vy, 0.001f)
    }

    @Test
    fun `cannonball falls under gravity and impacts at the ground line`() {
        val battle = CastlesEngine.newBattle(1, progress, seed = 1)
        val ball = com.heretek.dorado_hd.ui.apps.games.CastlesBall(
            id = 99,
            side = com.heretek.dorado_hd.ui.apps.games.CastlesSide.HUMAN,
            x = 120f,
            y = 150f,
            vx = 0f,
            vy = -100f,
            damage = 100f,
        )
        var state = battle.copy(balls = listOf(ball))
        repeat(3) { state = CastlesEngine.step(state, 100) }
        assertEquals(1, state.balls.size)
        assertTrue(state.balls.first().y > 150f)
        var impacted = false
        repeat(7) {
            state = CastlesEngine.step(state, 100)
            if (state.events.contains(CastlesEvent.CANNON_IMPACT)) impacted = true
        }
        assertTrue(state.balls.isEmpty())
        assertTrue(impacted)
    }

    @Test
    fun `splash damage falls off with distance and armor`() {
        assertEquals(100f, CastlesEngine.splashDamage(100f, 0f, 0f, 60f), 0.001f)
        assertEquals(50f, CastlesEngine.splashDamage(100f, 50f, 0f, 60f), 0.001f)
        assertEquals(33.333f, CastlesEngine.splashDamage(100f, 0f, 30f, 60f), 0.01f)
        assertEquals(0f, CastlesEngine.splashDamage(100f, 0f, 60f, 60f), 0.001f)
        assertEquals(0f, CastlesEngine.splashDamage(100f, 0f, 200f, 60f), 0.001f)
        assertEquals(2f * 100f, CastlesEngine.splashDamage(100f, -100f, 0f, 60f), 0.001f)
    }

    @Test
    fun `unit armor reduces damage`() {
        assertEquals(9f, CastlesEngine.splashDamage(10f, 10f, 0f, 20f), 0.001f)
        assertEquals(6f, CastlesEngine.splashDamage(10f, 40f, 0f, 20f), 0.001f)
    }

    @Test
    fun `score formula computes time offense and defense`() {
        assertEquals(650, CastlesEngine.score(180f, 0, 0, 0, 400f, 400f))
        assertEquals(0, CastlesEngine.score(400f, 0, 0, 0, 0f, 400f))
        val full = CastlesEngine.score(180f, 40, 40, 200, 400f, 400f)
        assertEquals(250 + (325 + 100) + (400 + 100), full)
        assertTrue(CastlesEngine.isGoldMedal(1000))
        assertFalse(CastlesEngine.isGoldMedal(999))
    }

    @Test
    fun `cannon drag fires and respects cooldown`() {
        val battle = CastlesEngine.newBattle(1, progress, seed = 6)
        val short = CastlesEngine.fireCannon(battle, 2f, 2f)
        assertTrue(short.balls.isEmpty())
        assertTrue(short.events.contains(CastlesEvent.INVALID))
        val fired = CastlesEngine.fireCannon(battle, 0f, -120f)
        assertEquals(1, fired.balls.size)
        assertEquals(1, fired.cannonsFired)
        assertTrue(fired.cannonCooldownMs > 0L)
        val again = CastlesEngine.fireCannon(fired, 0f, -120f)
        assertTrue(again.events.contains(CastlesEvent.INVALID))
        assertEquals(1, again.cannonsFired)
    }

    @Test
    fun `gold ticks once per interval and caps at 999`() {
        val battle = CastlesEngine.newBattle(1, progress, seed = 2)
        val ticked = CastlesEngine.step(battle, 10_000)
        assertEquals(battle.gold + 1, ticked.gold)
        assertEquals(1, ticked.goldAcquired)
        val capped = battle.copy(gold = 999)
        assertEquals(999, CastlesEngine.step(capped, 10_000).gold)
    }

    @Test
    fun `army spawns units in order at its frequency`() {
        val battle = CastlesEngine.newBattle(1, progress, seed = 3).copy(gold = 50)
        val bought = CastlesEngine.buy(battle, CastlesUnitKind.SOLDIER)
        assertEquals(45, bought.gold)
        assertEquals(1, bought.armies.size)
        assertEquals(4, bought.armies.first().remaining)
        var state = bought
        repeat(4) { state = CastlesEngine.step(state, 1_000) }
        val human = state.units.filter { it.side == com.heretek.dorado_hd.ui.apps.games.CastlesSide.HUMAN }
        assertEquals(4, human.size)
        assertTrue(human.all { it.kind == CastlesUnitKind.SOLDIER })
    }

    @Test
    fun `buy buttons gate on gold cooldown and unlocked slots`() {
        val battle = CastlesEngine.newBattle(1, progress, seed = 4).copy(gold = 50)
        val bought = CastlesEngine.buy(battle, CastlesUnitKind.SOLDIER)
        val again = CastlesEngine.buy(bought, CastlesUnitKind.SOLDIER)
        assertTrue(again.events.contains(CastlesEvent.INVALID))
        assertEquals(1, again.armies.size)
        val archerLocked = CastlesEngine.buy(battle.copy(gold = 1), CastlesUnitKind.ARCHER)
        assertTrue(archerLocked.events.contains(CastlesEvent.INVALID))
        val level6 = CastlesEngine.newBattle(6, progress, seed = 4).copy(gold = 50)
        assertTrue(CastlesEngine.buy(level6, CastlesUnitKind.BOMBER).armies.isNotEmpty())
    }

    @Test
    fun `first completion awards skill points once`() {
        val first = CastlesEngine.recordCompletion(progress, 1, 500)
        assertEquals(1, first.skills.unusedPoints)
        assertEquals(2, first.unlocked)
        assertEquals(500, first.best(1))
        val again = CastlesEngine.recordCompletion(first, 1, 900)
        assertEquals(1, again.skills.unusedPoints)
        assertEquals(900, again.best(1))
        val late = CastlesEngine.recordCompletion(progress, 14, 100)
        assertEquals(2, late.skills.unusedPoints)
    }

    @Test
    fun `skill upgrades cost the next tier index`() {
        val start = progress.copy(skills = CastlesSkillTree(unusedPoints = 3))
        val first = CastlesEngine.skillUpgrade(start, 0)
        assertEquals(1, first.skills.offense)
        assertEquals(2, first.skills.unusedPoints)
        val second = CastlesEngine.skillUpgrade(first, 0)
        assertEquals(2, second.skills.offense)
        assertEquals(0, second.skills.unusedPoints)
        val third = CastlesEngine.skillUpgrade(second, 0)
        assertEquals(2, third.skills.offense)
        assertTrue(third === second)
    }

    @Test
    fun `skill branches feed the player stats`() {
        val skills = CastlesSkillTree(offense = 3, defense = 3, utility = 3)
        val stats = CastlesEngine.playerStatsFor(CastlesEngine.levelFor(1), progress.copy(skills = skills))
        assertEquals(1f, stats.unitDamage, 0.001f)
        assertEquals(8f, stats.unitArmor, 0.001f)
        assertEquals(5, stats.armySize)
        assertEquals(110f, stats.cannonDamage, 0.001f)
        assertEquals(2.75f, stats.cannonCooldown, 0.001f)
        assertEquals(600f, stats.maxHealth, 0.001f)
        assertEquals(35f, stats.snipeDamage, 0.001f)
        assertEquals(0.65f, stats.snipeFrequency, 0.001f)
        assertEquals(5f, stats.goldInterval, 0.001f)
    }

    @Test
    fun `boss levels spawn their boss`() {
        assertEquals(16, CastlesEngine.levels.size)
        assertEquals(5, CastlesEngine.levels.count { it.boss != null })
        assertEquals(CastlesBossType.DRAGON, CastlesEngine.levelFor(3).boss)
        assertEquals(CastlesBackdrop.HILLS, CastlesEngine.levelFor(2).backdrop)
        assertEquals(CastlesBackdrop.PLAINS, CastlesEngine.levelFor(5).backdrop)
        assertEquals(CastlesBackdrop.DESERT, CastlesEngine.levelFor(8).backdrop)
        assertEquals(CastlesBackdrop.WATERFALL, CastlesEngine.levelFor(11).backdrop)
        assertEquals(CastlesBackdrop.MOUNTAIN, CastlesEngine.levelFor(14).backdrop)
        assertEquals(CastlesBackdrop.VOLCANO, CastlesEngine.levelFor(16).backdrop)
        val battle = CastlesEngine.newBattle(3, progress, seed = 5)
        assertEquals(42_000L, battle.bossTimerMs)
        val spawned = CastlesEngine.step(battle, 42_100)
        assertTrue(spawned.bossSpawned)
        assertTrue(spawned.units.any { it.boss == CastlesBossType.DRAGON })
        assertTrue(spawned.events.contains(CastlesEvent.BOSS_ALERT))
    }

    @Test
    fun `progress round trip preserves scores and skills`() {
        val saved = CastlesProgress(
            scores = mapOf(1 to 900, 2 to 1100),
            unlocked = 3,
            skills = CastlesSkillTree(2, 1, 0, 4),
        )
        val blob = CastlesEngine.encodeProgress(saved)
        val decoded = CastlesEngine.decodeProgress(blob)
        assertNotNull(decoded)
        assertEquals(saved, decoded)
        assertFalse(saved.completedAll())
    }
}
