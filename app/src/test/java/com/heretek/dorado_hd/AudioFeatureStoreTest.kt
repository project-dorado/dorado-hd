package com.heretek.dorado_hd

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heretek.dorado_hd.analysis.AudioAnalysisService
import com.heretek.dorado_hd.analysis.AudioFeatureExtractor
import com.heretek.dorado_hd.analysis.AudioFeatures
import com.heretek.dorado_hd.analysis.FeatureAnalyzer
import com.heretek.dorado_hd.analysis.FeatureStore
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.model.Track
import com.heretek.dorado_hd.data.repo.RoomFeatureStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioFeatureStoreTest {

    private fun track(id: Long = 1) = Track(
        mediaId = id,
        title = "T$id",
        artist = "A",
        artistId = 10,
        album = "Al",
        albumId = 10,
        genre = "rock",
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "",
        uri = Uri.parse("file:///t$id"),
    )

    @Test
    fun `room store round-trips features`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DoradoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = RoomFeatureStore(db.trackFeatureDao())
            val features = AudioFeatures(42, 128.0, 0.7, 0.5, 0.2, 0.8, 0.4)
            store.save(features)
            assertEquals(listOf(features), store.load())
        } finally {
            db.close()
        }
    }

    @Test
    fun `service computes via the analyzer and persists`() = runBlocking {
        val saved = mutableListOf<AudioFeatures>()
        val store = object : FeatureStore {
            override suspend fun load() = emptyList<AudioFeatures>()
            override suspend fun save(features: AudioFeatures) { saved += features }
        }
        var analyzerCalls = 0
        val analyzer = FeatureAnalyzer { t -> analyzerCalls++; AudioFeatureExtractor.extract(t) }
        val service = AudioAnalysisService(store, analyzer)
        val t = track()

        service.analyze(t)
        service.analyze(t)

        assertEquals(2, analyzerCalls)
        assertEquals(2, saved.size)
        // The cache is now authoritative for sync queries.
        assertEquals(saved.last(), service.featuresFor(t))
    }

    @Test
    fun `preload hydrates the cache and analyzeAll skips cached tracks`() = runBlocking {
        val t = track()
        val stored = AudioFeatureExtractor.extract(t)
        var loadCalls = 0
        var saveCalls = 0
        val store = object : FeatureStore {
            override suspend fun load(): List<AudioFeatures> { loadCalls++; return listOf(stored) }
            override suspend fun save(features: AudioFeatures) { saveCalls++ }
        }
        var analyzerCalls = 0
        val analyzer = FeatureAnalyzer { tr -> analyzerCalls++; AudioFeatureExtractor.extract(tr) }
        val service = AudioAnalysisService(store, analyzer)

        service.analyzeAll(listOf(t))

        assertEquals("persisted track must not be re-analyzed", 0, analyzerCalls)
        assertEquals(0, saveCalls)
        assertEquals(1, loadCalls)
        assertEquals(stored, service.featuresFor(t))
    }
}
