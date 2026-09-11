package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.games.EchoKind
import com.heretek.dorado_hd.ui.apps.games.EchoesAchievement
import com.heretek.dorado_hd.ui.apps.games.EchoesArenas
import com.heretek.dorado_hd.ui.apps.games.EchoesDifficulty
import com.heretek.dorado_hd.ui.apps.games.EchoesEnemy
import com.heretek.dorado_hd.ui.apps.games.EchoesEngine
import com.heretek.dorado_hd.ui.apps.games.EchoesInput
import com.heretek.dorado_hd.ui.apps.games.EchoesMode
import com.heretek.dorado_hd.ui.apps.games.EchoesPoint
import com.heretek.dorado_hd.ui.apps.games.EchoesPowerup
import com.heretek.dorado_hd.ui.apps.games.EchoesProgress
import com.heretek.dorado_hd.ui.apps.games.EchoesRank
import com.heretek.dorado_hd.ui.apps.games.EchoesStatus
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoesTest {

    private fun game(
        level: Int = 0,
        seed: Int = 7,
        mode: EchoesMode = EchoesMode.CAMPAIGN,
        difficulty: EchoesDifficulty = EchoesDifficulty.MEDIUM,
    ) = EchoesEngine.newGame(mode, difficulty, seed, level)

    private fun tick(state: com.heretek.dorado_hd.ui.apps.games.EchoesGame, input: EchoesInput = EchoesInput.NONE) =
        EchoesEngine.step(state, EchoesEngine.TICK_MS, input)

    private fun clearEnemies(state: com.heretek.dorado_hd.ui.apps.games.EchoesGame) =
        state.copy(enemies = emptyList())

    private fun collectAll(start: com.heretek.dorado_hd.ui.apps.games.EchoesGame): com.heretek.dorado_hd.ui.apps.games.EchoesGame {
        var state = start
        var guard = 0
        while (state.collectedCrystals < state.totalCrystals && state.status == EchoesStatus.PLAYING && guard++ < 200) {
            val crystal = state.crystals.firstOrNull { !it.collected && !it.secret } ?: break
            state = clearEnemies(
                state.copy(player = state.player.copy(x = crystal.x, y = crystal.y, vx = 0.0, vy = 0.0)),
            )
            state = tick(state)
            state = clearEnemies(state)
        }
        return state
    }

    @Test
    fun `engine is deterministic for the same seed and inputs`() {
        var a = game(seed = 1234)
        var b = game(seed = 1234)
        val script = listOf(
            EchoesInput(1, 0), EchoesInput(1, 0), EchoesInput(0, 1), EchoesInput(-1, 0),
            EchoesInput(-1, -1), EchoesInput(0, -1), EchoesInput.NONE, EchoesInput(1, 0),
        )
        script.forEach { input ->
            a = tick(a, input)
            b = tick(b, input)
        }
        assertEquals(a, b)
        assertEquals(a.player, b.player)
        assertEquals(a.crystals, b.crystals)
    }

    @Test
    fun `same seed assigns identical crystal powerups`() {
        val a = game(seed = 1)
        val b = game(seed = 1)
        assertEquals(a.crystals.map { it.powerup }, b.crystals.map { it.powerup })
    }

    @Test
    fun `echo replay retraces the recorded input path`() {
        var state = game()
        val inputs = listOf(
            EchoesInput(1, 0), EchoesInput(1, 0), EchoesInput(1, 0), EchoesInput(1, 0),
            EchoesInput(0, 1), EchoesInput(0, 1), EchoesInput(0, 1), EchoesInput(0, 1),
        )
        val recorded = ArrayList<EchoesPoint>()
        for (input in inputs) {
            state = tick(state, input)
            recorded += EchoesPoint(state.player.x, state.player.y)
        }
        val crystal = state.crystals.first { !it.collected }
        state = clearEnemies(state.copy(player = state.player.copy(x = crystal.x, y = crystal.y, vx = 0.0, vy = 0.0)))
        state = tick(state)
        assertEquals(1, state.collectedCrystals)
        val echo = state.enemies.first { it.kind == EchoKind.ECHO }
        assertEquals(state.spawn.x, echo.x, 1e-9)
        assertEquals(state.spawn.y, echo.y, 1e-9)
        for (index in inputs.indices) {
            state = tick(state)
            val replaying = state.enemies.first { it.kind == EchoKind.ECHO }
            assertEquals("tick ${index + 1}", recorded[index].x, replaying.x, 1e-6)
            assertEquals("tick ${index + 1}", recorded[index].y, replaying.y, 1e-6)
        }
    }

    @Test
    fun `collecting a crystal scores and marks it collected`() {
        var state = game()
        val crystal = state.crystals.first()
        state = clearEnemies(state.copy(player = state.player.copy(x = crystal.x, y = crystal.y)))
        state = tick(state)
        assertTrue(state.crystals.first { it.id == crystal.id }.collected)
        assertEquals(1, state.score)
        assertEquals(1, state.collectedCrystals)
    }

    @Test
    fun `collecting every crystal wins a campaign level`() {
        val state = collectAll(game(level = 2))
        assertEquals(EchoesStatus.WON, state.status)
        assertEquals(state.totalCrystals, state.collectedCrystals)
    }

    @Test
    fun `clearing an endless mode starts the next round`() {
        var state = game(mode = EchoesMode.ARCADE)
        var guard = 0
        while (state.round == 1 && guard++ < 200) {
            val crystal = state.crystals.firstOrNull { !it.collected && !it.secret } ?: break
            state = clearEnemies(
                state.copy(player = state.player.copy(x = crystal.x, y = crystal.y, vx = 0.0, vy = 0.0)),
            )
            state = tick(state)
            state = clearEnemies(state)
        }
        assertEquals(EchoesStatus.PLAYING, state.status)
        assertEquals(2, state.round)
        assertEquals(0, state.collectedCrystals)
        assertTrue(state.score > state.totalCrystals)
    }

    @Test
    fun `hitting an echo costs a life and grants invulnerability`() {
        var state = clearEnemies(game())
        val echo = EchoesEnemy(id = 99, kind = EchoKind.ECHO, x = state.player.x, y = state.player.y)
        state = state.copy(enemies = listOf(echo))
        state = tick(state)
        assertEquals(EchoesDifficulty.MEDIUM.lives - 1, state.lives)
        assertEquals(EchoesEngine.STILL_SAFE_AFTER_HIT_MS, state.effects.invulnMs, 1e-6)
        val lives = state.lives
        state = state.copy(enemies = listOf(echo.copy(x = state.player.x, y = state.player.y)))
        state = tick(state)
        state = state.copy(enemies = listOf(echo.copy(x = state.player.x, y = state.player.y)))
        state = tick(state)
        assertEquals(lives, state.lives)
    }

    @Test
    fun `invulnerability window expires and lets the next hit land`() {
        var state = clearEnemies(game())
        val echo = EchoesEnemy(id = 99, kind = EchoKind.ECHO, x = state.player.x, y = state.player.y)
        state = tick(state.copy(enemies = listOf(echo)))
        val lives = state.lives
        repeat(20) { state = clearEnemies(tick(state)) }
        state = state.copy(enemies = listOf(echo.copy(x = state.player.x, y = state.player.y)))
        state = tick(state)
        assertEquals(lives - 1, state.lives)
    }

    @Test
    fun `lives below zero ends the run`() {
        var state = clearEnemies(game().copy(lives = 0))
        val spike = EchoesEnemy(id = 99, kind = EchoKind.ECHO, x = state.player.x, y = state.player.y)
        state = tick(state.copy(enemies = listOf(spike)))
        assertEquals(-1, state.lives)
        assertEquals(EchoesStatus.LOST, state.status)
    }

    @Test
    fun `flyers are lethal on contact`() {
        var state = clearEnemies(game())
        val flyer = EchoesEnemy(
            id = 5,
            kind = EchoKind.FLYER,
            x = state.player.x,
            y = state.player.y,
            baseX = state.player.x,
            baseY = state.player.y,
            amplitude = 28.0,
        )
        state = tick(state.copy(enemies = listOf(flyer)))
        assertEquals(EchoesDifficulty.MEDIUM.lives - 1, state.lives)
    }

    @Test
    fun `authored spike tiles are lethal`() {
        val arena = EchoesArenas.ALL[4]
        var state = clearEnemies(game(level = 4))
        val spike = arena.let { a ->
            var found = EchoesPoint(0.0, 0.0)
            for (row in 0 until a.rows) {
                for (col in 0 until a.cols) {
                    if (a.spikeAt(col, row)) found = EchoesPoint((col + 0.5) * EchoesEngine.TILE, (row + 0.5) * EchoesEngine.TILE)
                }
            }
            found
        }
        state = state.copy(player = state.player.copy(x = spike.x, y = spike.y))
        state = tick(state)
        assertEquals(EchoesDifficulty.MEDIUM.lives - 1, state.lives)
    }

    @Test
    fun `time freeze stops echo movement`() {
        var state = clearEnemies(game())
        val echo = EchoesEnemy(id = 3, kind = EchoKind.ECHO, x = 200.0, y = 120.0, spawnTick = 0)
        state = state.copy(
            enemies = listOf(echo),
            effects = state.effects.copy(freezeMs = EchoesEngine.TIME_FREEZE_MS),
        )
        repeat(5) { state = tick(state) }
        assertEquals(200.0, state.enemies.first().x, 1e-9)
        assertEquals(120.0, state.enemies.first().y, 1e-9)
    }

    @Test
    fun `shrink powerup scales the player and expires`() {
        var state = game()
        val target = state.crystals.first()
        state = state.copy(
            crystals = state.crystals.map {
                if (it.id == target.id) it.copy(powerup = EchoesPowerup.SHRINK) else it
            },
        )
        state = clearEnemies(state.copy(player = state.player.copy(x = target.x, y = target.y)))
        state = tick(state)
        assertEquals(EchoesEngine.SHRINK_SCALE, state.player.scale, 1e-9)
        assertTrue(state.effects.shrinkMs > 0.0)
        val ticks = (EchoesEngine.SHRINK_MS / EchoesEngine.TICK_MS).toInt() + 4
        repeat(ticks) { state = clearEnemies(tick(state)) }
        assertEquals(1.0, state.player.scale, 1e-9)
    }

    @Test
    fun `pulse ring destroys echoes within its radius`() {
        var state = clearEnemies(game())
        val near = EchoesEnemy(id = 1, kind = EchoKind.ECHO, x = state.player.x + 20.0, y = state.player.y, spawnTick = 0)
        val far = EchoesEnemy(id = 2, kind = EchoKind.ECHO, x = state.player.x + 300.0, y = state.player.y, spawnTick = 0)
        state = state.copy(
            enemies = listOf(near, far),
            effects = state.effects.copy(pulseMs = EchoesEngine.PULSE_RING_MS),
        )
        repeat(10) { state = tick(state) }
        assertFalse(state.enemies.first { it.id == 1 }.alive)
        assertTrue(state.enemies.first { it.id == 2 }.alive)
    }

    @Test
    fun `extra life powerup extends the run`() {
        var state = game()
        val target = state.crystals.first()
        state = state.copy(
            crystals = state.crystals.map {
                if (it.id == target.id) it.copy(powerup = EchoesPowerup.EXTRA_LIFE) else it
            },
        )
        val before = state.lives
        state = clearEnemies(state.copy(player = state.player.copy(x = target.x, y = target.y)))
        state = tick(state)
        assertEquals(before + 1, state.lives)
    }

    @Test
    fun `secret powerup drops a bonus crystal worth two`() {
        var state = game()
        val target = state.crystals.first()
        state = state.copy(
            crystals = state.crystals.map {
                if (it.id == target.id) it.copy(powerup = EchoesPowerup.SECRET) else it
            },
        )
        state = clearEnemies(state.copy(player = state.player.copy(x = target.x, y = target.y)))
        state = tick(state)
        assertTrue(state.crystals.any { it.secret && it.value == EchoesEngine.SECRET_VALUE })
    }

    @Test
    fun `puck stops against an arena wall`() {
        var state = game(level = 0)
        state = clearEnemies(state.copy(player = state.player.copy(x = 40.0, y = 88.0, vx = 0.0, vy = 0.0)))
        repeat(90) { state = clearEnemies(tick(state, EchoesInput(1, 0))) }
        val wallLeft = 4.0 * EchoesEngine.TILE
        assertTrue(state.player.x <= wallLeft - EchoesEngine.PLAYER_RADIUS + 1e-6)
    }

    @Test
    fun `input history survives an encode decode round trip`() {
        var state = game()
        state = tick(state, EchoesInput(1, 0))
        state = tick(state, EchoesInput(1, 0))
        state = tick(state, EchoesInput(-1, 1))
        state = tick(state, EchoesInput.NONE)
        val decoded = EchoesEngine.decode(EchoesEngine.encode(state))
        assertNotNull(decoded)
        assertEquals(state.inputs, decoded!!.inputs)
        assertEquals(state.tick, decoded.tick)
        assertEquals(state.score, decoded.score)
        assertEquals(state.lives, decoded.lives)
        assertEquals(state.player.x, decoded.player.x, 0.01)
        assertEquals(state.player.y, decoded.player.y, 0.01)
        assertEquals(state.crystals, decoded.crystals)
        assertEquals(state.mode, decoded.mode)
        assertEquals(state.difficulty, decoded.difficulty)
    }

    @Test
    fun `progress survives an encode decode round trip`() {
        val progress = EchoesProgress(
            unlocked = 5,
            best = mapOf(0 to 12, 1 to 9, 2 to 3),
            totalCrystals = 220,
            echoesSpawned = 90,
            campaignCleared = true,
        )
        assertEquals(progress, EchoesEngine.decodeProgress(EchoesEngine.encodeProgress(progress)))
        assertEquals(EchoesProgress(), EchoesEngine.decodeProgress(null))
    }

    @Test
    fun `achievement ranks scale with progress`() {
        assertEquals(EchoesRank.NONE, EchoesEngine.achievementRank(EchoesAchievement.TREASURE_SEEKER, EchoesProgress()))
        assertEquals(
            EchoesRank.BRONZE,
            EchoesEngine.achievementRank(EchoesAchievement.TREASURE_SEEKER, EchoesProgress(totalCrystals = 30)),
        )
        assertEquals(
            EchoesRank.JEWEL,
            EchoesEngine.achievementRank(
                EchoesAchievement.CHAMPION,
                EchoesProgress(campaignCleared = true),
            ),
        )
    }

    @Test
    fun `input vectors quantise to eight directions`() {
        assertEquals(EchoesInput(1, 0), EchoesInput.fromVector(1.0, 0.0))
        assertEquals(EchoesInput(1, 1), EchoesInput.fromVector(0.7, 0.7))
        assertEquals(EchoesInput(0, -1), EchoesInput.fromVector(0.0, -1.0))
        assertEquals(EchoesInput(-1, 1), EchoesInput.fromVector(-0.8, 0.6))
        assertEquals(EchoesInput.NONE, EchoesInput.fromVector(0.1, 0.05))
        for (code in 0..8) {
            assertEquals(code, EchoesInput.fromCode(code).code)
        }
    }

    @Test
    fun `spec constants are preserved`() {
        assertEquals(0.05, EchoesEngine.SNAPSHOT_FREQUENCY_S, 1e-9)
        assertEquals(3, EchoesEngine.SNAPSHOT_TICKS)
        assertEquals(5.25, EchoesEngine.MOVE_SPEED, 1e-9)
        assertEquals(0.7, EchoesEngine.MOVE_DAMPEN, 1e-9)
        assertEquals(21.0, EchoesEngine.PLAYER_RADIUS, 1e-9)
        assertEquals(4.285714, EchoesEngine.TIME_FREEZE_MS / 1000.0, 1e-5)
        assertEquals(5.714286, EchoesEngine.PLAYER_GHOST_MS / 1000.0, 1e-5)
        assertEquals(11.428572, EchoesEngine.SHRINK_MS / 1000.0, 1e-4)
        assertEquals(0.5, EchoesEngine.PULSE_RING_MS / 1000.0, 1e-9)
        assertEquals(0.4, EchoesEngine.SHRINK_SCALE, 1e-9)
    }

    @Test
    fun `all authored arenas parse with equal rows and floor spawns`() {
        assertEquals(12, EchoesArenas.ALL.size)
        for (arena in EchoesArenas.ALL) {
            assertEquals(30, arena.cols)
            assertEquals(17, arena.rows)
            assertTrue(arena.name.isNotBlank())
            assertTrue(arena.crystals.size >= 4)
            assertFalse(arena.wallAt((arena.spawn.x / EchoesEngine.TILE).toInt(), (arena.spawn.y / EchoesEngine.TILE).toInt()))
            assertTrue(arena.floorCells.isNotEmpty())
        }
    }

    @Test
    fun `every crystal is reachable by a radius twenty one puck`() {
        for (arena in EchoesArenas.ALL) {
            val reachable = reachableNodes(arena)
            assertTrue("${arena.name} has an unreachable spawn", near(reachable, arena.spawn, 10.0))
            for (crystal in arena.crystals) {
                assertTrue(
                    "${arena.name} crystal ${crystal.x},${crystal.y} is unreachable",
                    near(reachable, crystal, 10.0),
                )
            }
        }
    }

    private fun near(nodes: Set<Long>, point: EchoesPoint, tolerance: Double): Boolean {
        val step = 4
        val i0 = ((point.x - tolerance) / step).toInt() - 1
        val i1 = ((point.x + tolerance) / step).toInt() + 1
        val j0 = ((point.y - tolerance) / step).toInt() - 1
        val j1 = ((point.y + tolerance) / step).toInt() + 1
        for (i in i0..i1) {
            for (j in j0..j1) {
                if (key(i, j) !in nodes) continue
                if (abs(i * 4.0 - point.x) <= tolerance && abs(j * 4.0 - point.y) <= tolerance) return true
            }
        }
        return false
    }

    private fun key(i: Int, j: Int): Long = (i.toLong() shl 32) xor (j.toLong() and 0xFFFFFFFFL)

    private fun reachableNodes(arena: com.heretek.dorado_hd.ui.apps.games.EchoesArena): Set<Long> {
        val maxI = arena.cols * EchoesEngine.TILE.toInt() / 4
        val maxJ = arena.rows * EchoesEngine.TILE.toInt() / 4
        fun clear(i: Int, j: Int): Boolean {
            val cx = i * 4.0
            val cy = j * 4.0
            val colMin = floor((cx - EchoesEngine.PLAYER_RADIUS) / EchoesEngine.TILE).toInt()
            val colMax = floor((cx + EchoesEngine.PLAYER_RADIUS) / EchoesEngine.TILE).toInt()
            val rowMin = floor((cy - EchoesEngine.PLAYER_RADIUS) / EchoesEngine.TILE).toInt()
            val rowMax = floor((cy + EchoesEngine.PLAYER_RADIUS) / EchoesEngine.TILE).toInt()
            for (row in rowMin..rowMax) {
                for (col in colMin..colMax) {
                    if (!arena.wallAt(col, row)) continue
                    val nx = (cx).coerceIn(col * EchoesEngine.TILE, (col + 1) * EchoesEngine.TILE)
                    val ny = (cy).coerceIn(row * EchoesEngine.TILE, (row + 1) * EchoesEngine.TILE)
                    if (hypot(cx - nx, cy - ny) < EchoesEngine.PLAYER_RADIUS) return false
                }
            }
            return true
        }
        val visited = HashSet<Long>()
        val startI = (arena.spawn.x / 4.0).toInt()
        val startJ = (arena.spawn.y / 4.0).toInt()
        val queue = ArrayDeque<IntArray>()
        var best: IntArray? = null
        var bestDistance = Double.MAX_VALUE
        for (i in 1 until maxI) {
            for (j in 1 until maxJ) {
                if (!clear(i, j)) continue
                val distance = hypot(i * 4.0 - arena.spawn.x, j * 4.0 - arena.spawn.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = intArrayOf(i, j)
                }
            }
        }
        val seed = best ?: intArrayOf(startI, startJ)
        if (!clear(seed[0], seed[1])) return visited
        visited += key(seed[0], seed[1])
        queue += seed
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (delta in listOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))) {
                val i = node[0] + delta[0]
                val j = node[1] + delta[1]
                if (i <= 0 || j <= 0 || i >= maxI || j >= maxJ) continue
                if (key(i, j) in visited) continue
                if (!clear(i, j)) continue
                visited += key(i, j)
                queue += intArrayOf(i, j)
            }
        }
        return visited
    }
}
