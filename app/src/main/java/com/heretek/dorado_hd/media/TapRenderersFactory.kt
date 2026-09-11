package com.heretek.dorado_hd.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor

/**
 * Renderers factory that installs the visualizer PCM tap ([VisualizerBus]) into
 * the audio processor chain, mirroring the flags DefaultRenderersFactory would
 * otherwise pass through. No permissions and no audible effect: TeeAudioProcessor
 * copies buffers without altering them.
 */
@OptIn(UnstableApi::class)
class TapRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(TeeAudioProcessor(VisualizerBus.pcmSink())))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build()
}
