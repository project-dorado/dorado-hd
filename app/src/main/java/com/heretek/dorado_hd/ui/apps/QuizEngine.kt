package com.heretek.dorado_hd.ui.apps

import com.heretek.dorado_hd.data.model.Track
import kotlin.random.Random

/**
 * Pure quiz question builder used by the Music Quiz mini-app. Produces
 * multiple-choice questions of two kinds from the live library:
 *   - "Which song is by <artist>?"     (kind = BY_ARTIST)
 *   - "Which album is '<song>' on?"    (kind = BY_ALBUM)
 * Distractors are 3 random siblings; returns null when the library has
 * fewer than 4 distinct artists/albums.
 */
object QuizEngine {
    enum class Kind { BY_ARTIST, BY_ALBUM }

    data class Question(
        val prompt: String,
        val answer: String,
        val options: List<String>,
    )

    fun buildQuestion(tracks: List<Track>, rng: Random = Random.Default): Question? {
        if (tracks.size < 4) return null
        val byArtist = tracks.groupBy { it.artist }
        val byAlbum = tracks.groupBy { it.album }
        val kind = if (rng.nextBoolean() && byArtist.size >= 4) Kind.BY_ARTIST else Kind.BY_ALBUM
        return when (kind) {
            Kind.BY_ARTIST -> artistQuestion(byArtist, rng)
            Kind.BY_ALBUM -> buildAlbumQuestion(byAlbum, rng)
        }
    }

    private fun artistQuestion(byArtist: Map<String, List<Track>>, rng: Random): Question? {
        val artists = byArtist.keys.filter { it.isNotBlank() }.distinct()
        if (artists.size < 4) return null
        val target = artists.random(rng)
        val siblings = artists.filter { it != target }.shuffled(rng).take(3)
        val targetTracks = byArtist[target] ?: return null
        val answerTrack = targetTracks.random(rng)
        return Question(
            prompt = "which song is by \"$target\"?",
            answer = answerTrack.title,
            options = (siblings.map { (byArtist[it] ?: emptyList()).random(rng).title } + answerTrack.title).shuffled(rng),
        )
    }

    private fun buildAlbumQuestion(byAlbum: Map<String, List<Track>>, rng: Random): Question? {
        val albums = byAlbum.keys.filter { it.isNotBlank() }.distinct()
        if (albums.size < 4) return null
        val target = albums.random(rng)
        val siblings = albums.filter { it != target }.shuffled(rng).take(3)
        val track = (byAlbum[target] ?: return null).random(rng)
        return Question(
            prompt = "which album is \"${track.title}\" on?",
            answer = target,
            options = (siblings + target).shuffled(rng),
        )
    }
}
