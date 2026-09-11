package com.heretek.dorado_hd.media

import android.media.audiofx.Equalizer
import com.heretek.dorado_hd.analysis.EqPreset

/**
 * Best-effort app equalizer applying a Zune HD [EqPreset] to the playback
 * session (device `zmedia_serv` equalizer presets). Attaches lazily to
 * [PlaybackSession.audioSessionId]; silently no-ops when the platform refuses
 * (some devices/ROMs restrict `Equalizer`).
 */
class EqualizerController {
    private var equalizer: Equalizer? = null
    private var boundSessionId: Int = -1

    fun apply(preset: EqPreset, sessionId: Int) {
        if (sessionId < 0) return
        if (equalizer == null || boundSessionId != sessionId) {
            release()
            boundSessionId = sessionId
            equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
            equalizer?.enabled = true
        }
        val eq = equalizer ?: return
        val range = runCatching { eq.bandLevelRange }.getOrNull() ?: return
        val min = range[0].toInt()
        val max = range[1].toInt()
        val bands = runCatching { eq.numberOfBands.toInt() }.getOrDefault(5)
        val gains = EqPreset.gainsForBands(preset, bands)
        gains.forEachIndexed { band, gainDb ->
            val millibels = (gainDb * 100).coerceIn(min, max).toShort()
            runCatching { eq.setBandLevel(band.toShort(), millibels) }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        boundSessionId = -1
    }
}
