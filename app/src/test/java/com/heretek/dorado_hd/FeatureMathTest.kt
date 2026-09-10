package com.heretek.dorado_hd

import android.net.Uri
import com.heretek.dorado_hd.analysis.AudioFeatureExtractor
import com.heretek.dorado_hd.analysis.FeatureMath
import com.heretek.dorado_hd.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class FeatureMathTest {

    private val sampleRate = 44_100

    private fun sine(freqHz: Double, samples: Int = 4096, amplitude: Float = 0.8f): FloatArray =
        FloatArray(samples) { (amplitude * sin(2.0 * PI * freqHz * it / sampleRate)).toFloat() }

    private fun track(id: Long = 1, genre: String = "rock") = Track(
        mediaId = id,
        title = "T$id",
        artist = "A",
        artistId = 10,
        album = "Al",
        albumId = 10,
        genre = genre,
        durationMs = 180_000,
        dateAdded = 0,
        trackNumber = 1,
        year = "",
        uri = Uri.parse("file:///t$id"),
    )

    @Test
    fun `rms matches the signal amplitude`() {
        val constant = FloatArray(1024) { 0.5f }
        assertEquals(0.5, FeatureMath.rms(constant), 1e-6)
        assertEquals(0.0, FeatureMath.rms(FloatArray(0)), 1e-6)
    }

    @Test
    fun `fft finds the dominant frequency of a sine`() {
        val freq = FeatureMath.dominantFrequency(sine(1000.0), sampleRate)
        assertEquals(1000.0, freq, 30.0)
    }

    @Test
    fun `spectral centroid of a pure tone tracks its frequency`() {
        val low = FeatureMath.spectralCentroidNormalized(sine(500.0), sampleRate)
        val high = FeatureMath.spectralCentroidNormalized(sine(3000.0), sampleRate)
        assertTrue("higher tone should have higher centroid", high > low)
        assertEquals(1000.0 / (sampleRate / 2.0), FeatureMath.spectralCentroidNormalized(sine(1000.0), sampleRate), 0.02)
    }

    @Test
    fun `zero crossing rate rises with frequency`() {
        val low = FeatureMath.zeroCrossingRate(sine(200.0))
        val high = FeatureMath.zeroCrossingRate(sine(4000.0))
        assertTrue(high > low)
    }

    @Test
    fun `loudness is monotonic and silent at zero`() {
        assertEquals(0.0, FeatureMath.loudness(0.0), 1e-9)
        assertTrue(FeatureMath.loudness(0.5) > FeatureMath.loudness(0.05))
    }

    @Test
    fun `pcm extractor raises energy for louder audio`() {
        val quiet = AudioFeatureExtractor.fromPcm(track(), sine(1000.0, amplitude = 0.02f), sampleRate)
        val loud = AudioFeatureExtractor.fromPcm(track(), sine(1000.0, amplitude = 0.9f), sampleRate)
        assertTrue("louder window should read more energetic", loud.energy > quiet.energy)
    }

    @Test
    fun `pcm extractor tracks brightness and falls back on empty input`() {
        val dark = AudioFeatureExtractor.fromPcm(track(), sine(300.0), sampleRate)
        val bright = AudioFeatureExtractor.fromPcm(track(), sine(6000.0), sampleRate)
        assertTrue(bright.spectralCentroid > dark.spectralCentroid)

        assertEquals(AudioFeatureExtractor.extract(track()), AudioFeatureExtractor.fromPcm(track(), FloatArray(0), sampleRate))
    }
}
