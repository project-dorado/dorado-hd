package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Splatter Bug — clean-room engine for the Zune HD squish-the-bugs game.
 *
 * Behaviour re-derived from docs/apps/splatter-bug.md; no Microsoft code or
 * assets. Bugs spawn every 750 ms, crawl from one edge to the other, and the
 * player taps to squish pests while freeing butterflies and ladybirds.
 * Coordinates are the device design space (272x480, y grows downward).
 */
const val SPLATTER_VIEW_W = 272f
const val SPLATTER_VIEW_H = 480f
const val SPLATTER_FRAME_MS = 1000f / 30f
const val SPLATTER_SPAWN_MS = 750L
const val SPLATTER_START_LIVES = 3
const val SPLATTER_FADE_MS = 5_000L
const val SPLATTER_TOAST_MS = 1_000L
const val SPLATTER_LABEL_MS = 1_000L
const val SPLATTER_TARANTULA_BURST = 10

enum class BugKind(
    val size: Float,
    val hp: Int,
    val speed: Float,
    val harmless: Boolean,
    val tapsToKill: Int = 1,
) {
    ANT(60f, 10, 6f, false),
    BUTTERFLY(70f, 30, 8f, true),
    COCKROACH(70f, 10, 7f, false),
    EARWIG(60f, 10, 10f, false),
    LADYBIRD(60f, 30, 5f, true),
    SPIDER(36f, 20, 4f, false),
    TARANTULA(162f, 50, 6f, false, tapsToKill = 3),
}

class SplatterRandom(seed: Int) {
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

data class BugTap(val x: Float, val y: Float)

data class SplatterBug(
    val id: Int,
    val kind: BugKind,
    val x: Float,
    val y: Float,
    val rotated: Boolean,
    val tapsLeft: Int = kind.tapsToKill,
    val splatted: Boolean = false,
    val ageMs: Long = 0L,
    val label: String = "",
) {
    val size: Float get() = kind.size

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= x + size && py >= y && py <= y + size
}

sealed interface SplatterBugEvent {
    data class Spawned(val kind: BugKind) : SplatterBugEvent
    data class Squish(val kind: BugKind, val points: Int, val harmless: Boolean) : SplatterBugEvent
    data class Escaped(val kind: BugKind, val points: Int, val harmless: Boolean) : SplatterBugEvent
    data object TarantulaBurst : SplatterBugEvent
    data object GameOver : SplatterBugEvent
}

data class SplatterBugState(
    val score: Int,
    val lives: Int,
    val bugs: List<SplatterBug>,
    val spawnClockMs: Long,
    val elapsedMs: Long,
    val nextId: Int,
    val seed: Int,
    val toastMs: Long = 0L,
    val over: Boolean = false,
    val paused: Boolean = false,
    val events: List<SplatterBugEvent> = emptyList(),
)

object SplatterBugEngine {

    /** Spawn weight bands out of 100. */
    fun rollKind(roll: Int): BugKind = when {
        roll < 20 -> BugKind.ANT
        roll < 35 -> BugKind.BUTTERFLY
        roll < 50 -> BugKind.COCKROACH
        roll < 65 -> BugKind.EARWIG
        roll < 80 -> BugKind.LADYBIRD
        roll < 95 -> BugKind.SPIDER
        else -> BugKind.TARANTULA
    }

    fun newGame(seed: Int): SplatterBugState = SplatterBugState(
        score = 0,
        lives = SPLATTER_START_LIVES,
        bugs = emptyList(),
        spawnClockMs = 0L,
        elapsedMs = 0L,
        nextId = 1,
        seed = seed,
    )

    fun pastEdge(bug: SplatterBug): Boolean =
        if (bug.rotated) bug.y > SPLATTER_VIEW_H else bug.y + bug.kind.size < 0f

    fun step(state: SplatterBugState, dtMs: Long, taps: List<BugTap> = emptyList()): SplatterBugState {
        if (state.paused || state.over || dtMs <= 0L) return state.copy(events = emptyList())
        val events = mutableListOf<SplatterBugEvent>()
        val frames = dtMs / SPLATTER_FRAME_MS
        var bugs = state.bugs.toMutableList()
        var score = state.score
        var lives = state.lives
        var toast = (state.toastMs - dtMs).coerceAtLeast(0L)
        var over = state.over
        var nextId = state.nextId
        val rng = SplatterRandom(state.seed)

        // Input: one bug per tap, topmost (most recently spawned) first.
        for (tap in taps) {
            val index = bugs.indexOfLast { !it.splatted && it.contains(tap.x, tap.y) }
            if (index < 0) continue
            val bug = bugs[index]
            when {
                bug.kind.harmless -> {
                    bugs[index] = bug.copy(splatted = true, ageMs = 0L, label = "-life")
                    lives--
                    events += SplatterBugEvent.Squish(bug.kind, 0, harmless = true)
                    if (lives < 0) {
                        over = true
                        events += SplatterBugEvent.GameOver
                        break
                    }
                }
                bug.tapsLeft > 1 -> {
                    bugs[index] = bug.copy(tapsLeft = bug.tapsLeft - 1)
                    events += SplatterBugEvent.Squish(bug.kind, 0, harmless = false)
                }
                else -> {
                    bugs[index] = bug.copy(splatted = true, ageMs = 0L, label = "+${bug.kind.hp}")
                    score += bug.kind.hp
                    events += SplatterBugEvent.Squish(bug.kind, bug.kind.hp, harmless = false)
                    if (bug.kind == BugKind.TARANTULA) {
                        events += SplatterBugEvent.TarantulaBurst
                        val cx = bug.x + bug.size / 2f
                        val cy = bug.y + bug.size / 2f
                        for (i in 0 until SPLATTER_TARANTULA_BURST) {
                            val angle = i * (2.0 * PI / SPLATTER_TARANTULA_BURST)
                            val dist = 16f + i * 6f
                            val sx = (cx + cos(angle).toFloat() * dist)
                                .coerceIn(0f, SPLATTER_VIEW_W - BugKind.SPIDER.size)
                            val sy = (cy + sin(angle).toFloat() * dist)
                                .coerceIn(0f, SPLATTER_VIEW_H - BugKind.SPIDER.size)
                            bugs += SplatterBug(
                                id = nextId++,
                                kind = BugKind.SPIDER,
                                x = sx,
                                y = sy,
                                rotated = bug.rotated,
                            )
                        }
                    }
                }
            }
        }

        if (over) {
            return state.copy(
                score = score,
                lives = lives,
                bugs = bugs,
                nextId = nextId,
                seed = rng.state,
                toastMs = toast,
                over = true,
                events = events,
            )
        }

        // Movement + splat fade (removed after 5000 ms).
        bugs = bugs.mapNotNull { bug ->
            if (bug.splatted) {
                val age = bug.ageMs + dtMs
                if (age >= SPLATTER_FADE_MS) null else bug.copy(ageMs = age)
            } else {
                bug.copy(
                    y = bug.y + if (bug.rotated) bug.kind.speed * frames else -bug.kind.speed * frames,
                )
            }
        }.toMutableList()

        // Edge exits: pests cost a life, harmless critters award their hit points.
        val escaped = bugs.filter { !it.splatted && pastEdge(it) }.sortedBy { it.id }
        for (bug in escaped) {
            bugs.remove(bug)
            if (bug.kind.harmless) {
                score += bug.kind.hp
                events += SplatterBugEvent.Escaped(bug.kind, bug.kind.hp, harmless = true)
            } else {
                lives--
                toast = SPLATTER_TOAST_MS
                events += SplatterBugEvent.Escaped(bug.kind, 0, harmless = false)
                if (lives < 0) {
                    over = true
                    events += SplatterBugEvent.GameOver
                    break
                }
            }
        }

        if (over) {
            return state.copy(
                score = score,
                lives = lives,
                bugs = bugs,
                nextId = nextId,
                seed = rng.state,
                toastMs = toast,
                over = true,
                events = events,
            )
        }

        // Spawn: one bug every 750 ms; x in 40..231, direction 0 or pi.
        var clock = state.spawnClockMs + dtMs
        while (clock >= SPLATTER_SPAWN_MS) {
            clock -= SPLATTER_SPAWN_MS
            val x = (rng.next(192) + 40).toFloat()
            val rotated = rng.next(2) == 1
            val kind = rollKind(rng.next(100))
            val y = if (rotated) 0f else SPLATTER_VIEW_H
            bugs += SplatterBug(id = nextId++, kind = kind, x = x, y = y, rotated = rotated)
            events += SplatterBugEvent.Spawned(kind)
        }

        return state.copy(
            score = score,
            lives = lives,
            bugs = bugs,
            spawnClockMs = clock,
            elapsedMs = state.elapsedMs + dtMs,
            nextId = nextId,
            seed = rng.state,
            toastMs = toast,
            over = over,
            events = events,
        )
    }

    fun encode(state: SplatterBugState): String {
        val bugs = state.bugs.joinToString("/") { bug ->
            listOf(
                bug.kind.ordinal,
                bug.id,
                bug.x,
                bug.y,
                if (bug.rotated) 1 else 0,
                bug.tapsLeft,
                if (bug.splatted) 1 else 0,
                bug.ageMs,
                bug.label.replace(";", "").replace("/", "").replace("|", "").replace(",", ""),
            ).joinToString(",")
        }
        return listOf(
            "sb1",
            state.score,
            state.lives,
            state.spawnClockMs,
            state.elapsedMs,
            state.nextId,
            state.seed,
            state.toastMs,
            if (state.over) 1 else 0,
            if (state.paused) 1 else 0,
            bugs,
        ).joinToString(";")
    }

    fun decode(blob: String): SplatterBugState? = try {
        val parts = blob.split(";")
        if (parts.size < 11 || parts[0] != "sb1") {
            null
        } else {
            val bugs = if (parts[10].isEmpty()) {
                emptyList()
            } else {
                parts[10].split("/").map { raw ->
                    val f = raw.split(",")
                    SplatterBug(
                        id = f[1].toInt(),
                        kind = BugKind.entries[f[0].toInt()],
                        x = f[2].toFloat(),
                        y = f[3].toFloat(),
                        rotated = f[4] == "1",
                        tapsLeft = f[5].toInt(),
                        splatted = f[6] == "1",
                        ageMs = f[7].toLong(),
                        label = f.getOrNull(8).orEmpty(),
                    )
                }
            }
            SplatterBugState(
                score = parts[1].toInt(),
                lives = parts[2].toInt(),
                bugs = bugs,
                spawnClockMs = parts[3].toLong(),
                elapsedMs = parts[4].toLong(),
                nextId = parts[5].toInt(),
                seed = parts[6].toInt(),
                toastMs = parts[7].toLong(),
                over = parts[8] == "1",
                paused = parts[9] == "1",
            )
        }
    } catch (_: Exception) {
        null
    }
}
