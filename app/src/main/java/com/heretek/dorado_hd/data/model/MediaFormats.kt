package com.heretek.dorado_hd.data.model

/**
 * Kotlin mirror of the desktop's `Dorado.Domain/Models/MediaFormats.cs`. The
 * desktop ingests a wider set than the device can play; the difference is what
 * it transcodes during sync. Both sides carry the same lists and each has a
 * parity test, so drift is caught.
 */
object MediaFormats {

    /** Extensions the desktop library scanner ingests. */
    val ingestExtensions: List<String> = listOf(
        "mp3", "m4a", "m4b", "wma", "mp4", "m4v", "flac", "ogg", "opus", "aac",
    )

    /**
     * Extensions Media3/ExoPlayer plays natively on this client. WMA and M4B are
     * absent: open-source Media3 has no WMA extractor, and M4B is not a device
     * target in this generation — the desktop transcodes both to AAC (m4a).
     */
    val hdPlayableExtensions: List<String> = listOf(
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "mp4", "m4v",
    )

    private val transcodeTargets: Map<String, String> = mapOf(
        "wma" to "m4a",
        "m4b" to "m4a",
        "ape" to "m4a",
        "wav" to "flac",
    )

    const val DEFAULT_TRANSCODE_TARGET: String = "m4a"

    fun isHdPlayable(extension: String): Boolean =
        hdPlayableExtensions.contains(normalize(extension))

    /** The container the desktop would transfer, or null when copied verbatim. */
    fun transcodeTargetFor(extension: String): String? {
        val normalized = normalize(extension)
        if (isHdPlayable(normalized)) return null
        return transcodeTargets[normalized] ?: DEFAULT_TRANSCODE_TARGET
    }

    fun needsTranscode(extension: String): Boolean = transcodeTargetFor(extension) != null

    private fun normalize(extension: String): String =
        extension.trim().trimStart('.').lowercase()
}
