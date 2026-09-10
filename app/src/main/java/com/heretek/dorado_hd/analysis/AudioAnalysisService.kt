package com.heretek.dorado_hd.analysis

import com.heretek.dorado_hd.data.model.Artist
import com.heretek.dorado_hd.data.model.Track

/**
 * In-memory feature cache + cosine-similarity ranking, mirroring the sibling
 * desktop `AudioAnalysisService` (Phase 13a). Pure JVM: no Android
 * dependencies, so the ranking is unit-testable. This is what makes the artist
 * page's `related` pivot a real signal instead of a genre-only approximation.
 *
 * Persistence of feature vectors is deferred to M9.1b, when a DSP extractor
 * replaces the metadata prior and analysis becomes expensive.
 */
class AudioAnalysisService(
    private val store: FeatureStore? = null,
    private val analyzer: FeatureAnalyzer = FeatureAnalyzer { AudioFeatureExtractor.extract(it) },
) {

    private val cache = HashMap<Long, AudioFeatures>()
    @Volatile
    private var loaded = false

    /** Hydrates the in-memory cache from the store (once). */
    suspend fun preload() {
        if (loaded) return
        store?.load()?.forEach { cache[it.trackId] = it }
        loaded = true
    }

    /** Computes features via the analyzer (DSP when wired), caches and persists. */
    suspend fun analyze(track: Track): AudioFeatures {
        preload()
        val features = analyzer.analyze(track)
        cache[track.mediaId] = features
        store?.save(features)
        return features
    }

    /** Analyzes every track not already cached/persisted. */
    suspend fun analyzeAll(tracks: List<Track>) {
        preload()
        tracks.forEach { track ->
            if (track.mediaId !in cache) analyze(track)
        }
    }

    /**
     * Synchronous feature lookup: the cache if analyzed, else the (cheap)
     * metadata prior so query methods need no suspend context.
     */
    fun featuresFor(track: Track): AudioFeatures =
        cache.getOrPut(track.mediaId) { AudioFeatureExtractor.extract(track) }

    /** Tracks most similar to [seed], excluding the seed itself. */
    fun findSimilar(seed: Track, library: List<Track>, count: Int = 25): List<Track> {
        return rank(library.filter { it.mediaId != seed.mediaId }, vectorFor(seed), count)
    }

    /** Tracks nearest the centroid of [favorites]. */
    fun findSimilarToFavorites(favorites: List<Track>, library: List<Track>, count: Int = 50): List<Track> {
        if (favorites.isEmpty()) {
            return library.sortedBy { it.title.lowercase() }.take(count)
        }
        val centroid = centroid(favorites.map { vectorFor(it) })
        val favoriteIds = favorites.map { it.mediaId }.toSet()
        return rank(library.filter { it.mediaId !in favoriteIds }, centroid, count)
    }

    /**
     * Artists whose catalog is closest to [seedArtistId]'s catalog centroid,
     * for the artist page's `related` pivot. Empty when the seed has no tracks.
     */
    fun relatedArtists(
        seedArtistId: Long,
        artists: List<Artist>,
        library: List<Track>,
        count: Int = 12,
    ): List<Artist> {
        val seedTracks = library.filter { it.artistId == seedArtistId }
        if (seedTracks.isEmpty()) return emptyList()
        val seedCentroid = centroid(seedTracks.map { vectorFor(it) })

        return library
            .filter { it.artistId != seedArtistId }
            .groupBy { it.artistId }
            .mapNotNull { (artistId, tracks) ->
                val artist = artists.firstOrNull { it.artistId == artistId } ?: return@mapNotNull null
                artist to cosine(seedCentroid, centroid(tracks.map { vectorFor(it) }))
            }
            .sortedWith(
                compareByDescending<Pair<Artist, Double>> { it.second }
                    .thenBy { it.first.name.lowercase() },
            )
            .take(count)
            .map { it.first }
    }

    private fun vectorFor(track: Track): DoubleArray = featuresFor(track).toVector()

    private fun rank(candidates: List<Track>, target: DoubleArray, count: Int): List<Track> =
        candidates
            .map { it to cosine(target, vectorFor(it)) }
            .sortedWith(
                compareByDescending<Pair<Track, Double>> { it.second }
                    .thenBy { it.first.title.lowercase() },
            )
            .take(count)
            .map { it.first }

    private fun centroid(vectors: List<DoubleArray>): DoubleArray {
        val result = DoubleArray(VECTOR_SIZE)
        if (vectors.isEmpty()) return result
        for (vector in vectors) {
            for (i in 0 until VECTOR_SIZE) result[i] += vector[i]
        }
        for (i in 0 until VECTOR_SIZE) result[i] /= vectors.size
        return result
    }

    companion object {
        const val VECTOR_SIZE = 6

        fun cosine(a: DoubleArray, b: DoubleArray): Double {
            var dot = 0.0
            var magA = 0.0
            var magB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                magA += a[i] * a[i]
                magB += b[i] * b[i]
            }
            if (magA == 0.0 || magB == 0.0) return 0.0
            return dot / (Math.sqrt(magA) * Math.sqrt(magB))
        }
    }
}
