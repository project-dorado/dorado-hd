package com.heretek.dorado_hd.analysis

/**
 * Zune HD equalizer presets (device `zmedia_serv.dll`
 * `Software\Microsoft\Zune\Equalizer` / `Preset%02d`). Gains are dB offsets for
 * a five-band equalizer, ordered low → high. Pure data so it is unit-testable.
 */
enum class EqPreset(val label: String, val gainsDb: List<Int>) {
    FLAT("flat", listOf(0, 0, 0, 0, 0)),
    ROCK("rock", listOf(4, 2, -1, 2, 4)),
    POP("pop", listOf(-1, 2, 4, 2, -1)),
    JAZZ("jazz", listOf(3, 1, -1, 1, 3)),
    CLASSICAL("classical", listOf(4, 2, -2, 2, 4)),
    ELECTRONIC("electronic", listOf(5, 3, 0, 2, 4));

    companion object {
        /** Resolves a persisted preset name/label, defaulting to [FLAT]. */
        fun fromName(name: String?): EqPreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) }
                ?: FLAT

        /** The bands resampled to [bands] bands (device Equalizer exposes 5). */
        fun gainsForBands(preset: EqPreset, bands: Int): List<Int> {
            if (bands <= 0) return emptyList()
            return (0 until bands).map { b ->
                preset.gainsDb[(b * preset.gainsDb.size) / bands]
            }
        }
    }
}
