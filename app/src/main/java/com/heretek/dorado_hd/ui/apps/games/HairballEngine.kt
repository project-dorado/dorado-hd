package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.min

/**
 * Hairball — pure clean-room engine for the Zune HD "drain descent" game.
 *
 * Behaviour re-derived from docs/apps/hairball.md; no Microsoft code or assets.
 * The player is a 20x20 hairball that always falls 12.8 px per 30 fps frame
 * while sludge rows scroll upward. Landing on a block snaps the hairball to the
 * block top; blocks keep pushing it toward the top edge, and losing the screen
 * ends the run. The only score source is surviving: +1 per simulated frame.
 *
 * Coordinates are the device design space (272x480, y grows downward).
 */
const val HAIRBALL_VIEW_W = 272f
const val HAIRBALL_VIEW_H = 480f
const val HAIRBALL_FRAME_MS = 1000f / 30f
const val HAIRBALL_FALL = 12.8f
const val HAIRBALL_PLAYER = 20f
const val HAIRBALL_BLOCK_W = 40f
const val HAIRBALL_BLOCK_H = 40f
const val HAIRBALL_COLUMN_SPACING = 42f
const val HAIRBALL_ROW_SPACING = 80f
const val HAIRBALL_ROW_BASE_Y = 400f
const val HAIRBALL_ROW_RECYCLE_Y = 720f
const val HAIRBALL_SPEED_BASE = 3.5f
const val HAIRBALL_SPEED_STEP = 0.3f
const val HAIRBALL_SPEED_CAP = 15f
const val HAIRBALL_SPEED_INTERVAL_MS = 12_000L
const val HAIRBALL_BLOCK_POOL = 40
const val HAIRBALL_LEFT_START_X = 32f
const val HAIRBALL_RIGHT_START_X = 240f
const val HAIRBALL_TILT_DEADZONE = 0.05f
const val HAIRBALL_TILT_BASE = 7f
const val HAIRBALL_TILT_GAIN = 6f
const val HAIRBALL_MIN_X = 20f
const val HAIRBALL_MAX_X = 252f
const val HAIRBALL_MAX_Y = 460f
const val HAIRBALL_LOSS_Y = -20f
const val HAIRBALL_START_X = 136f
const val HAIRBALL_START_Y = 40f

/** Deterministic LCG shared by the engine (same family as the other games). */
class HairballRandom(seed: Int) {
    var state: Int = seed

    fun nextSeed(): Int {
        state = state * 1103515245 + 12345
        return state
    }

    fun next(bound: Int): Int {
        if (bound <= 0) return 0
        val raw = (nextSeed() ushr 16) and 0x7FFF
        return raw % bound
    }
}

/** One recycled sludge row: a y line of occupied slot indices. */
data class HairballRow(
    val y: Float,
    val slots: List<Int>,
    val leftToRight: Boolean,
)

data class HairballBlock(val x: Float, val y: Float)

sealed interface HairballEvent {
    data object Landed : HairballEvent
    data object RowSpawned : HairballEvent
    data object SpeedUp : HairballEvent
    data object Lost : HairballEvent
}

data class HairballState(
    val playerX: Float,
    val playerY: Float,
    val rows: List<HairballRow>,
    val speed: Float,
    val elapsedMs: Long,
    val score: Int,
    val seed: Int,
    val nextRowLeftToRight: Boolean,
    val paused: Boolean = false,
    val lost: Boolean = false,
    val events: List<HairballEvent> = emptyList(),
)

object HairballEngine {

    fun slotX(row: HairballRow, slot: Int): Float =
        if (row.leftToRight) {
            HAIRBALL_LEFT_START_X + slot * HAIRBALL_COLUMN_SPACING
        } else {
            HAIRBALL_RIGHT_START_X - slot * HAIRBALL_COLUMN_SPACING
        }

    fun blocks(state: HairballState): List<HairballBlock> =
        state.rows.flatMap { row -> row.slots.map { HairballBlock(slotX(row, it), row.y) } }

    fun blockCount(state: HairballState): Int = state.rows.sumOf { it.slots.size }

    /** 3.5 px/frame, +0.3 every 12 s of total run time, clamped at 15. */
    fun speedFor(elapsedMs: Long): Float {
        val steps = (elapsedMs / HAIRBALL_SPEED_INTERVAL_MS).toInt()
        return min(HAIRBALL_SPEED_CAP, HAIRBALL_SPEED_BASE + steps * HAIRBALL_SPEED_STEP)
    }

    /**
     * Tilt envelope: +7..13 px/frame right when tilted past the 0.05 deadzone,
     * mirrored left; inside the deadzone the hairball does not drift.
     */
    fun tiltVelocity(tiltX: Float): Float = when {
        tiltX > HAIRBALL_TILT_DEADZONE -> HAIRBALL_TILT_BASE + HAIRBALL_TILT_GAIN * tiltX
        tiltX < -HAIRBALL_TILT_DEADZONE -> -(HAIRBALL_TILT_BASE + HAIRBALL_TILT_GAIN * -tiltX)
        else -> 0f
    }

    /**
     * One row roll: scan the six slots in placement order, skip each with
     * probability 1/6, stop after five placed or six scanned.
     */
    fun rollSlots(seed: Int): Pair<List<Int>, Int> {
        val rng = HairballRandom(seed)
        val slots = mutableListOf<Int>()
        var scanned = 0
        while (slots.size < 5 && scanned < 6) {
            val slot = scanned
            scanned++
            if (rng.next(6) == 1) continue
            slots += slot
        }
        return slots.toList() to rng.state
    }

    fun newGame(seed: Int): HairballState {
        val rng = HairballRandom(seed)
        val leftToRight = rng.next(2) == 0
        val (slots, nextSeed) = rollSlots(rng.state)
        return HairballState(
            playerX = HAIRBALL_START_X,
            playerY = HAIRBALL_START_Y,
            rows = listOf(HairballRow(HAIRBALL_ROW_BASE_Y, slots, leftToRight)),
            speed = HAIRBALL_SPEED_BASE,
            elapsedMs = 0L,
            score = 0,
            seed = nextSeed,
            nextRowLeftToRight = !leftToRight,
        )
    }

    fun step(state: HairballState, dtMs: Long, tiltX: Float = 0f): HairballState {
        if (state.paused || state.lost || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<HairballEvent>()
        val frames = dtMs / HAIRBALL_FRAME_MS
        val elapsed = state.elapsedMs + dtMs
        val speed = speedFor(elapsed)
        if (speed > state.speed) events += HairballEvent.SpeedUp

        // Rows scroll upward; a row is recycled once fully above the top edge.
        var rows = state.rows
            .map { it.copy(y = it.y - speed * frames) }
            .filter { it.y > -HAIRBALL_BLOCK_H }

        var count = rows.sumOf { it.slots.size }
        var seed = state.seed
        var nextLeft = state.nextRowLeftToRight
        var guard = 0
        // Queue a new row when >= 4 pool blocks are free, the grid has room for
        // a fifth row, and the deepest row has scrolled up to (at or above) the
        // 720 recycle line.
        while (guard < 8 && rows.size < 5 && count <= HAIRBALL_BLOCK_POOL - 4) {
            guard++
            val deepest = rows.maxByOrNull { it.y } ?: break
            if (deepest.y > HAIRBALL_ROW_RECYCLE_Y) break
            val (slots, rolled) = rollSlots(seed)
            seed = rolled
            val free = HAIRBALL_BLOCK_POOL - count
            val capped = if (slots.size > free) slots.take(free) else slots
            val direction = nextLeft
            nextLeft = !nextLeft
            if (capped.isEmpty()) continue
            rows = rows + HairballRow(deepest.y + HAIRBALL_ROW_SPACING, capped, direction)
            count += capped.size
            events += HairballEvent.RowSpawned
        }

        // Tilt moves horizontally (no vertical control: gravity always wins).
        var playerX = (state.playerX + tiltVelocity(tiltX) * frames)
            .coerceIn(HAIRBALL_MIN_X, HAIRBALL_MAX_X)
        var playerY = state.playerY + HAIRBALL_FALL * frames

        // AABB against active sludge; snap to the highest (smallest y) surface.
        var surface = Float.MAX_VALUE
        var landed = false
        for (row in rows) {
            for (slot in row.slots) {
                val bx = slotX(row, slot)
                val overlapX = playerX + HAIRBALL_PLAYER > bx && playerX < bx + HAIRBALL_BLOCK_W
                if (!overlapX) continue
                val overlapY = playerY + HAIRBALL_PLAYER > row.y && playerY < row.y + HAIRBALL_BLOCK_H
                if (!overlapY) continue
                val top = row.y - HAIRBALL_PLAYER
                if (top < surface) {
                    surface = top
                    landed = true
                }
            }
        }
        if (landed) {
            playerY = min(playerY, surface)
            events += HairballEvent.Landed
        }
        playerY = playerY.coerceAtMost(HAIRBALL_MAX_Y)

        val lost = playerY <= HAIRBALL_LOSS_Y
        if (lost) events += HairballEvent.Lost

        return state.copy(
            playerX = playerX,
            playerY = playerY,
            rows = rows,
            speed = speed,
            elapsedMs = elapsed,
            score = state.score + 1,
            seed = seed,
            nextRowLeftToRight = nextLeft,
            lost = lost,
            events = events,
        )
    }

    fun encode(state: HairballState): String {
        val rows = state.rows.joinToString("/") { row ->
            val dir = if (row.leftToRight) "L" else "R"
            dir + "@" + row.y + "@" + row.slots.joinToString(".")
        }
        return listOf(
            "hb1",
            state.playerX.toString(),
            state.playerY.toString(),
            state.speed.toString(),
            state.elapsedMs.toString(),
            state.score.toString(),
            state.seed.toString(),
            if (state.nextRowLeftToRight) "1" else "0",
            if (state.paused) "1" else "0",
            rows,
        ).joinToString(";")
    }

    fun decode(blob: String): HairballState? = try {
        val parts = blob.split(";")
        if (parts.size < 10 || parts[0] != "hb1") {
            null
        } else {
            val rows = if (parts[9].isEmpty()) {
                emptyList()
            } else {
                parts[9].split("/").map { row ->
                    val fields = row.split("@")
                    HairballRow(
                        y = fields[1].toFloat(),
                        slots = if (fields.getOrNull(2).isNullOrEmpty()) {
                            emptyList()
                        } else {
                            fields[2].split(".").map { it.toInt() }
                        },
                        leftToRight = fields[0] == "L",
                    )
                }
            }
            HairballState(
                playerX = parts[1].toFloat(),
                playerY = parts[2].toFloat(),
                rows = rows,
                speed = parts[3].toFloat(),
                elapsedMs = parts[4].toLong(),
                score = parts[5].toInt(),
                seed = parts[6].toInt(),
                nextRowLeftToRight = parts[7] == "1",
                paused = parts[8] == "1",
            )
        }
    } catch (_: Exception) {
        null
    }
}
