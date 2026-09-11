package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Tiki Totems — tap-to-remove physics puzzler re-authored from
// docs/apps/tiki-totems.md. The shared Physics2d solver in the same package
// provides the rigid bodies; this file owns block traits, original pack and
// level layouts, the vanish/detonate rules and the totem stability state
// machine. No original pack data, textures or code is used.
// ---------------------------------------------------------------------------

/** Traits carried by each block kind named in the reference binary. */
enum class TikiBlockType(
    val destructible: Boolean,
    val countable: Boolean,
    val vanishing: Boolean,
    val explosive: Boolean,
    val elastic: Boolean,
    val timed: Boolean,
) {
    BLOCK_TOTEM(destructible = false, countable = false, vanishing = false, explosive = false, elastic = false, timed = false),
    BLOCK_NORMAL(destructible = true, countable = true, vanishing = false, explosive = false, elastic = false, timed = false),
    BLOCK_INDESTRUCT(destructible = false, countable = false, vanishing = false, explosive = false, elastic = false, timed = false),
    BLOCK_ELASTIC(destructible = true, countable = true, vanishing = false, explosive = false, elastic = true, timed = false),
    BLOCK_VANISH(destructible = true, countable = true, vanishing = true, explosive = false, elastic = false, timed = false),
    BLOCK_ELASTIC_INDESTRUCT(destructible = false, countable = false, vanishing = false, explosive = false, elastic = true, timed = false),
    BLOCK_EXPLOSIVE_VANISH(destructible = true, countable = true, vanishing = true, explosive = true, elastic = false, timed = false),
    BLOCK_EXPLOSIVE_TIMER(destructible = true, countable = true, vanishing = false, explosive = false, elastic = false, timed = true),
    ;
}

data class TikiBlockDef(
    val type: TikiBlockType,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val angle: Float = 0f,
)

data class TikiLevelDef(
    val packIndex: Int,
    val levelIndex: Int,
    val name: String,
    val totem: TikiBlockDef,
    val blocks: List<TikiBlockDef>,
    val seed: Int,
) {
    /** The level's countable-block target. */
    val toDestroy: Int get() = blocks.count { it.type.countable }
}

data class TikiPack(val label: String, val levels: List<TikiLevelDef>)

/**
 * The 13 original packs (tutorial + 5 classic + 7 extra), all shipped free.
 * Every level is generated from a small authored support-row pattern; the
 * shapes obey the reference size matrix 40/80/120/160/200.
 */
object TikiPacks {
    const val LEVELS_PER_PACK = 8

    val NAMES: List<String> = listOf(
        "tutorial",
        "pack 1",
        "pack 2",
        "pack 3",
        "pack 4",
        "pack 5",
        "extra 2",
        "extra 3",
        "extra 4",
        "extra 5",
        "extra 6",
        "extra 7",
        "extra 8",
    )

    val ALL: List<TikiPack> by lazy { build() }

    fun pack(index: Int): TikiPack = ALL[index.coerceIn(0, ALL.lastIndex)]

    fun level(packIndex: Int, levelIndex: Int): TikiLevelDef =
        pack(packIndex).levels[levelIndex.coerceIn(0, LEVELS_PER_PACK - 1)]

    /**
     * Five-column support-row patterns. Column letters:
     * I indestructible, N normal, E elastic, V vanish, X explosive vanish,
     * T timer bomb. Each V/X pair is authored adjacent so it behaves exactly
     * as the spec describes (vanish on touch, detonate when explosive).
     */
    private val PATTERNS: List<List<String>> = listOf(
        listOf("ININI"),
        listOf("ININI", "ININI"),
        listOf("IVVII", "ININI"),
        listOf("IXXII", "ININI"),
        listOf("ININI", "ININI", "ININI"),
        listOf("IEEII", "ININI"),
        listOf("IVVII", "IVVII"),
        listOf("ITIII", "ININI"),
        listOf("ININI", "IXXII"),
        listOf("IVVII", "ININI", "ININI"),
        listOf("IEEII", "ITIII"),
        listOf("IXXII", "IVVII", "ININI"),
        listOf("ININI", "ININI", "IEEII"),
    )

    private fun build(): List<TikiPack> = NAMES.mapIndexed { packIndex, label ->
        TikiPack(label, (0 until LEVELS_PER_PACK).map { levelIndex -> buildLevel(packIndex, levelIndex) })
    }

    private fun buildLevel(packIndex: Int, levelIndex: Int): TikiLevelDef {
        val pattern = PATTERNS[packIndex]
        val rows = minOf(pattern.size, 1 + levelIndex / 3)
        val cell = 40f
        val baseY = TikiEngine.LAVA_Y - 1f
        val blocks = ArrayList<TikiBlockDef>()

        for (row in 0 until rows) {
            val rowText = pattern[row]
            val y = baseY - cell / 2f - row * cell
            for (column in 0 until 5) {
                val type = when (rowText[column]) {
                    'I' -> TikiBlockType.BLOCK_INDESTRUCT
                    'N' -> TikiBlockType.BLOCK_NORMAL
                    'E' -> TikiBlockType.BLOCK_ELASTIC
                    'V' -> TikiBlockType.BLOCK_VANISH
                    'X' -> TikiBlockType.BLOCK_EXPLOSIVE_VANISH
                    'T' -> TikiBlockType.BLOCK_EXPLOSIVE_TIMER
                    else -> null
                } ?: continue
                val x = 136f - 2f * cell + column * cell
                blocks.add(TikiBlockDef(type, x, y, cell, cell))
            }
        }

        val supportTop = baseY - rows * cell
        val beamH = 40f
        val beamW = 200f
        val beamCenter = supportTop - 1f - beamH / 2f
        blocks.add(TikiBlockDef(TikiBlockType.BLOCK_INDESTRUCT, 136f, beamCenter, beamW, beamH))
        val beamTop = beamCenter - beamH / 2f

        val plinthH = 40f
        val plinthCenter = beamTop - 1f - plinthH / 2f
        blocks.add(TikiBlockDef(TikiBlockType.BLOCK_INDESTRUCT, 136f, plinthCenter, 120f, plinthH))
        val plinthTop = plinthCenter - plinthH / 2f

        val totemH = 160f
        val totemCenter = plinthTop - 1f - totemH / 2f
        val totem = TikiBlockDef(TikiBlockType.BLOCK_TOTEM, 136f, totemCenter, 40f, totemH)

        val topperType = if ((packIndex + levelIndex) % 2 == 0) {
            TikiBlockType.BLOCK_NORMAL
        } else {
            TikiBlockType.BLOCK_ELASTIC
        }
        val topperH = 80f
        val topperY = beamTop - 1f - topperH / 2f
        blocks.add(TikiBlockDef(topperType, 56f, topperY, 40f, topperH))
        blocks.add(TikiBlockDef(topperType, 216f, topperY, 40f, topperH))

        return TikiLevelDef(
            packIndex = packIndex,
            levelIndex = levelIndex,
            name = "${NAMES[packIndex]} ${levelIndex + 1}",
            totem = totem,
            blocks = blocks,
            seed = packIndex * 131 + levelIndex * 17 + 3,
        )
    }
}

enum class TikiStatus { PLAYING, WON, LOST, SKIPPED }

enum class TikiLoseReason { NONE, LAVA, LOST, BROKEN }

enum class TikiTapResult { REMOVED, FUSE, DENIED, RECHARGING, MISS, IGNORED }

class TikiBlock(val body: P2Body, val def: TikiBlockDef) {
    var alive: Boolean = true

    /** Milliseconds left on a lit timer bomb; negative when unlit. */
    var fuseMs: Long = -1L
}

class TikiGame(val levelDef: TikiLevelDef, val world: P2World) {
    var status: TikiStatus = TikiStatus.PLAYING
    var loseReason: TikiLoseReason = TikiLoseReason.NONE
    var toDestroyRemaining: Int = levelDef.toDestroy
    var stableSteps: Int = 0
    var elapsedMs: Long = 0L
    var rechargeMs: Long = 0L
    var explosions: Int = 0
    var stepCount: Int = 0
    var accumulatorMs: Float = 0f
    var totemId: Int = 0
    val blocks = LinkedHashMap<Int, TikiBlock>()
    val events = ArrayList<String>()
    val stableMs: Long get() = stableSteps * 1000L / 60L
    val countableTotal: Int get() = levelDef.toDestroy
}

data class TikiPackProgress(
    val unlocked: Int = 1,
    val stars: Map<Int, Int> = emptyMap(),
) {
    fun stars(levelIndex: Int): Int = stars[levelIndex] ?: 0

    /** A level is playable when its 1-based ordinal is at or below unlocked. */
    fun isUnlocked(levelIndex: Int): Boolean = levelIndex + 1 <= unlocked
}

data class TikiProgress(
    val packs: Map<Int, TikiPackProgress> = emptyMap(),
) {
    fun pack(index: Int): TikiPackProgress = packs[index] ?: TikiPackProgress()

    val totalStars: Int get() = packs.values.sumOf { entry -> entry.stars.values.sum() }

    fun complete(packIndex: Int, levelIndex: Int, stars: Int): TikiProgress {
        val current = pack(packIndex)
        val best = max(current.stars(levelIndex), stars.coerceIn(1, 3))
        val unlocked = if (levelIndex + 1 >= current.unlocked) {
            (levelIndex + 2).coerceAtMost(TikiPacks.LEVELS_PER_PACK + 1)
        } else {
            current.unlocked
        }
        val updated = TikiPackProgress(unlocked, current.stars + (levelIndex to best))
        return copy(packs = packs + (packIndex to updated))
    }
}

object TikiEngine {
    const val DESIGN_W = 272f
    const val DESIGN_H = 480f

    /** Spec constants, with world gravity scaled by the 32 px world scaler. */
    const val WORLD_SCALER = 32f
    const val BASE_GRAVITY_UNITS = 20f
    const val GRAVITY = BASE_GRAVITY_UNITS * WORLD_SCALER
    const val RECHARGE_TIME_MS = 500L
    const val VICTORY_TIME_STEPS = 60
    const val STABLE_LINEAR = 0.2f * WORLD_SCALER
    const val STABLE_ANGULAR = 0.25f
    const val LAVA_Y = 481f

    /** Blast model: full strength within 48, fading out by 192 (design px). */
    const val EXPLOSION_FULL_RADIUS = 48f
    const val EXPLOSION_FALLOFF_RADIUS = 192f
    const val EXPLOSION_SPEED = 420f

    const val TIMER_FUSE_MS = 3000L
    const val TOUCH_SLOP = 1.0f

    private const val STEP_MS_F = 1000f / 60f

    fun explosionFalloff(distance: Float): Float = when {
        distance <= EXPLOSION_FULL_RADIUS -> 1f
        distance >= EXPLOSION_FALLOFF_RADIUS -> 0f
        else -> (EXPLOSION_FALLOFF_RADIUS - distance) / (EXPLOSION_FALLOFF_RADIUS - EXPLOSION_FULL_RADIUS)
    }

    fun isStable(body: P2Body): Boolean =
        isStableValues(body.vx, body.vy, body.omega)

    fun isStableValues(vx: Float, vy: Float, omega: Float): Boolean {
        val speed = sqrt(vx * vx + vy * vy)
        return speed <= STABLE_LINEAR && abs(omega) <= STABLE_ANGULAR
    }

    fun starsFor(elapsedMs: Long): Int = when {
        elapsedMs <= 20_000L -> 3
        elapsedMs <= 45_000L -> 2
        else -> 1
    }

    fun newGame(def: TikiLevelDef): TikiGame {
        val world = P2World(0f, GRAVITY)
        world.createBox(136f, LAVA_Y + 5f, 160f, 5f, 0f, "ground", isStatic = true, friction = 0.9f)
        val game = TikiGame(def, world)
        for (blockDef in def.blocks) {
            createBlock(game, blockDef)
        }
        val totemBody = createBlock(game, def.totem)
        game.totemId = totemBody.id
        return game
    }

    private fun createBlock(game: TikiGame, def: TikiBlockDef): P2Body {
        val type = def.type
        val body = game.world.createBox(
            x = def.x,
            y = def.y,
            halfW = def.w / 2f,
            halfH = def.h / 2f,
            angle = def.angle,
            tag = type.name,
            density = if (type == TikiBlockType.BLOCK_TOTEM) 1.4f else 1f,
            restitution = if (type.elastic) 0.85f else 0.05f,
            friction = 0.9f,
        )
        game.blocks[body.id] = TikiBlock(body, def)
        return body
    }

    fun tap(game: TikiGame, x: Float, y: Float): TikiTapResult {
        if (game.status != TikiStatus.PLAYING) return TikiTapResult.IGNORED
        if (game.rechargeMs > 0L) return TikiTapResult.RECHARGING
        val block = topBlockAt(game, x, y) ?: return TikiTapResult.MISS
        if (!block.def.type.destructible) return TikiTapResult.DENIED
        if (block.def.type.timed && block.fuseMs < 0L) {
            block.fuseMs = TIMER_FUSE_MS
            game.events.add("fuse")
            return TikiTapResult.FUSE
        }
        destroy(game, block)
        game.rechargeMs = RECHARGE_TIME_MS
        game.events.add(if (block.def.type.elastic) "boing" else "break")
        return TikiTapResult.REMOVED
    }

    private fun topBlockAt(game: TikiGame, x: Float, y: Float): TikiBlock? {
        val list = game.blocks.values.toList()
        for (i in list.indices.reversed()) {
            val block = list[i]
            if (!block.alive) continue
            if (block.body.containsPoint(x, y)) return block
        }
        return null
    }

    private fun destroy(game: TikiGame, block: TikiBlock) {
        if (!block.alive) return
        block.alive = false
        game.world.remove(block.body)
        if (block.def.type.countable) {
            game.toDestroyRemaining = (game.toDestroyRemaining - 1).coerceAtLeast(0)
        }
    }

    fun stepSteps(game: TikiGame, steps: Int): TikiGame {
        repeat(steps) { step(game, 17L) }
        return game
    }

    fun step(game: TikiGame, dtMs: Long): TikiGame {
        if (game.status != TikiStatus.PLAYING) return game
        game.events.clear()
        game.accumulatorMs += dtMs.toFloat().coerceAtMost(100f)
        var guard = 0
        while (game.accumulatorMs >= STEP_MS_F && guard < 6) {
            game.accumulatorMs -= STEP_MS_F
            guard++
            if (game.status != TikiStatus.PLAYING) break
            if (game.rechargeMs > 0L) game.rechargeMs = (game.rechargeMs - 17L).coerceAtLeast(0L)
            updateFuses(game)
            Physics2d.stepFixed(game.world, 1)
            game.stepCount++
            game.elapsedMs = game.stepCount * 1000L / 60L
            processTouchRules(game)
            cullEscapees(game)
            checkTotem(game)
            updateVictory(game)
        }
        if (game.status != TikiStatus.PLAYING) game.accumulatorMs = 0f
        return game
    }

    private fun updateFuses(game: TikiGame) {
        for (block in game.blocks.values) {
            if (!block.alive || block.fuseMs < 0L) continue
            block.fuseMs -= 17L
            if (block.fuseMs <= 0L) {
                block.fuseMs = -1L
                val x = block.body.x
                val y = block.body.y
                destroy(game, block)
                game.events.add("break")
                explode(game, x, y)
            }
        }
    }

    private fun processTouchRules(game: TikiGame) {
        val alive = game.blocks.values.filter { it.alive }
        for (i in alive.indices) {
            for (j in i + 1 until alive.size) {
                val a = alive[i]
                val b = alive[j]
                if (!a.def.type.vanishing || !b.def.type.vanishing) continue
                if (!touching(a.body, b.body)) continue
                val midpointX = (a.body.x + b.body.x) * 0.5f
                val midpointY = (a.body.y + b.body.y) * 0.5f
                val detonate = a.def.type.explosive || b.def.type.explosive
                destroy(game, a)
                destroy(game, b)
                game.events.add("break")
                if (detonate) explode(game, midpointX, midpointY)
            }
        }
    }

    private fun cullEscapees(game: TikiGame) {
        for (block in game.blocks.values) {
            if (!block.alive || !block.def.type.countable) continue
            val body = block.body
            if (body.y > LAVA_Y + 120f || body.x < -140f || body.x > DESIGN_W + 140f || body.y < -400f) {
                destroy(game, block)
                game.events.add("break")
            }
        }
    }

    private fun checkTotem(game: TikiGame) {
        if (game.status != TikiStatus.PLAYING) return
        val totem = game.blocks[game.totemId] ?: return
        if (!totem.alive) {
            fail(game, TikiLoseReason.BROKEN)
            return
        }
        val body = totem.body
        for (vertex in body.vertices()) {
            if (vertex.y >= LAVA_Y - 1f) {
                fail(game, TikiLoseReason.LAVA)
                return
            }
        }
        if (body.y > LAVA_Y + 60f || body.x < -100f || body.x > DESIGN_W + 100f || body.y < -260f) {
            fail(game, TikiLoseReason.LOST)
        }
    }

    private fun updateVictory(game: TikiGame) {
        if (game.status != TikiStatus.PLAYING) return
        if (game.toDestroyRemaining > 0) {
            game.stableSteps = 0
            return
        }
        val totem = game.blocks[game.totemId] ?: return
        if (!totem.alive) return
        if (isStable(totem.body)) {
            game.stableSteps++
        } else {
            game.stableSteps = 0
        }
        if (game.stableSteps >= VICTORY_TIME_STEPS) {
            game.status = TikiStatus.WON
            game.events.add("win")
        }
    }

    private fun fail(game: TikiGame, reason: TikiLoseReason) {
        game.status = TikiStatus.LOST
        game.loseReason = reason
        game.events.add("lose")
    }

    /** Radial impulse; full strength at the centre, gone past 192 px. */
    fun explode(game: TikiGame, x: Float, y: Float) {
        game.explosions++
        game.events.add("blast")
        val totem = game.blocks[game.totemId]
        if (totem != null && totem.alive) {
            val dx = totem.body.x - x
            val dy = totem.body.y - y
            if (sqrt(dx * dx + dy * dy) <= EXPLOSION_FULL_RADIUS) {
                fail(game, TikiLoseReason.BROKEN)
            }
        }
        for (block in game.blocks.values) {
            if (!block.alive) continue
            val body = block.body
            val dx = body.x - x
            val dy = body.y - y
            val dist = sqrt(dx * dx + dy * dy)
            val falloff = explosionFalloff(dist)
            if (falloff <= 0f) continue
            val nx: Float
            val ny: Float
            if (dist < 1e-3f) {
                nx = 0f
                ny = -1f
            } else {
                nx = dx / dist
                ny = dy / dist
            }
            body.vx += nx * EXPLOSION_SPEED * falloff
            body.vy += ny * EXPLOSION_SPEED * falloff
            body.omega += (nx - ny) * 0.5f * falloff
        }
    }

    private fun touching(a: P2Body, b: P2Body): Boolean {
        val ba = bounds(a)
        val bb = bounds(b)
        return ba[0] <= bb[2] + TOUCH_SLOP &&
            bb[0] <= ba[2] + TOUCH_SLOP &&
            ba[1] <= bb[3] + TOUCH_SLOP &&
            bb[1] <= ba[3] + TOUCH_SLOP
    }

    private fun bounds(body: P2Body): FloatArray {
        if (body.shape == P2Shape.CIRCLE) {
            return floatArrayOf(
                body.x - body.radius,
                body.y - body.radius,
                body.x + body.radius,
                body.y + body.radius,
            )
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (vertex in body.vertices()) {
            if (vertex.x < minX) minX = vertex.x
            if (vertex.y < minY) minY = vertex.y
            if (vertex.x > maxX) maxX = vertex.x
            if (vertex.y > maxY) maxY = vertex.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun snapshot(game: TikiGame): String {
        val sb = StringBuilder(512)
        sb.append(game.status).append('|').append(game.stepCount).append('|')
            .append(game.toDestroyRemaining).append('|').append(game.explosions).append('|')
        for (block in game.blocks.values) {
            if (!block.alive) continue
            sb.append(block.body.id).append(':')
                .append(Physics2d.round3(block.body.x)).append(',')
                .append(Physics2d.round3(block.body.y)).append(',')
                .append(Physics2d.round3(block.body.angle)).append(',')
                .append(Physics2d.round3(block.body.vx)).append(',')
                .append(Physics2d.round3(block.body.vy)).append(',')
                .append(Physics2d.round3(block.body.omega)).append(';')
        }
        return sb.toString()
    }

    fun encodeProgress(progress: TikiProgress): String {
        val packs = progress.packs.entries.sortedBy { it.key }.joinToString(";") { (index, entry) ->
            val stars = entry.stars.entries.sortedBy { it.key }.joinToString(",") { (level, count) -> "$level:$count" }
            "$index:${entry.unlocked}:$stars"
        }
        return "tp1|$packs"
    }

    fun decodeProgress(blob: String?): TikiProgress {
        if (blob.isNullOrBlank()) return TikiProgress()
        return try {
            val parts = blob.split('|')
            if (parts.size < 2 || parts[0] != "tp1") return TikiProgress()
            val packs = HashMap<Int, TikiPackProgress>()
            if (parts[1].isNotEmpty()) {
                parts[1].split(';').forEach { packText ->
                    val bits = packText.split(':')
                    if (bits.size < 3) return@forEach
                    val packIndex = bits[0].toIntOrNull() ?: return@forEach
                    val unlocked = bits[1].toIntOrNull()?.coerceIn(1, TikiPacks.LEVELS_PER_PACK + 1) ?: 1
                    val starsText = bits.drop(2).joinToString(":")
                    val stars = HashMap<Int, Int>()
                    if (starsText.isNotEmpty()) {
                        starsText.split(',').forEach { starText ->
                            val starBits = starText.split(':')
                            if (starBits.size == 2) {
                                val level = starBits[0].toIntOrNull() ?: return@forEach
                                val count = starBits[1].toIntOrNull() ?: return@forEach
                                if (level in 0 until TikiPacks.LEVELS_PER_PACK) stars[level] = count.coerceIn(1, 3)
                            }
                        }
                    }
                    packs[packIndex] = TikiPackProgress(unlocked, stars)
                }
            }
            TikiProgress(packs)
        } catch (_: Exception) {
            TikiProgress()
        }
    }
}
