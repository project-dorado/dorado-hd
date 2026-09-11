package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.BEES_HARVEST_SCORE
import com.heretek.dorado_hd.ui.apps.games.BEES_TIME_LIMIT_MS
import com.heretek.dorado_hd.ui.apps.games.BeesBee
import com.heretek.dorado_hd.ui.apps.games.BeesBeeState
import com.heretek.dorado_hd.ui.apps.games.BeesBaddie
import com.heretek.dorado_hd.ui.apps.games.BeesBaddieKind
import com.heretek.dorado_hd.ui.apps.games.BeesEngine
import com.heretek.dorado_hd.ui.apps.games.BeesEvent
import com.heretek.dorado_hd.ui.apps.games.BeesKind
import com.heretek.dorado_hd.ui.apps.games.BeesMode
import com.heretek.dorado_hd.ui.apps.games.BeesNodeType
import com.heretek.dorado_hd.ui.apps.games.BeesTank
import com.heretek.dorado_hd.ui.apps.games.BeesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Bees!!! engine (no Android dependency). */
class BeesTest {

    @Test
    fun `pollen converts to honey at 25 per unit and respects capacity`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 1)
        val converted = BeesEngine.step(base.copy(tank = BeesTank(0, 25f, 0f)), 1_000)
        assertEquals(1, converted.tank.honey)
        assertEquals(0f, converted.tank.pollen, 0.01f)
        val full = BeesEngine.step(base.copy(tank = BeesTank(3, 25f, 0f)), 1_000)
        assertEquals(3, full.tank.honey)
        assertEquals(25f, full.tank.pollen, 0.01f)
        assertEquals(3, BeesEngine.tankCapacity(0))
        assertEquals(10, BeesEngine.tankCapacity(4, extras = 3))
    }

    @Test
    fun `bank applies the fifteen percent bonus and awards jars`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 2)
        val partial = BeesEngine.bank(base.copy(tank = BeesTank(1, 25f, 0f)))
        assertEquals(0, partial.honeyJars)
        assertEquals(57.5f, partial.jarProgress, 0.01f)
        assertEquals(0f, partial.tank.pollen, 0.01f)
        assertEquals(0, partial.tank.honey)
        val jar = BeesEngine.bank(base.copy(tank = BeesTank(2, 30f, 0f)))
        assertEquals(1, jar.honeyJars)
        assertEquals(12f, jar.jarProgress, 0.01f)
        assertEquals(jar.theme.jarValue, jar.score)
        assertTrue(jar.events.contains(BeesEvent.JAR))
    }

    @Test
    fun `jar table matches the five themes`() {
        assertEquals(80f, BeesTheme.GRASS.jarCost, 0.001f)
        assertEquals(150, BeesTheme.GRASS.jarValue)
        assertEquals(100f, BeesTheme.SWAMP.jarCost, 0.001f)
        assertEquals(200, BeesTheme.SWAMP.jarValue)
        assertEquals(150f, BeesTheme.DESERT.jarCost, 0.001f)
        assertEquals(250, BeesTheme.DESERT.jarValue)
        assertEquals(150f, BeesTheme.SNOW.jarCost, 0.001f)
        assertEquals(300, BeesTheme.SNOW.jarValue)
        assertEquals(200f, BeesTheme.SPACE.jarCost, 0.001f)
        assertEquals(500, BeesTheme.SPACE.jarValue)
        assertEquals(1, BeesEngine.jarsFor(80f, BeesTheme.GRASS))
        assertEquals(1, BeesEngine.jarsFor(159f, BeesTheme.GRASS))
        assertEquals(2, BeesEngine.jarsFor(160f, BeesTheme.GRASS))
        assertEquals(0, BeesEngine.jarsFor(79f, BeesTheme.GRASS))
    }

    @Test
    fun `streak caps simultaneous bees`() {
        assertEquals(2, BeesEngine.beeCap(0, 0))
        assertEquals(2, BeesEngine.beeCap(1, 0))
        assertEquals(2, BeesEngine.beeCap(2, 0))
        assertEquals(4, BeesEngine.beeCap(5, 2))
        assertEquals(6, BeesEngine.beeCap(9, 4))
    }

    @Test
    fun `harvest takes a petal and carries flower value`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 3)
        val node = base.nodes.first { it.type == BeesNodeType.FLOWER && (it.flower?.petals ?: 0) > 0 }
        val flower = node.flower!!
        val bee = BeesBee(
            id = 42,
            kind = BeesKind.NORMAL,
            x = node.x,
            y = node.y,
            state = BeesBeeState.HARVESTING,
            target = node.id,
            harvestMs = 6_900L,
        )
        val after = BeesEngine.step(base.copy(bees = listOf(bee)), 200)
        val updated = after.nodes.first { it.id == node.id }.flower!!
        assertEquals(flower.petals - 1, updated.petals)
        val updatedBee = after.bees.first()
        assertEquals(BEES_HARVEST_SCORE, after.score)
        assertEquals(flower.value.toFloat(), updatedBee.carried, 0.01f)
        assertEquals(1, updatedBee.carriedPetals)
    }

    @Test
    fun `deposit scores carried pollen and grows the streak`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 12)
        val hive = base.nodes.first { it.type == BeesNodeType.HIVE }
        val bee = BeesBee(
            id = 1,
            kind = BeesKind.NORMAL,
            x = hive.x,
            y = hive.y,
            state = BeesBeeState.RETURNING,
            target = hive.id,
            carried = 45f,
            carriedPetals = 2,
        )
        val after = BeesEngine.step(base.copy(bees = listOf(bee), tank = BeesTank()), 100)
        assertEquals(45, after.score)
        assertEquals(45f, after.tank.pollen, 0.01f)
        assertEquals(1, after.streak)
        assertTrue(after.events.contains(BeesEvent.DEPOSIT))
    }

    @Test
    fun `challenge drain converts honey back and stops on arrival`() {
        val base = BeesEngine.newGame(1, BeesMode.CHALLENGE, seed = 4).copy(elapsedMs = 10_000)
        val drained = BeesEngine.step(base.copy(tank = BeesTank(0, 100f, 0f)), 1_000)
        assertEquals(59.6f, drained.tank.pollen, 0.05f)
        val arrived = BeesEngine.step(base.copy(tank = BeesTank(0, 100f, 0f), lastDepositMs = 9_500), 1_000)
        assertEquals(68f, arrived.tank.pollen, 0.05f)
        val converted = BeesEngine.step(base.copy(tank = BeesTank(1, 0f, 0f)), 1_000)
        assertEquals(0, converted.tank.honey)
        assertEquals(16.6f, converted.tank.pollen, 0.05f)
    }

    @Test
    fun `baddie combo multiplies defeat score`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 6).copy(combo = 1)
        val bee = BeesBee(1, BeesKind.NINJA, 100f, 140f, BeesBeeState.IDLE)
        val baddie = BeesBaddie(2, BeesBaddieKind.BROWN_BEAR, 100f, 140f, 10f, 0, combo = 1)
        val after = BeesEngine.step(base.copy(bees = listOf(bee), baddies = listOf(baddie)), 100)
        assertTrue(after.baddies.isEmpty())
        assertEquals(100, after.score)
        assertEquals(2, after.combo)
        assertTrue(after.events.contains(BeesEvent.BADDIE_DOWN))
    }

    @Test
    fun `non-ninja collisions explode both and reset the streak`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 7).copy(streak = 4, combo = 3)
        val bee = BeesBee(1, BeesKind.NORMAL, 100f, 140f, BeesBeeState.IDLE)
        val baddie = BeesBaddie(2, BeesBaddieKind.BUNNY, 100f, 140f, 8f, 0)
        val after = BeesEngine.step(base.copy(bees = listOf(bee), baddies = listOf(baddie)), 100)
        assertTrue(after.bees.isEmpty())
        assertTrue(after.baddies.isEmpty())
        assertEquals(0, after.streak)
        assertTrue(after.events.contains(BeesEvent.EXPLODE))
    }

    @Test
    fun `space empty flowers bite`() {
        val base = BeesEngine.newGame(21, BeesMode.ADVENTURE, seed = 8).copy(streak = 5)
        assertEquals(BeesTheme.SPACE, base.theme)
        val empty = base.nodes.first { it.type == BeesNodeType.FLOWER && (it.flower?.petals ?: 0) == 0 }
        val bee = BeesBee(
            id = 1,
            kind = BeesKind.NORMAL,
            x = empty.x,
            y = empty.y,
            state = BeesBeeState.FLYING,
            target = empty.id,
        )
        val after = BeesEngine.step(base.copy(bees = listOf(bee)), 100)
        assertEquals(0, after.streak)
        assertTrue(after.events.contains(BeesEvent.EXPLODE))
    }

    @Test
    fun `adventure ends as a scored win at the time limit`() {
        val base = BeesEngine.newGame(1, BeesMode.ADVENTURE, seed = 9)
            .copy(elapsedMs = BEES_TIME_LIMIT_MS - 100, warningShown = true)
        val after = BeesEngine.step(base, 200)
        assertTrue(after.over)
        assertTrue(after.won)
        assertTrue(after.events.contains(BeesEvent.WIN))
    }

    @Test
    fun `levels cover five themes with five each`() {
        assertEquals(25, BeesEngine.levels.size)
        BeesTheme.entries.forEach { theme ->
            assertEquals(5, BeesEngine.levels.count { it.theme == theme })
        }
        val (nodes, paths) = BeesEngine.buildBoard(BeesEngine.levelFor(1))
        assertTrue(nodes.any { it.type == BeesNodeType.HIVE })
        assertTrue(nodes.count { it.type == BeesNodeType.FLOWER } >= 6)
        assertTrue(paths.isNotEmpty())
    }

    @Test
    fun `state round trip preserves the hive`() {
        var state = BeesEngine.newGame(3, BeesMode.ADVENTURE, seed = 11)
        repeat(30) { state = BeesEngine.step(state, 500) }
        val decoded = BeesEngine.decode(BeesEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(state.levelId, decoded!!.levelId)
        assertEquals(state.score, decoded.score)
        assertEquals(state.streak, decoded.streak)
        assertEquals(state.tank.honey, decoded.tank.honey)
        assertEquals(state.nodes.size, decoded.nodes.size)
        assertEquals(state.bees.size, decoded.bees.size)
        assertEquals(state.baddies.size, decoded.baddies.size)
        assertFalse(decoded.over)
    }
}
