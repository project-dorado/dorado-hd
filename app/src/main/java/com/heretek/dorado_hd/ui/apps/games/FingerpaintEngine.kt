package com.heretek.dorado_hd.ui.apps.games

import kotlin.math.hypot
import kotlin.math.roundToInt

/* ============================================================ */
/*                       Fingerpaint engine                       */
/* ============================================================ */
/*
 * Clean-room implementation of the Quickdraw/Fingerpaint rules from
 * docs/apps/fingerpaint.md. No Microsoft code or assets. The original uses
 * XNA network sessions; we keep the round/scoring rules and drop the network:
 * play is single-device pass-and-play (drawer peeks the word, guessers type on
 * the same board) or a solo free-draw board. State transitions are pure so the
 * engine is unit-testable without Android.
 */

/** Round length (spec §3: RoundTimer = 90000 ms). */
const val FP_ROUND_MS = 90_000L

/** Default round structure (spec §3: RoundInfo.TotalRounds = 2). */
const val FP_TOTAL_ROUNDS = 2

/** Guesses are trimmed and capped (spec §3: "trimmed to 100 chars"). */
const val FP_GUESS_MAX = 100

/** Palette slots; the app maps indices onto DoradoAccent tokens. */
const val FP_PALETTE_SIZE = 8

/** Text-size clamp from the original canvas (spec §3: 2..240). */
const val FP_TEXT_SIZE_MIN = 2
const val FP_TEXT_SIZE_MAX = 240

/** Point sampling: ignore jitter, simplify on commit. */
const val FP_MIN_POINT_DISTANCE = 1.2f
const val FP_SIMPLIFY_EPSILON = 1.5f

/** Brush width ladder (design-space px) and the original alpha presets. */
val FP_BRUSH_WIDTHS: List<Float> = listOf(3f, 6f, 10f, 16f, 24f)
val FP_ALPHA_PRESETS: List<Int> = listOf(20, 40, 60, 80, 100)

enum class FingerpaintTool { BRUSH, ERASER }

/** Tool state: palette slot, brush ladder index, alpha index, text size. */
data class FingerpaintBrush(
    val colorIndex: Int = 0,
    val widthIndex: Int = 1,
    val alphaIndex: Int = FP_ALPHA_PRESETS.lastIndex,
    val tool: FingerpaintTool = FingerpaintTool.BRUSH,
    val textSize: Int = 24,
) {
    val width: Float get() = FP_BRUSH_WIDTHS[widthIndex.coerceIn(FP_BRUSH_WIDTHS.indices)]
    val alpha: Int get() = FP_ALPHA_PRESETS[alphaIndex.coerceIn(FP_ALPHA_PRESETS.indices)]

    fun withColor(index: Int): FingerpaintBrush = copy(colorIndex = index.coerceIn(0, FP_PALETTE_SIZE - 1))
    fun withWidth(index: Int): FingerpaintBrush = copy(widthIndex = index.coerceIn(FP_BRUSH_WIDTHS.indices))
    fun withAlpha(index: Int): FingerpaintBrush = copy(alphaIndex = index.coerceIn(FP_ALPHA_PRESETS.indices))
    fun withTool(next: FingerpaintTool): FingerpaintBrush = copy(tool = next)
    fun withTextSize(size: Int): FingerpaintBrush = copy(textSize = size.coerceIn(FP_TEXT_SIZE_MIN, FP_TEXT_SIZE_MAX))
}

/** One sampled point; [pressure] is derived from finger speed (fast = thin). */
data class FingerpaintPoint(val x: Float, val y: Float, val pressure: Float = 1f)

/** A freehand stroke (or eraser pass) in device pixel coordinates. */
data class FingerpaintStroke(
    val id: Int,
    val colorIndex: Int,
    val width: Float,
    val alpha: Int,
    val tool: FingerpaintTool = FingerpaintTool.BRUSH,
    val points: List<FingerpaintPoint> = emptyList(),
)

/**
 * Immutable drawing document. [undoTrail]/[redoTrail] hold full stroke-list
 * snapshots, so undo and redo cover add, erase and clear without special cases.
 */
data class FingerpaintDocument(
    val strokes: List<FingerpaintStroke> = emptyList(),
    val undoTrail: List<List<FingerpaintStroke>> = emptyList(),
    val redoTrail: List<List<FingerpaintStroke>> = emptyList(),
    val nextId: Int = 1,
)

data class FingerpaintWord(val text: String, val category: String)

/**
 * Re-authored word bank keeping the original five-category shape (spec §3/§5).
 * Every word here is ours; the reference list is never copied.
 */
object FingerpaintWordBank {
    val categories: List<String> = listOf("video games", "movies", "actions", "places", "objects")

    val words: Map<String, List<String>> = linkedMapOf(
        "video games" to listOf(
            "arcade cabinet", "controller", "joystick", "high score", "boss fight", "health bar",
            "game over screen", "loot chest", "mini map", "save point", "level select", "extra life",
        ),
        "movies" to listOf(
            "popcorn tub", "film reel", "movie ticket", "red carpet", "clapperboard", "director chair",
            "projector beam", "stunt double", "plot twist", "slow motion", "movie poster", "cinema screen",
        ),
        "actions" to listOf(
            "juggling", "sneezing", "skateboarding", "yawning", "applauding", "cartwheel",
            "fishing", "gardening", "hiccup", "moonwalk", "swimming", "whistling",
        ),
        "places" to listOf(
            "lighthouse", "desert island", "volcano", "submarine", "treehouse", "waterfall",
            "space station", "pyramid", "windmill", "harbor", "museum", "library",
        ),
        "objects" to listOf(
            "umbrella", "toaster", "snow globe", "kettle", "pillow", "lantern",
            "backpack", "wind chime", "alarm clock", "wheelbarrow", "telescope", "hourglass",
        ),
    )

    fun count(categoryIndex: Int): Int {
        val category = categories[categoryIndex.coerceIn(categories.indices)]
        return words.getValue(category).size
    }

    fun wordAt(categoryIndex: Int, wordIndex: Int): String {
        val category = categories[categoryIndex.coerceIn(categories.indices)]
        val list = words.getValue(category)
        return list[wordIndex.coerceIn(list.indices)]
    }

    /** Deterministic per-round pick so saved sessions replay identically. */
    fun pick(seed: Int, round: Int): FingerpaintWord {
        var state = seed * 1_664_525 + round * 1_013_904_223 + 0x2545F491
        fun next(bound: Int): Int {
            state = state * 1_103_515_245 + 12_345
            return ((state ushr 16) and 0x7FFF) % bound
        }
        val categoryIndex = next(categories.size)
        val category = categories[categoryIndex]
        val word = words.getValue(category)[next(words.getValue(category).size)]
        return FingerpaintWord(word, category)
    }
}

data class FingerpaintPlayer(val name: String, val score: Int = 0)

data class FingerpaintRound(
    val word: String,
    val category: String,
    val remainingMs: Long,
    val running: Boolean,
    val correct: Set<Int> = emptySet(),
    val message: String = "",
    val lastGuessCorrect: Boolean? = null,
) {
    val elapsedFraction: Double
        get() = 1.0 - (remainingMs.coerceIn(0L, FP_ROUND_MS).toDouble() / FP_ROUND_MS)
}

/** A completed drawing, replayed from the History screen. */
data class FingerpaintHistory(
    val round: Int,
    val drawer: String,
    val word: String,
    val category: String,
    val scorers: List<String>,
    val strokes: List<FingerpaintStroke>,
)

enum class FingerpaintMode { SOLO, PARTY }

enum class FingerpaintPhase { LOBBY, DRAW, ROUND_OVER, GAME_OVER }

data class FingerpaintSession(
    val players: List<FingerpaintPlayer> = emptyList(),
    val totalRounds: Int = FP_TOTAL_ROUNDS,
    val round: Int = 1,
    val drawer: Int = 0,
    val mode: FingerpaintMode = FingerpaintMode.PARTY,
    val phase: FingerpaintPhase = FingerpaintPhase.LOBBY,
    val roundState: FingerpaintRound? = null,
    val document: FingerpaintDocument = FingerpaintDocument(),
    val history: List<FingerpaintHistory> = emptyList(),
    val seed: Int = 0,
)

object FingerpaintEngine {

    /* ------------------------------ document ------------------------------ */

    fun document(): FingerpaintDocument = FingerpaintDocument()

    /** Speed-to-width: a fast finger draws a thinner line (spec §4). */
    fun pressureFor(distance: Float): Float = (1f - distance / 40f).coerceIn(0.35f, 1f)

    fun beginStroke(id: Int, brush: FingerpaintBrush, x: Float, y: Float): FingerpaintStroke =
        FingerpaintStroke(
            id = id,
            colorIndex = brush.colorIndex,
            width = brush.width,
            alpha = brush.alpha,
            tool = brush.tool,
            points = listOf(FingerpaintPoint(x, y, 1f)),
        )

    fun extendStroke(stroke: FingerpaintStroke, x: Float, y: Float): FingerpaintStroke {
        val last = stroke.points.lastOrNull() ?: return stroke
        val distance = hypot((x - last.x).toDouble(), (y - last.y).toDouble()).toFloat()
        if (distance < FP_MIN_POINT_DISTANCE) return stroke
        return stroke.copy(points = stroke.points + FingerpaintPoint(x, y, pressureFor(distance)))
    }

    /**
     * Commit a stroke. Eraser passes delete every stroke they touch and leave
     * no mark themselves; no-op gestures do not open an undo step.
     */
    fun addStroke(document: FingerpaintDocument, stroke: FingerpaintStroke): FingerpaintDocument {
        if (stroke.tool == FingerpaintTool.ERASER) {
            val remaining = eraseStrokes(document.strokes, stroke)
            if (remaining.size == document.strokes.size) return document
            return commit(document, remaining, bumpId = false)
        }
        val committed = stroke.copy(id = document.nextId, points = simplify(stroke.points, FP_SIMPLIFY_EPSILON))
        return commit(document, document.strokes + committed, bumpId = true)
    }

    fun eraseStrokes(strokes: List<FingerpaintStroke>, eraser: FingerpaintStroke): List<FingerpaintStroke> {
        val radius = eraser.width * 1.5f + 4f
        val radiusSq = radius * radius
        return strokes.filterNot { stroke ->
            stroke.points.any { p ->
                eraser.points.any { e ->
                    val dx = p.x - e.x
                    val dy = p.y - e.y
                    dx * dx + dy * dy <= radiusSq
                }
            }
        }
    }

    fun undo(document: FingerpaintDocument): FingerpaintDocument {
        if (document.undoTrail.isEmpty()) return document
        return document.copy(
            strokes = document.undoTrail.last(),
            undoTrail = document.undoTrail.dropLast(1),
            redoTrail = document.redoTrail + listOf(document.strokes),
        )
    }

    fun redo(document: FingerpaintDocument): FingerpaintDocument {
        if (document.redoTrail.isEmpty()) return document
        return document.copy(
            strokes = document.redoTrail.last(),
            redoTrail = document.redoTrail.dropLast(1),
            undoTrail = document.undoTrail + listOf(document.strokes),
        )
    }

    fun clear(document: FingerpaintDocument): FingerpaintDocument {
        if (document.strokes.isEmpty()) return document
        return document.copy(
            strokes = emptyList(),
            undoTrail = document.undoTrail + listOf(document.strokes),
            redoTrail = emptyList(),
        )
    }

    fun canUndo(document: FingerpaintDocument): Boolean = document.undoTrail.isNotEmpty()

    fun canRedo(document: FingerpaintDocument): Boolean = document.redoTrail.isNotEmpty()

    private fun commit(
        document: FingerpaintDocument,
        strokes: List<FingerpaintStroke>,
        bumpId: Boolean,
    ): FingerpaintDocument = document.copy(
        strokes = strokes,
        undoTrail = document.undoTrail + listOf(document.strokes),
        redoTrail = emptyList(),
        nextId = if (bumpId) document.nextId + 1 else document.nextId,
    )

    /** Ramer–Douglas–Peucker point reduction; keeps the first/last point. */
    fun simplify(points: List<FingerpaintPoint>, epsilon: Float): List<FingerpaintPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        reduce(points, 0, points.size - 1, epsilon, keep)
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun reduce(
        points: List<FingerpaintPoint>,
        start: Int,
        end: Int,
        epsilon: Float,
        keep: BooleanArray,
    ) {
        var maxDistance = 0f
        var index = -1
        for (i in start + 1 until end) {
            val distance = perpendicularDistance(points[i], points[start], points[end])
            if (distance > maxDistance) {
                maxDistance = distance
                index = i
            }
        }
        if (index > 0 && maxDistance > epsilon) {
            keep[index] = true
            reduce(points, start, index, epsilon, keep)
            reduce(points, index, end, epsilon, keep)
        }
    }

    private fun perpendicularDistance(
        point: FingerpaintPoint,
        lineStart: FingerpaintPoint,
        lineEnd: FingerpaintPoint,
    ): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        if (dx == 0f && dy == 0f) return hypot((point.x - lineStart.x).toDouble(), (point.y - lineStart.y).toDouble()).toFloat()
        val t = (((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val px = lineStart.x + t * dx
        val py = lineStart.y + t * dy
        return hypot((point.x - px).toDouble(), (point.y - py).toDouble()).toFloat()
    }

    /* ------------------------------- session ------------------------------ */

    /** Player names are stored delimiter-safe so sessions serialize cleanly. */
    fun sanitizeName(raw: String): String {
        val cleaned = raw.trim().take(24).map { if (it in ";,:=~/|#") ' ' else it }.joinToString("").trim()
        return cleaned.ifEmpty { "player" }
    }

    fun newSession(
        names: List<String>,
        seed: Int,
        mode: FingerpaintMode = FingerpaintMode.PARTY,
        totalRounds: Int = FP_TOTAL_ROUNDS,
    ): FingerpaintSession {
        val players = names.map { sanitizeName(it) }.ifEmpty { listOf("player") }
        return FingerpaintSession(
            players = players.map { FingerpaintPlayer(it) },
            totalRounds = totalRounds.coerceAtLeast(1),
            mode = mode,
            seed = seed,
        )
    }

    /** Solo board: no word, no timer, scores unused. */
    fun soloSession(seed: Int): FingerpaintSession =
        newSession(listOf("you"), seed, mode = FingerpaintMode.SOLO, totalRounds = 1)
            .copy(phase = FingerpaintPhase.DRAW)

    fun startRound(session: FingerpaintSession, seed: Int = session.seed): FingerpaintSession {
        if (session.players.isEmpty()) return session
        val drawer = session.drawer.coerceIn(0, session.players.size - 1)
        val word = FingerpaintWordBank.pick(seed, session.round)
        return session.copy(
            seed = seed,
            drawer = drawer,
            phase = FingerpaintPhase.DRAW,
            roundState = FingerpaintRound(
                word = word.text,
                category = word.category,
                remainingMs = FP_ROUND_MS,
                running = true,
                message = "${session.players[drawer].name} draws",
            ),
            document = FingerpaintDocument(),
        )
    }

    /**
     * Timer step. Drawing time is awarded faster: a correct guess scores
     * round((1 - elapsedFraction) * 100), i.e. ~100 at the first instant and
     * ~0 on the buzzer (spec §3; TimerControl.Value is the elapsed fraction).
     */
    fun scoreFor(remainingMs: Long): Int =
        (remainingMs.coerceIn(0L, FP_ROUND_MS).toDouble() / FP_ROUND_MS * 100.0).roundToInt()

    fun tick(session: FingerpaintSession, deltaMs: Long): FingerpaintSession {
        val round = session.roundState ?: return session
        if (session.phase != FingerpaintPhase.DRAW || !round.running || deltaMs <= 0L) return session
        val remaining = (round.remainingMs - deltaMs).coerceAtLeast(0L)
        val next = session.copy(roundState = round.copy(remainingMs = remaining))
        return if (remaining == 0L) finishRound(next, "time up!") else next
    }

    fun normalizeGuess(raw: String): String = raw.trim().take(FP_GUESS_MAX).lowercase()

    /** The player whose turn it is to guess (first non-drawer still un-scored). */
    fun currentGuesser(session: FingerpaintSession): Int? {
        val round = session.roundState ?: return null
        return session.players.indices.firstOrNull { it != session.drawer && it !in round.correct }
    }

    fun guess(session: FingerpaintSession, playerIndex: Int, raw: String): FingerpaintSession {
        val round = session.roundState ?: return session
        if (session.phase != FingerpaintPhase.DRAW || !round.running) return session
        if (playerIndex !in session.players.indices || playerIndex == session.drawer) return session
        if (playerIndex in round.correct) return session
        val guess = normalizeGuess(raw)
        if (guess.isEmpty()) return session

        if (guess == normalizeGuess(round.word)) {
            val points = scoreFor(round.remainingMs)
            val players = session.players.mapIndexed { index, player ->
                if (index == playerIndex) player.copy(score = player.score + points) else player
            }
            val correct = round.correct + playerIndex
            val everyone = correct.size == session.players.size - 1
            val next = session.copy(
                players = players,
                roundState = round.copy(
                    correct = correct,
                    message = "correct! +$points",
                    lastGuessCorrect = true,
                ),
            )
            return if (everyone) finishRound(next, "everyone got it!") else next
        }

        return session.copy(
            roundState = round.copy(
                message = "not it — try again",
                lastGuessCorrect = false,
            ),
        )
    }

    private fun finishRound(session: FingerpaintSession, message: String): FingerpaintSession {
        val round = session.roundState ?: return session
        val history = FingerpaintHistory(
            round = session.round,
            drawer = session.players.getOrNull(session.drawer)?.name ?: "",
            word = round.word,
            category = round.category,
            scorers = session.players.indices.filter { it in round.correct }.map { session.players[it].name },
            strokes = session.document.strokes,
        )
        return session.copy(
            phase = FingerpaintPhase.ROUND_OVER,
            roundState = round.copy(running = false, message = message),
            history = session.history + history,
        )
    }

    /** Round-over -> next round (next drawer) or the scoreboard. */
    fun advance(session: FingerpaintSession): FingerpaintSession {
        if (session.phase != FingerpaintPhase.ROUND_OVER) return session
        if (session.round >= session.totalRounds) {
            return session.copy(phase = FingerpaintPhase.GAME_OVER)
        }
        val nextRound = session.round + 1
        val drawer = (nextRound - 1) % session.players.size
        return startRound(session.copy(round = nextRound, drawer = drawer), session.seed)
    }

    fun winnerNames(session: FingerpaintSession): List<String> {
        val best = session.players.maxOfOrNull { it.score } ?: return emptyList()
        return session.players.filter { it.score == best }.map { it.name }
    }

    /* --------------------------- serialization ---------------------------- */
    /* Compact delimiter-safe format, versioned "fp1". Strokes use ':' fields,
     * '|' points and '/' lists; sessions use ';' fields and '~' groups. */

    fun encode(session: FingerpaintSession): String = listOf(
        "fp1",
        session.players.joinToString(",") { "${it.name}=${it.score}" },
        session.totalRounds,
        session.round,
        session.drawer,
        session.mode.ordinal,
        session.phase.ordinal,
        session.seed,
        session.roundState?.word ?: "",
        session.roundState?.category ?: "",
        session.roundState?.remainingMs ?: 0L,
        if (session.roundState?.running == true) 1 else 0,
        session.roundState?.correct?.joinToString(",") ?: "",
        session.roundState?.message ?: "",
        session.roundState?.lastGuessCorrect?.let { if (it) 1 else 0 } ?: -1,
        encodeDocument(session.document),
        session.history.joinToString("~") { encodeHistory(it) },
    ).joinToString(";")

    fun decode(blob: String?): FingerpaintSession? {
        if (blob == null) return null
        return try {
            val parts = blob.split(";")
            if (parts.size < 17 || parts[0] != "fp1") return null
            val players = parts[1].split(",").filter { it.isNotEmpty() }.map { raw ->
                val fields = raw.split("=")
                FingerpaintPlayer(fields[0], fields[1].toInt())
            }
            if (players.isEmpty()) return null
            val correct = parts[12].split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toSet()
            val roundState = if (parts[8].isEmpty()) {
                null
            } else {
                FingerpaintRound(
                    word = parts[8],
                    category = parts[9],
                    remainingMs = parts[10].toLong(),
                    running = parts[11] == "1",
                    correct = correct,
                    message = parts[13],
                    lastGuessCorrect = when (parts[14].toInt()) {
                        1 -> true
                        0 -> false
                        else -> null
                    },
                )
            }
            FingerpaintSession(
                players = players,
                totalRounds = parts[2].toInt(),
                round = parts[3].toInt(),
                drawer = parts[4].toInt(),
                mode = FingerpaintMode.entries[parts[5].toInt()],
                phase = FingerpaintPhase.entries[parts[6].toInt()],
                seed = parts[7].toInt(),
                roundState = roundState,
                document = decodeDocument(parts[15]),
                history = if (parts[16].isEmpty()) emptyList() else parts[16].split("~").map { decodeHistory(it) },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeDocument(document: FingerpaintDocument): String = listOf(
        encodeStrokes(document.strokes),
        encodeGroups(document.undoTrail),
        encodeGroups(document.redoTrail),
        document.nextId,
    ).joinToString("#")

    private fun decodeDocument(raw: String): FingerpaintDocument {
        val parts = raw.split("#")
        return FingerpaintDocument(
            strokes = decodeStrokes(parts.getOrElse(0) { "" }),
            undoTrail = decodeGroups(parts.getOrElse(1) { "" }),
            redoTrail = decodeGroups(parts.getOrElse(2) { "" }),
            nextId = parts.getOrElse(3) { "1" }.toInt(),
        )
    }

    /** A "+" marks each snapshot so an empty snapshot stays distinguishable. */
    private fun encodeGroups(groups: List<List<FingerpaintStroke>>): String =
        groups.joinToString("~") { "+" + encodeStrokes(it) }

    private fun decodeGroups(raw: String): List<List<FingerpaintStroke>> =
        if (raw.isEmpty()) emptyList() else raw.split("~").map { decodeStrokes(it.removePrefix("+")) }

    private fun encodeHistory(history: FingerpaintHistory): String = listOf(
        history.round,
        history.drawer,
        history.word,
        history.category,
        history.scorers.joinToString("|"),
        encodeStrokes(history.strokes),
    ).joinToString(",")

    private fun decodeHistory(raw: String): FingerpaintHistory {
        val fields = raw.split(",")
        return FingerpaintHistory(
            round = fields[0].toInt(),
            drawer = fields[1],
            word = fields[2],
            category = fields[3],
            scorers = fields[4].split("|").filter { it.isNotEmpty() },
            strokes = decodeStrokes(fields.getOrElse(5) { "" }),
        )
    }

    private fun encodeStrokes(strokes: List<FingerpaintStroke>): String =
        strokes.joinToString("/") { encodeStroke(it) }

    private fun encodeStroke(stroke: FingerpaintStroke): String = listOf(
        stroke.id,
        stroke.colorIndex,
        stroke.width,
        stroke.alpha,
        stroke.tool.ordinal,
        stroke.points.joinToString("|") { "${it.x}:${it.y}:${it.pressure}" },
    ).joinToString(":")

    private fun decodeStrokes(raw: String): List<FingerpaintStroke> =
        if (raw.isEmpty()) emptyList() else raw.split("/").map { decodeStroke(it) }

    private fun decodeStroke(raw: String): FingerpaintStroke {
        val fields = raw.split(":")
        val points = fields.drop(5).joinToString(":").split("|").filter { it.isNotEmpty() }.map { point ->
            val p = point.split(":")
            FingerpaintPoint(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
        }
        return FingerpaintStroke(
            id = fields[0].toInt(),
            colorIndex = fields[1].toInt(),
            width = fields[2].toFloat(),
            alpha = fields[3].toInt(),
            tool = FingerpaintTool.entries[fields[4].toInt()],
            points = points,
        )
    }
}
