package com.heretek.dorado_hd

import androidx.media3.common.C
import com.heretek.dorado_hd.media.VisualizerBus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerBusTest {

    private fun pcmSine(samples: Int, freqHz: Double = 440.0, sampleRate: Int = 44_100): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val v = (sin(2 * PI * freqHz * i / sampleRate) * 20_000).toInt().toShort()
            buffer.putShort(v)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun `snapshot is null until the window fills, then reports energy`() {
        VisualizerBus.reset()
        assertNull(VisualizerBus.snapshot(16))
        val sink = VisualizerBus.pcmSink()
        sink.flush(44_100, 1, C.ENCODING_PCM_16BIT)
        repeat(4) { sink.handleBuffer(pcmSine(1024)) }
        val bars = VisualizerBus.snapshot(16)
        assertNotNull(bars)
        assertTrue(bars!!.isNotEmpty())
        assertTrue(bars.all { it in 0f..1f })
        assertTrue(bars.any { it > 0.05f })
    }

    @Test
    fun `reset clears history`() {
        val sink = VisualizerBus.pcmSink()
        sink.flush(44_100, 1, C.ENCODING_PCM_16BIT)
        repeat(4) { sink.handleBuffer(pcmSine(1024)) }
        VisualizerBus.reset()
        assertNull(VisualizerBus.snapshot(16))
    }
}
