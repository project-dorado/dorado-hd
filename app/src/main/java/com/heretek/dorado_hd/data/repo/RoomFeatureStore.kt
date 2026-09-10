package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.analysis.AudioFeatures
import com.heretek.dorado_hd.analysis.FeatureStore
import com.heretek.dorado_hd.data.db.TrackFeatureDao
import com.heretek.dorado_hd.data.db.TrackFeatureEntity

/** Room-backed [FeatureStore] for the M9.3 audio-feature cache. */
class RoomFeatureStore(private val dao: TrackFeatureDao) : FeatureStore {

    override suspend fun load(): List<AudioFeatures> = dao.all().map { it.toModel() }

    override suspend fun save(features: AudioFeatures) {
        dao.upsert(
            TrackFeatureEntity(
                mediaId = features.trackId,
                bpm = features.bpm,
                energy = features.energy,
                valence = features.valence,
                acousticness = features.acousticness,
                danceability = features.danceability,
                spectralCentroid = features.spectralCentroid,
                analyzedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun TrackFeatureEntity.toModel() = AudioFeatures(
        trackId = mediaId,
        bpm = bpm,
        energy = energy,
        valence = valence,
        acousticness = acousticness,
        danceability = danceability,
        spectralCentroid = spectralCentroid,
    )
}
