package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.SpaceBattleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Space Battle 2 engine. */
class SpaceBattleTest {

    private val n = SpaceBattleEngine.Difficulty.NORMAL
    private val h = SpaceBattleEngine.Difficulty.HARD
    private val e = SpaceBattleEngine.Difficulty.ELITE

    @Test
    fun `primary fire follows the boost tier cadence`() {
        val slowBuild = SpaceBattleEngine.SbBuild(parts = listOf(0, 0, 0, 0))
        val fastBuild = SpaceBattleEngine.SbBuild(parts = listOf(0, 5, 0, 0))
        assertEquals(2, SpaceBattleEngine.shipStat(slowBuild, 3))
        assertEquals(400f, SpaceBattleEngine.primaryReloadMs(slowBuild), 0.01f)
        assertEquals(3, SpaceBattleEngine.shipStat(fastBuild, 3))
        assertEquals(350f, SpaceBattleEngine.primaryReloadMs(fastBuild), 0.01f)

        val first = SpaceBattleEngine.tick(SpaceBattleEngine.newGame(1, build = slowBuild))
        assertEquals(1, first.bolts.size)
        val second = SpaceBattleEngine.tick(first)
        assertEquals(1, second.bolts.size)

        val level4 = SpaceBattleEngine.newGame(1, build = slowBuild).let { it.copy(ship = it.ship.copy(primaryLevel = 4)) }
        assertEquals(4, SpaceBattleEngine.tick(level4).bolts.count { it.friendly })
        assertEquals(1, SpaceBattleEngine.tick(SpaceBattleEngine.newGame(1, build = slowBuild)).bolts.count { it.friendly })

        val later = (0 until 30).fold(second) { acc, _ -> SpaceBattleEngine.tick(acc) }
        assertTrue(later.bolts.count { it.friendly } > 1)
    }

    @Test
    fun `secondary fire spends ammo and ignores far taps`() {
        val base = SpaceBattleEngine.newGame(1)
        val nearX = base.ship.x * SpaceBattleEngine.VIEW_W / base.playAreaWidth
        val nearY = base.ship.y + SpaceBattleEngine.TOUCH_Y_OFFSET
        val near = SpaceBattleEngine.tick(
            base,
            SpaceBattleEngine.SbInput(touching = true, touchX = nearX, touchY = nearY, fire = true),
        )
        assertEquals(2, near.ammo)
        assertTrue(near.bolts.count { it.friendly } >= 4)

        val far = SpaceBattleEngine.tick(
            base,
            SpaceBattleEngine.SbInput(touching = true, touchX = 0f, touchY = 0f, fire = true),
        )
        assertEquals(3, far.ammo)

        val dry = SpaceBattleEngine.newGame(1).let { it.copy(ammo = 0) }
        val fired = SpaceBattleEngine.tick(
            dry,
            SpaceBattleEngine.SbInput(touching = true, touchX = nearX, touchY = nearY, fire = true),
        )
        assertEquals(0, fired.ammo)
    }

    @Test
    fun `touch steering chases the finger and tilt steers with the sensor`() {
        val state = SpaceBattleEngine.newGame(1)
        val touched = (0 until 20).fold(state) { acc, _ ->
            SpaceBattleEngine.tick(acc, SpaceBattleEngine.SbInput(touching = true, touchX = 272f, touchY = 300f))
        }
        assertTrue(touched.ship.x > state.ship.x)

        val tilted = (0 until 20).fold(state) { acc, _ ->
            SpaceBattleEngine.tick(
                acc,
                SpaceBattleEngine.SbInput(control = SpaceBattleEngine.ControlStyle.TILT, tiltActive = true, tiltX = 1f),
            )
        }
        assertTrue(tilted.ship.x > state.ship.x)
    }

    @Test
    fun `shield tiers match the hull table and elite modifiers`() {
        val base = SpaceBattleEngine.shipStats(SpaceBattleEngine.SbBuild(), n)
        assertEquals(12.8f, base.acceleration, 0.01f)
        assertEquals(3, base.armor)
        assertEquals(2, base.speed)
        assertEquals(100f, base.maxShields, 0.01f)
        assertEquals(9_000f, base.shieldRechargeDelayMs, 0.01f)
        assertEquals(0.04f, base.shieldRechargeSpeed, 0.01f)

        val elite = SpaceBattleEngine.shipStats(SpaceBattleEngine.SbBuild(), e)
        assertEquals(50f, elite.maxShields, 0.01f)
        assertEquals(11_250f, elite.shieldRechargeDelayMs, 0.01f)

        val heavy = SpaceBattleEngine.shipStats(SpaceBattleEngine.SbBuild(parts = listOf(0, 0, 0, 3)), n)
        assertEquals(4, heavy.armor)
        assertEquals(125f, heavy.maxShields, 0.01f)
        assertEquals(7_500f, heavy.shieldRechargeDelayMs, 0.01f)
        assertEquals(0.06f, heavy.shieldRechargeSpeed, 0.01f)

        val quick = SpaceBattleEngine.shipStats(SpaceBattleEngine.SbBuild(parts = listOf(0, 0, 9, 0)), n)
        assertEquals(4, quick.speed)
        assertEquals(SpaceBattleEngine.ACCEL_BASE + 2.4f * 4, quick.acceleration, 0.01f)

        assertEquals(9, SpaceBattleEngine.maxAmmo(n))
        assertEquals(5, SpaceBattleEngine.maxAmmo(e))
    }

    @Test
    fun `extra life thresholds follow the difficulty ladder and cap at nine`() {
        val normal = SpaceBattleEngine.grantScore(SpaceBattleEngine.newGame(1), 2001)
        assertEquals(5000, normal.nextLife)
        assertEquals(4500, normal.nextLifeInc)
        assertTrue(normal.pickups.any { it.kind == SpaceBattleEngine.PowerUpKind.EXTRA_LIFE })

        val hard = SpaceBattleEngine.grantScore(SpaceBattleEngine.newGame(1, difficulty = h), 5001)
        assertEquals(10_000, hard.nextLife)
        assertEquals(6500, hard.nextLifeInc)

        val capped = SpaceBattleEngine.grantScore(SpaceBattleEngine.newGame(1).copy(lives = 9), 2001)
        assertTrue(capped.pickups.any { it.kind == SpaceBattleEngine.PowerUpKind.ZAURIUM })
        assertFalse(capped.pickups.any { it.kind == SpaceBattleEngine.PowerUpKind.EXTRA_LIFE })
    }

    @Test
    fun `powerup randomizer respects the upgrade ammo and shield caps`() {
        val base = SpaceBattleEngine.newGame(1)
        val full = base.copy(
            ammo = SpaceBattleEngine.maxAmmo(n),
            ship = base.ship.copy(primaryLevel = 4, shields = base.ship.maxShields),
        )
        assertEquals(SpaceBattleEngine.PowerUpKind.SHIELDS, SpaceBattleEngine.resolvePowerUpKind(full, SpaceBattleEngine.PowerUpKind.UPGRADE))

        val overcharged = full.copy(ship = full.ship.copy(shields = full.ship.maxShields * 2f))
        assertEquals(SpaceBattleEngine.PowerUpKind.ZAURIUM, SpaceBattleEngine.resolvePowerUpKind(overcharged, SpaceBattleEngine.PowerUpKind.UPGRADE))

        val upgrade = SpaceBattleEngine.resolvePowerUpKind(base, SpaceBattleEngine.PowerUpKind.UPGRADE)
        assertEquals(SpaceBattleEngine.PowerUpKind.UPGRADE, upgrade)
    }

    @Test
    fun `boss weak points only take damage while exposed`() {
        val base = SpaceBattleEngine.newGame(1)
        val boss = SpaceBattleEngine.Boss(
            kind = "scout",
            x = base.playAreaWidth / 2f,
            y = 110f,
            hp = 100f,
            maxHp = 100f,
            attackMs = 2_000L,
        )
        val bolt = SpaceBattleEngine.Bolt(boss.x, boss.y, 0f, 0f, 25f, true)
        val dormant = SpaceBattleEngine.tick(base.copy(boss = boss, bolts = listOf(bolt)))
        assertEquals(100f, dormant.boss!!.hp, 0.001f)

        val awake = SpaceBattleEngine.tick(base.copy(boss = boss.copy(exposed = true, attackMs = 0L), bolts = listOf(bolt)))
        assertEquals(75f, awake.boss!!.hp, 0.001f)
    }

    @Test
    fun `stage scripts parse the record vocabulary and the catalog has ten stages`() {
        val parsed = SpaceBattleEngine.parseStages(
            """
            stage 2 world 1 width 480 boss gnat hp 250 at 900
            spawn 100 drone_seeker 40 2 250
            spawn 500 asteroid_epic 180
            """.trimIndent(),
        )
        assertEquals(1, parsed.size)
        assertEquals(2, parsed[0].id)
        assertEquals(1, parsed[0].world)
        assertEquals(480f, parsed[0].width, 0.01f)
        assertEquals("gnat", parsed[0].bossKind)
        assertEquals(250f, parsed[0].bossHp, 0.01f)
        assertEquals(900L, parsed[0].bossAtMs)
        assertEquals(2, parsed[0].spawns.size)
        assertEquals(2, parsed[0].spawns[0].count)
        assertEquals(250L, parsed[0].spawns[0].gapMs)
        assertEquals(SpaceBattleEngine.EnemyKind.ASTEROID_EPIC, parsed[0].spawns[1].kind)

        val catalog = SpaceBattleEngine.stages
        assertEquals(SpaceBattleEngine.NUM_STAGES, catalog.size)
        assertEquals((0..4).toSet(), catalog.map { it.world }.toSet())
        assertTrue(catalog.all { it.bossKind != null && it.spawns.isNotEmpty() })
        assertEquals("core", SpaceBattleEngine.stage(10).bossKind)
    }

    @Test
    fun `ghosts round trip and sample by time`() {
        val ghost = SpaceBattleEngine.SbGhost(
            trackId = 2,
            finishMs = 12_345L,
            samples = listOf(
                SpaceBattleEngine.GhostSample(0L, 0f, 10f),
                SpaceBattleEngine.GhostSample(250L, 300f, 11f),
                SpaceBattleEngine.GhostSample(500L, 600f, 12f),
            ),
        )
        assertEquals(ghost, SpaceBattleEngine.decodeGhost(SpaceBattleEngine.encodeGhost(ghost)))
        assertEquals(300f, SpaceBattleEngine.ghostAt(ghost, 400L)!!.distancePx, 0.01f)
        assertEquals(0f, SpaceBattleEngine.ghostAt(ghost, 10L)!!.distancePx, 0.01f)
        assertNull(SpaceBattleEngine.decodeGhost("garbage"))
    }

    @Test
    fun `race timing replays the same ghost and wins against a slower target`() {
        fun run(state: SpaceBattleEngine.SbState): SpaceBattleEngine.SbState {
            var current = state
            repeat(2_600) {
                if (!current.raceFinished) current = SpaceBattleEngine.tick(current)
            }
            return current
        }

        val a = run(SpaceBattleEngine.newGame(1, mode = SpaceBattleEngine.GameMode.RACE, raceId = 1))
        val b = run(SpaceBattleEngine.newGame(1, mode = SpaceBattleEngine.GameMode.RACE, raceId = 1))
        assertTrue(a.raceFinished)
        assertEquals(true, a.raceWon)
        assertEquals(SpaceBattleEngine.races[0].gates.size, a.gatesPassed)
        assertTrue(a.raceTimeMs > 0L)
        assertEquals(a.ghostSamples, b.ghostSamples)
        assertTrue(a.ghostSamples.isNotEmpty())

        val midRace = (0 until 300).fold(SpaceBattleEngine.newGame(1, mode = SpaceBattleEngine.GameMode.RACE, raceId = 1)) { acc, _ ->
            SpaceBattleEngine.tick(acc)
        }
        assertTrue(midRace.enemies.isEmpty())
        assertNull(midRace.boss)
        assertFalse(midRace.stageComplete)

        val slowGhost = SpaceBattleEngine.SbGhost(1, 0L, emptyList())
        val versusSlow = run(
            SpaceBattleEngine.newGame(1, mode = SpaceBattleEngine.GameMode.RACE, raceId = 1, ghost = slowGhost),
        )
        assertEquals(false, versusSlow.raceWon)
    }

    @Test
    fun `part tiers gate by wins and purchases spend zaurium`() {
        assertTrue(SpaceBattleEngine.isPartUnlocked(0, emptySet()))
        assertFalse(SpaceBattleEngine.isPartUnlocked(5, emptySet()))
        assertTrue(SpaceBattleEngine.isPartUnlocked(5, setOf(n)))
        assertFalse(SpaceBattleEngine.isPartUnlocked(7, setOf(n)))
        assertTrue(SpaceBattleEngine.isPartUnlocked(7, setOf(h)))
        assertTrue(SpaceBattleEngine.isPartUnlocked(9, setOf(e)))

        var progress = SpaceBattleEngine.SbProgress(zaurium = 9)
        assertFalse(SpaceBattleEngine.canBuy(progress, 0, 1))
        progress = progress.copy(zaurium = 10)
        assertTrue(SpaceBattleEngine.canBuy(progress, 0, 1))
        progress = SpaceBattleEngine.buy(progress, 0, 1)
        assertEquals(0, progress.zaurium)
        assertTrue(SpaceBattleEngine.owns(progress, 0, 1))
        assertEquals(1, progress.build.parts[0])
        progress = SpaceBattleEngine.equip(progress, 3, 0)
        assertEquals(0, progress.build.parts[3])
        assertFalse(SpaceBattleEngine.canBuy(progress, 0, 6))
    }

    @Test
    fun `progress round trips through encode and decode`() {
        val ghost = SpaceBattleEngine.SbGhost(1, 9_000L, listOf(SpaceBattleEngine.GhostSample(0L, 0f, 1f)))
        val progress = SpaceBattleEngine.SbProgress(
            zaurium = 7,
            build = SpaceBattleEngine.SbBuild(listOf(1, 2, 3, 4), listOf(4, 3, 2, 1)),
            wins = setOf(h),
            highestStage = mapOf(n to 3, h to 2, e to 1),
            racesBeaten = listOf(true, false, false, false, false),
            ghosts = mapOf(1 to ghost),
            bestTotals = mapOf(n to 123, h to 45, e to 0),
        )
        assertEquals(progress, SpaceBattleEngine.decodeProgress(SpaceBattleEngine.encodeProgress(progress)))
        assertNull(SpaceBattleEngine.decodeProgress("nonsense"))
    }
}
