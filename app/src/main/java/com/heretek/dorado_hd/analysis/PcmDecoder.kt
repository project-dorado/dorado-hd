package com.heretek.dorado_hd.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/**
 * Thin best-effort Android glue: decodes the first [MAX_SAMPLES] of 16-bit PCM
 * from a media URI for the DSP extractor. Any failure returns null so the
 * caller falls back to the metadata prior — analysis must never break playback
 * or crash the UI.
 */
object PcmDecoder {

    private const val TIMEOUT_US = 10_000L
    private const val MAX_SAMPLES = 1 shl 19

    suspend fun decodeWindow(
        context: Context,
        uri: Uri,
        maxSamples: Int = MAX_SAMPLES,
    ): Pair<FloatArray, Int>? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri, maxSamples) }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri, maxSamples: Int): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    format = candidate
                    extractor.selectTrack(i)
                    break
                }
            }
            val selected = format ?: return null
            val mime = selected.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = selected.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(selected, null, null, 0)
            codec.start()

            val out = FloatArray(maxSamples)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && count < maxSamples) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size >= 2) {
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = buffer.asShortBuffer()
                        var i = 0
                        while (i < shorts.remaining() && count < maxSamples) {
                            out[count++] = shorts.get(i) / 32768f
                            i++
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }

            return if (count > 0) out.copyOf(count) to sampleRate else null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
