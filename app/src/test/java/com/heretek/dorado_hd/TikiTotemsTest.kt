package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.TikiBlockDef
import com.heretek.dorado_hd.ui.apps.games.TikiBlockType
import com.heretek.dorado_hd.ui.apps.games.TikiEngine
import com.heretek.dorado_hd.ui.apps.games.TikiLoseReason
import com.heretek.dorado_hd.ui.apps.games.TikiLevelDef
import com.heretek.dorado_hd.ui.apps.games.TikiPacks
import com.heretek.dorado_hd.ui.apps.games.TikiProgress
import com.heretek.dorado_hd.ui.apps.games.TikiStatus
import com.heretek.dorado_hd.ui.apps.games.TikiTapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Tiki Totems engine and its authored packs. */
class TikiTotemsTest {

    /* ---------------------------------------------------------------- */
    /*                          Pack tables                             */
    /* ---------------------------------------------------------------- */

    @Test
    fun `thirteen packs of eight levels are authored`() {
        assertEquals(13, TikiPacks.NAMES.size)
        assertEquals(13, TikiPacks.ALL.size)
        TikiPacks.ALL.forEachIndexed { index, pack ->
            assertEquals(TikiPacks.NAMES[index], pack.label)
            assertEquals(TikiPacks.LEVELS_PER_PACK, pack.levels.size)
            pack.levels.forEachIndexed { levelIndex, level ->
                assertEquals(index, level.packIndex)
                assertEquals(levelIndex, level.levelIndex)
            }
        }
        assertEquals(104, TikiPacks.ALL.sumOf { it.levels.size })
    }

    @Test
    fun `toDestroy matches the countable blocks in every level`() {
        TikiPacks.ALL.forEach { pack ->
            pack.levels.forEach { level ->
                val countable = level.blocks.count { it.type.countable }
                assertEquals("${level.name} toDestroy", countable, level.toDestroy)
                assertTrue("${level.name} needs a goal", level.toDestroy >= 2)
                assertEquals(TikiBlockType.BLOCK_TOTEM, level.totem.type)
                assertFalse(level.blocks.any { it.type == TikiBlockType.BLOCK_TOTEM })
                assertTrue(level.blocks.isNotEmpty())
            }
        }
    }

    @Test
    fun `block type traits match the spec`() {
        val totem = TikiBlockType.BLOCK_TOTEM
        assertFalse(totem.destructible)
        assertFalse(totem.countable)

        val normal = TikiBlockType.BLOCK_NORMAL
        assertTrue(normal.destructible)
        assertTrue(normal.countable)

        val indestruct = TikiBlockType.BLOCK_INDESTRUCT
        assertFalse(indestruct.destructible)
        assertFalse(indestruct.countable)

        val elastic = TikiBlockType.BLOCK_ELASTIC
        assertTrue(elastic.destructible)
        assertTrue(elastic.countable)
        assertTrue(elastic.elastic)

        val elasticIndestruct = TikiBlockType.BLOCK_ELASTIC_INDESTRUCT
        assertFalse(elasticIndestruct.destructible)
        assertTrue(elasticIndestruct.elastic)

        val vanish = TikiBlockType.BLOCK_VANISH
        assertTrue(vanish.vanishing)
        assertTrue(vanish.destructible)

        val explosiveVanish = TikiBlockType.BLOCK_EXPLOSIVE_VANISH
        assertTrue(explosiveVanish.vanishing)
        assertTrue(explosiveVanish.explosive)

        val timer = TikiBlockType.BLOCK_EXPLOSIVE_TIMER
        assertTrue(timer.destructible)
        assertTrue(timer.countable)
        assertTrue(timer.timed)
    }

    @Test
    fun `block sizes come from the spec matrix`() {
        val allowed = setOf(40f, 80f, 120f, 160f, 200f)
        val used = HashSet<Float>()
        TikiPacks.ALL.forEach { pack ->
            pack.levels.forEach { level ->
                (level.blocks + level.totem).forEach { def ->
                    assertTrue("size ${def.w}x${def.h} not in spec matrix", def.w in allowed)
                    assertTrue("size ${def.w}x${def.h} not in spec matrix", def.h in allowed)
                    used.add(def.w)
                    used.add(def.h)
                }
            }
        }
        assertTrue("expected at least four spec sizes, used $used", used.size >= 4)
    }

    @Test
    fun `spec constants are preserved`() {
        assertEquals(32f, TikiEngine.WORLD_SCALER, 0.001f)
        assertEquals(640f, TikiEngine.GRAVITY, 0.001f)
        assertEquals(500L, TikiEngine.RECHARGE_TIME_MS)
        assertEquals(481f, TikiEngine.LAVA_Y, 0.001f)
        assertEquals(6.4f, TikiEngine.STABLE_LINEAR, 0.001f)
        assertEquals(0.25f, TikiEngine.STABLE_ANGULAR, 0.001f)
    }

    @Test
    fun `explosion falloff matches the spec curve`() {
        assertEquals(1f, TikiEngine.explosionFalloff(0f), 0.001f)
        assertEquals(1f, TikiEngine.explosionFalloff(48f), 0.001f)
        assertEquals(0.5f, TikiEngine.explosionFalloff(120f), 0.001f)
        assertEquals(0f, TikiEngine.explosionFalloff(192f), 0.001f)
    }

    /* ---------------------------------------------------------------- */
    /*                          Rules                                   */
    /* ---------------------------------------------------------------- */

    @Test
    fun `stability thresholds follow the spec`() {
        assertTrue(TikiEngine.isStableValues(0f, 0f, 0f))
        assertTrue(TikiEngine.isStableValues(6.3f, 0f, 0.24f))
        assertFalse(TikiEngine.isStableValues(6.5f, 0f, 0f))
        assertFalse(TikiEngine.isStableValues(0f, 0f, 0.26f))
    }

    @Test
    fun `totem touching the lava line loses`() {
        val level = TikiLevelDef(
            packIndex = 0,
            levelIndex = 0,
            name = "lava probe",
            totem = TikiBlockDef(TikiBlockType.BLOCK_TOTEM, 136f, TikiEngine.LAVA_Y - 80f, 40f, 160f),
            blocks = emptyList(),
            seed = 1,
        )
        val game = TikiEngine.newGame(level)
        TikiEngine.stepSteps(game, 5)
        assertEquals(TikiStatus.LOST, game.status)
        assertEquals(TikiLoseReason.LAVA, game.loseReason)
    }

    @Test
    fun `victory needs one second of totem stability`() {
        val level = TikiPacks.level(0, 0)
        val game = TikiEngine.newGame(level)
        level.blocks.filter { it.type.destructible }.forEach { def ->
            assertEquals(TikiTapResult.REMOVED, TikiEngine.tap(game, def.x, def.y))
            game.rechargeMs = 0L
        }
        assertEquals(0, game.toDestroyRemaining)

        TikiEngine.stepSteps(game, 30)
        assertEquals(TikiStatus.PLAYING, game.status)
        TikiEngine.stepSteps(game, 45)
        assertEquals(TikiStatus.WON, game.status)
        assertEquals(60, TikiEngine.VICTORY_TIME_STEPS)
    }

    @Test
    fun `vanishing pairs disappear on touch`() {
        val level = TikiPacks.level(2, 0)
        assertEquals(4, level.toDestroy)
        val game = TikiEngine.newGame(level)
        TikiEngine.stepSteps(game, 1)
        assertEquals(2, game.toDestroyRemaining)
        assertEquals(0, game.explosions)
    }

    @Test
    fun `explosive vanish pairs detonate when they touch`() {
        val level = TikiPacks.level(3, 0)
        val game = TikiEngine.newGame(level)
        TikiEngine.stepSteps(game, 1)
        assertEquals(1, game.explosions)
        assertEquals(2, game.toDestroyRemaining)
        assertEquals(TikiStatus.PLAYING, game.status)
    }

    @Test
    fun `timer bomb burns its fuse then explodes`() {
        val level = TikiPacks.level(7, 0)
        val game = TikiEngine.newGame(level)
        val timer = level.blocks.first { it.type == TikiBlockType.BLOCK_EXPLOSIVE_TIMER }
        assertEquals(TikiTapResult.FUSE, TikiEngine.tap(game, timer.x, timer.y))
        assertEquals(3, game.toDestroyRemaining)

        TikiEngine.stepSteps(game, 200)
        assertTrue(game.world.bodies.none { it.tag == TikiBlockType.BLOCK_EXPLOSIVE_TIMER.name })
        assertTrue("timer must count down: ${game.toDestroyRemaining}", game.toDestroyRemaining in 1..2)
        assertEquals(1, game.explosions)
    }

    @Test
    fun `tap recharge gates removals`() {
        val level = TikiPacks.level(0, 0)
        val game = TikiEngine.newGame(level)
        val first = level.blocks.first { it.type.destructible }
        val second = level.blocks.filter { it.type.destructible }[1]
        assertEquals(TikiTapResult.REMOVED, TikiEngine.tap(game, first.x, first.y))
        assertEquals(TikiTapResult.RECHARGING, TikiEngine.tap(game, second.x, second.y))
        game.rechargeMs = 0L
        assertEquals(TikiTapResult.REMOVED, TikiEngine.tap(game, second.x, second.y))
    }

    /* ---------------------------------------------------------------- */
    /*                     Determinism & persistence                    */
    /* ---------------------------------------------------------------- */

    private fun replay(): List<String> {
        val level = TikiPacks.level(1, 3)
        val game = TikiEngine.newGame(level)
        val snapshots = ArrayList<String>()
        level.blocks.filter { it.type.destructible }.take(2).forEach { def ->
            TikiEngine.tap(game, def.x, def.y)
            game.rechargeMs = 0L
        }
        repeat(240) {
            TikiEngine.step(game, 17L)
            snapshots.add(TikiEngine.snapshot(game))
        }
        return snapshots
    }

    @Test
    fun `fixed-step replay is deterministic`() {
        val first = replay()
        val second = replay()
        assertEquals(first, second)
        assertTrue(first.distinct().size > 1)
    }

    @Test
    fun `progress tracks unlocks stars and serialization`() {
        var progress = TikiProgress()
        assertTrue(progress.pack(0).isUnlocked(0))
        assertFalse(progress.pack(0).isUnlocked(1))

        progress = progress.complete(0, 0, 3)
        assertEquals(2, progress.pack(0).unlocked)
        assertEquals(3, progress.pack(0).stars(0))

        progress = progress.complete(0, 1, 2)
        assertEquals(3, progress.pack(0).unlocked)
        assertEquals(5, progress.totalStars)

        val decoded = TikiEngine.decodeProgress(TikiEngine.encodeProgress(progress))
        assertEquals(3, decoded.pack(0).unlocked)
        assertEquals(3, decoded.pack(0).stars(0))
        assertEquals(2, decoded.pack(0).stars(1))
        assertEquals(5, decoded.totalStars)
        assertEquals(TikiProgress(), TikiEngine.decodeProgress("junk"))
        assertEquals(TikiProgress(), TikiEngine.decodeProgress(null))
    }

    @Test
    fun `stars follow the elapsed-time bands`() {
        assertEquals(3, TikiEngine.starsFor(10_000L))
        assertEquals(3, TikiEngine.starsFor(20_000L))
        assertEquals(2, TikiEngine.starsFor(20_001L))
        assertEquals(2, TikiEngine.starsFor(45_000L))
        assertEquals(1, TikiEngine.starsFor(45_001L))
    }
}
