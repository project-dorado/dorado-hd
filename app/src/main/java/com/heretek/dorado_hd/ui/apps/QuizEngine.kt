package com.heretek.dorado_hd.ui.apps

import com.heretek.dorado_hd.data.model.Track
import kotlin.math.floor
import kotlin.random.Random

/**
 * Pure round machine for the Music Quiz mini-app.
 *
 * A round is [TOTAL_QUESTIONS] metadata questions over the user's library:
 * name the song, name the artist, name the album, spot the odd one out, or
 * identify album art. Every question offers [Difficulty.optionCount] distinct
 * answers, carries the tracks it referenced so the pool never repeats within
 * a round, and is generated from a seed so a round is fully deterministic.
 *
 * Nothing Android is touched here; the UI owns timers, playback and storage.
 */
object QuizEngine {
    const val TOTAL_QUESTIONS = 20
    const val NORMAL_OPTIONS = 4
    const val HARDCORE_OPTIONS = 6
    const val QUESTION_SECONDS = 15f
    const val BONUS_SECONDS = 30f
    const val BONUS_POINTS_PER_CORRECT = 5
    const val MAX_POINTS = 10
    const val HINT_LIMIT_NORMAL = 4
    const val HINT_LIMIT_HARDCORE = 2
    const val EXPIRED_CONTENT_ABORT = 25

    enum class Kind { SONG_TITLE, BY_ARTIST, BY_ALBUM, ODD_ONE_OUT, ALBUM_ART }

    enum class Difficulty {
        NORMAL,
        HARDCORE;

        val optionCount: Int get() = if (this == HARDCORE) HARDCORE_OPTIONS else NORMAL_OPTIONS
        val hintLimit: Int get() = if (this == HARDCORE) HINT_LIMIT_HARDCORE else HINT_LIMIT_NORMAL
    }

    enum class Phase { QUESTION, REVEAL, BONUS, RESULTS }

    data class Question(
        val kind: Kind,
        val prompt: String,
        val answer: String,
        val options: List<String>,
        val answerIndex: Int,
        /** Track the clip/hint/art question is about; null for odd-one-out. */
        val mediaId: Long? = null,
        val albumId: Long? = null,
        /** Every track referenced (answer + distractors) so pools can advance. */
        val referencedSongs: Set<Long> = emptySet(),
        /** Every album referenced so album questions do not repeat. */
        val referencedAlbums: Set<Long> = emptySet(),
    )

    data class QuizState(
        val difficulty: Difficulty,
        val questionNumber: Int,
        val totalQuestions: Int = TOTAL_QUESTIONS,
        val question: Question?,
        val phase: Phase = Phase.QUESTION,
        val secondsLeft: Float = QUESTION_SECONDS,
        val score: Int = 0,
        val correct: Int = 0,
        val points: Int = 0,
        val hintsLeft: Int,
        val hintsUsed: Int = 0,
        val chosenIndex: Int? = null,
        val hintActive: Boolean = false,
        val hintSeekMs: Long = 0L,
        val bonusEnabled: Boolean = true,
        val bonusQuestion: Question? = null,
        val bonusCorrect: Int = 0,
        val bonusSecondsLeft: Float = BONUS_SECONDS,
        val seed: Long = 0L,
        val mixName: String = "megamix",
        val usedSongs: Set<Long> = emptySet(),
        val usedAlbums: Set<Long> = emptySet(),
        val previousWasAlbumArt: Boolean = false,
        val expired: Int = 0,
    ) {
        val optionCount: Int get() = difficulty.optionCount
        val bonusPoints: Int get() = bonusCorrect * BONUS_POINTS_PER_CORRECT
        val isLastQuestion: Boolean get() = questionNumber >= totalQuestions
    }

    fun isContentValid(track: Track): Boolean =
        track.title.isNotBlank() && track.artist.isNotBlank() && track.album.isNotBlank()

    /**
     * Legacy single-question API kept for LogicTest and any one-shot callers.
     * Round play should use [startRound]/[proceed] so pools and difficulty
     * apply.
     */
    fun buildQuestion(tracks: List<Track>, rng: Random = Random.Default): Question? =
        nextQuestion(tracks, Difficulty.NORMAL, rng = rng)

    /**
     * Deterministically generate the next question from [tracks], avoiding
     * [usedSongs]/[usedAlbums] where possible and never chaining two album-art
     * questions.
     */
    fun nextQuestion(
        tracks: List<Track>,
        difficulty: Difficulty = Difficulty.NORMAL,
        usedSongs: Set<Long> = emptySet(),
        usedAlbums: Set<Long> = emptySet(),
        previousWasAlbumArt: Boolean = false,
        rng: Random = Random.Default,
    ): Question? {
        val n = difficulty.optionCount
        val usable = tracks.filter { isContentValid(it) }
        if (usable.size < n) return null
        val byArtist = usable.groupBy { it.artist }
        val byAlbum = usable.groupBy { it.album }

        val kinds = mutableListOf<Kind>()
        if (byArtist.size >= n) {
            kinds += Kind.BY_ARTIST
            kinds += Kind.ODD_ONE_OUT
        }
        if (byAlbum.size >= n) {
            kinds += Kind.BY_ALBUM
            kinds += Kind.SONG_TITLE
            if (!previousWasAlbumArt) kinds += Kind.ALBUM_ART
        }
        if (kinds.isEmpty()) return null
        for (kind in kinds.shuffled(rng)) {
            val q = when (kind) {
                Kind.BY_ARTIST -> artistQuestion(byArtist, usedSongs, n, rng)
                Kind.BY_ALBUM -> albumQuestion(byAlbum, usedSongs, usedAlbums, n, rng, albumArt = false)
                Kind.ALBUM_ART -> albumQuestion(byAlbum, usedSongs, usedAlbums, n, rng, albumArt = true)
                Kind.SONG_TITLE -> songTitleQuestion(byAlbum, usedSongs, n, rng)
                Kind.ODD_ONE_OUT -> oddOneOutQuestion(byArtist, usedSongs, n, rng)
            }
            if (q != null) return q
        }
        return null
    }

    /**
     * Start a full round, or null when the library cannot support one
     * (too few usable tracks, or too much unreadable content).
     */
    fun startRound(
        tracks: List<Track>,
        difficulty: Difficulty = Difficulty.NORMAL,
        seed: Long = 0L,
        bonusEnabled: Boolean = true,
        mixName: String = "megamix",
    ): QuizState? {
        val expired = tracks.count { !isContentValid(it) }
        if (expired >= EXPIRED_CONTENT_ABORT) return null
        val usable = tracks.filter { isContentValid(it) }
        if (usable.size < difficulty.optionCount) return null
        val rng = Random(seed)
        val q = nextQuestion(usable, difficulty, rng = rng) ?: return null
        return QuizState(
            difficulty = difficulty,
            questionNumber = 1,
            question = q,
            secondsLeft = QUESTION_SECONDS,
            hintsLeft = difficulty.hintLimit,
            seed = seed,
            bonusEnabled = bonusEnabled,
            mixName = mixName,
            usedSongs = q.referencedSongs,
            usedAlbums = q.referencedAlbums,
            previousWasAlbumArt = q.kind == Kind.ALBUM_ART,
            expired = expired,
        )
    }

    /** Device points rule: min(10, floor(seconds remaining) + 1). */
    fun pointsFor(secondsLeft: Float): Int =
        (floor(secondsLeft.coerceAtLeast(0f)).toInt() + 1).coerceAtMost(MAX_POINTS)

    fun tick(state: QuizState, deltaSeconds: Float): QuizState {
        if (deltaSeconds <= 0f) return state
        return when (state.phase) {
            Phase.QUESTION -> {
                val left = (state.secondsLeft - deltaSeconds).coerceAtLeast(0f)
                if (left <= 0f) {
                    state.copy(secondsLeft = 0f, phase = Phase.REVEAL, chosenIndex = null, points = 0)
                } else {
                    state.copy(secondsLeft = left)
                }
            }
            Phase.BONUS -> {
                val left = (state.bonusSecondsLeft - deltaSeconds).coerceAtLeast(0f)
                if (left <= 0f) endBonus(state) else state.copy(bonusSecondsLeft = left)
            }
            else -> state
        }
    }

    fun answer(state: QuizState, optionIndex: Int): QuizState {
        if (state.phase != Phase.QUESTION) return state
        val q = state.question ?: return state
        if (optionIndex !in q.options.indices) return state
        val isCorrect = optionIndex == q.answerIndex
        val points = if (isCorrect) pointsFor(state.secondsLeft) else 0
        return state.copy(
            phase = Phase.REVEAL,
            chosenIndex = optionIndex,
            points = points,
            score = state.score + points,
            correct = state.correct + if (isCorrect) 1 else 0,
        )
    }

    fun useHint(state: QuizState, tracks: List<Track>): QuizState {
        if (state.phase != Phase.QUESTION || state.hintsLeft <= 0) return state
        val duration = tracks.firstOrNull { it.mediaId == state.question?.mediaId }?.durationMs ?: 0L
        return state.copy(
            hintsLeft = state.hintsLeft - 1,
            hintsUsed = state.hintsUsed + 1,
            hintActive = true,
            secondsLeft = QUESTION_SECONDS,
            hintSeekMs = hintSeekFor(duration, state.seed + state.hintsUsed * 7919L),
        )
    }

    /** Device hint: very short songs restart from the top, longer ones seek. */
    fun hintSeekFor(durationMs: Long, seed: Long): Long {
        if (durationMs <= 10_000L) return 0L
        val window = (durationMs / 2).coerceAtLeast(1L)
        return Random(seed).nextInt(window.toInt().coerceAtLeast(1)).toLong()
    }

    /**
     * Leave the reveal state: next question, bonus round after question 20, or
     * results.
     */
    fun proceed(state: QuizState, tracks: List<Track>): QuizState {
        if (state.phase != Phase.REVEAL) return state
        if (state.isLastQuestion) {
            if (!state.bonusEnabled) return state.copy(phase = Phase.RESULTS, chosenIndex = null)
            val bonus = albumArtQuestion(
                tracks.filter { isContentValid(it) }.groupBy { it.album },
                emptySet(),
                emptySet(),
                state.difficulty.optionCount,
                Random(state.seed + 977L),
            )
            return state.copy(
                phase = Phase.BONUS,
                question = bonus ?: state.question,
                chosenIndex = null,
                points = 0,
                bonusQuestion = bonus,
                bonusCorrect = 0,
                bonusSecondsLeft = BONUS_SECONDS,
            )
        }
        val rng = Random(state.seed + state.questionNumber * 31L)
        val next = nextQuestion(
            tracks,
            state.difficulty,
            state.usedSongs,
            state.usedAlbums,
            state.previousWasAlbumArt,
            rng,
        )
        return state.copy(
            questionNumber = state.questionNumber + 1,
            question = next ?: state.question,
            phase = Phase.QUESTION,
            secondsLeft = QUESTION_SECONDS,
            chosenIndex = null,
            points = 0,
            hintActive = false,
            hintSeekMs = 0L,
            usedSongs = next?.referencedSongs ?: state.usedSongs,
            usedAlbums = next?.referencedAlbums ?: state.usedAlbums,
            previousWasAlbumArt = next?.kind == Kind.ALBUM_ART,
        )
    }

    /** Bonus round taps: correct answers stack, then pay out at the end. */
    fun answerBonus(state: QuizState, tracks: List<Track>, optionIndex: Int): QuizState {
        if (state.phase != Phase.BONUS) return state
        val q = state.bonusQuestion ?: return state
        if (optionIndex !in q.options.indices) return state
        val isCorrect = optionIndex == q.answerIndex
        val next = albumArtQuestion(
            tracks.filter { isContentValid(it) }.groupBy { it.album },
            emptySet(),
            emptySet(),
            state.difficulty.optionCount,
            Random(state.seed + state.bonusCorrect * 131L + 17L),
        )
        return state.copy(
            bonusCorrect = state.bonusCorrect + if (isCorrect) 1 else 0,
            bonusQuestion = next ?: q,
        )
    }

    /** Close the bonus round and bank NumberCorrect x 5. */
    fun endBonus(state: QuizState): QuizState =
        state.copy(
            phase = Phase.RESULTS,
            score = state.score + state.bonusPoints,
            bonusSecondsLeft = 0f,
        )

    fun highScoreKey(mixName: String, difficulty: Difficulty): String =
        "${mixName.trim().ifBlank { "megamix" }.lowercase()}/${difficulty.name.lowercase()}"

    fun updateHighScore(scores: Map<String, Int>, key: String, score: Int): Map<String, Int> {
        val best = maxOf(scores[key] ?: 0, score)
        return scores + (key to best)
    }

    fun encodeHighScores(scores: Map<String, Int>): String =
        scores.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    fun decodeHighScores(encoded: String?): Map<String, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(';').mapNotNull { entry ->
            val sep = entry.lastIndexOf('=')
            if (sep <= 0) null else {
                val key = entry.substring(0, sep)
                val value = entry.substring(sep + 1).toIntOrNull()
                if (value == null) null else key to value
            }
        }.toMap()
    }

    /* ------------------------------ builders ------------------------------ */

    private fun artistQuestion(
        byArtist: Map<String, List<Track>>,
        usedSongs: Set<Long>,
        n: Int,
        rng: Random,
    ): Question? {
        val artists = byArtist.keys.filter { it.isNotBlank() }.shuffled(rng)
        val preferred = artists.filter { a -> byArtist[a].orEmpty().any { it.mediaId !in usedSongs } } +
            artists.filter { a -> byArtist[a].orEmpty().none { it.mediaId !in usedSongs } }
        for (target in preferred) {
            val targetTracks = byArtist[target].orEmpty()
            if (targetTracks.isEmpty()) continue
            val answer = targetTracks.filter { it.mediaId !in usedSongs }.ifEmpty { targetTracks }.random(rng)
            val referencedSongs = mutableSetOf(answer.mediaId)
            val referencedAlbums = mutableSetOf(answer.albumId)
            val distractors = mutableListOf<String>()
            for (artist in artists) {
                if (artist == target) continue
                val track = byArtist[artist].orEmpty().randomOrNull(rng) ?: continue
                if (track.title.isBlank() || track.title == answer.title) continue
                if (distractors.contains(track.title)) continue
                distractors += track.title
                referencedSongs += track.mediaId
                referencedAlbums += track.albumId
                if (distractors.size == n - 1) break
            }
            if (distractors.size < n - 1) continue
            val options = (distractors + answer.title).shuffled(rng)
            return Question(
                kind = Kind.BY_ARTIST,
                prompt = "which song is by \"$target\"?",
                answer = answer.title,
                options = options,
                answerIndex = options.indexOf(answer.title),
                mediaId = answer.mediaId,
                albumId = answer.albumId,
                referencedSongs = referencedSongs,
                referencedAlbums = referencedAlbums,
            )
        }
        return null
    }

    private fun albumQuestion(
        byAlbum: Map<String, List<Track>>,
        usedSongs: Set<Long>,
        usedAlbums: Set<Long>,
        n: Int,
        rng: Random,
        albumArt: Boolean,
    ): Question? {
        if (byAlbum.size < n) return null
        val albums = byAlbum.keys.filter { it.isNotBlank() }.shuffled(rng)
        val fresh = albums.filter { name -> byAlbum[name].orEmpty().none { it.albumId in usedAlbums } }
        val stale = albums.filter { name -> byAlbum[name].orEmpty().any { it.albumId in usedAlbums } }
        val preferred = fresh + stale
        for (target in preferred) {
            val targetTracks = byAlbum[target].orEmpty()
            if (targetTracks.isEmpty()) continue
            val answer = targetTracks.filter { it.mediaId !in usedSongs }.ifEmpty { targetTracks }.random(rng)
            val distractors = albums.filter { it != target }.take(n - 1)
            if (distractors.size < n - 1) continue
            val options = (distractors + target).shuffled(rng)
            val referencedSongs = mutableSetOf(answer.mediaId)
            val referencedAlbums = mutableSetOf(answer.albumId)
            for (name in distractors) {
                byAlbum[name].orEmpty().randomOrNull(rng)?.let {
                    referencedSongs += it.mediaId
                    referencedAlbums += it.albumId
                }
            }
            return Question(
                kind = if (albumArt) Kind.ALBUM_ART else Kind.BY_ALBUM,
                prompt = if (albumArt) "which album is this?" else "which album is \"${answer.title}\" on?",
                answer = target,
                options = options,
                answerIndex = options.indexOf(target),
                mediaId = if (albumArt) answer.mediaId else null,
                albumId = answer.albumId,
                referencedSongs = referencedSongs,
                referencedAlbums = referencedAlbums,
            )
        }
        return null
    }

    private fun songTitleQuestion(
        byAlbum: Map<String, List<Track>>,
        usedSongs: Set<Long>,
        n: Int,
        rng: Random,
    ): Question? {
        val albums = byAlbum.keys.filter { it.isNotBlank() }.shuffled(rng)
        for (target in albums) {
            val targetTracks = byAlbum[target].orEmpty()
            if (targetTracks.isEmpty()) continue
            val answer = targetTracks.filter { it.mediaId !in usedSongs }.ifEmpty { targetTracks }.random(rng)
            val referenced = mutableSetOf(answer.mediaId)
            val distractors = mutableListOf<String>()
            for (album in albums) {
                if (album == target) continue
                val track = byAlbum[album].orEmpty().randomOrNull(rng) ?: continue
                if (track.title.isBlank() || track.title == answer.title) continue
                if (distractors.contains(track.title)) continue
                distractors += track.title
                referenced += track.mediaId
                if (distractors.size == n - 1) break
            }
            if (distractors.size < n - 1) continue
            val options = (distractors + answer.title).shuffled(rng)
            return Question(
                kind = Kind.SONG_TITLE,
                prompt = "which song is on \"$target\"?",
                answer = answer.title,
                options = options,
                answerIndex = options.indexOf(answer.title),
                mediaId = answer.mediaId,
                albumId = answer.albumId,
                referencedSongs = referenced,
                referencedAlbums = setOf(answer.albumId),
            )
        }
        return null
    }

    private fun oddOneOutQuestion(
        byArtist: Map<String, List<Track>>,
        usedSongs: Set<Long>,
        n: Int,
        rng: Random,
    ): Question? {
        val majorityArtists = byArtist.entries
            .filter { it.key.isNotBlank() && it.value.size >= n - 1 }
            .shuffled(rng)
        for ((majority, majorityTracks) in majorityArtists) {
            val odd = byArtist.entries
                .filter { it.key != majority && it.value.isNotEmpty() }
                .shuffled(rng)
                .firstOrNull { it.value.none { t -> majorityTracks.any { m -> m.mediaId == t.mediaId } } }
                ?: continue
            val oddTrack = odd.value.random(rng)
            val referenced = mutableSetOf(oddTrack.mediaId)
            val belong = majorityTracks
                .filter { it.mediaId !in usedSongs }
                .ifEmpty { majorityTracks }
                .shuffled(rng)
                .take(n - 1)
            belong.forEach { referenced += it.mediaId }
            val options = (belong.map { it.title } + oddTrack.title).shuffled(rng)
            if (options.distinct().size < n) continue
            return Question(
                kind = Kind.ODD_ONE_OUT,
                prompt = "which song doesn't belong?",
                answer = oddTrack.title,
                options = options,
                answerIndex = options.indexOf(oddTrack.title),
                mediaId = null,
                albumId = null,
                referencedSongs = referenced,
                referencedAlbums = (belong.map { it.albumId } + oddTrack.albumId).toSet(),
            )
        }
        return null
    }

    private fun albumArtQuestion(
        byAlbum: Map<String, List<Track>>,
        usedSongs: Set<Long>,
        usedAlbums: Set<Long>,
        n: Int,
        rng: Random,
    ): Question? {
        if (byAlbum.size < n) return null
        val albums = byAlbum.keys.filter { it.isNotBlank() }.shuffled(rng)
        for (target in albums) {
            val targetTracks = byAlbum[target].orEmpty()
            if (targetTracks.isEmpty()) continue
            val answer = targetTracks.filter { it.mediaId !in usedSongs }.ifEmpty { targetTracks }.random(rng)
            val distractors = albums.filter { it != target }.take(n - 1)
            if (distractors.size < n - 1) continue
            val options = (distractors + target).shuffled(rng)
            return Question(
                kind = Kind.ALBUM_ART,
                prompt = "which album is this?",
                answer = target,
                options = options,
                answerIndex = options.indexOf(target),
                mediaId = answer.mediaId,
                albumId = answer.albumId,
                referencedSongs = setOf(answer.mediaId),
                referencedAlbums = setOf(answer.albumId),
            )
        }
        return null
    }

    private fun <T> List<T>.randomOrNull(rng: Random): T? = if (isEmpty()) null else this.random(rng)
}
