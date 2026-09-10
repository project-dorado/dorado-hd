package com.heretek.dorado_hd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lyrics for a track: [plain] and/or LRC-timestamped [synced] text. */
data class Lyrics(val plain: String?, val synced: String?)

/**
 * Parses LRCLIB `/api/get` JSON. Pure and unit-tested; the HTTP call is the
 * only untested part. LRCLIB is the same source the sibling desktop uses.
 */
object LrcLibParser {

    fun parse(json: String): Lyrics? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (plain == null && synced == null) return null
        return Lyrics(plain, synced)
    }

    /** Strips LRC `[mm:ss.xx]` timestamps, leaving readable lines. */
    fun plainFromSynced(synced: String): String =
        synced.lineSequence()
            .map { line -> line.replace(Regex("\\[[0-9:.]+\\]"), "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}

/** LRCLIB lyrics lookup (post-device extension, canon §10). */
class LrcLibService {

    suspend fun lyricsFor(
        artist: String,
        title: String,
        album: String,
        durationSec: Int,
    ): Lyrics? = withContext(Dispatchers.IO) {
        if (artist.isBlank() || title.isBlank()) return@withContext null
        runCatching {
            val base = "$API_ROOT?artist_name=${encode(artist)}&track_name=${encode(title)}"
            val withAlbum = if (album.isNotBlank()) "$base&album_name=${encode(album)}" else base
            val url = if (durationSec > 0) "$withAlbum&duration=$durationSec" else withAlbum

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "DoradoHD/0.1")
            if (connection.responseCode !in 200..299) {
                null
            } else {
                LrcLibParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
            }
        }.getOrNull()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API_ROOT = "https://lrclib.net/api/get"
    }
}
