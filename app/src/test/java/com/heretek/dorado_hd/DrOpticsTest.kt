package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.DrOpticsEngine
import com.heretek.dorado_hd.ui.apps.games.DrOpticsState
import com.heretek.dorado_hd.ui.apps.games.OpticsColor
import com.heretek.dorado_hd.ui.apps.games.OpticsElement
import com.heretek.dorado_hd.ui.apps.games.OpticsKind
import com.heretek.dorado_hd.ui.apps.games.OpticsSinkState
import com.heretek.dorado_hd.ui.apps.games.OpticsStatus
import com.heretek.dorado_hd.ui.apps.games.OpticsVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the Dr Optics engine (no Android dependency). */
class DrOpticsTest {

    private fun laser(id: Int, x: Float, y: Float, deg: Float, color: OpticsColor, movable: Boolean = false) =
        OpticsElement(id, OpticsKind.LASER, OpticsVec(x, y), 9f, rotationDeg = deg, color = color, movable = movable)

    private fun sink(id: Int, x: Float, y: Float, color: OpticsColor, radius: Float = 12f) =
        OpticsElement(id, OpticsKind.SINK, OpticsVec(x, y), radius, color = color, movable = false)

    private fun mirror(id: Int, x: Float, y: Float, deg: Float, locked: Boolean = false) =
        OpticsElement(id, OpticsKind.PLANE_MIRROR, OpticsVec(x, y), 46f, rotationDeg = deg, locked = locked)

    @Test
    fun `plane reflection follows the mirror law`() {
        val down = DrOpticsEngine.reflect(OpticsVec(1f, 0f), OpticsVec(-0.70710678f, 0.70710678f))
        assertEquals(0f, down.x, 1e-3f)
        assertEquals(1f, down.y, 1e-3f)
        val back = DrOpticsEngine.reflect(OpticsVec(1f, 0f), OpticsVec(1f, 0f))
        assertEquals(-1f, back.x, 1e-3f)
        assertEquals(0f, back.y, 1e-3f)
        val angled = DrOpticsEngine.reflect(OpticsVec.dir(-45f), OpticsVec(0f, 1f))
        assertEquals(0.7071f, angled.x, 1e-3f)
        assertEquals(0.7071f, angled.y, 1e-3f)
    }

    @Test
    fun `refraction obeys snell and total internal reflection falls back`() {
        // 60 degrees in vacuum enters a 1.5 medium at 19.47 degrees.
        val bent = DrOpticsEngine.refract(OpticsVec.dir(60f), OpticsVec(0f, -1f), 1f, 1.5f)
        assertEquals(0.3333f, bent!!.x, 1e-3f)
        assertEquals(0.9428f, bent.y, 1e-3f)
        // 60 degrees inside a 1.5 medium cannot escape into vacuum.
        assertNull(DrOpticsEngine.refract(OpticsVec.dir(30f), OpticsVec(0f, -1f), 1.5f, 1f))
    }

    @Test
    fun `ray segment intersection picks the nearest contact`() {
        val t = DrOpticsEngine.raySegment(OpticsVec(0f, 0f), OpticsVec(1f, 0f), OpticsVec(10f, -1f), OpticsVec(10f, 1f))
        assertEquals(10f, t!!, 1e-3f)
        assertNull(
            DrOpticsEngine.raySegment(OpticsVec(0f, 0f), OpticsVec(1f, 0f), OpticsVec(0f, 1f), OpticsVec(10f, 1f)),
        )
        assertNull(
            DrOpticsEngine.raySegment(OpticsVec(0f, 0f), OpticsVec(1f, 0f), OpticsVec(-5f, -1f), OpticsVec(-5f, 1f)),
        )
        val elements = listOf(
            laser(1, 30f, 136f, 0f, OpticsColor.RED),
            sink(2, 100f, 136f, OpticsColor.RED),
            sink(3, 200f, 136f, OpticsColor.RED),
        )
        val simulation = DrOpticsEngine.simulation(elements, 0)
        assertEquals(1, simulation.beams.size)
        assertTrue(simulation.applied.containsKey(2))
        assertFalse(simulation.applied.containsKey(3))
    }

    @Test
    fun `lens medium uses the authored index of refraction`() {
        val lens = OpticsElement(1, OpticsKind.LENS, OpticsVec(100f, 100f), 40f, refractiveIndex = 1.5f, movable = false)
        assertEquals(1.5f, DrOpticsEngine.mediumAt(listOf(lens), 0, OpticsVec(100f, 100f)), 1e-4f)
        assertEquals(1f, DrOpticsEngine.mediumAt(listOf(lens), 0, OpticsVec(0f, 0f)), 1e-4f)
    }

    @Test
    fun `a lens level bends the beam through three traced segments`() {
        val level = DrOpticsEngine.level(15)
        val simulation = DrOpticsEngine.simulation(level.solution, 0)
        assertEquals(3, simulation.beams.size)
        assertTrue(simulation.applied.isNotEmpty())
        // The exit segment is not parallel to the entry segment.
        val entry = simulation.beams[0]
        val exit = simulation.beams[2]
        val entryDir = (entry.end - entry.start).normalized()
        val exitDir = (exit.end - exit.start).normalized()
        assertTrue(kotlin.math.abs(entryDir.x * exitDir.y - entryDir.y * exitDir.x) > 0.1f)
    }

    @Test
    fun `beam pool and depth caps bound the recursion`() {
        val hall = listOf(
            laser(1, 100f, 136f, 0f, OpticsColor.RED),
            mirror(2, 200f, 136f, 90f, locked = true),
            mirror(3, 50f, 136f, 90f, locked = true),
        )
        val simulation = DrOpticsEngine.simulation(hall, 0, maxBeams = 6)
        assertEquals(6, simulation.beams.size)
        assertEquals(4, DrOpticsEngine.maxBeamDepth(4, 1))
        assertEquals(15, DrOpticsEngine.maxBeamDepth(75, 5))
        assertEquals(1, DrOpticsEngine.maxBeamDepth(0, 3))
    }

    @Test
    fun `sink charge follows the spec formula and decays without light`() {
        val fed = listOf(laser(1, 30f, 136f, 0f, OpticsColor.RED), sink(2, 200f, 136f, OpticsColor.RED))
        var state = DrOpticsEngine.stateOf(fed)
        state = DrOpticsEngine.step(state, 1000)
        assertEquals(1.0f, state.sinks[0].stored, 1e-3f)
        assertFalse(state.sinks[0].satisfied)
        state = DrOpticsEngine.step(state, 1000)
        assertEquals(1.2f, state.sinks[0].stored, 1e-3f)
        assertTrue(state.solved)
        val frozen = DrOpticsEngine.step(state, 1000)
        assertEquals(1.2f, frozen.sinks[0].stored, 1e-3f)

        val dark = listOf(laser(1, 30f, 136f, 180f, OpticsColor.RED), sink(2, 200f, 136f, OpticsColor.RED))
        val charged = DrOpticsEngine.stateOf(dark).copy(sinks = listOf(OpticsSinkState(2, 1f)))
        val decayed = DrOpticsEngine.step(charged, 1000)
        assertEquals(0.2f, decayed.sinks[0].stored, 1e-3f)
    }

    @Test
    fun `win and lose detection`() {
        val fed = listOf(laser(1, 30f, 136f, 0f, OpticsColor.RED), sink(2, 200f, 136f, OpticsColor.RED))
        var win = DrOpticsEngine.stateOf(fed)
        win = DrOpticsEngine.step(win, 1000)
        win = DrOpticsEngine.step(win, 1000)
        assertEquals(OpticsStatus.SOLVED, win.status)

        val dark = listOf(laser(1, 30f, 136f, 180f, OpticsColor.RED), sink(2, 200f, 136f, OpticsColor.RED))
        var lose = DrOpticsEngine.stateOf(dark, maxMoves = 1)
        lose = DrOpticsEngine.commit(lose)
        assertEquals(OpticsStatus.PLAYING, lose.status)
        lose = DrOpticsEngine.commit(lose)
        assertEquals(OpticsStatus.FAILED, lose.status)
    }

    @Test
    fun `all forty one levels parse with the authored unlock ladder`() {
        assertEquals(emptyList<String>(), DrOpticsEngine.validate())
        assertEquals(41, DrOpticsEngine.levels.size)
        assertEquals(listOf(0, 8, 14, 8, 6, 4, 1), DrOpticsEngine.difficultyCounts())
        DrOpticsEngine.levels.forEachIndexed { position, level ->
            assertEquals(position, level.index)
            val expectedDifficulty = when (position) {
                in 0..7 -> 1
                in 8..21 -> 2
                in 22..29 -> 3
                in 30..35 -> 4
                in 36..39 -> 5
                else -> 6
            }
            assertEquals("level ${position + 1}", expectedDifficulty, level.difficulty)
        }
    }

    @Test
    fun `difficulty unlock cutoffs match the spec`() {
        assertEquals(0, DrOpticsEngine.requiredSolvedForDifficulty(1))
        assertEquals(6, DrOpticsEngine.requiredSolvedForDifficulty(2))
        assertEquals(16, DrOpticsEngine.requiredSolvedForDifficulty(3))
        assertEquals(20, DrOpticsEngine.requiredSolvedForDifficulty(4))
        assertEquals(24, DrOpticsEngine.requiredSolvedForDifficulty(5))
        assertEquals(40, DrOpticsEngine.requiredSolvedForDifficulty(6))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(1, 0))
        assertFalse(DrOpticsEngine.isDifficultyUnlocked(2, 5))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(2, 6))
        assertFalse(DrOpticsEngine.isDifficultyUnlocked(3, 15))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(3, 16))
        assertFalse(DrOpticsEngine.isDifficultyUnlocked(4, 19))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(4, 20))
        assertFalse(DrOpticsEngine.isDifficultyUnlocked(5, 23))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(5, 24))
        assertFalse(DrOpticsEngine.isDifficultyUnlocked(6, 39))
        assertTrue(DrOpticsEngine.isDifficultyUnlocked(6, 40))
    }

    @Test
    fun `every authored level solves from its solution pose`() {
        DrOpticsEngine.levels.forEach { level ->
            var state = DrOpticsEngine.stateAt(level.index, level.solution)
            repeat(120) {
                if (state.status == OpticsStatus.PLAYING) state = DrOpticsEngine.step(state, 100)
            }
            assertEquals("level ${level.index + 1} (${level.name})", OpticsStatus.SOLVED, state.status)
        }
    }

    @Test
    fun `scrambled starts need work`() {
        DrOpticsEngine.levels.forEach { level ->
            var state = DrOpticsEngine.start(level.index)
            repeat(40) {
                if (state.status == OpticsStatus.PLAYING) state = DrOpticsEngine.step(state, 100)
            }
            assertNotEquals("level ${level.index + 1} (${level.name})", OpticsStatus.SOLVED, state.status)
        }
    }

    @Test
    fun `ticks are deterministic for animated optics`() {
        val level = DrOpticsEngine.level(38)
        var left: DrOpticsState = DrOpticsEngine.stateAt(level.index, level.solution)
        var right: DrOpticsState = DrOpticsEngine.stateAt(level.index, level.solution)
        repeat(150) {
            left = DrOpticsEngine.step(left, 16)
            right = DrOpticsEngine.step(right, 16)
        }
        assertEquals(left, right)
        assertTrue(left.beams.isNotEmpty())
        assertTrue(left.clockMs > 0)
    }

    @Test
    fun `autosave round trips the level, poses and sink charge`() {
        var state = DrOpticsEngine.start(11)
        val id = state.elements.first { it.movable && it.kind == OpticsKind.PLANE_MIRROR }.id
        state = DrOpticsEngine.translate(state, id, 12f, -8f)
        state = DrOpticsEngine.commit(state)
        state = DrOpticsEngine.rotateElement(state, id, 15f)
        state = DrOpticsEngine.step(state, 250)
        val decoded = DrOpticsEngine.decode(DrOpticsEngine.encode(state))
        assertTrue(decoded != null)
        val restored = decoded!!
        assertEquals(state.levelIndex, restored.levelIndex)
        assertEquals(state.moves, restored.moves)
        assertEquals(state.clockMs, restored.clockMs)
        assertEquals(state.status, restored.status)
        val before = state.elements.first { it.id == id }
        val after = restored.elements.first { it.id == id }
        assertEquals(before.pos.x, after.pos.x, 0.11f)
        assertEquals(before.pos.y, after.pos.y, 0.11f)
        assertEquals(before.rotationDeg, after.rotationDeg, 0.6f)
        assertEquals(state.sinks[0].stored, restored.sinks[0].stored, 1e-3f)
        assertNull(DrOpticsEngine.decode("not a save"))
    }

    @Test
    fun `drag picks within the device radius and clamps to the room`() {
        val element = OpticsElement(1, OpticsKind.PLANE_MIRROR, OpticsVec(100f, 100f), 46f)
        val state = DrOpticsEngine.stateOf(listOf(element))
        assertEquals(1, DrOpticsEngine.pick(state, OpticsVec(100f, 100f))!!.id)
        assertEquals(1, DrOpticsEngine.pick(state, OpticsVec(170f, 100f))!!.id)
        assertNull(DrOpticsEngine.pick(state, OpticsVec(172f, 100f)))
        assertEquals(state.elements, DrOpticsEngine.translate(state, 1, 500f, 0f).elements)
        var clamped = state
        repeat(4) { clamped = DrOpticsEngine.translate(clamped, 1, 100f, 0f) }
        assertEquals(470f, clamped.elements.first().pos.x, 1e-3f)
        assertEquals(0, clamped.moves)
    }

    @Test
    fun `locked objects reject dragging until re-enabled`() {
        val locked = OpticsElement(1, OpticsKind.PLANE_MIRROR, OpticsVec(100f, 100f), 46f, locked = true)
        val state = DrOpticsEngine.stateOf(listOf(locked))
        assertNull(DrOpticsEngine.pick(state, OpticsVec(100f, 100f)))
        assertEquals(state.elements, DrOpticsEngine.translate(state, 1, 10f, 0f).elements)
        val unlocked = DrOpticsEngine.toggleLock(state, 1)
        assertEquals(1, DrOpticsEngine.pick(unlocked, OpticsVec(100f, 100f))!!.id)
        assertEquals(110f, DrOpticsEngine.translate(unlocked, 1, 10f, 0f).elements.first().pos.x, 1e-3f)
    }
}
